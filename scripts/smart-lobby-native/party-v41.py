"""Exercise real Parlor controls on an isolated, pinned v4.1 native pair."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import time

from ui_sdk import SessionGroup

# The operator supplies exact-source connection receipts and private token files.
os.umask(0o077)
root = Path(os.environ["PARLOR_UI_WORKSPACE"]).resolve(strict=True)
out = Path((root / "evidence-path").read_text().strip())
plan = json.loads((root / "run.json").read_text())
sides = ("android", "ios")
apps = {"android":"me.parlor.android.debug","ios":"me.parlor.ios.debug"}
seats = {"android":"HostQA","ios":"PeerQA"}
code_pattern = re.compile(r"[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}")
parser = argparse.ArgumentParser()
parser.add_argument("step", choices=("inspect","setup","play","recover","rejoin","close","host","join","playlist","start"))
parser.add_argument("side", choices=sides, nargs="?")
args=parser.parse_args()
mapping=root/"devices.json"
mapping.write_text(json.dumps({s:{"connection":f"connections/{s}-1.json","token_file":f"{s}-1.token"} for s in sides}))
group=SessionGroup.open(mapping)
clients={s:group.device(s) for s in sides}
viewport={}
metrics={s:{"compact_ms":[],"compact_bytes":[],"diff_bytes":[],"settled":0} for s in sides}

def result(response, action):
    if not response.get("ok"):
        status=response.get("error",{}).get("code","unknown")
        raise RuntimeError(f"{action} failed ({status})")
    return response["result"]

def command(side, command, params=None):
    return result(clients[side].action(command,params),f"{side} {command}")

def mark(side, action):
    item={"utc":datetime.now(timezone.utc).isoformat(timespec="milliseconds"),"platform":side,"action":action}
    with (out/"journey.jsonl").open("a") as log:
        log.write(json.dumps(item)+"\n")
    print(item["utc"], side, action, flush=True)

def observe(side):
    start=time.monotonic()
    state=result(clients[side].inspect(format="compact",limit=350,force_refresh=False),f"{side} compact snapshot")
    metrics[side]["compact_ms"].append(round((time.monotonic()-start)*1000,1))
    metrics[side]["compact_bytes"].append(len(state["text"].encode()))
    return state

def safe_text(state):
    return "\n".join(code_pattern.sub("[room-code]",line) for line in state["text"].splitlines())

def find(state, pattern, *, actionable=False):
    def captions(node):
        for key in ("label","value"):
            caption=node.get(key,"")
            yield caption
            parts=caption.split(", ")
            if len(parts)>1 and node["role"] in ("button","textbox"):
                yield parts[-1]
    matching=[n for n in state["nodes"] if any(re.fullmatch(pattern,caption,re.I) for caption in captions(n))]
    if not actionable:
        return next(iter(matching),None)
    candidates=[n for n in matching if n.get("pinned_ref") and not {"disabled","occluded"}.intersection(n.get("states",[]))]
    ranked=sorted(candidates,key=lambda n:n["role"] not in ("button","textbox","checkbox","switch"))
    return next(iter(ranked),None)

def tap(side, node, label):
    change=result(clients[side].click(ref=node["pinned_ref"],settle="fast",return_diff=True),f"{side} {label}")
    delta=change.get("diff")
    if delta:
        metrics[side]["diff_bytes"].append(len(delta.get("text","").encode()))
    if change.get("settle"):
        metrics[side]["settled"]+=1
    mark(side,label)

def wait(side, pattern, seconds=24):
    deadline=time.monotonic()+seconds
    while time.monotonic()<deadline:
        state=observe(side)
        if node:=find(state,pattern):
            return node
    raise RuntimeError(f"{side} did not show expected control {pattern}")

def scroll(side, down=True):
    if side not in viewport:
        dims=result(clients[side].device_info(),f"{side} device info")["viewport"]
        viewport[side]=(dims["width"],dims["height"])
    w,h=viewport[side]
    a,b=int(h*.70),int(h*.38)
    if not down:
        a,b=b,a
    command(side,"pointer.drag",{"x":int(w*.47),"y":a,"end_x":int(w*.47),"end_y":b,"duration_ms":390,"settle":"fast"})

def press(side, pattern, label=None, scrolls=0, down=True):
    for attempt in range(scrolls+1):
        state=observe(side)
        if node:=find(state,pattern,actionable=True):
            tap(side,node,label or pattern)
            return
        if attempt<scrolls:
            scroll(side,down)
    raise RuntimeError(f"{side} missing actionable control {label or pattern}")

def permission(side):
    for _ in range(4):
        state=observe(side)
        if find(state,"Your player name|Choose the first game|Restore hosted party"):
            return
        for pattern in ("Continue","Allow|Allow While Using App|OK"):
            if node:=find(state,pattern,actionable=True):
                tap(side,node,"Confirmed the visible local-network permission")
                break
        else:
            if side=="ios":
                dialog=clients[side].action("alert.present",{})
                if dialog.get("ok") and dialog["result"].get("present"):
                    result(clients[side].alert_accept("Allow"),"iOS Allow local network")
                    continue
            return

def fill(side, pattern, text, label):
    press(side,pattern,label)
    if "text.insert" in clients[side].capabilities()["commands"]:
        command(side,"text.insert",{"text":text,"settle":"fast"})
    else:
        # Android sessions with the native input IME disabled publish ADB typing.
        clients[side].client.fetch("/v1/input",{"action":"type","text":text},retries=0)
    command(side,"keyboard.dismiss",{})
    wait(side,re.escape(text),8)

def room_code(*, down=False):
    for _ in range(6):
        state=observe("android")
        found={s for n in state["nodes"] for k in ("label","value")
               for s in [n.get(k,"").replace(" ","")] if code_pattern.fullmatch(s) and s!="PARLOR"}
        if len(found)==1:
            return found.pop()
        scroll("android",down)
    raise RuntimeError("Android host did not expose one room code")

def app_markers(side):
    raw=result(clients[side].inspect(format="full",limit=350,force_refresh=True),f"{side} diagnostic tree")
    perf=raw.get("performance",{})
    (out/f"{side}-driver-observation.json").write_text(json.dumps({
        "engine":perf.get("engine"),"performance":perf,"node_count":len(raw.get("nodes",[])),
        "device":result(clients[side].device_info(),f"{side} device info")["driver"],
    },indent=2)+"\n")

def join():
    screen=observe("ios")
    if not find(screen,"Let Parlor find the room|Your player name"):
        press("ios","Party room|Open Party room",scrolls=3,down=False)
    permission("ios")
    fill("ios","Your player name",seats["ios"],"Entered the guest name")
    assert "disabled" in wait("ios","Request to join").get("states",[]), "Empty room code must not submit"
    mark("ios","Confirmed an empty room code disables admission")
    code=(root/"room-code.private").read_text().strip()
    fill("ios","Room code",code,"Entered the host code")
    press("ios","Request to join","Requested admission on the iOS app",scrolls=3)
    press("android","Approve|Approve PeerQA(?: to join this room\\.)?","Approved the guest",scrolls=4)
    wait("ios","Connected|PeerQA · Not ready")

def setup():
    for side in sides:
        app_markers(side)
        command(side,"trace.start",{"capture_text":False})
    press("android","Party room|Open Party room",scrolls=4)
    permission("android")
    host()

def host():
    fill("android","Your player name",seats["android"],"Entered the host name")
    press("android","Last Light",scrolls=3)
    press("android","Create party","Created the host Party",scrolls=5)
    (root/"room-code.private").write_text(room_code()+"\n")
    join()
    playlist()
    start()

def playlist():
    press("android","Make a playlist",scrolls=4)
    press("android","Add Last Light",scrolls=4)
    press("android","Add Egyptian Dominoes",scrolls=4)
    press("android","Save playlist",scrolls=4)
    wait("ios","0 of 2 matches completed")

def start():
    press("ios","Ready","Readied the guest",scrolls=4)
    press("android","Start game","Started the Last Light match",scrolls=4)

def snapshot_png(side,name):
    image=clients[side].screenshot(bind_snapshot=True)
    (out/f"{side}-{name}.png").write_bytes(image["png"])
    return image.get("snapshot_id")

def play():
    wins=r"(?:HostQA|PeerQA) wins\.?"
    deadline=time.monotonic()+600
    with ThreadPoolExecutor(max_workers=2) as pool:
        while time.monotonic()<deadline:
            views=dict(zip(sides,pool.map(observe,sides),strict=True))
            finished={s:find(views[s],wins) for s in sides}
            if all(finished.values()):
                outcomes={s:n.get("label",n.get("value","")).rstrip(".") for s,n in finished.items()}
                assert len(set(outcomes.values()))==1
                summaries={s:find(views[s],r"Round [1-9][0-9]*") for s in sides}
                rounds={s:n["label"] for s,n in summaries.items() if n}
                shots={s:snapshot_png(s,"terminal") for s in sides}
                (out/"terminal.json").write_text(json.dumps({"source":plan,"outcomes":outcomes,"rounds":rounds,
                    "snapshot_ids":shots},indent=2)+"\n")
                mark("both","Matching Last Light winner")
                return
            if node:=find(views["android"],"Next round",actionable=True):
                tap("android",node,"Next round")
                continue
            actor=next((s for s in sides if find(views[s],"Your turn")),None)
            if actor is None:
                if any(finished.values()):
                    continue
                scroll("android")
                continue
            for pattern,label in [(r"Challenge(?:\s+.*)?","Challenge"),
                                  (r"Play [1-3] cards?","Played the selected card"),
                                  ("Show hand","Revealed own hand"),
                                  (r".+\. Card 1 of \d+\.","Selected card one")]:
                if node:=find(views[actor],pattern,actionable=True):
                    tap(actor,node,label)
                    break
            else:
                scroll(actor)
    raise RuntimeError("Both native instances did not reach the same Last Light result")

def recover(resume=False):
    outcome=json.loads((out/"terminal.json").read_text())["outcomes"]["android"]
    winner=outcome.split()[0]
    loser=next(n for n in seats.values() if n!=winner)
    def standings(side):
        remaining={r"1 of 2 matches completed",r"Next game · Egyptian Dominoes",
                   re.escape(f"{winner} · Points: 1 · Played: 1"),
                   re.escape(f"{loser} · Points: 0 · Played: 1")}
        for _ in range(2):
            scroll(side,False)
        for _ in range(6):
            state=observe(side)
            remaining={pattern for pattern in remaining if not find(state,pattern)}
            if not remaining:
                return
            scroll(side)
        raise RuntimeError(f"{side} is missing {len(remaining)} public standings fields")
    if not resume:
        if find(observe("android"),r"Back to room"):
            press("android","Back to room","Committed the match result",scrolls=2)
        with ThreadPoolExecutor(max_workers=2) as pool:
            list(pool.map(standings,sides))
        apk=apps["android"]
        before=command("android","app.status",{"id":apk})["pid"]
        command("android","app.kill",{"id":apk,"preserve_state":True})
        assert not command("android","app.status",{"id":apk})["running"]
        mark("android","Host operating-system process stopped without clearing data")
        command("android","app.launch",{"id":apk})
        result(clients["android"].wait_ready(apk,timeout_ms=20000,require_tree=True),"host ready")
        new=command("android","app.status",{"id":apk})["pid"]
        assert before and new and before!=new
        (out/"host-process.json").write_text(json.dumps({"before":before,"after":new})+"\n")
        press("android","Party room|Open Party room",scrolls=4)
        permission("android")
        press("android","Restore hosted party","Restored the original hosting checkpoint")
    else:
        timeline=[json.loads(line) for line in (out/"journey.jsonl").read_text().splitlines()]
        assert any(e["action"]=="Host operating-system process stopped without clearing data" for e in timeline)
        assert any(e["action"]=="Restored the original hosting checkpoint" for e in timeline)
    assert (root/"room-code.private").read_text().strip()==room_code(down=True)
    press("android","Select next game","Selected Egyptian Dominoes after restoring",scrolls=5)
    press("ios","Ready","Original guest readied after reconnect",scrolls=5)
    for _ in range(6):
        if find(observe("android"),"PeerQA · Ready"):
            break
        scroll("android",False)
    else:
        raise RuntimeError("The host did not observe the original guest ready")
    with ThreadPoolExecutor(max_workers=2) as pool:
        list(pool.map(standings,sides))
    (out/"recovery.json").write_text(json.dumps({"source":plan,"result":"PASS","host_process":json.loads((out/"host-process.json").read_text()) if (out/"host-process.json").is_file()
        else {"stopped_and_relaunched":True,"after":command("android","app.status",{"id":apps["android"]})["pid"]},
        "same_room_code":True,"original_guest_ready":True,"both_standings_unchanged":True},indent=2)+"\n")

try:
    if args.step=="inspect":
        for side in ((args.side,) if args.side else sides):
            print(side, safe_text(observe(side)))
    elif args.step=="close":
        for side in sides:
            response=clients[side].action("trace.stop",{})
            if response.get("ok"):
                trace=response["result"]
                trace.pop("session_id",None)
                text=json.dumps(trace,indent=2)
                private_code=root/"room-code.private"
                if private_code.is_file():
                    text=text.replace(private_code.read_text().strip(),"[room-code]")
                (out/f"{side}-redacted-trace.json").write_text(text+"\n")
        with ThreadPoolExecutor(max_workers=2) as pool:
            list(pool.map(lambda side:clients[side].close_session(),sides))
    else:
        {"setup":setup,"host":host,"join":join,"playlist":playlist,"start":start,"play":play,"recover":recover,"rejoin":lambda:recover(resume=True)}[args.step]()
finally:
    if any(v["compact_ms"] for v in metrics.values()):
        with (out/"ui-observation-measurements.jsonl").open("a") as f:
            f.write(json.dumps({"step":args.step,"measurements":metrics})+"\n")

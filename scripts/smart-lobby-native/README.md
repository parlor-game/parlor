# Installed Parlor UI lab

The workflow checks out UI Test Actions v4.1.0 at its immutable release commit. It enables the Android accessibility daemon and independently reserves each device. The retained Party recovery mode uses the native iOS AXe controller. It verifies the source commit and tree, build job, artifact checksum, and installed app before opening the controllers.

## Run the paired Party regression

Use the verified connection files from the hosted lab and the separate private bearer files in an operator-owned directory. Set `PARLOR_UI_WORKSPACE` to that directory and add the matching UI Test Actions checkout to `PYTHONPATH`. The directory contains `run.json` with the exact source and harness identities, `evidence-path` with the local output directory, `connections/{android,ios}-1.json`, and `{android,ios}-1.token`. Restrict the directory to the operator. Keep the tokens, connection files, and room-code scratch file out of reports.

```sh
python3 -B scripts/smart-lobby-native/party-v41.py setup
python3 -B scripts/smart-lobby-native/party-v41.py play
python3 -B scripts/smart-lobby-native/party-v41.py recover
python3 -B scripts/smart-lobby-native/party-v41.py close
```

The journey uses compact accessibility observations, generation-pinned references, action settling, snapshot diffs, two independently owned sessions, and snapshot-bound terminal screenshots. It checks the empty-code form, admission, shared playlist, completed Last Light results, an actual host process restart, the original room code, and the original guest's ready action. The final standings must preserve one result and Egyptian Dominoes next. A failed mutation stops the current phase. The operator can inspect the observed state and resume with the specific phase without replaying a previously committed action. Device ownership conflicts or Human Takeover require the existing lease to become available before issuing further mutations.

Release evidence covers the hosted API 35 emulator and iPhone Simulator. The Android lane uses its configured native ADB typing path when the session does not advertise the optional IME command. Physical devices and cross-process file pickers are separate acceptance cases.

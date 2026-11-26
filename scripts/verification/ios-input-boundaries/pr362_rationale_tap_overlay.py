"""Trace public Local Network Continue callbacks without altering the tap or routing."""

from pathlib import Path
import sys


def replace_once(source: str, old: str, new: str) -> str:
    if source.count(old) != 1:
        raise ValueError(f"Expected one source anchor: {old!r}")
    return source.replace(old, new, 1)


def main(checkout: Path) -> None:
    sources = checkout / "composeApp" / "src"
    permission = Path("kotlin/com/parlor/app/permissions")
    common = sources / "commonMain" / permission
    ios = sources / "iosMain" / permission

    gate = common / "P2pPermissionGate.kt"
    gate.write_text(replace_once(
        gate.read_text(),
        "interface P2pPermissionGate {\n",
        "interface P2pPermissionGate {\n"
        "    fun tracePublicRationaleTap(stage: String) = Unit\n\n",
    ))

    implementation = ios / "P2pPermissionGate.ios.kt"
    native_source = replace_once(
        implementation.read_text(),
        "import platform.Foundation.NSURL\n",
        "import platform.Foundation.NSLog\nimport platform.Foundation.NSURL\n",
    )
    implementation.write_text(replace_once(
        native_source,
        "    override suspend fun request(): PermissionStatus {\n",
        "    override fun tracePublicRationaleTap(stage: String) {\n"
        '        NSLog("ParlorJoinTap $stage")\n'
        "    }\n\n"
        "    override suspend fun request(): PermissionStatus {\n",
    ))

    screen = common / "P2pPermissionRationaleScreen.kt"
    screen.write_text(replace_once(
        screen.read_text(),
        "                            scope.launch {\n"
        "                                val next = gate.request()\n"
        "                                if (next.mayAttemptNetwork) onContinue()\n"
        "                            }",
        '                            gate.tracePublicRationaleTap("click")\n'
        "                            scope.launch {\n"
        '                                gate.tracePublicRationaleTap("coroutine-start")\n'
        "                                val next = gate.request()\n"
        '                                gate.tracePublicRationaleTap("may-attempt=${next.mayAttemptNetwork}")\n'
        "                                if (next.mayAttemptNetwork) onContinue()\n"
        '                                gate.tracePublicRationaleTap("callback-complete")\n'
        "                            }",
    ))


if __name__ == "__main__":
    main(Path(sys.argv[1]))

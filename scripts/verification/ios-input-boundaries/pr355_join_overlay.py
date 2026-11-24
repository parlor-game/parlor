"""Add comparison cases to a frozen checkout's XCTest source only."""

import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
original = path.read_text()
source = original
for language, name, transition in (
    ("Arabic", "ArabicDirectJoin", False),
    ("Arabic", "ArabicHostBackJoin", True),
    ("English", "EnglishHostBackJoin", True),
):
    method = re.search(
        rf'    func testAdditionalGamesExposeOnlyMultiplayerSetupIn{language}\(\) throws \{{\n(.*?)\n    \}}',
        original,
        re.DOTALL,
    )
    assert method is not None
    call = method[1].replace('verifyAdditionalGamesSetup(', 'verifyAdditionalGamesSetup('
                            f'probe: "{name}", visitHost: {str(transition).lower()}, ')
    call = re.sub(r'games: \[("[^"]+").*?\],', r'games: [\1],', call, count=1)
    marker = '    private struct MultiplayerSetupCopy'
    source = source.replace(marker, f'    @MainActor\n    func testProbe{name}() throws {{\n'
                            f'{call}\n    }}\n\n{marker}', 1)

old = 'private func verifyAdditionalGamesSetup(language: String, locale: String, copy: MultiplayerSetupCopy) throws {'
new = ('private func verifyAdditionalGamesSetup(probe: String? = nil, visitHost: Bool = true, '
       'language: String, locale: String, copy: MultiplayerSetupCopy) throws {')
assert source.count(old) == 1
source = source.replace(old, new)
start = source.index('            try tapPublicSetupTransition(app, from: copy.host, to: copy.permission)')
end = source.index('\n\n            try tapSetupButton(app, prefix: game)', start)
host = source[start:end]
source = (source[:start] + '            if visitHost {\n' +
          '\n'.join('    ' + line if line else line for line in host.splitlines()) +
          '\n                try tapSetupButton(app, prefix: game)\n            }\n' +
          source[end:].replace('\n\n            try tapSetupButton(app, prefix: game)', '', 1))
old = '''            name.typeText("Table Test")
            assertTextValue(name, in: app, equals: "Table Test", introduction: introduction)'''
new = '''            if let probe {
                captureJoinProbe(app, name, copy.peerContinue, probe, "before-type")
            }
            name.typeText("Table Test")
            assertTextValue(name, in: app, equals: "Table Test", introduction: introduction)
            if let probe {
                captureJoinProbe(app, name, copy.peerContinue, probe, "after-commit")
            }'''
assert source.count(old) == 1
source = source.replace(old, new)
marker = '    @MainActor\n    func testLastLightLocalPlayerFieldsScrollWithNextAndDismissWithDone'
assert source.count(marker) == 1
source = source.replace(marker, '''    @MainActor
    private func captureJoinProbe(
        _ app: XCUIApplication, _ field: XCUIElement, _ continueLabel: String, _ route: String, _ phase: String
    ) {
        let snapshot = try? field.snapshot()
        let value = snapshot?.value as? String
        let enabled = setupButton(app, prefix: continueLabel).isEnabled
        let keyboard = app.keyboards.firstMatch.exists
        let capture = XCTAttachment(screenshot: app.screenshot())
        capture.name = "\\(route)-\\(phase)"
        capture.lifetime = .keepAlways
        add(capture)
        let focus = snapshot?.hasFocus
        print("Join probe \\(route) \\(phase), field=\\(snapshot != nil), focus=\\(String(describing: focus)), "
              + "keyboard=\\(keyboard), string=\\(value != nil), complete=\\(value == "Table Test"), continue=\\(enabled)")
    }

''' + marker)
path.write_text(source)

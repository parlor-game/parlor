"""Instrument only the disposable ARM64 focus-comparison checkout."""

import re
import sys
from pathlib import Path

root = Path(sys.argv[1])
test = root / "iosApp/iosAppUITests/IOSAppLaunchUITests.swift"
swift = root / "iosApp/iosApp/ComposeContainerViewController.swift"
original = test.read_text()

def replace_once(code, anchor, replacement):
    if code.count(anchor) != 1:
        raise ValueError(f"Frozen source anchor changed: {anchor.splitlines()[0]}")
    return code.replace(anchor, replacement, 1)

arabic = re.search(
    r"    func testAdditionalGamesExposeOnlyMultiplayerSetupInArabic\(\) throws \{\n(.*?)\n    \}",
    original, re.DOTALL,
)
if arabic is None:
    raise ValueError("Missing original Arabic test")
cases = []
for method, arm in (("Original", "original"), ("AlreadyFocused", "held")):
    body = arabic[1].replace(
        "verifyAdditionalGamesSetup(", f'verifyAdditionalGamesSetup(focusReplay: "{arm}", ', 1,
    )
    body, count = re.subn(r'games: \[("[^"]+").*?\],', r"games: [\1],", body, count=1)
    if count != 1:
        raise ValueError("Missing Arabic Egyptian Dominoes route")
    cases.append(f"    @MainActor\n    func testFocusReplay{method}() throws {{\n{body}\n    }}\n")
anchor = "    private struct MultiplayerSetupCopy"
code = replace_once(original, anchor, "\n".join(cases)+"\n"+anchor)
anchor = "private func verifyAdditionalGamesSetup(language: String, locale: String, copy: MultiplayerSetupCopy)"
code = replace_once(code, anchor, "private func verifyAdditionalGamesSetup(focusReplay: String? = nil, language: String, locale: String, copy: MultiplayerSetupCopy)")
anchor = '''            app.launch()
            defer { app.terminate() }
            XCTAssertTrue(app.staticTexts["parlor-home-brand"].waitForExistence(timeout: 30))
            try tapSetupButton(app, prefix: game)
            XCTAssertTrue(setupButton(app, prefix: copy.host).waitForExistence(timeout: 10))'''
code = replace_once(code, anchor, anchor.replace("            app.launch()", '''            if let focusReplay {
                app.launchEnvironment["PARLOR_FOCUS_REPLAY"] = focusReplay
            }
            app.launch()'''))
anchor = '''            name.typeText("Table Test")
            assertTextValue(name, in: app, equals: "Table Test", introduction: introduction)'''
code = replace_once(code, anchor, r'''
            if let focusReplay {
                print("FocusReplay arm=\(focusReplay) event=focus_path_settled epoch=\(Date().timeIntervalSince1970)")
                if focusReplay == "held" {
                    // Hold the same focus without typing or touching the field again.
                    RunLoop.current.run(until: Date().addingTimeInterval(8))
                }
                print("FocusReplay arm=\(focusReplay) event=type_begin epoch=\(Date().timeIntervalSince1970)")
            }
            name.typeText("Table Test")
            assertTextValue(name, in: app, equals: "Table Test", introduction: introduction)
            if let focusReplay {
                print("FocusReplay arm=\(focusReplay) event=assertions_executed epoch=\(Date().timeIntervalSince1970)")
            }'''.lstrip("\n"))
begin = code.index("    private func assertTextValue(")
end = code.index("    @MainActor", begin)
assertion = code[begin:end]
anchor = '''        var samples: [String] = []
        let started = ProcessInfo.processInfo.systemUptime'''
assertion = replace_once(assertion, anchor, '''        var samples: [String] = []
        var lastDiagnosticState: String?
        let focusReplay = app.launchEnvironment["PARLOR_FOCUS_REPLAY"]
        let started = ProcessInfo.processInfo.systemUptime''')
anchor = '''            let exact = matchesPublicTextValue(value, expected: expected)
            if samples.count < 12 {'''
assertion = replace_once(assertion, anchor, r'''            let exact = matchesPublicTextValue(value, expected: expected)
            if let focusReplay {
                let state = value == nil ? "absent" : value == "" ? "empty" : exact ? "exact" : "partial"
                if state != lastDiagnosticState {
                    print("FocusReplay arm=\(focusReplay) event=ax_value state=\(state) epoch=\(Date().timeIntervalSince1970)")
                    lastDiagnosticState = state
                }
            }
            if samples.count < 12 {''')
test.write_text(code[:begin]+assertion+code[end:])

host = swift.read_text()
anchor = "        contentController.didMove(toParent: self)"
swift.write_text(replace_once(host, anchor, anchor+'''
        if let arm = ProcessInfo.processInfo.environment["PARLOR_FOCUS_REPLAY"] {
            ParlorFocusReplay.shared.start(arm: arm)
        }''') + r'''

// This observer is only present in the disposable diagnostic build. It queries
// the first-responder action chain, never changes focus, and never logs text.
@MainActor
private final class ParlorFocusReplay: NSObject {
    static let shared = ParlorFocusReplay()
    private var arm: String?
    private var response = "none"
    private var previous = ""
    private var remaining = 960

    func start(arm: String) {
        guard self.arm == nil, arm == "original" || arm == "held" else { return }
        self.arm = arm
        sample()
    }

    @objc private func sample() {
        guard remaining > 0, let arm else { return }
        remaining -= 1
        response = "none"
        UIApplication.shared.sendAction(#selector(UIResponder.parlorFocusReplayReport(_:)), to: nil, from: self, for: nil)
        if response != previous {
            NSLog("ParlorFocusReplay arm=%@ event=responder state=%@ epoch=%.6f",
                  arm, response, Date().timeIntervalSince1970)
            previous = response
        }
        perform(#selector(sample), with: nil, afterDelay: 0.25)
    }

    func observed(_ responder: UIResponder) {
        guard responder.isFirstResponder else { return }
        let kind = String(describing: type(of: responder))
        let text = (responder as? UIKeyInput).map { $0.hasText ? "nonempty" : "empty" } ?? "unsupported"
        response = "\(kind):\(text)"
    }
}

extension UIResponder {
    @MainActor @objc func parlorFocusReplayReport(_ sender: NSObject) {
        (sender as? ParlorFocusReplay)?.observed(self)
    }
}
''')

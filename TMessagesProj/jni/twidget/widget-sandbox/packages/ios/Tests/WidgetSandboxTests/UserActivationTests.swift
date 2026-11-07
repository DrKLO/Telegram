import XCTest
@testable import WidgetSandbox

/// User activation on the SDK v1 calls (docs/protocol.md, "User activation"; docs/sdk-v1.md §2.5): one real
/// pointer press on the visible widget unlocks one link call (openLink or openTelegramLink), made in that press's task.
final class UserActivationTests: XCTestCase {
    func testNothingRunsAfterDestroy() {
        let renderer = RecordingRenderer()
        var actions = 0
        var host: WidgetHost? = try! WidgetHost(html: "<a id='a' href='https://example.com/x'>x</a><script>var n=0;setInterval(function(){n++;console.log('tick'+n);document.body.setAttribute('data-n',n);},10);requestAnimationFrame(function f(){requestAnimationFrame(f);});document.getElementById('a').addEventListener('click',function(){setTimeout(function(){},0);});</script>", theme: WidgetTheme(), width: 300, rendererFactory: { _, _, _ in renderer })
        host?.onAction = { _ in actions += 1 }
        let deadline = Date().addingTimeInterval(5)
        while !(host?.diagnostics.logs.count ?? 0 > 3) && Date() < deadline { RunLoop.main.run(until: Date().addingTimeInterval(0.01)) }
        host?.destroy()
        RunLoop.main.run(until: Date().addingTimeInterval(0.1))
        let sent = renderer.messages.count
        let logs = host?.diagnostics.logs.count ?? 0
        renderer.onMessage?("{\"type\":\"event\",\"ev\":{\"type\":\"click\",\"targetId\":5}}")
        renderer.onMessage?("{\"type\":\"frame\",\"time\":1}")
        RunLoop.main.run(until: Date().addingTimeInterval(0.4))
        XCTAssertEqual(renderer.messages.count, sent)
        XCTAssertEqual(host?.diagnostics.logs.count ?? 0, logs)
        XCTAssertEqual(actions, 0)
        host = nil
    }

    final class Harness {
        let renderer = RecordingRenderer()
        let host: WidgetHost
        var actions: [WidgetAction] = []
        var commits: [(area: WidgetStorageArea, values: [String: String])] = []
        var claims: [WidgetGestureClaim] = []

        init(_ html: String, timeout: TimeInterval = 5, theme: WidgetTheme = WidgetTheme(), options: WidgetHost.Options = WidgetHost.Options()) {
            let renderer = self.renderer
            var options = options
            options.policy = .strict
            host = try! WidgetHost(html: html, theme: theme, width: 300, options: options, rendererFactory: { _, _, _ in renderer })
            host.onAction = { [weak self] action in self?.actions.append(action) }
            host.onStorageCommit = { [weak self] area, values in self?.commits.append((area, values)) }
            host.onGestureClaim = { [weak self] claim in self?.claims.append(claim) }
            pump(timeout) { self.host.diagnostics.rendered || self.host.diagnostics.dead != nil }
        }

        /// The logged lines (`console.log`) so far.
        var logs: [String] { host.diagnostics.logs.map { $0.text } }

        /// Everything but `ready`.
        var privileged: [WidgetAction] { actions.filter { $0 != .ready } }
        /// The URLs of the link actions, in order.
        var links: [String] {
            actions.compactMap {
                switch $0 {
                case .openLink(let url), .openTelegramLink(let url): return url.absoluteString
                default: return nil
                }
            }
        }
        var blocked: Int { count("activation-blocked") }
        func count(_ diag: String) -> Int { host.diagnostics.diags[diag]?.count ?? 0 }

        /// A renderer event; `trusted` as the WebView renderer reports the DOM event's isTrusted.
        func event(_ ev: [String: Any], trusted: Bool? = true) {
            var message: [String: Any] = ["type": "event", "ev": ev]
            if let trusted { message["trusted"] = trusted }
            let data = try! JSONSerialization.data(withJSONObject: message)
            renderer.onMessage?(String(decoding: data, as: UTF8.self))
        }

        func click(_ target: Int) {
            event(["type": "click", "targetId": target, "clientX": 5, "clientY": 5, "button": 0, "buttons": 0, "detail": 1])
        }

        /// The renderer's claim for a primary press (sent for every one, before the press's own events).
        func pressClaim() {
            renderer.onMessage?("{\"type\":\"gesture\",\"x\":false,\"y\":false,\"hold\":false}")
        }

        func settle(_ seconds: TimeInterval = 0.3) {
            pump(seconds) { false }
        }

        func pump(_ timeout: TimeInterval = 5, _ condition: () -> Bool) {
            let deadline = Date().addingTimeInterval(timeout)
            while !condition() && Date() < deadline {
                RunLoop.main.run(until: Date().addingTimeInterval(0.01))
            }
        }

        deinit { host.destroy() }
    }

    private static let anchor = 5

    func testScriptedClicksAtBootAreIgnored() {
        let h = Harness("<a id='a' href='https://example.com/x'>link</a><script>var a=document.getElementById('a');a.click();a.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true}));TelegramWidget.openLink('https://example.com/y');window.open('https://example.com/z');TelegramWidget.openTelegramLink('https://t.me/w');TelegramWidget.ready();</script>")
        h.settle()
        XCTAssertEqual(h.privileged, [])
        XCTAssertGreaterThanOrEqual(h.blocked, 4)
        XCTAssertEqual(h.actions, [.ready])
    }

    func testRealClickOnAnchorOpensOnce() {
        let h = Harness("<a id='a' href='https://example.com/x'>link</a>")
        h.click(Self.anchor)
        h.pump { !h.privileged.isEmpty }
        h.settle()
        XCTAssertEqual(h.actions, [.openLink(URL(string: "https://example.com/x")!)])
    }

    /// v1 has no `chat:` links: an anchor whose href no link method takes is ignored with a diagnostic, and its tap is not used up.
    func testNonHttpsAnchorIsIgnored() {
        let h = Harness("<div class='widget'><a id='a' href='chat:send?text=/help'>help</a><a id='b' href='https://t.me/durov'>t</a></div>")
        h.click(6)
        h.settle()
        XCTAssertEqual(h.privileged, [])
        XCTAssertEqual(h.blocked, 0)
        XCTAssertGreaterThanOrEqual(h.count("navigation-blocked"), 1)
        h.click(8)
        h.pump { !h.privileged.isEmpty }
        XCTAssertEqual(h.actions, [.openTelegramLink(URL(string: "https://t.me/durov")!)], "a Telegram host goes to openTelegramLink")
    }

    func testActivationIsConsumedOnce() {
        let h = Harness("<a id='a' href='#'>x</a><script>document.getElementById('a').addEventListener('click',function(){TelegramWidget.openLink('https://a.example');TelegramWidget.openLink('https://b.example');TelegramWidget.openTelegramLink('https://t.me/c');});</script>")
        h.click(Self.anchor)
        h.pump { !h.privileged.isEmpty }
        h.settle()
        XCTAssertEqual(h.links, ["https://a.example"])
        XCTAssertEqual(h.blocked, 2, "one press unlocks one call, shared by both link methods")
    }

    func testTimersAndPromisesAfterClick() {
        let h = Harness("<a id='a' href='#'>x</a><script>document.getElementById('a').addEventListener('click',function(){Promise.resolve().then(function(){TelegramWidget.openLink('https://micro.example');});setTimeout(function(){TelegramWidget.openLink('https://timer.example');},0);});</script>")
        h.click(Self.anchor)
        h.settle(0.5)
        XCTAssertEqual(h.links, ["https://micro.example"])
        XCTAssertEqual(h.blocked, 1)
    }

    func testNonGestureEventsDoNotActivate() {
        let h = Harness("<a id='a' href='https://example.com/x'>x</a><script>var a=document.getElementById('a');['mousemove','mouseover','animationend','transitionend','scroll','focus','input','change','submit','load','wheel','keyup'].forEach(function(t){a.addEventListener(t,function(){a.click();TelegramWidget.openLink('https://'+t+'.example');});});document.addEventListener('keydown',function(e){TelegramWidget.openLink('https://key.example/'+encodeURIComponent(e.key));});</script>")
        for type in ["mousemove", "mouseover", "animationend", "transitionend", "scroll", "focus", "input", "change", "submit", "load", "wheel", "keyup"] {
            h.event(["type": type, "targetId": Self.anchor])
        }
        for (key, isRepeat, modifiers) in [("Enter", false, 0), (" ", false, 0), ("Escape", false, 0), ("Enter", true, 0), (" ", false, 4), ("Tab", false, 0), ("ArrowDown", false, 0), ("a", false, 0), ("PageDown", false, 0), ("Shift", false, 8)] {
            h.event(["type": "keydown", "targetId": 4, "key": key, "code": key, "repeat": isRepeat, "modifiers": modifiers])
        }
        h.settle(0.5)
        XCTAssertEqual(h.links, [])
        XCTAssertGreaterThanOrEqual(h.blocked, 20)
    }

    func testOnlyTrustedRendererEventsActivate() {
        let h = Harness("<a id='a' href='https://example.com/x'>x</a>")
        h.event(["type": "click", "targetId": Self.anchor, "detail": 1], trusted: false)
        h.event(["type": "click", "targetId": Self.anchor, "detail": 1], trusted: nil)
        h.settle()
        XCTAssertEqual(h.privileged.count, 0)
        XCTAssertEqual(h.blocked, 2)
        h.event(["type": "click", "targetId": Self.anchor, "detail": 1])
        h.pump { !h.privileged.isEmpty }
        XCTAssertEqual(h.links, ["https://example.com/x"])
    }

    /// Widgets get no keyboard: neither a key nor the click Enter or Space activates (`detail` 0) is a gesture.
    func testKeysAndKeyboardClicksNeverActivate() {
        let h = Harness("<button id='b'>go</button><script>var b=document.getElementById('b');b.addEventListener('keydown',function(e){TelegramWidget.openLink('https://example.com/key-'+encodeURIComponent(e.key));});b.addEventListener('click',function(e){TelegramWidget.openLink('https://example.com/click-'+e.detail);});</script>")
        let button = 5
        h.event(["type": "keydown", "targetId": button, "key": "Enter", "code": "Enter", "repeat": false, "modifiers": 0])
        h.event(["type": "click", "targetId": button, "detail": 0])
        h.event(["type": "keydown", "targetId": button, "key": "Enter", "code": "Enter", "repeat": true, "modifiers": 0])
        h.event(["type": "click", "targetId": button, "detail": 0])
        h.event(["type": "keydown", "targetId": button, "key": " ", "code": "Space", "repeat": false, "modifiers": 0])
        h.event(["type": "keyup", "targetId": button, "key": " ", "code": "Space", "repeat": false, "modifiers": 0])
        h.event(["type": "click", "targetId": button, "detail": 0])
        h.settle(0.5)
        XCTAssertEqual(h.links, [])
        XCTAssertEqual(h.blocked, 6)
        h.event(["type": "click", "targetId": button, "detail": 1])
        h.pump { !h.privileged.isEmpty }
        XCTAssertEqual(h.links, ["https://example.com/click-1"], "a pointer click still is a gesture")
        h.event(["type": "click", "targetId": button, "detail": 0])
        h.settle()
        XCTAssertEqual(h.privileged.count, 1, "a keyboard click after it is not")
    }

    func testOnePhysicalPressIsOneGesture() {
        let h = Harness("<a id='a' href='#'>x</a><script>['pointerdown','mousedown','pointerup','mouseup','click','dblclick'].forEach(function(t){document.addEventListener(t,function(){TelegramWidget.openLink('https://'+t+'.example');});});</script>")
        func press(_ detail: Int) {
            for type in ["pointerdown", "mousedown", "pointerup", "mouseup", "click"] + (detail == 2 ? ["dblclick"] : []) {
                h.event(["type": type, "targetId": Self.anchor, "clientX": 5, "clientY": 5, "detail": detail])
            }
        }
        press(1)
        h.settle()
        XCTAssertEqual(h.links, ["https://pointerdown.example"])
        press(2)
        h.settle()
        XCTAssertEqual(h.privileged.count, 2)
        XCTAssertEqual(h.blocked, 9)
    }

    func testDropOnPointerUpStillWorks() {
        let h = Harness("<div id='d'>card</div><script>var drag=false;document.getElementById('d').addEventListener('pointerdown',function(){drag=true;});document.addEventListener('pointerup',function(){if(drag){drag=false;TelegramWidget.openLink('https://example.com/done');}});</script>")
        h.event(["type": "pointerdown", "targetId": 5])
        h.event(["type": "pointermove", "targetId": 4])
        h.event(["type": "pointerup", "targetId": 4])
        h.pump { !h.privileged.isEmpty }
        XCTAssertEqual(h.links, ["https://example.com/done"])
    }

    /// A finger's pointerdown is not trusted (the press may still become the chat's scroll): its press counts from its
    /// release. The renderer's `gesture` claim begins a new gesture, so a tap after a drag that spent its gesture without
    /// a click is a gesture of its own, not the end of the drag's (user-activation.test.ts, the desktop
    /// `user_gestures_are_numbered_per_physical_press`). The widget listens to no pointerdown: only the claims split the presses.
    func testAPressTheRendererClaimsBeginsANewGesture() {
        let h = Harness("<div id='d'>card</div><script>var moved=false,n=0;document.addEventListener('pointermove',function(){moved=true;});document.addEventListener('pointerup',function(){if(moved){moved=false;TelegramWidget.openLink('https://example.com/drop-'+(++n));}});document.addEventListener('click',function(){TelegramWidget.openLink('https://example.com/click-'+(++n));});</script>")
        // a finger's drag: its gesture spent at its release (a drop), no click after it
        h.pressClaim()
        h.event(["type": "pointermove", "targetId": 5, "pointerType": "touch"], trusted: true)
        h.event(["type": "pointerup", "targetId": 5, "pointerType": "touch"])
        h.pump { h.links.count >= 1 }
        // a finger's tap: its release and its click are the next gesture
        h.pressClaim()
        h.event(["type": "pointerup", "targetId": 5, "pointerType": "touch"])
        h.event(["type": "click", "targetId": 5, "detail": 1])
        h.pump { h.links.count >= 2 }
        // a drag with a click: the claim comes before the press's events, which stay one gesture
        h.pressClaim()
        h.event(["type": "pointermove", "targetId": 5, "pointerType": "touch"], trusted: true)
        h.event(["type": "pointerup", "targetId": 5, "pointerType": "touch"])
        h.event(["type": "click", "targetId": 5, "detail": 1])
        h.pump { h.links.count >= 3 }
        h.settle()
        XCTAssertEqual(h.links, ["https://example.com/drop-1", "https://example.com/click-2", "https://example.com/drop-3"])
        XCTAssertEqual(h.blocked, 1, "the last click continues its press, whose gesture its release spent")
    }

    /// Any pointerdown begins a press, trusted or not: a second finger's is neither trusted (it may become the chat's
    /// scroll) nor claimed (`gesture` is the primary pointer's), so its release is a new gesture, not the one before it,
    /// spent (the desktop `an_untrusted_pointerdown_begins_a_new_gesture`).
    func testAnUntrustedPointerdownBeginsANewGesture() {
        let h = Harness("<button id='b'>go</button><script>TelegramWidget.ready();var n=0;document.getElementById('b').addEventListener('pointerup',function(){TelegramWidget.openLink('https://example.com/up-'+(++n));});</script>")
        let finger: [String: Any] = ["targetId": 5, "pointerId": 2, "pointerType": "touch", "isPrimary": false]
        // a second finger's tap, its gesture spent in its pointerup handler; then another tap of it
        for _ in 0..<2 {
            h.event(finger.merging(["type": "pointerdown"]) { $1 }, trusted: false)
            h.event(finger.merging(["type": "pointerup"]) { $1 })
        }
        h.pump { h.links.count >= 2 }
        h.settle()
        XCTAssertEqual(h.links, ["https://example.com/up-1", "https://example.com/up-2"])
        XCTAssertEqual(h.blocked, 0)
    }

    func testKeyPressDoesNotActivate() {
        let h = Harness("<div>x</div><script>document.addEventListener('keydown',function(e){if(e.key==='Enter')TelegramWidget.openLink('https://example.com/go');});</script>")
        h.event(["type": "keydown", "targetId": 4, "key": "Enter", "code": "Enter", "repeat": false, "modifiers": 0])
        h.settle(0.5)
        XCTAssertEqual(h.privileged.count, 0)
        XCTAssertEqual(h.blocked, 1)
    }

    func testHiddenWidgetCannotUseActivation() {
        let h = Harness("<a id='a' href='https://example.com/x'>x</a>")
        h.host.setVisible(false)
        h.settle(0.1)
        h.click(Self.anchor)
        h.settle()
        XCTAssertEqual(h.privileged.count, 0)
        let window = TestWindow.makeVisible()
        window.attach(h.host.view)
        defer { window.close() }
        h.host.setVisible(true)
        h.settle(0.1)
        h.click(Self.anchor)
        h.pump { !h.privileged.isEmpty }
        XCTAssertEqual(h.privileged.count, 1)
    }

    /// A flood of valid link calls from one tap: one action, the rest `activation-blocked` (reported at most 20 times).
    func testFloodAfterOneTapYieldsOneAction() {
        let h = Harness("<a id='a' href='#'>x</a><script>document.getElementById('a').addEventListener('click',function(){for(var i=0;i<50;i++){TelegramWidget.openLink('https://example.com/flood-'+i);TelegramWidget.openTelegramLink('https://t.me/flood'+i);}});</script>")
        h.click(Self.anchor)
        h.pump { !h.privileged.isEmpty }
        h.settle(0.3)
        XCTAssertEqual(h.links, ["https://example.com/flood-0"])
        XCTAssertEqual(h.blocked, 20)
    }

    private static let attacks = URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("../../../../corpus/attacks").standardized

    /// The attack's calls are blocked twice over: the shim throws on `http:` URLs and on the removed sendMessage, the glue blocks the rest.
    func testTimerDrivenAttackStaysBlocked() throws {
        try XCTSkipIf(!FileManager.default.fileExists(atPath: Self.attacks.path), "needs the repository's corpus/attacks")
        let h = Harness(try String(contentsOf: Self.attacks.appendingPathComponent("activation-timers.html")))
        h.settle(1.6)
        XCTAssertEqual(h.privileged, [])
    }

    func testSyntheticInputAttackStaysBlocked() throws {
        try XCTSkipIf(!FileManager.default.fileExists(atPath: Self.attacks.path), "needs the repository's corpus/attacks")
        let h = Harness(try String(contentsOf: Self.attacks.appendingPathComponent("activation-synthetic-input.html")))
        h.settle(0.3)
        XCTAssertEqual(h.privileged, [])
    }

    /// The corpus flood opens `http:` URLs, which no link method takes: after a real tap nothing reaches the client.
    func testFloodAttackAfterOneClickReachesNothing() throws {
        try XCTSkipIf(!FileManager.default.fileExists(atPath: Self.attacks.path), "needs the repository's corpus/attacks")
        let h = Harness(try String(contentsOf: Self.attacks.appendingPathComponent("activation-flood.html")))
        h.click(6)
        h.settle(0.5)
        XCTAssertEqual(h.privileged, [])
    }

    /// No attack opens a link (the privileged bridge) without a tap. Haptics need no tap: an attack that plays them
    /// is held to the haptic gate instead (`testHapticTimerBuzzPlaysOneSeries`).
    func testBundledAttackWidgetsReachNoPrivilegedBridge() throws {
        try XCTSkipIf(!FileManager.default.fileExists(atPath: Self.attacks.path), "needs the repository's corpus/attacks")
        let directory = Self.attacks
        let files = try FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil).filter { $0.pathExtension == "html" }
        XCTAssertGreaterThan(files.count, 50)
        for file in files {
            let h = Harness(try String(contentsOf: file), timeout: 1)
            h.settle(0.05)
            XCTAssertEqual(h.privileged.filter { if case .haptic = $0 { return false } else { return true } }, [], file.lastPathComponent)
        }
    }

    /// corpus/attacks/haptic-timer-buzz.html (Telegram Android 13.2.0): `setInterval(…, 0)` asking for haptics with no
    /// touch gets one series, the first second under the rate limit, then nothing (tests/attacks.spec.ts).
    func testHapticTimerBuzzPlaysOneSeries() throws {
        let file = Self.attacks.appendingPathComponent("haptic-timer-buzz.html")
        try XCTSkipIf(!FileManager.default.fileExists(atPath: file.path), "needs the repository's corpus/attacks")
        let h = Harness(try String(contentsOf: file))
        h.settle(3)
        let played = h.actions.filter { if case .haptic = $0 { return true } else { return false } }.count
        XCTAssertGreaterThan(played, 0)
        XCTAssertLessThanOrEqual(played, 5 + 10 + 1)
        XCTAssertGreaterThan(h.count("haptic-limit"), 0)
        XCTAssertEqual(h.links, [])
    }
}

final class RecordingRenderer: Renderer {
    let view = PlatformView(frame: CGRect(x: 0, y: 0, width: 300, height: 100))
    var onMessage: ((String) -> Void)?
    var onDiag: ((String, String) -> Void)?
    var messages: [String] = []

    func load() {
        onMessage?("{\"type\":\"ready\"}")
    }

    func send(_ json: String) {
        messages.append(json)
        guard let data = json.data(using: .utf8), let message = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any], let type = message["type"] as? String else {
            return
        }
        if type == "ops" {
            onMessage?("{\"type\":\"size\",\"width\":300,\"height\":120}")
        } else if type == "query", let qid = message["qid"] as? Int {
            DispatchQueue.main.async { [weak self] in
                self?.onMessage?("{\"type\":\"result\",\"qid\":\(qid),\"result\":null}")
            }
        }
    }

    func setTheme(_ theme: String) {}
    func evaluate(_ script: String, completion: @escaping (String?) -> Void) { completion(nil) }
    func destroy() {}
}

import XCTest
@testable import WidgetSandbox

final class WebAnimationsTests: XCTestCase {
    private static let skeleton = "[{\"op\":\"el\",\"id\":2,\"tag\":\"html\"},{\"op\":\"el\",\"id\":3,\"tag\":\"body\"},{\"op\":\"ins\",\"parent\":2,\"id\":3,\"before\":null},{\"op\":\"el\",\"id\":7,\"tag\":\"div\"},{\"op\":\"ins\",\"parent\":3,\"id\":7,\"before\":null}]"

    private final class Box {
        var diags: [String] = []
    }

    private func validator(native: Bool = false, box: Box = Box(), policy: SandboxPolicy = .compat) throws -> TrustedValidator {
        let v = try TrustedValidator(theme: "light") { _, _ in }
        v.onDiag = { k, d in box.diags.append(k + "|" + d) }
        v.setPolicy(policy)
        if native { v.useNativeModel(true) }
        _ = v.processOps(Self.skeleton)
        return v
    }

    private func record(aid: Int = 1, target: Int = 7, keyframes: String = "[{\"offset\":0,\"easing\":\"linear\",\"composite\":null,\"values\":{\"opacity\":\"0\"}},{\"offset\":1,\"easing\":\"linear\",\"composite\":null,\"values\":{\"opacity\":\"1\"}}]", duration: String = "1000", delay: String = "0", iterations: String = "null", easing: String = "\"ease-in\"", direction: String = "\"normal\"", rate: String = "1", start: String = "1000", hold: String = "null", composite: String = "\"replace\"") -> String {
        "{\"aid\":\(aid),\"target\":\(target),\"keyframes\":\(keyframes),\"timing\":{\"duration\":\(duration),\"delay\":\(delay),\"endDelay\":0,\"iterations\":\(iterations),\"iterationStart\":0,\"direction\":\(direction),\"fill\":\"none\",\"easing\":\(easing)},\"composite\":\(composite),\"iterationComposite\":\"replace\",\"startTime\":\(start),\"holdTime\":\(hold),\"playbackRate\":\(rate)}"
    }

    private func parse(_ json: String) -> [[String: Any]] {
        (try? JSONSerialization.jsonObject(with: Data(json.utf8))) as? [[String: Any]] ?? []
    }

    private static let hostileValues = "{\"background-image\":\"url(javascript:alert(1))\",\"background\":\"url(https://evil.example/x) red\",\"width\":\"expression(alert(1))\",\"color\":\"red; background: url(x)\",\"--x\":\"url(https://evil.example/)\",\"behavior\":\"url(x.htc)\",\"-moz-binding\":\"url(x.xml#y)\",\"Opacity\":\"1\",\"transition-duration\":\"1s\",\"-webkit-animation-name\":\"x\",\"cursor\":\"url(x.cur), auto\",\"opacity\":\"0.5\",\"color\":\"light-dark(red, blue)\",\"offset\":\"0.9\",\"easing\":\"steps(99999999)\",\"composite\":\"x\",\"computed-offset\":\"1\"}"

    private func hostile() -> [String] {
        let many = "[" + (0..<65).map { "{\"offset\":\(Double($0) / 64),\"easing\":\"linear\",\"composite\":null,\"values\":{}}" }.joined(separator: ",") + "]"
        let manyValues = "[{\"offset\":0,\"easing\":\"linear\",\"composite\":null,\"values\":{" + (0..<65).map { "\"--v\($0)\":\"1\"" }.joined(separator: ",") + "}}]"
        return [
            record(target: 99),
            record(keyframes: many),
            record(keyframes: manyValues),
            record(duration: "1e12"),
            record(duration: "1e999"),
            record(duration: "-1"),
            record(delay: "-1e999"),
            record(iterations: "-1"),
            record(iterations: "\"Infinity\""),
            record(duration: "\"NaN\""),
            record(easing: "\"evil(1)\""),
            record(easing: "\"cubic-bezier(0, 0, 1, url(x))\""),
            record(easing: "\"linear(" + Array(repeating: "0", count: 300).joined(separator: ",") + ")\""),
            record(direction: "\"sideways\""),
            record(rate: "1000000"),
            record(start: "1e999"),
            record(start: "null", hold: "null"),
            record(composite: "\"multiply\""),
            record(keyframes: "[{\"offset\":0.8,\"easing\":\"linear\",\"composite\":null,\"values\":{}},{\"offset\":0.2,\"easing\":\"linear\",\"composite\":null,\"values\":{}}]"),
            record(keyframes: "[{\"offset\":1.5,\"easing\":\"linear\",\"composite\":null,\"values\":{}}]"),
            record(keyframes: "[{\"offset\":0,\"easing\":\"linear\",\"composite\":null,\"values\":[]}]"),
            record(keyframes: "\"opacity\""),
            "{\"aid\":-1}",
            "{\"aid\":1.5,\"remove\":true}",
            "{\"aid\":2147483648}",
            "null",
            "{\"aid\":2,\"target\":7,\"css\":{\"kind\":\"animation\",\"name\":\"a;b\",\"origin\":1},\"startTime\":null,\"holdTime\":0,\"playbackRate\":1,\"cancelled\":false}",
            "{\"aid\":2,\"target\":7,\"css\":{\"kind\":\"keyframes\",\"name\":\"a\",\"origin\":1},\"startTime\":null,\"holdTime\":0,\"playbackRate\":1,\"cancelled\":false}",
            "{\"aid\":2,\"target\":7,\"css\":null,\"startTime\":null,\"holdTime\":0,\"playbackRate\":1,\"cancelled\":false}",
        ]
    }

    func testValidatorPassesCleanRecordsAndSanitizesValues() throws {
        for native in [false, true] {
            let v = try validator(native: native)
            let frames = "[{\"offset\":0,\"easing\":\"linear\",\"composite\":null,\"values\":{\"opacity\":\"0\",\"background-image\":\"url(https://example.com/x.png)\",\"animation-name\":\"x\",\"transition\":\"all 1s\",\"color\":\"light-dark(red, blue)\"}},{\"offset\":1,\"easing\":\"steps(2, jump-none)\",\"composite\":\"add\",\"values\":{\"transform\":\"translateX(10px)\"}}]"
            let out = parse(v.validateAnim("[\(record(keyframes: frames))]"))
            XCTAssertEqual(out.count, 1)
            let values = ((out.first?["keyframes"] as? [[String: Any]])?.first?["values"] as? [String: String]) ?? [:]
            XCTAssertEqual(values, ["opacity": "0", "color": "red"], "native \(native)")
            XCTAssertEqual(out.first?["playbackRate"] as? Double, 1)
        }
    }

    func testValidatorRejectsHostileRecordsWithDiagnostics() throws {
        for native in [false, true] {
            for json in hostile() {
                let box = Box()
                let v = try validator(native: native, box: box)
                XCTAssertEqual(v.validateAnim("[\(json)]"), "[]", "native \(native) \(json)")
                if !json.contains("\"target\":99") {
                    XCTAssertTrue(v.thread.sync { box.diags.contains { $0.hasPrefix("protocol-violation") } }, "native \(native) \(json)")
                }
            }
            let box = Box()
            let v = try validator(native: native, box: box)
            XCTAssertEqual(v.validateAnim("{\"not\":\"a list\"}"), "[]")
            XCTAssertEqual(v.validateAnim("[" + Array(repeating: "1", count: 4097).joined(separator: ",") + "]"), "[]")
            XCTAssertEqual(v.validateAnim("[" + String(repeating: " ", count: 4 << 20) + "]"), "[]")
            XCTAssertEqual(v.thread.sync { box.diags.filter { $0.hasPrefix("protocol-violation") }.count }, 2, "native \(native)")
        }
    }

    func testValidatorDropsURLsExpressionsAndUnknownProperties() throws {
        for native in [false, true] {
            let v = try validator(native: native)
            let out = parse(v.validateAnim("[\(record(keyframes: "[{\"offset\":0,\"easing\":\"linear\",\"composite\":null,\"values\":\(Self.hostileValues)}]"))]"))
            XCTAssertEqual(out.count, 1)
            let values = ((out.first?["keyframes"] as? [[String: Any]])?.first?["values"] as? [String: String]) ?? [:]
            for (k, value) in values {
                XCTAssertNil(value.range(of: "url\\s*\\(|expression|javascript:", options: [.regularExpression, .caseInsensitive]), "\(k): \(value)")
            }
            for k in ["behavior", "-moz-binding", "Opacity", "transition-duration", "-webkit-animation-name", "width", "background-image", "offset", "easing", "composite", "computed-offset"] {
                XCTAssertNil(values[k], k)
            }
            XCTAssertEqual(values["opacity"], "0.5")
        }
    }

    func testNativeValidatorMatchesTheJSValidator() throws {
        let cases = hostile() + [
            record(),
            record(aid: 3, keyframes: "[{\"offset\":0,\"easing\":\"cubic-bezier(0.1, 0.2, 0.3, 1)\",\"composite\":\"add\",\"values\":\(Self.hostileValues)},{\"offset\":0.5,\"easing\":\"linear(0, 0.5 25%, 1)\",\"composite\":null,\"values\":{\"transform\":\"rotate(0.1deg) translate(1.5e-7px)\"}}]", iterations: "2.5", rate: "-0.25", start: "null", hold: "12.125"),
            "{\"aid\":4,\"target\":7,\"css\":{\"kind\":\"transition\",\"name\":\"opacity\",\"origin\":3},\"startTime\":1e3,\"holdTime\":null,\"playbackRate\":1,\"cancelled\":true}",
            "{\"aid\":3,\"remove\":true}",
            "{\"aid\":1,\"remove\":true}",
        ]
        let jsBox = Box(), nativeBox = Box()
        let js = try validator(box: jsBox)
        let native = try validator(native: true, box: nativeBox)
        for json in cases {
            jsBox.diags.removeAll()
            nativeBox.diags.removeAll()
            XCTAssertEqual(native.validateAnim("[\(json)]"), js.validateAnim("[\(json)]"), json)
            XCTAssertEqual(native.thread.sync { nativeBox.diags }, js.thread.sync { jsBox.diags }, json)
        }
        let batch = "[" + (1...1100).map { record(aid: $0) }.joined(separator: ",") + "]"
        XCTAssertEqual(native.validateAnim(batch), js.validateAnim(batch))
    }

    /// Strict: 64 live script animations (dropped past it with `animation-limit`, not a stop), on both validator paths.
    func testStrictCapsLiveAnimationsAt64() throws {
        for native in [false, true] {
            let box = Box()
            let v = try validator(native: native, box: box, policy: .strict)
            let batch = "[" + (1...74).map { record(aid: $0) }.joined(separator: ",") + "]"
            XCTAssertEqual(parse(v.validateAnim(batch)).count, 64, "native \(native)")
            XCTAssertTrue(v.thread.sync { box.diags.contains("animation-limit|more than 64 live script animations") }, "native \(native)")
            XCTAssertNil(v.stopped)
        }
    }

    func testValidatorCapsLiveAnimationsAndForwardsRemovals() throws {
        for native in [false, true] {
            let box = Box()
            let v = try validator(native: native, box: box)
            let batch = "[" + (1...1100).map { record(aid: $0) }.joined(separator: ",") + "]"
            XCTAssertEqual(parse(v.validateAnim(batch)).count, 1024)
            XCTAssertTrue(v.thread.sync { box.diags.contains { $0.hasPrefix("animation-limit") } })
            XCTAssertEqual(parse(v.validateAnim("[{\"aid\":5,\"remove\":true}]")).first?["remove"] as? Bool, true)
            XCTAssertEqual(v.validateAnim("[{\"aid\":5,\"remove\":true}]"), "[]")
            XCTAssertEqual(parse(v.validateAnim("[\(record(aid: 2000))]")).count, 1)
        }
    }

    func testAnimsQueryNeedsARenderedElement() throws {
        for native in [false, true] {
            let v = try validator(native: native)
            XCTAssertEqual(v.validateQuery("{\"q\":\"anims\",\"id\":7,\"subtree\":true}"), "{\"q\":\"anims\",\"id\":7,\"subtree\":true}")
            XCTAssertNil(v.validateQuery("{\"q\":\"anims\",\"id\":99}"))
        }
    }

    private final class Delegate: SandboxEngineDelegate {
        let lock = NSLock()
        var anims: [String] = []
        var logs: [String] = []
        var diags: [String] = []
        var frames = 0
        func engine(_ engine: SandboxEngine, ops json: String) {}
        func engine(_ engine: SandboxEngine, query json: String) -> (json: String, binary: Data?) { ("{\"list\":[]}", nil) }
        func engine(_ engine: SandboxEngine, listen json: String) {}
        func engine(_ engine: SandboxEngine, preventDefault json: String) {}
        func engineRequestsFrame(_ engine: SandboxEngine) { lock.withLock { frames += 1 } }
        func engine(_ engine: SandboxEngine, log level: String, text: String) { lock.withLock { logs.append(text) } }
        func engine(_ engine: SandboxEngine, diag kind: String, detail: String) { lock.withLock { diags.append(kind + "|" + detail) } }
        func engine(_ engine: SandboxEngine, bridge method: String, argsJson: String) {}
        func engineLoaded(_ engine: SandboxEngine) {}
        func engine(_ engine: SandboxEngine, stats: SandboxStats) {}
        func engine(_ engine: SandboxEngine, died reason: String) {}
        func engine(_ engine: SandboxEngine, anim json: String) { lock.withLock { anims.append(json) } }
    }

    private func wait(_ condition: () -> Bool) {
        let deadline = Date().addingTimeInterval(10)
        while !condition() && Date() < deadline {
            RunLoop.current.run(until: Date().addingTimeInterval(0.01))
        }
    }

    private static let widget = "<div id=a></div><script>const a=document.getElementById('a').animate({opacity:[0,1],backgroundColor:['red','blue']},{duration:500,iterations:Infinity,easing:'steps(2)'});a.ready.then(()=>console.log('ready',a.playState,a.startTime!==null));</script>"

    func testShimSendsPendingThenStartedRecords() {
        var config = SandboxConfig(innerWidth: 300, innerHeight: 200, devicePixelRatio: 2, theme: "light")
        config.features = ["webAnimations"]
        let engine = SandboxEngine(html: Self.widget, config: config)
        let d = Delegate()
        engine.delegate = d
        engine.start()
        wait { d.lock.withLock { d.anims.count >= 1 && d.frames >= 1 } }
        let first = d.lock.withLock { d.anims.first } ?? ""
        let pending = parse(first).first ?? [:]
        XCTAssertEqual(pending["holdTime"] as? Double, 0)
        XCTAssertTrue(pending["startTime"] is NSNull)
        XCTAssertTrue((pending["timing"] as? [String: Any])?["iterations"] is NSNull)
        XCTAssertEqual((pending["timing"] as? [String: Any])?["easing"] as? String, "steps(2)")
        let values = ((pending["keyframes"] as? [[String: Any]])?.last?["values"] as? [String: String]) ?? [:]
        XCTAssertEqual(values["background-color"], "blue")
        engine.frame(time: CACurrentMediaTime() * 1000)
        wait { d.lock.withLock { d.anims.count >= 2 && d.logs.contains { $0.hasPrefix("ready") } } }
        let started = parse(d.lock.withLock { d.anims.last } ?? "").first ?? [:]
        XCTAssertTrue(started["holdTime"] is NSNull)
        XCTAssertNotNil(started["startTime"] as? Double)
        XCTAssertEqual(d.lock.withLock { d.logs.first { $0.hasPrefix("ready") } }, "ready running true")
        engine.terminate()
    }

    /// A seek, then a layout query in the same task: the shim sends the anim batch before the query, the host keeps the two
    /// in order (anim used to be posted past the paced queue), and the renderer answers from the seeked frame.
    func testLayoutQueryAfterASeekAnswersFromTheNewFrame() throws {
        let html = "<div id=p style='width:20px;height:10px'></div><p id=out></p><script>setTimeout(function(){var el=document.getElementById('p'),a=el.animate([{width:'20px'},{width:'120px'}],{duration:1000,fill:'forwards'});a.pause();a.currentTime=250;document.getElementById('out').textContent='w='+Math.round(el.getBoundingClientRect().width);},50);</script>"
        for asyncValidation in [false, true] {
            let window = TestWindow.makeVisible()
            var options = WidgetHost.Options()
            options.features = ["webAnimations"]
            options.asyncValidation = asyncValidation
            let host = try WidgetHost(html: html, theme: WidgetTheme(), width: 360, options: options)
            window.attach(host.view)
            var text = ""
            let deadline = Date().addingTimeInterval(15)
            while Date() < deadline, !text.hasPrefix("w=") {
                var answer: String?
                host.evaluateInRenderer("document.getElementById('out')?.textContent") { answer = $0 }
                while answer == nil, Date() < deadline { RunLoop.main.run(until: Date().addingTimeInterval(0.02)) }
                text = answer ?? ""
            }
            XCTAssertEqual(text, "w=45", "asyncValidation \(asyncValidation)")
            XCTAssertNil(host.diagnostics.dead)
            host.destroy()
        }
    }

    func testShimReportsUnsupportedWithoutTheFeature() {
        let config = SandboxConfig(innerWidth: 300, innerHeight: 200, devicePixelRatio: 2, theme: "light")
        let engine = SandboxEngine(html: Self.widget, config: config)
        let d = Delegate()
        engine.delegate = d
        engine.start()
        wait { d.lock.withLock { d.logs.contains { $0.hasPrefix("ready") } } }
        engine.queue.sync {}
        XCTAssertEqual(d.lock.withLock { d.anims.count }, 0)
        XCTAssertTrue(d.lock.withLock { d.diags.contains { $0.hasPrefix("unsupported|Element.animate") } })
        engine.terminate()
    }
}

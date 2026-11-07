import XCTest
@testable import WidgetSandbox

/// Collects everything the engine emits; answers layout queries with canned values.
final class RecordingDelegate: SandboxEngineDelegate {
    let loaded = XCTestExpectation(description: "loaded")
    var ops: [String] = []
    var logs: [(String, String)] = []
    var diags: [(String, String)] = []
    var queries: [String] = []
    var bridge: [(String, String)] = []
    var actions: [WidgetAction] = []
    var storageCommits: [SdkPolicy.StorageCommit] = []
    var died: String?
    var frameRequests = 0
    /// Tasks (a timer's, an event's, a frame's, …) started after `engineLoaded`: the boot's are not counted.
    var tasksAfterLoad = 0
    private var isLoaded = false
    let lock = NSLock()

    func engineTaskStarted(_ engine: SandboxEngine) { lock.lock(); if isLoaded { tasksAfterLoad += 1 }; lock.unlock() }

    func engine(_ engine: SandboxEngine, ops json: String) { lock.lock(); ops.append(json); lock.unlock() }
    func engine(_ engine: SandboxEngine, query json: String) -> (json: String, binary: Data?) {
        lock.lock(); queries.append(json); lock.unlock()
        if json.contains("\"rect\"") { return ("{\"x\":0,\"y\":0,\"width\":100,\"height\":20,\"top\":0,\"left\":0,\"right\":100,\"bottom\":20}", nil) }
        if json.contains("\"matchMedia\"") { return ("{\"matches\":false}", nil) }
        return ("{}", nil)
    }
    func engine(_ engine: SandboxEngine, listen json: String) {}
    func engine(_ engine: SandboxEngine, preventDefault json: String) {}
    func engineRequestsFrame(_ engine: SandboxEngine) { lock.lock(); frameRequests += 1; lock.unlock() }
    func engine(_ engine: SandboxEngine, log level: String, text: String) { lock.lock(); logs.append((level, text)); lock.unlock() }
    func engine(_ engine: SandboxEngine, diag kind: String, detail: String) { lock.lock(); diags.append((kind, detail)); lock.unlock() }
    func engine(_ engine: SandboxEngine, bridge method: String, argsJson: String) { lock.lock(); bridge.append((method, argsJson)); lock.unlock() }
    func engine(_ engine: SandboxEngine, action: WidgetAction) { lock.lock(); actions.append(action); lock.unlock() }
    func engine(_ engine: SandboxEngine, storageCommit: SdkPolicy.StorageCommit) { lock.lock(); storageCommits.append(storageCommit); lock.unlock() }
    func engineLoaded(_ engine: SandboxEngine) { lock.lock(); isLoaded = true; lock.unlock(); loaded.fulfill() }
    func engine(_ engine: SandboxEngine, stats: SandboxStats) {}
    func engine(_ engine: SandboxEngine, died reason: String) { lock.lock(); died = reason; lock.unlock(); loaded.fulfill() }
}

final class SandboxEngineTests: XCTestCase {
    let canary = "http://127.0.0.1:9999/hit/ios-unit"

    private func run(_ html: String, cpuLimitMs: Double = 2000, timeout: TimeInterval = 20, configure: (inout SandboxConfig) -> Void = { _ in }) -> (RecordingDelegate, SandboxEngine) {
        let d = RecordingDelegate()
        var config = SandboxConfig(innerWidth: 400, innerHeight: 300, devicePixelRatio: 2, theme: "light")
        config.cpuLimitMs = cpuLimitMs
        configure(&config)
        let engine = SandboxEngine(html: html, config: config)
        engine.delegate = d
        engine.start()
        wait(for: [d.loaded], timeout: timeout)
        return (d, engine)
    }

    func testBootsAndEmitsInitialTree() {
        let (d, engine) = run("<p id=\"a\">hi</p><script>document.getElementById('a').textContent = 'changed by script'; console.log('hello from quickjs');</script>")
        engine.queue.sync {}
        XCTAssertNil(d.died)
        let all = d.ops.joined()
        XCTAssertTrue(all.contains("\"tag\":\"p\""), "initial tree should be sent")
        XCTAssertTrue(all.contains("changed by script"), "script mutation should be sent")
        XCTAssertTrue(d.logs.contains { $0.0 == "log" && $0.1 == "hello from quickjs" })
        XCTAssertFalse(d.logs.contains { $0.0 == "error" }, "no errors: \(d.logs)")
    }

    func testNetworkApisAreStubbed() {
        let (d, engine) = run("<script>var r = []; try { new WebSocket('ws://x') } catch (e) { r.push(e.name) } fetch('\(canary)').catch(e => r.push(e.name)).then(() => console.log(r.join(',')));</script>")
        engine.queue.sync {}
        XCTAssertTrue(d.logs.contains { $0.1 == "NetworkDisabledError,NetworkDisabledError" }, "\(d.logs)")
        XCTAssertTrue(d.diags.contains { $0.0 == "network-blocked" })
    }

    func testCpuLimitKillsInfiniteLoop() {
        let (d, engine) = run("<script>for(;;){}</script>", cpuLimitMs: 200, timeout: 20)
        engine.queue.sync {}
        XCTAssertNotNil(d.died)
        XCTAssertTrue(d.died?.contains("CPU limit") == true, d.died ?? "")
    }

    func testLayoutQueryRoundTrip() {
        let (d, engine) = run("<div id=\"d\">x</div><script>console.log('w=' + document.getElementById('d').getBoundingClientRect().width);</script>")
        engine.queue.sync {}
        XCTAssertTrue(d.queries.contains { $0.contains("\"rect\"") })
        XCTAssertTrue(d.logs.contains { $0.1 == "w=100" }, "\(d.logs)")
    }

    func testInputsBeforeStartAreBufferedNotCrashing() {
        // The renderer reports its size before `ready`; WidgetHost forwards it before start().
        let d = RecordingDelegate()
        let engine = SandboxEngine(html: "<script>console.log('w=' + innerWidth + ' theme=' + TelegramWidget.theme);</script>", config: SandboxConfig(innerWidth: 400, innerHeight: 300, devicePixelRatio: 2, theme: "light"))
        engine.delegate = d
        engine.viewport(innerWidth: 360, innerHeight: 200, dpr: 3)
        engine.theme("dark")
        engine.frame(time: 1)
        engine.start()
        wait(for: [d.loaded], timeout: 20)
        engine.queue.sync {}
        XCTAssertNil(d.died)
        XCTAssertTrue(d.logs.contains { $0.1 == "w=400 theme=light" }, "boot sees the initial config: \(d.logs)")
    }

    /// `maxTimers` reaches the shim through the boot config: the 9th timer throws
    /// RangeError inside the widget, the first 8 still fire, and the engine's own
    /// backstop is never needed.
    func testTimerLimitIsEnforced() {
        let (d, engine) = run("<script>var fired = 0, err = ''; for (var i = 0; i < 8; i++) setTimeout(function () { if (++fired === 8) console.log('fired=' + fired); }, 5);" +
                              "try { setTimeout(function () {}, 5); } catch (e) { err = e.name; } try { setInterval(function () {}, 10); } catch (e) { err += ',' + e.name; } console.log('err=' + err);</script>") { $0.maxTimers = 8 }
        let deadline = Date().addingTimeInterval(3)
        while Date() < deadline, !(engine.queue.sync { d.logs.contains { $0.1 == "fired=8" } }) { RunLoop.current.run(until: Date().addingTimeInterval(0.05)) }
        XCTAssertTrue(d.logs.contains { $0.1 == "err=RangeError,RangeError" }, "\(d.logs)")
        XCTAssertTrue(d.logs.contains { $0.1 == "fired=8" }, "\(d.logs)")
        XCTAssertEqual(d.diags.filter { $0.0 == "timer-limit" }.count, 1, "\(d.diags)")
        XCTAssertFalse(d.diags.contains { $0.0 == "timer-limit-enforced" }, "the engine backstop must not be needed: \(d.diags)")
        XCTAssertNil(d.died)
    }

    /// The shim serialises a timer's `[id, delay]` with `JSON.stringify`, which a widget may replace: here every
    /// `timer` host call names one id. The glue replaces that id's timer, cancelling the one before: of 1000 timers
    /// asked for, one fires, and no more than `maxTimers` native timers ever run (the old handle was overwritten and its
    /// timer left running, past the cap and after destroy). The shim's own list stays under its cap: its clearTimeout
    /// names an id the glue never saw.
    func testATimerIdAskedForAgainReplacesItsTimer() {
        let html = """
            <script>
            var stringify = JSON.stringify, fixed = 0, hijack = true, fired = 0, set = 0;
            JSON.stringify = function (v) {
              if (hijack && Array.isArray(v) && v.length === 2 && typeof v[0] === 'number' && typeof v[1] === 'number') {
                if (!fixed) fixed = v[0];
                return stringify([fixed, v[1]]);
              }
              return stringify.apply(JSON, arguments);
            };
            var replaced = JSON.stringify !== stringify;
            for (var i = 0; i < 1000; i++) {
              var t = setTimeout(function () { fired++; console.log('fired ' + fired); }, 100);
              set++;
              if (t !== fixed) clearTimeout(t);
            }
            hijack = false;
            console.log('set ' + set + ' replaced ' + replaced);
            </script>
            """
        let (d, engine) = run(html) { $0.maxTimers = 8 }
        let deadline = Date().addingTimeInterval(3)
        while Date() < deadline, !(engine.queue.sync { d.logs.contains { $0.1 == "fired 1" } }) { RunLoop.current.run(until: Date().addingTimeInterval(0.02)) }
        RunLoop.current.run(until: Date().addingTimeInterval(0.6))
        engine.queue.sync {}
        d.lock.lock()
        let logs = d.logs.map { $0.1 }, tasks = d.tasksAfterLoad, diags = d.diags
        d.lock.unlock()
        XCTAssertTrue(logs.contains("set 1000 replaced true"), "\(logs.prefix(5))")
        XCTAssertEqual(logs.filter { $0.hasPrefix("fired") }, ["fired 1"])
        XCTAssertEqual(tasks, 1, "one native timer fired; each one left running is a task")
        XCTAssertLessThanOrEqual(tasks, engine.config.maxTimers)
        XCTAssertFalse(diags.contains { $0.0 == "timer-limit-enforced" }, "\(diags)")
        XCTAssertNil(d.died)
        engine.terminate()
    }

    /// A replaced id does not count against `maxTimers`: with the glue's list full (eight timers the shim does not know
    /// of), a timer for an id the list holds replaces it, and only a ninth id is refused (`timer-limit-enforced`).
    func testAReplacedTimerIdDoesNotCountAgainstTheCap() {
        func widget(_ last: Int) -> String {
            """
            <script>
            var stringify = JSON.stringify, next = 1000, as = -1;
            JSON.stringify = function (v) {
              if (as !== -1 && Array.isArray(v) && v.length === 2 && typeof v[0] === 'number') return stringify([as === 0 ? next++ : as, v[1]]);
              return stringify.apply(JSON, arguments);
            };
            function ask(id) { as = id; var t = setTimeout(function () {}, 10000); as = -1; clearTimeout(t); }
            for (var i = 0; i < 8; i++) ask(0);
            ask(\(last));
            console.log('asked ' + next);
            </script>
            """
        }
        for (last, refused) in [(1000, false), (1007, false), (0, true)] {
            let (d, engine) = run(widget(last)) { $0.maxTimers = 8 }
            engine.queue.sync {}
            d.lock.lock()
            XCTAssertTrue(d.logs.contains { $0.1 == "asked \(last == 0 ? 1009 : 1008)" }, "\(d.logs)")
            XCTAssertEqual(d.diags.filter { $0.0 == "timer-limit-enforced" }.count, refused ? 1 : 0, "id \(last): \(d.diags)")
            XCTAssertNil(d.died)
            d.lock.unlock()
            engine.terminate()
        }
    }

    /// A 100 ms interval burning 40 ms per tick is legal under cpuLimitMs alone;
    /// the quota (here 100 ms/s so a debug build of quickjs-ng gets there quickly)
    /// defers ticks instead of killing, reports `cpu-throttled`, and a click
    /// still gets through.
    func testCpuQuotaThrottlesGreedyTimer() {
        let (d, engine) = run("<p id=\"p\">x</p><script>var ticks = 0; setInterval(function () { var t = performance.now(); while (performance.now() - t < 40) {} ticks++; }, 100);" +
                              "document.addEventListener('click', function () { console.log('click at tick ' + ticks); });</script>", cpuLimitMs: 2000) { $0.cpuQuotaMs = 100 }
        RunLoop.current.run(until: Date().addingTimeInterval(2.5))
        engine.event(json: "{\"type\":\"click\",\"targetId\":2}")
        engine.queue.sync {}
        XCTAssertNil(d.died)
        XCTAssertTrue(d.diags.contains { $0.0 == "cpu-throttled" }, "\(d.diags)")
        XCTAssertTrue(d.logs.contains { $0.1.hasPrefix("click at tick") }, "events run under throttling: \(d.logs)")
        let ticks = Int(d.logs.last { $0.1.hasPrefix("click at tick") }!.1.split(separator: " ").last!) ?? -1
        // Unthrottled the interval would tick ~25 times in 2.5 s; 100 ms/s allows 2–3 per second.
        XCTAssertGreaterThan(ticks, 1)
        XCTAssertLessThan(ticks, 12, "\(d.logs)")
    }

    /// Hidden: frames are neither requested nor delivered; on return the one
    /// pending request is made and the shim saw `visibilitychange` both ways.
    func testHiddenEngineDeliversNoFrames() {
        let (d, engine) = run("<script>var n = 0; function step() { n++; console.log('frame ' + n); requestAnimationFrame(step); } requestAnimationFrame(step);" +
                              "document.addEventListener('visibilitychange', function () { console.log('vis=' + document.visibilityState); });</script>")
        engine.queue.sync {}
        XCTAssertEqual(d.frameRequests, 1)
        engine.frame(time: 1)
        engine.queue.sync {}
        XCTAssertEqual(d.frameRequests, 2)
        XCTAssertTrue(d.logs.contains { $0.1 == "frame 1" })
        engine.setVisible(false)
        engine.frame(time: 2) // in flight when the widget was hidden: dropped
        engine.queue.sync {}
        XCTAssertEqual(d.frameRequests, 2)
        XCTAssertFalse(d.logs.contains { $0.1 == "frame 2" })
        XCTAssertTrue(d.logs.contains { $0.1 == "vis=hidden" }, "\(d.logs)")
        engine.setVisible(true)
        engine.queue.sync {}
        XCTAssertEqual(d.frameRequests, 3, "the pending request is made on return")
        XCTAssertTrue(d.logs.contains { $0.1 == "vis=visible" }, "\(d.logs)")
        engine.frame(time: 3)
        engine.queue.sync {}
        XCTAssertTrue(d.logs.contains { $0.1 == "frame 2" })
        XCTAssertNil(d.died)
    }

    /// Hidden: a 50 ms interval ticks at most once per second; visible again it runs at full rate.
    func testHiddenTimersAreCoalescedToOneHertz() {
        let (d, engine) = run("<script>var t = 0; setInterval(function () { t++; console.log('tick ' + t); }, 50);</script>")
        engine.setVisible(false)
        RunLoop.current.run(until: Date().addingTimeInterval(1.6))
        let hiddenTicks = engine.queue.sync { d.logs.filter { $0.1.hasPrefix("tick") }.count }
        XCTAssertGreaterThanOrEqual(hiddenTicks, 1)
        XCTAssertLessThanOrEqual(hiddenTicks, 2, "\(d.logs)")
        engine.setVisible(true)
        RunLoop.current.run(until: Date().addingTimeInterval(1.6))
        let visibleTicks = engine.queue.sync { d.logs.filter { $0.1.hasPrefix("tick") }.count } - hiddenTicks
        XCTAssertGreaterThanOrEqual(visibleTicks, 5, "\(d.logs)")
        XCTAssertNil(d.died)
    }

    /// Hidden: the timers still run in the order of their delays (all at about 1 s; setTimeout 20, 10, 0 → 0, 10, 20).
    func testHiddenTimersKeepTheirOrder() {
        let (d, engine) = run("<script>document.addEventListener('visibilitychange', function () { if (!document.hidden) return; [20, 10, 0].forEach(function (d) { setTimeout(function () { console.log('order ' + d); }, d); }); });</script>")
        engine.setVisible(false)
        // Hidden timers fire at the next 1 Hz wake-up after their delay: up to 2 s.
        let orders = { engine.queue.sync { d.logs.map { $0.1 }.filter { $0.hasPrefix("order ") }.map { String($0.dropFirst(6)) } } }
        let deadline = Date().addingTimeInterval(4)
        while Date() < deadline, orders().count < 3 { RunLoop.current.run(until: Date().addingTimeInterval(0.05)) }
        let order = orders()
        XCTAssertEqual(order, ["0", "10", "20"], "\(d.logs)")
        XCTAssertNil(d.died)
    }

    func testCompatPolicyForwardsEveryBridgeCall() {
        let (d, engine) = run("<script>TelegramWidget.ready(); setTimeout(() => TelegramWidget.openTelegramLink('https://t.me/later'), 30); Promise.resolve().then(() => TelegramWidget.openLink('https://example.com/boot'));</script>", configure: { $0.policy = .compat })
        let deadline = Date().addingTimeInterval(3)
        while Date() < deadline, !(engine.queue.sync { d.bridge.contains { $0.0 == "openTelegramLink" } }) { RunLoop.current.run(until: Date().addingTimeInterval(0.05)) }
        engine.queue.sync {}
        XCTAssertEqual(engine.queue.sync { d.bridge.map { $0.0 } }.sorted(), ["openLink", "openTelegramLink", "ready"])
        XCTAssertEqual(engine.queue.sync { d.actions }, [.ready, .openLink(URL(string: "https://example.com/boot")!), .openTelegramLink(URL(string: "https://t.me/later")!)])
        XCTAssertFalse(engine.queue.sync { d.diags.contains { $0.0 == "activation-blocked" } })
    }

    func testPolicyIsStrictByDefault() {
        XCTAssertEqual(SandboxConfig(innerWidth: 1, innerHeight: 1, devicePixelRatio: 1, theme: "light").policy, .strict)
        XCTAssertEqual(WidgetHost.Options().policy, .strict)
    }

    /// `ready` needs no gesture; a privileged call from a timer is refused and reported,
    /// the same call from the handler of a trusted click goes through once (the default policy).
    func testBridgeAndTimers() {
        let (d, engine) = run("<button id='b'>go</button><script>TelegramWidget.ready(); setTimeout(() => TelegramWidget.openLink('https://example.com/later'), 30); document.getElementById('b').addEventListener('click', () => { TelegramWidget.openLink('https://example.com/clicked'); TelegramWidget.openTelegramLink('https://t.me/again'); });</script>")
        let deadline = Date().addingTimeInterval(3)
        while Date() < deadline, !(engine.queue.sync { d.diags.contains { $0.0 == "activation-blocked" } }) { RunLoop.current.run(until: Date().addingTimeInterval(0.05)) }
        XCTAssertTrue(engine.queue.sync { d.bridge.contains { $0.0 == "ready" } })
        XCTAssertFalse(engine.queue.sync { d.bridge.contains { $0.0 == "openLink" } })
        XCTAssertTrue(engine.queue.sync { d.diags.contains { $0.0 == "activation-blocked" && $0.1.contains("timer") } })
        engine.activationEvent(json: "{\"type\":\"click\",\"targetId\":5}", gesture: 1)
        let clicked = Date().addingTimeInterval(3)
        while Date() < clicked, !(engine.queue.sync { d.bridge.contains { $0.0 == "openLink" } }) { RunLoop.current.run(until: Date().addingTimeInterval(0.05)) }
        engine.queue.sync {}
        XCTAssertEqual(engine.queue.sync { d.bridge.filter { $0.0 != "ready" }.map { $0.1 } }, ["[\"https:\\/\\/example.com\\/clicked\"]"], "one press unlocks one call, shared by both link methods")
        XCTAssertEqual(engine.queue.sync { d.actions }, [.ready, .openLink(URL(string: "https://example.com/clicked")!)])
    }
}

final class QuickJSContextTests: XCTestCase {
    /// A serial DispatchQueue hops between pool threads; the runtime must re-read the
    /// stack top on every entry or later calls fail with a bogus RangeError (seen on
    /// the simulator when the theme toggle fired after d3 had loaded).
    func testStackCheckFollowsTheCallingThread() throws {
        // Depth reachable before QuickJS reports overflow (it throws RangeError, never crashes).
        let probe = "(function(){ var n = 0; function r(){ n++; r(); } try { r(); } catch (e) { if (!(e instanceof RangeError)) throw e; } return n; })()"
        var js: QuickJSContext?
        var depthOnCreator = 0.0
        let made = expectation(description: "created on another thread")
        let t = Thread {
            js = QuickJSContext(memoryLimitBytes: 16 << 20)
            depthOnCreator = (try? js!.evaluate(probe, filename: "t.js"))?.number ?? -1
            made.fulfill()
        }
        t.stackSize = 4 << 20
        t.start()
        wait(for: [made], timeout: 5)
        // Enter from the test thread, whose stack lives at a different address: the
        // reachable depth must be the same, not ~0 (bogus overflow) and not unbounded.
        let depthHere = try js!.evaluate(probe, filename: "t.js").number ?? -1
        print("quickjs recursion depth at default stack limit: creator \(depthOnCreator), here \(depthHere)")
        XCTAssertGreaterThan(depthOnCreator, 20)
        XCTAssertGreaterThan(depthHere, 20)
        XCTAssertLessThan(abs(depthHere - depthOnCreator) / depthOnCreator, 0.1)
    }

    /// Product contexts live on a JSThread (8 MB stack, 6 MB QuickJS limit): a debug
    /// build of quickjs-ng needs ~10 KB per JS call, so this must comfortably exceed
    /// what css-tree and chart libraries recurse to.
    func testPinnedThreadGivesDeepRecursion() {
        let t = JSThread(name: "test.js")
        let depth: Double = t.sync {
            let js = QuickJSContext(memoryLimitBytes: 16 << 20, maxStackBytes: JSThread.jsStackLimit)
            let probe = "(function(){ var n = 0; function r(){ n++; r(); } try { r(); } catch (e) { if (!(e instanceof RangeError)) throw e; } return n; })()"
            return (try? js.evaluate(probe, filename: "t.js"))?.number ?? -1
        }
        print("quickjs recursion depth on JSThread: \(depth)")
        XCTAssertGreaterThan(depth, 300)
        // Timers and ordering: `after` fires on the thread, `cancel` prevents it.
        let fired = expectation(description: "timer fired")
        var cancelledRan = false
        let c = t.after(0.01) { cancelledRan = true }
        c.cancel()
        _ = t.after(0.03) { XCTAssertTrue(t.isCurrent); fired.fulfill() }
        wait(for: [fired], timeout: 2)
        XCTAssertFalse(cancelledRan)
        t.stop()
    }
}

final class TrustedValidatorTests: XCTestCase {
    func testDiagBridge() throws {
        var diags: [(String, String)] = []
        let v = try TrustedValidator(theme: "light") { diags.append(($0, $1)) }
        // An unknown element is reported through __diag (tag-replaced).
        _ = v.processOps("[{\"op\":\"el\",\"id\":2,\"tag\":\"x-foo\"}]")
        XCTAssertTrue(diags.contains { $0.0 == "tag-replaced" }, "diags=\(diags)")
    }

    func testStripsExternalUrlsAndHandlers() throws {
        var diags: [(String, String)] = []
        let v = try TrustedValidator(theme: "light") { diags.append(($0, $1)) }
        let ops = """
        [{"op":"el","id":2,"tag":"html"},{"op":"ins","parent":1,"id":2,"before":null},
         {"op":"el","id":3,"tag":"body"},{"op":"ins","parent":2,"id":3,"before":null},
         {"op":"el","id":4,"tag":"img","attrs":{"src":"http://127.0.0.1:9999/hit/x","onerror":"fetch('http://127.0.0.1:9999/e')"}},{"op":"ins","parent":3,"id":4,"before":null},
         {"op":"el","id":5,"tag":"iframe","attrs":{"src":"http://127.0.0.1:9999/hit/f"}},{"op":"ins","parent":3,"id":5,"before":null},
         {"op":"el","id":6,"tag":"style"},{"op":"ins","parent":3,"id":6,"before":null},
         {"op":"text","id":7,"data":"@import url(http://127.0.0.1:9999/i); p{color:red;background:url(http://127.0.0.1:9999/b)}"},{"op":"ins","parent":3,"id":7,"before":null}]
        """
        let out = v.processOps(ops)
        let rendered = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(out.utf8)) as? [[String: Any]])
        // Nothing that is attached, set as an attribute or emitted as CSS may carry the canary.
        let attached = rendered.filter { ["ins", "attr", "css", "style"].contains($0["op"] as? String ?? "") || $0["op"] as? String == "el" }
        let attachedJson = String(decoding: try JSONSerialization.data(withJSONObject: attached), as: UTF8.self)
        XCTAssertFalse(attachedJson.contains("127.0.0.1"), attachedJson)
        XCTAssertFalse(attachedJson.lowercased().contains("onerror"), attachedJson)
        XCTAssertFalse(attachedJson.contains("\"tag\":\"iframe\""), attachedJson)
        XCTAssertTrue(out.contains("data-tg-placeholder"), out)
        XCTAssertTrue(diags.contains { $0.0 == "url-blocked" }, "diags=\(diags)")
        XCTAssertEqual(v.validateQuery("{\"q\":\"rect\",\"id\":4}"), "{\"q\":\"rect\",\"id\":4}")
        XCTAssertNil(v.validateQuery("{\"q\":\"eval\",\"code\":\"1\"}"))
        XCTAssertEqual(v.rewriteMediaQuery("(prefers-color-scheme: dark)"), "(min-width: 99999px)")
    }

    /// Opening the second widget in the demo crashed once in `JSThread.sync`
    /// (non-escaping closure retained by CFRunLoop past the semaphore): churn
    /// validators, each with its own thread, and let them tear down.
    func testRepeatedCreateAndTeardown() throws {
        for i in 0..<25 {
            let v = try TrustedValidator(theme: i % 2 == 0 ? "light" : "dark") { _, _ in }
            XCTAssertEqual(v.processOps("[{\"op\":\"el\",\"id\":2,\"tag\":\"p\"},{\"op\":\"ins\",\"parent\":1,\"id\":2,\"before\":null}]").contains("\"tag\":\"p\""), true)
            XCTAssertEqual(v.nodeCount, 2)
        }
    }

    func testStylesheetTextIsOnlyShippedSanitized() throws {
        var diags: [String] = []
        let v = try TrustedValidator(theme: "dark") { diags.append("\($0): \($1)") }
        let ops = """
        [{"op":"el","id":2,"tag":"html"},{"op":"ins","parent":1,"id":2,"before":null},
         {"op":"el","id":6,"tag":"style"},{"op":"ins","parent":2,"id":6,"before":null},
         {"op":"text","id":7,"data":"@import url(http://127.0.0.1:9999/i); @media (prefers-color-scheme: dark){p{color:red}} p{background:url(http://127.0.0.1:9999/b)}"},{"op":"ins","parent":6,"id":7,"before":null}]
        """
        let out = v.processOps(ops)
        XCTAssertTrue(out.contains("{\"op\":\"rm\",\"id\":7}"), out)
        XCTAssertTrue(out.contains("@media (min-width: 0px){p{color:red}}"), "\(out)\n\(diags)")
        // The two external URLs are reported through __diag (from the validator's thread).
        XCTAssertTrue(diags.contains { $0.hasPrefix("url-blocked") || $0.hasPrefix("css-dropped") }, "\(diags)")
        XCTAssertFalse(out.contains("\"css\",\"id\":6,\"text\":\"@import"), out)
        XCTAssertEqual(v.nodeCount, 4)
    }
}


/// Precompiled bundles (`.qbc`, built by `pnpm build:ios` next to each `.js`).
final class BytecodeTests: XCTestCase {
    func testRoundTripAndStaleBlobRefused() throws {
        let js = QuickJSContext(memoryLimitBytes: 16 << 20)
        let blob = try js.compileBytecode("globalThis.answer = 6 * 7;", filename: "t.js")
        XCTAssertGreaterThan(blob.count, 24)
        try js.evaluateBytecode(blob, source: "globalThis.answer = 6 * 7;")
        XCTAssertEqual(try js.evaluate("answer", filename: "t.js").number, 42)
        XCTAssertThrowsError(try js.evaluateBytecode(blob, source: "globalThis.answer = 6 * 8;")) { XCTAssertTrue("\($0)".contains("different source"), "\($0)") }
        var bad = blob
        bad[0] = 0x58
        XCTAssertThrowsError(try js.evaluateBytecode(bad, source: "globalThis.answer = 6 * 7;"))
        XCTAssertThrowsError(try js.evaluateBytecode(Data(), source: ""))
    }

    /// The shipped blobs load against the .js next to them (a stale pair fails here, not on a device);
    /// a CDN script is an external script like any other (profile 1 bundles no libraries).
    func testShippedBytecodeIsUsedAndNoLibraryIsBundled() throws {
        let r = SandboxResources.shared
        XCTAssertNotNil(r.sandboxBytecode, "pnpm build:ios ships sandbox-bundle.qbc")
        XCTAssertNotNil(r.validatorBytecode, "pnpm build:ios ships validator-bundle.qbc")
        let d = RecordingDelegate()
        var config = SandboxConfig(innerWidth: 400, innerHeight: 300, devicePixelRatio: 2, theme: "light")
        config.cpuLimitMs = 2000
        let engine = SandboxEngine(html: "<canvas id=\"c\"></canvas><script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script><script>console.log(typeof Chart);</script>", config: config)
        engine.delegate = d
        engine.start()
        wait(for: [d.loaded], timeout: 20)
        engine.queue.sync {}
        XCTAssertNil(d.died)
        XCTAssertTrue(d.diags.contains { $0.0 == "boot" && $0.1.contains("(bytecode)") }, "\(d.diags)")
        XCTAssertTrue(d.diags.contains { $0.0 == "script-skipped" && $0.1.contains("chart.js") }, "\(d.diags)")
        XCTAssertFalse(d.diags.contains { $0.0 == "bytecode" }, "fell back to source: \(d.diags)")
        XCTAssertTrue(d.logs.contains { $0.1 == "undefined" }, "\(d.logs)")
        var vdiags: [(String, String)] = []
        _ = try TrustedValidator(theme: "light") { vdiags.append(($0, $1)) }
        XCTAssertFalse(vdiags.contains { $0.0 == "bytecode" }, "validator fell back to source: \(vdiags)")
    }
}

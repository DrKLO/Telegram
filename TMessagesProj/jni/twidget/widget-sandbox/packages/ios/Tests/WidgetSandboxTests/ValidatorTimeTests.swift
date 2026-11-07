import XCTest
@testable import WidgetSandbox

/// The validator's time counts against the widget's JS quota (docs/decisions.md, "The validator's time counts"): the
/// validator measures its thread's CPU time per call for the widget's messages, WidgetHost hands it to the engine, the
/// engine records it like the widget's own JS time. The twins: desktop `validator_time_counts_against_the_js_quota`,
/// `widget_host_charges_the_validators_time`, Android ValidatorCpuTest.
final class ValidatorTimeTests: XCTestCase {
    private final class Ticks: SandboxEngineDelegate {
        let loaded = XCTestExpectation(description: "loaded")
        let lock = NSLock()
        var ticks = 0
        var diags: [(String, String)] = []
        var stats = SandboxStats()
        var died: String?
        func engine(_ engine: SandboxEngine, ops json: String) {}
        func engine(_ engine: SandboxEngine, query json: String) -> (json: String, binary: Data?) { ("{}", nil) }
        func engine(_ engine: SandboxEngine, listen json: String) {}
        func engine(_ engine: SandboxEngine, preventDefault json: String) {}
        func engineRequestsFrame(_ engine: SandboxEngine) {}
        func engine(_ engine: SandboxEngine, log level: String, text: String) {
            if text == "tick" { lock.withLock { ticks += 1 } }
        }
        func engine(_ engine: SandboxEngine, diag kind: String, detail: String) { lock.withLock { diags.append((kind, detail)) } }
        func engine(_ engine: SandboxEngine, bridge method: String, argsJson: String) {}
        func engineLoaded(_ engine: SandboxEngine) { loaded.fulfill() }
        func engine(_ engine: SandboxEngine, stats: SandboxStats) { lock.withLock { self.stats = stats } }
        func engine(_ engine: SandboxEngine, died reason: String) { lock.withLock { died = reason }; loaded.fulfill() }
    }

    /// A charge from another thread lands in the stats and defers the widget's timers as its own JS time would: 300 ms of
    /// validation against a 100 ms quota fills the window and leaves a debt of one more quota.
    func testAChargeDefersTimersLikeJSTime() {
        func run(charge: Double) -> (ticks: Int, validatorMs: Double, throttled: [String], died: String?) {
            let d = Ticks()
            var config = SandboxConfig(innerWidth: 300, innerHeight: 200, devicePixelRatio: 2, theme: "light")
            config.cpuLimitMs = 2000
            config.cpuQuotaMs = 100
            let engine = SandboxEngine(html: "<script>setInterval(function () { console.log('tick'); }, 20);</script>", config: config)
            engine.delegate = d
            engine.start()
            wait(for: [d.loaded], timeout: 20)
            let before = d.lock.withLock { d.ticks }
            if charge > 0 { DispatchQueue.global().sync { engine.chargeValidator(ms: charge) } }
            RunLoop.current.run(until: Date().addingTimeInterval(0.6))
            engine.queue.sync {}
            let ticks = d.lock.withLock { d.ticks } - before
            RunLoop.current.run(until: Date().addingTimeInterval(0.25)) // the trailing stats report
            engine.queue.sync {}
            let result = d.lock.withLock { (ticks, d.stats.validatorMs, d.diags.filter { $0.0 == "cpu-throttled" }.map(\.1), d.died) }
            engine.terminate()
            return result
        }
        let free = run(charge: 0)
        XCTAssertNil(free.died)
        XCTAssertGreaterThan(free.ticks, 10)
        XCTAssertEqual(free.validatorMs, 0)
        XCTAssertTrue(free.throttled.isEmpty, "\(free.throttled)")
        let charged = run(charge: 300)
        XCTAssertNil(charged.died)
        XCTAssertLessThan(charged.ticks, 3, "ticks after 300 ms of validation against a 100 ms quota")
        XCTAssertEqual(charged.validatorMs, 300)
        XCTAssertTrue(charged.throttled.contains { $0.contains("ms per 1000 ms budget used") }, "\(charged.throttled)")
    }

    /// The validator reports each call made for the widget's messages once (a call inside a measured one, `processOps`
    /// in `processOpsForRenderer`, is part of it), not the host's own calls (`setTheme`), and nothing without a sink.
    func testTheValidatorMeasuresEachCallForTheWidgetOnce() throws {
        let v = try TrustedValidator(theme: "light") { _, _ in }
        let lock = NSLock()
        var calls: [Double] = []
        v.setCpuSink { ms in lock.withLock { calls.append(ms) } }
        let batch = "[{\"op\":\"el\",\"id\":10,\"tag\":\"div\"},{\"op\":\"style\",\"id\":10,\"name\":\"color\",\"value\":\"var(--x, red)\"}]"
        _ = v.processOps(batch)
        XCTAssertEqual(lock.withLock { calls.count }, 1)
        _ = v.processOpsForRenderer("[{\"op\":\"style\",\"id\":10,\"name\":\"width\",\"value\":\"calc(100% - 1px)\"}]", typed: false)
        XCTAssertEqual(lock.withLock { calls.count }, 2)
        _ = v.processOpsCounted("[{\"op\":\"attr\",\"id\":10,\"name\":\"title\",\"value\":\"t\"}]")
        XCTAssertEqual(lock.withLock { calls.count }, 3)
        _ = v.validateQuery("{\"kind\":\"rect\",\"id\":10}")
        _ = v.validateListen("[\"click\"]")
        _ = v.validateAnim("[]")
        XCTAssertEqual(lock.withLock { calls.count }, 6)
        _ = v.setTheme("dark")
        XCTAssertEqual(lock.withLock { calls.count }, 6, "the host's theme change is not the widget's")
        XCTAssertTrue(lock.withLock { calls.allSatisfy { $0 >= 0 && $0 < 1000 } }, "\(calls)")
        v.setCpuSink(nil)
        _ = v.processOps(batch)
        XCTAssertEqual(lock.withLock { calls.count }, 6)
        v.setCpuSink { ms in lock.withLock { calls.append(ms) } }
        v.reset(theme: "light")
        _ = v.processOps(batch)
        XCTAssertEqual(lock.withLock { calls.count }, 6, "a recycled validator measures for nobody")
    }

    /// WidgetHost wires the two: style values css-tree has to parse show up in `stats.validatorMs`.
    func testWidgetHostChargesTheValidatorsTime() throws {
        let html = "<div id=d></div><script>var d = document.getElementById('d');" +
            "for (var i = 0; i < 300; i++) d.style.setProperty('--v' + (i % 7), 'var(--x, ' + i + 'px) calc(' + i + 'px + 1em)');" +
            "TelegramWidget.ready();</script>"
        let renderer = RecordingRenderer()
        let host = try WidgetHost(html: html, theme: WidgetTheme(), width: 300, options: WidgetHost.Options(), rendererFactory: { _, _, _ in renderer })
        defer { host.destroy() }
        let deadline = Date().addingTimeInterval(20)
        while host.diagnostics.stats.validatorMs <= 0 && host.diagnostics.dead == nil && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.02))
        }
        let d = host.diagnostics
        XCTAssertNil(d.dead)
        XCTAssertGreaterThan(d.stats.validatorMs, 0)
        XCTAssertLessThan(d.stats.validatorMs, 2000)
    }
}

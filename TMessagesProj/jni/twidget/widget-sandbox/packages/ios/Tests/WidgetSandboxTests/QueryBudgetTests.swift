import XCTest
@testable import WidgetSandbox

final class QueryBudgetTests: XCTestCase {
    private final class SlowHost: SandboxEngineDelegate {
        var died: String?
        var queries = 0
        var logs: [String] = []
        let delay: TimeInterval
        init(delay: TimeInterval) { self.delay = delay }
        func engine(_ engine: SandboxEngine, ops json: String) {}
        func engine(_ engine: SandboxEngine, query json: String) -> (json: String, binary: Data?) {
            queries += 1
            Thread.sleep(forTimeInterval: delay)
            return ("null", nil)
        }
        func engine(_ engine: SandboxEngine, listen json: String) {}
        func engine(_ engine: SandboxEngine, preventDefault json: String) {}
        func engineRequestsFrame(_ engine: SandboxEngine) {}
        func engine(_ engine: SandboxEngine, log level: String, text: String) { logs.append(text) }
        func engine(_ engine: SandboxEngine, diag kind: String, detail: String) {}
        func engine(_ engine: SandboxEngine, bridge method: String, argsJson: String) {}
        func engineLoaded(_ engine: SandboxEngine) {}
        func engine(_ engine: SandboxEngine, stats: SandboxStats) {}
        func engine(_ engine: SandboxEngine, died reason: String) { died = reason }
    }

    private func run(_ script: String, delay: TimeInterval, wait: TimeInterval) -> (engine: SandboxEngine, host: SlowHost) {
        var config = SandboxConfig(innerWidth: 300, innerHeight: 200, devicePixelRatio: 2, theme: "light")
        config.cpuLimitMs = 200
        let engine = SandboxEngine(html: "<div id=a></div><script>\(script)</script>", config: config)
        let host = SlowHost(delay: delay)
        engine.delegate = host
        engine.start()
        let deadline = Date().addingTimeInterval(wait)
        while engine.queue.sync { host.died } == nil && Date() < deadline {
            RunLoop.current.run(until: Date().addingTimeInterval(0.01))
        }
        return (engine, host)
    }

    func testForcedLayoutLoopIsKilledInsteadOfBlockingTheHost() {
        let started = Date()
        let (engine, host) = run("const a = document.getElementById('a'); let i = 0; for (;;) { a.style.width = (i++ % 300) + 'px'; a.offsetHeight; }", delay: 0.005, wait: 10)
        let died = engine.queue.sync { host.died }
        XCTAssertTrue(died?.hasPrefix("Layout query limit exceeded") ?? false, died ?? "alive")
        // 128 answered queries whatever the slice (the boot task here has 200 ms of CPU); 129 × 5 ms stays under the 1000 ms guard.
        XCTAssertTrue(died?.contains("more than 128 layout queries in one boot task") ?? false, died ?? "alive")
        XCTAssertLessThan(Date().timeIntervalSince(started), 3)
        XCTAssertEqual(engine.queue.sync { host.queries }, CpuBudget.queryLimit + 1)
        engine.terminate()
    }

    func testQueriesThatHoldTheHostPastTheGuardStopTheWidget() {
        // 30 ms per answer: the time guard (1000 ms) fires at the 34th query, and the loop that ends on the error answers is stopped at the task's end.
        let (engine, host) = run("const a = document.getElementById('a'); for (let i = 0; i < 60; i++) { a.style.width = i + 'px'; a.offsetHeight; }", delay: 0.03, wait: 10)
        let died = engine.queue.sync { host.died }
        XCTAssertTrue(died?.hasPrefix("Layout query limit exceeded: host queries blocked the renderer for more than 1000 ms in one boot task") ?? false, died ?? "alive")
        XCTAssertLessThan(engine.queue.sync { host.queries }, 40)
        engine.terminate()
    }

    func testOrdinaryQueriesKeepTheWidgetAlive() {
        let (engine, host) = run("const a = document.getElementById('a'); for (let i = 0; i < 10; i++) { a.style.width = i + 'px'; a.offsetHeight; }", delay: 0.002, wait: 1)
        XCTAssertNil(engine.queue.sync { host.died })
        XCTAssertGreaterThan(engine.queue.sync { host.queries }, 0)
        engine.terminate()
    }
}

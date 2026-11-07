import XCTest
@testable import WidgetSandbox

final class CpuTimeLimitTests: XCTestCase {
    private func run(_ html: String, cpuLimitMs: Double, contention: Bool, timeout: TimeInterval = 20) -> WidgetDiagnostics {
        var options = WidgetHost.Options()
        options.cpuLimitMs = cpuLimitMs
        let stop = UnsafeMutablePointer<Bool>.allocate(capacity: 1)
        stop.pointee = false
        defer { stop.deallocate() }
        let group = DispatchGroup()
        if contention {
            for _ in 0..<(ProcessInfo.processInfo.activeProcessorCount * 4) {
                let thread = Thread {
                    var x = 0.0
                    while !stop.pointee { x += sin(x) }
                    _ = x
                    group.leave()
                }
                thread.qualityOfService = .userInteractive
                group.enter()
                thread.start()
            }
        }
        let renderer = RecordingRenderer()
        let host = try! WidgetHost(html: html, theme: WidgetTheme(), width: 300, options: options, rendererFactory: { _, _, _ in renderer })
        defer { host.destroy() }
        let deadline = Date().addingTimeInterval(timeout)
        while !(host.diagnostics.loaded || host.diagnostics.dead != nil) && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
        stop.pointee = true
        group.wait()
        return host.diagnostics
    }

    private let work = "<div id=o></div><script>var t=0;for(var i=0;i<1500000;i++){t+=Math.sqrt(i)%3;}document.getElementById('o').textContent=String(t);console.log('done');</script>"

    func testBusyMachineDoesNotKillAWidgetWithinItsCPUBudget() {
        let alone = run(work, cpuLimitMs: 5000, contention: false)
        XCTAssertTrue(alone.loaded)
        let limit = max(alone.stats.jsMs * 4, 100)
        let loaded = run(work, cpuLimitMs: limit, contention: true)
        XCTAssertNil(loaded.dead, "jsMs alone \(alone.stats.jsMs), limit \(limit)")
        XCTAssertTrue(loaded.loaded)
    }

    func testInfiniteLoopIsStillKilled() {
        let t0 = Date()
        let result = run("<script>for(;;){}</script>", cpuLimitMs: 200, contention: false)
        XCTAssertNotNil(result.dead)
        XCTAssertTrue(result.dead?.contains("CPU limit") ?? false, result.dead ?? "")
        XCTAssertLessThan(Date().timeIntervalSince(t0), 5)
    }
}

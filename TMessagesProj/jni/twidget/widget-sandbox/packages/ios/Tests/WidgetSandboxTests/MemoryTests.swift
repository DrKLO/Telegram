import XCTest
import Darwin
@testable import WidgetSandbox

final class MemoryTests: XCTestCase {
    private static let tree = """
        [{"op":"el","id":2,"tag":"html","attrs":{}},{"op":"el","id":3,"tag":"body","attrs":{}},{"op":"ins","parent":1,"id":2,"before":null},{"op":"ins","parent":2,"id":3,"before":null},\
        {"op":"el","id":4,"tag":"div","attrs":{"class":"card"}},{"op":"text","id":5,"data":"secret of the previous widget"},{"op":"ins","parent":4,"id":5,"before":null},{"op":"ins","parent":3,"id":4,"before":null}]
        """

    func testRecycledValidatorKeepsNothingFromThePreviousWidget() throws {
        let validator = try TrustedValidator(theme: "light") { _, _ in }
        let initial = validator.nodeCount
        let built = validator.processOpsCounted(Self.tree)
        XCTAssertTrue(built.ops.contains("secret of the previous widget"))
        XCTAssertGreaterThan(built.nodes, initial)
        let stale = "[{\"op\":\"text\",\"id\":40,\"data\":\"late\"},{\"op\":\"ins\",\"parent\":4,\"id\":40,\"before\":null},{\"op\":\"attr\",\"id\":4,\"name\":\"class\",\"value\":\"x\"}]"
        let accepted = validator.processOps(stale)
        XCTAssertTrue(accepted.contains("\"parent\":4"), accepted)

        validator.reset(theme: "dark")
        XCTAssertEqual(validator.nodeCount, initial)
        let afterReset = validator.processOps(stale)
        XCTAssertFalse(afterReset.contains("\"parent\":4"), afterReset)
        XCTAssertFalse(afterReset.contains("\"op\":\"attr\""), afterReset)
        validator.reset(theme: "light")
        let rebuilt = validator.processOpsCounted(Self.tree)
        XCTAssertEqual(rebuilt.nodes, built.nodes)
    }

    func testValidatorDropsItsBuildGarbage() throws {
        let validator = try TrustedValidator(theme: "light") { _, _ in }
        let usage = try XCTUnwrap(validator.memoryUsage)
        XCTAssertLessThan(try XCTUnwrap(usage["malloc"]), 1_500_000, "\(usage)")
        validator.reset(theme: "light")
        let recycled = try XCTUnwrap(validator.memoryUsage)
        XCTAssertLessThan(try XCTUnwrap(recycled["malloc"]), 1_500_000, "\(recycled)")
    }

    func testPrewarmedEngineDropsItsBundleGarbage() throws {
        let engine = SandboxEngine()
        engine.prewarm()
        XCTAssertTrue(engine.isPrewarmed)
        let usage = try XCTUnwrap(engine.memoryUsage)
        XCTAssertLessThan(try XCTUnwrap(usage["malloc"]), 2_500_000, "\(usage)")
        engine.terminate()
    }

    func testKilledEngineFreesItsRuntime() {
        final class Delegate: SandboxEngineDelegate {
            var died: String?
            func engine(_ engine: SandboxEngine, ops json: String) {}
            func engine(_ engine: SandboxEngine, query json: String) -> (json: String, binary: Data?) { ("null", nil) }
            func engine(_ engine: SandboxEngine, listen json: String) {}
            func engine(_ engine: SandboxEngine, preventDefault json: String) {}
            func engineRequestsFrame(_ engine: SandboxEngine) {}
            func engine(_ engine: SandboxEngine, log level: String, text: String) {}
            func engine(_ engine: SandboxEngine, diag kind: String, detail: String) {}
            func engine(_ engine: SandboxEngine, bridge method: String, argsJson: String) {}
            func engineLoaded(_ engine: SandboxEngine) {}
            func engine(_ engine: SandboxEngine, stats: SandboxStats) {}
            func engine(_ engine: SandboxEngine, died reason: String) { died = reason }
        }
        var config = SandboxConfig(innerWidth: 300, innerHeight: 200, devicePixelRatio: 2, theme: "light")
        config.cpuLimitMs = 50
        let engine = SandboxEngine(html: "<script>const keep = []; for (let i = 0; i < 20000; i++) keep.push({ i }); for (;;) {}</script>", config: config)
        let delegate = Delegate()
        engine.delegate = delegate
        engine.start()
        let deadline = Date().addingTimeInterval(10)
        while engine.queue.sync { delegate.died } == nil && Date() < deadline {
            RunLoop.current.run(until: Date().addingTimeInterval(0.01))
        }
        XCTAssertTrue(engine.queue.sync { delegate.died }?.hasPrefix("CPU limit exceeded") ?? false)
        XCTAssertNil(engine.memoryUsage)
    }

    func testCancelledTimersLeaveTheThreadAndStoppedThreadsFreeTheirTimers() {
        final class Token {}
        weak var cancelled: Token?
        weak var pending: Token?
        var thread: JSThread? = JSThread(name: "memory-test")
        autoreleasepool {
            let a = Token(), b = Token()
            cancelled = a
            pending = b
            let handle = thread!.after(3600) { _ = a }
            _ = thread!.after(3600) { _ = b }
            thread!.sync { }
            handle.cancel()
            thread!.sync { }
            thread!.sync { }
        }
        XCTAssertNil(cancelled)
        XCTAssertNotNil(pending)
        thread?.stop()
        thread = nil
        let deadline = Date().addingTimeInterval(5)
        while pending != nil && Date() < deadline {
            RunLoop.current.run(until: Date().addingTimeInterval(0.01))
        }
        XCTAssertNil(pending)
    }

    func testDestroyedHostsReleaseEnginesAndThreads() throws {
        func threads() -> Int {
            var list: thread_act_array_t?
            var count = mach_msg_type_number_t(0)
            guard task_threads(mach_task_self_, &list, &count) == KERN_SUCCESS, let list = list else { return 0 }
            for i in 0..<Int(count) { mach_port_deallocate(mach_task_self_, list[i]) }
            vm_deallocate(mach_task_self_, vm_address_t(UInt(bitPattern: list)), vm_size_t(Int(count) * MemoryLayout<thread_t>.stride))
            return Int(count)
        }
        func footprint() -> Int {
            var info = task_vm_info_data_t()
            var count = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<natural_t>.size)
            _ = withUnsafeMutablePointer(to: &info) { $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) { task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &count) } }
            return Int(info.phys_footprint)
        }
        func pump(until done: () -> Bool, timeout: TimeInterval = 10) {
            let deadline = Date().addingTimeInterval(timeout)
            while !done() && Date() < deadline {
                RunLoop.main.run(until: Date().addingTimeInterval(0.01))
            }
        }
        let html = "<div id=x>hello</div><script>let n = 0; setInterval(() => { document.getElementById('x').textContent = 'tick ' + (++n) }, 30)</script>"
        func round() -> [() -> AnyObject?] {
            var weakHosts: [() -> AnyObject?] = []
            var hosts: [WidgetHost] = []
            autoreleasepool {
                for _ in 0..<8 {
                    let host = try! WidgetHost(html: html, theme: WidgetTheme(), width: 300, rendererFactory: { _, _, _ in StubRenderer() })
                    hosts.append(host)
                    weak var weakHost = host
                    weakHosts.append { weakHost }
                }
            }
            pump(until: { hosts.allSatisfy { $0.diagnostics.loaded } })
            XCTAssertTrue(hosts.allSatisfy { $0.diagnostics.loaded })
            pump(until: { false }, timeout: 0.2)
            autoreleasepool {
                hosts.forEach { $0.destroy() }
                hosts.removeAll()
            }
            return weakHosts
        }
        let warmHosts = round()
        pump(until: { warmHosts.allSatisfy { $0() == nil } })
        pump(until: { false }, timeout: 0.5)
        let threadsBefore = threads()
        let warm = footprint()
        for _ in 0..<4 {
            let weakHosts = round()
            pump(until: { weakHosts.allSatisfy { $0() == nil } && threads() <= threadsBefore + 1 })
            XCTAssertTrue(weakHosts.allSatisfy { $0() == nil })
        }
        XCTAssertLessThanOrEqual(threads(), threadsBefore + 1)
        XCTAssertLessThan(footprint() - warm, 24 << 20)
    }
}

private final class StubRenderer: Renderer {
    let view = PlatformView(frame: CGRect(x: 0, y: 0, width: 300, height: 100))
    var onMessage: ((String) -> Void)?
    var onDiag: ((String, String) -> Void)?

    func load() {
        onMessage?("{\"type\":\"ready\"}")
    }

    func send(_ json: String) {
        guard let data = json.data(using: .utf8), let message = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any], message["type"] as? String == "query", let qid = message["qid"] as? Int else {
            return
        }
        DispatchQueue.main.async { [weak self] in
            self?.onMessage?("{\"type\":\"result\",\"qid\":\(qid),\"result\":null}")
        }
    }

    func setTheme(_ theme: String) {}
    func evaluate(_ script: String, completion: @escaping (String?) -> Void) { completion(nil) }
    func destroy() {}
}

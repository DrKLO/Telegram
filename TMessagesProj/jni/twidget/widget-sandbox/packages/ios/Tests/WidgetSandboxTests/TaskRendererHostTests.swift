import XCTest
import WidgetSandboxProtocol
@testable import WidgetSandbox

final class TaskRendererHostTests: XCTestCase {
    private class Probe: Renderer {
        let view = PlatformView(frame: CGRect(x: 0, y: 0, width: 360, height: 200))
        var onMessage: ((String) -> Void)?
        var onDiag: ((String, String) -> Void)?
        var messages: [[String: Any]] = []
        var events: [String] = []
        var operationDepths: [Int] = []
        var queryDepths: [Int] = []
        var depth = 0
        var typedBatches = 0
        var destroyCount = 0
        var readyOnLoad = true
        var holdsQueries = false
        var heldQuery: Int?
        let typed: Bool

        init(typed: Bool = false) { self.typed = typed }
        var acceptsTypedOps: Bool { typed }
        func load() { if readyOnLoad { ready() } }
        func ready() { onMessage?("{\"type\":\"ready\"}") }
        func send(ops: [WidgetOp]) {
            typedBatches += 1
            send("{\"type\":\"ops\",\"ops\":\(WidgetOp.json(ops))}")
        }
        func send(_ json: String) {
            guard let message = try? JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any], let type = message["type"] as? String else { return }
            messages.append(message)
            if type == "ops" {
                events.append("ops")
                operationDepths.append(depth)
            }
            if type == "query", let id = message["qid"] as? Int {
                events.append("query:\(id)")
                if id > 0 {
                    queryDepths.append(depth)
                    if holdsQueries { heldQuery = id; return }
                }
                let result: [String: Any] = ["type": "result", "qid": id, "result": ["x": 0, "y": 0, "width": 100, "height": 20, "offsetHeight": 20]]
                onMessage?(String(decoding: try! JSONSerialization.data(withJSONObject: result), as: UTF8.self))
            }
        }
        func setTheme(_ theme: String) {}
        func evaluate(_ script: String, completion: @escaping (String?) -> Void) { completion(nil) }
        func destroy() { destroyCount += 1; events.append("destroy") }
        var operations: [[String: Any]] { messages.flatMap { $0["ops"] as? [[String: Any]] ?? [] } }
        var text: String { operations.compactMap { $0["data"] as? String ?? $0["text"] as? String ?? $0["value"] as? String }.joined(separator: "|") }
        var begins: Int { events.filter { $0 == "begin" }.count }
        var ends: Int { events.filter { $0 == "end" }.count }
    }

    private final class TaskProbe: Probe, TaskRenderer {
        func beginTask() { events.append("begin"); depth += 1 }
        func endTask() { events.append("end"); depth -= 1 }
    }

    private static let widget = """
        <div id="d">before</div><script>
        var d=document.getElementById('d');
        d.style.height='200px';
        var height=d.offsetHeight;
        d.textContent='height:'+height;
        </script>
        """

    private func options(asynchronous: Bool = true, binary: Bool = false) -> WidgetHost.Options {
        var options = WidgetHost.Options()
        options.policy = .strict
        options.asyncValidation = asynchronous
        options.sharedValidatorThread = asynchronous
        if binary { options.features = ["binaryOps", "canvasWords", "webAnimations", "morphUpdates"] }
        return options
    }

    private func pump(_ condition: () -> Bool, file: StaticString = #filePath, line: UInt = #line) {
        let deadline = Date().addingTimeInterval(5)
        while !condition() && Date() < deadline { RunLoop.main.run(until: Date().addingTimeInterval(0.005)) }
        XCTAssertTrue(condition(), "host did not finish", file: file, line: line)
    }

    private func assertBalanced(_ probe: TaskProbe, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertGreaterThan(probe.begins, 0, file: file, line: line)
        XCTAssertEqual(probe.begins, probe.ends, "\(probe.events)", file: file, line: line)
        XCTAssertEqual(probe.depth, 0, file: file, line: line)
        var depth = 0
        for event in probe.events {
            if event == "begin" { depth += 1; XCTAssertEqual(depth, 1, file: file, line: line) }
            if event == "end" { depth -= 1; XCTAssertEqual(depth, 0, file: file, line: line) }
        }
        XCTAssertTrue(probe.operationDepths.allSatisfy { $0 == 1 }, "\(probe.operationDepths)", file: file, line: line)
        XCTAssertTrue(probe.queryDepths.allSatisfy { $0 == 1 }, "\(probe.queryDepths)", file: file, line: line)
    }

    private func checkOrderedDelivery(asynchronous: Bool, binary: Bool) throws {
        let probe = TaskProbe(typed: binary)
        let host = try WidgetHost(html: Self.widget, options: options(asynchronous: asynchronous, binary: binary), rendererFactory: { _, _, _ in probe })
        defer { host.destroy() }
        pump { host.diagnostics.loaded && host.diagnostics.rendered && probe.depth == 0 }
        XCTAssertFalse(probe.operationDepths.isEmpty)
        XCTAssertFalse(probe.queryDepths.isEmpty)
        XCTAssertTrue(probe.text.contains("height:20"), probe.text)
        XCTAssertEqual(probe.typedBatches > 0, binary)
        XCTAssertNil(host.diagnostics.dead)
        XCTAssertNil(host.diagnostics.lastError)
        assertBalanced(probe)
    }

    func testSynchronousJSONDeliveryEnclosesScriptOpsAndQueries() throws {
        try checkOrderedDelivery(asynchronous: false, binary: false)
    }

    func testAsynchronousJSONDeliveryEnclosesScriptOpsAndQueries() throws {
        try checkOrderedDelivery(asynchronous: true, binary: false)
    }

    func testAsynchronousBinaryDeliveryEnclosesScriptOpsAndQueries() throws {
        try checkOrderedDelivery(asynchronous: true, binary: true)
    }

    func testPreReadyBootReplaysTaskBoundariesAroundQueuedOps() throws {
        let probe = TaskProbe(typed: true)
        probe.readyOnLoad = false
        var options = options(binary: true)
        options.startBeforeRendererReady = true
        let host = try WidgetHost(html: "<p>buffered</p>", options: options, rendererFactory: { _, _, _ in probe })
        defer { host.destroy() }
        pump { host.diagnostics.loaded }
        XCTAssertTrue(probe.events.isEmpty, "\(probe.events)")
        probe.ready()
        pump { host.diagnostics.rendered && probe.depth == 0 }
        XCTAssertTrue(probe.text.contains("buffered"), probe.text)
        assertBalanced(probe)
    }

    func testUncaughtScriptErrorClosesTask() throws {
        let probe = TaskProbe(typed: true)
        let host = try WidgetHost(html: "<p>before error</p><script>throw new Error('task-error-marker');</script>", options: options(binary: true), rendererFactory: { _, _, _ in probe })
        defer { host.destroy() }
        pump { host.diagnostics.loaded && host.diagnostics.rendered && probe.depth == 0 }
        XCTAssertTrue(host.diagnostics.logs.contains { $0.text.contains("task-error-marker") })
        XCTAssertNil(host.diagnostics.dead)
        assertBalanced(probe)
    }

    func testCPULimitClosesTask() throws {
        let probe = TaskProbe(typed: true)
        var options = options(binary: true)
        options.cpuLimitMs = 100
        let host = try WidgetHost(html: "<p>before stop</p><script>for(;;){}</script>", options: options, rendererFactory: { _, _, _ in probe })
        defer { host.destroy() }
        pump { host.diagnostics.dead != nil && probe.depth == 0 }
        XCTAssertTrue(host.diagnostics.dead?.contains("CPU limit") == true, host.diagnostics.dead ?? "not stopped")
        assertBalanced(probe)
    }

    func testDestroyDuringPendingQueryStopsRendererDelivery() throws {
        let probe = TaskProbe(typed: true)
        probe.holdsQueries = true
        let host = try WidgetHost(html: Self.widget, options: options(binary: true), rendererFactory: { _, _, _ in probe })
        defer { host.destroy() }
        pump { probe.heldQuery != nil }
        XCTAssertEqual(probe.depth, 1)
        let engine = try XCTUnwrap(Mirror(reflecting: host).children.first { $0.label == "engine" }?.value as? SandboxEngine)
        host.destroy()
        engine.queue.sync {}
        let events = probe.events
        let messages = probe.messages.count
        RunLoop.main.run(until: Date().addingTimeInterval(0.1))
        XCTAssertEqual(probe.events, events)
        XCTAssertEqual(probe.messages.count, messages)
        XCTAssertEqual(probe.destroyCount, 1)
        XCTAssertEqual(probe.events.last, "destroy")
        XCTAssertNil(probe.onMessage)
        host.destroy()
        XCTAssertEqual(probe.destroyCount, 1)
    }

    func testTaskCallbacksPreservePlainRendererOutput() throws {
        let plain = Probe(typed: true)
        let batched = TaskProbe(typed: true)
        var outputs: [String] = []
        for probe in [plain, batched] {
            let host = try WidgetHost(html: Self.widget, options: options(binary: true), rendererFactory: { _, _, _ in probe })
            pump { host.diagnostics.loaded && host.diagnostics.rendered && probe.depth == 0 }
            let relevant = probe.messages.filter { ["ops", "query"].contains($0["type"] as? String ?? "") }
            outputs.append(String(decoding: try JSONSerialization.data(withJSONObject: relevant, options: [.sortedKeys]), as: UTF8.self))
            XCTAssertTrue(probe.text.contains("height:20"), probe.text)
            XCTAssertNil(host.diagnostics.dead)
            XCTAssertNil(host.diagnostics.lastError)
            host.destroy()
        }
        XCTAssertEqual(outputs[0], outputs[1])
        assertBalanced(batched)
    }
}

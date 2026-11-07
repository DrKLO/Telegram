import XCTest
@testable import WidgetSandbox
import WidgetSandboxProtocol

/// WidgetSandboxProtocol: validated render ops as typed values and back to the JSON
/// contract of docs/protocol.md, and the JSON fallback of `Renderer.send(ops:)`.
final class WidgetOpTests: XCTestCase {
    private let batch = """
    [{"op":"el","id":2,"tag":"html","attrs":[],"adopt":"html"},
     {"op":"el","id":3,"tag":"svg","ns":"svg","attrs":[["viewBox","0 0 10 10"],["class","a \\"b\\""]]},
     {"op":"text","id":4,"data":"héllo\\n\\u2028"},
     {"op":"data","id":4,"data":""},
     {"op":"ins","parent":2,"id":3,"before":null},
     {"op":"ins","parent":2,"id":4,"before":3},
     {"op":"attr","id":3,"name":"width","value":"10"},
     {"op":"rattr","id":3,"name":"width"},
     {"op":"style","id":3,"name":"color","value":"red","important":true},
     {"op":"style","id":3,"name":"opacity","value":""},
     {"op":"css","id":5,"text":"a{color:red}"},
     {"op":"prop","id":6,"name":"value","value":"x"},
     {"op":"call","id":6,"method":"focus"},
     {"op":"rm","id":4}]
    """

    private func parse(_ json: String) throws -> [[String: Any]] {
        try XCTUnwrap(JSONSerialization.jsonObject(with: Data(json.utf8)) as? [[String: Any]])
    }

    private func normalized(_ op: [String: Any]) -> NSDictionary {
        var op = op
        if op["op"] as? String == "style", op["important"] as? Bool == false { op["important"] = nil }
        if op["op"] as? String == "el", (op["attrs"] as? [Any])?.isEmpty == true { op["attrs"] = nil }
        return op as NSDictionary
    }

    func testTypedOpsRoundTripToTheSameJSON() throws {
        let fields = try parse(batch)
        let ops = fields.map(WidgetOp.init)
        XCTAssertEqual(ops.map(\.kind), [.element, .element, .text, .data, .insert, .insert, .attribute, .removeAttribute, .style, .style, .css, .other, .other, .remove])
        let back = try parse(WidgetOp.json(ops))
        XCTAssertEqual(back.count, fields.count)
        for (a, b) in zip(fields, back) {
            XCTAssertEqual(normalized(a), normalized(b))
        }
    }

    func testDefaultTypedDeliveryFallsBackToTheJSONMessage() throws {
        final class JSONOnly: Renderer {
            var sent: [String] = []
            var view: PlatformView { PlatformView() }
            var onMessage: ((String) -> Void)?
            var onDiag: ((String, String) -> Void)?
            func load() {}
            func send(_ json: String) { sent.append(json) }
            func setTheme(_ theme: String) {}
            func evaluate(_ script: String, completion: @escaping (String?) -> Void) { completion(nil) }
            func destroy() {}
        }
        let renderer = JSONOnly()
        XCTAssertFalse(renderer.acceptsTypedOps)
        let ops = try parse(batch).map(WidgetOp.init)
        renderer.send(ops: ops)
        XCTAssertEqual(renderer.sent.count, 1)
        let message = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(renderer.sent[0].utf8)) as? [String: Any])
        XCTAssertEqual(message["type"] as? String, "ops")
        XCTAssertEqual((message["ops"] as? [Any])?.count, ops.count)
    }
}

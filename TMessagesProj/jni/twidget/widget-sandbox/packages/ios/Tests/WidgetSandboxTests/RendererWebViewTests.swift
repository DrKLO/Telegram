import WebKit
import XCTest
@testable import WidgetSandbox

/// Loads the real renderer bundle in a WKWebView and waits for its `ready` message.
final class RendererWebViewTests: XCTestCase {
    @MainActor
    func testRendererAnnouncesReadyAndAnswersQueries() async throws {
        let renderer = RendererWebView(rendererBundle: SandboxResources.shared.rendererBundle, theme: "light")
        var diags: [String] = []
        var messages: [String] = []
        let ready = expectation(description: "ready")
        let answered = expectation(description: "query answered")
        renderer.onDiag = { k, d in diags.append("\(k): \(d)") }
        renderer.onMessage = { json in
            messages.append(json)
            if json.contains("\"ready\"") { ready.fulfill() }
            if json.contains("\"result\"") { answered.fulfill() }
        }
        // WKWebView needs a window to run its web process reliably in tests (see TestWindow).
        let window = TestWindow.makeVisible()
        window.attach(renderer.webView)
        renderer.load()
        await fulfillment(of: [ready], timeout: 15)
        XCTAssertTrue(diags.allSatisfy { !$0.contains("load failed") }, "\(diags)")

        // Build a tiny tree, then ask for its layout through the real transport.
        renderer.send(#"{"type":"ops","ops":[{"op":"el","id":2,"tag":"html","attrs":[],"adopt":"html"},{"op":"el","id":3,"tag":"body","attrs":[],"adopt":"body"},{"op":"el","id":4,"tag":"div","attrs":[["style","width:120px;height:30px"]]},{"op":"ins","parent":3,"id":4,"before":null}]}"#)
        renderer.send(#"{"type":"query","qid":1,"query":{"q":"rect","id":4}}"#)
        await fulfillment(of: [answered], timeout: 10)
        let result = try XCTUnwrap(messages.last { $0.contains("\"result\"") })
        XCTAssertTrue(result.contains("\"width\":120"), result)
        XCTAssertTrue(messages.contains { $0.contains("\"size\"") }, "size report expected: \(messages)")
    }
}

import XCTest
@testable import WidgetSandbox
import WidgetSandboxProtocol

/// A renderer that takes typed ops gets the same render ops as a JSON renderer:
/// validator output parsed into `WidgetOp`s on the validator thread, and canvas
/// word batches validated natively (no JSON at all) when every payload passes the
/// envelope on a known canvas.
final class TypedDeliveryTests: XCTestCase {
    final class TypedRenderer: Renderer {
        let inner = RecordingRenderer()
        var view: PlatformView { inner.view }
        var onMessage: ((String) -> Void)? { get { inner.onMessage } set { inner.onMessage = newValue } }
        var onDiag: ((String, String) -> Void)? { get { inner.onDiag } set { inner.onDiag = newValue } }
        var typedBatches = 0
        var acceptsTypedOps: Bool { true }
        func load() { inner.load() }
        func send(_ json: String) { inner.send(json) }
        func send(ops: [WidgetOp]) {
            typedBatches += 1
            inner.send("{\"type\":\"ops\",\"ops\":\(WidgetOp.json(ops))}")
        }
        func setTheme(_ theme: String) {}
        func evaluate(_ script: String, completion: @escaping (String?) -> Void) { completion(nil) }
        func destroy() {}
    }

    private static let widget = """
        <div id="d" style="color: red">x</div><canvas id="c" width="100" height="50"></canvas><script>
        var c = document.getElementById('c').getContext('2d'), d = document.getElementById('d'), f = 0;
        var g = c.createLinearGradient(0, 0, 10, 0); g.addColorStop(0, 'red');
        function frame() {
          c.clearRect(0, 0, 100, 50); c.fillStyle = f % 2 ? g : 'blue'; c.fillRect(f, 0, 10.5, 1e21); c.lineWidth = NaN;
          c.fillText('h\\u00e9 \\ud83d\\ude00 ' + f, 1, 2); c.setLineDash([1, 2]); if (f === 0) c.font = '10px "A B"';
          if (f === 2) c.fillText('x'.repeat(30000), 0, 0);
          if (f % 2 === 0) { d.style.opacity = String(f / 10); d.textContent = 'n' + f; }
          if (++f < 6) requestAnimationFrame(frame);
        }
        frame();
        </script>
        """

    private func normalized(_ messages: [String]) -> [NSDictionary] {
        messages.compactMap { m -> [[String: Any]]? in
            guard let o = (try? JSONSerialization.jsonObject(with: Data(m.utf8))) as? [String: Any], o["type"] as? String == "ops" else { return nil }
            return o["ops"] as? [[String: Any]]
        }.flatMap { $0 }.map { op -> NSDictionary in
            var op = op
            if op["op"] as? String == "style", op["important"] as? Bool == false { op["important"] = nil }
            if op["op"] as? String == "el", (op["attrs"] as? [Any])?.isEmpty == true { op["attrs"] = nil }
            return op as NSDictionary
        }
    }

    private func run(typed: Bool) -> (messages: [String], typedBatches: Int, nativeCanvas: Int) {
        var options = WidgetHost.Options()
        options.policy = .strict
        options.asyncValidation = true
        options.features = ["canvasWords"]
        let typedRenderer = TypedRenderer()
        let jsonRenderer = RecordingRenderer()
        let host = try! WidgetHost(html: Self.widget, theme: WidgetTheme(), width: 300, options: options, rendererFactory: { _, _, _ in typed ? typedRenderer as Renderer : jsonRenderer as Renderer })
        defer { host.destroy() }
        let deadline = Date().addingTimeInterval(10)
        while !(host.diagnostics.rendered || host.diagnostics.dead != nil) && Date() < deadline { RunLoop.main.run(until: Date().addingTimeInterval(0.01)) }
        let fields = Dictionary(Mirror(reflecting: host).children.compactMap { c in c.label.map { ($0, c.value) } }, uniquingKeysWith: { a, _ in a })
        let engine = fields["engine"] as! SandboxEngine
        for frame in 0..<8 {
            engine.frame(time: Double(frame) * 16)
            engine.queue.sync {}
            RunLoop.main.run(until: Date().addingTimeInterval(0.03))
        }
        RunLoop.main.run(until: Date().addingTimeInterval(0.2))
        let validator = fields["validator"] as! TrustedValidator
        let native = validator.thread.sync { validator.nativeCanvasBatches }
        return (typed ? typedRenderer.inner.messages : jsonRenderer.messages, typedRenderer.typedBatches, native)
    }

    func testTypedRendererGetsTheSameOpsAsAJSONRenderer() {
        let json = run(typed: false)
        let typed = run(typed: true)
        XCTAssertEqual(json.nativeCanvas, 0)
        XCTAssertGreaterThan(typed.typedBatches, 3)
        XCTAssertGreaterThan(typed.nativeCanvas, 0, "canvas word batches were validated natively")
        XCTAssertEqual(normalized(typed.messages), normalized(json.messages))
        // A canvas string just under the 32 KB cap reaches the renderer on both paths (past it the widget stops: StrictLimitsTests).
        XCTAssertTrue(json.messages.joined().contains(String(repeating: "x", count: 30000)))
        XCTAssertTrue(typed.messages.joined().contains(String(repeating: "x", count: 30000)))
    }
}

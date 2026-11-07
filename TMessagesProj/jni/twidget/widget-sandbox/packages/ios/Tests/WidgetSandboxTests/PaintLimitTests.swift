import XCTest
@testable import WidgetSandbox

/// The heavy-box paint limit (docs/decisions.md "Heavy boxes"): the renderer counts (the WebView renderer's core, or a
/// client's own renderer through `limitExceeded`), the host stops the widget like a counted cap, compat counts nothing.
final class PaintLimitTests: XCTestCase {
    private func pump(_ timeout: TimeInterval, _ condition: () -> Bool) {
        let deadline = Date().addingTimeInterval(timeout)
        while !condition() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
    }

    private func host(_ renderer: RecordingRenderer, policy: SandboxPolicy = .strict) throws -> WidgetHost {
        var options = WidgetHost.Options()
        options.policy = policy
        return try WidgetHost(html: "<p id=p>x</p><script>setInterval(function () { document.getElementById('p').textContent += '.'; }, 50);</script>", theme: WidgetTheme(), width: 300, options: options, rendererFactory: { _, _, _ in renderer })
    }

    func testMessages() {
        XCTAssertEqual(WidgetPaintLimit.paint.message(count: 17), "Paint limit exceeded: 17 boxes with shadows, filters or transparency (max 16)")
        XCTAssertEqual(WidgetPaintLimit.backdrop.message(count: 3), "Paint limit exceeded: 3 boxes with backdrop-filter (max 2)")
        XCTAssertEqual(WidgetPaintLimit.maxHeavyBoxes, 16)
        XCTAssertEqual(WidgetPaintLimit.maxBackdropBoxes, 2)
    }

    func testTheRendererLearnsThePolicy() throws {
        for policy in [SandboxPolicy.strict, .compat] {
            let renderer = RecordingRenderer()
            let host = try host(renderer, policy: policy)
            defer { host.destroy() }
            pump(5) { host.diagnostics.loaded }
            XCTAssertTrue(renderer.messages.contains("{\"type\":\"policy\",\"policy\":\"\(policy.rawValue)\"}"), "\(policy)")
        }
    }

    func testLimitExceededStopsTheWidget() throws {
        for (code, count) in [(WidgetPaintLimit.paint, 17), (.backdrop, 3)] {
            let renderer = RecordingRenderer()
            let host = try host(renderer)
            defer { host.destroy() }
            pump(5) { host.diagnostics.loaded }
            renderer.limitExceeded(code, count: count)
            pump(5) { host.diagnostics.dead != nil }
            let reason = code.message(count: count)
            XCTAssertEqual(host.diagnostics.dead, reason)
            XCTAssertEqual(host.diagnostics.diags[code.rawValue]?.details, [reason])
            XCTAssertTrue(renderer.messages.last?.contains("\"type\":\"frozen\"") == true)
            // the engine is gone: the widget's timer sends nothing more
            let sent = renderer.messages.count
            RunLoop.main.run(until: Date().addingTimeInterval(0.3))
            XCTAssertEqual(renderer.messages.count, sent)
        }
    }

    func testCompatIgnoresTheLimit() throws {
        let renderer = RecordingRenderer()
        let host = try host(renderer, policy: .compat)
        defer { host.destroy() }
        pump(5) { host.diagnostics.loaded }
        renderer.limitExceeded(.paint, count: 40)
        RunLoop.main.run(until: Date().addingTimeInterval(0.2))
        XCTAssertNil(host.diagnostics.dead)
    }

    func testAMalformedReportIsAViolation() throws {
        let renderer = RecordingRenderer()
        let host = try host(renderer)
        defer { host.destroy() }
        pump(5) { host.diagnostics.loaded }
        renderer.onMessage?("{\"type\":\"limit\",\"code\":\"render-budget\",\"count\":1}")
        renderer.onMessage?("{\"type\":\"limit\",\"code\":\"paint-limit\"}")
        RunLoop.main.run(until: Date().addingTimeInterval(0.2))
        XCTAssertNil(host.diagnostics.dead)
        XCTAssertEqual(host.diagnostics.diags["renderer-violation"]?.count, 2)
    }

    #if canImport(UIKit)
    /// The product renderer counts by itself: 16 boxes with a blurred shadow draw, a 17th stops the widget. iOS only: the
    /// count runs before a frame is drawn (requestAnimationFrame), and an xctest window on macOS is never visible, so its
    /// WebView's document stays hidden and draws no frames.
    @MainActor
    func testTheWebViewRendererStopsAt17HeavyBoxes() async throws {
        let window = TestWindow.makeVisible()
        defer { window.close() }
        let boxes = { (n: Int) in String(repeating: "<div class=b></div>", count: n) }
        for (n, dead) in [(16, false), (17, true)] {
            let html = "<style>.b{display:inline-block;width:12px;height:12px;margin:2px;box-shadow:0 2px 6px rgba(0,0,0,.4)}</style>\(boxes(n))"
            let host = try WidgetHost(html: html, theme: WidgetTheme(), width: 300, options: WidgetHost.Options())
            window.attach(host.view)
            let deadline = Date().addingTimeInterval(10)
            while Date() < deadline && !(host.diagnostics.firstContentMs != nil && host.diagnostics.loaded) { try await Task.sleep(nanoseconds: 20_000_000) }
            try await Task.sleep(nanoseconds: 500_000_000)
            if dead {
                let until = Date().addingTimeInterval(5)
                while Date() < until && host.diagnostics.dead == nil { try await Task.sleep(nanoseconds: 20_000_000) }
                XCTAssertEqual(host.diagnostics.dead, WidgetPaintLimit.paint.message(count: 17))
            } else {
                XCTAssertNil(host.diagnostics.dead)
            }
            host.destroy()
        }
    }
    #endif
}

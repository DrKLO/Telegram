import AppKit
import XCTest
import WidgetSandbox
@testable import TwidgetRenderer

final class SwipeWidgetRegressionTests: XCTestCase {
    private final class VisibleWindow: NSWindow {
        override var isVisible: Bool { true }
        override var occlusionState: NSWindow.OcclusionState { .visible }
    }

    private func pump(_ duration: TimeInterval) {
        let end = Date().addingTimeInterval(duration)
        while Date() < end { RunLoop.main.run(until: Date().addingTimeInterval(0.002)) }
    }

    private func wheel(in view: NSView, at point: CGPoint, dx: Int32, dy: Int32) throws -> NSEvent {
        let event = try XCTUnwrap(CGEvent(scrollWheelEvent2Source: nil, units: .pixel, wheelCount: 2, wheel1: dy, wheel2: dx, wheel3: 0))
        let local = view.convert(point, to: nil)
        event.location = CGPoint(x: local.x, y: (NSScreen.screens.first?.frame.height ?? 0) - local.y)
        event.setIntegerValueField(CGEventField(rawValue: 88)!, value: 1)
        let native = try XCTUnwrap(NSEvent(cgEvent: event))
        let routed = view.convert(native.locationInWindow, from: nil)
        XCTAssertEqual(routed.x, point.x, accuracy: 0.5)
        XCTAssertEqual(routed.y, point.y, accuracy: 0.5)
        return native
    }

    func testProductionWidgetRoutesBothWheelAxesAndReportsJavaScriptOffsets() throws {
        try exercise(nativeScrolling: true)
        try exercise(nativeScrolling: false)
        try exercise(nativeScrolling: true)
    }

    private func exercise(nativeScrolling: Bool) throws {
        _ = NSApplication.shared
        let file = URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("../../../../tools/perflab/swipe-test/swipe-test.html")
        let html = try String(contentsOf: file)
        let window = VisibleWindow(contentRect: CGRect(x: 0, y: 0, width: 360, height: 600), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        defer { window.close() }
        let queue = WidgetExecutor.makeQueue("widget.swipe.production")
        let executor = WidgetExecutor(queue: queue)
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 360)
        var options = WidgetHost.Options()
        options.nativeScrolling = nativeScrolling
        options.asyncValidation = true
        options.sharedValidatorThread = true
        options.startBeforeRendererReady = true
        options.pools = true
        options.features = ["binaryOps", "canvasWords", "webAnimations", "morphUpdates"]
        EnginePool.shared.features = options.features
        let host = try WidgetHost(html: html, theme: WidgetTheme(colorScheme: .light), width: 360, options: options, rendererFactory: { _, _, _ in renderer }, executor: executor)
        defer {
            host.destroy()
            queue.sync {}
        }
        host.view.frame = CGRect(x: 0, y: 0, width: 360, height: 600)
        window.contentView?.addSubview(host.view)
        host.onChange = { [weak host] state in
            if state.height > 0 { host?.view.setFrameSize(CGSize(width: 360, height: state.height)) }
        }
        executor.async {
            renderer.preparesOffscreen = true
            renderer.resizeAnchor = .top
        }
        let deadline = Date().addingTimeInterval(5)
        while Date() < deadline {
            if host.diagnostics.rendered && queue.sync(execute: { renderer.isPrepared }) { break }
            pump(0.005)
        }
        XCTAssertTrue(host.diagnostics.rendered)
        pump(0.1)
        for (name, counter, dx, dy) in [("vertical", "v", Int32(0), Int32(-60)), ("horizontal", "h", Int32(-60), Int32(0))] {
            let state = try queue.sync { () -> (Int, CGRect, String, String, [CGRect]) in
                let node = try XCTUnwrap(renderer.dom.nodes.values.first { $0.attribute("id") == name })
                let id = renderer.dom.publicId(node)
                let document = try XCTUnwrap(renderer.document)
                let style = document.computedStyle(attribute: WidgetDOM.idAttribute, value: String(id))
                return (id, document.bounds(matching: "#" + name), style["overflow-x"] ?? "", style["overflow-y"] ?? "", renderer.documentView.windowState.scrollRegions)
            }
            let point = CGPoint(x: state.1.midX, y: state.1.midY)
            print("SWIPE BEFORE enabled=\(nativeScrolling) \(name) overflow=\(state.2)/\(state.3) viewport=\(state.1) regions=\(state.4)")
            XCTAssertEqual(state.4.contains { $0.contains(point) }, nativeScrolling, "The background view routing must respect the native scrolling capability")
            XCTAssertEqual(renderer.containsHorizontalScrollRegion(at: point), nativeScrolling && name == "horizontal")
            XCTAssertEqual(renderer.containsVerticalScrollRegion(at: point), nativeScrolling && name == "vertical")
            if !nativeScrolling {
                XCTAssertEqual(state.2, "hidden")
                XCTAssertEqual(state.3, "hidden")
            }
            XCTAssertFalse(renderer.containsHorizontalScrollRegion(at: CGPoint(x: -10, y: -10)))
            XCTAssertFalse(renderer.containsVerticalScrollRegion(at: CGPoint(x: -10, y: -10)))
            for _ in 0..<4 {
                renderer.view.scrollWheel(with: try wheel(in: renderer.view, at: point, dx: dx, dy: dy))
                pump(0.04)
            }
            pump(0.2)
            try queue.sync {
                let offset = renderer.scrolling.offset(state.0)
                let label = try XCTUnwrap(renderer.dom.nodes.values.first { $0.attribute("id") == counter })
                let text = WidgetDOM.textContent(label)
                print("SWIPE AFTER enabled=\(nativeScrolling) \(name) offset=\(offset) JS=\(text) \(renderer.tileDebug)")
                let number = Double(text.split(separator: ":").last?.trimmingCharacters(in: .whitespaces) ?? "") ?? 0
                if nativeScrolling {
                    XCTAssertGreaterThan(name == "vertical" ? offset.y : offset.x, 0)
                    XCTAssertGreaterThan(number, 0, "The HTML scroll event must observe the native offset")
                } else {
                    XCTAssertEqual(offset, .zero)
                    XCTAssertEqual(number, 0)
                }
                XCTAssertEqual(number, name == "vertical" ? offset.y.rounded() : offset.x.rounded(), accuracy: 1)
                XCTAssertNil(host.diagnostics.dead)
                XCTAssertNil(host.diagnostics.lastError)
            }
        }
    }
}

import AppKit
import XCTest
import WidgetSandbox
@testable import TwidgetRenderer

final class WidgetResizePresentationTests: XCTestCase {
    private func pump(until done: () -> Bool) {
        let end = Date().addingTimeInterval(5)
        while !done() && Date() < end {
            RunLoop.main.run(until: Date().addingTimeInterval(0.005))
        }
        XCTAssertTrue(done())
    }

    func testHotelWidgetResizesOnItsBackgroundExecutor() throws {
        _ = NSApplication.shared
        let window = NSWindow(contentRect: CGRect(x: 0, y: 0, width: 420, height: 600), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        defer { window.close() }
        let file = URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("../../../widget-sandbox/corpus/resize/3-grows-down/03-show-more.html")
        let queue = WidgetExecutor.makeQueue("widget.resize.regression")
        let executor = WidgetExecutor(queue: queue)
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 420)
        let host = try WidgetHost(html: String(contentsOf: file), theme: WidgetTheme(colorScheme: .light), width: 420, rendererFactory: { _, _, _ in renderer }, executor: executor)
        defer { host.destroy() }
        window.contentView?.addSubview(host.view)
        executor.async {
            renderer.preparesOffscreen = true
            renderer.resizeAnchor = .top
        }
        host.onChange = { [weak host] diagnostics in
            if diagnostics.height > 0 {
                queue.sync {
                    XCTAssertEqual(renderer.documentView.contentLayer?.frame.minY, 0, "The top of the painted widget must stay fixed while its host catches up to the new height")
                }
                host?.view.setFrameSize(CGSize(width: 420, height: diagnostics.height))
            }
        }
        func ready(height: CGFloat) -> Bool {
            return host.diagnostics.loaded && abs(host.diagnostics.height - height) < 1 && abs(renderer.view.frame.height - height) < 1 && queue.sync {
                renderer.isPrepared && renderer.documentView.tilesDrawn && renderer.heldOpBatches == 0
            }
        }
        pump { ready(height: 319) }
        for step in 0..<18 {
            let height: CGFloat = [411, 503, 319][step % 3]
            let down = try XCTUnwrap(NSEvent.mouseEvent(with: .leftMouseDown, location: .zero, modifierFlags: [], timestamp: ProcessInfo.processInfo.systemUptime, windowNumber: window.windowNumber, context: nil, eventNumber: 0, clickCount: 1, pressure: 1))
            let up = try XCTUnwrap(NSEvent.mouseEvent(with: .leftMouseUp, location: .zero, modifierFlags: [], timestamp: ProcessInfo.processInfo.systemUptime, windowNumber: window.windowNumber, context: nil, eventNumber: 1, clickCount: 1, pressure: 0))
            executor.async {
                let node = renderer.dom.nodes.values.first { $0.attribute("id") == "more" }!
                let rect = renderer.elementBounds()[renderer.dom.publicId(node)]!
                let point = CGPoint(x: rect.midX, y: rect.midY)
                renderer.handleMouseDown(point, event: down)
                renderer.handleMouseUp(point, event: up)
            }
            pump { ready(height: height) }
            XCTAssertTrue(renderer.view.isDescendant(of: host.view))
            XCTAssertEqual(renderer.view.frame.height, height, accuracy: 1)
            XCTAssertNil(host.diagnostics.dead)
            queue.sync {
                XCTAssertEqual(renderer.lastResizeDiff?.keepsBottom, false)
                XCTAssertEqual(renderer.staleTileCount, 0, "step \(step): \(renderer.tileDebug)")
            }
        }
    }
}

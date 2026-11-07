import XCTest
import AppKit
import IOSurface
@testable import TwidgetRenderer

final class DetachedHandoffTests: XCTestCase {
    private func pump(until ready: () -> Bool) {
        let deadline = Date().addingTimeInterval(2)
        while !ready() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.005))
        }
        XCTAssertTrue(ready())
    }

    private func send(_ renderer: LiteHTMLWidgetRenderer, _ ops: [[String: Any]]) {
        let data = try! JSONSerialization.data(withJSONObject: ["type": "ops", "ops": ops])
        renderer.send(String(data: data, encoding: .utf8)!)
    }

    private func alpha(_ layer: CALayer) throws -> UInt8 {
        let contents = try XCTUnwrap(layer.contents)
        XCTAssertEqual(CFGetTypeID(contents as CFTypeRef), IOSurfaceGetTypeID())
        let surface = unsafeBitCast(contents as AnyObject, to: IOSurfaceRef.self)
        IOSurfaceLock(surface, .readOnly, nil)
        defer { IOSurfaceUnlock(surface, .readOnly, nil) }
        return IOSurfaceGetBaseAddress(surface).assumingMemoryBound(to: UInt8.self)[20 * IOSurfaceGetBytesPerRow(surface) + 20 * 4 + 3]
    }

    func testPromotionAndDemotionReplaceTilesInTheSameUpdate() throws {
        _ = NSApplication.shared
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 120, height: 120), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.orderFrontRegardless()
        defer { window.orderOut(nil) }
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 120)
        renderer.executorIsMain = { false }
        renderer.onMessage = { _ in }
        renderer.view.frame = NSRect(x: 0, y: 0, width: 120, height: 120)
        window.contentView?.addSubview(renderer.view)
        renderer.load()
        defer { renderer.destroy() }
        let view = try XCTUnwrap(renderer.view as? WidgetDocumentView)
        send(renderer, [
            ["op": "el", "id": 1, "tag": "body", "attrs": [], "adopt": "body"],
            ["op": "el", "id": 2, "tag": "style", "attrs": []],
            ["op": "css", "id": 2, "text": "body { margin: 0; } .box { width: 120px; height: 120px; background: red; } .fade { animation: fade 10s linear infinite; } @keyframes fade { from { opacity: .5; } to { opacity: 1; } }"],
            ["op": "ins", "id": 2, "parent": 1],
            ["op": "el", "id": 3, "tag": "div", "attrs": [["class", "box"]]],
            ["op": "ins", "id": 3, "parent": 1],
        ])
        pump { renderer.isPrepared }
        XCTAssertEqual(try alpha(XCTUnwrap(view.contentLayer?.sublayers?.first)), 255)
        for height in [120, 720, 1200, 720, 120, 720] {
            renderer.noteUserInput()
            send(renderer, [
                ["op": "attr", "id": 3, "name": "class", "value": "box fade"],
                ["op": "attr", "id": 3, "name": "style", "value": "height: \(height)px"],
            ])
            XCTAssertEqual(renderer.compositedCount, 1)
            XCTAssertFalse(view.preparing)
            let tiles = Array(try XCTUnwrap(view.contentLayer?.sublayers).prefix(view.tileCount))
            for tile in tiles {
                XCTAssertEqual(try alpha(tile), 0, "The old opaque tile must not remain behind the fade layer")
            }
            pump { !view.preparing && renderer.isPrepared }
            renderer.noteUserInput()
            send(renderer, [["op": "attr", "id": 3, "name": "class", "value": "box"]])
            XCTAssertEqual(renderer.compositedCount, 0)
            XCTAssertFalse(view.preparing)
            for tile in tiles {
                XCTAssertEqual(try alpha(tile), 255, "Removing the animation layer must not expose an empty tile")
            }
            pump { !view.preparing && renderer.isPrepared }
        }
        renderer.noteUserInput()
        send(renderer, [["op": "attr", "id": 3, "name": "style", "value": "height: 720px; background: blue"]])
        XCTAssertTrue(view.preparing, "Ordinary paint updates should remain asynchronous")
        pump { !view.preparing && renderer.isPrepared }
    }
}

import XCTest
import AppKit
@testable import TwidgetRenderer

final class TickRenderTests: XCTestCase {
    private func pump(_ seconds: TimeInterval, until done: () -> Bool = { false }) {
        let deadline = Date().addingTimeInterval(seconds)
        while !done() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.005))
        }
    }

    private func send(_ renderer: LiteHTMLWidgetRenderer, _ ops: [[String: Any]]) {
        let data = try! JSONSerialization.data(withJSONObject: ["type": "ops", "ops": ops])
        renderer.send(String(data: data, encoding: .utf8)!)
    }

    func testOpsWaitForTheTickExceptRightAfterUserInput() {
        _ = NSApplication.shared
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 300, height: 200), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.orderFrontRegardless()
        defer { window.orderOut(nil) }
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 300)
        renderer.onMessage = { _ in }
        renderer.view.frame = NSRect(x: 0, y: 0, width: 300, height: 200)
        window.contentView?.addSubview(renderer.view)
        renderer.load()
        send(renderer, [
            ["op": "el", "id": 1, "tag": "body", "attrs": [], "adopt": "body"],
            ["op": "el", "id": 2, "tag": "style", "attrs": []],
            ["op": "css", "id": 2, "text": "body { margin: 0; } .tick { width: 100px; height: 10px; animation: blink 200ms linear infinite alternate; } @keyframes blink { from { background-color: #f00; } to { background-color: #00f; } }"],
            ["op": "ins", "id": 2, "parent": 1],
            ["op": "el", "id": 5, "tag": "div", "attrs": [["class", "tick"]]],
            ["op": "ins", "id": 5, "parent": 1],
        ])
        pump(10, until: { renderer.rendersAtTick && !renderer.isPreparingTiles && renderer.heldOpBatches == 0 })
        XCTAssertTrue(renderer.rendersAtTick)
        let before = renderer.layoutCount
        send(renderer, [["op": "attr", "id": 5, "name": "title", "value": "a"]])
        XCTAssertEqual(renderer.layoutCount, before)
        pump(5, until: { renderer.layoutCount > before })
        XCTAssertGreaterThan(renderer.layoutCount, before)
        pump(10, until: { !renderer.isPreparingTiles && renderer.heldOpBatches == 0 })
        renderer.noteUserInput()
        XCTAssertFalse(renderer.rendersAtTick)
        let atInput = renderer.layoutCount
        let rastering = renderer.isPreparingTiles
        send(renderer, [["op": "attr", "id": 5, "name": "title", "value": "b"]])
        if rastering {
            XCTAssertEqual(renderer.heldOpBatches, 1)
            pump(10, until: { !renderer.isPreparingTiles })
            XCTAssertEqual(renderer.heldOpBatches, 0)
            XCTAssertGreaterThan(renderer.layoutCount, atInput)
        } else {
            XCTAssertEqual(renderer.heldOpBatches, 0)
            XCTAssertGreaterThan(renderer.layoutCount, atInput)
        }
        renderer.noteUserInput()
        let midRaster = renderer.layoutCount
        var sentDuringRaster = false
        pump(10, until: { renderer.heldOpBatches == 0 })
        pump(10, until: {
            if renderer.isPreparingTiles && !sentDuringRaster {
                sentDuringRaster = true
                send(renderer, [["op": "attr", "id": 5, "name": "title", "value": "c"]])
                XCTAssertEqual(renderer.heldOpBatches, 1)
            }
            return sentDuringRaster && !renderer.isPreparingTiles
        })
        if sentDuringRaster {
            XCTAssertEqual(renderer.heldOpBatches, 0)
            XCTAssertGreaterThan(renderer.layoutCount, midRaster)
        }
        renderer.destroy()
    }

    func testScreenAndBackingChangeStormsCoalesceIntoOneTrailingRefresh() {
        _ = NSApplication.shared
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 300, height: 200), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.orderFrontRegardless()
        defer { window.orderOut(nil) }
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 300)
        renderer.onMessage = { _ in }
        renderer.view.frame = NSRect(x: 0, y: 0, width: 300, height: 200)
        window.contentView?.addSubview(renderer.view)
        renderer.load()
        send(renderer, [
            ["op": "el", "id": 1, "tag": "body", "attrs": [], "adopt": "body"],
            ["op": "el", "id": 5, "tag": "div", "attrs": [["style", "height:40px;background:#08f"]]],
            ["op": "ins", "id": 5, "parent": 1],
        ])
        pump(1)
        let refreshes = renderer.environmentRefreshes
        let layouts = renderer.layoutCount
        for index in 0..<60 {
            window.colorSpace = index % 2 == 0 ? .displayP3 : .sRGB
            NotificationCenter.default.post(name: NSWindow.didChangeBackingPropertiesNotification, object: window)
            NotificationCenter.default.post(name: NSWindow.didChangeScreenNotification, object: window)
        }
        pump(0.5)
        XCTAssertEqual(renderer.environmentRefreshes, refreshes + 1)
        XCTAssertGreaterThan(renderer.layoutCount, layouts)
        XCTAssertLessThanOrEqual(renderer.layoutCount, layouts + 2)
        renderer.destroy()
    }
}

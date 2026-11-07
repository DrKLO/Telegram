import XCTest
import AppKit
@testable import TwidgetRenderer

final class ExclusionTileTests: XCTestCase {
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

    func testDemotedElementIsRedrawnIntoTilesThatOnlyPatchedANeighbour() {
        _ = NSApplication.shared
        let verify = WidgetDocumentView.verify
        let layerTiles = WidgetDocumentView.layerTiles
        WidgetDocumentView.verify = true
        WidgetDocumentView.layerTiles = false
        defer {
            WidgetDocumentView.verify = verify
            WidgetDocumentView.layerTiles = layerTiles
        }
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
            ["op": "css", "id": 2, "text": "body { margin: 0; } .box { position: relative; height: 40px; overflow: hidden; } .slide { position: relative; width: 100px; height: 30px; background: #0a4; animation: in 250ms linear; } .tick { width: 100px; height: 10px; animation: blink 120ms linear infinite alternate; } @keyframes in { from { transform: translateX(400px); } to { transform: none; } } @keyframes blink { from { background-color: #f00; } to { background-color: #00f; } }"],
            ["op": "ins", "id": 2, "parent": 1],
            ["op": "el", "id": 3, "tag": "div", "attrs": [["class", "box"]]],
            ["op": "ins", "id": 3, "parent": 1],
            ["op": "el", "id": 4, "tag": "div", "attrs": [["class", "slide"]]],
            ["op": "ins", "id": 4, "parent": 3],
            ["op": "el", "id": 5, "tag": "div", "attrs": [["class", "tick"]]],
            ["op": "ins", "id": 5, "parent": 1],
        ])
        var promoted = false
        pump(1.5, until: {
            promoted = promoted || renderer.compositedCount > 0
            return promoted && renderer.compositedCount == 0 && renderer.verification.frames > 20
        })
        pump(0.2)
        XCTAssertTrue(promoted)
        XCTAssertEqual(renderer.compositedCount, 0)
        XCTAssertGreaterThan(renderer.verification.frames, 0)
        XCTAssertEqual(renderer.verification.mismatched, 0)
        renderer.destroy()
    }
}

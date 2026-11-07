import AppKit
import XCTest
import LiteHTML
@testable import TwidgetRenderer

final class ResizeRasterPresentationTests: XCTestCase {
    private final class FailedRasterDocument: LHDocument {
        override func drawIfCurrent(in context: CGContext, origin: CGPoint, clip: CGRect) -> Bool {
            return false
        }
    }

    private func send(_ renderer: LiteHTMLWidgetRenderer, _ ops: [[String: Any]]) throws {
        let json = try JSONSerialization.data(withJSONObject: ["type": "ops", "ops": ops])
        renderer.send(String(decoding: json, as: UTF8.self))
    }

    private func pump(until done: () -> Bool) {
        let end = Date().addingTimeInterval(5)
        while !done() && Date() < end {
            RunLoop.main.run(until: Date().addingTimeInterval(0.005))
        }
        XCTAssertTrue(done())
    }

    func testResizeReportsWaitForMatchingRasterInBothDirections() throws {
        _ = NSApplication.shared
        let window = NSWindow(contentRect: CGRect(x: 0, y: 0, width: 300, height: 600), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        defer { window.close() }
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 300)
        renderer.executorIsMain = { false }
        renderer.preparesOffscreen = true
        var reported: [Double] = []
        renderer.onMessage = { json in
            if let object = try? JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any],
               object["type"] as? String == "size", let height = object["height"] as? Double {
                reported.append(height)
            }
        }
        window.contentView?.addSubview(renderer.view)
        renderer.load()
        defer { renderer.destroy() }
        try send(renderer, [
            ["op": "el", "id": 1, "tag": "body", "adopt": "body", "attrs": [["style", "margin:0"]]],
            ["op": "el", "id": 3, "tag": "div", "attrs": [["style", "height:360px;background:red"]]],
            ["op": "ins", "id": 3, "parent": 1],
        ])
        pump { reported.last == 360 && renderer.isPrepared }
        for height in [80, 84, 480, 120, 124, 360] {
            reported.removeAll()
            let content = try XCTUnwrap(renderer.documentView.contentLayer)
            let oldTiles = content.sublayers ?? []
            let gate = DispatchSemaphore(value: 0)
            WidgetDocumentView.prepareQueue.async(flags: .barrier) { gate.wait() }
            renderer.noteUserInput()
            try send(renderer, [["op": "attr", "id": 3, "name": "style", "value": "height:\(height)px;background:blue"]])
            XCTAssertTrue(reported.isEmpty, "The host must keep its old size until the new pixels are ready")
            XCTAssertTrue(oldTiles.allSatisfy { $0.superlayer === content }, "The old painted area must survive until the replacement raster is installed")
            gate.signal()
            pump { reported.last == Double(height) && renderer.isPrepared }
            XCTAssertEqual(reported, [Double(height)])
            XCTAssertTrue(renderer.documentView.hasPresented(height: CGFloat(height)))
            XCTAssertEqual(content.sublayers?.count, renderer.tileLayerCount)
            XCTAssertEqual(renderer.staleTileCount, 0)
        }
    }

    func testFailedResizeRasterKeepsTheLastPaintedFrame() throws {
        _ = NSApplication.shared
        let owner = LiteHTMLWidgetRenderer(theme: "light", width: 300)
        owner.executorIsMain = { false }
        defer { owner.destroy() }
        let view = WidgetDocumentView(frame: CGRect(x: 0, y: 0, width: 300, height: 360))
        view.owner = owner
        defer { _ = view.retire() }
        let before = LHDocument(html: "<body style='margin:0;height:360px;background:red'></body>", css: nil)
        _ = before.layout(maxWidth: 300, preferredWidth: 300)
        view.document = before
        var complete = false
        XCTAssertTrue(view.prepareTiles {
            view.document = nil
            complete = true
        })
        pump { complete }
        XCTAssertTrue(view.hasPresented(height: 360))
        let content = try XCTUnwrap(view.contentLayer)
        let oldTiles = try XCTUnwrap(content.sublayers)
        let after = FailedRasterDocument(html: "<body style='margin:0;height:80px;background:blue'></body>", css: nil)
        _ = after.layout(maxWidth: 300, preferredWidth: 300)
        view.document = after
        complete = false
        XCTAssertTrue(view.prepareTiles {
            view.document = nil
            complete = true
        })
        pump { complete }
        XCTAssertFalse(view.hasPresented(height: 80))
        XCTAssertTrue(view.hasPresented(height: 360))
        XCTAssertTrue(oldTiles.allSatisfy { $0.superlayer === content })
    }
}

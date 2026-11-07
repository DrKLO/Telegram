import XCTest
import AppKit
@testable import TwidgetRenderer

final class QueryPaintTests: XCTestCase {
    private func pump(_ seconds: TimeInterval, step: TimeInterval = 0.002, until done: () -> Bool = { false }) {
        let deadline = Date().addingTimeInterval(seconds)
        while !done() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(step))
        }
    }

    private func send(_ renderer: LiteHTMLWidgetRenderer, _ ops: [[String: Any]]) {
        let data = try! JSONSerialization.data(withJSONObject: ["type": "ops", "ops": ops])
        renderer.send(String(data: data, encoding: .utf8)!)
    }

    private func scale(_ transform: String?) -> Double? {
        guard let transform = transform, let open = transform.firstIndex(of: "(") else {
            return nil
        }
        let inner = transform[transform.index(after: open)...].prefix { $0 != ")" && $0 != "," }
        return Double(inner.trimmingCharacters(in: .whitespaces))
    }

    func testLayoutQueryBetweenClassRemoveAndAddDoesNotPresentTheIntermediateState() {
        _ = NSApplication.shared
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 300, height: 240), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.orderFrontRegardless()
        defer { window.orderOut(nil) }
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 300)
        var results: [Any] = []
        renderer.onMessage = { json in
            if json.contains("\"type\":\"result\""), let data = json.data(using: .utf8), let message = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] {
                results.append(message["result"] ?? NSNull())
            }
        }
        renderer.view.frame = NSRect(x: 0, y: 0, width: 300, height: 240)
        window.contentView?.addSubview(renderer.view)
        renderer.load()
        send(renderer, [
            ["op": "el", "id": 1, "tag": "body", "attrs": [], "adopt": "body"],
            ["op": "el", "id": 2, "tag": "style", "attrs": []],
            ["op": "css", "id": 2, "text": "body { margin: 0; background-color: #fff; } .box { width: 100px; height: 40px; background-color: #f00; } @keyframes pop { from { transform: scale(1.25); opacity: 0.5 } to { transform: scale(1); opacity: 1 } } .box.pop { animation: pop 2s linear; }"],
            ["op": "ins", "id": 2, "parent": 1],
            ["op": "el", "id": 3, "tag": "div", "attrs": [["class", "box pop"]]],
            ["op": "ins", "id": 3, "parent": 1],
        ])
        pump(10, until: { !renderer.isPreparingTiles && renderer.heldOpBatches == 0 && renderer.layoutCount > 0 })
        pump(0.4)
        guard let firstEnd = renderer.animationEnd(3) else {
            return XCTFail("pop animation is not running")
        }
        let presented = renderer.presentedDocument
        let composited = renderer.compositedElementIDs
        XCTAssertNotNil(presented)

        renderer.noteUserInput()
        renderer.send("{\"type\":\"requestFrame\"}")
        send(renderer, [["op": "attr", "id": 3, "name": "class", "value": "box"]])
        XCTAssertTrue(renderer.holdsForFrame)
        XCTAssertTrue(renderer.presentedDocument === presented)
        renderer.send("{\"type\":\"query\",\"qid\":1,\"query\":{\"q\":\"box\",\"id\":3}}")
        XCTAssertEqual((results.last as? [String: Any])?["offsetWidth"] as? Double, 100)
        XCTAssertNil(renderer.animationEnd(3))
        XCTAssertTrue(renderer.presentedDocument === presented)
        XCTAssertEqual(renderer.compositedElementIDs, composited)
        pump(0.004)
        XCTAssertTrue(renderer.presentedDocument === presented)
        XCTAssertEqual(renderer.compositedElementIDs, composited)

        send(renderer, [["op": "attr", "id": 3, "name": "class", "value": "box pop"]])
        pump(0.2, step: 0.001, until: { renderer.presentedDocument !== presented })
        XCTAssertFalse(renderer.presentedDocument === presented)
        guard let restartedEnd = renderer.animationEnd(3) else {
            return XCTFail("pop animation did not restart")
        }
        XCTAssertGreaterThan(restartedEnd, firstEnd + 0.3)
        let first = renderer.animationValues(3)
        XCTAssertEqual(scale(first?.transform) ?? 0, 1.25, accuracy: 0.01)
        renderer.destroy()
    }

    func testQueryWithoutFollowUpOpsPresentsByTheDeadline() {
        _ = NSApplication.shared
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 300, height: 240), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.orderFrontRegardless()
        defer { window.orderOut(nil) }
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 300)
        renderer.view.frame = NSRect(x: 0, y: 0, width: 300, height: 240)
        window.contentView?.addSubview(renderer.view)
        renderer.load()
        send(renderer, [
            ["op": "el", "id": 1, "tag": "body", "attrs": [], "adopt": "body"],
            ["op": "el", "id": 3, "tag": "div", "attrs": [["style", "width: 100px; height: 40px; background-color: #f00"]]],
            ["op": "ins", "id": 3, "parent": 1],
        ])
        pump(10, until: { !renderer.isPreparingTiles && renderer.heldOpBatches == 0 && renderer.layoutCount > 0 })
        pump(0.1)
        let presented = renderer.presentedDocument
        send(renderer, [["op": "attr", "id": 3, "name": "style", "value": "width: 120px; height: 40px; background-color: #00f"]])
        renderer.send("{\"type\":\"query\",\"qid\":1,\"query\":{\"q\":\"box\",\"id\":3}}")
        pump(0.15, until: { renderer.presentedDocument !== presented })
        XCTAssertFalse(renderer.presentedDocument === presented)
        XCTAssertEqual(renderer.elementBounds()[3]?.width, 120)
        renderer.destroy()
    }

    func testSilentScrollContentQueryKeepsOldTilesUntilPresentation() throws {
        _ = NSApplication.shared
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 120, height: 100), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.orderFrontRegardless()
        defer { window.orderOut(nil) }
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 120)
        renderer.view.frame = window.contentView!.bounds
        window.contentView?.addSubview(renderer.view)
        defer { renderer.destroy() }
        renderer.load()
        send(renderer, [
            ["op": "el", "id": 1, "tag": "body", "attrs": [["style", "margin:0"]], "adopt": "body"],
            ["op": "el", "id": 3, "tag": "div", "attrs": [["style", "width:120px;height:100px;overflow:auto"]]],
            ["op": "ins", "id": 3, "parent": 1],
            ["op": "el", "id": 4, "tag": "div", "attrs": [["style", "height:700px;background:red"]]],
            ["op": "ins", "id": 4, "parent": 3],
        ])
        pump(2, until: { renderer.scrolling.layers[3].map { $0.rasterCount == $0.tileCount } == true })
        let layer = try XCTUnwrap(renderer.scrolling.layers[3])
        let tile = try XCTUnwrap(layer.content.sublayers?.first { $0.frame.minY == 0 })
        let image = try XCTUnwrap(WidgetSurfaceImage.image(of: tile)) as! CGImage
        let count = layer.rasterCount
        let presented = renderer.presentedDocument
        renderer.noteUserInput()
        renderer.send("{\"type\":\"requestFrame\"}")
        send(renderer, [["op": "attr", "id": 4, "name": "style", "value": "height:700px;background:blue"]])
        renderer.send("{\"type\":\"query\",\"qid\":1,\"query\":{\"q\":\"box\",\"id\":4}}")
        XCTAssertTrue(renderer.presentedDocument === presented)
        XCTAssertEqual(layer.rasterCount, count)
        XCTAssertTrue(WidgetSurfaceImage.image(of: tile) as AnyObject? === image)
        pump(2, until: { renderer.presentedDocument !== presented && layer.rasterCount >= count + layer.tileCount })
        let document = try XCTUnwrap(renderer.document)
        for tile in layer.content.sublayers ?? [] {
            let scale = tile.contentsScale
            let expected = try XCTUnwrap(document.copyScrollContent(attribute: WidgetDOM.idAttribute, value: "3", rect: tile.frame.insetBy(dx: -1 / scale, dy: -1 / scale), scale: scale))
            let actual = try XCTUnwrap(WidgetSurfaceImage.image(of: tile)) as! CGImage
            XCTAssertEqual(actual.dataProvider?.data as Data?, expected.dataProvider?.data as Data?)
        }
    }
}

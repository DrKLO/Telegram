import XCTest
@testable import TwidgetRenderer

final class VisualQueryTests: XCTestCase {
    private func send(_ renderer: LiteHTMLWidgetRenderer, _ value: [String: Any]) {
        renderer.send(String(data: try! JSONSerialization.data(withJSONObject: value), encoding: .utf8)!)
    }

    private func make() -> LiteHTMLWidgetRenderer {
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 400)
        renderer.schedule = { _ in }
        renderer.load()
        send(renderer, ["type": "ops", "ops": [
            ["op": "el", "id": 7, "tag": "body", "adopt": "body", "attrs": []],
            ["op": "el", "id": 8, "tag": "div", "attrs": [["style", "width:100px;height:60px;background:red"]]],
            ["op": "ins", "parent": 7, "id": 8]
        ]])
        return renderer
    }

    private func query(_ renderer: LiteHTMLWidgetRenderer, _ kind: String) -> [String: Any] {
        var result: [String: Any] = [:]
        renderer.onMessage = { json in
            let message = (try? JSONSerialization.jsonObject(with: Data(json.utf8))) as? [String: Any]
            if message?["type"] as? String == "result" { result = message?["result"] as? [String: Any] ?? [:] }
        }
        send(renderer, ["type": "query", "qid": 1, "query": ["q": kind, "id": 8]])
        return result
    }

    func testVisualWritesAndSizeReadsDoNotRenderUntilPaintIsNeeded() {
        let renderer = make()
        defer { renderer.destroy() }
        let count = renderer.layoutCount
        for (name, value) in [("color", "blue"), ("background-color", "green"), ("box-shadow", "0 2px 4px black"), ("opacity", "0.7"), ("transform", "translateY(20px)")] {
            send(renderer, ["type": "ops", "ops": [["op": "style", "id": 8, "name": name, "value": value]]])
            XCTAssertEqual(query(renderer, "box")["offsetHeight"] as? Double, 60)
            XCTAssertEqual(renderer.layoutCount, count, name)
        }
        _ = query(renderer, "rect")
        XCTAssertGreaterThan(renderer.layoutCount, count)
    }

    func testPositionOffsetsAreRelativeToThePositionedParentPaddingEdge() {
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 400)
        renderer.schedule = { _ in }
        renderer.load()
        defer { renderer.destroy() }
        send(renderer, ["type": "ops", "ops": [
            ["op": "el", "id": 7, "tag": "body", "adopt": "body", "attrs": [["style", "margin:0;padding:12px"]]],
            ["op": "el", "id": 9, "tag": "div", "attrs": [["style", "position:relative;width:300px;height:180px;border:2px solid black;padding:10px"]]],
            ["op": "el", "id": 8, "tag": "div", "attrs": [["style", "position:absolute;left:20px;top:25px;width:60px;height:60px;border:1px solid red;padding:3px"]]],
            ["op": "ins", "parent": 7, "id": 9],
            ["op": "ins", "parent": 9, "id": 8]
        ]])
        let box = query(renderer, "box")
        XCTAssertEqual(box["offsetLeft"] as? Double, 20)
        XCTAssertEqual(box["offsetTop"] as? Double, 25)
        XCTAssertEqual(box["offsetParent"] as? Double, 9)
        send(renderer, ["type": "ops", "ops": [["op": "style", "id": 9, "name": "transform", "value": "translate(30px,40px)"]]])
        _ = query(renderer, "rect")
        XCTAssertEqual(query(renderer, "box")["offsetLeft"] as? Double, 20)
        XCTAssertEqual(query(renderer, "box")["offsetTop"] as? Double, 25)
    }

    func testOffsetDimensionsIgnoreVisualTransforms() {
        for (tag, style) in [("div", "display:block"), ("div", "display:inline-block"), ("textarea", "display:block"), ("div", "overflow:auto"), ("img", "display:inline")] {
            let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 400)
            renderer.schedule = { _ in }
            renderer.load()
            defer { renderer.destroy() }
            send(renderer, ["type": "ops", "ops": [
                ["op": "el", "id": 7, "tag": "body", "adopt": "body", "attrs": [["style", "margin:0;transform:scale(1.2);transform-origin:0 0"]]],
                ["op": "el", "id": 8, "tag": tag, "attrs": [["style", "\(style);box-sizing:content-box;width:100.25px;height:60.25px;padding:3px;border:2px solid black;transform:scale(1.5);transform-origin:0 0"]]],
                ["op": "ins", "parent": 7, "id": 8]
            ]])
            let visual = query(renderer, "rect")
            XCTAssertEqual(visual["width"] as? Double ?? 0, 198.45, accuracy: 0.01, "\(tag) \(style)")
            XCTAssertEqual(visual["height"] as? Double ?? 0, 126.45, accuracy: 0.01, "\(tag) \(style)")
            let box = query(renderer, "box")
            XCTAssertEqual(box["offsetWidth"] as? Double, 110, "\(tag) \(style)")
            XCTAssertEqual(box["offsetHeight"] as? Double, 70, "\(tag) \(style)")
        }
    }

    func testVisualRectCacheUpdatesAfterRotationAndAncestorTranslation() {
        let renderer = make()
        defer { renderer.destroy() }
        let initial = query(renderer, "rect")
        send(renderer, ["type": "ops", "ops": [
            ["op": "style", "id": 8, "name": "transform-origin", "value": "0 0"],
            ["op": "style", "id": 8, "name": "transform", "value": "rotate(90deg)"],
            ["op": "style", "id": 7, "name": "transform", "value": "translate(20px,30px)"]
        ]])
        let transformed = query(renderer, "rect")
        XCTAssertEqual(transformed["x"] as? Double ?? 0, (initial["x"] as! Double) - 40, accuracy: 0.01)
        XCTAssertEqual(transformed["y"] as? Double ?? 0, (initial["y"] as! Double) + 30, accuracy: 0.01)
        XCTAssertEqual(transformed["width"] as? Double ?? 0, 60, accuracy: 0.01)
        XCTAssertEqual(transformed["height"] as? Double ?? 0, 100, accuracy: 0.01)
        XCTAssertEqual(query(renderer, "box")["offsetWidth"] as? Double, 100)
        send(renderer, ["type": "ops", "ops": [["op": "style", "id": 8, "name": "transform", "value": "none"]]])
        XCTAssertEqual(query(renderer, "rect")["width"] as? Double, 100)
    }

    func testPositionOffsetsDoNotChangeWhenAnAncestorScrolls() {
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 400)
        renderer.schedule = { _ in }
        renderer.load()
        defer { renderer.destroy() }
        send(renderer, ["type": "ops", "ops": [
            ["op": "el", "id": 7, "tag": "body", "adopt": "body", "attrs": [["style", "margin:0;padding:12px"]]],
            ["op": "el", "id": 9, "tag": "div", "attrs": [["style", "position:relative;width:300px;height:100px;overflow:auto;border:2px solid black"]]],
            ["op": "el", "id": 10, "tag": "div", "attrs": [["style", "height:400px"]]],
            ["op": "el", "id": 8, "tag": "div", "attrs": [["style", "position:absolute;left:20px;top:200px;width:60px;height:60px"]]],
            ["op": "ins", "parent": 7, "id": 9],
            ["op": "ins", "parent": 9, "id": 10],
            ["op": "ins", "parent": 10, "id": 8],
            ["op": "prop", "id": 9, "name": "scrollTop", "value": 80]
        ]])
        XCTAssertEqual(query(renderer, "box")["offsetLeft"] as? Double, 20)
        XCTAssertEqual(query(renderer, "box")["offsetTop"] as? Double, 200)
        XCTAssertEqual(query(renderer, "box")["offsetParent"] as? Double, 9)
    }

    func testTextareaSizeQueriesFlushValuesAndRows() {
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 400)
        renderer.schedule = { _ in }
        renderer.load()
        defer { renderer.destroy() }
        send(renderer, ["type": "ops", "ops": [
            ["op": "el", "id": 7, "tag": "body", "adopt": "body", "attrs": []],
            ["op": "el", "id": 8, "tag": "textarea", "attrs": [["style", "display:block;width:300px;height:auto;font:16px/20px monospace;padding:6px 10px;border:2px solid black"]]],
            ["op": "ins", "parent": 7, "id": 8],
            ["op": "prop", "id": 8, "name": "value", "value": "a"]
        ]])
        XCTAssertEqual(query(renderer, "box")["offsetHeight"] as? Double, 56)
        XCTAssertEqual(query(renderer, "box")["clientHeight"] as? Double, 52)
        send(renderer, ["type": "ops", "ops": [["op": "prop", "id": 8, "name": "value", "value": "one\ntwo\nthree\nfour\nfive"]]])
        XCTAssertEqual(query(renderer, "box")["offsetHeight"] as? Double, 56)
        XCTAssertEqual(query(renderer, "box")["scrollHeight"] as? Double, 112)
        send(renderer, ["type": "ops", "ops": [["op": "attr", "id": 8, "name": "rows", "value": "4"]]])
        XCTAssertEqual(query(renderer, "box")["offsetHeight"] as? Double, 96)
        XCTAssertEqual(query(renderer, "box")["clientHeight"] as? Double, 92)
        send(renderer, ["type": "ops", "ops": [
            ["op": "style", "id": 8, "name": "box-sizing", "value": "border-box"],
            ["op": "style", "id": 8, "name": "height", "value": "40px"]
        ]])
        XCTAssertEqual(query(renderer, "box")["clientHeight"] as? Double, 36)
        XCTAssertEqual(query(renderer, "box")["scrollHeight"] as? Double, 112)
        send(renderer, ["type": "ops", "ops": [["op": "prop", "id": 8, "name": "value", "value": ""]]])
        XCTAssertEqual(query(renderer, "box")["scrollHeight"] as? Double, 36)
    }

    func testStickyOffsetsIncludeTheAppliedStickyPosition() {
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 400)
        renderer.schedule = { _ in }
        renderer.load()
        defer { renderer.destroy() }
        send(renderer, ["type": "ops", "ops": [
            ["op": "el", "id": 7, "tag": "body", "adopt": "body", "attrs": [["style", "margin:0;padding:12px"]]],
            ["op": "el", "id": 9, "tag": "div", "attrs": [["style", "position:relative;width:300px;height:70px;overflow:auto;border:2px solid black"]]],
            ["op": "el", "id": 10, "tag": "div", "attrs": [["style", "height:25px"]]],
            ["op": "el", "id": 8, "tag": "div", "attrs": [["style", "position:sticky;top:0;height:20px"]]],
            ["op": "el", "id": 11, "tag": "div", "attrs": [["style", "height:200px"]]],
            ["op": "ins", "parent": 7, "id": 9],
            ["op": "ins", "parent": 9, "id": 10],
            ["op": "ins", "parent": 9, "id": 8],
            ["op": "ins", "parent": 9, "id": 11]
        ]])
        XCTAssertEqual(query(renderer, "box")["offsetTop"] as? Double, 25)
        send(renderer, ["type": "ops", "ops": [["op": "prop", "id": 9, "name": "scrollTop", "value": 80]]])
        XCTAssertEqual(query(renderer, "box")["offsetTop"] as? Double, 80)
        XCTAssertEqual(query(renderer, "box")["offsetParent"] as? Double, 9)
    }

    func testLayoutMutationAfterVisualWritesStillFlushes() {
        let renderer = make()
        defer { renderer.destroy() }
        send(renderer, ["type": "ops", "ops": [
            ["op": "style", "id": 8, "name": "color", "value": "green"],
            ["op": "style", "id": 8, "name": "height", "value": "90px"]
        ]])
        XCTAssertEqual(query(renderer, "box")["offsetHeight"] as? Double, 90)
    }

    func testVisualWritesCoalesceAndEventuallyPaint() {
        let renderer = make()
        defer { renderer.destroy() }
        var scheduled: [() -> Void] = []
        renderer.schedule = { scheduled.append($0) }
        let count = renderer.layoutCount
        for color in ["blue", "green", "yellow"] {
            send(renderer, ["type": "ops", "ops": [["op": "style", "id": 8, "name": "color", "value": color]]])
        }
        XCTAssertEqual(renderer.layoutCount, count)
        XCTAssertEqual(scheduled.count, 1)
        scheduled.forEach { $0() }
        XCTAssertEqual(renderer.layoutCount, count + 1)
        XCTAssertEqual(query(renderer, "cs")["color"] as? String, "rgb(255, 255, 0)")
    }

    func testComputedStylesFlushPendingVisualWrites() {
        let renderer = make()
        defer { renderer.destroy() }
        let count = renderer.layoutCount
        send(renderer, ["type": "ops", "ops": [["op": "style", "id": 8, "name": "color", "value": "blue"]]])
        XCTAssertEqual(query(renderer, "cs")["color"] as? String, "rgb(0, 0, 255)")
        XCTAssertGreaterThan(renderer.layoutCount, count)
    }

    func testComputedLineHeightPreservesNormalAndResolvesLengths() {
        let renderer = make()
        defer { renderer.destroy() }
        send(renderer, ["type": "ops", "ops": [["op": "style", "id": 8, "name": "line-height", "value": "normal"]]])
        XCTAssertEqual(query(renderer, "cs")["line-height"] as? String, "normal")
        send(renderer, ["type": "ops", "ops": [
            ["op": "style", "id": 8, "name": "font-size", "value": "20px"],
            ["op": "style", "id": 8, "name": "line-height", "value": "1.5"]
        ]])
        XCTAssertEqual(query(renderer, "cs")["line-height"] as? String, "30px")
    }

    func testVisualFeedbackAfterUserInputRendersImmediately() {
        let renderer = make()
        defer { renderer.destroy() }
        let count = renderer.layoutCount
        renderer.noteUserInput()
        send(renderer, ["type": "ops", "ops": [["op": "style", "id": 8, "name": "color", "value": "blue"]]])
        XCTAssertGreaterThan(renderer.layoutCount, count)
        XCTAssertEqual(query(renderer, "cs")["color"] as? String, "rgb(0, 0, 255)")
    }

    func testStyleAttributeSelectorsDisableTheVisualShortcut() {
        let renderer = make()
        defer { renderer.destroy() }
        send(renderer, ["type": "ops", "ops": [
            ["op": "el", "id": 9, "tag": "style", "attrs": []],
            ["op": "text", "id": 10, "data": "div[style*=blue]{height:120px!important}"],
            ["op": "ins", "parent": 9, "id": 10],
            ["op": "ins", "parent": 7, "id": 9]
        ]])
        let count = renderer.layoutCount
        send(renderer, ["type": "ops", "ops": [["op": "style", "id": 8, "name": "color", "value": "blue"]]])
        XCTAssertGreaterThan(renderer.layoutCount, count)
    }
}

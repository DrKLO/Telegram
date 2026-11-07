import XCTest
import CoreGraphics
@testable import TwidgetRenderer

final class WidgetJSONWriterTests: XCTestCase {
    private func legacy(_ object: Any) -> String? {
        guard JSONSerialization.isValidJSONObject(object), let data = try? JSONSerialization.data(withJSONObject: object) else {
            return nil
        }
        return String(data: data, encoding: .utf8)
    }

    private func parse(_ json: String) -> NSObject? {
        return (try? JSONSerialization.jsonObject(with: Data(json.utf8), options: [.fragmentsAllowed])) as? NSObject
    }

    private func assertEquivalent(_ object: Any, file: StaticString = #filePath, line: UInt = #line) {
        let old = legacy(object)
        let new = WidgetJSONWriter.encode(object)
        guard let old = old else {
            XCTAssertNil(new, "\(object)", file: file, line: line)
            return
        }
        guard let new = new else {
            XCTFail("writer declined \(object)", file: file, line: line)
            return
        }
        XCTAssertNotNil(parse(new), new, file: file, line: line)
        XCTAssertEqual(parse(old), parse(new), "\(old) vs \(new)", file: file, line: line)
        assertBooleansMatch(parse(old), parse(new), file: file, line: line)
    }

    private func assertBooleansMatch(_ a: Any?, _ b: Any?, file: StaticString, line: UInt) {
        switch (a, b) {
        case let (x as NSDictionary, y as NSDictionary):
            for (key, value) in x {
                assertBooleansMatch(value, y[key], file: file, line: line)
            }
        case let (x as NSArray, y as NSArray):
            for (left, right) in zip(x, y) {
                assertBooleansMatch(left, right, file: file, line: line)
            }
        case let (x as NSNumber, y as NSNumber):
            XCTAssertEqual(CFGetTypeID(x) == CFBooleanGetTypeID(), CFGetTypeID(y) == CFBooleanGetTypeID(), "\(x) vs \(y)", file: file, line: line)
        default:
            break
        }
    }

    static let strings: [String] = [
        "", "a", "plain text", "quote\"inside", "back\\slash", "slash/</script>", "\u{0}\u{1}\u{8}\u{9}\u{A}\u{B}\u{C}\u{D}\u{1F}\u{7F}",
        "tab\tnew\nline\rret", "unicode é ü 中文 🙂 👩‍👩‍👧", "\u{2028}\u{2029}", "\u{FEFF}bom", "mixed \"\\/\u{1}🙂",
        String(repeating: "long-ascii-", count: 200), String(repeating: "\"", count: 50)
    ]

    static let numbers: [Double] = [
        0, -0.0, 1, -1, 0.5, 0.1, 1.0 / 3.0, -2.75, 123.456, 1e-7, 5e-324, 2.2250738585072014e-308, 1e15, 1e15 + 0.5, 1e16, 9007199254740993, 1.7976931348623157e308,
        -1e300, 4503599627370496.5, 1234567.0, 0.000001, 100, 1e21, 12.5e-10, Double(Float(0.1)), Double(CGFloat(33.333333))
    ]

    func testStringsMatchLegacy() {
        for text in WidgetJSONWriterTests.strings {
            assertEquivalent(["type": "event", "ev": ["type": "keydown", "key": text, "code": text]])
            assertEquivalent([text: text])
            assertEquivalent([text])
        }
    }

    func testLoneSurrogatesProduceValidJSON() {
        let samples = [
            NSString(characters: [0x61, 0xD800, 0x22, 0xDFFF], length: 4) as String,
            String(utf16CodeUnits: [0x61, 0xD800, 0x62], count: 3),
            String(utf16CodeUnits: [0xDC00], count: 1),
            String(utf16CodeUnits: [0xD83D, 0xDE42, 0xD83D], count: 3)
        ]
        for raw in samples {
            let json = WidgetJSONWriter.encode(["type": "event", "ev": ["key": raw]])
            XCTAssertNotNil(json)
            XCTAssertNotNil(String(validatingUTF8: json!))
            let parsed = (parse(json!) as? [String: Any])?["ev"] as? [String: String]
            XCTAssertNotNil(parsed)
            XCTAssertEqual(parsed?["key"]?.unicodeScalars.contains("\u{FFFD}"), true)
        }
    }

    func testNumbersMatchLegacy() {
        for number in WidgetJSONWriterTests.numbers {
            assertEquivalent(["type": "frame", "time": number])
            assertEquivalent([number, -number, Float(number), CGFloat(number)])
            assertEquivalent(["box": ["offsetWidth": number, "offsetTop": -number]])
            XCTAssertEqual(parse(WidgetJSONWriter.frame(time: number)!), parse(legacy(["type": "frame", "time": number])!))
        }
        assertEquivalent([Int.max, Int.min, 0, -7, Int64.max, Int32.min] as [Any])
        assertEquivalent([NSNumber(value: UInt64.max), NSNumber(value: Int64.min), NSNumber(value: 1.5), NSNumber(value: true), NSNumber(value: false), NSNumber(value: Int8(-3)), NSNumber(value: UInt8(200)), NSNumber(value: Float(2.5))])
    }

    func testNonFiniteMatchesLegacy() {
        for bad in [Double.nan, .infinity, -.infinity] {
            XCTAssertNil(WidgetJSONWriter.frame(time: bad))
            XCTAssertNil(WidgetJSONWriter.encode(["type": "frame", "time": bad]))
            XCTAssertNil(WidgetJSONWriter.encode(["box": ["x": 1.0, "y": bad]]))
            XCTAssertNil(WidgetJSONWriter.encode([["a": [1, bad] as [Any]]]))
            XCTAssertNil(WidgetJSONWriter.encode([CGFloat(bad)]))
            XCTAssertNil(WidgetJSONWriter.encode([NSNumber(value: bad)]))
            XCTAssertFalse(JSONSerialization.isValidJSONObject(["type": "frame", "time": bad]))
        }
    }

    func testStructuresMatchLegacy() {
        let parsedQid = (try! JSONSerialization.jsonObject(with: Data(#"{"qid":17,"flag":true,"f":2.5,"n":null}"#.utf8))) as! [String: Any]
        let samples: [Any] = [
            ["type": "ready"],
            ["type": "violation", "detail": "bad \"thing\"\n"],
            ["type": "gesture", "wants": true],
            ["type": "result", "qid": parsedQid["qid"]!, "result": ["offsetWidth": 100.0, "offsetHeight": 60.0, "offsetParent": 0] as [String: Double]],
            ["type": "result", "qid": parsedQid["qid"]!, "result": ["color": "rgb(0, 0, 0)", "font-family": "\"Helvetica\", sans-serif"] as [String: String]],
            ["type": "result", "qid": NSNull(), "result": [["x": 1.5, "y": 2, "width": 3, "height": 4]]],
            ["type": "result", "qid": parsedQid["qid"]!, "result": parsedQid],
            ["type": "result", "qid": 3, "result": ["list": [] as [Any]]],
            ["type": "result", "qid": 3, "result": ["matches": false]],
            ["type": "result", "qid": 3, "result": 42],
            ["type": "result", "qid": 3, "result": "text"],
            ["type": "result", "qid": 3, "result": NSNull()],
            ["type": "result", "qid": 3, "result": [[[1, [2, [3, ["deep": [true, false, NSNull()]]]]]]] as [Any]],
            ["type": "result", "qid": 3, "result": ["a": 1, "b": true, "c": 2.5] as NSDictionary],
            ["type": "result", "qid": 3, "result": ["s", 1, true] as NSArray],
            ["type": "result", "qid": 3, "result": ["flag": NSNumber(value: true), "one": NSNumber(value: 1)] as [String: Any]],
            ["type": "resize", "entries": [["id": 5, "width": 10.5, "height": 20.25], ["id": 6, "width": 0, "height": 0]]],
            ["type": "event", "ev": ["type": "scroll", "targetId": 9, "bubbles": false]],
            ["type": "event", "ev": ["type": "click", "targetId": 1, "pointerId": 1, "pointerType": "mouse", "isPrimary": true, "modifiers": 5]],
            ["type": "result", "qid": 3, "result": ["opaque": Date()]],
            ["type": "result", "qid": 3, "result": [NSObject()]]
        ]
        for sample in samples {
            assertEquivalent(sample)
        }
    }

    func testMouseEventsMatchLegacy() {
        var seed: UInt64 = 0x9E3779B97F4A7C15
        func next() -> Double {
            seed = seed &* 6364136223846793005 &+ 1442695040888963407
            return Double(Int64(bitPattern: seed >> 11) % 2_000_000) / 997.0 - 1000
        }
        for index in 0..<500 {
            let type = ["pointermove", "mousemove", "click", "dblclick", "mouse\"down\\/\u{1}"][index % 5]
            let point = CGPoint(x: next(), y: next()), offset = CGPoint(x: next(), y: next()), movement = CGPoint(x: next() / 7, y: next() / 3)
            let buttons = index % 3, detail = type.hasSuffix("click") ? index % 4 : 0, modifiers = index % 16, target = index * 37
            let event = mouseEvent(type, target: target, point: point, offset: offset, movement: movement, buttons: buttons, detail: detail, modifiers: modifiers, defaultActions: type == "click")
            assertEquivalent(["type": "event", "ev": event])
            let json = LiteHTMLWidgetRenderer.mouseEventJSON(type, target: target, point: point, offset: offset, movement: movement, buttons: buttons, detail: detail, modifiers: modifiers, defaultActions: type == "click")
            XCTAssertEqual(parse("{\"type\":\"event\",\"ev\":" + json! + "}"), parse(legacy(["type": "event", "ev": event])!))
        }
        XCTAssertNil(LiteHTMLWidgetRenderer.mouseEventJSON("pointermove", target: 1, point: CGPoint(x: CGFloat.nan, y: 0), offset: .zero, movement: .zero, buttons: 0, detail: 0, modifiers: 0, defaultActions: false))
        XCTAssertNil(LiteHTMLWidgetRenderer.mouseEventJSON("pointermove", target: 1, point: .zero, offset: .zero, movement: CGPoint(x: 0, y: CGFloat.infinity), buttons: 0, detail: 0, modifiers: 0, defaultActions: false))
    }

    private func mouseEvent(_ type: String, target: Int, point: CGPoint, offset: CGPoint, movement: CGPoint, buttons: Int, detail: Int, modifiers: Int, defaultActions: Bool) -> [String: Any] {
        var payload: [String: Any] = [
            "type": type, "targetId": target, "clientX": Double(point.x), "clientY": Double(point.y), "offsetX": Double(offset.x), "offsetY": Double(offset.y),
            "pageX": Double(point.x), "pageY": Double(point.y), "movementX": Double(movement.x), "movementY": Double(movement.y), "button": 0, "buttons": buttons,
            "detail": detail, "pointerId": 1, "pointerType": "mouse", "isPrimary": true, "pressure": buttons != 0 ? 0.5 : 0, "modifiers": modifiers
        ]
        if defaultActions {
            payload["defaultActions"] = true
        }
        return payload
    }

    private func mouseEvent(x: CGFloat, y: CGFloat, buttons: Int, type: String) -> [String: Any] {
        return [
            "type": type, "targetId": 12, "clientX": Double(x), "clientY": Double(y), "offsetX": Double(x - 3.5), "offsetY": Double(y - 7.25),
            "pageX": Double(x), "pageY": Double(y), "movementX": Double(x / 7), "movementY": Double(y / 3), "button": 0, "buttons": buttons,
            "detail": type.hasSuffix("click") ? 2 : 0, "pointerId": 1, "pointerType": "mouse", "isPrimary": true, "pressure": buttons != 0 ? 0.5 : 0, "modifiers": 0
        ]
    }

    func testRendererPostsParseableMessages() {
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 300)
        defer { renderer.destroy() }
        var messages: [String] = []
        renderer.onMessage = { messages.append($0) }
        renderer.post(["type": "violation", "detail": "x\"y\u{1}"])
        renderer.post(["type": "frame", "time": Double.nan])
        renderer.postFrame(time: 16.5)
        renderer.postFrame(time: .infinity)
        XCTAssertEqual(messages.count, 2)
        XCTAssertEqual((parse(messages[0]) as? [String: String])?["detail"], "x\"y\u{1}")
        XCTAssertEqual((parse(messages[1]) as? [String: AnyHashable])?["time"], 16.5 as AnyHashable)
    }

    private func measure(_ label: String, iterations: Int, _ body: () -> Void) -> Double {
        for _ in 0..<min(iterations / 10, 1000) { body() }
        let start = CFAbsoluteTimeGetCurrent()
        for _ in 0..<iterations { body() }
        let micros = (CFAbsoluteTimeGetCurrent() - start) * 1e6 / Double(iterations)
        print(String(format: "BENCH %@ %.3f us", label, micros))
        return micros
    }

    func testBenchmark() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["TWIDGET_BENCH"] != nil)
        let n = 100_000
        var sink = 0
        _ = measure("frame legacy", iterations: n) { sink &+= legacy(["type": "frame", "time": 1234.5678 as Double])?.utf8.count ?? 0 }
        _ = measure("frame dict-writer", iterations: n) { sink &+= WidgetJSONWriter.encode(["type": "frame", "time": 1234.5678 as Double])?.utf8.count ?? 0 }
        _ = measure("frame fixed", iterations: n) { sink &+= WidgetJSONWriter.frame(time: 1234.5678)?.utf8.count ?? 0 }
        let move = mouseEvent(x: 123.25, y: 45.5, buttons: 0, type: "pointermove")
        _ = measure("event legacy", iterations: n) { sink &+= legacy(["type": "event", "ev": move])?.utf8.count ?? 0 }
        _ = measure("event writer", iterations: n) { sink &+= WidgetJSONWriter.encode(["type": "event", "ev": move])?.utf8.count ?? 0 }
        _ = measure("event build+legacy", iterations: n) { sink &+= legacy(["type": "event", "ev": mouseEvent(x: 123.25, y: 45.5, buttons: 0, type: "pointermove")])?.utf8.count ?? 0 }
        _ = measure("event build+writer", iterations: n) { sink &+= WidgetJSONWriter.encode(["type": "event", "ev": mouseEvent(x: 123.25, y: 45.5, buttons: 0, type: "pointermove")])?.utf8.count ?? 0 }
        _ = measure("event fixed", iterations: n) { sink &+= ("{\"type\":\"event\",\"ev\":" + LiteHTMLWidgetRenderer.mouseEventJSON("pointermove", target: 12, point: CGPoint(x: 123.25, y: 45.5), offset: CGPoint(x: 119.75, y: 38.25), movement: CGPoint(x: 1.5, y: -0.5), buttons: 0, detail: 0, modifiers: 0, defaultActions: false)! + "}").utf8.count }
        let box: [String: Double] = ["offsetWidth": 100, "offsetHeight": 60, "offsetTop": 12.5, "offsetLeft": 8, "offsetParent": 0, "clientWidth": 100, "clientHeight": 60, "clientTop": 0, "clientLeft": 0, "scrollWidth": 100, "scrollHeight": 60, "scrollTop": 0, "scrollLeft": 0]
        let qid: Any = NSNumber(value: 42)
        _ = measure("box result legacy", iterations: n) { sink &+= legacy(["type": "result", "qid": qid, "result": box])?.utf8.count ?? 0 }
        _ = measure("box result writer", iterations: n) { sink &+= WidgetJSONWriter.encode(["type": "result", "qid": qid, "result": box])?.utf8.count ?? 0 }
        let rect: [String: Double] = ["x": 8, "y": 12.5, "width": 100, "height": 60, "top": 12.5, "left": 8, "right": 108, "bottom": 72.5]
        _ = measure("rect result legacy", iterations: n) { sink &+= legacy(["type": "result", "qid": qid, "result": rect])?.utf8.count ?? 0 }
        _ = measure("rect result writer", iterations: n) { sink &+= WidgetJSONWriter.encode(["type": "result", "qid": qid, "result": rect])?.utf8.count ?? 0 }
        let queryJSON = #"{"type":"query","qid":42,"query":{"q":"rect","id":8}}"#
        _ = measure("query parse", iterations: n) { sink &+= ((try? JSONSerialization.jsonObject(with: queryJSON.data(using: .utf8)!)) as? [String: Any])?.count ?? 0 }
        XCTAssertNotEqual(sink, 0)
    }

    func testRendererQueryBenchmark() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["TWIDGET_BENCH"] != nil)
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 400)
        defer { renderer.destroy() }
        renderer.schedule = { _ in }
        renderer.load()
        renderer.send(#"{"type":"ops","ops":[{"op":"el","id":7,"tag":"body","adopt":"body","attrs":[]},{"op":"el","id":8,"tag":"div","attrs":[["style","width:100px;height:60px;background:red;color:blue"]]},{"op":"ins","parent":7,"id":8}]}"#)
        var count = 0
        renderer.onMessage = { _ in count += 1 }
        for kind in ["rect", "box", "cs"] {
            let json = "{\"type\":\"query\",\"qid\":42,\"query\":{\"q\":\"\(kind)\",\"id\":8}}"
            _ = measure("send query \(kind)", iterations: 20_000) { renderer.send(json) }
        }
        let prop = #"{"type":"query","qid":42,"query":{"q":"cs","id":8,"prop":"color"}}"#
        _ = measure("send query cs prop", iterations: 20_000) { renderer.send(prop) }
        XCTAssertGreaterThan(count, 0)
    }
}

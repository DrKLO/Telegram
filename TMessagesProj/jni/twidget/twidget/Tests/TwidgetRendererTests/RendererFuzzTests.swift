import XCTest
@testable import TwidgetRenderer
import WidgetSandboxProtocol

private struct SeededRandom: RandomNumberGenerator {
    var state: UInt64
    mutating func next() -> UInt64 {
        state ^= state << 13
        state ^= state >> 7
        state ^= state << 17
        return state
    }
}

final class RendererFuzzTests: XCTestCase {
    private static let hostileNames = [
        "div onclick=alert(1)", "a\" onload=\"x", "img src=x onerror=y", "script", "SCRIPT", "iframe", "style", "x>y", "<b>", "", " ", "data-lh-id",
        "data-lh-anim", "onmouseover", "srcdoc", "style\"", "color\";background:url(x", "--lh-anim", "-lh-anim", "-\u{0}", "\u{202e}span", String(repeating: "a", count: 5000),
        "svg", "canvas", "linearGradient", "use", "foreignObject", "a", "xlink:href", "href", "src", "width", "height", "class", "id",
    ]
    private static let hostileValues = [
        "\"><script>alert(1)</script>", "</style><img src=x onerror=alert(1)>", "javascript:alert(1)", "url(javascript:alert(1))", "expression(alert(1))",
        "light-dark(" + String(repeating: "light-dark(", count: 200) + "red", "#lg", "url(#a,b>c)", "1e308", "-1e308", "NaN", "Infinity",
        "@keyframes k { from { color: red\" } to { \"><b> } }", "\u{0}\u{1}\u{fffe}", String(repeating: "x", count: 100_000), "translate(1e308px, NaN)",
        "rgba(1e309, -1, 2, 3)", "calc(", "var(--x", ";;;", "!important", "data:image/svg+xml,<svg onload=alert(1)>", "twcanvas:x-1-1",
    ]
    private static let tags = ["div", "span", "p", "b", "style", "svg", "g", "path", "rect", "text", "canvas", "img", "a", "table", "tr", "td", "linearGradient", "stop", "ul", "li"]
    private static let numbers: [Any] = [0, 1, -1, 3.5, 1e9, -1e9, 1e300, -1e300, 4096, 8192, 100_000, 2147483648.0, 9.9e307]

    private var random = SeededRandom(state: 0x9E3779B97F4A7C15)

    private func pick<T>(_ items: [T]) -> T {
        return items[Int(random.next() % UInt64(items.count))]
    }

    private func chance(_ percent: UInt64) -> Bool {
        return random.next() % 100 < percent
    }

    private func id() -> Int {
        return Int(random.next() % 300) + (chance(3) ? -5 : 1)
    }

    private func value() -> Any {
        if chance(30) {
            return pick(RendererFuzzTests.hostileValues)
        }
        if chance(20) {
            return pick(RendererFuzzTests.numbers)
        }
        return pick(["red", "10px", "1", "scale(1.2)", "translateX(4px)", "#fff", "block", "none", "hello world", "pulse 1s infinite", "transform .3s ease", "light-dark(#000, #fff)"])
    }

    private func name() -> String {
        return chance(40) ? pick(RendererFuzzTests.hostileNames) : pick(["class", "id", "style", "width", "height", "fill", "d", "x", "y", "href", "src", "data-x", "aria-label", "viewBox"])
    }

    private func canvasCommand() -> [Any] {
        let methods = ["save", "restore", "scale", "rotate", "translate", "transform", "setTransform", "resetTransform", "clearRect", "fillRect", "strokeRect", "beginPath", "closePath", "moveTo", "lineTo", "bezierCurveTo", "quadraticCurveTo", "arc", "arcTo", "ellipse", "rect", "roundRect", "fill", "stroke", "clip", "fillText", "strokeText", "setLineDash", "putImageData", "drawImage", "bogus"]
        let props = ["fillStyle", "strokeStyle", "lineWidth", "lineCap", "lineJoin", "miterLimit", "lineDashOffset", "font", "textAlign", "textBaseline", "globalAlpha", "globalCompositeOperation", "shadowBlur", "shadowColor", "shadowOffsetX", "shadowOffsetY"]
        switch random.next() % 8 {
        case 0:
            return ["s", pick(props), value()]
        case 1:
            return ["r", pick(RendererFuzzTests.numbers), pick(RendererFuzzTests.numbers)]
        case 2:
            return ["g", id(), pick(["linear", "radial", "conic", "pattern", "x"]), 0, 0, pick(RendererFuzzTests.numbers), 10, 5, 20]
        case 3:
            return ["gs", id(), pick(RendererFuzzTests.numbers), value()]
        case 4:
            return ["pn", id(), chance(50) ? "M0 0L10 10Z" : value()]
        case 5:
            return ["pc", id(), pick(methods), pick(RendererFuzzTests.numbers), pick(RendererFuzzTests.numbers), 5, 0, 6.3]
        case 6:
            return ["c", "putImageData", ["b64": "AAAA", "w": pick(RendererFuzzTests.numbers), "h": pick(RendererFuzzTests.numbers)], 0, 0]
        default:
            var args: [Any] = ["c", pick(methods)]
            for _ in 0..<Int(random.next() % 9) {
                args.append(chance(15) ? ["g": id()] : (chance(10) ? ["p": id()] : (chance(5) ? ["n": id()] : (chance(10) ? value() : pick(RendererFuzzTests.numbers)))))
            }
            return args
        }
    }

    private func op() -> [String: Any] {
        switch random.next() % 13 {
        case 0, 1:
            let attrs: [[Any]] = (0..<Int(random.next() % 4)).map { _ in [name(), value()] }
            var result: [String: Any] = ["op": "el", "id": id(), "tag": chance(20) ? pick(RendererFuzzTests.hostileNames) : pick(RendererFuzzTests.tags), "attrs": attrs]
            if chance(10) {
                result["ns"] = "svg"
            }
            if chance(3) {
                result["adopt"] = pick(["html", "head", "body"])
            }
            return result
        case 2:
            return ["op": "text", "id": id(), "data": value()]
        case 3, 4:
            return ["op": "ins", "id": id(), "parent": chance(5) ? 0 : id(), "before": chance(50) ? NSNull() : id()]
        case 5:
            return ["op": "attr", "id": id(), "name": name(), "value": value()]
        case 6:
            return ["op": "rattr", "id": id(), "name": name()]
        case 7:
            return ["op": "style", "id": id(), "name": chance(50) ? name() : pick(["opacity", "transform", "animation", "transition", "color", "--x"]), "value": value(), "important": chance(10)]
        case 8:
            return ["op": "css", "id": id(), "text": pick([".a{animation:k 1s infinite} @keyframes k{from{opacity:0}to{opacity:1}}", "</style><script>x</script>", "* { transition: all 1s } div { transform: scale(2) }", "@keyframes x { 50% { transform: none; color: light-dark(red, blue) } }"]) + (chance(30) ? (value() as? String ?? "") : "")]
        case 9:
            return ["op": "rm", "id": id()]
        case 10:
            return ["op": "data", "id": id(), "data": value()]
        case 11:
            return ["op": "canvas", "id": id(), "cmds": (0..<Int(random.next() % 20)).map { _ in canvasCommand() }]
        default:
            return ["op": "call", "id": id(), "method": pick(["observeResize", "unobserveResize", "focus", "x"]), "args": []]
        }
    }

    private func message() -> [String: Any] {
        switch random.next() % 10 {
        case 0:
            let q: [String: Any] = ["q": pick(["rect", "rects", "box", "cs", "svg", "measureText", "imageData", "canvasCall", "elementFromPoint", "validity", "matchMedia", "viewport", "nope"]), "id": id(), "x": pick(RendererFuzzTests.numbers), "y": 0, "w": pick(RendererFuzzTests.numbers), "h": pick(RendererFuzzTests.numbers), "text": value(), "font": value(), "query": value(), "method": pick(["isPointInPath", "getTransform", "getLineDash", "toDataURL", "x"]), "args": [1, 2]]
            return ["type": "query", "qid": Int(random.next() % 1000), "query": q]
        case 1:
            return ["type": pick(["listen", "preventDefault", "requestFrame", "bogus"]), "types": ["click", "pointermove"], "keys": ["1:pointerdown"]]
        default:
            return ["type": "ops", "ops": (0..<Int(random.next() % 25 + 1)).map { _ in op() }]
        }
    }

    private static func assertSafe(_ html: String, file: StaticString = #filePath, line: UInt = #line) {
        let lower = html.lowercased()
        XCTAssertFalse(lower.contains("<script"), "script tag", file: file, line: line)
        XCTAssertFalse(lower.contains("<iframe"), "iframe tag", file: file, line: line)
        let tagPattern = try! NSRegularExpression(pattern: "<([^/!][^\\s>]*)")
        for match in tagPattern.matches(in: html, range: NSRange(html.startIndex..., in: html)) {
            let tag = (html as NSString).substring(with: match.range(at: 1))
            XCTAssertTrue(WidgetNames.isTag(tag), "bad tag \(tag)", file: file, line: line)
        }
        let attributePattern = try! NSRegularExpression(pattern: "<[A-Za-z][A-Za-z0-9-]*((?:\\s+[^\\s=>]+=\"[^\"]*\")*)\\s*>")
        var checked = 0
        for match in attributePattern.matches(in: html, range: NSRange(html.startIndex..., in: html)) {
            let attributes = (html as NSString).substring(with: match.range(at: 1))
            for pair in try! NSRegularExpression(pattern: "\\s+([^\\s=>]+)=\"").matches(in: attributes, range: NSRange(attributes.startIndex..., in: attributes)) {
                let name = (attributes as NSString).substring(with: pair.range(at: 1))
                XCTAssertTrue(WidgetNames.isAttribute(name), "bad attribute \(name)", file: file, line: line)
                XCTAssertFalse(name.lowercased().hasPrefix("on"), "event handler \(name)", file: file, line: line)
            }
            checked += 1
        }
        let tagsInHTML = tagPattern.numberOfMatches(in: html, range: NSRange(html.startIndex..., in: html))
        let styleBodies = try! NSRegularExpression(pattern: "<style[^>]*>").numberOfMatches(in: html, range: NSRange(html.startIndex..., in: html))
        XCTAssertGreaterThanOrEqual(checked + styleBodies, tagsInHTML - styleBodies * 0, "every element parses as name + quoted attributes", file: file, line: line)
    }

    func testHostileNamesNeverReachTheMarkup() {
        let dom = WidgetDOM()
        var violations = 0
        dom.onViolation = { _ in violations += 1 }
        dom.apply(["op": "el", "id": 2, "tag": "body", "attrs": [], "adopt": "body"])
        dom.apply(["op": "el", "id": 5, "tag": "div onclick=alert(1)", "attrs": []])
        dom.apply(["op": "el", "id": 6, "tag": "div", "attrs": [["a\" onload=\"x", "1"], ["style", "opacity:0.5;color\";background:url(x):1"], ["data-lh-id", "99"], ["onclick", "x"]]])
        dom.apply(["op": "ins", "id": 6, "parent": 2])
        dom.apply(["op": "style", "id": 6, "name": "color\";x:1", "value": "red"])
        dom.apply(["op": "attr", "id": 6, "name": "title", "value": "\"><script>alert(1)</script>"])
        let html = dom.serialize(theme: "light")
        RendererFuzzTests.assertSafe(html)
        XCTAssertFalse(html.contains("onclick"))
        XCTAssertFalse(html.contains("onload"))
        XCTAssertFalse(html.contains("data-lh-id=\"99\""))
        XCTAssertTrue(html.contains("opacity:0.5"))
        XCTAssertTrue(html.contains("&quot;&gt;&lt;script&gt;"))
        XCTAssertGreaterThanOrEqual(violations, 3)
    }

    func testTypedOpsRenderLikeTheirDictionaries() {
        let typed = WidgetDOM()
        let generic = WidgetDOM()
        for dom in [typed, generic] {
            dom.apply(["op": "el", "id": 1, "tag": "body", "attrs": [], "adopt": "body"])
        }
        var typedStyles = 0
        for round in 0..<800 {
            let batch: [[String: Any]] = (0..<Int(random.next() % 12 + 1)).map { _ in
                if chance(40) {
                    var o: [String: Any] = ["op": "style", "id": id(), "name": chance(25) ? pick(RendererFuzzTests.hostileNames) : pick(["transform", "opacity", "color", "left", "width", "background"]), "value": value()]
                    if chance(50) { o["important"] = chance(50) }
                    return o
                }
                return op()
            }
            for o in batch {
                let op = WidgetOp(o)
                if op.kind == .style { typedStyles += 1 }
                typed.apply(op)
                var padded = o
                padded["pad1"] = 1
                padded["pad2"] = 2
                padded["pad3"] = 3
                XCTAssertEqual(WidgetOp(padded).kind, .other)
                generic.apply(WidgetOp(padded))
            }
            if round % 20 == 0 {
                let theme = round % 40 == 0 ? "dark" : "light"
                XCTAssertEqual(typed.serialize(theme: theme), generic.serialize(theme: theme))
            }
        }
        XCTAssertEqual(typed.serialize(theme: "light"), generic.serialize(theme: "light"))
        XCTAssertGreaterThan(typedStyles, 1500)
    }

    func testTypedOpsSurviveTheJSONFallback() throws {
        var ops: [WidgetOp] = []
        for _ in 0..<2000 {
            switch random.next() % 3 {
            case 0: ops.append(WidgetOp(style: id(), name: pick(RendererFuzzTests.hostileNames), value: "\(value())", important: chance(50)))
            case 1: ops.append(WidgetOp(attribute: id(), name: pick(RendererFuzzTests.hostileNames), value: "\(value())"))
            default: ops.append(WidgetOp(op()))
            }
        }
        let parsed = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(WidgetOp.json(ops).utf8)) as? [[String: Any]])
        XCTAssertEqual(parsed.count, ops.count)
        for (op, back) in zip(ops, parsed) {
            XCTAssertEqual(back["op"] as? String, op.op)
            XCTAssertEqual((back["id"] as? NSNumber)?.intValue, op.kind == .other ? (op["id"] as? NSNumber)?.intValue : op.id)
            if op.kind == .style || op.kind == .attribute {
                XCTAssertEqual(back["name"] as? String, op.name)
                XCTAssertEqual(back["value"] as? String, op.value)
            }
        }
    }

    func testCachedSerializationMatchesFresh() {
        let warm = WidgetDOM()
        let cold = WidgetDOM()
        for dom in [warm, cold] {
            dom.apply(["op": "el", "id": 1, "tag": "body", "attrs": [], "adopt": "body"])
        }
        for round in 0..<600 {
            let batch: [[String: Any]] = (0..<Int(random.next() % 12 + 1)).map { _ in
                var o = op()
                if o["op"] as? String == "canvas" {
                    o = ["op": "data", "id": id(), "data": "x"]
                }
                return o
            }
            for o in batch {
                warm.apply(o)
                cold.apply(o)
            }
            let theme = round % 97 == 0 ? "dark" : "light"
            _ = warm.serialize(theme: theme)
            if round % 25 == 0 {
                XCTAssertEqual(warm.serialize(theme: theme), WidgetDOM.invalidatingCaches(cold).serialize(theme: theme))
            }
        }
        XCTAssertEqual(warm.serialize(theme: "dark"), WidgetDOM.invalidatingCaches(cold).serialize(theme: "dark"))
    }

    func testCompositorStaysWithinItsBudget() {
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 400, height: 400), styleMask: [.borderless], backing: .buffered, defer: false)
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 400)
        window.contentView?.addSubview(renderer.view)
        renderer.view.frame = NSRect(x: 0, y: 0, width: 400, height: 400)
        renderer.load()
        var ops: [[String: Any]] = [["op": "el", "id": 1, "tag": "body", "attrs": [], "adopt": "body"], ["op": "el", "id": 2, "tag": "style", "attrs": []], ["op": "css", "id": 2, "text": "@keyframes grow { to { transform: scale(3) } } .big { width: 1000px; height: 1000px; animation: grow 5s infinite; }"], ["op": "ins", "id": 2, "parent": 1]]
        for i in 0..<60 {
            ops.append(["op": "el", "id": 100 + i, "tag": "div", "attrs": [["class", "big"]]])
            ops.append(["op": "ins", "id": 100 + i, "parent": 1])
        }
        let data = try! JSONSerialization.data(withJSONObject: ["type": "ops", "ops": ops])
        renderer.send(String(data: data, encoding: .utf8)!)
        XCTAssertLessThanOrEqual(renderer.compositedCount, WidgetCompositor.maxLayers)
        renderer.destroy()
    }

    func testDepthAndNodeCaps() {
        let dom = WidgetDOM()
        dom.apply(["op": "el", "id": 1, "tag": "body", "attrs": [], "adopt": "body"])
        var parent = 1
        for id in 2...400 {
            dom.apply(["op": "el", "id": id, "tag": "div", "attrs": []])
            dom.apply(["op": "ins", "id": id, "parent": parent])
            parent = id
        }
        XCTAssertLessThan(WidgetDOM.depth(of: dom.node(400) ?? dom.body), WidgetDOM.maxDepth + 1)
        for id in 1000..<(1000 + WidgetDOM.maxNodes + 50) {
            dom.apply(["op": "text", "id": id, "data": "x"])
        }
        XCTAssertNil(dom.node(1000 + WidgetDOM.maxNodes + 10))
        _ = dom.serialize(theme: "dark")
    }

    func testNestedLightDarkIsBounded() {
        let nested = String(repeating: "light-dark(", count: 100_000) + "red" + String(repeating: ", blue)", count: 100_000)
        let start = Date()
        _ = CSSTheme.resolve(nested, theme: "dark")
        XCTAssertLessThan(Date().timeIntervalSince(start), 5)
    }

    func testCanvasExtremes() {
        let canvas = WidgetCanvas(width: 100, height: 100, allowance: { WidgetCanvas.maxPixels })
        canvas.run([["r", 1e300, 1e300], ["r", 100_000, 100_000], ["c", "putImageData", ["b64": "AAAA", "w": 1e300, "h": 1e300], 0, 0], ["s", "shadowBlur", 1e300], ["c", "setLineDash", Array(repeating: 1, count: 10_000)], ["c", "fillText", String(repeating: "x", count: 1_000_000), 0, 0]])
        XCTAssertLessThanOrEqual(canvas.pixelCount, WidgetCanvas.maxPixels)
        XCTAssertEqual(canvas.pixels(x: 0, y: 0, w: 100_000, h: 100_000).count, 0)
        for _ in 0..<2000 {
            canvas.run([["c", "save"]])
        }
        canvas.run([["r", 64, 64]])
        for _ in 0..<10_000 {
            canvas.run([["g", Int.random(in: 0...1_000_000), "linear", 0, 0, 1, 1]])
        }
    }

    func testRendererSurvivesRandomAndHostileMessages() {
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 400)
        var posted = 0
        renderer.onMessage = { json in
            posted += 1
            XCTAssertLessThan(json.utf8.count, 64 << 20)
        }
        renderer.load()
        let start = Date()
        for round in 0..<1500 {
            let message = self.message()
            guard let data = try? JSONSerialization.data(withJSONObject: message), let json = String(data: data, encoding: .utf8) else {
                continue
            }
            renderer.send(json)
            if round % 50 == 0 {
                RendererFuzzTests.assertSafe(renderer.dom.serialize(theme: "light"))
                let canvasPixels = renderer.canvases.values.reduce(0) { $0 + $1.pixelCount }
                XCTAssertLessThanOrEqual(canvasPixels, LiteHTMLWidgetRenderer.maxCanvasPixels)
            }
        }
        renderer.send("{not json")
        renderer.send("{\"type\":\"ops\",\"ops\":\"nope\"}")
        renderer.send("[1,2,3]")
        RendererFuzzTests.assertSafe(renderer.dom.serialize(theme: "dark"))
        XCTAssertGreaterThan(posted, 0)
        XCTAssertLessThan(Date().timeIntervalSince(start), 120)
        renderer.destroy()
    }
}

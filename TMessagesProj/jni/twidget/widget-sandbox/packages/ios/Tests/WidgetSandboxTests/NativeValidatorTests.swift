import XCTest
import WidgetSandboxProtocol
@testable import WidgetSandbox

final class NativeValidatorDifferentialTests: XCTestCase {
    final class Rig {
        let validator: TrustedValidator
        var diags: [String] = []

        init(native: Bool, policy: SandboxPolicy = .compat) {
            validator = try! TrustedValidator(theme: "light") { _, _ in }
            validator.onDiag = { [unowned self] k, d in self.diags.append(k + "|" + d) }
            validator.setPolicy(policy)
            if native { validator.useNativeModel(true) }
            XCTAssertEqual(validator.usesNativeModel, native)
            validator.markLoaded() // a loaded widget: text changes are rate-limited from the first batch
        }

        func json(_ batch: String) -> (NSArray, Int, [String]) {
            diags.removeAll()
            let out = validator.processOps(batch)
            return (Self.normalized(out), validator.nodeCount, validator.thread.sync { self.diags })
        }

        func binary(_ payload: BinaryOpsPayload, canvas: [Int: WidgetCanvasBatch] = [:]) -> (NSArray, Int, [String]) {
            diags.removeAll()
            let batch = validator.processBinary(payload, canvas: canvas, typed: true)
            let nodes = validator.nodeCount
            let out: NSArray
            switch batch {
            case .empty: out = []
            case .json(let json): out = Self.normalized(json)
            case .ops(let ops): out = Self.normalized(WidgetOp.json(ops))
            }
            return (out, nodes, validator.thread.sync { self.diags })
        }

        static func normalized(_ json: String) -> NSArray {
            (try? JSONSerialization.jsonObject(with: Data(json.utf8))) as? NSArray ?? []
        }
    }

    struct RNG: RandomNumberGenerator {
        var state: UInt64
        mutating func next() -> UInt64 {
            state ^= state << 13
            state ^= state >> 7
            state ^= state << 17
            return state
        }
    }

    enum Op {
        case shim(BinaryOp)
        case raw(String)
    }

    struct Gen {
        var rng: RNG
        mutating func chance(_ n: Int) -> Bool { Int.random(in: 0..<n, using: &rng) == 0 }
        mutating func one<T>(_ a: [T]) -> T { a.randomElement(using: &rng)! }

        static let htmlTags = ["div", "span", "p", "a", "img", "canvas", "table", "tr", "td", "ul", "li", "b", "input", "button", "style", "title", "script", "iframe", "video", "html", "head", "body",
                               "foo", "custom-el", "x", "math", "template", "DIV", "Span", "STYLE", "Body", "meta", "noscript", "picture", "select", "option", "progress", "details", "dialog"]
        static let svgTags = ["svg", "g", "path", "rect", "circle", "text", "linearGradient", "lineargradient", "stop", "use", "image", "a", "animate", "animateTransform", "foreignObject", "script", "style",
                              "title", "feImage", "filter", "feFlood", "mask", "clipPath", "unknownsvg", "set", "mpath", "textPath", "pattern", "marker", "desc"]
        static let badTags = ["1div", "div x", "", String(repeating: "a", count: 65), String(repeating: "b", count: 64), "a.b", "a-b", "a_b", "é", "a:b", "-a"]
        static let attrNames = ["class", "id", "title", "style", "href", "src", "xlink:href", "srcset", "onclick", "ONCLICK", "onload", "data-x", "data-tg-x", "data-tg-link", "aria-label", "fill", "stroke",
                                "filter", "mask", "clip-path", "viewBox", "viewbox", "values", "from", "to", "by", "attributeName", "attributename", "tabindex", "width", "height", "target", "rel", "foo",
                                "bar:baz", "1x", "a b", "", "_x", "x.y", "usemap", "poster", "action", "background", "CLASS", "Href", "XLINK:HREF", "stop-color", "cursor", "marker-end", "type", "value",
                                "checked", "colspan", "role", "0", "12", "__proto__", "constructor", String(repeating: "c", count: 129)]
        static let attrValues = ["", "a b", "up", "down", "#frag", "#bad frag", "#a(b)", "javascript:alert(1)", " java\tscript:x", "data:image/png;base64,AA==", "DATA:image/svg+xml,<svg/>", " \u{1}data:image/gif,x ",
                                 "data:text/html,x", "data:,x", "data:image/png", "data: IMAGE/PNG ;x,y", "data:image/png;x", "blob:x", "url(#g)", "url(http://x)", "red", "rgb(1,2,3)", "light-dark(red, blue)",
                                 "color:red; background:url(x)", "opacity:0.5", "0;1;0", "1;url(x)", "red;blue", "href", "xlink:HREF", "SRC", "Привет 🙂", "\u{0}x", "a\"b", "<b>", "x&y", "#", "##",
                                 "#\u{a0}x", "#x\u{2028}", "\n#x", "1", "none", String(repeating: "v", count: 90), "translate(1px, 2px)", "url( icon:star )", "expression(x)", "icon:airplane", "icon:airplane?weight=bold", "url(icon:airplane)", "url(\"icon:airplane\")", "url(\u{a0}icon:airplane)", "url(\ticon:airplane)", "URL(ICON:airplane)",
                                 String(repeating: "\u{2500}", count: 200), "\u{1F642}\u{1F642}\u{1F642}"]
        static let styleNames = ["transform", "opacity", "color", "left", "width", "background", "background-color", "fill", "display", "--x", "--my-var", "-webkit-transform", "behavior", "Transform", "x",
                                 "", "1x", "a b", "content", "background-image", "filter", "z-index", "transition", "π", String(repeating: "s", count: 129)]
        static let styleValues = ["", "1", "0.5", "red", "translate(1px,2px)", "translate(1px, 2px) rotate(3deg)", "url(x)", "url(#a)", "light-dark(red,blue)", "red !important", "rgb(1, 2, 3)",
                                  "calc(100% - 2px)", "expression(alert(1))", "10px", "Привет", "a;b", "var(--x)", "none", "-1px", "1e3px", "#fff", "blur(2px)", "inherit",
                                  "\"" + String(repeating: "\u{2500}", count: 200) + "\"", "attr(data-x)", "attr(class)", "attr(title)", "\"\\2500\\2500\"", "'a' attr(aria-label) 'b'"]
        static let texts = ["", "hi", "Привет 🙂", "a < b & c", "div{color:red}", String(repeating: "\u{2500}", count: 200),
                            "p::after{content:\"" + String(repeating: "\u{2500}", count: 150) + "\"} q::before{content:attr(id) attr(data-q)}", "@import url(x); body{background:url(http://x)}", "x\u{0}y", String(repeating: "t", count: 300), "\u{2028}", "@media (prefers-color-scheme: dark){p{color:red}}"]

        mutating func id() -> Int { Int.random(in: 2...60, using: &rng) }
        mutating func anyId() -> String {
            if chance(6) { return one(["0", "1", "-1", "2147483647", "2147483648", "2.5", "\"5\"", "null", "true", "1e3", "-0", "3.0", "[]", "{}"]) }
            return String(id())
        }
        mutating func str(_ s: String) -> String {
            var out: [UInt8] = []
            JSONText.writeString(s, to: &out)
            return String(decoding: out, as: UTF8.self)
        }
        mutating func bs(_ s: String) -> BinaryString { BinaryString(s: s, units: s.utf16.count) }

        mutating func tag(svg: Bool) -> String {
            if chance(10) { return one(Self.badTags) }
            return svg ? one(Self.svgTags) : one(Self.htmlTags)
        }

        mutating func attributes() -> [(String, String)] {
            (0..<Int.random(in: 0...4, using: &rng)).map { _ in (one(Self.attrNames), one(Self.attrValues)) }
        }

        mutating func op() -> Op {
            switch Int.random(in: 0..<100, using: &rng) {
            case 0..<20:
                let svg = chance(3)
                let t = tag(svg: svg)
                var attrs = attributes()
                if chance(12), let first = attrs.first { attrs.append((first.0, "dup")) }
                if chance(8) {
                    let a = attrs.map { "\(str($0.0)):\(chance(6) ? one(["5", "null", "true", "[]"]) : str($0.1))" }.joined(separator: ",")
                    let ns = svg ? ",\"ns\":\"svg\"" : (chance(5) ? ",\"ns\":" + one(["\"html\"", "5", "null"]) : "")
                    let attrsPart = chance(5) ? ",\"attrs\":" + one(["null", "[]", "\"x\"", "{}"]) : ",\"attrs\":{\(a)}"
                    return .raw("{\"op\":\"el\",\"id\":\(anyId()),\"tag\":\(chance(10) ? "5" : str(t))\(ns)\(attrsPart)}")
                }
                var seen = Set<String>()
                attrs = attrs.filter { seen.insert($0.0).inserted }
                return .shim(.element(id: id(), tag: bs(t), svg: svg, attributes: attrs.map { (bs($0.0), bs($0.1)) }))
            case 20..<28:
                if chance(8) { return .raw("{\"op\":\"text\",\"id\":\(anyId()),\"data\":\(one(["5", "null", str("x")]))}") }
                return .shim(.text(id: id(), data: bs(one(Self.texts))))
            case 28..<31:
                return .shim(.comment(id: id(), data: bs(one(Self.texts))))
            case 31..<46:
                if chance(8) { return .raw("{\"op\":\"attr\",\"id\":\(anyId()),\"name\":\(chance(3) ? "5" : str(one(Self.attrNames))),\"value\":\(chance(3) ? "null" : str(one(Self.attrValues)))}") }
                return .shim(.attribute(id: id(), name: bs(one(Self.attrNames)), value: bs(one(Self.attrValues))))
            case 46..<51:
                return .shim(.removeAttribute(id: id(), name: bs(one(Self.attrNames))))
            case 51..<59:
                if chance(8) { return .raw("{\"op\":\"data\",\"id\":\(anyId()),\"data\":\(one(["5", str("y")]))}") }
                return .shim(.data(id: id(), data: bs(one(Self.texts))))
            case 59..<77:
                if chance(8) {
                    let before = one(["null", "0", "\"x\"", String(id()), "1"])
                    return .raw(chance(4) ? "{\"op\":\"ins\",\"parent\":\(anyId()),\"id\":\(anyId())}" : "{\"op\":\"ins\",\"parent\":\(anyId()),\"id\":\(anyId()),\"before\":\(before)}")
                }
                let parent = chance(6) ? one([1, 0, -3]) : id()
                return .shim(.insert(parent: parent, id: id(), before: chance(2) ? 0 : id()))
            case 77..<83:
                if chance(8) { return .raw("{\"op\":\"rm\",\"id\":\(anyId())}") }
                return .shim(.remove(id: id()))
            case 83..<93:
                if chance(8) { return .raw("{\"op\":\"style\",\"id\":\(anyId()),\"name\":\(str(one(Self.styleNames))),\"value\":\(str(one(Self.styleValues))),\"important\":\(one(["false", "\"true\"", "1", "true"]))}") }
                return .shim(.style(id: id(), name: bs(one(Self.styleNames)), value: bs(one(Self.styleValues)), important: chance(4)))
            case 93..<95:
                let name = one(["value", "checked", "scrollTop", "selectedIndex", "selected", "indeterminate", "foo", "__proto__", "hasOwnProperty", "toString"])
                let value = one(["\"v\"", "true", "false", "5", "1.5", "null", "[]", "{}", "\"\"", "1e999", "-1e999"])
                return .raw(chance(5) ? "{\"op\":\"prop\",\"id\":\(anyId()),\"name\":\(str(name))}" : "{\"op\":\"prop\",\"id\":\(anyId()),\"name\":\(str(name)),\"value\":\(value)}")
            case 95..<97:
                let method = one(["focus", "scrollTo", "setPointerCapture", "blur", "foo", "__proto__", "toString", "observeResize", "scrollBy"])
                let args = one(["[]", "[1,2]", "[1]", "[\"a\",1]", "null", "[1,2,3]", "[1e300,-0]"])
                return .raw(chance(4) ? "{\"op\":\"call\",\"id\":\(anyId()),\"method\":\(str(method))}" : "{\"op\":\"call\",\"id\":\(anyId()),\"method\":\(str(method)),\"args\":\(args)}")
            case 97..<99:
                let cmds = one(["[[\"c\",\"fillRect\",0,0,10,10]]", "[[\"s\",\"filter\",\"blur(2px)\"]]", "[[\"s\",\"filter\",\"url(#x)\"]]", "[[\"s\",\"fillStyle\",\"url(x)\"]]",
                                "[[\"c\",\"drawImage\",{\"n\":\(id())},0,0]]", "null", "[]", "[[\"x\"],[\"c\",\"save\"]]", "[[\"r\",10,20]]"])
                return .raw("{\"op\":\"canvas\",\"id\":\(anyId()),\"cmds\":\(cmds)}")
            default:
                return .raw(one(["5", "\"x\"", "[]", "null", "{\"op\":\"zzz\",\"id\":3}", "{\"op\":5}", "{}", "{\"id\":3}", "{\"op\":\"el\",\"op\":\"rm\",\"id\":4}", "{\"0\":1,\"op\":\"x\",\"b\":2}"]))
            }
        }
    }

    static func encode(_ ops: [Op], into encoder: inout BinaryOpsEncoder, reset: Bool) -> BinaryOpsPayload {
        encoder.begin(reset: reset)
        for op in ops {
            switch op {
            case .raw(let text):
                encoder.json(text)
            case .shim(let b):
                switch b {
                case .element(let id, let tag, let svg, let attributes):
                    encoder.element(Int32(id), tag: tag.s, svg: svg, attributes: attributes.map { ($0.name.s, $0.value.s) })
                case .text(let id, let data): encoder.text(Int32(id), data.s, code: .text)
                case .comment(let id, let data): encoder.text(Int32(id), data.s, code: .comment)
                case .data(let id, let data): encoder.text(Int32(id), data.s, code: .data)
                case .attribute(let id, let name, let value): encoder.attribute(Int32(id), name.s, value.s, internValue: name.s == "class")
                case .removeAttribute(let id, let name): encoder.removeAttribute(Int32(id), name.s)
                case .insert(let parent, let id, let before): encoder.insert(parent: Int32(parent), Int32(id), before: Int32(before))
                case .remove(let id): encoder.remove(Int32(id))
                case .style(let id, let name, let value, let important): encoder.style(Int32(id), name.s, value.s, important: important)
                case .canvas(let id, let key): encoder.canvas(Int32(id), key: Int32(key))
                case .json(let value): encoder.json(value.json)
                }
            }
        }
        return encoder.finish()
    }

    static func json(_ ops: [Op]) -> String {
        "[" + ops.map { op -> String in
            switch op {
            case .raw(let text): return text
            case .shim(let b): return String(decoding: BinaryOps.json(b, canvas: [:]), as: UTF8.self)
            }
        }.joined(separator: ",") + "]"
    }

    func testDifferentialFuzz() {
        differential(policy: .compat, seeds: 1...60)
    }

    /// The same fuzz under the default policy: text budgets over attributes and CSS strings, `attr()` limits.
    func testDifferentialFuzzUnderStrict() {
        differential(policy: .strict, seeds: 101...160)
    }

    private func differential(policy: SandboxPolicy, seeds: ClosedRange<Int>) {
        var batches = 0, outputs = 0, diags = 0
        for seed in seeds {
            let js = Rig(native: false, policy: policy), viaJSON = Rig(native: true, policy: policy), viaBinary = Rig(native: true, policy: policy)
            var gen = Gen(rng: RNG(state: UInt64(seed) &* 0x9E37_79B9_7F4A_7C15 | 1))
            var encoder = BinaryOpsEncoder()
            let setup = "[{\"op\":\"el\",\"id\":2,\"tag\":\"html\",\"attrs\":{}},{\"op\":\"el\",\"id\":3,\"tag\":\"body\"},{\"op\":\"ins\",\"parent\":1,\"id\":2,\"before\":null},{\"op\":\"ins\",\"parent\":2,\"id\":3,\"before\":null}]"
            for rig in [js, viaJSON, viaBinary] { _ = rig.json(setup) }
            for round in 0..<70 {
                let ops = (0..<Int.random(in: 1...14, using: &gen.rng)).map { _ in gen.op() }
                let text = Self.json(ops)
                let expected = js.json(text)
                let a = viaJSON.json(text)
                let b = viaBinary.binary(Self.encode(ops, into: &encoder, reset: round % 25 == 0))
                XCTAssertEqual(a.0, expected.0, "json seed \(seed) round \(round) \(text.prefix(600))")
                XCTAssertEqual(a.1, expected.1, "json nodes seed \(seed) round \(round)")
                XCTAssertEqual(a.2, expected.2, "json diags seed \(seed) round \(round) \(text.prefix(600))")
                XCTAssertEqual(b.0, expected.0, "binary seed \(seed) round \(round) \(text.prefix(600))")
                XCTAssertEqual(b.1, expected.1, "binary nodes seed \(seed) round \(round)")
                XCTAssertEqual(b.2, expected.2, "binary diags seed \(seed) round \(round) \(text.prefix(600))")
                batches += 1
                outputs += expected.0.count
                diags += expected.2.count
                if round % 23 == 22 {
                    let theme = round % 2 == 0 ? "dark" : "light"
                    let truth = Rig.normalized(js.validator.setTheme(theme))
                    XCTAssertEqual(Rig.normalized(viaJSON.validator.setTheme(theme)), truth, "theme seed \(seed)")
                    XCTAssertEqual(Rig.normalized(viaBinary.validator.setTheme(theme)), truth, "theme seed \(seed)")
                }
                if round % 5 == 4 {
                    for _ in 0..<6 {
                        let q = gen.one(["{\"q\":\"rect\",\"id\":\(gen.anyId())}", "{\"q\":\"cs\",\"id\":\(gen.id()),\"pseudo\":\"::before\"}", "{\"q\":\"imageData\",\"id\":\(gen.id()),\"x\":0,\"y\":0,\"w\":2,\"h\":2}",
                                         "{\"q\":\"canvasCall\",\"id\":\(gen.id()),\"method\":\"isPointInPath\",\"args\":[{\"n\":\(gen.id())},1]}", "{\"q\":\"anims\",\"id\":\(gen.id())}", "{\"q\":\"svg\",\"id\":\(gen.id()),\"method\":\"getBBox\"}"])
                        let truth = js.validator.validateQuery(q)
                        XCTAssertEqual(viaJSON.validator.validateQuery(q), truth, q)
                        XCTAssertEqual(viaBinary.validator.validateQuery(q), truth, q)
                        let anim = "[{\"aid\":1,\"target\":\(gen.id()),\"css\":{\"kind\":\"animation\",\"name\":\"spin\",\"origin\":0},\"startTime\":0,\"holdTime\":null,\"playbackRate\":1,\"cancelled\":false}]"
                        XCTAssertEqual(viaBinary.validator.validateAnim(anim), js.validator.validateAnim(anim), anim)
                    }
                }
            }
        }
        print("native differential (\(policy.rawValue)): \(batches) batches, \(outputs) ops out, \(diags) diags")
        XCTAssertGreaterThan(outputs, 5000)
        XCTAssertGreaterThan(diags, 2000)
    }

    func testIconExpansionOfFieldsMatchesTheJSONText() {
        let icons = IconExpansion(resolver: IconExpansionTests.stubResolver, imageTypes: IconExpansionTests.profileImages) { _, _ in }
        XCTAssertNotNil(icons.standIn("icon:airplane"))
        let values = ["icon:airplane", "icon:airplane?weight=bold", "icon:nope", "x icon:airplane", "url(icon:airplane)", "url('icon:airplane')", "url(\"icon:airplane\")", "url(\u{a0}icon:airplane)",
                      "url(\u{2003}icon:airplane)", "url(\ticon:airplane)", "url(\nicon:airplane)", "URL(ICON:airplane)", "URL(icon:airplane)", "url(Icon:airplane)", "ICON:airplane", "url( icon:airplane ) url(icon:airplane)", "url(\\\"icon:airplane)", "icon:airplane\n",
                      "a:url(icon:airplane);b:url(icon:airplane)", "icon:" + String(repeating: "a", count: 97), "url(icon:airplane?x=" + String(repeating: "1", count: 401) + ")"]
        let keys = ["src", "href", "data-x", "1x", "fill", "style", "title"]
        var replaced = 0
        for value in values {
            var encoder = BinaryOpsEncoder()
            encoder.begin()
            var json: [String] = []
            for key in keys {
                encoder.attribute(5, key, value)
                json.append(String(decoding: BinaryOps.json(.attribute(id: 5, name: BinaryString(s: key, units: key.utf16.count), value: BinaryString(s: value, units: value.utf16.count)), canvas: [:]), as: UTF8.self))
            }
            encoder.text(6, value, code: .data)
            json.append(String(decoding: BinaryOps.json(.data(id: 6, data: BinaryString(s: value, units: value.utf16.count)), canvas: [:]), as: UTF8.self))
            let attrs = keys.map { ($0, value) }
            encoder.element(7, tag: "img", svg: false, attributes: attrs)
            json.append(String(decoding: BinaryOps.json(.element(id: 7, tag: BinaryString(s: "img", units: 3), svg: false, attributes: attrs.map { (BinaryString(s: $0.0, units: $0.0.utf16.count), BinaryString(s: $0.1, units: $0.1.utf16.count)) }), canvas: [:]), as: UTF8.self))
            icons.beginBatch()
            let expected = icons.expand("[" + json.joined(separator: ",") + "]")
            icons.beginBatch()
            guard case .success(let decoded) = BinaryOps.decode(encoder.finish(), table: BinaryInternTable(), icons: icons) else { return XCTFail(value) }
            XCTAssertEqual(BinaryOps.json(decoded.ops, canvas: [:]), expected, value)
            if expected.contains(icons.standInPrefix) { replaced += 1 }
        }
        XCTAssertGreaterThan(replaced, 6)
    }

    func testTablesComeFromTheBundle() throws {
        let rig = Rig(native: true)
        let tables = try XCTUnwrap(NativeValidatorTables(json: rig.validator.tablesJSON()))
        XCTAssertTrue(tables.html.contains("div"))
        XCTAssertEqual(tables.svg["lineargradient"], "linearGradient")
        XCTAssertTrue(tables.dropped.contains("script"))
        XCTAssertEqual(tables.methods["scrollTo"], 2)
        XCTAssertEqual(tables.maxNodes, 50_000)
        XCTAssertTrue(tables.placeholder.hasPrefix("data:image/svg+xml,"))
    }
}

final class BinaryOpsHostileTests: XCTestCase {
    typealias Rig = NativeValidatorDifferentialTests.Rig

    static func payload(_ words: [Int32], _ strings: String = "") -> BinaryOpsPayload {
        BinaryOpsPayload(words: words, strings: Array(strings.utf8))
    }

    func decode(_ p: BinaryOpsPayload, table: BinaryInternTable = BinaryInternTable()) -> Result<BinaryOps.Decoded, BinaryOps.Failure> {
        BinaryOps.decode(p, table: table)
    }

    /// A canvas payload key counts once per batch: a repeat would make the native model
    /// validate (and the splice serialise) the whole payload again for every reference.
    func testRepeatedCanvasKeyRejectsTheBatch() {
        let m = BinaryOps.magic
        guard case .failure(let failure) = decode(Self.payload([m, 2, 10, 5, 0, 10, 6, 0])) else { return XCTFail("a repeated key decoded") }
        XCTAssertEqual(failure, .canvasKey)
        XCTAssertNotNil(try? decode(Self.payload([m, 2, 10, 5, 0, 10, 6, 1])).get())
    }

    func testMalformedBuffersAreRejectedWhole() {
        let m = BinaryOps.magic
        let bad: [(String, BinaryOpsPayload)] = [
            ("empty", Self.payload([])),
            ("magic", Self.payload([0x1234, 0])),
            ("negative count", Self.payload([m, -1])),
            ("count", Self.payload([m, 2, 8, 5])),
            ("truncated", Self.payload([m, 1, 2, 5])),
            ("opcode", Self.payload([m, 1, 99, 5])),
            ("negative opcode", Self.payload([m, 1, -7, 5])),
            ("string overflow", Self.payload([m, 1, 2, 5, 10], "abc")),
            ("string leftover", Self.payload([m, 1, 2, 5, 1], "abc")),
            ("negative length", Self.payload([m, 1, 11, -5], "abc")),
            ("intern ref", Self.payload([m, 1, 6, 5, -1])),
            ("intern ref max", Self.payload([m, 1, 6, 5, Int32.min])),
            ("define order", Self.payload([m, 1, 0, 1, 1, 6, 5, -2], "a")),
            ("define long", Self.payload([m, 0, 0, 0, 257], String(repeating: "a", count: 257))),
            ("reset late", Self.payload([m, 1, 8, 5, 12])),
            ("el flags", Self.payload([m, 1, 1, 5, 1, 2, 0], "a")),
            ("el count", Self.payload([m, 1, 1, 5, 1, 0, 1000000], "a")),
            ("el negative count", Self.payload([m, 1, 1, 5, 1, 0, -1], "a")),
            ("style important", Self.payload([m, 1, 9, 5, 1, 1, 2], "ab")),
            ("utf8 overlong", BinaryOpsPayload(words: [m, 1, 2, 5, 1], strings: [0xc0, 0x80])),
            ("utf8 surrogate", BinaryOpsPayload(words: [m, 1, 2, 5, 1], strings: [0xed, 0xa0, 0x80])),
            ("utf8 truncated", BinaryOpsPayload(words: [m, 1, 2, 5, 1], strings: [0xe2, 0x82])),
            ("utf8 continuation", BinaryOpsPayload(words: [m, 1, 2, 5, 1], strings: [0x80])),
            ("utf8 high", BinaryOpsPayload(words: [m, 1, 2, 5, 1], strings: [0xf5, 0x80, 0x80, 0x80])),
            ("utf8 split pair", BinaryOpsPayload(words: [m, 2, 2, 5, 1, 2, 6, 1], strings: [0xf0, 0x9f, 0x99, 0x82])),
            ("utf8 beyond max", BinaryOpsPayload(words: [m, 1, 2, 5, 2], strings: [0xf4, 0x90, 0x80, 0x80])),
        ]
        for (name, p) in bad {
            if case .success(let d) = decode(p) { XCTFail("\(name) decoded to \(d.ops.count) ops") }
        }
        let table = BinaryInternTable()
        XCTAssertNotNil(try? decode(Self.payload([m, 1, 0, 0, 1, 6, 5, -1], "a"), table: table).get())
        XCTAssertEqual(table.strings.map { $0.s }, ["a"])
        XCTAssertNil(try? decode(Self.payload([m, 1, 0, 0, 1, 6, 5, -1], "a"), table: table).get(), "a second define of entry 0 must not pass")
        XCTAssertNil(try? decode(Self.payload([m, 1, 0, 1, 1, 6, 5, -3], "b"), table: table).get())
        XCTAssertEqual(table.strings.count, 1, "a rejected batch leaves the table as it was")
        XCTAssertNotNil(try? decode(Self.payload([m, 1, 12, 0, 0, 1, 6, 5, -1], "c"), table: table).get())
        XCTAssertEqual(table.strings.map { $0.s }, ["c"])
    }

    func testInterningIsCapped() {
        let m = BinaryOps.magic
        var words: [Int32] = [m, 0]
        var text = ""
        for k in 0...BinaryInternTable.maxEntries {
            words += [0, Int32(k), 1]
            text += "x"
        }
        XCTAssertNil(try? decode(Self.payload(words, text)).get(), "more entries than the cap")
        words = [m, 0]
        text = ""
        let big = String(repeating: "y", count: 256)
        for k in 0..<4100 where (k + 1) * 256 <= BinaryInternTable.maxBytes + 256 {
            words += [0, Int32(k), 256]
            text += big
        }
        XCTAssertNil(try? decode(Self.payload(words, text)).get(), "more bytes than the cap")
    }

    func testTooManyOpsIsOneDiagnostic() {
        let rig = Rig(native: true)
        var words: [Int32] = [BinaryOps.magic, Int32(BinaryOps.maxOps + 1)]
        for _ in 0...BinaryOps.maxOps { words += [8, 5] }
        let (out, _, diags) = rig.binary(Self.payload(words))
        XCTAssertEqual(out.count, 0)
        XCTAssertEqual(diags, ["protocol-violation|ops batch is not an array or too large"])
    }

    func testRandomBuffersNeverCrashAndNeverPassUnchecked() {
        var rng = NativeValidatorDifferentialTests.RNG(state: 0xDEAD_BEEF)
        let truth = Rig(native: false), native = Rig(native: true)
        let setup = "[{\"op\":\"el\",\"id\":2,\"tag\":\"html\"},{\"op\":\"el\",\"id\":3,\"tag\":\"body\"},{\"op\":\"ins\",\"parent\":1,\"id\":2,\"before\":null},{\"op\":\"ins\",\"parent\":2,\"id\":3,\"before\":null},{\"op\":\"el\",\"id\":5,\"tag\":\"div\"},{\"op\":\"ins\",\"parent\":3,\"id\":5,\"before\":null}]"
        _ = truth.json(setup)
        _ = native.json(setup)
        var gen = NativeValidatorDifferentialTests.Gen(rng: NativeValidatorDifferentialTests.RNG(state: 77))
        var encoder = BinaryOpsEncoder()
        var decoded = 0, rejected = 0
        for round in 0..<3000 {
            let ops = (0..<Int.random(in: 1...6, using: &gen.rng)).map { _ in gen.op() }
            var p = NativeValidatorDifferentialTests.encode(ops, into: &encoder, reset: true)
            for _ in 0..<Int.random(in: 1...4, using: &rng) {
                switch Int.random(in: 0..<6, using: &rng) {
                case 0 where p.words.count > 2: p.words[Int.random(in: 2..<p.words.count, using: &rng)] = Int32.random(in: -3...70, using: &rng)
                case 1 where p.words.count > 2: p.words[Int.random(in: 2..<p.words.count, using: &rng)] &+= Int32.random(in: -2...2, using: &rng)
                case 2 where !p.strings.isEmpty: p.strings[Int.random(in: 0..<p.strings.count, using: &rng)] = UInt8.random(in: 0...255, using: &rng)
                case 3 where p.words.count > 3: p.words.removeLast(Int.random(in: 1...min(3, p.words.count - 2), using: &rng))
                case 4: p.strings += [UInt8.random(in: 0x80...0xff, using: &rng)]
                default: p.words.append(Int32.random(in: 0...12, using: &rng))
                }
            }
            let table = BinaryInternTable()
            switch BinaryOps.decode(p, table: table) {
            case .failure:
                rejected += 1
                let (out, _, diags) = native.binary(p)
                XCTAssertEqual(out.count, 0)
                XCTAssertEqual(diags.count, 1)
                XCTAssertTrue(diags.first?.hasPrefix("protocol-violation|malformed binary ops batch") ?? false, "\(diags)")
            case .success(let d):
                decoded += 1
                let json = BinaryOps.json(d.ops, canvas: [:])
                let expected = truth.json(json)
                let got = native.binary(p)
                XCTAssertEqual(got.0, expected.0, "round \(round) \(json.prefix(400))")
                XCTAssertEqual(got.1, expected.1)
                XCTAssertEqual(got.2, expected.2, "round \(round) \(json.prefix(400))")
            }
        }
        print("hostile buffers: \(decoded) decoded and matched the JS validator, \(rejected) rejected whole")
        XCTAssertGreaterThan(decoded, 100)
        XCTAssertGreaterThan(rejected, 300)
    }

    func testScrollIntoViewArgumentsMatchTheScriptValidator() {
        let calls = ["", ",\"args\":[]", ",\"args\":null", ",\"args\":[1,2,1,0]", ",\"args\":[3,3,2,1]", ",\"args\":[4,0,0,0]", ",\"args\":[0,0,3,0]", ",\"args\":[0,0,0,2]", ",\"args\":[-1,0,0,0]", ",\"args\":[0.5,0,0,0]", ",\"args\":[0,0,0]"]
        for call in calls {
            var results: [NSArray] = []
            for native in [false, true] {
                let rig = Rig(native: native)
                _ = rig.json("[{\"op\":\"el\",\"id\":2,\"tag\":\"div\"},{\"op\":\"ins\",\"parent\":1,\"id\":2,\"before\":null}]")
                results.append(rig.json("[{\"op\":\"call\",\"id\":2,\"method\":\"scrollIntoView\"\(call)}]").0)
            }
            XCTAssertEqual(results[0], results[1], call)
        }
    }

    /// The client's time over the widget (docs/sdk-v1.md §2.17): the native model hands CSS with `env()` to the script
    /// validator, so a style op, a `style` attribute and a stylesheet come out rewritten to the root variables exactly as
    /// the script validator writes them; another `env()` name takes its fallback or is dropped.
    func testEnvSafeAreaIsRewrittenAsTheScriptValidatorDoes() {
        let setup = "[{\"op\":\"el\",\"id\":2,\"tag\":\"div\"},{\"op\":\"ins\",\"parent\":1,\"id\":2,\"before\":null},{\"op\":\"el\",\"id\":3,\"tag\":\"style\"},{\"op\":\"ins\",\"parent\":1,\"id\":3,\"before\":null}]"
        let batch = "[{\"op\":\"style\",\"id\":2,\"name\":\"padding-bottom\",\"value\":\"env(safe-area-inset-bottom, 8px)\",\"important\":false},"
            + "{\"op\":\"style\",\"id\":2,\"name\":\"margin-bottom\",\"value\":\"env(keyboard-inset-height)\",\"important\":false},"
            + "{\"op\":\"attr\",\"id\":2,\"name\":\"style\",\"value\":\"padding:0 max(12px,env(safe-area-inset-right)) calc(8px + env(safe-area-inset-bottom))\"},"
            + "{\"op\":\"text\",\"id\":4,\"data\":\"a{width:env(keyboard-inset-width,13px)}b{bottom:env(safe-area-inset-bottom)}\"},{\"op\":\"ins\",\"parent\":3,\"id\":4,\"before\":null}]"
        var results: [NSArray] = []
        for native in [false, true] {
            let rig = Rig(native: native)
            _ = rig.json(setup)
            results.append(rig.json(batch).0)
        }
        XCTAssertEqual(results[0], results[1])
        let text = String(decoding: (try? JSONSerialization.data(withJSONObject: results[1], options: [.withoutEscapingSlashes])) ?? Data(), as: UTF8.self)
        XCTAssertTrue(text.contains("var(--tg-content-safe-area-inset-bottom,8px)"), text)
        XCTAssertTrue(text.contains("padding:0 max(12px,var(--tg-content-safe-area-inset-right)) calc(8px + var(--tg-content-safe-area-inset-bottom))"), text)
        // The style element's text reaches the renderer only as the sanitized `css` op (its raw text node is never inserted).
        XCTAssertTrue(text.contains("{\"op\":\"css\",\"id\":3,\"text\":\"a{width:13px}b{bottom:var(--tg-content-safe-area-inset-bottom)}\"}"), text)
    }

    func testJSONOpsStillGoThroughTheNativeModel() {
        let rig = Rig(native: true)
        _ = rig.json("[{\"op\":\"el\",\"id\":2,\"tag\":\"div\"},{\"op\":\"ins\",\"parent\":1,\"id\":2,\"before\":null}]")
        let (out, nodes, diags) = rig.json("[{\"op\":\"attr\",\"id\":2,\"name\":\"onclick\",\"value\":\"x\"},{\"op\":\"attr\",\"id\":2,\"name\":\"href\",\"value\":\"javascript:x\"},{\"op\":\"el\",\"id\":3,\"tag\":\"script\"}]")
        XCTAssertEqual(nodes, 3)
        XCTAssertEqual(out, [["op": "rattr", "id": 2, "name": "href"]])
        XCTAssertEqual(diags, ["url-blocked|div[href]=javascript:x"])
        let bad = rig.json("not json")
        XCTAssertEqual(bad.2, ["protocol-violation|ops batch is not valid JSON"])
        XCTAssertEqual(rig.validator.nodeCount, 3)
    }

}

import XCTest
import QuartzCore
import WidgetSandboxProtocol
@testable import WidgetSandbox

/// The strict caps of docs/decisions.md, "Strict limits from the start" (tests/unit/strict-limits.test.ts on the web):
/// past a counted cap the widget stops (the validator's `stopped`, the host's `dead`), compat keeps the caps hosts had
/// before; the shim refuses at the same numbers (canvas, timers) so the author gets an error in the widget; the
/// main-thread window (layout queries + the modelled cost of the ops) defers frames apart from the JS quota. The native
/// node model (`binaryOps`) and the canvas-word shortcut stop exactly where the script validator does.
final class StrictLimitsTests: XCTestCase {
    typealias Rig = NativeValidatorDifferentialTests.Rig

    private static let skeleton = "[{\"op\":\"el\",\"id\":2,\"tag\":\"html\"},{\"op\":\"ins\",\"parent\":1,\"id\":2,\"before\":null},{\"op\":\"el\",\"id\":3,\"tag\":\"body\"},{\"op\":\"ins\",\"parent\":2,\"id\":3,\"before\":null}]"

    private func rig(native: Bool, policy: SandboxPolicy = .strict) -> Rig {
        let r = Rig(native: native, policy: policy)
        _ = r.json(Self.skeleton)
        return r
    }

    private func json(_ ops: [[String: Any]]) -> String {
        String(decoding: try! JSONSerialization.data(withJSONObject: ops), as: UTF8.self)
    }

    /// A `<style>` element `id` with `css` as its text (text node `id + 1`).
    private func style(_ id: Int, _ css: String) -> [[String: Any]] {
        [["op": "el", "id": id, "tag": "style"], ["op": "ins", "parent": 3, "id": id, "before": NSNull()],
         ["op": "text", "id": id + 1, "data": css], ["op": "ins", "parent": id, "id": id + 1, "before": NSNull()]]
    }

    private func rules(_ n: Int, from: Int = 0) -> String { (0..<n).map { ".r\(from + $0){color:red}" }.joined() }
    private func keyframes(_ n: Int) -> String { (0..<n).map { "@keyframes k\($0){from{opacity:0}50%{opacity:.5}to{opacity:1}}" }.joined() }

    private static let canvas: [[String: Any]] = [["op": "el", "id": 9, "tag": "canvas"], ["op": "ins", "parent": 3, "id": 9, "before": NSNull()]]

    /// `n` fillRect commands as one canvas word payload.
    private func fillRects(_ n: Int) -> WidgetCanvasBatch {
        var words: [Double] = []
        for i in 0..<n { words += [0, 0, 0, 4, 0, Double(i), 0, 0, 0, 1, 0, 1] }
        return WidgetCanvasBatch(words: words, strings: ["fillRect"])
    }

    // MARK: defaults

    func testDefaults() {
        let config = SandboxConfig(innerWidth: 300, innerHeight: 200, devicePixelRatio: 2, theme: "light")
        XCTAssertEqual(config.memoryLimitBytes, 8 * 1024 * 1024)
        XCTAssertEqual(config.maxTimers, 64)
        XCTAssertEqual(config.mainThreadQuotaMs, 100)
        XCTAssertEqual(config.cpuQuotaMs, 300)
        let options = WidgetHost.Options()
        XCTAssertEqual(options.memoryLimitBytes, 8 * 1024 * 1024)
        XCTAssertEqual(options.maxTimers, 64)
        XCTAssertEqual(options.mainThreadQuotaMs, 100)
        XCTAssertEqual(CpuBudget.queryLimit, 128)
        XCTAssertEqual(CpuBudget.queryBudgetMs, 1000)
        XCTAssertEqual(CpuBudget.opsCostMs(ops: 4000, canvasCommands: 0), 100, accuracy: 1e-9)
        XCTAssertEqual(CpuBudget.opsCostMs(ops: 1, canvasCommands: 2000), 4.025, accuracy: 1e-9)
    }

    func testTablesCarryThePolicyCaps() throws {
        let strict = try XCTUnwrap(NativeValidatorTables(json: Rig(native: true, policy: .strict).validator.tablesJSON()))
        XCTAssertEqual([strict.maxText, strict.maxAttr, strict.maxOps, strict.maxCanvasCommands], [32768, 8192, 10_000, 4000])
        XCTAssertEqual([strict.textBudget, strict.maxCssRules, strict.maxKeyframes, strict.maxLiveAnims], [49152, 400, 32, 64])
        XCTAssertEqual(strict.maxCanvasPixels, 2048 * 2048)
        XCTAssertTrue(strict.stop)
        let compat = try XCTUnwrap(NativeValidatorTables(json: Rig(native: true, policy: .compat).validator.tablesJSON()))
        XCTAssertEqual([compat.maxText, compat.maxAttr, compat.maxOps, compat.maxCanvasCommands], [2 << 20, 4 << 20, 200_000, 500_000])
        XCTAssertEqual([compat.textBudget, compat.maxCssRules, compat.maxKeyframes, compat.maxLiveAnims], [0, 0, 0, 1024])
        XCTAssertEqual(compat.maxCanvasPixels, 0)
        XCTAssertFalse(compat.stop)
    }

    // MARK: CSS rules and @keyframes

    func testCountCssRulesMatchesTheScript() {
        XCTAssertTrue(NativeValidator.countCssRules("a{color:red}@media (min-width:1px){b{color:red}c{color:red}}") == (3, 0))
        XCTAssertTrue(NativeValidator.countCssRules("@keyframes x{from{opacity:0}to{opacity:1}}@-webkit-keyframes y{0%{top:0}}") == (0, 2))
        XCTAssertTrue(NativeValidator.countCssRules("a::before{content:\"}{\"}/* x{y} */b{color:red}") == (2, 0))
        XCTAssertTrue(NativeValidator.countCssRules("@supports (display:grid){@container (min-width:1px){a{color:red}}}") == (1, 0))
        XCTAssertTrue(NativeValidator.countCssRules("@KEYFRAMES x{to{top:0}}@keyframesx{a{b:c}}\u{a0}@keyframes z{to{top:0}}/* open") == (1, 2))
        XCTAssertTrue(NativeValidator.countCssRules("@\u{212A}eyframes q{to{top:0}}") == (0, 1), "lowercased as String.prototype.toLowerCase does (KELVIN SIGN)")
        // an escaped brace in a selector or a @keyframes name is no brace (the tweb audit of v0.4.0)
        XCTAssertTrue(NativeValidator.countCssRules(".a\\{x0{color:red}.b\\}{color:red}") == (2, 0))
        XCTAssertTrue(NativeValidator.countCssRules("@keyframes k\\}0{to{opacity:0}}@keyframes k\\{1{to{opacity:0}}") == (0, 2))
        XCTAssertTrue(NativeValidator.countCssRules("a{content:\"\\\"\"}b{color:red}") == (2, 0), "an escape inside a string still ends with it")
    }

    /// The document's totals are compared with the caps after the whole batch (the tweb audit of v0.4.0): two sheets
    /// rewritten in one task, in either order, and a subtree replaced in one task pass through a sum of the old and the
    /// new that the document never holds.
    func testTotalsAreComparedAfterTheWholeBatch() {
        for native in [false, true] {
            for order in [[11, 21], [21, 11]] {
                let v = rig(native: native)
                _ = v.json(json(style(10, rules(200)) + style(20, rules(200, from: 200))))
                let data = [11: rules(300), 21: rules(100, from: 300)]
                XCTAssertEqual(v.json(json(order.map { ["op": "data", "id": $0, "data": data[$0]!] })).0.count, 2, "native \(native), \(order)")
                XCTAssertNil(v.validator.stopped, "400 rules after the batch (native \(native), \(order))")
                XCTAssertEqual(v.json(json([["op": "data", "id": 21, "data": rules(101, from: 300)]])).0.count, 0)
                XCTAssertEqual(v.validator.stopped, "CSS limit exceeded: 401 rules in the document's stylesheets (max 400)")
            }
            let p = { (id: Int, ch: String) -> [[String: Any]] in
                [["op": "el", "id": id, "tag": "p"], ["op": "text", "id": id + 1, "data": String(repeating: ch, count: 26_000)],
                 ["op": "ins", "parent": id, "id": id + 1, "before": NSNull()], ["op": "ins", "parent": 3, "id": id, "before": NSNull()]]
            }
            let t = rig(native: native)
            _ = t.json(json(p(10, "x")))
            XCTAssertEqual(t.json(json(p(20, "y") + [["op": "rm", "id": 10]])).0.count, 5, "native \(native)")
            XCTAssertNil(t.validator.stopped, "26 K before and after; 52 K only in the middle of the batch (native \(native))")
            XCTAssertEqual(t.json(json(p(30, "z"))).0.count, 0)
            XCTAssertEqual(t.validator.stopped, "Text limit exceeded: the document's text is over 49152 characters", "native \(native)")
        }
    }

    func testCssRuleAndKeyframeCapsStopOnBothValidatorPaths() {
        var stops: [String?] = []
        for native in [false, true] {
            let v = rig(native: native)
            let first = v.json(json(style(10, rules(250)) + style(20, rules(150, from: 250))))
            XCTAssertEqual((first.0 as! [[String: Any]]).filter { $0["op"] as? String == "css" }.count, 2, "native \(native)")
            XCTAssertNil(v.validator.stopped)
            _ = v.json(json([["op": "rm", "id": 20]]))
            _ = v.json(json(style(30, rules(150, from: 400))))
            XCTAssertNil(v.validator.stopped, "the removed sheet was refunded (native \(native))")
            let over = v.json(json(style(40, rules(1, from: 900))))
            XCTAssertEqual(over.0.count, 0)
            XCTAssertTrue(over.2.contains("limit|CSS limit exceeded: 401 rules in the document's stylesheets (max 400)"), "\(over.2)")
            stops.append(v.validator.stopped)

            let k = rig(native: native)
            _ = k.json(json(style(10, keyframes(32))))
            XCTAssertNil(k.validator.stopped)
            XCTAssertEqual(k.json(json([["op": "data", "id": 11, "data": keyframes(33)]])).0.count, 0)
            XCTAssertEqual(k.validator.stopped, "CSS limit exceeded: 33 @keyframes in the document's stylesheets (max 32)", "native \(native)")

            let compat = rig(native: native, policy: .compat)
            XCTAssertEqual((compat.json(json(style(10, rules(1000)))).0 as! [[String: Any]]).filter { $0["op"] as? String == "css" }.count, 1)
            XCTAssertNil(compat.validator.stopped)
        }
        XCTAssertEqual(stops, Array(repeating: "CSS limit exceeded: 401 rules in the document's stylesheets (max 400)", count: 2))
    }

    // MARK: ops per batch, canvas commands per send

    /// The twin of strict-limits.test.ts "nodes in the document": compared once the batch is applied, on both paths.
    func testNodeCapStopsOnEveryPath() {
        let els = { (n: Int, from: Int) in (0..<n).flatMap { [["op": "el", "id": from + $0, "tag": "div"], ["op": "ins", "parent": 3, "id": from + $0, "before": NSNull()]] as [[String: Any]] } }
        for native in [false, true] {
            let v = rig(native: native)
            XCTAssertEqual(v.json(json(els(1998, 100))).0.count, 3996, "native \(native)") // html and body are two of them
            XCTAssertNil(v.validator.stopped)
            XCTAssertEqual(v.json(json(els(1, 5000))).0.count, 0)
            XCTAssertEqual(v.validator.stopped, "Node limit exceeded: 2001 nodes in the document (max 2000)", "native \(native)")
            // A document rebuilt in one batch, the old nodes removed after the new ones are made.
            let r = rig(native: native)
            _ = r.json(json(els(1500, 100)))
            XCTAssertEqual(r.json(json(els(1500, 10_000) + (0..<1500).map { ["op": "rm", "id": 100 + $0] })).0.count, 4500, "native \(native)")
            XCTAssertNil(r.validator.stopped)
            let compat = rig(native: native, policy: .compat)
            _ = compat.json(json(els(3000, 100)))
            XCTAssertNil(compat.validator.stopped)
        }
    }

    func testOpsPerBatchCapStopsOnEveryPath() {
        // One text node and its changes: n ops without passing the node cap.
        let texts = { (n: Int, from: Int) in [["op": "text", "id": from, "data": ""] as [String: Any]] + (1..<n).map { ["op": "data", "id": from, "data": String($0 % 10)] as [String: Any] } }
        for native in [false, true] {
            let v = rig(native: native)
            XCTAssertEqual(v.json(json(texts(10_000, 100))).0.count, 10_000)
            let over = v.json(json(texts(10_001, 20_100)))
            XCTAssertEqual(over.0.count, 0)
            XCTAssertEqual(over.2, ["limit|Ops limit exceeded: 10001 operations in one batch (max 10000)"])
            XCTAssertEqual(v.validator.stopped, "Ops limit exceeded: 10001 operations in one batch (max 10000)")
            let compat = rig(native: native, policy: .compat)
            XCTAssertEqual(compat.json(json(texts(10_001, 100))).0.count, 10_001)
            XCTAssertNil(compat.validator.stopped)
        }
        for policy in [SandboxPolicy.strict, .compat] {
            let v = rig(native: true, policy: policy)
            var encoder = BinaryOpsEncoder()
            encoder.begin(reset: true)
            for i in 0..<10_001 { encoder.text(Int32(100 + i), "", code: .text) }
            let out = v.binary(encoder.finish())
            XCTAssertEqual(out.0.count, policy == .strict ? 0 : 10_001)
            XCTAssertEqual(v.validator.stopped, policy == .strict ? "Ops limit exceeded: 10001 operations in one batch (max 10000)" : nil)
        }
    }

    func testCanvasCommandsPerSendStopOnEveryPath() {
        let cmds = { (n: Int) in (0..<n).map { ["c", "fillRect", $0, 0, 1, 1] as [Any] } }
        let reason = "Canvas limit exceeded: 4001 commands in one send (max 4000)"
        for native in [false, true] {
            let v = rig(native: native)
            _ = v.json(json(Self.canvas))
            let fits = v.json(json([["op": "canvas", "id": 9, "cmds": cmds(4000)]])).0 as! [[String: Any]]
            XCTAssertEqual((fits.first?["cmds"] as? [Any])?.count, 4000)
            XCTAssertEqual(v.json(json([["op": "canvas", "id": 9, "cmds": cmds(4001)]])).0.count, 0)
            XCTAssertEqual(v.validator.stopped, reason, "native \(native)")
            let compat = rig(native: native, policy: .compat)
            _ = compat.json(json(Self.canvas))
            let all = compat.json(json([["op": "canvas", "id": 9, "cmds": cmds(10_001)]])).0 as! [[String: Any]]
            XCTAssertEqual((all.first?["cmds"] as? [Any])?.count, 10_001)
        }
        // Canvas words through the native model (binary ops).
        for (count, stops) in [(4000, false), (4001, true)] {
            let v = rig(native: true)
            var encoder = BinaryOpsEncoder()
            encoder.begin(reset: true)
            encoder.element(9, tag: "canvas", svg: false, attributes: [])
            encoder.insert(parent: 3, 9, before: 0)
            encoder.canvas(9, key: 7)
            let out = v.binary(encoder.finish(), canvas: [7: fillRects(count)])
            XCTAssertEqual(out.0.count, stops ? 0 : 3, "\(count)")
            XCTAssertEqual(v.validator.stopped, stops ? reason : nil)
        }
        // The canvas-word shortcut of the script validator's host takes 4000; 4001 go through the validator, which stops.
        for (count, stops) in [(4000, false), (4001, true)] {
            let v = rig(native: false)
            _ = v.json(json(Self.canvas))
            let result = v.validator.processOpsForRenderer("[{\"op\":\"canvas\",\"id\":9,\"bin\":7}]", canvas: [7: fillRects(count)], typed: true)
            if stops {
                guard case .empty = result else { return XCTFail("4001 commands reached the renderer") }
                XCTAssertEqual(v.validator.stopped, reason)
                XCTAssertEqual(v.validator.nativeCanvasBatches, 0)
                guard case .empty = v.validator.processOpsForRenderer("[{\"op\":\"canvas\",\"id\":9,\"bin\":7}]", canvas: [7: fillRects(1)], typed: true) else { return XCTFail("a stopped validator took the shortcut") }
            } else {
                guard case .ops(let ops) = result else { return XCTFail("expected the shortcut") }
                XCTAssertEqual(ops.count, 1)
                XCTAssertEqual(v.validator.nativeCanvasBatches, 1)
            }
        }
    }

    // MARK: text values and attributes

    func testTextAndAttributeCapsStopOnBothValidatorPaths() {
        let big = String(repeating: "b", count: 32 * 1024 + 1)
        let text = "Text limit exceeded: a text value of 32769 characters (max 32768)"
        let attribute = "Attribute limit exceeded: a value of 8193 characters (max 8192)"
        let cases: [(String, [[String: Any]], String)] = [
            ("a text node", [["op": "text", "id": 21, "data": big]], text),
            ("text data", [["op": "text", "id": 21, "data": "x"], ["op": "data", "id": 21, "data": big]], text),
            ("a canvas string", Self.canvas + [["op": "canvas", "id": 9, "cmds": [["c", "fillText", "short", 0, 0], ["c", "fillText", big, 0, 0]]]], text),
            ("Path2D data", Self.canvas + [["op": "canvas", "id": 9, "cmds": [["pn", 1, big]]]], text),
            ("an attribute on creation", [["op": "el", "id": 22, "tag": "div", "attrs": ["data-label": String(repeating: "x", count: 8193)]]], attribute),
            ("an attribute", [["op": "el", "id": 22, "tag": "div"], ["op": "attr", "id": 22, "name": "VALUE", "value": String(repeating: "x", count: 8193)]], attribute),
            ("an inline style", [["op": "el", "id": 22, "tag": "div"], ["op": "style", "id": 22, "name": "--x", "value": String(repeating: "x", count: 8193)]], attribute),
        ]
        for (what, ops, reason) in cases {
            var results: [(NSArray, Int, [String])] = []
            for native in [false, true] {
                let v = rig(native: native)
                let out = v.json(json([["op": "text", "id": 30, "data": "before"]] + ops))
                XCTAssertEqual(out.0.count, 0, "\(what): its batch is refused whole (native \(native))")
                XCTAssertEqual(v.validator.stopped, reason, "\(what) (native \(native))")
                XCTAssertTrue(out.2.contains("limit|" + reason), "\(what) (native \(native)): \(out.2)")
                XCTAssertEqual(v.json(json([["op": "text", "id": 31, "data": "after"]])).0.count, 0, "\(what): a stopped validator refuses every batch")
                results.append(out)
            }
            XCTAssertEqual(results[1].0, results[0].0, what)
            XCTAssertEqual(results[1].2, results[0].2, what)
        }
        // 32 KB and 8 KB pass.
        for native in [false, true] {
            let v = rig(native: native)
            let fits = v.json(json([["op": "text", "id": 20, "data": "x"], ["op": "ins", "parent": 3, "id": 20, "before": NSNull()], ["op": "data", "id": 20, "data": String(repeating: "a", count: 32 * 1024)],
                                    ["op": "el", "id": 22, "tag": "div", "attrs": ["data-label": String(repeating: "x", count: 8192)]]]))
            XCTAssertEqual(fits.0.count, 4)
            XCTAssertNil(v.validator.stopped)
        }
        // Compat keeps the old caps and never stops.
        for native in [false, true] {
            let v = rig(native: native, policy: .compat)
            XCTAssertEqual(v.json(json([["op": "text", "id": 21, "data": big], ["op": "el", "id": 22, "tag": "div", "attrs": ["data-label": big]]])).0.count, 2)
            XCTAssertNil(v.validator.stopped)
        }
    }

    /// A canvas string past 32 KB in a word payload never takes a shortcut to the renderer: the native model and the
    /// script validator's host both send it through the validator, which stops the widget.
    func testLongCanvasStringsInWordPayloadsStop() {
        let big = String(repeating: "b", count: 32 * 1024 + 1)
        let payloads = [
            WidgetCanvasBatch(words: [1, 0, 0, 1, 1, 1], strings: ["fillStyle", big]),
            WidgetCanvasBatch(words: [5, -1, 1, 1, 1, 0], strings: [big]),
        ]
        for batch in payloads {
            let native = rig(native: true)
            var encoder = BinaryOpsEncoder()
            encoder.begin(reset: true)
            encoder.element(9, tag: "canvas", svg: false, attributes: [])
            encoder.insert(parent: 3, 9, before: 0)
            encoder.canvas(9, key: 7)
            XCTAssertEqual(native.binary(encoder.finish(), canvas: [7: batch]).0.count, 0)
            XCTAssertEqual(native.validator.stopped, "Text limit exceeded: a text value of 32769 characters (max 32768)")

            let script = rig(native: false)
            _ = script.json(json(Self.canvas))
            guard case .empty = script.validator.processOpsForRenderer("[{\"op\":\"canvas\",\"id\":9,\"bin\":7}]", canvas: [7: batch], typed: true) else { return XCTFail("a 32 KB + 1 string reached the renderer") }
            XCTAssertEqual(script.validator.stopped, "Text limit exceeded: a text value of 32769 characters (max 32768)")
        }
    }

    // MARK: canvas pixels

    /// A `<canvas>` `id` in the body with these attributes, in this order (the bundle reads an object's keys in order).
    private func canvasEl(_ id: Int, _ attrs: KeyValuePairs<String, String> = [:]) -> String {
        let list = attrs.map { "\"\($0.key)\":\"\($0.value)\"" }.joined(separator: ",")
        return "{\"op\":\"el\",\"id\":\(id),\"tag\":\"canvas\",\"attrs\":{\(list)}},{\"op\":\"ins\",\"parent\":3,\"id\":\(id),\"before\":null}"
    }

    /// The shim's `r` (`canvas.width = …`) as a canvas word payload, after `fillRects` fillRect commands.
    private func resize(_ w: Double, _ h: Double, after fillRects: Int = 0) -> WidgetCanvasBatch {
        var words: [Double] = []
        for i in 0..<fillRects { words += [0, 0, 0, 4, 0, Double(i), 0, 0, 0, 1, 0, 1] }
        return WidgetCanvasBatch(words: words + [2, -1, 0, 2, 0, w, 0, h], strings: fillRects > 0 ? ["fillRect"] : [])
    }

    /// strict-limits.test.ts, "2048² pixels over the widget's canvases draw; one more stops the widget, by attribute or
    /// by the shim's r; removing a canvas refunds it", on the script validator and the native model: the same ops, the
    /// same diagnostics, the same reason.
    func testCanvasPixelsStopOnBothValidatorPaths() {
        let steps: [(String, String?)] = [
            ("[" + canvasEl(10, ["width": "2048", "height": "1024"]) + "," + canvasEl(11) + "]", nil),
            // 2048 × 1024 + 2048 × 1024 = 2048²: still fits (the second canvas was 300 × 150)
            ("[{\"op\":\"canvas\",\"id\":11,\"cmds\":[[\"r\",2048,1024]]}]", nil),
            ("[{\"op\":\"rm\",\"id\":11}]", nil),
            ("[" + canvasEl(12, ["width": "2048", "height": "1024"]) + "]", nil),
            ("[" + canvasEl(13) + "]", "Canvas limit exceeded: canvases of 4239304 pixels together (max 4194304)"),
        ]
        var results: [[(NSArray, Int, [String])]] = []
        var nanSteps: [[(NSArray, Int, [String])]] = []
        for native in [false, true] {
            let v = rig(native: native)
            var outs: [(NSArray, Int, [String])] = []
            for (k, (batch, stop)) in steps.enumerated() {
                let out = v.json(batch)
                XCTAssertEqual(v.validator.stopped, stop, "step \(k) (native \(native))")
                if let stop {
                    XCTAssertEqual(out.0.count, 0)
                    XCTAssertEqual(out.2, ["limit|" + stop], "native \(native)")
                } else {
                    XCTAssertGreaterThan(out.0.count, 0, "step \(k) (native \(native))")
                }
                outs.append(out)
            }
            XCTAssertEqual(v.json("[{\"op\":\"rm\",\"id\":12}," + canvasEl(14) + "]").0.count, 0, "a stopped validator refuses every batch (native \(native))")
            results.append(outs)

            let w = rig(native: native)
            _ = w.json("[" + canvasEl(10) + "]")
            XCTAssertEqual(w.json("[{\"op\":\"attr\",\"id\":10,\"name\":\"width\",\"value\":\"30000\"}]").0.count, 0)
            XCTAssertEqual(w.validator.stopped, "Canvas limit exceeded: canvases of 4500000 pixels together (max 4194304)", "native \(native)")

            // a size past 2147483647 is the default, so 0 × 1e310 cannot make the sum NaN and switch the cap off
            let n = rig(native: native)
            let nan = n.json("[" + canvasEl(10, ["width": "0", "height": "1" + String(repeating: "0", count: 310)]) + "]")
            XCTAssertNil(n.validator.stopped, "native \(native)")
            let past = n.json("[" + canvasEl(11, ["width": "4096", "height": "2048"]) + "]")
            XCTAssertEqual(past.0.count, 0)
            XCTAssertTrue(n.validator.stopped?.hasPrefix("Canvas limit exceeded: canvases of 8388608 pixels together") ?? false, "native \(native): \(String(describing: n.validator.stopped))")
            nanSteps.append([nan, past])

            let c = rig(native: native, policy: .compat)
            XCTAssertEqual(c.json("[" + canvasEl(10, ["width": "8192", "height": "8192"]) + "]").0.count, 2)
            XCTAssertEqual(c.json("[{\"op\":\"canvas\",\"id\":10,\"cmds\":[[\"r\",16384,16384]]}]").0.count, 1)
            XCTAssertNil(c.validator.stopped, "compat leaves canvas sizes to the renderer (native \(native))")
        }
        for k in steps.indices {
            XCTAssertEqual(results[1][k].0, results[0][k].0, "step \(k)")
            XCTAssertEqual(results[1][k].2, results[0][k].2, "step \(k)")
        }
        for k in 0..<2 {
            XCTAssertEqual(nanSteps[1][k].0, nanSteps[0][k].0, "0 × 1e310, batch \(k)")
            XCTAssertEqual(nanSteps[1][k].2, nanSteps[0][k].2, "0 × 1e310, batch \(k)")
        }
    }

    /// A canvas's size by HTML's rules for non-negative integers (`width` / `height` on creation, `attr`, `rattr` back to
    /// 300 × 150), a removed subtree refunding its canvases, a send's `r`s in order, and the reason's number as JS
    /// prints it: the native model gives what the script validator gives, op for op.
    func testCanvasSizesMatchTheScriptValidator() {
        XCTAssertEqual(NativeValidator.canvasDimension(" \t\n\u{c}\r+2048xx"), 2048)
        XCTAssertEqual(NativeValidator.canvasDimension("007"), 7)
        for value in ["", "-5", "+", "++5", " + 5", "\u{a0}12", "\u{b}12", "\u{FF11}\u{FF12}", "x12"] { XCTAssertNil(NativeValidator.canvasDimension(value), value) }
        XCTAssertEqual(NativeValidator.canvasDimension("1" + String(repeating: "0", count: 320)), .infinity)

        let huge = "1" + String(repeating: "0", count: 320)
        let rm = { (id: Int, name: String) in "{\"op\":\"rattr\",\"id\":\(id),\"name\":\"\(name)\"}" }
        let attr = { (id: Int, name: String, value: String) in "{\"op\":\"attr\",\"id\":\(id),\"name\":\"\(name)\",\"value\":\"\(value)\"}" }
        let canvasOp = { (id: Int, cmds: String) in "{\"op\":\"canvas\",\"id\":\(id),\"cmds\":\(cmds)}" }
        let big = String(repeating: "b", count: 32 * 1024 + 1)
        let scripts: [(String, [String], String?)] = [
            ("parsing", ["[" + canvasEl(10, ["width": " \\t+2048xx", "height": "-5"]) + "]", "[" + canvasEl(11, ["width": "2048", "height": "1975"]) + "]"],
             "Canvas limit exceeded: canvases of 4352000 pixels together (max 4194304)"),
            ("names in any case", ["[" + canvasEl(10, ["WIDTH": "2048", "Height": "2048"]) + "]", "[" + attr(10, "HEIGHT", "2049") + "]"],
             "Canvas limit exceeded: canvases of 4196352 pixels together (max 4194304)"),
            ("rattr back to 300 × 150", ["[" + canvasEl(10, ["width": "2048", "height": "2048"]) + "]", "[" + rm(10, "Height") + "," + canvasEl(11, ["width": "2048", "height": "1898"]) + "]",
                                         "[" + rm(11, "width") + "," + attr(10, "data-x", "1") + "," + rm(10, "title") + "]", "[" + attr(11, "width", "2048") + "," + attr(10, "height", "151") + "]"],
             "Canvas limit exceeded: canvases of 4196352 pixels together (max 4194304)"),
            ("a removed subtree", ["[{\"op\":\"el\",\"id\":20,\"tag\":\"div\"},{\"op\":\"ins\",\"parent\":3,\"id\":20,\"before\":null},{\"op\":\"el\",\"id\":21,\"tag\":\"canvas\",\"attrs\":{\"width\":\"2048\",\"height\":\"2048\"}},{\"op\":\"ins\",\"parent\":20,\"id\":21,\"before\":null}]",
                                   "[{\"op\":\"rm\",\"id\":20}," + canvasEl(22, ["width": "2048", "height": "2048"]) + "]", "[" + canvasEl(23) + "]"],
             "Canvas limit exceeded: canvases of 4239304 pixels together (max 4194304)"),
            ("SVG has no canvas", ["[{\"op\":\"el\",\"id\":20,\"tag\":\"canvas\",\"ns\":\"svg\",\"attrs\":{\"width\":\"99999\",\"height\":\"99999\"}}]", "[" + canvasEl(10, ["width": "2048", "height": "2048"]) + "]"], nil),
            ("r in order", ["[" + canvasEl(10) + "]", "[" + canvasOp(10, "[[\"r\",4096,4096],[\"r\",1,1]]") + "]"],
             "Canvas limit exceeded: canvases of 16777216 pixels together (max 4194304)"),
            ("r shrinks first", ["[" + canvasEl(10, ["width": "2048", "height": "2048"]) + "]", "[" + canvasOp(10, "[[\"r\",1,1],[\"c\",\"fillRect\",0,0,1,1]]") + "," + canvasEl(11, ["width": "2047", "height": "2048"]) + "]",
                                 "[" + canvasOp(10, "[[\"r\",2.9,1.9],[\"r\",-1,5],[\"r\",5],[\"r\",16385,1],[\"r\",\"9\",9],[\"c\",\"save\"],[\"r\",4,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0],[\"r\",3,1,0,0,0,0,0,0,0,0,0,0,0,0,0]]") + "]",
                                 "[" + canvasOp(10, "[[\"r\",1,2049]]") + "]"],
             "Canvas limit exceeded: canvases of 4194305 pixels together (max 4194304)"),
            ("a string before the r stops first", ["[" + canvasEl(10) + "]", "[" + canvasOp(10, "[[\"c\",\"fillRect\",0,0,1,1],[\"c\",\"fillText\",\"\(big)\",0,0],[\"r\",16384,16384]]") + "]"],
             "Text limit exceeded: a text value of 32769 characters (max 32768)"),
            ("the r before a string stops first", ["[" + canvasEl(10) + "]", "[" + canvasOp(10, "[[\"x\"],[\"s\",\"filter\",\"blur(1px)\"],[\"r\",16384,16384],[\"c\",\"fillText\",\"\(big)\",0,0]]") + "]"],
             "Canvas limit exceeded: canvases of 268435456 pixels together (max 4194304)"),
            ("digits past a double are the default", ["[" + canvasEl(10, ["width": "0", "height": huge]) + "," + canvasEl(11, ["width": huge, "height": "0"]) + "]",
                                                      "[" + attr(10, "width", "2048") + "," + attr(11, "height", "2048") + "]", "[" + canvasEl(12, ["width": "2048", "height": "2048"]) + "]"],
             "Canvas limit exceeded: canvases of 5115904 pixels together (max 4194304)"),
            ("past 2147483647 is the default", ["[" + canvasEl(10, ["width": "2147483648", "height": "99999999999"]) + "]", "[" + attr(10, "width", "2147483647") + "]"],
             "Canvas limit exceeded: canvases of 322122547050 pixels together (max 4194304)"),
            ("2147483647² as JS sums it", ["[" + canvasEl(10, ["width": "0", "height": "2147483647"]) + "]", "[" + attr(10, "width", "2147483647") + "]"],
             "Canvas limit exceeded: canvases of 4611686014132420600 pixels together (max 4194304)"),
        ]
        for (what, batches, stop) in scripts {
            var results: [[(NSArray, Int, [String])]] = []
            for native in [false, true] {
                let v = rig(native: native)
                results.append(batches.map { v.json($0) })
                XCTAssertEqual(v.validator.stopped, stop, "\(what) (native \(native))")
            }
            for k in batches.indices {
                XCTAssertEqual(results[1][k].0, results[0][k].0, "\(what): batch \(k)")
                XCTAssertEqual(results[1][k].1, results[0][k].1, "\(what): batch \(k) nodes")
                XCTAssertEqual(results[1][k].2, results[0][k].2, "\(what): batch \(k) diagnostics")
            }
        }
    }

    /// The shim's `r` as canvas words through the native model (binary ops): the envelope's shortcut and the general
    /// path size the canvas as the script validator does with the same commands as JSON, and stop at the same `r`.
    func testCanvasWordResizesStopWhereTheScriptValidatorDoes() {
        let big = String(repeating: "b", count: 32 * 1024 + 1)
        let cases: [(String, WidgetCanvasBatch, String, String?)] = [
            ("fits", resize(2048, 1024), "[[\"r\",2048,1024]]", nil),
            ("one row more", resize(2048, 1025), "[[\"r\",2048,1025]]", "Canvas limit exceeded: canvases of 4196352 pixels together (max 4194304)"),
            ("after commands", resize(16384, 16384, after: 3), "[[\"c\",\"fillRect\",0,0,1,1],[\"c\",\"fillRect\",1,0,1,1],[\"c\",\"fillRect\",2,0,1,1],[\"r\",16384,16384]]",
             "Canvas limit exceeded: canvases of 270532608 pixels together (max 4194304)"),
            ("truncated", WidgetCanvasBatch(words: [2, -1, 0, 2, 0, 2048.99, 0, 1024.99], strings: []), "[[\"r\",2048.99,1024.99]]", nil),
            ("not an r the validator takes", WidgetCanvasBatch(words: [2, -1, 0, 2, 0, 16385, 0, 1, 2, -1, 0, 2, 1, 0, 0, 9, 2, -1, 0, 1, 0, 9000], strings: ["9000"]),
             "[[\"r\",16385,1],[\"r\",\"9000\",9],[\"r\",9000]]", nil),
            ("past the envelope", WidgetCanvasBatch(words: [2, -1, 0, 2, 0, 4096, 0, 4096, 0, 0, 0, 3, 1, 1, 0, 0, 0, 0], strings: ["fillText", big]),
             "[[\"r\",4096,4096],[\"c\",\"fillText\",\"\(big)\",0,0]]", "Canvas limit exceeded: canvases of 18874368 pixels together (max 4194304)"),
            ("a string before the r", WidgetCanvasBatch(words: [0, 0, 0, 3, 1, 1, 0, 0, 0, 0, 2, -1, 0, 2, 0, 4096, 0, 4096], strings: ["fillText", big]),
             "[[\"c\",\"fillText\",\"\(big)\",0,0],[\"r\",4096,4096]]", "Text limit exceeded: a text value of 32769 characters (max 32768)"),
        ]
        for (what, batch, json, stop) in cases {
            XCTAssertEqual(CanvasWords.commandsJSON(batch).map { Rig.normalized($0) }, Rig.normalized(json), what)
            let script = rig(native: false)
            _ = script.json("[" + canvasEl(10, ["width": "2048", "height": "1024"]) + "," + canvasEl(11) + "]")
            let expected = script.json("[{\"op\":\"canvas\",\"id\":11,\"cmds\":\(json)}]")
            XCTAssertEqual(script.validator.stopped, stop, what)

            let native = rig(native: true)
            var encoder = BinaryOpsEncoder()
            encoder.begin(reset: true)
            encoder.element(10, tag: "canvas", svg: false, attributes: [("width", "2048"), ("height", "1024")])
            encoder.insert(parent: 3, 10, before: 0)
            encoder.element(11, tag: "canvas", svg: false, attributes: [])
            encoder.insert(parent: 3, 11, before: 0)
            XCTAssertEqual(native.binary(encoder.finish()).0.count, 4)
            encoder.begin(reset: false)
            encoder.canvas(11, key: 7)
            let got = native.binary(encoder.finish(), canvas: [7: batch])
            XCTAssertEqual(native.validator.stopped, stop, what)
            XCTAssertEqual(got.0, expected.0, what)
            XCTAssertEqual(got.2, expected.2, what)
            // what the canvas holds now, seen by the next canvas: 45 000 pixels more
            let next = "[" + canvasEl(12) + "]"
            XCTAssertEqual(native.json(next).2, script.json(next).2, what)
            XCTAssertEqual(native.validator.stopped, script.validator.stopped, what)
        }
    }

    /// The script validator's host sends canvas words straight to the renderer when they pass the envelope; under strict
    /// a payload with an `r` goes through the validator instead, which counts the canvas pixels and stops past 2048².
    func testTheCanvasWordShortcutLeavesResizesToTheValidatorUnderStrict() {
        let send = "[{\"op\":\"canvas\",\"id\":9,\"bin\":7}]"
        let v = rig(native: false)
        _ = v.json(json(Self.canvas))
        guard case .ops = v.validator.processOpsForRenderer(send, canvas: [7: fillRects(1)], typed: true) else { return XCTFail("expected the shortcut") }
        XCTAssertEqual(v.validator.nativeCanvasBatches, 1)
        guard case .ops(let fits) = v.validator.processOpsForRenderer(send, canvas: [7: resize(2048, 2048)], typed: true) else { return XCTFail("2048² fits") }
        XCTAssertEqual(fits.count, 1)
        XCTAssertEqual(v.validator.nativeCanvasBatches, 1, "through the validator")
        guard case .empty = v.validator.processOpsForRenderer(send, canvas: [7: resize(2048, 2049, after: 2)], typed: true) else { return XCTFail("2048 × 2049 reached the renderer") }
        XCTAssertEqual(v.validator.stopped, "Canvas limit exceeded: canvases of 4196352 pixels together (max 4194304)")
        XCTAssertEqual(v.validator.nativeCanvasBatches, 1)

        let compat = rig(native: false, policy: .compat)
        _ = compat.json(json(Self.canvas))
        guard case .ops = compat.validator.processOpsForRenderer(send, canvas: [7: resize(16384, 16384)], typed: true) else { return XCTFail("compat takes the shortcut") }
        XCTAssertEqual(compat.validator.nativeCanvasBatches, 1)
        XCTAssertNil(compat.validator.stopped)
    }

    /// A widget whose canvases pass 2048² pixels together is stopped with the validator's reason, through the real engine
    /// and host, on the script validator and on the native model with canvas words.
    func testCanvasPixelsPast2048SquaredStopTheWidget() {
        let html = "<canvas id=a width=2048 height=1024></canvas><canvas id=b></canvas><p id=p>first</p><script>var b = document.getElementById('b'); b.getContext('2d').fillRect(0, 0, 1, 1);" +
            "setTimeout(function () { b.width = 2048; b.height = 1024; b.getContext('2d').fillRect(0, 0, 1, 1); document.getElementById('p').textContent = 'second'; }, 10);" +
            "setTimeout(function () { document.body.appendChild(document.createElement('canvas')); document.getElementById('p').textContent = 'never'; }, 40);</script>"
        let reason = "Canvas limit exceeded: canvases of 4239304 pixels together (max 4194304)"
        for features in [Set<String>(), ["binaryOps", "canvasWords"]] {
            let renderer = RecordingRenderer()
            var options = WidgetHost.Options()
            options.cpuLimitMs = 2000
            options.features = features
            let host = try! WidgetHost(html: html, theme: WidgetTheme(), width: 300, options: options, rendererFactory: { _, _, _ in renderer })
            defer { host.destroy() }
            let deadline = Date().addingTimeInterval(10)
            while host.diagnostics.dead == nil && Date() < deadline {
                RunLoop.main.run(until: Date().addingTimeInterval(0.01))
            }
            RunLoop.main.run(until: Date().addingTimeInterval(0.2))
            XCTAssertEqual(host.diagnostics.dead, reason, "\(features)")
            XCTAssertEqual(host.diagnostics.diags["limit"]?.details, [reason], "\(features)")
            let all = renderer.messages.joined()
            XCTAssertTrue(all.contains("second"), "the canvases that fit drew (\(features))")
            XCTAssertFalse(all.contains("never"), "\(features)")
        }
    }

    // MARK: the engine's cost model

    func testCanvasCommandsAreCountedForTheMainThreadCost() {
        XCTAssertEqual(SandboxEngine.countCanvasCommands(inJSON: "[{\"op\":\"canvas\",\"id\":9,\"cmds\":[[\"c\",\"fillRect\",0,0,1,1],[\"s\",\"fillStyle\",\"]\\\"cmds\\\":[\"],[\"c\",\"drawImage\",{\"n\":5},0,0]]},{\"op\":\"text\",\"id\":3,\"data\":\"\\\"cmds\\\":[1,2]\"},{\"op\":\"canvas\",\"id\":9,\"cmds\":[]}]"), 3)
        XCTAssertEqual(SandboxEngine.countCanvasCommands(inJSON: "[{\"op\":\"canvas\",\"id\":9,\"cmds\":[[\"c\",\"save\"]]},{\"op\":\"canvas\",\"id\":10,\"cmds\":[[\"c\",\"restore\"],[\"r\",1,1]]}]"), 3)
        XCTAssertEqual(SandboxEngine.countCanvasCommands(inJSON: "[{\"op\":\"canvas\",\"id\":9,\"cmds\":[[\"c\""), 1)
        XCTAssertEqual(CanvasWords.commandCount(fillRects(37)), 37)
        XCTAssertNil(CanvasWords.commandCount(WidgetCanvasBatch(words: [0, 0, 0, 4, 0], strings: ["fillRect"])))
    }

    // MARK: through the engine and the host

    func testThe65thLiveTimerThrowsRangeError() {
        let d = RecordingDelegate()
        let engine = SandboxEngine(html: "<script>var n = 0; try { for (; n < 100; n++) setTimeout(function () {}, 60000); } catch (e) { console.log(e.name + ' at ' + n); }</script>",
                                   config: SandboxConfig(innerWidth: 300, innerHeight: 200, devicePixelRatio: 2, theme: "light"))
        engine.delegate = d
        engine.start()
        wait(for: [d.loaded], timeout: 20)
        engine.queue.sync {}
        XCTAssertTrue(d.logs.contains { $0.1 == "RangeError at 64" }, "\(d.logs)")
        XCTAssertFalse(d.diags.contains { $0.0 == "timer-limit-enforced" })
        engine.terminate()
    }

    /// The boot config carries the policy: under strict the shim throws at the validator's 10 000 commands per canvas
    /// and task, so the author sees the error where the host would stop the widget; compat draws them all.
    func testTheShimRefusesTheCanvasCommandPastTheCapUnderStrict() {
        let html = "<canvas id=c></canvas><script>var x = document.getElementById('c').getContext('2d'); try { for (var i = 0; i < 4001; i++) x.fillRect(i, 0, 1, 1); console.log('drew all'); } catch (e) { console.log(e.name + ': ' + e.message); }</script>"
        for policy in [SandboxPolicy.strict, .compat] {
            let d = RecordingDelegate()
            var config = SandboxConfig(innerWidth: 300, innerHeight: 200, devicePixelRatio: 2, theme: "light")
            config.cpuLimitMs = 2000
            config.policy = policy
            let engine = SandboxEngine(html: html, config: config)
            engine.delegate = d
            engine.start()
            wait(for: [d.loaded], timeout: 20)
            engine.queue.sync {}
            let expected = policy == .strict ? "RangeError: Canvas limit exceeded: more than 4000 commands for one canvas in one task (max 4000)" : "drew all"
            XCTAssertTrue(d.logs.contains { $0.1 == expected }, "\(policy): \(d.logs)")
            engine.terminate()
        }
    }

    /// A widget whose script sets a 40 000-character text node is stopped with the validator's reason, through the
    /// real engine and host, on both validator paths: the renderer keeps its last render, frozen, and gets nothing after.
    func testATextNodeOver32KBStopsTheWidget() {
        let html = "<p id=p>first</p><script>setTimeout(function () { document.getElementById('p').textContent = 'x'.repeat(40000); }, 10); setTimeout(function () { document.getElementById('p').textContent = 'never'; }, 30); setTimeout(function () { localStorage.setItem('k', 'late'); }, 40);</script>"
        let reason = "Text limit exceeded: a text value of 40000 characters (max 32768)"
        for binary in [false, true] {
            let renderer = RecordingRenderer()
            var options = WidgetHost.Options()
            options.cpuLimitMs = 2000
            if binary { options.features = ["binaryOps"] }
            let host = try! WidgetHost(html: html, theme: WidgetTheme(), width: 300, options: options, rendererFactory: { _, _, _ in renderer })
            var commits = 0
            host.onStorageCommit = { _, _ in commits += 1 }
            defer { host.destroy() }
            let deadline = Date().addingTimeInterval(10)
            while host.diagnostics.dead == nil && Date() < deadline {
                RunLoop.main.run(until: Date().addingTimeInterval(0.01))
            }
            RunLoop.main.run(until: Date().addingTimeInterval(0.2))
            XCTAssertEqual(host.diagnostics.dead, reason, "binary \(binary)")
            XCTAssertEqual(host.diagnostics.diags["limit"]?.details, [reason], "binary \(binary)")
            let all = renderer.messages.joined()
            XCTAssertTrue(all.contains("first"), "the last render stays (binary \(binary))")
            XCTAssertFalse(all.contains(String(repeating: "x", count: 40000)), "binary \(binary)")
            XCTAssertFalse(all.contains("never"), "binary \(binary)")
            XCTAssertTrue(renderer.messages.contains { $0.contains("\"frozen\"") }, "binary \(binary)")
            XCTAssertEqual(commits, 0, "the engine was stopped (binary \(binary))")
        }
    }

    /// Answers the engine's frame requests like a renderer at ~60 Hz and records what the widget did.
    private final class FrameDriver: SandboxEngineDelegate {
        let loaded = XCTestExpectation(description: "loaded")
        let lock = NSLock()
        var diags: [(String, String)] = []
        var frames = 0
        var died: String?
        func engineRequestsFrame(_ engine: SandboxEngine) {
            DispatchQueue.global().asyncAfter(deadline: .now() + 0.016) { [weak engine] in engine?.frame(time: CACurrentMediaTime() * 1000) }
        }
        func engine(_ engine: SandboxEngine, ops json: String) {}
        func engine(_ engine: SandboxEngine, query json: String) -> (json: String, binary: Data?) { ("{}", nil) }
        func engine(_ engine: SandboxEngine, listen json: String) {}
        func engine(_ engine: SandboxEngine, preventDefault json: String) {}
        func engine(_ engine: SandboxEngine, log level: String, text: String) {
            if text == "frame" { lock.withLock { frames += 1 } }
        }
        func engine(_ engine: SandboxEngine, diag kind: String, detail: String) { lock.withLock { diags.append((kind, detail)) } }
        func engine(_ engine: SandboxEngine, bridge method: String, argsJson: String) {}
        func engineLoaded(_ engine: SandboxEngine) { loaded.fulfill() }
        func engine(_ engine: SandboxEngine, stats: SandboxStats) {}
        func engine(_ engine: SandboxEngine, died reason: String) { lock.withLock { died = reason }; loaded.fulfill() }
    }

    /// 200 text updates per frame cost the host 5 ms of modelled main-thread work: a 2 ms window lets about one frame
    /// a second through and says so; with the window open the same widget runs every frame. The JS quota is wide in both.
    func testTheMainThreadWindowDefersFrames() {
        let html = "<div id=list></div><script>var list = document.getElementById('list'); for (var i = 0; i < 200; i++) list.appendChild(document.createElement('span')); var n = 0;" +
            "function frame() { n++; for (var s = list.firstChild; s; s = s.nextSibling) s.textContent = String(n); console.log('frame'); requestAnimationFrame(frame); } requestAnimationFrame(frame);</script>"
        func run(mainThreadQuotaMs: Double, seconds: Double) -> (frames: Int, diags: [(String, String)], died: String?) {
            let d = FrameDriver()
            var config = SandboxConfig(innerWidth: 300, innerHeight: 200, devicePixelRatio: 2, theme: "light")
            config.cpuLimitMs = 2000
            config.cpuQuotaMs = 100_000
            config.mainThreadQuotaMs = mainThreadQuotaMs
            let engine = SandboxEngine(html: html, config: config)
            engine.delegate = d
            engine.start()
            wait(for: [d.loaded], timeout: 20)
            RunLoop.current.run(until: Date().addingTimeInterval(seconds))
            engine.queue.sync {}
            let result = d.lock.withLock { (d.frames, d.diags, d.died) }
            engine.terminate()
            return result
        }
        let open = run(mainThreadQuotaMs: 100_000, seconds: 1.5)
        XCTAssertNil(open.died)
        XCTAssertGreaterThan(open.frames, 10)
        XCTAssertFalse(open.diags.contains { $0.0 == "cpu-throttled" }, "\(open.diags.filter { $0.0 == "cpu-throttled" })")
        // The boot's 200 spans (about 5 ms) spend the window and leave a debt of one more: the first frame comes at about 2 s.
        let tight = run(mainThreadQuotaMs: 2, seconds: 2.5)
        XCTAssertNil(tight.died)
        XCTAssertLessThanOrEqual(tight.frames, 4, "frames through a 2 ms window in 2.5 s")
        XCTAssertGreaterThanOrEqual(tight.frames, 1)
        XCTAssertTrue(tight.diags.contains { $0.0 == "cpu-throttled" && $0.1.contains("main-thread budget used by layout queries and ops") }, "\(tight.diags)")
    }
}

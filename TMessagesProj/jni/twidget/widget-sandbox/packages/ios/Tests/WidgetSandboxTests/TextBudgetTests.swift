import XCTest
import WidgetSandboxProtocol
@testable import WidgetSandbox

/// Text values are capped at 32 KB and document text at 48 KB under strict by the validator bundle every native glue
/// loads (and the native node model); past a cap the widget stops (docs/decisions.md, "Strict limits from the start").
final class TextBudgetTests: XCTestCase {
    static var strict: WidgetHost.Options {
        var options = WidgetHost.Options()
        options.policy = .strict
        return options
    }

    func testCompatPolicyKeepsTheOldLimits() {
        let renderer = RecordingRenderer()
        var compat = WidgetHost.Options()
        compat.policy = .compat
        compat.memoryLimitBytes = 64 << 20 // the validator's compat caps, not the heap: a 1 MB text and its JSON
        let host = try! WidgetHost(html: "<p id=a></p><p id=b></p><script>document.getElementById('a').textContent='a'.repeat(1024*1024);document.getElementById('b').textContent='\\u0627'.repeat(20000);</script>", theme: WidgetTheme(), width: 300, options: compat, rendererFactory: { _, _, _ in renderer })
        defer { host.destroy() }
        let deadline = Date().addingTimeInterval(5)
        while !(host.diagnostics.rendered || host.diagnostics.dead != nil) && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
        let all = renderer.messages.joined()
        XCTAssertTrue(all.contains(String(repeating: "a", count: 1024 * 1024)))
        XCTAssertTrue(all.contains(String(repeating: "\u{0627}", count: 20000)))
        XCTAssertNil(host.diagnostics.diags["text-budget"])
    }

    /// 32 KB passes; 32 KB + 1 in the same task stops the widget and that task's batch is refused whole.
    func testTextNodesAreCappedAt32KB() {
        for binary in [false, true] {
            let renderer = RecordingRenderer()
            var options = Self.strict
            if binary { options.features = ["binaryOps"] }
            let host = try! WidgetHost(html: "<p id=a></p><p id=b></p><script>document.getElementById('a').textContent='a'.repeat(32*1024);document.getElementById('b').textContent='b'.repeat(32*1024+1);</script>", theme: WidgetTheme(), width: 300, options: options, rendererFactory: { _, _, _ in renderer })
            defer { host.destroy() }
            let deadline = Date().addingTimeInterval(5)
            while host.diagnostics.dead == nil && Date() < deadline {
                RunLoop.main.run(until: Date().addingTimeInterval(0.01))
            }
            RunLoop.main.run(until: Date().addingTimeInterval(0.2)) // the validator's diagnostics reach the executor on their own
            XCTAssertEqual(host.diagnostics.dead, "Text limit exceeded: a text value of 32769 characters (max 32768)", "binary \(binary)")
            let all = renderer.messages.joined()
            XCTAssertFalse(all.contains(String(repeating: "a", count: 32 * 1024)), "binary \(binary)")
            XCTAssertFalse(all.contains(String(repeating: "b", count: 32 * 1024 + 1)), "binary \(binary)")
            XCTAssertEqual(host.diagnostics.diags["limit"]?.details, ["Text limit exceeded: a text value of 32769 characters (max 32768)"], "binary \(binary)")
        }
    }

    /// 8192 complex-script units per document, through the validator bundle every native glue loads.
    func testComplexTextIsBudgetedPerDocument() {
        let renderer = RecordingRenderer()
        let host = try! WidgetHost(html: "<p id=a></p><p id=b></p><script>document.getElementById('a').textContent='\\u0627'.repeat(8000);document.getElementById('b').textContent='\\u0628'.repeat(500);</script>", theme: WidgetTheme(), width: 300, options: Self.strict, rendererFactory: { _, _, _ in renderer })
        defer { host.destroy() }
        let deadline = Date().addingTimeInterval(5)
        while !(host.diagnostics.rendered || host.diagnostics.dead != nil) && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
        let all = renderer.messages.joined()
        XCTAssertTrue(all.contains(String(repeating: "\u{0627}", count: 8000)))
        XCTAssertFalse(all.contains(String(repeating: "\u{0628}", count: 500)))
        XCTAssertGreaterThan(host.diagnostics.diags["text-budget"]?.count ?? 0, 0)
    }

    /// The same budget in the native node model (binary ops), checked against the JS validator op for op.
    func testComplexTextBudgetIsTheSameInTheNativeModel() {
        let arabic = { (n: Int) in String(repeating: "\u{0627}", count: n) }
        let batch = """
            [{"op":"el","id":2,"tag":"html"},{"op":"ins","parent":1,"id":2,"before":null},{"op":"el","id":3,"tag":"body"},{"op":"ins","parent":2,"id":3,"before":null},
             {"op":"text","id":20,"data":"\(arabic(5000))"},{"op":"ins","parent":3,"id":20,"before":null},
             {"op":"text","id":21,"data":"\(arabic(4000))"},{"op":"ins","parent":3,"id":21,"before":null},
             {"op":"text","id":22,"data":"\(arabic(3000))"},{"op":"ins","parent":3,"id":22,"before":null},
             {"op":"data","id":22,"data":"\(arabic(3193))"},{"op":"data","id":20,"data":"shrunk"},
             {"op":"text","id":23,"data":"\(arabic(5000))"},{"op":"ins","parent":3,"id":23,"before":null},{"op":"rm","id":23},
             {"op":"text","id":24,"data":"\(arabic(5000))"},{"op":"ins","parent":3,"id":24,"before":null}]
            """
        let js = NativeValidatorDifferentialTests.Rig(native: false, policy: .strict).json(batch)
        let native = NativeValidatorDifferentialTests.Rig(native: true, policy: .strict).json(batch)
        XCTAssertEqual(native.0, js.0)
        XCTAssertEqual(native.1, js.1)
        XCTAssertEqual(native.2, js.2)
        XCTAssertGreaterThanOrEqual(js.2.filter { $0.hasPrefix("text-budget") }.count, 2)
    }

    private func churn(binary: Bool, js: String, seconds: TimeInterval) -> (renderer: RecordingRenderer, churnDiags: Int) {
        let renderer = RecordingRenderer()
        var options = Self.strict
        if binary { options.features = ["binaryOps"] }
        let host = try! WidgetHost(html: "<p id=p></p><script>" + js + "</script>", theme: WidgetTheme(), width: 300, options: options, rendererFactory: { _, _, _ in renderer })
        defer { host.destroy() }
        let deadline = Date().addingTimeInterval(seconds)
        while Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
        return (renderer, host.diagnostics.diags["text-churn"]?.count ?? 0)
    }

    func testComplexTextChurnIsRateLimitedOnBothValidatorPaths() {
        let js = "var p=document.getElementById('p'),n=0;setInterval(function(){n++;var c=String.fromCharCode(0x4E00+n%500);p.textContent=c.repeat(3000);},20);"
        for binary in [true, false] {
            let start = Date()
            let r = churn(binary: binary, js: js, seconds: 1.2)
            let elapsed = Date().timeIntervalSince(start)
            var accepted = 0
            for message in r.renderer.messages {
                for n in 1...500 where message.contains(String(repeating: String(UnicodeScalar(0x4E00 + n % 500)!), count: 3000)) { accepted += 1 }
            }
            let allowed = (8192.0 + 1024.0 * elapsed) / 3000.0 + 1
            XCTAssertGreaterThan(r.churnDiags, 0, "binary \(binary)")
            XCTAssertGreaterThan(accepted, 0, "binary \(binary)")
            XCTAssertLessThanOrEqual(Double(accepted), allowed, "binary \(binary) accepted \(accepted)")
        }
    }

    /// The twin of strict-limits.test.ts "text inserted while the widget loads": ~45 K characters of paragraphs built by
    /// the loading script are not charged to the churn bucket; the host's `markLoaded` starts it.
    func testTextInsertedWhileLoadingIsNotChargedToTheChurnBucket() {
        let js = "for(var i=0;i<95;i++){var q=document.createElement('p');q.textContent='Paragraph '+i+': '+'lorem ipsum dolor sit amet '.repeat(17);document.body.appendChild(q);}"
        for binary in [true, false] {
            let r = churn(binary: binary, js: js, seconds: 0.8)
            XCTAssertEqual(r.churnDiags, 0, "binary \(binary)")
            XCTAssertTrue(r.renderer.messages.contains { $0.contains("Paragraph 94:") }, "binary \(binary)")
        }
    }

    func testTickerTextNeverHitsTheChurnLimit() {
        let js = "var p=document.getElementById('p'),n=0;setInterval(function(){n++;p.textContent='BTC $'+(60000+n)+' \\u25B2 +'+(n%7)+'.0% \\u00B7 ETH $'+(3000+n%50)+' \\u092E\\u0947\\u0930\\u093E';},10);"
        for binary in [true, false] {
            XCTAssertEqual(churn(binary: binary, js: js, seconds: 0.8).churnDiags, 0, "binary \(binary)")
        }
    }

    func testLatinTickerOf400CellsNeverHitsTheChurnLimitOnBothValidatorPaths() {
        for native in [true, false] {
            let rig = NativeValidatorDifferentialTests.Rig(native: native, policy: .strict)
            let cells = 0..<400
            _ = rig.json("[{\"op\":\"el\",\"id\":2,\"tag\":\"html\"},{\"op\":\"ins\",\"parent\":1,\"id\":2,\"before\":null},{\"op\":\"el\",\"id\":3,\"tag\":\"body\"},{\"op\":\"ins\",\"parent\":2,\"id\":3,\"before\":null}," + cells.map { "{\"op\":\"text\",\"id\":\(20 + $0),\"data\":\"0.00\"},{\"op\":\"ins\",\"parent\":3,\"id\":\(20 + $0),\"before\":null}" }.joined(separator: ",") + "]")
            var accepted = 0
            for frame in 0..<60 {
                let arrow = frame % 2 == 0 ? "\u{25B2}" : "\u{25BC}"
                accepted += rig.json("[" + cells.map { "{\"op\":\"data\",\"id\":\(20 + $0),\"data\":\"\(100 + $0 + frame).\(frame % 100) \(arrow) \(frame % 9 - 4).0%\"}" }.joined(separator: ",") + "]").0.count
            }
            XCTAssertEqual(accepted, 400 * 60, "native \(native)")
            XCTAssertFalse(rig.diags.contains { $0.hasPrefix("text-churn") }, "native \(native)")
        }
    }

    func testTextWorkCountsBreaksAcrossScripts() {
        XCTAssertEqual(NativeValidator.textWork("BTC $60000 \u{25B2}"), 3)
        XCTAssertEqual(NativeValidator.textWork("\u{4E2D}\u{6587}\u{5B57} hello \u{092E}\u{0947}\u{0930}\u{093E} \u{0928}\u{093E}\u{092E}"), 6)
        XCTAssertEqual(NativeValidator.textWork("\u{3042}\u{3044}\u{3046}\u{3048}\u{304A}\u{304B}\u{304D}\u{304F}\u{3051}\u{3053}\u{3002}\u{3055}\u{3057}\u{3059}"), 14)
        XCTAssertEqual(NativeValidator.textWork(""), 0)
    }

    func testChurnRefillsWithTheClockInTheNativeModel() {
        let rig = NativeValidatorDifferentialTests.Rig(native: true, policy: .strict)
        var now = 1000.0
        rig.validator.thread.sync { rig.validator.nativeModel?.clock = { now } }
        let cjk = { (n: Int) in String(repeating: "\u{4E2D}", count: n) }
        _ = rig.json("[{\"op\":\"el\",\"id\":2,\"tag\":\"html\"},{\"op\":\"ins\",\"parent\":1,\"id\":2,\"before\":null},{\"op\":\"el\",\"id\":3,\"tag\":\"body\"},{\"op\":\"ins\",\"parent\":2,\"id\":3,\"before\":null},{\"op\":\"text\",\"id\":20,\"data\":\"\(cjk(3000))\"},{\"op\":\"ins\",\"parent\":3,\"id\":20,\"before\":null}]")
        var accepted = 0
        for _ in 0 ..< 4 {
            let r = rig.json("[{\"op\":\"data\",\"id\":20,\"data\":\"\(cjk(2999))\"},{\"op\":\"data\",\"id\":20,\"data\":\"\(cjk(3000))\"}]")
            accepted += r.0.count
        }
        XCTAssertEqual(accepted, 1)
        XCTAssertTrue(rig.diags.contains { $0.hasPrefix("text-churn") })
        now += 6
        XCTAssertEqual(rig.json("[{\"op\":\"data\",\"id\":20,\"data\":\"\(cjk(2999))\"}]").0.count, 1)
    }

    func testCompatPolicyLeavesChurnAloneOnBothValidatorPaths() {
        let cjk = { (n: Int) in String(repeating: "\u{4E2D}", count: n) }
        for native in [true, false] {
            let rig = NativeValidatorDifferentialTests.Rig(native: native)
            _ = rig.json("[{\"op\":\"el\",\"id\":2,\"tag\":\"html\"},{\"op\":\"ins\",\"parent\":1,\"id\":2,\"before\":null},{\"op\":\"el\",\"id\":3,\"tag\":\"body\"},{\"op\":\"ins\",\"parent\":2,\"id\":3,\"before\":null},{\"op\":\"text\",\"id\":20,\"data\":\"\(cjk(3000))\"},{\"op\":\"ins\",\"parent\":3,\"id\":20,\"before\":null}]")
            var accepted = 0
            for _ in 0 ..< 4 {
                accepted += rig.json("[{\"op\":\"data\",\"id\":20,\"data\":\"\(cjk(2999))\"},{\"op\":\"data\",\"id\":20,\"data\":\"\(cjk(3000))\"}]").0.count
            }
            XCTAssertEqual(accepted, 8, "native \(native)")
            XCTAssertFalse(rig.diags.contains { $0.hasPrefix("text-churn") }, "native \(native)")
        }
    }
    func testHostileTextFloodsOnBothValidatorPaths() {
        for native in [true, false] {
            for unit in ["a ", "a-", "x?", "\u{3400}", "\u{ac00}", "\u{30a2}", "\u{ff21}", "\u{20000}"] {
                let rig = NativeValidatorDifferentialTests.Rig(native: native, policy: .strict)
                let data = String(repeating: unit, count: 3000)
                var accepted = 0
                var limited = false
                for i in 20..<30 {
                    accepted += rig.json("[{\"op\":\"text\",\"id\":\(i),\"data\":\"\(data)\"}]").0.count
                    limited = limited || rig.diags.contains { $0.hasPrefix("text-churn") }
                    _ = rig.json("[{\"op\":\"rm\",\"id\":\(i)}]")
                }
                XCTAssertEqual(accepted, 2, "native \(native), unit \(unit)")
                XCTAssertTrue(limited)
            }
        }
    }

    func testNativeResetRefundsTextBudgets() {
        let rig = NativeValidatorDifferentialTests.Rig(native: true, policy: .strict)
        let data = String(repeating: "中", count: 8000)
        let op = "[{\"op\":\"text\",\"id\":20,\"data\":\"\(data)\"}]"
        XCTAssertEqual(rig.json(op).0.count, 1)
        rig.validator.thread.sync { rig.validator.nativeModel?.reset() }
        XCTAssertEqual(rig.json(op).0.count, 1)
    }

    func testVisibleTextBudgetsMatchOnBothValidatorPaths() throws {
        let chunk = String(repeating: "a", count: 16 * 1024)
        let title = String(repeating: "a", count: 8192)
        let arabic = { String(repeating: "ا", count: $0) }
        // Batches run one after another on one validator of each kind.
        let cases: [[[[String: Any]]]] = [
            // An attribute value over 8 KB stops the widget, on creation and on mutation.
            [[["op": "el", "id": 20, "tag": "button", "attrs": ["value": title, "data-label": title + "x"]]]],
            [[["op": "el", "id": 20, "tag": "button"]], [["op": "attr", "id": 20, "name": "TITLE", "value": title + "x"]], [["op": "text", "id": 21, "data": "after"]]],
            // 48 KB of document text shared with visible attributes, removals refunded; past it the widget stops.
            [[
                ["op": "text", "id": 20, "data": chunk], ["op": "text", "id": 21, "data": chunk],
                ["op": "el", "id": 30, "tag": "div", "attrs": ["title": title]], ["op": "text", "id": 22, "data": title],
                ["op": "rm", "id": 30], ["op": "text", "id": 24, "data": title],
            ], [["op": "text", "id": 25, "data": "overflow"]]],
            // The complex-script budget only drops (text-budget), as before.
            [[
                ["op": "text", "id": 20, "data": arabic(6000)],
                ["op": "el", "id": 21, "tag": "button", "attrs": ["title": arabic(3000), "value": arabic(2000)]],
                ["op": "rattr", "id": 21, "name": "VALUE"], ["op": "attr", "id": 21, "name": "value", "value": arabic(2192)],
                ["op": "attr", "id": 21, "name": "title", "value": arabic(1)],
            ]],
        ]
        for (index, batches) in cases.enumerated() {
            let js = NativeValidatorDifferentialTests.Rig(native: false, policy: .strict)
            let native = NativeValidatorDifferentialTests.Rig(native: true, policy: .strict)
            var outputs: [[[String: Any]]] = []
            var diags: [String] = []
            for (k, ops) in batches.enumerated() {
                let batch = String(decoding: try JSONSerialization.data(withJSONObject: ops, options: [.sortedKeys]), as: UTF8.self)
                let a = js.json(batch), b = native.json(batch)
                XCTAssertEqual(b.0, a.0, "case \(index) batch \(k)")
                XCTAssertEqual(b.1, a.1, "case \(index) batch \(k)")
                XCTAssertEqual(b.2, a.2, "case \(index) batch \(k)")
                outputs.append(b.0 as! [[String: Any]])
                diags += b.2
            }
            XCTAssertEqual(js.validator.stopped, native.validator.stopped, "case \(index)")
            switch index {
            case 0:
                XCTAssertEqual(outputs.map(\.count), [0])
                XCTAssertEqual(native.validator.stopped, "Attribute limit exceeded: a value of 8193 characters (max 8192)")
                XCTAssertTrue(diags.contains("limit|Attribute limit exceeded: a value of 8193 characters (max 8192)"))
            case 1:
                XCTAssertEqual(outputs[1].count, 0)
                XCTAssertEqual(outputs[2].count, 0, "a stopped validator refuses every batch")
                XCTAssertEqual(native.validator.stopped, "Attribute limit exceeded: a value of 8193 characters (max 8192)")
            case 2:
                XCTAssertTrue(outputs[0].contains { $0["op"] as? String == "el" && ($0["attrs"] as? [Any])?.isEmpty == false })
                XCTAssertTrue(outputs[0].contains { $0["id"] as? Int == 24 }, "the removed title was refunded")
                XCTAssertEqual(outputs[1].count, 0)
                XCTAssertEqual(native.validator.stopped, "Text limit exceeded: the document's text is over 49152 characters")
            default:
                XCTAssertNil(native.validator.stopped)
                XCTAssertTrue(diags.contains { $0.hasPrefix("text-budget") })
                XCTAssertFalse(outputs[0].contains { $0["op"] as? String == "attr" && $0["name"] as? String == "title" })
                XCTAssertTrue(outputs[0].contains { $0["op"] as? String == "attr" && $0["value"] as? String == arabic(2192) })
            }
        }
    }

    func testAttributeChurnIsLimitedOnBothPaths() throws {
        for native in [false, true] {
            let rig = NativeValidatorDifferentialTests.Rig(native: native, policy: .strict)
            var ops: [[String: Any]] = [["op": "el", "id": 20, "tag": "div"]]
            for i in 0..<10 {
                ops.append(["op": "attr", "id": 20, "name": "title", "value": String(repeating: String(UnicodeScalar(0x4e00 + i)!), count: 3000)])
            }
            let batch = String(decoding: try JSONSerialization.data(withJSONObject: ops), as: UTF8.self)
            let result = rig.json(batch)
            let output = result.0 as! [[String: Any]]
            XCTAssertEqual(output.filter { $0["op"] as? String == "attr" }.count, 2)
            XCTAssertTrue(result.2.contains { $0.hasPrefix("text-churn") })
        }
    }

    /// 48 KB of document text: three 8 KB attributes and 16 KB of text, a removed attribute refunded once, a removed
    /// element refunded once; one character past the full budget stops the widget.
    func testBinaryVisibleAttributeBudgetsRefundExactlyOnce() {
        let attribute = String(repeating: "x", count: 8192)
        let text = String(repeating: "x", count: 16384)
        func textIds(_ output: NSArray) -> [Int] {
            (output as! [[String: Any]]).filter { $0["op"] as? String == "text" }.compactMap { $0["id"] as? Int }
        }
        do {
            let rig = NativeValidatorDifferentialTests.Rig(native: true, policy: .strict)
            var encoder = BinaryOpsEncoder()
            encoder.begin(reset: true)
            encoder.element(20, tag: "button", svg: false, attributes: [("value", attribute), ("data-label", attribute), ("title", attribute)])
            encoder.text(21, text, code: .text)
            encoder.removeAttribute(20, "VALUE")
            encoder.removeAttribute(20, "value")
            encoder.text(23, text, code: .text)
            XCTAssertEqual(textIds(rig.binary(encoder.finish()).0), [21, 23])
            encoder.begin()
            encoder.text(24, "x", code: .text)
            XCTAssertEqual(rig.binary(encoder.finish()).0.count, 0, "full: a removed attribute is refunded once")
            XCTAssertEqual(rig.validator.stopped, "Text limit exceeded: the document's text is over 49152 characters")
        }
        do {
            let rig = NativeValidatorDifferentialTests.Rig(native: true, policy: .strict)
            var encoder = BinaryOpsEncoder()
            encoder.begin(reset: true)
            encoder.element(20, tag: "button", svg: false, attributes: [("value", attribute), ("data-label", attribute), ("title", attribute)])
            encoder.text(21, text, code: .text)
            encoder.remove(20)
            encoder.text(25, text, code: .text)
            encoder.text(26, text, code: .text)
            XCTAssertEqual(textIds(rig.binary(encoder.finish()).0), [21, 25, 26])
            XCTAssertNil(rig.validator.stopped)
            encoder.begin()
            encoder.text(27, "still full", code: .text)
            XCTAssertEqual(rig.binary(encoder.finish()).0.count, 0)
            XCTAssertEqual(rig.validator.stopped, "Text limit exceeded: the document's text is over 49152 characters")
        }
    }

    func testCanvasTextChurnIsLimitedAcrossJSONAndBinaryPaths() {
        for native in [false, true] {
            for binary in [false, true] where native || !binary {
                for strict in [false, true] {
                    let rig = NativeValidatorDifferentialTests.Rig(native: native, policy: strict ? .strict : .compat)
                    var words: [Double] = []
                    for _ in 0..<10 { words += [0, 0, 0, 3, 1, 1, 0, 0, 0, 0] }
                    words += [0, 2, 0, 4, 0, 0, 0, 0, 0, 10, 0, 10]
                    words += [0, 3, 0, 3, 1, 4, 0, 0, 0, 0]
                    let batch = WidgetCanvasBatch(words: words, strings: ["fillText", String(repeating: "中", count: 3000), "fillRect", "strokeText", "Ready"])
                    let output: NSArray
                    if binary {
                        var encoder = BinaryOpsEncoder()
                        encoder.begin(reset: true)
                        encoder.element(20, tag: "canvas", svg: false, attributes: [])
                        encoder.canvas(20, key: 7)
                        output = rig.binary(encoder.finish(), canvas: [7: batch]).0
                    } else {
                        let ops: [[String: Any]] = [["op": "el", "id": 20, "tag": "canvas"], ["op": "canvas", "id": 20, "cmds": batch.commands()]]
                        output = rig.json(String(decoding: try! JSONSerialization.data(withJSONObject: ops), as: UTF8.self)).0
                    }
                    let commands = (output as! [[String: Any]]).filter { $0["op"] as? String == "canvas" }.flatMap { $0["cmds"] as! [[Any]] }
                    XCTAssertEqual(commands.count, strict ? 4 : 12, "native \(native), binary \(binary)")
                    XCTAssertEqual(rig.diags.contains { $0.hasPrefix("text-churn") }, strict)
                }
            }
        }
    }

    func testCanvasTextSharesTheDocumentChurnBudget() throws {
        for native in [false, true] {
            let rig = NativeValidatorDifferentialTests.Rig(native: native, policy: .strict)
            let ops: [[String: Any]] = [
                ["op": "text", "id": 21, "data": String(repeating: "中", count: 6000)],
                ["op": "el", "id": 20, "tag": "canvas"],
                ["op": "canvas", "id": 20, "cmds": [["c", "strokeText", String(repeating: "中", count: 3000), 0, 0]]],
            ]
            let result = rig.json(String(decoding: try JSONSerialization.data(withJSONObject: ops), as: UTF8.self))
            XCTAssertFalse((result.0 as! [[String: Any]]).contains { $0["op"] as? String == "canvas" })
            XCTAssertTrue(result.2.contains { $0.hasPrefix("text-churn") })
        }
    }

    func testCanvasWordShortcutCannotBypassJavaScriptChurn() {
        let rig = NativeValidatorDifferentialTests.Rig(native: false, policy: .strict)
        _ = rig.json("[{\"op\":\"el\",\"id\":20,\"tag\":\"canvas\"}]")
        let command: [Double] = [0, 0, 0, 3, 1, 1, 0, 0, 0, 0]
        let words = Array(repeating: command, count: 10).flatMap { $0 }
        let batch = WidgetCanvasBatch(words: words, strings: ["fillText", String(repeating: "中", count: 3000)])
        let result = rig.validator.processOpsForRenderer("[{\"op\":\"canvas\",\"id\":20,\"bin\":7}]", canvas: [7: batch], typed: true)
        guard case .ops(let ops) = result else { return XCTFail("expected accepted canvas commands") }
        let decoded = NativeValidatorDifferentialTests.Rig.normalized(WidgetOp.json(ops)) as! [[String: Any]]
        XCTAssertEqual((decoded.first?["cmds"] as? [Any])?.count, 2)
        XCTAssertTrue(rig.diags.contains { $0.hasPrefix("text-churn") })
        XCTAssertEqual(rig.validator.nativeCanvasBatches, 0)
    }

    func testShortCanvasLabelsKeepTheWordShortcut() {
        let rig = NativeValidatorDifferentialTests.Rig(native: false, policy: .strict)
        _ = rig.json("[{\"op\":\"el\",\"id\":20,\"tag\":\"canvas\"}]")
        let command: [Double] = [0, 0, 0, 3, 1, 1, 0, 0, 0, 0]
        let words = Array(repeating: command, count: 200).flatMap { $0 }
        let batch = WidgetCanvasBatch(words: words, strings: ["fillText", "BTC $100.00"])
        for _ in 0..<60 {
            let result = rig.validator.processOpsForRenderer("[{\"op\":\"canvas\",\"id\":20,\"bin\":7}]", canvas: [7: batch], typed: true)
            guard case .ops(let ops) = result else { return XCTFail("expected short labels") }
            XCTAssertEqual(ops.count, 1)
        }
        XCTAssertEqual(rig.validator.nativeCanvasBatches, 60)
        XCTAssertFalse(rig.diags.contains { $0.hasPrefix("text-churn") })
    }

}

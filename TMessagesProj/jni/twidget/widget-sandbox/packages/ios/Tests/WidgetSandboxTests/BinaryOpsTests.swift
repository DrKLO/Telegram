import XCTest
@testable import WidgetSandbox

/// Binary DOM ops (`config.features` = binaryOps): the engine hands its delegate the
/// same ops JSON, byte for byte, as the JSON path; malformed buffers are rejected whole.
final class BinaryOpsTests: XCTestCase {
    private static let widgets = [
        """
        <div id="d" class="a b" style="color: red !important; width: 1px" data-x="1">x<b>y</b><!-- c --></div>
        <svg viewBox="0 0 10 10"><circle cx="5" cy="5" r="4"/></svg><input id="i" value="v">
        <script>
        var d = document.getElementById('d'), n = 0;
        function tick() {
          d.className = 'k' + (n % 3); d.setAttribute('data-n', 'v\\u0000' + n); d.removeAttribute('data-x');
          d.style.opacity = String(n / 10); d.style.setProperty('--k', 'x', 'important');
          d.firstChild.data = 'h\\u00e9 \\ud83d\\ude00 ' + n; d.lastElementChild && n % 2 && d.removeChild(d.lastElementChild);
          var p = document.createElement('p'); p.textContent = '"quoted" \\\\ ' + n; d.insertBefore(p, d.firstChild);
          document.getElementById('i').value = 'typed ' + n;
          if (++n < 40) setTimeout(tick, 1);
        }
        tick();
        </script>
        """,
        """
        <canvas id="c" width="50" height="50"></canvas><p id="p">0</p><script>
        var c = document.getElementById('c').getContext('2d'), f = 0;
        function frame() { c.fillStyle = 'red'; c.fillRect(f, 0, 1.5, 1e21); c.fillText('\\u00e9' + f, 0, 0); if (f % 2) document.getElementById('p').textContent = String(f); if (++f < 8) requestAnimationFrame(frame); }
        frame();
        </script>
        """,
    ]

    private func run(_ html: String, features: Set<String>) -> (ops: [String], binary: Int) {
        let d = RecordingDelegate()
        var config = SandboxConfig(innerWidth: 400, innerHeight: 300, devicePixelRatio: 2, theme: "light")
        config.features = features
        let engine = SandboxEngine(html: html, config: config)
        engine.delegate = d
        engine.start()
        wait(for: [d.loaded], timeout: 20)
        for frame in 0..<10 {
            engine.frame(time: Double(frame) * 16)
            engine.queue.sync {}
        }
        let end = Date().addingTimeInterval(0.6)
        while Date() < end { RunLoop.current.run(until: Date().addingTimeInterval(0.02)); engine.queue.sync {} }
        let binary = engine.queue.sync { engine.binaryBatches }
        engine.terminate()
        return (d.lock.withLock { d.ops }, binary)
    }

    func testBinaryOpsDeliverTheSameJSONAsTheJSONPath() {
        for (k, html) in Self.widgets.enumerated() {
            let json = run(html, features: [])
            let binary = run(html, features: ["binaryOps"])
            let both = run(html, features: ["binaryOps", "canvasWords"])
            XCTAssertEqual(json.binary, 0)
            XCTAssertGreaterThan(binary.binary, 3, "widget \(k)")
            XCTAssertEqual(binary.ops, json.ops, "widget \(k)")
            XCTAssertEqual(both.ops, json.ops, "widget \(k) with canvas words")
        }
    }

    /// Through a host: binary batches go to the validator's native model (no JSON in between), JSON stays on the JS validator, and the renderer sees the same ops.
    func testHostsValidateBinaryOpsNatively() throws {
        func host(_ features: Set<String>) throws -> (messages: [String], native: Bool) {
            var options = WidgetHost.Options()
            options.asyncValidation = true
            options.features = features
            let renderer = RecordingRenderer()
            let host = try WidgetHost(html: Self.widgets[0], theme: WidgetTheme(), width: 300, options: options, rendererFactory: { _, _, _ in renderer })
            defer { host.destroy() }
            let deadline = Date().addingTimeInterval(10)
            while !(host.diagnostics.rendered || host.diagnostics.dead != nil) && Date() < deadline { RunLoop.main.run(until: Date().addingTimeInterval(0.01)) }
            RunLoop.main.run(until: Date().addingTimeInterval(1.0))
            let validator = Mirror(reflecting: host).children.first { $0.label == "validator" }?.value as! TrustedValidator
            return (renderer.messages, validator.usesNativeModel)
        }
        let json = try host([])
        let binary = try host(["binaryOps"])
        XCTAssertFalse(json.native)
        XCTAssertTrue(binary.native)
        func ops(_ messages: [String]) -> [NSDictionary] {
            messages.compactMap { m -> [[String: Any]]? in
                guard let o = (try? JSONSerialization.jsonObject(with: Data(m.utf8))) as? [String: Any], o["type"] as? String == "ops" else { return nil }
                return o["ops"] as? [[String: Any]]
            }.flatMap { $0 }.map { op -> NSDictionary in
                var op = op
                if op["op"] as? String == "style", op["important"] as? Bool == false { op["important"] = nil }
                if op["op"] as? String == "el", (op["attrs"] as? [Any])?.isEmpty == true { op["attrs"] = nil }
                return op as NSDictionary
            }
        }
        XCTAssertGreaterThan(ops(json.messages).count, 10)
        XCTAssertEqual(ops(binary.messages), ops(json.messages))
    }

    private func decode(_ words: [Int32], _ strings: String = "", table: BinaryInternTable = BinaryInternTable()) -> Result<BinaryOps.Decoded, BinaryOps.Failure> {
        BinaryOps.decode(BinaryOpsPayload(words: words, strings: Array(strings.utf8)), table: table)
    }

    private func failure(_ r: Result<BinaryOps.Decoded, BinaryOps.Failure>) -> BinaryOps.Failure? {
        if case .failure(let f) = r { return f }
        return nil
    }

    func testMalformedBuffersAreRejectedWhole() {
        let m = BinaryOps.magic
        XCTAssertNotNil(failure(decode([])), "empty")
        XCTAssertNotNil(failure(decode([0x1234, 0])), "bad magic")
        XCTAssertNotNil(failure(decode([m, 1, 99, 1])), "bad opcode")
        XCTAssertNotNil(failure(decode([m, 1, 8])), "truncated rm")
        XCTAssertNotNil(failure(decode([m, 1, 2, 5, 10], "abc")), "string past the end")
        XCTAssertNotNil(failure(decode([m, 1, 2, 5, 2], "abc")), "unused string bytes")
        XCTAssertNotNil(failure(decode([m, 2, 8, 5])), "op count mismatch")
        XCTAssertNotNil(failure(decode([m, 1, 5, 5, -1, 0])), "undefined intern reference")
        XCTAssertNotNil(failure(decode([m, 0, 0, 3, 1], "a")), "definition out of order")
        XCTAssertNotNil(failure(decode([m, 0, 0, 0, 300], String(repeating: "a", count: 300))), "definition too long")
        XCTAssertNotNil(failure(decode([m, 1, 2, 5, -2])), "negative string length")
        let overlong = BinaryOpsPayload(words: [m, 1, 2, 5, 1], strings: [0xC0, 0x80])
        if case .success = BinaryOps.decode(overlong, table: BinaryInternTable()) { XCTFail("overlong UTF-8") }
        let surrogate = BinaryOpsPayload(words: [m, 1, 2, 5, 1], strings: [0xED, 0xA0, 0x80])
        if case .success = BinaryOps.decode(surrogate, table: BinaryInternTable()) { XCTFail("UTF-8 surrogate") }
        XCTAssertNil(failure(decode([m, 1, 2, 5, 3], "abc")), "a valid text op")
    }

    func testAFailedBatchDropsItsInternDefinitions() {
        let m = BinaryOps.magic
        let table = BinaryInternTable()
        XCTAssertNotNil(failure(decode([m, 1, 0, 0, 3, 99], "div", table: table)))
        XCTAssertEqual(table.strings.count, 0)
        XCTAssertNil(failure(decode([m, 1, 0, 0, 3, 8, 5], "div", table: table)))
        XCTAssertEqual(table.strings.count, 1)
        XCTAssertNil(failure(decode([m, 1, 6, 5, -1], table: table)), "the committed entry is usable in the next batch")
    }

    func testWidgetCodeCannotForgeBinaryOps() {
        let d = RecordingDelegate()
        var config = SandboxConfig(innerWidth: 400, innerHeight: 300, devicePixelRatio: 2, theme: "light")
        config.features = ["binaryOps"]
        let engine = SandboxEngine(html: "<script>console.log('hostOps ' + typeof __hostOps);</script>", config: config)
        engine.delegate = d
        engine.start()
        wait(for: [d.loaded], timeout: 20)
        engine.queue.sync {}
        XCTAssertTrue(d.lock.withLock { d.logs.contains { $0.1 == "hostOps undefined" } })
        engine.terminate()
    }
}

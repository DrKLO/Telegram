import XCTest
@testable import WidgetSandbox

/// corpus/attacks through the binary ops path and the Swift native node model
/// (`binaryOps` + `canvasWords`, strict policy): nothing reaches the renderer that
/// the script validator would not let through on the JSON path.
final class AttackCorpusNativeModelTests: XCTestCase {
    private static let attacks = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        .appendingPathComponent("corpus/attacks")

    private func run(_ html: String, features: Set<String>) -> RecordingRenderer {
        let renderer = RecordingRenderer()
        var options = WidgetHost.Options()
        options.features = features
        let host = try! WidgetHost(html: html, theme: WidgetTheme(), width: 300, options: options, rendererFactory: { _, _, _ in renderer })
        let deadline = Date().addingTimeInterval(3)
        while !(host.diagnostics.rendered || host.diagnostics.dead != nil) && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
        RunLoop.main.run(until: Date().addingTimeInterval(0.3))
        host.destroy()
        return renderer
    }

    private func needles(_ renderer: RecordingRenderer) -> Set<String> {
        var found = Set<String>()
        for message in renderer.messages.dropFirst(2) {
            let lower = HostOpsAttackTests.withoutTextData(message).lowercased()
            for needle in HostOpsAttackTests.forbidden where lower.contains(needle) { found.insert(needle) }
        }
        return found
    }

    private func validatedOps(_ renderer: RecordingRenderer) -> NSArray {
        let all = NSMutableArray()
        for message in renderer.messages {
            guard let object = (try? JSONSerialization.jsonObject(with: Data(message.utf8))) as? [String: Any], object["type"] as? String == "ops",
                  let ops = object["ops"] as? [Any] else { continue }
            all.addObjects(from: ops)
        }
        return all
    }

    /// Canvas words and binary batches through the native model give the renderer exactly
    /// what the JSON path through the script validator gives it, under the strict budgets
    /// (a canvas string just under the 32 KB cap; past it the widget stops: StrictLimitsTests).
    func testCanvasWordsAndStrictBudgetsMatchTheScriptValidator() {
        let html = """
            <canvas id="c" width="200" height="100"></canvas><p id="p" data-x="\(String(repeating: "\u{4E2D}", count: 6000))"></p><p id="q"></p><script>
            var c = document.getElementById('c').getContext('2d');
            var g = c.createLinearGradient(0, 0, 100, 0); g.addColorStop(0, 'red'); g.addColorStop(1, 'blue');
            c.fillStyle = g; c.fillRect(0, 0, 50, 50); c.font = '12px sans-serif';
            c.fillText('h\\u00e9llo \\ud83d\\ude00', 5, 80); c.fillText('x'.repeat(30000), 0, 0); c.strokeText('\\u4e2d'.repeat(100), 0, 20);
            c.setLineDash([2, 3.25]); c.arc(50, 50, 10, 0, Math.PI * 2, false); c.stroke();
            var q = document.getElementById('q');
            q.setAttribute('data-y', '\\u4e2d'.repeat(4000)); q.style.setProperty('--s', '"' + '\\u4e2d'.repeat(100) + '"');
            q.setAttribute('style', 'content: attr(class); --t: "' + '\\u2500'.repeat(50) + '"');
            setTimeout(function () { c.clearRect(0, 0, 10, 10); c.fillText('later', 1, 1); }, 1);
            </script>
            """
        let viaJSON = validatedOps(run(html, features: []))
        let viaNative = validatedOps(run(html, features: ["binaryOps", "canvasWords"]))
        XCTAssertGreaterThan(viaJSON.count, 5)
        XCTAssertTrue(viaJSON.description.contains("fillText"))
        XCTAssertEqual(viaNative, viaJSON)
    }

    func testAttackCorpusThroughTheNativeModel() throws {
        guard let files = try? FileManager.default.contentsOfDirectory(atPath: Self.attacks.path).filter({ $0.hasSuffix(".html") }).sorted(), !files.isEmpty else {
            throw XCTSkip("corpus/attacks is not readable from this test process")
        }
        var compared = 0
        for file in files {
            let html = try String(contentsOf: Self.attacks.appendingPathComponent(file), encoding: .utf8)
            let viaJSON = needles(run(html, features: []))
            let viaNative = run(html, features: ["binaryOps", "canvasWords", "webAnimations"])
            let extra = needles(viaNative).subtracting(viaJSON)
            XCTAssertTrue(extra.isEmpty, "\(file): the native model let through \(extra.sorted())")
            compared += 1
        }
        print("attack corpus through the native model: \(compared) files")
        XCTAssertGreaterThan(compared, 90)
    }
}

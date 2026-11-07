import AppKit
import XCTest
import WidgetSandbox
@testable import TwidgetRenderer

final class WidgetTaskPresentationTests: XCTestCase {
    private func send(_ renderer: LiteHTMLWidgetRenderer, _ ops: [[String: Any]]) throws {
        let json = try JSONSerialization.data(withJSONObject: ["type": "ops", "ops": ops])
        renderer.send(String(decoding: json, as: UTF8.self))
    }

    func testNestedTasksAnswerLayoutQueriesWithoutPresentingIntermediateContent() throws {
        _ = NSApplication.shared
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 300)
        defer { renderer.destroy() }
        var heights: [Double] = []
        var measured: [Double] = []
        renderer.onMessage = { json in
            guard let object = try? JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any] else { return }
            if object["type"] as? String == "size", let height = object["height"] as? Double {
                heights.append(height)
            }
            if object["type"] as? String == "result", let result = object["result"] as? [String: Any], let height = result["offsetHeight"] as? Double {
                measured.append(height)
            }
        }
        renderer.load()
        try send(renderer, [
            ["op": "el", "id": 1, "tag": "body", "adopt": "body", "attrs": [["style", "margin:0"]]],
            ["op": "el", "id": 3, "tag": "div", "attrs": [["style", "height:40px;background:red"]]],
            ["op": "ins", "id": 3, "parent": 1],
        ])
        let initial = try XCTUnwrap(renderer.presentedDocument)
        heights.removeAll()
        renderer.beginTask()
        renderer.beginTask()
        for height in [240, 80] {
            try send(renderer, [["op": "attr", "id": 3, "name": "style", "value": "height:\(height)px;background:blue"]])
            renderer.send("{\"type\":\"query\",\"qid\":1,\"query\":{\"q\":\"box\",\"id\":3}}")
            XCTAssertTrue(renderer.presentedDocument === initial)
            XCTAssertTrue(heights.isEmpty)
        }
        XCTAssertEqual(measured, [240, 80])
        renderer.endTask()
        XCTAssertTrue(renderer.presentedDocument === initial)
        renderer.endTask()
        XCTAssertFalse(renderer.presentedDocument === initial)
        XCTAssertEqual(heights, [80])
        XCTAssertEqual(renderer.lastResizeDiff?.oldHeight, 40)
        XCTAssertEqual(renderer.lastResizeDiff?.newHeight, 80)
    }

    private func pump(until done: () -> Bool) {
        let end = Date().addingTimeInterval(5)
        var ready = done()
        while !ready && Date() < end {
            RunLoop.main.run(until: Date().addingTimeInterval(0.005))
            ready = done()
        }
        XCTAssertTrue(ready)
    }

    func testSupportThreadDoesNotPublishHeightsFromItsFittingLoop() throws {
        _ = NSApplication.shared
        let window = NSWindow(contentRect: CGRect(x: 0, y: 0, width: 420, height: 600), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        defer { window.close() }
        let file = URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("../../../widget-sandbox/corpus/resize/2-grows-up/02-support-thread.html")
        let queue = WidgetExecutor.makeQueue("widget.task.presentation")
        let executor = WidgetExecutor(queue: queue)
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 420)
        var options = WidgetHost.Options()
        options.asyncValidation = true
        options.sharedValidatorThread = true
        options.startBeforeRendererReady = true
        options.features = ["binaryOps", "canvasWords", "webAnimations", "morphUpdates"]
        options.policy = .strict
        let host = try WidgetHost(html: String(contentsOf: file), theme: WidgetTheme(colorScheme: .light), width: 420, options: options, rendererFactory: { _, _, _ in renderer }, executor: executor)
        defer { host.destroy() }
        window.contentView?.addSubview(host.view)
        var sizes: [CGFloat] = []
        var commits = 0
        executor.async {
            renderer.preparesOffscreen = true
            renderer.resizeAnchor = .bottom
            let forward = renderer.onMessage
            renderer.onMessage = { json in
                if let object = try? JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any], object["type"] as? String == "size", let height = object["height"] as? Double {
                    sizes.append(CGFloat(height))
                }
                forward?(json)
            }
        }
        host.onChange = { [weak host] diagnostics in
            if diagnostics.height > 0 {
                host?.view.setFrameSize(CGSize(width: 420, height: diagnostics.height))
            }
        }
        host.onStorageCommit = { _, _ in commits += 1 }
        pump { host.diagnostics.loaded && host.diagnostics.rendered && queue.sync { renderer.taskDepth == 0 && renderer.isPrepared && renderer.heldOpBatches == 0 } }
        for title in ["Where is my parcel?", "Change the time", "Talk to a person", "Leave it at the door", "Where is my parcel?", "Change the time", "Talk to a person", "Where is my parcel?", "Start over", "Leave it at the door"] {
            let previous = host.diagnostics.height
            let previousCommits = commits
            queue.sync { sizes.removeAll() }
            executor.async {
                let button = renderer.dom.nodes.values.first { $0.tag == "button" && WidgetDOM.textContent($0) == title }!
                let id = renderer.dom.publicId(button)
                renderer.noteUserInput()
                renderer.onMessage?("{\"type\":\"event\",\"trusted\":true,\"ev\":{\"type\":\"click\",\"targetId\":\(id),\"bubbles\":true}}")
            }
            pump { commits > previousCommits && queue.sync { renderer.taskDepth == 0 && renderer.isPrepared && renderer.heldOpBatches == 0 } }
            let final = host.diagnostics.height
            let heights = queue.sync { sizes }
            XCTAssertTrue(heights.allSatisfy { abs($0 - previous) < 1 || abs($0 - final) < 1 }, "\(title): intermediate heights \(heights), before \(previous), final \(final)")
            XCTAssertNil(host.diagnostics.dead)
        }
    }
}

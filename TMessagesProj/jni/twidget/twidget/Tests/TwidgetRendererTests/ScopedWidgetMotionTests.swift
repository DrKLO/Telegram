import AppKit
import XCTest
import WidgetSandbox
@testable import TwidgetRenderer

final class ScopedWidgetMotionTests: XCTestCase {
    private func pump(_ seconds: TimeInterval, until done: () -> Bool = { false }) {
        let deadline = Date().addingTimeInterval(seconds)
        while !done() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.005))
        }
    }

    private func payload(_ name: String) throws -> String {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("../../../../tools/perflab/widget-glitches/repaired")
        return try String(contentsOf: root.appendingPathComponent(name + ".html"), encoding: .utf8)
    }

    private func node(_ renderer: LiteHTMLWidgetRenderer, _ attributes: [String: String]) throws -> WidgetNode {
        return try XCTUnwrap(renderer.dom.nodes.values.first { node in attributes.allSatisfy { node.attribute($0.key) == $0.value } })
    }

    private func click(_ renderer: LiteHTMLWidgetRenderer, _ attributes: [String: String]) throws {
        let target = try node(renderer, attributes)
        renderer.noteUserInput()
        renderer.onMessage?("{\"type\":\"event\",\"trusted\":true,\"ev\":{\"type\":\"click\",\"targetId\":\(renderer.dom.publicId(target)),\"bubbles\":true,\"button\":0}}")
        pump(0.04)
    }

    func testSampleNavigationKeepsPanelsOpaqueAndStationary() throws {
        _ = NSApplication.shared
        let samples: [(String, [String: String], String)] = [
            ("reviews", ["data-a": "s", "data-v": "1"], "ls"),
            ("shortcuts", ["data-action": "tab", "data-value": "1"], "rows"),
            ("shortcuts-legacy", ["data-action": "tab", "data-value": "1"], "rows"),
            ("sprint", ["data-a": "f", "data-v": "1"], "list"),
            ("slots", ["data-action": "pay"], "pay")
        ]
        for (name, action, panelID) in samples {
            let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 400)
            var options = WidgetHost.Options()
            options.features = ["binaryOps", "canvasWords", "webAnimations", "morphUpdates"]
            let host = try WidgetHost(html: payload(name), theme: WidgetTheme(colorScheme: .light), width: 400, options: options, rendererFactory: { _, _, _ in renderer })
            defer { host.destroy() }
            host.view.frame = CGRect(x: 0, y: 0, width: 400, height: 600)
            pump(5) { host.diagnostics.rendered && renderer.document != nil }
            XCTAssertTrue(host.diagnostics.rendered, name)
            try click(renderer, action)
            let panel = try node(renderer, ["id": panelID])
            let publicID = renderer.dom.publicId(panel)
            XCTAssertFalse(renderer.animatedElementIDs.contains(publicID), name)
            XCTAssertNil(renderer.animationValues(publicID), name)
            let opacity = renderer.document?.computedStyle(attribute: WidgetDOM.idAttribute, value: String(publicID))["opacity"].flatMap(Double.init) ?? 1
            XCTAssertEqual(opacity, 1, accuracy: 0.001, name)
            if name == "sprint" {
                XCTAssertEqual(renderer.document?.bounds(matching: "#em").height ?? -1, 0, accuracy: 0.1)
            }
            XCTAssertNil(host.diagnostics.dead, name)
            XCTAssertEqual(host.diagnostics.logs.filter { $0.level == "error" }.map(\.text), [], name)
        }
    }

    func testStopwatchChartIsVisibleImmediatelyAndSurvivesPauseResume() throws {
        _ = NSApplication.shared
        for name in ["stopwatch", "stopwatch-fluid"] {
            let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 400)
            var options = WidgetHost.Options()
            options.features = ["binaryOps", "canvasWords", "webAnimations", "morphUpdates"]
            let host = try WidgetHost(html: payload(name), theme: WidgetTheme(colorScheme: .light), width: 400, options: options, rendererFactory: { _, _, _ in renderer })
            defer { host.destroy() }
            host.view.frame = CGRect(x: 0, y: 0, width: 400, height: 600)
            pump(5) { host.diagnostics.rendered && renderer.document != nil }
            try click(renderer, ["id": "right"])
            try click(renderer, ["id": "left"])
            try click(renderer, ["id": "right"])
            try click(renderer, ["data-view": "chart"])
            let fill = try XCTUnwrap(renderer.dom.nodes.values.first { $0.attribute("class") == "fill" })
            let fillID = renderer.dom.publicId(fill)
            let document = try XCTUnwrap(renderer.document)
            let track = document.bounds(matching: ".track")
            XCTAssertGreaterThan(track.width, 20, name)
            XCTAssertGreaterThanOrEqual(document.bounds(matching: ".fill").width, track.width * 0.95, name)
            for _ in 0..<2 {
                try click(renderer, ["id": "right"])
                let current = try XCTUnwrap(renderer.dom.nodes.values.first { $0.attribute("class") == "fill" })
                XCTAssertEqual(renderer.dom.publicId(current), fillID, name)
                XCTAssertGreaterThanOrEqual(renderer.document?.bounds(matching: ".fill").width ?? 0, track.width * 0.95, name)
            }
            XCTAssertFalse(renderer.animatedElementIDs.contains(renderer.dom.publicId(try node(renderer, ["id": "list"]))))
            XCTAssertNil(host.diagnostics.dead, name)
        }
    }
}

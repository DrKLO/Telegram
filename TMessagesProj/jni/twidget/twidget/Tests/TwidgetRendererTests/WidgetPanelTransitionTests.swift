import AppKit
import XCTest
import WidgetSandbox
@testable import TwidgetRenderer

final class WidgetPanelTransitionTests: XCTestCase {
    private func pump(_ seconds: TimeInterval, until done: () -> Bool = { false }) {
        let deadline = Date().addingTimeInterval(seconds)
        while !done() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.005))
        }
    }

    private func verifyTabs(path: String, rootId: String, action: String, value: String, tabs: [String]) throws {
        _ = NSApplication.shared
        let path = URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("../../../widget-sandbox/corpus/htmlbubbles-ported/" + path)
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 420)
        var options = WidgetHost.Options()
        options.features = ["binaryOps", "canvasWords", "webAnimations", "morphUpdates"]
        let host = try WidgetHost(html: String(contentsOf: path), theme: WidgetTheme(colorScheme: .light), width: 420, options: options, rendererFactory: { _, _, _ in renderer })
        defer { host.destroy() }
        host.view.frame = CGRect(x: 0, y: 0, width: 420, height: 600)
        pump(10) { host.diagnostics.rendered && renderer.dom.nodes.values.contains { $0.attribute("id") == rootId } }
        XCTAssertTrue(host.diagnostics.rendered)
        pump(1.25)
        let root = try XCTUnwrap(renderer.dom.nodes.values.first { $0.attribute("id") == rootId })
        let rootPublicId = renderer.dom.publicId(root)
        for index in 0..<12 {
            let tab = tabs[index % tabs.count]
            let target = try XCTUnwrap(renderer.dom.nodes.values.first { node in
                if action == "data-tab" {
                    return node.attribute(action) == tab
                }
                return node.attribute(action) == value && node.attribute("data-value") == tab
            })
            let start = CACurrentMediaTime()
            renderer.noteUserInput()
            renderer.onMessage?("{\"type\":\"event\",\"trusted\":true,\"ev\":{\"type\":\"click\",\"targetId\":\(renderer.dom.publicId(target)),\"bubbles\":true,\"button\":0}}")
            pump(1) { target.attribute("class")?.split(separator: " ").contains("on") == true }
            XCTAssertTrue(target.attribute("class")?.split(separator: " ").contains("on") == true)
            XCTAssertLessThan(CACurrentMediaTime() - start, 0.25)
            pump(0.025)
            XCTAssertFalse(renderer.animatedElementIDs.contains(rootPublicId), "Tab changes must keep the card and its controls stationary")
            let opacity = renderer.document?.computedStyle(attribute: WidgetDOM.idAttribute, value: String(rootPublicId))["opacity"].flatMap(Double.init) ?? 1
            XCTAssertEqual(opacity, 1, accuracy: 0.001)
            XCTAssertNil(renderer.animationValues(rootPublicId)?.opacity)
            XCTAssertNil(host.diagnostics.dead)
        }
        XCTAssertEqual(host.diagnostics.logs.filter { $0.level == "error" }.map(\.text), [])
    }

    func testPollTabsRespondImmediatelyWithoutFadingChrome() throws {
        try verifyTabs(path: "11-live-scripts/04-theo-bare.html", rootId: "root", action: "data-tab", value: "", tabs: ["voters", "results"])
    }

    func testTypeSpecimenTabsRespondImmediatelyWithoutFadingChrome() throws {
        try verifyTabs(path: "03-stress-test/01-fuzz.html", rootId: "ty", action: "data-action", value: "tab", tabs: ["1", "2", "0"])
    }
}

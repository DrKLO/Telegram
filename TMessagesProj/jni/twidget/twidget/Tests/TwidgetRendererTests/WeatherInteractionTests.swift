import AppKit
import XCTest
import WidgetSandbox
@testable import TwidgetRenderer

final class WeatherInteractionTests: XCTestCase {
    private final class VisibleWindow: NSWindow {
        override var isVisible: Bool { true }
        override var occlusionState: NSWindow.OcclusionState { .visible }
    }

    private func pump(_ seconds: TimeInterval, until done: () -> Bool = { false }) {
        let deadline = Date().addingTimeInterval(seconds)
        while !done() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.005))
        }
    }

    func testWeatherResizesWithoutFadingAndLatestUnitSelectionWins() throws {
        _ = NSApplication.shared
        let path = URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("../../../widget-sandbox/corpus/htmlbubbles-ported/06-trip-lisbon/05-tom-bare.html")
        let html = try String(contentsOf: path)
        for width: CGFloat in [240, 440] {
            let window = VisibleWindow(contentRect: CGRect(x: 0, y: 0, width: width, height: 600), styleMask: [.borderless], backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            defer { window.close() }
            let renderer = LiteHTMLWidgetRenderer(theme: "light", width: width)
            let host = try WidgetHost(html: html, theme: WidgetTheme(colorScheme: .light), width: width, rendererFactory: { _, _, _ in renderer })
            defer { host.destroy() }
            host.view.frame = CGRect(x: 0, y: 0, width: width, height: 500)
            window.contentView?.addSubview(host.view)
            pump(10) { host.diagnostics.rendered && renderer.dom.nodes.values.contains { $0.attribute("id") == "note" } }
            XCTAssertTrue(host.diagnostics.rendered)
            pump(1.4)
            func node(_ attribute: String, _ value: String) throws -> WidgetNode {
                try XCTUnwrap(renderer.dom.nodes.values.first { $0.attribute(attribute) == value })
            }
            func click(_ attribute: String, _ value: String) throws {
                let target = try node(attribute, value)
                renderer.noteUserInput()
                renderer.onMessage?("{\"type\":\"event\",\"trusted\":true,\"ev\":{\"type\":\"click\",\"targetId\":\(renderer.dom.publicId(target)),\"bubbles\":true,\"button\":0}}")
                pump(0.02)
            }
            let root = try node("id", "root")
            let initialHeight = host.diagnostics.height
            for _ in 0..<3 {
                try click("data-day", "0")
                pump(0.4) { host.diagnostics.height < initialHeight - 30 }
                XCTAssertLessThan(host.diagnostics.height, initialHeight - 30)
                XCTAssertNil(renderer.animationValues(root.id))
                try click("data-day", "0")
                pump(0.4) { abs(host.diagnostics.height - initialHeight) < 1 }
                XCTAssertEqual(host.diagnostics.height, initialHeight, accuracy: 1)
                XCTAssertNil(renderer.animationValues(root.id))
            }
            for unit in ["F", "C", "F", "C", "F"] {
                try click("data-unit", unit)
            }
            pump(0.4)
            XCTAssertEqual(WidgetDOM.textContent(try node("id", "now-t")), "75°")
            try click("data-metric", "wind")
            try click("data-metric", "rain")
            try click("data-metric", "temp")
            pump(0.4)
            XCTAssertTrue(WidgetDOM.textContent(try node("id", "note")).contains("79°"))
            let document = try XCTUnwrap(renderer.document)
            let bounds = document.bounds(matching: "#root")
            XCTAssertEqual(bounds.width, width, accuracy: 1)
            let chart = document.bounds(matching: "#chart")
            XCTAssertLessThanOrEqual(chart.maxX, width + 1)
            let labels = document.bounds(matching: "#labels .val")
            XCTAssertGreaterThanOrEqual(labels.minX, -1)
            XCTAssertLessThanOrEqual(labels.maxX, width + 1)
            XCTAssertNotNil(renderer.snapshot())
            XCTAssertNil(host.diagnostics.dead)
            XCTAssertNil(host.diagnostics.lastError)
            XCTAssertEqual(host.diagnostics.logs.filter { $0.level == "error" }.map(\.text), [])
        }
    }
}

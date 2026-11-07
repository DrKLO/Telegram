import AppKit
import XCTest
import WidgetSandbox
@testable import TwidgetRenderer

final class TableSelectionBorderTests: XCTestCase {
    private func pump(_ seconds: TimeInterval) {
        let end = Date().addingTimeInterval(seconds)
        while Date() < end {
            RunLoop.main.run(until: Date().addingTimeInterval(0.005))
        }
    }

    private func check(_ file: String, attribute: String, selections: [String]) throws {
        _ = NSApplication.shared
        let window = NSWindow(contentRect: CGRect(x: 0, y: 0, width: 420, height: 900), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.orderFrontRegardless()
        defer { window.close() }
        let path = URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("../../../widget-sandbox/corpus/htmlbubbles-ported/\(file)")
        let queue = WidgetExecutor.makeQueue("table.selection.regression")
        let executor = WidgetExecutor(queue: queue)
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 420)
        var options = WidgetHost.Options()
        options.asyncValidation = true
        options.sharedValidatorThread = true
        options.startBeforeRendererReady = true
        options.features = ["binaryOps", "canvasWords", "webAnimations", "morphUpdates"]
        options.policy = .strict
        let host = try WidgetHost(html: String(contentsOf: path), theme: WidgetTheme(colorScheme: .light), width: 420, options: options, rendererFactory: { _, _, _ in renderer }, executor: executor)
        defer { host.destroy() }
        window.contentView?.addSubview(host.view)
        host.onChange = { [weak host] diagnostics in
            if diagnostics.height > 0 {
                host?.view.setFrameSize(CGSize(width: 420, height: diagnostics.height))
            }
        }
        pump(1.8)
        XCTAssertNil(host.diagnostics.dead)
        XCTAssertTrue(host.diagnostics.rendered)
        let rect = CGRect(x: 0, y: 0, width: 420, height: host.diagnostics.height)
        func image() throws -> NSBitmapImageRep {
            return try queue.sync {
                let document = try XCTUnwrap(renderer.document)
                return NSBitmapImageRep(cgImage: try XCTUnwrap(document.copyImage(maxWidth: 420, rect: rect, scale: 2)))
            }
        }
        let reference = try image()
        let borders: [CGPoint] = queue.sync {
            let boxes = renderer.elementBounds()
            return renderer.dom.nodes.values.filter { $0.tag == "td" }.flatMap { node -> [CGPoint] in
                guard let box = boxes[renderer.dom.publicId(node)] else { return [] }
                return [CGPoint(x: box.minX + 0.5, y: box.midY), CGPoint(x: box.maxX - 0.5, y: box.midY), CGPoint(x: box.midX, y: box.minY + 0.5), CGPoint(x: box.midX, y: box.maxY - 0.5)]
            }
        }
        XCTAssertFalse(borders.isEmpty)
        for selection in selections {
            executor.async {
                let target = renderer.dom.nodes.values.first { $0.tag == "td" && $0.attribute(attribute) == selection }!
                let id = renderer.dom.publicId(target)
                renderer.noteUserInput()
                renderer.onMessage?("{\"type\":\"event\",\"trusted\":true,\"ev\":{\"type\":\"click\",\"targetId\":\(id),\"bubbles\":true}}")
            }
            pump(0.8)
            XCTAssertNil(host.diagnostics.dead)
            queue.sync {
                let target = renderer.dom.nodes.values.first { $0.tag == "td" && $0.attribute(attribute) == selection }!
                XCTAssertTrue(target.attribute("class")?.split(separator: " ").contains("sel") == true || target.declarations.contains { $0.name == "background-color" && !$0.value.isEmpty })
            }
            let selected = try image()
            for point in borders {
                let x = Int(point.x * 2), y = Int(point.y * 2)
                let before = try XCTUnwrap(reference.colorAt(x: x, y: y)?.usingColorSpace(.sRGB))
                if before.alphaComponent < 0.99 || max(before.redComponent, before.greenComponent, before.blueComponent) > 0.8 { continue }
                let after = try XCTUnwrap(selected.colorAt(x: x, y: y)?.usingColorSpace(.sRGB))
                XCTAssertLessThanOrEqual(after.redComponent, before.redComponent + 0.04, "\(selection) at \(point)")
                XCTAssertLessThanOrEqual(after.greenComponent, before.greenComponent + 0.04, "\(selection) at \(point)")
                XCTAssertLessThanOrEqual(after.blueComponent, before.blueComponent + 0.04, "\(selection) at \(point)")
            }
        }
    }

    func testRevenueWidgetSelectionKeepsCellBorders() throws {
        try check("03-stress-test/06-fuzz.html", attribute: "data-value", selections: ["1:2", "2:2", "3:4"])
    }

    func testSudokuWidgetSelectionKeepsGridBorders() throws {
        try check("10-game-night/09-sam-bare.html", attribute: "data-i", selections: ["29", "44", "80"])
    }
}

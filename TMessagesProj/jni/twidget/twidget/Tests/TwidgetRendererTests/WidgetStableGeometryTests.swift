import AppKit
import XCTest
import WidgetSandbox
@testable import TwidgetRenderer

final class WidgetStableGeometryTests: XCTestCase {
    private func pump(_ seconds: TimeInterval, until done: () -> Bool = { false }) {
        let deadline = Date().addingTimeInterval(seconds)
        while !done() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.005))
        }
    }

    private func payload(_ name: String) throws -> String {
        let root = ProcessInfo.processInfo.environment["TW_STABLE_WIDGET_DIR"].map { URL(fileURLWithPath: $0) } ?? URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("../../../../tools/perflab/widget-glitches/repaired")
        return try String(contentsOf: root.appendingPathComponent(name + ".html"), encoding: .utf8) + "<script>document.documentElement.setAttribute('data-stable-ready','1')</script>"
    }

    private func waitForFixturePresentation(_ host: WidgetHost, renderer: LiteHTMLWidgetRenderer) {
        let marker = "data-stable-ready=\"1\""
        pump(5) {
            guard host.diagnostics.rendered, let document = renderer.document, document.html.contains(marker) else { return false }
            let height = min(600, max(50, ceil(document.size.height)))
            return abs(host.diagnostics.height - height) < 0.5 && abs(renderer.documentView.size.height - height) < 0.5
        }
        XCTAssertTrue(host.diagnostics.rendered)
        XCTAssertTrue(renderer.document?.html.contains(marker) == true, "Fixture initialization has not reached the rendered document")
        if let document = renderer.document {
            let height = min(600, max(50, ceil(document.size.height)))
            XCTAssertEqual(host.diagnostics.height, height, accuracy: 0.5)
            XCTAssertEqual(renderer.documentView.size.height, height, accuracy: 0.5)
        }
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

    private func greenPixels(_ renderer: LiteHTMLWidgetRenderer, rect: CGRect) throws -> Int {
        let image = try XCTUnwrap(renderer.snapshot())
        let bitmap = try XCTUnwrap(image.tiffRepresentation.flatMap(NSBitmapImageRep.init(data:)))
        let scale = CGFloat(bitmap.pixelsWide) / image.size.width
        var count = 0
        for y in Int(floor(rect.minY * scale))..<Int(ceil(rect.maxY * scale)) {
            for x in Int(floor(rect.minX * scale))..<Int(ceil(rect.maxX * scale)) {
                if let color = bitmap.colorAt(x: x, y: y)?.usingColorSpace(.sRGB), color.greenComponent > 0.55, color.redComponent < 0.45, color.blueComponent < 0.65 {
                    count += 1
                }
            }
        }
        return count
    }

    private func capture(_ renderer: LiteHTMLWidgetRenderer, name: String) throws {
        guard let directory = ProcessInfo.processInfo.environment["TW_STABLE_GEOMETRY_OUTPUT"] else { return }
        let image = try XCTUnwrap(renderer.snapshot())
        let bitmap = try XCTUnwrap(image.tiffRepresentation.flatMap(NSBitmapImageRep.init(data:)))
        let context = try XCTUnwrap(CGContext(data: nil, width: bitmap.pixelsWide, height: bitmap.pixelsHigh, bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        let rect = CGRect(x: 0, y: 0, width: CGFloat(bitmap.pixelsWide), height: CGFloat(bitmap.pixelsHigh))
        context.setFillColor(CGColor(gray: 1, alpha: 1))
        context.fill(rect)
        context.draw(try XCTUnwrap(bitmap.cgImage), in: rect)
        let opaque = NSBitmapImageRep(cgImage: try XCTUnwrap(context.makeImage()))
        let output = URL(fileURLWithPath: directory)
        try FileManager.default.createDirectory(at: output, withIntermediateDirectories: true)
        try XCTUnwrap(opaque.representation(using: .png, properties: [:])).write(to: output.appendingPathComponent(name + ".png"))
    }

    func testPackingCheckboxesAndProgressUpdateWithoutFrames() throws {
        _ = NSApplication.shared
        for width: CGFloat in [320, 440] {
            let renderer = LiteHTMLWidgetRenderer(theme: "light", width: width)
            var options = WidgetHost.Options()
            options.features = ["binaryOps", "canvasWords", "webAnimations", "morphUpdates"]
            let host = try WidgetHost(html: payload("packing"), theme: WidgetTheme(colorScheme: .light), width: width, options: options, rendererFactory: { _, _, _ in renderer })
            defer { host.destroy() }
            host.onChange = { [weak host] state in
                if let host = host, state.height > 0, abs(host.view.frame.height - state.height) > 0.5 {
                    host.view.setFrameSize(CGSize(width: width, height: state.height))
                }
            }
            host.view.frame = CGRect(x: 0, y: 0, width: width, height: 600)
            waitForFixturePresentation(host, renderer: renderer)
            let reference = try XCTUnwrap(renderer.document).bounds(matching: "[data-id='1'] .box")
            let referencePixels = try greenPixels(renderer, rect: reference)
            XCTAssertGreaterThan(referencePixels, 100)
            for turn in 0..<3 {
                try click(renderer, ["data-id": "3"])
                let box = try XCTUnwrap(renderer.document).bounds(matching: "[data-id='3'] .box")
                XCTAssertEqual(box.width, 20, accuracy: 0.1)
                XCTAssertEqual(box.height, 20, accuracy: 0.1)
                XCTAssertEqual(WidgetDOM.textContent(try node(renderer, ["id": "pc"])), turn % 2 == 0 ? "40%" : "30%")
                if turn % 2 == 0 {
                    XCTAssertGreaterThanOrEqual(try greenPixels(renderer, rect: box), Int(Double(referencePixels) * 0.8))
                }
            }
            for category in ["docs", "all", "clothes", "all", "gear", "all", "docs", "all"] {
                try click(renderer, ["data-cat": category])
                let document = try XCTUnwrap(renderer.document)
                let root = document.bounds(matching: "#root")
                let items = document.bounds(matching: "#items")
                XCTAssertEqual(items.minX, root.minX, accuracy: 0.1)
                XCTAssertEqual(items.width, root.width, accuracy: 0.1)
                XCTAssertLessThanOrEqual(items.maxX, width + 0.1)
                XCTAssertGreaterThanOrEqual(items.minX, 0)
                XCTAssertFalse(renderer.animatedElementIDs.contains(renderer.dom.publicId(try node(renderer, ["id": "items"]))))
            }
            try capture(renderer, name: "packing-\(Int(width))-checked-and-tabs")
            XCTAssertNil(host.diagnostics.dead)
        }
    }

    func testPhrasebookTabsAndRevealsKeepTheSameInsets() throws {
        _ = NSApplication.shared
        for width: CGFloat in [320, 440] {
            let renderer = LiteHTMLWidgetRenderer(theme: "light", width: width)
            var options = WidgetHost.Options()
            options.features = ["binaryOps", "canvasWords", "webAnimations", "morphUpdates"]
            let host = try WidgetHost(html: payload("phrasebook"), theme: WidgetTheme(colorScheme: .light), width: width, options: options, rendererFactory: { _, _, _ in renderer })
            defer { host.destroy() }
            host.onChange = { [weak host] state in
                if let host = host, state.height > 0, abs(host.view.frame.height - state.height) > 0.5 {
                    host.view.setFrameSize(CGSize(width: width, height: state.height))
                }
            }
            host.view.frame = CGRect(x: 0, y: 0, width: width, height: 600)
            waitForFixturePresentation(host, renderer: renderer)
            try click(renderer, ["data-row": "0"])
            for category in ["1", "0", "2", "0", "3", "0", "1", "0"] {
                try click(renderer, ["data-cat": category])
                let document = try XCTUnwrap(renderer.document)
                let rows = document.bounds(matching: "#rows")
                let rowIndex = category == "2" ? "4" : category == "3" ? "8" : "0"
                let row = document.bounds(matching: "[data-row='\(rowIndex)']")
                let content = document.bounds(matching: "[data-row='\(rowIndex)'] > .row")
                XCTAssertEqual(row.minX, rows.minX, accuracy: 0.1)
                XCTAssertEqual(row.width, rows.width, accuracy: 0.1)
                XCTAssertEqual(content.minX - row.minX, 8, accuracy: 0.1)
                XCTAssertEqual(row.maxX - content.maxX, 8, accuracy: 0.1)
                XCTAssertLessThanOrEqual(row.maxX, width + 0.1)
                XCTAssertFalse(renderer.animatedElementIDs.contains(renderer.dom.publicId(try node(renderer, ["id": "rows"]))))
            }
            let document = try XCTUnwrap(renderer.document)
            let reveal = document.bounds(matching: "[data-row='0'] .rv")
            let revealedContent = document.bounds(matching: "[data-row='0'] .rvi")
            XCTAssertGreaterThan(reveal.height, 20)
            XCTAssertGreaterThanOrEqual(reveal.height + 0.1, revealedContent.height)
            try capture(renderer, name: "phrasebook-\(Int(width))-reveal-and-tabs")
            XCTAssertNil(host.diagnostics.dead)
        }
    }
}

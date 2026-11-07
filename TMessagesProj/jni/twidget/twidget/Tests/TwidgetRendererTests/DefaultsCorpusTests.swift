import XCTest
import AppKit
import WidgetSandbox
@testable import TwidgetRenderer

final class DefaultsCorpusTests: XCTestCase {
    private func pump(_ seconds: TimeInterval, until done: () -> Bool = { false }) {
        let deadline = Date().addingTimeInterval(seconds)
        while !done() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
    }

    func testDefaultsCorpusRendersOnTheClientTokens() throws {
        let sandbox = ProcessInfo.processInfo.environment["TWIDGET_WIDGET_SANDBOX"] ?? URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("../../../widget-sandbox").path
        let root = URL(fileURLWithPath: sandbox).appendingPathComponent("corpus/defaults")
        guard let enumerator = FileManager.default.enumerator(at: root, includingPropertiesForKeys: nil) else {
            throw XCTSkip("no widget-sandbox corpus at \(root.path)")
        }
        let files = enumerator.compactMap { $0 as? URL }.filter { $0.pathExtension == "html" }.sorted { $0.path < $1.path }
        XCTAssertEqual(files.count, 6)
        let out = ProcessInfo.processInfo.environment["TW_DEFAULTS_OUT"].map { URL(fileURLWithPath: $0) }
        if let out {
            try FileManager.default.createDirectory(at: out, withIntermediateDirectories: true)
        }
        _ = NSApplication.shared
        for file in files {
            let html = try String(contentsOf: file, encoding: .utf8)
            let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 320, height: 640), styleMask: [.borderless], backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            window.orderFrontRegardless()
            var made: LiteHTMLWidgetRenderer?
            let theme = WidgetTheme(colorScheme: .light, text: WidgetTextTokens(fontSize: 14, lineHeight: 1.2, padding: "7px 11px"))
            let host = try WidgetHost(html: html, theme: theme, width: 320, rendererFactory: { _, theme, width in
                let renderer = LiteHTMLWidgetRenderer(theme: theme, width: width)
                made = renderer
                return renderer
            })
            let renderer = try XCTUnwrap(made)
            host.view.frame = NSRect(x: 0, y: 0, width: 320, height: 640)
            window.contentView?.addSubview(host.view)
            pump(10, until: { host.diagnostics.loaded && host.diagnostics.rendered && renderer.document != nil && !renderer.isPreparingTiles })
            pump(0.5)
            let name = file.lastPathComponent
            let diagnostics = host.diagnostics
            XCTAssertTrue(diagnostics.loaded && diagnostics.rendered, name)
            XCTAssertNil(diagnostics.dead, name)
            XCTAssertEqual(diagnostics.logs.filter { $0.level == "error" }.map { $0.text }, [], name)
            let height = ceil(renderer.document?.size.height ?? 0)
            XCTAssertGreaterThan(height, 50, name)
            XCTAssertLessThanOrEqual(height, 600, name)
            let bodyMargin = renderer.document?.computedStyle(attribute: WidgetDOM.idAttribute, value: String(renderer.dom.publicId(renderer.dom.body)))["margin-left"]
            XCTAssertTrue(bodyMargin == "11px" || html.contains("margin"), "\(name) body margin \(bodyMargin ?? "-")")
            if let out, let image = renderer.snapshot(), let tiff = image.tiffRepresentation, let png = NSBitmapImageRep(data: tiff)?.representation(using: .png, properties: [:]) {
                try png.write(to: out.appendingPathComponent(file.deletingPathExtension().lastPathComponent + ".png"))
            }
            host.destroy()
            window.close()
        }
    }
}

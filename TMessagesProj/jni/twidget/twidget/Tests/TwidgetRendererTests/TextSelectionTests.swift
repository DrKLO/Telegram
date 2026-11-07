import XCTest
import AppKit
import WidgetSandbox
@testable import TwidgetRenderer

final class TextSelectionTests: XCTestCase {
    private func pump(_ seconds: TimeInterval, until done: () -> Bool = { false }) {
        let deadline = Date().addingTimeInterval(seconds)
        while !done() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
    }

    private final class Harness {
        let window: NSWindow
        let host: WidgetHost
        let renderer: LiteHTMLWidgetRenderer
        var changes: [Bool] = []

        init(_ test: TextSelectionTests, html: String) throws {
            _ = NSApplication.shared
            window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 320, height: 400), styleMask: [.borderless], backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            window.orderFrontRegardless()
            var made: LiteHTMLWidgetRenderer?
            host = try WidgetHost(html: html, theme: WidgetTheme(colorScheme: .light), width: 320, rendererFactory: { _, theme, width in
                let renderer = LiteHTMLWidgetRenderer(theme: theme, width: width)
                made = renderer
                return renderer
            })
            renderer = try XCTUnwrap(made)
            host.view.frame = NSRect(x: 0, y: 0, width: 320, height: 400)
            window.contentView?.addSubview(host.view)
            let host = self.host
            let renderer = self.renderer
            test.pump(10, until: { host.diagnostics.loaded && host.diagnostics.rendered && renderer.dom.nodes.values.contains { $0.attribute("id") == "done" } && !renderer.isPreparingTiles })
            test.pump(0.3)
            renderer.onTextSelectionChange = { [weak self] in self?.changes.append($0) }
        }

        func close() {
            host.destroy()
            window.close()
        }

        func bounds(_ id: String) throws -> CGRect {
            let node = try XCTUnwrap(renderer.dom.nodes.values.first { $0.attribute("id") == id })
            return try XCTUnwrap(renderer.elementBounds()[renderer.dom.publicId(node)])
        }

        func send(_ type: NSEvent.EventType, _ point: CGPoint, clicks: Int = 1) {
            let view = renderer.view
            let location = view.convert(point, to: nil)
            let event = NSEvent.mouseEvent(with: type, location: location, modifierFlags: [], timestamp: ProcessInfo.processInfo.systemUptime, windowNumber: window.windowNumber, context: nil, eventNumber: 0, clickCount: clicks, pressure: type == .leftMouseUp ? 0 : 1)!
            switch type {
            case .leftMouseDown: view.mouseDown(with: event)
            case .leftMouseDragged: view.mouseDragged(with: event)
            case .leftMouseUp: view.mouseUp(with: event)
            default: break
            }
        }

        func drag(from: CGPoint, to: CGPoint) {
            send(.leftMouseDown, from)
            send(.leftMouseDragged, CGPoint(x: (from.x + to.x) / 2, y: (from.y + to.y) / 2))
            send(.leftMouseDragged, to)
            send(.leftMouseUp, to)
        }
    }

    private static let html = #"<style>body{margin:0;font:16px/20px sans-serif}p{margin:0 0 10px}#on{user-select:text}</style><p id="plain">Plain words here</p><div id="on"><p id="child">Selectable words here</p></div><p id="off" style="-webkit-user-select:none">Nothing to take</p><i id="done"></i>"#

    func testDragSelectsOnlyOptedInText() throws {
        let h = try Harness(self, html: TextSelectionTests.html)
        defer { h.close() }
        let child = try h.bounds("child")
        h.drag(from: CGPoint(x: child.minX + 1, y: child.midY), to: CGPoint(x: child.maxX - 1, y: child.midY))
        XCTAssertEqual(h.renderer.selectedText, "Selectable words here")
        XCTAssertEqual(h.changes, [true])

        let plain = try h.bounds("plain")
        h.drag(from: CGPoint(x: plain.minX + 1, y: plain.midY), to: CGPoint(x: plain.maxX - 1, y: plain.midY))
        XCTAssertNil(h.renderer.selectedText)
        XCTAssertEqual(h.changes, [true, false])

        let off = try h.bounds("off")
        h.drag(from: CGPoint(x: off.minX + 1, y: off.midY), to: CGPoint(x: off.maxX - 1, y: off.midY))
        XCTAssertNil(h.renderer.selectedText)
    }

    func testDoubleClickSelectsAWordAndAClickClearsIt() throws {
        let h = try Harness(self, html: TextSelectionTests.html)
        defer { h.close() }
        let child = try h.bounds("child")
        let point = CGPoint(x: child.minX + 3, y: child.midY)
        h.send(.leftMouseDown, point, clicks: 2)
        h.send(.leftMouseUp, point, clicks: 2)
        XCTAssertEqual(h.renderer.selectedText, "Selectable")
        h.send(.leftMouseDown, CGPoint(x: child.maxX + 5, y: child.midY))
        h.send(.leftMouseUp, CGPoint(x: child.maxX + 5, y: child.midY))
        XCTAssertNil(h.renderer.selectedText)
        XCTAssertEqual(h.changes, [true, false])
    }

    func testSelectionDrawsAHighlightAndClearsOnDemand() throws {
        let h = try Harness(self, html: TextSelectionTests.html)
        defer { h.close() }
        let child = try h.bounds("child")
        h.drag(from: CGPoint(x: child.minX + 1, y: child.midY), to: CGPoint(x: child.midX, y: child.midY))
        let path = try XCTUnwrap(h.renderer.documentView.textSelection.layer.path)
        XCTAssertGreaterThan(path.boundingBox.width, 10)
        XCTAssertEqual(path.boundingBox.minY, child.minY, accuracy: 4)
        h.renderer.clearTextSelection()
        XCTAssertNil(h.renderer.documentView.textSelection.layer.path)
        XCTAssertNil(h.renderer.selectedText)
    }

    func testNothingIsSelectableByDefault() throws {
        let h = try Harness(self, html: #"<p id="p">Default text<i id="done"></i></p>"#)
        defer { h.close() }
        let p = try h.bounds("p")
        h.drag(from: CGPoint(x: p.minX + 1, y: p.midY), to: CGPoint(x: p.maxX - 1, y: p.midY))
        XCTAssertNil(h.renderer.selectedText)
        XCTAssertEqual(h.changes, [])
    }
}

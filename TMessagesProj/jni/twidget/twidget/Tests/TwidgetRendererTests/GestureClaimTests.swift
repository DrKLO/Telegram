import XCTest
import AppKit
import WidgetSandbox
@testable import TwidgetRenderer

final class GestureClaimTests: XCTestCase {
    private func pump(_ seconds: TimeInterval, until done: () -> Bool = { false }) {
        let deadline = Date().addingTimeInterval(seconds)
        while !done() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
    }

    private final class BubbleView: NSView {
        var menuClicks = 0
        var controlClicks = 0

        override func rightMouseDown(with event: NSEvent) {
            menuClicks += 1
        }

        override func mouseDown(with event: NSEvent) {
            if event.modifierFlags.contains(.control) {
                controlClicks += 1
            }
        }
    }

    private final class Harness {
        let window: NSWindow
        let bubble: BubbleView
        let host: WidgetHost
        let renderer: LiteHTMLWidgetRenderer
        var claims: [WidgetGestureClaim] = []

        init(_ test: GestureClaimTests, html: String = GestureClaimTests.html) throws {
            _ = NSApplication.shared
            window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 320, height: 400), styleMask: [.borderless], backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            window.orderFrontRegardless()
            bubble = BubbleView(frame: NSRect(x: 0, y: 0, width: 320, height: 400))
            window.contentView?.addSubview(bubble)
            var made: LiteHTMLWidgetRenderer?
            host = try WidgetHost(html: html, theme: WidgetTheme(colorScheme: .light), width: 320, rendererFactory: { _, theme, width in
                let renderer = LiteHTMLWidgetRenderer(theme: theme, width: width)
                made = renderer
                return renderer
            })
            renderer = try XCTUnwrap(made)
            host.view.frame = NSRect(x: 0, y: 0, width: 320, height: 400)
            bubble.addSubview(host.view)
            host.onGestureClaim = { [weak self] claim in
                self?.claims.append(claim)
            }
            let host = self.host
            let renderer = self.renderer
            test.pump(10, until: { host.diagnostics.loaded && host.diagnostics.rendered && renderer.dom.nodes.values.contains { $0.attribute("id") == "done" } && !renderer.isPreparingTiles })
            test.pump(0.3)
        }

        func close() {
            host.destroy()
            window.close()
        }

        func has(_ id: String) -> Bool {
            return renderer.dom.nodes.values.contains { $0.attribute("id") == id }
        }

        func center(_ id: String) throws -> CGPoint {
            let node = try XCTUnwrap(renderer.dom.nodes.values.first { $0.attribute("id") == id })
            let box = try XCTUnwrap(renderer.elementBounds()[renderer.dom.publicId(node)])
            return renderer.view.convert(CGPoint(x: box.midX, y: box.midY), to: nil)
        }

        func send(_ type: NSEvent.EventType, _ location: CGPoint, clicks: Int = 1, flags: NSEvent.ModifierFlags = []) {
            let pressed = type == .leftMouseDown || type == .rightMouseDown
            let event = NSEvent.mouseEvent(with: type, location: location, modifierFlags: flags, timestamp: ProcessInfo.processInfo.systemUptime, windowNumber: window.windowNumber, context: nil, eventNumber: 0, clickCount: clicks, pressure: pressed ? 1 : 0)!
            window.sendEvent(event)
        }

        func click(_ location: CGPoint, clicks: Int = 1) {
            send(.leftMouseDown, location, clicks: clicks)
            send(.leftMouseUp, location, clicks: clicks)
        }
    }

    private static let html = #"<style>body{margin:0}#none{touch-action:none;height:80px}#auto{height:80px}#pan{touch-action:pan-y;height:80px}</style><div id="none">none</div><div id="auto">auto</div><div id="pan">pan</div><script>function mark(id){var i=document.createElement('i');i.id=id;document.body.appendChild(i)}var n=document.getElementById('none'),a=document.getElementById('auto');n.addEventListener('dblclick',function(e){mark('dbl-none-'+e.detail)});a.addEventListener('dblclick',function(e){mark('dbl-auto-'+e.detail)});n.addEventListener('contextmenu',function(e){e.preventDefault();mark('ctx-none-'+e.button)});a.addEventListener('contextmenu',function(){mark('ctx-auto')});mark('done')</script>"#

    func testTheClaimCarriesHoldOnlyFromTouchActionNone() throws {
        let h = try Harness(self)
        defer { h.close() }
        for (id, expected) in [("none", WidgetGestureClaim(x: true, y: false, hold: true)), ("auto", WidgetGestureClaim(x: false, y: false, hold: false)), ("pan", WidgetGestureClaim(x: true, y: false, hold: false))] {
            let count = h.claims.count
            h.click(try h.center(id))
            pump(1, until: { h.claims.count > count })
            XCTAssertEqual(h.claims.last, expected, id)
            pump(0.4)
        }
    }

    func testRootTouchActionClaimsDescendantPressesAndContextMenus() throws {
        for (action, expected) in [("none", WidgetGestureClaim(x: true, y: false, hold: true)), ("pan-y", WidgetGestureClaim(x: true, y: false, hold: false)), ("pan-x", WidgetGestureClaim(x: false, y: false, hold: false))] {
            let h = try Harness(self, html: "<style>html{touch-action:\(action)}</style>" + Self.html)
            defer { h.close() }
            h.click(try h.center("auto"))
            pump(1, until: { !h.claims.isEmpty })
            XCTAssertEqual(h.claims.last, expected, action)
            h.send(.rightMouseDown, try h.center("auto"))
            pump(1, until: { h.has("ctx-auto") || h.bubble.menuClicks > 0 })
            XCTAssertEqual(h.has("ctx-auto"), expected.hold, action)
            XCTAssertEqual(h.bubble.menuClicks, expected.hold ? 0 : 1, action)
        }
    }

    func testADoubleClickSendsDblclickAfterBothClicksOnEveryElement() throws {
        let h = try Harness(self)
        defer { h.close() }
        for id in ["none", "auto"] {
            let point = try h.center(id)
            h.click(point, clicks: 1)
            h.click(point, clicks: 2)
            pump(2, until: { h.has("dbl-\(id)-2") })
            XCTAssertTrue(h.has("dbl-\(id)-2"), "\(id): \(h.renderer.dom.nodes.values.compactMap { $0.attribute("id") })")
            pump(0.4)
        }
    }

    func testTwoSlowClicksAreNoDoubleClick() throws {
        let h = try Harness(self)
        defer { h.close() }
        let point = try h.center("auto")
        h.click(point)
        pump(0.5)
        h.click(point)
        pump(0.6)
        XCTAssertFalse(h.renderer.dom.nodes.values.contains { ($0.attribute("id") ?? "").hasPrefix("dbl-auto") })
    }

    func testDoubleClickHasTwoClicksWhenNativeCountIsOne() throws {
        let h = try Harness(self)
        defer { h.close() }
        let point = try h.center("auto")
        h.click(point)
        h.click(CGPoint(x: point.x + 20, y: point.y))
        pump(2, until: { h.has("dbl-auto-2") })
        XCTAssertTrue(h.has("dbl-auto-2"))
    }

    func testARightClickOnNoneIsTheWidgetsContextMenu() throws {
        let h = try Harness(self)
        defer { h.close() }
        h.send(.rightMouseDown, try h.center("none"))
        pump(2, until: { h.has("ctx-none-2") })
        XCTAssertTrue(h.has("ctx-none-2"))
        pump(0.3)
        XCTAssertEqual(h.bubble.menuClicks, 0)
    }

    func testARightClickOnAutoIsTheMessageMenu() throws {
        let h = try Harness(self)
        defer { h.close() }
        h.send(.rightMouseDown, try h.center("auto"))
        pump(2, until: { h.bubble.menuClicks > 0 })
        XCTAssertEqual(h.bubble.menuClicks, 1)
        pump(0.3)
        XCTAssertFalse(h.has("ctx-auto"))
    }

    func testAControlClickIsARightClick() throws {
        let h = try Harness(self)
        defer { h.close() }
        h.send(.leftMouseDown, try h.center("auto"), flags: .control)
        pump(2, until: { h.bubble.controlClicks > 0 })
        XCTAssertEqual(h.bubble.controlClicks, 1)
        h.send(.leftMouseDown, try h.center("none"), flags: .control)
        pump(2, until: { h.has("ctx-none-2") })
        XCTAssertTrue(h.has("ctx-none-2"))
        XCTAssertEqual(h.bubble.controlClicks, 1)
    }
}

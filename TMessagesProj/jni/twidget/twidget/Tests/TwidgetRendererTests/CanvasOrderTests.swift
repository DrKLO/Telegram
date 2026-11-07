import XCTest
import AppKit
import WidgetSandboxProtocol
import WidgetSandbox
@testable import TwidgetRenderer

final class CanvasOrderTests: XCTestCase {
    private func pump(_ seconds: TimeInterval, until done: () -> Bool) {
        let deadline = Date().addingTimeInterval(seconds)
        while !done() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
    }

    private func send(_ renderer: LiteHTMLWidgetRenderer, _ ops: [[String: Any]]) {
        let data = try! JSONSerialization.data(withJSONObject: ["type": "ops", "ops": ops])
        renderer.send(String(data: data, encoding: .utf8)!)
    }

    func testDrawImageOfACanvasDrawnLaterInTheBatchDrawsIt() throws {
        _ = NSApplication.shared
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 300, height: 200), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.orderFrontRegardless()
        defer { window.orderOut(nil) }
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 300)
        renderer.view.frame = NSRect(x: 0, y: 0, width: 300, height: 200)
        window.contentView?.addSubview(renderer.view)
        renderer.load()
        send(renderer, [
            ["op": "el", "id": 1, "tag": "html", "attrs": [], "adopt": "html"],
            ["op": "el", "id": 3, "tag": "body", "attrs": [], "adopt": "body"],
            ["op": "style", "id": 3, "name": "margin", "value": "0"],
            ["op": "el", "id": 40, "tag": "canvas", "attrs": [["width", "40"], ["height", "40"]]],
            ["op": "ins", "id": 40, "parent": 3],
            ["op": "canvas", "id": 40, "cmds": [["c", "drawImage", ["n": 41], 0, 0, 40, 40]]],
            ["op": "el", "id": 41, "tag": "canvas", "attrs": [["width", "10"], ["height", "10"]]],
            ["op": "canvas", "id": 41, "cmds": [["s", "fillStyle", "#ff0000"], ["c", "fillRect", 0, 0, 10, 10]]],
        ])
        pump(5, until: { renderer.layoutCount > 0 && renderer.document != nil && !renderer.isPreparingTiles })
        let image = try XCTUnwrap(renderer.snapshot())
        let pixels = NSBitmapImageRep(cgImage: try XCTUnwrap(image.cgImage(forProposedRect: nil, context: nil, hints: nil)))
        let scale = CGFloat(pixels.pixelsWide) / image.size.width
        let color = try XCTUnwrap(pixels.colorAt(x: Int(20 * scale), y: Int(20 * scale))?.usingColorSpace(.sRGB))
        XCTAssertGreaterThan(color.alphaComponent, 0.9)
        XCTAssertGreaterThan(color.redComponent - max(color.greenComponent, color.blueComponent), 0.6)
    }

    func testDrawImageOfADetachedCanvasThroughTheSandbox() throws {
        _ = NSApplication.shared
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 300, height: 200), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.orderFrontRegardless()
        defer { window.close() }
        let html = #"<style>body{margin:0}</style><canvas id="dst" width="80" height="60"></canvas><script>var c=document.getElementById('dst').getContext('2d');var t=document.createElement('canvas');t.width=20;t.height=20;var tc=t.getContext('2d');tc.fillStyle='#00ff00';tc.fillRect(0,0,20,20);c.drawImage(t,10,10,40,40);var d=document.createElement('i');d.id='done';document.body.appendChild(d);</script>"#
        var made: LiteHTMLWidgetRenderer?
        let host = try WidgetHost(html: html, theme: WidgetTheme(colorScheme: .light), width: 300, rendererFactory: { _, theme, width in
            let renderer = LiteHTMLWidgetRenderer(theme: theme, width: width)
            made = renderer
            return renderer
        })
        defer { host.destroy() }
        let renderer = try XCTUnwrap(made)
        host.view.frame = NSRect(x: 0, y: 0, width: 300, height: 200)
        window.contentView?.addSubview(host.view)
        pump(10, until: { host.diagnostics.loaded && host.diagnostics.rendered && renderer.dom.nodes.values.contains { $0.attribute("id") == "done" } && !renderer.isPreparingTiles })
        pump(0.3, until: { false })
        let image = try XCTUnwrap(renderer.snapshot())
        let pixels = NSBitmapImageRep(cgImage: try XCTUnwrap(image.cgImage(forProposedRect: nil, context: nil, hints: nil)))
        let scale = CGFloat(pixels.pixelsWide) / image.size.width
        let color = try XCTUnwrap(pixels.colorAt(x: Int(30 * scale), y: Int(30 * scale))?.usingColorSpace(.sRGB))
        XCTAssertGreaterThan(color.alphaComponent, 0.9)
        XCTAssertGreaterThan(color.greenComponent - max(color.redComponent, color.blueComponent), 0.5)
    }

    func testOrderKeepsASourceDrawnEarlierInTheBatch() {
        let ops = [
            WidgetOp(["op": "el", "id": 41, "tag": "canvas", "attrs": []]),
            WidgetOp(["op": "canvas", "id": 41, "cmds": [["c", "fillRect", 0, 0, 1, 1]]]),
            WidgetOp(["op": "canvas", "id": 40, "cmds": [["c", "drawImage", ["n": 41], 0, 0]]]),
            WidgetOp(["op": "attr", "id": 41, "name": "width", "value": "2"]),
        ]
        XCTAssertEqual(WidgetCanvasOrder.ordered(ops).map(ids), [41, 41, 40, 41])
    }

    func testOrderFollowsTheSandboxFlushOrder() {
        let ops = [
            WidgetOp(["op": "el", "id": 74, "tag": "canvas", "attrs": [["width", "20"]]]),
            WidgetOp(["op": "canvas", "id": 55, "cmds": [["c", "scale", 2, 2], ["c", "drawImage", ["n": 74], 44, 26, 30, 30]]]),
            WidgetOp(["op": "canvas", "id": 74, "cmds": [["c", "fillRect", 0, 0, 20, 20]]]),
            WidgetOp(["op": "canvas", "id": 66, "cmds": [["c", "fillRect", 0, 0, 1, 1]]]),
        ]
        XCTAssertEqual(WidgetCanvasOrder.ordered(ops).map(ids), [74, 74, 66, 55])
        let single = [WidgetOp(["op": "canvas", "id": 55, "cmds": [["c", "drawImage", ["n": 74], 0, 0]]])]
        XCTAssertEqual(WidgetCanvasOrder.ordered(single).count, 1)
    }

    func testOrderMovesAForwardReferenceAndItsLaterOps() {
        let ops = [
            WidgetOp(["op": "canvas", "id": 40, "cmds": [["c", "drawImage", ["n": 41], 0, 0]]]),
            WidgetOp(["op": "el", "id": 41, "tag": "canvas", "attrs": []]),
            WidgetOp(["op": "attr", "id": 40, "name": "width", "value": "2"]),
            WidgetOp(["op": "canvas", "id": 41, "cmds": [["c", "fillRect", 0, 0, 1, 1]]]),
        ]
        let ordered = WidgetCanvasOrder.ordered(ops)
        XCTAssertEqual(ordered.map(ids), [41, 41, 40, 40])
        XCTAssertEqual(ordered.map { $0.op ?? "" }, ["el", "canvas", "canvas", "attr"])
    }

    private func ids(_ op: WidgetOp) -> Int {
        return (op["id"] as? NSNumber)?.intValue ?? op.id
    }
}

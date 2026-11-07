import AppKit
import XCTest
import LiteHTML
import WidgetSandbox
@testable import TwidgetRenderer

final class AnimationSettlementRegressionTests: XCTestCase {
    private final class VisibleWindow: NSWindow {
        override var isVisible: Bool { true }
        override var occlusionState: NSWindow.OcclusionState { .visible }
    }

    private func pump(_ seconds: TimeInterval) {
        let deadline = Date().addingTimeInterval(seconds)
        while Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.005))
        }
    }

    private func pixels(_ renderer: LiteHTMLWidgetRenderer) throws -> Data {
        let image = try XCTUnwrap(renderer.snapshot())
        let tiff = try XCTUnwrap(image.tiffRepresentation)
        let bitmap = try XCTUnwrap(NSBitmapImageRep(data: tiff))
        return Data(bytes: try XCTUnwrap(bitmap.bitmapData), count: bitmap.bytesPerRow * bitmap.pixelsHigh)
    }

    private func send(_ renderer: LiteHTMLWidgetRenderer, _ ops: [[String: Any]]) throws {
        let data = try JSONSerialization.data(withJSONObject: ["type": "ops", "ops": ops])
        renderer.send(String(decoding: data, as: UTF8.self))
    }

    func testImplicitScaleAnimationRestoresOriginalPixels() throws {
        _ = NSApplication.shared
        let window = VisibleWindow(contentRect: CGRect(x: 0, y: 0, width: 300, height: 120), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        defer { window.close() }
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 300)
        defer { renderer.destroy() }
        renderer.onMessage = { _ in }
        renderer.view.frame = CGRect(x: 0, y: 0, width: 300, height: 120)
        window.contentView?.addSubview(renderer.view)
        renderer.load()
        try send(renderer, [
            ["op": "el", "id": 1, "tag": "body", "adopt": "body", "attrs": [["style", "margin:0"]]],
            ["op": "el", "id": 2, "tag": "style", "attrs": []],
            ["op": "css", "id": 2, "text": ".box{width:20px;height:20px;background:#34c759;margin:12px}.pop{animation:bp .36s cubic-bezier(.34,1.56,.64,1)}@keyframes bp{0%{transform:scale(.3)}}"],
            ["op": "ins", "id": 2, "parent": 1],
            ["op": "el", "id": 3, "tag": "div", "attrs": [["class", "box"]]],
            ["op": "ins", "id": 3, "parent": 1],
        ])
        pump(0.15)
        let baseline = try pixels(renderer)
        for _ in 0..<4 {
            renderer.noteUserInput()
            try send(renderer, [["op": "attr", "id": 3, "name": "class", "value": "box pop"]])
            pump(0.6)
            let style = renderer.document?.computedStyle(attribute: WidgetDOM.idAttribute, value: "3")
            print("SETTLED SCALE style=\(style?["transform"] ?? "nil") promoted=\(renderer.compositedElementIDs) animated=\(renderer.animatedElementIDs)")
            XCTAssertEqual(try pixels(renderer), baseline)
            XCTAssertFalse(renderer.animatedElementIDs.contains(3))
            renderer.noteUserInput()
            try send(renderer, [["op": "attr", "id": 3, "name": "class", "value": "box"]])
            pump(0.05)
        }
    }

    func testReplacingAnimatedPanelChildrenRestoresOriginalPixels() throws {
        _ = NSApplication.shared
        let window = VisibleWindow(contentRect: CGRect(x: 0, y: 0, width: 300, height: 180), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        defer { window.close() }
        let html = "<style>body{margin:0}#panel{margin:12px;padding:8px} .box{width:80px;height:40px;background:#34c759}</style><button id='next'>Next</button><div id='panel'><div class='box'></div></div><script>const panel=document.getElementById('panel');document.getElementById('next').addEventListener('click',()=>{panel.animate([{opacity:.75,transform:'translateX(-12px)'},{opacity:1,transform:'none'}],{duration:160,easing:'cubic-bezier(.2,.8,.2,1)'});panel.innerHTML='<div class=box></div>'})</script>"
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 300)
        let host = try WidgetHost(html: html, theme: WidgetTheme(colorScheme: .light), width: 300, rendererFactory: { _, _, _ in renderer })
        defer { host.destroy() }
        host.view.frame = CGRect(x: 0, y: 0, width: 300, height: 180)
        window.contentView?.addSubview(host.view)
        pump(0.5)
        let baseline = try pixels(renderer)
        let before = try XCTUnwrap(renderer.document).bounds(matching: ".box")
        for _ in 0..<6 {
            XCTAssertTrue(renderer.debugClick(attributes: ["id": "next"]))
            pump(0.35)
            let after = try XCTUnwrap(renderer.document).bounds(matching: ".box")
            print("SETTLED PANEL before=\(before) after=\(after) promoted=\(renderer.compositedElementIDs)")
            XCTAssertEqual(after, before)
            XCTAssertEqual(try pixels(renderer), baseline)
        }
    }
    func testBackgroundExecutorSettlesCSSAndWebAnimationPixels() throws {
        _ = NSApplication.shared
        let window = VisibleWindow(contentRect: CGRect(x: 0, y: 0, width: 330, height: 180), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        defer { window.close() }
        let html = "<style>body{margin:0}#panel{margin:12px;padding:8px}.box{width:20px;height:20px;background:#34c759}.pop{animation:bp .36s cubic-bezier(.34,1.56,.64,1)}@keyframes bp{0%{transform:scale(.3)}}</style><button id='next'>Next</button><div id='panel'><div class='box' id='box'></div></div><script>const panel=document.getElementById('panel');document.getElementById('next').addEventListener('click',()=>{panel.animate([{opacity:.75,transform:'translateX(-12px)'},{opacity:1,transform:'none'}],{duration:160,easing:'cubic-bezier(.2,.8,.2,1)'});panel.innerHTML='<div class=box id=box></div>';document.getElementById('box').classList.add('pop')})</script>"
        let queue = WidgetExecutor.makeQueue("widget.pixel.settlement")
        let executor = WidgetExecutor(queue: queue)
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 330)
        var options = WidgetHost.Options()
        options.asyncValidation = true
        options.sharedValidatorThread = true
        options.startBeforeRendererReady = true
        options.features = ["binaryOps", "canvasWords", "webAnimations", "morphUpdates"]
        let host = try WidgetHost(html: html, theme: WidgetTheme(colorScheme: .light), width: 330, options: options, rendererFactory: { _, _, _ in renderer }, executor: executor)
        defer { host.destroy() }
        host.view.frame = CGRect(x: 0, y: 0, width: 330, height: 180)
        window.contentView?.addSubview(host.view)
        host.onChange = { [weak host] diagnostics in
            if diagnostics.height > 0 { host?.view.setFrameSize(CGSize(width: 330, height: diagnostics.height)) }
        }
        executor.async { renderer.preparesOffscreen = true }
        pump(0.7)
        let baseline = try queue.sync { try pixels(renderer) }
        for _ in 0..<6 {
            XCTAssertTrue(queue.sync { renderer.debugClick(attributes: ["id": "next"]) })
            pump(0.04)
            executor.async { renderer.setVisible(false); host.setVisible(false) }
            pump(0.12)
            executor.async { renderer.setVisible(true); host.setVisible(true) }
            pump(0.65)
            try queue.sync {
                print("BACKGROUND SETTLED \(renderer.tileDebug) ticks=\(renderer.ticks) frames=\(renderer.framesPosted) promoted=\(renderer.compositedElementIDs)")
                XCTAssertEqual(try pixels(renderer), baseline)
                XCTAssertTrue(renderer.animatedElementIDs.isEmpty)
                XCTAssertTrue(renderer.compositedElementIDs.isEmpty)
                XCTAssertEqual(renderer.staleTileCount, 0)
            }
        }
    }

    func testAncestorVisibilityRestartsTheRendererClock() throws {
        _ = NSApplication.shared
        let window = VisibleWindow(contentRect: CGRect(x: 0, y: 0, width: 330, height: 180), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        defer { window.close() }
        let parent = NSView(frame: CGRect(x: 0, y: 0, width: 330, height: 180))
        parent.isHidden = true
        window.contentView?.addSubview(parent)
        let queue = WidgetExecutor.makeQueue("widget.ancestor.visibility")
        let executor = WidgetExecutor(queue: queue)
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 330)
        let html = "<div id='value'>0</div><script>let n=0;function step(){document.getElementById('value').textContent=String(++n);requestAnimationFrame(step)}requestAnimationFrame(step)</script>"
        let host = try WidgetHost(html: html, theme: WidgetTheme(colorScheme: .light), width: 330, rendererFactory: { _, _, _ in renderer }, executor: executor)
        defer { host.destroy() }
        host.view.frame = parent.bounds
        parent.addSubview(host.view)
        pump(0.2)
        parent.isHidden = false
        executor.async { renderer.setVisible(true) }
        pump(0.3)
        queue.sync {
            print("ANCESTOR VISIBILITY \(renderer.tileDebug) ticks=\(renderer.ticks) frames=\(renderer.framesPosted)")
            XCTAssertTrue(renderer.isOnScreen)
            XCTAssertGreaterThan(renderer.framesPosted, 2)
        }
    }

    func testRecordedPackingOnBackgroundExecutor() throws {
        _ = NSApplication.shared
        let path = ProcessInfo.processInfo.environment["TW_ANIMATION_PACKING"] ?? URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("../../../../tools/perflab/widget-glitches/repaired/packing.html").path
        let html = try String(contentsOfFile: path)
        let window = VisibleWindow(contentRect: CGRect(x: 0, y: 0, width: 330, height: 700), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        defer { window.close() }
        let queue = WidgetExecutor.makeQueue("widget.recorded.packing")
        let executor = WidgetExecutor(queue: queue)
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 330)
        var options = WidgetHost.Options()
        options.asyncValidation = true
        options.sharedValidatorThread = true
        options.startBeforeRendererReady = true
        options.features = ["binaryOps", "canvasWords", "webAnimations", "morphUpdates"]
        let host = try WidgetHost(html: html, theme: WidgetTheme(colorScheme: .light), width: 330, options: options, rendererFactory: { _, _, _ in renderer }, executor: executor)
        defer { host.destroy() }
        host.view.frame = CGRect(x: 0, y: 0, width: 330, height: 700)
        window.contentView?.addSubview(host.view)
        host.onChange = { [weak host] diagnostics in
            if diagnostics.height > 0 { host?.view.setFrameSize(CGSize(width: 330, height: diagnostics.height)) }
        }
        executor.async { renderer.preparesOffscreen = true }
        pump(1.3)
        func realClick(_ selector: String) throws {
            let rect = try queue.sync { try XCTUnwrap(renderer.document).bounds(matching: selector) }
            XCTAssertFalse(rect.isEmpty)
            let down = try XCTUnwrap(NSEvent.mouseEvent(with: .leftMouseDown, location: .zero, modifierFlags: [], timestamp: ProcessInfo.processInfo.systemUptime, windowNumber: window.windowNumber, context: nil, eventNumber: 0, clickCount: 1, pressure: 1))
            let up = try XCTUnwrap(NSEvent.mouseEvent(with: .leftMouseUp, location: .zero, modifierFlags: [], timestamp: ProcessInfo.processInfo.systemUptime, windowNumber: window.windowNumber, context: nil, eventNumber: 1, clickCount: 1, pressure: 0))
            queue.sync {
                let point = CGPoint(x: rect.midX, y: rect.midY)
                renderer.handleMouseDown(point, event: down)
                renderer.handleMouseUp(point, event: up)
            }
        }
        try realClick(".item[data-id='3'] .box")
        pump(0.8)
        try queue.sync {
            let document = try XCTUnwrap(renderer.document)
            let box = document.bounds(matching: ".item[data-id='3'] .box")
            let reference = document.bounds(matching: ".item[data-id='1'] .box")
            print("RECORDED PACKING box=\(box) reference=\(reference) \(renderer.tileDebug) ticks=\(renderer.ticks) frames=\(renderer.framesPosted)")
            XCTAssertEqual(box.width, reference.width, accuracy: 0.5)
            XCTAssertEqual(box.height, reference.height, accuracy: 0.5)
            XCTAssertTrue(renderer.animatedElementIDs.isEmpty)
            XCTAssertEqual(renderer.staleTileCount, 0)
        }
        for category in ["docs", "all", "clothes", "all", "gear", "all"] {
            try realClick(".cat[data-cat='\(category)']")
            pump(0.35)
            try queue.sync {
                let document = try XCTUnwrap(renderer.document)
                let items = document.bounds(matching: "#items")
                let row = document.bounds(matching: ".item")
                print("RECORDED PACKING category=\(category) items=\(items) row=\(row) promoted=\(renderer.compositedElementIDs)")
                XCTAssertGreaterThanOrEqual(row.minX, items.minX - 0.5)
                XCTAssertLessThanOrEqual(row.maxX, items.maxX + 0.5)
                XCTAssertTrue(renderer.compositedElementIDs.isEmpty)
                XCTAssertEqual(renderer.staleTileCount, 0)
            }
        }
    }

}

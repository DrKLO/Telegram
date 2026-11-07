import XCTest
import AppKit
import LiteHTML
@testable import TwidgetRenderer

final class RendererLifetimeTests: XCTestCase {
    private final class Lifetime {
        weak var document: LHDocument?
        weak var canvas: WidgetCanvas?
        weak var scroll: WidgetScrollLayer?
        weak var node: WidgetNode?

        init(_ renderer: LiteHTMLWidgetRenderer) {
            document = renderer.document
            canvas = renderer.canvases[5]
            scroll = renderer.scrolling.layers[3]
            node = renderer.dom.node(6)
        }

        var released: Bool {
            document == nil && canvas == nil && scroll == nil && node == nil
        }
    }

    private func footprint() -> Double {
        var info = task_vm_info_data_t()
        var count = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<natural_t>.size)
        let result = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &count)
            }
        }
        return result == KERN_SUCCESS ? Double(info.phys_footprint) / 1048576 : 0
    }

    private func pump(_ seconds: TimeInterval = 0.3, until done: () -> Bool = { false }) {
        let deadline = Date().addingTimeInterval(seconds)
        while !done() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.005))
        }
    }

    private func populate(_ renderer: LiteHTMLWidgetRenderer) throws {
        renderer.onMessage = { _ in }
        renderer.load()
        let ops: [[String: Any]] = [
            ["op": "el", "id": 1, "tag": "body", "adopt": "body", "attrs": [["style", "margin:0"]]],
            ["op": "el", "id": 3, "tag": "div", "attrs": [["style", "width:240px;height:100px;overflow:auto"]]],
            ["op": "ins", "id": 3, "parent": 1],
            ["op": "el", "id": 4, "tag": "div", "attrs": [["style", "height:900px;background:linear-gradient(red,blue)"]]],
            ["op": "ins", "id": 4, "parent": 3],
            ["op": "el", "id": 5, "tag": "canvas", "attrs": [["width", "1024"], ["height", "1024"], ["style", "width:240px;height:160px"]]],
            ["op": "ins", "id": 5, "parent": 1],
            ["op": "canvas", "id": 5, "cmds": [["s", "fillStyle", "#123456"], ["c", "fillRect", 0, 0, 1024, 1024]]],
            ["op": "text", "id": 6, "data": String(repeating: "retained text ", count: 1000)],
            ["op": "ins", "id": 6, "parent": 4]
        ]
        renderer.send(String(data: try JSONSerialization.data(withJSONObject: ["type": "ops", "ops": ops]), encoding: .utf8)!)
    }

    func testDestroyReleasesResourcesWhileHostRetainsRenderer() throws {
        _ = NSApplication.shared
        let registered = WidgetCanvasImages.registeredCount
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 240)
        renderer.view.frame = CGRect(x: 0, y: 0, width: 240, height: 300)
        try populate(renderer)
        pump(3, until: { renderer.scrolling.layers[3] != nil && renderer.canvases[5] != nil })
        weak var document = renderer.document
        weak var canvas = renderer.canvases[5]
        weak var scroll = renderer.scrolling.layers[3]
        weak var text = renderer.dom.node(6)
        XCTAssertNotNil(document)
        XCTAssertNotNil(canvas)
        XCTAssertNotNil(scroll)
        XCTAssertNotNil(text)
        autoreleasepool { renderer.destroy() }
        renderer.destroy()
        pump(3, until: { document == nil && canvas == nil && scroll == nil && text == nil })
        XCTAssertNil(document)
        XCTAssertNil(canvas)
        XCTAssertNil(scroll)
        XCTAssertNil(text)
        XCTAssertEqual(renderer.tileLayerCount, 0)
        XCTAssertTrue(renderer.canvases.isEmpty)
        XCTAssertTrue(renderer.scrolling.layers.isEmpty)
        XCTAssertEqual(WidgetCanvasImages.registeredCount, registered)
        withExtendedLifetime(renderer) {}
    }

    func testRetireReleasesPresentedTileStorageWhileViewIsRetained() {
        _ = NSApplication.shared
        let view = WidgetDocumentView(frame: CGRect(x: 0, y: 0, width: 400, height: 600))
        autoreleasepool {
            let document = LHDocument(html: "<body style='margin:0;height:600px;background:linear-gradient(red,blue)'></body>", css: nil)
            _ = document.layout(maxWidth: 400, preferredWidth: 400)
            view.document = document
        }
        weak var document = view.document
        var completed = false
        XCTAssertTrue(view.prepareTiles { completed = true })
        pump(3, until: { completed })
        XCTAssertTrue(completed)
        XCTAssertGreaterThan(view.tileCount, 0)
        autoreleasepool { WidgetTeardown.release(view.retire()) }
        pump(3, until: { document == nil })
        XCTAssertNil(document)
        XCTAssertEqual(view.tileCount, 0)
        XCTAssertTrue(view.contentLayer?.sublayers?.isEmpty != false)
        withExtendedLifetime(view) {}
    }

    func testDroppingRendererReleasesDocumentCanvasAndView() throws {
        _ = NSApplication.shared
        let registered = WidgetCanvasImages.registeredCount
        weak var rendererRef: LiteHTMLWidgetRenderer?
        weak var view: NSView?
        weak var document: LHDocument?
        weak var canvas: WidgetCanvas?
        try autoreleasepool {
            let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 240)
            renderer.view.frame = CGRect(x: 0, y: 0, width: 240, height: 300)
            try populate(renderer)
            pump(3, until: { renderer.scrolling.layers[3] != nil && renderer.canvases[5] != nil })
            rendererRef = renderer
            view = renderer.view
            document = renderer.document
            canvas = renderer.canvases[5]
            XCTAssertNotNil(document)
            XCTAssertNotNil(canvas)
        }
        pump(3, until: { rendererRef == nil && view == nil && document == nil && canvas == nil })
        XCTAssertNil(rendererRef)
        XCTAssertNil(view)
        XCTAssertNil(document)
        XCTAssertNil(canvas)
        XCTAssertEqual(WidgetCanvasImages.registeredCount, registered)
    }

    func testDestroyReturnsSpareTileBudget() {
        checkSpareBudget(destroy: true)
    }

    func testRepeatedCreateDestroyWithRetainedHosts() throws {
        _ = NSApplication.shared
        let count = max(1, Int(ProcessInfo.processInfo.environment["TW_LIFETIME_CYCLES"] ?? "") ?? 40)
        var hosts: [LiteHTMLWidgetRenderer] = []
        var lifetimes: [Lifetime] = []
        for index in 1...count {
            try autoreleasepool {
                let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 240)
                renderer.view.frame = CGRect(x: 0, y: 0, width: 240, height: 300)
                try populate(renderer)
                pump(3, until: { renderer.scrolling.layers[3] != nil && renderer.canvases[5] != nil })
                let lifetime = Lifetime(renderer)
                XCTAssertNotNil(lifetime.document)
                XCTAssertNotNil(lifetime.canvas)
                XCTAssertNotNil(lifetime.scroll)
                lifetimes.append(lifetime)
                renderer.destroy()
                hosts.append(renderer)
            }
            if index % 20 == 0 || index == count {
                pump(3, until: { lifetimes.allSatisfy(\.released) })
                XCTAssertTrue(lifetimes.allSatisfy(\.released), "cycle \(index)")
                print(String(format: "renderer-lifetime cycles=%d footprint=%.2f MiB retained-hosts=%d", index, footprint(), hosts.count))
            }
        }
        withExtendedLifetime(hosts) {}
    }

    func testDestroyOnBackgroundExecutorReleasesResources() throws {
        _ = NSApplication.shared
        let queue = DispatchQueue(label: "twidget.test.lifetime.owner")
        let key = DispatchSpecificKey<Bool>()
        queue.setSpecific(key: key, value: true)
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 240)
        renderer.view.frame = CGRect(x: 0, y: 0, width: 240, height: 300)
        renderer.executorIsMain = { false }
        renderer.isOnExecutor = { DispatchQueue.getSpecific(key: key) == true }
        renderer.schedule = { queue.async(execute: $0) }
        try queue.sync { try populate(renderer) }
        pump(3, until: { queue.sync { renderer.canvases[5] != nil } })
        let lifetime = queue.sync { () -> Lifetime in
            let lifetime = Lifetime(renderer)
            XCTAssertNotNil(lifetime.document)
            XCTAssertNotNil(lifetime.canvas)
            renderer.destroy()
            renderer.load()
            return lifetime
        }
        pump(3, until: { lifetime.released })
        XCTAssertTrue(lifetime.released)
        queue.sync {
            XCTAssertNil(renderer.document)
            XCTAssertEqual(renderer.tileLayerCount, 0)
        }
        withExtendedLifetime(renderer) {}
    }

    func testDroppingViewReturnsSpareTileBudget() {
        checkSpareBudget(destroy: false)
    }

    func testDiscardedRasterCompletionReturnsBorrowedSpareBudget() {
        checkSpareBudget(destroy: false, discardCompletion: true)
    }

    private func checkSpareBudget(destroy: Bool, discardCompletion: Bool = false) {
        _ = NSApplication.shared
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 400, height: 280), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.orderFrontRegardless()
        defer { window.orderOut(nil) }
        let initial = WidgetTileSpares.currentBytes
        let gate = DispatchSemaphore(value: 0)
        autoreleasepool {
            let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 400)
            renderer.executorIsMain = { false }
            renderer.scheduleAfter = { _, _ in }
            renderer.onMessage = { _ in }
            renderer.view.frame = CGRect(x: 0, y: 0, width: 400, height: 280)
            window.contentView?.addSubview(renderer.view)
            renderer.load()
            renderer.send("{\"type\":\"ops\",\"ops\":[{\"op\":\"el\",\"id\":7,\"tag\":\"body\",\"attrs\":[[\"style\",\"margin:0;height:270px\"]],\"adopt\":\"body\"},{\"op\":\"el\",\"id\":9,\"tag\":\"div\",\"attrs\":[[\"style\",\"height:130px;width:100px;margin:20px;background:#d33\"]]},{\"op\":\"ins\",\"parent\":7,\"id\":9}]}")
            pump(3, until: { renderer.tileLayerCount > 0 && !renderer.tilesPreparing })
            for frame in 0..<8 {
                renderer.send("{\"type\":\"ops\",\"ops\":[{\"op\":\"attr\",\"id\":9,\"name\":\"style\",\"value\":\"height:130px;width:\(101 + frame)px;margin:20px;background:#d33\"}]}")
                pump(0.03)
                pump(3, until: { !renderer.tilesPreparing })
            }
            XCTAssertGreaterThan(WidgetTileSpares.currentBytes, initial)
            if discardCompletion {
                renderer.scheduleUrgent = { _ in }
                WidgetDocumentView.prepareQueue.async(flags: .barrier) { gate.wait() }
                renderer.documentView.invalidate([CGRect(x: 20, y: 20, width: 120, height: 130)])
                renderer.documentView.flushIfDetached()
                pump(3, until: { renderer.tilesPreparing })
                XCTAssertTrue(renderer.tilesPreparing)
            }
            if destroy {
                renderer.destroy()
                pump(0.2)
                XCTAssertEqual(WidgetTileSpares.currentBytes, initial)
            }
            renderer.view.removeFromSuperview()
        }
        gate.signal()
        WidgetDocumentView.prepareQueue.sync(flags: .barrier) {}
        pump(0.2)
        XCTAssertEqual(WidgetTileSpares.currentBytes, initial)
    }
}

import XCTest
import AppKit
import LiteHTML
@testable import TwidgetRenderer

final class HiddenTileTests: XCTestCase {
    private final class CountingDocument: LHDocument {
        private let drawLock = NSLock()
        private var draws = 0
        var beforeDraw: ((Int) -> Void)?

        var rasterCount: Int {
            drawLock.lock()
            defer { drawLock.unlock() }
            return draws
        }

        override func drawIfCurrent(in context: CGContext, origin: CGPoint, clip: CGRect) -> Bool {
            drawLock.lock()
            draws += 1
            let count = draws
            drawLock.unlock()
            beforeDraw?(count)
            return super.drawIfCurrent(in: context, origin: origin, clip: clip)
        }
    }

    private func pump(_ seconds: TimeInterval, until done: () -> Bool = { false }) {
        let deadline = Date().addingTimeInterval(seconds)
        while !done() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
    }

    func testRetiredViewDoesNotRasterizeQueuedTiles() {
        _ = NSApplication.shared
        let document = CountingDocument(html: "<body style='margin:0;height:600px;background:red'></body>", css: nil)
        _ = document.layout(maxWidth: 400, preferredWidth: 400)
        let view = WidgetDocumentView(frame: NSRect(x: 0, y: 0, width: 400, height: 600))
        view.document = document
        let gate = DispatchSemaphore(value: 0)
        WidgetDocumentView.prepareQueue.async(flags: .barrier) { gate.wait() }
        var completed = false
        XCTAssertTrue(view.prepareTiles { completed = true })
        _ = view.retire()
        gate.signal()
        pump(2, until: { completed })
        XCTAssertTrue(completed)
        XCTAssertEqual(document.rasterCount, 0)
        XCTAssertEqual(view.preparedTiles, 0)
    }

    func testLiveViewStillRasterizesQueuedTiles() {
        _ = NSApplication.shared
        let document = CountingDocument(html: "<body style='margin:0;height:600px;background:red'></body>", css: nil)
        _ = document.layout(maxWidth: 400, preferredWidth: 400)
        let view = WidgetDocumentView(frame: NSRect(x: 0, y: 0, width: 400, height: 600))
        view.document = document
        let gate = DispatchSemaphore(value: 0)
        WidgetDocumentView.prepareQueue.async(flags: .barrier) { gate.wait() }
        var completed = false
        XCTAssertTrue(view.prepareTiles { completed = true })
        gate.signal()
        pump(2, until: { completed })
        XCTAssertTrue(completed)
        XCTAssertGreaterThan(document.rasterCount, 0)
        XCTAssertEqual(view.preparedTiles, document.rasterCount)
        _ = view.retire()
    }

    func testRetiringDuringRasterStopsTheRemainingTiles() {
        _ = NSApplication.shared
        let document = CountingDocument(html: "<body style='margin:0;height:600px;background:red'></body>", css: nil)
        _ = document.layout(maxWidth: 400, preferredWidth: 400)
        let started = DispatchSemaphore(value: 0)
        let resume = DispatchSemaphore(value: 0)
        document.beforeDraw = { count in
            if count == 1 {
                started.signal()
                resume.wait()
            }
        }
        let view = WidgetDocumentView(frame: NSRect(x: 0, y: 0, width: 400, height: 600))
        view.document = document
        var completed = false
        XCTAssertTrue(view.prepareTiles { completed = true })
        XCTAssertEqual(started.wait(timeout: .now() + 2), .success)
        _ = view.retire()
        resume.signal()
        pump(2, until: { completed })
        XCTAssertTrue(completed)
        XCTAssertEqual(document.rasterCount, 1)
        XCTAssertEqual(view.preparedTiles, 0)
    }

    func testHiddenRendererDropsItsTilesAndRedrawsThemWhenShown() {
        _ = NSApplication.shared
        let delay = LiteHTMLWidgetRenderer.hiddenTrimDelay
        LiteHTMLWidgetRenderer.hiddenTrimDelay = 0.05
        defer { LiteHTMLWidgetRenderer.hiddenTrimDelay = delay }
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 400, height: 500), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.orderFrontRegardless()
        defer { window.orderOut(nil) }
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 400)
        renderer.onMessage = { _ in }
        renderer.view.frame = NSRect(x: 0, y: 0, width: 400, height: 400)
        window.contentView?.addSubview(renderer.view)
        renderer.load()
        func text(_ value: String) -> String {
            return "{\"type\":\"ops\",\"ops\":[{\"op\":\"text\",\"id\":20,\"data\":\"\(value)\"}]}"
        }
        renderer.send("{\"type\":\"ops\",\"ops\":[{\"op\":\"el\",\"id\":2,\"tag\":\"html\",\"attrs\":[],\"adopt\":\"html\"},{\"op\":\"el\",\"id\":7,\"tag\":\"body\",\"attrs\":[],\"adopt\":\"body\"},{\"op\":\"el\",\"id\":9,\"tag\":\"div\",\"attrs\":[[\"style\",\"height:360px;background:#3a7\"]]},{\"op\":\"ins\",\"parent\":7,\"id\":9,\"before\":null},{\"op\":\"text\",\"id\":20,\"data\":\"one\"},{\"op\":\"ins\",\"parent\":9,\"id\":20,\"before\":null}]}")
        pump(3, until: { renderer.tileLayerCount > 0 && renderer.drawCount > 0 })
        let shownTiles = renderer.tileLayerCount
        XCTAssertGreaterThan(shownTiles, 0)
        renderer.setVisible(false)
        pump(2, until: { renderer.tileLayerCount == 0 })
        XCTAssertEqual(renderer.tileLayerCount, 0)
        let drawsWhileHidden = renderer.drawCount
        renderer.send(text("two"))
        pump(0.3)
        XCTAssertEqual(renderer.tileLayerCount, 0)
        XCTAssertEqual(renderer.drawCount, drawsWhileHidden)
        renderer.setVisible(true)
        pump(3, until: { renderer.tileLayerCount == shownTiles && renderer.drawCount > drawsWhileHidden })
        XCTAssertEqual(renderer.tileLayerCount, shownTiles)
        XCTAssertGreaterThan(renderer.drawCount, drawsWhileHidden)
        renderer.destroy()
    }

    func testPausedRendererKeepsItsTiles() {
        _ = NSApplication.shared
        let delay = LiteHTMLWidgetRenderer.hiddenTrimDelay
        LiteHTMLWidgetRenderer.hiddenTrimDelay = 0.05
        defer { LiteHTMLWidgetRenderer.hiddenTrimDelay = delay }
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 400, height: 500), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.orderFrontRegardless()
        defer { window.orderOut(nil) }
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 400)
        renderer.onMessage = { _ in }
        renderer.view.frame = NSRect(x: 0, y: 0, width: 400, height: 400)
        window.contentView?.addSubview(renderer.view)
        renderer.load()
        renderer.send("{\"type\":\"ops\",\"ops\":[{\"op\":\"el\",\"id\":2,\"tag\":\"html\",\"attrs\":[],\"adopt\":\"html\"},{\"op\":\"el\",\"id\":7,\"tag\":\"body\",\"attrs\":[],\"adopt\":\"body\"},{\"op\":\"el\",\"id\":9,\"tag\":\"div\",\"attrs\":[[\"style\",\"height:360px;background:#3a7\"]]},{\"op\":\"ins\",\"parent\":7,\"id\":9,\"before\":null}]}")
        pump(3, until: { renderer.tileLayerCount > 0 && renderer.drawCount > 0 })
        let shownTiles = renderer.tileLayerCount
        XCTAssertGreaterThan(shownTiles, 0)
        renderer.keepsTilesWhileHidden = true
        renderer.setVisible(false)
        pump(0.4)
        XCTAssertEqual(renderer.tileLayerCount, shownTiles)
        renderer.keepsTilesWhileHidden = false
        pump(2, until: { renderer.tileLayerCount == 0 })
        XCTAssertEqual(renderer.tileLayerCount, 0)
        renderer.destroy()
    }
}

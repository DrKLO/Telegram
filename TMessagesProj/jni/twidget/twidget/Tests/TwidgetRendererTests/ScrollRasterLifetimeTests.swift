import XCTest
import AppKit
import LiteHTML
@testable import TwidgetRenderer

final class ScrollRasterLifetimeTests: XCTestCase {
    private let geometry = WidgetScrollGeometry(viewport: CGRect(x: 0, y: 0, width: 120, height: 100), content: CGSize(width: 120, height: 700), radii: [])

    private func document() -> LHDocument {
        let document = LHDocument(html: "<body style='margin:0'><div id='3' style='width:120px;height:100px;overflow:auto'><div id='4' style='height:700px;background:red'></div></div><div id='5' style='height:20px;background:red'></div></body>", css: nil, previous: nil)
        _ = document.layout(maxWidth: 120, preferredWidth: 120)
        return document
    }

    private func pump(until done: () -> Bool) {
        let deadline = Date().addingTimeInterval(2)
        while !done() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.002))
        }
        XCTAssertTrue(done())
    }

    private func current(_ document: LHDocument) throws -> Bool {
        let space = try XCTUnwrap(CGColorSpace(name: CGColorSpace.sRGB))
        let context = try XCTUnwrap(CGContext(data: nil, width: 120, height: 100, bitsPerComponent: 8, bytesPerRow: 480, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        return document.drawIfCurrent(in: context, origin: .zero, clip: geometry.viewport)
    }

    private func pixels(_ tile: CALayer) throws -> Data {
        let image = try XCTUnwrap(WidgetSurfaceImage.image(of: tile)) as! CGImage
        return try XCTUnwrap(image.dataProvider?.data) as Data
    }

    private func compare(_ layer: WidgetScrollLayer, to document: LHDocument) throws {
        for tile in layer.content.sublayers ?? [] {
            let scale = tile.contentsScale
            let expected = try XCTUnwrap(document.copyScrollContent(attribute: "id", value: "3", rect: tile.frame.insetBy(dx: -1 / scale, dy: -1 / scale), scale: scale))
            XCTAssertEqual(try pixels(tile), expected.dataProvider?.data as Data?)
            XCTAssertNil(tile.animationKeys())
        }
    }

    func testCurrentRasterRejectsCancelledAndConsumedDocumentsWithoutRestoring() throws {
        let before = document()
        XCTAssertNil(before.copyScrollContentIfCurrent(attribute: "id", value: "3", rect: geometry.viewport, scale: 1, shouldContinue: { false }))
        XCTAssertTrue(try current(before))
        let after = try XCTUnwrap(LHDocument(previous: before, styles: ["4": "height:700px;background:blue"], attribute: "id", css: nil))
        XCTAssertTrue(after.reusedPrevious)
        var called = false
        XCTAssertNil(before.copyScrollContentIfCurrent(attribute: "id", value: "3", rect: geometry.viewport, scale: 1, shouldContinue: { called = true; return true }))
        XCTAssertFalse(called)
        XCTAssertFalse(try current(before))
        _ = after.layout(maxWidth: 120, preferredWidth: 120)
        XCTAssertNotNil(after.copyScrollContentIfCurrent(attribute: "id", value: "3", rect: geometry.viewport, scale: 1, shouldContinue: { true }))
        XCTAssertNotNil(before.copyScrollContent(attribute: "id", value: "3", rect: geometry.viewport, scale: 1))
        XCTAssertTrue(try current(before))
    }

    func testCancellationIsCheckedAfterWaitingForTheDocumentLock() throws {
        let document = document()
        let locked = DispatchSemaphore(value: 0)
        let release = DispatchSemaphore(value: 0)
        let lifetime = WidgetRasterLifetime()
        let occupied = expectation(description: "lock owner finished")
        let waiting = DispatchSemaphore(value: 0)
        let cancelled = expectation(description: "waiting raster cancelled")
        DispatchQueue.global().async {
            _ = document.copyScrollContentIfCurrent(attribute: "id", value: "3", rect: self.geometry.viewport, scale: 1, shouldContinue: {
                locked.signal()
                release.wait()
                return false
            })
            occupied.fulfill()
        }
        XCTAssertEqual(locked.wait(timeout: .now() + 2), .success)
        DispatchQueue.global().async {
            XCTAssertTrue(lifetime.isActive)
            waiting.signal()
            let image = document.copyScrollContentIfCurrent(attribute: "id", value: "3", rect: self.geometry.viewport, scale: 1, shouldContinue: { lifetime.isActive })
            XCTAssertNil(image)
            cancelled.fulfill()
        }
        XCTAssertEqual(waiting.wait(timeout: .now() + 2), .success)
        lifetime.cancel()
        release.signal()
        wait(for: [occupied, cancelled], timeout: 2)
        XCTAssertNotNil(document.copyScrollContentIfCurrent(attribute: "id", value: "3", rect: geometry.viewport, scale: 1, shouldContinue: { true }))
    }

    func testConsumedQueuedRasterUsesLatestSourceAndReusesUnchangedTiles() throws {
        for changedInside in [false, true] {
            let before = document()
            let layer = WidgetScrollLayer(id: 3, geometry: geometry)
            let gate = DispatchSemaphore(value: 0)
            WidgetScrollLayer.queue.async { gate.wait() }
            defer { gate.signal() }
            layer.update(signature: 1)
            layer.ensureTiles(document: before, scale: 1, attribute: "id")
            let firstFill = layer.rasterCount
            let first = try XCTUnwrap(layer.content.sublayers?.first { $0.frame.minY == 0 })
            let initialImage = WidgetSurfaceImage.image(of: first)
            let after = try XCTUnwrap(LHDocument(previous: before, styles: changedInside ? ["4": "height:700px;background:blue"] : ["5": "height:20px;background:blue"], attribute: "id", css: nil))
            _ = after.layout(maxWidth: 120, preferredWidth: 120)
            if changedInside { layer.update(signature: 2) }
            layer.refresh(document: after, scale: 1, attribute: "id")
            gate.signal()
            let expected = layer.tileCount + (changedInside ? firstFill : 0)
            pump { layer.rasterCount == expected }
            XCTAssertFalse(try current(before))
            XCTAssertTrue(layer.content.sublayers?.contains { $0 === first } == true)
            if !changedInside {
                XCTAssertTrue(WidgetSurfaceImage.image(of: first) as AnyObject? === initialImage as AnyObject?)
            }
            try compare(layer, to: after)
        }
    }

    func testRejectedRasterKeepsPresentedPixelsUntilSourceRefresh() throws {
        let before = document()
        let layer = WidgetScrollLayer(id: 3, geometry: geometry)
        layer.update(signature: 1)
        layer.ensureTiles(document: before, scale: 1, attribute: "id")
        pump { layer.rasterCount == layer.tileCount }
        let tiles = layer.content.sublayers ?? []
        let originals = try tiles.map { try pixels($0) }
        let firstCount = layer.rasterCount
        let gate = DispatchSemaphore(value: 0)
        WidgetScrollLayer.queue.async { gate.wait() }
        defer { gate.signal() }
        layer.update(signature: 2)
        layer.ensureTiles(document: before, scale: 1, attribute: "id")
        let after = try XCTUnwrap(LHDocument(previous: before, styles: ["4": "height:700px;background:blue"], attribute: "id", css: nil))
        _ = after.layout(maxWidth: 120, preferredWidth: 120)
        let drained = expectation(description: "obsolete jobs completed")
        WidgetScrollLayer.queue.async { DispatchQueue.main.async { drained.fulfill() } }
        gate.signal()
        wait(for: [drained], timeout: 2)
        XCTAssertEqual(layer.rasterCount, firstCount)
        XCTAssertFalse(try current(before))
        XCTAssertEqual(try tiles.map { try pixels($0) }, originals)
        layer.refresh(document: after, scale: 1, attribute: "id")
        pump { layer.rasterCount == firstCount + layer.tileCount }
        try compare(layer, to: after)
    }

    func testInitialPromotionCompletesTilesAfterTheFinalLayoutWithoutAnotherFrame() throws {
        _ = NSApplication.shared
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 120)
        renderer.view.frame = geometry.viewport
        defer { renderer.destroy() }
        var scheduled: [() -> Void] = []
        renderer.schedule = { work in
            if Thread.isMainThread {
                scheduled.append(work)
            } else {
                DispatchQueue.main.async(execute: work)
            }
        }
        renderer.load()
        let ops: [[String: Any]] = [
            ["op": "el", "id": 1, "tag": "body", "attrs": [["style", "margin:0"]], "adopt": "body"],
            ["op": "el", "id": 3, "tag": "div", "attrs": [["style", "width:120px;height:100px;overflow:auto"]]],
            ["op": "ins", "id": 3, "parent": 1],
            ["op": "el", "id": 4, "tag": "div", "attrs": [["style", "height:700px;background:blue"]]],
            ["op": "ins", "id": 4, "parent": 3],
        ]
        renderer.send(String(data: try JSONSerialization.data(withJSONObject: ["type": "ops", "ops": ops]), encoding: .utf8)!)
        while !scheduled.isEmpty { scheduled.removeFirst()() }
        let count = renderer.layoutCount
        let layer = try XCTUnwrap(renderer.scrolling.layers[3])
        pump { layer.rasterCount == layer.tileCount }
        XCTAssertEqual(renderer.layoutCount, count)
        XCTAssertTrue(scheduled.isEmpty)
        let document = try XCTUnwrap(renderer.document)
        for tile in layer.content.sublayers ?? [] {
            let scale = tile.contentsScale
            let expected = try XCTUnwrap(document.copyScrollContent(attribute: WidgetDOM.idAttribute, value: "3", rect: tile.frame.insetBy(dx: -1 / scale, dy: -1 / scale), scale: scale))
            XCTAssertEqual(try pixels(tile), expected.dataProvider?.data as Data?)
        }
    }
}

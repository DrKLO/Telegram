import XCTest
import AppKit
import LiteHTML
@testable import TwidgetRenderer

final class ScrollRasterExecutorTests: XCTestCase {
    private final class Owner {
        private let queue = DispatchQueue(label: "twidget.test.scroll.owner")
        private let key = DispatchSpecificKey<Bool>()
        private let lock = NSLock()
        private var held: [() -> Void] = []
        private(set) var completed = 0
        var layer: WidgetScrollLayer?
        var document: LHDocument?

        init() {
            queue.setSpecific(key: key, value: true)
        }

        private func execute<T>(_ body: @escaping () -> T) -> T {
            let finished = DispatchSemaphore(value: 0)
            var result: T?
            queue.async {
                XCTAssertEqual(DispatchQueue.getSpecific(key: self.key), true)
                XCTAssertFalse(Thread.isMainThread)
                CATransaction.begin()
                CATransaction.setDisableActions(true)
                result = body()
                CATransaction.commit()
                finished.signal()
            }
            finished.wait()
            return result!
        }

        func sync<T>(_ body: () -> T) -> T {
            withoutActuallyEscaping(body) { block in execute(block) }
        }

        func sync<T>(_ body: () throws -> T) throws -> T {
            let result: Result<T, Error> = withoutActuallyEscaping(body) { block in
                execute { Result { try block() } }
            }
            return try result.get()
        }

        var isCurrent: Bool {
            DispatchQueue.getSpecific(key: key) == true
        }

        func hold(_ block: @escaping () -> Void) {
            lock.lock()
            held.append(block)
            lock.unlock()
        }

        func makeLayer(_ geometry: WidgetScrollGeometry) {
            XCTAssertEqual(DispatchQueue.getSpecific(key: key), true)
            layer = WidgetScrollLayer(id: 3, geometry: geometry, schedule: { [weak self] block in
                self?.hold(block)
            })
        }

        var heldCount: Int {
            lock.lock()
            defer { lock.unlock() }
            return held.count
        }

        private func takeCompletions() -> [() -> Void] {
            lock.lock()
            defer { lock.unlock() }
            let result = held
            held.removeAll()
            return result
        }

        func runHeldCompletions() {
            let blocks = takeCompletions()
            sync {
                for block in blocks {
                    XCTAssertEqual(DispatchQueue.getSpecific(key: key), true)
                    block()
                    completed += 1
                }
            }
        }

        func transferHeldCompletions(to owner: Owner) {
            for block in takeCompletions() { owner.hold(block) }
        }

        func drain() {
            for _ in 0..<16 {
                WidgetScrollLayer.queue.sync {}
                if heldCount == 0 { return }
                runHeldCompletions()
            }
            XCTFail("Scroll raster retries did not settle")
        }

        func destroy() {
            sync {
                layer = nil
                document = nil
            }
            drain()
        }
    }

    private var geometry: WidgetScrollGeometry {
        WidgetScrollGeometry(viewport: CGRect(x: 0, y: 0, width: 120, height: 100), content: CGSize(width: 120, height: 700), radii: [])
    }

    private func document() -> LHDocument {
        let document = LHDocument(html: "<body style='margin:0'><div id='3' style='width:120px;height:100px;overflow:auto'><div id='4' style='height:700px;background:red'>Queued scroll pixels</div></div><div id='5' style='height:20px;background:red'></div></body>", css: nil, previous: nil)
        _ = document.layout(maxWidth: 120, preferredWidth: 120)
        return document
    }

    private func pixels(_ tile: CALayer) throws -> Data {
        let contents = try XCTUnwrap(WidgetSurfaceImage.image(of: tile))
        let image = contents as! CGImage
        return try XCTUnwrap(image.dataProvider?.data) as Data
    }

    private func verifyLatestPixels(_ owner: Owner, visibleOnly: Bool = false) throws {
        try owner.sync {
            let layer = try XCTUnwrap(owner.layer)
            let document = try XCTUnwrap(owner.document)
            let tiles = layer.content.sublayers ?? []
            XCTAssertGreaterThan(tiles.count, 0)
            XCTAssertEqual(tiles.count, layer.tileCount)
            let visible = CGRect(origin: layer.geometry.clamp(layer.offset), size: layer.geometry.viewport.size)
            var coverage: CGFloat = 0
            for tile in tiles where !visibleOnly || tile.frame.intersects(visible) {
                let covered = tile.frame.intersection(visible)
                if !covered.isNull { coverage += covered.width * covered.height }
                let scale = tile.contentsScale
                let expected = try XCTUnwrap(document.copyScrollContent(attribute: "id", value: "3", rect: tile.frame.insetBy(dx: -1 / scale, dy: -1 / scale), scale: scale))
                XCTAssertEqual(try pixels(tile), expected.dataProvider?.data as Data?, "Tile \(tile.frame)")
                XCTAssertNil(tile.animationKeys())
            }
            XCTAssertEqual(coverage, visible.width * visible.height, accuracy: 0.0001)
        }
    }

    func testAsyncRasterWaitsForItsBackgroundOwnerScheduler() throws {
        let owner = Owner()
        defer { owner.destroy() }
        let initial = owner.sync { () -> Int in
            owner.document = document()
            owner.makeLayer(geometry)
            owner.layer?.update(signature: 1)
            owner.layer?.ensureTiles(document: owner.document!, scale: 1.5, attribute: "id")
            return owner.layer!.rasterCount
        }
        WidgetScrollLayer.queue.sync {}
        RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        XCTAssertGreaterThan(owner.heldCount, 0)
        owner.sync {
            XCTAssertEqual(owner.layer?.rasterCount, initial)
            XCTAssertGreaterThan(owner.layer!.tileCount, initial)
            XCTAssertEqual(owner.completed, 0)
        }
        owner.drain()
        owner.sync {
            XCTAssertGreaterThan(owner.completed, 0)
            XCTAssertEqual(owner.layer?.rasterCount, owner.layer?.tileCount)
        }
        try verifyLatestPixels(owner)
    }

    func testCancelledGenerationAndConsumedSourceRetryOnTheOwner() throws {
        for changedInside in [false, true] {
            let owner = Owner()
            defer { owner.destroy() }
            let gate = DispatchSemaphore(value: 0)
            WidgetScrollLayer.queue.async { gate.wait() }
            var released = false
            defer { if !released { gate.signal() } }
            let initial = try owner.sync { () throws -> Int in
                let before = document()
                owner.document = before
                owner.makeLayer(geometry)
                let layer = try XCTUnwrap(owner.layer)
                layer.update(signature: 1)
                layer.ensureTiles(document: before, scale: 2, attribute: "id")
                let initial = layer.rasterCount
                let after = try XCTUnwrap(LHDocument(previous: before, styles: changedInside ? ["4": "height:700px;background:blue"] : ["5": "height:20px;background:blue"], attribute: "id", css: nil))
                XCTAssertTrue(after.reusedPrevious)
                _ = after.layout(maxWidth: 120, preferredWidth: 120)
                owner.document = after
                if changedInside { layer.update(signature: 2) }
                layer.refresh(document: after, scale: 2, attribute: "id")
                return initial
            }
            gate.signal()
            released = true
            WidgetScrollLayer.queue.sync {}
            XCTAssertGreaterThan(owner.heldCount, 0)
            owner.sync { XCTAssertEqual(owner.layer?.rasterCount, initial) }
            owner.drain()
            owner.sync {
                let expected = owner.layer!.tileCount + (changedInside ? initial : 0)
                XCTAssertEqual(owner.layer?.rasterCount, expected)
                XCTAssertGreaterThan(owner.completed, owner.layer!.tileCount - initial)
            }
            try verifyLatestPixels(owner)
        }
    }

    func testTeardownBeforeHeldCompletionDoesNotRetainOrResurrectLayer() {
        let owner = Owner()
        weak var retired: WidgetScrollLayer?
        owner.sync {
            owner.document = document()
            owner.makeLayer(geometry)
            retired = owner.layer
            owner.layer?.update(signature: 1)
            owner.layer?.ensureTiles(document: owner.document!, scale: 1, attribute: "id")
        }
        WidgetScrollLayer.queue.sync {}
        XCTAssertGreaterThan(owner.heldCount, 0)
        owner.sync {
            owner.layer = nil
            owner.document = nil
            XCTAssertNil(retired)
        }
        owner.drain()
        owner.sync {
            XCTAssertNil(retired)
            XCTAssertNil(owner.layer)
            XCTAssertGreaterThan(owner.completed, 0)
        }
    }

    func testRendererCompletionsFollowItsCurrentSchedulerAfterMigration() throws {
        _ = NSApplication.shared
        let firstOwner = Owner()
        let secondOwner = Owner()
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 120)
        renderer.schedule = { firstOwner.hold($0) }
        renderer.isOnExecutor = { firstOwner.isCurrent }
        renderer.executorIsMain = { false }
        renderer.view.frame = geometry.viewport
        let gate = DispatchSemaphore(value: 0)
        WidgetScrollLayer.queue.async { gate.wait() }
        var released = false
        defer {
            if !released { gate.signal() }
            firstOwner.transferHeldCompletions(to: secondOwner)
            secondOwner.drain()
            secondOwner.sync { renderer.destroy() }
            secondOwner.drain()
        }
        let ops: [[String: Any]] = [
            ["op": "el", "id": 1, "tag": "body", "attrs": [["style", "margin:0"]], "adopt": "body"],
            ["op": "el", "id": 3, "tag": "div", "attrs": [["style", "width:120px;height:100px;overflow:auto"]]],
            ["op": "ins", "id": 3, "parent": 1],
            ["op": "el", "id": 4, "tag": "div", "attrs": [["style", "height:700px;background:blue"]]],
            ["op": "ins", "id": 4, "parent": 3]
        ]
        let message = String(data: try JSONSerialization.data(withJSONObject: ["type": "ops", "ops": ops]), encoding: .utf8)!
        firstOwner.sync {
            renderer.load()
            renderer.noteUserInput()
            renderer.send(message)
        }
        let deadline = Date().addingTimeInterval(2)
        while !firstOwner.sync({ renderer.scrolling.layers[3] != nil }) && Date() < deadline {
            firstOwner.runHeldCompletions()
            RunLoop.main.run(until: Date().addingTimeInterval(0.002))
        }
        let initial = try firstOwner.sync { () throws -> Int in
            let layer = try XCTUnwrap(renderer.scrolling.layers[3])
            XCTAssertGreaterThan(layer.tileCount, layer.rasterCount)
            renderer.schedule = { secondOwner.hold($0) }
            renderer.isOnExecutor = { secondOwner.isCurrent }
            return layer.rasterCount
        }
        let previousOwnerCallbacks = firstOwner.heldCount
        gate.signal()
        released = true
        WidgetScrollLayer.queue.sync {}
        RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        XCTAssertEqual(firstOwner.heldCount, previousOwnerCallbacks)
        XCTAssertGreaterThan(secondOwner.heldCount, 0)
        secondOwner.sync { XCTAssertEqual(renderer.scrolling.layers[3]?.rasterCount, initial) }
        firstOwner.transferHeldCompletions(to: secondOwner)
        secondOwner.drain()
        try secondOwner.sync {
            let layer = try XCTUnwrap(renderer.scrolling.layers[3])
            let document = try XCTUnwrap(renderer.document)
            XCTAssertGreaterThan(layer.rasterCount, initial)
            XCTAssertGreaterThan(secondOwner.completed, 0)
            for tile in layer.content.sublayers ?? [] {
                let scale = tile.contentsScale
                let expected = try XCTUnwrap(document.copyScrollContent(attribute: WidgetDOM.idAttribute, value: "3", rect: tile.frame.insetBy(dx: -1 / scale, dy: -1 / scale), scale: scale))
                XCTAssertEqual(try pixels(tile), expected.dataProvider?.data as Data?)
            }
        }
    }

    func testNarrowResizeRefillsVisibleTilesBeforeQueuedCompletion() throws {
        let owner = Owner()
        let gate = DispatchSemaphore(value: 0)
        WidgetScrollLayer.queue.async { gate.wait() }
        defer {
            gate.signal()
            owner.destroy()
        }
        try owner.sync {
            let before = document()
            owner.document = before
            owner.makeLayer(geometry)
            let layer = try XCTUnwrap(owner.layer)
            layer.update(signature: 1)
            layer.ensureTiles(document: before, scale: 1.5, attribute: "id")
            let after = try XCTUnwrap(LHDocument(previous: before, styles: ["3": "width:160px;height:100px;overflow:auto"], attribute: "id", css: nil))
            _ = after.layout(maxWidth: 160, preferredWidth: 160)
            owner.document = after
            let resized = WidgetScrollGeometry(viewport: CGRect(x: 0, y: 0, width: 160, height: 100), content: CGSize(width: 160, height: 700), radii: [])
            layer.apply(resized)
            layer.show(after.setScrollOffset(CGPoint(x: 0, y: 300), attribute: "id", value: "3"))
            layer.ensureTiles(document: after, scale: 1.5, attribute: "id")
        }
        try verifyLatestPixels(owner, visibleOnly: true)
    }

    func testRepeatedResizeAndScrollWithDelayedRasterCompletions() throws {
        let owner = Owner()
        defer { owner.destroy() }
        owner.sync {
            owner.document = document()
            owner.makeLayer(geometry)
            owner.layer?.update(signature: 1)
            owner.layer?.ensureTiles(document: owner.document!, scale: 1.5, attribute: "id")
        }
        owner.drain()
        for step in 0..<24 {
            try owner.sync {
                let width = 120 + step % 4 * 19
                let height = 100 + step % 3 * 17
                let contentWidth = 900 + step % 3 * 96
                let contentHeight = 1000 + step % 5 * 60
                let colors = ["#bc2443", "#268348", "#2655ac", "#7840a4"]
                let before = try XCTUnwrap(owner.document)
                let after = try XCTUnwrap(LHDocument(previous: before, styles: [
                    "3": "width:\(width)px;height:\(height)px;overflow:auto",
                    "4": "width:\(contentWidth)px;height:\(contentHeight)px;background:\(colors[step % colors.count])"
                ], attribute: "id", css: nil))
                _ = after.layout(maxWidth: CGFloat(width), preferredWidth: CGFloat(width))
                owner.document = after
                let layer = try XCTUnwrap(owner.layer)
                let geometry = WidgetScrollGeometry(viewport: CGRect(x: 0, y: 0, width: CGFloat(width), height: CGFloat(height)), content: CGSize(width: CGFloat(contentWidth), height: CGFloat(contentHeight)), radii: [])
                layer.apply(geometry)
                layer.update(signature: step + 2)
                let requested = CGPoint(x: CGFloat(step * 97 % 700) + 0.25, y: CGFloat(step * 131 % 900) + 0.125)
                let applied = after.setScrollOffset(geometry.clamp(requested), attribute: "id", value: "3")
                layer.show(applied)
                layer.ensureTiles(document: after, scale: 1.5, attribute: "id", velocity: CGFloat(step % 3 - 1) * 80)
            }
            if step % 4 == 3 {
                owner.drain()
                try verifyLatestPixels(owner, visibleOnly: true)
            } else {
                WidgetScrollLayer.queue.sync {}
                XCTAssertGreaterThan(owner.heldCount, 0)
            }
        }
        owner.drain()
        try verifyLatestPixels(owner, visibleOnly: true)
        owner.sync { XCTAssertGreaterThan(owner.completed, 24) }
    }
}

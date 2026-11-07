import XCTest
import AppKit
import LiteHTML
import WidgetSandboxProtocol
@testable import TwidgetRenderer

final class ScrollCompositorHandoffTests: XCTestCase {
    private final class Harness {
        let window: NSWindow
        let renderer: LiteHTMLWidgetRenderer
        let size = CGSize(width: 320, height: 180)
        var scheduled: [() -> Void] = []

        init(allowTakeover: Bool = false) {
            let size = CGSize(width: 320, height: 180)
            _ = NSApplication.shared
            window = NSWindow(contentRect: CGRect(origin: .zero, size: size), styleMask: [.borderless], backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            window.colorSpace = .sRGB
            renderer = LiteHTMLWidgetRenderer(theme: "light", width: size.width)
            renderer.scrolling.allowsCompositorTakeover = allowTakeover
            renderer.view.frame = CGRect(origin: .zero, size: size)
            window.contentView?.addSubview(renderer.view)
            if let view = renderer.view as? WidgetDocumentView {
                view.captureWindowState()
                XCTAssertEqual(view.windowState.colorSpace, NSColorSpace.sRGB.cgColorSpace)
            }
            renderer.schedule = { [unowned self] in self.scheduled.append($0) }
            renderer.load()
        }

        func send(_ ops: [[String: Any]], file: StaticString = #filePath, line: UInt = #line) {
            let deadline = Date().addingTimeInterval(5)
            renderer.send(ops: ops.map(WidgetOp.init))
            while !scheduled.isEmpty && Date() < deadline { scheduled.removeFirst()() }
            var scrollQueueDrained = false
            WidgetScrollLayer.queue.async {
                DispatchQueue.main.async { scrollQueueDrained = true }
            }
            repeat {
                let count = scheduled.count
                for _ in 0..<count where !scheduled.isEmpty && Date() < deadline { scheduled.removeFirst()() }
                renderer.view.displayIfNeeded()
                RunLoop.main.run(until: Date().addingTimeInterval(0.002))
            } while (!scrollQueueDrained || !scheduled.isEmpty || renderer.heldOpBatches > 0 || renderer.isPreparingTiles) && Date() < deadline
            XCTAssertTrue(scrollQueueDrained, file: file, line: line)
            XCTAssertTrue(scheduled.isEmpty, file: file, line: line)
            XCTAssertEqual(renderer.heldOpBatches, 0, file: file, line: line)
            XCTAssertFalse(renderer.isPreparingTiles, file: file, line: line)
        }

        func assertPromoted(_ tracks: Set<Int>, overlaps: Set<Int>, file: StaticString = #filePath, line: UInt = #line) {
            let actual = renderer.compositedElementIDs
            XCTAssertTrue(tracks.isSubset(of: actual), "missing tracks: \(tracks.subtracting(actual))", file: file, line: line)
            XCTAssertTrue(actual.isSubset(of: tracks.union(overlaps)), "unexpected promotions: \(actual.subtracting(tracks.union(overlaps)))", file: file, line: line)
        }

        func assertPixels(file: StaticString = #filePath, line: UInt = #line) throws {
            let view = try XCTUnwrap(renderer.view as? WidgetDocumentView, file: file, line: line)
            let root = try XCTUnwrap(view.contentLayer, file: file, line: line)
            let reference = LHDocument(html: renderer.dom.serialize(theme: "light"), css: nil, previous: nil, viewportSize: renderer.presentedDocument?.viewportSize ?? size)
            _ = reference.layout(maxWidth: size.width, preferredWidth: size.width)
            for (id, offset) in renderer.scrolling.offsets {
                _ = reference.setScrollOffset(offset, attribute: WidgetDOM.idAttribute, value: String(id))
            }
            func display(_ layer: CALayer) {
                layer.displayIfNeeded()
                for child in layer.sublayers ?? [] { display(child) }
            }
            display(root)
            CATransaction.flush()
            let scale = view.scale
            let width = Int(ceil(size.width * scale)), height = Int(ceil(size.height * scale))
            func pixels(_ draw: (CGContext) -> Void) throws -> [UInt8] {
                var bytes = [UInt8](repeating: 0, count: width * height * 4)
                let context = try XCTUnwrap(CGContext(data: &bytes, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width * 4, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue), file: file, line: line)
                context.translateBy(x: 0, y: CGFloat(height))
                context.scaleBy(x: scale, y: -scale)
                context.setAllowsFontSmoothing(false)
                draw(context)
                return bytes
            }
            let expected = try pixels { reference.draw(in: $0, origin: .zero, clip: CGRect(origin: .zero, size: size)) }
            let actual = try pixels { root.render(in: $0) }
            var interiorDifferences = 0, edgeDifferences = 0
            var worst = 0
            var histogram: [Int: Int] = [:]
            var examples: [String] = []
            for y in 0..<height {
                for x in 0..<width {
                    let index = (y * width + x) * 4
                    let delta = (0..<4).map { abs(Int(expected[index + $0]) - Int(actual[index + $0])) }.max() ?? 0
                    guard delta > 0 else { continue }
                    worst = max(worst, delta)
                    histogram[delta, default: 0] += 1
                    if examples.count < 5 {
                        examples.append("\(x),\(height - 1 - y):\(Array(expected[index..<(index + 4)]))->\(Array(actual[index..<(index + 4)]))")
                    }
                    var interior = true
                    for ny in max(0, y - 1)...min(height - 1, y + 1) {
                        for nx in max(0, x - 1)...min(width - 1, x + 1) {
                            let neighbor = (ny * width + nx) * 4
                            if (0..<4).contains(where: { expected[neighbor + $0] != expected[index + $0] }) {
                                interior = false
                            }
                        }
                    }
                    if interior { interiorDifferences += 1 }
                    if delta > 24 { edgeDifferences += 1 }
                }
            }
            var diagnostic = "worst=\(worst) deltas=\(histogram.sorted { $0.key < $1.key }) first=\(examples)"
            if interiorDifferences > 0 || edgeDifferences > 0 {
                if renderer.compositedElementIDs.isEmpty, renderer.scrolling.promotedIDs.isEmpty, let document = renderer.presentedDocument {
                    let current = try pixels { document.draw(in: $0, origin: .zero, clip: CGRect(origin: .zero, size: size)) }
                    diagnostic += " current-document-vs-fresh=\(zip(current, expected).filter { $0 != $1 }.count) current-document-vs-layers=\(zip(current, actual).filter { $0 != $1 }.count)"
                }
                if let directory = ProcessInfo.processInfo.environment["TW_SCROLL_HANDOFF_DUMP"] {
                    try FileManager.default.createDirectory(atPath: directory, withIntermediateDirectories: true)
                    for (name, bytes) in [("expected", expected), ("actual", actual)] {
                        let provider = try XCTUnwrap(CGDataProvider(data: Data(bytes) as CFData))
                        let image = try XCTUnwrap(CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: width * 4, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue), provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent))
                        try NSBitmapImageRep(cgImage: image).representation(using: .png, properties: [:])?.write(to: URL(fileURLWithPath: directory + "/handoff-\(line)-\(name).png"))
                    }
                    try renderer.dom.serialize(theme: "light").write(toFile: directory + "/handoff-\(line).html", atomically: true, encoding: .utf8)
                }
            }
            XCTAssertEqual(interiorDifferences, 0, "interior pixels differ; \(diagnostic)", file: file, line: line)
            XCTAssertEqual(edgeDifferences, 0, "edge differences exceed antialiasing tolerance; \(diagnostic)", file: file, line: line)
        }

        deinit {
            renderer.destroy()
        }
    }

    private func element(_ id: Int, parent: Int, style: String) -> [[String: Any]] {
        return [
            ["op": "el", "id": id, "tag": "div", "attrs": [["style", style]]],
            ["op": "ins", "id": id, "parent": parent],
        ]
    }

    private func body() -> [[String: Any]] {
        return [["op": "el", "id": 1, "tag": "body", "attrs": [["style", "margin:0;width:320px;height:180px;background:#fff"]], "adopt": "body"]]
    }

    private func track(_ id: Int, parent: Int, top: Int = 0, color: String? = nil) -> [[String: Any]] {
        var ops = element(id, parent: parent, style: "position:absolute;left:0;top:\(top)px;width:300px;height:32px;display:flex;transform:translateX(0px)")
        let colors: [String] = color.map { [$0, $0, $0] } ?? ["#f00", "#0f0", "#00f"]
        for entry in colors.enumerated() {
            ops += element(id * 100 + entry.offset, parent: id, style: "width:100px;height:32px;flex-shrink:0;background:\(entry.element)")
        }
        return ops
    }

    private func scene(overflow: String = "hidden", second: Bool = true) -> [[String: Any]] {
        var ops = body()
        for (owner, child, left) in second ? [(10, 11, 20), (20, 21, 180)] : [(10, 11, 20)] {
            ops += element(owner, parent: 1, style: "position:absolute;left:\(left)px;top:20px;width:100px;height:48px;overflow:\(overflow);border-radius:6px;background:#000")
            ops += track(child, parent: owner)
            ops += element(owner + 5, parent: owner, style: "position:absolute;left:0;top:36px;width:100px;height:8px;background:#ff0")
        }
        ops += element(30, parent: 1, style: "position:absolute;left:20px;top:100px;width:260px;height:20px;background:#f0f")
        return ops
    }

    private func motion(_ ids: [Int], step: Int) -> [[String: Any]] {
        return ids.map { ["op": "style", "id": $0, "name": "transform", "value": "translateX(-\(step * 10)px)"] }
    }

    func testHiddenScrollRasterKeepsSamplingDuringFractionalMovementByDefault() throws {
        let harness = Harness()
        harness.send(scene(second: false))
        XCTAssertFalse(harness.renderer.scrolling.allowsCompositorTakeover)
        for step in 1...8 {
            harness.send([
                ["op": "style", "id": 10, "name": "left", "value": "\(20 + Double(step % 3) * 0.125)px"],
                ["op": "style", "id": 11, "name": "transform", "value": "translateX(-\(Double(step) * 10.125)px)"],
            ])
            XCTAssertTrue(harness.renderer.compositedElementIDs.isEmpty)
            let owner = try XCTUnwrap(harness.renderer.scrolling.layers[10])
            let document = try XCTUnwrap(harness.renderer.presentedDocument)
            XCTAssertGreaterThan(owner.tileCount, 0)
            for tile in owner.content.sublayers ?? [] {
                let contents = try XCTUnwrap(WidgetSurfaceImage.image(of: tile))
                XCTAssertEqual(CFGetTypeID(contents as CFTypeRef), CGImage.typeID)
                let image = contents as! CGImage
                let scale = tile.contentsScale
                let expected = try XCTUnwrap(document.copyScrollContent(attribute: WidgetDOM.idAttribute, value: "10", rect: tile.frame.insetBy(dx: -1 / scale, dy: -1 / scale), scale: scale))
                XCTAssertEqual(image.width, expected.width)
                XCTAssertEqual(image.height, expected.height)
                XCTAssertEqual(image.dataProvider?.data as Data?, expected.dataProvider?.data as Data?)
            }
        }
    }

    func testHiddenTracksTakeOverOnTheirFirstTransformAndDemoteWithoutLosingSiblings() throws {
        let harness = Harness(allowTakeover: true)
        harness.send(scene())
        XCTAssertEqual(harness.renderer.scrolling.promotedIDs, [10, 20])
        XCTAssertTrue(harness.renderer.compositedElementIDs.isEmpty)
        try harness.assertPixels()
        for step in 1...4 {
            harness.send(motion([11, 21], step: step))
            harness.assertPromoted([11, 21], overlaps: [15, 25])
            XCTAssertTrue(harness.renderer.scrolling.promotedIDs.isEmpty, "Remaining scroll rasters: \(harness.renderer.scrolling.promotedIDs)")
            try harness.assertPixels()
        }
        harness.send([
            ["op": "style", "id": 10, "name": "width", "value": "84px"],
            ["op": "style", "id": 20, "name": "left", "value": "168px"],
        ] + motion([11, 21], step: 8))
        harness.assertPromoted([11, 21], overlaps: [15, 25])
        try harness.assertPixels()
        harness.send([11, 21].map { ["op": "style", "id": $0, "name": "position", "value": "sticky"] })
        XCTAssertTrue(harness.renderer.compositedElementIDs.isEmpty)
        try harness.assertPixels()
        harness.send([11, 21].map { ["op": "style", "id": $0, "name": "position", "value": "absolute"] })
        harness.assertPromoted([11, 21], overlaps: [15, 25])
        try harness.assertPixels()
    }

    func testOverlappingTracksKeepPaintOrderWhenTheirScrollOwnerDisappears() throws {
        let harness = Harness(allowTakeover: true)
        var ops = body()
        ops += element(10, parent: 1, style: "position:absolute;left:20px;top:20px;width:100px;height:48px;overflow:hidden;border-radius:6px;background:#000")
        ops += track(11, parent: 10, color: "#f00")
        ops += track(12, parent: 10, top: 8, color: "#00f")
        ops += element(15, parent: 10, style: "position:absolute;left:0;top:42px;width:100px;height:6px;background:#ff0")
        harness.send(ops)
        XCTAssertEqual(harness.renderer.scrolling.promotedIDs, [10])
        for step in 1...4 {
            harness.send(motion([11, 12], step: step))
            try harness.assertPixels()
        }
        harness.assertPromoted([11, 12], overlaps: [15])
        XCTAssertTrue(harness.renderer.scrolling.promotedIDs.isEmpty)
        harness.send(motion([11, 12], step: 12))
        try harness.assertPixels()
    }

    func testWheelAndExplicitOffsetOwnersKeepTheirScrollLayers() throws {
        for overflow in ["auto", "hidden"] {
            let harness = Harness(allowTakeover: true)
            harness.send(scene(overflow: overflow, second: false))
            if overflow == "hidden" {
                harness.send([["op": "prop", "id": 10, "name": "scrollLeft", "value": 23]])
                XCTAssertEqual(harness.renderer.scrolling.offset(10).x, 23)
            }
            for step in 1...4 {
                harness.send(motion([11], step: step))
                XCTAssertTrue(harness.renderer.compositedElementIDs.isEmpty, overflow)
                XCTAssertEqual(harness.renderer.scrolling.promotedIDs, [10], overflow)
                try harness.assertPixels()
            }
        }
    }

    func testNewWheelOwnerTakesBackAlreadyPromotedTracks() throws {
        for overflow in ["auto", "scroll"] {
            let harness = Harness(allowTakeover: true)
            harness.send(scene(second: false))
            for step in 1...3 {
                harness.send(motion([11], step: step))
            }
            harness.assertPromoted([11], overlaps: [15])
            XCTAssertTrue(harness.renderer.scrolling.promotedIDs.isEmpty)
            XCTAssertNil(harness.renderer.scrolling.hitViewports[10])
            try harness.assertPixels()

            harness.send([["op": "style", "id": 10, "name": "overflow", "value": overflow]])
            XCTAssertTrue(harness.renderer.compositedElementIDs.isEmpty, overflow)
            XCTAssertEqual(harness.renderer.scrolling.promotedIDs, [10], overflow)
            XCTAssertNotNil(harness.renderer.scrolling.hitViewports[10], overflow)
            try harness.assertPixels()

            harness.send(motion([11], step: 4))
            XCTAssertTrue(harness.renderer.compositedElementIDs.isEmpty, overflow)
            XCTAssertEqual(harness.renderer.scrolling.promotedIDs, [10], overflow)
            try harness.assertPixels()

            harness.send([["op": "prop", "id": 10, "name": "scrollLeft", "value": 23]] + motion([11], step: 5))
            XCTAssertTrue(harness.renderer.compositedElementIDs.isEmpty, overflow)
            XCTAssertEqual(harness.renderer.scrolling.promotedIDs, [10], overflow)
            XCTAssertEqual(harness.renderer.scrolling.offset(10).x, 23, overflow)
            try harness.assertPixels()
        }
    }

    func testNestedWheelOwnerBlocksDescendantTakeover() throws {
        let harness = Harness(allowTakeover: true)
        var ops = body()
        ops += element(3, parent: 1, style: "position:absolute;left:20px;top:20px;width:140px;height:80px;overflow:auto;background:#000")
        ops += element(10, parent: 3, style: "position:relative;width:100px;height:48px;overflow:hidden;border-radius:6px")
        ops += track(11, parent: 10)
        ops += element(40, parent: 3, style: "width:140px;height:200px;background:#0ff")
        harness.send(ops)
        XCTAssertEqual(harness.renderer.scrolling.promotedIDs, [3])
        for step in 1...4 {
            harness.send(motion([11], step: step))
            XCTAssertTrue(harness.renderer.compositedElementIDs.isEmpty)
            XCTAssertEqual(harness.renderer.scrolling.promotedIDs, [3])
            try harness.assertPixels()
        }
    }

    func testScriptedWrapperKeepsInstalledWheelContentThroughPromotionAndScrolling() throws {
        let harness = Harness()
        var ops = body()
        ops += element(3, parent: 1, style: "position:absolute;left:20px;top:20px;width:140px;height:80px;background:#ff0")
        ops += element(10, parent: 3, style: "width:100px;height:60px;overflow:auto;background:#000")
        for row in 0..<12 {
            ops += element(100 + row, parent: 10, style: "height:20px;background:\(row % 2 == 0 ? "#f00" : "#00f")")
        }
        harness.send(ops)
        XCTAssertEqual(harness.renderer.scrolling.promotedIDs, [10])
        XCTAssertTrue(harness.renderer.compositedElementIDs.isEmpty)
        try harness.assertPixels()
        for _ in 0..<2 {
            harness.send([["op": "style", "id": 3, "name": "opacity", "value": "1"]])
            XCTAssertEqual(harness.renderer.scrolling.promotedIDs, [10])
            XCTAssertTrue(harness.renderer.compositedElementIDs.isEmpty)
        }
        harness.send([["op": "style", "id": 3, "name": "transform", "value": "translateX(6px)"]])
        XCTAssertEqual(harness.renderer.compositedElementIDs, [3])
        XCTAssertTrue(harness.renderer.scrolling.promotedIDs.isEmpty)
        try harness.assertPixels()
        harness.send([["op": "style", "id": 3, "name": "transform", "value": "translateX(8px)"]])
        XCTAssertEqual(harness.renderer.compositedElementIDs, [3])
        try harness.assertPixels()
        let cg = try XCTUnwrap(CGEvent(scrollWheelEvent2Source: nil, units: .pixel, wheelCount: 1, wheel1: -20, wheel2: 0, wheel3: 0))
        cg.setIntegerValueField(CGEventField(rawValue: 88)!, value: 1)
        let event = try XCTUnwrap(NSEvent(cgEvent: cg))
        XCTAssertTrue(harness.renderer.handleScroll(CGPoint(x: 50, y: 40), event: event))
        harness.send([])
        XCTAssertEqual(harness.renderer.scrolling.offset(10).y, 20)
        XCTAssertEqual(harness.renderer.compositedElementIDs, [3])
        XCTAssertTrue(harness.renderer.scrolling.promotedIDs.isEmpty)
        try harness.assertPixels()
        harness.send([["op": "prop", "id": 10, "name": "scrollTop", "value": 40]])
        XCTAssertEqual(harness.renderer.scrolling.offset(10).y, 40)
        try harness.assertPixels()
    }
}

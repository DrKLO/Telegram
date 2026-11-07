import XCTest
import AppKit
import WidgetSandboxProtocol
@testable import TwidgetRenderer

final class CrowdedScriptedTests: XCTestCase {
    private final class Harness {
        let window: NSWindow
        let renderer: LiteHTMLWidgetRenderer
        var scheduled: [() -> Void] = []

        init() {
            window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 400, height: 400), styleMask: [.borderless], backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            renderer = LiteHTMLWidgetRenderer(theme: "light", width: 400)
            renderer.view.frame = NSRect(x: 0, y: 0, width: 400, height: 400)
            window.contentView?.addSubview(renderer.view)
            renderer.schedule = { [unowned self] in self.scheduled.append($0) }
            renderer.load()
        }

        func send(_ ops: [[String: Any]]) {
            renderer.send(ops: ops.map(WidgetOp.init))
            while !scheduled.isEmpty { scheduled.removeFirst()() }
            let deadline = Date().addingTimeInterval(5)
            while (renderer.heldOpBatches > 0 || renderer.isPreparingTiles) && Date() < deadline {
                RunLoop.main.run(until: Date().addingTimeInterval(0.005))
                while !scheduled.isEmpty { scheduled.removeFirst()() }
            }
        }

        func pixels() throws -> [UInt8] {
            let image = try XCTUnwrap(renderer.snapshot())
            let cg = try XCTUnwrap(image.cgImage(forProposedRect: nil, context: nil, hints: nil))
            let width = cg.width, height = cg.height
            var data = [UInt8](repeating: 0, count: width * height * 4)
            let ctx = try XCTUnwrap(CGContext(data: &data, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width * 4, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
            ctx.draw(cg, in: CGRect(x: 0, y: 0, width: width, height: height))
            return data
        }

        deinit {
            renderer.destroy()
        }
    }

    private let count = WidgetCompositor.maxLayers + 44

    private func setup(transform: (Int) -> String?) -> [[String: Any]] {
        var ops: [[String: Any]] = [["op": "el", "id": 1, "tag": "body", "attrs": [["style", "margin:0;height:400px;position:relative"]], "adopt": "body"]]
        for i in 0..<count {
            var style = "position:absolute;width:10px;height:10px;background:rgb(\(i % 255),80,160);left:\((i % 20) * 20)px;top:\((i / 20) * 20)px"
            if let value = transform(i) {
                style += ";transform:\(value)"
            }
            ops.append(["op": "el", "id": 100 + i, "tag": "div", "attrs": [["style", style]]])
            ops.append(["op": "ins", "id": 100 + i, "parent": 1])
        }
        return ops
    }

    private func motion(_ frame: Int, _ i: Int) -> String {
        return "translate(\((frame + i) % 5)px, \((frame * 2 + i) % 4)px)"
    }

    private func batch(_ frame: Int, ids: Range<Int>) -> [[String: Any]] {
        return ids.map { ["op": "style", "id": 100 + $0, "name": "transform", "value": motion(frame, $0)] }
    }

    func testFewScriptedElementsArePromoted() throws {
        _ = NSApplication.shared
        let harness = Harness()
        harness.send(setup(transform: { _ in nil }))
        for frame in 0..<5 {
            harness.send(batch(frame, ids: 0..<8))
        }
        XCTAssertGreaterThan(harness.renderer.compositedCount, 0)
    }

    func testCrowdedScriptedBatchesStayPaintedAndMatchAFreshRender() throws {
        _ = NSApplication.shared
        let harness = Harness()
        harness.send(setup(transform: { _ in nil }))
        for frame in 0..<5 {
            harness.send(batch(frame, ids: 0..<8))
        }
        XCTAssertGreaterThan(harness.renderer.compositedCount, 0)
        let last = 9
        for frame in 5...last {
            harness.send(batch(frame, ids: 0..<count))
            XCTAssertEqual(harness.renderer.compositedCount, 0, "frame \(frame)")
        }
        let fresh = Harness()
        fresh.send(setup(transform: { self.motion(last, $0) }))
        XCTAssertEqual(fresh.renderer.compositedCount, 0)
        let a = try harness.pixels()
        let b = try fresh.pixels()
        XCTAssertEqual(a.count, b.count)
        XCTAssertTrue(a == b, "crowded scripted frame differs from a fresh render")
    }
}

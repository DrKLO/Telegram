import XCTest
@testable import TwidgetRenderer

final class MagicMoveTests: XCTestCase {
    private static let space = CGColorSpace(name: CGColorSpace.sRGB)!

    private static func card(_ size: CGSize, scale: CGFloat, color: CGColor, radius: CGFloat, mark: CGColor? = nil) -> CGImage {
        let pw = Int(size.width * scale), ph = Int(size.height * scale)
        let ctx = CGContext(data: nil, width: pw, height: ph, bitsPerComponent: 8, bytesPerRow: 0, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue)!
        ctx.scaleBy(x: scale, y: scale)
        ctx.addPath(CGPath(roundedRect: CGRect(origin: .zero, size: size), cornerWidth: radius, cornerHeight: radius, transform: nil))
        ctx.setFillColor(color)
        ctx.fillPath()
        if let mark = mark {
            ctx.setFillColor(mark)
            ctx.fill(CGRect(x: 14, y: size.height - 24, width: 20, height: 8))
        }
        return ctx.makeImage()!
    }

    private static func snapshot(_ rect: CGRect, scale: CGFloat, color: CGColor, radius: CGFloat, mark: CGColor? = nil) -> MagicMoveStage.Snapshot {
        return MagicMoveStage.Snapshot(image: card(rect.size, scale: scale, color: color, radius: radius, mark: mark), ink: rect, border: rect, corners: MagicMoveCorners(topLeft: radius, topRight: radius, bottomRight: radius, bottomLeft: radius))
    }

    private static func render(_ size: CGSize, scale: CGFloat, _ body: (CGContext) -> Void) -> [UInt8] {
        let pw = Int(size.width * scale), ph = Int(size.height * scale)
        var data = [UInt8](repeating: 0, count: pw * ph * 4)
        data.withUnsafeMutableBytes { buffer in
            let ctx = CGContext(data: buffer.baseAddress, width: pw, height: ph, bitsPerComponent: 8, bytesPerRow: pw * 4, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
            ctx.translateBy(x: 0, y: CGFloat(ph))
            ctx.scaleBy(x: scale, y: -scale)
            body(ctx)
        }
        return data
    }

    private static func plain(_ snapshot: MagicMoveStage.Snapshot, _ ctx: CGContext) {
        ctx.saveGState()
        ctx.translateBy(x: snapshot.ink.minX, y: snapshot.ink.maxY)
        ctx.scaleBy(x: 1, y: -1)
        ctx.draw(snapshot.image, in: CGRect(origin: .zero, size: snapshot.ink.size))
        ctx.restoreGState()
    }

    private static func worst(_ a: [UInt8], _ b: [UInt8]) -> Int {
        return zip(a, b).reduce(0) { max($0, abs(Int($1.0) - Int($1.1))) }
    }

    private func stage(at time: CFTimeInterval) -> (MagicMoveStage, CALayer) {
        let host = CALayer()
        let stage = MagicMoveStage(host: host)
        stage.clock = { time }
        return (stage, host)
    }

    func testResizingMoveStartsAndEndsPixelIdentical() {
        let scale: CGFloat = 2
        let red = CGColor(colorSpace: MagicMoveTests.space, components: [1, 0.23, 0.19, 1])!
        let white = CGColor(colorSpace: MagicMoveTests.space, components: [1, 1, 1, 1])!
        let old = MagicMoveTests.snapshot(CGRect(x: 10, y: 20, width: 130, height: 54), scale: scale, color: red, radius: 12, mark: white)
        let new = MagicMoveTests.snapshot(CGRect(x: 30, y: 90, width: 256, height: 88), scale: scale, color: red, radius: 12)
        let (stage, host) = stage(at: 100)
        stage.reducesMotion = { false }
        stage.play([.move(key: "a", from: old.border, to: new, previous: old)], scale: scale)
        let canvas = CGSize(width: 320, height: 200)
        let start = MagicMoveTests.render(canvas, scale: scale) { stage.draw(into: $0, at: 100) }
        let expectedStart = MagicMoveTests.render(canvas, scale: scale) { MagicMoveTests.plain(old, $0) }
        XCTAssertLessThanOrEqual(MagicMoveTests.worst(start, expectedStart), 1)
        let residual = stage.presentedFramesAt(100 + MagicMoveStage.duration)["a"]!
        XCTAssertLessThan(abs(residual.maxX - new.border.maxX) + abs(residual.maxY - new.border.maxY), 0.1)
        let end = MagicMoveTests.render(canvas, scale: scale) { stage.draw(into: $0, at: 101) }
        let expectedEnd = MagicMoveTests.render(canvas, scale: scale) { MagicMoveTests.plain(new, $0) }
        XCTAssertLessThanOrEqual(MagicMoveTests.worst(end, expectedEnd), 1)
        stage.cancel()
        XCTAssertNil(host.sublayers?.first)
    }

    func testSpringMatchesCoreAnimationSamples() {
        let spring = MagicMoveSpring.standard
        let samples: [(Double, Double)] = [(0.038, 20.088), (0.075, 46.930), (0.113, 69.837), (0.150, 84.886), (0.225, 98.285), (0.292, 100.709)]
        for (time, value) in samples {
            XCTAssertEqual(spring.value(from: 0, to: 100, velocity: 200, at: time), value, accuracy: 0.4)
        }
        XCTAssertEqual(spring.value(from: 0, to: 100, velocity: 0, at: 0), 0)
        XCTAssertEqual(spring.value(from: 0, to: 100, velocity: 0, at: 2), 100)
        XCTAssertEqual(spring.normalizedVelocity(200, from: 0, to: 100), 2)
        XCTAssertEqual(spring.normalizedVelocity(200, from: 50, to: 50.1), 0)
    }

    func testSpringVelocityIsTheDerivative() {
        let spring = MagicMoveSpring.standard
        for time in stride(from: 0.0, through: 0.5, by: 0.05) {
            let h = 1e-5
            let numeric = (spring.value(from: 10, to: 240, velocity: -300, at: time + h) - spring.value(from: 10, to: 240, velocity: -300, at: max(0, time - h))) / (time == 0 ? h : 2 * h)
            XCTAssertEqual(spring.velocity(from: 10, to: 240, velocity: -300, at: time), numeric, accuracy: max(1, abs(numeric) * 1e-3))
        }
        let critical = MagicMoveSpring(mass: 1, stiffness: 100, damping: 20)
        let over = MagicMoveSpring(mass: 1, stiffness: 100, damping: 40)
        for spring in [critical, over] {
            XCTAssertEqual(spring.value(from: 0, to: 1, velocity: 5, at: 0), 0, accuracy: 1e-9)
            XCTAssertEqual(spring.velocity(from: 0, to: 1, velocity: 5, at: 0), 5, accuracy: 1e-9)
            XCTAssertEqual(spring.value(from: 0, to: 1, velocity: 0, at: 5), 1, accuracy: 1e-6)
        }
    }

    func testRetargetKeepsPositionAndVelocity() {
        let first = MagicMoveMotion(from: CGRect(x: 0, y: 0, width: 100, height: 40), to: CGRect(x: 200, y: 80, width: 236, height: 90), begin: 10)
        let presented = first.frame(at: 10.1)
        let second = first.retargeted(from: presented, to: CGRect(x: 0, y: 0, width: 100, height: 40), at: 10.1)
        XCTAssertEqual(second.frame(at: 10.1), presented)
        for (a, b) in zip(first.rates(at: 10.1), second.rates(at: 10.1)) {
            XCTAssertEqual(a, b, accuracy: 1e-6)
        }
        XCTAssertGreaterThan(second.frame(at: 10.12).minX, presented.minX)
        XCTAssertEqual(second.frame(at: 12).minX, 0)
    }

    func testRetargetAcceptsAFramePresentedEarlierInTheWindow() {
        let first = MagicMoveMotion(from: CGRect(x: 0, y: 0, width: 100, height: 40), to: CGRect(x: 300, y: 0, width: 100, height: 40), begin: 10)
        let stale = first.frame(at: 10.08)
        let second = first.retargeted(from: stale, to: .zero, at: 10.1)
        XCTAssertEqual(second.from, first.frame(at: 10.1))
        XCTAssertNotEqual(second.velocity, [0, 0, 0, 0])
        let unrelated = first.retargeted(from: CGRect(x: 900, y: 900, width: 10, height: 10), to: .zero, at: 10.1)
        XCTAssertEqual(unrelated.from, CGRect(x: 900, y: 900, width: 10, height: 10))
        XCTAssertEqual(unrelated.velocity, [0, 0, 0, 0])
    }

    func testFadeCurveIsMonotonicWithExactEnds() {
        let fade = MagicMoveFade.fadeOut
        XCTAssertEqual(fade.value(at: 0), 1)
        XCTAssertEqual(fade.value(at: fade.delay), 1)
        XCTAssertEqual(fade.value(at: fade.delay + fade.duration), 0)
        var last: Float = 1
        for step in 0...40 {
            let value = fade.value(at: fade.delay + fade.duration * Double(step) / 40)
            XCTAssertLessThanOrEqual(value, last + 1e-6)
            last = value
        }
        XCTAssertEqual(MagicMoveFade.solve([0.45, 0, 0.55, 1], x: 0.5), 0.5, accuracy: 1e-6)
        XCTAssertEqual(MagicMoveFade.solve([0.33, 1, 0.68, 1], x: 0.5), 0.872, accuracy: 0.002)
        XCTAssertEqual(MagicMoveFade.solve([0.42, 0, 1, 1], x: 0.25), 1 - MagicMoveFade.solve([0, 0, 0.58, 1], x: 0.75), accuracy: 1e-6)
    }

    func testGeometryClassifiesAndPinsContent() {
        XCTAssertEqual(MagicMoveGeometry.kind(from: CGRect(x: 0, y: 0, width: 100, height: 40), to: CGRect(x: 50, y: 9, width: 100.3, height: 40)), .translate)
        XCTAssertEqual(MagicMoveGeometry.kind(from: CGRect(x: 0, y: 0, width: 110, height: 40), to: CGRect(x: 0, y: 0, width: 236, height: 40)), .resize)
        let overhang = MagicMoveInsets.overhang(ink: CGRect(x: 9, y: 10, width: 103, height: 41), border: CGRect(x: 10, y: 10, width: 100, height: 40))
        XCTAssertEqual(overhang, MagicMoveInsets(top: 0, left: 1, bottom: 1, right: 2))
        XCTAssertEqual(overhang.outset(CGRect(x: 10, y: 10, width: 100, height: 40)), CGRect(x: 9, y: 10, width: 103, height: 41))
        XCTAssertEqual(MagicMoveGeometry.contentOrigin(ink: CGRect(x: 9, y: 10, width: 103, height: 41), border: CGRect(x: 10, y: 10, width: 100, height: 40), overhang: overhang), .zero)
        XCTAssertTrue(MagicMoveGeometry.sharesLeadingEdges(overhang, MagicMoveInsets(top: 0, left: 1, bottom: 0, right: 0)))
        XCTAssertFalse(MagicMoveGeometry.sharesLeadingEdges(overhang, .zero))
        XCTAssertEqual(MagicMoveGeometry.aligned(CGRect(x: 10.2, y: 5.3, width: 55.02, height: 25), to: CGRect(x: 100, y: 40.25, width: 80, height: 25), scale: 2), CGRect(x: 10, y: 5.25, width: 55.02, height: 25))
        XCTAssertEqual(MagicMoveGeometry.scaled(CGRect(x: 0, y: 0, width: 100, height: 50), factor: 0.5, about: CGPoint(x: 50, y: 25)), CGRect(x: 25, y: 12.5, width: 50, height: 25))
    }

    func testNinePatchKeepsCornersAndStretchesTheMiddle() {
        let patches = MagicMoveGeometry.ninePatch(source: CGSize(width: 100, height: 60), slice: CGSize(width: 24, height: 20), destination: CGSize(width: 300, height: 70))
        XCTAssertEqual(patches.count, 9)
        XCTAssertEqual(patches[0].source, CGRect(x: 0, y: 0, width: 24, height: 20))
        XCTAssertEqual(patches[0].destination, CGRect(x: 0, y: 0, width: 24, height: 20))
        XCTAssertEqual(patches[4].source, CGRect(x: 24, y: 20, width: 52, height: 20))
        XCTAssertEqual(patches[4].destination, CGRect(x: 24, y: 20, width: 252, height: 30))
        XCTAssertEqual(patches[8].source, CGRect(x: 76, y: 40, width: 24, height: 20))
        XCTAssertEqual(patches[8].destination, CGRect(x: 276, y: 50, width: 24, height: 20))
        XCTAssertEqual(patches.reduce(CGRect.null) { $0.union($1.destination) }, CGRect(x: 0, y: 0, width: 300, height: 70))
        let flat = MagicMoveGeometry.ninePatch(source: CGSize(width: 100, height: 60), slice: CGSize(width: 24, height: 20), destination: CGSize(width: 300, height: 40))
        XCTAssertEqual(flat.count, 6)
        XCTAssertEqual(flat.reduce(CGRect.null) { $0.union($1.destination) }, CGRect(x: 0, y: 0, width: 300, height: 40))
        XCTAssertEqual(MagicMoveGeometry.maskSlice(corners: MagicMoveCorners(topLeft: 12, topRight: 12, bottomRight: 12, bottomLeft: 12), size: CGSize(width: 130, height: 54), scale: 2, stretches: (true, true)), CGSize(width: 12, height: 12))
        XCTAssertNil(MagicMoveGeometry.maskSlice(corners: MagicMoveCorners(topLeft: 12.5, topRight: 12.5, bottomRight: 12.5, bottomLeft: 12.5), size: CGSize(width: 56, height: 25), scale: 2, stretches: (true, true)))
        XCTAssertEqual(MagicMoveGeometry.maskSlice(corners: MagicMoveCorners(topLeft: 12.5, topRight: 12.5, bottomRight: 12.5, bottomLeft: 12.5), size: CGSize(width: 56, height: 25), scale: 2, stretches: (true, false)), CGSize(width: 12.5, height: 12))
        XCTAssertEqual(MagicMoveGeometry.maskSlice(corners: .zero, size: CGSize(width: 56, height: 25), scale: 2, stretches: (true, true)), CGSize(width: 0.5, height: 0.5))
        XCTAssertNil(MagicMoveGeometry.maskSlice(corners: .zero, size: CGSize(width: 1, height: 25), scale: 2, stretches: (true, true)))
    }

    func testCornersAndFillSampling() {
        XCTAssertEqual(MagicMoveCorners(radii: [12, 10, 12, 12, 0, 0, 4, 6]), MagicMoveCorners(topLeft: 10, topRight: 12, bottomRight: 0, bottomLeft: 4))
        XCTAssertEqual(MagicMoveCorners(radii: [12, 10, 12, 12, 0, 0, 4, 6]).clipRadius, 4)
        let red = CGColor(colorSpace: MagicMoveTests.space, components: [1, 0, 0, 1])!
        let card = MagicMoveTests.snapshot(CGRect(x: 0, y: 0, width: 80, height: 40), scale: 2, color: red, radius: 10)
        XCTAssertEqual(card.fillColor, [1, 0, 0, 1])
        let clear = CGColor(colorSpace: MagicMoveTests.space, components: [1, 0, 0, 0.5])!
        XCTAssertNil(MagicMoveTests.snapshot(CGRect(x: 0, y: 0, width: 80, height: 40), scale: 2, color: clear, radius: 10).fillColor)
        let fill = MagicMoveFill(from: [1, 0, 0, 1], to: [0, 0, 1, 1])!
        XCTAssertEqual(fill.components(at: 0), [1, 0, 0, 1])
        XCTAssertEqual(fill.components(at: 1), [0, 0, 1, 1])
        XCTAssertNil(MagicMoveFill(from: nil, to: nil))
        XCTAssertEqual(MagicMoveFill(from: nil, to: [0, 1, 0, 1])!.from, [0, 1, 0, 0])
    }

    func testInterruptionKeepsThePresentedImage() {
        let scale: CGFloat = 2
        let red = CGColor(colorSpace: MagicMoveTests.space, components: [1, 0.23, 0.19, 1])!
        let white = CGColor(colorSpace: MagicMoveTests.space, components: [1, 1, 1, 1])!
        let old = MagicMoveTests.snapshot(CGRect(x: 10, y: 20, width: 130, height: 54), scale: scale, color: red, radius: 12, mark: white)
        let new = MagicMoveTests.snapshot(CGRect(x: 30, y: 90, width: 256, height: 88), scale: scale, color: red, radius: 12)
        var now: CFTimeInterval = 100
        let host = CALayer()
        let stage = MagicMoveStage(host: host)
        stage.clock = { now }
        stage.reducesMotion = { false }
        stage.play([.move(key: "a", from: old.border, to: new, previous: old)], scale: scale)
        now = 100.12
        let canvas = CGSize(width: 320, height: 200)
        let before = MagicMoveTests.render(canvas, scale: scale) { stage.draw(into: $0, at: now) }
        let presented = stage.presentedFrames()["a"]!
        let back = MagicMoveTests.snapshot(CGRect(x: 10, y: 20, width: 130, height: 54), scale: scale, color: red, radius: 12, mark: white)
        stage.play([.move(key: "a", from: presented, to: back, previous: new)], scale: scale)
        XCTAssertEqual(stage.presentedFrames()["a"], presented)
        let after = MagicMoveTests.render(canvas, scale: scale) { stage.draw(into: $0, at: now) }
        XCTAssertLessThanOrEqual(MagicMoveTests.worst(before, after), 3)
        let soon = stage.presentedFramesAt(now + 0.016)["a"]!
        XCTAssertGreaterThan(soon.minY, presented.minY)
        stage.cancel()
        now = 100.13
        let frame = stage.presentedFramesAt(now)["a"]
        XCTAssertNil(frame)
        stage.play([.move(key: "a", from: presented, to: new, previous: back)], scale: scale)
        let resumed = stage.presentedFrames()["a"]!
        XCTAssertGreaterThan(resumed.minY, presented.minY)
        stage.cancel()
    }

    func testReduceMotionDissolvesInPlace() {
        let scale: CGFloat = 2
        let red = CGColor(colorSpace: MagicMoveTests.space, components: [1, 0.23, 0.19, 1])!
        let old = MagicMoveTests.snapshot(CGRect(x: 10, y: 20, width: 130, height: 54), scale: scale, color: red, radius: 12)
        let new = MagicMoveTests.snapshot(CGRect(x: 30, y: 90, width: 256, height: 88), scale: scale, color: red, radius: 12)
        let (stage, host) = stage(at: 100)
        stage.reducesMotion = { true }
        stage.play([.move(key: "a", from: old.border, to: new, previous: old)], scale: scale)
        XCTAssertEqual(stage.presentedFramesAt(100)["a"], new.border)
        XCTAssertEqual(stage.presentedFramesAt(100.2)["a"], new.border)
        let canvas = CGSize(width: 320, height: 200)
        let start = MagicMoveTests.render(canvas, scale: scale) { stage.draw(into: $0, at: 100) }
        let expected = MagicMoveTests.render(canvas, scale: scale) { MagicMoveTests.plain(old, $0) }
        XCTAssertLessThanOrEqual(MagicMoveTests.worst(start, expected), 1)
        let end = MagicMoveTests.render(canvas, scale: scale) { stage.draw(into: $0, at: 101) }
        XCTAssertLessThanOrEqual(MagicMoveTests.worst(end, MagicMoveTests.render(canvas, scale: scale) { MagicMoveTests.plain(new, $0) }), 1)
        stage.cancel()
        XCTAssertNil(host.sublayers?.first)
    }

    func testSquareCardGrowsSolidAndExact() {
        let scale: CGFloat = 2
        let red = CGColor(colorSpace: MagicMoveTests.space, components: [1, 0.23, 0.19, 1])!
        let white = CGColor(colorSpace: MagicMoveTests.space, components: [1, 1, 1, 1])!
        let old = MagicMoveTests.snapshot(CGRect(x: 10, y: 20, width: 130, height: 54), scale: scale, color: red, radius: 0, mark: white)
        let new = MagicMoveTests.snapshot(CGRect(x: 10, y: 20, width: 256, height: 88), scale: scale, color: red, radius: 0)
        let (stage, host) = stage(at: 100)
        stage.reducesMotion = { false }
        stage.play([.move(key: "a", from: old.border, to: new, previous: old)], scale: scale)
        XCTAssertNotNil(host.sublayers?.first?.mask)
        let canvas = CGSize(width: 320, height: 200)
        XCTAssertLessThanOrEqual(MagicMoveTests.worst(MagicMoveTests.render(canvas, scale: scale) { stage.draw(into: $0, at: 100) }, MagicMoveTests.render(canvas, scale: scale) { MagicMoveTests.plain(old, $0) }), 1)
        XCTAssertLessThanOrEqual(MagicMoveTests.worst(MagicMoveTests.render(canvas, scale: scale) { stage.draw(into: $0, at: 101) }, MagicMoveTests.render(canvas, scale: scale) { MagicMoveTests.plain(new, $0) }), 1)
        let frame = stage.presentedFramesAt(100.1)["a"]!
        XCTAssertGreaterThan(frame.width, 150)
        let middle = MagicMoveTests.render(canvas, scale: scale) { stage.draw(into: $0, at: 100.1) }
        let x = Int((frame.maxX - 6) * scale), y = Int((frame.maxY - 6) * scale)
        XCTAssertEqual(middle[(y * Int(canvas.width * scale) + x) * 4 + 3], 255)
        stage.cancel()
    }
}

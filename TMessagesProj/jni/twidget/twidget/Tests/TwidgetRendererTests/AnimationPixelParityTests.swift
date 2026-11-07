import XCTest
import Cocoa
@testable import TwidgetRenderer

final class AnimationPixelParityTests: XCTestCase {
    private func pixels(transform: CGAffineTransform, opacity: Float, scale: CGFloat, box: CGSize) -> Data {
        let width = Int((box.width + 136) * scale), height = Int((box.height + 72) * scale)
        let context = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width * 4, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        context.scaleBy(x: scale, y: scale)
        context.setFillColor(CGColor(red: 0.09, green: 0.11, blue: 0.15, alpha: 1))
        context.fill(CGRect(x: 0, y: 0, width: box.width + 136, height: box.height + 72))
        context.translateBy(x: 28 + box.width / 2, y: 36 + box.height / 2)
        context.concatenate(transform)
        context.setAlpha(CGFloat(opacity))
        context.setFillColor(CGColor(red: 0.1, green: 0.55, blue: 0.95, alpha: 1))
        context.addPath(CGPath(roundedRect: CGRect(x: -box.width / 2, y: -box.height / 2, width: box.width, height: box.height), cornerWidth: 7, cornerHeight: 7, transform: nil))
        context.fillPath()
        context.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: 0.65))
        context.fill(CGRect(x: -box.width * 7 / 24, y: -3, width: box.width * 14 / 24, height: 2))
        return Data(bytes: context.data!, count: width * height * 4)
    }

    private func interpolated(_ track: WidgetCompositor.Track, at progress: Double) -> (CGAffineTransform, Float) {
        let right = track.times.firstIndex(where: { $0 >= progress }) ?? track.times.count - 1
        let left = max(0, right - 1)
        let span = track.times[right] - track.times[left]
        let f = span > 0 ? CGFloat((progress - track.times[left]) / span) : 0
        let a = track.transforms[left], b = track.transforms[right]
        let transform = CGAffineTransform(a: a.a + (b.a - a.a) * f, b: a.b + (b.b - a.b) * f, c: a.c + (b.c - a.c) * f, d: a.d + (b.d - a.d) * f, tx: a.tx + (b.tx - a.tx) * f, ty: a.ty + (b.ty - a.ty) * f)
        return (transform, track.opacities[left] + (track.opacities[right] - track.opacities[left]) * Float(f))
    }

    private func check(_ transition: String, from: [String: String], to: [String: String], geometryTolerance: CGFloat = 0.0002, channelTolerance: Int = 1, box: CGSize = CGSize(width: 24, height: 24), file: StaticString = #filePath, line: UInt = #line) throws {
        let animator = WidgetAnimator()
        animator.update(declarations: [7: from.merging(["transition": transition]) { _, value in value }], now: 0)
        animator.update(declarations: [7: to.merging(["transition": transition]) { _, value in value }], now: 1)
        let baked = try XCTUnwrap(animator.bakeable(7), file: file, line: line)
        let base = animator.base(7)
        let track = try XCTUnwrap(WidgetCompositor.bake(baked, base: base, box: box, origin: CGPoint(x: box.width / 2, y: box.height / 2)), transition, file: file, line: line)
        var differentFrames = 0, maximumChannelDifference = 0
        var maximumGeometryDifference: CGFloat = 0
        var maximumOpacityDifference: Float = 0
        for step in 0...120 {
            let progress = Double(step) / 120
            let now = baked.start + baked.spec.delay + progress * baked.spec.duration
            let sampled = animator.sample(now: now)[7] ?? []
            let transform = CSSTransform.parse(sampled.first { $0.0 == "transform" }?.1 ?? base["transform"], box: box) ?? .identity
            let opacity = Float(sampled.first { $0.0 == "opacity" }?.1 ?? base["opacity"] ?? "1")!
            let candidate = interpolated(track, at: progress)
            for corner in [CGPoint(x: -box.width / 2, y: -box.height / 2), CGPoint(x: box.width / 2, y: -box.height / 2), CGPoint(x: -box.width / 2, y: box.height / 2), CGPoint(x: box.width / 2, y: box.height / 2)] {
                let expected = corner.applying(transform), actual = corner.applying(candidate.0)
                maximumGeometryDifference = max(maximumGeometryDifference, hypot(expected.x - actual.x, expected.y - actual.y))
            }
            maximumOpacityDifference = max(maximumOpacityDifference, abs(opacity - candidate.1))
            for scale: CGFloat in [1, 2] {
                let expected = pixels(transform: transform, opacity: opacity, scale: scale, box: box)
                let actual = pixels(transform: candidate.0, opacity: candidate.1, scale: scale, box: box)
                if expected != actual {
                    differentFrames += 1
                    maximumChannelDifference = max(maximumChannelDifference, zip(expected, actual).map { abs(Int($0) - Int($1)) }.max() ?? 0)
                }
            }
        }
        print("PIXEL_PARITY \(transition) frames=242 different=\(differentFrames) maxChannel=\(maximumChannelDifference) maxGeometry=\(maximumGeometryDifference) maxAlpha=\(maximumOpacityDifference) points=\(track.times.count)")
        XCTAssertLessThanOrEqual(maximumChannelDifference, channelTolerance, transition, file: file, line: line)
        XCTAssertLessThanOrEqual(maximumGeometryDifference, geometryTolerance, transition, file: file, line: line)
        XCTAssertLessThanOrEqual(maximumOpacityDifference, 0.0002, transition, file: file, line: line)
    }

    func testBakedTransitionPixelsMatchSoftwareAtIdenticalTimes() throws {
        for (property, from, to, timing) in [
            ("transform", "translateX(0px)", "translateX(60px)", "linear"),
            ("transform", "translateX(0px)", "translateX(60px)", "ease-in-out"),
            ("transform", "translateX(0px)", "translateX(60px)", "linear(0, .3, 1.1, 1)"),
            ("opacity", "0", "1", "linear"),
            ("opacity", "0", "1", "ease-in-out")
        ] {
            try check("\(property) 1s \(timing)", from: [property: from], to: [property: to])
        }
    }

    func testCombinedTranslationAndOpacityPixelsMatchSoftware() throws {
        let from = ["transform": "translateX(0px)", "opacity": "0.2"]
        let to = ["transform": "translateX(60px)", "opacity": "1"]
        try check("transform 1s ease-in-out, opacity 1s ease-in-out", from: from, to: to)
        try check("transform 1s linear 0.2s, opacity 2s ease-in-out -0.25s", from: from, to: to)
        try check("transform 1s linear(0, .3, 1.1, 1), opacity 0.6s ease-in 0.3s", from: from, to: to)
    }

    func testModalSizedScaleAndFadePreservesGeometry() throws {
        try check("transform 900ms ease-in-out, opacity 1300ms ease-in-out 100ms", from: ["transform": "translateY(28px) scale(0.96)", "opacity": "0.2"], to: ["transform": "translateY(0px) scale(1)", "opacity": "1"], geometryTolerance: 0.012, channelTolerance: 8, box: CGSize(width: 320, height: 180))
    }

    func testCombinedScaleAndOpacityPixelsMatchSoftware() throws {
        try check("transform 1s linear, opacity 1s linear", from: ["transform": "scale(0.8)", "opacity": "0.2"], to: ["transform": "scale(1)", "opacity": "1"], geometryTolerance: 0.001)
        try check("transform 1s ease-in-out, opacity 1s ease-in-out", from: ["transform": "scale(0.8)", "opacity": "0.2"], to: ["transform": "scale(1)", "opacity": "1"], geometryTolerance: 0.001, channelTolerance: 2)
        try check("transform 1s ease-in-out, opacity 1s ease-in-out", from: ["transform": "rotate(0deg)", "opacity": "0.2"], to: ["transform": "rotate(40deg)", "opacity": "1"])
    }
}

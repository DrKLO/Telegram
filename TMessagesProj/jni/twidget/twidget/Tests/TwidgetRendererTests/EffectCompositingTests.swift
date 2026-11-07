import XCTest
import LiteHTML
@testable import TwidgetRenderer

final class EffectCompositingTests: XCTestCase {
    func testFilterListsParse() throws {
        let steps = try XCTUnwrap(CSSFilterStep.parseList("blur(4px) brightness(150%) hue-rotate(0.5turn) drop-shadow(2px 3px 6px rgba(0,0,0,.5))"))
        XCTAssertEqual(steps.map { $0.kind }, [.blur, .brightness, .hueRotate, .dropShadow])
        XCTAssertEqual(steps[0].radius, 4)
        XCTAssertEqual(steps[1].amount, 1.5, accuracy: 1e-9)
        XCTAssertEqual(steps[2].amount, 180, accuracy: 1e-9)
        XCTAssertEqual(steps[3].x, 2)
        XCTAssertEqual(steps[3].y, 3)
        XCTAssertEqual(steps[3].radius, 6)
        XCTAssertEqual(steps[3].color.a, 0.5, accuracy: 1e-6)
        XCTAssertEqual(CSSFilterStep.parseList("none"), [])
        XCTAssertEqual(CSSFilterStep.parseList(nil), [])
        XCTAssertEqual(CSSFilterStep.parseList("grayscale(3)")?.first?.amount, 1)
        XCTAssertNil(CSSFilterStep.parseList("blur(1em)"))
        XCTAssertNil(CSSFilterStep.parseList("url(#f)"))
        XCTAssertNil(CSSFilterStep.parseList("brightness(-1)"))
        XCTAssertNil(CSSFilterStep.parseList("drop-shadow(1px 1px red) blur(2px)"))
    }

    func testColorMatricesFollowTheFilterEffectsSpec() throws {
        func apply(_ text: String, _ rgb: [Double]) throws -> [Double] {
            let m = try XCTUnwrap(CSSFilterStep.parseList(text)?.first).matrix
            return (0..<3).map { r in m[r * 5] * rgb[0] + m[r * 5 + 1] * rgb[1] + m[r * 5 + 2] * rgb[2] + m[r * 5 + 4] }
        }
        XCTAssertEqual(try apply("brightness(1.5)", [0.4, 0.2, 0.1]), [0.6, 0.3, 0.15].map { $0 }, accuracy: 1e-9)
        XCTAssertEqual(try apply("contrast(2)", [0.25, 0.5, 0.75]), [0, 0.5, 1], accuracy: 1e-9)
        XCTAssertEqual(try apply("invert(1)", [0.25, 0.5, 1]), [0.75, 0.5, 0], accuracy: 1e-9)
        XCTAssertEqual(try apply("hue-rotate(0deg)", [0.3, 0.6, 0.9]), [0.3, 0.6, 0.9], accuracy: 1e-3)
        XCTAssertEqual(try apply("saturate(1)", [0.3, 0.6, 0.9]), [0.3, 0.6, 0.9], accuracy: 1e-9)
        let gray = try apply("grayscale(1)", [1, 0, 0])
        XCTAssertEqual(gray[0], gray[1], accuracy: 1e-9)
        XCTAssertEqual(gray[1], gray[2], accuracy: 1e-9)
    }

    func testBoxShadowsParseAndRejectInset() throws {
        let shadows = try XCTUnwrap(CSSBoxShadow.parseList("0 12px 40px rgba(0,0,0,.25), 1px 2px 3px 4px #ff0000"))
        XCTAssertEqual(shadows.count, 2)
        XCTAssertEqual(shadows[0].blur, 40)
        XCTAssertEqual(shadows[1].spread, 4)
        XCTAssertEqual(shadows[1].color.r, 1, accuracy: 1e-6)
        XCTAssertEqual(WidgetEffects.parse(filter: nil, shadow: "inset 0 0 4px black, 0 1px 2px red")?.shadows.count, 1)
        XCTAssertEqual(CSSFilterStep.parseList("blur(100000px)")?.first?.radius, CSSEffectLength.maxBlur)
        XCTAssertNil(CSSBoxShadow.parseList("0 0 4em black"))
    }

    func testStructuresAlignToATemplate() throws {
        let none = try XCTUnwrap(WidgetEffects.parse(filter: "none", shadow: nil))
        let blurred = try XCTUnwrap(WidgetEffects.parse(filter: "grayscale(1) contrast(1.4)", shadow: "0 4px 8px black"))
        let partial = try XCTUnwrap(WidgetEffects.parse(filter: "grayscale(0)", shadow: nil))
        let template = try XCTUnwrap(WidgetEffects.template([none, partial, blurred]))
        XCTAssertEqual(template.structure, ["grayscale", "contrast", "shadow"])
        let aligned = try XCTUnwrap(none.aligned(to: template))
        XCTAssertEqual(aligned.filter[1].amount, 1)
        XCTAssertEqual(aligned.shadows[0].color.a, 0)
        XCTAssertEqual(try XCTUnwrap(partial.aligned(to: template)).structure, template.structure)
        XCTAssertNil(WidgetEffects.template([try XCTUnwrap(WidgetEffects.parse(filter: "blur(1px)", shadow: nil)), try XCTUnwrap(WidgetEffects.parse(filter: "sepia(1)", shadow: nil))]))
    }

    func testEffectTracksBakeAlongTheAnimation() throws {
        let animator = WidgetAnimator()
        animator.setStyleSheets(["@keyframes glow { 0%, 100% { filter: blur(0px); box-shadow: 0 2px 6px rgba(0,0,0,.15); } 50% { filter: blur(8px); box-shadow: 0 24px 48px rgba(0,0,0,.35); } }"])
        animator.update(declarations: [3: ["animation": "glow 1s ease-in-out infinite", "box-shadow": "0 2px 6px rgba(0,0,0,.15)"]], now: 0)
        XCTAssertTrue(animator.isCompositable(3))
        XCTAssertTrue(animator.animatesEffects(3))
        let baked = try XCTUnwrap(animator.bakeable(3))
        let samples = animator.reachableValues(3).compactMap { WidgetEffects.parse(filter: $0.filter, shadow: $0.shadow) }
        let template = try XCTUnwrap(WidgetEffects.template(samples))
        let track = try XCTUnwrap(WidgetCompositor.bake(baked, base: animator.base(3), box: CGSize(width: 40, height: 40), origin: CGPoint(x: 20, y: 20), effects: template))
        XCTAssertEqual(track.effects.count, track.times.count)
        for step in 0...200 {
            let p = Double(step) / 200
            let truth = try XCTUnwrap(WidgetEffects.parse(filter: baked.value(p).filter, shadow: baked.value(p).shadow)?.aligned(to: template))
            let index = max(0, (track.times.lastIndex { $0 <= p } ?? 0))
            let next = min(index + 1, track.times.count - 1)
            let t0 = track.times[index], t1 = track.times[next]
            let f = t1 > t0 ? (p - t0) / (t1 - t0) : 0
            let a = track.effects[index].parameters, b = track.effects[next].parameters
            for (k, value) in truth.parameters.enumerated() {
                XCTAssertEqual(a[k] + (b[k] - a[k]) * f, value, accuracy: 0.02, "parameter \(k) at \(p)")
            }
        }
    }

    func testFilterChainsWrapColorStepsInSRGB() throws {
        let filters = try XCTUnwrap(WidgetEffectLayers.makeFilters(try XCTUnwrap(CSSFilterStep.parseList("brightness(2) saturate(1.2) blur(3px) invert(1)"))))
        XCTAssertEqual(filters.map { $0.name }, ["twE0", "tw0", "twC0", "tw1", "twC1", "twD2", "tw2", "twE3", "tw3", "twC3", "twD4"])
        XCTAssertEqual(filters[6].value(forKey: kCIInputRadiusKey) as? Double, 3)
    }

    func testShadowLayersUseHalfTheBlurRadius() throws {
        let layer = CALayer()
        layer.bounds = CGRect(x: 0, y: 0, width: 200, height: 160)
        let effects = try XCTUnwrap(WidgetEffects.parse(filter: nil, shadow: "0 12px 40px 2px rgba(0,0,0,.25)"))
        let layers = WidgetEffectLayers()
        layers.configure(layer: layer, effects: effects, backdrop: [], box: CGRect(x: 50, y: 40, width: 100, height: 80), radii: [CGFloat](repeating: 8, count: 8), maxReach: effects.reach)
        let shadow = try XCTUnwrap(layer.sublayers?.first)
        XCTAssertEqual(shadow.shadowRadius, 20)
        XCTAssertEqual(shadow.shadowOffset, CGSize(width: 0, height: 12))
        XCTAssertEqual(shadow.shadowPath?.boundingBox, CGRect(x: 48, y: 38, width: 104, height: 84))
        XCTAssertNotNil(shadow.mask)
        XCTAssertTrue(layers.content?.superlayer === layer)
    }

    private func pixel(_ image: CGImage, _ x: Int, _ y: Int) -> [Int] {
        var data = [UInt8](repeating: 0, count: 4)
        let ctx = CGContext(data: &data, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        ctx.draw(image, in: CGRect(x: -x, y: y - image.height + 1, width: image.width, height: image.height))
        return data.prefix(3).map { Int($0) }
    }

    func testDocumentsRenderFiltersInSRGB() throws {
        let document = LHDocument(html: "<body style='margin:0;background:#fff'><div style='width:40px;height:40px;background:rgb(102,51,25);filter:brightness(1.5)'></div><div style='margin-top:40px;width:40px;height:40px;background:#000;filter:blur(4px)'></div></body>", css: nil)
        _ = document.layout(maxWidth: 100, preferredWidth: 100)
        let image = try XCTUnwrap(document.copyImage(maxWidth: 100, rect: CGRect(x: 0, y: 0, width: 100, height: 140), scale: 1))
        XCTAssertEqual(pixel(image, 20, 20), [153, 76, 38], accuracy: 1)
        XCTAssertEqual(pixel(image, 40, 100)[0], 128, accuracy: 16)
        XCTAssertGreaterThan(pixel(image, 54, 100)[0], 245)
        XCTAssertLessThan(pixel(image, 44, 100)[0], 250)
        XCTAssertLessThan(pixel(image, 30, 100)[0], 12)
    }

    func testBackdropFiltersBlurWhatIsBehind() throws {
        let html = "<body style='margin:0;background:#fff'><div style='position:absolute;left:0;top:0;width:20px;height:60px;background:#000'></div><div style='position:absolute;left:0;top:0;width:60px;height:60px;backdrop-filter:blur(6px)'></div></body>"
        let document = LHDocument(html: html, css: nil)
        _ = document.layout(maxWidth: 100, preferredWidth: 100)
        let image = try XCTUnwrap(document.copyImage(maxWidth: 100, rect: CGRect(x: 0, y: 0, width: 100, height: 60), scale: 1))
        XCTAssertEqual(pixel(image, 20, 30)[0], 128, accuracy: 20)
        XCTAssertEqual(pixel(image, 5, 30)[0], 0, accuracy: 12)
        XCTAssertEqual(pixel(image, 80, 30), [255, 255, 255])
    }
}

private func XCTAssertEqual(_ a: [Double], _ b: [Double], accuracy: Double, file: StaticString = #filePath, line: UInt = #line) {
    XCTAssertEqual(a.count, b.count, file: file, line: line)
    for (x, y) in zip(a, b) {
        XCTAssertEqual(x, y, accuracy: accuracy, file: file, line: line)
    }
}

private func XCTAssertEqual(_ a: [Int], _ b: [Int], accuracy: Int, file: StaticString = #filePath, line: UInt = #line) {
    XCTAssertEqual(a.count, b.count, file: file, line: line)
    for (x, y) in zip(a, b) {
        XCTAssertEqual(x, y, accuracy: accuracy, file: file, line: line)
    }
}

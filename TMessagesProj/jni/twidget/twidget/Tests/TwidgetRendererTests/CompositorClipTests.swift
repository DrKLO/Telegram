import XCTest
import LiteHTML
@testable import TwidgetRenderer

final class CompositorClipTests: XCTestCase {
    private let track = WidgetCompositor.Clip(rect: CGRect(x: 0, y: 0, width: 400, height: 120), radii: [CGFloat](repeating: 12, count: 8))

    func testCornerFreeBandsHoldTheElement() {
        let both = WidgetCompositor.cornerFreeBands(track, containing: CGRect(x: 100, y: 40, width: 40, height: 40))
        XCTAssertEqual(both, [CGRect(x: 0.5, y: 12.5, width: 399, height: 95), CGRect(x: 12.5, y: 0.5, width: 375, height: 119)])
        XCTAssertEqual(WidgetCompositor.cornerFreeBands(track, containing: CGRect(x: 100, y: 2, width: 40, height: 100)), [CGRect(x: 12.5, y: 0.5, width: 375, height: 119)])
        XCTAssertEqual(WidgetCompositor.cornerFreeBands(track, containing: CGRect(x: 2, y: 40, width: 40, height: 40)), [CGRect(x: 0.5, y: 12.5, width: 399, height: 95)])
        XCTAssertTrue(WidgetCompositor.cornerFreeBands(track, containing: CGRect(x: 0, y: 0, width: 40, height: 40)).isEmpty)
    }

    func testClipIsInertOnlyAwayFromEdgesAndCorners() {
        let box = CGRect(x: 0, y: 40, width: 40, height: 40)
        XCTAssertTrue(WidgetCompositor.clipIsInert(track, reach: CGRect(x: 1, y: 13, width: 300, height: 94), box: box, ink: box, scale: 1))
        XCTAssertFalse(WidgetCompositor.clipIsInert(track, reach: CGRect(x: 0, y: 40, width: 300, height: 40), box: box, ink: box, scale: 1))
        XCTAssertFalse(WidgetCompositor.clipIsInert(track, reach: CGRect(x: 2, y: 2, width: 40, height: 40), box: box, ink: box, scale: 1))
        XCTAssertFalse(WidgetCompositor.clipIsInert(track, reach: CGRect(x: 360, y: 40, width: 60, height: 40), box: box, ink: box, scale: 1))
        XCTAssertFalse(WidgetCompositor.clipIsInert(track, reach: CGRect(x: 4, y: 40, width: 300, height: 40), box: box, ink: box.insetBy(dx: -6, dy: -6), scale: 1))
        XCTAssertFalse(WidgetCompositor.clipIsInert(track, reach: nil, box: box, ink: box, scale: 1))
        let square = WidgetCompositor.Clip(rect: track.rect, radii: [CGFloat](repeating: 0, count: 8))
        XCTAssertTrue(WidgetCompositor.clipIsInert(square, reach: CGRect(x: 0.5, y: 0.5, width: 399, height: 119), box: box, ink: box, scale: 1))
    }

    private func snapshot(_ style: String, scale: CGFloat) -> (CGImage, CGRect, CGRect)? {
        let document = LHDocument(html: "<div style='padding:30px'><div data-x='a' style='width:40px;height:40px;\(style)'></div></div>", css: nil)
        _ = document.layout(maxWidth: 200, preferredWidth: 200)
        var ink = CGRect.zero, box = CGRect.zero
        guard let image = document.copyImageOfElement(attribute: "data-x", value: "a", scale: scale, inkBounds: &ink, borderBox: &box) else {
            return nil
        }
        return (image, ink, box)
    }

    func testElementSnapshotsCropToPaintedPixels() throws {
        let (image, ink, box) = try XCTUnwrap(snapshot("border-radius:20px;background:#3390ec", scale: 2))
        XCTAssertEqual(ink, box)
        XCTAssertEqual(image.width, 80)
        XCTAssertEqual(image.height, 80)
        let (shadowed, shadowInk, shadowBox) = try XCTUnwrap(snapshot("background:#3390ec;box-shadow:0 4px 10px rgba(0,0,0,0.5)", scale: 2))
        XCTAssertTrue(shadowInk.contains(shadowBox))
        XCTAssertGreaterThan(shadowInk.width, shadowBox.width)
        XCTAssertLessThan(shadowInk.width, shadowBox.width + 96)
        XCTAssertEqual(CGFloat(shadowed.width), shadowInk.width * 2)
        let (_, transparentInk, transparentBox) = try XCTUnwrap(snapshot("", scale: 2))
        XCTAssertEqual(transparentInk, transparentBox)
    }

    func testElementSnapshotsPaintPositionedDescendantsOwnedByAncestors() throws {
        let document = LHDocument(html: "<div data-x='a' style='width:100px;height:60px;background:#ffffff'><div style='position:relative;height:40px'><div style='position:absolute;left:10px;top:10px;width:30px;height:20px;background:#ff0000'></div></div></div>", css: nil)
        _ = document.layout(maxWidth: 200, preferredWidth: 200)
        var ink = CGRect.zero, box = CGRect.zero
        let image = try XCTUnwrap(document.copyImageOfElement(attribute: "data-x", value: "a", scale: 1, inkBounds: &ink, borderBox: &box))
        let context = try XCTUnwrap(CGContext(data: nil, width: image.width, height: image.height, bitsPerComponent: 8, bytesPerRow: image.width * 4, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
        let pixels = try XCTUnwrap(context.data).assumingMemoryBound(to: UInt8.self)
        let x = Int(box.minX - ink.minX) + 25, y = Int(box.minY - ink.minY) + 20
        let offset = (y * image.width + x) * 4
        XCTAssertEqual(pixels[offset], 255)
        XCTAssertEqual(pixels[offset + 1], 0)
        XCTAssertEqual(pixels[offset + 2], 0)
    }

    func testScriptedRasterDensityTracksStretchInsteadOfRotation() {
        for angle in [0.1, 0.5, 1.0, 2.5, 4.0] {
            let transform = CGAffineTransform(rotationAngle: angle).translatedBy(x: 100, y: 20)
            XCTAssertEqual(WidgetCompositor.scriptedRasterMagnification(transform), 1.25, accuracy: 0.000001)
            XCTAssertEqual(WidgetCompositor.rasterScale(2, WidgetCompositor.scriptedRasterMagnification(transform)), 2.5)
        }
        XCTAssertEqual(WidgetCompositor.scriptedRasterMagnification(CGAffineTransform(translationX: 100, y: 20)), 1)
        XCTAssertEqual(WidgetCompositor.scriptedRasterMagnification(CGAffineTransform(scaleX: 2, y: 0.5)), 2.5)
        XCTAssertEqual(WidgetCompositor.scriptedRasterMagnification(CGAffineTransform(a: 1, b: 0, c: 1, d: 1, tx: 0, ty: 0)), 1.25 * (1 + sqrt(5)) / 2, accuracy: 0.000001)
    }

    func testCompactRasterMatchesConservativeCapture() throws {
        for contents in ["<span>88</span>", "<span style='font-style:italic;text-shadow:2px 1px 3px black'>Wj</span>", "<span style='position:absolute;left:-12px;top:25px'>wide</span>"] {
            func capture(_ tag: String) throws -> (CGRect, [UInt8]) {
                let style = "position:absolute;left:60px;top:60px;width:22px;height:22px;border:1px solid white;border-radius:6px;background:linear-gradient(135deg,red,blue);box-shadow:0 1px 3px #666;font:8px/22px sans-serif"
                let document = LHDocument(html: "<\(tag) id='cell' style='\(style)'>\(contents)</\(tag)>", css: nil)
                _ = document.layout(maxWidth: 200, preferredWidth: 200)
                var ink = CGRect.zero, box = CGRect.zero
                let image = try XCTUnwrap(document.copyImageOfElement(attribute: "id", value: "cell", scale: 4, inkBounds: &ink, borderBox: &box))
                let context = try XCTUnwrap(CGContext(data: nil, width: image.width, height: image.height, bitsPerComponent: 8, bytesPerRow: image.width * 4, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
                context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
                let data = try XCTUnwrap(context.data).assumingMemoryBound(to: UInt8.self)
                return (ink, Array(UnsafeBufferPointer(start: data, count: image.width * image.height * 4)))
            }
            let compact = try capture("div"), conservative = try capture("section")
            XCTAssertEqual(compact.0, conservative.0)
            XCTAssertEqual(compact.1.count, conservative.1.count)
            XCTAssertLessThanOrEqual(zip(compact.1, conservative.1).map { abs(Int($0) - Int($1)) }.max() ?? 0, 1)
        }
    }

    func testSmallCellRasterBudgetIncludesTextAndShadows() throws {
        let document = LHDocument(html: "<div id='cell' style='width:22px;height:22px;border:1px solid white;border-radius:6px;background:linear-gradient(135deg,red,blue);box-shadow:0 1px 3px #666;font:8px/22px sans-serif'><span>88</span></div>", css: nil)
        _ = document.layout(maxWidth: 200, preferredWidth: 200)
        let reserved = document.rasterBoundsOfElement(attribute: "id", value: "cell", bareShadows: false)
        var ink = CGRect.zero, box = CGRect.zero
        let image = try XCTUnwrap(document.copyImageOfElement(attribute: "id", value: "cell", scale: 4, inkBounds: &ink, borderBox: &box))
        XCTAssertTrue(reserved.contains(ink))
        XCTAssertTrue(ink.contains(box))
        XCTAssertGreaterThan(ink.width, box.width)
        XCTAssertLessThan(reserved.width, box.width + 40)
        XCTAssertLessThanOrEqual(Int(ceil(reserved.width * 4) * ceil(reserved.height * 4)) * 250, WidgetCompositor.maxPixels)
        XCTAssertEqual(CGFloat(image.width), ink.width * 4)
    }

    func testFractionalScaleCropsOnWholePixels() throws {
        let (image, ink, box) = try XCTUnwrap(snapshot("background:#3390ec", scale: 2.5))
        XCTAssertTrue(ink.contains(box))
        XCTAssertEqual(CGFloat(image.width), ink.width * 2.5, accuracy: 0.001)
        XCTAssertEqual(CGFloat(image.height), ink.height * 2.5, accuracy: 0.001)
        XCTAssertLessThan(ink.width, box.width + 8)
    }
}

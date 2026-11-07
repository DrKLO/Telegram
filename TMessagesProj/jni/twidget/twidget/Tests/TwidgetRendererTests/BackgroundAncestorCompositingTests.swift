import XCTest
import AppKit
import LiteHTML
@testable import TwidgetRenderer

final class BackgroundAncestorCompositingTests: XCTestCase {
    private func scene(parentFrames: String = "from{background-color:#123}to{background-color:#f90}", childStyle: String = "", parentStyle: String = "") -> (WidgetDOM, LHDocument, WidgetAnimator, WidgetCompositor, CALayer) {
        let dom = WidgetDOM()
        dom.apply(["op": "el", "id": 1, "tag": "html", "adopt": "html"])
        dom.apply(["op": "el", "id": 2, "tag": "head", "adopt": "head"])
        dom.apply(["op": "el", "id": 3, "tag": "body", "adopt": "body"])
        func add(_ id: Int, _ tag: String, _ parent: Int, _ style: String) {
            dom.apply(["op": "el", "id": id, "tag": tag, "attrs": [["style", style]]])
            dom.apply(["op": "ins", "id": id, "parent": parent])
        }
        add(5, "style", 2, "")
        let css = "body{margin:0}@keyframes cell{\(parentFrames)}@keyframes pop{from{transform:scale(.5)}to{transform:scale(1)}}"
        dom.apply(["op": "text", "id": 6, "data": css])
        dom.apply(["op": "ins", "id": 6, "parent": 5])
        add(10, "div", 3, "width:120px;height:120px;background-color:#123;animation:cell 1s linear both;\(parentStyle)")
        add(11, "div", 10, "margin:30px;width:40px;height:40px;background-color:#39f;animation:pop 1s linear both;\(childStyle)")
        let document = LHDocument(html: dom.serialize(theme: "light"), css: nil, previous: nil)
        document.layout(maxWidth: 180, preferredWidth: 180)
        let animator = WidgetAnimator()
        animator.setStyleSheets([css])
        let declarations = Dictionary(uniqueKeysWithValues: document.declarations(WidgetAnimator.watched, byAttribute: WidgetDOM.idAttribute).compactMap { entry in Int(entry.key).map { ($0, entry.value) } })
        animator.update(declarations: declarations, now: 0)
        let host = CALayer()
        host.bounds = CGRect(x: 0, y: 0, width: 180, height: 180)
        host.isGeometryFlipped = true
        let compositor = WidgetCompositor(host: host)
        compositor.update(document: document, animator: animator, dom: dom, declarations: declarations, scale: 2, space: CGColorSpace(name: CGColorSpace.sRGB)!)
        return (dom, document, animator, compositor, host)
    }

    func testColorAnimationAllowsIndependentChildMotion() {
        let (_, _, animator, compositor, host) = scene()
        XCTAssertEqual(compositor.ids, [11])
        compositor.drive(animator: animator, now: 0, mediaOffset: 0, enabled: true)
        XCTAssertEqual(compositor.drivenIDs, [11])
        let remainder = compositor.apply(animator.sample(now: 0.5), bases: animator.base)
        XCTAssertNotNil(remainder[10])
        XCTAssertNil(remainder[11])
        XCTAssertFalse(host.sublayers?.isEmpty ?? true)
    }

    func testInheritedBackgroundAndBackdropKeepSoftwareFallback() {
        for style in ["background-color:inherit", "background:inherit", "background-color:var(--missing,inherit)", "backdrop-filter:blur(2px)"] {
            let (_, _, _, compositor, host) = scene(childStyle: style)
            XCTAssertTrue(compositor.ids.isEmpty, style)
            XCTAssertTrue(host.sublayers?.isEmpty ?? true, style)
        }
    }

    func testInheritedTextOrGeometryChangesKeepSoftwareFallback() {
        for frames in ["from{color:#123}to{color:#f90}", "from{height:100px}to{height:120px}", "from{background-color:#123;opacity:.5}to{background-color:#f90;opacity:1}"] {
            let (_, _, _, compositor, host) = scene(parentFrames: frames)
            XCTAssertTrue(compositor.ids.isEmpty, frames)
            XCTAssertTrue(host.sublayers?.isEmpty ?? true, frames)
        }
    }
    func testNativeChildOverAnimatedBackgroundMatchesSoftwarePixels() throws {
        let (dom, _, animator, compositor, host) = scene()
        XCTAssertEqual(compositor.ids, [11])
        for scale: CGFloat in [1, 2] {
            for step in 0...4 {
                let sample = animator.sample(now: Double(step) / 4)
                _ = compositor.apply(sample, bases: animator.base)
                let document = LHDocument(html: dom.serialize(theme: "light", overrides: sample), css: nil)
                document.layout(maxWidth: 180, preferredWidth: 180)
                let rect = CGRect(x: 0, y: 0, width: 180, height: 180)
                let expectedImage = try XCTUnwrap(document.copyImage(maxWidth: 180, rect: rect, scale: scale))
                let baseImage = try XCTUnwrap(document.copyImage(maxWidth: 180, rect: rect, scale: scale, excluding: ["11"], attribute: WidgetDOM.idAttribute))
                func pixels(_ image: CGImage, overlay: Bool) -> [UInt8] {
                    let width = Int(180 * scale)
                    let context = CGContext(data: nil, width: width, height: width, bitsPerComponent: 8, bytesPerRow: width * 4, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
                    context.scaleBy(x: scale, y: scale)
                    context.draw(image, in: rect)
                    if overlay {
                        context.translateBy(x: 0, y: 180)
                        context.scaleBy(x: 1, y: -1)
                        host.render(in: context)
                    }
                    return Array(UnsafeBufferPointer(start: context.data!.assumingMemoryBound(to: UInt8.self), count: width * width * 4))
                }
                let expected = pixels(expectedImage, overlay: false), actual = pixels(baseImage, overlay: true)
                let maximum = zip(expected, actual).map { abs(Int($0) - Int($1)) }.max() ?? 0
                XCTAssertLessThanOrEqual(maximum, 1, "step=\(step) scale=\(scale)")
            }
        }
    }

}

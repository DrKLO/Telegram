import XCTest
import QuartzCore
import LiteHTML
@testable import TwidgetRenderer

final class DenseOverlapCompositingTests: XCTestCase {
    private func scene(_ count: Int) -> (WidgetDOM, LHDocument, WidgetAnimator, [Int: [String: String]]) {
        let dom = WidgetDOM()
        for (id, tag) in [(1, "html"), (2, "head"), (3, "body")] {
            dom.apply(["op": "el", "id": id, "tag": tag, "adopt": tag])
        }
        let css = "body{margin:0;width:480px;background:#101722}.scene{position:absolute;width:106px;height:94px;background:#24344c;border-radius:14px;overflow:hidden}.ring{position:absolute;left:14px;top:12px;width:62px;height:62px;border:3px solid #34435b;border-top-color:#28d8b0;border-radius:50%;animation:spin 2.4s linear infinite}.dot{position:absolute;left:26px;top:26px;width:10px;height:10px;background:#e9bf67;border-radius:50%;animation:orbit 3s linear infinite}.label{position:absolute;bottom:4px;left:7px;width:72px;height:12px;background:#9aaec7}@keyframes spin{to{transform:rotate(360deg)}}@keyframes orbit{from{transform:rotate(0deg) translateX(24px)}to{transform:rotate(360deg) translateX(24px)}}"
        dom.apply(["op": "el", "id": 5, "tag": "style", "attrs": []])
        dom.apply(["op": "ins", "id": 5, "parent": 2])
        dom.apply(["op": "text", "id": 6, "data": css])
        dom.apply(["op": "ins", "id": 6, "parent": 5])
        for i in 0..<count {
            let id = 10 + i * 4
            for (offset, name, parent) in [(0, "scene", 3), (1, "ring", id), (2, "dot", id + 1), (3, "label", id)] {
                var attrs = [["class", name]]
                if offset == 0 { attrs.append(["style", "left:\(12 + i % 4 * 116)px;top:\(12 + i / 4 * 104)px"]) }
                dom.apply(["op": "el", "id": id + offset, "tag": "div", "attrs": attrs])
                dom.apply(["op": "ins", "id": id + offset, "parent": parent])
            }
        }
        let document = LHDocument(html: dom.serialize(theme: "dark"), css: nil)
        document.layout(maxWidth: 480, preferredWidth: 480)
        let animator = WidgetAnimator()
        animator.setStyleSheets([css])
        let declarations = Dictionary(uniqueKeysWithValues: document.declarations(WidgetAnimator.watched, byAttribute: WidgetDOM.idAttribute).compactMap { entry in Int(entry.key).map { ($0, entry.value) } })
        animator.update(declarations: declarations, now: 0)
        return (dom, document, animator, declarations)
    }

    func testDenseNestedAnimationsStayCompositedAndMatchSoftware() throws {
        let (dom, document, animator, declarations) = scene(64)
        let host = CALayer()
        let rect = CGRect(x: 0, y: 0, width: 480, height: 1680)
        host.bounds = rect
        host.isGeometryFlipped = true
        let compositor = WidgetCompositor(host: host)
        let space = CGColorSpace(name: CGColorSpace.sRGB)!
        compositor.update(document: document, animator: animator, dom: dom, declarations: declarations, scale: 2, space: space)
        XCTAssertEqual(compositor.ids.count, 192)
        XCTAssertEqual(compositor.overlapIDs.count, 64)
        for time in [0.0, 0.37, 1.25, 2.39] {
            let sample = animator.sample(now: time)
            XCTAssertTrue(compositor.apply(sample, bases: animator.base).isEmpty)
            let reference = LHDocument(html: dom.serialize(theme: "dark", overrides: sample), css: nil)
            reference.layout(maxWidth: 480, preferredWidth: 480)
            let expectedImage = try XCTUnwrap(reference.copyImage(maxWidth: 480, rect: rect, scale: 2))
            let baseImage = try XCTUnwrap(reference.copyImage(maxWidth: 480, rect: rect, scale: 2, excluding: Set(compositor.ids.map(String.init)), attribute: WidgetDOM.idAttribute))
            func pixels(_ image: CGImage, overlay: Bool) -> [UInt8] {
                let ctx = CGContext(data: nil, width: 960, height: 3360, bitsPerComponent: 8, bytesPerRow: 960 * 4, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
                ctx.scaleBy(x: 2, y: 2)
                ctx.draw(image, in: rect)
                if overlay {
                    ctx.translateBy(x: 0, y: rect.height)
                    ctx.scaleBy(x: 1, y: -1)
                    host.render(in: ctx)
                }
                return Array(UnsafeBufferPointer(start: ctx.data!.assumingMemoryBound(to: UInt8.self), count: 960 * 3360 * 4))
            }
            let expected = pixels(expectedImage, overlay: false), actual = pixels(baseImage, overlay: true)
            let changed = zip(expected, actual).filter { abs(Int($0) - Int($1)) > 24 }.count
            XCTAssertLessThan(Double(changed) / Double(expected.count), 0.005, "time=\(time)")
        }
    }

    func testOverlapAdmissionRemainsWithinLayerBudget() {
        let (dom, document, animator, declarations) = scene(96)
        let host = CALayer()
        let compositor = WidgetCompositor(host: host)
        compositor.update(document: document, animator: animator, dom: dom, declarations: declarations, scale: 2, space: CGColorSpace(name: CGColorSpace.sRGB)!)
        XCTAssertLessThanOrEqual(compositor.ids.count, WidgetCompositor.maxLayers)
        for i in 0..<96 where compositor.ids.contains(12 + i * 4) {
            XCTAssertTrue(compositor.ids.contains(11 + i * 4))
        }
    }
}

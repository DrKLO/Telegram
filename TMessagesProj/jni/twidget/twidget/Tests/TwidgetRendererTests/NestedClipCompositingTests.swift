import XCTest
import QuartzCore
import LiteHTML
@testable import TwidgetRenderer

final class NestedClipCompositingTests: XCTestCase {
    private let side: CGFloat = 300

    private func scene(stageRadius: Int, cardRadius: Int, cardPadding: Int) -> (WidgetDOM, LHDocument, WidgetAnimator, [Int: [String: String]]) {
        let dom = WidgetDOM()
        dom.apply(["op": "el", "id": 1, "tag": "html", "adopt": "html"])
        dom.apply(["op": "el", "id": 2, "tag": "head", "adopt": "head"])
        dom.apply(["op": "el", "id": 3, "tag": "body", "adopt": "body"])
        dom.apply(["op": "el", "id": 5, "tag": "style", "attrs": []])
        dom.apply(["op": "ins", "id": 5, "parent": 2])
        let css = "body{margin:0;width:\(Int(side))px;height:\(Int(side))px;background:#fff}.card{margin:10px;padding:\(cardPadding)px;overflow:hidden;border-radius:\(cardRadius)px;background:#dde}.stage{position:relative;height:200px;overflow:hidden;border-radius:\(stageRadius)px;background:#eef0f6;border:1px solid #888}.t{position:absolute;width:60px;height:60px;border-radius:6px}@keyframes slide{from{transform:translate(-30px,-20px) rotate(10deg)}to{transform:translate(200px,170px) rotate(40deg)}}"
        dom.apply(["op": "text", "id": 6, "data": css])
        dom.apply(["op": "ins", "id": 6, "parent": 5])
        dom.apply(["op": "el", "id": 10, "tag": "div", "attrs": [["class", "card"]]])
        dom.apply(["op": "ins", "id": 10, "parent": 3])
        dom.apply(["op": "el", "id": 11, "tag": "div", "attrs": [["class", "stage"]]])
        dom.apply(["op": "ins", "id": 11, "parent": 10])
        let colors = ["#e33", "#3a3", "#33e"]
        for i in 0..<3 {
            dom.apply(["op": "el", "id": 100 + i, "tag": "div", "attrs": [["class", "t"], ["style", "left:\(i * 50)px;top:\(i * 30)px;background:\(colors[i]);animation:slide \(1 + i)s linear both"]]])
            dom.apply(["op": "ins", "id": 100 + i, "parent": 11])
        }
        let document = LHDocument(html: dom.serialize(theme: "light"), css: nil, previous: nil)
        document.layout(maxWidth: side, preferredWidth: side)
        let animator = WidgetAnimator()
        animator.setStyleSheets([css])
        let declarations = Dictionary(uniqueKeysWithValues: document.declarations(WidgetAnimator.watched, byAttribute: WidgetDOM.idAttribute).compactMap { entry in Int(entry.key).map { ($0, entry.value) } })
        animator.update(declarations: declarations, now: 0)
        return (dom, document, animator, declarations)
    }

    func testInnermostClipIsReportedWhenOuterClipContainsIt() {
        let (_, document, _, _) = scene(stageRadius: 14, cardRadius: 8, cardPadding: 16)
        var rect = CGRect.zero
        var radii = [CGFloat](repeating: 0, count: 8)
        XCTAssertTrue(document.clipOfElement(attribute: WidgetDOM.idAttribute, value: "100", rect: &rect, radii: &radii))
        XCTAssertEqual(rect.width, side - 20 - 32 - 2, accuracy: 0.5)
        XCTAssertEqual(rect.height, 200, accuracy: 0.5)
        XCTAssertEqual(radii[0], 13, accuracy: 0.5)
    }

    func testOuterClipCuttingIntoInnerClipIsNotReported() {
        let (_, document, _, _) = scene(stageRadius: 14, cardRadius: 30, cardPadding: 4)
        var rect = CGRect.zero
        var radii = [CGFloat](repeating: 0, count: 8)
        XCTAssertFalse(document.clipOfElement(attribute: WidgetDOM.idAttribute, value: "100", rect: &rect, radii: &radii))
    }

    func testPromotedElementsInsideNestedClipsMatchFullRender() throws {
        let scale: CGFloat = 2
        let (dom, document, animator, declarations) = scene(stageRadius: 14, cardRadius: 8, cardPadding: 16)
        let host = CALayer()
        host.bounds = CGRect(x: 0, y: 0, width: side, height: side)
        host.isGeometryFlipped = true
        let compositor = WidgetCompositor(host: host)
        compositor.update(document: document, animator: animator, dom: dom, declarations: declarations, scale: scale, space: CGColorSpace(name: CGColorSpace.sRGB)!)
        XCTAssertFalse(compositor.ids.isEmpty)
        for step in [0, 1, 2, 3, 4] {
            let sample = animator.sample(now: Double(step) / 4)
            _ = compositor.apply(sample, bases: animator.base)
            let reference = LHDocument(html: dom.serialize(theme: "light", overrides: sample), css: nil)
            reference.layout(maxWidth: side, preferredWidth: side)
            let rect = CGRect(x: 0, y: 0, width: side, height: side)
            let expectedImage = try XCTUnwrap(reference.copyImage(maxWidth: side, rect: rect, scale: scale))
            let baseImage = try XCTUnwrap(reference.copyImage(maxWidth: side, rect: rect, scale: scale, excluding: Set(compositor.ids.map { String($0) }), attribute: WidgetDOM.idAttribute))
            func pixels(_ image: CGImage, overlay: Bool) -> [UInt8] {
                let width = Int(side * scale)
                let context = CGContext(data: nil, width: width, height: width, bitsPerComponent: 8, bytesPerRow: width * 4, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
                context.scaleBy(x: scale, y: scale)
                context.draw(image, in: rect)
                if overlay {
                    context.translateBy(x: 0, y: side)
                    context.scaleBy(x: 1, y: -1)
                    host.render(in: context)
                }
                return Array(UnsafeBufferPointer(start: context.data!.assumingMemoryBound(to: UInt8.self), count: width * width * 4))
            }
            let expected = pixels(expectedImage, overlay: false), actual = pixels(baseImage, overlay: true)
            let differing = zip(expected, actual).filter { abs(Int($0) - Int($1)) > 24 }.count
            XCTAssertLessThanOrEqual(differing, 400, "step=\(step) composited=\(compositor.ids.count)")
        }
    }

    func testElementsClippedAwayByTheirOwnContainerDoNotObscure() {
        let html = "<html><body style=\"margin:0;width:300px;height:300px\"><div data-lh-id=\"1\" style=\"position:absolute;left:0;top:0;width:100px;height:50px;background:#f00\"></div><div data-lh-id=\"2\" style=\"position:absolute;left:0;top:60px;width:100px;height:40px;overflow:hidden\"><div data-lh-id=\"3\" style=\"position:relative;top:-40px;width:100px;height:60px;background:#00f\"></div></div><div data-lh-id=\"4\" style=\"position:absolute;left:0;top:30px;width:100px;height:40px;background:#0f0\"></div></body></html>"
        let document = LHDocument(html: html, css: nil, previous: nil)
        document.layout(maxWidth: side, preferredWidth: side)
        let below = document.elementsObscuring(attribute: WidgetDOM.idAttribute, value: "1", in: CGRect(x: 0, y: 0, width: 100, height: 25), ignoringSet: NSSet(), clipped: false) ?? ["nil"]
        XCTAssertEqual(below, [])
        let overlapped = document.elementsObscuring(attribute: WidgetDOM.idAttribute, value: "1", in: CGRect(x: 0, y: 0, width: 100, height: 50), ignoringSet: NSSet(), clipped: false) ?? ["nil"]
        XCTAssertEqual(overlapped, ["4"])
    }
}

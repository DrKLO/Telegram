import XCTest
import AppKit
import LiteHTML
@testable import TwidgetRenderer

final class CompositorAdmissionTests: XCTestCase {
    private struct Generator {
        var state: UInt64
        mutating func next(_ bound: Int) -> Int {
            state = state &* 6364136223846793005 &+ 1442695040888963407
            return Int((state >> 33) % UInt64(bound))
        }
    }

    private let side: CGFloat = 600

    private func scene(seed: UInt64, count: Int, animatedShare: Int) -> (WidgetDOM, LHDocument, WidgetAnimator, [Int: [String: String]], String) {
        let dom = WidgetDOM()
        dom.apply(["op": "el", "id": 1, "tag": "html", "adopt": "html"])
        dom.apply(["op": "el", "id": 2, "tag": "head", "adopt": "head"])
        dom.apply(["op": "el", "id": 3, "tag": "body", "adopt": "body"])
        dom.apply(["op": "el", "id": 5, "tag": "style", "attrs": []])
        dom.apply(["op": "ins", "id": 5, "parent": 2])
        let css = "body{margin:0;width:\(Int(side))px;height:\(Int(side))px}div{position:absolute}@keyframes slide{from{transform:translate(0px,0px)}to{transform:translate(40px,20px)}}"
        dom.apply(["op": "text", "id": 6, "data": css])
        dom.apply(["op": "ins", "id": 6, "parent": 5])
        var random = Generator(state: seed)
        for i in 0..<count {
            let size = 150 + random.next(150)
            let left = random.next(Int(side) - size - 40)
            let top = random.next(Int(side) - size - 20)
            let color = "rgb(\(random.next(256)),\(random.next(256)),\(random.next(256)))"
            let animated = random.next(100) < animatedShare
            let style = "left:\(left)px;top:\(top)px;width:\(size)px;height:\(size)px;background-color:\(color);" + (animated ? "animation:slide 1s linear both" : "")
            dom.apply(["op": "el", "id": 100 + i, "tag": "div", "attrs": [["style", style]]])
            dom.apply(["op": "ins", "id": 100 + i, "parent": 3])
        }
        let document = LHDocument(html: dom.serialize(theme: "light"), css: nil, previous: nil)
        document.layout(maxWidth: side, preferredWidth: side)
        let animator = WidgetAnimator()
        animator.setStyleSheets([css])
        let declarations = Dictionary(uniqueKeysWithValues: document.declarations(WidgetAnimator.watched, byAttribute: WidgetDOM.idAttribute).compactMap { entry in Int(entry.key).map { ($0, entry.value) } })
        animator.update(declarations: declarations, now: 0)
        return (dom, document, animator, declarations, css)
    }

    private func compositor(_ document: LHDocument, _ animator: WidgetAnimator, _ dom: WidgetDOM, _ declarations: [Int: [String: String]]) -> (WidgetCompositor, CALayer) {
        let host = CALayer()
        host.bounds = CGRect(x: 0, y: 0, width: side, height: side)
        host.isGeometryFlipped = true
        let compositor = WidgetCompositor(host: host)
        compositor.update(document: document, animator: animator, dom: dom, declarations: declarations, scale: 2, space: CGColorSpace(name: CGColorSpace.sRGB)!)
        return (compositor, host)
    }

    func testAdmissionUnderBudgetPressureMatchesFullRender() throws {
        let scale: CGFloat = 2
        var admittedTotal = 0
        for seed: UInt64 in 1...6 {
            let (dom, document, animator, declarations, _) = scene(seed: seed, count: 60, animatedShare: 70)
            let (compositor, host) = compositor(document, animator, dom, declarations)
            admittedTotal += compositor.ids.count
            for step in [0, 2, 4] {
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
                let maximum = zip(expected, actual).map { abs(Int($0) - Int($1)) }.max() ?? 0
                XCTAssertLessThanOrEqual(maximum, 1, "seed=\(seed) step=\(step) admitted=\(compositor.ids.count)")
            }
        }
        XCTAssertGreaterThan(admittedTotal, 6)
    }

    func testAdmissionIsStableAcrossFrames() {
        for seed: UInt64 in 1...6 {
            let (dom, document, animator, declarations, _) = scene(seed: seed, count: 60, animatedShare: 70)
            let host = CALayer()
            host.bounds = CGRect(x: 0, y: 0, width: side, height: side)
            host.isGeometryFlipped = true
            let compositor = WidgetCompositor(host: host)
            var history: [Set<Int>] = []
            for _ in 0..<4 {
                compositor.update(document: document, animator: animator, dom: dom, declarations: declarations, scale: 2, space: CGColorSpace(name: CGColorSpace.sRGB)!)
                history.append(Set(compositor.ids))
            }
            XCTAssertEqual(Set(history).count, 1, "seed=\(seed) sizes=\(history.map { $0.count })")
            XCTAssertFalse(history[0].isEmpty, "seed=\(seed)")
        }
    }
}

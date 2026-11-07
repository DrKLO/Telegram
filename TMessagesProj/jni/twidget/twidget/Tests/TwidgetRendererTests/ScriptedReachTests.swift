import XCTest
@testable import TwidgetRenderer

final class ScriptedReachTests: XCTestCase {
    private var window: NSWindow?
    private var scheduled: [() -> Void] = []

    private func renderer(css: String) -> LiteHTMLWidgetRenderer {
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 400, height: 240), styleMask: [.borderless], backing: .buffered, defer: false)
        self.window = window
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 400)
        renderer.schedule = { [weak self] in self?.scheduled.append($0) }
        window.contentView?.addSubview(renderer.view)
        renderer.view.frame = NSRect(x: 0, y: 0, width: 400, height: 240)
        renderer.load()
        send(renderer, [
            ["op": "el", "id": 1, "tag": "body", "attrs": [], "adopt": "body"],
            ["op": "el", "id": 2, "tag": "style", "attrs": []],
            ["op": "css", "id": 2, "text": "body { margin: 0; } .f { position: relative; width: 400px; height: 200px; overflow: hidden; background: #000; } .b { position: absolute; left: 0; top: 0; width: 20px; height: 20px; background: #f00; } .bar { position: absolute; left: 300px; top: 0; width: 100px; height: 200px; background: #00f; } " + css],
            ["op": "ins", "id": 2, "parent": 1],
            ["op": "el", "id": 3, "tag": "div", "attrs": [["class", "f"]]],
            ["op": "ins", "id": 3, "parent": 1],
            ["op": "el", "id": 4, "tag": "div", "attrs": [["class", "b"]]],
            ["op": "ins", "id": 4, "parent": 3],
            ["op": "el", "id": 5, "tag": "div", "attrs": [["class", "bar"]]],
            ["op": "ins", "id": 5, "parent": 3],
        ])
        for step in 0..<4 {
            move(renderer, x: 100 + CGFloat(step), y: 90)
        }
        return renderer
    }

    private func send(_ renderer: LiteHTMLWidgetRenderer, _ ops: [[String: Any]]) {
        let data = try! JSONSerialization.data(withJSONObject: ["type": "ops", "ops": ops])
        renderer.send(String(data: data, encoding: .utf8)!)
        while !scheduled.isEmpty {
            scheduled.removeFirst()()
        }
    }

    private func move(_ renderer: LiteHTMLWidgetRenderer, x: CGFloat, y: CGFloat) {
        send(renderer, [["op": "style", "id": 4, "name": "transform", "value": "translate(\(x)px,\(y)px)"]])
    }

    private func pixel(_ image: NSImage, x: CGFloat, y: CGFloat) -> (red: CGFloat, blue: CGFloat) {
        let rep = NSBitmapImageRep(cgImage: image.cgImage(forProposedRect: nil, context: nil, hints: nil)!)
        let scale = CGFloat(rep.pixelsWide) / image.size.width
        let color = rep.colorAt(x: Int(x * scale), y: Int(y * scale))!.usingColorSpace(.sRGB)!
        return (color.redComponent, color.blueComponent)
    }

    func testMovesThroughFreeSpaceStayOnTheFastPath() {
        let renderer = self.renderer(css: "")
        XCTAssertEqual(renderer.compositedCount, 1)
        let layouts = renderer.layoutCount
        move(renderer, x: 200, y: 20)
        move(renderer, x: 10, y: 170)
        move(renderer, x: -10, y: 185)
        XCTAssertEqual(renderer.layoutCount, layouts)
        XCTAssertGreaterThanOrEqual(renderer.rereachCount, 2)
        let image = renderer.snapshot()!
        XCTAssertGreaterThan(pixel(image, x: 5, y: 195).red, 0.9)
        renderer.destroy()
    }

    func testScriptedScaleUsesTheStylesheetTransformOrigin() throws {
        let renderer = self.renderer(css: ".b{left:20px;top:20px;width:100px;transform-origin:right center}.bar{display:none}")
        defer { renderer.destroy() }
        send(renderer, [["op": "style", "id": 4, "name": "transform", "value": "scaleX(0.25)"]])
        XCTAssertEqual(renderer.compositedCount, 1)
        let image = try XCTUnwrap(renderer.snapshot())
        XCTAssertGreaterThan(pixel(image, x: 105, y: 30).red, 0.9)
        XCTAssertLessThan(pixel(image, x: 65, y: 30).red, 0.1)
        send(renderer, [["op": "style", "id": 4, "name": "transform-origin", "value": "left top"]])
        let changed = try XCTUnwrap(renderer.snapshot())
        XCTAssertGreaterThan(pixel(changed, x: 30, y: 30).red, 0.9)
        XCTAssertLessThan(pixel(changed, x: 105, y: 30).red, 0.1)
    }

    func testPromotedTransformReceivesClicksAtItsVisiblePosition() throws {
        let renderer = self.renderer(css: "")
        defer { renderer.destroy() }
        XCTAssertEqual(renderer.compositedCount, 1)
        move(renderer, x: 200, y: 20)
        renderer.listened = ["pointerdown", "pointerup", "click"]
        var events: [[String: Any]] = []
        renderer.onMessage = { message in
            if let payload = try? JSONSerialization.jsonObject(with: Data(message.utf8)) as? [String: Any], let event = payload["ev"] as? [String: Any] {
                events.append(event)
            }
        }
        let event = try XCTUnwrap(NSEvent.mouseEvent(with: .leftMouseDown, location: .zero, modifierFlags: [], timestamp: 0, windowNumber: window!.windowNumber, context: nil, eventNumber: 1, clickCount: 1, pressure: 1))
        renderer.handleMouseDown(CGPoint(x: 210, y: 30), event: event)
        renderer.handleMouseUp(CGPoint(x: 210, y: 30), event: event)
        for type in ["pointerdown", "pointerup", "click"] {
            XCTAssertEqual(events.last { $0["type"] as? String == type }?["targetId"] as? Int, 4, type)
        }
    }

    func testMoveUnderALaterSiblingRendersAgain() {
        let renderer = self.renderer(css: "")
        XCTAssertEqual(renderer.compositedCount, 1)
        let layouts = renderer.layoutCount
        move(renderer, x: 320, y: 90)
        XCTAssertGreaterThan(renderer.layoutCount, layouts)
        let image = renderer.snapshot()!
        XCTAssertGreaterThan(pixel(image, x: 330, y: 100).blue, 0.9)
        XCTAssertLessThan(pixel(image, x: 330, y: 100).red, 0.1)
        renderer.destroy()
    }

    func testLeavingTheCornerFreeBandKeepsTheRoundedClip() {
        let renderer = self.renderer(css: ".f { border-radius: 40px; } .bar { display: none; }")
        XCTAssertEqual(renderer.compositedCount, 1)
        let layouts = renderer.layoutCount
        move(renderer, x: 0, y: 0)
        XCTAssertEqual(renderer.layoutCount, layouts)
        let image = renderer.snapshot()!
        XCTAssertLessThan(pixel(image, x: 1, y: 1).red, 0.1)
        XCTAssertGreaterThan(pixel(image, x: 17, y: 17).red, 0.9)
        renderer.destroy()
    }

    func testDisabledRereachFallsBack() {
        let renderer = self.renderer(css: "")
        let layouts = renderer.layoutCount
        move(renderer, x: 200, y: 20)
        XCTAssertEqual(renderer.rereachCount > 0, WidgetCompositor.rereachEnabled)
        XCTAssertEqual(renderer.layoutCount == layouts, WidgetCompositor.rereachEnabled)
        renderer.destroy()
    }
}

final class TransformFastPathTests: XCTestCase {
    func testTranslationMatchesTheGeneralParser() {
        var state: UInt64 = 0x2545F4914F6CDD1D
        func next() -> UInt64 {
            state ^= state << 13
            state ^= state >> 7
            state ^= state << 17
            return state
        }
        let pieces = ["translate(", "1", "-2.5", "+3", "0", ".5", "1e2", "px", "PX", "%", ",", " ", ")", "(", "-", ".", "x", "var(--a)", "calc(1px)", "translateX(", "--"]
        var fast = 0
        for _ in 0..<20000 {
            var text = next() % 3 == 0 ? "" : "translate("
            if next() % 2 == 0 {
                let numbers = ["1", "-2.5", "+3", "0", ".5", "1e2", "12.", "-0", "1.2.3", "", "-"]
                let units = ["px", "px", "px", "PX", "%", ""]
                let separators = [",", ", ", " , ", " ", ",,", ""]
                text += numbers[Int(next() % UInt64(numbers.count))] + units[Int(next() % UInt64(units.count))]
                if next() % 4 != 0 {
                    text += separators[Int(next() % UInt64(separators.count))] + numbers[Int(next() % UInt64(numbers.count))] + units[Int(next() % UInt64(units.count))]
                }
                text += [")", " )", "", ")x"][Int(next() % 4)]
            } else {
                for _ in 0..<(next() % 9) {
                    text += pieces[Int(next() % UInt64(pieces.count))]
                }
                if next() % 2 == 0 {
                    text += ")"
                }
            }
            let box = CGSize(width: 40, height: 20)
            XCTAssertEqual(CSSTransform.needsResolving(text), text.contains("var(") || text.contains("calc("), text)
            guard let translation = CSSTransform.translation(text) else {
                continue
            }
            fast += 1
            XCTAssertEqual(translation, CSSTransform.parse(" " + text, box: box), text)
        }
        XCTAssertGreaterThan(fast, 100)
        XCTAssertEqual(CSSTransform.translation("translate(12.5px,-3px)"), CGAffineTransform(translationX: 12.5, y: -3))
        XCTAssertEqual(CSSTransform.translation("translate(7px)"), CGAffineTransform(translationX: 7, y: 0))
        XCTAssertNil(CSSTransform.translation("translate(1px 2px)"))
        XCTAssertNil(CSSTransform.translation("translate(1px,2px,3px)"))
    }
}

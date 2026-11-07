import XCTest
import AppKit
@testable import TwidgetRenderer

final class InputFrameHoldTests: XCTestCase {
    private func pump(_ seconds: TimeInterval, step: TimeInterval = 0.005, until done: () -> Bool = { false }) {
        let deadline = Date().addingTimeInterval(seconds)
        while !done() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(step))
        }
    }

    private func send(_ renderer: LiteHTMLWidgetRenderer, _ ops: [[String: Any]]) {
        let data = try! JSONSerialization.data(withJSONObject: ["type": "ops", "ops": ops])
        renderer.send(String(data: data, encoding: .utf8)!)
    }

    func testInputJoinsAnAlreadyRequestedAnimationFrame() {
        _ = NSApplication.shared
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 200, height: 160), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.orderFrontRegardless()
        defer { window.orderOut(nil) }
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 200)
        defer { renderer.destroy() }
        renderer.view.frame = window.contentView!.bounds
        window.contentView?.addSubview(renderer.view)
        renderer.load()
        send(renderer, [["op": "el", "id": 1, "tag": "body", "adopt": "body"]])
        pump(2, until: { !renderer.isPreparingTiles && renderer.layoutCount > 0 })
        var posted = false
        renderer.onMessage = { if $0.hasPrefix("{\"type\":\"frame\"") { posted = true } }
        renderer.send("{\"type\":\"requestFrame\"}")
        pump(1, until: { posted })
        XCTAssertTrue(posted)
        renderer.send("{\"type\":\"requestFrame\"}")
        renderer.noteUserInput()
        XCTAssertTrue(renderer.holdsForFrame)
        let before = renderer.layoutCount
        send(renderer, [["op": "el", "id": 3, "tag": "div", "attrs": [["style", "width:22px;height:22px;background:red"]]], ["op": "ins", "id": 3, "parent": 1]])
        XCTAssertEqual(renderer.layoutCount, before)
        pump(0.2, until: { !renderer.holdsForFrame })
        XCTAssertFalse(renderer.holdsForFrame)
        XCTAssertGreaterThan(renderer.layoutCount, before)
    }

    func testClickOpsWaitForTheRequestedFrameAnswer() {
        _ = NSApplication.shared
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 300, height: 240), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.orderFrontRegardless()
        defer { window.orderOut(nil) }
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 300)
        var frames = 0
        renderer.onMessage = { json in
            if json.hasPrefix("{\"type\":\"frame\"") {
                frames += 1
            }
        }
        renderer.view.frame = NSRect(x: 0, y: 0, width: 300, height: 240)
        window.contentView?.addSubview(renderer.view)
        renderer.load()
        send(renderer, [
            ["op": "el", "id": 1, "tag": "body", "attrs": [], "adopt": "body"],
            ["op": "el", "id": 2, "tag": "style", "attrs": []],
            ["op": "css", "id": 2, "text": "body { margin: 0; background-color: #fff; } [hidden] { display: none !important; } .faq { width: 300px; } .q { height: 20px; background-color: #ddd; } .a { height: 60px; background-color: #000; } @keyframes slide-down { from { opacity: 0; transform: translateY(-10px) } to { opacity: 1; transform: none } } .faq.slide-down { animation: slide-down 240ms cubic-bezier(.33,1,.68,1); }"],
            ["op": "ins", "id": 2, "parent": 1],
            ["op": "el", "id": 3, "tag": "div", "attrs": [["class", "faq"]]],
            ["op": "ins", "id": 3, "parent": 1],
            ["op": "el", "id": 4, "tag": "div", "attrs": [["class", "q"]]],
            ["op": "ins", "id": 4, "parent": 3],
            ["op": "el", "id": 5, "tag": "div", "attrs": [["class", "a"], ["hidden", ""]]],
            ["op": "ins", "id": 5, "parent": 3],
            ["op": "el", "id": 6, "tag": "div", "attrs": [["class", "q"]]],
            ["op": "ins", "id": 6, "parent": 3],
            ["op": "el", "id": 7, "tag": "div", "attrs": [["class", "a"], ["hidden", ""]]],
            ["op": "ins", "id": 7, "parent": 3],
        ])
        pump(10, until: { !renderer.isPreparingTiles && renderer.heldOpBatches == 0 && renderer.layoutCount > 0 })
        pump(0.3)

        func click(open: Int, close: Int?) -> (layouts: Int, animating: Bool) {
            renderer.noteUserInput()
            renderer.send("{\"type\":\"requestFrame\"}")
            var ops: [[String: Any]] = [["op": "attr", "id": 3, "name": "class", "value": "faq"], ["op": "rattr", "id": open, "name": "hidden"]]
            if let close = close {
                ops.append(["op": "attr", "id": close, "name": "hidden", "value": ""])
            }
            let before = renderer.layoutCount
            let posted = frames
            send(renderer, ops)
            var layoutsEarly = 0
            let deadline = Date().addingTimeInterval(1)
            while frames == posted && Date() < deadline {
                RunLoop.main.run(until: Date().addingTimeInterval(0.001))
                layoutsEarly = renderer.layoutCount - before
            }
            XCTAssertGreaterThan(frames, posted)
            pump(0.003)
            layoutsEarly = renderer.layoutCount - before
            let answered = renderer.layoutCount
            XCTAssertTrue(renderer.holdsForFrame)
            send(renderer, [["op": "attr", "id": 3, "name": "class", "value": "faq slide-down"]])
            pump(0.03)
            let animating = renderer.animatedElementIDs.contains(3)
            pump(0.6)
            XCTAssertGreaterThan(renderer.layoutCount, answered)
            return (layoutsEarly, animating)
        }

        let first = click(open: 5, close: nil)
        XCTAssertEqual(first.layouts, 0)
        XCTAssertTrue(first.animating)
        XCTAssertEqual(click(open: 7, close: 5).layouts, 0)
        pump(0.3)
        renderer.noteUserInput()
        renderer.send("{\"type\":\"requestFrame\"}")
        let silent = renderer.layoutCount
        send(renderer, [["op": "attr", "id": 3, "name": "class", "value": "faq"], ["op": "attr", "id": 7, "name": "hidden", "value": ""]])
        XCTAssertTrue(renderer.holdsForFrame)
        pump(0.15, step: 0.002, until: { renderer.layoutCount > silent })
        XCTAssertGreaterThan(renderer.layoutCount, silent)
        XCTAssertFalse(renderer.holdsForFrame)
        renderer.destroy()
    }
}

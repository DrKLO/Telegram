import AppKit
import XCTest
@testable import TwidgetRenderer

private final class KeyboardEventProbe: NSEvent {
    override var keyCode: UInt16 { 0 }
    override var characters: String? {
        XCTAssertTrue(Thread.isMainThread)
        return "Å"
    }
    override var charactersIgnoringModifiers: String? {
        XCTAssertTrue(Thread.isMainThread)
        return "a"
    }
    override var isARepeat: Bool { true }
    override var modifierFlags: NSEvent.ModifierFlags { [.shift, .option] }
}

final class WidgetKeyboardTests: XCTestCase {
    func testKeyboardCharactersAreDecodedBeforeDispatchingToTheRendererQueue() {
        _ = NSApplication.shared
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 200)
        let queue = DispatchQueue(label: "widget.keyboard.test")
        renderer.listened = ["keydown", "keyup"]
        renderer.isOnExecutor = { !Thread.isMainThread }
        renderer.scheduleUrgent = { block in queue.async(execute: block) }
        let posted = expectation(description: "keyboard events")
        posted.expectedFulfillmentCount = 2
        renderer.onMessage = { json in
            XCTAssertFalse(Thread.isMainThread)
            let message = try! JSONSerialization.jsonObject(with: Data(json.utf8)) as! [String: Any]
            let event = message["ev"] as! [String: Any]
            XCTAssertEqual(event["key"] as? String, "Å")
            XCTAssertEqual(event["code"] as? String, "KeyA")
            XCTAssertEqual(event["repeat"] as? Bool, true)
            XCTAssertEqual(event["modifiers"] as? Int, 9)
            posted.fulfill()
        }
        renderer.view.keyDown(with: KeyboardEventProbe())
        renderer.view.keyUp(with: KeyboardEventProbe())
        wait(for: [posted], timeout: 3)
        queue.sync {}
        renderer.isOnExecutor = { Thread.isMainThread }
        renderer.destroy()
    }
}

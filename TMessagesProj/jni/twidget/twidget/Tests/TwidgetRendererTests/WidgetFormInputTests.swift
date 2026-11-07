import XCTest
import AppKit
import LiteHTML
@testable import TwidgetRenderer

final class WidgetFormInputTests: XCTestCase {
    private func pump(_ seconds: TimeInterval, until done: () -> Bool = { false }) {
        let deadline = Date().addingTimeInterval(seconds)
        while !done() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
    }

    private func make(_ ops: [[String: Any]]) -> (LiteHTMLWidgetRenderer, NSWindow, () -> [[String: Any]]) {
        _ = NSApplication.shared
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 400, height: 300), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.orderFrontRegardless()
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 400)
        var events: [[String: Any]] = []
        renderer.onMessage = { json in
            if let object = try? JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any], object["type"] as? String == "event", let ev = object["ev"] as? [String: Any] {
                events.append(ev)
            }
        }
        renderer.view.frame = NSRect(x: 0, y: 0, width: 400, height: 300)
        window.contentView?.addSubview(renderer.view)
        renderer.load()
        let base: [[String: Any]] = [["op": "el", "id": 2, "tag": "html", "attrs": [], "adopt": "html"], ["op": "el", "id": 7, "tag": "body", "attrs": [], "adopt": "body"]]
        let data = try! JSONSerialization.data(withJSONObject: ["type": "ops", "ops": base + ops])
        renderer.send(String(data: data, encoding: .utf8)!)
        pump(2, until: { renderer.elementBounds().count > 2 })
        return (renderer, window, { events })
    }

    private func click(_ renderer: LiteHTMLWidgetRenderer, _ id: Int) {
        let box = renderer.elementBounds()[id] ?? .zero
        let point = CGPoint(x: box.midX, y: box.midY)
        let event = NSEvent.mouseEvent(with: .leftMouseDown, location: renderer.view.convert(point, to: nil), modifierFlags: [], timestamp: 0, windowNumber: renderer.view.window?.windowNumber ?? 0, context: nil, eventNumber: 0, clickCount: 1, pressure: 1)!
        renderer.handleMouseDown(point, event: event)
    }

    func testTextareaScrollSurvivesBlurAndRefocusAndAcceptsScriptUpdates() throws {
        let value = (1...16).map { String(format: "Line %02d — scroll test", $0) }.joined(separator: "\n")
        let (renderer, window, _) = make([
            ["op": "el", "id": 10, "tag": "textarea", "attrs": [["style", "display:block;box-sizing:border-box;width:260px;height:120px;padding:10px;border:2px solid blue;margin-top:97.92px;font:16px/24px Courier"]]],
            ["op": "ins", "parent": 7, "id": 10],
            ["op": "prop", "id": 10, "name": "value", "value": value]
        ])
        defer { renderer.destroy(); window.orderOut(nil) }
        click(renderer, 10)
        let view = renderer.view as! WidgetDocumentView
        pump(2, until: { view.formEditor?.textView != nil })
        let editor = try XCTUnwrap(view.formEditor?.textView)
        editor.moveToEndOfDocument(nil)
        editor.scrollRangeToVisible(editor.selectedRange())
        let scroll = try XCTUnwrap(editor.enclosingScrollView)
        let offset = scroll.contentView.bounds.origin.y
        XCTAssertEqual(offset, 288, accuracy: 0.5)
        pump(1, until: { abs(renderer.scrolling.offset(10).y - offset) < 0.5 })
        XCTAssertEqual(renderer.scrolling.offset(10).y, offset, accuracy: 0.5)
        renderer.endEditing()
        pump(1, until: { view.formEditor == nil })
        XCTAssertEqual(renderer.scrolling.offset(10).y, offset, accuracy: 0.5)
        click(renderer, 10)
        pump(2, until: { view.formEditor?.textView != nil })
        let restored = try XCTUnwrap(view.formEditor?.textView?.enclosingScrollView)
        pump(0.1)
        XCTAssertEqual(restored.contentView.bounds.origin.y, offset, accuracy: 0.5)
        XCTAssertEqual(renderer.scrolling.offset(10).y, offset, accuracy: 0.5)
        renderer.send("{\"type\":\"ops\",\"ops\":[{\"op\":\"prop\",\"id\":10,\"name\":\"scrollTop\",\"value\":24}]}")
        pump(1, until: { abs(restored.contentView.bounds.origin.y - 24) < 0.5 })
        XCTAssertEqual(restored.contentView.bounds.origin.y, 24, accuracy: 0.5)
        XCTAssertTrue(window.firstResponder === view.formEditor?.textView)
    }

    func testFirstTextareaClickPlacesCaretAtTheClickedLine() throws {
        let (renderer, window, _) = make([
            ["op": "el", "id": 10, "tag": "textarea", "attrs": [["style", "display:block;width:200px;height:96px;padding:10px;border:0;font:16px/24px Courier"]]],
            ["op": "ins", "parent": 7, "id": 10],
            ["op": "prop", "id": 10, "name": "value", "value": "alpha\nbravo\ncharlie"]
        ])
        defer { renderer.destroy(); window.orderOut(nil) }
        let box = try XCTUnwrap(renderer.elementBounds()[10])
        let point = CGPoint(x: box.minX + 12, y: box.minY + 46)
        let event = try XCTUnwrap(NSEvent.mouseEvent(with: .leftMouseDown, location: renderer.view.convert(point, to: nil), modifierFlags: [], timestamp: 0, windowNumber: window.windowNumber, context: nil, eventNumber: 0, clickCount: 1, pressure: 1))
        renderer.handleMouseDown(point, event: event)
        let view = renderer.view as! WidgetDocumentView
        pump(2, until: { view.formEditor?.textView != nil })
        let text = try XCTUnwrap(view.formEditor?.textView)
        XCTAssertEqual(text.selectedRange(), NSRange(location: 6, length: 0))
        text.insertText("!", replacementRange: text.selectedRange())
        pump(1, until: { renderer.dom.node(10)?.formValue == "alpha\n!bravo\ncharlie" })
        XCTAssertEqual(renderer.dom.node(10)?.formValue, "alpha\n!bravo\ncharlie")
    }

    func testTextareaTrailingNewlineKeepsLastLineScrollable() throws {
        let value = (1...16).map { "Line \($0)" }.joined(separator: "\n") + "\n"
        let (renderer, window, _) = make([
            ["op": "el", "id": 10, "tag": "textarea", "attrs": [["style", "display:block;width:200px;height:96px;padding:10px;font:16px/24px Courier"]]],
            ["op": "ins", "parent": 7, "id": 10],
            ["op": "prop", "id": 10, "name": "value", "value": value]
        ])
        defer { renderer.destroy(); window.orderOut(nil) }
        renderer.send("{\"type\":\"ops\",\"ops\":[{\"op\":\"prop\",\"id\":10,\"name\":\"scrollTop\",\"value\":1000}]}")
        pump(1, until: { renderer.scrolling.offset(10).y > 300 })
        XCTAssertEqual(renderer.scrolling.offset(10).y, 312, accuracy: 0.5)
        click(renderer, 10)
        let view = renderer.view as! WidgetDocumentView
        pump(2, until: { view.formEditor?.textView != nil })
        let text = try XCTUnwrap(view.formEditor?.textView)
        text.moveToEndOfDocument(nil)
        text.scrollRangeToVisible(text.selectedRange())
        XCTAssertEqual(try XCTUnwrap(text.enclosingScrollView).contentView.bounds.origin.y, 312, accuracy: 0.5)
    }

    func testFirstPointerDragSelectsTextInInputsAndTextareas() throws {
        for tag in ["input", "textarea"] {
            let (renderer, window, _) = make([
                ["op": "el", "id": 10, "tag": tag, "attrs": [["style", "display:block;width:240px;height:48px;padding:10px;border:0;font:16px/24px Courier"]]],
                ["op": "ins", "parent": 7, "id": 10],
                ["op": "prop", "id": 10, "name": "value", "value": "alpha bravo charlie"]
            ])
            defer { renderer.destroy(); window.orderOut(nil) }
            let box = try XCTUnwrap(renderer.elementBounds()[10])
            let advance = ("a" as NSString).size(withAttributes: [.font: NSFont(name: "Courier", size: 16)!]).width
            let start = CGPoint(x: box.minX + 10 + 6 * advance, y: tag == "input" ? box.midY : box.minY + 22)
            let end = CGPoint(x: box.minX + 10 + 11 * advance, y: start.y)
            func event(_ type: NSEvent.EventType, _ point: CGPoint) -> NSEvent {
                return NSEvent.mouseEvent(with: type, location: renderer.view.convert(point, to: nil), modifierFlags: [], timestamp: 0, windowNumber: window.windowNumber, context: nil, eventNumber: 0, clickCount: 1, pressure: 1)!
            }
            renderer.handleMouseDown(start, event: event(.leftMouseDown, start))
            let view = renderer.view as! WidgetDocumentView
            pump(2, until: { view.formEditor != nil })
            let editor = try XCTUnwrap(view.formEditor)
            let text = try XCTUnwrap(editor.textView ?? editor.field?.currentEditor() as? NSTextView)
            renderer.handleMouseDragged(end, event: event(.leftMouseDragged, end))
            pump(1, until: { text.selectedRange().length > 0 })
            renderer.handleMouseUp(end, event: event(.leftMouseUp, end))
            XCTAssertEqual(text.selectedRange(), NSRange(location: 6, length: 5), tag)
            text.insertText("DRAG", replacementRange: text.selectedRange())
            pump(1, until: { renderer.dom.node(10)?.formValue == "alpha DRAG charlie" })
            XCTAssertEqual(renderer.dom.node(10)?.formValue, "alpha DRAG charlie", tag)
        }
    }

    func testReadOnlyInputAllowsPointerSelection() throws {
        let (renderer, window, _) = make([
            ["op": "el", "id": 10, "tag": "input", "attrs": [["readonly", ""], ["style", "display:block;width:200px;height:32px;font:16px/24px Courier"]]],
            ["op": "ins", "parent": 7, "id": 10],
            ["op": "prop", "id": 10, "name": "value", "value": "select this text"]
        ])
        defer { renderer.destroy(); window.orderOut(nil) }
        click(renderer, 10)
        let view = renderer.view as! WidgetDocumentView
        pump(2, until: { view.formEditor != nil })
        let field = try XCTUnwrap(view.formEditor?.field)
        XCTAssertTrue(field.isSelectable)
        XCTAssertFalse(field.isEditable)
        let text = try XCTUnwrap(field.currentEditor() as? NSTextView)
        XCTAssertFalse(text.isEditable)
        XCTAssertTrue(window.firstResponder === text)
    }

    func testInitialMultipleClicksSelectWordsAndParagraphs() throws {
        for clicks in [2, 3] {
            let value = "alpha bravo charlie\ndelta echo foxtrot"
            let (renderer, window, _) = make([
                ["op": "el", "id": 10, "tag": "textarea", "attrs": [["style", "display:block;width:240px;height:96px;padding:10px;border:0;font:16px/24px Courier"]]],
                ["op": "ins", "parent": 7, "id": 10],
                ["op": "prop", "id": 10, "name": "value", "value": value]
            ])
            defer { renderer.destroy(); window.orderOut(nil) }
            let box = try XCTUnwrap(renderer.elementBounds()[10])
            let advance = ("a" as NSString).size(withAttributes: [.font: NSFont(name: "Courier", size: 16)!]).width
            let point = CGPoint(x: box.minX + 10 + 8 * advance, y: box.minY + 22)
            let event = try XCTUnwrap(NSEvent.mouseEvent(with: .leftMouseDown, location: renderer.view.convert(point, to: nil), modifierFlags: [], timestamp: 0, windowNumber: window.windowNumber, context: nil, eventNumber: 0, clickCount: clicks, pressure: 1))
            renderer.handleMouseDown(point, event: event)
            let view = renderer.view as! WidgetDocumentView
            pump(2, until: { view.formEditor?.textView != nil })
            let text = try XCTUnwrap(view.formEditor?.textView)
            XCTAssertEqual((text.string as NSString).substring(with: text.selectedRange()), clicks == 2 ? "bravo" : "alpha bravo charlie\n")
        }
    }

    func testTextareaScrollClampsAfterContentShrinks() throws {
        let value = (1...16).map { "Line \($0)" }.joined(separator: "\n")
        let (renderer, window, _) = make([
            ["op": "el", "id": 10, "tag": "textarea", "attrs": [["style", "display:block;width:200px;height:96px;padding:10px;font:16px/24px Courier"]]],
            ["op": "ins", "parent": 7, "id": 10],
            ["op": "prop", "id": 10, "name": "value", "value": value]
        ])
        defer { renderer.destroy(); window.orderOut(nil) }
        renderer.send("{\"type\":\"ops\",\"ops\":[{\"op\":\"prop\",\"id\":10,\"name\":\"scrollTop\",\"value\":1000}]}")
        pump(1, until: { renderer.scrolling.offset(10).y > 200 })
        XCTAssertEqual(renderer.scrolling.offset(10).y, 288, accuracy: 0.5)
        click(renderer, 10)
        let view = renderer.view as! WidgetDocumentView
        pump(2, until: { view.formEditor?.textView != nil })
        let text = try XCTUnwrap(view.formEditor?.textView)
        text.selectAll(nil)
        text.insertText("Short text", replacementRange: text.selectedRange())
        pump(1, until: { renderer.scrolling.offset(10).y == 0 })
        XCTAssertEqual(renderer.scrolling.offset(10).y, 0)
        XCTAssertEqual(text.enclosingScrollView?.contentView.bounds.origin.y, 0)
        XCTAssertTrue(window.firstResponder === text)
        XCTAssertEqual(renderer.dom.node(10)?.formValue, "Short text")
    }

    func testNativeClicksRequestSandboxDefaultActions() throws {
        let (renderer, window, events) = make([
            ["op": "el", "id": 10, "tag": "button", "attrs": [["type", "submit"], ["style", "width:100px;height:40px"]]],
            ["op": "ins", "parent": 7, "id": 10]
        ])
        defer { renderer.destroy(); window.orderOut(nil) }
        click(renderer, 10)
        let box = try XCTUnwrap(renderer.elementBounds()[10])
        let event = try XCTUnwrap(NSEvent.mouseEvent(with: .leftMouseUp, location: .zero, modifierFlags: [], timestamp: 0, windowNumber: window.windowNumber, context: nil, eventNumber: 0, clickCount: 1, pressure: 0))
        renderer.handleMouseUp(CGPoint(x: box.midX, y: box.midY), event: event)
        let clicks = events().filter { $0["type"] as? String == "click" }
        XCTAssertEqual(clicks.count, 1)
        XCTAssertEqual(clicks.first?["defaultActions"] as? Bool, true)
        XCTAssertFalse(events().contains { $0["type"] as? String == "submit" })
    }

    func testTextareaEnterWaitsForCancellationAndIgnoresStaleReplies() throws {
        let (renderer, window, events) = make([
            ["op": "el", "id": 10, "tag": "textarea", "attrs": [["style", "width:200px;height:80px"]]],
            ["op": "ins", "parent": 7, "id": 10],
            ["op": "prop", "id": 10, "name": "value", "value": "hello"]
        ])
        defer { renderer.destroy(); window.orderOut(nil) }
        renderer.listened = ["keyup"]
        click(renderer, 10)
        let view = renderer.view as! WidgetDocumentView
        pump(2, until: { view.formEditor != nil })
        let editor = try XCTUnwrap(view.formEditor)
        let text = try XCTUnwrap(editor.textView as? WidgetEditorTextView)
        text.setSelectedRange(NSRange(location: 5, length: 0))
        XCTAssertTrue(editor.textView(text, doCommandBy: #selector(NSResponder.insertNewline(_:))))
        XCTAssertEqual(text.string, "hello")
        XCTAssertTrue(text.waitingForCommand)
        let request = try XCTUnwrap(events().last { $0["type"] as? String == "keydown" })
        XCTAssertEqual(request["editorGeneration"] as? Int, editor.generation)
        let token = try XCTUnwrap(request["editorCommand"] as? Int)
        editor.completeEnter(token: token + 1, allowed: true)
        XCTAssertTrue(text.waitingForCommand)
        let node = try XCTUnwrap(renderer.dom.node(10))
        renderer.completeEditorCommand(node, [editor.generation - 1, token, 1])
        pump(0.03)
        XCTAssertTrue(text.waitingForCommand)
        renderer.completeEditorCommand(node, [editor.generation, token, 0])
        pump(1, until: { !text.waitingForCommand })
        XCTAssertEqual(text.string, "hello")
        XCTAssertEqual(events().last?["type"] as? String, "keyup")
        XCTAssertFalse(events().contains { $0["type"] as? String == "input" })
        editor.completeEnter(token: token, allowed: true)
        XCTAssertEqual(text.string, "hello")
    }

    func testTextareaEnterFallsBackWhenTheScriptNeverReplies() throws {
        let (renderer, window, events) = make([
            ["op": "el", "id": 10, "tag": "textarea", "attrs": [["style", "width:200px;height:80px"]]],
            ["op": "ins", "parent": 7, "id": 10],
            ["op": "prop", "id": 10, "name": "value", "value": "hello"]
        ])
        defer { renderer.destroy(); window.orderOut(nil) }
        click(renderer, 10)
        let view = renderer.view as! WidgetDocumentView
        pump(2, until: { view.formEditor != nil })
        let editor = try XCTUnwrap(view.formEditor)
        let text = try XCTUnwrap(editor.textView as? WidgetEditorTextView)
        text.setSelectedRange(NSRange(location: 5, length: 0))
        XCTAssertTrue(editor.textView(text, doCommandBy: #selector(NSResponder.insertNewline(_:))))
        XCTAssertTrue(text.waitingForCommand)
        let token = try XCTUnwrap(events().last { $0["type"] as? String == "keydown" }?["editorCommand"] as? Int)
        pump(2, until: { !text.waitingForCommand })
        XCTAssertFalse(text.waitingForCommand)
        XCTAssertEqual(text.string, "hello\n")
        editor.completeEnter(token: token, allowed: true)
        XCTAssertEqual(text.string, "hello\n")
    }

    func testTextareaEnterReplacesSelectionBeforeQueuedTyping() throws {
        let (renderer, window, events) = make([
            ["op": "el", "id": 10, "tag": "textarea", "attrs": [["style", "width:200px;height:80px"]]],
            ["op": "ins", "parent": 7, "id": 10],
            ["op": "prop", "id": 10, "name": "value", "value": "a😀b"]
        ])
        defer { renderer.destroy(); window.orderOut(nil) }
        click(renderer, 10)
        let view = renderer.view as! WidgetDocumentView
        pump(2, until: { view.formEditor != nil })
        let editor = try XCTUnwrap(view.formEditor)
        let text = try XCTUnwrap(editor.textView as? WidgetEditorTextView)
        text.setSelectedRange(NSRange(location: 1, length: 2))
        XCTAssertTrue(editor.textView(text, doCommandBy: #selector(NSResponder.insertNewline(_:))))
        let request = try XCTUnwrap(events().last { $0["type"] as? String == "keydown" })
        let token = try XCTUnwrap(request["editorCommand"] as? Int)
        let key = try XCTUnwrap(NSEvent.keyEvent(with: .keyDown, location: .zero, modifierFlags: [], timestamp: 0, windowNumber: window.windowNumber, context: nil, characters: "x", charactersIgnoringModifiers: "x", isARepeat: false, keyCode: 7))
        text.keyDown(with: key)
        XCTAssertEqual(text.string, "a😀b")
        editor.completeEnter(token: token, allowed: true)
        XCTAssertEqual(text.string, "a\nxb")
        XCTAssertEqual(text.selectedRange(), NSRange(location: 3, length: 0))
        XCTAssertEqual(renderer.dom.node(10)?.formValue, "a\nxb")
        XCTAssertEqual(events().filter { $0["type"] as? String == "input" }.compactMap { $0["value"] as? String }, ["a\nb", "a\nxb"])
        XCTAssertEqual(events().filter { $0["type"] as? String == "input" }.compactMap { $0["inputType"] as? String }, ["insertLineBreak", "insertText"])
        XCTAssertTrue(editor.textView(text, doCommandBy: #selector(NSResponder.insertNewline(_:))))
        let pending = try XCTUnwrap(events().last?["editorCommand"] as? Int)
        let shiftedEnter = try XCTUnwrap(NSEvent.keyEvent(with: .keyDown, location: .zero, modifierFlags: [.shift], timestamp: 0, windowNumber: window.windowNumber, context: nil, characters: "\r", charactersIgnoringModifiers: "\r", isARepeat: false, keyCode: 36))
        text.keyDown(with: shiftedEnter)
        editor.completeEnter(token: pending, allowed: false)
        XCTAssertEqual(events().last?["modifiers"] as? Int, 8)
        XCTAssertTrue(text.waitingForCommand)
        editor.completeEnter(token: pending + 1, allowed: false)
        XCTAssertTrue(editor.textView(text, doCommandBy: #selector(NSResponder.insertNewline(_:))))
        let replacementCommand = try XCTUnwrap(events().last?["editorCommand"] as? Int)
        editor.setText("replacement")
        editor.completeEnter(token: replacementCommand, allowed: true)
        XCTAssertEqual(text.string, "replacement\n")
    }

    func testRangeDragClampsSnapsAndCommitsOnceOutsideTheControl() {
        let (renderer, window, events) = make([
            ["op": "el", "id": 10, "tag": "input", "attrs": [["type", "range"], ["min", "10"], ["max", "90"], ["step", "10"], ["value", "50"], ["style", "width:200px"]]],
            ["op": "ins", "parent": 7, "id": 10]
        ])
        defer { renderer.destroy(); window.orderOut(nil) }
        click(renderer, 10)
        let event = NSEvent.mouseEvent(with: .leftMouseDragged, location: .zero, modifierFlags: [], timestamp: 0, windowNumber: window.windowNumber, context: nil, eventNumber: 0, clickCount: 1, pressure: 1)!
        let box = renderer.elementBounds()[10]!
        renderer.handleMouseDragged(CGPoint(x: box.minX + 130, y: box.midY), event: event)
        XCTAssertEqual(renderer.dom.node(10)?.formValue, "60")
        renderer.handleMouseDragged(CGPoint(x: -100, y: 200), event: event)
        XCTAssertEqual(renderer.dom.node(10)?.formValue, "10")
        renderer.handleMouseUp(CGPoint(x: 600, y: 200), event: event)
        XCTAssertEqual(renderer.dom.node(10)?.formValue, "90")
        XCTAssertEqual(events().filter { $0["type"] as? String == "change" }.count, 1)
        XCTAssertEqual(events().filter { $0["type"] as? String == "input" }.last?["value"] as? String, "90")
        renderer.handleMouseDragged(CGPoint(x: -100, y: 200), event: event)
        XCTAssertEqual(renderer.dom.node(10)?.formValue, "90")
    }

    func testDisabledRangeDoesNotChange() {
        let (renderer, window, events) = make([
            ["op": "el", "id": 10, "tag": "input", "attrs": [["type", "range"], ["disabled", ""], ["value", "25"]]],
            ["op": "ins", "parent": 7, "id": 10]
        ])
        defer { renderer.destroy(); window.orderOut(nil) }
        click(renderer, 10)
        renderer.finishRange(at: CGPoint(x: 500, y: 100))
        XCTAssertNil(renderer.dom.node(10)?.formValue)
        XCTAssertFalse(events().contains { $0["type"] as? String == "input" })
    }

    func testPointerCaptureDeliversReleaseOutsideAndResetsForNextGesture() {
        let (renderer, window, events) = make([
            ["op": "el", "id": 10, "tag": "div", "attrs": [["style", "width:100px;height:60px"]]],
            ["op": "ins", "parent": 7, "id": 10]
        ])
        defer { renderer.destroy(); window.orderOut(nil) }
        renderer.listened = ["pointerdown", "pointermove", "pointerup"]
        click(renderer, 10)
        renderer.send("{\"type\":\"ops\",\"ops\":[{\"op\":\"call\",\"id\":10,\"method\":\"setPointerCapture\",\"args\":[1]}]}")
        let event = NSEvent.mouseEvent(with: .leftMouseUp, location: .zero, modifierFlags: [], timestamp: 0, windowNumber: window.windowNumber, context: nil, eventNumber: 0, clickCount: 1, pressure: 0)!
        renderer.handleMouseDragged(CGPoint(x: 500, y: 500), event: event)
        renderer.handleMouseUp(CGPoint(x: 500, y: 500), event: event)
        XCTAssertEqual(events().last { $0["type"] as? String == "pointermove" }?["targetId"] as? Int, 10)
        let types = events().compactMap { $0["type"] as? String }
        XCTAssertLessThan(types.lastIndex(of: "pointermove")!, types.lastIndex(of: "pointerup")!)
        XCTAssertEqual(events().last { $0["type"] as? String == "pointerup" }?["targetId"] as? Int, 10)
        XCTAssertEqual(events().filter { $0["type"] as? String == "lostpointercapture" }.count, 1)
        renderer.handleMouseDown(CGPoint(x: 300, y: 200), event: event)
        renderer.handleMouseUp(CGPoint(x: 300, y: 200), event: event)
        XCTAssertNotEqual(events().last { $0["type"] as? String == "pointerup" }?["targetId"] as? Int, 10)
    }

    func testNumberInputKeepsItsColorsAndEditorWhileTyping() throws {
        let (renderer, window, events) = make([
            ["op": "el", "id": 10, "tag": "input", "attrs": [["type", "number"], ["placeholder", "Calories"], ["style", "color:rgb(220,230,240);background:#292929;padding:12px;border:0;border-radius:12px"]]],
            ["op": "ins", "parent": 7, "id": 10, "before": NSNull()],
        ])
        defer { renderer.destroy(); window.orderOut(nil) }
        window.appearance = NSAppearance(named: .darkAqua)
        click(renderer, 10)
        let view = renderer.view as! WidgetDocumentView
        pump(2, until: { view.formEditor?.field?.currentEditor() != nil })
        let editor = try XCTUnwrap(view.formEditor?.field?.currentEditor() as? NSTextView)
        let color = NSColor(srgbRed: 220 / 255.0, green: 230 / 255.0, blue: 240 / 255.0, alpha: 1)
        XCTAssertFalse(editor.drawsBackground)
        XCTAssertEqual(editor.backgroundColor.alphaComponent, 0)
        XCTAssertFalse(editor.usesAdaptiveColorMappingForDarkAppearance)
        if let clip = editor.superview as? NSClipView {
            XCTAssertFalse(clip.drawsBackground)
        }
        XCTAssertEqual(editor.textColor?.usingColorSpace(.sRGB), color)
        XCTAssertEqual(editor.insertionPointColor.usingColorSpace(.sRGB), color)
        editor.insertText("1", replacementRange: NSRange(location: NSNotFound, length: 0))
        pump(1, until: { events().contains { $0["type"] as? String == "input" } })
        XCTAssertEqual(renderer.dom.node(10)?.formValue, "1")
        XCTAssertTrue(window.firstResponder === editor)
        XCTAssertFalse(editor.drawsBackground)
        XCTAssertEqual(editor.textColor?.usingColorSpace(.sRGB), color)
    }

    func testTextareaEditorUsesRenderedFontAndLineSpacing() throws {
        let (renderer, window, _) = make([
            ["op": "el", "id": 10, "tag": "textarea", "attrs": [["style", "width:250px;height:150px;font:16px/24px monospace;color:rgb(220,230,240)"]]],
            ["op": "ins", "parent": 7, "id": 10],
            ["op": "prop", "id": 10, "name": "value", "value": "one\ntwo\nthree"]
        ])
        defer { renderer.destroy(); window.orderOut(nil) }
        click(renderer, 10)
        let view = renderer.view as! WidgetDocumentView
        pump(2, until: { view.formEditor?.textView != nil })
        let editor = try XCTUnwrap(view.formEditor?.textView)
        let layout = try XCTUnwrap(editor.layoutManager)
        let container = try XCTUnwrap(editor.textContainer)
        layout.ensureLayout(for: container)
        XCTAssertEqual(editor.font?.familyName, "Courier")
        let first = layout.lineFragmentRect(forGlyphAt: 0, effectiveRange: nil)
        let second = layout.lineFragmentRect(forGlyphAt: 4, effectiveRange: nil)
        XCTAssertEqual(second.minY - first.minY, 24, accuracy: 0.01)
        func assertBaseline(file: StaticString = #filePath, line: UInt = #line) throws {
            let raster = LHDocument(html: renderer.dom.serialize(theme: "light"), css: nil)
            raster.layout(maxWidth: 400, preferredWidth: 400)
            let fragment = try XCTUnwrap(raster.textFragments(maxWidth: 400).first { $0.text == "one" })
            let font = try XCTUnwrap(editor.font)
            let expected = fragment.frame.maxY - (-font.descender).rounded() - font.leading.rounded()
            let actual = editor.convert(.zero, to: view).y + layout.lineFragmentRect(forGlyphAt: 0, effectiveRange: nil).minY + layout.location(forGlyphAt: 0).y
            XCTAssertEqual(actual, expected, accuracy: 0.5, file: file, line: line)
        }
        try assertBaseline()
        editor.setSelectedRange(NSRange(location: 4, length: 3))
        let data = try JSONSerialization.data(withJSONObject: ["type": "ops", "ops": [
            ["op": "style", "id": 10, "name": "font-family", "value": "serif"],
            ["op": "style", "id": 10, "name": "line-height", "value": "30px"],
            ["op": "style", "id": 10, "name": "text-align", "value": "right"]
        ]])
        renderer.send(String(data: data, encoding: .utf8)!)
        pump(1, until: { editor.font?.familyName == "Times" })
        layout.ensureLayout(for: container)
        XCTAssertTrue(view.formEditor?.textView === editor)
        XCTAssertTrue(window.firstResponder === editor)
        XCTAssertEqual(editor.selectedRange(), NSRange(location: 4, length: 3))
        XCTAssertEqual(editor.font?.familyName, "Times")
        XCTAssertEqual(editor.alignment, .right)
        let updatedFirst = layout.lineFragmentRect(forGlyphAt: 0, effectiveRange: nil)
        let updatedSecond = layout.lineFragmentRect(forGlyphAt: 4, effectiveRange: nil)
        XCTAssertEqual(updatedSecond.minY - updatedFirst.minY, 30, accuracy: 0.01)
        try assertBaseline()
        let replacement = try JSONSerialization.data(withJSONObject: ["type": "ops", "ops": [["op": "prop", "id": 10, "name": "value", "value": "new\nlines"]]])
        renderer.send(String(data: replacement, encoding: .utf8)!)
        pump(1, until: { editor.string == "new\nlines" })
        layout.ensureLayout(for: container)
        let replacedFirst = layout.lineFragmentRect(forGlyphAt: 0, effectiveRange: nil)
        let replacedSecond = layout.lineFragmentRect(forGlyphAt: 4, effectiveRange: nil)
        XCTAssertEqual(replacedSecond.minY - replacedFirst.minY, 30, accuracy: 0.01)
        editor.insertText(" added", replacementRange: NSRange(location: NSNotFound, length: 0))
        XCTAssertEqual(editor.string, "new\nlines added")
        XCTAssertTrue(editor.undoManager?.canUndo == true)
        editor.undoManager?.undo()
        XCTAssertEqual(editor.string, "new\nlines")
        XCTAssertEqual(editor.font?.familyName, "Times")
        XCTAssertEqual((editor.typingAttributes[.paragraphStyle] as? NSParagraphStyle)?.minimumLineHeight, 30)
        for readOnly in [true, false] {
            let change = try JSONSerialization.data(withJSONObject: ["type": "ops", "ops": [["op": readOnly ? "attr" : "rattr", "id": 10, "name": "readonly", "value": ""]]])
            renderer.send(String(data: change, encoding: .utf8)!)
            pump(1, until: { editor.isEditable == !readOnly })
            XCTAssertEqual(editor.isEditable, !readOnly)
            XCTAssertTrue(window.firstResponder === editor)
        }
    }

    func testFocusedTextareaFollowsColorChangesWithoutLosingSelection() throws {
        let (renderer, window, _) = make([
            ["op": "el", "id": 10, "tag": "textarea", "attrs": [["style", "width:250px;height:100px;color:rgb(220,230,240)"]]],
            ["op": "ins", "parent": 7, "id": 10],
            ["op": "prop", "id": 10, "name": "value", "value": "Color changes"]
        ])
        defer { renderer.destroy(); window.orderOut(nil) }
        click(renderer, 10)
        let view = renderer.view as! WidgetDocumentView
        pump(2, until: { view.formEditor?.textView != nil })
        let editor = try XCTUnwrap(view.formEditor?.textView)
        editor.setSelectedRange(NSRange(location: 6, length: 7))
        for (css, expected) in [("rgb(20,180,80)", NSColor(srgbRed: 20 / 255.0, green: 180 / 255.0, blue: 80 / 255.0, alpha: 1)), ("rgba(80,100,220,0.4)", NSColor(srgbRed: 80 / 255.0, green: 100 / 255.0, blue: 220 / 255.0, alpha: 0.4)), ("transparent", NSColor(srgbRed: 0, green: 0, blue: 0, alpha: 0))] {
            let data = try JSONSerialization.data(withJSONObject: ["type": "ops", "ops": [["op": "style", "id": 10, "name": "color", "value": css]]])
            renderer.send(String(data: data, encoding: .utf8)!)
            pump(1, until: { editor.textColor?.usingColorSpace(.sRGB) == expected })
            XCTAssertEqual(editor.textColor?.usingColorSpace(.sRGB), expected)
            XCTAssertEqual(editor.insertionPointColor.usingColorSpace(.sRGB), expected)
            XCTAssertEqual(editor.selectedRange(), NSRange(location: 6, length: 7))
            XCTAssertTrue(window.firstResponder === editor)
            XCTAssertFalse(renderer.document?.textFragments(maxWidth: 400).contains { $0.text == "Color" } ?? true)
        }
        let inherited = try JSONSerialization.data(withJSONObject: ["type": "ops", "ops": [
            ["op": "style", "id": 7, "name": "color", "value": "rgb(40,80,160)"],
            ["op": "style", "id": 10, "name": "color", "value": "inherit"]
        ]])
        renderer.send(String(data: inherited, encoding: .utf8)!)
        let inheritedColor = NSColor(srgbRed: 40 / 255.0, green: 80 / 255.0, blue: 160 / 255.0, alpha: 1)
        pump(1, until: { editor.textColor?.usingColorSpace(.sRGB) == inheritedColor })
        XCTAssertEqual(editor.textColor?.usingColorSpace(.sRGB), inheritedColor)
        XCTAssertEqual(editor.selectedRange(), NSRange(location: 6, length: 7))
        XCTAssertTrue(window.firstResponder === editor)
        renderer.endEditing()
        pump(1, until: { view.formEditor == nil && renderer.document?.textFragments(maxWidth: 400).contains { $0.text == "Color" } == true })
        XCTAssertTrue(renderer.document?.textFragments(maxWidth: 400).contains { $0.text == "Color" } ?? false)
    }

    func testTextareaBaselinesMatchRasterAcrossFonts() throws {
        for fontCSS in ["16px/normal system-ui", "600 15px/1.35 ui-rounded,-apple-system", "italic 20px/32px serif", "12px/10px monospace"] {
            let (renderer, window, _) = make([
                ["op": "el", "id": 10, "tag": "textarea", "attrs": [["style", "width:250px;height:150px;font:\(fontCSS)"]]],
                ["op": "ins", "parent": 7, "id": 10],
                ["op": "prop", "id": 10, "name": "value", "value": "one\ntwo"]
            ])
            defer { renderer.destroy(); window.orderOut(nil) }
            click(renderer, 10)
            let view = renderer.view as! WidgetDocumentView
            pump(2, until: { view.formEditor?.textView != nil })
            let editor = try XCTUnwrap(view.formEditor?.textView)
            let font = try XCTUnwrap(editor.font)
            let layout = try XCTUnwrap(editor.layoutManager)
            layout.ensureLayout(for: try XCTUnwrap(editor.textContainer))
            let raster = LHDocument(html: renderer.dom.serialize(theme: "light"), css: nil)
            raster.layout(maxWidth: 400, preferredWidth: 400)
            let fragments = raster.textFragments(maxWidth: 400)
            let first = try XCTUnwrap(fragments.first { $0.text == "one" })
            let second = try XCTUnwrap(fragments.first { $0.text == "two" })
            let expected = first.frame.maxY - (-font.descender).rounded() - font.leading.rounded()
            let actual = editor.convert(.zero, to: view).y + layout.lineFragmentRect(forGlyphAt: 0, effectiveRange: nil).minY + layout.location(forGlyphAt: 0).y
            XCTAssertEqual(actual, expected, accuracy: 0.5, fontCSS)
            let firstLine = layout.lineFragmentRect(forGlyphAt: 0, effectiveRange: nil)
            let secondLine = layout.lineFragmentRect(forGlyphAt: 4, effectiveRange: nil)
            XCTAssertEqual(secondLine.minY - firstLine.minY, second.frame.minY - first.frame.minY, accuracy: 0.5, fontCSS)
        }
    }

    func testEditorClipDoesNotInterceptButtonsOutsideTheTextField() throws {
        let (renderer, window, _) = make([
            ["op": "el", "id": 30, "tag": "div", "attrs": [["style", "height:180px;overflow:auto"]]],
            ["op": "ins", "id": 30, "parent": 7],
            ["op": "el", "id": 31, "tag": "textarea", "attrs": [["style", "display:block;width:200px;height:40px"]]],
            ["op": "ins", "id": 31, "parent": 30],
            ["op": "el", "id": 32, "tag": "button", "attrs": [["style", "display:block;width:100px;height:40px;margin-top:20px"]]],
            ["op": "ins", "id": 32, "parent": 30],
        ])
        defer { renderer.destroy(); window.orderOut(nil) }
        click(renderer, 31)
        let view = try XCTUnwrap(renderer.view as? WidgetDocumentView)
        pump(2, until: { view.formEditor != nil })
        XCTAssertNotNil(view.formEditor)
        let button = try XCTUnwrap(renderer.elementBounds()[32])
        let field = try XCTUnwrap(renderer.elementBounds()[31])
        let buttonPoint = view.convert(CGPoint(x: button.midX, y: button.midY), to: view.superview)
        let fieldPoint = view.convert(CGPoint(x: field.midX, y: field.midY), to: view.superview)
        XCTAssertTrue(view.hitTest(buttonPoint) === view)
        XCTAssertFalse(view.hitTest(fieldPoint) === view)
    }

    func testChoosingFromTheSelectMenuSetsTheValueAndDispatchesInputAndChange() {
        let previous = WidgetRendererEnvironment.presentMenu
        defer { WidgetRendererEnvironment.presentMenu = previous }
        var shown: [WidgetMenuItem] = []
        WidgetRendererEnvironment.presentMenu = { items, _, _, _, completion in
            shown = items
            completion(items.firstIndex { $0.title == "Blue" })
        }
        let (renderer, window, events) = make([
            ["op": "el", "id": 10, "tag": "select", "attrs": []], ["op": "ins", "parent": 7, "id": 10, "before": NSNull()],
            ["op": "el", "id": 11, "tag": "option", "attrs": [["value", "r"]]], ["op": "ins", "parent": 10, "id": 11, "before": NSNull()],
            ["op": "text", "id": 12, "data": "Red"], ["op": "ins", "parent": 11, "id": 12, "before": NSNull()],
            ["op": "el", "id": 13, "tag": "optgroup", "attrs": [["label", "Cool"]]], ["op": "ins", "parent": 10, "id": 13, "before": NSNull()],
            ["op": "el", "id": 14, "tag": "option", "attrs": [["value", "g"], ["disabled", ""]]], ["op": "ins", "parent": 13, "id": 14, "before": NSNull()],
            ["op": "text", "id": 15, "data": "Green"], ["op": "ins", "parent": 14, "id": 15, "before": NSNull()],
            ["op": "el", "id": 16, "tag": "option", "attrs": [["value", "b"]]], ["op": "ins", "parent": 13, "id": 16, "before": NSNull()],
            ["op": "text", "id": 17, "data": "Blue"], ["op": "ins", "parent": 16, "id": 17, "before": NSNull()],
        ])
        defer { renderer.destroy(); window.orderOut(nil) }
        click(renderer, 10)
        pump(2, until: { events().contains { $0["type"] as? String == "change" } })
        XCTAssertEqual(shown.map { $0.kind }, [.option, .separator, .header, .option, .option])
        XCTAssertEqual(shown.filter { $0.kind == .option }.map { $0.enabled }, [true, false, true])
        XCTAssertEqual(shown.first?.selected, true)
        let form = events().filter { ["input", "change"].contains($0["type"] as? String) }
        XCTAssertEqual(form.map { $0["type"] as? String }, ["input", "change"])
        XCTAssertEqual(form.last?["value"] as? String, "b")
        XCTAssertEqual(form.last?["selectedIndex"] as? Int, 2)
        XCTAssertEqual(WidgetDOM.selectedIndex(of: renderer.dom.node(10)!), 2)
        XCTAssertTrue(renderer.dom.serialize(theme: "light").contains("value=\"b\" selected"))
        shown = []
        let space = NSEvent.keyEvent(with: .keyDown, location: .zero, modifierFlags: [], timestamp: 0, windowNumber: window.windowNumber, context: nil, characters: " ", charactersIgnoringModifiers: " ", isARepeat: false, keyCode: 49)!
        renderer.handleKey("keydown", event: WidgetKeyEvent(space))
        pump(1, until: { !shown.isEmpty })
        XCTAssertEqual(shown.first { $0.selected }?.title, "Blue")
    }

    func testAnEditorInsideAScrollerFollowsItsOffsetAndIsClippedToIt() {
        let (renderer, window, _) = make([
            ["op": "el", "id": 30, "tag": "div", "attrs": [["style", "overflow:auto;height:60px;margin-top:20px"]]], ["op": "ins", "parent": 7, "id": 30, "before": NSNull()],
            ["op": "el", "id": 31, "tag": "div", "attrs": [["style", "height:40px"]]], ["op": "ins", "parent": 30, "id": 31, "before": NSNull()],
            ["op": "el", "id": 32, "tag": "input", "attrs": [["value", "x"]]], ["op": "ins", "parent": 30, "id": 32, "before": NSNull()],
            ["op": "el", "id": 33, "tag": "div", "attrs": [["style", "height:200px"]]], ["op": "ins", "parent": 30, "id": 33, "before": NSNull()],
        ])
        defer { renderer.destroy(); window.orderOut(nil) }
        renderer.send("{\"type\":\"ops\",\"ops\":[{\"op\":\"prop\",\"id\":30,\"name\":\"scrollTop\",\"value\":30}]}")
        pump(1, until: { renderer.scrolling.offset(30).y == 30 })
        let laid = renderer.elementBounds()[32]!
        let box = renderer.scrolling.geometries[30]!.viewport
        let point = CGPoint(x: laid.midX, y: laid.midY)
        let event = NSEvent.mouseEvent(with: .leftMouseDown, location: renderer.view.convert(point, to: nil), modifierFlags: [], timestamp: 0, windowNumber: window.windowNumber, context: nil, eventNumber: 0, clickCount: 1, pressure: 1)!
        renderer.handleMouseDown(point, event: event)
        let view = renderer.view as! WidgetDocumentView
        pump(2, until: { view.formEditor != nil })
        guard let editor = view.formEditor else {
            return XCTFail("no editor")
        }
        XCTAssertEqual(editor.container.frame, box)
        XCTAssertFalse(editor.container.isHidden)
        let field = editor.view.frame.offsetBy(dx: box.minX, dy: box.minY)
        XCTAssertTrue(field.minY > laid.minY && field.maxY < laid.maxY && laid.minY < 60, "\(field) \(laid)")
        renderer.send("{\"type\":\"ops\",\"ops\":[{\"op\":\"prop\",\"id\":30,\"name\":\"scrollTop\",\"value\":150}]}")
        pump(1, until: { editor.container.isHidden })
        XCTAssertTrue(editor.container.isHidden)
    }

    func testTypingIntoAnInputUpdatesTheDOMAndDispatchesInputThenSubmitAndChangeOnBlur() {
        let (renderer, window, events) = make([
            ["op": "el", "id": 20, "tag": "form", "attrs": []], ["op": "ins", "parent": 7, "id": 20, "before": NSNull()],
            ["op": "el", "id": 21, "tag": "input", "attrs": [["placeholder", "Name"], ["maxlength", "8"]]], ["op": "ins", "parent": 20, "id": 21, "before": NSNull()],
            ["op": "el", "id": 22, "tag": "input", "attrs": [["type", "password"]]], ["op": "ins", "parent": 20, "id": 22, "before": NSNull()],
        ])
        defer { renderer.destroy(); window.orderOut(nil) }
        click(renderer, 22)
        pump(0.2)
        XCTAssertNil((renderer.view as? WidgetDocumentView)?.formEditor)
        click(renderer, 21)
        let view = renderer.view as! WidgetDocumentView
        pump(2, until: { view.formEditor != nil })
        guard let field = view.formEditor?.field else {
            return XCTFail("no editor")
        }
        XCTAssertTrue(field.superview?.superview === view)
        XCTAssertFalse(renderer.dom.serialize(theme: "light").contains("placeholder"))
        field.stringValue = "hello"
        view.formEditor?.controlTextDidChange(Notification(name: NSControl.textDidChangeNotification))
        field.stringValue = "hello world!"
        view.formEditor?.controlTextDidChange(Notification(name: NSControl.textDidChangeNotification))
        XCTAssertEqual(field.stringValue, "hello")
        pump(1, until: { events().contains { $0["type"] as? String == "input" } })
        XCTAssertEqual(renderer.dom.node(21)?.formValue, "hello")
        let input = events().first { $0["type"] as? String == "input" }
        XCTAssertEqual(input?["targetId"] as? Int, 21)
        XCTAssertEqual(input?["value"] as? String, "hello")
        renderer.editorCommand(generation: renderer.form.generation, command: "Enter")
        XCTAssertEqual(events().last?["type"] as? String, "submit")
        XCTAssertEqual(events().last?["targetId"] as? Int, 20)
        renderer.editorCommand(generation: renderer.form.generation, command: "Escape")
        pump(1, until: { view.formEditor == nil })
        XCTAssertNil(view.formEditor)
        XCTAssertNil(field.superview?.superview)
        let tail = events().map { $0["type"] as? String ?? "" }.filter { ["change", "blur"].contains($0) }
        XCTAssertEqual(tail, ["change", "blur"])
        let html = renderer.dom.serialize(theme: "light")
        XCTAssertTrue(html.contains("value=\"hello\""), html)
        XCTAssertTrue(html.contains("placeholder=\"Name\""), html)
    }
}

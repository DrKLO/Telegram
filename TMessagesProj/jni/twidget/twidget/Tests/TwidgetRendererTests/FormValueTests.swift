import XCTest
import LiteHTML
@testable import TwidgetRenderer

final class FormValueTests: XCTestCase {
    private func makeDOM() -> WidgetDOM {
        let dom = WidgetDOM()
        dom.apply(["op": "el", "id": 1, "tag": "html", "adopt": "html"])
        dom.apply(["op": "el", "id": 2, "tag": "head", "adopt": "head"])
        dom.apply(["op": "el", "id": 3, "tag": "body", "adopt": "body"])
        return dom
    }

    private func add(_ dom: WidgetDOM, _ id: Int, _ tag: String, parent: Int = 3, attrs: [[Any]] = []) {
        dom.apply(["op": "el", "id": id, "tag": tag, "attrs": attrs])
        dom.apply(["op": "ins", "id": id, "parent": parent])
    }

    func testInputValueAndCheckedPropertiesReplaceTheAttributes() {
        let dom = makeDOM()
        add(dom, 10, "input", attrs: [["value", "attr"]])
        add(dom, 11, "input", attrs: [["type", "checkbox"], ["checked", ""]])
        dom.apply(["op": "prop", "id": 10, "name": "value", "value": "typed \"x\""])
        dom.apply(["op": "prop", "id": 11, "name": "checked", "value": false])
        let html = dom.serialize(theme: "light")
        XCTAssertTrue(html.contains("value=\"typed &quot;x&quot;\""), html)
        XCTAssertFalse(html.contains("value=\"attr\""), html)
        XCTAssertFalse(html.contains("checked"), html)
    }

    func testTextareaValueReplacesItsTextAndKeepsALeadingNewline() {
        let dom = makeDOM()
        add(dom, 10, "textarea")
        dom.apply(["op": "text", "id": 11, "data": "default"])
        dom.apply(["op": "ins", "id": 11, "parent": 10])
        dom.apply(["op": "prop", "id": 10, "name": "value", "value": "\nline <b>"])
        let html = dom.serialize(theme: "light")
        XCTAssertTrue(html.contains(">\n\nline &lt;b&gt;</textarea>"), html)
        XCTAssertFalse(html.contains("default"), html)
    }

    func testSelectValueAndIndexMarkTheChosenOption() {
        let dom = makeDOM()
        add(dom, 10, "select")
        add(dom, 11, "option", parent: 10, attrs: [["selected", ""]])
        dom.apply(["op": "text", "id": 12, "data": "One"])
        dom.apply(["op": "ins", "id": 12, "parent": 11])
        add(dom, 13, "option", parent: 10, attrs: [["value", "b"]])
        dom.apply(["op": "prop", "id": 10, "name": "value", "value": "b"])
        var html = dom.serialize(theme: "light")
        XCTAssertTrue(html.contains("value=\"b\" selected"), html)
        XCTAssertFalse(html.contains("data-lh-id=\"11\" selected"), html)
        dom.apply(["op": "prop", "id": 10, "name": "selectedIndex", "value": 0])
        html = dom.serialize(theme: "light")
        XCTAssertTrue(html.contains("data-lh-id=\"11\" selected"), html)
        XCTAssertFalse(html.contains("value=\"b\" selected"), html)
    }

    func testAFormPropertyInvalidatesThePatchLog() {
        let dom = makeDOM()
        add(dom, 10, "input")
        _ = dom.serialize(theme: "light")
        dom.resetPatchLog()
        dom.apply(["op": "prop", "id": 10, "name": "value", "value": "x"])
        XCTAssertFalse(dom.patchLog.valid)
    }

    func testTextareaValueChangesUseTargetedPatches() throws {
        let dom = makeDOM()
        add(dom, 10, "textarea", attrs: [["style", "width:180px;height:auto;font:16px system-ui;line-height:22px;padding:10px"], ["placeholder", "Write here"]])
        dom.apply(["op": "prop", "id": 10, "name": "value", "value": "first"])
        let doc = LHDocument(html: dom.serialize(theme: "light"), css: nil, previous: nil)
        doc.layout(maxWidth: 260, preferredWidth: 260)
        dom.resetPatchLog()
        dom.apply(["op": "prop", "id": 10, "name": "value", "value": "first more"])
        let changes = try XCTUnwrap(dom.patchChanges(theme: "light", overrides: [:], previous: [:]))
        let next = try XCTUnwrap(LHDocument(previous: doc, changes: Data(changes), attribute: WidgetDOM.idAttribute, css: nil))
        XCTAssertEqual(next.html, dom.serialize(theme: "light"))
    }

    func testTextareaPatchTransitionsMatchFreshRendering() throws {
        let values = ["Typing", "Typing more", "Typing more text that wraps onto several lines at a narrow width", "a😀b", "line one\nline two", "a & b <tag>", "plain again", "\nleading line", "after leading line", "", "restart", " ", "last\r\nline", "final"]
        for width: CGFloat in [180, 320] {
            for initial in ["", "seed", "\nseed", "a & b"] {
                let dom = makeDOM()
                add(dom, 10, "textarea", attrs: [["style", "width:100%;height:auto;font:16px system-ui;line-height:22px;padding:10px;box-sizing:border-box"], ["placeholder", "Write here"]])
                dom.apply(["op": "prop", "id": 10, "name": "value", "value": initial])
                var doc = LHDocument(html: dom.serialize(theme: "light"), css: nil, previous: nil)
                doc.layout(maxWidth: width, preferredWidth: width)
                for value in values {
                    dom.resetPatchLog()
                    dom.apply(["op": "prop", "id": 10, "name": "value", "value": value])
                    let html = dom.serialize(theme: "light")
                    let changes = try XCTUnwrap(dom.patchChanges(theme: "light", overrides: [:], previous: [:]))
                    doc = LHDocument(previous: doc, changes: Data(changes), attribute: WidgetDOM.idAttribute, css: nil) ?? LHDocument(html: html, css: nil, previous: doc)
                    let fresh = LHDocument(html: html, css: nil, previous: nil)
                    doc.layout(maxWidth: width, preferredWidth: width)
                    fresh.layout(maxWidth: width, preferredWidth: width)
                    XCTAssertEqual(doc.html, html, "\(initial.debugDescription) → \(value.debugDescription)")
                    XCTAssertEqual(doc.layoutSignature, fresh.layoutSignature, value.debugDescription)
                    XCTAssertEqual(doc.textareaMetrics(attribute: WidgetDOM.idAttribute, value: "10"), fresh.textareaMetrics(attribute: WidgetDOM.idAttribute, value: "10"), value.debugDescription)
                    let rect = CGRect(x: 0, y: 0, width: width, height: max(doc.size.height, fresh.size.height))
                    let a = try XCTUnwrap(doc.copyImage(maxWidth: width, rect: rect, scale: 1)?.dataProvider?.data)
                    let b = try XCTUnwrap(fresh.copyImage(maxWidth: width, rect: rect, scale: 1)?.dataProvider?.data)
                    XCTAssertEqual(a as Data, b as Data, value.debugDescription)
                }
            }
        }
    }
}

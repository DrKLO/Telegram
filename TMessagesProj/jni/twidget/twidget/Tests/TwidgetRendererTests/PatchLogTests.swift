import XCTest
import LiteHTML
@testable import TwidgetRenderer

private struct PatchRandom: RandomNumberGenerator {
    var state: UInt64
    mutating func next() -> UInt64 {
        state ^= state << 13
        state ^= state >> 7
        state ^= state << 17
        return state
    }
}

final class PatchLogTests: XCTestCase {
    private static let words = ["12.50", "7", "alpha beta", "x  y", "δ", "a & b", "<b>", "\"q\"", "lead ", " ", "", "Wyj fg"]
    private static let classes = ["up", "down", "hot", "up hot", "a\"b", ""]
    private static let styles = ["color:red", "background-color:#eef", "padding:2px", "", "color:light-dark(#000, #fff)"]
    private static let tags = ["div", "span", "td", "pre", "b", "style", "p", "circle", "br"]

    private func build(_ dom: WidgetDOM, random: inout PatchRandom, next: inout Int) {
        dom.apply(["op": "el", "id": 1, "tag": "html", "adopt": "html"])
        dom.apply(["op": "el", "id": 2, "tag": "head", "adopt": "head"])
        dom.apply(["op": "el", "id": 3, "tag": "body", "adopt": "body"])
        dom.apply(["op": "el", "id": 4, "tag": "style"])
        dom.apply(["op": "ins", "id": 4, "parent": 2])
        dom.apply(["op": "css", "id": 4, "text": ".up{color:green}.down{color:red}.hot{background:#fee}td{padding:1px}"])
        var parents = [3]
        for _ in 0..<24 {
            let tag = PatchLogTests.tags[Int(random.next() % UInt64(PatchLogTests.tags.count))]
            let id = next
            next += 1
            var attrs: [[Any]] = []
            if random.next() % 4 != 0 {
                attrs.append(["class", random.next() % 2 == 0 ? "up" : "down"])
            }
            if random.next() % 2 == 0 {
                attrs.append(["style", "color:#06c"])
            }
            if random.next() % 4 == 0 {
                attrs.append(["title", "t"])
            }
            dom.apply(["op": "el", "id": id, "tag": tag, "attrs": attrs])
            dom.apply(["op": "ins", "id": id, "parent": parents[Int(random.next() % UInt64(parents.count))]])
            if tag != "br" && random.next() % 4 != 0 {
                let text = next
                next += 1
                dom.apply(["op": "text", "id": text, "data": PatchLogTests.words[Int(random.next() % 3)]])
                dom.apply(["op": "ins", "id": text, "parent": id])
            }
            if random.next() % 3 == 0 {
                parents.append(id)
            }
        }
    }

    private func randomOp(_ dom: WidgetDOM, random: inout PatchRandom, next: inout Int) -> [[String: Any]] {
        let ids = Array(5..<next)
        let target = ids[Int(random.next() % UInt64(ids.count))]
        let word = PatchLogTests.words[Int(random.next() % UInt64(PatchLogTests.words.count))]
        switch random.next() % 20 {
        case 0...7:
            let text = next
            next += 1
            var ops: [[String: Any]] = [["op": "text", "id": text, "data": word]]
            for child in dom.node(target)?.children ?? [] {
                ops.append(["op": "rm", "id": dom.publicId(child)])
            }
            ops.append(["op": "ins", "id": text, "parent": target])
            return ops
        case 8, 9:
            return [["op": "data", "id": target, "data": word]]
        case 10...14:
            return [["op": "attr", "id": target, "name": random.next() % 5 == 0 ? "CLASS" : "class", "value": PatchLogTests.classes[Int(random.next() % UInt64(PatchLogTests.classes.count))]]]
        case 15, 16:
            return [["op": "style", "id": target, "name": random.next() % 2 == 0 ? "color" : "background-color", "value": random.next() % 4 == 0 ? "" : "#a0a"]]
        case 17:
            return [["op": "attr", "id": target, "name": "style", "value": PatchLogTests.styles[Int(random.next() % UInt64(PatchLogTests.styles.count))]]]
        case 18:
            return [["op": "attr", "id": target, "name": random.next() % 2 == 0 ? "title" : "data-x", "value": word]]
        case 19 where random.next() % 2 == 0:
            return [random.next() % 2 == 0 ? ["op": "rattr", "id": target, "name": "class"] : ["op": "rm", "id": target]]
        default:
            let other = ids[Int(random.next() % UInt64(ids.count))]
            return [["op": "ins", "id": target, "parent": other]]
        }
    }

    func testLoggedChangesPatchToTheSerializedHTML() {
        var random = PatchRandom(state: 0x5DEECE66D)
        var patched = 0, frames = 0
        for round in 0..<120 {
            let dom = WidgetDOM()
            var next = 5
            build(dom, random: &random, next: &next)
            let theme = round % 2 == 0 ? "light" : "dark"
            var document = LHDocument(html: dom.serialize(theme: theme), css: nil, previous: nil)
            document.layout(maxWidth: 240, preferredWidth: 240)
            dom.resetPatchLog()
            for step in 0..<20 {
                for _ in 0..<(1 + Int(random.next() % 3)) {
                    for op in randomOp(dom, random: &random, next: &next) {
                        dom.apply(op)
                    }
                }
                frames += 1
                let html = dom.serialize(theme: theme)
                let changes = dom.patchChanges(theme: theme, overrides: [:], previous: [:])
                dom.resetPatchLog()
                if let changes = changes, !changes.isEmpty, let next = LHDocument(previous: document, changes: Data(changes), attribute: WidgetDOM.idAttribute, css: nil) {
                    patched += 1
                    XCTAssertEqual(next.html, html, "round \(round) step \(step)")
                    document = next
                } else {
                    document = LHDocument(html: html, css: nil, previous: document)
                }
                document.layout(maxWidth: 240, preferredWidth: 240)
            }
        }
        XCTAssertGreaterThan(patched, frames / 30)
    }

    func testStructureAndRemovalsInvalidateTheLog() {
        func fresh() -> WidgetDOM {
            let dom = WidgetDOM()
            dom.apply(["op": "el", "id": 1, "tag": "html", "adopt": "html"])
            dom.apply(["op": "el", "id": 3, "tag": "body", "adopt": "body"])
            dom.apply(["op": "el", "id": 5, "tag": "div", "attrs": [["class", "a"]]])
            dom.apply(["op": "ins", "id": 5, "parent": 3])
            dom.apply(["op": "text", "id": 6, "data": "one"])
            dom.apply(["op": "ins", "id": 6, "parent": 5])
            dom.resetPatchLog()
            return dom
        }
        let dom = fresh()
        dom.apply(["op": "el", "id": 7, "tag": "span"])
        dom.apply(["op": "attr", "id": 7, "name": "class", "value": "b"])
        dom.apply(["op": "text", "id": 8, "data": "two"])
        dom.apply(["op": "data", "id": 6, "data": "uno"])
        XCTAssertTrue(dom.patchLog.valid)
        XCTAssertTrue(dom.patchLog.attributes.isEmpty)
        XCTAssertEqual(dom.patchLog.texts.count, 1)
        dom.apply(["op": "ins", "id": 7, "parent": 5])
        XCTAssertFalse(dom.patchLog.valid)
        for op: [String: Any] in [["op": "rattr", "id": 5, "name": "class"], ["op": "rm", "id": 5], ["op": "css", "id": 5, "text": "x"], ["op": "el", "id": 5, "tag": "p"], ["op": "el", "id": 3, "tag": "body", "adopt": "body"]] {
            let dom = fresh()
            dom.apply(op)
            XCTAssertFalse(dom.patchLog.valid, "\(op)")
        }
        let moved = fresh()
        moved.apply(["op": "rm", "id": 6])
        XCTAssertTrue(moved.patchLog.valid)
        XCTAssertNil(moved.patchChanges(theme: "light", overrides: [:], previous: [:]))
    }
}

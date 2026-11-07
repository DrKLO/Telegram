import XCTest
import AppKit
import LiteHTML
@testable import TwidgetRenderer

final class DocumentQueryTests: XCTestCase {
    static func largeDocument(_ count: Int) -> LHDocument {
        var body = ""
        var id = 10
        for i in 0..<count / 4 {
            let anim = i % 10 == 0 ? "animation:pop 1s linear both;" : ""
            let transform = i % 7 == 0 ? "transform:rotate(\(i % 30)deg) scale(1.1);" : ""
            body += "<div data-lh-id=\"\(id)\" style=\"padding:2px;\(anim)\(transform)\"><span data-lh-id=\"\(id + 1)\">row \(i)</span><b data-lh-id=\"\(id + 2)\" style=\"filter:blur(1px)\">x</b><div data-lh-id=\"\(id + 3)\" style=\"overflow:auto;height:\(i % 5 == 0 ? 20 : 400)px\"><div style=\"height:60px\">i</div></div></div>"
            id += 4
        }
        let html = "<html data-lh-id=\"1\"><head><style>@keyframes pop{from{transform:scale(.5)}to{transform:scale(1)}}</style></head><body data-lh-id=\"3\" style=\"margin:0\">\(body)</body></html>"
        let document = LHDocument(html: html, css: nil, previous: nil)
        document.layout(maxWidth: 400, preferredWidth: 400)
        return document
    }

    private func time(_ label: String, _ iterations: Int = 50, _ block: () -> Void) {
        block()
        let start = CACurrentMediaTime()
        for _ in 0..<iterations { block() }
        let us = (CACurrentMediaTime() - start) / Double(iterations) * 1_000_000
        print("BENCH \(label): \(Int(us)) us")
    }

    func testBenchDocumentQueries() throws {
        guard ProcessInfo.processInfo.environment["TW_QUERY_BENCH"] != nil else { throw XCTSkip("set TW_QUERY_BENCH") }
        let document = Self.largeDocument(2000)
        let attr = WidgetDOM.idAttribute
        let required = WidgetAnimator.entryProperties + ["backdrop-filter", "filter", "box-shadow"]
        time("declarations") {
            var out: [Int: [String: String]] = [:]
            for (key, values) in document.declarations(WidgetAnimator.watched, byAttribute: attr, requiringAny: required) {
                if let id = Int(key) { out[id] = values }
            }
            XCTAssertFalse(out.isEmpty)
        }
        time("bounds") {
            var out: [Int: CGRect] = [:]
            for (key, value) in document.bounds(byAttribute: attr) {
                if let id = Int(key) { out[id] = value.rectValue }
            }
            XCTAssertGreaterThan(out.count, 1000)
        }
        time("visualBounds") {
            var out: [Int: CGRect] = [:]
            for (key, value) in document.visualBounds(byAttribute: attr) {
                if let id = Int(key) { out[id] = value.rectValue }
            }
        }
        time("scrollers") {
            var out: [Int: CGRect] = [:]
            for (key, info) in document.scrollers(byAttribute: attr) {
                if let id = Int(key), let viewport = (info["viewport"] as? NSValue)?.rectValue { out[id] = viewport }
            }
            XCTAssertFalse(out.isEmpty)
        }
        time("bounds raw") { XCTAssertGreaterThan((document.bounds(byAttribute: attr) as NSDictionary).count, 1000) }
        time("scrollers raw") { XCTAssertGreaterThan((document.scrollers(byAttribute: attr) as NSDictionary).count, 0) }
        time("declarations raw") { XCTAssertGreaterThan((document.declarations(WidgetAnimator.watched, byAttribute: attr, requiringAny: required) as NSDictionary).count, 0) }
        time("idBounds") { XCTAssertGreaterThan(document.idBounds().count, 1000) }
        time("idBounds transformed") { XCTAssertGreaterThan(document.idBounds(transformed: true).count, 1000) }
        var known: UInt64 = 0
        var memo: [Int: [String: String]] = [:]
        time("declarations memo") {
            var generation: UInt64 = 0
            if let scanned = document.declarations(WidgetAnimator.watched, byAttribute: attr, requiringAny: required, changedSince: known, generation: &generation) {
                memo = [:]
                for (key, values) in scanned { if let id = Int(key) { memo[id] = values } }
                known = generation
            }
            XCTAssertFalse(memo.isEmpty)
        }
        let values = Set((10..<2010).map { String($0) })
        time("paintOrder") {
            XCTAssertFalse(document.paintOrder(attribute: attr, values: values).isEmpty)
        }
        time("parents") {
            XCTAssertFalse(document.parents(byAttribute: attr).isEmpty)
        }
    }

    private static func legacy(_ boxes: [String: NSValue]) -> [Int: CGRect] {
        Dictionary(uniqueKeysWithValues: boxes.compactMap { entry in Int(entry.key).map { ($0, entry.value.rectValue) } })
    }

    private static let documents: [String] = [
        "<html data-lh-id=\"1\"><body data-lh-id=\"3\" style=\"margin:0\"><div data-lh-id=\"10\" style=\"padding:4px;border:2px solid red\">a<span data-lh-id=\"11\">b</span><span data-lh-id=\"11\">c</span></div><div data-lh-id=\"x12\">n</div><div data-lh-id=\"-4\">m</div><div data-lh-id=\"\">e</div></body></html>",
        "<html data-lh-id=\"1\"><body data-lh-id=\"3\"><div data-lh-id=\"20\" style=\"transform:rotate(30deg) scale(1.5);width:80px;height:40px\"><div data-lh-id=\"21\" style=\"transform:translate(10px,5px) skewX(10deg);width:20px;height:20px\"></div></div></body></html>",
        "<html data-lh-id=\"1\"><body data-lh-id=\"3\"><div data-lh-id=\"30\" style=\"overflow:auto;height:100px;width:200px\"><div data-lh-id=\"31\" style=\"height:300px\"><div data-lh-id=\"32\" style=\"overflow:scroll;height:80px\"><div data-lh-id=\"33\" style=\"height:400px;transform:scale(.5)\">deep</div></div></div></div></body></html>",
        "<html data-lh-id=\"1\"><body data-lh-id=\"3\"><svg data-lh-id=\"40\" width=\"120\" height=\"80\" viewBox=\"0 0 60 40\" style=\"transform:rotate(10deg)\"><g data-lh-id=\"41\" transform=\"translate(5,5)\"><rect data-lh-id=\"42\" x=\"1\" y=\"2\" width=\"20\" height=\"10\"/><circle data-lh-id=\"43\" cx=\"30\" cy=\"20\" r=\"8\"/></g></svg></body></html>",
    ]

    func testKeyedBoundsMatchStringKeyedBounds() {
        var all = Self.documents.map { html -> LHDocument in
            let document = LHDocument(html: html, css: nil, previous: nil)
            document.layout(maxWidth: 300, preferredWidth: 300)
            return document
        }
        let scrolled = all[2]
        _ = scrolled.setScrollOffset(CGPoint(x: 0, y: 60), attribute: WidgetDOM.idAttribute, value: "30")
        _ = scrolled.setScrollOffset(CGPoint(x: 0, y: 25), attribute: WidgetDOM.idAttribute, value: "32")
        all.append(Self.largeDocument(400))
        for document in all {
            let plain = document.idBounds()
            let visual = document.idBounds(transformed: true)
            XCTAssertFalse(plain.isEmpty)
            XCTAssertEqual(plain, Self.legacy(document.bounds(byAttribute: WidgetDOM.idAttribute)))
            XCTAssertEqual(visual, Self.legacy(document.visualBounds(byAttribute: WidgetDOM.idAttribute)))
        }
        XCTAssertNotNil(all[0].idBounds()[-4])
        XCTAssertEqual(all[0].idBounds().count, 5)
        XCTAssertNotEqual(all[1].idBounds()[21], all[1].idBounds(transformed: true)[21])
    }

    func testDeclarationGenerations() {
        let document = Self.largeDocument(200)
        let attr = WidgetDOM.idAttribute
        let names = WidgetAnimator.watched
        let gates = LiteHTMLWidgetRenderer.declarationGates
        var first: UInt64 = 0
        let fresh = document.declarations(names, byAttribute: attr, requiringAny: gates, changedSince: 0, generation: &first)
        XCTAssertNotEqual(first, 0)
        XCTAssertEqual(fresh, document.declarations(names, byAttribute: attr, requiringAny: gates))
        var again: UInt64 = 0
        XCTAssertNil(document.declarations(names, byAttribute: attr, requiringAny: gates, changedSince: first, generation: &again))
        XCTAssertEqual(again, first)
        var other: UInt64 = 0
        let otherResult = document.declarations(["transform", "filter"], byAttribute: attr, requiringAny: ["filter"], changedSince: 0, generation: &other)
        XCTAssertNotNil(otherResult)
        XCTAssertNotEqual(other, first)
        XCTAssertNil(document.declarations(names, byAttribute: attr, requiringAny: gates, changedSince: first, generation: &again))
        XCTAssertEqual(again, first)
        XCTAssertEqual(document.declarations(names, byAttribute: attr, requiringAny: gates, changedSince: first + 1_000_000, generation: &again), fresh)
    }
}

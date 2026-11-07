import XCTest
import LiteHTML

final class ScopedExclusionTests: XCTestCase {
    private func page(hidden: Set<String> = []) -> LHDocument {
        let panels = (0..<40).map { index in
            "<section id='p\(index)'><div id='a\(index)' class='red' style='visibility:\(hidden.contains("a\(index)") ? "hidden" : "visible")'></div><div id='b\(index)' class='blue' style='visibility:\(hidden.contains("b\(index)") ? "hidden" : "visible")'></div></section>"
        }.joined()
        let document = LHDocument(html: "<style>body{margin:0}section{position:relative;width:100px;height:100px;background:white}section div{position:absolute;top:10px;width:20px;height:20px}.red{left:10px;background:red}.blue{left:50px;background:blue}</style>\(panels)", css: nil)
        _ = document.layout(maxWidth: 100, preferredWidth: 100)
        return document
    }

    private func pixels(_ image: CGImage?) throws -> Data {
        let image = try XCTUnwrap(image)
        return try XCTUnwrap(image.dataProvider?.data) as Data
    }

    private func snapshot(_ document: LHDocument, _ id: String) throws -> Data {
        var ink = CGRect.zero, box = CGRect.zero
        return try pixels(document.copyImageOfElement(attribute: "id", value: id, scale: 2, inkBounds: &ink, borderBox: &box))
    }

    func testElementSnapshotsKeepLocalExclusionsAcrossRootsAndUpdates() throws {
        let document = page()
        let hidden = Set((0..<40).map { "a\($0)" })
        let expected = page(hidden: hidden)
        document.setPaintExclusions(hidden, attribute: "id")
        document.setLayerExclusions(["p0", "p39"], attribute: "id")
        for id in ["p0", "p39", "p0"] {
            XCTAssertEqual(try snapshot(document, id), try snapshot(expected, id))
        }
        document.setPaintExclusions(["b0"], attribute: "id")
        XCTAssertEqual(try snapshot(document, "p0"), try snapshot(page(hidden: ["b0"]), "p0"))
        document.setPaintExclusions([], attribute: "id")
        XCTAssertEqual(try snapshot(document, "p0"), try snapshot(page(), "p0"))
    }

    func testTemporaryExclusionsDoNotLeakIntoFollowingSnapshots() throws {
        let document = page()
        document.setPaintExclusions(["a0"], attribute: "id")
        let rect = CGRect(x: 0, y: 0, width: 100, height: 100)
        let temporary = document.copyImage(maxWidth: 100, rect: rect, scale: 2, excluding: ["b0"], attribute: "id")
        let expected = page(hidden: ["a0", "b0"])
        XCTAssertEqual(try pixels(temporary), try pixels(expected.copyImage(maxWidth: 100, rect: rect, scale: 2)))
        XCTAssertEqual(try snapshot(document, "p0"), try snapshot(page(hidden: ["a0"]), "p0"))
    }

    func testNestedScrollSnapshotsPreserveChildExclusionsAndOffsets() throws {
        let html = "<style>body{margin:0}.outer{width:100px;height:60px;overflow:hidden;background:white}.inner{width:100px;height:40px;overflow:hidden;background:lime}.content{height:200px;background:red}</style><div class='outer' id='one'><div class='inner' id='nested'><div class='content' id='red'></div></div><div class='content' style='background:blue'></div></div><div class='outer' id='two'><div class='content'></div></div>"
        let document = LHDocument(html: html, css: nil)
        _ = document.layout(maxWidth: 100, preferredWidth: 100)
        let rect = CGRect(x: 0, y: 0, width: 100, height: 60)
        let before = try pixels(document.copyScrollContent(attribute: "id", value: "one", rect: rect, scale: 2))
        document.setScrollContentExclusions(["one", "two"], attribute: "id")
        _ = document.setScrollOffset(CGPoint(x: 0, y: 90), attribute: "id", value: "one")
        XCTAssertEqual(try pixels(document.copyScrollContent(attribute: "id", value: "one", rect: rect, scale: 2)), before)
        document.setScrollContentExclusions(["one", "nested", "two"], attribute: "id")
        let nestedHidden = try pixels(document.copyScrollContent(attribute: "id", value: "one", rect: rect, scale: 2))
        XCTAssertNotEqual(nestedHidden, before)
        let fresh = LHDocument(html: html.replacingOccurrences(of: "id='red'", with: "id='red' style='visibility:hidden'"), css: nil)
        _ = fresh.layout(maxWidth: 100, preferredWidth: 100)
        XCTAssertEqual(nestedHidden, try pixels(fresh.copyScrollContent(attribute: "id", value: "one", rect: rect, scale: 2)))
        XCTAssertEqual((document.scrollers(byAttribute: "id")["one"]?["offset"] as? NSValue)?.pointValue.y, 90)
        document.setScrollContentExclusions([], attribute: "id")
        XCTAssertEqual(try pixels(document.copyScrollContent(attribute: "id", value: "one", rect: rect, scale: 2)), before)
    }

    func testPaintOrderDoesNotDependOnTheLastSnapshotRoot() throws {
        let html = "<style>body{margin:0}.clip{width:100px;height:40px;overflow:hidden}.child{height:200px;background:red}</style><div class='clip' id='one'><div class='child' id='first'></div></div><div class='clip' id='two'><div class='child' id='second'></div></div>"
        let document = LHDocument(html: html, css: nil)
        _ = document.layout(maxWidth: 100, preferredWidth: 100)
        document.setScrollContentExclusions(["one", "two"], attribute: "id")
        _ = document.copyImage(maxWidth: 100, rect: CGRect(x: 0, y: 0, width: 100, height: 80), scale: 2)
        let wanted: Set<String> = ["one", "two", "first", "second"]
        let expected = document.paintOrder(attribute: "id", values: wanted)
        XCTAssertNil(expected["first"])
        XCTAssertNil(expected["second"])
        for id in ["one", "two", "one"] {
            _ = try snapshot(document, id)
            XCTAssertEqual(document.paintOrder(attribute: "id", values: wanted), expected)
            _ = try pixels(document.copyScrollContent(attribute: "id", value: id, rect: CGRect(x: 0, y: 0, width: 100, height: 40), scale: 2))
            XCTAssertEqual(document.paintOrder(attribute: "id", values: wanted), expected)
        }
    }

    func testBackdropSnapshotsRetainExclusionsOutsideTheirRoot() throws {
        for primed in [false, true] {
            func document(hidden: Bool) -> LHDocument {
                let html = "<style>body{margin:0;width:100px;height:100px;background:blue}#red{position:absolute;left:0;top:0;width:100px;height:100px;background:red;visibility:\(hidden ? "hidden" : "visible")}#panel{position:absolute;left:0;top:0;width:100px;height:100px}#glass{width:100px;height:100px;backdrop-filter:blur(3px);background:#ffffff22}</style><div id='red'></div><div id='panel'><div id='glass'></div></div>"
                let page = LHDocument(html: html, css: nil)
                _ = page.layout(maxWidth: 100, preferredWidth: 100)
                if primed {
                    _ = page.copyImage(maxWidth: 100, rect: CGRect(x: 0, y: 0, width: 100, height: 100), scale: 2)
                }
                return page
            }
            let actual = document(hidden: false)
            actual.setPaintExclusions(["red"], attribute: "id")
            let expected = document(hidden: true)
            XCTAssertEqual(try snapshot(actual, "panel"), try snapshot(expected, "panel"), "primed=\(primed)")
        }
    }

    func testAdmissionSeesScrollOwnedDescendantsAndTheirOverlaps() throws {
        for indexed in [false, true] {
            for primed in [false, true] {
                for overlapping in [false, true] {
                    let html = "<style>body{margin:0}.clip{position:relative;width:100px;height:40px;overflow:hidden;background:blue}.track{width:300px;height:32px;background:red}.cover{position:absolute;left:40px;top:\(overlapping ? 0 : 60)px;width:20px;height:20px;background:lime}</style><div class='clip' id='window'><div class='track' id='track'></div><div class='cover' id='cover'></div></div>"
                    let document = LHDocument(html: html, css: nil)
                    _ = document.layout(maxWidth: 100, preferredWidth: 100)
                    document.setValue(!indexed, forKey: "obscuringCacheDisabled")
                    let rect = CGRect(x: 0, y: 0, width: 100, height: 40)
                    let expected = overlapping ? ["cover"] : []
                    if primed {
                        XCTAssertEqual(document.elementsObscuring(attribute: "id", value: "track", in: rect, ignoring: [], clipped: true), expected)
                    }
                    document.setScrollContentExclusions(["window"], attribute: "id")
                    let before = try pixels(document.copyImage(maxWidth: 100, rect: rect, scale: 2))
                    XCTAssertEqual(document.elementsObscuring(attribute: "id", value: "track", in: rect, ignoring: [], clipped: true), expected, "indexed=\(indexed) primed=\(primed) overlapping=\(overlapping)")
                    XCTAssertEqual(document.elementsObscuring(attribute: "id", value: "track", in: rect, ignoring: ["cover"], clipped: true), [])
                    XCTAssertNil(document.paintOrder(attribute: "id", values: ["track"])["track"])
                    let order = document.paintOrder(attribute: "id", values: ["track", "cover"], includingScrollContent: true)
                    XCTAssertNotNil(order["track"])
                    if overlapping {
                        XCTAssertLessThan(try XCTUnwrap(order["track"]).intValue, try XCTUnwrap(order["cover"]).intValue)
                    }
                    XCTAssertEqual(try pixels(document.copyImage(maxWidth: 100, rect: rect, scale: 2)), before)
                    document.setScrollContentExclusions([], attribute: "id")
                    XCTAssertEqual(document.elementsObscuring(attribute: "id", value: "track", in: rect, ignoring: [], clipped: true), expected)
                }
            }
        }
    }

    func testBackdropSamplingPreservesScrollOwnershipAfterAdmission() throws {
        for primed in [false, true] {
            func page(hidden: Bool) -> LHDocument {
                let html = "<style>body{margin:0;width:100px;height:100px;background:blue}#window{position:absolute;width:100px;height:100px;overflow:hidden}#track{height:200px;background:red;visibility:\(hidden ? "hidden" : "visible")}#panel{position:absolute;width:100px;height:100px}#glass{width:100px;height:100px;backdrop-filter:blur(3px);background:#ffffff22}</style><div id='window'><div id='track'></div></div><div id='panel'><div id='glass'></div></div>"
                let document = LHDocument(html: html, css: nil)
                _ = document.layout(maxWidth: 100, preferredWidth: 100)
                return document
            }
            let actual = page(hidden: false)
            actual.setScrollContentExclusions(["window"], attribute: "id")
            if primed {
                _ = actual.elementsObscuring(attribute: "id", value: "track", in: CGRect(x: 0, y: 0, width: 100, height: 100), ignoring: [], clipped: true)
            }
            XCTAssertEqual(try snapshot(actual, "panel"), try snapshot(page(hidden: true), "panel"), "primed=\(primed)")
        }
    }
}

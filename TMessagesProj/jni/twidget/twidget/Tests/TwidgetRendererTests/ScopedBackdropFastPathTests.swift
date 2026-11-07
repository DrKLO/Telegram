import XCTest
import LiteHTML

final class ScopedBackdropFastPathTests: XCTestCase {
    private enum CaptureKind: CaseIterable {
        case element, scroll
    }

    private struct Pixels: Equatable {
        let width: Int
        let height: Int
        let bytes: Data
    }

    private func source(hidden: Bool, rules: String = "", inline: String = "") -> String {
        return "<style>body{margin:0;width:120px;height:100px;background:#1457a8}#red{position:absolute;left:0;top:0;width:120px;height:100px;background:#e43228}#panel{position:absolute;left:10px;top:10px;width:100px;height:80px;overflow:hidden}#glass{width:100px;height:160px;background:#ffffff22}\(rules)</style><div id='red' style='visibility:\(hidden ? "hidden" : "visible")'></div><div id='panel'><div id='glass' style='\(inline)'></div></div>"
    }

    private func page(hidden: Bool, rules: String = "", inline: String = "", previous: LHDocument? = nil) -> LHDocument {
        let document = LHDocument(html: source(hidden: hidden, rules: rules, inline: inline), css: nil, previous: previous)
        if !hidden {
            document.setPaintExclusions(["red"], attribute: "id")
        }
        _ = document.layout(maxWidth: 120, preferredWidth: 120)
        return document
    }

    private func patched(_ previous: LHDocument, filter: String) throws -> LHDocument {
        let document = try XCTUnwrap(LHDocument(previous: previous, styles: ["glass": "backdrop-filter:\(filter)"], attribute: "id", css: nil))
        XCTAssertTrue(document.reusedPrevious)
        document.setPaintExclusions(["red"], attribute: "id")
        _ = document.layout(maxWidth: 120, preferredWidth: 120)
        return document
    }

    private func pixels(_ image: CGImage?) throws -> Pixels {
        let image = try XCTUnwrap(image)
        let source = try XCTUnwrap(image.dataProvider?.data) as Data
        let rowBytes = image.width * image.bitsPerPixel / 8
        var bytes = Data()
        bytes.reserveCapacity(rowBytes * image.height)
        for row in 0..<image.height {
            let start = row * image.bytesPerRow
            bytes.append(source[start..<(start + rowBytes)])
        }
        return Pixels(width: image.width, height: image.height, bytes: bytes)
    }

    private func capture(_ document: LHDocument, kind: CaptureKind, primed: Bool) throws -> Pixels {
        if primed {
            let order = document.paintOrder(attribute: "id", values: ["red", "panel", "glass"], includingScrollContent: true)
            XCTAssertNotNil(order["panel"])
            XCTAssertNotNil(order["glass"])
        }
        switch kind {
        case .element:
            var ink = CGRect.zero, box = CGRect.zero
            return try pixels(document.copyImageOfElement(attribute: "id", value: "panel", scale: 2, inkBounds: &ink, borderBox: &box))
        case .scroll:
            return try pixels(document.copyScrollContent(attribute: "id", value: "panel", rect: CGRect(x: 0, y: 0, width: 100, height: 80), scale: 2))
        }
    }

    @discardableResult
    private func assertHiddenSiblingReference(_ actual: LHDocument, _ reference: LHDocument, kind: CaptureKind, primed: Bool, file: StaticString = #filePath, line: UInt = #line) throws -> Pixels {
        let result = try capture(actual, kind: kind, primed: primed)
        XCTAssertEqual(result, try capture(reference, kind: kind, primed: primed), "\(kind), primed=\(primed)", file: file, line: line)
        return result
    }

    func testInitiallyAbsentBackdropMatchesPhysicallyHiddenSibling() throws {
        for kind in CaptureKind.allCases {
            for primed in [false, true] {
                let actual = page(hidden: false)
                let reference = page(hidden: true)
                let first = try assertHiddenSiblingReference(actual, reference, kind: kind, primed: primed)
                XCTAssertEqual(try assertHiddenSiblingReference(actual, reference, kind: kind, primed: primed), first)
            }
        }
    }

    func testHoverEnablesDisablesAndReenablesBackdropWithoutLosingOutsideExclusions() throws {
        let rules = "#panel:hover #glass{backdrop-filter:blur(3px)}"
        for kind in CaptureKind.allCases {
            for primed in [false, true] {
                let actual = page(hidden: false, rules: rules)
                let reference = page(hidden: true, rules: rules)
                let before = try assertHiddenSiblingReference(actual, reference, kind: kind, primed: primed)
                XCTAssertTrue(actual.mouseMoved(CGPoint(x: 50, y: 50)))
                XCTAssertTrue(reference.mouseMoved(CGPoint(x: 50, y: 50)))
                let hovered = try assertHiddenSiblingReference(actual, reference, kind: kind, primed: primed)
                XCTAssertNotEqual(hovered, before)
                _ = actual.mouseExited()
                _ = reference.mouseExited()
                XCTAssertEqual(try assertHiddenSiblingReference(actual, reference, kind: kind, primed: primed), before)
                XCTAssertTrue(actual.mouseMoved(CGPoint(x: 50, y: 50)))
                XCTAssertTrue(reference.mouseMoved(CGPoint(x: 50, y: 50)))
                XCTAssertEqual(try assertHiddenSiblingReference(actual, reference, kind: kind, primed: primed), hovered)
            }
        }
    }

    func testInlinePatchAndConsumedRestorationReestablishBackdropScope() throws {
        for kind in CaptureKind.allCases {
            for primed in [false, true] {
                let original = page(hidden: false, inline: "backdrop-filter:none")
                let before = try assertHiddenSiblingReference(original, page(hidden: true, inline: "backdrop-filter:none"), kind: kind, primed: primed)
                let enabled = try patched(original, filter: "blur(3px)")
                var shouldContinueCalls = 0
                XCTAssertNil(original.copyScrollContentIfCurrent(attribute: "id", value: "panel", rect: CGRect(x: 0, y: 0, width: 100, height: 80), scale: 2, shouldContinue: {
                    shouldContinueCalls += 1
                    return true
                }))
                XCTAssertEqual(shouldContinueCalls, 0)
                let filtered = try assertHiddenSiblingReference(enabled, page(hidden: true, inline: "backdrop-filter:blur(3px)"), kind: kind, primed: primed)
                XCTAssertNotEqual(filtered, before)
                XCTAssertEqual(try assertHiddenSiblingReference(original, page(hidden: true, inline: "backdrop-filter:none"), kind: kind, primed: primed), before)
                let disabled = try patched(enabled, filter: "none")
                XCTAssertEqual(try assertHiddenSiblingReference(disabled, page(hidden: true, inline: "backdrop-filter:none"), kind: kind, primed: primed), before)
                XCTAssertEqual(try assertHiddenSiblingReference(enabled, page(hidden: true, inline: "backdrop-filter:blur(3px)"), kind: kind, primed: primed), filtered)
                let reenabled = try patched(disabled, filter: "blur(3px)")
                XCTAssertEqual(try assertHiddenSiblingReference(reenabled, page(hidden: true, inline: "backdrop-filter:blur(3px)"), kind: kind, primed: primed), filtered)
            }
        }
    }

    func testStylesheetReplacementEnablesBackdropWithIndependentReference() throws {
        for kind in CaptureKind.allCases {
            for primed in [false, true] {
                let original = page(hidden: false)
                let before = try assertHiddenSiblingReference(original, page(hidden: true), kind: kind, primed: primed)
                let enabledRules = "#glass{backdrop-filter:blur(3px)}"
                let enabled = page(hidden: false, rules: enabledRules, previous: original)
                let filtered = try assertHiddenSiblingReference(enabled, page(hidden: true, rules: enabledRules), kind: kind, primed: primed)
                XCTAssertNotEqual(filtered, before)
                let disabledRules = "#glass{backdrop-filter:none}"
                let disabled = page(hidden: false, rules: disabledRules, previous: enabled)
                XCTAssertEqual(try assertHiddenSiblingReference(disabled, page(hidden: true, rules: disabledRules), kind: kind, primed: primed), before)
                let reenabled = page(hidden: false, rules: enabledRules, previous: disabled)
                XCTAssertEqual(try assertHiddenSiblingReference(reenabled, page(hidden: true, rules: enabledRules), kind: kind, primed: primed), filtered)
            }
        }
    }
}

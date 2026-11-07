import XCTest
import AppKit
import LiteHTML
@testable import TwidgetRenderer

final class WidgetResizeDiffTests: XCTestCase {
    private func layout(_ blocks: [(Int, CGFloat)], wrap: Bool = true) -> (bounds: [Int: CGRect], height: CGFloat) {
        let body = blocks.map { "<div data-lh-id=\"\($0.0)\" style=\"height:\(Int($0.1))px\"></div>" }.joined()
        let content = wrap ? "<div data-lh-id=\"5\">\(body)</div>" : body
        let html = "<html data-lh-id=\"1\"><body data-lh-id=\"3\" style=\"margin:0\">\(content)</body></html>"
        let document = LHDocument(html: html, css: nil, previous: nil)
        document.layout(maxWidth: 300, preferredWidth: 300)
        return (document.idBounds(), ceil(document.size.height))
    }

    private func diff(_ before: [(Int, CGFloat)], _ after: [(Int, CGFloat)], pointer: CGPoint? = nil, target: Int? = nil) -> WidgetResizeDiff {
        let old = layout(before)
        let new = layout(after)
        return WidgetResizeDiff.compute(old: old.bounds, new: new.bounds, oldHeight: old.height, newHeight: new.height, pointer: pointer, pointerTarget: target)
    }

    func testInsertBelowKeepsEverythingAboveUnchanged() {
        let result = diff([(10, 44), (11, 36), (12, 60), (14, 40)], [(10, 44), (11, 36), (12, 60), (13, 180), (14, 40)])
        XCTAssertEqual(result.oldHeight, 180)
        XCTAssertEqual(result.newHeight, 360)
        XCTAssertEqual(result.unchangedTop, 140, accuracy: 0.5)
        XCTAssertEqual(result.unchangedBottom, 40, accuracy: 0.5)
    }

    func testRemoveAboveKeepsBottomUnchanged() {
        let result = diff([(10, 120), (11, 60), (12, 40)], [(11, 60), (12, 40)])
        XCTAssertEqual(result.unchangedTop, 0, accuracy: 0.5)
        XCTAssertEqual(result.unchangedBottom, 100, accuracy: 0.5)
    }

    func testPointerFollowsClickedElement() {
        let result = diff([(10, 120), (11, 40), (12, 60)], [(10, 120), (11, 40), (13, 200), (12, 60)], pointer: CGPoint(x: 10, y: 135), target: 11)
        XCTAssertEqual(result.pointerFrom, 135)
        XCTAssertEqual(result.pointerTo, 135)
    }

    func testPointerMovesWithContentInsertedAbove() {
        let result = diff([(10, 60), (11, 40)], [(10, 60), (13, 90), (11, 40)], pointer: CGPoint(x: 10, y: 80), target: 11)
        XCTAssertEqual(result.pointerFrom, 80)
        XCTAssertEqual(result.pointerTo, 170)
    }

    func testRemovedPointerTargetGivesNoPointerAnchor() {
        let result = diff([(10, 60), (11, 40)], [(10, 60)], pointer: CGPoint(x: 10, y: 80), target: 11)
        XCTAssertNil(result.pointerFrom)
        XCTAssertNil(result.pointerTo)
    }

    func testGrowingElementInMiddle() {
        let result = diff([(10, 50), (11, 50), (12, 50)], [(10, 50), (11, 150), (12, 50)])
        XCTAssertEqual(result.unchangedTop, 100, accuracy: 0.5)
        XCTAssertEqual(result.unchangedBottom, 100, accuracy: 0.5)
    }

    func testUnwrappedBodyBlocks() {
        let old = layout([(10, 50), (11, 50)], wrap: false)
        let new = layout([(10, 50), (11, 50), (12, 70)], wrap: false)
        let result = WidgetResizeDiff.compute(old: old.bounds, new: new.bounds, oldHeight: old.height, newHeight: new.height)
        XCTAssertEqual(result.unchangedTop, 100, accuracy: 0.5)
        XCTAssertEqual(result.unchangedBottom, 0, accuracy: 0.5)
    }

    func testStalePointerIsClampedIntoClickedElement() {
        let result = diff([(10, 100), (11, 40)], [(10, 150), (11, 40)], pointer: CGPoint(x: 10, y: 20), target: 11)
        XCTAssertEqual(result.pointerFrom, 100)
        XCTAssertEqual(result.pointerTo, 150)
    }

    func testTinyStepKeepsPreviousSide() {
        XCTAssertTrue(WidgetResizeDiff.keepsBottom(oldHeight: 120, newHeight: 121, unchangedTop: 120, unchangedBottom: 0.3, pointerFrom: nil, pointerTo: nil, click: 90, prefersBottom: false, previous: true))
        XCTAssertFalse(WidgetResizeDiff.keepsBottom(oldHeight: 120, newHeight: 121, unchangedTop: 0, unchangedBottom: 120, pointerFrom: nil, pointerTo: nil, click: 90, prefersBottom: true, previous: false))
    }

    func testSideFollowsClickAgainstUnchangedRegions() {
        XCTAssertFalse(WidgetResizeDiff.keepsBottom(oldHeight: 200, newHeight: 300, unchangedTop: 150, unchangedBottom: 20, pointerFrom: nil, pointerTo: nil, click: 120, prefersBottom: true, previous: nil))
        XCTAssertTrue(WidgetResizeDiff.keepsBottom(oldHeight: 200, newHeight: 300, unchangedTop: 20, unchangedBottom: 100, pointerFrom: nil, pointerTo: nil, click: 150, prefersBottom: false, previous: nil))
        XCTAssertTrue(WidgetResizeDiff.keepsBottom(oldHeight: 200, newHeight: 300, unchangedTop: 20, unchangedBottom: 20, pointerFrom: nil, pointerTo: nil, click: nil, prefersBottom: true, previous: nil))
        XCTAssertTrue(WidgetResizeDiff.keepsBottom(oldHeight: 200, newHeight: 300, unchangedTop: 20, unchangedBottom: 100, pointerFrom: 150, pointerTo: 250, click: 150, prefersBottom: false, previous: nil))
        XCTAssertFalse(WidgetResizeDiff.keepsBottom(oldHeight: 200, newHeight: 300, unchangedTop: 20, unchangedBottom: 100, pointerFrom: 150, pointerTo: 150, click: 150, prefersBottom: true, previous: nil))
    }
}

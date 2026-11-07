import XCTest
@testable import TwidgetRenderer

final class TransitionHandoffTests: XCTestCase {
    private func animator(_ transition: String, property: String = "transform", from: String = "translateX(0px)", to: String = "translateX(190px)") -> WidgetAnimator {
        let animator = WidgetAnimator()
        animator.update(declarations: [7: ["transition": transition, property: from]], now: 9)
        animator.update(declarations: [7: ["transition": transition, property: to]], now: 10)
        return animator
    }

    func testTransformTransitionBakesAndReleasesTheFrameClock() throws {
        let animator = animator("transform 1500ms linear(0, .3, 1.1, 1)")
        let baked = try XCTUnwrap(animator.bakeable(7))
        XCTAssertEqual(baked.start, 10)
        XCTAssertEqual(baked.spec.duration, 1.5)
        XCTAssertEqual(baked.spec.fill, "both")
        XCTAssertFalse(animator.isActive(excluding: [7]))
        XCTAssertEqual(animator.endTime(7), 11.5)
        let track = try XCTUnwrap(WidgetCompositor.bake(baked, base: animator.base(7), box: CGSize(width: 12, height: 12), origin: CGPoint(x: 6, y: 6)))
        XCTAssertGreaterThan(track.transforms.map(\.tx).max() ?? 0, 190)
        let reach = animator.reachableValues(7).compactMap { CSSTransform.parse($0.transform, box: .zero)?.tx }
        XCTAssertGreaterThan(reach.max() ?? 0, 208)
        for step in 0..<100 {
            let progress = Double(step) / 100
            let now = 10 + progress * 1.5
            let expected = animator.sample(now: now)[7]?.first { $0.0 == "transform" }?.1
            let actual = CSSTransform.parse(baked.value(progress).transform, box: .zero)
            let sampled = CSSTransform.parse(expected, box: .zero)
            XCTAssertEqual(actual?.tx ?? -1, sampled?.tx ?? -2, accuracy: 0.002)
            XCTAssertEqual(animator.peek(7, at: now)?.transform, expected)
        }
    }

    func testCompletionEventStillFiresExactlyOnce() throws {
        let animator = animator("opacity 1s linear", property: "opacity", from: "0", to: "1")
        var events: [String] = []
        animator.onEvent = { kind, _, property, duration in events.append("\(kind):\(property):\(duration)") }
        XCTAssertNotNil(animator.bakeable(7))
        _ = animator.sample(now: 11.002)
        _ = animator.sample(now: 12)
        XCTAssertEqual(events, ["transitionend:opacity:1.0"])
        XCTAssertNil(animator.bakeable(7))
        XCTAssertFalse(animator.isActive)
    }

    func testPositiveAndNegativeDelaysPreserveTiming() throws {
        for delay in [-0.4, 0.4] {
            let animator = animator("opacity 1s linear \(delay)s", property: "opacity", from: "0", to: "1")
            let baked = try XCTUnwrap(animator.bakeable(7))
            XCTAssertEqual(baked.spec.delay, delay, accuracy: 1e-9)
            XCTAssertEqual(animator.endTime(7) ?? 0, 11 + delay, accuracy: 1e-9)
            XCTAssertEqual(Double(animator.peek(7, at: 10)?.opacity ?? "") ?? -1, max(0, -delay), accuracy: 1e-9)
        }
    }

    func testReversalStartsAtCurrentPositionAndChangesSignature() throws {
        let animator = animator("transform 1s linear")
        let original = try XCTUnwrap(animator.bakeable(7))
        animator.update(declarations: [7: ["transition": "transform 1s linear", "transform": "translateX(0px)"]], now: 10.4)
        let reversed = try XCTUnwrap(animator.bakeable(7))
        XCTAssertNotEqual(original.spec.name, reversed.spec.name)
        XCTAssertEqual(CSSTransform.parse(reversed.value(0).transform, box: .zero)?.tx ?? -1, 76, accuracy: 0.001)
        XCTAssertEqual(CSSTransform.parse(reversed.value(0.5).transform, box: .zero)?.tx ?? -1, 38, accuracy: 0.001)
        XCTAssertEqual(animator.endTime(7) ?? 0, 11.4, accuracy: 1e-9)
    }

    func testReplayWithoutTransitionCancelsNativeTrack() {
        let animator = animator("transform 1s linear")
        animator.update(declarations: [7: ["transition": "none", "transform": "translateX(0px)"]], now: 10.3)
        XCTAssertNil(animator.bakeable(7))
        XCTAssertFalse(animator.isActive)
    }

    func testLayoutTransitionsKeepExistingSampling() {
        XCTAssertNil(animator("left 1s linear", property: "left", from: "0px", to: "100px").bakeable(7))
        let animator = WidgetAnimator()
        let before = ["transition": "all 1s linear", "left": "0px", "opacity": "0"]
        var after = before
        after["left"] = "100px"
        after["opacity"] = "1"
        animator.update(declarations: [7: before], now: 9)
        animator.update(declarations: [7: after], now: 10)
        XCTAssertNil(animator.bakeable(7))
        XCTAssertTrue(animator.isActive(excluding: []))
    }

    func testCombinedTransitionsKeepIndependentDelaysDurationsAndEvents() throws {
        let animator = WidgetAnimator()
        let transition = "transform 1s linear 0.2s, opacity 2s ease-in-out -0.25s"
        animator.update(declarations: [7: ["transition": transition, "transform": "translateX(0px)", "opacity": "0"]], now: 9)
        animator.update(declarations: [7: ["transition": transition, "transform": "translateX(100px)", "opacity": "1"]], now: 10)
        var completed: [String] = []
        animator.onEvent = { _, _, property, _ in completed.append(property) }
        let baked = try XCTUnwrap(animator.bakeable(7))
        XCTAssertEqual(baked.spec.delay, -0.25)
        XCTAssertEqual(baked.spec.duration, 2)
        XCTAssertEqual(animator.endTime(7) ?? 0, 11.2, accuracy: 1e-9)
        for step in 0..<100 {
            let now = 10 + Double(step) / 100 * 1.2
            let sample = animator.sample(now: now)[7] ?? []
            let candidate = baked.value((now - 9.75) / 2)
            XCTAssertEqual(CSSTransform.parse(candidate.transform, box: .zero)?.tx ?? -1, CSSTransform.parse(sample.first { $0.0 == "transform" }?.1, box: .zero)?.tx ?? -2, accuracy: 0.0001)
            XCTAssertEqual(Double(candidate.opacity ?? "") ?? -1, Double(sample.first { $0.0 == "opacity" }?.1 ?? "") ?? -2, accuracy: 0.0001)
        }
        _ = animator.sample(now: 11.201)
        XCTAssertEqual(completed, ["transform"])
        let continuing = try XCTUnwrap(animator.bakeable(7))
        XCTAssertEqual(continuing.value(0.75).transform, "translateX(100px)")
        XCTAssertEqual(animator.endTime(7), 11.75)
        _ = animator.sample(now: 11.751)
        XCTAssertEqual(completed, ["transform", "opacity"])
        XCTAssertNil(animator.bakeable(7))
    }

    func testCombinedTransitionReversalKeepsBothCurrentValues() throws {
        let animator = WidgetAnimator()
        let transition = "transform 1s linear, opacity 2s linear"
        animator.update(declarations: [7: ["transition": transition, "transform": "translateX(0px)", "opacity": "0"]], now: 9)
        animator.update(declarations: [7: ["transition": transition, "transform": "translateX(100px)", "opacity": "1"]], now: 10)
        animator.update(declarations: [7: ["transition": transition, "transform": "translateX(0px)", "opacity": "0"]], now: 10.5)
        let baked = try XCTUnwrap(animator.bakeable(7))
        XCTAssertEqual(CSSTransform.parse(baked.value(0).transform, box: .zero)?.tx ?? -1, 50, accuracy: 1e-9)
        XCTAssertEqual(Double(baked.value(0).opacity ?? "") ?? -1, 0.25, accuracy: 1e-9)
        XCTAssertEqual(CSSTransform.parse(baked.value(0.25).transform, box: .zero)?.tx ?? -1, 25, accuracy: 1e-9)
        XCTAssertEqual(Double(baked.value(0.25).opacity ?? "") ?? -1, 0.1875, accuracy: 1e-9)
    }

    func testCombinedTransitionsDoNotReplaceConcurrentKeyframes() {
        let animator = WidgetAnimator()
        animator.setStyleSheets(["@keyframes spin { to { transform: rotate(360deg); } }"])
        let common = ["animation": "spin 1s infinite", "transition": "transform 1s, opacity 1s"]
        animator.update(declarations: [7: common.merging(["transform": "translateX(0px)", "opacity": "0"]) { _, value in value }], now: 9)
        animator.update(declarations: [7: common.merging(["transform": "translateX(100px)", "opacity": "1"]) { _, value in value }], now: 10)
        XCTAssertNil(animator.bakeable(7))
    }

    func testTransitionTrackIncludesEasingCornersAndDelayBoundaries() throws {
        let animator = WidgetAnimator()
        let transition = "transform 1s linear(0, .3, 1.1, 1), opacity 2s linear 0.2s"
        animator.update(declarations: [7: ["transition": transition, "transform": "translateX(0px)", "opacity": "0"]], now: 9)
        animator.update(declarations: [7: ["transition": transition, "transform": "translateX(100px)", "opacity": "1"]], now: 10)
        let baked = try XCTUnwrap(animator.bakeable(7))
        let track = try XCTUnwrap(WidgetCompositor.bake(baked, base: animator.base(7), box: CGSize(width: 24, height: 24), origin: CGPoint(x: 12, y: 12)))
        for time in [0.2, 1.0 / 3, 2.0 / 3, 1.0] {
            XCTAssertTrue(track.times.contains { abs($0 * 2.2 - time) < 1e-9 })
        }
        XCTAssertLessThan(track.times.count, 32)
    }

    func testExcessiveEasingCornersRetainSoftwareFallback() throws {
        let values = (0...5000).map { String(Double($0) / 5000) }.joined(separator: ",")
        let animator = animator("transform 1s linear(\(values))")
        let baked = try XCTUnwrap(animator.bakeable(7))
        XCTAssertNil(WidgetCompositor.bake(baked, base: animator.base(7), box: .zero, origin: .zero))
        XCTAssertTrue(animator.isActive(excluding: []))
    }
}

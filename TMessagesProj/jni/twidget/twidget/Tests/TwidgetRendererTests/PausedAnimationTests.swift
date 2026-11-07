import XCTest
@testable import TwidgetRenderer

final class PausedAnimationTests: XCTestCase {
    private func animator() -> WidgetAnimator {
        let animator = WidgetAnimator()
        animator.setStyleSheets(["@keyframes fade { from { opacity: 0; } to { opacity: 1; } }"])
        return animator
    }

    private func opacity(_ animator: WidgetAnimator, _ now: Double) -> Double? {
        animator.sample(now: now)[7]?.first { $0.0 == "opacity" }.flatMap { Double($0.1) }
    }

    func testInitiallyPausedNegativeDelayStaysAtOffset() {
        let animator = animator()
        animator.update(declarations: [7: ["animation": "fade 1s linear -650ms infinite paused"]], now: 10)
        for now in [10.0, 10.2, 15, 100] {
            XCTAssertEqual(opacity(animator, now), 0.65)
            XCTAssertEqual(animator.peek(7, at: now)?.opacity, "0.65")
            XCTAssertEqual(animator.cssAnimations([7], now: now).first?["currentTime"] as? Double, 0)
        }
        XCTAssertFalse(animator.isActive)
    }

    func testPauseResumePreservesElapsedTime() {
        let animator = animator()
        animator.update(declarations: [7: ["animation": "fade 1s linear infinite"]], now: 10)
        XCTAssertEqual(opacity(animator, 10.25), 0.25)
        animator.update(declarations: [7: ["animation": "fade 1s linear infinite paused"]], now: 10.25)
        XCTAssertEqual(opacity(animator, 80), 0.25)
        animator.update(declarations: [7: ["animation": "fade 1s linear infinite paused", "color": "red"]], now: 90)
        XCTAssertEqual(opacity(animator, 95), 0.25)
        animator.update(declarations: [7: ["animation": "fade 1s linear infinite"]], now: 100)
        XCTAssertEqual(opacity(animator, 100), 0.25)
        XCTAssertEqual(opacity(animator, 100.5), 0.75)
        XCTAssertEqual(animator.cssAnimations([7], now: 100).first?["origin"] as? Double, 10_000)
        XCTAssertEqual(animator.cssAnimations([7], now: 100).first?["currentTime"] as? Double, 250)
        XCTAssertEqual(animator.bakeable(7)?.start, 99.75)
        XCTAssertEqual(animator.nextIteration(7, after: 100), 100.75)
        XCTAssertTrue(animator.isActive)
    }

    func testPauseFreezesPositiveDelayAndCompletion() {
        let animator = animator()
        animator.update(declarations: [7: ["animation": "fade 1s linear 1s both paused"]], now: 0)
        XCTAssertEqual(opacity(animator, 20), 0)
        animator.update(declarations: [7: ["animation": "fade 1s linear 1s both"]], now: 20)
        XCTAssertEqual(opacity(animator, 20.5), 0)
        XCTAssertEqual(opacity(animator, 21.5), 0.5)
        animator.update(declarations: [7: ["animation": "fade 1s linear 1s both paused"]], now: 21.5)
        XCTAssertEqual(opacity(animator, 100), 0.5)
        XCTAssertFalse(animator.running[7]?.first?.ended ?? true)
        XCTAssertNil(animator.endTime(7))
        XCTAssertNil(animator.nextIteration(7, after: 100))
        animator.update(declarations: [7: ["animation": "fade 1s linear 1s both"]], now: 100)
        XCTAssertEqual(animator.endTime(7), 100.5)
        XCTAssertEqual(opacity(animator, 100.25), 0.75)
    }
}

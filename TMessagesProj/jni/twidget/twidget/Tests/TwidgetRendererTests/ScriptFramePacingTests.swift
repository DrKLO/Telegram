import XCTest
@testable import TwidgetRenderer

final class ScriptFramePacingTests: XCTestCase {
    private func run(_ pacer: inout ScriptFramePacer, cost: TimeInterval, ticks: Int, start: CFTimeInterval, period: CFTimeInterval = 1.0 / 120) -> (posted: Int, end: CFTimeInterval) {
        var now = start
        var posted = 0
        for _ in 0..<ticks {
            if pacer.due(now) {
                pacer.posted(now)
                pacer.answered(now + cost)
                posted += 1
            }
            now += period
        }
        return (posted, now)
    }

    func testCheapFramesRunAtDisplayRate() {
        var pacer = ScriptFramePacer(budget: 0.001, fallbackInterval: 1.0 / 60 - 0.002)
        let result = run(&pacer, cost: 0.0003, ticks: 120, start: 0)
        XCTAssertEqual(result.posted, 120)
        XCTAssertTrue(pacer.fullRate)
    }

    func testExpensiveFramesFallBackToSixtyHertz() {
        var pacer = ScriptFramePacer(budget: 0.001, fallbackInterval: 1.0 / 60 - 0.002)
        let result = run(&pacer, cost: 0.004, ticks: 120, start: 0)
        XCTAssertFalse(pacer.fullRate)
        XCTAssertLessThanOrEqual(result.posted, 62)
        XCTAssertGreaterThanOrEqual(result.posted, 59)
    }

    func testFramesReturnToDisplayRateWhenWorkShrinks() {
        var pacer = ScriptFramePacer(budget: 0.001, fallbackInterval: 1.0 / 60 - 0.002)
        let slow = run(&pacer, cost: 0.004, ticks: 60, start: 0)
        XCTAssertFalse(pacer.fullRate)
        let fast = run(&pacer, cost: 0.0002, ticks: 240, start: slow.end)
        XCTAssertTrue(pacer.fullRate)
        XCTAssertGreaterThan(fast.posted, 200)
    }

    func testHysteresisHoldsNearTheBudget() {
        var pacer = ScriptFramePacer(budget: 0.001, fallbackInterval: 1.0 / 60 - 0.002)
        _ = run(&pacer, cost: 0.003, ticks: 60, start: 0)
        XCTAssertFalse(pacer.fullRate)
        _ = run(&pacer, cost: 0.0009, ticks: 240, start: 1)
        XCTAssertFalse(pacer.fullRate)
    }

    func testUnansweredFramesMeasureFromTheFirstPost() {
        var pacer = ScriptFramePacer(budget: 0.001, fallbackInterval: 1.0 / 60 - 0.002)
        pacer.posted(0)
        pacer.posted(1.0 / 120)
        pacer.answered(0.012)
        XCTAssertEqual(pacer.cost, 0.012, accuracy: 1e-9)
        XCTAssertFalse(pacer.fullRate)
    }

    func testRendererBudgetKnob() {
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 300)
        XCTAssertEqual(renderer.scriptFrameCost, 0)
        renderer.scriptFrameBudget = .infinity
        renderer.destroy()
    }
}

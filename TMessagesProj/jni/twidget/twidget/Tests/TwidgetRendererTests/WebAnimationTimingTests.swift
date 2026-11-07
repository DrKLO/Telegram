import XCTest
@testable import TwidgetRenderer

final class WebAnimationTimingTests: XCTestCase {
    private func timing(duration: Double = 1, delay: Double = 0, endDelay: Double = 0, iterations: Double = 1, iterationStart: Double = 0, direction: String = "normal", fill: String = "none", easing: String = "linear") -> WebTiming {
        var t = WebTiming()
        t.duration = duration
        t.delay = delay
        t.endDelay = endDelay
        t.iterations = iterations
        t.iterationStart = iterationStart
        t.direction = direction
        t.fill = fill
        t.easing = WebEasing.parse(easing)!
        return t
    }

    func testPhasesAndFill() {
        let t = timing(delay: 1, fill: "none")
        XCTAssertEqual(t.compute(localTime: 0.5)?.phase, .before)
        XCTAssertNil(t.compute(localTime: 0.5)?.activeTime)
        XCTAssertEqual(t.compute(localTime: 1.5)?.progress ?? -1, 0.5, accuracy: 1e-9)
        XCTAssertEqual(t.compute(localTime: 2)?.phase, .after)
        XCTAssertNil(t.compute(localTime: 2)?.progress)
        let both = timing(delay: 1, fill: "both")
        XCTAssertEqual(both.compute(localTime: 0)?.progress, 0)
        XCTAssertEqual(both.compute(localTime: 5)?.progress, 1)
        XCTAssertEqual(both.compute(localTime: 5)?.currentIteration, 0)
        XCTAssertNil(both.compute(localTime: nil))
    }

    func testBoundaryUsesPlaybackDirection() {
        let t = timing(fill: "none")
        XCTAssertEqual(t.compute(localTime: 0, forwards: true)?.phase, .active)
        XCTAssertEqual(t.compute(localTime: 0, forwards: false)?.phase, .before)
        XCTAssertEqual(t.compute(localTime: 1, forwards: true)?.phase, .after)
        XCTAssertEqual(t.compute(localTime: 1, forwards: false)?.phase, .active)
    }

    func testIterationsAndDirections() {
        let t = timing(iterations: 3, direction: "alternate", fill: "forwards")
        XCTAssertEqual(t.compute(localTime: 0.25)?.progress ?? -1, 0.25, accuracy: 1e-9)
        XCTAssertEqual(t.compute(localTime: 1.25)?.progress ?? -1, 0.75, accuracy: 1e-9)
        XCTAssertEqual(t.compute(localTime: 1.25)?.currentIteration, 1)
        XCTAssertEqual(t.compute(localTime: 3)?.progress, 1)
        XCTAssertEqual(t.compute(localTime: 3)?.currentIteration, 2)
        let r = timing(iterations: 2, direction: "alternate-reverse", fill: "forwards")
        XCTAssertEqual(r.compute(localTime: 0.25)?.progress ?? -1, 0.75, accuracy: 1e-9)
        XCTAssertEqual(r.compute(localTime: 2)?.progress, 1)
        let reverse = timing(direction: "reverse")
        XCTAssertEqual(reverse.compute(localTime: 0.2)?.progress ?? -1, 0.8, accuracy: 1e-9)
    }

    func testIterationStart() {
        let t = timing(iterations: 2, iterationStart: 0.5, fill: "both")
        XCTAssertEqual(t.compute(localTime: 0)?.progress, 0.5)
        XCTAssertEqual(t.compute(localTime: 0)?.currentIteration, 0)
        XCTAssertEqual(t.compute(localTime: 0.75)?.progress ?? -1, 0.25, accuracy: 1e-9)
        XCTAssertEqual(t.compute(localTime: 0.75)?.currentIteration, 1)
        XCTAssertEqual(t.compute(localTime: 2)?.progress ?? -1, 0.5, accuracy: 1e-9)
        XCTAssertEqual(t.compute(localTime: 2)?.currentIteration, 2)
        let whole = timing(iterations: 1, iterationStart: 1, fill: "both")
        XCTAssertEqual(whole.compute(localTime: 1)?.progress, 1)
        XCTAssertEqual(whole.compute(localTime: 1)?.currentIteration, 1)
    }

    func testZeroDurationAndInfinite() {
        let zero = timing(duration: 0, iterations: 2, fill: "both")
        XCTAssertEqual(zero.compute(localTime: -1)?.progress, 0)
        XCTAssertEqual(zero.compute(localTime: 0)?.progress, 1)
        XCTAssertEqual(zero.compute(localTime: 0)?.currentIteration, 1)
        let infinite = timing(iterations: .infinity, fill: "both")
        XCTAssertEqual(infinite.activeDuration, .infinity)
        XCTAssertEqual(infinite.compute(localTime: 10.25)?.progress ?? -1, 0.25, accuracy: 1e-9)
        XCTAssertEqual(infinite.compute(localTime: 10.25)?.currentIteration, 10)
    }

    func testEndDelayClipsActiveInterval() {
        let t = timing(duration: 2, endDelay: -1, fill: "forwards")
        XCTAssertEqual(t.endTime, 1)
        XCTAssertEqual(t.compute(localTime: 1.5)?.phase, .after)
        XCTAssertEqual(t.compute(localTime: 1.5)?.progress ?? -1, 0.75, accuracy: 1e-9)
    }

    func testStepsBeforeFlag() {
        let start = WebEasing.parse("steps(1, start)")!
        XCTAssertEqual(start.apply(0, before: false), 1)
        XCTAssertEqual(start.apply(0, before: true), 0)
        let t = timing(delay: 1, fill: "backwards", easing: "step-start")
        XCTAssertEqual(t.compute(localTime: 0)?.progress, 0)
        XCTAssertEqual(t.compute(localTime: 1)?.progress, 1)
        let both = WebEasing.parse("steps(3, jump-both)")!
        XCTAssertEqual(both.apply(0, before: false), 0.25)
        XCTAssertEqual(both.apply(1, before: false), 1)
        let none = WebEasing.parse("steps(3, jump-none)")!
        XCTAssertEqual(none.apply(0, before: false), 0)
        XCTAssertEqual(none.apply(0.5, before: false), 0.5)
        XCTAssertEqual(none.apply(1, before: false), 1)
        XCTAssertNil(WebEasing.parse("steps(1, jump-none)"))
        XCTAssertNil(WebEasing.parse("cubic-bezier(2, 0, 0, 1)"))
        XCTAssertNil(WebEasing.parse("bogus"))
    }

    private func animation(_ object: [String: Any]) -> WebAnimation {
        var base: [String: Any] = ["aid": 1, "target": 5, "startTime": 0, "playbackRate": 1]
        base.merge(object) { $1 }
        return WebAnimation.parse(base)!
    }

    func testKeyframeInterpolationWithImplicitEnds() {
        let a = animation(["keyframes": [["offset": 0.5, "values": ["opacity": "0"]]], "timing": ["duration": 1000]])
        let underlying: (String) -> String = { _ in "1" }
        XCTAssertEqual(a.values(progress: 0.25, iteration: 0, underlying: underlying).first?.1, "0.5")
        XCTAssertEqual(a.values(progress: 0.75, iteration: 0, underlying: underlying).first?.1, "0.5")
        XCTAssertEqual(a.values(progress: 1, iteration: 0, underlying: underlying).first?.1, "1")
    }

    func testKeyframeEasingAndComposite() {
        let a = animation(["keyframes": [["offset": 0, "easing": "steps(2)", "values": ["width": "0px"]], ["offset": 1, "values": ["width": "100px"]]], "timing": ["duration": 1000]])
        XCTAssertEqual(a.values(progress: 0.7, iteration: 0, underlying: { _ in "0px" }).first?.1, "50px")
        let add = animation(["keyframes": [["offset": 0, "values": ["transform": "translateX(0px)"]], ["offset": 1, "values": ["transform": "translateX(100px)"]]], "composite": "add", "timing": ["duration": 1000]])
        XCTAssertEqual(add.values(progress: 0.5, iteration: 0, underlying: { _ in "rotate(10deg)" }).first?.1, "rotate(10deg) translateX(50px)")
        let accumulate = animation(["keyframes": [["offset": 0, "values": ["margin-left": "0px"]], ["offset": 1, "values": ["margin-left": "10px"]]], "iterationComposite": "accumulate", "timing": ["duration": 1000, "iterations": 3]])
        XCTAssertEqual(accumulate.values(progress: 0.5, iteration: 2, underlying: { _ in "0px" }).first?.1, "25px")
    }

    func testLocalTimeAndParsingLimits() {
        let running = animation(["keyframes": [], "startTime": 1000, "playbackRate": 2, "timing": ["duration": 1000]])
        XCTAssertEqual(running.localTime(1.5) ?? -1, 1000, accuracy: 1e-6)
        let held = animation(["keyframes": [], "holdTime": 250, "timing": ["duration": 1000]])
        XCTAssertEqual(held.localTime(99), 250)
        XCTAssertFalse(held.isRunning)
        XCTAssertNil(WebAnimation.parse(["aid": 1, "target": 1, "keyframes": [], "playbackRate": 1]))
        XCTAssertNil(WebAnimation.parse(["aid": 1, "target": 1, "keyframes": [], "startTime": 0, "playbackRate": 1e9]))
        XCTAssertNil(WebAnimation.parse(["aid": 1, "target": 1, "keyframes": [["offset": 0.5], ["offset": 0.2]], "startTime": 0]))
        let infinite = WebAnimation.parse(["aid": 1, "target": 1, "keyframes": [], "startTime": 0, "timing": ["duration": 1000, "iterations": NSNull()]])
        XCTAssertEqual(infinite?.timing.iterations, .infinity)
    }

    func testBakeMatchesSampling() {
        let a = animation(["keyframes": [["offset": 0, "values": ["opacity": "0"]], ["offset": 1, "values": ["opacity": "1"]]], "timing": ["duration": 1000, "iterations": 2, "iterationStart": 0.5, "direction": "alternate", "easing": "ease-in", "fill": "forwards"]])
        let bake = WidgetWebAnimations.bake(a, base: [:])!
        XCTAssertEqual(bake.cycle, 2)
        XCTAssertEqual(bake.begin ?? -1, 0, accuracy: 1e-9)
        XCTAssertEqual(bake.repeatCount, 1)
        for step in 0...20 {
            let s = Double(step) / 20
            let expected = a.values(at: s * 2, underlying: { _ in "1" })?.first?.1
            XCTAssertEqual(Double(bake.value(s).opacity ?? "") ?? -1, Double(expected ?? "") ?? -2, accuracy: 1e-6, "s=\(s)")
        }
        let reversed = animation(["keyframes": [["offset": 0, "values": ["opacity": "0"]], ["offset": 1, "values": ["opacity": "1"]]], "playbackRate": -1, "startTime": 1000, "timing": ["duration": 1000, "fill": "both"]])
        let back = WidgetWebAnimations.bake(reversed, base: [:])!
        XCTAssertEqual(back.begin ?? 0, 0, accuracy: 1e-9)
        XCTAssertEqual(back.value(0).opacity, "1")
        XCTAssertEqual(back.value(1).opacity, "0")
    }
}

final class TransformListPaddingTests: XCTestCase {
    func testShorterListIsPaddedWithIdentities() {
        XCTAssertEqual(CSSInterpolate.mix("scale(0.5)", "scale(1.6) rotate(-30deg)", 0.5), "scale(1.05, 1.05) rotate(-15deg)")
        XCTAssertEqual(CSSInterpolate.mix("scale(1.6) rotate(-30deg)", "scale(1)", 0.5), "scale(1.3, 1.3) rotate(-15deg)")
    }

    func testBezierExtrapolatesOutsideTheUnitInterval() {
        let easeIn = WebEasing.parse("ease-in")!
        XCTAssertEqual(easeIn.apply(1.5, before: false), 1 + 0.5 / 0.58, accuracy: 1e-9)
        let overshoot = WebEasing.parse("cubic-bezier(0.3, 1.6, 0.6, 1)")!
        XCTAssertEqual(overshoot.apply(-0.1, before: false), -0.1 * 1.6 / 0.3, accuracy: 1e-9)
        XCTAssertEqual(overshoot.apply(1.2, before: false), 1, accuracy: 1e-9)
    }
}

final class WebAnimationHandoffTests: XCTestCase {
    private func emulate(_ bake: WidgetWebAnimations.Bake, _ track: WidgetCompositor.Track, at time: CFTimeInterval) -> Float? {
        let begin = bake.begin ?? 0
        let local = (time - begin) * Double(bake.speed)
        let total = Double(bake.repeatCount) * bake.cycle
        func at(_ s: Double) -> Float {
            if s <= track.times[0] {
                return track.opacities[0]
            }
            for i in 1..<track.times.count where s <= track.times[i] {
                let span = track.times[i] - track.times[i - 1]
                let f = span == 0 ? 1 : (s - track.times[i - 1]) / span
                return track.opacities[i - 1] + Float(f) * (track.opacities[i] - track.opacities[i - 1])
            }
            return track.opacities[track.opacities.count - 1]
        }
        if local < 0 {
            return bake.caBackwards ? at(0) : nil
        }
        if local >= total {
            guard bake.caForwards else {
                return nil
            }
            let cycles = Double(bake.repeatCount)
            let fraction = cycles - floor(cycles)
            return at(fraction == 0 ? 1 : fraction)
        }
        let cycles = local / bake.cycle
        return at(cycles - floor(cycles))
    }

    private func check(_ object: [String: Any], file: StaticString = #filePath, line: UInt = #line) {
        var base: [String: Any] = ["aid": 1, "target": 5, "startTime": 1000, "playbackRate": 1, "keyframes": [["offset": 0, "values": ["opacity": "0"]], ["offset": 0.3, "easing": "ease-in", "values": ["opacity": "0.8"]], ["offset": 1, "values": ["opacity": "0.2"]]]]
        base.merge(object) { $1 }
        let animation = WebAnimation.parse(base)!
        guard let bake = WidgetWebAnimations.bake(animation, base: [:]), let track = WidgetCompositor.bake(WidgetAnimator.Baked(spec: CSSAnimationSpec(), start: 0, value: { progress in let v = bake.value(progress); return (v.transform, v.opacity, nil, nil) }), base: [:], box: CGSize(width: 100, height: 40), origin: CGPoint(x: 50, y: 20)) else {
            XCTFail("not bakeable", file: file, line: line)
            return
        }
        var worst: Float = 0
        for step in 0...400 {
            let time = 0.8037 + Double(step) * 0.01
            let expected = min(1, max(0, animation.values(at: time, underlying: { _ in "1" }).flatMap { $0.first.map { Float(Double($0.1)!) } } ?? 1))
            let shown = emulate(bake, track, at: time) ?? 1
            worst = max(worst, abs(shown - expected))
        }
        XCTAssertLessThan(worst, 0.004, "\(object)", file: file, line: line)
    }

    func testCoreAnimationTimingMatchesSampling() {
        check(["timing": ["duration": 500, "fill": "both"]])
        check(["timing": ["duration": 400, "iterations": 3, "direction": "alternate", "easing": "ease-in-out"]])
        check(["timing": ["duration": 300, "iterations": NSNull(), "iterationStart": 0.4, "direction": "alternate-reverse"]])
        check(["timing": ["duration": 500, "delay": 300, "iterations": 2.5, "fill": "both", "easing": "steps(4, jump-end)"]])
        check(["timing": ["duration": 500, "iterations": 2.5, "fill": "forwards", "easing": "steps(4, jump-both)"]])
        let before = WebAnimation.parse(["aid": 1, "target": 5, "startTime": 1000, "keyframes": [], "timing": ["duration": 500, "delay": 300, "fill": "both", "easing": "steps(4, jump-both)"]])!
        XCTAssertNil(WidgetWebAnimations.bake(before, base: [:]))
        check(["timing": ["duration": 600, "iterations": 2, "fill": "forwards", "endDelay": 200], "playbackRate": 2])
        check(["timing": ["duration": 400, "iterations": 3, "fill": "both"], "iterationComposite": "accumulate"])
        check(["timing": ["duration": 1500, "fill": "both", "delay": 200], "playbackRate": -1.5, "startTime": 3500])
        check(["timing": ["duration": 700, "iterations": 2, "fill": "backwards", "delay": 100], "playbackRate": -1, "startTime": 2600])
    }
}

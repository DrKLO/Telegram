import XCTest
@testable import TwidgetRenderer

final class CSSAnimationTests: XCTestCase {
    func testInterpolatesMatchingShapes() {
        XCTAssertEqual(CSSInterpolate.mix("translateY(10px)", "translateY(20px)", 0.5), "translateY(15px)")
        XCTAssertEqual(CSSInterpolate.mix("0", "1", 0.25), "0.25")
        XCTAssertEqual(CSSInterpolate.mix("translate(0, 0) rotate(0deg)", "translate(10px, -4px) rotate(90deg)", 0.5), "translate(5px, -2px) rotate(45deg)")
    }

    func testInterpolatesFromNoneTransform() {
        XCTAssertEqual(CSSInterpolate.mix("none", "scale(1.2)", 0.5), "scale(1.1)")
        XCTAssertEqual(CSSInterpolate.mix("translateX(8px)", "none", 0.5), "translateX(4px)")
    }

    func testInterpolatesColors() {
        XCTAssertEqual(CSSInterpolate.mix("#000", "#ffffff", 0.5), "rgba(127.5, 127.5, 127.5, 1)")
        XCTAssertEqual(CSSInterpolate.mix("rgba(255,0,0,0)", "red", 0.5), "rgba(255, 0, 0, 0.5)")
    }

    func testFallsBackToDiscreteSwitch() {
        XCTAssertEqual(CSSInterpolate.mix("block", "none", 0.4), "block")
        XCTAssertEqual(CSSInterpolate.mix("block", "none", 0.6), "none")
    }

    func testTimingFunctions() {
        XCTAssertEqual(CSSTiming.ease.function(0.5), 0.8024, accuracy: 0.001)
        XCTAssertEqual(CSSTiming.parse("linear")!.function(0.3), 0.3, accuracy: 1e-9)
        XCTAssertEqual(CSSTiming.parse("steps(4)")!.function(0.3), 0.25, accuracy: 1e-9)
        XCTAssertEqual(CSSTiming.parse("cubic-bezier(0, 0, 1, 1)")!.function(0.4), 0.4, accuracy: 0.001)
    }

    func testParsesKeyframes() {
        var rules: [String: [CSSKeyframe]] = [:]
        CSSKeyframes.parse(".a{color:red} @keyframes pulse { 0%, 100% { opacity: 1 } 50% { opacity: .4; transform: scale(1.1) } } @-webkit-keyframes x{to{left:0}}", into: &rules)
        XCTAssertEqual(rules["pulse"]?.map { $0.offset }, [0, 0.5, 1])
        XCTAssertEqual(rules["pulse"]?[1].declarations.map { $0.name }, ["opacity", "transform"])
        XCTAssertEqual(rules["x"]?.first?.offset, 1)
    }

    func testParsesAnimationShorthand() {
        let specs = CSSAnimationSpec.list(["animation": "pulse 1.2s ease-in-out 200ms infinite alternate both, spin 3s linear"])
        XCTAssertEqual(specs.count, 2)
        XCTAssertEqual(specs[0].name, "pulse")
        XCTAssertEqual(specs[0].duration, 1.2, accuracy: 1e-9)
        XCTAssertEqual(specs[0].delay, 0.2, accuracy: 1e-9)
        XCTAssertEqual(specs[0].iterations, .infinity)
        XCTAssertEqual(specs[0].direction, "alternate")
        XCTAssertEqual(specs[0].fill, "both")
        XCTAssertEqual(specs[1].timingText, "linear")
    }

    func testParsesTransitions() {
        let specs = CSSTransitionSpec.list(["transition": "transform .3s ease, opacity 150ms"])
        XCTAssertEqual(specs.map { $0.property }, ["transform", "opacity"])
        XCTAssertEqual(specs[1].duration, 0.15, accuracy: 1e-9)
    }

    func testResolvesLightDark() {
        XCTAssertEqual(CSSTheme.resolve("--fg: light-dark(#000, #fff); --b: light-dark(rgba(0,0,0,.1), rgba(255,255,255,.2))", theme: "dark"), "--fg: #fff; --b: rgba(255,255,255,.2)")
        XCTAssertEqual(CSSTheme.resolve("color: light-dark(#000, #fff)", theme: "light"), "color: #000")
    }

    func testAnimatorSamplesKeyframes() {
        let animator = WidgetAnimator()
        animator.setStyleSheets(["@keyframes fade { from { opacity: 0 } to { opacity: 1 } }"])
        animator.update(declarations: [5: ["animation": "fade 1s linear"]], now: 100)
        XCTAssertTrue(animator.isActive)
        XCTAssertEqual(animator.sample(now: 100.25)[5]?.first?.1, "0.25")
        XCTAssertNil(animator.sample(now: 101.5)[5])
        XCTAssertFalse(animator.isActive)
    }

    func testAnimatorRunsTransitions() {
        let animator = WidgetAnimator()
        animator.update(declarations: [2: ["transition": "transform 1s linear", "transform": "translateX(0px)"]], now: 10)
        animator.update(declarations: [2: ["transition": "transform 1s linear", "transform": "translateX(100px)"]], now: 11)
        XCTAssertEqual(animator.sample(now: 11.5)[2]?.first?.1, "translateX(50px)")
        XCTAssertNil(animator.sample(now: 12.1)[2])
    }
}

final class CSSTransformTests: XCTestCase {
    func testParsesTransformFunctions() {
        let box = CGSize(width: 40, height: 20)
        XCTAssertEqual(CSSTransform.parse("none", box: box), .identity)
        XCTAssertEqual(CSSTransform.parse("translateX(10px)", box: box), CGAffineTransform(translationX: 10, y: 0))
        XCTAssertEqual(CSSTransform.parse("translate(50%, -50%)", box: box), CGAffineTransform(translationX: 20, y: -10))
        XCTAssertEqual(CSSTransform.parse("scale(2)", box: box), CGAffineTransform(scaleX: 2, y: 2))
        let rotated = CSSTransform.parse("rotate(90deg)", box: box)!
        XCTAssertEqual(rotated.b, 1, accuracy: 1e-9)
        XCTAssertEqual(CSSTransform.parse("rotate(0.25turn)", box: box)!.b, 1, accuracy: 1e-9)
        let combined = CSSTransform.parse("translateX(10px) scale(2)", box: box)!
        XCTAssertEqual(CGPoint(x: 1, y: 0).applying(combined).x, 12, accuracy: 1e-9)
        XCTAssertNil(CSSTransform.parse("rotateX(30deg)", box: box))
        XCTAssertNil(CSSTransform.parse("translate(calc(1px + 2px))", box: box))
        XCTAssertNil(CSSTransform.parse("perspective(10px)", box: box))
    }

    func testParsesTransformOrigin() {
        let box = CGSize(width: 40, height: 20)
        XCTAssertEqual(CSSTransform.origin(nil, box: box), CGPoint(x: 20, y: 10))
        XCTAssertEqual(CSSTransform.origin("left top", box: box), CGPoint(x: 0, y: 0))
        XCTAssertEqual(CSSTransform.origin("top right", box: box), CGPoint(x: 40, y: 0))
        XCTAssertEqual(CSSTransform.origin("25% 10px", box: box), CGPoint(x: 10, y: 10))
        XCTAssertEqual(CSSTransform.origin("bottom", box: box), CGPoint(x: 20, y: 20))
    }
}

final class CSSLinearEasingTests: XCTestCase {
    func testLinearFunctionPoints() {
        let curve = CSSTiming.parse("linear(0, 0.25, 1)")!
        XCTAssertEqual(curve.function(0.5), 0.25, accuracy: 1e-9)
        XCTAssertEqual(curve.function(0.75), 0.625, accuracy: 1e-9)
        let positioned = CSSTiming.parse("linear(0, 0.9 25%, 1)")!
        XCTAssertEqual(positioned.function(0.25), 0.9, accuracy: 1e-9)
        XCTAssertEqual(positioned.function(0.625), 0.95, accuracy: 1e-9)
        let overshoot = CSSTiming.parse("linear(0, 1.1 80%, 1)")!
        XCTAssertEqual(overshoot.function(0.8), 1.1, accuracy: 1e-9)
        XCTAssertEqual(CSSTiming.parse("linear(0,0.030,0.120,1.000)")!.function(1), 1, accuracy: 1e-9)
        XCTAssertNil(CSSTiming.parse("linear(0, x, 1)"))
    }
}

final class CSSOvershootTests: XCTestCase {
    func testInterpolationExtrapolatesForOvershootingCurves() {
        XCTAssertEqual(CSSInterpolate.mix("translateX(0px)", "translateX(100px)", 1.1), "translateX(110px)")
        XCTAssertEqual(CSSInterpolate.mix("0", "1", -0.1), "-0.1")
    }

    func testInterpolatesFromNoneWithSpacedArguments() {
        XCTAssertEqual(CSSInterpolate.mix("none", "skewX(20deg) translate(10px, 5px)", 0.5), "skewX(10deg) translate(5px, 2.5px)")
    }
}

final class MixedUnitTranslationTests: XCTestCase {
    func testMixedUnitTranslationsInterpolateAdditively() {
        XCTAssertEqual(CSSInterpolate.mix("translateX(30px)", "translateX(-20%)", 0.25), "translateX(22.5px) translateX(-5%)")
        XCTAssertEqual(CSSInterpolate.mix("translate(10px, 0)", "translate(50%, 4px)", 0.5), "translate(5px, 0) translate(25%, 2px)")
        XCTAssertEqual(CSSInterpolate.mix("translateX(30px)", "translateX(-20%)", 0), "translateX(30px)")
        XCTAssertEqual(CSSInterpolate.mix("translateX(30px)", "translateX(-20%)", 1), "translateX(-20%)")
    }

    func testOtherMismatchesStillFlipAtHalfway() {
        XCTAssertEqual(CSSInterpolate.mix("translateX(30px) rotate(10deg)", "translateX(-20%) rotate(20deg)", 0.25), "translateX(30px) rotate(10deg)")
        XCTAssertEqual(CSSInterpolate.mix("scale(1)", "translateX(2%)", 0.75), "translateX(2%)")
        XCTAssertEqual(CSSInterpolate.mix("10px", "20%", 0.25), "10px")
    }
}

final class TransformPrimitiveTests: XCTestCase {
    func testDifferentArityInterpolatesThroughCommonPrimitives() {
        XCTAssertEqual(CSSInterpolate.mix("scale(1)", "scale(1.6,0.7)", 0.5), "scale(1.3, 0.85)")
        XCTAssertEqual(CSSInterpolate.mix("translateX(10px)", "translate(30px, 8px)", 0.5), "translate(20px, 4px)")
        XCTAssertEqual(CSSInterpolate.mix("translateY(10px) scale(2)", "translate(4px, 20px) scaleX(3)", 0.5), "translate(2px, 15px) scale(2.5, 1.5)")
        XCTAssertEqual(CSSInterpolate.mix("skewX(10deg)", "skew(20deg, 4deg)", 0.5), "skew(15deg, 2deg)")
        XCTAssertEqual(CSSInterpolate.mix("scale(1)", "rotate(10deg)", 0.25), "scale(1)")
    }

    func testTimesEvaluateCalc() {
        XCTAssertEqual(CSSTime.parse("calc(2 * 30ms)")!, 0.06, accuracy: 1e-9)
        XCTAssertEqual(CSSTime.parse("calc(30ms * 2 + 0.1s)")!, 0.16, accuracy: 1e-9)
        XCTAssertEqual(CSSTime.parse("calc((1s - 200ms) / 4)")!, 0.2, accuracy: 1e-9)
        XCTAssertEqual(CSSTime.parse("calc(0 * 30ms)")!, 0, accuracy: 1e-9)
        XCTAssertNil(CSSTime.parse("calc(2 * 3)"))
        XCTAssertNil(CSSTime.parse("calc(1s * 2s)"))
        XCTAssertNil(CSSTime.parse("calc(1s / 0)"))
        XCTAssertNil(CSSTime.parse("calc(1s + 2)"))
        XCTAssertNil(CSSTime.parse("calc(1s"))
        XCTAssertNil(CSSTime.parse("calc(" + String(repeating: "(", count: 40) + "1s" + String(repeating: ")", count: 41)))
        XCTAssertNil(CSSTime.parse("calc(" + String(repeating: "1s + ", count: 200) + "1s)"))
    }

    func testTransitionShorthandTakesCalcDelay() {
        let specs = CSSTransitionSpec.list(["transition": "height 560ms cubic-bezier(.33 , 1 , .68 , 1) calc(3 * 30ms) , opacity 350ms ease"])
        XCTAssertEqual(specs.map { $0.property }, ["height", "opacity"])
        XCTAssertEqual(specs[0].duration, 0.56, accuracy: 1e-9)
        XCTAssertEqual(specs[0].delay, 0.09, accuracy: 1e-9)
    }
}

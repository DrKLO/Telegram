import XCTest
@testable import TwidgetRenderer

final class HandoffBakeTests: XCTestCase {
    private func baked(_ keyframes: String, _ animation: String, base: [String: String] = [:]) -> (WidgetAnimator.Baked, [String: String])? {
        let animator = WidgetAnimator()
        animator.setStyleSheets([keyframes])
        var declarations = base
        declarations["animation"] = animation
        animator.update(declarations: [7: declarations], now: 0)
        return animator.bakeable(7).map { ($0, animator.base(7)) }
    }

    private func check(_ keyframes: String, _ animation: String, box: CGSize = CGSize(width: 40, height: 40), file: StaticString = #filePath, line: UInt = #line) {
        guard let (item, base) = baked(keyframes, animation) else {
            return XCTFail("not bakeable", file: file, line: line)
        }
        let origin = CGPoint(x: box.width / 2, y: box.height / 2)
        guard let track = WidgetCompositor.bake(item, base: base, box: box, origin: origin) else {
            return XCTFail("bake failed", file: file, line: line)
        }
        XCTAssertEqual(track.times.first, 0, file: file, line: line)
        XCTAssertEqual(track.times.last, 1, file: file, line: line)
        XCTAssertLessThan(track.times.count, 1200, file: file, line: line)
        var index = 0
        for step in 0...2000 {
            let p = Double(step) / 2000
            while index + 1 < track.times.count - 1 && track.times[index + 1] < p {
                index += 1
            }
            let t0 = track.times[index], t1 = track.times[min(index + 1, track.times.count - 1)]
            let f = t1 > t0 ? CGFloat((p - t0) / (t1 - t0)) : 0
            let a = track.transforms[index], b = track.transforms[min(index + 1, track.transforms.count - 1)]
            let lerp = CGAffineTransform(a: a.a + (b.a - a.a) * f, b: a.b + (b.b - a.b) * f, c: a.c + (b.c - a.c) * f, d: a.d + (b.d - a.d) * f, tx: a.tx + (b.tx - a.tx) * f, ty: a.ty + (b.ty - a.ty) * f)
            let value = item.value(p)
            let truth = CSSTransform.parse(value.transform ?? base["transform"], box: box) ?? .identity
            for corner in [CGPoint(x: -origin.x, y: -origin.y), CGPoint(x: origin.x, y: origin.y)] {
                let x = corner.applying(lerp), y = corner.applying(truth)
                XCTAssertLessThan(hypot(x.x - y.x, x.y - y.y), 0.2, "at \(p)", file: file, line: line)
            }
            let opacity = max(0, min(1, Double(value.opacity ?? base["opacity"] ?? "1") ?? 1))
            let oa = Double(track.opacities[index]), ob = Double(track.opacities[min(index + 1, track.opacities.count - 1)])
            XCTAssertEqual(oa + (ob - oa) * Double(f), opacity, accuracy: 0.01, "at \(p)", file: file, line: line)
        }
    }

    func testBakedTracksFollowTheAnimator() {
        check("@keyframes spin { to { transform: rotate(360deg); } }", "spin 1s linear infinite")
        check("@keyframes spin { to { transform: rotate(360deg); } }", "spin 1s ease-in-out infinite")
        check("@keyframes pop { from { transform: scale(.5) rotate(-20deg); opacity: 0; } to { transform: scale(1) rotate(0deg); opacity: 1; } }", "pop .8s cubic-bezier(.34,1.56,.64,1)")
        check("@keyframes m { 0% { transform: translate(0, 0); } 30% { transform: translate(30px, 10px); animation-timing-function: cubic-bezier(.3,1.6,.6,1); } 100% { transform: translate(-10px, 40px); } }", "m 2s ease infinite alternate")
        check("@keyframes f { 50% { opacity: .2; } }", "f 1s linear(0, 0.25 40%, 1) infinite")
        check("@keyframes k { to { transform: skewX(20deg) translate(10px, 5px); } }", "k 1.4s ease-in-out infinite")
    }

    func testStepsBakeIntoNarrowJumps() {
        check("@keyframes t { to { transform: translateY(20px); } }", "t 1s steps(4) infinite")
        check("@keyframes t { to { transform: translateY(20px); opacity: 0; } }", "t 1s steps(3, jump-start) infinite")
        check("@keyframes t { 0% { transform: rotate(0deg); } 50% { transform: rotate(90deg); animation-timing-function: steps(2); } 100% { transform: rotate(180deg); } }", "t 2s ease infinite alternate")
        guard let (item, base) = baked("@keyframes t { to { transform: translateY(20px); } }", "t 1s steps(4) infinite"), let track = WidgetCompositor.bake(item, base: base, box: CGSize(width: 40, height: 40), origin: CGPoint(x: 20, y: 20)) else {
            return XCTFail("steps did not bake")
        }
        XCTAssertLessThan(track.times.count, 16)
        for jump in [0.25, 0.5, 0.75] {
            guard let after = track.times.firstIndex(where: { $0 >= jump }) else {
                return XCTFail("no keyframe at \(jump)")
            }
            XCTAssertEqual(track.times[after], jump, accuracy: 1e-9)
            XCTAssertLessThanOrEqual(jump - track.times[after - 1], WidgetCompositor.jumpWidth)
            XCTAssertGreaterThan(jump - track.times[after - 1], 0)
        }
        XCTAssertEqual(track.times, track.times.sorted())
    }

    func testRasterScaleRoundsUpToQuarterPixels() {
        XCTAssertEqual(WidgetCompositor.rasterScale(2, 1), 2)
        XCTAssertEqual(WidgetCompositor.rasterScale(2, 1.2), 2.5)
        XCTAssertEqual(WidgetCompositor.rasterScale(2, 1.25), 2.5)
        XCTAssertEqual(WidgetCompositor.rasterScale(1, 1.2), 1.25)
        XCTAssertEqual(WidgetCompositor.rasterScale(2, 1.37), 2.75)
        XCTAssertEqual(WidgetCompositor.rasterScale(3, 3), 9)
        for magnification in stride(from: 1.0, through: 3.0, by: 0.013) {
            for display in [1.0, 2.0, 3.0] {
                let scale = WidgetCompositor.rasterScale(CGFloat(display), CGFloat(magnification))
                XCTAssertGreaterThanOrEqual(scale, CGFloat(display * magnification) - 1e-9)
                XCTAssertEqual((scale * 4).rounded(), scale * 4, accuracy: 1e-9)
            }
        }
    }

    func testUnparseableTransformsStayOnTheFrameClock() {
        if let (item, base) = baked("@keyframes p { to { transform: perspective(100px) rotateY(40deg); } }", "p 1s infinite") {
            XCTAssertNil(WidgetCompositor.bake(item, base: base, box: CGSize(width: 40, height: 40), origin: CGPoint(x: 20, y: 20)))
        }
    }

    func testOnlyCompositableSingleAnimationsBake() {
        XCTAssertNil(baked("@keyframes c { to { background-color: red; } }", "c 1s infinite"))
        XCTAssertNil(baked("@keyframes a { to { opacity: 0; } } @keyframes b { to { transform: scale(2); } }", "a 1s infinite, b 1s infinite"))
        XCTAssertNil(baked("@keyframes a { to { opacity: 0; } }", "a 1s infinite paused"))
    }

    func testResolvesVariablesAndCalc() {
        let variables = ["--w": "21.5", "--a": "var(--b)", "--b": "3"]
        let resolve = { (text: String) in CSSTransform.resolve(text) { variables[$0] } }
        XCTAssertEqual(resolve("translateX(calc(var(--w) * 2px))"), "translateX(43.000000px)")
        XCTAssertEqual(resolve("rotate(calc((var(--a) + 1) * 10deg))"), "rotate(40.000000deg)")
        XCTAssertEqual(resolve("translate(calc(10px - -5px), 0)"), "translate(15.000000px, 0)")
        XCTAssertNil(resolve("translateX(var(--missing))"))
        XCTAssertEqual(resolve("translateX(var(--missing, 4px))"), "translateX(4px)")
        XCTAssertNil(resolve("translateX(calc(10px * 2px))"))
        XCTAssertNil(resolve("translateX(calc(10px + 5%))"))
        XCTAssertNil(resolve("translateX(calc(1px / 0))"))
        XCTAssertNil(CSSTransform.resolve("var(--x)") { _ in "var(--x)" })
    }

    func testResolverSurvivesHostileInput() {
        var rng = SystemRandomNumberGenerator()
        let pieces = ["calc(", "var(--w", ")", "(", "*", "/", "+", "-", "1e308", "px", "deg", " ", ",", "var(--a)", "0", "nan", "%"]
        for _ in 0..<20000 {
            var text = ""
            for _ in 0..<Int.random(in: 1...40, using: &rng) {
                text += pieces.randomElement(using: &rng)!
            }
            _ = CSSTransform.resolve(text) { $0 == "--a" ? "var(--a)" : "2" }
            _ = Calc.evaluate(text)
        }
        let deep = String(repeating: "calc(", count: 2000) + "1px" + String(repeating: ")", count: 2000)
        _ = CSSTransform.resolve(deep) { _ in nil }
    }
}

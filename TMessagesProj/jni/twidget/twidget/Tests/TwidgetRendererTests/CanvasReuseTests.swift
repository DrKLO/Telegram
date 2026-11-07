import XCTest
@testable import TwidgetRenderer

final class CanvasReuseTests: XCTestCase {
    private func canvas() -> WidgetCanvas {
        WidgetCanvas(width: 240, height: 160, allowance: { WidgetCanvas.maxPixels })
    }

    private func pixels(_ canvas: WidgetCanvas) -> Data {
        canvas.pixels(x: 0, y: 0, w: 240, h: 160)
    }

    func testWarmTextMatchesFreshShapingAcrossPaintAndStateChanges() {
        let warm = canvas()
        for font in ["12px monospace", "bold 22px Georgia", "italic 16px sans-serif"] {
            for text in ["123.45", "مرحبا بالعالم", "Cafe\u{301} 日本語", "👩🏽‍🚀"] {
                warm.run([["s", "font", font]])
                _ = warm.measure(text)
                for stroke in [false, true] {
                    for baseline in ["alphabetic", "top", "middle", "bottom"] {
                        let commands: [Any] = [
                            ["c", "clearRect", 0, 0, 240, 160], ["c", "save"],
                            ["s", "font", font], ["s", "textAlign", baseline == "middle" ? "right" : "center"], ["s", "textBaseline", baseline],
                            ["s", "fillStyle", baseline == "top" ? "#00ff00" : "#e03060"],
                            ["s", "strokeStyle", baseline == "top" ? "#ffcc00" : "#2070c0"], ["s", "globalAlpha", baseline == "bottom" ? 0.3 : 0.7],
                            ["c", "translate", 120, 80], ["c", "rotate", 0.17],
                            ["c", stroke ? "strokeText" : "fillText", text, 0, 0, 90], ["c", "restore"]
                        ]
                        let fresh = canvas()
                        fresh.run(commands)
                        warm.run(commands)
                        XCTAssertEqual(pixels(warm), pixels(fresh), "\(font) \(text) \(baseline) stroke=\(stroke)")
                    }
                }
            }
        }
    }

    func testTextEvictionAndResizeDoNotChangeMeasurementOrPixels() {
        let warm = canvas()
        let expected = warm.measure("oldest label")
        for i in 0..<(WidgetCanvas.maxCachedTextLines * 3) {
            _ = warm.measure("label \(i)")
        }
        XCTAssertLessThanOrEqual(warm.cachedTextLineCount, WidgetCanvas.maxCachedTextLines)
        XCTAssertEqual(warm.measure("oldest label"), expected)
        let count = warm.cachedTextLineCount
        for i in 0..<20 { _ = warm.measure(String(repeating: "x", count: 300) + "\(i)") }
        XCTAssertEqual(warm.cachedTextLineCount, count)
        warm.resize(240, 160)
        XCTAssertEqual(warm.cachedTextLineCount, 0)
        let commands: [Any] = [["c", "fillText", "oldest label", 10, 30]]
        warm.run(commands)
        let fresh = canvas()
        fresh.run(commands)
        XCTAssertEqual(pixels(warm), pixels(fresh))
    }

    func testGradientCacheInvalidatesAfterStopsChangeOrHandleIsReused() {
        for kind in ["linear", "radial"] {
            let setup: [Any] = [
                kind == "linear" ? ["g", 1, kind, 0, 0, 240, 160] : ["g", 1, kind, 120, 80, 0, 120, 80, 100],
                ["gs", 1, 1, "#ff0000"], ["gs", 1, 0, "#0000ff"], ["s", "fillStyle", ["g": 1]]
            ]
            let draw: [Any] = [["c", "clearRect", 0, 0, 240, 160], ["c", "fillRect", 0, 0, 240, 160]]
            let warm = canvas()
            warm.run(setup + draw)
            let before = pixels(warm)
            let mutation: [Any] = [["gs", 1, 0.5, "#00ff00"], ["gs", 1, 0.5, "#ffff00"]]
            warm.run(mutation + draw)
            let fresh = canvas()
            fresh.run(setup + mutation + draw)
            XCTAssertEqual(pixels(warm), pixels(fresh))
            XCTAssertNotEqual(pixels(warm), before)
            let reuse: [Any] = [["g", 1, "linear", 240, 0, 0, 160], ["gs", 1, 0, "#00ffff"], ["gs", 1, 1, "#000000"]]
            warm.run(reuse + draw)
            let reused = canvas()
            reused.run(reuse + [["s", "fillStyle", ["g": 1]]] + draw)
            XCTAssertEqual(pixels(warm), pixels(reused))
        }
    }
}

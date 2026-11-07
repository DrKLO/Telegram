import AppKit
import XCTest
import LiteHTML
@testable import TwidgetRenderer

final class StopwatchLayoutTests: XCTestCase {
    func testStopwatchFillsViewportAndKeepsResumeOnOneLine() throws {
        let url = URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("../../../widget-sandbox/corpus/htmlbubbles-ported/11-live-scripts/10-pixel-bare.html").standardized
        let html = try String(contentsOf: url).replacingOccurrences(of: ">Start</button>", with: ">Resume</button>")
        let css = WidgetThemeStyle.masterCSS(WidgetThemeStyle.variables(["colorScheme": "light"]))
        for width: CGFloat in [200, 240, 330, 440, 600] {
            let document = LHDocument(html: html, css: css, previous: nil, viewportSize: CGSize(width: width, height: 600))
            _ = document.layout(maxWidth: width, preferredWidth: width)
            let root = document.bounds(matching: "#root")
            let left = document.bounds(matching: "#left")
            let right = document.bounds(matching: "#right")
            XCTAssertEqual(root.minX, 0, accuracy: 0.1)
            XCTAssertEqual(root.width, width, accuracy: 0.1)
            XCTAssertEqual(left.minY, right.minY, accuracy: 0.1)
            XCTAssertEqual(left.height, right.height, accuracy: 0.1)
            XCTAssertLessThanOrEqual(left.maxX, right.minX)
            XCTAssertLessThanOrEqual(right.maxX, root.maxX - 14)
            let fragments = document.textFragments(maxWidth: width).filter { $0.text.contains("Resume") }
            XCTAssertEqual(fragments.map(\.text).joined(), "Resume")
            XCTAssertTrue(fragments.allSatisfy { right.contains($0.frame) })
        }
    }

    func testStopwatchChartUsesAvailableWidth() throws {
        let url = URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("../../../widget-sandbox/corpus/htmlbubbles-ported/11-live-scripts/10-pixel-bare.html").standardized
        let chart = "<div id=\"list\"><div class=\"bars\"><div class=\"br\"><span class=\"n muted\">L1</span><div class=\"track\" id=\"track\"><div class=\"fill\" id=\"fill\" style=\"width:100%;background-color:#34c759\"></div></div><span class=\"v\">00:01.00</span></div></div></div>"
        let html = try String(contentsOf: url).replacingOccurrences(of: "<div id=\"list\"></div>", with: chart)
        let css = WidgetThemeStyle.masterCSS(WidgetThemeStyle.variables(["colorScheme": "light"]))
        for width: CGFloat in [200, 240, 330, 440, 600] {
            let document = LHDocument(html: html, css: css, previous: nil, viewportSize: CGSize(width: width, height: 600))
            _ = document.layout(maxWidth: width, preferredWidth: width)
            let track = document.bounds(matching: "#track")
            let fill = document.bounds(matching: "#fill")
            let value = document.bounds(matching: ".br .v")
            XCTAssertGreaterThan(track.width, 0)
            XCTAssertEqual(track.width, fill.width, accuracy: 0.1)
            XCTAssertLessThanOrEqual(track.maxX, value.minX + 0.1)
            XCTAssertLessThanOrEqual(value.maxX, width - 14)
        }
    }
}

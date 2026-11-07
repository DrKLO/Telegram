import XCTest
import AppKit
import LiteHTML
@testable import TwidgetRenderer

final class EmptyClipPathTests: XCTestCase {
    private func redPixels(_ html: String) -> Int {
        let document = LHDocument(html: html, css: nil, previous: nil)
        document.layout(maxWidth: 200, preferredWidth: 200)
        guard let image = document.copyImage(maxWidth: 200, rect: CGRect(x: 0, y: 0, width: 200, height: 60), scale: 1), let data = image.dataProvider?.data, let bytes = CFDataGetBytePtr(data) else {
            XCTFail("no image")
            return -1
        }
        var count = 0
        for y in 0 ..< image.height {
            for x in 0 ..< image.width {
                let p = bytes + y * image.bytesPerRow + x * (image.bitsPerPixel / 8)
                let a = Int(p[0]), b = Int(p[1]), c = Int(p[2])
                if (a > 200 && b < 60 && c < 60) || (c > 200 && b < 60 && a < 60) {
                    count += 1
                }
            }
        }
        return count
    }

    func testZeroWidthRoundedClipHidesOverflowingChild() {
        let html = "<html><body style=\"margin:0\"><div style=\"width:0;height:40px;overflow:hidden;border-radius:8px\"><div style=\"width:150px;height:40px;background:#ff0000\"></div></div></body></html>"
        XCTAssertEqual(redPixels(html), 0)
    }

    func testRoundedClipStillShowsContent() {
        let html = "<html><body style=\"margin:0\"><div style=\"width:100px;height:40px;overflow:hidden;border-radius:8px\"><div style=\"width:150px;height:40px;background:#ff0000\"></div></div></body></html>"
        XCTAssertGreaterThan(redPixels(html), 3000)
    }

    func testCanvasClipWithEmptyPathDrawsNothing() {
        let canvas = WidgetCanvas(width: 40, height: 40, allowance: { WidgetCanvas.maxPixels })
        canvas.run([["c", "beginPath"], ["c", "clip"], ["s", "fillStyle", "#ff0000"], ["c", "fillRect", 0, 0, 40, 40]])
        let data = canvas.pixels(x: 0, y: 0, w: 40, h: 40)
        XCTAssertEqual(data.count, 40 * 40 * 4)
        XCTAssertTrue(data.allSatisfy { $0 == 0 })
    }

    func testCanvasClipWithPathStillDraws() {
        let canvas = WidgetCanvas(width: 40, height: 40, allowance: { WidgetCanvas.maxPixels })
        canvas.run([["c", "beginPath"], ["c", "rect", 0, 0, 20, 40], ["c", "clip"], ["s", "fillStyle", "#ff0000"], ["c", "fillRect", 0, 0, 40, 40]])
        let data = canvas.pixels(x: 0, y: 0, w: 40, h: 40)
        XCTAssertEqual(data[0], 255)
        XCTAssertEqual(data[(39 * 4)], 0)
    }
}

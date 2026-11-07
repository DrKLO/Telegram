import XCTest
import AppKit
import LiteHTML
@testable import TwidgetRenderer

final class LayerShadowTests: XCTestCase {
    private final class Drawer: NSObject, CALayerDelegate {
        let html: String
        var pixels: Data?
        var expected: Data?

        init(html: String) {
            self.html = html
        }

        func draw(_ layer: CALayer, in context: CGContext) {
            let document = LHDocument(html: html, css: "body{margin:0;font:24px -apple-system;color:black;background:white}p{margin:10px}")
            _ = document.layout(maxWidth: 300, preferredWidth: 300)
            context.setAllowsFontSmoothing(false)
            context.setShouldAntialias(true)
            document.draw(in: context, origin: .zero, clip: layer.bounds, baseTransform: context.ctm)
            context.flush()
            if let data = context.data {
                var bytes = Data()
                for y in 0..<context.height {
                    bytes.append(data.advanced(by: y * context.bytesPerRow).assumingMemoryBound(to: UInt8.self), count: context.width * 4)
                }
                pixels = bytes
            }
            if let image = document.copyImage(maxWidth: 300, rect: layer.bounds, scale: layer.contentsScale), let data = image.dataProvider?.data {
                var bytes = Data()
                let pointer = CFDataGetBytePtr(data)!
                for y in 0..<image.height {
                    bytes.append(pointer.advanced(by: y * image.bytesPerRow), count: image.width * 4)
                }
                expected = bytes
            }
        }
    }

    func testBackingViewShadowPositionMatchesBitmap() throws {
        let document = LHDocument(html: "<p style='text-shadow:3px 4px 0 red'>Shadow cache</p>", css: "body{margin:0;font:24px -apple-system;color:black;background:white}p{margin:10px}")
        _ = document.layout(maxWidth: 300, preferredWidth: 300)
        let view = WidgetBackingStoreDocumentView(frame: CGRect(x: 0, y: 0, width: 300, height: 80))
        view.document = document
        let actual = try XCTUnwrap(view.bitmapImageRepForCachingDisplay(in: view.bounds))
        view.cacheDisplay(in: view.bounds, to: actual)
        let image = try XCTUnwrap(document.copyImage(maxWidth: 300, rect: view.bounds, scale: CGFloat(actual.pixelsWide) / 300))
        let expected = NSBitmapImageRep(cgImage: image)
        func shadowCenter(_ bitmap: NSBitmapImageRep) throws -> CGPoint {
            var count = 0.0, x = 0.0, y = 0.0
            for row in 0..<bitmap.pixelsHigh {
                for column in 0..<bitmap.pixelsWide {
                    if let color = bitmap.colorAt(x: column, y: row)?.usingColorSpace(.deviceRGB), color.redComponent > color.greenComponent + 0.4 && color.redComponent > color.blueComponent + 0.4 {
                        count += 1
                        x += Double(column)
                        y += Double(row)
                    }
                }
            }
            XCTAssertGreaterThan(count, 100)
            return CGPoint(x: x / max(1, count), y: y / max(1, count))
        }
        let actualCenter = try shadowCenter(actual)
        let expectedCenter = try shadowCenter(expected)
        XCTAssertEqual(actualCenter.x, expectedCenter.x, accuracy: 0.5)
        XCTAssertEqual(actualCenter.y, expectedCenter.y, accuracy: 0.5)
    }

    func testLayerDrawingMatchesBitmapShadows() throws {
        let fixtures = [
            "<p style='text-shadow:3px 4px 0 red'>Shadow cache</p>",
            "<p style='text-shadow:0 0 0 transparent,3px 4px 0 red'>Shadow cache</p>",
            "<p style='text-shadow:0 0 0 transparent,-3px 4px 2px blue,3px -4px 2px green'>Shadow cache</p>",
            "<p style='text-shadow:3px 4px 3px red;text-decoration:underline'>Shadow cache</p>",
            "<p style='text-shadow:3px 4px 20px red'>Shadow cache</p>",
            "<p style='text-shadow:3px 4px 2px red;transform:rotate(4deg) scale(.9)'>Shadow cache</p>",
            "<div style='margin:10px;width:120px;height:40px;background:blue;box-shadow:4px 5px 3px red'></div>",
            "<div style='margin:10px;width:120px;height:40px;background:blue;box-shadow:inset 4px 5px 3px red'></div>"
        ]
        defer { LHDocument.setTextBatchingEnabled(true) }
        for batching in [false, true] {
            LHDocument.setTextBatchingEnabled(batching)
            for scale: CGFloat in [1, 2] {
                for html in fixtures {
                    let layer = CALayer()
                    layer.bounds = CGRect(x: 0, y: 0, width: 300, height: 80)
                    layer.contentsScale = scale
                    layer.isGeometryFlipped = true
                    let drawer = Drawer(html: html)
                    layer.delegate = drawer
                    layer.setNeedsDisplay()
                    layer.displayIfNeeded()
                    let actual = try XCTUnwrap(drawer.pixels)
                    let expected = try XCTUnwrap(drawer.expected)
                    XCTAssertEqual(actual.count, expected.count)
                    let differing = zip(actual, expected).filter { abs(Int($0) - Int($1)) > 8 }.count
                    XCTAssertLessThan(Double(differing) / Double(expected.count), 0.001, "scale \(scale), batching \(batching): \(html)")
                }
            }
        }
    }
}

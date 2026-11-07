import XCTest
import AppKit
import LiteHTML
@testable import TwidgetRenderer

final class ScrollPaddingTests: XCTestCase {
    func testPaddedScrollTilesMatchSoftwareRendering() throws {
        for padding in ["0", "0 16px 96px", "13px 19px 31px 17px"] {
            for scale: CGFloat in [1, 2] {
                let document = LHDocument(html: "<body style='margin:0;background:#fff'><div id='list' style='margin:11px 7px;width:180px;height:140px;box-sizing:border-box;overflow:auto;padding:\(padding);border:3px solid #00f;background:#0f0'><div style='width:300px;height:280px;background:#f00'><div style='height:41px;background:#ff0'></div><div style='margin:17px;width:32px;height:33px;background:#f0f'></div></div></div></body>", css: nil)
                _ = document.layout(maxWidth: 240, preferredWidth: 240)
                let info = try XCTUnwrap(document.scrollers(byAttribute: "id")["list"])
                let viewport = try XCTUnwrap(info["viewport"] as? NSValue).rectValue
                let page = CGRect(x: 0, y: 0, width: 240, height: 180)
                for requested in [CGPoint.zero, CGPoint(x: 23, y: 37), CGPoint(x: 500, y: 500)] {
                    let offset = document.setScrollOffset(requested, attribute: "id", value: "list")
                    document.setScrollContentExclusions([], attribute: "id")
                    let reference = try XCTUnwrap(document.copyImage(maxWidth: 240, rect: page, scale: scale))
                    document.setScrollContentExclusions(["list"], attribute: "id")
                    let base = try XCTUnwrap(document.copyImage(maxWidth: 240, rect: page, scale: scale))
                    let tile = try XCTUnwrap(document.copyScrollContent(attribute: "id", value: "list", rect: CGRect(origin: offset, size: viewport.size), scale: scale))
                    let composed = try XCTUnwrap(CGContext(data: nil, width: reference.width, height: reference.height, bitsPerComponent: 8, bytesPerRow: reference.width * 4, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
                    composed.draw(base, in: CGRect(x: 0, y: 0, width: reference.width, height: reference.height))
                    composed.draw(tile, in: CGRect(x: viewport.minX * scale, y: (page.height - viewport.maxY) * scale, width: viewport.width * scale, height: viewport.height * scale))
                    let actual = try XCTUnwrap(composed.makeImage())
                    let a = bytes(reference), b = bytes(actual)
                    XCTAssertEqual(zip(a, b).filter { $0 != $1 }.count, 0, "padding=\(padding) scale=\(scale) offset=\(offset)")
                }
            }
        }
    }

    private func bytes(_ image: CGImage) -> [UInt8] {
        var result = [UInt8](repeating: 0, count: image.width * image.height * 4)
        let context = CGContext(data: &result, width: image.width, height: image.height, bitsPerComponent: 8, bytesPerRow: image.width * 4, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
        return result
    }
}

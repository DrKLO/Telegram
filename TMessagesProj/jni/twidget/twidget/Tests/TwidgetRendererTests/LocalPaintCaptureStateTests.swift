import XCTest
import AppKit
import LiteHTML

final class LocalPaintCaptureStateTests: XCTestCase {
    private func pixels(_ image: CGImage?) throws -> Data {
        let image = try XCTUnwrap(image)
        return try XCTUnwrap(image.dataProvider?.data) as Data
    }

    private func snapshot(_ document: LHDocument, scale: CGFloat) throws -> Data {
        var ink = CGRect.zero, box = CGRect.zero
        return try pixels(document.copyImageOfElement(attribute: "id", value: "wrapper", scale: scale, inkBounds: &ink, borderBox: &box))
    }

    func testRejectedPaintPreservesFollowingCaptureAndRaster() throws {
        let space = CGColorSpace(name: CGColorSpace.sRGB)!
        let context = try XCTUnwrap(CGContext(data: nil, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        context.setFillColor(CGColor(red: 1, green: 0, blue: 0, alpha: 1))
        context.fill(CGRect(x: 0, y: 0, width: 1, height: 1))
        let image = try XCTUnwrap(context.makeImage())
        let png = try XCTUnwrap(NSBitmapImageRep(cgImage: image).representation(using: .png, properties: [:]))
        let imageURL = "data:image/png;base64," + png.base64EncodedString()
        let html = """
        <style>
        body{margin:0;background:white;font:13px Menlo}
        .panel{margin:3px 24px;width:140px;height:36px;overflow:hidden;border-radius:9px}
        #image{background-image:url('\(imageURL)');background-size:8px 8px}
        #radial{background:radial-gradient(red,blue)}
        #marker{display:list-item;list-style-type:disc;list-style-position:inside}
        #valid{background:linear-gradient(90deg,#163c42,#263858);color:white;border:1px solid green}
        </style>
        <div id='image' class='panel'>Image</div>
        <div id='radial' class='panel'>Gradient</div>
        <div id='marker' class='panel'>Marker</div>
        <div id='valid' class='panel'>Valid <span>capture</span></div>
        """
        let document = LHDocument(html: html, css: nil)
        _ = document.layout(maxWidth: 200, preferredWidth: 200)
        let rect = CGRect(x: 0, y: 0, width: 200, height: 180)
        func pixels(_ scale: CGFloat) throws -> Data {
            let image = try XCTUnwrap(document.copyImage(maxWidth: rect.width, rect: rect, scale: scale))
            return try XCTUnwrap(image.dataProvider?.data) as Data
        }
        for scale: CGFloat in [1, 2] {
            let expectedKey = try XCTUnwrap(document.localPaintKey(attribute: "id", value: "valid", scale: scale))
            let expectedPixels = try pixels(scale)
            for id in ["image", "radial", "marker"] {
                XCTAssertNil(document.localPaintKey(attribute: "id", value: id, scale: scale), id)
                XCTAssertEqual(document.localPaintKey(attribute: "id", value: "valid", scale: scale), expectedKey, id)
                XCTAssertEqual(try pixels(scale), expectedPixels, id)
            }
        }
    }

    func testAncestorCaptureIncludesScrollOwnedContentAndRestoresExclusions() throws {
        let html = """
        <style>
        body{margin:0;background:white}
        #wrapper{position:relative;width:140px;height:90px;background:white}
        .scroller{width:100px;height:40px;overflow:hidden;background:black}
        #separate{position:absolute;left:110px;top:0;width:20px;height:20px;background:magenta}
        </style>
        <div id='wrapper'><div id='scroller' class='scroller'><div style='height:20px;background:red'></div><div style='height:20px;background:blue'></div><div style='height:80px;background:lime'></div></div><div id='separate'></div></div>
        <div id='outside' class='scroller'><div style='height:80px;background:yellow'></div></div>
        """
        let document = LHDocument(html: html, css: nil)
        _ = document.layout(maxWidth: 140, preferredWidth: 140)
        XCTAssertEqual(document.setScrollOffset(CGPoint(x: 0, y: 20), attribute: "id", value: "scroller").y, 20)
        let rect = CGRect(x: 0, y: 0, width: 140, height: 130)
        for scale: CGFloat in [1, 2] {
            document.setScrollContentExclusions([], attribute: "id")
            document.setPaintExclusions([], attribute: "id")
            let complete = try snapshot(document, scale: scale)
            document.setPaintExclusions(["separate"], attribute: "id")
            let expectedSnapshot = try snapshot(document, scale: scale)
            XCTAssertNotEqual(expectedSnapshot, complete)
            let expectedKey = try XCTUnwrap(document.localPaintKey(attribute: "id", value: "wrapper", scale: scale))
            let included = try pixels(document.copyImage(maxWidth: rect.width, rect: rect, scale: scale))
            document.setScrollContentExclusions(["scroller", "outside"], attribute: "id")
            let excluded = try pixels(document.copyImage(maxWidth: rect.width, rect: rect, scale: scale))
            XCTAssertNotEqual(excluded, included)
            XCTAssertEqual(document.localPaintKey(attribute: "id", value: "wrapper", scale: scale), expectedKey)
            XCTAssertEqual(try pixels(document.copyImage(maxWidth: rect.width, rect: rect, scale: scale)), excluded)
            XCTAssertEqual(try snapshot(document, scale: scale), expectedSnapshot)
            XCTAssertEqual(try pixels(document.copyImage(maxWidth: rect.width, rect: rect, scale: scale)), excluded)
            XCTAssertEqual((document.scrollers(byAttribute: "id")["scroller"]?["offset"] as? NSValue)?.pointValue.y, 20)
        }
    }

    func testAncestorBackdropCaptureIncludesOwnedScrollContentWithColdAndWarmTrace() throws {
        let html = """
        <style>
        body{margin:0;background:white}
        #wrapper{position:relative;width:100px;height:60px;background:blue}
        #scroller{width:100px;height:60px;overflow:hidden}
        #content{height:120px;background:red}
        #glass{position:absolute;left:0;top:0;width:100px;height:60px;backdrop-filter:blur(3px);background:#ffffff22}
        </style>
        <div id='wrapper'><div id='scroller'><div id='content'></div></div><div id='glass'></div></div>
        """
        func page() -> LHDocument {
            let document = LHDocument(html: html, css: nil)
            _ = document.layout(maxWidth: 100, preferredWidth: 100)
            return document
        }
        for scale: CGFloat in [1, 2] {
            let expected = try snapshot(page(), scale: scale)
            for primed in [false, true] {
                let document = page()
                document.setScrollContentExclusions(["scroller"], attribute: "id")
                if primed {
                    _ = document.paintOrder(attribute: "id", values: ["wrapper", "scroller", "content", "glass"], includingScrollContent: true)
                }
                XCTAssertEqual(try snapshot(document, scale: scale), expected, "primed=\(primed) scale=\(scale)")
            }
        }
    }
}

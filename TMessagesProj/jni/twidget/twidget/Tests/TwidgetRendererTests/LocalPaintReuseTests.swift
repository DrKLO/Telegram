import XCTest
import AppKit
import LiteHTML
@testable import TwidgetRenderer

final class LocalPaintReuseTests: XCTestCase {
    private func html(width: String = "400px", color: String = "red", font: Int = 12, track: String = "", child: String = "", text: String = "Active") -> String {
        return "<style>body{margin:0}#parent{position:relative;width:\(width);height:80px;color:\(color);font:\(font)px Menlo}#track{position:absolute;left:25%;top:8px;width:300px;height:32px;display:flex;transform:translateX(-10px);\(track)}.pill{width:100px;flex-shrink:0;background:linear-gradient(90deg,#163c42,#263858);\(child)}</style><div id='parent'><div id='track'><span class='pill'>\(text)</span><span class='pill'>Queue</span><span class='pill'>Ready</span></div></div>"
    }

    private func page(_ source: String, previous: LHDocument? = nil) -> LHDocument {
        let document = LHDocument(html: source, css: nil, previous: previous)
        _ = document.layout(maxWidth: 500, preferredWidth: 500)
        return document
    }

    private func key(_ document: LHDocument, scale: CGFloat = 2) throws -> Data {
        return try XCTUnwrap(document.localPaintKey(attribute: "id", value: "track", scale: scale))
    }

    func testResolvedKeySurvivesParentResizeAndDocumentTransfer() throws {
        for scale: CGFloat in [1, 2] {
            var document = page(html().replacingOccurrences(of: "<div id='parent'>", with: "<div id='parent' style='width:400px'>"))
            let first = try key(document, scale: scale)
            for width in ["360px", "420px", "400px"] {
                document = try XCTUnwrap(LHDocument(previous: document, styles: ["parent": "width:\(width)"], attribute: "id", css: nil))
                _ = document.layout(maxWidth: 500, preferredWidth: 500)
                XCTAssertTrue(document.reusedPrevious)
                XCTAssertEqual(try key(document, scale: scale), first, "width=\(width) scale=\(scale)")
            }
        }
    }

    func testResolvedKeyInvalidatesPaintAndGeometryChanges() throws {
        let variants = [
            html(color: "blue"), html(font: 16), html(text: "Changed"),
            html(child: "width:25%"), html(child: "padding-left:3px"),
            html(child: "background:linear-gradient(90deg,red,blue)"),
            html(child: "border:1px solid red"), html(child: "letter-spacing:2px"),
            html(child: "text-decoration:underline"), html(track: "top:8.25px"),
        ]
        for source in variants {
            let before = page(html())
            let baseline = try key(before)
            let after = page(source, previous: before)
            XCTAssertNotEqual(try key(after), baseline, source)
        }
        let document = page(html())
        XCTAssertNotEqual(try key(document, scale: 1), try key(document, scale: 2))
    }

    func testUnsupportedPaintFallsBack() {
        for css in ["filter:blur(1px)", "backdrop-filter:blur(1px)", "text-shadow:1px 1px red", "box-shadow:1px 1px red", "clip-path:circle(40%)", "position:sticky"] {
            XCTAssertNil(page(html(track: css)).localPaintKey(attribute: "id", value: "track", scale: 2), css)
        }
        XCTAssertNil(page(html(child: "transform:rotate(2deg)")).localPaintKey(attribute: "id", value: "track", scale: 2))
        XCTAssertNil(page(html(text: "<input value='a'>")).localPaintKey(attribute: "id", value: "track", scale: 2))
        XCTAssertNil(page(html(text: "<svg width='10' height='10'><rect width='10' height='10' fill='red'/></svg>")).localPaintKey(attribute: "id", value: "track", scale: 2))
    }

    func testUnsupportedPaintPreservesBroadSignatureReuseAfterLocalCacheInvalidation() throws {
        let space = CGColorSpace(name: CGColorSpace.sRGB)!
        let imageContext = try XCTUnwrap(CGContext(data: nil, width: 2, height: 2, bitsPerComponent: 8, bytesPerRow: 8, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        imageContext.setFillColor(CGColor(red: 0, green: 0, blue: 1, alpha: 1))
        imageContext.fill(CGRect(x: 0, y: 0, width: 2, height: 2))
        let image = try XCTUnwrap(imageContext.makeImage())
        let png = try XCTUnwrap(NSBitmapImageRep(cgImage: image).representation(using: .png, properties: [:]))
        let imageURL = "data:image/png;base64," + png.base64EncodedString()

        for unsupported in ["shadow", "image"] {
            for startsSupported in [false, true] {
                let dom = WidgetDOM()
                dom.apply(["op": "el", "id": 1, "tag": "body", "adopt": "body", "attrs": [["style", "margin:0;background:white;width:500px;height:100px"]]])
                dom.apply(["op": "el", "id": 11, "tag": "div", "attrs": [["style", "position:absolute;left:40px;top:16px;width:180px;height:48px;background:#163c42;transform:translateX(0px)"]]])
                dom.apply(["op": "ins", "id": 11, "parent": 1])
                func addUnsupportedPaint() {
                    if unsupported == "shadow" {
                        dom.apply(["op": "style", "id": 11, "name": "box-shadow", "value": "2px 3px 4px #f00"])
                    } else {
                        dom.apply(["op": "el", "id": 12, "tag": "img", "attrs": [["src", imageURL], ["style", "width:16px;height:16px"]]])
                        dom.apply(["op": "ins", "id": 12, "parent": 11])
                    }
                }
                if !startsSupported {
                    addUnsupportedPaint()
                }
                let host = CALayer()
                host.bounds = CGRect(x: 0, y: 0, width: 500, height: 100)
                host.isGeometryFlipped = true
                let compositor = WidgetCompositor(host: host)
                compositor.scriptedCandidates = [11]
                let animator = WidgetAnimator()
                let initialRasters = WidgetCompositor.totalPromotions
                var document: LHDocument?
                for step in 0...5 {
                    if startsSupported && step == 2 {
                        addUnsupportedPaint()
                    }
                    dom.apply(["op": "style", "id": 11, "name": "transform", "value": "translateX(\(step * 2)px)"])
                    document = page(dom.serialize(theme: "light"), previous: document)
                    let current = try XCTUnwrap(document)
                    let localKey = current.localPaintKey(attribute: WidgetDOM.idAttribute, value: "11", scale: 2)
                    let state = "paint=\(unsupported) startsSupported=\(startsSupported) step=\(step)"
                    if startsSupported && step < 2 {
                        XCTAssertNotNil(localKey, state)
                    } else {
                        XCTAssertNil(localKey, state)
                    }
                    compositor.update(document: current, animator: animator, dom: dom, declarations: [:], scale: 2, space: space)
                    XCTAssertEqual(compositor.ids, [11], state)
                    XCTAssertTrue(compositor.applyScripted([11], dom: dom, document: current), state)
                    let transitionRasters = startsSupported && step >= 2 ? 1 : 0
                    XCTAssertEqual(WidgetCompositor.totalPromotions, initialRasters + 1 + transitionRasters, state)
                }
            }
        }
    }

    func testFractionalPlacementFallsBackUntilPaintAndOriginStabilize() throws {
        let widths: [CGFloat] = [400, 400.25, 400.5, 400.5, 400, 400]
        let promoted = [true, false, false, true, false, true]
        let rasterCounts = [1, 1, 1, 2, 2, 3]
        let rect = CGRect(x: 0, y: 0, width: 500, height: 100)
        let space = CGColorSpace(name: CGColorSpace.sRGB)!
        for scale: CGFloat in [1, 2] {
            let dom = WidgetDOM()
            dom.apply(["op": "el", "id": 1, "tag": "body", "adopt": "body", "attrs": [["style", "margin:0;background:white;width:500px;height:100px;font:12px Menlo"]]])
            func element(_ id: Int, parent: Int, style: String) {
                dom.apply(["op": "el", "id": id, "tag": "div", "attrs": [["style", style]]])
                dom.apply(["op": "ins", "id": id, "parent": parent])
            }
            element(10, parent: 1, style: "position:relative;width:400px;height:80px;overflow:hidden")
            element(11, parent: 10, style: "position:absolute;left:25%;top:8px;width:300px;height:32px;display:flex;transform:translateX(-10px)")
            for id in 12...14 {
                element(id, parent: 11, style: "width:100px;height:32px;flex-shrink:0;background:linear-gradient(90deg,#163c42,#263858);color:white")
                dom.apply(["op": "text", "id": id + 20, "data": "Track \(id)"])
                dom.apply(["op": "ins", "id": id + 20, "parent": id])
            }
            let host = CALayer()
            host.bounds = rect
            host.isGeometryFlipped = true
            let compositor = WidgetCompositor(host: host)
            compositor.scriptedCandidates = [11]
            let animator = WidgetAnimator()
            let initialRasters = WidgetCompositor.totalPromotions
            var document: LHDocument?
            for (index, width) in widths.enumerated() {
                dom.apply(["op": "style", "id": 10, "name": "width", "value": "\(width)px"])
                document = page(dom.serialize(theme: "light"), previous: document)
                let current = try XCTUnwrap(document)
                let state = "step=\(index) width=\(width) scale=\(scale)"
                compositor.update(document: current, animator: animator, dom: dom, declarations: [:], scale: scale, space: space)
                XCTAssertEqual(compositor.ids, promoted[index] ? [11] : [], state)
                if promoted[index] {
                    XCTAssertTrue(compositor.applyScripted([11], dom: dom, document: current), state)
                }
                XCTAssertEqual(WidgetCompositor.totalPromotions, initialRasters + rasterCounts[index], state)
                let reference = page(dom.serialize(theme: "light"))
                let full = try XCTUnwrap(reference.copyImage(maxWidth: rect.width, rect: rect, scale: scale))
                let exclusions = Set(compositor.ids.map { String($0) })
                let base = try XCTUnwrap(current.copyImage(maxWidth: rect.width, rect: rect, scale: scale, excluding: exclusions, attribute: WidgetDOM.idAttribute))
                func pixels(_ image: CGImage, overlay: Bool) throws -> Data {
                    let width = Int(rect.width * scale), height = Int(rect.height * scale)
                    let context = try XCTUnwrap(CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width * 4, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
                    context.scaleBy(x: scale, y: scale)
                    context.draw(image, in: rect)
                    if overlay {
                        context.translateBy(x: 0, y: rect.height)
                        context.scaleBy(x: 1, y: -1)
                        host.render(in: context)
                    }
                    return Data(bytes: try XCTUnwrap(context.data), count: width * height * 4)
                }
                let expected = try pixels(full, overlay: false), actual = try pixels(base, overlay: true)
                XCTAssertEqual(zip(expected, actual).filter { abs(Int($0) - Int($1)) > 1 }.count, 0, state)
            }
        }
    }

    func testRetainedLayersRefreshPaintOrderAfterZIndexAndDOMChanges() throws {
        let dom = WidgetDOM()
        dom.apply(["op": "el", "id": 1, "tag": "body", "adopt": "body", "attrs": [["style", "margin:0;background:white;width:500px;height:100px"]]])
        for (id, color) in [(11, "#f00"), (21, "#00f")] {
            dom.apply(["op": "el", "id": id, "tag": "div", "attrs": [["style", "position:absolute;left:40px;top:20px;width:120px;height:48px;background:\(color);z-index:0;transform:translateX(0px)"]]])
            dom.apply(["op": "ins", "id": id, "parent": 1])
        }
        let rect = CGRect(x: 0, y: 0, width: 500, height: 100)
        let host = CALayer()
        host.bounds = rect
        host.isGeometryFlipped = true
        let compositor = WidgetCompositor(host: host)
        compositor.scriptedCandidates = [11, 21]
        let animator = WidgetAnimator()
        let space = CGColorSpace(name: CGColorSpace.sRGB)!
        let initialRasters = WidgetCompositor.totalPromotions
        var document: LHDocument?
        for step in 0...4 {
            if step == 1 || step == 2 {
                dom.apply(["op": "style", "id": 11, "name": "z-index", "value": step == 1 ? "2" : "0"])
            } else if step == 3 {
                dom.apply(["op": "ins", "id": 11, "parent": 1])
            } else if step == 4 {
                dom.apply(["op": "ins", "id": 11, "parent": 1, "before": 21])
            }
            document = page(dom.serialize(theme: "light"), previous: document)
            let current = try XCTUnwrap(document)
            compositor.update(document: current, animator: animator, dom: dom, declarations: [:], scale: 2, space: space)
            XCTAssertEqual(compositor.ids, [11, 21], "step=\(step)")
            XCTAssertTrue(compositor.applyScripted([11, 21], dom: dom, document: current), "step=\(step)")
            XCTAssertEqual(WidgetCompositor.totalPromotions, initialRasters + 2, "step=\(step)")
            let reference = page(dom.serialize(theme: "light"))
            let full = try XCTUnwrap(reference.copyImage(maxWidth: rect.width, rect: rect, scale: 2))
            let base = try XCTUnwrap(current.copyImage(maxWidth: rect.width, rect: rect, scale: 2, excluding: ["11", "21"], attribute: WidgetDOM.idAttribute))
            func pixels(_ image: CGImage, overlay: Bool) throws -> Data {
                let context = try XCTUnwrap(CGContext(data: nil, width: 1000, height: 200, bitsPerComponent: 8, bytesPerRow: 4000, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
                context.scaleBy(x: 2, y: 2)
                context.draw(image, in: rect)
                if overlay {
                    context.translateBy(x: 0, y: rect.height)
                    context.scaleBy(x: 1, y: -1)
                    host.render(in: context)
                }
                return Data(bytes: try XCTUnwrap(context.data), count: 800000)
            }
            let expected = try pixels(full, overlay: false), actual = try pixels(base, overlay: true)
            XCTAssertEqual(expected, actual, "step=\(step)")
        }
    }

    func testCompositorReusesAndRepositionsUnchangedLocalPaint() throws {
        let dom = WidgetDOM()
        dom.apply(["op": "el", "id": 1, "tag": "body", "adopt": "body", "attrs": [["style", "margin:0;background:white;width:500px;height:100px;font:12px Menlo"]]])
        dom.apply(["op": "el", "id": 2, "tag": "style", "attrs": []])
        dom.apply(["op": "ins", "id": 2, "parent": 1])
        dom.apply(["op": "text", "id": 3, "data": ".flag.on + .track > div{color:blue!important}"])
        dom.apply(["op": "ins", "id": 3, "parent": 2])
        func element(_ id: Int, parent: Int, style: String) {
            dom.apply(["op": "el", "id": id, "tag": "div", "attrs": [["style", style]]])
            dom.apply(["op": "ins", "id": id, "parent": parent])
        }
        element(10, parent: 1, style: "position:relative;width:400px;height:80px;overflow:hidden")
        element(9, parent: 10, style: "position:absolute;width:1px;height:1px;top:70px")
        dom.apply(["op": "attr", "id": 9, "name": "class", "value": "flag"])
        element(11, parent: 10, style: "position:absolute;left:25%;top:8px;width:300px;height:32px;display:flex;transform:translateX(-10px)")
        dom.apply(["op": "attr", "id": 11, "name": "class", "value": "track"])
        for id in 12...14 {
            element(id, parent: 11, style: "width:100px;height:32px;flex-shrink:0;background:linear-gradient(90deg,#163c42,#263858);color:white")
            dom.apply(["op": "text", "id": id + 20, "data": "Track \(id)"])
            dom.apply(["op": "ins", "id": id + 20, "parent": id])
        }
        let host = CALayer()
        host.bounds = CGRect(x: 0, y: 0, width: 500, height: 100)
        host.isGeometryFlipped = true
        let compositor = WidgetCompositor(host: host)
        compositor.scriptedCandidates = [11]
        let animator = WidgetAnimator()
        let space = CGColorSpace(name: CGColorSpace.sRGB)!
        var document: LHDocument?
        var initialRasters = 0
        var positions = Set<CGFloat>()
        for (index, width) in [400, 360, 420, 400, 400, 400].enumerated() {
            dom.apply(["op": "style", "id": 10, "name": "width", "value": "\(width)px"])
            if index >= 4 {
                dom.apply(["op": "attr", "id": 9, "name": "class", "value": index == 4 ? "flag on" : "flag"])
            }
            document = page(dom.serialize(theme: "light"), previous: document)
            let current = try XCTUnwrap(document)
            compositor.update(document: current, animator: animator, dom: dom, declarations: [:], scale: 2, space: space)
            XCTAssertEqual(compositor.ids, [11])
            XCTAssertTrue(compositor.applyScripted([11], dom: dom, document: current))
            if index == 0 {
                initialRasters = WidgetCompositor.totalPromotions
            } else if index < 4 {
                XCTAssertEqual(WidgetCompositor.totalPromotions, initialRasters)
            } else {
                XCTAssertEqual(WidgetCompositor.totalPromotions, initialRasters + index - 3)
            }
            let layer = try XCTUnwrap(host.sublayers?.first)
            positions.insert(layer.position.x)
            let rect = CGRect(x: 0, y: 0, width: 500, height: 100)
            let reference = page(dom.serialize(theme: "light"))
            let full = try XCTUnwrap(reference.copyImage(maxWidth: 500, rect: rect, scale: 2))
            let base = try XCTUnwrap(reference.copyImage(maxWidth: 500, rect: rect, scale: 2, excluding: ["11"], attribute: WidgetDOM.idAttribute))
            func pixels(_ image: CGImage, overlay: Bool) -> Data {
                let context = CGContext(data: nil, width: 1000, height: 200, bitsPerComponent: 8, bytesPerRow: 4000, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
                context.scaleBy(x: 2, y: 2)
                context.draw(image, in: rect)
                if overlay {
                    context.translateBy(x: 0, y: 100)
                    context.scaleBy(x: 1, y: -1)
                    host.render(in: context)
                }
                return Data(bytes: context.data!, count: 800000)
            }
            let expected = pixels(full, overlay: false), actual = pixels(base, overlay: true)
            XCTAssertEqual(zip(expected, actual).filter { abs(Int($0) - Int($1)) > 1 }.count, 0, "width=\(width)")
        }
        XCTAssertGreaterThan(positions.count, 1)
    }
}

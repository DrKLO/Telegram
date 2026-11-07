import XCTest
import AppKit
import Darwin
import LiteHTML
@testable import TwidgetRenderer

final class ScrollViewportRasterTests: XCTestCase {
    private func drain() {
        for _ in 0..<4 {
            WidgetScrollLayer.queue.sync {}
            RunLoop.main.run(until: Date().addingTimeInterval(0.002))
        }
    }

    private func pixels(_ layer: CALayer, scale: CGFloat) throws -> Data {
        let width = Int(ceil(660 * scale)), height = Int(ceil(180 * scale))
        let context = try XCTUnwrap(CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
                                            space: CGColorSpace(name: CGColorSpace.sRGB)!,
                                            bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue))
        context.translateBy(x: 0, y: CGFloat(height))
        context.scaleBy(x: scale, y: -scale)
        let root = CALayer()
        root.bounds = CGRect(x: 0, y: 0, width: 660, height: 180)
        root.isGeometryFlipped = true
        root.addSublayer(layer)
        root.render(in: context)
        return Data(bytes: try XCTUnwrap(context.data), count: context.bytesPerRow * height)
    }

    func testClippedRasterMatchesFullTilesAcrossResizingAndFractionalPhases() throws {
        for scale: CGFloat in [1, 1.5, 2] {
            let full = WidgetScrollLayer(id: 3, geometry: WidgetScrollGeometry(viewport: .zero, content: .zero, radii: []))
            let clipped = WidgetScrollLayer(id: 3, geometry: full.geometry)
            clipped.clipsRasterToViewport = true
            for (step, width) in [100.25, 120.5, 127.5, 129.25, 200, 513.5, 120.25].enumerated() {
                let html = """
                <style>body{margin:0;font:13px -apple-system}#clip{margin:10.25px;width:\(width)px;height:96.25px;overflow:hidden;border-radius:11px}
                .track{position:relative;width:1025px;height:513px;background:linear-gradient(110deg,#f008,#08f6);transform:translate(\(-12.375 * Double(step))px,-0.25px)}
                p{margin:0;padding:10px;color:#124;text-shadow:1px 2px 3px #0007;background:#fc08}.overlay{position:absolute;top:30px;left:100px;width:160px;height:90px;background:#16d9;border-radius:13px;transform:rotate(7deg);box-shadow:0 0 14px #2309}</style>
                <div id=clip data-key=3><div class=track>\((0..<16).map { "<p>State \(step) row \($0) · 界 · text clipping</p>" }.joined())<div class=overlay></div></div>
                """
                let document = LHDocument(html: html, css: nil)
                _ = document.layout(maxWidth: 660, preferredWidth: 660)
                let info = try XCTUnwrap(document.scrollers(byAttribute: "data-key")["3"])
                let geometry = WidgetScrollGeometry(viewport: try XCTUnwrap((info["viewport"] as? NSValue)?.rectValue), content: try XCTUnwrap((info["content"] as? NSValue)?.sizeValue), radii: try XCTUnwrap(info["radii"] as? [NSNumber]).map { CGFloat($0.doubleValue) })
                for layer in [full, clipped] {
                    layer.apply(geometry)
                    layer.update(signature: step + 1)
                    layer.ensureTiles(document: document, scale: scale, attribute: "data-key")
                }
                drain()
                let expected = try pixels(full.clip, scale: scale)
                let actual = try pixels(clipped.clip, scale: scale)
                let differences = zip(actual, expected).enumerated().compactMap { index, pair in pair.0 == pair.1 ? nil : (index, abs(Int(pair.0) - Int(pair.1))) }
                let changedPixels = Set(differences.map { $0.0 / 4 * 4 })
                let height = Int(ceil(180 * scale)), stride = expected.count / height
                let imageWidth = Int(ceil(660 * scale))
                let flatDifferences = changedPixels.filter { index in
                    let y = index / stride, x = index % stride / 4
                    for ny in max(0, y - 1)...min(height - 1, y + 1) {
                        for nx in max(0, x - 1)...min(imageWidth - 1, x + 1) {
                            if (0..<4).contains(where: { expected[ny * stride + nx * 4 + $0] != expected[index + $0] }) { return false }
                        }
                    }
                    return true
                }
                if !differences.isEmpty, let directory = ProcessInfo.processInfo.environment["TW_SCROLL_VIEWPORT_REPORT"] {
                    try FileManager.default.createDirectory(atPath: directory, withIntermediateDirectories: true)
                    try actual.write(to: URL(fileURLWithPath: directory + "/\(scale)-\(width)-actual.bgra"))
                    try expected.write(to: URL(fileURLWithPath: directory + "/\(scale)-\(width)-expected.bgra"))
                }
                let worst = differences.map { $0.1 }.max() ?? 0
                let visiblePixels = geometry.viewport.width * geometry.viewport.height * scale * scale
                XCTAssertEqual(flatDifferences.count, 0, "scale=\(scale) width=\(width)")
                XCTAssertLessThanOrEqual(worst, 8, "scale=\(scale) width=\(width)")
                XCTAssertLessThanOrEqual(CGFloat(changedPixels.count), visiblePixels * 0.02, "scale=\(scale) width=\(width)")
                print("VIEWPORT_PIXELS scale=\(scale) width=\(width) changed=\(changedPixels.count) flat=\(flatDifferences.count) worst=\(worst)")
                XCTAssertLessThan(clipped.content.sublayers!.reduce(0) { $0 + $1.frame.width * $1.frame.height }, full.content.sublayers!.reduce(0) { $0 + $1.frame.width * $1.frame.height })
            }
        }
    }

    func testFirstScriptedScrollRestoresFullRasterCoverageWithoutLayout() throws {
        let dom = WidgetDOM()
        for op: [String: Any] in [
            ["op": "el", "id": 1, "tag": "body", "adopt": "body", "attrs": [["style", "margin:0"]]],
            ["op": "el", "id": 3, "tag": "div", "attrs": [["style", "width:120px;height:100px;overflow:hidden"]]],
            ["op": "ins", "id": 3, "parent": 1],
            ["op": "el", "id": 4, "tag": "div", "attrs": [["style", "width:1025px;height:513px;background:linear-gradient(90deg,red,blue)"]]],
            ["op": "ins", "id": 4, "parent": 3],
        ] { dom.apply(op) }
        let document = LHDocument(html: dom.serialize(theme: "light"), css: nil)
        _ = document.layout(maxWidth: 660, preferredWidth: 660)
        let host = CALayer()
        let scrolling = WidgetScrolling()
        scrolling.clipsUnscrolledRasters = true
        scrolling.attach(host)
        _ = scrolling.update(document: document, dom: dom, animated: [], promoted: [], styleKey: 0, scale: 2)
        let layer = try XCTUnwrap(scrolling.layers[3])
        XCTAssertTrue(layer.clipsRasterToViewport)
        XCTAssertEqual(layer.content.sublayers?.first?.frame.size, CGSize(width: 192, height: 128))
        XCTAssertTrue(scrolling.setOffset(3, CGPoint(x: 520.25, y: 260.5), document: document))
        XCTAssertFalse(layer.clipsRasterToViewport)
        layer.ensureTiles(document: document, scale: 2, attribute: WidgetDOM.idAttribute)
        drain()
        let reference = WidgetScrollLayer(id: 3, geometry: layer.geometry)
        reference.update(signature: 1)
        reference.show(scrolling.offset(3))
        reference.ensureTiles(document: document, scale: 2, attribute: WidgetDOM.idAttribute)
        drain()
        XCTAssertEqual(try pixels(layer.clip, scale: 2), try pixels(reference.clip, scale: 2))
        _ = scrolling.update(document: document, dom: dom, animated: [], promoted: [], styleKey: 0, scale: 2)
        XCTAssertFalse(layer.clipsRasterToViewport)
    }

    func testWheelScrollersKeepTheirPrefetchedTiles() throws {
        let dom = WidgetDOM()
        for op: [String: Any] in [
            ["op": "el", "id": 1, "tag": "body", "adopt": "body", "attrs": [["style", "margin:0"]]],
            ["op": "el", "id": 3, "tag": "div", "attrs": [["style", "width:120px;height:100px;overflow:auto"]]],
            ["op": "ins", "id": 3, "parent": 1],
            ["op": "el", "id": 4, "tag": "div", "attrs": [["style", "width:1025px;height:513px;background:red"]]],
            ["op": "ins", "id": 4, "parent": 3],
        ] { dom.apply(op) }
        let document = LHDocument(html: dom.serialize(theme: "light"), css: nil)
        _ = document.layout(maxWidth: 660, preferredWidth: 660)
        let host = CALayer()
        let scrolling = WidgetScrolling()
        scrolling.clipsUnscrolledRasters = true
        scrolling.attach(host)
        _ = scrolling.update(document: document, dom: dom, animated: [], promoted: [], styleKey: 0, scale: 2)
        let layer = try XCTUnwrap(scrolling.layers[3])
        XCTAssertFalse(layer.clipsRasterToViewport)
        XCTAssertEqual(layer.content.sublayers?.first?.frame.size, CGSize(width: 512, height: 256))
        drain()
    }

    func testSmallResizesReuseRasterCapacityWithoutSynchronousRefills() throws {
        let document = LHDocument(html: "<body style='margin:0'><div id=3 style='width:127.5px;height:127.5px;overflow:hidden'><div style='width:1025px;height:513px;background:red'></div></div></body>", css: nil)
        _ = document.layout(maxWidth: 660, preferredWidth: 660)
        let layer = WidgetScrollLayer(id: 3, geometry: WidgetScrollGeometry(viewport: CGRect(x: 0, y: 0, width: 127.5, height: 127.5), content: CGSize(width: 1025, height: 513), radii: []))
        layer.clipsRasterToViewport = true
        layer.update(signature: 1)
        layer.ensureTiles(document: document, scale: 2, attribute: "id")
        let tile = try XCTUnwrap(layer.content.sublayers?.first)
        let frame = tile.frame
        let rasterCount = layer.rasterCount
        let gate = DispatchSemaphore(value: 0)
        WidgetScrollLayer.queue.async { gate.wait() }
        defer { gate.signal(); drain() }
        for size: CGFloat in [129.25, 127.5, 160, 128.25, 100.25] {
            var geometry = layer.geometry
            geometry.viewport.size = CGSize(width: size, height: size)
            layer.apply(geometry)
            layer.ensureTiles(document: document, scale: 2, attribute: "id")
            XCTAssertTrue(layer.content.sublayers?.first === tile)
            XCTAssertEqual(tile.frame, frame)
            XCTAssertEqual(layer.rasterCount, rasterCount)
        }
    }

    func testDenseSubtreesKeepFullRasterCoverageAsContentChanges() throws {
        let dom = WidgetDOM()
        for op: [String: Any] in [
            ["op": "el", "id": 1, "tag": "body", "adopt": "body", "attrs": [["style", "margin:0"]]],
            ["op": "el", "id": 3, "tag": "div", "attrs": [["style", "width:120px;height:100px;overflow:hidden"]]],
            ["op": "ins", "id": 3, "parent": 1],
            ["op": "el", "id": 4, "tag": "div", "attrs": [["style", "width:1025px;height:513px;background:red"]]],
            ["op": "ins", "id": 4, "parent": 3],
        ] { dom.apply(op) }
        let host = CALayer()
        let scrolling = WidgetScrolling()
        scrolling.clipsUnscrolledRasters = true
        scrolling.attach(host)
        for dense in [false, true, false] {
            for id in 10..<90 {
                if dense {
                    dom.apply(["op": "el", "id": id, "tag": "span"])
                    dom.apply(["op": "ins", "id": id, "parent": 4])
                } else if dom.node(id) != nil {
                    dom.apply(["op": "rm", "id": id])
                }
            }
            let document = LHDocument(html: dom.serialize(theme: "light"), css: nil)
            _ = document.layout(maxWidth: 660, preferredWidth: 660)
            _ = scrolling.update(document: document, dom: dom, animated: [], promoted: [], styleKey: 0, scale: 2)
            let layer = try XCTUnwrap(scrolling.layers[3])
            XCTAssertEqual(layer.clipsRasterToViewport, !dense)
            let expected = dense ? CGSize(width: 512, height: 256) : CGSize(width: 192, height: 128)
            XCTAssertEqual(layer.content.sublayers?.first?.frame.size, expected)
            drain()
        }
    }

    func testExpandingRasterCoverageFillsVisibleTilesWhileWorkerIsBusy() throws {
        let document = LHDocument(html: "<body style='margin:0'><div id=3 style='width:600px;height:300px;overflow:hidden'><div style='width:1537px;height:769px;background:linear-gradient(90deg,red,blue)'></div></div></body>", css: nil)
        _ = document.layout(maxWidth: 660, preferredWidth: 660)
        let layer = WidgetScrollLayer(id: 3, geometry: WidgetScrollGeometry(viewport: CGRect(x: 0, y: 0, width: 600, height: 300), content: CGSize(width: 1537, height: 769), radii: []))
        layer.clipsRasterToViewport = true
        layer.update(signature: 1)
        layer.ensureTiles(document: document, scale: 1.5, attribute: "id")
        let retained = try XCTUnwrap(layer.content.sublayers?.first { $0.frame == CGRect(x: 0, y: 0, width: 512, height: 256) })
        let gate = DispatchSemaphore(value: 0)
        WidgetScrollLayer.queue.async { gate.wait() }
        defer { gate.signal(); drain() }
        layer.clipsRasterToViewport = false
        layer.show(CGPoint(x: 500.25, y: 0))
        layer.ensureTiles(document: document, scale: 1.5, attribute: "id")
        XCTAssertTrue(layer.content.sublayers?.contains { $0 === retained } == true)
        let visible = CGRect(origin: layer.offset, size: layer.geometry.viewport.size)
        var area: CGFloat = 0
        for tile in layer.content.sublayers ?? [] where tile.frame.intersects(visible) {
            let covered = tile.frame.intersection(visible)
            area += covered.width * covered.height
            let image = try XCTUnwrap(WidgetSurfaceImage.image(of: tile)) as! CGImage
            let expected = try XCTUnwrap(document.copyScrollContent(attribute: "id", value: "3", rect: tile.frame.insetBy(dx: -1 / 1.5, dy: -1 / 1.5), scale: 1.5))
            XCTAssertEqual(image.dataProvider?.data as Data?, expected.dataProvider?.data as Data?)
        }
        XCTAssertEqual(area, visible.width * visible.height, accuracy: 0.0001)
    }

    func testViewportRasterBenchmark() throws {
        guard ProcessInfo.processInfo.environment["TW_SCROLL_VIEWPORT_BENCH"] == "1" else {
            throw XCTSkip("Set TW_SCROLL_VIEWPORT_BENCH=1 to run the fixed-state raster comparison")
        }
        func cpu() -> Double {
            var usage = rusage()
            getrusage(RUSAGE_SELF, &usage)
            return Double(usage.ru_utime.tv_sec + usage.ru_stime.tv_sec) + Double(usage.ru_utime.tv_usec + usage.ru_stime.tv_usec) / 1_000_000
        }
        var references: [Int: Data] = [:]
        let modes = ProcessInfo.processInfo.environment["TW_SCROLL_VIEWPORT_REVERSE"] == "1" ? [true, false, false, true] : [false, true, true, false]
        for clipped in modes {
            let layer = WidgetScrollLayer(id: 3, geometry: WidgetScrollGeometry(viewport: .zero, content: .zero, radii: []))
            layer.clipsRasterToViewport = clipped
            var seconds: Double = 0, rasters = 0, area: CGFloat = 0
            for step in -12..<60 {
                let width = 128 + 8 * sin(Double(step) * 0.3)
                let html = """
                <style>body{margin:0;font:12px Menlo}#clip{width:\(width)px;height:112px;overflow:hidden;border-radius:9px}
                .track{position:relative;width:1537px;height:769px;background:linear-gradient(110deg,#163c42b0,#26385890);transform:translate(\(-40 - 30 * sin(Double(step)))px,-20px)}
                p{margin:0;padding:8px;height:30px;color:#92e5c4;background:linear-gradient(90deg,#163c42a0,#263858c0);border-bottom:1px solid #ffffff24}</style>
                <div id=clip data-key=3><div class=track>\((0..<16).map { row in "<p>" + (0..<8).map { "State \(step) R\(row) C\($0) · ready" }.joined(separator: " · ") + "</p>" }.joined())</div></div>
                """
                let document = LHDocument(html: html, css: nil)
                _ = document.layout(maxWidth: 660, preferredWidth: 660)
                let info = try XCTUnwrap(document.scrollers(byAttribute: "data-key")["3"])
                let geometry = WidgetScrollGeometry(viewport: try XCTUnwrap((info["viewport"] as? NSValue)?.rectValue), content: try XCTUnwrap((info["content"] as? NSValue)?.sizeValue), radii: try XCTUnwrap(info["radii"] as? [NSNumber]).map { CGFloat($0.doubleValue) })
                let beforeRasters = layer.rasterCount
                let started = cpu()
                layer.apply(geometry)
                layer.update(signature: step + 13)
                layer.ensureTiles(document: document, scale: 2, attribute: "data-key")
                drain()
                let elapsed = cpu() - started
                if step >= 0 {
                    seconds += elapsed
                    rasters += layer.rasterCount - beforeRasters
                    area += (layer.content.sublayers ?? []).reduce(0) { $0 + $1.frame.width * $1.frame.height }
                    let actual = try pixels(layer.clip, scale: 2)
                    if let expected = references[step] {
                        XCTAssertEqual(actual.count, expected.count)
                        let worst = zip(actual, expected).reduce(0) { max($0, abs(Int($1.0) - Int($1.1))) }
                        XCTAssertLessThanOrEqual(worst, 8, "clipped=\(clipped) step=\(step)")
                    } else {
                        references[step] = actual
                    }
                }
            }
            print("VIEWPORT_BENCH clipped=\(clipped) states=60 cpu_ms=\(seconds * 1000) rasters=\(rasters) raster_area=\(area)")
        }
    }
}

import XCTest
import AppKit
import Darwin
import LiteHTML
import WidgetSandboxProtocol
@testable import TwidgetRenderer

final class LocalPaintWorkloadTests: XCTestCase {
    private final class ScheduledWork {
        private let lock = NSLock()
        private var blocks: [() -> Void] = []
        private var delayed: [(CFTimeInterval, () -> Void)] = []
        private(set) var executed = 0

        func add(_ block: @escaping () -> Void) {
            lock.lock()
            blocks.append(block)
            lock.unlock()
        }

        func after(_ delay: TimeInterval, _ block: @escaping () -> Void) {
            lock.lock()
            delayed.append((CACurrentMediaTime() + delay, block))
            lock.unlock()
        }

        var count: Int {
            lock.lock()
            defer { lock.unlock() }
            return blocks.count + delayed.count
        }

        func drain(until deadline: CFTimeInterval) {
            while CACurrentMediaTime() < deadline {
                lock.lock()
                let now = CACurrentMediaTime()
                blocks.append(contentsOf: delayed.filter { $0.0 <= now }.map { $0.1 })
                delayed.removeAll { $0.0 <= now }
                let block = blocks.isEmpty ? nil : blocks.removeFirst()
                lock.unlock()
                guard let block = block else { return }
                executed += 1
                block()
            }
        }
    }

    private struct Completion: Equatable {
        let scheduled: Int
        let layouts: Int
        let tileJobs: Int
        let tileInstalls: Int
        let scrollRasters: Int
    }

    private final class Harness {
        let size = CGSize(width: 480, height: 1840)
        let window: NSWindow
        let renderer: LiteHTMLWidgetRenderer
        let work = ScheduledWork()

        init() {
            _ = NSApplication.shared
            window = NSWindow(contentRect: CGRect(x: 0, y: 0, width: 480, height: 1840), styleMask: [.borderless], backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            window.colorSpace = .sRGB
            renderer = LiteHTMLWidgetRenderer(theme: "light", width: 480)
            renderer.keepsTilesWhileHidden = true
            renderer.view.frame = CGRect(origin: .zero, size: size)
            window.contentView?.addSubview(renderer.view)
            renderer.documentView.captureWindowState()
            let work = self.work
            renderer.schedule = { [work] in work.add($0) }
            renderer.scheduleAfter = { [work] in work.after($0, $1) }
            renderer.load()
        }

        var completion: Completion {
            let tiles = renderer.asyncTileReport
            return Completion(scheduled: work.executed, layouts: renderer.layoutCount, tileJobs: tiles.jobs, tileInstalls: tiles.installed, scrollRasters: renderer.scrolling.layers.values.reduce(0) { $0 + $1.rasterCount })
        }

        private func display(_ layer: CALayer) {
            layer.displayIfNeeded()
            for child in layer.sublayers ?? [] { display(child) }
        }

        private func needsDisplay(_ layer: CALayer) -> Bool {
            return layer.needsDisplay() || (layer.sublayers ?? []).contains { needsDisplay($0) }
        }

        func send(_ ops: [[String: Any]], minimumControlRasters: Int, file: StaticString = #filePath, line: UInt = #line) throws {
            let deadline = CACurrentMediaTime() + 10
            let scrollsBefore = renderer.scrolling.layers.mapValues { ($0, $0.rasterCount) }
            renderer.send(ops: ops.map(WidgetOp.init))
            var passedFences = 0
            var fenceReturned = true
            var fencedState: Completion?
            while CACurrentMediaTime() < deadline {
                work.drain(until: deadline)
                renderer.view.displayIfNeeded()
                if let root = renderer.documentView.contentLayer { display(root) }
                CATransaction.flush()
                work.drain(until: deadline)
                let scrollsReady = renderer.scrolling.layers.allSatisfy { id, layer in
                    let previous = scrollsBefore[id]
                    let minimum = previous?.0 === layer ? (previous?.1 ?? 0) + layer.tileCount : layer.tileCount
                    return layer.tileCount > 0 && layer.rasterCount >= minimum && (layer.content.sublayers ?? []).allSatisfy { WidgetSurfaceImage.image(of: $0) != nil }
                }
                let ready = work.count == 0 && renderer.heldOpBatches == 0 && !renderer.isPreparingTiles && !renderer.documentView.hasUnpreparedTiles && scrollsReady && (renderer.scrolling.layers[5]?.rasterCount ?? 0) >= minimumControlRasters && renderer.documentView.contentLayer.map { !needsDisplay($0) } == true
                if ready && fenceReturned {
                    let state = completion
                    passedFences = fencedState == state ? passedFences + 1 : 0
                    if passedFences >= 2 { return }
                    fencedState = state
                    fenceReturned = false
                    WidgetScrollLayer.queue.async {
                        DispatchQueue.main.async { fenceReturned = true }
                    }
                } else if !ready {
                    passedFences = 0
                }
                RunLoop.main.run(until: Date().addingTimeInterval(0.001))
            }
            XCTFail("component state did not finish: queued=\(work.count) held=\(renderer.heldOpBatches) preparing=\(renderer.isPreparingTiles) control=\(renderer.scrolling.layers[5]?.rasterCount ?? -1)/\(minimumControlRasters) \(renderer.tileDebug)", file: file, line: line)
            throw NSError(domain: "LocalPaintWorkloadTests", code: 1)
        }

        func assertApplied(_ ops: [[String: Any]], file: StaticString = #filePath, line: UInt = #line) throws {
            for op in ops {
                let id = try XCTUnwrap(op["id"] as? Int, file: file, line: line)
                let name = try XCTUnwrap(op["name"] as? String, file: file, line: line)
                if op["op"] as? String == "style" {
                    let node = try XCTUnwrap(renderer.dom.nodes[id], file: file, line: line)
                    XCTAssertEqual(node.declarations.first { $0.name == name }?.value, op["value"] as? String, "node=\(id) property=\(name)", file: file, line: line)
                } else if name == "scrollLeft" {
                    let value = try XCTUnwrap(op["value"] as? Int, file: file, line: line)
                    XCTAssertEqual(renderer.scrolling.offset(id).x, CGFloat(value), file: file, line: line)
                }
            }
        }

        func assertControlPixels(file: StaticString = #filePath, line: UInt = #line) throws {
            let layer = try XCTUnwrap(renderer.scrolling.layers[5], file: file, line: line)
            let document = try XCTUnwrap(renderer.document, file: file, line: line)
            XCTAssertEqual(layer.tileCount, 2, file: file, line: line)
            for tile in layer.content.sublayers ?? [] {
                let scale = tile.contentsScale
                let contents = try XCTUnwrap(WidgetSurfaceImage.image(of: tile), file: file, line: line)
                guard CFGetTypeID(contents as CFTypeRef) == CGImage.typeID else {
                    XCTFail("scroll tile has no CGImage", file: file, line: line)
                    throw NSError(domain: "LocalPaintWorkloadTests", code: 2)
                }
                let actual = contents as! CGImage
                let expected = try XCTUnwrap(document.copyScrollContent(attribute: WidgetDOM.idAttribute, value: "5", rect: tile.frame.insetBy(dx: -1 / scale, dy: -1 / scale), scale: scale), file: file, line: line)
                let actualBytes = try XCTUnwrap(actual.dataProvider?.data, file: file, line: line) as Data
                let expectedBytes = try XCTUnwrap(expected.dataProvider?.data, file: file, line: line) as Data
                XCTAssertEqual(actual.width, expected.width, file: file, line: line)
                XCTAssertEqual(actual.height, expected.height, file: file, line: line)
                XCTAssertEqual(actualBytes, expectedBytes, "scroll tile \(tile.frame)", file: file, line: line)
            }
        }

        func assertPixels(state: String, file: StaticString = #filePath, line: UInt = #line) throws {
            let root = try XCTUnwrap(renderer.documentView.contentLayer, file: file, line: line)
            let reference = LHDocument(html: renderer.dom.serialize(theme: "light"), css: nil, previous: nil, viewportSize: renderer.presentedDocument?.viewportSize ?? size)
            _ = reference.layout(maxWidth: size.width, preferredWidth: size.width)
            for (id, offset) in renderer.scrolling.offsets {
                _ = reference.setScrollOffset(offset, attribute: WidgetDOM.idAttribute, value: String(id))
            }
            let scale = renderer.documentView.scale
            let scrollRoot = CALayer()
            scrollRoot.bounds = root.bounds
            scrollRoot.isGeometryFlipped = root.isGeometryFlipped
            let scrollers = reference.scrollers(byAttribute: WidgetDOM.idAttribute)
            for (id, installed) in renderer.scrolling.layers {
                let info = try XCTUnwrap(scrollers[String(id)], file: file, line: line)
                let viewport = try XCTUnwrap((info["viewport"] as? NSValue)?.rectValue, file: file, line: line)
                let content = try XCTUnwrap((info["content"] as? NSValue)?.sizeValue, file: file, line: line)
                let radii = ((info["radii"] as? [NSNumber]) ?? []).map { CGFloat($0.doubleValue) }
                XCTAssertEqual(installed.geometry.viewport, viewport, file: file, line: line)
                XCTAssertEqual(installed.geometry.content, content, file: file, line: line)
                let fresh = WidgetScrollLayer(id: id, geometry: WidgetScrollGeometry(viewport: viewport, content: content, radii: radii))
                fresh.show(renderer.scrolling.offset(id))
                let width = max(viewport.width, content.width), height = max(viewport.height, content.height)
                let tileSize = WidgetScrollLayer.tileSize
                let columns = Int(ceil(width / tileSize.width)), rows = Int(ceil(height / tileSize.height))
                XCTAssertEqual(installed.tileCount, columns * rows, file: file, line: line)
                for row in 0..<rows {
                    for column in 0..<columns {
                        let x = CGFloat(column) * tileSize.width, y = CGFloat(row) * tileSize.height
                        let rect = CGRect(x: x, y: y, width: min(tileSize.width, width - x), height: min(tileSize.height, height - y))
                        let image = try XCTUnwrap(reference.copyScrollContent(attribute: WidgetDOM.idAttribute, value: String(id), rect: rect.insetBy(dx: -1 / scale, dy: -1 / scale), scale: scale), file: file, line: line)
                        let tile = CALayer()
                        tile.anchorPoint = .zero
                        tile.frame = rect
                        tile.contentsScale = scale
                        tile.contentsGravity = .resize
                        tile.allowsEdgeAntialiasing = false
                        tile.edgeAntialiasingMask = []
                        tile.contentsRect = CGRect(x: 1 / CGFloat(image.width), y: 1 / CGFloat(image.height), width: rect.width * scale / CGFloat(image.width), height: rect.height * scale / CGFloat(image.height))
                        tile.contents = image
                        fresh.content.addSublayer(tile)
                    }
                }
                scrollRoot.addSublayer(fresh.clip)
            }
            reference.setScrollContentExclusions(Set(renderer.scrolling.promotedIDs.map { String($0) }), attribute: WidgetDOM.idAttribute)
            let width = Int(ceil(size.width * scale)), height = Int(ceil(size.height * scale))
            let space = try XCTUnwrap(CGColorSpace(name: CGColorSpace.sRGB), file: file, line: line)
            func pixels(_ draw: (CGContext) -> Void) throws -> [UInt8] {
                let context = try XCTUnwrap(CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width * 4, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue), file: file, line: line)
                context.translateBy(x: 0, y: CGFloat(height))
                context.scaleBy(x: scale, y: -scale)
                context.setAllowsFontSmoothing(false)
                draw(context)
                let bytes = try XCTUnwrap(context.data, file: file, line: line).assumingMemoryBound(to: UInt8.self)
                return Array(UnsafeBufferPointer(start: bytes, count: width * height * 4))
            }
            let expected = try pixels {
                reference.draw(in: $0, origin: .zero, clip: CGRect(origin: .zero, size: size))
                scrollRoot.render(in: $0)
            }
            let actual = try pixels { root.render(in: $0) }
            var interiorDifferences = 0, edgeDifferences = 0, worst = 0
            var examples: [String] = []
            for y in 0..<height {
                for x in 0..<width {
                    let index = (y * width + x) * 4
                    var delta = 0
                    for channel in 0..<4 { delta = max(delta, abs(Int(expected[index + channel]) - Int(actual[index + channel]))) }
                    guard delta > 0 else { continue }
                    worst = max(worst, delta)
                    var interior = true
                    for ny in max(0, y - 1)...min(height - 1, y + 1) {
                        for nx in max(0, x - 1)...min(width - 1, x + 1) {
                            let neighbor = (ny * width + nx) * 4
                            for channel in 0..<4 where expected[neighbor + channel] != expected[index + channel] { interior = false }
                        }
                    }
                    if interior { interiorDifferences += 1 }
                    if delta > 24 { edgeDifferences += 1 }
                    if examples.count < 5 { examples.append("\(x),\(height - 1 - y):\(delta)") }
                }
            }
            let diagnostic = "\(state) scale=\(scale) worst=\(worst) first=\(examples)"
            XCTAssertEqual(interiorDifferences, 0, "interior pixels differ: \(diagnostic)", file: file, line: line)
            XCTAssertEqual(edgeDifferences, 0, "edge differences exceed the handoff test antialiasing allowance: \(diagnostic)", file: file, line: line)
        }

        deinit {
            renderer.destroy()
        }
    }

    private let tracks = Set((0..<120).map { 1002 + $0 * 10 })
    private let colors = ["#26734d", "#9b5d26", "#285ba6", "#684492"]

    private func scene(contained: Bool) -> [[String: Any]] {
        var ops: [[String: Any]] = [["op": "el", "id": 1, "tag": "body", "adopt": "body", "attrs": [["style", "margin:0;width:480px;height:1840px;background:#fff"]]]]
        func element(_ id: Int, parent: Int, style: String) {
            ops.append(["op": "el", "id": id, "tag": "div", "attrs": [["style", style]]])
            ops.append(["op": "ins", "id": id, "parent": parent])
        }
        element(4, parent: 1, style: "display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:4px 6px;padding:4px 6px;box-sizing:border-box;width:480px")
        for index in 0..<120 {
            let id = 1000 + index * 10
            element(id, parent: 4, style: "position:relative;height:40px;min-width:0;background:#e2e5e8")
            element(id + 6, parent: id, style: "position:absolute;left:0;top:0;width:16px;height:6px;background:\(colors[index % colors.count])")
            element(id + 1, parent: id, style: "position:absolute;left:0;top:8px;width:100%;height:32px;overflow:hidden;background:#172235")
            let width = contained ? 64 : 300
            let translation = contained ? 20 : -40
            element(id + 2, parent: id + 1, style: "position:absolute;left:0;top:0;width:\(width)px;height:32px;display:flex;transform:translateX(\(translation)px)")
            for child in 0..<3 {
                let width = contained ? [21, 21, 22][child] : 100
                element(id + 3 + child, parent: id + 2, style: "width:\(width)px;height:32px;flex-shrink:0;background:\(colors[child])")
            }
        }
        element(5, parent: 1, style: "position:absolute;left:6px;top:1780px;width:468px;height:48px;overflow:auto;background:#111")
        element(6, parent: 5, style: "position:relative;width:900px;height:48px;background:\(colors[0])")
        element(7, parent: 6, style: "position:absolute;left:400px;top:0;width:100px;height:48px;background:#fff")
        return ops
    }

    private func state(_ step: Int, fractional: Bool, contained: Bool) -> [[String: Any]] {
        let widths = fractional ? ["480.25", "456.5", "432.75", "468"] : ["480", "456", "432", "468"]
        var ops: [[String: Any]] = [
            ["op": "style", "id": 4, "name": "width", "value": widths[step % widths.count] + "px"],
            ["op": "style", "id": 6, "name": "background-color", "value": colors[step % colors.count]],
            ["op": "prop", "id": 5, "name": "scrollLeft", "value": (step * 37) % 433],
        ]
        for index in 0..<120 {
            let id = 1000 + index * 10
            let translation = contained ? 20 + (step * 7 + index * 3) % 11 : -(40 + (step * 7 + index * 3) % 80)
            ops.append(["op": "style", "id": id + 2, "name": "transform", "value": "translateX(\(translation)px)"])
            if index % 4 == step % 4 {
                ops.append(["op": "style", "id": id + 6, "name": "background-color", "value": colors[(step / 4 + index / 4) % colors.count]])
            }
        }
        return ops
    }

    private func cpuSeconds() -> Double {
        var usage = rusage()
        getrusage(RUSAGE_SELF, &usage)
        return Double(usage.ru_utime.tv_sec + usage.ru_stime.tv_sec) + Double(usage.ru_utime.tv_usec + usage.ru_stime.tv_usec) / 1_000_000
    }

    private func run(fractional: Bool, contained: Bool = false) throws {
        let harness = Harness()
        let takeover = harness.renderer.scrolling.allowsCompositorTakeover
        let promotes = contained || takeover
        let mode = contained ? "contained-integer-width" : fractional ? "fractional-width" : "integer-width"
        try harness.send(scene(contained: contained), minimumControlRasters: 2)
        if contained { XCTAssertEqual(Set(harness.renderer.scrolling.layers.keys), [5]) }
        try harness.assertControlPixels()
        try harness.assertPixels(state: "initial")
        for step in 1...4 {
            let control = try XCTUnwrap(harness.renderer.scrolling.layers[5])
            try harness.send(state(step, fractional: false, contained: contained), minimumControlRasters: control.rasterCount + 2)
        }
        XCTAssertEqual(harness.renderer.compositedElementIDs, promotes ? tracks : [])
        if contained { XCTAssertEqual(Set(harness.renderer.scrolling.layers.keys), [5]) }
        try harness.assertPixels(state: "warmup")
        let initialRasters = WidgetCompositor.totalPromotions
        var cpu = 0.0, elapsed = 0.0
        var completed = 0
        var rows: [[String: Any]] = []
        for index in 0..<60 {
            let step = index + 5
            let control = try XCTUnwrap(harness.renderer.scrolling.layers[5])
            let minimumRasters = control.rasterCount + 2
            let ops = state(step, fractional: fractional, contained: contained)
            let before = harness.completion
            let tilesBefore = WidgetTileStats.snapshot
            let rastersBefore = WidgetCompositor.totalPromotions
            let start = CACurrentMediaTime(), cpuStart = cpuSeconds()
            try harness.send(ops, minimumControlRasters: minimumRasters)
            let cpuMilliseconds = (cpuSeconds() - cpuStart) * 1000
            let elapsedMilliseconds = (CACurrentMediaTime() - start) * 1000
            let after = harness.completion
            let exclusionStats = harness.renderer.document?.paintExclusionCacheStats ?? [:]
            let tileJobs = WidgetTileStats.snapshot.jobs - tilesBefore.jobs
            cpu += cpuMilliseconds
            elapsed += elapsedMilliseconds
            try harness.assertApplied(ops)
            try harness.assertControlPixels()
            XCTAssertTrue(harness.renderer.compositedElementIDs.isSubset(of: tracks))
            if promotes {
                XCTAssertGreaterThanOrEqual(harness.renderer.compositedElementIDs.count, fractional ? 40 : 120)
            } else {
                XCTAssertTrue(harness.renderer.compositedElementIDs.isEmpty)
            }
            if contained { XCTAssertEqual(Set(harness.renderer.scrolling.layers.keys), [5]) }
            if index == 0 || (index + 1) % 15 == 0 {
                try harness.assertPixels(state: "mode=\(mode) step=\(step)")
            }
            completed += 1
            rows.append([
                "step": step, "cpu_ms": cpuMilliseconds, "elapsed_ms": elapsedMilliseconds,
                "exclusion_cache_hits": exclusionStats["hits"]?.intValue ?? 0,
                "exclusion_cache_misses": exclusionStats["misses"]?.intValue ?? 0,
                "exclusion_cache_entries": exclusionStats["entries"]?.intValue ?? 0,
                "exclusion_cache_pointers": exclusionStats["pointers"]?.intValue ?? 0,
                "render_callbacks": after.scheduled - before.scheduled, "render_queue_remaining": harness.work.count,
                "layouts": after.layouts - before.layouts, "document_jobs": after.tileJobs - before.tileJobs,
                "document_installs": after.tileInstalls - before.tileInstalls, "tile_jobs": tileJobs,
                "scroll_rasters": after.scrollRasters - before.scrollRasters,
                "scroll_layers": harness.renderer.scrolling.layers.count,
                "scroll_tiles": harness.renderer.scrolling.layers.values.reduce(0) { $0 + $1.tileCount },
                "control_rasters": control.rasterCount - (minimumRasters - 2), "control_tiles": control.tileCount,
                "promoted": harness.renderer.compositedElementIDs.count,
                "compositor_rasters": WidgetCompositor.totalPromotions - rastersBefore,
            ])
        }
        XCTAssertEqual(completed, 60)
        let rasters = WidgetCompositor.totalPromotions - initialRasters
        if promotes && !fractional && WidgetCompositor.localPaintReuse { XCTAssertEqual(rasters, 0) }
        let report: [String: Any] = [
            "kind": "renderer-component-fixed-work", "mode": mode,
            "scroll_compositor_takeover": takeover, "path": contained ? "production-compositor" : takeover ? "experimental-takeover" : "production-scroll",
            "pixel_reference": "fresh-document-with-fresh-scroll-tiles",
            "local_paint_reuse": WidgetCompositor.localPaintReuse, "completed_states": completed,
            "cpu_ms": cpu, "cpu_ms_per_completed_state": cpu / Double(completed),
            "elapsed_ms": elapsed, "compositor_rasters": rasters,
            "pixel_checks": 7, "steps": rows,
        ]
        let data = try JSONSerialization.data(withJSONObject: report, options: [.sortedKeys])
        print("LOCAL_PAINT_WORKLOAD " + String(decoding: data, as: UTF8.self))
        if let directory = ProcessInfo.processInfo.environment["TW_LOCAL_PAINT_WORKLOAD_REPORT"] {
            try FileManager.default.createDirectory(atPath: directory, withIntermediateDirectories: true)
            let name = "\(contained ? "contained-integer" : fractional ? "fractional" : "integer")-takeover-\(takeover ? "on" : "off")-reuse-\(WidgetCompositor.localPaintReuse ? "on" : "off").json"
            try data.write(to: URL(fileURLWithPath: directory).appendingPathComponent(name))
        }
    }

    func testIntegerWidthFixedWorkAndInstalledPixels() throws {
        try run(fractional: false)
    }

    func testFractionalWidthFixedWorkAndInstalledPixels() throws {
        try run(fractional: true)
    }

    func testContainedIntegerWidthFixedWorkAndInstalledPixels() throws {
        try run(fractional: false, contained: true)
    }
}

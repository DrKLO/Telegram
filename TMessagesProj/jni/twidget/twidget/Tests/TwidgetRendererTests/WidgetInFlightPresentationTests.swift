import AppKit
import XCTest
import IOSurface
import WidgetSandbox
@testable import TwidgetRenderer

final class WidgetInFlightPresentationTests: XCTestCase {
    private final class VisibleWindow: NSWindow {
        override var isVisible: Bool { true }
        override var occlusionState: NSWindow.OcclusionState { .visible }
    }

    private final class Session {
        let name: String
        let width: CGFloat
        let queue: DispatchQueue
        let executor: WidgetExecutor
        let renderer: LiteHTMLWidgetRenderer
        let host: WidgetHost
        let window: NSWindow

        init(_ name: String, width: CGFloat) throws {
            self.name = name
            self.width = width
            queue = WidgetExecutor.makeQueue("widget.inflight.\(name)")
            executor = WidgetExecutor(queue: queue)
            renderer = LiteHTMLWidgetRenderer(theme: "light", width: width)
            window = VisibleWindow(contentRect: CGRect(x: 0, y: 0, width: width, height: 700), styleMask: [.borderless], backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            let directory = ProcessInfo.processInfo.environment["TW_INFLIGHT_WIDGET_DIR"].map { URL(fileURLWithPath: $0) } ?? URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("../../../../tools/perflab/widget-glitches/repaired")
            var options = WidgetHost.Options()
            options.asyncValidation = true
            options.sharedValidatorThread = true
            options.startBeforeRendererReady = true
            options.pools = true
            options.features = ["binaryOps", "canvasWords", "webAnimations", "morphUpdates"]
            EnginePool.shared.features = options.features
            let made = renderer
            host = try WidgetHost(html: String(contentsOf: directory.appendingPathComponent(name + ".html")), theme: WidgetTheme(colorScheme: .light), width: width, options: options, rendererFactory: { _, _, _ in made }, executor: executor)
            host.view.frame = CGRect(x: 0, y: 0, width: width, height: 700)
            window.contentView?.addSubview(host.view)
            host.onChange = { [weak host] state in
                if state.height > 0 { host?.view.setFrameSize(CGSize(width: width, height: state.height)) }
            }
            executor.async { [renderer] in
                renderer.preparesOffscreen = true
                renderer.resizeAnchor = .top
            }
        }

        func close() {
            host.destroy()
            window.close()
        }
    }

    private func pump(_ duration: TimeInterval) {
        let end = Date().addingTimeInterval(duration)
        while Date() < end {
            RunLoop.main.run(until: Date().addingTimeInterval(0.001))
        }
    }

    private func waitForInitialFrame(_ session: Session) {
        let end = Date().addingTimeInterval(5)
        while Date() < end {
            let state = session.host.diagnostics
            if state.rendered && session.queue.sync(execute: {
                guard let document = session.renderer.document else { return false }
                let height = min(600, max(50, ceil(document.size.height)))
                return session.renderer.isPrepared && session.renderer.documentView.tilesDrawn && abs(state.height - height) < 0.5 && abs(session.renderer.documentView.size.height - height) < 0.5
            }) { return }
            pump(0.005)
        }
        XCTFail("No initial prepared frame for \(session.name)")
    }

    private func onExecutor<T>(_ session: Session, _ body: @escaping () throws -> T) throws -> T {
        var completed: Result<T, Error>?
        session.executor.urgent {
            let result = Result { try body() }
            DispatchQueue.main.async { completed = result }
        }
        let deadline = Date().addingTimeInterval(5)
        while completed == nil && Date() < deadline { pump(0.001) }
        return try XCTUnwrap(completed, "Native capture did not complete").get()
    }

    private func surfaceImage(_ value: Any?) -> CGImage? {
        guard let value = value else { return nil }
        let object = value as CFTypeRef
        if CFGetTypeID(object) == CGImage.typeID { return value as! CGImage }
        guard CFGetTypeID(object) == IOSurfaceGetTypeID() else { return nil }
        let surface = unsafeBitCast(value as AnyObject, to: IOSurfaceRef.self)
        IOSurfaceLock(surface, .readOnly, nil)
        defer { IOSurfaceUnlock(surface, .readOnly, nil) }
        let width = IOSurfaceGetWidth(surface), height = IOSurfaceGetHeight(surface), row = IOSurfaceGetBytesPerRow(surface)
        let data = Data(bytes: IOSurfaceGetBaseAddress(surface), count: row * height) as CFData
        guard let provider = CGDataProvider(data: data) else { return nil }
        return CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: row, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue), provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent)
    }

    private func preparedPixels(_ session: Session) throws -> NSBitmapImageRep {
        let view = session.renderer.documentView
        let size = view.size
        let scale: CGFloat = 2
        let context = try XCTUnwrap(CGContext(data: nil, width: Int(ceil(size.width * scale)), height: Int(ceil(size.height * scale)), bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        context.setFillColor(CGColor(gray: 1, alpha: 1))
        context.fill(CGRect(x: 0, y: 0, width: size.width * scale, height: size.height * scale))
        context.translateBy(x: 0, y: size.height * scale)
        context.scaleBy(x: scale, y: -scale)
        func paint(_ layer: CALayer) {
            guard !layer.isHidden, layer.opacity > 0 else { return }
            context.saveGState()
            context.translateBy(x: layer.position.x, y: layer.position.y)
            context.concatenate(CATransform3DGetAffineTransform(layer.transform))
            context.translateBy(x: -layer.anchorPoint.x * layer.bounds.width, y: -layer.anchorPoint.y * layer.bounds.height)
            if layer.masksToBounds { context.clip(to: CGRect(origin: .zero, size: layer.bounds.size)) }
            context.setAlpha(CGFloat(layer.opacity))
            if let image = surfaceImage(layer.contents) {
                context.saveGState()
                context.translateBy(x: 0, y: layer.bounds.height)
                context.scaleBy(x: 1, y: -1)
                context.draw(image, in: CGRect(origin: .zero, size: layer.bounds.size))
                context.restoreGState()
            }
            for child in (layer.sublayers ?? []).sorted(by: { $0.zPosition < $1.zPosition }) { paint(child) }
            context.restoreGState()
        }
        paint(try XCTUnwrap(view.contentLayer))
        return NSBitmapImageRep(cgImage: try XCTUnwrap(context.makeImage()))
    }

    private func composedPixels(_ session: Session) throws -> NSBitmapImageRep {
        let image = try XCTUnwrap(session.renderer.snapshot())
        let source = try XCTUnwrap(image.cgImage(forProposedRect: nil, context: nil, hints: nil))
        let context = try XCTUnwrap(CGContext(data: nil, width: source.width, height: source.height, bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        context.setFillColor(CGColor(gray: 1, alpha: 1))
        context.fill(CGRect(x: 0, y: 0, width: source.width, height: source.height))
        context.draw(source, in: CGRect(x: 0, y: 0, width: source.width, height: source.height))
        return NSBitmapImageRep(cgImage: try XCTUnwrap(context.makeImage()))
    }

    private func ink(_ bitmap: NSBitmapImageRep, rect: CGRect, width: CGFloat, darkOnly: Bool = false) -> (count: Int, bounds: CGRect) {
        let scale = CGFloat(bitmap.pixelsWide) / width
        var count = 0
        var bounds = CGRect.null
        let left = max(0, Int(floor(rect.minX * scale))), right = min(bitmap.pixelsWide, Int(ceil(rect.maxX * scale)))
        let top = max(0, Int(floor(rect.minY * scale))), bottom = min(bitmap.pixelsHigh, Int(ceil(rect.maxY * scale)))
        guard left < right, top < bottom else { return (0, .null) }
        guard bitmap.bitsPerSample == 8, bitmap.samplesPerPixel == 4, let bytes = bitmap.bitmapData else { return (0, .null) }
        let first = bitmap.bitmapFormat.contains(.alphaFirst) ? 1 : 0
        for y in top..<bottom {
            for x in left..<right {
                let pixel = bytes.advanced(by: y * bitmap.bytesPerRow + x * 4 + first)
                let visible = darkOnly ? max(pixel[0], pixel[1], pixel[2]) < 102 : min(pixel[0], pixel[1], pixel[2]) < 217
                if visible {
                    count += 1
                    bounds = bounds.union(CGRect(x: CGFloat(x) / scale, y: CGFloat(y) / scale, width: 1 / scale, height: 1 / scale))
                }
            }
        }
        return (count, bounds)
    }

    private func click(_ session: Session, selector: String) throws {
        let rect = try session.queue.sync { try XCTUnwrap(session.renderer.document).bounds(matching: selector) }
        XCTAssertFalse(rect.isEmpty, selector)
        let down = try XCTUnwrap(NSEvent.mouseEvent(with: .leftMouseDown, location: .zero, modifierFlags: [], timestamp: ProcessInfo.processInfo.systemUptime, windowNumber: session.window.windowNumber, context: nil, eventNumber: 0, clickCount: 1, pressure: 1))
        let up = try XCTUnwrap(NSEvent.mouseEvent(with: .leftMouseUp, location: .zero, modifierFlags: [], timestamp: ProcessInfo.processInfo.systemUptime, windowNumber: session.window.windowNumber, context: nil, eventNumber: 1, clickCount: 1, pressure: 0))
        session.queue.sync {
            let point = CGPoint(x: rect.midX, y: rect.midY)
            session.renderer.handleMouseDown(point, event: down)
            session.renderer.handleMouseUp(point, event: up)
        }
    }

    private func capture(_ bitmap: NSBitmapImageRep, session: Session, action: Int, time: Int, kind: String) throws {
        guard let directory = ProcessInfo.processInfo.environment["TW_INFLIGHT_OUTPUT"] else { return }
        let root = URL(fileURLWithPath: directory).appendingPathComponent(session.name)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        try XCTUnwrap(bitmap.representation(using: .png, properties: [:])).write(to: root.appendingPathComponent("\(Int(session.width))-action\(action)-\(time)ms-\(kind).png"))
    }

    private func check(_ session: Session, actions: [String], header: String, controls: String?, selectors: [String], verify: ([String: CGRect], NSBitmapImageRep) throws -> Void) throws {
        waitForInitialFrame(session)
        pump(0.1)
        let initial = try session.queue.sync { () -> (CGRect, CGRect?, Int, Int) in
            let document = try XCTUnwrap(session.renderer.document)
            let box = document.bounds(matching: header)
            let control = controls.map { document.bounds(matching: $0) }
            return (box, control, ink(try self.composedPixels(session), rect: box, width: session.width).count, ink(try self.preparedPixels(session), rect: box, width: session.width).count)
        }
        XCTAssertGreaterThan(initial.2, 50)
        XCTAssertGreaterThan(initial.3, 50)
        var samples = 0
        for (action, selector) in actions.enumerated() {
            try click(session, selector: selector)
            let start = Date()
            var frames: [(time: Int, elapsed: Double, boxes: [String: CGRect], composed: NSBitmapImageRep, prepared: NSBitmapImageRep)] = []
            for time in [0, 8, 16, 33, 66, 120, 220] {
                pump(max(0, Double(time) / 1000 - Date().timeIntervalSince(start)))
                try onExecutor(session) {
                    let elapsed = Date().timeIntervalSince(start) * 1000
                    let document = try XCTUnwrap(session.renderer.document)
                    var boxes: [String: CGRect] = [:]
                    for query in Set(selectors + [header] + (controls.map { [$0] } ?? [])) { boxes[query] = document.bounds(matching: query) }
                    frames.append((time, elapsed, boxes, try self.composedPixels(session), try self.preparedPixels(session)))
                }
            }
            for frame in frames {
                let currentHeader = frame.boxes[header]!
                let label = "\(session.name) action\(action) target\(frame.time) actual\(String(format: "%.1f", frame.elapsed))ms"
                XCTAssertLessThanOrEqual(frame.elapsed - Double(frame.time), 25, label + ": sampling deadline")
                XCTAssertEqual(currentHeader, initial.0, label + ": header geometry")
                if let controls = controls, let control = initial.1 { XCTAssertEqual(frame.boxes[controls], control, label + ": controls geometry") }
                XCTAssertGreaterThanOrEqual(ink(frame.composed, rect: currentHeader, width: session.width).count, initial.2 * 3 / 5, label + ": composed header blank/faded")
                XCTAssertGreaterThanOrEqual(ink(frame.prepared, rect: currentHeader, width: session.width).count, initial.3 * 3 / 5, label + ": prepared header blank/faded")
                for bitmap in [frame.composed, frame.prepared] { try verify(frame.boxes, bitmap) }
                try capture(frame.composed, session: session, action: action, time: frame.time, kind: "composed")
                try capture(frame.prepared, session: session, action: action, time: frame.time, kind: "prepared")
                samples += 1
            }
            print("INFLIGHT \(session.name) width=\(session.width) action=\(action) actual-ms=" + frames.map { String(format: "%.1f", $0.elapsed) }.joined(separator: ","))
            XCTAssertNil(session.host.diagnostics.dead)
        }
        print("INFLIGHT \(session.name) width=\(session.width) actions=\(actions.count) samples=\(samples) composed+prepared")
    }

    func testPackingFirstFramesKeepFullCheckboxInkAndFixedHeader() throws {
        _ = NSApplication.shared
        for width: CGFloat in [320, 440] {
            let session = try Session("packing", width: width)
            defer { session.close() }
            try check(session, actions: ["[data-id='3'] .box", "[data-id='3'] .box", "[data-cat='docs']", "[data-cat='all']", "[data-cat='clothes']", "[data-cat='all']", "[data-id='3'] .box", "[data-cat='gear']", "[data-cat='all']"], header: "#root > .row", controls: "#cats", selectors: ["#root", "#items", ".item:first-child .box"]) { boxes, bitmap in
                let root = boxes["#root"]!, items = boxes["#items"]!
                XCTAssertEqual(items.minX, root.minX, accuracy: 0.1)
                XCTAssertEqual(items.maxX, root.maxX, accuracy: 0.1)
                XCTAssertGreaterThanOrEqual(items.minX, 0)
                XCTAssertLessThanOrEqual(items.maxX, width)
                let box = boxes[".item:first-child .box"]!
                XCTAssertEqual(box.width, 20, accuracy: 0.1)
                XCTAssertEqual(box.height, 20, accuracy: 0.1)
                let painted = self.ink(bitmap, rect: box.insetBy(dx: -1, dy: -1), width: width)
                XCTAssertGreaterThanOrEqual(painted.bounds.width, 18)
                XCTAssertGreaterThanOrEqual(painted.bounds.height, 18)
            }
        }
    }

    func testPhrasebookFirstFramesKeepRowsWithinTheSameInsets() throws {
        _ = NSApplication.shared
        for width: CGFloat in [320, 440] {
            let session = try Session("phrasebook", width: width)
            defer { session.close() }
            try check(session, actions: ["[data-row='0']", "[data-cat='1']", "[data-cat='0']", "[data-row='0']", "[data-cat='2']", "[data-cat='0']", "[data-cat='3']", "[data-cat='0']"], header: "#v-list > .row:first-child", controls: "#cats", selectors: ["#rows", "#rows > .pr:first-child", "#rows > .pr:first-child > .row"]) { boxes, bitmap in
                let rows = boxes["#rows"]!, row = boxes["#rows > .pr:first-child"]!, content = boxes["#rows > .pr:first-child > .row"]!
                XCTAssertEqual(row.minX, rows.minX, accuracy: 0.1)
                XCTAssertEqual(row.maxX, rows.maxX, accuracy: 0.1)
                XCTAssertEqual(content.minX - row.minX, 8, accuracy: 0.1)
                XCTAssertEqual(row.maxX - content.maxX, 8, accuracy: 0.1)
                XCTAssertGreaterThanOrEqual(row.minX, 0)
                XCTAssertLessThanOrEqual(row.maxX, width)
                let text = self.ink(bitmap, rect: CGRect(x: row.minX, y: row.minY + 3, width: row.width - 24, height: 20), width: width, darkOnly: true)
                XCTAssertGreaterThan(text.count, 20)
                XCTAssertGreaterThanOrEqual(text.bounds.minX, content.minX - 1)
                XCTAssertLessThanOrEqual(text.bounds.minX, content.minX + 4)
            }
        }
    }

    func testMusicFirstFramesKeepArtworkControlsAndWaveformPainted() throws {
        _ = NSApplication.shared
        for width: CGFloat in [320, 440] {
            let session = try Session("music-player", width: width)
            defer { session.close() }
            try check(session, actions: ["[data-action='queue']", "[data-action='next']", "[data-action='next']", "[data-action='queue']", "[data-action='queue']", "[data-action='prev']", "[data-action='play']", "[data-action='play']", "[data-action='queue']"], header: ".top", controls: ".ctl", selectors: ["#art", "#eq", "#list .q"]) { boxes, bitmap in
                let art = boxes["#art"]!, canvas = boxes["#eq"]!, controls = boxes[".ctl"]!
                if let rows = boxes["#list .q"], !rows.isEmpty {
                    XCTAssertGreaterThanOrEqual(rows.minX, 0)
                    XCTAssertLessThanOrEqual(rows.maxX, width)
                }
                XCTAssertEqual(art.size, CGSize(width: 64, height: 64))
                for box in [art, canvas, controls] {
                    XCTAssertGreaterThanOrEqual(box.minX, 0)
                    XCTAssertLessThanOrEqual(box.maxX, width)
                }
                XCTAssertGreaterThan(self.ink(bitmap, rect: art, width: width).count, 2000)
                XCTAssertGreaterThan(self.ink(bitmap, rect: canvas, width: width).count, 150)
                XCTAssertGreaterThan(self.ink(bitmap, rect: controls, width: width).count, 100)
            }
        }
    }
    func testStartupHiddenElementsHaveNoGeometryOrPaintAcrossTheRealSamples() throws {
        _ = NSApplication.shared
        let directory = ProcessInfo.processInfo.environment["TW_INFLIGHT_WIDGET_DIR"].map { URL(fileURLWithPath: $0) } ?? URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("../../../../tools/perflab/widget-glitches/repaired")
        let files = try FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil).filter { $0.pathExtension == "html" }.sorted { $0.path < $1.path }
        XCTAssertGreaterThanOrEqual(files.count, 19)
        var checked = 0
        for file in files {
            let session = try Session(file.deletingPathExtension().lastPathComponent, width: 320)
            defer { session.close() }
            waitForInitialFrame(session)
            try session.queue.sync {
                let document = try XCTUnwrap(session.renderer.document)
                let hidden = session.renderer.dom.nodes.values.filter { $0.attribute("hidden") != nil }
                let bounds = document.bounds(byAttribute: WidgetDOM.idAttribute)
                let ids = Set(hidden.map { String(session.renderer.dom.publicId($0)) })
                for id in ids {
                    XCTAssertTrue(bounds[id]?.rectValue.isEmpty ?? true, "\(file.lastPathComponent) hidden node\(id) occupies layout space")
                }
                let rect = CGRect(x: 0, y: 0, width: 320, height: max(1, document.size.height))
                let normal = try XCTUnwrap(document.copyImage(maxWidth: 320, rect: rect, scale: 1))
                let excluded = try XCTUnwrap(document.copyImage(maxWidth: 320, rect: rect, scale: 1, excluding: ids, attribute: WidgetDOM.idAttribute))
                let normalPixels = try XCTUnwrap(normal.dataProvider?.data) as Data
                let excludedPixels = try XCTUnwrap(excluded.dataProvider?.data) as Data
                XCTAssertEqual(normalPixels, excludedPixels, "\(file.lastPathComponent) hidden elements contribute painted pixels")
                checked += ids.count
            }
        }
        XCTAssertGreaterThan(checked, 10)
        print("HIDDEN_STARTUP samples=\(files.count) hidden-elements=\(checked) geometry-zero+paint-identical")
    }

}

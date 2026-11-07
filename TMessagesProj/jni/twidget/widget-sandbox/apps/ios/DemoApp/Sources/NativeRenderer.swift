// The native renderer prototypes behind one Swift glue: the Renderer contract
// of WidgetHost on a C ABI (`twb_*` for Blitz in renderer-blitz/, `twl_*` for
// litehtml in renderer-litehtml/) that takes validated RenderOps as JSON,
// answers queries and events as JSON, and paints premultiplied RGBA on
// request. This file shows the raster in a plain view, feeds it touches (CSS
// px = points), coalesces paints into display refreshes and reports
// diagnostics; `NativeEngine` is the table of C functions that differs per
// engine (BlitzRenderer.swift, LitehtmlRenderer.swift). No WKWebView, no
// script, no network: the engines only ever see validated RenderOps and
// `data:` images. Dev tooling for docs/blitz-experiment.md and
// docs/litehtml-renderer.md, not a product path. iOS and macOS share this
// file; the view-level differences (touches vs mouse, CADisplayLink vs a
// timer) are the `#if` branches below. Mirrors BlitzRenderer.kt /
// LitehtmlRenderer.kt on Android.
// Frozen since 2026-10-30 (docs/decisions.md, "The renderer prototypes are frozen"): compiled only in a
// build with TW_NATIVE_RENDERERS=YES, which also builds and links the engines (project.yml).

#if TW_NATIVE_RENDERERS
import Foundation
import QuartzCore
import WidgetSandbox
#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

/// The C ABI of one engine (renderer-blitz/include/twidget_blitz.h = renderer-litehtml/include/twidget_litehtml.h).
struct NativeEngine {
    /// "Blitz" / "litehtml": the diagnostics keys are `<name lowercased>-init|paint|touch`, the bubble caption `sandbox · <name> renderer`.
    let name: String
    let create: (Double, Double, String) -> OpaquePointer?
    let destroy: (OpaquePointer?) -> Void
    let receive: (OpaquePointer, String) -> UnsafeMutablePointer<CChar>?
    let setWidth: (OpaquePointer, Double) -> UnsafeMutablePointer<CChar>?
    let setTheme: (OpaquePointer, String) -> UnsafeMutablePointer<CChar>?
    let touch: (OpaquePointer, Int32, Double, Double, Int32, Bool) -> UnsafeMutablePointer<CChar>?
    let size: (OpaquePointer, UnsafeMutablePointer<UInt32>, UnsafeMutablePointer<UInt32>) -> Void
    let dirty: (OpaquePointer) -> Bool
    let render: (OpaquePointer, UnsafeMutablePointer<UInt8>?, Int) -> Double
    let stringFree: (UnsafeMutablePointer<CChar>?) -> Void
    /// Engine-specific diagnostics lines, asked once after `load` and after each reported paint (optional).
    var report: ((OpaquePointer) -> [(String, String)])? = nil
}

final class NativeRenderer: Renderer {
    let engine: NativeEngine
    private var handle: OpaquePointer?
    private let rasterView: NativeRasterView
    private let dpr: Double
    var view: PlatformView { rasterView }
    var onMessage: ((String) -> Void)?
    var onDiag: ((String, String) -> Void)?

    private var destroyed = false
    private var frozen = false
    private var paintScheduled = false
    private var frameRequested = false
    private var lastPaintReport = Date.distantPast
    /// Paint statistics for the diagnostics panel (`<engine>-paint`).
    private(set) var paints = 0
    private(set) var paintMs = 0.0
    private var key: String { engine.name.lowercased() }

    init(engine: NativeEngine, theme: String, width: CGFloat) {
        self.engine = engine
        #if canImport(UIKit)
        dpr = Double(UIScreen.main.scale)
        #else
        dpr = Double(NSScreen.main?.backingScaleFactor ?? 2)
        #endif
        let t0 = CACurrentMediaTime()
        handle = engine.create(Double(width), dpr, theme)
        initMs = (CACurrentMediaTime() - t0) * 1000
        rasterView = NativeRasterView(frame: CGRect(x: 0, y: 0, width: width, height: 24))
        rasterView.renderer = self
    }

    /// Creating the document; the first one in the process may also scan the system fonts (shared afterwards).
    private let initMs: Double

    deinit { destroy() }

    func load() {
        guard let handle else {
            onDiag?("renderer", "\(engine.name) renderer failed to initialise")
            return
        }
        onDiag?("\(key)-init", String(format: "%.1f ms", initMs))
        engine.report?(handle).forEach { onDiag?($0.0, $0.1) }
        DispatchQueue.main.async { [self] in
            if destroyed { return }
            onMessage?("{\"type\":\"ready\"}")
            schedulePaint()
        }
    }

    func send(_ json: String) {
        guard !destroyed, let handle else { return }
        if json.hasPrefix("{\"type\":\"requestFrame\"") {
            requestFrame()
            return
        }
        if json.hasPrefix("{\"type\":\"frozen\"") { frozen = true }
        dispatch(engine.receive(handle, json))
        if engine.dirty(handle) { schedulePaint() }
    }

    func setTheme(_ theme: String) {
        guard !destroyed, let handle else { return }
        dispatch(engine.setTheme(handle, theme))
        if engine.dirty(handle) { schedulePaint() }
    }

    func evaluate(_ script: String, completion: @escaping (String?) -> Void) {
        completion(nil)
    }

    func destroy() {
        if destroyed { return }
        destroyed = true
        stopClock()
        rasterView.removeFromSuperview()
        rasterView.renderer = nil
        engine.destroy(handle)
        handle = nil
    }

    // MARK: renderer → host

    /// A JSON array of messages from the native side; each one goes to the host separately.
    /// Returns a short label per message (`event:click`, `size`, …) for the touch diagnostics.
    @discardableResult
    fileprivate func dispatch(_ cString: UnsafeMutablePointer<CChar>?) -> [String] {
        guard let cString else { return [] }
        defer { engine.stringFree(cString) }
        if destroyed { return [] }
        guard let array = try? JSONSerialization.jsonObject(with: Data(bytes: cString, count: strlen(cString))) as? [[String: Any]] else { return [] }
        var labels: [String] = []
        for msg in array {
            let type = msg["type"] as? String ?? "?"
            if type == "violation" { onDiag?("renderer-violation", msg["detail"] as? String ?? "") }
            labels.append(type == "event" ? "event:" + ((msg["ev"] as? [String: Any])?["type"] as? String ?? "?") : type)
            if let data = try? JSONSerialization.data(withJSONObject: msg), let json = String(data: data, encoding: .utf8) { onMessage?(json) }
        }
        return labels
    }

    private var touchDown: CGPoint = .zero
    private var touchMoves = 0

    fileprivate func width(_ width: Double) {
        guard !destroyed, let handle else { return }
        dispatch(engine.setWidth(handle, width))
        if engine.dirty(handle) { schedulePaint() }
    }

    /// A touch in CSS px (= points). action: 0 down, 1 move, 2 up, 3 cancel.
    fileprivate func touch(_ action: Int32, _ point: CGPoint, pointer: Int32, primary: Bool) {
        guard !destroyed, let handle else { return }
        let labels = dispatch(engine.touch(handle, action, Double(point.x), Double(point.y), pointer, primary))
        // Dev panel: what the document made of a primary touch (the moves are only counted).
        if primary {
            switch action {
            case 0: touchDown = point; touchMoves = 0
            case 1: touchMoves += 1
            default:
                let name = action == 2 ? "up" : "cancel"
                onDiag?("\(key)-touch", String(format: "%@ after %d moves, %.0f pt away → %@", name, touchMoves, hypot(point.x - touchDown.x, point.y - touchDown.y), labels.isEmpty ? "nothing" : labels.joined(separator: ",")))
            }
        }
        if engine.dirty(handle) { schedulePaint() }
    }

    // MARK: frames and paints — coalesced into display refreshes

    private func requestFrame() {
        if frozen || frameRequested { return }
        frameRequested = true
        startClock()
    }

    /// Paints are coalesced: one raster per display refresh at most.
    private func schedulePaint() {
        if paintScheduled || destroyed { return }
        paintScheduled = true
        startClock()
    }

    private func tick() {
        if destroyed { return }
        let frame = frameRequested, paint = paintScheduled
        frameRequested = false
        paintScheduled = false
        stopClock()
        // Like rAF before the browser's paint: the callback first, the raster after.
        if frame { onMessage?("{\"type\":\"frame\",\"time\":\(CACurrentMediaTime() * 1000)}") }
        if paint { self.paint() }
    }

    #if canImport(UIKit)
    private var displayLink: CADisplayLink?
    private func startClock() {
        if displayLink != nil { return }
        let link = CADisplayLink(target: ClockTarget { [weak self] in self?.tick() }, selector: #selector(ClockTarget.fire))
        link.add(to: .main, forMode: .common)
        displayLink = link
    }
    private func stopClock() {
        displayLink?.invalidate()
        displayLink = nil
    }
    #else
    private var clockArmed = false
    private func startClock() {
        if clockArmed { return }
        clockArmed = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0 / 60) { [weak self] in
            self?.clockArmed = false
            self?.tick()
        }
    }
    private func stopClock() {}
    #endif

    private func paint() {
        guard let handle else { return }
        var w: UInt32 = 0, h: UInt32 = 0
        engine.size(handle, &w, &h)
        guard w > 0, h > 0 else { return }
        let (width, height) = (Int(w), Int(h))
        var pixels = Data(count: width * height * 4)
        let ms = pixels.withUnsafeMutableBytes { engine.render(handle, $0.baseAddress?.assumingMemoryBound(to: UInt8.self), $0.count) }
        if ms < 0 {
            onDiag?("renderer", "\(engine.name) paint failed (\(ms))")
            return
        }
        guard let provider = CGDataProvider(data: pixels as CFData),
              let image = CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: width * 4,
                                  space: CGColorSpaceCreateDeviceRGB(),
                                  bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue),
                                  provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent) else { return }
        paints += 1
        paintMs = ms
        if Date().timeIntervalSince(lastPaintReport) > 1 {
            lastPaintReport = Date()
            onDiag?("\(key)-paint", String(format: "%.1f ms, %d×%d px, paint #%d", ms, width, height, paints))
            engine.report?(handle).forEach { onDiag?($0.0, $0.1) }
        }
        rasterView.present(image, scale: dpr)
    }
}

/// CADisplayLink wants an Objective-C selector target.
private final class ClockTarget: NSObject {
    let callback: () -> Void
    init(_ callback: @escaping () -> Void) { self.callback = callback }
    @objc func fire() { callback() }
}

#if canImport(UIKit)
/// Shows the last raster and feeds touches to the document (CSS px = points).
/// A scrolling chat list cancels the touches, which reaches the document as pointercancel.
private final class NativeRasterView: UIView {
    weak var renderer: NativeRenderer?
    private var pointerIds: [ObjectIdentifier: Int32] = [:]
    private var nextPointer: Int32 = 0
    private var lastWidth: CGFloat = 0

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        backgroundColor = .clear
        isMultipleTouchEnabled = true
        layer.contentsGravity = .topLeft
        layer.masksToBounds = true
    }
    required init?(coder: NSCoder) { fatalError() }

    func present(_ image: CGImage, scale: Double) {
        layer.contentsScale = scale
        layer.contents = image
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        if bounds.width != lastWidth, bounds.width > 0 {
            lastWidth = bounds.width
            renderer?.width(Double(bounds.width))
        }
    }

    private func pointer(for touch: UITouch) -> (id: Int32, primary: Bool) {
        let key = ObjectIdentifier(touch)
        if let id = pointerIds[key] { return (id, id == pointerIds.values.min()) }
        let id = nextPointer
        nextPointer += 1
        pointerIds[key] = id
        return (id, pointerIds.count == 1)
    }

    private func forward(_ touches: Set<UITouch>, action: Int32) {
        for touch in touches {
            let p = pointer(for: touch)
            renderer?.touch(action, touch.location(in: self), pointer: p.id, primary: p.primary)
            if action >= 2 { pointerIds[ObjectIdentifier(touch)] = nil }
        }
        if pointerIds.isEmpty { nextPointer = 0 }
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) { forward(touches, action: 0) }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) { forward(touches, action: 1) }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) { forward(touches, action: 2) }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { forward(touches, action: 3) }
}
#elseif canImport(AppKit)
/// Shows the last raster and feeds the mouse to the document as one primary pointer (CSS px = points, y down).
private final class NativeRasterView: NSView {
    weak var renderer: NativeRenderer?
    private var lastWidth: CGFloat = 0

    override init(frame: CGRect) {
        super.init(frame: frame)
        wantsLayer = true
        layer?.contentsGravity = .topLeft
        layer?.masksToBounds = true
    }
    required init?(coder: NSCoder) { fatalError() }

    override var isFlipped: Bool { true }

    func present(_ image: CGImage, scale: Double) {
        layer?.contentsScale = scale
        layer?.contents = image
    }

    override func layout() {
        super.layout()
        if bounds.width != lastWidth, bounds.width > 0 {
            lastWidth = bounds.width
            renderer?.width(Double(bounds.width))
        }
    }

    private func forward(_ event: NSEvent, action: Int32) {
        renderer?.touch(action, convert(event.locationInWindow, from: nil), pointer: 0, primary: true)
    }

    override func mouseDown(with event: NSEvent) { forward(event, action: 0) }
    override func mouseDragged(with event: NSEvent) { forward(event, action: 1) }
    override func mouseUp(with event: NSEvent) { forward(event, action: 2) }
}
#endif

#endif

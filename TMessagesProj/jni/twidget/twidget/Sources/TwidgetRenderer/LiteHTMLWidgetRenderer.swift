import AppKit
import IOSurface
import CoreVideo
import LiteHTML
import WidgetSandboxProtocol
import struct WidgetSandbox.GestureArbiter
import enum WidgetSandbox.GesturePolicy
import struct WidgetSandbox.WidgetGestureClaim
import enum WidgetSandbox.WidgetResizeAnchor
import protocol WidgetSandbox.TaskRenderer

public final class LiteHTMLWidgetRenderer: TaskRenderer {
    public let view: NSView
    public var onMessage: ((String) -> Void)? {
        didSet {
            flushBuffered()
        }
    }
    public var onDiag: ((String, String) -> Void)?
    public var schedule: (@escaping () -> Void) -> Void = { DispatchQueue.main.async(execute: $0) }
    public var scheduleAfter: (TimeInterval, @escaping () -> Void) -> Void = { DispatchQueue.main.asyncAfter(deadline: .now() + $0, execute: $1) }
    public var isOnExecutor: () -> Bool = { Thread.isMainThread }
    public var debugName: String?
    public lazy var scheduleUrgent: (@escaping () -> Void) -> Void = { [weak self] block in self?.schedule(block) }
    public var executorIsMain: () -> Bool = { true }

    func onExecutor(_ block: @escaping () -> Void) {
        if isOnExecutor() {
            block()
        } else {
            schedule(block)
        }
    }
    public var onSerialized: ((String, Bool) -> Void)?
    private var layoutStats = (count: 0, total: 0.0, worst: 0.0)
    public var layoutCount: Int {
        statsLock.lock()
        defer { statsLock.unlock() }
        return layoutStats.count
    }
    public var layoutMilliseconds: Double {
        statsLock.lock()
        defer { statsLock.unlock() }
        return layoutStats.total
    }
    public var layoutMaxMilliseconds: Double {
        statsLock.lock()
        defer { statsLock.unlock() }
        return layoutStats.worst
    }
    public private(set) var stageMilliseconds: [String: Double] = [:]
    public private(set) var passMaxCPUMilliseconds = 0.0

    static func threadCPUSeconds() -> Double {
        var t = timespec()
        clock_gettime(CLOCK_THREAD_CPUTIME_ID, &t)
        return Double(t.tv_sec) + Double(t.tv_nsec) / 1e9
    }

    public var drawMilliseconds: Double {
        return documentView.drawMilliseconds
    }
    public var drawCount: Int {
        return documentView.drawCount
    }
    public var drawMaxMilliseconds: Double {
        return documentView.drawMaxMilliseconds
    }
    public var verification: (frames: Int, mismatched: Int) {
        return (documentView.verifiedFrames, documentView.mismatchedFrames)
    }

    let documentView: WidgetDocumentView
    let dom = WidgetDOM()
    private var theme: String
    private(set) var document: LHDocument?
    private var masterCSS = WidgetThemeStyle.uaStylesheet
    private var buffered: [String] = []
    var listened: Set<String> = []
    var prevented: Set<String> = []
    private var lastSize = ""
    private var sizeTransition = false
    var frozen = false
    var destroyed = false
    private var needsRender = false
    private var frameRequested = false
    private var idleTicks = 0
    private var controlsActive = false
    private var lastFramePost: CFTimeInterval = 0
    private var frameHoldStart: CFTimeInterval?
    private var frameHoldPosted: CFTimeInterval?
    private var frameHoldAnswers = 0
    private var frameHoldRender = false
    static var frameHoldLimit: CFTimeInterval = 0.05
    static var frameHoldAnswerWait: CFTimeInterval = 0.02
    private static let frameInterval = ProcessInfo.processInfo.environment["TW_RAF_FPS"].flatMap { Double($0) }.map { 1 / $0 - 0.002 } ?? 1.0 / 60 - 0.002
    private var clock: WidgetFrameClock?
    private let timeOrigin = CACurrentMediaTime()
    private var hiddenSince: CFTimeInterval? = CACurrentMediaTime()
    private var hiddenTotal: CFTimeInterval = 0

    private var animationNow: CFTimeInterval {
        return (hiddenSince ?? CACurrentMediaTime()) - hiddenTotal
    }

    private func updateAnimationClock() {
        let onScreen = isOnScreen
        if onScreen, let since = hiddenSince {
            hiddenTotal += CACurrentMediaTime() - since
            hiddenSince = nil
        } else if !onScreen, hiddenSince == nil {
            hiddenSince = CACurrentMediaTime()
        }
    }
    private struct PendingMove {
        let event: String
        let movement: CGPoint
    }

    private struct PendingMoveQueue {
        private var entries: [String: PendingMove] = [:]
        private var keys: [String] = []

        var isEmpty: Bool { entries.isEmpty }
        var values: [PendingMove] { keys.compactMap { entries[$0] } }

        subscript(key: String) -> PendingMove? { entries[key] }

        mutating func set(_ move: PendingMove, for key: String) {
            if entries[key] == nil { keys.append(key) }
            entries[key] = move
        }

        mutating func removeAll() {
            entries.removeAll()
            keys.removeAll()
        }
    }

    private var pendingMoves = PendingMoveQueue()
    private var bounds: [Int: CGRect] = [:]
    private var scannedDeclarations: (generation: UInt64, values: [Int: [String: String]]) = (0, [:])
    private var visualBounds: [Int: CGRect]?
    private var boundsValid = false {
        didSet { if !boundsValid { visualBounds = nil } }
    }
    private var hoverId = 0
    var form = WidgetFormState()
    private var pressId = 0
    private var pointerCaptureId = 0
    private var lastPointer: CGPoint?
    private var clickCount = 0
    private var gestures = GestureArbiter()
    private var lastClick: (time: CFTimeInterval, point: CGPoint) = (0, .zero)
    private var observedResize: Set<Int> = []
    private let resizeDiffLock = NSLock()
    private var resizeDiff: WidgetResizeDiff?
    static let resizePointerAge: CFTimeInterval = 1.5
    static let resizeBurstGap: CFTimeInterval = 0.15
    private var resizeDiffAt: CFTimeInterval = 0
    private var resizeClickY: CGFloat = 0
    private var resizePressId = 0
    private var nextBounds: (document: LHDocument, bounds: [Int: CGRect])?
    private var resizePrefersBottom = false
    private var resizeAnchorOverride: WidgetResizeAnchor?

    public var resizeAnchor: WidgetResizeAnchor? {
        get {
            resizeDiffLock.lock()
            defer { resizeDiffLock.unlock() }
            return resizeAnchorOverride
        }
        set {
            resizeDiffLock.lock()
            resizeAnchorOverride = newValue
            resizeDiffLock.unlock()
        }
    }

    public var alignmentDebug: String {
        return documentView.alignmentDebug
    }

    public var lastResizeDiff: WidgetResizeDiff? {
        resizeDiffLock.lock()
        defer { resizeDiffLock.unlock() }
        return resizeDiff
    }

    public var prefersBottomResize: Bool {
        get {
            resizeDiffLock.lock()
            defer { resizeDiffLock.unlock() }
            return resizePrefersBottom
        }
        set {
            resizeDiffLock.lock()
            resizePrefersBottom = newValue
            resizeDiffLock.unlock()
        }
    }

    private func adoptNextBounds(_ document: LHDocument) {
        guard let next = nextBounds else {
            return
        }
        nextBounds = nil
        if next.document === document {
            bounds = next.bounds
            boundsValid = true
        }
    }

    private var probing = false
    private var probeBase: (document: LHDocument, bounds: [Int: CGRect]?)?
    private var probePending = false

    private func noteResize(to next: LHDocument) {
        if let base = taskResizeBase {
            taskResizeBase = nil
            probeBase = nil
            noteResize(from: base.document, oldBounds: { base.bounds }, to: next)
        } else if let base = probeBase {
            probeBase = nil
            noteResize(from: base.document, oldBounds: { base.bounds ?? base.document.idBounds() }, to: next)
        } else if let previous = document {
            noteResize(from: previous, oldBounds: { [unowned self] in self.elementBounds() }, to: next)
        }
    }

    private func finishProbe() {
        probing = false
        guard probePending else {
            probeBase = nil
            return
        }
        probePending = false
        if let document = document {
            noteResize(to: document)
            documentView.presentAlignmentIfDrawnSynchronously(document)
        }
        reportSize()
        reportResize()
    }

    private func noteResize(from previous: LHDocument, oldBounds: () -> [Int: CGRect], to next: LHDocument) {
        let oldHeight = ceil(previous.size.height)
        let newHeight = ceil(next.size.height)
        guard abs(oldHeight - newHeight) > WidgetResizeDiff.tolerance else {
            return
        }
        let now = CACurrentMediaTime()
        let recent = now - lastClick.time < LiteHTMLWidgetRenderer.resizePointerAge
        resizeDiffLock.lock()
        let previousKeepsBottom = now - resizeDiffAt < LiteHTMLWidgetRenderer.resizeBurstGap ? resizeDiff?.keepsBottom : nil
        let prefersBottom = resizePrefersBottom
        let anchor = resizeAnchorOverride
        resizeDiffLock.unlock()
        let pointer = recent ? CGPoint(x: lastClick.point.x, y: resizeClickY) : nil
        var diff: WidgetResizeDiff
        if let keepsBottom = previousKeepsBottom, abs(newHeight - oldHeight) <= 1.5 {
            diff = WidgetResizeDiff(oldHeight: oldHeight, newHeight: newHeight, unchangedTop: keepsBottom ? 0 : oldHeight, unchangedBottom: keepsBottom ? oldHeight : 0, keepsBottom: keepsBottom)
        } else {
            let fresh = next.idBounds()
            diff = WidgetResizeDiff.compute(old: oldBounds(), new: fresh, oldHeight: oldHeight, newHeight: newHeight, exactOldHeight: previous.size.height, exactNewHeight: next.size.height, pointer: pointer, pointerTarget: recent ? resizePressId : nil, prefersBottom: prefersBottom, previousKeepsBottom: previousKeepsBottom)
            nextBounds = (next, fresh)
        }
        if let anchor = anchor {
            diff = WidgetResizeDiff(oldHeight: diff.oldHeight, newHeight: diff.newHeight, unchangedTop: diff.unchangedTop, unchangedBottom: diff.unchangedBottom, pointerFrom: diff.pointerFrom, pointerTo: diff.pointerTo, keepsBottom: anchor == .bottom)
        }
        if diff.keepsBottom {
            resizeClickY += next.size.height - previous.size.height
        }
        resizeDiffLock.lock()
        resizeDiff = diff
        resizeDiffAt = now
        resizeDiffLock.unlock()
        let shift: CGFloat
        if let anchor = anchor {
            shift = anchor == .bottom ? newHeight - oldHeight : 0
        } else if let from = diff.pointerFrom, let to = diff.pointerTo {
            shift = to - from
        } else {
            shift = diff.keepsBottom ? newHeight - oldHeight : 0
        }
        documentView.noteAnchorShift(shift, height: newHeight)
    }

    private var displayList: LHDisplayList?
    private let animator = WidgetAnimator()
    private lazy var compositor = WidgetCompositor(host: documentView.contentLayer)
    private let svgLayers = WidgetSVGLayers()
    private let shimmers = WidgetShimmers()
    private var shimmerVerifyTick = 0

    public var shimmerCheckReport: String {
        return shimmers.report
    }
    let scrolling = WidgetScrolling()
    private var lastScrollExclusions: Set<Int> = []
    private lazy var magic: WidgetMagicMove = {
        let magic = WidgetMagicMove(host: documentView.contentLayer)
        magic.onFinish = { [weak self] in
            self?.finishMagicMove()
        }
        return magic
    }()
    public var magicMoveCount: Int {
        return magic.transitions
    }
    public var tileLayerCount: Int {
        return documentView.tileCount
    }

    var staleTileCount: Int {
        return documentView.staleTileCount
    }

    var tilesPreparing: Bool {
        return documentView.preparing
    }
    private let statsLock = NSLock()
    private var publishedComposited = 0

    var dirtyArea: CGFloat {
        return documentView.dirtyArea
    }

    public var compositedCount: Int {
        if isOnExecutor() {
            return compositor.ids.count
        }
        statsLock.lock()
        defer { statsLock.unlock() }
        return publishedComposited
    }
    public var promotionCount: Int {
        return compositor.promotions
    }
    public var rereachCount: Int {
        return compositor.rereaches
    }
    private(set) var canvases: [Int: WidgetCanvas] = [:]
    private var canvasAttributeSizes: [Int: (Int, Int)] = [:]
    private var canvasURLs: [Int: String] = [:]
    private let uid = UUID().uuidString.lowercased()
    private var styleSheetKey = ""
    private var overrides: [Int: [(String, String)]] = [:]
    private var lastResize: [Int: CGSize] = [:]
    private var webUnderlying: [Int: [String: String]] = [:]

    static let maxCanvases = 64
    static let maxCanvasPixels = 4096 * 4096
    static let maxSurfaces = 8
    static let surfaceAttribute = "data-tw3d"

    private let surfaceCallbacksLock = NSLock()
    private var lockedSurfaceLayer: ((String) -> CALayer?)?
    private var lockedSurfaceLayout: ((String, CGSize, CGFloat, Bool) -> Void)?

    public var surfaceLayer: ((String) -> CALayer?)? {
        get {
            surfaceCallbacksLock.lock()
            defer { surfaceCallbacksLock.unlock() }
            return lockedSurfaceLayer
        }
        set {
            surfaceCallbacksLock.lock()
            lockedSurfaceLayer = newValue
            surfaceCallbacksLock.unlock()
            placeSurfaces()
        }
    }

    public var surfaceLayout: ((String, CGSize, CGFloat, Bool) -> Void)? {
        get {
            surfaceCallbacksLock.lock()
            defer { surfaceCallbacksLock.unlock() }
            return lockedSurfaceLayout
        }
        set {
            surfaceCallbacksLock.lock()
            lockedSurfaceLayout = newValue
            surfaceCallbacksLock.unlock()
        }
    }
    private var surfaceContainer: CALayer?
    private var placedSurfaces: [String: CALayer] = [:]
    private var surfaceTokens: [String] = []
    private var surfaceRevision: UInt64 = .max

    private static let alwaysSent: Set<String> = ["click", "input", "change", "submit", "reset", "load", "error", "toggle", "animationstart", "animationend", "transitionend"]
    private static let coalesced: Set<String> = ["pointermove", "mousemove", "touchmove", "wheel", "scroll"]

    public init(theme: String, width: CGFloat) {
        self.theme = theme
        documentView = WidgetDocumentView.backingStore ? WidgetBackingStoreDocumentView(frame: CGRect(x: 0, y: 0, width: width, height: 300)) : WidgetDocumentView(frame: CGRect(x: 0, y: 0, width: width, height: 300))
        view = documentView
        documentView.owner = self
        scrolling.schedule = { [weak self] block in
            self?.schedule(block)
        }
        dom.onViolation = { [weak self] detail in
            self?.post(["type": "violation", "detail": detail])
        }
        dom.onProperty = { [weak self] node, name, value in
            self?.setProperty(node, name, value)
        }
        dom.onCall = { [weak self] node, method, args in
            self?.call(node, method, args)
        }
        dom.onCanvas = { [weak self] node, commands in
            self?.canvas(for: node).run(commands)
        }
        dom.onImageSource = { [weak self] node in
            self?.imageSourceChanged(node)
        }
        WidgetCanvasImages.install()
        animator.onEvent = { [weak self] type, id, name, elapsed in
            self?.dispatchAnimationEvent(type, id, name: name, elapsed: elapsed)
        }
        animator.underlying = { [weak self] id, property in
            self?.webUnderlying[id]?[property]
        }
        animator.web.isAlive = { [weak self] id in
            self?.dom.node(id) != nil
        }
    }

    public func load() {
        render()
        post(["type": "ready"])
    }

    deinit {
        forgetCanvasImages()
    }

    private func forgetCanvasImages() {
        for id in canvases.keys {
            WidgetCanvasImages.unregister(uid + "-" + String(id))
        }
        for url in Array(canvasURLs.values) + retiredURLs {
            LHImages.forgetImage(withURL: url)
        }
    }

    public func setTheme(_ theme: String) {
        guard theme != self.theme else {
            return
        }
        self.theme = theme
        setNeedsRender()
        post(["type": "media"])
    }

    public var selectedText: String? {
        return documentView.textSelection.text
    }

    public func clearTextSelection() {
        documentView.textSelection.clear()
    }

    public var onTextSelectionChange: ((Bool) -> Void)? {
        get { return documentView.textSelection.onChange }
        set { documentView.textSelection.onChange = newValue }
    }

    public var textSelectionColor: NSColor {
        get { return documentView.textSelection.color }
        set { documentView.textSelection.color = newValue }
    }

    public func snapshot() -> NSImage? {
        guard let document = document else {
            return nil
        }
        let size = documentView.size
        let scale = documentView.scale
        guard let image = document.copyImage(maxWidth: size.width, rect: CGRect(origin: .zero, size: size), scale: scale) else {
            return nil
        }
        if compositor.ids.isEmpty && !magic.isActive && presenters.isEmpty && scrolling.layers.isEmpty {
            return NSImage(cgImage: image, size: size)
        }
        guard let ctx = CGContext(data: nil, width: image.width, height: image.height, bitsPerComponent: 8, bytesPerRow: 0, space: image.colorSpace ?? CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue) else {
            return NSImage(cgImage: image, size: size)
        }
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
        ctx.translateBy(x: 0, y: CGFloat(image.height))
        ctx.scaleBy(x: scale, y: -scale)
        scrolling.draw(into: ctx, document: document, scale: scale)
        for (_, presenter) in presenters.sorted(by: { $0.key < $1.key }) {
            presenter.draw(into: ctx)
        }
        compositor.draw(into: ctx)
        magic.draw(into: ctx)
        return ctx.makeImage().map { NSImage(cgImage: $0, size: size) } ?? NSImage(cgImage: image, size: size)
    }

    public func destroy() {
        guard !destroyed else {
            return
        }
        destroyed = true
        smoothScrolls.removeAll()
        pendingScrollRequests.removeAll()
        pointerCaptureId = 0
        pressId = 0
        form.rangeId = 0
        endEditing()
        magic.cancel()
        compositor.reset()
        svgLayers.reset()
        shimmers.reset()
        scrolling.reset()
        animator.reset()
        forgetCanvasImages()
        canvasURLs.removeAll()
        retiredURLs.removeAll()
        for presenter in presenters.values {
            presenter.remove()
        }
        presenters.removeAll()
        canvases.removeAll()
        canvasAttributeSizes.removeAll()
        canvasDraws.removeAll()
        presentationChecked = nil
        for token in placedSurfaces.keys {
            surfaceLayout?(token, .zero, 1, false)
        }
        placedSurfaces.removeAll()
        surfaceContainer?.twRemoveFromSuperlayer()
        surfaceContainer = nil
        clock?.stop()
        clock = nil
        trimWork?.cancel()
        trimWork = nil
        documentView.owner = nil
        let detachedView = documentView
        if Thread.isMainThread {
            detachedView.removeFromSuperview()
        } else {
            DispatchQueue.main.async {
                detachedView.removeFromSuperview()
            }
        }
        var retired: [AnyObject] = documentView.retire()
        if let document = document {
            retired.append(document)
        }
        if let displayList = displayList {
            retired.append(displayList)
        }
        document = nil
        taskResizeBase = nil
        displayList = nil
        dom.reset()
        held.removeAll()
        buffered.removeAll()
        pendingMoves.removeAll()
        bounds.removeAll()
        boundsValid = false
        scannedDeclarations = (0, [:])
        overrides.removeAll()
        webUnderlying.removeAll()
        lastLayoutOverrides = nil
        lastSerialized = nil
        styleSheetKey = ""
        form = WidgetFormState()
        onMessage = nil
        onDiag = nil
        onSerialized = nil
        surfaceLayer = nil
        surfaceLayout = nil
        WidgetTeardown.release(retired)
    }

    static let messageLog = ProcessInfo.processInfo.environment["TW_MSG_LOG"] != nil

    private enum Held {
        case json(String)
        case ops([WidgetOp])
    }

    private var held: [Held] = []
    private var throttledUntil: CFTimeInterval = 0
    private var throttleFlushScheduled = false
    private var lastThrottleDiag: CFTimeInterval = -.infinity
    public private(set) var throttledUpdates = 0
    static let fixedThrottleFactor = ProcessInfo.processInfo.environment["TW_THROTTLE_FACTOR"].flatMap { Double($0) }
    static let throttleFactor = 3.0
    static let sustainedThrottleFactor = 19.0
    static let sustainedWindow: CFTimeInterval = 2
    static let sustainedLimit: CFTimeInterval = 0.1
    static let heavyPassSeconds: CFTimeInterval = 0.05
    static let loadGraceSeconds: CFTimeInterval = 1
    static let throttleFreeSeconds: CFTimeInterval = 0.1
    static let maxThrottleDelay: CFTimeInterval = 0.1
    private let throttleLock = NSLock()
    private var chargeWindow: [(at: CFTimeInterval, spent: CFTimeInterval)] = []
    private var firstChargeAt: CFTimeInterval?
    public private(set) var escalations = 0

    private var meterDepth = 0

    private func metered<T>(_ body: () -> T) -> T {
        meterDepth += 1
        let cpuStart = LiteHTMLWidgetRenderer.threadCPUSeconds()
        defer {
            meterDepth -= 1
            if meterDepth == 0 {
                let spent = LiteHTMLWidgetRenderer.threadCPUSeconds() - cpuStart
                charge(spent)
                passMaxCPUMilliseconds = max(passMaxCPUMilliseconds, spent * 1000)
            }
        }
        return body()
    }

    public func chargeTileDraw(_ spent: CFTimeInterval) {
        charge(spent, tile: true)
    }

    func charge(_ spent: CFTimeInterval, tile: Bool = false) {
        guard spent > 0 else {
            return
        }
        let now = CACurrentMediaTime()
        throttleLock.lock()
        defer { throttleLock.unlock() }
        let first = firstChargeAt ?? now
        firstChargeAt = first
        if now - first < LiteHTMLWidgetRenderer.loadGraceSeconds {
            return
        }
        chargeWindow.append((now, spent))
        while let oldest = chargeWindow.first, now - oldest.at > LiteHTMLWidgetRenderer.sustainedWindow {
            chargeWindow.removeFirst()
        }
        var factor: Double
        if let fixed = LiteHTMLWidgetRenderer.fixedThrottleFactor {
            guard spent > 0.004 else {
                return
            }
            factor = fixed
        } else {
            let sustained = chargeWindow.reduce(0, { $0 + $1.spent }) > LiteHTMLWidgetRenderer.sustainedLimit * LiteHTMLWidgetRenderer.sustainedWindow
            let heavy = chargeWindow.filter({ $0.spent > LiteHTMLWidgetRenderer.heavyPassSeconds }).count >= 2
            guard !tile, spent > LiteHTMLWidgetRenderer.throttleFreeSeconds else {
                return
            }
            if sustained && heavy {
                factor = LiteHTMLWidgetRenderer.sustainedThrottleFactor
                escalations += 1
            } else {
                factor = LiteHTMLWidgetRenderer.throttleFactor
            }
        }
        guard factor > 0 else {
            return
        }
        throttledUntil = min(now + LiteHTMLWidgetRenderer.maxThrottleDelay, max(throttledUntil, now) + spent * factor)
    }

    private func throttleRemaining(_ now: CFTimeInterval) -> CFTimeInterval {
        throttleLock.lock()
        defer { throttleLock.unlock() }
        return max(0, throttledUntil - now)
    }

    private var throttled: Bool {
        return throttleRemaining(CACurrentMediaTime()) > 0
    }

    private func holdThrottled(_ item: Held) {
        held.append(item)
        throttledUpdates += 1
        let now = CACurrentMediaTime()
        if now - lastThrottleDiag > 1 {
            lastThrottleDiag = now
            onDiag?("renderer-throttled", String(format: "widget updates cost %.0f ms of rendering ahead of budget; coalescing them", self.throttleRemaining(now) * 1000))
        }
        guard !throttleFlushScheduled else {
            return
        }
        throttleFlushScheduled = true
        scheduleAfter(throttleRemaining(now)) { [weak self] in
            guard let self = self else {
                return
            }
            self.throttleFlushScheduled = false
            self.prepared()
        }
    }
    private var prepareScheduled = false

    private var preparesOffscreenValue = false

    public var preparesOffscreen: Bool {
        get {
            return preparesOffscreenValue
        }
        set {
            guard isOnExecutor() else {
                return scheduleUrgent { [weak self] in self?.preparesOffscreen = newValue }
            }
            if preparesOffscreenValue != newValue {
                preparesOffscreenValue = newValue
                updateTileTrim()
                schedulePrepare()
            }
        }
    }

    public static var preparedReport: String {
        return WidgetDocumentView.preparedReport
    }

    public var preparedTileCount: Int {
        return documentView.preparedTiles
    }

    public var preparedMilliseconds: Double {
        return documentView.preparedMilliseconds
    }

    public static var colorSpaceChanges: Int {
        return WidgetCompositor.totalSpaceChanges
    }

    public static var totalPromotions: Int {
        return WidgetCompositor.totalPromotions
    }

    public static var tileSpareReport: String {
        return "spares granted=\(WidgetTileSpares.granted) reused=\(WidgetTileSpares.reused) bytes=\(WidgetTileSpares.currentBytes >> 10)KB peak=\(WidgetTileSpares.peakBytes >> 10)KB"
    }

    public static func quiesceRaster() {
        WidgetDocumentView.prepareQueue.sync(flags: .barrier) {}
    }

    public var tileDebug: String {
        return "detached=\(documentView.detached) preparing=\(documentView.preparing) held=\(held.count) throttle-ms=\(Int(throttleRemaining(CACurrentMediaTime()) * 1000)) tiles=\(documentView.tileCount) trimmed=\(documentView.tilesTrimmed) \(documentView.flushDebug) doc=\(document != nil) h=\(document.map { Int($0.size.height) } ?? -1) inWindow=\(documentView.windowState.inWindow) onScreen=\(isOnScreen) hostVisible=\(hostVisible) frozen=\(frozen) destroyed=\(destroyed)"
    }

    public var isPrepared: Bool {
        return document != nil && !documentView.preparing && !documentView.hasUnpreparedTiles
    }

    private func schedulePrepare() {
        guard preparesOffscreen, !destroyed, !prepareScheduled, document != nil, !documentView.windowState.inWindow else {
            return
        }
        prepareScheduled = true
        schedule { [weak self] in
            guard let self = self else {
                return
            }
            self.prepareScheduled = false
            guard self.preparesOffscreen, !self.destroyed, !self.documentView.windowState.inWindow else {
                return
            }
            _ = self.documentView.prepareTiles { [weak self] in
                self?.rasterFinished()
            }
        }
    }

    func rasterFinished() {
        if documentView.detached {
            reportSize()
        }
        prepared()
        if renderDeferred {
            renderDeferred = false
            render()
        }
    }

    private var renderDeferred = false

    public var asyncTileReport: (jobs: Int, installed: Int) {
        return (documentView.asyncTileJobs, documentView.asyncTilesInstalled)
    }

    private var flushingHeld = false

    public static var slowApply: ((_ ops: Int, _ milliseconds: Double, _ held: Bool, _ stages: [String: Double]) -> Void)?

    private func applyMeasured(_ ops: [WidgetOp], held: Bool) {
        metered { applyMeasuredBody(ops, held: held) }
        if inputAwaitingAnswer && !documentView.preparing && (!held || self.held.isEmpty) {
            inputAwaitingAnswer = false
        }
        if frameHoldPosted != nil {
            frameHoldAnswers -= 1
            if frameHoldAnswers <= 0 {
                releaseFrameHold()
            }
        }
    }

    private func applyMeasuredBody(_ ops: [WidgetOp], held: Bool) {
        guard let report = LiteHTMLWidgetRenderer.slowApply else {
            return apply(ops)
        }
        let before = stageMilliseconds
        let start = CACurrentMediaTime()
        apply(ops)
        let spent = (CACurrentMediaTime() - start) * 1000
        if spent > 8 {
            var stages: [String: Double] = [:]
            for (key, value) in stageMilliseconds where value - (before[key] ?? 0) > 0.5 {
                stages[key] = value - (before[key] ?? 0)
            }
            report(ops.count, spent, held, stages)
        }
    }

    private func prepared() {
        guard !documentView.preparing, !destroyed else {
            return
        }
        let start = CACurrentMediaTime()
        flushingHeld = true
        let answering = answersInput || answeringQuery
        while !held.isEmpty && !documentView.preparing && (answering || !throttled) {
            var ops: [WidgetOp] = []
            while case .ops(let batch)? = held.first {
                ops += batch
                held.removeFirst()
            }
            if !ops.isEmpty {
                deferringRender = LiteHTMLWidgetRenderer.coalescesHeld && !answering
                applyMeasured(ops, held: true)
                deferringRender = false
            } else if case .json(let json)? = held.first {
                held.removeFirst()
                send(json)
            }
            if !answering && CACurrentMediaTime() - start > 0.004 {
                break
            }
        }
        flushingHeld = false
        if !held.isEmpty && !documentView.preparing {
            if throttled {
                if !throttleFlushScheduled {
                    throttleFlushScheduled = true
                    scheduleAfter(throttleRemaining(CACurrentMediaTime())) { [weak self] in
                        self?.throttleFlushScheduled = false
                        self?.prepared()
                    }
                }
            } else {
                schedule { [weak self] in
                    self?.prepared()
                }
            }
        }
        schedulePrepare()
    }

    private var answeringQuery = false
    private(set) var taskDepth = 0
    private var taskNeedsRender = false
    private var taskResizeBase: (document: LHDocument, bounds: [Int: CGRect])?

    public func beginTask() {
        send("{\"type\":\"beginTask\"}")
    }

    public func endTask() {
        send("{\"type\":\"endTask\"}")
    }

    public func send(_ json: String) {
        if !destroyed, !flushingHeld, !documentView.preparing, !held.isEmpty, json.hasPrefix("{\"type\":\"query\"") {
            held.append(.json(json))
            answeringQuery = true
            prepared()
            answeringQuery = false
            return
        }
        if !destroyed, !flushingHeld, !documentView.preparing, !held.isEmpty, answersInput {
            held.append(.json(json))
            prepared()
            return
        }
        if json == "{\"type\":\"requestFrame\"}", !destroyed, !frozen {
            if !frameRequested {
                frameRequested = true
                holdForFrameIfAnswering()
                startClock()
            }
            return
        }
        if (documentView.preparing || (!held.isEmpty && !flushingHeld)) && !destroyed {
            if throttled && json.hasPrefix("{\"type\":\"ops\"") {
                throttledUpdates += 1
            }
            held.append(.json(json))
            return
        }
        if !destroyed, !flushingHeld, !answersInput, throttled, json.hasPrefix("{\"type\":\"ops\"") {
            if json.utf8.count <= 32768,
               let data = json.data(using: .utf8),
               let message = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
               let values = message["ops"] as? [[String: Any]],
               applyCompositedWhileThrottled(values.map(WidgetOp.init)) {
                return
            }
            holdThrottled(.json(json))
            return
        }
        if !destroyed, LiteHTMLWidgetRenderer.canvasFastPath, json.hasPrefix("{\"type\":\"ops\",\"ops\":[{\"op\":\"canvas\""), let list = CanvasStream.canvasOps(json: json) {
            sendCanvas(list)
            return
        }
        guard !destroyed, let data = json.data(using: .utf8), let message = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any], let type = message["type"] as? String else {
            return
        }
        if LiteHTMLWidgetRenderer.messageLog, type != "ops" {
            print(String(format: "MSG %.4f %@", CACurrentMediaTime(), type))
        }
        switch type {
        case "beginTask":
            taskDepth += 1
        case "endTask":
            guard taskDepth > 0 else { return }
            taskDepth -= 1
            if taskDepth == 0, taskNeedsRender || needsRender || renderAtTick || silentPending {
                taskNeedsRender = false
                silentPending = false
                if !frozen {
                    render()
                } else {
                    needsRender = false
                    renderAtTick = false
                    taskResizeBase = nil
                }
            }
        case "ops":
            let ops = ((message["ops"] as? [Any]) ?? []).compactMap { ($0 as? [String: Any]).map(WidgetOp.init) }
            if !flushingHeld, !answersInput, throttled {
                if !applyCompositedWhileThrottled(ops) { holdThrottled(.ops(ops)) }
                return
            }
            applyMeasured(ops, held: false)
        case "query":
            let qid = message["qid"] ?? NSNull()
            let q = (message["query"] as? [String: Any]) ?? [:]
            if let binary = binaryQuery(q) {
                post(["type": "result", "qid": qid, "result": binary.result, "binaryBase64": binary.data.base64EncodedString()])
            } else {
                post(["type": "result", "qid": qid, "result": query(q)])
            }
        case "listen":
            listened = Set((message["types"] as? [String]) ?? [])
        case "preventDefault":
            prevented = Set((message["keys"] as? [String]) ?? [])
        case "requestFrame":
            if frozen || frameRequested {
                return
            }
            frameRequested = true
            holdForFrameIfAnswering()
            startClock()
        case "frozen":
            freeze(reason: (message["reason"] as? String) ?? "", fatal: (message["fatal"] as? Bool) ?? false)
        case "theme":
            let variables = WidgetThemeStyle.variables(message["theme"])
            let css = WidgetThemeStyle.masterCSS(variables)
            let restyled = css != masterCSS
            masterCSS = css
            if dom.setRootVariables(variables) || restyled {
                setNeedsRender()
            }
        case "anim":
            if silentPending {
                opsSinceSilent = true
            }
            animator.web.apply(message)
            for item in (message["anims"] as? [[String: Any]]) ?? [] where item["css"] != nil {
                animator.applyCSSOverride(item, offset: hiddenTotal)
            }
            if !frozen {
                render()
            }
        default:
            break
        }
    }

    public var acceptsTypedOps: Bool {
        return true
    }

    public func send(ops: [WidgetOp]) {
        guard !destroyed else {
            return
        }
        if !documentView.preparing, !held.isEmpty, answersInput {
            held.append(.ops(ops))
            prepared()
            return
        }
        if documentView.preparing || !held.isEmpty {
            if throttled {
                throttledUpdates += 1
            }
            held.append(.ops(ops))
            return
        }
        if throttled && !answersInput {
            if !applyCompositedWhileThrottled(ops) {
                holdThrottled(.ops(ops))
            }
            return
        }
        applyMeasured(ops, held: false)
    }

    static let presamples = ProcessInfo.processInfo.environment["TW_NO_PRESAMPLE"] == nil
    static let declarationGates = WidgetAnimator.entryProperties + ["transform", "opacity", "backdrop-filter", "filter", "box-shadow"]
    static let coalescesHeld = ProcessInfo.processInfo.environment["TW_HELD_RENDER_NOW"] == nil
    private var deferringRender = false

    static let coalescesFrames = ProcessInfo.processInfo.environment["TW_OPS_RENDER_NOW"] == nil
    private var renderAtTick = false
    public private(set) var tickRenders = 0
    static var silentPresentDeadline: TimeInterval = 1.0 / 60
    private var silentRendering = false
    private var silentPending = false
    private var opsSinceSilent = false
    private var silentEpoch = 0
    private var silentDebt: [CGRect] = []

    private var holdsSilentState: Bool {
        return silentPending && !opsSinceSilent && !destroyed && !frozen
    }

    private func renderForQuery() {
        if frozen || destroyed {
            render()
            return
        }
        needsRender = false
        renderAtTick = false
        let before = document
        if taskDepth > 0, taskResizeBase == nil, let document = document {
            taskResizeBase = (document, elementBounds())
        }
        silentRendering = true
        metered { renderBody() }
        silentRendering = false
        guard document !== before || silentPending else {
            return
        }
        silentPending = true
        opsSinceSilent = false
        silentEpoch += 1
        let epoch = silentEpoch
        scheduleAfter(LiteHTMLWidgetRenderer.silentPresentDeadline) { [weak self] in
            guard let self = self, self.silentPending, self.silentEpoch == epoch else {
                return
            }
            self.silentPending = false
            if !self.destroyed && !self.frozen {
                self.render()
            }
        }
    }

    private func releaseTickRender() {
        if renderAtTick {
            renderAtTick = false
            setNeedsRender()
        }
    }

    private func renderOrDefer() {
        if deferringRender || (!pendingGeometry && !answersInput) {
            setNeedsRender()
        } else if rendersAtTick {
            renderAtTick = true
        } else {
            render()
        }
    }

    private var pendingGeometry = true

    static let visualProperties: Set<String> = [
        "transform", "transform-origin", "opacity", "color", "background", "background-color",
        "background-image", "background-position", "background-repeat", "box-shadow", "text-shadow",
        "filter", "outline", "outline-color", "outline-width", "outline-style", "outline-offset",
        "border-color", "border-top-color", "border-right-color", "border-bottom-color",
        "border-left-color", "text-decoration-color", "caret-color", "cursor"
    ]

    private func notePendingGeometry(_ ops: [WidgetOp]) {
        if pendingGeometry {
            return
        }
        if !ops.isEmpty, dom.styleSheets().contains(where: {
            $0.range(of: "\\[\\s*style(?:\\s|[~|^$*]?=|\\])", options: [.regularExpression, .caseInsensitive]) != nil
        }) {
            pendingGeometry = true
            return
        }
        for op in ops where !(op.kind == .style && LiteHTMLWidgetRenderer.visualProperties.contains(op.name.lowercased())) {
            pendingGeometry = true
            return
        }
    }

    private func apply(_ ops: [WidgetOp]) {
        let outer = applyingOps
        applyingOps = true
        defer {
            applyingOps = outer
            pacer.answered(CACurrentMediaTime())
        }
        if silentPending && !ops.isEmpty {
            opsSinceSilent = true
        }
        if !frozen, !pendingGeometry, !needsRender, !renderAtTick, pendingScrollRequests.isEmpty, document != nil, !ops.isEmpty,
           ops.allSatisfy({ op in
               guard op.kind == .other else { return false }
               if op.op == "prop", let name = op["name"] as? String {
                   return name == "scrollTop" || name == "scrollLeft"
               }
               if op.op == "call", let method = op["method"] as? String {
                   return method == "scrollTo" || method == "scrollBy"
               }
               return false
           }) {
            for op in ops { dom.apply(op) }
            stageMilliseconds["scroll-fast", default: 0] += 1
            return
        }
        let ops = WidgetCanvasOrder.ordered(ops)
        notePendingGeometry(ops)
        if LiteHTMLWidgetRenderer.canvasFastPath, !ops.isEmpty, ops.allSatisfy({ $0.kind == .canvas && $0.canvas != nil }) {
            sendCanvas(ops.map { ($0.id, CanvasStream(batch: $0.canvas!)) })
            return
        }
        if LiteHTMLWidgetRenderer.messageLog {
            print(String(format: "MSG %.4f ops %@", CACurrentMediaTime(), ops.map { "\($0.op ?? "?"):\($0.kind == .other ? ($0["name"] as? String) ?? "" : $0.name)" }.joined(separator: ",")))
        }
        if !frozen, let touched = scriptedOnly(ops) {
            for op in ops {
                dom.apply(op)
            }
            animator.recordScriptedValues(ops)
            stageMilliseconds["scripted-fast", default: 0] += 1
            if compositor.applyScripted(touched, dom: dom, document: document) {
                if !animator.isActive {
                    compositeCheck([:])
                }
                return
            }
            renderOrDefer()
            return
        }
        noteScriptedTransforms(ops)
        var capture: WidgetMagicMove.Capture?
        if dom.usesTransitions, let document = document, !frozen, isOnScreen, WidgetMagicMove.isStructural(ops, dom: dom) {
            capture = magic.capture(dom: dom, document: document)
        }
        for op in ops {
            dom.apply(op)
        }
        if var pending = capture, let document = document {
            magic.collectLeaving(&pending, dom: dom, document: document, scale: documentView.scale)
            capture = pending
        }
        sizeTransition = capture != nil
        if capture == nil {
            renderOrDefer()
        } else {
            render()
        }
        sizeTransition = false
        if let capture = capture, let document = document, magic.run(capture, dom: dom, document: document, skip: animator.animatedIDs().union(compositor.ids), scale: documentView.scale) {
            applyExclusions()
        }
    }

    func post(_ message: [String: Any]) {
        if let json = WidgetJSONWriter.encode(message) {
            deliver(json)
            return
        }
        guard JSONSerialization.isValidJSONObject(message), let data = try? JSONSerialization.data(withJSONObject: message), let json = String(data: data, encoding: .utf8) else {
            return
        }
        deliver(json)
    }

    func postFrame(time: Double) {
        if let json = WidgetJSONWriter.frame(time: time) {
            deliver(json)
        }
    }

    func postEvent(_ event: String) {
        deliver("{\"type\":\"event\",\"trusted\":true,\"ev\":" + event + "}")
    }

    private func deliver(_ json: String) {
        guard !destroyed else {
            return
        }
        if let onMessage = onMessage {
            onMessage(json)
        } else {
            buffered.append(json)
        }
    }

    private func flushBuffered() {
        guard let onMessage = onMessage, !buffered.isEmpty else {
            return
        }
        let queued = buffered
        buffered = []
        queued.forEach(onMessage)
    }

    static var inputImmediateWindow: CFTimeInterval = 0.25
    private var lastInputAt: CFTimeInterval = -1

    var heldOpBatches: Int {
        return held.count
    }

    var isPreparingTiles: Bool {
        return documentView.preparing
    }

    private var inputAwaitingAnswer = false

    var answersInput: Bool {
        return inputAwaitingAnswer || CACurrentMediaTime() - lastInputAt <= LiteHTMLWidgetRenderer.inputImmediateWindow
    }

    func noteUserInput() {
        lastInputAt = CACurrentMediaTime()
        inputAwaitingAnswer = true
        if frameRequested {
            holdForFrameIfAnswering()
        }
    }

    var rendersAtTick: Bool {
        return LiteHTMLWidgetRenderer.coalescesFrames && !frozen && clock?.isRunning == true && needsFrames && isOnScreen && !answersInput
    }

    private var applyingOps = false

    func setNeedsRender() {
        if !applyingOps {
            pendingGeometry = true
        }
        if needsRender {
            return
        }
        if rendersAtTick {
            renderAtTick = true
            return
        }
        needsRender = true
        schedule { [weak self] in
            guard let self = self, self.needsRender else {
                return
            }
            self.render()
        }
    }

    private var width: CGFloat {
        return max(1, documentView.size.width)
    }

    private static let resolvedSheetsLock = NSLock()
    private static var resolvedSheets: [String: String] = [:]

    private static func resolvedSheet(_ css: String, theme: String) -> String {
        let key = theme + "\u{1}" + css
        resolvedSheetsLock.lock()
        if let resolved = resolvedSheets[key] {
            resolvedSheetsLock.unlock()
            return resolved
        }
        resolvedSheetsLock.unlock()
        let resolved = CSSTheme.resolve(css, theme: theme)
        resolvedSheetsLock.lock()
        if resolvedSheets.count >= 256 {
            resolvedSheets.removeAll()
        }
        resolvedSheets[key] = resolved
        resolvedSheetsLock.unlock()
        return resolved
    }

    private func holdForFrameIfAnswering() {
        let now = CACurrentMediaTime()
        guard frameHoldStart == nil, answersInput, isOnScreen else {
            return
        }
        frameHoldStart = now
        frameHoldPosted = nil
        scheduleAfter(LiteHTMLWidgetRenderer.frameHoldLimit) { [weak self] in
            if self?.frameHoldStart == now {
                self?.releaseFrameHold()
            }
        }
    }

    private func releaseFrameHold() {
        guard frameHoldStart != nil else {
            return
        }
        frameHoldStart = nil
        frameHoldPosted = nil
        frameHoldAnswers = 0
        if frameHoldRender {
            frameHoldRender = false
            if !destroyed && !frozen {
                render()
            }
        }
    }

    var holdsForFrame: Bool {
        return frameHoldStart != nil
    }

    var animatedElementIDs: Set<Int> {
        return animator.animatedIDs()
    }

    var presentedDocument: LHDocument? {
        return documentView.document
    }

    var compositedElementIDs: Set<Int> {
        return compositor.ids
    }

    func animationEnd(_ id: Int) -> CFTimeInterval? {
        return animator.endTime(id)
    }

    func animationValues(_ id: Int) -> (transform: String?, opacity: String?)? {
        return animator.peek(id, at: animationNow)
    }

    private func render() {
        guard !destroyed else {
            return
        }
        if taskDepth > 0 {
            taskNeedsRender = true
            needsRender = true
            renderAtTick = false
            return
        }
        if frameHoldStart != nil && !destroyed && !frozen {
            frameHoldRender = true
            needsRender = false
            renderAtTick = false
            return
        }
        if holdsSilentState {
            needsRender = false
            renderAtTick = false
            return
        }
        silentPending = false
        renderAtTick = false
        metered { renderBody() }
        if form.focusedId != 0 {
            syncEditor()
        }
    }

    private func unpromoted(_ overrides: [Int: [(String, String)]]) -> [Int: [(String, String)]] {
        let promoted = compositor.ids
        return promoted.isEmpty ? overrides : overrides.filter { !promoted.contains($0.key) }
    }

    private func renderBody() {
        needsRender = false
        if documentView.preparing && documentView.windowState.inWindow {
            renderDeferred = true
            return
        }
        documentView.beginUpdates()
        defer { documentView.endUpdates() }
        pendingGeometry = false
        updateAnimationClock()
        let now = animationNow
        let sheetsStart = CACurrentMediaTime()
        let sheets = dom.styleSheets()
        let key = sheets.joined(separator: "\u{1}")
        if key != styleSheetKey {
            styleSheetKey = key
            animator.setStyleSheets(sheets.map { LiteHTMLWidgetRenderer.resolvedSheet($0, theme: theme) })
        }
        stageMilliseconds["sheets", default: 0] += (CACurrentMediaTime() - sheetsStart) * 1000
        compositor.styleKey = (key + "\u{2}" + theme + "\u{2}\(hoverId)\u{2}\(pressId)").hashValue
        if LiteHTMLWidgetRenderer.presamples, animator.isActive, !frozen {
            if silentRendering {
                overrides = unpromoted(animator.sample(now: now))
            } else {
                var presampled = compositor.apply(animator.sample(now: now), bases: { [animator] in animator.base($0) })
                shimmers.apply(&presampled)
                overrides = presampled
            }
        }
        probing = true
        layout(overrides)
        probing = false
        defer { finishProbe() }
        let scanStart = CACurrentMediaTime()
        var generation: UInt64 = 0
        let scanned = document?.declarations(WidgetAnimator.watched, byAttribute: WidgetDOM.idAttribute, requiringAny: LiteHTMLWidgetRenderer.declarationGates, changedSince: scannedDeclarations.generation, generation: &generation)
        if let scanned = scanned {
            var converted: [Int: [String: String]] = [:]
            for (key, values) in scanned {
                if let id = Int(key) {
                    converted[id] = values
                }
            }
            scannedDeclarations = (generation, converted)
        } else if document == nil {
            scannedDeclarations = (0, [:])
        }
        var declarations = scannedDeclarations.values
        if !animator.web.isEmpty, let document = document {
            webUnderlying.removeAll()
            let targets = animator.web.targets
            var names = Set(WidgetAnimator.watched)
            var required = Set<String>()
            for id in targets {
                for animation in animator.web.animations(id) {
                    required.formUnion(animation.properties)
                }
            }
            required.insert("transform-origin")
            names.formUnion(required)
            for (key, values) in document.declarations(Array(names), byAttribute: WidgetDOM.idAttribute, requiringAny: Array(required)) {
                guard let id = Int(key), targets.contains(id) else {
                    continue
                }
                let plain = values.filter { !$0.key.hasPrefix("animation") && !$0.key.hasPrefix("transition") }
                declarations[id] = (declarations[id] ?? [:]).merging(plain) { current, _ in current }
                webUnderlying[id] = values
            }
            for id in targets where declarations[id] == nil && dom.node(id) != nil {
                declarations[id] = [:]
            }
        }
        stageMilliseconds["declarations", default: 0] += (CACurrentMediaTime() - scanStart) * 1000
        animator.update(declarations: declarations, now: now)
        let sampled = animator.sample(now: now)
        if silentRendering {
            let rest = unpromoted(sampled)
            if !LiteHTMLWidgetRenderer.same(rest, overrides) {
                overrides = rest
                layout(overrides)
            }
            return
        }
        let before = compositor.ids
        let svgAdded = svgLayers.observe(dom.takeSVGTouched())
        var scrollersChanged = svgAdded
        if let document = document {
            compositor.attach(documentView.contentLayer)
            let scrollers = document.scrollers(byAttribute: WidgetDOM.idAttribute)
            compositor.update(document: document, animator: animator, dom: dom, declarations: declarations, scale: documentView.scale, space: documentView.windowState.colorSpace ?? WidgetDocumentView.lastWindowSpace ?? CGColorSpace(name: CGColorSpace.sRGB)!, extraExclusions: magic.excluded, blockedIDs: scrolling.compositorBlockedIDs(dom: dom, scrollers: scrollers))
            scrolling.attach(documentView.contentLayer)
            scrollersChanged = scrolling.update(document: document, dom: dom, animated: animator.animatedIDs(), promoted: compositor.ids.union(magic.excluded), styleKey: compositor.styleKey, scale: documentView.scale, scrollers: scrollers) || svgAdded
            publishScrollRegions()
            statsLock.lock()
            publishedComposited = compositor.ids.count
            statsLock.unlock()
        }
        compositor.drive(animator: animator, now: now, mediaOffset: hiddenTotal, enabled: canDrive)
        var rest = compositor.apply(sampled, bases: { [animator] in animator.base($0) })
        shimmers.apply(&rest)
        _ = compositor.applyScripted(compositor.scriptedIDs, dom: dom, document: document)
        if !LiteHTMLWidgetRenderer.same(rest, overrides) || before != compositor.ids || scrollersChanged {
            overrides = rest
            layout(overrides)
        }
        applyPendingScrollRequests()
        if WidgetScrollLayer.skipsObsoleteRaster, let document = document {
            scrolling.refresh(document: document, scale: documentView.scale)
        }
        scheduleDrivenEnds(now: now)
        updateCanvasPresentation()
        if needsFrames && !frozen {
            startClock()
        }
    }

    static let canvasFastPath = ProcessInfo.processInfo.environment["TW_CANVAS_FAST"] != "0"
    static let canvasGPUAfter = ProcessInfo.processInfo.environment["TW_CANVAS_GPU_AFTER"].flatMap { Int($0) }.map { max(1, $0) } ?? 3
    static let maxPresentedPixels = 2048 * 2048 * 2
    private var presenters: [Int: WidgetCanvasPresenter] = [:]
    private var canvasDraws: [Int: Int] = [:]
    private var presentationEpoch = 0
    private var presentationBackoff = 0
    private var presentationChecked: LHDocument?
    public private(set) var canvasFastMessages = 0

    public var svgCheckReport: String {
        return svgLayers.report
    }

    public static var checkTotals: String {
        return WidgetSVGLayers.totals
    }

    public var canvasReport: String {
        var accelerated = 0, fallback = 0, gpuFrames = 0, gpuMilliseconds = 0.0
        #if canImport(Metal)
        for canvas in canvases.values {
            if let gpu = canvas.backend as? WidgetCanvasMetal {
                accelerated += gpu.acceleratedBatches
                fallback += gpu.fallbackBatches
                let stats = gpu.gpuStats
                gpuFrames += stats.frames
                gpuMilliseconds += stats.milliseconds
            }
        }
        #endif
        let shown = presenters.values.reduce(0) { $0 + $1.presented }
        let skipped = presenters.values.reduce(0) { $0 + $1.skipped }
        return String(format: "canvas: fast=%d gpu-batches=%d cg-batches=%d gpu-buffers=%d gpu-ms=%.3f/buffer presented=%d layers=%d skipped=%d", canvasFastMessages, accelerated, fallback, gpuFrames, gpuMilliseconds / Double(max(1, gpuFrames)), shown, presenters.count, skipped)
    }

    private func sendCanvas(_ list: [(id: Int, stream: CanvasStream)]) {
        canvasFastMessages += 1
        var direct = true
        for (id, stream) in list {
            guard let node = dom.canvasNode(id) else {
                continue
            }
            let target = canvas(for: node)
            let size = (target.width, target.height)
            target.execute(stream)
            if presenters[id] == nil || size != (target.width, target.height) {
                direct = false
            }
        }
        if direct {
            stageMilliseconds["canvas-direct", default: 0] += 1
            return
        }
        render()
    }

    private func canvasDrew(_ id: Int) {
        let count = (canvasDraws[id] ?? 0) + 1
        canvasDraws[id] = count
        if let presenter = presenters[id] {
            presenter.present()
            return
        }
        #if canImport(Metal)
        if count + 1 >= LiteHTMLWidgetRenderer.canvasGPUAfter, let canvas = canvases[id], canvas.backend == nil, canvas.storage != nil, WidgetCanvasMetal.enabled {
            canvas.backend = WidgetCanvasMetal()
        }
        #endif
    }

    private func canvasPresentable(_ id: Int, _ canvas: WidgetCanvas, document: LHDocument, boxes: [Int: CGRect]) -> (frame: CGRect, clip: (rect: CGRect, radius: CGFloat, corners: CACornerMask)?)? {
        guard WidgetCanvasPresenter.enabled, canvas.storage != nil, canvas.width > 0, canvas.height > 0, !magic.isActive, let node = dom.node(id), node.tag == "canvas", !node.svg else {
            return nil
        }
        let key = String(id)
        guard let box = boxes[id], box.width >= 1, box.height >= 1, box.width * box.height < 4_000_000 else {
            return nil
        }
        for other in compositor.ids where other == id || isAncestor(other, of: id) {
            return nil
        }
        let style = document.computedStyle(attribute: WidgetDOM.idAttribute, value: key)
        for name in ["padding-top", "padding-right", "padding-bottom", "padding-left", "border-top-width", "border-right-width", "border-bottom-width", "border-left-width"] {
            guard let value = style[name], value == "0px" else {
                return nil
            }
        }
        if let opacity = style["opacity"], let number = Double(opacity), number < 0.999 {
            return nil
        }
        if let visibility = style["visibility"], visibility != "visible" {
            return nil
        }
        let declared = document.declarations(["transform", "filter", "border-radius", "clip-path", "mix-blend-mode", "object-fit", "object-position", "image-rendering", "mask", "-webkit-mask", "backdrop-filter", "outline-style"], byAttribute: WidgetDOM.idAttribute)[key] ?? [:]
        for (name, value) in declared {
            let trimmed = value.trimmingCharacters(in: .whitespaces).lowercased()
            if trimmed.isEmpty || trimmed == "none" || trimmed == "normal" || trimmed == "initial" || trimmed == "fill" || (name == "border-radius" && (trimmed == "0" || trimmed == "0px")) || (name == "image-rendering" && trimmed == "auto") {
                continue
            }
            return nil
        }
        var clipRect = CGRect.zero
        var radii = [CGFloat](repeating: 0, count: 8)
        var clip: (rect: CGRect, radius: CGFloat, corners: CACornerMask)?
        if document.clipOfElement(attribute: WidgetDOM.idAttribute, value: key, rect: &clipRect, radii: &radii) {
            let rounded = radii.filter { $0 > 0.01 }
            guard clipRect.intersects(box), rounded.allSatisfy({ abs($0 - rounded[0]) < 0.01 }) else {
                return nil
            }
            var corners: CACornerMask = []
            let masks: [CACornerMask] = [.layerMinXMinYCorner, .layerMaxXMinYCorner, .layerMaxXMaxYCorner, .layerMinXMaxYCorner]
            for (index, mask) in masks.enumerated() {
                let x = radii[index * 2], y = radii[index * 2 + 1]
                guard (x > 0.01) == (y > 0.01) else {
                    return nil
                }
                if x > 0.01 {
                    corners.insert(mask)
                }
            }
            if !rounded.isEmpty || !clipRect.contains(box) {
                clip = (clipRect, rounded.first ?? 0, corners)
            }
        }
        let visible = clip.map { box.intersection($0.rect) } ?? box
        guard !visible.isNull, !visible.isEmpty, document.isElement(attribute: WidgetDOM.idAttribute, value: key, unobscuredIn: visible, ignoring: [], clipped: clip != nil || !clipRect.isEmpty) else {
            return nil
        }
        return (box, clip)
    }

    private func updateCanvasPresentation() {
        guard let document = document, !presenters.isEmpty || canvasDraws.values.contains(where: { $0 >= LiteHTMLWidgetRenderer.canvasGPUAfter }) else {
            return
        }
        if presentationChecked === document {
            return
        }
        if presenters.isEmpty && presentationBackoff > 0 {
            presentationBackoff -= 1
            return
        }
        presentationChecked = document
        let boxes = document.idBounds()
        var changed = false
        var added: [WidgetCanvasPresenter] = []
        var budget = LiteHTMLWidgetRenderer.maxPresentedPixels
        for (id, canvas) in canvases.sorted(by: { $0.key < $1.key }) {
            let wanted = presenters[id] != nil || (canvasDraws[id] ?? 0) >= LiteHTMLWidgetRenderer.canvasGPUAfter
            let affordable = canvas.pixelCount <= budget
            let placement = wanted && affordable ? canvasPresentable(id, canvas, document: document, boxes: boxes) : nil
            if placement != nil {
                budget -= canvas.pixelCount
            }
            if let placement = placement {
                let presenter: WidgetCanvasPresenter
                if let existing = presenters[id] {
                    presenter = existing
                } else {
                    presenter = WidgetCanvasPresenter(canvas: canvas)
                    presenter.schedule = { [weak self] block in self?.schedule(block) }
                    presenter.scheduleAfter = { [weak self] seconds, block in self?.scheduleAfter(seconds, block) }
                    presenters[id] = presenter
                    added.append(presenter)
                    changed = true
                }
                if presenter.frame != placement.frame || presenter.clip?.rect != placement.clip?.rect || presenter.clip?.radius != placement.clip?.radius || presenter.clip?.corners != placement.clip?.corners {
                    presenter.place(placement.frame, clip: placement.clip)
                }
            } else if let removed = presenters.removeValue(forKey: id) {
                removed.remove()
                changed = true
            }
        }
        if presenters.isEmpty && !changed {
            presentationBackoff = 30
        }
        guard changed else {
            return
        }
        presentationEpoch += 1
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        for presenter in added {
            presenter.presentNow()
            documentView.insertOverlay(presenter.container)
        }
        layout(overrides)
        presentationChecked = document
        CATransaction.commit()
    }

    public private(set) var handoffWorst: (geometry: CGFloat, alpha: Float) = (0, 0)
    public private(set) var handoffOffsets: [Double] = []

    private func renderAnimationFrame() {
        if taskDepth > 0 {
            taskNeedsRender = true
            return
        }
        if frameHoldStart != nil {
            frameHoldRender = true
            return
        }
        if holdsSilentState {
            return
        }
        let sampled = animator.sample(now: animationNow)
        if WidgetDocumentView.verify && !compositor.drivenIDs.isEmpty {
            let error = compositor.handoffError(animator: animator, now: animationNow)
            handoffWorst = (max(handoffWorst.geometry, error.geometry), max(handoffWorst.alpha, error.alpha))
            handoffOffsets.append(error.offset)
        }
        var rest = compositor.apply(sampled, bases: { [animator] in animator.base($0) })
        shimmers.apply(&rest)
        defer {
            compositeCheck(sampled)
        }
        if !LiteHTMLWidgetRenderer.same(rest, overrides) {
            if documentView.preparing {
                stageMilliseconds["raster-busy", default: 0] += 1
            } else {
                overrides = rest
                layout(overrides)
            }
        } else {
            stageMilliseconds["composited-only", default: 0] += 1
        }
        if !compositor.animatedPromotions.isEmpty && !animator.animatedIDs().isSuperset(of: compositor.animatedPromotions) {
            setNeedsRender()
        }
    }

    private func canvas(for node: WidgetNode) -> WidgetCanvas {
        let id = dom.publicId(node)
        if let canvas = canvases[id] {
            return canvas
        }
        let size = canvasAttributeSize(node)
        if canvases.count >= LiteHTMLWidgetRenderer.maxCanvases {
            return WidgetCanvas(width: 0, height: 0, allowance: { 0 })
        }
        let canvas = WidgetCanvas(width: size.0, height: size.1, allowance: { [weak self] in
            guard let self = self else {
                return 0
            }
            let used = self.canvases.filter { $0.key != id }.reduce(0) { $0 + $1.value.pixelCount }
            return LiteHTMLWidgetRenderer.maxCanvasPixels - used
        })
        canvasAttributeSizes[id] = size
        canvas.resolveImage = { [weak self] other in
            self?.image(forNode: other)
        }
        canvas.onDraw = { [weak self] in
            self?.canvasDrew(id)
        }
        #if canImport(Metal)
        if LiteHTMLWidgetRenderer.canvasGPUAfter <= 1, canvas.storage != nil, WidgetCanvasMetal.enabled {
            WidgetCanvasMetal.prepare(wait: true)
            canvas.backend = WidgetCanvasMetal()
        } else if canvas.storage != nil, WidgetCanvasMetal.enabled {
            WidgetCanvasMetal.prepare(wait: false)
        }
        #endif
        canvases[id] = canvas
        WidgetCanvasImages.register(uid + "-" + String(id), canvas)
        return canvas
    }

    private func canvasAttributeSize(_ node: WidgetNode) -> (Int, Int) {
        return (node.attribute("width").flatMap { Int($0.trimmingCharacters(in: .whitespaces)) } ?? 300, node.attribute("height").flatMap { Int($0.trimmingCharacters(in: .whitespaces)) } ?? 150)
    }

    private func image(forNode id: Int) -> CGImage? {
        if let canvas = canvases[id] {
            return canvas.image
        }
        guard let node = dom.node(id), node.tag == "img", let src = node.attribute("src"), src.hasPrefix("data:") else {
            return nil
        }
        return LHImages.copyImage(forURL: src)
    }

    private func syncCanvases() {
        for (id, canvas) in canvases {
            guard let node = dom.node(id), node.tag == "canvas" else {
                canvases[id] = nil
                canvasAttributeSizes[id] = nil
                canvasDraws[id] = nil
                presenters.removeValue(forKey: id)?.remove()
                WidgetCanvasImages.unregister(uid + "-" + String(id))
                if let url = canvasURLs.removeValue(forKey: id) {
                    LHImages.forgetImage(withURL: url)
                }
                continue
            }
            let size = canvasAttributeSize(node)
            if let known = canvasAttributeSizes[id], known != size {
                canvasAttributeSizes[id] = size
                if size.0 != canvas.width || size.1 != canvas.height {
                    canvas.resize(size.0, size.1)
                }
            }
        }
    }

    private func canvasSource(_ node: WidgetNode) -> String? {
        let canvas = self.canvas(for: node)
        let id = dom.publicId(node)
        let url = "twcanvas:" + uid + "-" + String(id) + "-" + (presenters[id] != nil ? "p" + String(presentationEpoch) + "_" + String(canvas.width) + "_" + String(canvas.height) : String(canvas.version))
        if let previous = canvasURLs[id], previous != url {
            retiredURLs.append(previous)
        }
        canvasURLs[id] = url
        return url
    }

    private var retiredURLs: [String] = []
    private var listDebt: [CGRect] = []
    static let slowLayoutMilliseconds = ProcessInfo.processInfo.environment["TW_SLOW_MS"].flatMap { Double($0) }
    static let patchRegions = ProcessInfo.processInfo.environment["TW_NO_PATCH_REGIONS"] == nil
    private var lastLayoutTheme = ""
    private var lastLayoutOverrides: [Int: [(String, String)]]?
    private var lastExclusions: Set<Int> = []
    private var lastTextExclusions: Set<Int> = []
    public private(set) var styleFastMismatches = 0
    static let styleFastPath = ProcessInfo.processInfo.environment["TW_NO_STYLE_FAST"] == nil
    static let holePatches = ProcessInfo.processInfo.environment["TW_NO_HOLE_PATCH"] == nil

    private var layoutViewport: CGSize {
        return CGSize(width: width, height: max(1, floor(documentView.size.height)))
    }

    private func patchedDocument(_ overrides: [Int: [(String, String)]]) -> LHDocument? {
        guard LiteHTMLWidgetRenderer.styleFastPath, let document = document, document.viewportSize == layoutViewport, onSerialized == nil, canvases.isEmpty, lastLayoutTheme == theme, let last = lastLayoutOverrides else {
            return nil
        }
        if !LiteHTMLWidgetRenderer.holePatches && (!dom.patchLog.attributes.isEmpty || !dom.patchLog.texts.isEmpty || !dom.patchLog.formTexts.isEmpty) {
            return nil
        }
        guard let changes = dom.patchChanges(theme: theme, overrides: overrides, previous: last), !changes.isEmpty else {
            return nil
        }
        return LHDocument(previous: document, changes: Data(changes), attribute: WidgetDOM.idAttribute, css: masterCSS)
    }

    private func layout(_ overrides: [Int: [(String, String)]]) {
        let start = CACurrentMediaTime()
        var stages: [(String, Double)] = []
        defer {
            let spent = (CACurrentMediaTime() - start) * 1000
            statsLock.lock()
            layoutStats.count += 1
            layoutStats.total += spent
            layoutStats.worst = max(layoutStats.worst, spent)
            statsLock.unlock()
            if let slow = LiteHTMLWidgetRenderer.slowLayoutMilliseconds, spent >= slow {
                print(String(format: "SLOW layout %.2f ms t=%.3f ", spent, CACurrentMediaTime() - timeOrigin) + stages.map { String(format: "%@ %.2f", $0.0, $0.1) }.joined(separator: "  "))
            }
        }
        syncCanvases()
        var mark = CACurrentMediaTime()
        func stage(_ name: String) {
            let now = CACurrentMediaTime()
            stageMilliseconds[name, default: 0] += (now - mark) * 1000
            stages.append((name, (now - mark) * 1000))
            mark = now
        }
        let fast = patchedDocument(overrides)
        stage("style-patch")
        let html = fast == nil || LiteHTMLWidgetRenderer.patchCheck ? dom.serialize(theme: theme, overrides: overrides, canvasSource: { [unowned self] node in self.canvasSource(node) }) : ""
        stage("serialize")
        let next = fast ?? LHDocument(html: html, css: masterCSS, previous: document, viewportSize: layoutViewport)
        next.controlsActive = controlsActive
        dom.resetPatchLog()
        if fast != nil {
            stageMilliseconds["style-fast", default: 0] += 1
            if LiteHTMLWidgetRenderer.patchCheck && next.html != html {
                styleFastMismatches += 1
                print("STYLEFAST source differs from serialized HTML")
            }
        }
        lastLayoutTheme = theme
        lastLayoutOverrides = overrides
        let layered = magic.isActive ? Set<Int>() : svgLayers.ids
        svgLayers.setSuspended(magic.isActive)
        let shimmered = magic.isActive ? Set<Int>() : shimmers.ids
        let excluded = compositor.ids.union(magic.excluded).union(layered).union(shimmered.map { -$0 - 1 })
        let scrollExcluded = scrolling.promotedIDs
        var textExcluded: Set<Int> = []
        if form.focusedId != 0, let node = dom.node(form.focusedId), WidgetDOM.hidesText(node) {
            textExcluded.insert(form.focusedId)
        }
        let exclusionsChanged = excluded != lastExclusions || scrollExcluded != lastScrollExclusions || textExcluded != lastTextExclusions
        if !silentRendering {
            lastExclusions = excluded
            lastScrollExclusions = scrollExcluded
            lastTextExclusions = textExcluded
            if exclusionsChanged {
                documentView.synchronizePaintHandoff()
            }
        }
        if !textExcluded.isEmpty {
            next.setTextPaintExclusions(Set(textExcluded.map { String($0) }), attribute: WidgetDOM.idAttribute)
        }
        if !scrollExcluded.isEmpty {
            next.setScrollContentExclusions(Set(scrollExcluded.map { String($0) }), attribute: WidgetDOM.idAttribute)
        }
        let painted = compositor.ids.union(magic.excluded)
        if !painted.isEmpty {
            next.setPaintExclusions(Set(painted.map { String($0) }), attribute: WidgetDOM.idAttribute)
        }
        next.setLayerExclusions(Set(layered.map { String($0) }), attribute: WidgetDOM.idAttribute)
        next.setBackgroundImageExclusions(Set(shimmered.map { String($0) }), attribute: WidgetDOM.idAttribute)
        stage("document")
        if next.reusedPrevious {
            stageMilliseconds["reused", default: 0] += 1
        }
        onSerialized?(html, next.reusedPrevious)
        defer {
            if LiteHTMLWidgetRenderer.patchCheck {
                lastSerialized = html
            }
        }
        _ = next.layout(maxWidth: width, preferredWidth: width)
        for (id, offset) in scrolling.offsets {
            _ = next.setScrollOffset(offset, attribute: WidgetDOM.idAttribute, value: String(id))
        }
        stage("layout")
        if LiteHTMLWidgetRenderer.patchCheck && next.reusedPrevious {
            patchChecks += 1
            let fresh = LHDocument(html: html, css: masterCSS, previous: nil, viewportSize: layoutViewport)
            fresh.controlsActive = controlsActive
            if !textExcluded.isEmpty {
                fresh.setTextPaintExclusions(Set(textExcluded.map { String($0) }), attribute: WidgetDOM.idAttribute)
            }
            _ = fresh.layout(maxWidth: width, preferredWidth: width)
            let size = CGSize(width: width, height: max(1, ceil(max(next.size.height, fresh.size.height))))
            if !painted.isEmpty {
                fresh.setPaintExclusions(Set(painted.map { String($0) }), attribute: WidgetDOM.idAttribute)
            }
            fresh.setLayerExclusions(Set(layered.map { String($0) }), attribute: WidgetDOM.idAttribute)
            fresh.setBackgroundImageExclusions(Set(shimmered.map { String($0) }), attribute: WidgetDOM.idAttribute)
            if !scrollExcluded.isEmpty {
                fresh.setScrollContentExclusions(Set(scrollExcluded.map { String($0) }), attribute: WidgetDOM.idAttribute)
            }
            for (id, offset) in scrolling.offsets {
                _ = fresh.setScrollOffset(offset, attribute: WidgetDOM.idAttribute, value: String(id))
            }
            let a = next.copyImage(maxWidth: width, rect: CGRect(origin: .zero, size: size), scale: 2)
            let b = fresh.copyImage(maxWidth: width, rect: CGRect(origin: .zero, size: size), scale: 2)
            let boundsDiffer = next.layoutSignature != fresh.layoutSignature
            if boundsDiffer {
                patchBoundsMismatches += 1
                print("PATCHBOUNDS reusedLayout=\(next.reusedLayout) element bounds differ from a fresh layout")
                if let dir = ProcessInfo.processInfo.environment["TW_PATCHCHECK_DUMP"], patchBoundsMismatches <= 3 {
                    try? next.layoutSignature.write(toFile: dir + "/bounds-patched\(patchBoundsMismatches).txt", atomically: true, encoding: .utf8)
                    try? fresh.layoutSignature.write(toFile: dir + "/bounds-fresh\(patchBoundsMismatches).txt", atomically: true, encoding: .utf8)
                }
            }
            if abs(next.size.height - fresh.size.height) > 0.01 || !LiteHTMLWidgetRenderer.sameImage(a, b) || boundsDiffer {
                patchMismatches += 1
                if let dir = ProcessInfo.processInfo.environment["TW_PATCHCHECK_DUMP"], patchMismatches <= 3 {
                    print("PATCHDBG reusedLayout=\(next.reusedLayout) sizes=\(next.size) \(fresh.size)")
                    for (name, image) in [("patched", a), ("fresh", b)] {
                        if let image = image {
                            try? NSBitmapImageRep(cgImage: image).representation(using: .png, properties: [:])?.write(to: URL(fileURLWithPath: dir + "/\(name)\(patchMismatches).png"))
                        }
                    }
                    try? html.write(toFile: dir + "/mismatch\(patchMismatches).html", atomically: true, encoding: .utf8)
                    if let previousHTML = lastSerialized {
                        try? previousHTML.write(toFile: dir + "/before\(patchMismatches).html", atomically: true, encoding: .utf8)
                    }
                }
            }
        }
        defer { stage("report") }
        if silentRendering {
            silentLayout(next)
            return
        }
        if !silentDebt.isEmpty, documentView.document != nil {
            documentView.invalidate(silentDebt.map { $0.insetBy(dx: -1, dy: -1) })
        }
        silentDebt.removeAll()
        if LiteHTMLWidgetRenderer.patchRegions, next.reusedPrevious, !exclusionsChanged, displayList != nil, documentView.document != nil, let regions = (next.reusedLayout ? next.patchDirtyRects : next.layoutDirtyRects)?.map({ $0.rectValue }).filter({ !svgLayers.covers($0) }) {
            stage("diff-list")
            stageMilliseconds["regions", default: 0] += 1
            if listDebt.count + regions.count > 32 {
                listDebt = [(listDebt + regions).reduce(CGRect.null) { $0.union($1) }]
            } else {
                listDebt += regions
            }
            if LiteHTMLWidgetRenderer.dirtyLog, !regions.isEmpty {
                print("DIRTY t=\(String(format: "%.3f", CACurrentMediaTime() - timeOrigin)) regions \(regions.map { "\(Int($0.minX)),\(Int($0.minY)) \(Int($0.width))x\(Int($0.height))" }.joined(separator: " | "))")
            }
            documentView.asyncAllowed = !exclusionsChanged && !magic.isActive
            documentView.invalidate(regions.map { $0.insetBy(dx: -1, dy: -1) })
            documentView.asyncAllowed = false
        } else {
            let list = next.displayList(maxWidth: width, scale: documentView.scale)
            let previousList = displayList
            displayList = list
            let debt = listDebt
            listDebt.removeAll()
            stage("diff-list")
            if let previous = previousList, documentView.document != nil {
                let rects = (list.changedRects(since: previous).map { $0.rectValue } + debt).filter { !svgLayers.covers($0) }
                if !rects.isEmpty {
                    if LiteHTMLWidgetRenderer.dirtyLog {
                        print("DIRTY t=\(String(format: "%.3f", CACurrentMediaTime() - timeOrigin)) \(rects.map { "\(Int($0.minX)),\(Int($0.minY)) \(Int($0.width))x\(Int($0.height))" }.joined(separator: " | "))")
                    }
                    documentView.asyncAllowed = !exclusionsChanged && !magic.isActive
                    documentView.invalidate(rects.map { $0.insetBy(dx: -1, dy: -1) })
                    documentView.asyncAllowed = false
                }
            } else {
                documentView.invalidate(nil)
            }
            stage("diff")
        }
        if probing {
            if probeBase == nil, let document = document {
                probeBase = (document, boundsValid ? bounds : nil)
            }
            probePending = true
        } else {
            probePending = false
            noteResize(to: next)
        }
        document = next
        let shimmerBefore = shimmers.ids
        if shimmers.update(document: next, animator: animator, width: width, host: documentView.contentLayer, scale: documentView.scale, composited: compositor.ids.union(magic.excluded).union(scrolling.promotedIDs).union(svgLayers.ids), suspended: magic.isActive) || shimmers.dropped || shimmerBefore != shimmers.ids {
            shimmers.dropped = false
            next.setBackgroundImageExclusions(Set((magic.isActive ? [] : shimmers.ids).map { String($0) }), attribute: WidgetDOM.idAttribute)
            documentView.invalidate(nil)
        }
        if WidgetDocumentView.verify, !shimmers.ids.isEmpty {
            shimmerVerifyTick += 1
            if shimmerVerifyTick % 1 == 0 {
                shimmers.verify(document: next, width: width)
            }
        }
        if !svgLayers.ids.isEmpty, !magic.isActive, svgLayers.render(document: next, width: width, host: documentView.contentLayer, scale: documentView.scale, dom: dom, composited: compositor.ids.union(magic.excluded).union(scrolling.promotedIDs)) {
            next.setLayerExclusions(Set(svgLayers.ids.map { String($0) }), attribute: WidgetDOM.idAttribute)
            documentView.invalidate(nil)
        }
        svgLayers.verify(document: next, width: width)
        for url in retiredURLs {
            LHImages.forgetImage(withURL: url)
        }
        retiredURLs.removeAll()
        boundsValid = false
        adoptNextBounds(next)
        documentView.document = next
        if !probing {
            documentView.presentAlignmentIfDrawnSynchronously(next)
            reportSize()
            reportResize()
        }
        placeSurfaces()
        schedulePrepare()
        documentView.flushIfDetached()
    }

    private func silentLayout(_ next: LHDocument) {
        if LiteHTMLWidgetRenderer.patchRegions, next.reusedPrevious, displayList != nil, documentView.document != nil, let regions = (next.reusedLayout ? next.patchDirtyRects : next.layoutDirtyRects)?.map({ $0.rectValue }) {
            if listDebt.count + regions.count > 32 {
                listDebt = [(listDebt + regions).reduce(CGRect.null) { $0.union($1) }]
            } else {
                listDebt += regions
            }
            silentDebt += regions
        } else {
            let list = next.displayList(maxWidth: width, scale: documentView.scale)
            if let previous = displayList {
                silentDebt += list.changedRects(since: previous).map { $0.rectValue } + listDebt
                displayList = list
                listDebt.removeAll()
            }
        }
        if silentDebt.count > 32 {
            silentDebt = [silentDebt.reduce(CGRect.null) { $0.union($1) }]
        }
        if taskDepth == 0 {
            noteResize(to: next)
        }
        document = next
        boundsValid = false
        adoptNextBounds(next)
    }

    private static let scriptedProperties: Set<String> = ["transform", "opacity"]
    static let scriptedPromotion = ProcessInfo.processInfo.environment["TW_NO_SCRIPTED"] == nil
    private var scriptedHits: [Int: (count: Int, batch: Int, at: CFTimeInterval)] = [:]
    private var opsBatch = 0
    static let scriptedStreakGap: CFTimeInterval = 0.12
    static let scriptedStreakBatches = 4

    private func applyCompositedWhileThrottled(_ ops: [WidgetOp]) -> Bool {
        guard !frozen, !pendingGeometry, !needsRender, !renderAtTick, document != nil,
              ops.count <= WidgetCompositor.maxLayers * 2,
              scriptedOnly(ops) != nil,
              ops.allSatisfy({ $0.value.utf8.count <= 256 }),
              compositor.canApplyScripted(ops, dom: dom) else {
            return false
        }
        applyMeasured(ops, held: false)
        return true
    }

    private func scriptedOnly(_ ops: [WidgetOp]) -> Set<Int>? {
        let promoted = compositor.scriptedIDs
        if promoted.isEmpty || ops.isEmpty {
            return nil
        }
        var touched: Set<Int> = []
        for op in ops {
            guard op.kind == .style, LiteHTMLWidgetRenderer.scriptedProperties.contains(op.name.lowercased()), promoted.contains(op.id), !animator.transitions(op.id, property: op.name.lowercased()) else {
                return nil
            }
            touched.insert(op.id)
        }
        return touched
    }

    private func noteScriptedTransforms(_ ops: [WidgetOp]) {
        opsBatch += 1
        let now = CACurrentMediaTime()
        var moving: Set<Int> = []
        for op in ops where op.kind == .style && LiteHTMLWidgetRenderer.scriptedProperties.contains(op.name.lowercased()) {
            moving.insert(op.id)
        }
        if moving.count > WidgetCompositor.maxLayers {
            compositor.scriptedCandidates.subtract(moving)
            return
        }
        for op in ops {
            guard op.kind == .style, LiteHTMLWidgetRenderer.scriptedProperties.contains(op.name.lowercased()) else {
                continue
            }
            let id = op.id
            let hit = scriptedHits[id]
            if let hit = hit, hit.batch == opsBatch {
                continue
            }
            let continues = hit.map { opsBatch - $0.batch <= LiteHTMLWidgetRenderer.scriptedStreakBatches || now - $0.at <= LiteHTMLWidgetRenderer.scriptedStreakGap } ?? false
            let count = continues ? hit!.count + 1 : 1
            scriptedHits[id] = (count, opsBatch, now)
            if (count >= 3 || op.name.lowercased() == "transform") && LiteHTMLWidgetRenderer.scriptedPromotion && !compositor.scriptedCandidates.contains(id) && compositor.scriptedCandidates.count < WidgetCompositor.maxLayers {
                compositor.scriptedCandidates.insert(id)
            }
        }
        if scriptedHits.count > 4096 {
            scriptedHits = scriptedHits.filter { opsBatch - $0.value.batch < 8 || now - $0.value.at < 1 }
        }
    }

    private var needsFrames: Bool {
        return WidgetDocumentView.verify ? animator.isActive : animator.isActive(excluding: compositor.drivenIDs)
    }

    public var drivenCount: Int {
        return compositor.drivenIDs.count
    }

    private var canDrive: Bool {
        return !frozen && isOnScreen && WidgetCompositor.handoffEnabled
    }

    private var drivenEndToken = 0

    private func scheduleDrivenEnds(now: CFTimeInterval) {
        let real = CACurrentMediaTime()
        var delays: [Double] = []
        for id in compositor.drivenIDs {
            let clock = animator.web.animations(id).isEmpty ? now : real
            if let end = animator.endTime(id), end > clock {
                delays.append(end - clock)
            }
        }
        if listened.contains("animationiteration") {
            delays += compositor.drivenIDs.compactMap { animator.nextIteration($0, after: now) }.map { $0 - now }
        }
        guard let next = delays.min() else {
            return
        }
        drivenEndToken += 1
        let token = drivenEndToken
        scheduleAfter(next + 0.002) { [weak self] in
            guard let self = self, self.drivenEndToken == token, !self.destroyed else {
                return
            }
            self.render()
        }
    }

    private func applyExclusions() {
        guard let document = document else {
            return
        }
        document.setPaintExclusions(Set(compositor.ids.union(magic.excluded).map { String($0) }), attribute: WidgetDOM.idAttribute)
        document.setLayerExclusions(Set((magic.isActive ? [] : svgLayers.ids).map { String($0) }), attribute: WidgetDOM.idAttribute)
        svgLayers.setSuspended(magic.isActive)
        documentView.invalidate(nil)
    }

    private func finishMagicMove() {
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        magic.removeLayers()
        if let document = document, !svgLayers.ids.isEmpty {
            _ = svgLayers.render(document: document, width: width, host: documentView.contentLayer, scale: documentView.scale, dom: dom, composited: compositor.ids.union(scrolling.promotedIDs))
        }
        applyExclusions()
        documentView.displayIfNeeded()
        CATransaction.commit()
    }

    static let compositeChecking = ProcessInfo.processInfo.environment["TW_COMPOSITECHECK"] != nil
    public private(set) var compositeChecks = 0
    public private(set) var compositeWorst = 0.0

    private func compositeCheck(_ sampled: [Int: [(String, String)]]) {
        guard LiteHTMLWidgetRenderer.compositeChecking, !compositor.ids.isEmpty, let document = document else {
            return
        }
        let html = dom.serialize(theme: theme, overrides: sampled, canvasSource: { [unowned self] node in self.canvasSource(node) })
        let reference = LHDocument(html: html, css: masterCSS, previous: nil, viewportSize: document.viewportSize)
        reference.controlsActive = controlsActive
        _ = reference.layout(maxWidth: width, preferredWidth: width)
        let size = CGSize(width: width, height: max(1, ceil(document.size.height)))
        let scale = documentView.scale
        let pw = Int(ceil(size.width * scale)), ph = Int(ceil(size.height * scale))
        func render(_ body: (CGContext) -> Void) -> CGImage? {
            guard let ctx = CGContext(data: nil, width: pw, height: ph, bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else {
                return nil
            }
            ctx.translateBy(x: 0, y: CGFloat(ph))
            ctx.scaleBy(x: scale, y: -scale)
            ctx.setAllowsFontSmoothing(false)
            body(ctx)
            return ctx.makeImage()
        }
        guard let expected = render({ reference.draw(in: $0, origin: .zero, clip: CGRect(origin: .zero, size: size)) }), let root = documentView.contentLayer, let actual = render({ root.render(in: $0) }) else {
            return
        }
        let w = expected.width, h = expected.height
        guard actual.width == w, actual.height == h else {
            return
        }
        func pixels(_ image: CGImage) -> [UInt8] {
            var data = [UInt8](repeating: 0, count: w * h * 4)
            let ctx = CGContext(data: &data, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
            ctx.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))
            return data
        }
        let a = pixels(expected), b = pixels(actual)
        func distance(_ x: [UInt8], _ i: Int, _ y: [UInt8], _ j: Int) -> Int {
            return max(abs(Int(x[i]) - Int(y[j])), abs(Int(x[i + 1]) - Int(y[j + 1])), abs(Int(x[i + 2]) - Int(y[j + 2])), abs(Int(x[i + 3]) - Int(y[j + 3])))
        }
        func unexplained(_ x: [UInt8], _ y: [UInt8], _ row: Int, _ col: Int) -> Bool {
            let i = (row * w + col) * 4
            for dy in -1...1 {
                for dx in -1...1 {
                    let r = row + dy, c = col + dx
                    if r >= 0 && r < h && c >= 0 && c < w && distance(x, i, y, (r * w + c) * 4) <= 48 {
                        return false
                    }
                }
            }
            return true
        }
        var bad = 0
        for row in 0..<h {
            for col in 0..<w {
                let i = (row * w + col) * 4
                if distance(a, i, b, i) > 48 && (unexplained(a, b, row, col) || unexplained(b, a, row, col)) {
                    bad += 1
                }
            }
        }
        compositeChecks += 1
        let share = Double(bad) / Double(w * h) * 100
        if share > compositeWorst, let dir = ProcessInfo.processInfo.environment["TW_COMPOSITECHECK_DUMP"] {
            try? NSBitmapImageRep(cgImage: expected).representation(using: .png, properties: [:])?.write(to: URL(fileURLWithPath: dir + "/expected.png"))
            try? NSBitmapImageRep(cgImage: actual).representation(using: .png, properties: [:])?.write(to: URL(fileURLWithPath: dir + "/actual.png"))
        }
        compositeWorst = max(compositeWorst, share)
    }

    static let patchCheck = ProcessInfo.processInfo.environment["TW_PATCHCHECK"] != nil
    static let dirtyLog = ProcessInfo.processInfo.environment["TW_DIRTY_LOG"] != nil
    public private(set) var patchChecks = 0
    public private(set) var patchMismatches = 0
    public private(set) var patchBoundsMismatches = 0
    private var lastSerialized: String?

    private static func sameImage(_ a: CGImage?, _ b: CGImage?) -> Bool {
        guard let a = a, let b = b, a.width == b.width, a.height == b.height, let da = a.dataProvider?.data, let db = b.dataProvider?.data, CFDataGetLength(da) == CFDataGetLength(db) else {
            return a == nil && b == nil
        }
        let pa = CFDataGetBytePtr(da)!, pb = CFDataGetBytePtr(db)!
        for i in 0..<CFDataGetLength(da) where abs(Int(pa[i]) - Int(pb[i])) > 1 {
            return false
        }
        return true
    }

    private static func same(_ a: [Int: [(String, String)]], _ b: [Int: [(String, String)]]) -> Bool {
        guard a.count == b.count else {
            return false
        }
        for (key, list) in a {
            guard let other = b[key], other.count == list.count else {
                return false
            }
            for (x, y) in zip(list, other) where x.0 != y.0 || x.1 != y.1 {
                return false
            }
        }
        return true
    }

    private func dispatchAnimationEvent(_ type: String, _ id: Int, name: String, elapsed: Double) {
        if frozen || (!LiteHTMLWidgetRenderer.alwaysSent.contains(type) && !listened.contains(type)) {
            return
        }
        let event: [String: Any] = ["type": type, "targetId": id, type == "transitionend" ? "propertyName" : "animationName": name, "elapsedTime": elapsed.isFinite ? elapsed : 0]
        schedule { [weak self] in
            self?.post(["type": "event", "ev": event])
        }
    }

    private var pendingImages: [Int] = []

    private func imageSourceChanged(_ node: WidgetNode) {
        pendingImages.append(dom.publicId(node))
        if pendingImages.count == 1 {
            schedule { [weak self] in
                self?.reportImages()
            }
        }
    }

    private func reportImages() {
        let ids = pendingImages
        pendingImages = []
        if frozen || destroyed {
            return
        }
        var seen = Set<Int>()
        for id in ids where seen.insert(id).inserted {
            guard let node = dom.node(id), node.tag == "img", let src = node.attribute("src"), !src.isEmpty else {
                continue
            }
            let size = LHImages.size(forURL: src)
            if size.width > 0 || size.height > 0 {
                post(["type": "event", "ev": ["type": "load", "targetId": id, "naturalWidth": Double(size.width), "naturalHeight": Double(size.height)]])
            } else {
                post(["type": "event", "ev": ["type": "error", "targetId": id]])
            }
        }
    }

    private var reportedHeight = 0.0

    private func reportSize() {
        guard taskDepth == 0, let document = document else {
            return
        }
        var message: [String: Any] = [
            "type": "size",
            "width": Double(width),
            "height": Double(ceil(document.size.height)),
            "innerWidth": Double(width),
            "innerHeight": Double(documentView.size.height),
            "dpr": Double(documentView.scale),
            "painted": documentView.paintedForReport,
        ]
        let key = "\(message["height"]!)|\(message["width"]!)|\(message["innerHeight"]!)|\(message["dpr"]!)|\(message["painted"]!)"
        let height = Double(ceil(document.size.height))
        if documentView.detached, height != reportedHeight, !documentView.hasPresented(height: CGFloat(height)) {
            return
        }
        if key != lastSize {
            if sizeTransition {
                message["transition"] = MagicMoveStage.timing
            }
            lastSize = key
            reportedHeight = height
            post(message)
        }
    }

    func documentViewScaleChanged() {
        documentView.scaleChanged()
        setNeedsRender()
    }

    func viewResized() {
        guard document != nil else {
            return
        }
        if magic.isActive {
            magic.cancel()
        }
        render()
    }

    func elementBounds() -> [Int: CGRect] {
        if boundsValid {
            return bounds
        }
        let result = document?.idBounds() ?? [:]
        bounds = result
        boundsValid = true
        return result
    }

    func placeSurfaces() {
        guard isOnExecutor() else {
            schedule { [weak self] in self?.placeSurfaces() }
            return
        }
        guard !destroyed, surfaceLayer != nil || !placedSurfaces.isEmpty else {
            return
        }
        if surfaceRevision != WidgetDOM.revision {
            surfaceRevision = WidgetDOM.revision
            var tokens: [String] = []
            for node in dom.nodes.values where !node.isText {
                if let token = node.attribute(LiteHTMLWidgetRenderer.surfaceAttribute), !token.isEmpty, token.count <= 12, token.allSatisfy({ $0.isASCII && $0.isNumber }), !tokens.contains(token) {
                    tokens.append(token)
                    if tokens.count == LiteHTMLWidgetRenderer.maxSurfaces {
                        break
                    }
                }
            }
            surfaceTokens = tokens.sorted()
        }
        let shown = isOnScreen
        let active = shown && !frozen
        let scale = documentView.scale
        var rects: [String: CGRect] = [:]
        if let document = document, surfaceLayer != nil {
            for token in surfaceTokens {
                let rect = document.bounds(matching: "[\(LiteHTMLWidgetRenderer.surfaceAttribute)=\"\(token)\"]")
                if !rect.isNull, rect.width >= 1, rect.height >= 1 {
                    rects[token] = rect
                }
            }
        }
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        for (token, layer) in placedSurfaces where rects[token] == nil {
            layer.twRemoveFromSuperlayer()
            placedSurfaces[token] = nil
            surfaceLayout?(token, .zero, scale, false)
        }
        if !rects.isEmpty {
            let container: CALayer
            if let existing = surfaceContainer {
                container = existing
            } else {
                container = CALayer()
                container.masksToBounds = true
                container.zPosition = 0.5
                container.actions = ["bounds": NSNull(), "position": NSNull(), "frame": NSNull(), "hidden": NSNull(), "sublayers": NSNull()]
                surfaceContainer = container
            }
            if container.superlayer !== documentView.contentLayer {
                documentView.contentLayer?.twAddSublayer(container)
            }
            container.frame = CGRect(origin: .zero, size: documentView.size)
            container.isHidden = !shown
            for (token, rect) in rects {
                var layer = placedSurfaces[token]
                if layer == nil, let made = surfaceLayer?(token) {
                    made.zPosition = 0
                    container.twAddSublayer(made)
                    placedSurfaces[token] = made
                    layer = made
                }
                guard let placed = layer else {
                    continue
                }
                if placed.frame != rect {
                    placed.frame = rect
                }
                placed.contentsScale = scale
                surfaceLayout?(token, rect.size, scale, active)
            }
        } else {
            surfaceContainer?.twRemoveFromSuperlayer()
        }
        CATransaction.commit()
    }

    private func reportResize() {
        guard taskDepth == 0, !observedResize.isEmpty else {
            return
        }
        let boxes = elementBounds()
        var entries: [[String: Any]] = []
        for id in observedResize {
            let size = boxes[id]?.size ?? .zero
            if lastResize[id] != size {
                lastResize[id] = size
                entries.append(["id": id, "width": Double(size.width), "height": Double(size.height)])
            }
        }
        if !entries.isEmpty {
            post(["type": "resize", "entries": entries])
        }
    }

    private func call(_ node: WidgetNode, _ method: String, _ args: [Any] = []) {
        let id = dom.publicId(node)
        switch method {
        case "setPointerCapture", "releasePointerCapture":
            guard (args.first as? NSNumber)?.intValue == 1, pressId != 0 else { return }
            if method == "setPointerCapture", dom.isAttached(node) {
                setPointerCapture(id)
            } else if pointerCaptureId == id {
                setPointerCapture(0)
            }
        case "scrollTo", "scrollBy":
            guard args.count >= 2, let left = (args[0] as? NSNumber)?.doubleValue, let top = (args[1] as? NSNumber)?.doubleValue, left.isFinite, top.isFinite else {
                return
            }
            requestScroll(.position(id, left: CGFloat(left), top: CGFloat(top), relative: method == "scrollBy"))
        case "scrollIntoView":
            pendingScrollRequests.append(.reveal(id, args))
        case "observeResize":
            observedResize.insert(id)
            lastResize[id] = nil
            schedule { [weak self] in
                self?.reportResize()
            }
        case "unobserveResize":
            observedResize.remove(id)
            lastResize[id] = nil
        case "focus":
            scriptFocus(id)
        case "completeEditorCommand":
            completeEditorCommand(node, args)
        case "blur":
            if form.focusedId == id {
                endEditing()
            }
        default:
            break
        }
    }

    private static func rectResult(_ rect: CGRect) -> [String: Any] {
        return ["x": Double(rect.minX), "y": Double(rect.minY), "width": Double(rect.width), "height": Double(rect.height), "top": Double(rect.minY), "left": Double(rect.minX), "right": Double(rect.maxX), "bottom": Double(rect.maxY)]
    }

    private func viewport() -> [String: Any] {
        return ["innerWidth": Double(width), "innerHeight": Double(documentView.size.height), "dpr": Double(documentView.scale)]
    }

    private func query(_ q: [String: Any]) -> Any {
        let kind = (q["q"] as? String) ?? ""
        if needsRender || renderAtTick || frameHoldRender {
            if !(kind == "box" && !pendingGeometry && document != nil) {
                renderForQuery()
            }
        }
        var rect: CGRect?
        if let id = (q["id"] as? NSNumber)?.intValue {
            guard let node = dom.node(id), !node.isText else {
                return ["error": "no such element"]
            }
            if kind == "rect" || kind == "rects" {
                if visualBounds == nil {
                    visualBounds = document?.idBounds(transformed: true, lineBoxes: true) ?? [:]
                }
                rect = visualBounds?[id] ?? svgBox(node, id: id) ?? .zero
            } else {
                rect = elementBounds()[id] ?? .zero
            }
        }
        switch kind {
        case "rect":
            return LiteHTMLWidgetRenderer.rectResult(rect ?? .zero)
        case "rects":
            let lines = (q["id"] as? NSNumber).map { document?.lineBoxes(attribute: WidgetDOM.idAttribute, value: $0.stringValue) ?? [] } ?? []
            if !lines.isEmpty {
                return lines.map { LiteHTMLWidgetRenderer.rectResult($0.rectValue) }
            }
            return [LiteHTMLWidgetRenderer.rectResult(rect ?? .zero)]
        case "box":
            let r = rect ?? .zero
            let offsets = (q["id"] as? NSNumber).flatMap { document?.offsetMetrics(attribute: WidgetDOM.idAttribute, value: $0.stringValue) }?.mapValues { $0.doubleValue } ?? [:]
            func withOffsets(_ box: [String: Double]) -> [String: Double] {
                return box.merging(offsets) { _, offset in offset }
            }
            if let id = (q["id"] as? NSNumber)?.intValue, dom.node(id)?.tag.lowercased() == "textarea", let metrics = document?.textareaMetrics(attribute: WidgetDOM.idAttribute, value: String(id)) {
                var box = metrics.mapValues { $0.doubleValue }
                box["offsetWidth"] = Double(r.width.rounded())
                box["offsetHeight"] = Double(r.height.rounded())
                box["offsetTop"] = Double(r.minY.rounded())
                box["offsetLeft"] = Double(r.minX.rounded())
                box["offsetParent"] = 0
                box["scrollTop"] = Double(scrolling.offset(id).y)
                box["scrollLeft"] = Double(scrolling.offset(id).x)
                return withOffsets(box)
            }
            if let id = (q["id"] as? NSNumber)?.intValue, let geometry = scrolling.geometries[id] {
                let offset = scrolling.offset(id)
                var box: [String: Double] = ["offsetWidth": Double(r.width), "offsetHeight": Double(r.height), "offsetTop": Double(r.minY), "offsetLeft": Double(r.minX), "offsetParent": 0]
                box["clientWidth"] = Double(geometry.viewport.width)
                box["clientHeight"] = Double(geometry.viewport.height)
                box["clientTop"] = 0
                box["clientLeft"] = 0
                box["scrollWidth"] = Double(max(geometry.viewport.width, geometry.content.width))
                box["scrollHeight"] = Double(max(geometry.viewport.height, geometry.content.height))
                box["scrollTop"] = Double(offset.y)
                box["scrollLeft"] = Double(offset.x)
                return withOffsets(box)
            }
            return withOffsets(["offsetWidth": Double(r.width), "offsetHeight": Double(r.height), "offsetTop": Double(r.minY), "offsetLeft": Double(r.minX), "offsetParent": 0, "clientWidth": Double(r.width), "clientHeight": Double(r.height), "clientTop": 0, "clientLeft": 0, "scrollWidth": Double(r.width), "scrollHeight": Double(r.height), "scrollTop": 0, "scrollLeft": 0])
        case "elementFromPoint":
            let x = (q["x"] as? NSNumber)?.doubleValue ?? 0
            let y = (q["y"] as? NSNumber)?.doubleValue ?? 0
            return target(at: CGPoint(x: x, y: y))
        case "matchMedia":
            return ["matches": matchMedia((q["query"] as? String) ?? "")]
        case "viewport":
            return viewport()
        case "measureText":
            return measureText((q["text"] as? String) ?? "", font: (q["font"] as? String) ?? "10px sans-serif")
        case "canvasCall":
            return canvasCall(q)
        case "anims":
            guard let id = (q["id"] as? NSNumber)?.intValue, let root = dom.node(id) else {
                return ["list": []]
            }
            var ids = [id]
            if q["subtree"] as? Bool == true {
                var stack = Array(root.children.reversed())
                while let node = stack.popLast(), ids.count < 100_000 {
                    ids.append(dom.publicId(node))
                    stack.append(contentsOf: node.children.reversed())
                }
            }
            updateAnimationClock()
            return ["list": animator.cssAnimations(ids, now: animationNow)]
        case "svg":
            guard let id = (q["id"] as? NSNumber)?.intValue, let method = q["method"] as? String,
                  let geometry = document?.svgGeometry(method, at: ((q["args"] as? [Any])?.first as? NSNumber)?.doubleValue ?? 0, attribute: WidgetDOM.idAttribute, value: String(id)) else {
                return ["error": "unsupported"]
            }
            if method == "getTotalLength" || method == "getComputedTextLength" {
                return geometry["length"]?.doubleValue ?? 0
            }
            return geometry.mapValues { $0.doubleValue }
        case "cs":
            let id = (q["id"] as? NSNumber)?.intValue ?? 0
            if let prop = q["prop"] as? String, prop.hasPrefix("--") {
                return document?.customProperty(prop, attribute: WidgetDOM.idAttribute, value: String(id)) ?? ""
            }
            let pseudo = q["pseudo"] as? String
            var style = document?.computedStyle(attribute: WidgetDOM.idAttribute, value: String(id), pseudo: pseudo) ?? [:]
            if (pseudo ?? "").isEmpty, let transform = animator.lastOverrides[id]?.last(where: { $0.0 == "transform" })?.1 {
                style["transform"] = LiteHTMLWidgetRenderer.computedTransform(transform, box: rect?.size ?? .zero)
            }
            if let prop = q["prop"] as? String {
                return style[prop] ?? LiteHTMLWidgetRenderer.computedDefault(prop)
            }
            return style
        default:
            return ["error": "unsupported"]
        }
    }

    private func svgBox(_ node: WidgetNode, id: Int) -> CGRect? {
        guard node.svg, let box = document?.svgBox(attribute: WidgetDOM.idAttribute, value: String(id)), !box.isNull else {
            return nil
        }
        return box
    }

    private func binaryQuery(_ q: [String: Any]) -> (result: [String: Any], data: Data)? {
        guard (q["q"] as? String) == "imageData" else { return nil }
        if needsRender || renderAtTick || frameHoldRender {
            renderForQuery()
        }
        guard let id = (q["id"] as? NSNumber)?.intValue, let node = dom.node(id), node.tag == "canvas" else {
            return nil
        }
        let canvas = self.canvas(for: node)
        let w = WidgetCanvas.int(q["w"] ?? 0), h = WidgetCanvas.int(q["h"] ?? 0)
        return (["width": w, "height": h], canvas.pixels(x: WidgetCanvas.int(q["x"] ?? 0), y: WidgetCanvas.int(q["y"] ?? 0), w: w, h: h))
    }

    private func canvasCall(_ q: [String: Any]) -> Any {
        guard let id = (q["id"] as? NSNumber)?.intValue, let node = dom.node(id), node.tag == "canvas" else {
            return ["error": "no context"]
        }
        let canvas = self.canvas(for: node)
        let args = (q["args"] as? [Any]) ?? []
        switch (q["method"] as? String) ?? "" {
        case "isPointInPath", "isPointInStroke":
            return canvas.isPointInPath(args)
        case "getTransform":
            let t = canvas.currentTransform
            let matrix: [String: Double] = ["a": Double(t.a), "b": Double(t.b), "c": Double(t.c), "d": Double(t.d), "e": Double(t.tx), "f": Double(t.ty)]
            return matrix
        case "getLineDash":
            return canvas.lineDash()
        case "toDataURL":
            guard let image = canvas.image, let png = NSBitmapImageRep(cgImage: image).representation(using: .png, properties: [:]) else {
                return "data:,"
            }
            return "data:image/png;base64," + png.base64EncodedString()
        default:
            return ["error": "unsupported"]
        }
    }

    private static func computedTransform(_ value: String, box: CGSize) -> String {
        let text = value.trimmingCharacters(in: .whitespaces)
        if text.isEmpty || text.lowercased() == "none" {
            return "none"
        }
        guard let m = CSSTransform.parse(text, box: box) else {
            return text
        }
        return "matrix(" + [m.a, m.b, m.c, m.d, m.tx, m.ty].map { String(format: "%g", $0 == 0 ? 0 : Double($0)) }.joined(separator: ", ") + ")"
    }

    private static func computedDefault(_ property: String) -> String {
        switch property {
        case let p where p.hasPrefix("padding") || p.hasPrefix("margin") || (p.hasPrefix("border") && p.hasSuffix("width")):
            return "0px"
        case "max-width", "max-height":
            return "none"
        case "box-sizing":
            return "content-box"
        default:
            return ""
        }
    }

    private func matchMedia(_ query: String) -> Bool {
        let q = query.lowercased().replacingOccurrences(of: " ", with: "")
        if q.contains("prefers-color-scheme:dark") {
            return theme == "dark"
        }
        if q.contains("prefers-color-scheme:light") {
            return theme != "dark"
        }
        if q.contains("prefers-reduced-motion:reduce") {
            return NSWorkspace.shared.accessibilityDisplayShouldReduceMotion
        }
        if let range = q.range(of: "min-width:"), let value = Double(q[range.upperBound...].prefix { $0.isNumber || $0 == "." }) {
            return Double(width) >= value
        }
        if let range = q.range(of: "max-width:"), let value = Double(q[range.upperBound...].prefix { $0.isNumber || $0 == "." }) {
            return Double(width) <= value
        }
        return false
    }

    private func measureText(_ full: String, font: String) -> [String: Any] {
        let text = String(full.prefix(4096))
        let ctFont = CSSFont.make(String(font.prefix(512)))
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: [NSAttributedString.Key(kCTFontAttributeName as String): ctFont]))
        var ascent: CGFloat = 0, descent: CGFloat = 0, leading: CGFloat = 0
        let width = CTLineGetTypographicBounds(line, &ascent, &descent, &leading)
        let ink = CTLineGetBoundsWithOptions(line, .useGlyphPathBounds)
        return [
            "width": width,
            "actualBoundingBoxLeft": Double(-ink.minX),
            "actualBoundingBoxRight": Double(ink.maxX),
            "actualBoundingBoxAscent": Double(ink.maxY),
            "actualBoundingBoxDescent": Double(-ink.minY),
            "fontBoundingBoxAscent": Double(CTFontGetAscent(ctFont)),
            "fontBoundingBoxDescent": Double(CTFontGetDescent(ctFont)),
        ]
    }

    private func target(at point: CGPoint) -> Int {
        for hit in compositor.hitCandidates(for: point) {
            if let value = document?.attribute(WidgetDOM.idAttribute, at: hit.point, withinAttribute: WidgetDOM.idAttribute, value: String(hit.id)), let id = Int(value) {
                return id
            }
        }
        guard let value = document?.attribute(WidgetDOM.idAttribute, at: point), let id = Int(value) else {
            return dom.publicId(dom.html)
        }
        return id
    }

    private func freeze(reason: String, fatal: Bool) {
        if frozen {
            return
        }
        frozen = true
        smoothScrolls.removeAll()
        pendingScrollRequests.removeAll()
        pointerCaptureId = 0
        pressId = 0
        form.rangeId = 0
        endEditing()
        if magic.isActive {
            magic.cancel()
            applyExclusions()
        }
        frameHoldStart = nil
        frameHoldPosted = nil
        frameHoldRender = false
        releaseTickRender()
        clock?.stop()
        frameRequested = false
        pendingMoves.removeAll()
        if Thread.isMainThread {
            documentView.showBadge(reason, fatal: fatal)
        } else {
            DispatchQueue.main.async { [weak documentView] in
                documentView?.showBadge(reason, fatal: fatal)
            }
        }
        placeSurfaces()
    }

    func keyChanged() {
        let active = documentView.windowState.key
        guard active != controlsActive else {
            return
        }
        controlsActive = active
        document?.controlsActive = active
        documentView.invalidate(nil)
        setNeedsRender()
    }

    func screenChanged() {
        guard let number = documentView.windowState.display else {
            return
        }
        display = number
        if let display = display {
            clock?.setDisplay(display)
        }
        reportSize()
        placeSurfaces()
        setNeedsRender()
    }

    func backingPropertiesChanged() {
        setNeedsRender()
    }

    static var environmentCoalesce: TimeInterval = ProcessInfo.processInfo.environment["TW_ENV_COALESCE"].flatMap { Double($0) } ?? 0.1
    private var environmentPending = false
    public private(set) var environmentRefreshes = 0

    func environmentChanged() {
        guard !environmentPending else {
            return
        }
        environmentPending = true
        scheduleAfter(LiteHTMLWidgetRenderer.environmentCoalesce) { [weak self] in
            guard let self = self, !self.destroyed else {
                return
            }
            self.environmentPending = false
            self.environmentRefreshes += 1
            self.screenChanged()
            self.documentView.scaleChanged()
            self.setNeedsRender()
        }
    }

    private var display: CGDirectDisplayID?

    private var hostVisible = true

    var isHostVisible: Bool {
        return hostVisible
    }

    public static var hiddenTrimDelay: TimeInterval = 2

    public static var scriptFrameBudget: TimeInterval = ScriptFramePacer.defaultBudget
    public var scriptFrameBudget: TimeInterval? {
        didSet {
            pacer.budget = scriptFrameBudget ?? LiteHTMLWidgetRenderer.scriptFrameBudget
        }
    }
    public var scriptFrameCost: TimeInterval {
        return pacer.cost
    }
    public private(set) var scriptFramesPaced = 0
    public private(set) var framesPosted = 0
    private lazy var pacer = ScriptFramePacer(budget: LiteHTMLWidgetRenderer.scriptFrameBudget, fallbackInterval: LiteHTMLWidgetRenderer.frameInterval)

    private var trimWork: DispatchWorkItem?

    private var keepsTilesValue = false

    public var keepsTilesWhileHidden: Bool {
        get {
            return keepsTilesValue
        }
        set {
            guard isOnExecutor() else {
                return scheduleUrgent { [weak self] in self?.keepsTilesWhileHidden = newValue }
            }
            if keepsTilesValue != newValue {
                keepsTilesValue = newValue
                updateTileTrim()
            }
        }
    }

    private func updateTileTrim() {
        if isOnScreen || destroyed || keepsTilesWhileHidden || preparesOffscreen {
            trimWork?.cancel()
            trimWork = nil
            if !destroyed {
                documentView.restoreTiles()
            }
            return
        }
        guard trimWork == nil else {
            return
        }
        let work = DispatchWorkItem { [weak self] in
            guard let self = self else {
                return
            }
            self.trimWork = nil
            if !self.destroyed && !self.isOnScreen && !self.keepsTilesWhileHidden && !self.preparesOffscreen {
                self.documentView.trimTiles()
            }
        }
        trimWork = work
        scheduleAfter(LiteHTMLWidgetRenderer.hiddenTrimDelay) {
            if !work.isCancelled {
                work.perform()
            }
        }
    }

    public func setVisible(_ visible: Bool) {
        guard isOnExecutor() else {
            return scheduleUrgent { [weak self] in self?.setVisible(visible) }
        }
        hostVisible = visible
        if !visible {
            endEditing()
            documentView.dropSpares()
        }
        updateAnimationClock()
        updateTileTrim()
        schedulePrepare()
        placeSurfaces()
        if !isOnScreen {
            compositor.undrive()
        } else if !compositor.ids.isEmpty && animator.isActive && !frozen {
            setNeedsRender()
        }
        if isOnScreen && (needsFrames || frameRequested || !pendingMoves.isEmpty || !smoothScrolls.isEmpty) && !frozen {
            if needsFrames {
                renderAnimationFrame()
            }
            startClock()
        }
        if isOnScreen && documentView.detached {
            documentView.scheduleFlush()
        }
    }

    var isOnScreen: Bool {
        guard hostVisible else {
            return false
        }
        let state = documentView.windowState
        guard state.inWindow, !state.hidden else {
            return false
        }
        return state.occlusionTrusted ? state.occlusionVisible : state.windowVisible
    }

    private func startClock() {
        if clock == nil {
            clock = WidgetFrameClock(post: { [weak self] block in self?.schedule(block) }) { [weak self] in
                self?.tick()
            }
            if let display = display {
                clock?.setDisplay(display)
            }
        }
        clock?.start()
    }

    public private(set) var ticks = 0

    private func tick() {
        ticks += 1
        updateAnimationClock()
        if !isOnScreen {
            releaseFrameHold()
            releaseTickRender()
            clock?.stop()
            return
        }
        if !frozen { advanceSmoothScrolls() }
        if !pendingMoves.isEmpty {
            let moves = pendingMoves.values
            pendingMoves.removeAll()
            for event in moves {
                postEvent(event.event)
            }
        }
        if renderAtTick && !frozen {
            tickRenders += 1
            render()
        } else if needsFrames && !frozen && isOnScreen {
            renderAnimationFrame()
        }
        var worked = needsFrames || !smoothScrolls.isEmpty
        if frameRequested {
            worked = true
            let now = CACurrentMediaTime()
            if (held.isEmpty || answersInput) && pacer.due(now) {
                frameRequested = false
                lastFramePost = now
                if frameHoldStart != nil && frameHoldPosted == nil {
                    frameHoldPosted = now
                    frameHoldAnswers = inputAwaitingAnswer ? 2 : 1
                }
                pacer.posted(now)
                postFrame(time: (now - timeOrigin) * 1000)
                framesPosted += 1
            } else {
                scriptFramesPaced += 1
            }
        }
        if let posted = frameHoldPosted, CACurrentMediaTime() - posted >= LiteHTMLWidgetRenderer.frameHoldAnswerWait {
            releaseFrameHold()
        }
        if frameHoldStart != nil {
            worked = true
        }
        idleTicks = worked ? 0 : idleTicks + 1
        if !frameRequested && pendingMoves.isEmpty && !needsFrames && smoothScrolls.isEmpty && idleTicks > 8 {
            releaseTickRender()
            clock?.stop()
        }
    }

    private func modifiers(_ flags: NSEvent.ModifierFlags) -> Int {
        return (flags.contains(.option) ? 1 : 0) | (flags.contains(.control) ? 2 : 0) | (flags.contains(.command) ? 4 : 0) | (flags.contains(.shift) ? 8 : 0)
    }

    private func mouseEvent(_ type: String, target: Int, point: CGPoint, movement: CGPoint, event: NSEvent, buttons: Int, button: Int = 0) -> String? {
        let box = elementBounds()[target] ?? .zero
        return LiteHTMLWidgetRenderer.mouseEventJSON(type, target: target, point: point, offset: CGPoint(x: point.x - box.minX, y: point.y - box.minY), movement: movement, buttons: buttons, detail: type.hasSuffix("click") ? clickCount : 0, modifiers: modifiers(event.modifierFlags), defaultActions: type == "click", button: button)
    }

    static func mouseEventJSON(_ type: String, target: Int, point: CGPoint, offset: CGPoint, movement: CGPoint, buttons: Int, detail: Int, modifiers: Int, defaultActions: Bool, button: Int = 0) -> String? {
        var writer = WidgetJSONWriter(capacity: 384)
        writer.raw("{\"type\":")
        writer.string(type)
        writer.raw(",\"targetId\":")
        writer.integer(Int64(target))
        writer.raw(",\"clientX\":")
        guard writer.number(Double(point.x)) else { return nil }
        writer.raw(",\"clientY\":")
        guard writer.number(Double(point.y)) else { return nil }
        writer.raw(",\"offsetX\":")
        guard writer.number(Double(offset.x)) else { return nil }
        writer.raw(",\"offsetY\":")
        guard writer.number(Double(offset.y)) else { return nil }
        writer.raw(",\"pageX\":")
        guard writer.number(Double(point.x)) else { return nil }
        writer.raw(",\"pageY\":")
        guard writer.number(Double(point.y)) else { return nil }
        writer.raw(",\"movementX\":")
        guard writer.number(Double(movement.x)) else { return nil }
        writer.raw(",\"movementY\":")
        guard writer.number(Double(movement.y)) else { return nil }
        writer.raw(",\"button\":")
        writer.integer(Int64(button))
        writer.raw(",\"buttons\":")
        writer.integer(Int64(buttons))
        writer.raw(",\"detail\":")
        writer.integer(Int64(detail))
        writer.raw(",\"pointerId\":1,\"pointerType\":\"mouse\",\"isPrimary\":true,\"pressure\":")
        if buttons != 0 {
            writer.raw("0.5")
        } else {
            writer.raw("0")
        }
        writer.raw(",\"modifiers\":")
        writer.integer(Int64(modifiers))
        if defaultActions {
            writer.raw(",\"defaultActions\":true")
        }
        writer.raw("}")
        return writer.string
    }

    private func dispatch(_ type: String, target: Int, point: CGPoint, event: NSEvent, buttons: Int, button: Int = 0) {
        noteUserInput()
        if frozen || target == 0 {
            return
        }
        if !LiteHTMLWidgetRenderer.alwaysSent.contains(type) && !listened.contains(type) {
            return
        }
        let coalesced = LiteHTMLWidgetRenderer.coalesced.contains(type)
        let key = type + ":1"
        var movement = lastPointer.map { CGPoint(x: point.x - $0.x, y: point.y - $0.y) } ?? .zero
        if coalesced, let pending = pendingMoves[key] {
            movement.x += pending.movement.x
            movement.y += pending.movement.y
        }
        guard let payload = mouseEvent(type, target: target, point: point, movement: movement, event: event, buttons: buttons, button: button) else {
            return
        }
        if coalesced {
            pendingMoves.set(PendingMove(event: payload, movement: movement), for: key)
            startClock()
            return
        }
        if !pendingMoves.isEmpty {
            let moves = pendingMoves.values
            pendingMoves.removeAll()
            for move in moves {
                postEvent(move.event)
            }
        }
        postEvent(payload)
    }

    func handleScroll(_ point: CGPoint, event: NSEvent) -> Bool {
        noteUserInput()
        if frozen {
            return false
        }
        let outcome = scrolling.scroll(event: event, at: point, document: document, scale: documentView.scale, attribute: WidgetDOM.idAttribute)
        switch outcome {
        case .ignored:
            return false
        case .unchanged:
            smoothScrolls.removeAll()
            return true
        case let .moved(id):
            smoothScrolls.removeAll()
            scrolled(id)
            return true
        }
    }

    public func containsHorizontalScrollRegion(at point: CGPoint) -> Bool {
        return documentView.windowState.horizontalScrollRegions.contains { $0.contains(point) }
    }

    public func containsVerticalScrollRegion(at point: CGPoint) -> Bool {
        return documentView.windowState.verticalScrollRegions.contains { $0.contains(point) }
    }

    private func publishScrollRegions() {
        let horizontal = scrolling.hitViewports.compactMap { id, rect in
            scrolling.horizontalWheelIDs.contains(id) && scrolling.geometries[id].map { $0.maxOffset.x > 0.5 } == true ? rect : nil
        }
        let vertical = scrolling.hitViewports.compactMap { id, rect in
            scrolling.verticalWheelIDs.contains(id) && scrolling.geometries[id].map { $0.maxOffset.y > 0.5 } == true ? rect : nil
        }
        documentView.publishScrollRegions(Array(scrolling.hitViewports.values), horizontal: horizontal, vertical: vertical)
    }

    func scrolled(_ id: Int) {
        boundsValid = false
        if let document = document, scrolling.didScroll(id, dom: dom, document: document, scale: documentView.scale) {
            publishScrollRegions()
        }
        if compositor.invalidateScroll(id, dom: dom) {
            setNeedsRender()
        }
        if form.focusedId != 0 {
            syncEditor()
        }
        if scrolling.layers[id] == nil, let geometry = scrolling.geometries[id] {
            documentView.invalidate(geometry.viewport.insetBy(dx: -1, dy: -1))
        }
        if listened.contains("scroll") && !frozen {
            pendingMoves.set(PendingMove(event: "{\"type\":\"scroll\",\"targetId\":\(id),\"bubbles\":false}", movement: .zero), for: "scroll:\(id)")
            startClock()
        }
    }

    private func setProperty(_ node: WidgetNode, _ name: String, _ value: Any) {
        if name == "value" {
            formValueSet(node)
        }
        guard name == "scrollTop" || name == "scrollLeft", let number = (value as? NSNumber)?.doubleValue, number.isFinite else {
            return
        }
        let id = dom.publicId(node)
        requestScroll(.position(id, left: name == "scrollLeft" ? CGFloat(number) : nil, top: name == "scrollTop" ? CGFloat(number) : nil, relative: false))
    }

    private enum ScrollRequest {
        case reveal(Int, [Any])
        case position(Int, left: CGFloat?, top: CGFloat?, relative: Bool)
    }
    private var pendingScrollRequests: [ScrollRequest] = []
    private struct SmoothScroll {
        var from: CGPoint
        var to: CGPoint
        var start: CFTimeInterval
    }
    private var smoothScrolls: [Int: SmoothScroll] = [:]

    private func requestScroll(_ request: ScrollRequest) {
        if pendingGeometry || needsRender || renderAtTick || document == nil || !pendingScrollRequests.isEmpty {
            pendingScrollRequests.append(request)
        } else {
            applyScrollRequest(request)
        }
    }

    private func applyPendingScrollRequests() {
        guard !pendingScrollRequests.isEmpty, document != nil else { return }
        let requests = pendingScrollRequests
        pendingScrollRequests.removeAll()
        scrolling.beginScrollUpdates()
        defer {
            if scrolling.endScrollUpdates(dom: dom, document: document) {
                publishScrollRegions()
            }
        }
        for request in requests {
            if case .reveal = request {
                if scrolling.endScrollUpdates(dom: dom, document: document) {
                    publishScrollRegions()
                }
                applyScrollRequest(request)
                scrolling.beginScrollUpdates()
            } else {
                applyScrollRequest(request)
            }
        }
    }

    private func applyScrollRequest(_ request: ScrollRequest) {
        switch request {
        case let .position(id, left, top, relative):
            guard let node = dom.node(id), dom.isAttached(node), scrolling.geometries[id] != nil else { return }
            let current = scrolling.offset(id)
            let offset = CGPoint(x: left.map { $0 + (relative ? current.x : 0) } ?? current.x, y: top.map { $0 + (relative ? current.y : 0) } ?? current.y)
            scrollProgrammatically(id, offset)
        case let .reveal(id, args):
            guard let document = document, let node = dom.node(id), dom.isAttached(node), var target = elementBounds()[id] else { return }
            let info = document.scrollers(byAttribute: WidgetDOM.idAttribute)
            let block = (args.first as? NSNumber)?.intValue ?? 0
            let inline = (args.count > 1 ? args[1] as? NSNumber : nil)?.intValue ?? 3
            let smooth = (args.count > 2 ? args[2] as? NSNumber : nil)?.intValue == 2
            let nearest = (args.count > 3 ? args[3] as? NSNumber : nil)?.intValue == 1
            var walk = node.parent
            while let parent = walk {
                let scroller = dom.publicId(parent)
                if var geometry = scrolling.geometries[scroller], let viewport = (info[String(scroller)]?["viewport"] as? NSValue)?.rectValue {
                    geometry.viewport = viewport
                    let current = scrolling.offset(scroller)
                    let next = geometry.offsetToReveal(target, from: current, block: block, inline: inline)
                    scrollProgrammatically(scroller, next, smooth: smooth)
                    target = target.offsetBy(dx: current.x - next.x, dy: current.y - next.y)
                    if nearest { break }
                }
                walk = parent.parent
            }
        }
    }

    private func advanceSmoothScrolls() {
        let now = CACurrentMediaTime()
        for (id, animation) in smoothScrolls {
            guard scrolling.geometries[id] != nil else {
                smoothScrolls[id] = nil
                continue
            }
            let progress = min(1, max(0, (now - animation.start) / 0.3))
            let eased = CGFloat(progress * progress * (3 - 2 * progress))
            let next = CGPoint(x: animation.from.x + (animation.to.x - animation.from.x) * eased, y: animation.from.y + (animation.to.y - animation.from.y) * eased)
            applyScrollOffset(id, next)
            if progress == 1 { smoothScrolls[id] = nil }
        }
    }

    private func scrollProgrammatically(_ id: Int, _ offset: CGPoint, smooth: Bool = false) {
        smoothScrolls[id] = nil
        if smooth, !frozen, let geometry = scrolling.geometries[id] {
            let from = scrolling.offset(id)
            let to = geometry.clamp(offset)
            if from != to {
                smoothScrolls[id] = SmoothScroll(from: from, to: to, start: CACurrentMediaTime())
                startClock()
            }
        } else {
            applyScrollOffset(id, offset)
        }
    }

    private func applyScrollOffset(_ id: Int, _ offset: CGPoint) {
        let needsLayer = scrolling.needsLayerForFirstScroll(id)
        let changed = scrolling.setOffset(id, offset, document: document)
        if needsLayer {
            setNeedsRender()
        }
        if changed {
            if let layer = scrolling.layers[id], let document = document {
                layer.ensureTiles(document: document, scale: documentView.scale, attribute: WidgetDOM.idAttribute)
            }
            scrolled(id)
            if id == form.focusedId {
                let generation = form.generation
                let applied = scrolling.offset(id)
                let view = documentView
                DispatchQueue.main.async { [weak view] in
                    guard let editor = view?.formEditor, editor.generation == generation else { return }
                    editor.setScrollOffset(applied)
                }
            }
        }
    }

    private func setPointerCapture(_ id: Int) {
        guard pointerCaptureId != id else { return }
        let previous = pointerCaptureId
        pointerCaptureId = id
        for (type, target) in [("lostpointercapture", previous), ("gotpointercapture", id)] where target != 0 {
            post(["type": "event", "ev": ["type": type, "targetId": target, "pointerId": 1, "pointerType": "mouse", "isPrimary": true]])
        }
    }

    private func pointerTarget(at point: CGPoint) -> Int {
        if pointerCaptureId != 0 {
            if let node = dom.node(pointerCaptureId), dom.isAttached(node) { return pointerCaptureId }
            setPointerCapture(0)
        }
        return target(at: point)
    }

    func handleMouseDown(_ point: CGPoint, event: NSEvent) {
        guard !frozen, !destroyed else { return }
        setPointerCapture(0)
        let id = target(at: point)
        pressId = id
        clickCount = max(1, event.clickCount)
        lastClick = (CACurrentMediaTime(), point)
        resizeClickY = point.y
        resizePressId = id
        let claim = gestureClaim(id)
        post(["type": "gesture", "wants": claim.y, "x": claim.x, "y": claim.y, "hold": claim.hold])
        _ = gestures.down(at: LiteHTMLWidgetRenderer.gestureTime(), x: Double(point.x), y: Double(point.y), claim: claim, pointer: .mouse)
        if document?.mouseDown(point) == true {
            setNeedsRender()
        }
        dispatch("pointerdown", target: id, point: point, event: event, buttons: 1)
        dispatch("mousedown", target: id, point: point, event: event, buttons: 1)
        lastPointer = point
        formMouseDown(id, point: point, event: event)
    }

    func handleMouseDragged(_ point: CGPoint, event: NSEvent) {
        _ = gestures.move(at: LiteHTMLWidgetRenderer.gestureTime(), x: Double(point.x), y: Double(point.y))
        dispatch("pointermove", target: pressId != 0 ? pointerTarget(at: point) : 0, point: point, event: event, buttons: 1)
        dispatch("mousemove", target: target(at: point), point: point, event: event, buttons: 1)
        dragRange(to: point)
        dragEditorSelection(to: point, event: event)
        lastPointer = point
    }

    func handleMouseUp(_ point: CGPoint, event: NSEvent) {
        let id = target(at: point)
        if document?.mouseUp(point) == true {
            setNeedsRender()
        }
        let verdict = gestures.up(at: LiteHTMLWidgetRenderer.gestureTime(), x: Double(point.x), y: Double(point.y))
        if verdict?.event == .dblclick {
            clickCount = max(2, clickCount)
        }
        dispatch("pointerup", target: pointerTarget(at: point), point: point, event: event, buttons: 0)
        dispatch("mouseup", target: id, point: point, event: event, buttons: 0)
        if id == pressId || isAncestor(pressId, of: id) || isAncestor(id, of: pressId) {
            let clickTarget = isAncestor(id, of: pressId) ? id : (isAncestor(pressId, of: id) ? pressId : id)
            dispatch("click", target: clickTarget, point: point, event: event, buttons: 0)
            toggleDetails(from: clickTarget)
            if verdict?.event == .dblclick {
                dispatch("dblclick", target: clickTarget, point: point, event: event, buttons: 0)
            }
        }
        finishRange(at: point)
        finishEditorSelection()
        setPointerCapture(0)
        pressId = 0
        lastPointer = point
    }

    private func toggleDetails(from id: Int) {
        var walk = dom.node(id)
        while let node = walk, node.tag.lowercased() != "summary" {
            if prevented.contains("\(dom.publicId(node)):click") {
                return
            }
            walk = node.parent
        }
        guard let summary = walk, !prevented.contains("\(dom.publicId(summary)):click"),
              let details = summary.parent, details.tag.lowercased() == "details",
              details.children.first(where: { !$0.isText && $0.tag.lowercased() == "summary" }) === summary else {
            return
        }
        let detailsId = dom.publicId(details)
        let opening = details.attribute("open") == nil
        send(ops: [opening ? WidgetOp(attribute: detailsId, name: "open", value: "") : WidgetOp(removeAttribute: detailsId, name: "open")])
        post(["type": "event", "ev": ["type": "toggle", "targetId": detailsId, "open": opening]])
    }

    func handleMouseMoved(_ point: CGPoint, event: NSEvent) {
        let id = target(at: point)
        if document?.mouseMoved(point) == true {
            setNeedsRender()
        }
        if id != hoverId {
            let previous = hoverId
            hoverId = id
            if previous != 0 {
                dispatch("pointerout", target: previous, point: point, event: event, buttons: 0)
                dispatch("mouseout", target: previous, point: point, event: event, buttons: 0)
                dispatch("pointerleave", target: previous, point: point, event: event, buttons: 0)
                dispatch("mouseleave", target: previous, point: point, event: event, buttons: 0)
            }
            dispatch("pointerover", target: id, point: point, event: event, buttons: 0)
            dispatch("mouseover", target: id, point: point, event: event, buttons: 0)
            dispatch("pointerenter", target: id, point: point, event: event, buttons: 0)
            dispatch("mouseenter", target: id, point: point, event: event, buttons: 0)
        }
        dispatch("pointermove", target: id, point: point, event: event, buttons: 0)
        dispatch("mousemove", target: id, point: point, event: event, buttons: 0)
        lastPointer = point
    }

    func handleMouseExited(_ event: NSEvent) {
        if document?.mouseExited() == true {
            setNeedsRender()
        }
        if hoverId != 0, let point = lastPointer {
            dispatch("pointerleave", target: hoverId, point: point, event: event, buttons: 0)
            dispatch("mouseleave", target: hoverId, point: point, event: event, buttons: 0)
        }
        hoverId = 0
    }

    func handleKey(_ type: String, event: WidgetKeyEvent) {
        noteUserInput()
        form.lastActivation = CACurrentMediaTime()
        if frozen {
            return
        }
        let id = keyTarget() ?? dom.publicId(dom.body)
        let key = event.key
        if LiteHTMLWidgetRenderer.alwaysSent.contains(type) || listened.contains(type) {
            post(["type": "event", "trusted": true, "ev": ["type": type, "targetId": id, "key": key, "code": event.code, "repeat": event.isARepeat, "modifiers": modifiers(event.modifierFlags)]])
        }
        selectKey(type, key: key, event: event)
    }

    private func gestureClaim(_ id: Int) -> WidgetGestureClaim {
        var touchActions: [String] = []
        var node = dom.node(id)
        while let current = node {
            let style = document?.computedStyle(attribute: WidgetDOM.idAttribute, value: String(dom.publicId(current))) ?? [:]
            touchActions.append(style["touch-action"] ?? "auto")
            node = current.parent
        }
        return GesturePolicy.claimOf(touchActions, prevented: prevented.contains("\(id):pointerdown") || prevented.contains("\(id):touchstart"))
    }

    private static func gestureTime() -> Double {
        return CACurrentMediaTime() * 1000
    }

    func handleMenuClick(_ point: CGPoint, event: NSEvent) -> Bool {
        guard !frozen, !destroyed else { return false }
        let id = target(at: point)
        let verdict = gestures.down(at: LiteHTMLWidgetRenderer.gestureTime(), x: Double(point.x), y: Double(point.y), claim: gestureClaim(id), pointer: .mouse, button: 2)
        gestures.cancel()
        guard verdict.event == .contextmenu else { return false }
        dispatch("contextmenu", target: id, point: point, event: event, buttons: 2, button: 2)
        return true
    }

    private func isAncestor(_ ancestor: Int, of id: Int) -> Bool {
        guard ancestor != 0, var node = dom.node(id) else {
            return false
        }
        while let parent = node.parent {
            if dom.publicId(parent) == ancestor {
                return true
            }
            node = parent
        }
        return false
    }
}

class WidgetDocumentView: NSView {
    weak var owner: LiteHTMLWidgetRenderer?
    var formEditor: WidgetTextEditor?
    let textSelection = WidgetTextSelection()
    var document: LHDocument? {
        didSet {
            if Thread.isMainThread {
                textSelection.revalidate(document)
            } else {
                DispatchQueue.main.async { [weak self] in
                    self?.textSelection.revalidate(self?.document)
                }
            }
        }
    }
    private let rasterLifetime = WidgetRasterLifetime()
    private var tracking: NSTrackingArea?

    override var isFlipped: Bool {
        return true
    }

    override var acceptsFirstResponder: Bool {
        return false
    }

    override init(frame: NSRect) {
        super.init(frame: frame)
        wantsLayer = true
        layerUsesCoreImageFilters = true
        layerContentsRedrawPolicy = .onSetNeedsDisplay
        lockedWindowState.size = frame.size
        drawer.view = self
        content.anchorPoint = .zero
        content.frame = CGRect(origin: .zero, size: frame.size)
        content.autoresizingMask = [.layerWidthSizable, .layerHeightSizable]
        content.actions = ["bounds": NSNull(), "position": NSNull(), "contents": NSNull(), "sublayers": NSNull()]
        layer?.twAddSublayer(content)
        textSelection.layer.frame = CGRect(origin: .zero, size: frame.size)
        textSelection.layer.autoresizingMask = [.layerWidthSizable, .layerHeightSizable]
        layer?.twAddSublayer(textSelection.layer)
    }

    private let content = CALayer()
    private let alignmentLock = NSLock()
    private var anchorHistory: [(height: CGFloat, anchor: CGFloat, time: CFTimeInterval)] = []
    private var anchorTotal: CGFloat = 0
    private var presentedAnchor: CGFloat = 0
    private var presentedHeight: CGFloat = 0
    private var resizeInstallsUntil: CFTimeInterval = 0
    private var retiredGridLayers: [CALayer] = []

    var contentLayer: CALayer? {
        return content
    }

    func noteAnchorShift(_ shift: CGFloat, height: CGFloat) {
        alignmentLock.lock()
        let now = CACurrentMediaTime()
        if anchorHistory.count >= 2, abs(anchorHistory[anchorHistory.count - 2].height - height) < 0.5, now - anchorHistory[anchorHistory.count - 2].time < 0.05 {
            anchorTotal = anchorHistory[anchorHistory.count - 2].anchor
        } else {
            anchorTotal += shift
        }
        anchorHistory.append((height, anchorTotal, now))
        if anchorHistory.count > 32 {
            anchorHistory.removeFirst(anchorHistory.count - 32)
        }
        alignmentLock.unlock()
    }

    private func anchor(forHeight height: CGFloat) -> CGFloat? {
        return anchorHistory.last(where: { abs($0.height - height) < 0.5 })?.anchor
    }

    private final class InstallGate {
        private let lock = NSLock()
        private var claimed = false
        let finished = DispatchSemaphore(value: 0)

        func claim() -> Bool {
            lock.lock()
            defer { lock.unlock() }
            if claimed {
                return false
            }
            claimed = true
            return true
        }
    }

    static let installOnMainTimeout: TimeInterval = 0.05
    static let resizeInstallWindow: CFTimeInterval = 0.25

    static func beginInstallTransaction() -> Bool {
        if Thread.isMainThread && WidgetDocumentView.joinsMainCommit {
            return false
        }
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        return true
    }

    static let joinsMainCommit = ProcessInfo.processInfo.environment["TW_INSTALL_OWN_COMMIT"] == nil
    static let gateDisabled = ProcessInfo.processInfo.environment["TW_NO_GATE"] != nil

    func scheduleInstall(height: CGFloat, schedule: @escaping (@escaping () -> Void) -> Void, _ body: @escaping () -> Void) {
        alignmentLock.lock()
        let now = CACurrentMediaTime()
        if presentedHeight > 0 && abs(height - presentedHeight) >= 0.5 {
            resizeInstallsUntil = now + WidgetDocumentView.resizeInstallWindow
        }
        let changesHeight = now < resizeInstallsUntil
        alignmentLock.unlock()
        guard changesHeight, !WidgetDocumentView.gateDisabled else {
            schedule(body)
            return
        }
        schedule {
            if Thread.isMainThread {
                body()
                return
            }
            let gate = InstallGate()
            DispatchQueue.main.async {
                if gate.claim() {
                    body()
                }
                gate.finished.signal()
            }
            if gate.finished.wait(timeout: .now() + WidgetDocumentView.installOnMainTimeout) == .timedOut {
                if gate.claim() {
                    body()
                } else {
                    gate.finished.wait()
                }
            }
        }
    }

    private func layoutContent(viewSize: CGSize) {
        var offset: CGFloat = 0
        if presentedHeight > 0, abs(viewSize.height - presentedHeight) >= 0.5, let viewAnchor = anchor(forHeight: viewSize.height) {
            offset = viewAnchor - presentedAnchor
        }
        let frame = CGRect(origin: CGPoint(x: 0, y: offset), size: viewSize)
        if content.frame != frame {
            content.frame = frame
        }
    }

    var drawsSynchronously = false

    func presentAlignmentIfDrawnSynchronously(_ document: LHDocument) {
        if drawsSynchronously {
            drawsSynchronously = false
            presentAlignment(for: document)
        }
    }

    var alignmentDebug: String {
        alignmentLock.lock()
        defer { alignmentLock.unlock() }
        return String(format: "view %.0f presented %.0f origin %.0f pres %.0f", size.height, presentedHeight, content.frame.origin.y, content.presentation()?.frame.origin.y ?? -999)
    }

    func hasPresented(height: CGFloat) -> Bool {
        alignmentLock.lock()
        defer { alignmentLock.unlock() }
        return abs(presentedHeight - height) < 0.5
    }

    func presentAlignment(for document: LHDocument) {
        presentAlignment(height: ceil(document.size.height))
    }

    func presentAlignment(height: CGFloat) {
        for layer in retiredGridLayers {
            layer.twRemoveFromSuperlayer()
        }
        retiredGridLayers.removeAll()
        alignmentLock.lock()
        presentedHeight = height
        presentedAnchor = anchor(forHeight: height) ?? anchorTotal
        layoutContent(viewSize: size)
        alignmentLock.unlock()
    }

    required init?(coder: NSCoder) {
        fatalError()
    }

    override func setFrameSize(_ newSize: NSSize) {
        let widthChanged = newSize.width != frame.width
        let heightChanged = newSize.height != frame.height
        super.setFrameSize(newSize)
        captureSize(newSize)
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        alignmentLock.lock()
        layoutContent(viewSize: newSize)
        alignmentLock.unlock()
        CATransaction.commit()
        if widthChanged || heightChanged {
            toOwner { $0.viewResized() }
        }
    }

    let drawStatsLock = NSLock()
    private var drawStats = (count: 0, total: 0.0, worst: 0.0)
    var drawMilliseconds: Double {
        drawStatsLock.lock()
        defer { drawStatsLock.unlock() }
        return drawStats.total
    }
    var drawCount: Int {
        drawStatsLock.lock()
        defer { drawStatsLock.unlock() }
        return drawStats.count
    }
    var drawMaxMilliseconds: Double {
        drawStatsLock.lock()
        defer { drawStatsLock.unlock() }
        return drawStats.worst
    }

    func recordDraw(_ spent: Double) {
        drawStatsLock.lock()
        drawStats.count += 1
        drawStats.total += spent
        drawStats.worst = max(drawStats.worst, spent)
        drawStatsLock.unlock()
    }
    private final class Surface {
        let surface: IOSurfaceRef
        let context: CGContext
        let width: Int
        let height: Int
        var stale = CGRect.null

        init?(width: Int, height: Int, space: CGColorSpace) {
            guard let surface = WidgetSurfacePool.shared.take(width: width, height: height) ?? WidgetSurfacePool.make(width: width, height: height) else {
                return nil
            }
            if let plist = space.copyPropertyList() {
                IOSurfaceSetValue(surface, kIOSurfaceColorSpace, plist)
            }
            IOSurfaceLock(surface, [], nil)
            let base = IOSurfaceGetBaseAddress(surface)
            let context = CGContext(data: base, width: width, height: height, bitsPerComponent: 8, bytesPerRow: IOSurfaceGetBytesPerRow(surface), space: space, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue)
            IOSurfaceUnlock(surface, [], nil)
            guard let made = context else {
                return nil
            }
            self.surface = surface
            self.context = made
            self.width = width
            self.height = height
        }
    }

    static let cullMargin: CGFloat = 32
    static let recycleSurfaces = ProcessInfo.processInfo.environment["TW_NO_SURFACE_RECYCLE"] == nil
    static let surfaceSpace = CGColorSpace(name: CGColorSpace.sRGB)!
    static let screenTagged = ProcessInfo.processInfo.environment["TW_SURFACE_SCREEN"] != nil
    static var verify = ProcessInfo.processInfo.environment["TW_VERIFY"] != nil
    private(set) var verifiedFrames = 0
    private(set) var mismatchedFrames = 0

    private func verifyFrame(_ surface: Surface, document: LHDocument, rect: CGRect, scale: CGFloat, space: CGColorSpace) {
        guard let fresh = document.copyTileImage(maxWidth: bounds.width, rect: rect, scale: scale), fresh.width == surface.width, fresh.height == surface.height,
              let context = CGContext(data: nil, width: surface.width, height: surface.height, bitsPerComponent: 8, bytesPerRow: surface.width * 4, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue) else {
            return
        }
        context.setBlendMode(.copy)
        context.draw(fresh, in: CGRect(x: 0, y: 0, width: surface.width, height: surface.height))
        IOSurfaceLock(surface.surface, .readOnly, nil)
        defer { IOSurfaceUnlock(surface.surface, .readOnly, nil) }
        guard let a = context.data, let b = IOSurfaceGetBaseAddress(surface.surface) as UnsafeMutableRawPointer? else {
            return
        }
        let rowA = context.bytesPerRow, rowB = IOSurfaceGetBytesPerRow(surface.surface)
        var worst = 0
        var worstAt = (0, 0)
        for y in 0..<surface.height {
            let pa = a.advanced(by: y * rowA).assumingMemoryBound(to: UInt8.self)
            let pb = b.advanced(by: y * rowB).assumingMemoryBound(to: UInt8.self)
            for x in 0..<(surface.width * 4) {
                let d = abs(Int(pa[x]) - Int(pb[x]))
                if d > worst {
                    worst = d
                    worstAt = (x / 4, y)
                }
            }
        }
        if worst > 1, ProcessInfo.processInfo.environment["TW_VERIFY_LOG"] != nil {
            print("VERIFYDBG rect=\(rect) worst=\(worst) at=\(worstAt)")
        }
        verifiedFrames += 1
        WidgetSVGLayers.tally(verified: 1, mismatched: 0)
        let tiled = rect.width < bounds.width - 0.5 || rect.height < max(1, ceil(document.size.height)) - 0.5
        if worst > (tiled ? 2 : 1) {
            mismatchedFrames += 1
            WidgetSVGLayers.tally(verified: 0, mismatched: 1)
        }
    }

    private final class TileDrawer: NSObject, CALayerDelegate {
        weak var view: WidgetDocumentView?

        func draw(_ layer: CALayer, in ctx: CGContext) {
            guard let view = view, let document = view.document else {
                return
            }
            let start = CACurrentMediaTime()
            let clip = ctx.boundingBoxOfClipPath
            let origin = layer.frame.origin
            let baseTransform = ctx.ctm
            ctx.saveGState()
            ctx.translateBy(x: -origin.x, y: -origin.y)
            ctx.setAllowsFontSmoothing(false)
            ctx.setShouldAntialias(true)
            let dirty = clip.offsetBy(dx: origin.x, dy: origin.y)
            let drawCPU = LiteHTMLWidgetRenderer.threadCPUSeconds()
            document.draw(in: ctx, origin: .zero, clip: dirty.insetBy(dx: -WidgetDocumentView.cullMargin, dy: -WidgetDocumentView.cullMargin), baseTransform: baseTransform)
            view.owner?.chargeTileDraw(LiteHTMLWidgetRenderer.threadCPUSeconds() - drawCPU)
            ctx.restoreGState()
            let spent = (CACurrentMediaTime() - start) * 1000
            view.recordDraw(spent)
        }

        func action(for layer: CALayer, forKey event: String) -> CAAction? {
            return NSNull()
        }
    }

    private let drawer = TileDrawer()

    private final class PreparedDisplay: NSObject, CALayerDelegate {
        weak var drawer: TileDrawer?

        func display(_ layer: CALayer) {
            if drawer?.view?.detached ?? false {
                return
            }
            layer.delegate = drawer
        }

        func action(for layer: CALayer, forKey event: String) -> CAAction? {
            return NSNull()
        }
    }

    private lazy var preparedDisplay: PreparedDisplay = {
        let display = PreparedDisplay()
        display.drawer = drawer
        return display
    }()

    static let prepareQueue = DispatchQueue(label: "twidget.tiles.prepare", qos: .userInitiated, attributes: .concurrent)
    private(set) var preparing = false
    private(set) var preparedTiles = 0
    private(set) var preparedMilliseconds = 0.0
    private var invalidations = 0
    private var syncInvalidations = 0
    private(set) var dirtyArea: CGFloat = 0
    private var tileScale: CGFloat = 0

    private static func surface(of layer: CALayer) -> IOSurfaceRef? {
        guard let contents = layer.contents else {
            return nil
        }
        let object = contents as CFTypeRef
        guard CFGetTypeID(object) == IOSurfaceGetTypeID() else {
            return nil
        }
        return (object as! IOSurfaceRef)
    }

    var hasUnpreparedTiles: Bool {
        return tilesTrimmed || tiles.isEmpty || tiles.contains { !$0.prepared && ($0.layer.contents == nil || $0.layer.needsDisplay()) }
    }

    func prepareTiles(synchronously: Bool = false, completion: @escaping () -> Void) -> Bool {
        guard rasterLifetime.isActive, WidgetDocumentView.layerTiles, !WidgetDocumentView.backingStore, !preparing, let layer = rootLayer, let document = document else {
            return false
        }
        tilesTrimmed = false
        let scale = self.scale
        let detached = self.detached
        let drawnHeight = ceil(document.size.height)
        guard syncTiles(layer: layer, document: document, scale: scale) else {
            return false
        }
        var jobs: [(index: Int, rect: CGRect)] = []
        for (index, tile) in tiles.enumerated() where !tile.prepared && (detached || tile.layer.contents == nil || tile.layer.needsDisplay()) {
            jobs.append((index, tile.rect))
        }
        if jobs.isEmpty {
            return false
        }
        if detached {
            for job in jobs {
                asyncDirty[job.index] = nil
                tiles[job.index].note("prepare-start clears dirty")
            }
        }
        preparing = true
        let epoch = invalidations
        let syncEpoch = syncInvalidations
        let lifetime = rasterLifetime
        let space = WidgetDocumentView.surfaceSpace
        let margin = WidgetDocumentView.cullMargin
        let schedule = owner?.scheduleUrgent
        let draw = { [weak self] in
            let start = CACurrentMediaTime()
            var made: [(index: Int, rect: CGRect, surface: IOSurfaceRef)] = []
            for job in jobs {
                guard lifetime.isActive else {
                    made.removeAll()
                    break
                }
                let rect = job.rect
                let width = max(1, Int(ceil(rect.width * scale)))
                let height = max(1, Int(ceil(rect.height * scale)))
                guard let surface = Surface(width: width, height: height, space: space) else {
                    made.removeAll()
                    break
                }
                let ctx = surface.context
                IOSurfaceLock(surface.surface, [], nil)
                ctx.saveGState()
                ctx.clear(CGRect(x: 0, y: 0, width: width, height: height))
                ctx.translateBy(x: 0, y: CGFloat(height))
                ctx.scaleBy(x: scale, y: -scale)
                ctx.translateBy(x: -rect.minX, y: -rect.minY)
                ctx.setAllowsFontSmoothing(false)
                ctx.setShouldAntialias(true)
                let drawCPU = LiteHTMLWidgetRenderer.threadCPUSeconds()
                let drawn = document.drawIfCurrent(in: ctx, origin: .zero, clip: rect.insetBy(dx: -margin, dy: -margin))
                if !synchronously {
                    self?.owner?.chargeTileDraw(LiteHTMLWidgetRenderer.threadCPUSeconds() - drawCPU)
                }
                ctx.restoreGState()
                ctx.flush()
                IOSurfaceUnlock(surface.surface, [], nil)
                guard drawn else {
                    made.removeAll()
                    break
                }
                made.append((job.index, rect, surface.surface))
            }
            let spent = (CACurrentMediaTime() - start) * 1000
            let finish = {
                guard let self = self else {
                    return
                }
                self.preparing = false
                self.preparedMilliseconds += spent
                if made.count == jobs.count && lifetime.isActive && self.document === document && ((detached && self.detached) || (self.invalidations == epoch && self.syncInvalidations == syncEpoch)) && !self.tilesTrimmed && self.tileScale == scale {
                    let explicit = WidgetDocumentView.beginInstallTransaction()
                    for item in made where item.index < self.tiles.count {
                        let tile = self.tiles[item.index]
                        guard tile.rect == item.rect, !tile.prepared else {
                            continue
                        }
                        if WidgetDocumentView.verifyPrepared {
                            WidgetDocumentView.checkPrepared(item.surface, document: document, rect: item.rect, width: self.size.width, scale: scale)
                        }
                        let previous = WidgetDocumentView.surface(of: tile.layer)
                        self.dropSpare(tile)
                        if tile.layer.frame != tile.rect {
                            tile.layer.frame = tile.rect
                            tile.layer.contentsScale = scale
                        }
                        tile.layer.contents = item.surface
                        tile.layer.delegate = self.preparedDisplay
                        tile.note("prepare-install")
                        if let previous = previous, WidgetDocumentView.recycleSurfaces {
                            WidgetSurfacePool.shared.recycle(previous)
                        }
                        tile.prepared = true
                        self.preparedTiles += 1
                    }
                    self.presentAlignment(height: drawnHeight)
                    if explicit {
                        CATransaction.commit()
                    }
                }
                completion()
                if detached && self.detached {
                    self.scheduleFlush()
                }
            }
            if synchronously {
                if let view = self {
                    view.scheduleInstall(height: drawnHeight, schedule: { $0() }, finish)
                } else {
                    finish()
                }
            } else if let schedule = schedule {
                if let view = self {
                    view.scheduleInstall(height: drawnHeight, schedule: schedule, finish)
                } else {
                    schedule(finish)
                }
            } else {
                DispatchQueue.main.async(execute: finish)
            }
        }
        if synchronously {
            draw()
        } else {
            WidgetDocumentView.prepareQueue.async(execute: draw)
        }
        return true
    }

    static let verifyPrepared = ProcessInfo.processInfo.environment["TW_VERIFY_PREPARE"] != nil
    static var preparedChecks = 0
    static var preparedWorstDiff = 0

    private static func checkPrepared(_ surface: IOSurfaceRef, document: LHDocument, rect: CGRect, width: CGFloat, scale: CGFloat, label: String = "prepare") {
        let w = IOSurfaceGetWidth(surface), h = IOSurfaceGetHeight(surface)
        guard let context = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4, space: surfaceSpace, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue) else {
            return
        }
        context.saveGState()
        context.translateBy(x: 0, y: CGFloat(h))
        context.scaleBy(x: scale, y: -scale)
        context.translateBy(x: -rect.minX, y: -rect.minY)
        context.setAllowsFontSmoothing(false)
        context.setShouldAntialias(true)
        document.draw(in: context, origin: .zero, clip: rect.insetBy(dx: -cullMargin, dy: -cullMargin))
        context.restoreGState()
        guard let fresh = context.makeImage() else {
            return
        }
        IOSurfaceLock(surface, .readOnly, nil)
        defer { IOSurfaceUnlock(surface, .readOnly, nil) }
        guard let a = context.data, let b = IOSurfaceGetBaseAddress(surface) as UnsafeMutableRawPointer? else {
            return
        }
        let rowA = context.bytesPerRow, rowB = IOSurfaceGetBytesPerRow(surface)
        var worst = 0
        var box = (x0: Int.max, y0: Int.max, x1: -1, y1: -1)
        for y in 0..<h {
            let pa = a.advanced(by: y * rowA).assumingMemoryBound(to: UInt8.self)
            let pb = b.advanced(by: y * rowB).assumingMemoryBound(to: UInt8.self)
            for x in 0..<(w * 4) {
                let d = abs(Int(pa[x]) - Int(pb[x]))
                worst = max(worst, d)
                if d > 2 {
                    box = (min(box.x0, x / 4), min(box.y0, y), max(box.x1, x / 4), max(box.y1, y))
                }
            }
        }
        if worst > 40, ProcessInfo.processInfo.environment["TW_VERIFY_PREPARE_DUMP"] != nil {
            print("PREPARE DIFF box=\(box) of \(w)x\(h)")
        }
        preparedChecks += 1
        preparedWorstDiff = max(preparedWorstDiff, worst)
        if worst > 2 {
            preparedMismatches += 1
            mismatchByLabel[label + (worst > 16 ? ">16" : "<=16"), default: 0] += 1
            if worst > 40 { bigMismatches += 1 }
            if worst > 40, bigMismatches <= 4, let dir = ProcessInfo.processInfo.environment["TW_VERIFY_PREPARE_DUMP"] {
                let rep = NSBitmapImageRep(cgImage: fresh)
                try? rep.representation(using: .png, properties: [:])?.write(to: URL(fileURLWithPath: dir + "/fresh\(bigMismatches).png"))
                if let mine = context.makeImage() {
                    _ = mine
                }
                let surfaceImage = CIImage(ioSurface: surface)
                let ci = CIContext()
                if let cg = ci.createCGImage(surfaceImage, from: surfaceImage.extent) {
                    try? NSBitmapImageRep(cgImage: cg).representation(using: .png, properties: [:])?.write(to: URL(fileURLWithPath: dir + "/surface\(bigMismatches).png"))
                }
                print("PREPARE MISMATCH \(bigMismatches) rect=\(rect) worst=\(worst) label=\(label)")
            }
        }
    }
    static var preparedMismatches = 0
    static var bigMismatches = 0
    static var mismatchByLabel: [String: Int] = [:]

    public static var preparedReport: String {
        return "prepared checks=\(preparedChecks) worst diff=\(preparedWorstDiff) mismatches=\(preparedMismatches) big=\(bigMismatches) \(mismatchByLabel)"
    }

    private func unprepare(_ tile: Tile) {
        tile.note("unprepare detached=\(detached)")
        syncInvalidations += 1
        dropSpare(tile)
        tile.prepared = false
        if detached {
            return
        }
        tile.layer.delegate = drawer
        tile.layer.setNeedsDisplay()
    }

    private var rootLayer: CALayer? {
        return contentLayer
    }
    private var flushScheduled = false
    private var updateDepth = 0
    private var synchronousHandoff = false

    func beginUpdates() {
        updateDepth += 1
    }

    func endUpdates() {
        updateDepth -= 1
        flushIfDetached()
    }

    func synchronizePaintHandoff() {
        guard detached else { return }
        synchronousHandoff = true
        invalidateDetached(nil)
    }

    func scheduleFlush() {
        guard !flushScheduled, let owner = owner else {
            return
        }
        flushScheduled = true
        owner.scheduleUrgent { [weak self] in
            guard let self = self else {
                return
            }
            self.flushScheduled = false
            self.flushDetached()
        }
    }

    var flushDebug: String {
        return "flushScheduled=\(flushScheduled) gridFails=\(gridFails) size=\(size) scale=\(scale)"
    }
    private var gridFails = 0

    func flushIfDetached() {
        if detached {
            flushDetached()
        }
    }

    private(set) var everPainted = false

    var tilesDrawn: Bool {
        guard let document = document, !tiles.isEmpty, !tilesTrimmed else {
            return false
        }
        let covered = tiles.reduce(CGFloat(0)) { max($0, $1.rect.maxY) }
        return covered >= ceil(document.size.height) - 0.5 && !tiles.contains(where: { $0.layer.contents == nil })
    }

    var paintedForReport: Bool {
        if !detached || everPainted {
            return true
        }
        if document != nil, !tiles.isEmpty, !tiles.contains(where: { $0.layer.contents == nil }) {
            everPainted = true
        }
        return everPainted
    }

    private func flushDetached() {
        guard updateDepth == 0, detached, document != nil, !tilesTrimmed, windowState.inWindow || owner?.preparesOffscreen == true || !everPainted else {
            return
        }
        if preparing {
            return
        }
        let synchronously = synchronousHandoff
        synchronousHandoff = false
        let started = prepareTiles(synchronously: synchronously) { [weak self] in
            if !synchronously { self?.owner?.rasterFinished() }
        }
        if (!started || synchronously) && !asyncDirty.isEmpty {
            rasterDirtyTiles(synchronously: synchronously)
        }
        if synchronously {
            owner?.scheduleUrgent { [weak self] in
                self?.owner?.rasterFinished()
            }
        }
    }

    private func invalidateDetached(_ rects: [CGRect]?) {
        invalidations += 1
        for (index, tile) in tiles.enumerated() where tile.prepared || preparing {
            if let rects = rects {
                for rect in rects where rect.intersects(tile.rect) {
                    asyncDirty[index] = (asyncDirty[index] ?? .null).union(rect.intersection(tile.rect))
                    tile.note("detached-dirty \(rect.intersection(tile.rect)) prepared=\(tile.prepared)")
                }
            } else {
                asyncDirty[index] = tile.rect
                tile.note("detached-dirty all")
            }
        }
        scheduleFlush()
    }

    func insertOverlay(_ overlay: CALayer) {
        rootLayer?.twInsertSublayer(overlay, at: UInt32(tiles.count))
    }
    static var layerTiles = ProcessInfo.processInfo.environment["TW_SURFACE_TILES"] == nil && ProcessInfo.processInfo.environment["TW_VERIFY"] == nil

    static let damageTrace = ProcessInfo.processInfo.environment["TW_DAMAGE_TRACE"] != nil
    static var damageMisses = 0

    var staleTileCount: Int {
        guard let document = document else {
            return 0
        }
        var count = 0
        for tile in tiles where tile.prepared {
            if let surface = WidgetDocumentView.surface(of: tile.layer), WidgetDocumentView.differs(surface, document: document, rect: tile.rect, dirty: .null, scale: scale) != nil {
                count += 1
            }
        }
        return count
    }

    private static func checkBase(_ surface: IOSurfaceRef, document: LHDocument, rect: CGRect, dirty: CGRect, scale: CGFloat, history: [String], label: String) {
        if let box = differs(surface, document: document, rect: rect, dirty: dirty, scale: scale) {
            damageMisses += 1
            print("DAMAGE MISS \(damageMisses) \(label) tile=\(rect) dirty=\(dirty) outside=\(box)")
            for line in history.suffix(12) {
                print("  | \(line)")
            }
        }
    }

    private static func differs(_ surface: IOSurfaceRef, document: LHDocument, rect: CGRect, dirty: CGRect, scale: CGFloat) -> (x0: Int, y0: Int, x1: Int, y1: Int)? {
        let w = IOSurfaceGetWidth(surface), h = IOSurfaceGetHeight(surface)
        guard let context = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4, space: surfaceSpace, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue) else {
            return nil
        }
        context.translateBy(x: 0, y: CGFloat(h))
        context.scaleBy(x: scale, y: -scale)
        context.translateBy(x: -rect.minX, y: -rect.minY)
        context.setAllowsFontSmoothing(false)
        context.setShouldAntialias(true)
        document.draw(in: context, origin: .zero, clip: rect.insetBy(dx: -cullMargin, dy: -cullMargin))
        IOSurfaceLock(surface, .readOnly, nil)
        defer { IOSurfaceUnlock(surface, .readOnly, nil) }
        guard let a = context.data, let b = IOSurfaceGetBaseAddress(surface) as UnsafeMutableRawPointer? else {
            return nil
        }
        let local = dirty.isNull ? CGRect.null : dirty.intersection(rect).offsetBy(dx: -rect.minX, dy: -rect.minY)
        let x0 = local.isNull ? 0 : Int(floor(local.minX * scale)), x1 = local.isNull ? 0 : Int(ceil(local.maxX * scale))
        let y0 = local.isNull ? 0 : Int(floor(local.minY * scale)), y1 = local.isNull ? 0 : Int(ceil(local.maxY * scale))
        let rowA = context.bytesPerRow, rowB = IOSurfaceGetBytesPerRow(surface)
        var box = (x0: Int.max, y0: Int.max, x1: -1, y1: -1)
        for y in 0..<h {
            let pa = a.advanced(by: y * rowA).assumingMemoryBound(to: UInt8.self)
            let pb = b.advanced(by: y * rowB).assumingMemoryBound(to: UInt8.self)
            for x in 0..<w where !(x >= x0 && x < x1 && y >= y0 && y < y1) {
                for c in 0..<4 where abs(Int(pa[x * 4 + c]) - Int(pb[x * 4 + c])) > 2 {
                    box = (min(box.x0, x), min(box.y0, y), max(box.x1, x), max(box.y1, y))
                }
            }
        }
        return box.x1 >= 0 ? box : nil
    }

    private final class Tile {
        var history: [String] = []
        func note(_ text: @autoclosure () -> String) {
            guard WidgetDocumentView.damageTrace else {
                return
            }
            history.append(String(format: "%.3f ", CACurrentMediaTime().truncatingRemainder(dividingBy: 1000)) + text())
            if history.count > 40 {
                history.removeFirst(history.count - 40)
            }
        }
        let layer = CALayer()
        var surfaces: [Surface] = []
        var shown: Surface?
        var rect = CGRect.null
        var pending = true
        var prepared = false
        var spare: IOSurfaceRef?
        var spareStale = CGRect.null
        var spareEpoch = 0
        var partialStreak = 0
        var lastPartial: CFTimeInterval = 0

        deinit {
            if let spare = spare {
                WidgetTileSpares.release(spare)
            }
        }
    }

    private func dropSpare(_ tile: Tile) {
        tile.partialStreak = 0
        guard let spare = tile.spare else {
            return
        }
        tile.spare = nil
        tile.spareStale = .null
        WidgetTileSpares.release(spare)
        if WidgetDocumentView.recycleSurfaces {
            WidgetSurfacePool.shared.recycle(spare)
        }
    }

    private func settleSpare(_ tile: Tile, previous: IOSurfaceRef?, item: (partial: Bool, dirty: CGRect, usedSpare: Bool)) {
        if item.usedSpare {
            WidgetTileSpares.noteReuse()
            if let previous = previous {
                tile.spare = previous
                tile.spareStale = item.dirty
                tile.spareEpoch = WidgetTileSpares.epoch
            } else {
                tile.spare = nil
                tile.spareStale = .null
            }
            tile.lastPartial = CACurrentMediaTime()
            scheduleSpareSweep()
            return
        }
        if !item.partial || !WidgetTileSpares.enabled {
            dropSpare(tile)
            if let previous = previous, WidgetDocumentView.recycleSurfaces {
                WidgetSurfacePool.shared.recycle(previous)
            }
            return
        }
        tile.partialStreak += 1
        tile.lastPartial = CACurrentMediaTime()
        if let old = tile.spare {
            tile.spare = nil
            WidgetTileSpares.release(old)
            if WidgetDocumentView.recycleSurfaces {
                WidgetSurfacePool.shared.recycle(old)
            }
        }
        guard let previous = previous else {
            return
        }
        if tile.partialStreak >= WidgetTileSpares.streakNeeded, WidgetTileSpares.reserve(previous) {
            tile.spare = previous
            tile.spareStale = item.dirty
            tile.spareEpoch = WidgetTileSpares.epoch
            scheduleSpareSweep()
        } else if WidgetDocumentView.recycleSurfaces {
            WidgetSurfacePool.shared.recycle(previous)
        }
    }

    func dropSpares() {
        for tile in tiles {
            dropSpare(tile)
        }
    }

    private var spareSweepScheduled = false

    private func scheduleSpareSweep() {
        guard !spareSweepScheduled, let owner = owner else {
            return
        }
        spareSweepScheduled = true
        owner.scheduleAfter(WidgetTileSpares.idleSeconds) { [weak self] in
            guard let self = self else {
                return
            }
            self.spareSweepScheduled = false
            let now = CACurrentMediaTime()
            var remaining = false
            for tile in self.tiles where tile.spare != nil {
                if now - tile.lastPartial > WidgetTileSpares.idleSeconds || tile.spareEpoch != WidgetTileSpares.epoch {
                    self.dropSpare(tile)
                } else {
                    remaining = true
                }
            }
            if remaining {
                self.scheduleSpareSweep()
            }
        }
    }

    private var tiles: [Tile] = []
    private var surfaceSpace: CGColorSpace?
    private var surfaceWidth = 0
    private var badgeLayer: CATextLayer?

    static let backingStore = ProcessInfo.processInfo.environment["TW_BACKING"] != nil
    static let eightBitTiles = ProcessInfo.processInfo.environment["TW_DEEP_TILES"] == nil
    static var tileHeight: CGFloat = ProcessInfo.processInfo.environment["TW_TILE"].flatMap { Double($0) }.map { CGFloat($0) } ?? 128

    override var wantsUpdateLayer: Bool {
        return !WidgetDocumentView.backingStore
    }

    static let asyncFullTiles = ProcessInfo.processInfo.environment["TW_ASYNC_FULL"] != nil
    static var asyncTiles = ProcessInfo.processInfo.environment["TW_SYNC_TILES"] == nil
    var asyncAllowed = false
    private var asyncDirty: [Int: CGRect] = [:]
    private var asyncCounts = (jobs: 0, installed: 0)
    var asyncTileJobs: Int {
        drawStatsLock.lock()
        defer { drawStatsLock.unlock() }
        return asyncCounts.jobs
    }
    var asyncTilesInstalled: Int {
        drawStatsLock.lock()
        defer { drawStatsLock.unlock() }
        return asyncCounts.installed
    }

    private var canInvalidateAsync: Bool {
        return asyncAllowed && WidgetDocumentView.asyncTiles && WidgetDocumentView.layerTiles && !WidgetDocumentView.backingStore && windowState.inWindow && !tilesTrimmed && !tiles.isEmpty && tileScale == scale
    }

    private func invalidateAsync(_ rects: [CGRect]) {
        invalidations += 1
        markNeedsDisplay()
        for rect in rects {
            for (index, tile) in tiles.enumerated() where rect.intersects(tile.rect) {
                asyncDirty[index] = (asyncDirty[index] ?? .null).union(rect.intersection(tile.rect))
                tile.note("async-dirty \(rect.intersection(tile.rect)) prepared=\(tile.prepared)")
            }
        }
        if !asyncDirty.isEmpty {
            owner?.schedule { [weak self] in
                self?.rasterDirtyTiles()
            }
        }
    }

    static func rasterDamage(_ dirty: CGRect, tile: CGRect, scale: CGFloat) -> CGRect {
        let local = dirty.intersection(tile).offsetBy(dx: -tile.minX, dy: -tile.minY)
        guard !local.isNull, !local.isEmpty else { return .null }
        let x0 = floor(local.minX * scale) / scale, y0 = floor(local.minY * scale) / scale
        let x1 = ceil(local.maxX * scale) / scale, y1 = ceil(local.maxY * scale) / scale
        return CGRect(x: tile.minX + x0, y: tile.minY + y0, width: x1 - x0, height: y1 - y0)
    }

    private func rasterDirtyTiles(synchronously: Bool = false) {
        guard rasterLifetime.isActive, !preparing, !asyncDirty.isEmpty, let document = document, windowState.inWindow, !tilesTrimmed else {
            return
        }
        let scale = self.scale
        let drawnHeight = ceil(document.size.height)
        var jobs: [(index: Int, rect: CGRect, dirty: CGRect, base: IOSurfaceRef?, spare: IOSurfaceRef?, spareStale: CGRect)] = []
        var reservations: [Int: WidgetTileSpares.Reservation] = [:]
        let epoch = WidgetTileSpares.epoch
        for (index, dirty) in asyncDirty where index < tiles.count {
            let tile = tiles[index]
            if detached ? !tile.prepared : tile.layer.needsDisplay() {
                if !detached, !dirty.isNull {
                    tile.layer.setNeedsDisplay(dirty.intersection(tile.rect).offsetBy(dx: -tile.rect.minX, dy: -tile.rect.minY))
                }
                continue
            }
            if tile.spare != nil && tile.spareEpoch != epoch {
                dropSpare(tile)
            }
            let base = tile.prepared && !WidgetDocumentView.asyncFullTiles ? WidgetDocumentView.surface(of: tile.layer) : nil
            if WidgetDocumentView.damageTrace, let base = base {
                WidgetDocumentView.checkBase(base, document: document, rect: tile.rect, dirty: dirty, scale: scale, history: tile.history, label: owner?.debugName ?? "?")
            }
            tile.note("job dirty=\(dirty) base=\(base != nil)")
            let spare = base == nil ? nil : tile.spare
            if let spare = spare {
                reservations[index] = WidgetTileSpares.Reservation(spare)
                tile.spare = nil
            }
            jobs.append((index, tile.rect, base == nil ? tile.rect : dirty, base, spare, tile.spareStale))
        }
        var kept: [Int: CGRect] = [:]
        if detached {
            for (index, dirty) in asyncDirty where index < tiles.count && !tiles[index].prepared {
                kept[index] = dirty
            }
        }
        let pending = asyncDirty.filter { kept[$0.key] == nil }
        asyncDirty = kept
        if jobs.isEmpty {
            return
        }
        preparing = true
        drawStatsLock.lock()
        asyncCounts.jobs += 1
        drawStatsLock.unlock()
        let syncEpoch = syncInvalidations
        let lifetime = rasterLifetime
        let space = WidgetDocumentView.surfaceSpace
        let margin = WidgetDocumentView.cullMargin
        let schedule = owner?.scheduleUrgent
        let draw = { [weak self] in
            var made: [(index: Int, rect: CGRect, surface: IOSurfaceRef, partial: Bool, dirty: CGRect, usedSpare: Bool)] = []
            var aborted = false
            var stats = WidgetTileStats.Snapshot()
            stats.batches = 1
            for job in jobs {
                guard lifetime.isActive else {
                    aborted = true
                    break
                }
                var mark = WidgetTileStats.threadMilliseconds()
                let rect = job.rect
                let width = max(1, Int(ceil(rect.width * scale)))
                let height = max(1, Int(ceil(rect.height * scale)))
                var dirty = job.dirty.intersection(rect)
                let changed = WidgetDocumentView.rasterDamage(dirty, tile: rect, scale: scale)
                var usedSpare = false
                var target: (surface: IOSurfaceRef, context: CGContext)?
                if let spare = job.spare, IOSurfaceGetWidth(spare) == width, IOSurfaceGetHeight(spare) == height, !IOSurfaceIsInUse(spare), let base = job.base, IOSurfaceGetWidth(base) == width, IOSurfaceGetHeight(base) == height, let context = CGContext(data: IOSurfaceGetBaseAddress(spare), width: width, height: height, bitsPerComponent: 8, bytesPerRow: IOSurfaceGetBytesPerRow(spare), space: space, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue) {
                    target = (spare, context)
                    usedSpare = true
                } else if let surface = Surface(width: width, height: height, space: space) {
                    target = (surface.surface, surface.context)
                }
                guard let (targetSurface, ctx) = target else {
                    aborted = true
                    break
                }
                IOSurfaceLock(targetSurface, [], nil)
                var now = WidgetTileStats.threadMilliseconds()
                stats.setup += now - mark
                mark = now
                if usedSpare {
                    dirty = dirty.union(job.spareStale.intersection(rect))
                } else if let base = job.base, IOSurfaceGetWidth(base) == width, IOSurfaceGetHeight(base) == height {
                    IOSurfaceLock(base, .readOnly, nil)
                    let from = IOSurfaceGetBaseAddress(base), to = IOSurfaceGetBaseAddress(targetSurface)
                    let fromRow = IOSurfaceGetBytesPerRow(base), toRow = IOSurfaceGetBytesPerRow(targetSurface)
                    for y in 0..<height {
                        memcpy(to.advanced(by: y * toRow), from.advanced(by: y * fromRow), width * 4)
                    }
                    IOSurfaceUnlock(base, .readOnly, nil)
                } else {
                    dirty = rect
                    stats.full += 1
                }
                now = WidgetTileStats.threadMilliseconds()
                stats.copy += now - mark
                mark = now
                stats.jobs += 1
                stats.area += Double(dirty.width * dirty.height)
                let aligned = WidgetDocumentView.rasterDamage(dirty, tile: rect, scale: scale)
                ctx.saveGState()
                ctx.translateBy(x: 0, y: CGFloat(height))
                ctx.scaleBy(x: scale, y: -scale)
                ctx.translateBy(x: -rect.minX, y: -rect.minY)
                ctx.clip(to: aligned)
                ctx.clear(aligned)
                ctx.setAllowsFontSmoothing(false)
                ctx.setShouldAntialias(true)
                let drawCPU = LiteHTMLWidgetRenderer.threadCPUSeconds()
                let drawn = document.drawIfCurrent(in: ctx, origin: .zero, clip: aligned.insetBy(dx: -margin, dy: -margin))
                if !synchronously {
                    self?.owner?.chargeTileDraw(LiteHTMLWidgetRenderer.threadCPUSeconds() - drawCPU)
                }
                ctx.restoreGState()
                ctx.flush()
                IOSurfaceUnlock(targetSurface, [], nil)
                stats.draw += WidgetTileStats.threadMilliseconds() - mark
                guard drawn else {
                    aborted = true
                    break
                }
                made.append((job.index, rect, targetSurface, job.base != nil && (usedSpare || dirty != rect), changed, usedSpare))
            }
            WidgetTileStats.add(stats)
            let finish = {
                guard let self = self else {
                    return
                }
                self.preparing = false
                var installed = Set<Int>()
                if aborted || !lifetime.isActive || self.syncInvalidations != syncEpoch {
                } else if self.windowState.inWindow && !self.tilesTrimmed && self.tileScale == scale {
                    let explicit = WidgetDocumentView.beginInstallTransaction()
                    for item in made where item.index < self.tiles.count {
                        let tile = self.tiles[item.index]
                        guard tile.rect == item.rect, self.detached ? tile.prepared : !tile.layer.needsDisplay() else {
                            continue
                        }
                        if WidgetDocumentView.verifyPrepared, self.document === document {
                            WidgetDocumentView.checkPrepared(item.surface, document: document, rect: item.rect, width: self.size.width, scale: scale, label: "async:" + (self.owner?.debugName ?? "?") + (item.usedSpare ? ":spare" : (item.partial ? ":partial" : ":full")))
                        }
                        let previous = WidgetDocumentView.surface(of: tile.layer)
                        if tile.layer.frame != tile.rect {
                            tile.layer.frame = tile.rect
                            tile.layer.contentsScale = scale
                        }
                        tile.layer.contents = item.surface
                        tile.layer.delegate = self.preparedDisplay
                        tile.note("async-install partial=\(item.partial) dirty=\(item.dirty)")
                        if item.usedSpare, previous != nil {
                            reservations[item.index]?.transfer()
                        }
                        self.settleSpare(tile, previous: previous, item: (item.partial, item.dirty, item.usedSpare))
                        tile.prepared = true
                        installed.insert(item.index)
                        self.drawStatsLock.lock()
                        self.asyncCounts.installed += 1
                        self.drawStatsLock.unlock()
                    }
                    self.presentAlignment(height: drawnHeight)
                    if explicit {
                        CATransaction.commit()
                    }
                }
                for (index, dirty) in pending where !installed.contains(index) {
                    self.asyncDirty[index] = (self.asyncDirty[index] ?? .null).union(dirty)
                    if WidgetDocumentView.damageTrace, index < self.tiles.count {
                        let tile = self.tiles[index]
                        print("DAMAGE RESTORE aborted=\(aborted) sync=\(self.syncInvalidations != syncEpoch) inWindow=\(self.windowState.inWindow) trimmed=\(self.tilesTrimmed) scale=\(self.tileScale == scale) made=\(made.contains { $0.index == index }) prepared=\(tile.prepared) detached=\(self.detached)")
                    }
                }
                if !synchronously { self.owner?.rasterFinished() }
                if self.detached {
                    self.scheduleFlush()
                } else if !self.asyncDirty.isEmpty {
                    self.rasterDirtyTiles()
                }
            }
            if synchronously {
                if let view = self {
                    view.scheduleInstall(height: drawnHeight, schedule: { $0() }, finish)
                } else {
                    finish()
                }
            } else if let schedule = schedule {
                if let view = self {
                    view.scheduleInstall(height: drawnHeight, schedule: schedule, finish)
                } else {
                    schedule(finish)
                }
            } else {
                DispatchQueue.main.async(execute: finish)
            }
        }
        if synchronously {
            draw()
        } else {
            WidgetDocumentView.prepareQueue.async(execute: draw)
        }
    }

    func invalidate(_ rects: [CGRect]) {
        for rect in rects {
            let visible = rect.intersection(bounds)
            if !visible.isNull {
                dirtyArea += visible.width * visible.height
            }
        }
        if detached {
            invalidateDetached(rects)
            return
        }
        if canInvalidateAsync {
            invalidateAsync(rects)
            return
        }
        drawsSynchronously = true
        syncInvalidations += 1
        if rects.count <= 32 || WidgetDocumentView.backingStore || tiles.isEmpty {
            for rect in rects {
                invalidate(rect)
            }
            return
        }
        var pieces = [CGRect](repeating: .null, count: tiles.count)
        var all = CGRect.null
        for rect in rects {
            all = all.union(rect)
            for (index, tile) in tiles.enumerated() where rect.intersects(tile.rect) {
                pieces[index] = pieces[index].union(rect.intersection(tile.rect))
            }
        }
        invalidations += 1
        for (index, tile) in tiles.enumerated() where !pieces[index].isNull {
            if WidgetDocumentView.layerTiles {
                if tile.prepared {
                    unprepare(tile)
                    continue
                }
                tile.layer.setNeedsDisplay(pieces[index].offsetBy(dx: -tile.rect.minX, dy: -tile.rect.minY))
                tile.note("sync-dirty \(pieces[index])")
            } else {
                tile.pending = true
            }
        }
        if !WidgetDocumentView.layerTiles {
            for tile in tiles {
                for surface in tile.surfaces {
                    surface.stale = surface.stale.union(all)
                }
            }
        }
        markNeedsDisplay()
    }

    func invalidate(_ rect: CGRect?) {
        if rect == nil {
            dirtyArea += bounds.width * bounds.height
        }
        if detached {
            invalidateDetached(rect.map { [$0] })
            return
        }
        if canInvalidateAsync {
            invalidateAsync([rect ?? CGRect(x: 0, y: 0, width: size.width, height: CGFloat.greatestFiniteMagnitude / 4)])
            return
        }
        drawsSynchronously = true
        syncInvalidations += 1
        if WidgetDocumentView.backingStore {
            if let rect = rect {
                setNeedsDisplay(rect)
            } else {
                markNeedsDisplay()
            }
            return
        }
        let everything = CGRect(x: 0, y: 0, width: CGFloat.greatestFiniteMagnitude / 4, height: CGFloat.greatestFiniteMagnitude / 4)
        invalidations += 1
        if WidgetDocumentView.layerTiles {
            for tile in tiles where rect.map({ $0.intersects(tile.rect) }) ?? true {
                if tile.prepared {
                    unprepare(tile)
                } else if let rect = rect {
                    tile.layer.setNeedsDisplay(rect.intersection(tile.rect).offsetBy(dx: -tile.rect.minX, dy: -tile.rect.minY))
                    tile.note("sync-dirty \(rect.intersection(tile.rect))")
                } else {
                    tile.layer.setNeedsDisplay()
                }
            }
            markNeedsDisplay()
            return
        }
        for tile in tiles {
            if rect.map({ $0.intersects(tile.rect) }) ?? true {
                tile.pending = true
            }
            for surface in tile.surfaces {
                surface.stale = rect.map { surface.stale.union($0) } ?? everything
            }
        }
        markNeedsDisplay()
    }

    private static let screenScaleLock = NSLock()
    private static var screenScaleValue: CGFloat = 2
    private let scaleLock = NSLock()
    private var windowScale: CGFloat?

    private static var screenScale: CGFloat {
        get {
            screenScaleLock.lock()
            defer { screenScaleLock.unlock() }
            return screenScaleValue
        }
        set {
            screenScaleLock.lock()
            screenScaleValue = newValue
            screenScaleLock.unlock()
        }
    }

    private func refreshWindowScale() {
        let value = window?.backingScaleFactor
        scaleLock.lock()
        windowScale = value
        scaleLock.unlock()
        if let main = NSScreen.main {
            WidgetDocumentView.screenScale = main.backingScaleFactor
        }
    }

    var scale: CGFloat {
        if Thread.isMainThread {
            if let window = window {
                return window.backingScaleFactor
            }
            if let main = NSScreen.main {
                WidgetDocumentView.screenScale = main.backingScaleFactor
            }
            return WidgetDocumentView.screenScale
        }
        scaleLock.lock()
        let cached = windowScale
        scaleLock.unlock()
        return cached ?? WidgetDocumentView.screenScale
    }

    private var displayPending = false

    private func markNeedsDisplay() {
        if detached {
            scheduleFlush()
            return
        }
        if Thread.isMainThread {
            needsDisplay = true
        } else {
            displayPending = true
        }
    }

    override func viewDidChangeBackingProperties() {
        super.viewDidChangeBackingProperties()
        refreshWindowScale()
        captureWindowState()
        if detached {
            toOwner { owner in
                owner.environmentChanged()
            }
            return
        }
        if tileScale != scale {
            removeTiles()
        }
        markNeedsDisplay()
        owner?.environmentChanged()
    }

    func retire() -> [AnyObject] {
        rasterLifetime.cancel()
        var retired: [AnyObject] = []
        if let document = document {
            retired.append(document)
        }
        document = nil
        for tile in tiles {
            retired.append(contentsOf: tile.surfaces as [AnyObject])
            retired.append(tile.layer)
            tile.surfaces.removeAll()
            tile.shown = nil
        }
        removeTiles()
        asyncDirty.removeAll()
        return retired
    }

    private func removeTiles() {
        syncInvalidations += 1
        for layer in retiredGridLayers {
            layer.twRemoveFromSuperlayer()
        }
        retiredGridLayers.removeAll()
        for tile in tiles {
            dropSpare(tile)
            tile.layer.twRemoveFromSuperlayer()
        }
        tiles.removeAll()
    }

    private(set) var tilesTrimmed = false

    static let trimsDetached = ProcessInfo.processInfo.environment["TW_TRIM_DETACHED"] != nil

    func trimTiles() {
        guard !tilesTrimmed, !detached || WidgetDocumentView.trimsDetached else {
            return
        }
        tilesTrimmed = true
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        removeTiles()
        CATransaction.commit()
    }

    func restoreTiles() {
        guard tilesTrimmed else {
            return
        }
        tilesTrimmed = false
        markNeedsDisplay()
    }

    var tileCount: Int {
        return tiles.count
    }

    private weak var appliedSpaceDocument: LHDocument?
    private var appliedSpace: CGColorSpace?

    private func syncGrid(layer: CALayer, document: LHDocument, scale: CGFloat) -> (columns: Int, tileSize: CGFloat, height: CGFloat, space: CGColorSpace)? {
        let space = WidgetDocumentView.screenTagged ? (window?.colorSpace?.cgColorSpace ?? WidgetDocumentView.surfaceSpace) : WidgetDocumentView.surfaceSpace
        let height = max(1, ceil(document.size.height))
        let pixelWidth = Int(ceil(size.width * scale))
        if pixelWidth <= 0 || Int(ceil(height * scale)) <= 0 || pixelWidth > 16384 || height * scale > 16384 {
            return nil
        }
        if surfaceSpace.map({ $0 != space }) ?? true || surfaceWidth != pixelWidth {
            removeTiles()
            surfaceSpace = space
            surfaceWidth = pixelWidth
        }
        let tileSize = max(1, (WidgetDocumentView.tileHeight * scale).rounded()) / scale
        let columns = max(1, Int(ceil(size.width / tileSize)))
        let count = columns * Int(ceil(height / tileSize))
        while tiles.count > count {
            dropSpare(tiles[tiles.count - 1])
            let layer = tiles.removeLast().layer
            if detached, layer.contents != nil {
                retiredGridLayers.append(layer)
            } else {
                layer.twRemoveFromSuperlayer()
            }
        }
        while tiles.count < count {
            let tile = Tile()
            tile.layer.contentsGravity = .topLeft
            if WidgetDocumentView.eightBitTiles {
                tile.layer.contentsFormat = .RGBA8Uint
            }
            tile.layer.anchorPoint = .zero
            tile.layer.actions = ["contents": NSNull(), "position": NSNull(), "bounds": NSNull()]
            layer.twInsertSublayer(tile.layer, at: UInt32(tiles.count))
            tiles.append(tile)
        }
        if appliedSpaceDocument !== document || appliedSpace !== space {
            document.colorSpace = space
            appliedSpaceDocument = document
            appliedSpace = space
        }
        tileScale = scale
        return (columns, tileSize, height, space)
    }

    private func syncTiles(layer: CALayer, document: LHDocument, scale: CGFloat) -> Bool {
        guard let grid = syncGrid(layer: layer, document: document, scale: scale) else {
            gridFails += 1
            return false
        }
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        layer.contents = nil
        for (index, tile) in tiles.enumerated() {
            let top = CGFloat(index / grid.columns) * grid.tileSize
            let left = CGFloat(index % grid.columns) * grid.tileSize
            let rect = CGRect(x: left, y: top, width: min(grid.tileSize, size.width - left), height: min(grid.tileSize, grid.height - top))
            if rect != tile.rect {
                dropSpare(tile)
                tile.rect = rect
                tile.surfaces.removeAll()
                tile.shown = nil
                tile.pending = true
            }
            if tile.layer.delegate == nil {
                drawer.view = self
                tile.layer.delegate = detached ? preparedDisplay : drawer
                tile.layer.isGeometryFlipped = false
                tile.layer.needsDisplayOnBoundsChange = !detached
            }
            if tile.layer.frame != rect || tile.layer.contentsScale != scale {
                tile.note("frame \(tile.layer.frame) -> \(rect)")
                syncInvalidations += 1
                if !(detached && tile.prepared && tile.layer.contents != nil && tile.layer.contentsScale == scale) {
                    tile.layer.frame = rect
                    tile.layer.contentsScale = scale
                }
                if tile.prepared {
                    unprepare(tile)
                } else if !detached {
                    tile.layer.setNeedsDisplay()
                }
            }
            tile.pending = false
        }
        CATransaction.commit()
        return true
    }

    func scaleChanged() {
        if tileScale != scale {
            removeTiles()
        }
        markNeedsDisplay()
    }

    override func updateLayer() {
        if detached {
            return
        }
        guard let layer = contentLayer, let document = document, !tilesTrimmed else {
            return
        }
        let scale = self.scale
        if WidgetDocumentView.layerTiles {
            _ = syncTiles(layer: layer, document: document, scale: scale)
            return
        }
        let start = CACurrentMediaTime()
        defer {
            recordDraw((CACurrentMediaTime() - start) * 1000)
        }
        guard let (columns, tileSize, height, space) = syncGrid(layer: layer, document: document, scale: scale) else {
            return
        }
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        layer.contents = nil
        for (index, tile) in tiles.enumerated() {
            let top = CGFloat(index / columns) * tileSize
            let left = CGFloat(index % columns) * tileSize
            let rect = CGRect(x: left, y: top, width: min(tileSize, bounds.width - left), height: min(tileSize, height - top))
            if rect != tile.rect {
                dropSpare(tile)
                tile.rect = rect
                tile.surfaces.removeAll()
                tile.shown = nil
                tile.pending = true
            }
            if !tile.pending {
                continue
            }
            let pixelHeight = Int(ceil(rect.height * scale))
            let tileWidth = Int(ceil(rect.maxX * scale)) - Int(floor(rect.minX * scale))
            var target = tile.surfaces.first { $0 !== tile.shown && !IOSurfaceIsInUse($0.surface) }
            if target == nil && tile.surfaces.count < 3, let made = Surface(width: max(1, tileWidth), height: pixelHeight, space: space) {
                made.stale = rect
                tile.surfaces.append(made)
                target = made
            }
            guard let surface = target ?? tile.surfaces.first(where: { $0 !== tile.shown }) ?? tile.shown else {
                continue
            }
            let dirty = surface.stale.intersection(rect)
            if !dirty.isNull && !dirty.isEmpty {
                let aligned = WidgetDocumentView.rasterDamage(dirty, tile: rect, scale: scale)
                let ctx = surface.context
                IOSurfaceLock(surface.surface, [], nil)
                ctx.saveGState()
                ctx.translateBy(x: 0, y: CGFloat(surface.height))
                ctx.scaleBy(x: scale, y: -scale)
                ctx.translateBy(x: -left, y: -top)
                ctx.setAllowsFontSmoothing(false)
                ctx.setShouldAntialias(true)
                ctx.clip(to: aligned)
                ctx.clear(aligned)
                let drawCPU = LiteHTMLWidgetRenderer.threadCPUSeconds()
                document.draw(in: ctx, origin: .zero, clip: aligned.insetBy(dx: -WidgetDocumentView.cullMargin, dy: -WidgetDocumentView.cullMargin))
                owner?.chargeTileDraw(LiteHTMLWidgetRenderer.threadCPUSeconds() - drawCPU)
                ctx.restoreGState()
                ctx.flush()
                IOSurfaceUnlock(surface.surface, [], nil)
            }
            if WidgetDocumentView.verify {
                verifyFrame(surface, document: document, rect: rect, scale: scale, space: space)
            }
            surface.stale = .null
            tile.layer.frame = rect
            tile.layer.contentsScale = scale
            if tile.layer.contents as AnyObject? === surface.surface as AnyObject {
                tile.layer.contents = nil
            }
            tile.layer.contents = surface.surface
            tile.shown = surface
            tile.pending = false
        }
        presentAlignment(for: document)
        CATransaction.commit()
    }

    func showBadge(_ text: String, fatal: Bool) {
        guard let layer = contentLayer else {
            return
        }
        if fatal {
            layer.backgroundColor = NSColor(white: 0.5, alpha: 0.25).cgColor
        }
        let badge = badgeLayer ?? CATextLayer()
        badgeLayer = badge
        badge.string = NSAttributedString(string: text, attributes: [.font: NSFont.systemFont(ofSize: 11), .foregroundColor: NSColor.white])
        badge.backgroundColor = NSColor(white: 0.24, alpha: 0.75).cgColor
        badge.cornerRadius = 9
        badge.alignmentMode = .center
        badge.contentsScale = scale
        badge.zPosition = 1000
        let width = ceil((text as NSString).size(withAttributes: [.font: NSFont.systemFont(ofSize: 11)]).width) + 14
        badge.frame = CGRect(x: size.width - width - 6, y: 6, width: width, height: 17)
        if badge.superlayer == nil {
            layer.twAddSublayer(badge)
        }
    }

    private var screenObserver: NSObjectProtocol?
    private var occlusionObserver: NSObjectProtocol?
    private var keyObservers: [NSObjectProtocol] = []
    struct WindowState {
        var inWindow = false
        var hidden = false
        var occlusionVisible = false
        var windowVisible = false
        var occlusionTrusted = false
        var key = false
        var display: CGDirectDisplayID?
        var colorSpace: CGColorSpace?
        var size = CGSize.zero
        var scrollRegions: [CGRect] = []
        var horizontalScrollRegions: [CGRect] = []
        var verticalScrollRegions: [CGRect] = []
    }

    private let windowStateLock = NSLock()
    private var lockedWindowState = WindowState()

    var windowState: WindowState {
        windowStateLock.lock()
        defer { windowStateLock.unlock() }
        return lockedWindowState
    }

    private var occlusionTrusted = false

    private static let lastSpaceLock = NSLock()
    private static var lastSpace: CGColorSpace?

    static var lastWindowSpace: CGColorSpace? {
        get {
            lastSpaceLock.lock()
            defer { lastSpaceLock.unlock() }
            return lastSpace
        }
        set {
            lastSpaceLock.lock()
            lastSpace = newValue
            lastSpaceLock.unlock()
        }
    }

    func captureWindowState() {
        var state = WindowState()
        if let window = window {
            state.inWindow = true
            state.hidden = isHiddenOrHasHiddenAncestor
            state.occlusionVisible = window.occlusionState.contains(.visible)
            state.windowVisible = window.isVisible
            state.key = window.isKeyWindow
            state.display = (window.screen?.deviceDescription[NSDeviceDescriptionKey("NSScreenNumber")] as? NSNumber).map { CGDirectDisplayID($0.uint32Value) }
            state.colorSpace = window.colorSpace?.cgColorSpace
            if let space = state.colorSpace {
                WidgetDocumentView.lastWindowSpace = space
            }

            if state.occlusionVisible {
                occlusionTrusted = true
            }
        }
        state.occlusionTrusted = occlusionTrusted
        windowStateLock.lock()
        state.size = lockedWindowState.size
        state.scrollRegions = lockedWindowState.scrollRegions
        state.horizontalScrollRegions = lockedWindowState.horizontalScrollRegions
        state.verticalScrollRegions = lockedWindowState.verticalScrollRegions
        lockedWindowState = state
        windowStateLock.unlock()
    }

    var size: CGSize {
        windowStateLock.lock()
        defer { windowStateLock.unlock() }
        return lockedWindowState.size
    }

    private func captureSize(_ size: CGSize) {
        windowStateLock.lock()
        lockedWindowState.size = size
        windowStateLock.unlock()
    }

    func publishScrollRegions(_ regions: [CGRect], horizontal: [CGRect], vertical: [CGRect]) {
        windowStateLock.lock()
        lockedWindowState.scrollRegions = regions
        lockedWindowState.horizontalScrollRegions = horizontal
        lockedWindowState.verticalScrollRegions = vertical
        windowStateLock.unlock()
    }

    var detached: Bool {
        return !(owner?.executorIsMain() ?? true)
    }

    func toOwner(_ block: @escaping (LiteHTMLWidgetRenderer) -> Void) {
        guard let owner = owner else {
            return
        }
        if owner.isOnExecutor() {
            block(owner)
            return
        }
        owner.scheduleUrgent { [weak owner] in
            if let owner = owner {
                block(owner)
            }
        }
    }

    override func viewDidHide() {
        super.viewDidHide()
        captureWindowState()
    }

    override func viewDidUnhide() {
        super.viewDidUnhide()
        captureWindowState()
        if detached {
            toOwner { $0.setVisible($0.isHostVisible) }
        }
    }

    override func viewDidMoveToWindow() {
        super.viewDidMoveToWindow()
        refreshWindowScale()
        if displayPending {
            displayPending = false
            needsDisplay = true
        }
        if let screenObserver = screenObserver {
            NotificationCenter.default.removeObserver(screenObserver)
        }
        if let occlusionObserver = occlusionObserver {
            NotificationCenter.default.removeObserver(occlusionObserver)
        }
        screenObserver = nil
        occlusionObserver = nil
        keyObservers.forEach { NotificationCenter.default.removeObserver($0) }
        keyObservers = []
        occlusionTrusted = false
        captureWindowState()
        if let window = window {
            for name in [NSWindow.didBecomeKeyNotification, NSWindow.didResignKeyNotification] {
                keyObservers.append(NotificationCenter.default.addObserver(forName: name, object: window, queue: .main) { [weak self] _ in
                    self?.captureWindowState()
                    self?.toOwner { $0.keyChanged() }
                })
            }
            keyObservers.append(NotificationCenter.default.addObserver(forName: NSWindow.didChangeBackingPropertiesNotification, object: window, queue: .main) { [weak self] _ in
                self?.captureWindowState()
                self?.toOwner { $0.environmentChanged() }
            })
            screenObserver = NotificationCenter.default.addObserver(forName: NSWindow.didChangeScreenNotification, object: window, queue: .main) { [weak self] _ in
                self?.captureWindowState()
                self?.toOwner { $0.environmentChanged() }
            }
            occlusionObserver = NotificationCenter.default.addObserver(forName: NSWindow.didChangeOcclusionStateNotification, object: window, queue: .main) { [weak self] _ in
                self?.occlusionTrusted = true
                self?.captureWindowState()
                self?.toOwner { $0.setVisible($0.isHostVisible) }
            }
        }
        toOwner { owner in
            owner.screenChanged()
            owner.keyChanged()
            owner.setVisible(owner.isHostVisible)
        }
    }

    deinit {
        rasterLifetime.cancel()
        keyObservers.forEach { NotificationCenter.default.removeObserver($0) }
        if let screenObserver = screenObserver {
            NotificationCenter.default.removeObserver(screenObserver)
        }
        if let occlusionObserver = occlusionObserver {
            NotificationCenter.default.removeObserver(occlusionObserver)
        }
    }

    override func updateTrackingAreas() {
        super.updateTrackingAreas()
        if let tracking = tracking {
            if trackingAreas.contains(tracking) {
                return
            }
            removeTrackingArea(tracking)
        }
        let area = NSTrackingArea(rect: bounds, options: [.mouseMoved, .mouseEnteredAndExited, .activeInKeyWindow, .inVisibleRect], owner: self, userInfo: nil)
        addTrackingArea(area)
        tracking = area
    }

    private func point(_ event: NSEvent) -> CGPoint {
        return convert(event.locationInWindow, from: nil)
    }

    override func mouseDown(with event: NSEvent) {
        if event.modifierFlags.contains(.control) {
            menuClick(event)
            return
        }
        let at = point(event)
        if owner?.frozen != true {
            _ = textSelection.press(at: at, clickCount: event.clickCount, document: document)
        }
        toOwner { $0.handleMouseDown(at, event: event) }
    }

    override func mouseDragged(with event: NSEvent) {
        let at = point(event)
        textSelection.drag(to: at)
        toOwner { $0.handleMouseDragged(at, event: event) }
    }

    override func mouseUp(with event: NSEvent) {
        let at = point(event)
        textSelection.release()
        toOwner { $0.handleMouseUp(at, event: event) }
    }

    override func mouseMoved(with event: NSEvent) {
        let at = point(event)
        toOwner { $0.handleMouseMoved(at, event: event) }
    }

    override func rightMouseDown(with event: NSEvent) {
        menuClick(event)
    }

    private func menuClick(_ event: NSEvent) {
        let at = point(event)
        guard owner != nil, owner?.frozen != true else {
            passMenuClick(event)
            return
        }
        toOwner { [weak self] owner in
            if owner.handleMenuClick(at, event: event) {
                return
            }
            if Thread.isMainThread {
                self?.passMenuClick(event)
            } else {
                DispatchQueue.main.async {
                    self?.passMenuClick(event)
                }
            }
        }
    }

    private func passMenuClick(_ event: NSEvent) {
        if event.type == .rightMouseDown {
            super.rightMouseDown(with: event)
        } else {
            super.mouseDown(with: event)
        }
    }

    override func mouseExited(with event: NSEvent) {
        toOwner { $0.handleMouseExited(event) }
    }

    override static var isCompatibleWithResponsiveScrolling: Bool {
        return true
    }

    override func scrollWheel(with event: NSEvent) {
        let at = point(event)
        if detached {
            windowStateLock.lock()
            let hit = lockedWindowState.scrollRegions.contains { $0.contains(at) }
            windowStateLock.unlock()
            if hit {
                toOwner { _ = $0.handleScroll(at, event: event) }
            } else {
                super.scrollWheel(with: event)
            }
            return
        }
        if owner?.handleScroll(at, event: event) != true {
            super.scrollWheel(with: event)
        }
    }

    override func keyDown(with event: NSEvent) {
        let key = WidgetKeyEvent(event)
        toOwner { $0.handleKey("keydown", event: key) }
    }

    override func keyUp(with event: NSEvent) {
        let key = WidgetKeyEvent(event)
        toOwner { $0.handleKey("keyup", event: key) }
    }
}

struct WidgetKeyEvent {
    let key: String
    let code: String
    let isARepeat: Bool
    let modifierFlags: NSEvent.ModifierFlags
    let menuEvent: NSEvent

    init(_ event: NSEvent) {
        precondition(Thread.isMainThread)
        key = Self.key(event)
        code = Self.code(event)
        isARepeat = event.isARepeat
        modifierFlags = event.modifierFlags
        menuEvent = event
    }

    private static func key(_ event: NSEvent) -> String {
        switch event.keyCode {
        case 36, 76: return "Enter"
        case 48: return "Tab"
        case 49: return " "
        case 51: return "Backspace"
        case 53: return "Escape"
        case 117: return "Delete"
        case 123: return "ArrowLeft"
        case 124: return "ArrowRight"
        case 125: return "ArrowDown"
        case 126: return "ArrowUp"
        case 115: return "Home"
        case 119: return "End"
        case 116: return "PageUp"
        case 121: return "PageDown"
        default: return event.characters ?? ""
        }
    }

    private static func code(_ event: NSEvent) -> String {
        switch event.keyCode {
        case 36, 76: return "Enter"
        case 48: return "Tab"
        case 49: return "Space"
        case 51: return "Backspace"
        case 53: return "Escape"
        case 123: return "ArrowLeft"
        case 124: return "ArrowRight"
        case 125: return "ArrowDown"
        case 126: return "ArrowUp"
        default:
            let chars = (event.charactersIgnoringModifiers ?? "").uppercased()
            if let ch = chars.first, ch.isLetter {
                return "Key\(ch)"
            }
            if let ch = chars.first, ch.isNumber {
                return "Digit\(ch)"
            }
            return ""
        }
    }
}

enum CSSFont {
    private static let lock = NSLock()
    private static var cache: [String: CTFont] = [:]
    static let cacheLimit = 64

    static func make(_ css: String) -> CTFont {
        lock.lock()
        if let font = cache[css] {
            lock.unlock()
            return font
        }
        lock.unlock()
        let font = resolve(css)
        lock.lock()
        if cache.count >= cacheLimit {
            cache.removeAll(keepingCapacity: true)
        }
        cache[css] = font
        lock.unlock()
        return font
    }

    static var cachedCount: Int {
        lock.lock()
        defer { lock.unlock() }
        return cache.count
    }

    private static func resolve(_ css: String) -> CTFont {
        var size: CGFloat = 10
        var weight: CGFloat = 400
        var italic = false
        var families: [String] = []
        let tokens = WidgetNode.splitTopLevel(css, separator: " ").filter { !$0.isEmpty }
        var familyStart = tokens.count
        for (index, token) in tokens.enumerated() {
            let t = token.lowercased()
            if t == "bold" {
                weight = 700
            } else if t == "bolder" {
                weight = 900
            } else if t == "lighter" {
                weight = 300
            } else if let numeric = Int(t), numeric >= 1, numeric <= 1000, index < tokens.count - 1 {
                weight = CGFloat(numeric)
            } else if t == "italic" || t == "oblique" {
                italic = true
            } else if let px = t.split(separator: "/").first, px.hasSuffix("px"), let value = Double(px.dropLast(2)), value.isFinite {
                size = CGFloat(min(max(value, 1), 1000))
                familyStart = index + 1
                break
            }
        }
        if familyStart < tokens.count {
            families = tokens[familyStart...].joined(separator: " ").split(separator: ",").map { $0.trimmingCharacters(in: CharacterSet(charactersIn: " \"'")) }
        }
        return LHDocument.font(families: families.joined(separator: ","), size: size, weight: Int(weight), italic: italic) as CTFont
    }
}

public struct ScriptFramePacer {
    public static let defaultBudget: TimeInterval = ProcessInfo.processInfo.environment["TW_RAF_BUDGET_MS"].flatMap { Double($0) }.map { $0 / 1000 } ?? 0.001
    public var budget: TimeInterval
    public let fallbackInterval: CFTimeInterval
    public private(set) var cost: TimeInterval = 0
    public private(set) var fullRate = true
    private var postedAt: CFTimeInterval?
    private var lastPost: CFTimeInterval = -.infinity

    public init(budget: TimeInterval, fallbackInterval: CFTimeInterval) {
        self.budget = budget
        self.fallbackInterval = fallbackInterval
    }

    public func due(_ now: CFTimeInterval) -> Bool {
        return fullRate || now - lastPost >= fallbackInterval
    }

    public mutating func posted(_ now: CFTimeInterval) {
        lastPost = now
        if postedAt.map({ now - $0 > 0.25 }) ?? true {
            postedAt = now
        }
    }

    public mutating func answered(_ now: CFTimeInterval) {
        guard let posted = postedAt else {
            return
        }
        postedAt = nil
        let spent = now - posted
        cost = cost == 0 ? spent : cost * 0.8 + spent * 0.2
        if fullRate && cost > budget {
            fullRate = false
        } else if !fullRate && cost < budget * 0.7 {
            fullRate = true
        }
    }
}

final class WidgetFrameClock {
    private var link: CVDisplayLink?
    private let tick: () -> Void
    private let post: (@escaping () -> Void) -> Void
    private var running = false
    private var scheduled = false
    private var lastFire: CFTimeInterval = 0
    private let lock = NSLock()
    private static let minimumInterval = ProcessInfo.processInfo.environment["TW_FPS"].flatMap { Double($0) }.map { 1 / $0 - 0.002 } ?? 0

    init(post: @escaping (@escaping () -> Void) -> Void = { DispatchQueue.main.async(execute: $0) }, tick: @escaping () -> Void) {
        self.tick = tick
        self.post = post
        CVDisplayLinkCreateWithActiveCGDisplays(&link)
        if let link = link {
            CVDisplayLinkSetOutputHandler(link) { [weak self] _, _, _, _, _ in
                self?.fire()
                return kCVReturnSuccess
            }
        }
    }

    deinit {
        if let link = link {
            CVDisplayLinkStop(link)
        }
    }

    private func fire() {
        lock.lock()
        let now = CACurrentMediaTime()
        if scheduled || now - lastFire < WidgetFrameClock.minimumInterval {
            lock.unlock()
            return
        }
        lastFire = now
        scheduled = true
        lock.unlock()
        post { [weak self] in
            guard let self = self else {
                return
            }
            self.lock.lock()
            self.scheduled = false
            self.lock.unlock()
            if self.running {
                self.tick()
            }
        }
    }

    func setDisplay(_ display: CGDirectDisplayID) {
        if let link = link {
            CVDisplayLinkSetCurrentCGDisplay(link, display)
        }
    }

    func start() {
        if running {
            return
        }
        running = true
        if let link = link {
            CVDisplayLinkStart(link)
        }
    }

    var isRunning: Bool {
        return running
    }

    func stop() {
        if !running {
            return
        }
        running = false
        if let link = link {
            CVDisplayLinkStop(link)
        }
    }
}

final class WidgetBackingStoreDocumentView: WidgetDocumentView {
    override func draw(_ dirtyRect: NSRect) {
        guard let document = document, let ctx = NSGraphicsContext.current?.cgContext else {
            return
        }
        let start = CACurrentMediaTime()
        defer {
            recordDraw((CACurrentMediaTime() - start) * 1000)
        }
        ctx.saveGState()
        ctx.setAllowsFontSmoothing(false)
        ctx.clear(dirtyRect)
        let baseTransform = CGAffineTransform(scaleX: abs(ctx.ctm.a), y: -abs(ctx.ctm.d))
        document.draw(in: ctx, origin: .zero, clip: dirtyRect.insetBy(dx: -WidgetDocumentView.cullMargin, dy: -WidgetDocumentView.cullMargin), baseTransform: baseTransform)
        ctx.restoreGState()
    }
}

extension LiteHTMLWidgetRenderer: SurfaceHostingRenderer {}

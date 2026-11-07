// WidgetHost: one widget = one SandboxEngine (QuickJS on its own queue) + one
// TrustedValidator + one Renderer (RendererWebView unless a `rendererFactory`
// supplies another, see Renderer.swift). The only place where the three meet;
// nothing from the engine reaches the renderer without passing the validator.
// Mirrors src/host/widget-host.ts. All public API is main-thread.
// iOS and macOS share this file; `PlatformView` is UIView or NSView.

import Foundation
import WidgetSandboxProtocol
#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

public struct WidgetDiagnostics {
    public var logs: [(level: String, text: String)] = []
    public var diags: [String: (count: Int, details: [String])] = [:]
    /// The raw bridge calls that passed the glue's checks (the deprecated `onBridge` view of `actions`).
    public var bridge: [(method: String, argsJson: String)] = []
    /// What `onAction` got, the latest `maxActions`.
    public var actions: [WidgetAction] = []
    public static let maxActions = 200
    public var stats = SandboxStats()
    public var nodes = 0
    public var loaded = false
    /// Wall clock from WidgetHost creation to the renderer's `ready` and to `loaded` (the same definition as the compare runner).
    public var rendererReadyMs: Double?
    public var loadedMs: Double?
    /// `loaded` means the widget's scripts ran; `rendered` means the renderer has applied every op sent up to that
    /// point and its size reports for them have arrived (an ack round trip after `loaded`). A list that lays
    /// bubbles out from remembered heights trusts a new height only once this is set.
    public var rendered = false
    public var renderedMs: Double?
    /// Fatal: the runtime is gone (memory, CPU, or a counted cap passed under strict: the validator's `limit`, docs/decisions.md
    /// "Strict limits from the start"). The renderer keeps the last state (frozen); the owner removes the view with `destroy()`.
    public var dead: String?
    /// Last uncaught widget error (non-fatal), cleared once a later task completes without one.
    public var lastError: String?
    public var height: CGFloat = 0
    /// The edge of the bubble that stays put on screen when `height` changes: the widget's latest
    /// `TelegramWidget.setResizeAnchor`, `.top` until then (docs/sdk-v1.md §2.12, §5.6). A list keeps `.bottom`
    /// bubbles' bottom edge in place (the content above moves) and `.top` ones' top edge (the content below moves).
    /// It is set before the size report of the ops that follow it in the widget's task.
    public var resizeAnchor = SdkPolicy.defaultResizeAnchor
    /// Whether the widget watches its shared `public_data` (its latest `TelegramWidget.watchPublicData` /
    /// `unwatchPublicData`; docs/sdk-v1.md §2.13), false until then. The client polls `getWidgetPublicData` only for a
    /// widget that watches, is on screen and whose message has a `widget_id`. Not reset when the widget stops.
    public var watchesPublicData = false
    /// Wall clock to the first `size` report that follows validated ops (the widget's first content on screen).
    public var firstContentMs: Double?
    /// Which parts came prewarmed from the pools ("renderer+engine+validator"), empty when none.
    public var prewarmed = ""
    public init() {}
}

public final class WidgetHost: SandboxEngineDelegate {
    /// Limits for the widget's QuickJS runtime (see SandboxConfig); mirrors WidgetHost.Options on Android.
    public struct Options {
        public var memoryLimitBytes = 8 << 20
        public var cpuLimitMs = 500.0
        public var cpuQuotaMs = 300.0
        public var cpuQuotaWindowMs = 1000.0
        public var cpuKillAfterMs = 0.0
        /// The host's main-thread work for this widget per window (layout queries + 25 µs per op + 2 µs per canvas command).
        public var mainThreadQuotaMs = 100.0
        public var maxTimers = 64
        /// The bubble's maximum height in points; 600 is the shared v0.1.0 value (docs/integration.md, "Bubble sizing").
        public var maximumHeight: CGFloat = 600
        /// Initial visibility; change it later with `setVisible`.
        public var visible = true
        /// One WebKit environment (process pool, data store, content controller, rule list, scheme handler) for every renderer; see RendererEnvironment.
        public var sharedWebKit = false
        /// A few JSThreads shared by every validator instead of a thread per widget (see TrustedValidator).
        public var sharedValidatorThread = false
        /// Take prewarmed parts from RendererPool / EnginePool / ValidatorPool when they have any, and recycle the validator on destroy.
        public var pools = false
        public var recyclesRenderers = false
        /// Experimental, outside widget profile 1: lets `overflow` scroll inside the widget and lets it listen to `scroll`.
        /// Only DEBUG builds or builds with TWIDGET_EXPERIMENTAL_SCROLLING honour it; a release build reports a
        /// `native-scrolling` diag and keeps profile 1 (docs/decisions.md, "Native scrolling: an experiment").
        public var nativeScrolling = false
        /// Optional wire forms to advertise (SandboxConfig.features), e.g. `canvasWords`, `binaryOps`; empty means plain JSON.
        public var features: Set<String> = []
        /// Side channels next to the DOM protocol, one fresh instance per host (e.g. the twidget kit's 3D surfaces).
        public var sideChannels: [() -> WidgetSideChannel] = []
        /// Build the QuickJS runtime and evaluate the sandbox bundle while the renderer document is still loading, instead of after `ready`.
        public var parallelBoot = false
        /// Run the widget's own scripts as soon as the host exists instead of after the renderer's `ready`; queries made before the renderer loads wait for it.
        public var startBeforeRendererReady = false
        /// Validate ops and queries on the validator's thread and only talk to the renderer on the executor; false validates on the executor itself.
        public var asyncValidation = false
        /// Boot hosts that the feed creates ahead of the viewport on a background queue (WidgetPreparer) and adopt them once their first content is laid out.
        public var backgroundBoot = false
        public var assetResolver: WidgetAssetResolver?
        /// `.strict` unless a test or tool asks for `.compat` (SandboxPolicy).
        public var policy: SandboxPolicy = .strict
        /// The client's UI language, BCP 47 (`navigator.language`, the Intl default); `en-US` when malformed.
        public var locale = SdkPolicy.defaultLocale
        /// What the client draws over the widget (docs/sdk-v1.md §2.17, §5.12): for a widget with no caption below it the band of
        /// the message time at the bottom and its corner; zero (the default) with a caption below it. Pass it here, before the first
        /// render, so the first height already has the band; later only with the bubble's layout (`setContentSafeArea`).
        public var contentSafeArea = WidgetContentSafeArea()
        /// Web Storage of this message as the client keeps it (docs/sdk-v1.md §5.4); over the quota it is dropped whole (`storage-rejected`).
        public var storage = WidgetStorage()
        /// The bot's `chatData` and `publicData` as the message has them now (docs/sdk-v1.md §2.13); later edits go through `setData`.
        public var data = WidgetData()
        /// The message's flags from the server (`isCopy`, `canQuery`, `backgroundQuery`), the chat type, the viewer and
        /// `preview` for an inline result not sent yet (docs/sdk-v1.md §4.4); fixed for the host's life. Without
        /// `canQuery` (the default) every request is refused; a preview takes no input, no actions and commits no storage.
        public var context = WidgetContext()
        public init() {}
    }

    public let view: PlatformView
    private var state = WidgetDiagnostics()
    private let publishLock = NSLock()
    private var published = WidgetDiagnostics()
    public var diagnostics: WidgetDiagnostics {
        publishLock.lock()
        defer { publishLock.unlock() }
        return published
    }
    public let executor: WidgetExecutor
    public var rendererObject: Renderer { renderer }
    public var onChange: ((WidgetDiagnostics) -> Void)?
    /// Called on the host's executor with every change, before the coalesced main-thread `onChange`.
    public var onChangeOnExecutor: ((WidgetDiagnostics) -> Void)?
    /// What the widget asked the client to do, checked: the URL rules, user activation for the links, the haptic rate limit (docs/sdk-v1.md §5.2, §5.3). Main thread.
    public var onAction: ((WidgetAction) -> Void)?
    /// The new full content of a Web Storage area, at the end of each task that changed it; `local` first (docs/sdk-v1.md §5.4). Main thread.
    public var onStorageCommit: ((WidgetStorageArea, [String: String]) -> Void)?
    /// On every primary pointerdown: what of this press the widget claims with `touch-action`, horizontal drags (`x`) and
    /// holds (`hold`, no message menu) (docs/sdk-v1.md §5.5); taps and double taps are always its own, vertical drags
    /// always the chat's (`y` is always false since v0.1.3). The client's recognizers decide with it (GestureArbiter).
    /// Main thread, a few ms after the touch began.
    public var onGestureClaim: ((WidgetGestureClaim) -> Void)?
    /// The widget started (`true`) or stopped (`false`) watching its shared `public_data` (`TelegramWidget.watchPublicData`,
    /// docs/sdk-v1.md §2.13): poll `getWidgetPublicData` for it only while it watches and is on screen, and only when the
    /// message has a `widget_id`. Called on a change only; `diagnostics.watchesPublicData` has the latest. Main thread.
    public var onWatchPublicData: ((Bool) -> Void)?
    /// A request the widget sends to the bot (`TelegramWidget.request`, checked: the data, the server's flags, the
    /// press or the background rules, one in flight; docs/sdk-v1.md §2.14). The client sends `data` like a callback
    /// button's press and calls the completion once, from any thread, with the server's answer (`result`) or a
    /// failure; later calls, and calls after `destroy()`, are ignored. Read on the main thread when a request is
    /// delivered: nil then refuses it (`NOT_ALLOWED`), so setting it right after creating the host catches every
    /// request. Main thread.
    public var onRequest: ((WidgetRequest, @escaping (Result<WidgetRequestAnswer, WidgetRequestFailure>) -> Void) -> Void)?
    /// A wallet call (`TelegramWidget.wallet`, docs/sdk-v1.md §2.18; checked: the transaction or the payload, a press or
    /// the grant, one call in flight with the requests). Only with `context.wallet`. The client shows its own wallet
    /// window with the bot of the message; on confirmation it sends the transaction (or signs for `bot<bot_id>`), sends
    /// the outcome to the bot as a query with `call.data` and calls the completion once, from any thread, with the bot's
    /// answer, or `.rejected` (the user declined), `.badRequest` (the wallet refused it) or a request's failure. Read on
    /// the main thread when a call is delivered: nil then refuses it (`NOT_ALLOWED`). Main thread.
    public var onWallet: ((WalletCall, @escaping (Result<WidgetRequestAnswer, WidgetRequestFailure>) -> Void) -> Void)?
    /// Copy this text to the clipboard and show a toast that says what was copied (`TelegramWidget.copyText`, checked:
    /// at most 256 UTF-16 code units, one per press, shared with the link calls; docs/sdk-v1.md §2.15). Main thread.
    public var onCopyText: ((String) -> Void)?
    /// The raw method and arguments JSON of each call `onAction` gets.
    @available(*, deprecated, message: "use onAction (SDK v1)")
    public var onBridge: ((String, String) -> Void)? {
        get { bridgeHandler }
        set { bridgeHandler = newValue }
    }
    private var bridgeHandler: ((String, String) -> Void)?
    /// Non-fatal uncaught exception in widget code (also in `state.logs`).
    public var onError: ((String) -> Void)?
    /// Badge text shown by the renderer over a dead widget (user-facing, no error details).
    public var stoppedLabel = "Widget stopped"

    private let engine: SandboxEngine
    private let sideChannels: [WidgetSideChannel]
    private let validator: TrustedValidator
    private let renderer: Renderer
    private let options: Options
    /// What the engine boots with; `setData` before the engine starts updates its data. Executor only after init.
    private var config: SandboxConfig
    private var theme: WidgetTheme
    /// The colour scheme as the renderer, the validator and the sandbox take it.
    private var scheme: String { theme.colorScheme.rawValue }
    private var rendererReady = false
    private var engineStarted = false
    private let html: String
    private enum Outgoing {
        case json(String)
        case ops([WidgetOp])
        case beginTask
        case endTask
    }
    private var pendingToRenderer: [Outgoing] = []
    private let typedOps: Bool
    private var destroyed = false
    private var viewport: (w: Double, h: Double, dpr: Double)
    private var ownerVisible: Bool
    private var inWindow = false
    private var qid = 0
    private var pendingQuery: PendingQuery?
    private let queryLock = NSLock()
    private let minHeight: CGFloat = 50
    private let maxHeight: CGFloat
    private let createdAt = Date()
    private var opsSent = 0
    public private(set) var frameCount = 0

    /// `theme`: the chat's theme for this bubble (docs/sdk-v1.md §5.1). `rendererFactory`: the renderer; the WebView renderer when nil.
    public init(html: String, theme widgetTheme: WidgetTheme = WidgetTheme(), width: CGFloat = 400, options: Options = Options(), resources: SandboxResources = .shared, rendererFactory: RendererFactory? = nil, executor: WidgetExecutor = WidgetExecutor(), shell: WidgetShellPool.Shell? = nil) throws {
        self.executor = executor
        self.html = html
        sideChannels = options.sideChannels.map { $0() }
        self.theme = widgetTheme
        let theme = widgetTheme.colorScheme.rawValue
        self.options = options
        maxHeight = options.maximumHeight.isFinite ? max(50, options.maximumHeight) : 600
        ownerVisible = options.visible
        let scale = PlatformScreen.scale
        viewport = (Double(width), 300, scale)
        var prewarmed: [String] = []
        var rendererLoaded = false
        if let shell = shell {
            renderer = shell.renderer
            prewarmed.append("shell")
        } else if let factory = rendererFactory {
            renderer = factory(resources, theme, width)
        } else if options.pools, let pooled = RendererPool.shared.take() {
            pooled.setTheme(theme)
            renderer = pooled
            rendererLoaded = true
            prewarmed.append("renderer")
        } else {
            renderer = RendererWebView(rendererBundle: resources.rendererBundle, theme: theme, environment: options.sharedWebKit ? .shared : nil)
        }
        typedOps = renderer.acceptsTypedOps
        let container = shell?.container ?? WidgetShellPool.makeContainer(renderer, width: width)
        view = container
        var diagSink: ((String, String) -> Void) = { _, _ in }
        if options.pools, let pooled = ValidatorPool.shared.take(theme: theme) {
            validator = pooled
            validator.onDiag = { k, d in diagSink(k, d) }
            prewarmed.append("validator")
        } else {
            validator = TrustedValidator.background(theme: theme, resources: resources, thread: options.sharedValidatorThread ? TrustedValidator.sharedThreadForNextValidator() : nil) { k, d in diagSink(k, d) }
        }
        var config = SandboxConfig(innerWidth: viewport.w, innerHeight: viewport.h, devicePixelRatio: scale, theme: theme)
        config.memoryLimitBytes = options.memoryLimitBytes
        config.cpuLimitMs = options.cpuLimitMs
        config.cpuQuotaMs = options.cpuQuotaMs
        config.cpuQuotaWindowMs = options.cpuQuotaWindowMs
        config.cpuKillAfterMs = options.cpuKillAfterMs
        config.mainThreadQuotaMs = options.mainThreadQuotaMs
        config.maxTimers = options.maxTimers
        config.features = options.features
        config.visible = options.visible
        config.policy = options.policy
        config.themeParams = widgetTheme.params
        config.locale = options.locale
        config.contentSafeArea = options.contentSafeArea
        config.storage = options.storage
        config.data = options.data
        config.context = options.context
        self.config = config
        if options.pools, let pooled = EnginePool.shared.take(features: options.features) {
            engine = pooled
            prewarmed.append("engine")
        } else {
            engine = SandboxEngine(resources: resources, features: options.features)
        }
        state.prewarmed = prewarmed.joined(separator: "+")
        validator.setAssetResolver(options.assetResolver)
        validator.setPolicy(options.policy)
        #if DEBUG || TWIDGET_EXPERIMENTAL_SCROLLING
        validator.setNativeScrolling(options.nativeScrolling)
        #else
        if options.nativeScrolling {
            // Inner scrolling is outside widget profile 1: other clients clip, so a release build must not let widgets rely on it.
            executor.async { [weak self] in self?.diag("native-scrolling", "nativeScrolling is an experiment outside widget profile 1: only DEBUG or TWIDGET_EXPERIMENTAL_SCROLLING builds honour it") }
        }
        #endif
        validator.useNativeModel(engine.runtimeFeatures.contains("binaryOps"))
        engine.delegate = self
        // The validator's time on the widget's messages counts against its JS quota (docs/decisions.md, "The validator's time counts").
        validator.setCpuSink { [weak engine] ms in engine?.chargeValidator(ms: ms) }
        let channels = sideChannels
        engine.afterBoot = { js in channels.forEach { $0.install(in: js) } }
        for channel in channels {
            channel.connect(renderer)
            channel.onDiag = { [weak self] k, d in self?.executor.async { self?.diag(k, d) } }
        }
        diagSink = { [weak self] k, d in self?.diag(k, d) }
        renderer.onDiag = { [weak self] k, d in self?.diag(k, d) }
        if let scheduled = renderer as? ScheduledRenderer {
            scheduled.schedule = { [weak executor] block in executor?.async(block) }
            scheduled.scheduleAfter = { [weak executor] seconds, block in executor?.after(seconds, block) }
            scheduled.isOnExecutor = { [weak executor] in executor?.isCurrent ?? Thread.isMainThread }
            scheduled.scheduleUrgent = { [weak executor] block in executor?.urgent(block) }
            scheduled.executorIsMain = { [weak executor] in executor?.isMain ?? true }
        }
        if shell != nil {
            if executor.isMain || executor.isCurrent {
                renderer.setTheme(theme)
            } else {
                let themed = renderer
                executor.async { themed.setTheme(theme) }
            }
        }
        container.onWindowChange = { [weak self] inWindow in
            self?.onExecutor {
                self?.inWindow = inWindow
                self?.pushVisibility()
            }
        }
        // The theme variables go first, before the renderer can announce `ready` (a pooled one replays it when onMessage is set).
        toRenderer(widgetTheme.rendererMessage)
        toRenderer(options.contentSafeArea.rendererMessage)
        // Under strict the renderer counts heavy boxes and reports `limit` past them (docs/decisions.md "Heavy boxes"); compat counts nothing.
        toRenderer("{\"type\":\"policy\",\"policy\":\"\(options.policy.rawValue)\"}")
        renderer.onMessage = { [weak self] json in self?.onRenderer(json) }
        publish()
        if !rendererLoaded && !executor.isMain && !executor.isCurrent {
            rendererLoaded = true
            let loading = renderer
            executor.async { loading.load() }
        }
        if options.startBeforeRendererReady {
            startEngine()
        } else if options.parallelBoot {
            engine.prewarm()
        }
        let warmed = validator
        warmed.perform { warmed.warmStylesheets(Self.styleTexts(in: html)) }
        if !rendererLoaded { renderer.load() }
    }

    /// The API before SDK v1: a plain colour scheme, `"dark"` or `"light"`.
    @available(*, deprecated, message: "pass a WidgetTheme (SDK v1)")
    public convenience init(html: String, theme: String, width: CGFloat = 400, options: Options = Options(), resources: SandboxResources = .shared, rendererFactory: RendererFactory? = nil, executor: WidgetExecutor = WidgetExecutor(), shell: WidgetShellPool.Shell? = nil) throws {
        try self.init(html: html, theme: WidgetTheme(colorScheme: theme), width: width, options: options, resources: resources, rendererFactory: rendererFactory, executor: executor, shell: shell)
    }

    private func startEngine() {
        if engineStarted { return }
        engineStarted = true
        engine.start(html: html, config: config)
    }

    private func onExecutor(_ block: @escaping () -> Void) {
        if executor.isCurrent {
            block()
        } else {
            executor.async(block)
        }
    }

    private func onMain(_ block: @escaping () -> Void) {
        if Thread.isMainThread {
            block()
        } else {
            DispatchQueue.main.async(execute: block)
        }
    }

    public var isAdopted: Bool {
        return executor.isMain
    }

    public func moveToMain(_ completion: @escaping () -> Void) {
        executor.moveToMain(completion)
    }

    /// The chat's theme changed (docs/sdk-v1.md §5.1): stylesheets are re-sanitized against the colour scheme
    /// (prefers-color-scheme, color-scheme, light-dark()), the renderer gets the variables, and the widget gets
    /// `colorScheme`, `themeParams` and `themeChanged` in one task.
    public func setTheme(_ theme: WidgetTheme) {
        guard executor.isCurrent else {
            return executor.async { [weak self] in self?.setTheme(theme) }
        }
        guard !destroyed else { return }
        self.theme = theme
        let scheme = self.scheme
        renderer.setTheme(scheme)
        toRenderer(theme.rendererMessage)
        validate { [self] in
            let ops = validator.setTheme(scheme)
            deliver { self.toRenderer("{\"type\":\"ops\",\"ops\":\(ops)}") }
        }
        engine.theme(scheme, params: theme.params)
    }

    /// The API before SDK v1: a plain colour scheme, `"dark"` or `"light"`.
    @available(*, deprecated, message: "pass a WidgetTheme (SDK v1)")
    public func setTheme(_ theme: String) {
        setTheme(WidgetTheme(colorScheme: theme))
    }

    /// What the client draws over the widget changed with the bubble's layout (docs/sdk-v1.md §2.17, §5.12): a caption
    /// added or removed by an edit, an "edited" mark that widens the time. The renderer gets the root variables and the
    /// widget `contentSafeAreaInset` and `contentSafeAreaChanged`, from this one call; never per frame.
    public func setContentSafeArea(_ area: WidgetContentSafeArea) {
        guard executor.isCurrent else {
            return executor.async { [weak self] in self?.setContentSafeArea(area) }
        }
        guard !destroyed else { return }
        config.contentSafeArea = area
        toRenderer(area.rendererMessage)
        engine.contentSafeArea(area)
    }

    /// The bot's data changed (`updateMessageWidgetData`, or a new `public_data` from polling the shared object;
    /// docs/sdk-v1.md §2.13): the full state of both strings. The widget gets `chatDataChanged` and then
    /// `publicDataChanged` for each that differs; a copy keeps no `chatData`, only its `publicData` changes; a field
    /// over 1024 bytes (UTF-8) or with a NUL is null for the widget (`data-rejected`). Before the widget starts, it
    /// starts with this data.
    public func setData(_ data: WidgetData) {
        guard executor.isCurrent else {
            return executor.async { [weak self] in self?.setData(data) }
        }
        guard !destroyed else { return }
        config.data = data
        if engineStarted { engine.setData(data) }
    }

    /// The last render as an image, for an owner that is about to `destroy()` a
    /// bubble scrolled far away and wants to keep its picture. nil when the
    /// renderer cannot snapshot (not a WebView, or WebKit refused).
    public func snapshot(completion: @escaping (PlatformImage?) -> Void) {
        guard executor.isCurrent else {
            return executor.async { [weak self] in
                guard let self = self else { return DispatchQueue.main.async { completion(nil) } }
                self.snapshot { image in DispatchQueue.main.async { completion(image) } }
            }
        }
        if destroyed { return completion(nil) }
        renderer.snapshot(completion: completion)
    }

    /// Sends a newer version of the widget's HTML into the running sandbox (`morphUpdates`); `completion` runs on main.
    public func morph(html: String, completion: @escaping (Bool) -> Void) {
        engine.morph(html: html) { applied in
            DispatchQueue.main.async { completion(applied) }
        }
    }

    /// Off-screen pause. The owner reports whether the bubble is on screen
    /// (scrolled away, app in the background, …); the view's own window
    /// attachment is combined with it. Hidden: no animation frames, timers at
    /// 1 Hz, `document.hidden` / `visibilitychange` inside the widget.
    public func setVisible(_ visible: Bool) {
        guard executor.isCurrent else {
            return executor.async { [weak self] in self?.setVisible(visible) }
        }
        ownerVisible = visible
        pushVisibility()
    }

    private func pushVisibility() {
        engine.setVisible(ownerVisible && inWindow)
        sideChannels.forEach { $0.setVisible(ownerVisible && inWindow) }
    }


    public func destroy() {
        guard executor.isCurrent else {
            return executor.async { [self] in destroy() }
        }
        if destroyed { return }
        destroyed = true
        engine.terminate()
        sideChannels.forEach { $0.destroy() }
        renderer.onMessage = nil
        renderer.onDiag = nil
        if options.pools, options.recyclesRenderers, let web = renderer as? RendererWebView {
            RendererPool.shared.recycle(web)
        } else {
            renderer.destroy()
        }
        resolveQuery(json: "{\"error\":\"dead\"}", binary: nil)
        validatorLock.lock()
        validatorReleased = true
        validatorLock.unlock()
        if options.pools, TrustedValidator.isShared(validator.thread) { ValidatorPool.shared.recycle(validator) }
    }

    // MARK: engine → host (engine queue)

    /// Validation runs where the options say: on the executor, or on the
    /// validator's thread with only the renderer call on the executor. Both keep
    /// the order of ops, queries, bridge calls and diagnostics: one serial
    /// validator, one serial paced queue.
    private let validatorLock = NSLock()
    private var validatorReleased = false

    private func validate(_ work: @escaping () -> Void) {
        let guarded = { [weak self] in
            guard let self = self else { return }
            self.validatorLock.lock()
            let released = self.validatorReleased
            self.validatorLock.unlock()
            if !released { work() }
        }
        if options.asyncValidation {
            validator.perform(guarded)
        } else {
            executor.paced(guarded)
        }
    }

    private func deliver(_ block: @escaping () -> Void) {
        if options.asyncValidation {
            executor.paced(block)
        } else {
            block()
        }
    }

    private func inOrder(_ block: @escaping () -> Void) {
        validate { [self] in deliver(block) }
    }

    public func engineTaskStarted(_ engine: SandboxEngine) {
        guard renderer is TaskRenderer else { return }
        inOrder { self.toRenderer(.beginTask) }
    }

    public func engineTaskCompleted(_ engine: SandboxEngine) {
        guard renderer is TaskRenderer else { return }
        inOrder { self.toRenderer(.endTask) }
    }

    public func engine(_ engine: SandboxEngine, ops json: String) {
        self.engine(engine, ops: json, canvas: [:])
    }

    public func engine(_ engine: SandboxEngine, ops json: String, canvas: [Int: WidgetCanvasBatch]) {
        validate { [self] in
            let batch = validator.processOpsForRenderer(json, canvas: canvas, typed: typedOps)
            let nodes = validator.nodeCount
            let stopped = validator.stopped
            deliver {
                // A counted cap passed under strict: the widget stops like it does for memory or CPU (the renderer keeps its last render).
                if let stopped { return self.die(stopped) }
                switch batch {
                case .empty:
                    break
                case .json(let clean):
                    self.opsSent += 1
                    self.toRenderer(.json("{\"type\":\"ops\",\"ops\":\(clean)}"))
                case .ops(let ops):
                    self.opsSent += 1
                    self.toRenderer(.ops(ops))
                }
                self.state.nodes = nodes
            }
        }
    }

    public func engine(_ engine: SandboxEngine, binaryOps: BinaryOpsPayload, canvas: [Int: WidgetCanvasBatch]) {
        validate { [self] in
            let batch = validator.processBinary(binaryOps, canvas: canvas, typed: typedOps)
            let nodes = validator.nodeCount
            let stopped = validator.stopped
            deliver {
                if let stopped { return self.die(stopped) }
                switch batch {
                case .empty:
                    break
                case .json(let clean):
                    self.opsSent += 1
                    self.toRenderer(.json("{\"type\":\"ops\",\"ops\":\(clean)}"))
                case .ops(let ops):
                    self.opsSent += 1
                    self.toRenderer(.ops(ops))
                }
                self.state.nodes = nodes
            }
        }
    }

    public func engine(_ engine: SandboxEngine, query json: String) -> (json: String, binary: Data?) {
        let pending = PendingQuery()
        queryLock.lock()
        pendingQuery = pending
        queryLock.unlock()
        validate { [self] in
            // Validation (css-tree, media query rewrite) on the validator's thread or inline; the send on main.
            var clean = validator.validateQuery(json)
            if let c = clean, c.contains("\"matchMedia\""), var obj = SandboxEngine.parse(c) as? [String: Any], let q = obj["query"] as? String {
                obj["query"] = validator.rewriteMediaQuery(q)
                clean = Self.json(obj)
            }
            let send = {
                guard !self.destroyed else { return self.resolveQuery(json: "{\"error\":\"dead\"}", binary: nil) }
                guard let clean else { return self.resolveQuery(json: "{\"error\":\"invalid query\"}", binary: nil) }
                self.qid += 1
                pending.id = self.qid
                self.toRenderer("{\"type\":\"query\",\"qid\":\(self.qid),\"query\":\(clean)}")
            }
            deliver(send)
        }
        if pending.semaphore.wait(timeout: .now() + 3) == .timedOut {
            queryLock.lock()
            if pendingQuery === pending { pendingQuery = nil }
            queryLock.unlock()
            return ("{\"error\":\"timeout\"}", nil)
        }
        return (pending.json, pending.binary)
    }

    private func resolveQuery(json: String, binary: Data?) {
        queryLock.lock()
        let p = pendingQuery
        pendingQuery = nil
        queryLock.unlock()
        guard let p else { return }
        p.json = json
        p.binary = binary
        p.semaphore.signal()
    }

    public func engine(_ engine: SandboxEngine, listen json: String) {
        validate { [self] in
            let types = validator.validateListen(json)
            deliver { self.toRenderer("{\"type\":\"listen\",\"types\":\(types)}") }
        }
    }
    public func engine(_ engine: SandboxEngine, preventDefault json: String) {
        validate { [self] in
            let keys = validator.validatePreventDefault(json)
            deliver { self.toRenderer("{\"type\":\"preventDefault\",\"keys\":\(keys)}") }
        }
    }
    public func engine(_ engine: SandboxEngine, anim json: String) {
        validate { [self] in
            let clean = validator.validateAnim(json)
            guard clean != "[]" else { return }
            deliver { self.toRenderer("{\"type\":\"anim\",\"now\":\(CACurrentMediaTime() * 1000),\"anims\":\(clean)}") }
        }
    }
    public func engineRequestsFrame(_ engine: SandboxEngine) {
        executor.paced { self.toRenderer("{\"type\":\"requestFrame\"}") }
    }
    public func engine(_ engine: SandboxEngine, log level: String, text: String) {
        inOrder {
            if self.state.logs.count < 5000 { self.state.logs.append((level, text)) }
            if level == "error", text.hasPrefix("Uncaught ") {
                self.state.lastError = text
                self.tasksAtError = self.state.stats.tasks
                let onError = self.onError
                self.onMain { onError?(text) }
            }
            self.changed()
        }
    }
    private var tasksAtError = 0
    public func engine(_ engine: SandboxEngine, diag kind: String, detail: String) {
        inOrder { self.diag(kind, detail) }
    }
    public func engine(_ engine: SandboxEngine, bridge method: String, argsJson: String) {
        inOrder {
            guard self.state.dead == nil else { return }
            if self.state.bridge.count >= WidgetDiagnostics.maxActions { self.state.bridge.removeFirst(WidgetDiagnostics.maxActions / 2) }
            self.state.bridge.append((method, argsJson))
            self.changed()
            self.onMain { self.bridgeHandler?(method, argsJson) }
        }
    }
    public func engine(_ engine: SandboxEngine, action: WidgetAction) {
        inOrder {
            guard self.state.dead == nil else { return } // the rest of the task in which the validator stopped the widget
            if self.state.actions.count >= WidgetDiagnostics.maxActions { self.state.actions.removeFirst(WidgetDiagnostics.maxActions / 2) }
            self.state.actions.append(action)
            self.changed()
            self.onMain { self.onAction?(action) }
        }
    }
    /// Not an action: the latest anchor goes into the state next to the height, through the same ordered pipeline
    /// as the ops (validate, then deliver), so it is there before the renderer gets the ops of the same task and
    /// before their size report; an equal value changes nothing, and nothing is called per call.
    public func engine(_ engine: SandboxEngine, resizeAnchor: WidgetResizeAnchor) {
        inOrder {
            guard self.state.resizeAnchor != resizeAnchor else { return }
            self.state.resizeAnchor = resizeAnchor
            self.changed()
        }
    }
    /// Not an action either: whether the widget watches its shared `public_data` goes into the state, in order with
    /// the ops, and to the client's `onWatchPublicData` on a change only.
    public func engine(_ engine: SandboxEngine, watchesPublicData on: Bool) {
        inOrder {
            guard self.state.watchesPublicData != on else { return }
            self.state.watchesPublicData = on
            self.changed()
            self.onMain { self.onWatchPublicData?(on) }
        }
    }
    /// A request that passed the engine's gate, to the client's `onRequest` on main, in order with the actions. The
    /// client side is decided here, at delivery: without `onRequest` now the widget gets `NOT_ALLOWED` (`unsupported`).
    /// The engine honours the first call of `respond` only.
    public func engine(_ engine: SandboxEngine, request: WidgetRequest, respond: WidgetRequestCompletion) {
        inOrder {
            guard self.state.dead == nil else { return }
            self.onMain {
                guard let onRequest = self.onRequest else { return respond.unsupported() }
                onRequest(request) { respond($0) }
            }
        }
    }
    /// A wallet call that passed the engine's gate, to the client's `onWallet` on main, in order with the actions;
    /// without `onWallet` at delivery the widget gets `NOT_ALLOWED` (`unsupported`).
    public func engine(_ engine: SandboxEngine, wallet: WalletCall, respond: WidgetRequestCompletion) {
        inOrder {
            guard self.state.dead == nil else { return }
            self.onMain {
                guard let onWallet = self.onWallet else { return respond.unsupported() }
                onWallet(wallet) { respond($0) }
            }
        }
    }
    /// Not an action: the client copies the text and shows what it copied.
    public func engine(_ engine: SandboxEngine, copyText: String) {
        inOrder {
            guard self.state.dead == nil else { return }
            self.onMain { self.onCopyText?(copyText) }
        }
    }
    public func engine(_ engine: SandboxEngine, storageCommit: SdkPolicy.StorageCommit) {
        inOrder {
            guard self.state.dead == nil else { return }
            self.onMain {
                if let local = storageCommit.local { self.onStorageCommit?(.local, local) }
                if let session = storageCommit.session { self.onStorageCommit?(.session, session) }
            }
        }
    }
    public func engineLoaded(_ engine: SandboxEngine) {
        let loadedMs = Date().timeIntervalSince(createdAt) * 1000
        // Through the validation pipeline, so `loaded` is observed after the ops of the loading tasks were sent;
        // then one trusted query to the renderer: its answer comes after every size report those ops produced.
        validate { [self] in
            validator.markLoaded() // after the loading tasks' ops: text changes are rate-limited from here on
            let deliver = {
                self.state.loaded = true
                self.state.loadedMs = loadedMs
                self.changed()
                self.toRenderer("{\"type\":\"query\",\"qid\":\(Self.renderAckQid),\"query\":{\"q\":\"viewport\"}}")
            }
            self.deliver(deliver)
        }
    }
    /// qid of the host's own post-load ack query; engine queries count up from 1.
    private static let renderAckQid = -1
    public func engine(_ engine: SandboxEngine, stats: SandboxStats) {
        executor.async {
            self.state.stats = stats
            if self.state.lastError != nil, stats.tasks > self.tasksAtError + 1 { self.state.lastError = nil }
            self.changed()
        }
    }
    public func engine(_ engine: SandboxEngine, died reason: String) {
        executor.paced { self.die(reason) }
    }

    /// The runtime is gone (memory, CPU, a counted cap): the reason goes into the diagnostics, the renderer keeps the last
    /// render frozen, the engine is stopped (a no-op when it died by itself). Executor.
    private func die(_ reason: String) {
        if state.dead != nil { return }
        state.dead = reason
        changed()
        resolveQuery(json: "{\"error\":\"dead\"}", binary: nil)
        // The renderer stays: last render as fallback, badge from the renderer, no more events or frames.
        toRenderer(Self.json(["type": "frozen", "reason": stoppedLabel, "fatal": true]))
        engine.stop(reason: reason)
    }

    /// Trusted script evaluated in the renderer (tests and diagnostics only); "null" from a renderer without script.
    public func evaluateInRenderer(_ script: String, completion: @escaping (String) -> Void) {
        guard executor.isCurrent else {
            return executor.async { [weak self] in self?.evaluateInRenderer(script) { value in DispatchQueue.main.async { completion(value) } } }
        }
        renderer.evaluate(script) { completion($0 ?? "null") }
    }

    // MARK: renderer → host (main thread)

    private func onRenderer(_ json: String) {
        guard let msg = SandboxEngine.parse(json) as? [String: Any], let type = msg["type"] as? String else { return }
        switch type {
        case "ready":
            if rendererReady { return }
            rendererReady = true
            state.rendererReadyMs = Date().timeIntervalSince(createdAt) * 1000
            let queued = pendingToRenderer
            pendingToRenderer = []
            queued.forEach(deliver)
            startEngine()
        case "event":
            // A preview takes no input: a tap on it selects the result (the client's), and the widget gets nothing.
            if options.context.preview { return }
            if let ev = msg["ev"] {
                // Any pointerdown begins a press, trusted or not: a second finger's is neither trusted nor claimed (`gesture`
                // is the primary pointer's), and without this its release continued the press before it.
                if ((ev as? [String: Any])?["type"] as? String) == "pointerdown" { pressBegan() }
                // Only input the renderer saw as real (the DOM event's isTrusted; every input event of a native renderer) is a gesture.
                if (msg["trusted"] as? Bool) == true, let gesture = gesture(for: ev) {
                    engine.activationEvent(json: Self.json(ev), gesture: gesture)
                } else {
                    engine.event(json: Self.json(ev))
                }
            }
        case "result":
            guard let id = (msg["qid"] as? NSNumber)?.intValue else { return }
            if id == Self.renderAckQid {
                if !state.rendered {
                    state.rendered = true
                    state.renderedMs = Date().timeIntervalSince(createdAt) * 1000
                    changed()
                }
                return
            }
            queryLock.lock()
            let match = pendingQuery?.id == id
            queryLock.unlock()
            guard match else { return }
            let binary = (msg["binaryBase64"] as? String).flatMap { Data(base64Encoded: $0) }
            resolveQuery(json: Self.json(msg["result"] ?? NSNull()), binary: binary)
        case "size":
            let height = min(max(CGFloat((msg["height"] as? NSNumber)?.doubleValue ?? 0), minHeight), maxHeight)
            if state.firstContentMs == nil, opsSent > 0, (msg["painted"] as? Bool) != false {
                state.firstContentMs = Date().timeIntervalSince(createdAt) * 1000
                changed()
            }
            if height != state.height {
                state.height = height
                changed()
            }
            let vp = ((msg["innerWidth"] as? NSNumber)?.doubleValue ?? viewport.w, (msg["innerHeight"] as? NSNumber)?.doubleValue ?? viewport.h, (msg["dpr"] as? NSNumber)?.doubleValue ?? viewport.dpr)
            if vp != viewport {
                viewport = vp
                engine.viewport(innerWidth: vp.0, innerHeight: vp.1, dpr: vp.2)
            }
        case "resize":
            if let entries = msg["entries"] { engine.resize(entriesJson: Self.json(entries)) }
        case "frame":
            frameCount += 1
            engine.frame(time: (msg["time"] as? NSNumber)?.doubleValue ?? 0)
        case "media":
            // OS-level change inside the renderer: the host theme is authoritative, just re-notify.
            engine.theme(scheme, params: theme.params)
        case "gesture":
            // A primary press began (renderer-made: the widget cannot fake it): haptics are free while it lasts, and 5 s
            // after. Before the preview check: harmless, a preview plays no haptics anyway.
            engine.pressBegan()
            pressBegan()
            if options.context.preview { return }
            // The renderer core sends x, y and hold (`wants` is y, always false since v0.1.3). Vertical drags are the
            // chat's: the claim the client gets has `y` false whatever the renderer says. A renderer that sends only
            // `wants` (the Blitz and litehtml prototypes) claims nothing.
            let claim = WidgetGestureClaim(x: Self.isTrue(msg["x"]), y: false, hold: Self.isTrue(msg["hold"]))
            onMain { self.onGestureClaim?(claim) }
        case "release":
            // The primary press ended (pointerup or pointercancel, listened to or not).
            engine.pressEnded()
        case "limit":
            // Past a paint limit (16 heavy boxes, 2 with backdrop-filter; the WebView renderer's message or a client
            // renderer's `limitExceeded`): the renderer drew the frame without the effects, and the widget stops as for
            // memory or CPU. compat counts nothing.
            guard let limit = (msg["code"] as? String).flatMap(WidgetPaintLimit.init(rawValue:)), let count = (msg["count"] as? NSNumber)?.doubleValue, count.isFinite, count >= 0 else {
                return diag("renderer-violation", "a limit report with an unknown code or no count")
            }
            if options.policy == .compat || state.dead != nil { return }
            let reason = limit.message(count: Int(count))
            diag(limit.rawValue, reason)
            die(reason)
        case "violation":
            diag("renderer-violation", msg["detail"] as? String ?? "")
        default:
            break
        }
    }

    private func toRenderer(_ json: String) {
        toRenderer(.json(json))
    }

    private func toRenderer(_ message: Outgoing) {
        if destroyed { return }
        if rendererReady { deliver(message) } else { pendingToRenderer.append(message) }
    }

    private func deliver(_ message: Outgoing) {
        switch message {
        case .json(let json): renderer.send(json)
        case .ops(let ops): renderer.send(ops: ops)
        case .beginTask: (renderer as? TaskRenderer)?.beginTask()
        case .endTask: (renderer as? TaskRenderer)?.endTask()
        }
    }

    private func diag(_ kind: String, _ detail: String) {
        guard executor.isCurrent else {
            return executor.async { [weak self] in self?.diag(kind, detail) }
        }
        var entry = state.diags[kind] ?? (0, [])
        entry.count += 1
        if entry.details.count < 50 { entry.details.append(detail) }
        state.diags[kind] = entry
        changed()
    }

    private var changeScheduled = false
    private func publish() {
        publishLock.lock()
        published = state
        publishLock.unlock()
    }

    private func changed() {
        // Coalesce UI updates: a misbehaving widget can emit thousands of diagnostics per second.
        onChangeOnExecutor?(state)
        publishLock.lock()
        published = state
        let schedule = !changeScheduled
        changeScheduled = true
        publishLock.unlock()
        guard schedule else { return }
        DispatchQueue.main.async {
            self.publishLock.lock()
            self.changeScheduled = false
            let snapshot = self.published
            self.publishLock.unlock()
            self.onChange?(snapshot)
        }
    }

    static func json(_ value: Any) -> String { SandboxEngine.json(value) }

    /// A JSON `true` (not a number that bridges to one).
    static func isTrue(_ value: Any?) -> Bool {
        guard let n = value as? NSNumber else { return false }
        return CFGetTypeID(n) == CFBooleanGetTypeID() && n.boolValue
    }

    /// Pointer presses only: widgets get no keyboard (docs/decisions.md, "Profile v1"), so no key is a gesture.
    static let activationEventTypes: Set<String> = ["click", "dblclick", "mousedown", "mouseup", "pointerdown", "pointerup"]
    private var gestureNumber = 0
    private var pressOpen = false
    private var lastActivationType = ""

    /// One physical pointer press (pointerdown … click / dblclick) is one gesture. A click without a click
    /// count (`detail` 0: what Enter or Space on a focused control produces) counts only as the end of an open press.
    /// Twin of GestureCounter in user-activation.ts, UserActivation.kt and widget_host.h.
    private func gesture(for ev: Any) -> Int? {
        guard let event = ev as? [String: Any], let type = event["type"] as? String, Self.activationEventTypes.contains(type) else { return nil }
        let continues: Bool
        switch type {
        case "mousedown": continues = lastActivationType == "pointerdown"
        case "pointerup", "mouseup", "click": continues = pressOpen
        case "dblclick": continues = lastActivationType == "click"
        default: continues = false
        }
        if !continues && !Self.isUserActivation(ev) {
            // A keyboard click ends any chain: a dblclick after it is not the pointer's.
            pressOpen = false
            lastActivationType = ""
            return nil
        }
        if !continues { gestureNumber += 1 }
        pressOpen = type != "click" && type != "dblclick"
        lastActivationType = type
        return gestureNumber
    }

    /// A primary press began: the renderer's `gesture` claim, sent for every one, whether the widget listens or not, and
    /// before the press's own events; or any forwarded `pointerdown`, trusted or not (a second finger's has no claim).
    /// What follows is a new gesture, not the end of an open one: a finger's pointerdown is not `trusted` (it may become
    /// the chat's scroll), so without this its release continued a press that had ended without a click (a drag), whose
    /// gesture was spent. Twin of `GestureCounter.pressBegan` in user-activation.ts, UserActivation.kt and widget_host.h.
    private func pressBegan() {
        pressOpen = false
        lastActivationType = ""
    }

    /// A pointer press or release, or a click with a click count (`detail` > 0). Keys never count.
    static func isUserActivation(_ ev: Any) -> Bool {
        guard let ev = ev as? [String: Any], let type = ev["type"] as? String, activationEventTypes.contains(type) else { return false }
        if type == "click" || type == "dblclick" { return ((ev["detail"] as? NSNumber)?.doubleValue ?? 0) > 0 }
        return true
    }

    static func styleTexts(in html: String, limit: Int = 4) -> [String] {
        var texts: [String] = []
        var rest = html[...]
        while texts.count < limit, let open = rest.range(of: "<style", options: .caseInsensitive), let tagEnd = rest[open.upperBound...].firstIndex(of: ">"),
              let close = rest[rest.index(after: tagEnd)...].range(of: "</style", options: .caseInsensitive) {
            texts.append(String(rest[rest.index(after: tagEnd)..<close.lowerBound]))
            rest = rest[close.upperBound...]
        }
        return texts
    }
}

#if canImport(UIKit)
/// Reports window attachment to the host (the UIKit side of the off-screen pause).
final class WidgetContainerView: UIView {
    var onWindowChange: ((Bool) -> Void)?
    override func didMoveToWindow() {
        super.didMoveToWindow()
        onWindowChange?(window != nil)
    }
}
#elseif canImport(AppKit)
/// The AppKit side of the off-screen pause: the view counts as hidden when it
/// has no window or when that window is not visible on any screen (minimised,
/// fully covered, on another Space — `NSWindow.occlusionState`).
final class WidgetContainerView: NSView {
    var onWindowChange: ((Bool) -> Void)?
    private var occlusionObserver: NSObjectProtocol?
    /// `occlusionState` is trusted once the window server has reported on this
    /// window at all (a notification, or `.visible` seen). A process that never
    /// finishes launching NSApplication — the xctest runner — gets no occlusion
    /// updates and its windows stay "not visible" forever; `isVisible` is the
    /// fallback there.
    private var occlusionTrusted = false

    override func viewDidMoveToWindow() {
        super.viewDidMoveToWindow()
        if let occlusionObserver { NotificationCenter.default.removeObserver(occlusionObserver) }
        occlusionObserver = nil
        occlusionTrusted = false
        if let window {
            occlusionObserver = NotificationCenter.default.addObserver(forName: NSWindow.didChangeOcclusionStateNotification, object: window, queue: .main) { [weak self] _ in
                self?.occlusionTrusted = true
                self?.report()
            }
        }
        report()
    }

    private func report() {
        guard let window else { return onWindowChange?(false) ?? () }
        let occluded = !window.occlusionState.contains(.visible)
        if !occluded { occlusionTrusted = true }
        onWindowChange?(occlusionTrusted ? !occluded : window.isVisible)
    }

    deinit {
        if let occlusionObserver { NotificationCenter.default.removeObserver(occlusionObserver) }
    }
}
#endif

private final class PendingQuery {
    let semaphore = DispatchSemaphore(value: 0)
    var id = 0
    var json = "null"
    var binary: Data?
}

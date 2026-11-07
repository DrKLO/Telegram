// The sandbox engine: the native counterpart of src/host/worker.ts. Owns one
// QuickJS runtime per widget on a private serial queue, exposes exactly one
// capability to the untrusted code (`__host(kind, arg)` + `__hostBinary()`),
// enforces CPU and memory limits, and drives timers / frames / scripts.
//
// Everything that comes out of the context is UNTRUSTED: the delegate must
// pass it through the validator before it reaches the renderer.

import Foundation
import WidgetSandboxProtocol
import QuartzCore

/// Mirrors `SandboxPolicy` in src/shared/protocol.ts. `strict` (the default everywhere): the link calls and copyText
/// (`SdkPolicy.privilegedMethods`) need a user gesture, a request one unless the context allows background requests;
/// visible text is budgeted, and past a counted cap (32 KB per text value, 48 KB of document text, 8 KB per attribute,
/// 10 000 ops per batch, 10 000 canvas commands per send, 400 CSS rules, 32 `@keyframes`) the widget stops
/// (docs/decisions.md, "Strict limits from the start").
/// `compat`: the behaviour before that hardening, for tests and tooling.
public enum SandboxPolicy: String {
    case compat
    case strict
}

public struct SandboxConfig {
    public var memoryLimitBytes = 8 << 20
    /// Longest continuous run of widget JS in one task before the runtime is killed.
    public var cpuLimitMs = 500.0
    /// JS time the widget may use per `cpuQuotaWindowMs` (sliding window); beyond it timers and frames are deferred, never killed.
    public var cpuQuotaMs = 300.0
    public var cpuQuotaWindowMs = 1000.0
    /// Kill after this long of uninterrupted throttling; 0 disables (default).
    public var cpuKillAfterMs = 0.0
    /// The host's main-thread work for the widget per `cpuQuotaWindowMs`, apart from the JS quota: the time its layout
    /// queries block the renderer and the modelled cost of the ops it sends (`CpuBudget.opsCostMs`); beyond it timers and
    /// frames are deferred like under the JS quota.
    public var mainThreadQuotaMs = 100.0
    /// Live timers a widget may hold; the shim throws RangeError beyond it, the engine refuses as a backstop.
    public var maxTimers = 64
    /// Initial visibility (see `setVisible`): hidden widgets get no frames and timers at 1 Hz.
    public var visible = true
    public var innerWidth: Double
    public var innerHeight: Double
    public var devicePixelRatio: Double
    public var theme: String
    /// Optional wire forms the host understands (docs/protocol.md, "Features"); empty means plain JSON.
    /// `canvasWords`: canvas commands arrive as word payloads and are spliced back into the ops JSON.
    public var features: Set<String> = []
    public var policy: SandboxPolicy = .strict
    /// The chat's palette (docs/sdk-v1.md §2.3), checked at boot (`SdkPolicy.checkThemeParams`); missing keys take the scheme's defaults.
    public var themeParams: [String: String] = [:]
    /// The client's UI language, BCP 47 (`navigator.language`, the Intl default); `en-US` when malformed (`SdkPolicy.checkLocale`).
    public var locale = SdkPolicy.defaultLocale
    /// What the client draws over the widget at boot (docs/sdk-v1.md §2.17); later changes go through `contentSafeArea(_:)`.
    public var contentSafeArea = WidgetContentSafeArea()
    /// The client's Web Storage preload (docs/sdk-v1.md §2.8); over the quota it is dropped whole at boot (`storage-rejected`).
    public var storage = WidgetStorage()
    /// The bot's `chatData` and `publicData` at start (docs/sdk-v1.md §2.13), checked at boot (`SdkPolicy.checkWidgetData`):
    /// a field over 1024 bytes or with a NUL is null for the widget (`data-rejected`), a copy gets no `chatData`; later
    /// edits go through `setData`.
    public var data = WidgetData()
    /// The message's flags from the server (`isCopy`, `canQuery`, `backgroundQuery`), the chat type, the viewer and
    /// whether this is a preview (docs/sdk-v1.md §4.4); fixed for the engine's life. Without `canQuery` every request
    /// is refused; a preview takes no actions and commits no storage.
    public var context = WidgetContext()

    public init(innerWidth: Double, innerHeight: Double, devicePixelRatio: Double, theme: String) {
        self.innerWidth = innerWidth
        self.innerHeight = innerHeight
        self.devicePixelRatio = devicePixelRatio
        self.theme = theme
    }
}

public struct SandboxStats {
    public var jsMs = 0.0, tasks = 0, memoryBytes = 0, ops = 0, queries = 0, queryMs = 0.0, queryCacheHits = 0, missedMutations = 0
    /// Tasks deferred by the CPU quota; JS time used in the current quota window at the time of the report.
    public var throttledTasks = 0, cpuWindowMs = 0.0
    /// The validator's CPU time on this widget's messages (`chargeValidator`), charged to the JS quota next to `jsMs`.
    public var validatorMs = 0.0
}

/// All delegate calls happen on the engine's queue, never on the main thread.
/// `query` is synchronous: it may block the engine queue (and must not block the main thread).
public protocol SandboxEngineDelegate: AnyObject {
    func engineTaskStarted(_ engine: SandboxEngine)
    func engineTaskCompleted(_ engine: SandboxEngine)
    func engine(_ engine: SandboxEngine, ops json: String)
    /// An ops batch whose `{"op":"canvas","id":N,"bin":K}` placeholders stand for `canvas[K]` (canvas words).
    /// The default splices them back into JSON and calls `engine(_:ops:)`.
    func engine(_ engine: SandboxEngine, ops json: String, canvas: [Int: WidgetCanvasBatch])
    /// A binary op batch (`binaryOps`), copied out of the runtime. The default decodes it with the engine's
    /// intern table into the same JSON as the JSON path; a host with a native node model validates it directly.
    func engine(_ engine: SandboxEngine, binaryOps payload: BinaryOpsPayload, canvas: [Int: WidgetCanvasBatch])
    func engine(_ engine: SandboxEngine, query json: String) -> (json: String, binary: Data?)
    func engine(_ engine: SandboxEngine, listen json: String)
    func engine(_ engine: SandboxEngine, preventDefault json: String)
    func engineRequestsFrame(_ engine: SandboxEngine)
    func engine(_ engine: SandboxEngine, log level: String, text: String)
    func engine(_ engine: SandboxEngine, diag kind: String, detail: String)
    /// The raw method and arguments of each bridge call that passed every check, right after `engine(_:action:)`.
    func engine(_ engine: SandboxEngine, bridge method: String, argsJson: String)
    /// A bridge call that passed every check (docs/sdk-v1.md §3.2): the arguments, user activation for the links, the haptic limit.
    func engine(_ engine: SandboxEngine, action: WidgetAction)
    /// The Web Storage areas a task changed, in full, within the quota (docs/sdk-v1.md §3.2); at least one area is set.
    func engine(_ engine: SandboxEngine, storageCommit: SdkPolicy.StorageCommit)
    /// A checked `setResizeAnchor` call (docs/sdk-v1.md §2.12): no action, no gesture, not gated by visibility. The shim
    /// sends only changes, in the task before that task's ops; the host keeps the latest next to the height.
    func engine(_ engine: SandboxEngine, resizeAnchor: WidgetResizeAnchor)
    /// A checked `watchPublicData` call (docs/sdk-v1.md §2.13): whether the widget watches its shared `public_data`.
    /// No action, no gesture; the shim sends only changes; the host keeps the latest and tells the client on a change.
    func engine(_ engine: SandboxEngine, watchesPublicData: Bool)
    /// A request to the bot that passed the request gate (docs/sdk-v1.md §2.14): the client sends it and calls
    /// `respond` once, from any thread, with the bot's answer or a failure; the engine checks the answer and passes it
    /// into the widget. Later calls, and calls after the engine died, are ignored. A delegate without a client side
    /// for requests leaves the default, which refuses it (`NOT_ALLOWED`, reason `unsupported`).
    func engine(_ engine: SandboxEngine, request: WidgetRequest, respond: WidgetRequestCompletion)
    /// A wallet call that passed the request gate (docs/sdk-v1.md §2.18): the client shows its own wallet window with
    /// the bot of the message, sends the transaction (or signs the payload) on the user's confirmation, sends the
    /// outcome to the bot and calls `respond` once, from any thread, with the bot's answer or a failure (`rejected`,
    /// `badRequest`, a request's). A delegate without a client side for the wallet leaves the default, which refuses it.
    func engine(_ engine: SandboxEngine, wallet: WalletCall, respond: WidgetRequestCompletion)
    /// A checked `copyText` call (docs/sdk-v1.md §2.15): one per press (strict), like the link calls; not an action.
    func engine(_ engine: SandboxEngine, copyText: String)
    func engineLoaded(_ engine: SandboxEngine)
    func engine(_ engine: SandboxEngine, stats: SandboxStats)
    func engine(_ engine: SandboxEngine, died reason: String)
    func engine(_ engine: SandboxEngine, anim json: String)
}

public extension SandboxEngineDelegate {
    func engineTaskStarted(_ engine: SandboxEngine) {}
    func engineTaskCompleted(_ engine: SandboxEngine) {}
    func engine(_ engine: SandboxEngine, anim json: String) {}
    func engine(_ engine: SandboxEngine, action: WidgetAction) {}
    func engine(_ engine: SandboxEngine, storageCommit: SdkPolicy.StorageCommit) {}
    func engine(_ engine: SandboxEngine, resizeAnchor: WidgetResizeAnchor) {}
    func engine(_ engine: SandboxEngine, watchesPublicData: Bool) {}
    func engine(_ engine: SandboxEngine, request: WidgetRequest, respond: WidgetRequestCompletion) { respond.unsupported() }
    func engine(_ engine: SandboxEngine, wallet: WalletCall, respond: WidgetRequestCompletion) { respond.unsupported() }
    func engine(_ engine: SandboxEngine, copyText: String) {}

    func engine(_ engine: SandboxEngine, ops json: String, canvas: [Int: WidgetCanvasBatch]) {
        self.engine(engine, ops: canvas.isEmpty ? json : CanvasWords.splice(json, canvas))
    }

    func engine(_ engine: SandboxEngine, binaryOps payload: BinaryOpsPayload, canvas: [Int: WidgetCanvasBatch]) {
        switch BinaryOps.decode(payload, table: engine.internTable) {
        case .success(let decoded):
            self.engine(engine, ops: decoded.tooMany ? "{}" : BinaryOps.json(decoded.ops, canvas: canvas, placeholders: true), canvas: canvas)
        case .failure(let failure):
            self.engine(engine, diag: "protocol-violation", detail: "malformed binary ops batch (\(failure))")
        }
    }
}

public final class SandboxEngine {
    /// Everything below runs on this thread (see JSThread for why not a DispatchQueue).
    public let queue = JSThread(name: "widget.sandbox")
    private let delegateLock = NSLock()
    private weak var _delegate: SandboxEngineDelegate?
    /// Set on the owner's thread, read on the JS thread.
    public var delegate: SandboxEngineDelegate? {
        get {
            delegateLock.lock()
            defer { delegateLock.unlock() }
            return _delegate
        }
        set {
            delegateLock.lock()
            _delegate = newValue
            delegateLock.unlock()
        }
    }

    private var html = ""
    public private(set) var config: SandboxConfig
    private let resources: SandboxResources
    private var js: QuickJSContext!
    /// Set by `prewarm()`: the runtime exists and the sandbox bundle has been evaluated, no widget yet.
    private var prewarmed = false
    private var prewarmRequested = false
    private var started = false
    private var bundleEval: (ms: Double, how: String)?
    private var dead = false
    private var stats = SandboxStats()
    private var timers: [Int: JSThread.Scheduled] = [:]
    private var lastBinary: Data?
    private var lastStatsPost = 0.0
    private var lastMemCheck = 0.0
    private var timerLimitReported = false
    /// Timer delays are clamped to 24 h on every platform (MAX_TIMER_DELAY_MS in protocol.ts).
    private let maxTimerDelay: TimeInterval = 86_400

    // Visibility and the deferred-task queue (mirrors src/host/worker.ts).
    //
    // Hidden widget (`setVisible(false)`): no frames are requested from the
    // renderer (one is requested on return if the shim asked), timers fire at
    // most once per second, batched into one wake-up, and the shim gets
    // `visibilitychange`. Deferred tasks keep their order; one `fire` per timer
    // id, one frame at most.
    private var visible: Bool
    private var frameWanted = false
    private struct Deferred { let kind: String; var arg: String }
    private var deferred: [Deferred] = []
    private var wakeTimer: JSThread.Scheduled?
    private var wakeAt = Double.infinity
    /// Next 1 Hz boundary at which hidden timers fire (0 = none scheduled); CACurrentMediaTime seconds.
    private var hiddenTickAt = 0.0

    // CPU quota (mirrors worker.ts): decide before a task, record after it.
    private var budget: CpuBudget
    /// The main-thread window (`mainThreadQuotaMs`): layout queries and the cost of the ops, apart from the JS quota.
    private var mainBudget: CpuBudget
    private static func mainBudget(_ c: SandboxConfig) -> CpuBudget {
        CpuBudget(cpuLimitMs: c.cpuLimitMs, cpuQuotaMs: c.mainThreadQuotaMs, cpuQuotaWindowMs: c.cpuQuotaWindowMs, cpuKillAfterMs: 0)
    }
    /// Slice given to the running task in ms (for the kill message); the boot task gets cpuLimitMs.
    private var currentSliceMs = 0.0
    private var currentKind = "boot"
    /// JS time of the current task that is ours, not the widget's (evaluating the sandbox bundle), ms.
    private var excludeMs = 0.0
    private var taskQueryMs = 0.0
    private var taskQueries = 0
    /// Modelled main-thread cost of the ops this task sent (`CpuBudget.opsCostMs`).
    private var taskOpsCostMs = 0.0
    /// `"count"` / `"time"` once this task is past its layout-query limit (`CpuBudget.queryLimitHit`).
    private var queryLimitHit: String?
    private var lastThrottleDiag = -Double.infinity
    private var throttledSinceDiag: [String: Int] = [:]
    private static func nowMs() -> Double { CACurrentMediaTime() * 1000 }
    /// Inputs that arrived before the runtime existed (the renderer reports its
    /// size before it announces `ready`); replayed after boot, like the web worker does.
    private var pendingBeforeBoot: [(String, String)] = []
    /// The user gesture whose event task is running, 0 outside one; a gesture unlocks one privileged bridge call, made in its own tasks.
    private var activeGesture = 0
    private var consumedGesture = 0
    private var blockedBridgeReported = 0
    private var rejectedBridgeReported = 0
    private var rejectedStorageReported = 0
    private var haptics = SdkPolicy.HapticGate()
    /// The server's flags, one request to the bot in flight, a press or the background rules (docs/sdk-v1.md §2.14); JS thread only.
    private var requests = SdkPolicy.RequestGate()
    /// A background request the widget made off screen: it goes to the client when the widget appears (`setVisible`); JS thread only.
    private var heldRequest: (id: Int, data: String)?
    private var blockedRequestsReported = 0
    private var previewBlockedReported = 0
    /// The texts of `request-blocked` per refusal (REQUEST_REFUSED in widget-host.ts).
    private static let requestRefused: [SdkPolicy.RequestVerdict: String] = [
        .disabled: "the server allows no requests for this message (no can_query: a widget sent without allow_queries, or a forwarded copy of one sent without fwd_allow_queries)",
        .busy: "a request is already in flight",
        .noActivation: "it needs a tap on the visible widget, one request per tap; this message allows no background requests (no background_query)",
        .rateLimited: "background requests at most once per 5 s",
        .noWallet: "the client has no wallet for this user (no WidgetContext.wallet)",
    ]
    /// Where a wallet call's refusal differs from a request's (WALLET_REFUSED in widget-host.ts).
    private static let walletRefused: [SdkPolicy.RequestVerdict: String] = [
        .noActivation: "it needs a tap on the visible widget, or the answer to a call made by a tap within 10 s; one call each",
        .busy: "a request or a wallet call is already in flight",
    ]
    private static let activationPrefix = "activation:"
    /// Two full areas of the quota at 12 JSON characters per byte (`\uXXXX` escapes plus the quotes, colon and comma of
    /// tiny pairs): a longer commit cannot be within it (`MAX_STORAGE_COMMIT_CHARS` in worker.ts).
    static let maxStorageCommitChars = 2 * 12 * SdkPolicy.storageQuotaBytes + 64

    public init(html: String, config: SandboxConfig, resources: SandboxResources = .shared) {
        self.html = html
        self.config = config
        self.runtimeFeatures = config.features
        self.resources = resources
        visible = config.visible
        budget = CpuBudget(config: config)
        mainBudget = Self.mainBudget(config)
    }

    /// An engine without a widget yet: `prewarm()` builds the runtime and
    /// evaluates the sandbox bundle ahead of time, `start(html:config:)` boots
    /// the widget later (a pool of such engines takes the bundle's evaluation
    /// off the critical path of a bubble that scrolls into view).
    /// An engine for a widget that comes later (`start(html:config:)`), e.g. a pooled one. `features` decides
    /// which optional host functions its runtime gets when it is created; a later config cannot add them.
    public init(resources: SandboxResources = .shared, features: Set<String> = []) {
        self.resources = resources
        self.runtimeFeatures = features
        config = SandboxConfig(innerWidth: 400, innerHeight: 300, devicePixelRatio: 2, theme: "light")
        visible = true
        budget = CpuBudget(config: config)
        mainBudget = Self.mainBudget(config)
    }

    deinit { queue.stop() }

    // MARK: lifecycle

    /// Runtime + sandbox bundle now, widget later. Idempotent; a no-op once started.
    public func prewarm() {
        if prewarmRequested { return }
        prewarmRequested = true
        queue.async {
            guard !self.started && !self.dead else { return }
            // A prewarm that hits a limit (a busy machine) must not leave a dead
            // engine in the pool: drop the runtime and let start() build a fresh one.
            self.prewarming = true
            self.createRuntime()
            self.prewarming = false
            if self.prewarmFailed {
                self.prewarmFailed = false
                self.js = nil
                self.prewarmed = false
                self.bundleEval = nil
            }
        }
    }

    static let hostGlobals = ["__host", "__hostBinary", "__hostCanvas", "__hostOps", "__twDispatch"]
    #if DEBUG
    static var hidesHostFunctions = true
    #else
    static let hidesHostFunctions = true
    #endif
    static let hideHostGlobals = "(function(g){" + hostGlobals.map { "if(!delete g.\($0))throw new Error('\($0)');" }.joined() + "})(globalThis);"

    /// The optional host functions this engine's runtime was built with (fixed at init).
    public let runtimeFeatures: Set<String>

    /// Canvas word payloads (`__hostCanvas`) for the next ops batch, by key.
    private var stagedCanvas: [Int: WidgetCanvasBatch] = [:]
    private var stagedWords = 0
    /// Runs on the engine's thread right after the sandbox bundle booted, before widget code (side channels install their globals here).
    public var afterBoot: ((QuickJSContext) -> Void)?

    /// The intern table of the binary op stream (`binaryOps`); one per runtime.
    /// Used only on the JSON fallback (`engine(_:binaryOps:canvas:)`'s default), on the delegate's thread.
    let internTable = BinaryInternTable()
    /// Binary op batches accepted so far (tests).
    private(set) var binaryBatches = 0
    /// Word payloads accepted so far (tests).
    private(set) var canvasWordPayloads = 0

    private var prewarming = false
    private var prewarmFailed = false

    public var isPrewarmed: Bool { queue.sync { self.prewarmed } }

    /// `JS_ComputeMemoryUsage` of the widget's runtime, nil once it is gone (diagnostics and memory tests).
    public var memoryUsage: [String: Int]? { queue.sync { self.js?.memoryUsage } }
    public func collectGarbage() { queue.async { self.js?.collectGarbage() } }

    public func start() {
        queue.async { self.boot() }
    }

    /// Boots `html` with `config` (the limits, viewport and theme of the host that adopted a prewarmed engine).
    public func start(html: String, config: SandboxConfig) {
        queue.async {
            self.html = html
            self.config = config
            self.visible = config.visible
            self.budget = CpuBudget(config: config)
            self.mainBudget = Self.mainBudget(config)
            self.boot()
        }
    }

    public func terminate() {
        queue.async { self.kill("terminated") }
    }

    /// Stops the widget for `reason` the way a memory or CPU death does (`engine(_:died:)` with it): the host's answer to
    /// the validator's `limit` (a counted cap passed under strict). The running task, if any, ends first.
    public func stop(reason: String) {
        queue.async { self.kill(reason) }
    }

    /// The runtime, the two host functions and the sandbox bundle (ours, so its time is excluded from the widget's quota).
    private func createRuntime() {
        if js != nil { return }
        js = QuickJSContext(memoryLimitBytes: config.memoryLimitBytes, maxStackBytes: JSThread.jsStackLimit)
        js.registerFunction("__host") { [unowned self] args in self.hostCall(kind: args.first ?? "", arg: args.count > 1 ? args[1] : "") }
        if runtimeFeatures.contains("binaryOps") {
            js.registerOpsFunction("__hostOps") { [unowned self] words, strings in
                guard words.count >= 2, words.count <= BinaryOps.maxWords, strings.count <= BinaryOps.maxStringBytes else {
                    delegate?.engine(self, diag: "protocol-violation", detail: "binary ops batch too large")
                    return
                }
                let canvas = stagedCanvas
                stagedCanvas = [:]
                stagedWords = 0
                stats.ops += Int(words[1])
                taskOpsCostMs += CpuBudget.opsCostMs(ops: max(0, Int(words[1])), canvasCommands: Self.canvasCommands(in: canvas))
                binaryBatches += 1
                delegate?.engine(self, binaryOps: BinaryOpsPayload(words: Array(words), strings: Array(strings)), canvas: canvas)
            }
        }
        if runtimeFeatures.contains("canvasWords") {
            js.registerWordsFunction("__hostCanvas") { [unowned self] key, words, strings in
                guard key >= 0, key < Double(CanvasWords.maxPayloads), key == key.rounded(), stagedCanvas.count < CanvasWords.maxPayloads,
                      stagedWords + words.count <= CanvasWords.maxWords, let batch = CanvasWords.batch(words: words, strings: strings) else { return }
                stagedWords += words.count
                stagedCanvas[Int(key)] = batch
                canvasWordPayloads += 1
            }
        }
        js.registerFunction("__hostBinary") { [unowned self] _ in
            guard let data = self.lastBinary else { return .null }
            self.lastBinary = nil
            return .arrayBuffer(data)
        }
        let t0 = CACurrentMediaTime()
        // Generous one-off budget for our own bundle; its time is ours, not the widget's.
        js.deadline = ProcessInfo.processInfo.systemUptime + max(config.cpuLimitMs, 5000) / 1000
        let how = evalBundle(resources.sandboxBundle, bytecode: resources.sandboxBytecode, filename: "sandbox-bundle.js")
        js.deadline = .infinity
        js.cpuDeadline = .infinity
        // Fail closed: a runtime whose shim did not load completely (its network
        // stubs and DOM capture may be missing) never runs widget code.
        if how == "failed" {
            if !dead && !prewarmFailed { kill("sandbox bundle failed to load") }
            return
        }
        // The bundle holds private references to the host functions; widget code
        // must not reach them, nor the shim's dispatcher, through the global object.
        guard js.captureGlobalFunction("__twDispatch"), !Self.hidesHostFunctions || (try? js.evaluate(Self.hideHostGlobals, filename: "hide-host.js")) != nil else {
            if !dead && !prewarmFailed { kill("sandbox bundle failed to load") }
            return
        }
        bundleEval = ((CACurrentMediaTime() - t0) * 1000, how)
        js.collectGarbage()
        prewarmed = true
    }

    private func boot() {
        if started || dead { return }
        started = true
        let wasPrewarmed = js != nil
        createRuntime()
        if dead { return }
        js.setMemoryLimit(config.memoryLimitBytes)
        if let bundleEval {
            delegate?.engine(self, diag: "boot", detail: String(format: "sandbox bundle eval %.0f ms (%@%@)", bundleEval.ms, bundleEval.how, wasPrewarmed ? ", prewarmed" : ""))
        }
        task("boot", sliceMs: config.cpuLimitMs) {
            var bootConfig: [String: Any] = [
                "memoryLimitBytes": self.config.memoryLimitBytes, "cpuLimitMs": self.config.cpuLimitMs,
                "cpuQuotaMs": self.config.cpuQuotaMs, "cpuQuotaWindowMs": self.config.cpuQuotaWindowMs, "cpuKillAfterMs": self.config.cpuKillAfterMs,
                "mainThreadQuotaMs": self.config.mainThreadQuotaMs, "maxTimers": self.config.maxTimers, "visible": self.visible,
                // The shim caps canvas commands per task and live script animations from it, as the validator does (anything but compat is strict).
                "policy": self.config.policy.rawValue,
                "viewport": ["innerWidth": self.config.innerWidth, "innerHeight": self.config.innerHeight, "dpr": self.config.devicePixelRatio],
                "theme": self.config.theme,
                "themeParams": SdkPolicy.checkThemeParams(self.config.themeParams),
                "locale": SdkPolicy.checkLocale(self.config.locale) ?? SdkPolicy.defaultLocale,
                "contentSafeArea": self.config.contentSafeArea.json,
                "profile": SdkPolicy.profileVersion,
                // A preview has no message yet: no storage of its own (it lives in the sandbox for this run only).
                "storage": self.config.context.preview ? [String: Any]() : self.preload(),
                "features": Array(self.config.features.intersection(self.runtimeFeatures)).sorted(),
            ]
            bootConfig.merge(self.widgetContext()) { $1 }
            let bootArg = Self.json(["html": self.html, "config": bootConfig])
            self.callTw("boot", bootArg)
            if !self.dead, let afterBoot = self.afterBoot {
                afterBoot(self.js)
            }
        }
        task("boot", sliceMs: config.cpuLimitMs) { self.callTw("loaded", "") }
        if !dead { delegate?.engineLoaded(self) }
        if !dead { js.collectGarbage() }
        postStats(force: true)
        let queued = pendingBeforeBoot
        pendingBeforeBoot.removeAll()
        for (kind, arg) in queued where !dead { dispatch(kind, arg) }
    }

    /// The client's preload, checked whole (docs/sdk-v1.md §3.1): over the quota the widget starts with empty storage.
    private func preload() -> [String: Any] {
        guard let checked = SdkPolicy.checkStorageCommit(["local": config.storage.local, "session": config.storage.session]) else {
            delegate?.engine(self, diag: "storage-rejected", detail: "the preload is not { local, session } objects of strings within \(SdkPolicy.storageQuotaBytes) bytes each; the widget starts with empty storage")
            return [:]
        }
        var out: [String: Any] = [:]
        if let local = checked.local { out["local"] = local }
        if let session = checked.session { out["session"] = session }
        return out
    }

    /// The bot's data, the server's flags and where the widget is, checked for the shim's boot (docs/sdk-v1.md §3.1):
    /// JSON null for none; a forwarded copy gets no `chatData` and no `chatType` (the bot learns nothing about where its
    /// widget was forwarded), and no `viewer` unless it may request; a preview gets its data but no flags, chat type or
    /// viewer.
    private func widgetContext() -> [String: Any] {
        let context = config.context.effective
        var out = dataFields(SdkPolicy.checkWidgetData(config.data, copy: context.isCopy))
        out["isCopy"] = context.isCopy
        out["canQuery"] = context.canQuery
        out["isPreview"] = context.preview
        // Whatever the widget knows can reach the bot in a request: no copy tells where it was forwarded, and the
        // widget knows its viewer only where it may send requests (canQuery, never in a preview), as the network marker.
        let noChatType = context.preview || context.isCopy
        let noViewer = context.preview || !context.canQuery
        out["chatType"] = Self.orNull(noChatType ? nil : SdkPolicy.checkChatType(context.chatType)?.rawValue)
        out["viewer"] = noViewer ? NSNull() : checkedViewer(context.viewer)
        out["wallet"] = context.wallet
        return out
    }

    /// The viewer in the widget's field names (`WebAppUser`), JSON null for none; each field the client passed that
    /// breaks its rule is left out and reported (`data-rejected`).
    private func checkedViewer(_ viewer: WidgetViewer?) -> Any {
        let checked = SdkPolicy.checkViewer(viewer)
        for field in checked.rejected {
            delegate?.engine(self, diag: "data-rejected", detail: "viewer.\(field): \(SdkPolicy.viewerFieldRules[field] ?? "breaks its rule"); the widget does not see it")
        }
        return Self.orNull(checked.viewer?.json)
    }

    /// The full state of `chatData` and `publicData` as the widget gets it (JSON null for none); the fields the client
    /// passed over the limit are reported (`data-rejected`).
    private func dataFields(_ checked: SdkPolicy.CheckedWidgetData) -> [String: Any] {
        if !checked.rejected.isEmpty {
            delegate?.engine(self, diag: "data-rejected", detail: "\(checked.rejected.joined(separator: " and ")): not text of at most \(SdkPolicy.maxWidgetDataBytes) bytes (UTF-8, no NUL); the widget sees null")
        }
        return ["chatData": Self.orNull(checked.chatData), "publicData": Self.orNull(checked.publicData)]
    }

    // MARK: inputs from the host (all asynchronous)

    public func event(json: String) { enqueue("event", json) }
    /// A real user input event from the renderer (press, click, key press); every event of one physical gesture carries the same increasing `gesture` number.
    public func activationEvent(json: String, gesture: Int) { enqueue(Self.activationPrefix + String(gesture), json) }
    public func frame(time: Double) { enqueue("frame", String(Self.coarseNowMs(time))) }

    /// Widget clocks tick in 100 µs steps, like a browser's `performance.now()`, so they cannot time cache or pool effects of other widgets.
    static let clockResolutionMs = 0.1

    static func coarseNowMs(_ ms: Double) -> Double {
        (ms / clockResolutionMs).rounded(.down) * clockResolutionMs
    }
    public func viewport(innerWidth: Double, innerHeight: Double, dpr: Double) {
        enqueue("viewport", Self.json(["innerWidth": innerWidth, "innerHeight": innerHeight, "dpr": dpr]))
    }
    public func resize(entriesJson: String) { enqueue("resize", entriesJson) }
    /// The chat's theme changed: the colour scheme and the palette (checked here), in one task (docs/sdk-v1.md §3.3).
    public func theme(_ theme: String, params: [String: String] = [:]) {
        enqueue("theme", Self.json(["theme": theme, "themeParams": SdkPolicy.checkThemeParams(params)]))
    }

    /// What the client draws over the widget changed (docs/sdk-v1.md §2.17): `TelegramWidget.contentSafeAreaInset` and
    /// `contentSafeAreaChanged` in one task; the shim checks the values. Before `start` it only replaces `config.contentSafeArea`.
    public func contentSafeArea(_ area: WidgetContentSafeArea) {
        queue.async {
            if self.dead { return }
            guard self.started else { self.config.contentSafeArea = area; return }
            self.enqueueNow("safeArea", Self.json(area.json))
        }
    }

    /// The bot's data changed (docs/sdk-v1.md §2.13): the full state of both strings, checked against this engine's
    /// context (a copy keeps no `chatData`, only its `publicData` changes); the widget gets `chatDataChanged` and then
    /// `publicDataChanged` for each that differs. Before `start` it only replaces `config.data` (a host that starts the
    /// engine with its own config passes the latest data there).
    public func setData(_ data: WidgetData) {
        queue.async {
            if self.dead { return }
            guard self.started else { self.config.data = data; return }
            var event = self.dataFields(SdkPolicy.checkWidgetData(data, copy: self.config.context.effective.isCopy))
            event["type"] = "data"
            self.enqueueNow("bridge", Self.json(event))
        }
    }

    /// In-place document update (`morphUpdates`): the shim patches the live DOM to `html`, keeping node identity,
    /// and fires `widgetupdate`; scripts in `html` do not run. `completion` gets false when the shim did not apply it
    /// (feature off, unreadable document, engine dead or not booted), and is called on the engine's queue.
    public func morph(html: String, completion: @escaping (Bool) -> Void) {
        queue.async {
            guard !self.dead, self.js != nil, self.runtimeFeatures.contains("morphUpdates"), self.config.features.contains("morphUpdates") else {
                completion(false)
                return
            }
            var applied = false
            self.task("morph", sliceMs: self.config.cpuLimitMs) {
                applied = self.callTw("morph", Self.json(["html": html])) == "1"
            }
            completion(applied && !self.dead)
        }
    }

    /// A primary press began on the widget (the renderer's claim at a trusted pointerdown) / ended (its release): haptics
    /// are free while it lasts and 5 s after (`SdkPolicy.HapticGate`). The host's trusted input only, never the widget's
    /// events; the time is taken here, on the caller's thread.
    public func pressBegan() {
        let now = Self.nowMs()
        queue.async { if !self.dead { self.haptics.press(nowMs: now) } }
    }

    public func pressEnded() {
        let now = Self.nowMs()
        queue.async { if !self.dead { self.haptics.release(nowMs: now) } }
    }

    /// The host knows whether the widget is on screen; the sandbox does not.
    public func setVisible(_ v: Bool) {
        queue.async {
            if self.dead || self.visible == v { return }
            self.visible = v
            // On screen again: the widget's next background request may go out at once, the one it made off screen now
            // (docs/sdk-v1.md §2.14), before the widget hears it is visible.
            if v, self.requests.show(nowMs: Self.nowMs()), let held = self.heldRequest {
                self.heldRequest = nil
                self.deliverRequest(id: held.id, data: held.data)
            }
            guard self.js != nil else { return } // boot reads `visible` for the shim
            self.dispatch("visibility", "{\"visible\":\(v)}")
            if !v, !self.dead { self.js.collectGarbage() }
            if v {
                if self.frameWanted { self.frameWanted = false; self.delegate?.engineRequestsFrame(self) }
                if !self.deferred.isEmpty { self.wake() }
            }
        }
    }

    private func enqueue(_ kind: String, _ arg: String) {
        queue.async { self.enqueueNow(kind, arg) }
    }

    /// `enqueue` from a block already on the engine's thread (never from inside a task): in the order of that block.
    private func enqueueNow(_ kind: String, _ arg: String) {
        if dead { return }
        guard js != nil else { pendingBeforeBoot.append((kind, arg)); return }
        // A frame that arrives after the widget was hidden is dropped; the shim's callbacks wait for the next visible frame.
        if kind == "frame", !visible { frameWanted = true; return }
        dispatch(kind, arg)
    }

    /// Runs one task for an input (event, timer, frame, …) if the CPU quota
    /// allows, otherwise defers it (returns false). Events and visibility always run.
    @discardableResult
    private func dispatch(_ kind: String, _ arg: String) -> Bool {
        if dead { return true }
        if kind.hasPrefix(Self.activationPrefix) {
            let gesture = Int(kind.dropFirst(Self.activationPrefix.count)) ?? 0
            activeGesture = visible && gesture > consumedGesture ? gesture : 0
            defer { activeGesture = 0 }
            return dispatch("event", arg)
        }
        let now = Self.nowMs()
        // A deferrable task waits for both windows (the JS quota and the main thread's); the slice is the JS quota's.
        let js = budget.decide(kind, now: now)
        let main = mainBudget.decide(kind, now: now)
        if case .run(let sliceMs) = js, case .run = main {
            task(kind, sliceMs: sliceMs) { self.callTw(kind, arg) }
            return true
        }
        if budget.shouldKill(now) {
            kill(String(format: "CPU quota: throttled for %.1f s (%d ms per %d ms, cpuKillAfterMs=%d)", budget.throttledFor(now) / 1000, Int(config.cpuQuotaMs), Int(config.cpuQuotaWindowMs), Int(config.cpuKillAfterMs)))
            return true
        }
        defer_(kind, arg)
        stats.throttledTasks += 1
        throttledSinceDiag[kind, default: 0] += 1
        if now - lastThrottleDiag >= 1000 {
            lastThrottleDiag = now
            let parts = throttledSinceDiag.sorted { $0.key < $1.key }.map { "\($0.value) \($0.key)\($0.value == 1 ? "" : "s")" }
            throttledSinceDiag.removeAll()
            let window: String
            if case .run = js {
                window = "\(Int(mainBudget.used(now))) ms of the \(Int(config.mainThreadQuotaMs)) ms per \(Int(config.cpuQuotaWindowMs)) ms main-thread budget used by layout queries and ops"
            } else {
                window = "\(Int(budget.used(now))) ms of the \(Int(config.cpuQuotaMs)) ms per \(Int(config.cpuQuotaWindowMs)) ms budget used"
            }
            delegate?.engine(self, diag: "cpu-throttled", detail: "deferred \(parts.joined(separator: ", ")): \(window)")
        }
        var retryAt = 0.0
        if case .defer_(let at) = js { retryAt = max(retryAt, at) }
        if case .defer_(let at) = main { retryAt = max(retryAt, at) }
        scheduleWake(at: retryAt / 1000)
        return false
    }

    private func defer_(_ kind: String, _ arg: String) {
        if kind == "frame", let i = deferred.firstIndex(where: { $0.kind == "frame" }) { deferred[i].arg = arg; return }
        if kind == "timer", deferred.contains(where: { $0.kind == "timer" && $0.arg == arg }) { return } // intervals do not accumulate missed ticks
        deferred.append(Deferred(kind: kind, arg: arg))
    }

    private func scheduleWake(at: Double) {
        if wakeTimer != nil, at >= wakeAt { return }
        wakeTimer?.cancel()
        wakeAt = at
        wakeTimer = queue.after(max(0, at - CACurrentMediaTime())) { [weak self] in self?.wake() }
    }

    /// Runs deferred tasks in order; stops (and re-queues the rest) if one is deferred again.
    private func wake() {
        wakeTimer = nil
        wakeAt = .infinity
        hiddenTickAt = 0
        let items = deferred
        deferred.removeAll()
        for (i, item) in items.enumerated() {
            if dead { return }
            if !dispatch(item.kind, item.arg) {
                deferred.insert(contentsOf: items[i...], at: 0)
                return
            }
        }
    }

    private func onTimerFired(_ id: Int) {
        timers.removeValue(forKey: id)
        if !visible {
            defer_("timer", String(id))
            if hiddenTickAt == 0 { hiddenTickAt = CACurrentMediaTime() + 1 }
            scheduleWake(at: hiddenTickAt)
            return
        }
        dispatch("timer", String(id))
    }

    // MARK: the single capability

    private func hostCall(kind: String, arg: String) -> HostReturn {
        switch kind {
        case "ops":
            let ops = Self.countOccurrences(of: "\"op\":", in: arg)
            stats.ops += ops
            let canvas = stagedCanvas
            stagedCanvas = [:]
            stagedWords = 0
            taskOpsCostMs += CpuBudget.opsCostMs(ops: ops, canvasCommands: Self.countCanvasCommands(inJSON: arg) + Self.canvasCommands(in: canvas))
            delegate?.engine(self, ops: arg, canvas: canvas)
            return .undefined
        case "query":
            return .string(syncQuery(arg))
        case "now":
            return .number(Self.coarseNowMs(CACurrentMediaTime() * 1000))
        case "timer":
            guard let pair = Self.parse(arg) as? [Any], pair.count == 2, let id = (pair[0] as? NSNumber)?.intValue else { return .undefined }
            var delay = min(max(0, (pair[1] as? NSNumber)?.doubleValue ?? 0) / 1000, maxTimerDelay)
            // Hidden: 1 Hz, like a background tab. The timers keep the order of their own delays: all at 1 s, the
            // queue's order by deadline would run the ones set first first (setTimeout 20, 10, 0 → 20, 10, 0).
            if !visible { delay = max(delay, 1 + delay / 10) }
            // An id that has a timer replaces it: overwriting the handle did not cancel the old one (the thread keeps
            // the timer), so a shim told to reuse an id ran timers past maxTimers, still queued after destroy.
            if let replaced = timers.removeValue(forKey: id) {
                replaced.cancel()
            } else if timers.count >= config.maxTimers {
                // Backstop: the shim should have thrown RangeError before asking. Reaching this means a shim bug (or a tampered shim).
                if !timerLimitReported {
                    timerLimitReported = true
                    delegate?.engine(self, diag: "timer-limit-enforced", detail: "engine refused a timer beyond maxTimers=\(config.maxTimers); the shim did not stop it")
                }
                return .undefined
            }
            timers[id] = queue.after(delay) { [weak self] in
                guard let self, !self.dead else { return }
                self.onTimerFired(id)
            }
            return .undefined
        case "clearTimer":
            if let id = Int(arg), let item = timers.removeValue(forKey: id) { item.cancel() }
            return .undefined
        case "raf":
            if !visible { frameWanted = true; return .undefined } // one frame is requested when the widget becomes visible again
            delegate?.engineRequestsFrame(self)
            return .undefined
        case "log":
            let (level, text) = Self.splitPrefix(arg)
            delegate?.engine(self, log: level, text: String(text.prefix(4000)))
            return .undefined
        case "diag":
            let (k, detail) = Self.splitPrefix(arg)
            delegate?.engine(self, diag: String(k.prefix(64)), detail: String(detail.prefix(4000)))
            return .undefined
        case "listen":
            delegate?.engine(self, listen: arg)
            return .undefined
        case "pd":
            delegate?.engine(self, preventDefault: arg)
            return .undefined
        case "anim":
            delegate?.engine(self, anim: arg)
            return .undefined
        case "bridge":
            bridgeCall(arg)
            return .undefined
        case "storage":
            storageCommit(arg)
            return .undefined
        case "intl":
            return .string(IntlBridge.format(requestJson: arg))
        case "random":
            let n = min(max(Int(arg) ?? 0, 0), 65536)
            var bytes = [UInt8](repeating: 0, count: n)
            _ = bytes.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, n, $0.baseAddress!) }
            return .string("[" + bytes.map(String.init).joined(separator: ",") + "]")
        case "stat":
            if let s = Self.parse(arg) as? [String: Any] {
                stats.queryCacheHits = (s["cacheHits"] as? NSNumber)?.intValue ?? 0
                stats.missedMutations = (s["missed"] as? NSNumber)?.intValue ?? 0
            }
            return .undefined
        default:
            return .undefined
        }
    }

    /// docs/sdk-v1.md §3.2, in this order: the method and its arguments, user activation for the privileged calls
    /// (strict: the links and copyText), the haptic gate; then the action and, deprecated, the raw call to the
    /// delegate. `setResizeAnchor` and `watchPublicData` are no actions: once checked they go to the delegate's
    /// `resizeAnchor` / `watchesPublicData` and nowhere else (no gesture, any visibility). A request has its own gate (`request(id:data:)`); copyText goes to the delegate's
    /// `copyText`, not to `action` or `bridge`.
    private func bridgeCall(_ arg: String) {
        let call = Self.parse(arg) as? [Any]
        let name = String(((call?.first).map { $0 as? String ?? "\($0)" } ?? "undefined").prefix(64))
        let checked = call.flatMap { SdkPolicy.checkBridgeCall($0.first, $0.count > 1 ? $0[1] : nil) }
        // A preview may only say it is ready and set its anchor: no request, link, copy or haptic (docs/sdk-v1.md §2.16).
        if let checked, config.context.preview, !SdkPolicy.allowedInPreview(name) {
            if previewBlockedReported < 20 {
                previewBlockedReported += 1
                delegate?.engine(self, diag: "preview-blocked", detail: "\(name) ignored: a preview (an inline result not sent yet) takes no actions")
            }
            switch checked {
            case .request(let id, _, _), .wallet(let id, _):
                enqueue("bridge", Self.json(["type": "answer", "id": id, "error": SdkPolicy.RequestError.notAllowed.rawValue, "reason": "preview"]))
            default:
                break
            }
            return
        }
        if case .setResizeAnchor(let edge) = checked {
            delegate?.engine(self, resizeAnchor: edge)
            return
        }
        if case .watchPublicData(let on) = checked {
            delegate?.engine(self, watchesPublicData: on)
            return
        }
        if case .request(let id, let data, let wallet) = checked {
            request(id: id, data: data, wallet: wallet)
            return
        }
        // A wallet call shares the request gate (a press or the grant, never the background) and goes to the delegate's `wallet`.
        if case .wallet(let id, let call) = checked {
            walletCall(id: id, call: call)
            return
        }
        if case .copyText(let text) = checked {
            if acceptActivation(name) { delegate?.engine(self, copyText: text) }
            return
        }
        guard let call, let checked, let action = WidgetAction(checked) else {
            if rejectedBridgeReported < 20 {
                rejectedBridgeReported += 1
                delegate?.engine(self, diag: "bridge-rejected", detail: "\(name): unknown method or malformed arguments")
            }
            return
        }
        guard acceptActivation(name) else { return }
        if case .haptic = checked {
            switch haptics.decide(nowMs: Self.nowMs(), visible: visible) {
            case .play:
                break
            case .report:
                delegate?.engine(self, diag: "haptic-limit", detail: visible ? "haptics over 5 per burst or 10 per second, or outside a touch after a 1 s series, are dropped" : "haptics of a hidden widget are dropped")
                return
            case .drop:
                return
            }
        }
        let argsJson = (try? JSONSerialization.data(withJSONObject: call[1], options: [.fragmentsAllowed])).map { String(decoding: $0, as: UTF8.self) } ?? "[]"
        delegate?.engine(self, action: action)
        delegate?.engine(self, bridge: name, argsJson: argsJson)
    }

    /// Strict: a privileged call (`SdkPolicy.privilegedMethods`) needs the task's unconsumed gesture on the visible
    /// widget and consumes it, one call per press; false (and `activation-blocked`) without one. Other calls pass.
    private func acceptActivation(_ name: String) -> Bool {
        guard config.policy == .strict, SdkPolicy.privilegedMethods.contains(name) else { return true }
        guard activeGesture > consumedGesture, visible else {
            if blockedBridgeReported < 20 {
                blockedBridgeReported += 1
                delegate?.engine(self, diag: "activation-blocked", detail: "\(name) ignored: it needs a tap on the visible widget and runs in that tap's handler, one call per tap (called from the \(currentKind) task)")
            }
            return false
        }
        consumedGesture = activeGesture
        activeGesture = 0
        return true
    }

    /// docs/sdk-v1.md §2.14 (WidgetHost.request in widget-host.ts): the request gate on the server's flags: none
    /// without `canQuery`; a press unlocks one request (peeked here, consumed only when the request goes out as that
    /// press's, under `strict`); background ones only with `backgroundQuery`, one made off screen held until the widget
    /// appears (`setVisible`); one in flight. Whether the client has a side for requests is decided where it
    /// is delivered, by the delegate (WidgetHost: `onRequest` on the main thread, so a client that sets it right after
    /// creating the host gets a request the widget made before that). Every outcome goes back into the widget as a
    /// `bridge` task of its own, never inside the calling task.
    private func request(id: Int, data: String, wallet: Bool) {
        let pressed = config.policy == .compat || (activeGesture > consumedGesture && visible)
        let context = config.context.effective
        let verdict = requests.decide(nowMs: Self.nowMs(), gesture: pressed, visible: visible, canQuery: context.canQuery, backgroundQuery: context.backgroundQuery, withWallet: wallet, wallet: context.wallet)
        if let error = SdkPolicy.requestRefusal(verdict) {
            let why = wallet && verdict == .noActivation ? "a request for the wallet needs a tap on the visible widget, one request per tap" : Self.requestRefused[verdict] ?? verdict.rawValue
            let event = refusal(id, reason: verdict.rawValue, error: error, detail: "request refused (\(verdict.rawValue)): \(why)")
            return enqueue("bridge", event)
        }
        if verdict == .foreground, config.policy == .strict {
            consumedGesture = activeGesture
            activeGesture = 0
        }
        if verdict == .held { heldRequest = (id, data) } else { deliverRequest(id: id, data: data, wallet: wallet) }
    }

    /// docs/sdk-v1.md §2.18: a wallet call goes out on a press or on the grant (the answer to a call made by a press,
    /// within 10 s), never in the background; one call in flight with the requests. Its outcome settles like a request's.
    private func walletCall(id: Int, call: WalletCall) {
        let pressed = config.policy == .compat || (activeGesture > consumedGesture && visible)
        let context = config.context.effective
        let verdict = requests.decideWallet(nowMs: Self.nowMs(), gesture: pressed, visible: visible, canQuery: context.canQuery, wallet: context.wallet)
        if let error = SdkPolicy.requestRefusal(verdict) {
            let why = Self.walletRefused[verdict] ?? Self.requestRefused[verdict] ?? verdict.rawValue
            return enqueue("bridge", refusal(id, reason: verdict.rawValue, error: error, detail: "wallet.\(call.method) refused (\(verdict.rawValue)): \(why)"))
        }
        if verdict == .foreground, config.policy == .strict {
            consumedGesture = activeGesture
            activeGesture = 0
        }
        let respond = completion(id, wallet: true)
        guard let delegate else { return respond.unsupported() }
        delegate.engine(self, wallet: call, respond: respond)
    }

    /// The client answers once, from any thread; the gate and the widget are touched on this thread only.
    private func completion(_ id: Int, wallet: Bool) -> WidgetRequestCompletion {
        let once = RequestOnce()
        return WidgetRequestCompletion { [weak self] outcome in
            guard once.claim(), let self else { return }
            self.queue.async { [weak self] in self?.settleRequest(id, outcome, wallet: wallet) }
        }
    }

    /// A request the gate let through, to the delegate; its outcome frees the gate (`settleRequest`).
    private func deliverRequest(id: Int, data: String, wallet: Bool = false) {
        let respond = completion(id, wallet: false)
        guard let delegate else { return respond.unsupported() }
        delegate.engine(self, request: WidgetRequest(data: data, wallet: wallet), respond: respond)
    }

    /// A refused request `id` (`request-blocked`, at most 20 per widget): the answer event with its error and reason.
    private func refusal(_ id: Int, reason: String, error: SdkPolicy.RequestError, detail: String) -> String {
        if blockedRequestsReported < 20 {
            blockedRequestsReported += 1
            delegate?.engine(self, diag: "request-blocked", detail: detail)
        }
        return Self.json(["type": "answer", "id": id, "error": error.rawValue, "reason": reason])
    }

    /// The client's outcome for request or wallet call `id`, on the engine's thread: the gate is free again (an answer
    /// brings the grant when the call had a press or the grant); no client side is a refusal (`unsupported`); the
    /// answer's `result` is checked (`answer-rejected` → `INVALID_ANSWER`) and settles the widget's promise (an answer
    /// carries no data); a failure becomes its code (the wallet's own codes only for a wallet call).
    private func settleRequest(_ id: Int, _ outcome: WidgetRequestCompletion.Outcome, wallet: Bool) {
        if dead { return }
        guard case .answer(let result) = outcome else {
            requests.finish()
            let detail = wallet ? "wallet call refused: the client handles no wallet calls (WidgetHost.onWallet)" : "request refused: the client handles no requests (WidgetHost.onRequest)"
            return enqueueNow("bridge", refusal(id, reason: "unsupported", error: .notAllowed, detail: detail))
        }
        var event: [String: Any] = ["type": "answer", "id": id]
        switch result {
        case .failure(let failure):
            requests.finish()
            event["error"] = (wallet ? failure.code : failure.requestCode).rawValue
        case .success(let answer):
            if let checked = SdkPolicy.checkRequestAnswer(answer) {
                requests.finish(answeredAtMs: Self.nowMs())
                event["result"] = Self.orNull(checked.result)
            } else {
                requests.finish()
                delegate?.engine(self, diag: "answer-rejected", detail: "the answer's result is not text of at most \(SdkPolicy.maxRequestResultBytes) bytes (UTF-8, no NUL); the widget gets INVALID_ANSWER")
                event["error"] = SdkPolicy.RequestError.invalidAnswer.rawValue
            }
        }
        enqueueNow("bridge", Self.json(event))
    }

    /// The areas of Web Storage a task changed, in full (`__host('storage', …)`, at most once per task, after its ops).
    private func storageCommit(_ arg: String) {
        // A preview's Web Storage lives in this run only: there is no message to keep it for.
        if config.context.preview { return }
        let length = arg.utf16.count
        let commit = length > Self.maxStorageCommitChars ? nil : SdkPolicy.checkStorageCommit(Self.parse(arg))
        guard let commit, !commit.isEmpty else {
            if rejectedStorageReported < 20 {
                rejectedStorageReported += 1
                let detail = length > Self.maxStorageCommitChars ? "commit of \(length) characters"
                    : "a storage commit that is not { local?, session? } objects of strings within \(SdkPolicy.storageQuotaBytes) bytes each; the client keeps the previous content"
                delegate?.engine(self, diag: "storage-rejected", detail: detail)
            }
            return
        }
        delegate?.engine(self, storageCommit: commit)
    }

    /// Blocks the engine queue until the host answers (the host must never wait for the engine).
    private func syncQuery(_ json: String) -> String {
        guard let delegate else { return "{\"error\":\"no host\"}" }
        let t0 = CACurrentMediaTime()
        if queryLimitHit != nil {
            return "{\"error\":\"query budget exceeded\"}"
        }
        let (answer, binary) = delegate.engine(self, query: json)
        let dt = CACurrentMediaTime() - t0
        js.deadline += dt // time spent waiting is not widget CPU time
        stats.queries += 1
        stats.queryMs += dt * 1000
        taskQueryMs += dt * 1000
        taskQueries += 1
        queryLimitHit = CpuBudget.queryLimitHit(queries: taskQueries, queryMs: taskQueryMs)
        if queryLimitHit != nil {
            // The host answered every query, but this task has asked too many or held the renderer too long: interrupt it at the next poll.
            js.cpuDeadline = -.infinity
        }
        lastBinary = binary
        return answer
    }

    // MARK: running code

    private func kill(_ reason: String) {
        if prewarming {
            prewarmFailed = true
            return
        }
        if dead { return }
        dead = true
        heldRequest = nil // never reaches the client
        pendingBeforeBoot.removeAll()
        for (_, t) in timers { t.cancel() }
        timers.removeAll()
        deferred.removeAll()
        wakeTimer?.cancel()
        wakeTimer = nil
        delegate?.engine(self, died: reason)
        // The runtime is gone for good: free its heap now instead of with the engine.
        queue.async { self.js = nil }
    }

    private func handleError(_ error: Error, where location: String) {
        let text = (error as? QuickJSError)?.description ?? String(describing: error)
        if js?.interrupted == true, let hit = queryLimitHit {
            return kill("\(CpuBudget.queryLimitReason(hit, kind: currentKind)) (\(location))")
        }
        if js?.interrupted == true {
            return kill("CPU limit exceeded: more than \(Int(currentSliceMs)) ms of continuous execution (\(location))")
        }
        if text.lowercased().contains("out of memory") { return kill("Memory limit exceeded (\(config.memoryLimitBytes >> 20) MB)") }
        delegate?.engine(self, log: "error", text: String(("Uncaught " + text).prefix(4000)))
    }

    private func drainJobs() {
        js.executePendingJobs { self.handleError($0, where: "promise job") }
    }

    @discardableResult
    private func callTw(_ kind: String, _ arg: String) -> String? {
        if dead { return nil }
        do {
            return try js.call("__twDispatch", [kind, arg]).string
        } catch {
            handleError(error, where: kind)
            return nil
        }
    }

    private func settle() {
        for _ in 0..<20 where !dead {
            drainJobs()
            if dead { return }
            let more = callTw("endTick", "")
            if more != "1" && !js.hasPendingJob { break }
        }
    }

    /// One of our own bundles: the precompiled `.qbc` when shipped and matching the source,
    /// else the source. Returns "bytecode" or "source" for the diag.
    private func evalBundle(_ source: String, bytecode: Data?, filename: String) -> String {
        // Bundles of another release are refused before anything runs, as a stale `.qbc` is (BundleStamp.swift).
        // A prewarm that fails is retried by start(), which reports it.
        if let refusal = resources.refusal {
            if !prewarming { delegate?.engine(self, diag: "bundle-mismatch", detail: refusal) }
            return "failed"
        }
        if let bytecode {
            do {
                try js.evaluateBytecode(bytecode, source: source)
                return "bytecode"
            } catch {
                delegate?.engine(self, diag: "bytecode", detail: "\(filename): falling back to source — \("\(error)".split(separator: "\n").first ?? "")")
            }
        }
        do {
            try js.evaluate(source, filename: filename)
        } catch {
            handleError(error, where: filename)
            return "failed"
        }
        return "source"
    }

    private func evalScript(_ code: String, filename: String, asModule: Bool = false) {
        do { try js.evaluate(code, filename: filename, asModule: asModule) } catch { handleError(error, where: filename) }
    }

    private func pumpScripts() {
        for _ in 0..<1000 where !dead {
            guard let next = callTw("nextScript", ""), !next.isEmpty, let s = Self.parse(next) as? [String: Any] else { break }
            let id = (s["id"] as? NSNumber)?.intValue ?? 0
            var ok = true
            resetDeadline()
            if let src = s["src"] as? String {
                // Nothing is fetched and nothing is bundled (profile 1: no libraries): an external script never runs.
                ok = false
                delegate?.engine(self, diag: "script-skipped", detail: "external script not available: \(src.prefix(200))")
            } else if let code = s["code"] as? String {
                evalScript(code, filename: "inline-script-\(id).js", asModule: (s["type"] as? String) == "module")
            }
            if dead { return }
            settle()
            callTw("scriptDone", "[\(id),\(ok)]")
            settle()
        }
    }

    /// The slice is measured in the JS thread's CPU time, so a busy machine does not
    /// kill widgets; wall-clock time is only a backstop for a thread that never runs.
    private func resetDeadline() {
        js.interrupted = false
        js.cpuDeadline = QuickJSContext.threadCPUTime() + currentSliceMs / 1000
        js.deadline = ProcessInfo.processInfo.systemUptime + max(currentSliceMs * Self.wallBackstopFactor, Self.wallBackstopMinMs) / 1000
    }

    static let wallBackstopFactor = 10.0
    static let wallBackstopMinMs = 5000.0

    /// One task of `kind` with `sliceMs` of continuous execution; its JS time is recorded against the quota.
    private func task(_ kind: String, sliceMs: Double, _ body: () -> Void) {
        if dead { return }
        delegate?.engineTaskStarted(self)
        defer { delegate?.engineTaskCompleted(self) }
        currentKind = kind
        currentSliceMs = sliceMs
        excludeMs = 0
        taskQueryMs = 0
        taskQueries = 0
        taskOpsCostMs = 0
        queryLimitHit = nil
        let c0 = QuickJSContext.threadCPUTime()
        resetDeadline()
        body()
        if !dead { settle() }
        if !dead { pumpScripts() }
        // Past the limit but done before the interrupt landed (a loop that ended on the error answers): stopped all the same.
        if !dead, let hit = queryLimitHit { kill(CpuBudget.queryLimitReason(hit, kind: kind)) }
        stats.tasks += 1
        let cpuMs = (QuickJSContext.threadCPUTime() - c0) * 1000 - excludeMs
        stats.jsMs += cpuMs
        let end = Self.nowMs()
        budget.record(kind, cpuMs: cpuMs, now: end)
        mainBudget.record(kind, cpuMs: taskQueryMs + taskOpsCostMs, now: end) // what the task cost the host's main thread
        stats.cpuWindowMs = budget.used(end)
        let now = CACurrentMediaTime()
        if now - lastMemCheck > 1, !dead {
            lastMemCheck = now
            stats.memoryBytes = js.memoryUsed
        }
        postStats(force: false)
    }

    /// The validator's CPU time for this widget's messages (TrustedValidator's CPU sink, ms of its thread's clock):
    /// recorded against the JS quota on the engine's thread as it arrives (`stats.validatorMs`; with the debt rule a late
    /// record still counts in full; docs/decisions.md, "The validator's time counts"). Any thread.
    public func chargeValidator(ms: Double) {
        guard ms > 0 else { return }
        queue.async { [weak self] in
            guard let self, !self.dead else { return }
            let now = Self.nowMs()
            self.budget.record("validator", cpuMs: ms, now: now)
            self.stats.validatorMs += ms
            self.stats.cpuWindowMs = self.budget.used(now)
            self.postStats(force: false)
        }
    }

    private var trailingStats: JSThread.Scheduled?

    /// At most one stats message per 200 ms, but the last state always gets out
    /// (a burst of tasks followed by silence used to leave the panel stale).
    private func postStats(force: Bool) {
        let now = CACurrentMediaTime()
        if force || now - lastStatsPost > 0.2 {
            lastStatsPost = now
            trailingStats?.cancel()
            trailingStats = nil
            delegate?.engine(self, stats: stats)
        } else if trailingStats == nil {
            trailingStats = queue.after(0.2) { [weak self] in
                guard let self else { return }
                self.trailingStats = nil
                self.postStats(force: true)
            }
        }
    }

    // MARK: helpers

    static func json(_ value: Any) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: value, options: [.fragmentsAllowed]) else { return "null" }
        return String(decoding: data, as: UTF8.self)
    }
    static func parse(_ json: String) -> Any? {
        try? JSONSerialization.jsonObject(with: Data(json.utf8), options: [.fragmentsAllowed])
    }
    /// A value for `json`: JSON null for none.
    static func orNull(_ value: Any?) -> Any { value ?? NSNull() }
    private static func splitPrefix(_ s: String) -> (String, String) {
        guard let i = s.firstIndex(of: ":") else { return (s, "") }
        return (String(s[..<i]), String(s[s.index(after: i)...]))
    }
    /// Canvas commands in an ops batch's JSON (the main-thread cost): the elements of each `"cmds":[...]` array; strings
    /// are skipped (inside one a quote is always `\"`), as is anything nested in a command.
    static func countCanvasCommands(inJSON json: String) -> Int {
        var json = json
        return json.withUTF8 { b -> Int in
            let needle = Array("\"cmds\":[".utf8)
            let n = b.count
            var count = 0
            var i = 0
            var inString = false
            var depth = 0 // inside a cmds array: 1 = its elements, more = inside one
            var expecting = false
            while i < n {
                let c = b[i]
                if inString {
                    if c == 0x5c { i += 2; continue }
                    if c == 0x22 { inString = false }
                    i += 1
                    continue
                }
                if depth == 0 {
                    if c == 0x22 {
                        if n - i >= needle.count, needle.indices.allSatisfy({ b[i + $0] == needle[$0] }) {
                            i += needle.count
                            depth = 1
                            expecting = true
                            continue
                        }
                        inString = true
                    }
                    i += 1
                    continue
                }
                if depth == 1, expecting, c != 0x2c, c != 0x5d, c != 0x20, c != 0x0a, c != 0x0d, c != 0x09 {
                    count += 1
                    expecting = false
                }
                switch c {
                case 0x5b, 0x7b: depth += 1
                case 0x5d, 0x7d: depth -= 1
                case 0x2c: if depth == 1 { expecting = true }
                case 0x22: inString = true
                default: break
                }
                i += 1
            }
            return count
        }
    }

    /// Commands in the canvas word payloads staged for a batch.
    private static func canvasCommands(in canvas: [Int: WidgetCanvasBatch]) -> Int {
        canvas.values.reduce(0) { $0 + (CanvasWords.commandCount($1) ?? 0) }
    }

    private static func countOccurrences(of needle: String, in text: String) -> Int {
        var count = 0
        var range = text.startIndex..<text.endIndex
        while let r = text.range(of: needle, range: range) {
            count += 1
            range = r.upperBound..<text.endIndex
        }
        return count
    }
}

/// How a delegate completes a request (`engine(_:request:respond:)`): `respond(.success(answer))` or
/// `respond(.failure(.timeout))`, once, from any thread; later calls are ignored.
public struct WidgetRequestCompletion {
    /// What reaches the engine: the client's answer or failure, or no client side for requests (WidgetHost without
    /// `onRequest` when the request is delivered), which the widget gets as `NOT_ALLOWED`, reason `unsupported`.
    enum Outcome {
        case answer(Result<WidgetRequestAnswer, WidgetRequestFailure>)
        case unsupported
    }

    let settle: (Outcome) -> Void

    public func callAsFunction(_ result: Result<WidgetRequestAnswer, WidgetRequestFailure>) {
        settle(.answer(result))
    }

    /// The client handles no requests.
    func unsupported() {
        settle(.unsupported)
    }
}

/// The first call of a request's completion wins; the client may call it from any thread, any number of times.
private final class RequestOnce {
    private let lock = NSLock()
    private var done = false

    func claim() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        if done { return false }
        done = true
        return true
    }
}

// MARK: - Resources

/// The JavaScript shipped with the package (built by `pnpm build:ios`). The
/// directory is deliberately not called "Resources": codesign rejects iOS
/// bundles with a top-level Resources/ folder ("bundle format unrecognized").
public final class SandboxResources {
    public static let shared = SandboxResources()

    public let sandboxBundle: String
    public let rendererBundle: String
    public let validatorBundle: String
    /// Precompiled bytecode of the bundles (`.qbc` next to the `.js`, built together; nil when not shipped).
    public let sandboxBytecode: Data?
    public let validatorBytecode: Data?
    /// Why this glue refuses these bundles (the first bundle whose stamp is not this glue's protocol, profile and
    /// sdk, BundleStamp.swift), nil when it runs them: the engine dies at boot with `bundle-mismatch`, the validator
    /// does not build.
    public let refusal: String?

    /// `bundle` defaults to the package's resource bundle.
    public convenience init(bundle: Bundle? = nil) {
        let bundle = bundle ?? Bundle.module
        func read(_ name: String, _ ext: String, _ sub: String) -> String {
            guard let url = bundle.url(forResource: name, withExtension: ext, subdirectory: sub), let s = try? String(contentsOf: url, encoding: .utf8) else {
                fatalError("WidgetSandbox: missing resource \(sub)/\(name).\(ext) — run `pnpm build:ios`")
            }
            return s
        }
        self.init(sandboxBundle: read("sandbox-bundle", "js", "WidgetJS"), rendererBundle: read("renderer-bundle", "js", "WidgetJS"),
                  validatorBundle: read("validator-bundle", "js", "WidgetJS"),
                  sandboxBytecode: bundle.url(forResource: "sandbox-bundle", withExtension: "qbc", subdirectory: "WidgetJS").flatMap { try? Data(contentsOf: $0) },
                  validatorBytecode: bundle.url(forResource: "validator-bundle", withExtension: "qbc", subdirectory: "WidgetJS").flatMap { try? Data(contentsOf: $0) })
    }

    /// Bundles from elsewhere (a client's own resource layout, tests).
    public init(sandboxBundle: String, rendererBundle: String, validatorBundle: String, sandboxBytecode: Data?, validatorBytecode: Data?) {
        self.sandboxBundle = sandboxBundle
        self.rendererBundle = rendererBundle
        self.validatorBundle = validatorBundle
        self.sandboxBytecode = sandboxBytecode
        self.validatorBytecode = validatorBytecode
        refusal = BundleStamp.refusal(sandboxBundle, name: "sandbox-bundle.js")
            ?? BundleStamp.refusal(validatorBundle, name: "validator-bundle.js")
            ?? BundleStamp.refusal(rendererBundle, name: "renderer-bundle.js")
    }
}

// MARK: - Intl delegation (pure formatting, no I/O)

enum IntlBridge {
    /// A BCP 47 tag as a Locale. Locale(identifier:) misreads a Unicode extension ("ar-EG-u-nu-arab" becomes
    /// "ar_EG@numbers=yes") and Foundation traps on the formatters of such a locale; Locale.Components parses it.
    /// Before macOS 13 the extension is dropped instead, so it never reaches Locale(identifier:).
    static func bcp47Locale(_ tag: String) -> Locale {
        if #available(macOS 13, iOS 16, *) {
            return Locale(components: Locale.Components(identifier: tag))
        }
        return Locale(identifier: withoutUnicodeExtension(tag))
    }

    static func withoutUnicodeExtension(_ tag: String) -> String {
        var kept: [Substring] = []
        var inExtension = false
        for part in tag.split(separator: "-", omittingEmptySubsequences: false) {
            if part.count == 1 {
                inExtension = part.lowercased() == "u"
            }
            if !inExtension {
                kept.append(part)
            }
        }
        return kept.joined(separator: "-")
    }

    static func format(requestJson: String) -> String {
        guard let req = SandboxEngine.parse(requestJson) as? [String: Any], let kind = req["kind"] as? String else { return "" }
        let locale = (req["locale"] as? String).map(bcp47Locale) ?? ((req["locale"] as? [String])?.first).map(bcp47Locale) ?? .current
        let opts = req["opts"] as? [String: Any] ?? [:]
        let value = (req["value"] as? NSNumber)?.doubleValue ?? 0
        switch kind {
        case "number":
            let f = numberFormatter(locale: locale, options: opts, value: value)
            return f.string(from: NSNumber(value: value)) ?? String(value)
        case "numberParts":
            let f = numberFormatter(locale: locale, options: opts, value: value)
            return json(numberParts(f.string(from: NSNumber(value: value)) ?? String(value), f))
        case "date", "dateMethod":
            let date = Date(timeIntervalSince1970: value / 1000)
            let method = req["method"] as? String
            let f = dateFormatter(locale: locale, options: opts, method: method)
            return f.string(from: date)
        case "dateParts":
            let method = req["method"] as? String
            let f = dateFormatter(locale: locale, options: opts, method: method)
            return json(dateParts(Date(timeIntervalSince1970: value / 1000), f))
        case "numberOptions":
            let f = numberFormatter(locale: locale, options: opts)
            var out: [String: Any] = ["locale": languageTag(locale), "numberingSystem": "latn", "style": opts["style"] as? String ?? "decimal",
                                      "minimumIntegerDigits": f.minimumIntegerDigits, "minimumFractionDigits": f.minimumFractionDigits,
                                      "maximumFractionDigits": f.maximumFractionDigits, "useGrouping": f.usesGroupingSeparator ? "auto" : false,
                                      "signDisplay": opts["signDisplay"] as? String ?? "auto"]
            if let currency = opts["currency"] as? String { out["currency"] = currency.uppercased() }
            return jsonObject(out)
        case "dateOptions":
            let zone = (opts["timeZone"] as? String).flatMap(TimeZone.init(identifier:)) ?? .current
            var out = opts.filter { $0.value is String || $0.value is NSNumber }
            out.merge(["locale": languageTag(locale), "calendar": "gregory", "numberingSystem": "latn", "timeZone": zone.identifier]) { $1 }
            return jsonObject(out)
        case "plural":
            return CLDRPluralRules.select(value, locale: locale, options: opts)
        case "relative":
            guard #available(macOS 10.15, iOS 13.0, *) else {
                return ""
            }
            return relative(value, unit: req["unit"] as? String ?? "second", locale: locale, options: opts)
        case "list":
            let items = (req["value"] as? [Any])?.map { "\($0)" } ?? []
            guard #available(macOS 10.15, iOS 13.0, *) else {
                return items.joined(separator: ", ")
            }
            let type = opts["type"] as? String, style = opts["style"] as? String
            if #available(macOS 12, iOS 15, *) {
                // Foundation has no unit lists: a narrow conjunction is the closest (the same in English).
                let narrow = style == "narrow" || type == "unit"
                return items.formatted(.list(type: type == "disjunction" ? .or : .and, width: narrow ? .narrow : style == "short" ? .short : .standard).locale(locale))
            }
            let f = ListFormatter()
            f.locale = locale
            return f.string(from: items) ?? items.joined(separator: ", ")
        case "compare":
            let a = req["a"] as? String ?? "", b = req["b"] as? String ?? ""
            var options: String.CompareOptions = []
            switch opts["sensitivity"] as? String {
            case "base": options.formUnion([.caseInsensitive, .diacriticInsensitive])
            case "accent": options.insert(.caseInsensitive)
            case "case": options.insert(.diacriticInsensitive)
            default: break
            }
            if opts["numeric"] as? Bool == true { options.insert(.numeric) }
            return String(a.compare(b, options: options, range: nil, locale: locale).rawValue)
        case "upper":
            return (req["value"] as? String ?? "").uppercased(with: locale)
        case "lower":
            return (req["value"] as? String ?? "").lowercased(with: locale)
        default:
            return ""
        }
    }

    @available(macOS 10.15, iOS 13.0, *)
    private static func relative(_ value: Double, unit rawUnit: String, locale: Locale, options: [String: Any]) -> String {
        if rawUnit == "quarter" || rawUnit == "quarters" {
            // RelativeDateTimeFormatter has no quarter: English is written out, other languages count months.
            if CLDRPluralRules.languageKey(locale) == "en" { return englishQuarters(value, options: options) }
            return relative(value * 3, unit: "month", locale: locale, options: options)
        }
        let f = RelativeDateTimeFormatter()
        f.locale = locale
        f.dateTimeStyle = options["numeric"] as? String == "auto" ? .named : .numeric
        switch options["style"] as? String {
        case "short": f.unitsStyle = .short
        case "narrow": f.unitsStyle = .abbreviated
        default: f.unitsStyle = .full
        }
        let unit = rawUnit.hasSuffix("s") ? String(rawUnit.dropLast()) : rawUnit
        if value == value.rounded(), abs(value) < 1e9 {
            let n = Int(value)
            var c = DateComponents()
            switch unit {
            case "second": c.second = n
            case "minute": c.minute = n
            case "hour": c.hour = n
            case "day": c.day = n
            case "week": c.weekOfMonth = n
            case "month": c.month = n
            case "year": c.year = n
            default: c.second = nil
            }
            if c != DateComponents() {
                let s = f.localizedString(from: c)
                if !s.isEmpty { return s }
            }
        }
        let seconds: Double = ["second": 1, "minute": 60, "hour": 3600, "day": 86400, "week": 604800, "month": 2_592_000, "quarter": 7_776_000, "year": 31_536_000][unit] ?? 1
        return f.localizedString(fromTimeInterval: value * seconds)
    }

    /// `RelativeTimeFormat` in English for the quarter unit, as CLDR words it.
    private static func englishQuarters(_ value: Double, options: [String: Any]) -> String {
        let style = options["style"] as? String ?? "long"
        if options["numeric"] as? String == "auto", value == value.rounded(), abs(value) <= 1 {
            let unit = style == "long" ? "quarter" : "qtr."
            return (value < 0 ? "last " : value > 0 ? "next " : "this ") + unit
        }
        let n = NumberFormatter()
        n.locale = Locale(identifier: "en_US")
        n.numberStyle = .decimal
        n.maximumFractionDigits = 3
        let count = n.string(from: NSNumber(value: abs(value))) ?? String(abs(value))
        let one = count == "1"
        let amount = style == "narrow" ? count + "q" : count + " " + (style == "short" ? (one ? "qtr." : "qtrs.") : (one ? "quarter" : "quarters"))
        return value < 0 || (value == 0 && value.sign == .minus) ? amount + " ago" : "in " + amount
    }

    /// ECMA-402's signDisplay on a formatter: "always" signs everything, "exceptZero" what does not round to zero,
    /// "negative" puts a minus only on negatives that do not round to zero, "never" shows none ("auto" is the default).
    private static func applySignDisplay(_ sign: String, value: Double, to f: NumberFormatter) {
        let shown = f.string(from: NSNumber(value: abs(value))) ?? ""
        let zero = !shown.unicodeScalars.contains { $0.properties.numericType == .decimal && $0.properties.numericValue != 0 }
        let negative = value < 0 || (value == 0 && value.sign == .minus)
        let plus = (sign == "always" && !negative) || (sign == "exceptZero" && !negative && !zero)
        let noMinus = sign == "never" || ((sign == "exceptZero" || sign == "negative") && zero)
        if plus { f.positivePrefix = (f.plusSign ?? "+") + (f.positivePrefix ?? "") }
        if noMinus {
            f.negativePrefix = f.positivePrefix
            f.negativeSuffix = f.positiveSuffix
        }
    }

    private static func numberFormatter(locale: Locale, options opts: [String: Any], value: Double? = nil) -> NumberFormatter {
        let f = NumberFormatter()
        f.locale = locale
        // ECMA-402 rounds half away from zero (halfExpand); NumberFormatter's default is half-even.
        f.roundingMode = .halfUp
        switch opts["style"] as? String {
        case "currency": f.numberStyle = .currency; if let c = opts["currency"] as? String { f.currencyCode = c }
        case "percent": f.numberStyle = .percent
        default: f.numberStyle = .decimal
        }
        if let n = (opts["minimumFractionDigits"] as? NSNumber)?.intValue { f.minimumFractionDigits = n }
        if let n = (opts["maximumFractionDigits"] as? NSNumber)?.intValue { f.maximumFractionDigits = n }
        if let g = opts["useGrouping"] as? Bool { f.usesGroupingSeparator = g }
        if let n = (opts["minimumIntegerDigits"] as? NSNumber)?.intValue { f.minimumIntegerDigits = n }
        if opts["minimumSignificantDigits"] != nil || opts["maximumSignificantDigits"] != nil {
            f.usesSignificantDigits = true
            if let n = (opts["minimumSignificantDigits"] as? NSNumber)?.intValue { f.minimumSignificantDigits = n }
            f.maximumSignificantDigits = (opts["maximumSignificantDigits"] as? NSNumber)?.intValue ?? 21
        }
        if f.numberStyle == .currency {
            switch opts["currencyDisplay"] as? String {
            case "code": f.numberStyle = .currencyISOCode
            case "name": f.numberStyle = .currencyPlural
            default: break
            }
        }
        if let sign = opts["signDisplay"] as? String, sign != "auto", let value { applySignDisplay(sign, value: value, to: f) }
        return f
    }

    private static func languageTag(_ locale: Locale) -> String {
        if #available(macOS 13, iOS 16, *) {
            return locale.identifier(.bcp47)
        }
        return locale.identifier.replacingOccurrences(of: "_", with: "-")
    }

    private static func jsonObject(_ object: [String: Any]) -> String {
        (try? JSONSerialization.data(withJSONObject: object, options: [.sortedKeys])).map { String(decoding: $0, as: UTF8.self) } ?? "{}"
    }

    private static func json(_ parts: [(String, String)]) -> String {
        let list = parts.map { ["type": $0.0, "value": $0.1] }
        return (try? JSONSerialization.data(withJSONObject: list)).map { String(decoding: $0, as: UTF8.self) } ?? "[]"
    }

    /// JS `NumberFormat.formatToParts`: the formatted string split by the formatter's own symbols.
    static func numberParts(_ s: String, _ f: NumberFormatter) -> [(String, String)] {
        var out: [(String, String)] = []
        func push(_ type: String, _ text: String) {
            if let last = out.last, last.0 == type, type == "literal" || type == "currency" { out[out.count - 1].1 += text } else { out.append((type, text)) }
        }
        let symbols: [(String, String)] = [(f.currencySymbol ?? "", "currency"), (f.internationalCurrencySymbol ?? "", "currency"), (f.percentSymbol ?? "", "percentSign"),
                                           (f.minusSign ?? "", "minusSign"), (f.plusSign ?? "", "plusSign"), (f.exponentSymbol ?? "", "exponentSeparator"),
                                           (f.notANumberSymbol ?? "", "nan"), ("∞", "infinity")].filter { !$0.0.isEmpty }.sorted { $0.0.count > $1.0.count }
        var i = s.startIndex
        var fraction = false
        var digits = ""
        func flush() { if !digits.isEmpty { push(fraction ? "fraction" : "integer", digits); digits = "" } }
        while i < s.endIndex {
            let rest = s[i...]
            if s[i].isWholeNumber { digits.append(s[i]); i = s.index(after: i); continue }
            if !fraction, !digits.isEmpty, let decimal = f.decimalSeparator, !decimal.isEmpty, rest.hasPrefix(decimal), rest.dropFirst(decimal.count).first?.isWholeNumber == true {
                flush(); push("decimal", decimal); fraction = true; i = s.index(i, offsetBy: decimal.count); continue
            }
            if !fraction, !digits.isEmpty, let group = f.groupingSeparator, !group.isEmpty, rest.hasPrefix(group), rest.dropFirst(group.count).first?.isWholeNumber == true {
                flush(); push("group", group); i = s.index(i, offsetBy: group.count); continue
            }
            flush()
            if let symbol = symbols.first(where: { rest.hasPrefix($0.0) }) {
                push(symbol.1, symbol.0); i = s.index(i, offsetBy: symbol.0.count); continue
            }
            push("literal", String(s[i])); i = s.index(after: i)
        }
        flush()
        return out
    }

    /// JS `DateTimeFormat.formatToParts`: the formatter's pattern split into fields, each formatted on its own.
    static func dateParts(_ date: Date, _ f: DateFormatter) -> [(String, String)] {
        let types: [Character: String] = ["G": "era", "y": "year", "u": "year", "Y": "year", "M": "month", "L": "month", "d": "day", "E": "weekday", "c": "weekday", "e": "weekday",
                                          "a": "dayPeriod", "b": "dayPeriod", "B": "dayPeriod", "h": "hour", "H": "hour", "k": "hour", "K": "hour", "m": "minute", "s": "second",
                                          "S": "fractionalSecond", "z": "timeZoneName", "Z": "timeZoneName", "v": "timeZoneName", "V": "timeZoneName", "O": "timeZoneName", "X": "timeZoneName", "x": "timeZoneName"]
        var out: [(String, String)] = []
        func literal(_ text: String) {
            if text.isEmpty { return }
            if let last = out.last, last.0 == "literal" { out[out.count - 1].1 += text } else { out.append(("literal", text)) }
        }
        let pattern = Array(f.dateFormat ?? "")
        var i = 0
        while i < pattern.count {
            let c = pattern[i]
            if c == "'" {
                var j = i + 1
                var text = ""
                while j < pattern.count {
                    if pattern[j] == "'" { if j + 1 < pattern.count && pattern[j + 1] == "'" { text.append("'"); j += 2; continue }; break }
                    text.append(pattern[j]); j += 1
                }
                literal(i + 1 == j ? "'" : text)
                i = j + 1
            } else if c.isASCII && c.isLetter {
                var j = i
                while j < pattern.count && pattern[j] == c { j += 1 }
                let single = DateFormatter()
                single.locale = f.locale
                single.timeZone = f.timeZone
                single.calendar = f.calendar
                single.dateFormat = String(pattern[i..<j])
                let text = single.string(from: date)
                if let type = types[c] { out.append((type, text)) } else { literal(text) }
                i = j
            } else {
                literal(String(c)); i += 1
            }
        }
        return out
    }

    /// `pattern` with every unquoted run of the field letter `field` replaced by `with`.
    static func replacingField(_ field: Character, in pattern: String, with replacement: String) -> String {
        var out = "", quoted = false, inField = false
        for c in pattern {
            if c == "'" { quoted.toggle() }
            if !quoted && c == field {
                if !inField { out += replacement }
                inField = true
            } else {
                inField = false
                out.append(c)
            }
        }
        return out
    }

    /// Apple's patterns join a medium or short date to the time with the "at" connector; ECMA-402 (CLDR's standard
    /// date-time format) uses it only after a long date (full month or weekday name) and joins the others as the
    /// numeric skeleton does ("Jan 1, 1970, 1:45 PM").
    static func plainConnector(_ pattern: String, locale: Locale) -> String {
        func middle(_ whole: String?, _ head: String?, _ tail: String?) -> String? {
            guard let whole, let head, let tail, whole.count > head.count + tail.count, whole.hasPrefix(head), whole.hasSuffix(tail) else { return nil }
            return String(whole.dropFirst(head.count).dropLast(tail.count))
        }
        let template = { (t: String) in DateFormatter.dateFormat(fromTemplate: t, options: 0, locale: locale) }
        let styled = { (d: DateFormatter.Style, t: DateFormatter.Style) -> String? in
            let f = DateFormatter()
            f.locale = locale
            f.dateStyle = d
            f.timeStyle = t
            return f.dateFormat
        }
        guard let at = middle(styled(.medium, .short), styled(.medium, .none), styled(.none, .short)),
              let plain = middle(template("yMdjmm"), template("yMd"), template("jmm")),
              at != plain, pattern.contains(at),
              !["MMMM", "LLLL", "EEEE", "cccc"].contains(where: pattern.contains) else { return pattern }
        return pattern.replacingOccurrences(of: at, with: plain)
    }

    private static func dateFormatter(locale: Locale, options: [String: Any], method: String?) -> DateFormatter {
        let zone = (options["timeZone"] as? String).flatMap(TimeZone.init(identifier:)) ?? .current
        let optionsData = (try? JSONSerialization.data(withJSONObject: options, options: [.sortedKeys])) ?? Data()
        let key = "\(locale.identifier)|\(zone.identifier)|\(method ?? "")|\(String(decoding: optionsData, as: UTF8.self))" as NSString
        let cache: NSCache<NSString, DateFormatter>
        if let existing = Thread.current.threadDictionary["WidgetSandbox.IntlBridge.dateFormatters"] as? NSCache<NSString, DateFormatter> {
            cache = existing
        } else {
            cache = NSCache<NSString, DateFormatter>()
            cache.countLimit = 64
            Thread.current.threadDictionary["WidgetSandbox.IntlBridge.dateFormatters"] = cache
        }
        if let formatter = cache.object(forKey: key) { return formatter }
        let formatter = DateFormatter()
        formatter.locale = locale
        formatter.timeZone = zone
        let wantsDate = ["era", "weekday", "year", "month", "day", "dateStyle"].contains { options[$0] != nil }
        let wantsTime = ["hour", "minute", "second", "fractionalSecondDigits", "timeZoneName", "timeStyle"].contains { options[$0] != nil }
        var hourSymbol = "j"
        if let hour12 = options["hour12"] as? Bool {
            hourSymbol = hour12 ? "h" : "H"
        } else if let cycle = options["hourCycle"] as? String {
            hourSymbol = ["h11": "K", "h12": "h", "h23": "H", "h24": "k"][cycle] ?? "j"
        }
        if let cycle = ["K": "h11", "h": "h12", "H": "h23", "k": "h24"][hourSymbol] {
            var components = Locale.components(fromIdentifier: locale.identifier)
            components["hours"] = cycle
            formatter.locale = Locale(identifier: Locale.identifier(fromComponents: components))
        }
        if wantsDate || wantsTime {
            var template = ""
            if let weekday = options["weekday"] as? String { template += weekday == "long" ? "EEEE" : weekday == "narrow" ? "EEEEE" : "EEE" }
            if let month = options["month"] as? String { template += month == "long" ? "MMMM" : month == "short" ? "MMM" : month == "narrow" ? "MMMMM" : month == "numeric" ? "M" : "MM" }
            if let day = options["day"] as? String { template += day == "2-digit" ? "dd" : "d" }
            if let year = options["year"] as? String { template += year == "2-digit" ? "yy" : "y" }
            if let hour = options["hour"] as? String { template += hour == "2-digit" ? hourSymbol + hourSymbol : hourSymbol }
            if options["minute"] != nil { template += "mm" }
            if options["second"] != nil { template += "ss" }
            if let digits = (options["fractionalSecondDigits"] as? NSNumber)?.intValue, (1...3).contains(digits) { template += String(repeating: "S", count: digits) }
            if let era = options["era"] as? String { template += era == "long" ? "GGGG" : era == "narrow" ? "GGGGG" : "G" }
            if let zone = options["timeZoneName"] as? String {
                template += ["long": "zzzz", "shortOffset": "O", "longOffset": "OOOO", "shortGeneric": "v", "longGeneric": "vvvv"][zone] ?? "z"
            }
            if template.isEmpty {
                formatter.dateStyle = Self.style(options["dateStyle"] as? String)
                formatter.timeStyle = Self.style(options["timeStyle"] as? String)
            } else {
                formatter.setLocalizedDateFormatFromTemplate(template)
                // The pattern generator may pick another era width than asked for (en: "GGGGG", a lone "A").
                if let era = options["era"] as? String {
                    formatter.dateFormat = replacingField("G", in: formatter.dateFormat ?? "", with: era == "long" ? "GGGG" : era == "narrow" ? "GGGGG" : "G")
                }
                // ECMA-402 calls the UTC zone "UTC" (Foundation: "GMT").
                if options["timeZoneName"] as? String == "short", ["UTC", "GMT", "Etc/UTC", "Etc/GMT"].contains(zone.identifier) {
                    formatter.dateFormat = replacingField("z", in: formatter.dateFormat ?? "", with: "'UTC'")
                }
            }
            formatter.dateFormat = plainConnector(formatter.dateFormat ?? "", locale: locale)
        }
        if hourSymbol != "j" || options["hour"] as? String == "2-digit" {
            let defaultTimePattern = DateFormatter.dateFormat(fromTemplate: "jmm", options: 0, locale: locale) ?? ""
            let inferredPadding = (options["hour"] as? String != "numeric" || defaultTimePattern.contains("h")) && ["hh", "HH", "KK", "kk"].contains(where: (formatter.dateFormat ?? "").contains)
            let stylePadding = options["timeStyle"] != nil && (defaultTimePattern.contains("HH") || defaultTimePattern.contains("kk"))
            let paddedHour = options["hour"] as? String == "2-digit" || inferredPadding || stylePadding
            var quoted = false
            var inHour = false
            var pattern = ""
            for character in formatter.dateFormat ?? "" {
                if character == "'" { quoted.toggle() }
                if !quoted && "hHKk".contains(character) {
                    if !inHour {
                        let symbol = hourSymbol == "j" ? String(character) : hourSymbol
                        pattern += paddedHour ? symbol + symbol : symbol
                    }
                    inHour = true
                } else {
                    inHour = false
                    pattern.append(!quoted && "bB".contains(character) ? "a" : character)
                }
            }
            formatter.dateFormat = pattern
        }
        cache.setObject(formatter, forKey: key)
        return formatter
    }

    private static func style(_ s: String?) -> DateFormatter.Style {
        switch s { case "full": return .full; case "long": return .long; case "medium": return .medium; case "short": return .short; default: return .none }
    }
}

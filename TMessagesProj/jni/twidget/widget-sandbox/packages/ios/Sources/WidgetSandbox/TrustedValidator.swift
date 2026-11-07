// The validator / sanitizer (src/host/validator.ts) compiled into
// validator-bundle.js and run in a TRUSTED QuickJS context — a separate
// runtime that never executes widget code. One instance per widget. Calls are
// synchronous from the caller's point of view (WidgetHost on the main thread)
// but execute on the validator's JSThread, whose 8 MB stack is what the
// css-tree parser needs in a debug build. Input and output are JSON strings.
//
// The thread can be the validator's own (the default) or one shared by every
// validator in the process (`TrustedValidator.sharedThread`): validator calls
// are short and never block, so one thread serves a whole chat of widgets,
// and a host that validates asynchronously (`perform`) keeps the css-tree
// work off the main thread without a thread per bubble.

import Foundation
import WidgetSandboxProtocol

public final class TrustedValidator {
    /// One thread for every validator that asks for it (see the header).
    public static let sharedThread = JSThread(name: "widget.validator")
    /// Validators of many widgets spread over up to four shared threads (half the cores), so one busy widget does not delay the others.
    public static let sharedThreadCount = min(4, max(1, ProcessInfo.processInfo.activeProcessorCount / 2))
    private static let extraSharedThreads = (1..<sharedThreadCount).map { JSThread(name: "widget.validator.\($0)") }
    private static var nextSharedThread = 0
    private static let nextSharedThreadLock = NSLock()

    public static func sharedThreadForNextValidator() -> JSThread {
        nextSharedThreadLock.lock()
        let index = nextSharedThread % sharedThreadCount
        nextSharedThread += 1
        nextSharedThreadLock.unlock()
        return index == 0 ? sharedThread : extraSharedThreads[index - 1]
    }

    public static func isShared(_ thread: JSThread) -> Bool {
        return thread === sharedThread || extraSharedThreads.contains { $0 === thread }
    }

    public let thread: JSThread
    private let ownsThread: Bool
    private var js: QuickJSContext!
    public var onDiag: (String, String) -> Void
    private var icons: IconExpansion?

    /// `icon:` tokens become stand-ins before validation and the host's URLs after it (`IconExpansion`): the strict caps
    /// count what the widget sent, never the host's icon bytes.
    public func setAssetResolver(_ resolver: WidgetAssetResolver?) {
        thread.async { [self] in
            icons = resolver.map { resolver in
                IconExpansion(resolver: resolver, imageTypes: { [weak self] in self?.loadTables()?.mime ?? [] }) { [weak self] k, d in self?.onDiag(k, d) }
            }
        }
    }

    /// On the validator's thread, before validation.
    private func expandIcons(_ json: String) -> String {
        guard let icons else { return json }
        icons.beginBatch()
        return icons.expand(json)
    }

    /// On the validator's thread: validated ops with the host's URLs in place of the stand-ins.
    private func restoreIcons(_ json: String) -> String {
        guard let icons else { return json }
        return icons.restore(json)
    }

    private func restoreIcons(_ batch: RenderBatch) -> RenderBatch {
        guard let icons else { return batch }
        switch batch {
        case .empty: return batch
        case .json(let json): return .json(icons.restore(json))
        case .ops(let ops): return .ops(icons.restore(ops))
        }
    }

    /// Set once the bundle is evaluated: from the precompiled blob or from source.
    public private(set) var bundleSource = "pending"

    /// Builds the context and evaluates the bundle before returning (the caller's thread waits for the validator's).
    public convenience init(theme: String, resources: SandboxResources = .shared, thread: JSThread? = nil, onDiag: @escaping (String, String) -> Void) throws {
        self.init(thread: thread, onDiag: onDiag)
        try self.thread.sync { Result { try self.build(theme: theme, resources: resources) } }.get()
    }

    /// A validator whose context is built on its thread without blocking the
    /// caller (a pool refilling while the main thread scrolls). Every later call
    /// queues behind the build; a failed build is reported as `validator-error`.
    public static func background(theme: String, resources: SandboxResources = .shared, thread: JSThread? = nil, onDiag: @escaping (String, String) -> Void) -> TrustedValidator {
        let v = TrustedValidator(thread: thread, onDiag: onDiag)
        v.thread.async { [weak v] in
            guard let v else { return }
            do { try v.build(theme: theme, resources: resources) } catch { v.onDiag("validator-error", "build: \(error)") }
        }
        return v
    }

    private init(thread: JSThread?, onDiag: @escaping (String, String) -> Void) {
        self.onDiag = onDiag
        self.thread = thread ?? JSThread(name: "widget.validator")
        ownsThread = thread == nil
    }

    /// On the validator's thread.
    private func build(theme: String, resources: SandboxResources) throws {
        // Bundles of another release are refused before anything runs (BundleStamp.swift).
        if let refusal = resources.refusal {
            onDiag("bundle-mismatch", refusal)
            throw BundleStamp.Refused(reason: refusal)
        }
        let js = QuickJSContext(memoryLimitBytes: 256 << 20, maxStackBytes: JSThread.jsStackLimit)
        self.js = js
        var fromBytecode = false
        js.registerFunction("__twNode") { [weak self] args in
            guard let self, let native = self.native, let id = Int(args.first ?? "") else { return .string("") }
            return .string(native.renderedTag(id) ?? "")
        }
        js.registerFunction("__diag") { [weak self] args in
            guard let self else { return .undefined }
            self.oracleDiagnostics += 1
            let kind = args.first ?? "", detail = args.count > 1 ? args[1] : ""
            // `limit` is fatal: a counted cap was passed under strict; the bundle refuses every later batch and the
            // host stops the widget with the reason once the call returns (`stopped`).
            if kind == "limit", self.scriptStop == nil { self.scriptStop = detail }
            self.onDiag(kind, detail)
            return .undefined
        }
        // Precompiled when shipped and matching the source, else the source (same rule as the engine).
        if let bytecode = resources.validatorBytecode {
            do { try js.evaluateBytecode(bytecode, source: resources.validatorBundle); fromBytecode = true }
            catch { onDiag("bytecode", "validator-bundle.js: falling back to source — \("\(error)".split(separator: "\n").first ?? "")") }
        }
        if !fromBytecode { try js.evaluate(resources.validatorBundle, filename: "validator-bundle.js") }
        // Flatten the API into globals so QuickJSContext.call can reach it.
        try js.evaluate("""
            globalThis.__v_processOps = (j) => __twValidator.processOps(j);
            globalThis.__v_validateQuery = (j) => __twValidator.validateQuery(j);
            globalThis.__v_validateListen = (j) => __twValidator.validateListen(j);
            globalThis.__v_validatePreventDefault = (j) => __twValidator.validatePreventDefault(j);
            globalThis.__v_validateAnim = (j) => __twValidator.validateAnim(j);
            globalThis.__v_setTheme = (t) => __twValidator.setTheme(t);
            globalThis.__v_nativeScrolling = (v) => __twValidator.setNativeScrolling(v);
            globalThis.__v_rewriteMediaQuery = (q) => __twValidator.rewriteMediaQuery(q);
            globalThis.__v_nodeCount = () => __twValidator.nodeCount();
            globalThis.__v_markLoaded = () => { __twValidator.markLoaded(); return ''; };
            globalThis.__v_isCanvas = (id) => __twValidator.isCanvas(Number(id)) ? 1 : 0;
            globalThis.__v_create = (t) => { __twValidator.create(t); return ''; };
            globalThis.__v_tables = () => __twValidator.tables();
            globalThis.__v_setPolicy = (p) => __twValidator.setPolicy ? __twValidator.setPolicy(p) : '';
            globalThis.__v_warmCss = (j) => __twValidator.warmStylesheets(j);
            globalThis.__v_native = (f) => __twValidator.native(f);
            globalThis.__v_cssProp = (n, v) => __twValidator.cssProp(n, v);
            globalThis.__v_cssAttr = (v) => __twValidator.cssAttr(v);
            globalThis.__v_cssValue = (v, w) => __twValidator.cssValue(v, w);
            globalThis.__v_cssSheet = (t) => __twValidator.cssSheet(t);
            globalThis.__v_canvasCmds = (j) => __twValidator.canvasCmds(j);
            """, filename: "validator-glue.js")
        try js.call("__v_create", [theme])
        js.collectGarbage()
        bundleSource = fromBytecode ? "bytecode" : "source"
    }

    deinit {
        // The context belongs to the validator's thread: release it there.
        if js != nil, !thread.isCurrent {
            let retired = RetiredRuntime(js)
            js = nil
            thread.async { retired.release() }
        }
        if ownsThread { thread.stop() }
    }

    private final class RetiredRuntime {
        private var context: QuickJSContext?

        init(_ context: QuickJSContext?) {
            self.context = context
        }

        func release() {
            context = nil
        }
    }

    // MARK: the validator's time

    /// The validator's thread only: where the CPU time of a call for the widget's messages goes, and how deep the calls
    /// that are measured nest (a call inside one, `processOps` in `processOpsForRenderer`, counts once).
    private var cpuSink: ((Double) -> Void)?
    private var meterDepth = 0

    /// Called on the validator's thread with the CPU time (ms, its thread's clock) of each call made for the widget's
    /// messages: the ops batches (JSON, for a renderer, binary), queries and their media rewrite, listen, preventDefault
    /// and anim batches. The host records it against the widget's JS quota (`SandboxEngine.chargeValidator`;
    /// docs/decisions.md, "The validator's time counts"). Nil: nothing is measured. Takes effect before any later call.
    public func setCpuSink(_ sink: ((Double) -> Void)?) {
        thread.async { [self] in cpuSink = sink }
    }

    /// On the validator's thread: `body`, its CPU time reported to the sink when it is the outermost measured call.
    private func metered<T>(_ body: () -> T) -> T {
        guard let sink = cpuSink, meterDepth == 0 else {
            meterDepth += 1
            defer { meterDepth -= 1 }
            return body()
        }
        meterDepth = 1
        let t0 = QuickJSContext.threadCPUTime()
        let result = body()
        meterDepth = 0
        sink((QuickJSContext.threadCPUTime() - t0) * 1000)
        return result
    }

    /// A fresh validator instance in the same context (the bundle stays
    /// evaluated): how a pool recycles a validator for the next widget.
    public func reset(theme: String) {
        thread.sync { [self] in
            if native != nil { _ = str("__v_native", ["0"]) }
            native = nil
            icons = nil
            policy = .compat
            scriptStop = nil
            loaded = false
            cpuSink = nil
        }
        _ = str("__v_create", [theme])
        thread.sync { [self] in js?.collectGarbage() }
    }

    /// Stylesheets found in the widget source, sanitized on the validator's thread while the sandbox boots;
    /// the bundle caches each result with its diagnostics and replays them when the widget's ops bring the
    /// same text, so the output is identical to a cold run.
    public func warmStylesheets(_ texts: [String]) {
        guard !texts.isEmpty, let data = try? JSONSerialization.data(withJSONObject: texts) else { return }
        let json = String(decoding: data, as: UTF8.self)
        thread.async { [self] in
            _ = str("__v_warmCss", [json])
        }
    }

    // MARK: native node model (binary ops)

    /// The node model kept in Swift for a widget whose sandbox sends binary ops; nil: the JS validator owns it.
    private var native: NativeValidator?
    /// The script validator's stop (diagnostic `limit`): the widget's model in JS, or the canvas commands it checks for the native model.
    private var scriptStop: String?

    /// Set once a counted cap was passed under strict (validator.ts `stopped`, diagnostic `limit`): the reason the host
    /// stops the widget with, as for memory or CPU. Every later batch comes back empty.
    public var stopped: String? { thread.sync { [self] in native?.stopReason ?? scriptStop } }
    var nativeModel: NativeValidator? { native }
    fileprivate var oracleDiagnostics = 0
    private static let tablesLock = NSLock()
    private static var cachedTables: [SandboxPolicy: NativeValidatorTables] = [:]

    public var usesNativeModel: Bool { thread.sync { [self] in native != nil } }

    /// Switches this validator to the native node model (`NativeValidator`, rules from `__twValidator.tables()`,
    /// CSS and canvas commands from the JS sanitizers). Must happen before the widget's first batch.
    public func useNativeModel(_ on: Bool) {
        thread.sync { [self] in
            guard js != nil else { return }
            if !on {
                if native != nil { _ = str("__v_native", ["0"]) }
                native = nil
                return
            }
            if native != nil { return }
            guard let tables = loadTables() else {
                onDiag("validator-error", "native tables")
                return
            }
            native = NativeValidator(tables: tables, oracle: self) { [weak self] k, d in
                self?.onDiag(k, d.firstIndex(of: "\u{0}").map { String(d[..<$0]) } ?? d)
            }
            if loaded { native?.markLoaded() }
            _ = str("__v_native", ["1"])
        }
    }

    func tablesJSON() -> String {
        str("__v_tables", [])
    }

    private func loadTables() -> NativeValidatorTables? {
        Self.tablesLock.lock()
        defer { Self.tablesLock.unlock() }
        if let cached = Self.cachedTables[policy] { return cached }
        let tables = NativeValidatorTables(json: str("__v_tables", []))
        Self.cachedTables[policy] = tables
        return tables
    }

    public private(set) var policy: SandboxPolicy = .compat

    public func setNativeScrolling(_ enabled: Bool) {
        thread.async { [self] in
            _ = str("__v_nativeScrolling", [enabled ? "1" : "0"])
        }
    }

    public func setPolicy(_ policy: SandboxPolicy) {
        thread.async { [self] in
            guard js != nil, self.policy != policy else { return }
            _ = str("__v_setPolicy", [policy.rawValue])
            self.policy = policy
            if native != nil {
                _ = str("__v_native", ["0"])
                native = nil
                onDiag("validator-error", "policy changed after the native model started")
            }
        }
    }

    /// A binary ops batch through the native model (`useNativeModel(true)` first).
    public func processBinary(_ payload: BinaryOpsPayload, canvas: [Int: WidgetCanvasBatch] = [:], typed: Bool) -> RenderBatch {
        thread.sync { [self] in metered { processBinaryNow(payload, canvas: canvas, typed: typed) } }
    }

    private func processBinaryNow(_ payload: BinaryOpsPayload, canvas: [Int: WidgetCanvasBatch], typed: Bool) -> RenderBatch {
        guard let native else {
            onDiag("protocol-violation", "binary ops without the native model")
            return .empty
        }
        icons?.beginBatch()
        switch BinaryOps.decode(payload, table: native.intern, icons: icons) {
        case .failure(let failure):
            onDiag("protocol-violation", "malformed binary ops batch (\(failure))")
            return .empty
        case .success(let decoded):
            return restoreIcons(Self.render(native.process(decoded, canvas: canvas), typed: typed))
        }
    }

    private static func render(_ ops: [WidgetOp], typed: Bool) -> RenderBatch {
        ops.isEmpty ? .empty : typed ? .ops(ops) : .json(WidgetOp.json(ops))
    }

    /// Runs `block` on the validator's thread, after everything queued before
    /// it; the validator's methods are inline there. Hosts use it to validate
    /// off the main thread while keeping the order of ops and queries.
    public func perform(_ block: @escaping () -> Void) {
        thread.async(block)
    }

    private func str(_ name: String, _ args: [String]) -> String {
        thread.sync { [self] in
            guard let js else { return "" } // the background build failed; already reported
            do {
                return try js.call(name, args).string ?? ""
            } catch {
                self.onDiag("validator-error", "\(name): \(error)")
                return ""
            }
        }
    }

    /// Untrusted ops batch (JSON) → validated RenderOps (JSON array; "[]" if nothing survived).
    /// One turn of the validator's thread: the stand-ins of a batch are the expansion's of that batch.
    public func processOps(_ json: String) -> String {
        thread.sync { [self] in metered { processOpsNow(json) } }
    }

    private func processOpsNow(_ json: String) -> String {
        let json = expandIcons(json)
        if let native {
            guard let value = JSONText.parse(json) else {
                onDiag("protocol-violation", "ops batch is not valid JSON")
                return "[]"
            }
            let ops = native.process(json: value)
            return ops.isEmpty ? "[]" : restoreIcons(WidgetOp.json(ops))
        }
        let out = str("__v_processOps", [json])
        return out.isEmpty ? "[]" : restoreIcons(out)
    }

    /// What a renderer gets for one ops batch: nothing, the JSON array, or typed ops for a renderer that takes them.
    public enum RenderBatch {
        case empty
        case json(String)
        case ops([WidgetOp])
    }

    /// Batches of canvas word payloads validated without JSON (tests and stats).
    public private(set) var nativeCanvasBatches = 0

    /// Validates a batch for a renderer. With `typed`, the output is parsed into `WidgetOp`s here, on the
    /// validator's thread, so the main thread never parses JSON. A batch made only of canvas placeholders whose
    /// word payloads pass `CanvasWords.envelope` (the same rules as the JS validator, stricter where they differ)
    /// on known canvas elements goes to the renderer as typed canvas ops without any JSON; anything else is
    /// spliced back into JSON and validated by the JS validator as before.
    public func processOpsForRenderer(_ json: String, canvas: [Int: WidgetCanvasBatch] = [:], typed: Bool) -> RenderBatch {
        thread.sync { [self] in metered { processOpsForRendererNow(json, canvas: canvas, typed: typed) } }
    }

    private func processOpsForRendererNow(_ json: String, canvas: [Int: WidgetCanvasBatch], typed: Bool) -> RenderBatch {
        guard let js else { return .empty }
        // A stopped widget gets nothing; the shortcut takes payloads within the strict caps only (10 000 commands, 32 KB
        // strings: `CanvasBatchFastPath`), anything bigger goes through the validator, which stops the widget there.
        // Under strict a payload that resizes its canvas (`r`) goes there too: the validator counts the widget's canvas
        // pixels and stops it past 2048².
        if (native?.stopReason ?? scriptStop) != nil { return .empty }
        if typed, !canvas.isEmpty, let list = CanvasWords.only(json, canvas),
           list.allSatisfy({ ((try? js.call("__v_isCanvas", [String($0.id)]))?.number ?? 0) == 1 && CanvasWords.envelope($0.batch)
               && (policy != .strict || (CanvasWords.textCharges($0.batch).isEmpty && CanvasWords.resizes($0.batch).isEmpty)) }) {
            nativeCanvasBatches += 1
            return .ops(list.map { WidgetOp(canvas: $0.id, batch: $0.batch) })
        }
        let out = processOps(canvas.isEmpty ? json : CanvasWords.splice(json, canvas))
        if out == "[]" { return .empty }
        return typed ? Self.typedOps(out) : .json(out)
    }

    static func typedOps(_ json: String) -> RenderBatch {
        guard let list = (try? JSONSerialization.jsonObject(with: Data(json.utf8))) as? [Any] else { return .json(json) }
        var ops: [WidgetOp] = []
        ops.reserveCapacity(list.count)
        for item in list {
            guard let fields = item as? [String: Any] else { return .json(json) }
            ops.append(WidgetOp(fields))
        }
        return ops.isEmpty ? .empty : .ops(ops)
    }

    /// Untrusted query → clean query JSON, or nil if rejected.
    public func validateQuery(_ json: String) -> String? {
        let out = measured("__v_validateQuery", [json])
        return out.isEmpty ? nil : out
    }

    /// `str` measured for the CPU sink.
    private func measured(_ name: String, _ args: [String]) -> String {
        thread.sync { [self] in metered { str(name, args) } }
    }

    public func validateListen(_ json: String) -> String { measured("__v_validateListen", [json]).nilIfEmpty ?? "[]" }
    public func validatePreventDefault(_ json: String) -> String { measured("__v_validatePreventDefault", [json]).nilIfEmpty ?? "[]" }
    public func validateAnim(_ json: String) -> String {
        if json.utf8.count > 4 << 20, json.utf16.count > 4 << 20 { return "[]" }
        return thread.sync { [self] in metered { validateAnimNow(json) } }
    }

    /// On the validator's thread.
    private func validateAnimNow(_ json: String) -> String {
        if let native {
            guard let value = JSONText.parse(json) else {
                onDiag("protocol-violation", "anim batch is not valid JSON")
                return "[]"
            }
            return native.validateAnim(json: value).json
        }
        return str("__v_validateAnim", [json]).nilIfEmpty ?? "[]"
    }

    /// The stylesheets again, sanitized for the scheme: the validator keeps them with the stand-ins.
    public func setTheme(_ theme: String) -> String {
        thread.sync { [self] in
            let fromJS = str("__v_setTheme", [theme]).nilIfEmpty ?? "[]"
            guard let native else { return restoreIcons(fromJS) }
            let ops = native.setTheme()
            return ops.isEmpty ? "[]" : restoreIcons(WidgetOp.json(ops))
        }
    }
    public func rewriteMediaQuery(_ query: String) -> String { measured("__v_rewriteMediaQuery", [query]) }
    public var memoryUsage: [String: Int]? { thread.sync { [self] in js?.memoryUsage } }
    public func collectGarbage() { thread.async { [self] in js?.collectGarbage() } }

    /// `processOps` and the node count after it, read in the same turn of the validator's thread.
    public func processOpsCounted(_ json: String) -> (ops: String, nodes: Int) {
        thread.sync { [self] in metered { (processOps(json), nodeCount) } }
    }

    public var nodeCount: Int { thread.sync { [self] in native?.nodeCount ?? Int((try? js?.call("__v_nodeCount"))?.number ?? 0) } }

    /// The loading tasks are over (their ops are in): text changes are rate-limited from now on (validator.ts `markLoaded`).
    public func markLoaded() {
        thread.sync { [self] in
            loaded = true
            _ = str("__v_markLoaded", [])
            native?.markLoaded()
        }
    }
    /// `markLoaded` was called: a native model made later starts loaded too. Validator's thread.
    private var loaded = false
}

extension TrustedValidator: NativeValidatorOracle {
    var diagnostics: Int { oracleDiagnostics }
    /// On the validator's thread, from the native model's calls.
    var limitStop: String? { scriptStop }

    func styleProperty(_ name: String, _ value: String) -> String? {
        let r = str("__v_cssProp", [name, value])
        return r.hasPrefix("+") ? String(r.dropFirst()) : nil
    }

    func styleAttribute(_ value: String) -> String {
        str("__v_cssAttr", [value])
    }

    func cssValue(_ value: String, where place: String) -> String? {
        let r = str("__v_cssValue", [value, place])
        return r.hasPrefix("+") ? String(r.dropFirst()) : nil
    }

    func stylesheet(_ text: String) -> String {
        str("__v_cssSheet", [text])
    }

    func canvasCommands(_ json: String) -> String {
        str("__v_canvasCmds", [json]).nilIfEmpty ?? "[]"
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}

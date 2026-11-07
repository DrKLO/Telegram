// The validator / sanitizer (src/host/validator.ts) compiled into
// validator-bundle.js and run in a TRUSTED QuickJS context — a separate
// runtime that never executes widget code. One instance per widget. Calls are
// synchronous from the caller's point of view (WidgetHost on the main thread)
// but execute on the validator's JSThread, whose 8 MB stack is what the
// css-tree parser needs. Input and output are JSON strings.
//
// The thread can be the validator's own (the default) or one shared by every
// validator in the process (`TrustedValidator.sharedThread`, = the iOS
// `sharedValidatorThread` option): validator calls are short and never block,
// so one thread serves a whole chat of widgets instead of a thread per bubble.
// `background()` builds the context on that thread without blocking the
// caller (a pool refilling while the list scrolls), `reset()` makes a fresh
// instance in the same context (the bundle stays evaluated), which is how
// ValidatorPool recycles a validator for the next widget.

package org.telegram.widgetsandbox

class TrustedValidator private constructor(
    thread: JSThread?,
    @Volatile var onDiag: (String, String) -> Unit,
) : AutoCloseable {
    companion object {
        /** One thread for every validator that asks for it (see the header). */
        val sharedThread: JSThread by lazy { JSThread("widget.validator") }

        /**
         * A validator whose context is built on its thread without blocking the
         * caller. Every later call queues behind the build; a failed build is
         * reported as `validator-error` and the validator then answers empty.
         */
        fun background(theme: String, resources: SandboxResources, thread: JSThread? = null, onDiag: (String, String) -> Unit): TrustedValidator {
            val v = TrustedValidator(thread, onDiag)
            v.thread.post {
                try {
                    v.build(theme, resources)
                } catch (t: Throwable) {
                    v.onDiag("validator-error", "build: $t")
                }
            }
            return v
        }
    }

    /** Builds the context and evaluates the bundle before returning (the caller's thread waits for the validator's). */
    constructor(theme: String, resources: SandboxResources, thread: JSThread? = null, onDiag: (String, String) -> Unit) : this(thread, onDiag) {
        this.thread.sync { build(theme, resources) }
    }

    val thread: JSThread = thread ?: JSThread("widget.validator")
    private val ownsThread = thread == null
    @Volatile private var js: QuickJSContext? = null
    /** Set once the bundle is evaluated: "bytecode" or "source"; "pending" before. */
    @Volatile var bundleSource = "pending"
        private set

    /**
     * Set once a counted cap was passed under strict (the bundle's fatal diagnostic `limit`): the reason the host stops
     * the widget with; from then on every batch comes back empty. Seen by the caller when the validator call that
     * passed it returns (the diagnostic arrives on the validator's thread during it); null again after [reset].
     */
    @Volatile var stopped: String? = null
        private set

    /** On the validator's thread. */
    private fun build(theme: String, resources: SandboxResources) {
        // Bundles of another release are refused before anything runs (BundleStamp.kt): no context, every call answers empty.
        resources.refusal?.let {
            onDiag("bundle-mismatch", it)
            return
        }
        val ctx = QuickJSContext(256L shl 20, JSThread.JS_STACK_LIMIT)
        try {
            ctx.registerFunction("__diag") { args ->
                val kind = args.getOrElse(0) { "" }
                val detail = args.getOrElse(1) { "" }
                if (kind == "limit" && stopped == null) stopped = detail
                onDiag(kind, detail)
                HostReturn.Undefined
            }
            // Precompiled when shipped and matching the source, else the source (same rule as the engine).
            val bytecode = resources.validatorBytecode
            var fromBytecode = false
            if (bytecode != null) {
                try {
                    ctx.evaluateBytecode(bytecode, resources.validatorBundle)
                    fromBytecode = true
                } catch (e: QuickJSException) {
                    onDiag("bytecode", "validator-bundle.js: falling back to source — ${e.message?.lineSequence()?.firstOrNull()}")
                }
            }
            if (!fromBytecode) ctx.evaluate(resources.validatorBundle, "validator-bundle.js")
            // Flatten the API into globals so QuickJSContext.call can reach it.
            ctx.evaluate(
                """
                globalThis.__v_processOps = (j) => __twValidator.processOps(j);
                globalThis.__v_validateQuery = (j) => __twValidator.validateQuery(j);
                globalThis.__v_validateListen = (j) => __twValidator.validateListen(j);
                globalThis.__v_validatePreventDefault = (j) => __twValidator.validatePreventDefault(j);
                globalThis.__v_setPolicy = (p) => __twValidator.setPolicy ? __twValidator.setPolicy(p) : '';
                globalThis.__v_validateAnim = (j) => __twValidator.validateAnim(j);
                globalThis.__v_setTheme = (t) => __twValidator.setTheme(t);
                globalThis.__v_rewriteMediaQuery = (q) => __twValidator.rewriteMediaQuery(q);
                globalThis.__v_nodeCount = () => __twValidator.nodeCount();
                globalThis.__v_markLoaded = () => { __twValidator.markLoaded(); return ''; };
                globalThis.__v_create = (t) => { __twValidator.create(t); return ''; };
                """.trimIndent(),
                "validator-glue.js",
            )
            ctx.call("__v_create", listOf(theme))
            js = ctx
            bundleSource = if (fromBytecode) "bytecode" else "source"
        } catch (t: Throwable) {
            ctx.close()
            throw t
        }
    }

    /** A fresh validator instance in the same context (the previous widget's node model is gone). */
    fun reset(theme: String) {
        str("__v_create", listOf(theme))
        stopped = null
    }

    private val meter = CpuMeter { js?.threadCpuNanos() ?: 0L }

    /**
     * Called on the validator's thread with the CPU time (ms, its thread's clock) of each call made for the widget's
     * messages: [processOps], [validateQuery], [rewriteMediaQuery], [validateListen], [validatePreventDefault],
     * [validateAnim]. The host records it against the widget's JS quota ([SandboxEngine.chargeValidator];
     * docs/decisions.md, "The validator's time counts"). Null: nothing is measured.
     */
    var cpuSink: ((Double) -> Unit)?
        get() = meter.sink
        set(value) { meter.sink = value }

    /** [str] measured for the CPU sink. */
    private fun metered(name: String, args: List<String>): String = thread.sync { meter.measure { str(name, args) } }

    /** Runs `block` on the validator's thread after everything queued before it; the validator's methods are inline there. */
    fun perform(block: () -> Unit) = thread.post(block)

    private fun str(name: String, args: List<String>): String = thread.sync {
        val ctx = js ?: return@sync "" // the background build failed; already reported
        try {
            ctx.call(name, args).string ?: ""
        } catch (e: QuickJSException) {
            onDiag("validator-error", "$name: ${e.message}")
            ""
        }
    }

    /** Untrusted ops batch (JSON) → validated RenderOps (JSON array; "[]" if nothing survived). */
    fun processOps(json: String): String = metered("__v_processOps", listOf(json)).ifEmpty { "[]" }

    /** Untrusted query → clean query JSON, or null if rejected. */
    fun validateQuery(json: String): String? = metered("__v_validateQuery", listOf(json)).ifEmpty { null }

    fun validateListen(json: String): String = metered("__v_validateListen", listOf(json)).ifEmpty { "[]" }
    fun validatePreventDefault(json: String): String = metered("__v_validatePreventDefault", listOf(json)).ifEmpty { "[]" }
    fun setPolicy(policy: String) { str("__v_setPolicy", listOf(policy)) }
    fun validateAnim(json: String): String = metered("__v_validateAnim", listOf(json)).ifEmpty { "[]" }
    fun setTheme(theme: String): String = str("__v_setTheme", listOf(theme)).ifEmpty { "[]" }
    fun rewriteMediaQuery(query: String): String = metered("__v_rewriteMediaQuery", listOf(query))
    val nodeCount: Int get() = thread.sync { (try { js?.call("__v_nodeCount")?.number } catch (e: QuickJSException) { null } ?: 0.0).toInt() }
    /** The loading tasks are over (their ops are in): text changes are rate-limited from now on (validator.ts `markLoaded`). */
    fun markLoaded() { str("__v_markLoaded", emptyList()) }

    /** Frees the context; stops the thread when it is the validator's own. A pooled validator is recycled by ValidatorPool instead. */
    override fun close() {
        thread.post { js?.close(); js = null }
        if (ownsThread) thread.stop()
    }
}

/**
 * The validator's time on the widget's messages: the CPU time of the calling thread around a call, reported to [sink]
 * once per outermost call (a call inside a measured one is part of it). Pure, so the JVM tests check it with a fake
 * clock; [TrustedValidator] runs it on its thread with QuickJS's thread clock.
 */
class CpuMeter(private val clockNanos: () -> Long) {
    @Volatile var sink: ((Double) -> Unit)? = null
    /** The measuring thread only. */
    private var depth = 0

    fun <T> measure(block: () -> T): T {
        val sink = sink
        if (sink == null || depth > 0) {
            depth++
            try { return block() } finally { depth-- }
        }
        depth = 1
        val t0 = clockNanos()
        try {
            return block()
        } finally {
            depth = 0
            sink((clockNanos() - t0) / 1e6)
        }
    }
}

// Thin Kotlin wrapper over quickjs-ng (through quickjs_jni.c → qjs_shim). One
// runtime, one context, string-based host functions. All methods must be called
// from the thread that owns the context (a JSThread in the product).

package org.telegram.widgetsandbox

import android.util.Log
import androidx.annotation.Keep

class QuickJSException(message: String) : RuntimeException(message)

/** Value returned by a host function into the sandbox. */
sealed class HostReturn {
    object Undefined : HostReturn()
    @Keep object Null : HostReturn()
    class Str(val value: String) : HostReturn()
    class Num(val value: Double) : HostReturn()
    /** Read by JNI through the `bytes` field. */
    @Keep class ArrayBuffer(@JvmField val bytes: ByteArray) : HostReturn()
}

/** Result of calling a sandbox function from the host. */
sealed class JSResult {
    object Undefined : JSResult()
    class Str(val value: String) : JSResult()
    class Num(val value: Double) : JSResult()

    val string: String? get() = (this as? Str)?.value
    val number: Double? get() = (this as? Num)?.value
}

class QuickJSContext(memoryLimitBytes: Long, maxStackBytes: Long = DEFAULT_MAX_STACK) : AutoCloseable {
    companion object {
        /**
         * Must stay below the real stack of whichever thread enters JS. QuickJS only
         * *detects* overflow against this limit; the OS kills the process at the real
         * one. Product contexts live on a JSThread (8 MB) and use JSThread.JS_STACK_LIMIT.
         */
        const val DEFAULT_MAX_STACK = 384L shl 10

        init {
            System.loadLibrary("twquickjs")
        }

        /** CLOCK_BOOTTIME nanoseconds, the clock the interrupt handler compares deadlines against. */
        @JvmStatic external fun nativeNow(): Long
    }

    private var handle: Long = nativeCreate(memoryLimitBytes, maxStackBytes)
    private val functions = ArrayList<(Array<String>) -> HostReturn>()

    init {
        check(handle != 0L) { "QuickJS runtime allocation failed" }
    }

    /** Interrupt deadline in [nativeNow] nanoseconds; Long.MAX_VALUE disables it. */
    var deadlineNanos: Long
        get() = nativeGetDeadline(handle)
        set(value) = nativeSetDeadline(handle, value)

    /** Interrupt deadline in CPU nanoseconds of the thread running JS (`threadCpuNanos`); Long.MAX_VALUE disables it. */
    var cpuDeadlineNanos: Long = Long.MAX_VALUE
        set(value) {
            field = value
            nativeSetCpuDeadline(handle, value)
        }

    /** Keeps a private reference to the global function [name]; [call] uses it from then on, even after the global is deleted. */
    fun captureGlobalFunction(name: String): Boolean = nativeCaptureGlobalFunction(handle, name)

    /** CPU time consumed by the calling thread; unaffected by how busy the device is. */
    fun threadCpuNanos(): Long = nativeThreadCpuNanos()

    /** Set by the interrupt handler when the deadline fired; reset by the caller. */
    val interrupted: Boolean get() = nativeInterrupted(handle)
    fun resetInterrupted() = nativeResetInterrupted(handle)

    val memoryUsed: Long get() = nativeMemoryUsed(handle)
    val hasPendingJob: Boolean get() = nativeIsJobPending(handle)

    // MARK: host functions

    /** Exposes `name(...)` to the sandbox. Arguments arrive as strings (numbers are formatted by JS). */
    fun registerFunction(name: String, handler: (Array<String>) -> HostReturn) {
        functions.add(handler)
        nativeRegisterFunction(handle, name, functions.size - 1)
    }

    /** Called from quickjs_jni.c (host_trampoline). Never throws. */
    @Keep
    @Suppress("unused")
    private fun dispatch(magic: Int, args: Array<ByteArray>): Any? {
        return try {
            when (val r = functions[magic](Array(args.size) { String(args[it], Charsets.UTF_8) })) {
                HostReturn.Undefined -> null
                HostReturn.Null -> r
                is HostReturn.Str -> r.value.toByteArray(Charsets.UTF_8)
                is HostReturn.Num -> r.value
                is HostReturn.ArrayBuffer -> r
            }
        } catch (t: Throwable) {
            Log.e("twquickjs", "host function threw", t)
            null
        }
    }

    /** Called from quickjs_jni.c to raise a QuickJSException with a UTF-8 description. */
    @Keep
    @Suppress("unused")
    private fun raise(description: ByteArray) {
        throw QuickJSException(String(description, Charsets.UTF_8))
    }

    // MARK: evaluating and calling

    @Throws(QuickJSException::class)
    fun evaluate(code: String, filename: String, asModule: Boolean = false): JSResult =
        toResult(nativeEval(handle, code.toByteArray(Charsets.UTF_8), filename, asModule))

    /**
     * Runs one of our own precompiled bundles (`.qbc`, see qjs_shim.h), checked
     * against the source it was built from; throws on any mismatch so the caller
     * can fall back to [evaluate]. Never for widget code: bytecode is not verified.
     */
    @Throws(QuickJSException::class)
    fun evaluateBytecode(blob: ByteArray, source: String): JSResult =
        toResult(nativeEvalBytecode(handle, blob, source.toByteArray(Charsets.UTF_8)))

    /** Compiles `code` into the same container the build ships (tests). */
    @Throws(QuickJSException::class)
    fun compileBytecode(code: String, filename: String): ByteArray =
        nativeCompileBytecode(handle, code.toByteArray(Charsets.UTF_8), filename)

    /** Calls the global function `name` with string arguments. */
    @Throws(QuickJSException::class)
    fun call(name: String, args: List<String> = emptyList()): JSResult =
        toResult(nativeCall(handle, name, Array(args.size) { args[it].toByteArray(Charsets.UTF_8) }))

    /** Runs queued promise jobs. Errors thrown by jobs are reported through `onError` and do not stop the loop. */
    fun executePendingJobs(onError: (QuickJSException) -> Unit) {
        for (i in 0 until 100) {
            val r = try {
                nativeExecutePendingJob(handle)
            } catch (e: QuickJSException) {
                onError(e)
                continue
            }
            if (r == 0) break
        }
    }

    private fun toResult(raw: Any?): JSResult = when (raw) {
        null -> JSResult.Undefined
        is ByteArray -> JSResult.Str(String(raw, Charsets.UTF_8))
        is Double -> JSResult.Num(raw)
        else -> JSResult.Undefined
    }

    override fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0
        }
    }

    private external fun nativeCreate(memoryLimit: Long, maxStack: Long): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeRegisterFunction(handle: Long, name: String, magic: Int)
    private external fun nativeEval(handle: Long, code: ByteArray, filename: String, asModule: Boolean): Any?
    private external fun nativeEvalBytecode(handle: Long, blob: ByteArray, source: ByteArray): Any?
    private external fun nativeCompileBytecode(handle: Long, code: ByteArray, filename: String): ByteArray
    private external fun nativeCall(handle: Long, name: String, args: Array<ByteArray>): Any?
    private external fun nativeIsJobPending(handle: Long): Boolean
    private external fun nativeExecutePendingJob(handle: Long): Int
    private external fun nativeMemoryUsed(handle: Long): Long
    private external fun nativeSetDeadline(handle: Long, nanos: Long)
    private external fun nativeGetDeadline(handle: Long): Long
    private external fun nativeSetCpuDeadline(handle: Long, nanos: Long)
    private external fun nativeThreadCpuNanos(): Long
    private external fun nativeCaptureGlobalFunction(handle: Long, name: String): Boolean
    private external fun nativeInterrupted(handle: Long): Boolean
    private external fun nativeResetInterrupted(handle: Long)
}

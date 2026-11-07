// A QuickJS runtime is pinned to one thread with a known, large stack.
//
// Why not an executor or a HandlerThread: QuickJS measures recursion from the
// stack pointer it saw at creation, and a pooled thread hops (the iOS port hit
// a bogus "Maximum call stack size exceeded" from exactly that). HandlerThread
// has no stack-size constructor and the default ~1 MB is too small for css-tree
// inside the validator. So every runtime gets a plain Thread with an 8 MB stack
// and its own Looper; the QuickJS limit (6 MB) stays well under the real stack,
// which turns any overflow into a catchable RangeError.

package org.telegram.widgetsandbox

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch

class JSThread(name: String) {
    companion object {
        /** Real stack of the thread; QuickJSContext.maxStackBytes must stay below it. */
        const val STACK_SIZE = 8L shl 20
        /** Safe QuickJS limit for a context that only ever runs on a JSThread. */
        const val JS_STACK_LIMIT = 6L shl 20
    }

    private val ready = CountDownLatch(1)
    @Volatile private var handler: Handler? = null
    @Volatile private var stopped = false
    private val thread = Thread(null, {
        Looper.prepare()
        handler = Handler(Looper.myLooper()!!)
        ready.countDown()
        Looper.loop()
    }, name, STACK_SIZE)

    init {
        thread.isDaemon = true
        thread.start()
        ready.await()
    }

    val isCurrent: Boolean get() = Thread.currentThread() === thread

    /** Runs `block` on the thread, after everything queued before it. */
    fun post(block: () -> Unit) {
        if (stopped) return
        handler?.post(block)
    }

    /** Runs `block` on the thread and waits for it (inline when already there). */
    fun <T> sync(block: () -> T): T {
        if (isCurrent) return block()
        check(!stopped) { "JSThread stopped" }
        val done = CountDownLatch(1)
        var result: T? = null
        var error: Throwable? = null
        handler!!.post {
            try {
                result = block()
            } catch (t: Throwable) {
                error = t
            }
            done.countDown()
        }
        done.await()
        error?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    class Scheduled internal constructor(private val handler: Handler?, block: () -> Unit, stopped: () -> Boolean) {
        @Volatile private var cancelled = false
        internal val runnable = Runnable { if (!cancelled && !stopped()) block() }
        /**
         * Safe from any thread; the block will not run after this returns, and the queue lets go of it at once (as Swift's
         * `Timer.invalidate`): a cancelled timer no longer waits there until its time, up to 24 h for a widget's, so a
         * widget setting and clearing timers in a loop cannot grow the queue.
         */
        fun cancel() {
            cancelled = true
            handler?.removeCallbacks(runnable)
        }
    }

    /** Runs `block` on the thread after `delayMs` unless cancelled. */
    fun after(delayMs: Long, block: () -> Unit): Scheduled {
        val h = handler
        val handle = Scheduled(h, block) { stopped }
        h?.postDelayed(handle.runnable, maxOf(0L, delayMs))
        return handle
    }

    /** Stops the looper once queued (non-delayed) work has run; the thread then exits. */
    fun stop() {
        if (stopped) return
        stopped = true
        handler?.post { Looper.myLooper()?.quit() }
    }
}

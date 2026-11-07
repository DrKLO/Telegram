package org.telegram.widgetsandbox

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch

@RunWith(AndroidJUnit4::class)
class QuickJSContextTest {
    private val probe = "(function(){ var n = 0; function r(){ n++; r(); } try { r(); } catch (e) { if (!(e instanceof RangeError)) throw e; } return n; })()"

    @Test
    fun evaluatesAndCallsWithRealUtf8() {
        QuickJSContext(16L shl 20).use { js ->
            assertEquals(3.0, js.evaluate("1 + 2", "t.js").number)
            assertEquals("héllo 🎉", js.evaluate("'h' + 'éllo ' + '\\u{1F389}'", "t.js").string)
            js.evaluate("globalThis.echo = (a, b) => a + '|' + b + '|' + a.length", "t.js")
            assertEquals("🎉|ß|2", js.call("echo", listOf("🎉", "ß")).string)
            assertTrue(js.evaluate("undefined", "t.js") is JSResult.Undefined)
        }
    }

    @Test
    fun bytecodeRoundTripAndStaleBlobRefused() {
        QuickJSContext(16L shl 20).use { js ->
            val blob = js.compileBytecode("globalThis.answer = 6 * 7;", "t.js")
            assertTrue(blob.size > 24)
            js.evaluateBytecode(blob, "globalThis.answer = 6 * 7;")
            assertEquals(42.0, js.evaluate("answer", "t.js").number)
            try {
                js.evaluateBytecode(blob, "globalThis.answer = 6 * 8;")
                fail("a blob compiled from another source must be refused")
            } catch (e: QuickJSException) {
                assertTrue(e.message, e.message!!.contains("different source"))
            }
            val bad = blob.copyOf().also { it[0] = 'X'.code.toByte() }
            try { js.evaluateBytecode(bad, "globalThis.answer = 6 * 7;"); fail("corrupt header") } catch (e: QuickJSException) {}
            try { js.evaluateBytecode(ByteArray(0), ""); fail("empty blob") } catch (e: QuickJSException) {}
        }
    }

    @Test
    fun hostFunctionRoundTrip() {
        QuickJSContext(16L shl 20).use { js ->
            val seen = ArrayList<List<String>>()
            js.registerFunction("__host") { args ->
                seen.add(args.toList())
                when (args[0]) {
                    "str" -> HostReturn.Str("→ " + args[1])
                    "num" -> HostReturn.Num(42.5)
                    "null" -> HostReturn.Null
                    "buf" -> HostReturn.ArrayBuffer(byteArrayOf(1, 2, 3))
                    else -> HostReturn.Undefined
                }
            }
            val out = js.evaluate(
                "[__host('str', 'émoji 🎉'), __host('num', 7), String(__host('null')), new Uint8Array(__host('buf')).join(','), String(__host('x'))].join(';')",
                "t.js",
            ).string
            assertEquals("→ émoji 🎉;42.5;null;1,2,3;undefined", out)
            assertEquals(listOf("num", "7"), seen[1])
        }
    }

    @Test
    fun exceptionsCarryNameMessageAndStack() {
        QuickJSContext(16L shl 20).use { js ->
            try {
                js.evaluate("function f(){ throw new TypeError('bad ✗') } f()", "boom.js")
                fail("expected an exception")
            } catch (e: QuickJSException) {
                assertTrue(e.message, e.message!!.startsWith("TypeError: bad ✗"))
                assertTrue(e.message, e.message!!.contains("boom.js"))
            }
            try {
                js.call("nope")
                fail("expected an exception")
            } catch (e: QuickJSException) {
                assertEquals("nope is not a function", e.message)
            }
            // The context is still usable afterwards.
            assertEquals(2.0, js.evaluate("1+1", "t.js").number)
        }
    }

    @Test
    fun deadlineInterruptsAndIsReported() {
        QuickJSContext(16L shl 20).use { js ->
            js.deadlineNanos = QuickJSContext.nativeNow() + 150_000_000L
            try {
                js.evaluate("for(;;){}", "loop.js")
                fail("expected an interrupt")
            } catch (e: QuickJSException) {
                assertTrue(e.message, e.message!!.contains("interrupted"))
            }
            assertTrue(js.interrupted)
            js.resetInterrupted()
            js.deadlineNanos = Long.MAX_VALUE
            assertFalse(js.interrupted)
            assertEquals(2.0, js.evaluate("1+1", "t.js").number)
        }
    }

    @Test
    fun memoryLimitThrowsOutOfMemory() {
        QuickJSContext(8L shl 20).use { js ->
            try {
                js.evaluate("var a = []; for (;;) a.push(new Array(100000).fill(1));", "mem.js")
                fail("expected out of memory")
            } catch (e: QuickJSException) {
                assertTrue(e.message, e.message!!.lowercase().contains("out of memory"))
            }
        }
    }

    @Test
    fun promiseJobsRunAndReportErrors() {
        QuickJSContext(16L shl 20).use { js ->
            // A throw inside a reaction only rejects the derived promise; the job itself
            // succeeds (same on iOS). Job *errors* come from the engine, e.g. an interrupt.
            js.evaluate("globalThis.log = []; Promise.resolve(1).then(v => log.push(v)); Promise.resolve().then(() => { throw new Error('in job') }).catch(e => log.push(e.message));", "p.js")
            assertTrue(js.hasPendingJob)
            val errors = ArrayList<String>()
            js.executePendingJobs { errors.add(it.message ?: "") }
            assertFalse(js.hasPendingJob)
            assertEquals("1,in job", js.evaluate("log.join()", "t.js").string)
            assertTrue(errors.toString(), errors.isEmpty())
            js.evaluate("Promise.resolve().then(() => { for(;;){} })", "p2.js")
            js.deadlineNanos = QuickJSContext.nativeNow() + 100_000_000L
            js.executePendingJobs { errors.add(it.message ?: "") }
            assertTrue(errors.toString(), errors.any { it.contains("interrupted") })
            assertTrue(js.interrupted)
        }
    }

    /** QuickJS must re-read the stack top on every entry, or a call from another thread fails with a bogus RangeError. */
    @Test
    fun stackCheckFollowsTheCallingThread() {
        var js: QuickJSContext? = null
        var depthOnCreator = 0.0
        val made = CountDownLatch(1)
        val t = Thread(null, {
            js = QuickJSContext(16L shl 20)
            depthOnCreator = js!!.evaluate(probe, "t.js").number ?: -1.0
            made.countDown()
        }, "creator", 4L shl 20)
        t.start()
        made.await()
        val depthHere = js!!.evaluate(probe, "t.js").number ?: -1.0
        println("quickjs recursion depth at default stack limit: creator $depthOnCreator, here $depthHere")
        assertTrue(depthOnCreator > 20)
        assertTrue(depthHere > 20)
        assertTrue(Math.abs(depthHere - depthOnCreator) / depthOnCreator < 0.1)
        js!!.close()
    }

    /** Product contexts live on a JSThread (8 MB stack, 6 MB QuickJS limit): css-tree and chart libraries need hundreds of levels. */
    @Test
    fun pinnedThreadGivesDeepRecursionAndTimers() {
        val t = JSThread("test.js")
        val depth = t.sync {
            QuickJSContext(16L shl 20, JSThread.JS_STACK_LIMIT).use { js -> js.evaluate(probe, "t.js").number ?: -1.0 }
        }
        println("quickjs recursion depth on JSThread: $depth")
        assertTrue("depth $depth", depth > 300)
        val fired = CountDownLatch(1)
        var cancelledRan = false
        var onThread = false
        val c = t.after(10) { cancelledRan = true }
        c.cancel()
        t.after(30) { onThread = t.isCurrent; fired.countDown() }
        fired.await()
        assertTrue(onThread)
        assertFalse(cancelledRan)
        t.stop()
    }

    @Test
    fun syncPropagatesExceptionsAndResults() {
        val t = JSThread("test.sync")
        assertEquals("ok", t.sync { "ok" })
        try {
            t.sync<Unit> { throw IllegalStateException("inside") }
            fail("expected rethrow")
        } catch (e: IllegalStateException) {
            assertEquals("inside", e.message)
        }
        assertNull(t.sync { null })
        assertNotNull(t.sync { Thread.currentThread().name })
        t.stop()
    }
}

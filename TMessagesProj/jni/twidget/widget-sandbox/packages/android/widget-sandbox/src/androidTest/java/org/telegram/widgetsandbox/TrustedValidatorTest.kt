package org.telegram.widgetsandbox

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

@RunWith(AndroidJUnit4::class)
class TrustedValidatorTest {
    private val resources = SandboxResources.get(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun poolRecyclesAValidatorWithAFreshInstance() {
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        fun onMain(block: () -> Unit) { val done = java.util.concurrent.CountDownLatch(1); main.post { block(); done.countDown() }; done.await() }
        onMain { ValidatorPool.prewarm(resources, 2) }
        // Built in the background on the shared thread; a take right away still gets a validator (calls queue behind the build).
        var v: TrustedValidator? = null
        onMain { v = ValidatorPool.take("dark") }
        val first = v!!
        assertTrue(first.thread === TrustedValidator.sharedThread)
        assertEquals(1, ValidatorPool.hits)
        val fresh = TrustedValidator("light", resources) { _, _ -> }.use { it.nodeCount }
        first.processOps("[{\"op\":\"el\",\"id\":2,\"tag\":\"html\"},{\"op\":\"ins\",\"parent\":1,\"id\":2,\"before\":null}]")
        assertEquals(fresh + 1, first.nodeCount)
        assertEquals("bytecode", first.bundleSource)
        // Recycled: the same context, a fresh instance — the previous widget's nodes are gone.
        onMain { ValidatorPool.recycle(first) }
        assertEquals(1, ValidatorPool.recycled)
        var again: TrustedValidator? = null
        onMain { again = ValidatorPool.take("light") }
        assertTrue(again === first)
        assertEquals(fresh, first.nodeCount)
        assertEquals("[]", first.processOps("[{\"op\":\"el\",\"id\":2,\"tag\":\"script\"}]"))
        onMain { ValidatorPool.recycle(first); ValidatorPool.targetSize = 0; ValidatorPool.drain() }
        assertEquals(0, ValidatorPool.count)
    }

    @Test
    fun diagBridge() {
        val diags: MutableList<Pair<String, String>> = Collections.synchronizedList(ArrayList())
        TrustedValidator("light", resources) { k, d -> diags.add(k to d) }.use { v ->
            // An unknown element is reported through __diag (tag-replaced).
            v.processOps("[{\"op\":\"el\",\"id\":2,\"tag\":\"x-foo\"}]")
            assertTrue("diags=$diags", diags.any { it.first == "tag-replaced" })
        }
    }

    @Test
    fun stripsExternalUrlsAndHandlers() {
        val diags: MutableList<Pair<String, String>> = Collections.synchronizedList(ArrayList())
        TrustedValidator("light", resources) { k, d -> diags.add(k to d) }.use { v ->
            val ops = """
            [{"op":"el","id":2,"tag":"html"},{"op":"ins","parent":1,"id":2,"before":null},
             {"op":"el","id":3,"tag":"body"},{"op":"ins","parent":2,"id":3,"before":null},
             {"op":"el","id":4,"tag":"img","attrs":{"src":"http://10.0.2.2:9999/hit/x","onerror":"fetch('http://10.0.2.2:9999/e')"}},{"op":"ins","parent":3,"id":4,"before":null},
             {"op":"el","id":5,"tag":"iframe","attrs":{"src":"http://10.0.2.2:9999/hit/f"}},{"op":"ins","parent":3,"id":5,"before":null},
             {"op":"el","id":6,"tag":"style"},{"op":"ins","parent":3,"id":6,"before":null},
             {"op":"text","id":7,"data":"@import url(http://10.0.2.2:9999/i); p{color:red;background:url(http://10.0.2.2:9999/b)}"},{"op":"ins","parent":3,"id":7,"before":null}]
            """.trimIndent()
            val out = v.processOps(ops)
            val rendered = JSONArray(out)
            // Nothing that is attached, set as an attribute or emitted as CSS may carry the canary.
            val attached = JSONArray()
            for (i in 0 until rendered.length()) {
                val op = rendered.getJSONObject(i)
                if (op.optString("op") in listOf("ins", "attr", "css", "style", "el")) attached.put(op)
            }
            val attachedJson = attached.toString()
            assertFalse(attachedJson, attachedJson.contains("10.0.2.2"))
            assertFalse(attachedJson, attachedJson.lowercase().contains("onerror"))
            assertFalse(attachedJson, attachedJson.contains("\"tag\":\"iframe\""))
            assertTrue(out, out.contains("data-tg-placeholder"))
            assertTrue("diags=$diags", diags.any { it.first == "url-blocked" })
            assertEquals("{\"q\":\"rect\",\"id\":4}", v.validateQuery("{\"q\":\"rect\",\"id\":4}"))
            assertNull(v.validateQuery("{\"q\":\"eval\",\"code\":\"1\"}"))
            assertEquals("(min-width: 99999px)", v.rewriteMediaQuery("(prefers-color-scheme: dark)"))
        }
    }

    /**
     * Strict: a counted cap passed is fatal (the bundle's `limit`, docs/decisions.md "Strict limits from the start"): that
     * batch and every later one come back empty and `stopped` has the reason the host stops the widget with. A fresh
     * instance (`reset`, the pool's recycling) starts clean, compat by default, and draws the same text.
     */
    @Test
    fun aCountedCapStopsTheValidator() {
        val diags: MutableList<String> = Collections.synchronizedList(ArrayList())
        val skeleton = "[{\"op\":\"el\",\"id\":2,\"tag\":\"html\"},{\"op\":\"ins\",\"parent\":1,\"id\":2,\"before\":null},{\"op\":\"el\",\"id\":3,\"tag\":\"body\"},{\"op\":\"ins\",\"parent\":2,\"id\":3,\"before\":null}]"
        val text = "[{\"op\":\"text\",\"id\":4,\"data\":\"${"x".repeat(40000)}\"},{\"op\":\"ins\",\"parent\":3,\"id\":4,\"before\":null}]"
        val reason = "Text limit exceeded: a text value of 40000 characters (max 32768)"
        TrustedValidator("light", resources) { k, d -> diags.add("$k|$d") }.use { v ->
            v.setPolicy("strict")
            assertTrue(v.processOps(skeleton).contains("\"tag\":\"body\""))
            assertNull(v.stopped)
            assertEquals("[]", v.processOps(text))
            assertEquals(reason, v.stopped)
            assertEquals("$diags", 1, diags.count { it == "limit|$reason" })
            assertEquals("[]", v.processOps("[{\"op\":\"el\",\"id\":5,\"tag\":\"p\"},{\"op\":\"ins\",\"parent\":3,\"id\":5,\"before\":null}]"))
            v.reset("light")
            assertNull(v.stopped)
            v.processOps(skeleton)
            assertTrue(v.processOps(text).contains("x".repeat(40000)))
            assertNull(v.stopped)
        }
    }

    /** Churn validators, each with its own thread, and let them tear down (the iOS demo once crashed on the second widget). */
    @Test
    fun repeatedCreateAndTeardown() {
        for (i in 0 until 25) {
            TrustedValidator(if (i % 2 == 0) "light" else "dark", resources) { _, _ -> }.use { v ->
                assertTrue(v.processOps("[{\"op\":\"el\",\"id\":2,\"tag\":\"p\"},{\"op\":\"ins\",\"parent\":1,\"id\":2,\"before\":null}]").contains("\"tag\":\"p\""))
                assertEquals(2, v.nodeCount)
            }
        }
    }

    @Test
    fun stylesheetTextIsOnlyShippedSanitized() {
        val diags: MutableList<String> = Collections.synchronizedList(ArrayList())
        TrustedValidator("dark", resources) { k, d -> diags.add("$k: $d") }.use { v ->
            val ops = """
            [{"op":"el","id":2,"tag":"html"},{"op":"ins","parent":1,"id":2,"before":null},
             {"op":"el","id":6,"tag":"style"},{"op":"ins","parent":2,"id":6,"before":null},
             {"op":"text","id":7,"data":"@import url(http://10.0.2.2:9999/i); @media (prefers-color-scheme: dark){p{color:red}} p{background:url(http://10.0.2.2:9999/b)}"},{"op":"ins","parent":6,"id":7,"before":null}]
            """.trimIndent()
            val out = v.processOps(ops)
            assertTrue(out, out.contains("{\"op\":\"rm\",\"id\":7}"))
            assertTrue("$out\n$diags", out.contains("@media (min-width: 0px){p{color:red}}"))
            assertTrue("$diags", diags.any { it.startsWith("url-blocked") || it.startsWith("css-dropped") })
            assertFalse(out, out.contains("\"css\",\"id\":6,\"text\":\"@import"))
            assertEquals(4, v.nodeCount)
        }
    }
}

package org.telegram.widgetsandbox

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Bundles of another release (BundleStamp.kt) are refused before anything runs: the engine dies at boot with
 * `bundle-mismatch`, the validator answers empty. The parser and the shipped stamp: BundleStampTest (JVM).
 */
@RunWith(AndroidJUnit4::class)
class BundleStampEngineTest {
    private val shared = SandboxResources.get(InstrumentationRegistry.getInstrumentation().targetContext)

    /** The shipped bundles, with [name]'s first line replaced by [line] (null: removed). */
    private fun resources(name: String, line: String?) = SandboxResources { path ->
        val bytes = when (path) {
            "sandbox-bundle.js" -> shared.sandboxBundle.toByteArray()
            "validator-bundle.js" -> shared.validatorBundle.toByteArray()
            "renderer-bundle.js" -> shared.rendererBundle.toByteArray()
            "sandbox-bundle.qbc" -> shared.sandboxBytecode
            "validator-bundle.qbc" -> shared.validatorBytecode
            else -> null
        }
        if (path != name || bytes == null) bytes else {
            val rest = String(bytes).substringAfter('\n', "")
            (if (line == null) rest else "$line\n$rest").toByteArray()
        }
    }

    @Test
    fun engineRefusesBundlesOfAnotherRelease() {
        val cases = listOf(
            "sandbox-bundle.js" to BundleStamp("9.9.9", WidgetSandboxVersion.PROTOCOL + 1, WidgetSandboxVersion.PROFILE, WidgetSandboxVersion.SDK).line,
            "sandbox-bundle.js" to null,
            "validator-bundle.js" to BundleStamp("9.9.9", WidgetSandboxVersion.PROTOCOL, WidgetSandboxVersion.PROFILE + 1, WidgetSandboxVersion.SDK).line,
            "renderer-bundle.js" to BundleStamp("9.9.9", WidgetSandboxVersion.PROTOCOL, WidgetSandboxVersion.PROFILE, WidgetSandboxVersion.SDK + 1).line,
        )
        for ((name, line) in cases) {
            val res = resources(name, line)
            val d = RecordingDelegate()
            val config = SandboxConfig(innerWidth = 400.0, innerHeight = 300.0, devicePixelRatio = 2.0, theme = "light")
            val engine = SandboxEngine("<p>x</p><script>TelegramWidget.ready(); console.log('widget ran');</script>", config, res)
            engine.delegate = d
            engine.start()
            assertTrue("$name: engine did not finish in time", d.loaded.await(20, TimeUnit.SECONDS))
            engine.thread.sync {}
            assertEquals(name, "sandbox bundle failed to load", d.died)
            assertEquals("$name: ${d.diags}", listOf(res.refusal), d.diags.filter { it.first == "bundle-mismatch" }.map { it.second })
            assertFalse(name, d.logs.any { it.second.contains("widget ran") })
            assertTrue(name, d.ops.isEmpty())
            engine.terminate()
        }
    }

    @Test
    fun validatorRefusesBundlesOfAnotherRelease() {
        val res = resources("validator-bundle.js", BundleStamp("9.9.9", WidgetSandboxVersion.PROTOCOL, WidgetSandboxVersion.PROFILE + 1, WidgetSandboxVersion.SDK).line)
        val diags = ArrayList<Pair<String, String>>()
        TrustedValidator("light", res) { k, v -> synchronized(diags) { diags.add(k to v) } }.use { v ->
            assertEquals("[]", v.processOps("[{\"op\":\"el\",\"id\":2,\"tag\":\"p\"}]"))
            assertEquals("pending", v.bundleSource)
        }
        assertEquals(listOf("bundle-mismatch" to res.refusal), synchronized(diags) { diags.toList() })
        assertNull(shared.refusal)
    }
}

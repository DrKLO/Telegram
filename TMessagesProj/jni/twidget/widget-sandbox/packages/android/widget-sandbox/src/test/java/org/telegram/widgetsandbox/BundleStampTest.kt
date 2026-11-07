package org.telegram.widgetsandbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * VERSION.json's stamp on the bundles (BundleStamp.kt) on the JVM: the parser, the refusal, and the bundles in
 * src/main/assets/WidgetJS (from the module directory, Gradle's working directory for unit tests) carry this glue's
 * stamp. The engine's refusal itself needs the native library: BundleStampEngineTest (instrumented).
 */
class BundleStampTest {
    private fun shipped(path: String): ByteArray? = File("src/main/assets/WidgetJS/$path").takeIf { it.isFile }?.readBytes()

    /** [source] with its first line replaced by [line] (null: removed). */
    private fun restamped(source: String, line: String?): String {
        val rest = source.substringAfter('\n', "")
        return if (line == null) rest else "$line\n$rest"
    }

    private fun stamp(protocol: Int = WidgetSandboxVersion.PROTOCOL, profile: Int = WidgetSandboxVersion.PROFILE, sdk: Int = WidgetSandboxVersion.SDK, version: String = "9.9.9") =
        BundleStamp(version, protocol, profile, sdk).line

    @Test
    fun shippedBundlesCarryThisGluesStamp() {
        val resources = SandboxResources(::shipped)
        assertNull(resources.refusal)
        for (name in listOf("sandbox-bundle.js", "validator-bundle.js", "renderer-bundle.js")) {
            assertEquals("$name is not this release's; run pnpm build:android", BundleStamp.GLUE, BundleStamp.parse(String(shipped(name)!!)))
        }
        assertEquals(WidgetSandboxVersion.PROFILE, SdkPolicy.PROFILE_VERSION)
    }

    @Test
    fun parse() {
        assertEquals(BundleStamp("1.2.3-rc.1", 7, 2, 3), BundleStamp.parse("/* widget-sandbox version=1.2.3-rc.1 protocol=7 profile=2 sdk=3 later=x */\n\"use strict\";"))
        assertEquals(BundleStamp.GLUE, BundleStamp.parse(BundleStamp.GLUE.line))
        for (line in listOf(
            "\"use strict\";(()=>{", "/* widget-sandbox */", "/* widget-sandbox version=0.6.0 protocol=1 profile=1 */",
            "/* widget-sandbox version=0.6.0 protocol=x profile=1 sdk=1 */", "/* widget-sandbox version=0.6.0 protocol=-1 profile=1 sdk=1 */",
            "/* widget-sandbox version= protocol=1 profile=1 sdk=1 */", "/* widget-sandbox version=0.6.0  protocol=1 profile=1 sdk=1 */",
            "/* widget-sandbox version=0.6.0 protocol=1 profile=1 sdk=1", "\n/* widget-sandbox version=0.6.0 protocol=1 profile=1 sdk=1 */",
            "/* widget-sandbox version=0.6.0 protocol=1234567 profile=1 sdk=1 */",
        )) assertNull(line, BundleStamp.parse(line))
    }

    @Test
    fun refusalNamesBothSides() {
        assertNull("another release with the same numbers runs", BundleStamp.refusal(stamp(version = "0.0.1"), "x.js"))
        val other = BundleStamp.refusal(stamp(protocol = WidgetSandboxVersion.PROTOCOL + 1), "sandbox-bundle.js")!!
        assertTrue(other, other.startsWith("sandbox-bundle.js is widget-sandbox 9.9.9 (protocol ${WidgetSandboxVersion.PROTOCOL + 1},"))
        assertTrue(other, other.contains("this glue is widget-sandbox ${WidgetSandboxVersion.VERSION} (protocol ${WidgetSandboxVersion.PROTOCOL},"))
        assertNotNull(BundleStamp.refusal(stamp(profile = WidgetSandboxVersion.PROFILE + 1), "x.js"))
        assertNotNull(BundleStamp.refusal(stamp(sdk = WidgetSandboxVersion.SDK + 1), "x.js"))
        val none = BundleStamp.refusal("\"use strict\";", "validator-bundle.js")!!
        assertTrue(none, none.startsWith("validator-bundle.js has no version stamp"))
    }

    @Test
    fun resourcesNameTheFirstBundleOfAnotherRelease() {
        for ((name, line) in listOf("sandbox-bundle.js" to null, "validator-bundle.js" to stamp(profile = 99), "renderer-bundle.js" to stamp(sdk = 99))) {
            val resources = SandboxResources { path ->
                if (path == name) restamped(String(shipped(path)!!), line).toByteArray() else shipped(path)
            }
            assertTrue("$name: ${resources.refusal}", resources.refusal?.startsWith("$name ") == true)
        }
    }
}

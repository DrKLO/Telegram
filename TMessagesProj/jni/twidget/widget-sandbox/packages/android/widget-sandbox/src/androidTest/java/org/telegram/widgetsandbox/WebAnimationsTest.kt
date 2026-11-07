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
class WebAnimationsTest {
    private val resources = SandboxResources.get(InstrumentationRegistry.getInstrumentation().targetContext)

    private val skeleton = "[{\"op\":\"el\",\"id\":2,\"tag\":\"html\"},{\"op\":\"el\",\"id\":3,\"tag\":\"body\"},{\"op\":\"ins\",\"parent\":2,\"id\":3,\"before\":null},{\"op\":\"el\",\"id\":7,\"tag\":\"div\"},{\"op\":\"ins\",\"parent\":3,\"id\":7,\"before\":null}]"

    private fun record(
        aid: Int = 1, target: Int = 7,
        keyframes: String = "[{\"offset\":0,\"easing\":\"linear\",\"composite\":null,\"values\":{\"opacity\":\"0\"}},{\"offset\":1,\"easing\":\"linear\",\"composite\":null,\"values\":{\"opacity\":\"1\"}}]",
        duration: String = "1000", easing: String = "\"ease-in\"", rate: String = "1", start: String = "1000", hold: String = "null",
    ) = "{\"aid\":$aid,\"target\":$target,\"keyframes\":$keyframes,\"timing\":{\"duration\":$duration,\"delay\":0,\"endDelay\":0,\"iterations\":null,\"iterationStart\":0,\"direction\":\"normal\",\"fill\":\"none\",\"easing\":$easing},\"composite\":\"replace\",\"iterationComposite\":\"replace\",\"startTime\":$start,\"holdTime\":$hold,\"playbackRate\":$rate}"

    private fun <T> withValidator(block: (TrustedValidator, MutableList<String>) -> T): T {
        val diags: MutableList<String> = Collections.synchronizedList(ArrayList())
        return TrustedValidator("light", resources) { k, d -> diags.add("$k|$d") }.use { v ->
            v.processOps(skeleton)
            diags.clear()
            block(v, diags)
        }
    }

    @Test
    fun cleanRecordsPassWithSanitizedValues() = withValidator { v, _ ->
        val frames = "[{\"offset\":0,\"easing\":\"linear\",\"composite\":null,\"values\":{\"opacity\":\"0\",\"background-image\":\"url(https://example.com/x.png)\",\"animation-name\":\"x\",\"transition\":\"all 1s\",\"width\":\"expression(alert(1))\",\"behavior\":\"url(x.htc)\",\"color\":\"light-dark(red, blue)\"}},{\"offset\":1,\"easing\":\"steps(2, jump-none)\",\"composite\":\"add\",\"values\":{\"transform\":\"translateX(10px)\"}}]"
        val out = JSONArray(v.validateAnim("[${record(keyframes = frames)}]"))
        assertEquals(1, out.length())
        val values = out.getJSONObject(0).getJSONArray("keyframes").getJSONObject(0).getJSONObject("values")
        assertEquals("0", values.getString("opacity"))
        assertEquals("red", values.getString("color"))
        for (k in listOf("background-image", "animation-name", "transition", "width", "behavior")) assertFalse(k, values.has(k))
    }

    @Test
    fun hostileRecordsAreRejectedWithDiagnostics() {
        val many = "[" + (0 until 65).joinToString(",") { "{\"offset\":${it / 64.0},\"easing\":\"linear\",\"composite\":null,\"values\":{}}" } + "]"
        val hostile = listOf(
            record(keyframes = many),
            record(duration = "1e12"),
            record(duration = "1e999"),
            record(duration = "\"NaN\""),
            record(easing = "\"evil(1)\""),
            record(easing = "\"cubic-bezier(0, 0, 1, url(x))\""),
            record(rate = "1000000"),
            record(start = "null", hold = "null"),
            record(keyframes = "[{\"offset\":0.8,\"easing\":\"linear\",\"composite\":null,\"values\":{}},{\"offset\":0.2,\"easing\":\"linear\",\"composite\":null,\"values\":{}}]"),
            "{\"aid\":-1}",
            "{\"aid\":2,\"target\":7,\"css\":{\"kind\":\"animation\",\"name\":\"a;b\",\"origin\":1},\"startTime\":null,\"holdTime\":0,\"playbackRate\":1,\"cancelled\":false}",
        )
        for (json in hostile) withValidator { v, diags ->
            assertEquals(json, "[]", v.validateAnim("[$json]"))
            assertTrue(json, diags.any { it.startsWith("protocol-violation|") })
        }
        withValidator { v, diags ->
            assertEquals("[]", v.validateAnim("{\"not\":\"a list\"}"))
            assertEquals("[]", v.validateAnim("[${record(target = 99)}]"))
            assertEquals(1, diags.count { it.startsWith("protocol-violation|") })
        }
    }

    /** Strict (every host's default): 64 live script animations, the shim's own cap; further ones are dropped, the widget is not stopped. */
    @Test
    fun liveAnimationsAreCappedAtSixtyFourUnderStrict() = withValidator { v, diags ->
        v.setPolicy("strict")
        val batch = "[" + (1..80).joinToString(",") { record(aid = it) } + "]"
        assertEquals(64, JSONArray(v.validateAnim(batch)).length())
        assertTrue(diags.any { it.startsWith("animation-limit|") })
        assertNull(v.stopped)
    }

    /** Compat (the validator's own default): 1024. */
    @Test
    fun liveAnimationsAreCappedAndRemovalsForwardedOnce() = withValidator { v, diags ->
        val batch = "[" + (1..1100).joinToString(",") { record(aid = it) } + "]"
        assertEquals(1024, JSONArray(v.validateAnim(batch)).length())
        assertTrue(diags.any { it.startsWith("animation-limit|") })
        assertTrue(JSONArray(v.validateAnim("[{\"aid\":5,\"remove\":true}]")).getJSONObject(0).getBoolean("remove"))
        assertEquals("[]", v.validateAnim("[{\"aid\":5,\"remove\":true}]"))
        assertEquals(1, JSONArray(v.validateAnim("[${record(aid = 2000)}]")).length())
    }
}

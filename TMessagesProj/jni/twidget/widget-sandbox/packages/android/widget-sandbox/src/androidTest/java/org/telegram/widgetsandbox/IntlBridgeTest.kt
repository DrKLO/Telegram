package org.telegram.widgetsandbox

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * IntlBridge.kt against V8's answers in the fixture shared with intl.ts, IntlBridge.swift and intl.cpp
 * (tests/fixtures/intl-cases.json, copied to androidTest/assets by `pnpm build:android`).
 */
@RunWith(AndroidJUnit4::class)
class IntlBridgeTest {
    private val cases: List<JSONObject> = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("intl-cases.json").bufferedReader().readText())
        .getJSONArray("cases").let { a -> List(a.length()) { a.getJSONObject(it) } }

    private fun spaces(s: String) = s.replace(' ', ' ').replace(' ', ' ').replace(' ', ' ')

    /** Strings with the three spaces as one; parts as [type, value] lists; options on the expected keys. */
    private fun matches(got: String, expected: Any): Boolean = when (expected) {
        is String -> spaces(got) == spaces(expected)
        is JSONArray -> runCatching {
            val g = JSONArray(got)
            fun list(a: JSONArray) = List(a.length()) { a.getJSONObject(it).let { p -> p.optString("type") to spaces(p.optString("value")) } }
            list(g) == list(expected)
        }.getOrDefault(false)
        is JSONObject -> runCatching {
            val g = JSONObject(got)
            expected.keys().asSequence().all { k -> g.has(k) && g.opt(k).toString() == expected.opt(k).toString() }
        }.getOrDefault(false)
        else -> false
    }

    @Test
    fun everyCaseMatchesV8() {
        assertTrue(cases.size > 100)
        val mismatches = cases.mapNotNull { c ->
            val request = c.getJSONObject("request").toString()
            val got = IntlBridge.format(request)
            if (matches(got, c.get("expected"))) null else "$request: $got != ${c.get("expected")}"
        }
        assertEquals(mismatches.joinToString("\n"), emptyList<String>(), mismatches)
    }

    /** The API 26–27 path of RelativeTimeFormat (no RelativeDateTimeUnit), on the units it words. */
    @Test
    fun relativeTimeBeforeApi28() {
        val mismatches = cases.mapNotNull { c ->
            val r = c.getJSONObject("request")
            val unit = r.optString("unit").removeSuffix("s")
            if (r.optString("kind") != "relative" || unit !in setOf("second", "day", "week", "month", "year")) return@mapNotNull null
            val got = IntlBridge.relative(Locale.forLanguageTag(r.optString("locale")), r.getJSONObject("opts"), r.getDouble("value"), r.optString("unit"), modern = false)
            if (matches(got, c.get("expected"))) null else "$r: $got != ${c.get("expected")}"
        }
        assertEquals(mismatches.joinToString("\n"), emptyList<String>(), mismatches)
    }
}

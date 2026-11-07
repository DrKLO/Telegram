package org.telegram.widgetsandbox

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SdkPolicy.kt against the fixture shared with sdk-policy.ts, SdkPolicy.swift and sdk_policy.cpp
 * (tests/fixtures/sdk-policy.json, copied to androidTest/assets by `pnpm build:android`): every section.
 * Written once for both runs (src/sharedTest): `SdkPolicyTest` in src/test reads the asset from the module
 * directory on the JVM, the one in src/androidTest from the test APK's assets.
 */
abstract class SdkPolicyFixtureTest {
    /** The fixture's text. */
    protected abstract fun fixtureJson(): String

    private val fixture by lazy { JSONObject(fixtureJson()) }

    private fun cases(section: String): List<JSONObject> {
        val a = fixture.getJSONArray(section)
        assertTrue(section, a.length() > 0)
        return List(a.length()) { a.getJSONObject(it) }
    }

    /** The fixture's JSON null as Kotlin null. */
    private fun JSONObject.value(name: String): Any? = opt(name).takeUnless { it == JSONObject.NULL }

    /** `{"repeat": [s, n]}` stands for s repeated n times. */
    private fun expand(v: Any?): Any? = when (v) {
        is JSONArray -> JSONArray().also { out -> for (i in 0 until v.length()) out.put(expand(v.opt(i))) }
        is JSONObject -> {
            val r = v.optJSONArray("repeat")
            if (v.length() == 1 && r != null) r.getString(0).repeat(r.getInt(1))
            else JSONObject().also { out -> for (k in v.keys()) out.put(k, expand(v.opt(k))) }
        }
        else -> v
    }

    private fun strings(o: JSONObject): Map<String, String> = o.keys().asSequence().associateWith { o.getString(it) }

    /** A fixture `expect` of the bridge section: an action, `{kind: "setResizeAnchor", edge}` (since v0.1.3), `request` / `copyText`. */
    private fun action(o: JSONObject?): BridgeCall? = when (o?.getString("kind")) {
        null -> null
        "setResizeAnchor" -> BridgeCall.SetResizeAnchor(o.getString("edge"))
        "request" -> BridgeCall.Request(o.getInt("id"), o.getString("data"), o.optBoolean("wallet", false))
        "copyText" -> BridgeCall.CopyText(o.getString("text"))
        "watchPublicData" -> BridgeCall.WatchPublicData(o.getBoolean("on"))
        "ready" -> WidgetAction.Ready
        "openLink" -> WidgetAction.OpenLink(o.getString("url"))
        "openTelegramLink" -> WidgetAction.OpenTelegramLink(o.getString("url"))
        "haptic" -> WidgetAction.Haptic(o.getString("haptic"), o.value("value") as String?)
        else -> error("unknown kind ${o.getString("kind")}")
    }

    /** A fixture `{chatData, publicData, rejected}` (already expanded). */
    private fun widgetData(o: JSONObject) = CheckedWidgetData(
        o.value("chatData") as String?, o.value("publicData") as String?,
        o.getJSONArray("rejected").let { a -> List(a.length()) { a.getString(it) } },
    )

    @Test
    fun links() {
        for (c in cases("links")) {
            val url = c.value("url")
            assertEquals("${c.getString("method")} ${url.toString().take(80)}", c.value("expect"), SdkPolicy.checkLink(c.getString("method"), url))
        }
    }

    @Test
    fun haptics() {
        for (c in cases("haptics")) {
            val expect = (c.value("expect") as JSONObject?)?.let { WidgetAction.Haptic(it.getString("haptic"), it.value("value") as String?) }
            assertEquals("${c.opt("args")}", expect, SdkPolicy.checkHaptic(c.value("args")))
        }
    }

    /** Deep JSON equality (Android's org.json has no `similar`): the same keys and values, numbers by value. */
    private fun Any?.sameAs(other: Any?): Boolean = when {
        this is JSONObject && other is JSONObject -> length() == other.length() && keys().asSequence().all { other.has(it) && opt(it).sameAs(other.opt(it)) }
        this is JSONArray && other is JSONArray -> length() == other.length() && (0 until length()).all { opt(it).sameAs(other.opt(it)) }
        this is Number && other is Number -> toDouble() == other.toDouble()
        else -> this == other
    }

    /** A checked wallet call as the fixture writes it: `{kind: "wallet", id, call: {method, transaction | payload, data}}`. */
    private fun walletJson(c: BridgeCall.Wallet): JSONObject {
        val call = JSONObject().put("method", c.call.method).put("data", c.call.data)
        when (val w = c.call) {
            is WalletCall.SendTransaction -> call.put("transaction", w.transaction.json())
            is WalletCall.SignData -> call.put("payload", w.payload.json())
        }
        return JSONObject().put("kind", "wallet").put("id", c.id).put("call", call)
    }

    @Test
    fun bridgeCalls() {
        var wallets = 0
        for (c in cases("bridge")) {
            val name = "${c.opt("method")} ${c.opt("args")}".take(120)
            val expect = c.value("expect") as JSONObject?
            val got = SdkPolicy.checkBridgeCall(c.value("method"), c.value("args"))
            if (expect?.getString("kind") == "wallet") {
                assertTrue(name, got is BridgeCall.Wallet && walletJson(got).sameAs(expect))
                wallets++
                continue
            }
            assertEquals(name, action(expect), got)
        }
        assertEquals("the fixture's valid wallet entries", 3, wallets)
    }

    @Test
    fun walletTransactions() {
        for (c in cases("walletTransactions")) {
            val expect = expand(c.value("expect")) as JSONObject?
            val got = SdkPolicy.checkWalletTransaction(expand(c.value("input")))
            val name = c.optString("note", "${c.opt("input")}".take(120))
            if (expect == null) assertNull(name, got) else assertTrue(name, got != null && got.json().sameAs(expect))
        }
    }

    @Test
    fun signDataPayloads() {
        for (c in cases("signDataPayloads")) {
            val expect = expand(c.value("expect")) as JSONObject?
            val got = SdkPolicy.checkSignDataPayload(expand(c.value("input")))
            val name = c.optString("note", "${c.opt("input")}".take(120))
            if (expect == null) assertNull(name, got) else assertTrue(name, got != null && got.json().sameAs(expect))
        }
    }

    @Test
    fun hapticGate() {
        for (c in cases("hapticGate")) {
            val gate = HapticGate()
            val steps = c.getJSONArray("steps")
            for (i in 0 until steps.length()) {
                val s = steps.getJSONObject(i)
                // `{at, touch: "down" | "up"}` is the host's press / release (v0.2.2), else a decide.
                when (s.optString("touch")) {
                    "down" -> gate.press(s.getDouble("at"))
                    "up" -> gate.release(s.getDouble("at"))
                    else -> assertEquals("${c.getString("name")}, step $i", HapticDecision.valueOf(s.getString("expect").uppercase()), gate.decide(s.getDouble("at"), s.getBoolean("visible")))
                }
            }
        }
    }

    @Test
    fun themeParams() {
        for (c in cases("themeParams")) assertEquals("${c.opt("input")}", strings(c.getJSONObject("expect")), SdkPolicy.checkThemeParams(c.value("input")))
    }

    @Test
    fun locales() {
        for (c in cases("locales")) assertEquals("${c.opt("input")}", c.value("expect"), SdkPolicy.checkLocale(c.value("input")))
    }

    @Test
    fun storage() {
        for (c in cases("storage")) {
            val where = c.optString("note").ifEmpty { "${c.opt("input")}".take(120) }
            val got = SdkPolicy.checkStorageCommit(expand(c.value("input")))
            val expect = expand(c.value("expect")) as JSONObject?
            if (expect == null) {
                assertNull(where, got)
                continue
            }
            assertEquals(where, StorageCommit(expect.optJSONObject("local")?.let(::strings), expect.optJSONObject("session")?.let(::strings)), got)
        }
    }

    @Test
    fun utf8() {
        for (c in cases("utf8")) assertEquals(c.getString("s"), c.getInt("bytes"), SdkPolicy.utf8Length(c.getString("s")))
    }

    // ── bot data and requests ──

    @Test
    fun widgetData() {
        for (c in cases("widgetData")) {
            val where = c.optString("note").ifEmpty { "${c.opt("input")}".take(80) }
            assertEquals(where, widgetData(expand(c.getJSONObject("expect")) as JSONObject), SdkPolicy.checkWidgetData(expand(c.value("input")), c.getBoolean("copy")))
        }
    }

    /** `input` and `expect.viewer` have the widget's field names (`WebAppUser`); `is_premium` in `expect` only when true. */
    @Test
    fun viewer() {
        for (c in cases("viewer")) {
            val where = c.optString("note").ifEmpty { "${c.opt("input")}".take(80) }
            val e = expand(c.getJSONObject("expect")) as JSONObject
            val rejected = e.getJSONArray("rejected")
            val v = e.value("viewer") as JSONObject?
            val expect = CheckedViewer(
                v?.let { WidgetViewer(it.getLong("id"), it.value("first_name") as String?, it.value("last_name") as String?, it.value("username") as String?, it.optBoolean("is_premium", false)) },
                List(rejected.length()) { rejected.getString(it) },
            )
            assertEquals(where, expect, SdkPolicy.checkViewer(expand(c.value("input"))))
        }
    }

    @Test
    fun chatType() {
        for (c in cases("chatType")) assertEquals("${c.opt("input")}", c.value("expect"), SdkPolicy.checkChatType(c.value("input")))
    }

    @Test
    fun answers() {
        for (c in cases("answers")) {
            val where = c.optString("note").ifEmpty { "${c.opt("input")}".take(80) }
            val expect = (expand(c.value("expect")) as JSONObject?)?.let { e -> CheckedAnswer(e.value("result") as String?) }
            assertEquals(where, expect, SdkPolicy.checkRequestAnswer(expand(c.value("input"))))
        }
    }

    @Test
    fun requestGate() {
        for (c in cases("requestGate")) {
            val gate = RequestGate()
            val steps = c.getJSONArray("steps")
            for (i in 0 until steps.length()) {
                val s = steps.getJSONObject(i)
                when (s.getString("op")) {
                    "finish" -> { gate.finish(if (s.has("answeredAt")) s.getDouble("answeredAt") else null); continue }
                    "decideWallet" -> {
                        val got = gate.decideWallet(s.getDouble("at"), gesture = s.getBoolean("gesture"), visible = s.getBoolean("visible"), canQuery = s.getBoolean("canQuery"), wallet = s.getBoolean("wallet"))
                        assertEquals("${c.getString("name")}, step $i", s.getString("expect"), got.value)
                        continue
                    }
                    "show" -> {
                        val busy = gate.busy
                        assertEquals("${c.getString("name")}, step $i", s.getBoolean("expect"), gate.show(s.getDouble("at")))
                        assertEquals("${c.getString("name")}, step $i: show changes nothing in flight", busy, gate.busy)
                        continue
                    }
                }
                val got = gate.decide(
                    s.getDouble("at"), gesture = s.getBoolean("gesture"), visible = s.getBoolean("visible"), canQuery = s.getBoolean("canQuery"), backgroundQuery = s.getBoolean("backgroundQuery"),
                    withWallet = s.optBoolean("withWallet", false), wallet = s.optBoolean("wallet", false),
                )
                assertEquals("${c.getString("name")}, step $i", s.getString("expect"), got.value)
            }
        }
    }

    @Test
    fun requestRefusals() {
        for (c in cases("requestRefusals")) {
            val verdict = RequestVerdict.entries.single { it.value == c.getString("verdict") }
            assertEquals(c.getString("verdict"), c.value("expect"), SdkPolicy.requestRefusal(verdict))
        }
    }

    @Test
    fun previewMethods() {
        for (c in cases("previewMethods")) assertEquals("${c.opt("method")}", c.getBoolean("allowed"), SdkPolicy.allowedInPreview(c.value("method")))
        assertEquals(setOf("ready", "setResizeAnchor"), SdkPolicy.PREVIEW_METHODS)
        assertFalse(SdkPolicy.allowedInPreview(null))
        assertFalse(SdkPolicy.allowedInPreview(StringBuilder("ready")))
    }

    /** Kotlin-only inputs: the same checks over Kotlin collections (what WidgetHost passes), and a lone surrogate, which the fixture cannot carry. */
    @Test
    fun kotlinCollectionsAndLoneSurrogates() {
        assertEquals(WidgetAction.Haptic("impact", "soft"), SdkPolicy.checkBridgeCall("haptic", listOf("impact", "soft")))
        assertEquals(BridgeCall.SetResizeAnchor("bottom"), SdkPolicy.checkBridgeCall("setResizeAnchor", listOf("bottom")))
        assertNull(SdkPolicy.checkBridgeCall("setResizeAnchor", listOf(StringBuilder("top"))))
        assertEquals(listOf("top", "bottom", null, null), listOf("top", "bottom", "TOP", "top ").map(SdkPolicy::checkResizeAnchor))
        assertEquals("top", SdkPolicy.DEFAULT_RESIZE_ANCHOR)
        assertEquals(StorageCommit(local = mapOf("a" to "1")), SdkPolicy.checkStorageCommit(mapOf("local" to mapOf("a" to "1"))))
        assertNull(SdkPolicy.checkStorageCommit(mapOf("local" to mapOf("a" to 1))))
        assertEquals(mapOf("bg_color" to "#abcdef"), SdkPolicy.checkThemeParams(mapOf("bg_color" to "#ABCDEF", "text_color" to "red", "x" to "#000000")))
        assertNull(SdkPolicy.checkLink("openLink", "https://example.com/\ud800"))
        assertNull(SdkPolicy.checkLink("openLink", "https://example.com/\udc00x"))
        assertEquals(3, SdkPolicy.utf8Length("\udc00"))
        assertEquals(3, SdkPolicy.utf8Length("\ud800"))
    }

    /** Bot data and requests (sdk-policy.test.ts, "TS only" part): lone surrogates, Kotlin and JSON number types, the failure codes. */
    @Test
    fun botDataAndRequestsBeyondTheFixture() {
        // Lone surrogates are refused everywhere a string leaves the widget or enters it.
        assertNull(SdkPolicy.checkRequestData("a\ud800"))
        assertNull(SdkPolicy.checkRequestData("\udc00b"))
        assertNull(SdkPolicy.checkCopyText("x\ud83d"))
        assertEquals(CheckedWidgetData(null, "ok", listOf("chatData")), SdkPolicy.checkWidgetData(mapOf("chatData" to "\ud800", "publicData" to "ok"), false))
        assertNull(SdkPolicy.checkRequestAnswer(mapOf("result" to "\udfff")))
        assertEquals(CheckedWidgetData("a\tb", null, listOf("publicData")), SdkPolicy.checkWidgetData(mapOf("chatData" to "a\tb", "publicData" to "\u0000"), false))
        assertNull("a NUL is not text", SdkPolicy.checkWidgetDataValue("x\u0000"))
        assertNull(SdkPolicy.checkRequestAnswer(mapOf("result" to "\u0000")))
        assertEquals("😀", SdkPolicy.checkRequestData("😀"))
        // Wrong types: no data, no viewer, no answer.
        assertNull(SdkPolicy.checkRequestData(42))
        assertNull(SdkPolicy.checkCopyText(StringBuilder("x")))
        assertEquals(CheckedWidgetData(null, null, listOf("chatData", "publicData")), SdkPolicy.checkWidgetData(mapOf("chatData" to 5, "publicData" to listOf("x")), false))
        assertEquals(CheckedWidgetData(null, null), SdkPolicy.checkWidgetData("chat", false))
        assertEquals(CheckedWidgetData(null, null), SdkPolicy.checkWidgetData(null, true))
        assertEquals(CheckedViewer(null), SdkPolicy.checkViewer(mapOf("id" to "1")))
        assertEquals(CheckedViewer(null), SdkPolicy.checkViewer(mapOf("id" to 1.5)))
        assertEquals(CheckedViewer(WidgetViewer(7, username = "artem")), SdkPolicy.checkViewer(mapOf("id" to 7.0, "username" to "artem")))
        assertEquals(CheckedViewer(WidgetViewer(SdkPolicy.MAX_VIEWER_ID)), SdkPolicy.checkViewer(mapOf("id" to SdkPolicy.MAX_VIEWER_ID)))
        assertEquals(CheckedViewer(null), SdkPolicy.checkViewer(mapOf("id" to Long.MAX_VALUE)))
        // What WidgetHost passes: every field, nulls for none, isPremium a Boolean.
        assertEquals(CheckedViewer(WidgetViewer(5)), SdkPolicy.checkViewer(mapOf("id" to 5L, "first_name" to null, "last_name" to null, "username" to null, "is_premium" to false)))
        assertEquals(
            "a lone surrogate is no name; is_premium must be a boolean",
            CheckedViewer(WidgetViewer(5, lastName = "Lee"), listOf("first_name", "is_premium")),
            SdkPolicy.checkViewer(mapOf("id" to 5, "first_name" to "A\ud800", "last_name" to "Lee", "is_premium" to "yes")),
        )
        assertEquals(CheckedViewer(WidgetViewer(5), listOf("username")), SdkPolicy.checkViewer(mapOf("id" to 5, "username" to 12345)))
        assertEquals(listOf("first_name", "last_name", "username", "is_premium"), SdkPolicy.VIEWER_FIELD_RULES.keys.toList())
        assertNull(SdkPolicy.checkRequestAnswer(mapOf("result" to 5)))
        assertEquals(CheckedAnswer(null), SdkPolicy.checkRequestAnswer(null))
        assertEquals(CheckedAnswer(null), SdkPolicy.checkRequestAnswer("not an object"))
        assertEquals(CheckedAnswer(null), SdkPolicy.checkRequestAnswer(JSONObject("{\"result\":null}")))
        assertEquals("an answer is only its result: data fields are no part of it", CheckedAnswer("r"), SdkPolicy.checkRequestAnswer(mapOf("result" to "r", "chatData" to "c", "publicData" to 5)))
        // Ids as every JSON number type a parser may give; JSON from the shim carries plain integers.
        assertEquals(BridgeCall.Request(3, "x"), SdkPolicy.checkBridgeCall("request", listOf(3L, "x")))
        assertEquals(BridgeCall.Request(3, "x"), SdkPolicy.checkBridgeCall("request", listOf(3.0, "x")))
        assertEquals(BridgeCall.Request(3, "x"), SdkPolicy.checkBridgeCall("request", listOf(java.math.BigDecimal("3"), "x")))
        assertNull(SdkPolicy.checkBridgeCall("request", listOf(java.math.BigDecimal("1.5"), "x")))
        assertNull(SdkPolicy.checkBridgeCall("request", listOf(Double.NaN, "x")))
        assertNull(SdkPolicy.checkBridgeCall("request", listOf(true, "x")))
        assertEquals(BridgeCall.Request(9, "vote"), SdkPolicy.checkBridgeCall("request", JSONArray("[9,\"vote\"]")))
        assertEquals(BridgeCall.CopyText("hi"), SdkPolicy.checkBridgeCall("copyText", JSONArray("[\"hi\"]")))
        assertTrue("copyText" in SdkPolicy.PRIVILEGED_METHODS)
        assertEquals(BridgeCall.WatchPublicData(false), SdkPolicy.checkBridgeCall("watchPublicData", listOf(false)))
        assertNull("a Boolean only", SdkPolicy.checkBridgeCall("watchPublicData", listOf("false")))
        assertFalse("watchPublicData needs no tap", "watchPublicData" in SdkPolicy.PRIVILEGED_METHODS)
        assertFalse("nor runs in a preview", SdkPolicy.allowedInPreview("watchPublicData"))
        assertFalse("request has its own gate", "request" in SdkPolicy.PRIVILEGED_METHODS)
        // A client's failure becomes TIMEOUT, RATE_LIMITED or FAILED.
        assertEquals("TIMEOUT", SdkPolicy.requestFailure(WidgetRequestException(WidgetRequestFailure.TIMEOUT)))
        assertEquals("RATE_LIMITED", SdkPolicy.requestFailure(WidgetRequestException(WidgetRequestFailure.RATE_LIMITED)))
        assertEquals("FAILED", SdkPolicy.requestFailure(WidgetRequestException(WidgetRequestFailure.FAILED)))
        assertEquals("TIMEOUT", SdkPolicy.requestFailure(WidgetRequestFailure.TIMEOUT))
        assertEquals("FAILED", SdkPolicy.requestFailure(IllegalStateException("offline")))
        assertEquals("FAILED", SdkPolicy.requestFailure("NOT_ALLOWED"))
        assertEquals(listOf("NOT_ALLOWED", "BUSY", "RATE_LIMITED", "TIMEOUT", "FAILED", "INVALID_ANSWER", "REJECTED", "BAD_REQUEST"), SdkPolicy.REQUEST_ERRORS)
        // A wallet call keeps the wallet's own codes; a request turns them into FAILED.
        assertEquals("REJECTED", SdkPolicy.walletFailure(WidgetRequestException(WidgetRequestFailure.REJECTED)))
        assertEquals("BAD_REQUEST", SdkPolicy.walletFailure(WidgetRequestFailure.BAD_REQUEST))
        assertEquals("TIMEOUT", SdkPolicy.walletFailure(WidgetRequestException(WidgetRequestFailure.TIMEOUT)))
        assertEquals("FAILED", SdkPolicy.walletFailure(IllegalStateException("offline")))
        assertEquals("FAILED", SdkPolicy.requestFailure(WidgetRequestException(WidgetRequestFailure.REJECTED)))
        // Kotlin collections reach the same checks (what a client builds by hand).
        assertEquals(BridgeCall.Request(1, "x", true), SdkPolicy.checkBridgeCall("request", listOf(1, "x", mapOf("wallet" to true))))
        assertEquals(
            WalletTransaction(validUntil = 4294967295L, messages = listOf(WalletMessage("EQ" + "a".repeat(46), "1"))),
            SdkPolicy.checkWalletTransaction(mapOf("valid_until" to 4294967295L, "messages" to listOf(mapOf("address" to "EQ" + "a".repeat(46), "amount" to "1")))),
        )
        assertNull(SdkPolicy.checkSignDataPayload(mapOf("type" to "text", "text" to "a\ud800")))
        assertEquals(listOf("private", "group", "supergroup", "channel"), SdkPolicy.CHAT_TYPES)
        assertFalse(RequestGate().busy)
        assertTrue(RequestGate().apply { decide(0.0, gesture = true, visible = true, canQuery = true, backgroundQuery = false) }.busy)
        assertTrue("show changes nothing in flight", RequestGate().apply { decide(0.0, gesture = true, visible = true, canQuery = true, backgroundQuery = false); show(1.0) }.busy)
        assertTrue("a held request stays in flight as it goes out", RequestGate().apply { decide(0.0, gesture = false, visible = false, canQuery = true, backgroundQuery = true); show(1.0) }.busy)
    }
}

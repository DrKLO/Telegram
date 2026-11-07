package org.telegram.widgetsandbox

import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * SDK v1 (docs/sdk-v1.md §2–§4) through the real sandbox bundle in QuickJS: the glue's checks in
 * SandboxEngine (links and user activation, the haptic limit, storage preload and commits, theme
 * params and locale in the boot config), then WidgetHost end to end on the WebView renderer (a real
 * tap → onAction, onGesture and the deprecated onGestureClaim, onStorageCommit, the theme variables on the renderer's root).
 * The bot's data and requests (§2.13–§2.16, the twin of sdk.test.ts "SDK v1: the bot's data, where the widget is, requests, copyText"): the bot's data and the server's flags at boot, requests
 * through the request gate and their answers, `setData` and its two events, `copyText`; in the engine, then through WidgetHost.
 * `bridge-rejected` is covered by SdkPolicyTest: the shim only sends calls it has checked itself.
 */
@RunWith(AndroidJUnit4::class)
class SdkTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val resources = SandboxResources.get(instrumentation.targetContext)

    private fun run(html: String, d: RecordingDelegate = RecordingDelegate(), configure: (SandboxConfig) -> Unit = {}): Pair<RecordingDelegate, SandboxEngine> {
        val config = SandboxConfig(innerWidth = 400.0, innerHeight = 300.0, devicePixelRatio = 2.0, theme = "light", cpuLimitMs = 2000.0)
        configure(config)
        val engine = SandboxEngine(html, config, resources)
        engine.delegate = d
        engine.start()
        assertTrue("engine did not load in time", d.loaded.await(20, TimeUnit.SECONDS))
        engine.thread.sync {}
        assertNull(d.died)
        return d to engine
    }

    /** The node id of the first `<tag>` in the sandbox's ops. */
    private fun idOf(d: RecordingDelegate, tag: String): Int {
        for (batch in d.ops.toList()) {
            val ops = try { JSONArray(batch) } catch (e: Exception) { continue }
            for (i in 0 until ops.length()) {
                val op = ops.optJSONObject(i) ?: continue
                if (op.optString("op") == "el" && op.optString("tag") == tag) return op.getInt("id")
            }
        }
        throw AssertionError("no <$tag> in the ops")
    }

    private fun click(id: Int) = "{\"type\":\"click\",\"targetId\":$id,\"detail\":1}"
    private fun RecordingDelegate.count(kind: String) = diags.count { it.first == kind }
    private fun RecordingDelegate.logged() = logs.filter { it.first == "log" }.map { it.second }

    @Test
    fun aTapOpensOneLinkNormalised() {
        val (d, engine) = run(
            "<button>go</button><script>document.querySelector('button').addEventListener('click', function () {" +
                "TelegramWidget.openLink('HTTPS://Example.com/поиск'); TelegramWidget.openLink('https://example.com/second'); });</script>",
        )
        val button = idOf(d, "button")
        engine.activationEvent(click(button), 1)
        engine.thread.sync {}
        assertEquals(listOf(WidgetAction.OpenLink("https://example.com/%D0%BF%D0%BE%D0%B8%D1%81%D0%BA")), d.actions.toList())
        assertEquals("the second call of the tap: ${d.diags}", 1, d.count("activation-blocked"))
        // A click that is no gesture (untrusted, or the same press again) opens nothing; the next press opens one link.
        engine.event(click(button))
        engine.activationEvent(click(button), 1)
        engine.activationEvent(click(button), 2)
        engine.thread.sync {}
        assertEquals(2, d.actions.size)
        assertEquals(6, d.count("activation-blocked"))
        assertEquals(listOf("openLink", "openLink"), d.bridge.map { it.first })
        assertEquals(0, d.count("bridge-rejected"))
        engine.terminate()
    }

    @Test
    fun hapticsAreLimitedNotGated() {
        val (d, engine) = run("<script>for (var i = 0; i < 7; i++) TelegramWidget.HapticFeedback.impactOccurred('medium');</script>")
        assertEquals(List(5) { WidgetAction.Haptic("impact", "medium") }, d.actions.toList())
        assertEquals("${d.diags}", 1, d.count("haptic-limit"))
        assertEquals(0, d.count("activation-blocked"))
        engine.terminate()

        val (hidden, engine2) = run("<script>TelegramWidget.HapticFeedback.selectionChanged();</script>") { it.visible = false }
        assertEquals(emptyList<WidgetAction>(), hidden.actions.toList())
        assertEquals(1, hidden.count("haptic-limit"))
        engine2.terminate()
    }

    /**
     * v0.2.2: haptics need a touch. A timer that calls one every ~10 ms gets one series: at most 16 played in its first
     * second (burst 5, 10 per second), none after; a tap (a press from the renderer, then its click) plays one.
     */
    @Test
    fun hapticsNeedATouch() {
        val html = "<button>b</button><script>var n = 0;" +
            "var t = setInterval(function () { TelegramWidget.HapticFeedback.notificationOccurred('error'); if (++n === 200) { clearInterval(t); console.log('timer=' + n); } }, 10);" +
            "document.querySelector('button').onclick = function () { TelegramWidget.HapticFeedback.impactOccurred('heavy'); console.log('tapped'); };</script>"
        val times = Collections.synchronizedList(ArrayList<Long>())
        val d = object : RecordingDelegate() {
            override fun onAction(engine: SandboxEngine, action: WidgetAction) {
                if (action is WidgetAction.Haptic) times.add(System.nanoTime())
                super.onAction(engine, action)
            }
        }
        val (_, engine) = run(html, d)
        assertTrue("${d.logs}", waitFor(15) { d.logged().any { it.startsWith("timer=") } })
        engine.thread.sync {}
        val played = times.toList()
        assertTrue("${played.size} played", played.size in 5..16)
        val span = (played.last() - played.first()) / 1_000_000
        assertTrue("all in the series' first second: $span ms", span <= 1100)
        assertEquals("${d.diags}", "haptics over 5 per burst or 10 per second, or outside a touch after a 1 s series, are dropped", d.rejected("haptic-limit").first())
        val timer = played.size
        // A click that is no press plays nothing now (the series is over, no touch, under 10 s since it began)…
        val button = idOf(d, "button")
        engine.event(click(button))
        engine.thread.sync {}
        assertEquals(timer, d.actions.count { it is WidgetAction.Haptic })
        // …a tap does: the renderer's press, the click, the release.
        engine.press()
        engine.activationEvent(click(button), 1)
        engine.release()
        engine.thread.sync {}
        assertEquals(WidgetAction.Haptic("impact", "heavy"), d.actions.last())
        assertEquals(timer + 1, d.actions.count { it is WidgetAction.Haptic })
        engine.terminate()
    }

    @Test
    fun storagePreloadAndCommits() {
        val html = "<button>b</button><script>" +
            "console.log('seen=' + localStorage.getItem('count') + '/' + sessionStorage.getItem('s') + '/' + localStorage.length);" +
            "localStorage.setItem('count', String(Number(localStorage.getItem('count')) + 1)); localStorage.extra = 7;" +
            "document.querySelector('button').onclick = function () { sessionStorage.setItem('s', 'tapped'); localStorage.setItem('count', localStorage.count); };</script>"
        val (d, engine) = run(html) { it.storage = WidgetStorage(local = mapOf("count" to "41"), session = mapOf("s" to "kept")) }
        assertTrue("${d.logs}", d.logged().contains("seen=41/kept/1"))
        engine.event(click(idOf(d, "button")))
        engine.thread.sync {}
        assertEquals(
            "one commit per task, the changed areas in full; an unchanged value is no change",
            listOf("local" to mapOf("count" to "42", "extra" to "7"), "session" to mapOf("s" to "tapped")),
            d.storage.toList(),
        )
        assertEquals(0, d.count("storage-rejected"))
        engine.terminate()
    }

    @Test
    fun aPreloadOverTheQuotaIsDroppedWhole() {
        val (d, engine) = run("<script>console.log('n=' + (localStorage.length + sessionStorage.length));</script>") {
            it.storage = WidgetStorage(local = mapOf("a" to "x".repeat(20000)), session = mapOf("b" to "1"))
        }
        assertEquals("${d.diags}", 1, d.count("storage-rejected"))
        assertTrue("${d.logs}", d.logged().contains("n=0"))
        engine.terminate()
    }

    @Test
    fun themeParamsAndLocaleReachTheWidget() {
        val html = "<script>TelegramWidget.onEvent('themeChanged', function () { console.log('changed:' + TelegramWidget.colorScheme + ':' + TelegramWidget.themeParams.bg_color + ':' + TelegramWidget.themeParams.text_color); });" +
            "var p = TelegramWidget.themeParams; console.log([p.bg_color, p.text_color, 'made_up' in p, navigator.language, TelegramWidget.profile].join());</script>"
        val (d, engine) = run(html) {
            it.themeParams = mapOf("bg_color" to "#ABCDEF", "text_color" to "red", "made_up" to "#123456")
            it.locale = "pt-BR"
        }
        assertTrue("${d.logs}", d.logged().contains("#abcdef,#000000,false,pt-BR,1"))
        engine.theme("dark", mapOf("bg_color" to "#101010"))
        engine.thread.sync {}
        assertTrue("${d.logs}", d.logged().contains("changed:dark:#101010:#ffffff"))
        assertFalse("${d.diags}", d.diags.any { it.first == "profile-mismatch" })
        engine.terminate()

        val (d2, engine2) = run("<script>console.log('lang=' + navigator.language);</script>") { it.locale = "ru_RU" }
        assertTrue("${d2.logs}", d2.logged().contains("lang=en-US"))
        engine2.terminate()
    }

    /**
     * The client's time over the widget (docs/sdk-v1.md §2.17): the boot value from the first line (checked by the shim:
     * out of range is 0), then one contentSafeAreaChanged with a fresh frozen object per change, nothing for the same
     * value, and a value set before boot replaces the config's.
     */
    @Test
    fun contentSafeAreaReachesTheWidget() {
        val html = "<script>var first = TelegramWidget.contentSafeAreaInset; console.log('first=' + JSON.stringify(first));" +
            "TelegramWidget.onEvent('contentSafeAreaChanged', function () { var a = TelegramWidget.contentSafeAreaInset;" +
            " console.log('changed=' + [a.bottom, a.cornerLeft, a.cornerRight, Object.isFrozen(a), a !== first, this === TelegramWidget].join(':')); });</script>"
        val (d, engine) = run(html) { it.contentSafeArea = WidgetContentSafeArea(top = 99.0, bottom = 24.0, cornerRight = 72.0) }
        assertTrue("${d.logs}", d.logged().contains("first={\"top\":0,\"right\":0,\"bottom\":24,\"left\":0,\"cornerLeft\":0,\"cornerRight\":72}"))
        val rtl = WidgetContentSafeArea(bottom = 26.5, cornerLeft = 80.0)
        engine.contentSafeArea(rtl)
        engine.contentSafeArea(rtl)
        engine.contentSafeArea(WidgetContentSafeArea(bottom = Double.NaN))
        engine.thread.sync {}
        assertEquals(listOf("changed=26.5:80:0:true:true:true", "changed=0:0:0:true:true:true"), d.logged().filter { it.startsWith("changed=") })
        engine.terminate()
    }

    /** `setResizeAnchor` (v0.1.3): the shim sends only changes, the glue takes them with no gesture and hidden, never as an action. */
    @Test
    fun resizeAnchorIsNoAction() {
        val html = "<script>TelegramWidget.setResizeAnchor('top'); TelegramWidget.setResizeAnchor('bottom');" +
            "for (var i = 0; i < 1000; i++) TelegramWidget.setResizeAnchor('bottom');" +
            "TelegramWidget.setResizeAnchor('top'); TelegramWidget.setResizeAnchor('bottom');" +
            "[undefined, 'left', 'Bottom', ' top', 1].forEach(function (v) { try { TelegramWidget.setResizeAnchor(v); } catch (e) { console.log(e.name); } });</script>"
        val (d, engine) = run(html) { it.visible = false }
        assertEquals(listOf("bottom", "top", "bottom"), d.resizeAnchors.toList())
        assertEquals(emptyList<WidgetAction>(), d.actions.toList())
        assertEquals(emptyList<Pair<String, String>>(), d.bridge.toList())
        assertEquals(List(5) { "TypeError" }, d.logged())
        assertEquals("${d.diags}", 0, d.count("activation-blocked") + d.count("bridge-rejected"))
        engine.terminate()
    }

    // ── The bot's data, the message's flags, requests, copyText (in the engine) ──

    private val show = "<script>console.log(JSON.stringify([TelegramWidget.chatData, TelegramWidget.publicData, TelegramWidget.isForward, TelegramWidget.chatType, TelegramWidget.viewer]));</script>"

    /** A button whose tap asks twice (the shim forwards both, the glue's gate refuses the second as BUSY); every outcome goes into one log line. */
    private val requester = "<button>go</button><script>var log = [];" +
        "function put(s) { log.push(s); console.log('log=' + log.join(' ')); }" +
        "function ask(d) { TelegramWidget.request(d).then(function (a) { put('ok:' + d + ':' + JSON.stringify(a) + ':' + Object.isFrozen(a)); }, function (e) { put('err:' + d + ':' + e.name + ':' + e.code); }); }" +
        "document.querySelector('button').addEventListener('click', function () { ask('tap'); ask('tap2'); });</script>"

    /** The server's flags of an original in a group: requests on a tap only. */
    private val tapOnly = WidgetContext(canQuery = true)

    /** The widget's latest `log=` line. */
    private fun RecordingDelegate.log() = logged().lastOrNull { it.startsWith("log=") }?.removePrefix("log=") ?: ""
    private fun RecordingDelegate.outcomes() = log().split(" ").filter { it.startsWith("ok:tap:") || it.startsWith("err:tap:") }
    private fun RecordingDelegate.rejected(kind: String) = diags.filter { it.first == kind }.map { it.second }

    @Test
    fun botDataAndContextAtBoot() {
        val (d, engine) = run(show) {
            it.data = WidgetData(chatData = "{\"turn\":1}", publicData = "score 3:1")
            it.context = WidgetContext(canQuery = true, chatType = "private", viewer = WidgetViewer(SdkPolicy.MAX_VIEWER_ID))
        }
        assertEquals(listOf("""["{\"turn\":1}","score 3:1",false,"private",{"id":9007199254740991}]"""), d.logged())
        engine.terminate()

        val copy = { flags: WidgetContext ->
            val (c, e) = run(show) { it.data = WidgetData(chatData = "secret", publicData = "score 3:1"); it.context = flags }
            e.terminate()
            assertEquals(0, c.count("data-rejected"))
            c.logged()
        }
        assertEquals("a plain copy knows only publicData", listOf("""[null,"score 3:1",true,null,null]"""), copy(WidgetContext(isCopy = true, chatType = "private", viewer = WidgetViewer(42))))
        assertEquals("a live copy (canQuery) knows who looks, never where it was forwarded, never chatData", listOf("""[null,"score 3:1",true,null,{"id":42}]"""), copy(WidgetContext(isCopy = true, canQuery = true, chatType = "supergroup", viewer = WidgetViewer(42))))
        val (quiet, engine2) = run(show) { it.data = WidgetData(chatData = "mine", publicData = "p"); it.context = WidgetContext(chatType = "private", viewer = WidgetViewer(42)) }
        assertEquals("an original sent without allow_queries knows no viewer", listOf("""["mine","p",false,"private",null]"""), quiet.logged())
        engine2.terminate()

        val (none, engine3) = run(show) {
            it.data = WidgetData(chatData = "a\u0000b", publicData = "x".repeat(1025))
            it.context = WidgetContext(chatType = "sender", viewer = WidgetViewer(0))
        }
        assertEquals(listOf("[null,null,false,null,null]"), none.logged())
        assertEquals("a NUL is not text", listOf("chatData and publicData: not text of at most 1024 bytes (UTF-8, no NUL); the widget sees null"), none.rejected("data-rejected"))
        engine3.terminate()
    }

    /**
     * The viewer is Mini Apps' `WebAppUser` (`{ id, first_name?, last_name?, username?, is_premium? }`, frozen), given only
     * with canQuery; a field that breaks its rule is left out with one `data-rejected` each.
     */
    @Test
    fun theViewerIsAWebAppUser() {
        val html = "<script>console.log('viewer=' + JSON.stringify(TelegramWidget.viewer) + ':' + Object.isFrozen(TelegramWidget.viewer));</script>"
        fun boot(flags: WidgetContext): RecordingDelegate {
            val (d, engine) = run(html) { it.context = flags }
            engine.terminate()
            return d
        }
        val private = boot(WidgetContext(canQuery = true, backgroundQuery = true, chatType = "private", viewer = WidgetViewer(42, "Ann", "Lee", "ann_lee", isPremium = true)))
        assertEquals(listOf("""viewer={"id":42,"first_name":"Ann","last_name":"Lee","username":"ann_lee","is_premium":true}:true"""), private.logged())
        assertEquals(emptyList<String>(), private.rejected("data-rejected"))

        val group = boot(WidgetContext(canQuery = true, chatType = "group", viewer = WidgetViewer(42, firstName = "Ann", username = "a-b", isPremium = false)))
        assertEquals("is_premium only when true; the broken username left out", listOf("""viewer={"id":42,"first_name":"Ann"}:true"""), group.logged())
        assertEquals(listOf("viewer.username: not 5–32 characters of A–Z, a–z, 0–9 and _; the widget does not see it"), group.rejected("data-rejected"))

        val noQuery = boot(WidgetContext(chatType = "private", viewer = WidgetViewer(42, "Ann", "Lee", "ann_lee", isPremium = true)))
        assertEquals("no canQuery, no viewer", listOf("viewer=null:true"), noQuery.logged())
        assertEquals("a viewer the widget does not get is not checked", emptyList<String>(), noQuery.rejected("data-rejected"))

        val names = boot(WidgetContext(canQuery = true, viewer = WidgetViewer(7, firstName = "", lastName = "x".repeat(65))))
        assertEquals(listOf("""viewer={"id":7}:true"""), names.logged())
        assertEquals(List(2) { "viewer.${listOf("first_name", "last_name")[it]}: not text of 1–64 UTF-16 code units; the widget does not see it" }, names.rejected("data-rejected"))
    }

    @Test
    fun aTapSendsOneRequestAndItsAnswerResolves() {
        val d = RecordingDelegate()
        d.answer = { _, respond -> respond(Result.success(WidgetRequestAnswer("thanks"))) }
        val (_, engine) = run(requester, d) { it.context = tapOnly }
        val button = idOf(d, "button")
        engine.activationEvent(click(button), 1)
        assertTrue(d.log(), waitFor { d.log().contains("ok:") })
        assertTrue(d.log(), waitFor { d.log().contains("err:tap2:") })
        // The answer was posted to the engine thread inside onRequest, before the gate's refusal of the second call.
        assertEquals("the second call of the tap is BUSY in the glue's gate", "ok:tap:{\"result\":\"thanks\"}:true err:tap2:WidgetRequestError:BUSY", d.log())
        assertEquals("onRequest gets only the data", listOf(WidgetRequest("tap")), d.requests.toList())
        // After the answer the next tap can request again.
        engine.activationEvent(click(button), 2)
        assertTrue(d.log(), waitFor { d.outcomes().size == 2 })
        assertEquals(2, d.requests.size)
        // Without a tap (a click that is no gesture), an original in a group gets NOT_ALLOWED and the client is not asked.
        engine.event(click(button))
        assertTrue(d.log(), waitFor { d.outcomes().size == 3 })
        assertEquals("err:tap:WidgetRequestError:NOT_ALLOWED", d.outcomes()[2])
        assertEquals(2, d.requests.size)
        assertTrue(d.log(), waitFor { d.log().split(" ").size == 6 })
        // The second call of the untapped click is forwarded too: refused for itself, not BUSY behind a refused one.
        assertEquals("err:tap2:WidgetRequestError:NOT_ALLOWED", d.log().split(" ").last())
        val busy = "request refused (busy): a request is already in flight"
        val noTap = "request refused (no-activation): it needs a tap on the visible widget, one request per tap; this message allows no background requests (no background_query)"
        assertEquals(listOf(busy, busy, noTap, noTap), d.rejected("request-blocked"))
        assertEquals("a request is no action", emptyList<WidgetAction>(), d.actions.toList())
        assertEquals(emptyList<Pair<String, String>>(), d.bridge.toList())
        engine.terminate()
    }

    @Test
    fun requestsWithoutATap() {
        val html = "<script>var log = []; function put(s) { log.push(s); console.log('log=' + log.join(' ')); }" +
            "function ask() { TelegramWidget.request('bg').then(function () { put('ok'); }, function (e) { put(e.code + ':' + /tap/.test(e.message)); }); }" +
            "ask(); setTimeout(ask, 300);</script>"
        fun client() = RecordingDelegate().apply { answer = { _, respond -> respond(Result.success(WidgetRequestAnswer())) } }

        val (d, engine) = run(html, client()) { it.context = tapOnly }
        assertTrue(d.log(), waitFor { d.log().split(" ").size == 2 })
        assertEquals("canQuery without backgroundQuery: a tap's only", "NOT_ALLOWED:true NOT_ALLOWED:true", d.log())
        assertEquals(emptyList<WidgetRequest>(), d.requests.toList())
        assertEquals(2, d.rejected("request-blocked").count { it.startsWith("request refused (no-activation)") })
        engine.terminate()

        val (bg, engine2) = run(html, client()) { it.context = WidgetContext(canQuery = true, backgroundQuery = true) }
        assertTrue(bg.log(), waitFor { bg.log().split(" ").size == 2 })
        assertEquals("one background request per 5 s", "ok RATE_LIMITED:false", bg.log())
        assertEquals("delivered without a tap: the gate's background verdict", listOf(WidgetRequest("bg")), bg.requests.toList())
        assertTrue("${bg.diags}", bg.rejected("request-blocked").any { it.startsWith("request refused (rate-limited)") })
        engine2.terminate()

        // Off screen the boot request waits for the widget to appear (held, in flight): the timer's one meanwhile is BUSY.
        val (hidden, engine3) = run(html, client()) { it.context = WidgetContext(canQuery = true, backgroundQuery = true); it.visible = false }
        assertTrue(hidden.log(), waitFor { hidden.log().isNotEmpty() })
        assertEquals("BUSY:false", hidden.log())
        assertEquals(emptyList<WidgetRequest>(), hidden.requests.toList())
        engine3.setVisible(true)
        assertTrue(hidden.log(), waitFor { hidden.log().split(" ").size == 2 })
        assertEquals("BUSY:false ok", hidden.log())
        assertEquals(listOf(WidgetRequest("bg")), hidden.requests.toList())
        assertEquals(listOf("request refused (busy): a request is already in flight"), hidden.rejected("request-blocked"))
        engine3.terminate()

        val (compat, engine4) = run(html, client()) { it.policy = "compat"; it.context = tapOnly }
        assertTrue(compat.log(), waitFor { compat.log().split(" ").size == 2 })
        assertEquals("compat: every request is a press's", listOf(WidgetRequest("bg"), WidgetRequest("bg")), compat.requests.toList())
        engine4.terminate()
    }

    /** The server's flags: no flags, or a plain copy, never asks (the shim refuses at once); a live copy asks on a tap. */
    @Test
    fun theServerFlagsDecide() {
        fun tapped(flags: WidgetContext): RecordingDelegate {
            val d = RecordingDelegate().apply { answer = { _, respond -> respond(Result.success(WidgetRequestAnswer("x"))) } }
            val (_, engine) = run(requester + show, d) { it.data = WidgetData("c", "p"); it.context = flags.copy(chatType = "supergroup", viewer = WidgetViewer(5)) }
            engine.activationEvent(click(idOf(d, "button")), 1)
            assertTrue(d.log(), waitFor { d.outcomes().isNotEmpty() })
            engine.thread.sync {}
            engine.terminate()
            return d
        }
        for ((name, flags) in listOf("no flags" to WidgetContext(), "a plain copy" to WidgetContext(isCopy = true), "background without canQuery" to WidgetContext(backgroundQuery = true))) {
            val d = tapped(flags)
            assertEquals(name, "err:tap:WidgetRequestError:NOT_ALLOWED err:tap2:WidgetRequestError:NOT_ALLOWED", d.log())
            assertEquals(name, emptyList<WidgetRequest>(), d.requests.toList())
        }
        assertEquals("a plain copy sees no chat type or viewer", """[null,"p",true,null,null]""", tapped(WidgetContext(isCopy = true)).logged().first { it.startsWith("[") })
        val live = tapped(WidgetContext(isCopy = true, canQuery = true))
        assertEquals("a live copy asks on a tap", "ok:tap:{\"result\":\"x\"}:true err:tap2:WidgetRequestError:BUSY", live.log())
        assertEquals(listOf(WidgetRequest("tap")), live.requests.toList())
        assertEquals("a live copy sees the viewer, no chat type, no chatData", """[null,"p",true,null,{"id":5}]""", live.logged().first { it.startsWith("[") })
    }

    @Test
    fun aClientWithoutRequestsRefusesAtDelivery() {
        // No client side: the gate lets the tap's request through, the delegate's default refuses it at delivery (`unsupported`).
        val (d, engine) = run(requester) { it.context = tapOnly }
        val button = idOf(d, "button")
        engine.activationEvent(click(button), 1)
        assertTrue(d.log(), waitFor { d.outcomes().isNotEmpty() })
        assertTrue(d.log(), waitFor { d.log().split(" ").size == 2 })
        assertEquals("err:tap2:WidgetRequestError:BUSY err:tap:WidgetRequestError:NOT_ALLOWED", d.log())
        assertEquals(listOf("request refused (busy): a request is already in flight", "request refused: the client handles no requests (onRequest)"), d.rejected("request-blocked"))
        assertEquals(emptyList<WidgetRequest>(), d.requests.toList())
        // The refusal freed the gate, and the client is asked at delivery: one that starts answering gets the next tap's request.
        d.answer = { _, respond -> respond(Result.success(WidgetRequestAnswer("now"))) }
        engine.activationEvent(click(button), 2)
        assertTrue(d.log(), waitFor { d.outcomes().size == 2 })
        assertEquals("ok:tap:{\"result\":\"now\"}:true", d.outcomes()[1])
        assertEquals(listOf(WidgetRequest("tap")), d.requests.toList())
        engine.terminate()

        // The reason travels: the widget's error says the client delivers no requests.
        val html = "<button>u</button><script>document.querySelector('button').onclick = function () { TelegramWidget.request('u').catch(function (e) { console.log('msg=' + e.code + ':' + /does not deliver/.test(e.message)); }); };</script>"
        val (u, engine2) = run(html) { it.context = tapOnly }
        engine2.activationEvent(click(idOf(u, "button")), 1)
        assertTrue("${u.logs}", waitFor { u.logged().any { it.startsWith("msg=") } })
        assertEquals(listOf("msg=NOT_ALLOWED:true"), u.logged().filter { it.startsWith("msg=") })
        engine2.terminate()
    }

    @Test
    fun aRequestTakesTheTap() {
        val html = "<button>x</button><script>document.querySelector('button').onclick = function () { TelegramWidget.request('r'); TelegramWidget.copyText('c'); TelegramWidget.openLink('https://example.com/'); };</script>"
        val d = RecordingDelegate().apply { answer = { _, respond -> respond(Result.success(WidgetRequestAnswer())) } }
        val (_, engine) = run(html, d) { it.context = tapOnly }
        engine.activationEvent(click(idOf(d, "button")), 1)
        engine.thread.sync {}
        assertEquals(listOf(WidgetRequest("r")), d.requests.toList())
        assertEquals(emptyList<String>(), d.copied.toList())
        assertEquals(emptyList<WidgetAction>(), d.actions.toList())
        assertEquals("${d.diags}", 2, d.count("activation-blocked"))
        engine.terminate()
    }

    @Test
    fun failuresAndInvalidAnswers() {
        val outcomes = listOf<Result<WidgetRequestAnswer>>(
            Result.failure(WidgetRequestException(WidgetRequestFailure.TIMEOUT)),
            Result.success(WidgetRequestAnswer("x".repeat(1025))),
            Result.failure(WidgetRequestException(WidgetRequestFailure.RATE_LIMITED)),
            Result.failure(IllegalStateException("offline")),
            Result.success(WidgetRequestAnswer("a\u0000b")),
            Result.success(WidgetRequestAnswer("{\"v\":[1,2]}")),
            Result.success(WidgetRequestAnswer()),
        )
        val d = RecordingDelegate()
        d.answer = { _, respond -> respond(outcomes[d.requests.size - 1]) }
        val (_, engine) = run(requester, d) { it.context = tapOnly }
        val button = idOf(d, "button")
        for (n in 1..outcomes.size) {
            engine.activationEvent(click(button), n)
            assertTrue(d.log(), waitFor { d.outcomes().size == n })
        }
        assertEquals(
            listOf(
                "err:tap:WidgetRequestError:TIMEOUT", "err:tap:WidgetRequestError:INVALID_ANSWER", "err:tap:WidgetRequestError:RATE_LIMITED",
                "err:tap:WidgetRequestError:FAILED", "err:tap:WidgetRequestError:INVALID_ANSWER", "ok:tap:{\"result\":\"{\\\"v\\\":[1,2]}\"}:true", "ok:tap:{\"result\":null}:true",
            ),
            d.outcomes(),
        )
        assertEquals("over 1024 bytes, and a NUL", List(2) { "the answer's result is not text of at most 1024 bytes (UTF-8, no NUL); the widget gets INVALID_ANSWER" }, d.rejected("answer-rejected"))
        engine.terminate()
    }

    /**
     * Telegram's clients create the host off screen: a background request at boot waits for the widget to appear and
     * goes out then, as that appearance's; one on `activated` is BUSY, the first being in flight (it used to be that the boot
     * one was refused as hidden and, on Android, the `activated` one met the shim's BUSY: nothing went out). A widget
     * terminated before it appears never gets its held request to the client.
     */
    @Test
    fun aBackgroundRequestAtBootOffScreenGoesOutWhenTheWidgetAppears() {
        val html = "<script>var log = []; function put(s) { log.push(s); console.log('log=' + log.join(' ')); }" +
            "function ask(t) { TelegramWidget.request(t).then(function (a) { put(t + ':' + a.result); }, function (e) { put(t + ':' + e.code); }); }" +
            "TelegramWidget.onEvent('activated', function () { ask('activated'); }); ask('boot'); console.log('active=' + TelegramWidget.isActive);</script>"
        val background = WidgetContext(canQuery = true, backgroundQuery = true)
        val d = RecordingDelegate().apply { answer = { _, respond -> respond(Result.success(WidgetRequestAnswer("r"))) } }
        val (_, engine) = run(html, d) { it.context = background; it.visible = false }
        assertTrue("${d.logs}", waitFor { d.logged().contains("active=false") })
        engine.thread.sync {}
        assertEquals("held while off screen", emptyList<WidgetRequest>(), d.requests.toList())
        assertEquals("", d.log())
        assertEquals(emptyList<String>(), d.rejected("request-blocked"))
        engine.setVisible(true)
        assertTrue(d.log(), waitFor { d.log().split(" ").size == 2 })
        assertEquals(listOf("activated:BUSY", "boot:r"), d.log().split(" ").sorted())
        assertEquals(listOf(WidgetRequest("boot")), d.requests.toList())
        engine.terminate()

        val dropped = RecordingDelegate().apply { answer = { _, respond -> respond(Result.success(WidgetRequestAnswer())) } }
        val (_, engine2) = run(html, dropped) { it.context = background; it.visible = false }
        assertTrue("${dropped.logs}", waitFor { dropped.logged().contains("active=false") })
        engine2.terminate()
        engine2.setVisible(true)
        Thread.sleep(300)
        assertEquals(emptyList<WidgetRequest>(), dropped.requests.toList())
    }

    /** One background request when the widget appears on screen (hidden → visible), then one per 5 s. */
    @Test
    fun aBackgroundRequestAtOnceWhenTheWidgetAppears() {
        val html = "<button>go</button><script>var log = []; function put(s) { log.push(s); console.log('log=' + log.join(' ')); }" +
            "document.querySelector('button').onclick = function () { TelegramWidget.request('bg').then(function () { put('ok'); }, function (e) { put(e.code); }); };</script>"
        val d = RecordingDelegate().apply { answer = { _, respond -> respond(Result.success(WidgetRequestAnswer())) } }
        val (_, engine) = run(html, d) { it.context = WidgetContext(canQuery = true, backgroundQuery = true) }
        val button = idOf(d, "button")
        // A click that is no gesture: the request goes out in the background, or not at all.
        fun ask(n: Int) {
            engine.event(click(button))
            assertTrue(d.log(), waitFor { d.log().split(" ").count { it.isNotEmpty() } == n })
        }
        ask(1)
        ask(2)
        engine.setVisible(false)
        engine.setVisible(true)
        ask(3)
        ask(4)
        assertEquals("ok RATE_LIMITED ok RATE_LIMITED", d.log())
        assertEquals(List(2) { WidgetRequest("bg") }, d.requests.toList())
        engine.terminate()
    }

    @Test
    fun theCompletionIsHonouredOnceFromAnyThread() {
        val d = RecordingDelegate()
        d.answer = { _, respond ->
            Thread {
                Thread.sleep(50)
                respond(Result.success(WidgetRequestAnswer("one")))
                respond(Result.success(WidgetRequestAnswer("two")))
                respond(Result.failure(WidgetRequestException(WidgetRequestFailure.TIMEOUT)))
            }.start()
        }
        val (_, engine) = run(requester, d) { it.context = tapOnly }
        engine.activationEvent(click(idOf(d, "button")), 1)
        assertTrue(d.log(), waitFor { d.outcomes().isNotEmpty() })
        Thread.sleep(300)
        engine.thread.sync {}
        assertEquals(listOf("ok:tap:{\"result\":\"one\"}:true"), d.outcomes())
        engine.terminate()

        // An answer after the widget is gone is dropped.
        val late = AtomicReference<(Result<WidgetRequestAnswer>) -> Unit>()
        val d2 = RecordingDelegate().apply { answer = { _, respond -> late.set(respond) } }
        val (_, engine2) = run(requester, d2) { it.context = tapOnly }
        engine2.activationEvent(click(idOf(d2, "button")), 1)
        assertTrue(waitFor { late.get() != null })
        engine2.terminate()
        Thread.sleep(100)
        late.get()(Result.success(WidgetRequestAnswer("late")))
        Thread.sleep(100)
        assertTrue(d2.log(), d2.outcomes().isEmpty())
        assertEquals("terminate() is the only death", "terminated", d2.died)

        // A delegate that throws has failed the request: FAILED in the widget, and the gate is free for the next tap.
        val d3 = RecordingDelegate().apply { answer = { _, _ -> throw IllegalStateException("client bug") } }
        val (_, engine3) = run(requester, d3) { it.context = tapOnly }
        engine3.activationEvent(click(idOf(d3, "button")), 1)
        assertTrue(d3.log(), waitFor { d3.outcomes().size == 1 })
        engine3.activationEvent(click(idOf(d3, "button")), 2)
        assertTrue(d3.log(), waitFor { d3.outcomes().size == 2 })
        assertEquals(List(2) { "err:tap:WidgetRequestError:FAILED" }, d3.outcomes())
        assertEquals(2, d3.requests.size)
        engine3.terminate()
    }

    /** `setData`: `chatDataChanged`, then `publicDataChanged`, for each field that changed; equal data fires nothing; a copy only `publicDataChanged`. */
    @Test
    fun setDataFiresOneEventPerChangedField() {
        val html = "<script>var log = []; function put(s) { log.push(s); console.log('log=' + log.join(' ')); }" +
            "TelegramWidget.onEvent('chatDataChanged', function () { put('c:' + TelegramWidget.chatData + ':' + (this === TelegramWidget)); });" +
            "TelegramWidget.onEvent('publicDataChanged', function () { put('p:' + TelegramWidget.publicData + ':' + (this === TelegramWidget)); });</script>"
        val (d, engine) = run(html) { it.data = WidgetData("a", "p") }
        engine.setData(WidgetData("a", "p"))
        engine.setData(WidgetData("b", "q"))
        engine.setData(WidgetData("b", "r"))
        engine.setData(WidgetData(publicData = "r"))
        engine.setData(WidgetData("x".repeat(1025), "r"))
        engine.setData(WidgetData("n\u0000", "r"))
        engine.thread.sync {}
        assertEquals("c:b:true p:q:true p:r:true c:null:true", d.log())
        assertEquals("equal data fires nothing", 4, d.logged().count { it.startsWith("log=") })
        assertEquals(List(2) { "chatData: not text of at most 1024 bytes (UTF-8, no NUL); the widget sees null" }, d.rejected("data-rejected"))
        engine.terminate()

        val (copy, engine2) = run(html) { it.data = WidgetData(publicData = "p"); it.context = WidgetContext(isCopy = true) }
        engine2.setData(WidgetData("c", "p"))
        engine2.setData(WidgetData("c", "p2"))
        engine2.thread.sync {}
        assertEquals("a copy ignores chatData; its publicData changes", "p:p2:true", copy.log())
        assertEquals(0, copy.count("data-rejected"))
        engine2.terminate()
    }

    /**
     * A preview (an inline result not sent yet, docs/sdk-v1.md §2.16): its data, nothing about where it is or who looks,
     * no storage preload, requests refused whatever the flags, only `ready` / `setResizeAnchor` reach the client
     * (`preview-blocked` for the rest), storage never committed, timers as usual.
     */
    @Test
    fun aPreviewTakesNoActions() {
        val html = "<script>" +
            "console.log('boot=' + JSON.stringify([TelegramWidget.isPreview, TelegramWidget.chatData, TelegramWidget.publicData, TelegramWidget.isForward, TelegramWidget.chatType, TelegramWidget.viewer, localStorage.length]));" +
            "TelegramWidget.ready(); TelegramWidget.setResizeAnchor('bottom');" +
            "TelegramWidget.request('r').then(function () { console.log('req=ok'); }, function (e) { console.log('req=' + e.code + ':' + /preview/.test(e.message)); });" +
            "TelegramWidget.openLink('https://example.com/'); TelegramWidget.copyText('c'); TelegramWidget.HapticFeedback.selectionChanged();" +
            "localStorage.setItem('k', 'v2'); sessionStorage.setItem('s', '1');" +
            "setTimeout(function () { console.log('timer=' + localStorage.getItem('k')); }, 0);</script>"
        val d = RecordingDelegate().apply { answer = { _, respond -> respond(Result.success(WidgetRequestAnswer("never"))) } }
        val (_, engine) = run(html, d) {
            it.data = WidgetData("c", "p")
            it.context = WidgetContext(isCopy = true, canQuery = true, backgroundQuery = true, chatType = "private", viewer = WidgetViewer(42), preview = true)
            it.storage = WidgetStorage(local = mapOf("k" to "v"))
        }
        assertTrue("${d.logs}", waitFor { d.logged().any { it.startsWith("timer=") } && d.logged().any { it.startsWith("req=") } })
        engine.thread.sync {}
        assertEquals("the data, no copy, nothing about the user, no preload", listOf("""boot=[true,"c","p",false,null,null,0]"""), d.logged().filter { it.startsWith("boot=") })
        assertEquals(listOf("req=NOT_ALLOWED:true"), d.logged().filter { it.startsWith("req=") })
        assertEquals("Web Storage works for this run", listOf("timer=v2"), d.logged().filter { it.startsWith("timer=") })
        assertEquals("ready reaches the client", listOf<WidgetAction>(WidgetAction.Ready), d.actions.toList())
        assertEquals(listOf("bottom"), d.resizeAnchors.toList())
        assertEquals(emptyList<WidgetRequest>(), d.requests.toList())
        assertEquals(emptyList<String>(), d.copied.toList())
        assertEquals("never committed", emptyList<Pair<String, Map<String, String>>>(), d.storage.toList())
        assertEquals(
            listOf("openLink", "copyText", "haptic").map { "$it ignored: a preview (an inline result not sent yet) takes no actions" },
            d.rejected("preview-blocked"),
        )
        assertEquals("${d.diags}", 0, d.count("activation-blocked") + d.count("haptic-limit") + d.count("storage-rejected") + d.count("request-blocked"))
        engine.terminate()
    }

    /** `watchPublicData` (docs/sdk-v1.md §2.13): kept like the anchor, no tap needed, never an action; a preview drops it. */
    @Test
    fun watchPublicDataIsKeptLikeTheAnchor() {
        val html = "<script>TelegramWidget.watchPublicData(); TelegramWidget.watchPublicData();" +
            "setTimeout(function () { TelegramWidget.unwatchPublicData(); TelegramWidget.watchPublicData(); console.log('done'); }, 50);</script>"
        val (d, engine) = run(html)
        assertTrue("${d.logs}", waitFor { d.logged().contains("done") })
        engine.thread.sync {}
        assertEquals("the shim sends only changes", listOf(true, false, true), d.watches.toList())
        assertEquals("${d.diags}", 0, d.count("bridge-rejected") + d.count("activation-blocked"))
        assertEquals("no action", emptyList<WidgetAction>(), d.actions.toList())
        engine.terminate()

        val (never, engine2) = run("<p>x</p>")
        assertEquals(emptyList<Boolean>(), never.watches.toList())
        engine2.terminate()

        val (preview, engine3) = run("<script>TelegramWidget.watchPublicData();</script>") { it.context = WidgetContext(preview = true) }
        assertEquals(emptyList<Boolean>(), preview.watches.toList())
        assertEquals(listOf("watchPublicData ignored: a preview (an inline result not sent yet) takes no actions"), preview.rejected("preview-blocked"))
        engine3.terminate()
    }

    @Test
    fun copyTextOnePerTapNeverWithoutOne() {
        val html = "<button>c</button><script>setTimeout(function () { TelegramWidget.copyText('timer'); }, 0);" +
            "document.querySelector('button').onclick = function () { TelegramWidget.copyText('PROMO-2026 ✓'); TelegramWidget.copyText('second'); };</script>"
        val (d, engine) = run(html)
        assertTrue("${d.diags}", waitFor { d.count("activation-blocked") == 1 })
        engine.activationEvent(click(idOf(d, "button")), 1)
        engine.thread.sync {}
        assertEquals(listOf("PROMO-2026 ✓"), d.copied.toList())
        assertEquals("${d.diags}", 2, d.count("activation-blocked"))
        assertEquals("copyText is no action", emptyList<WidgetAction>(), d.actions.toList())
        assertEquals(emptyList<Pair<String, String>>(), d.bridge.toList())
        engine.terminate()
    }

    // ── WidgetHost end to end ──

    /** A tap (down, up) at a point of the widget in dp, dispatched into the WebView like a finger. */
    private fun tap(view: View, xDp: Float, yDp: Float) {
        val density = view.resources.displayMetrics.density
        val t0 = SystemClock.uptimeMillis()
        fun send(action: Int, dt: Long) {
            val e = MotionEvent.obtain(t0, t0 + dt, action, xDp * density, yDp * density, 0)
            e.source = InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.runOnMainSync { view.dispatchTouchEvent(e) }
            e.recycle()
        }
        send(MotionEvent.ACTION_DOWN, 0)
        Thread.sleep(60)
        send(MotionEvent.ACTION_UP, 60)
    }

    private fun waitFor(timeoutS: Long = 10, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutS)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(50)
        }
        return condition()
    }

    @Test
    fun hostDeliversActionsClaimsCommitsAndThemeVariables() {
        val html = """
            <style>body { margin: 0 }</style>
            <div id="b" style="height:100px;background:#ccc">open</div>
            <div id="area" style="height:100px;touch-action:none">drag</div>
            <script>
              document.getElementById('b').addEventListener('click', function () {
                TelegramWidget.openLink('https://example.com/a');
                TelegramWidget.openLink('https://example.com/b');
                TelegramWidget.HapticFeedback.selectionChanged();
                localStorage.setItem('taps', String(Number(localStorage.getItem('taps')) + 1));
              });
            </script>
        """.trimIndent()
        val probe = "(function(){ var s = document.documentElement.style; return ['--tg-theme-bg-color', '--tg-theme-text-color', '--tg-color-scheme', '--tg-button-radius', '--tg-button-pressed-opacity', '--tg-text-font-size', '--tg-text-padding'].map(function (n) { return s.getPropertyValue(n).trim(); }).concat([getComputedStyle(document.documentElement).fontSize, getComputedStyle(document.body).marginTop]).join('|'); })()"
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            lateinit var host: WidgetHost
            val latest = AtomicReference(WidgetDiagnostics())
            val actions = Collections.synchronizedList(ArrayList<WidgetAction>())
            val claims = Collections.synchronizedList(ArrayList<WidgetGestureClaim>())
            val drags = Collections.synchronizedList(ArrayList<Pair<Boolean, Boolean>>())
            val commits = Collections.synchronizedList(ArrayList<Pair<String, Map<String, String>>>())
            val bridge = Collections.synchronizedList(ArrayList<String>())
            val offMain = Collections.synchronizedList(ArrayList<String>())
            fun onMain(what: String) { if (Looper.myLooper() != Looper.getMainLooper()) offMain.add(what) }
            scenario.onActivity { activity ->
                host = WidgetHost(
                    activity, html, WidgetTheme("light", mapOf("bg_color" to "#ABCDEF", "text_color" to "nope")), 360.0,
                    WidgetHost.Options(storage = WidgetStorage(local = mapOf("taps" to "41"))),
                )
                host.onChange = { latest.set(it) }
                host.onAction = { onMain("action"); actions.add(it) }
                host.onGesture = { onMain("claim"); claims.add(it) }
                @Suppress("DEPRECATION")
                host.onGestureClaim = { x, y -> onMain("claim"); drags.add(x to y) }
                host.onStorageCommit = { area, values -> onMain("storage"); commits.add(area to values) }
                @Suppress("DEPRECATION")
                host.onBridge = { method, _ -> bridge.add(method) }
                activity.show(host.view)
            }
            assertTrue("widget never loaded", waitFor(15) { latest.get().loaded })
            Thread.sleep(300)
            fun eval(): String {
                val done = CountDownLatch(1)
                val out = AtomicReference("")
                instrumentation.runOnMainSync { host.evaluateInRenderer(probe) { out.set(it); done.countDown() } }
                assertTrue(done.await(5, TimeUnit.SECONDS))
                return out.get().trim('"')
            }
            assertEquals("the scheme's defaults and the reference text tokens stand for what the client did not pass; the widget's body margin beats the layer's", "#abcdef|#000000|light|||15px|8px 12px|15px|0px", eval())

            tap(host.view, 50f, 50f)
            assertTrue("actions: $actions, diags: ${latest.get().diags.keys}", waitFor { actions.size >= 2 && commits.isNotEmpty() })
            Thread.sleep(200)
            assertEquals(listOf(WidgetAction.OpenLink("https://example.com/a"), WidgetAction.Haptic("selection", null)), actions.toList())
            assertEquals(actions.toList(), latest.get().actions)
            assertEquals("${latest.get().diags.mapValues { it.value.details }}", 1, latest.get().diags["activation-blocked"]?.count)
            assertEquals(listOf("local" to mapOf("taps" to "42")), commits.toList())
            assertEquals(listOf("openLink", "haptic"), bridge.toList())
            assertEquals("a plain element claims nothing", listOf(WidgetGestureClaim(x = false, y = false)), claims.toList())

            tap(host.view, 50f, 150f)
            assertTrue("$claims", waitFor { claims.size >= 2 && drags.size >= 2 })
            assertEquals("touch-action: none takes horizontal drags and holds, never vertical ones (v0.1.3)", WidgetGestureClaim(x = true, y = false, hold = true), claims[1])
            assertEquals("the deprecated onGestureClaim gets the drags of each claim", listOf(false to false, true to false), drags.toList())

            instrumentation.runOnMainSync { host.setTheme(WidgetTheme("dark", mapOf("bg_color" to "#101010"), WidgetButtonTokens(radius = "10px", pressedOpacity = 0.7), WidgetTextTokens(fontSize = 17.0, padding = "4px 6px"))) }
            val dark = "#101010|#ffffff|dark|10px|0.7|17px|4px 6px|17px|0px"
            assertTrue(waitFor(5) { eval() == dark })
            assertEquals(dark, eval())

            // The client's time over the widget (docs/sdk-v1.md §2.17): the variables on the root, the UA layer's body padding.
            val safeArea = "(function(){ var s = getComputedStyle(document.documentElement); return ['--tg-content-safe-area-inset-bottom', '--tg-content-safe-area-corner-right', '--tg-content-safe-area-corner-left'].map(function (n) { return s.getPropertyValue(n).trim(); }).concat([getComputedStyle(document.body).paddingBottom]).join('|'); })()"
            fun evalSafeArea(): String {
                val done = CountDownLatch(1)
                val out = AtomicReference("")
                instrumentation.runOnMainSync { host.evaluateInRenderer(safeArea) { out.set(it); done.countDown() } }
                assertTrue(done.await(5, TimeUnit.SECONDS))
                return out.get().trim('"')
            }
            assertEquals("0 until the client passes a band", "0px|0px|0px|0px", evalSafeArea())
            instrumentation.runOnMainSync { host.setContentSafeArea(WidgetContentSafeArea(bottom = 24.0, cornerLeft = 64.0)) }
            assertTrue(waitFor(5) { evalSafeArea() == "24px|0px|64px|24px" })
            assertEquals("24px|0px|64px|24px", evalSafeArea())
            assertEquals("callbacks run on the main thread", emptyList<String>(), offMain.toList())
            assertNull(latest.get().dead)
            instrumentation.runOnMainSync { host.destroy() }
        }
    }

    /**
     * v0.2.2 through WidgetHost on the WebView renderer: a widget whose timer used up its haptic series gets none from the
     * timer afterwards, but a real tap (the renderer's `gesture` at pointerdown and `release` at pointerup) plays one.
     */
    @Test
    fun hostHapticsNeedATouch() {
        val html = """
            <style>body { margin: 0 }</style>
            <div id="b" style="height:150px;background:#ccc">tap</div>
            <script>
              var n = 0;
              var t = setInterval(function () { TelegramWidget.HapticFeedback.selectionChanged(); if (++n === 150) { clearInterval(t); console.log('timer=' + n); } }, 10);
              document.getElementById('b').addEventListener('click', function () { TelegramWidget.HapticFeedback.impactOccurred('heavy'); console.log('tapped'); });
            </script>
        """.trimIndent()
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            lateinit var host: WidgetHost
            val latest = AtomicReference(WidgetDiagnostics())
            val actions = Collections.synchronizedList(ArrayList<WidgetAction>())
            scenario.onActivity { activity ->
                host = WidgetHost(activity, html, WidgetTheme(), 360.0)
                host.onChange = { latest.set(it) }
                host.onAction = { actions.add(it) }
                activity.show(host.view)
            }
            assertTrue("widget never loaded", waitFor(15) { latest.get().loaded && latest.get().logs.any { it.second.startsWith("timer=") } })
            Thread.sleep(300)
            val fromTimer = actions.toList()
            assertTrue("the timer's series: $fromTimer", fromTimer.size in 5..16 && fromTimer.all { it == WidgetAction.Haptic("selection", null) })
            tap(host.view, 50f, 50f)
            assertTrue("${latest.get().logs}", waitFor { latest.get().logs.any { it.second == "tapped" } })
            Thread.sleep(300)
            assertEquals("the tap's haptic plays", fromTimer + WidgetAction.Haptic("impact", "heavy"), actions.toList())
            assertNull(latest.get().dead)
            instrumentation.runOnMainSync { host.destroy() }
        }
    }

    /** The renderer with a hand on its messages: `inject` hands WidgetHost one as if the renderer sent it, `sent` keeps what WidgetHost sent the renderer. */
    private class Injector(private val inner: Renderer) : Renderer by inner {
        val sent: MutableList<String> = Collections.synchronizedList(ArrayList())
        override var onMessage: ((String) -> Unit)? = null
            set(value) {
                field = value
                inner.onMessage = value
            }
        override fun send(json: String) {
            sent.add(json)
            inner.send(json)
        }
        fun inject(json: String) { onMessage?.invoke(json) }
    }

    /**
     * Any pointerdown begins a press, trusted or not (desktop `an_untrusted_pointerdown_begins_a_new_gesture`): a second
     * finger's is neither trusted (it may become the chat's scroll) nor claimed (`gesture` is the primary pointer's), so each
     * of its taps opens a link from its pointerup handler; without WidgetHost's `pressBegan` the second release continued the
     * first press, whose gesture was spent.
     */
    @Test
    fun hostAnUntrustedPointerdownBeginsANewGesture() {
        val html = "<button id='b'>go</button><script>TelegramWidget.ready(); var n = 0;" +
            "document.getElementById('b').addEventListener('pointerup', function () { TelegramWidget.openLink('https://example.com/up-' + (++n)); });</script>"
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            lateinit var host: WidgetHost
            lateinit var renderer: Injector
            val latest = AtomicReference(WidgetDiagnostics())
            val links = Collections.synchronizedList(ArrayList<String>())
            scenario.onActivity { activity ->
                host = WidgetHost(activity, html, WidgetTheme(), 360.0, rendererFactory = { context, res, theme, _ ->
                    Injector(RendererWebView(context, res.rendererBundle, theme)).also { renderer = it }
                })
                host.onChange = { latest.set(it) }
                host.onAction = { if (it is WidgetAction.OpenLink) links.add(it.url) }
                activity.show(host.view)
            }
            assertTrue("widget never loaded", waitFor(15) { latest.get().loaded })
            Thread.sleep(300)
            val button = renderer.sent.toList().firstNotNullOfOrNull { json ->
                val ops = runCatching { JSONObject(json) }.getOrNull()?.takeIf { it.optString("type") == "ops" }?.optJSONArray("ops") ?: return@firstNotNullOfOrNull null
                (0 until ops.length()).mapNotNull { ops.optJSONObject(it) }.firstOrNull { it.optString("op") == "el" && it.optString("tag") == "button" }?.getInt("id")
            } ?: throw AssertionError("no <button> in the ops sent to the renderer")
            fun event(type: String, trusted: Boolean) = instrumentation.runOnMainSync {
                renderer.inject("{\"type\":\"event\",\"ev\":{\"type\":\"$type\",\"targetId\":$button,\"pointerId\":2,\"pointerType\":\"touch\",\"isPrimary\":false},\"trusted\":$trusted}")
            }
            // a second finger's tap, its gesture spent in its pointerup handler; then another tap of it
            event("pointerdown", false)
            event("pointerup", true)
            event("pointerdown", false)
            event("pointerup", true)
            waitFor { links.size >= 2 }
            Thread.sleep(100)
            assertEquals("${latest.get().diags.mapValues { it.value.details }}", listOf("https://example.com/up-1", "https://example.com/up-2"), links.toList())
            assertNull("${latest.get().diags.mapValues { it.value.details }}", latest.get().diags["activation-blocked"])
            assertNull(latest.get().dead)
            instrumentation.runOnMainSync { host.destroy() }
        }
    }

    /** The renderer with a look at each `size` report just before WidgetHost handles it. */
    private class SizeProbe(private val inner: Renderer, private val onSize: (JSONObject) -> Unit) : Renderer by inner {
        override var onMessage: ((String) -> Unit)? = null
            set(value) {
                field = value
                inner.onMessage = { json ->
                    if (json.contains("\"size\"")) runCatching { JSONObject(json) }.getOrNull()?.takeIf { it.optString("type") == "size" }?.let(onSize)
                    value?.invoke(json)
                }
            }
    }

    /**
     * `setResizeAnchor` through WidgetHost on the WebView renderer (v0.1.3): the latest anchor is in the diagnostics next to
     * the height, never in `actions` / `bridge`, and it is there before the size report of the content its task changed
     * (bridge → ops → renderer → size: the engine posts the anchor during the task, the ops at its end).
     */
    @Test
    fun hostKeepsTheResizeAnchorNextToTheHeight() {
        val html = """
            <style>body { margin: 0 }</style>
            <script>TelegramWidget.setResizeAnchor('bottom');</script>
            <div id="box" style="height:100px;background:#ccc">tap</div>
            <script>
              document.getElementById('box').addEventListener('click', function () {
                TelegramWidget.setResizeAnchor('top');
                document.getElementById('box').style.height = '220px';
              });
            </script>
        """.trimIndent()
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            lateinit var host: WidgetHost
            lateinit var plain: WidgetHost
            val latest = AtomicReference(WidgetDiagnostics())
            val plainLatest = AtomicReference(WidgetDiagnostics())
            /** Each size report the renderer sent: (its height, the host's anchor when the host got it). */
            val atSize = Collections.synchronizedList(ArrayList<Pair<Double, String>>())
            val actions = Collections.synchronizedList(ArrayList<WidgetAction>())
            scenario.onActivity { activity ->
                val probe = RendererFactory { context, res, theme, _ ->
                    SizeProbe(RendererWebView(context, res.rendererBundle, theme)) { atSize.add(it.optDouble("height") to host.diagnostics.resizeAnchor) }
                }
                host = WidgetHost(activity, html, WidgetTheme(), 360.0, rendererFactory = probe)
                host.onChange = { latest.set(it) }
                host.onAction = { actions.add(it) }
                activity.show(host.view)
            }
            assertTrue("widget never loaded", waitFor(15) { latest.get().loaded && latest.get().height >= 100 })
            Thread.sleep(300)
            assertEquals("bottom", latest.get().resizeAnchor)
            assertEquals("the first size report of the content finds the anchor its task set: $atSize", "bottom", atSize.first { it.first >= 100 }.second)

            tap(host.view, 50f, 50f)
            assertTrue("$atSize", waitFor { latest.get().height >= 220 })
            Thread.sleep(200)
            assertEquals("top", latest.get().resizeAnchor)
            assertEquals("the grown height's size report finds the anchor of the tap that grew it: $atSize", "top", atSize.first { it.first >= 220 }.second)
            assertEquals(emptyList<WidgetAction>(), actions.toList())
            assertEquals(emptyList<WidgetAction>(), latest.get().actions)
            assertEquals(emptyList<Pair<String, String>>(), latest.get().bridge)
            assertNull("${latest.get().diags.keys}", latest.get().diags["bridge-rejected"])

            // A widget that never calls it: top.
            scenario.onActivity { activity ->
                plain = WidgetHost(activity, "<p>plain</p>", WidgetTheme(), 360.0)
                plain.onChange = { plainLatest.set(it) }
                activity.show(plain.view)
            }
            assertTrue("plain widget never loaded", waitFor(15) { plainLatest.get().loaded })
            assertEquals("top", plainLatest.get().resizeAnchor)
            instrumentation.runOnMainSync { host.destroy(); plain.destroy() }
        }
    }

    /**
     * `onRequest` is read on the main thread when a request arrives, so a client may set it after the constructor: a request
     * the widget makes from a timer after that is delivered (background, where the context allows it). A widget that asks
     * at boot reaches a client that set `onRequest` right after the constructor, in the same main-thread turn.
     */
    @Test
    fun hostDeliversToAnOnRequestSetAfterConstruction() {
        val timer = "<p>poll</p><script>setTimeout(function () { TelegramWidget.request('poll').then(function (a) { console.log('poll=ok:' + a.result); }, function (e) { console.log('poll=err:' + e.code); }); }, 3000);</script>"
        val boot = "<p>boot</p><script>TelegramWidget.request('boot').then(function (a) { console.log('poll=ok:' + a.result); }, function (e) { console.log('poll=err:' + e.code); });</script>"
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            lateinit var host: WidgetHost
            lateinit var early: WidgetHost
            val latest = AtomicReference(WidgetDiagnostics())
            val earlyLatest = AtomicReference(WidgetDiagnostics())
            val requests = Collections.synchronizedList(ArrayList<WidgetRequest>())
            fun poll(d: WidgetDiagnostics) = d.logs.map { it.second }.filter { it.startsWith("poll=") }
            val options = WidgetHost.Options(context = WidgetContext(canQuery = true, backgroundQuery = true, chatType = "private"))
            scenario.onActivity { activity ->
                host = WidgetHost(activity, timer, WidgetTheme(), 360.0, options)
                host.onChange = { latest.set(it) }
                activity.show(host.view)
            }
            assertTrue("widget never loaded", waitFor(15) { latest.get().loaded })
            scenario.onActivity { host.onRequest = { request, respond -> requests.add(request); respond(Result.success(WidgetRequestAnswer("fresh"))) } }
            assertTrue("${latest.get().logs}", waitFor { poll(latest.get()).isNotEmpty() })
            assertEquals(listOf("poll=ok:fresh"), poll(latest.get()))
            assertEquals("a timer's request: background", listOf(WidgetRequest("poll")), requests.toList())
            assertNull("${latest.get().diags.keys}", latest.get().diags["request-blocked"])

            scenario.onActivity { activity ->
                early = WidgetHost(activity, boot, WidgetTheme(), 360.0, options)
                early.onChange = { earlyLatest.set(it) }
                early.onRequest = { request, respond -> requests.add(request); respond(Result.success(WidgetRequestAnswer("at boot"))) }
                activity.show(early.view)
            }
            assertTrue("${earlyLatest.get().logs}", waitFor(15) { poll(earlyLatest.get()).isNotEmpty() })
            assertEquals(listOf("poll=ok:at boot"), poll(earlyLatest.get()))
            assertEquals(WidgetRequest("boot"), requests.last())
            instrumentation.runOnMainSync { host.destroy(); early.destroy() }
        }
    }

    /**
     * `watchPublicData` through WidgetHost: `onWatchPublicData` on a change only (main thread), the latest in
     * `diagnostics.watchesPublicData`; a widget that never calls it stays false; a preview's call is dropped.
     */
    @Test
    fun hostReportsWatchPublicData() {
        val html = "<p>w</p><script>TelegramWidget.watchPublicData(); TelegramWidget.watchPublicData();" +
            "setTimeout(function () { TelegramWidget.unwatchPublicData(); TelegramWidget.watchPublicData(); console.log('done'); }, 100);</script>"
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            lateinit var host: WidgetHost
            lateinit var plain: WidgetHost
            lateinit var preview: WidgetHost
            val latest = AtomicReference(WidgetDiagnostics())
            val plainLatest = AtomicReference(WidgetDiagnostics())
            val previewLatest = AtomicReference(WidgetDiagnostics())
            val changes = Collections.synchronizedList(ArrayList<Boolean>())
            val others = Collections.synchronizedList(ArrayList<Boolean>())
            val offMain = Collections.synchronizedList(ArrayList<String>())
            scenario.onActivity { activity ->
                host = WidgetHost(activity, html, WidgetTheme(), 360.0)
                host.onChange = { latest.set(it) }
                host.onWatchPublicData = { if (Looper.myLooper() != Looper.getMainLooper()) offMain.add("watch"); changes.add(it) }
                plain = WidgetHost(activity, "<p>plain</p>", WidgetTheme(), 360.0)
                plain.onChange = { plainLatest.set(it) }
                plain.onWatchPublicData = { others.add(it) }
                preview = WidgetHost(activity, html, WidgetTheme(), 360.0, WidgetHost.Options(context = WidgetContext(preview = true)))
                preview.onChange = { previewLatest.set(it) }
                preview.onWatchPublicData = { others.add(it) }
                val column = android.widget.LinearLayout(activity).apply { orientation = android.widget.LinearLayout.VERTICAL }
                val dp = activity.resources.displayMetrics.density
                for (v in listOf(host.view, plain.view, preview.view)) column.addView(v, android.widget.LinearLayout.LayoutParams((360 * dp).toInt(), (150 * dp).toInt()))
                activity.show(column)
            }
            assertTrue("widgets never loaded", waitFor(15) { latest.get().loaded && plainLatest.get().loaded && previewLatest.get().loaded })
            assertTrue("${latest.get().logs}", waitFor { latest.get().logs.any { it.second == "done" } && previewLatest.get().logs.any { it.second == "done" } })
            Thread.sleep(300)
            assertEquals("on a change only", listOf(true, false, true), changes.toList())
            assertTrue(latest.get().watchesPublicData)
            assertNull("${latest.get().diags.keys}", latest.get().diags["bridge-rejected"])
            assertEquals(emptyList<WidgetAction>(), latest.get().actions)
            assertFalse("never called: false", plainLatest.get().watchesPublicData)
            assertFalse("a preview's call is dropped", previewLatest.get().watchesPublicData)
            assertEquals("each change the shim sends (true, false, true) is dropped", 3, previewLatest.get().diags["preview-blocked"]?.count)
            assertEquals("no callback for the plain widget or the preview", emptyList<Boolean>(), others.toList())
            assertEquals("callbacks run on the main thread", emptyList<String>(), offMain.toList())
            instrumentation.runOnMainSync { host.destroy(); plain.destroy(); preview.destroy() }
        }
    }

    /** A parent that records whether its child asked it not to intercept (RendererWebView does on every press it may claim). */
    private class RecordingFrame(context: android.content.Context) : android.widget.FrameLayout(context) {
        val disallowed: MutableList<Boolean> = Collections.synchronizedList(ArrayList())
        override fun requestDisallowInterceptTouchEvent(disallow: Boolean) {
            disallowed.add(disallow)
            super.requestDisallowInterceptTouchEvent(disallow)
        }
    }

    /**
     * A preview through WidgetHost on the WebView renderer: a real tap reaches nothing (no event in the widget, no claim, no
     * action) and the WebView never keeps the press from its parent; `ready` and `setResizeAnchor` work; storage is never
     * committed; timers run. A normal bubble beside it does hold the press back (the contrast for the parent check).
     */
    @Test
    fun hostPreviewTakesNoInput() {
        val html = """
            <style>body { margin: 0 }</style>
            <div id="b" style="height:150px;touch-action:none;background:#ccc">tap</div>
            <script>
              TelegramWidget.ready();
              TelegramWidget.setResizeAnchor('bottom');
              localStorage.setItem('k', 'v');
              var n = 0;
              var t = setInterval(function () { n++; if (n === 3) { clearInterval(t); console.log('ticks=' + n); } }, 50);
              ['pointerdown', 'click'].forEach(function (type) {
                document.getElementById('b').addEventListener(type, function () { console.log('got=' + type); TelegramWidget.openLink('https://example.com/'); });
              });
            </script>
        """.trimIndent()
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            lateinit var preview: WidgetHost
            lateinit var normal: WidgetHost
            lateinit var frame: RecordingFrame
            lateinit var normalFrame: RecordingFrame
            val latest = AtomicReference(WidgetDiagnostics())
            val normalLatest = AtomicReference(WidgetDiagnostics())
            val seen = Collections.synchronizedList(ArrayList<String>())
            fun lines(d: WidgetDiagnostics, prefix: String) = d.logs.map { it.second }.filter { it.startsWith(prefix) }
            scenario.onActivity { activity ->
                preview = WidgetHost(
                    activity, html, WidgetTheme(), 360.0,
                    WidgetHost.Options(
                        data = WidgetData("c", "p"), storage = WidgetStorage(local = mapOf("k" to "old")),
                        context = WidgetContext(canQuery = true, backgroundQuery = true, chatType = "private", viewer = WidgetViewer(7), preview = true),
                    ),
                )
                preview.onChange = { latest.set(it) }
                preview.onAction = { seen.add("action:$it") }
                preview.onGesture = { seen.add("claim:$it") }
                @Suppress("DEPRECATION")
                preview.onGestureClaim = { x, y -> seen.add("drags:$x,$y") }
                preview.onRequest = { _, _ -> seen.add("request") }
                preview.onCopyText = { seen.add("copy:$it") }
                preview.onStorageCommit = { area, _ -> seen.add("storage:$area") }
                frame = RecordingFrame(activity).also { it.addView(preview.view) }
                activity.show(frame)
            }
            assertTrue("preview never loaded", waitFor(15) { latest.get().loaded && latest.get().height >= 150 && lines(latest.get(), "ticks=").isNotEmpty() })
            Thread.sleep(300)
            frame.disallowed.clear()
            tap(preview.view, 50f, 50f)
            Thread.sleep(600)
            assertEquals("a tap on a preview reaches nothing in the widget", emptyList<String>(), lines(latest.get(), "got="))
            assertEquals("ready only; no claim, request, copy or storage commit", listOf("action:${WidgetAction.Ready}"), seen.toList())
            assertEquals("the WebView keeps no press from its parent", emptyList<Boolean>(), frame.disallowed.toList())
            assertEquals("bottom", latest.get().resizeAnchor)
            assertNull("${latest.get().diags.keys}", latest.get().diags["activation-blocked"])
            assertNull(latest.get().dead)

            scenario.onActivity { activity ->
                normal = WidgetHost(activity, html, WidgetTheme(), 360.0)
                normal.onChange = { normalLatest.set(it) }
                normalFrame = RecordingFrame(activity).also { it.addView(normal.view) }
                activity.show(normalFrame)
            }
            assertTrue("normal widget never loaded", waitFor(15) { normalLatest.get().loaded && normalLatest.get().height >= 150 })
            Thread.sleep(300)
            normalFrame.disallowed.clear()
            tap(normal.view, 50f, 50f)
            assertTrue("${normalLatest.get().logs}", waitFor { lines(normalLatest.get(), "got=click").isNotEmpty() })
            assertEquals("a normal bubble holds the press back until the claim", true, normalFrame.disallowed.firstOrNull())
            instrumentation.runOnMainSync { preview.destroy(); normal.destroy() }
        }
    }

    /**
     * The bot's data and requests through WidgetHost on the WebView renderer: the context and data at boot, a real tap → `onRequest` on the
     * main thread → the promise resolves with the result; a failure from another thread; `copyText` → `onCopyText`;
     * `setData`; a host without `onRequest` refuses (NOT_ALLOWED, `unsupported`).
     */
    @Test
    fun hostSendsRequestsCopiesAndData() {
        val html = """
            <style>body { margin: 0 }</style>
            <div id="ask" style="height:100px;background:#ccc">ask</div>
            <div id="copy" style="height:100px;background:#eee">copy</div>
            <script>
              var log = [];
              function put(s) { log.push(s); console.log('log=' + log.join(' ')); }
              console.log('boot=' + JSON.stringify([TelegramWidget.chatData, TelegramWidget.publicData, TelegramWidget.isForward, TelegramWidget.chatType, TelegramWidget.viewer]));
              TelegramWidget.onEvent('chatDataChanged', function () { put('changed:' + TelegramWidget.chatData); });
              document.getElementById('ask').addEventListener('click', function () {
                TelegramWidget.request('vote:1').then(function (a) { put('ok:' + a.result + ':' + TelegramWidget.chatData); }, function (e) { put('err:' + e.code); });
              });
              document.getElementById('copy').addEventListener('click', function () { TelegramWidget.copyText('PROMO'); });
            </script>
        """.trimIndent()
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            lateinit var host: WidgetHost
            lateinit var bare: WidgetHost
            val latest = AtomicReference(WidgetDiagnostics())
            val bareLatest = AtomicReference(WidgetDiagnostics())
            val requests = Collections.synchronizedList(ArrayList<WidgetRequest>())
            val copied = Collections.synchronizedList(ArrayList<String>())
            val offMain = Collections.synchronizedList(ArrayList<String>())
            fun onMain(what: String) { if (Looper.myLooper() != Looper.getMainLooper()) offMain.add(what) }
            fun lines(d: WidgetDiagnostics, prefix: String) = d.logs.map { it.second }.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
            fun log(d: WidgetDiagnostics) = lines(d, "log=").lastOrNull() ?: ""
            scenario.onActivity { activity ->
                host = WidgetHost(
                    activity, html, WidgetTheme(), 360.0,
                    WidgetHost.Options(data = WidgetData("v1", "pub"), context = WidgetContext(canQuery = true, chatType = "group", viewer = WidgetViewer(7))),
                )
                host.onChange = { latest.set(it) }
                host.onRequest = { request, respond ->
                    onMain("request")
                    requests.add(request)
                    if (requests.size == 1) respond(Result.success(WidgetRequestAnswer("thanks")))
                    else Thread { respond(Result.failure(WidgetRequestException(WidgetRequestFailure.TIMEOUT))) }.start()
                }
                host.onCopyText = { onMain("copy"); copied.add(it) }
                activity.show(host.view)
            }
            assertTrue("widget never loaded", waitFor(15) { latest.get().loaded && latest.get().height >= 200 })
            Thread.sleep(300)
            assertEquals(listOf("""["v1","pub",false,"group",{"id":7}]"""), lines(latest.get(), "boot="))

            tap(host.view, 50f, 50f)
            assertTrue(log(latest.get()), waitFor { log(latest.get()).contains("ok:") })
            assertEquals("the answer is only its result", "ok:thanks:v1", log(latest.get()))
            assertEquals("onRequest gets only the data", listOf(WidgetRequest("vote:1")), requests.toList())

            tap(host.view, 50f, 50f)
            assertTrue(log(latest.get()), waitFor { log(latest.get()).contains("err:") })
            assertEquals("a failure from another thread", "ok:thanks:v1 err:TIMEOUT", log(latest.get()))

            tap(host.view, 50f, 150f)
            assertTrue("$copied", waitFor { copied.isNotEmpty() })
            assertEquals(listOf("PROMO"), copied.toList())

            instrumentation.runOnMainSync { host.setData(WidgetData("v3", "pub")); host.setData(WidgetData("v3", "pub")) }
            assertTrue(log(latest.get()), waitFor { log(latest.get()).endsWith("changed:v3") })
            Thread.sleep(300)
            assertEquals("ok:thanks:v1 err:TIMEOUT changed:v3", log(latest.get()))
            assertEquals("requests and copies are no actions", emptyList<WidgetAction>(), latest.get().actions)
            assertEquals(emptyList<Pair<String, String>>(), latest.get().bridge)
            assertEquals("callbacks run on the main thread", emptyList<String>(), offMain.toList())
            assertNull(latest.get().dead)

            scenario.onActivity { activity ->
                bare = WidgetHost(activity, html, WidgetTheme(), 360.0, WidgetHost.Options(context = WidgetContext(canQuery = true)))
                bare.onChange = { bareLatest.set(it) }
                activity.show(bare.view)
            }
            assertTrue("bare widget never loaded", waitFor(15) { bareLatest.get().loaded && bareLatest.get().height >= 200 })
            Thread.sleep(300)
            assertEquals(listOf("[null,null,false,null,null]"), lines(bareLatest.get(), "boot="))
            tap(bare.view, 50f, 50f)
            assertTrue(log(bareLatest.get()), waitFor { log(bareLatest.get()).isNotEmpty() })
            assertEquals("err:NOT_ALLOWED", log(bareLatest.get()))
            assertEquals(listOf("request refused: the client handles no requests (onRequest)"), bareLatest.get().diags["request-blocked"]?.details)
            instrumentation.runOnMainSync { host.destroy(); bare.destroy() }
        }
    }

    // ── the wallet (docs/sdk-v1.md §2.18): sdk.test.ts and SdkTests.swift ──

    private val tx = WalletTransaction(validUntil = 1900000000L, messages = listOf(WalletMessage("EQ" + "ab".repeat(23), "1500000000")))
    private val txJson = """{"valid_until":1900000000,"messages":[{"address":"EQ${"ab".repeat(23)}","amount":"1500000000"}]}"""
    /** A tap asks the bot with the wallet, the answer's transaction goes to `sendTransaction`; every outcome into `log`. */
    private val payer = "<button>pay</button><script>var log = []; function put(s) { log.push(s); console.log('log=' + log.join(' ')); }" +
        "document.querySelector('button').onclick = function () { TelegramWidget.request('pay:42', { wallet: true })" +
        ".then(function (a) { put('tx'); return TelegramWidget.wallet.sendTransaction(JSON.parse(a.result), 'paid:42'); })" +
        ".then(function (b) { put('ok:' + b.result); }, function (e) { put('err:' + e.code); return TelegramWidget.wallet.sendTransaction(JSON.parse('$txJson'), 'again').catch(function (e2) { put('again:' + e2.code); }); }); };</script>"

    @Test
    fun walletOnlyWhereTheClientHasItAndTheWidgetMayAsk() {
        val probe = "<script>console.log('w=' + (TelegramWidget.wallet === null ? 'null' : typeof TelegramWidget.wallet.sendTransaction + Object.isFrozen(TelegramWidget.wallet)));</script>"
        for ((flags, expect) in listOf(
            WidgetContext(canQuery = true, backgroundQuery = true, chatType = "private", wallet = true) to "w=functiontrue",
            WidgetContext(isCopy = true, canQuery = true, wallet = true) to "w=functiontrue",
            WidgetContext(canQuery = true) to "w=null",
            WidgetContext(wallet = true) to "w=null",
            WidgetContext(canQuery = true, preview = true, wallet = true) to "w=null",
        )) {
            val (d, engine) = run(probe) { it.context = flags }
            assertTrue("$flags", waitFor { d.logged().any { it.startsWith("w=") } })
            assertEquals("$flags", expect, d.logged().first { it.startsWith("w=") })
            engine.terminate()
        }
    }

    @Test
    fun aTapPaysOnTheAnswerToItsRequest() {
        val d = RecordingDelegate()
        d.answer = { _, respond -> respond(Result.success(WidgetRequestAnswer(txJson))) }
        d.wallet = { _, respond -> respond(Result.success(WidgetRequestAnswer("thanks"))) }
        val (_, engine) = run(payer, d) { it.context = WidgetContext(canQuery = true, backgroundQuery = true, chatType = "private", wallet = true) }
        engine.activationEvent(click(idOf(d, "button")), 1)
        assertTrue(d.log(), waitFor { d.log().contains("ok:") || d.log().contains("err:") })
        assertEquals("tx ok:thanks", d.log())
        assertEquals(listOf(WidgetRequest("pay:42", wallet = true)), d.requests.toList())
        assertEquals(listOf<WalletCall>(WalletCall.SendTransaction(tx, "paid:42")), d.walletCalls.toList())
        assertEquals("a wallet call is no action", emptyList<WidgetAction>(), d.actions.toList())
        engine.terminate()
    }

    @Test
    fun walletRefusalsAndTheBackground() {
        val d = RecordingDelegate()
        d.answer = { _, respond -> respond(Result.success(WidgetRequestAnswer(txJson))) }
        d.wallet = { _, respond -> respond(Result.failure(WidgetRequestException(WidgetRequestFailure.REJECTED))) }
        val (_, engine) = run(payer, d) { it.context = WidgetContext(canQuery = true, chatType = "group", wallet = true) }
        engine.activationEvent(click(idOf(d, "button")), 1)
        assertTrue(d.log(), waitFor { d.log().contains("again:") })
        assertEquals("a refusal brings no grant", "tx err:REJECTED again:NOT_ALLOWED", d.log())
        assertEquals(1, d.walletCalls.size)
        assertEquals(listOf("wallet.sendTransaction refused (no-activation): it needs a tap on the visible widget, or the answer to a call made by a tap within 10 s; one call each"), d.rejected("request-blocked"))
        engine.terminate()

        // A request's failure REJECTED is FAILED there.
        val plain = RecordingDelegate().apply { answer = { _, respond -> respond(Result.failure(WidgetRequestException(WidgetRequestFailure.REJECTED))) } }
        val (_, e2) = run(requester, plain) { it.context = tapOnly }
        e2.activationEvent(click(idOf(plain, "button")), 1)
        assertTrue(plain.log(), waitFor { plain.outcomes().isNotEmpty() })
        assertEquals("err:tap:WidgetRequestError:FAILED", plain.outcomes()[0])
        e2.terminate()

        // From a timer: neither a wallet call nor a request for the wallet goes out, also in the private chat with the bot.
        val timer = RecordingDelegate().apply {
            answer = { _, respond -> respond(Result.success(WidgetRequestAnswer())) }
            wallet = { _, respond -> respond(Result.success(WidgetRequestAnswer())) }
        }
        val html = "<script>var log = []; function put(s) { log.push(s); console.log('log=' + log.join(' ')); } setTimeout(function () {" +
            "TelegramWidget.wallet.signData({ type: 'text', text: 'x' }, 's').catch(function (e) { put('sign:' + e.code); });" +
            "TelegramWidget.request('w', { wallet: true }).catch(function (e) { put('rq:' + e.code); }); }, 0);</script>"
        val (_, e3) = run(html, timer) { it.context = WidgetContext(canQuery = true, backgroundQuery = true, chatType = "private", wallet = true) }
        assertTrue(timer.log(), waitFor { timer.log().split(" ").size == 2 })
        assertEquals(listOf("rq:NOT_ALLOWED", "sign:NOT_ALLOWED"), timer.log().split(" ").sorted())
        assertEquals(0, timer.walletCalls.size + timer.requests.size)
        e3.terminate()
    }

    @Test
    fun signDataOnATapAndNoClientSide() {
        val html = "<button>s</button><script>document.querySelector('button').onclick = function () {" +
            "TelegramWidget.wallet.signData({ type: 'text', text: 'I agree', network: null }, 'sign:1').then(function (a) { console.log('ok:' + a.result); }, function (e) { console.log('err:' + e.code); }); };</script>"
        val flags = WidgetContext(isCopy = true, canQuery = true, wallet = true)
        val d = RecordingDelegate().apply { wallet = { _, respond -> respond(Result.success(WidgetRequestAnswer("signed"))) } }
        val (_, engine) = run(html, d) { it.context = flags }
        engine.activationEvent(click(idOf(d, "button")), 1)
        assertTrue(waitFor { d.logged().any { it.startsWith("ok:") || it.startsWith("err:") } })
        assertEquals(listOf("ok:signed"), d.logged().filter { it.startsWith("ok:") || it.startsWith("err:") })
        assertEquals(listOf<WalletCall>(WalletCall.SignData(SignDataPayload("text", text = "I agree"), "sign:1")), d.walletCalls.toList())
        engine.terminate()

        val none = RecordingDelegate()
        val (_, e2) = run(html, none) { it.context = flags }
        e2.activationEvent(click(idOf(none, "button")), 1)
        assertTrue(waitFor { none.logged().any { it.startsWith("err:") } })
        assertEquals(listOf("err:NOT_ALLOWED"), none.logged().filter { it.startsWith("err:") })
        assertEquals(listOf("wallet call refused: the client handles no wallet calls (onWallet)"), none.rejected("request-blocked"))
        e2.terminate()
    }
}

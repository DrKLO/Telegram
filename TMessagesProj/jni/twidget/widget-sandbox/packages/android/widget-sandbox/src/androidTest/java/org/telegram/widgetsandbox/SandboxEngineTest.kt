package org.telegram.widgetsandbox

import android.os.Looper
import android.util.Printer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Collects everything the engine emits; answers layout queries with canned values. */
open class RecordingDelegate : SandboxEngine.Delegate {
    val loaded = CountDownLatch(1)
    val ops: MutableList<String> = Collections.synchronizedList(ArrayList())
    val logs: MutableList<Pair<String, String>> = Collections.synchronizedList(ArrayList())
    val diags: MutableList<Pair<String, String>> = Collections.synchronizedList(ArrayList())
    val queries: MutableList<String> = Collections.synchronizedList(ArrayList())
    val bridge: MutableList<Pair<String, String>> = Collections.synchronizedList(ArrayList())
    val actions: MutableList<WidgetAction> = Collections.synchronizedList(ArrayList())
    val storage: MutableList<Pair<String, Map<String, String>>> = Collections.synchronizedList(ArrayList())
    val resizeAnchors: MutableList<String> = Collections.synchronizedList(ArrayList())
    val watches: MutableList<Boolean> = Collections.synchronizedList(ArrayList())
    /** The requests that reached the client and the texts it was asked to copy. */
    val requests: MutableList<WidgetRequest> = Collections.synchronizedList(ArrayList())
    val copied: MutableList<String> = Collections.synchronizedList(ArrayList())
    /** The client's side of a request: called on the engine thread; null leaves the delegate's default, which refuses every request (`unsupported`). */
    @Volatile var answer: ((WidgetRequest, (Result<WidgetRequestAnswer>) -> Unit) -> Unit)? = null
    /** The wallet calls that reached the client, and its side of them (null: the default, which refuses every call). */
    val walletCalls: MutableList<WalletCall> = Collections.synchronizedList(ArrayList())
    @Volatile var wallet: ((WalletCall, (Result<WidgetRequestAnswer>) -> Unit) -> Unit)? = null
    @Volatile var died: String? = null
    @Volatile var frameRequests = 0

    override fun onOps(engine: SandboxEngine, json: String) { ops.add(json) }
    override fun onQuery(engine: SandboxEngine, json: String): SandboxEngine.QueryAnswer {
        queries.add(json)
        if (json.contains("\"rect\"")) return SandboxEngine.QueryAnswer("{\"x\":0,\"y\":0,\"width\":100,\"height\":20,\"top\":0,\"left\":0,\"right\":100,\"bottom\":20}", null)
        if (json.contains("\"matchMedia\"")) return SandboxEngine.QueryAnswer("{\"matches\":false}", null)
        return SandboxEngine.QueryAnswer("{}", null)
    }
    override fun onListen(engine: SandboxEngine, json: String) {}
    override fun onPreventDefault(engine: SandboxEngine, json: String) {}
    override fun onRequestFrame(engine: SandboxEngine) { frameRequests++ }
    override fun onLog(engine: SandboxEngine, level: String, text: String) { logs.add(level to text) }
    override fun onDiag(engine: SandboxEngine, kind: String, detail: String) { diags.add(kind to detail) }
    override fun onBridge(engine: SandboxEngine, method: String, argsJson: String) { bridge.add(method to argsJson) }
    override fun onAction(engine: SandboxEngine, action: WidgetAction) { actions.add(action) }
    override fun onStorageCommit(engine: SandboxEngine, area: String, values: Map<String, String>) { storage.add(area to values) }
    override fun onResizeAnchor(engine: SandboxEngine, edge: String) { resizeAnchors.add(edge) }
    override fun onWatchPublicData(engine: SandboxEngine, on: Boolean) { watches.add(on) }
    override fun onRequest(engine: SandboxEngine, request: WidgetRequest, respond: (Result<WidgetRequestAnswer>) -> Unit) {
        val a = answer ?: return super.onRequest(engine, request, respond)
        requests.add(request)
        a(request, respond)
    }
    override fun onWallet(engine: SandboxEngine, call: WalletCall, respond: (Result<WidgetRequestAnswer>) -> Unit) {
        val w = wallet ?: return super.onWallet(engine, call, respond)
        walletCalls.add(call)
        w(call, respond)
    }
    override fun onCopyText(engine: SandboxEngine, text: String) { copied.add(text) }
    override fun onLoaded(engine: SandboxEngine) { loaded.countDown() }
    override fun onStats(engine: SandboxEngine, stats: SandboxStats) {}
    override fun onDied(engine: SandboxEngine, reason: String) { died = reason; loaded.countDown() }
}

@RunWith(AndroidJUnit4::class)
class SandboxEngineTest {
    private val canary = "http://10.0.2.2:9999/hit/android-unit"
    private val resources = SandboxResources.get(InstrumentationRegistry.getInstrumentation().targetContext)

    /** The product's memory limit (8 MB) unless [configure] changes it. */
    private fun run(html: String, cpuLimitMs: Double = 2000.0, timeoutS: Long = 20, configure: (SandboxConfig) -> Unit = {}): Pair<RecordingDelegate, SandboxEngine> {
        val d = RecordingDelegate()
        val config = SandboxConfig(innerWidth = 400.0, innerHeight = 300.0, devicePixelRatio = 2.0, theme = "light", cpuLimitMs = cpuLimitMs)
        configure(config)
        val engine = SandboxEngine(html, config, resources)
        engine.delegate = d
        engine.start()
        assertTrue("engine did not load in time", d.loaded.await(timeoutS, TimeUnit.SECONDS))
        return d to engine
    }

    /** The shipped .qbc blobs load against the .js next to them (a stale pair fails here, not on a phone); a CDN script is an external script like any other (profile 1 bundles no libraries). */
    @Test
    fun shippedBytecodeIsUsedAndNoLibraryIsBundled() {
        assertNotNull("pnpm build:android ships sandbox-bundle.qbc", resources.sandboxBytecode)
        assertNotNull("pnpm build:android ships validator-bundle.qbc", resources.validatorBytecode)
        val (d, engine) = run("<canvas id=\"c\"></canvas><script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script><script>console.log(typeof Chart);</script>")
        engine.thread.sync {}
        assertNull(d.died)
        assertTrue(d.diags.toString(), d.diags.any { it.first == "boot" && it.second.contains("(bytecode)") })
        assertTrue(d.diags.toString(), d.diags.any { it.first == "script-skipped" && it.second.contains("chart.js") })
        assertFalse("fell back to source: ${d.diags}", d.diags.any { it.first == "bytecode" })
        assertTrue(d.logs.toString(), d.logs.any { it.second == "undefined" })
        val vdiags = ArrayList<Pair<String, String>>()
        TrustedValidator("light", resources) { k, v -> vdiags.add(k to v) }.use { }
        assertFalse("validator fell back to source: $vdiags", vdiags.any { it.first == "bytecode" })
    }

    @Test
    fun bootsAndEmitsInitialTree() {
        val (d, engine) = run("<p id=\"a\">hi</p><script>document.getElementById('a').textContent = 'changed by script'; console.log('hello from quickjs');</script>")
        engine.thread.sync {}
        assertNull(d.died)
        val all = d.ops.joinToString()
        assertTrue("initial tree should be sent", all.contains("\"tag\":\"p\""))
        assertTrue("script mutation should be sent", all.contains("changed by script"))
        assertTrue(d.logs.any { it.first == "log" && it.second == "hello from quickjs" })
        assertFalse("no errors: ${d.logs}", d.logs.any { it.first == "error" })
        assertTrue("boot diag: ${d.diags}", d.diags.any { it.first == "boot" })
        engine.terminate()
    }

    @Test
    fun networkApisAreStubbed() {
        val (d, engine) = run("<script>var r = []; try { new WebSocket('ws://x') } catch (e) { r.push(e.name) } fetch('$canary').catch(e => r.push(e.name)).then(() => console.log(r.join(',')));</script>")
        engine.thread.sync {}
        assertTrue("${d.logs}", d.logs.any { it.second == "NetworkDisabledError,NetworkDisabledError" })
        assertTrue(d.diags.any { it.first == "network-blocked" })
        engine.terminate()
    }

    @Test
    fun cpuLimitKillsInfiniteLoop() {
        val (d, engine) = run("<script>for(;;){}</script>", cpuLimitMs = 200.0)
        engine.thread.sync {}
        assertNotNull(d.died)
        assertTrue(d.died ?: "", d.died!!.contains("CPU limit"))
        engine.terminate()
    }

    @Test
    fun memoryLimitKillsAllocationLoop() {
        val (d, engine) = run("<p>mem</p><script>var a = []; for (;;) a.push(new Array(100000).fill(1));</script>", cpuLimitMs = 10_000.0) { it.memoryLimitBytes = 24L shl 20 }
        engine.thread.sync {}
        assertTrue("bundle should have booted: ${d.diags}", d.diags.any { it.first == "boot" })
        assertNotNull("widget should be dead: ${d.logs}", d.died)
        assertTrue(d.died ?: "", d.died!!.contains("Memory limit"))
        engine.terminate()
    }

    /** The default heap (8 MB) boots the bundle and a widget, and stops an allocation loop at the cap (docs/decisions.md, "Strict limits from the start"). */
    @Test
    fun defaultMemoryLimitIsEightMegabytes() {
        val (d, engine) = run("<p>mem</p><script>var a = []; for (;;) a.push(new Array(10000).fill(1));</script>", cpuLimitMs = 10_000.0)
        engine.thread.sync {}
        assertTrue("bundle should have booted: ${d.diags}", d.diags.any { it.first == "boot" })
        assertEquals("${d.logs}", "Memory limit exceeded (8 MB)", d.died)
        engine.terminate()
    }

    @Test
    fun layoutQueryRoundTrip() {
        val (d, engine) = run("<div id=\"d\">x</div><script>console.log('w=' + document.getElementById('d').getBoundingClientRect().width);</script>")
        engine.thread.sync {}
        assertTrue(d.queries.any { it.contains("\"rect\"") })
        assertTrue("${d.logs}", d.logs.any { it.second == "w=100" })
        engine.terminate()
    }

    @Test
    fun inputsBeforeStartAreBufferedNotCrashing() {
        // The renderer reports its size before `ready`; WidgetHost forwards it before start().
        val d = RecordingDelegate()
        val engine = SandboxEngine("<script>console.log('w=' + innerWidth + ' theme=' + TelegramWidget.theme);</script>", SandboxConfig(400.0, 300.0, 2.0, "light"), resources)
        engine.delegate = d
        engine.viewport(360.0, 200.0, 3.0)
        engine.theme("dark")
        engine.frame(1.0)
        engine.start()
        assertTrue(d.loaded.await(20, TimeUnit.SECONDS))
        engine.thread.sync {}
        assertNull(d.died)
        assertTrue("boot sees the initial config: ${d.logs}", d.logs.any { it.second == "w=400 theme=light" })
        engine.terminate()
    }

    /** `maxTimers` reaches the shim through the boot config: the 9th timer throws RangeError inside the widget, the first 8 still fire, the engine backstop is never needed. */
    @Test
    fun timerLimitIsEnforced() {
        val (d, engine) = run(
            "<script>var fired = 0, err = ''; for (var i = 0; i < 8; i++) setTimeout(function () { if (++fired === 8) console.log('fired=' + fired); }, 5);" +
                "try { setTimeout(function () {}, 5); } catch (e) { err = e.name; } try { setInterval(function () {}, 10); } catch (e) { err += ',' + e.name; } console.log('err=' + err);</script>",
        ) { it.maxTimers = 8 }
        val deadline = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < deadline && !d.logs.any { it.second == "fired=8" }) Thread.sleep(50)
        assertTrue("${d.logs}", d.logs.any { it.second == "err=RangeError,RangeError" })
        assertTrue("${d.logs}", d.logs.any { it.second == "fired=8" })
        assertTrue("${d.diags}", d.diags.count { it.first == "timer-limit" } == 1)
        assertFalse("the engine backstop must not be needed: ${d.diags}", d.diags.any { it.first == "timer-limit-enforced" })
        assertNull(d.died)
        engine.terminate()
    }

    /**
     * A `timer` host call with an id that already has a timer replaces it (worker.ts, sandbox_engine.cpp). The widget replaces
     * JSON.stringify, which the shim builds the call with (`[id, delay]`), so 1000 timers all ask for the id of its one
     * interval: only that interval runs (one tick, not one per timer), a replaced id does not count against `maxTimers` (no
     * `timer-limit-enforced` with the glue's map full), and the engine thread's queue holds no more than `maxTimers` timers:
     * a replaced or cleared one leaves it at once, not at its time (up to 24 h).
     */
    @Test
    fun aTimerIdAskedForAgainReplacesItsTimer() {
        val html = """
            <script>
            var S = JSON.stringify, to = 0, ticks = 0, noop = function () {};
            var K = setInterval(function () { console.log('tick ' + (++ticks)); }, 1000);
            // 1000 timers set and cleared as usual, with the longest delay
            for (var i = 0; i < 1000; i++) clearTimeout(setTimeout(noop, 86400000));
            // the shim's timer call with the id `to`, whatever the shim's own id
            JSON.stringify = function (v) {
              return Array.isArray(v) && v.length === 2 && typeof v[0] === 'number' && typeof v[1] === 'number' ? S([to, v[1]]) : S.apply(JSON, arguments);
            };
            // 1000 timers on K's id; the shim clears each by its own id, which the glue does not know
            to = K;
            for (var j = 0; j < 1000; j++) clearTimeout(setTimeout(noop, 1000));
            // the glue's map full: K and 7 ids the shim does not know; then K's id once more, at the cap
            for (var f = 1; f < 8; f++) { to = 100000 + f; clearTimeout(setTimeout(noop, 60000)); }
            to = K;
            clearTimeout(setTimeout(noop, 1000));
            JSON.stringify = S;
            console.log('set');
            </script>
        """.trimIndent()
        val (d, engine) = run(html) { it.maxTimers = 8 }
        fun ticks() = d.logs.count { it.second.startsWith("tick ") }
        assertTrue("${d.logs}", d.logs.any { it.second == "set" })
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline && ticks() == 0) Thread.sleep(20)
        // The next tick is 1 s away; every timer replaced on K's id was due with the first one.
        Thread.sleep(400)
        val (ticked, dump) = engine.thread.sync {
            val lines = ArrayList<String>()
            Looper.myLooper()!!.dump(Printer { lines.add(it) }, "")
            ticks() to lines.joinToString("\n")
        }
        assertEquals("one interval, one tick: ${d.logs.filter { it.second.startsWith("tick ") }.takeLast(3)}", 1, ticked)
        assertFalse("a replaced id does not count against maxTimers: ${d.diags}", d.diags.any { it.first == "timer-limit-enforced" || it.first == "timer-limit" })
        // K's next tick and the 7 ids at 60 s; nothing else is pending on the engine thread between two tasks.
        val queued = Regex("Total messages: (\\d+)").find(dump)?.groupValues?.get(1)?.toInt()
        assertNotNull("Looper.dump has no message count:\n$dump", queued)
        assertTrue("$queued messages queued, more than maxTimers=8:\n${dump.take(2000)}", queued!! <= 8)
        assertNull(d.died)
        engine.terminate()
    }

    /** A 100 ms interval burning 40 ms per tick is legal under cpuLimitMs alone; the quota (100 ms/s here) defers ticks instead of killing, reports `cpu-throttled`, and a click still gets through. */
    @Test
    fun cpuQuotaThrottlesGreedyTimer() {
        val (d, engine) = run(
            "<p id=\"p\">x</p><script>var ticks = 0; setInterval(function () { var t = performance.now(); while (performance.now() - t < 40) {} ticks++; }, 100);" +
                "document.addEventListener('click', function () { console.log('click at tick ' + ticks); });</script>",
        ) { it.cpuQuotaMs = 100.0 }
        Thread.sleep(2500)
        engine.event("{\"type\":\"click\",\"targetId\":2}")
        engine.thread.sync {}
        assertNull(d.died)
        assertTrue("${d.diags}", d.diags.any { it.first == "cpu-throttled" })
        val click = d.logs.lastOrNull { it.second.startsWith("click at tick") }
        assertNotNull("events run under throttling: ${d.logs}", click)
        val ticks = click!!.second.substringAfterLast(' ').toInt()
        // Unthrottled the interval would tick ~25 times in 2.5 s; 100 ms/s allows 2–3 per second.
        assertTrue("$ticks ticks: ${d.logs}", ticks in 2..11)
        engine.terminate()
    }

    /** Hidden: frames are neither requested nor delivered; on return the one pending request is made and the shim saw `visibilitychange` both ways. */
    @Test
    fun hiddenEngineDeliversNoFrames() {
        val (d, engine) = run(
            "<script>var n = 0; function step() { n++; console.log('frame ' + n); requestAnimationFrame(step); } requestAnimationFrame(step);" +
                "document.addEventListener('visibilitychange', function () { console.log('vis=' + document.visibilityState); });</script>",
        )
        engine.thread.sync {}
        assertEquals(1, d.frameRequests)
        engine.frame(1.0)
        engine.thread.sync {}
        assertEquals(2, d.frameRequests)
        assertTrue(d.logs.any { it.second == "frame 1" })
        engine.setVisible(false)
        engine.frame(2.0) // in flight when the widget was hidden: dropped
        engine.thread.sync {}
        assertEquals(2, d.frameRequests)
        assertFalse(d.logs.any { it.second == "frame 2" })
        assertTrue("${d.logs}", d.logs.any { it.second == "vis=hidden" })
        engine.setVisible(true)
        engine.thread.sync {}
        assertEquals("the pending request is made on return", 3, d.frameRequests)
        assertTrue("${d.logs}", d.logs.any { it.second == "vis=visible" })
        engine.frame(3.0)
        engine.thread.sync {}
        assertTrue(d.logs.any { it.second == "frame 2" })
        assertNull(d.died)
        engine.terminate()
    }

    /** Hidden: a 50 ms interval ticks at most once per second; visible again it runs at full rate. */
    @Test
    fun hiddenTimersAreCoalescedToOneHertz() {
        val (d, engine) = run("<script>var t = 0; setInterval(function () { t++; console.log('tick ' + t); }, 50);</script>")
        engine.setVisible(false)
        Thread.sleep(1600)
        val hiddenTicks = engine.thread.sync { d.logs.count { it.second.startsWith("tick") } }
        assertTrue("$hiddenTicks ticks while hidden: ${d.logs}", hiddenTicks in 1..2)
        engine.setVisible(true)
        Thread.sleep(1600)
        val visibleTicks = engine.thread.sync { d.logs.count { it.second.startsWith("tick") } } - hiddenTicks
        assertTrue("$visibleTicks ticks after return: ${d.logs}", visibleTicks >= 5)
        assertNull(d.died)
        engine.terminate()
    }

    /** Hidden: the timers still run in the order of their delays (all at about 1 s; setTimeout 20, 10, 0 → 0, 10, 20). */
    @Test
    fun hiddenTimersKeepTheirOrder() {
        val (d, engine) = run("<script>document.addEventListener('visibilitychange', function () { if (!document.hidden) return; [20, 10, 0].forEach(function (d) { setTimeout(function () { console.log('order ' + d); }, d); }); });</script>")
        engine.setVisible(false)
        // Hidden timers fire at the next 1 Hz wake-up after their delay: up to 2 s.
        val orders = { engine.thread.sync { d.logs.map { it.second }.filter { it.startsWith("order ") }.map { it.removePrefix("order ") } } }
        val deadline = System.currentTimeMillis() + 4000
        while (System.currentTimeMillis() < deadline && orders().size < 3) Thread.sleep(50)
        val order = orders()
        assertEquals("${d.logs}", listOf("0", "10", "20"), order)
        assertNull(d.died)
        engine.terminate()
    }

    /** The 65th live timer throws RangeError with the default `maxTimers` (64). */
    @Test
    fun sixtyFifthTimerThrowsByDefault() {
        val (d, engine) = run("<script>var n = 0; try { for (; n < 100; n++) setTimeout(function () {}, 60000); } catch (e) { console.log(e.name + ' at ' + n); }</script>")
        engine.thread.sync {}
        assertTrue("${d.logs}", d.logs.any { it.second == "RangeError at 64" })
        assertFalse("the engine backstop must not be needed: ${d.diags}", d.diags.any { it.first == "timer-limit-enforced" })
        assertNull(d.died)
        engine.terminate()
    }

    /** The boot config carries the policy: under strict the shim refuses the 4001st canvas command of a task with RangeError (the validator's per-send cap), under compat it draws on. */
    @Test
    fun canvasCommandCapReachesTheShim() {
        val html = "<canvas id=\"c\"></canvas><script>var x = document.getElementById('c').getContext('2d');" +
            "try { for (var i = 0; i < 4001; i++) x.fillRect(i, 0, 1, 1); console.log('drew all'); } catch (e) { console.log(e.name + ': ' + e.message); }</script>"
        val (d, engine) = run(html)
        engine.thread.sync {}
        assertTrue("${d.logs}", d.logs.any { it.second == "RangeError: Canvas limit exceeded: more than 4000 commands for one canvas in one task (max 4000)" })
        assertNull(d.died)
        engine.terminate()
        val (c, compat) = run(html) { it.policy = "compat" }
        compat.thread.sync {}
        assertTrue("${c.logs}", c.logs.any { it.second == "drew all" })
        compat.terminate()
    }

    /**
     * The main-thread window (`mainThreadQuotaMs`): every op the engine forwards costs the host 25 µs, so 40 text
     * updates per frame cost 1 ms of it. At 0.5 ms per second the boot's ops have spent the window and frames are
     * deferred (`cpu-throttled`, the main-thread budget), the JS quota untouched; at the default 100 ms they all run.
     */
    @Test
    fun mainThreadWindowDefersFrames() {
        val html = "<div id=\"list\"></div><script>var list = document.getElementById('list'); for (var i = 0; i < 40; i++) list.appendChild(document.createElement('span'));" +
            "var frames = 0; function frame() { frames++; for (var i = 0; i < list.children.length; i++) list.children[i].textContent = String(frames * 100 + i); console.log('frame ' + frames); requestAnimationFrame(frame); } requestAnimationFrame(frame);</script>"
        /** The recorder, the frames the widget ran and the seconds the 60 frames took to send. */
        fun frames(quotaMs: Double): Triple<RecordingDelegate, Int, Double> {
            val (d, engine) = run(html) { it.mainThreadQuotaMs = quotaMs }
            val t0 = System.nanoTime()
            for (i in 0 until 60) {
                engine.frame(i * 25.0)
                Thread.sleep(25)
            }
            engine.thread.sync {}
            val seconds = (System.nanoTime() - t0) / 1e9
            val n = d.logs.count { it.second.startsWith("frame ") }
            assertNull(d.died)
            engine.terminate()
            return Triple(d, n, seconds)
        }
        // About one frame per second: each one spends the window until its bucket leaves it.
        val (tight, tightFrames, seconds) = frames(0.5)
        assertTrue("$tightFrames frames in $seconds s: ${tight.diags}", tightFrames <= seconds.toInt() + 2)
        assertTrue("${tight.diags}", tight.diags.any { it.first == "cpu-throttled" && it.second.contains("main-thread budget used by layout queries and ops") })
        val (open, openFrames, _) = frames(100.0)
        assertTrue("$openFrames frames: ${open.diags}", openFrames >= 20)
        assertFalse("${open.diags}", open.diags.any { it.first == "cpu-throttled" && it.second.contains("main-thread") })
    }

    /** The strict limits' defaults (docs/decisions.md, "Strict limits from the start"), the same in the engine's config and the host's options. */
    @Test
    fun strictLimitDefaults() {
        val config = SandboxConfig(innerWidth = 1.0, innerHeight = 1.0, devicePixelRatio = 1.0, theme = "light")
        assertEquals(8L * 1024 * 1024, config.memoryLimitBytes)
        assertEquals(64, config.maxTimers)
        assertEquals(100.0, config.mainThreadQuotaMs, 0.0)
        assertEquals(300.0, config.cpuQuotaMs, 0.0)
        val options = WidgetHost.Options()
        assertEquals(config.memoryLimitBytes, options.memoryLimitBytes)
        assertEquals(config.maxTimers, options.maxTimers)
        assertEquals(config.mainThreadQuotaMs, options.mainThreadQuotaMs, 0.0)
        assertEquals(4096, SdkPolicy.STORAGE_QUOTA_BYTES)
        assertEquals(128, CpuBudget.QUERY_LIMIT)
        assertEquals(1000.0, CpuBudget.QUERY_BUDGET_MS, 0.0)
    }

    @Test
    fun policyIsStrictByDefaultAndFailsClosed() {
        assertEquals("strict", SandboxConfig(innerWidth = 1.0, innerHeight = 1.0, devicePixelRatio = 1.0, theme = "light").policy)
        assertTrue(isStrictPolicy("Strict"))
        assertTrue(isStrictPolicy("compat "))
        assertFalse(isStrictPolicy("compat"))
    }

    /** `ready` from the boot script and a link from a timer (compat: no gesture gate; strict needs a tap, SdkTest) reach onAction, and the deprecated onBridge right after. */
    @Test
    fun bridgeAndTimers() {
        val (d, engine) = run("<script>TelegramWidget.ready(); setTimeout(() => TelegramWidget.openLink('https://Example.com/later'), 30);</script>") { it.policy = "compat" }
        val deadline = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < deadline && !d.actions.any { it is WidgetAction.OpenLink }) Thread.sleep(50)
        assertEquals(listOf(WidgetAction.Ready, WidgetAction.OpenLink("https://example.com/later")), d.actions.toList())
        assertEquals("${d.bridge}", listOf("ready", "openLink"), d.bridge.map { it.first })
        assertTrue("${d.bridge}", d.bridge[1].second.contains("example.com"))
        engine.terminate()
    }

    @Test
    fun intlGoesThroughTheHost() {
        val (d, engine) = run("<script>console.log([new Intl.NumberFormat('en-US').format(1234567.891), (1234.5).toLocaleString('de-DE'), new Intl.PluralRules('en').select(1), new Intl.ListFormat('en').format(['a','b','c'])].join('|'));</script>")
        engine.thread.sync {}
        val line = d.logs.firstOrNull { it.first == "log" }?.second ?: ""
        assertTrue(line, line.startsWith("1,234,567.891|1.234,5|one|a, b, and c"))
        engine.terminate()
    }
}

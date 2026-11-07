package org.telegram.widgetsandbox.demo

// Renderer bench: WebView vs Blitz on start-up and memory, the Android half of
// the question docs/android-renderer-bench.md answers. N widgets are created
// one after another the way a chat scrolled to its newest message creates
// them (each after the previous one loaded, the list scrolled to it), all kept
// alive and attached, then held (the Mac-side script takes `dumpsys meminfo`
// of every process of the package meanwhile — the WebView renderer process is
// an isolated uid the app cannot see), then destroyed. Per widget: the main-thread
// cost of the constructor (for the first WebView of a process that is the
// Chromium provider load), renderer ready / first content / loaded clocks,
// the late frame time and the worst frame while it was starting. Per phase:
// the process PSS breakdown.
//
//   adb shell am start -n org.telegram.widgetsandbox.demo/.MainActivity --es autorun bench \
//       --es renderer webview|blitz|litehtml|none --ei count 20 [--ez prewarm true] [--ei hold 8] [--ez pools true --ei prewarmValidators 4]
// (`none`: NullRenderer below — engine + validator only, so a renderer's own cost is the difference.)
//
// Report: files/bench-report.json (scripts/android-bench.sh pulls it).

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.view.View
import org.telegram.widgetsandbox.Renderer
import org.telegram.widgetsandbox.RendererFactory
import android.util.Log
import android.view.Choreographer
import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.webkit.WebViewCompat
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "TwidgetBench"

/**
 * The `none` renderer: answers `ready`, reports one fixed size and drops everything else. A widget on it costs
 * exactly the engine (QuickJS + sandbox bundle) and the validator context, so renderer costs can be read as a difference.
 */
private class NullRenderer(context: Context, private val widthDp: Double) : Renderer {
    override val view: View = View(context)
    override var onMessage: ((String) -> Unit)? = null
    override var onDiag: ((String, String) -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())
    override fun load() {
        main.post {
            onMessage?.invoke("{\"type\":\"ready\"}")
            onMessage?.invoke("{\"type\":\"size\",\"width\":$widthDp,\"height\":120,\"innerWidth\":$widthDp,\"innerHeight\":120,\"dpr\":2.625}")
        }
    }
    override fun send(json: String) {
        // Sync layout queries must be answered or the engine waits 3 s on each.
        if (json.startsWith("{\"type\":\"query\"")) {
            val qid = Regex("\"qid\":(\\d+)").find(json)?.groupValues?.get(1) ?: return
            main.post { onMessage?.invoke("{\"type\":\"result\",\"qid\":$qid,\"result\":null}") }
        }
    }
    override fun evaluate(script: String, callback: ((String?) -> Unit)?) { callback?.invoke(null) }
    override fun gestureDecision(wants: Boolean) {}
    override fun destroy() {}
    companion object { val factory = RendererFactory { context, _, _, widthDp -> NullRenderer(context, widthDp) } }
}

/** Main-thread frame watcher: a Choreographer callback re-posted every vsync; a late frame is main-thread work. */
private class FrameWatcher : Choreographer.FrameCallback {
    private var last = 0L
    private var running = false
    var frames = 0; var lateNs = 0L; var worstNs = 0L; var freezes = 0

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (last != 0L) {
            val d = frameTimeNanos - last
            frames += 1
            val late = d - 16_666_667L
            if (late > 0) lateNs += late
            if (d > worstNs) worstNs = d
            if (d > 100_000_000L) freezes += 1
        }
        last = frameTimeNanos
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun start() { running = true; last = 0; Choreographer.getInstance().postFrameCallback(this) }
    fun stop() { running = false }

    class Mark(val frames: Int, val lateNs: Long, val worstNs: Long, val freezes: Int)
    fun mark() = Mark(frames, lateNs, worstNs, freezes)
    /** Frames, late ms and freezes since `m`; `worstSince` is the caller's window maximum (it resets `worstNs` around a window). */
    fun since(m: Mark, worstSince: Long): JSONObject = JSONObject()
        .put("frames", frames - m.frames).put("lateMs", (lateNs - m.lateNs) / 1e6).put("worstFrameMs", worstSince / 1e6).put("freezes", freezes - m.freezes)
}

/** PSS breakdown of this process (dumpsys meminfo's summary, fresh for the caller's own process). */
private fun memory(label: String): JSONObject {
    val mi = Debug.MemoryInfo()
    Debug.getMemoryInfo(mi)
    val o = JSONObject().put("label", label).put("totalPssKb", mi.totalPss).put("nativeHeapAllocatedKb", Debug.getNativeHeapAllocatedSize() / 1024)
    for (k in listOf("java-heap", "native-heap", "code", "stack", "graphics", "private-other", "system", "total-pss", "total-swap")) {
        mi.getMemoryStat("summary.$k")?.toLongOrNull()?.let { o.put(k.replace('-', '_') + "Kb", it) }
    }
    return o
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchScreen(renderer: String, count: Int, prewarm: Boolean, holdSeconds: Int, pools: Boolean = false, prewarmValidators: Int = 4, corpusName: String = "feed") {
    val context = LocalContext.current
    val blitz = DemoRenderer.of(renderer)?.factory != null // a native prototype, only in a -Ptw.nativeRenderers=true build
    var status by remember { mutableStateOf("bench: $renderer × $count" + (if (prewarm) " (prewarmed WebView)" else "") + (if (pools) " (validator pool)" else "")) }
    val models = remember { mutableStateListOf<WidgetModel>() }
    val scroll = rememberScrollState()

    LaunchedEffect(Unit) {
        // "feed": the same corpus as the iOS chat feed (compat, then the start of the generated corpus, cycled);
        // "common": the renderer-comparison corpus, which every renderer draws the same way (corpus/common/README.md).
        val corpus = if (corpusName == "common") Corpus.commonGroups(context).flatMap { g -> Corpus.files(context, g).map { g to it } }
            else Corpus.files(context, "compat").map { "compat" to it } + Corpus.files(context, "generated/with-profile").map { "generated/with-profile" to it }
        val report = JSONObject()
            .put("renderer", renderer).put("count", count).put("prewarm", prewarm).put("pools", pools).put("prewarmValidators", if (pools) prewarmValidators else 0).put("corpus", corpusName)
            .put("device", JSONObject().put("model", Build.MODEL).put("sdk", Build.VERSION.SDK_INT).put("density", context.resources.displayMetrics.density)
                .put("webview", WebViewCompat.getCurrentWebViewPackage(context)?.let { "${it.packageName} ${it.versionName}" } ?: "?"))
        val phases = JSONArray()
        val widgets = JSONArray()
        val frames = FrameWatcher()
        // The bundles are read once per process; take them off the first widget's clock, a product reads them at launch.
        org.telegram.widgetsandbox.SandboxResources.get(context).let { it.sandboxBundle; it.rendererBundle; it.validatorBundle; it.sandboxBytecode; it.validatorBytecode }
        delay(500)
        phases.put(memory("start"))
        val options = org.telegram.widgetsandbox.WidgetHost.Options(pools = pools, sharedValidatorThread = pools)
        if (pools) {
            // What a messenger does at launch: validators built ahead on the shared thread; the pool refills itself between widgets.
            org.telegram.widgetsandbox.ValidatorPool.prewarm(org.telegram.widgetsandbox.SandboxResources.get(context), prewarmValidators)
            delay(1000)
            phases.put(memory("prewarmed-validators"))
        }
        var spare: WebView? = null
        if (prewarm && !blitz) {
            // What an app would do at launch: load the Chromium provider and start the renderer process
            // on a spare WebView that is kept around (not attached).
            val t0 = System.nanoTime()
            spare = WebView(context)
            report.put("prewarmConstructorMs", (System.nanoTime() - t0) / 1e6)
            spare.loadUrl("about:blank")
            delay(1500)
            phases.put(memory("prewarmed"))
        }
        frames.start()
        val runMark = frames.mark()
        if (renderer == "validators") {
            // Bare validator contexts, no engine, no renderer: what one TrustedValidator costs (bundle evaluation time, memory).
            val validators = ArrayList<org.telegram.widgetsandbox.TrustedValidator>()
            val resources = org.telegram.widgetsandbox.SandboxResources.get(context)
            for (i in 0 until count) {
                status = "validator ${i + 1}/$count"
                val t0 = System.nanoTime()
                validators.add(org.telegram.widgetsandbox.TrustedValidator("light", resources) { _, _ -> })
                widgets.put(JSONObject().put("i", i).put("group", "-").put("file", "validator").put("createMs", (System.nanoTime() - t0) / 1e6).put("loaded", true).put("errors", 0).put("jsMs", 0).put("jsMemoryKb", 0).put("nodes", 0).put("height", 0).put("frames", JSONObject()))
                if (i == 0) phases.put(memory("first"))
                delay(50)
            }
            report.put("createFrames", frames.since(runMark, frames.worstNs))
            frames.stop()
            phases.put(memory("live"))
            Log.i(TAG, "phase=live")
            delay(holdSeconds * 1000L)
            phases.put(memory("live-held"))
            val t1 = System.nanoTime()
            validators.forEach { it.close() }
            report.put("destroyAllMs", (System.nanoTime() - t1) / 1e6)
            delay(500); System.gc(); delay(2000)
            phases.put(memory("destroyed"))
            report.put("phases", phases).put("widgets", widgets)
            status = "done · " + writeReport(context, "bench-report.json", report.toString(2)).absolutePath
            Log.i(TAG, "phase=done")
            return@LaunchedEffect
        }
        for (i in 0 until count) {
            val (group, file) = corpus[i % corpus.size]
            status = "creating ${i + 1}/$count $file"
            val mark = frames.mark()
            val worstBefore = frames.worstNs
            frames.worstNs = 0
            val t0 = System.nanoTime()
            val model = WidgetModel(context, Corpus.html(context, group, file), demoTheme(false), 360.0, options, renderer = DemoRenderer.of(renderer) ?: DemoRenderer.WEBVIEW, rendererFactory = if (renderer == "none") NullRenderer.factory else null)
            val createMs = (System.nanoTime() - t0) / 1e6
            models.add(model)
            // Let the new bubble be composed and attached before waiting on it, then keep the list at the newest message.
            delay(16)
            scroll.scrollTo(scroll.maxValue)
            val deadline = System.nanoTime() + 15_000_000_000L
            while (System.nanoTime() < deadline && !model.diagnostics.loaded && model.diagnostics.dead == null) delay(20)
            delay(200)
            scroll.scrollTo(scroll.maxValue)
            val d = model.diagnostics
            val worstWindow = frames.worstNs
            if (worstBefore > frames.worstNs) frames.worstNs = worstBefore
            widgets.put(
                JSONObject().put("i", i).put("group", group).put("file", file)
                    .put("createMs", createMs).put("rendererReadyMs", d.rendererReadyMs ?: JSONObject.NULL).put("firstContentMs", d.firstContentMs ?: JSONObject.NULL)
                    .put("loadedMs", d.loadedMs ?: JSONObject.NULL).put("loaded", d.loaded).put("dead", d.dead ?: JSONObject.NULL)
                    .put("errors", d.logs.count { it.first == "error" }).put("jsMs", d.stats.jsMs).put("jsMemoryKb", d.stats.memoryBytes / 1024)
                    .put("nodes", d.nodes).put("height", d.height).put("prewarmed", d.prewarmed).put("frames", frames.since(mark, worstWindow)),
            )
            if (i == 0) phases.put(memory("first"))
        }
        report.put("createFrames", frames.since(runMark, frames.worstNs))
        frames.stop()
        if (pools) report.put("pool", org.telegram.widgetsandbox.ValidatorPool.status)
        phases.put(memory("live"))
        status = "live: $count widgets, holding ${holdSeconds}s"
        Log.i(TAG, "phase=live")
        delay(holdSeconds * 1000L)
        phases.put(memory("live-held"))
        status = "destroying"
        val t1 = System.nanoTime()
        models.forEach { it.destroy() }
        report.put("destroyAllMs", (System.nanoTime() - t1) / 1e6)
        models.clear()
        spare?.destroy()
        delay(500)
        System.gc()
        delay(2000)
        phases.put(memory("destroyed"))
        report.put("phases", phases).put("widgets", widgets)
        val file = writeReport(context, "bench-report.json", report.toString(2))
        status = "done · ${file.absolutePath}"
        Log.i(TAG, "phase=done")
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Renderer bench · $renderer", fontSize = 16.sp) }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(scroll).padding(12.dp)) {
            Text(status, fontSize = 12.sp)
            for (m in models) key(m) { Column(modifier = Modifier.padding(vertical = 6.dp)) { ChatBubble(m, scroll) } }
        }
    }
}

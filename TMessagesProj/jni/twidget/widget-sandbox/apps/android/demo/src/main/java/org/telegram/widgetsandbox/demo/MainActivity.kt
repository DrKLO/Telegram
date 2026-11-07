package org.telegram.widgetsandbox.demo

import org.telegram.widgetsandbox.WidgetHost
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // `adb shell am start ... --es autorun attacks|generated|compat|htmlbubbles|common|bench [--es canary host:port]`
        // starts a runner without touching the screen (used for the acceptance runs).
        val autorun = intent.getStringExtra("autorun")
        val canary = intent.getStringExtra("canary")
        // `--es open <group>/<file> [--es renderer webview|blitz|litehtml]` opens one widget directly (dev loop for the
        // native renderers); `--ez blitz true` is the old spelling of `--es renderer blitz`.
        val open = intent.getStringExtra("open")
        val prefs = getSharedPreferences("demo", MODE_PRIVATE)
        if (intent.hasExtra("blitz")) prefs.edit().putString(DemoRenderer.PREF, (if (intent.getBooleanExtra("blitz", false)) DemoRenderer.BLITZ else DemoRenderer.WEBVIEW).name).apply()
        DemoRenderer.of(intent.getStringExtra("renderer"))?.let { prefs.edit().putString(DemoRenderer.PREF, it.name).apply() }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                val nav = rememberNavController()
                val openRoute = open?.let { val i = it.lastIndexOf('/'); if (i > 0) "widget/${enc(it.substring(0, i))}/${enc(it.substring(i + 1))}" else null }
                NavHost(nav, startDestination = openRoute ?: when (autorun) { "attacks" -> "attacks?auto=1"; "generated" -> "run/generated?auto=1"; "compat" -> "run/compat?auto=1"; "htmlbubbles" -> "run/htmlbubbles?auto=1"; "common" -> "run/common?auto=1"; "bench" -> "bench"; else -> "home" }) {
                    // `--es autorun bench --es renderer webview|blitz --ei count N [--ez prewarm true] [--ei hold 8] [--es corpus feed|common]`: renderer start-up / memory bench (Bench.kt).
                    composable("bench") { BenchScreen(intent.getStringExtra("renderer") ?: "webview", intent.getIntExtra("count", 10), intent.getBooleanExtra("prewarm", false), intent.getIntExtra("hold", 8), intent.getBooleanExtra("pools", false), intent.getIntExtra("prewarmValidators", 4), intent.getStringExtra("corpus") ?: "feed") }
                    composable("home") { HomeScreen(nav) }
                    composable("widget/{group}/{file}") { entry ->
                        val group = URLDecoder.decode(entry.arguments?.getString("group") ?: "", "UTF-8")
                        val file = URLDecoder.decode(entry.arguments?.getString("file") ?: "", "UTF-8")
                        WidgetScreen(group, file)
                    }
                    composable("attacks?auto={auto}") { entry -> AttackRunnerScreen(auto = entry.arguments?.getString("auto") == "1", canaryOverride = canary) }
                    composable("run/{which}?auto={auto}") { entry ->
                        val which = entry.arguments?.getString("which") ?: "generated"
                        val context = LocalContext.current
                        CorpusRunnerScreen(
                            title = when (which) { "compat" -> "Compat corpus"; "htmlbubbles" -> "Telegram demos"; "common" -> "Common-profile corpus"; else -> "Generated corpus" },
                            groups = when (which) { "compat" -> listOf("compat"); "htmlbubbles" -> Corpus.htmlbubblesGroups(context); "common" -> Corpus.commonGroups(context); else -> Corpus.generatedGroups },
                            reportName = "$which-report.json",
                            auto = entry.arguments?.getString("auto") == "1",
                        )
                    }
                }
            }
        }
    }
}

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavHostController) {
    val context = LocalContext.current
    val sections = remember {
        listOf("compat" to Corpus.files(context, "compat")) +
            Corpus.generatedGroups.map { it to Corpus.files(context, it) } +
            listOf("attacks" to Corpus.files(context, "attacks")) +
            Corpus.htmlbubblesGroups(context).map { it to Corpus.files(context, it) } +
            Corpus.commonGroups(context).map { it to Corpus.files(context, it) } +
            Corpus.safeAreaGroups(context).map { it to Corpus.files(context, it) }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Widget sandbox") }) }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item { ListItem(headlineContent = { Text("Run all attacks against the canary") }, modifier = Modifier.clickable { nav.navigate("attacks?auto=0") }) }
            item { ListItem(headlineContent = { Text("Run the generated corpus") }, modifier = Modifier.clickable { nav.navigate("run/generated?auto=0") }) }
            item { ListItem(headlineContent = { Text("Run the compat corpus") }, modifier = Modifier.clickable { nav.navigate("run/compat?auto=0") }) }
            item { ListItem(headlineContent = { Text("Run the Telegram demos (htmlbubbles-ported)") }, modifier = Modifier.clickable { nav.navigate("run/htmlbubbles?auto=0") }) }
            item { ListItem(headlineContent = { Text("Run the common-profile corpus (renderer comparison)") }, modifier = Modifier.clickable { nav.navigate("run/common?auto=0") }) }
            for ((group, files) in sections) {
                item { Text(group, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)) }
                items(files) { f ->
                    ListItem(headlineContent = { Text(f, fontSize = 14.sp) }, modifier = Modifier.clickable { nav.navigate("widget/${enc(group)}/${enc(f)}") })
                }
            }
        }
    }
}

/** A chat-bubble-sized widget with the diagnostics panel below it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetScreen(group: String, file: String) {
    val context = LocalContext.current
    // The toggles follow the user from widget to widget (and across launches), like @AppStorage in the iOS demo.
    val prefs = remember { context.getSharedPreferences("demo", android.content.Context.MODE_PRIVATE) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    /** Reference mode (dev tooling): the same file rendered natively, without the sandbox. Never for attacks. */
    val referenceAllowed = group != "attacks"
    var showReference by remember { mutableStateOf(prefs.getBoolean("reference", false)) }
    /** WebView, Blitz or litehtml (dev tooling; the sandbox and validator are the same). See DemoRenderer. */
    var renderer by remember { mutableStateOf(DemoRenderer.load(prefs)) }
    // Restart = a new WidgetHost from the same HTML; the previous one is destroyed by the DisposableEffect.
    var generation by remember { mutableStateOf(0) }
    val html = remember(group, file) { Corpus.html(context, group, file) }
    // corpus/safe-area: a message with no caption below the widget, the time over its corner (docs/sdk-v1.md §2.17).
    val model = remember(generation, renderer) {
        WidgetModel(context, html, demoTheme(dark), 360.0, if (Corpus.isSafeArea(group)) WidgetHost.Options(contentSafeArea = Corpus.timeCorner) else WidgetHost.Options(), renderer = renderer)
    }
    DisposableEffect(model) { onDispose { model.destroy() } }
    // The reference page follows `prefers-color-scheme` of its WebView context, so a theme change reloads it.
    val reference = remember(generation, dark, showReference && referenceAllowed) {
        if (showReference && referenceAllowed) ReferenceModel(context, html, if (dark) "dark" else "light") else null
    }
    DisposableEffect(reference) { onDispose { reference?.destroy() } }
    // The list's scroll: ChatBubble keeps the widget's resize anchor in it.
    val scroll = rememberScrollState()
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text(file, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }, actions = {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        TextButton(onClick = { generation++ }) { Text("Restart", fontSize = 13.sp) }
                        if (referenceAllowed) {
                            Text("Ref", fontSize = 13.sp)
                            Switch(checked = showReference, onCheckedChange = { showReference = it; prefs.edit().putBoolean("reference", it).apply() }, modifier = Modifier.padding(horizontal = 6.dp))
                        }
                        Text("Dark", fontSize = 13.sp)
                        Switch(checked = dark, onCheckedChange = { dark = it; prefs.edit().putBoolean("dark", it).apply(); model.setTheme(demoTheme(it)) }, modifier = Modifier.padding(start = 6.dp, end = 8.dp))
                    }
                })
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().background(if (dark) Color.Black else MaterialTheme.colorScheme.surfaceVariant).padding(padding).verticalScroll(scroll).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RendererPicker(renderer) { renderer = it; prefs.edit().putString(DemoRenderer.PREF, it.name).apply() }
                ChatBubble(model, scroll, timeOverCorner = Corpus.isSafeArea(group))
                DiagnosticsPanel(model)
                if (reference != null) ReferenceBubble(reference)
            }
        }
    }
}

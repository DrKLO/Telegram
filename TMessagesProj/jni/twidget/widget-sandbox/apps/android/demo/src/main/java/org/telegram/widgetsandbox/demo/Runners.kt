package org.telegram.widgetsandbox.demo

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.telegram.widgetsandbox.WidgetDiagnostics
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val PREFS = "twidget-demo"
private const val PREF_CANARY = "canary"
/** 10.0.2.2 is the emulator's alias for the host machine; on a phone use the Mac's LAN address. */
private const val DEFAULT_CANARY = "10.0.2.2:9999"

private fun httpGet(url: String): String? = try {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 3000
    c.readTimeout = 3000
    c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
} catch (e: Exception) {
    null
}

private suspend fun awaitLoaded(model: WidgetModel, seconds: Int) {
    val deadline = System.nanoTime() + seconds * 1_000_000_000L
    while (System.nanoTime() < deadline && !model.diagnostics.loaded && model.diagnostics.dead == null) delay(100)
}

internal fun writeReport(context: Context, name: String, json: String): File =
    File(context.filesDir, name).also { it.writeText(json) }

/**
 * Loads every corpus/attacks file for a few seconds and then asks the canary
 * server on the Mac how many hits it saw. The app itself talks to the canary;
 * the widgets must not.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttackRunnerScreen(auto: Boolean, canaryOverride: String?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var canary by remember { mutableStateOf(canaryOverride ?: prefs.getString(PREF_CANARY, DEFAULT_CANARY) ?: DEFAULT_CANARY) }
    var status by remember { mutableStateOf("Start the canary on the Mac: pnpm canary") }
    var hits by remember { mutableStateOf("—") }
    var running by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<WidgetModel?>(null) }
    val results = remember { mutableStateListOf<Triple<String, Int, String?>>() }
    val scope = rememberCoroutineScope()

    suspend fun run() {
        running = true
        results.clear()
        prefs.edit().putString(PREF_CANARY, canary).apply()
        val base = "http://$canary"
        val reset = withContext(Dispatchers.IO) { httpGet("$base/__reset") }
        if (reset == null) {
            status = "canary not reachable at $canary (run `pnpm canary` on the Mac)"
            running = false
            return
        }
        for (file in Corpus.files(context, "attacks")) {
            status = "loading $file"
            val model = WidgetModel(context, Corpus.html(context, "attacks", file), demoTheme(false), 360.0)
            current = model
            // Wait for the widget to load (cold start can take >1 s), then give
            // its scripts and timers a second to try their tricks.
            awaitLoaded(model, 6)
            delay(1000)
            val d = model.diagnostics
            results.add(Triple(file, blockedCount(d), d.dead))
            current = null
            model.destroy()
        }
        status = "asking the canary"
        val body = withContext(Dispatchers.IO) { httpGet("$base/__hits") }
        val list = body?.let { try { JSONArray(it) } catch (e: Exception) { null } }
        if (list != null) {
            hits = list.length().toString()
            status = if (list.length() == 0) "done — no leak reached the canary" else "LEAK: ${body.take(300)}"
        } else {
            status = "canary not reachable at $canary (run `pnpm canary` on the Mac)"
        }
        val report = JSONObject()
            .put("canary", canary)
            .put("hits", list ?: JSONArray())
            .put("files", JSONArray(results.map { JSONObject().put("file", it.first).put("blocked", it.second).put("dead", it.third ?: JSONObject.NULL) }))
        writeReport(context, "attack-report.json", report.toString(2))
        running = false
    }

    LaunchedEffect(auto) { if (auto) run() }

    Scaffold(topBar = { TopAppBar(title = { Text("Attack suite") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = canary, onValueChange = { canary = it }, label = { Text("canary host:port (10.0.2.2 = this Mac from the emulator)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(status, fontSize = 12.sp)
            Text("canary hits after run: $hits", fontSize = 16.sp, color = if (hits == "0") Color(0xFF2E7D32) else Color.Red)
            Button(onClick = { scope.launch { run() } }, enabled = !running) { Text(if (running) "Running…" else "Run all") }
            current?.let { model -> key(model) { WidgetView(model) } }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(results) { r ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(r.first, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("${r.second} blocked" + (r.third?.let { " · dead: $it" } ?: ""), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * Loads every widget of the given groups on the real Android stack and records
 * the same facts the web suite checks (minus the pixel diff, which needs a
 * reference renderer): loaded, not dead, no JS errors, plus the diagnostics.
 * The rows are written to <filesDir>/<reportName> (`adb shell run-as
 * org.telegram.widgetsandbox.demo cat files/<reportName>`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorpusRunnerScreen(title: String, groups: List<String>, reportName: String, auto: Boolean) {
    class Row(val group: String, val file: String, val loaded: Boolean, val dead: String?, val errors: List<String>, val diags: Map<String, Int>, val jsMs: Double, val nodes: Int) {
        val pass get() = loaded && dead == null && errors.isEmpty()
        fun toJson(): JSONObject = JSONObject().put("group", group).put("file", file).put("loaded", loaded).put("dead", dead ?: JSONObject.NULL)
            .put("errors", JSONArray(errors)).put("diags", JSONObject(diags)).put("jsMs", jsMs).put("nodes", nodes).put("pass", pass)
    }

    val context = LocalContext.current
    var status by remember { mutableStateOf("Loads each widget, waits for `loaded` + 1 s, records errors and diagnostics.") }
    var running by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<WidgetModel?>(null) }
    val rows = remember { mutableStateListOf<Row>() }
    val scope = rememberCoroutineScope()

    suspend fun run() {
        running = true
        rows.clear()
        for (group in groups) {
            for (file in Corpus.files(context, group)) {
                status = "loading $group/$file"
                val model = WidgetModel(context, Corpus.html(context, group, file), demoTheme(false), 360.0)
                current = model
                awaitLoaded(model, 8)
                delay(1000)
                val d: WidgetDiagnostics = model.diagnostics
                rows.add(
                    Row(
                        group, file, d.loaded, d.dead,
                        d.logs.filter { it.first == "error" }.map { it.second.lineSequence().first().take(160) },
                        d.diags.mapValues { it.value.count }, d.stats.jsMs, d.nodes,
                    ),
                )
                current = null
                model.destroy()
            }
        }
        val passed = rows.count { it.pass }
        val file = writeReport(context, reportName, JSONArray(rows.map { it.toJson() }).toString(2))
        status = "done — $passed/${rows.size} pass · ${file.absolutePath}"
        running = false
    }

    LaunchedEffect(auto) { if (auto) run() }

    Scaffold(topBar = { TopAppBar(title = { Text(title) }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(status, fontSize = 12.sp)
            Row {
                Button(onClick = { scope.launch { run() } }, enabled = !running) { Text(if (running) "Running…" else "Run all") }
                Spacer(Modifier.weight(1f))
                // Per-group tallies for a couple of groups; one total for the 12 Telegram chats.
                if (groups.size > 2) {
                    if (rows.isNotEmpty()) Text("${rows.count { it.pass }}/${rows.size}", fontSize = 12.sp, color = if (rows.all { it.pass }) Color(0xFF2E7D32) else Color.Red, modifier = Modifier.padding(start = 8.dp))
                } else for (g in groups) {
                    val rs = rows.filter { it.group == g }
                    if (rs.isNotEmpty()) {
                        Text("${g.substringAfterLast('/')}: ${rs.count { it.pass }}/${rs.size}", fontSize = 12.sp, color = if (rs.all { it.pass }) Color(0xFF2E7D32) else Color.Red, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            current?.let { model -> key(model) { WidgetView(model) } }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(rows) { r ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Row {
                            Text(if (r.pass) "✅" else "❌", fontSize = 12.sp)
                            Text(r.file, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 4.dp))
                            Text(String.format("%.0f ms · %d nodes", r.jsMs, r.nodes), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val extra = (r.dead?.let { listOf("dead: $it") } ?: emptyList()) + r.errors + r.diags.filter { it.key != "boot" && it.key != "library" }.map { "${it.key}×${it.value}" }
                        if (extra.isNotEmpty()) Text(extra.joinToString(" · "), fontSize = 10.sp, maxLines = 2, color = if (r.pass) Color(0xFFEF6C00) else Color.Red)
                    }
                }
            }
        }
    }
}

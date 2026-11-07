package org.telegram.widgetsandbox.demo

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.telegram.widgetsandbox.WidgetAction
import org.telegram.widgetsandbox.WidgetDiagnostics
import org.telegram.widgetsandbox.WidgetHost
import org.telegram.widgetsandbox.WidgetTheme
import org.telegram.widgetsandbox.RendererFactory

/**
 * The renderer the demo's widgets use (the **Renderer** picker on a widget screen, persisted as the
 * `renderer` preference): the product WebView renderer, or one of the native prototypes — Blitz
 * (renderer-blitz module, Rust) or litehtml (renderer-litehtml module, C++). The sandbox and the validator
 * are the same either way; dev tooling. `--es renderer webview|blitz|blitz_canvas|litehtml` sets it from adb.
 * `blitz_canvas` is Blitz's layout painted by android.graphics from a display list (CanvasScene.kt).
 * The prototypes are frozen (2026-10-30): they exist only in a build with -Ptw.nativeRenderers=true (NativeRenderers).
 */
enum class DemoRenderer(val title: String, val factory: RendererFactory?) {
    WEBVIEW("WebView", null), BLITZ("Blitz", NativeRenderers.blitz), BLITZ_CANVAS("Blitz·Canvas", NativeRenderers.blitzCanvas), LITEHTML("litehtml", NativeRenderers.litehtml);

    val label get() = if (this == WEBVIEW) "sandbox" else "sandbox · $title renderer"

    companion object {
        const val PREF = "renderer"
        /** The renderers in this build: WebView, plus the prototypes when they were built in. */
        val choices get() = entries.filter { it == WEBVIEW || it.factory != null }
        fun of(name: String?) = choices.firstOrNull { it.name.equals(name, ignoreCase = true) }
        /** The persisted choice; `blitz` was the Blitz switch before the picker existed. */
        fun load(prefs: android.content.SharedPreferences) = of(prefs.getString(PREF, null)) ?: (if (prefs.getBoolean("blitz", false)) of(BLITZ.name) else null) ?: WEBVIEW
    }
}

/**
 * The demo's chat theme: the colour scheme and, as `bg_color`, the colour the demo draws its bubbles in
 * (the Material `surface` of the widget screen; the UA layer paints the widget's root with it); the rest of
 * the palette and the button and text tokens take the framework's defaults. A client passes the bubble's
 * own colours, button and text (docs/sdk-v1.md §5.1).
 */
fun demoTheme(dark: Boolean) = WidgetTheme(if (dark) "dark" else "light", mapOf("bg_color" to demoBubbleHex(dark)))

/** The Material `surface` the widget screen draws its bubbles in, as `#rrggbb`. */
fun demoBubbleHex(dark: Boolean): String =
    String.format("#%06x", (if (dark) darkColorScheme() else lightColorScheme()).surface.toArgb() and 0xffffff)

/** The same bubble colour for the reference mode (dev tooling), after `Corpus/reference.css`. */
fun demoReferenceCss(): String =
    ":root{--tg-theme-bg-color:${demoBubbleHex(false)}}@media (prefers-color-scheme: dark){:root{--tg-theme-bg-color:${demoBubbleHex(true)}}}"

/** Observable façade over WidgetHost for Compose screens (= WidgetModel.swift). */
class WidgetModel(context: Context, html: String, theme: WidgetTheme, widthDp: Double, options: WidgetHost.Options = WidgetHost.Options(), val renderer: DemoRenderer = DemoRenderer.WEBVIEW, rendererFactory: RendererFactory? = null) {
    /** `rendererFactory` overrides the picker's renderer (the bench's `none` renderer, Bench.kt). */
    val host = WidgetHost(context, html, theme, widthDp, options, rendererFactory = rendererFactory ?: renderer.factory)
    var diagnostics by mutableStateOf(WidgetDiagnostics())
        private set
    /** What the bubble did with the last press it took (BubbleGestureLayout); ChatBubble shows it for a moment. */
    var bubbleNote by mutableStateOf<BubbleNote?>(null)
    private val bubbleLayout = lazy { BubbleGestureLayout(context, host.view) { bubbleNote = BubbleNote(BubbleGestureLayout.label(it)) } }
    /** host.view inside the bubble's recognizers: what ChatBubble shows. */
    val bubbleView: View by bubbleLayout

    init {
        host.onChange = { diagnostics = it }
        // The claim of each press, for the bubble's recognizers (a client's own over the widget, docs/sdk-v1.md §5.5).
        host.onGesture = { claim -> if (bubbleLayout.isInitialized()) bubbleLayout.value.claim(claim) }
        // What a client does with the actions (docs/sdk-v1.md §5.2, §5.3), reduced to dev tooling:
        // haptics play, links are shown and not opened (the panel lists every action).
        host.onAction = { action ->
            when (action) {
                is WidgetAction.Haptic -> host.view.performHapticFeedback(
                    when (action.kind) {
                        "impact" -> HapticFeedbackConstants.VIRTUAL_KEY
                        "notification" -> HapticFeedbackConstants.LONG_PRESS
                        else -> HapticFeedbackConstants.CLOCK_TICK
                    },
                )
                is WidgetAction.OpenLink -> Toast.makeText(context, "openLink: ${action.url}", Toast.LENGTH_SHORT).show()
                is WidgetAction.OpenTelegramLink -> Toast.makeText(context, "openTelegramLink: ${action.url}", Toast.LENGTH_SHORT).show()
                WidgetAction.Ready -> {}
            }
        }
    }

    fun setTheme(theme: WidgetTheme) = host.setTheme(theme)
    fun destroy() = host.destroy()

    // The owner's side of the off-screen pause: the bubble scrolled out of the window, or the
    // activity stopped, counts as hidden (the view's own attach state is tracked by the host).
    private var onScreen = true
    private var started = true
    fun setOnScreen(v: Boolean) { onScreen = v; host.setVisible(onScreen && started) }
    fun setStarted(v: Boolean) { started = v; host.setVisible(onScreen && started) }
}

/** WebView | Blitz | litehtml, the renderer picker above the bubble (= RendererPicker in DemoPlatform.swift); nothing without the prototypes. */
@Composable
fun RendererPicker(selected: DemoRenderer, onSelect: (DemoRenderer) -> Unit) {
    val choices = DemoRenderer.choices
    if (choices.size < 2) return
    SingleChoiceSegmentedButtonRow {
        choices.forEachIndexed { i, r ->
            SegmentedButton(
                selected = r == selected,
                onClick = { onSelect(r) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = choices.size),
                label = { Text(r.title, fontSize = 12.sp) },
            )
        }
    }
}

/** One note per press the bubble took (a new instance each time: ChatBubble's timer restarts). */
class BubbleNote(val text: String)

/**
 * The widget at chat-bubble width; its height follows the renderer's size reports. `bubble`: inside the bubble's
 * recognizers (ChatBubble). `scroll`: the top-down list the bubble sits in, which keeps the widget's resize anchor
 * (`TelegramWidget.setResizeAnchor`, WidgetDiagnostics.resizeAnchor; docs/sdk-v1.md §5.6): when the height of a bubble
 * on screen changes with `bottom`, the scroll offset takes the delta, so the bubble's bottom stays put and the content
 * above moves; with `top` (the default) nothing changes, the content below moves. The offset follows the layout that
 * changed the height (a frame later: onGloballyPositioned), and it is clamped by the list's ends.
 */
@Composable
fun WidgetView(model: WidgetModel, widthDp: Int = 360, bubble: Boolean = false, scroll: ScrollState? = null) {
    // The bubble's height in px at its last layout (-1 before the first).
    val laidOut = remember(model) { IntArray(1) { -1 } }
    // key: a new model (Restart) must get a new AndroidView, or the factory is never re-run and the
    // destroyed host's view stays on screen.
    key(model) {
        AndroidView(
            factory = { if (bubble) model.bubbleView else model.host.view },
            modifier = Modifier.width(widthDp.dp).height(maxOf(model.diagnostics.height, 24.0).dp)
                .onGloballyPositioned {
                    // boundsInWindow is clipped by the scrolling parents: empty means scrolled out of view.
                    val b = it.boundsInWindow()
                    val onScreen = b.width > 0f && b.height > 0f
                    model.setOnScreen(onScreen)
                    val before = laidOut[0]
                    val after = it.size.height
                    laidOut[0] = after
                    if (scroll != null && before >= 0 && after != before && onScreen && model.diagnostics.resizeAnchor == "bottom") {
                        scroll.dispatchRawDelta((after - before).toFloat())
                    }
                },
        )
    }
    @Suppress("DEPRECATION")
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> model.setStarted(true)
                Lifecycle.Event.ON_STOP -> model.setStarted(false)
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

/**
 * The widget in a chat bubble; `scroll`: the list it sits in, for the resize anchor (WidgetView). `timeOverCorner`: a
 * message with no caption below the widget, the time drawn over the widget's bottom right corner on a backing (docs/sdk-v1.md §2.17;
 * the host got the band as `contentSafeArea`); a view over the widget, so its taps are the bubble's.
 */
@Composable
fun ChatBubble(model: WidgetModel, scroll: ScrollState? = null, timeOverCorner: Boolean = false) {
    Column {
        Text(model.renderer.label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
        Box(
            // The bubble is the widget's canvas, as on a client: no padding of its own (the widget's body margin is the inset),
            // the colour demoTheme passes as bg_color, the radius clips what the widget paints.
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface),
        ) {
            WidgetView(model, bubble = true, scroll = scroll)
            if (timeOverCorner) {
                Text(
                    "12:00 ✓✓", fontSize = 11.sp, color = Color.White, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 3.dp).width(52.dp).height(18.dp)
                        .clip(RoundedCornerShape(9.dp)).background(Color.Black.copy(alpha = 0.35f)).clickable {},
                )
            }
        }
        // What a product shows under the bubble: the widget stays on screen (frozen) with a one-line reason.
        val d = model.diagnostics
        val line = d.dead?.let { "Widget stopped: " + it.lineSequence().first() } ?: d.lastError?.lineSequence()?.first()
        if (line != null) Text(line, fontSize = 10.sp, color = Color.Red, maxLines = 1, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
        // In place of the menu or the reply a client would show: what the bubble took the press for.
        model.bubbleNote?.let { note ->
            Text("bubble: ${note.text}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
            LaunchedEffect(note) {
                delay(1500)
                if (model.bubbleNote === note) model.bubbleNote = null
            }
        }
    }
}

val blockedKinds = listOf("url-blocked", "network-blocked", "navigation-blocked", "tag-dropped", "script-skipped", "css-dropped")
private val redKinds = setOf("url-blocked", "network-blocked", "navigation-blocked", "protocol-violation", "renderer-violation")

fun blockedCount(d: WidgetDiagnostics) = blockedKinds.sumOf { d.diags[it]?.count ?: 0 }

@Composable
fun DiagnosticsPanel(model: WidgetModel) {
    val d = model.diagnostics
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
    ) {
        val status = (d.dead?.let { "✖ terminated: $it" } ?: if (d.loaded) "● loaded" else "… loading") +
            if (d.resizeAnchor != "top") " · resize anchor: ${d.resizeAnchor}" else ""
        Text(status, fontSize = 12.sp, color = if (d.dead != null) Color.Red else if (d.loaded) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            String.format(
                "JS %.0f ms in %d tasks · %.1f MB · %d ops · %d nodes · %d sync queries (%.0f ms, %d cache hits) · missed %d",
                d.stats.jsMs, d.stats.tasks, d.stats.memoryBytes / 1048576.0, d.stats.ops, d.nodes, d.stats.queries, d.stats.queryMs, d.stats.queryCacheHits, d.stats.missedMutations,
            ),
            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (k in d.diags.keys.sorted()) {
            val e = d.diags[k]!!
            Text("$k ×${e.count}: ${e.details.takeLast(2).joinToString(" · ")}", fontSize = 10.sp, maxLines = 2, color = if (k in redKinds) Color.Red else Color(0xFFEF6C00))
        }
        for (a in d.actions.takeLast(10)) Text("action: $a", fontSize = 10.sp)
        for (l in d.logs.takeLast(30)) {
            Text("[${l.first}] ${l.second}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 4, color = if (l.first == "error") Color.Red else MaterialTheme.colorScheme.onSurface)
        }
    }
}

// The renderer as WidgetHost sees it: a View plus the JSON message contract of
// docs/protocol.md (host → renderer: ops / query / listen / preventDefault /
// requestFrame / theme / safeArea / policy / frozen; renderer → host: ready /
// event / gesture / result / size / resize / frame / media / limit /
// violation). A renderer of the client's own reports the paint limit through
// `limitExceeded`. `theme` carries the
// WidgetTheme (docs/sdk-v1.md §3.4) at load and on every change; a renderer
// that has no use for it ignores it, as any unknown type. `gesture` is
// `{wants, x, y, hold}` (`wants` = `y`, both always false since v0.1.3:
// vertical drags are the chat's; WidgetGestureClaim), the prototypes send
// `wants` alone and so claim nothing. RendererWebView is the product renderer;
// other implementations (the Blitz and litehtml prototypes) plug in through
// WidgetHost's `rendererFactory`. All members are main-thread.

package org.telegram.widgetsandbox

import android.content.Context
import android.view.View
import org.json.JSONObject

interface Renderer {
    val view: View
    /** Renderer → host messages (JSON), delivered on the main thread. */
    var onMessage: ((String) -> Unit)?
    var onDiag: ((String, String) -> Unit)?

    /** Starts the renderer; it announces itself with `{type:'ready'}`. */
    fun load()
    /** Host → renderer message (JSON). */
    fun send(json: String)
    /** Trusted script evaluated in the renderer (tests / diagnostics only); renderers without script answer `null`. */
    fun evaluate(script: String, callback: ((String?) -> Unit)?)
    /**
     * Answer to the last primary pointerdown's `gesture` message, its `wants` (= `y`): whether to keep the scrolling
     * parent from intercepting. Since v0.1.3 WidgetHost always passes false (a vertical drag is the list's).
     */
    fun gestureDecision(wants: Boolean)
    /**
     * The same answer as the whole claim (WidgetHost also reports it to `onGesture`); a renderer that acts on `y` alone
     * keeps this default, which since v0.1.3 always lets the parent intercept (`y` is always false).
     */
    fun gestureDecision(claim: WidgetGestureClaim) = gestureDecision(claim.y)
    /**
     * A preview (WidgetContext.preview: an inline result not sent yet) takes no input: the renderer keeps no press from
     * its parent (a tap selects the result) and WidgetHost sends it no gesture decisions. Called once, before `load`.
     * The default does nothing: a renderer that never holds presses back needs nothing.
     */
    fun setPreview(preview: Boolean) {}
    fun destroy()

    /**
     * A renderer of the client's own that counts heavy boxes (docs/decisions.md "Heavy boxes", the rule and the cases in
     * packages/core/tests/fixtures/paint-policy.json) reports a passed limit here, with the count, on the main thread:
     * before it draws that frame, and once. WidgetHost stops the widget as for memory or CPU (`WidgetDiagnostics.dead`
     * = [WidgetPaintLimit.message], the diagnostic `paint-limit` or `backdrop-limit`); the renderer draws the stopped
     * widget without the effects (no blurred shadow, filter, backdrop-filter or transparency; animations stopped). Under
     * `compat` the host ignores it. The WebView renderer sends the same `limit` message itself.
     */
    fun limitExceeded(code: WidgetPaintLimit, count: Int) {
        onMessage?.invoke(JSONObject().put("type", "limit").put("code", code.code).put("count", maxOf(0, count)).toString())
    }
}

/**
 * The paint limits a renderer enforces (packages/core/src/shared/paint-policy.ts): at most 16 heavy boxes at once (a
 * blurred box-shadow, a filter, a backdrop-filter, opacity below 1, or an animation of one of them), of which at most 2
 * with backdrop-filter.
 */
enum class WidgetPaintLimit(val code: String) {
    /** More than [MAX_HEAVY_BOXES] heavy boxes; the count is the heavy boxes'. */
    PAINT("paint-limit"),
    /** More than [MAX_BACKDROP_BOXES] boxes with backdrop-filter; the count is theirs. */
    BACKDROP("backdrop-limit");

    /** The reason the host stops the widget with, the same on every platform (`paintLimitMessage` in paint-policy.ts). */
    fun message(count: Int): String = when (this) {
        PAINT -> "Paint limit exceeded: $count boxes with shadows, filters or transparency (max $MAX_HEAVY_BOXES)"
        BACKDROP -> "Paint limit exceeded: $count boxes with backdrop-filter (max $MAX_BACKDROP_BOXES)"
    }

    companion object {
        const val MAX_HEAVY_BOXES = 16
        const val MAX_BACKDROP_BOXES = 2
        fun of(code: String): WidgetPaintLimit? = entries.firstOrNull { it.code == code }
    }
}

/** Builds a renderer for one widget: width in CSS px (= dp) and the chat's colour scheme ("light" | "dark"; the palette follows as a `theme` message). */
fun interface RendererFactory {
    fun create(context: Context, resources: SandboxResources, theme: String, widthDp: Double): Renderer
}

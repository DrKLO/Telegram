// litehtml renderer prototype: the Renderer contract of WidgetHost on the C++
// core in renderer-litehtml/ (repo root, shared with the iOS/macOS demo). The
// validated DOM is kept as a shadow tree on the C++ side and re-parsed by
// litehtml on every batch, as Telegram's HTMLBubbles does; litehtml asks this
// side for fonts, text widths and images (LitehtmlBackend) and hands back a
// display list that a plain View paints on its Canvas. No WebView, no script,
// no network: the document only ever sees validated RenderOps and `data:`
// images. Dev tooling for the three-way renderer comparison
// (docs/litehtml-renderer.md), not a product path. Mirrors BlitzRenderer.kt in
// structure and LitehtmlRenderer.swift in substance.

package org.telegram.widgetsandbox.litehtml

import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import org.telegram.widgetsandbox.Renderer
import org.telegram.widgetsandbox.RendererFactory
import org.telegram.widgetsandbox.SandboxResources
import org.json.JSONArray
import org.json.JSONObject

class LitehtmlRenderer(context: Context, theme: String, widthDp: Double) : Renderer {
    companion object {
        /** Plug into WidgetHost: `WidgetHost(..., rendererFactory = LitehtmlRenderer.factory)`. */
        val factory = RendererFactory { context: Context, _: SandboxResources, theme: String, widthDp: Double -> LitehtmlRenderer(context, theme, widthDp) }
        private const val GESTURE_DECISION_TIMEOUT_MS = 150L
    }

    private val density = context.resources.displayMetrics.density.toDouble()
    private val backend = LitehtmlBackend()
    private val initMs: Double
    private var handle: Long
    private val litehtmlView = LitehtmlView(context)
    override val view: View get() = litehtmlView
    override var onMessage: ((String) -> Unit)? = null
    override var onDiag: ((String, String) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var destroyed = false
    private var frozen = false
    private var frameCallback: Choreographer.FrameCallback? = null
    private var paintScheduled = false
    private var list: JSONArray? = null
    private var lastPaintReport = 0L
    /** Paint statistics (ms per display-list fetch + parse, count) for the diagnostics panel. */
    var paints = 0
        private set
    var paintMs = 0.0
        private set

    init {
        val t0 = System.nanoTime()
        handle = LitehtmlNative.create(widthDp, density, theme, backend)
        initMs = (System.nanoTime() - t0) / 1e6
    }

    override fun load() {
        if (handle == 0L) {
            onDiag?.invoke("renderer", "litehtml renderer failed to initialise")
            return
        }
        onDiag?.invoke("litehtml-init", String.format("%.1f ms", initMs))
        main.post {
            if (destroyed) return@post
            onMessage?.invoke("{\"type\":\"ready\"}")
            schedulePaint()
        }
    }

    override fun send(json: String) {
        if (destroyed || handle == 0L) return
        if (json.startsWith("{\"type\":\"requestFrame\"")) {
            requestFrame()
            return
        }
        if (json.startsWith("{\"type\":\"frozen\"")) frozen = true
        dispatch(LitehtmlNative.receive(handle, json))
        if (LitehtmlNative.dirty(handle)) schedulePaint()
    }

    override fun evaluate(script: String, callback: ((String?) -> Unit)?) {
        callback?.invoke(null)
    }

    /**
     * The host's answer to this renderer's `gesture` message (`wants` alone, the old vertical claim): always false since
     * v0.1.3, as vertical drags are the chat's, so the list may intercept as soon as it arrives (this prototype claims nothing).
     */
    override fun gestureDecision(wants: Boolean) = litehtmlView.decide(wants)

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        frameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        frameCallback = null
        (litehtmlView.parent as? ViewGroup)?.removeView(litehtmlView)
        LitehtmlNative.destroy(handle)
        handle = 0
        backend.release()
        list = null
    }

    private fun dispatch(arrayJson: String) {
        if (destroyed) return
        val arr = try { JSONArray(arrayJson) } catch (e: Exception) { return }
        for (i in 0 until arr.length()) {
            val msg = arr.optJSONObject(i) ?: continue
            if (msg.optString("type") == "violation") onDiag?.invoke("renderer-violation", msg.optString("detail"))
            onMessage?.invoke(msg.toString())
        }
    }

    private fun requestFrame() {
        if (frozen || frameCallback != null) return
        val cb = Choreographer.FrameCallback { nanos ->
            frameCallback = null
            if (!destroyed) onMessage?.invoke(JSONObject().put("type", "frame").put("time", nanos / 1e6).toString())
        }
        frameCallback = cb
        Choreographer.getInstance().postFrameCallback(cb)
    }

    /** Display lists are fetched once per vsync at most; the view paints the latest in onDraw. */
    private fun schedulePaint() {
        if (paintScheduled || destroyed) return
        paintScheduled = true
        Choreographer.getInstance().postFrameCallback {
            paintScheduled = false
            if (!destroyed) paint()
        }
    }

    private fun paint() {
        if (handle == 0L) return
        val t0 = System.nanoTime()
        val json = LitehtmlNative.displayList(handle)
        list = try { JSONArray(json) } catch (e: Exception) { JSONArray() }
        paintMs = (System.nanoTime() - t0) / 1e6
        paints += 1
        val now = System.currentTimeMillis()
        if (now - lastPaintReport > 1000) {
            lastPaintReport = now
            onDiag?.invoke("litehtml-paint", String.format("%.1f ms display list (%d ops), paint #%d", paintMs, list?.length() ?: 0, paints))
            try {
                val s = JSONObject(LitehtmlNative.stats(handle))
                onDiag?.invoke("litehtml-build", String.format("serialise %.2f ms, parse+layout %.1f ms in %d pass(es), %d B html, build #%d", s.optDouble("serializeMs"), s.optDouble("parseLayoutMs"), s.optInt("passes"), s.optInt("htmlBytes"), s.optInt("builds")))
            } catch (e: Exception) {}
        }
        litehtmlView.invalidate()
    }

    /** Paints the last display list (scaled to CSS px) and feeds touches to the document; the chat list's interception is handled like GestureWebView. */
    private inner class LitehtmlView(context: Context) : View(context) {
        private var decided = false
        private val timeout = Runnable { if (!decided) release() }

        private fun release() {
            decided = true
            parent?.requestDisallowInterceptTouchEvent(false)
        }

        fun decide(wants: Boolean) {
            removeCallbacks(timeout)
            if (decided) return
            if (wants) decided = true else release()
        }

        override fun onDraw(canvas: Canvas) {
            val l = list ?: return
            canvas.save()
            canvas.scale(density.toFloat(), density.toFloat())
            backend.paint(canvas, l)
            canvas.restore()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            if (handle == 0L || w == oldw) return
            dispatch(LitehtmlNative.setWidth(handle, w / density))
            if (LitehtmlNative.dirty(handle)) schedulePaint()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (handle == 0L || destroyed) return false
            val action = when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> 0
                MotionEvent.ACTION_MOVE -> 1
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> 2
                MotionEvent.ACTION_CANCEL -> 3
                else -> return false
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    decided = false
                    parent?.requestDisallowInterceptTouchEvent(true)
                    removeCallbacks(timeout)
                    postDelayed(timeout, GESTURE_DECISION_TIMEOUT_MS)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(timeout)
            }
            val indices = if (action == 1 || action == 3) (0 until event.pointerCount).toList() else listOf(event.actionIndex)
            for (i in indices) {
                val id = event.getPointerId(i)
                val primary = id == event.getPointerId(0) && (event.actionMasked != MotionEvent.ACTION_POINTER_DOWN && event.actionMasked != MotionEvent.ACTION_POINTER_UP || i == 0)
                dispatch(LitehtmlNative.touch(handle, action, event.getX(i) / density, event.getY(i) / density, id, primary))
            }
            if (LitehtmlNative.dirty(handle)) schedulePaint()
            return true
        }
    }
}

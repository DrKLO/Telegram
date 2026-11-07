// Blitz renderer prototype: the Renderer contract of WidgetHost on a Rust
// cdylib (renderer-blitz/ at the repo root, shared with the iOS/macOS demo) that lays out the validated DOM with blitz-dom and paints it
// on the CPU with vello_cpu into a Bitmap drawn by a plain View — or, with `backend = "canvas"`, records
// the same scene as a display list that CanvasScene.kt replays on the View's Canvas (platform paths,
// gradients and glyphs; the Apple twin is renderer-blitz/src/cg.rs). No WebView,
// no script, no network: the document only ever sees validated RenderOps and
// `data:` images. Dev tooling for the comparison in docs/blitz-experiment.md,
// not a product path.

package org.telegram.widgetsandbox.blitz

import android.content.Context
import android.graphics.Bitmap
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
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BlitzRenderer(context: Context, theme: String, widthDp: Double, private val backend: String = "vello") : Renderer {
    companion object {
        /** Plug into WidgetHost: `WidgetHost(..., rendererFactory = BlitzRenderer.factory)`. */
        val factory = RendererFactory { context: Context, _: SandboxResources, theme: String, widthDp: Double -> BlitzRenderer(context, theme, widthDp) }
        /** The same layout, painted by android.graphics from a display list (CanvasScene.kt). */
        val canvasFactory = RendererFactory { context: Context, _: SandboxResources, theme: String, widthDp: Double -> BlitzRenderer(context, theme, widthDp, "canvas") }
        private const val GESTURE_DECISION_TIMEOUT_MS = 150L
        /** One scratch buffer per process for the Rust → Bitmap copy (paints are main-thread, one at a time); grows to the largest raster. */
        private var scratch: ByteBuffer? = null
        private fun scratch(bytes: Int): ByteBuffer {
            val b = scratch
            if (b != null && b.capacity() >= bytes) return b
            return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder()).also { scratch = it }
        }
    }

    private val density = context.resources.displayMetrics.density.toDouble()
    private var handle: Long = BlitzNative.create(widthDp, density, theme)
    private val blitzView = BlitzView(context)
    override val view: View get() = blitzView
    override var onMessage: ((String) -> Unit)? = null
    override var onDiag: ((String, String) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var destroyed = false
    private var frozen = false
    private var frameCallback: Choreographer.FrameCallback? = null
    private var paintScheduled = false
    private var bitmap: Bitmap? = null
    private var scene: CanvasScene? = null
    private val onCanvas = backend == "canvas"
    private var lastPaintReport = 0L
    /** Paint statistics (ms per paint, count) for the diagnostics panel. */
    var paints = 0
        private set
    var paintMs = 0.0
        private set

    override fun load() {
        if (handle == 0L) {
            onDiag?.invoke("renderer", "Blitz renderer failed to initialise")
            return
        }
        if (backend != "vello") {
            if (!BlitzNative.setBackend(handle, backend)) onDiag?.invoke("renderer", "Blitz backend $backend unavailable, painting with vello")
            else onDiag?.invoke("blitz-backend", backend + if (onCanvas && android.os.Build.VERSION.SDK_INT < 31) " (API ${android.os.Build.VERSION.SDK_INT}: no Canvas.drawGlyphs, text skipped)" else "")
        }
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
        dispatch(BlitzNative.receive(handle, json))
        if (BlitzNative.dirty(handle)) schedulePaint()
    }

    override fun evaluate(script: String, callback: ((String?) -> Unit)?) {
        callback?.invoke(null)
    }

    /**
     * The host's answer to this renderer's `gesture` message (`wants` alone, the old vertical claim): always false since
     * v0.1.3, as vertical drags are the chat's, so the list may intercept as soon as it arrives (this prototype claims nothing).
     */
    override fun gestureDecision(wants: Boolean) = blitzView.decide(wants)

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        frameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        frameCallback = null
        (blitzView.parent as? ViewGroup)?.removeView(blitzView)
        BlitzNative.destroy(handle)
        handle = 0
        bitmap?.recycle()
        bitmap = null
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

    /** Paints are coalesced into Choreographer frames: one raster per vsync at most. */
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
        if (onCanvas) {
            val t0 = System.nanoTime()
            val bytes = BlitzNative.displayList(handle)
            val s = CanvasScene.parse(bytes)
            scene = s
            paintMs = (System.nanoTime() - t0) / 1e6
            paints += 1
            val now = System.currentTimeMillis()
            if (now - lastPaintReport > 1000) {
                lastPaintReport = now
                onDiag?.invoke("blitz-paint", String.format("%.1f ms display list (%d ops, %d KB), paint #%d%s", paintMs, s.size, s.bytes / 1024, paints, if (CanvasResources.fontFailures > 0) ", ${CanvasResources.fontFailures} font(s) failed" else ""))
            }
            blitzView.invalidate()
            return
        }
        val packed = BlitzNative.size(handle)
        val w = (packed ushr 32).toInt()
        val h = (packed and 0xffffffffL).toInt()
        if (w <= 0 || h <= 0) return
        var bmp = bitmap
        if (bmp == null || bmp.width != w || bmp.height != h) {
            bmp?.recycle()
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap = bmp
        }
        val buf = scratch(w * h * 4)
        buf.rewind()
        val ms = BlitzNative.render(handle, buf)
        if (ms < 0) {
            onDiag?.invoke("renderer", "Blitz paint failed ($ms)")
            return
        }
        buf.rewind()
        bmp.copyPixelsFromBuffer(buf)
        paints += 1
        paintMs = ms
        val now = System.currentTimeMillis()
        if (now - lastPaintReport > 1000) {
            lastPaintReport = now
            onDiag?.invoke("blitz-paint", String.format("%.1f ms, %d×%d px, paint #%d", ms, w, h, paints))
        }
        blitzView.invalidate()
    }

    /** Draws the last raster and feeds touches to the document; the chat list's interception is handled like GestureWebView. */
    private inner class BlitzView(context: Context) : View(context) {
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
            if (onCanvas) scene?.draw(canvas) else bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            if (handle == 0L || w == oldw) return
            dispatch(BlitzNative.setWidth(handle, w / density))
            if (BlitzNative.dirty(handle)) schedulePaint()
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
                dispatch(BlitzNative.touch(handle, action, event.getX(i) / density, event.getY(i) / density, id, primary))
            }
            if (BlitzNative.dirty(handle)) schedulePaint()
            return true
        }
    }
}

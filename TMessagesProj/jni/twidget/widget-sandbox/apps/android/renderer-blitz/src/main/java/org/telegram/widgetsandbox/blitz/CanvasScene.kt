// The Android executor of the Blitz display list (renderer-blitz/src/dl.rs at the repo root, format in its
// header): the scene blitz-paint produced from the validated DOM, drawn with android.graphics instead of vello —
// paths, gradients, images, layers, box shadows, and glyphs through Canvas.drawGlyphs with a Font built from the
// very bytes parley shaped with, so text and colour emoji are the system's. Dev tooling for the comparison in
// docs/blitz-experiment.md ("PaintScene on CoreGraphics" has the Apple twin), not a product path.

package org.telegram.widgetsandbox.blitz

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.fonts.Font
import android.graphics.fonts.FontVariationAxis
import android.os.Build
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Fonts and images by display-list id, shared by every scene in the process (the ids are process-wide too). */
internal object CanvasResources {
    private val fonts = HashMap<String, Font?>()
    private val images = HashMap<Long, Bitmap?>()
    var fontFailures = 0
        private set

    fun font(id: Long, face: Int, axes: Array<FontVariationAxis>): Font? {
        if (Build.VERSION.SDK_INT < 29) return null
        val key = "$id/$face/" + axes.joinToString(",") { it.tag + "=" + it.styleValue }
        return fonts.getOrPut(key) {
            try {
                val bytes = BlitzNative.fontBytes(id) ?: return@getOrPut null
                val b = Font.Builder(bytes).setTtcIndex(face)
                if (axes.isNotEmpty()) b.setFontVariationSettings(axes)
                b.build()
            } catch (e: Exception) {
                fontFailures += 1
                null
            }
        }
    }

    fun image(id: Long, width: Int, height: Int): Bitmap? = images.getOrPut(id) {
        val px = BlitzNative.imagePixels(id)
        if (px.size < width * height * 4 || width <= 0 || height <= 0) return@getOrPut null
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { it.copyPixelsFromBuffer(ByteBuffer.wrap(px)) }
    }
}

/** One parsed frame: `draw` replays it on a Canvas whose units are device px (the list is in device px already). */
internal class CanvasScene private constructor(private val cmds: List<Cmd>, val bytes: Int) {
    val size get() = cmds.size

    private sealed class Cmd {
        class Layer(val clip: Path, val alpha: Float, val blend: BlendMode?) : Cmd()
        class Clip(val clip: Path) : Cmd()
        object Pop : Cmd()
        class Draw(val path: Path, val matrix: Matrix, val paint: Paint) : Cmd()
        class Glyphs(val font: Font?, val paint: Paint, val matrix: Matrix, val ids: IntArray, val pos: FloatArray) : Cmd()
        class Shadow(val matrix: Matrix, val rect: RectF, val radius: Float, val paint: Paint) : Cmd()
    }

    fun draw(canvas: Canvas) {
        val stack = ArrayList<Int>()
        for (c in cmds) when (c) {
            is Cmd.Layer -> {
                stack.add(canvas.save())
                canvas.clipPath(c.clip)
                val p = Paint()
                p.alpha = (c.alpha * 255f + 0.5f).toInt().coerceIn(0, 255)
                if (c.blend != null && Build.VERSION.SDK_INT >= 29) p.blendMode = c.blend
                canvas.saveLayer(null, p)
            }
            is Cmd.Clip -> {
                stack.add(canvas.save())
                canvas.clipPath(c.clip)
            }
            Cmd.Pop -> if (stack.isNotEmpty()) canvas.restoreToCount(stack.removeAt(stack.size - 1))
            is Cmd.Draw -> {
                canvas.save()
                canvas.concat(c.matrix)
                canvas.drawPath(c.path, c.paint)
                canvas.restore()
            }
            is Cmd.Glyphs -> if (c.font != null && Build.VERSION.SDK_INT >= 31) {
                canvas.save()
                canvas.concat(c.matrix)
                canvas.drawGlyphs(c.ids, 0, c.pos, 0, c.ids.size, c.font, c.paint)
                canvas.restore()
            }
            is Cmd.Shadow -> {
                canvas.save()
                canvas.concat(c.matrix)
                canvas.drawRoundRect(c.rect, c.radius, c.radius, c.paint)
                canvas.restore()
            }
        }
        while (stack.isNotEmpty()) canvas.restoreToCount(stack.removeAt(stack.size - 1))
    }

    companion object {
        private const val MAGIC = 0x4C445754
        private val MIX = arrayOf(null, BlendMode.MULTIPLY, BlendMode.SCREEN, BlendMode.OVERLAY, BlendMode.DARKEN, BlendMode.LIGHTEN, BlendMode.COLOR_DODGE, BlendMode.COLOR_BURN,
            BlendMode.HARD_LIGHT, BlendMode.SOFT_LIGHT, BlendMode.DIFFERENCE, BlendMode.EXCLUSION, BlendMode.HUE, BlendMode.SATURATION, BlendMode.COLOR, BlendMode.LUMINOSITY)
        private val COMPOSE = arrayOf(BlendMode.CLEAR, BlendMode.SRC, BlendMode.DST, null, BlendMode.DST_OVER, BlendMode.SRC_IN, BlendMode.DST_IN, BlendMode.SRC_OUT,
            BlendMode.DST_OUT, BlendMode.SRC_ATOP, BlendMode.DST_ATOP, BlendMode.XOR, BlendMode.PLUS, BlendMode.PLUS)
        private val TILE = arrayOf(Shader.TileMode.CLAMP, Shader.TileMode.REPEAT, Shader.TileMode.MIRROR)

        /** Parses a frame; a malformed stream yields what was read up to the error. */
        fun parse(bytes: ByteArray): CanvasScene {
            val cmds = ArrayList<Cmd>()
            if (bytes.size < 12) return CanvasScene(cmds, bytes.size)
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (b.int != MAGIC) return CanvasScene(cmds, bytes.size)
            b.int // version
            val count = b.int
            try {
                for (i in 0 until count) {
                    when (b.get().toInt()) {
                        1 -> {
                            val rule = b.get().toInt(); val mix = b.get().toInt(); val compose = b.get().toInt(); val alpha = b.float
                            val blend = if (compose == 3) MIX.getOrNull(mix) else COMPOSE.getOrNull(compose)
                            cmds.add(Cmd.Layer(path(b, rule), alpha, blend))
                        }
                        2 -> { val rule = b.get().toInt(); cmds.add(Cmd.Clip(path(b, rule))) }
                        3 -> cmds.add(Cmd.Pop)
                        4 -> {
                            val rule = b.get().toInt(); val m = matrix(b)
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG); paintOf(b, paint)
                            cmds.add(Cmd.Draw(path(b, rule), m, paint))
                        }
                        5 -> {
                            val m = matrix(b)
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG); paintOf(b, paint)
                            paint.style = Paint.Style.STROKE
                            paint.strokeWidth = b.float
                            paint.strokeJoin = when (b.get().toInt()) { 1 -> Paint.Join.ROUND; 2 -> Paint.Join.BEVEL; else -> Paint.Join.MITER }
                            paint.strokeCap = when (b.get().toInt()) { 1 -> Paint.Cap.ROUND; 2 -> Paint.Cap.SQUARE; else -> Paint.Cap.BUTT }
                            paint.strokeMiter = b.float
                            val nd = b.int
                            val dash = FloatArray(nd) { b.float }
                            val offset = b.float
                            if (nd >= 2) paint.pathEffect = DashPathEffect(dash, offset)
                            cmds.add(Cmd.Draw(path(b, 0), m, paint))
                        }
                        6 -> {
                            val id = b.long; val face = b.int; val size = b.float
                            val naxes = b.int
                            val axes = Array(naxes) { val tag = b.int; val v = b.float; FontVariationAxis(tagString(tag), v) }
                            val color = b.int
                            val m = matrix(b)
                            val skew = b.float
                            val n = b.int
                            val ids = IntArray(n); val pos = FloatArray(n * 2)
                            for (k in 0 until n) { ids[k] = b.int; pos[2 * k] = b.float; pos[2 * k + 1] = b.float }
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG)
                            paint.textSize = size
                            paint.color = color
                            // A glyph transform is a synthetic-italic x shear; Android leans the glyph with a negative skew.
                            if (skew != 0f) paint.textSkewX = -skew
                            cmds.add(Cmd.Glyphs(CanvasResources.font(id, face, axes), paint, m, ids, pos))
                        }
                        7 -> {
                            val m = matrix(b)
                            val rect = RectF(b.float, b.float, b.float, b.float)
                            val color = b.int; val radius = b.float; val sigma = b.float
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                            paint.color = color
                            // Skia: sigma = 0.57735 × radius + 0.5 (SkBlurMask::ConvertRadiusToSigma).
                            if (sigma > 0.5f) paint.maskFilter = BlurMaskFilter(((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.1f), BlurMaskFilter.Blur.NORMAL)
                            cmds.add(Cmd.Shadow(m, rect, radius, paint))
                        }
                        else -> break
                    }
                }
            } catch (e: Exception) {
                // truncated stream: keep what was decoded
            }
            return CanvasScene(cmds, bytes.size)
        }

        private fun tagString(tag: Int) = String(charArrayOf((tag ushr 24).toChar(), ((tag ushr 16) and 0xff).toChar(), ((tag ushr 8) and 0xff).toChar(), (tag and 0xff).toChar()))

        private fun matrix(b: ByteBuffer): Matrix {
            val a = b.float; val bb = b.float; val c = b.float; val d = b.float; val tx = b.float; val ty = b.float
            return Matrix().apply { setValues(floatArrayOf(a, c, tx, bb, d, ty, 0f, 0f, 1f)) }
        }

        private fun path(b: ByteBuffer, rule: Int): Path {
            val p = Path()
            p.fillType = if (rule == 1) Path.FillType.EVEN_ODD else Path.FillType.WINDING
            val n = b.int
            var open = false
            for (i in 0 until n) when (b.get().toInt()) {
                0 -> { p.moveTo(b.float, b.float); open = true }
                1 -> { val x = b.float; val y = b.float; if (open) p.lineTo(x, y) else { p.moveTo(x, y); open = true } }
                2 -> { val cx = b.float; val cy = b.float; val x = b.float; val y = b.float; if (!open) { p.moveTo(cx, cy); open = true }; p.quadTo(cx, cy, x, y) }
                3 -> { val c1x = b.float; val c1y = b.float; val c2x = b.float; val c2y = b.float; val x = b.float; val y = b.float; if (!open) { p.moveTo(c1x, c1y); open = true }; p.cubicTo(c1x, c1y, c2x, c2y, x, y) }
                4 -> { if (open) p.close(); open = false }
            }
            return p
        }

        private fun stops(b: ByteBuffer): Pair<IntArray, FloatArray> {
            val n = b.int
            val offsets = FloatArray(n); val colors = IntArray(n)
            for (i in 0 until n) { offsets[i] = b.float; colors[i] = b.int }
            return colors to offsets
        }

        /** Reads a paint into `paint` (colour or shader). */
        private fun paintOf(b: ByteBuffer, paint: Paint) {
            var shader: Shader? = null
            when (b.get().toInt()) {
                0 -> paint.color = b.int
                1 -> {
                    val x0 = b.float; val y0 = b.float; val x1 = b.float; val y1 = b.float
                    val tile = TILE[b.get().toInt().coerceIn(0, 2)]
                    val (colors, offsets) = stops(b)
                    shader = if (colors.size >= 2) LinearGradient(x0, y0, x1, y1, colors, offsets, tile) else null
                    if (shader == null) paint.color = colors.firstOrNull() ?: 0
                }
                2 -> {
                    b.float; b.float; b.float // start circle: Android's public RadialGradient is one circle
                    val cx = b.float; val cy = b.float; val r = b.float
                    val tile = TILE[b.get().toInt().coerceIn(0, 2)]
                    val (colors, offsets) = stops(b)
                    shader = if (colors.size >= 2 && r > 0f) RadialGradient(cx, cy, r, colors, offsets, tile) else null
                    if (shader == null) paint.color = colors.firstOrNull() ?: 0
                }
                3 -> {
                    val cx = b.float; val cy = b.float; val a0 = b.float; val a1 = b.float
                    b.get() // extend: a sweep covers the circle; pad/repeat differ only outside [a0,a1]
                    val (colors, offsets) = stops(b)
                    val span = if (a1 - a0 == 0f) (2 * Math.PI).toFloat() else a1 - a0
                    val scaled = FloatArray(offsets.size) { (offsets[it] * span / (2 * Math.PI).toFloat()).coerceIn(0f, 1f) }
                    shader = if (colors.size >= 2) SweepGradient(cx, cy, colors, scaled).also {
                        it.setLocalMatrix(Matrix().apply { preRotate(Math.toDegrees(a0.toDouble()).toFloat(), cx, cy) })
                    } else null
                    if (shader == null) paint.color = colors.firstOrNull() ?: 0
                }
                4 -> {
                    val id = b.long; val w = b.int; val h = b.int
                    val tx = TILE[b.get().toInt().coerceIn(0, 2)]; val ty = TILE[b.get().toInt().coerceIn(0, 2)]
                    val quality = b.get().toInt(); val alpha = b.float
                    val bmp = CanvasResources.image(id, w, h)
                    shader = bmp?.let { BitmapShader(it, tx, ty) }
                    paint.isFilterBitmap = quality > 0
                    paint.alpha = (alpha * 255f + 0.5f).toInt().coerceIn(0, 255)
                    if (shader == null) paint.color = 0
                }
                else -> paint.color = 0
            }
            if (b.get().toInt() == 1) {
                val m = matrix(b)
                shader?.setLocalMatrix(m)
            }
            paint.shader = shader
        }
    }
}

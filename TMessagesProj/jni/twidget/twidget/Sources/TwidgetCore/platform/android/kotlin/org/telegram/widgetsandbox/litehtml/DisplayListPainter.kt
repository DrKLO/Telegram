// Executes TwidgetCore display lists (src/display_list.h, to_json) on an android.graphics.Canvas.
package org.telegram.widgetsandbox.litehtml

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** Fonts, images and canvas bitmaps the painter draws with. */
internal interface PaintResources {
    fun font(id: Int): Paint?
    fun image(id: Int): Bitmap?
    fun canvas(id: Int): Bitmap?
}

internal class DisplayListPainter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val clear = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val path = Path()
    private val clipPath = Path()
    private val rectF = RectF()
    private val clipRectF = RectF()
    private val radii = FloatArray(8)
    private val matrix = Matrix()
    private val values = FloatArray(9)

    /**
     * Paints `list` on `canvas`, already scaled to CSS px. `density` is device px per CSS px: shadows are
     * blurred into bitmaps at that resolution. With `damage` (LitehtmlNative.damage, CSS px), only those
     * areas are cleared and painted again on a canvas that keeps the last frame; null paints everything.
     */
    fun paint(canvas: Canvas, list: JSONArray, res: PaintResources, density: Float, damage: JSONArray? = null) {
        val base = canvas.save()
        if (damage != null) {
            if (damage.length() == 0) return
            // Whole CSS px, one more around, for the antialiased edges.
            clipPath.rewind()
            for (i in 0 until damage.length()) {
                val d = damage.optJSONArray(i) ?: continue
                val x = floor(d.optDouble(0)).toFloat() - 1
                val y = floor(d.optDouble(1)).toFloat() - 1
                rectF.set(x, y, ceil(d.optDouble(0) + d.optDouble(2)).toFloat() + 1, ceil(d.optDouble(1) + d.optDouble(3)).toFloat() + 1)
                clipPath.addRect(rectF, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            canvas.drawPaint(clear)
        }
        paintAll(canvas, list, 0, list.length(), res, density)
        canvas.restoreToCount(base)
    }

    private fun paintAll(canvas: Canvas, list: JSONArray, from: Int, to: Int, res: PaintResources, density: Float) {
        // Clips and groups nest in paint order and unwind together, as on every platform: each is one save.
        var depth = 0
        var i = from
        while (i < to) {
            val c = list.optJSONObject(i++) ?: continue
            when (c.optInt("k")) {
                15 -> { // FilterPush: what it wraps goes through the filters on a bitmap of its own
                    val end = filterEnd(list, i - 1, to)
                    filtered(canvas, c, list, i, end, res, density)
                    i = end + 1
                }
                0 -> { // FillRect
                    canvas.save()
                    clipRect(canvas, c)
                    solid(fill, argb(c, "c"))
                    canvas.drawPath(rounded(c, "r", path), fill)
                    canvas.restore()
                }
                1 -> { // StrokeRect
                    stroke.color = argb(c, "c")
                    stroke.strokeWidth = c.optDouble("w", 1.0).toFloat()
                    stroke.pathEffect = null
                    canvas.drawPath(rounded(c, "r", path), stroke)
                }
                2 -> { // FillEllipse
                    solid(fill, argb(c, "c"))
                    canvas.drawOval(readRect(c, "r", rectF), fill)
                }
                3 -> { // StrokeEllipse
                    val w = c.optDouble("w", 1.0).toFloat()
                    readRect(c, "r", rectF).inset(w / 2, w / 2)
                    stroke.color = argb(c, "c")
                    stroke.strokeWidth = w
                    stroke.pathEffect = null
                    canvas.drawOval(rectF, stroke)
                }
                4 -> { // Text
                    val font = res.font(c.optInt("f")) ?: continue
                    font.color = argb(c, "c")
                    font.shader = null
                    canvas.drawText(c.optString("t"), c.optDouble("x").toFloat(), c.optDouble("y").toFloat(), font)
                }
                5 -> { // Image
                    val bmp = (if (c.has("cv")) res.canvas(c.optInt("cv")) else res.image(c.optInt("i"))) ?: continue
                    canvas.save()
                    clipRect(canvas, c)
                    // The layer's border radius bounds the image like the clip does.
                    if (c.has("q")) canvas.clipPath(rounded(c, if (c.has("l")) "l" else "r", clipPath))
                    canvas.drawBitmap(bmp, null, readRect(c, "r", rectF), bitmapPaint)
                    canvas.restore()
                }
                6, 7, 8 -> gradient(canvas, c)
                9 -> { // ClipPush
                    canvas.save()
                    depth++
                    val p = c.optJSONObject("p")
                    canvas.clipPath(if (p != null) readPath(p, clipPath) else rounded(c, "r", clipPath))
                }
                10, 13 -> if (depth > 0) { // ClipPop, GroupPop
                    canvas.restore()
                    depth--
                }
                11 -> c.optJSONObject("p")?.let { svgPath(canvas, it) }
                12 -> { // GroupPush
                    val opacity = c.optDouble("o", 1.0).toFloat()
                    // An opacity group is a layer composited once, so overlapping children do not darken.
                    if (opacity < 1f && c.optInt("h") == 0) canvas.saveLayerAlpha(null, (opacity.coerceIn(0f, 1f) * 255).toInt())
                    else canvas.save()
                    depth++
                    val m = floats(c.optJSONArray("m"), 6)
                    values[0] = m[0]; values[1] = m[2]; values[2] = m[4]
                    values[3] = m[1]; values[4] = m[3]; values[5] = m[5]
                    values[6] = 0f; values[7] = 0f; values[8] = 1f
                    matrix.setValues(values)
                    canvas.concat(matrix)
                    if (c.optInt("h") != 0) canvas.clipRect(0f, 0f, 0f, 0f)
                }
                14 -> shadow(canvas, c, density)
                // 16 FilterPop closes in filtered(); 17 Backdrop needs the pixels under it, which a Canvas does not give.
            }
        }
        while (depth-- > 0) canvas.restore()
    }

    /** The index of the FilterPop closing the FilterPush at `at`, or `to`. */
    private fun filterEnd(list: JSONArray, at: Int, to: Int): Int {
        var depth = 0
        for (j in at until to) {
            when (list.optJSONObject(j)?.optInt("k")) {
                15 -> depth++
                16 -> if (--depth == 0) return j
            }
        }
        return to
    }

    private fun filtered(canvas: Canvas, c: JSONObject, list: JSONArray, from: Int, to: Int, res: PaintResources, density: Float) {
        val fl = c.optJSONArray("fl")
        @Suppress("DEPRECATION") val m = canvas.matrix
        m.getValues(values)
        // How far the filters spread past the box, in CSS px: blurs three deviations, a shadow that and its offset.
        var left = 0f; var top = 0f; var right = 0f; var bottom = 0f
        for (k in 0 until (fl?.length() ?: 0)) {
            val f = fl!!.optJSONArray(k) ?: continue
            val amount = f.optDouble(1).toFloat().coerceAtLeast(0f)
            if (f.optInt(0) == 0) { left += 3 * amount; top += 3 * amount; right += 3 * amount; bottom += 3 * amount }
            else if (f.optInt(0) == 9) {
                val x = f.optDouble(2).toFloat(); val y = f.optDouble(3).toFloat()
                left = max(left, 1.5f * amount - x); right = max(right, 1.5f * amount + x)
                top = max(top, 1.5f * amount - y); bottom = max(bottom, 1.5f * amount + y)
            }
        }
        readRect(c, "r", rectF)
        rectF.set(rectF.left - left, rectF.top - top, rectF.right + right, rectF.bottom + bottom)
        val device = RectF()
        m.mapRect(device, rectF)
        val w = ceil(device.right).toInt() - floor(device.left).toInt()
        val h = ceil(device.bottom).toInt() - floor(device.top).toInt()
        // Under a rotation or past a sensible size the filters do not apply: what they wrap draws as it is.
        if (fl == null || fl.length() == 0 || values[1] != 0f || values[3] != 0f || w <= 0 || h <= 0 || w.toLong() * h > 4096L * 4096L) {
            paintAll(canvas, list, from, to, res, density)
            return
        }
        val x0 = floor(device.left)
        val y0 = floor(device.top)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val layer = Canvas(bitmap)
        layer.translate(-x0, -y0)
        layer.concat(m)
        paintAll(layer, list, from, to, res, density)
        LitehtmlNative.filterPixels(bitmap, fl.toString(), values[0])
        // Back where it was drawn, in user space: the inverse of the device rect it covers.
        val inverse = Matrix()
        m.invert(inverse)
        val back = RectF(x0, y0, x0 + w, y0 + h)
        inverse.mapRect(back)
        canvas.drawBitmap(bitmap, null, back, bitmapPaint)
        bitmap.recycle()
    }

    private fun solid(paint: Paint, color: Int) {
        paint.shader = null
        paint.color = color
    }

    private fun clipRect(canvas: Canvas, c: JSONObject) {
        if (c.has("l")) canvas.clipRect(readRect(c, "l", clipRectF))
    }

    private fun rounded(c: JSONObject, key: String, into: Path): Path {
        readRect(c, key, rectF)
        into.rewind()
        val q = c.optJSONArray("q")
        if (q == null) {
            into.addRect(rectF, Path.Direction.CW)
        } else {
            for (i in 0 until 8) radii[i] = q.optDouble(i).toFloat()
            into.addRoundRect(rectF, radii, Path.Direction.CW)
        }
        return into
    }

    private fun gradient(canvas: Canvas, c: JSONObject) {
        val a = floats(c.optJSONArray("a"), 4)
        val stops = Stops(c.optJSONArray("s") ?: return)
        if (stops.empty) return
        canvas.save()
        clipRect(canvas, c)
        canvas.clipPath(rounded(c, "r", clipPath))
        when (c.optInt("k")) {
            6 -> fill.shader = LinearGradient(a[0], a[1], a[2], a[3], stops.shaderColors, stops.shaderOffsets, Shader.TileMode.CLAMP)
            7 -> {
                val rx = max(a[2], 0.01f)
                val ry = max(a[3], 0.01f)
                fill.shader = RadialGradient(a[0], a[1], rx, stops.shaderColors, stops.shaderOffsets, Shader.TileMode.CLAMP)
                canvas.translate(a[0], a[1]); canvas.scale(1f, ry / rx); canvas.translate(-a[0], -a[1])
            }
            else -> {
                // CSS: degrees clockwise from the top; SweepGradient starts at +x going clockwise.
                fill.shader = SweepGradient(a[0], a[1], stops.shaderColors, stops.shaderOffsets)
                canvas.rotate(a[2] - 90f, a[0], a[1])
            }
        }
        readRect(c, "r", rectF)
        canvas.drawRect(rectF.left - 4000f, rectF.top - 4000f, rectF.right + 4000f, rectF.bottom + 4000f, fill)
        fill.shader = null
        canvas.restore()
    }

    private fun svgPath(canvas: Canvas, p: JSONObject) {
        val shape = readPath(p, path)
        val s = p.optJSONArray("s")
        if (p.has("g") && s != null && !Stops(s).empty) {
            val stops = Stops(s)
            val a = floats(p.optJSONArray("ga"), 7)
            // Android's radial gradient has no focus: it is centred, as the focus usually is.
            fill.shader = if (p.optInt("g") == 1) LinearGradient(a[0], a[1], a[2], a[3], stops.shaderColors, stops.shaderOffsets, Shader.TileMode.CLAMP)
            else RadialGradient(a[0], a[1], max(a[4], 0.01f), stops.shaderColors, stops.shaderOffsets, Shader.TileMode.CLAMP)
            canvas.drawPath(shape, fill)
            fill.shader = null
        } else if (p.has("f") && (argb(p, "f") ushr 24) != 0) {
            solid(fill, argb(p, "f"))
            canvas.drawPath(shape, fill)
        }
        val width = p.optDouble("sw").toFloat()
        if (p.has("sc") && (argb(p, "sc") ushr 24) != 0 && width > 0) {
            stroke.color = argb(p, "sc")
            stroke.lineStyle(p, width, 4f)
            canvas.drawPath(shape, stroke)
            stroke.pathEffect = null
        }
    }

    /**
     * CSS box-shadow, blurred in a software bitmap: hardware canvases before API 28 ignore blur on shapes. The
     * blur is sigma = blur / 2 in CSS px, drawn outside the border box, or inside the padding box when inset.
     */
    private fun shadow(canvas: Canvas, c: JSONObject, density: Float) {
        val a = floats(c.optJSONArray("a"), 4)
        val dx = a[0]; val dy = a[1]; val blur = max(a[2], 0f); val spread = a[3]
        val color = argb(c, "c")
        val box = RectF(readRect(c, "r", rectF))
        if (box.isEmpty || (color ushr 24) == 0) return
        val inset = c.optInt("n") != 0
        // Three sigma of blur beyond the spread shape: past that the Gaussian is invisible.
        val reach = blur * 1.5f + abs(spread) + 2
        // The ink: where the blurred shape can show, in CSS px, before the offset.
        val ink = RectF(box).apply { inset(-reach - (if (inset) abs(dx) + abs(dy) else 0f), -reach - (if (inset) abs(dx) + abs(dy) else 0f)) }
        val scale = max(density, 1f)
        val w = ceil(ink.width() * scale).toInt()
        val h = ceil(ink.height() * scale).toInt()
        if (w <= 0 || h <= 0 || w.toLong() * h > 16L shl 20) return

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val offscreen = Canvas(bitmap)
        offscreen.scale(scale, scale)
        offscreen.translate(-ink.left, -ink.top)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        // Skia's blur radius r has sigma = 0.57735 r + 0.5; this inverts it for sigma = blur / 2, in device px.
        val sigma = blur / 2f * scale
        if (sigma > 0.5f) paint.maskFilter = BlurMaskFilter((sigma - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
        val shape = Path()
        if (inset) {
            shape.addRect(ink, Path.Direction.CW)
            rectF.set(box.left + spread, box.top + spread, box.right - spread, box.bottom - spread)
            if (!rectF.isEmpty) shape.addPath(roundedAt(c, rectF, -spread))
            shape.fillType = Path.FillType.EVEN_ODD
        } else {
            rectF.set(box.left - spread, box.top - spread, box.right + spread, box.bottom + spread)
            shape.addPath(roundedAt(c, rectF, if (c.has("q")) spread else 0f))
        }
        offscreen.drawPath(shape, paint)

        canvas.save()
        val border = rounded(c, "r", Path())
        if (inset) canvas.clipPath(border) else canvas.clipOutPath(border)
        rectF.set(ink.left + dx, ink.top + dy, ink.right + dx, ink.bottom + dy)
        canvas.drawBitmap(bitmap, null, rectF, bitmapPaint)
        canvas.restore()
        bitmap.recycle()
    }

    /** The radii of `c` grown by `grow`, on `rect`. */
    private fun roundedAt(c: JSONObject, rect: RectF, grow: Float): Path {
        val out = Path()
        val q = c.optJSONArray("q")
        if (q == null) {
            out.addRect(rect, Path.Direction.CW)
        } else {
            val r = FloatArray(8) { max(q.optDouble(it).toFloat() + grow, 0f) }
            out.addRoundRect(rect, r, Path.Direction.CW)
        }
        return out
    }
}

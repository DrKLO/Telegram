// Bitmaps for <canvas> elements, run from TwidgetCore's canvas op batches (src/canvas_ops.h, to_json).
package org.telegram.widgetsandbox.litehtml

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

internal class CanvasBitmaps(private val fonts: (Int) -> Paint?, private val images: (Int) -> Bitmap?) {
    private class Target(val bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
    }

    private val targets = HashMap<Int, Target>()
    private val path = Path()
    private val user = Matrix()
    private val inverse = Matrix()
    private val values = FloatArray(9)

    fun bitmap(id: Int): Bitmap? = targets[id]?.bitmap

    /** jni.cpp → canvasRun: a w × h bitmap, created on the first call, runs the ops. */
    fun run(id: Int, w: Int, h: Int, json: String) {
        if (w <= 0 || h <= 0) {
            release(id)
            return
        }
        var target = targets[id]
        if (target == null || target.bitmap.width != w || target.bitmap.height != h) {
            target?.bitmap?.recycle()
            target = Target(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))
            targets[id] = target
        }
        val ops = try { JSONArray(json) } catch (e: Exception) { return }
        for (i in 0 until ops.length()) ops.optJSONObject(i)?.let { op(target, id, it) }
    }

    /** jni.cpp → canvasRead: straight RGBA, transparent outside the bitmap. */
    fun read(id: Int, x: Int, y: Int, w: Int, h: Int): ByteArray {
        val out = ByteArray(max(w, 0) * max(h, 0) * 4)
        val bitmap = targets[id]?.bitmap ?: return out
        val x0 = max(0, x); val y0 = max(0, y)
        val x1 = minOf(bitmap.width, x + w); val y1 = minOf(bitmap.height, y + h)
        if (x1 <= x0 || y1 <= y0) return out
        val pixels = IntArray((x1 - x0) * (y1 - y0))
        // getPixels answers unpremultiplied colours, which is what getImageData wants.
        bitmap.getPixels(pixels, 0, x1 - x0, x0, y0, x1 - x0, y1 - y0)
        for (row in y0 until y1) {
            for (col in x0 until x1) {
                val c = pixels[(row - y0) * (x1 - x0) + (col - x0)]
                val o = ((row - y) * w + (col - x)) * 4
                out[o] = Color.red(c).toByte()
                out[o + 1] = Color.green(c).toByte()
                out[o + 2] = Color.blue(c).toByte()
                out[o + 3] = Color.alpha(c).toByte()
            }
        }
        return out
    }

    fun release(id: Int) {
        targets.remove(id)?.bitmap?.recycle()
    }

    fun releaseAll() {
        targets.values.forEach { it.bitmap.recycle() }
        targets.clear()
    }

    private fun op(target: Target, id: Int, op: JSONObject) {
        val canvas = target.canvas
        when (op.optInt("k")) {
            0 -> { // Reset
                target.bitmap.eraseColor(Color.TRANSPARENT)
                return
            }
            6 -> { // PutImage: raw pixels, outside drawing
                val w = op.optInt("w"); val h = op.optInt("h")
                val bytes = try { Base64.decode(op.optString("px"), Base64.DEFAULT) } catch (e: Exception) { return }
                if (w <= 0 || h <= 0 || bytes.size != w * h * 4) return
                val dx = op.optDouble("x").toInt(); val dy = op.optDouble("y").toInt()
                val x0 = max(0, dx); val y0 = max(0, dy)
                val x1 = minOf(target.bitmap.width, dx + w); val y1 = minOf(target.bitmap.height, dy + h)
                if (x1 <= x0 || y1 <= y0) return
                val pixels = IntArray((x1 - x0) * (y1 - y0))
                for (row in y0 until y1) for (col in x0 until x1) {
                    val o = ((row - dy) * w + (col - dx)) * 4
                    fun b(k: Int) = bytes[o + k].toInt() and 0xff
                    pixels[(row - y0) * (x1 - x0) + (col - x0)] = Color.argb(b(3), b(0), b(1), b(2))
                }
                // setPixels takes unpremultiplied colours: putImageData's straight alpha.
                target.bitmap.setPixels(pixels, 0, x1 - x0, x0, y0, x1 - x0, y1 - y0)
                return
            }
        }
        canvas.save()
        val clips = op.optJSONArray("cl")
        if (clips != null) for (i in 0 until clips.length()) clips.optJSONObject(i)?.let { canvas.clipPath(readPath(it, Path())) }
        if (op.optInt("k") == 3) { // Clear
            val clear = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
            canvas.drawPath(readPath(op, path), clear)
            canvas.restore()
            return
        }
        val alpha = op.optDouble("a", 1.0).toFloat().coerceIn(0f, 1f)
        val composite = op.optInt("cm")
        val shadow = op.optJSONArray("sh")
        if (shadow != null && op.has("shc")) {
            // The shadow is cast by the whole operation in canvas px, under it: draw the content once into a
            // scratch bitmap, then its blurred alpha at the offset, then the content.
            val scratch = Bitmap.createBitmap(target.bitmap.width, target.bitmap.height, Bitmap.Config.ARGB_8888)
            content(Canvas(scratch), id, op, Paint(Paint.ANTI_ALIAS_FLAG))
            val blur = Paint(Paint.ANTI_ALIAS_FLAG)
            val sigma = shadow.optDouble(2).toFloat() / 2f
            if (sigma > 0.5f) blur.maskFilter = BlurMaskFilter((sigma - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
            val offset = IntArray(2)
            val mask = scratch.extractAlpha(blur, offset)
            val layer = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.alpha = (alpha * 255).toInt(); composite(composite) }
            canvas.saveLayer(null, layer)
            val tint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(op, "shc") }
            canvas.drawBitmap(mask, offset[0] + shadow.optDouble(0).toFloat(), offset[1] + shadow.optDouble(1).toFloat(), tint)
            canvas.drawBitmap(scratch, 0f, 0f, null)
            canvas.restore()
            mask.recycle()
            scratch.recycle()
        } else if (alpha < 1f || composite != 0) {
            // A layer applies alpha and the mode to the operation as a whole, as the canvas does.
            val layer = Paint().apply { this.alpha = (alpha * 255).toInt(); composite(composite) }
            canvas.saveLayer(null, layer)
            content(canvas, id, op, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        } else {
            content(canvas, id, op, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        canvas.restore()
    }

    /** One operation's content, at full alpha and source-over, on `canvas` whose state it restores. */
    private fun content(canvas: Canvas, id: Int, op: JSONObject, paint: Paint) {
        val m = floats(op.optJSONArray("m") ?: JSONArray(listOf(1, 0, 0, 1, 0, 0)), 6)
        values[0] = m[0]; values[1] = m[2]; values[2] = m[4]
        values[3] = m[1]; values[4] = m[3]; values[5] = m[5]
        values[6] = 0f; values[7] = 0f; values[8] = 1f
        user.setValues(values)
        canvas.save()
        when (op.optInt("k")) {
            1, 2 -> { // Fill, Stroke
                // The path is in canvas px; paints and line widths are in user space, so draw it back there.
                if (!user.invert(inverse)) { canvas.restore(); return }
                val shape = readPath(op, path)
                shape.transform(inverse)
                canvas.concat(user)
                paint(paint, op)
                if (op.optInt("k") == 2) {
                    paint.style = Paint.Style.STROKE
                    paint.lineStyle(op, op.optDouble("lw", 1.0).toFloat(), 10f)
                }
                canvas.drawPath(shape, paint)
            }
            4 -> { // Text
                val font = fonts(op.optInt("f"))
                val tp = floats(op.optJSONArray("tp") ?: JSONArray(listOf(0, 0, 1, 0)), 4)
                if (font != null) {
                    val text = Paint(font)
                    canvas.concat(user)
                    canvas.translate(tp[0], tp[1])
                    canvas.scale(tp[2], 1f)
                    paint(text, op)
                    if (op.optInt("st") != 0) {
                        text.style = Paint.Style.STROKE
                        text.lineStyle(op, op.optDouble("lw", 1.0).toFloat(), 10f)
                    }
                    canvas.drawText(op.optString("t"), tp[3], 0f, text)
                }
            }
            5 -> { // Image
                var image = if (op.has("cv")) targets[op.optInt("cv")]?.bitmap else images(op.optInt("i"))
                if (image != null) {
                    // Drawing a canvas onto itself reads what it held before the draw.
                    val copy = if (op.optInt("cv") == id && op.has("cv")) image.copy(Bitmap.Config.ARGB_8888, false) else null
                    if (copy != null) image = copy
                    val r = floats(op.optJSONArray("ir"), 8)
                    canvas.concat(user)
                    paint.isFilterBitmap = op.optInt("ns") == 0
                    val src = if (r[2] < 0) null else Rect(r[0].toInt(), r[1].toInt(), (r[0] + r[2]).toInt(), (r[1] + r[3]).toInt())
                    canvas.drawBitmap(image!!, src, RectF(r[4], r[5], r[4] + r[6], r[5] + r[7]), paint)
                    copy?.recycle()
                }
            }
        }
        canvas.restore()
    }

    /** The op's fill or stroke style, in user space. */
    private fun paint(paint: Paint, op: JSONObject) {
        val kind = op.optInt("pk")
        val stopsJson = op.optJSONArray("s")
        if (kind == 0 || stopsJson == null || Stops(stopsJson).empty) {
            paint.shader = null
            paint.color = argb(op, "c")
            return
        }
        val stops = Stops(stopsJson)
        val a = floats(op.optJSONArray("pa"), 6)
        paint.color = Color.BLACK
        paint.shader = when (kind) {
            1 -> LinearGradient(a[0], a[1], a[2], a[3], stops.shaderColors, stops.shaderOffsets, Shader.TileMode.CLAMP)
            2 -> {
                // Android's radial gradient starts at radius 0 around the outer circle's centre: an inner
                // circle squeezes the stops outwards, as on Direct2D.
                val r0 = a[2]; val r1 = max(a[5], 0.01f)
                val offsets = if (r0 > 0 && r0 < r1) FloatArray(stops.shaderOffsets.size) { r0 / r1 + stops.shaderOffsets[it] * (1 - r0 / r1) } else stops.shaderOffsets
                RadialGradient(a[3], a[4], r1, stops.shaderColors, offsets, Shader.TileMode.CLAMP)
            }
            else -> SweepGradient(a[1], a[2], stops.shaderColors, stops.shaderOffsets).apply {
                setLocalMatrix(Matrix().apply { setRotate(Math.toDegrees(a[0].toDouble()).toFloat(), a[1], a[2]) })
            }
        }
    }
}

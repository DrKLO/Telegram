// What the C++ litehtml container asks the platform for (renderer-litehtml/src/backend.h): fonts with
// metrics, text widths and decoded `data:` images — android.graphics has them, the NDK does not — plus
// the painter that executes the display list the container records (display_list.h) on a Canvas.
// Coordinates are CSS px; the view scales its canvas by the density before painting.
package org.telegram.widgetsandbox.litehtml

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.Build
import android.util.Base64
import androidx.annotation.Keep
import org.json.JSONArray
import org.json.JSONObject

@Keep
class LitehtmlBackend {
    private class Font(val paint: Paint)

    private val fonts = HashMap<Int, Font>()
    private var nextFont = 0
    private val images = HashMap<Int, Bitmap>()

    // ── callbacks from C++ (names and signatures are looked up by jni.cpp) ──

    /** [id, ascent, descent, height, xHeight, zeroWidth]; id 0 when no font could be made. */
    @Keep
    fun createFont(families: String, sizePx: Float, weight: Int, italic: Boolean): FloatArray {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        paint.textSize = sizePx
        paint.typeface = typeface(families, weight, italic)
        val fm = paint.fontMetrics
        val id = ++nextFont
        fonts[id] = Font(paint)
        // fontMetrics.ascent is negative (above the baseline)
        return floatArrayOf(id.toFloat(), -fm.ascent, fm.descent, fm.descent - fm.ascent + fm.leading, xHeight(paint), paint.measureText("0"))
    }

    @Keep
    fun deleteFont(id: Int) {
        fonts.remove(id)
    }

    @Keep
    fun textWidth(id: Int, text: String): Float = fonts[id]?.paint?.measureText(text) ?: 0f

    /** Decodes a `data:` image (base64 or percent-encoded; no SVG) and keeps it under `id`. [w, h] in px, or null. */
    @Keep
    fun loadImage(id: Int, url: String): FloatArray? {
        val comma = url.indexOf(',')
        if (!url.startsWith("data:") || comma < 0) return null
        val meta = url.substring(5, comma)
        if (meta.contains("svg")) return null
        val payload = url.substring(comma + 1)
        val bytes = try {
            if (meta.contains(";base64")) Base64.decode(payload, Base64.DEFAULT) else java.net.URLDecoder.decode(payload, "UTF-8").toByteArray(Charsets.ISO_8859_1)
        } catch (e: Exception) { return null }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        images[id]?.recycle()
        images[id] = bmp
        return floatArrayOf(bmp.width.toFloat(), bmp.height.toFloat())
    }

    fun release() {
        images.values.forEach { it.recycle() }
        images.clear()
        fonts.clear()
    }

    private fun typeface(families: String, weight: Int, italic: Boolean): Typeface {
        var base: Typeface? = null
        for (raw in families.split(',')) {
            val fam = raw.trim().trim('"', '\'')
            if (fam.isEmpty()) continue
            base = when (fam.lowercase()) {
                "-apple-system", "blinkmacsystemfont", "system-ui", "ui-sans-serif", "sans-serif", "segoe ui", "roboto", "inter", "helvetica neue", "helvetica", "arial" -> Typeface.SANS_SERIF
                "serif", "ui-serif", "georgia", "times new roman", "times" -> Typeface.SERIF
                "monospace", "ui-monospace", "menlo", "consolas", "courier new", "sf mono" -> Typeface.MONOSPACE
                "cursive", "fantasy" -> Typeface.create(fam, Typeface.NORMAL)
                else -> Typeface.create(fam, Typeface.NORMAL).takeIf { it != Typeface.DEFAULT }  // unknown names come back as the default
            }
            if (base != null) break
        }
        val family = base ?: Typeface.SANS_SERIF
        return if (Build.VERSION.SDK_INT >= 28) Typeface.create(family, weight.coerceIn(1, 1000), italic)
        else Typeface.create(family, (if (weight >= 600) Typeface.BOLD else 0) or (if (italic) Typeface.ITALIC else 0))
    }

    private val bounds = Rect()
    private fun xHeight(paint: Paint): Float {
        paint.getTextBounds("x", 0, 1, bounds)
        return bounds.height().toFloat()
    }

    // ── the painter ──

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val rectF = RectF()
    private val radii = FloatArray(8)

    /** Executes a display list (the JSON of `LitehtmlNative.displayList`) on `canvas`, which is already scaled to CSS px. */
    fun paint(canvas: Canvas, list: JSONArray) {
        var clipDepth = 0
        for (i in 0 until list.length()) {
            val c = list.optJSONObject(i) ?: continue
            when (c.optInt("k")) {
                0 -> { // FillRect
                    canvas.save()
                    clipRect(canvas, c)
                    fill.color = c.optLong("c").toInt()
                    fill.shader = null
                    canvas.drawPath(rounded(c), fill)
                    canvas.restore()
                }
                1 -> { // StrokeRect
                    stroke.color = c.optLong("c").toInt()
                    stroke.strokeWidth = c.optDouble("w", 1.0).toFloat()
                    canvas.drawPath(rounded(c), stroke)
                }
                2 -> { // FillEllipse
                    rect(c, "r")
                    fill.color = c.optLong("c").toInt()
                    fill.shader = null
                    canvas.drawOval(rectF, fill)
                }
                3 -> { // StrokeEllipse
                    rect(c, "r")
                    val w = c.optDouble("w", 1.0).toFloat()
                    rectF.inset(w / 2, w / 2)
                    stroke.color = c.optLong("c").toInt()
                    stroke.strokeWidth = w
                    canvas.drawOval(rectF, stroke)
                }
                4 -> { // Text
                    val font = fonts[c.optInt("f")] ?: continue
                    font.paint.color = c.optLong("c").toInt()
                    canvas.drawText(c.optString("t"), c.optDouble("x").toFloat(), c.optDouble("y").toFloat(), font.paint)
                }
                5 -> { // Image
                    val bmp = images[c.optInt("i")] ?: continue
                    canvas.save()
                    clipRect(canvas, c)
                    if (c.has("q")) {
                        // the layer's border radius bounds the image like the clip does
                        val key = if (c.has("l")) "l" else "r"
                        canvas.clipPath(rounded(c, key))
                    }
                    rect(c, "r")
                    canvas.drawBitmap(bmp, null, rectF, fill.also { it.shader = null; it.color = -1 })
                    canvas.restore()
                }
                6, 7, 8 -> { // Linear / Radial / Conic
                    val a = c.optJSONArray("a") ?: continue
                    val s = c.optJSONArray("s") ?: continue
                    val n = s.length() / 2
                    if (n == 0) continue
                    val colors = IntArray(n) { s.optLong(it * 2 + 1).toInt() }
                    val stops = FloatArray(n) { s.optDouble(it * 2).toFloat().coerceIn(0f, 1f) }
                    canvas.save()
                    clipRect(canvas, c)
                    canvas.clipPath(rounded(c))
                    val ax = a.optDouble(0).toFloat(); val ay = a.optDouble(1).toFloat()
                    val bx = a.optDouble(2).toFloat(); val by = a.optDouble(3).toFloat()
                    when (c.optInt("k")) {
                        6 -> fill.shader = LinearGradient(ax, ay, bx, by, colors, stops, Shader.TileMode.CLAMP)
                        7 -> {
                            val rx = maxOf(bx, 0.01f); val ry = maxOf(by, 0.01f)
                            fill.shader = RadialGradient(ax, ay, rx, colors, stops, Shader.TileMode.CLAMP)
                            canvas.translate(ax, ay); canvas.scale(1f, ry / rx); canvas.translate(-ax, -ay)
                        }
                        else -> {
                            // CSS: degrees clockwise from the top; SweepGradient starts at +x going clockwise
                            fill.shader = SweepGradient(ax, ay, colors, stops)
                            canvas.rotate(bx - 90f, ax, ay)
                        }
                    }
                    rect(c, "r")
                    canvas.drawRect(rectF.left - 4000f, rectF.top - 4000f, rectF.right + 4000f, rectF.bottom + 4000f, fill)
                    fill.shader = null
                    canvas.restore()
                }
                9 -> { // ClipPush
                    canvas.save()
                    clipDepth++
                    canvas.clipPath(rounded(c))
                }
                10 -> if (clipDepth > 0) { canvas.restore(); clipDepth-- }
            }
        }
        while (clipDepth-- > 0) canvas.restore()
    }

    private fun rect(c: JSONObject, key: String) {
        val r = c.optJSONArray(key)
        if (r == null) { rectF.setEmpty(); return }
        val x = r.optDouble(0).toFloat(); val y = r.optDouble(1).toFloat()
        rectF.set(x, y, x + r.optDouble(2).toFloat(), y + r.optDouble(3).toFloat())
    }

    private fun clipRect(canvas: Canvas, c: JSONObject) {
        if (!c.has("l")) return
        rect(c, "l")
        canvas.clipRect(rectF)
    }

    private fun rounded(c: JSONObject, key: String = "r"): Path {
        rect(c, key)
        path.rewind()
        val q = c.optJSONArray("q")
        if (q == null) {
            path.addRect(rectF, Path.Direction.CW)
        } else {
            for (i in 0 until 8) radii[i] = q.optDouble(i).toFloat()
            path.addRoundRect(rectF, radii, Path.Direction.CW)
        }
        return path
    }
}

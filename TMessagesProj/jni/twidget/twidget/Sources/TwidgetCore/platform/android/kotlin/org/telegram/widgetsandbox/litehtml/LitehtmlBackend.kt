// What TwidgetCore asks Android for (src/backend.h): fonts with metrics, text widths, decoded `data:`
// images and canvas bitmaps — android.graphics has them, the NDK does not — and the painter of the display
// lists it records. Coordinates are CSS px; the view scales its canvas by the density before painting.
package org.telegram.widgetsandbox.litehtml

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.util.Base64
import androidx.annotation.Keep
import org.json.JSONArray

@Keep
class LitehtmlBackend {
    private val fonts = HashMap<Int, Paint>()
    private var nextFont = 0
    private val images = HashMap<Int, Bitmap>()
    private val canvases = CanvasBitmaps({ fonts[it] }, { images[it] })
    private val painter = DisplayListPainter()
    private val resources = object : PaintResources {
        override fun font(id: Int) = fonts[id]
        override fun image(id: Int) = images[id]
        override fun canvas(id: Int) = canvases.bitmap(id)
    }

    // ── callbacks from C++ (names and signatures are looked up by jni.cpp) ──

    /**
     * [id, ascent, descent, height, xHeight, zeroWidth]; id 0 when no font could be made. `features` is
     * in font-feature-settings syntax, empty for the font's defaults.
     */
    @Keep
    fun createFont(families: String, sizePx: Float, weight: Int, italic: Boolean, features: String): FloatArray {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        paint.textSize = sizePx
        paint.typeface = typeface(families, weight, italic)
        if (features.isNotEmpty()) paint.fontFeatureSettings = features
        val fm = paint.fontMetrics
        val id = ++nextFont
        fonts[id] = paint
        // fontMetrics.ascent is negative (above the baseline)
        return floatArrayOf(id.toFloat(), -fm.ascent, fm.descent, fm.descent - fm.ascent + fm.leading, xHeight(paint), paint.measureText("0"))
    }

    @Keep
    fun deleteFont(id: Int) {
        fonts.remove(id)
    }

    @Keep
    fun textWidth(id: Int, text: String): Float = fonts[id]?.measureText(text) ?: 0f

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

    /** Runs a canvas op batch (JSON, src/canvas_ops.h) on canvas `id`'s w × h bitmap. */
    @Keep
    fun canvasRun(id: Int, w: Int, h: Int, ops: String) = canvases.run(id, w, h, ops)

    /** Straight RGBA of a canvas region, w × h × 4 bytes. */
    @Keep
    fun canvasRead(id: Int, x: Int, y: Int, w: Int, h: Int): ByteArray = canvases.read(id, x, y, w, h)

    @Keep
    fun canvasRelease(id: Int) = canvases.release(id)

    /**
     * Executes a display list (the JSON of `LitehtmlNative.displayList`) on `canvas`, already scaled to CSS px by
     * `density`; with `damage` (`LitehtmlNative.damage`), only the areas that changed on a canvas that keeps the last frame.
     */
    fun paint(canvas: Canvas, list: JSONArray, density: Float, damage: JSONArray? = null) = painter.paint(canvas, list, resources, density, damage)

    fun release() {
        images.values.forEach { it.recycle() }
        images.clear()
        canvases.releaseAll()
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
}

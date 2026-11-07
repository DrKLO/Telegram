package org.telegram.widgetsandbox.litehtml

import android.graphics.BlendMode
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/** Builds `{"o": op codes, "v": points, "e": evenodd}` (0 move, 1 line, 2 quad, 3 cubic, 4 close) into `into`. */
internal fun readPath(json: JSONObject, into: Path = Path()): Path {
    into.rewind()
    val ops = json.optJSONArray("o") ?: return into
    val v = json.optJSONArray("v") ?: JSONArray()
    var i = 0
    fun p(k: Int) = v.optDouble(i + k).toFloat()
    for (n in 0 until ops.length()) {
        val op = ops.optInt(n)
        val need = when (op) { 3 -> 6; 2 -> 4; 4 -> 0; else -> 2 }
        if (i + need > v.length()) break
        when (op) {
            0 -> into.moveTo(p(0), p(1))
            1 -> into.lineTo(p(0), p(1))
            2 -> into.quadTo(p(0), p(1), p(2), p(3))
            3 -> into.cubicTo(p(0), p(1), p(2), p(3), p(4), p(5))
            4 -> into.close()
        }
        i += need
    }
    into.fillType = if (json.optInt("e") != 0) Path.FillType.EVEN_ODD else Path.FillType.WINDING
    return into
}

/** A rectangle, `[x, y, w, h]`, into `out`; empty when absent. */
internal fun readRect(json: JSONObject, key: String, out: RectF): RectF {
    val r = json.optJSONArray(key)
    if (r == null) {
        out.setEmpty()
        return out
    }
    val x = r.optDouble(0).toFloat()
    val y = r.optDouble(1).toFloat()
    out.set(x, y, x + r.optDouble(2).toFloat(), y + r.optDouble(3).toFloat())
    return out
}

internal fun floats(array: JSONArray?, count: Int = array?.length() ?: 0): FloatArray =
    FloatArray(count) { array?.optDouble(it)?.toFloat() ?: 0f }

/** ARGB as the JSON writes it (an unsigned 32-bit number). */
internal fun argb(json: JSONObject, key: String): Int = json.optLong(key).toInt()

/** Stops as offset, ARGB pairs. */
internal class Stops(s: JSONArray) {
    val colors = IntArray(s.length() / 2) { s.optLong(it * 2 + 1).toInt() }
    val offsets = FloatArray(s.length() / 2) { s.optDouble(it * 2).toFloat().coerceIn(0f, 1f) }
    val empty get() = colors.isEmpty()
    /** Android gradients need two colours at least. */
    val shaderColors: IntArray get() = if (colors.size == 1) intArrayOf(colors[0], colors[0]) else colors
    val shaderOffsets: FloatArray get() = if (offsets.size == 1) floatArrayOf(0f, 1f) else offsets
}

internal fun Paint.lineStyle(json: JSONObject, width: Float, defaultMiter: Float) {
    strokeWidth = width
    strokeCap = when (json.optInt("lc")) { 1 -> Paint.Cap.ROUND; 2 -> Paint.Cap.SQUARE; else -> Paint.Cap.BUTT }
    strokeJoin = when (json.optInt("lj")) { 1 -> Paint.Join.ROUND; 2 -> Paint.Join.BEVEL; else -> Paint.Join.MITER }
    strokeMiter = json.optDouble("ml", defaultMiter.toDouble()).toFloat()
    val dashes = floats(json.optJSONArray("d"))
    pathEffect = if (dashes.isNotEmpty() && dashes.any { it > 0 }) {
        // DashPathEffect wants an even count; the canvas and SVG repeat an odd list twice.
        val even = if (dashes.size % 2 == 1) dashes + dashes else dashes
        android.graphics.DashPathEffect(even, json.optDouble("do").toFloat())
    } else null
}

/** globalCompositeOperation, in the order of Composite in canvas_ops.h. */
internal fun Paint.composite(index: Int) {
    if (Build.VERSION.SDK_INT >= 29) {
        val modes = arrayOf(
            BlendMode.SRC_OVER, BlendMode.SRC_IN, BlendMode.SRC_OUT, BlendMode.SRC_ATOP, BlendMode.DST_OVER, BlendMode.DST_IN,
            BlendMode.DST_OUT, BlendMode.DST_ATOP, BlendMode.PLUS, BlendMode.SRC, BlendMode.XOR, BlendMode.MULTIPLY, BlendMode.SCREEN,
            BlendMode.OVERLAY, BlendMode.DARKEN, BlendMode.LIGHTEN, BlendMode.COLOR_DODGE, BlendMode.COLOR_BURN, BlendMode.HARD_LIGHT,
            BlendMode.SOFT_LIGHT, BlendMode.DIFFERENCE, BlendMode.EXCLUSION, BlendMode.HUE, BlendMode.SATURATION, BlendMode.COLOR,
            BlendMode.LUMINOSITY,
        )
        blendMode = if (index == 0) null else modes.getOrNull(index)
        return
    }
    // Before API 29 only the Porter-Duff modes and a few separable blends exist; the rest draw as source-over.
    val modes = arrayOf(
        null, PorterDuff.Mode.SRC_IN, PorterDuff.Mode.SRC_OUT, PorterDuff.Mode.SRC_ATOP, PorterDuff.Mode.DST_OVER, PorterDuff.Mode.DST_IN,
        PorterDuff.Mode.DST_OUT, PorterDuff.Mode.DST_ATOP, PorterDuff.Mode.ADD, PorterDuff.Mode.SRC, PorterDuff.Mode.XOR,
        PorterDuff.Mode.MULTIPLY, PorterDuff.Mode.SCREEN, PorterDuff.Mode.OVERLAY, PorterDuff.Mode.DARKEN, PorterDuff.Mode.LIGHTEN,
    )
    xfermode = modes.getOrNull(index)?.let { PorterDuffXfermode(it) }
}

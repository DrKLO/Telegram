package org.telegram.utils.twidget

import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * The chat's widget glue without a widget running: the text a widget makes selectable, the height cache,
 * font features, damaged PNGs. Instrumented (android.text.Layout, Bitmap); run by the developer.
 */
class WidgetClientTest {

    // ── WidgetText: selectable text as the chat's selection reads it ──

    /** [block, x, y, w, h, baseline, font, letter, word, text] in CSS px; font 0: no font, so the width is shared evenly. */
    private fun run(block: Int, x: Float, y: Float, w: Float, h: Float, text: String) =
        "[$block,$x,$y,$w,$h,${y + h - 4},0,0,0,\"$text\"]"

    private fun parse(vararg runs: String): WidgetText? =
        WidgetText.parse("[" + runs.joinToString(",") + "]", { null }, 1f)

    @Test
    fun selectableTextReadsAsParagraphsOfLines() {
        val text = parse(
            run(1, 0f, 0f, 60f, 20f, "Hello "), run(1, 60f, 0f, 50f, 20f, "world"),
            run(1, 0f, 20f, 50f, 20f, "again"),
            run(2, 0f, 40f, 40f, 20f, "Next"), "[2,0,0,0,0,0,0,0,0,\"\\n\"]", run(2, 0f, 60f, 40f, 20f, "line"),
        )
        assertNotNull(text)
        assertEquals(2, text!!.paragraphs.size)
        val first = text.paragraphs[0]
        // A wrapped line reads on with a space; the paragraph's end has none.
        assertEquals("Hello world again", first.text)
        assertEquals(2, first.layout().lineCount)
        assertEquals(12, first.layout().getLineStart(1))
        // A <br> ends its line with a line break.
        assertEquals("Next\nline", text.paragraphs[1].text)
        assertEquals(40, text.paragraphs[1].top)
    }

    @Test
    fun layoutMapsOffsetsAndPoints() {
        val layout = parse(run(1, 0f, 0f, 60f, 20f, "Hello "), run(1, 60f, 0f, 50f, 20f, "world"))!!.paragraphs[0].layout()
        // "world" starts at 60 and each of its letters is 10 wide.
        assertEquals(60f, layout.getPrimaryHorizontal(6), 0.01f)
        assertEquals(6, layout.getOffsetForHorizontal(0, 62f))
        assertEquals(7, layout.getOffsetForHorizontal(0, 66f))
        assertEquals(110f, layout.getLineRight(0), 0.01f)
        val path = Path()
        layout.getSelectionPath(6, 8, path)
        val bounds = RectF()
        path.computeBounds(bounds, true)
        assertEquals(60f, bounds.left, 0.01f)
        assertEquals(80f, bounds.right, 0.01f)
    }

    @Test
    fun wordsApartOnOneLineCopyWithASpace() {
        val text = parse(run(1, 0f, 0f, 40f, 20f, "left"), run(1, 100f, 0f, 50f, 20f, "right"))
        assertEquals("left right", text!!.paragraphs[0].text)
    }

    @Test
    fun nothingSelectableIsNull() {
        assertNull(WidgetText.parse("[]", { null }, 1f))
    }

    // ── WidgetMedia.Heights: content heights by width ──

    @Test
    fun heightsAnswerTheNearestWidthAndKeepFour() {
        val heights = WidgetMedia.Heights()
        assertEquals(0, heights.near(320))
        assertTrue(heights.put(300, 100))
        assertTrue(heights.put(400, 200))
        assertFalse("the same height at the newest width is no change", heights.put(400, 200))
        assertEquals(200, heights.near(390))
        assertEquals(100, heights.near(310))
        for (w in listOf(500, 600, 700)) heights.put(w, w)
        // Four kept, the oldest (300) dropped: 310 is now nearest to 400.
        assertEquals(200, heights.near(310))
    }

    @Test
    fun heightsKeepTheResizeAnchorOfTheLatestHeight() {
        val heights = WidgetMedia.Heights()
        assertFalse("top until a height says otherwise", heights.anchorBottom())
        assertTrue(heights.put(300, 100, true))
        assertTrue(heights.anchorBottom())
        assertTrue("the same height with another anchor is a change", heights.put(300, 100, false))
        assertFalse(heights.anchorBottom())
        // Rows read from widget_heights: [width, height, anchor], most recent first.
        val loaded = WidgetMedia.Heights()
        loaded.load(arrayListOf(intArrayOf(320, 140, 1), intArrayOf(300, 120, 0)))
        assertTrue(loaded.anchorBottom())
        assertEquals(140, loaded.near(330))
    }

    // ── WidgetSandboxBackend.featureSettings: OpenType features for Paint ──

    @Test
    fun fontFeaturesBecomePaintSyntax() {
        assertEquals("\"tnum\" 1, \"zero\" 1", WidgetSandboxBackend.featureSettings("tnum=1,zero=1"))
        assertNull(WidgetSandboxBackend.featureSettings(""))
        assertNull("bad tags and values are left out", WidgetSandboxBackend.featureSettings("tn=1,abcd=x,\"bad=1"))
    }

    // ── WidgetPng: a damaged PNG keeps its rows before the damage ──

    @Test
    fun damagedPngKeepsItsIntactRows() {
        val width = 2
        val rows = ByteArrayOutputStream()
        for (y in 0 until 4) {
            // Row 2 has an unknown filter type: it and what follows are damage.
            rows.write(if (y == 2) 51 else 0)
            repeat(width) { rows.write(byteArrayOf(0xFF.toByte(), 0, 0)) }
        }
        val png = png(width, 4, rows.toByteArray(), badCrc = true)
        val bitmap = WidgetPng.decodeBroken(png)
        assertNotNull(bitmap)
        assertEquals(Color.RED, bitmap!!.getPixel(0, 0))
        assertEquals(Color.RED, bitmap.getPixel(1, 1))
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 2)))
        assertEquals(0, Color.alpha(bitmap.getPixel(1, 3)))
    }

    @Test
    fun intactPngIsLeftToBitmapFactory() {
        val rows = ByteArrayOutputStream()
        repeat(2) { rows.write(0); repeat(2) { rows.write(byteArrayOf(0, 0xFF.toByte(), 0)) } }
        assertNull(WidgetPng.decodeBroken(png(2, 2, rows.toByteArray(), badCrc = false)))
    }

    private fun png(width: Int, height: Int, raw: ByteArray, badCrc: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A))
        val header = ByteArray(13)
        put32(header, 0, width)
        put32(header, 4, height)
        header[8] = 8
        header[9] = 2 // RGB
        chunk(out, "IHDR", header, false)
        val deflater = Deflater()
        deflater.setInput(raw)
        deflater.finish()
        val data = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) data.write(buffer, 0, deflater.deflate(buffer))
        chunk(out, "IDAT", data.toByteArray(), badCrc)
        chunk(out, "IEND", ByteArray(0), false)
        return out.toByteArray()
    }

    private fun chunk(out: ByteArrayOutputStream, type: String, data: ByteArray, badCrc: Boolean) {
        val length = ByteArray(4)
        put32(length, 0, data.size)
        out.write(length)
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.write(typeBytes)
        out.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        val value = ByteArray(4)
        put32(value, 0, (crc.value.toInt()) xor (if (badCrc) 1 else 0))
        out.write(value)
    }

    private fun put32(bytes: ByteArray, at: Int, value: Int) {
        bytes[at] = (value ushr 24).toByte()
        bytes[at + 1] = (value ushr 16).toByte()
        bytes[at + 2] = (value ushr 8).toByte()
        bytes[at + 3] = value.toByte()
    }
}

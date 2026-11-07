package org.telegram.widgetsandbox.blitz

import java.nio.ByteBuffer

/** JNI surface of renderer-blitz/src/android.rs (repo root). Every call on the main thread. */
internal object BlitzNative {
    init {
        System.loadLibrary("twidget_blitz")
    }

    external fun create(widthDp: Double, dpr: Double, theme: String): Long
    external fun destroy(handle: Long)
    /** Host → renderer JSON message; returns a JSON array of renderer → host messages. */
    external fun receive(handle: Long, json: String): String
    external fun setWidth(handle: Long, widthDp: Double): String
    /** action: 0 down, 1 move, 2 up, 3 cancel; coordinates in CSS px. Returns renderer → host messages. */
    external fun touch(handle: Long, action: Int, x: Double, y: Double, pointer: Int, primary: Boolean): String
    /** (width shl 32) or height, physical px. */
    external fun size(handle: Long): Long
    external fun dirty(handle: Long): Boolean
    /** Paints premultiplied RGBA8 into a direct buffer; returns the paint time in ms (negative on failure). */
    external fun render(handle: Long, buffer: ByteBuffer): Double
    /** "vello" (pixels through `render`) or "canvas" (the frame through `displayList`, drawn by CanvasScene). */
    external fun setBackend(handle: Long, name: String): Boolean
    /** The frame as a display list (renderer-blitz/src/dl.rs); empty on failure. */
    external fun displayList(handle: Long): ByteArray
    /** The font file behind a display-list font id: a direct buffer over memory Rust keeps alive, or null. */
    external fun fontBytes(id: Long): ByteBuffer?
    /** The image behind a display-list image id as premultiplied RGBA8 rows; empty when unknown. */
    external fun imagePixels(id: Long): ByteArray
}

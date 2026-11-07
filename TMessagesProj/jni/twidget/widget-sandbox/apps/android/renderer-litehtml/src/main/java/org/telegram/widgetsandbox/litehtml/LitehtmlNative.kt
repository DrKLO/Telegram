package org.telegram.widgetsandbox.litehtml

/** JNI surface of renderer-litehtml/src/android/jni.cpp (repo root). Every call on the main thread. */
internal object LitehtmlNative {
    init {
        System.loadLibrary("twidget_litehtml")
    }

    /** `backend` answers the font, text-metric and image callbacks for the lifetime of the handle. */
    external fun create(widthDp: Double, dpr: Double, theme: String, backend: LitehtmlBackend): Long
    external fun destroy(handle: Long)
    /** Host → renderer JSON message; returns a JSON array of renderer → host messages. */
    external fun receive(handle: Long, json: String): String
    external fun setWidth(handle: Long, widthDp: Double): String
    external fun setTheme(handle: Long, theme: String): String
    /** action: 0 down, 1 move, 2 up, 3 cancel; coordinates in CSS px. Returns renderer → host messages. */
    external fun touch(handle: Long, action: Int, x: Double, y: Double, pointer: Int, primary: Boolean): String
    /** (width shl 32) or height, CSS px (= dp). */
    external fun size(handle: Long): Long
    external fun dirty(handle: Long): Boolean
    /** Records the document into a display list (JSON, renderer-litehtml/src/display_list.h), CSS px; clears `dirty`. */
    external fun displayList(handle: Long): String
    /** The last rebuild as JSON: serializeMs, parseLayoutMs, passes, htmlBytes, builds, displayOps. */
    external fun stats(handle: Long): String
}

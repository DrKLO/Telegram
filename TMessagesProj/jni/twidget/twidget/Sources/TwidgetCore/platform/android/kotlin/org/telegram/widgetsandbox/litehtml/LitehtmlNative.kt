package org.telegram.widgetsandbox.litehtml

/** JNI surface of platform/android/jni.cpp. Every call on the main thread. */
internal object LitehtmlNative {
    init {
        System.loadLibrary("twidget_litehtml")
    }

    /** `backend` answers the font, text-metric, image and canvas callbacks for the lifetime of the handle. */
    external fun create(widthDp: Double, dpr: Double, theme: String, backend: LitehtmlBackend): Long
    external fun destroy(handle: Long)
    /** Host → renderer JSON message; returns a JSON array of renderer → host messages. */
    external fun receive(handle: Long, json: String): String
    external fun setWidth(handle: Long, widthDp: Double): String
    external fun setMaxHeight(handle: Long, heightDp: Double): String
    external fun setTheme(handle: Long, theme: String): String
    /** action: 0 down, 1 move, 2 up, 3 cancel; coordinates in CSS px. Returns renderer → host messages. */
    external fun touch(handle: Long, action: Int, x: Double, y: Double, pointer: Int, primary: Boolean): String
    /** As touch, from a mouse (no touch events) with modifiers: bit0 alt, bit1 ctrl, bit2 meta, bit3 shift; `button` 2 is the secondary one. */
    external fun pointer(handle: Long, action: Int, x: Double, y: Double, pointer: Int, primary: Boolean, mouse: Boolean, modifiers: Int, button: Int): String
    /** A mouse over the view with no button down (action 0), or leaving it (1). */
    external fun hover(handle: Long, action: Int, x: Double, y: Double, modifiers: Int): String
    /** mode: 0 pixels, 1 lines, 2 pages. */
    external fun wheel(handle: Long, x: Double, y: Double, dx: Double, dy: Double, mode: Int, modifiers: Int): String
    /** type "keydown" or "keyup", with the DOM key and code. */
    external fun key(handle: Long, type: String, key: String, code: String, repeat: Boolean, modifiers: Int): String
    /** The text control being edited as JSON, or "null": cover it with an EditText (Renderer::EditorSpec). */
    external fun editor(handle: Long): String
    /** The EditText's text changed, for editor generation [generation]. */
    external fun edit(handle: Long, generation: Long, text: String, inputType: String): String
    /** The EditText let go of the keyboard. */
    external fun endEditing(handle: Long): String
    /** The text a mouse drag selected, "" without a selection: what the host's copy takes. */
    external fun selectedText(handle: Long): String
    /** Drops the selection, for a press the host handles itself; true when there was one. */
    external fun clearSelection(handle: Long): Boolean
    /** The selectable text as laid out, for the host's own touch selection: `[[block, x, y, w, h, baseline, font,
     *  letterSpacing, wordSpacing, text, [a, b, c, d, e, f]], ...]`, the matrix mapping the box to where it shows. */
    external fun selectableText(handle: Long): String
    /** The document's CSS cursor where the mouse last was; "auto" when it says nothing. */
    external fun cursor(handle: Long): String
    /** The master CSS documents are parsed with, the profile's UA layer with the theme's values in place. */
    external fun masterCss(handle: Long): String
    /** The theme's variables on the root as a JSON object, name to value. */
    external fun themeVariables(handle: Long): String
    /** Each height change then comes with a `resize` in the size message; `prefersBottom` breaks a tie. */
    external fun setResizeTracking(handle: Long, enabled: Boolean, prefersBottom: Boolean)
    /** One display frame (Choreographer): animations advance. Every frame while wantsTick, else after nextTickIn seconds. */
    external fun tick(handle: Long): String
    external fun wantsTick(handle: Long): Boolean
    external fun nextTickIn(handle: Long): Double
    external fun setCompositing(handle: Long, enabled: Boolean)
    /** The layers above the last displayList (src/compositor.h). */
    external fun composition(handle: Long): String
    /** The renderer clock in seconds: what the layers' track starts are on. */
    external fun clock(): Double
    /** Layer `index` of the last composition as a display list; null past the end. */
    external fun layerContent(handle: Long, index: Int): String?
    /** (width shl 32) or height, CSS px (= dp). */
    external fun size(handle: Long): Long
    external fun dirty(handle: Long): Boolean
    /** Records the document into a display list (JSON, src/display_list.h), CSS px; clears `dirty`. */
    external fun displayList(handle: Long): String
    /** Where the last displayList differs from the one before: `[[x, y, w, h], ...]`, or "null" for all. */
    external fun damage(handle: Long): String
    /** Drops caches that are made again on demand: from onTrimMemory, or for a widget scrolled away. */
    external fun trim(handle: Long)
    /** The last rebuild as JSON: serializeMs, parseLayoutMs, passes, htmlBytes, builds, displayOps. */
    external fun stats(handle: Long): String
    /** The size of an image the backend decoded later; rebuilds the document. Returns renderer → host messages. */
    external fun imageReady(handle: Long, image: Int, width: Float, height: Float): String
    /** Diagnostics: the HTML the document was last built from. */
    external fun html(handle: Long): String
    /** Runs a display list FilterPush's "fl" over an ARGB_8888 bitmap's pixels; `scale` is its px per CSS px. */
    external fun filterPixels(bitmap: android.graphics.Bitmap, filters: String, scale: Float)
}

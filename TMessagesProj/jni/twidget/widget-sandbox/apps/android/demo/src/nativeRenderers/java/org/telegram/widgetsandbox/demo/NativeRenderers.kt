package org.telegram.widgetsandbox.demo

import org.telegram.widgetsandbox.RendererFactory
import org.telegram.widgetsandbox.blitz.BlitzRenderer
import org.telegram.widgetsandbox.litehtml.LitehtmlRenderer

/** The frozen native renderer prototypes, built in with -Ptw.nativeRenderers=true (src/noNativeRenderers: the build without them). */
object NativeRenderers {
    val blitz: RendererFactory? = BlitzRenderer.factory
    val blitzCanvas: RendererFactory? = BlitzRenderer.canvasFactory
    val litehtml: RendererFactory? = LitehtmlRenderer.factory
}

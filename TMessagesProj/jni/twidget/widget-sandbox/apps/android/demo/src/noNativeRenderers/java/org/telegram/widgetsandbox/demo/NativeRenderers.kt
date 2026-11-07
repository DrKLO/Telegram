package org.telegram.widgetsandbox.demo

import org.telegram.widgetsandbox.RendererFactory

/** The default demo build: no native renderer prototypes (frozen; -Ptw.nativeRenderers=true builds them, src/nativeRenderers). */
object NativeRenderers {
    val blitz: RendererFactory? = null
    val blitzCanvas: RendererFactory? = null
    val litehtml: RendererFactory? = null
}

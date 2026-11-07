// The JavaScript shipped with the library (built by `pnpm build:android` into
// src/main/assets/WidgetJS): the three bundles and their bytecode.

package org.telegram.widgetsandbox

import android.content.Context
import android.content.res.AssetManager

class SandboxResources internal constructor(private val open: (String) -> ByteArray?) {
    /** The bundles in the library's assets (`WidgetJS/`). */
    constructor(assets: AssetManager) : this({ path -> try { assets.open("WidgetJS/$path").use { it.readBytes() } } catch (e: Exception) { null } })

    companion object {
        @Volatile private var shared: SandboxResources? = null

        /** One instance per process; the bundles are read once. */
        fun get(context: Context): SandboxResources =
            shared ?: synchronized(this) { shared ?: SandboxResources(context.applicationContext.assets).also { shared = it } }
    }

    private fun read(path: String): String =
        open(path)?.toString(Charsets.UTF_8) ?: throw IllegalStateException("WidgetSandbox: missing asset WidgetJS/$path — run `pnpm build:android`")

    val sandboxBundle: String by lazy { read("sandbox-bundle.js") }
    val rendererBundle: String by lazy { read("renderer-bundle.js") }
    val validatorBundle: String by lazy { read("validator-bundle.js") }

    /** Precompiled bytecode of the bundles (`.qbc` next to the `.js`, built together; null when not shipped). */
    val sandboxBytecode: ByteArray? by lazy { open("sandbox-bundle.qbc") }
    val validatorBytecode: ByteArray? by lazy { open("validator-bundle.qbc") }

    /**
     * Why this glue refuses these bundles (the first bundle whose stamp is not this glue's protocol, profile and
     * sdk, BundleStamp.kt), null when it runs them: the engine dies at boot with `bundle-mismatch`, the validator
     * does not build.
     */
    val refusal: String? by lazy {
        BundleStamp.refusal(sandboxBundle, "sandbox-bundle.js")
            ?: BundleStamp.refusal(validatorBundle, "validator-bundle.js")
            ?: BundleStamp.refusal(rendererBundle, "renderer-bundle.js")
    }
}

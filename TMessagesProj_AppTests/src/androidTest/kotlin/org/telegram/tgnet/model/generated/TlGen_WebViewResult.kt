package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_WebViewResult : TlGen_Object {
  public data class TL_webViewResultUrl(
    public val fullsize: Boolean,
    public val fullscreen: Boolean,
    public val query_id: Long?,
    public val url: String,
  ) : TlGen_WebViewResult() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (query_id != null) result = result or 1U
        if (fullsize) result = result or 2U
        if (fullscreen) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      query_id?.let { stream.writeInt64(it) }
      stream.writeString(url)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4D22FF98U
    }
  }
}

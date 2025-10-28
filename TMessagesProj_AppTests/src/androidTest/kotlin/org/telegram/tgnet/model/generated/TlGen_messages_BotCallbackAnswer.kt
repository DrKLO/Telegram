package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_BotCallbackAnswer : TlGen_Object {
  public data class TL_messages_botCallbackAnswer(
    public val alert: Boolean,
    public val has_url: Boolean,
    public val native_ui: Boolean,
    public val message: String?,
    public val url: String?,
    public val cache_time: Int,
  ) : TlGen_messages_BotCallbackAnswer() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (message != null) result = result or 1U
        if (alert) result = result or 2U
        if (url != null) result = result or 4U
        if (has_url) result = result or 8U
        if (native_ui) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      message?.let { stream.writeString(it) }
      url?.let { stream.writeString(it) }
      stream.writeInt32(cache_time)
    }

    public companion object {
      public const val MAGIC: UInt = 0x36585EA4U
    }
  }
}

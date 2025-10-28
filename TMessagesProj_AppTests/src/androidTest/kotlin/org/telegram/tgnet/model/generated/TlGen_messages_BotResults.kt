package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_BotResults : TlGen_Object {
  public data class TL_messages_botResults(
    public val gallery: Boolean,
    public val query_id: Long,
    public val next_offset: String?,
    public val switch_pm: TlGen_InlineBotSwitchPM?,
    public val switch_webview: TlGen_InlineBotWebView?,
    public val results: List<TlGen_BotInlineResult>,
    public val cache_time: Int,
    public val users: List<TlGen_User>,
  ) : TlGen_messages_BotResults() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (gallery) result = result or 1U
        if (next_offset != null) result = result or 2U
        if (switch_pm != null) result = result or 4U
        if (switch_webview != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(query_id)
      next_offset?.let { stream.writeString(it) }
      switch_pm?.serializeToStream(stream)
      switch_webview?.serializeToStream(stream)
      TlGen_Vector.serialize(stream, results)
      stream.writeInt32(cache_time)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE021F2F6U
    }
  }
}

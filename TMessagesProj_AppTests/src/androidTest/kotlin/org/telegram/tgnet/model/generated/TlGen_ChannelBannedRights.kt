package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ChannelBannedRights : TlGen_Object {
  public data class TL_channelBannedRights_layer92(
    public val view_messages: Boolean,
    public val send_messages: Boolean,
    public val send_media: Boolean,
    public val send_stickers: Boolean,
    public val send_gifs: Boolean,
    public val send_games: Boolean,
    public val send_inline: Boolean,
    public val embed_links: Boolean,
    public val until_date: Int,
  ) : TlGen_ChannelBannedRights() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (view_messages) result = result or 1U
        if (send_messages) result = result or 2U
        if (send_media) result = result or 4U
        if (send_stickers) result = result or 8U
        if (send_gifs) result = result or 16U
        if (send_games) result = result or 32U
        if (send_inline) result = result or 64U
        if (embed_links) result = result or 128U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(until_date)
    }

    public companion object {
      public const val MAGIC: UInt = 0x58CF4249U
    }
  }
}

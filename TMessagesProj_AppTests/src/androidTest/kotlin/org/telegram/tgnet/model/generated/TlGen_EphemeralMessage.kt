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

public sealed class TlGen_EphemeralMessage : TlGen_Object {
  public data class TL_ephemeralMessage(
    public val `out`: Boolean,
    public val id: Int,
    public val from_id: TlGen_Peer,
    public val peer_id: TlGen_Peer,
    public val receiver_id: Long,
    public val top_msg_id: Int?,
    public val date: Int,
    public val message: String,
    public val entities: List<TlGen_MessageEntity>?,
    public val media: TlGen_MessageMedia?,
    public val reply_markup: TlGen_ReplyMarkup?,
    public val reply_to: TlGen_MessageReplyHeader?,
  ) : TlGen_EphemeralMessage() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 1U
        if (top_msg_id != null) result = result or 2U
        if (entities != null) result = result or 4U
        if (media != null) result = result or 8U
        if (reply_markup != null) result = result or 16U
        if (reply_to != null) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      from_id.serializeToStream(stream)
      peer_id.serializeToStream(stream)
      stream.writeInt64(receiver_id)
      top_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(date)
      stream.writeString(message)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      media?.serializeToStream(stream)
      reply_markup?.serializeToStream(stream)
      reply_to?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD9C6DC1AU
    }
  }
}

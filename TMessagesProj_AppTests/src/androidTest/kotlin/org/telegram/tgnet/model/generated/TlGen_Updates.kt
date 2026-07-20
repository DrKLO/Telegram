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

public sealed class TlGen_Updates : TlGen_Object {
  public data object TL_updatesTooLong : TlGen_Updates() {
    public const val MAGIC: UInt = 0xE317AF7EU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_updateShort(
    public val update: TlGen_Update,
    public val date: Int,
  ) : TlGen_Updates() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      update.serializeToStream(stream)
      stream.writeInt32(date)
    }

    public companion object {
      public const val MAGIC: UInt = 0x78D4DEC1U
    }
  }

  public data class TL_updatesCombined(
    public val updates: List<TlGen_Update>,
    public val users: List<TlGen_User>,
    public val chats: List<TlGen_Chat>,
    public val date: Int,
    public val seq_start: Int,
    public val seq: Int,
  ) : TlGen_Updates() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, updates)
      TlGen_Vector.serialize(stream, users)
      TlGen_Vector.serialize(stream, chats)
      stream.writeInt32(date)
      stream.writeInt32(seq_start)
      stream.writeInt32(seq)
    }

    public companion object {
      public const val MAGIC: UInt = 0x725B04C3U
    }
  }

  public data class TL_updates(
    public val updates: List<TlGen_Update>,
    public val users: List<TlGen_User>,
    public val chats: List<TlGen_Chat>,
    public val date: Int,
    public val seq: Int,
  ) : TlGen_Updates() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, updates)
      TlGen_Vector.serialize(stream, users)
      TlGen_Vector.serialize(stream, chats)
      stream.writeInt32(date)
      stream.writeInt32(seq)
    }

    public companion object {
      public const val MAGIC: UInt = 0x74AE4240U
    }
  }

  public data class TL_updateShortSentMessage(
    public val `out`: Boolean,
    public val id: Int,
    public val pts: Int,
    public val pts_count: Int,
    public val date: Int,
    public val media: TlGen_MessageMedia?,
    public val entities: List<TlGen_MessageEntity>?,
    public val ttl_period: Int?,
  ) : TlGen_Updates() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (entities != null) result = result or 128U
        if (media != null) result = result or 512U
        if (ttl_period != null) result = result or 33554432U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeInt32(pts)
      stream.writeInt32(pts_count)
      stream.writeInt32(date)
      media?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      ttl_period?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x9015E101U
    }
  }

  public data class TL_updateShortMessage(
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val silent: Boolean,
    public val id: Int,
    public val user_id: Long,
    public val message: String,
    public val pts: Int,
    public val pts_count: Int,
    public val date: Int,
    public val fwd_from: TlGen_MessageFwdHeader?,
    public val via_bot_id: Long?,
    public val reply_to: TlGen_MessageReplyHeader?,
    public val entities: List<TlGen_MessageEntity>?,
    public val ttl_period: Int?,
  ) : TlGen_Updates() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (fwd_from != null) result = result or 4U
        if (reply_to != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (entities != null) result = result or 128U
        if (via_bot_id != null) result = result or 2048U
        if (silent) result = result or 8192U
        if (ttl_period != null) result = result or 33554432U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeInt64(user_id)
      stream.writeString(message)
      stream.writeInt32(pts)
      stream.writeInt32(pts_count)
      stream.writeInt32(date)
      fwd_from?.serializeToStream(stream)
      via_bot_id?.let { stream.writeInt64(it) }
      reply_to?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      ttl_period?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x313BC7F8U
    }
  }

  public data class TL_updateShortChatMessage(
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val silent: Boolean,
    public val id: Int,
    public val from_id: Long,
    public val chat_id: Long,
    public val message: String,
    public val pts: Int,
    public val pts_count: Int,
    public val date: Int,
    public val fwd_from: TlGen_MessageFwdHeader?,
    public val via_bot_id: Long?,
    public val reply_to: TlGen_MessageReplyHeader?,
    public val entities: List<TlGen_MessageEntity>?,
    public val ttl_period: Int?,
  ) : TlGen_Updates() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (fwd_from != null) result = result or 4U
        if (reply_to != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (entities != null) result = result or 128U
        if (via_bot_id != null) result = result or 2048U
        if (silent) result = result or 8192U
        if (ttl_period != null) result = result or 33554432U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeInt64(from_id)
      stream.writeInt64(chat_id)
      stream.writeString(message)
      stream.writeInt32(pts)
      stream.writeInt32(pts_count)
      stream.writeInt32(date)
      fwd_from?.serializeToStream(stream)
      via_bot_id?.let { stream.writeInt64(it) }
      reply_to?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      ttl_period?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x4D6DEEA5U
    }
  }
}

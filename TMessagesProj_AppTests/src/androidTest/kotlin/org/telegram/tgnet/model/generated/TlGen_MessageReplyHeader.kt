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

public sealed class TlGen_MessageReplyHeader : TlGen_Object {
  public data class TL_messageReplyStoryHeader(
    public val peer: TlGen_Peer,
    public val story_id: Int,
  ) : TlGen_MessageReplyHeader() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(story_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0E5AF939U
    }
  }

  public data class TL_messageReplyHeader(
    public val reply_to_scheduled: Boolean,
    public val forum_topic: Boolean,
    public val quote: Boolean,
    public val reply_to_msg_id: Int?,
    public val reply_to_peer_id: TlGen_Peer?,
    public val reply_from: TlGen_MessageFwdHeader?,
    public val reply_media: TlGen_MessageMedia?,
    public val reply_to_top_id: Int?,
    public val quote_text: String?,
    public val quote_entities: List<TlGen_MessageEntity>?,
    public val quote_offset: Int?,
    public val todo_item_id: Int?,
  ) : TlGen_MessageReplyHeader() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (reply_to_peer_id != null) result = result or 1U
        if (reply_to_top_id != null) result = result or 2U
        if (reply_to_scheduled) result = result or 4U
        if (forum_topic) result = result or 8U
        if (reply_to_msg_id != null) result = result or 16U
        if (reply_from != null) result = result or 32U
        if (quote_text != null) result = result or 64U
        if (quote_entities != null) result = result or 128U
        if (reply_media != null) result = result or 256U
        if (quote) result = result or 512U
        if (quote_offset != null) result = result or 1024U
        if (todo_item_id != null) result = result or 2048U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      reply_to_msg_id?.let { stream.writeInt32(it) }
      reply_to_peer_id?.serializeToStream(stream)
      reply_from?.serializeToStream(stream)
      reply_media?.serializeToStream(stream)
      reply_to_top_id?.let { stream.writeInt32(it) }
      quote_text?.let { stream.writeString(it) }
      quote_entities?.let { TlGen_Vector.serialize(stream, it) }
      quote_offset?.let { stream.writeInt32(it) }
      todo_item_id?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x6917560BU
    }
  }

  public data class TL_messageReplyHeader_layer165(
    public val reply_to_scheduled: Boolean,
    public val forum_topic: Boolean,
    public val reply_to_msg_id: Int,
    public val reply_to_peer_id: TlGen_Peer?,
    public val reply_to_top_id: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (reply_to_peer_id != null) result = result or 1U
        if (reply_to_top_id != null) result = result or 2U
        if (reply_to_scheduled) result = result or 4U
        if (forum_topic) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(reply_to_msg_id)
      reply_to_peer_id?.serializeToStream(stream)
      reply_to_top_id?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xA6D57763U
    }
  }

  public data class TL_messageReplyStoryHeader_layer173(
    public val user_id: Long,
    public val story_id: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(user_id)
      stream.writeInt32(story_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9C98BFC1U
    }
  }

  public data class TL_messageReplyHeader_layer207(
    public val reply_to_scheduled: Boolean,
    public val forum_topic: Boolean,
    public val quote: Boolean,
    public val reply_to_msg_id: Int?,
    public val reply_to_peer_id: TlGen_Peer?,
    public val reply_from: TlGen_MessageFwdHeader?,
    public val reply_media: TlGen_MessageMedia?,
    public val reply_to_top_id: Int?,
    public val quote_text: String?,
    public val quote_entities: List<TlGen_MessageEntity>?,
    public val quote_offset: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (reply_to_peer_id != null) result = result or 1U
        if (reply_to_top_id != null) result = result or 2U
        if (reply_to_scheduled) result = result or 4U
        if (forum_topic) result = result or 8U
        if (reply_to_msg_id != null) result = result or 16U
        if (reply_from != null) result = result or 32U
        if (quote_text != null) result = result or 64U
        if (quote_entities != null) result = result or 128U
        if (reply_media != null) result = result or 256U
        if (quote) result = result or 512U
        if (quote_offset != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      reply_to_msg_id?.let { stream.writeInt32(it) }
      reply_to_peer_id?.serializeToStream(stream)
      reply_from?.serializeToStream(stream)
      reply_media?.serializeToStream(stream)
      reply_to_top_id?.let { stream.writeInt32(it) }
      quote_text?.let { stream.writeString(it) }
      quote_entities?.let { TlGen_Vector.serialize(stream, it) }
      quote_offset?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xAFBC09DBU
    }
  }

  public data class TL_messageReplyHeader_layer166(
    public val reply_to_scheduled: Boolean,
    public val forum_topic: Boolean,
    public val quote: Boolean,
    public val reply_to_msg_id: Int?,
    public val reply_to_peer_id: TlGen_Peer?,
    public val reply_from: TlGen_MessageFwdHeader?,
    public val reply_media: TlGen_MessageMedia?,
    public val reply_to_top_id: Int?,
    public val quote_text: String?,
    public val quote_entities: List<TlGen_MessageEntity>?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (reply_to_peer_id != null) result = result or 1U
        if (reply_to_top_id != null) result = result or 2U
        if (reply_to_scheduled) result = result or 4U
        if (forum_topic) result = result or 8U
        if (reply_to_msg_id != null) result = result or 16U
        if (reply_from != null) result = result or 32U
        if (quote_text != null) result = result or 64U
        if (quote_entities != null) result = result or 128U
        if (reply_media != null) result = result or 256U
        if (quote) result = result or 512U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      reply_to_msg_id?.let { stream.writeInt32(it) }
      reply_to_peer_id?.serializeToStream(stream)
      reply_from?.serializeToStream(stream)
      reply_media?.serializeToStream(stream)
      reply_to_top_id?.let { stream.writeInt32(it) }
      quote_text?.let { stream.writeString(it) }
      quote_entities?.let { TlGen_Vector.serialize(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x6EEBCABDU
    }
  }
}

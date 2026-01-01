package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputReplyTo : TlGen_Object {
  public data class TL_inputReplyToStory(
    public val peer: TlGen_InputPeer,
    public val story_id: Int,
  ) : TlGen_InputReplyTo() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(story_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5881323AU
    }
  }

  public data class TL_inputReplyToMonoForum(
    public val monoforum_peer_id: TlGen_InputPeer,
  ) : TlGen_InputReplyTo() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      monoforum_peer_id.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x69D66C45U
    }
  }

  public data class TL_inputReplyToMessage(
    public val reply_to_msg_id: Int,
    public val top_msg_id: Int?,
    public val reply_to_peer_id: TlGen_InputPeer?,
    public val quote_text: String?,
    public val quote_entities: List<TlGen_MessageEntity>?,
    public val quote_offset: Int?,
    public val monoforum_peer_id: TlGen_InputPeer?,
    public val todo_item_id: Int?,
  ) : TlGen_InputReplyTo() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (top_msg_id != null) result = result or 1U
        if (reply_to_peer_id != null) result = result or 2U
        if (quote_text != null) result = result or 4U
        if (quote_entities != null) result = result or 8U
        if (quote_offset != null) result = result or 16U
        if (monoforum_peer_id != null) result = result or 32U
        if (todo_item_id != null) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(reply_to_msg_id)
      top_msg_id?.let { stream.writeInt32(it) }
      reply_to_peer_id?.serializeToStream(stream)
      quote_text?.let { stream.writeString(it) }
      quote_entities?.let { TlGen_Vector.serialize(stream, it) }
      quote_offset?.let { stream.writeInt32(it) }
      monoforum_peer_id?.serializeToStream(stream)
      todo_item_id?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x869FBE10U
    }
  }

  public data class TL_inputReplyToStory_layer173(
    public val user_id: TlGen_InputUser,
    public val story_id: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      user_id.serializeToStream(stream)
      stream.writeInt32(story_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x15B0F283U
    }
  }

  public data class TL_inputReplyToMessage_layer203(
    public val reply_to_msg_id: Int,
    public val top_msg_id: Int?,
    public val reply_to_peer_id: TlGen_InputPeer?,
    public val quote_text: String?,
    public val quote_entities: List<TlGen_MessageEntity>?,
    public val quote_offset: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (top_msg_id != null) result = result or 1U
        if (reply_to_peer_id != null) result = result or 2U
        if (quote_text != null) result = result or 4U
        if (quote_entities != null) result = result or 8U
        if (quote_offset != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(reply_to_msg_id)
      top_msg_id?.let { stream.writeInt32(it) }
      reply_to_peer_id?.serializeToStream(stream)
      quote_text?.let { stream.writeString(it) }
      quote_entities?.let { TlGen_Vector.serialize(stream, it) }
      quote_offset?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x22C0F6D5U
    }
  }

  public data class TL_inputReplyToMessage_layer166(
    public val reply_to_msg_id: Int,
    public val top_msg_id: Int?,
    public val reply_to_peer_id: TlGen_InputPeer?,
    public val quote_text: String?,
    public val quote_entities: List<TlGen_MessageEntity>?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (top_msg_id != null) result = result or 1U
        if (reply_to_peer_id != null) result = result or 2U
        if (quote_text != null) result = result or 4U
        if (quote_entities != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(reply_to_msg_id)
      top_msg_id?.let { stream.writeInt32(it) }
      reply_to_peer_id?.serializeToStream(stream)
      quote_text?.let { stream.writeString(it) }
      quote_entities?.let { TlGen_Vector.serialize(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x073EC805U
    }
  }

  public data class TL_inputReplyToMessage_layer207(
    public val reply_to_msg_id: Int,
    public val top_msg_id: Int?,
    public val reply_to_peer_id: TlGen_InputPeer?,
    public val quote_text: String?,
    public val quote_entities: List<TlGen_MessageEntity>?,
    public val quote_offset: Int?,
    public val monoforum_peer_id: TlGen_InputPeer?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (top_msg_id != null) result = result or 1U
        if (reply_to_peer_id != null) result = result or 2U
        if (quote_text != null) result = result or 4U
        if (quote_entities != null) result = result or 8U
        if (quote_offset != null) result = result or 16U
        if (monoforum_peer_id != null) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(reply_to_msg_id)
      top_msg_id?.let { stream.writeInt32(it) }
      reply_to_peer_id?.serializeToStream(stream)
      quote_text?.let { stream.writeString(it) }
      quote_entities?.let { TlGen_Vector.serialize(stream, it) }
      quote_offset?.let { stream.writeInt32(it) }
      monoforum_peer_id?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB07038B0U
    }
  }
}

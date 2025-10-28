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

public sealed class TlGen_DraftMessage : TlGen_Object {
  public data class TL_draftMessageEmpty(
    public val date: Int?,
  ) : TlGen_DraftMessage() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (date != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      date?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x1B0C841AU
    }
  }

  public data class TL_draftMessage(
    public val no_webpage: Boolean,
    public val invert_media: Boolean,
    public val reply_to: TlGen_InputReplyTo?,
    public val message: String,
    public val entities: List<TlGen_MessageEntity>?,
    public val media: TlGen_InputMedia?,
    public val date: Int,
    public val effect: Long?,
    public val suggested_post: TlGen_SuggestedPost?,
  ) : TlGen_DraftMessage() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (no_webpage) result = result or 2U
        if (entities != null) result = result or 8U
        if (reply_to != null) result = result or 16U
        if (media != null) result = result or 32U
        if (invert_media) result = result or 64U
        if (effect != null) result = result or 128U
        if (suggested_post != null) result = result or 256U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      reply_to?.serializeToStream(stream)
      stream.writeString(message)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      media?.serializeToStream(stream)
      stream.writeInt32(date)
      effect?.let { stream.writeInt64(it) }
      suggested_post?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x96EAA5EBU
    }
  }

  public data class TL_draftMessage_layer165(
    public val no_webpage: Boolean,
    public val reply_to_msg_id: Int?,
    public val message: String,
    public val entities: List<TlGen_MessageEntity>?,
    public val date: Int,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (reply_to_msg_id != null) result = result or 1U
        if (no_webpage) result = result or 2U
        if (entities != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      reply_to_msg_id?.let { stream.writeInt32(it) }
      stream.writeString(message)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      stream.writeInt32(date)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFD8E711FU
    }
  }

  public data class TL_draftMessage_layer181(
    public val no_webpage: Boolean,
    public val invert_media: Boolean,
    public val reply_to: TlGen_InputReplyTo?,
    public val message: String,
    public val entities: List<TlGen_MessageEntity>?,
    public val media: TlGen_InputMedia?,
    public val date: Int,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (no_webpage) result = result or 2U
        if (entities != null) result = result or 8U
        if (reply_to != null) result = result or 16U
        if (media != null) result = result or 32U
        if (invert_media) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      reply_to?.serializeToStream(stream)
      stream.writeString(message)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      media?.serializeToStream(stream)
      stream.writeInt32(date)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3FCCF7EFU
    }
  }

  public data class TL_draftMessage_layer205(
    public val no_webpage: Boolean,
    public val invert_media: Boolean,
    public val reply_to: TlGen_InputReplyTo?,
    public val message: String,
    public val entities: List<TlGen_MessageEntity>?,
    public val media: TlGen_InputMedia?,
    public val date: Int,
    public val effect: Long?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (no_webpage) result = result or 2U
        if (entities != null) result = result or 8U
        if (reply_to != null) result = result or 16U
        if (media != null) result = result or 32U
        if (invert_media) result = result or 64U
        if (effect != null) result = result or 128U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      reply_to?.serializeToStream(stream)
      stream.writeString(message)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      media?.serializeToStream(stream)
      stream.writeInt32(date)
      effect?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x2D65321FU
    }
  }
}

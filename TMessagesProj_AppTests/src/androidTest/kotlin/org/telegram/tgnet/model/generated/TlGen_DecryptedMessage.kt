package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_DecryptedMessage : TlGen_Object {
  public data class TL_decryptedMessage_layer8(
    public val random_id: Long,
    public val random_bytes: List<Byte>,
    public val message: String,
    public val media: TlGen_DecryptedMessageMedia,
  ) : TlGen_DecryptedMessage() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(random_id)
      stream.writeByteArray(random_bytes.toByteArray())
      stream.writeString(message)
      media.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1F814F1FU
    }
  }

  public data class TL_decryptedMessage_layer17(
    public val random_id: Long,
    public val ttl: Int,
    public val message: String,
    public val media: TlGen_DecryptedMessageMedia,
  ) : TlGen_DecryptedMessage() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(random_id)
      stream.writeInt32(ttl)
      stream.writeString(message)
      media.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x204D3878U
    }
  }

  public data class TL_decryptedMessage_layer45(
    public val random_id: Long,
    public val ttl: Int,
    public val message: String,
    public val media: TlGen_DecryptedMessageMedia?,
    public val entities: List<TlGen_MessageEntity>?,
    public val via_bot_name: String?,
    public val reply_to_random_id: Long?,
  ) : TlGen_DecryptedMessage() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (reply_to_random_id != null) result = result or 8U
        if (entities != null) result = result or 128U
        if (media != null) result = result or 512U
        if (via_bot_name != null) result = result or 2048U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(random_id)
      stream.writeInt32(ttl)
      stream.writeString(message)
      media?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      via_bot_name?.let { stream.writeString(it) }
      reply_to_random_id?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x36B091DEU
    }
  }

  public data class TL_decryptedMessage_layer73(
    public val random_id: Long,
    public val ttl: Int,
    public val message: String,
    public val media: TlGen_DecryptedMessageMedia?,
    public val entities: List<TlGen_MessageEntity>?,
    public val via_bot_name: String?,
    public val reply_to_random_id: Long?,
    public val grouped_id: Long?,
  ) : TlGen_DecryptedMessage() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (reply_to_random_id != null) result = result or 8U
        if (entities != null) result = result or 128U
        if (media != null) result = result or 512U
        if (via_bot_name != null) result = result or 2048U
        if (grouped_id != null) result = result or 131072U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(random_id)
      stream.writeInt32(ttl)
      stream.writeString(message)
      media?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      via_bot_name?.let { stream.writeString(it) }
      reply_to_random_id?.let { stream.writeInt64(it) }
      grouped_id?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x91CC4674U
    }
  }

  public data class TL_decryptedMessageService_layer8(
    public val random_id: Long,
    public val random_bytes: List<Byte>,
    public val action: TlGen_DecryptedMessageAction,
  ) : TlGen_DecryptedMessage() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(random_id)
      stream.writeByteArray(random_bytes.toByteArray())
      action.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAA48327DU
    }
  }

  public data class TL_decryptedMessageService_layer17(
    public val random_id: Long,
    public val action: TlGen_DecryptedMessageAction,
  ) : TlGen_DecryptedMessage() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(random_id)
      action.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x73164160U
    }
  }
}

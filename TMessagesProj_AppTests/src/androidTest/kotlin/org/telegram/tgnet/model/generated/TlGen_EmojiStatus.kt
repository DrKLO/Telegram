package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_EmojiStatus : TlGen_Object {
  public data object TL_emojiStatusEmpty : TlGen_EmojiStatus() {
    public const val MAGIC: UInt = 0x2DE11AAEU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_emojiStatus(
    public val document_id: Long,
    public val until: Int?,
  ) : TlGen_EmojiStatus() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (until != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(document_id)
      until?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xE7FF068AU
    }
  }

  public data class TL_emojiStatusCollectible(
    public val collectible_id: Long,
    public val document_id: Long,
    public val title: String,
    public val slug: String,
    public val pattern_document_id: Long,
    public val center_color: Int,
    public val edge_color: Int,
    public val pattern_color: Int,
    public val text_color: Int,
    public val until: Int?,
  ) : TlGen_EmojiStatus() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (until != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(collectible_id)
      stream.writeInt64(document_id)
      stream.writeString(title)
      stream.writeString(slug)
      stream.writeInt64(pattern_document_id)
      stream.writeInt32(center_color)
      stream.writeInt32(edge_color)
      stream.writeInt32(pattern_color)
      stream.writeInt32(text_color)
      until?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x7184603BU
    }
  }

  public data class TL_inputEmojiStatusCollectible(
    public val collectible_id: Long,
    public val until: Int?,
  ) : TlGen_EmojiStatus() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (until != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(collectible_id)
      until?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x07141DBFU
    }
  }

  public data class TL_emojiStatus_layer197(
    public val document_id: Long,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(document_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x929B619DU
    }
  }

  public data class TL_emojiStatusUntil_layer197(
    public val document_id: Long,
    public val until: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(document_id)
      stream.writeInt32(until)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFA30A8C7U
    }
  }
}

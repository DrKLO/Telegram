package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ReplyMarkup : TlGen_Object {
  public data class TL_replyKeyboardHide(
    public val selective: Boolean,
  ) : TlGen_ReplyMarkup() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (selective) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
    }

    public companion object {
      public const val MAGIC: UInt = 0xA03E5B85U
    }
  }

  public data class TL_replyInlineMarkup(
    public val rows: List<TlGen_KeyboardButtonRow>,
  ) : TlGen_ReplyMarkup() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, rows)
    }

    public companion object {
      public const val MAGIC: UInt = 0x48A30254U
    }
  }

  public data class TL_replyKeyboardForceReply(
    public val single_use: Boolean,
    public val selective: Boolean,
    public val placeholder: String?,
  ) : TlGen_ReplyMarkup() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (single_use) result = result or 2U
        if (selective) result = result or 4U
        if (placeholder != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      placeholder?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x86B40B08U
    }
  }

  public data class TL_replyKeyboardMarkup(
    public val resize: Boolean,
    public val single_use: Boolean,
    public val selective: Boolean,
    public val persistent: Boolean,
    public val rows: List<TlGen_KeyboardButtonRow>,
    public val placeholder: String?,
  ) : TlGen_ReplyMarkup() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (resize) result = result or 1U
        if (single_use) result = result or 2U
        if (selective) result = result or 4U
        if (placeholder != null) result = result or 8U
        if (persistent) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serialize(stream, rows)
      placeholder?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x85DD99D1U
    }
  }

  public data class TL_replyKeyboardForceReply_layer129(
    public val single_use: Boolean,
    public val selective: Boolean,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (single_use) result = result or 2U
        if (selective) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
    }

    public companion object {
      public const val MAGIC: UInt = 0xF4108AA0U
    }
  }

  public data class TL_replyKeyboardMarkup_layer129(
    public val resize: Boolean,
    public val single_use: Boolean,
    public val selective: Boolean,
    public val rows: List<TlGen_KeyboardButtonRow>,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (resize) result = result or 1U
        if (single_use) result = result or 2U
        if (selective) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serialize(stream, rows)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3502758CU
    }
  }
}

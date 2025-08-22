package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_MessageEntity : TlGen_Object {
  public data class TL_messageEntityUnknown(
    public val offset: Int,
    public val length: Int,
  ) : TlGen_MessageEntity() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(offset)
      stream.writeInt32(length)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBB92BA95U
    }
  }

  public data class TL_messageEntityMention(
    public val offset: Int,
    public val length: Int,
  ) : TlGen_MessageEntity() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(offset)
      stream.writeInt32(length)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFA04579DU
    }
  }

  public data class TL_messageEntityHashtag(
    public val offset: Int,
    public val length: Int,
  ) : TlGen_MessageEntity() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(offset)
      stream.writeInt32(length)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6F635B0DU
    }
  }

  public data class TL_messageEntityBotCommand(
    public val offset: Int,
    public val length: Int,
  ) : TlGen_MessageEntity() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(offset)
      stream.writeInt32(length)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6CEF8AC7U
    }
  }

  public data class TL_messageEntityUrl(
    public val offset: Int,
    public val length: Int,
  ) : TlGen_MessageEntity() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(offset)
      stream.writeInt32(length)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6ED02538U
    }
  }

  public data class TL_messageEntityEmail(
    public val offset: Int,
    public val length: Int,
  ) : TlGen_MessageEntity() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(offset)
      stream.writeInt32(length)
    }

    public companion object {
      public const val MAGIC: UInt = 0x64E475C2U
    }
  }

  public data class TL_messageEntityBold(
    public val offset: Int,
    public val length: Int,
  ) : TlGen_MessageEntity() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(offset)
      stream.writeInt32(length)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBD610BC9U
    }
  }

  public data class TL_messageEntityItalic(
    public val offset: Int,
    public val length: Int,
  ) : TlGen_MessageEntity() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(offset)
      stream.writeInt32(length)
    }

    public companion object {
      public const val MAGIC: UInt = 0x826F8B60U
    }
  }

  public data class TL_messageEntityCode(
    public val offset: Int,
    public val length: Int,
  ) : TlGen_MessageEntity() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(offset)
      stream.writeInt32(length)
    }

    public companion object {
      public const val MAGIC: UInt = 0x28A20571U
    }
  }

  public data class TL_messageEntityPre(
    public val offset: Int,
    public val length: Int,
    public val language: String,
  ) : TlGen_MessageEntity() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(offset)
      stream.writeInt32(length)
      stream.writeString(language)
    }

    public companion object {
      public const val MAGIC: UInt = 0x73924BE0U
    }
  }

  public data class TL_messageEntityTextUrl(
    public val offset: Int,
    public val length: Int,
    public val url: String,
  ) : TlGen_MessageEntity() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(offset)
      stream.writeInt32(length)
      stream.writeString(url)
    }

    public companion object {
      public const val MAGIC: UInt = 0x76A6D327U
    }
  }

  public data class TL_inputMessageEntityMentionName(
    public val offset: Int,
    public val length: Int,
    public val user_id: TlGen_InputUser,
  ) : TlGen_MessageEntity() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(offset)
      stream.writeInt32(length)
      user_id.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x208E68C9U
    }
  }

  public data class TL_messageEntityPhone(
    public val offset: Int,
    public val length: Int,
  ) : TlGen_MessageEntity() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(offset)
      stream.writeInt32(length)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9B69E34BU
    }
  }

  public data class TL_messageEntityCashtag(
    public val offset: Int,
    public val length: Int,
  ) : TlGen_MessageEntity() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(offset)
      stream.writeInt32(length)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4C4E743FU
    }
  }

  public data class TL_messageEntityUnderline(
    public val offset: Int,
    public val length: Int,
  ) : TlGen_MessageEntity() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(offset)
      stream.writeInt32(length)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9C4E7E8BU
    }
  }

  public data class TL_messageEntityStrike(
    public val offset: Int,
    public val length: Int,
  ) : TlGen_MessageEntity() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(offset)
      stream.writeInt32(length)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBF0693D4U
    }
  }

  public data class TL_messageEntityBankCard(
    public val offset: Int,
    public val length: Int,
  ) : TlGen_MessageEntity() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(offset)
      stream.writeInt32(length)
    }

    public companion object {
      public const val MAGIC: UInt = 0x761E6AF4U
    }
  }

  public data class TL_messageEntityMentionName(
    public val offset: Int,
    public val length: Int,
    public val user_id: Long,
  ) : TlGen_MessageEntity() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(offset)
      stream.writeInt32(length)
      stream.writeInt64(user_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDC7B1140U
    }
  }

  public data class TL_messageEntitySpoiler(
    public val offset: Int,
    public val length: Int,
  ) : TlGen_MessageEntity() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(offset)
      stream.writeInt32(length)
    }

    public companion object {
      public const val MAGIC: UInt = 0x32CA960FU
    }
  }

  public data class TL_messageEntityCustomEmoji(
    public val offset: Int,
    public val length: Int,
    public val document_id: Long,
  ) : TlGen_MessageEntity() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(offset)
      stream.writeInt32(length)
      stream.writeInt64(document_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC8CF05F8U
    }
  }

  public data class TL_messageEntityBlockquote(
    public val collapsed: Boolean,
    public val offset: Int,
    public val length: Int,
  ) : TlGen_MessageEntity() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (collapsed) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(offset)
      stream.writeInt32(length)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF1CCAAACU
    }
  }

  public data class TL_messageEntityMentionName_layer132(
    public val offset: Int,
    public val length: Int,
    public val user_id: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(offset)
      stream.writeInt32(length)
      stream.writeInt32(user_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x352DCA58U
    }
  }

  public data class TL_messageEntityBlockquote_layer180(
    public val offset: Int,
    public val length: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(offset)
      stream.writeInt32(length)
    }

    public companion object {
      public const val MAGIC: UInt = 0x020DF5D0U
    }
  }
}

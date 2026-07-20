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

public sealed class TlGen_RichText : TlGen_Object {
  public data object TL_textEmpty : TlGen_RichText() {
    public const val MAGIC: UInt = 0xDC3D824FU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_textPlain(
    public val text: String,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0x744694E0U
    }
  }

  public data class TL_textBold(
    public val text: TlGen_RichText,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6724ABC4U
    }
  }

  public data class TL_textItalic(
    public val text: TlGen_RichText,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD912A59CU
    }
  }

  public data class TL_textUnderline(
    public val text: TlGen_RichText,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC12622C4U
    }
  }

  public data class TL_textStrike(
    public val text: TlGen_RichText,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9BF8BB95U
    }
  }

  public data class TL_textFixed(
    public val text: TlGen_RichText,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6C3F19B9U
    }
  }

  public data class TL_textUrl(
    public val text: TlGen_RichText,
    public val url: String,
    public val webpage_id: Long,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
      stream.writeString(url)
      stream.writeInt64(webpage_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3C2884C1U
    }
  }

  public data class TL_textEmail(
    public val text: TlGen_RichText,
    public val email: String,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
      stream.writeString(email)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDE5A0DD6U
    }
  }

  public data class TL_textConcat(
    public val texts: List<TlGen_RichText>,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, texts)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7E6260D7U
    }
  }

  public data class TL_textSubscript(
    public val text: TlGen_RichText,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xED6A8504U
    }
  }

  public data class TL_textSuperscript(
    public val text: TlGen_RichText,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC7FB5E01U
    }
  }

  public data class TL_textMarked(
    public val text: TlGen_RichText,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x034B8621U
    }
  }

  public data class TL_textPhone(
    public val text: TlGen_RichText,
    public val phone: String,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
      stream.writeString(phone)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1CCB966AU
    }
  }

  public data class TL_textImage(
    public val document_id: Long,
    public val w: Int,
    public val h: Int,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(document_id)
      stream.writeInt32(w)
      stream.writeInt32(h)
    }

    public companion object {
      public const val MAGIC: UInt = 0x081CCF4FU
    }
  }

  public data class TL_textAnchor(
    public val text: TlGen_RichText,
    public val name: String,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
      stream.writeString(name)
    }

    public companion object {
      public const val MAGIC: UInt = 0x35553762U
    }
  }

  public data class TL_textMath(
    public val source: String,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(source)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9D2EAC97U
    }
  }

  public data class TL_textCustomEmoji(
    public val document_id: Long,
    public val alt: String,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(document_id)
      stream.writeString(alt)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA26156C0U
    }
  }

  public data class TL_textSpoiler(
    public val text: TlGen_RichText,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4C2A5D62U
    }
  }

  public data class TL_textMention(
    public val text: TlGen_RichText,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCD24CF44U
    }
  }

  public data class TL_textHashtag(
    public val text: TlGen_RichText,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x519524EAU
    }
  }

  public data class TL_textBotCommand(
    public val text: TlGen_RichText,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x02FF29D3U
    }
  }

  public data class TL_textCashtag(
    public val text: TlGen_RichText,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7B9E1801U
    }
  }

  public data class TL_textAutoUrl(
    public val text: TlGen_RichText,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAC6A83AAU
    }
  }

  public data class TL_textAutoEmail(
    public val text: TlGen_RichText,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC556A45DU
    }
  }

  public data class TL_textAutoPhone(
    public val text: TlGen_RichText,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x24C26789U
    }
  }

  public data class TL_textBankCard(
    public val text: TlGen_RichText,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB956812DU
    }
  }

  public data class TL_textMentionName(
    public val text: TlGen_RichText,
    public val user_id: Long,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
      stream.writeInt64(user_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x01A9FBFCU
    }
  }

  public data class TL_textDate(
    public val relative: Boolean,
    public val short_time: Boolean,
    public val long_time: Boolean,
    public val short_date: Boolean,
    public val long_date: Boolean,
    public val day_of_week: Boolean,
    public val text: TlGen_RichText,
    public val date: Int,
  ) : TlGen_RichText() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (relative) result = result or 1U
        if (short_time) result = result or 2U
        if (long_time) result = result or 4U
        if (short_date) result = result or 8U
        if (long_date) result = result or 16U
        if (day_of_week) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      text.serializeToStream(stream)
      stream.writeInt32(date)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA5B45E2BU
    }
  }

  public data class TL_textDiff(
    public val text: TlGen_RichText,
    public val old_text: TlGen_RichText,
  ) : TlGen_RichText() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
      old_text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9686CB50U
    }
  }
}

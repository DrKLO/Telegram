package org.telegram.tgnet.model.generated

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
}

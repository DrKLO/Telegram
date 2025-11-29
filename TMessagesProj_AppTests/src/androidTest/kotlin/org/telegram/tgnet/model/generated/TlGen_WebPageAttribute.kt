package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_WebPageAttribute : TlGen_Object {
  public data class TL_webPageAttributeTheme(
    public val documents: List<TlGen_Document>?,
    public val settings: TlGen_ThemeSettings?,
  ) : TlGen_WebPageAttribute() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (documents != null) result = result or 1U
        if (settings != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      documents?.let { TlGen_Vector.serialize(stream, it) }
      settings?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x54B56617U
    }
  }

  public data class TL_webPageAttributeStory(
    public val peer: TlGen_Peer,
    public val id: Int,
    public val story: TlGen_StoryItem?,
  ) : TlGen_WebPageAttribute() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (story != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(id)
      story?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2E94C3E7U
    }
  }

  public data class TL_webPageAttributeStickerSet(
    public val emojis: Boolean,
    public val text_color: Boolean,
    public val stickers: List<TlGen_Document>,
  ) : TlGen_WebPageAttribute() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (emojis) result = result or 1U
        if (text_color) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serialize(stream, stickers)
    }

    public companion object {
      public const val MAGIC: UInt = 0x50CC03D3U
    }
  }

  public data class TL_webPageAttributeUniqueStarGift(
    public val gift: TlGen_StarGift,
  ) : TlGen_WebPageAttribute() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      gift.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCF6F6DB8U
    }
  }

  public data class TL_webPageAttributeStarGiftCollection(
    public val icons: List<TlGen_Document>,
  ) : TlGen_WebPageAttribute() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, icons)
    }

    public companion object {
      public const val MAGIC: UInt = 0x31CAD303U
    }
  }

  public data class TL_webPageAttributeStarGiftAuction(
    public val gift: TlGen_StarGift,
    public val end_date: Int,
    public val center_color: Int,
    public val edge_color: Int,
    public val text_color: Int,
  ) : TlGen_WebPageAttribute() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      gift.serializeToStream(stream)
      stream.writeInt32(end_date)
      stream.writeInt32(center_color)
      stream.writeInt32(edge_color)
      stream.writeInt32(text_color)
    }

    public companion object {
      public const val MAGIC: UInt = 0x034986ABU
    }
  }

  public data class TL_webPageAttributeStory_layer163(
    public val user_id: Long,
    public val id: Int,
    public val story: TlGen_StoryItem?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (story != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(user_id)
      stream.writeInt32(id)
      story?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x939A4671U
    }
  }
}

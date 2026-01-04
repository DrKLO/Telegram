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

public sealed class TlGen_PageBlock : TlGen_Object {
  public data object TL_pageBlockUnsupported : TlGen_PageBlock() {
    public const val MAGIC: UInt = 0x13567E8AU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_pageBlockTitle(
    public val text: TlGen_RichText,
  ) : TlGen_PageBlock() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x70ABC3FDU
    }
  }

  public data class TL_pageBlockSubtitle(
    public val text: TlGen_RichText,
  ) : TlGen_PageBlock() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8FFA9A1FU
    }
  }

  public data class TL_pageBlockHeader(
    public val text: TlGen_RichText,
  ) : TlGen_PageBlock() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBFD064ECU
    }
  }

  public data class TL_pageBlockSubheader(
    public val text: TlGen_RichText,
  ) : TlGen_PageBlock() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF12BB6E1U
    }
  }

  public data class TL_pageBlockParagraph(
    public val text: TlGen_RichText,
  ) : TlGen_PageBlock() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x467A0766U
    }
  }

  public data class TL_pageBlockPreformatted(
    public val text: TlGen_RichText,
    public val language: String,
  ) : TlGen_PageBlock() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
      stream.writeString(language)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC070D93EU
    }
  }

  public data class TL_pageBlockFooter(
    public val text: TlGen_RichText,
  ) : TlGen_PageBlock() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x48870999U
    }
  }

  public data object TL_pageBlockDivider : TlGen_PageBlock() {
    public const val MAGIC: UInt = 0xDB20B188U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_pageBlockAnchor(
    public val name: String,
  ) : TlGen_PageBlock() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(name)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCE0D37B0U
    }
  }

  public data class TL_pageBlockBlockquote(
    public val text: TlGen_RichText,
    public val caption: TlGen_RichText,
  ) : TlGen_PageBlock() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
      caption.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x263D7C26U
    }
  }

  public data class TL_pageBlockPullquote(
    public val text: TlGen_RichText,
    public val caption: TlGen_RichText,
  ) : TlGen_PageBlock() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
      caption.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4F4456D3U
    }
  }

  public data class TL_pageBlockCover(
    public val cover: TlGen_PageBlock,
  ) : TlGen_PageBlock() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      cover.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x39F23300U
    }
  }

  public data class TL_pageBlockAuthorDate(
    public val author: TlGen_RichText,
    public val published_date: Int,
  ) : TlGen_PageBlock() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      author.serializeToStream(stream)
      stream.writeInt32(published_date)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBAAFE5E0U
    }
  }

  public data class TL_pageBlockChannel(
    public val channel: TlGen_Chat,
  ) : TlGen_PageBlock() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      channel.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEF1751B5U
    }
  }

  public data class TL_pageBlockList(
    public val items: List<TlGen_PageListItem>,
  ) : TlGen_PageBlock() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, items)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE4E88011U
    }
  }

  public data class TL_pageBlockPhoto(
    public val photo_id: Long,
    public val caption: TlGen_PageCaption,
    public val multiflags_0: Multiflags_0?,
  ) : TlGen_PageBlock() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (multiflags_0 != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(photo_id)
      caption.serializeToStream(stream)
      multiflags_0?.let { stream.writeString(it.url) }
      multiflags_0?.let { stream.writeInt64(it.webpage_id) }
    }

    public data class Multiflags_0(
      public val url: String,
      public val webpage_id: Long,
    )

    public companion object {
      public const val MAGIC: UInt = 0x1759C560U
    }
  }

  public data class TL_pageBlockVideo(
    public val autoplay: Boolean,
    public val loop: Boolean,
    public val video_id: Long,
    public val caption: TlGen_PageCaption,
  ) : TlGen_PageBlock() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (autoplay) result = result or 1U
        if (loop) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(video_id)
      caption.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7C8FE7B6U
    }
  }

  public data class TL_pageBlockEmbed(
    public val full_width: Boolean,
    public val allow_scrolling: Boolean,
    public val url: String?,
    public val html: String?,
    public val poster_photo_id: Long?,
    public val caption: TlGen_PageCaption,
    public val multiflags_5: Multiflags_5?,
  ) : TlGen_PageBlock() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (full_width) result = result or 1U
        if (url != null) result = result or 2U
        if (html != null) result = result or 4U
        if (allow_scrolling) result = result or 8U
        if (poster_photo_id != null) result = result or 16U
        if (multiflags_5 != null) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      url?.let { stream.writeString(it) }
      html?.let { stream.writeString(it) }
      poster_photo_id?.let { stream.writeInt64(it) }
      multiflags_5?.let { stream.writeInt32(it.w) }
      multiflags_5?.let { stream.writeInt32(it.h) }
      caption.serializeToStream(stream)
    }

    public data class Multiflags_5(
      public val w: Int,
      public val h: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xA8718DC5U
    }
  }

  public data class TL_pageBlockEmbedPost(
    public val url: String,
    public val webpage_id: Long,
    public val author_photo_id: Long,
    public val author: String,
    public val date: Int,
    public val blocks: List<TlGen_PageBlock>,
    public val caption: TlGen_PageCaption,
  ) : TlGen_PageBlock() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(url)
      stream.writeInt64(webpage_id)
      stream.writeInt64(author_photo_id)
      stream.writeString(author)
      stream.writeInt32(date)
      TlGen_Vector.serialize(stream, blocks)
      caption.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF259A80BU
    }
  }

  public data class TL_pageBlockCollage(
    public val items: List<TlGen_PageBlock>,
    public val caption: TlGen_PageCaption,
  ) : TlGen_PageBlock() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, items)
      caption.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x65A0FA4DU
    }
  }

  public data class TL_pageBlockSlideshow(
    public val items: List<TlGen_PageBlock>,
    public val caption: TlGen_PageCaption,
  ) : TlGen_PageBlock() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, items)
      caption.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x031F9590U
    }
  }

  public data class TL_pageBlockAudio(
    public val audio_id: Long,
    public val caption: TlGen_PageCaption,
  ) : TlGen_PageBlock() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(audio_id)
      caption.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x804361EAU
    }
  }

  public data class TL_pageBlockKicker(
    public val text: TlGen_RichText,
  ) : TlGen_PageBlock() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1E148390U
    }
  }

  public data class TL_pageBlockTable(
    public val bordered: Boolean,
    public val striped: Boolean,
    public val title: TlGen_RichText,
    public val rows: List<TlGen_PageTableRow>,
  ) : TlGen_PageBlock() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (bordered) result = result or 1U
        if (striped) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      title.serializeToStream(stream)
      TlGen_Vector.serialize(stream, rows)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBF4DEA82U
    }
  }

  public data class TL_pageBlockOrderedList(
    public val items: List<TlGen_PageListOrderedItem>,
  ) : TlGen_PageBlock() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, items)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9A8AE1E1U
    }
  }

  public data class TL_pageBlockDetails(
    public val `open`: Boolean,
    public val blocks: List<TlGen_PageBlock>,
    public val title: TlGen_RichText,
  ) : TlGen_PageBlock() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (open) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serialize(stream, blocks)
      title.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x76768BEDU
    }
  }

  public data class TL_pageBlockRelatedArticles(
    public val title: TlGen_RichText,
    public val articles: List<TlGen_PageRelatedArticle>,
  ) : TlGen_PageBlock() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      title.serializeToStream(stream)
      TlGen_Vector.serialize(stream, articles)
    }

    public companion object {
      public const val MAGIC: UInt = 0x16115A96U
    }
  }

  public data class TL_pageBlockMap(
    public val geo: TlGen_GeoPoint,
    public val zoom: Int,
    public val w: Int,
    public val h: Int,
    public val caption: TlGen_PageCaption,
  ) : TlGen_PageBlock() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      geo.serializeToStream(stream)
      stream.writeInt32(zoom)
      stream.writeInt32(w)
      stream.writeInt32(h)
      caption.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA44F3EF6U
    }
  }

  public data class TL_pageBlockAuthorDate_layer60(
    public val author: String,
    public val published_date: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(author)
      stream.writeInt32(published_date)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3D5B64F2U
    }
  }

  public data class TL_pageBlockList_layer87(
    public val ordered: Boolean,
    public val items: List<TlGen_RichText>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeBool(ordered)
      TlGen_Vector.serialize(stream, items)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3A58C7F4U
    }
  }

  public data class TL_pageBlockPhoto_layer87(
    public val photo_id: Long,
    public val caption: TlGen_RichText,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(photo_id)
      caption.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE9C69982U
    }
  }

  public data class TL_pageBlockVideo_layer87(
    public val autoplay: Boolean,
    public val loop: Boolean,
    public val video_id: Long,
    public val caption: TlGen_RichText,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (autoplay) result = result or 1U
        if (loop) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(video_id)
      caption.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD9D71866U
    }
  }

  public data class TL_pageBlockEmbed_layer60(
    public val full_width: Boolean,
    public val allow_scrolling: Boolean,
    public val url: String?,
    public val html: String?,
    public val w: Int,
    public val h: Int,
    public val caption: TlGen_RichText,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (full_width) result = result or 1U
        if (url != null) result = result or 2U
        if (html != null) result = result or 4U
        if (allow_scrolling) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      url?.let { stream.writeString(it) }
      html?.let { stream.writeString(it) }
      stream.writeInt32(w)
      stream.writeInt32(h)
      caption.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD935D8FBU
    }
  }

  public data class TL_pageBlockEmbedPost_layer87(
    public val url: String,
    public val webpage_id: Long,
    public val author_photo_id: Long,
    public val author: String,
    public val date: Int,
    public val blocks: List<TlGen_PageBlock>,
    public val caption: TlGen_RichText,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(url)
      stream.writeInt64(webpage_id)
      stream.writeInt64(author_photo_id)
      stream.writeString(author)
      stream.writeInt32(date)
      TlGen_Vector.serialize(stream, blocks)
      caption.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x292C7BE9U
    }
  }

  public data class TL_pageBlockCollage_layer87(
    public val items: List<TlGen_PageBlock>,
    public val caption: TlGen_RichText,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, items)
      caption.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x08B31C4FU
    }
  }

  public data class TL_pageBlockSlideshow_layer87(
    public val items: List<TlGen_PageBlock>,
    public val caption: TlGen_RichText,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, items)
      caption.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x130C8963U
    }
  }

  public data class TL_pageBlockEmbed_layer87(
    public val full_width: Boolean,
    public val allow_scrolling: Boolean,
    public val url: String?,
    public val html: String?,
    public val poster_photo_id: Long?,
    public val w: Int,
    public val h: Int,
    public val caption: TlGen_RichText,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (full_width) result = result or 1U
        if (url != null) result = result or 2U
        if (html != null) result = result or 4U
        if (allow_scrolling) result = result or 8U
        if (poster_photo_id != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      url?.let { stream.writeString(it) }
      html?.let { stream.writeString(it) }
      poster_photo_id?.let { stream.writeInt64(it) }
      stream.writeInt32(w)
      stream.writeInt32(h)
      caption.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCDE200D1U
    }
  }

  public data class TL_pageBlockAudio_layer87(
    public val audio_id: Long,
    public val caption: TlGen_RichText,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(audio_id)
      caption.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x31B81A7FU
    }
  }
}

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

public sealed class TlGen_WebPage : TlGen_Object {
  public data class TL_webPage(
    public val has_large_media: Boolean,
    public val video_cover_photo: Boolean,
    public val id: Long,
    public val url: String,
    public val display_url: String,
    public val hash: Int,
    public val type: String?,
    public val site_name: String?,
    public val title: String?,
    public val description: String?,
    public val photo: TlGen_Photo?,
    public val duration: Int?,
    public val author: String?,
    public val document: TlGen_Document?,
    public val cached_page: TlGen_Page?,
    public val attributes: List<TlGen_WebPageAttribute>?,
    public val multiflags_5: Multiflags_5?,
    public val multiflags_6: Multiflags_6?,
  ) : TlGen_WebPage() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (type != null) result = result or 1U
        if (site_name != null) result = result or 2U
        if (title != null) result = result or 4U
        if (description != null) result = result or 8U
        if (photo != null) result = result or 16U
        if (multiflags_5 != null) result = result or 32U
        if (multiflags_6 != null) result = result or 64U
        if (duration != null) result = result or 128U
        if (author != null) result = result or 256U
        if (document != null) result = result or 512U
        if (cached_page != null) result = result or 1024U
        if (attributes != null) result = result or 4096U
        if (has_large_media) result = result or 8192U
        if (video_cover_photo) result = result or 16384U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeString(url)
      stream.writeString(display_url)
      stream.writeInt32(hash)
      type?.let { stream.writeString(it) }
      site_name?.let { stream.writeString(it) }
      title?.let { stream.writeString(it) }
      description?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      multiflags_5?.let { stream.writeString(it.embed_url) }
      multiflags_5?.let { stream.writeString(it.embed_type) }
      multiflags_6?.let { stream.writeInt32(it.embed_width) }
      multiflags_6?.let { stream.writeInt32(it.embed_height) }
      duration?.let { stream.writeInt32(it) }
      author?.let { stream.writeString(it) }
      document?.serializeToStream(stream)
      cached_page?.serializeToStream(stream)
      attributes?.let { TlGen_Vector.serialize(stream, it) }
    }

    public data class Multiflags_5(
      public val embed_url: String,
      public val embed_type: String,
    )

    public data class Multiflags_6(
      public val embed_width: Int,
      public val embed_height: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xE89C45B2U
    }
  }

  public data class TL_webPageNotModified(
    public val cached_page_views: Int?,
  ) : TlGen_WebPage() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (cached_page_views != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      cached_page_views?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x7311CA11U
    }
  }

  public data class TL_webPageEmpty(
    public val id: Long,
    public val url: String?,
  ) : TlGen_WebPage() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (url != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      url?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x211A1788U
    }
  }

  public data class TL_webPagePending(
    public val id: Long,
    public val url: String?,
    public val date: Int,
  ) : TlGen_WebPage() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (url != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      url?.let { stream.writeString(it) }
      stream.writeInt32(date)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB0D13E47U
    }
  }

  public data class TL_webPageEmpty_layer165(
    public val id: Long,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEB1477E8U
    }
  }

  public data class TL_webPagePending_layer165(
    public val id: Long,
    public val date: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt32(date)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC586DA1CU
    }
  }

  public data class TL_webPage_layer34(
    public val id: Long,
    public val url: String,
    public val display_url: String,
    public val type: String?,
    public val site_name: String?,
    public val title: String?,
    public val description: String?,
    public val photo: TlGen_Photo?,
    public val duration: Int?,
    public val author: String?,
    public val multiflags_5: Multiflags_5?,
    public val multiflags_6: Multiflags_6?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (type != null) result = result or 1U
        if (site_name != null) result = result or 2U
        if (title != null) result = result or 4U
        if (description != null) result = result or 8U
        if (photo != null) result = result or 16U
        if (multiflags_5 != null) result = result or 32U
        if (multiflags_6 != null) result = result or 64U
        if (duration != null) result = result or 128U
        if (author != null) result = result or 256U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeString(url)
      stream.writeString(display_url)
      type?.let { stream.writeString(it) }
      site_name?.let { stream.writeString(it) }
      title?.let { stream.writeString(it) }
      description?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      multiflags_5?.let { stream.writeString(it.embed_url) }
      multiflags_5?.let { stream.writeString(it.embed_type) }
      multiflags_6?.let { stream.writeInt32(it.embed_width) }
      multiflags_6?.let { stream.writeInt32(it.embed_height) }
      duration?.let { stream.writeInt32(it) }
      author?.let { stream.writeString(it) }
    }

    public data class Multiflags_5(
      public val embed_url: String,
      public val embed_type: String,
    )

    public data class Multiflags_6(
      public val embed_width: Int,
      public val embed_height: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xA31EA0B5U
    }
  }

  public data class TL_webPage_layer58(
    public val id: Long,
    public val url: String,
    public val display_url: String,
    public val type: String?,
    public val site_name: String?,
    public val title: String?,
    public val description: String?,
    public val photo: TlGen_Photo?,
    public val duration: Int?,
    public val author: String?,
    public val document: TlGen_Document?,
    public val multiflags_5: Multiflags_5?,
    public val multiflags_6: Multiflags_6?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (type != null) result = result or 1U
        if (site_name != null) result = result or 2U
        if (title != null) result = result or 4U
        if (description != null) result = result or 8U
        if (photo != null) result = result or 16U
        if (multiflags_5 != null) result = result or 32U
        if (multiflags_6 != null) result = result or 64U
        if (duration != null) result = result or 128U
        if (author != null) result = result or 256U
        if (document != null) result = result or 512U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeString(url)
      stream.writeString(display_url)
      type?.let { stream.writeString(it) }
      site_name?.let { stream.writeString(it) }
      title?.let { stream.writeString(it) }
      description?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      multiflags_5?.let { stream.writeString(it.embed_url) }
      multiflags_5?.let { stream.writeString(it.embed_type) }
      multiflags_6?.let { stream.writeInt32(it.embed_width) }
      multiflags_6?.let { stream.writeInt32(it.embed_height) }
      duration?.let { stream.writeInt32(it) }
      author?.let { stream.writeString(it) }
      document?.serializeToStream(stream)
    }

    public data class Multiflags_5(
      public val embed_url: String,
      public val embed_type: String,
    )

    public data class Multiflags_6(
      public val embed_width: Int,
      public val embed_height: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xCA820ED7U
    }
  }

  public data class TL_webPage_layer104(
    public val id: Long,
    public val url: String,
    public val display_url: String,
    public val hash: Int,
    public val type: String?,
    public val site_name: String?,
    public val title: String?,
    public val description: String?,
    public val photo: TlGen_Photo?,
    public val duration: Int?,
    public val author: String?,
    public val document: TlGen_Document?,
    public val cached_page: TlGen_Page?,
    public val multiflags_5: Multiflags_5?,
    public val multiflags_6: Multiflags_6?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (type != null) result = result or 1U
        if (site_name != null) result = result or 2U
        if (title != null) result = result or 4U
        if (description != null) result = result or 8U
        if (photo != null) result = result or 16U
        if (multiflags_5 != null) result = result or 32U
        if (multiflags_6 != null) result = result or 64U
        if (duration != null) result = result or 128U
        if (author != null) result = result or 256U
        if (document != null) result = result or 512U
        if (cached_page != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeString(url)
      stream.writeString(display_url)
      stream.writeInt32(hash)
      type?.let { stream.writeString(it) }
      site_name?.let { stream.writeString(it) }
      title?.let { stream.writeString(it) }
      description?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      multiflags_5?.let { stream.writeString(it.embed_url) }
      multiflags_5?.let { stream.writeString(it.embed_type) }
      multiflags_6?.let { stream.writeInt32(it.embed_width) }
      multiflags_6?.let { stream.writeInt32(it.embed_height) }
      duration?.let { stream.writeInt32(it) }
      author?.let { stream.writeString(it) }
      document?.serializeToStream(stream)
      cached_page?.serializeToStream(stream)
    }

    public data class Multiflags_5(
      public val embed_url: String,
      public val embed_type: String,
    )

    public data class Multiflags_6(
      public val embed_width: Int,
      public val embed_height: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x5F07B4BCU
    }
  }

  public data object TL_webPageNotModified_layer110 : TlGen_Object {
    public const val MAGIC: UInt = 0x85849473U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_webPage_layer107(
    public val id: Long,
    public val url: String,
    public val display_url: String,
    public val hash: Int,
    public val type: String?,
    public val site_name: String?,
    public val title: String?,
    public val description: String?,
    public val photo: TlGen_Photo?,
    public val duration: Int?,
    public val author: String?,
    public val document: TlGen_Document?,
    public val documents: List<TlGen_Document>?,
    public val cached_page: TlGen_Page?,
    public val multiflags_5: Multiflags_5?,
    public val multiflags_6: Multiflags_6?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (type != null) result = result or 1U
        if (site_name != null) result = result or 2U
        if (title != null) result = result or 4U
        if (description != null) result = result or 8U
        if (photo != null) result = result or 16U
        if (multiflags_5 != null) result = result or 32U
        if (multiflags_6 != null) result = result or 64U
        if (duration != null) result = result or 128U
        if (author != null) result = result or 256U
        if (document != null) result = result or 512U
        if (cached_page != null) result = result or 1024U
        if (documents != null) result = result or 2048U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeString(url)
      stream.writeString(display_url)
      stream.writeInt32(hash)
      type?.let { stream.writeString(it) }
      site_name?.let { stream.writeString(it) }
      title?.let { stream.writeString(it) }
      description?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      multiflags_5?.let { stream.writeString(it.embed_url) }
      multiflags_5?.let { stream.writeString(it.embed_type) }
      multiflags_6?.let { stream.writeInt32(it.embed_width) }
      multiflags_6?.let { stream.writeInt32(it.embed_height) }
      duration?.let { stream.writeInt32(it) }
      author?.let { stream.writeString(it) }
      document?.serializeToStream(stream)
      documents?.let { TlGen_Vector.serialize(stream, it) }
      cached_page?.serializeToStream(stream)
    }

    public data class Multiflags_5(
      public val embed_url: String,
      public val embed_type: String,
    )

    public data class Multiflags_6(
      public val embed_width: Int,
      public val embed_height: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xFA64E172U
    }
  }
}

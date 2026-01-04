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

public sealed class TlGen_StickerSet : TlGen_Object {
  public data class TL_stickerSet(
    public val archived: Boolean,
    public val official: Boolean,
    public val masks: Boolean,
    public val emojis: Boolean,
    public val text_color: Boolean,
    public val channel_emoji_status: Boolean,
    public val creator: Boolean,
    public val installed_date: Int?,
    public val id: Long,
    public val access_hash: Long,
    public val title: String,
    public val short_name: String,
    public val thumb_document_id: Long?,
    public val count: Int,
    public val hash: Int,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_StickerSet() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (installed_date != null) result = result or 1U
        if (archived) result = result or 2U
        if (official) result = result or 4U
        if (masks) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (emojis) result = result or 128U
        if (thumb_document_id != null) result = result or 256U
        if (text_color) result = result or 512U
        if (channel_emoji_status) result = result or 1024U
        if (creator) result = result or 2048U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      installed_date?.let { stream.writeInt32(it) }
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeString(title)
      stream.writeString(short_name)
      multiflags_4?.let { TlGen_Vector.serialize(stream, it.thumbs) }
      multiflags_4?.let { stream.writeInt32(it.thumb_dc_id) }
      multiflags_4?.let { stream.writeInt32(it.thumb_version) }
      thumb_document_id?.let { stream.writeInt64(it) }
      stream.writeInt32(count)
      stream.writeInt32(hash)
    }

    public data class Multiflags_4(
      public val thumbs: List<TlGen_PhotoSize>,
      public val thumb_dc_id: Int,
      public val thumb_version: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x2DD14EDCU
    }
  }

  public data class TL_stickerSet_layer75(
    public val installed: Boolean,
    public val archived: Boolean,
    public val official: Boolean,
    public val masks: Boolean,
    public val id: Long,
    public val access_hash: Long,
    public val title: String,
    public val short_name: String,
    public val count: Int,
    public val hash: Int,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (installed) result = result or 1U
        if (archived) result = result or 2U
        if (official) result = result or 4U
        if (masks) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeString(title)
      stream.writeString(short_name)
      stream.writeInt32(count)
      stream.writeInt32(hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCD303B41U
    }
  }

  public data class TL_stickerSet_layer96(
    public val archived: Boolean,
    public val official: Boolean,
    public val masks: Boolean,
    public val installed_date: Int?,
    public val id: Long,
    public val access_hash: Long,
    public val title: String,
    public val short_name: String,
    public val count: Int,
    public val hash: Int,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (installed_date != null) result = result or 1U
        if (archived) result = result or 2U
        if (official) result = result or 4U
        if (masks) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      installed_date?.let { stream.writeInt32(it) }
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeString(title)
      stream.writeString(short_name)
      stream.writeInt32(count)
      stream.writeInt32(hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5585A139U
    }
  }

  public data class TL_stickerSet_layer97(
    public val archived: Boolean,
    public val official: Boolean,
    public val masks: Boolean,
    public val installed_date: Int?,
    public val id: Long,
    public val access_hash: Long,
    public val title: String,
    public val short_name: String,
    public val thumb: TlGen_PhotoSize?,
    public val count: Int,
    public val hash: Int,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (installed_date != null) result = result or 1U
        if (archived) result = result or 2U
        if (official) result = result or 4U
        if (masks) result = result or 8U
        if (thumb != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      installed_date?.let { stream.writeInt32(it) }
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeString(title)
      stream.writeString(short_name)
      thumb?.serializeToStream(stream)
      stream.writeInt32(count)
      stream.writeInt32(hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6A90BCB7U
    }
  }

  public data class TL_stickerSet_layer121(
    public val archived: Boolean,
    public val official: Boolean,
    public val masks: Boolean,
    public val animated: Boolean,
    public val installed_date: Int?,
    public val id: Long,
    public val access_hash: Long,
    public val title: String,
    public val short_name: String,
    public val count: Int,
    public val hash: Int,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (installed_date != null) result = result or 1U
        if (archived) result = result or 2U
        if (official) result = result or 4U
        if (masks) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (animated) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      installed_date?.let { stream.writeInt32(it) }
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeString(title)
      stream.writeString(short_name)
      multiflags_4?.let { it.thumb.serializeToStream(stream) }
      multiflags_4?.let { stream.writeInt32(it.thumb_dc_id) }
      stream.writeInt32(count)
      stream.writeInt32(hash)
    }

    public data class Multiflags_4(
      public val thumb: TlGen_PhotoSize,
      public val thumb_dc_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xEEB46F27U
    }
  }

  public data class TL_stickerSet_layer126(
    public val archived: Boolean,
    public val official: Boolean,
    public val masks: Boolean,
    public val animated: Boolean,
    public val installed_date: Int?,
    public val id: Long,
    public val access_hash: Long,
    public val title: String,
    public val short_name: String,
    public val count: Int,
    public val hash: Int,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (installed_date != null) result = result or 1U
        if (archived) result = result or 2U
        if (official) result = result or 4U
        if (masks) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (animated) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      installed_date?.let { stream.writeInt32(it) }
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeString(title)
      stream.writeString(short_name)
      multiflags_4?.let { TlGen_Vector.serialize(stream, it.thumbs) }
      multiflags_4?.let { stream.writeInt32(it.thumb_dc_id) }
      stream.writeInt32(count)
      stream.writeInt32(hash)
    }

    public data class Multiflags_4(
      public val thumbs: List<TlGen_PhotoSize>,
      public val thumb_dc_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x40E237A8U
    }
  }

  public data class TL_stickerSet_layer143(
    public val archived: Boolean,
    public val official: Boolean,
    public val masks: Boolean,
    public val animated: Boolean,
    public val videos: Boolean,
    public val emojis: Boolean,
    public val installed_date: Int?,
    public val id: Long,
    public val access_hash: Long,
    public val title: String,
    public val short_name: String,
    public val count: Int,
    public val hash: Int,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (installed_date != null) result = result or 1U
        if (archived) result = result or 2U
        if (official) result = result or 4U
        if (masks) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (animated) result = result or 32U
        if (videos) result = result or 64U
        if (emojis) result = result or 128U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      installed_date?.let { stream.writeInt32(it) }
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeString(title)
      stream.writeString(short_name)
      multiflags_4?.let { TlGen_Vector.serialize(stream, it.thumbs) }
      multiflags_4?.let { stream.writeInt32(it.thumb_dc_id) }
      multiflags_4?.let { stream.writeInt32(it.thumb_version) }
      stream.writeInt32(count)
      stream.writeInt32(hash)
    }

    public data class Multiflags_4(
      public val thumbs: List<TlGen_PhotoSize>,
      public val thumb_dc_id: Int,
      public val thumb_version: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xD7DF217AU
    }
  }
}

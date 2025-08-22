package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Byte
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_Photo : TlGen_Object {
  public data class TL_photoEmpty(
    public val id: Long,
  ) : TlGen_Photo() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2331B22DU
    }
  }

  public data class TL_photo(
    public val has_stickers: Boolean,
    public val id: Long,
    public val access_hash: Long,
    public val file_reference: List<Byte>,
    public val date: Int,
    public val sizes: List<TlGen_PhotoSize>,
    public val video_sizes: List<TlGen_VideoSize>?,
    public val dc_id: Int,
  ) : TlGen_Photo() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (has_stickers) result = result or 1U
        if (video_sizes != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeByteArray(file_reference.toByteArray())
      stream.writeInt32(date)
      TlGen_Vector.serialize(stream, sizes)
      video_sizes?.let { TlGen_Vector.serialize(stream, it) }
      stream.writeInt32(dc_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFB197A65U
    }
  }

  public data class TL_photo_layer27(
    public val id: Long,
    public val access_hash: Long,
    public val user_id: Int,
    public val date: Int,
    public val caption: String,
    public val geo: TlGen_GeoPoint,
    public val sizes: List<TlGen_PhotoSize>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeInt32(user_id)
      stream.writeInt32(date)
      stream.writeString(caption)
      geo.serializeToStream(stream)
      TlGen_Vector.serialize(stream, sizes)
    }

    public companion object {
      public const val MAGIC: UInt = 0x22B56751U
    }
  }

  public data class TL_photo_layer32(
    public val id: Long,
    public val access_hash: Long,
    public val user_id: Int,
    public val date: Int,
    public val geo: TlGen_GeoPoint,
    public val sizes: List<TlGen_PhotoSize>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeInt32(user_id)
      stream.writeInt32(date)
      geo.serializeToStream(stream)
      TlGen_Vector.serialize(stream, sizes)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC3838076U
    }
  }

  public data class TL_photo_layer55(
    public val id: Long,
    public val access_hash: Long,
    public val date: Int,
    public val sizes: List<TlGen_PhotoSize>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeInt32(date)
      TlGen_Vector.serialize(stream, sizes)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCDED42FEU
    }
  }

  public data class TL_photo_layer85(
    public val has_stickers: Boolean,
    public val id: Long,
    public val access_hash: Long,
    public val date: Int,
    public val sizes: List<TlGen_PhotoSize>,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (has_stickers) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeInt32(date)
      TlGen_Vector.serialize(stream, sizes)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9288DD29U
    }
  }

  public data class TL_photo_layer97(
    public val has_stickers: Boolean,
    public val id: Long,
    public val access_hash: Long,
    public val file_reference: List<Byte>,
    public val date: Int,
    public val sizes: List<TlGen_PhotoSize>,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (has_stickers) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeByteArray(file_reference.toByteArray())
      stream.writeInt32(date)
      TlGen_Vector.serialize(stream, sizes)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9C477DD8U
    }
  }

  public data class TL_photo_layer115(
    public val has_stickers: Boolean,
    public val id: Long,
    public val access_hash: Long,
    public val file_reference: List<Byte>,
    public val date: Int,
    public val sizes: List<TlGen_PhotoSize>,
    public val dc_id: Int,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (has_stickers) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeByteArray(file_reference.toByteArray())
      stream.writeInt32(date)
      TlGen_Vector.serialize(stream, sizes)
      stream.writeInt32(dc_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD07504A5U
    }
  }
}

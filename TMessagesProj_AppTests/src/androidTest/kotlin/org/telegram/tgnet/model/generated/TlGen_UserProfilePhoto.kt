package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Byte
import kotlin.Int
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_UserProfilePhoto : TlGen_Object {
  public data object TL_userProfilePhotoEmpty : TlGen_UserProfilePhoto() {
    public const val MAGIC: UInt = 0x4F11BAE1U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_userProfilePhoto(
    public val has_video: Boolean,
    public val personal: Boolean,
    public val photo_id: Long,
    public val stripped_thumb: List<Byte>?,
    public val dc_id: Int,
  ) : TlGen_UserProfilePhoto() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (has_video) result = result or 1U
        if (stripped_thumb != null) result = result or 2U
        if (personal) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(photo_id)
      stripped_thumb?.let { stream.writeByteArray(it.toByteArray()) }
      stream.writeInt32(dc_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x82D1F706U
    }
  }

  public data class TL_userProfilePhoto_layer1(
    public val photo_small: TlGen_FileLocation,
    public val photo_big: TlGen_FileLocation,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      photo_small.serializeToStream(stream)
      photo_big.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x990D1493U
    }
  }

  public data class TL_userProfilePhoto_layer97(
    public val photo_id: Long,
    public val photo_small: TlGen_FileLocation,
    public val photo_big: TlGen_FileLocation,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(photo_id)
      photo_small.serializeToStream(stream)
      photo_big.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD559D8C8U
    }
  }

  public data class TL_userProfilePhoto_layer115(
    public val photo_id: Long,
    public val photo_small: TlGen_FileLocation,
    public val photo_big: TlGen_FileLocation,
    public val dc_id: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(photo_id)
      photo_small.serializeToStream(stream)
      photo_big.serializeToStream(stream)
      stream.writeInt32(dc_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xECD75D8CU
    }
  }

  public data class TL_userProfilePhoto_layer126(
    public val has_video: Boolean,
    public val photo_id: Long,
    public val photo_small: TlGen_FileLocation,
    public val photo_big: TlGen_FileLocation,
    public val dc_id: Int,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (has_video) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(photo_id)
      photo_small.serializeToStream(stream)
      photo_big.serializeToStream(stream)
      stream.writeInt32(dc_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x69D3AB26U
    }
  }

  public data class TL_userProfilePhoto_layer127(
    public val has_video: Boolean,
    public val photo_id: Long,
    public val photo_small: TlGen_FileLocation,
    public val photo_big: TlGen_FileLocation,
    public val stripped_thumb: List<Byte>?,
    public val dc_id: Int,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (has_video) result = result or 1U
        if (stripped_thumb != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(photo_id)
      photo_small.serializeToStream(stream)
      photo_big.serializeToStream(stream)
      stripped_thumb?.let { stream.writeByteArray(it.toByteArray()) }
      stream.writeInt32(dc_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCC656077U
    }
  }
}

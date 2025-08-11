package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PhotoSize : TlGen_Object {
  public data class TL_photoSizeEmpty(
    public val type: String,
  ) : TlGen_PhotoSize() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(type)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0E17E23CU
    }
  }

  public data class TL_photoStrippedSize(
    public val type: String,
    public val bytes: List<Byte>,
  ) : TlGen_PhotoSize() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(type)
      stream.writeByteArray(bytes.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0xE0B0BC2EU
    }
  }

  public data class TL_photoPathSize(
    public val type: String,
    public val bytes: List<Byte>,
  ) : TlGen_PhotoSize() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(type)
      stream.writeByteArray(bytes.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0xD8214D41U
    }
  }

  public data class TL_photoSize(
    public val type: String,
    public val w: Int,
    public val h: Int,
    public val size: Int,
  ) : TlGen_PhotoSize() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(type)
      stream.writeInt32(w)
      stream.writeInt32(h)
      stream.writeInt32(size)
    }

    public companion object {
      public const val MAGIC: UInt = 0x75C78E60U
    }
  }

  public data class TL_photoCachedSize(
    public val type: String,
    public val w: Int,
    public val h: Int,
    public val bytes: List<Byte>,
  ) : TlGen_PhotoSize() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(type)
      stream.writeInt32(w)
      stream.writeInt32(h)
      stream.writeByteArray(bytes.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x021E1AD6U
    }
  }

  public data class TL_photoSizeProgressive(
    public val type: String,
    public val w: Int,
    public val h: Int,
    public val sizes: List<Int>,
  ) : TlGen_PhotoSize() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(type)
      stream.writeInt32(w)
      stream.writeInt32(h)
      TlGen_Vector.serializeInt(stream, sizes)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFA3EFB95U
    }
  }

  public data class TL_photoSize_layer127(
    public val type: String,
    public val location: TlGen_FileLocation,
    public val w: Int,
    public val h: Int,
    public val size: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(type)
      location.serializeToStream(stream)
      stream.writeInt32(w)
      stream.writeInt32(h)
      stream.writeInt32(size)
    }

    public companion object {
      public const val MAGIC: UInt = 0x77BFB61BU
    }
  }

  public data class TL_photoCachedSize_layer127(
    public val type: String,
    public val location: TlGen_FileLocation,
    public val w: Int,
    public val h: Int,
    public val bytes: List<Byte>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(type)
      location.serializeToStream(stream)
      stream.writeInt32(w)
      stream.writeInt32(h)
      stream.writeByteArray(bytes.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0xE9A734FAU
    }
  }

  public data class TL_photoSizeProgressive_layer127(
    public val type: String,
    public val location: TlGen_FileLocation,
    public val w: Int,
    public val h: Int,
    public val sizes: List<Int>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(type)
      location.serializeToStream(stream)
      stream.writeInt32(w)
      stream.writeInt32(h)
      TlGen_Vector.serializeInt(stream, sizes)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5AA86A51U
    }
  }
}

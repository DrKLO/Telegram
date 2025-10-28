package org.telegram.tgnet.model.generated

import kotlin.Double
import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_GeoPoint : TlGen_Object {
  public data object TL_geoPointEmpty : TlGen_GeoPoint() {
    public const val MAGIC: UInt = 0x1117DD5FU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_geoPoint(
    public val long: Double,
    public val lat: Double,
    public val access_hash: Long,
    public val accuracy_radius: Int?,
  ) : TlGen_GeoPoint() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (accuracy_radius != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeDouble(long)
      stream.writeDouble(lat)
      stream.writeInt64(access_hash)
      accuracy_radius?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xB2A2F663U
    }
  }

  public data class TL_geoPoint_layer81(
    public val long: Double,
    public val lat: Double,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeDouble(long)
      stream.writeDouble(lat)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2049D70CU
    }
  }

  public data class TL_geoPoint_layer119(
    public val long: Double,
    public val lat: Double,
    public val access_hash: Long,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeDouble(long)
      stream.writeDouble(lat)
      stream.writeInt64(access_hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0296F104U
    }
  }
}

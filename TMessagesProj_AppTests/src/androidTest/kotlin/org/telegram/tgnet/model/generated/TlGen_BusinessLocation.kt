package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_BusinessLocation : TlGen_Object {
  public data class TL_businessLocation(
    public val geo_point: TlGen_GeoPoint?,
    public val address: String,
  ) : TlGen_BusinessLocation() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (geo_point != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      geo_point?.serializeToStream(stream)
      stream.writeString(address)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAC5C1AF7U
    }
  }
}

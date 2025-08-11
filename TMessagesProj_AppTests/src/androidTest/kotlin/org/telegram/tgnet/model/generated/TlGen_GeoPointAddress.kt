package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_GeoPointAddress : TlGen_Object {
  public data class TL_geoPointAddress(
    public val country_iso2: String,
    public val state: String?,
    public val city: String?,
    public val street: String?,
  ) : TlGen_GeoPointAddress() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (state != null) result = result or 1U
        if (city != null) result = result or 2U
        if (street != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(country_iso2)
      state?.let { stream.writeString(it) }
      city?.let { stream.writeString(it) }
      street?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xDE4C5D93U
    }
  }
}

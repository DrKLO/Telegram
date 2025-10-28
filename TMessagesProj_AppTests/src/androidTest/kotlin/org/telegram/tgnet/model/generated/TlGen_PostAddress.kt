package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PostAddress : TlGen_Object {
  public data class TL_postAddress(
    public val street_line1: String,
    public val street_line2: String,
    public val city: String,
    public val state: String,
    public val country_iso2: String,
    public val post_code: String,
  ) : TlGen_PostAddress() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(street_line1)
      stream.writeString(street_line2)
      stream.writeString(city)
      stream.writeString(state)
      stream.writeString(country_iso2)
      stream.writeString(post_code)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1E8CAAEBU
    }
  }
}

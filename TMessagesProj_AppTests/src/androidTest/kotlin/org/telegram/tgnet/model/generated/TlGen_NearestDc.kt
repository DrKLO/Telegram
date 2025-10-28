package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_NearestDc : TlGen_Object {
  public data class TL_nearestDc(
    public val country: String,
    public val this_dc: Int,
    public val nearest_dc: Int,
  ) : TlGen_NearestDc() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(country)
      stream.writeInt32(this_dc)
      stream.writeInt32(nearest_dc)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8E1A1775U
    }
  }
}

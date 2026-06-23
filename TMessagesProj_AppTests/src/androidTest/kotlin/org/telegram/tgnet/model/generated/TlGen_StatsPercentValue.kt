package org.Tajgram.tgnet.model.generated

import kotlin.Double
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_StatsPercentValue : TlGen_Object {
  public data class TL_statsPercentValue(
    public val part: Double,
    public val total: Double,
  ) : TlGen_StatsPercentValue() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeDouble(part)
      stream.writeDouble(total)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCBCE2FE0U
    }
  }
}

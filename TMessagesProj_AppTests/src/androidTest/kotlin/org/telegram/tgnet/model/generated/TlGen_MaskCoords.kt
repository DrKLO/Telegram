package org.Tajgram.tgnet.model.generated

import kotlin.Double
import kotlin.Int
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_MaskCoords : TlGen_Object {
  public data class TL_maskCoords(
    public val n: Int,
    public val x: Double,
    public val y: Double,
    public val zoom: Double,
  ) : TlGen_MaskCoords() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(n)
      stream.writeDouble(x)
      stream.writeDouble(y)
      stream.writeDouble(zoom)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAED6DBB2U
    }
  }
}

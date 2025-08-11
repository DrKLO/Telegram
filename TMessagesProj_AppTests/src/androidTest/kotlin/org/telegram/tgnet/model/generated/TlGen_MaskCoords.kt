package org.telegram.tgnet.model.generated

import kotlin.Double
import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

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

package org.telegram.tgnet.model.generated

import kotlin.Double
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_CurrencyRate : TlGen_Object {
  public data class TL_currencyRate(
    public val currency: String,
    public val rate: Double,
  ) : TlGen_CurrencyRate() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(currency)
      stream.writeDouble(rate)
    }

    public companion object {
      public const val MAGIC: UInt = 0x30D9743CU
    }
  }
}

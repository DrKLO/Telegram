package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_payments_CurrencyRates : TlGen_Object {
  public data class TL_payments_currencyRates(
    public val rates: List<TlGen_CurrencyRate>,
  ) : TlGen_payments_CurrencyRates() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, rates)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBBCCE4C2U
    }
  }
}

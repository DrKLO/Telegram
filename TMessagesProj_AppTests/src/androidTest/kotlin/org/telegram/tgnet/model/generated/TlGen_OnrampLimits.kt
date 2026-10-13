package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_OnrampLimits : TlGen_Object {
  public data class TL_onrampLimits(
    public val base_currency: String,
    public val base_min_amount: String,
    public val base_max_amount: String,
    public val crypto_min_amount: String,
    public val crypto_max_amount: String,
    public val payment_method: String,
  ) : TlGen_OnrampLimits() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(base_currency)
      stream.writeString(base_min_amount)
      stream.writeString(base_max_amount)
      stream.writeString(crypto_min_amount)
      stream.writeString(crypto_max_amount)
      stream.writeString(payment_method)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7BE2677EU
    }
  }
}

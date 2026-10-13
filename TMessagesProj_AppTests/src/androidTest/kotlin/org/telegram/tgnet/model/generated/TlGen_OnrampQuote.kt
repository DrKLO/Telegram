package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_OnrampQuote : TlGen_Object {
  public data class TL_onrampQuote(
    public val base_currency: String,
    public val base_amount: String,
    public val crypto_currency: String,
    public val crypto_amount: String,
    public val crypto_price: String,
    public val fee_amount: String,
    public val extra_fee_amount: String,
    public val network_fee_amount: String,
    public val total_amount: String,
    public val payment_method: String,
    public val expires_date: Int,
  ) : TlGen_OnrampQuote() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(base_currency)
      stream.writeString(base_amount)
      stream.writeString(crypto_currency)
      stream.writeString(crypto_amount)
      stream.writeString(crypto_price)
      stream.writeString(fee_amount)
      stream.writeString(extra_fee_amount)
      stream.writeString(network_fee_amount)
      stream.writeString(total_amount)
      stream.writeString(payment_method)
      stream.writeInt32(expires_date)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7A8011E9U
    }
  }
}

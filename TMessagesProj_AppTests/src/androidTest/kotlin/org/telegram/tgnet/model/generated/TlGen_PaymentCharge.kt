package org.Tajgram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_PaymentCharge : TlGen_Object {
  public data class TL_paymentCharge(
    public val id: String,
    public val provider_charge_id: String,
  ) : TlGen_PaymentCharge() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(id)
      stream.writeString(provider_charge_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEA02C27EU
    }
  }
}

package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_payments_PaymentResult : TlGen_Object {
  public data class TL_payments_paymentResult(
    public val updates: TlGen_Updates,
  ) : TlGen_payments_PaymentResult() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      updates.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4E5F810DU
    }
  }

  public data class TL_payments_paymentVerificationNeeded(
    public val url: String,
  ) : TlGen_payments_PaymentResult() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(url)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD8411139U
    }
  }
}

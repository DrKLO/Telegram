package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PaymentSavedCredentials : TlGen_Object {
  public data class TL_paymentSavedCredentialsCard(
    public val id: String,
    public val title: String,
  ) : TlGen_PaymentSavedCredentials() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(id)
      stream.writeString(title)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCDC27A1FU
    }
  }
}

package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PaymentFormMethod : TlGen_Object {
  public data class TL_paymentFormMethod(
    public val url: String,
    public val title: String,
  ) : TlGen_PaymentFormMethod() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(url)
      stream.writeString(title)
    }

    public companion object {
      public const val MAGIC: UInt = 0x88F8F21BU
    }
  }
}

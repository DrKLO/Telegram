package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PaymentRequestedInfo : TlGen_Object {
  public data class TL_paymentRequestedInfo(
    public val name: String?,
    public val phone: String?,
    public val email: String?,
    public val shipping_address: TlGen_PostAddress?,
  ) : TlGen_PaymentRequestedInfo() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (name != null) result = result or 1U
        if (phone != null) result = result or 2U
        if (email != null) result = result or 4U
        if (shipping_address != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      name?.let { stream.writeString(it) }
      phone?.let { stream.writeString(it) }
      email?.let { stream.writeString(it) }
      shipping_address?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x909C3F94U
    }
  }
}

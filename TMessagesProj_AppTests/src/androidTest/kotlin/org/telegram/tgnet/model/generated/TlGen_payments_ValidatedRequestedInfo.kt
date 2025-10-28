package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_payments_ValidatedRequestedInfo : TlGen_Object {
  public data class TL_payments_validatedRequestedInfo(
    public val id: String?,
    public val shipping_options: List<TlGen_ShippingOption>?,
  ) : TlGen_payments_ValidatedRequestedInfo() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (id != null) result = result or 1U
        if (shipping_options != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      id?.let { stream.writeString(it) }
      shipping_options?.let { TlGen_Vector.serialize(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xD1451883U
    }
  }
}

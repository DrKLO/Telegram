package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_payments_SavedInfo : TlGen_Object {
  public data class TL_payments_savedInfo(
    public val has_saved_credentials: Boolean,
    public val saved_info: TlGen_PaymentRequestedInfo?,
  ) : TlGen_payments_SavedInfo() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (saved_info != null) result = result or 1U
        if (has_saved_credentials) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      saved_info?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFB8FE43CU
    }
  }
}

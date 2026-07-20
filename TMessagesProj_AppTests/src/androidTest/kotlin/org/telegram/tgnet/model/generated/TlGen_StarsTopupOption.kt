package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StarsTopupOption : TlGen_Object {
  public data class TL_starsTopupOption(
    public val extended: Boolean,
    public val stars: Long,
    public val store_product: String?,
    public val currency: String,
    public val amount: Long,
  ) : TlGen_StarsTopupOption() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (store_product != null) result = result or 1U
        if (extended) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(stars)
      store_product?.let { stream.writeString(it) }
      stream.writeString(currency)
      stream.writeInt64(amount)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0BD915C0U
    }
  }
}

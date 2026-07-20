package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PremiumGiftCodeOption : TlGen_Object {
  public data class TL_premiumGiftCodeOption(
    public val users: Int,
    public val months: Int,
    public val store_product: String?,
    public val store_quantity: Int?,
    public val currency: String,
    public val amount: Long,
  ) : TlGen_PremiumGiftCodeOption() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (store_product != null) result = result or 1U
        if (store_quantity != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(users)
      stream.writeInt32(months)
      store_product?.let { stream.writeString(it) }
      store_quantity?.let { stream.writeInt32(it) }
      stream.writeString(currency)
      stream.writeInt64(amount)
    }

    public companion object {
      public const val MAGIC: UInt = 0x257E962BU
    }
  }
}

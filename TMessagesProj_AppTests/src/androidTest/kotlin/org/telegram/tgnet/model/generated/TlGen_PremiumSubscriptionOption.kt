package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PremiumSubscriptionOption : TlGen_Object {
  public data class TL_premiumSubscriptionOption(
    public val current: Boolean,
    public val can_purchase_upgrade: Boolean,
    public val transaction: String?,
    public val months: Int,
    public val currency: String,
    public val amount: Long,
    public val bot_url: String,
    public val store_product: String?,
  ) : TlGen_PremiumSubscriptionOption() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (store_product != null) result = result or 1U
        if (current) result = result or 2U
        if (can_purchase_upgrade) result = result or 4U
        if (transaction != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      transaction?.let { stream.writeString(it) }
      stream.writeInt32(months)
      stream.writeString(currency)
      stream.writeInt64(amount)
      stream.writeString(bot_url)
      store_product?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x5F2D1DF2U
    }
  }
}

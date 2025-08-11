package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PremiumGiftOption : TlGen_Object {
  public data class TL_premiumGiftOption_layer199(
    public val months: Int,
    public val currency: String,
    public val amount: Long,
    public val bot_url: String,
    public val store_product: String?,
  ) : TlGen_PremiumGiftOption() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (store_product != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(months)
      stream.writeString(currency)
      stream.writeInt64(amount)
      stream.writeString(bot_url)
      store_product?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x74C34319U
    }
  }
}

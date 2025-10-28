package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_fragment_CollectibleInfo : TlGen_Object {
  public data class TL_fragment_collectibleInfo(
    public val purchase_date: Int,
    public val currency: String,
    public val amount: Long,
    public val crypto_currency: String,
    public val crypto_amount: Long,
    public val url: String,
  ) : TlGen_fragment_CollectibleInfo() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(purchase_date)
      stream.writeString(currency)
      stream.writeInt64(amount)
      stream.writeString(crypto_currency)
      stream.writeInt64(crypto_amount)
      stream.writeString(url)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6EBDFF91U
    }
  }
}

package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StarsGiveawayOption : TlGen_Object {
  public data class TL_starsGiveawayOption(
    public val extended: Boolean,
    public val default: Boolean,
    public val stars: Long,
    public val yearly_boosts: Int,
    public val store_product: String?,
    public val currency: String,
    public val amount: Long,
    public val winners: List<TlGen_StarsGiveawayWinnersOption>,
  ) : TlGen_StarsGiveawayOption() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (extended) result = result or 1U
        if (default) result = result or 2U
        if (store_product != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(stars)
      stream.writeInt32(yearly_boosts)
      store_product?.let { stream.writeString(it) }
      stream.writeString(currency)
      stream.writeInt64(amount)
      TlGen_Vector.serialize(stream, winners)
    }

    public companion object {
      public const val MAGIC: UInt = 0x94CE852AU
    }
  }
}

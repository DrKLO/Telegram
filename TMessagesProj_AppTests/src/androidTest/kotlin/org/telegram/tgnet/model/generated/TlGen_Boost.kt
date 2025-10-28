package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_Boost : TlGen_Object {
  public data class TL_boost(
    public val gift: Boolean,
    public val unclaimed: Boolean,
    public val id: String,
    public val user_id: Long?,
    public val giveaway_msg_id: Int?,
    public val date: Int,
    public val expires: Int,
    public val used_gift_slug: String?,
    public val multiplier: Int?,
    public val stars: Long?,
  ) : TlGen_Boost() {
    public val giveaway: Boolean = giveaway_msg_id != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (user_id != null) result = result or 1U
        if (gift) result = result or 2U
        if (giveaway) result = result or 4U
        if (unclaimed) result = result or 8U
        if (used_gift_slug != null) result = result or 16U
        if (multiplier != null) result = result or 32U
        if (stars != null) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(id)
      user_id?.let { stream.writeInt64(it) }
      giveaway_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(date)
      stream.writeInt32(expires)
      used_gift_slug?.let { stream.writeString(it) }
      multiplier?.let { stream.writeInt32(it) }
      stars?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x4B3E14D6U
    }
  }
}

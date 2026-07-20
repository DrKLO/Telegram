package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_premium_BoostsStatus : TlGen_Object {
  public data class TL_premium_boostsStatus(
    public val level: Int,
    public val current_level_boosts: Int,
    public val boosts: Int,
    public val gift_boosts: Int?,
    public val next_level_boosts: Int?,
    public val premium_audience: TlGen_StatsPercentValue?,
    public val boost_url: String,
    public val prepaid_giveaways: List<TlGen_PrepaidGiveaway>?,
    public val my_boost_slots: List<Int>?,
  ) : TlGen_premium_BoostsStatus() {
    public val my_boost: Boolean = my_boost_slots != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (next_level_boosts != null) result = result or 1U
        if (premium_audience != null) result = result or 2U
        if (my_boost) result = result or 4U
        if (prepaid_giveaways != null) result = result or 8U
        if (gift_boosts != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(level)
      stream.writeInt32(current_level_boosts)
      stream.writeInt32(boosts)
      gift_boosts?.let { stream.writeInt32(it) }
      next_level_boosts?.let { stream.writeInt32(it) }
      premium_audience?.serializeToStream(stream)
      stream.writeString(boost_url)
      prepaid_giveaways?.let { TlGen_Vector.serialize(stream, it) }
      my_boost_slots?.let { TlGen_Vector.serializeInt(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x4959427AU
    }
  }
}

package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StarGiftAuctionState : TlGen_Object {
  public data object TL_starGiftAuctionStateNotModified : TlGen_StarGiftAuctionState() {
    public const val MAGIC: UInt = 0xFE333952U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_starGiftAuctionState(
    public val version: Int,
    public val start_date: Int,
    public val end_date: Int,
    public val min_bid_amount: Long,
    public val bid_levels: List<TlGen_AuctionBidLevel>,
    public val top_bidders: List<Long>,
    public val next_round_at: Int,
    public val last_gift_num: Int,
    public val gifts_left: Int,
    public val current_round: Int,
    public val total_rounds: Int,
    public val rounds: List<TlGen_StarGiftAuctionRound>,
  ) : TlGen_StarGiftAuctionState() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(version)
      stream.writeInt32(start_date)
      stream.writeInt32(end_date)
      stream.writeInt64(min_bid_amount)
      TlGen_Vector.serialize(stream, bid_levels)
      TlGen_Vector.serializeLong(stream, top_bidders)
      stream.writeInt32(next_round_at)
      stream.writeInt32(last_gift_num)
      stream.writeInt32(gifts_left)
      stream.writeInt32(current_round)
      stream.writeInt32(total_rounds)
      TlGen_Vector.serialize(stream, rounds)
    }

    public companion object {
      public const val MAGIC: UInt = 0x771A4E66U
    }
  }

  public data class TL_starGiftAuctionStateFinished(
    public val start_date: Int,
    public val end_date: Int,
    public val average_price: Long,
    public val listed_count: Int?,
    public val multiflags_1: Multiflags_1?,
  ) : TlGen_StarGiftAuctionState() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (listed_count != null) result = result or 1U
        if (multiflags_1 != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(start_date)
      stream.writeInt32(end_date)
      stream.writeInt64(average_price)
      listed_count?.let { stream.writeInt32(it) }
      multiflags_1?.let { stream.writeInt32(it.fragment_listed_count) }
      multiflags_1?.let { stream.writeString(it.fragment_listed_url) }
    }

    public data class Multiflags_1(
      public val fragment_listed_count: Int,
      public val fragment_listed_url: String,
    )

    public companion object {
      public const val MAGIC: UInt = 0x972DABBFU
    }
  }
}

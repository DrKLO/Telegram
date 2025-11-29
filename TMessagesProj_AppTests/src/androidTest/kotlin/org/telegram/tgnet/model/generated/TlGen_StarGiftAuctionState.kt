package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
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
    public val gifts_left: Int,
    public val current_round: Int,
    public val total_rounds: Int,
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
      stream.writeInt32(gifts_left)
      stream.writeInt32(current_round)
      stream.writeInt32(total_rounds)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5DB04F4BU
    }
  }

  public data class TL_starGiftAuctionStateFinished(
    public val start_date: Int,
    public val end_date: Int,
    public val average_price: Long,
  ) : TlGen_StarGiftAuctionState() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(start_date)
      stream.writeInt32(end_date)
      stream.writeInt64(average_price)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7D967C3AU
    }
  }
}

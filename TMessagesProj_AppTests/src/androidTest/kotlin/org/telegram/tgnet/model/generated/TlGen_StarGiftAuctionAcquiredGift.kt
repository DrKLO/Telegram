package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StarGiftAuctionAcquiredGift : TlGen_Object {
  public data class TL_starGiftAuctionAcquiredGift(
    public val name_hidden: Boolean,
    public val peer: TlGen_Peer,
    public val date: Int,
    public val bid_amount: Long,
    public val round: Int,
    public val pos: Int,
    public val message: TlGen_TextWithEntities?,
    public val gift_num: Int?,
  ) : TlGen_StarGiftAuctionAcquiredGift() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (name_hidden) result = result or 1U
        if (message != null) result = result or 2U
        if (gift_num != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeInt64(bid_amount)
      stream.writeInt32(round)
      stream.writeInt32(pos)
      message?.serializeToStream(stream)
      gift_num?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x42B00348U
    }
  }
}

package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StarGiftAuctionUserState : TlGen_Object {
  public data class TL_starGiftAuctionUserState(
    public val returned: Boolean,
    public val acquired_count: Int,
    public val multiflags_0: Multiflags_0?,
  ) : TlGen_StarGiftAuctionUserState() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (multiflags_0 != null) result = result or 1U
        if (returned) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      multiflags_0?.let { stream.writeInt64(it.bid_amount) }
      multiflags_0?.let { stream.writeInt32(it.bid_date) }
      multiflags_0?.let { stream.writeInt64(it.min_bid_amount) }
      multiflags_0?.let { it.bid_peer.serializeToStream(stream) }
      stream.writeInt32(acquired_count)
    }

    public data class Multiflags_0(
      public val bid_amount: Long,
      public val bid_date: Int,
      public val min_bid_amount: Long,
      public val bid_peer: TlGen_Peer,
    )

    public companion object {
      public const val MAGIC: UInt = 0x2EEED1C4U
    }
  }
}

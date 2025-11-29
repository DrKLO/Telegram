package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_payments_StarGiftActiveAuctions : TlGen_Object {
  public data object TL_payments_starGiftActiveAuctionsNotModified :
      TlGen_payments_StarGiftActiveAuctions() {
    public const val MAGIC: UInt = 0xDB33DAD0U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_payments_starGiftActiveAuctions(
    public val auctions: List<TlGen_StarGiftActiveAuctionState>,
    public val users: List<TlGen_User>,
  ) : TlGen_payments_StarGiftActiveAuctions() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, auctions)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x97F187D8U
    }
  }
}

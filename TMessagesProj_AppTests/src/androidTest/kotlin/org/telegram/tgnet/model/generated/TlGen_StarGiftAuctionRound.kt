package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StarGiftAuctionRound : TlGen_Object {
  public data class TL_starGiftAuctionRound(
    public val num: Int,
    public val duration: Int,
  ) : TlGen_StarGiftAuctionRound() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(num)
      stream.writeInt32(duration)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3AAE0528U
    }
  }

  public data class TL_starGiftAuctionRoundExtendable(
    public val num: Int,
    public val duration: Int,
    public val extend_top: Int,
    public val extend_window: Int,
  ) : TlGen_StarGiftAuctionRound() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(num)
      stream.writeInt32(duration)
      stream.writeInt32(extend_top)
      stream.writeInt32(extend_window)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0AA021E5U
    }
  }
}

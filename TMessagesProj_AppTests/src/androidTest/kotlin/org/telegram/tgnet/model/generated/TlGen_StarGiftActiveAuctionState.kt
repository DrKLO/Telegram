package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StarGiftActiveAuctionState : TlGen_Object {
  public data class TL_starGiftActiveAuctionState(
    public val gift: TlGen_StarGift,
    public val state: TlGen_StarGiftAuctionState,
    public val user_state: TlGen_StarGiftAuctionUserState,
  ) : TlGen_StarGiftActiveAuctionState() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      gift.serializeToStream(stream)
      state.serializeToStream(stream)
      user_state.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD31BC45DU
    }
  }
}

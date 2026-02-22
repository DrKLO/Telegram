package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_payments_StarGiftAuctionState : TlGen_Object {
  public data class TL_payments_starGiftAuctionState(
    public val gift: TlGen_StarGift,
    public val state: TlGen_StarGiftAuctionState,
    public val user_state: TlGen_StarGiftAuctionUserState,
    public val timeout: Int,
    public val users: List<TlGen_User>,
    public val chats: List<TlGen_Chat>,
  ) : TlGen_payments_StarGiftAuctionState() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      gift.serializeToStream(stream)
      state.serializeToStream(stream)
      user_state.serializeToStream(stream)
      stream.writeInt32(timeout)
      TlGen_Vector.serialize(stream, users)
      TlGen_Vector.serialize(stream, chats)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6B39F4ECU
    }
  }
}

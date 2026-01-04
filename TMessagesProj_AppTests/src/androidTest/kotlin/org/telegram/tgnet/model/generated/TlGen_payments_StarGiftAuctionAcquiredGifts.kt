package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_payments_StarGiftAuctionAcquiredGifts : TlGen_Object {
  public data class TL_payments_starGiftAuctionAcquiredGifts(
    public val gifts: List<TlGen_StarGiftAuctionAcquiredGift>,
    public val users: List<TlGen_User>,
    public val chats: List<TlGen_Chat>,
  ) : TlGen_payments_StarGiftAuctionAcquiredGifts() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, gifts)
      TlGen_Vector.serialize(stream, users)
      TlGen_Vector.serialize(stream, chats)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7D5BD1F0U
    }
  }
}

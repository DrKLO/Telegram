package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_wallet_Card : TlGen_Object {
  public data class TL_wallet_card(
    public val stickers: List<TlGen_wallet_CardSticker>?,
    public val bg_nft_item: TlGen_wallet_NftItem?,
    public val address: String,
    public val balance: Long,
  ) : TlGen_wallet_Card() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (stickers != null) result = result or 1U
        if (bg_nft_item != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stickers?.let { TlGen_Vector.serialize(stream, it) }
      bg_nft_item?.serializeToStream(stream)
      stream.writeString(address)
      stream.writeInt64(balance)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8ED043B1U
    }
  }
}

package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_wallet_CardSticker : TlGen_Object {
  public data class TL_wallet_cardSticker(
    public val coordinates: TlGen_MediaAreaCoordinates,
    public val nft: TlGen_wallet_NftItem,
  ) : TlGen_wallet_CardSticker() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      coordinates.serializeToStream(stream)
      nft.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF3010CBAU
    }
  }
}

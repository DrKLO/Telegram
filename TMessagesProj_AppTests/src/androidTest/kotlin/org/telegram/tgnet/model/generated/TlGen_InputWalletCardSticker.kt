package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputWalletCardSticker : TlGen_Object {
  public data class TL_inputWalletCardSticker(
    public val coordinates: TlGen_MediaAreaCoordinates,
    public val nft_address: String,
  ) : TlGen_InputWalletCardSticker() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      coordinates.serializeToStream(stream)
      stream.writeString(nft_address)
    }

    public companion object {
      public const val MAGIC: UInt = 0x57DF45A7U
    }
  }
}

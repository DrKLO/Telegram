package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_wallet_NftAttribute : TlGen_Object {
  public data class TL_wallet_nftAttribute(
    public val trait_type: String,
    public val `value`: String,
  ) : TlGen_wallet_NftAttribute() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(trait_type)
      stream.writeString(value)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4C1EF10EU
    }
  }
}

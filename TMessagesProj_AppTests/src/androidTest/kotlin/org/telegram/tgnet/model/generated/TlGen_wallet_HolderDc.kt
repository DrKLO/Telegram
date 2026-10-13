package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_wallet_HolderDc : TlGen_Object {
  public data class TL_wallet_holderDc(
    public val dc: Int,
    public val public_key: List<Byte>,
  ) : TlGen_wallet_HolderDc() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(dc)
      stream.writeByteArray(public_key.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0xF9D612EFU
    }
  }
}

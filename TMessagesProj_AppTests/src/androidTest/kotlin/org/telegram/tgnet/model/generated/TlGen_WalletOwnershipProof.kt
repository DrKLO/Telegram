package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_WalletOwnershipProof : TlGen_Object {
  public data class TL_walletOwnershipProof(
    public val timestamp: Int,
    public val signature: List<Byte>,
  ) : TlGen_WalletOwnershipProof() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(timestamp)
      stream.writeByteArray(signature.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x60BCCB0DU
    }
  }
}

package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_wallet_UserAddresses : TlGen_Object {
  public data class TL_wallet_userAddresses(
    public val addresses: List<TlGen_WalletUserAddress>,
    public val users: List<TlGen_User>,
  ) : TlGen_wallet_UserAddresses() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, addresses)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x928E7B55U
    }
  }
}

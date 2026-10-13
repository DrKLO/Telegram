package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_WalletUserAddress : TlGen_Object {
  public data class TL_walletUserAddress(
    public val user_id: Long?,
    public val address: String,
    public val public_key: List<Byte>,
  ) : TlGen_WalletUserAddress() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (user_id != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      user_id?.let { stream.writeInt64(it) }
      stream.writeString(address)
      stream.writeByteArray(public_key.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0xFE78EECCU
    }
  }
}

package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_wallet_Transactions : TlGen_Object {
  public data class TL_wallet_transactions(
    public val balance: Long,
    public val transactions: List<TlGen_WalletTransaction>,
    public val next_offset: String?,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_wallet_Transactions() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (next_offset != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(balance)
      TlGen_Vector.serialize(stream, transactions)
      next_offset?.let { stream.writeString(it) }
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4322D5A5U
    }
  }
}

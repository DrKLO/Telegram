package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_wallet_ExistingBalance : TlGen_Object {
  public data class TL_wallet_existingBalance(
    public val has_balance: Boolean,
    public val url: String,
  ) : TlGen_wallet_ExistingBalance() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (has_balance) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(url)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBDE90A8DU
    }
  }
}

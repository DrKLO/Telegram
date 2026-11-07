package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_wallet_SecretPhraseParts : TlGen_Object {
  public data class TL_wallet_secretPhraseParts(
    public val token: String,
    public val dcs: List<Int>,
  ) : TlGen_wallet_SecretPhraseParts() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(token)
      TlGen_Vector.serializeInt(stream, dcs)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE6D0EF01U
    }
  }
}

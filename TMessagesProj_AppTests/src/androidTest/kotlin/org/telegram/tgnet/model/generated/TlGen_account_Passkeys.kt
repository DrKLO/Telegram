package org.Tajgram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_account_Passkeys : TlGen_Object {
  public data class TL_account_passkeys(
    public val passkeys: List<TlGen_Passkey>,
  ) : TlGen_account_Passkeys() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, passkeys)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF8E0AA1CU
    }
  }
}

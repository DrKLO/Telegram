package org.Tajgram.tgnet.model.generated

import kotlin.Byte
import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_account_TmpPassword : TlGen_Object {
  public data class TL_account_tmpPassword(
    public val tmp_password: List<Byte>,
    public val valid_until: Int,
  ) : TlGen_account_TmpPassword() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeByteArray(tmp_password.toByteArray())
      stream.writeInt32(valid_until)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDB64FD34U
    }
  }
}

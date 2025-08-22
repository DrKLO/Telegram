package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_account_SentEmailCode : TlGen_Object {
  public data class TL_account_sentEmailCode(
    public val email_pattern: String,
    public val length: Int,
  ) : TlGen_account_SentEmailCode() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(email_pattern)
      stream.writeInt32(length)
    }

    public companion object {
      public const val MAGIC: UInt = 0x811F854FU
    }
  }
}

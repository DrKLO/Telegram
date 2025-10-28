package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_SecurePlainData : TlGen_Object {
  public data class TL_securePlainPhone(
    public val phone: String,
  ) : TlGen_SecurePlainData() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(phone)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7D6099DDU
    }
  }

  public data class TL_securePlainEmail(
    public val email: String,
  ) : TlGen_SecurePlainData() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(email)
    }

    public companion object {
      public const val MAGIC: UInt = 0x21EC5A5FU
    }
  }
}

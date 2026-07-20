package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_help_Support : TlGen_Object {
  public data class TL_help_support(
    public val phone_number: String,
    public val user: TlGen_User,
  ) : TlGen_help_Support() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(phone_number)
      user.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x17C6B5F6U
    }
  }
}

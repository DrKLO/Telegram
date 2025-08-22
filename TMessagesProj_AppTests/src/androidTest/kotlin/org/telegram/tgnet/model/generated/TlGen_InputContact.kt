package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputContact : TlGen_Object {
  public data class TL_inputPhoneContact(
    public val client_id: Long,
    public val phone: String,
    public val first_name: String,
    public val last_name: String,
  ) : TlGen_InputContact() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(client_id)
      stream.writeString(phone)
      stream.writeString(first_name)
      stream.writeString(last_name)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF392B7F4U
    }
  }
}

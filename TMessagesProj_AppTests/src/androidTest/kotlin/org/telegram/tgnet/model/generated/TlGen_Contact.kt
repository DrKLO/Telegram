package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_Contact : TlGen_Object {
  public data class TL_contact(
    public val user_id: Long,
    public val mutual: Boolean,
  ) : TlGen_Contact() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(user_id)
      stream.writeBool(mutual)
    }

    public companion object {
      public const val MAGIC: UInt = 0x145ADE0BU
    }
  }
}

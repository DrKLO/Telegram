package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ImportedContact : TlGen_Object {
  public data class TL_importedContact(
    public val user_id: Long,
    public val client_id: Long,
  ) : TlGen_ImportedContact() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(user_id)
      stream.writeInt64(client_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC13E3C50U
    }
  }
}

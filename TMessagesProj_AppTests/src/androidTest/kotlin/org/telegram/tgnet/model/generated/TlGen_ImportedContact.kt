package org.Tajgram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

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

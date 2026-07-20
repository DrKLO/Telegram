package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PopularContact : TlGen_Object {
  public data class TL_popularContact(
    public val client_id: Long,
    public val importers: Int,
  ) : TlGen_PopularContact() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(client_id)
      stream.writeInt32(importers)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5CE14175U
    }
  }
}

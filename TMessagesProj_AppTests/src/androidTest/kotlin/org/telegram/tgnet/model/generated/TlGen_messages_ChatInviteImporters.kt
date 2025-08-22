package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_ChatInviteImporters : TlGen_Object {
  public data class TL_messages_chatInviteImporters(
    public val count: Int,
    public val importers: List<TlGen_ChatInviteImporter>,
    public val users: List<TlGen_User>,
  ) : TlGen_messages_ChatInviteImporters() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(count)
      TlGen_Vector.serialize(stream, importers)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x81B6B00AU
    }
  }
}

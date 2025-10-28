package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_ChatAdminsWithInvites : TlGen_Object {
  public data class TL_messages_chatAdminsWithInvites(
    public val admins: List<TlGen_ChatAdminWithInvites>,
    public val users: List<TlGen_User>,
  ) : TlGen_messages_ChatAdminsWithInvites() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, admins)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB69B72D7U
    }
  }
}

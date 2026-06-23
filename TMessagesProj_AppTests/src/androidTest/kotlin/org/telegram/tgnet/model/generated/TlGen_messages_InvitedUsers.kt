package org.Tajgram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_InvitedUsers : TlGen_Object {
  public data class TL_messages_invitedUsers(
    public val updates: TlGen_Updates,
    public val missing_invitees: List<TlGen_MissingInvitee>,
  ) : TlGen_messages_InvitedUsers() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      updates.serializeToStream(stream)
      TlGen_Vector.serialize(stream, missing_invitees)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7F5DEFA6U
    }
  }
}

package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_ExportedChatInvite : TlGen_Object {
  public data class TL_messages_exportedChatInvite(
    public val invite: TlGen_ExportedChatInvite,
    public val users: List<TlGen_User>,
  ) : TlGen_messages_ExportedChatInvite() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      invite.serializeToStream(stream)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1871BE50U
    }
  }

  public data class TL_messages_exportedChatInviteReplaced(
    public val invite: TlGen_ExportedChatInvite,
    public val new_invite: TlGen_ExportedChatInvite,
    public val users: List<TlGen_User>,
  ) : TlGen_messages_ExportedChatInvite() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      invite.serializeToStream(stream)
      new_invite.serializeToStream(stream)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x222600EFU
    }
  }
}

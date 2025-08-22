package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_PeerDialogs : TlGen_Object {
  public data class TL_messages_peerDialogs(
    public val dialogs: List<TlGen_Dialog>,
    public val messages: List<TlGen_Message>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
    public val state: TlGen_updates_State,
  ) : TlGen_messages_PeerDialogs() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, dialogs)
      TlGen_Vector.serialize(stream, messages)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
      state.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3371C354U
    }
  }
}

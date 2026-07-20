package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_communities_ParticipantJoinedChats : TlGen_Object {
  public data class TL_communities_participantJoinedChats(
    public val creator_chat_ids: List<Long>,
    public val joined_chat_ids: List<Long>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_communities_ParticipantJoinedChats() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serializeLong(stream, creator_chat_ids)
      TlGen_Vector.serializeLong(stream, joined_chat_ids)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8D78512AU
    }
  }
}

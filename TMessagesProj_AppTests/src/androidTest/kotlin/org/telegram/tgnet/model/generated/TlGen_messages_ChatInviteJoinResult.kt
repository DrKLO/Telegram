package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_ChatInviteJoinResult : TlGen_Object {
  public data class TL_messages_chatInviteJoinResultOk(
    public val updates: TlGen_Updates,
  ) : TlGen_messages_ChatInviteJoinResult() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      updates.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x445663A7U
    }
  }

  public data class TL_messages_chatInviteJoinResultWebView(
    public val bot_id: Long,
    public val query_id: Long,
    public val users: List<TlGen_User>,
  ) : TlGen_messages_ChatInviteJoinResult() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(bot_id)
      stream.writeInt64(query_id)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x61CA29D3U
    }
  }
}

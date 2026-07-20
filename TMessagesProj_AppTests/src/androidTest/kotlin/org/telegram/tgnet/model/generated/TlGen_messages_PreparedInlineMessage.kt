package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_PreparedInlineMessage : TlGen_Object {
  public data class TL_messages_preparedInlineMessage(
    public val query_id: Long,
    public val result: TlGen_BotInlineResult,
    public val peer_types: List<TlGen_InlineQueryPeerType>,
    public val cache_time: Int,
    public val users: List<TlGen_User>,
  ) : TlGen_messages_PreparedInlineMessage() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(query_id)
      result.serializeToStream(stream)
      TlGen_Vector.serialize(stream, peer_types)
      stream.writeInt32(cache_time)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFF57708DU
    }
  }
}

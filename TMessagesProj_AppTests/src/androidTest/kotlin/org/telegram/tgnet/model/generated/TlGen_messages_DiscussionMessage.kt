package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_DiscussionMessage : TlGen_Object {
  public data class TL_messages_discussionMessage(
    public val messages: List<TlGen_Message>,
    public val max_id: Int?,
    public val read_inbox_max_id: Int?,
    public val read_outbox_max_id: Int?,
    public val unread_count: Int,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_messages_DiscussionMessage() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (max_id != null) result = result or 1U
        if (read_inbox_max_id != null) result = result or 2U
        if (read_outbox_max_id != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serialize(stream, messages)
      max_id?.let { stream.writeInt32(it) }
      read_inbox_max_id?.let { stream.writeInt32(it) }
      read_outbox_max_id?.let { stream.writeInt32(it) }
      stream.writeInt32(unread_count)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA6341782U
    }
  }
}

package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_ForumTopics : TlGen_Object {
  public data class TL_messages_forumTopics(
    public val order_by_create_date: Boolean,
    public val count: Int,
    public val topics: List<TlGen_ForumTopic>,
    public val messages: List<TlGen_Message>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
    public val pts: Int,
  ) : TlGen_messages_ForumTopics() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (order_by_create_date) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(count)
      TlGen_Vector.serialize(stream, topics)
      TlGen_Vector.serialize(stream, messages)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
      stream.writeInt32(pts)
    }

    public companion object {
      public const val MAGIC: UInt = 0x367617D3U
    }
  }
}

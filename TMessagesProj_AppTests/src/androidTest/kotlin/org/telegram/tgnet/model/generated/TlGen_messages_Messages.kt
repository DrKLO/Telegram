package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_Messages : TlGen_Object {
  public data class TL_messages_messagesNotModified(
    public val count: Int,
  ) : TlGen_messages_Messages() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(count)
    }

    public companion object {
      public const val MAGIC: UInt = 0x74535F21U
    }
  }

  public data class TL_messages_channelMessages(
    public val inexact: Boolean,
    public val pts: Int,
    public val count: Int,
    public val offset_id_offset: Int?,
    public val messages: List<TlGen_Message>,
    public val topics: List<TlGen_ForumTopic>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_messages_Messages() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (inexact) result = result or 2U
        if (offset_id_offset != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(pts)
      stream.writeInt32(count)
      offset_id_offset?.let { stream.writeInt32(it) }
      TlGen_Vector.serialize(stream, messages)
      TlGen_Vector.serialize(stream, topics)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC776BA4EU
    }
  }

  public data class TL_messages_messages(
    public val messages: List<TlGen_Message>,
    public val topics: List<TlGen_ForumTopic>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_messages_Messages() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, messages)
      TlGen_Vector.serialize(stream, topics)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1D73E7EAU
    }
  }

  public data class TL_messages_messagesSlice(
    public val inexact: Boolean,
    public val count: Int,
    public val next_rate: Int?,
    public val offset_id_offset: Int?,
    public val search_flood: TlGen_SearchPostsFlood?,
    public val messages: List<TlGen_Message>,
    public val topics: List<TlGen_ForumTopic>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_messages_Messages() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (next_rate != null) result = result or 1U
        if (inexact) result = result or 2U
        if (offset_id_offset != null) result = result or 4U
        if (search_flood != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(count)
      next_rate?.let { stream.writeInt32(it) }
      offset_id_offset?.let { stream.writeInt32(it) }
      search_flood?.serializeToStream(stream)
      TlGen_Vector.serialize(stream, messages)
      TlGen_Vector.serialize(stream, topics)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5F206716U
    }
  }
}

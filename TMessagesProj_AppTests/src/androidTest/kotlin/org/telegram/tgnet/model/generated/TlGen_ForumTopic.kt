package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ForumTopic : TlGen_Object {
  public data class TL_forumTopicDeleted(
    public val id: Int,
  ) : TlGen_ForumTopic() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x023F109BU
    }
  }

  public data class TL_forumTopic(
    public val my: Boolean,
    public val closed: Boolean,
    public val pinned: Boolean,
    public val short: Boolean,
    public val hidden: Boolean,
    public val title_missing: Boolean,
    public val id: Int,
    public val date: Int,
    public val peer: TlGen_Peer,
    public val title: String,
    public val icon_color: Int,
    public val icon_emoji_id: Long?,
    public val top_message: Int,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val unread_mentions_count: Int,
    public val unread_reactions_count: Int,
    public val from_id: TlGen_Peer,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val draft: TlGen_DraftMessage?,
  ) : TlGen_ForumTopic() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (icon_emoji_id != null) result = result or 1U
        if (my) result = result or 2U
        if (closed) result = result or 4U
        if (pinned) result = result or 8U
        if (draft != null) result = result or 16U
        if (short) result = result or 32U
        if (hidden) result = result or 64U
        if (title_missing) result = result or 128U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeInt32(date)
      peer.serializeToStream(stream)
      stream.writeString(title)
      stream.writeInt32(icon_color)
      icon_emoji_id?.let { stream.writeInt64(it) }
      stream.writeInt32(top_message)
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      stream.writeInt32(unread_mentions_count)
      stream.writeInt32(unread_reactions_count)
      from_id.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      draft?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCDFF0ECAU
    }
  }

  public data class TL_forumTopic_layer215(
    public val my: Boolean,
    public val closed: Boolean,
    public val pinned: Boolean,
    public val short: Boolean,
    public val hidden: Boolean,
    public val id: Int,
    public val date: Int,
    public val title: String,
    public val icon_color: Int,
    public val icon_emoji_id: Long?,
    public val top_message: Int,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val unread_mentions_count: Int,
    public val unread_reactions_count: Int,
    public val from_id: TlGen_Peer,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val draft: TlGen_DraftMessage?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (icon_emoji_id != null) result = result or 1U
        if (my) result = result or 2U
        if (closed) result = result or 4U
        if (pinned) result = result or 8U
        if (draft != null) result = result or 16U
        if (short) result = result or 32U
        if (hidden) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeInt32(date)
      stream.writeString(title)
      stream.writeInt32(icon_color)
      icon_emoji_id?.let { stream.writeInt64(it) }
      stream.writeInt32(top_message)
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      stream.writeInt32(unread_mentions_count)
      stream.writeInt32(unread_reactions_count)
      from_id.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      draft?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x71701DA9U
    }
  }
}

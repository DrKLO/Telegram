package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_Dialog : TlGen_Object {
  public data class TL_dialogFolder(
    public val pinned: Boolean,
    public val folder: TlGen_Folder,
    public val peer: TlGen_Peer,
    public val top_message: Int,
    public val unread_muted_peers_count: Int,
    public val unread_unmuted_peers_count: Int,
    public val unread_muted_messages_count: Int,
    public val unread_unmuted_messages_count: Int,
  ) : TlGen_Dialog() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (pinned) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      folder.serializeToStream(stream)
      peer.serializeToStream(stream)
      stream.writeInt32(top_message)
      stream.writeInt32(unread_muted_peers_count)
      stream.writeInt32(unread_unmuted_peers_count)
      stream.writeInt32(unread_muted_messages_count)
      stream.writeInt32(unread_unmuted_messages_count)
    }

    public companion object {
      public const val MAGIC: UInt = 0x71BD134CU
    }
  }

  public data class TL_dialog(
    public val pinned: Boolean,
    public val unread_mark: Boolean,
    public val view_forum_as_messages: Boolean,
    public val peer: TlGen_Peer,
    public val top_message: Int,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val unread_mentions_count: Int,
    public val unread_reactions_count: Int,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val pts: Int?,
    public val draft: TlGen_DraftMessage?,
    public val folder_id: Int?,
    public val ttl_period: Int?,
  ) : TlGen_Dialog() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (pts != null) result = result or 1U
        if (draft != null) result = result or 2U
        if (pinned) result = result or 4U
        if (unread_mark) result = result or 8U
        if (folder_id != null) result = result or 16U
        if (ttl_period != null) result = result or 32U
        if (view_forum_as_messages) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(top_message)
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      stream.writeInt32(unread_mentions_count)
      stream.writeInt32(unread_reactions_count)
      notify_settings.serializeToStream(stream)
      pts?.let { stream.writeInt32(it) }
      draft?.serializeToStream(stream)
      folder_id?.let { stream.writeInt32(it) }
      ttl_period?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xD58A08C6U
    }
  }
}

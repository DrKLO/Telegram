package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ChatFull : TlGen_Object {
  public data class TL_chatFull(
    public val can_set_username: Boolean,
    public val has_scheduled: Boolean,
    public val translations_disabled: Boolean,
    public val id: Long,
    public val about: String,
    public val participants: TlGen_ChatParticipants,
    public val chat_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>?,
    public val pinned_msg_id: Int?,
    public val folder_id: Int?,
    public val call: TlGen_InputGroupCall?,
    public val ttl_period: Int?,
    public val groupcall_default_join_as: TlGen_Peer?,
    public val theme_emoticon: String?,
    public val available_reactions: TlGen_ChatReactions?,
    public val reactions_limit: Int?,
    public val multiflags_17: Multiflags_17?,
  ) : TlGen_ChatFull() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (chat_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (pinned_msg_id != null) result = result or 64U
        if (can_set_username) result = result or 128U
        if (has_scheduled) result = result or 256U
        if (folder_id != null) result = result or 2048U
        if (call != null) result = result or 4096U
        if (exported_invite != null) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (groupcall_default_join_as != null) result = result or 32768U
        if (theme_emoticon != null) result = result or 65536U
        if (multiflags_17 != null) result = result or 131072U
        if (available_reactions != null) result = result or 262144U
        if (translations_disabled) result = result or 524288U
        if (reactions_limit != null) result = result or 1048576U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeString(about)
      participants.serializeToStream(stream)
      chat_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      bot_info?.let { TlGen_Vector.serialize(stream, it) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      call?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
      groupcall_default_join_as?.serializeToStream(stream)
      theme_emoticon?.let { stream.writeString(it) }
      multiflags_17?.let { stream.writeInt32(it.requests_pending) }
      multiflags_17?.let { TlGen_Vector.serializeLong(stream, it.recent_requesters) }
      available_reactions?.serializeToStream(stream)
      reactions_limit?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_17(
      public val requests_pending: Int,
      public val recent_requesters: List<Long>,
    )

    public companion object {
      public const val MAGIC: UInt = 0x2633421BU
    }
  }

  public data class TL_channelFull(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val can_set_location: Boolean,
    public val has_scheduled: Boolean,
    public val can_view_stats: Boolean,
    public val blocked: Boolean,
    public val can_delete_channel: Boolean,
    public val antispam: Boolean,
    public val participants_hidden: Boolean,
    public val translations_disabled: Boolean,
    public val stories_pinned_available: Boolean,
    public val view_forum_as_messages: Boolean,
    public val restricted_sponsored: Boolean,
    public val can_view_revenue: Boolean,
    public val paid_media_allowed: Boolean,
    public val can_view_stars_revenue: Boolean,
    public val paid_reactions_available: Boolean,
    public val stargifts_available: Boolean,
    public val paid_messages_available: Boolean,
    public val id: Long,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val online_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val folder_id: Int?,
    public val linked_chat_id: Long?,
    public val location: TlGen_ChannelLocation?,
    public val slowmode_seconds: Int?,
    public val slowmode_next_send_date: Int?,
    public val stats_dc: Int?,
    public val pts: Int,
    public val call: TlGen_InputGroupCall?,
    public val ttl_period: Int?,
    public val pending_suggestions: List<String>?,
    public val groupcall_default_join_as: TlGen_Peer?,
    public val theme_emoticon: String?,
    public val default_send_as: TlGen_Peer?,
    public val available_reactions: TlGen_ChatReactions?,
    public val reactions_limit: Int?,
    public val stories: TlGen_PeerStories?,
    public val wallpaper: TlGen_WallPaper?,
    public val boosts_applied: Int?,
    public val boosts_unrestrict: Int?,
    public val emojiset: TlGen_StickerSet?,
    public val bot_verification: TlGen_BotVerification?,
    public val stargifts_count: Int?,
    public val send_paid_messages_stars: Long?,
    public val main_tab: TlGen_ProfileTab?,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
    public val multiflags_28: Multiflags_28?,
  ) : TlGen_ChatFull() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        if (folder_id != null) result = result or 2048U
        if (stats_dc != null) result = result or 4096U
        if (online_count != null) result = result or 8192U
        if (linked_chat_id != null) result = result or 16384U
        if (location != null) result = result or 32768U
        if (can_set_location) result = result or 65536U
        if (slowmode_seconds != null) result = result or 131072U
        if (slowmode_next_send_date != null) result = result or 262144U
        if (has_scheduled) result = result or 524288U
        if (can_view_stats) result = result or 1048576U
        if (call != null) result = result or 2097152U
        if (blocked) result = result or 4194304U
        if (exported_invite != null) result = result or 8388608U
        if (ttl_period != null) result = result or 16777216U
        if (pending_suggestions != null) result = result or 33554432U
        if (groupcall_default_join_as != null) result = result or 67108864U
        if (theme_emoticon != null) result = result or 134217728U
        if (multiflags_28 != null) result = result or 268435456U
        if (default_send_as != null) result = result or 536870912U
        if (available_reactions != null) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (can_delete_channel) result = result or 1U
        if (antispam) result = result or 2U
        if (participants_hidden) result = result or 4U
        if (translations_disabled) result = result or 8U
        if (stories != null) result = result or 16U
        if (stories_pinned_available) result = result or 32U
        if (view_forum_as_messages) result = result or 64U
        if (wallpaper != null) result = result or 128U
        if (boosts_applied != null) result = result or 256U
        if (boosts_unrestrict != null) result = result or 512U
        if (emojiset != null) result = result or 1024U
        if (restricted_sponsored) result = result or 2048U
        if (can_view_revenue) result = result or 4096U
        if (reactions_limit != null) result = result or 8192U
        if (paid_media_allowed) result = result or 16384U
        if (can_view_stars_revenue) result = result or 32768U
        if (paid_reactions_available) result = result or 65536U
        if (bot_verification != null) result = result or 131072U
        if (stargifts_count != null) result = result or 262144U
        if (stargifts_available) result = result or 524288U
        if (paid_messages_available) result = result or 1048576U
        if (send_paid_messages_stars != null) result = result or 2097152U
        if (main_tab != null) result = result or 4194304U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      online_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt64(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      linked_chat_id?.let { stream.writeInt64(it) }
      location?.serializeToStream(stream)
      slowmode_seconds?.let { stream.writeInt32(it) }
      slowmode_next_send_date?.let { stream.writeInt32(it) }
      stats_dc?.let { stream.writeInt32(it) }
      stream.writeInt32(pts)
      call?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
      pending_suggestions?.let { TlGen_Vector.serializeString(stream, it) }
      groupcall_default_join_as?.serializeToStream(stream)
      theme_emoticon?.let { stream.writeString(it) }
      multiflags_28?.let { stream.writeInt32(it.requests_pending) }
      multiflags_28?.let { TlGen_Vector.serializeLong(stream, it.recent_requesters) }
      default_send_as?.serializeToStream(stream)
      available_reactions?.serializeToStream(stream)
      reactions_limit?.let { stream.writeInt32(it) }
      stories?.serializeToStream(stream)
      wallpaper?.serializeToStream(stream)
      boosts_applied?.let { stream.writeInt32(it) }
      boosts_unrestrict?.let { stream.writeInt32(it) }
      emojiset?.serializeToStream(stream)
      bot_verification?.serializeToStream(stream)
      stargifts_count?.let { stream.writeInt32(it) }
      send_paid_messages_stars?.let { stream.writeInt64(it) }
      main_tab?.serializeToStream(stream)
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Long,
      public val migrated_from_max_id: Int,
    )

    public data class Multiflags_28(
      public val requests_pending: Int,
      public val recent_requesters: List<Long>,
    )

    public companion object {
      public const val MAGIC: UInt = 0xE4E0B29DU
    }
  }

  public data class TL_chatFull_layer27(
    public val id: Int,
    public val participants: TlGen_ChatParticipants,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      participants.serializeToStream(stream)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x630E61BEU
    }
  }

  public data class TL_chatFull_layer30(
    public val id: Int,
    public val participants: TlGen_ChatParticipants,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      participants.serializeToStream(stream)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCADE0791U
    }
  }

  public data class TL_chatFull_layer86(
    public val id: Int,
    public val participants: TlGen_ChatParticipants,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite,
    public val bot_info: List<TlGen_BotInfo>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      participants.serializeToStream(stream)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2E02A614U
    }
  }

  public data class TL_channelFull_layer40(
    public val can_view_participants: Boolean,
    public val id: Int,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val kicked_count: Int?,
    public val read_inbox_max_id: Int,
    public val unread_count: Int,
    public val unread_important_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (kicked_count != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      kicked_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(unread_count)
      stream.writeInt32(unread_important_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFAB31AA3U
    }
  }

  public data class TL_channelFull_layer48(
    public val can_view_participants: Boolean,
    public val id: Int,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val kicked_count: Int?,
    public val read_inbox_max_id: Int,
    public val unread_count: Int,
    public val unread_important_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite,
    public val bot_info: List<TlGen_BotInfo>,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (kicked_count != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      kicked_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(unread_count)
      stream.writeInt32(unread_important_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt32(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
    }

    public data class Multiflags_4(
      public val migrated_from_chat_id: Int,
      public val migrated_from_max_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x9E341DDFU
    }
  }

  public data class TL_channelFull_layer52(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val id: Int,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val kicked_count: Int?,
    public val read_inbox_max_id: Int,
    public val unread_count: Int,
    public val unread_important_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (kicked_count != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      kicked_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(unread_count)
      stream.writeInt32(unread_important_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt32(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_4(
      public val migrated_from_chat_id: Int,
      public val migrated_from_max_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x97BEE562U
    }
  }

  public data class TL_channelFull_layer67(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val id: Int,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val kicked_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (kicked_count != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      kicked_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt32(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_4(
      public val migrated_from_chat_id: Int,
      public val migrated_from_max_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xC3D5512FU
    }
  }

  public data class TL_channelFull_layer70(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val id: Int,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt32(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Int,
      public val migrated_from_max_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x95CB5F57U
    }
  }

  public data class TL_channelFull_layer71(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val id: Int,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt32(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Int,
      public val migrated_from_max_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x17F45FCFU
    }
  }

  public data class TL_channelFull_layer90(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val id: Int,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt32(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Int,
      public val migrated_from_max_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x76AF5481U
    }
  }

  public data class TL_chatFull_layer92(
    public val id: Int,
    public val participants: TlGen_ChatParticipants,
    public val chat_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite,
    public val bot_info: List<TlGen_BotInfo>?,
    public val pinned_msg_id: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (chat_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (pinned_msg_id != null) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      participants.serializeToStream(stream)
      chat_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite.serializeToStream(stream)
      bot_info?.let { TlGen_Vector.serialize(stream, it) }
      pinned_msg_id?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xEDD2A791U
    }
  }

  public data class TL_channelFull_layer98(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val can_view_stats: Boolean,
    public val id: Int,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val online_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        if (can_view_stats) result = result or 4096U
        if (online_count != null) result = result or 8192U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      online_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt32(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Int,
      public val migrated_from_max_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x1C87A71AU
    }
  }

  public data class TL_chatFull_layer98(
    public val can_set_username: Boolean,
    public val id: Int,
    public val about: String,
    public val participants: TlGen_ChatParticipants,
    public val chat_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite,
    public val bot_info: List<TlGen_BotInfo>?,
    public val pinned_msg_id: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (chat_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (pinned_msg_id != null) result = result or 64U
        if (can_set_username) result = result or 128U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(about)
      participants.serializeToStream(stream)
      chat_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite.serializeToStream(stream)
      bot_info?.let { TlGen_Vector.serialize(stream, it) }
      pinned_msg_id?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x22A235DAU
    }
  }

  public data class TL_chatFull_layer121(
    public val can_set_username: Boolean,
    public val has_scheduled: Boolean,
    public val id: Int,
    public val about: String,
    public val participants: TlGen_ChatParticipants,
    public val chat_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite,
    public val bot_info: List<TlGen_BotInfo>?,
    public val pinned_msg_id: Int?,
    public val folder_id: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (chat_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (pinned_msg_id != null) result = result or 64U
        if (can_set_username) result = result or 128U
        if (has_scheduled) result = result or 256U
        if (folder_id != null) result = result or 2048U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(about)
      participants.serializeToStream(stream)
      chat_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite.serializeToStream(stream)
      bot_info?.let { TlGen_Vector.serialize(stream, it) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x1B7C9DB3U
    }
  }

  public data class TL_channelFull_layer99(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val can_view_stats: Boolean,
    public val id: Int,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val online_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val folder_id: Int?,
    public val pts: Int,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        if (folder_id != null) result = result or 2048U
        if (can_view_stats) result = result or 4096U
        if (online_count != null) result = result or 8192U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      online_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt32(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      stream.writeInt32(pts)
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Int,
      public val migrated_from_max_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x03648977U
    }
  }

  public data class TL_channelFull_layer101(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val can_view_stats: Boolean,
    public val id: Int,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val folder_id: Int?,
    public val pts: Int,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_13: Multiflags_13?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        if (folder_id != null) result = result or 2048U
        if (can_view_stats) result = result or 4096U
        if (multiflags_13 != null) result = result or 8192U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      multiflags_13?.let { stream.writeInt32(it.online_count) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt32(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      multiflags_13?.let { stream.writeInt32(it.linked_chat_id) }
      stream.writeInt32(pts)
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_13(
      public val online_count: Int,
      public val linked_chat_id: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Int,
      public val migrated_from_max_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x9882E516U
    }
  }

  public data class TL_channelFull_layer103(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val can_view_stats: Boolean,
    public val can_set_location: Boolean,
    public val id: Int,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val online_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val folder_id: Int?,
    public val linked_chat_id: Int?,
    public val location: TlGen_ChannelLocation?,
    public val pts: Int,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        if (folder_id != null) result = result or 2048U
        if (can_view_stats) result = result or 4096U
        if (online_count != null) result = result or 8192U
        if (linked_chat_id != null) result = result or 16384U
        if (location != null) result = result or 32768U
        if (can_set_location) result = result or 65536U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      online_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt32(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      linked_chat_id?.let { stream.writeInt32(it) }
      location?.serializeToStream(stream)
      stream.writeInt32(pts)
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Int,
      public val migrated_from_max_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x10916653U
    }
  }

  public data class TL_channelFull_layer110(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val can_view_stats: Boolean,
    public val can_set_location: Boolean,
    public val has_scheduled: Boolean,
    public val id: Int,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val online_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val folder_id: Int?,
    public val linked_chat_id: Int?,
    public val location: TlGen_ChannelLocation?,
    public val slowmode_seconds: Int?,
    public val slowmode_next_send_date: Int?,
    public val pts: Int,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        if (folder_id != null) result = result or 2048U
        if (can_view_stats) result = result or 4096U
        if (online_count != null) result = result or 8192U
        if (linked_chat_id != null) result = result or 16384U
        if (location != null) result = result or 32768U
        if (can_set_location) result = result or 65536U
        if (slowmode_seconds != null) result = result or 131072U
        if (slowmode_next_send_date != null) result = result or 262144U
        if (has_scheduled) result = result or 524288U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      online_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt32(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      linked_chat_id?.let { stream.writeInt32(it) }
      location?.serializeToStream(stream)
      slowmode_seconds?.let { stream.writeInt32(it) }
      slowmode_next_send_date?.let { stream.writeInt32(it) }
      stream.writeInt32(pts)
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Int,
      public val migrated_from_max_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x2D895C74U
    }
  }

  public data class TL_channelFull_layer121(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val can_set_location: Boolean,
    public val has_scheduled: Boolean,
    public val can_view_stats: Boolean,
    public val blocked: Boolean,
    public val id: Int,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val online_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val folder_id: Int?,
    public val linked_chat_id: Int?,
    public val location: TlGen_ChannelLocation?,
    public val slowmode_seconds: Int?,
    public val slowmode_next_send_date: Int?,
    public val stats_dc: Int?,
    public val pts: Int,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        if (folder_id != null) result = result or 2048U
        if (stats_dc != null) result = result or 4096U
        if (online_count != null) result = result or 8192U
        if (linked_chat_id != null) result = result or 16384U
        if (location != null) result = result or 32768U
        if (can_set_location) result = result or 65536U
        if (slowmode_seconds != null) result = result or 131072U
        if (slowmode_next_send_date != null) result = result or 262144U
        if (has_scheduled) result = result or 524288U
        if (can_view_stats) result = result or 1048576U
        if (blocked) result = result or 4194304U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      online_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt32(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      linked_chat_id?.let { stream.writeInt32(it) }
      location?.serializeToStream(stream)
      slowmode_seconds?.let { stream.writeInt32(it) }
      slowmode_next_send_date?.let { stream.writeInt32(it) }
      stats_dc?.let { stream.writeInt32(it) }
      stream.writeInt32(pts)
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Int,
      public val migrated_from_max_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xF0E6672AU
    }
  }

  public data class TL_chatFull_layer122(
    public val can_set_username: Boolean,
    public val has_scheduled: Boolean,
    public val id: Int,
    public val about: String,
    public val participants: TlGen_ChatParticipants,
    public val chat_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite,
    public val bot_info: List<TlGen_BotInfo>?,
    public val pinned_msg_id: Int?,
    public val folder_id: Int?,
    public val call: TlGen_InputGroupCall?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (chat_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (pinned_msg_id != null) result = result or 64U
        if (can_set_username) result = result or 128U
        if (has_scheduled) result = result or 256U
        if (folder_id != null) result = result or 2048U
        if (call != null) result = result or 4096U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(about)
      participants.serializeToStream(stream)
      chat_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite.serializeToStream(stream)
      bot_info?.let { TlGen_Vector.serialize(stream, it) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      call?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0DC8C181U
    }
  }

  public data class TL_channelFull_layer122(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val can_set_location: Boolean,
    public val has_scheduled: Boolean,
    public val can_view_stats: Boolean,
    public val blocked: Boolean,
    public val id: Int,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val online_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val folder_id: Int?,
    public val linked_chat_id: Int?,
    public val location: TlGen_ChannelLocation?,
    public val slowmode_seconds: Int?,
    public val slowmode_next_send_date: Int?,
    public val stats_dc: Int?,
    public val pts: Int,
    public val call: TlGen_InputGroupCall?,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        if (folder_id != null) result = result or 2048U
        if (stats_dc != null) result = result or 4096U
        if (online_count != null) result = result or 8192U
        if (linked_chat_id != null) result = result or 16384U
        if (location != null) result = result or 32768U
        if (can_set_location) result = result or 65536U
        if (slowmode_seconds != null) result = result or 131072U
        if (slowmode_next_send_date != null) result = result or 262144U
        if (has_scheduled) result = result or 524288U
        if (can_view_stats) result = result or 1048576U
        if (call != null) result = result or 2097152U
        if (blocked) result = result or 4194304U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      online_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt32(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      linked_chat_id?.let { stream.writeInt32(it) }
      location?.serializeToStream(stream)
      slowmode_seconds?.let { stream.writeInt32(it) }
      slowmode_next_send_date?.let { stream.writeInt32(it) }
      stats_dc?.let { stream.writeInt32(it) }
      stream.writeInt32(pts)
      call?.serializeToStream(stream)
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Int,
      public val migrated_from_max_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xEF3A6ACDU
    }
  }

  public data class TL_chatFull_layer123(
    public val can_set_username: Boolean,
    public val has_scheduled: Boolean,
    public val id: Int,
    public val about: String,
    public val participants: TlGen_ChatParticipants,
    public val chat_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>?,
    public val pinned_msg_id: Int?,
    public val folder_id: Int?,
    public val call: TlGen_InputGroupCall?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (chat_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (pinned_msg_id != null) result = result or 64U
        if (can_set_username) result = result or 128U
        if (has_scheduled) result = result or 256U
        if (folder_id != null) result = result or 2048U
        if (call != null) result = result or 4096U
        if (exported_invite != null) result = result or 8192U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(about)
      participants.serializeToStream(stream)
      chat_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      bot_info?.let { TlGen_Vector.serialize(stream, it) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      call?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF3474AF6U
    }
  }

  public data class TL_channelFull_layer123(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val can_set_location: Boolean,
    public val has_scheduled: Boolean,
    public val can_view_stats: Boolean,
    public val blocked: Boolean,
    public val id: Int,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val online_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val folder_id: Int?,
    public val linked_chat_id: Int?,
    public val location: TlGen_ChannelLocation?,
    public val slowmode_seconds: Int?,
    public val slowmode_next_send_date: Int?,
    public val stats_dc: Int?,
    public val pts: Int,
    public val call: TlGen_InputGroupCall?,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        if (folder_id != null) result = result or 2048U
        if (stats_dc != null) result = result or 4096U
        if (online_count != null) result = result or 8192U
        if (linked_chat_id != null) result = result or 16384U
        if (location != null) result = result or 32768U
        if (can_set_location) result = result or 65536U
        if (slowmode_seconds != null) result = result or 131072U
        if (slowmode_next_send_date != null) result = result or 262144U
        if (has_scheduled) result = result or 524288U
        if (can_view_stats) result = result or 1048576U
        if (call != null) result = result or 2097152U
        if (blocked) result = result or 4194304U
        if (exported_invite != null) result = result or 8388608U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      online_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt32(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      linked_chat_id?.let { stream.writeInt32(it) }
      location?.serializeToStream(stream)
      slowmode_seconds?.let { stream.writeInt32(it) }
      slowmode_next_send_date?.let { stream.writeInt32(it) }
      stats_dc?.let { stream.writeInt32(it) }
      stream.writeInt32(pts)
      call?.serializeToStream(stream)
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Int,
      public val migrated_from_max_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x7A7DE4F7U
    }
  }

  public data class TL_chatFull_layer124(
    public val can_set_username: Boolean,
    public val has_scheduled: Boolean,
    public val id: Int,
    public val about: String,
    public val participants: TlGen_ChatParticipants,
    public val chat_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>?,
    public val pinned_msg_id: Int?,
    public val folder_id: Int?,
    public val call: TlGen_InputGroupCall?,
    public val ttl_period: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (chat_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (pinned_msg_id != null) result = result or 64U
        if (can_set_username) result = result or 128U
        if (has_scheduled) result = result or 256U
        if (folder_id != null) result = result or 2048U
        if (call != null) result = result or 4096U
        if (exported_invite != null) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(about)
      participants.serializeToStream(stream)
      chat_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      bot_info?.let { TlGen_Vector.serialize(stream, it) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      call?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xF06C4018U
    }
  }

  public data class TL_channelFull_layer124(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val can_set_location: Boolean,
    public val has_scheduled: Boolean,
    public val can_view_stats: Boolean,
    public val blocked: Boolean,
    public val id: Int,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val online_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val folder_id: Int?,
    public val linked_chat_id: Int?,
    public val location: TlGen_ChannelLocation?,
    public val slowmode_seconds: Int?,
    public val slowmode_next_send_date: Int?,
    public val stats_dc: Int?,
    public val pts: Int,
    public val call: TlGen_InputGroupCall?,
    public val ttl_period: Int?,
    public val pending_suggestions: List<String>?,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        if (folder_id != null) result = result or 2048U
        if (stats_dc != null) result = result or 4096U
        if (online_count != null) result = result or 8192U
        if (linked_chat_id != null) result = result or 16384U
        if (location != null) result = result or 32768U
        if (can_set_location) result = result or 65536U
        if (slowmode_seconds != null) result = result or 131072U
        if (slowmode_next_send_date != null) result = result or 262144U
        if (has_scheduled) result = result or 524288U
        if (can_view_stats) result = result or 1048576U
        if (call != null) result = result or 2097152U
        if (blocked) result = result or 4194304U
        if (exported_invite != null) result = result or 8388608U
        if (ttl_period != null) result = result or 16777216U
        if (pending_suggestions != null) result = result or 33554432U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      online_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt32(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      linked_chat_id?.let { stream.writeInt32(it) }
      location?.serializeToStream(stream)
      slowmode_seconds?.let { stream.writeInt32(it) }
      slowmode_next_send_date?.let { stream.writeInt32(it) }
      stats_dc?.let { stream.writeInt32(it) }
      stream.writeInt32(pts)
      call?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
      pending_suggestions?.let { TlGen_Vector.serializeString(stream, it) }
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Int,
      public val migrated_from_max_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x2548C037U
    }
  }

  public data class TL_chatFull_layer131(
    public val can_set_username: Boolean,
    public val has_scheduled: Boolean,
    public val id: Int,
    public val about: String,
    public val participants: TlGen_ChatParticipants,
    public val chat_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>?,
    public val pinned_msg_id: Int?,
    public val folder_id: Int?,
    public val call: TlGen_InputGroupCall?,
    public val ttl_period: Int?,
    public val groupcall_default_join_as: TlGen_Peer?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (chat_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (pinned_msg_id != null) result = result or 64U
        if (can_set_username) result = result or 128U
        if (has_scheduled) result = result or 256U
        if (folder_id != null) result = result or 2048U
        if (call != null) result = result or 4096U
        if (exported_invite != null) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (groupcall_default_join_as != null) result = result or 32768U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(about)
      participants.serializeToStream(stream)
      chat_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      bot_info?.let { TlGen_Vector.serialize(stream, it) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      call?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
      groupcall_default_join_as?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8A1E2983U
    }
  }

  public data class TL_channelFull_layer131(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val can_set_location: Boolean,
    public val has_scheduled: Boolean,
    public val can_view_stats: Boolean,
    public val blocked: Boolean,
    public val id: Int,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val online_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val folder_id: Int?,
    public val linked_chat_id: Int?,
    public val location: TlGen_ChannelLocation?,
    public val slowmode_seconds: Int?,
    public val slowmode_next_send_date: Int?,
    public val stats_dc: Int?,
    public val pts: Int,
    public val call: TlGen_InputGroupCall?,
    public val ttl_period: Int?,
    public val pending_suggestions: List<String>?,
    public val groupcall_default_join_as: TlGen_Peer?,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        if (folder_id != null) result = result or 2048U
        if (stats_dc != null) result = result or 4096U
        if (online_count != null) result = result or 8192U
        if (linked_chat_id != null) result = result or 16384U
        if (location != null) result = result or 32768U
        if (can_set_location) result = result or 65536U
        if (slowmode_seconds != null) result = result or 131072U
        if (slowmode_next_send_date != null) result = result or 262144U
        if (has_scheduled) result = result or 524288U
        if (can_view_stats) result = result or 1048576U
        if (call != null) result = result or 2097152U
        if (blocked) result = result or 4194304U
        if (exported_invite != null) result = result or 8388608U
        if (ttl_period != null) result = result or 16777216U
        if (pending_suggestions != null) result = result or 33554432U
        if (groupcall_default_join_as != null) result = result or 67108864U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      online_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt32(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      linked_chat_id?.let { stream.writeInt32(it) }
      location?.serializeToStream(stream)
      slowmode_seconds?.let { stream.writeInt32(it) }
      slowmode_next_send_date?.let { stream.writeInt32(it) }
      stats_dc?.let { stream.writeInt32(it) }
      stream.writeInt32(pts)
      call?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
      pending_suggestions?.let { TlGen_Vector.serializeString(stream, it) }
      groupcall_default_join_as?.serializeToStream(stream)
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Int,
      public val migrated_from_max_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x548C3F93U
    }
  }

  public data class TL_chatFull_layer132(
    public val can_set_username: Boolean,
    public val has_scheduled: Boolean,
    public val id: Int,
    public val about: String,
    public val participants: TlGen_ChatParticipants,
    public val chat_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>?,
    public val pinned_msg_id: Int?,
    public val folder_id: Int?,
    public val call: TlGen_InputGroupCall?,
    public val ttl_period: Int?,
    public val groupcall_default_join_as: TlGen_Peer?,
    public val theme_emoticon: String?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (chat_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (pinned_msg_id != null) result = result or 64U
        if (can_set_username) result = result or 128U
        if (has_scheduled) result = result or 256U
        if (folder_id != null) result = result or 2048U
        if (call != null) result = result or 4096U
        if (exported_invite != null) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (groupcall_default_join_as != null) result = result or 32768U
        if (theme_emoticon != null) result = result or 65536U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(about)
      participants.serializeToStream(stream)
      chat_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      bot_info?.let { TlGen_Vector.serialize(stream, it) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      call?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
      groupcall_default_join_as?.serializeToStream(stream)
      theme_emoticon?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x49A0A5D9U
    }
  }

  public data class TL_channelFull_layer132(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val can_set_location: Boolean,
    public val has_scheduled: Boolean,
    public val can_view_stats: Boolean,
    public val blocked: Boolean,
    public val id: Int,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val online_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val folder_id: Int?,
    public val linked_chat_id: Int?,
    public val location: TlGen_ChannelLocation?,
    public val slowmode_seconds: Int?,
    public val slowmode_next_send_date: Int?,
    public val stats_dc: Int?,
    public val pts: Int,
    public val call: TlGen_InputGroupCall?,
    public val ttl_period: Int?,
    public val pending_suggestions: List<String>?,
    public val groupcall_default_join_as: TlGen_Peer?,
    public val theme_emoticon: String?,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        if (folder_id != null) result = result or 2048U
        if (stats_dc != null) result = result or 4096U
        if (online_count != null) result = result or 8192U
        if (linked_chat_id != null) result = result or 16384U
        if (location != null) result = result or 32768U
        if (can_set_location) result = result or 65536U
        if (slowmode_seconds != null) result = result or 131072U
        if (slowmode_next_send_date != null) result = result or 262144U
        if (has_scheduled) result = result or 524288U
        if (can_view_stats) result = result or 1048576U
        if (call != null) result = result or 2097152U
        if (blocked) result = result or 4194304U
        if (exported_invite != null) result = result or 8388608U
        if (ttl_period != null) result = result or 16777216U
        if (pending_suggestions != null) result = result or 33554432U
        if (groupcall_default_join_as != null) result = result or 67108864U
        if (theme_emoticon != null) result = result or 134217728U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      online_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt32(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      linked_chat_id?.let { stream.writeInt32(it) }
      location?.serializeToStream(stream)
      slowmode_seconds?.let { stream.writeInt32(it) }
      slowmode_next_send_date?.let { stream.writeInt32(it) }
      stats_dc?.let { stream.writeInt32(it) }
      stream.writeInt32(pts)
      call?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
      pending_suggestions?.let { TlGen_Vector.serializeString(stream, it) }
      groupcall_default_join_as?.serializeToStream(stream)
      theme_emoticon?.let { stream.writeString(it) }
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Int,
      public val migrated_from_max_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x2F532F3CU
    }
  }

  public data class TL_chatFull_layer133(
    public val can_set_username: Boolean,
    public val has_scheduled: Boolean,
    public val id: Long,
    public val about: String,
    public val participants: TlGen_ChatParticipants,
    public val chat_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>?,
    public val pinned_msg_id: Int?,
    public val folder_id: Int?,
    public val call: TlGen_InputGroupCall?,
    public val ttl_period: Int?,
    public val groupcall_default_join_as: TlGen_Peer?,
    public val theme_emoticon: String?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (chat_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (pinned_msg_id != null) result = result or 64U
        if (can_set_username) result = result or 128U
        if (has_scheduled) result = result or 256U
        if (folder_id != null) result = result or 2048U
        if (call != null) result = result or 4096U
        if (exported_invite != null) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (groupcall_default_join_as != null) result = result or 32768U
        if (theme_emoticon != null) result = result or 65536U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeString(about)
      participants.serializeToStream(stream)
      chat_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      bot_info?.let { TlGen_Vector.serialize(stream, it) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      call?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
      groupcall_default_join_as?.serializeToStream(stream)
      theme_emoticon?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x4DBDC099U
    }
  }

  public data class TL_channelFull_layer133(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val can_set_location: Boolean,
    public val has_scheduled: Boolean,
    public val can_view_stats: Boolean,
    public val blocked: Boolean,
    public val id: Long,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val online_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val folder_id: Int?,
    public val linked_chat_id: Long?,
    public val location: TlGen_ChannelLocation?,
    public val slowmode_seconds: Int?,
    public val slowmode_next_send_date: Int?,
    public val stats_dc: Int?,
    public val pts: Int,
    public val call: TlGen_InputGroupCall?,
    public val ttl_period: Int?,
    public val pending_suggestions: List<String>?,
    public val groupcall_default_join_as: TlGen_Peer?,
    public val theme_emoticon: String?,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        if (folder_id != null) result = result or 2048U
        if (stats_dc != null) result = result or 4096U
        if (online_count != null) result = result or 8192U
        if (linked_chat_id != null) result = result or 16384U
        if (location != null) result = result or 32768U
        if (can_set_location) result = result or 65536U
        if (slowmode_seconds != null) result = result or 131072U
        if (slowmode_next_send_date != null) result = result or 262144U
        if (has_scheduled) result = result or 524288U
        if (can_view_stats) result = result or 1048576U
        if (call != null) result = result or 2097152U
        if (blocked) result = result or 4194304U
        if (exported_invite != null) result = result or 8388608U
        if (ttl_period != null) result = result or 16777216U
        if (pending_suggestions != null) result = result or 33554432U
        if (groupcall_default_join_as != null) result = result or 67108864U
        if (theme_emoticon != null) result = result or 134217728U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      online_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt64(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      linked_chat_id?.let { stream.writeInt64(it) }
      location?.serializeToStream(stream)
      slowmode_seconds?.let { stream.writeInt32(it) }
      slowmode_next_send_date?.let { stream.writeInt32(it) }
      stats_dc?.let { stream.writeInt32(it) }
      stream.writeInt32(pts)
      call?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
      pending_suggestions?.let { TlGen_Vector.serializeString(stream, it) }
      groupcall_default_join_as?.serializeToStream(stream)
      theme_emoticon?.let { stream.writeString(it) }
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Long,
      public val migrated_from_max_id: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xE9B27A17U
    }
  }

  public data class TL_chatFull_layer135(
    public val can_set_username: Boolean,
    public val has_scheduled: Boolean,
    public val id: Long,
    public val about: String,
    public val participants: TlGen_ChatParticipants,
    public val chat_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>?,
    public val pinned_msg_id: Int?,
    public val folder_id: Int?,
    public val call: TlGen_InputGroupCall?,
    public val ttl_period: Int?,
    public val groupcall_default_join_as: TlGen_Peer?,
    public val theme_emoticon: String?,
    public val multiflags_17: Multiflags_17?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (chat_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (pinned_msg_id != null) result = result or 64U
        if (can_set_username) result = result or 128U
        if (has_scheduled) result = result or 256U
        if (folder_id != null) result = result or 2048U
        if (call != null) result = result or 4096U
        if (exported_invite != null) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (groupcall_default_join_as != null) result = result or 32768U
        if (theme_emoticon != null) result = result or 65536U
        if (multiflags_17 != null) result = result or 131072U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeString(about)
      participants.serializeToStream(stream)
      chat_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      bot_info?.let { TlGen_Vector.serialize(stream, it) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      call?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
      groupcall_default_join_as?.serializeToStream(stream)
      theme_emoticon?.let { stream.writeString(it) }
      multiflags_17?.let { stream.writeInt32(it.requests_pending) }
      multiflags_17?.let { TlGen_Vector.serializeLong(stream, it.recent_requesters) }
    }

    public data class Multiflags_17(
      public val requests_pending: Int,
      public val recent_requesters: List<Long>,
    )

    public companion object {
      public const val MAGIC: UInt = 0x46A6FFB4U
    }
  }

  public data class TL_channelFull_layer134(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val can_set_location: Boolean,
    public val has_scheduled: Boolean,
    public val can_view_stats: Boolean,
    public val blocked: Boolean,
    public val id: Long,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val online_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val folder_id: Int?,
    public val linked_chat_id: Long?,
    public val location: TlGen_ChannelLocation?,
    public val slowmode_seconds: Int?,
    public val slowmode_next_send_date: Int?,
    public val stats_dc: Int?,
    public val pts: Int,
    public val call: TlGen_InputGroupCall?,
    public val ttl_period: Int?,
    public val pending_suggestions: List<String>?,
    public val groupcall_default_join_as: TlGen_Peer?,
    public val theme_emoticon: String?,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
    public val multiflags_28: Multiflags_28?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        if (folder_id != null) result = result or 2048U
        if (stats_dc != null) result = result or 4096U
        if (online_count != null) result = result or 8192U
        if (linked_chat_id != null) result = result or 16384U
        if (location != null) result = result or 32768U
        if (can_set_location) result = result or 65536U
        if (slowmode_seconds != null) result = result or 131072U
        if (slowmode_next_send_date != null) result = result or 262144U
        if (has_scheduled) result = result or 524288U
        if (can_view_stats) result = result or 1048576U
        if (call != null) result = result or 2097152U
        if (blocked) result = result or 4194304U
        if (exported_invite != null) result = result or 8388608U
        if (ttl_period != null) result = result or 16777216U
        if (pending_suggestions != null) result = result or 33554432U
        if (groupcall_default_join_as != null) result = result or 67108864U
        if (theme_emoticon != null) result = result or 134217728U
        if (multiflags_28 != null) result = result or 268435456U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      online_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt64(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      linked_chat_id?.let { stream.writeInt64(it) }
      location?.serializeToStream(stream)
      slowmode_seconds?.let { stream.writeInt32(it) }
      slowmode_next_send_date?.let { stream.writeInt32(it) }
      stats_dc?.let { stream.writeInt32(it) }
      stream.writeInt32(pts)
      call?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
      pending_suggestions?.let { TlGen_Vector.serializeString(stream, it) }
      groupcall_default_join_as?.serializeToStream(stream)
      theme_emoticon?.let { stream.writeString(it) }
      multiflags_28?.let { stream.writeInt32(it.requests_pending) }
      multiflags_28?.let { TlGen_Vector.serializeLong(stream, it.recent_requesters) }
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Long,
      public val migrated_from_max_id: Int,
    )

    public data class Multiflags_28(
      public val requests_pending: Int,
      public val recent_requesters: List<Long>,
    )

    public companion object {
      public const val MAGIC: UInt = 0x59CFF963U
    }
  }

  public data class TL_channelFull_layer135(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val can_set_location: Boolean,
    public val has_scheduled: Boolean,
    public val can_view_stats: Boolean,
    public val blocked: Boolean,
    public val id: Long,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val online_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val folder_id: Int?,
    public val linked_chat_id: Long?,
    public val location: TlGen_ChannelLocation?,
    public val slowmode_seconds: Int?,
    public val slowmode_next_send_date: Int?,
    public val stats_dc: Int?,
    public val pts: Int,
    public val call: TlGen_InputGroupCall?,
    public val ttl_period: Int?,
    public val pending_suggestions: List<String>?,
    public val groupcall_default_join_as: TlGen_Peer?,
    public val theme_emoticon: String?,
    public val default_send_as: TlGen_Peer?,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
    public val multiflags_28: Multiflags_28?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        if (folder_id != null) result = result or 2048U
        if (stats_dc != null) result = result or 4096U
        if (online_count != null) result = result or 8192U
        if (linked_chat_id != null) result = result or 16384U
        if (location != null) result = result or 32768U
        if (can_set_location) result = result or 65536U
        if (slowmode_seconds != null) result = result or 131072U
        if (slowmode_next_send_date != null) result = result or 262144U
        if (has_scheduled) result = result or 524288U
        if (can_view_stats) result = result or 1048576U
        if (call != null) result = result or 2097152U
        if (blocked) result = result or 4194304U
        if (exported_invite != null) result = result or 8388608U
        if (ttl_period != null) result = result or 16777216U
        if (pending_suggestions != null) result = result or 33554432U
        if (groupcall_default_join_as != null) result = result or 67108864U
        if (theme_emoticon != null) result = result or 134217728U
        if (multiflags_28 != null) result = result or 268435456U
        if (default_send_as != null) result = result or 536870912U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      online_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt64(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      linked_chat_id?.let { stream.writeInt64(it) }
      location?.serializeToStream(stream)
      slowmode_seconds?.let { stream.writeInt32(it) }
      slowmode_next_send_date?.let { stream.writeInt32(it) }
      stats_dc?.let { stream.writeInt32(it) }
      stream.writeInt32(pts)
      call?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
      pending_suggestions?.let { TlGen_Vector.serializeString(stream, it) }
      groupcall_default_join_as?.serializeToStream(stream)
      theme_emoticon?.let { stream.writeString(it) }
      multiflags_28?.let { stream.writeInt32(it.requests_pending) }
      multiflags_28?.let { TlGen_Vector.serializeLong(stream, it.recent_requesters) }
      default_send_as?.serializeToStream(stream)
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Long,
      public val migrated_from_max_id: Int,
    )

    public data class Multiflags_28(
      public val requests_pending: Int,
      public val recent_requesters: List<Long>,
    )

    public companion object {
      public const val MAGIC: UInt = 0x56662E2EU
    }
  }

  public data class TL_chatFull_layer144(
    public val can_set_username: Boolean,
    public val has_scheduled: Boolean,
    public val id: Long,
    public val about: String,
    public val participants: TlGen_ChatParticipants,
    public val chat_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>?,
    public val pinned_msg_id: Int?,
    public val folder_id: Int?,
    public val call: TlGen_InputGroupCall?,
    public val ttl_period: Int?,
    public val groupcall_default_join_as: TlGen_Peer?,
    public val theme_emoticon: String?,
    public val available_reactions: List<String>?,
    public val multiflags_17: Multiflags_17?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (chat_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (pinned_msg_id != null) result = result or 64U
        if (can_set_username) result = result or 128U
        if (has_scheduled) result = result or 256U
        if (folder_id != null) result = result or 2048U
        if (call != null) result = result or 4096U
        if (exported_invite != null) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (groupcall_default_join_as != null) result = result or 32768U
        if (theme_emoticon != null) result = result or 65536U
        if (multiflags_17 != null) result = result or 131072U
        if (available_reactions != null) result = result or 262144U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeString(about)
      participants.serializeToStream(stream)
      chat_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      bot_info?.let { TlGen_Vector.serialize(stream, it) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      call?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
      groupcall_default_join_as?.serializeToStream(stream)
      theme_emoticon?.let { stream.writeString(it) }
      multiflags_17?.let { stream.writeInt32(it.requests_pending) }
      multiflags_17?.let { TlGen_Vector.serializeLong(stream, it.recent_requesters) }
      available_reactions?.let { TlGen_Vector.serializeString(stream, it) }
    }

    public data class Multiflags_17(
      public val requests_pending: Int,
      public val recent_requesters: List<Long>,
    )

    public companion object {
      public const val MAGIC: UInt = 0xD18EE226U
    }
  }

  public data class TL_channelFull_layer139(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val can_set_location: Boolean,
    public val has_scheduled: Boolean,
    public val can_view_stats: Boolean,
    public val blocked: Boolean,
    public val id: Long,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val online_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val folder_id: Int?,
    public val linked_chat_id: Long?,
    public val location: TlGen_ChannelLocation?,
    public val slowmode_seconds: Int?,
    public val slowmode_next_send_date: Int?,
    public val stats_dc: Int?,
    public val pts: Int,
    public val call: TlGen_InputGroupCall?,
    public val ttl_period: Int?,
    public val pending_suggestions: List<String>?,
    public val groupcall_default_join_as: TlGen_Peer?,
    public val theme_emoticon: String?,
    public val default_send_as: TlGen_Peer?,
    public val available_reactions: List<String>?,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
    public val multiflags_28: Multiflags_28?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        if (folder_id != null) result = result or 2048U
        if (stats_dc != null) result = result or 4096U
        if (online_count != null) result = result or 8192U
        if (linked_chat_id != null) result = result or 16384U
        if (location != null) result = result or 32768U
        if (can_set_location) result = result or 65536U
        if (slowmode_seconds != null) result = result or 131072U
        if (slowmode_next_send_date != null) result = result or 262144U
        if (has_scheduled) result = result or 524288U
        if (can_view_stats) result = result or 1048576U
        if (call != null) result = result or 2097152U
        if (blocked) result = result or 4194304U
        if (exported_invite != null) result = result or 8388608U
        if (ttl_period != null) result = result or 16777216U
        if (pending_suggestions != null) result = result or 33554432U
        if (groupcall_default_join_as != null) result = result or 67108864U
        if (theme_emoticon != null) result = result or 134217728U
        if (multiflags_28 != null) result = result or 268435456U
        if (default_send_as != null) result = result or 536870912U
        if (available_reactions != null) result = result or 1073741824U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      online_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt64(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      linked_chat_id?.let { stream.writeInt64(it) }
      location?.serializeToStream(stream)
      slowmode_seconds?.let { stream.writeInt32(it) }
      slowmode_next_send_date?.let { stream.writeInt32(it) }
      stats_dc?.let { stream.writeInt32(it) }
      stream.writeInt32(pts)
      call?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
      pending_suggestions?.let { TlGen_Vector.serializeString(stream, it) }
      groupcall_default_join_as?.serializeToStream(stream)
      theme_emoticon?.let { stream.writeString(it) }
      multiflags_28?.let { stream.writeInt32(it.requests_pending) }
      multiflags_28?.let { TlGen_Vector.serializeLong(stream, it.recent_requesters) }
      default_send_as?.serializeToStream(stream)
      available_reactions?.let { TlGen_Vector.serializeString(stream, it) }
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Long,
      public val migrated_from_max_id: Int,
    )

    public data class Multiflags_28(
      public val requests_pending: Int,
      public val recent_requesters: List<Long>,
    )

    public companion object {
      public const val MAGIC: UInt = 0xE13C3D20U
    }
  }

  public data class TL_channelFull_layer144(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val can_set_location: Boolean,
    public val has_scheduled: Boolean,
    public val can_view_stats: Boolean,
    public val blocked: Boolean,
    public val can_delete_channel: Boolean,
    public val id: Long,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val online_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val folder_id: Int?,
    public val linked_chat_id: Long?,
    public val location: TlGen_ChannelLocation?,
    public val slowmode_seconds: Int?,
    public val slowmode_next_send_date: Int?,
    public val stats_dc: Int?,
    public val pts: Int,
    public val call: TlGen_InputGroupCall?,
    public val ttl_period: Int?,
    public val pending_suggestions: List<String>?,
    public val groupcall_default_join_as: TlGen_Peer?,
    public val theme_emoticon: String?,
    public val default_send_as: TlGen_Peer?,
    public val available_reactions: List<String>?,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
    public val multiflags_28: Multiflags_28?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        if (folder_id != null) result = result or 2048U
        if (stats_dc != null) result = result or 4096U
        if (online_count != null) result = result or 8192U
        if (linked_chat_id != null) result = result or 16384U
        if (location != null) result = result or 32768U
        if (can_set_location) result = result or 65536U
        if (slowmode_seconds != null) result = result or 131072U
        if (slowmode_next_send_date != null) result = result or 262144U
        if (has_scheduled) result = result or 524288U
        if (can_view_stats) result = result or 1048576U
        if (call != null) result = result or 2097152U
        if (blocked) result = result or 4194304U
        if (exported_invite != null) result = result or 8388608U
        if (ttl_period != null) result = result or 16777216U
        if (pending_suggestions != null) result = result or 33554432U
        if (groupcall_default_join_as != null) result = result or 67108864U
        if (theme_emoticon != null) result = result or 134217728U
        if (multiflags_28 != null) result = result or 268435456U
        if (default_send_as != null) result = result or 536870912U
        if (available_reactions != null) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (can_delete_channel) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      online_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt64(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      linked_chat_id?.let { stream.writeInt64(it) }
      location?.serializeToStream(stream)
      slowmode_seconds?.let { stream.writeInt32(it) }
      slowmode_next_send_date?.let { stream.writeInt32(it) }
      stats_dc?.let { stream.writeInt32(it) }
      stream.writeInt32(pts)
      call?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
      pending_suggestions?.let { TlGen_Vector.serializeString(stream, it) }
      groupcall_default_join_as?.serializeToStream(stream)
      theme_emoticon?.let { stream.writeString(it) }
      multiflags_28?.let { stream.writeInt32(it.requests_pending) }
      multiflags_28?.let { TlGen_Vector.serializeLong(stream, it.recent_requesters) }
      default_send_as?.serializeToStream(stream)
      available_reactions?.let { TlGen_Vector.serializeString(stream, it) }
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Long,
      public val migrated_from_max_id: Int,
    )

    public data class Multiflags_28(
      public val requests_pending: Int,
      public val recent_requesters: List<Long>,
    )

    public companion object {
      public const val MAGIC: UInt = 0xEA68A619U
    }
  }

  public data class TL_chatFull_layer177(
    public val can_set_username: Boolean,
    public val has_scheduled: Boolean,
    public val translations_disabled: Boolean,
    public val id: Long,
    public val about: String,
    public val participants: TlGen_ChatParticipants,
    public val chat_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>?,
    public val pinned_msg_id: Int?,
    public val folder_id: Int?,
    public val call: TlGen_InputGroupCall?,
    public val ttl_period: Int?,
    public val groupcall_default_join_as: TlGen_Peer?,
    public val theme_emoticon: String?,
    public val available_reactions: TlGen_ChatReactions?,
    public val multiflags_17: Multiflags_17?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (chat_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (pinned_msg_id != null) result = result or 64U
        if (can_set_username) result = result or 128U
        if (has_scheduled) result = result or 256U
        if (folder_id != null) result = result or 2048U
        if (call != null) result = result or 4096U
        if (exported_invite != null) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (groupcall_default_join_as != null) result = result or 32768U
        if (theme_emoticon != null) result = result or 65536U
        if (multiflags_17 != null) result = result or 131072U
        if (available_reactions != null) result = result or 262144U
        if (translations_disabled) result = result or 524288U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeString(about)
      participants.serializeToStream(stream)
      chat_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      bot_info?.let { TlGen_Vector.serialize(stream, it) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      call?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
      groupcall_default_join_as?.serializeToStream(stream)
      theme_emoticon?.let { stream.writeString(it) }
      multiflags_17?.let { stream.writeInt32(it.requests_pending) }
      multiflags_17?.let { TlGen_Vector.serializeLong(stream, it.recent_requesters) }
      available_reactions?.serializeToStream(stream)
    }

    public data class Multiflags_17(
      public val requests_pending: Int,
      public val recent_requesters: List<Long>,
    )

    public companion object {
      public const val MAGIC: UInt = 0xC9D31138U
    }
  }

  public data class TL_channelFull_layer163(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val can_set_location: Boolean,
    public val has_scheduled: Boolean,
    public val can_view_stats: Boolean,
    public val blocked: Boolean,
    public val can_delete_channel: Boolean,
    public val antispam: Boolean,
    public val participants_hidden: Boolean,
    public val translations_disabled: Boolean,
    public val id: Long,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val online_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val folder_id: Int?,
    public val linked_chat_id: Long?,
    public val location: TlGen_ChannelLocation?,
    public val slowmode_seconds: Int?,
    public val slowmode_next_send_date: Int?,
    public val stats_dc: Int?,
    public val pts: Int,
    public val call: TlGen_InputGroupCall?,
    public val ttl_period: Int?,
    public val pending_suggestions: List<String>?,
    public val groupcall_default_join_as: TlGen_Peer?,
    public val theme_emoticon: String?,
    public val default_send_as: TlGen_Peer?,
    public val available_reactions: TlGen_ChatReactions?,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
    public val multiflags_28: Multiflags_28?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        if (folder_id != null) result = result or 2048U
        if (stats_dc != null) result = result or 4096U
        if (online_count != null) result = result or 8192U
        if (linked_chat_id != null) result = result or 16384U
        if (location != null) result = result or 32768U
        if (can_set_location) result = result or 65536U
        if (slowmode_seconds != null) result = result or 131072U
        if (slowmode_next_send_date != null) result = result or 262144U
        if (has_scheduled) result = result or 524288U
        if (can_view_stats) result = result or 1048576U
        if (call != null) result = result or 2097152U
        if (blocked) result = result or 4194304U
        if (exported_invite != null) result = result or 8388608U
        if (ttl_period != null) result = result or 16777216U
        if (pending_suggestions != null) result = result or 33554432U
        if (groupcall_default_join_as != null) result = result or 67108864U
        if (theme_emoticon != null) result = result or 134217728U
        if (multiflags_28 != null) result = result or 268435456U
        if (default_send_as != null) result = result or 536870912U
        if (available_reactions != null) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (can_delete_channel) result = result or 1U
        if (antispam) result = result or 2U
        if (participants_hidden) result = result or 4U
        if (translations_disabled) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      online_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt64(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      linked_chat_id?.let { stream.writeInt64(it) }
      location?.serializeToStream(stream)
      slowmode_seconds?.let { stream.writeInt32(it) }
      slowmode_next_send_date?.let { stream.writeInt32(it) }
      stats_dc?.let { stream.writeInt32(it) }
      stream.writeInt32(pts)
      call?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
      pending_suggestions?.let { TlGen_Vector.serializeString(stream, it) }
      groupcall_default_join_as?.serializeToStream(stream)
      theme_emoticon?.let { stream.writeString(it) }
      multiflags_28?.let { stream.writeInt32(it.requests_pending) }
      multiflags_28?.let { TlGen_Vector.serializeLong(stream, it.recent_requesters) }
      default_send_as?.serializeToStream(stream)
      available_reactions?.serializeToStream(stream)
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Long,
      public val migrated_from_max_id: Int,
    )

    public data class Multiflags_28(
      public val requests_pending: Int,
      public val recent_requesters: List<Long>,
    )

    public companion object {
      public const val MAGIC: UInt = 0xF2355507U
    }
  }

  public data class TL_channelFull_layer173(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val can_set_location: Boolean,
    public val has_scheduled: Boolean,
    public val can_view_stats: Boolean,
    public val blocked: Boolean,
    public val can_delete_channel: Boolean,
    public val antispam: Boolean,
    public val participants_hidden: Boolean,
    public val translations_disabled: Boolean,
    public val stories_pinned_available: Boolean,
    public val view_forum_as_messages: Boolean,
    public val id: Long,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val online_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val folder_id: Int?,
    public val linked_chat_id: Long?,
    public val location: TlGen_ChannelLocation?,
    public val slowmode_seconds: Int?,
    public val slowmode_next_send_date: Int?,
    public val stats_dc: Int?,
    public val pts: Int,
    public val call: TlGen_InputGroupCall?,
    public val ttl_period: Int?,
    public val pending_suggestions: List<String>?,
    public val groupcall_default_join_as: TlGen_Peer?,
    public val theme_emoticon: String?,
    public val default_send_as: TlGen_Peer?,
    public val available_reactions: TlGen_ChatReactions?,
    public val stories: TlGen_PeerStories?,
    public val wallpaper: TlGen_WallPaper?,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
    public val multiflags_28: Multiflags_28?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        if (folder_id != null) result = result or 2048U
        if (stats_dc != null) result = result or 4096U
        if (online_count != null) result = result or 8192U
        if (linked_chat_id != null) result = result or 16384U
        if (location != null) result = result or 32768U
        if (can_set_location) result = result or 65536U
        if (slowmode_seconds != null) result = result or 131072U
        if (slowmode_next_send_date != null) result = result or 262144U
        if (has_scheduled) result = result or 524288U
        if (can_view_stats) result = result or 1048576U
        if (call != null) result = result or 2097152U
        if (blocked) result = result or 4194304U
        if (exported_invite != null) result = result or 8388608U
        if (ttl_period != null) result = result or 16777216U
        if (pending_suggestions != null) result = result or 33554432U
        if (groupcall_default_join_as != null) result = result or 67108864U
        if (theme_emoticon != null) result = result or 134217728U
        if (multiflags_28 != null) result = result or 268435456U
        if (default_send_as != null) result = result or 536870912U
        if (available_reactions != null) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (can_delete_channel) result = result or 1U
        if (antispam) result = result or 2U
        if (participants_hidden) result = result or 4U
        if (translations_disabled) result = result or 8U
        if (stories != null) result = result or 16U
        if (stories_pinned_available) result = result or 32U
        if (view_forum_as_messages) result = result or 64U
        if (wallpaper != null) result = result or 128U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      online_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt64(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      linked_chat_id?.let { stream.writeInt64(it) }
      location?.serializeToStream(stream)
      slowmode_seconds?.let { stream.writeInt32(it) }
      slowmode_next_send_date?.let { stream.writeInt32(it) }
      stats_dc?.let { stream.writeInt32(it) }
      stream.writeInt32(pts)
      call?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
      pending_suggestions?.let { TlGen_Vector.serializeString(stream, it) }
      groupcall_default_join_as?.serializeToStream(stream)
      theme_emoticon?.let { stream.writeString(it) }
      multiflags_28?.let { stream.writeInt32(it.requests_pending) }
      multiflags_28?.let { TlGen_Vector.serializeLong(stream, it.recent_requesters) }
      default_send_as?.serializeToStream(stream)
      available_reactions?.serializeToStream(stream)
      stories?.serializeToStream(stream)
      wallpaper?.serializeToStream(stream)
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Long,
      public val migrated_from_max_id: Int,
    )

    public data class Multiflags_28(
      public val requests_pending: Int,
      public val recent_requesters: List<Long>,
    )

    public companion object {
      public const val MAGIC: UInt = 0x0F2BCB6FU
    }
  }

  public data class TL_channelFull_layer167(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val can_set_location: Boolean,
    public val has_scheduled: Boolean,
    public val can_view_stats: Boolean,
    public val blocked: Boolean,
    public val can_delete_channel: Boolean,
    public val antispam: Boolean,
    public val participants_hidden: Boolean,
    public val translations_disabled: Boolean,
    public val stories_pinned_available: Boolean,
    public val view_forum_as_messages: Boolean,
    public val id: Long,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val online_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val folder_id: Int?,
    public val linked_chat_id: Long?,
    public val location: TlGen_ChannelLocation?,
    public val slowmode_seconds: Int?,
    public val slowmode_next_send_date: Int?,
    public val stats_dc: Int?,
    public val pts: Int,
    public val call: TlGen_InputGroupCall?,
    public val ttl_period: Int?,
    public val pending_suggestions: List<String>?,
    public val groupcall_default_join_as: TlGen_Peer?,
    public val theme_emoticon: String?,
    public val default_send_as: TlGen_Peer?,
    public val available_reactions: TlGen_ChatReactions?,
    public val stories: TlGen_PeerStories?,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
    public val multiflags_28: Multiflags_28?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        if (folder_id != null) result = result or 2048U
        if (stats_dc != null) result = result or 4096U
        if (online_count != null) result = result or 8192U
        if (linked_chat_id != null) result = result or 16384U
        if (location != null) result = result or 32768U
        if (can_set_location) result = result or 65536U
        if (slowmode_seconds != null) result = result or 131072U
        if (slowmode_next_send_date != null) result = result or 262144U
        if (has_scheduled) result = result or 524288U
        if (can_view_stats) result = result or 1048576U
        if (call != null) result = result or 2097152U
        if (blocked) result = result or 4194304U
        if (exported_invite != null) result = result or 8388608U
        if (ttl_period != null) result = result or 16777216U
        if (pending_suggestions != null) result = result or 33554432U
        if (groupcall_default_join_as != null) result = result or 67108864U
        if (theme_emoticon != null) result = result or 134217728U
        if (multiflags_28 != null) result = result or 268435456U
        if (default_send_as != null) result = result or 536870912U
        if (available_reactions != null) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (can_delete_channel) result = result or 1U
        if (antispam) result = result or 2U
        if (participants_hidden) result = result or 4U
        if (translations_disabled) result = result or 8U
        if (stories != null) result = result or 16U
        if (stories_pinned_available) result = result or 32U
        if (view_forum_as_messages) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      online_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt64(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      linked_chat_id?.let { stream.writeInt64(it) }
      location?.serializeToStream(stream)
      slowmode_seconds?.let { stream.writeInt32(it) }
      slowmode_next_send_date?.let { stream.writeInt32(it) }
      stats_dc?.let { stream.writeInt32(it) }
      stream.writeInt32(pts)
      call?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
      pending_suggestions?.let { TlGen_Vector.serializeString(stream, it) }
      groupcall_default_join_as?.serializeToStream(stream)
      theme_emoticon?.let { stream.writeString(it) }
      multiflags_28?.let { stream.writeInt32(it.requests_pending) }
      multiflags_28?.let { TlGen_Vector.serializeLong(stream, it.recent_requesters) }
      default_send_as?.serializeToStream(stream)
      available_reactions?.serializeToStream(stream)
      stories?.serializeToStream(stream)
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Long,
      public val migrated_from_max_id: Int,
    )

    public data class Multiflags_28(
      public val requests_pending: Int,
      public val recent_requesters: List<Long>,
    )

    public companion object {
      public const val MAGIC: UInt = 0x723027BDU
    }
  }

  public data class TL_channelFull_layer177(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val can_set_location: Boolean,
    public val has_scheduled: Boolean,
    public val can_view_stats: Boolean,
    public val blocked: Boolean,
    public val can_delete_channel: Boolean,
    public val antispam: Boolean,
    public val participants_hidden: Boolean,
    public val translations_disabled: Boolean,
    public val stories_pinned_available: Boolean,
    public val view_forum_as_messages: Boolean,
    public val restricted_sponsored: Boolean,
    public val can_view_revenue: Boolean,
    public val id: Long,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val online_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val folder_id: Int?,
    public val linked_chat_id: Long?,
    public val location: TlGen_ChannelLocation?,
    public val slowmode_seconds: Int?,
    public val slowmode_next_send_date: Int?,
    public val stats_dc: Int?,
    public val pts: Int,
    public val call: TlGen_InputGroupCall?,
    public val ttl_period: Int?,
    public val pending_suggestions: List<String>?,
    public val groupcall_default_join_as: TlGen_Peer?,
    public val theme_emoticon: String?,
    public val default_send_as: TlGen_Peer?,
    public val available_reactions: TlGen_ChatReactions?,
    public val stories: TlGen_PeerStories?,
    public val wallpaper: TlGen_WallPaper?,
    public val boosts_applied: Int?,
    public val boosts_unrestrict: Int?,
    public val emojiset: TlGen_StickerSet?,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
    public val multiflags_28: Multiflags_28?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        if (folder_id != null) result = result or 2048U
        if (stats_dc != null) result = result or 4096U
        if (online_count != null) result = result or 8192U
        if (linked_chat_id != null) result = result or 16384U
        if (location != null) result = result or 32768U
        if (can_set_location) result = result or 65536U
        if (slowmode_seconds != null) result = result or 131072U
        if (slowmode_next_send_date != null) result = result or 262144U
        if (has_scheduled) result = result or 524288U
        if (can_view_stats) result = result or 1048576U
        if (call != null) result = result or 2097152U
        if (blocked) result = result or 4194304U
        if (exported_invite != null) result = result or 8388608U
        if (ttl_period != null) result = result or 16777216U
        if (pending_suggestions != null) result = result or 33554432U
        if (groupcall_default_join_as != null) result = result or 67108864U
        if (theme_emoticon != null) result = result or 134217728U
        if (multiflags_28 != null) result = result or 268435456U
        if (default_send_as != null) result = result or 536870912U
        if (available_reactions != null) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (can_delete_channel) result = result or 1U
        if (antispam) result = result or 2U
        if (participants_hidden) result = result or 4U
        if (translations_disabled) result = result or 8U
        if (stories != null) result = result or 16U
        if (stories_pinned_available) result = result or 32U
        if (view_forum_as_messages) result = result or 64U
        if (wallpaper != null) result = result or 128U
        if (boosts_applied != null) result = result or 256U
        if (boosts_unrestrict != null) result = result or 512U
        if (emojiset != null) result = result or 1024U
        if (restricted_sponsored) result = result or 2048U
        if (can_view_revenue) result = result or 4096U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      online_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt64(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      linked_chat_id?.let { stream.writeInt64(it) }
      location?.serializeToStream(stream)
      slowmode_seconds?.let { stream.writeInt32(it) }
      slowmode_next_send_date?.let { stream.writeInt32(it) }
      stats_dc?.let { stream.writeInt32(it) }
      stream.writeInt32(pts)
      call?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
      pending_suggestions?.let { TlGen_Vector.serializeString(stream, it) }
      groupcall_default_join_as?.serializeToStream(stream)
      theme_emoticon?.let { stream.writeString(it) }
      multiflags_28?.let { stream.writeInt32(it.requests_pending) }
      multiflags_28?.let { TlGen_Vector.serializeLong(stream, it.recent_requesters) }
      default_send_as?.serializeToStream(stream)
      available_reactions?.serializeToStream(stream)
      stories?.serializeToStream(stream)
      wallpaper?.serializeToStream(stream)
      boosts_applied?.let { stream.writeInt32(it) }
      boosts_unrestrict?.let { stream.writeInt32(it) }
      emojiset?.serializeToStream(stream)
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Long,
      public val migrated_from_max_id: Int,
    )

    public data class Multiflags_28(
      public val requests_pending: Int,
      public val recent_requesters: List<Long>,
    )

    public companion object {
      public const val MAGIC: UInt = 0x44C054A7U
    }
  }

  public data class TL_channelFull_layer195(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val can_set_location: Boolean,
    public val has_scheduled: Boolean,
    public val can_view_stats: Boolean,
    public val blocked: Boolean,
    public val can_delete_channel: Boolean,
    public val antispam: Boolean,
    public val participants_hidden: Boolean,
    public val translations_disabled: Boolean,
    public val stories_pinned_available: Boolean,
    public val view_forum_as_messages: Boolean,
    public val restricted_sponsored: Boolean,
    public val can_view_revenue: Boolean,
    public val paid_media_allowed: Boolean,
    public val can_view_stars_revenue: Boolean,
    public val paid_reactions_available: Boolean,
    public val id: Long,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val online_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val folder_id: Int?,
    public val linked_chat_id: Long?,
    public val location: TlGen_ChannelLocation?,
    public val slowmode_seconds: Int?,
    public val slowmode_next_send_date: Int?,
    public val stats_dc: Int?,
    public val pts: Int,
    public val call: TlGen_InputGroupCall?,
    public val ttl_period: Int?,
    public val pending_suggestions: List<String>?,
    public val groupcall_default_join_as: TlGen_Peer?,
    public val theme_emoticon: String?,
    public val default_send_as: TlGen_Peer?,
    public val available_reactions: TlGen_ChatReactions?,
    public val reactions_limit: Int?,
    public val stories: TlGen_PeerStories?,
    public val wallpaper: TlGen_WallPaper?,
    public val boosts_applied: Int?,
    public val boosts_unrestrict: Int?,
    public val emojiset: TlGen_StickerSet?,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
    public val multiflags_28: Multiflags_28?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        if (folder_id != null) result = result or 2048U
        if (stats_dc != null) result = result or 4096U
        if (online_count != null) result = result or 8192U
        if (linked_chat_id != null) result = result or 16384U
        if (location != null) result = result or 32768U
        if (can_set_location) result = result or 65536U
        if (slowmode_seconds != null) result = result or 131072U
        if (slowmode_next_send_date != null) result = result or 262144U
        if (has_scheduled) result = result or 524288U
        if (can_view_stats) result = result or 1048576U
        if (call != null) result = result or 2097152U
        if (blocked) result = result or 4194304U
        if (exported_invite != null) result = result or 8388608U
        if (ttl_period != null) result = result or 16777216U
        if (pending_suggestions != null) result = result or 33554432U
        if (groupcall_default_join_as != null) result = result or 67108864U
        if (theme_emoticon != null) result = result or 134217728U
        if (multiflags_28 != null) result = result or 268435456U
        if (default_send_as != null) result = result or 536870912U
        if (available_reactions != null) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (can_delete_channel) result = result or 1U
        if (antispam) result = result or 2U
        if (participants_hidden) result = result or 4U
        if (translations_disabled) result = result or 8U
        if (stories != null) result = result or 16U
        if (stories_pinned_available) result = result or 32U
        if (view_forum_as_messages) result = result or 64U
        if (wallpaper != null) result = result or 128U
        if (boosts_applied != null) result = result or 256U
        if (boosts_unrestrict != null) result = result or 512U
        if (emojiset != null) result = result or 1024U
        if (restricted_sponsored) result = result or 2048U
        if (can_view_revenue) result = result or 4096U
        if (reactions_limit != null) result = result or 8192U
        if (paid_media_allowed) result = result or 16384U
        if (can_view_stars_revenue) result = result or 32768U
        if (paid_reactions_available) result = result or 65536U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      online_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt64(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      linked_chat_id?.let { stream.writeInt64(it) }
      location?.serializeToStream(stream)
      slowmode_seconds?.let { stream.writeInt32(it) }
      slowmode_next_send_date?.let { stream.writeInt32(it) }
      stats_dc?.let { stream.writeInt32(it) }
      stream.writeInt32(pts)
      call?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
      pending_suggestions?.let { TlGen_Vector.serializeString(stream, it) }
      groupcall_default_join_as?.serializeToStream(stream)
      theme_emoticon?.let { stream.writeString(it) }
      multiflags_28?.let { stream.writeInt32(it.requests_pending) }
      multiflags_28?.let { TlGen_Vector.serializeLong(stream, it.recent_requesters) }
      default_send_as?.serializeToStream(stream)
      available_reactions?.serializeToStream(stream)
      reactions_limit?.let { stream.writeInt32(it) }
      stories?.serializeToStream(stream)
      wallpaper?.serializeToStream(stream)
      boosts_applied?.let { stream.writeInt32(it) }
      boosts_unrestrict?.let { stream.writeInt32(it) }
      emojiset?.serializeToStream(stream)
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Long,
      public val migrated_from_max_id: Int,
    )

    public data class Multiflags_28(
      public val requests_pending: Int,
      public val recent_requesters: List<Long>,
    )

    public companion object {
      public const val MAGIC: UInt = 0xBBAB348DU
    }
  }

  public data class TL_channelFull_layer197(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val can_set_location: Boolean,
    public val has_scheduled: Boolean,
    public val can_view_stats: Boolean,
    public val blocked: Boolean,
    public val can_delete_channel: Boolean,
    public val antispam: Boolean,
    public val participants_hidden: Boolean,
    public val translations_disabled: Boolean,
    public val stories_pinned_available: Boolean,
    public val view_forum_as_messages: Boolean,
    public val restricted_sponsored: Boolean,
    public val can_view_revenue: Boolean,
    public val paid_media_allowed: Boolean,
    public val can_view_stars_revenue: Boolean,
    public val paid_reactions_available: Boolean,
    public val id: Long,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val online_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val folder_id: Int?,
    public val linked_chat_id: Long?,
    public val location: TlGen_ChannelLocation?,
    public val slowmode_seconds: Int?,
    public val slowmode_next_send_date: Int?,
    public val stats_dc: Int?,
    public val pts: Int,
    public val call: TlGen_InputGroupCall?,
    public val ttl_period: Int?,
    public val pending_suggestions: List<String>?,
    public val groupcall_default_join_as: TlGen_Peer?,
    public val theme_emoticon: String?,
    public val default_send_as: TlGen_Peer?,
    public val available_reactions: TlGen_ChatReactions?,
    public val reactions_limit: Int?,
    public val stories: TlGen_PeerStories?,
    public val wallpaper: TlGen_WallPaper?,
    public val boosts_applied: Int?,
    public val boosts_unrestrict: Int?,
    public val emojiset: TlGen_StickerSet?,
    public val bot_verification: TlGen_BotVerification?,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
    public val multiflags_28: Multiflags_28?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        if (folder_id != null) result = result or 2048U
        if (stats_dc != null) result = result or 4096U
        if (online_count != null) result = result or 8192U
        if (linked_chat_id != null) result = result or 16384U
        if (location != null) result = result or 32768U
        if (can_set_location) result = result or 65536U
        if (slowmode_seconds != null) result = result or 131072U
        if (slowmode_next_send_date != null) result = result or 262144U
        if (has_scheduled) result = result or 524288U
        if (can_view_stats) result = result or 1048576U
        if (call != null) result = result or 2097152U
        if (blocked) result = result or 4194304U
        if (exported_invite != null) result = result or 8388608U
        if (ttl_period != null) result = result or 16777216U
        if (pending_suggestions != null) result = result or 33554432U
        if (groupcall_default_join_as != null) result = result or 67108864U
        if (theme_emoticon != null) result = result or 134217728U
        if (multiflags_28 != null) result = result or 268435456U
        if (default_send_as != null) result = result or 536870912U
        if (available_reactions != null) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (can_delete_channel) result = result or 1U
        if (antispam) result = result or 2U
        if (participants_hidden) result = result or 4U
        if (translations_disabled) result = result or 8U
        if (stories != null) result = result or 16U
        if (stories_pinned_available) result = result or 32U
        if (view_forum_as_messages) result = result or 64U
        if (wallpaper != null) result = result or 128U
        if (boosts_applied != null) result = result or 256U
        if (boosts_unrestrict != null) result = result or 512U
        if (emojiset != null) result = result or 1024U
        if (restricted_sponsored) result = result or 2048U
        if (can_view_revenue) result = result or 4096U
        if (reactions_limit != null) result = result or 8192U
        if (paid_media_allowed) result = result or 16384U
        if (can_view_stars_revenue) result = result or 32768U
        if (paid_reactions_available) result = result or 65536U
        if (bot_verification != null) result = result or 131072U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      online_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt64(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      linked_chat_id?.let { stream.writeInt64(it) }
      location?.serializeToStream(stream)
      slowmode_seconds?.let { stream.writeInt32(it) }
      slowmode_next_send_date?.let { stream.writeInt32(it) }
      stats_dc?.let { stream.writeInt32(it) }
      stream.writeInt32(pts)
      call?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
      pending_suggestions?.let { TlGen_Vector.serializeString(stream, it) }
      groupcall_default_join_as?.serializeToStream(stream)
      theme_emoticon?.let { stream.writeString(it) }
      multiflags_28?.let { stream.writeInt32(it.requests_pending) }
      multiflags_28?.let { TlGen_Vector.serializeLong(stream, it.recent_requesters) }
      default_send_as?.serializeToStream(stream)
      available_reactions?.serializeToStream(stream)
      reactions_limit?.let { stream.writeInt32(it) }
      stories?.serializeToStream(stream)
      wallpaper?.serializeToStream(stream)
      boosts_applied?.let { stream.writeInt32(it) }
      boosts_unrestrict?.let { stream.writeInt32(it) }
      emojiset?.serializeToStream(stream)
      bot_verification?.serializeToStream(stream)
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Long,
      public val migrated_from_max_id: Int,
    )

    public data class Multiflags_28(
      public val requests_pending: Int,
      public val recent_requesters: List<Long>,
    )

    public companion object {
      public const val MAGIC: UInt = 0x9FF3B858U
    }
  }

  public data class TL_channelFull_layer204(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val can_set_location: Boolean,
    public val has_scheduled: Boolean,
    public val can_view_stats: Boolean,
    public val blocked: Boolean,
    public val can_delete_channel: Boolean,
    public val antispam: Boolean,
    public val participants_hidden: Boolean,
    public val translations_disabled: Boolean,
    public val stories_pinned_available: Boolean,
    public val view_forum_as_messages: Boolean,
    public val restricted_sponsored: Boolean,
    public val can_view_revenue: Boolean,
    public val paid_media_allowed: Boolean,
    public val can_view_stars_revenue: Boolean,
    public val paid_reactions_available: Boolean,
    public val stargifts_available: Boolean,
    public val paid_messages_available: Boolean,
    public val id: Long,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val online_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val folder_id: Int?,
    public val linked_chat_id: Long?,
    public val location: TlGen_ChannelLocation?,
    public val slowmode_seconds: Int?,
    public val slowmode_next_send_date: Int?,
    public val stats_dc: Int?,
    public val pts: Int,
    public val call: TlGen_InputGroupCall?,
    public val ttl_period: Int?,
    public val pending_suggestions: List<String>?,
    public val groupcall_default_join_as: TlGen_Peer?,
    public val theme_emoticon: String?,
    public val default_send_as: TlGen_Peer?,
    public val available_reactions: TlGen_ChatReactions?,
    public val reactions_limit: Int?,
    public val stories: TlGen_PeerStories?,
    public val wallpaper: TlGen_WallPaper?,
    public val boosts_applied: Int?,
    public val boosts_unrestrict: Int?,
    public val emojiset: TlGen_StickerSet?,
    public val bot_verification: TlGen_BotVerification?,
    public val stargifts_count: Int?,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
    public val multiflags_28: Multiflags_28?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        if (folder_id != null) result = result or 2048U
        if (stats_dc != null) result = result or 4096U
        if (online_count != null) result = result or 8192U
        if (linked_chat_id != null) result = result or 16384U
        if (location != null) result = result or 32768U
        if (can_set_location) result = result or 65536U
        if (slowmode_seconds != null) result = result or 131072U
        if (slowmode_next_send_date != null) result = result or 262144U
        if (has_scheduled) result = result or 524288U
        if (can_view_stats) result = result or 1048576U
        if (call != null) result = result or 2097152U
        if (blocked) result = result or 4194304U
        if (exported_invite != null) result = result or 8388608U
        if (ttl_period != null) result = result or 16777216U
        if (pending_suggestions != null) result = result or 33554432U
        if (groupcall_default_join_as != null) result = result or 67108864U
        if (theme_emoticon != null) result = result or 134217728U
        if (multiflags_28 != null) result = result or 268435456U
        if (default_send_as != null) result = result or 536870912U
        if (available_reactions != null) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (can_delete_channel) result = result or 1U
        if (antispam) result = result or 2U
        if (participants_hidden) result = result or 4U
        if (translations_disabled) result = result or 8U
        if (stories != null) result = result or 16U
        if (stories_pinned_available) result = result or 32U
        if (view_forum_as_messages) result = result or 64U
        if (wallpaper != null) result = result or 128U
        if (boosts_applied != null) result = result or 256U
        if (boosts_unrestrict != null) result = result or 512U
        if (emojiset != null) result = result or 1024U
        if (restricted_sponsored) result = result or 2048U
        if (can_view_revenue) result = result or 4096U
        if (reactions_limit != null) result = result or 8192U
        if (paid_media_allowed) result = result or 16384U
        if (can_view_stars_revenue) result = result or 32768U
        if (paid_reactions_available) result = result or 65536U
        if (bot_verification != null) result = result or 131072U
        if (stargifts_count != null) result = result or 262144U
        if (stargifts_available) result = result or 524288U
        if (paid_messages_available) result = result or 1048576U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      online_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt64(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      linked_chat_id?.let { stream.writeInt64(it) }
      location?.serializeToStream(stream)
      slowmode_seconds?.let { stream.writeInt32(it) }
      slowmode_next_send_date?.let { stream.writeInt32(it) }
      stats_dc?.let { stream.writeInt32(it) }
      stream.writeInt32(pts)
      call?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
      pending_suggestions?.let { TlGen_Vector.serializeString(stream, it) }
      groupcall_default_join_as?.serializeToStream(stream)
      theme_emoticon?.let { stream.writeString(it) }
      multiflags_28?.let { stream.writeInt32(it.requests_pending) }
      multiflags_28?.let { TlGen_Vector.serializeLong(stream, it.recent_requesters) }
      default_send_as?.serializeToStream(stream)
      available_reactions?.serializeToStream(stream)
      reactions_limit?.let { stream.writeInt32(it) }
      stories?.serializeToStream(stream)
      wallpaper?.serializeToStream(stream)
      boosts_applied?.let { stream.writeInt32(it) }
      boosts_unrestrict?.let { stream.writeInt32(it) }
      emojiset?.serializeToStream(stream)
      bot_verification?.serializeToStream(stream)
      stargifts_count?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Long,
      public val migrated_from_max_id: Int,
    )

    public data class Multiflags_28(
      public val requests_pending: Int,
      public val recent_requesters: List<Long>,
    )

    public companion object {
      public const val MAGIC: UInt = 0x52D6806BU
    }
  }

  public data class TL_channelFull_layer212(
    public val can_view_participants: Boolean,
    public val can_set_username: Boolean,
    public val can_set_stickers: Boolean,
    public val hidden_prehistory: Boolean,
    public val can_set_location: Boolean,
    public val has_scheduled: Boolean,
    public val can_view_stats: Boolean,
    public val blocked: Boolean,
    public val can_delete_channel: Boolean,
    public val antispam: Boolean,
    public val participants_hidden: Boolean,
    public val translations_disabled: Boolean,
    public val stories_pinned_available: Boolean,
    public val view_forum_as_messages: Boolean,
    public val restricted_sponsored: Boolean,
    public val can_view_revenue: Boolean,
    public val paid_media_allowed: Boolean,
    public val can_view_stars_revenue: Boolean,
    public val paid_reactions_available: Boolean,
    public val stargifts_available: Boolean,
    public val paid_messages_available: Boolean,
    public val id: Long,
    public val about: String,
    public val participants_count: Int?,
    public val admins_count: Int?,
    public val online_count: Int?,
    public val read_inbox_max_id: Int,
    public val read_outbox_max_id: Int,
    public val unread_count: Int,
    public val chat_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val exported_invite: TlGen_ExportedChatInvite?,
    public val bot_info: List<TlGen_BotInfo>,
    public val pinned_msg_id: Int?,
    public val stickerset: TlGen_StickerSet?,
    public val available_min_id: Int?,
    public val folder_id: Int?,
    public val linked_chat_id: Long?,
    public val location: TlGen_ChannelLocation?,
    public val slowmode_seconds: Int?,
    public val slowmode_next_send_date: Int?,
    public val stats_dc: Int?,
    public val pts: Int,
    public val call: TlGen_InputGroupCall?,
    public val ttl_period: Int?,
    public val pending_suggestions: List<String>?,
    public val groupcall_default_join_as: TlGen_Peer?,
    public val theme_emoticon: String?,
    public val default_send_as: TlGen_Peer?,
    public val available_reactions: TlGen_ChatReactions?,
    public val reactions_limit: Int?,
    public val stories: TlGen_PeerStories?,
    public val wallpaper: TlGen_WallPaper?,
    public val boosts_applied: Int?,
    public val boosts_unrestrict: Int?,
    public val emojiset: TlGen_StickerSet?,
    public val bot_verification: TlGen_BotVerification?,
    public val stargifts_count: Int?,
    public val send_paid_messages_stars: Long?,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_4: Multiflags_4?,
    public val multiflags_28: Multiflags_28?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (participants_count != null) result = result or 1U
        if (admins_count != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (can_view_participants) result = result or 8U
        if (multiflags_4 != null) result = result or 16U
        if (pinned_msg_id != null) result = result or 32U
        if (can_set_username) result = result or 64U
        if (can_set_stickers) result = result or 128U
        if (stickerset != null) result = result or 256U
        if (available_min_id != null) result = result or 512U
        if (hidden_prehistory) result = result or 1024U
        if (folder_id != null) result = result or 2048U
        if (stats_dc != null) result = result or 4096U
        if (online_count != null) result = result or 8192U
        if (linked_chat_id != null) result = result or 16384U
        if (location != null) result = result or 32768U
        if (can_set_location) result = result or 65536U
        if (slowmode_seconds != null) result = result or 131072U
        if (slowmode_next_send_date != null) result = result or 262144U
        if (has_scheduled) result = result or 524288U
        if (can_view_stats) result = result or 1048576U
        if (call != null) result = result or 2097152U
        if (blocked) result = result or 4194304U
        if (exported_invite != null) result = result or 8388608U
        if (ttl_period != null) result = result or 16777216U
        if (pending_suggestions != null) result = result or 33554432U
        if (groupcall_default_join_as != null) result = result or 67108864U
        if (theme_emoticon != null) result = result or 134217728U
        if (multiflags_28 != null) result = result or 268435456U
        if (default_send_as != null) result = result or 536870912U
        if (available_reactions != null) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (can_delete_channel) result = result or 1U
        if (antispam) result = result or 2U
        if (participants_hidden) result = result or 4U
        if (translations_disabled) result = result or 8U
        if (stories != null) result = result or 16U
        if (stories_pinned_available) result = result or 32U
        if (view_forum_as_messages) result = result or 64U
        if (wallpaper != null) result = result or 128U
        if (boosts_applied != null) result = result or 256U
        if (boosts_unrestrict != null) result = result or 512U
        if (emojiset != null) result = result or 1024U
        if (restricted_sponsored) result = result or 2048U
        if (can_view_revenue) result = result or 4096U
        if (reactions_limit != null) result = result or 8192U
        if (paid_media_allowed) result = result or 16384U
        if (can_view_stars_revenue) result = result or 32768U
        if (paid_reactions_available) result = result or 65536U
        if (bot_verification != null) result = result or 131072U
        if (stargifts_count != null) result = result or 262144U
        if (stargifts_available) result = result or 524288U
        if (paid_messages_available) result = result or 1048576U
        if (send_paid_messages_stars != null) result = result or 2097152U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      stream.writeString(about)
      participants_count?.let { stream.writeInt32(it) }
      admins_count?.let { stream.writeInt32(it) }
      multiflags_2?.let { stream.writeInt32(it.kicked_count) }
      multiflags_2?.let { stream.writeInt32(it.banned_count) }
      online_count?.let { stream.writeInt32(it) }
      stream.writeInt32(read_inbox_max_id)
      stream.writeInt32(read_outbox_max_id)
      stream.writeInt32(unread_count)
      chat_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      exported_invite?.serializeToStream(stream)
      TlGen_Vector.serialize(stream, bot_info)
      multiflags_4?.let { stream.writeInt64(it.migrated_from_chat_id) }
      multiflags_4?.let { stream.writeInt32(it.migrated_from_max_id) }
      pinned_msg_id?.let { stream.writeInt32(it) }
      stickerset?.serializeToStream(stream)
      available_min_id?.let { stream.writeInt32(it) }
      folder_id?.let { stream.writeInt32(it) }
      linked_chat_id?.let { stream.writeInt64(it) }
      location?.serializeToStream(stream)
      slowmode_seconds?.let { stream.writeInt32(it) }
      slowmode_next_send_date?.let { stream.writeInt32(it) }
      stats_dc?.let { stream.writeInt32(it) }
      stream.writeInt32(pts)
      call?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
      pending_suggestions?.let { TlGen_Vector.serializeString(stream, it) }
      groupcall_default_join_as?.serializeToStream(stream)
      theme_emoticon?.let { stream.writeString(it) }
      multiflags_28?.let { stream.writeInt32(it.requests_pending) }
      multiflags_28?.let { TlGen_Vector.serializeLong(stream, it.recent_requesters) }
      default_send_as?.serializeToStream(stream)
      available_reactions?.serializeToStream(stream)
      reactions_limit?.let { stream.writeInt32(it) }
      stories?.serializeToStream(stream)
      wallpaper?.serializeToStream(stream)
      boosts_applied?.let { stream.writeInt32(it) }
      boosts_unrestrict?.let { stream.writeInt32(it) }
      emojiset?.serializeToStream(stream)
      bot_verification?.serializeToStream(stream)
      stargifts_count?.let { stream.writeInt32(it) }
      send_paid_messages_stars?.let { stream.writeInt64(it) }
    }

    public data class Multiflags_2(
      public val kicked_count: Int,
      public val banned_count: Int,
    )

    public data class Multiflags_4(
      public val migrated_from_chat_id: Long,
      public val migrated_from_max_id: Int,
    )

    public data class Multiflags_28(
      public val requests_pending: Int,
      public val recent_requesters: List<Long>,
    )

    public companion object {
      public const val MAGIC: UInt = 0xE07429DEU
    }
  }
}

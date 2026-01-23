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

public sealed class TlGen_Chat : TlGen_Object {
  public data class TL_chatEmpty(
    public val id: Long,
  ) : TlGen_Chat() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x29562865U
    }
  }

  public data class TL_chat(
    public val creator: Boolean,
    public val left: Boolean,
    public val deactivated: Boolean,
    public val call_active: Boolean,
    public val call_not_empty: Boolean,
    public val noforwards: Boolean,
    public val id: Long,
    public val title: String,
    public val photo: TlGen_ChatPhoto,
    public val participants_count: Int,
    public val date: Int,
    public val version: Int,
    public val migrated_to: TlGen_InputChannel?,
    public val admin_rights: TlGen_ChatAdminRights?,
    public val default_banned_rights: TlGen_ChatBannedRights?,
  ) : TlGen_Chat() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (left) result = result or 4U
        if (deactivated) result = result or 32U
        if (migrated_to != null) result = result or 64U
        if (admin_rights != null) result = result or 16384U
        if (default_banned_rights != null) result = result or 262144U
        if (call_active) result = result or 8388608U
        if (call_not_empty) result = result or 16777216U
        if (noforwards) result = result or 33554432U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeString(title)
      photo.serializeToStream(stream)
      stream.writeInt32(participants_count)
      stream.writeInt32(date)
      stream.writeInt32(version)
      migrated_to?.serializeToStream(stream)
      admin_rights?.serializeToStream(stream)
      default_banned_rights?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x41CBF256U
    }
  }

  public data class TL_chatForbidden(
    public val id: Long,
    public val title: String,
  ) : TlGen_Chat() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeString(title)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6592A1A7U
    }
  }

  public data class TL_channelForbidden(
    public val broadcast: Boolean,
    public val megagroup: Boolean,
    public val id: Long,
    public val access_hash: Long,
    public val title: String,
    public val until_date: Int?,
  ) : TlGen_Chat() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (broadcast) result = result or 32U
        if (megagroup) result = result or 256U
        if (until_date != null) result = result or 65536U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeString(title)
      until_date?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x17D493D5U
    }
  }

  public data class TL_channel(
    public val creator: Boolean,
    public val left: Boolean,
    public val broadcast: Boolean,
    public val verified: Boolean,
    public val megagroup: Boolean,
    public val signatures: Boolean,
    public val min: Boolean,
    public val scam: Boolean,
    public val has_link: Boolean,
    public val has_geo: Boolean,
    public val slowmode_enabled: Boolean,
    public val call_active: Boolean,
    public val call_not_empty: Boolean,
    public val fake: Boolean,
    public val gigagroup: Boolean,
    public val noforwards: Boolean,
    public val join_to_send: Boolean,
    public val join_request: Boolean,
    public val forum: Boolean,
    public val stories_hidden: Boolean,
    public val stories_hidden_min: Boolean,
    public val stories_unavailable: Boolean,
    public val signature_profiles: Boolean,
    public val autotranslation: Boolean,
    public val broadcast_messages_allowed: Boolean,
    public val monoforum: Boolean,
    public val forum_tabs: Boolean,
    public val id: Long,
    public val access_hash: Long?,
    public val title: String,
    public val username: String?,
    public val photo: TlGen_ChatPhoto,
    public val date: Int,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val admin_rights: TlGen_ChatAdminRights?,
    public val banned_rights: TlGen_ChatBannedRights?,
    public val default_banned_rights: TlGen_ChatBannedRights?,
    public val participants_count: Int?,
    public val usernames: List<TlGen_Username>?,
    public val stories_max_id: TlGen_RecentStory?,
    public val color: TlGen_PeerColor?,
    public val profile_color: TlGen_PeerColor?,
    public val emoji_status: TlGen_EmojiStatus?,
    public val level: Int?,
    public val subscription_until_date: Int?,
    public val bot_verification_icon: Long?,
    public val send_paid_messages_stars: Long?,
    public val linked_monoforum_id: Long?,
  ) : TlGen_Chat() {
    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (left) result = result or 4U
        if (broadcast) result = result or 32U
        if (username != null) result = result or 64U
        if (verified) result = result or 128U
        if (megagroup) result = result or 256U
        if (restricted) result = result or 512U
        if (signatures) result = result or 2048U
        if (min) result = result or 4096U
        if (access_hash != null) result = result or 8192U
        if (admin_rights != null) result = result or 16384U
        if (banned_rights != null) result = result or 32768U
        if (participants_count != null) result = result or 131072U
        if (default_banned_rights != null) result = result or 262144U
        if (scam) result = result or 524288U
        if (has_link) result = result or 1048576U
        if (has_geo) result = result or 2097152U
        if (slowmode_enabled) result = result or 4194304U
        if (call_active) result = result or 8388608U
        if (call_not_empty) result = result or 16777216U
        if (fake) result = result or 33554432U
        if (gigagroup) result = result or 67108864U
        if (noforwards) result = result or 134217728U
        if (join_to_send) result = result or 268435456U
        if (join_request) result = result or 536870912U
        if (forum) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (usernames != null) result = result or 1U
        if (stories_hidden) result = result or 2U
        if (stories_hidden_min) result = result or 4U
        if (stories_unavailable) result = result or 8U
        if (stories_max_id != null) result = result or 16U
        if (color != null) result = result or 128U
        if (profile_color != null) result = result or 256U
        if (emoji_status != null) result = result or 512U
        if (level != null) result = result or 1024U
        if (subscription_until_date != null) result = result or 2048U
        if (signature_profiles) result = result or 4096U
        if (bot_verification_icon != null) result = result or 8192U
        if (send_paid_messages_stars != null) result = result or 16384U
        if (autotranslation) result = result or 32768U
        if (broadcast_messages_allowed) result = result or 65536U
        if (monoforum) result = result or 131072U
        if (linked_monoforum_id != null) result = result or 262144U
        if (forum_tabs) result = result or 524288U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      access_hash?.let { stream.writeInt64(it) }
      stream.writeString(title)
      username?.let { stream.writeString(it) }
      photo.serializeToStream(stream)
      stream.writeInt32(date)
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      admin_rights?.serializeToStream(stream)
      banned_rights?.serializeToStream(stream)
      default_banned_rights?.serializeToStream(stream)
      participants_count?.let { stream.writeInt32(it) }
      usernames?.let { TlGen_Vector.serialize(stream, it) }
      stories_max_id?.serializeToStream(stream)
      color?.serializeToStream(stream)
      profile_color?.serializeToStream(stream)
      emoji_status?.serializeToStream(stream)
      level?.let { stream.writeInt32(it) }
      subscription_until_date?.let { stream.writeInt32(it) }
      bot_verification_icon?.let { stream.writeInt64(it) }
      send_paid_messages_stars?.let { stream.writeInt64(it) }
      linked_monoforum_id?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x1C32B11CU
    }
  }

  public data class TL_chatEmpty_layer132(
    public val id: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9BA2D800U
    }
  }

  public data class TL_chat_layer37(
    public val id: Int,
    public val title: String,
    public val photo: TlGen_ChatPhoto,
    public val participants_count: Int,
    public val date: Int,
    public val left: Boolean,
    public val version: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeString(title)
      photo.serializeToStream(stream)
      stream.writeInt32(participants_count)
      stream.writeInt32(date)
      stream.writeBool(left)
      stream.writeInt32(version)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6E9C9BC7U
    }
  }

  public data class TL_chatForbidden_layer37(
    public val id: Int,
    public val title: String,
    public val date: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeString(title)
      stream.writeInt32(date)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFB0CCC41U
    }
  }

  public data class TL_chat_layer40(
    public val creator: Boolean,
    public val kicked: Boolean,
    public val left: Boolean,
    public val admins_enabled: Boolean,
    public val admin: Boolean,
    public val id: Int,
    public val title: String,
    public val photo: TlGen_ChatPhoto,
    public val participants_count: Int,
    public val date: Int,
    public val version: Int,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (kicked) result = result or 2U
        if (left) result = result or 4U
        if (admins_enabled) result = result or 8U
        if (admin) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(title)
      photo.serializeToStream(stream)
      stream.writeInt32(participants_count)
      stream.writeInt32(date)
      stream.writeInt32(version)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7312BC48U
    }
  }

  public data class TL_chatForbidden_layer132(
    public val id: Int,
    public val title: String,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeString(title)
    }

    public companion object {
      public const val MAGIC: UInt = 0x07328BDBU
    }
  }

  public data class TL_channel_layer43(
    public val creator: Boolean,
    public val kicked: Boolean,
    public val left: Boolean,
    public val editor: Boolean,
    public val moderator: Boolean,
    public val broadcast: Boolean,
    public val verified: Boolean,
    public val megagroup: Boolean,
    public val explicit_content: Boolean,
    public val id: Int,
    public val access_hash: Long,
    public val title: String,
    public val username: String?,
    public val photo: TlGen_ChatPhoto,
    public val date: Int,
    public val version: Int,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (kicked) result = result or 2U
        if (left) result = result or 4U
        if (editor) result = result or 8U
        if (moderator) result = result or 16U
        if (broadcast) result = result or 32U
        if (username != null) result = result or 64U
        if (verified) result = result or 128U
        if (megagroup) result = result or 256U
        if (explicit_content) result = result or 512U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeInt64(access_hash)
      stream.writeString(title)
      username?.let { stream.writeString(it) }
      photo.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeInt32(version)
    }

    public companion object {
      public const val MAGIC: UInt = 0x678E9587U
    }
  }

  public data class TL_channelForbidden_layer52(
    public val id: Int,
    public val access_hash: Long,
    public val title: String,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeInt64(access_hash)
      stream.writeString(title)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2D85832CU
    }
  }

  public data class TL_chat_layer92(
    public val creator: Boolean,
    public val kicked: Boolean,
    public val left: Boolean,
    public val admins_enabled: Boolean,
    public val admin: Boolean,
    public val deactivated: Boolean,
    public val id: Int,
    public val title: String,
    public val photo: TlGen_ChatPhoto,
    public val participants_count: Int,
    public val date: Int,
    public val version: Int,
    public val migrated_to: TlGen_InputChannel?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (kicked) result = result or 2U
        if (left) result = result or 4U
        if (admins_enabled) result = result or 8U
        if (admin) result = result or 16U
        if (deactivated) result = result or 32U
        if (migrated_to != null) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(title)
      photo.serializeToStream(stream)
      stream.writeInt32(participants_count)
      stream.writeInt32(date)
      stream.writeInt32(version)
      migrated_to?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD91CDD54U
    }
  }

  public data class TL_channel_layer48(
    public val creator: Boolean,
    public val kicked: Boolean,
    public val left: Boolean,
    public val editor: Boolean,
    public val moderator: Boolean,
    public val broadcast: Boolean,
    public val verified: Boolean,
    public val megagroup: Boolean,
    public val democracy: Boolean,
    public val signatures: Boolean,
    public val id: Int,
    public val access_hash: Long,
    public val title: String,
    public val username: String?,
    public val photo: TlGen_ChatPhoto,
    public val date: Int,
    public val version: Int,
    public val restriction_reason: String?,
  ) : TlGen_Object {
    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (kicked) result = result or 2U
        if (left) result = result or 4U
        if (editor) result = result or 8U
        if (moderator) result = result or 16U
        if (broadcast) result = result or 32U
        if (username != null) result = result or 64U
        if (verified) result = result or 128U
        if (megagroup) result = result or 256U
        if (restricted) result = result or 512U
        if (democracy) result = result or 1024U
        if (signatures) result = result or 2048U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeInt64(access_hash)
      stream.writeString(title)
      username?.let { stream.writeString(it) }
      photo.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeInt32(version)
      restriction_reason?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x4B1B7506U
    }
  }

  public data class TL_channel_layer67(
    public val creator: Boolean,
    public val kicked: Boolean,
    public val left: Boolean,
    public val editor: Boolean,
    public val moderator: Boolean,
    public val broadcast: Boolean,
    public val verified: Boolean,
    public val megagroup: Boolean,
    public val democracy: Boolean,
    public val signatures: Boolean,
    public val min: Boolean,
    public val id: Int,
    public val access_hash: Long?,
    public val title: String,
    public val username: String?,
    public val photo: TlGen_ChatPhoto,
    public val date: Int,
    public val version: Int,
    public val restriction_reason: String?,
  ) : TlGen_Object {
    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (kicked) result = result or 2U
        if (left) result = result or 4U
        if (editor) result = result or 8U
        if (moderator) result = result or 16U
        if (broadcast) result = result or 32U
        if (username != null) result = result or 64U
        if (verified) result = result or 128U
        if (megagroup) result = result or 256U
        if (restricted) result = result or 512U
        if (democracy) result = result or 1024U
        if (signatures) result = result or 2048U
        if (min) result = result or 4096U
        if (access_hash != null) result = result or 8192U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      access_hash?.let { stream.writeInt64(it) }
      stream.writeString(title)
      username?.let { stream.writeString(it) }
      photo.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeInt32(version)
      restriction_reason?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xA14DCA52U
    }
  }

  public data class TL_channelForbidden_layer67(
    public val broadcast: Boolean,
    public val megagroup: Boolean,
    public val id: Int,
    public val access_hash: Long,
    public val title: String,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (broadcast) result = result or 32U
        if (megagroup) result = result or 256U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeInt64(access_hash)
      stream.writeString(title)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8537784FU
    }
  }

  public data class TL_channel_layer72(
    public val creator: Boolean,
    public val left: Boolean,
    public val editor: Boolean,
    public val broadcast: Boolean,
    public val verified: Boolean,
    public val megagroup: Boolean,
    public val democracy: Boolean,
    public val signatures: Boolean,
    public val min: Boolean,
    public val id: Int,
    public val access_hash: Long?,
    public val title: String,
    public val username: String?,
    public val photo: TlGen_ChatPhoto,
    public val date: Int,
    public val version: Int,
    public val restriction_reason: String?,
    public val admin_rights: TlGen_ChannelAdminRights?,
    public val banned_rights: TlGen_ChannelBannedRights?,
  ) : TlGen_Object {
    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (left) result = result or 4U
        if (editor) result = result or 8U
        if (broadcast) result = result or 32U
        if (username != null) result = result or 64U
        if (verified) result = result or 128U
        if (megagroup) result = result or 256U
        if (restricted) result = result or 512U
        if (democracy) result = result or 1024U
        if (signatures) result = result or 2048U
        if (min) result = result or 4096U
        if (access_hash != null) result = result or 8192U
        if (admin_rights != null) result = result or 16384U
        if (banned_rights != null) result = result or 32768U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      access_hash?.let { stream.writeInt64(it) }
      stream.writeString(title)
      username?.let { stream.writeString(it) }
      photo.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeInt32(version)
      restriction_reason?.let { stream.writeString(it) }
      admin_rights?.serializeToStream(stream)
      banned_rights?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0CB44B1CU
    }
  }

  public data class TL_channelForbidden_layer132(
    public val broadcast: Boolean,
    public val megagroup: Boolean,
    public val id: Int,
    public val access_hash: Long,
    public val title: String,
    public val until_date: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (broadcast) result = result or 32U
        if (megagroup) result = result or 256U
        if (until_date != null) result = result or 65536U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeInt64(access_hash)
      stream.writeString(title)
      until_date?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x289DA732U
    }
  }

  public data class TL_channel_layer76(
    public val creator: Boolean,
    public val left: Boolean,
    public val editor: Boolean,
    public val broadcast: Boolean,
    public val verified: Boolean,
    public val megagroup: Boolean,
    public val democracy: Boolean,
    public val signatures: Boolean,
    public val min: Boolean,
    public val id: Int,
    public val access_hash: Long?,
    public val title: String,
    public val username: String?,
    public val photo: TlGen_ChatPhoto,
    public val date: Int,
    public val version: Int,
    public val restriction_reason: String?,
    public val admin_rights: TlGen_ChannelAdminRights?,
    public val banned_rights: TlGen_ChannelBannedRights?,
    public val participants_count: Int?,
  ) : TlGen_Object {
    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (left) result = result or 4U
        if (editor) result = result or 8U
        if (broadcast) result = result or 32U
        if (username != null) result = result or 64U
        if (verified) result = result or 128U
        if (megagroup) result = result or 256U
        if (restricted) result = result or 512U
        if (democracy) result = result or 1024U
        if (signatures) result = result or 2048U
        if (min) result = result or 4096U
        if (access_hash != null) result = result or 8192U
        if (admin_rights != null) result = result or 16384U
        if (banned_rights != null) result = result or 32768U
        if (participants_count != null) result = result or 131072U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      access_hash?.let { stream.writeInt64(it) }
      stream.writeString(title)
      username?.let { stream.writeString(it) }
      photo.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeInt32(version)
      restriction_reason?.let { stream.writeString(it) }
      admin_rights?.serializeToStream(stream)
      banned_rights?.serializeToStream(stream)
      participants_count?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x450B7115U
    }
  }

  public data class TL_channel_layer92(
    public val creator: Boolean,
    public val left: Boolean,
    public val editor: Boolean,
    public val broadcast: Boolean,
    public val verified: Boolean,
    public val megagroup: Boolean,
    public val democracy: Boolean,
    public val signatures: Boolean,
    public val min: Boolean,
    public val id: Int,
    public val access_hash: Long?,
    public val title: String,
    public val username: String?,
    public val photo: TlGen_ChatPhoto,
    public val date: Int,
    public val version: Int,
    public val restriction_reason: String?,
    public val admin_rights: TlGen_ChannelAdminRights?,
    public val banned_rights: TlGen_ChannelBannedRights?,
    public val participants_count: Int?,
  ) : TlGen_Object {
    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (left) result = result or 4U
        if (editor) result = result or 8U
        if (broadcast) result = result or 32U
        if (username != null) result = result or 64U
        if (verified) result = result or 128U
        if (megagroup) result = result or 256U
        if (restricted) result = result or 512U
        if (democracy) result = result or 1024U
        if (signatures) result = result or 2048U
        if (min) result = result or 4096U
        if (access_hash != null) result = result or 8192U
        if (admin_rights != null) result = result or 16384U
        if (banned_rights != null) result = result or 32768U
        if (participants_count != null) result = result or 131072U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      access_hash?.let { stream.writeInt64(it) }
      stream.writeString(title)
      username?.let { stream.writeString(it) }
      photo.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeInt32(version)
      restriction_reason?.let { stream.writeString(it) }
      admin_rights?.serializeToStream(stream)
      banned_rights?.serializeToStream(stream)
      participants_count?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xC88974ACU
    }
  }

  public data class TL_chat_layer132(
    public val creator: Boolean,
    public val kicked: Boolean,
    public val left: Boolean,
    public val deactivated: Boolean,
    public val call_active: Boolean,
    public val call_not_empty: Boolean,
    public val id: Int,
    public val title: String,
    public val photo: TlGen_ChatPhoto,
    public val participants_count: Int,
    public val date: Int,
    public val version: Int,
    public val migrated_to: TlGen_InputChannel?,
    public val admin_rights: TlGen_ChatAdminRights?,
    public val default_banned_rights: TlGen_ChatBannedRights?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (kicked) result = result or 2U
        if (left) result = result or 4U
        if (deactivated) result = result or 32U
        if (migrated_to != null) result = result or 64U
        if (admin_rights != null) result = result or 16384U
        if (default_banned_rights != null) result = result or 262144U
        if (call_active) result = result or 8388608U
        if (call_not_empty) result = result or 16777216U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(title)
      photo.serializeToStream(stream)
      stream.writeInt32(participants_count)
      stream.writeInt32(date)
      stream.writeInt32(version)
      migrated_to?.serializeToStream(stream)
      admin_rights?.serializeToStream(stream)
      default_banned_rights?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3BDA1BDEU
    }
  }

  public data class TL_channel_layer104(
    public val creator: Boolean,
    public val left: Boolean,
    public val broadcast: Boolean,
    public val verified: Boolean,
    public val megagroup: Boolean,
    public val signatures: Boolean,
    public val min: Boolean,
    public val scam: Boolean,
    public val has_link: Boolean,
    public val has_geo: Boolean,
    public val slowmode_enabled: Boolean,
    public val id: Int,
    public val access_hash: Long?,
    public val title: String,
    public val username: String?,
    public val photo: TlGen_ChatPhoto,
    public val date: Int,
    public val version: Int,
    public val restriction_reason: String?,
    public val admin_rights: TlGen_ChatAdminRights?,
    public val banned_rights: TlGen_ChatBannedRights?,
    public val default_banned_rights: TlGen_ChatBannedRights?,
    public val participants_count: Int?,
  ) : TlGen_Object {
    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (left) result = result or 4U
        if (broadcast) result = result or 32U
        if (username != null) result = result or 64U
        if (verified) result = result or 128U
        if (megagroup) result = result or 256U
        if (restricted) result = result or 512U
        if (signatures) result = result or 2048U
        if (min) result = result or 4096U
        if (access_hash != null) result = result or 8192U
        if (admin_rights != null) result = result or 16384U
        if (banned_rights != null) result = result or 32768U
        if (participants_count != null) result = result or 131072U
        if (default_banned_rights != null) result = result or 262144U
        if (scam) result = result or 524288U
        if (has_link) result = result or 1048576U
        if (has_geo) result = result or 2097152U
        if (slowmode_enabled) result = result or 4194304U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      access_hash?.let { stream.writeInt64(it) }
      stream.writeString(title)
      username?.let { stream.writeString(it) }
      photo.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeInt32(version)
      restriction_reason?.let { stream.writeString(it) }
      admin_rights?.serializeToStream(stream)
      banned_rights?.serializeToStream(stream)
      default_banned_rights?.serializeToStream(stream)
      participants_count?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x4DF30834U
    }
  }

  public data class TL_channel_layer132(
    public val creator: Boolean,
    public val left: Boolean,
    public val broadcast: Boolean,
    public val verified: Boolean,
    public val megagroup: Boolean,
    public val signatures: Boolean,
    public val min: Boolean,
    public val scam: Boolean,
    public val has_link: Boolean,
    public val has_geo: Boolean,
    public val slowmode_enabled: Boolean,
    public val call_active: Boolean,
    public val call_not_empty: Boolean,
    public val fake: Boolean,
    public val gigagroup: Boolean,
    public val id: Int,
    public val access_hash: Long?,
    public val title: String,
    public val username: String?,
    public val photo: TlGen_ChatPhoto,
    public val date: Int,
    public val version: Int,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val admin_rights: TlGen_ChatAdminRights?,
    public val banned_rights: TlGen_ChatBannedRights?,
    public val default_banned_rights: TlGen_ChatBannedRights?,
    public val participants_count: Int?,
  ) : TlGen_Object {
    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (left) result = result or 4U
        if (broadcast) result = result or 32U
        if (username != null) result = result or 64U
        if (verified) result = result or 128U
        if (megagroup) result = result or 256U
        if (restricted) result = result or 512U
        if (signatures) result = result or 2048U
        if (min) result = result or 4096U
        if (access_hash != null) result = result or 8192U
        if (admin_rights != null) result = result or 16384U
        if (banned_rights != null) result = result or 32768U
        if (participants_count != null) result = result or 131072U
        if (default_banned_rights != null) result = result or 262144U
        if (scam) result = result or 524288U
        if (has_link) result = result or 1048576U
        if (has_geo) result = result or 2097152U
        if (slowmode_enabled) result = result or 4194304U
        if (call_active) result = result or 8388608U
        if (call_not_empty) result = result or 16777216U
        if (fake) result = result or 33554432U
        if (gigagroup) result = result or 67108864U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      access_hash?.let { stream.writeInt64(it) }
      stream.writeString(title)
      username?.let { stream.writeString(it) }
      photo.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeInt32(version)
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      admin_rights?.serializeToStream(stream)
      banned_rights?.serializeToStream(stream)
      default_banned_rights?.serializeToStream(stream)
      participants_count?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xD31A961EU
    }
  }

  public data class TL_channel_layer147(
    public val creator: Boolean,
    public val left: Boolean,
    public val broadcast: Boolean,
    public val verified: Boolean,
    public val megagroup: Boolean,
    public val signatures: Boolean,
    public val min: Boolean,
    public val scam: Boolean,
    public val has_link: Boolean,
    public val has_geo: Boolean,
    public val slowmode_enabled: Boolean,
    public val call_active: Boolean,
    public val call_not_empty: Boolean,
    public val fake: Boolean,
    public val gigagroup: Boolean,
    public val noforwards: Boolean,
    public val join_to_send: Boolean,
    public val join_request: Boolean,
    public val id: Long,
    public val access_hash: Long?,
    public val title: String,
    public val username: String?,
    public val photo: TlGen_ChatPhoto,
    public val date: Int,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val admin_rights: TlGen_ChatAdminRights?,
    public val banned_rights: TlGen_ChatBannedRights?,
    public val default_banned_rights: TlGen_ChatBannedRights?,
    public val participants_count: Int?,
  ) : TlGen_Object {
    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (left) result = result or 4U
        if (broadcast) result = result or 32U
        if (username != null) result = result or 64U
        if (verified) result = result or 128U
        if (megagroup) result = result or 256U
        if (restricted) result = result or 512U
        if (signatures) result = result or 2048U
        if (min) result = result or 4096U
        if (access_hash != null) result = result or 8192U
        if (admin_rights != null) result = result or 16384U
        if (banned_rights != null) result = result or 32768U
        if (participants_count != null) result = result or 131072U
        if (default_banned_rights != null) result = result or 262144U
        if (scam) result = result or 524288U
        if (has_link) result = result or 1048576U
        if (has_geo) result = result or 2097152U
        if (slowmode_enabled) result = result or 4194304U
        if (call_active) result = result or 8388608U
        if (call_not_empty) result = result or 16777216U
        if (fake) result = result or 33554432U
        if (gigagroup) result = result or 67108864U
        if (noforwards) result = result or 134217728U
        if (join_to_send) result = result or 268435456U
        if (join_request) result = result or 536870912U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      access_hash?.let { stream.writeInt64(it) }
      stream.writeString(title)
      username?.let { stream.writeString(it) }
      photo.serializeToStream(stream)
      stream.writeInt32(date)
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      admin_rights?.serializeToStream(stream)
      banned_rights?.serializeToStream(stream)
      default_banned_rights?.serializeToStream(stream)
      participants_count?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x8261AC61U
    }
  }

  public data class TL_channel_layer163(
    public val creator: Boolean,
    public val left: Boolean,
    public val broadcast: Boolean,
    public val verified: Boolean,
    public val megagroup: Boolean,
    public val signatures: Boolean,
    public val min: Boolean,
    public val scam: Boolean,
    public val has_link: Boolean,
    public val has_geo: Boolean,
    public val slowmode_enabled: Boolean,
    public val call_active: Boolean,
    public val call_not_empty: Boolean,
    public val fake: Boolean,
    public val gigagroup: Boolean,
    public val noforwards: Boolean,
    public val join_to_send: Boolean,
    public val join_request: Boolean,
    public val forum: Boolean,
    public val id: Long,
    public val access_hash: Long?,
    public val title: String,
    public val username: String?,
    public val photo: TlGen_ChatPhoto,
    public val date: Int,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val admin_rights: TlGen_ChatAdminRights?,
    public val banned_rights: TlGen_ChatBannedRights?,
    public val default_banned_rights: TlGen_ChatBannedRights?,
    public val participants_count: Int?,
    public val usernames: List<TlGen_Username>?,
  ) : TlGen_Object {
    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (left) result = result or 4U
        if (broadcast) result = result or 32U
        if (username != null) result = result or 64U
        if (verified) result = result or 128U
        if (megagroup) result = result or 256U
        if (restricted) result = result or 512U
        if (signatures) result = result or 2048U
        if (min) result = result or 4096U
        if (access_hash != null) result = result or 8192U
        if (admin_rights != null) result = result or 16384U
        if (banned_rights != null) result = result or 32768U
        if (participants_count != null) result = result or 131072U
        if (default_banned_rights != null) result = result or 262144U
        if (scam) result = result or 524288U
        if (has_link) result = result or 1048576U
        if (has_geo) result = result or 2097152U
        if (slowmode_enabled) result = result or 4194304U
        if (call_active) result = result or 8388608U
        if (call_not_empty) result = result or 16777216U
        if (fake) result = result or 33554432U
        if (gigagroup) result = result or 67108864U
        if (noforwards) result = result or 134217728U
        if (join_to_send) result = result or 268435456U
        if (join_request) result = result or 536870912U
        if (forum) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (usernames != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      access_hash?.let { stream.writeInt64(it) }
      stream.writeString(title)
      username?.let { stream.writeString(it) }
      photo.serializeToStream(stream)
      stream.writeInt32(date)
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      admin_rights?.serializeToStream(stream)
      banned_rights?.serializeToStream(stream)
      default_banned_rights?.serializeToStream(stream)
      participants_count?.let { stream.writeInt32(it) }
      usernames?.let { TlGen_Vector.serialize(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x83259464U
    }
  }

  public data class TL_channel_layer185(
    public val creator: Boolean,
    public val left: Boolean,
    public val broadcast: Boolean,
    public val verified: Boolean,
    public val megagroup: Boolean,
    public val signatures: Boolean,
    public val min: Boolean,
    public val scam: Boolean,
    public val has_link: Boolean,
    public val has_geo: Boolean,
    public val slowmode_enabled: Boolean,
    public val call_active: Boolean,
    public val call_not_empty: Boolean,
    public val fake: Boolean,
    public val gigagroup: Boolean,
    public val noforwards: Boolean,
    public val join_to_send: Boolean,
    public val join_request: Boolean,
    public val forum: Boolean,
    public val stories_hidden: Boolean,
    public val stories_hidden_min: Boolean,
    public val stories_unavailable: Boolean,
    public val id: Long,
    public val access_hash: Long?,
    public val title: String,
    public val username: String?,
    public val photo: TlGen_ChatPhoto,
    public val date: Int,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val admin_rights: TlGen_ChatAdminRights?,
    public val banned_rights: TlGen_ChatBannedRights?,
    public val default_banned_rights: TlGen_ChatBannedRights?,
    public val participants_count: Int?,
    public val usernames: List<TlGen_Username>?,
    public val stories_max_id: Int?,
    public val color: TlGen_PeerColor?,
    public val profile_color: TlGen_PeerColor?,
    public val emoji_status: TlGen_EmojiStatus?,
    public val level: Int?,
  ) : TlGen_Object {
    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (left) result = result or 4U
        if (broadcast) result = result or 32U
        if (username != null) result = result or 64U
        if (verified) result = result or 128U
        if (megagroup) result = result or 256U
        if (restricted) result = result or 512U
        if (signatures) result = result or 2048U
        if (min) result = result or 4096U
        if (access_hash != null) result = result or 8192U
        if (admin_rights != null) result = result or 16384U
        if (banned_rights != null) result = result or 32768U
        if (participants_count != null) result = result or 131072U
        if (default_banned_rights != null) result = result or 262144U
        if (scam) result = result or 524288U
        if (has_link) result = result or 1048576U
        if (has_geo) result = result or 2097152U
        if (slowmode_enabled) result = result or 4194304U
        if (call_active) result = result or 8388608U
        if (call_not_empty) result = result or 16777216U
        if (fake) result = result or 33554432U
        if (gigagroup) result = result or 67108864U
        if (noforwards) result = result or 134217728U
        if (join_to_send) result = result or 268435456U
        if (join_request) result = result or 536870912U
        if (forum) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (usernames != null) result = result or 1U
        if (stories_hidden) result = result or 2U
        if (stories_hidden_min) result = result or 4U
        if (stories_unavailable) result = result or 8U
        if (stories_max_id != null) result = result or 16U
        if (color != null) result = result or 128U
        if (profile_color != null) result = result or 256U
        if (emoji_status != null) result = result or 512U
        if (level != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      access_hash?.let { stream.writeInt64(it) }
      stream.writeString(title)
      username?.let { stream.writeString(it) }
      photo.serializeToStream(stream)
      stream.writeInt32(date)
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      admin_rights?.serializeToStream(stream)
      banned_rights?.serializeToStream(stream)
      default_banned_rights?.serializeToStream(stream)
      participants_count?.let { stream.writeInt32(it) }
      usernames?.let { TlGen_Vector.serialize(stream, it) }
      stories_max_id?.let { stream.writeInt32(it) }
      color?.serializeToStream(stream)
      profile_color?.serializeToStream(stream)
      emoji_status?.serializeToStream(stream)
      level?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x0AADFC8FU
    }
  }

  public data class TL_channel_layer165(
    public val creator: Boolean,
    public val left: Boolean,
    public val broadcast: Boolean,
    public val verified: Boolean,
    public val megagroup: Boolean,
    public val signatures: Boolean,
    public val min: Boolean,
    public val scam: Boolean,
    public val has_link: Boolean,
    public val has_geo: Boolean,
    public val slowmode_enabled: Boolean,
    public val call_active: Boolean,
    public val call_not_empty: Boolean,
    public val fake: Boolean,
    public val gigagroup: Boolean,
    public val noforwards: Boolean,
    public val join_to_send: Boolean,
    public val join_request: Boolean,
    public val forum: Boolean,
    public val stories_hidden: Boolean,
    public val stories_hidden_min: Boolean,
    public val stories_unavailable: Boolean,
    public val id: Long,
    public val access_hash: Long?,
    public val title: String,
    public val username: String?,
    public val photo: TlGen_ChatPhoto,
    public val date: Int,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val admin_rights: TlGen_ChatAdminRights?,
    public val banned_rights: TlGen_ChatBannedRights?,
    public val default_banned_rights: TlGen_ChatBannedRights?,
    public val participants_count: Int?,
    public val usernames: List<TlGen_Username>?,
    public val stories_max_id: Int?,
  ) : TlGen_Object {
    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (left) result = result or 4U
        if (broadcast) result = result or 32U
        if (username != null) result = result or 64U
        if (verified) result = result or 128U
        if (megagroup) result = result or 256U
        if (restricted) result = result or 512U
        if (signatures) result = result or 2048U
        if (min) result = result or 4096U
        if (access_hash != null) result = result or 8192U
        if (admin_rights != null) result = result or 16384U
        if (banned_rights != null) result = result or 32768U
        if (participants_count != null) result = result or 131072U
        if (default_banned_rights != null) result = result or 262144U
        if (scam) result = result or 524288U
        if (has_link) result = result or 1048576U
        if (has_geo) result = result or 2097152U
        if (slowmode_enabled) result = result or 4194304U
        if (call_active) result = result or 8388608U
        if (call_not_empty) result = result or 16777216U
        if (fake) result = result or 33554432U
        if (gigagroup) result = result or 67108864U
        if (noforwards) result = result or 134217728U
        if (join_to_send) result = result or 268435456U
        if (join_request) result = result or 536870912U
        if (forum) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (usernames != null) result = result or 1U
        if (stories_hidden) result = result or 2U
        if (stories_hidden_min) result = result or 4U
        if (stories_unavailable) result = result or 8U
        if (stories_max_id != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      access_hash?.let { stream.writeInt64(it) }
      stream.writeString(title)
      username?.let { stream.writeString(it) }
      photo.serializeToStream(stream)
      stream.writeInt32(date)
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      admin_rights?.serializeToStream(stream)
      banned_rights?.serializeToStream(stream)
      default_banned_rights?.serializeToStream(stream)
      participants_count?.let { stream.writeInt32(it) }
      usernames?.let { TlGen_Vector.serialize(stream, it) }
      stories_max_id?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x94F592DBU
    }
  }

  public data class TL_channel_layer166(
    public val creator: Boolean,
    public val left: Boolean,
    public val broadcast: Boolean,
    public val verified: Boolean,
    public val megagroup: Boolean,
    public val signatures: Boolean,
    public val min: Boolean,
    public val scam: Boolean,
    public val has_link: Boolean,
    public val has_geo: Boolean,
    public val slowmode_enabled: Boolean,
    public val call_active: Boolean,
    public val call_not_empty: Boolean,
    public val fake: Boolean,
    public val gigagroup: Boolean,
    public val noforwards: Boolean,
    public val join_to_send: Boolean,
    public val join_request: Boolean,
    public val forum: Boolean,
    public val stories_hidden: Boolean,
    public val stories_hidden_min: Boolean,
    public val stories_unavailable: Boolean,
    public val id: Long,
    public val access_hash: Long?,
    public val title: String,
    public val username: String?,
    public val photo: TlGen_ChatPhoto,
    public val date: Int,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val admin_rights: TlGen_ChatAdminRights?,
    public val banned_rights: TlGen_ChatBannedRights?,
    public val default_banned_rights: TlGen_ChatBannedRights?,
    public val participants_count: Int?,
    public val usernames: List<TlGen_Username>?,
    public val stories_max_id: Int?,
    public val color: Int?,
    public val background_emoji_id: Long?,
  ) : TlGen_Object {
    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (left) result = result or 4U
        if (broadcast) result = result or 32U
        if (username != null) result = result or 64U
        if (verified) result = result or 128U
        if (megagroup) result = result or 256U
        if (restricted) result = result or 512U
        if (signatures) result = result or 2048U
        if (min) result = result or 4096U
        if (access_hash != null) result = result or 8192U
        if (admin_rights != null) result = result or 16384U
        if (banned_rights != null) result = result or 32768U
        if (participants_count != null) result = result or 131072U
        if (default_banned_rights != null) result = result or 262144U
        if (scam) result = result or 524288U
        if (has_link) result = result or 1048576U
        if (has_geo) result = result or 2097152U
        if (slowmode_enabled) result = result or 4194304U
        if (call_active) result = result or 8388608U
        if (call_not_empty) result = result or 16777216U
        if (fake) result = result or 33554432U
        if (gigagroup) result = result or 67108864U
        if (noforwards) result = result or 134217728U
        if (join_to_send) result = result or 268435456U
        if (join_request) result = result or 536870912U
        if (forum) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (usernames != null) result = result or 1U
        if (stories_hidden) result = result or 2U
        if (stories_hidden_min) result = result or 4U
        if (stories_unavailable) result = result or 8U
        if (stories_max_id != null) result = result or 16U
        if (background_emoji_id != null) result = result or 32U
        if (color != null) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      access_hash?.let { stream.writeInt64(it) }
      stream.writeString(title)
      username?.let { stream.writeString(it) }
      photo.serializeToStream(stream)
      stream.writeInt32(date)
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      admin_rights?.serializeToStream(stream)
      banned_rights?.serializeToStream(stream)
      default_banned_rights?.serializeToStream(stream)
      participants_count?.let { stream.writeInt32(it) }
      usernames?.let { TlGen_Vector.serialize(stream, it) }
      stories_max_id?.let { stream.writeInt32(it) }
      color?.let { stream.writeInt32(it) }
      background_emoji_id?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x1981EA7EU
    }
  }

  public data class TL_channel_layer167(
    public val creator: Boolean,
    public val left: Boolean,
    public val broadcast: Boolean,
    public val verified: Boolean,
    public val megagroup: Boolean,
    public val signatures: Boolean,
    public val min: Boolean,
    public val scam: Boolean,
    public val has_link: Boolean,
    public val has_geo: Boolean,
    public val slowmode_enabled: Boolean,
    public val call_active: Boolean,
    public val call_not_empty: Boolean,
    public val fake: Boolean,
    public val gigagroup: Boolean,
    public val noforwards: Boolean,
    public val join_to_send: Boolean,
    public val join_request: Boolean,
    public val forum: Boolean,
    public val stories_hidden: Boolean,
    public val stories_hidden_min: Boolean,
    public val stories_unavailable: Boolean,
    public val id: Long,
    public val access_hash: Long?,
    public val title: String,
    public val username: String?,
    public val photo: TlGen_ChatPhoto,
    public val date: Int,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val admin_rights: TlGen_ChatAdminRights?,
    public val banned_rights: TlGen_ChatBannedRights?,
    public val default_banned_rights: TlGen_ChatBannedRights?,
    public val participants_count: Int?,
    public val usernames: List<TlGen_Username>?,
    public val stories_max_id: Int?,
    public val color: TlGen_PeerColor?,
  ) : TlGen_Object {
    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (left) result = result or 4U
        if (broadcast) result = result or 32U
        if (username != null) result = result or 64U
        if (verified) result = result or 128U
        if (megagroup) result = result or 256U
        if (restricted) result = result or 512U
        if (signatures) result = result or 2048U
        if (min) result = result or 4096U
        if (access_hash != null) result = result or 8192U
        if (admin_rights != null) result = result or 16384U
        if (banned_rights != null) result = result or 32768U
        if (participants_count != null) result = result or 131072U
        if (default_banned_rights != null) result = result or 262144U
        if (scam) result = result or 524288U
        if (has_link) result = result or 1048576U
        if (has_geo) result = result or 2097152U
        if (slowmode_enabled) result = result or 4194304U
        if (call_active) result = result or 8388608U
        if (call_not_empty) result = result or 16777216U
        if (fake) result = result or 33554432U
        if (gigagroup) result = result or 67108864U
        if (noforwards) result = result or 134217728U
        if (join_to_send) result = result or 268435456U
        if (join_request) result = result or 536870912U
        if (forum) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (usernames != null) result = result or 1U
        if (stories_hidden) result = result or 2U
        if (stories_hidden_min) result = result or 4U
        if (stories_unavailable) result = result or 8U
        if (stories_max_id != null) result = result or 16U
        if (color != null) result = result or 128U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      access_hash?.let { stream.writeInt64(it) }
      stream.writeString(title)
      username?.let { stream.writeString(it) }
      photo.serializeToStream(stream)
      stream.writeInt32(date)
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      admin_rights?.serializeToStream(stream)
      banned_rights?.serializeToStream(stream)
      default_banned_rights?.serializeToStream(stream)
      participants_count?.let { stream.writeInt32(it) }
      usernames?.let { TlGen_Vector.serialize(stream, it) }
      stories_max_id?.let { stream.writeInt32(it) }
      color?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8E87CCD8U
    }
  }

  public data class TL_channel_layer195(
    public val creator: Boolean,
    public val left: Boolean,
    public val broadcast: Boolean,
    public val verified: Boolean,
    public val megagroup: Boolean,
    public val signatures: Boolean,
    public val min: Boolean,
    public val scam: Boolean,
    public val has_link: Boolean,
    public val has_geo: Boolean,
    public val slowmode_enabled: Boolean,
    public val call_active: Boolean,
    public val call_not_empty: Boolean,
    public val fake: Boolean,
    public val gigagroup: Boolean,
    public val noforwards: Boolean,
    public val join_to_send: Boolean,
    public val join_request: Boolean,
    public val forum: Boolean,
    public val stories_hidden: Boolean,
    public val stories_hidden_min: Boolean,
    public val stories_unavailable: Boolean,
    public val signature_profiles: Boolean,
    public val id: Long,
    public val access_hash: Long?,
    public val title: String,
    public val username: String?,
    public val photo: TlGen_ChatPhoto,
    public val date: Int,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val admin_rights: TlGen_ChatAdminRights?,
    public val banned_rights: TlGen_ChatBannedRights?,
    public val default_banned_rights: TlGen_ChatBannedRights?,
    public val participants_count: Int?,
    public val usernames: List<TlGen_Username>?,
    public val stories_max_id: Int?,
    public val color: TlGen_PeerColor?,
    public val profile_color: TlGen_PeerColor?,
    public val emoji_status: TlGen_EmojiStatus?,
    public val level: Int?,
    public val subscription_until_date: Int?,
  ) : TlGen_Object {
    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (left) result = result or 4U
        if (broadcast) result = result or 32U
        if (username != null) result = result or 64U
        if (verified) result = result or 128U
        if (megagroup) result = result or 256U
        if (restricted) result = result or 512U
        if (signatures) result = result or 2048U
        if (min) result = result or 4096U
        if (access_hash != null) result = result or 8192U
        if (admin_rights != null) result = result or 16384U
        if (banned_rights != null) result = result or 32768U
        if (participants_count != null) result = result or 131072U
        if (default_banned_rights != null) result = result or 262144U
        if (scam) result = result or 524288U
        if (has_link) result = result or 1048576U
        if (has_geo) result = result or 2097152U
        if (slowmode_enabled) result = result or 4194304U
        if (call_active) result = result or 8388608U
        if (call_not_empty) result = result or 16777216U
        if (fake) result = result or 33554432U
        if (gigagroup) result = result or 67108864U
        if (noforwards) result = result or 134217728U
        if (join_to_send) result = result or 268435456U
        if (join_request) result = result or 536870912U
        if (forum) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (usernames != null) result = result or 1U
        if (stories_hidden) result = result or 2U
        if (stories_hidden_min) result = result or 4U
        if (stories_unavailable) result = result or 8U
        if (stories_max_id != null) result = result or 16U
        if (color != null) result = result or 128U
        if (profile_color != null) result = result or 256U
        if (emoji_status != null) result = result or 512U
        if (level != null) result = result or 1024U
        if (subscription_until_date != null) result = result or 2048U
        if (signature_profiles) result = result or 4096U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      access_hash?.let { stream.writeInt64(it) }
      stream.writeString(title)
      username?.let { stream.writeString(it) }
      photo.serializeToStream(stream)
      stream.writeInt32(date)
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      admin_rights?.serializeToStream(stream)
      banned_rights?.serializeToStream(stream)
      default_banned_rights?.serializeToStream(stream)
      participants_count?.let { stream.writeInt32(it) }
      usernames?.let { TlGen_Vector.serialize(stream, it) }
      stories_max_id?.let { stream.writeInt32(it) }
      color?.serializeToStream(stream)
      profile_color?.serializeToStream(stream)
      emoji_status?.serializeToStream(stream)
      level?.let { stream.writeInt32(it) }
      subscription_until_date?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xFE4478BDU
    }
  }

  public data class TL_channel_layer199(
    public val creator: Boolean,
    public val left: Boolean,
    public val broadcast: Boolean,
    public val verified: Boolean,
    public val megagroup: Boolean,
    public val signatures: Boolean,
    public val min: Boolean,
    public val scam: Boolean,
    public val has_link: Boolean,
    public val has_geo: Boolean,
    public val slowmode_enabled: Boolean,
    public val call_active: Boolean,
    public val call_not_empty: Boolean,
    public val fake: Boolean,
    public val gigagroup: Boolean,
    public val noforwards: Boolean,
    public val join_to_send: Boolean,
    public val join_request: Boolean,
    public val forum: Boolean,
    public val stories_hidden: Boolean,
    public val stories_hidden_min: Boolean,
    public val stories_unavailable: Boolean,
    public val signature_profiles: Boolean,
    public val id: Long,
    public val access_hash: Long?,
    public val title: String,
    public val username: String?,
    public val photo: TlGen_ChatPhoto,
    public val date: Int,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val admin_rights: TlGen_ChatAdminRights?,
    public val banned_rights: TlGen_ChatBannedRights?,
    public val default_banned_rights: TlGen_ChatBannedRights?,
    public val participants_count: Int?,
    public val usernames: List<TlGen_Username>?,
    public val stories_max_id: Int?,
    public val color: TlGen_PeerColor?,
    public val profile_color: TlGen_PeerColor?,
    public val emoji_status: TlGen_EmojiStatus?,
    public val level: Int?,
    public val subscription_until_date: Int?,
    public val bot_verification_icon: Long?,
  ) : TlGen_Object {
    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (left) result = result or 4U
        if (broadcast) result = result or 32U
        if (username != null) result = result or 64U
        if (verified) result = result or 128U
        if (megagroup) result = result or 256U
        if (restricted) result = result or 512U
        if (signatures) result = result or 2048U
        if (min) result = result or 4096U
        if (access_hash != null) result = result or 8192U
        if (admin_rights != null) result = result or 16384U
        if (banned_rights != null) result = result or 32768U
        if (participants_count != null) result = result or 131072U
        if (default_banned_rights != null) result = result or 262144U
        if (scam) result = result or 524288U
        if (has_link) result = result or 1048576U
        if (has_geo) result = result or 2097152U
        if (slowmode_enabled) result = result or 4194304U
        if (call_active) result = result or 8388608U
        if (call_not_empty) result = result or 16777216U
        if (fake) result = result or 33554432U
        if (gigagroup) result = result or 67108864U
        if (noforwards) result = result or 134217728U
        if (join_to_send) result = result or 268435456U
        if (join_request) result = result or 536870912U
        if (forum) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (usernames != null) result = result or 1U
        if (stories_hidden) result = result or 2U
        if (stories_hidden_min) result = result or 4U
        if (stories_unavailable) result = result or 8U
        if (stories_max_id != null) result = result or 16U
        if (color != null) result = result or 128U
        if (profile_color != null) result = result or 256U
        if (emoji_status != null) result = result or 512U
        if (level != null) result = result or 1024U
        if (subscription_until_date != null) result = result or 2048U
        if (signature_profiles) result = result or 4096U
        if (bot_verification_icon != null) result = result or 8192U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      access_hash?.let { stream.writeInt64(it) }
      stream.writeString(title)
      username?.let { stream.writeString(it) }
      photo.serializeToStream(stream)
      stream.writeInt32(date)
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      admin_rights?.serializeToStream(stream)
      banned_rights?.serializeToStream(stream)
      default_banned_rights?.serializeToStream(stream)
      participants_count?.let { stream.writeInt32(it) }
      usernames?.let { TlGen_Vector.serialize(stream, it) }
      stories_max_id?.let { stream.writeInt32(it) }
      color?.serializeToStream(stream)
      profile_color?.serializeToStream(stream)
      emoji_status?.serializeToStream(stream)
      level?.let { stream.writeInt32(it) }
      subscription_until_date?.let { stream.writeInt32(it) }
      bot_verification_icon?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xE00998B7U
    }
  }

  public data class TL_channel_layer203(
    public val creator: Boolean,
    public val left: Boolean,
    public val broadcast: Boolean,
    public val verified: Boolean,
    public val megagroup: Boolean,
    public val signatures: Boolean,
    public val min: Boolean,
    public val scam: Boolean,
    public val has_link: Boolean,
    public val has_geo: Boolean,
    public val slowmode_enabled: Boolean,
    public val call_active: Boolean,
    public val call_not_empty: Boolean,
    public val fake: Boolean,
    public val gigagroup: Boolean,
    public val noforwards: Boolean,
    public val join_to_send: Boolean,
    public val join_request: Boolean,
    public val forum: Boolean,
    public val stories_hidden: Boolean,
    public val stories_hidden_min: Boolean,
    public val stories_unavailable: Boolean,
    public val signature_profiles: Boolean,
    public val autotranslation: Boolean,
    public val id: Long,
    public val access_hash: Long?,
    public val title: String,
    public val username: String?,
    public val photo: TlGen_ChatPhoto,
    public val date: Int,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val admin_rights: TlGen_ChatAdminRights?,
    public val banned_rights: TlGen_ChatBannedRights?,
    public val default_banned_rights: TlGen_ChatBannedRights?,
    public val participants_count: Int?,
    public val usernames: List<TlGen_Username>?,
    public val stories_max_id: Int?,
    public val color: TlGen_PeerColor?,
    public val profile_color: TlGen_PeerColor?,
    public val emoji_status: TlGen_EmojiStatus?,
    public val level: Int?,
    public val subscription_until_date: Int?,
    public val bot_verification_icon: Long?,
    public val send_paid_messages_stars: Long?,
  ) : TlGen_Object {
    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (left) result = result or 4U
        if (broadcast) result = result or 32U
        if (username != null) result = result or 64U
        if (verified) result = result or 128U
        if (megagroup) result = result or 256U
        if (restricted) result = result or 512U
        if (signatures) result = result or 2048U
        if (min) result = result or 4096U
        if (access_hash != null) result = result or 8192U
        if (admin_rights != null) result = result or 16384U
        if (banned_rights != null) result = result or 32768U
        if (participants_count != null) result = result or 131072U
        if (default_banned_rights != null) result = result or 262144U
        if (scam) result = result or 524288U
        if (has_link) result = result or 1048576U
        if (has_geo) result = result or 2097152U
        if (slowmode_enabled) result = result or 4194304U
        if (call_active) result = result or 8388608U
        if (call_not_empty) result = result or 16777216U
        if (fake) result = result or 33554432U
        if (gigagroup) result = result or 67108864U
        if (noforwards) result = result or 134217728U
        if (join_to_send) result = result or 268435456U
        if (join_request) result = result or 536870912U
        if (forum) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (usernames != null) result = result or 1U
        if (stories_hidden) result = result or 2U
        if (stories_hidden_min) result = result or 4U
        if (stories_unavailable) result = result or 8U
        if (stories_max_id != null) result = result or 16U
        if (color != null) result = result or 128U
        if (profile_color != null) result = result or 256U
        if (emoji_status != null) result = result or 512U
        if (level != null) result = result or 1024U
        if (subscription_until_date != null) result = result or 2048U
        if (signature_profiles) result = result or 4096U
        if (bot_verification_icon != null) result = result or 8192U
        if (send_paid_messages_stars != null) result = result or 16384U
        if (autotranslation) result = result or 32768U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      access_hash?.let { stream.writeInt64(it) }
      stream.writeString(title)
      username?.let { stream.writeString(it) }
      photo.serializeToStream(stream)
      stream.writeInt32(date)
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      admin_rights?.serializeToStream(stream)
      banned_rights?.serializeToStream(stream)
      default_banned_rights?.serializeToStream(stream)
      participants_count?.let { stream.writeInt32(it) }
      usernames?.let { TlGen_Vector.serialize(stream, it) }
      stories_max_id?.let { stream.writeInt32(it) }
      color?.serializeToStream(stream)
      profile_color?.serializeToStream(stream)
      emoji_status?.serializeToStream(stream)
      level?.let { stream.writeInt32(it) }
      subscription_until_date?.let { stream.writeInt32(it) }
      bot_verification_icon?.let { stream.writeInt64(it) }
      send_paid_messages_stars?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x7482147EU
    }
  }

  public data class TL_channel_layer216(
    public val creator: Boolean,
    public val left: Boolean,
    public val broadcast: Boolean,
    public val verified: Boolean,
    public val megagroup: Boolean,
    public val signatures: Boolean,
    public val min: Boolean,
    public val scam: Boolean,
    public val has_link: Boolean,
    public val has_geo: Boolean,
    public val slowmode_enabled: Boolean,
    public val call_active: Boolean,
    public val call_not_empty: Boolean,
    public val fake: Boolean,
    public val gigagroup: Boolean,
    public val noforwards: Boolean,
    public val join_to_send: Boolean,
    public val join_request: Boolean,
    public val forum: Boolean,
    public val stories_hidden: Boolean,
    public val stories_hidden_min: Boolean,
    public val stories_unavailable: Boolean,
    public val signature_profiles: Boolean,
    public val autotranslation: Boolean,
    public val broadcast_messages_allowed: Boolean,
    public val monoforum: Boolean,
    public val forum_tabs: Boolean,
    public val id: Long,
    public val access_hash: Long?,
    public val title: String,
    public val username: String?,
    public val photo: TlGen_ChatPhoto,
    public val date: Int,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val admin_rights: TlGen_ChatAdminRights?,
    public val banned_rights: TlGen_ChatBannedRights?,
    public val default_banned_rights: TlGen_ChatBannedRights?,
    public val participants_count: Int?,
    public val usernames: List<TlGen_Username>?,
    public val stories_max_id: Int?,
    public val color: TlGen_PeerColor?,
    public val profile_color: TlGen_PeerColor?,
    public val emoji_status: TlGen_EmojiStatus?,
    public val level: Int?,
    public val subscription_until_date: Int?,
    public val bot_verification_icon: Long?,
    public val send_paid_messages_stars: Long?,
    public val linked_monoforum_id: Long?,
  ) : TlGen_Object {
    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (left) result = result or 4U
        if (broadcast) result = result or 32U
        if (username != null) result = result or 64U
        if (verified) result = result or 128U
        if (megagroup) result = result or 256U
        if (restricted) result = result or 512U
        if (signatures) result = result or 2048U
        if (min) result = result or 4096U
        if (access_hash != null) result = result or 8192U
        if (admin_rights != null) result = result or 16384U
        if (banned_rights != null) result = result or 32768U
        if (participants_count != null) result = result or 131072U
        if (default_banned_rights != null) result = result or 262144U
        if (scam) result = result or 524288U
        if (has_link) result = result or 1048576U
        if (has_geo) result = result or 2097152U
        if (slowmode_enabled) result = result or 4194304U
        if (call_active) result = result or 8388608U
        if (call_not_empty) result = result or 16777216U
        if (fake) result = result or 33554432U
        if (gigagroup) result = result or 67108864U
        if (noforwards) result = result or 134217728U
        if (join_to_send) result = result or 268435456U
        if (join_request) result = result or 536870912U
        if (forum) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (usernames != null) result = result or 1U
        if (stories_hidden) result = result or 2U
        if (stories_hidden_min) result = result or 4U
        if (stories_unavailable) result = result or 8U
        if (stories_max_id != null) result = result or 16U
        if (color != null) result = result or 128U
        if (profile_color != null) result = result or 256U
        if (emoji_status != null) result = result or 512U
        if (level != null) result = result or 1024U
        if (subscription_until_date != null) result = result or 2048U
        if (signature_profiles) result = result or 4096U
        if (bot_verification_icon != null) result = result or 8192U
        if (send_paid_messages_stars != null) result = result or 16384U
        if (autotranslation) result = result or 32768U
        if (broadcast_messages_allowed) result = result or 65536U
        if (monoforum) result = result or 131072U
        if (linked_monoforum_id != null) result = result or 262144U
        if (forum_tabs) result = result or 524288U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      access_hash?.let { stream.writeInt64(it) }
      stream.writeString(title)
      username?.let { stream.writeString(it) }
      photo.serializeToStream(stream)
      stream.writeInt32(date)
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      admin_rights?.serializeToStream(stream)
      banned_rights?.serializeToStream(stream)
      default_banned_rights?.serializeToStream(stream)
      participants_count?.let { stream.writeInt32(it) }
      usernames?.let { TlGen_Vector.serialize(stream, it) }
      stories_max_id?.let { stream.writeInt32(it) }
      color?.serializeToStream(stream)
      profile_color?.serializeToStream(stream)
      emoji_status?.serializeToStream(stream)
      level?.let { stream.writeInt32(it) }
      subscription_until_date?.let { stream.writeInt32(it) }
      bot_verification_icon?.let { stream.writeInt64(it) }
      send_paid_messages_stars?.let { stream.writeInt64(it) }
      linked_monoforum_id?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xFE685355U
    }
  }
}

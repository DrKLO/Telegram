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

public sealed class TlGen_UserFull : TlGen_Object {
  public data class TL_userFull(
    public val blocked: Boolean,
    public val phone_calls_available: Boolean,
    public val phone_calls_private: Boolean,
    public val can_pin_message: Boolean,
    public val has_scheduled: Boolean,
    public val video_calls_available: Boolean,
    public val voice_messages_forbidden: Boolean,
    public val translations_disabled: Boolean,
    public val stories_pinned_available: Boolean,
    public val blocked_my_stories_from: Boolean,
    public val wallpaper_overridden: Boolean,
    public val contact_require_premium: Boolean,
    public val read_dates_private: Boolean,
    public val sponsored_enabled: Boolean,
    public val can_view_revenue: Boolean,
    public val bot_can_manage_emoji_status: Boolean,
    public val display_gifts_button: Boolean,
    public val id: Long,
    public val about: String?,
    public val settings: TlGen_PeerSettings,
    public val personal_photo: TlGen_Photo?,
    public val profile_photo: TlGen_Photo?,
    public val fallback_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
    public val pinned_msg_id: Int?,
    public val common_chats_count: Int,
    public val folder_id: Int?,
    public val ttl_period: Int?,
    public val theme: TlGen_ChatTheme?,
    public val private_forward_name: String?,
    public val bot_group_admin_rights: TlGen_ChatAdminRights?,
    public val bot_broadcast_admin_rights: TlGen_ChatAdminRights?,
    public val wallpaper: TlGen_WallPaper?,
    public val stories: TlGen_PeerStories?,
    public val business_work_hours: TlGen_BusinessWorkHours?,
    public val business_location: TlGen_BusinessLocation?,
    public val business_greeting_message: TlGen_BusinessGreetingMessage?,
    public val business_away_message: TlGen_BusinessAwayMessage?,
    public val business_intro: TlGen_BusinessIntro?,
    public val birthday: TlGen_Birthday?,
    public val stargifts_count: Int?,
    public val starref_program: TlGen_StarRefProgram?,
    public val bot_verification: TlGen_BotVerification?,
    public val send_paid_messages_stars: Long?,
    public val disallowed_gifts: TlGen_DisallowedGiftsSettings?,
    public val stars_rating: TlGen_StarsRating?,
    public val main_tab: TlGen_ProfileTab?,
    public val saved_music: TlGen_Document?,
    public val note: TlGen_TextWithEntities?,
    public val multiflags2_6: Multiflags2_6?,
    public val multiflags2_18: Multiflags2_18?,
  ) : TlGen_UserFull() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (phone_calls_available) result = result or 16U
        if (phone_calls_private) result = result or 32U
        if (pinned_msg_id != null) result = result or 64U
        if (can_pin_message) result = result or 128U
        if (folder_id != null) result = result or 2048U
        if (has_scheduled) result = result or 4096U
        if (video_calls_available) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (theme != null) result = result or 32768U
        if (private_forward_name != null) result = result or 65536U
        if (bot_group_admin_rights != null) result = result or 131072U
        if (bot_broadcast_admin_rights != null) result = result or 262144U
        if (voice_messages_forbidden) result = result or 1048576U
        if (personal_photo != null) result = result or 2097152U
        if (fallback_photo != null) result = result or 4194304U
        if (translations_disabled) result = result or 8388608U
        if (wallpaper != null) result = result or 16777216U
        if (stories != null) result = result or 33554432U
        if (stories_pinned_available) result = result or 67108864U
        if (blocked_my_stories_from) result = result or 134217728U
        if (wallpaper_overridden) result = result or 268435456U
        if (contact_require_premium) result = result or 536870912U
        if (read_dates_private) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (business_work_hours != null) result = result or 1U
        if (business_location != null) result = result or 2U
        if (business_greeting_message != null) result = result or 4U
        if (business_away_message != null) result = result or 8U
        if (business_intro != null) result = result or 16U
        if (birthday != null) result = result or 32U
        if (multiflags2_6 != null) result = result or 64U
        if (sponsored_enabled) result = result or 128U
        if (stargifts_count != null) result = result or 256U
        if (can_view_revenue) result = result or 512U
        if (bot_can_manage_emoji_status) result = result or 1024U
        if (starref_program != null) result = result or 2048U
        if (bot_verification != null) result = result or 4096U
        if (send_paid_messages_stars != null) result = result or 16384U
        if (disallowed_gifts != null) result = result or 32768U
        if (display_gifts_button) result = result or 65536U
        if (stars_rating != null) result = result or 131072U
        if (multiflags2_18 != null) result = result or 262144U
        if (main_tab != null) result = result or 1048576U
        if (saved_music != null) result = result or 2097152U
        if (note != null) result = result or 4194304U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      about?.let { stream.writeString(it) }
      settings.serializeToStream(stream)
      personal_photo?.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      fallback_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
      pinned_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(common_chats_count)
      folder_id?.let { stream.writeInt32(it) }
      ttl_period?.let { stream.writeInt32(it) }
      theme?.serializeToStream(stream)
      private_forward_name?.let { stream.writeString(it) }
      bot_group_admin_rights?.serializeToStream(stream)
      bot_broadcast_admin_rights?.serializeToStream(stream)
      wallpaper?.serializeToStream(stream)
      stories?.serializeToStream(stream)
      business_work_hours?.serializeToStream(stream)
      business_location?.serializeToStream(stream)
      business_greeting_message?.serializeToStream(stream)
      business_away_message?.serializeToStream(stream)
      business_intro?.serializeToStream(stream)
      birthday?.serializeToStream(stream)
      multiflags2_6?.let { stream.writeInt64(it.personal_channel_id) }
      multiflags2_6?.let { stream.writeInt32(it.personal_channel_message) }
      stargifts_count?.let { stream.writeInt32(it) }
      starref_program?.serializeToStream(stream)
      bot_verification?.serializeToStream(stream)
      send_paid_messages_stars?.let { stream.writeInt64(it) }
      disallowed_gifts?.serializeToStream(stream)
      stars_rating?.serializeToStream(stream)
      multiflags2_18?.let { it.stars_my_pending_rating.serializeToStream(stream) }
      multiflags2_18?.let { stream.writeInt32(it.stars_my_pending_rating_date) }
      main_tab?.serializeToStream(stream)
      saved_music?.serializeToStream(stream)
      note?.serializeToStream(stream)
    }

    public data class Multiflags2_6(
      public val personal_channel_id: Long,
      public val personal_channel_message: Int,
    )

    public data class Multiflags2_18(
      public val stars_my_pending_rating: TlGen_StarsRating,
      public val stars_my_pending_rating_date: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xA02BC13EU
    }
  }

  public data class TL_userFull_layer30(
    public val user: TlGen_User,
    public val link: TlGen_contacts_Link,
    public val profile_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val blocked: Boolean,
    public val real_first_name: String,
    public val real_last_name: String,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      user.serializeToStream(stream)
      link.serializeToStream(stream)
      profile_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      stream.writeBool(blocked)
      stream.writeString(real_first_name)
      stream.writeString(real_last_name)
    }

    public companion object {
      public const val MAGIC: UInt = 0x771095DAU
    }
  }

  public data class TL_userFull_layer48(
    public val user: TlGen_User,
    public val link: TlGen_contacts_Link,
    public val profile_photo: TlGen_Photo,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val blocked: Boolean,
    public val bot_info: TlGen_BotInfo,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      user.serializeToStream(stream)
      link.serializeToStream(stream)
      profile_photo.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      stream.writeBool(blocked)
      bot_info.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5A89AC5BU
    }
  }

  public data class TL_userFull_layer57(
    public val blocked: Boolean,
    public val user: TlGen_User,
    public val about: String?,
    public val link: TlGen_contacts_Link,
    public val profile_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      user.serializeToStream(stream)
      about?.let { stream.writeString(it) }
      link.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5932FC03U
    }
  }

  public data class TL_userFull_layer86(
    public val blocked: Boolean,
    public val phone_calls_available: Boolean,
    public val phone_calls_private: Boolean,
    public val user: TlGen_User,
    public val about: String?,
    public val link: TlGen_contacts_Link,
    public val profile_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
    public val common_chats_count: Int,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (phone_calls_available) result = result or 16U
        if (phone_calls_private) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      user.serializeToStream(stream)
      about?.let { stream.writeString(it) }
      link.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
      stream.writeInt32(common_chats_count)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0F220F3FU
    }
  }

  public data class TL_userFull_layer98(
    public val blocked: Boolean,
    public val phone_calls_available: Boolean,
    public val phone_calls_private: Boolean,
    public val can_pin_message: Boolean,
    public val user: TlGen_User,
    public val about: String?,
    public val link: TlGen_contacts_Link,
    public val profile_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
    public val pinned_msg_id: Int?,
    public val common_chats_count: Int,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (phone_calls_available) result = result or 16U
        if (phone_calls_private) result = result or 32U
        if (pinned_msg_id != null) result = result or 64U
        if (can_pin_message) result = result or 128U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      user.serializeToStream(stream)
      about?.let { stream.writeString(it) }
      link.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
      pinned_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(common_chats_count)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8EA4A881U
    }
  }

  public data class TL_userFull_layer101(
    public val blocked: Boolean,
    public val phone_calls_available: Boolean,
    public val phone_calls_private: Boolean,
    public val can_pin_message: Boolean,
    public val user: TlGen_User,
    public val about: String?,
    public val link: TlGen_contacts_Link,
    public val profile_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
    public val pinned_msg_id: Int?,
    public val common_chats_count: Int,
    public val folder_id: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (phone_calls_available) result = result or 16U
        if (phone_calls_private) result = result or 32U
        if (pinned_msg_id != null) result = result or 64U
        if (can_pin_message) result = result or 128U
        if (folder_id != null) result = result or 2048U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      user.serializeToStream(stream)
      about?.let { stream.writeString(it) }
      link.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
      pinned_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(common_chats_count)
      folder_id?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x745559CCU
    }
  }

  public data class TL_userFull_layer123(
    public val blocked: Boolean,
    public val phone_calls_available: Boolean,
    public val phone_calls_private: Boolean,
    public val can_pin_message: Boolean,
    public val has_scheduled: Boolean,
    public val video_calls_available: Boolean,
    public val user: TlGen_User,
    public val about: String?,
    public val settings: TlGen_PeerSettings,
    public val profile_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
    public val pinned_msg_id: Int?,
    public val common_chats_count: Int,
    public val folder_id: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (phone_calls_available) result = result or 16U
        if (phone_calls_private) result = result or 32U
        if (pinned_msg_id != null) result = result or 64U
        if (can_pin_message) result = result or 128U
        if (folder_id != null) result = result or 2048U
        if (has_scheduled) result = result or 4096U
        if (video_calls_available) result = result or 8192U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      user.serializeToStream(stream)
      about?.let { stream.writeString(it) }
      settings.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
      pinned_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(common_chats_count)
      folder_id?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xEDF17C12U
    }
  }

  public data class TL_userFull_layer131(
    public val blocked: Boolean,
    public val phone_calls_available: Boolean,
    public val phone_calls_private: Boolean,
    public val can_pin_message: Boolean,
    public val has_scheduled: Boolean,
    public val video_calls_available: Boolean,
    public val user: TlGen_User,
    public val about: String?,
    public val settings: TlGen_PeerSettings,
    public val profile_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
    public val pinned_msg_id: Int?,
    public val common_chats_count: Int,
    public val folder_id: Int?,
    public val ttl_period: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (phone_calls_available) result = result or 16U
        if (phone_calls_private) result = result or 32U
        if (pinned_msg_id != null) result = result or 64U
        if (can_pin_message) result = result or 128U
        if (folder_id != null) result = result or 2048U
        if (has_scheduled) result = result or 4096U
        if (video_calls_available) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      user.serializeToStream(stream)
      about?.let { stream.writeString(it) }
      settings.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
      pinned_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(common_chats_count)
      folder_id?.let { stream.writeInt32(it) }
      ttl_period?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x139A9A77U
    }
  }

  public data class TL_userFull_layer134(
    public val blocked: Boolean,
    public val phone_calls_available: Boolean,
    public val phone_calls_private: Boolean,
    public val can_pin_message: Boolean,
    public val has_scheduled: Boolean,
    public val video_calls_available: Boolean,
    public val user: TlGen_User,
    public val about: String?,
    public val settings: TlGen_PeerSettings,
    public val profile_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
    public val pinned_msg_id: Int?,
    public val common_chats_count: Int,
    public val folder_id: Int?,
    public val ttl_period: Int?,
    public val theme_emoticon: String?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (phone_calls_available) result = result or 16U
        if (phone_calls_private) result = result or 32U
        if (pinned_msg_id != null) result = result or 64U
        if (can_pin_message) result = result or 128U
        if (folder_id != null) result = result or 2048U
        if (has_scheduled) result = result or 4096U
        if (video_calls_available) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (theme_emoticon != null) result = result or 32768U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      user.serializeToStream(stream)
      about?.let { stream.writeString(it) }
      settings.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
      pinned_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(common_chats_count)
      folder_id?.let { stream.writeInt32(it) }
      ttl_period?.let { stream.writeInt32(it) }
      theme_emoticon?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xD697FF05U
    }
  }

  public data class TL_userFull_layer139(
    public val blocked: Boolean,
    public val phone_calls_available: Boolean,
    public val phone_calls_private: Boolean,
    public val can_pin_message: Boolean,
    public val has_scheduled: Boolean,
    public val video_calls_available: Boolean,
    public val id: Long,
    public val about: String?,
    public val settings: TlGen_PeerSettings,
    public val profile_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
    public val pinned_msg_id: Int?,
    public val common_chats_count: Int,
    public val folder_id: Int?,
    public val ttl_period: Int?,
    public val theme_emoticon: String?,
    public val private_forward_name: String?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (phone_calls_available) result = result or 16U
        if (phone_calls_private) result = result or 32U
        if (pinned_msg_id != null) result = result or 64U
        if (can_pin_message) result = result or 128U
        if (folder_id != null) result = result or 2048U
        if (has_scheduled) result = result or 4096U
        if (video_calls_available) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (theme_emoticon != null) result = result or 32768U
        if (private_forward_name != null) result = result or 65536U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      about?.let { stream.writeString(it) }
      settings.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
      pinned_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(common_chats_count)
      folder_id?.let { stream.writeInt32(it) }
      ttl_period?.let { stream.writeInt32(it) }
      theme_emoticon?.let { stream.writeString(it) }
      private_forward_name?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xCF366521U
    }
  }

  public data class TL_userFull_layer143(
    public val blocked: Boolean,
    public val phone_calls_available: Boolean,
    public val phone_calls_private: Boolean,
    public val can_pin_message: Boolean,
    public val has_scheduled: Boolean,
    public val video_calls_available: Boolean,
    public val id: Long,
    public val about: String?,
    public val settings: TlGen_PeerSettings,
    public val profile_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
    public val pinned_msg_id: Int?,
    public val common_chats_count: Int,
    public val folder_id: Int?,
    public val ttl_period: Int?,
    public val theme_emoticon: String?,
    public val private_forward_name: String?,
    public val bot_group_admin_rights: TlGen_ChatAdminRights?,
    public val bot_broadcast_admin_rights: TlGen_ChatAdminRights?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (phone_calls_available) result = result or 16U
        if (phone_calls_private) result = result or 32U
        if (pinned_msg_id != null) result = result or 64U
        if (can_pin_message) result = result or 128U
        if (folder_id != null) result = result or 2048U
        if (has_scheduled) result = result or 4096U
        if (video_calls_available) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (theme_emoticon != null) result = result or 32768U
        if (private_forward_name != null) result = result or 65536U
        if (bot_group_admin_rights != null) result = result or 131072U
        if (bot_broadcast_admin_rights != null) result = result or 262144U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      about?.let { stream.writeString(it) }
      settings.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
      pinned_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(common_chats_count)
      folder_id?.let { stream.writeInt32(it) }
      ttl_period?.let { stream.writeInt32(it) }
      theme_emoticon?.let { stream.writeString(it) }
      private_forward_name?.let { stream.writeString(it) }
      bot_group_admin_rights?.serializeToStream(stream)
      bot_broadcast_admin_rights?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8C72EA81U
    }
  }

  public data class TL_userFull_layer150(
    public val blocked: Boolean,
    public val phone_calls_available: Boolean,
    public val phone_calls_private: Boolean,
    public val can_pin_message: Boolean,
    public val has_scheduled: Boolean,
    public val video_calls_available: Boolean,
    public val voice_messages_forbidden: Boolean,
    public val id: Long,
    public val about: String?,
    public val settings: TlGen_PeerSettings,
    public val profile_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
    public val pinned_msg_id: Int?,
    public val common_chats_count: Int,
    public val folder_id: Int?,
    public val ttl_period: Int?,
    public val theme_emoticon: String?,
    public val private_forward_name: String?,
    public val bot_group_admin_rights: TlGen_ChatAdminRights?,
    public val bot_broadcast_admin_rights: TlGen_ChatAdminRights?,
    public val premium_gifts: List<TlGen_PremiumGiftOption>?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (phone_calls_available) result = result or 16U
        if (phone_calls_private) result = result or 32U
        if (pinned_msg_id != null) result = result or 64U
        if (can_pin_message) result = result or 128U
        if (folder_id != null) result = result or 2048U
        if (has_scheduled) result = result or 4096U
        if (video_calls_available) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (theme_emoticon != null) result = result or 32768U
        if (private_forward_name != null) result = result or 65536U
        if (bot_group_admin_rights != null) result = result or 131072U
        if (bot_broadcast_admin_rights != null) result = result or 262144U
        if (premium_gifts != null) result = result or 524288U
        if (voice_messages_forbidden) result = result or 1048576U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      about?.let { stream.writeString(it) }
      settings.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
      pinned_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(common_chats_count)
      folder_id?.let { stream.writeInt32(it) }
      ttl_period?.let { stream.writeInt32(it) }
      theme_emoticon?.let { stream.writeString(it) }
      private_forward_name?.let { stream.writeString(it) }
      bot_group_admin_rights?.serializeToStream(stream)
      bot_broadcast_admin_rights?.serializeToStream(stream)
      premium_gifts?.let { TlGen_Vector.serialize(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xC4B1FC3FU
    }
  }

  public data class TL_userFull_layer157(
    public val blocked: Boolean,
    public val phone_calls_available: Boolean,
    public val phone_calls_private: Boolean,
    public val can_pin_message: Boolean,
    public val has_scheduled: Boolean,
    public val video_calls_available: Boolean,
    public val voice_messages_forbidden: Boolean,
    public val translations_disabled: Boolean,
    public val id: Long,
    public val about: String?,
    public val settings: TlGen_PeerSettings,
    public val personal_photo: TlGen_Photo?,
    public val profile_photo: TlGen_Photo?,
    public val fallback_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
    public val pinned_msg_id: Int?,
    public val common_chats_count: Int,
    public val folder_id: Int?,
    public val ttl_period: Int?,
    public val theme_emoticon: String?,
    public val private_forward_name: String?,
    public val bot_group_admin_rights: TlGen_ChatAdminRights?,
    public val bot_broadcast_admin_rights: TlGen_ChatAdminRights?,
    public val premium_gifts: List<TlGen_PremiumGiftOption>?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (phone_calls_available) result = result or 16U
        if (phone_calls_private) result = result or 32U
        if (pinned_msg_id != null) result = result or 64U
        if (can_pin_message) result = result or 128U
        if (folder_id != null) result = result or 2048U
        if (has_scheduled) result = result or 4096U
        if (video_calls_available) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (theme_emoticon != null) result = result or 32768U
        if (private_forward_name != null) result = result or 65536U
        if (bot_group_admin_rights != null) result = result or 131072U
        if (bot_broadcast_admin_rights != null) result = result or 262144U
        if (premium_gifts != null) result = result or 524288U
        if (voice_messages_forbidden) result = result or 1048576U
        if (personal_photo != null) result = result or 2097152U
        if (fallback_photo != null) result = result or 4194304U
        if (translations_disabled) result = result or 8388608U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      about?.let { stream.writeString(it) }
      settings.serializeToStream(stream)
      personal_photo?.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      fallback_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
      pinned_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(common_chats_count)
      folder_id?.let { stream.writeInt32(it) }
      ttl_period?.let { stream.writeInt32(it) }
      theme_emoticon?.let { stream.writeString(it) }
      private_forward_name?.let { stream.writeString(it) }
      bot_group_admin_rights?.serializeToStream(stream)
      bot_broadcast_admin_rights?.serializeToStream(stream)
      premium_gifts?.let { TlGen_Vector.serialize(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xF8D32AEDU
    }
  }

  public data class TL_userFull_layer159(
    public val blocked: Boolean,
    public val phone_calls_available: Boolean,
    public val phone_calls_private: Boolean,
    public val can_pin_message: Boolean,
    public val has_scheduled: Boolean,
    public val video_calls_available: Boolean,
    public val voice_messages_forbidden: Boolean,
    public val translations_disabled: Boolean,
    public val id: Long,
    public val about: String?,
    public val settings: TlGen_PeerSettings,
    public val personal_photo: TlGen_Photo?,
    public val profile_photo: TlGen_Photo?,
    public val fallback_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
    public val pinned_msg_id: Int?,
    public val common_chats_count: Int,
    public val folder_id: Int?,
    public val ttl_period: Int?,
    public val theme_emoticon: String?,
    public val private_forward_name: String?,
    public val bot_group_admin_rights: TlGen_ChatAdminRights?,
    public val bot_broadcast_admin_rights: TlGen_ChatAdminRights?,
    public val premium_gifts: List<TlGen_PremiumGiftOption>?,
    public val wallpaper: TlGen_WallPaper?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (phone_calls_available) result = result or 16U
        if (phone_calls_private) result = result or 32U
        if (pinned_msg_id != null) result = result or 64U
        if (can_pin_message) result = result or 128U
        if (folder_id != null) result = result or 2048U
        if (has_scheduled) result = result or 4096U
        if (video_calls_available) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (theme_emoticon != null) result = result or 32768U
        if (private_forward_name != null) result = result or 65536U
        if (bot_group_admin_rights != null) result = result or 131072U
        if (bot_broadcast_admin_rights != null) result = result or 262144U
        if (premium_gifts != null) result = result or 524288U
        if (voice_messages_forbidden) result = result or 1048576U
        if (personal_photo != null) result = result or 2097152U
        if (fallback_photo != null) result = result or 4194304U
        if (translations_disabled) result = result or 8388608U
        if (wallpaper != null) result = result or 16777216U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      about?.let { stream.writeString(it) }
      settings.serializeToStream(stream)
      personal_photo?.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      fallback_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
      pinned_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(common_chats_count)
      folder_id?.let { stream.writeInt32(it) }
      ttl_period?.let { stream.writeInt32(it) }
      theme_emoticon?.let { stream.writeString(it) }
      private_forward_name?.let { stream.writeString(it) }
      bot_group_admin_rights?.serializeToStream(stream)
      bot_broadcast_admin_rights?.serializeToStream(stream)
      premium_gifts?.let { TlGen_Vector.serialize(stream, it) }
      wallpaper?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x93EADB53U
    }
  }

  public data class TL_userFull_layer163(
    public val blocked: Boolean,
    public val phone_calls_available: Boolean,
    public val phone_calls_private: Boolean,
    public val can_pin_message: Boolean,
    public val has_scheduled: Boolean,
    public val video_calls_available: Boolean,
    public val voice_messages_forbidden: Boolean,
    public val translations_disabled: Boolean,
    public val stories_pinned_available: Boolean,
    public val blocked_my_stories_from: Boolean,
    public val id: Long,
    public val about: String?,
    public val settings: TlGen_PeerSettings,
    public val personal_photo: TlGen_Photo?,
    public val profile_photo: TlGen_Photo?,
    public val fallback_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
    public val pinned_msg_id: Int?,
    public val common_chats_count: Int,
    public val folder_id: Int?,
    public val ttl_period: Int?,
    public val theme_emoticon: String?,
    public val private_forward_name: String?,
    public val bot_group_admin_rights: TlGen_ChatAdminRights?,
    public val bot_broadcast_admin_rights: TlGen_ChatAdminRights?,
    public val premium_gifts: List<TlGen_PremiumGiftOption>?,
    public val wallpaper: TlGen_WallPaper?,
    public val stories: TlGen_UserStories?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (phone_calls_available) result = result or 16U
        if (phone_calls_private) result = result or 32U
        if (pinned_msg_id != null) result = result or 64U
        if (can_pin_message) result = result or 128U
        if (folder_id != null) result = result or 2048U
        if (has_scheduled) result = result or 4096U
        if (video_calls_available) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (theme_emoticon != null) result = result or 32768U
        if (private_forward_name != null) result = result or 65536U
        if (bot_group_admin_rights != null) result = result or 131072U
        if (bot_broadcast_admin_rights != null) result = result or 262144U
        if (premium_gifts != null) result = result or 524288U
        if (voice_messages_forbidden) result = result or 1048576U
        if (personal_photo != null) result = result or 2097152U
        if (fallback_photo != null) result = result or 4194304U
        if (translations_disabled) result = result or 8388608U
        if (wallpaper != null) result = result or 16777216U
        if (stories != null) result = result or 33554432U
        if (stories_pinned_available) result = result or 67108864U
        if (blocked_my_stories_from) result = result or 134217728U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      about?.let { stream.writeString(it) }
      settings.serializeToStream(stream)
      personal_photo?.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      fallback_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
      pinned_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(common_chats_count)
      folder_id?.let { stream.writeInt32(it) }
      ttl_period?.let { stream.writeInt32(it) }
      theme_emoticon?.let { stream.writeString(it) }
      private_forward_name?.let { stream.writeString(it) }
      bot_group_admin_rights?.serializeToStream(stream)
      bot_broadcast_admin_rights?.serializeToStream(stream)
      premium_gifts?.let { TlGen_Vector.serialize(stream, it) }
      wallpaper?.serializeToStream(stream)
      stories?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4FE1CC86U
    }
  }

  public data class TL_userFull_layer175(
    public val blocked: Boolean,
    public val phone_calls_available: Boolean,
    public val phone_calls_private: Boolean,
    public val can_pin_message: Boolean,
    public val has_scheduled: Boolean,
    public val video_calls_available: Boolean,
    public val voice_messages_forbidden: Boolean,
    public val translations_disabled: Boolean,
    public val stories_pinned_available: Boolean,
    public val blocked_my_stories_from: Boolean,
    public val wallpaper_overridden: Boolean,
    public val contact_require_premium: Boolean,
    public val read_dates_private: Boolean,
    public val id: Long,
    public val about: String?,
    public val settings: TlGen_PeerSettings,
    public val personal_photo: TlGen_Photo?,
    public val profile_photo: TlGen_Photo?,
    public val fallback_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
    public val pinned_msg_id: Int?,
    public val common_chats_count: Int,
    public val folder_id: Int?,
    public val ttl_period: Int?,
    public val theme_emoticon: String?,
    public val private_forward_name: String?,
    public val bot_group_admin_rights: TlGen_ChatAdminRights?,
    public val bot_broadcast_admin_rights: TlGen_ChatAdminRights?,
    public val premium_gifts: List<TlGen_PremiumGiftOption>?,
    public val wallpaper: TlGen_WallPaper?,
    public val stories: TlGen_PeerStories?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (phone_calls_available) result = result or 16U
        if (phone_calls_private) result = result or 32U
        if (pinned_msg_id != null) result = result or 64U
        if (can_pin_message) result = result or 128U
        if (folder_id != null) result = result or 2048U
        if (has_scheduled) result = result or 4096U
        if (video_calls_available) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (theme_emoticon != null) result = result or 32768U
        if (private_forward_name != null) result = result or 65536U
        if (bot_group_admin_rights != null) result = result or 131072U
        if (bot_broadcast_admin_rights != null) result = result or 262144U
        if (premium_gifts != null) result = result or 524288U
        if (voice_messages_forbidden) result = result or 1048576U
        if (personal_photo != null) result = result or 2097152U
        if (fallback_photo != null) result = result or 4194304U
        if (translations_disabled) result = result or 8388608U
        if (wallpaper != null) result = result or 16777216U
        if (stories != null) result = result or 33554432U
        if (stories_pinned_available) result = result or 67108864U
        if (blocked_my_stories_from) result = result or 134217728U
        if (wallpaper_overridden) result = result or 268435456U
        if (contact_require_premium) result = result or 536870912U
        if (read_dates_private) result = result or 1073741824U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      about?.let { stream.writeString(it) }
      settings.serializeToStream(stream)
      personal_photo?.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      fallback_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
      pinned_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(common_chats_count)
      folder_id?.let { stream.writeInt32(it) }
      ttl_period?.let { stream.writeInt32(it) }
      theme_emoticon?.let { stream.writeString(it) }
      private_forward_name?.let { stream.writeString(it) }
      bot_group_admin_rights?.serializeToStream(stream)
      bot_broadcast_admin_rights?.serializeToStream(stream)
      premium_gifts?.let { TlGen_Vector.serialize(stream, it) }
      wallpaper?.serializeToStream(stream)
      stories?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB9B12C6CU
    }
  }

  public data class TL_userFull_layer176(
    public val blocked: Boolean,
    public val phone_calls_available: Boolean,
    public val phone_calls_private: Boolean,
    public val can_pin_message: Boolean,
    public val has_scheduled: Boolean,
    public val video_calls_available: Boolean,
    public val voice_messages_forbidden: Boolean,
    public val translations_disabled: Boolean,
    public val stories_pinned_available: Boolean,
    public val blocked_my_stories_from: Boolean,
    public val wallpaper_overridden: Boolean,
    public val contact_require_premium: Boolean,
    public val read_dates_private: Boolean,
    public val id: Long,
    public val about: String?,
    public val settings: TlGen_PeerSettings,
    public val personal_photo: TlGen_Photo?,
    public val profile_photo: TlGen_Photo?,
    public val fallback_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
    public val pinned_msg_id: Int?,
    public val common_chats_count: Int,
    public val folder_id: Int?,
    public val ttl_period: Int?,
    public val theme_emoticon: String?,
    public val private_forward_name: String?,
    public val bot_group_admin_rights: TlGen_ChatAdminRights?,
    public val bot_broadcast_admin_rights: TlGen_ChatAdminRights?,
    public val premium_gifts: List<TlGen_PremiumGiftOption>?,
    public val wallpaper: TlGen_WallPaper?,
    public val stories: TlGen_PeerStories?,
    public val business_work_hours: TlGen_BusinessWorkHours?,
    public val business_location: TlGen_BusinessLocation?,
    public val business_greeting_message: TlGen_BusinessGreetingMessage?,
    public val business_away_message: TlGen_BusinessAwayMessage?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (phone_calls_available) result = result or 16U
        if (phone_calls_private) result = result or 32U
        if (pinned_msg_id != null) result = result or 64U
        if (can_pin_message) result = result or 128U
        if (folder_id != null) result = result or 2048U
        if (has_scheduled) result = result or 4096U
        if (video_calls_available) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (theme_emoticon != null) result = result or 32768U
        if (private_forward_name != null) result = result or 65536U
        if (bot_group_admin_rights != null) result = result or 131072U
        if (bot_broadcast_admin_rights != null) result = result or 262144U
        if (premium_gifts != null) result = result or 524288U
        if (voice_messages_forbidden) result = result or 1048576U
        if (personal_photo != null) result = result or 2097152U
        if (fallback_photo != null) result = result or 4194304U
        if (translations_disabled) result = result or 8388608U
        if (wallpaper != null) result = result or 16777216U
        if (stories != null) result = result or 33554432U
        if (stories_pinned_available) result = result or 67108864U
        if (blocked_my_stories_from) result = result or 134217728U
        if (wallpaper_overridden) result = result or 268435456U
        if (contact_require_premium) result = result or 536870912U
        if (read_dates_private) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (business_work_hours != null) result = result or 1U
        if (business_location != null) result = result or 2U
        if (business_greeting_message != null) result = result or 4U
        if (business_away_message != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      about?.let { stream.writeString(it) }
      settings.serializeToStream(stream)
      personal_photo?.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      fallback_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
      pinned_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(common_chats_count)
      folder_id?.let { stream.writeInt32(it) }
      ttl_period?.let { stream.writeInt32(it) }
      theme_emoticon?.let { stream.writeString(it) }
      private_forward_name?.let { stream.writeString(it) }
      bot_group_admin_rights?.serializeToStream(stream)
      bot_broadcast_admin_rights?.serializeToStream(stream)
      premium_gifts?.let { TlGen_Vector.serialize(stream, it) }
      wallpaper?.serializeToStream(stream)
      stories?.serializeToStream(stream)
      business_work_hours?.serializeToStream(stream)
      business_location?.serializeToStream(stream)
      business_greeting_message?.serializeToStream(stream)
      business_away_message?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x22FF3E85U
    }
  }

  public data class TL_userFull_layer188(
    public val blocked: Boolean,
    public val phone_calls_available: Boolean,
    public val phone_calls_private: Boolean,
    public val can_pin_message: Boolean,
    public val has_scheduled: Boolean,
    public val video_calls_available: Boolean,
    public val voice_messages_forbidden: Boolean,
    public val translations_disabled: Boolean,
    public val stories_pinned_available: Boolean,
    public val blocked_my_stories_from: Boolean,
    public val wallpaper_overridden: Boolean,
    public val contact_require_premium: Boolean,
    public val read_dates_private: Boolean,
    public val sponsored_enabled: Boolean,
    public val id: Long,
    public val about: String?,
    public val settings: TlGen_PeerSettings,
    public val personal_photo: TlGen_Photo?,
    public val profile_photo: TlGen_Photo?,
    public val fallback_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
    public val pinned_msg_id: Int?,
    public val common_chats_count: Int,
    public val folder_id: Int?,
    public val ttl_period: Int?,
    public val theme_emoticon: String?,
    public val private_forward_name: String?,
    public val bot_group_admin_rights: TlGen_ChatAdminRights?,
    public val bot_broadcast_admin_rights: TlGen_ChatAdminRights?,
    public val premium_gifts: List<TlGen_PremiumGiftOption>?,
    public val wallpaper: TlGen_WallPaper?,
    public val stories: TlGen_PeerStories?,
    public val business_work_hours: TlGen_BusinessWorkHours?,
    public val business_location: TlGen_BusinessLocation?,
    public val business_greeting_message: TlGen_BusinessGreetingMessage?,
    public val business_away_message: TlGen_BusinessAwayMessage?,
    public val business_intro: TlGen_BusinessIntro?,
    public val birthday: TlGen_Birthday?,
    public val multiflags2_6: Multiflags2_6?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (phone_calls_available) result = result or 16U
        if (phone_calls_private) result = result or 32U
        if (pinned_msg_id != null) result = result or 64U
        if (can_pin_message) result = result or 128U
        if (folder_id != null) result = result or 2048U
        if (has_scheduled) result = result or 4096U
        if (video_calls_available) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (theme_emoticon != null) result = result or 32768U
        if (private_forward_name != null) result = result or 65536U
        if (bot_group_admin_rights != null) result = result or 131072U
        if (bot_broadcast_admin_rights != null) result = result or 262144U
        if (premium_gifts != null) result = result or 524288U
        if (voice_messages_forbidden) result = result or 1048576U
        if (personal_photo != null) result = result or 2097152U
        if (fallback_photo != null) result = result or 4194304U
        if (translations_disabled) result = result or 8388608U
        if (wallpaper != null) result = result or 16777216U
        if (stories != null) result = result or 33554432U
        if (stories_pinned_available) result = result or 67108864U
        if (blocked_my_stories_from) result = result or 134217728U
        if (wallpaper_overridden) result = result or 268435456U
        if (contact_require_premium) result = result or 536870912U
        if (read_dates_private) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (business_work_hours != null) result = result or 1U
        if (business_location != null) result = result or 2U
        if (business_greeting_message != null) result = result or 4U
        if (business_away_message != null) result = result or 8U
        if (business_intro != null) result = result or 16U
        if (birthday != null) result = result or 32U
        if (multiflags2_6 != null) result = result or 64U
        if (sponsored_enabled) result = result or 128U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      about?.let { stream.writeString(it) }
      settings.serializeToStream(stream)
      personal_photo?.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      fallback_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
      pinned_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(common_chats_count)
      folder_id?.let { stream.writeInt32(it) }
      ttl_period?.let { stream.writeInt32(it) }
      theme_emoticon?.let { stream.writeString(it) }
      private_forward_name?.let { stream.writeString(it) }
      bot_group_admin_rights?.serializeToStream(stream)
      bot_broadcast_admin_rights?.serializeToStream(stream)
      premium_gifts?.let { TlGen_Vector.serialize(stream, it) }
      wallpaper?.serializeToStream(stream)
      stories?.serializeToStream(stream)
      business_work_hours?.serializeToStream(stream)
      business_location?.serializeToStream(stream)
      business_greeting_message?.serializeToStream(stream)
      business_away_message?.serializeToStream(stream)
      business_intro?.serializeToStream(stream)
      birthday?.serializeToStream(stream)
      multiflags2_6?.let { stream.writeInt64(it.personal_channel_id) }
      multiflags2_6?.let { stream.writeInt32(it.personal_channel_message) }
    }

    public data class Multiflags2_6(
      public val personal_channel_id: Long,
      public val personal_channel_message: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xCC997720U
    }
  }

  public data class TL_userFull_layer194(
    public val blocked: Boolean,
    public val phone_calls_available: Boolean,
    public val phone_calls_private: Boolean,
    public val can_pin_message: Boolean,
    public val has_scheduled: Boolean,
    public val video_calls_available: Boolean,
    public val voice_messages_forbidden: Boolean,
    public val translations_disabled: Boolean,
    public val stories_pinned_available: Boolean,
    public val blocked_my_stories_from: Boolean,
    public val wallpaper_overridden: Boolean,
    public val contact_require_premium: Boolean,
    public val read_dates_private: Boolean,
    public val sponsored_enabled: Boolean,
    public val can_view_revenue: Boolean,
    public val bot_can_manage_emoji_status: Boolean,
    public val id: Long,
    public val about: String?,
    public val settings: TlGen_PeerSettings,
    public val personal_photo: TlGen_Photo?,
    public val profile_photo: TlGen_Photo?,
    public val fallback_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
    public val pinned_msg_id: Int?,
    public val common_chats_count: Int,
    public val folder_id: Int?,
    public val ttl_period: Int?,
    public val theme_emoticon: String?,
    public val private_forward_name: String?,
    public val bot_group_admin_rights: TlGen_ChatAdminRights?,
    public val bot_broadcast_admin_rights: TlGen_ChatAdminRights?,
    public val premium_gifts: List<TlGen_PremiumGiftOption>?,
    public val wallpaper: TlGen_WallPaper?,
    public val stories: TlGen_PeerStories?,
    public val business_work_hours: TlGen_BusinessWorkHours?,
    public val business_location: TlGen_BusinessLocation?,
    public val business_greeting_message: TlGen_BusinessGreetingMessage?,
    public val business_away_message: TlGen_BusinessAwayMessage?,
    public val business_intro: TlGen_BusinessIntro?,
    public val birthday: TlGen_Birthday?,
    public val stargifts_count: Int?,
    public val multiflags2_6: Multiflags2_6?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (phone_calls_available) result = result or 16U
        if (phone_calls_private) result = result or 32U
        if (pinned_msg_id != null) result = result or 64U
        if (can_pin_message) result = result or 128U
        if (folder_id != null) result = result or 2048U
        if (has_scheduled) result = result or 4096U
        if (video_calls_available) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (theme_emoticon != null) result = result or 32768U
        if (private_forward_name != null) result = result or 65536U
        if (bot_group_admin_rights != null) result = result or 131072U
        if (bot_broadcast_admin_rights != null) result = result or 262144U
        if (premium_gifts != null) result = result or 524288U
        if (voice_messages_forbidden) result = result or 1048576U
        if (personal_photo != null) result = result or 2097152U
        if (fallback_photo != null) result = result or 4194304U
        if (translations_disabled) result = result or 8388608U
        if (wallpaper != null) result = result or 16777216U
        if (stories != null) result = result or 33554432U
        if (stories_pinned_available) result = result or 67108864U
        if (blocked_my_stories_from) result = result or 134217728U
        if (wallpaper_overridden) result = result or 268435456U
        if (contact_require_premium) result = result or 536870912U
        if (read_dates_private) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (business_work_hours != null) result = result or 1U
        if (business_location != null) result = result or 2U
        if (business_greeting_message != null) result = result or 4U
        if (business_away_message != null) result = result or 8U
        if (business_intro != null) result = result or 16U
        if (birthday != null) result = result or 32U
        if (multiflags2_6 != null) result = result or 64U
        if (sponsored_enabled) result = result or 128U
        if (stargifts_count != null) result = result or 256U
        if (can_view_revenue) result = result or 512U
        if (bot_can_manage_emoji_status) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      about?.let { stream.writeString(it) }
      settings.serializeToStream(stream)
      personal_photo?.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      fallback_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
      pinned_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(common_chats_count)
      folder_id?.let { stream.writeInt32(it) }
      ttl_period?.let { stream.writeInt32(it) }
      theme_emoticon?.let { stream.writeString(it) }
      private_forward_name?.let { stream.writeString(it) }
      bot_group_admin_rights?.serializeToStream(stream)
      bot_broadcast_admin_rights?.serializeToStream(stream)
      premium_gifts?.let { TlGen_Vector.serialize(stream, it) }
      wallpaper?.serializeToStream(stream)
      stories?.serializeToStream(stream)
      business_work_hours?.serializeToStream(stream)
      business_location?.serializeToStream(stream)
      business_greeting_message?.serializeToStream(stream)
      business_away_message?.serializeToStream(stream)
      business_intro?.serializeToStream(stream)
      birthday?.serializeToStream(stream)
      multiflags2_6?.let { stream.writeInt64(it.personal_channel_id) }
      multiflags2_6?.let { stream.writeInt32(it.personal_channel_message) }
      stargifts_count?.let { stream.writeInt32(it) }
    }

    public data class Multiflags2_6(
      public val personal_channel_id: Long,
      public val personal_channel_message: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x1F58E369U
    }
  }

  public data class TL_userFull_layer195(
    public val blocked: Boolean,
    public val phone_calls_available: Boolean,
    public val phone_calls_private: Boolean,
    public val can_pin_message: Boolean,
    public val has_scheduled: Boolean,
    public val video_calls_available: Boolean,
    public val voice_messages_forbidden: Boolean,
    public val translations_disabled: Boolean,
    public val stories_pinned_available: Boolean,
    public val blocked_my_stories_from: Boolean,
    public val wallpaper_overridden: Boolean,
    public val contact_require_premium: Boolean,
    public val read_dates_private: Boolean,
    public val sponsored_enabled: Boolean,
    public val can_view_revenue: Boolean,
    public val bot_can_manage_emoji_status: Boolean,
    public val id: Long,
    public val about: String?,
    public val settings: TlGen_PeerSettings,
    public val personal_photo: TlGen_Photo?,
    public val profile_photo: TlGen_Photo?,
    public val fallback_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
    public val pinned_msg_id: Int?,
    public val common_chats_count: Int,
    public val folder_id: Int?,
    public val ttl_period: Int?,
    public val theme_emoticon: String?,
    public val private_forward_name: String?,
    public val bot_group_admin_rights: TlGen_ChatAdminRights?,
    public val bot_broadcast_admin_rights: TlGen_ChatAdminRights?,
    public val premium_gifts: List<TlGen_PremiumGiftOption>?,
    public val wallpaper: TlGen_WallPaper?,
    public val stories: TlGen_PeerStories?,
    public val business_work_hours: TlGen_BusinessWorkHours?,
    public val business_location: TlGen_BusinessLocation?,
    public val business_greeting_message: TlGen_BusinessGreetingMessage?,
    public val business_away_message: TlGen_BusinessAwayMessage?,
    public val business_intro: TlGen_BusinessIntro?,
    public val birthday: TlGen_Birthday?,
    public val stargifts_count: Int?,
    public val starref_program: TlGen_StarRefProgram?,
    public val multiflags2_6: Multiflags2_6?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (phone_calls_available) result = result or 16U
        if (phone_calls_private) result = result or 32U
        if (pinned_msg_id != null) result = result or 64U
        if (can_pin_message) result = result or 128U
        if (folder_id != null) result = result or 2048U
        if (has_scheduled) result = result or 4096U
        if (video_calls_available) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (theme_emoticon != null) result = result or 32768U
        if (private_forward_name != null) result = result or 65536U
        if (bot_group_admin_rights != null) result = result or 131072U
        if (bot_broadcast_admin_rights != null) result = result or 262144U
        if (premium_gifts != null) result = result or 524288U
        if (voice_messages_forbidden) result = result or 1048576U
        if (personal_photo != null) result = result or 2097152U
        if (fallback_photo != null) result = result or 4194304U
        if (translations_disabled) result = result or 8388608U
        if (wallpaper != null) result = result or 16777216U
        if (stories != null) result = result or 33554432U
        if (stories_pinned_available) result = result or 67108864U
        if (blocked_my_stories_from) result = result or 134217728U
        if (wallpaper_overridden) result = result or 268435456U
        if (contact_require_premium) result = result or 536870912U
        if (read_dates_private) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (business_work_hours != null) result = result or 1U
        if (business_location != null) result = result or 2U
        if (business_greeting_message != null) result = result or 4U
        if (business_away_message != null) result = result or 8U
        if (business_intro != null) result = result or 16U
        if (birthday != null) result = result or 32U
        if (multiflags2_6 != null) result = result or 64U
        if (sponsored_enabled) result = result or 128U
        if (stargifts_count != null) result = result or 256U
        if (can_view_revenue) result = result or 512U
        if (bot_can_manage_emoji_status) result = result or 1024U
        if (starref_program != null) result = result or 2048U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      about?.let { stream.writeString(it) }
      settings.serializeToStream(stream)
      personal_photo?.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      fallback_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
      pinned_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(common_chats_count)
      folder_id?.let { stream.writeInt32(it) }
      ttl_period?.let { stream.writeInt32(it) }
      theme_emoticon?.let { stream.writeString(it) }
      private_forward_name?.let { stream.writeString(it) }
      bot_group_admin_rights?.serializeToStream(stream)
      bot_broadcast_admin_rights?.serializeToStream(stream)
      premium_gifts?.let { TlGen_Vector.serialize(stream, it) }
      wallpaper?.serializeToStream(stream)
      stories?.serializeToStream(stream)
      business_work_hours?.serializeToStream(stream)
      business_location?.serializeToStream(stream)
      business_greeting_message?.serializeToStream(stream)
      business_away_message?.serializeToStream(stream)
      business_intro?.serializeToStream(stream)
      birthday?.serializeToStream(stream)
      multiflags2_6?.let { stream.writeInt64(it.personal_channel_id) }
      multiflags2_6?.let { stream.writeInt32(it.personal_channel_message) }
      stargifts_count?.let { stream.writeInt32(it) }
      starref_program?.serializeToStream(stream)
    }

    public data class Multiflags2_6(
      public val personal_channel_id: Long,
      public val personal_channel_message: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x979D2376U
    }
  }

  public data class TL_userFull_layer199(
    public val blocked: Boolean,
    public val phone_calls_available: Boolean,
    public val phone_calls_private: Boolean,
    public val can_pin_message: Boolean,
    public val has_scheduled: Boolean,
    public val video_calls_available: Boolean,
    public val voice_messages_forbidden: Boolean,
    public val translations_disabled: Boolean,
    public val stories_pinned_available: Boolean,
    public val blocked_my_stories_from: Boolean,
    public val wallpaper_overridden: Boolean,
    public val contact_require_premium: Boolean,
    public val read_dates_private: Boolean,
    public val sponsored_enabled: Boolean,
    public val can_view_revenue: Boolean,
    public val bot_can_manage_emoji_status: Boolean,
    public val id: Long,
    public val about: String?,
    public val settings: TlGen_PeerSettings,
    public val personal_photo: TlGen_Photo?,
    public val profile_photo: TlGen_Photo?,
    public val fallback_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
    public val pinned_msg_id: Int?,
    public val common_chats_count: Int,
    public val folder_id: Int?,
    public val ttl_period: Int?,
    public val theme_emoticon: String?,
    public val private_forward_name: String?,
    public val bot_group_admin_rights: TlGen_ChatAdminRights?,
    public val bot_broadcast_admin_rights: TlGen_ChatAdminRights?,
    public val premium_gifts: List<TlGen_PremiumGiftOption>?,
    public val wallpaper: TlGen_WallPaper?,
    public val stories: TlGen_PeerStories?,
    public val business_work_hours: TlGen_BusinessWorkHours?,
    public val business_location: TlGen_BusinessLocation?,
    public val business_greeting_message: TlGen_BusinessGreetingMessage?,
    public val business_away_message: TlGen_BusinessAwayMessage?,
    public val business_intro: TlGen_BusinessIntro?,
    public val birthday: TlGen_Birthday?,
    public val stargifts_count: Int?,
    public val starref_program: TlGen_StarRefProgram?,
    public val bot_verification: TlGen_BotVerification?,
    public val multiflags2_6: Multiflags2_6?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (phone_calls_available) result = result or 16U
        if (phone_calls_private) result = result or 32U
        if (pinned_msg_id != null) result = result or 64U
        if (can_pin_message) result = result or 128U
        if (folder_id != null) result = result or 2048U
        if (has_scheduled) result = result or 4096U
        if (video_calls_available) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (theme_emoticon != null) result = result or 32768U
        if (private_forward_name != null) result = result or 65536U
        if (bot_group_admin_rights != null) result = result or 131072U
        if (bot_broadcast_admin_rights != null) result = result or 262144U
        if (premium_gifts != null) result = result or 524288U
        if (voice_messages_forbidden) result = result or 1048576U
        if (personal_photo != null) result = result or 2097152U
        if (fallback_photo != null) result = result or 4194304U
        if (translations_disabled) result = result or 8388608U
        if (wallpaper != null) result = result or 16777216U
        if (stories != null) result = result or 33554432U
        if (stories_pinned_available) result = result or 67108864U
        if (blocked_my_stories_from) result = result or 134217728U
        if (wallpaper_overridden) result = result or 268435456U
        if (contact_require_premium) result = result or 536870912U
        if (read_dates_private) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (business_work_hours != null) result = result or 1U
        if (business_location != null) result = result or 2U
        if (business_greeting_message != null) result = result or 4U
        if (business_away_message != null) result = result or 8U
        if (business_intro != null) result = result or 16U
        if (birthday != null) result = result or 32U
        if (multiflags2_6 != null) result = result or 64U
        if (sponsored_enabled) result = result or 128U
        if (stargifts_count != null) result = result or 256U
        if (can_view_revenue) result = result or 512U
        if (bot_can_manage_emoji_status) result = result or 1024U
        if (starref_program != null) result = result or 2048U
        if (bot_verification != null) result = result or 4096U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      about?.let { stream.writeString(it) }
      settings.serializeToStream(stream)
      personal_photo?.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      fallback_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
      pinned_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(common_chats_count)
      folder_id?.let { stream.writeInt32(it) }
      ttl_period?.let { stream.writeInt32(it) }
      theme_emoticon?.let { stream.writeString(it) }
      private_forward_name?.let { stream.writeString(it) }
      bot_group_admin_rights?.serializeToStream(stream)
      bot_broadcast_admin_rights?.serializeToStream(stream)
      premium_gifts?.let { TlGen_Vector.serialize(stream, it) }
      wallpaper?.serializeToStream(stream)
      stories?.serializeToStream(stream)
      business_work_hours?.serializeToStream(stream)
      business_location?.serializeToStream(stream)
      business_greeting_message?.serializeToStream(stream)
      business_away_message?.serializeToStream(stream)
      business_intro?.serializeToStream(stream)
      birthday?.serializeToStream(stream)
      multiflags2_6?.let { stream.writeInt64(it.personal_channel_id) }
      multiflags2_6?.let { stream.writeInt32(it.personal_channel_message) }
      stargifts_count?.let { stream.writeInt32(it) }
      starref_program?.serializeToStream(stream)
      bot_verification?.serializeToStream(stream)
    }

    public data class Multiflags2_6(
      public val personal_channel_id: Long,
      public val personal_channel_message: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x4D975BBCU
    }
  }

  public data class TL_userFull_layer200(
    public val blocked: Boolean,
    public val phone_calls_available: Boolean,
    public val phone_calls_private: Boolean,
    public val can_pin_message: Boolean,
    public val has_scheduled: Boolean,
    public val video_calls_available: Boolean,
    public val voice_messages_forbidden: Boolean,
    public val translations_disabled: Boolean,
    public val stories_pinned_available: Boolean,
    public val blocked_my_stories_from: Boolean,
    public val wallpaper_overridden: Boolean,
    public val contact_require_premium: Boolean,
    public val read_dates_private: Boolean,
    public val sponsored_enabled: Boolean,
    public val can_view_revenue: Boolean,
    public val bot_can_manage_emoji_status: Boolean,
    public val id: Long,
    public val about: String?,
    public val settings: TlGen_PeerSettings,
    public val personal_photo: TlGen_Photo?,
    public val profile_photo: TlGen_Photo?,
    public val fallback_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
    public val pinned_msg_id: Int?,
    public val common_chats_count: Int,
    public val folder_id: Int?,
    public val ttl_period: Int?,
    public val theme_emoticon: String?,
    public val private_forward_name: String?,
    public val bot_group_admin_rights: TlGen_ChatAdminRights?,
    public val bot_broadcast_admin_rights: TlGen_ChatAdminRights?,
    public val wallpaper: TlGen_WallPaper?,
    public val stories: TlGen_PeerStories?,
    public val business_work_hours: TlGen_BusinessWorkHours?,
    public val business_location: TlGen_BusinessLocation?,
    public val business_greeting_message: TlGen_BusinessGreetingMessage?,
    public val business_away_message: TlGen_BusinessAwayMessage?,
    public val business_intro: TlGen_BusinessIntro?,
    public val birthday: TlGen_Birthday?,
    public val stargifts_count: Int?,
    public val starref_program: TlGen_StarRefProgram?,
    public val bot_verification: TlGen_BotVerification?,
    public val send_paid_messages_stars: Long?,
    public val multiflags2_6: Multiflags2_6?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (phone_calls_available) result = result or 16U
        if (phone_calls_private) result = result or 32U
        if (pinned_msg_id != null) result = result or 64U
        if (can_pin_message) result = result or 128U
        if (folder_id != null) result = result or 2048U
        if (has_scheduled) result = result or 4096U
        if (video_calls_available) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (theme_emoticon != null) result = result or 32768U
        if (private_forward_name != null) result = result or 65536U
        if (bot_group_admin_rights != null) result = result or 131072U
        if (bot_broadcast_admin_rights != null) result = result or 262144U
        if (voice_messages_forbidden) result = result or 1048576U
        if (personal_photo != null) result = result or 2097152U
        if (fallback_photo != null) result = result or 4194304U
        if (translations_disabled) result = result or 8388608U
        if (wallpaper != null) result = result or 16777216U
        if (stories != null) result = result or 33554432U
        if (stories_pinned_available) result = result or 67108864U
        if (blocked_my_stories_from) result = result or 134217728U
        if (wallpaper_overridden) result = result or 268435456U
        if (contact_require_premium) result = result or 536870912U
        if (read_dates_private) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (business_work_hours != null) result = result or 1U
        if (business_location != null) result = result or 2U
        if (business_greeting_message != null) result = result or 4U
        if (business_away_message != null) result = result or 8U
        if (business_intro != null) result = result or 16U
        if (birthday != null) result = result or 32U
        if (multiflags2_6 != null) result = result or 64U
        if (sponsored_enabled) result = result or 128U
        if (stargifts_count != null) result = result or 256U
        if (can_view_revenue) result = result or 512U
        if (bot_can_manage_emoji_status) result = result or 1024U
        if (starref_program != null) result = result or 2048U
        if (bot_verification != null) result = result or 4096U
        if (send_paid_messages_stars != null) result = result or 16384U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      about?.let { stream.writeString(it) }
      settings.serializeToStream(stream)
      personal_photo?.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      fallback_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
      pinned_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(common_chats_count)
      folder_id?.let { stream.writeInt32(it) }
      ttl_period?.let { stream.writeInt32(it) }
      theme_emoticon?.let { stream.writeString(it) }
      private_forward_name?.let { stream.writeString(it) }
      bot_group_admin_rights?.serializeToStream(stream)
      bot_broadcast_admin_rights?.serializeToStream(stream)
      wallpaper?.serializeToStream(stream)
      stories?.serializeToStream(stream)
      business_work_hours?.serializeToStream(stream)
      business_location?.serializeToStream(stream)
      business_greeting_message?.serializeToStream(stream)
      business_away_message?.serializeToStream(stream)
      business_intro?.serializeToStream(stream)
      birthday?.serializeToStream(stream)
      multiflags2_6?.let { stream.writeInt64(it.personal_channel_id) }
      multiflags2_6?.let { stream.writeInt32(it.personal_channel_message) }
      stargifts_count?.let { stream.writeInt32(it) }
      starref_program?.serializeToStream(stream)
      bot_verification?.serializeToStream(stream)
      send_paid_messages_stars?.let { stream.writeInt64(it) }
    }

    public data class Multiflags2_6(
      public val personal_channel_id: Long,
      public val personal_channel_message: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xD2234EA0U
    }
  }

  public data class TL_userFull_layer209(
    public val blocked: Boolean,
    public val phone_calls_available: Boolean,
    public val phone_calls_private: Boolean,
    public val can_pin_message: Boolean,
    public val has_scheduled: Boolean,
    public val video_calls_available: Boolean,
    public val voice_messages_forbidden: Boolean,
    public val translations_disabled: Boolean,
    public val stories_pinned_available: Boolean,
    public val blocked_my_stories_from: Boolean,
    public val wallpaper_overridden: Boolean,
    public val contact_require_premium: Boolean,
    public val read_dates_private: Boolean,
    public val sponsored_enabled: Boolean,
    public val can_view_revenue: Boolean,
    public val bot_can_manage_emoji_status: Boolean,
    public val display_gifts_button: Boolean,
    public val id: Long,
    public val about: String?,
    public val settings: TlGen_PeerSettings,
    public val personal_photo: TlGen_Photo?,
    public val profile_photo: TlGen_Photo?,
    public val fallback_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
    public val pinned_msg_id: Int?,
    public val common_chats_count: Int,
    public val folder_id: Int?,
    public val ttl_period: Int?,
    public val theme_emoticon: String?,
    public val private_forward_name: String?,
    public val bot_group_admin_rights: TlGen_ChatAdminRights?,
    public val bot_broadcast_admin_rights: TlGen_ChatAdminRights?,
    public val wallpaper: TlGen_WallPaper?,
    public val stories: TlGen_PeerStories?,
    public val business_work_hours: TlGen_BusinessWorkHours?,
    public val business_location: TlGen_BusinessLocation?,
    public val business_greeting_message: TlGen_BusinessGreetingMessage?,
    public val business_away_message: TlGen_BusinessAwayMessage?,
    public val business_intro: TlGen_BusinessIntro?,
    public val birthday: TlGen_Birthday?,
    public val stargifts_count: Int?,
    public val starref_program: TlGen_StarRefProgram?,
    public val bot_verification: TlGen_BotVerification?,
    public val send_paid_messages_stars: Long?,
    public val disallowed_gifts: TlGen_DisallowedGiftsSettings?,
    public val multiflags2_6: Multiflags2_6?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (phone_calls_available) result = result or 16U
        if (phone_calls_private) result = result or 32U
        if (pinned_msg_id != null) result = result or 64U
        if (can_pin_message) result = result or 128U
        if (folder_id != null) result = result or 2048U
        if (has_scheduled) result = result or 4096U
        if (video_calls_available) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (theme_emoticon != null) result = result or 32768U
        if (private_forward_name != null) result = result or 65536U
        if (bot_group_admin_rights != null) result = result or 131072U
        if (bot_broadcast_admin_rights != null) result = result or 262144U
        if (voice_messages_forbidden) result = result or 1048576U
        if (personal_photo != null) result = result or 2097152U
        if (fallback_photo != null) result = result or 4194304U
        if (translations_disabled) result = result or 8388608U
        if (wallpaper != null) result = result or 16777216U
        if (stories != null) result = result or 33554432U
        if (stories_pinned_available) result = result or 67108864U
        if (blocked_my_stories_from) result = result or 134217728U
        if (wallpaper_overridden) result = result or 268435456U
        if (contact_require_premium) result = result or 536870912U
        if (read_dates_private) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (business_work_hours != null) result = result or 1U
        if (business_location != null) result = result or 2U
        if (business_greeting_message != null) result = result or 4U
        if (business_away_message != null) result = result or 8U
        if (business_intro != null) result = result or 16U
        if (birthday != null) result = result or 32U
        if (multiflags2_6 != null) result = result or 64U
        if (sponsored_enabled) result = result or 128U
        if (stargifts_count != null) result = result or 256U
        if (can_view_revenue) result = result or 512U
        if (bot_can_manage_emoji_status) result = result or 1024U
        if (starref_program != null) result = result or 2048U
        if (bot_verification != null) result = result or 4096U
        if (send_paid_messages_stars != null) result = result or 16384U
        if (disallowed_gifts != null) result = result or 32768U
        if (display_gifts_button) result = result or 65536U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      about?.let { stream.writeString(it) }
      settings.serializeToStream(stream)
      personal_photo?.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      fallback_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
      pinned_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(common_chats_count)
      folder_id?.let { stream.writeInt32(it) }
      ttl_period?.let { stream.writeInt32(it) }
      theme_emoticon?.let { stream.writeString(it) }
      private_forward_name?.let { stream.writeString(it) }
      bot_group_admin_rights?.serializeToStream(stream)
      bot_broadcast_admin_rights?.serializeToStream(stream)
      wallpaper?.serializeToStream(stream)
      stories?.serializeToStream(stream)
      business_work_hours?.serializeToStream(stream)
      business_location?.serializeToStream(stream)
      business_greeting_message?.serializeToStream(stream)
      business_away_message?.serializeToStream(stream)
      business_intro?.serializeToStream(stream)
      birthday?.serializeToStream(stream)
      multiflags2_6?.let { stream.writeInt64(it.personal_channel_id) }
      multiflags2_6?.let { stream.writeInt32(it.personal_channel_message) }
      stargifts_count?.let { stream.writeInt32(it) }
      starref_program?.serializeToStream(stream)
      bot_verification?.serializeToStream(stream)
      send_paid_messages_stars?.let { stream.writeInt64(it) }
      disallowed_gifts?.serializeToStream(stream)
    }

    public data class Multiflags2_6(
      public val personal_channel_id: Long,
      public val personal_channel_message: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x99E78045U
    }
  }

  public data class TL_userFull_layer210(
    public val blocked: Boolean,
    public val phone_calls_available: Boolean,
    public val phone_calls_private: Boolean,
    public val can_pin_message: Boolean,
    public val has_scheduled: Boolean,
    public val video_calls_available: Boolean,
    public val voice_messages_forbidden: Boolean,
    public val translations_disabled: Boolean,
    public val stories_pinned_available: Boolean,
    public val blocked_my_stories_from: Boolean,
    public val wallpaper_overridden: Boolean,
    public val contact_require_premium: Boolean,
    public val read_dates_private: Boolean,
    public val sponsored_enabled: Boolean,
    public val can_view_revenue: Boolean,
    public val bot_can_manage_emoji_status: Boolean,
    public val display_gifts_button: Boolean,
    public val id: Long,
    public val about: String?,
    public val settings: TlGen_PeerSettings,
    public val personal_photo: TlGen_Photo?,
    public val profile_photo: TlGen_Photo?,
    public val fallback_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
    public val pinned_msg_id: Int?,
    public val common_chats_count: Int,
    public val folder_id: Int?,
    public val ttl_period: Int?,
    public val theme_emoticon: String?,
    public val private_forward_name: String?,
    public val bot_group_admin_rights: TlGen_ChatAdminRights?,
    public val bot_broadcast_admin_rights: TlGen_ChatAdminRights?,
    public val wallpaper: TlGen_WallPaper?,
    public val stories: TlGen_PeerStories?,
    public val business_work_hours: TlGen_BusinessWorkHours?,
    public val business_location: TlGen_BusinessLocation?,
    public val business_greeting_message: TlGen_BusinessGreetingMessage?,
    public val business_away_message: TlGen_BusinessAwayMessage?,
    public val business_intro: TlGen_BusinessIntro?,
    public val birthday: TlGen_Birthday?,
    public val stargifts_count: Int?,
    public val starref_program: TlGen_StarRefProgram?,
    public val bot_verification: TlGen_BotVerification?,
    public val send_paid_messages_stars: Long?,
    public val disallowed_gifts: TlGen_DisallowedGiftsSettings?,
    public val stars_rating: TlGen_StarsRating?,
    public val multiflags2_6: Multiflags2_6?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (phone_calls_available) result = result or 16U
        if (phone_calls_private) result = result or 32U
        if (pinned_msg_id != null) result = result or 64U
        if (can_pin_message) result = result or 128U
        if (folder_id != null) result = result or 2048U
        if (has_scheduled) result = result or 4096U
        if (video_calls_available) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (theme_emoticon != null) result = result or 32768U
        if (private_forward_name != null) result = result or 65536U
        if (bot_group_admin_rights != null) result = result or 131072U
        if (bot_broadcast_admin_rights != null) result = result or 262144U
        if (voice_messages_forbidden) result = result or 1048576U
        if (personal_photo != null) result = result or 2097152U
        if (fallback_photo != null) result = result or 4194304U
        if (translations_disabled) result = result or 8388608U
        if (wallpaper != null) result = result or 16777216U
        if (stories != null) result = result or 33554432U
        if (stories_pinned_available) result = result or 67108864U
        if (blocked_my_stories_from) result = result or 134217728U
        if (wallpaper_overridden) result = result or 268435456U
        if (contact_require_premium) result = result or 536870912U
        if (read_dates_private) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (business_work_hours != null) result = result or 1U
        if (business_location != null) result = result or 2U
        if (business_greeting_message != null) result = result or 4U
        if (business_away_message != null) result = result or 8U
        if (business_intro != null) result = result or 16U
        if (birthday != null) result = result or 32U
        if (multiflags2_6 != null) result = result or 64U
        if (sponsored_enabled) result = result or 128U
        if (stargifts_count != null) result = result or 256U
        if (can_view_revenue) result = result or 512U
        if (bot_can_manage_emoji_status) result = result or 1024U
        if (starref_program != null) result = result or 2048U
        if (bot_verification != null) result = result or 4096U
        if (send_paid_messages_stars != null) result = result or 16384U
        if (disallowed_gifts != null) result = result or 32768U
        if (display_gifts_button) result = result or 65536U
        if (stars_rating != null) result = result or 131072U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      about?.let { stream.writeString(it) }
      settings.serializeToStream(stream)
      personal_photo?.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      fallback_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
      pinned_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(common_chats_count)
      folder_id?.let { stream.writeInt32(it) }
      ttl_period?.let { stream.writeInt32(it) }
      theme_emoticon?.let { stream.writeString(it) }
      private_forward_name?.let { stream.writeString(it) }
      bot_group_admin_rights?.serializeToStream(stream)
      bot_broadcast_admin_rights?.serializeToStream(stream)
      wallpaper?.serializeToStream(stream)
      stories?.serializeToStream(stream)
      business_work_hours?.serializeToStream(stream)
      business_location?.serializeToStream(stream)
      business_greeting_message?.serializeToStream(stream)
      business_away_message?.serializeToStream(stream)
      business_intro?.serializeToStream(stream)
      birthday?.serializeToStream(stream)
      multiflags2_6?.let { stream.writeInt64(it.personal_channel_id) }
      multiflags2_6?.let { stream.writeInt32(it.personal_channel_message) }
      stargifts_count?.let { stream.writeInt32(it) }
      starref_program?.serializeToStream(stream)
      bot_verification?.serializeToStream(stream)
      send_paid_messages_stars?.let { stream.writeInt64(it) }
      disallowed_gifts?.serializeToStream(stream)
      stars_rating?.serializeToStream(stream)
    }

    public data class Multiflags2_6(
      public val personal_channel_id: Long,
      public val personal_channel_message: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x29DE80BEU
    }
  }

  public data class TL_userFull_layer212(
    public val blocked: Boolean,
    public val phone_calls_available: Boolean,
    public val phone_calls_private: Boolean,
    public val can_pin_message: Boolean,
    public val has_scheduled: Boolean,
    public val video_calls_available: Boolean,
    public val voice_messages_forbidden: Boolean,
    public val translations_disabled: Boolean,
    public val stories_pinned_available: Boolean,
    public val blocked_my_stories_from: Boolean,
    public val wallpaper_overridden: Boolean,
    public val contact_require_premium: Boolean,
    public val read_dates_private: Boolean,
    public val sponsored_enabled: Boolean,
    public val can_view_revenue: Boolean,
    public val bot_can_manage_emoji_status: Boolean,
    public val display_gifts_button: Boolean,
    public val id: Long,
    public val about: String?,
    public val settings: TlGen_PeerSettings,
    public val personal_photo: TlGen_Photo?,
    public val profile_photo: TlGen_Photo?,
    public val fallback_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
    public val pinned_msg_id: Int?,
    public val common_chats_count: Int,
    public val folder_id: Int?,
    public val ttl_period: Int?,
    public val theme_emoticon: String?,
    public val private_forward_name: String?,
    public val bot_group_admin_rights: TlGen_ChatAdminRights?,
    public val bot_broadcast_admin_rights: TlGen_ChatAdminRights?,
    public val wallpaper: TlGen_WallPaper?,
    public val stories: TlGen_PeerStories?,
    public val business_work_hours: TlGen_BusinessWorkHours?,
    public val business_location: TlGen_BusinessLocation?,
    public val business_greeting_message: TlGen_BusinessGreetingMessage?,
    public val business_away_message: TlGen_BusinessAwayMessage?,
    public val business_intro: TlGen_BusinessIntro?,
    public val birthday: TlGen_Birthday?,
    public val stargifts_count: Int?,
    public val starref_program: TlGen_StarRefProgram?,
    public val bot_verification: TlGen_BotVerification?,
    public val send_paid_messages_stars: Long?,
    public val disallowed_gifts: TlGen_DisallowedGiftsSettings?,
    public val stars_rating: TlGen_StarsRating?,
    public val multiflags2_6: Multiflags2_6?,
    public val multiflags2_18: Multiflags2_18?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (phone_calls_available) result = result or 16U
        if (phone_calls_private) result = result or 32U
        if (pinned_msg_id != null) result = result or 64U
        if (can_pin_message) result = result or 128U
        if (folder_id != null) result = result or 2048U
        if (has_scheduled) result = result or 4096U
        if (video_calls_available) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (theme_emoticon != null) result = result or 32768U
        if (private_forward_name != null) result = result or 65536U
        if (bot_group_admin_rights != null) result = result or 131072U
        if (bot_broadcast_admin_rights != null) result = result or 262144U
        if (voice_messages_forbidden) result = result or 1048576U
        if (personal_photo != null) result = result or 2097152U
        if (fallback_photo != null) result = result or 4194304U
        if (translations_disabled) result = result or 8388608U
        if (wallpaper != null) result = result or 16777216U
        if (stories != null) result = result or 33554432U
        if (stories_pinned_available) result = result or 67108864U
        if (blocked_my_stories_from) result = result or 134217728U
        if (wallpaper_overridden) result = result or 268435456U
        if (contact_require_premium) result = result or 536870912U
        if (read_dates_private) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (business_work_hours != null) result = result or 1U
        if (business_location != null) result = result or 2U
        if (business_greeting_message != null) result = result or 4U
        if (business_away_message != null) result = result or 8U
        if (business_intro != null) result = result or 16U
        if (birthday != null) result = result or 32U
        if (multiflags2_6 != null) result = result or 64U
        if (sponsored_enabled) result = result or 128U
        if (stargifts_count != null) result = result or 256U
        if (can_view_revenue) result = result or 512U
        if (bot_can_manage_emoji_status) result = result or 1024U
        if (starref_program != null) result = result or 2048U
        if (bot_verification != null) result = result or 4096U
        if (send_paid_messages_stars != null) result = result or 16384U
        if (disallowed_gifts != null) result = result or 32768U
        if (display_gifts_button) result = result or 65536U
        if (stars_rating != null) result = result or 131072U
        if (multiflags2_18 != null) result = result or 262144U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      about?.let { stream.writeString(it) }
      settings.serializeToStream(stream)
      personal_photo?.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      fallback_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
      pinned_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(common_chats_count)
      folder_id?.let { stream.writeInt32(it) }
      ttl_period?.let { stream.writeInt32(it) }
      theme_emoticon?.let { stream.writeString(it) }
      private_forward_name?.let { stream.writeString(it) }
      bot_group_admin_rights?.serializeToStream(stream)
      bot_broadcast_admin_rights?.serializeToStream(stream)
      wallpaper?.serializeToStream(stream)
      stories?.serializeToStream(stream)
      business_work_hours?.serializeToStream(stream)
      business_location?.serializeToStream(stream)
      business_greeting_message?.serializeToStream(stream)
      business_away_message?.serializeToStream(stream)
      business_intro?.serializeToStream(stream)
      birthday?.serializeToStream(stream)
      multiflags2_6?.let { stream.writeInt64(it.personal_channel_id) }
      multiflags2_6?.let { stream.writeInt32(it.personal_channel_message) }
      stargifts_count?.let { stream.writeInt32(it) }
      starref_program?.serializeToStream(stream)
      bot_verification?.serializeToStream(stream)
      send_paid_messages_stars?.let { stream.writeInt64(it) }
      disallowed_gifts?.serializeToStream(stream)
      stars_rating?.serializeToStream(stream)
      multiflags2_18?.let { it.stars_my_pending_rating.serializeToStream(stream) }
      multiflags2_18?.let { stream.writeInt32(it.stars_my_pending_rating_date) }
    }

    public data class Multiflags2_6(
      public val personal_channel_id: Long,
      public val personal_channel_message: Int,
    )

    public data class Multiflags2_18(
      public val stars_my_pending_rating: TlGen_StarsRating,
      public val stars_my_pending_rating_date: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x7E63CE1FU
    }
  }

  public data class TL_userFull_layer213(
    public val blocked: Boolean,
    public val phone_calls_available: Boolean,
    public val phone_calls_private: Boolean,
    public val can_pin_message: Boolean,
    public val has_scheduled: Boolean,
    public val video_calls_available: Boolean,
    public val voice_messages_forbidden: Boolean,
    public val translations_disabled: Boolean,
    public val stories_pinned_available: Boolean,
    public val blocked_my_stories_from: Boolean,
    public val wallpaper_overridden: Boolean,
    public val contact_require_premium: Boolean,
    public val read_dates_private: Boolean,
    public val sponsored_enabled: Boolean,
    public val can_view_revenue: Boolean,
    public val bot_can_manage_emoji_status: Boolean,
    public val display_gifts_button: Boolean,
    public val id: Long,
    public val about: String?,
    public val settings: TlGen_PeerSettings,
    public val personal_photo: TlGen_Photo?,
    public val profile_photo: TlGen_Photo?,
    public val fallback_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
    public val pinned_msg_id: Int?,
    public val common_chats_count: Int,
    public val folder_id: Int?,
    public val ttl_period: Int?,
    public val theme_emoticon: String?,
    public val private_forward_name: String?,
    public val bot_group_admin_rights: TlGen_ChatAdminRights?,
    public val bot_broadcast_admin_rights: TlGen_ChatAdminRights?,
    public val wallpaper: TlGen_WallPaper?,
    public val stories: TlGen_PeerStories?,
    public val business_work_hours: TlGen_BusinessWorkHours?,
    public val business_location: TlGen_BusinessLocation?,
    public val business_greeting_message: TlGen_BusinessGreetingMessage?,
    public val business_away_message: TlGen_BusinessAwayMessage?,
    public val business_intro: TlGen_BusinessIntro?,
    public val birthday: TlGen_Birthday?,
    public val stargifts_count: Int?,
    public val starref_program: TlGen_StarRefProgram?,
    public val bot_verification: TlGen_BotVerification?,
    public val send_paid_messages_stars: Long?,
    public val disallowed_gifts: TlGen_DisallowedGiftsSettings?,
    public val stars_rating: TlGen_StarsRating?,
    public val main_tab: TlGen_ProfileTab?,
    public val saved_music: TlGen_Document?,
    public val multiflags2_6: Multiflags2_6?,
    public val multiflags2_18: Multiflags2_18?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (phone_calls_available) result = result or 16U
        if (phone_calls_private) result = result or 32U
        if (pinned_msg_id != null) result = result or 64U
        if (can_pin_message) result = result or 128U
        if (folder_id != null) result = result or 2048U
        if (has_scheduled) result = result or 4096U
        if (video_calls_available) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (theme_emoticon != null) result = result or 32768U
        if (private_forward_name != null) result = result or 65536U
        if (bot_group_admin_rights != null) result = result or 131072U
        if (bot_broadcast_admin_rights != null) result = result or 262144U
        if (voice_messages_forbidden) result = result or 1048576U
        if (personal_photo != null) result = result or 2097152U
        if (fallback_photo != null) result = result or 4194304U
        if (translations_disabled) result = result or 8388608U
        if (wallpaper != null) result = result or 16777216U
        if (stories != null) result = result or 33554432U
        if (stories_pinned_available) result = result or 67108864U
        if (blocked_my_stories_from) result = result or 134217728U
        if (wallpaper_overridden) result = result or 268435456U
        if (contact_require_premium) result = result or 536870912U
        if (read_dates_private) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (business_work_hours != null) result = result or 1U
        if (business_location != null) result = result or 2U
        if (business_greeting_message != null) result = result or 4U
        if (business_away_message != null) result = result or 8U
        if (business_intro != null) result = result or 16U
        if (birthday != null) result = result or 32U
        if (multiflags2_6 != null) result = result or 64U
        if (sponsored_enabled) result = result or 128U
        if (stargifts_count != null) result = result or 256U
        if (can_view_revenue) result = result or 512U
        if (bot_can_manage_emoji_status) result = result or 1024U
        if (starref_program != null) result = result or 2048U
        if (bot_verification != null) result = result or 4096U
        if (send_paid_messages_stars != null) result = result or 16384U
        if (disallowed_gifts != null) result = result or 32768U
        if (display_gifts_button) result = result or 65536U
        if (stars_rating != null) result = result or 131072U
        if (multiflags2_18 != null) result = result or 262144U
        if (main_tab != null) result = result or 1048576U
        if (saved_music != null) result = result or 2097152U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      about?.let { stream.writeString(it) }
      settings.serializeToStream(stream)
      personal_photo?.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      fallback_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
      pinned_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(common_chats_count)
      folder_id?.let { stream.writeInt32(it) }
      ttl_period?.let { stream.writeInt32(it) }
      theme_emoticon?.let { stream.writeString(it) }
      private_forward_name?.let { stream.writeString(it) }
      bot_group_admin_rights?.serializeToStream(stream)
      bot_broadcast_admin_rights?.serializeToStream(stream)
      wallpaper?.serializeToStream(stream)
      stories?.serializeToStream(stream)
      business_work_hours?.serializeToStream(stream)
      business_location?.serializeToStream(stream)
      business_greeting_message?.serializeToStream(stream)
      business_away_message?.serializeToStream(stream)
      business_intro?.serializeToStream(stream)
      birthday?.serializeToStream(stream)
      multiflags2_6?.let { stream.writeInt64(it.personal_channel_id) }
      multiflags2_6?.let { stream.writeInt32(it.personal_channel_message) }
      stargifts_count?.let { stream.writeInt32(it) }
      starref_program?.serializeToStream(stream)
      bot_verification?.serializeToStream(stream)
      send_paid_messages_stars?.let { stream.writeInt64(it) }
      disallowed_gifts?.serializeToStream(stream)
      stars_rating?.serializeToStream(stream)
      multiflags2_18?.let { it.stars_my_pending_rating.serializeToStream(stream) }
      multiflags2_18?.let { stream.writeInt32(it.stars_my_pending_rating_date) }
      main_tab?.serializeToStream(stream)
      saved_music?.serializeToStream(stream)
    }

    public data class Multiflags2_6(
      public val personal_channel_id: Long,
      public val personal_channel_message: Int,
    )

    public data class Multiflags2_18(
      public val stars_my_pending_rating: TlGen_StarsRating,
      public val stars_my_pending_rating_date: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x3FD81E28U
    }
  }

  public data class TL_userFull_layer215(
    public val blocked: Boolean,
    public val phone_calls_available: Boolean,
    public val phone_calls_private: Boolean,
    public val can_pin_message: Boolean,
    public val has_scheduled: Boolean,
    public val video_calls_available: Boolean,
    public val voice_messages_forbidden: Boolean,
    public val translations_disabled: Boolean,
    public val stories_pinned_available: Boolean,
    public val blocked_my_stories_from: Boolean,
    public val wallpaper_overridden: Boolean,
    public val contact_require_premium: Boolean,
    public val read_dates_private: Boolean,
    public val sponsored_enabled: Boolean,
    public val can_view_revenue: Boolean,
    public val bot_can_manage_emoji_status: Boolean,
    public val display_gifts_button: Boolean,
    public val id: Long,
    public val about: String?,
    public val settings: TlGen_PeerSettings,
    public val personal_photo: TlGen_Photo?,
    public val profile_photo: TlGen_Photo?,
    public val fallback_photo: TlGen_Photo?,
    public val notify_settings: TlGen_PeerNotifySettings,
    public val bot_info: TlGen_BotInfo?,
    public val pinned_msg_id: Int?,
    public val common_chats_count: Int,
    public val folder_id: Int?,
    public val ttl_period: Int?,
    public val theme: TlGen_ChatTheme?,
    public val private_forward_name: String?,
    public val bot_group_admin_rights: TlGen_ChatAdminRights?,
    public val bot_broadcast_admin_rights: TlGen_ChatAdminRights?,
    public val wallpaper: TlGen_WallPaper?,
    public val stories: TlGen_PeerStories?,
    public val business_work_hours: TlGen_BusinessWorkHours?,
    public val business_location: TlGen_BusinessLocation?,
    public val business_greeting_message: TlGen_BusinessGreetingMessage?,
    public val business_away_message: TlGen_BusinessAwayMessage?,
    public val business_intro: TlGen_BusinessIntro?,
    public val birthday: TlGen_Birthday?,
    public val stargifts_count: Int?,
    public val starref_program: TlGen_StarRefProgram?,
    public val bot_verification: TlGen_BotVerification?,
    public val send_paid_messages_stars: Long?,
    public val disallowed_gifts: TlGen_DisallowedGiftsSettings?,
    public val stars_rating: TlGen_StarsRating?,
    public val main_tab: TlGen_ProfileTab?,
    public val saved_music: TlGen_Document?,
    public val multiflags2_6: Multiflags2_6?,
    public val multiflags2_18: Multiflags2_18?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (about != null) result = result or 2U
        if (profile_photo != null) result = result or 4U
        if (bot_info != null) result = result or 8U
        if (phone_calls_available) result = result or 16U
        if (phone_calls_private) result = result or 32U
        if (pinned_msg_id != null) result = result or 64U
        if (can_pin_message) result = result or 128U
        if (folder_id != null) result = result or 2048U
        if (has_scheduled) result = result or 4096U
        if (video_calls_available) result = result or 8192U
        if (ttl_period != null) result = result or 16384U
        if (theme != null) result = result or 32768U
        if (private_forward_name != null) result = result or 65536U
        if (bot_group_admin_rights != null) result = result or 131072U
        if (bot_broadcast_admin_rights != null) result = result or 262144U
        if (voice_messages_forbidden) result = result or 1048576U
        if (personal_photo != null) result = result or 2097152U
        if (fallback_photo != null) result = result or 4194304U
        if (translations_disabled) result = result or 8388608U
        if (wallpaper != null) result = result or 16777216U
        if (stories != null) result = result or 33554432U
        if (stories_pinned_available) result = result or 67108864U
        if (blocked_my_stories_from) result = result or 134217728U
        if (wallpaper_overridden) result = result or 268435456U
        if (contact_require_premium) result = result or 536870912U
        if (read_dates_private) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (business_work_hours != null) result = result or 1U
        if (business_location != null) result = result or 2U
        if (business_greeting_message != null) result = result or 4U
        if (business_away_message != null) result = result or 8U
        if (business_intro != null) result = result or 16U
        if (birthday != null) result = result or 32U
        if (multiflags2_6 != null) result = result or 64U
        if (sponsored_enabled) result = result or 128U
        if (stargifts_count != null) result = result or 256U
        if (can_view_revenue) result = result or 512U
        if (bot_can_manage_emoji_status) result = result or 1024U
        if (starref_program != null) result = result or 2048U
        if (bot_verification != null) result = result or 4096U
        if (send_paid_messages_stars != null) result = result or 16384U
        if (disallowed_gifts != null) result = result or 32768U
        if (display_gifts_button) result = result or 65536U
        if (stars_rating != null) result = result or 131072U
        if (multiflags2_18 != null) result = result or 262144U
        if (main_tab != null) result = result or 1048576U
        if (saved_music != null) result = result or 2097152U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      about?.let { stream.writeString(it) }
      settings.serializeToStream(stream)
      personal_photo?.serializeToStream(stream)
      profile_photo?.serializeToStream(stream)
      fallback_photo?.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
      bot_info?.serializeToStream(stream)
      pinned_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(common_chats_count)
      folder_id?.let { stream.writeInt32(it) }
      ttl_period?.let { stream.writeInt32(it) }
      theme?.serializeToStream(stream)
      private_forward_name?.let { stream.writeString(it) }
      bot_group_admin_rights?.serializeToStream(stream)
      bot_broadcast_admin_rights?.serializeToStream(stream)
      wallpaper?.serializeToStream(stream)
      stories?.serializeToStream(stream)
      business_work_hours?.serializeToStream(stream)
      business_location?.serializeToStream(stream)
      business_greeting_message?.serializeToStream(stream)
      business_away_message?.serializeToStream(stream)
      business_intro?.serializeToStream(stream)
      birthday?.serializeToStream(stream)
      multiflags2_6?.let { stream.writeInt64(it.personal_channel_id) }
      multiflags2_6?.let { stream.writeInt32(it.personal_channel_message) }
      stargifts_count?.let { stream.writeInt32(it) }
      starref_program?.serializeToStream(stream)
      bot_verification?.serializeToStream(stream)
      send_paid_messages_stars?.let { stream.writeInt64(it) }
      disallowed_gifts?.serializeToStream(stream)
      stars_rating?.serializeToStream(stream)
      multiflags2_18?.let { it.stars_my_pending_rating.serializeToStream(stream) }
      multiflags2_18?.let { stream.writeInt32(it.stars_my_pending_rating_date) }
      main_tab?.serializeToStream(stream)
      saved_music?.serializeToStream(stream)
    }

    public data class Multiflags2_6(
      public val personal_channel_id: Long,
      public val personal_channel_message: Int,
    )

    public data class Multiflags2_18(
      public val stars_my_pending_rating: TlGen_StarsRating,
      public val stars_my_pending_rating_date: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xC577B5ADU
    }
  }
}

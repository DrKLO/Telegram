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

public sealed class TlGen_User : TlGen_Object {
  public data class TL_userEmpty(
    public val id: Long,
  ) : TlGen_User() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD3BC4B7AU
    }
  }

  public data class TL_user(
    public val self: Boolean,
    public val contact: Boolean,
    public val mutual_contact: Boolean,
    public val deleted: Boolean,
    public val bot_chat_history: Boolean,
    public val bot_nochats: Boolean,
    public val verified: Boolean,
    public val min: Boolean,
    public val bot_inline_geo: Boolean,
    public val support: Boolean,
    public val scam: Boolean,
    public val apply_min_photo: Boolean,
    public val fake: Boolean,
    public val bot_attach_menu: Boolean,
    public val premium: Boolean,
    public val attach_menu_enabled: Boolean,
    public val bot_can_edit: Boolean,
    public val close_friend: Boolean,
    public val stories_hidden: Boolean,
    public val stories_unavailable: Boolean,
    public val contact_require_premium: Boolean,
    public val bot_business: Boolean,
    public val bot_has_main_app: Boolean,
    public val bot_forum_view: Boolean,
    public val id: Long,
    public val access_hash: Long?,
    public val first_name: String?,
    public val last_name: String?,
    public val username: String?,
    public val phone: String?,
    public val photo: TlGen_UserProfilePhoto?,
    public val status: TlGen_UserStatus?,
    public val bot_info_version: Int?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val bot_inline_placeholder: String?,
    public val lang_code: String?,
    public val emoji_status: TlGen_EmojiStatus?,
    public val usernames: List<TlGen_Username>?,
    public val stories_max_id: TlGen_RecentStory?,
    public val color: TlGen_PeerColor?,
    public val profile_color: TlGen_PeerColor?,
    public val bot_active_users: Int?,
    public val bot_verification_icon: Long?,
    public val send_paid_messages_stars: Long?,
  ) : TlGen_User() {
    public val bot: Boolean = bot_info_version != null

    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (access_hash != null) result = result or 1U
        if (first_name != null) result = result or 2U
        if (last_name != null) result = result or 4U
        if (username != null) result = result or 8U
        if (phone != null) result = result or 16U
        if (photo != null) result = result or 32U
        if (status != null) result = result or 64U
        if (self) result = result or 1024U
        if (contact) result = result or 2048U
        if (mutual_contact) result = result or 4096U
        if (deleted) result = result or 8192U
        if (bot) result = result or 16384U
        if (bot_chat_history) result = result or 32768U
        if (bot_nochats) result = result or 65536U
        if (verified) result = result or 131072U
        if (restricted) result = result or 262144U
        if (bot_inline_placeholder != null) result = result or 524288U
        if (min) result = result or 1048576U
        if (bot_inline_geo) result = result or 2097152U
        if (lang_code != null) result = result or 4194304U
        if (support) result = result or 8388608U
        if (scam) result = result or 16777216U
        if (apply_min_photo) result = result or 33554432U
        if (fake) result = result or 67108864U
        if (bot_attach_menu) result = result or 134217728U
        if (premium) result = result or 268435456U
        if (attach_menu_enabled) result = result or 536870912U
        if (emoji_status != null) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (usernames != null) result = result or 1U
        if (bot_can_edit) result = result or 2U
        if (close_friend) result = result or 4U
        if (stories_hidden) result = result or 8U
        if (stories_unavailable) result = result or 16U
        if (stories_max_id != null) result = result or 32U
        if (color != null) result = result or 256U
        if (profile_color != null) result = result or 512U
        if (contact_require_premium) result = result or 1024U
        if (bot_business) result = result or 2048U
        if (bot_active_users != null) result = result or 4096U
        if (bot_has_main_app) result = result or 8192U
        if (bot_verification_icon != null) result = result or 16384U
        if (send_paid_messages_stars != null) result = result or 32768U
        if (bot_forum_view) result = result or 65536U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      access_hash?.let { stream.writeInt64(it) }
      first_name?.let { stream.writeString(it) }
      last_name?.let { stream.writeString(it) }
      username?.let { stream.writeString(it) }
      phone?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      status?.serializeToStream(stream)
      bot_info_version?.let { stream.writeInt32(it) }
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      bot_inline_placeholder?.let { stream.writeString(it) }
      lang_code?.let { stream.writeString(it) }
      emoji_status?.serializeToStream(stream)
      usernames?.let { TlGen_Vector.serialize(stream, it) }
      stories_max_id?.serializeToStream(stream)
      color?.serializeToStream(stream)
      profile_color?.serializeToStream(stream)
      bot_active_users?.let { stream.writeInt32(it) }
      bot_verification_icon?.let { stream.writeInt64(it) }
      send_paid_messages_stars?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x31774388U
    }
  }

  public data class TL_userEmpty_layer132(
    public val id: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x200250BAU
    }
  }

  public data class TL_userSelf_layer17(
    public val id: Int,
    public val first_name: String,
    public val last_name: String,
    public val phone: String,
    public val photo: TlGen_UserProfilePhoto,
    public val status: TlGen_UserStatus,
    public val inactive: Boolean,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeString(first_name)
      stream.writeString(last_name)
      stream.writeString(phone)
      photo.serializeToStream(stream)
      status.serializeToStream(stream)
      stream.writeBool(inactive)
    }

    public companion object {
      public const val MAGIC: UInt = 0x720535ECU
    }
  }

  public data class TL_userContact_layer17(
    public val id: Int,
    public val first_name: String,
    public val last_name: String,
    public val access_hash: Long,
    public val phone: String,
    public val photo: TlGen_UserProfilePhoto,
    public val status: TlGen_UserStatus,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeString(first_name)
      stream.writeString(last_name)
      stream.writeInt64(access_hash)
      stream.writeString(phone)
      photo.serializeToStream(stream)
      status.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF2FB8319U
    }
  }

  public data class TL_userRequest_layer17(
    public val id: Int,
    public val first_name: String,
    public val last_name: String,
    public val access_hash: Long,
    public val phone: String,
    public val photo: TlGen_UserProfilePhoto,
    public val status: TlGen_UserStatus,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeString(first_name)
      stream.writeString(last_name)
      stream.writeInt64(access_hash)
      stream.writeString(phone)
      photo.serializeToStream(stream)
      status.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x22E8CEB0U
    }
  }

  public data class TL_userForeign_layer17(
    public val id: Int,
    public val first_name: String,
    public val last_name: String,
    public val access_hash: Long,
    public val photo: TlGen_UserProfilePhoto,
    public val status: TlGen_UserStatus,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeString(first_name)
      stream.writeString(last_name)
      stream.writeInt64(access_hash)
      photo.serializeToStream(stream)
      status.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5214C89DU
    }
  }

  public data class TL_userDeleted_layer17(
    public val id: Int,
    public val first_name: String,
    public val last_name: String,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeString(first_name)
      stream.writeString(last_name)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB29AD7CCU
    }
  }

  public data class TL_userSelf_layer23(
    public val id: Int,
    public val first_name: String,
    public val last_name: String,
    public val username: String,
    public val phone: String,
    public val photo: TlGen_UserProfilePhoto,
    public val status: TlGen_UserStatus,
    public val inactive: Boolean,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeString(first_name)
      stream.writeString(last_name)
      stream.writeString(username)
      stream.writeString(phone)
      photo.serializeToStream(stream)
      status.serializeToStream(stream)
      stream.writeBool(inactive)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7007B451U
    }
  }

  public data class TL_userContact_layer30(
    public val id: Int,
    public val first_name: String,
    public val last_name: String,
    public val username: String,
    public val access_hash: Long,
    public val phone: String,
    public val photo: TlGen_UserProfilePhoto,
    public val status: TlGen_UserStatus,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeString(first_name)
      stream.writeString(last_name)
      stream.writeString(username)
      stream.writeInt64(access_hash)
      stream.writeString(phone)
      photo.serializeToStream(stream)
      status.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCAB35E18U
    }
  }

  public data class TL_userRequest_layer30(
    public val id: Int,
    public val first_name: String,
    public val last_name: String,
    public val username: String,
    public val access_hash: Long,
    public val phone: String,
    public val photo: TlGen_UserProfilePhoto,
    public val status: TlGen_UserStatus,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeString(first_name)
      stream.writeString(last_name)
      stream.writeString(username)
      stream.writeInt64(access_hash)
      stream.writeString(phone)
      photo.serializeToStream(stream)
      status.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD9CCC4EFU
    }
  }

  public data class TL_userForeign_layer30(
    public val id: Int,
    public val first_name: String,
    public val last_name: String,
    public val username: String,
    public val access_hash: Long,
    public val photo: TlGen_UserProfilePhoto,
    public val status: TlGen_UserStatus,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeString(first_name)
      stream.writeString(last_name)
      stream.writeString(username)
      stream.writeInt64(access_hash)
      photo.serializeToStream(stream)
      status.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x075CF7A8U
    }
  }

  public data class TL_userDeleted_layer30(
    public val id: Int,
    public val first_name: String,
    public val last_name: String,
    public val username: String,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeString(first_name)
      stream.writeString(last_name)
      stream.writeString(username)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD6016D7AU
    }
  }

  public data class TL_userSelf_layer30(
    public val id: Int,
    public val first_name: String,
    public val last_name: String,
    public val username: String,
    public val phone: String,
    public val photo: TlGen_UserProfilePhoto,
    public val status: TlGen_UserStatus,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeString(first_name)
      stream.writeString(last_name)
      stream.writeString(username)
      stream.writeString(phone)
      photo.serializeToStream(stream)
      status.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1C60E608U
    }
  }

  public data class TL_user_layer43(
    public val self: Boolean,
    public val contact: Boolean,
    public val mutual_contact: Boolean,
    public val deleted: Boolean,
    public val bot_chat_history: Boolean,
    public val bot_nochats: Boolean,
    public val verified: Boolean,
    public val explicit_content: Boolean,
    public val id: Int,
    public val access_hash: Long?,
    public val first_name: String?,
    public val last_name: String?,
    public val username: String?,
    public val phone: String?,
    public val photo: TlGen_UserProfilePhoto?,
    public val status: TlGen_UserStatus?,
    public val bot_info_version: Int?,
  ) : TlGen_Object {
    public val bot: Boolean = bot_info_version != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (access_hash != null) result = result or 1U
        if (first_name != null) result = result or 2U
        if (last_name != null) result = result or 4U
        if (username != null) result = result or 8U
        if (phone != null) result = result or 16U
        if (photo != null) result = result or 32U
        if (status != null) result = result or 64U
        if (self) result = result or 1024U
        if (contact) result = result or 2048U
        if (mutual_contact) result = result or 4096U
        if (deleted) result = result or 8192U
        if (bot) result = result or 16384U
        if (bot_chat_history) result = result or 32768U
        if (bot_nochats) result = result or 65536U
        if (verified) result = result or 131072U
        if (explicit_content) result = result or 262144U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      access_hash?.let { stream.writeInt64(it) }
      first_name?.let { stream.writeString(it) }
      last_name?.let { stream.writeString(it) }
      username?.let { stream.writeString(it) }
      phone?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      status?.serializeToStream(stream)
      bot_info_version?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x22E49072U
    }
  }

  public data class TL_user_layer44(
    public val self: Boolean,
    public val contact: Boolean,
    public val mutual_contact: Boolean,
    public val deleted: Boolean,
    public val bot_chat_history: Boolean,
    public val bot_nochats: Boolean,
    public val verified: Boolean,
    public val id: Int,
    public val access_hash: Long?,
    public val first_name: String?,
    public val last_name: String?,
    public val username: String?,
    public val phone: String?,
    public val photo: TlGen_UserProfilePhoto?,
    public val status: TlGen_UserStatus?,
    public val bot_info_version: Int?,
    public val restriction_reason: String?,
  ) : TlGen_Object {
    public val bot: Boolean = bot_info_version != null

    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (access_hash != null) result = result or 1U
        if (first_name != null) result = result or 2U
        if (last_name != null) result = result or 4U
        if (username != null) result = result or 8U
        if (phone != null) result = result or 16U
        if (photo != null) result = result or 32U
        if (status != null) result = result or 64U
        if (self) result = result or 1024U
        if (contact) result = result or 2048U
        if (mutual_contact) result = result or 4096U
        if (deleted) result = result or 8192U
        if (bot) result = result or 16384U
        if (bot_chat_history) result = result or 32768U
        if (bot_nochats) result = result or 65536U
        if (verified) result = result or 131072U
        if (restricted) result = result or 262144U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      access_hash?.let { stream.writeInt64(it) }
      first_name?.let { stream.writeString(it) }
      last_name?.let { stream.writeString(it) }
      username?.let { stream.writeString(it) }
      phone?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      status?.serializeToStream(stream)
      bot_info_version?.let { stream.writeInt32(it) }
      restriction_reason?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x603539B4U
    }
  }

  public data class TL_user_layer65(
    public val self: Boolean,
    public val contact: Boolean,
    public val mutual_contact: Boolean,
    public val deleted: Boolean,
    public val bot_chat_history: Boolean,
    public val bot_nochats: Boolean,
    public val verified: Boolean,
    public val min: Boolean,
    public val bot_inline_geo: Boolean,
    public val id: Int,
    public val access_hash: Long?,
    public val first_name: String?,
    public val last_name: String?,
    public val username: String?,
    public val phone: String?,
    public val photo: TlGen_UserProfilePhoto?,
    public val status: TlGen_UserStatus?,
    public val bot_info_version: Int?,
    public val restriction_reason: String?,
    public val bot_inline_placeholder: String?,
  ) : TlGen_Object {
    public val bot: Boolean = bot_info_version != null

    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (access_hash != null) result = result or 1U
        if (first_name != null) result = result or 2U
        if (last_name != null) result = result or 4U
        if (username != null) result = result or 8U
        if (phone != null) result = result or 16U
        if (photo != null) result = result or 32U
        if (status != null) result = result or 64U
        if (self) result = result or 1024U
        if (contact) result = result or 2048U
        if (mutual_contact) result = result or 4096U
        if (deleted) result = result or 8192U
        if (bot) result = result or 16384U
        if (bot_chat_history) result = result or 32768U
        if (bot_nochats) result = result or 65536U
        if (verified) result = result or 131072U
        if (restricted) result = result or 262144U
        if (bot_inline_placeholder != null) result = result or 524288U
        if (min) result = result or 1048576U
        if (bot_inline_geo) result = result or 2097152U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      access_hash?.let { stream.writeInt64(it) }
      first_name?.let { stream.writeString(it) }
      last_name?.let { stream.writeString(it) }
      username?.let { stream.writeString(it) }
      phone?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      status?.serializeToStream(stream)
      bot_info_version?.let { stream.writeInt32(it) }
      restriction_reason?.let { stream.writeString(it) }
      bot_inline_placeholder?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xD10D979AU
    }
  }

  public data class TL_user_layer104(
    public val self: Boolean,
    public val contact: Boolean,
    public val mutual_contact: Boolean,
    public val deleted: Boolean,
    public val bot_chat_history: Boolean,
    public val bot_nochats: Boolean,
    public val verified: Boolean,
    public val min: Boolean,
    public val bot_inline_geo: Boolean,
    public val support: Boolean,
    public val scam: Boolean,
    public val id: Int,
    public val access_hash: Long?,
    public val first_name: String?,
    public val last_name: String?,
    public val username: String?,
    public val phone: String?,
    public val photo: TlGen_UserProfilePhoto?,
    public val status: TlGen_UserStatus?,
    public val bot_info_version: Int?,
    public val restriction_reason: String?,
    public val bot_inline_placeholder: String?,
    public val lang_code: String?,
  ) : TlGen_Object {
    public val bot: Boolean = bot_info_version != null

    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (access_hash != null) result = result or 1U
        if (first_name != null) result = result or 2U
        if (last_name != null) result = result or 4U
        if (username != null) result = result or 8U
        if (phone != null) result = result or 16U
        if (photo != null) result = result or 32U
        if (status != null) result = result or 64U
        if (self) result = result or 1024U
        if (contact) result = result or 2048U
        if (mutual_contact) result = result or 4096U
        if (deleted) result = result or 8192U
        if (bot) result = result or 16384U
        if (bot_chat_history) result = result or 32768U
        if (bot_nochats) result = result or 65536U
        if (verified) result = result or 131072U
        if (restricted) result = result or 262144U
        if (bot_inline_placeholder != null) result = result or 524288U
        if (min) result = result or 1048576U
        if (bot_inline_geo) result = result or 2097152U
        if (lang_code != null) result = result or 4194304U
        if (support) result = result or 8388608U
        if (scam) result = result or 16777216U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      access_hash?.let { stream.writeInt64(it) }
      first_name?.let { stream.writeString(it) }
      last_name?.let { stream.writeString(it) }
      username?.let { stream.writeString(it) }
      phone?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      status?.serializeToStream(stream)
      bot_info_version?.let { stream.writeInt32(it) }
      restriction_reason?.let { stream.writeString(it) }
      bot_inline_placeholder?.let { stream.writeString(it) }
      lang_code?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x2E13F4C3U
    }
  }

  public data class TL_user_layer132(
    public val self: Boolean,
    public val contact: Boolean,
    public val mutual_contact: Boolean,
    public val deleted: Boolean,
    public val bot_chat_history: Boolean,
    public val bot_nochats: Boolean,
    public val verified: Boolean,
    public val min: Boolean,
    public val bot_inline_geo: Boolean,
    public val support: Boolean,
    public val scam: Boolean,
    public val apply_min_photo: Boolean,
    public val fake: Boolean,
    public val id: Int,
    public val access_hash: Long?,
    public val first_name: String?,
    public val last_name: String?,
    public val username: String?,
    public val phone: String?,
    public val photo: TlGen_UserProfilePhoto?,
    public val status: TlGen_UserStatus?,
    public val bot_info_version: Int?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val bot_inline_placeholder: String?,
    public val lang_code: String?,
  ) : TlGen_Object {
    public val bot: Boolean = bot_info_version != null

    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (access_hash != null) result = result or 1U
        if (first_name != null) result = result or 2U
        if (last_name != null) result = result or 4U
        if (username != null) result = result or 8U
        if (phone != null) result = result or 16U
        if (photo != null) result = result or 32U
        if (status != null) result = result or 64U
        if (self) result = result or 1024U
        if (contact) result = result or 2048U
        if (mutual_contact) result = result or 4096U
        if (deleted) result = result or 8192U
        if (bot) result = result or 16384U
        if (bot_chat_history) result = result or 32768U
        if (bot_nochats) result = result or 65536U
        if (verified) result = result or 131072U
        if (restricted) result = result or 262144U
        if (bot_inline_placeholder != null) result = result or 524288U
        if (min) result = result or 1048576U
        if (bot_inline_geo) result = result or 2097152U
        if (lang_code != null) result = result or 4194304U
        if (support) result = result or 8388608U
        if (scam) result = result or 16777216U
        if (apply_min_photo) result = result or 33554432U
        if (fake) result = result or 67108864U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      access_hash?.let { stream.writeInt64(it) }
      first_name?.let { stream.writeString(it) }
      last_name?.let { stream.writeString(it) }
      username?.let { stream.writeString(it) }
      phone?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      status?.serializeToStream(stream)
      bot_info_version?.let { stream.writeInt32(it) }
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      bot_inline_placeholder?.let { stream.writeString(it) }
      lang_code?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x938458C1U
    }
  }

  public data class TL_user_layer144(
    public val self: Boolean,
    public val contact: Boolean,
    public val mutual_contact: Boolean,
    public val deleted: Boolean,
    public val bot_chat_history: Boolean,
    public val bot_nochats: Boolean,
    public val verified: Boolean,
    public val min: Boolean,
    public val bot_inline_geo: Boolean,
    public val support: Boolean,
    public val scam: Boolean,
    public val apply_min_photo: Boolean,
    public val fake: Boolean,
    public val bot_attach_menu: Boolean,
    public val premium: Boolean,
    public val attach_menu_enabled: Boolean,
    public val id: Long,
    public val access_hash: Long?,
    public val first_name: String?,
    public val last_name: String?,
    public val username: String?,
    public val phone: String?,
    public val photo: TlGen_UserProfilePhoto?,
    public val status: TlGen_UserStatus?,
    public val bot_info_version: Int?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val bot_inline_placeholder: String?,
    public val lang_code: String?,
  ) : TlGen_Object {
    public val bot: Boolean = bot_info_version != null

    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (access_hash != null) result = result or 1U
        if (first_name != null) result = result or 2U
        if (last_name != null) result = result or 4U
        if (username != null) result = result or 8U
        if (phone != null) result = result or 16U
        if (photo != null) result = result or 32U
        if (status != null) result = result or 64U
        if (self) result = result or 1024U
        if (contact) result = result or 2048U
        if (mutual_contact) result = result or 4096U
        if (deleted) result = result or 8192U
        if (bot) result = result or 16384U
        if (bot_chat_history) result = result or 32768U
        if (bot_nochats) result = result or 65536U
        if (verified) result = result or 131072U
        if (restricted) result = result or 262144U
        if (bot_inline_placeholder != null) result = result or 524288U
        if (min) result = result or 1048576U
        if (bot_inline_geo) result = result or 2097152U
        if (lang_code != null) result = result or 4194304U
        if (support) result = result or 8388608U
        if (scam) result = result or 16777216U
        if (apply_min_photo) result = result or 33554432U
        if (fake) result = result or 67108864U
        if (bot_attach_menu) result = result or 134217728U
        if (premium) result = result or 268435456U
        if (attach_menu_enabled) result = result or 536870912U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      access_hash?.let { stream.writeInt64(it) }
      first_name?.let { stream.writeString(it) }
      last_name?.let { stream.writeString(it) }
      username?.let { stream.writeString(it) }
      phone?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      status?.serializeToStream(stream)
      bot_info_version?.let { stream.writeInt32(it) }
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      bot_inline_placeholder?.let { stream.writeString(it) }
      lang_code?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x3FF6ECB0U
    }
  }

  public data class TL_user_layer147(
    public val self: Boolean,
    public val contact: Boolean,
    public val mutual_contact: Boolean,
    public val deleted: Boolean,
    public val bot_chat_history: Boolean,
    public val bot_nochats: Boolean,
    public val verified: Boolean,
    public val min: Boolean,
    public val bot_inline_geo: Boolean,
    public val support: Boolean,
    public val scam: Boolean,
    public val apply_min_photo: Boolean,
    public val fake: Boolean,
    public val bot_attach_menu: Boolean,
    public val premium: Boolean,
    public val attach_menu_enabled: Boolean,
    public val id: Long,
    public val access_hash: Long?,
    public val first_name: String?,
    public val last_name: String?,
    public val username: String?,
    public val phone: String?,
    public val photo: TlGen_UserProfilePhoto?,
    public val status: TlGen_UserStatus?,
    public val bot_info_version: Int?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val bot_inline_placeholder: String?,
    public val lang_code: String?,
    public val emoji_status: TlGen_EmojiStatus?,
  ) : TlGen_Object {
    public val bot: Boolean = bot_info_version != null

    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (access_hash != null) result = result or 1U
        if (first_name != null) result = result or 2U
        if (last_name != null) result = result or 4U
        if (username != null) result = result or 8U
        if (phone != null) result = result or 16U
        if (photo != null) result = result or 32U
        if (status != null) result = result or 64U
        if (self) result = result or 1024U
        if (contact) result = result or 2048U
        if (mutual_contact) result = result or 4096U
        if (deleted) result = result or 8192U
        if (bot) result = result or 16384U
        if (bot_chat_history) result = result or 32768U
        if (bot_nochats) result = result or 65536U
        if (verified) result = result or 131072U
        if (restricted) result = result or 262144U
        if (bot_inline_placeholder != null) result = result or 524288U
        if (min) result = result or 1048576U
        if (bot_inline_geo) result = result or 2097152U
        if (lang_code != null) result = result or 4194304U
        if (support) result = result or 8388608U
        if (scam) result = result or 16777216U
        if (apply_min_photo) result = result or 33554432U
        if (fake) result = result or 67108864U
        if (bot_attach_menu) result = result or 134217728U
        if (premium) result = result or 268435456U
        if (attach_menu_enabled) result = result or 536870912U
        if (emoji_status != null) result = result or 1073741824U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      access_hash?.let { stream.writeInt64(it) }
      first_name?.let { stream.writeString(it) }
      last_name?.let { stream.writeString(it) }
      username?.let { stream.writeString(it) }
      phone?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      status?.serializeToStream(stream)
      bot_info_version?.let { stream.writeInt32(it) }
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      bot_inline_placeholder?.let { stream.writeString(it) }
      lang_code?.let { stream.writeString(it) }
      emoji_status?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5D99ADEEU
    }
  }

  public data class TL_user_layer159(
    public val self: Boolean,
    public val contact: Boolean,
    public val mutual_contact: Boolean,
    public val deleted: Boolean,
    public val bot_chat_history: Boolean,
    public val bot_nochats: Boolean,
    public val verified: Boolean,
    public val min: Boolean,
    public val bot_inline_geo: Boolean,
    public val support: Boolean,
    public val scam: Boolean,
    public val apply_min_photo: Boolean,
    public val fake: Boolean,
    public val bot_attach_menu: Boolean,
    public val premium: Boolean,
    public val attach_menu_enabled: Boolean,
    public val bot_can_edit: Boolean,
    public val id: Long,
    public val access_hash: Long?,
    public val first_name: String?,
    public val last_name: String?,
    public val username: String?,
    public val phone: String?,
    public val photo: TlGen_UserProfilePhoto?,
    public val status: TlGen_UserStatus?,
    public val bot_info_version: Int?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val bot_inline_placeholder: String?,
    public val lang_code: String?,
    public val emoji_status: TlGen_EmojiStatus?,
    public val usernames: List<TlGen_Username>?,
  ) : TlGen_Object {
    public val bot: Boolean = bot_info_version != null

    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (access_hash != null) result = result or 1U
        if (first_name != null) result = result or 2U
        if (last_name != null) result = result or 4U
        if (username != null) result = result or 8U
        if (phone != null) result = result or 16U
        if (photo != null) result = result or 32U
        if (status != null) result = result or 64U
        if (self) result = result or 1024U
        if (contact) result = result or 2048U
        if (mutual_contact) result = result or 4096U
        if (deleted) result = result or 8192U
        if (bot) result = result or 16384U
        if (bot_chat_history) result = result or 32768U
        if (bot_nochats) result = result or 65536U
        if (verified) result = result or 131072U
        if (restricted) result = result or 262144U
        if (bot_inline_placeholder != null) result = result or 524288U
        if (min) result = result or 1048576U
        if (bot_inline_geo) result = result or 2097152U
        if (lang_code != null) result = result or 4194304U
        if (support) result = result or 8388608U
        if (scam) result = result or 16777216U
        if (apply_min_photo) result = result or 33554432U
        if (fake) result = result or 67108864U
        if (bot_attach_menu) result = result or 134217728U
        if (premium) result = result or 268435456U
        if (attach_menu_enabled) result = result or 536870912U
        if (emoji_status != null) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (usernames != null) result = result or 1U
        if (bot_can_edit) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      access_hash?.let { stream.writeInt64(it) }
      first_name?.let { stream.writeString(it) }
      last_name?.let { stream.writeString(it) }
      username?.let { stream.writeString(it) }
      phone?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      status?.serializeToStream(stream)
      bot_info_version?.let { stream.writeInt32(it) }
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      bot_inline_placeholder?.let { stream.writeString(it) }
      lang_code?.let { stream.writeString(it) }
      emoji_status?.serializeToStream(stream)
      usernames?.let { TlGen_Vector.serialize(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x8F97C628U
    }
  }

  public data class TL_user_layer165(
    public val self: Boolean,
    public val contact: Boolean,
    public val mutual_contact: Boolean,
    public val deleted: Boolean,
    public val bot_chat_history: Boolean,
    public val bot_nochats: Boolean,
    public val verified: Boolean,
    public val min: Boolean,
    public val bot_inline_geo: Boolean,
    public val support: Boolean,
    public val scam: Boolean,
    public val apply_min_photo: Boolean,
    public val fake: Boolean,
    public val bot_attach_menu: Boolean,
    public val premium: Boolean,
    public val attach_menu_enabled: Boolean,
    public val bot_can_edit: Boolean,
    public val close_friend: Boolean,
    public val stories_hidden: Boolean,
    public val stories_unavailable: Boolean,
    public val id: Long,
    public val access_hash: Long?,
    public val first_name: String?,
    public val last_name: String?,
    public val username: String?,
    public val phone: String?,
    public val photo: TlGen_UserProfilePhoto?,
    public val status: TlGen_UserStatus?,
    public val bot_info_version: Int?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val bot_inline_placeholder: String?,
    public val lang_code: String?,
    public val emoji_status: TlGen_EmojiStatus?,
    public val usernames: List<TlGen_Username>?,
    public val stories_max_id: Int?,
  ) : TlGen_Object {
    public val bot: Boolean = bot_info_version != null

    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (access_hash != null) result = result or 1U
        if (first_name != null) result = result or 2U
        if (last_name != null) result = result or 4U
        if (username != null) result = result or 8U
        if (phone != null) result = result or 16U
        if (photo != null) result = result or 32U
        if (status != null) result = result or 64U
        if (self) result = result or 1024U
        if (contact) result = result or 2048U
        if (mutual_contact) result = result or 4096U
        if (deleted) result = result or 8192U
        if (bot) result = result or 16384U
        if (bot_chat_history) result = result or 32768U
        if (bot_nochats) result = result or 65536U
        if (verified) result = result or 131072U
        if (restricted) result = result or 262144U
        if (bot_inline_placeholder != null) result = result or 524288U
        if (min) result = result or 1048576U
        if (bot_inline_geo) result = result or 2097152U
        if (lang_code != null) result = result or 4194304U
        if (support) result = result or 8388608U
        if (scam) result = result or 16777216U
        if (apply_min_photo) result = result or 33554432U
        if (fake) result = result or 67108864U
        if (bot_attach_menu) result = result or 134217728U
        if (premium) result = result or 268435456U
        if (attach_menu_enabled) result = result or 536870912U
        if (emoji_status != null) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (usernames != null) result = result or 1U
        if (bot_can_edit) result = result or 2U
        if (close_friend) result = result or 4U
        if (stories_hidden) result = result or 8U
        if (stories_unavailable) result = result or 16U
        if (stories_max_id != null) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      access_hash?.let { stream.writeInt64(it) }
      first_name?.let { stream.writeString(it) }
      last_name?.let { stream.writeString(it) }
      username?.let { stream.writeString(it) }
      phone?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      status?.serializeToStream(stream)
      bot_info_version?.let { stream.writeInt32(it) }
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      bot_inline_placeholder?.let { stream.writeString(it) }
      lang_code?.let { stream.writeString(it) }
      emoji_status?.serializeToStream(stream)
      usernames?.let { TlGen_Vector.serialize(stream, it) }
      stories_max_id?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xABB5F120U
    }
  }

  public data class TL_user_layer184(
    public val self: Boolean,
    public val contact: Boolean,
    public val mutual_contact: Boolean,
    public val deleted: Boolean,
    public val bot_chat_history: Boolean,
    public val bot_nochats: Boolean,
    public val verified: Boolean,
    public val min: Boolean,
    public val bot_inline_geo: Boolean,
    public val support: Boolean,
    public val scam: Boolean,
    public val apply_min_photo: Boolean,
    public val fake: Boolean,
    public val bot_attach_menu: Boolean,
    public val premium: Boolean,
    public val attach_menu_enabled: Boolean,
    public val bot_can_edit: Boolean,
    public val close_friend: Boolean,
    public val stories_hidden: Boolean,
    public val stories_unavailable: Boolean,
    public val contact_require_premium: Boolean,
    public val bot_business: Boolean,
    public val id: Long,
    public val access_hash: Long?,
    public val first_name: String?,
    public val last_name: String?,
    public val username: String?,
    public val phone: String?,
    public val photo: TlGen_UserProfilePhoto?,
    public val status: TlGen_UserStatus?,
    public val bot_info_version: Int?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val bot_inline_placeholder: String?,
    public val lang_code: String?,
    public val emoji_status: TlGen_EmojiStatus?,
    public val usernames: List<TlGen_Username>?,
    public val stories_max_id: Int?,
    public val color: TlGen_PeerColor?,
    public val profile_color: TlGen_PeerColor?,
  ) : TlGen_Object {
    public val bot: Boolean = bot_info_version != null

    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (access_hash != null) result = result or 1U
        if (first_name != null) result = result or 2U
        if (last_name != null) result = result or 4U
        if (username != null) result = result or 8U
        if (phone != null) result = result or 16U
        if (photo != null) result = result or 32U
        if (status != null) result = result or 64U
        if (self) result = result or 1024U
        if (contact) result = result or 2048U
        if (mutual_contact) result = result or 4096U
        if (deleted) result = result or 8192U
        if (bot) result = result or 16384U
        if (bot_chat_history) result = result or 32768U
        if (bot_nochats) result = result or 65536U
        if (verified) result = result or 131072U
        if (restricted) result = result or 262144U
        if (bot_inline_placeholder != null) result = result or 524288U
        if (min) result = result or 1048576U
        if (bot_inline_geo) result = result or 2097152U
        if (lang_code != null) result = result or 4194304U
        if (support) result = result or 8388608U
        if (scam) result = result or 16777216U
        if (apply_min_photo) result = result or 33554432U
        if (fake) result = result or 67108864U
        if (bot_attach_menu) result = result or 134217728U
        if (premium) result = result or 268435456U
        if (attach_menu_enabled) result = result or 536870912U
        if (emoji_status != null) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (usernames != null) result = result or 1U
        if (bot_can_edit) result = result or 2U
        if (close_friend) result = result or 4U
        if (stories_hidden) result = result or 8U
        if (stories_unavailable) result = result or 16U
        if (stories_max_id != null) result = result or 32U
        if (color != null) result = result or 256U
        if (profile_color != null) result = result or 512U
        if (contact_require_premium) result = result or 1024U
        if (bot_business) result = result or 2048U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      access_hash?.let { stream.writeInt64(it) }
      first_name?.let { stream.writeString(it) }
      last_name?.let { stream.writeString(it) }
      username?.let { stream.writeString(it) }
      phone?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      status?.serializeToStream(stream)
      bot_info_version?.let { stream.writeInt32(it) }
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      bot_inline_placeholder?.let { stream.writeString(it) }
      lang_code?.let { stream.writeString(it) }
      emoji_status?.serializeToStream(stream)
      usernames?.let { TlGen_Vector.serialize(stream, it) }
      stories_max_id?.let { stream.writeInt32(it) }
      color?.serializeToStream(stream)
      profile_color?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x215C4438U
    }
  }

  public data class TL_user_layer166(
    public val self: Boolean,
    public val contact: Boolean,
    public val mutual_contact: Boolean,
    public val deleted: Boolean,
    public val bot_chat_history: Boolean,
    public val bot_nochats: Boolean,
    public val verified: Boolean,
    public val min: Boolean,
    public val bot_inline_geo: Boolean,
    public val support: Boolean,
    public val scam: Boolean,
    public val apply_min_photo: Boolean,
    public val fake: Boolean,
    public val bot_attach_menu: Boolean,
    public val premium: Boolean,
    public val attach_menu_enabled: Boolean,
    public val bot_can_edit: Boolean,
    public val close_friend: Boolean,
    public val stories_hidden: Boolean,
    public val stories_unavailable: Boolean,
    public val id: Long,
    public val access_hash: Long?,
    public val first_name: String?,
    public val last_name: String?,
    public val username: String?,
    public val phone: String?,
    public val photo: TlGen_UserProfilePhoto?,
    public val status: TlGen_UserStatus?,
    public val bot_info_version: Int?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val bot_inline_placeholder: String?,
    public val lang_code: String?,
    public val emoji_status: TlGen_EmojiStatus?,
    public val usernames: List<TlGen_Username>?,
    public val stories_max_id: Int?,
    public val color: Int?,
    public val background_emoji_id: Long?,
  ) : TlGen_Object {
    public val bot: Boolean = bot_info_version != null

    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (access_hash != null) result = result or 1U
        if (first_name != null) result = result or 2U
        if (last_name != null) result = result or 4U
        if (username != null) result = result or 8U
        if (phone != null) result = result or 16U
        if (photo != null) result = result or 32U
        if (status != null) result = result or 64U
        if (self) result = result or 1024U
        if (contact) result = result or 2048U
        if (mutual_contact) result = result or 4096U
        if (deleted) result = result or 8192U
        if (bot) result = result or 16384U
        if (bot_chat_history) result = result or 32768U
        if (bot_nochats) result = result or 65536U
        if (verified) result = result or 131072U
        if (restricted) result = result or 262144U
        if (bot_inline_placeholder != null) result = result or 524288U
        if (min) result = result or 1048576U
        if (bot_inline_geo) result = result or 2097152U
        if (lang_code != null) result = result or 4194304U
        if (support) result = result or 8388608U
        if (scam) result = result or 16777216U
        if (apply_min_photo) result = result or 33554432U
        if (fake) result = result or 67108864U
        if (bot_attach_menu) result = result or 134217728U
        if (premium) result = result or 268435456U
        if (attach_menu_enabled) result = result or 536870912U
        if (emoji_status != null) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (usernames != null) result = result or 1U
        if (bot_can_edit) result = result or 2U
        if (close_friend) result = result or 4U
        if (stories_hidden) result = result or 8U
        if (stories_unavailable) result = result or 16U
        if (stories_max_id != null) result = result or 32U
        if (background_emoji_id != null) result = result or 64U
        if (color != null) result = result or 128U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      access_hash?.let { stream.writeInt64(it) }
      first_name?.let { stream.writeString(it) }
      last_name?.let { stream.writeString(it) }
      username?.let { stream.writeString(it) }
      phone?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      status?.serializeToStream(stream)
      bot_info_version?.let { stream.writeInt32(it) }
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      bot_inline_placeholder?.let { stream.writeString(it) }
      lang_code?.let { stream.writeString(it) }
      emoji_status?.serializeToStream(stream)
      usernames?.let { TlGen_Vector.serialize(stream, it) }
      stories_max_id?.let { stream.writeInt32(it) }
      color?.let { stream.writeInt32(it) }
      background_emoji_id?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xEB602F25U
    }
  }

  public data class TL_user_layer195(
    public val self: Boolean,
    public val contact: Boolean,
    public val mutual_contact: Boolean,
    public val deleted: Boolean,
    public val bot_chat_history: Boolean,
    public val bot_nochats: Boolean,
    public val verified: Boolean,
    public val min: Boolean,
    public val bot_inline_geo: Boolean,
    public val support: Boolean,
    public val scam: Boolean,
    public val apply_min_photo: Boolean,
    public val fake: Boolean,
    public val bot_attach_menu: Boolean,
    public val premium: Boolean,
    public val attach_menu_enabled: Boolean,
    public val bot_can_edit: Boolean,
    public val close_friend: Boolean,
    public val stories_hidden: Boolean,
    public val stories_unavailable: Boolean,
    public val contact_require_premium: Boolean,
    public val bot_business: Boolean,
    public val bot_has_main_app: Boolean,
    public val id: Long,
    public val access_hash: Long?,
    public val first_name: String?,
    public val last_name: String?,
    public val username: String?,
    public val phone: String?,
    public val photo: TlGen_UserProfilePhoto?,
    public val status: TlGen_UserStatus?,
    public val bot_info_version: Int?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val bot_inline_placeholder: String?,
    public val lang_code: String?,
    public val emoji_status: TlGen_EmojiStatus?,
    public val usernames: List<TlGen_Username>?,
    public val stories_max_id: Int?,
    public val color: TlGen_PeerColor?,
    public val profile_color: TlGen_PeerColor?,
    public val bot_active_users: Int?,
  ) : TlGen_Object {
    public val bot: Boolean = bot_info_version != null

    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (access_hash != null) result = result or 1U
        if (first_name != null) result = result or 2U
        if (last_name != null) result = result or 4U
        if (username != null) result = result or 8U
        if (phone != null) result = result or 16U
        if (photo != null) result = result or 32U
        if (status != null) result = result or 64U
        if (self) result = result or 1024U
        if (contact) result = result or 2048U
        if (mutual_contact) result = result or 4096U
        if (deleted) result = result or 8192U
        if (bot) result = result or 16384U
        if (bot_chat_history) result = result or 32768U
        if (bot_nochats) result = result or 65536U
        if (verified) result = result or 131072U
        if (restricted) result = result or 262144U
        if (bot_inline_placeholder != null) result = result or 524288U
        if (min) result = result or 1048576U
        if (bot_inline_geo) result = result or 2097152U
        if (lang_code != null) result = result or 4194304U
        if (support) result = result or 8388608U
        if (scam) result = result or 16777216U
        if (apply_min_photo) result = result or 33554432U
        if (fake) result = result or 67108864U
        if (bot_attach_menu) result = result or 134217728U
        if (premium) result = result or 268435456U
        if (attach_menu_enabled) result = result or 536870912U
        if (emoji_status != null) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (usernames != null) result = result or 1U
        if (bot_can_edit) result = result or 2U
        if (close_friend) result = result or 4U
        if (stories_hidden) result = result or 8U
        if (stories_unavailable) result = result or 16U
        if (stories_max_id != null) result = result or 32U
        if (color != null) result = result or 256U
        if (profile_color != null) result = result or 512U
        if (contact_require_premium) result = result or 1024U
        if (bot_business) result = result or 2048U
        if (bot_active_users != null) result = result or 4096U
        if (bot_has_main_app) result = result or 8192U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      access_hash?.let { stream.writeInt64(it) }
      first_name?.let { stream.writeString(it) }
      last_name?.let { stream.writeString(it) }
      username?.let { stream.writeString(it) }
      phone?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      status?.serializeToStream(stream)
      bot_info_version?.let { stream.writeInt32(it) }
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      bot_inline_placeholder?.let { stream.writeString(it) }
      lang_code?.let { stream.writeString(it) }
      emoji_status?.serializeToStream(stream)
      usernames?.let { TlGen_Vector.serialize(stream, it) }
      stories_max_id?.let { stream.writeInt32(it) }
      color?.serializeToStream(stream)
      profile_color?.serializeToStream(stream)
      bot_active_users?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x83314FCAU
    }
  }

  public data class TL_user_layer199(
    public val self: Boolean,
    public val contact: Boolean,
    public val mutual_contact: Boolean,
    public val deleted: Boolean,
    public val bot_chat_history: Boolean,
    public val bot_nochats: Boolean,
    public val verified: Boolean,
    public val min: Boolean,
    public val bot_inline_geo: Boolean,
    public val support: Boolean,
    public val scam: Boolean,
    public val apply_min_photo: Boolean,
    public val fake: Boolean,
    public val bot_attach_menu: Boolean,
    public val premium: Boolean,
    public val attach_menu_enabled: Boolean,
    public val bot_can_edit: Boolean,
    public val close_friend: Boolean,
    public val stories_hidden: Boolean,
    public val stories_unavailable: Boolean,
    public val contact_require_premium: Boolean,
    public val bot_business: Boolean,
    public val bot_has_main_app: Boolean,
    public val id: Long,
    public val access_hash: Long?,
    public val first_name: String?,
    public val last_name: String?,
    public val username: String?,
    public val phone: String?,
    public val photo: TlGen_UserProfilePhoto?,
    public val status: TlGen_UserStatus?,
    public val bot_info_version: Int?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val bot_inline_placeholder: String?,
    public val lang_code: String?,
    public val emoji_status: TlGen_EmojiStatus?,
    public val usernames: List<TlGen_Username>?,
    public val stories_max_id: Int?,
    public val color: TlGen_PeerColor?,
    public val profile_color: TlGen_PeerColor?,
    public val bot_active_users: Int?,
    public val bot_verification_icon: Long?,
  ) : TlGen_Object {
    public val bot: Boolean = bot_info_version != null

    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (access_hash != null) result = result or 1U
        if (first_name != null) result = result or 2U
        if (last_name != null) result = result or 4U
        if (username != null) result = result or 8U
        if (phone != null) result = result or 16U
        if (photo != null) result = result or 32U
        if (status != null) result = result or 64U
        if (self) result = result or 1024U
        if (contact) result = result or 2048U
        if (mutual_contact) result = result or 4096U
        if (deleted) result = result or 8192U
        if (bot) result = result or 16384U
        if (bot_chat_history) result = result or 32768U
        if (bot_nochats) result = result or 65536U
        if (verified) result = result or 131072U
        if (restricted) result = result or 262144U
        if (bot_inline_placeholder != null) result = result or 524288U
        if (min) result = result or 1048576U
        if (bot_inline_geo) result = result or 2097152U
        if (lang_code != null) result = result or 4194304U
        if (support) result = result or 8388608U
        if (scam) result = result or 16777216U
        if (apply_min_photo) result = result or 33554432U
        if (fake) result = result or 67108864U
        if (bot_attach_menu) result = result or 134217728U
        if (premium) result = result or 268435456U
        if (attach_menu_enabled) result = result or 536870912U
        if (emoji_status != null) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (usernames != null) result = result or 1U
        if (bot_can_edit) result = result or 2U
        if (close_friend) result = result or 4U
        if (stories_hidden) result = result or 8U
        if (stories_unavailable) result = result or 16U
        if (stories_max_id != null) result = result or 32U
        if (color != null) result = result or 256U
        if (profile_color != null) result = result or 512U
        if (contact_require_premium) result = result or 1024U
        if (bot_business) result = result or 2048U
        if (bot_active_users != null) result = result or 4096U
        if (bot_has_main_app) result = result or 8192U
        if (bot_verification_icon != null) result = result or 16384U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      access_hash?.let { stream.writeInt64(it) }
      first_name?.let { stream.writeString(it) }
      last_name?.let { stream.writeString(it) }
      username?.let { stream.writeString(it) }
      phone?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      status?.serializeToStream(stream)
      bot_info_version?.let { stream.writeInt32(it) }
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      bot_inline_placeholder?.let { stream.writeString(it) }
      lang_code?.let { stream.writeString(it) }
      emoji_status?.serializeToStream(stream)
      usernames?.let { TlGen_Vector.serialize(stream, it) }
      stories_max_id?.let { stream.writeInt32(it) }
      color?.serializeToStream(stream)
      profile_color?.serializeToStream(stream)
      bot_active_users?.let { stream.writeInt32(it) }
      bot_verification_icon?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x4B46C37EU
    }
  }

  public data class TL_user_layer216(
    public val self: Boolean,
    public val contact: Boolean,
    public val mutual_contact: Boolean,
    public val deleted: Boolean,
    public val bot_chat_history: Boolean,
    public val bot_nochats: Boolean,
    public val verified: Boolean,
    public val min: Boolean,
    public val bot_inline_geo: Boolean,
    public val support: Boolean,
    public val scam: Boolean,
    public val apply_min_photo: Boolean,
    public val fake: Boolean,
    public val bot_attach_menu: Boolean,
    public val premium: Boolean,
    public val attach_menu_enabled: Boolean,
    public val bot_can_edit: Boolean,
    public val close_friend: Boolean,
    public val stories_hidden: Boolean,
    public val stories_unavailable: Boolean,
    public val contact_require_premium: Boolean,
    public val bot_business: Boolean,
    public val bot_has_main_app: Boolean,
    public val bot_forum_view: Boolean,
    public val id: Long,
    public val access_hash: Long?,
    public val first_name: String?,
    public val last_name: String?,
    public val username: String?,
    public val phone: String?,
    public val photo: TlGen_UserProfilePhoto?,
    public val status: TlGen_UserStatus?,
    public val bot_info_version: Int?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val bot_inline_placeholder: String?,
    public val lang_code: String?,
    public val emoji_status: TlGen_EmojiStatus?,
    public val usernames: List<TlGen_Username>?,
    public val stories_max_id: Int?,
    public val color: TlGen_PeerColor?,
    public val profile_color: TlGen_PeerColor?,
    public val bot_active_users: Int?,
    public val bot_verification_icon: Long?,
    public val send_paid_messages_stars: Long?,
  ) : TlGen_Object {
    public val bot: Boolean = bot_info_version != null

    public val restricted: Boolean = restriction_reason != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (access_hash != null) result = result or 1U
        if (first_name != null) result = result or 2U
        if (last_name != null) result = result or 4U
        if (username != null) result = result or 8U
        if (phone != null) result = result or 16U
        if (photo != null) result = result or 32U
        if (status != null) result = result or 64U
        if (self) result = result or 1024U
        if (contact) result = result or 2048U
        if (mutual_contact) result = result or 4096U
        if (deleted) result = result or 8192U
        if (bot) result = result or 16384U
        if (bot_chat_history) result = result or 32768U
        if (bot_nochats) result = result or 65536U
        if (verified) result = result or 131072U
        if (restricted) result = result or 262144U
        if (bot_inline_placeholder != null) result = result or 524288U
        if (min) result = result or 1048576U
        if (bot_inline_geo) result = result or 2097152U
        if (lang_code != null) result = result or 4194304U
        if (support) result = result or 8388608U
        if (scam) result = result or 16777216U
        if (apply_min_photo) result = result or 33554432U
        if (fake) result = result or 67108864U
        if (bot_attach_menu) result = result or 134217728U
        if (premium) result = result or 268435456U
        if (attach_menu_enabled) result = result or 536870912U
        if (emoji_status != null) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (usernames != null) result = result or 1U
        if (bot_can_edit) result = result or 2U
        if (close_friend) result = result or 4U
        if (stories_hidden) result = result or 8U
        if (stories_unavailable) result = result or 16U
        if (stories_max_id != null) result = result or 32U
        if (color != null) result = result or 256U
        if (profile_color != null) result = result or 512U
        if (contact_require_premium) result = result or 1024U
        if (bot_business) result = result or 2048U
        if (bot_active_users != null) result = result or 4096U
        if (bot_has_main_app) result = result or 8192U
        if (bot_verification_icon != null) result = result or 16384U
        if (send_paid_messages_stars != null) result = result or 32768U
        if (bot_forum_view) result = result or 65536U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt64(id)
      access_hash?.let { stream.writeInt64(it) }
      first_name?.let { stream.writeString(it) }
      last_name?.let { stream.writeString(it) }
      username?.let { stream.writeString(it) }
      phone?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      status?.serializeToStream(stream)
      bot_info_version?.let { stream.writeInt32(it) }
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      bot_inline_placeholder?.let { stream.writeString(it) }
      lang_code?.let { stream.writeString(it) }
      emoji_status?.serializeToStream(stream)
      usernames?.let { TlGen_Vector.serialize(stream, it) }
      stories_max_id?.let { stream.writeInt32(it) }
      color?.serializeToStream(stream)
      profile_color?.serializeToStream(stream)
      bot_active_users?.let { stream.writeInt32(it) }
      bot_verification_icon?.let { stream.writeInt64(it) }
      send_paid_messages_stars?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x020B1422U
    }
  }
}

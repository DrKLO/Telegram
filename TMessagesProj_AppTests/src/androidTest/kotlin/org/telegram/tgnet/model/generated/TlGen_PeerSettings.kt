package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PeerSettings : TlGen_Object {
  public data class TL_peerSettings(
    public val report_spam: Boolean,
    public val add_contact: Boolean,
    public val block_contact: Boolean,
    public val share_contact: Boolean,
    public val need_contacts_exception: Boolean,
    public val report_geo: Boolean,
    public val autoarchived: Boolean,
    public val invite_members: Boolean,
    public val request_chat_broadcast: Boolean,
    public val business_bot_paused: Boolean,
    public val business_bot_can_reply: Boolean,
    public val geo_distance: Int?,
    public val charge_paid_message_stars: Long?,
    public val registration_month: String?,
    public val phone_country: String?,
    public val name_change_date: Int?,
    public val photo_change_date: Int?,
    public val multiflags_9: Multiflags_9?,
    public val multiflags_13: Multiflags_13?,
  ) : TlGen_PeerSettings() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (report_spam) result = result or 1U
        if (add_contact) result = result or 2U
        if (block_contact) result = result or 4U
        if (share_contact) result = result or 8U
        if (need_contacts_exception) result = result or 16U
        if (report_geo) result = result or 32U
        if (geo_distance != null) result = result or 64U
        if (autoarchived) result = result or 128U
        if (invite_members) result = result or 256U
        if (multiflags_9 != null) result = result or 512U
        if (request_chat_broadcast) result = result or 1024U
        if (business_bot_paused) result = result or 2048U
        if (business_bot_can_reply) result = result or 4096U
        if (multiflags_13 != null) result = result or 8192U
        if (charge_paid_message_stars != null) result = result or 16384U
        if (registration_month != null) result = result or 32768U
        if (phone_country != null) result = result or 65536U
        if (name_change_date != null) result = result or 131072U
        if (photo_change_date != null) result = result or 262144U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      geo_distance?.let { stream.writeInt32(it) }
      multiflags_9?.let { stream.writeString(it.request_chat_title) }
      multiflags_9?.let { stream.writeInt32(it.request_chat_date) }
      multiflags_13?.let { stream.writeInt64(it.business_bot_id) }
      multiflags_13?.let { stream.writeString(it.business_bot_manage_url) }
      charge_paid_message_stars?.let { stream.writeInt64(it) }
      registration_month?.let { stream.writeString(it) }
      phone_country?.let { stream.writeString(it) }
      name_change_date?.let { stream.writeInt32(it) }
      photo_change_date?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_9(
      public val request_chat_title: String,
      public val request_chat_date: Int,
    )

    public data class Multiflags_13(
      public val business_bot_id: Long,
      public val business_bot_manage_url: String,
    )

    public companion object {
      public const val MAGIC: UInt = 0xF47741F7U
    }
  }

  public data class TL_peerSettings_layer115(
    public val report_spam: Boolean,
    public val add_contact: Boolean,
    public val block_contact: Boolean,
    public val share_contact: Boolean,
    public val need_contacts_exception: Boolean,
    public val report_geo: Boolean,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (report_spam) result = result or 1U
        if (add_contact) result = result or 2U
        if (block_contact) result = result or 4U
        if (share_contact) result = result or 8U
        if (need_contacts_exception) result = result or 16U
        if (report_geo) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
    }

    public companion object {
      public const val MAGIC: UInt = 0x818426CDU
    }
  }

  public data class TL_peerSettings_layer134(
    public val report_spam: Boolean,
    public val add_contact: Boolean,
    public val block_contact: Boolean,
    public val share_contact: Boolean,
    public val need_contacts_exception: Boolean,
    public val report_geo: Boolean,
    public val autoarchived: Boolean,
    public val invite_members: Boolean,
    public val geo_distance: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (report_spam) result = result or 1U
        if (add_contact) result = result or 2U
        if (block_contact) result = result or 4U
        if (share_contact) result = result or 8U
        if (need_contacts_exception) result = result or 16U
        if (report_geo) result = result or 32U
        if (geo_distance != null) result = result or 64U
        if (autoarchived) result = result or 128U
        if (invite_members) result = result or 256U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      geo_distance?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x733F2961U
    }
  }

  public data class TL_peerSettings_layer176(
    public val report_spam: Boolean,
    public val add_contact: Boolean,
    public val block_contact: Boolean,
    public val share_contact: Boolean,
    public val need_contacts_exception: Boolean,
    public val report_geo: Boolean,
    public val autoarchived: Boolean,
    public val invite_members: Boolean,
    public val request_chat_broadcast: Boolean,
    public val geo_distance: Int?,
    public val multiflags_9: Multiflags_9?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (report_spam) result = result or 1U
        if (add_contact) result = result or 2U
        if (block_contact) result = result or 4U
        if (share_contact) result = result or 8U
        if (need_contacts_exception) result = result or 16U
        if (report_geo) result = result or 32U
        if (geo_distance != null) result = result or 64U
        if (autoarchived) result = result or 128U
        if (invite_members) result = result or 256U
        if (multiflags_9 != null) result = result or 512U
        if (request_chat_broadcast) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      geo_distance?.let { stream.writeInt32(it) }
      multiflags_9?.let { stream.writeString(it.request_chat_title) }
      multiflags_9?.let { stream.writeInt32(it.request_chat_date) }
    }

    public data class Multiflags_9(
      public val request_chat_title: String,
      public val request_chat_date: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xA518110DU
    }
  }

  public data class TL_peerSettings_layer199(
    public val report_spam: Boolean,
    public val add_contact: Boolean,
    public val block_contact: Boolean,
    public val share_contact: Boolean,
    public val need_contacts_exception: Boolean,
    public val report_geo: Boolean,
    public val autoarchived: Boolean,
    public val invite_members: Boolean,
    public val request_chat_broadcast: Boolean,
    public val business_bot_paused: Boolean,
    public val business_bot_can_reply: Boolean,
    public val geo_distance: Int?,
    public val multiflags_9: Multiflags_9?,
    public val multiflags_13: Multiflags_13?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (report_spam) result = result or 1U
        if (add_contact) result = result or 2U
        if (block_contact) result = result or 4U
        if (share_contact) result = result or 8U
        if (need_contacts_exception) result = result or 16U
        if (report_geo) result = result or 32U
        if (geo_distance != null) result = result or 64U
        if (autoarchived) result = result or 128U
        if (invite_members) result = result or 256U
        if (multiflags_9 != null) result = result or 512U
        if (request_chat_broadcast) result = result or 1024U
        if (business_bot_paused) result = result or 2048U
        if (business_bot_can_reply) result = result or 4096U
        if (multiflags_13 != null) result = result or 8192U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      geo_distance?.let { stream.writeInt32(it) }
      multiflags_9?.let { stream.writeString(it.request_chat_title) }
      multiflags_9?.let { stream.writeInt32(it.request_chat_date) }
      multiflags_13?.let { stream.writeInt64(it.business_bot_id) }
      multiflags_13?.let { stream.writeString(it.business_bot_manage_url) }
    }

    public data class Multiflags_9(
      public val request_chat_title: String,
      public val request_chat_date: Int,
    )

    public data class Multiflags_13(
      public val business_bot_id: Long,
      public val business_bot_manage_url: String,
    )

    public companion object {
      public const val MAGIC: UInt = 0xACD66C5EU
    }
  }
}

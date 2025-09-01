package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_Config : TlGen_Object {
  public data class TL_config(
    public val default_p2p_contacts: Boolean,
    public val preload_featured_stickers: Boolean,
    public val revoke_pm_inbox: Boolean,
    public val blocked_mode: Boolean,
    public val force_try_ipv6: Boolean,
    public val date: Int,
    public val expires: Int,
    public val test_mode: Boolean,
    public val this_dc: Int,
    public val dc_options: List<TlGen_DcOption>,
    public val dc_txt_domain_name: String,
    public val chat_size_max: Int,
    public val megagroup_size_max: Int,
    public val forwarded_count_max: Int,
    public val online_update_period_ms: Int,
    public val offline_blur_timeout_ms: Int,
    public val offline_idle_timeout_ms: Int,
    public val online_cloud_timeout_ms: Int,
    public val notify_cloud_delay_ms: Int,
    public val notify_default_delay_ms: Int,
    public val push_chat_period_ms: Int,
    public val push_chat_limit: Int,
    public val edit_time_limit: Int,
    public val revoke_time_limit: Int,
    public val revoke_pm_time_limit: Int,
    public val rating_e_decay: Int,
    public val stickers_recent_limit: Int,
    public val channels_read_media_period: Int,
    public val tmp_sessions: Int?,
    public val call_receive_timeout_ms: Int,
    public val call_ring_timeout_ms: Int,
    public val call_connect_timeout_ms: Int,
    public val call_packet_timeout_ms: Int,
    public val me_url_prefix: String,
    public val autoupdate_url_prefix: String?,
    public val gif_search_username: String?,
    public val venue_search_username: String?,
    public val img_search_username: String?,
    public val static_maps_provider: String?,
    public val caption_length_max: Int,
    public val message_length_max: Int,
    public val webfile_dc_id: Int,
    public val reactions_default: TlGen_Reaction?,
    public val autologin_token: String?,
    public val multiflags_2: Multiflags_2?,
  ) : TlGen_Config() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (tmp_sessions != null) result = result or 1U
        if (multiflags_2 != null) result = result or 4U
        if (default_p2p_contacts) result = result or 8U
        if (preload_featured_stickers) result = result or 16U
        if (revoke_pm_inbox) result = result or 64U
        if (autoupdate_url_prefix != null) result = result or 128U
        if (blocked_mode) result = result or 256U
        if (gif_search_username != null) result = result or 512U
        if (venue_search_username != null) result = result or 1024U
        if (img_search_username != null) result = result or 2048U
        if (static_maps_provider != null) result = result or 4096U
        if (force_try_ipv6) result = result or 16384U
        if (reactions_default != null) result = result or 32768U
        if (autologin_token != null) result = result or 65536U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(date)
      stream.writeInt32(expires)
      stream.writeBool(test_mode)
      stream.writeInt32(this_dc)
      TlGen_Vector.serialize(stream, dc_options)
      stream.writeString(dc_txt_domain_name)
      stream.writeInt32(chat_size_max)
      stream.writeInt32(megagroup_size_max)
      stream.writeInt32(forwarded_count_max)
      stream.writeInt32(online_update_period_ms)
      stream.writeInt32(offline_blur_timeout_ms)
      stream.writeInt32(offline_idle_timeout_ms)
      stream.writeInt32(online_cloud_timeout_ms)
      stream.writeInt32(notify_cloud_delay_ms)
      stream.writeInt32(notify_default_delay_ms)
      stream.writeInt32(push_chat_period_ms)
      stream.writeInt32(push_chat_limit)
      stream.writeInt32(edit_time_limit)
      stream.writeInt32(revoke_time_limit)
      stream.writeInt32(revoke_pm_time_limit)
      stream.writeInt32(rating_e_decay)
      stream.writeInt32(stickers_recent_limit)
      stream.writeInt32(channels_read_media_period)
      tmp_sessions?.let { stream.writeInt32(it) }
      stream.writeInt32(call_receive_timeout_ms)
      stream.writeInt32(call_ring_timeout_ms)
      stream.writeInt32(call_connect_timeout_ms)
      stream.writeInt32(call_packet_timeout_ms)
      stream.writeString(me_url_prefix)
      autoupdate_url_prefix?.let { stream.writeString(it) }
      gif_search_username?.let { stream.writeString(it) }
      venue_search_username?.let { stream.writeString(it) }
      img_search_username?.let { stream.writeString(it) }
      static_maps_provider?.let { stream.writeString(it) }
      stream.writeInt32(caption_length_max)
      stream.writeInt32(message_length_max)
      stream.writeInt32(webfile_dc_id)
      multiflags_2?.let { stream.writeString(it.suggested_lang_code) }
      multiflags_2?.let { stream.writeInt32(it.lang_pack_version) }
      multiflags_2?.let { stream.writeInt32(it.base_lang_pack_version) }
      reactions_default?.serializeToStream(stream)
      autologin_token?.let { stream.writeString(it) }
    }

    public data class Multiflags_2(
      public val suggested_lang_code: String,
      public val lang_pack_version: Int,
      public val base_lang_pack_version: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xCC1A241EU
    }
  }
}

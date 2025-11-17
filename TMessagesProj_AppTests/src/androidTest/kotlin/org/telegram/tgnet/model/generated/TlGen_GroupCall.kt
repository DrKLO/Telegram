package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_GroupCall : TlGen_Object {
  public data class TL_groupCallDiscarded(
    public val id: Long,
    public val access_hash: Long,
    public val duration: Int,
  ) : TlGen_GroupCall() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeInt32(duration)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7780BCB4U
    }
  }

  public data class TL_groupCall(
    public val join_muted: Boolean,
    public val can_change_join_muted: Boolean,
    public val join_date_asc: Boolean,
    public val schedule_start_subscribed: Boolean,
    public val can_start_video: Boolean,
    public val record_video_active: Boolean,
    public val rtmp_stream: Boolean,
    public val listeners_hidden: Boolean,
    public val conference: Boolean,
    public val creator: Boolean,
    public val messages_enabled: Boolean,
    public val can_change_messages_enabled: Boolean,
    public val min: Boolean,
    public val id: Long,
    public val access_hash: Long,
    public val participants_count: Int,
    public val title: String?,
    public val stream_dc_id: Int?,
    public val record_start_date: Int?,
    public val schedule_date: Int?,
    public val unmuted_video_count: Int?,
    public val unmuted_video_limit: Int,
    public val version: Int,
    public val invite_link: String?,
    public val send_paid_messages_stars: Long?,
    public val default_send_as: TlGen_Peer?,
  ) : TlGen_GroupCall() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (join_muted) result = result or 2U
        if (can_change_join_muted) result = result or 4U
        if (title != null) result = result or 8U
        if (stream_dc_id != null) result = result or 16U
        if (record_start_date != null) result = result or 32U
        if (join_date_asc) result = result or 64U
        if (schedule_date != null) result = result or 128U
        if (schedule_start_subscribed) result = result or 256U
        if (can_start_video) result = result or 512U
        if (unmuted_video_count != null) result = result or 1024U
        if (record_video_active) result = result or 2048U
        if (rtmp_stream) result = result or 4096U
        if (listeners_hidden) result = result or 8192U
        if (conference) result = result or 16384U
        if (creator) result = result or 32768U
        if (invite_link != null) result = result or 65536U
        if (messages_enabled) result = result or 131072U
        if (can_change_messages_enabled) result = result or 262144U
        if (min) result = result or 524288U
        if (send_paid_messages_stars != null) result = result or 1048576U
        if (default_send_as != null) result = result or 2097152U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeInt32(participants_count)
      title?.let { stream.writeString(it) }
      stream_dc_id?.let { stream.writeInt32(it) }
      record_start_date?.let { stream.writeInt32(it) }
      schedule_date?.let { stream.writeInt32(it) }
      unmuted_video_count?.let { stream.writeInt32(it) }
      stream.writeInt32(unmuted_video_limit)
      stream.writeInt32(version)
      invite_link?.let { stream.writeString(it) }
      send_paid_messages_stars?.let { stream.writeInt64(it) }
      default_send_as?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEFB2B617U
    }
  }
}

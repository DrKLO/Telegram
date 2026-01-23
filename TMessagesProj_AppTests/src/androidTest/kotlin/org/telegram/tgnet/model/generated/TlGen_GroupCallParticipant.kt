package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_GroupCallParticipant : TlGen_Object {
  public data class TL_groupCallParticipant(
    public val muted: Boolean,
    public val left: Boolean,
    public val can_self_unmute: Boolean,
    public val just_joined: Boolean,
    public val versioned: Boolean,
    public val min: Boolean,
    public val muted_by_you: Boolean,
    public val volume_by_admin: Boolean,
    public val self: Boolean,
    public val video_joined: Boolean,
    public val peer: TlGen_Peer,
    public val date: Int,
    public val active_date: Int?,
    public val source: Int,
    public val volume: Int?,
    public val about: String?,
    public val raise_hand_rating: Long?,
    public val video: TlGen_GroupCallParticipantVideo?,
    public val presentation: TlGen_GroupCallParticipantVideo?,
    public val paid_stars_total: Long?,
  ) : TlGen_GroupCallParticipant() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (muted) result = result or 1U
        if (left) result = result or 2U
        if (can_self_unmute) result = result or 4U
        if (active_date != null) result = result or 8U
        if (just_joined) result = result or 16U
        if (versioned) result = result or 32U
        if (video != null) result = result or 64U
        if (volume != null) result = result or 128U
        if (min) result = result or 256U
        if (muted_by_you) result = result or 512U
        if (volume_by_admin) result = result or 1024U
        if (about != null) result = result or 2048U
        if (self) result = result or 4096U
        if (raise_hand_rating != null) result = result or 8192U
        if (presentation != null) result = result or 16384U
        if (video_joined) result = result or 32768U
        if (paid_stars_total != null) result = result or 65536U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(date)
      active_date?.let { stream.writeInt32(it) }
      stream.writeInt32(source)
      volume?.let { stream.writeInt32(it) }
      about?.let { stream.writeString(it) }
      raise_hand_rating?.let { stream.writeInt64(it) }
      video?.serializeToStream(stream)
      presentation?.serializeToStream(stream)
      paid_stars_total?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x2A3DC7ACU
    }
  }
}

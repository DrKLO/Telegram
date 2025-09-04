package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ChannelParticipant : TlGen_Object {
  public data class TL_channelParticipantLeft(
    public val peer: TlGen_Peer,
  ) : TlGen_ChannelParticipant() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1B03F006U
    }
  }

  public data class TL_channelParticipantCreator(
    public val user_id: Long,
    public val admin_rights: TlGen_ChatAdminRights,
    public val rank: String?,
  ) : TlGen_ChannelParticipant() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (rank != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(user_id)
      admin_rights.serializeToStream(stream)
      rank?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x2FE601D3U
    }
  }

  public data class TL_channelParticipantAdmin(
    public val can_edit: Boolean,
    public val user_id: Long,
    public val inviter_id: Long?,
    public val promoted_by: Long,
    public val date: Int,
    public val admin_rights: TlGen_ChatAdminRights,
    public val rank: String?,
  ) : TlGen_ChannelParticipant() {
    public val self: Boolean = inviter_id != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (can_edit) result = result or 1U
        if (self) result = result or 2U
        if (rank != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(user_id)
      inviter_id?.let { stream.writeInt64(it) }
      stream.writeInt64(promoted_by)
      stream.writeInt32(date)
      admin_rights.serializeToStream(stream)
      rank?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x34C3BB53U
    }
  }

  public data class TL_channelParticipantBanned(
    public val left: Boolean,
    public val peer: TlGen_Peer,
    public val kicked_by: Long,
    public val date: Int,
    public val banned_rights: TlGen_ChatBannedRights,
  ) : TlGen_ChannelParticipant() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (left) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
      stream.writeInt64(kicked_by)
      stream.writeInt32(date)
      banned_rights.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6DF8014EU
    }
  }

  public data class TL_channelParticipant(
    public val user_id: Long,
    public val date: Int,
    public val subscription_until_date: Int?,
  ) : TlGen_ChannelParticipant() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (subscription_until_date != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(user_id)
      stream.writeInt32(date)
      subscription_until_date?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xCB397619U
    }
  }

  public data class TL_channelParticipantSelf(
    public val via_request: Boolean,
    public val user_id: Long,
    public val inviter_id: Long,
    public val date: Int,
    public val subscription_until_date: Int?,
  ) : TlGen_ChannelParticipant() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (via_request) result = result or 1U
        if (subscription_until_date != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(user_id)
      stream.writeInt64(inviter_id)
      stream.writeInt32(date)
      subscription_until_date?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x4F607BEFU
    }
  }
}

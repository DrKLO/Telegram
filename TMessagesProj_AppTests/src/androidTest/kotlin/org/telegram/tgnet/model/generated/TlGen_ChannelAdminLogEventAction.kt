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

public sealed class TlGen_ChannelAdminLogEventAction : TlGen_Object {
  public data class TL_channelAdminLogEventActionChangeTitle(
    public val prev_value: String,
    public val new_value: String,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(prev_value)
      stream.writeString(new_value)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE6DFB825U
    }
  }

  public data class TL_channelAdminLogEventActionChangeAbout(
    public val prev_value: String,
    public val new_value: String,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(prev_value)
      stream.writeString(new_value)
    }

    public companion object {
      public const val MAGIC: UInt = 0x55188A2EU
    }
  }

  public data class TL_channelAdminLogEventActionChangeUsername(
    public val prev_value: String,
    public val new_value: String,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(prev_value)
      stream.writeString(new_value)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6A4AFC38U
    }
  }

  public data class TL_channelAdminLogEventActionToggleInvites(
    public val new_value: Boolean,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeBool(new_value)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1B7907AEU
    }
  }

  public data class TL_channelAdminLogEventActionToggleSignatures(
    public val new_value: Boolean,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeBool(new_value)
    }

    public companion object {
      public const val MAGIC: UInt = 0x26AE0971U
    }
  }

  public data class TL_channelAdminLogEventActionUpdatePinned(
    public val message: TlGen_Message,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      message.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE9E82C18U
    }
  }

  public data class TL_channelAdminLogEventActionEditMessage(
    public val prev_message: TlGen_Message,
    public val new_message: TlGen_Message,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      prev_message.serializeToStream(stream)
      new_message.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x709B2405U
    }
  }

  public data class TL_channelAdminLogEventActionDeleteMessage(
    public val message: TlGen_Message,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      message.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x42E047BBU
    }
  }

  public data object TL_channelAdminLogEventActionParticipantJoin :
      TlGen_ChannelAdminLogEventAction() {
    public const val MAGIC: UInt = 0x183040D3U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_channelAdminLogEventActionParticipantLeave :
      TlGen_ChannelAdminLogEventAction() {
    public const val MAGIC: UInt = 0xF89777F2U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_channelAdminLogEventActionParticipantInvite(
    public val participant: TlGen_ChannelParticipant,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      participant.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE31C34D8U
    }
  }

  public data class TL_channelAdminLogEventActionParticipantToggleBan(
    public val prev_participant: TlGen_ChannelParticipant,
    public val new_participant: TlGen_ChannelParticipant,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      prev_participant.serializeToStream(stream)
      new_participant.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE6D83D7EU
    }
  }

  public data class TL_channelAdminLogEventActionParticipantToggleAdmin(
    public val prev_participant: TlGen_ChannelParticipant,
    public val new_participant: TlGen_ChannelParticipant,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      prev_participant.serializeToStream(stream)
      new_participant.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD5676710U
    }
  }

  public data class TL_channelAdminLogEventActionChangeStickerSet(
    public val prev_stickerset: TlGen_InputStickerSet,
    public val new_stickerset: TlGen_InputStickerSet,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      prev_stickerset.serializeToStream(stream)
      new_stickerset.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB1C3CAA7U
    }
  }

  public data class TL_channelAdminLogEventActionTogglePreHistoryHidden(
    public val new_value: Boolean,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeBool(new_value)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5F5C95F1U
    }
  }

  public data class TL_channelAdminLogEventActionDefaultBannedRights(
    public val prev_banned_rights: TlGen_ChatBannedRights,
    public val new_banned_rights: TlGen_ChatBannedRights,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      prev_banned_rights.serializeToStream(stream)
      new_banned_rights.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2DF5FC0AU
    }
  }

  public data class TL_channelAdminLogEventActionStopPoll(
    public val message: TlGen_Message,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      message.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8F079643U
    }
  }

  public data class TL_channelAdminLogEventActionChangePhoto(
    public val prev_photo: TlGen_Photo,
    public val new_photo: TlGen_Photo,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      prev_photo.serializeToStream(stream)
      new_photo.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x434BD2AFU
    }
  }

  public data class TL_channelAdminLogEventActionChangeLocation(
    public val prev_value: TlGen_ChannelLocation,
    public val new_value: TlGen_ChannelLocation,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      prev_value.serializeToStream(stream)
      new_value.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0E6B76AEU
    }
  }

  public data class TL_channelAdminLogEventActionToggleSlowMode(
    public val prev_value: Int,
    public val new_value: Int,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(prev_value)
      stream.writeInt32(new_value)
    }

    public companion object {
      public const val MAGIC: UInt = 0x53909779U
    }
  }

  public data class TL_channelAdminLogEventActionStartGroupCall(
    public val call: TlGen_InputGroupCall,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      call.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x23209745U
    }
  }

  public data class TL_channelAdminLogEventActionDiscardGroupCall(
    public val call: TlGen_InputGroupCall,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      call.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDB9F9140U
    }
  }

  public data class TL_channelAdminLogEventActionParticipantMute(
    public val participant: TlGen_GroupCallParticipant,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      participant.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF92424D2U
    }
  }

  public data class TL_channelAdminLogEventActionParticipantUnmute(
    public val participant: TlGen_GroupCallParticipant,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      participant.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE64429C0U
    }
  }

  public data class TL_channelAdminLogEventActionToggleGroupCallSetting(
    public val join_muted: Boolean,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeBool(join_muted)
    }

    public companion object {
      public const val MAGIC: UInt = 0x56D6A247U
    }
  }

  public data class TL_channelAdminLogEventActionExportedInviteDelete(
    public val invite: TlGen_ExportedChatInvite,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      invite.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5A50FCA4U
    }
  }

  public data class TL_channelAdminLogEventActionExportedInviteRevoke(
    public val invite: TlGen_ExportedChatInvite,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      invite.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x410A134EU
    }
  }

  public data class TL_channelAdminLogEventActionExportedInviteEdit(
    public val prev_invite: TlGen_ExportedChatInvite,
    public val new_invite: TlGen_ExportedChatInvite,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      prev_invite.serializeToStream(stream)
      new_invite.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE90EBB59U
    }
  }

  public data class TL_channelAdminLogEventActionParticipantVolume(
    public val participant: TlGen_GroupCallParticipant,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      participant.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3E7F6847U
    }
  }

  public data class TL_channelAdminLogEventActionChangeHistoryTTL(
    public val prev_value: Int,
    public val new_value: Int,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(prev_value)
      stream.writeInt32(new_value)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6E941A38U
    }
  }

  public data class TL_channelAdminLogEventActionChangeLinkedChat(
    public val prev_value: Long,
    public val new_value: Long,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(prev_value)
      stream.writeInt64(new_value)
    }

    public companion object {
      public const val MAGIC: UInt = 0x050C7AC8U
    }
  }

  public data class TL_channelAdminLogEventActionParticipantJoinByRequest(
    public val invite: TlGen_ExportedChatInvite,
    public val approved_by: Long,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      invite.serializeToStream(stream)
      stream.writeInt64(approved_by)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAFB6144AU
    }
  }

  public data class TL_channelAdminLogEventActionToggleNoForwards(
    public val new_value: Boolean,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeBool(new_value)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCB2AC766U
    }
  }

  public data class TL_channelAdminLogEventActionSendMessage(
    public val message: TlGen_Message,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      message.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x278F2868U
    }
  }

  public data class TL_channelAdminLogEventActionChangeAvailableReactions(
    public val prev_value: TlGen_ChatReactions,
    public val new_value: TlGen_ChatReactions,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      prev_value.serializeToStream(stream)
      new_value.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBE4E0EF8U
    }
  }

  public data class TL_channelAdminLogEventActionChangeUsernames(
    public val prev_value: List<String>,
    public val new_value: List<String>,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serializeString(stream, prev_value)
      TlGen_Vector.serializeString(stream, new_value)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF04FB3A9U
    }
  }

  public data class TL_channelAdminLogEventActionToggleForum(
    public val new_value: Boolean,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeBool(new_value)
    }

    public companion object {
      public const val MAGIC: UInt = 0x02CC6383U
    }
  }

  public data class TL_channelAdminLogEventActionCreateTopic(
    public val topic: TlGen_ForumTopic,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      topic.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x58707D28U
    }
  }

  public data class TL_channelAdminLogEventActionEditTopic(
    public val prev_topic: TlGen_ForumTopic,
    public val new_topic: TlGen_ForumTopic,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      prev_topic.serializeToStream(stream)
      new_topic.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF06FE208U
    }
  }

  public data class TL_channelAdminLogEventActionDeleteTopic(
    public val topic: TlGen_ForumTopic,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      topic.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAE168909U
    }
  }

  public data class TL_channelAdminLogEventActionPinTopic(
    public val prev_topic: TlGen_ForumTopic?,
    public val new_topic: TlGen_ForumTopic?,
  ) : TlGen_ChannelAdminLogEventAction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (prev_topic != null) result = result or 1U
        if (new_topic != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      prev_topic?.serializeToStream(stream)
      new_topic?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5D8D353BU
    }
  }

  public data class TL_channelAdminLogEventActionToggleAntiSpam(
    public val new_value: Boolean,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeBool(new_value)
    }

    public companion object {
      public const val MAGIC: UInt = 0x64F36DFCU
    }
  }

  public data class TL_channelAdminLogEventActionParticipantJoinByInvite(
    public val via_chatlist: Boolean,
    public val invite: TlGen_ExportedChatInvite,
  ) : TlGen_ChannelAdminLogEventAction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (via_chatlist) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      invite.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFE9FC158U
    }
  }

  public data class TL_channelAdminLogEventActionChangePeerColor(
    public val prev_value: TlGen_PeerColor,
    public val new_value: TlGen_PeerColor,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      prev_value.serializeToStream(stream)
      new_value.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5796E780U
    }
  }

  public data class TL_channelAdminLogEventActionChangeProfilePeerColor(
    public val prev_value: TlGen_PeerColor,
    public val new_value: TlGen_PeerColor,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      prev_value.serializeToStream(stream)
      new_value.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5E477B25U
    }
  }

  public data class TL_channelAdminLogEventActionChangeWallpaper(
    public val prev_value: TlGen_WallPaper,
    public val new_value: TlGen_WallPaper,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      prev_value.serializeToStream(stream)
      new_value.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x31BB5D52U
    }
  }

  public data class TL_channelAdminLogEventActionChangeEmojiStatus(
    public val prev_value: TlGen_EmojiStatus,
    public val new_value: TlGen_EmojiStatus,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      prev_value.serializeToStream(stream)
      new_value.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3EA9FEB1U
    }
  }

  public data class TL_channelAdminLogEventActionChangeEmojiStickerSet(
    public val prev_stickerset: TlGen_InputStickerSet,
    public val new_stickerset: TlGen_InputStickerSet,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      prev_stickerset.serializeToStream(stream)
      new_stickerset.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x46D840ABU
    }
  }

  public data class TL_channelAdminLogEventActionToggleSignatureProfiles(
    public val new_value: Boolean,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeBool(new_value)
    }

    public companion object {
      public const val MAGIC: UInt = 0x60A79C79U
    }
  }

  public data class TL_channelAdminLogEventActionParticipantSubExtend(
    public val prev_participant: TlGen_ChannelParticipant,
    public val new_participant: TlGen_ChannelParticipant,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      prev_participant.serializeToStream(stream)
      new_participant.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x64642DB3U
    }
  }

  public data class TL_channelAdminLogEventActionToggleAutotranslation(
    public val new_value: Boolean,
  ) : TlGen_ChannelAdminLogEventAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeBool(new_value)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC517F77EU
    }
  }
}

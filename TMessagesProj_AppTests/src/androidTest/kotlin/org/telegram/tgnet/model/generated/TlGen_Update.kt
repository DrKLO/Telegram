package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Byte
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_Update : TlGen_Object {
  public data class TL_updateMessageID(
    public val id: Int,
    public val random_id: Long,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeInt64(random_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4E90BFD6U
    }
  }

  public data class TL_updateChatParticipants(
    public val participants: TlGen_ChatParticipants,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      participants.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x07761198U
    }
  }

  public data class TL_updateNewEncryptedMessage(
    public val message: TlGen_EncryptedMessage,
    public val qts: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      message.serializeToStream(stream)
      stream.writeInt32(qts)
    }

    public companion object {
      public const val MAGIC: UInt = 0x12BCBD9AU
    }
  }

  public data class TL_updateEncryptedChatTyping(
    public val chat_id: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(chat_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1710F156U
    }
  }

  public data class TL_updateEncryption(
    public val chat: TlGen_EncryptedChat,
    public val date: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      chat.serializeToStream(stream)
      stream.writeInt32(date)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB4A2E88DU
    }
  }

  public data class TL_updateEncryptedMessagesRead(
    public val chat_id: Int,
    public val max_date: Int,
    public val date: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(chat_id)
      stream.writeInt32(max_date)
      stream.writeInt32(date)
    }

    public companion object {
      public const val MAGIC: UInt = 0x38FE25B7U
    }
  }

  public data class TL_updateDcOptions(
    public val dc_options: List<TlGen_DcOption>,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, dc_options)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8E5E9873U
    }
  }

  public data class TL_updateNotifySettings(
    public val peer: TlGen_NotifyPeer,
    public val notify_settings: TlGen_PeerNotifySettings,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      notify_settings.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBEC268EFU
    }
  }

  public data class TL_updatePrivacy(
    public val key: TlGen_PrivacyKey,
    public val rules: List<TlGen_PrivacyRule>,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      key.serializeToStream(stream)
      TlGen_Vector.serialize(stream, rules)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEE3B272AU
    }
  }

  public data class TL_updateNewMessage(
    public val message: TlGen_Message,
    public val pts: Int,
    public val pts_count: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      message.serializeToStream(stream)
      stream.writeInt32(pts)
      stream.writeInt32(pts_count)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1F2B0AFDU
    }
  }

  public data class TL_updateDeleteMessages(
    public val messages: List<Int>,
    public val pts: Int,
    public val pts_count: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serializeInt(stream, messages)
      stream.writeInt32(pts)
      stream.writeInt32(pts_count)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA20DB0E5U
    }
  }

  public data class TL_updateReadHistoryOutbox(
    public val peer: TlGen_Peer,
    public val max_id: Int,
    public val pts: Int,
    public val pts_count: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(max_id)
      stream.writeInt32(pts)
      stream.writeInt32(pts_count)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2F2F21BFU
    }
  }

  public data class TL_updateWebPage(
    public val webpage: TlGen_WebPage,
    public val pts: Int,
    public val pts_count: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      webpage.serializeToStream(stream)
      stream.writeInt32(pts)
      stream.writeInt32(pts_count)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7F891213U
    }
  }

  public data class TL_updateNewChannelMessage(
    public val message: TlGen_Message,
    public val pts: Int,
    public val pts_count: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      message.serializeToStream(stream)
      stream.writeInt32(pts)
      stream.writeInt32(pts_count)
    }

    public companion object {
      public const val MAGIC: UInt = 0x62BA04D9U
    }
  }

  public data class TL_updateNewStickerSet(
    public val stickerset: TlGen_messages_StickerSet,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stickerset.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x688A30AAU
    }
  }

  public data object TL_updateSavedGifs : TlGen_Update() {
    public const val MAGIC: UInt = 0x9375341EU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_updateEditChannelMessage(
    public val message: TlGen_Message,
    public val pts: Int,
    public val pts_count: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      message.serializeToStream(stream)
      stream.writeInt32(pts)
      stream.writeInt32(pts_count)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1B3F4DF7U
    }
  }

  public data class TL_updateEditMessage(
    public val message: TlGen_Message,
    public val pts: Int,
    public val pts_count: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      message.serializeToStream(stream)
      stream.writeInt32(pts)
      stream.writeInt32(pts_count)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE40370A3U
    }
  }

  public data object TL_updateReadFeaturedStickers : TlGen_Update() {
    public const val MAGIC: UInt = 0x571D2742U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_updateRecentStickers : TlGen_Update() {
    public const val MAGIC: UInt = 0x9A422C20U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_updateConfig : TlGen_Update() {
    public const val MAGIC: UInt = 0xA229DD06U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_updateStickerSetsOrder(
    public val masks: Boolean,
    public val emojis: Boolean,
    public val order: List<Long>,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (masks) result = result or 1U
        if (emojis) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serializeLong(stream, order)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0BB2D201U
    }
  }

  public data class TL_updateServiceNotification(
    public val popup: Boolean,
    public val invert_media: Boolean,
    public val inbox_date: Int?,
    public val type: String,
    public val message: String,
    public val media: TlGen_MessageMedia,
    public val entities: List<TlGen_MessageEntity>,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (popup) result = result or 1U
        if (inbox_date != null) result = result or 2U
        if (invert_media) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      inbox_date?.let { stream.writeInt32(it) }
      stream.writeString(type)
      stream.writeString(message)
      media.serializeToStream(stream)
      TlGen_Vector.serialize(stream, entities)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEBE46819U
    }
  }

  public data class TL_updatePhoneCall(
    public val phone_call: TlGen_PhoneCall,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      phone_call.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAB0F6B1EU
    }
  }

  public data class TL_updateLangPack(
    public val difference: TlGen_LangPackDifference,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      difference.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x56022F4DU
    }
  }

  public data object TL_updateFavedStickers : TlGen_Update() {
    public const val MAGIC: UInt = 0xE511996DU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_updateContactsReset : TlGen_Update() {
    public const val MAGIC: UInt = 0x7084A7BEU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_updateLangPackTooLong(
    public val lang_code: String,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(lang_code)
    }

    public companion object {
      public const val MAGIC: UInt = 0x46560264U
    }
  }

  public data class TL_updateMessagePoll(
    public val poll_id: Long,
    public val poll: TlGen_Poll?,
    public val results: TlGen_PollResults,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (poll != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(poll_id)
      poll?.serializeToStream(stream)
      results.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xACA1657BU
    }
  }

  public data class TL_updateChatDefaultBannedRights(
    public val peer: TlGen_Peer,
    public val default_banned_rights: TlGen_ChatBannedRights,
    public val version: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      default_banned_rights.serializeToStream(stream)
      stream.writeInt32(version)
    }

    public companion object {
      public const val MAGIC: UInt = 0x54C01850U
    }
  }

  public data class TL_updateDialogPinned(
    public val pinned: Boolean,
    public val folder_id: Int?,
    public val peer: TlGen_DialogPeer,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (pinned) result = result or 1U
        if (folder_id != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      folder_id?.let { stream.writeInt32(it) }
      peer.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6E6FE51CU
    }
  }

  public data class TL_updatePinnedDialogs(
    public val folder_id: Int?,
    public val order: List<TlGen_DialogPeer>?,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (order != null) result = result or 1U
        if (folder_id != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      folder_id?.let { stream.writeInt32(it) }
      order?.let { TlGen_Vector.serialize(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xFA0F3CA2U
    }
  }

  public data class TL_updateFolderPeers(
    public val folder_peers: List<TlGen_FolderPeer>,
    public val pts: Int,
    public val pts_count: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, folder_peers)
      stream.writeInt32(pts)
      stream.writeInt32(pts_count)
    }

    public companion object {
      public const val MAGIC: UInt = 0x19360DC0U
    }
  }

  public data class TL_updatePeerSettings(
    public val peer: TlGen_Peer,
    public val settings: TlGen_PeerSettings,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      settings.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6A7E7366U
    }
  }

  public data class TL_updatePeerLocated(
    public val peers: List<TlGen_PeerLocated>,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, peers)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB4AFCFB0U
    }
  }

  public data class TL_updateNewScheduledMessage(
    public val message: TlGen_Message,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      message.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x39A51DFBU
    }
  }

  public data class TL_updateTheme(
    public val theme: TlGen_Theme,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      theme.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8216FBA3U
    }
  }

  public data class TL_updateGeoLiveViewed(
    public val peer: TlGen_Peer,
    public val msg_id: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(msg_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x871FB939U
    }
  }

  public data object TL_updateLoginToken : TlGen_Update() {
    public const val MAGIC: UInt = 0x564FE691U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_updateDialogFilter(
    public val id: Int,
    public val filter: TlGen_DialogFilter?,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (filter != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      filter?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x26FFDE7DU
    }
  }

  public data class TL_updateDialogFilterOrder(
    public val order: List<Int>,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serializeInt(stream, order)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA5D72105U
    }
  }

  public data object TL_updateDialogFilters : TlGen_Update() {
    public const val MAGIC: UInt = 0x3504914FU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_updatePhoneCallSignalingData(
    public val phone_call_id: Long,
    public val `data`: List<Byte>,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(phone_call_id)
      stream.writeByteArray(data.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x2661BF09U
    }
  }

  public data class TL_updatePinnedMessages(
    public val pinned: Boolean,
    public val peer: TlGen_Peer,
    public val messages: List<Int>,
    public val pts: Int,
    public val pts_count: Int,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (pinned) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
      TlGen_Vector.serializeInt(stream, messages)
      stream.writeInt32(pts)
      stream.writeInt32(pts_count)
    }

    public companion object {
      public const val MAGIC: UInt = 0xED85EAB5U
    }
  }

  public data class TL_updateGroupCallParticipants(
    public val call: TlGen_InputGroupCall,
    public val participants: List<TlGen_GroupCallParticipant>,
    public val version: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      call.serializeToStream(stream)
      TlGen_Vector.serialize(stream, participants)
      stream.writeInt32(version)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF2EBDB4EU
    }
  }

  public data class TL_updatePeerHistoryTTL(
    public val peer: TlGen_Peer,
    public val ttl_period: Int?,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (ttl_period != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xBB9BB9A5U
    }
  }

  public data class TL_updateGroupCallConnection(
    public val presentation: Boolean,
    public val params: TlGen_DataJSON,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (presentation) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      params.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0B783982U
    }
  }

  public data class TL_updateChatUserTyping(
    public val chat_id: Long,
    public val from_id: TlGen_Peer,
    public val action: TlGen_SendMessageAction,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(chat_id)
      from_id.serializeToStream(stream)
      action.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x83487AF0U
    }
  }

  public data class TL_updateUserStatus(
    public val user_id: Long,
    public val status: TlGen_UserStatus,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(user_id)
      status.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE5BDF8DEU
    }
  }

  public data class TL_updateChatParticipantAdd(
    public val chat_id: Long,
    public val user_id: Long,
    public val inviter_id: Long,
    public val date: Int,
    public val version: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(chat_id)
      stream.writeInt64(user_id)
      stream.writeInt64(inviter_id)
      stream.writeInt32(date)
      stream.writeInt32(version)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3DDA5451U
    }
  }

  public data class TL_updateChatParticipantDelete(
    public val chat_id: Long,
    public val user_id: Long,
    public val version: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(chat_id)
      stream.writeInt64(user_id)
      stream.writeInt32(version)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE32F3D77U
    }
  }

  public data class TL_updateUserPhone(
    public val user_id: Long,
    public val phone: String,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(user_id)
      stream.writeString(phone)
    }

    public companion object {
      public const val MAGIC: UInt = 0x05492A13U
    }
  }

  public data class TL_updateChannelTooLong(
    public val channel_id: Long,
    public val pts: Int?,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (pts != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(channel_id)
      pts?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x108D941FU
    }
  }

  public data class TL_updateChannel(
    public val channel_id: Long,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(channel_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x635B4C09U
    }
  }

  public data class TL_updateReadChannelInbox(
    public val folder_id: Int?,
    public val channel_id: Long,
    public val max_id: Int,
    public val still_unread_count: Int,
    public val pts: Int,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (folder_id != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      folder_id?.let { stream.writeInt32(it) }
      stream.writeInt64(channel_id)
      stream.writeInt32(max_id)
      stream.writeInt32(still_unread_count)
      stream.writeInt32(pts)
    }

    public companion object {
      public const val MAGIC: UInt = 0x922E6E10U
    }
  }

  public data class TL_updateDeleteChannelMessages(
    public val channel_id: Long,
    public val messages: List<Int>,
    public val pts: Int,
    public val pts_count: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(channel_id)
      TlGen_Vector.serializeInt(stream, messages)
      stream.writeInt32(pts)
      stream.writeInt32(pts_count)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC32D5B12U
    }
  }

  public data class TL_updateChannelMessageViews(
    public val channel_id: Long,
    public val id: Int,
    public val views: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(channel_id)
      stream.writeInt32(id)
      stream.writeInt32(views)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF226AC08U
    }
  }

  public data class TL_updateChatParticipantAdmin(
    public val chat_id: Long,
    public val user_id: Long,
    public val is_admin: Boolean,
    public val version: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(chat_id)
      stream.writeInt64(user_id)
      stream.writeBool(is_admin)
      stream.writeInt32(version)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD7CA61A2U
    }
  }

  public data class TL_updateReadChannelOutbox(
    public val channel_id: Long,
    public val max_id: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(channel_id)
      stream.writeInt32(max_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB75F99A9U
    }
  }

  public data class TL_updateChannelWebPage(
    public val channel_id: Long,
    public val webpage: TlGen_WebPage,
    public val pts: Int,
    public val pts_count: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(channel_id)
      webpage.serializeToStream(stream)
      stream.writeInt32(pts)
      stream.writeInt32(pts_count)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2F2BA99FU
    }
  }

  public data class TL_updateChannelAvailableMessages(
    public val channel_id: Long,
    public val available_min_id: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(channel_id)
      stream.writeInt32(available_min_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB23FC698U
    }
  }

  public data class TL_updateChannelMessageForwards(
    public val channel_id: Long,
    public val id: Int,
    public val forwards: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(channel_id)
      stream.writeInt32(id)
      stream.writeInt32(forwards)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD29A27F4U
    }
  }

  public data class TL_updateReadChannelDiscussionInbox(
    public val channel_id: Long,
    public val top_msg_id: Int,
    public val read_max_id: Int,
    public val multiflags_0: Multiflags_0?,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (multiflags_0 != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(channel_id)
      stream.writeInt32(top_msg_id)
      stream.writeInt32(read_max_id)
      multiflags_0?.let { stream.writeInt64(it.broadcast_id) }
      multiflags_0?.let { stream.writeInt32(it.broadcast_post) }
    }

    public data class Multiflags_0(
      public val broadcast_id: Long,
      public val broadcast_post: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xD6B19546U
    }
  }

  public data class TL_updateReadChannelDiscussionOutbox(
    public val channel_id: Long,
    public val top_msg_id: Int,
    public val read_max_id: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(channel_id)
      stream.writeInt32(top_msg_id)
      stream.writeInt32(read_max_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x695C9E7CU
    }
  }

  public data class TL_updateChannelUserTyping(
    public val channel_id: Long,
    public val top_msg_id: Int?,
    public val from_id: TlGen_Peer,
    public val action: TlGen_SendMessageAction,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (top_msg_id != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(channel_id)
      top_msg_id?.let { stream.writeInt32(it) }
      from_id.serializeToStream(stream)
      action.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8C88C923U
    }
  }

  public data class TL_updatePinnedChannelMessages(
    public val pinned: Boolean,
    public val channel_id: Long,
    public val messages: List<Int>,
    public val pts: Int,
    public val pts_count: Int,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (pinned) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(channel_id)
      TlGen_Vector.serializeInt(stream, messages)
      stream.writeInt32(pts)
      stream.writeInt32(pts_count)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5BB98608U
    }
  }

  public data class TL_updateChat(
    public val chat_id: Long,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(chat_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF89A6A4EU
    }
  }

  public data class TL_updateChannelParticipant(
    public val via_chatlist: Boolean,
    public val channel_id: Long,
    public val date: Int,
    public val actor_id: Long,
    public val user_id: Long,
    public val prev_participant: TlGen_ChannelParticipant?,
    public val new_participant: TlGen_ChannelParticipant?,
    public val invite: TlGen_ExportedChatInvite?,
    public val qts: Int,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (prev_participant != null) result = result or 1U
        if (new_participant != null) result = result or 2U
        if (invite != null) result = result or 4U
        if (via_chatlist) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(channel_id)
      stream.writeInt32(date)
      stream.writeInt64(actor_id)
      stream.writeInt64(user_id)
      prev_participant?.serializeToStream(stream)
      new_participant?.serializeToStream(stream)
      invite?.serializeToStream(stream)
      stream.writeInt32(qts)
    }

    public companion object {
      public const val MAGIC: UInt = 0x985D3ABBU
    }
  }

  public data class TL_updateBotCommands(
    public val peer: TlGen_Peer,
    public val bot_id: Long,
    public val commands: List<TlGen_BotCommand>,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeInt64(bot_id)
      TlGen_Vector.serialize(stream, commands)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4D712F2EU
    }
  }

  public data class TL_updatePendingJoinRequests(
    public val peer: TlGen_Peer,
    public val requests_pending: Int,
    public val recent_requesters: List<Long>,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(requests_pending)
      TlGen_Vector.serializeLong(stream, recent_requesters)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7063C3DBU
    }
  }

  public data object TL_updateAttachMenuBots : TlGen_Update() {
    public const val MAGIC: UInt = 0x17B7A20BU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_updateWebViewResultSent(
    public val query_id: Long,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(query_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1592B79DU
    }
  }

  public data class TL_updateBotMenuButton(
    public val bot_id: Long,
    public val button: TlGen_BotMenuButton,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(bot_id)
      button.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x14B85813U
    }
  }

  public data object TL_updateSavedRingtones : TlGen_Update() {
    public const val MAGIC: UInt = 0x74D8BE99U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_updateTranscribedAudio(
    public val pending: Boolean,
    public val peer: TlGen_Peer,
    public val msg_id: Int,
    public val transcription_id: Long,
    public val text: String,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (pending) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(msg_id)
      stream.writeInt64(transcription_id)
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0084CD5AU
    }
  }

  public data object TL_updateReadFeaturedEmojiStickers : TlGen_Update() {
    public const val MAGIC: UInt = 0xFB4C496CU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_updateStickerSets(
    public val masks: Boolean,
    public val emojis: Boolean,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (masks) result = result or 1U
        if (emojis) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
    }

    public companion object {
      public const val MAGIC: UInt = 0x31C24808U
    }
  }

  public data class TL_updateUserEmojiStatus(
    public val user_id: Long,
    public val emoji_status: TlGen_EmojiStatus,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(user_id)
      emoji_status.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x28373599U
    }
  }

  public data object TL_updateRecentEmojiStatuses : TlGen_Update() {
    public const val MAGIC: UInt = 0x30F443DBU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_updateRecentReactions : TlGen_Update() {
    public const val MAGIC: UInt = 0x6F7863F4U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_updateMoveStickerSetToTop(
    public val masks: Boolean,
    public val emojis: Boolean,
    public val stickerset: Long,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (masks) result = result or 1U
        if (emojis) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(stickerset)
    }

    public companion object {
      public const val MAGIC: UInt = 0x86FCCF85U
    }
  }

  public data class TL_updateUserName(
    public val user_id: Long,
    public val first_name: String,
    public val last_name: String,
    public val usernames: List<TlGen_Username>,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(user_id)
      stream.writeString(first_name)
      stream.writeString(last_name)
      TlGen_Vector.serialize(stream, usernames)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA7848924U
    }
  }

  public data class TL_updateUser(
    public val user_id: Long,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(user_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x20529438U
    }
  }

  public data class TL_updateMessagePollVote(
    public val poll_id: Long,
    public val peer: TlGen_Peer,
    public val options: List<List<Byte>>,
    public val qts: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(poll_id)
      peer.serializeToStream(stream)
      TlGen_Vector.serializeBytes(stream, options)
      stream.writeInt32(qts)
    }

    public companion object {
      public const val MAGIC: UInt = 0x24F40E77U
    }
  }

  public data class TL_updateStoryID(
    public val id: Int,
    public val random_id: Long,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeInt64(random_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1BF335B9U
    }
  }

  public data class TL_updateNewAuthorization(
    public val hash: Long,
    public val multiflags_0: Multiflags_0?,
  ) : TlGen_Update() {
    public val unconfirmed: Boolean = multiflags_0 != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (unconfirmed) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(hash)
      multiflags_0?.let { stream.writeInt32(it.date) }
      multiflags_0?.let { stream.writeString(it.device) }
      multiflags_0?.let { stream.writeString(it.location) }
    }

    public data class Multiflags_0(
      public val date: Int,
      public val device: String,
      public val location: String,
    )

    public companion object {
      public const val MAGIC: UInt = 0x8951ABEFU
    }
  }

  public data class TL_updateReadMessagesContents(
    public val messages: List<Int>,
    public val pts: Int,
    public val pts_count: Int,
    public val date: Int?,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (date != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serializeInt(stream, messages)
      stream.writeInt32(pts)
      stream.writeInt32(pts_count)
      date?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xF8227181U
    }
  }

  public data class TL_updatePeerBlocked(
    public val blocked: Boolean,
    public val blocked_my_stories_from: Boolean,
    public val peer_id: TlGen_Peer,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (blocked_my_stories_from) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer_id.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEBE07752U
    }
  }

  public data class TL_updateStory(
    public val peer: TlGen_Peer,
    public val story: TlGen_StoryItem,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      story.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x75B3B798U
    }
  }

  public data class TL_updateReadStories(
    public val peer: TlGen_Peer,
    public val max_id: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(max_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF74E932BU
    }
  }

  public data class TL_updateStoriesStealthMode(
    public val stealth_mode: TlGen_StoriesStealthMode,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stealth_mode.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2C084DC1U
    }
  }

  public data class TL_updateSentStoryReaction(
    public val peer: TlGen_Peer,
    public val story_id: Int,
    public val reaction: TlGen_Reaction,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(story_id)
      reaction.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7D627683U
    }
  }

  public data class TL_updateChannelViewForumAsMessages(
    public val channel_id: Long,
    public val enabled: Boolean,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(channel_id)
      stream.writeBool(enabled)
    }

    public companion object {
      public const val MAGIC: UInt = 0x07B68920U
    }
  }

  public data class TL_updatePeerWallpaper(
    public val wallpaper_overridden: Boolean,
    public val peer: TlGen_Peer,
    public val wallpaper: TlGen_WallPaper?,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (wallpaper != null) result = result or 1U
        if (wallpaper_overridden) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
      wallpaper?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAE3F101DU
    }
  }

  public data class TL_updateSavedDialogPinned(
    public val pinned: Boolean,
    public val peer: TlGen_DialogPeer,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (pinned) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAEAF9E74U
    }
  }

  public data class TL_updatePinnedSavedDialogs(
    public val order: List<TlGen_DialogPeer>?,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (order != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      order?.let { TlGen_Vector.serialize(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x686C85A6U
    }
  }

  public data object TL_updateSavedReactionTags : TlGen_Update() {
    public const val MAGIC: UInt = 0x39C67432U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_updateQuickReplies(
    public val quick_replies: List<TlGen_QuickReply>,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, quick_replies)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF9470AB2U
    }
  }

  public data class TL_updateNewQuickReply(
    public val quick_reply: TlGen_QuickReply,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      quick_reply.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF53DA717U
    }
  }

  public data class TL_updateDeleteQuickReply(
    public val shortcut_id: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(shortcut_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x53E6F1ECU
    }
  }

  public data class TL_updateQuickReplyMessage(
    public val message: TlGen_Message,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      message.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3E050D0FU
    }
  }

  public data class TL_updateDeleteQuickReplyMessages(
    public val shortcut_id: Int,
    public val messages: List<Int>,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(shortcut_id)
      TlGen_Vector.serializeInt(stream, messages)
    }

    public companion object {
      public const val MAGIC: UInt = 0x566FE7CDU
    }
  }

  public data class TL_updateNewStoryReaction(
    public val story_id: Int,
    public val peer: TlGen_Peer,
    public val reaction: TlGen_Reaction,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(story_id)
      peer.serializeToStream(stream)
      reaction.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1824E40BU
    }
  }

  public data class TL_updateStarsRevenueStatus(
    public val peer: TlGen_Peer,
    public val status: TlGen_StarsRevenueStatus,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      status.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA584B019U
    }
  }

  public data class TL_updateMessageExtendedMedia(
    public val peer: TlGen_Peer,
    public val msg_id: Int,
    public val extended_media: List<TlGen_MessageExtendedMedia>,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(msg_id)
      TlGen_Vector.serialize(stream, extended_media)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD5A41724U
    }
  }

  public data class TL_updateBotPurchasedPaidMedia(
    public val user_id: Long,
    public val payload: String,
    public val qts: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(user_id)
      stream.writeString(payload)
      stream.writeInt32(qts)
    }

    public companion object {
      public const val MAGIC: UInt = 0x283BD312U
    }
  }

  public data class TL_updateDeleteScheduledMessages(
    public val peer: TlGen_Peer,
    public val messages: List<Int>,
    public val sent_messages: List<Int>?,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (sent_messages != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
      TlGen_Vector.serializeInt(stream, messages)
      sent_messages?.let { TlGen_Vector.serializeInt(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xF2A71983U
    }
  }

  public data class TL_updateStarsBalance(
    public val balance: TlGen_StarsAmount,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      balance.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4E80A379U
    }
  }

  public data class TL_updatePaidReactionPrivacy(
    public val `private`: TlGen_PaidReactionPrivacy,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      private.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8B725FCEU
    }
  }

  public data class TL_updateSentPhoneCode(
    public val sent_code: TlGen_auth_SentCode,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      sent_code.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x504AA18FU
    }
  }

  public data class TL_updateGroupCallChainBlocks(
    public val call: TlGen_InputGroupCall,
    public val sub_chain_id: Int,
    public val blocks: List<List<Byte>>,
    public val next_offset: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      call.serializeToStream(stream)
      stream.writeInt32(sub_chain_id)
      TlGen_Vector.serializeBytes(stream, blocks)
      stream.writeInt32(next_offset)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA477288FU
    }
  }

  public data class TL_updateDraftMessage(
    public val peer: TlGen_Peer,
    public val top_msg_id: Int?,
    public val saved_peer_id: TlGen_Peer?,
    public val draft: TlGen_DraftMessage,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (top_msg_id != null) result = result or 1U
        if (saved_peer_id != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
      top_msg_id?.let { stream.writeInt32(it) }
      saved_peer_id?.serializeToStream(stream)
      draft.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEDFC111EU
    }
  }

  public data class TL_updateChannelReadMessagesContents(
    public val channel_id: Long,
    public val top_msg_id: Int?,
    public val saved_peer_id: TlGen_Peer?,
    public val messages: List<Int>,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (top_msg_id != null) result = result or 1U
        if (saved_peer_id != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(channel_id)
      top_msg_id?.let { stream.writeInt32(it) }
      saved_peer_id?.serializeToStream(stream)
      TlGen_Vector.serializeInt(stream, messages)
    }

    public companion object {
      public const val MAGIC: UInt = 0x25F324F7U
    }
  }

  public data class TL_updateDialogUnreadMark(
    public val unread: Boolean,
    public val peer: TlGen_DialogPeer,
    public val saved_peer_id: TlGen_Peer?,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (unread) result = result or 1U
        if (saved_peer_id != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
      saved_peer_id?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB658F23EU
    }
  }

  public data class TL_updateMessageReactions(
    public val peer: TlGen_Peer,
    public val msg_id: Int,
    public val top_msg_id: Int?,
    public val saved_peer_id: TlGen_Peer?,
    public val reactions: TlGen_MessageReactions,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (top_msg_id != null) result = result or 1U
        if (saved_peer_id != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(msg_id)
      top_msg_id?.let { stream.writeInt32(it) }
      saved_peer_id?.serializeToStream(stream)
      reactions.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1E297BFAU
    }
  }

  public data class TL_updateReadMonoForumInbox(
    public val channel_id: Long,
    public val saved_peer_id: TlGen_Peer,
    public val read_max_id: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(channel_id)
      saved_peer_id.serializeToStream(stream)
      stream.writeInt32(read_max_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x77B0E372U
    }
  }

  public data class TL_updateReadMonoForumOutbox(
    public val channel_id: Long,
    public val saved_peer_id: TlGen_Peer,
    public val read_max_id: Int,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(channel_id)
      saved_peer_id.serializeToStream(stream)
      stream.writeInt32(read_max_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA4A79376U
    }
  }

  public data class TL_updateMonoForumNoPaidException(
    public val exception: Boolean,
    public val channel_id: Long,
    public val saved_peer_id: TlGen_Peer,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (exception) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(channel_id)
      saved_peer_id.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9F812B08U
    }
  }

  public data class TL_updateUserTyping(
    public val user_id: Long,
    public val top_msg_id: Int?,
    public val action: TlGen_SendMessageAction,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (top_msg_id != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(user_id)
      top_msg_id?.let { stream.writeInt32(it) }
      action.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2A17BF5CU
    }
  }

  public data class TL_updateReadHistoryInbox(
    public val folder_id: Int?,
    public val peer: TlGen_Peer,
    public val top_msg_id: Int?,
    public val max_id: Int,
    public val still_unread_count: Int,
    public val pts: Int,
    public val pts_count: Int,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (folder_id != null) result = result or 1U
        if (top_msg_id != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      folder_id?.let { stream.writeInt32(it) }
      peer.serializeToStream(stream)
      top_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(max_id)
      stream.writeInt32(still_unread_count)
      stream.writeInt32(pts)
      stream.writeInt32(pts_count)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9E84BC99U
    }
  }

  public data class TL_updateGroupCallEncryptedMessage(
    public val call: TlGen_InputGroupCall,
    public val from_id: TlGen_Peer,
    public val encrypted_message: List<Byte>,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      call.serializeToStream(stream)
      from_id.serializeToStream(stream)
      stream.writeByteArray(encrypted_message.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0xC957A766U
    }
  }

  public data class TL_updatePinnedForumTopic(
    public val pinned: Boolean,
    public val peer: TlGen_Peer,
    public val topic_id: Int,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (pinned) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(topic_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x683B2C52U
    }
  }

  public data class TL_updatePinnedForumTopics(
    public val peer: TlGen_Peer,
    public val order: List<Int>?,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (order != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
      order?.let { TlGen_Vector.serializeInt(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xDEF143D0U
    }
  }

  public data class TL_updateGroupCall(
    public val live_story: Boolean,
    public val peer: TlGen_Peer?,
    public val call: TlGen_GroupCall,
  ) : TlGen_Update() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (peer != null) result = result or 2U
        if (live_story) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer?.serializeToStream(stream)
      call.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9D2216E0U
    }
  }

  public data class TL_updateGroupCallMessage(
    public val call: TlGen_InputGroupCall,
    public val message: TlGen_GroupCallMessage,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      call.serializeToStream(stream)
      message.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD8326F0DU
    }
  }

  public data class TL_updateDeleteGroupCallMessages(
    public val call: TlGen_InputGroupCall,
    public val messages: List<Int>,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      call.serializeToStream(stream)
      TlGen_Vector.serializeInt(stream, messages)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3E85E92CU
    }
  }

  public data class TL_updateStarGiftAuctionState(
    public val gift_id: Long,
    public val state: TlGen_StarGiftAuctionState,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(gift_id)
      state.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x48E246C2U
    }
  }

  public data class TL_updateStarGiftAuctionUserState(
    public val gift_id: Long,
    public val user_state: TlGen_StarGiftAuctionUserState,
  ) : TlGen_Update() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(gift_id)
      user_state.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDC58F31EU
    }
  }
}

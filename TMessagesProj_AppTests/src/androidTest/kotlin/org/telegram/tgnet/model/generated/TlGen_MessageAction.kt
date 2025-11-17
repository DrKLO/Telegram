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

public sealed class TlGen_MessageAction : TlGen_Object {
  public data object TL_messageActionEmpty : TlGen_MessageAction() {
    public const val MAGIC: UInt = 0xB6AEF7B0U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_messageActionChatEditTitle(
    public val title: String,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(title)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB5A1CE5AU
    }
  }

  public data class TL_messageActionChatEditPhoto(
    public val photo: TlGen_Photo,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      photo.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7FCB13A8U
    }
  }

  public data object TL_messageActionChatDeletePhoto : TlGen_MessageAction() {
    public const val MAGIC: UInt = 0x95E3FBEFU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_messageActionChannelCreate(
    public val title: String,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(title)
    }

    public companion object {
      public const val MAGIC: UInt = 0x95D2AC92U
    }
  }

  public data object TL_messageActionPinMessage : TlGen_MessageAction() {
    public const val MAGIC: UInt = 0x94BD38EDU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_messageActionHistoryClear : TlGen_MessageAction() {
    public const val MAGIC: UInt = 0x9FBAB604U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_messageActionGameScore(
    public val game_id: Long,
    public val score: Int,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(game_id)
      stream.writeInt32(score)
    }

    public companion object {
      public const val MAGIC: UInt = 0x92A72876U
    }
  }

  public data class TL_messageActionPhoneCall(
    public val video: Boolean,
    public val call_id: Long,
    public val reason: TlGen_PhoneCallDiscardReason?,
    public val duration: Int?,
  ) : TlGen_MessageAction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (reason != null) result = result or 1U
        if (duration != null) result = result or 2U
        if (video) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(call_id)
      reason?.serializeToStream(stream)
      duration?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x80E11A7FU
    }
  }

  public data object TL_messageActionScreenshotTaken : TlGen_MessageAction() {
    public const val MAGIC: UInt = 0x4792929BU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_messageActionCustomAction(
    public val message: String,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(message)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFAE69F56U
    }
  }

  public data class TL_messageActionSecureValuesSent(
    public val types: List<TlGen_SecureValueType>,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, types)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD95C6154U
    }
  }

  public data object TL_messageActionContactSignUp : TlGen_MessageAction() {
    public const val MAGIC: UInt = 0xF3F25F76U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_messageActionGeoProximityReached(
    public val from_id: TlGen_Peer,
    public val to_id: TlGen_Peer,
    public val distance: Int,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      from_id.serializeToStream(stream)
      to_id.serializeToStream(stream)
      stream.writeInt32(distance)
    }

    public companion object {
      public const val MAGIC: UInt = 0x98E0D697U
    }
  }

  public data class TL_messageActionGroupCall(
    public val call: TlGen_InputGroupCall,
    public val duration: Int?,
  ) : TlGen_MessageAction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (duration != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      call.serializeToStream(stream)
      duration?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x7A0D7F42U
    }
  }

  public data class TL_messageActionGroupCallScheduled(
    public val call: TlGen_InputGroupCall,
    public val schedule_date: Int,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      call.serializeToStream(stream)
      stream.writeInt32(schedule_date)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB3A07661U
    }
  }

  public data class TL_messageActionChatCreate(
    public val title: String,
    public val users: List<Long>,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(title)
      TlGen_Vector.serializeLong(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBD47CBADU
    }
  }

  public data class TL_messageActionChatAddUser(
    public val users: List<Long>,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serializeLong(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x15CEFD00U
    }
  }

  public data class TL_messageActionChatDeleteUser(
    public val user_id: Long,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(user_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA43F30CCU
    }
  }

  public data class TL_messageActionChatJoinedByLink(
    public val inviter_id: Long,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(inviter_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x031224C3U
    }
  }

  public data class TL_messageActionChatMigrateTo(
    public val channel_id: Long,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(channel_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE1037F92U
    }
  }

  public data class TL_messageActionChannelMigrateFrom(
    public val title: String,
    public val chat_id: Long,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(title)
      stream.writeInt64(chat_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEA3948E9U
    }
  }

  public data class TL_messageActionInviteToGroupCall(
    public val call: TlGen_InputGroupCall,
    public val users: List<Long>,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      call.serializeToStream(stream)
      TlGen_Vector.serializeLong(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x502F92F7U
    }
  }

  public data object TL_messageActionChatJoinedByRequest : TlGen_MessageAction() {
    public const val MAGIC: UInt = 0xEBBCA3CBU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_messageActionWebViewDataSentMe(
    public val text: String,
    public val `data`: String,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(text)
      stream.writeString(data)
    }

    public companion object {
      public const val MAGIC: UInt = 0x47DD8079U
    }
  }

  public data class TL_messageActionWebViewDataSent(
    public val text: String,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB4C38CB5U
    }
  }

  public data class TL_messageActionTopicCreate(
    public val title_missing: Boolean,
    public val title: String,
    public val icon_color: Int,
    public val icon_emoji_id: Long?,
  ) : TlGen_MessageAction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (icon_emoji_id != null) result = result or 1U
        if (title_missing) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(title)
      stream.writeInt32(icon_color)
      icon_emoji_id?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x0D999256U
    }
  }

  public data class TL_messageActionSetMessagesTTL(
    public val period: Int,
    public val auto_setting_from: Long?,
  ) : TlGen_MessageAction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (auto_setting_from != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(period)
      auto_setting_from?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x3C134D7BU
    }
  }

  public data class TL_messageActionTopicEdit(
    public val title: String?,
    public val icon_emoji_id: Long?,
    public val closed: Boolean?,
    public val hidden: Boolean?,
  ) : TlGen_MessageAction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (title != null) result = result or 1U
        if (icon_emoji_id != null) result = result or 2U
        if (closed != null) result = result or 4U
        if (hidden != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      title?.let { stream.writeString(it) }
      icon_emoji_id?.let { stream.writeInt64(it) }
      closed?.let { stream.writeBool(it) }
      hidden?.let { stream.writeBool(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xC0944820U
    }
  }

  public data class TL_messageActionSuggestProfilePhoto(
    public val photo: TlGen_Photo,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      photo.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x57DE635EU
    }
  }

  public data class TL_messageActionBotAllowed(
    public val attach_menu: Boolean,
    public val from_request: Boolean,
    public val domain: String?,
    public val app: TlGen_BotApp?,
  ) : TlGen_MessageAction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (domain != null) result = result or 1U
        if (attach_menu) result = result or 2U
        if (app != null) result = result or 4U
        if (from_request) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      domain?.let { stream.writeString(it) }
      app?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC516D679U
    }
  }

  public data class TL_messageActionRequestedPeer(
    public val button_id: Int,
    public val peers: List<TlGen_Peer>,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(button_id)
      TlGen_Vector.serialize(stream, peers)
    }

    public companion object {
      public const val MAGIC: UInt = 0x31518E9BU
    }
  }

  public data class TL_messageActionSetChatWallPaper(
    public val same: Boolean,
    public val for_both: Boolean,
    public val wallpaper: TlGen_WallPaper,
  ) : TlGen_MessageAction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (same) result = result or 1U
        if (for_both) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      wallpaper.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5060A3F4U
    }
  }

  public data class TL_messageActionBoostApply(
    public val boosts: Int,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(boosts)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCC02AA6DU
    }
  }

  public data class TL_messageActionPaymentRefunded(
    public val peer: TlGen_Peer,
    public val currency: String,
    public val total_amount: Long,
    public val payload: List<Byte>?,
    public val charge: TlGen_PaymentCharge,
  ) : TlGen_MessageAction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (payload != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
      stream.writeString(currency)
      stream.writeInt64(total_amount)
      payload?.let { stream.writeByteArray(it.toByteArray()) }
      charge.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x41B3E202U
    }
  }

  public data class TL_messageActionGiftStars(
    public val currency: String,
    public val amount: Long,
    public val stars: Long,
    public val transaction_id: String?,
    public val multiflags_0: Multiflags_0?,
  ) : TlGen_MessageAction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (multiflags_0 != null) result = result or 1U
        if (transaction_id != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(currency)
      stream.writeInt64(amount)
      stream.writeInt64(stars)
      multiflags_0?.let { stream.writeString(it.crypto_currency) }
      multiflags_0?.let { stream.writeInt64(it.crypto_amount) }
      transaction_id?.let { stream.writeString(it) }
    }

    public data class Multiflags_0(
      public val crypto_currency: String,
      public val crypto_amount: Long,
    )

    public companion object {
      public const val MAGIC: UInt = 0x45D5B021U
    }
  }

  public data class TL_messageActionGiveawayLaunch(
    public val stars: Long?,
  ) : TlGen_MessageAction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (stars != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stars?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xA80F51E4U
    }
  }

  public data class TL_messageActionGiveawayResults(
    public val stars: Boolean,
    public val winners_count: Int,
    public val unclaimed_count: Int,
  ) : TlGen_MessageAction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (stars) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(winners_count)
      stream.writeInt32(unclaimed_count)
    }

    public companion object {
      public const val MAGIC: UInt = 0x87E2F155U
    }
  }

  public data class TL_messageActionPrizeStars(
    public val unclaimed: Boolean,
    public val stars: Long,
    public val transaction_id: String,
    public val boost_peer: TlGen_Peer,
    public val giveaway_msg_id: Int,
  ) : TlGen_MessageAction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (unclaimed) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(stars)
      stream.writeString(transaction_id)
      boost_peer.serializeToStream(stream)
      stream.writeInt32(giveaway_msg_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB00C47A2U
    }
  }

  public data class TL_messageActionPaymentSentMe(
    public val recurring_init: Boolean,
    public val recurring_used: Boolean,
    public val currency: String,
    public val total_amount: Long,
    public val payload: List<Byte>,
    public val info: TlGen_PaymentRequestedInfo?,
    public val shipping_option_id: String?,
    public val charge: TlGen_PaymentCharge,
    public val subscription_until_date: Int?,
  ) : TlGen_MessageAction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (info != null) result = result or 1U
        if (shipping_option_id != null) result = result or 2U
        if (recurring_init) result = result or 4U
        if (recurring_used) result = result or 8U
        if (subscription_until_date != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(currency)
      stream.writeInt64(total_amount)
      stream.writeByteArray(payload.toByteArray())
      info?.serializeToStream(stream)
      shipping_option_id?.let { stream.writeString(it) }
      charge.serializeToStream(stream)
      subscription_until_date?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xFFA00CCCU
    }
  }

  public data class TL_messageActionPaymentSent(
    public val recurring_init: Boolean,
    public val recurring_used: Boolean,
    public val currency: String,
    public val total_amount: Long,
    public val invoice_slug: String?,
    public val subscription_until_date: Int?,
  ) : TlGen_MessageAction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (invoice_slug != null) result = result or 1U
        if (recurring_init) result = result or 4U
        if (recurring_used) result = result or 8U
        if (subscription_until_date != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(currency)
      stream.writeInt64(total_amount)
      invoice_slug?.let { stream.writeString(it) }
      subscription_until_date?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xC624B16EU
    }
  }

  public data class TL_messageActionPaidMessagesRefunded(
    public val count: Int,
    public val stars: Long,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(count)
      stream.writeInt64(stars)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAC1F1FCDU
    }
  }

  public data class TL_messageActionConferenceCall(
    public val missed: Boolean,
    public val active: Boolean,
    public val video: Boolean,
    public val call_id: Long,
    public val duration: Int?,
    public val other_participants: List<TlGen_Peer>?,
  ) : TlGen_MessageAction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (missed) result = result or 1U
        if (active) result = result or 2U
        if (duration != null) result = result or 4U
        if (other_participants != null) result = result or 8U
        if (video) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(call_id)
      duration?.let { stream.writeInt32(it) }
      other_participants?.let { TlGen_Vector.serialize(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x2FFE2F7AU
    }
  }

  public data class TL_messageActionPaidMessagesPrice(
    public val broadcast_messages_allowed: Boolean,
    public val stars: Long,
  ) : TlGen_MessageAction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (broadcast_messages_allowed) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(stars)
    }

    public companion object {
      public const val MAGIC: UInt = 0x84B88578U
    }
  }

  public data class TL_messageActionTodoCompletions(
    public val completed: List<Int>,
    public val incompleted: List<Int>,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serializeInt(stream, completed)
      TlGen_Vector.serializeInt(stream, incompleted)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCC7C5C89U
    }
  }

  public data class TL_messageActionTodoAppendTasks(
    public val list: List<TlGen_TodoItem>,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, list)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC7EDBC83U
    }
  }

  public data class TL_messageActionSuggestedPostApproval(
    public val rejected: Boolean,
    public val balance_too_low: Boolean,
    public val reject_comment: String?,
    public val schedule_date: Int?,
    public val price: TlGen_StarsAmount?,
  ) : TlGen_MessageAction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (rejected) result = result or 1U
        if (balance_too_low) result = result or 2U
        if (reject_comment != null) result = result or 4U
        if (schedule_date != null) result = result or 8U
        if (price != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      reject_comment?.let { stream.writeString(it) }
      schedule_date?.let { stream.writeInt32(it) }
      price?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEE7A1596U
    }
  }

  public data class TL_messageActionSuggestedPostSuccess(
    public val price: TlGen_StarsAmount,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      price.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x95DDCF69U
    }
  }

  public data class TL_messageActionSuggestedPostRefund(
    public val payer_initiated: Boolean,
  ) : TlGen_MessageAction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (payer_initiated) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
    }

    public companion object {
      public const val MAGIC: UInt = 0x69F916F8U
    }
  }

  public data class TL_messageActionGiftTon(
    public val currency: String,
    public val amount: Long,
    public val crypto_currency: String,
    public val crypto_amount: Long,
    public val transaction_id: String?,
  ) : TlGen_MessageAction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (transaction_id != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(currency)
      stream.writeInt64(amount)
      stream.writeString(crypto_currency)
      stream.writeInt64(crypto_amount)
      transaction_id?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xA8A3C699U
    }
  }

  public data class TL_messageActionSetChatTheme(
    public val theme: TlGen_ChatTheme,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      theme.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB91BBD3AU
    }
  }

  public data class TL_messageActionStarGiftUnique(
    public val upgrade: Boolean,
    public val transferred: Boolean,
    public val saved: Boolean,
    public val refunded: Boolean,
    public val prepaid_upgrade: Boolean,
    public val assigned: Boolean,
    public val gift: TlGen_StarGift,
    public val can_export_at: Int?,
    public val transfer_stars: Long?,
    public val from_id: TlGen_Peer?,
    public val resale_amount: TlGen_StarsAmount?,
    public val can_transfer_at: Int?,
    public val can_resell_at: Int?,
    public val drop_original_details_stars: Long?,
    public val multiflags_7: Multiflags_7?,
  ) : TlGen_MessageAction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (upgrade) result = result or 1U
        if (transferred) result = result or 2U
        if (saved) result = result or 4U
        if (can_export_at != null) result = result or 8U
        if (transfer_stars != null) result = result or 16U
        if (refunded) result = result or 32U
        if (from_id != null) result = result or 64U
        if (multiflags_7 != null) result = result or 128U
        if (resale_amount != null) result = result or 256U
        if (can_transfer_at != null) result = result or 512U
        if (can_resell_at != null) result = result or 1024U
        if (prepaid_upgrade) result = result or 2048U
        if (drop_original_details_stars != null) result = result or 4096U
        if (assigned) result = result or 8192U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      gift.serializeToStream(stream)
      can_export_at?.let { stream.writeInt32(it) }
      transfer_stars?.let { stream.writeInt64(it) }
      from_id?.serializeToStream(stream)
      multiflags_7?.let { it.peer.serializeToStream(stream) }
      multiflags_7?.let { stream.writeInt64(it.saved_id) }
      resale_amount?.serializeToStream(stream)
      can_transfer_at?.let { stream.writeInt32(it) }
      can_resell_at?.let { stream.writeInt32(it) }
      drop_original_details_stars?.let { stream.writeInt64(it) }
    }

    public data class Multiflags_7(
      public val peer: TlGen_Peer,
      public val saved_id: Long,
    )

    public companion object {
      public const val MAGIC: UInt = 0x95728543U
    }
  }

  public data class TL_messageActionSuggestBirthday(
    public val birthday: TlGen_Birthday,
  ) : TlGen_MessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      birthday.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2C8F2A25U
    }
  }

  public data class TL_messageActionGiftPremium(
    public val currency: String,
    public val amount: Long,
    public val days: Int,
    public val message: TlGen_TextWithEntities?,
    public val multiflags_0: Multiflags_0?,
  ) : TlGen_MessageAction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (multiflags_0 != null) result = result or 1U
        if (message != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(currency)
      stream.writeInt64(amount)
      stream.writeInt32(days)
      multiflags_0?.let { stream.writeString(it.crypto_currency) }
      multiflags_0?.let { stream.writeInt64(it.crypto_amount) }
      message?.serializeToStream(stream)
    }

    public data class Multiflags_0(
      public val crypto_currency: String,
      public val crypto_amount: Long,
    )

    public companion object {
      public const val MAGIC: UInt = 0x48E91302U
    }
  }

  public data class TL_messageActionGiftCode(
    public val via_giveaway: Boolean,
    public val unclaimed: Boolean,
    public val boost_peer: TlGen_Peer?,
    public val days: Int,
    public val slug: String,
    public val message: TlGen_TextWithEntities?,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_3: Multiflags_3?,
  ) : TlGen_MessageAction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (via_giveaway) result = result or 1U
        if (boost_peer != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (multiflags_3 != null) result = result or 8U
        if (message != null) result = result or 16U
        if (unclaimed) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      boost_peer?.serializeToStream(stream)
      stream.writeInt32(days)
      stream.writeString(slug)
      multiflags_2?.let { stream.writeString(it.currency) }
      multiflags_2?.let { stream.writeInt64(it.amount) }
      multiflags_3?.let { stream.writeString(it.crypto_currency) }
      multiflags_3?.let { stream.writeInt64(it.crypto_amount) }
      message?.serializeToStream(stream)
    }

    public data class Multiflags_2(
      public val currency: String,
      public val amount: Long,
    )

    public data class Multiflags_3(
      public val crypto_currency: String,
      public val crypto_amount: Long,
    )

    public companion object {
      public const val MAGIC: UInt = 0x31C48347U
    }
  }

  public data class TL_messageActionStarGift(
    public val name_hidden: Boolean,
    public val saved: Boolean,
    public val converted: Boolean,
    public val refunded: Boolean,
    public val can_upgrade: Boolean,
    public val prepaid_upgrade: Boolean,
    public val upgrade_separate: Boolean,
    public val auction_acquired: Boolean,
    public val gift: TlGen_StarGift,
    public val message: TlGen_TextWithEntities?,
    public val convert_stars: Long?,
    public val upgrade_msg_id: Int?,
    public val upgrade_stars: Long?,
    public val from_id: TlGen_Peer?,
    public val prepaid_upgrade_hash: String?,
    public val gift_msg_id: Int?,
    public val to_id: TlGen_Peer?,
    public val multiflags_12: Multiflags_12?,
  ) : TlGen_MessageAction() {
    public val upgraded: Boolean = upgrade_msg_id != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (name_hidden) result = result or 1U
        if (message != null) result = result or 2U
        if (saved) result = result or 4U
        if (converted) result = result or 8U
        if (convert_stars != null) result = result or 16U
        if (upgraded) result = result or 32U
        if (upgrade_stars != null) result = result or 256U
        if (refunded) result = result or 512U
        if (can_upgrade) result = result or 1024U
        if (from_id != null) result = result or 2048U
        if (multiflags_12 != null) result = result or 4096U
        if (prepaid_upgrade) result = result or 8192U
        if (prepaid_upgrade_hash != null) result = result or 16384U
        if (gift_msg_id != null) result = result or 32768U
        if (upgrade_separate) result = result or 65536U
        if (auction_acquired) result = result or 131072U
        if (to_id != null) result = result or 262144U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      gift.serializeToStream(stream)
      message?.serializeToStream(stream)
      convert_stars?.let { stream.writeInt64(it) }
      upgrade_msg_id?.let { stream.writeInt32(it) }
      upgrade_stars?.let { stream.writeInt64(it) }
      from_id?.serializeToStream(stream)
      multiflags_12?.let { it.peer.serializeToStream(stream) }
      multiflags_12?.let { stream.writeInt64(it.saved_id) }
      prepaid_upgrade_hash?.let { stream.writeString(it) }
      gift_msg_id?.let { stream.writeInt32(it) }
      to_id?.serializeToStream(stream)
    }

    public data class Multiflags_12(
      public val peer: TlGen_Peer,
      public val saved_id: Long,
    )

    public companion object {
      public const val MAGIC: UInt = 0xDB596550U
    }
  }

  public data class TL_messageActionChatCreate_layer132(
    public val title: String,
    public val users: List<Int>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(title)
      TlGen_Vector.serializeInt(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA6638B9AU
    }
  }

  public data class TL_messageActionChatAddUser_layer40(
    public val user_id: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(user_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5E3CFC4BU
    }
  }

  public data class TL_messageActionChatDeleteUser_layer132(
    public val user_id: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(user_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB2AE9B0CU
    }
  }

  public data class TL_messageActionChatJoinedByLink_layer132(
    public val inviter_id: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(inviter_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF89CF5E8U
    }
  }

  public data class TL_messageActionChatAddUser_layer132(
    public val users: List<Int>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serializeInt(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x488A7337U
    }
  }

  public data class TL_messageActionChatMigrateTo_layer132(
    public val channel_id: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(channel_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x51BDB021U
    }
  }

  public data class TL_messageActionChannelMigrateFrom_layer132(
    public val title: String,
    public val chat_id: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(title)
      stream.writeInt32(chat_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB055EAEEU
    }
  }

  public data class TL_messageActionPaymentSentMe_layer193(
    public val recurring_init: Boolean,
    public val recurring_used: Boolean,
    public val currency: String,
    public val total_amount: Long,
    public val payload: List<Byte>,
    public val info: TlGen_PaymentRequestedInfo?,
    public val shipping_option_id: String?,
    public val charge: TlGen_PaymentCharge,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (info != null) result = result or 1U
        if (shipping_option_id != null) result = result or 2U
        if (recurring_init) result = result or 4U
        if (recurring_used) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(currency)
      stream.writeInt64(total_amount)
      stream.writeByteArray(payload.toByteArray())
      info?.serializeToStream(stream)
      shipping_option_id?.let { stream.writeString(it) }
      charge.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8F31B327U
    }
  }

  public data class TL_messageActionPaymentSent_layer142(
    public val currency: String,
    public val total_amount: Long,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(currency)
      stream.writeInt64(total_amount)
    }

    public companion object {
      public const val MAGIC: UInt = 0x40699CD0U
    }
  }

  public data class TL_messageActionBotAllowed_layer153(
    public val domain: String,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(domain)
    }

    public companion object {
      public const val MAGIC: UInt = 0xABE9AFFEU
    }
  }

  public data class TL_messageActionContactSignUp_layer90(
    public val silent: Boolean,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (silent) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
    }

    public companion object {
      public const val MAGIC: UInt = 0x70EF8294U
    }
  }

  public data class TL_messageActionInviteToGroupCall_layer132(
    public val call: TlGen_InputGroupCall,
    public val users: List<Int>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      call.serializeToStream(stream)
      TlGen_Vector.serializeInt(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x76B9F11AU
    }
  }

  public data class TL_messageActionSetMessagesTTL_layer149(
    public val period: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(period)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAA1AFBFDU
    }
  }

  public data class TL_messageActionSetChatTheme_layer213(
    public val emoticon: String,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(emoticon)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAA786345U
    }
  }

  public data class TL_messageActionPaymentSent_layer193(
    public val recurring_init: Boolean,
    public val recurring_used: Boolean,
    public val currency: String,
    public val total_amount: Long,
    public val invoice_slug: String?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (invoice_slug != null) result = result or 1U
        if (recurring_init) result = result or 4U
        if (recurring_used) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(currency)
      stream.writeInt64(total_amount)
      invoice_slug?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x96163F56U
    }
  }

  public data class TL_messageActionGiftPremium_layer156(
    public val currency: String,
    public val amount: Long,
    public val months: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(currency)
      stream.writeInt64(amount)
      stream.writeInt32(months)
    }

    public companion object {
      public const val MAGIC: UInt = 0xABA0F5C6U
    }
  }

  public data class TL_messageActionTopicEdit_layer149(
    public val title: String?,
    public val icon_emoji_id: Long?,
    public val closed: Boolean?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (title != null) result = result or 1U
        if (icon_emoji_id != null) result = result or 2U
        if (closed != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      title?.let { stream.writeString(it) }
      icon_emoji_id?.let { stream.writeInt64(it) }
      closed?.let { stream.writeBool(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xB18A431CU
    }
  }

  public data object TL_messageActionAttachMenuBotAllowed_layer153 : TlGen_Object {
    public const val MAGIC: UInt = 0xE7E75F97U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_messageActionRequestedPeer_layer168(
    public val button_id: Int,
    public val peer: TlGen_Peer,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(button_id)
      peer.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFE77345DU
    }
  }

  public data class TL_messageActionGiftPremium_layer189(
    public val currency: String,
    public val amount: Long,
    public val months: Int,
    public val multiflags_0: Multiflags_0?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (multiflags_0 != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(currency)
      stream.writeInt64(amount)
      stream.writeInt32(months)
      multiflags_0?.let { stream.writeString(it.crypto_currency) }
      multiflags_0?.let { stream.writeInt64(it.crypto_amount) }
    }

    public data class Multiflags_0(
      public val crypto_currency: String,
      public val crypto_amount: Long,
    )

    public companion object {
      public const val MAGIC: UInt = 0xC83D6AECU
    }
  }

  public data class TL_messageActionSetChatWallPaper_layer166(
    public val wallpaper: TlGen_WallPaper,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      wallpaper.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBC44A927U
    }
  }

  public data class TL_messageActionSetSameChatWallPaper_layer166(
    public val wallpaper: TlGen_WallPaper,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      wallpaper.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC0787D6DU
    }
  }

  public data class TL_messageActionGiftCode_layer189(
    public val via_giveaway: Boolean,
    public val boost_peer: TlGen_Peer?,
    public val months: Int,
    public val slug: String,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_3: Multiflags_3?,
  ) : TlGen_Object {
    public val unclaimed: Boolean = multiflags_2 != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (via_giveaway) result = result or 1U
        if (boost_peer != null) result = result or 2U
        if (unclaimed) result = result or 4U
        if (multiflags_3 != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      boost_peer?.serializeToStream(stream)
      stream.writeInt32(months)
      stream.writeString(slug)
      multiflags_2?.let { stream.writeString(it.currency) }
      multiflags_2?.let { stream.writeInt64(it.amount) }
      multiflags_3?.let { stream.writeString(it.crypto_currency) }
      multiflags_3?.let { stream.writeInt64(it.crypto_amount) }
    }

    public data class Multiflags_2(
      public val currency: String,
      public val amount: Long,
    )

    public data class Multiflags_3(
      public val crypto_currency: String,
      public val crypto_amount: Long,
    )

    public companion object {
      public const val MAGIC: UInt = 0x678C2E09U
    }
  }

  public data object TL_messageActionGiveawayLaunch_layer186 : TlGen_Object {
    public const val MAGIC: UInt = 0x332BA9EDU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_messageActionGiveawayResults_layer186(
    public val winners_count: Int,
    public val unclaimed_count: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(winners_count)
      stream.writeInt32(unclaimed_count)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2A9FADC5U
    }
  }

  public data class TL_messageActionGiftCode_layer167(
    public val via_giveaway: Boolean,
    public val unclaimed: Boolean,
    public val boost_peer: TlGen_Peer?,
    public val months: Int,
    public val slug: String,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (via_giveaway) result = result or 1U
        if (boost_peer != null) result = result or 2U
        if (unclaimed) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      boost_peer?.serializeToStream(stream)
      stream.writeInt32(months)
      stream.writeString(slug)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD2CFDB0EU
    }
  }

  public data class TL_messageActionStarGift_layer192(
    public val name_hidden: Boolean,
    public val saved: Boolean,
    public val converted: Boolean,
    public val gift: TlGen_StarGift,
    public val message: TlGen_TextWithEntities?,
    public val convert_stars: Long,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (name_hidden) result = result or 1U
        if (message != null) result = result or 2U
        if (saved) result = result or 4U
        if (converted) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      gift.serializeToStream(stream)
      message?.serializeToStream(stream)
      stream.writeInt64(convert_stars)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9BB3EF44U
    }
  }

  public data class TL_messageActionGiftPremium_layer216(
    public val currency: String,
    public val amount: Long,
    public val months: Int,
    public val message: TlGen_TextWithEntities?,
    public val multiflags_0: Multiflags_0?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (multiflags_0 != null) result = result or 1U
        if (message != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(currency)
      stream.writeInt64(amount)
      stream.writeInt32(months)
      multiflags_0?.let { stream.writeString(it.crypto_currency) }
      multiflags_0?.let { stream.writeInt64(it.crypto_amount) }
      message?.serializeToStream(stream)
    }

    public data class Multiflags_0(
      public val crypto_currency: String,
      public val crypto_amount: Long,
    )

    public companion object {
      public const val MAGIC: UInt = 0x6C6274FAU
    }
  }

  public data class TL_messageActionGiftCode_layer216(
    public val via_giveaway: Boolean,
    public val unclaimed: Boolean,
    public val boost_peer: TlGen_Peer?,
    public val months: Int,
    public val slug: String,
    public val message: TlGen_TextWithEntities?,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_3: Multiflags_3?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (via_giveaway) result = result or 1U
        if (boost_peer != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (multiflags_3 != null) result = result or 8U
        if (message != null) result = result or 16U
        if (unclaimed) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      boost_peer?.serializeToStream(stream)
      stream.writeInt32(months)
      stream.writeString(slug)
      multiflags_2?.let { stream.writeString(it.currency) }
      multiflags_2?.let { stream.writeInt64(it.amount) }
      multiflags_3?.let { stream.writeString(it.crypto_currency) }
      multiflags_3?.let { stream.writeInt64(it.crypto_amount) }
      message?.serializeToStream(stream)
    }

    public data class Multiflags_2(
      public val currency: String,
      public val amount: Long,
    )

    public data class Multiflags_3(
      public val crypto_currency: String,
      public val crypto_amount: Long,
    )

    public companion object {
      public const val MAGIC: UInt = 0x56D03994U
    }
  }

  public data class TL_messageActionStarGift_layer195(
    public val name_hidden: Boolean,
    public val saved: Boolean,
    public val converted: Boolean,
    public val gift: TlGen_StarGift,
    public val message: TlGen_TextWithEntities?,
    public val convert_stars: Long?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (name_hidden) result = result or 1U
        if (message != null) result = result or 2U
        if (saved) result = result or 4U
        if (converted) result = result or 8U
        if (convert_stars != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      gift.serializeToStream(stream)
      message?.serializeToStream(stream)
      convert_stars?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x08557637U
    }
  }

  public data class TL_messageActionStarGift_layer197(
    public val name_hidden: Boolean,
    public val saved: Boolean,
    public val converted: Boolean,
    public val refunded: Boolean,
    public val can_upgrade: Boolean,
    public val gift: TlGen_StarGift,
    public val message: TlGen_TextWithEntities?,
    public val convert_stars: Long?,
    public val upgrade_msg_id: Int?,
    public val upgrade_stars: Long?,
  ) : TlGen_Object {
    public val upgraded: Boolean = upgrade_msg_id != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (name_hidden) result = result or 1U
        if (message != null) result = result or 2U
        if (saved) result = result or 4U
        if (converted) result = result or 8U
        if (convert_stars != null) result = result or 16U
        if (upgraded) result = result or 32U
        if (upgrade_stars != null) result = result or 256U
        if (refunded) result = result or 512U
        if (can_upgrade) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      gift.serializeToStream(stream)
      message?.serializeToStream(stream)
      convert_stars?.let { stream.writeInt64(it) }
      upgrade_msg_id?.let { stream.writeInt32(it) }
      upgrade_stars?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xD8F4F0A7U
    }
  }

  public data class TL_messageActionStarGiftUnique_layer197(
    public val upgrade: Boolean,
    public val transferred: Boolean,
    public val saved: Boolean,
    public val refunded: Boolean,
    public val gift: TlGen_StarGift,
    public val can_export_at: Int?,
    public val transfer_stars: Long?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (upgrade) result = result or 1U
        if (transferred) result = result or 2U
        if (saved) result = result or 4U
        if (can_export_at != null) result = result or 8U
        if (transfer_stars != null) result = result or 16U
        if (refunded) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      gift.serializeToStream(stream)
      can_export_at?.let { stream.writeInt32(it) }
      transfer_stars?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x26077B99U
    }
  }

  public data class TL_messageActionStarGift_layer211(
    public val name_hidden: Boolean,
    public val saved: Boolean,
    public val converted: Boolean,
    public val refunded: Boolean,
    public val can_upgrade: Boolean,
    public val gift: TlGen_StarGift,
    public val message: TlGen_TextWithEntities?,
    public val convert_stars: Long?,
    public val upgrade_msg_id: Int?,
    public val upgrade_stars: Long?,
    public val from_id: TlGen_Peer?,
    public val multiflags_12: Multiflags_12?,
  ) : TlGen_Object {
    public val upgraded: Boolean = upgrade_msg_id != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (name_hidden) result = result or 1U
        if (message != null) result = result or 2U
        if (saved) result = result or 4U
        if (converted) result = result or 8U
        if (convert_stars != null) result = result or 16U
        if (upgraded) result = result or 32U
        if (upgrade_stars != null) result = result or 256U
        if (refunded) result = result or 512U
        if (can_upgrade) result = result or 1024U
        if (from_id != null) result = result or 2048U
        if (multiflags_12 != null) result = result or 4096U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      gift.serializeToStream(stream)
      message?.serializeToStream(stream)
      convert_stars?.let { stream.writeInt64(it) }
      upgrade_msg_id?.let { stream.writeInt32(it) }
      upgrade_stars?.let { stream.writeInt64(it) }
      from_id?.serializeToStream(stream)
      multiflags_12?.let { it.peer.serializeToStream(stream) }
      multiflags_12?.let { stream.writeInt64(it.saved_id) }
    }

    public data class Multiflags_12(
      public val peer: TlGen_Peer,
      public val saved_id: Long,
    )

    public companion object {
      public const val MAGIC: UInt = 0x4717E8A4U
    }
  }

  public data class TL_messageActionStarGiftUnique_layer202(
    public val upgrade: Boolean,
    public val transferred: Boolean,
    public val saved: Boolean,
    public val refunded: Boolean,
    public val gift: TlGen_StarGift,
    public val can_export_at: Int?,
    public val transfer_stars: Long?,
    public val from_id: TlGen_Peer?,
    public val multiflags_7: Multiflags_7?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (upgrade) result = result or 1U
        if (transferred) result = result or 2U
        if (saved) result = result or 4U
        if (can_export_at != null) result = result or 8U
        if (transfer_stars != null) result = result or 16U
        if (refunded) result = result or 32U
        if (from_id != null) result = result or 64U
        if (multiflags_7 != null) result = result or 128U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      gift.serializeToStream(stream)
      can_export_at?.let { stream.writeInt32(it) }
      transfer_stars?.let { stream.writeInt64(it) }
      from_id?.serializeToStream(stream)
      multiflags_7?.let { it.peer.serializeToStream(stream) }
      multiflags_7?.let { stream.writeInt64(it.saved_id) }
    }

    public data class Multiflags_7(
      public val peer: TlGen_Peer,
      public val saved_id: Long,
    )

    public companion object {
      public const val MAGIC: UInt = 0xACDFCB81U
    }
  }

  public data class TL_messageActionPaidMessagesPrice_layer203(
    public val stars: Long,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(stars)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBCD71419U
    }
  }

  public data class TL_messageActionStarGiftUnique_layer210(
    public val upgrade: Boolean,
    public val transferred: Boolean,
    public val saved: Boolean,
    public val refunded: Boolean,
    public val gift: TlGen_StarGift,
    public val can_export_at: Int?,
    public val transfer_stars: Long?,
    public val from_id: TlGen_Peer?,
    public val resale_stars: Long?,
    public val can_transfer_at: Int?,
    public val can_resell_at: Int?,
    public val multiflags_7: Multiflags_7?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (upgrade) result = result or 1U
        if (transferred) result = result or 2U
        if (saved) result = result or 4U
        if (can_export_at != null) result = result or 8U
        if (transfer_stars != null) result = result or 16U
        if (refunded) result = result or 32U
        if (from_id != null) result = result or 64U
        if (multiflags_7 != null) result = result or 128U
        if (resale_stars != null) result = result or 256U
        if (can_transfer_at != null) result = result or 512U
        if (can_resell_at != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      gift.serializeToStream(stream)
      can_export_at?.let { stream.writeInt32(it) }
      transfer_stars?.let { stream.writeInt64(it) }
      from_id?.serializeToStream(stream)
      multiflags_7?.let { it.peer.serializeToStream(stream) }
      multiflags_7?.let { stream.writeInt64(it.saved_id) }
      resale_stars?.let { stream.writeInt64(it) }
      can_transfer_at?.let { stream.writeInt32(it) }
      can_resell_at?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_7(
      public val peer: TlGen_Peer,
      public val saved_id: Long,
    )

    public companion object {
      public const val MAGIC: UInt = 0x2E3AE60EU
    }
  }

  public data class TL_messageActionStarGiftUnique_layer214(
    public val upgrade: Boolean,
    public val transferred: Boolean,
    public val saved: Boolean,
    public val refunded: Boolean,
    public val prepaid_upgrade: Boolean,
    public val gift: TlGen_StarGift,
    public val can_export_at: Int?,
    public val transfer_stars: Long?,
    public val from_id: TlGen_Peer?,
    public val resale_amount: TlGen_StarsAmount?,
    public val can_transfer_at: Int?,
    public val can_resell_at: Int?,
    public val multiflags_7: Multiflags_7?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (upgrade) result = result or 1U
        if (transferred) result = result or 2U
        if (saved) result = result or 4U
        if (can_export_at != null) result = result or 8U
        if (transfer_stars != null) result = result or 16U
        if (refunded) result = result or 32U
        if (from_id != null) result = result or 64U
        if (multiflags_7 != null) result = result or 128U
        if (resale_amount != null) result = result or 256U
        if (can_transfer_at != null) result = result or 512U
        if (can_resell_at != null) result = result or 1024U
        if (prepaid_upgrade) result = result or 2048U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      gift.serializeToStream(stream)
      can_export_at?.let { stream.writeInt32(it) }
      transfer_stars?.let { stream.writeInt64(it) }
      from_id?.serializeToStream(stream)
      multiflags_7?.let { it.peer.serializeToStream(stream) }
      multiflags_7?.let { stream.writeInt64(it.saved_id) }
      resale_amount?.serializeToStream(stream)
      can_transfer_at?.let { stream.writeInt32(it) }
      can_resell_at?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_7(
      public val peer: TlGen_Peer,
      public val saved_id: Long,
    )

    public companion object {
      public const val MAGIC: UInt = 0x34F762F3U
    }
  }

  public data class TL_messageActionStarGift_layer217(
    public val name_hidden: Boolean,
    public val saved: Boolean,
    public val converted: Boolean,
    public val refunded: Boolean,
    public val can_upgrade: Boolean,
    public val prepaid_upgrade: Boolean,
    public val upgrade_separate: Boolean,
    public val gift: TlGen_StarGift,
    public val message: TlGen_TextWithEntities?,
    public val convert_stars: Long?,
    public val upgrade_msg_id: Int?,
    public val upgrade_stars: Long?,
    public val from_id: TlGen_Peer?,
    public val prepaid_upgrade_hash: String?,
    public val gift_msg_id: Int?,
    public val multiflags_12: Multiflags_12?,
  ) : TlGen_Object {
    public val upgraded: Boolean = upgrade_msg_id != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (name_hidden) result = result or 1U
        if (message != null) result = result or 2U
        if (saved) result = result or 4U
        if (converted) result = result or 8U
        if (convert_stars != null) result = result or 16U
        if (upgraded) result = result or 32U
        if (upgrade_stars != null) result = result or 256U
        if (refunded) result = result or 512U
        if (can_upgrade) result = result or 1024U
        if (from_id != null) result = result or 2048U
        if (multiflags_12 != null) result = result or 4096U
        if (prepaid_upgrade) result = result or 8192U
        if (prepaid_upgrade_hash != null) result = result or 16384U
        if (gift_msg_id != null) result = result or 32768U
        if (upgrade_separate) result = result or 65536U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      gift.serializeToStream(stream)
      message?.serializeToStream(stream)
      convert_stars?.let { stream.writeInt64(it) }
      upgrade_msg_id?.let { stream.writeInt32(it) }
      upgrade_stars?.let { stream.writeInt64(it) }
      from_id?.serializeToStream(stream)
      multiflags_12?.let { it.peer.serializeToStream(stream) }
      multiflags_12?.let { stream.writeInt64(it.saved_id) }
      prepaid_upgrade_hash?.let { stream.writeString(it) }
      gift_msg_id?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_12(
      public val peer: TlGen_Peer,
      public val saved_id: Long,
    )

    public companion object {
      public const val MAGIC: UInt = 0xF24DE7FAU
    }
  }
}

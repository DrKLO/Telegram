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

public sealed class TlGen_Message : TlGen_Object {
  public data class TL_messageEmpty(
    public val id: Int,
    public val peer_id: TlGen_Peer?,
  ) : TlGen_Message() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (peer_id != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      peer_id?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x90A6CA84U
    }
  }

  public data class TL_messageService(
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val reactions_are_possible: Boolean,
    public val silent: Boolean,
    public val post: Boolean,
    public val legacy: Boolean,
    public val id: Int,
    public val from_id: TlGen_Peer?,
    public val peer_id: TlGen_Peer,
    public val saved_peer_id: TlGen_Peer?,
    public val reply_to: TlGen_MessageReplyHeader?,
    public val date: Int,
    public val action: TlGen_MessageAction,
    public val reactions: TlGen_MessageReactions?,
    public val ttl_period: Int?,
  ) : TlGen_Message() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (reply_to != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (from_id != null) result = result or 256U
        if (reactions_are_possible) result = result or 512U
        if (silent) result = result or 8192U
        if (post) result = result or 16384U
        if (legacy) result = result or 524288U
        if (reactions != null) result = result or 1048576U
        if (ttl_period != null) result = result or 33554432U
        if (saved_peer_id != null) result = result or 268435456U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      from_id?.serializeToStream(stream)
      peer_id.serializeToStream(stream)
      saved_peer_id?.serializeToStream(stream)
      reply_to?.serializeToStream(stream)
      stream.writeInt32(date)
      action.serializeToStream(stream)
      reactions?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x7A800E0AU
    }
  }

  public data class TL_message(
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val silent: Boolean,
    public val post: Boolean,
    public val from_scheduled: Boolean,
    public val legacy: Boolean,
    public val edit_hide: Boolean,
    public val pinned: Boolean,
    public val noforwards: Boolean,
    public val invert_media: Boolean,
    public val offline: Boolean,
    public val video_processing_pending: Boolean,
    public val paid_suggested_post_stars: Boolean,
    public val paid_suggested_post_ton: Boolean,
    public val id: Int,
    public val from_id: TlGen_Peer?,
    public val from_boosts_applied: Int?,
    public val peer_id: TlGen_Peer,
    public val saved_peer_id: TlGen_Peer?,
    public val fwd_from: TlGen_MessageFwdHeader?,
    public val via_bot_id: Long?,
    public val via_business_bot_id: Long?,
    public val reply_to: TlGen_MessageReplyHeader?,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia?,
    public val reply_markup: TlGen_ReplyMarkup?,
    public val entities: List<TlGen_MessageEntity>?,
    public val replies: TlGen_MessageReplies?,
    public val edit_date: Int?,
    public val post_author: String?,
    public val grouped_id: Long?,
    public val reactions: TlGen_MessageReactions?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val ttl_period: Int?,
    public val quick_reply_shortcut_id: Int?,
    public val effect: Long?,
    public val factcheck: TlGen_FactCheck?,
    public val report_delivery_until_date: Int?,
    public val paid_message_stars: Long?,
    public val suggested_post: TlGen_SuggestedPost?,
    public val schedule_repeat_period: Int?,
    public val multiflags_10: Multiflags_10?,
  ) : TlGen_Message() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (fwd_from != null) result = result or 4U
        if (reply_to != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (reply_markup != null) result = result or 64U
        if (entities != null) result = result or 128U
        if (from_id != null) result = result or 256U
        if (media != null) result = result or 512U
        if (multiflags_10 != null) result = result or 1024U
        if (via_bot_id != null) result = result or 2048U
        if (silent) result = result or 8192U
        if (post) result = result or 16384U
        if (edit_date != null) result = result or 32768U
        if (post_author != null) result = result or 65536U
        if (grouped_id != null) result = result or 131072U
        if (from_scheduled) result = result or 262144U
        if (legacy) result = result or 524288U
        if (reactions != null) result = result or 1048576U
        if (edit_hide) result = result or 2097152U
        if (restriction_reason != null) result = result or 4194304U
        if (replies != null) result = result or 8388608U
        if (pinned) result = result or 16777216U
        if (ttl_period != null) result = result or 33554432U
        if (noforwards) result = result or 67108864U
        if (invert_media) result = result or 134217728U
        if (saved_peer_id != null) result = result or 268435456U
        if (from_boosts_applied != null) result = result or 536870912U
        if (quick_reply_shortcut_id != null) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (via_business_bot_id != null) result = result or 1U
        if (offline) result = result or 2U
        if (effect != null) result = result or 4U
        if (factcheck != null) result = result or 8U
        if (video_processing_pending) result = result or 16U
        if (report_delivery_until_date != null) result = result or 32U
        if (paid_message_stars != null) result = result or 64U
        if (suggested_post != null) result = result or 128U
        if (paid_suggested_post_stars) result = result or 256U
        if (paid_suggested_post_ton) result = result or 512U
        if (schedule_repeat_period != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt32(id)
      from_id?.serializeToStream(stream)
      from_boosts_applied?.let { stream.writeInt32(it) }
      peer_id.serializeToStream(stream)
      saved_peer_id?.serializeToStream(stream)
      fwd_from?.serializeToStream(stream)
      via_bot_id?.let { stream.writeInt64(it) }
      via_business_bot_id?.let { stream.writeInt64(it) }
      reply_to?.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeString(message)
      media?.serializeToStream(stream)
      reply_markup?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      multiflags_10?.let { stream.writeInt32(it.views) }
      multiflags_10?.let { stream.writeInt32(it.forwards) }
      replies?.serializeToStream(stream)
      edit_date?.let { stream.writeInt32(it) }
      post_author?.let { stream.writeString(it) }
      grouped_id?.let { stream.writeInt64(it) }
      reactions?.serializeToStream(stream)
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      ttl_period?.let { stream.writeInt32(it) }
      quick_reply_shortcut_id?.let { stream.writeInt32(it) }
      effect?.let { stream.writeInt64(it) }
      factcheck?.serializeToStream(stream)
      report_delivery_until_date?.let { stream.writeInt32(it) }
      paid_message_stars?.let { stream.writeInt64(it) }
      suggested_post?.serializeToStream(stream)
      schedule_repeat_period?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_10(
      public val views: Int,
      public val forwards: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xB92F76CFU
    }
  }

  public data class TL_messageEmpty_layer122(
    public val id: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x83E5DE54U
    }
  }

  public data class TL_message_layer16(
    public val id: Int,
    public val from_id: Int,
    public val to_id: TlGen_Peer,
    public val `out`: Boolean,
    public val unread: Boolean,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeInt32(from_id)
      to_id.serializeToStream(stream)
      stream.writeBool(out)
      stream.writeBool(unread)
      stream.writeInt32(date)
      stream.writeString(message)
      media.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x22EB6ABAU
    }
  }

  public data class TL_messageForwarded_layer16(
    public val id: Int,
    public val fwd_from_id: Int,
    public val fwd_date: Int,
    public val from_id: Int,
    public val to_id: TlGen_Peer,
    public val `out`: Boolean,
    public val unread: Boolean,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeInt32(fwd_from_id)
      stream.writeInt32(fwd_date)
      stream.writeInt32(from_id)
      to_id.serializeToStream(stream)
      stream.writeBool(out)
      stream.writeBool(unread)
      stream.writeInt32(date)
      stream.writeString(message)
      media.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x05F46804U
    }
  }

  public data class TL_messageService_layer16(
    public val id: Int,
    public val from_id: Int,
    public val to_id: TlGen_Peer,
    public val `out`: Boolean,
    public val unread: Boolean,
    public val date: Int,
    public val action: TlGen_MessageAction,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeInt32(from_id)
      to_id.serializeToStream(stream)
      stream.writeBool(out)
      stream.writeBool(unread)
      stream.writeInt32(date)
      action.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9F8D60BBU
    }
  }

  public data class TL_message_layer24(
    public val flags: Int,
    public val id: Int,
    public val from_id: Int,
    public val to_id: TlGen_Peer,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags)
      stream.writeInt32(id)
      stream.writeInt32(from_id)
      to_id.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeString(message)
      media.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x567699B3U
    }
  }

  public data class TL_messageForwarded_layer24(
    public val flags: Int,
    public val id: Int,
    public val fwd_from_id: Int,
    public val fwd_date: Int,
    public val from_id: Int,
    public val to_id: TlGen_Peer,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags)
      stream.writeInt32(id)
      stream.writeInt32(fwd_from_id)
      stream.writeInt32(fwd_date)
      stream.writeInt32(from_id)
      to_id.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeString(message)
      media.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA367E716U
    }
  }

  public data class TL_messageService_layer37(
    public val flags: Int,
    public val id: Int,
    public val from_id: Int,
    public val to_id: TlGen_Peer,
    public val date: Int,
    public val action: TlGen_MessageAction,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags)
      stream.writeInt32(id)
      stream.writeInt32(from_id)
      to_id.serializeToStream(stream)
      stream.writeInt32(date)
      action.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1D86F70EU
    }
  }

  public data class TL_message_layer30(
    public val unread: Boolean,
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val id: Int,
    public val from_id: Int,
    public val to_id: TlGen_Peer,
    public val reply_to_msg_id: Int?,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia,
    public val multiflags_2: Multiflags_2?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (unread) result = result or 1U
        if (out) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (reply_to_msg_id != null) result = result or 8U
        if (mentioned) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeInt32(from_id)
      to_id.serializeToStream(stream)
      multiflags_2?.let { stream.writeInt32(it.fwd_from_id) }
      multiflags_2?.let { stream.writeInt32(it.fwd_date) }
      reply_to_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(date)
      stream.writeString(message)
      media.serializeToStream(stream)
    }

    public data class Multiflags_2(
      public val fwd_from_id: Int,
      public val fwd_date: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xA7AB1991U
    }
  }

  public data class TL_message_layer33(
    public val unread: Boolean,
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val id: Int,
    public val from_id: Int,
    public val to_id: TlGen_Peer,
    public val reply_to_msg_id: Int?,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia,
    public val reply_markup: TlGen_ReplyMarkup?,
    public val multiflags_2: Multiflags_2?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (unread) result = result or 1U
        if (out) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (reply_to_msg_id != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (reply_markup != null) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeInt32(from_id)
      to_id.serializeToStream(stream)
      multiflags_2?.let { stream.writeInt32(it.fwd_from_id) }
      multiflags_2?.let { stream.writeInt32(it.fwd_date) }
      reply_to_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(date)
      stream.writeString(message)
      media.serializeToStream(stream)
      reply_markup?.serializeToStream(stream)
    }

    public data class Multiflags_2(
      public val fwd_from_id: Int,
      public val fwd_date: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xC3060325U
    }
  }

  public data class TL_message_layer35(
    public val unread: Boolean,
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val id: Int,
    public val from_id: Int,
    public val to_id: TlGen_Peer,
    public val reply_to_msg_id: Int?,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia,
    public val reply_markup: TlGen_ReplyMarkup?,
    public val entities: List<TlGen_MessageEntity>?,
    public val multiflags_2: Multiflags_2?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (unread) result = result or 1U
        if (out) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (reply_to_msg_id != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (reply_markup != null) result = result or 64U
        if (entities != null) result = result or 128U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeInt32(from_id)
      to_id.serializeToStream(stream)
      multiflags_2?.let { stream.writeInt32(it.fwd_from_id) }
      multiflags_2?.let { stream.writeInt32(it.fwd_date) }
      reply_to_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(date)
      stream.writeString(message)
      media.serializeToStream(stream)
      reply_markup?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
    }

    public data class Multiflags_2(
      public val fwd_from_id: Int,
      public val fwd_date: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xF07814C8U
    }
  }

  public data class TL_message_layer37(
    public val unread: Boolean,
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val id: Int,
    public val from_id: Int,
    public val to_id: TlGen_Peer,
    public val reply_to_msg_id: Int?,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia?,
    public val reply_markup: TlGen_ReplyMarkup?,
    public val entities: List<TlGen_MessageEntity>?,
    public val multiflags_2: Multiflags_2?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (unread) result = result or 1U
        if (out) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (reply_to_msg_id != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (reply_markup != null) result = result or 64U
        if (entities != null) result = result or 128U
        if (media != null) result = result or 512U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeInt32(from_id)
      to_id.serializeToStream(stream)
      multiflags_2?.let { stream.writeInt32(it.fwd_from_id) }
      multiflags_2?.let { stream.writeInt32(it.fwd_date) }
      reply_to_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(date)
      stream.writeString(message)
      media?.serializeToStream(stream)
      reply_markup?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
    }

    public data class Multiflags_2(
      public val fwd_from_id: Int,
      public val fwd_date: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x2BEBFA86U
    }
  }

  public data class TL_message_layer44(
    public val unread: Boolean,
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val id: Int,
    public val from_id: Int?,
    public val to_id: TlGen_Peer,
    public val reply_to_msg_id: Int?,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia?,
    public val reply_markup: TlGen_ReplyMarkup?,
    public val entities: List<TlGen_MessageEntity>?,
    public val views: Int?,
    public val multiflags_2: Multiflags_2?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (unread) result = result or 1U
        if (out) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (reply_to_msg_id != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (reply_markup != null) result = result or 64U
        if (entities != null) result = result or 128U
        if (from_id != null) result = result or 256U
        if (media != null) result = result or 512U
        if (views != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      from_id?.let { stream.writeInt32(it) }
      to_id.serializeToStream(stream)
      multiflags_2?.let { it.fwd_from_id.serializeToStream(stream) }
      multiflags_2?.let { stream.writeInt32(it.fwd_date) }
      reply_to_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(date)
      stream.writeString(message)
      media?.serializeToStream(stream)
      reply_markup?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      views?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_2(
      public val fwd_from_id: TlGen_Peer,
      public val fwd_date: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x5BA66C13U
    }
  }

  public data class TL_messageService_layer48(
    public val unread: Boolean,
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val silent: Boolean,
    public val post: Boolean,
    public val id: Int,
    public val from_id: Int?,
    public val to_id: TlGen_Peer,
    public val date: Int,
    public val action: TlGen_MessageAction,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (unread) result = result or 1U
        if (out) result = result or 2U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (from_id != null) result = result or 256U
        if (silent) result = result or 8192U
        if (post) result = result or 16384U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      from_id?.let { stream.writeInt32(it) }
      to_id.serializeToStream(stream)
      stream.writeInt32(date)
      action.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC06B9607U
    }
  }

  public data class TL_message_layer47(
    public val unread: Boolean,
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val id: Int,
    public val from_id: Int?,
    public val to_id: TlGen_Peer,
    public val via_bot_id: Int?,
    public val reply_to_msg_id: Int?,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia?,
    public val reply_markup: TlGen_ReplyMarkup?,
    public val entities: List<TlGen_MessageEntity>?,
    public val views: Int?,
    public val multiflags_2: Multiflags_2?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (unread) result = result or 1U
        if (out) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (reply_to_msg_id != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (reply_markup != null) result = result or 64U
        if (entities != null) result = result or 128U
        if (from_id != null) result = result or 256U
        if (media != null) result = result or 512U
        if (views != null) result = result or 1024U
        if (via_bot_id != null) result = result or 2048U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      from_id?.let { stream.writeInt32(it) }
      to_id.serializeToStream(stream)
      multiflags_2?.let { it.fwd_from_id.serializeToStream(stream) }
      multiflags_2?.let { stream.writeInt32(it.fwd_date) }
      via_bot_id?.let { stream.writeInt32(it) }
      reply_to_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(date)
      stream.writeString(message)
      media?.serializeToStream(stream)
      reply_markup?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      views?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_2(
      public val fwd_from_id: TlGen_Peer,
      public val fwd_date: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xC992E15CU
    }
  }

  public data class TL_message_layer69(
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val silent: Boolean,
    public val post: Boolean,
    public val id: Int,
    public val from_id: Int?,
    public val to_id: TlGen_Peer,
    public val fwd_from: TlGen_MessageFwdHeader?,
    public val via_bot_id: Int?,
    public val reply_to_msg_id: Int?,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia?,
    public val reply_markup: TlGen_ReplyMarkup?,
    public val entities: List<TlGen_MessageEntity>?,
    public val views: Int?,
    public val edit_date: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (fwd_from != null) result = result or 4U
        if (reply_to_msg_id != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (reply_markup != null) result = result or 64U
        if (entities != null) result = result or 128U
        if (from_id != null) result = result or 256U
        if (media != null) result = result or 512U
        if (views != null) result = result or 1024U
        if (via_bot_id != null) result = result or 2048U
        if (silent) result = result or 8192U
        if (post) result = result or 16384U
        if (edit_date != null) result = result or 32768U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      from_id?.let { stream.writeInt32(it) }
      to_id.serializeToStream(stream)
      fwd_from?.serializeToStream(stream)
      via_bot_id?.let { stream.writeInt32(it) }
      reply_to_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(date)
      stream.writeString(message)
      media?.serializeToStream(stream)
      reply_markup?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      views?.let { stream.writeInt32(it) }
      edit_date?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xC09BE45FU
    }
  }

  public data class TL_messageService_layer118(
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val silent: Boolean,
    public val post: Boolean,
    public val legacy: Boolean,
    public val id: Int,
    public val from_id: Int?,
    public val to_id: TlGen_Peer,
    public val reply_to_msg_id: Int?,
    public val date: Int,
    public val action: TlGen_MessageAction,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (reply_to_msg_id != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (from_id != null) result = result or 256U
        if (silent) result = result or 8192U
        if (post) result = result or 16384U
        if (legacy) result = result or 524288U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      from_id?.let { stream.writeInt32(it) }
      to_id.serializeToStream(stream)
      reply_to_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(date)
      action.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9E19A1F6U
    }
  }

  public data class TL_message_layer72(
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val silent: Boolean,
    public val post: Boolean,
    public val id: Int,
    public val from_id: Int?,
    public val to_id: TlGen_Peer,
    public val fwd_from: TlGen_MessageFwdHeader?,
    public val via_bot_id: Int?,
    public val reply_to_msg_id: Int?,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia?,
    public val reply_markup: TlGen_ReplyMarkup?,
    public val entities: List<TlGen_MessageEntity>?,
    public val views: Int?,
    public val edit_date: Int?,
    public val post_author: String?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (fwd_from != null) result = result or 4U
        if (reply_to_msg_id != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (reply_markup != null) result = result or 64U
        if (entities != null) result = result or 128U
        if (from_id != null) result = result or 256U
        if (media != null) result = result or 512U
        if (views != null) result = result or 1024U
        if (via_bot_id != null) result = result or 2048U
        if (silent) result = result or 8192U
        if (post) result = result or 16384U
        if (edit_date != null) result = result or 32768U
        if (post_author != null) result = result or 65536U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      from_id?.let { stream.writeInt32(it) }
      to_id.serializeToStream(stream)
      fwd_from?.serializeToStream(stream)
      via_bot_id?.let { stream.writeInt32(it) }
      reply_to_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(date)
      stream.writeString(message)
      media?.serializeToStream(stream)
      reply_markup?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      views?.let { stream.writeInt32(it) }
      edit_date?.let { stream.writeInt32(it) }
      post_author?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x90DDDC11U
    }
  }

  public data class TL_message_layer104(
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val silent: Boolean,
    public val post: Boolean,
    public val legacy: Boolean,
    public val id: Int,
    public val from_id: Int?,
    public val to_id: TlGen_Peer,
    public val fwd_from: TlGen_MessageFwdHeader?,
    public val via_bot_id: Int?,
    public val reply_to_msg_id: Int?,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia?,
    public val reply_markup: TlGen_ReplyMarkup?,
    public val entities: List<TlGen_MessageEntity>?,
    public val views: Int?,
    public val edit_date: Int?,
    public val post_author: String?,
    public val grouped_id: Long?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (fwd_from != null) result = result or 4U
        if (reply_to_msg_id != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (reply_markup != null) result = result or 64U
        if (entities != null) result = result or 128U
        if (from_id != null) result = result or 256U
        if (media != null) result = result or 512U
        if (views != null) result = result or 1024U
        if (via_bot_id != null) result = result or 2048U
        if (silent) result = result or 8192U
        if (post) result = result or 16384U
        if (edit_date != null) result = result or 32768U
        if (post_author != null) result = result or 65536U
        if (grouped_id != null) result = result or 131072U
        if (legacy) result = result or 524288U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      from_id?.let { stream.writeInt32(it) }
      to_id.serializeToStream(stream)
      fwd_from?.serializeToStream(stream)
      via_bot_id?.let { stream.writeInt32(it) }
      reply_to_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(date)
      stream.writeString(message)
      media?.serializeToStream(stream)
      reply_markup?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      views?.let { stream.writeInt32(it) }
      edit_date?.let { stream.writeInt32(it) }
      post_author?.let { stream.writeString(it) }
      grouped_id?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x44F9B43DU
    }
  }

  public data class TL_message_layer118(
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val silent: Boolean,
    public val post: Boolean,
    public val from_scheduled: Boolean,
    public val legacy: Boolean,
    public val edit_hide: Boolean,
    public val id: Int,
    public val from_id: Int?,
    public val to_id: TlGen_Peer,
    public val fwd_from: TlGen_MessageFwdHeader?,
    public val via_bot_id: Int?,
    public val reply_to_msg_id: Int?,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia?,
    public val reply_markup: TlGen_ReplyMarkup?,
    public val entities: List<TlGen_MessageEntity>?,
    public val views: Int?,
    public val edit_date: Int?,
    public val post_author: String?,
    public val grouped_id: Long?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (fwd_from != null) result = result or 4U
        if (reply_to_msg_id != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (reply_markup != null) result = result or 64U
        if (entities != null) result = result or 128U
        if (from_id != null) result = result or 256U
        if (media != null) result = result or 512U
        if (views != null) result = result or 1024U
        if (via_bot_id != null) result = result or 2048U
        if (silent) result = result or 8192U
        if (post) result = result or 16384U
        if (edit_date != null) result = result or 32768U
        if (post_author != null) result = result or 65536U
        if (grouped_id != null) result = result or 131072U
        if (from_scheduled) result = result or 262144U
        if (legacy) result = result or 524288U
        if (edit_hide) result = result or 2097152U
        if (restriction_reason != null) result = result or 4194304U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      from_id?.let { stream.writeInt32(it) }
      to_id.serializeToStream(stream)
      fwd_from?.serializeToStream(stream)
      via_bot_id?.let { stream.writeInt32(it) }
      reply_to_msg_id?.let { stream.writeInt32(it) }
      stream.writeInt32(date)
      stream.writeString(message)
      media?.serializeToStream(stream)
      reply_markup?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      views?.let { stream.writeInt32(it) }
      edit_date?.let { stream.writeInt32(it) }
      post_author?.let { stream.writeString(it) }
      grouped_id?.let { stream.writeInt64(it) }
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x452C0E65U
    }
  }

  public data class TL_message_layer123(
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val silent: Boolean,
    public val post: Boolean,
    public val from_scheduled: Boolean,
    public val legacy: Boolean,
    public val edit_hide: Boolean,
    public val pinned: Boolean,
    public val id: Int,
    public val from_id: TlGen_Peer?,
    public val peer_id: TlGen_Peer,
    public val fwd_from: TlGen_MessageFwdHeader?,
    public val via_bot_id: Int?,
    public val reply_to: TlGen_MessageReplyHeader?,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia?,
    public val reply_markup: TlGen_ReplyMarkup?,
    public val entities: List<TlGen_MessageEntity>?,
    public val replies: TlGen_MessageReplies?,
    public val edit_date: Int?,
    public val post_author: String?,
    public val grouped_id: Long?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val multiflags_10: Multiflags_10?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (fwd_from != null) result = result or 4U
        if (reply_to != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (reply_markup != null) result = result or 64U
        if (entities != null) result = result or 128U
        if (from_id != null) result = result or 256U
        if (media != null) result = result or 512U
        if (multiflags_10 != null) result = result or 1024U
        if (via_bot_id != null) result = result or 2048U
        if (silent) result = result or 8192U
        if (post) result = result or 16384U
        if (edit_date != null) result = result or 32768U
        if (post_author != null) result = result or 65536U
        if (grouped_id != null) result = result or 131072U
        if (from_scheduled) result = result or 262144U
        if (legacy) result = result or 524288U
        if (edit_hide) result = result or 2097152U
        if (restriction_reason != null) result = result or 4194304U
        if (replies != null) result = result or 8388608U
        if (pinned) result = result or 16777216U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      from_id?.serializeToStream(stream)
      peer_id.serializeToStream(stream)
      fwd_from?.serializeToStream(stream)
      via_bot_id?.let { stream.writeInt32(it) }
      reply_to?.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeString(message)
      media?.serializeToStream(stream)
      reply_markup?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      multiflags_10?.let { stream.writeInt32(it.views) }
      multiflags_10?.let { stream.writeInt32(it.forwards) }
      replies?.serializeToStream(stream)
      edit_date?.let { stream.writeInt32(it) }
      post_author?.let { stream.writeString(it) }
      grouped_id?.let { stream.writeInt64(it) }
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
    }

    public data class Multiflags_10(
      public val views: Int,
      public val forwards: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x58AE39C9U
    }
  }

  public data class TL_messageService_layer123(
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val silent: Boolean,
    public val post: Boolean,
    public val legacy: Boolean,
    public val id: Int,
    public val from_id: TlGen_Peer?,
    public val peer_id: TlGen_Peer,
    public val reply_to: TlGen_MessageReplyHeader?,
    public val date: Int,
    public val action: TlGen_MessageAction,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (reply_to != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (from_id != null) result = result or 256U
        if (silent) result = result or 8192U
        if (post) result = result or 16384U
        if (legacy) result = result or 524288U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      from_id?.serializeToStream(stream)
      peer_id.serializeToStream(stream)
      reply_to?.serializeToStream(stream)
      stream.writeInt32(date)
      action.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x286FA604U
    }
  }

  public data class TL_message_layer132(
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val silent: Boolean,
    public val post: Boolean,
    public val from_scheduled: Boolean,
    public val legacy: Boolean,
    public val edit_hide: Boolean,
    public val pinned: Boolean,
    public val id: Int,
    public val from_id: TlGen_Peer?,
    public val peer_id: TlGen_Peer,
    public val fwd_from: TlGen_MessageFwdHeader?,
    public val via_bot_id: Int?,
    public val reply_to: TlGen_MessageReplyHeader?,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia?,
    public val reply_markup: TlGen_ReplyMarkup?,
    public val entities: List<TlGen_MessageEntity>?,
    public val replies: TlGen_MessageReplies?,
    public val edit_date: Int?,
    public val post_author: String?,
    public val grouped_id: Long?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val ttl_period: Int?,
    public val multiflags_10: Multiflags_10?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (fwd_from != null) result = result or 4U
        if (reply_to != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (reply_markup != null) result = result or 64U
        if (entities != null) result = result or 128U
        if (from_id != null) result = result or 256U
        if (media != null) result = result or 512U
        if (multiflags_10 != null) result = result or 1024U
        if (via_bot_id != null) result = result or 2048U
        if (silent) result = result or 8192U
        if (post) result = result or 16384U
        if (edit_date != null) result = result or 32768U
        if (post_author != null) result = result or 65536U
        if (grouped_id != null) result = result or 131072U
        if (from_scheduled) result = result or 262144U
        if (legacy) result = result or 524288U
        if (edit_hide) result = result or 2097152U
        if (restriction_reason != null) result = result or 4194304U
        if (replies != null) result = result or 8388608U
        if (pinned) result = result or 16777216U
        if (ttl_period != null) result = result or 33554432U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      from_id?.serializeToStream(stream)
      peer_id.serializeToStream(stream)
      fwd_from?.serializeToStream(stream)
      via_bot_id?.let { stream.writeInt32(it) }
      reply_to?.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeString(message)
      media?.serializeToStream(stream)
      reply_markup?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      multiflags_10?.let { stream.writeInt32(it.views) }
      multiflags_10?.let { stream.writeInt32(it.forwards) }
      replies?.serializeToStream(stream)
      edit_date?.let { stream.writeInt32(it) }
      post_author?.let { stream.writeString(it) }
      grouped_id?.let { stream.writeInt64(it) }
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      ttl_period?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_10(
      public val views: Int,
      public val forwards: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xBCE383D2U
    }
  }

  public data class TL_messageService_layer195(
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val silent: Boolean,
    public val post: Boolean,
    public val legacy: Boolean,
    public val id: Int,
    public val from_id: TlGen_Peer?,
    public val peer_id: TlGen_Peer,
    public val reply_to: TlGen_MessageReplyHeader?,
    public val date: Int,
    public val action: TlGen_MessageAction,
    public val ttl_period: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (reply_to != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (from_id != null) result = result or 256U
        if (silent) result = result or 8192U
        if (post) result = result or 16384U
        if (legacy) result = result or 524288U
        if (ttl_period != null) result = result or 33554432U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      from_id?.serializeToStream(stream)
      peer_id.serializeToStream(stream)
      reply_to?.serializeToStream(stream)
      stream.writeInt32(date)
      action.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x2B085862U
    }
  }

  public data class TL_message_layer135(
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val silent: Boolean,
    public val post: Boolean,
    public val from_scheduled: Boolean,
    public val legacy: Boolean,
    public val edit_hide: Boolean,
    public val pinned: Boolean,
    public val noforwards: Boolean,
    public val id: Int,
    public val from_id: TlGen_Peer?,
    public val peer_id: TlGen_Peer,
    public val fwd_from: TlGen_MessageFwdHeader?,
    public val via_bot_id: Long?,
    public val reply_to: TlGen_MessageReplyHeader?,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia?,
    public val reply_markup: TlGen_ReplyMarkup?,
    public val entities: List<TlGen_MessageEntity>?,
    public val replies: TlGen_MessageReplies?,
    public val edit_date: Int?,
    public val post_author: String?,
    public val grouped_id: Long?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val ttl_period: Int?,
    public val multiflags_10: Multiflags_10?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (fwd_from != null) result = result or 4U
        if (reply_to != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (reply_markup != null) result = result or 64U
        if (entities != null) result = result or 128U
        if (from_id != null) result = result or 256U
        if (media != null) result = result or 512U
        if (multiflags_10 != null) result = result or 1024U
        if (via_bot_id != null) result = result or 2048U
        if (silent) result = result or 8192U
        if (post) result = result or 16384U
        if (edit_date != null) result = result or 32768U
        if (post_author != null) result = result or 65536U
        if (grouped_id != null) result = result or 131072U
        if (from_scheduled) result = result or 262144U
        if (legacy) result = result or 524288U
        if (edit_hide) result = result or 2097152U
        if (restriction_reason != null) result = result or 4194304U
        if (replies != null) result = result or 8388608U
        if (pinned) result = result or 16777216U
        if (ttl_period != null) result = result or 33554432U
        if (noforwards) result = result or 67108864U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      from_id?.serializeToStream(stream)
      peer_id.serializeToStream(stream)
      fwd_from?.serializeToStream(stream)
      via_bot_id?.let { stream.writeInt64(it) }
      reply_to?.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeString(message)
      media?.serializeToStream(stream)
      reply_markup?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      multiflags_10?.let { stream.writeInt32(it.views) }
      multiflags_10?.let { stream.writeInt32(it.forwards) }
      replies?.serializeToStream(stream)
      edit_date?.let { stream.writeInt32(it) }
      post_author?.let { stream.writeString(it) }
      grouped_id?.let { stream.writeInt64(it) }
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      ttl_period?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_10(
      public val views: Int,
      public val forwards: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x85D6CBE2U
    }
  }

  public data class TL_message_layer169(
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val silent: Boolean,
    public val post: Boolean,
    public val from_scheduled: Boolean,
    public val legacy: Boolean,
    public val edit_hide: Boolean,
    public val pinned: Boolean,
    public val noforwards: Boolean,
    public val invert_media: Boolean,
    public val id: Int,
    public val from_id: TlGen_Peer?,
    public val peer_id: TlGen_Peer,
    public val fwd_from: TlGen_MessageFwdHeader?,
    public val via_bot_id: Long?,
    public val reply_to: TlGen_MessageReplyHeader?,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia?,
    public val reply_markup: TlGen_ReplyMarkup?,
    public val entities: List<TlGen_MessageEntity>?,
    public val replies: TlGen_MessageReplies?,
    public val edit_date: Int?,
    public val post_author: String?,
    public val grouped_id: Long?,
    public val reactions: TlGen_MessageReactions?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val ttl_period: Int?,
    public val multiflags_10: Multiflags_10?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (fwd_from != null) result = result or 4U
        if (reply_to != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (reply_markup != null) result = result or 64U
        if (entities != null) result = result or 128U
        if (from_id != null) result = result or 256U
        if (media != null) result = result or 512U
        if (multiflags_10 != null) result = result or 1024U
        if (via_bot_id != null) result = result or 2048U
        if (silent) result = result or 8192U
        if (post) result = result or 16384U
        if (edit_date != null) result = result or 32768U
        if (post_author != null) result = result or 65536U
        if (grouped_id != null) result = result or 131072U
        if (from_scheduled) result = result or 262144U
        if (legacy) result = result or 524288U
        if (reactions != null) result = result or 1048576U
        if (edit_hide) result = result or 2097152U
        if (restriction_reason != null) result = result or 4194304U
        if (replies != null) result = result or 8388608U
        if (pinned) result = result or 16777216U
        if (ttl_period != null) result = result or 33554432U
        if (noforwards) result = result or 67108864U
        if (invert_media) result = result or 134217728U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      from_id?.serializeToStream(stream)
      peer_id.serializeToStream(stream)
      fwd_from?.serializeToStream(stream)
      via_bot_id?.let { stream.writeInt64(it) }
      reply_to?.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeString(message)
      media?.serializeToStream(stream)
      reply_markup?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      multiflags_10?.let { stream.writeInt32(it.views) }
      multiflags_10?.let { stream.writeInt32(it.forwards) }
      replies?.serializeToStream(stream)
      edit_date?.let { stream.writeInt32(it) }
      post_author?.let { stream.writeString(it) }
      grouped_id?.let { stream.writeInt64(it) }
      reactions?.serializeToStream(stream)
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      ttl_period?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_10(
      public val views: Int,
      public val forwards: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x38116EE0U
    }
  }

  public data class TL_message_layer173(
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val silent: Boolean,
    public val post: Boolean,
    public val from_scheduled: Boolean,
    public val legacy: Boolean,
    public val edit_hide: Boolean,
    public val pinned: Boolean,
    public val noforwards: Boolean,
    public val invert_media: Boolean,
    public val id: Int,
    public val from_id: TlGen_Peer?,
    public val peer_id: TlGen_Peer,
    public val saved_peer_id: TlGen_Peer?,
    public val fwd_from: TlGen_MessageFwdHeader?,
    public val via_bot_id: Long?,
    public val reply_to: TlGen_MessageReplyHeader?,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia?,
    public val reply_markup: TlGen_ReplyMarkup?,
    public val entities: List<TlGen_MessageEntity>?,
    public val replies: TlGen_MessageReplies?,
    public val edit_date: Int?,
    public val post_author: String?,
    public val grouped_id: Long?,
    public val reactions: TlGen_MessageReactions?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val ttl_period: Int?,
    public val multiflags_10: Multiflags_10?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (fwd_from != null) result = result or 4U
        if (reply_to != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (reply_markup != null) result = result or 64U
        if (entities != null) result = result or 128U
        if (from_id != null) result = result or 256U
        if (media != null) result = result or 512U
        if (multiflags_10 != null) result = result or 1024U
        if (via_bot_id != null) result = result or 2048U
        if (silent) result = result or 8192U
        if (post) result = result or 16384U
        if (edit_date != null) result = result or 32768U
        if (post_author != null) result = result or 65536U
        if (grouped_id != null) result = result or 131072U
        if (from_scheduled) result = result or 262144U
        if (legacy) result = result or 524288U
        if (reactions != null) result = result or 1048576U
        if (edit_hide) result = result or 2097152U
        if (restriction_reason != null) result = result or 4194304U
        if (replies != null) result = result or 8388608U
        if (pinned) result = result or 16777216U
        if (ttl_period != null) result = result or 33554432U
        if (noforwards) result = result or 67108864U
        if (invert_media) result = result or 134217728U
        if (saved_peer_id != null) result = result or 268435456U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      from_id?.serializeToStream(stream)
      peer_id.serializeToStream(stream)
      saved_peer_id?.serializeToStream(stream)
      fwd_from?.serializeToStream(stream)
      via_bot_id?.let { stream.writeInt64(it) }
      reply_to?.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeString(message)
      media?.serializeToStream(stream)
      reply_markup?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      multiflags_10?.let { stream.writeInt32(it.views) }
      multiflags_10?.let { stream.writeInt32(it.forwards) }
      replies?.serializeToStream(stream)
      edit_date?.let { stream.writeInt32(it) }
      post_author?.let { stream.writeString(it) }
      grouped_id?.let { stream.writeInt64(it) }
      reactions?.serializeToStream(stream)
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      ttl_period?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_10(
      public val views: Int,
      public val forwards: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x76BEC211U
    }
  }

  public data class TL_message_layer175(
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val silent: Boolean,
    public val post: Boolean,
    public val from_scheduled: Boolean,
    public val legacy: Boolean,
    public val edit_hide: Boolean,
    public val pinned: Boolean,
    public val noforwards: Boolean,
    public val invert_media: Boolean,
    public val id: Int,
    public val from_id: TlGen_Peer?,
    public val from_boosts_applied: Int?,
    public val peer_id: TlGen_Peer,
    public val saved_peer_id: TlGen_Peer?,
    public val fwd_from: TlGen_MessageFwdHeader?,
    public val via_bot_id: Long?,
    public val reply_to: TlGen_MessageReplyHeader?,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia?,
    public val reply_markup: TlGen_ReplyMarkup?,
    public val entities: List<TlGen_MessageEntity>?,
    public val replies: TlGen_MessageReplies?,
    public val edit_date: Int?,
    public val post_author: String?,
    public val grouped_id: Long?,
    public val reactions: TlGen_MessageReactions?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val ttl_period: Int?,
    public val multiflags_10: Multiflags_10?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (fwd_from != null) result = result or 4U
        if (reply_to != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (reply_markup != null) result = result or 64U
        if (entities != null) result = result or 128U
        if (from_id != null) result = result or 256U
        if (media != null) result = result or 512U
        if (multiflags_10 != null) result = result or 1024U
        if (via_bot_id != null) result = result or 2048U
        if (silent) result = result or 8192U
        if (post) result = result or 16384U
        if (edit_date != null) result = result or 32768U
        if (post_author != null) result = result or 65536U
        if (grouped_id != null) result = result or 131072U
        if (from_scheduled) result = result or 262144U
        if (legacy) result = result or 524288U
        if (reactions != null) result = result or 1048576U
        if (edit_hide) result = result or 2097152U
        if (restriction_reason != null) result = result or 4194304U
        if (replies != null) result = result or 8388608U
        if (pinned) result = result or 16777216U
        if (ttl_period != null) result = result or 33554432U
        if (noforwards) result = result or 67108864U
        if (invert_media) result = result or 134217728U
        if (saved_peer_id != null) result = result or 268435456U
        if (from_boosts_applied != null) result = result or 536870912U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      from_id?.serializeToStream(stream)
      from_boosts_applied?.let { stream.writeInt32(it) }
      peer_id.serializeToStream(stream)
      saved_peer_id?.serializeToStream(stream)
      fwd_from?.serializeToStream(stream)
      via_bot_id?.let { stream.writeInt64(it) }
      reply_to?.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeString(message)
      media?.serializeToStream(stream)
      reply_markup?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      multiflags_10?.let { stream.writeInt32(it.views) }
      multiflags_10?.let { stream.writeInt32(it.forwards) }
      replies?.serializeToStream(stream)
      edit_date?.let { stream.writeInt32(it) }
      post_author?.let { stream.writeString(it) }
      grouped_id?.let { stream.writeInt64(it) }
      reactions?.serializeToStream(stream)
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      ttl_period?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_10(
      public val views: Int,
      public val forwards: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x1E4C8A69U
    }
  }

  public data class TL_message_layer176(
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val silent: Boolean,
    public val post: Boolean,
    public val from_scheduled: Boolean,
    public val legacy: Boolean,
    public val edit_hide: Boolean,
    public val pinned: Boolean,
    public val noforwards: Boolean,
    public val invert_media: Boolean,
    public val id: Int,
    public val from_id: TlGen_Peer?,
    public val from_boosts_applied: Int?,
    public val peer_id: TlGen_Peer,
    public val saved_peer_id: TlGen_Peer?,
    public val fwd_from: TlGen_MessageFwdHeader?,
    public val via_bot_id: Long?,
    public val reply_to: TlGen_MessageReplyHeader?,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia?,
    public val reply_markup: TlGen_ReplyMarkup?,
    public val entities: List<TlGen_MessageEntity>?,
    public val replies: TlGen_MessageReplies?,
    public val edit_date: Int?,
    public val post_author: String?,
    public val grouped_id: Long?,
    public val reactions: TlGen_MessageReactions?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val ttl_period: Int?,
    public val quick_reply_shortcut_id: Int?,
    public val multiflags_10: Multiflags_10?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (fwd_from != null) result = result or 4U
        if (reply_to != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (reply_markup != null) result = result or 64U
        if (entities != null) result = result or 128U
        if (from_id != null) result = result or 256U
        if (media != null) result = result or 512U
        if (multiflags_10 != null) result = result or 1024U
        if (via_bot_id != null) result = result or 2048U
        if (silent) result = result or 8192U
        if (post) result = result or 16384U
        if (edit_date != null) result = result or 32768U
        if (post_author != null) result = result or 65536U
        if (grouped_id != null) result = result or 131072U
        if (from_scheduled) result = result or 262144U
        if (legacy) result = result or 524288U
        if (reactions != null) result = result or 1048576U
        if (edit_hide) result = result or 2097152U
        if (restriction_reason != null) result = result or 4194304U
        if (replies != null) result = result or 8388608U
        if (pinned) result = result or 16777216U
        if (ttl_period != null) result = result or 33554432U
        if (noforwards) result = result or 67108864U
        if (invert_media) result = result or 134217728U
        if (saved_peer_id != null) result = result or 268435456U
        if (from_boosts_applied != null) result = result or 536870912U
        if (quick_reply_shortcut_id != null) result = result or 1073741824U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      from_id?.serializeToStream(stream)
      from_boosts_applied?.let { stream.writeInt32(it) }
      peer_id.serializeToStream(stream)
      saved_peer_id?.serializeToStream(stream)
      fwd_from?.serializeToStream(stream)
      via_bot_id?.let { stream.writeInt64(it) }
      reply_to?.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeString(message)
      media?.serializeToStream(stream)
      reply_markup?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      multiflags_10?.let { stream.writeInt32(it.views) }
      multiflags_10?.let { stream.writeInt32(it.forwards) }
      replies?.serializeToStream(stream)
      edit_date?.let { stream.writeInt32(it) }
      post_author?.let { stream.writeString(it) }
      grouped_id?.let { stream.writeInt64(it) }
      reactions?.serializeToStream(stream)
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      ttl_period?.let { stream.writeInt32(it) }
      quick_reply_shortcut_id?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_10(
      public val views: Int,
      public val forwards: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xA66C7EFCU
    }
  }

  public data class TL_message_layer179(
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val silent: Boolean,
    public val post: Boolean,
    public val from_scheduled: Boolean,
    public val legacy: Boolean,
    public val edit_hide: Boolean,
    public val pinned: Boolean,
    public val noforwards: Boolean,
    public val invert_media: Boolean,
    public val offline: Boolean,
    public val id: Int,
    public val from_id: TlGen_Peer?,
    public val from_boosts_applied: Int?,
    public val peer_id: TlGen_Peer,
    public val saved_peer_id: TlGen_Peer?,
    public val fwd_from: TlGen_MessageFwdHeader?,
    public val via_bot_id: Long?,
    public val via_business_bot_id: Long?,
    public val reply_to: TlGen_MessageReplyHeader?,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia?,
    public val reply_markup: TlGen_ReplyMarkup?,
    public val entities: List<TlGen_MessageEntity>?,
    public val replies: TlGen_MessageReplies?,
    public val edit_date: Int?,
    public val post_author: String?,
    public val grouped_id: Long?,
    public val reactions: TlGen_MessageReactions?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val ttl_period: Int?,
    public val quick_reply_shortcut_id: Int?,
    public val multiflags_10: Multiflags_10?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (fwd_from != null) result = result or 4U
        if (reply_to != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (reply_markup != null) result = result or 64U
        if (entities != null) result = result or 128U
        if (from_id != null) result = result or 256U
        if (media != null) result = result or 512U
        if (multiflags_10 != null) result = result or 1024U
        if (via_bot_id != null) result = result or 2048U
        if (silent) result = result or 8192U
        if (post) result = result or 16384U
        if (edit_date != null) result = result or 32768U
        if (post_author != null) result = result or 65536U
        if (grouped_id != null) result = result or 131072U
        if (from_scheduled) result = result or 262144U
        if (legacy) result = result or 524288U
        if (reactions != null) result = result or 1048576U
        if (edit_hide) result = result or 2097152U
        if (restriction_reason != null) result = result or 4194304U
        if (replies != null) result = result or 8388608U
        if (pinned) result = result or 16777216U
        if (ttl_period != null) result = result or 33554432U
        if (noforwards) result = result or 67108864U
        if (invert_media) result = result or 134217728U
        if (saved_peer_id != null) result = result or 268435456U
        if (from_boosts_applied != null) result = result or 536870912U
        if (quick_reply_shortcut_id != null) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (via_business_bot_id != null) result = result or 1U
        if (offline) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt32(id)
      from_id?.serializeToStream(stream)
      from_boosts_applied?.let { stream.writeInt32(it) }
      peer_id.serializeToStream(stream)
      saved_peer_id?.serializeToStream(stream)
      fwd_from?.serializeToStream(stream)
      via_bot_id?.let { stream.writeInt64(it) }
      via_business_bot_id?.let { stream.writeInt64(it) }
      reply_to?.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeString(message)
      media?.serializeToStream(stream)
      reply_markup?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      multiflags_10?.let { stream.writeInt32(it.views) }
      multiflags_10?.let { stream.writeInt32(it.forwards) }
      replies?.serializeToStream(stream)
      edit_date?.let { stream.writeInt32(it) }
      post_author?.let { stream.writeString(it) }
      grouped_id?.let { stream.writeInt64(it) }
      reactions?.serializeToStream(stream)
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      ttl_period?.let { stream.writeInt32(it) }
      quick_reply_shortcut_id?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_10(
      public val views: Int,
      public val forwards: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x2357BF25U
    }
  }

  public data class TL_message_layer180(
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val silent: Boolean,
    public val post: Boolean,
    public val from_scheduled: Boolean,
    public val legacy: Boolean,
    public val edit_hide: Boolean,
    public val pinned: Boolean,
    public val noforwards: Boolean,
    public val invert_media: Boolean,
    public val offline: Boolean,
    public val id: Int,
    public val from_id: TlGen_Peer?,
    public val from_boosts_applied: Int?,
    public val peer_id: TlGen_Peer,
    public val saved_peer_id: TlGen_Peer?,
    public val fwd_from: TlGen_MessageFwdHeader?,
    public val via_bot_id: Long?,
    public val via_business_bot_id: Long?,
    public val reply_to: TlGen_MessageReplyHeader?,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia?,
    public val reply_markup: TlGen_ReplyMarkup?,
    public val entities: List<TlGen_MessageEntity>?,
    public val replies: TlGen_MessageReplies?,
    public val edit_date: Int?,
    public val post_author: String?,
    public val grouped_id: Long?,
    public val reactions: TlGen_MessageReactions?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val ttl_period: Int?,
    public val quick_reply_shortcut_id: Int?,
    public val effect: Long?,
    public val multiflags_10: Multiflags_10?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (fwd_from != null) result = result or 4U
        if (reply_to != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (reply_markup != null) result = result or 64U
        if (entities != null) result = result or 128U
        if (from_id != null) result = result or 256U
        if (media != null) result = result or 512U
        if (multiflags_10 != null) result = result or 1024U
        if (via_bot_id != null) result = result or 2048U
        if (silent) result = result or 8192U
        if (post) result = result or 16384U
        if (edit_date != null) result = result or 32768U
        if (post_author != null) result = result or 65536U
        if (grouped_id != null) result = result or 131072U
        if (from_scheduled) result = result or 262144U
        if (legacy) result = result or 524288U
        if (reactions != null) result = result or 1048576U
        if (edit_hide) result = result or 2097152U
        if (restriction_reason != null) result = result or 4194304U
        if (replies != null) result = result or 8388608U
        if (pinned) result = result or 16777216U
        if (ttl_period != null) result = result or 33554432U
        if (noforwards) result = result or 67108864U
        if (invert_media) result = result or 134217728U
        if (saved_peer_id != null) result = result or 268435456U
        if (from_boosts_applied != null) result = result or 536870912U
        if (quick_reply_shortcut_id != null) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (via_business_bot_id != null) result = result or 1U
        if (offline) result = result or 2U
        if (effect != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt32(id)
      from_id?.serializeToStream(stream)
      from_boosts_applied?.let { stream.writeInt32(it) }
      peer_id.serializeToStream(stream)
      saved_peer_id?.serializeToStream(stream)
      fwd_from?.serializeToStream(stream)
      via_bot_id?.let { stream.writeInt64(it) }
      via_business_bot_id?.let { stream.writeInt64(it) }
      reply_to?.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeString(message)
      media?.serializeToStream(stream)
      reply_markup?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      multiflags_10?.let { stream.writeInt32(it.views) }
      multiflags_10?.let { stream.writeInt32(it.forwards) }
      replies?.serializeToStream(stream)
      edit_date?.let { stream.writeInt32(it) }
      post_author?.let { stream.writeString(it) }
      grouped_id?.let { stream.writeInt64(it) }
      reactions?.serializeToStream(stream)
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      ttl_period?.let { stream.writeInt32(it) }
      quick_reply_shortcut_id?.let { stream.writeInt32(it) }
      effect?.let { stream.writeInt64(it) }
    }

    public data class Multiflags_10(
      public val views: Int,
      public val forwards: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xBDE09C2EU
    }
  }

  public data class TL_message_layer195(
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val silent: Boolean,
    public val post: Boolean,
    public val from_scheduled: Boolean,
    public val legacy: Boolean,
    public val edit_hide: Boolean,
    public val pinned: Boolean,
    public val noforwards: Boolean,
    public val invert_media: Boolean,
    public val offline: Boolean,
    public val video_processing_pending: Boolean,
    public val id: Int,
    public val from_id: TlGen_Peer?,
    public val from_boosts_applied: Int?,
    public val peer_id: TlGen_Peer,
    public val saved_peer_id: TlGen_Peer?,
    public val fwd_from: TlGen_MessageFwdHeader?,
    public val via_bot_id: Long?,
    public val via_business_bot_id: Long?,
    public val reply_to: TlGen_MessageReplyHeader?,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia?,
    public val reply_markup: TlGen_ReplyMarkup?,
    public val entities: List<TlGen_MessageEntity>?,
    public val replies: TlGen_MessageReplies?,
    public val edit_date: Int?,
    public val post_author: String?,
    public val grouped_id: Long?,
    public val reactions: TlGen_MessageReactions?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val ttl_period: Int?,
    public val quick_reply_shortcut_id: Int?,
    public val effect: Long?,
    public val factcheck: TlGen_FactCheck?,
    public val multiflags_10: Multiflags_10?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (fwd_from != null) result = result or 4U
        if (reply_to != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (reply_markup != null) result = result or 64U
        if (entities != null) result = result or 128U
        if (from_id != null) result = result or 256U
        if (media != null) result = result or 512U
        if (multiflags_10 != null) result = result or 1024U
        if (via_bot_id != null) result = result or 2048U
        if (silent) result = result or 8192U
        if (post) result = result or 16384U
        if (edit_date != null) result = result or 32768U
        if (post_author != null) result = result or 65536U
        if (grouped_id != null) result = result or 131072U
        if (from_scheduled) result = result or 262144U
        if (legacy) result = result or 524288U
        if (reactions != null) result = result or 1048576U
        if (edit_hide) result = result or 2097152U
        if (restriction_reason != null) result = result or 4194304U
        if (replies != null) result = result or 8388608U
        if (pinned) result = result or 16777216U
        if (ttl_period != null) result = result or 33554432U
        if (noforwards) result = result or 67108864U
        if (invert_media) result = result or 134217728U
        if (saved_peer_id != null) result = result or 268435456U
        if (from_boosts_applied != null) result = result or 536870912U
        if (quick_reply_shortcut_id != null) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (via_business_bot_id != null) result = result or 1U
        if (offline) result = result or 2U
        if (effect != null) result = result or 4U
        if (factcheck != null) result = result or 8U
        if (video_processing_pending) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt32(id)
      from_id?.serializeToStream(stream)
      from_boosts_applied?.let { stream.writeInt32(it) }
      peer_id.serializeToStream(stream)
      saved_peer_id?.serializeToStream(stream)
      fwd_from?.serializeToStream(stream)
      via_bot_id?.let { stream.writeInt64(it) }
      via_business_bot_id?.let { stream.writeInt64(it) }
      reply_to?.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeString(message)
      media?.serializeToStream(stream)
      reply_markup?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      multiflags_10?.let { stream.writeInt32(it.views) }
      multiflags_10?.let { stream.writeInt32(it.forwards) }
      replies?.serializeToStream(stream)
      edit_date?.let { stream.writeInt32(it) }
      post_author?.let { stream.writeString(it) }
      grouped_id?.let { stream.writeInt64(it) }
      reactions?.serializeToStream(stream)
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      ttl_period?.let { stream.writeInt32(it) }
      quick_reply_shortcut_id?.let { stream.writeInt32(it) }
      effect?.let { stream.writeInt64(it) }
      factcheck?.serializeToStream(stream)
    }

    public data class Multiflags_10(
      public val views: Int,
      public val forwards: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x94345242U
    }
  }

  public data class TL_message_layer199(
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val silent: Boolean,
    public val post: Boolean,
    public val from_scheduled: Boolean,
    public val legacy: Boolean,
    public val edit_hide: Boolean,
    public val pinned: Boolean,
    public val noforwards: Boolean,
    public val invert_media: Boolean,
    public val offline: Boolean,
    public val video_processing_pending: Boolean,
    public val id: Int,
    public val from_id: TlGen_Peer?,
    public val from_boosts_applied: Int?,
    public val peer_id: TlGen_Peer,
    public val saved_peer_id: TlGen_Peer?,
    public val fwd_from: TlGen_MessageFwdHeader?,
    public val via_bot_id: Long?,
    public val via_business_bot_id: Long?,
    public val reply_to: TlGen_MessageReplyHeader?,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia?,
    public val reply_markup: TlGen_ReplyMarkup?,
    public val entities: List<TlGen_MessageEntity>?,
    public val replies: TlGen_MessageReplies?,
    public val edit_date: Int?,
    public val post_author: String?,
    public val grouped_id: Long?,
    public val reactions: TlGen_MessageReactions?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val ttl_period: Int?,
    public val quick_reply_shortcut_id: Int?,
    public val effect: Long?,
    public val factcheck: TlGen_FactCheck?,
    public val report_delivery_until_date: Int?,
    public val multiflags_10: Multiflags_10?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (fwd_from != null) result = result or 4U
        if (reply_to != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (reply_markup != null) result = result or 64U
        if (entities != null) result = result or 128U
        if (from_id != null) result = result or 256U
        if (media != null) result = result or 512U
        if (multiflags_10 != null) result = result or 1024U
        if (via_bot_id != null) result = result or 2048U
        if (silent) result = result or 8192U
        if (post) result = result or 16384U
        if (edit_date != null) result = result or 32768U
        if (post_author != null) result = result or 65536U
        if (grouped_id != null) result = result or 131072U
        if (from_scheduled) result = result or 262144U
        if (legacy) result = result or 524288U
        if (reactions != null) result = result or 1048576U
        if (edit_hide) result = result or 2097152U
        if (restriction_reason != null) result = result or 4194304U
        if (replies != null) result = result or 8388608U
        if (pinned) result = result or 16777216U
        if (ttl_period != null) result = result or 33554432U
        if (noforwards) result = result or 67108864U
        if (invert_media) result = result or 134217728U
        if (saved_peer_id != null) result = result or 268435456U
        if (from_boosts_applied != null) result = result or 536870912U
        if (quick_reply_shortcut_id != null) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (via_business_bot_id != null) result = result or 1U
        if (offline) result = result or 2U
        if (effect != null) result = result or 4U
        if (factcheck != null) result = result or 8U
        if (video_processing_pending) result = result or 16U
        if (report_delivery_until_date != null) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt32(id)
      from_id?.serializeToStream(stream)
      from_boosts_applied?.let { stream.writeInt32(it) }
      peer_id.serializeToStream(stream)
      saved_peer_id?.serializeToStream(stream)
      fwd_from?.serializeToStream(stream)
      via_bot_id?.let { stream.writeInt64(it) }
      via_business_bot_id?.let { stream.writeInt64(it) }
      reply_to?.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeString(message)
      media?.serializeToStream(stream)
      reply_markup?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      multiflags_10?.let { stream.writeInt32(it.views) }
      multiflags_10?.let { stream.writeInt32(it.forwards) }
      replies?.serializeToStream(stream)
      edit_date?.let { stream.writeInt32(it) }
      post_author?.let { stream.writeString(it) }
      grouped_id?.let { stream.writeInt64(it) }
      reactions?.serializeToStream(stream)
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      ttl_period?.let { stream.writeInt32(it) }
      quick_reply_shortcut_id?.let { stream.writeInt32(it) }
      effect?.let { stream.writeInt64(it) }
      factcheck?.serializeToStream(stream)
      report_delivery_until_date?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_10(
      public val views: Int,
      public val forwards: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x96FDBBE9U
    }
  }

  public data class TL_messageService_layer204(
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val reactions_are_possible: Boolean,
    public val silent: Boolean,
    public val post: Boolean,
    public val legacy: Boolean,
    public val id: Int,
    public val from_id: TlGen_Peer?,
    public val peer_id: TlGen_Peer,
    public val reply_to: TlGen_MessageReplyHeader?,
    public val date: Int,
    public val action: TlGen_MessageAction,
    public val reactions: TlGen_MessageReactions?,
    public val ttl_period: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (reply_to != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (from_id != null) result = result or 256U
        if (reactions_are_possible) result = result or 512U
        if (silent) result = result or 8192U
        if (post) result = result or 16384U
        if (legacy) result = result or 524288U
        if (reactions != null) result = result or 1048576U
        if (ttl_period != null) result = result or 33554432U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      from_id?.serializeToStream(stream)
      peer_id.serializeToStream(stream)
      reply_to?.serializeToStream(stream)
      stream.writeInt32(date)
      action.serializeToStream(stream)
      reactions?.serializeToStream(stream)
      ttl_period?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xD3D28540U
    }
  }

  public data class TL_message_layer205(
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val silent: Boolean,
    public val post: Boolean,
    public val from_scheduled: Boolean,
    public val legacy: Boolean,
    public val edit_hide: Boolean,
    public val pinned: Boolean,
    public val noforwards: Boolean,
    public val invert_media: Boolean,
    public val offline: Boolean,
    public val video_processing_pending: Boolean,
    public val id: Int,
    public val from_id: TlGen_Peer?,
    public val from_boosts_applied: Int?,
    public val peer_id: TlGen_Peer,
    public val saved_peer_id: TlGen_Peer?,
    public val fwd_from: TlGen_MessageFwdHeader?,
    public val via_bot_id: Long?,
    public val via_business_bot_id: Long?,
    public val reply_to: TlGen_MessageReplyHeader?,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia?,
    public val reply_markup: TlGen_ReplyMarkup?,
    public val entities: List<TlGen_MessageEntity>?,
    public val replies: TlGen_MessageReplies?,
    public val edit_date: Int?,
    public val post_author: String?,
    public val grouped_id: Long?,
    public val reactions: TlGen_MessageReactions?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val ttl_period: Int?,
    public val quick_reply_shortcut_id: Int?,
    public val effect: Long?,
    public val factcheck: TlGen_FactCheck?,
    public val report_delivery_until_date: Int?,
    public val paid_message_stars: Long?,
    public val multiflags_10: Multiflags_10?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (fwd_from != null) result = result or 4U
        if (reply_to != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (reply_markup != null) result = result or 64U
        if (entities != null) result = result or 128U
        if (from_id != null) result = result or 256U
        if (media != null) result = result or 512U
        if (multiflags_10 != null) result = result or 1024U
        if (via_bot_id != null) result = result or 2048U
        if (silent) result = result or 8192U
        if (post) result = result or 16384U
        if (edit_date != null) result = result or 32768U
        if (post_author != null) result = result or 65536U
        if (grouped_id != null) result = result or 131072U
        if (from_scheduled) result = result or 262144U
        if (legacy) result = result or 524288U
        if (reactions != null) result = result or 1048576U
        if (edit_hide) result = result or 2097152U
        if (restriction_reason != null) result = result or 4194304U
        if (replies != null) result = result or 8388608U
        if (pinned) result = result or 16777216U
        if (ttl_period != null) result = result or 33554432U
        if (noforwards) result = result or 67108864U
        if (invert_media) result = result or 134217728U
        if (saved_peer_id != null) result = result or 268435456U
        if (from_boosts_applied != null) result = result or 536870912U
        if (quick_reply_shortcut_id != null) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (via_business_bot_id != null) result = result or 1U
        if (offline) result = result or 2U
        if (effect != null) result = result or 4U
        if (factcheck != null) result = result or 8U
        if (video_processing_pending) result = result or 16U
        if (report_delivery_until_date != null) result = result or 32U
        if (paid_message_stars != null) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt32(id)
      from_id?.serializeToStream(stream)
      from_boosts_applied?.let { stream.writeInt32(it) }
      peer_id.serializeToStream(stream)
      saved_peer_id?.serializeToStream(stream)
      fwd_from?.serializeToStream(stream)
      via_bot_id?.let { stream.writeInt64(it) }
      via_business_bot_id?.let { stream.writeInt64(it) }
      reply_to?.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeString(message)
      media?.serializeToStream(stream)
      reply_markup?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      multiflags_10?.let { stream.writeInt32(it.views) }
      multiflags_10?.let { stream.writeInt32(it.forwards) }
      replies?.serializeToStream(stream)
      edit_date?.let { stream.writeInt32(it) }
      post_author?.let { stream.writeString(it) }
      grouped_id?.let { stream.writeInt64(it) }
      reactions?.serializeToStream(stream)
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      ttl_period?.let { stream.writeInt32(it) }
      quick_reply_shortcut_id?.let { stream.writeInt32(it) }
      effect?.let { stream.writeInt64(it) }
      factcheck?.serializeToStream(stream)
      report_delivery_until_date?.let { stream.writeInt32(it) }
      paid_message_stars?.let { stream.writeInt64(it) }
    }

    public data class Multiflags_10(
      public val views: Int,
      public val forwards: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0xEABCDD4DU
    }
  }

  public data class TL_message_layer216(
    public val `out`: Boolean,
    public val mentioned: Boolean,
    public val media_unread: Boolean,
    public val silent: Boolean,
    public val post: Boolean,
    public val from_scheduled: Boolean,
    public val legacy: Boolean,
    public val edit_hide: Boolean,
    public val pinned: Boolean,
    public val noforwards: Boolean,
    public val invert_media: Boolean,
    public val offline: Boolean,
    public val video_processing_pending: Boolean,
    public val paid_suggested_post_stars: Boolean,
    public val paid_suggested_post_ton: Boolean,
    public val id: Int,
    public val from_id: TlGen_Peer?,
    public val from_boosts_applied: Int?,
    public val peer_id: TlGen_Peer,
    public val saved_peer_id: TlGen_Peer?,
    public val fwd_from: TlGen_MessageFwdHeader?,
    public val via_bot_id: Long?,
    public val via_business_bot_id: Long?,
    public val reply_to: TlGen_MessageReplyHeader?,
    public val date: Int,
    public val message: String,
    public val media: TlGen_MessageMedia?,
    public val reply_markup: TlGen_ReplyMarkup?,
    public val entities: List<TlGen_MessageEntity>?,
    public val replies: TlGen_MessageReplies?,
    public val edit_date: Int?,
    public val post_author: String?,
    public val grouped_id: Long?,
    public val reactions: TlGen_MessageReactions?,
    public val restriction_reason: List<TlGen_RestrictionReason>?,
    public val ttl_period: Int?,
    public val quick_reply_shortcut_id: Int?,
    public val effect: Long?,
    public val factcheck: TlGen_FactCheck?,
    public val report_delivery_until_date: Int?,
    public val paid_message_stars: Long?,
    public val suggested_post: TlGen_SuggestedPost?,
    public val multiflags_10: Multiflags_10?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (out) result = result or 2U
        if (fwd_from != null) result = result or 4U
        if (reply_to != null) result = result or 8U
        if (mentioned) result = result or 16U
        if (media_unread) result = result or 32U
        if (reply_markup != null) result = result or 64U
        if (entities != null) result = result or 128U
        if (from_id != null) result = result or 256U
        if (media != null) result = result or 512U
        if (multiflags_10 != null) result = result or 1024U
        if (via_bot_id != null) result = result or 2048U
        if (silent) result = result or 8192U
        if (post) result = result or 16384U
        if (edit_date != null) result = result or 32768U
        if (post_author != null) result = result or 65536U
        if (grouped_id != null) result = result or 131072U
        if (from_scheduled) result = result or 262144U
        if (legacy) result = result or 524288U
        if (reactions != null) result = result or 1048576U
        if (edit_hide) result = result or 2097152U
        if (restriction_reason != null) result = result or 4194304U
        if (replies != null) result = result or 8388608U
        if (pinned) result = result or 16777216U
        if (ttl_period != null) result = result or 33554432U
        if (noforwards) result = result or 67108864U
        if (invert_media) result = result or 134217728U
        if (saved_peer_id != null) result = result or 268435456U
        if (from_boosts_applied != null) result = result or 536870912U
        if (quick_reply_shortcut_id != null) result = result or 1073741824U
        return result
      }

    internal val flags2: UInt
      get() {
        var result = 0U
        if (via_business_bot_id != null) result = result or 1U
        if (offline) result = result or 2U
        if (effect != null) result = result or 4U
        if (factcheck != null) result = result or 8U
        if (video_processing_pending) result = result or 16U
        if (report_delivery_until_date != null) result = result or 32U
        if (paid_message_stars != null) result = result or 64U
        if (suggested_post != null) result = result or 128U
        if (paid_suggested_post_stars) result = result or 256U
        if (paid_suggested_post_ton) result = result or 512U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(flags2.toInt())
      stream.writeInt32(id)
      from_id?.serializeToStream(stream)
      from_boosts_applied?.let { stream.writeInt32(it) }
      peer_id.serializeToStream(stream)
      saved_peer_id?.serializeToStream(stream)
      fwd_from?.serializeToStream(stream)
      via_bot_id?.let { stream.writeInt64(it) }
      via_business_bot_id?.let { stream.writeInt64(it) }
      reply_to?.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeString(message)
      media?.serializeToStream(stream)
      reply_markup?.serializeToStream(stream)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      multiflags_10?.let { stream.writeInt32(it.views) }
      multiflags_10?.let { stream.writeInt32(it.forwards) }
      replies?.serializeToStream(stream)
      edit_date?.let { stream.writeInt32(it) }
      post_author?.let { stream.writeString(it) }
      grouped_id?.let { stream.writeInt64(it) }
      reactions?.serializeToStream(stream)
      restriction_reason?.let { TlGen_Vector.serialize(stream, it) }
      ttl_period?.let { stream.writeInt32(it) }
      quick_reply_shortcut_id?.let { stream.writeInt32(it) }
      effect?.let { stream.writeInt64(it) }
      factcheck?.serializeToStream(stream)
      report_delivery_until_date?.let { stream.writeInt32(it) }
      paid_message_stars?.let { stream.writeInt64(it) }
      suggested_post?.serializeToStream(stream)
    }

    public data class Multiflags_10(
      public val views: Int,
      public val forwards: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x9815CEC8U
    }
  }
}

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

public sealed class TlGen_StarsTransaction : TlGen_Object {
  public data class TL_starsTransaction(
    public val refund: Boolean,
    public val pending: Boolean,
    public val failed: Boolean,
    public val gift: Boolean,
    public val reaction: Boolean,
    public val stargift_upgrade: Boolean,
    public val business_transfer: Boolean,
    public val stargift_resale: Boolean,
    public val posts_search: Boolean,
    public val stargift_prepaid_upgrade: Boolean,
    public val stargift_drop_original_details: Boolean,
    public val phonegroup_message: Boolean,
    public val stargift_auction_bid: Boolean,
    public val id: String,
    public val amount: TlGen_StarsAmount,
    public val date: Int,
    public val peer: TlGen_StarsTransactionPeer,
    public val title: String?,
    public val description: String?,
    public val photo: TlGen_WebDocument?,
    public val bot_payload: List<Byte>?,
    public val msg_id: Int?,
    public val extended_media: List<TlGen_MessageMedia>?,
    public val subscription_period: Int?,
    public val giveaway_post_id: Int?,
    public val stargift: TlGen_StarGift?,
    public val floodskip_number: Int?,
    public val starref_commission_permille: Int?,
    public val paid_messages: Int?,
    public val premium_gift_months: Int?,
    public val multiflags_5: Multiflags_5?,
    public val multiflags_17: Multiflags_17?,
    public val multiflags_23: Multiflags_23?,
  ) : TlGen_StarsTransaction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (title != null) result = result or 1U
        if (description != null) result = result or 2U
        if (photo != null) result = result or 4U
        if (refund) result = result or 8U
        if (pending) result = result or 16U
        if (multiflags_5 != null) result = result or 32U
        if (failed) result = result or 64U
        if (bot_payload != null) result = result or 128U
        if (msg_id != null) result = result or 256U
        if (extended_media != null) result = result or 512U
        if (gift) result = result or 1024U
        if (reaction) result = result or 2048U
        if (subscription_period != null) result = result or 4096U
        if (giveaway_post_id != null) result = result or 8192U
        if (stargift != null) result = result or 16384U
        if (floodskip_number != null) result = result or 32768U
        if (starref_commission_permille != null) result = result or 65536U
        if (multiflags_17 != null) result = result or 131072U
        if (stargift_upgrade) result = result or 262144U
        if (paid_messages != null) result = result or 524288U
        if (premium_gift_months != null) result = result or 1048576U
        if (business_transfer) result = result or 2097152U
        if (stargift_resale) result = result or 4194304U
        if (multiflags_23 != null) result = result or 8388608U
        if (posts_search) result = result or 16777216U
        if (stargift_prepaid_upgrade) result = result or 33554432U
        if (stargift_drop_original_details) result = result or 67108864U
        if (phonegroup_message) result = result or 134217728U
        if (stargift_auction_bid) result = result or 268435456U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(id)
      amount.serializeToStream(stream)
      stream.writeInt32(date)
      peer.serializeToStream(stream)
      title?.let { stream.writeString(it) }
      description?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      multiflags_5?.let { stream.writeInt32(it.transaction_date) }
      multiflags_5?.let { stream.writeString(it.transaction_url) }
      bot_payload?.let { stream.writeByteArray(it.toByteArray()) }
      msg_id?.let { stream.writeInt32(it) }
      extended_media?.let { TlGen_Vector.serialize(stream, it) }
      subscription_period?.let { stream.writeInt32(it) }
      giveaway_post_id?.let { stream.writeInt32(it) }
      stargift?.serializeToStream(stream)
      floodskip_number?.let { stream.writeInt32(it) }
      starref_commission_permille?.let { stream.writeInt32(it) }
      multiflags_17?.let { it.starref_peer.serializeToStream(stream) }
      multiflags_17?.let { it.starref_amount.serializeToStream(stream) }
      paid_messages?.let { stream.writeInt32(it) }
      premium_gift_months?.let { stream.writeInt32(it) }
      multiflags_23?.let { stream.writeInt32(it.ads_proceeds_from_date) }
      multiflags_23?.let { stream.writeInt32(it.ads_proceeds_to_date) }
    }

    public data class Multiflags_5(
      public val transaction_date: Int,
      public val transaction_url: String,
    )

    public data class Multiflags_17(
      public val starref_peer: TlGen_Peer,
      public val starref_amount: TlGen_StarsAmount,
    )

    public data class Multiflags_23(
      public val ads_proceeds_from_date: Int,
      public val ads_proceeds_to_date: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x13659EB0U
    }
  }

  public data class TL_starsTransaction_layer181(
    public val refund: Boolean,
    public val id: String,
    public val stars: Long,
    public val date: Int,
    public val peer: TlGen_StarsTransactionPeer,
    public val title: String?,
    public val description: String?,
    public val photo: TlGen_WebDocument?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (title != null) result = result or 1U
        if (description != null) result = result or 2U
        if (photo != null) result = result or 4U
        if (refund) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(id)
      stream.writeInt64(stars)
      stream.writeInt32(date)
      peer.serializeToStream(stream)
      title?.let { stream.writeString(it) }
      description?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCC7079B2U
    }
  }

  public data class TL_starsTransaction_layer182(
    public val refund: Boolean,
    public val pending: Boolean,
    public val failed: Boolean,
    public val id: String,
    public val stars: Long,
    public val date: Int,
    public val peer: TlGen_StarsTransactionPeer,
    public val title: String?,
    public val description: String?,
    public val photo: TlGen_WebDocument?,
    public val multiflags_5: Multiflags_5?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (title != null) result = result or 1U
        if (description != null) result = result or 2U
        if (photo != null) result = result or 4U
        if (refund) result = result or 8U
        if (pending) result = result or 16U
        if (multiflags_5 != null) result = result or 32U
        if (failed) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(id)
      stream.writeInt64(stars)
      stream.writeInt32(date)
      peer.serializeToStream(stream)
      title?.let { stream.writeString(it) }
      description?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      multiflags_5?.let { stream.writeInt32(it.transaction_date) }
      multiflags_5?.let { stream.writeString(it.transaction_url) }
    }

    public data class Multiflags_5(
      public val transaction_date: Int,
      public val transaction_url: String,
    )

    public companion object {
      public const val MAGIC: UInt = 0xAA00C898U
    }
  }

  public data class TL_starsTransaction_layer185(
    public val refund: Boolean,
    public val pending: Boolean,
    public val failed: Boolean,
    public val gift: Boolean,
    public val id: String,
    public val stars: Long,
    public val date: Int,
    public val peer: TlGen_StarsTransactionPeer,
    public val title: String?,
    public val description: String?,
    public val photo: TlGen_WebDocument?,
    public val bot_payload: List<Byte>?,
    public val msg_id: Int?,
    public val extended_media: List<TlGen_MessageMedia>?,
    public val multiflags_5: Multiflags_5?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (title != null) result = result or 1U
        if (description != null) result = result or 2U
        if (photo != null) result = result or 4U
        if (refund) result = result or 8U
        if (pending) result = result or 16U
        if (multiflags_5 != null) result = result or 32U
        if (failed) result = result or 64U
        if (bot_payload != null) result = result or 128U
        if (msg_id != null) result = result or 256U
        if (extended_media != null) result = result or 512U
        if (gift) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(id)
      stream.writeInt64(stars)
      stream.writeInt32(date)
      peer.serializeToStream(stream)
      title?.let { stream.writeString(it) }
      description?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      multiflags_5?.let { stream.writeInt32(it.transaction_date) }
      multiflags_5?.let { stream.writeString(it.transaction_url) }
      bot_payload?.let { stream.writeByteArray(it.toByteArray()) }
      msg_id?.let { stream.writeInt32(it) }
      extended_media?.let { TlGen_Vector.serialize(stream, it) }
    }

    public data class Multiflags_5(
      public val transaction_date: Int,
      public val transaction_url: String,
    )

    public companion object {
      public const val MAGIC: UInt = 0x2DB5418FU
    }
  }

  public data class TL_starsTransaction_layer186(
    public val refund: Boolean,
    public val pending: Boolean,
    public val failed: Boolean,
    public val gift: Boolean,
    public val reaction: Boolean,
    public val id: String,
    public val stars: Long,
    public val date: Int,
    public val peer: TlGen_StarsTransactionPeer,
    public val title: String?,
    public val description: String?,
    public val photo: TlGen_WebDocument?,
    public val bot_payload: List<Byte>?,
    public val msg_id: Int?,
    public val extended_media: List<TlGen_MessageMedia>?,
    public val subscription_period: Int?,
    public val multiflags_5: Multiflags_5?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (title != null) result = result or 1U
        if (description != null) result = result or 2U
        if (photo != null) result = result or 4U
        if (refund) result = result or 8U
        if (pending) result = result or 16U
        if (multiflags_5 != null) result = result or 32U
        if (failed) result = result or 64U
        if (bot_payload != null) result = result or 128U
        if (msg_id != null) result = result or 256U
        if (extended_media != null) result = result or 512U
        if (gift) result = result or 1024U
        if (reaction) result = result or 2048U
        if (subscription_period != null) result = result or 4096U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(id)
      stream.writeInt64(stars)
      stream.writeInt32(date)
      peer.serializeToStream(stream)
      title?.let { stream.writeString(it) }
      description?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      multiflags_5?.let { stream.writeInt32(it.transaction_date) }
      multiflags_5?.let { stream.writeString(it.transaction_url) }
      bot_payload?.let { stream.writeByteArray(it.toByteArray()) }
      msg_id?.let { stream.writeInt32(it) }
      extended_media?.let { TlGen_Vector.serialize(stream, it) }
      subscription_period?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_5(
      public val transaction_date: Int,
      public val transaction_url: String,
    )

    public companion object {
      public const val MAGIC: UInt = 0x433AEB2BU
    }
  }

  public data class TL_starsTransaction_layer188(
    public val refund: Boolean,
    public val pending: Boolean,
    public val failed: Boolean,
    public val gift: Boolean,
    public val reaction: Boolean,
    public val id: String,
    public val stars: Long,
    public val date: Int,
    public val peer: TlGen_StarsTransactionPeer,
    public val title: String?,
    public val description: String?,
    public val photo: TlGen_WebDocument?,
    public val bot_payload: List<Byte>?,
    public val msg_id: Int?,
    public val extended_media: List<TlGen_MessageMedia>?,
    public val subscription_period: Int?,
    public val giveaway_post_id: Int?,
    public val multiflags_5: Multiflags_5?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (title != null) result = result or 1U
        if (description != null) result = result or 2U
        if (photo != null) result = result or 4U
        if (refund) result = result or 8U
        if (pending) result = result or 16U
        if (multiflags_5 != null) result = result or 32U
        if (failed) result = result or 64U
        if (bot_payload != null) result = result or 128U
        if (msg_id != null) result = result or 256U
        if (extended_media != null) result = result or 512U
        if (gift) result = result or 1024U
        if (reaction) result = result or 2048U
        if (subscription_period != null) result = result or 4096U
        if (giveaway_post_id != null) result = result or 8192U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(id)
      stream.writeInt64(stars)
      stream.writeInt32(date)
      peer.serializeToStream(stream)
      title?.let { stream.writeString(it) }
      description?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      multiflags_5?.let { stream.writeInt32(it.transaction_date) }
      multiflags_5?.let { stream.writeString(it.transaction_url) }
      bot_payload?.let { stream.writeByteArray(it.toByteArray()) }
      msg_id?.let { stream.writeInt32(it) }
      extended_media?.let { TlGen_Vector.serialize(stream, it) }
      subscription_period?.let { stream.writeInt32(it) }
      giveaway_post_id?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_5(
      public val transaction_date: Int,
      public val transaction_url: String,
    )

    public companion object {
      public const val MAGIC: UInt = 0xEE7522D5U
    }
  }

  public data class TL_starsTransaction_layer191(
    public val refund: Boolean,
    public val pending: Boolean,
    public val failed: Boolean,
    public val gift: Boolean,
    public val reaction: Boolean,
    public val id: String,
    public val stars: Long,
    public val date: Int,
    public val peer: TlGen_StarsTransactionPeer,
    public val title: String?,
    public val description: String?,
    public val photo: TlGen_WebDocument?,
    public val bot_payload: List<Byte>?,
    public val msg_id: Int?,
    public val extended_media: List<TlGen_MessageMedia>?,
    public val subscription_period: Int?,
    public val giveaway_post_id: Int?,
    public val stargift: TlGen_StarGift?,
    public val multiflags_5: Multiflags_5?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (title != null) result = result or 1U
        if (description != null) result = result or 2U
        if (photo != null) result = result or 4U
        if (refund) result = result or 8U
        if (pending) result = result or 16U
        if (multiflags_5 != null) result = result or 32U
        if (failed) result = result or 64U
        if (bot_payload != null) result = result or 128U
        if (msg_id != null) result = result or 256U
        if (extended_media != null) result = result or 512U
        if (gift) result = result or 1024U
        if (reaction) result = result or 2048U
        if (subscription_period != null) result = result or 4096U
        if (giveaway_post_id != null) result = result or 8192U
        if (stargift != null) result = result or 16384U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(id)
      stream.writeInt64(stars)
      stream.writeInt32(date)
      peer.serializeToStream(stream)
      title?.let { stream.writeString(it) }
      description?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      multiflags_5?.let { stream.writeInt32(it.transaction_date) }
      multiflags_5?.let { stream.writeString(it.transaction_url) }
      bot_payload?.let { stream.writeByteArray(it.toByteArray()) }
      msg_id?.let { stream.writeInt32(it) }
      extended_media?.let { TlGen_Vector.serialize(stream, it) }
      subscription_period?.let { stream.writeInt32(it) }
      giveaway_post_id?.let { stream.writeInt32(it) }
      stargift?.serializeToStream(stream)
    }

    public data class Multiflags_5(
      public val transaction_date: Int,
      public val transaction_url: String,
    )

    public companion object {
      public const val MAGIC: UInt = 0x0A9EE4C2U
    }
  }

  public data class TL_starsTransaction_layer194(
    public val refund: Boolean,
    public val pending: Boolean,
    public val failed: Boolean,
    public val gift: Boolean,
    public val reaction: Boolean,
    public val id: String,
    public val stars: Long,
    public val date: Int,
    public val peer: TlGen_StarsTransactionPeer,
    public val title: String?,
    public val description: String?,
    public val photo: TlGen_WebDocument?,
    public val bot_payload: List<Byte>?,
    public val msg_id: Int?,
    public val extended_media: List<TlGen_MessageMedia>?,
    public val subscription_period: Int?,
    public val giveaway_post_id: Int?,
    public val stargift: TlGen_StarGift?,
    public val floodskip_number: Int?,
    public val multiflags_5: Multiflags_5?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (title != null) result = result or 1U
        if (description != null) result = result or 2U
        if (photo != null) result = result or 4U
        if (refund) result = result or 8U
        if (pending) result = result or 16U
        if (multiflags_5 != null) result = result or 32U
        if (failed) result = result or 64U
        if (bot_payload != null) result = result or 128U
        if (msg_id != null) result = result or 256U
        if (extended_media != null) result = result or 512U
        if (gift) result = result or 1024U
        if (reaction) result = result or 2048U
        if (subscription_period != null) result = result or 4096U
        if (giveaway_post_id != null) result = result or 8192U
        if (stargift != null) result = result or 16384U
        if (floodskip_number != null) result = result or 32768U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(id)
      stream.writeInt64(stars)
      stream.writeInt32(date)
      peer.serializeToStream(stream)
      title?.let { stream.writeString(it) }
      description?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      multiflags_5?.let { stream.writeInt32(it.transaction_date) }
      multiflags_5?.let { stream.writeString(it.transaction_url) }
      bot_payload?.let { stream.writeByteArray(it.toByteArray()) }
      msg_id?.let { stream.writeInt32(it) }
      extended_media?.let { TlGen_Vector.serialize(stream, it) }
      subscription_period?.let { stream.writeInt32(it) }
      giveaway_post_id?.let { stream.writeInt32(it) }
      stargift?.serializeToStream(stream)
      floodskip_number?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_5(
      public val transaction_date: Int,
      public val transaction_url: String,
    )

    public companion object {
      public const val MAGIC: UInt = 0x35D4F276U
    }
  }

  public data class TL_starsTransaction_layer199(
    public val refund: Boolean,
    public val pending: Boolean,
    public val failed: Boolean,
    public val gift: Boolean,
    public val reaction: Boolean,
    public val stargift_upgrade: Boolean,
    public val id: String,
    public val stars: TlGen_StarsAmount,
    public val date: Int,
    public val peer: TlGen_StarsTransactionPeer,
    public val title: String?,
    public val description: String?,
    public val photo: TlGen_WebDocument?,
    public val bot_payload: List<Byte>?,
    public val msg_id: Int?,
    public val extended_media: List<TlGen_MessageMedia>?,
    public val subscription_period: Int?,
    public val giveaway_post_id: Int?,
    public val stargift: TlGen_StarGift?,
    public val floodskip_number: Int?,
    public val starref_commission_permille: Int?,
    public val multiflags_5: Multiflags_5?,
    public val multiflags_17: Multiflags_17?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (title != null) result = result or 1U
        if (description != null) result = result or 2U
        if (photo != null) result = result or 4U
        if (refund) result = result or 8U
        if (pending) result = result or 16U
        if (multiflags_5 != null) result = result or 32U
        if (failed) result = result or 64U
        if (bot_payload != null) result = result or 128U
        if (msg_id != null) result = result or 256U
        if (extended_media != null) result = result or 512U
        if (gift) result = result or 1024U
        if (reaction) result = result or 2048U
        if (subscription_period != null) result = result or 4096U
        if (giveaway_post_id != null) result = result or 8192U
        if (stargift != null) result = result or 16384U
        if (floodskip_number != null) result = result or 32768U
        if (starref_commission_permille != null) result = result or 65536U
        if (multiflags_17 != null) result = result or 131072U
        if (stargift_upgrade) result = result or 262144U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(id)
      stars.serializeToStream(stream)
      stream.writeInt32(date)
      peer.serializeToStream(stream)
      title?.let { stream.writeString(it) }
      description?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      multiflags_5?.let { stream.writeInt32(it.transaction_date) }
      multiflags_5?.let { stream.writeString(it.transaction_url) }
      bot_payload?.let { stream.writeByteArray(it.toByteArray()) }
      msg_id?.let { stream.writeInt32(it) }
      extended_media?.let { TlGen_Vector.serialize(stream, it) }
      subscription_period?.let { stream.writeInt32(it) }
      giveaway_post_id?.let { stream.writeInt32(it) }
      stargift?.serializeToStream(stream)
      floodskip_number?.let { stream.writeInt32(it) }
      starref_commission_permille?.let { stream.writeInt32(it) }
      multiflags_17?.let { it.starref_peer.serializeToStream(stream) }
      multiflags_17?.let { it.starref_amount.serializeToStream(stream) }
    }

    public data class Multiflags_5(
      public val transaction_date: Int,
      public val transaction_url: String,
    )

    public data class Multiflags_17(
      public val starref_peer: TlGen_Peer,
      public val starref_amount: TlGen_StarsAmount,
    )

    public companion object {
      public const val MAGIC: UInt = 0x64DFC926U
    }
  }

  public data class TL_starsTransaction_layer205(
    public val refund: Boolean,
    public val pending: Boolean,
    public val failed: Boolean,
    public val gift: Boolean,
    public val reaction: Boolean,
    public val stargift_upgrade: Boolean,
    public val business_transfer: Boolean,
    public val stargift_resale: Boolean,
    public val id: String,
    public val stars: TlGen_StarsAmount,
    public val date: Int,
    public val peer: TlGen_StarsTransactionPeer,
    public val title: String?,
    public val description: String?,
    public val photo: TlGen_WebDocument?,
    public val bot_payload: List<Byte>?,
    public val msg_id: Int?,
    public val extended_media: List<TlGen_MessageMedia>?,
    public val subscription_period: Int?,
    public val giveaway_post_id: Int?,
    public val stargift: TlGen_StarGift?,
    public val floodskip_number: Int?,
    public val starref_commission_permille: Int?,
    public val paid_messages: Int?,
    public val premium_gift_months: Int?,
    public val multiflags_5: Multiflags_5?,
    public val multiflags_17: Multiflags_17?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (title != null) result = result or 1U
        if (description != null) result = result or 2U
        if (photo != null) result = result or 4U
        if (refund) result = result or 8U
        if (pending) result = result or 16U
        if (multiflags_5 != null) result = result or 32U
        if (failed) result = result or 64U
        if (bot_payload != null) result = result or 128U
        if (msg_id != null) result = result or 256U
        if (extended_media != null) result = result or 512U
        if (gift) result = result or 1024U
        if (reaction) result = result or 2048U
        if (subscription_period != null) result = result or 4096U
        if (giveaway_post_id != null) result = result or 8192U
        if (stargift != null) result = result or 16384U
        if (floodskip_number != null) result = result or 32768U
        if (starref_commission_permille != null) result = result or 65536U
        if (multiflags_17 != null) result = result or 131072U
        if (stargift_upgrade) result = result or 262144U
        if (paid_messages != null) result = result or 524288U
        if (premium_gift_months != null) result = result or 1048576U
        if (business_transfer) result = result or 2097152U
        if (stargift_resale) result = result or 4194304U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(id)
      stars.serializeToStream(stream)
      stream.writeInt32(date)
      peer.serializeToStream(stream)
      title?.let { stream.writeString(it) }
      description?.let { stream.writeString(it) }
      photo?.serializeToStream(stream)
      multiflags_5?.let { stream.writeInt32(it.transaction_date) }
      multiflags_5?.let { stream.writeString(it.transaction_url) }
      bot_payload?.let { stream.writeByteArray(it.toByteArray()) }
      msg_id?.let { stream.writeInt32(it) }
      extended_media?.let { TlGen_Vector.serialize(stream, it) }
      subscription_period?.let { stream.writeInt32(it) }
      giveaway_post_id?.let { stream.writeInt32(it) }
      stargift?.serializeToStream(stream)
      floodskip_number?.let { stream.writeInt32(it) }
      starref_commission_permille?.let { stream.writeInt32(it) }
      multiflags_17?.let { it.starref_peer.serializeToStream(stream) }
      multiflags_17?.let { it.starref_amount.serializeToStream(stream) }
      paid_messages?.let { stream.writeInt32(it) }
      premium_gift_months?.let { stream.writeInt32(it) }
    }

    public data class Multiflags_5(
      public val transaction_date: Int,
      public val transaction_url: String,
    )

    public data class Multiflags_17(
      public val starref_peer: TlGen_Peer,
      public val starref_amount: TlGen_StarsAmount,
    )

    public companion object {
      public const val MAGIC: UInt = 0xA39FD94AU
    }
  }
}

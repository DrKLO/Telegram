package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputInvoice : TlGen_Object {
  public data class TL_inputInvoiceMessage(
    public val peer: TlGen_InputPeer,
    public val msg_id: Int,
  ) : TlGen_InputInvoice() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(msg_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC5B56859U
    }
  }

  public data class TL_inputInvoiceSlug(
    public val slug: String,
  ) : TlGen_InputInvoice() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(slug)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC326CAEFU
    }
  }

  public data class TL_inputInvoicePremiumGiftCode(
    public val purpose: TlGen_InputStorePaymentPurpose,
    public val option: TlGen_PremiumGiftCodeOption,
  ) : TlGen_InputInvoice() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      purpose.serializeToStream(stream)
      option.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x98986C0DU
    }
  }

  public data class TL_inputInvoiceStars(
    public val purpose: TlGen_InputStorePaymentPurpose,
  ) : TlGen_InputInvoice() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      purpose.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x65F00CE3U
    }
  }

  public data class TL_inputInvoiceChatInviteSubscription(
    public val hash: String,
  ) : TlGen_InputInvoice() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0x34E793F1U
    }
  }

  public data class TL_inputInvoiceStarGift(
    public val hide_name: Boolean,
    public val include_upgrade: Boolean,
    public val peer: TlGen_InputPeer,
    public val gift_id: Long,
    public val message: TlGen_TextWithEntities?,
  ) : TlGen_InputInvoice() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (hide_name) result = result or 1U
        if (message != null) result = result or 2U
        if (include_upgrade) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
      stream.writeInt64(gift_id)
      message?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE8625E92U
    }
  }

  public data class TL_inputInvoiceStarGiftUpgrade(
    public val keep_original_details: Boolean,
    public val stargift: TlGen_InputSavedStarGift,
  ) : TlGen_InputInvoice() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (keep_original_details) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stargift.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4D818D5DU
    }
  }

  public data class TL_inputInvoiceStarGiftTransfer(
    public val stargift: TlGen_InputSavedStarGift,
    public val to_id: TlGen_InputPeer,
  ) : TlGen_InputInvoice() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stargift.serializeToStream(stream)
      to_id.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4A5F5BD9U
    }
  }

  public data class TL_inputInvoicePremiumGiftStars(
    public val user_id: TlGen_InputUser,
    public val months: Int,
    public val message: TlGen_TextWithEntities?,
  ) : TlGen_InputInvoice() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (message != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      user_id.serializeToStream(stream)
      stream.writeInt32(months)
      message?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDABAB2EFU
    }
  }

  public data class TL_inputInvoiceStarGiftResale(
    public val ton: Boolean,
    public val slug: String,
    public val to_id: TlGen_InputPeer,
  ) : TlGen_InputInvoice() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (ton) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(slug)
      to_id.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC39F5324U
    }
  }

  public data class TL_inputInvoiceStarGiftPrepaidUpgrade(
    public val peer: TlGen_InputPeer,
    public val hash: String,
  ) : TlGen_InputInvoice() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeString(hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9A0B48B8U
    }
  }

  public data class TL_inputInvoicePremiumAuthCode(
    public val purpose: TlGen_InputStorePaymentPurpose,
  ) : TlGen_InputInvoice() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      purpose.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3E77F614U
    }
  }

  public data class TL_inputInvoiceStarGiftDropOriginalDetails(
    public val stargift: TlGen_InputSavedStarGift,
  ) : TlGen_InputInvoice() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stargift.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0923D8D1U
    }
  }

  public data class TL_inputInvoiceStarGiftAuctionBid(
    public val hide_name: Boolean,
    public val update_bid: Boolean,
    public val peer: TlGen_InputPeer?,
    public val gift_id: Long,
    public val bid_amount: Long,
    public val message: TlGen_TextWithEntities?,
  ) : TlGen_InputInvoice() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (hide_name) result = result or 1U
        if (message != null) result = result or 2U
        if (update_bid) result = result or 4U
        if (peer != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer?.serializeToStream(stream)
      stream.writeInt64(gift_id)
      stream.writeInt64(bid_amount)
      message?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1ECAFA10U
    }
  }
}

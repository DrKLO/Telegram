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

public sealed class TlGen_InputStorePaymentPurpose : TlGen_Object {
  public data class TL_inputStorePaymentPremiumSubscription(
    public val restore: Boolean,
    public val upgrade: Boolean,
  ) : TlGen_InputStorePaymentPurpose() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (restore) result = result or 1U
        if (upgrade) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
    }

    public companion object {
      public const val MAGIC: UInt = 0xA6751E66U
    }
  }

  public data class TL_inputStorePaymentGiftPremium(
    public val user_id: TlGen_InputUser,
    public val currency: String,
    public val amount: Long,
  ) : TlGen_InputStorePaymentPurpose() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      user_id.serializeToStream(stream)
      stream.writeString(currency)
      stream.writeInt64(amount)
    }

    public companion object {
      public const val MAGIC: UInt = 0x616F7FE8U
    }
  }

  public data class TL_inputStorePaymentPremiumGiveaway(
    public val only_new_subscribers: Boolean,
    public val winners_are_visible: Boolean,
    public val boost_peer: TlGen_InputPeer,
    public val additional_peers: List<TlGen_InputPeer>?,
    public val countries_iso2: List<String>?,
    public val prize_description: String?,
    public val random_id: Long,
    public val until_date: Int,
    public val currency: String,
    public val amount: Long,
  ) : TlGen_InputStorePaymentPurpose() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (only_new_subscribers) result = result or 1U
        if (additional_peers != null) result = result or 2U
        if (countries_iso2 != null) result = result or 4U
        if (winners_are_visible) result = result or 8U
        if (prize_description != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      boost_peer.serializeToStream(stream)
      additional_peers?.let { TlGen_Vector.serialize(stream, it) }
      countries_iso2?.let { TlGen_Vector.serializeString(stream, it) }
      prize_description?.let { stream.writeString(it) }
      stream.writeInt64(random_id)
      stream.writeInt32(until_date)
      stream.writeString(currency)
      stream.writeInt64(amount)
    }

    public companion object {
      public const val MAGIC: UInt = 0x160544CAU
    }
  }

  public data class TL_inputStorePaymentStarsGift(
    public val user_id: TlGen_InputUser,
    public val stars: Long,
    public val currency: String,
    public val amount: Long,
  ) : TlGen_InputStorePaymentPurpose() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      user_id.serializeToStream(stream)
      stream.writeInt64(stars)
      stream.writeString(currency)
      stream.writeInt64(amount)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1D741EF7U
    }
  }

  public data class TL_inputStorePaymentStarsGiveaway(
    public val only_new_subscribers: Boolean,
    public val winners_are_visible: Boolean,
    public val stars: Long,
    public val boost_peer: TlGen_InputPeer,
    public val additional_peers: List<TlGen_InputPeer>?,
    public val countries_iso2: List<String>?,
    public val prize_description: String?,
    public val random_id: Long,
    public val until_date: Int,
    public val currency: String,
    public val amount: Long,
    public val users: Int,
  ) : TlGen_InputStorePaymentPurpose() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (only_new_subscribers) result = result or 1U
        if (additional_peers != null) result = result or 2U
        if (countries_iso2 != null) result = result or 4U
        if (winners_are_visible) result = result or 8U
        if (prize_description != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(stars)
      boost_peer.serializeToStream(stream)
      additional_peers?.let { TlGen_Vector.serialize(stream, it) }
      countries_iso2?.let { TlGen_Vector.serializeString(stream, it) }
      prize_description?.let { stream.writeString(it) }
      stream.writeInt64(random_id)
      stream.writeInt32(until_date)
      stream.writeString(currency)
      stream.writeInt64(amount)
      stream.writeInt32(users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x751F08FAU
    }
  }

  public data class TL_inputStorePaymentPremiumGiftCode(
    public val users: List<TlGen_InputUser>,
    public val boost_peer: TlGen_InputPeer?,
    public val currency: String,
    public val amount: Long,
    public val message: TlGen_TextWithEntities?,
  ) : TlGen_InputStorePaymentPurpose() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (boost_peer != null) result = result or 1U
        if (message != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serialize(stream, users)
      boost_peer?.serializeToStream(stream)
      stream.writeString(currency)
      stream.writeInt64(amount)
      message?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFB790393U
    }
  }

  public data class TL_inputStorePaymentAuthCode(
    public val restore: Boolean,
    public val phone_number: String,
    public val phone_code_hash: String,
    public val currency: String,
    public val amount: Long,
  ) : TlGen_InputStorePaymentPurpose() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (restore) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(phone_number)
      stream.writeString(phone_code_hash)
      stream.writeString(currency)
      stream.writeInt64(amount)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9BB2636DU
    }
  }

  public data class TL_inputStorePaymentStarsTopup(
    public val stars: Long,
    public val currency: String,
    public val amount: Long,
    public val spend_purpose_peer: TlGen_InputPeer?,
  ) : TlGen_InputStorePaymentPurpose() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (spend_purpose_peer != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(stars)
      stream.writeString(currency)
      stream.writeInt64(amount)
      spend_purpose_peer?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF9A2A6CBU
    }
  }

  public data class TL_inputStorePaymentPremiumGiftCode_layer189(
    public val users: List<TlGen_InputUser>,
    public val boost_peer: TlGen_InputPeer?,
    public val currency: String,
    public val amount: Long,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (boost_peer != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serialize(stream, users)
      boost_peer?.serializeToStream(stream)
      stream.writeString(currency)
      stream.writeInt64(amount)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA3805F3FU
    }
  }

  public data class TL_inputStorePaymentPremiumGiveaway_layer167(
    public val only_new_subscribers: Boolean,
    public val boost_peer: TlGen_InputPeer,
    public val additional_peers: List<TlGen_InputPeer>?,
    public val countries_iso2: List<String>?,
    public val random_id: Long,
    public val until_date: Int,
    public val currency: String,
    public val amount: Long,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (only_new_subscribers) result = result or 1U
        if (additional_peers != null) result = result or 2U
        if (countries_iso2 != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      boost_peer.serializeToStream(stream)
      additional_peers?.let { TlGen_Vector.serialize(stream, it) }
      countries_iso2?.let { TlGen_Vector.serializeString(stream, it) }
      stream.writeInt64(random_id)
      stream.writeInt32(until_date)
      stream.writeString(currency)
      stream.writeInt64(amount)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7C9375E6U
    }
  }

  public data class TL_inputStorePaymentStars_layer184(
    public val stars: Long,
    public val currency: String,
    public val amount: Long,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(stars)
      stream.writeString(currency)
      stream.writeInt64(amount)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4F0EE8DFU
    }
  }

  public data class TL_inputStorePaymentStarsTopup_layer212(
    public val stars: Long,
    public val currency: String,
    public val amount: Long,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(stars)
      stream.writeString(currency)
      stream.writeInt64(amount)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDDDD0F56U
    }
  }
}

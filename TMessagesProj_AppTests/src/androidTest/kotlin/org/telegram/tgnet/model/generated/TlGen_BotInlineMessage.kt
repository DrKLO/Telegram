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

public sealed class TlGen_BotInlineMessage : TlGen_Object {
  public data class TL_botInlineMessageText(
    public val no_webpage: Boolean,
    public val invert_media: Boolean,
    public val message: String,
    public val entities: List<TlGen_MessageEntity>?,
    public val reply_markup: TlGen_ReplyMarkup?,
  ) : TlGen_BotInlineMessage() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (no_webpage) result = result or 1U
        if (entities != null) result = result or 2U
        if (reply_markup != null) result = result or 4U
        if (invert_media) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(message)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      reply_markup?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8C7F65E2U
    }
  }

  public data class TL_botInlineMessageMediaAuto(
    public val invert_media: Boolean,
    public val message: String,
    public val entities: List<TlGen_MessageEntity>?,
    public val reply_markup: TlGen_ReplyMarkup?,
  ) : TlGen_BotInlineMessage() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (entities != null) result = result or 2U
        if (reply_markup != null) result = result or 4U
        if (invert_media) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(message)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      reply_markup?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x764CF810U
    }
  }

  public data class TL_botInlineMessageMediaVenue(
    public val geo: TlGen_GeoPoint,
    public val title: String,
    public val address: String,
    public val provider: String,
    public val venue_id: String,
    public val venue_type: String,
    public val reply_markup: TlGen_ReplyMarkup?,
  ) : TlGen_BotInlineMessage() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (reply_markup != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      geo.serializeToStream(stream)
      stream.writeString(title)
      stream.writeString(address)
      stream.writeString(provider)
      stream.writeString(venue_id)
      stream.writeString(venue_type)
      reply_markup?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8A86659CU
    }
  }

  public data class TL_botInlineMessageMediaContact(
    public val phone_number: String,
    public val first_name: String,
    public val last_name: String,
    public val vcard: String,
    public val reply_markup: TlGen_ReplyMarkup?,
  ) : TlGen_BotInlineMessage() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (reply_markup != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(phone_number)
      stream.writeString(first_name)
      stream.writeString(last_name)
      stream.writeString(vcard)
      reply_markup?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x18D1CDC2U
    }
  }

  public data class TL_botInlineMessageMediaGeo(
    public val geo: TlGen_GeoPoint,
    public val heading: Int?,
    public val period: Int?,
    public val proximity_notification_radius: Int?,
    public val reply_markup: TlGen_ReplyMarkup?,
  ) : TlGen_BotInlineMessage() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (heading != null) result = result or 1U
        if (period != null) result = result or 2U
        if (reply_markup != null) result = result or 4U
        if (proximity_notification_radius != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      geo.serializeToStream(stream)
      heading?.let { stream.writeInt32(it) }
      period?.let { stream.writeInt32(it) }
      proximity_notification_radius?.let { stream.writeInt32(it) }
      reply_markup?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x051846FDU
    }
  }

  public data class TL_botInlineMessageMediaInvoice(
    public val shipping_address_requested: Boolean,
    public val test: Boolean,
    public val title: String,
    public val description: String,
    public val photo: TlGen_WebDocument?,
    public val currency: String,
    public val total_amount: Long,
    public val reply_markup: TlGen_ReplyMarkup?,
  ) : TlGen_BotInlineMessage() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (photo != null) result = result or 1U
        if (shipping_address_requested) result = result or 2U
        if (reply_markup != null) result = result or 4U
        if (test) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(title)
      stream.writeString(description)
      photo?.serializeToStream(stream)
      stream.writeString(currency)
      stream.writeInt64(total_amount)
      reply_markup?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x354A9B09U
    }
  }

  public data class TL_botInlineMessageMediaWebPage(
    public val invert_media: Boolean,
    public val force_large_media: Boolean,
    public val force_small_media: Boolean,
    public val manual: Boolean,
    public val safe: Boolean,
    public val message: String,
    public val entities: List<TlGen_MessageEntity>?,
    public val url: String,
    public val reply_markup: TlGen_ReplyMarkup?,
  ) : TlGen_BotInlineMessage() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (entities != null) result = result or 2U
        if (reply_markup != null) result = result or 4U
        if (invert_media) result = result or 8U
        if (force_large_media) result = result or 16U
        if (force_small_media) result = result or 32U
        if (manual) result = result or 128U
        if (safe) result = result or 256U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(message)
      entities?.let { TlGen_Vector.serialize(stream, it) }
      stream.writeString(url)
      reply_markup?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x809AD9A6U
    }
  }
}

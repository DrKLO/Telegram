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

public sealed class TlGen_MessageMedia : TlGen_Object {
  public data object TL_messageMediaEmpty : TlGen_MessageMedia() {
    public const val MAGIC: UInt = 0x3DED6320U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_messageMediaGeo(
    public val geo: TlGen_GeoPoint,
  ) : TlGen_MessageMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      geo.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x56E0D474U
    }
  }

  public data object TL_messageMediaUnsupported : TlGen_MessageMedia() {
    public const val MAGIC: UInt = 0x9F84F49EU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_messageMediaGame(
    public val game: TlGen_Game,
  ) : TlGen_MessageMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      game.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFDB19008U
    }
  }

  public data class TL_messageMediaVenue(
    public val geo: TlGen_GeoPoint,
    public val title: String,
    public val address: String,
    public val provider: String,
    public val venue_id: String,
    public val venue_type: String,
  ) : TlGen_MessageMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      geo.serializeToStream(stream)
      stream.writeString(title)
      stream.writeString(address)
      stream.writeString(provider)
      stream.writeString(venue_id)
      stream.writeString(venue_type)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2EC0533FU
    }
  }

  public data class TL_messageMediaPhoto(
    public val spoiler: Boolean,
    public val photo: TlGen_Photo?,
    public val ttl_seconds: Int?,
  ) : TlGen_MessageMedia() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (photo != null) result = result or 1U
        if (ttl_seconds != null) result = result or 4U
        if (spoiler) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      photo?.serializeToStream(stream)
      ttl_seconds?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x695150D7U
    }
  }

  public data class TL_messageMediaPoll(
    public val poll: TlGen_Poll,
    public val results: TlGen_PollResults,
  ) : TlGen_MessageMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      poll.serializeToStream(stream)
      results.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4BD6E798U
    }
  }

  public data class TL_messageMediaDice(
    public val `value`: Int,
    public val emoticon: String,
  ) : TlGen_MessageMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(value)
      stream.writeString(emoticon)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3F7EE58BU
    }
  }

  public data class TL_messageMediaGeoLive(
    public val geo: TlGen_GeoPoint,
    public val heading: Int?,
    public val period: Int,
    public val proximity_notification_radius: Int?,
  ) : TlGen_MessageMedia() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (heading != null) result = result or 1U
        if (proximity_notification_radius != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      geo.serializeToStream(stream)
      heading?.let { stream.writeInt32(it) }
      stream.writeInt32(period)
      proximity_notification_radius?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xB940C666U
    }
  }

  public data class TL_messageMediaContact(
    public val phone_number: String,
    public val first_name: String,
    public val last_name: String,
    public val vcard: String,
    public val user_id: Long,
  ) : TlGen_MessageMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(phone_number)
      stream.writeString(first_name)
      stream.writeString(last_name)
      stream.writeString(vcard)
      stream.writeInt64(user_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x70322949U
    }
  }

  public data class TL_messageMediaInvoice(
    public val shipping_address_requested: Boolean,
    public val test: Boolean,
    public val title: String,
    public val description: String,
    public val photo: TlGen_WebDocument?,
    public val receipt_msg_id: Int?,
    public val currency: String,
    public val total_amount: Long,
    public val start_param: String,
    public val extended_media: TlGen_MessageExtendedMedia?,
  ) : TlGen_MessageMedia() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (photo != null) result = result or 1U
        if (shipping_address_requested) result = result or 2U
        if (receipt_msg_id != null) result = result or 4U
        if (test) result = result or 8U
        if (extended_media != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(title)
      stream.writeString(description)
      photo?.serializeToStream(stream)
      receipt_msg_id?.let { stream.writeInt32(it) }
      stream.writeString(currency)
      stream.writeInt64(total_amount)
      stream.writeString(start_param)
      extended_media?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF6A548D3U
    }
  }

  public data class TL_messageMediaWebPage(
    public val force_large_media: Boolean,
    public val force_small_media: Boolean,
    public val manual: Boolean,
    public val safe: Boolean,
    public val webpage: TlGen_WebPage,
  ) : TlGen_MessageMedia() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (force_large_media) result = result or 1U
        if (force_small_media) result = result or 2U
        if (manual) result = result or 8U
        if (safe) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      webpage.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDDF10C3BU
    }
  }

  public data class TL_messageMediaStory(
    public val via_mention: Boolean,
    public val peer: TlGen_Peer,
    public val id: Int,
    public val story: TlGen_StoryItem?,
  ) : TlGen_MessageMedia() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (story != null) result = result or 1U
        if (via_mention) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(id)
      story?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x68CB6283U
    }
  }

  public data class TL_messageMediaPaidMedia(
    public val stars_amount: Long,
    public val extended_media: List<TlGen_MessageExtendedMedia>,
  ) : TlGen_MessageMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(stars_amount)
      TlGen_Vector.serialize(stream, extended_media)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA8852491U
    }
  }

  public data class TL_messageMediaGiveaway(
    public val only_new_subscribers: Boolean,
    public val winners_are_visible: Boolean,
    public val channels: List<Long>,
    public val countries_iso2: List<String>?,
    public val prize_description: String?,
    public val quantity: Int,
    public val months: Int?,
    public val stars: Long?,
    public val until_date: Int,
  ) : TlGen_MessageMedia() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (only_new_subscribers) result = result or 1U
        if (countries_iso2 != null) result = result or 2U
        if (winners_are_visible) result = result or 4U
        if (prize_description != null) result = result or 8U
        if (months != null) result = result or 16U
        if (stars != null) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serializeLong(stream, channels)
      countries_iso2?.let { TlGen_Vector.serializeString(stream, it) }
      prize_description?.let { stream.writeString(it) }
      stream.writeInt32(quantity)
      months?.let { stream.writeInt32(it) }
      stars?.let { stream.writeInt64(it) }
      stream.writeInt32(until_date)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAA073BEBU
    }
  }

  public data class TL_messageMediaGiveawayResults(
    public val only_new_subscribers: Boolean,
    public val refunded: Boolean,
    public val channel_id: Long,
    public val additional_peers_count: Int?,
    public val launch_msg_id: Int,
    public val winners_count: Int,
    public val unclaimed_count: Int,
    public val winners: List<Long>,
    public val months: Int?,
    public val stars: Long?,
    public val prize_description: String?,
    public val until_date: Int,
  ) : TlGen_MessageMedia() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (only_new_subscribers) result = result or 1U
        if (prize_description != null) result = result or 2U
        if (refunded) result = result or 4U
        if (additional_peers_count != null) result = result or 8U
        if (months != null) result = result or 16U
        if (stars != null) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(channel_id)
      additional_peers_count?.let { stream.writeInt32(it) }
      stream.writeInt32(launch_msg_id)
      stream.writeInt32(winners_count)
      stream.writeInt32(unclaimed_count)
      TlGen_Vector.serializeLong(stream, winners)
      months?.let { stream.writeInt32(it) }
      stars?.let { stream.writeInt64(it) }
      prize_description?.let { stream.writeString(it) }
      stream.writeInt32(until_date)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCEAA3EA1U
    }
  }

  public data class TL_messageMediaDocument(
    public val nopremium: Boolean,
    public val spoiler: Boolean,
    public val video: Boolean,
    public val round: Boolean,
    public val voice: Boolean,
    public val document: TlGen_Document?,
    public val alt_documents: List<TlGen_Document>?,
    public val video_cover: TlGen_Photo?,
    public val video_timestamp: Int?,
    public val ttl_seconds: Int?,
  ) : TlGen_MessageMedia() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (document != null) result = result or 1U
        if (ttl_seconds != null) result = result or 4U
        if (nopremium) result = result or 8U
        if (spoiler) result = result or 16U
        if (alt_documents != null) result = result or 32U
        if (video) result = result or 64U
        if (round) result = result or 128U
        if (voice) result = result or 256U
        if (video_cover != null) result = result or 512U
        if (video_timestamp != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      document?.serializeToStream(stream)
      alt_documents?.let { TlGen_Vector.serialize(stream, it) }
      video_cover?.serializeToStream(stream)
      video_timestamp?.let { stream.writeInt32(it) }
      ttl_seconds?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x52D8CCD9U
    }
  }

  public data class TL_messageMediaToDo(
    public val todo: TlGen_TodoList,
    public val completions: List<TlGen_TodoCompletion>?,
  ) : TlGen_MessageMedia() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (completions != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      todo.serializeToStream(stream)
      completions?.let { TlGen_Vector.serialize(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x8A53B014U
    }
  }

  public data class TL_messageMediaVideoStream(
    public val rtmp_stream: Boolean,
    public val call: TlGen_InputGroupCall,
  ) : TlGen_MessageMedia() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (rtmp_stream) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      call.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCA5CAB89U
    }
  }

  public data class TL_messageMediaPhoto_layer27(
    public val photo: TlGen_Photo,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      photo.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC8C45A2AU
    }
  }

  public data class TL_messageMediaVideo_layer27(
    public val video: TlGen_Video,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      video.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA2D24290U
    }
  }

  public data class TL_messageMediaContact_layer81(
    public val phone_number: String,
    public val first_name: String,
    public val last_name: String,
    public val user_id: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(phone_number)
      stream.writeString(first_name)
      stream.writeString(last_name)
      stream.writeInt32(user_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5E7D2F39U
    }
  }

  public data class TL_messageMediaDocument_layer44(
    public val document: TlGen_Document,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      document.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2FDA2204U
    }
  }

  public data class TL_messageMediaAudio_layer45(
    public val audio: TlGen_Audio,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      audio.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC6B68300U
    }
  }

  public data class TL_messageMediaWebPage_layer165(
    public val webpage: TlGen_WebPage,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      webpage.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA32DD600U
    }
  }

  public data class TL_messageMediaPhoto_layer69(
    public val photo: TlGen_Photo,
    public val caption: String,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      photo.serializeToStream(stream)
      stream.writeString(caption)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3D8CE53DU
    }
  }

  public data class TL_messageMediaVideo_layer46(
    public val video: TlGen_Video,
    public val caption: String,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      video.serializeToStream(stream)
      stream.writeString(caption)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5BCF1675U
    }
  }

  public data class TL_messageMediaVenue_layer71(
    public val geo: TlGen_GeoPoint,
    public val title: String,
    public val address: String,
    public val provider: String,
    public val venue_id: String,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      geo.serializeToStream(stream)
      stream.writeString(title)
      stream.writeString(address)
      stream.writeString(provider)
      stream.writeString(venue_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7912B71FU
    }
  }

  public data class TL_messageMediaDocument_layer69(
    public val document: TlGen_Document,
    public val caption: String,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      document.serializeToStream(stream)
      stream.writeString(caption)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF3E02EA8U
    }
  }

  public data class TL_messageMediaInvoice_layer145(
    public val shipping_address_requested: Boolean,
    public val test: Boolean,
    public val title: String,
    public val description: String,
    public val photo: TlGen_WebDocument?,
    public val receipt_msg_id: Int?,
    public val currency: String,
    public val total_amount: Long,
    public val start_param: String,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (photo != null) result = result or 1U
        if (shipping_address_requested) result = result or 2U
        if (receipt_msg_id != null) result = result or 4U
        if (test) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(title)
      stream.writeString(description)
      photo?.serializeToStream(stream)
      receipt_msg_id?.let { stream.writeInt32(it) }
      stream.writeString(currency)
      stream.writeInt64(total_amount)
      stream.writeString(start_param)
    }

    public companion object {
      public const val MAGIC: UInt = 0x84551347U
    }
  }

  public data class TL_messageMediaPhoto_layer74(
    public val photo: TlGen_Photo?,
    public val caption: String?,
    public val ttl_seconds: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (photo != null) result = result or 1U
        if (caption != null) result = result or 2U
        if (ttl_seconds != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      photo?.serializeToStream(stream)
      caption?.let { stream.writeString(it) }
      ttl_seconds?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xB5223B0FU
    }
  }

  public data class TL_messageMediaDocument_layer74(
    public val document: TlGen_Document?,
    public val caption: String?,
    public val ttl_seconds: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (document != null) result = result or 1U
        if (caption != null) result = result or 2U
        if (ttl_seconds != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      document?.serializeToStream(stream)
      caption?.let { stream.writeString(it) }
      ttl_seconds?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x7C4414D3U
    }
  }

  public data class TL_messageMediaGeoLive_layer119(
    public val geo: TlGen_GeoPoint,
    public val period: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      geo.serializeToStream(stream)
      stream.writeInt32(period)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7C3C2609U
    }
  }

  public data class TL_messageMediaDocument_layer159(
    public val nopremium: Boolean,
    public val spoiler: Boolean,
    public val document: TlGen_Document?,
    public val ttl_seconds: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (document != null) result = result or 1U
        if (ttl_seconds != null) result = result or 4U
        if (nopremium) result = result or 8U
        if (spoiler) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      document?.serializeToStream(stream)
      ttl_seconds?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x9CB070D7U
    }
  }

  public data class TL_messageMediaContact_layer132(
    public val phone_number: String,
    public val first_name: String,
    public val last_name: String,
    public val vcard: String,
    public val user_id: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(phone_number)
      stream.writeString(first_name)
      stream.writeString(last_name)
      stream.writeString(vcard)
      stream.writeInt32(user_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCBF24940U
    }
  }

  public data class TL_messageMediaDice_layer111(
    public val `value`: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(value)
    }

    public companion object {
      public const val MAGIC: UInt = 0x638FE46BU
    }
  }

  public data class TL_messageMediaDocument_layer187(
    public val nopremium: Boolean,
    public val spoiler: Boolean,
    public val video: Boolean,
    public val round: Boolean,
    public val voice: Boolean,
    public val document: TlGen_Document?,
    public val alt_document: TlGen_Document?,
    public val ttl_seconds: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (document != null) result = result or 1U
        if (ttl_seconds != null) result = result or 4U
        if (nopremium) result = result or 8U
        if (spoiler) result = result or 16U
        if (alt_document != null) result = result or 32U
        if (video) result = result or 64U
        if (round) result = result or 128U
        if (voice) result = result or 256U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      document?.serializeToStream(stream)
      alt_document?.serializeToStream(stream)
      ttl_seconds?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x4CF4D72DU
    }
  }

  public data class TL_messageMediaStory_layer163(
    public val via_mention: Boolean,
    public val user_id: Long,
    public val id: Int,
    public val story: TlGen_StoryItem?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (story != null) result = result or 1U
        if (via_mention) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(user_id)
      stream.writeInt32(id)
      story?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCBB20D88U
    }
  }

  public data class TL_messageMediaGiveaway_layer186(
    public val only_new_subscribers: Boolean,
    public val winners_are_visible: Boolean,
    public val channels: List<Long>,
    public val countries_iso2: List<String>?,
    public val prize_description: String?,
    public val quantity: Int,
    public val months: Int,
    public val until_date: Int,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (only_new_subscribers) result = result or 1U
        if (countries_iso2 != null) result = result or 2U
        if (winners_are_visible) result = result or 4U
        if (prize_description != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serializeLong(stream, channels)
      countries_iso2?.let { TlGen_Vector.serializeString(stream, it) }
      prize_description?.let { stream.writeString(it) }
      stream.writeInt32(quantity)
      stream.writeInt32(months)
      stream.writeInt32(until_date)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDAAD85B0U
    }
  }

  public data class TL_messageMediaGiveawayResults_layer186(
    public val only_new_subscribers: Boolean,
    public val refunded: Boolean,
    public val channel_id: Long,
    public val additional_peers_count: Int?,
    public val launch_msg_id: Int,
    public val winners_count: Int,
    public val unclaimed_count: Int,
    public val winners: List<Long>,
    public val months: Int,
    public val prize_description: String?,
    public val until_date: Int,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (only_new_subscribers) result = result or 1U
        if (prize_description != null) result = result or 2U
        if (refunded) result = result or 4U
        if (additional_peers_count != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(channel_id)
      additional_peers_count?.let { stream.writeInt32(it) }
      stream.writeInt32(launch_msg_id)
      stream.writeInt32(winners_count)
      stream.writeInt32(unclaimed_count)
      TlGen_Vector.serializeLong(stream, winners)
      stream.writeInt32(months)
      prize_description?.let { stream.writeString(it) }
      stream.writeInt32(until_date)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC6991068U
    }
  }

  public data class TL_messageMediaGiveaway_layer167(
    public val only_new_subscribers: Boolean,
    public val channels: List<Long>,
    public val countries_iso2: List<String>?,
    public val quantity: Int,
    public val months: Int,
    public val until_date: Int,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (only_new_subscribers) result = result or 1U
        if (countries_iso2 != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serializeLong(stream, channels)
      countries_iso2?.let { TlGen_Vector.serializeString(stream, it) }
      stream.writeInt32(quantity)
      stream.writeInt32(months)
      stream.writeInt32(until_date)
    }

    public companion object {
      public const val MAGIC: UInt = 0x58260664U
    }
  }

  public data class TL_messageMediaDocument_layer197(
    public val nopremium: Boolean,
    public val spoiler: Boolean,
    public val video: Boolean,
    public val round: Boolean,
    public val voice: Boolean,
    public val document: TlGen_Document?,
    public val alt_documents: List<TlGen_Document>?,
    public val ttl_seconds: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (document != null) result = result or 1U
        if (ttl_seconds != null) result = result or 4U
        if (nopremium) result = result or 8U
        if (spoiler) result = result or 16U
        if (alt_documents != null) result = result or 32U
        if (video) result = result or 64U
        if (round) result = result or 128U
        if (voice) result = result or 256U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      document?.serializeToStream(stream)
      alt_documents?.let { TlGen_Vector.serialize(stream, it) }
      ttl_seconds?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xDD570BD5U
    }
  }
}

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

public sealed class TlGen_InputMedia : TlGen_Object {
  public data object TL_inputMediaEmpty : TlGen_InputMedia() {
    public const val MAGIC: UInt = 0x9664F57FU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_inputMediaGeoPoint(
    public val geo_point: TlGen_InputGeoPoint,
  ) : TlGen_InputMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      geo_point.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF9C44144U
    }
  }

  public data class TL_inputMediaGame(
    public val id: TlGen_InputGame,
  ) : TlGen_InputMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      id.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD33F43F3U
    }
  }

  public data class TL_inputMediaVenue(
    public val geo_point: TlGen_InputGeoPoint,
    public val title: String,
    public val address: String,
    public val provider: String,
    public val venue_id: String,
    public val venue_type: String,
  ) : TlGen_InputMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      geo_point.serializeToStream(stream)
      stream.writeString(title)
      stream.writeString(address)
      stream.writeString(provider)
      stream.writeString(venue_id)
      stream.writeString(venue_type)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC13D1C11U
    }
  }

  public data class TL_inputMediaUploadedPhoto(
    public val spoiler: Boolean,
    public val `file`: TlGen_InputFile,
    public val stickers: List<TlGen_InputDocument>?,
    public val ttl_seconds: Int?,
  ) : TlGen_InputMedia() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (stickers != null) result = result or 1U
        if (ttl_seconds != null) result = result or 2U
        if (spoiler) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      file.serializeToStream(stream)
      stickers?.let { TlGen_Vector.serialize(stream, it) }
      ttl_seconds?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x1E287D04U
    }
  }

  public data class TL_inputMediaPhoto(
    public val spoiler: Boolean,
    public val id: TlGen_InputPhoto,
    public val ttl_seconds: Int?,
  ) : TlGen_InputMedia() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (ttl_seconds != null) result = result or 1U
        if (spoiler) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      id.serializeToStream(stream)
      ttl_seconds?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xB3BA0635U
    }
  }

  public data class TL_inputMediaPhotoExternal(
    public val spoiler: Boolean,
    public val url: String,
    public val ttl_seconds: Int?,
  ) : TlGen_InputMedia() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (ttl_seconds != null) result = result or 1U
        if (spoiler) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(url)
      ttl_seconds?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xE5BBFE1AU
    }
  }

  public data class TL_inputMediaContact(
    public val phone_number: String,
    public val first_name: String,
    public val last_name: String,
    public val vcard: String,
  ) : TlGen_InputMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(phone_number)
      stream.writeString(first_name)
      stream.writeString(last_name)
      stream.writeString(vcard)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF8AB7DFBU
    }
  }

  public data class TL_inputMediaPoll(
    public val poll: TlGen_Poll,
    public val correct_answers: List<List<Byte>>?,
    public val multiflags_1: Multiflags_1?,
  ) : TlGen_InputMedia() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (correct_answers != null) result = result or 1U
        if (multiflags_1 != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      poll.serializeToStream(stream)
      correct_answers?.let { TlGen_Vector.serializeBytes(stream, it) }
      multiflags_1?.let { stream.writeString(it.solution) }
      multiflags_1?.let { TlGen_Vector.serialize(stream, it.solution_entities) }
    }

    public data class Multiflags_1(
      public val solution: String,
      public val solution_entities: List<TlGen_MessageEntity>,
    )

    public companion object {
      public const val MAGIC: UInt = 0x0F94E5F1U
    }
  }

  public data class TL_inputMediaDice(
    public val emoticon: String,
  ) : TlGen_InputMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(emoticon)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE66FBF7BU
    }
  }

  public data class TL_inputMediaGeoLive(
    public val stopped: Boolean,
    public val geo_point: TlGen_InputGeoPoint,
    public val heading: Int?,
    public val period: Int?,
    public val proximity_notification_radius: Int?,
  ) : TlGen_InputMedia() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (stopped) result = result or 1U
        if (period != null) result = result or 2U
        if (heading != null) result = result or 4U
        if (proximity_notification_radius != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      geo_point.serializeToStream(stream)
      heading?.let { stream.writeInt32(it) }
      period?.let { stream.writeInt32(it) }
      proximity_notification_radius?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x971FA843U
    }
  }

  public data class TL_inputMediaStory(
    public val peer: TlGen_InputPeer,
    public val id: Int,
  ) : TlGen_InputMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x89FDD778U
    }
  }

  public data class TL_inputMediaWebPage(
    public val force_large_media: Boolean,
    public val force_small_media: Boolean,
    public val optional: Boolean,
    public val url: String,
  ) : TlGen_InputMedia() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (force_large_media) result = result or 1U
        if (force_small_media) result = result or 2U
        if (optional) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(url)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC21B8849U
    }
  }

  public data class TL_inputMediaInvoice(
    public val title: String,
    public val description: String,
    public val photo: TlGen_InputWebDocument?,
    public val invoice: TlGen_Invoice,
    public val payload: List<Byte>,
    public val provider: String?,
    public val provider_data: TlGen_DataJSON,
    public val start_param: String?,
    public val extended_media: TlGen_InputMedia?,
  ) : TlGen_InputMedia() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (photo != null) result = result or 1U
        if (start_param != null) result = result or 2U
        if (extended_media != null) result = result or 4U
        if (provider != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(title)
      stream.writeString(description)
      photo?.serializeToStream(stream)
      invoice.serializeToStream(stream)
      stream.writeByteArray(payload.toByteArray())
      provider?.let { stream.writeString(it) }
      provider_data.serializeToStream(stream)
      start_param?.let { stream.writeString(it) }
      extended_media?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x405FEF0DU
    }
  }

  public data class TL_inputMediaPaidMedia(
    public val stars_amount: Long,
    public val extended_media: List<TlGen_InputMedia>,
    public val payload: String?,
  ) : TlGen_InputMedia() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (payload != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(stars_amount)
      TlGen_Vector.serialize(stream, extended_media)
      payload?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xC4103386U
    }
  }

  public data class TL_inputMediaUploadedDocument(
    public val nosound_video: Boolean,
    public val force_file: Boolean,
    public val spoiler: Boolean,
    public val `file`: TlGen_InputFile,
    public val thumb: TlGen_InputFile?,
    public val mime_type: String,
    public val attributes: List<TlGen_DocumentAttribute>,
    public val stickers: List<TlGen_InputDocument>?,
    public val video_cover: TlGen_InputPhoto?,
    public val video_timestamp: Int?,
    public val ttl_seconds: Int?,
  ) : TlGen_InputMedia() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (stickers != null) result = result or 1U
        if (ttl_seconds != null) result = result or 2U
        if (thumb != null) result = result or 4U
        if (nosound_video) result = result or 8U
        if (force_file) result = result or 16U
        if (spoiler) result = result or 32U
        if (video_cover != null) result = result or 64U
        if (video_timestamp != null) result = result or 128U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      file.serializeToStream(stream)
      thumb?.serializeToStream(stream)
      stream.writeString(mime_type)
      TlGen_Vector.serialize(stream, attributes)
      stickers?.let { TlGen_Vector.serialize(stream, it) }
      video_cover?.serializeToStream(stream)
      video_timestamp?.let { stream.writeInt32(it) }
      ttl_seconds?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x037C9330U
    }
  }

  public data class TL_inputMediaDocument(
    public val spoiler: Boolean,
    public val id: TlGen_InputDocument,
    public val video_cover: TlGen_InputPhoto?,
    public val video_timestamp: Int?,
    public val ttl_seconds: Int?,
    public val query: String?,
  ) : TlGen_InputMedia() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (ttl_seconds != null) result = result or 1U
        if (query != null) result = result or 2U
        if (spoiler) result = result or 4U
        if (video_cover != null) result = result or 8U
        if (video_timestamp != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      id.serializeToStream(stream)
      video_cover?.serializeToStream(stream)
      video_timestamp?.let { stream.writeInt32(it) }
      ttl_seconds?.let { stream.writeInt32(it) }
      query?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xA8763AB5U
    }
  }

  public data class TL_inputMediaDocumentExternal(
    public val spoiler: Boolean,
    public val url: String,
    public val ttl_seconds: Int?,
    public val video_cover: TlGen_InputPhoto?,
    public val video_timestamp: Int?,
  ) : TlGen_InputMedia() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (ttl_seconds != null) result = result or 1U
        if (spoiler) result = result or 2U
        if (video_cover != null) result = result or 4U
        if (video_timestamp != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(url)
      ttl_seconds?.let { stream.writeInt32(it) }
      video_cover?.serializeToStream(stream)
      video_timestamp?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x779600F9U
    }
  }

  public data class TL_inputMediaTodo(
    public val todo: TlGen_TodoList,
  ) : TlGen_InputMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      todo.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9FC55FDEU
    }
  }

  public data class TL_inputMediaUploadedDocument_layer197(
    public val nosound_video: Boolean,
    public val force_file: Boolean,
    public val spoiler: Boolean,
    public val `file`: TlGen_InputFile,
    public val thumb: TlGen_InputFile?,
    public val mime_type: String,
    public val attributes: List<TlGen_DocumentAttribute>,
    public val stickers: List<TlGen_InputDocument>?,
    public val ttl_seconds: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (stickers != null) result = result or 1U
        if (ttl_seconds != null) result = result or 2U
        if (thumb != null) result = result or 4U
        if (nosound_video) result = result or 8U
        if (force_file) result = result or 16U
        if (spoiler) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      file.serializeToStream(stream)
      thumb?.serializeToStream(stream)
      stream.writeString(mime_type)
      TlGen_Vector.serialize(stream, attributes)
      stickers?.let { TlGen_Vector.serialize(stream, it) }
      ttl_seconds?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x5B38C6C1U
    }
  }

  public data class TL_inputMediaDocumentExternal_layer197(
    public val spoiler: Boolean,
    public val url: String,
    public val ttl_seconds: Int?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (ttl_seconds != null) result = result or 1U
        if (spoiler) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(url)
      ttl_seconds?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xFB52DC99U
    }
  }

  public data class TL_inputMediaDocument_layer197(
    public val spoiler: Boolean,
    public val id: TlGen_InputDocument,
    public val ttl_seconds: Int?,
    public val query: String?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (ttl_seconds != null) result = result or 1U
        if (query != null) result = result or 2U
        if (spoiler) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      id.serializeToStream(stream)
      ttl_seconds?.let { stream.writeInt32(it) }
      query?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x33473058U
    }
  }

  public data class TL_inputMediaInvoice_layer180(
    public val title: String,
    public val description: String,
    public val photo: TlGen_InputWebDocument?,
    public val invoice: TlGen_Invoice,
    public val payload: List<Byte>,
    public val provider: String,
    public val provider_data: TlGen_DataJSON,
    public val start_param: String?,
    public val extended_media: TlGen_InputMedia?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (photo != null) result = result or 1U
        if (start_param != null) result = result or 2U
        if (extended_media != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(title)
      stream.writeString(description)
      photo?.serializeToStream(stream)
      invoice.serializeToStream(stream)
      stream.writeByteArray(payload.toByteArray())
      stream.writeString(provider)
      provider_data.serializeToStream(stream)
      start_param?.let { stream.writeString(it) }
      extended_media?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8EB5A6D5U
    }
  }

  public data class TL_inputMediaPaidMedia_layer186(
    public val stars_amount: Long,
    public val extended_media: List<TlGen_InputMedia>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(stars_amount)
      TlGen_Vector.serialize(stream, extended_media)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAA661FC3U
    }
  }
}

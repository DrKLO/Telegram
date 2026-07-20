package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Double
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_DecryptedMessageMedia : TlGen_Object {
  public data object TL_decryptedMessageMediaEmpty_layer8 : TlGen_DecryptedMessageMedia() {
    public const val MAGIC: UInt = 0x089F5C4AU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_decryptedMessageMediaPhoto_layer8(
    public val thumb: List<Byte>,
    public val thumb_w: Int,
    public val thumb_h: Int,
    public val w: Int,
    public val h: Int,
    public val size: Int,
    public val key: List<Byte>,
    public val iv: List<Byte>,
  ) : TlGen_DecryptedMessageMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeByteArray(thumb.toByteArray())
      stream.writeInt32(thumb_w)
      stream.writeInt32(thumb_h)
      stream.writeInt32(w)
      stream.writeInt32(h)
      stream.writeInt32(size)
      stream.writeByteArray(key.toByteArray())
      stream.writeByteArray(iv.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x32798A8CU
    }
  }

  public data class TL_decryptedMessageMediaPhoto_layer45(
    public val thumb: List<Byte>,
    public val thumb_w: Int,
    public val thumb_h: Int,
    public val w: Int,
    public val h: Int,
    public val size: Int,
    public val key: List<Byte>,
    public val iv: List<Byte>,
    public val caption: String,
  ) : TlGen_DecryptedMessageMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeByteArray(thumb.toByteArray())
      stream.writeInt32(thumb_w)
      stream.writeInt32(thumb_h)
      stream.writeInt32(w)
      stream.writeInt32(h)
      stream.writeInt32(size)
      stream.writeByteArray(key.toByteArray())
      stream.writeByteArray(iv.toByteArray())
      stream.writeString(caption)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF1FA8D78U
    }
  }

  public data class TL_decryptedMessageMediaVideo_layer8(
    public val thumb: List<Byte>,
    public val thumb_w: Int,
    public val thumb_h: Int,
    public val duration: Int,
    public val w: Int,
    public val h: Int,
    public val size: Int,
    public val key: List<Byte>,
    public val iv: List<Byte>,
  ) : TlGen_DecryptedMessageMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeByteArray(thumb.toByteArray())
      stream.writeInt32(thumb_w)
      stream.writeInt32(thumb_h)
      stream.writeInt32(duration)
      stream.writeInt32(w)
      stream.writeInt32(h)
      stream.writeInt32(size)
      stream.writeByteArray(key.toByteArray())
      stream.writeByteArray(iv.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x4CEE6EF3U
    }
  }

  public data class TL_decryptedMessageMediaVideo_layer17(
    public val thumb: List<Byte>,
    public val thumb_w: Int,
    public val thumb_h: Int,
    public val duration: Int,
    public val mime_type: String,
    public val w: Int,
    public val h: Int,
    public val size: Int,
    public val key: List<Byte>,
    public val iv: List<Byte>,
  ) : TlGen_DecryptedMessageMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeByteArray(thumb.toByteArray())
      stream.writeInt32(thumb_w)
      stream.writeInt32(thumb_h)
      stream.writeInt32(duration)
      stream.writeString(mime_type)
      stream.writeInt32(w)
      stream.writeInt32(h)
      stream.writeInt32(size)
      stream.writeByteArray(key.toByteArray())
      stream.writeByteArray(iv.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x524A415DU
    }
  }

  public data class TL_decryptedMessageMediaVideo_layer45(
    public val thumb: List<Byte>,
    public val thumb_w: Int,
    public val thumb_h: Int,
    public val duration: Int,
    public val mime_type: String,
    public val w: Int,
    public val h: Int,
    public val size: Int,
    public val key: List<Byte>,
    public val iv: List<Byte>,
    public val caption: String,
  ) : TlGen_DecryptedMessageMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeByteArray(thumb.toByteArray())
      stream.writeInt32(thumb_w)
      stream.writeInt32(thumb_h)
      stream.writeInt32(duration)
      stream.writeString(mime_type)
      stream.writeInt32(w)
      stream.writeInt32(h)
      stream.writeInt32(size)
      stream.writeByteArray(key.toByteArray())
      stream.writeByteArray(iv.toByteArray())
      stream.writeString(caption)
    }

    public companion object {
      public const val MAGIC: UInt = 0x970C8C0EU
    }
  }

  public data class TL_decryptedMessageMediaGeoPoint_layer8(
    public val lat: Double,
    public val long: Double,
  ) : TlGen_DecryptedMessageMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeDouble(lat)
      stream.writeDouble(long)
    }

    public companion object {
      public const val MAGIC: UInt = 0x35480A59U
    }
  }

  public data class TL_decryptedMessageMediaContact_layer8(
    public val phone_number: String,
    public val first_name: String,
    public val last_name: String,
    public val user_id: Int,
  ) : TlGen_DecryptedMessageMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(phone_number)
      stream.writeString(first_name)
      stream.writeString(last_name)
      stream.writeInt32(user_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x588A0A97U
    }
  }

  public data class TL_decryptedMessageMediaDocument_layer8(
    public val thumb: List<Byte>,
    public val thumb_w: Int,
    public val thumb_h: Int,
    public val file_name: String,
    public val mime_type: String,
    public val size: Int,
    public val key: List<Byte>,
    public val iv: List<Byte>,
  ) : TlGen_DecryptedMessageMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeByteArray(thumb.toByteArray())
      stream.writeInt32(thumb_w)
      stream.writeInt32(thumb_h)
      stream.writeString(file_name)
      stream.writeString(mime_type)
      stream.writeInt32(size)
      stream.writeByteArray(key.toByteArray())
      stream.writeByteArray(iv.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0xB095434BU
    }
  }

  public data class TL_decryptedMessageMediaDocument_layer45(
    public val thumb: List<Byte>,
    public val thumb_w: Int,
    public val thumb_h: Int,
    public val mime_type: String,
    public val size: Int,
    public val key: List<Byte>,
    public val iv: List<Byte>,
    public val attributes: List<TlGen_DocumentAttribute>,
    public val caption: String,
  ) : TlGen_DecryptedMessageMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeByteArray(thumb.toByteArray())
      stream.writeInt32(thumb_w)
      stream.writeInt32(thumb_h)
      stream.writeString(mime_type)
      stream.writeInt32(size)
      stream.writeByteArray(key.toByteArray())
      stream.writeByteArray(iv.toByteArray())
      TlGen_Vector.serialize(stream, attributes)
      stream.writeString(caption)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7AFE8AE2U
    }
  }

  public data class TL_decryptedMessageMediaDocument_layer143(
    public val thumb: List<Byte>,
    public val thumb_w: Int,
    public val thumb_h: Int,
    public val mime_type: String,
    public val size: Long,
    public val key: List<Byte>,
    public val iv: List<Byte>,
    public val attributes: List<TlGen_DocumentAttribute>,
    public val caption: String,
  ) : TlGen_DecryptedMessageMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeByteArray(thumb.toByteArray())
      stream.writeInt32(thumb_w)
      stream.writeInt32(thumb_h)
      stream.writeString(mime_type)
      stream.writeInt64(size)
      stream.writeByteArray(key.toByteArray())
      stream.writeByteArray(iv.toByteArray())
      TlGen_Vector.serialize(stream, attributes)
      stream.writeString(caption)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6ABD9782U
    }
  }

  public data class TL_decryptedMessageMediaAudio_layer8(
    public val duration: Int,
    public val size: Int,
    public val key: List<Byte>,
    public val iv: List<Byte>,
  ) : TlGen_DecryptedMessageMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(duration)
      stream.writeInt32(size)
      stream.writeByteArray(key.toByteArray())
      stream.writeByteArray(iv.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x6080758FU
    }
  }

  public data class TL_decryptedMessageMediaAudio_layer17(
    public val duration: Int,
    public val mime_type: String,
    public val size: Int,
    public val key: List<Byte>,
    public val iv: List<Byte>,
  ) : TlGen_DecryptedMessageMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(duration)
      stream.writeString(mime_type)
      stream.writeInt32(size)
      stream.writeByteArray(key.toByteArray())
      stream.writeByteArray(iv.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x57E0A9CBU
    }
  }

  public data class TL_decryptedMessageMediaExternalDocument_layer23(
    public val id: Long,
    public val access_hash: Long,
    public val date: Int,
    public val mime_type: String,
    public val size: Int,
    public val thumb: TlGen_PhotoSize,
    public val dc_id: Int,
    public val attributes: List<TlGen_DocumentAttribute>,
  ) : TlGen_DecryptedMessageMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeInt32(date)
      stream.writeString(mime_type)
      stream.writeInt32(size)
      thumb.serializeToStream(stream)
      stream.writeInt32(dc_id)
      TlGen_Vector.serialize(stream, attributes)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFA95B0DDU
    }
  }

  public data class TL_decryptedMessageMediaVenue_layer45(
    public val lat: Double,
    public val long: Double,
    public val title: String,
    public val address: String,
    public val provider: String,
    public val venue_id: String,
  ) : TlGen_DecryptedMessageMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeDouble(lat)
      stream.writeDouble(long)
      stream.writeString(title)
      stream.writeString(address)
      stream.writeString(provider)
      stream.writeString(venue_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8A0DF56FU
    }
  }

  public data class TL_decryptedMessageMediaWebPage_layer45(
    public val url: String,
  ) : TlGen_DecryptedMessageMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(url)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE50511D8U
    }
  }
}

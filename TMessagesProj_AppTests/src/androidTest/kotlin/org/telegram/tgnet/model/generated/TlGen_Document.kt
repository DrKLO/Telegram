package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_Document : TlGen_Object {
  public data class TL_documentEmpty(
    public val id: Long,
  ) : TlGen_Document() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x36F8C871U
    }
  }

  public data class TL_document(
    public val id: Long,
    public val access_hash: Long,
    public val file_reference: List<Byte>,
    public val date: Int,
    public val mime_type: String,
    public val size: Long,
    public val thumbs: List<TlGen_PhotoSize>?,
    public val video_thumbs: List<TlGen_VideoSize>?,
    public val dc_id: Int,
    public val attributes: List<TlGen_DocumentAttribute>,
  ) : TlGen_Document() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (thumbs != null) result = result or 1U
        if (video_thumbs != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeByteArray(file_reference.toByteArray())
      stream.writeInt32(date)
      stream.writeString(mime_type)
      stream.writeInt64(size)
      thumbs?.let { TlGen_Vector.serialize(stream, it) }
      video_thumbs?.let { TlGen_Vector.serialize(stream, it) }
      stream.writeInt32(dc_id)
      TlGen_Vector.serialize(stream, attributes)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8FD4C4D8U
    }
  }

  public data class TL_document_layer21(
    public val id: Long,
    public val access_hash: Long,
    public val user_id: Int,
    public val date: Int,
    public val file_name: String,
    public val mime_type: String,
    public val size: Int,
    public val thumb: TlGen_PhotoSize,
    public val dc_id: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeInt32(user_id)
      stream.writeInt32(date)
      stream.writeString(file_name)
      stream.writeString(mime_type)
      stream.writeInt32(size)
      thumb.serializeToStream(stream)
      stream.writeInt32(dc_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9EFC6326U
    }
  }

  public data class TL_document_layer53(
    public val id: Long,
    public val access_hash: Long,
    public val date: Int,
    public val mime_type: String,
    public val size: Int,
    public val thumb: TlGen_PhotoSize,
    public val dc_id: Int,
    public val attributes: List<TlGen_DocumentAttribute>,
  ) : TlGen_Object {
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
      public const val MAGIC: UInt = 0xF9A39F4FU
    }
  }

  public data class TL_document_layer85(
    public val id: Long,
    public val access_hash: Long,
    public val date: Int,
    public val mime_type: String,
    public val size: Int,
    public val thumb: TlGen_PhotoSize,
    public val dc_id: Int,
    public val version: Int,
    public val attributes: List<TlGen_DocumentAttribute>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeInt32(date)
      stream.writeString(mime_type)
      stream.writeInt32(size)
      thumb.serializeToStream(stream)
      stream.writeInt32(dc_id)
      stream.writeInt32(version)
      TlGen_Vector.serialize(stream, attributes)
    }

    public companion object {
      public const val MAGIC: UInt = 0x87232BC7U
    }
  }

  public data class TL_document_layer92(
    public val id: Long,
    public val access_hash: Long,
    public val file_reference: List<Byte>,
    public val date: Int,
    public val mime_type: String,
    public val size: Int,
    public val thumb: TlGen_PhotoSize,
    public val dc_id: Int,
    public val attributes: List<TlGen_DocumentAttribute>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeByteArray(file_reference.toByteArray())
      stream.writeInt32(date)
      stream.writeString(mime_type)
      stream.writeInt32(size)
      thumb.serializeToStream(stream)
      stream.writeInt32(dc_id)
      TlGen_Vector.serialize(stream, attributes)
    }

    public companion object {
      public const val MAGIC: UInt = 0x59534E4CU
    }
  }

  public data class TL_document_layer113(
    public val id: Long,
    public val access_hash: Long,
    public val file_reference: List<Byte>,
    public val date: Int,
    public val mime_type: String,
    public val size: Int,
    public val thumbs: List<TlGen_PhotoSize>?,
    public val dc_id: Int,
    public val attributes: List<TlGen_DocumentAttribute>,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (thumbs != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeByteArray(file_reference.toByteArray())
      stream.writeInt32(date)
      stream.writeString(mime_type)
      stream.writeInt32(size)
      thumbs?.let { TlGen_Vector.serialize(stream, it) }
      stream.writeInt32(dc_id)
      TlGen_Vector.serialize(stream, attributes)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9BA29CC1U
    }
  }

  public data class TL_document_layer142(
    public val id: Long,
    public val access_hash: Long,
    public val file_reference: List<Byte>,
    public val date: Int,
    public val mime_type: String,
    public val size: Int,
    public val thumbs: List<TlGen_PhotoSize>?,
    public val video_thumbs: List<TlGen_VideoSize>?,
    public val dc_id: Int,
    public val attributes: List<TlGen_DocumentAttribute>,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (thumbs != null) result = result or 1U
        if (video_thumbs != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeByteArray(file_reference.toByteArray())
      stream.writeInt32(date)
      stream.writeString(mime_type)
      stream.writeInt32(size)
      thumbs?.let { TlGen_Vector.serialize(stream, it) }
      video_thumbs?.let { TlGen_Vector.serialize(stream, it) }
      stream.writeInt32(dc_id)
      TlGen_Vector.serialize(stream, attributes)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1E87342BU
    }
  }
}

package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_WebDocument : TlGen_Object {
  public data class TL_webDocumentNoProxy(
    public val url: String,
    public val size: Int,
    public val mime_type: String,
    public val attributes: List<TlGen_DocumentAttribute>,
  ) : TlGen_WebDocument() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(url)
      stream.writeInt32(size)
      stream.writeString(mime_type)
      TlGen_Vector.serialize(stream, attributes)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF9C8BCC6U
    }
  }

  public data class TL_webDocument(
    public val url: String,
    public val access_hash: Long,
    public val size: Int,
    public val mime_type: String,
    public val attributes: List<TlGen_DocumentAttribute>,
  ) : TlGen_WebDocument() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(url)
      stream.writeInt64(access_hash)
      stream.writeInt32(size)
      stream.writeString(mime_type)
      TlGen_Vector.serialize(stream, attributes)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1C570ED1U
    }
  }

  public data class TL_webDocument_layer81(
    public val url: String,
    public val access_hash: Long,
    public val size: Int,
    public val mime_type: String,
    public val attributes: List<TlGen_DocumentAttribute>,
    public val dc_id: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(url)
      stream.writeInt64(access_hash)
      stream.writeInt32(size)
      stream.writeString(mime_type)
      TlGen_Vector.serialize(stream, attributes)
      stream.writeInt32(dc_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC61ACBD8U
    }
  }
}

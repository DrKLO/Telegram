package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_Page : TlGen_Object {
  public data class TL_page(
    public val part: Boolean,
    public val rtl: Boolean,
    public val v2: Boolean,
    public val url: String,
    public val blocks: List<TlGen_PageBlock>,
    public val photos: List<TlGen_Photo>,
    public val documents: List<TlGen_Document>,
    public val views: Int?,
  ) : TlGen_Page() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (part) result = result or 1U
        if (rtl) result = result or 2U
        if (v2) result = result or 4U
        if (views != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(url)
      TlGen_Vector.serialize(stream, blocks)
      TlGen_Vector.serialize(stream, photos)
      TlGen_Vector.serialize(stream, documents)
      views?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x98657F0DU
    }
  }

  public data class TL_pagePart_layer67(
    public val blocks: List<TlGen_PageBlock>,
    public val photos: List<TlGen_Photo>,
    public val videos: List<TlGen_Document>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, blocks)
      TlGen_Vector.serialize(stream, photos)
      TlGen_Vector.serialize(stream, videos)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8DEE6C44U
    }
  }

  public data class TL_pageFull_layer67(
    public val blocks: List<TlGen_PageBlock>,
    public val photos: List<TlGen_Photo>,
    public val videos: List<TlGen_Document>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, blocks)
      TlGen_Vector.serialize(stream, photos)
      TlGen_Vector.serialize(stream, videos)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD7A19D69U
    }
  }

  public data class TL_pagePart_layer87(
    public val blocks: List<TlGen_PageBlock>,
    public val photos: List<TlGen_Photo>,
    public val documents: List<TlGen_Document>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, blocks)
      TlGen_Vector.serialize(stream, photos)
      TlGen_Vector.serialize(stream, documents)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8E3F9EBEU
    }
  }

  public data class TL_pageFull_layer87(
    public val blocks: List<TlGen_PageBlock>,
    public val photos: List<TlGen_Photo>,
    public val documents: List<TlGen_Document>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, blocks)
      TlGen_Vector.serialize(stream, photos)
      TlGen_Vector.serialize(stream, documents)
    }

    public companion object {
      public const val MAGIC: UInt = 0x556EC7AAU
    }
  }

  public data class TL_page_layer88(
    public val part: Boolean,
    public val rtl: Boolean,
    public val v2: Boolean,
    public val blocks: List<TlGen_PageBlock>,
    public val photos: List<TlGen_Photo>,
    public val documents: List<TlGen_Document>,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (part) result = result or 1U
        if (rtl) result = result or 2U
        if (v2) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serialize(stream, blocks)
      TlGen_Vector.serialize(stream, photos)
      TlGen_Vector.serialize(stream, documents)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF199A0A8U
    }
  }

  public data class TL_page_layer110(
    public val part: Boolean,
    public val rtl: Boolean,
    public val v2: Boolean,
    public val url: String,
    public val blocks: List<TlGen_PageBlock>,
    public val photos: List<TlGen_Photo>,
    public val documents: List<TlGen_Document>,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (part) result = result or 1U
        if (rtl) result = result or 2U
        if (v2) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(url)
      TlGen_Vector.serialize(stream, blocks)
      TlGen_Vector.serialize(stream, photos)
      TlGen_Vector.serialize(stream, documents)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAE891BECU
    }
  }
}

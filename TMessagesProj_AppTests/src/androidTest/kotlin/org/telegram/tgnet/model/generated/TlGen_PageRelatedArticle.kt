package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PageRelatedArticle : TlGen_Object {
  public data class TL_pageRelatedArticle(
    public val url: String,
    public val webpage_id: Long,
    public val title: String?,
    public val description: String?,
    public val photo_id: Long?,
    public val author: String?,
    public val published_date: Int?,
  ) : TlGen_PageRelatedArticle() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (title != null) result = result or 1U
        if (description != null) result = result or 2U
        if (photo_id != null) result = result or 4U
        if (author != null) result = result or 8U
        if (published_date != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(url)
      stream.writeInt64(webpage_id)
      title?.let { stream.writeString(it) }
      description?.let { stream.writeString(it) }
      photo_id?.let { stream.writeInt64(it) }
      author?.let { stream.writeString(it) }
      published_date?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xB390DC08U
    }
  }

  public data class TL_pageRelatedArticle_layer88(
    public val url: String,
    public val webpage_id: Long,
    public val title: String?,
    public val description: String?,
    public val photo_id: Long?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (title != null) result = result or 1U
        if (description != null) result = result or 2U
        if (photo_id != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(url)
      stream.writeInt64(webpage_id)
      title?.let { stream.writeString(it) }
      description?.let { stream.writeString(it) }
      photo_id?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xF186F93CU
    }
  }
}

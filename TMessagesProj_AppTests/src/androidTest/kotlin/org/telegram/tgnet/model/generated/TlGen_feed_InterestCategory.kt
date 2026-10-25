package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_feed_InterestCategory : TlGen_Object {
  public data class TL_feed_interestCategory(
    public val id: Int,
    public val title: String,
    public val emoji: String?,
    public val emoji_document_id: Long?,
  ) : TlGen_feed_InterestCategory() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (emoji != null) result = result or 1U
        if (emoji_document_id != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(title)
      emoji?.let { stream.writeString(it) }
      emoji_document_id?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x6C4666E3U
    }
  }
}

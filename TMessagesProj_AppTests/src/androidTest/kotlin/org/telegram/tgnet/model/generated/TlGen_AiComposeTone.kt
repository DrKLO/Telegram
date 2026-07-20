package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_AiComposeTone : TlGen_Object {
  public data class TL_aiComposeTone(
    public val creator: Boolean,
    public val id: Long,
    public val access_hash: Long,
    public val slug: String,
    public val title: String,
    public val emoji_id: Long?,
    public val prompt: String?,
    public val installs_count: Int?,
    public val author_id: Long?,
    public val example_english: TlGen_AiComposeToneExample?,
  ) : TlGen_AiComposeTone() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (emoji_id != null) result = result or 2U
        if (installs_count != null) result = result or 4U
        if (author_id != null) result = result or 8U
        if (prompt != null) result = result or 16U
        if (example_english != null) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeString(slug)
      stream.writeString(title)
      emoji_id?.let { stream.writeInt64(it) }
      prompt?.let { stream.writeString(it) }
      installs_count?.let { stream.writeInt32(it) }
      author_id?.let { stream.writeInt64(it) }
      example_english?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCFF63EA9U
    }
  }

  public data class TL_aiComposeToneDefault(
    public val tone: String,
    public val emoji_id: Long,
    public val title: String,
  ) : TlGen_AiComposeTone() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(tone)
      stream.writeInt64(emoji_id)
      stream.writeString(title)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9BAD6414U
    }
  }
}

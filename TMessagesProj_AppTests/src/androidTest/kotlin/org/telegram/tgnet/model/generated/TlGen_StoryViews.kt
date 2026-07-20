package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StoryViews : TlGen_Object {
  public data class TL_storyViews(
    public val has_viewers: Boolean,
    public val views_count: Int,
    public val forwards_count: Int?,
    public val reactions: List<TlGen_ReactionCount>?,
    public val reactions_count: Int?,
    public val recent_viewers: List<Long>?,
  ) : TlGen_StoryViews() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (recent_viewers != null) result = result or 1U
        if (has_viewers) result = result or 2U
        if (forwards_count != null) result = result or 4U
        if (reactions != null) result = result or 8U
        if (reactions_count != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(views_count)
      forwards_count?.let { stream.writeInt32(it) }
      reactions?.let { TlGen_Vector.serialize(stream, it) }
      reactions_count?.let { stream.writeInt32(it) }
      recent_viewers?.let { TlGen_Vector.serializeLong(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x8D595CD6U
    }
  }

  public data class TL_storyViews_layer160(
    public val views_count: Int,
    public val recent_viewers: List<Long>?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (recent_viewers != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(views_count)
      recent_viewers?.let { TlGen_Vector.serializeLong(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xD36760CFU
    }
  }

  public data class TL_storyViews_layer163(
    public val has_viewers: Boolean,
    public val views_count: Int,
    public val reactions_count: Int,
    public val recent_viewers: List<Long>?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (recent_viewers != null) result = result or 1U
        if (has_viewers) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(views_count)
      stream.writeInt32(reactions_count)
      recent_viewers?.let { TlGen_Vector.serializeLong(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xC64C0B97U
    }
  }
}

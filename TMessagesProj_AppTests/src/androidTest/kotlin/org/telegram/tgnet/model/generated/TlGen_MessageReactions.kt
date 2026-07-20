package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_MessageReactions : TlGen_Object {
  public data class TL_messageReactions(
    public val min: Boolean,
    public val can_see_list: Boolean,
    public val reactions_as_tags: Boolean,
    public val results: List<TlGen_ReactionCount>,
    public val recent_reactions: List<TlGen_MessagePeerReaction>?,
    public val top_reactors: List<TlGen_MessageReactor>?,
  ) : TlGen_MessageReactions() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (min) result = result or 1U
        if (recent_reactions != null) result = result or 2U
        if (can_see_list) result = result or 4U
        if (reactions_as_tags) result = result or 8U
        if (top_reactors != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serialize(stream, results)
      recent_reactions?.let { TlGen_Vector.serialize(stream, it) }
      top_reactors?.let { TlGen_Vector.serialize(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x0A339F0BU
    }
  }

  public data class TL_messageReactions_layer137(
    public val min: Boolean,
    public val can_see_list: Boolean,
    public val results: List<TlGen_ReactionCount>,
    public val recent_reactons: List<TlGen_MessageUserReaction>?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (min) result = result or 1U
        if (recent_reactons != null) result = result or 2U
        if (can_see_list) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serialize(stream, results)
      recent_reactons?.let { TlGen_Vector.serialize(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x087B6E36U
    }
  }

  public data class TL_messageReactions_layer185(
    public val min: Boolean,
    public val can_see_list: Boolean,
    public val reactions_as_tags: Boolean,
    public val results: List<TlGen_ReactionCount>,
    public val recent_reactions: List<TlGen_MessagePeerReaction>?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (min) result = result or 1U
        if (recent_reactions != null) result = result or 2U
        if (can_see_list) result = result or 4U
        if (reactions_as_tags) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serialize(stream, results)
      recent_reactions?.let { TlGen_Vector.serialize(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x4F2B9479U
    }
  }
}

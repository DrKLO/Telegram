package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ReactionCount : TlGen_Object {
  public data class TL_reactionCount(
    public val chosen_order: Int?,
    public val reaction: TlGen_Reaction,
    public val count: Int,
  ) : TlGen_ReactionCount() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (chosen_order != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      chosen_order?.let { stream.writeInt32(it) }
      reaction.serializeToStream(stream)
      stream.writeInt32(count)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA3D1CB80U
    }
  }

  public data class TL_reactionCount_layer144(
    public val chosen: Boolean,
    public val reaction: String,
    public val count: Int,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (chosen) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(reaction)
      stream.writeInt32(count)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6FB250D1U
    }
  }
}

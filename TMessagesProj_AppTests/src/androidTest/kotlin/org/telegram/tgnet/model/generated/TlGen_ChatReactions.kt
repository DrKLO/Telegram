package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ChatReactions : TlGen_Object {
  public data object TL_chatReactionsNone : TlGen_ChatReactions() {
    public const val MAGIC: UInt = 0xEAFC32BCU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_chatReactionsAll(
    public val allow_custom: Boolean,
  ) : TlGen_ChatReactions() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (allow_custom) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
    }

    public companion object {
      public const val MAGIC: UInt = 0x52928BCAU
    }
  }

  public data class TL_chatReactionsSome(
    public val reactions: List<TlGen_Reaction>,
  ) : TlGen_ChatReactions() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, reactions)
    }

    public companion object {
      public const val MAGIC: UInt = 0x661D4037U
    }
  }
}

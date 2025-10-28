package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_Reactions : TlGen_Object {
  public data object TL_messages_reactionsNotModified : TlGen_messages_Reactions() {
    public const val MAGIC: UInt = 0xB06FDBDFU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_messages_reactions(
    public val hash: Long,
    public val reactions: List<TlGen_Reaction>,
  ) : TlGen_messages_Reactions() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(hash)
      TlGen_Vector.serialize(stream, reactions)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEAFDF716U
    }
  }
}

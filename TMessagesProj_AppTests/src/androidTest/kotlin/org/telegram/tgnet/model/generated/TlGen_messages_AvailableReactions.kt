package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_AvailableReactions : TlGen_Object {
  public data object TL_messages_availableReactionsNotModified : TlGen_messages_AvailableReactions()
      {
    public const val MAGIC: UInt = 0x9F071957U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_messages_availableReactions(
    public val hash: Int,
    public val reactions: List<TlGen_AvailableReaction>,
  ) : TlGen_messages_AvailableReactions() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(hash)
      TlGen_Vector.serialize(stream, reactions)
    }

    public companion object {
      public const val MAGIC: UInt = 0x768E3AADU
    }
  }
}

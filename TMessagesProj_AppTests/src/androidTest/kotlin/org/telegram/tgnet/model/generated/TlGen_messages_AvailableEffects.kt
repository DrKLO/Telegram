package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_AvailableEffects : TlGen_Object {
  public data object TL_messages_availableEffectsNotModified : TlGen_messages_AvailableEffects() {
    public const val MAGIC: UInt = 0xD1ED9A5BU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_messages_availableEffects(
    public val hash: Int,
    public val effects: List<TlGen_AvailableEffect>,
    public val documents: List<TlGen_Document>,
  ) : TlGen_messages_AvailableEffects() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(hash)
      TlGen_Vector.serialize(stream, effects)
      TlGen_Vector.serialize(stream, documents)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBDDB616EU
    }
  }
}

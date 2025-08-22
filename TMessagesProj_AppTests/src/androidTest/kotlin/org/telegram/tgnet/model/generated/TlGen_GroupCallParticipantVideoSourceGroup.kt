package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_GroupCallParticipantVideoSourceGroup : TlGen_Object {
  public data class TL_groupCallParticipantVideoSourceGroup(
    public val semantics: String,
    public val sources: List<Int>,
  ) : TlGen_GroupCallParticipantVideoSourceGroup() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(semantics)
      TlGen_Vector.serializeInt(stream, sources)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDCB118B7U
    }
  }
}

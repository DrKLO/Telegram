package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_AffectedFoundMessages : TlGen_Object {
  public data class TL_messages_affectedFoundMessages(
    public val pts: Int,
    public val pts_count: Int,
    public val offset: Int,
    public val messages: List<Int>,
  ) : TlGen_messages_AffectedFoundMessages() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(pts)
      stream.writeInt32(pts_count)
      stream.writeInt32(offset)
      TlGen_Vector.serializeInt(stream, messages)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEF8D3E6CU
    }
  }
}

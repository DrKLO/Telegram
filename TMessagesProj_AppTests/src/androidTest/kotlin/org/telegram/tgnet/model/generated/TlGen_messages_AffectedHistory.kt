package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_AffectedHistory : TlGen_Object {
  public data class TL_messages_affectedHistory(
    public val pts: Int,
    public val pts_count: Int,
    public val offset: Int,
  ) : TlGen_messages_AffectedHistory() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(pts)
      stream.writeInt32(pts_count)
      stream.writeInt32(offset)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB45C69D1U
    }
  }
}

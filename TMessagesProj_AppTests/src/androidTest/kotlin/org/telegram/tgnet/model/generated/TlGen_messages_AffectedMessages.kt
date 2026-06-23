package org.Tajgram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_AffectedMessages : TlGen_Object {
  public data class TL_messages_affectedMessages(
    public val pts: Int,
    public val pts_count: Int,
  ) : TlGen_messages_AffectedMessages() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(pts)
      stream.writeInt32(pts_count)
    }

    public companion object {
      public const val MAGIC: UInt = 0x84D19185U
    }
  }
}

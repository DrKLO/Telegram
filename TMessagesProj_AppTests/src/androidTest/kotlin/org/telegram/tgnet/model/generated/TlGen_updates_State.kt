package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_updates_State : TlGen_Object {
  public data class TL_updates_state(
    public val pts: Int,
    public val qts: Int,
    public val date: Int,
    public val seq: Int,
    public val unread_count: Int,
  ) : TlGen_updates_State() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(pts)
      stream.writeInt32(qts)
      stream.writeInt32(date)
      stream.writeInt32(seq)
      stream.writeInt32(unread_count)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA56C2A3EU
    }
  }
}

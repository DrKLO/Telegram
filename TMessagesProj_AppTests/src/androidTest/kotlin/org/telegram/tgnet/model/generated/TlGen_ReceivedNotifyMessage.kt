package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ReceivedNotifyMessage : TlGen_Object {
  public data class TL_receivedNotifyMessage(
    public val id: Int,
    public val flags: Int,
  ) : TlGen_ReceivedNotifyMessage() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeInt32(flags)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA384B779U
    }
  }
}

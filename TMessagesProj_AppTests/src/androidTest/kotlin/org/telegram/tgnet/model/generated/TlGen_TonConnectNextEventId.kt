package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_TonConnectNextEventId : TlGen_Object {
  public data class TL_tonConnectNextEventId(
    public val event_id: Long,
  ) : TlGen_TonConnectNextEventId() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(event_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x582464E3U
    }
  }
}

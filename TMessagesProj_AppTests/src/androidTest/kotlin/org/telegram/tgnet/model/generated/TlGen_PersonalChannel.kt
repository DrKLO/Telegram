package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PersonalChannel : TlGen_Object {
  public data class TL_personalChannel(
    public val user_id: Long,
    public val channel_id: Long,
  ) : TlGen_PersonalChannel() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(user_id)
      stream.writeInt64(channel_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x19BC407DU
    }
  }
}

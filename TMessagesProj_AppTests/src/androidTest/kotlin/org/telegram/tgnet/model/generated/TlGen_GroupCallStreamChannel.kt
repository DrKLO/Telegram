package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_GroupCallStreamChannel : TlGen_Object {
  public data class TL_groupCallStreamChannel(
    public val channel: Int,
    public val scale: Int,
    public val last_timestamp_ms: Long,
  ) : TlGen_GroupCallStreamChannel() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(channel)
      stream.writeInt32(scale)
      stream.writeInt64(last_timestamp_ms)
    }

    public companion object {
      public const val MAGIC: UInt = 0x80EB48AFU
    }
  }
}

package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StarsAmount : TlGen_Object {
  public data class TL_starsAmount(
    public val amount: Long,
    public val nanos: Int,
  ) : TlGen_StarsAmount() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(amount)
      stream.writeInt32(nanos)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBBB6B4A3U
    }
  }

  public data class TL_starsTonAmount(
    public val amount: Long,
  ) : TlGen_StarsAmount() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(amount)
    }

    public companion object {
      public const val MAGIC: UInt = 0x74AEE3E0U
    }
  }
}

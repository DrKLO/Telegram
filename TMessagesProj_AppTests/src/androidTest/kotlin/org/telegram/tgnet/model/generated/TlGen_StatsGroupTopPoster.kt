package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StatsGroupTopPoster : TlGen_Object {
  public data class TL_statsGroupTopPoster(
    public val user_id: Long,
    public val messages: Int,
    public val avg_chars: Int,
  ) : TlGen_StatsGroupTopPoster() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(user_id)
      stream.writeInt32(messages)
      stream.writeInt32(avg_chars)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9D04AF9BU
    }
  }
}

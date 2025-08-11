package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StatsGroupTopAdmin : TlGen_Object {
  public data class TL_statsGroupTopAdmin(
    public val user_id: Long,
    public val deleted: Int,
    public val kicked: Int,
    public val banned: Int,
  ) : TlGen_StatsGroupTopAdmin() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(user_id)
      stream.writeInt32(deleted)
      stream.writeInt32(kicked)
      stream.writeInt32(banned)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD7584C87U
    }
  }
}

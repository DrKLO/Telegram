package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ChannelAdminLogEvent : TlGen_Object {
  public data class TL_channelAdminLogEvent(
    public val id: Long,
    public val date: Int,
    public val user_id: Long,
    public val action: TlGen_ChannelAdminLogEventAction,
  ) : TlGen_ChannelAdminLogEvent() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt32(date)
      stream.writeInt64(user_id)
      action.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1FAD68CDU
    }
  }
}

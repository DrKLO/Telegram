package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ChannelCategory : TlGen_Object {
  public data class TL_channelCategory(
    public val id: Int,
    public val title: String,
  ) : TlGen_ChannelCategory() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeString(title)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0C484F32U
    }
  }
}

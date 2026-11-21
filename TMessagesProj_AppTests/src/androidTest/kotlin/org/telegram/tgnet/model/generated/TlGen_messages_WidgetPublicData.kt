package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_WidgetPublicData : TlGen_Object {
  public data class TL_messages_widgetPublicDataNotModified(
    public val period: Int,
  ) : TlGen_messages_WidgetPublicData() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(period)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB9196E57U
    }
  }

  public data class TL_messages_widgetPublicData(
    public val hash: Long,
    public val `data`: String,
    public val period: Int,
  ) : TlGen_messages_WidgetPublicData() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(hash)
      stream.writeString(data)
      stream.writeInt32(period)
    }

    public companion object {
      public const val MAGIC: UInt = 0x04493BFFU
    }
  }
}

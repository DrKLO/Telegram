package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ChannelLocation : TlGen_Object {
  public data object TL_channelLocationEmpty : TlGen_ChannelLocation() {
    public const val MAGIC: UInt = 0xBFB5AD8BU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_channelLocation(
    public val geo_point: TlGen_GeoPoint,
    public val address: String,
  ) : TlGen_ChannelLocation() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      geo_point.serializeToStream(stream)
      stream.writeString(address)
    }

    public companion object {
      public const val MAGIC: UInt = 0x209B82DBU
    }
  }
}

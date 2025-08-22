package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputWebFileLocation : TlGen_Object {
  public data class TL_inputWebFileLocation(
    public val url: String,
    public val access_hash: Long,
  ) : TlGen_InputWebFileLocation() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(url)
      stream.writeInt64(access_hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC239D686U
    }
  }

  public data class TL_inputWebFileGeoPointLocation(
    public val geo_point: TlGen_InputGeoPoint,
    public val access_hash: Long,
    public val w: Int,
    public val h: Int,
    public val zoom: Int,
    public val scale: Int,
  ) : TlGen_InputWebFileLocation() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      geo_point.serializeToStream(stream)
      stream.writeInt64(access_hash)
      stream.writeInt32(w)
      stream.writeInt32(h)
      stream.writeInt32(zoom)
      stream.writeInt32(scale)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9F2221C9U
    }
  }
}

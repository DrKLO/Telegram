package org.telegram.tgnet.model.generated

import kotlin.Double
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_MediaAreaCoordinates : TlGen_Object {
  public data class TL_mediaAreaCoordinates(
    public val x: Double,
    public val y: Double,
    public val w: Double,
    public val h: Double,
    public val rotation: Double,
    public val radius: Double?,
  ) : TlGen_MediaAreaCoordinates() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (radius != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeDouble(x)
      stream.writeDouble(y)
      stream.writeDouble(w)
      stream.writeDouble(h)
      stream.writeDouble(rotation)
      radius?.let { stream.writeDouble(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xCFC9E002U
    }
  }

  public data class TL_mediaAreaCoordinates_layer181(
    public val x: Double,
    public val y: Double,
    public val w: Double,
    public val h: Double,
    public val rotation: Double,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeDouble(x)
      stream.writeDouble(y)
      stream.writeDouble(w)
      stream.writeDouble(h)
      stream.writeDouble(rotation)
    }

    public companion object {
      public const val MAGIC: UInt = 0x03D1EA4EU
    }
  }
}

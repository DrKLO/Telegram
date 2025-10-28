package org.telegram.tgnet.model.generated

import kotlin.Double
import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputGeoPoint : TlGen_Object {
  public data object TL_inputGeoPointEmpty : TlGen_InputGeoPoint() {
    public const val MAGIC: UInt = 0xE4C123D6U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_inputGeoPoint(
    public val lat: Double,
    public val long: Double,
    public val accuracy_radius: Int?,
  ) : TlGen_InputGeoPoint() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (accuracy_radius != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeDouble(lat)
      stream.writeDouble(long)
      accuracy_radius?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x48222FAFU
    }
  }
}

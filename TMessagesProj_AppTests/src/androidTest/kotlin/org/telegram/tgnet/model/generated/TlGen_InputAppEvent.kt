package org.Tajgram.tgnet.model.generated

import kotlin.Double
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputAppEvent : TlGen_Object {
  public data class TL_inputAppEvent(
    public val time: Double,
    public val type: String,
    public val peer: Long,
    public val `data`: TlGen_JSONValue,
  ) : TlGen_InputAppEvent() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeDouble(time)
      stream.writeString(type)
      stream.writeInt64(peer)
      data.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1D1B1245U
    }
  }
}

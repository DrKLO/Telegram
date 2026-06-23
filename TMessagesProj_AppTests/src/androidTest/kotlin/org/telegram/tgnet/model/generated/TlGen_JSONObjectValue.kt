package org.Tajgram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_JSONObjectValue : TlGen_Object {
  public data class TL_jsonObjectValue(
    public val key: String,
    public val `value`: TlGen_JSONValue,
  ) : TlGen_JSONObjectValue() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(key)
      value.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC0DE1BD9U
    }
  }
}

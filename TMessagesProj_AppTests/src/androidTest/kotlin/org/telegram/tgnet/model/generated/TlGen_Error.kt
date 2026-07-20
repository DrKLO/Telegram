package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_Error : TlGen_Object {
  public data class TL_error(
    public val code: Int,
    public val text: String,
  ) : TlGen_Error() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(code)
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC4B9F9BBU
    }
  }
}

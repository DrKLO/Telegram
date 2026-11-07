package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_toncenter_ApiResponse : TlGen_Object {
  public data class TL_toncenter_apiResponse(
    public val response: TlGen_DataJSON,
  ) : TlGen_toncenter_ApiResponse() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      response.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAC8DFE19U
    }
  }
}

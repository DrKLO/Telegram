package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_OnrampSession : TlGen_Object {
  public data class TL_onrampSession(
    public val provider: String,
    public val session_id: String,
    public val url: String,
    public val expires_date: Int,
  ) : TlGen_OnrampSession() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(provider)
      stream.writeString(session_id)
      stream.writeString(url)
      stream.writeInt32(expires_date)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD1E42E24U
    }
  }
}

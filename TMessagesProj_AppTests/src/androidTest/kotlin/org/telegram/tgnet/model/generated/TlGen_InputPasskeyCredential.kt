package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputPasskeyCredential : TlGen_Object {
  public data class TL_inputPasskeyCredentialPublicKey(
    public val id: String,
    public val raw_id: String,
    public val response: TlGen_InputPasskeyResponse,
  ) : TlGen_InputPasskeyCredential() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(id)
      stream.writeString(raw_id)
      response.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3C27B78FU
    }
  }
}

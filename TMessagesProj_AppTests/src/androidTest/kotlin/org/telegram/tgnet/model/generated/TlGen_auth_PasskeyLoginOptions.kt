package org.Tajgram.tgnet.model.generated

import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_auth_PasskeyLoginOptions : TlGen_Object {
  public data class TL_auth_passkeyLoginOptions(
    public val options: TlGen_DataJSON,
  ) : TlGen_auth_PasskeyLoginOptions() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      options.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE2037789U
    }
  }
}

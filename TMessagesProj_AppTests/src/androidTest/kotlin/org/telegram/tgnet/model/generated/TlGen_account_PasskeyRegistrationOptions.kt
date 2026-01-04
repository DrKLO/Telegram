package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_account_PasskeyRegistrationOptions : TlGen_Object {
  public data class TL_account_passkeyRegistrationOptions(
    public val options: TlGen_DataJSON,
  ) : TlGen_account_PasskeyRegistrationOptions() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      options.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE16B5CE1U
    }
  }
}

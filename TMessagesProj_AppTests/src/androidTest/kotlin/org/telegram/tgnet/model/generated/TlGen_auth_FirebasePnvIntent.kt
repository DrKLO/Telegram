package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_auth_FirebasePnvIntent : TlGen_Object {
  public data class TL_auth_firebasePnvIntent(
    public val nonce: String,
    public val digital_credential_payload: String,
  ) : TlGen_auth_FirebasePnvIntent() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(nonce)
      stream.writeString(digital_credential_payload)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDF5AC00CU
    }
  }
}

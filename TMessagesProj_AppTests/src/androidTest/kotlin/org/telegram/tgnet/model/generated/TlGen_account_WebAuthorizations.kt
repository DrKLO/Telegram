package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_account_WebAuthorizations : TlGen_Object {
  public data class TL_account_webAuthorizations(
    public val authorizations: List<TlGen_WebAuthorization>,
    public val users: List<TlGen_User>,
  ) : TlGen_account_WebAuthorizations() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, authorizations)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0xED56C9FCU
    }
  }
}

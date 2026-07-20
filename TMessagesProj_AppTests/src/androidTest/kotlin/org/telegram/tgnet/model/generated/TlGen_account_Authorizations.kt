package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_account_Authorizations : TlGen_Object {
  public data class TL_account_authorizations(
    public val authorization_ttl_days: Int,
    public val authorizations: List<TlGen_Authorization>,
  ) : TlGen_account_Authorizations() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(authorization_ttl_days)
      TlGen_Vector.serialize(stream, authorizations)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4BFF8EA0U
    }
  }
}

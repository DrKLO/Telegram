package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_account_EmailVerified : TlGen_Object {
  public data class TL_account_emailVerified(
    public val email: String,
  ) : TlGen_account_EmailVerified() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(email)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2B96CD1BU
    }
  }

  public data class TL_account_emailVerifiedLogin(
    public val email: String,
    public val sent_code: TlGen_auth_SentCode,
  ) : TlGen_account_EmailVerified() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(email)
      sent_code.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE1BB0D61U
    }
  }
}

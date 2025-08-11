package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_auth_LoginToken : TlGen_Object {
  public data class TL_auth_loginToken(
    public val expires: Int,
    public val token: List<Byte>,
  ) : TlGen_auth_LoginToken() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(expires)
      stream.writeByteArray(token.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x629F1980U
    }
  }

  public data class TL_auth_loginTokenMigrateTo(
    public val dc_id: Int,
    public val token: List<Byte>,
  ) : TlGen_auth_LoginToken() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(dc_id)
      stream.writeByteArray(token.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x068E9916U
    }
  }

  public data class TL_auth_loginTokenSuccess(
    public val authorization: TlGen_auth_Authorization,
  ) : TlGen_auth_LoginToken() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      authorization.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x390D5C5EU
    }
  }
}

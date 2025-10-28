package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_EmailVerification : TlGen_Object {
  public data class TL_emailVerificationCode(
    public val code: String,
  ) : TlGen_EmailVerification() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(code)
    }

    public companion object {
      public const val MAGIC: UInt = 0x922E55A9U
    }
  }

  public data class TL_emailVerificationGoogle(
    public val token: String,
  ) : TlGen_EmailVerification() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(token)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDB909EC2U
    }
  }

  public data class TL_emailVerificationApple(
    public val token: String,
  ) : TlGen_EmailVerification() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(token)
    }

    public companion object {
      public const val MAGIC: UInt = 0x96D074FDU
    }
  }
}

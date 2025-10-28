package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_EmailVerifyPurpose : TlGen_Object {
  public data class TL_emailVerifyPurposeLoginSetup(
    public val phone_number: String,
    public val phone_code_hash: String,
  ) : TlGen_EmailVerifyPurpose() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(phone_number)
      stream.writeString(phone_code_hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4345BE73U
    }
  }

  public data object TL_emailVerifyPurposeLoginChange : TlGen_EmailVerifyPurpose() {
    public const val MAGIC: UInt = 0x527D22EBU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_emailVerifyPurposePassport : TlGen_EmailVerifyPurpose() {
    public const val MAGIC: UInt = 0xBBF51685U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}

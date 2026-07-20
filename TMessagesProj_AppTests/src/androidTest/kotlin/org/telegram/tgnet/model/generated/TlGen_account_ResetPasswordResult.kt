package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_account_ResetPasswordResult : TlGen_Object {
  public data class TL_account_resetPasswordFailedWait(
    public val retry_date: Int,
  ) : TlGen_account_ResetPasswordResult() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(retry_date)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE3779861U
    }
  }

  public data class TL_account_resetPasswordRequestedWait(
    public val until_date: Int,
  ) : TlGen_account_ResetPasswordResult() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(until_date)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE9EFFC7DU
    }
  }

  public data object TL_account_resetPasswordOk : TlGen_account_ResetPasswordResult() {
    public const val MAGIC: UInt = 0xE926D63EU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}

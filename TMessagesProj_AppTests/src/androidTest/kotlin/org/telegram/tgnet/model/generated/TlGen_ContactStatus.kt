package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ContactStatus : TlGen_Object {
  public data class TL_contactStatus(
    public val user_id: Long,
    public val status: TlGen_UserStatus,
  ) : TlGen_ContactStatus() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(user_id)
      status.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x16D9703BU
    }
  }
}

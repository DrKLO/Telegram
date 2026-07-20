package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_payments_CheckCanSendGiftResult : TlGen_Object {
  public data object TL_payments_checkCanSendGiftResultOk : TlGen_payments_CheckCanSendGiftResult()
      {
    public const val MAGIC: UInt = 0x374FA7ADU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_payments_checkCanSendGiftResultFail(
    public val reason: TlGen_TextWithEntities,
  ) : TlGen_payments_CheckCanSendGiftResult() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      reason.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD5E58274U
    }
  }
}

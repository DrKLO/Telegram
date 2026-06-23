package org.Tajgram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_account_PaidMessagesRevenue : TlGen_Object {
  public data class TL_account_paidMessagesRevenue(
    public val stars_amount: Long,
  ) : TlGen_account_PaidMessagesRevenue() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(stars_amount)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1E109708U
    }
  }
}

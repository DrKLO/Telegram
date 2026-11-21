package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_payments_StarGiftDepositPayload : TlGen_Object {
  public data class TL_payments_starGiftDepositPayload(
    public val nft_address: String,
    public val payload: List<Byte>,
    public val amount: Long,
  ) : TlGen_payments_StarGiftDepositPayload() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(nft_address)
      stream.writeByteArray(payload.toByteArray())
      stream.writeInt64(amount)
    }

    public companion object {
      public const val MAGIC: UInt = 0x725BB4EBU
    }
  }
}

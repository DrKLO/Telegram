package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_payments_BankCardData : TlGen_Object {
  public data class TL_payments_bankCardData(
    public val title: String,
    public val open_urls: List<TlGen_BankCardOpenUrl>,
  ) : TlGen_payments_BankCardData() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(title)
      TlGen_Vector.serialize(stream, open_urls)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3E24E573U
    }
  }
}

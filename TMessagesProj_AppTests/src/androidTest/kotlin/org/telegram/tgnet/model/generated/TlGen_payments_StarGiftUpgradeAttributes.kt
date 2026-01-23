package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_payments_StarGiftUpgradeAttributes : TlGen_Object {
  public data class TL_payments_starGiftUpgradeAttributes(
    public val attributes: List<TlGen_StarGiftAttribute>,
  ) : TlGen_payments_StarGiftUpgradeAttributes() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, attributes)
    }

    public companion object {
      public const val MAGIC: UInt = 0x46C6E36FU
    }
  }
}

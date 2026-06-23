package org.Tajgram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_payments_StarGiftUpgradePreview : TlGen_Object {
  public data class TL_payments_starGiftUpgradePreview(
    public val sample_attributes: List<TlGen_StarGiftAttribute>,
    public val prices: List<TlGen_StarGiftUpgradePrice>,
    public val next_prices: List<TlGen_StarGiftUpgradePrice>,
  ) : TlGen_payments_StarGiftUpgradePreview() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, sample_attributes)
      TlGen_Vector.serialize(stream, prices)
      TlGen_Vector.serialize(stream, next_prices)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3DE1DFEDU
    }
  }
}

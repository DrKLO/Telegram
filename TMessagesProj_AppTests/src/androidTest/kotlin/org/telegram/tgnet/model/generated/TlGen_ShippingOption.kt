package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ShippingOption : TlGen_Object {
  public data class TL_shippingOption(
    public val id: String,
    public val title: String,
    public val prices: List<TlGen_LabeledPrice>,
  ) : TlGen_ShippingOption() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(id)
      stream.writeString(title)
      TlGen_Vector.serialize(stream, prices)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB6213CDFU
    }
  }
}

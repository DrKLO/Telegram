package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_LabeledPrice : TlGen_Object {
  public data class TL_labeledPrice(
    public val label: String,
    public val amount: Long,
  ) : TlGen_LabeledPrice() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(label)
      stream.writeInt64(amount)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCB296BF8U
    }
  }
}

package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PageListOrderedItem : TlGen_Object {
  public data class TL_pageListOrderedItemText(
    public val num: String,
    public val text: TlGen_RichText,
  ) : TlGen_PageListOrderedItem() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(num)
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5E068047U
    }
  }

  public data class TL_pageListOrderedItemBlocks(
    public val num: String,
    public val blocks: List<TlGen_PageBlock>,
  ) : TlGen_PageListOrderedItem() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(num)
      TlGen_Vector.serialize(stream, blocks)
    }

    public companion object {
      public const val MAGIC: UInt = 0x98DD8936U
    }
  }
}

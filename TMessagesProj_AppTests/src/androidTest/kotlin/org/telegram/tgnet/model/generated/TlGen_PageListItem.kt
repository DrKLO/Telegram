package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PageListItem : TlGen_Object {
  public data class TL_pageListItemText(
    public val text: TlGen_RichText,
  ) : TlGen_PageListItem() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB92FB6CDU
    }
  }

  public data class TL_pageListItemBlocks(
    public val blocks: List<TlGen_PageBlock>,
  ) : TlGen_PageListItem() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, blocks)
    }

    public companion object {
      public const val MAGIC: UInt = 0x25E073FCU
    }
  }
}

package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PageTableRow : TlGen_Object {
  public data class TL_pageTableRow(
    public val cells: List<TlGen_PageTableCell>,
  ) : TlGen_PageTableRow() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, cells)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE0C0C5E5U
    }
  }
}

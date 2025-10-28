package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PageTableCell : TlGen_Object {
  public data class TL_pageTableCell(
    public val `header`: Boolean,
    public val align_center: Boolean,
    public val align_right: Boolean,
    public val valign_middle: Boolean,
    public val valign_bottom: Boolean,
    public val text: TlGen_RichText?,
    public val colspan: Int?,
    public val rowspan: Int?,
  ) : TlGen_PageTableCell() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (header) result = result or 1U
        if (colspan != null) result = result or 2U
        if (rowspan != null) result = result or 4U
        if (align_center) result = result or 8U
        if (align_right) result = result or 16U
        if (valign_middle) result = result or 32U
        if (valign_bottom) result = result or 64U
        if (text != null) result = result or 128U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      text?.serializeToStream(stream)
      colspan?.let { stream.writeInt32(it) }
      rowspan?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x34566B6AU
    }
  }
}

package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PageButton : TlGen_Object {
  public data class TL_pageButton(
    public val text: TlGen_RichText,
    public val type: TlGen_InlineButtonType,
    public val style: TlGen_RichButtonStyle?,
  ) : TlGen_PageButton() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (style != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      text.serializeToStream(stream)
      type.serializeToStream(stream)
      style?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x692A5488U
    }
  }
}

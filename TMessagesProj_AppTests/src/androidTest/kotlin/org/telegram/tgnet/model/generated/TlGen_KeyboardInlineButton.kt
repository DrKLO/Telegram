package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_KeyboardInlineButton : TlGen_Object {
  public data class TL_keyboardInlineButton(
    public val style: TlGen_KeyboardButtonStyle?,
    public val text: String,
    public val type: TlGen_InlineButtonType,
  ) : TlGen_KeyboardInlineButton() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (style != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      style?.serializeToStream(stream)
      stream.writeString(text)
      type.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x11C1A322U
    }
  }
}

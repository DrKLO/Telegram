package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_KeyboardButtonStyle : TlGen_Object {
  public data class TL_keyboardButtonStyle(
    public val bg_primary: Boolean,
    public val bg_danger: Boolean,
    public val bg_success: Boolean,
    public val icon: Long?,
  ) : TlGen_KeyboardButtonStyle() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (bg_primary) result = result or 1U
        if (bg_danger) result = result or 2U
        if (bg_success) result = result or 4U
        if (icon != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      icon?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x4FDD3430U
    }
  }
}

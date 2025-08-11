package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_AttachMenuBotIcon : TlGen_Object {
  public data class TL_attachMenuBotIcon(
    public val name: String,
    public val icon: TlGen_Document,
    public val colors: List<TlGen_AttachMenuBotIconColor>?,
  ) : TlGen_AttachMenuBotIcon() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (colors != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(name)
      icon.serializeToStream(stream)
      colors?.let { TlGen_Vector.serialize(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xB2A7386BU
    }
  }
}

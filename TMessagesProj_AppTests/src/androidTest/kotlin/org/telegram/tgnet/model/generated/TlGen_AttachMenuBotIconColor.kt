package org.Tajgram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_AttachMenuBotIconColor : TlGen_Object {
  public data class TL_attachMenuBotIconColor(
    public val name: String,
    public val color: Int,
  ) : TlGen_AttachMenuBotIconColor() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(name)
      stream.writeInt32(color)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4576F3F0U
    }
  }
}

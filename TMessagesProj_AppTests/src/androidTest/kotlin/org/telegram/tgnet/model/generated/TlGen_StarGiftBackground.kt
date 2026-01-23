package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StarGiftBackground : TlGen_Object {
  public data class TL_starGiftBackground(
    public val center_color: Int,
    public val edge_color: Int,
    public val text_color: Int,
  ) : TlGen_StarGiftBackground() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(center_color)
      stream.writeInt32(edge_color)
      stream.writeInt32(text_color)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAFF56398U
    }
  }
}

package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_ArchivedStickers : TlGen_Object {
  public data class TL_messages_archivedStickers(
    public val count: Int,
    public val sets: List<TlGen_StickerSetCovered>,
  ) : TlGen_messages_ArchivedStickers() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(count)
      TlGen_Vector.serialize(stream, sets)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4FCBA9C8U
    }
  }
}

package org.Tajgram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_MyStickers : TlGen_Object {
  public data class TL_messages_myStickers(
    public val count: Int,
    public val sets: List<TlGen_StickerSetCovered>,
  ) : TlGen_messages_MyStickers() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(count)
      TlGen_Vector.serialize(stream, sets)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFAFF629DU
    }
  }
}

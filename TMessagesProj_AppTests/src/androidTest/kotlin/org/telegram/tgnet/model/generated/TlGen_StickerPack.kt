package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StickerPack : TlGen_Object {
  public data class TL_stickerPack(
    public val emoticon: String,
    public val documents: List<Long>,
  ) : TlGen_StickerPack() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(emoticon)
      TlGen_Vector.serializeLong(stream, documents)
    }

    public companion object {
      public const val MAGIC: UInt = 0x12B299D4U
    }
  }
}

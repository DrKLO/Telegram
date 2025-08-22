package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_StickerSet : TlGen_Object {
  public data object TL_messages_stickerSetNotModified : TlGen_messages_StickerSet() {
    public const val MAGIC: UInt = 0xD3F924EBU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_messages_stickerSet(
    public val `set`: TlGen_StickerSet,
    public val packs: List<TlGen_StickerPack>,
    public val keywords: List<TlGen_StickerKeyword>,
    public val documents: List<TlGen_Document>,
  ) : TlGen_messages_StickerSet() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      set.serializeToStream(stream)
      TlGen_Vector.serialize(stream, packs)
      TlGen_Vector.serialize(stream, keywords)
      TlGen_Vector.serialize(stream, documents)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6E153F16U
    }
  }
}

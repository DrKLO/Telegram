package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_FavedStickers : TlGen_Object {
  public data object TL_messages_favedStickersNotModified : TlGen_messages_FavedStickers() {
    public const val MAGIC: UInt = 0x9E8FA6D3U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_messages_favedStickers(
    public val hash: Long,
    public val packs: List<TlGen_StickerPack>,
    public val stickers: List<TlGen_Document>,
  ) : TlGen_messages_FavedStickers() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(hash)
      TlGen_Vector.serialize(stream, packs)
      TlGen_Vector.serialize(stream, stickers)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2CB51097U
    }
  }
}

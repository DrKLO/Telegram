package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_RecentStickers : TlGen_Object {
  public data object TL_messages_recentStickersNotModified : TlGen_messages_RecentStickers() {
    public const val MAGIC: UInt = 0x0B17F890U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_messages_recentStickers(
    public val hash: Long,
    public val packs: List<TlGen_StickerPack>,
    public val stickers: List<TlGen_Document>,
    public val dates: List<Int>,
  ) : TlGen_messages_RecentStickers() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(hash)
      TlGen_Vector.serialize(stream, packs)
      TlGen_Vector.serialize(stream, stickers)
      TlGen_Vector.serializeInt(stream, dates)
    }

    public companion object {
      public const val MAGIC: UInt = 0x88D37C56U
    }
  }
}

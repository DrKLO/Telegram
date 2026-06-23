package org.Tajgram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_AllStickers : TlGen_Object {
  public data object TL_messages_allStickersNotModified : TlGen_messages_AllStickers() {
    public const val MAGIC: UInt = 0xE86602C3U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_messages_allStickers(
    public val hash: Long,
    public val sets: List<TlGen_StickerSet>,
  ) : TlGen_messages_AllStickers() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(hash)
      TlGen_Vector.serialize(stream, sets)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCDBBCEBBU
    }
  }
}

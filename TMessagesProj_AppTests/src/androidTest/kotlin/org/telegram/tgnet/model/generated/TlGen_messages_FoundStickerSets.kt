package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_FoundStickerSets : TlGen_Object {
  public data object TL_messages_foundStickerSetsNotModified : TlGen_messages_FoundStickerSets() {
    public const val MAGIC: UInt = 0x0D54B65DU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_messages_foundStickerSets(
    public val hash: Long,
    public val sets: List<TlGen_StickerSetCovered>,
  ) : TlGen_messages_FoundStickerSets() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(hash)
      TlGen_Vector.serialize(stream, sets)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8AF09DD2U
    }
  }
}

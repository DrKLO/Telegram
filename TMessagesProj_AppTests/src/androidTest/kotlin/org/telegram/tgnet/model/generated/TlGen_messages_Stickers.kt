package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_Stickers : TlGen_Object {
  public data object TL_messages_stickersNotModified : TlGen_messages_Stickers() {
    public const val MAGIC: UInt = 0xF1749A22U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_messages_stickers(
    public val hash: Long,
    public val stickers: List<TlGen_Document>,
  ) : TlGen_messages_Stickers() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(hash)
      TlGen_Vector.serialize(stream, stickers)
    }

    public companion object {
      public const val MAGIC: UInt = 0x30A6EC7EU
    }
  }
}

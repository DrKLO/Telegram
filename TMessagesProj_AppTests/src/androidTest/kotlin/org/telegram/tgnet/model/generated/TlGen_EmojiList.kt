package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_EmojiList : TlGen_Object {
  public data object TL_emojiListNotModified : TlGen_EmojiList() {
    public const val MAGIC: UInt = 0x481EADFAU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_emojiList(
    public val hash: Long,
    public val document_id: List<Long>,
  ) : TlGen_EmojiList() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(hash)
      TlGen_Vector.serializeLong(stream, document_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7A1E11D1U
    }
  }
}

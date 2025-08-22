package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_account_EmojiStatuses : TlGen_Object {
  public data object TL_account_emojiStatusesNotModified : TlGen_account_EmojiStatuses() {
    public const val MAGIC: UInt = 0xD08CE645U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_account_emojiStatuses(
    public val hash: Long,
    public val statuses: List<TlGen_EmojiStatus>,
  ) : TlGen_account_EmojiStatuses() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(hash)
      TlGen_Vector.serialize(stream, statuses)
    }

    public companion object {
      public const val MAGIC: UInt = 0x90C467D1U
    }
  }
}

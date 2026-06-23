package org.Tajgram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_EmojiGroups : TlGen_Object {
  public data object TL_messages_emojiGroupsNotModified : TlGen_messages_EmojiGroups() {
    public const val MAGIC: UInt = 0x6FB4AD87U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_messages_emojiGroups(
    public val hash: Int,
    public val groups: List<TlGen_EmojiGroup>,
  ) : TlGen_messages_EmojiGroups() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(hash)
      TlGen_Vector.serialize(stream, groups)
    }

    public companion object {
      public const val MAGIC: UInt = 0x881FB94BU
    }
  }
}

package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_SavedReactionTags : TlGen_Object {
  public data object TL_messages_savedReactionTagsNotModified : TlGen_messages_SavedReactionTags() {
    public const val MAGIC: UInt = 0x889B59EFU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_messages_savedReactionTags(
    public val tags: List<TlGen_SavedReactionTag>,
    public val hash: Long,
  ) : TlGen_messages_SavedReactionTags() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, tags)
      stream.writeInt64(hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3259950AU
    }
  }
}

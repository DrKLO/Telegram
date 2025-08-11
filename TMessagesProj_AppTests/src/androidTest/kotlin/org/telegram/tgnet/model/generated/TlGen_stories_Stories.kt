package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_stories_Stories : TlGen_Object {
  public data class TL_stories_stories(
    public val count: Int,
    public val stories: List<TlGen_StoryItem>,
    public val pinned_to_top: List<Int>?,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_stories_Stories() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (pinned_to_top != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(count)
      TlGen_Vector.serialize(stream, stories)
      pinned_to_top?.let { TlGen_Vector.serializeInt(stream, it) }
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x63C3DD0AU
    }
  }
}

package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_stories_AllStories : TlGen_Object {
  public data class TL_stories_allStoriesNotModified(
    public val state: String,
    public val stealth_mode: TlGen_StoriesStealthMode,
  ) : TlGen_stories_AllStories() {
    internal val flags: UInt
      get() {
        var result = 0U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(state)
      stealth_mode.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1158FE3EU
    }
  }

  public data class TL_stories_allStories(
    public val has_more: Boolean,
    public val count: Int,
    public val state: String,
    public val peer_stories: List<TlGen_PeerStories>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
    public val stealth_mode: TlGen_StoriesStealthMode,
  ) : TlGen_stories_AllStories() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (has_more) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(count)
      stream.writeString(state)
      TlGen_Vector.serialize(stream, peer_stories)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
      stealth_mode.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6EFC5E81U
    }
  }
}

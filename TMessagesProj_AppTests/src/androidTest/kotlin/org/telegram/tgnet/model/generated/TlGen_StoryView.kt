package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StoryView : TlGen_Object {
  public data class TL_storyView(
    public val blocked: Boolean,
    public val blocked_my_stories_from: Boolean,
    public val user_id: Long,
    public val date: Int,
    public val reaction: TlGen_Reaction?,
  ) : TlGen_StoryView() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (blocked_my_stories_from) result = result or 2U
        if (reaction != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(user_id)
      stream.writeInt32(date)
      reaction?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB0BDEAC5U
    }
  }

  public data class TL_storyViewPublicForward(
    public val blocked: Boolean,
    public val blocked_my_stories_from: Boolean,
    public val message: TlGen_Message,
  ) : TlGen_StoryView() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (blocked_my_stories_from) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      message.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9083670BU
    }
  }

  public data class TL_storyViewPublicRepost(
    public val blocked: Boolean,
    public val blocked_my_stories_from: Boolean,
    public val peer_id: TlGen_Peer,
    public val story: TlGen_StoryItem,
  ) : TlGen_StoryView() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (blocked) result = result or 1U
        if (blocked_my_stories_from) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer_id.serializeToStream(stream)
      story.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBD74CF49U
    }
  }
}

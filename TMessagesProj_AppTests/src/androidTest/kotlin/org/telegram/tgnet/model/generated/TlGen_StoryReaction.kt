package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StoryReaction : TlGen_Object {
  public data class TL_storyReaction(
    public val peer_id: TlGen_Peer,
    public val date: Int,
    public val reaction: TlGen_Reaction,
  ) : TlGen_StoryReaction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer_id.serializeToStream(stream)
      stream.writeInt32(date)
      reaction.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6090D6D5U
    }
  }

  public data class TL_storyReactionPublicForward(
    public val message: TlGen_Message,
  ) : TlGen_StoryReaction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      message.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBBAB2643U
    }
  }

  public data class TL_storyReactionPublicRepost(
    public val peer_id: TlGen_Peer,
    public val story: TlGen_StoryItem,
  ) : TlGen_StoryReaction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer_id.serializeToStream(stream)
      story.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCFCD0F13U
    }
  }
}

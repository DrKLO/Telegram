package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PostInteractionCounters : TlGen_Object {
  public data class TL_postInteractionCountersMessage(
    public val msg_id: Int,
    public val views: Int,
    public val forwards: Int,
    public val reactions: Int,
  ) : TlGen_PostInteractionCounters() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(msg_id)
      stream.writeInt32(views)
      stream.writeInt32(forwards)
      stream.writeInt32(reactions)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE7058E7FU
    }
  }

  public data class TL_postInteractionCountersStory(
    public val story_id: Int,
    public val views: Int,
    public val forwards: Int,
    public val reactions: Int,
  ) : TlGen_PostInteractionCounters() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(story_id)
      stream.writeInt32(views)
      stream.writeInt32(forwards)
      stream.writeInt32(reactions)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8A480E27U
    }
  }
}

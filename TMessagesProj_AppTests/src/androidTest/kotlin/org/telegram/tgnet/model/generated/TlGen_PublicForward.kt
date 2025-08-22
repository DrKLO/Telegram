package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PublicForward : TlGen_Object {
  public data class TL_publicForwardMessage(
    public val message: TlGen_Message,
  ) : TlGen_PublicForward() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      message.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x01F2BF4AU
    }
  }

  public data class TL_publicForwardStory(
    public val peer: TlGen_Peer,
    public val story: TlGen_StoryItem,
  ) : TlGen_PublicForward() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      story.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEDF3ADD0U
    }
  }
}

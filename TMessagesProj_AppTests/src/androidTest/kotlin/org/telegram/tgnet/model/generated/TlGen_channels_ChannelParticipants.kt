package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_channels_ChannelParticipants : TlGen_Object {
  public data object TL_channels_channelParticipantsNotModified :
      TlGen_channels_ChannelParticipants() {
    public const val MAGIC: UInt = 0xF0173FE9U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_channels_channelParticipants(
    public val count: Int,
    public val participants: List<TlGen_ChannelParticipant>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_channels_ChannelParticipants() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(count)
      TlGen_Vector.serialize(stream, participants)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9AB0FEAFU
    }
  }
}

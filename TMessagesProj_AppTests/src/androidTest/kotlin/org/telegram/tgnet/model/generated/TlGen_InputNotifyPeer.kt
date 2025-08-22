package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputNotifyPeer : TlGen_Object {
  public data class TL_inputNotifyPeer(
    public val peer: TlGen_InputPeer,
  ) : TlGen_InputNotifyPeer() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB8BC5B0CU
    }
  }

  public data object TL_inputNotifyUsers : TlGen_InputNotifyPeer() {
    public const val MAGIC: UInt = 0x193B4417U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputNotifyChats : TlGen_InputNotifyPeer() {
    public const val MAGIC: UInt = 0x4A95E84EU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputNotifyBroadcasts : TlGen_InputNotifyPeer() {
    public const val MAGIC: UInt = 0xB1DB7C7EU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_inputNotifyForumTopic(
    public val peer: TlGen_InputPeer,
    public val top_msg_id: Int,
  ) : TlGen_InputNotifyPeer() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(top_msg_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5C467992U
    }
  }
}

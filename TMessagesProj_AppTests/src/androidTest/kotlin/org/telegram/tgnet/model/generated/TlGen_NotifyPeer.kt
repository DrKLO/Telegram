package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_NotifyPeer : TlGen_Object {
  public data class TL_notifyPeer(
    public val peer: TlGen_Peer,
  ) : TlGen_NotifyPeer() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9FD40BD8U
    }
  }

  public data object TL_notifyUsers : TlGen_NotifyPeer() {
    public const val MAGIC: UInt = 0xB4C83B4CU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_notifyChats : TlGen_NotifyPeer() {
    public const val MAGIC: UInt = 0xC007CEC3U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_notifyBroadcasts : TlGen_NotifyPeer() {
    public const val MAGIC: UInt = 0xD612E8EFU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_notifyForumTopic(
    public val peer: TlGen_Peer,
    public val top_msg_id: Int,
  ) : TlGen_NotifyPeer() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(top_msg_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x226E6308U
    }
  }
}

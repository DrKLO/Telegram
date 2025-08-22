package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputChannel : TlGen_Object {
  public data object TL_inputChannelEmpty : TlGen_InputChannel() {
    public const val MAGIC: UInt = 0xEE8C1E86U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_inputChannel(
    public val channel_id: Long,
    public val access_hash: Long,
  ) : TlGen_InputChannel() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(channel_id)
      stream.writeInt64(access_hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF35AEC28U
    }
  }

  public data class TL_inputChannelFromMessage(
    public val peer: TlGen_InputPeer,
    public val msg_id: Int,
    public val channel_id: Long,
  ) : TlGen_InputChannel() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(msg_id)
      stream.writeInt64(channel_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5B934F9DU
    }
  }

  public data class TL_inputChannel_layer132(
    public val channel_id: Int,
    public val access_hash: Long,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(channel_id)
      stream.writeInt64(access_hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAFEB712EU
    }
  }

  public data class TL_inputChannelFromMessage_layer132(
    public val peer: TlGen_InputPeer,
    public val msg_id: Int,
    public val channel_id: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(msg_id)
      stream.writeInt32(channel_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2A286531U
    }
  }
}

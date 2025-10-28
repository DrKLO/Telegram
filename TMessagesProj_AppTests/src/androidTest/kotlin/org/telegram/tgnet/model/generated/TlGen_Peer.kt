package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_Peer : TlGen_Object {
  public data class TL_peerUser(
    public val user_id: Long,
  ) : TlGen_Peer() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(user_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x59511722U
    }
  }

  public data class TL_peerChat(
    public val chat_id: Long,
  ) : TlGen_Peer() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(chat_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x36C6019AU
    }
  }

  public data class TL_peerChannel(
    public val channel_id: Long,
  ) : TlGen_Peer() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(channel_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA2A5371EU
    }
  }

  public data class TL_peerUser_layer132(
    public val user_id: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(user_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9DB1BC6DU
    }
  }

  public data class TL_peerChat_layer132(
    public val chat_id: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(chat_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBAD0E5BBU
    }
  }

  public data class TL_peerChannel_layer132(
    public val channel_id: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(channel_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBDDDE532U
    }
  }
}

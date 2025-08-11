package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputPeer : TlGen_Object {
  public data object TL_inputPeerEmpty : TlGen_InputPeer() {
    public const val MAGIC: UInt = 0x7F3B18EAU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputPeerSelf : TlGen_InputPeer() {
    public const val MAGIC: UInt = 0x7DA07EC9U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_inputPeerChat(
    public val chat_id: Long,
  ) : TlGen_InputPeer() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(chat_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x35A95CB9U
    }
  }

  public data class TL_inputPeerUser(
    public val user_id: Long,
    public val access_hash: Long,
  ) : TlGen_InputPeer() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(user_id)
      stream.writeInt64(access_hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDDE8A54CU
    }
  }

  public data class TL_inputPeerChannel(
    public val channel_id: Long,
    public val access_hash: Long,
  ) : TlGen_InputPeer() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(channel_id)
      stream.writeInt64(access_hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0x27BCBBFCU
    }
  }

  public data class TL_inputPeerUserFromMessage(
    public val peer: TlGen_InputPeer,
    public val msg_id: Int,
    public val user_id: Long,
  ) : TlGen_InputPeer() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(msg_id)
      stream.writeInt64(user_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA87B0A1CU
    }
  }

  public data class TL_inputPeerChannelFromMessage(
    public val peer: TlGen_InputPeer,
    public val msg_id: Int,
    public val channel_id: Long,
  ) : TlGen_InputPeer() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(msg_id)
      stream.writeInt64(channel_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBD2A0840U
    }
  }

  public data class TL_inputPeerChat_layer132(
    public val chat_id: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(chat_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x179BE863U
    }
  }

  public data class TL_inputPeerUser_layer132(
    public val user_id: Int,
    public val access_hash: Long,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(user_id)
      stream.writeInt64(access_hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7B8E7DE6U
    }
  }

  public data class TL_inputPeerChannel_layer132(
    public val channel_id: Int,
    public val access_hash: Long,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(channel_id)
      stream.writeInt64(access_hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0x20ADAEF8U
    }
  }

  public data class TL_inputPeerUserFromMessage_layer132(
    public val peer: TlGen_InputPeer,
    public val msg_id: Int,
    public val user_id: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(msg_id)
      stream.writeInt32(user_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x17BAE2E6U
    }
  }

  public data class TL_inputPeerChannelFromMessage_layer132(
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
      public const val MAGIC: UInt = 0x9C95F7BBU
    }
  }
}

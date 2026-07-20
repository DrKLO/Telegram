package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputUser : TlGen_Object {
  public data object TL_inputUserEmpty : TlGen_InputUser() {
    public const val MAGIC: UInt = 0xB98886CFU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputUserSelf : TlGen_InputUser() {
    public const val MAGIC: UInt = 0xF7C1B13FU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_inputUser(
    public val user_id: Long,
    public val access_hash: Long,
  ) : TlGen_InputUser() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(user_id)
      stream.writeInt64(access_hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF21158C6U
    }
  }

  public data class TL_inputUserFromMessage(
    public val peer: TlGen_InputPeer,
    public val msg_id: Int,
    public val user_id: Long,
  ) : TlGen_InputUser() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(msg_id)
      stream.writeInt64(user_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1DA448E2U
    }
  }

  public data class TL_inputUser_layer132(
    public val user_id: Int,
    public val access_hash: Long,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(user_id)
      stream.writeInt64(access_hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD8292816U
    }
  }

  public data class TL_inputUserFromMessage_layer132(
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
      public const val MAGIC: UInt = 0x2D117597U
    }
  }
}

package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ChatParticipant : TlGen_Object {
  public data class TL_chatParticipant(
    public val user_id: Long,
    public val inviter_id: Long,
    public val date: Int,
  ) : TlGen_ChatParticipant() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(user_id)
      stream.writeInt64(inviter_id)
      stream.writeInt32(date)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC02D4007U
    }
  }

  public data class TL_chatParticipantCreator(
    public val user_id: Long,
  ) : TlGen_ChatParticipant() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(user_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE46BCEE4U
    }
  }

  public data class TL_chatParticipantAdmin(
    public val user_id: Long,
    public val inviter_id: Long,
    public val date: Int,
  ) : TlGen_ChatParticipant() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(user_id)
      stream.writeInt64(inviter_id)
      stream.writeInt32(date)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA0933F5BU
    }
  }

  public data class TL_chatParticipant_layer132(
    public val user_id: Int,
    public val inviter_id: Int,
    public val date: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(user_id)
      stream.writeInt32(inviter_id)
      stream.writeInt32(date)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC8D7493EU
    }
  }

  public data class TL_chatParticipantCreator_layer132(
    public val user_id: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(user_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDA13538AU
    }
  }

  public data class TL_chatParticipantAdmin_layer132(
    public val user_id: Int,
    public val inviter_id: Int,
    public val date: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(user_id)
      stream.writeInt32(inviter_id)
      stream.writeInt32(date)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE2D6E436U
    }
  }
}

package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_updates_Difference : TlGen_Object {
  public data class TL_updates_differenceEmpty(
    public val date: Int,
    public val seq: Int,
  ) : TlGen_updates_Difference() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(date)
      stream.writeInt32(seq)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5D75A138U
    }
  }

  public data class TL_updates_difference(
    public val new_messages: List<TlGen_Message>,
    public val new_encrypted_messages: List<TlGen_EncryptedMessage>,
    public val other_updates: List<TlGen_Update>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
    public val state: TlGen_updates_State,
  ) : TlGen_updates_Difference() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, new_messages)
      TlGen_Vector.serialize(stream, new_encrypted_messages)
      TlGen_Vector.serialize(stream, other_updates)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
      state.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x00F49CA0U
    }
  }

  public data class TL_updates_differenceSlice(
    public val new_messages: List<TlGen_Message>,
    public val new_encrypted_messages: List<TlGen_EncryptedMessage>,
    public val other_updates: List<TlGen_Update>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
    public val intermediate_state: TlGen_updates_State,
  ) : TlGen_updates_Difference() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, new_messages)
      TlGen_Vector.serialize(stream, new_encrypted_messages)
      TlGen_Vector.serialize(stream, other_updates)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
      intermediate_state.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA8FB1981U
    }
  }

  public data class TL_updates_differenceTooLong(
    public val pts: Int,
  ) : TlGen_updates_Difference() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(pts)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4AFE8F6DU
    }
  }
}

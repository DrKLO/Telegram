package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ChatParticipants : TlGen_Object {
  public data class TL_chatParticipantsForbidden(
    public val chat_id: Long,
    public val self_participant: TlGen_ChatParticipant?,
  ) : TlGen_ChatParticipants() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (self_participant != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(chat_id)
      self_participant?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8763D3E1U
    }
  }

  public data class TL_chatParticipants(
    public val chat_id: Long,
    public val participants: List<TlGen_ChatParticipant>,
    public val version: Int,
  ) : TlGen_ChatParticipants() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(chat_id)
      TlGen_Vector.serialize(stream, participants)
      stream.writeInt32(version)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3CBC93F8U
    }
  }

  public data class TL_chatParticipantsForbidden_layer36(
    public val chat_id: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(chat_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0FD2BB8AU
    }
  }

  public data class TL_chatParticipants_layer39(
    public val chat_id: Int,
    public val admin_id: Int,
    public val participants: List<TlGen_ChatParticipant>,
    public val version: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(chat_id)
      stream.writeInt32(admin_id)
      TlGen_Vector.serialize(stream, participants)
      stream.writeInt32(version)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7841B415U
    }
  }

  public data class TL_chatParticipantsForbidden_layer132(
    public val chat_id: Int,
    public val self_participant: TlGen_ChatParticipant?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (self_participant != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(chat_id)
      self_participant?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFC900C2BU
    }
  }

  public data class TL_chatParticipants_layer132(
    public val chat_id: Int,
    public val participants: List<TlGen_ChatParticipant>,
    public val version: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(chat_id)
      TlGen_Vector.serialize(stream, participants)
      stream.writeInt32(version)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3F460FEDU
    }
  }
}

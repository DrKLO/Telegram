package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_phone_GroupParticipants : TlGen_Object {
  public data class TL_phone_groupParticipants(
    public val count: Int,
    public val participants: List<TlGen_GroupCallParticipant>,
    public val next_offset: String,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
    public val version: Int,
  ) : TlGen_phone_GroupParticipants() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(count)
      TlGen_Vector.serialize(stream, participants)
      stream.writeString(next_offset)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
      stream.writeInt32(version)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF47751B6U
    }
  }
}

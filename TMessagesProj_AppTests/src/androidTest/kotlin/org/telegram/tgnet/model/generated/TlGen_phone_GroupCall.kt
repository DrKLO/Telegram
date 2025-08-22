package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_phone_GroupCall : TlGen_Object {
  public data class TL_phone_groupCall(
    public val call: TlGen_GroupCall,
    public val participants: List<TlGen_GroupCallParticipant>,
    public val participants_next_offset: String,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_phone_GroupCall() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      call.serializeToStream(stream)
      TlGen_Vector.serialize(stream, participants)
      stream.writeString(participants_next_offset)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9E727AADU
    }
  }
}

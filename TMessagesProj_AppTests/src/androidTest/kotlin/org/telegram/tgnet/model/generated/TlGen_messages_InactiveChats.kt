package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_InactiveChats : TlGen_Object {
  public data class TL_messages_inactiveChats(
    public val dates: List<Int>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_messages_InactiveChats() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serializeInt(stream, dates)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA927FEC5U
    }
  }
}

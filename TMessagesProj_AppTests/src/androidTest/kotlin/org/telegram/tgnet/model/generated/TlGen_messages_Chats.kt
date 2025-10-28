package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_Chats : TlGen_Object {
  public data class TL_messages_chats(
    public val chats: List<TlGen_Chat>,
  ) : TlGen_messages_Chats() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, chats)
    }

    public companion object {
      public const val MAGIC: UInt = 0x64FF9FD5U
    }
  }

  public data class TL_messages_chatsSlice(
    public val count: Int,
    public val chats: List<TlGen_Chat>,
  ) : TlGen_messages_Chats() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(count)
      TlGen_Vector.serialize(stream, chats)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9CD81144U
    }
  }
}

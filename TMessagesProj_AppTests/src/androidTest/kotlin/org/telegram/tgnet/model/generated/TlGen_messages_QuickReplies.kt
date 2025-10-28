package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_QuickReplies : TlGen_Object {
  public data class TL_messages_quickReplies(
    public val quick_replies: List<TlGen_QuickReply>,
    public val messages: List<TlGen_Message>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_messages_QuickReplies() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, quick_replies)
      TlGen_Vector.serialize(stream, messages)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC68D6695U
    }
  }

  public data object TL_messages_quickRepliesNotModified : TlGen_messages_QuickReplies() {
    public const val MAGIC: UInt = 0x5F91EB5BU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}

package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_Dialogs : TlGen_Object {
  public data class TL_messages_dialogs(
    public val dialogs: List<TlGen_Dialog>,
    public val messages: List<TlGen_Message>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_messages_Dialogs() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, dialogs)
      TlGen_Vector.serialize(stream, messages)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x15BA6C40U
    }
  }

  public data class TL_messages_dialogsSlice(
    public val count: Int,
    public val dialogs: List<TlGen_Dialog>,
    public val messages: List<TlGen_Message>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_messages_Dialogs() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(count)
      TlGen_Vector.serialize(stream, dialogs)
      TlGen_Vector.serialize(stream, messages)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x71E094F3U
    }
  }

  public data class TL_messages_dialogsNotModified(
    public val count: Int,
  ) : TlGen_messages_Dialogs() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(count)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF0E3E596U
    }
  }
}

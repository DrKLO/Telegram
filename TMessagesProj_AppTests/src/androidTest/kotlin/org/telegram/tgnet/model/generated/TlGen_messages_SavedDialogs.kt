package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_SavedDialogs : TlGen_Object {
  public data class TL_messages_savedDialogs(
    public val dialogs: List<TlGen_SavedDialog>,
    public val messages: List<TlGen_Message>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_messages_SavedDialogs() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, dialogs)
      TlGen_Vector.serialize(stream, messages)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF83AE221U
    }
  }

  public data class TL_messages_savedDialogsSlice(
    public val count: Int,
    public val dialogs: List<TlGen_SavedDialog>,
    public val messages: List<TlGen_Message>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_messages_SavedDialogs() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(count)
      TlGen_Vector.serialize(stream, dialogs)
      TlGen_Vector.serialize(stream, messages)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x44BA9DD9U
    }
  }

  public data class TL_messages_savedDialogsNotModified(
    public val count: Int,
  ) : TlGen_messages_SavedDialogs() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(count)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC01F6FE8U
    }
  }
}

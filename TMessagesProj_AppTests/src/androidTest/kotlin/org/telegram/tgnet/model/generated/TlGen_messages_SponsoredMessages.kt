package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_SponsoredMessages : TlGen_Object {
  public data object TL_messages_sponsoredMessagesEmpty : TlGen_messages_SponsoredMessages() {
    public const val MAGIC: UInt = 0x1839490FU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_messages_sponsoredMessages(
    public val posts_between: Int?,
    public val start_delay: Int?,
    public val between_delay: Int?,
    public val messages: List<TlGen_SponsoredMessage>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_messages_SponsoredMessages() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (posts_between != null) result = result or 1U
        if (start_delay != null) result = result or 2U
        if (between_delay != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      posts_between?.let { stream.writeInt32(it) }
      start_delay?.let { stream.writeInt32(it) }
      between_delay?.let { stream.writeInt32(it) }
      TlGen_Vector.serialize(stream, messages)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFFDA656DU
    }
  }
}

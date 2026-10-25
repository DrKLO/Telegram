package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_feed_FeedMessages : TlGen_Object {
  public data object TL_feed_feedMessagesNotModified : TlGen_feed_FeedMessages() {
    public const val MAGIC: UInt = 0xDB1A3503U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_feed_feedMessages(
    public val max_position: TlGen_FeedPosition?,
    public val min_position: TlGen_FeedPosition?,
    public val read_max_position: TlGen_FeedPosition?,
    public val messages: List<TlGen_Message>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
  ) : TlGen_feed_FeedMessages() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (max_position != null) result = result or 1U
        if (min_position != null) result = result or 2U
        if (read_max_position != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      max_position?.serializeToStream(stream)
      min_position?.serializeToStream(stream)
      read_max_position?.serializeToStream(stream)
      TlGen_Vector.serialize(stream, messages)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDCF754B9U
    }
  }
}

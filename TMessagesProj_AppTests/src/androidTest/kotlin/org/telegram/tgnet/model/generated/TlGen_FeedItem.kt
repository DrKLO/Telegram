package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_FeedItem : TlGen_Object {
  public data class TL_feedItemMessage(
    public val message: TlGen_Message,
  ) : TlGen_FeedItem() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      message.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7E73B3A5U
    }
  }

  public data class TL_feedItemMessageGroup(
    public val messages: List<TlGen_Message>,
  ) : TlGen_FeedItem() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, messages)
    }

    public companion object {
      public const val MAGIC: UInt = 0x652F4C71U
    }
  }

  public data object TL_feedItemInterestCategories : TlGen_FeedItem() {
    public const val MAGIC: UInt = 0x6BE7187BU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}

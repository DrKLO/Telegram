package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ChannelMessagesFilter : TlGen_Object {
  public data object TL_channelMessagesFilterEmpty : TlGen_ChannelMessagesFilter() {
    public const val MAGIC: UInt = 0x94D42EE7U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_channelMessagesFilter(
    public val exclude_new_messages: Boolean,
    public val ranges: List<TlGen_MessageRange>,
  ) : TlGen_ChannelMessagesFilter() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (exclude_new_messages) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serialize(stream, ranges)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCD77D957U
    }
  }
}

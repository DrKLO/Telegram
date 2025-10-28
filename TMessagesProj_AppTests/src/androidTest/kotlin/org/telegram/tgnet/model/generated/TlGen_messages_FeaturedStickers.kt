package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_FeaturedStickers : TlGen_Object {
  public data class TL_messages_featuredStickersNotModified(
    public val count: Int,
  ) : TlGen_messages_FeaturedStickers() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(count)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC6DC0C66U
    }
  }

  public data class TL_messages_featuredStickers(
    public val premium: Boolean,
    public val hash: Long,
    public val count: Int,
    public val sets: List<TlGen_StickerSetCovered>,
    public val unread: List<Long>,
  ) : TlGen_messages_FeaturedStickers() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (premium) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(hash)
      stream.writeInt32(count)
      TlGen_Vector.serialize(stream, sets)
      TlGen_Vector.serializeLong(stream, unread)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBE382906U
    }
  }
}

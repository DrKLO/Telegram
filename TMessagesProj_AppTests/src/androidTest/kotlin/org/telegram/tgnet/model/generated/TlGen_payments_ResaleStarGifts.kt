package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_payments_ResaleStarGifts : TlGen_Object {
  public data class TL_payments_resaleStarGifts(
    public val count: Int,
    public val gifts: List<TlGen_StarGift>,
    public val next_offset: String?,
    public val chats: List<TlGen_Chat>,
    public val counters: List<TlGen_StarGiftAttributeCounter>?,
    public val users: List<TlGen_User>,
    public val multiflags_1: Multiflags_1?,
  ) : TlGen_payments_ResaleStarGifts() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (next_offset != null) result = result or 1U
        if (multiflags_1 != null) result = result or 2U
        if (counters != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(count)
      TlGen_Vector.serialize(stream, gifts)
      next_offset?.let { stream.writeString(it) }
      multiflags_1?.let { TlGen_Vector.serialize(stream, it.attributes) }
      multiflags_1?.let { stream.writeInt64(it.attributes_hash) }
      TlGen_Vector.serialize(stream, chats)
      counters?.let { TlGen_Vector.serialize(stream, it) }
      TlGen_Vector.serialize(stream, users)
    }

    public data class Multiflags_1(
      public val attributes: List<TlGen_StarGiftAttribute>,
      public val attributes_hash: Long,
    )

    public companion object {
      public const val MAGIC: UInt = 0x947A12DFU
    }
  }
}

package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_FoundStickers : TlGen_Object {
  public data class TL_messages_foundStickersNotModified(
    public val next_offset: Int?,
  ) : TlGen_messages_FoundStickers() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (next_offset != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      next_offset?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x6010C534U
    }
  }

  public data class TL_messages_foundStickers(
    public val next_offset: Int?,
    public val hash: Long,
    public val stickers: List<TlGen_Document>,
  ) : TlGen_messages_FoundStickers() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (next_offset != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      next_offset?.let { stream.writeInt32(it) }
      stream.writeInt64(hash)
      TlGen_Vector.serialize(stream, stickers)
    }

    public companion object {
      public const val MAGIC: UInt = 0x82C9E290U
    }
  }
}

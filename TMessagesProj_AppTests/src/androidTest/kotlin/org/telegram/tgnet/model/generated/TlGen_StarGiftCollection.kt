package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StarGiftCollection : TlGen_Object {
  public data class TL_starGiftCollection(
    public val collection_id: Int,
    public val title: String,
    public val icon: TlGen_Document?,
    public val gifts_count: Int,
    public val hash: Long,
  ) : TlGen_StarGiftCollection() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (icon != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(collection_id)
      stream.writeString(title)
      icon?.serializeToStream(stream)
      stream.writeInt32(gifts_count)
      stream.writeInt64(hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9D6B13B0U
    }
  }
}

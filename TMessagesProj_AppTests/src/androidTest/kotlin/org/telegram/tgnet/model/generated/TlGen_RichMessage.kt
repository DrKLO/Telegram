package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_RichMessage : TlGen_Object {
  public data class TL_richMessage(
    public val rtl: Boolean,
    public val part: Boolean,
    public val blocks: List<TlGen_PageBlock>,
    public val photos: List<TlGen_Photo>,
    public val documents: List<TlGen_Document>,
  ) : TlGen_RichMessage() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (rtl) result = result or 1U
        if (part) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serialize(stream, blocks)
      TlGen_Vector.serialize(stream, photos)
      TlGen_Vector.serialize(stream, documents)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBAF39D8BU
    }
  }
}

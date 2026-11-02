package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_feed_InterestCategories : TlGen_Object {
  public data object TL_feed_interestCategoriesNotModified : TlGen_feed_InterestCategories() {
    public const val MAGIC: UInt = 0x79CF2195U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_feed_interestCategories(
    public val categories: List<TlGen_feed_InterestCategory>,
    public val selected: List<Int>,
  ) : TlGen_feed_InterestCategories() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, categories)
      TlGen_Vector.serializeInt(stream, selected)
    }

    public companion object {
      public const val MAGIC: UInt = 0xECB62FA0U
    }
  }
}

package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_SearchResultsCalendarPeriod : TlGen_Object {
  public data class TL_searchResultsCalendarPeriod(
    public val date: Int,
    public val min_msg_id: Int,
    public val max_msg_id: Int,
    public val count: Int,
  ) : TlGen_SearchResultsCalendarPeriod() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(date)
      stream.writeInt32(min_msg_id)
      stream.writeInt32(max_msg_id)
      stream.writeInt32(count)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC9B0539FU
    }
  }
}

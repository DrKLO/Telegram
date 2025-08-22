package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_SearchResultsPosition : TlGen_Object {
  public data class TL_searchResultPosition(
    public val msg_id: Int,
    public val date: Int,
    public val offset: Int,
  ) : TlGen_SearchResultsPosition() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(msg_id)
      stream.writeInt32(date)
      stream.writeInt32(offset)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7F648B67U
    }
  }
}

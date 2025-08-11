package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_SearchCounter : TlGen_Object {
  public data class TL_messages_searchCounter(
    public val inexact: Boolean,
    public val filter: TlGen_MessagesFilter,
    public val count: Int,
  ) : TlGen_messages_SearchCounter() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (inexact) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      filter.serializeToStream(stream)
      stream.writeInt32(count)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE844EBFFU
    }
  }
}

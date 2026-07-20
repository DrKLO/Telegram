package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_SuggestedPost : TlGen_Object {
  public data class TL_suggestedPost(
    public val accepted: Boolean,
    public val rejected: Boolean,
    public val price: TlGen_StarsAmount?,
    public val schedule_date: Int?,
  ) : TlGen_SuggestedPost() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (schedule_date != null) result = result or 1U
        if (accepted) result = result or 2U
        if (rejected) result = result or 4U
        if (price != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      price?.serializeToStream(stream)
      schedule_date?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x0E8E37E5U
    }
  }
}

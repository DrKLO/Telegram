package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_BusinessWorkHours : TlGen_Object {
  public data class TL_businessWorkHours(
    public val open_now: Boolean,
    public val timezone_id: String,
    public val weekly_open: List<TlGen_BusinessWeeklyOpen>,
  ) : TlGen_BusinessWorkHours() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (open_now) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(timezone_id)
      TlGen_Vector.serialize(stream, weekly_open)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8C92B098U
    }
  }
}

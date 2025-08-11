package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StarRefProgram : TlGen_Object {
  public data class TL_starRefProgram(
    public val bot_id: Long,
    public val commission_permille: Int,
    public val duration_months: Int?,
    public val end_date: Int?,
    public val daily_revenue_per_user: TlGen_StarsAmount?,
  ) : TlGen_StarRefProgram() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (duration_months != null) result = result or 1U
        if (end_date != null) result = result or 2U
        if (daily_revenue_per_user != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(bot_id)
      stream.writeInt32(commission_permille)
      duration_months?.let { stream.writeInt32(it) }
      end_date?.let { stream.writeInt32(it) }
      daily_revenue_per_user?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDD0C66F2U
    }
  }
}

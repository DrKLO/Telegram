package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StarsRevenueStatus : TlGen_Object {
  public data class TL_starsRevenueStatus(
    public val withdrawal_enabled: Boolean,
    public val current_balance: TlGen_StarsAmount,
    public val available_balance: TlGen_StarsAmount,
    public val overall_revenue: TlGen_StarsAmount,
    public val next_withdrawal_at: Int?,
  ) : TlGen_StarsRevenueStatus() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (withdrawal_enabled) result = result or 1U
        if (next_withdrawal_at != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      current_balance.serializeToStream(stream)
      available_balance.serializeToStream(stream)
      overall_revenue.serializeToStream(stream)
      next_withdrawal_at?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xFEBE5491U
    }
  }
}

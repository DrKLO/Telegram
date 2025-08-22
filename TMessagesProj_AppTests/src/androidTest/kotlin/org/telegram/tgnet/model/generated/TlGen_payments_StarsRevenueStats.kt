package org.telegram.tgnet.model.generated

import kotlin.Double
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_payments_StarsRevenueStats : TlGen_Object {
  public data class TL_payments_starsRevenueStats(
    public val top_hours_graph: TlGen_StatsGraph?,
    public val revenue_graph: TlGen_StatsGraph,
    public val status: TlGen_StarsRevenueStatus,
    public val usd_rate: Double,
  ) : TlGen_payments_StarsRevenueStats() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (top_hours_graph != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      top_hours_graph?.serializeToStream(stream)
      revenue_graph.serializeToStream(stream)
      status.serializeToStream(stream)
      stream.writeDouble(usd_rate)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6C207376U
    }
  }
}

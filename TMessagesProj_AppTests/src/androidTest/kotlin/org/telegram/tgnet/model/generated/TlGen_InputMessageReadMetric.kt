package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputMessageReadMetric : TlGen_Object {
  public data class TL_inputMessageReadMetric(
    public val msg_id: Int,
    public val view_id: Long,
    public val time_in_view_ms: Int,
    public val active_time_in_view_ms: Int,
    public val height_to_viewport_ratio_permille: Int,
    public val seen_range_ratio_permille: Int,
  ) : TlGen_InputMessageReadMetric() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(msg_id)
      stream.writeInt64(view_id)
      stream.writeInt32(time_in_view_ms)
      stream.writeInt32(active_time_in_view_ms)
      stream.writeInt32(height_to_viewport_ratio_permille)
      stream.writeInt32(seen_range_ratio_permille)
    }

    public companion object {
      public const val MAGIC: UInt = 0x402B4495U
    }
  }
}

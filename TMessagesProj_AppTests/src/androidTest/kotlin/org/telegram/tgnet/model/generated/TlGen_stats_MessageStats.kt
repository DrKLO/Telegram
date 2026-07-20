package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_stats_MessageStats : TlGen_Object {
  public data class TL_stats_messageStats(
    public val views_graph: TlGen_StatsGraph,
    public val reactions_by_emotion_graph: TlGen_StatsGraph,
  ) : TlGen_stats_MessageStats() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      views_graph.serializeToStream(stream)
      reactions_by_emotion_graph.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7FE91C14U
    }
  }
}

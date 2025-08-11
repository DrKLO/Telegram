package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_stats_MegagroupStats : TlGen_Object {
  public data class TL_stats_megagroupStats(
    public val period: TlGen_StatsDateRangeDays,
    public val members: TlGen_StatsAbsValueAndPrev,
    public val messages: TlGen_StatsAbsValueAndPrev,
    public val viewers: TlGen_StatsAbsValueAndPrev,
    public val posters: TlGen_StatsAbsValueAndPrev,
    public val growth_graph: TlGen_StatsGraph,
    public val members_graph: TlGen_StatsGraph,
    public val new_members_by_source_graph: TlGen_StatsGraph,
    public val languages_graph: TlGen_StatsGraph,
    public val messages_graph: TlGen_StatsGraph,
    public val actions_graph: TlGen_StatsGraph,
    public val top_hours_graph: TlGen_StatsGraph,
    public val weekdays_graph: TlGen_StatsGraph,
    public val top_posters: List<TlGen_StatsGroupTopPoster>,
    public val top_admins: List<TlGen_StatsGroupTopAdmin>,
    public val top_inviters: List<TlGen_StatsGroupTopInviter>,
    public val users: List<TlGen_User>,
  ) : TlGen_stats_MegagroupStats() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      period.serializeToStream(stream)
      members.serializeToStream(stream)
      messages.serializeToStream(stream)
      viewers.serializeToStream(stream)
      posters.serializeToStream(stream)
      growth_graph.serializeToStream(stream)
      members_graph.serializeToStream(stream)
      new_members_by_source_graph.serializeToStream(stream)
      languages_graph.serializeToStream(stream)
      messages_graph.serializeToStream(stream)
      actions_graph.serializeToStream(stream)
      top_hours_graph.serializeToStream(stream)
      weekdays_graph.serializeToStream(stream)
      TlGen_Vector.serialize(stream, top_posters)
      TlGen_Vector.serialize(stream, top_admins)
      TlGen_Vector.serialize(stream, top_inviters)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEF7FF916U
    }
  }
}

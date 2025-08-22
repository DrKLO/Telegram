package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_stats_BroadcastStats : TlGen_Object {
  public data class TL_stats_broadcastStats(
    public val period: TlGen_StatsDateRangeDays,
    public val followers: TlGen_StatsAbsValueAndPrev,
    public val views_per_post: TlGen_StatsAbsValueAndPrev,
    public val shares_per_post: TlGen_StatsAbsValueAndPrev,
    public val reactions_per_post: TlGen_StatsAbsValueAndPrev,
    public val views_per_story: TlGen_StatsAbsValueAndPrev,
    public val shares_per_story: TlGen_StatsAbsValueAndPrev,
    public val reactions_per_story: TlGen_StatsAbsValueAndPrev,
    public val enabled_notifications: TlGen_StatsPercentValue,
    public val growth_graph: TlGen_StatsGraph,
    public val followers_graph: TlGen_StatsGraph,
    public val mute_graph: TlGen_StatsGraph,
    public val top_hours_graph: TlGen_StatsGraph,
    public val interactions_graph: TlGen_StatsGraph,
    public val iv_interactions_graph: TlGen_StatsGraph,
    public val views_by_source_graph: TlGen_StatsGraph,
    public val new_followers_by_source_graph: TlGen_StatsGraph,
    public val languages_graph: TlGen_StatsGraph,
    public val reactions_by_emotion_graph: TlGen_StatsGraph,
    public val story_interactions_graph: TlGen_StatsGraph,
    public val story_reactions_by_emotion_graph: TlGen_StatsGraph,
    public val recent_posts_interactions: List<TlGen_PostInteractionCounters>,
  ) : TlGen_stats_BroadcastStats() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      period.serializeToStream(stream)
      followers.serializeToStream(stream)
      views_per_post.serializeToStream(stream)
      shares_per_post.serializeToStream(stream)
      reactions_per_post.serializeToStream(stream)
      views_per_story.serializeToStream(stream)
      shares_per_story.serializeToStream(stream)
      reactions_per_story.serializeToStream(stream)
      enabled_notifications.serializeToStream(stream)
      growth_graph.serializeToStream(stream)
      followers_graph.serializeToStream(stream)
      mute_graph.serializeToStream(stream)
      top_hours_graph.serializeToStream(stream)
      interactions_graph.serializeToStream(stream)
      iv_interactions_graph.serializeToStream(stream)
      views_by_source_graph.serializeToStream(stream)
      new_followers_by_source_graph.serializeToStream(stream)
      languages_graph.serializeToStream(stream)
      reactions_by_emotion_graph.serializeToStream(stream)
      story_interactions_graph.serializeToStream(stream)
      story_reactions_by_emotion_graph.serializeToStream(stream)
      TlGen_Vector.serialize(stream, recent_posts_interactions)
    }

    public companion object {
      public const val MAGIC: UInt = 0x396CA5FCU
    }
  }
}

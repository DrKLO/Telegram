package org.telegram.tgnet.tl;

import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public class TL_stats {
    public static class TL_megagroupStats extends TLObject {
        public static final int constructor = 0xef7ff916;
        public TL_statsDateRangeDays period;
        public TL_statsAbsValueAndPrev members;
        public TL_statsAbsValueAndPrev messages;
        public TL_statsAbsValueAndPrev viewers;
        public TL_statsAbsValueAndPrev posters;
        public StatsGraph growth_graph;
        public StatsGraph members_graph;
        public StatsGraph new_members_by_source_graph;
        public StatsGraph languages_graph;
        public StatsGraph messages_graph;
        public StatsGraph actions_graph;
        public StatsGraph top_hours_graph;
        public StatsGraph weekdays_graph;
        public ArrayList<TL_statsGroupTopPoster> top_posters = new ArrayList<>();
        public ArrayList<TL_statsGroupTopAdmin> top_admins = new ArrayList<>();
        public ArrayList<TL_statsGroupTopInviter> top_inviters = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static TL_megagroupStats TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (TL_megagroupStats.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_stats_megagroupStats", constructor));
                } else {
                    return null;
                }
            }
            TL_megagroupStats result = new TL_megagroupStats();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            period = TL_statsDateRangeDays.TLdeserialize(stream, stream.readInt32(exception), exception);
            members = TL_statsAbsValueAndPrev.TLdeserialize(stream, stream.readInt32(exception), exception);
            messages = TL_statsAbsValueAndPrev.TLdeserialize(stream, stream.readInt32(exception), exception);
            viewers = TL_statsAbsValueAndPrev.TLdeserialize(stream, stream.readInt32(exception), exception);
            posters = TL_statsAbsValueAndPrev.TLdeserialize(stream, stream.readInt32(exception), exception);
            growth_graph = StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
            members_graph = StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
            new_members_by_source_graph = StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
            languages_graph = StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
            messages_graph = StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
            actions_graph = StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
            top_hours_graph = StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
            weekdays_graph = StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_statsGroupTopPoster object = TL_statsGroupTopPoster.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                top_posters.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_statsGroupTopAdmin object = TL_statsGroupTopAdmin.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                top_admins.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_statsGroupTopInviter object = TL_statsGroupTopInviter.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                top_inviters.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TLRPC.User object = TLRPC.User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
            }
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            period.serializeToStream(stream);
            members.serializeToStream(stream);
            messages.serializeToStream(stream);
            viewers.serializeToStream(stream);
            posters.serializeToStream(stream);
            growth_graph.serializeToStream(stream);
            members_graph.serializeToStream(stream);
            new_members_by_source_graph.serializeToStream(stream);
            languages_graph.serializeToStream(stream);
            messages_graph.serializeToStream(stream);
            actions_graph.serializeToStream(stream);
            top_hours_graph.serializeToStream(stream);
            weekdays_graph.serializeToStream(stream);
            stream.writeInt32(0x1cb5c415);
            int count = top_posters.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                top_posters.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = top_admins.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                top_admins.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = top_inviters.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                top_inviters.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static abstract class StatsGraph extends TLObject {

        public float rate; // custom

        public static StatsGraph TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            StatsGraph result = null;
            switch (constructor) {
                case TL_statsGraph.constructor:
                    result = new TL_statsGraph();
                    break;
                case TL_statsGraphAsync.constructor:
                    result = new TL_statsGraphAsync();
                    break;
                case TL_statsGraphError.constructor:
                    result = new TL_statsGraphError();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in StatsGraph", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_statsGraph extends StatsGraph {
        public static final int constructor = 0x8ea464b6;

        public int flags;
        public TLRPC.TL_dataJSON json;
        public String zoom_token;

        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            json = TLRPC.TL_dataJSON.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 1) != 0) {
                zoom_token = stream.readString(exception);
            }
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            json.serializeToStream(stream);
            if ((flags & 1) != 0) {
                stream.writeString(zoom_token);
            }
        }
    }

    public static class TL_statsGraphAsync extends StatsGraph {
        public static final int constructor = 0x4a27eb2d;

        public String token;

        public void readParams(AbstractSerializedData stream, boolean exception) {
            token = stream.readString(exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(token);
        }
    }

    public static class TL_statsGraphError extends StatsGraph {
        public static final int constructor = 0xbedc9822;

        public String error;

        public void readParams(AbstractSerializedData stream, boolean exception) {
            error = stream.readString(exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(error);
        }
    }

    public static abstract class PostInteractionCounters extends TLObject {

        public static PostInteractionCounters TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            PostInteractionCounters result = null;
            switch (constructor) {
                case TL_postInteractionCountersStory.constructor:
                    result = new TL_postInteractionCountersStory();
                    break;
                case TL_postInteractionCountersMessage.constructor:
                    result = new TL_postInteractionCountersMessage();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in PostInteractionCounters", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_postInteractionCountersStory extends PostInteractionCounters {
        public final static int constructor = 0x8a480e27;

        public int story_id;
        public int views;
        public int forwards;
        public int reactions;

        public void readParams(AbstractSerializedData stream, boolean exception) {
            story_id = stream.readInt32(exception);
            views = stream.readInt32(exception);
            forwards = stream.readInt32(exception);
            reactions = stream.readInt32(exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(story_id);
            stream.writeInt32(views);
            stream.writeInt32(forwards);
            stream.writeInt32(reactions);
        }
    }

    public static class TL_postInteractionCountersMessage extends PostInteractionCounters {
        public static final int constructor = 0xe7058e7f;

        public int msg_id;
        public int views;
        public int forwards;
        public int reactions;

        public void readParams(AbstractSerializedData stream, boolean exception) {
            msg_id = stream.readInt32(exception);
            views = stream.readInt32(exception);
            forwards = stream.readInt32(exception);
            reactions = stream.readInt32(exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(msg_id);
            stream.writeInt32(views);
            stream.writeInt32(forwards);
            stream.writeInt32(reactions);
        }
    }

    public static class TL_messageStats extends TLObject {
        public final static int constructor = 0x7fe91c14;

        public StatsGraph views_graph;
        public StatsGraph reactions_by_emotion_graph;

        public static TL_messageStats TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (TL_messageStats.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_stats_messageStats", constructor));
                } else {
                    return null;
                }
            }
            TL_messageStats result = new TL_messageStats();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            views_graph = StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
            reactions_by_emotion_graph = StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            views_graph.serializeToStream(stream);
            reactions_by_emotion_graph.serializeToStream(stream);
        }
    }

    public static class TL_statsGroupTopPoster extends TLObject {
        public static final int constructor = 0x9d04af9b;

        public long user_id;
        public int messages;
        public int avg_chars;

        public static TL_statsGroupTopPoster TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (TL_statsGroupTopPoster.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_statsGroupTopPoster", constructor));
                } else {
                    return null;
                }
            }
            TL_statsGroupTopPoster result = new TL_statsGroupTopPoster();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            user_id = stream.readInt64(exception);
            messages = stream.readInt32(exception);
            avg_chars = stream.readInt32(exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(user_id);
            stream.writeInt32(messages);
            stream.writeInt32(avg_chars);
        }
    }

    public static class TL_statsDateRangeDays extends TLObject {
        public static final int constructor = 0xb637edaf;

        public int min_date;
        public int max_date;

        public static TL_statsDateRangeDays TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (TL_statsDateRangeDays.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_statsDateRangeDays", constructor));
                } else {
                    return null;
                }
            }
            TL_statsDateRangeDays result = new TL_statsDateRangeDays();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            min_date = stream.readInt32(exception);
            max_date = stream.readInt32(exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(min_date);
            stream.writeInt32(max_date);
        }
    }

    public static class TL_broadcastStats extends TLObject {
        public static int constructor = 0x396ca5fc;

        public TL_statsDateRangeDays period;
        public TL_statsAbsValueAndPrev followers;
        public TL_statsAbsValueAndPrev views_per_post;
        public TL_statsAbsValueAndPrev shares_per_post;
        public TL_statsAbsValueAndPrev reactions_per_post;
        public TL_statsAbsValueAndPrev views_per_story;
        public TL_statsAbsValueAndPrev shares_per_story;
        public TL_statsAbsValueAndPrev reactions_per_story;
        public TL_statsPercentValue enabled_notifications;
        public StatsGraph growth_graph;
        public StatsGraph followers_graph;
        public StatsGraph mute_graph;
        public StatsGraph top_hours_graph;
        public StatsGraph interactions_graph;
        public StatsGraph iv_interactions_graph;
        public StatsGraph views_by_source_graph;
        public StatsGraph new_followers_by_source_graph;
        public StatsGraph languages_graph;
        public StatsGraph reactions_by_emotion_graph;
        public StatsGraph story_interactions_graph;
        public StatsGraph story_reactions_by_emotion_graph;
        public ArrayList<PostInteractionCounters> recent_posts_interactions = new ArrayList<>();

        public static TL_broadcastStats TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (TL_broadcastStats.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_stats_broadcastStats", constructor));
                } else {
                    return null;
                }
            }
            TL_broadcastStats result = new TL_broadcastStats();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            period = TL_statsDateRangeDays.TLdeserialize(stream, stream.readInt32(exception), exception);
            followers = TL_statsAbsValueAndPrev.TLdeserialize(stream, stream.readInt32(exception), exception);
            views_per_post = TL_statsAbsValueAndPrev.TLdeserialize(stream, stream.readInt32(exception), exception);
            shares_per_post = TL_statsAbsValueAndPrev.TLdeserialize(stream, stream.readInt32(exception), exception);
            reactions_per_post = TL_statsAbsValueAndPrev.TLdeserialize(stream, stream.readInt32(exception), exception);
            views_per_story = TL_statsAbsValueAndPrev.TLdeserialize(stream, stream.readInt32(exception), exception);
            shares_per_story = TL_statsAbsValueAndPrev.TLdeserialize(stream, stream.readInt32(exception), exception);
            reactions_per_story = TL_statsAbsValueAndPrev.TLdeserialize(stream, stream.readInt32(exception), exception);
            enabled_notifications = TL_statsPercentValue.TLdeserialize(stream, stream.readInt32(exception), exception);
            growth_graph = StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
            followers_graph = StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
            mute_graph = StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
            top_hours_graph = StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
            interactions_graph = StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
            iv_interactions_graph = StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
            views_by_source_graph = StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
            new_followers_by_source_graph = StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
            languages_graph = StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
            reactions_by_emotion_graph = StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
            story_interactions_graph = StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
            story_reactions_by_emotion_graph = StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                PostInteractionCounters object = PostInteractionCounters.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                recent_posts_interactions.add(object);
            }
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            period.serializeToStream(stream);
            followers.serializeToStream(stream);
            views_per_post.serializeToStream(stream);
            shares_per_post.serializeToStream(stream);
            reactions_per_post.serializeToStream(stream);
            views_per_story.serializeToStream(stream);
            shares_per_story.serializeToStream(stream);
            reactions_per_story.serializeToStream(stream);
            enabled_notifications.serializeToStream(stream);
            growth_graph.serializeToStream(stream);
            followers_graph.serializeToStream(stream);
            mute_graph.serializeToStream(stream);
            top_hours_graph.serializeToStream(stream);
            interactions_graph.serializeToStream(stream);
            iv_interactions_graph.serializeToStream(stream);
            views_by_source_graph.serializeToStream(stream);
            new_followers_by_source_graph.serializeToStream(stream);
            languages_graph.serializeToStream(stream);
            reactions_by_emotion_graph.serializeToStream(stream);
            story_interactions_graph.serializeToStream(stream);
            story_reactions_by_emotion_graph.serializeToStream(stream);
            stream.writeInt32(0x1cb5c415);
            int count = recent_posts_interactions.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                recent_posts_interactions.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_getBroadcastStats extends TLObject {
        public static final int constructor = 0xab42441a;

        public int flags;
        public boolean dark;
        public TLRPC.InputChannel channel;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TL_broadcastStats.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = dark ? (flags | 1) : (flags & ~1);
            stream.writeInt32(flags);
            channel.serializeToStream(stream);
        }
    }

    public static class TL_loadAsyncGraph extends TLObject {
        public static final int constructor = 0x621d5fa0;

        public int flags;
        public String token;
        public long x;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return StatsGraph.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeString(token);
            if ((flags & 1) != 0) {
                stream.writeInt64(x);
            }
        }
    }

    public static class TL_getMegagroupStats extends TLObject {
        public static final int constructor = 0xdcdf8607;

        public int flags;
        public boolean dark;
        public TLRPC.InputChannel channel;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TL_megagroupStats.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = dark ? (flags | 1) : (flags & ~1);
            stream.writeInt32(flags);
            channel.serializeToStream(stream);
        }
    }

    public static class TL_getMessagePublicForwards extends TLObject {
        public static final int constructor = 0x5f150144;

        public TLRPC.InputChannel channel;
        public int msg_id;
        public String offset;
        public int limit;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TL_publicForwards.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            channel.serializeToStream(stream);
            stream.writeInt32(msg_id);
            stream.writeString(offset);
            stream.writeInt32(limit);
        }
    }

    public static class TL_getMessageStats extends TLObject {
        public static final int constructor = 0xb6e0a3f5;

        public int flags;
        public boolean dark;
        public TLRPC.InputChannel channel;
        public int msg_id;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TL_messageStats.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = dark ? (flags | 1) : (flags & ~1);
            stream.writeInt32(flags);
            channel.serializeToStream(stream);
            stream.writeInt32(msg_id);
        }
    }

    public static class TL_getStoryPublicForwards extends TLObject {
        public static final int constructor = 0xa6437ef6;

        public TLRPC.InputPeer peer;
        public int id;
        public String offset;
        public int limit;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TL_publicForwards.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(id);
            stream.writeString(offset);
            stream.writeInt32(limit);
        }
    }

    public static class TL_publicForwards extends TLObject {
        public static final int constructor = 0x93037e20;

        public int flags;
        public int count;
        public ArrayList<PublicForward> forwards = new ArrayList<>();
        public String next_offset;
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static TL_publicForwards TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (TL_publicForwards.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_stats_publicForwards", constructor));
                } else {
                    return null;
                }
            }
            TL_publicForwards result = new TL_publicForwards();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            count = stream.readInt32(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                PublicForward object = PublicForward.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                forwards.add(object);
            }
            if ((flags & 1) != 0) {
                next_offset = stream.readString(exception);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TLRPC.Chat object = TLRPC.Chat.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                chats.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TLRPC.User object = TLRPC.User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
            }
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(count);
            stream.writeInt32(0x1cb5c415);
            int count = forwards.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                forwards.get(a).serializeToStream(stream);
            }
            if ((flags & 1) != 0) {
                stream.writeString(next_offset);
            }
            stream.writeInt32(0x1cb5c415);
            count = chats.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                chats.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static abstract class PublicForward extends TLObject {

        public static PublicForward TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            PublicForward result = null;
            switch (constructor) {
                case TL_stats.TL_publicForwardMessage.constructor:
                    result = new TL_stats.TL_publicForwardMessage();
                    break;
                case TL_stories.TL_publicForwardStory.constructor:
                    result = new TL_stories.TL_publicForwardStory();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in PublicForward", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_publicForwardMessage extends PublicForward {
        public static final int constructor = 0x1f2bf4a;

        public TLRPC.Message message;

        public void readParams(AbstractSerializedData stream, boolean exception) {
            message = TLRPC.Message.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            message.serializeToStream(stream);
        }
    }

    public static class TL_broadcastRevenueStats extends TLObject {
        public static final int constructor = 0x5407e297;

        public StatsGraph top_hours_graph;
        public StatsGraph revenue_graph;
        public TLRPC.TL_broadcastRevenueBalances balances;
        public double usd_rate;

        public static TL_broadcastRevenueStats TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (TL_broadcastRevenueStats.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_stats_broadcastRevenueStats", constructor));
                } else {
                    return null;
                }
            }
            TL_broadcastRevenueStats result = new TL_broadcastRevenueStats();
            result.readParams(stream, exception);
            return result;
        }


        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            top_hours_graph = StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
            revenue_graph = StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
            balances = TLRPC.TL_broadcastRevenueBalances.TLdeserialize(stream, stream.readInt32(exception), exception);
            usd_rate = stream.readDouble(exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            top_hours_graph.serializeToStream(stream);
            revenue_graph.serializeToStream(stream);
            balances.serializeToStream(stream);
            stream.writeDouble(usd_rate);
        }
    }

    public static class TL_broadcastRevenueWithdrawalUrl extends TLObject {
        public static final int constructor = 0xec659737;

        public String url;

        public static TL_broadcastRevenueWithdrawalUrl TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (TL_broadcastRevenueWithdrawalUrl.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_stats_broadcastRevenueWithdrawalUrl", constructor));
                } else {
                    return null;
                }
            }
            TL_broadcastRevenueWithdrawalUrl result = new TL_broadcastRevenueWithdrawalUrl();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            url = stream.readString(exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(url);
        }
    }

    public static class BroadcastRevenueTransaction extends TLObject {
        public static BroadcastRevenueTransaction TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            BroadcastRevenueTransaction result = null;
            switch (constructor) {
                case TL_broadcastRevenueTransactionProceeds.constructor:
                    result = new TL_broadcastRevenueTransactionProceeds();
                    break;
                case TL_broadcastRevenueTransactionWithdrawal.constructor:
                    result = new TL_broadcastRevenueTransactionWithdrawal();
                    break;
                case TL_broadcastRevenueTransactionRefund.constructor:
                    result = new TL_broadcastRevenueTransactionRefund();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in BroadcastRevenueTransaction", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_broadcastRevenueTransactionProceeds extends BroadcastRevenueTransaction {
        public static final int constructor = 0x557e2cc4;

        public long amount;
        public int from_date;
        public int to_date;

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            amount = stream.readInt64(exception);
            from_date = stream.readInt32(exception);
            to_date = stream.readInt32(exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(amount);
            stream.writeInt32(from_date);
            stream.writeInt32(to_date);
        }
    }

    public static class TL_broadcastRevenueTransactionWithdrawal extends BroadcastRevenueTransaction {
        public static final int constructor = 0x5a590978;

        public int flags;
        public boolean pending;
        public boolean failed;
        public long amount;
        public int date;
        public String provider;
        public int transaction_date;
        public String transaction_url;

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            pending = (flags & 1) != 0;
            failed = (flags & 4) != 0;
            amount = stream.readInt64(exception);
            date = stream.readInt32(exception);
            provider = stream.readString(exception);
            if ((flags & 2) != 0) {
                transaction_date = stream.readInt32(exception);
                transaction_url = stream.readString(exception);
            }
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = pending ? (flags | 1) : (flags & ~1);
            flags = failed ? (flags | 1) : (flags & ~1);
            stream.writeInt64(amount);
            stream.writeInt32(date);
            stream.writeString(provider);
            if ((flags & 2) != 0) {
                stream.writeInt32(transaction_date);
                stream.writeString(transaction_url);
            }
        }
    }

    public static class TL_broadcastRevenueTransactionRefund extends BroadcastRevenueTransaction {
        public static final int constructor = 0x42d30d2e;

        public long amount;
        public int from_date;
        public String provider;

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            amount = stream.readInt64(exception);
            from_date = stream.readInt32(exception);
            provider = stream.readString(exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(amount);
            stream.writeInt32(from_date);
            stream.writeString(provider);
        }
    }

    public static class TL_broadcastRevenueTransactions extends TLObject {
        public static final int constructor = 0x87158466;

        public int count;
        public ArrayList<BroadcastRevenueTransaction> transactions = new ArrayList<>();

        public static TL_broadcastRevenueTransactions TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (TL_broadcastRevenueTransactions.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_stats_broadcastRevenueTransactions", constructor));
                } else {
                    return null;
                }
            }
            TL_broadcastRevenueTransactions result = new TL_broadcastRevenueTransactions();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            this.count = stream.readInt32(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int i = 0; i < count; ++i) {
                transactions.add(BroadcastRevenueTransaction.TLdeserialize(stream, stream.readInt32(exception), exception));
            }
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(this.count);
            stream.writeInt32(0x1cb5c415);
            int count = transactions.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                transactions.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_getBroadcastRevenueStats extends TLObject {
        public static final int constructor = 0x75dfb671;

        public int flags;
        public boolean dark;
        public TLRPC.InputChannel channel;

        @Override
        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TL_broadcastRevenueStats.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = dark ? (flags | 1) : (flags & ~1);
            stream.writeInt32(flags);
            channel.serializeToStream(stream);
        }
    }

    public static class TL_getBroadcastRevenueWithdrawalUrl extends TLObject {
        public static final int constructor = 0x2a65ef73;

        public TLRPC.InputChannel channel;
        public TLRPC.InputCheckPasswordSRP password;

        @Override
        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TL_broadcastRevenueWithdrawalUrl.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            channel.serializeToStream(stream);
            password.serializeToStream(stream);
        }
    }

    public static class TL_getBroadcastRevenueTransactions extends TLObject {
        public static final int constructor = 0x69280f;

        public TLRPC.InputChannel channel;
        public int offset;
        public int limit;

        @Override
        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TL_broadcastRevenueTransactions.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            channel.serializeToStream(stream);
            stream.writeInt32(offset);
            stream.writeInt32(limit);
        }
    }

    public static class TL_statsAbsValueAndPrev extends TLObject {
        public static final int constructor = 0xcb43acde;

        public double current;
        public double previous;

        public static TL_statsAbsValueAndPrev TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (TL_statsAbsValueAndPrev.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_statsAbsValueAndPrev", constructor));
                } else {
                    return null;
                }
            }
            TL_statsAbsValueAndPrev result = new TL_statsAbsValueAndPrev();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            current = stream.readDouble(exception);
            previous = stream.readDouble(exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeDouble(current);
            stream.writeDouble(previous);
        }
    }

    public static class TL_statsGroupTopAdmin extends TLObject {
        public static final int constructor = 0xd7584c87;

        public long user_id;
        public int deleted;
        public int kicked;
        public int banned;

        public static TL_statsGroupTopAdmin TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (TL_statsGroupTopAdmin.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_statsGroupTopAdmin", constructor));
                } else {
                    return null;
                }
            }
            TL_statsGroupTopAdmin result = new TL_statsGroupTopAdmin();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            user_id = stream.readInt64(exception);
            deleted = stream.readInt32(exception);
            kicked = stream.readInt32(exception);
            banned = stream.readInt32(exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(user_id);
            stream.writeInt32(deleted);
            stream.writeInt32(kicked);
            stream.writeInt32(banned);
        }
    }

    public static class TL_statsGroupTopInviter extends TLObject {
        public static final int constructor = 0x535f779d;

        public long user_id;
        public int invitations;

        public static TL_statsGroupTopInviter TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (TL_statsGroupTopInviter.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_statsGroupTopInviter", constructor));
                } else {
                    return null;
                }
            }
            TL_statsGroupTopInviter result = new TL_statsGroupTopInviter();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            user_id = stream.readInt64(exception);
            invitations = stream.readInt32(exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(user_id);
            stream.writeInt32(invitations);
        }
    }

    public static class TL_statsPercentValue extends TLObject {
        public static final int constructor = 0xcbce2fe0;

        public double part;
        public double total;

        public static TL_statsPercentValue TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (TL_statsPercentValue.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_statsPercentValue", constructor));
                } else {
                    return null;
                }
            }
            TL_statsPercentValue result = new TL_statsPercentValue();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            part = stream.readDouble(exception);
            total = stream.readDouble(exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeDouble(part);
            stream.writeDouble(total);
        }
    }
}

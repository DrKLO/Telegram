package org.telegram.tgnet.tl;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLMethod;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;

import java.util.ArrayList;

public class TL_forum {
    private TL_forum() {

    }

    public static class TL_messages_getForumTopics extends TLMethod<TLRPC.TL_messages_forumTopics> {
        public static final int constructor = 0x3ba47bff;

        public TLRPC.InputPeer peer;
        public String q;
        public int offset_date;
        public int offset_id;
        public int offset_topic;
        public int limit;

        public TLRPC.TL_messages_forumTopics deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.TL_messages_forumTopics.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);

            int flags = 0;
            flags = setFlag(flags, FLAG_0, q != null);
            stream.writeInt32(flags);

            peer.serializeToStream(stream);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeString(q);
            }
            stream.writeInt32(offset_date);
            stream.writeInt32(offset_id);
            stream.writeInt32(offset_topic);
            stream.writeInt32(limit);
        }
    }

    public static class TL_messages_getForumTopicsByID extends TLMethod<TLRPC.TL_messages_forumTopics> {
        public static final int constructor = 0xaf0a4a08;

        public TLRPC.InputPeer peer;
        public ArrayList<Integer> topics = new ArrayList<>();

        public TLRPC.TL_messages_forumTopics deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.TL_messages_forumTopics.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            Vector.serializeInt(stream, topics);
        }
    }

    public static class TL_messages_editForumTopic extends TLMethod<TLRPC.Updates> {
        public static final int constructor = 0xcecc1134;

        public int flags;
        public TLRPC.InputPeer peer;
        public int topic_id;
        public String title;
        public long icon_emoji_id;
        public boolean closed;
        public boolean hidden;

        public TLRPC.Updates deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, title != null);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            stream.writeInt32(topic_id);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeString(title);
            }
            if (hasFlag(flags, FLAG_1)) {
                stream.writeInt64(icon_emoji_id);
            }
            if (hasFlag(flags, FLAG_2)) {
                stream.writeBool(closed);
            }
            if (hasFlag(flags, FLAG_3)) {
                stream.writeBool(hidden);
            }
        }
    }

    public static class TL_messages_createForumTopic extends TLMethod<TLRPC.Updates> {
        public static final int constructor = 0x2f98c3d5;

        public int flags;
        public boolean title_missing;
        public TLRPC.InputPeer peer;
        public String title;
        public int icon_color;
        public long icon_emoji_id;
        public long random_id;
        public TLRPC.InputPeer send_as;


        public TLRPC.Updates deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);

            flags = setFlag(flags, FLAG_2, send_as != null);
            flags = setFlag(flags, FLAG_4, title_missing);

            stream.writeInt32(flags);

            peer.serializeToStream(stream);
            stream.writeString(title);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeInt32(icon_color);
            }
            if (hasFlag(flags, FLAG_3)) {
                stream.writeInt64(icon_emoji_id);
            }
            stream.writeInt64(random_id);
            if (hasFlag(flags, FLAG_2)) {
                send_as.serializeToStream(stream);
            }
        }
    }

    public static class TL_messages_deleteTopicHistory extends TLMethod<TLRPC.TL_messages_affectedHistory> {
        public static final int constructor = 0xd2816f10;

        public TLRPC.InputPeer peer;
        public int top_msg_id;

        public TLRPC.TL_messages_affectedHistory deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.TL_messages_affectedHistory.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(top_msg_id);
        }
    }

    public static class TL_messages_updatePinnedForumTopic extends TLObject {
        public static final int constructor = 0x175df251;

        public TLRPC.InputPeer peer;
        public int topic_id;
        public boolean pinned;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(topic_id);
            stream.writeBool(pinned);
        }
    }

    public static class TL_messages_reorderPinnedForumTopics extends TLObject {
        public static final int constructor = 0xe7841f0;

        public boolean force;
        public TLRPC.InputPeer peer;
        public ArrayList<Integer> order = new ArrayList<>();

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, force);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            Vector.serializeInt(stream, order);
        }
    }
}

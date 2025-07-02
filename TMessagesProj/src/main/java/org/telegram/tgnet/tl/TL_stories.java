package org.telegram.tgnet.tl;

import androidx.annotation.NonNull;

import org.telegram.messenger.DialogObject;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.tgnet.tl.TL_stats.TL_statsPercentValue;
import org.telegram.ui.Stories.recorder.StoryPrivacyBottomSheet;

import java.util.ArrayList;

public class TL_stories {
    public static class TL_stories_storyViews extends TLObject {
        public static final int constructor = 0xde9eed1d;

        public ArrayList<StoryViews> views = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static TL_stories_storyViews TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (TL_stories_storyViews.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_stories_storyViews", constructor));
                } else {
                    return null;
                }
            }
            TL_stories_storyViews result = new TL_stories_storyViews();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            views = Vector.deserialize(stream, StoryViews::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, views);
            Vector.serialize(stream, users);
        }
    }

    public static class StoryView extends TLObject {

        public int flags;
        public boolean blocked;
        public boolean blocked_my_stories_from;
        public long user_id;
        public int date;
        public TLRPC.Reaction reaction;
        public TLRPC.Message message;
        public TLRPC.Peer peer_id;
        public StoryItem story;

        public static StoryView TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            StoryView result = null;
            switch (constructor) {
                case TL_storyView.constructor:
                    result = new TL_storyView();
                    break;
                case TL_storyViewPublicForward.constructor:
                    result = new TL_storyViewPublicForward();
                    break;
                case TL_storyViewPublicRepost.constructor:
                    result = new TL_storyViewPublicRepost();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in StoryView", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_storyView extends StoryView {
        public static final int constructor = 0xb0bdeac5;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            blocked = (flags & 1) != 0;
            blocked_my_stories_from = (flags & 2) != 0;
            user_id = stream.readInt64(exception);
            date = stream.readInt32(exception);
            if ((flags & 4) != 0) {
                reaction = TLRPC.Reaction.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = blocked ? (flags | 1) : (flags &~ 1);
            flags = blocked_my_stories_from ? (flags | 2) : (flags &~ 2);
            stream.writeInt32(flags);
            stream.writeInt64(user_id);
            stream.writeInt32(date);
            if ((flags & 4) != 0) {
                reaction.serializeToStream(stream);
            }
        }
    }

    public static class TL_storyViewPublicForward extends StoryView {
        public static final int constructor = 0x9083670b;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            blocked = (flags & 1) != 0;
            blocked_my_stories_from = (flags & 2) != 0;
            message = TLRPC.Message.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = blocked ? (flags | 1) : (flags &~ 1);
            flags = blocked_my_stories_from ? (flags | 2) : (flags &~ 2);
            stream.writeInt32(flags);
            message.serializeToStream(stream);
        }
    }

    public static class TL_storyViewPublicRepost extends StoryView {
        public static final int constructor = 0xbd74cf49;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            blocked = (flags & 1) != 0;
            blocked_my_stories_from = (flags & 2) != 0;
            peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            story = StoryItem.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = blocked ? (flags | 1) : (flags &~ 1);
            flags = blocked_my_stories_from ? (flags | 2) : (flags &~ 2);
            stream.writeInt32(flags);
            peer_id.serializeToStream(stream);
            story.serializeToStream(stream);
        }
    }

    public static abstract class PeerStories extends TLObject {

        public int flags;
        public TLRPC.Peer peer;
        public int max_read_id;
        public ArrayList<StoryItem> stories = new ArrayList<>();
        public boolean checkedExpired; //custom

        public static PeerStories TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            PeerStories result = null;
            switch (constructor) {
                case 0x9a35e999:
                    result = new TL_peerStories();
                    break;
                case 0x8611a200:
                    result = new TL_peerStories_layer162();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in PeerStories", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_peerStories extends PeerStories {
        public static final int constructor = 0x9a35e999;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 1) != 0) {
                max_read_id = stream.readInt32(exception);
            }
            stories = Vector.deserialize(stream, StoryItem::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            if ((flags & 1) != 0) {
                stream.writeInt32(max_read_id);
            }
            Vector.serialize(stream, stories);
        }
    }

    public static class TL_peerStories_layer162 extends TL_peerStories {
        public static final int constructor = 0x8611a200;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            long user_id = stream.readInt64(exception);
            peer = new TLRPC.TL_peerUser();
            peer.user_id = user_id;
            if ((flags & 1) != 0) {
                max_read_id = stream.readInt32(exception);
            }
            stories = Vector.deserialize(stream, StoryItem::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt64(peer.user_id);
            if ((flags & 1) != 0) {
                stream.writeInt32(max_read_id);
            }
            Vector.serialize(stream, stories);
        }
    }

    public static class TL_stories_peerStories extends TLObject {
        public static final int constructor = 0xcae68768;

        public PeerStories stories;
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static TL_stories_peerStories TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (TL_stories_peerStories.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_stories_peerStories", constructor));
                } else {
                    return null;
                }
            }
            TL_stories_peerStories result = new TL_stories_peerStories();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            stories = PeerStories.TLdeserialize(stream, stream.readInt32(exception), exception);
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stories.serializeToStream(stream);
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);
        }
    }

    public static abstract class stories_AllStories extends TLObject {

        public static stories_AllStories TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            stories_AllStories result = null;
            switch (constructor) {
                case 0x1158fe3e:
                    result = new TL_stories_allStoriesNotModified();
                    break;
                case 0x6efc5e81:
                    result = new TL_stories_allStories();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in stories_AllStories", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_stories_allStoriesNotModified extends stories_AllStories {
        public static final int constructor = 0x1158fe3e;

        public int flags;
        public String state;
        public TL_storiesStealthMode stealth_mode;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            state = stream.readString(exception);
            stealth_mode = TL_storiesStealthMode.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeString(state);
            stealth_mode.serializeToStream(stream);
        }
    }

    public static class TL_stories_allStories extends stories_AllStories {
        public static final int constructor = 0x6efc5e81;

        public int flags;
        public boolean has_more;
        public int count;
        public String state;
        public ArrayList<PeerStories> peer_stories = new ArrayList<>();
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();
        public TL_storiesStealthMode stealth_mode;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            has_more = (flags & 1) != 0;
            count = stream.readInt32(exception);
            state = stream.readString(exception);
            peer_stories = Vector.deserialize(stream, PeerStories::TLdeserialize, exception);
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
            stealth_mode = TL_storiesStealthMode.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = has_more ? (flags | 1) : (flags &~ 1);
            stream.writeInt32(flags);
            stream.writeInt32(count);
            stream.writeString(state);
            Vector.serialize(stream, peer_stories);
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);
            stealth_mode.serializeToStream(stream);
        }
    }

    public static class canSendStoryCount extends TLObject {
        public static final int constructor = 0xc387c04e;

        public int count_remains;

        public static canSendStoryCount TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (canSendStoryCount.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in canSendStoryCount", constructor));
                } else {
                    return null;
                }
            }
            canSendStoryCount result = new canSendStoryCount();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(count_remains);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            count_remains = stream.readInt32(exception);
        }
    }

    public static class TL_stories_canSendStory extends TLObject {
        public static final int constructor = 0x30eb63f0;

        public TLRPC.InputPeer peer;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return canSendStoryCount.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
        }
    }

    public static class TL_stories_sendStory extends TLObject {
        public static final int constructor = 0xe4e6694b;

        public int flags;
        public boolean pinned;
        public boolean noforwards;
        public boolean fwd_modified;
        public TLRPC.InputPeer peer;
        public TLRPC.InputMedia media;
        public ArrayList<MediaArea> media_areas = new ArrayList<>();
        public String caption;
        public ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        public ArrayList<TLRPC.InputPrivacyRule> privacy_rules = new ArrayList<>();
        public long random_id;
        public int period;
        public TLRPC.InputPeer fwd_from_id;
        public int fwd_from_story;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = pinned ? (flags | 4) : (flags &~ 4);
            flags = noforwards ? (flags | 16) : (flags &~ 16);
            flags = fwd_modified ? (flags | 128) : (flags &~ 128);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            media.serializeToStream(stream);
            if ((flags & 32) != 0) {
                Vector.serialize(stream, media_areas);
            }
            if ((flags & 1) != 0) {
                stream.writeString(caption);
            }
            if ((flags & 2) != 0) {
                Vector.serialize(stream, entities);
            }
            Vector.serialize(stream, privacy_rules);
            stream.writeInt64(random_id);
            if ((flags & 8) != 0) {
                stream.writeInt32(period);
            }
            if ((flags & 64) != 0) {
                fwd_from_id.serializeToStream(stream);
            }
            if ((flags & 64) != 0) {
                stream.writeInt32(fwd_from_story);
            }
        }
    }

    public static class TL_stories_deleteStories extends TLObject {
        public static final int constructor = 0xae59db5f;

        public TLRPC.InputPeer peer;
        public ArrayList<Integer> id = new ArrayList<>();

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return Vector.TLDeserializeInt(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            Vector.serializeInt(stream, id);
        }
    }

    public static class togglePinned extends TLObject {
        public static final int constructor = 0x9a75a1ef;

        public TLRPC.InputPeer peer;
        public ArrayList<Integer> id = new ArrayList<>();
        public boolean pinned;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return Vector.TLDeserializeInt(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            Vector.serializeInt(stream, id);
            stream.writeBool(pinned);
        }
    }

    public static class TL_stories_editStory extends TLObject {
        public static final int constructor = 0xb583ba46;

        public int flags;
        public TLRPC.InputPeer peer;
        public int id;
        public TLRPC.InputMedia media;
        public ArrayList<MediaArea> media_areas = new ArrayList<>();
        public String caption;
        public ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        public ArrayList<TLRPC.InputPrivacyRule> privacy_rules = new ArrayList<>();

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            stream.writeInt32(id);
            if ((flags & 1) != 0) {
                media.serializeToStream(stream);
            }
            if ((flags & 8) != 0) {
                Vector.serialize(stream, media_areas);
            }
            if ((flags & 2) != 0) {
                stream.writeString(caption);
            }
            if ((flags & 2) != 0) {
                Vector.serialize(stream, entities);
            }
            if ((flags & 4) != 0) {
                Vector.serialize(stream, privacy_rules);
            }
        }
    }

    public static class TL_stories_getAllStories extends TLObject {
        public static final int constructor = 0xeeb0d625;

        public int flags;
        public boolean include_hidden;
        public boolean next;
        public String state;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return stories_AllStories.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = next ? (flags | 2) : (flags &~ 2);
            flags = include_hidden ? (flags | 4) : (flags &~ 4);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                stream.writeString(state);
            }
        }
    }

    public static class TL_stories_togglePeerStoriesHidden extends TLObject {
        public static final int constructor = 0xbd0415c4;

        public TLRPC.InputPeer peer;
        public boolean hidden;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeBool(hidden);
        }
    }

    public static class TL_stories_stories extends TLObject {
        public static final int constructor = 0x63c3dd0a;

        public int flags;
        public int count;
        public ArrayList<StoryItem> stories = new ArrayList<>();
        public ArrayList<Integer> pinned_to_top = new ArrayList<>();
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static TL_stories_stories TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (TL_stories_stories.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_stories_stories", constructor));
                } else {
                    return null;
                }
            }
            TL_stories_stories result = new TL_stories_stories();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            count = stream.readInt32(exception);
            stories = Vector.deserialize(stream, StoryItem::TLdeserialize, exception);
            if ((flags & 1) != 0) {
                pinned_to_top = Vector.deserializeInt(stream, exception);
            }
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(count);
            Vector.serialize(stream, stories);
            if ((flags & 1) != 0) {
                Vector.serializeInt(stream, pinned_to_top);
            }
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);
        }
    }

    public static class TL_stories_getPeerStories extends TLObject {
        public static final int constructor = 0x2c4ada50;

        public TLRPC.InputPeer peer;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_stories_peerStories.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
        }
    }

    public static class TL_updateStory extends TLRPC.Update {
        public static final int constructor = 0x75b3b798;

        public TLRPC.Peer peer;
        public StoryItem story;

        public void readParams(InputSerializedData stream, boolean exception) {
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            story = StoryItem.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            story.serializeToStream(stream);
        }
    }

    public static class TL_stories_getPinnedStories extends TLObject {
        public static final int constructor = 0x5821a5dc;

        public TLRPC.InputPeer peer;
        public int offset_id;
        public int limit;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_stories_stories.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(offset_id);
            stream.writeInt32(limit);
        }
    }

    public static class TL_stories_getStoriesArchive extends TLObject {
        public static final int constructor = 0xb4352016;

        public TLRPC.InputPeer peer;
        public int offset_id;
        public int limit;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_stories_stories.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(offset_id);
            stream.writeInt32(limit);
        }
    }

    public static class TL_updateReadStories extends TLRPC.Update {
        public static final int constructor = 0xf74e932b;

        public TLRPC.Peer peer;
        public int max_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            max_id = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(max_id);
        }
    }

    public static class StoryViewsList extends TLObject {

        public int flags;
        public int count;
        public int views_count;
        public int forwards_count;
        public int reactions_count;
        public ArrayList<StoryView> views = new ArrayList<>();
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();
        public String next_offset = "";

        public static StoryViewsList TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            StoryViewsList result = null;
            switch (constructor) {
                case TL_storyViewsList.constructor:
                    result = new TL_storyViewsList();
                    break;
                case TL_storyViewsList_layer167.constructor:
                    result = new TL_storyViewsList_layer167();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in StoryViewsList", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_storyViewsList extends StoryViewsList {
        public static final int constructor = 0x59d78fc5;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            count = stream.readInt32(exception);
            views_count = stream.readInt32(exception);
            forwards_count = stream.readInt32(exception);
            reactions_count = stream.readInt32(exception);
            views = Vector.deserialize(stream, StoryView::TLdeserialize, exception);
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
            if ((flags & 1) != 0) {
                next_offset = stream.readString(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(count);
            stream.writeInt32(views_count);
            stream.writeInt32(forwards_count);
            stream.writeInt32(reactions_count);
            Vector.serialize(stream, views);
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);
            if ((flags & 1) != 0) {
                stream.writeString(next_offset);
            }
        }
    }


    public static class TL_storyViewsList_layer167 extends StoryViewsList {
        public static final int constructor = 0x46e9b9ec;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            count = stream.readInt32(exception);
            reactions_count = stream.readInt32(exception);
            views = Vector.deserialize(stream, StoryView::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
            if ((flags & 1) != 0) {
                next_offset = stream.readString(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(count);
            stream.writeInt32(reactions_count);
            Vector.serialize(stream, views);
            Vector.serialize(stream, users);
            if ((flags & 1) != 0) {
                stream.writeString(next_offset);
            }
        }
    }

    public static class TL_stories_readStories extends TLObject {
        public static final int constructor = 0xa556dac8;

        public TLRPC.InputPeer peer;
        public int max_id;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return Vector.TLDeserializeInt(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(max_id);
        }
    }

    public static class TL_stories_incrementStoryViews extends TLObject {
        public static final int constructor = 0xb2028afb;

        public TLRPC.InputPeer peer;
        public ArrayList<Integer> id = new ArrayList<>();

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            Vector.serializeInt(stream, id);
        }
    }

    public static class TL_stories_getStoryViewsList extends TLObject {
        public static final int constructor = 0x7ed23c57;

        public int flags;
        public boolean just_contacts;
        public boolean reactions_first;
        public boolean forwards_first;
        public TLRPC.InputPeer peer;
        public String q;
        public int id;
        public String offset;
        public int limit;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return StoryViewsList.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = just_contacts ? (flags | 1) : (flags &~ 1);
            flags = reactions_first ? (flags | 4) : (flags &~ 4);
            flags = forwards_first ? (flags | 8) : (flags &~ 8);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            if ((flags & 2) != 0) {
                stream.writeString(q);
            }
            stream.writeInt32(id);
            stream.writeString(offset);
            stream.writeInt32(limit);
        }
    }

    public static class TL_stories_getStoriesByID extends TLObject {
        public static final int constructor = 0x5774ca74;

        public TLRPC.InputPeer peer;
        public ArrayList<Integer> id = new ArrayList<>();

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_stories_stories.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            Vector.serializeInt(stream, id);
        }
    }

    public static class TL_foundStory extends TLObject {
        public static final int constructor = 0xe87acbc0;

        public TLRPC.Peer peer;
        public StoryItem storyItem;

        public static TL_foundStory TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (TL_foundStory.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_foundStory", constructor));
                } else {
                    return null;
                }
            }
            TL_foundStory result = new TL_foundStory();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            storyItem = StoryItem.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            storyItem.serializeToStream(stream);
        }
    }

    public static class TL_foundStories extends TLObject {
        public static final int constructor = 0xe2de7737;

        public int flags;
        public int count;
        public ArrayList<TL_foundStory> stories = new ArrayList<>();
        public String next_offset;
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static TL_foundStories TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (TL_foundStories.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_foundStories", constructor));
                } else {
                    return null;
                }
            }
            TL_foundStories result = new TL_foundStories();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            this.count = stream.readInt32(exception);
            stories = Vector.deserialize(stream, TL_foundStory::TLdeserialize, exception);
            if ((flags & 1) != 0) {
                next_offset = stream.readString(exception);
            }
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(this.count);
            Vector.serialize(stream, stories);
            if ((flags & 1) != 0) {
                stream.writeString(next_offset);
            }
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);
        }
    }

    public static class TL_stories_searchPosts extends TLObject {
        public static final int constructor = 0xd1810907;

        public int flags;
        public String hashtag;
        public MediaArea area;
        public TLRPC.InputPeer peer;
        public String offset;
        public int limit;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_foundStories.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                stream.writeString(hashtag);
            }
            if ((flags & 2) != 0) {
                area.serializeToStream(stream);
            }
            if ((flags & 4) != 0) {
                peer.serializeToStream(stream);
            }
            stream.writeString(offset);
            stream.writeInt32(limit);
        }
    }

    public static class TL_stories_getStoriesViews extends TLObject {
        public static final int constructor = 0x28e16cc8;

        public TLRPC.InputPeer peer;
        public ArrayList<Integer> id = new ArrayList<>();

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_stories_storyViews.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            Vector.serializeInt(stream, id);
        }
    }

    public static class TL_exportedStoryLink extends TLObject {
        public static final int constructor = 0x3fc9053b;

        public String link;

        public static TL_exportedStoryLink TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (TL_exportedStoryLink.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_exportedStoryLink", constructor));
                } else {
                    return null;
                }
            }
            TL_exportedStoryLink result = new TL_exportedStoryLink();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            link = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(link);
        }
    }

    public static class TL_stories_exportStoryLink extends TLObject {
        public static final int constructor = 0x7b8def20;

        public TLRPC.InputPeer peer;
        public int id;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_exportedStoryLink.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(id);
        }
    }

    public static class TL_stories_report extends TLObject {
        public static final int constructor = 0x19d8eb45;

        public TLRPC.InputPeer peer;
        public ArrayList<Integer> id = new ArrayList<>();
        public byte[] option;
        public String message;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.ReportResult.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            Vector.serialize(stream, stream::writeInt32, id);
            stream.writeByteArray(option);
            stream.writeString(message);
        }
    }

    public static class TL_stories_getAllReadPeerStories extends TLObject {
        public static final int constructor = 0x9b5ae7f9;


        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_stories_getPeerMaxIDs extends TLObject {
        public static final int constructor = 0x535983c3;

        public ArrayList<TLRPC.InputPeer> id = new ArrayList<>();

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return Vector.TLDeserializeInt(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, id);
        }
    }

    public static class TL_updateStoriesStealthMode extends TLRPC.Update {
        public static final int constructor = 0x2c084dc1;

        public TL_storiesStealthMode stealth_mode;

        public void readParams(InputSerializedData stream, boolean exception) {
            stealth_mode = TL_storiesStealthMode.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stealth_mode.serializeToStream(stream);
        }
    }

    public static class TL_storiesStealthMode extends TLObject {
        public static final int constructor = 0x712e27fd;

        public int flags;
        public int active_until_date;
        public int cooldown_until_date;

        public static TL_storiesStealthMode TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (TL_storiesStealthMode.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_storiesStealthMode", constructor));
                } else {
                    return null;
                }
            }
            TL_storiesStealthMode result = new TL_storiesStealthMode();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                active_until_date = stream.readInt32(exception);
            }
            if ((flags & 2) != 0) {
                cooldown_until_date = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                stream.writeInt32(active_until_date);
            }
            if ((flags & 2) != 0) {
                stream.writeInt32(cooldown_until_date);
            }
        }
    }

    public static class TL_stories_activateStealthMode extends TLObject {
        public static final int constructor = 0x57bbd166;

        public int flags;
        public boolean past;
        public boolean future;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = past ? (flags | 1) : (flags &~ 1);
            flags = future ? (flags | 2) : (flags &~ 2);
            stream.writeInt32(flags);
        }
    }

    public static class TL_stories_sendReaction extends TLObject {
        public static final int constructor = 0x7fd736b2;

        public int flags;
        public boolean add_to_recent;
        public TLRPC.InputPeer peer;
        public int story_id;
        public TLRPC.Reaction reaction;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = add_to_recent ? (flags | 1) : (flags &~ 1);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            stream.writeInt32(story_id);
            reaction.serializeToStream(stream);
        }
    }

    public static class TL_stories_getChatsToSend extends TLObject {
        public static final int constructor = 0xa56a8b60;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.TL_messages_chats.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_myBoost extends TLObject {
        public static int constructor = 0xc448415c;

        public int flags;
        public int slot;
        public TLRPC.Peer peer;
        public int date;
        public int expires;
        public int cooldown_until_date;

        public static TL_myBoost TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (TL_myBoost.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_myBoost", constructor));
                } else {
                    return null;
                }
            }
            TL_myBoost result = new TL_myBoost();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            slot = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            date = stream.readInt32(exception);
            expires = stream.readInt32(exception);
            if ((flags & 2) != 0) {
                cooldown_until_date = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(slot);
            if ((flags & 1) != 0) {
                peer.serializeToStream(stream);
            }
            stream.writeInt32(date);
            stream.writeInt32(expires);
            if ((flags & 2) != 0) {
                stream.writeInt32(cooldown_until_date);
            }
        }
    }

    public static class TL_premium_myBoosts extends TLObject {
        public static int constructor = 0x9ae228e2;

        public ArrayList<TL_myBoost> my_boosts = new ArrayList<>();
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static TL_premium_myBoosts TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (TL_premium_myBoosts.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_premium_myBoosts", constructor));
                } else {
                    return null;
                }
            }
            TL_premium_myBoosts result = new TL_premium_myBoosts();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            my_boosts = Vector.deserialize(stream, TL_myBoost::TLdeserialize, exception);
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, my_boosts);
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);
        }
    }

    public static class TL_premium_getMyBoosts extends TLObject {
        public static int constructor = 0xbe77b4a;


        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_premium_myBoosts.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_premium_boostsStatus extends TLObject {
        public static int constructor = 0x4959427a;

        public int flags;
        public boolean my_boost;
        public int level;
        public int current_level_boosts;
        public int boosts;
        public int gift_boosts;
        public int next_level_boosts;
        public TL_statsPercentValue premium_audience;
        public String boost_url;
        public ArrayList<PrepaidGiveaway> prepaid_giveaways = new ArrayList<>();
        public ArrayList<Integer> my_boost_slots = new ArrayList<>();

        public static TL_premium_boostsStatus TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (TL_premium_boostsStatus.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_premium_boostsStatus", constructor));
                } else {
                    return null;
                }
            }
            TL_premium_boostsStatus result = new TL_premium_boostsStatus();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            my_boost = (flags & 4) != 0;
            level = stream.readInt32(exception);
            current_level_boosts = stream.readInt32(exception);
            boosts = stream.readInt32(exception);
            if ((flags & 16) != 0) {
                gift_boosts = stream.readInt32(exception);
            }
            if ((flags & 1) != 0) {
                next_level_boosts = stream.readInt32(exception);
            }
            if ((flags & 2) != 0) {
                premium_audience = TL_stats.TL_statsPercentValue.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            boost_url = stream.readString(exception);
            if ((flags & 8) != 0) {
                prepaid_giveaways = Vector.deserialize(stream, PrepaidGiveaway::TLdeserialize, exception);
            }
            if ((flags & 4) != 0) {
                my_boost_slots = Vector.deserializeInt(stream, exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = my_boost ? (flags | 4) : (flags &~ 4);
            stream.writeInt32(flags);
            stream.writeInt32(level);
            stream.writeInt32(current_level_boosts);
            stream.writeInt32(boosts);
            if ((flags & 16) != 0) {
                stream.writeInt32(gift_boosts);
            }
            if ((flags & 1) != 0) {
                stream.writeInt32(next_level_boosts);
            }
            if ((flags & 2) != 0) {
                premium_audience.serializeToStream(stream);
            }
            stream.writeString(boost_url);
            if ((flags & 8) != 0) {
                Vector.serialize(stream, prepaid_giveaways);
            }
            if ((flags & 4) != 0) {
                Vector.serializeInt(stream, my_boost_slots);
            }
        }
    }

    public static class TL_premium_getBoostsStatus extends TLObject {
        public static int constructor = 0x42f1f61;

        public TLRPC.InputPeer peer;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_premium_boostsStatus.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
        }
    }

    public static class TL_premium_applyBoost extends TLObject {
        public static int constructor = 0x6b7da746;

        public int flags;
        public ArrayList<Integer> slots = new ArrayList<>();
        public TLRPC.InputPeer peer;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_premium_myBoosts.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                Vector.serializeInt(stream, slots);
            }
            peer.serializeToStream(stream);
        }
    }

    public static class Boost extends TLObject {
        public static int constructor = 0x2a1c8c71;
        public static final long NO_USER_ID = -1L; //custom

        public int flags;
        public boolean gift;
        public boolean giveaway;
        public boolean unclaimed;
        public String id;
        public long user_id = NO_USER_ID;
        public int giveaway_msg_id;
        public int date;
        public int expires;
        public String used_gift_slug;
        public int multiplier;
        public long stars;

        public static Boost TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            Boost result = null;
            switch (constructor) {
                case TL_boost.constructor:
                    result = new TL_boost();
                    break;
                case TL_boost_layer186.constructor:
                    result = new TL_boost_layer186();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in Boost", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_boost extends Boost {
        public static final int constructor = 0x4b3e14d6;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            gift = (flags & 2) != 0;
            giveaway = (flags & 4) != 0;
            unclaimed = (flags & 8) != 0;
            id = stream.readString(exception);
            if ((flags & 1) != 0) {
                user_id = stream.readInt64(exception);
            }
            if ((flags & 4) != 0) {
                giveaway_msg_id = stream.readInt32(exception);
            }
            date = stream.readInt32(exception);
            expires = stream.readInt32(exception);
            if ((flags & 16) != 0) {
                used_gift_slug = stream.readString(exception);
            }
            if ((flags & 32) != 0) {
                multiplier = stream.readInt32(exception);
            }
            if ((flags & 64) != 0) {
                stars = stream.readInt64(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = gift ? (flags | 2) : (flags &~ 2);
            flags = giveaway ? (flags | 4) : (flags &~ 4);
            flags = unclaimed ? (flags | 8) : (flags &~ 8);
            stream.writeInt32(flags);
            stream.writeString(id);
            if ((flags & 1) != 0) {
                stream.writeInt64(user_id);
            }
            if ((flags & 4) != 0) {
                stream.writeInt32(giveaway_msg_id);
            }
            stream.writeInt32(date);
            stream.writeInt32(expires);
            if ((flags & 16) != 0) {
                stream.writeString(used_gift_slug);
            }
            if ((flags & 32) != 0) {
                stream.writeInt32(multiplier);
            }
            if ((flags & 64) != 0) {
                stream.writeInt64(stars);
            }
        }
    }

    public static class TL_boost_layer186 extends TL_boost {
        public static final int constructor = 0x2a1c8c71;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            gift = (flags & 2) != 0;
            giveaway = (flags & 4) != 0;
            unclaimed = (flags & 8) != 0;
            id = stream.readString(exception);
            if ((flags & 1) != 0) {
                user_id = stream.readInt64(exception);
            }
            if ((flags & 4) != 0) {
                giveaway_msg_id = stream.readInt32(exception);
            }
            date = stream.readInt32(exception);
            expires = stream.readInt32(exception);
            if ((flags & 16) != 0) {
                used_gift_slug = stream.readString(exception);
            }
            if ((flags & 32) != 0) {
                multiplier = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = gift ? (flags | 2) : (flags &~ 2);
            flags = giveaway ? (flags | 4) : (flags &~ 4);
            flags = unclaimed ? (flags | 8) : (flags &~ 8);
            stream.writeInt32(flags);
            stream.writeString(id);
            if ((flags & 1) != 0) {
                stream.writeInt64(user_id);
            }
            if ((flags & 4) != 0) {
                stream.writeInt32(giveaway_msg_id);
            }
            stream.writeInt32(date);
            stream.writeInt32(expires);
            if ((flags & 16) != 0) {
                stream.writeString(used_gift_slug);
            }
            if ((flags & 32) != 0) {
                stream.writeInt32(multiplier);
            }
        }
    }

    public static class TL_premium_boostsList extends TLObject {
        public static int constructor = 0x86f8613c;

        public int flags;
        public int count;
        public ArrayList<Boost> boosts = new ArrayList<>();
        public String next_offset;
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static TL_premium_boostsList TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (TL_premium_boostsList.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_premium_boostsList", constructor));
                } else {
                    return null;
                }
            }
            TL_premium_boostsList result = new TL_premium_boostsList();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            count = stream.readInt32(exception);
            boosts = Vector.deserialize(stream, Boost::TLdeserialize, exception);
            if ((flags & 1) != 0) {
                next_offset = stream.readString(exception);
            }
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(count);
            Vector.serialize(stream, boosts);
            if ((flags & 1) != 0) {
                stream.writeString(next_offset);
            }
            Vector.serialize(stream, users);
        }
    }

    public static class TL_premium_getBoostsList extends TLObject {
        public static int constructor = 0x60f67660;

        public int flags;
        public boolean gifts;
        public TLRPC.InputPeer peer;
        public String offset;
        public int limit;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_premium_boostsList.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = gifts ? (flags | 1) : (flags &~ 1);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            stream.writeString(offset);
            stream.writeInt32(limit);
        }
    }

    public static abstract class StoryItem extends TLObject {

        public int flags;
        public boolean pinned;
        public boolean isPublic;
        public boolean close_friends;
        public boolean contacts;
        public boolean selected_contacts;
        public boolean noforwards;
        public boolean min;
        public boolean out;
        public int id;
        public int date;
        public TLRPC.Peer from_id;
        public StoryFwdHeader fwd_from;
        public int expire_date;
        public String caption;
        public boolean edited;
        public ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        public TLRPC.MessageMedia media;
        public ArrayList<MediaArea> media_areas = new ArrayList();
        public ArrayList<TLRPC.PrivacyRule> privacy = new ArrayList<>();
        public StoryViews views;
        public TLRPC.Reaction sent_reaction;
        public long lastUpdateTime; //custom
        public String attachPath; //custom
        public String firstFramePath; //custom
        public long dialogId;// custom
        public boolean justUploaded;// custom
        public int messageId;//custom
        public int messageType;//custom
        public int fileReference;
        public String detectedLng; //custom
        public String translatedLng; //custom
        public boolean translated; //custom
        public TLRPC.TL_textWithEntities translatedText; //custom
        public StoryPrivacyBottomSheet.StoryPrivacy parsedPrivacy; //custom

        public static StoryItem TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            StoryItem result = null;
            switch (constructor) {
                case TL_storyItem.constructor:
                    result = new TL_storyItem();
                    break;
                case TL_storyItem_layer174.constructor:
                    result = new TL_storyItem_layer174();
                    break;
                case TL_storyItem_layer166.constructor:
                    result = new TL_storyItem_layer166();
                    break;
                case TL_storyItem_layer160.constructor:
                    result = new TL_storyItem_layer160();
                    break;
                case TL_storyItemDeleted.constructor:
                    result = new TL_storyItemDeleted();
                    break;
                case TL_storyItemSkipped.constructor:
                    result = new TL_storyItemSkipped();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in StoryItem", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static abstract class StoryViews extends TLObject {

        public int flags;
        public int views_count;
        public int reactions_count;
        public ArrayList<Long> recent_viewers = new ArrayList<>();
        public boolean has_viewers;
        public int forwards_count;
        public ArrayList<TLRPC.ReactionCount> reactions = new ArrayList<>();

        public static StoryViews TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            StoryViews result = null;
            switch (constructor) {
                case 0xd36760cf:
                    result = new TL_storyViews_layer160();
                    break;
                case 0xc64c0b97:
                    result = new TL_storyViews_layer161();
                    break;
                case 0x8d595cd6:
                    result = new TL_storyViews();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in StoryViews", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_storyViews_layer160 extends StoryViews {
        public static final int constructor = 0xd36760cf;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            views_count = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                recent_viewers = Vector.deserializeLong(stream, exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(views_count);
            if ((flags & 1) != 0) {
                Vector.serializeLong(stream, recent_viewers);
            }
        }
    }

    public static class TL_storyViews_layer161 extends StoryViews {
        public static final int constructor = 0xc64c0b97;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            views_count = stream.readInt32(exception);
            reactions_count = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                recent_viewers = Vector.deserializeLong(stream, exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(views_count);
            stream.writeInt32(reactions_count);
            if ((flags & 1) != 0) {
                Vector.serializeLong(stream, recent_viewers);
            }
        }
    }

    public static class TL_storyViews extends StoryViews {
        public static final int constructor = 0x8d595cd6;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            has_viewers = (flags & 2) != 0;
            views_count = stream.readInt32(exception);
            if ((flags & 4) != 0) {
                forwards_count = stream.readInt32(exception);
            }
            if ((flags & 8) != 0) {
                reactions = Vector.deserialize(stream, TLRPC.ReactionCount::TLdeserialize, exception);
            }
            if ((flags & 16) != 0) {
                reactions_count = stream.readInt32(exception);
            }
            if ((flags & 1) != 0) {
                recent_viewers = Vector.deserializeLong(stream, exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = has_viewers ? (flags | 2) : (flags & ~2);
            stream.writeInt32(flags);
            stream.writeInt32(views_count);
            if ((flags & 4) != 0) {
                stream.writeInt32(forwards_count);
            }
            if ((flags & 8) != 0) {
                Vector.serialize(stream, reactions);
            }
            if ((flags & 16) != 0) {
                stream.writeInt32(reactions_count);
            }
            if ((flags & 1) != 0) {
                Vector.serializeLong(stream, recent_viewers);
            }
        }
    }

    public static class TL_publicForwardStory extends TL_stats.PublicForward {
        public static final int constructor = 0xedf3add0;

        public TLRPC.Peer peer;
        public StoryItem story;

        public void readParams(InputSerializedData stream, boolean exception) {
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            story = StoryItem.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            story.serializeToStream(stream);
        }
    }

    public static class StoryFwdHeader extends TLObject {

        public int flags;
        public boolean modified;
        public TLRPC.Peer from;
        public String from_name;
        public int story_id;

        public static StoryFwdHeader TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            StoryFwdHeader result = null;
            switch (constructor) {
                case TL_storyFwdHeader.constructor:
                    result = new TL_storyFwdHeader();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in StoryFwdHeader", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_storyFwdHeader extends StoryFwdHeader {
        public static final int constructor = 0xb826e150;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            modified = (flags & 8) != 0;
            if ((flags & 1) != 0) {
                from = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 2) != 0) {
                from_name = stream.readString(exception);
            }
            if ((flags & 4) != 0) {
                story_id = stream.readInt32(exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = modified ? (flags | 8) : (flags &~ 8);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                from.serializeToStream(stream);
            }
            if ((flags & 2) != 0) {
                stream.writeString(from_name);
            }
            if ((flags & 4) != 0) {
                stream.writeInt32(story_id);
            }
        }
    }

    public static class TL_storyItem extends StoryItem {
        public static final int constructor = 0x79b26a24;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            pinned = (flags & 32) != 0;
            isPublic = (flags & 128) != 0;
            close_friends = (flags & 256) != 0;
            min = (flags & 512) != 0;
            noforwards = (flags & 1024) != 0;
            edited = (flags & 2048) != 0;
            contacts = (flags & 4096) != 0;
            selected_contacts = (flags & 8192) != 0;
            out = (flags & 65536) != 0;
            id = stream.readInt32(exception);
            date = stream.readInt32(exception);
            if ((flags & 262144) != 0) {
                from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 131072) != 0) {
                fwd_from = TL_storyFwdHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            expire_date = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                caption = stream.readString(exception);
            }
            if ((flags & 2) != 0) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 16384) != 0) {
                media_areas = Vector.deserialize(stream, MediaArea::TLdeserialize, exception);
            }
            if ((flags & 4) != 0) {
                privacy = Vector.deserialize(stream, TLRPC.PrivacyRule::TLdeserialize, exception);
            }
            if ((flags & 8) != 0) {
                views = StoryViews.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 32768) != 0) {
                sent_reaction = TLRPC.Reaction.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = pinned ? (flags | 32) : (flags &~ 32);
            flags = isPublic ? (flags | 128) : (flags &~ 128);
            flags = close_friends ? (flags | 256) : (flags &~ 256);
            flags = min ? (flags | 512) : (flags &~ 512);
            flags = noforwards ? (flags | 1024) : (flags &~ 1024);
            flags = edited ? (flags | 2048) : (flags &~ 2048);
            flags = contacts ? (flags | 4096) : (flags &~ 4096);
            flags = selected_contacts ? (flags | 8192) : (flags &~ 8192);
            flags = out ? (flags | 65536) : (flags &~ 65536);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            stream.writeInt32(date);
            if ((flags & 262144) != 0) {
                from_id.serializeToStream(stream);
            }
            if ((flags & 131072) != 0) {
                fwd_from.serializeToStream(stream);
            }
            stream.writeInt32(expire_date);
            if ((flags & 1) != 0) {
                stream.writeString(caption);
            }
            if ((flags & 2) != 0) {
                Vector.serialize(stream, entities);
            }
            media.serializeToStream(stream);
            if ((flags & 16384) != 0) {
                Vector.serialize(stream, media_areas);
            }
            if ((flags & 4) != 0) {
                Vector.serialize(stream, privacy);
            }
            if ((flags & 8) != 0) {
                views.serializeToStream(stream);
            }
            if ((flags & 32768) != 0) {
                sent_reaction.serializeToStream(stream);
            }
        }
    }

    public static class TL_storyItem_layer174 extends TL_storyItem {
        public static final int constructor = 0xaf6365a1;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            pinned = (flags & 32) != 0;
            isPublic = (flags & 128) != 0;
            close_friends = (flags & 256) != 0;
            min = (flags & 512) != 0;
            noforwards = (flags & 1024) != 0;
            edited = (flags & 2048) != 0;
            contacts = (flags & 4096) != 0;
            selected_contacts = (flags & 8192) != 0;
            out = (flags & 65536) != 0;
            id = stream.readInt32(exception);
            date = stream.readInt32(exception);
            if ((flags & 131072) != 0) {
                fwd_from = TL_storyFwdHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            expire_date = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                caption = stream.readString(exception);
            }
            if ((flags & 2) != 0) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 16384) != 0) {
                media_areas = Vector.deserialize(stream, MediaArea::TLdeserialize, exception);
            }
            if ((flags & 4) != 0) {
                privacy = Vector.deserialize(stream, TLRPC.PrivacyRule::TLdeserialize, exception);
            }
            if ((flags & 8) != 0) {
                views = StoryViews.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 32768) != 0) {
                sent_reaction = TLRPC.Reaction.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = pinned ? (flags | 32) : (flags &~ 32);
            flags = isPublic ? (flags | 128) : (flags &~ 128);
            flags = close_friends ? (flags | 256) : (flags &~ 256);
            flags = min ? (flags | 512) : (flags &~ 512);
            flags = noforwards ? (flags | 1024) : (flags &~ 1024);
            flags = edited ? (flags | 2048) : (flags &~ 2048);
            flags = contacts ? (flags | 4096) : (flags &~ 4096);
            flags = selected_contacts ? (flags | 8192) : (flags &~ 8192);
            flags = out ? (flags | 65536) : (flags &~ 65536);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            stream.writeInt32(date);
            if ((flags & 131072) != 0) {
                fwd_from.serializeToStream(stream);
            }
            stream.writeInt32(expire_date);
            if ((flags & 1) != 0) {
                stream.writeString(caption);
            }
            if ((flags & 2) != 0) {
                Vector.serialize(stream, entities);
            }
            media.serializeToStream(stream);
            if ((flags & 16384) != 0) {
                Vector.serialize(stream, media_areas);
            }
            if ((flags & 4) != 0) {
                Vector.serialize(stream, privacy);
            }
            if ((flags & 8) != 0) {
                views.serializeToStream(stream);
            }
            if ((flags & 32768) != 0) {
                sent_reaction.serializeToStream(stream);
            }
        }
    }

    public static class TL_storyItem_layer166 extends TL_storyItem {
        public static final int constructor = 0x44c457ce;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            pinned = (flags & 32) != 0;
            isPublic = (flags & 128) != 0;
            close_friends = (flags & 256) != 0;
            min = (flags & 512) != 0;
            noforwards = (flags & 1024) != 0;
            edited = (flags & 2048) != 0;
            contacts = (flags & 4096) != 0;
            selected_contacts = (flags & 8192) != 0;
            out = (flags & 65536) != 0;
            id = stream.readInt32(exception);
            date = stream.readInt32(exception);
            expire_date = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                caption = stream.readString(exception);
            }
            if ((flags & 2) != 0) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 16384) != 0) {
                media_areas = Vector.deserialize(stream, MediaArea::TLdeserialize, exception);
            }
            if ((flags & 4) != 0) {
                privacy = Vector.deserialize(stream, TLRPC.PrivacyRule::TLdeserialize, exception);
            }
            if ((flags & 8) != 0) {
                views = StoryViews.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 32768) != 0) {
                sent_reaction = TLRPC.Reaction.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = pinned ? (flags | 32) : (flags &~ 32);
            flags = isPublic ? (flags | 128) : (flags &~ 128);
            flags = close_friends ? (flags | 256) : (flags &~ 256);
            flags = min ? (flags | 512) : (flags &~ 512);
            flags = noforwards ? (flags | 1024) : (flags &~ 1024);
            flags = edited ? (flags | 2048) : (flags &~ 2048);
            flags = contacts ? (flags | 4096) : (flags &~ 4096);
            flags = selected_contacts ? (flags | 8192) : (flags &~ 8192);
            flags = out ? (flags | 65536) : (flags &~ 65536);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            stream.writeInt32(date);
            stream.writeInt32(expire_date);
            if ((flags & 1) != 0) {
                stream.writeString(caption);
            }
            if ((flags & 2) != 0) {
                Vector.serialize(stream, entities);
            }
            media.serializeToStream(stream);
            if ((flags & 16384) != 0) {
                Vector.serialize(stream, media_areas);
            }
            if ((flags & 4) != 0) {
                Vector.serialize(stream, privacy);
            }
            if ((flags & 8) != 0) {
                views.serializeToStream(stream);
            }
            if ((flags & 32768) != 0) {
                sent_reaction.serializeToStream(stream);
            }
        }
    }

    public static class TL_storyItem_layer160 extends TL_storyItem {
        public static final int constructor = 0x562aa637;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            pinned = (flags & 32) != 0;
            isPublic = (flags & 128) != 0;
            close_friends = (flags & 256) != 0;
            min = (flags & 512) != 0;
            noforwards = (flags & 1024) != 0;
            edited = (flags & 2048) != 0;
            contacts = (flags & 4096) != 0;
            selected_contacts = (flags & 8192) != 0;
            id = stream.readInt32(exception);
            date = stream.readInt32(exception);
            expire_date = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                caption = stream.readString(exception);
            }
            if ((flags & 2) != 0) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 4) != 0) {
                privacy = Vector.deserialize(stream, TLRPC.PrivacyRule::TLdeserialize, exception);
            }
            if ((flags & 8) != 0) {
                views = StoryViews.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = pinned ? (flags | 32) : (flags &~ 32);
            flags = isPublic ? (flags | 128) : (flags &~ 128);
            flags = close_friends ? (flags | 256) : (flags &~ 256);
            flags = min ? (flags | 512) : (flags &~ 512);
            flags = noforwards ? (flags | 1024) : (flags &~ 1024);
            flags = edited ? (flags | 2048) : (flags &~ 2048);
            flags = contacts ? (flags | 4096) : (flags &~ 4096);
            flags = selected_contacts ? (flags | 8192) : (flags &~ 8192);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            stream.writeInt32(date);
            stream.writeInt32(expire_date);
            if ((flags & 1) != 0) {
                stream.writeString(caption);
            }
            if ((flags & 2) != 0) {
                Vector.serialize(stream, entities);
            }
            media.serializeToStream(stream);
            if ((flags & 4) != 0) {
                Vector.serialize(stream, privacy);
            }
            if ((flags & 8) != 0) {
                views.serializeToStream(stream);
            }
        }
    }

    public static class TL_storyItemDeleted extends StoryItem {
        public static final int constructor = 0x51e6ee4f;

        public void readParams(InputSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
        }
    }

    public static class TL_storyItemSkipped extends StoryItem {
        public static final int constructor = 0xffadc913;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            close_friends = (flags & 256) != 0;
            id = stream.readInt32(exception);
            date = stream.readInt32(exception);
            expire_date = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = close_friends ? (flags | 256) : (flags &~ 256);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            stream.writeInt32(date);
            stream.writeInt32(expire_date);
        }
    }

    public static class MediaAreaCoordinates extends TLObject {

        public int flags;
        public double x;
        public double y;
        public double w;
        public double h;
        public double rotation;
        public double radius;

        public static MediaAreaCoordinates TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            MediaAreaCoordinates result = null;
            switch (constructor) {
                case TL_mediaAreaCoordinates.constructor:
                    result = new TL_mediaAreaCoordinates();
                    break;
                case TL_mediaAreaCoordinates_layer181.constructor:
                    result = new TL_mediaAreaCoordinates_layer181();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in MediaAreaCoordinates", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_mediaAreaCoordinates extends MediaAreaCoordinates {
        public static final int constructor = 0xcfc9e002;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            x = stream.readDouble(exception);
            y = stream.readDouble(exception);
            w = stream.readDouble(exception);
            h = stream.readDouble(exception);
            rotation = stream.readDouble(exception);
            if ((flags & 1) != 0) {
                radius = stream.readDouble(exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeDouble(x);
            stream.writeDouble(y);
            stream.writeDouble(w);
            stream.writeDouble(h);
            stream.writeDouble(rotation);
            if ((flags & 1) != 0) {
                stream.writeDouble(radius);
            }
        }
    }

    public static class TL_mediaAreaCoordinates_layer181 extends MediaAreaCoordinates {
        public static final int constructor = 0x3d1ea4e;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            x = stream.readDouble(exception);
            y = stream.readDouble(exception);
            w = stream.readDouble(exception);
            h = stream.readDouble(exception);
            rotation = stream.readDouble(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeDouble(x);
            stream.writeDouble(y);
            stream.writeDouble(w);
            stream.writeDouble(h);
            stream.writeDouble(rotation);
        }
    }

    public static class MediaArea extends TLObject {
        public MediaAreaCoordinates coordinates;
        public TLRPC.Reaction reaction;
        public int flags;
        public boolean dark;
        public boolean flipped;

        public static MediaArea TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            MediaArea result = null;
            switch (constructor) {
                case TL_mediaAreaUrl.constructor:
                    result = new TL_mediaAreaUrl();
                    break;
                case TL_mediaAreaStarGift.constructor:
                    result = new TL_mediaAreaStarGift();
                    break;
                case TL_mediaAreaVenue.constructor:
                    result = new TL_mediaAreaVenue();
                    break;
                case TL_mediaAreaGeoPoint.constructor:
                    result = new TL_mediaAreaGeoPoint();
                    break;
                case TL_mediaAreaGeoPoint_layer181.constructor:
                    result = new TL_mediaAreaGeoPoint_layer181();
                    break;
                case TL_mediaAreaSuggestedReaction.constructor:
                    result = new TL_mediaAreaSuggestedReaction();
                    break;
                case TL_mediaAreaChannelPost.constructor:
                    result = new TL_mediaAreaChannelPost();
                    break;
                case TL_inputMediaAreaVenue.constructor:
                    result = new TL_inputMediaAreaVenue();
                    break;
                case TL_inputMediaAreaChannelPost.constructor:
                    result = new TL_inputMediaAreaChannelPost();
                    break;
                case TL_mediaAreaWeather.constructor:
                    result = new TL_mediaAreaWeather();
                    break;
                case TL_mediaAreaWeatherOld.constructor:
                    result = new TL_mediaAreaWeatherOld();
                    break;
                case TL_mediaAreaWeather2.constructor:
                    result = new TL_mediaAreaWeather2();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in MediaArea", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_mediaAreaSuggestedReaction extends MediaArea {
        public static final int constructor = 0x14455871;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            dark = (flags & 1) != 0;
            flipped = (flags & 2) != 0;
            coordinates = MediaAreaCoordinates.TLdeserialize(stream, stream.readInt32(exception), exception);
            reaction = TLRPC.Reaction.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = dark ? (flags | 1) : (flags &~ 1);
            flags = flipped ? (flags | 2) : (flags &~ 2);
            stream.writeInt32(flags);
            coordinates.serializeToStream(stream);
            reaction.serializeToStream(stream);
        }
    }

    public static class TL_mediaAreaChannelPost extends MediaArea {
        public static final int constructor = 0x770416af;

        public long channel_id;
        public int msg_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            coordinates = MediaAreaCoordinates.TLdeserialize(stream, stream.readInt32(exception), exception);
            channel_id = stream.readInt64(exception);
            msg_id = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            coordinates.serializeToStream(stream);
            stream.writeInt64(channel_id);
            stream.writeInt32(msg_id);
        }
    }

    public static class TL_mediaAreaVenue extends MediaArea {
        public static final int constructor = 0xbe82db9c;

        public TLRPC.GeoPoint geo;
        public String title;
        public String address;
        public String provider;
        public String venue_id;
        public String venue_type;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            coordinates = MediaAreaCoordinates.TLdeserialize(stream, stream.readInt32(exception), exception);
            geo = TLRPC.GeoPoint.TLdeserialize(stream, stream.readInt32(exception), exception);
            title = stream.readString(exception);
            address = stream.readString(exception);
            provider = stream.readString(exception);
            venue_id = stream.readString(exception);
            venue_type = stream.readString(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            coordinates.serializeToStream(stream);
            geo.serializeToStream(stream);
            stream.writeString(title);
            stream.writeString(address);
            stream.writeString(provider);
            stream.writeString(venue_id);
            stream.writeString(venue_type);
        }
    }


    public static class TL_mediaAreaUrl extends MediaArea {
        public static final int constructor = 0x37381085;

        public String url;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            coordinates = MediaAreaCoordinates.TLdeserialize(stream, stream.readInt32(exception), exception);
            url = stream.readString(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            coordinates.serializeToStream(stream);
            stream.writeString(url);
        }
    }

    public static class TL_mediaAreaStarGift extends MediaArea {
        public static final int constructor = 0x5787686d;

        public String slug;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            coordinates = MediaAreaCoordinates.TLdeserialize(stream, stream.readInt32(exception), exception);
            slug = stream.readString(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            coordinates.serializeToStream(stream);
            stream.writeString(slug);
        }
    }

    public static class TL_mediaAreaWeather2 extends MediaArea {
        public static final int constructor = 0x855f223e;

        public String emoji;
        public int temperature_c;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            dark = (flags & 1) != 0;
            coordinates = MediaAreaCoordinates.TLdeserialize(stream, stream.readInt32(exception), exception);
            emoji = stream.readString(exception);
            temperature_c = stream.readInt32(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = dark ? flags | 1 : flags &~ 1;
            stream.writeInt32(flags);
            coordinates.serializeToStream(stream);
            stream.writeString(emoji);
            stream.writeInt32(temperature_c);
        }
    }

    public static class TL_mediaAreaWeather extends MediaArea {
        public static final int constructor = 0x49a6549c;

        public String emoji;
        public double temperature_c;
        public int color;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            coordinates = MediaAreaCoordinates.TLdeserialize(stream, stream.readInt32(exception), exception);
            emoji = stream.readString(exception);
            temperature_c = stream.readDouble(exception);
            color = stream.readInt32(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            coordinates.serializeToStream(stream);
            stream.writeString(emoji);
            stream.writeDouble(temperature_c);
            stream.writeInt32(color);
        }
    }

    public static class TL_mediaAreaWeatherOld extends MediaArea {
        public static final int constructor = 0x4386f849;

        public String emoji;
        public double temperature_c;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            dark = (flags & 1) != 0;
            coordinates = MediaAreaCoordinates.TLdeserialize(stream, stream.readInt32(exception), exception);
            emoji = stream.readString(exception);
            temperature_c = stream.readDouble(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = dark ? flags | 1 : flags &~ 1;
            stream.writeInt32(flags);
            coordinates.serializeToStream(stream);
            stream.writeString(emoji);
            stream.writeDouble(temperature_c);
        }
    }

    public static class TL_inputMediaAreaVenue extends MediaArea {
        public static final int constructor = 0xb282217f;

        public long query_id;
        public String result_id;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            coordinates = MediaAreaCoordinates.TLdeserialize(stream, stream.readInt32(exception), exception);
            query_id = stream.readInt64(exception);
            result_id = stream.readString(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            coordinates.serializeToStream(stream);
            stream.writeInt64(query_id);
            stream.writeString(result_id);
        }
    }

    public static class TL_inputMediaAreaChannelPost extends MediaArea {
        public static final int constructor = 0x2271f2bf;

        public TLRPC.InputChannel channel;
        public int msg_id;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            coordinates = MediaAreaCoordinates.TLdeserialize(stream, stream.readInt32(exception), exception);
            channel = TLRPC.InputChannel.TLdeserialize(stream, stream.readInt32(exception), exception);
            msg_id = stream.readInt32(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            coordinates.serializeToStream(stream);
            channel.serializeToStream(stream);
            stream.writeInt32(msg_id);
        }
    }

    public static class TL_geoPointAddress extends TLObject {
        public static final int constructor = 0xde4c5d93;

        public int flags;
        public String country_iso2;
        public String state;
        public String city;
        public String street;

        @NonNull
        @Override
        public String toString() {
            return "geo{country=" + country_iso2 + ", " + (state != null ? "state=" + state + ", " : "") + (city != null ? "city=" + city + ", " : "") + (street != null ? "street=" + street : "") + "}";
        }

        public static TL_geoPointAddress TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (TL_geoPointAddress.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_geoPointAddress", constructor));
                } else {
                    return null;
                }
            }
            TL_geoPointAddress result = new TL_geoPointAddress();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            country_iso2 = stream.readString(exception);
            if ((flags & 1) != 0) {
                state = stream.readString(exception);
            }
            if ((flags & 2) != 0) {
                city = stream.readString(exception);
            }
            if ((flags & 4) != 0) {
                street = stream.readString(exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeString(country_iso2);
            if ((flags & 1) != 0) {
                stream.writeString(state);
            }
            if ((flags & 2) != 0) {
                stream.writeString(city);
            }
            if ((flags & 4) != 0) {
                stream.writeString(street);
            }
        }
    }

    public static class TL_mediaAreaGeoPoint extends MediaArea {
        public static final int constructor = 0xcad5452d;

        public TLRPC.GeoPoint geo;
        public TL_geoPointAddress address;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            coordinates = MediaAreaCoordinates.TLdeserialize(stream, stream.readInt32(exception), exception);
            geo = TLRPC.GeoPoint.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 1) != 0) {
                address = TL_geoPointAddress.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            coordinates.serializeToStream(stream);
            geo.serializeToStream(stream);
            if ((flags & 1) != 0) {
                address.serializeToStream(stream);
            }
        }
    }

    public static class TL_mediaAreaGeoPoint_layer181 extends TL_mediaAreaGeoPoint {
        public static final int constructor = 0xdf8b3b22;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            coordinates = MediaAreaCoordinates.TLdeserialize(stream, stream.readInt32(exception), exception);
            geo = TLRPC.GeoPoint.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            coordinates.serializeToStream(stream);
            geo.serializeToStream(stream);
        }
    }

    public static class PrepaidGiveaway extends TLObject {

        public long id;
        public int quantity;
        public int date;
        public int boosts;

        public static PrepaidGiveaway TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            PrepaidGiveaway result = null;
            switch (constructor) {
                case TL_prepaidGiveaway.constructor:
                    result = new TL_prepaidGiveaway();
                    break;
                case TL_prepaidStarsGiveaway.constructor:
                    result = new TL_prepaidStarsGiveaway();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in PrepaidGiveaway", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_prepaidStarsGiveaway extends PrepaidGiveaway {
        public static final int constructor = 0x9a9d77e0;

        public long stars;

        public void readParams(InputSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            stars = stream.readInt64(exception);
            quantity = stream.readInt32(exception);
            boosts = stream.readInt32(exception);
            date = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(stars);
            stream.writeInt32(quantity);
            stream.writeInt32(boosts);
            stream.writeInt32(date);
        }
    }

    public static class TL_prepaidGiveaway extends PrepaidGiveaway {
        public static final int constructor = 0xb2539d54;

        public int months;

        public void readParams(InputSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            months = stream.readInt32(exception);
            quantity = stream.readInt32(exception);
            date = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt32(months);
            stream.writeInt32(quantity);
            stream.writeInt32(date);
        }
    }

    public static class TL_stats_storyStats extends TLObject {
        public final static int constructor = 0x50cd067c;

        public TL_stats.StatsGraph views_graph;
        public TL_stats.StatsGraph reactions_by_emotion_graph;

        public static TL_stats_storyStats TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (TL_stats_storyStats.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_stats_storyStats", constructor));
                } else {
                    return null;
                }
            }
            TL_stats_storyStats result = new TL_stats_storyStats();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            views_graph = TL_stats.StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
            reactions_by_emotion_graph = TL_stats.StatsGraph.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            views_graph.serializeToStream(stream);
            reactions_by_emotion_graph.serializeToStream(stream);
        }
    }

    public static class TL_stats_getStoryStats extends TLObject {
        public final static int constructor = 0x374fef40;

        public int flags;
        public boolean dark;
        public TLRPC.InputPeer peer;
        public int id;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_stats_storyStats.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = dark ? (flags | 1) : (flags & ~1);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            stream.writeInt32(id);
        }
    }

    public static class StoryReaction extends TLObject {

        public TLRPC.Peer peer_id;
        public StoryItem story;
        public TLRPC.Message message;

        public static StoryReaction TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            StoryReaction result = null;
            switch (constructor) {
                case TL_storyReaction.constructor:
                    result = new TL_storyReaction();
                    break;
                case TL_storyReactionPublicForward.constructor:
                    result = new TL_storyReactionPublicForward();
                    break;
                case TL_storyReactionPublicRepost.constructor:
                    result = new TL_storyReactionPublicRepost();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in StoryReaction", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_storyReactionPublicForward extends StoryReaction {
        public final static int constructor = 0xbbab2643;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            message = TLRPC.Message.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            message.serializeToStream(stream);
        }
    }

    public static class TL_storyReactionPublicRepost extends StoryReaction {
        public final static int constructor = 0xcfcd0f13;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            story = StoryItem.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (story != null) {
                story.dialogId = DialogObject.getPeerDialogId(peer_id);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer_id.serializeToStream(stream);
            story.serializeToStream(stream);
        }
    }

    public static class TL_storyReaction extends StoryReaction {
        public final static int constructor = 0x6090d6d5;

        public int date;
        public TLRPC.Reaction reaction;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            date = stream.readInt32(exception);
            reaction = TLRPC.Reaction.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer_id.serializeToStream(stream);
            stream.writeInt32(date);
            reaction.serializeToStream(stream);
        }
    }

    public static class TL_storyReactionsList extends TLObject {
        public final static int constructor = 0xaa5f789c;

        public int flags;
        public int count;
        public ArrayList<StoryReaction> reactions = new ArrayList<>();
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();
        public String next_offset;

        public static TL_storyReactionsList TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (TL_storyReactionsList.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_storyReactionsList", constructor));
                } else {
                    return null;
                }
            }
            TL_storyReactionsList result = new TL_storyReactionsList();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(count);
            Vector.serialize(stream, reactions);
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);
            if ((flags & 1) != 0) {
                stream.writeString(next_offset);
            }
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            count = stream.readInt32(exception);
            reactions = Vector.deserialize(stream, StoryReaction::TLdeserialize, exception);
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
            if ((flags & 1) != 0) {
                next_offset = stream.readString(exception);
            }
        }
    }

    public static class TL_getStoryReactionsList extends TLObject {
        public final static int constructor = 0xb9b2881f;

        public int flags;
        public boolean forwards_first;
        public TLRPC.InputPeer peer;
        public int id;
        public TLRPC.Reaction reaction;
        public String offset;
        public int limit;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_storyReactionsList.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = forwards_first ? (flags | 4) : (flags &~ 4);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            stream.writeInt32(id);
            if ((flags & 1) != 0) {
                reaction.serializeToStream(stream);
            }
            if ((flags & 2) != 0) {
                stream.writeString(offset);
            }
            stream.writeInt32(limit);
        }
    }

    public static class TL_togglePinnedToTop extends TLObject {
        public static final int constructor = 0xb297e9b;

        public TLRPC.InputPeer peer;
        public ArrayList<Integer> id = new ArrayList<>();

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            Vector.serializeInt(stream, id);
        }
    }
}

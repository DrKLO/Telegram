package org.telegram.tgnet.tl;

import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public class TL_chatlists {
    public static class TL_inputChatlistDialogFilter extends TLObject {
        public static final int constructor = 0xf3e0da33;

        public int filter_id;

        public static TL_inputChatlistDialogFilter TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (TL_inputChatlistDialogFilter.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_inputChatlistDialogFilter", constructor));
                } else {
                    return null;
                }
            }
            TL_inputChatlistDialogFilter result = new TL_inputChatlistDialogFilter();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            filter_id = stream.readInt32(exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(filter_id);
        }
    }

    public static class TL_chatlists_exportedChatlistInvite extends TLObject {
        public static final int constructor = 0x10e6e3a6;

        public TLRPC.DialogFilter filter;
        public TL_exportedChatlistInvite invite;

        public static TL_chatlists_exportedChatlistInvite TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (TL_chatlists_exportedChatlistInvite.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_chatlists_exportedChatlistInvite", constructor));
                } else {
                    return null;
                }
            }
            TL_chatlists_exportedChatlistInvite result = new TL_chatlists_exportedChatlistInvite();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            filter = TLRPC.DialogFilter.TLdeserialize(stream, stream.readInt32(exception), exception);
            invite = TL_exportedChatlistInvite.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            filter.serializeToStream(stream);
            invite.serializeToStream(stream);
        }
    }

    public static class TL_exportedChatlistInvite extends TLObject {
        public static final int constructor = 0xc5181ac;

        public int flags;
        public boolean revoked;
        public String title;
        public String url;
        public ArrayList<TLRPC.Peer> peers = new ArrayList<>();

        public static TL_exportedChatlistInvite TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (TL_exportedChatlistInvite.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_exportedChatlistInvite", constructor));
                } else {
                    return null;
                }
            }
            TL_exportedChatlistInvite result = new TL_exportedChatlistInvite();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            revoked = (flags & 1) != 0;
            title = stream.readString(exception);
            url = stream.readString(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TLRPC.Peer object = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                peers.add(object);
            }
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = revoked ? (flags | 1) : (flags & ~1);
            stream.writeInt32(flags);
            stream.writeString(title);
            stream.writeString(url);
            stream.writeInt32(0x1cb5c415);
            int count = peers.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                peers.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_chatlists_exportedInvites extends TLObject {
        public static final int constructor = 0x10ab6dc7;

        public ArrayList<TL_exportedChatlistInvite> invites = new ArrayList<>();
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static TL_chatlists_exportedInvites TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (TL_chatlists_exportedInvites.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_chatlists_exportedInvites", constructor));
                } else {
                    return null;
                }
            }
            TL_chatlists_exportedInvites result = new TL_chatlists_exportedInvites();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_exportedChatlistInvite object = TL_exportedChatlistInvite.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                invites.add(object);
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
            stream.writeInt32(0x1cb5c415);
            int count = invites.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                invites.get(a).serializeToStream(stream);
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

    public static abstract class chatlist_ChatlistInvite extends TLObject {

        public static chatlist_ChatlistInvite TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            chatlist_ChatlistInvite result = null;
            switch (constructor) {
                case 0xfa87f659:
                    result = new TL_chatlists_chatlistInviteAlready();
                    break;
                case 0x1dcd839d:
                    result = new TL_chatlists_chatlistInvite();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in chatlist_ChatlistInvite", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_chatlists_chatlistInviteAlready extends chatlist_ChatlistInvite {
        public static final int constructor = 0xfa87f659;

        public int filter_id;
        public ArrayList<TLRPC.Peer> missing_peers = new ArrayList<>();
        public ArrayList<TLRPC.Peer> already_peers = new ArrayList<>();
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public void readParams(AbstractSerializedData stream, boolean exception) {
            filter_id = stream.readInt32(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TLRPC.Peer object = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                missing_peers.add(object);
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
                TLRPC.Peer object = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                already_peers.add(object);
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
            stream.writeInt32(filter_id);
            stream.writeInt32(0x1cb5c415);
            int count = missing_peers.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                missing_peers.get(a).serializeToStream(stream);
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

    public static class TL_chatlists_chatlistInvite extends chatlist_ChatlistInvite {
        public static final int constructor = 0x1dcd839d;

        public int flags;
        public String title;
        public String emoticon;
        public ArrayList<TLRPC.Peer> peers = new ArrayList<>();
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            title = stream.readString(exception);
            if ((flags & 1) > 0) {
                emoticon = stream.readString(exception);
            }
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TLRPC.Peer object = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                peers.add(object);
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
            stream.writeString(title);
            if ((flags & 1) > 0) {
                stream.writeString(emoticon);
            }
            stream.writeInt32(0x1cb5c415);
            int count = peers.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                peers.get(a).serializeToStream(stream);
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

    public static class TL_chatlists_chatlistUpdates extends TLObject {
        public static final int constructor = 0x93bd878d;

        public ArrayList<TLRPC.Peer> missing_peers = new ArrayList<>();
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static TL_chatlists_chatlistUpdates TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (TL_chatlists_chatlistUpdates.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_chatlists_chatlistUpdates", constructor));
                } else {
                    return null;
                }
            }
            TL_chatlists_chatlistUpdates result = new TL_chatlists_chatlistUpdates();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TLRPC.Peer object = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                missing_peers.add(object);
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
            stream.writeInt32(0x1cb5c415);
            int count = missing_peers.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                missing_peers.get(a).serializeToStream(stream);
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

    public static class TL_chatlists_exportChatlistInvite extends TLObject {
        public static final int constructor = 0x8472478e;

        public TL_inputChatlistDialogFilter chatlist;
        public String title;
        public ArrayList<TLRPC.InputPeer> peers = new ArrayList<>();

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TL_chatlists_exportedChatlistInvite.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            chatlist.serializeToStream(stream);
            stream.writeString(title);
            stream.writeInt32(0x1cb5c415);
            int count = peers.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                peers.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_chatlists_deleteExportedInvite extends TLObject {
        public static final int constructor = 0x719c5c5e;

        public TL_inputChatlistDialogFilter chatlist;
        public String slug;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            chatlist.serializeToStream(stream);
            stream.writeString(slug);
        }
    }

    public static class TL_chatlists_editExportedInvite extends TLObject {
        public static final int constructor = 0x653db63d;

        public int flags;
        public boolean revoked;
        public TL_inputChatlistDialogFilter chatlist;
        public String slug;
        public String title;
        public ArrayList<TLRPC.InputPeer> peers = new ArrayList<>();

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TL_exportedChatlistInvite.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = revoked ? (flags | 1) : (flags &~ 1);
            stream.writeInt32(flags);
            chatlist.serializeToStream(stream);
            stream.writeString(slug);
            if ((flags & 2) != 0) {
                stream.writeString(title);
            }
            if ((flags & 4) != 0) {
                stream.writeInt32(0x1cb5c415);
                int count = peers.size();
                stream.writeInt32(count);
                for (int a = 0; a < count; a++) {
                    peers.get(a).serializeToStream(stream);
                }
            }
        }
    }

    public static class TL_chatlists_getExportedInvites extends TLObject {
        public static final int constructor = 0xce03da83;

        public TL_inputChatlistDialogFilter chatlist;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TL_chatlists_exportedInvites.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            chatlist.serializeToStream(stream);
        }
    }

    public static class TL_chatlists_checkChatlistInvite extends TLObject {
        public static final int constructor = 0x41c10fff;

        public String slug;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return chatlist_ChatlistInvite.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(slug);
        }
    }

    public static class TL_chatlists_joinChatlistInvite extends TLObject {
        public static final int constructor = 0xa6b1e39a;

        public String slug;
        public ArrayList<TLRPC.InputPeer> peers = new ArrayList<>();

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(slug);
            stream.writeInt32(0x1cb5c415);
            int count = peers.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                peers.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_chatlists_getChatlistUpdates extends TLObject {
        public static final int constructor = 0x89419521;

        public TL_inputChatlistDialogFilter chatlist;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TL_chatlists_chatlistUpdates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            chatlist.serializeToStream(stream);
        }
    }

    public static class TL_chatlists_joinChatlistUpdates extends TLObject {
        public static final int constructor = 0xe089f8f5;

        public TL_inputChatlistDialogFilter chatlist;
        public ArrayList<TLRPC.InputPeer> peers = new ArrayList<>();

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            chatlist.serializeToStream(stream);
            stream.writeInt32(0x1cb5c415);
            int count = peers.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                peers.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_chatlists_hideChatlistUpdates extends TLObject {
        public static final int constructor = 0x66e486fb;

        public TL_inputChatlistDialogFilter chatlist;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            chatlist.serializeToStream(stream);
        }
    }

    public static class TL_chatlists_getLeaveChatlistSuggestions extends TLObject {
        public static final int constructor = 0xfdbcd714;

        public TL_inputChatlistDialogFilter chatlist;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            TLRPC.Vector vector = new TLRPC.Vector();
            int size = stream.readInt32(exception);
            for (int a = 0; a < size; a++) {
                TLRPC.Peer object = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return vector;
                }
                vector.objects.add(object);
            }
            return vector;
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            chatlist.serializeToStream(stream);
        }
    }

    public static class TL_chatlists_leaveChatlist extends TLObject {
        public static final int constructor = 0x74fae13a;

        public TL_inputChatlistDialogFilter chatlist;
        public ArrayList<TLRPC.InputPeer> peers = new ArrayList<>();

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            chatlist.serializeToStream(stream);
            stream.writeInt32(0x1cb5c415);
            int count = peers.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                peers.get(a).serializeToStream(stream);
            }
        }
    }
}

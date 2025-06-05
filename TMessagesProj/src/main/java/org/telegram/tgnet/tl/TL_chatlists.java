package org.telegram.tgnet.tl;

import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;

import java.util.ArrayList;

public class TL_chatlists {
    public static class TL_inputChatlistDialogFilter extends TLObject {
        public static final int constructor = 0xf3e0da33;

        public int filter_id;

        public static TL_inputChatlistDialogFilter TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
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

        public void readParams(InputSerializedData stream, boolean exception) {
            filter_id = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(filter_id);
        }
    }

    public static class TL_chatlists_exportedChatlistInvite extends TLObject {
        public static final int constructor = 0x10e6e3a6;

        public TLRPC.DialogFilter filter;
        public TL_exportedChatlistInvite invite;

        public static TL_chatlists_exportedChatlistInvite TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
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

        public void readParams(InputSerializedData stream, boolean exception) {
            filter = TLRPC.DialogFilter.TLdeserialize(stream, stream.readInt32(exception), exception);
            invite = TL_exportedChatlistInvite.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
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

        public static TL_exportedChatlistInvite TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
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

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            revoked = (flags & 1) != 0;
            title = stream.readString(exception);
            url = stream.readString(exception);
            peers = Vector.deserialize(stream, TLRPC.Peer::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = revoked ? (flags | 1) : (flags & ~1);
            stream.writeInt32(flags);
            stream.writeString(title);
            stream.writeString(url);
            Vector.serialize(stream, peers);
        }
    }

    public static class TL_chatlists_exportedInvites extends TLObject {
        public static final int constructor = 0x10ab6dc7;

        public ArrayList<TL_exportedChatlistInvite> invites = new ArrayList<>();
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static TL_chatlists_exportedInvites TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
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

        public void readParams(InputSerializedData stream, boolean exception) {
            invites = Vector.deserialize(stream, TL_exportedChatlistInvite::TLdeserialize, exception);
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, invites);
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);
        }
    }

    public static abstract class chatlist_ChatlistInvite extends TLObject {

        public static chatlist_ChatlistInvite TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            chatlist_ChatlistInvite result = null;
            switch (constructor) {
                case TL_chatlists_chatlistInviteAlready.constructor:
                    result = new TL_chatlists_chatlistInviteAlready();
                    break;
                case TL_chatlists_chatlistInvite.constructor:
                    result = new TL_chatlists_chatlistInvite();
                    break;
                case TL_chatlists_chatlistInvite_layer195.constructor:
                    result = new TL_chatlists_chatlistInvite_layer195();
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

        public void readParams(InputSerializedData stream, boolean exception) {
            filter_id = stream.readInt32(exception);
            missing_peers = Vector.deserialize(stream, TLRPC.Peer::TLdeserialize, exception);
            already_peers = Vector.deserialize(stream, TLRPC.Peer::TLdeserialize, exception);
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(filter_id);
            Vector.serialize(stream, missing_peers);
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);
        }
    }

    public static class TL_chatlists_chatlistInvite extends chatlist_ChatlistInvite {
        public static final int constructor = 0xf10ece2f;

        public int flags;
        public boolean title_noanimate;
        public TLRPC.TL_textWithEntities title = new TLRPC.TL_textWithEntities();
        public String emoticon;
        public ArrayList<TLRPC.Peer> peers = new ArrayList<>();
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            title_noanimate = (flags & 2) != 0;
            title = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 1) > 0) {
                emoticon = stream.readString(exception);
            }
            peers = Vector.deserialize(stream, TLRPC.Peer::TLdeserialize, exception);
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = title_noanimate ? (flags | 2) : (flags & ~2);
            stream.writeInt32(flags);
            title.serializeToStream(stream);
            if ((flags & 1) > 0) {
                stream.writeString(emoticon);
            }
            Vector.serialize(stream, peers);
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);
        }
    }

    public static class TL_chatlists_chatlistInvite_layer195 extends TL_chatlists_chatlistInvite {
        public static final int constructor = 0x1dcd839d;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            title = new TLRPC.TL_textWithEntities();
            title.text = stream.readString(exception);
            if ((flags & 1) > 0) {
                emoticon = stream.readString(exception);
            }
            peers = Vector.deserialize(stream, TLRPC.Peer::TLdeserialize, exception);
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            title.serializeToStream(stream);
            if ((flags & 1) > 0) {
                stream.writeString(emoticon);
            }
            Vector.serialize(stream, peers);
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);
        }
    }

    public static class TL_chatlists_chatlistUpdates extends TLObject {
        public static final int constructor = 0x93bd878d;

        public ArrayList<TLRPC.Peer> missing_peers = new ArrayList<>();
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static TL_chatlists_chatlistUpdates TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
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

        public void readParams(InputSerializedData stream, boolean exception) {
            missing_peers = Vector.deserialize(stream, TLRPC.Peer::TLdeserialize, exception);
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, missing_peers);
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);
        }
    }

    public static class TL_chatlists_exportChatlistInvite extends TLObject {
        public static final int constructor = 0x8472478e;

        public TL_inputChatlistDialogFilter chatlist;
        public String title;
        public ArrayList<TLRPC.InputPeer> peers = new ArrayList<>();

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_chatlists_exportedChatlistInvite.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            chatlist.serializeToStream(stream);
            stream.writeString(title);
            Vector.serialize(stream, peers);
        }
    }

    public static class TL_chatlists_deleteExportedInvite extends TLObject {
        public static final int constructor = 0x719c5c5e;

        public TL_inputChatlistDialogFilter chatlist;
        public String slug;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
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

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_exportedChatlistInvite.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = revoked ? (flags | 1) : (flags &~ 1);
            stream.writeInt32(flags);
            chatlist.serializeToStream(stream);
            stream.writeString(slug);
            if ((flags & 2) != 0) {
                stream.writeString(title);
            }
            if ((flags & 4) != 0) {
                Vector.serialize(stream, peers);
            }
        }
    }

    public static class TL_chatlists_getExportedInvites extends TLObject {
        public static final int constructor = 0xce03da83;

        public TL_inputChatlistDialogFilter chatlist;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_chatlists_exportedInvites.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            chatlist.serializeToStream(stream);
        }
    }

    public static class TL_chatlists_checkChatlistInvite extends TLObject {
        public static final int constructor = 0x41c10fff;

        public String slug;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return chatlist_ChatlistInvite.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(slug);
        }
    }

    public static class TL_chatlists_joinChatlistInvite extends TLObject {
        public static final int constructor = 0xa6b1e39a;

        public String slug;
        public ArrayList<TLRPC.InputPeer> peers = new ArrayList<>();

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(slug);
            Vector.serialize(stream, peers);
        }
    }

    public static class TL_chatlists_getChatlistUpdates extends TLObject {
        public static final int constructor = 0x89419521;

        public TL_inputChatlistDialogFilter chatlist;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_chatlists_chatlistUpdates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            chatlist.serializeToStream(stream);
        }
    }

    public static class TL_chatlists_joinChatlistUpdates extends TLObject {
        public static final int constructor = 0xe089f8f5;

        public TL_inputChatlistDialogFilter chatlist;
        public ArrayList<TLRPC.InputPeer> peers = new ArrayList<>();

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            chatlist.serializeToStream(stream);
            Vector.serialize(stream, peers);
        }
    }

    public static class TL_chatlists_hideChatlistUpdates extends TLObject {
        public static final int constructor = 0x66e486fb;

        public TL_inputChatlistDialogFilter chatlist;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            chatlist.serializeToStream(stream);
        }
    }

    public static class TL_chatlists_getLeaveChatlistSuggestions extends TLObject {
        public static final int constructor = 0xfdbcd714;

        public TL_inputChatlistDialogFilter chatlist;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return Vector.TLDeserialize(stream, constructor, exception, TLRPC.Peer::TLdeserialize);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            chatlist.serializeToStream(stream);
        }
    }

    public static class TL_chatlists_leaveChatlist extends TLObject {
        public static final int constructor = 0x74fae13a;

        public TL_inputChatlistDialogFilter chatlist;
        public ArrayList<TLRPC.InputPeer> peers = new ArrayList<>();

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            chatlist.serializeToStream(stream);
            Vector.serialize(stream, peers);
        }
    }
}

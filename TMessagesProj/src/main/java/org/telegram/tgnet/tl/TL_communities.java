package org.telegram.tgnet.tl;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLMethod;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;

import java.util.ArrayList;

public class TL_communities {
    private TL_communities() {

    }



    public static class ParticipantJoinedChats extends TLObject {
        public static final int constructor = 0x8D78512A;

        public ArrayList<Long> creator_chat_ids = new ArrayList<>();
        public ArrayList<Long> joined_chat_ids = new ArrayList<>();
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static ParticipantJoinedChats TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final ParticipantJoinedChats result = constructor != ParticipantJoinedChats.constructor ? null : new ParticipantJoinedChats();
            return TLdeserialize(ParticipantJoinedChats.class, result, stream, constructor, exception);
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            creator_chat_ids = Vector.deserializeLong(stream, exception);
            joined_chat_ids = Vector.deserializeLong(stream, exception);
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serializeLong(stream, creator_chat_ids);
            Vector.serializeLong(stream, joined_chat_ids);
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);
        }
    }

    public static class PeerLinkRequests extends TLObject {
        public static final int constructor = 0x2244AFAD;

        public static PeerLinkRequests TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final PeerLinkRequests result = constructor != PeerLinkRequests.constructor ? null : new PeerLinkRequests();
            return TLdeserialize(PeerLinkRequests.class, result, stream, constructor, exception);
        }

        public int flags;
        public int total_count;
        public ArrayList<CommunityPeerRequest> requests = new ArrayList<>();
        public String next_offset;
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            total_count = stream.readInt32(exception);
            requests = Vector.deserialize(stream, CommunityPeerRequest::TLdeserialize, exception);
            if (hasFlag(flags, FLAG_0)) {
                next_offset = stream.readString(exception);
            }
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, next_offset != null);
            stream.writeInt32(flags);
            stream.writeInt32(total_count);
            Vector.serialize(stream, requests);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeString(next_offset);
            }
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);
        }
    }

    public static class CommunityPeer extends TLObject {
        public static final int constructor = 0x76141EBD;

        public static CommunityPeer TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final CommunityPeer result = constructor != CommunityPeer.constructor ? null : new CommunityPeer();
            return TLdeserialize(CommunityPeer.class, result, stream, constructor, exception);
        }

        public int flags;
        public boolean can_view_history;
        public boolean visible;
        public TLRPC.Peer peer;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            can_view_history = hasFlag(flags, FLAG_2);
            if (hasFlag(flags, FLAG_0)) {
                visible = stream.readBool(exception);
            }
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_2, can_view_history);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeBool(visible);
            }
            peer.serializeToStream(stream);
        }
    }

    public static class CommunityPeerRequest extends TLObject {
        public static final int constructor = 0x7BEAFA85;

        public int flags;
        public boolean visible;
        public TLRPC.Peer peer;
        public long requested_by;
        public int date;

        public static CommunityPeerRequest TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final CommunityPeerRequest result = constructor != CommunityPeerRequest.constructor ? null : new CommunityPeerRequest();
            return TLdeserialize(CommunityPeerRequest.class, result, stream, constructor, exception);
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            visible = hasFlag(flags, FLAG_0);
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            requested_by = stream.readInt64(exception);
            date = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, visible);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            stream.writeInt64(requested_by);
            stream.writeInt32(date);
        }
    }



    public static class TL_communities_create extends TLMethod<TLRPC.Updates> {
        public static final int constructor = 0xA63859EC;

        public int flags;
        public boolean hidden;
        public String title;
        public String about;
        public TLRPC.InputPeer peer;

        public TLRPC.Updates deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_1, hidden);
            flags = setFlag(flags, FLAG_0, about != null);
            stream.writeInt32(flags);
            stream.writeString(title);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeString(about);
            }
            peer.serializeToStream(stream);
        }
    }

    public static class TL_communities_togglePeerLink extends TLMethod<TLRPC.Bool> {
        public static final int constructor = 0x736DCFEA;

        public int flags;
        public boolean visible;
        public boolean hidden;
        public boolean deleted;
        public TLRPC.InputChannel community;
        public TLRPC.InputPeer peer;

        public TLRPC.Bool deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, visible);
            flags = setFlag(flags, FLAG_1, hidden);
            flags = setFlag(flags, FLAG_2, deleted);
            stream.writeInt32(flags);
            community.serializeToStream(stream);
            peer.serializeToStream(stream);
        }
    }

    public static class TL_communities_getJoinedCommunities extends TLMethod<TLRPC.messages_Chats> {
        public static final int constructor = 0xA663E830;

        public TLRPC.messages_Chats deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.messages_Chats.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_communities_toggleCommunityCollapsedInDialogs extends TLMethod<TLRPC.Updates> {
        public static final int constructor = 0xD766E3EA;

        public int flags;
        public boolean collapsed;
        public TLRPC.InputChannel community;

        public TLRPC.Updates deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, collapsed);
            stream.writeInt32(flags);
            community.serializeToStream(stream);
        }
    }

    public static class TL_communities_getPeerLinkRequests extends TLMethod<PeerLinkRequests> {
        public static final int constructor = 0x93773344;

        public TLRPC.InputChannel community;
        public String offset;
        public int limit;

        public PeerLinkRequests deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return PeerLinkRequests.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            community.serializeToStream(stream);
            stream.writeString(offset);
            stream.writeInt32(limit);
        }
    }

    public static class TL_communities_togglePeerLinkRequestApproval extends TLMethod<TLRPC.Bool> {
        public static final int constructor = 0x8C8219A8;

        public int flags;
        public boolean reject;
        public TLRPC.InputChannel community;
        public TLRPC.InputPeer peer;

        public TLRPC.Bool deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, reject);
            stream.writeInt32(flags);
            community.serializeToStream(stream);
            peer.serializeToStream(stream);
        }
    }

    public static class TL_communities_toggleAllPeerLinkRequestApproval extends TLMethod<TLRPC.Bool> {
        public static final int constructor = 0xBFE3DD3D;

        public int flags;
        public boolean reject;
        public TLRPC.InputChannel community;

        public TLRPC.Bool deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, reject);
            stream.writeInt32(flags);
            community.serializeToStream(stream);
        }
    }

    public static class TL_communities_toggleParticipantBanned extends TLMethod<TLRPC.Bool> {
        public static final int constructor = 0x9967AD0F;

        public int flags;
        public boolean unban;
        public TLRPC.InputChannel community;
        public TLRPC.InputPeer participant;

        public TLRPC.Bool deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, unban);
            stream.writeInt32(flags);
            community.serializeToStream(stream);
            participant.serializeToStream(stream);
        }
    }

    public static class TL_communities_getParticipantJoinedChats extends TLMethod<ParticipantJoinedChats> {
        public static final int constructor = 0xF87EABAB;

        public TLRPC.InputChannel community;
        public TLRPC.InputPeer participant;

        public ParticipantJoinedChats deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return ParticipantJoinedChats.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            community.serializeToStream(stream);
            participant.serializeToStream(stream);
        }
    }
}

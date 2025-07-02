package org.telegram.tgnet.tl;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;

import java.util.ArrayList;

public class TL_phone {

    public static class joinAsPeers extends TLObject {
        public static final int constructor = 0xafe5623f;

        public ArrayList<TLRPC.Peer> peers = new ArrayList<>();
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static joinAsPeers TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (joinAsPeers.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_phone_joinAsPeers", constructor));
                } else {
                    return null;
                }
            }
            joinAsPeers result = new joinAsPeers();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            peers = Vector.deserialize(stream, TLRPC.Peer::TLdeserialize, exception);
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, peers);
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);
        }
    }

    public static class groupCall extends TLObject {
        public static final int constructor = 0x9e727aad;

        public TLRPC.GroupCall call;
        public ArrayList<TLRPC.GroupCallParticipant> participants = new ArrayList<>();
        public String participants_next_offset;
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static groupCall TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (groupCall.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_phone_groupCall", constructor));
                } else {
                    return null;
                }
            }
            groupCall result = new groupCall();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            call = TLRPC.GroupCall.TLdeserialize(stream, stream.readInt32(exception), exception);
            participants = Vector.deserialize(stream, TLRPC.GroupCallParticipant::TLdeserialize, exception);
            participants_next_offset = stream.readString(exception);
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            call.serializeToStream(stream);
            Vector.serialize(stream, participants);
            stream.writeString(participants_next_offset);
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);
        }
    }

    public static abstract class PhoneCall extends TLObject {

        public int flags;
        public boolean p2p_allowed;
        public long id;
        public long access_hash;
        public int date;
        public long admin_id;
        public long participant_id;
        public byte[] g_a_or_b;
        public long key_fingerprint;
        public PhoneCallProtocol protocol;
        public ArrayList<TLRPC.PhoneConnection> connections = new ArrayList<>();
        public int start_date;
        public boolean need_rating;
        public boolean need_debug;
        public boolean video;
        public boolean conference_supported;
        public TLRPC.PhoneCallDiscardReason reason;
        public int duration;
        public byte[] g_a_hash;
        public byte[] g_b;
        public int receive_date;
        public TLRPC.TL_dataJSON custom_parameters;

        public static PhoneCall TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            PhoneCall result = null;
            switch (constructor) {
                case phoneCallRequested.constructor:
                    result = new phoneCallRequested();
                    break;
                case TL_phoneCall.constructor:
                    result = new TL_phoneCall();
                    break;
                case TL_phoneCall_layer176.constructor:
                    result = new TL_phoneCall_layer176();
                    break;
                case TL_phoneCallEmpty.constructor:
                    result = new TL_phoneCallEmpty();
                    break;
                case TL_phoneCallAccepted.constructor:
                    result = new TL_phoneCallAccepted();
                    break;
                case TL_phoneCallWaiting.constructor:
                    result = new TL_phoneCallWaiting();
                    break;
                case TL_phoneCallDiscarded.constructor:
                    result = new TL_phoneCallDiscarded();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in PhoneCall", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class phoneCallRequested extends PhoneCall {
        public static final int constructor = 0x14b0ed0c;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            video = (flags & 64) != 0;
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            date = stream.readInt32(exception);
            admin_id = stream.readInt64(exception);
            participant_id = stream.readInt64(exception);
            g_a_hash = stream.readByteArray(exception);
            protocol = PhoneCallProtocol.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = video ? (flags | 64) : (flags &~ 64);
            stream.writeInt32(flags);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(date);
            stream.writeInt64(admin_id);
            stream.writeInt64(participant_id);
            stream.writeByteArray(g_a_hash);
            protocol.serializeToStream(stream);
        }
    }

    public static class TL_phoneCall extends PhoneCall {
        public static final int constructor = 0x30535af5;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            p2p_allowed = (flags & 32) != 0;
            video = (flags & 64) != 0;
            conference_supported = (flags & 256) != 0;
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            date = stream.readInt32(exception);
            admin_id = stream.readInt64(exception);
            participant_id = stream.readInt64(exception);
            g_a_or_b = stream.readByteArray(exception);
            key_fingerprint = stream.readInt64(exception);
            protocol = PhoneCallProtocol.TLdeserialize(stream, stream.readInt32(exception), exception);
            connections = Vector.deserialize(stream, TLRPC.PhoneConnection::TLdeserialize, exception);
            start_date = stream.readInt32(exception);
            if ((flags & 128) != 0) {
                custom_parameters = TLRPC.TL_dataJSON.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = p2p_allowed ? (flags | 32) : (flags &~ 32);
            flags = video ? (flags | 64) : (flags &~ 64);
            flags = conference_supported ? (flags | 256) : (flags &~ 256);
            stream.writeInt32(flags);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(date);
            stream.writeInt64(admin_id);
            stream.writeInt64(participant_id);
            stream.writeByteArray(g_a_or_b);
            stream.writeInt64(key_fingerprint);
            protocol.serializeToStream(stream);
            Vector.serialize(stream, connections);
            stream.writeInt32(start_date);
            if ((flags & 128) != 0) {
                custom_parameters.serializeToStream(stream);
            }
        }
    }

    public static class TL_phoneCall_layer176 extends TL_phoneCall {
        public static final int constructor = 0x967f7c67;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            p2p_allowed = (flags & 32) != 0;
            video = (flags & 64) != 0;
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            date = stream.readInt32(exception);
            admin_id = stream.readInt64(exception);
            participant_id = stream.readInt64(exception);
            g_a_or_b = stream.readByteArray(exception);
            key_fingerprint = stream.readInt64(exception);
            protocol = PhoneCallProtocol.TLdeserialize(stream, stream.readInt32(exception), exception);
            connections = Vector.deserialize(stream, TLRPC.PhoneConnection::TLdeserialize, exception);
            start_date = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = p2p_allowed ? (flags | 32) : (flags &~ 32);
            flags = video ? (flags | 64) : (flags &~ 64);
            stream.writeInt32(flags);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(date);
            stream.writeInt64(admin_id);
            stream.writeInt64(participant_id);
            stream.writeByteArray(g_a_or_b);
            stream.writeInt64(key_fingerprint);
            protocol.serializeToStream(stream);
            Vector.serialize(stream, connections);
            stream.writeInt32(start_date);
        }
    }

    public static class TL_phoneCallEmpty extends PhoneCall {
        public static final int constructor = 0x5366c915;

        public void readParams(InputSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
        }
    }

    public static class TL_phoneCallAccepted extends PhoneCall {
        public static final int constructor = 0x3660c311;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            video = (flags & 64) != 0;
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            date = stream.readInt32(exception);
            admin_id = stream.readInt64(exception);
            participant_id = stream.readInt64(exception);
            g_b = stream.readByteArray(exception);
            protocol = PhoneCallProtocol.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = video ? (flags | 64) : (flags &~ 64);
            stream.writeInt32(flags);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(date);
            stream.writeInt64(admin_id);
            stream.writeInt64(participant_id);
            stream.writeByteArray(g_b);
            protocol.serializeToStream(stream);
        }
    }

    public static class TL_phoneCallWaiting extends PhoneCall {
        public static final int constructor = 0xc5226f17;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            video = (flags & 64) != 0;
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            date = stream.readInt32(exception);
            admin_id = stream.readInt64(exception);
            participant_id = stream.readInt64(exception);
            protocol = PhoneCallProtocol.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 1) != 0) {
                receive_date = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = video ? (flags | 64) : (flags &~ 64);
            stream.writeInt32(flags);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(date);
            stream.writeInt64(admin_id);
            stream.writeInt64(participant_id);
            protocol.serializeToStream(stream);
            if ((flags & 1) != 0) {
                stream.writeInt32(receive_date);
            }
        }
    }

    public static class TL_phoneCallDiscarded extends PhoneCall {
        public static final int constructor = 0x50ca4de1;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            need_rating = (flags & 4) != 0;
            need_debug = (flags & 8) != 0;
            video = (flags & 64) != 0;
            id = stream.readInt64(exception);
            if ((flags & 1) != 0) {
                reason = TLRPC.PhoneCallDiscardReason.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 2) != 0) {
                duration = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = need_rating ? (flags | 4) : (flags &~ 4);
            flags = need_debug ? (flags | 8) : (flags &~ 8);
            flags = video ? (flags | 64) : (flags &~ 64);
            stream.writeInt32(flags);
            stream.writeInt64(id);
            if ((flags & 1) != 0) {
                reason.serializeToStream(stream);
            }
            if ((flags & 2) != 0) {
                stream.writeInt32(duration);
            }
        }
    }

    public static class groupCallStreamRtmpUrl extends TLObject {
        public static final int constructor = 0x2dbf3432;

        public String url;
        public String key;

        public static groupCallStreamRtmpUrl TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (groupCallStreamRtmpUrl.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_phone_groupCallStreamRtmpUrl", constructor));
                } else {
                    return null;
                }
            }
            groupCallStreamRtmpUrl result = new groupCallStreamRtmpUrl();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            url = stream.readString(exception);
            key = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(url);
            stream.writeString(key);
        }
    }

    public static class TL_phone_phoneCall extends TLObject {
        public static final int constructor = 0xec82e140;

        public PhoneCall phone_call;
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static TL_phone_phoneCall TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (TL_phone_phoneCall.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_phone_phoneCall", constructor));
                } else {
                    return null;
                }
            }
            TL_phone_phoneCall result = new TL_phone_phoneCall();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            phone_call = PhoneCall.TLdeserialize(stream, stream.readInt32(exception), exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            phone_call.serializeToStream(stream);
            Vector.serialize(stream, users);
        }
    }

    public static class groupParticipants extends TLObject {
        public static final int constructor = 0xf47751b6;

        public int count;
        public ArrayList<TLRPC.GroupCallParticipant> participants = new ArrayList<>();
        public String next_offset;
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();
        public int version;

        public static groupParticipants TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (groupParticipants.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_phone_groupParticipants", constructor));
                } else {
                    return null;
                }
            }
            groupParticipants result = new groupParticipants();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            count = stream.readInt32(exception);
            participants = Vector.deserialize(stream, TLRPC.GroupCallParticipant::TLdeserialize, exception);
            next_offset = stream.readString(exception);
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
            version = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(count);
            Vector.serialize(stream, participants);
            stream.writeString(next_offset);
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);
            stream.writeInt32(version);
        }
    }

    public static class exportedGroupCallInvite extends TLObject {
        public static final int constructor = 0x204bd158;

        public String link;

        public static exportedGroupCallInvite TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (exportedGroupCallInvite.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_phone_exportedGroupCallInvite", constructor));
                } else {
                    return null;
                }
            }
            exportedGroupCallInvite result = new exportedGroupCallInvite();
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

    public static class getCallConfig extends TLObject {
        public static final int constructor = 0x55451fa9;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.TL_dataJSON.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class requestCall extends TLObject {
        public static final int constructor = 0x42ff96ed;

        public int flags;
        public boolean video;
        public TLRPC.InputUser user_id;
        public int random_id;
        public byte[] g_a_hash;
        public TL_phoneCallProtocol protocol;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_phone_phoneCall.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = video ? (flags | 1) : (flags &~ 1);
            stream.writeInt32(flags);
            user_id.serializeToStream(stream);
            stream.writeInt32(random_id);
            stream.writeByteArray(g_a_hash);
            protocol.serializeToStream(stream);
        }
    }

    public static class acceptCall extends TLObject {
        public static final int constructor = 0x3bd2b4a0;

        public TLRPC.TL_inputPhoneCall peer;
        public byte[] g_b;
        public TL_phoneCallProtocol protocol;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_phone_phoneCall.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeByteArray(g_b);
            protocol.serializeToStream(stream);
        }
    }

    public static class confirmCall extends TLObject {
        public static final int constructor = 0x2efe1722;

        public TLRPC.TL_inputPhoneCall peer;
        public byte[] g_a;
        public long key_fingerprint;
        public TL_phoneCallProtocol protocol;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_phone_phoneCall.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeByteArray(g_a);
            stream.writeInt64(key_fingerprint);
            protocol.serializeToStream(stream);
        }
    }

    public static class receivedCall extends TLObject {
        public static final int constructor = 0x17d54f61;

        public TLRPC.TL_inputPhoneCall peer;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
        }
    }

    public static class discardCall extends TLObject {
        public static final int constructor = 0xb2cbc1c0;

        public int flags;
        public boolean video;
        public TLRPC.TL_inputPhoneCall peer;
        public int duration;
        public TLRPC.PhoneCallDiscardReason reason;
        public long connection_id;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = video ? (flags | 1) : (flags &~ 1);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            stream.writeInt32(duration);
            reason.serializeToStream(stream);
            stream.writeInt64(connection_id);
        }
    }

    public static class setCallRating extends TLObject {
        public static final int constructor = 0x59ead627;

        public int flags;
        public boolean user_initiative;
        public TLRPC.TL_inputPhoneCall peer;
        public int rating;
        public String comment;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = user_initiative ? (flags | 1) : (flags &~ 1);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            stream.writeInt32(rating);
            stream.writeString(comment);
        }
    }

    public static class saveCallDebug extends TLObject {
        public static final int constructor = 0x277add7e;

        public TLRPC.TL_inputPhoneCall peer;
        public TLRPC.TL_dataJSON debug;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            debug.serializeToStream(stream);
        }
    }

    public static class sendSignalingData extends TLObject {
        public static final int constructor = 0xff7a9383;

        public TLRPC.TL_inputPhoneCall peer;
        public byte[] data;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeByteArray(data);
        }
    }

    public static class createGroupCall extends TLObject {
        public static final int constructor = 0x48cdc6d8;

        public int flags;
        public TLRPC.InputPeer peer;
        public int random_id;
        public String title;
        public int schedule_date;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            stream.writeInt32(random_id);
            if ((flags & 1) != 0) {
                stream.writeString(title);
            }
            if ((flags & 2) != 0) {
                stream.writeInt32(schedule_date);
            }
        }
    }

    public static class joinGroupCall extends TLObject {
        public static final int constructor = 0x8fb53057;

        public int flags;
        public boolean muted;
        public boolean video_stopped;
        public TLRPC.InputGroupCall call;
        public TLRPC.InputPeer join_as;
        public String invite_hash;
        public byte[] public_key;
        public byte[] block;
        public TLRPC.TL_dataJSON params;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = muted ? (flags | 1) : (flags &~ 1);
            flags = video_stopped ? (flags | 4) : (flags &~ 4);
            stream.writeInt32(flags);
            call.serializeToStream(stream);
            join_as.serializeToStream(stream);
            if ((flags & 2) != 0) {
                stream.writeString(invite_hash);
            }
            if ((flags & 8) != 0) {
                stream.writeBytes(public_key);
                stream.writeByteArray(block);
            }
            params.serializeToStream(stream);
        }
    }

    public static class leaveGroupCall extends TLObject {
        public static final int constructor = 0x500377f9;

        public TLRPC.InputGroupCall call;
        public int source;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            call.serializeToStream(stream);
            stream.writeInt32(source);
        }
    }

    public static class inviteToGroupCall extends TLObject {
        public static final int constructor = 0x7b393160;

        public TLRPC.InputGroupCall call;
        public ArrayList<TLRPC.InputUser> users = new ArrayList<>();

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            call.serializeToStream(stream);
            Vector.serialize(stream, users);
        }
    }

    public static class discardGroupCall extends TLObject {
        public static final int constructor = 0x7a777135;

        public TLRPC.InputGroupCall call;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            call.serializeToStream(stream);
        }
    }

    public static class toggleGroupCallSettings extends TLObject {
        public static final int constructor = 0x74bbb43d;

        public int flags;
        public boolean reset_invite_hash;
        public TLRPC.InputGroupCall call;
        public boolean join_muted;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = reset_invite_hash ? (flags | 2) : (flags &~ 2);
            stream.writeInt32(flags);
            call.serializeToStream(stream);
            if ((flags & 1) != 0) {
                stream.writeBool(join_muted);
            }
        }
    }

    public static class getGroupCall extends TLObject {
        public static final int constructor = 0x41845db;

        public TLRPC.InputGroupCall call;
        public int limit;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return groupCall.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            call.serializeToStream(stream);
            stream.writeInt32(limit);
        }
    }

    public static class getGroupParticipants extends TLObject {
        public static final int constructor = 0xc558d8ab;

        public TLRPC.InputGroupCall call;
        public ArrayList<TLRPC.InputPeer> ids = new ArrayList<>();
        public ArrayList<Integer> sources = new ArrayList<>();
        public String offset;
        public int limit;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return groupParticipants.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            call.serializeToStream(stream);
            Vector.serialize(stream, ids);
            Vector.serializeInt(stream, sources);
            stream.writeString(offset);
            stream.writeInt32(limit);
        }
    }

    public static class checkGroupCall extends TLObject {
        public static final int constructor = 0xb59cf977;

        public TLRPC.InputGroupCall call;
        public ArrayList<Integer> sources = new ArrayList<>();

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return Vector.TLDeserializeInt(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            call.serializeToStream(stream);
            Vector.serializeInt(stream, sources);
        }
    }

    public static class toggleGroupCallRecord extends TLObject {
        public static final int constructor = 0xf128c708;

        public int flags;
        public boolean start;
        public boolean video;
        public TLRPC.InputGroupCall call;
        public String title;
        public boolean video_portrait;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = start ? (flags | 1) : (flags &~ 1);
            flags = video ? (flags | 4) : (flags &~ 4);
            stream.writeInt32(flags);
            call.serializeToStream(stream);
            if ((flags & 2) != 0) {
                stream.writeString(title);
            }
            if ((flags & 4) != 0) {
                stream.writeBool(video_portrait);
            }
        }
    }

    public static class editGroupCallParticipant extends TLObject {
        public static final int constructor = 0xa5273abf;

        public int flags;
        public TLRPC.InputGroupCall call;
        public TLRPC.InputPeer participant;
        public boolean muted;
        public int volume;
        public boolean raise_hand;
        public boolean video_stopped;
        public boolean video_paused;
        public boolean presentation_paused;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            call.serializeToStream(stream);
            participant.serializeToStream(stream);
            if ((flags & 1) != 0) {
                stream.writeBool(muted);
            }
            if ((flags & 2) != 0) {
                stream.writeInt32(volume);
            }
            if ((flags & 4) != 0) {
                stream.writeBool(raise_hand);
            }
            if ((flags & 8) != 0) {
                stream.writeBool(video_stopped);
            }
            if ((flags & 16) != 0) {
                stream.writeBool(video_paused);
            }
            if ((flags & 32) != 0) {
                stream.writeBool(presentation_paused);
            }
        }
    }

    public static class editGroupCallTitle extends TLObject {
        public static final int constructor = 0x1ca6ac0a;

        public TLRPC.InputGroupCall call;
        public String title;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            call.serializeToStream(stream);
            stream.writeString(title);
        }
    }

    public static class getGroupCallJoinAs extends TLObject {
        public static final int constructor = 0xef7c213a;

        public TLRPC.InputPeer peer;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return joinAsPeers.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
        }
    }

    public static class exportGroupCallInvite extends TLObject {
        public static final int constructor = 0xe6aa647f;

        public int flags;
        public boolean can_self_unmute;
        public TLRPC.InputGroupCall call;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return exportedGroupCallInvite.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = can_self_unmute ? (flags | 1) : (flags &~ 1);
            stream.writeInt32(flags);
            call.serializeToStream(stream);
        }
    }

    public static class toggleGroupCallStartSubscription extends TLObject {
        public static final int constructor = 0x219c34e6;

        public TLRPC.InputGroupCall call;
        public boolean subscribed;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            call.serializeToStream(stream);
            stream.writeBool(subscribed);
        }
    }

    public static class startScheduledGroupCall extends TLObject {
        public static final int constructor = 0x5680e342;

        public TLRPC.InputGroupCall call;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            call.serializeToStream(stream);
        }
    }

    public static class saveDefaultGroupCallJoinAs extends TLObject {
        public static final int constructor = 0x575e1f8c;

        public TLRPC.InputPeer peer;
        public TLRPC.InputPeer join_as;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            join_as.serializeToStream(stream);
        }
    }

    public static class joinGroupCallPresentation extends TLObject {
        public static final int constructor = 0xcbea6bc4;

        public TLRPC.InputGroupCall call;
        public TLRPC.TL_dataJSON params;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            call.serializeToStream(stream);
            params.serializeToStream(stream);
        }
    }

    public static class TL_groupCallStreamChannel extends TLObject {
        public static final int constructor = 0x80eb48af;

        public int channel;
        public int scale;
        public long last_timestamp_ms;

        public static TL_groupCallStreamChannel TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (TL_groupCallStreamChannel.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_groupCallStreamChannel", constructor));
                } else {
                    return null;
                }
            }
            TL_groupCallStreamChannel result = new TL_groupCallStreamChannel();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            channel = stream.readInt32(exception);
            scale = stream.readInt32(exception);
            last_timestamp_ms = stream.readInt64(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(channel);
            stream.writeInt32(scale);
            stream.writeInt64(last_timestamp_ms);
        }
    }

    public static class groupCallStreamChannels extends TLObject {
        public static final int constructor = 0xd0e482b2;

        public ArrayList<TL_groupCallStreamChannel> channels = new ArrayList<>();

        public static groupCallStreamChannels TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (groupCallStreamChannels.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_phone_groupCallStreamChannels", constructor));
                } else {
                    return null;
                }
            }
            groupCallStreamChannels result = new groupCallStreamChannels();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            channels = Vector.deserialize(stream, TL_groupCallStreamChannel::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, channels);
        }
    }

    public static class leaveGroupCallPresentation extends TLObject {
        public static final int constructor = 0x1c50d144;

        public TLRPC.InputGroupCall call;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            call.serializeToStream(stream);
        }
    }

    public static class getGroupCallStreamChannels extends TLObject {
        public static final int constructor = 0x1ab21940;

        public TLRPC.InputGroupCall call;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return groupCallStreamChannels.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            call.serializeToStream(stream);
        }
    }

    public static class getGroupCallStreamRtmpUrl extends TLObject {
        public static final int constructor = 0xdeb3abbf;

        public TLRPC.InputPeer peer;
        public boolean revoke;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return groupCallStreamRtmpUrl.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeBool(revoke);
        }
    }

    public static abstract class PhoneCallProtocol extends TLObject {
        public int flags;
        public boolean udp_p2p;
        public boolean udp_reflector;
        public int min_layer;
        public int max_layer;
        public ArrayList<String> library_versions = new ArrayList<>();

        public static PhoneCallProtocol TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            PhoneCallProtocol result = null;
            switch (constructor) {
                case 0xfc878fc8:
                    result = new TL_phoneCallProtocol();
                    break;
                case 0xa2bb35cb:
                    result = new TL_phoneCallProtocol_layer110();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in PhoneCallProtocol", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_phoneCallProtocol extends PhoneCallProtocol {
        public static final int constructor = 0xfc878fc8;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            udp_p2p = (flags & 1) != 0;
            udp_reflector = (flags & 2) != 0;
            min_layer = stream.readInt32(exception);
            max_layer = stream.readInt32(exception);
            library_versions = Vector.deserializeString(stream, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = udp_p2p ? (flags | 1) : (flags &~ 1);
            flags = udp_reflector ? (flags | 2) : (flags &~ 2);
            stream.writeInt32(flags);
            stream.writeInt32(min_layer);
            stream.writeInt32(max_layer);
            Vector.serializeString(stream, library_versions);
        }
    }

    public static class TL_phoneCallProtocol_layer110 extends TL_phoneCallProtocol {
        public static final int constructor = 0xa2bb35cb;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            udp_p2p = (flags & 1) != 0;
            udp_reflector = (flags & 2) != 0;
            min_layer = stream.readInt32(exception);
            max_layer = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = udp_p2p ? (flags | 1) : (flags &~ 1);
            flags = udp_reflector ? (flags | 2) : (flags &~ 2);
            stream.writeInt32(flags);
            stream.writeInt32(min_layer);
            stream.writeInt32(max_layer);
        }
    }

    public static class saveCallLog extends TLObject {
        public static final int constructor = 0x41248786;

        public TLRPC.TL_inputPhoneCall peer;
        public TLRPC.InputFile file;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            file.serializeToStream(stream);
        }
    }

    public static class createConferenceCall extends TLObject {
        public static final int constructor = 0x7d0444bb;

        public int flags;
        public boolean muted;
        public boolean video_stopped;
        public boolean join;
        public int random_id;
        public byte[] public_key;
        public byte[] block;
        public TLRPC.TL_dataJSON params;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = muted ? flags | 1 : flags &~ 1;
            flags = video_stopped ? flags | 4 : flags &~ 4;
            flags = join ? flags | 8 : flags &~ 8;
            stream.writeInt32(flags);
            stream.writeInt32(random_id);
            if ((flags & 8) != 0) {
                stream.writeBytes(public_key);
                stream.writeByteArray(block);
                params.serializeToStream(stream);
            }
        }
    }

    public static class deleteConferenceCallParticipants extends TLObject {
        public static final int constructor = 0x8ca60525;

        public int flags;
        public boolean only_left;
        public boolean kick;
        public TLRPC.InputGroupCall call;
        public ArrayList<Long> ids = new ArrayList<>();
        public byte[] block;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = only_left ? (flags | 1) : (flags &~ 1);
            flags = kick ? (flags | 2) : (flags &~ 2);
            stream.writeInt32(flags);
            call.serializeToStream(stream);
            Vector.serializeLong(stream, ids);
            stream.writeByteArray(block);
        }
    }

    public static class sendConferenceCallBroadcast extends TLObject {
        public static final int constructor = 0xc6701900;

        public TLRPC.InputGroupCall call;
        public byte[] block;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            call.serializeToStream(stream);
            stream.writeByteArray(block);
        }
    }

    public static class inviteConferenceCallParticipant extends TLObject {
        public static final int constructor = 0xbcf22685;

        public int flags;
        public boolean video;
        public TLRPC.InputGroupCall call;
        public TLRPC.InputUser user_id;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = video ? flags | 1 : flags &~ 1;
            stream.writeInt32(flags);
            call.serializeToStream(stream);
            user_id.serializeToStream(stream);
        }
    }

    public static class declineConferenceCallInvite extends TLObject {
        public static final int constructor = 0x3c479971;

        public int msg_id;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(msg_id);
        }
    }

    public static class getGroupCallChainBlocks extends TLObject {
        public static final int constructor = 0xee9f88a6;

        public TLRPC.InputGroupCall call;
        public int sub_chain_id;
        public int offset;
        public int limit;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            call.serializeToStream(stream);
            stream.writeInt32(sub_chain_id);
            stream.writeInt32(offset);
            stream.writeInt32(limit);
        }
    }

}

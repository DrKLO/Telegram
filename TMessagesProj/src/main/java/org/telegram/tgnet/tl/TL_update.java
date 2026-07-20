package org.telegram.tgnet.tl;

import org.telegram.messenger.DialogObject;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;

import java.util.ArrayList;

public class TL_update {
    private TL_update() {

    }

    public static class TL_updateStarsRevenueStatus extends TLRPC.Update {
        public static final int constructor = 0xa584b019;

        public TLRPC.Peer peer;
        public TLRPC.TL_starsRevenueStatus status;

        public void readParams(InputSerializedData stream, boolean exception) {
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            status = TLRPC.TL_starsRevenueStatus.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            status.serializeToStream(stream);
        }
    }

    public static class TL_updateBotPurchasedPaidMedia extends TLRPC.Update {
        public static final int constructor = 0x283bd312;

        public long user_id;
        public String payload;
        public int qts;

        public void readParams(InputSerializedData stream, boolean exception) {
            user_id = stream.readInt64(exception);
            payload = stream.readString(exception);
            qts = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(user_id);
            stream.writeString(payload);
            stream.writeInt32(qts);
        }
    }

    public static class TL_updatePaidReactionPrivacy extends TLRPC.Update {
        public static final int constructor = 0x8b725fce;

        public TL_stars.PaidReactionPrivacy privacy;

        public void readParams(InputSerializedData stream, boolean exception) {
            privacy = TL_stars.PaidReactionPrivacy.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            privacy.serializeToStream(stream);
        }
    }

    public static class TL_updateBotSubscriptionExpire extends TLRPC.Update {
        public static final int constructor = 0xa8ae3eb1;

        public long user_id;
        public String payload;
        public int until_date;
        public int qts;

        public void readParams(InputSerializedData stream, boolean exception) {
            user_id = stream.readInt64(exception);
            payload = stream.readString(exception);
            until_date = stream.readInt32(exception);
            qts = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(user_id);
            stream.writeString(payload);
            stream.writeInt32(until_date);
            stream.writeInt32(qts);
        }
    }

    public static class TL_updateSentPhoneCode extends TLRPC.Update {
        public static final int constructor = 0x504aa18f;

        public TLRPC.auth_SentCode sent_code;

        public void readParams(InputSerializedData stream, boolean exception) {
            sent_code = TLRPC.auth_SentCode.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            sent_code.serializeToStream(stream);
        }
    }

    public static class TL_updateGroupCallChainBlocks extends TLRPC.Update {
        public static final int constructor = 0xa477288f;

        public TLRPC.InputGroupCall call;
        public int sub_chain_id;
        public ArrayList<byte[]> blocks = new ArrayList<>();
        public int next_offset;

        public void readParams(InputSerializedData stream, boolean exception) {
            call = TLRPC.InputGroupCall.TLdeserialize(stream, stream.readInt32(exception), exception);
            sub_chain_id = stream.readInt32(exception);
            blocks = Vector.deserializeByteArray(stream, exception);
            next_offset = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            call.serializeToStream(stream);
            stream.writeInt32(sub_chain_id);
            Vector.serialize(stream, stream::writeByteArray, blocks);
            stream.writeInt32(next_offset);
        }
    }

    public static class TL_updateSavedDialogPinned extends TLRPC.Update {
        public static final int constructor = 0xaeaf9e74;

        public int flags;
        public boolean pinned;
        public TLRPC.DialogPeer peer;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            pinned = hasFlag(flags, FLAG_0);
            peer = TLRPC.DialogPeer.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, pinned);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
        }
    }

    public static class TL_updatePeerWallpaper extends TLRPC.Update {
        public static final int constructor = 0xae3f101d;

        public int flags;
        public boolean wallpaper_overridden;
        public TLRPC.Peer peer;
        public TLRPC.WallPaper wallpaper;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            wallpaper_overridden = hasFlag(flags, FLAG_1);
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_0)) {
                wallpaper = TLRPC.WallPaper.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_1, wallpaper_overridden);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            if (hasFlag(flags, FLAG_0)) {
                wallpaper.serializeToStream(stream);
            }
        }
    }

    public static class TL_updateMonoForumNoPaidException extends TLRPC.Update {
        public static final int constructor = 0x9f812b08;

        public int flags;
        public boolean exception;
        public long channel_id;
        public TLRPC.Peer saved_peer_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            this.exception = hasFlag(flags, FLAG_0);

            channel_id = stream.readInt64(exception);
            saved_peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);

            flags = setFlag(flags, FLAG_0, exception);
            stream.writeInt32(flags);

            stream.writeInt64(channel_id);
            saved_peer_id.serializeToStream(stream);
        }
    }

    public static class TL_updateJoinChatWebViewDecision extends TLRPC.Update {
        public static final int constructor = 0xBDAC7E70;

        public TLRPC.Peer peer;
        public long query_id;
        public TLRPC.JoinChatBotResult result;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            query_id = stream.readInt64(exception);
            result = TLRPC.JoinChatBotResult.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt64(query_id);
            result.serializeToStream(stream);
        }
    }

    public static class TL_updateStarGiftAuctionState extends TLRPC.Update {
        public static final int constructor = 0x48E246C2;

        public long gift_id;
        public TL_stars.StarGiftAuctionState state;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            gift_id = stream.readInt64(exception);
            state = TL_stars.StarGiftAuctionState.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(gift_id);
            state.serializeToStream(stream);
        }
    }

    public static class TL_updateStarGiftAuctionUserState extends TLRPC.Update {
        public static final int constructor = 0xDC58F31E;

        public long gift_id;
        public TL_stars.TL_StarGiftAuctionUserState user_state;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            gift_id = stream.readInt64(exception);
            user_state = TL_stars.TL_StarGiftAuctionUserState.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(gift_id);
            user_state.serializeToStream(stream);
        }
    }

    public static class TL_updateEmojiGameInfo extends TLRPC.Update {
        public static final int constructor = 0xfb9c547a;

        public TLRPC.EmojiGameInfo info;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            info = TLRPC.EmojiGameInfo.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            info.serializeToStream(stream);
        }
    }

    public static class TL_updateStarGiftCraftFail extends TLRPC.Update {
        public static final int constructor = 0xac072444;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            super.readParams(stream, exception);
        }
    }

    public static class TL_updateReadMonoForumInbox extends TLRPC.Update {
        public static final int constructor = 0x77b0e372;

        public long channel_id;
        public TLRPC.Peer saved_peer_id;
        public int read_max_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            channel_id = stream.readInt64(exception);
            saved_peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            read_max_id = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(channel_id);
            saved_peer_id.serializeToStream(stream);
            stream.writeInt32(read_max_id);
        }
    }

    public static class TL_updateReadMonoForumOutbox extends TLRPC.Update {
        public static final int constructor = 0xa4a79376;

        public long channel_id;
        public TLRPC.Peer saved_peer_id;
        public int read_max_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            channel_id = stream.readInt64(exception);
            saved_peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            read_max_id = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(channel_id);
            saved_peer_id.serializeToStream(stream);
            stream.writeInt32(read_max_id);
        }
    }

    public static class TL_updateMessagePollVote extends TLRPC.Update {
        public static final int constructor = 0x7699F014;

        public long poll_id;
        public TLRPC.Peer peer;
        public ArrayList<byte[]> options = new ArrayList<>();
        public ArrayList<Integer> positions = new ArrayList<>();
        public int qts;

        public void readParams(InputSerializedData stream, boolean exception) {
            poll_id = stream.readInt64(exception);
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            options = Vector.deserializeByteArray(stream, exception);
            positions = Vector.deserializeInt(stream, exception);
            qts = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(poll_id);
            peer.serializeToStream(stream);
            Vector.serializeByteArray(stream, options);
            Vector.serializeInt(stream, positions);
            stream.writeInt32(qts);
        }
    }

    public static class TL_updateMoveStickerSetToTop extends TLRPC.Update {
        public static final int constructor = 0x86fccf85;

        public int flags;
        public boolean masks;
        public boolean emojis;
        public long stickerset;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            masks = hasFlag(flags, FLAG_0);
            emojis = hasFlag(flags, FLAG_1);
            stickerset = stream.readInt64(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, masks);
            flags = setFlag(flags, FLAG_1, emojis);
            stream.writeInt32(flags);
            stream.writeInt64(stickerset);
        }
    }

    public static class TL_updateMessageExtendedMedia extends TLRPC.Update {
        public static final int constructor = 0xd5a41724;

        public TLRPC.Peer peer;
        public int msg_id;
        public ArrayList<TLRPC.MessageExtendedMedia> extended_media = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            msg_id = stream.readInt32(exception);
            extended_media = Vector.deserialize(stream, TLRPC.MessageExtendedMedia::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(msg_id);
            Vector.serialize(stream, extended_media);
        }
    }

    public static class TL_updatePeerHistoryTTL extends TLRPC.Update {
        public static final int constructor = 0xbb9bb9a5;

        public int flags;
        public TLRPC.Peer peer;
        public int ttl_period;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_0)) {
                ttl_period = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeInt32(ttl_period);
            }
        }
    }

    public static class TL_updateMessagePoll extends TLRPC.Update {
        public static final int constructor = 0xD64C522B;

        public int flags;
        public long poll_id;
        public TLRPC.Poll poll;
        public TLRPC.PollResults results;
        public int top_msg_id;
        public TLRPC.Peer peer;
        public int msg_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_1)) {
                peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
                msg_id = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_2)) {
                top_msg_id = stream.readInt32(exception);
            }
            poll_id = stream.readInt64(exception);
            if (hasFlag(flags, FLAG_0)) {
                poll = TLRPC.Poll.TLdeserialize(stream, exception);
            }
            results = TLRPC.PollResults.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, poll != null);
            flags = setFlag(flags, FLAG_1, peer != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_1)) {
                peer.serializeToStream(stream);
                stream.writeInt32(msg_id);
            }
            if (hasFlag(flags, FLAG_2)) {
                stream.writeInt32(top_msg_id);
            }
            stream.writeInt64(poll_id);
            if (hasFlag(flags, FLAG_0)) {
                poll.serializeToStream(stream);
            }
            results.serializeToStream(stream);
        }
    }

    public static class TL_updateChat extends TLRPC.Update {
        public static final int constructor = 0xf89a6a4e;

        public long chat_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            chat_id = stream.readInt64(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(chat_id);
        }
    }

    public static class TL_updateDeleteMessages extends TLRPC.Update {
        public static final int constructor = 0xa20db0e5;

        public ArrayList<Integer> messages = new ArrayList<>();
        public int pts;
        public int pts_count;

        public void readParams(InputSerializedData stream, boolean exception) {
            messages = Vector.deserializeInt(stream, exception);
            pts = stream.readInt32(exception);
            pts_count = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serializeInt(stream, messages);
            stream.writeInt32(pts);
            stream.writeInt32(pts_count);
        }
    }

    public static class TL_updatePinnedChannelMessages extends TLRPC.Update {
        public static final int constructor = 0x5bb98608;

        public int flags;
        public boolean pinned;
        public long channel_id;
        public ArrayList<Integer> messages = new ArrayList<>();
        public int pts;
        public int pts_count;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            pinned = hasFlag(flags, FLAG_0);
            channel_id = stream.readInt64(exception);
            messages = Vector.deserializeInt(stream, exception);
            pts = stream.readInt32(exception);
            pts_count = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, pinned);
            stream.writeInt32(flags);
            stream.writeInt64(channel_id);
            Vector.serializeInt(stream, messages);
            stream.writeInt32(pts);
            stream.writeInt32(pts_count);
        }
    }

    public static class TL_updateGroupCallParticipants extends TLRPC.Update {
        public static final int constructor = 0xf2ebdb4e;

        public TLRPC.InputGroupCall call;
        public ArrayList<TLRPC.GroupCallParticipant> participants = new ArrayList<>();
        public int version;

        public void readParams(InputSerializedData stream, boolean exception) {
            call = TLRPC.InputGroupCall.TLdeserialize(stream, stream.readInt32(exception), exception);
            participants = Vector.deserialize(stream, TLRPC.GroupCallParticipant::TLdeserialize, exception);
            version = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            call.serializeToStream(stream);
            Vector.serialize(stream, participants);
            stream.writeInt32(version);
        }
    }

    public static class TL_updateReadFeaturedStickers extends TLRPC.Update {
        public static final int constructor = 0x571d2742;

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_updateEncryptedChatTyping extends TLRPC.Update {
        public static final int constructor = 0x1710f156;

        public int chat_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            chat_id = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
        }
    }

    public static class TL_updateReadChannelDiscussionInbox extends TLRPC.Update {
        public static final int constructor = 0xd6b19546;

        public int flags;
        public long channel_id;
        public int top_msg_id;
        public int read_max_id;
        public long broadcast_id;
        public int broadcast_post;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            channel_id = stream.readInt64(exception);
            top_msg_id = stream.readInt32(exception);
            read_max_id = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_0)) {
                broadcast_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_0)) {
                broadcast_post = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt64(channel_id);
            stream.writeInt32(top_msg_id);
            stream.writeInt32(read_max_id);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeInt64(broadcast_id);
            }
            if (hasFlag(flags, FLAG_0)) {
                stream.writeInt32(broadcast_post);
            }
        }
    }

    public static class TL_updateReadHistoryOutbox extends TLRPC.Update {
        public static final int constructor = 0x2f2f21bf;

        public TLRPC.Peer peer;
        public int max_id;
        public int pts;
        public int pts_count;

        public void readParams(InputSerializedData stream, boolean exception) {
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            max_id = stream.readInt32(exception);
            pts = stream.readInt32(exception);
            pts_count = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(max_id);
            stream.writeInt32(pts);
            stream.writeInt32(pts_count);
        }
    }

    public static class TL_updateNewChannelMessage extends TLRPC.Update {
        public static final int constructor = 0x62ba04d9;

        public TLRPC.Message message;
        public int pts;
        public int pts_count;

        public void readParams(InputSerializedData stream, boolean exception) {
            message = TLRPC.Message.TLdeserialize(stream, stream.readInt32(exception), exception);
            pts = stream.readInt32(exception);
            pts_count = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            message.serializeToStream(stream);
            stream.writeInt32(pts);
            stream.writeInt32(pts_count);
        }
    }

    public static class TL_updateChatParticipantRank extends TLRPC.Update {
        public static final int constructor = 0xBD8367B9;

        public long chat_id;
        public long user_id;
        public String rank;
        public int version;

        public void readParams(InputSerializedData stream, boolean exception) {
            chat_id = stream.readInt64(exception);
            user_id = stream.readInt64(exception);
            rank = stream.readString(exception);
            version = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(chat_id);
            stream.writeInt64(user_id);
            stream.writeString(rank);
            stream.writeInt32(version);
        }
    }

    public static class TL_updateManagedBot extends TLRPC.Update {
        public static final int constructor = 0x4880ed9a;

        public long user_id;
        public long bot_id;
        public int qts;

        public void readParams(InputSerializedData stream, boolean exception) {
            user_id = stream.readInt64(exception);
            bot_id = stream.readInt64(exception);
            qts = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(user_id);
            stream.writeInt64(bot_id);
            stream.writeInt32(qts);
        }
    }

    public static class TL_updateAiComposeTones extends TLRPC.Update {
        public static final int constructor = 0x8c0f91fb;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_updateDialogPinned extends TLRPC.Update {
        public static final int constructor = 0x6e6fe51c;

        public int flags;
        public boolean pinned;
        public int folder_id;
        public TLRPC.DialogPeer peer;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            pinned = hasFlag(flags, FLAG_0);
            if (hasFlag(flags, FLAG_1)) {
                folder_id = stream.readInt32(exception);
            }
            peer = TLRPC.DialogPeer.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, pinned);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_1)) {
                stream.writeInt32(folder_id);
            }
            peer.serializeToStream(stream);
        }
    }

    public static class TL_updatePeerSettings extends TLRPC.Update {
        public static final int constructor = 0x6a7e7366;

        public TLRPC.Peer peer;
        public TLRPC.PeerSettings settings;

        public void readParams(InputSerializedData stream, boolean exception) {
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            settings = TLRPC.PeerSettings.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            settings.serializeToStream(stream);
        }
    }

    public static class TL_updateUserPhone extends TLRPC.Update {
        public static final int constructor = 0x5492a13;

        public long user_id;
        public String phone;

        public void readParams(InputSerializedData stream, boolean exception) {
            user_id = stream.readInt64(exception);
            phone = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(user_id);
            stream.writeString(phone);
        }
    }

    public static class TL_updateMessageID extends TLRPC.Update {
        public static final int constructor = 0x4e90bfd6;

        public int id;
        public long random_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            random_id = stream.readInt64(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeInt64(random_id);
        }
    }

    public static class TL_updateReadChannelOutbox extends TLRPC.Update {
        public static final int constructor = 0xb75f99a9;

        public long channel_id;
        public int max_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            channel_id = stream.readInt64(exception);
            max_id = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(channel_id);
            stream.writeInt32(max_id);
        }
    }

    public static class TL_updateChannelUserTyping extends TLRPC.Update {
        public static final int constructor = 0x8c88c923;

        public int flags;
        public long channel_id;
        public int top_msg_id;
        public TLRPC.Peer from_id;
        public TLRPC.SendMessageAction action;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            channel_id = stream.readInt64(exception);
            if (hasFlag(flags, FLAG_0)) {
                top_msg_id = stream.readInt32(exception);
            }
            from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            action = TLRPC.SendMessageAction.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt64(channel_id);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeInt32(top_msg_id);
            }
            from_id.serializeToStream(stream);
            action.serializeToStream(stream);
        }
    }

    public static class TL_updateStoryID extends TLRPC.Update {
        public static final int constructor = 0x1bf335b9;

        public int id;
        public long random_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            random_id = stream.readInt64(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeInt64(random_id);
        }
    }

    public static class TL_updateStickerSets extends TLRPC.Update {
        public static final int constructor = 0x31c24808;

        public int flags;
        public boolean masks;
        public boolean emojis;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            masks = hasFlag(flags, FLAG_0);
            emojis = hasFlag(flags, FLAG_1);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, masks);
            flags = setFlag(flags, FLAG_1, emojis);
            stream.writeInt32(flags);
        }
    }

    public static class TL_updateChannelViewForumAsMessages extends TLRPC.Update {
        public static final int constructor = 0x7b68920;

        public long channel_id;
        public boolean enabled;

        public void readParams(InputSerializedData stream, boolean exception) {
            channel_id = stream.readInt64(exception);
            enabled = stream.readBool(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(channel_id);
            stream.writeBool(enabled);
        }
    }

    public static class TL_updatePinnedSavedDialogs extends TLRPC.Update {
        public static final int constructor = 0x686c85a6;

        public int flags;
        public ArrayList<TLRPC.DialogPeer> order = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_0)) {
                order = Vector.deserialize(stream, TLRPC.DialogPeer::TLdeserialize, exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_0)) {
                Vector.serialize(stream, order);
            }
        }
    }

    public static class TL_updateQuickReplies extends TLRPC.Update {
        public static final int constructor = 0xf9470ab2;

        public ArrayList<TLRPC.TL_quickReply> quick_replies = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            quick_replies = Vector.deserialize(stream, TLRPC.TL_quickReply::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, quick_replies);
        }
    }

    public static class TL_updateNewQuickReply extends TLRPC.Update {
        public static final int constructor = 0xf53da717;

        public TLRPC.TL_quickReply quick_reply;

        public void readParams(InputSerializedData stream, boolean exception) {
            quick_reply = TLRPC.TL_quickReply.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            quick_reply.serializeToStream(stream);
        }
    }

    public static class TL_updateDeleteQuickReply extends TLRPC.Update {
        public static final int constructor = 0x53e6f1ec;

        public int shortcut_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            shortcut_id = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(shortcut_id);
        }
    }

    public static class TL_updateQuickReplyMessage extends TLRPC.Update {
        public static final int constructor = 0x3e050d0f;

        public TLRPC.Message message;

        public void readParams(InputSerializedData stream, boolean exception) {
            message = TLRPC.Message.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            message.serializeToStream(stream);
        }
    }

    public static class TL_updateGroupCallMessage extends TLRPC.Update {
        public static final int constructor = 0xd8326f0d;

        public TLRPC.InputGroupCall call;
        public TLRPC.GroupCallMessage message;

        public void readParams(InputSerializedData stream, boolean exception) {
            call = TLRPC.InputGroupCall.TLdeserialize(stream, stream.readInt32(exception), exception);
            message = TLRPC.GroupCallMessage.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            call.serializeToStream(stream);
            message.serializeToStream(stream);
        }
    }

    public static class TL_updateDeleteGroupCallMessages extends TLRPC.Update {
        public static final int constructor = 0x3e85e92c;

        public TLRPC.InputGroupCall call;
        public ArrayList<Integer> messages = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            call = TLRPC.InputGroupCall.TLdeserialize(stream, stream.readInt32(exception), exception);
            messages = Vector.deserializeInt(stream, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            call.serializeToStream(stream);
            Vector.serializeInt(stream, messages);
        }
    }

    public static class TL_updateGroupCallEncryptedMessage extends TLRPC.Update {
        public static final int constructor = 0xc957a766;

        public TLRPC.InputGroupCall call;
        public TLRPC.Peer from_id;
        public byte[] encrypted_message;

        public void readParams(InputSerializedData stream, boolean exception) {
            call = TLRPC.InputGroupCall.TLdeserialize(stream, stream.readInt32(exception), exception);
            from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            encrypted_message = stream.readByteArray(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            call.serializeToStream(stream);
            from_id.serializeToStream(stream);
            stream.writeByteArray(encrypted_message);
        }
    }

    public static class TL_updateDeleteQuickReplyMessages extends TLRPC.Update {
        public static final int constructor = 0x566fe7cd;

        public int shortcut_id;
        public ArrayList<Integer> messages = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            shortcut_id = stream.readInt32(exception);
            messages = Vector.deserializeInt(stream, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(shortcut_id);
            Vector.serializeInt(stream, messages);
        }
    }

    public static class TL_updateNewStoryReaction extends TLRPC.Update {
        public static final int constructor = 0x1824e40b;

        public int story_id;
        public TLRPC.Peer peer;
        public TLRPC.Reaction reaction;

        public void readParams(InputSerializedData stream, boolean exception) {
            story_id = stream.readInt32(exception);
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            reaction = TLRPC.Reaction.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(story_id);
            peer.serializeToStream(stream);
            reaction.serializeToStream(stream);
        }
    }

    public static class TL_updateStarsBalance extends TLRPC.Update {
        public static final int constructor = 0x4e80a379;

        public TL_stars.StarsAmount balance;

        public void readParams(InputSerializedData stream, boolean exception) {
            balance = TL_stars.StarsAmount.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            balance.serializeToStream(stream);
        }
    }

    public static class TL_updateWebViewResultSent extends TLRPC.Update {
        public static final int constructor = 0x1592b79d;

        public long query_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            query_id = stream.readInt64(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(query_id);
        }
    }

    public static class TL_updateEncryptedMessagesRead extends TLRPC.Update {
        public static final int constructor = 0x38fe25b7;

        public int chat_id;
        public int max_date;
        public int date;

        public void readParams(InputSerializedData stream, boolean exception) {
            chat_id = stream.readInt32(exception);
            max_date = stream.readInt32(exception);
            date = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            stream.writeInt32(max_date);
            stream.writeInt32(date);
        }
    }

    public static class TL_updateStickerSetsOrder extends TLRPC.Update {
        public static final int constructor = 0xbb2d201;

        public int flags;
        public boolean masks;
        public boolean emojis;
        public ArrayList<Long> order = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            masks = hasFlag(flags, FLAG_0);
            emojis = hasFlag(flags, FLAG_1);
            order = Vector.deserializeLong(stream, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, masks);
            flags = setFlag(flags, FLAG_1, emojis);
            stream.writeInt32(flags);
            Vector.serializeLong(stream, order);
        }
    }

    public static class TL_updateReadFeaturedEmojiStickers extends TLRPC.Update {
        public static final int constructor = 0xfb4c496c;

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_updateReadChannelInbox extends TLRPC.Update {
        public static final int constructor = 0x922e6e10;

        public int flags;
        public int folder_id;
        public long channel_id;
        public int max_id;
        public int still_unread_count;
        public int pts;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_0)) {
                folder_id = stream.readInt32(exception);
            }
            channel_id = stream.readInt64(exception);
            max_id = stream.readInt32(exception);
            still_unread_count = stream.readInt32(exception);
            pts = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeInt32(folder_id);
            }
            stream.writeInt64(channel_id);
            stream.writeInt32(max_id);
            stream.writeInt32(still_unread_count);
            stream.writeInt32(pts);
        }
    }

    public static class TL_updateReadMessagesContents extends TLRPC.Update {
        public static final int constructor = 0xf8227181;

        public int flags;
        public ArrayList<Integer> messages = new ArrayList<>();
        public int pts;
        public int pts_count;
        public int date;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            messages = Vector.deserializeInt(stream, exception);
            pts = stream.readInt32(exception);
            pts_count = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_0)) {
                date = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            Vector.serializeInt(stream, messages);
            stream.writeInt32(pts);
            stream.writeInt32(pts_count);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeInt32(date);
            }
        }
    }

    public static class TL_updateChatParticipants extends TLRPC.Update {
        public static final int constructor = 0x7761198;

        public TLRPC.ChatParticipants participants;

        public void readParams(InputSerializedData stream, boolean exception) {
            participants = TLRPC.ChatParticipants.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            participants.serializeToStream(stream);
        }
    }

    public static class TL_updateChatDefaultBannedRights extends TLRPC.Update {
        public static final int constructor = 0x54c01850;

        public TLRPC.Peer peer;
        public TLRPC.TL_chatBannedRights default_banned_rights;
        public int version;

        public void readParams(InputSerializedData stream, boolean exception) {
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            default_banned_rights = TLRPC.TL_chatBannedRights.TLdeserialize(stream, stream.readInt32(exception), exception);
            version = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            default_banned_rights.serializeToStream(stream);
            stream.writeInt32(version);
        }
    }

    public static class TL_updatePinnedForumTopics extends TLRPC.Update {
        public static final int constructor = 0xdef143d0;

        public int flags;
        public TLRPC.Peer peer;
        public ArrayList<Integer> order = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_0)) {
                order = Vector.deserializeInt(stream, exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            if (hasFlag(flags, FLAG_0)) {
                Vector.serializeInt(stream, order);
            }
        }
    }

    public static class TL_updatePinnedForumTopic extends TLRPC.Update {
        public static final int constructor = 0x683b2c52;

        public boolean pinned;
        public TLRPC.Peer peer;
        public int topic_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            final int flags = stream.readInt32(exception);
            pinned = hasFlag(flags, FLAG_0);
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            topic_id = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, pinned);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            stream.writeInt32(topic_id);
        }
    }

    public static class TL_updateAttachMenuBots extends TLRPC.Update {
        public static final int constructor = 0x17b7a20b;

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_updateChannel extends TLRPC.Update {
        public static final int constructor = 0x635b4c09;

        public long channel_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            channel_id = stream.readInt64(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(channel_id);
        }
    }

    public static class TL_updateChannelWebPage extends TLRPC.Update {
        public static final int constructor = 0x2f2ba99f;

        public long channel_id;
        public TLRPC.WebPage webpage;
        public int pts;
        public int pts_count;

        public void readParams(InputSerializedData stream, boolean exception) {
            channel_id = stream.readInt64(exception);
            webpage = TLRPC.WebPage.TLdeserialize(stream, stream.readInt32(exception), exception);
            pts = stream.readInt32(exception);
            pts_count = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(channel_id);
            webpage.serializeToStream(stream);
            stream.writeInt32(pts);
            stream.writeInt32(pts_count);
        }
    }

    public static class TL_updateDeleteScheduledMessages extends TLRPC.Update {
        public static final int constructor = 0xf2a71983;

        public int flags;
        public TLRPC.Peer peer;
        public ArrayList<Integer> messages = new ArrayList<>();
        public ArrayList<Integer> sent_messages = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            messages = Vector.deserializeInt(stream, exception);
            if (hasFlag(flags, FLAG_0)) {
                sent_messages = Vector.deserializeInt(stream, exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            Vector.serializeInt(stream, messages);
            if (hasFlag(flags, FLAG_0)) {
                Vector.serializeInt(stream, sent_messages);
            }
        }
    }

    public static class TL_updateSentStoryReaction extends TLRPC.Update {
        public static final int constructor = 0x7d627683;

        public TLRPC.Peer peer;
        public int story_id;
        public TLRPC.Reaction reaction;

        public void readParams(InputSerializedData stream, boolean exception) {
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            story_id = stream.readInt32(exception);
            reaction = TLRPC.Reaction.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(story_id);
            reaction.serializeToStream(stream);
        }
    }

    public static class TL_updateChannelMessageForwards extends TLRPC.Update {
        public static final int constructor = 0xd29a27f4;

        public long channel_id;
        public int id;
        public int forwards;

        public void readParams(InputSerializedData stream, boolean exception) {
            channel_id = stream.readInt64(exception);
            id = stream.readInt32(exception);
            forwards = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(channel_id);
            stream.writeInt32(id);
            stream.writeInt32(forwards);
        }
    }

    public static class TL_updateDeleteChannelMessages extends TLRPC.Update {
        public static final int constructor = 0xc32d5b12;

        public long channel_id;
        public ArrayList<Integer> messages = new ArrayList<>();
        public int pts;
        public int pts_count;

        public void readParams(InputSerializedData stream, boolean exception) {
            channel_id = stream.readInt64(exception);
            messages = Vector.deserializeInt(stream, exception);
            pts = stream.readInt32(exception);
            pts_count = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(channel_id);
            Vector.serializeInt(stream, messages);
            stream.writeInt32(pts);
            stream.writeInt32(pts_count);
        }
    }

    public static class TL_updateUserPhoto extends TLRPC.Update {
        public static final int constructor = 0xf227868c;

        public long user_id;
        public int date;
        public TLRPC.UserProfilePhoto photo;
        public boolean previous;

        public void readParams(InputSerializedData stream, boolean exception) {
            user_id = stream.readInt64(exception);
            date = stream.readInt32(exception);
            photo = TLRPC.UserProfilePhoto.TLdeserialize(stream, stream.readInt32(exception), exception);
            previous = stream.readBool(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(user_id);
            stream.writeInt32(date);
            photo.serializeToStream(stream);
            stream.writeBool(previous);
        }
    }

    public static class TL_updateUser extends TLRPC.Update {
        public static final int constructor = 0x20529438;

        public long user_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            user_id = stream.readInt64(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(user_id);
        }
    }

    public static class TL_updateDialogFilters extends TLRPC.Update {
        public static final int constructor = 0x3504914f;

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_updateRecentEmojiStatuses extends TLRPC.Update {
        public static final int constructor = 0x30f443db;

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_updatePendingJoinRequests extends TLRPC.Update {
        public static final int constructor = 0x7063c3db;

        public TLRPC.Peer peer;
        public int requests_pending;
        public ArrayList<Long> recent_requesters = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            requests_pending = stream.readInt32(exception);
            recent_requesters = Vector.deserializeLong(stream, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(requests_pending);
            Vector.serializeLong(stream, recent_requesters);
        }
    }

    public static class TL_updateDcOptions extends TLRPC.Update {
        public static final int constructor = 0x8e5e9873;

        public ArrayList<TLRPC.TL_dcOption> dc_options = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            dc_options = Vector.deserialize(stream, TLRPC.TL_dcOption::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, dc_options);
        }
    }

    public static class TL_updateEditChannelMessage extends TLRPC.Update {
        public static final int constructor = 0x1b3f4df7;

        public TLRPC.Message message;
        public int pts;
        public int pts_count;

        public void readParams(InputSerializedData stream, boolean exception) {
            message = TLRPC.Message.TLdeserialize(stream, stream.readInt32(exception), exception);
            pts = stream.readInt32(exception);
            pts_count = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            message.serializeToStream(stream);
            stream.writeInt32(pts);
            stream.writeInt32(pts_count);
        }
    }

    public static class TL_updateNewStickerSet extends TLRPC.Update {
        public static final int constructor = 0x688a30aa;

        public TLRPC.TL_messages_stickerSet stickerset;

        public void readParams(InputSerializedData stream, boolean exception) {
            stickerset = TLRPC.messages_StickerSet.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stickerset.serializeToStream(stream);
        }
    }

    public static class TL_updateTheme extends TLRPC.Update {
        public static final int constructor = 0x8216fba3;

        public TLRPC.Theme theme;

        public void readParams(InputSerializedData stream, boolean exception) {
            theme = TLRPC.Theme.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            theme.serializeToStream(stream);
        }
    }

    public static class TL_updateLangPackTooLong extends TLRPC.Update {
        public static final int constructor = 0x46560264;

        public String lang_code;

        public void readParams(InputSerializedData stream, boolean exception) {
            lang_code = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(lang_code);
        }
    }

    public static class TL_updateDialogUnreadMark extends TLRPC.Update {
        public static final int constructor = 0xb658f23e;

        public int flags;
        public boolean unread;
        public TLRPC.DialogPeer peer;
        public TLRPC.Peer saved_peer_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            unread = hasFlag(flags, FLAG_0);
            peer = TLRPC.DialogPeer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_1)) {
                saved_peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, unread);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            if (hasFlag(flags, FLAG_1)) {
                saved_peer_id.serializeToStream(stream);
            }
        }
    }

    public static class TL_updateDraftMessage extends TLRPC.Update {
        public static final int constructor = 0xedfc111e;

        public int flags;
        public TLRPC.Peer peer;
        public TLRPC.Peer saved_peer_id;
        public int top_msg_id;
        public TLRPC.DraftMessage draft;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_0)) {
                top_msg_id = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_1)) {
                saved_peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            draft = TLRPC.DraftMessage.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeInt32(top_msg_id);
            }
            if (hasFlag(flags, FLAG_1)) {
                saved_peer_id.serializeToStream(stream);
            }
            draft.serializeToStream(stream);
        }
    }

    public static class TL_updateNewAuthorization extends TLRPC.Update {
        public static final int constructor = 0x8951abef;

        public int flags;
        public boolean unconfirmed;
        public long hash;
        public int date;
        public String device;
        public String location;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            unconfirmed = hasFlag(flags, FLAG_0);
            hash = stream.readInt64(exception);
            if (hasFlag(flags, FLAG_0)) {
                date = stream.readInt32(exception);
                device = stream.readString(exception);
                location = stream.readString(exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt64(hash);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeInt32(date);
                stream.writeString(device);
                stream.writeString(location);
            }
        }
    }

    public static class TL_updateNewBotConnection extends TLRPC.Update {
        public static final int constructor = 0xb22083a6;

        public int flags;
        public boolean confirmed;
        public long bot_id;
        public int date;
        public String device;
        public String location;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            confirmed = hasFlag(flags, FLAG_0);
            bot_id = stream.readInt64(exception);
            if (hasFlag(flags, FLAG_1)) {
                date = stream.readInt32(exception);
                device = stream.readString(exception);
                location = stream.readString(exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, confirmed);
            stream.writeInt32(flags);
            stream.writeInt64(bot_id);
            if (hasFlag(flags, FLAG_1)) {
                stream.writeInt32(date);
                stream.writeString(device);
                stream.writeString(location);
            }
        }
    }

    public static class TL_updateUserName extends TLRPC.Update {
        public static final int constructor = 0xa7848924;

        public long user_id;
        public String first_name;
        public String last_name;
        public ArrayList<TLRPC.TL_username> usernames = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            user_id = stream.readInt64(exception);
            first_name = stream.readString(exception);
            last_name = stream.readString(exception);
            usernames = Vector.deserialize(stream, TLRPC.TL_username::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(user_id);
            stream.writeString(first_name);
            stream.writeString(last_name);
            Vector.serialize(stream, usernames);
        }
    }

    public static class TL_updateMessageReactions extends TLRPC.Update {
        public static final int constructor = 0x1e297bfa;

        public int flags;
        public TLRPC.Peer peer;
        public int msg_id;
        public int top_msg_id;
        public TLRPC.Peer saved_peer_id;
        public TLRPC.TL_messageReactions reactions;
        public boolean updateUnreadState = true; //custom

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            msg_id = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_0)) {
                top_msg_id = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_1)) {
                saved_peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            reactions = TLRPC.MessageReactions.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            stream.writeInt32(msg_id);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeInt32(top_msg_id);
            }
            if (hasFlag(flags, FLAG_1)) {
                saved_peer_id.serializeToStream(stream);
            }
            reactions.serializeToStream(stream);
        }
    }

    public static class TL_updatePhoneCall extends TLRPC.Update {
        public static final int constructor = 0xab0f6b1e;

        public TL_phone.PhoneCall phone_call;

        public void readParams(InputSerializedData stream, boolean exception) {
            phone_call = TL_phone.PhoneCall.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            phone_call.serializeToStream(stream);
        }
    }

    public static class TL_updateDialogFilter extends TLRPC.Update {
        public static final int constructor = 0x26ffde7d;

        public int flags;
        public int id;
        public TLRPC.DialogFilter filter;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            id = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_0)) {
                filter = TLRPC.DialogFilter.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            if (hasFlag(flags, FLAG_0)) {
                filter.serializeToStream(stream);
            }
        }
    }

    public static class TL_updatePeerBlocked extends TLRPC.Update {
        public static final int constructor = 0xebe07752;

        public int flags;
        public boolean blocked;
        public boolean blocked_my_stories_from;
        public TLRPC.Peer peer_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            blocked = hasFlag(flags, FLAG_0);
            blocked_my_stories_from = hasFlag(flags, FLAG_1);
            peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, blocked);
            flags = setFlag(flags, FLAG_1, blocked_my_stories_from);
            stream.writeInt32(flags);
            peer_id.serializeToStream(stream);
        }
    }

    public static class TL_updatePinnedMessages extends TLRPC.Update {
        public static final int constructor = 0xed85eab5;

        public int flags;
        public boolean pinned;
        public TLRPC.Peer peer;
        public ArrayList<Integer> messages = new ArrayList<>();
        public int pts;
        public int pts_count;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            pinned = hasFlag(flags, FLAG_0);
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            messages = Vector.deserializeInt(stream, exception);
            pts = stream.readInt32(exception);
            pts_count = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, pinned);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            Vector.serializeInt(stream, messages);
            stream.writeInt32(pts);
            stream.writeInt32(pts_count);
        }
    }

    public static class TL_updatePhoneCallSignalingData extends TLRPC.Update {
        public static final int constructor = 0x2661bf09;

        public long phone_call_id;
        public byte[] data;

        public void readParams(InputSerializedData stream, boolean exception) {
            phone_call_id = stream.readInt64(exception);
            data = stream.readByteArray(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(phone_call_id);
            stream.writeByteArray(data);
        }
    }

    public static class TL_updateTranscribeAudio extends TLRPC.Update {
        public static final int constructor = 0x88617090;

        public int flags;
        public boolean isFinal;
        public long transcription_id;
        public String text;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            isFinal = hasFlag(flags, FLAG_0);
            transcription_id = stream.readInt64(exception);
            text = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, isFinal);
            stream.writeInt32(flags);
            stream.writeInt64(transcription_id);
            stream.writeString(text);
        }
    }

    public static class TL_updatePinnedDialogs extends TLRPC.Update {
        public static final int constructor = 0xfa0f3ca2;

        public int flags;
        public int folder_id;
        public ArrayList<TLRPC.DialogPeer> order = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_1)) {
                folder_id = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_0)) {
                order = Vector.deserialize(stream, TLRPC.DialogPeer::TLdeserialize, exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_1)) {
                stream.writeInt32(folder_id);
            }
            if (hasFlag(flags, FLAG_0)) {
                Vector.serialize(stream, order);
            }
        }
    }

    public static class TL_updatePeerLocated extends TLRPC.Update {
        public static final int constructor = 0xb4afcfb0;

        public ArrayList<TLRPC.PeerLocated> peers = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            peers = Vector.deserialize(stream, TLRPC.PeerLocated::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, peers);
        }
    }

    public static class TL_updateRecentStickers extends TLRPC.Update {
        public static final int constructor = 0x9a422c20;

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_updateReadHistoryInbox extends TLRPC.Update {
        public static final int constructor = 0x9E84BC99;

        public int flags;
        public int folder_id;
        public TLRPC.Peer peer;
        public int top_msg_id;
        public int max_id;
        public int still_unread_count;
        public int pts;
        public int pts_count;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_0)) {
                folder_id = stream.readInt32(exception);
            }
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_1)) {
                top_msg_id = stream.readInt32(exception);
            }
            max_id = stream.readInt32(exception);
            still_unread_count = stream.readInt32(exception);
            pts = stream.readInt32(exception);
            pts_count = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeInt32(folder_id);
            }
            peer.serializeToStream(stream);
            if (hasFlag(flags, FLAG_1)) {
                stream.writeInt32(top_msg_id);
            }
            stream.writeInt32(max_id);
            stream.writeInt32(still_unread_count);
            stream.writeInt32(pts);
            stream.writeInt32(pts_count);
        }
    }

    public static class TL_updateDialogFilterOrder extends TLRPC.Update {
        public static final int constructor = 0xa5d72105;

        public ArrayList<Integer> order = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            order = Vector.deserializeInt(stream, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serializeInt(stream, order);
        }
    }

    public static class TL_updateSavedGifs extends TLRPC.Update {
        public static final int constructor = 0x9375341e;

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_updateContactsReset extends TLRPC.Update {
        public static final int constructor = 0x7084a7be;

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_updateWebPage extends TLRPC.Update {
        public static final int constructor = 0x7f891213;

        public TLRPC.WebPage webpage;
        public int pts;
        public int pts_count;

        public void readParams(InputSerializedData stream, boolean exception) {
            webpage = TLRPC.WebPage.TLdeserialize(stream, stream.readInt32(exception), exception);
            pts = stream.readInt32(exception);
            pts_count = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            webpage.serializeToStream(stream);
            stream.writeInt32(pts);
            stream.writeInt32(pts_count);
        }
    }

    public static class TL_updateFavedStickers extends TLRPC.Update {
        public static final int constructor = 0xe511996d;

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_updateChatParticipantAdd extends TLRPC.Update {
        public static final int constructor = 0x3dda5451;

        public long chat_id;
        public long user_id;
        public long inviter_id;
        public int date;
        public int version;

        public void readParams(InputSerializedData stream, boolean exception) {
            chat_id = stream.readInt64(exception);
            user_id = stream.readInt64(exception);
            inviter_id = stream.readInt64(exception);
            date = stream.readInt32(exception);
            version = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(chat_id);
            stream.writeInt64(user_id);
            stream.writeInt64(inviter_id);
            stream.writeInt32(date);
            stream.writeInt32(version);
        }
    }

    public static class TL_updateChatUserTyping extends TLRPC.Update {
        public static final int constructor = 0x83487af0;

        public long chat_id;
        public TLRPC.Peer from_id;
        public TLRPC.SendMessageAction action;

        public void readParams(InputSerializedData stream, boolean exception) {
            chat_id = stream.readInt64(exception);
            from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            action = TLRPC.SendMessageAction.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(chat_id);
            from_id.serializeToStream(stream);
            action.serializeToStream(stream);
        }
    }

    public static class TL_updateLoginToken extends TLRPC.Update {
        public static final int constructor = 0x564fe691;

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_updateEncryption extends TLRPC.Update {
        public static final int constructor = 0xb4a2e88d;

        public TLRPC.EncryptedChat chat;
        public int date;

        public void readParams(InputSerializedData stream, boolean exception) {
            chat = TLRPC.EncryptedChat.TLdeserialize(stream, stream.readInt32(exception), exception);
            date = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            chat.serializeToStream(stream);
            stream.writeInt32(date);
        }
    }

    public static class TL_updateGroupCall extends TLRPC.Update {
        public static final int constructor = 0x9d2216e0;

        public int flags;
        public boolean live_story;
        public TLRPC.Peer peer;
        public TLRPC.GroupCall call;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            live_story = hasFlag(flags, FLAG_2);
            if (hasFlag(flags, FLAG_1)) {
                peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            call = TLRPC.GroupCall.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_2, live_story);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_1)) {
                peer.serializeToStream(stream);
            }
            call.serializeToStream(stream);
        }
    }

    public static class TL_updateGroupCall_layer216 extends TL_updateGroupCall {
        public static final int constructor = 0x97d64341;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            live_story = hasFlag(flags, FLAG_2);
            if (hasFlag(flags, FLAG_0)) {
                long chat_id = stream.readInt64(exception);
                peer = new TLRPC.TL_peerChannel();
                peer.channel_id = -chat_id;
            }
            call = TLRPC.GroupCall.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_2, live_story);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeInt64(-DialogObject.getPeerDialogId(peer));
            }
            call.serializeToStream(stream);
        }
    }

    public static class TL_updateChannelTooLong extends TLRPC.Update {
        public static final int constructor = 0x108d941f;

        public int flags;
        public long channel_id;
        public int pts;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            channel_id = stream.readInt64(exception);
            if (hasFlag(flags, FLAG_0)) {
                pts = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt64(channel_id);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeInt32(pts);
            }
        }
    }

    public static class TL_updateUserTyping extends TLRPC.Update {
        public static final int constructor = 0x2A17BF5C;

        public int flags;
        public long user_id;
        public int top_msg_id;
        public TLRPC.SendMessageAction action;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            user_id = stream.readInt64(exception);
            if (hasFlag(flags, FLAG_0)) {
                top_msg_id = stream.readInt32(exception);
            }
            action = TLRPC.SendMessageAction.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt64(user_id);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeInt32(top_msg_id);
            }
            action.serializeToStream(stream);
        }
    }

    public static class TL_updateServiceNotification extends TLRPC.Update {
        public static final int constructor = 0xebe46819;

        public int flags;
        public boolean popup;
        public int inbox_date;
        public String type;
        public String message;
        public TLRPC.MessageMedia media;
        public ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        public boolean invert_media;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            popup = hasFlag(flags, FLAG_0);
            if (hasFlag(flags, FLAG_1)) {
                inbox_date = stream.readInt32(exception);
            }
            invert_media = hasFlag(flags, FLAG_2);
            type = stream.readString(exception);
            message = stream.readString(exception);
            media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
            entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, popup);
            flags = setFlag(flags, FLAG_2, invert_media);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_1)) {
                stream.writeInt32(inbox_date);
            }
            stream.writeString(type);
            stream.writeString(message);
            media.serializeToStream(stream);
            Vector.serialize(stream, entities);
        }
    }

    public static class TL_updateSavedRingtones extends TLRPC.Update {
        public static final int constructor = 0x74d8be99;

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_updateTranscribedAudio extends TLRPC.Update {
        public static final int constructor = 0x84cd5a;

        public int flags;
        public boolean pending;
        public TLRPC.Peer peer;
        public int msg_id;
        public long transcription_id;
        public String text;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            pending = hasFlag(flags, FLAG_0);
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            msg_id = stream.readInt32(exception);
            transcription_id = stream.readInt64(exception);
            text = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, pending);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            stream.writeInt32(msg_id);
            stream.writeInt64(transcription_id);
            stream.writeString(text);
        }
    }

    public static class TL_updateUserEmojiStatus extends TLRPC.Update {
        public static final int constructor = 0x28373599;

        public long user_id;
        public TLRPC.EmojiStatus emoji_status;

        public void readParams(InputSerializedData stream, boolean exception) {
            user_id = stream.readInt64(exception);
            emoji_status = TLRPC.EmojiStatus.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(user_id);
            emoji_status.serializeToStream(stream);
        }
    }

    public static class TL_updateLangPack extends TLRPC.Update {
        public static final int constructor = 0x56022f4d;

        public TLRPC.TL_langPackDifference difference;

        public void readParams(InputSerializedData stream, boolean exception) {
            difference = TLRPC.TL_langPackDifference.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            difference.serializeToStream(stream);
        }
    }

    public static class TL_updateChannelAvailableMessages extends TLRPC.Update {
        public static final int constructor = 0xb23fc698;

        public long channel_id;
        public int available_min_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            channel_id = stream.readInt64(exception);
            available_min_id = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(channel_id);
            stream.writeInt32(available_min_id);
        }
    }

    public static class TL_updateChatParticipantAdmin extends TLRPC.Update {
        public static final int constructor = 0xd7ca61a2;

        public long chat_id;
        public long user_id;
        public boolean is_admin;
        public int version;

        public void readParams(InputSerializedData stream, boolean exception) {
            chat_id = stream.readInt64(exception);
            user_id = stream.readInt64(exception);
            is_admin = stream.readBool(exception);
            version = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(chat_id);
            stream.writeInt64(user_id);
            stream.writeBool(is_admin);
            stream.writeInt32(version);
        }
    }

    public static class TL_updateChannelReadMessagesContents extends TLRPC.Update {
        public static final int constructor = 0x25f324f7;

        public int flags;
        public long channel_id;
        public int top_msg_id;
        public TLRPC.Peer saved_peer_id;
        public ArrayList<Integer> messages = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            channel_id = stream.readInt64(exception);
            if (hasFlag(flags, FLAG_0)) {
                top_msg_id = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_1)) {
                saved_peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            messages = Vector.deserializeInt(stream, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt64(channel_id);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeInt32(top_msg_id);
            }
            if (hasFlag(flags, FLAG_1)) {
                saved_peer_id.serializeToStream(stream);
            }
            Vector.serializeInt(stream, messages);
        }
    }

    public static class TL_updatePrivacy extends TLRPC.Update {
        public static final int constructor = 0xee3b272a;

        public TLRPC.PrivacyKey key;
        public ArrayList<TLRPC.PrivacyRule> rules = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            key = TLRPC.PrivacyKey.TLdeserialize(stream, stream.readInt32(exception), exception);
            rules = Vector.deserialize(stream, TLRPC.PrivacyRule::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            key.serializeToStream(stream);
            Vector.serialize(stream, rules);
        }
    }

    public static class TL_updateConfig extends TLRPC.Update {
        public static final int constructor = 0xa229dd06;

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_updateFolderPeers extends TLRPC.Update {
        public static final int constructor = 0x19360dc0;

        public ArrayList<TLRPC.TL_folderPeer> folder_peers = new ArrayList<>();
        public int pts;
        public int pts_count;

        public void readParams(InputSerializedData stream, boolean exception) {
            folder_peers = Vector.deserialize(stream, TLRPC.TL_folderPeer::TLdeserialize, exception);
            pts = stream.readInt32(exception);
            pts_count = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, folder_peers);
            stream.writeInt32(pts);
            stream.writeInt32(pts_count);
        }
    }

    public static class TL_updateNewMessage extends TLRPC.Update {
        public static final int constructor = 0x1f2b0afd;

        public TLRPC.Message message;
        public int pts;
        public int pts_count;

        public void readParams(InputSerializedData stream, boolean exception) {
            message = TLRPC.Message.TLdeserialize(stream, stream.readInt32(exception), exception);
            pts = stream.readInt32(exception);
            pts_count = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            message.serializeToStream(stream);
            stream.writeInt32(pts);
            stream.writeInt32(pts_count);
        }
    }

    public static class TL_updateNewScheduledMessage extends TLRPC.Update {
        public static final int constructor = 0x39a51dfb;

        public TLRPC.Message message;

        public void readParams(InputSerializedData stream, boolean exception) {
            message = TLRPC.Message.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            message.serializeToStream(stream);
        }
    }

    public static class TL_updateNewEncryptedMessage extends TLRPC.Update {
        public static final int constructor = 0x12bcbd9a;

        public TLRPC.EncryptedMessage message;
        public int qts;

        public void readParams(InputSerializedData stream, boolean exception) {
            message = TLRPC.EncryptedMessage.TLdeserialize(stream, stream.readInt32(exception), exception);
            qts = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            message.serializeToStream(stream);
            stream.writeInt32(qts);
        }
    }

    public static class TL_updateUserStatus extends TLRPC.Update {
        public static final int constructor = 0xe5bdf8de;

        public long user_id;
        public TLRPC.UserStatus status;

        public void readParams(InputSerializedData stream, boolean exception) {
            user_id = stream.readInt64(exception);
            status = TLRPC.UserStatus.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(user_id);
            status.serializeToStream(stream);
        }
    }

    public static class TL_updateChannelMessageViews extends TLRPC.Update {
        public static final int constructor = 0xf226ac08;

        public long channel_id;
        public int id;
        public int views;

        public void readParams(InputSerializedData stream, boolean exception) {
            channel_id = stream.readInt64(exception);
            id = stream.readInt32(exception);
            views = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(channel_id);
            stream.writeInt32(id);
            stream.writeInt32(views);
        }
    }

    public static class TL_updateGroupCallConnection extends TLRPC.Update {
        public static final int constructor = 0xb783982;

        public int flags;
        public boolean presentation;
        public TLRPC.TL_dataJSON params;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            presentation = hasFlag(flags, FLAG_0);
            params = TLRPC.TL_dataJSON.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, presentation);
            stream.writeInt32(flags);
            params.serializeToStream(stream);
        }
    }

    public static class TL_updateBotCommands extends TLRPC.Update {
        public static final int constructor = 0x4d712f2e;

        public TLRPC.Peer peer;
        public long bot_id;
        public ArrayList<TLRPC.BotCommand> commands = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            bot_id = stream.readInt64(exception);
            commands = Vector.deserialize(stream, TLRPC.BotCommand::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt64(bot_id);
            Vector.serialize(stream, commands);
        }
    }

    public static class TL_updateGeoLiveViewed extends TLRPC.Update {
        public static final int constructor = 0x871fb939;

        public TLRPC.Peer peer;
        public int msg_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            msg_id = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(msg_id);
        }
    }

    public static class TL_updateNotifySettings extends TLRPC.Update {
        public static final int constructor = 0xbec268ef;

        public TLRPC.NotifyPeer peer;
        public TLRPC.PeerNotifySettings notify_settings;

        public void readParams(InputSerializedData stream, boolean exception) {
            peer = TLRPC.NotifyPeer.TLdeserialize(stream, stream.readInt32(exception), exception);
            notify_settings = TLRPC.PeerNotifySettings.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            notify_settings.serializeToStream(stream);
        }
    }

    public static class TL_updateChannelParticipant extends TLRPC.Update {
        public static final int constructor = 0x985d3abb;

        public int flags;
        public long channel_id;
        public int date;
        public long actor_id;
        public long user_id;
        public TLRPC.ChannelParticipant prev_participant;
        public TLRPC.ChannelParticipant new_participant;
        public TLRPC.ExportedChatInvite invite;
        public int qts;
        public boolean via_chatlist;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            channel_id = stream.readInt64(exception);
            date = stream.readInt32(exception);
            actor_id = stream.readInt64(exception);
            user_id = stream.readInt64(exception);
            if (hasFlag(flags, FLAG_0)) {
                prev_participant = TLRPC.ChannelParticipant.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_1)) {
                new_participant = TLRPC.ChannelParticipant.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_2)) {
                invite = TLRPC.ExportedChatInvite.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            via_chatlist = hasFlag(flags, FLAG_3);
            qts = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_3, via_chatlist);
            stream.writeInt32(flags);
            stream.writeInt64(channel_id);
            stream.writeInt32(date);
            stream.writeInt64(actor_id);
            stream.writeInt64(user_id);
            if (hasFlag(flags, FLAG_0)) {
                prev_participant.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_1)) {
                new_participant.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_2)) {
                invite.serializeToStream(stream);
            }
            stream.writeInt32(qts);
        }
    }

    public static class TL_updateReadChannelDiscussionOutbox extends TLRPC.Update {
        public static final int constructor = 0x695c9e7c;

        public long channel_id;
        public int top_msg_id;
        public int read_max_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            channel_id = stream.readInt64(exception);
            top_msg_id = stream.readInt32(exception);
            read_max_id = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(channel_id);
            stream.writeInt32(top_msg_id);
            stream.writeInt32(read_max_id);
        }
    }

    public static class TL_updateChatParticipantDelete extends TLRPC.Update {
        public static final int constructor = 0xe32f3d77;

        public long chat_id;
        public long user_id;
        public int version;

        public void readParams(InputSerializedData stream, boolean exception) {
            chat_id = stream.readInt64(exception);
            user_id = stream.readInt64(exception);
            version = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(chat_id);
            stream.writeInt64(user_id);
            stream.writeInt32(version);
        }
    }

    public static class TL_updateEditMessage extends TLRPC.Update {
        public static final int constructor = 0xe40370a3;

        public TLRPC.Message message;
        public int pts;
        public int pts_count;

        public void readParams(InputSerializedData stream, boolean exception) {
            message = TLRPC.Message.TLdeserialize(stream, stream.readInt32(exception), exception);
            pts = stream.readInt32(exception);
            pts_count = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            message.serializeToStream(stream);
            stream.writeInt32(pts);
            stream.writeInt32(pts_count);
        }
    }

    public static class TL_updateRecentReactions extends TLRPC.Update {
        public static final int constructor = 0x6f7863f4;

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_updateSavedReactionTags extends TLRPC.Update {
        public static final int constructor = 0x39c67432;

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_updateWebBrowserSettings extends TLRPC.Update {
        public static final int constructor = 0xC39A2ADE;

        public int flags;
        public boolean open_external_browser;
        public boolean display_close_button;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            open_external_browser = hasFlag(flags, FLAG_0);
            display_close_button = hasFlag(flags, FLAG_1);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, open_external_browser);
            flags = setFlag(flags, FLAG_1, display_close_button);
            stream.writeInt32(flags);
        }
    }

    public static class TL_updateWebBrowserException extends TLRPC.Update {
        public static final int constructor = 0x140502D1;

        public int flags;
        public boolean delete;
        public boolean open_external_browser;
        public TL_account.WebDomainException exception;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            delete = hasFlag(flags, FLAG_1);
            if (hasFlag(flags, FLAG_0)) {
                open_external_browser = stream.readBool(exception);
            }
            this.exception = TL_account.WebDomainException.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_1, delete);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeBool(open_external_browser);
            }
            exception.serializeToStream(stream);
        }
    }

    public static class TL_updateNewEphemeralMessage extends TLRPC.Update {
        public static final int constructor = 0x20BCBBA1;

        public TLRPC.EphemeralMessage message;

        public void readParams(InputSerializedData stream, boolean exception) {
            message = TLRPC.EphemeralMessage.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            message.serializeToStream(stream);
        }
    }

    public static class TL_updateDeleteEphemeralMessages extends TLRPC.Update {
        public static final int constructor = 0x56DBFCF8;

        public TLRPC.Peer peer;
        public ArrayList<Integer> ids = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            ids = Vector.deserializeInt(stream, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            Vector.serializeInt(stream, ids);
        }
    }

    public static class TL_updateEditEphemeralMessage extends TLRPC.Update {
        public static final int constructor = 0x4BBB8F01;

        public TLRPC.EphemeralMessage message;

        public void readParams(InputSerializedData stream, boolean exception) {
            message = TLRPC.EphemeralMessage.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            message.serializeToStream(stream);
        }
    }
}

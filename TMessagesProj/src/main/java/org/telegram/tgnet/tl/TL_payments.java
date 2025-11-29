package org.telegram.tgnet.tl;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLMethod;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;

import java.util.ArrayList;

public class TL_payments {

    public static class connectedBotStarRef extends TLObject {
        public static final int constructor = 0x19a13f71;

        public int flags;
        public boolean revoked;
        public String url;
        public int date;
        public long bot_id;
        public int commission_permille;
        public int duration_months;
        public long participants;
        public long revenue;

        public static connectedBotStarRef TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final connectedBotStarRef result = connectedBotStarRef.constructor != constructor ? null : new connectedBotStarRef();
            return TLdeserialize(connectedBotStarRef.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            revoked = (flags & 2) != 0;
            url = stream.readString(exception);
            date = stream.readInt32(exception);
            bot_id = stream.readInt64(exception);
            commission_permille = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                duration_months = stream.readInt32(exception);
            }
            participants = stream.readInt64(exception);
            revenue = stream.readInt64(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = revoked ? flags | 2 : flags &~ 2;
            stream.writeInt32(flags);
            stream.writeString(url);
            stream.writeInt32(date);
            stream.writeInt64(bot_id);
            stream.writeInt32(commission_permille);
            if ((flags & 1) != 0) {
                stream.writeInt32(duration_months);
            }
            stream.writeInt64(participants);
            stream.writeInt64(revenue);
        }
    }

    public static class connectedStarRefBots extends TLObject {
        public static final int constructor = 0x98d5ea1d;

        public int count;
        public ArrayList<connectedBotStarRef> connected_bots = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<TLRPC.User>();

        public static connectedStarRefBots TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final connectedStarRefBots result = connectedStarRefBots.constructor != constructor ? null : new connectedStarRefBots();
            return TLdeserialize(connectedStarRefBots.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            count = stream.readInt32(exception);
            connected_bots = Vector.deserialize(stream, connectedBotStarRef::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(count);
            Vector.serialize(stream, connected_bots);
            Vector.serialize(stream, users);
        }
    }

    public static class suggestedStarRefBots extends TLObject {
        public static final int constructor = 0xb4d5d859;

        public int flags;
        public int count;
        public ArrayList<starRefProgram> suggested_bots = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();
        public String next_offset;

        public static suggestedStarRefBots TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final suggestedStarRefBots result = suggestedStarRefBots.constructor != constructor ? null : new suggestedStarRefBots();
            return TLdeserialize(suggestedStarRefBots.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            count = stream.readInt32(exception);
            suggested_bots = Vector.deserialize(stream, starRefProgram::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
            if ((flags & 1) != 0) {
                next_offset = stream.readString(exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(count);
            Vector.serialize(stream, suggested_bots);
            Vector.serialize(stream, users);
            if ((flags & 1) != 0) {
                stream.writeString(next_offset);
            }
        }
    }

    public static class starRefProgram extends TLObject {
        public static final int constructor = 0xdd0c66f2;

        public int flags;
        public long bot_id;
        public int commission_permille;
        public int duration_months;
        public int end_date;
        public TL_stars.StarsAmount daily_revenue_per_user = TL_stars.StarsAmount.ofStars(0);

        public static starRefProgram TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final starRefProgram result = starRefProgram.constructor != constructor ? null : new starRefProgram();
            return TLdeserialize(starRefProgram.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            bot_id = stream.readInt64(exception);
            commission_permille = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                duration_months = stream.readInt32(exception);
            }
            if ((flags & 2) != 0) {
                end_date = stream.readInt32(exception);
            }
            if ((flags & 4) != 0) {
                daily_revenue_per_user = TL_stars.StarsAmount.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt64(bot_id);
            stream.writeInt32(commission_permille);
            if ((flags & 1) != 0) {
                stream.writeInt32(duration_months);
            }
            if ((flags & 2) != 0) {
                stream.writeInt32(end_date);
            }
            if ((flags & 4) != 0) {
                daily_revenue_per_user.serializeToStream(stream);
            }
        }
    }

    public static class connectStarRefBot extends TLObject {
        public static final int constructor = 0x7ed5348a;

        public TLRPC.InputPeer peer;
        public TLRPC.InputUser bot;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return connectedStarRefBots.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            bot.serializeToStream(stream);
        }
    }

    public static class getSuggestedStarRefBots extends TLObject {
        public static final int constructor = 0xd6b48f7;

        public int flags;
        public boolean order_by_revenue;
        public boolean order_by_date;
        public TLRPC.InputPeer peer;
        public String offset;
        public int limit;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return suggestedStarRefBots.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = order_by_revenue ? flags | 1 : flags &~ 1;
            flags = order_by_date ? flags | 2 : flags &~ 2;
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            stream.writeString(offset);
            stream.writeInt32(limit);
        }
    }

    public static class getConnectedStarRefBots extends TLObject {
        public static final int constructor = 0x5869a553;

        public int flags;
        public TLRPC.InputPeer peer;
        public int offset_date;
        public String offset_link;
        public int limit;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return connectedStarRefBots.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            if ((flags & 4) != 0) {
                stream.writeInt32(offset_date);
                stream.writeString(offset_link);
            }
            stream.writeInt32(limit);
        }
    }


    public static class getConnectedStarRefBot extends TLObject {
        public static final int constructor = 0xb7d998f0;

        public TLRPC.InputPeer peer;
        public TLRPC.InputUser bot;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return connectedStarRefBots.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            bot.serializeToStream(stream);
        }
    }

    public static class editConnectedStarRefBot extends TLObject {
        public static final int constructor = 0xe4fca4a3;

        public int flags;
        public boolean revoked;
        public TLRPC.InputPeer peer;
        public String link;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return connectedStarRefBots.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = revoked ? flags | 1 : flags &~ 1;
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            stream.writeString(link);
        }
    }

    public static abstract class StarGiftActiveAuctions extends TLObject {

        private static StarGiftActiveAuctions fromConstructor(int constructor) {
            switch (constructor) {
                case TL_starGiftActiveAuctions.constructor:
                    return new TL_starGiftActiveAuctions();
                case TL_starGiftActiveAuctionsNotModified.constructor:
                    return new TL_starGiftActiveAuctionsNotModified();
            }
            return null;
        }

        public static StarGiftActiveAuctions TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            return TLdeserialize(StarGiftActiveAuctions.class, fromConstructor(constructor), stream, constructor, exception);
        }
    }

    public static class TL_starGiftActiveAuctionsNotModified extends StarGiftActiveAuctions {
        public static final int constructor = 0xDB33DAD0;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {

        }
    }

    public static class TL_starGiftActiveAuctions extends StarGiftActiveAuctions {
        public static final int constructor = 0x97F187D8;

        public ArrayList<TL_stars.TL_StarGiftActiveAuctionState> auctions = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, auctions);
            Vector.serialize(stream, users);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            auctions = Vector.deserialize(stream, TL_stars.TL_StarGiftActiveAuctionState::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }
    }

    public static class TL_StarGiftAuctionAcquiredGifts extends TLObject {
        public static final int constructor = 0x7D5BD1F0;

        public ArrayList<TL_stars.TL_StarGiftAuctionAcquiredGift> gifts = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, gifts);
            Vector.serialize(stream, users);
            Vector.serialize(stream, chats);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            gifts = Vector.deserialize(stream, TL_stars.TL_StarGiftAuctionAcquiredGift::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
        }

        public static TL_StarGiftAuctionAcquiredGifts TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final TL_StarGiftAuctionAcquiredGifts result = TL_StarGiftAuctionAcquiredGifts.constructor != constructor ? null : new TL_StarGiftAuctionAcquiredGifts();
            return TLdeserialize(TL_StarGiftAuctionAcquiredGifts.class, result, stream, constructor, exception);
        }
    }

    public static class TL_StarGiftAuctionState extends TLObject {
        public static final int constructor = 0x0E98E474;

        public TL_stars.StarGift gift;
        public TL_stars.StarGiftAuctionState state;
        public TL_stars.TL_StarGiftAuctionUserState user_state;
        public int timeout;
        public ArrayList<TLRPC.User> users;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            gift.serializeToStream(stream);
            state.serializeToStream(stream);
            user_state.serializeToStream(stream);
            stream.writeInt32(timeout);
            Vector.serialize(stream, users);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            gift = TL_stars.StarGift.TLdeserialize(stream, stream.readInt32(exception), exception);
            state = TL_stars.StarGiftAuctionState.TLdeserialize(stream, stream.readInt32(exception), exception);
            user_state = TL_stars.TL_StarGiftAuctionUserState.TLdeserialize(stream, stream.readInt32(exception), exception);
            timeout = stream.readInt32(exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        public static TL_StarGiftAuctionState TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final TL_StarGiftAuctionState result = TL_StarGiftAuctionState.constructor != constructor ? null : new TL_StarGiftAuctionState();
            return TLdeserialize(TL_StarGiftAuctionState.class, result, stream, constructor, exception);
        }
    }

    public static class TL_getStarGiftAuctionState extends TLMethod<TL_StarGiftAuctionState> {
        public static final int constructor = 0x5c9ff4d6;

        public TL_stars.InputStarGiftAuction auction;
        public int version;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            auction.serializeToStream(stream);
            stream.writeInt32(version);
        }

        @Override
        public TL_StarGiftAuctionState deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TL_StarGiftAuctionState.TLdeserialize(stream, constructor, exception);
        }
    }

    public static class TL_getStarGiftAuctionAcquiredGifts extends TLMethod<TL_StarGiftAuctionAcquiredGifts> {
        public static final int constructor = 0x6ba2cbec;
        public long gift_id;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(gift_id);
        }

        @Override
        public TL_StarGiftAuctionAcquiredGifts deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TL_StarGiftAuctionAcquiredGifts.TLdeserialize(stream, constructor, exception);
        }
    }

    public static class TL_getStarGiftActiveAuctions extends TLMethod<StarGiftActiveAuctions> {
        public static final int constructor = 0xa5d0514d;
        public long hash;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(hash);
        }

        @Override
        public StarGiftActiveAuctions deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return StarGiftActiveAuctions.TLdeserialize(stream, constructor, exception);
        }
    }
}

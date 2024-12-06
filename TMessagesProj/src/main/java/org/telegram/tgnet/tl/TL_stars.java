package org.telegram.tgnet.tl;

import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public class TL_stars {

    public static class StarGift extends TLObject {

        public int flags;
        public boolean limited;
        public boolean sold_out;
        public boolean birthday;
        public long id;
        public TLRPC.Document sticker;
        public long stars;
        public int availability_remains;
        public int availability_total;
        public long convert_stars;
        public int first_sale_date;
        public int last_sale_date;

        public static StarGift TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            StarGift result = null;
            switch (constructor) {
                case TL_starGift.constructor:
                    result = new TL_starGift();
                    break;
                case TL_starGift_layer190.constructor:
                    result = new TL_starGift_layer190();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in StarGift", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }

    }

    public static class TL_starGift extends StarGift {
        public static final int constructor = 0x49c577cd;

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = limited ? flags | 1 : flags &~ 1;
            flags = sold_out ? flags | 2 : flags &~ 2;
            flags = birthday ? flags | 4 : flags &~ 4;
            stream.writeInt32(flags);
            stream.writeInt64(id);
            sticker.serializeToStream(stream);
            stream.writeInt64(stars);
            if ((flags & 1) != 0) {
                stream.writeInt32(availability_remains);
                stream.writeInt32(availability_total);
            }
            stream.writeInt64(convert_stars);
            if ((flags & 2) != 0) {
                stream.writeInt32(first_sale_date);
                stream.writeInt32(last_sale_date);
            }
        }

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            limited = (flags & 1) != 0;
            sold_out = (flags & 2) != 0;
            birthday = (flags & 4) != 0;
            id = stream.readInt64(exception);
            sticker = TLRPC.Document.TLdeserialize(stream, stream.readInt32(exception), exception);
            stars = stream.readInt64(exception);
            if ((flags & 1) != 0) {
                availability_remains = stream.readInt32(exception);
                availability_total = stream.readInt32(exception);
            }
            convert_stars = stream.readInt64(exception);
            if ((flags & 2) != 0) {
                first_sale_date = stream.readInt32(exception);
                last_sale_date = stream.readInt32(exception);
            }
        }
    }

    public static class TL_starGift_layer190 extends TL_starGift {
        public static final int constructor = 0xaea174ee;

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = limited ? flags | 1 : flags &~ 1;
            stream.writeInt32(flags);
            stream.writeInt64(id);
            sticker.serializeToStream(stream);
            stream.writeInt64(stars);
            if ((flags & 1) != 0) {
                stream.writeInt32(availability_remains);
                stream.writeInt32(availability_total);
            }
            stream.writeInt64(convert_stars);
        }

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            limited = (flags & 1) != 0;
            id = stream.readInt64(exception);
            sticker = TLRPC.Document.TLdeserialize(stream, stream.readInt32(exception), exception);
            stars = stream.readInt64(exception);
            if ((flags & 1) != 0) {
                availability_remains = stream.readInt32(exception);
                availability_total = stream.readInt32(exception);
            }
            convert_stars = stream.readInt64(exception);
            sold_out = limited && availability_remains <= 0;
        }
    }

    public static class StarGifts extends TLObject {
        public static StarGifts TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            StarGifts result = null;
            switch (constructor) {
                case TL_starGifts.constructor:
                    result = new TL_starGifts();
                    break;
                case TL_starGiftsNotModified.constructor:
                    result = new TL_starGiftsNotModified();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in StarGifts", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }
    public static class TL_starGifts extends StarGifts {
        public static final int constructor = 0x901689ea;

        public int hash;
        public ArrayList<StarGift> gifts = new ArrayList<>();

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(hash);
            stream.writeInt32(0x1cb5c415);
            int count = gifts.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                gifts.get(a).serializeToStream(stream);
            }
        }

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            hash = stream.readInt32(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                StarGift gift = StarGift.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (gift == null) {
                    return;
                }
                gifts.add(gift);
            }
        }
    }
    public static class TL_starGiftsNotModified extends StarGifts {
        public static final int constructor = 0xa388a368;

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
        }

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {}
    }

    public static class getStarGifts extends TLObject {
        public static final int constructor = 0xc4563590;

        public int hash;

        @Override
        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return StarGifts.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(hash);
        }
    }

    public static class getUserStarGifts extends TLObject {
        public static final int constructor = 0x5e72c7e1;

        public TLRPC.InputUser user_id;
        public String offset;
        public int limit;

        @Override
        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TL_userStarGifts.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            user_id.serializeToStream(stream);
            stream.writeString(offset);
            stream.writeInt32(limit);
        }
    }

    public static class TL_userStarGifts extends TLObject {
        public static final int constructor = 0x6b65b517;

        public int flags;
        public int count;
        public ArrayList<UserStarGift> gifts = new ArrayList<>();
        public String next_offset;
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static TL_userStarGifts TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            TL_userStarGifts result = null;
            switch (constructor) {
                case TL_userStarGifts.constructor:
                    result = new TL_userStarGifts();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in TL_userStarGifts", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }

        @Override
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
                UserStarGift gift = UserStarGift.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (gift == null) {
                    return;
                }
                gifts.add(gift);
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
                TLRPC.User user = TLRPC.User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (user == null) {
                    return;
                }
                users.add(user);
            }
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(count);
            stream.writeInt32(0x1cb5c415);
            stream.writeInt32(gifts.size());
            for (int a = 0; a < gifts.size(); a++) {
                gifts.get(a).serializeToStream(stream);
            }
            if ((flags & 1) != 0) {
                stream.writeString(next_offset);
            }
            stream.writeInt32(0x1cb5c415);
            stream.writeInt32(users.size());
            for (int a = 0; a < users.size(); a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static class UserStarGift extends TLObject {

        public int flags;
        public boolean name_hidden;
        public boolean unsaved;
        public long from_id;
        public int date;
        public TL_stars.StarGift gift;
        public TLRPC.TL_textWithEntities message;
        public int msg_id;
        public long convert_stars;

        public static UserStarGift TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            UserStarGift result = null;
            switch (constructor) {
                case TL_userStarGift.constructor:
                    result = new TL_userStarGift();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in UserStarGift", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }
    public static class TL_userStarGift extends UserStarGift {
        public static final int constructor = 0xeea49a6e;

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = name_hidden ? flags | 1 : flags &~ 1;
            flags = unsaved ? flags | 32 : flags &~ 32;
            stream.writeInt32(flags);
            if ((flags & 2) != 0) {
                stream.writeInt64(from_id);
            }
            stream.writeInt32(date);
            gift.serializeToStream(stream);
            if ((flags & 4) != 0) {
                message.serializeToStream(stream);
            }
            if ((flags & 8) != 0) {
                stream.writeInt32(msg_id);
            }
            if ((flags & 16) != 0) {
                stream.writeInt64(convert_stars);
            }
        }

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            name_hidden = (flags & 1) != 0;
            unsaved = (flags & 32) != 0;
            if ((flags & 2) != 0) {
                from_id = stream.readInt64(exception);
            }
            date = stream.readInt32(exception);
            gift = TL_stars.StarGift.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 4) != 0) {
                message = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 8) != 0) {
                msg_id = stream.readInt32(exception);
            }
            if ((flags & 16) != 0) {
                convert_stars = stream.readInt64(exception);
            }
        }
    }

    public static class saveStarGift extends TLObject {
        public static final int constructor = 0x87acf08e;

        public int flags;
        public boolean unsave;
        public TLRPC.InputUser user_id;
        public int msg_id;

        @Override
        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = unsave ? flags | 1 : flags &~ 1;
            stream.writeInt32(flags);
            user_id.serializeToStream(stream);
            stream.writeInt32(msg_id);
        }
    }

    public static class convertStarGift extends TLObject {
        public static final int constructor = 0x421e027;

        public TLRPC.InputUser user_id;
        public int msg_id;

        @Override
        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            user_id.serializeToStream(stream);
            stream.writeInt32(msg_id);
        }
    }

    public static class TL_starsTopupOption extends TLObject {
        public static final int constructor = 0xbd915c0;

        public int flags;
        public boolean extended;
        public long stars;
        public String store_product;
        public String currency;
        public long amount;
        public boolean loadingStorePrice; //custom
        public boolean missingStorePrice; //custom

        public static TL_starsTopupOption TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (TL_starsTopupOption.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_starsTopupOption", constructor));
                } else {
                    return null;
                }
            }
            TL_starsTopupOption result = new TL_starsTopupOption();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            extended = (flags & 2) != 0;
            stars = stream.readInt64(exception);
            if ((flags & 1) != 0) {
                store_product = stream.readString(exception);
            }
            currency = stream.readString(exception);
            amount = stream.readInt64(exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = extended ? flags | 2 : flags &~ 2;
            stream.writeInt32(flags);
            stream.writeInt64(stars);
            if ((flags & 1) != 0) {
                stream.writeString(store_product);
            }
            stream.writeString(currency);
            stream.writeInt64(amount);
        }
    }

    public static class TL_starsGiftOption extends TLObject {
        public static final int constructor = 0x5e0589f1;

        public int flags;
        public boolean extended;
        public long stars;
        public String store_product;
        public String currency;
        public long amount;
        public boolean loadingStorePrice; //custom
        public boolean missingStorePrice; //custom

        public static TL_starsGiftOption TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (TL_starsGiftOption.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_starsGiftOption", constructor));
                } else {
                    return null;
                }
            }
            TL_starsGiftOption result = new TL_starsGiftOption();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            extended = (flags & 2) != 0;
            stars = stream.readInt64(exception);
            if ((flags & 1) != 0) {
                store_product = stream.readString(exception);
            }
            currency = stream.readString(exception);
            amount = stream.readInt64(exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = extended ? flags | 2 : flags &~ 2;
            stream.writeInt32(flags);
            stream.writeInt64(stars);
            if ((flags & 1) != 0) {
                stream.writeString(store_product);
            }
            stream.writeString(currency);
            stream.writeInt64(amount);
        }
    }

    public static class TL_starsGiveawayWinnersOption extends TLObject {
        public static final int constructor = 0x54236209;

        public int flags;
        public boolean isDefault;
        public int users;
        public long per_user_stars;

        public static TL_starsGiveawayWinnersOption TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (TL_starsGiveawayWinnersOption.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_starsGiveawayWinnersOption", constructor));
                } else {
                    return null;
                }
            }
            TL_starsGiveawayWinnersOption result = new TL_starsGiveawayWinnersOption();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            isDefault = (flags & 1) != 0;
            users = stream.readInt32(exception);
            per_user_stars = stream.readInt64(exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = isDefault ? (flags | 1) : (flags &~ 1);
            stream.writeInt32(flags);
            stream.writeInt32(users);
            stream.writeInt64(per_user_stars);
        }
    }

    public static class TL_starsGiveawayOption extends TLObject {
        public static final int constructor = 0x94ce852a;

        public int flags;
        public boolean extended;
        public boolean isDefault;
        public long stars;
        public int yearly_boosts;
        public String store_product;
        public String currency;
        public long amount;
        public ArrayList<TL_starsGiveawayWinnersOption> winners = new ArrayList<>();

        public boolean loadingStorePrice; //custom
        public boolean missingStorePrice; //custom

        public static TL_starsGiveawayOption TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (TL_starsGiveawayOption.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_starsGiveawayOption", constructor));
                } else {
                    return null;
                }
            }
            TL_starsGiveawayOption result = new TL_starsGiveawayOption();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            extended = (flags & 1) != 0;
            isDefault = (flags & 2) != 0;
            stars = stream.readInt64(exception);
            yearly_boosts = stream.readInt32(exception);
            if ((flags & 4) != 0) {
                store_product = stream.readString(exception);
            }
            currency = stream.readString(exception);
            amount = stream.readInt64(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_starsGiveawayWinnersOption object = TL_starsGiveawayWinnersOption.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                winners.add(object);
            }
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = extended ? (flags | 1) : (flags &~ 1);
            flags = isDefault ? (flags | 2) : (flags &~ 2);
            stream.writeInt32(flags);
            stream.writeInt64(stars);
            stream.writeInt32(yearly_boosts);
            if ((flags & 4) != 0) {
                stream.writeString(store_product);
            }
            stream.writeString(currency);
            stream.writeInt64(amount);
            stream.writeInt32(0x1cb5c415);
            int count = winners.size();
            stream.writeInt32(count);
            for (int i = 0; i < count; ++i) {
                winners.get(i).serializeToStream(stream);
            }
        }
    }

    public static class StarsTransactionPeer extends TLObject {

        public TLRPC.Peer peer;

        public static StarsTransactionPeer TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            StarsTransactionPeer result = null;
            switch (constructor) {
                case TL_starsTransactionPeer.constructor:
                    result = new TL_starsTransactionPeer();
                    break;
                case TL_starsTransactionPeerAppStore.constructor:
                    result = new TL_starsTransactionPeerAppStore();
                    break;
                case TL_starsTransactionPeerPlayMarket.constructor:
                    result = new TL_starsTransactionPeerPlayMarket();
                    break;
                case TL_starsTransactionPeerFragment.constructor:
                    result = new TL_starsTransactionPeerFragment();
                    break;
                case TL_starsTransactionPeerPremiumBot.constructor:
                    result = new TL_starsTransactionPeerPremiumBot();
                    break;
                case TL_starsTransactionPeerUnsupported.constructor:
                    result = new TL_starsTransactionPeerUnsupported();
                    break;
                case TL_starsTransactionPeerAds.constructor:
                    result = new TL_starsTransactionPeerAds();
                    break;
                case TL_starsTransactionPeerAPI.constructor:
                    result = new TL_starsTransactionPeerAPI();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in StarsTransactionPeer", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_starsTransactionPeer extends StarsTransactionPeer {
        public static final int constructor = 0xd80da15d;

        public void readParams(AbstractSerializedData stream, boolean exception) {
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
        }
    }

    public static class TL_starsTransactionPeerAppStore extends StarsTransactionPeer {
        public static final int constructor = 0xb457b375;

        public void readParams(AbstractSerializedData stream, boolean exception) {}

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_starsTransactionPeerPlayMarket extends StarsTransactionPeer {
        public static final int constructor = 0x7b560a0b;

        public void readParams(AbstractSerializedData stream, boolean exception) {}

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_starsTransactionPeerFragment extends StarsTransactionPeer {
        public static final int constructor = 0xe92fd902;

        public void readParams(AbstractSerializedData stream, boolean exception) {}

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_starsTransactionPeerPremiumBot extends StarsTransactionPeer {
        public static final int constructor = 0x250dbaf8;

        public void readParams(AbstractSerializedData stream, boolean exception) {}

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_starsTransactionPeerUnsupported extends StarsTransactionPeer {
        public static final int constructor = 0x95f2bfe4;

        public void readParams(AbstractSerializedData stream, boolean exception) {}

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_starsTransactionPeerAds extends StarsTransactionPeer {
        public static final int constructor = 0x60682812;

        public void readParams(AbstractSerializedData stream, boolean exception) {}

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_starsTransactionPeerAPI extends StarsTransactionPeer {
        public static final int constructor = 0xf9677aad;

        public void readParams(AbstractSerializedData stream, boolean exception) {}

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class StarsTransaction extends TLObject {

        public int flags;
        public boolean refund;
        public boolean pending;
        public boolean failed;
        public boolean gift;
        public boolean reaction;
        public boolean subscription;
        public boolean floodskip;
        public String id;
        public StarsAmount stars = new StarsAmount(0);
        public int date;
        public StarsTransactionPeer peer;
        public String title;
        public String description;
        public TLRPC.WebDocument photo;
        public int transaction_date;
        public String transaction_url;
        public byte[] bot_payload;
        public int msg_id;
        public ArrayList<TLRPC.MessageMedia> extended_media = new ArrayList<>();
        public int subscription_period;
        public int giveaway_post_id;
        public StarGift stargift;
        public int floodskip_number;
        public int starref_commission_permille;
        public TLRPC.Peer starref_peer;
        public StarsAmount starref_amount;

        public TLRPC.Peer sent_by; //custom
        public TLRPC.Peer received_by; //custom

        public static StarsTransaction TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            StarsTransaction result = null;
            switch (constructor) {
                case TL_starsTransaction_layer181.constructor:
                    result = new TL_starsTransaction_layer181();
                    break;
                case TL_starsTransaction_layer182.constructor:
                    result = new TL_starsTransaction_layer182();
                    break;
                case TL_starsTransaction_layer185.constructor:
                    result = new TL_starsTransaction_layer185();
                    break;
                case TL_starsTransaction_layer186.constructor:
                    result = new TL_starsTransaction_layer186();
                    break;
                case TL_starsTransaction_layer188.constructor:
                    result = new TL_starsTransaction_layer188();
                    break;
                case TL_starsTransaction_layer191.constructor:
                    result = new TL_starsTransaction_layer191();
                    break;
                case TL_starsTransaction_layer194.constructor:
                    result = new TL_starsTransaction_layer194();
                    break;
                case TL_starsTransaction.constructor:
                    result = new TL_starsTransaction();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in StarsTransaction", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }

    }

    public static class TL_starsTransaction_layer181 extends StarsTransaction {
        public static final int constructor = 0xcc7079b2;

        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            refund = (flags & 8) != 0;
            id = stream.readString(exception);
            stars = new StarsAmount(stream.readInt64(exception));
            date = stream.readInt32(exception);
            peer = StarsTransactionPeer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 1) != 0) {
                title = stream.readString(exception);
            }
            if ((flags & 2) != 0) {
                description = stream.readString(exception);
            }
            if ((flags & 4) != 0) {
                photo = TLRPC.WebDocument.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = refund ? flags | 8 : flags &~ 8;
            stream.writeInt32(flags);
            stream.writeInt64(stars.amount);
            stream.writeInt32(date);
            peer.serializeToStream(stream);
            if ((flags & 1) != 0) {
                stream.writeString(title);
            }
            if ((flags & 2) != 0) {
                stream.writeString(description);
            }
            if ((flags & 4) != 0) {
                photo.serializeToStream(stream);
            }
        }
    }

    public static class TL_starsTransaction_layer182 extends TL_starsTransaction {
        public static final int constructor = 0xaa00c898;

        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            refund = (flags & 8) != 0;
            pending = (flags & 16) != 0;
            failed = (flags & 64) != 0;
            id = stream.readString(exception);
            stars = new StarsAmount(stream.readInt64(exception));
            date = stream.readInt32(exception);
            peer = StarsTransactionPeer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 1) != 0) {
                title = stream.readString(exception);
            }
            if ((flags & 2) != 0) {
                description = stream.readString(exception);
            }
            if ((flags & 4) != 0) {
                photo = TLRPC.WebDocument.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 32) != 0) {
                transaction_date = stream.readInt32(exception);
                transaction_url = stream.readString(exception);
            }
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = refund ? flags | 8 : flags &~ 8;
            flags = pending ? flags | 16 : flags &~ 16;
            flags = failed ? flags | 64 : flags &~ 64;
            stream.writeInt32(flags);
            stream.writeInt64(stars.amount);
            stream.writeInt32(date);
            peer.serializeToStream(stream);
            if ((flags & 1) != 0) {
                stream.writeString(title);
            }
            if ((flags & 2) != 0) {
                stream.writeString(description);
            }
            if ((flags & 4) != 0) {
                photo.serializeToStream(stream);
            }
            if ((flags & 32) != 0) {
                stream.writeInt32(transaction_date);
                stream.writeString(transaction_url);
            }
        }
    }

    public static class StarsAmount extends TLObject {
        public static final int constructor = 0xbbb6b4a3;

        public long amount;
        public int nanos;

        public static StarsAmount TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (StarsAmount.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in StarsAmount", constructor));
                } else {
                    return null;
                }
            }
            StarsAmount result = new StarsAmount();
            result.readParams(stream, exception);
            return result;
        }

        public StarsAmount() {}
        public StarsAmount(long stars) {
            this.amount = stars;
            this.nanos = 0;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            amount = stream.readInt64(exception);
            nanos = stream.readInt32(exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(amount);
            stream.writeInt32(nanos);
        }

        public boolean equals(TL_stars.StarsAmount amount) {
            if (amount == null) return false;
            return this.amount == amount.amount && this.nanos == amount.nanos;
        }
    }

    public static class TL_starsTransaction extends StarsTransaction {
        public static final int constructor = 0x64dfc926;

        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            refund = (flags & 8) != 0;
            pending = (flags & 16) != 0;
            failed = (flags & 64) != 0;
            gift = (flags & 1024) != 0;
            reaction = (flags & 2048) != 0;
            subscription = (flags & 4096) != 0;
            floodskip = (flags & 32768) != 0;
            id = stream.readString(exception);
            stars = StarsAmount.TLdeserialize(stream, stream.readInt32(exception), exception);
            date = stream.readInt32(exception);
            peer = StarsTransactionPeer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 1) != 0) {
                title = stream.readString(exception);
            }
            if ((flags & 2) != 0) {
                description = stream.readString(exception);
            }
            if ((flags & 4) != 0) {
                photo = TLRPC.WebDocument.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 32) != 0) {
                transaction_date = stream.readInt32(exception);
                transaction_url = stream.readString(exception);
            }
            if ((flags & 128) != 0) {
                bot_payload = stream.readByteArray(exception);
            }
            if ((flags & 256) != 0) {
                msg_id = stream.readInt32(exception);
            }
            if ((flags & 512) != 0) {
                int magic = stream.readInt32(exception);
                if (magic != 0x1cb5c415) {
                    if (exception) {
                        throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                    }
                    return;
                }
                int count = stream.readInt32(exception);
                for (int a = 0; a < count; a++) {
                    TLRPC.MessageMedia object = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
                    if (object == null) {
                        return;
                    }
                    extended_media.add(object);
                }
            }
            if ((flags & 4096) != 0) {
                subscription_period = stream.readInt32(exception);
            }
            if ((flags & 8192) != 0) {
                giveaway_post_id = stream.readInt32(exception);
            }
            if ((flags & 16384) != 0) {
                stargift = StarGift.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 32768) != 0) {
                floodskip_number = stream.readInt32(exception);
            }
            if ((flags & 65536) != 0) {
                starref_commission_permille = stream.readInt32(exception);
            }
            if ((flags & 131072) != 0) {
                starref_peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
                starref_amount = StarsAmount.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = refund ? flags | 8 : flags &~ 8;
            flags = pending ? flags | 16 : flags &~ 16;
            flags = failed ? flags | 64 : flags &~ 64;
            flags = gift ? flags | 1024 : flags &~ 1024;
            flags = reaction ? flags | 2048 : flags &~ 2048;
            flags = subscription ? flags | 4096 : flags &~ 4096;
            flags = floodskip ? flags | 32768 : flags &~ 32768;
            stream.writeInt32(flags);
            stars.serializeToStream(stream);
            stream.writeInt32(date);
            peer.serializeToStream(stream);
            if ((flags & 1) != 0) {
                stream.writeString(title);
            }
            if ((flags & 2) != 0) {
                stream.writeString(description);
            }
            if ((flags & 4) != 0) {
                photo.serializeToStream(stream);
            }
            if ((flags & 32) != 0) {
                stream.writeInt32(transaction_date);
                stream.writeString(transaction_url);
            }
            if ((flags & 128) != 0) {
                stream.writeByteArray(bot_payload);
            }
            if ((flags & 256) != 0) {
                stream.writeInt32(msg_id);
            }
            if ((flags & 512) != 0) {
                stream.writeInt32(0x1cb5c415);
                int count = extended_media.size();
                stream.writeInt32(count);
                for (int i = 0; i < count; ++i) {
                    extended_media.get(i).serializeToStream(stream);
                }
            }
            if ((flags & 4096) != 0) {
                stream.writeInt32(subscription_period);
            }
            if ((flags & 8192) != 0) {
                stream.writeInt32(giveaway_post_id);
            }
            if ((flags & 16384) != 0) {
                stargift.serializeToStream(stream);
            }
            if ((flags & 32768) != 0) {
                stream.writeInt32(floodskip_number);
            }
            if ((flags & 65536) != 0) {
                stream.writeInt32(starref_commission_permille);
            }
            if ((flags & 131072) != 0) {
                starref_peer.serializeToStream(stream);
                starref_amount.serializeToStream(stream);
            }
        }
    }

    public static class TL_starsTransaction_layer194 extends TL_starsTransaction {
        public static final int constructor = 0x35d4f276;

        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            refund = (flags & 8) != 0;
            pending = (flags & 16) != 0;
            failed = (flags & 64) != 0;
            gift = (flags & 1024) != 0;
            reaction = (flags & 2048) != 0;
            subscription = (flags & 4096) != 0;
            floodskip = (flags & 32768) != 0;
            id = stream.readString(exception);
            stars = new StarsAmount(stream.readInt64(exception));
            date = stream.readInt32(exception);
            peer = StarsTransactionPeer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 1) != 0) {
                title = stream.readString(exception);
            }
            if ((flags & 2) != 0) {
                description = stream.readString(exception);
            }
            if ((flags & 4) != 0) {
                photo = TLRPC.WebDocument.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 32) != 0) {
                transaction_date = stream.readInt32(exception);
                transaction_url = stream.readString(exception);
            }
            if ((flags & 128) != 0) {
                bot_payload = stream.readByteArray(exception);
            }
            if ((flags & 256) != 0) {
                msg_id = stream.readInt32(exception);
            }
            if ((flags & 512) != 0) {
                int magic = stream.readInt32(exception);
                if (magic != 0x1cb5c415) {
                    if (exception) {
                        throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                    }
                    return;
                }
                int count = stream.readInt32(exception);
                for (int a = 0; a < count; a++) {
                    TLRPC.MessageMedia object = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
                    if (object == null) {
                        return;
                    }
                    extended_media.add(object);
                }
            }
            if ((flags & 4096) != 0) {
                subscription_period = stream.readInt32(exception);
            }
            if ((flags & 8192) != 0) {
                giveaway_post_id = stream.readInt32(exception);
            }
            if ((flags & 16384) != 0) {
                stargift = StarGift.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 32768) != 0) {
                floodskip_number = stream.readInt32(exception);
            }
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = refund ? flags | 8 : flags &~ 8;
            flags = pending ? flags | 16 : flags &~ 16;
            flags = failed ? flags | 64 : flags &~ 64;
            flags = gift ? flags | 1024 : flags &~ 1024;
            flags = reaction ? flags | 2048 : flags &~ 2048;
            flags = subscription ? flags | 4096 : flags &~ 4096;
            flags = floodskip ? flags | 32768 : flags &~ 32768;
            stream.writeInt32(flags);
            stream.writeInt64(stars.amount);
            stream.writeInt32(date);
            peer.serializeToStream(stream);
            if ((flags & 1) != 0) {
                stream.writeString(title);
            }
            if ((flags & 2) != 0) {
                stream.writeString(description);
            }
            if ((flags & 4) != 0) {
                photo.serializeToStream(stream);
            }
            if ((flags & 32) != 0) {
                stream.writeInt32(transaction_date);
                stream.writeString(transaction_url);
            }
            if ((flags & 128) != 0) {
                stream.writeByteArray(bot_payload);
            }
            if ((flags & 256) != 0) {
                stream.writeInt32(msg_id);
            }
            if ((flags & 512) != 0) {
                stream.writeInt32(0x1cb5c415);
                int count = extended_media.size();
                stream.writeInt32(count);
                for (int i = 0; i < count; ++i) {
                    extended_media.get(i).serializeToStream(stream);
                }
            }
            if ((flags & 4096) != 0) {
                stream.writeInt32(subscription_period);
            }
            if ((flags & 8192) != 0) {
                stream.writeInt32(giveaway_post_id);
            }
            if ((flags & 16384) != 0) {
                stargift.serializeToStream(stream);
            }
            if ((flags & 32768) != 0) {
                stream.writeInt32(floodskip_number);
            }
        }
    }

    public static class TL_starsTransaction_layer191 extends TL_starsTransaction {
        public static final int constructor = 0xa9ee4c2;

        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            refund = (flags & 8) != 0;
            pending = (flags & 16) != 0;
            failed = (flags & 64) != 0;
            gift = (flags & 1024) != 0;
            reaction = (flags & 2048) != 0;
            subscription = (flags & 4096) != 0;
            id = stream.readString(exception);
            stars = new StarsAmount(stream.readInt64(exception));
            date = stream.readInt32(exception);
            peer = StarsTransactionPeer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 1) != 0) {
                title = stream.readString(exception);
            }
            if ((flags & 2) != 0) {
                description = stream.readString(exception);
            }
            if ((flags & 4) != 0) {
                photo = TLRPC.WebDocument.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 32) != 0) {
                transaction_date = stream.readInt32(exception);
                transaction_url = stream.readString(exception);
            }
            if ((flags & 128) != 0) {
                bot_payload = stream.readByteArray(exception);
            }
            if ((flags & 256) != 0) {
                msg_id = stream.readInt32(exception);
            }
            if ((flags & 512) != 0) {
                int magic = stream.readInt32(exception);
                if (magic != 0x1cb5c415) {
                    if (exception) {
                        throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                    }
                    return;
                }
                int count = stream.readInt32(exception);
                for (int a = 0; a < count; a++) {
                    TLRPC.MessageMedia object = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
                    if (object == null) {
                        return;
                    }
                    extended_media.add(object);
                }
            }
            if ((flags & 4096) != 0) {
                subscription_period = stream.readInt32(exception);
            }
            if ((flags & 8192) != 0) {
                giveaway_post_id = stream.readInt32(exception);
            }
            if ((flags & 16384) != 0) {
                stargift = StarGift.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = refund ? flags | 8 : flags &~ 8;
            flags = pending ? flags | 16 : flags &~ 16;
            flags = failed ? flags | 64 : flags &~ 64;
            flags = gift ? flags | 1024 : flags &~ 1024;
            flags = reaction ? flags | 2048 : flags &~ 2048;
            flags = subscription ? flags | 4096 : flags &~ 4096;
            stream.writeInt32(flags);
            stream.writeInt64(stars.amount);
            stream.writeInt32(date);
            peer.serializeToStream(stream);
            if ((flags & 1) != 0) {
                stream.writeString(title);
            }
            if ((flags & 2) != 0) {
                stream.writeString(description);
            }
            if ((flags & 4) != 0) {
                photo.serializeToStream(stream);
            }
            if ((flags & 32) != 0) {
                stream.writeInt32(transaction_date);
                stream.writeString(transaction_url);
            }
            if ((flags & 128) != 0) {
                stream.writeByteArray(bot_payload);
            }
            if ((flags & 256) != 0) {
                stream.writeInt32(msg_id);
            }
            if ((flags & 512) != 0) {
                stream.writeInt32(0x1cb5c415);
                int count = extended_media.size();
                stream.writeInt32(count);
                for (int i = 0; i < count; ++i) {
                    extended_media.get(i).serializeToStream(stream);
                }
            }
            if ((flags & 4096) != 0) {
                stream.writeInt32(subscription_period);
            }
            if ((flags & 8192) != 0) {
                stream.writeInt32(giveaway_post_id);
            }
            if ((flags & 16384) != 0) {
                stargift.serializeToStream(stream);
            }
        }
    }

    public static class TL_starsTransaction_layer188 extends TL_starsTransaction {
        public static final int constructor = 0xee7522d5;

        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            refund = (flags & 8) != 0;
            pending = (flags & 16) != 0;
            failed = (flags & 64) != 0;
            gift = (flags & 1024) != 0;
            reaction = (flags & 2048) != 0;
            subscription = (flags & 4096) != 0;
            id = stream.readString(exception);
            stars = new StarsAmount(stream.readInt64(exception));
            date = stream.readInt32(exception);
            peer = StarsTransactionPeer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 1) != 0) {
                title = stream.readString(exception);
            }
            if ((flags & 2) != 0) {
                description = stream.readString(exception);
            }
            if ((flags & 4) != 0) {
                photo = TLRPC.WebDocument.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 32) != 0) {
                transaction_date = stream.readInt32(exception);
                transaction_url = stream.readString(exception);
            }
            if ((flags & 128) != 0) {
                bot_payload = stream.readByteArray(exception);
            }
            if ((flags & 256) != 0) {
                msg_id = stream.readInt32(exception);
            }
            if ((flags & 512) != 0) {
                int magic = stream.readInt32(exception);
                if (magic != 0x1cb5c415) {
                    if (exception) {
                        throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                    }
                    return;
                }
                int count = stream.readInt32(exception);
                for (int a = 0; a < count; a++) {
                    TLRPC.MessageMedia object = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
                    if (object == null) {
                        return;
                    }
                    extended_media.add(object);
                }
            }
            if ((flags & 4096) != 0) {
                subscription_period = stream.readInt32(exception);
            }
            if ((flags & 8192) != 0) {
                giveaway_post_id = stream.readInt32(exception);
            }
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = refund ? flags | 8 : flags &~ 8;
            flags = pending ? flags | 16 : flags &~ 16;
            flags = failed ? flags | 64 : flags &~ 64;
            flags = gift ? flags | 1024 : flags &~ 1024;
            flags = reaction ? flags | 2048 : flags &~ 2048;
            flags = subscription ? flags | 4096 : flags &~ 4096;
            stream.writeInt32(flags);
            stream.writeInt64(stars.amount);
            stream.writeInt32(date);
            peer.serializeToStream(stream);
            if ((flags & 1) != 0) {
                stream.writeString(title);
            }
            if ((flags & 2) != 0) {
                stream.writeString(description);
            }
            if ((flags & 4) != 0) {
                photo.serializeToStream(stream);
            }
            if ((flags & 32) != 0) {
                stream.writeInt32(transaction_date);
                stream.writeString(transaction_url);
            }
            if ((flags & 128) != 0) {
                stream.writeByteArray(bot_payload);
            }
            if ((flags & 256) != 0) {
                stream.writeInt32(msg_id);
            }
            if ((flags & 512) != 0) {
                stream.writeInt32(0x1cb5c415);
                int count = extended_media.size();
                stream.writeInt32(count);
                for (int i = 0; i < count; ++i) {
                    extended_media.get(i).serializeToStream(stream);
                }
            }
            if ((flags & 4096) != 0) {
                stream.writeInt32(subscription_period);
            }
            if ((flags & 8192) != 0) {
                stream.writeInt32(giveaway_post_id);
            }
        }
    }

    public static class TL_starsTransaction_layer186 extends TL_starsTransaction {
        public static final int constructor = 0x433aeb2b;

        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            refund = (flags & 8) != 0;
            pending = (flags & 16) != 0;
            failed = (flags & 64) != 0;
            gift = (flags & 1024) != 0;
            reaction = (flags & 2048) != 0;
            subscription = (flags & 4096) != 0;
            id = stream.readString(exception);
            stars = new StarsAmount(stream.readInt64(exception));
            date = stream.readInt32(exception);
            peer = StarsTransactionPeer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 1) != 0) {
                title = stream.readString(exception);
            }
            if ((flags & 2) != 0) {
                description = stream.readString(exception);
            }
            if ((flags & 4) != 0) {
                photo = TLRPC.WebDocument.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 32) != 0) {
                transaction_date = stream.readInt32(exception);
                transaction_url = stream.readString(exception);
            }
            if ((flags & 128) != 0) {
                bot_payload = stream.readByteArray(exception);
            }
            if ((flags & 256) != 0) {
                msg_id = stream.readInt32(exception);
            }
            if ((flags & 512) != 0) {
                int magic = stream.readInt32(exception);
                if (magic != 0x1cb5c415) {
                    if (exception) {
                        throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                    }
                    return;
                }
                int count = stream.readInt32(exception);
                for (int a = 0; a < count; a++) {
                    TLRPC.MessageMedia object = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
                    if (object == null) {
                        return;
                    }
                    extended_media.add(object);
                }
            }
            if ((flags & 4096) != 0) {
                subscription_period = stream.readInt32(exception);
            }
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = refund ? flags | 8 : flags &~ 8;
            flags = pending ? flags | 16 : flags &~ 16;
            flags = failed ? flags | 64 : flags &~ 64;
            flags = gift ? flags | 1024 : flags &~ 1024;
            flags = reaction ? flags | 2048 : flags &~ 2048;
            flags = subscription ? flags | 4096 : flags &~ 4096;
            stream.writeInt32(flags);
            stream.writeInt64(stars.amount);
            stream.writeInt32(date);
            peer.serializeToStream(stream);
            if ((flags & 1) != 0) {
                stream.writeString(title);
            }
            if ((flags & 2) != 0) {
                stream.writeString(description);
            }
            if ((flags & 4) != 0) {
                photo.serializeToStream(stream);
            }
            if ((flags & 32) != 0) {
                stream.writeInt32(transaction_date);
                stream.writeString(transaction_url);
            }
            if ((flags & 128) != 0) {
                stream.writeByteArray(bot_payload);
            }
            if ((flags & 256) != 0) {
                stream.writeInt32(msg_id);
            }
            if ((flags & 512) != 0) {
                stream.writeInt32(0x1cb5c415);
                int count = extended_media.size();
                stream.writeInt32(count);
                for (int i = 0; i < count; ++i) {
                    extended_media.get(i).serializeToStream(stream);
                }
            }
            if ((flags & 4096) != 0) {
                stream.writeInt32(subscription_period);
            }
        }
    }

    public static class TL_starsTransaction_layer185 extends TL_starsTransaction {
        public static final int constructor = 0x2db5418f;

        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            refund = (flags & 8) != 0;
            pending = (flags & 16) != 0;
            failed = (flags & 64) != 0;
            gift = (flags & 1024) != 0;
            id = stream.readString(exception);
            stars = new StarsAmount(stream.readInt64(exception));
            date = stream.readInt32(exception);
            peer = StarsTransactionPeer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 1) != 0) {
                title = stream.readString(exception);
            }
            if ((flags & 2) != 0) {
                description = stream.readString(exception);
            }
            if ((flags & 4) != 0) {
                photo = TLRPC.WebDocument.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 32) != 0) {
                transaction_date = stream.readInt32(exception);
                transaction_url = stream.readString(exception);
            }
            if ((flags & 128) != 0) {
                bot_payload = stream.readByteArray(exception);
            }
            if ((flags & 256) != 0) {
                msg_id = stream.readInt32(exception);
            }
            if ((flags & 512) != 0) {
                int magic = stream.readInt32(exception);
                if (magic != 0x1cb5c415) {
                    if (exception) {
                        throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                    }
                    return;
                }
                int count = stream.readInt32(exception);
                for (int a = 0; a < count; a++) {
                    TLRPC.MessageMedia object = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
                    if (object == null) {
                        return;
                    }
                    extended_media.add(object);
                }
            }
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = refund ? flags | 8 : flags &~ 8;
            flags = pending ? flags | 16 : flags &~ 16;
            flags = failed ? flags | 64 : flags &~ 64;
            flags = gift ? flags | 1024 : flags &~ 1024;
            stream.writeInt32(flags);
            stream.writeInt64(stars.amount);
            stream.writeInt32(date);
            peer.serializeToStream(stream);
            if ((flags & 1) != 0) {
                stream.writeString(title);
            }
            if ((flags & 2) != 0) {
                stream.writeString(description);
            }
            if ((flags & 4) != 0) {
                photo.serializeToStream(stream);
            }
            if ((flags & 32) != 0) {
                stream.writeInt32(transaction_date);
                stream.writeString(transaction_url);
            }
            if ((flags & 128) != 0) {
                stream.writeByteArray(bot_payload);
            }
            if ((flags & 256) != 0) {
                stream.writeInt32(msg_id);
            }
            if ((flags & 512) != 0) {
                stream.writeInt32(0x1cb5c415);
                int count = extended_media.size();
                stream.writeInt32(count);
                for (int i = 0; i < count; ++i) {
                    extended_media.get(i).serializeToStream(stream);
                }
            }
        }
    }

    public static class StarsStatus extends TLObject {

        public int flags;
        public StarsAmount balance = new StarsAmount(0);
        public ArrayList<StarsSubscription> subscriptions = new ArrayList<>();
        public String subscriptions_next_offset;
        public long subscriptions_missing_balance;
        public ArrayList<StarsTransaction> history = new ArrayList<>();
        public String next_offset;
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static StarsStatus TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            StarsStatus result = null;
            switch (constructor) {
                case TL_payments_starsStatus_layer194.constructor:
                    result = new TL_payments_starsStatus_layer194();
                    break;
                case TL_payments_starsStatus.constructor:
                    result = new TL_payments_starsStatus();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in StarsStatus", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_payments_starsStatus extends StarsStatus {
        public static final int constructor = 0x6c9ce8ed;

        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            balance = StarsAmount.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 2) != 0) {
                int magic = stream.readInt32(exception);
                if (magic != 0x1cb5c415) {
                    if (exception) {
                        throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                    }
                    return;
                }
                int count = stream.readInt32(exception);
                for (int a = 0; a < count; a++) {
                    StarsSubscription object = StarsSubscription.TLdeserialize(stream, stream.readInt32(exception), exception);
                    if (object == null) {
                        return;
                    }
                    subscriptions.add(object);
                }
            }
            if ((flags & 4) != 0) {
                subscriptions_next_offset = stream.readString(exception);
            }
            if ((flags & 16) != 0) {
                subscriptions_missing_balance = stream.readInt64(exception);
            }
            if ((flags & 8) != 0) {
                int magic = stream.readInt32(exception);
                if (magic != 0x1cb5c415) {
                    if (exception) {
                        throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                    }
                    return;
                }
                int count = stream.readInt32(exception);
                for (int a = 0; a < count; a++) {
                    StarsTransaction object = StarsTransaction.TLdeserialize(stream, stream.readInt32(exception), exception);
                    if (object == null) {
                        return;
                    }
                    history.add(object);
                }
            }
            if ((flags & 1) != 0) {
                next_offset = stream.readString(exception);
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
            balance.serializeToStream(stream);
            if ((flags & 2) != 0) {
                stream.writeInt32(0x1cb5c415);
                int count = subscriptions.size();
                stream.writeInt32(count);
                for (int i = 0; i < count; ++i) {
                    subscriptions.get(i).serializeToStream(stream);
                }
            }
            if ((flags & 4) != 0) {
                stream.writeString(subscriptions_next_offset);
            }
            if ((flags & 16) != 0) {
                stream.writeInt64(subscriptions_missing_balance);
            }
            stream.writeInt32(0x1cb5c415);
            int count = history.size();
            stream.writeInt32(count);
            for (int i = 0; i < count; ++i) {
                history.get(i).serializeToStream(stream);
            }
            if ((flags & 1) != 0) {
                stream.writeString(next_offset);
            }
            stream.writeInt32(0x1cb5c415);
            count = chats.size();
            stream.writeInt32(count);
            for (int i = 0; i < count; ++i) {
                chats.get(i).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int i = 0; i < count; ++i) {
                users.get(i).serializeToStream(stream);
            }
        }
    }

    public static class TL_payments_starsStatus_layer194 extends TL_payments_starsStatus {
        public static final int constructor = 0xbbfa316c;


        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            balance = new StarsAmount(stream.readInt64(exception));
            if ((flags & 2) != 0) {
                int magic = stream.readInt32(exception);
                if (magic != 0x1cb5c415) {
                    if (exception) {
                        throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                    }
                    return;
                }
                int count = stream.readInt32(exception);
                for (int a = 0; a < count; a++) {
                    StarsSubscription object = StarsSubscription.TLdeserialize(stream, stream.readInt32(exception), exception);
                    if (object == null) {
                        return;
                    }
                    subscriptions.add(object);
                }
            }
            if ((flags & 4) != 0) {
                subscriptions_next_offset = stream.readString(exception);
            }
            if ((flags & 16) != 0) {
                subscriptions_missing_balance = stream.readInt64(exception);
            }
            if ((flags & 8) != 0) {
                int magic = stream.readInt32(exception);
                if (magic != 0x1cb5c415) {
                    if (exception) {
                        throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                    }
                    return;
                }
                int count = stream.readInt32(exception);
                for (int a = 0; a < count; a++) {
                    StarsTransaction object = StarsTransaction.TLdeserialize(stream, stream.readInt32(exception), exception);
                    if (object == null) {
                        return;
                    }
                    history.add(object);
                }
            }
            if ((flags & 1) != 0) {
                next_offset = stream.readString(exception);
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
            stream.writeInt64(balance.amount);
            if ((flags & 2) != 0) {
                stream.writeInt32(0x1cb5c415);
                int count = subscriptions.size();
                stream.writeInt32(count);
                for (int i = 0; i < count; ++i) {
                    subscriptions.get(i).serializeToStream(stream);
                }
            }
            if ((flags & 4) != 0) {
                stream.writeString(subscriptions_next_offset);
            }
            if ((flags & 16) != 0) {
                stream.writeInt64(subscriptions_missing_balance);
            }
            stream.writeInt32(0x1cb5c415);
            int count = history.size();
            stream.writeInt32(count);
            for (int i = 0; i < count; ++i) {
                history.get(i).serializeToStream(stream);
            }
            if ((flags & 1) != 0) {
                stream.writeString(next_offset);
            }
            stream.writeInt32(0x1cb5c415);
            count = chats.size();
            stream.writeInt32(count);
            for (int i = 0; i < count; ++i) {
                chats.get(i).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int i = 0; i < count; ++i) {
                users.get(i).serializeToStream(stream);
            }
        }
    }

    public static class TL_payments_getStarsTopupOptions extends TLObject {
        public static final int constructor = 0xc00ec7d3;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            TLRPC.Vector vector = new TLRPC.Vector();
            int size = stream.readInt32(exception);
            for (int a = 0; a < size; a++) {
                TL_starsTopupOption object = TL_starsTopupOption.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return vector;
                }
                vector.objects.add(object);
            }
            return vector;
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_payments_getStarsGiftOptions extends TLObject {
        public static final int constructor = 0xd3c96bc8;

        public int flags;
        public TLRPC.InputUser user_id;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            TLRPC.Vector vector = new TLRPC.Vector();
            int size = stream.readInt32(exception);
            for (int a = 0; a < size; a++) {
                TL_starsGiftOption object = TL_starsGiftOption.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return vector;
                }
                vector.objects.add(object);
            }
            return vector;
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                user_id.serializeToStream(stream);
            }
        }
    }

    public static class TL_payments_getStarsGiveawayOptions extends TLObject {
        public static final int constructor = 0xbd1efd3e;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            TLRPC.Vector vector = new TLRPC.Vector();
            int size = stream.readInt32(exception);
            for (int a = 0; a < size; a++) {
                TL_starsGiveawayOption object = TL_starsGiveawayOption.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return vector;
                }
                vector.objects.add(object);
            }
            return vector;
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_payments_getStarsStatus extends TLObject {
        public static final int constructor = 0x104fcfa7;

        public TLRPC.InputPeer peer;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return StarsStatus.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
        }
    }

    public static class TL_payments_getStarsTransactions extends TLObject {
        public static final int constructor = 0x673ac2f9;

        public int flags;
        public boolean inbound;
        public boolean outbound;
        public TLRPC.InputPeer peer;
        public String offset;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return StarsStatus.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = inbound ? flags | 1 : flags &~ 1;
            flags = outbound ? flags | 2 : flags &~ 2;
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            stream.writeString(offset);
        }
    }

    public static class TL_payments_sendStarsForm extends TLObject {
        public static final int constructor = 0x7998c914;

        public long form_id;
        public TLRPC.InputInvoice invoice;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TLRPC.TL_payments_paymentResult.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(form_id);
            invoice.serializeToStream(stream);
        }
    }

    public static class StarsSubscription extends TLObject {

        public int flags;
        public boolean canceled;
        public boolean can_refulfill;
        public boolean missing_balance;
        public boolean bot_canceled;
        public String id;
        public TLRPC.Peer peer;
        public int until_date;
        public TL_starsSubscriptionPricing pricing;
        public String chat_invite_hash;
        public String title;
        public TLRPC.WebDocument photo;
        public String invoice_slug;

        public static StarsSubscription TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            StarsSubscription result = null;
            switch (constructor) {
                case TL_starsSubscription.constructor:
                    result = new TL_starsSubscription();
                    break;
                case TL_starsSubscription_layer193.constructor:
                    result = new TL_starsSubscription_layer193();
                    break;
                case TL_starsSubscription_old.constructor:
                    result = new TL_starsSubscription_old();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in StarsTransaction", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }

    }

    public static class TL_starsSubscription extends StarsSubscription {
        public static final int constructor = 0x2e6eab1a;

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            canceled = (flags & 1) != 0;
            can_refulfill = (flags & 2) != 0;
            missing_balance = (flags & 4) != 0;
            bot_canceled = (flags & 128) != 0;
            id = stream.readString(exception);
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            until_date = stream.readInt32(exception);
            pricing = TL_starsSubscriptionPricing.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 8) != 0) {
                chat_invite_hash = stream.readString(exception);
            }
            if ((flags & 16) != 0) {
                title = stream.readString(exception);
            }
            if ((flags & 32) != 0) {
                photo = TLRPC.WebDocument.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 64) != 0) {
                invoice_slug = stream.readString(exception);
            }
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = canceled ? (flags | 1) : (flags &~ 1);
            flags = can_refulfill ? (flags | 2) : (flags &~ 2);
            flags = missing_balance ? (flags | 4) : (flags &~ 4);
            flags = bot_canceled ? (flags | 128) : (flags &~ 128);
            stream.writeInt32(flags);
            stream.writeString(id);
            peer.serializeToStream(stream);
            stream.writeInt32(until_date);
            pricing.serializeToStream(stream);
            if ((flags & 8) != 0) {
                stream.writeString(chat_invite_hash);
            }
            if ((flags & 16) != 0) {
                stream.writeString(title);
            }
            if ((flags & 32) != 0) {
                photo.serializeToStream(stream);
            }
            if ((flags & 64) != 0) {
                stream.writeString(invoice_slug);
            }
        }
    }

    public static class TL_starsSubscription_layer193 extends StarsSubscription {
        public static final int constructor = 0x538ecf18;

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            canceled = (flags & 1) != 0;
            can_refulfill = (flags & 2) != 0;
            missing_balance = (flags & 4) != 0;
            id = stream.readString(exception);
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            until_date = stream.readInt32(exception);
            pricing = TL_starsSubscriptionPricing.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 8) != 0) {
                chat_invite_hash = stream.readString(exception);
            }
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = canceled ? (flags | 1) : (flags &~ 1);
            flags = can_refulfill ? (flags | 2) : (flags &~ 2);
            flags = missing_balance ? (flags | 4) : (flags &~ 4);
            stream.writeInt32(flags);
            stream.writeString(id);
            peer.serializeToStream(stream);
            stream.writeInt32(until_date);
            pricing.serializeToStream(stream);
            if ((flags & 8) != 0) {
                stream.writeString(chat_invite_hash);
            }
        }
    }

    public static class TL_starsSubscription_old extends TL_starsSubscription {
        public static final int constructor = 0xd073f1e6;

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            canceled = (flags & 1) != 0;
            can_refulfill = (flags & 2) != 0;
            missing_balance = (flags & 4) != 0;
            id = stream.readString(exception);
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            until_date = stream.readInt32(exception);
            pricing = TL_starsSubscriptionPricing.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = canceled ? (flags | 1) : (flags &~ 1);
            flags = can_refulfill ? (flags | 2) : (flags &~ 2);
            flags = missing_balance ? (flags | 4) : (flags &~ 4);
            stream.writeInt32(flags);
            stream.writeString(id);
            peer.serializeToStream(stream);
            stream.writeInt32(until_date);
            pricing.serializeToStream(stream);
        }
    }

    public static class TL_starsSubscriptionPricing extends TLObject {
        public static final int constructor = 0x5416d58;

        public int period;
        public long amount;

        public static TL_starsSubscriptionPricing TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (TL_starsSubscriptionPricing.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_starsSubscriptionPricing", constructor));
                } else {
                    return null;
                }
            }
            TL_starsSubscriptionPricing result = new TL_starsSubscriptionPricing();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            period = stream.readInt32(exception);
            amount = stream.readInt64(exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(period);
            stream.writeInt64(amount);
        }
    }

    public static class TL_getStarsSubscriptions extends TLObject {
        public static final int constructor = 0x32512c5;

        public int flags;
        public boolean missing_balance;
        public TLRPC.InputPeer peer;
        public String offset;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return StarsStatus.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = missing_balance ? (flags | 1) : (flags &~ 1);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            stream.writeString(offset);
        }
    }

    public static class TL_changeStarsSubscription extends TLObject {
        public static final int constructor = 0xc7770878;

        public int flags;
        public TLRPC.InputPeer peer;
        public String subscription_id;
        public Boolean canceled;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = (canceled != null) ? (flags | 1) : (flags &~ 1);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            stream.writeString(subscription_id);
            if ((flags & 1) != 0) {
                stream.writeBool(canceled);
            }
        }
    }

    public static class TL_fulfillStarsSubscription extends TLObject {
        public static final int constructor = 0xcc5bebb3;

        public TLRPC.InputPeer peer;
        public String subscription_id;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeString(subscription_id);
        }
    }
}

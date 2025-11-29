package org.telegram.tgnet.tl;

import androidx.annotation.Nullable;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.utils.tlutils.AmountUtils;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLMethod;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;

import java.util.ArrayList;

public class TL_stars {

    public static class StarGift extends TLObject {

        public int flags;
        public boolean limited;
        public boolean sold_out;
        public boolean birthday;
        public boolean require_premium;
        public boolean resale_ton_only;
        public boolean limited_per_user;
        public boolean peer_color_available;
        public int per_user_total;
        public int per_user_remains;
        public int locked_until_date;
        public boolean can_upgrade;
        public boolean auction;
        public long id;
        public long gift_id;
        public TLRPC.Document sticker;
        public long stars;
        public int availability_remains;
        public int availability_total;
        public long availability_resale;
        public long convert_stars;
        public int first_sale_date;
        public int last_sale_date;
        public long upgrade_stars;
        public long resell_min_stars;
        public boolean theme_available;
        public TLRPC.Peer theme_peer;
        public TLRPC.PeerColor peer_color;
        public TLRPC.Peer host_id;

        public String title;
        public String slug;
        public int num;
        public TLRPC.Peer owner_id;
        public String owner_name;
        public String owner_address;
        public ArrayList<StarGiftAttribute> attributes = new ArrayList<>();
        public int availability_issued;
        public String gift_address;
        public TLRPC.Peer released_by;
        public long value_amount;
        public String value_currency;
        public @Nullable ArrayList<StarsAmount> resell_amount;
        public String auction_slug;
        public int gifts_per_round;

        public AmountUtils.Amount getResellAmount(AmountUtils.Currency currency) {
            if (resell_amount == null || resell_amount.isEmpty()) {
                return AmountUtils.Amount.fromNano(0, currency);
            }

            for (StarsAmount r : resell_amount) {
                if (r.getCurrency() == currency) {
                    return AmountUtils.Amount.of(r);
                }
            }

            return AmountUtils.Amount.fromNano(0, currency);
        }

        @Deprecated
        public long getResellStars() {
            AmountUtils.Amount amount = getResellAmount(AmountUtils.Currency.STARS);
            return amount != null ? amount.asDecimal() : 0;
        }

        private static StarGift fromConstructor(int constructor) {
            switch (constructor) {
                case TL_starGift.constructor:                   return new TL_starGift();
                case TL_starGiftUnique.constructor:             return new TL_starGiftUnique();
                case TL_starGiftUnique_layer215.constructor:    return new TL_starGiftUnique_layer215();
                case TL_starGiftUnique_layer214.constructor:    return new TL_starGiftUnique_layer214();
                case TL_starGiftUnique_layer213.constructor:    return new TL_starGiftUnique_layer213();
                case TL_starGiftUnique_layer211.constructor:    return new TL_starGiftUnique_layer211();
                case TL_starGiftUnique_layer210.constructor:    return new TL_starGiftUnique_layer210();
                case TL_starGiftUnique_layer206.constructor:    return new TL_starGiftUnique_layer206();
                case TL_starGiftUnique_layer202.constructor:    return new TL_starGiftUnique_layer202();
                case TL_starGiftUnique_layer198.constructor:    return new TL_starGiftUnique_layer198();
                case TL_starGiftUnique_layer197.constructor:    return new TL_starGiftUnique_layer197();
                case TL_starGiftUnique_layer196.constructor:    return new TL_starGiftUnique_layer196();
                case TL_starGift_layer217.constructor:          return new TL_starGift_layer217();
                case TL_starGift_layer212.constructor:          return new TL_starGift_layer212();
                case TL_starGift_layer209.constructor:          return new TL_starGift_layer209();
                case TL_starGift_layer206.constructor:          return new TL_starGift_layer206();
                case TL_starGift_layer202.constructor:          return new TL_starGift_layer202();
                case TL_starGift_layer190.constructor:          return new TL_starGift_layer190();
                case TL_starGift_layer195.constructor:          return new TL_starGift_layer195();
            }
            return null;
        }

        public static StarGift TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            return TLdeserialize(StarGift.class, fromConstructor(constructor), stream, constructor, exception);
        }

        @Nullable
        public TLRPC.Document getDocument() {
            if (sticker != null) {
                return sticker;
            }
            for (TL_stars.StarGiftAttribute attr : attributes) {
                if (attr instanceof TL_stars.starGiftAttributeModel) {
                    return ((starGiftAttributeModel) attr).document;
                }
            }
            return null;
        }
    }

    public static class TL_starGiftUnique extends StarGift {
        public static final int constructor = 0xb0bf741b;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            require_premium = hasFlag(flags, FLAG_6);
            resale_ton_only = hasFlag(flags, FLAG_7);
            theme_available = hasFlag(flags, FLAG_9);
            id = stream.readInt64(exception);
            gift_id = stream.readInt64(exception);
            title = stream.readString(exception);
            slug = stream.readString(exception);
            num = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                owner_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 2) != 0) {
                owner_name = stream.readString(exception);
            }
            if ((flags & 4) != 0) {
                owner_address = stream.readString(exception);
            }
            attributes = Vector.deserialize(stream, StarGiftAttribute::TLdeserialize, exception);
            availability_issued = stream.readInt32(exception);
            availability_total = stream.readInt32(exception);
            if ((flags & 8) != 0) {
                gift_address = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_4)) {
                resell_amount = Vector.deserialize(stream,StarsAmount::TLdeserialize, exception);
            }
            if ((flags & 32) != 0) {
                released_by = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 256) != 0) {
                value_amount = stream.readInt64(exception);
                value_currency = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                theme_peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_11)) {
                peer_color = TLRPC.PeerColor.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_12)) {
                host_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_4, resell_amount != null && !resell_amount.isEmpty());
            flags = setFlag(flags, FLAG_6, require_premium);
            flags = setFlag(flags, FLAG_7, resale_ton_only);
            flags = setFlag(flags, FLAG_9, theme_available);
            stream.writeInt32(flags);
            stream.writeInt64(id);
            stream.writeInt64(gift_id);
            stream.writeString(title);
            stream.writeString(slug);
            stream.writeInt32(num);
            if ((flags & 1) != 0) {
                owner_id.serializeToStream(stream);
            }
            if ((flags & 2) != 0) {
                stream.writeString(owner_name);
            }
            if ((flags & 4) != 0) {
                stream.writeString(owner_address);
            }
            Vector.serialize(stream, attributes);
            stream.writeInt32(availability_issued);
            stream.writeInt32(availability_total);
            if ((flags & 8) != 0) {
                stream.writeString(gift_address);
            }
            if ((flags & 16) != 0) {
                Vector.serialize(stream, resell_amount);
            }
            if ((flags & 32) != 0) {
                released_by.serializeToStream(stream);
            }
            if ((flags & 256) != 0) {
                stream.writeInt64(value_amount);
                stream.writeString(value_currency);
            }
            if (hasFlag(flags, FLAG_10)) {
                theme_peer.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_11)) {
                peer_color.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_12)) {
                host_id.serializeToStream(stream);
            }
        }
    }

    public static class TL_starGiftUnique_layer215 extends TL_starGiftUnique {
        public static final int constructor = 0x3a0893b8;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            require_premium = hasFlag(flags, FLAG_6);
            resale_ton_only = hasFlag(flags, FLAG_7);
            theme_available = hasFlag(flags, FLAG_9);
            id = stream.readInt64(exception);
            gift_id = stream.readInt64(exception);
            title = stream.readString(exception);
            slug = stream.readString(exception);
            num = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                owner_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 2) != 0) {
                owner_name = stream.readString(exception);
            }
            if ((flags & 4) != 0) {
                owner_address = stream.readString(exception);
            }
            attributes = Vector.deserialize(stream, StarGiftAttribute::TLdeserialize, exception);
            availability_issued = stream.readInt32(exception);
            availability_total = stream.readInt32(exception);
            if ((flags & 8) != 0) {
                gift_address = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_4)) {
                resell_amount = Vector.deserialize(stream,StarsAmount::TLdeserialize, exception);
            }
            if ((flags & 32) != 0) {
                released_by = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 256) != 0) {
                value_amount = stream.readInt64(exception);
                value_currency = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                theme_peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_11)) {
                peer_color = TLRPC.PeerColor.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_4, resell_amount != null && !resell_amount.isEmpty());
            flags = setFlag(flags, FLAG_6, require_premium);
            flags = setFlag(flags, FLAG_7, resale_ton_only);
            flags = setFlag(flags, FLAG_9, theme_available);
            stream.writeInt32(flags);
            stream.writeInt64(id);
            stream.writeInt64(gift_id);
            stream.writeString(title);
            stream.writeString(slug);
            stream.writeInt32(num);
            if ((flags & 1) != 0) {
                owner_id.serializeToStream(stream);
            }
            if ((flags & 2) != 0) {
                stream.writeString(owner_name);
            }
            if ((flags & 4) != 0) {
                stream.writeString(owner_address);
            }
            Vector.serialize(stream, attributes);
            stream.writeInt32(availability_issued);
            stream.writeInt32(availability_total);
            if ((flags & 8) != 0) {
                stream.writeString(gift_address);
            }
            if ((flags & 16) != 0) {
                Vector.serialize(stream, resell_amount);
            }
            if ((flags & 32) != 0) {
                released_by.serializeToStream(stream);
            }
            if ((flags & 256) != 0) {
                stream.writeInt64(value_amount);
                stream.writeString(value_currency);
            }
            if (hasFlag(flags, FLAG_10)) {
                theme_peer.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_11)) {
                peer_color.serializeToStream(stream);
            }
        }
    }

    public static class TL_starGiftUnique_layer214 extends TL_starGiftUnique {
        public static final int constructor = 0x1befe865;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            require_premium = hasFlag(flags, FLAG_6);
            resale_ton_only = hasFlag(flags, FLAG_7);
            theme_available = hasFlag(flags, FLAG_9);
            id = stream.readInt64(exception);
            gift_id = stream.readInt64(exception);
            title = stream.readString(exception);
            slug = stream.readString(exception);
            num = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                owner_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 2) != 0) {
                owner_name = stream.readString(exception);
            }
            if ((flags & 4) != 0) {
                owner_address = stream.readString(exception);
            }
            attributes = Vector.deserialize(stream, StarGiftAttribute::TLdeserialize, exception);
            availability_issued = stream.readInt32(exception);
            availability_total = stream.readInt32(exception);
            if ((flags & 8) != 0) {
                gift_address = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_4)) {
                resell_amount = Vector.deserialize(stream,StarsAmount::TLdeserialize, exception);
            }
            if ((flags & 32) != 0) {
                released_by = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 256) != 0) {
                value_amount = stream.readInt64(exception);
                value_currency = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                theme_peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_4, resell_amount != null && !resell_amount.isEmpty());
            flags = setFlag(flags, FLAG_6, require_premium);
            flags = setFlag(flags, FLAG_7, resale_ton_only);
            flags = setFlag(flags, FLAG_9, theme_available);
            stream.writeInt32(flags);
            stream.writeInt64(id);
            stream.writeInt64(gift_id);
            stream.writeString(title);
            stream.writeString(slug);
            stream.writeInt32(num);
            if ((flags & 1) != 0) {
                owner_id.serializeToStream(stream);
            }
            if ((flags & 2) != 0) {
                stream.writeString(owner_name);
            }
            if ((flags & 4) != 0) {
                stream.writeString(owner_address);
            }
            Vector.serialize(stream, attributes);
            stream.writeInt32(availability_issued);
            stream.writeInt32(availability_total);
            if ((flags & 8) != 0) {
                stream.writeString(gift_address);
            }
            if ((flags & 16) != 0) {
                Vector.serialize(stream, resell_amount);
            }
            if ((flags & 32) != 0) {
                released_by.serializeToStream(stream);
            }
            if ((flags & 256) != 0) {
                stream.writeInt64(value_amount);
                stream.writeString(value_currency);
            }
            if (hasFlag(flags, FLAG_10)) {
                theme_peer.serializeToStream(stream);
            }
        }
    }

    public static class TL_starGiftUnique_layer213 extends TL_starGiftUnique {
        public static final int constructor = 0x26a5553e;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            require_premium = hasFlag(flags, FLAG_6);
            resale_ton_only = hasFlag(flags, FLAG_7);
            id = stream.readInt64(exception);
            gift_id = stream.readInt64(exception);
            title = stream.readString(exception);
            slug = stream.readString(exception);
            num = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                owner_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 2) != 0) {
                owner_name = stream.readString(exception);
            }
            if ((flags & 4) != 0) {
                owner_address = stream.readString(exception);
            }
            attributes = Vector.deserialize(stream, StarGiftAttribute::TLdeserialize, exception);
            availability_issued = stream.readInt32(exception);
            availability_total = stream.readInt32(exception);
            if ((flags & 8) != 0) {
                gift_address = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_4)) {
                resell_amount = Vector.deserialize(stream,StarsAmount::TLdeserialize, exception);
            }
            if ((flags & 32) != 0) {
                released_by = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 256) != 0) {
                value_amount = stream.readInt64(exception);
                value_currency = stream.readString(exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_4, resell_amount != null && !resell_amount.isEmpty());
            flags = setFlag(flags, FLAG_6, require_premium);
            flags = setFlag(flags, FLAG_7, resale_ton_only);
            stream.writeInt32(flags);
            stream.writeInt64(id);
            stream.writeInt64(gift_id);
            stream.writeString(title);
            stream.writeString(slug);
            stream.writeInt32(num);
            if ((flags & 1) != 0) {
                owner_id.serializeToStream(stream);
            }
            if ((flags & 2) != 0) {
                stream.writeString(owner_name);
            }
            if ((flags & 4) != 0) {
                stream.writeString(owner_address);
            }
            Vector.serialize(stream, attributes);
            stream.writeInt32(availability_issued);
            stream.writeInt32(availability_total);
            if ((flags & 8) != 0) {
                stream.writeString(gift_address);
            }
            if ((flags & 16) != 0) {
                Vector.serialize(stream, resell_amount);
            }
            if ((flags & 32) != 0) {
                released_by.serializeToStream(stream);
            }
            if ((flags & 256) != 0) {
                stream.writeInt64(value_amount);
                stream.writeString(value_currency);
            }
        }
    }

    public static class TL_starGiftUnique_layer211 extends TL_starGiftUnique {
        public static final int constructor = 0x3a274d50;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            require_premium = hasFlag(flags, FLAG_6);
            resale_ton_only = hasFlag(flags, FLAG_7);
            id = stream.readInt64(exception);
            title = stream.readString(exception);
            slug = stream.readString(exception);
            num = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                owner_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 2) != 0) {
                owner_name = stream.readString(exception);
            }
            if ((flags & 4) != 0) {
                owner_address = stream.readString(exception);
            }
            attributes = Vector.deserialize(stream, StarGiftAttribute::TLdeserialize, exception);
            availability_issued = stream.readInt32(exception);
            availability_total = stream.readInt32(exception);
            if ((flags & 8) != 0) {
                gift_address = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_4)) {
                resell_amount = Vector.deserialize(stream,StarsAmount::TLdeserialize, exception);
            }
            if ((flags & 32) != 0) {
                released_by = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_4, resell_amount != null && !resell_amount.isEmpty());
            flags = setFlag(flags, FLAG_6, require_premium);
            flags = setFlag(flags, FLAG_7, resale_ton_only);

            stream.writeInt32(flags);
            stream.writeInt64(id);
            stream.writeString(title);
            stream.writeString(slug);
            stream.writeInt32(num);
            if ((flags & 1) != 0) {
                owner_id.serializeToStream(stream);
            }
            if ((flags & 2) != 0) {
                stream.writeString(owner_name);
            }
            if ((flags & 4) != 0) {
                stream.writeString(owner_address);
            }
            Vector.serialize(stream, attributes);
            stream.writeInt32(availability_issued);
            stream.writeInt32(availability_total);
            if ((flags & 8) != 0) {
                stream.writeString(gift_address);
            }
            if ((flags & 16) != 0) {
                Vector.serialize(stream, resell_amount);
            }
            if ((flags & 32) != 0) {
                released_by.serializeToStream(stream);
            }
        }
    }

    public static class TL_starGiftUnique_layer210 extends TL_starGiftUnique {
        public static final int constructor = 0xf63778ae;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            require_premium = hasFlag(flags, FLAG_6);
            id = stream.readInt64(exception);
            title = stream.readString(exception);
            slug = stream.readString(exception);
            num = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                owner_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 2) != 0) {
                owner_name = stream.readString(exception);
            }
            if ((flags & 4) != 0) {
                owner_address = stream.readString(exception);
            }
            attributes = Vector.deserialize(stream, StarGiftAttribute::TLdeserialize, exception);
            availability_issued = stream.readInt32(exception);
            availability_total = stream.readInt32(exception);
            if ((flags & 8) != 0) {
                gift_address = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_4)) {
                resell_amount = new ArrayList<>();
                resell_amount.add(StarsAmount.ofStars(stream.readInt64(exception)));
            }
            if ((flags & 32) != 0) {
                released_by = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_6, require_premium);
            stream.writeInt32(flags);
            stream.writeInt64(id);
            stream.writeString(title);
            stream.writeString(slug);
            stream.writeInt32(num);
            if ((flags & 1) != 0) {
                owner_id.serializeToStream(stream);
            }
            if ((flags & 2) != 0) {
                stream.writeString(owner_name);
            }
            if ((flags & 4) != 0) {
                stream.writeString(owner_address);
            }
            Vector.serialize(stream, attributes);
            stream.writeInt32(availability_issued);
            stream.writeInt32(availability_total);
            if ((flags & 8) != 0) {
                stream.writeString(gift_address);
            }
            if ((flags & 16) != 0) {
                stream.writeInt64(getResellStars());
            }
            if ((flags & 32) != 0) {
                released_by.serializeToStream(stream);
            }
        }
    }

    public static class TL_starGiftUnique_layer206 extends TL_starGiftUnique {
        public static final int constructor = 0x6411db89;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            id = stream.readInt64(exception);
            title = stream.readString(exception);
            slug = stream.readString(exception);
            num = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                owner_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 2) != 0) {
                owner_name = stream.readString(exception);
            }
            if ((flags & 4) != 0) {
                owner_address = stream.readString(exception);
            }
            attributes = Vector.deserialize(stream, StarGiftAttribute::TLdeserialize, exception);
            availability_issued = stream.readInt32(exception);
            availability_total = stream.readInt32(exception);
            if ((flags & 8) != 0) {
                gift_address = stream.readString(exception);
            }
            if ((flags & 16) != 0) {
                resell_amount = new ArrayList<>();
                resell_amount.add(StarsAmount.ofStars(stream.readInt64(exception)));
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt64(id);
            stream.writeString(title);
            stream.writeString(slug);
            stream.writeInt32(num);
            if ((flags & 1) != 0) {
                owner_id.serializeToStream(stream);
            }
            if ((flags & 2) != 0) {
                stream.writeString(owner_name);
            }
            if ((flags & 4) != 0) {
                stream.writeString(owner_address);
            }
            Vector.serialize(stream, attributes);
            stream.writeInt32(availability_issued);
            stream.writeInt32(availability_total);
            if ((flags & 8) != 0) {
                stream.writeString(gift_address);
            }
            if ((flags & 16) != 0) {
                stream.writeInt64(getResellStars());
            }
        }
    }

    public static class TL_starGiftUnique_layer202 extends TL_starGiftUnique {
        public static final int constructor = 0x5c62d151;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            id = stream.readInt64(exception);
            title = stream.readString(exception);
            slug = stream.readString(exception);
            num = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                owner_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 2) != 0) {
                owner_name = stream.readString(exception);
            }
            if ((flags & 4) != 0) {
                owner_address = stream.readString(exception);
            }
            attributes = Vector.deserialize(stream, StarGiftAttribute::TLdeserialize, exception);
            availability_issued = stream.readInt32(exception);
            availability_total = stream.readInt32(exception);
            if ((flags & 8) != 0) {
                gift_address = stream.readString(exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt64(id);
            stream.writeString(title);
            stream.writeString(slug);
            stream.writeInt32(num);
            if ((flags & 1) != 0) {
                owner_id.serializeToStream(stream);
            }
            if ((flags & 2) != 0) {
                stream.writeString(owner_name);
            }
            if ((flags & 4) != 0) {
                stream.writeString(owner_address);
            }
            Vector.serialize(stream, attributes);
            stream.writeInt32(availability_issued);
            stream.writeInt32(availability_total);
            if ((flags & 8) != 0) {
                stream.writeString(gift_address);
            }
        }
    }

    public static class TL_starGiftUnique_layer198 extends TL_starGiftUnique {
        public static final int constructor = 0xf2fe7e4a;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            id = stream.readInt64(exception);
            title = stream.readString(exception);
            slug = stream.readString(exception);
            num = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                owner_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 2) != 0) {
                owner_name = stream.readString(exception);
            }
            if ((flags & 4) != 0) {
                owner_address = stream.readString(exception);
            }
            attributes = Vector.deserialize(stream, StarGiftAttribute::TLdeserialize, exception);
            availability_issued = stream.readInt32(exception);
            availability_total = stream.readInt32(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt64(id);
            stream.writeString(title);
            stream.writeString(slug);
            stream.writeInt32(num);
            if ((flags & 1) != 0) {
                owner_id.serializeToStream(stream);
            }
            if ((flags & 2) != 0) {
                stream.writeString(owner_name);
            }
            if ((flags & 4) != 0) {
                stream.writeString(owner_address);
            }
            Vector.serialize(stream, attributes);
            stream.writeInt32(availability_issued);
            stream.writeInt32(availability_total);
        }
    }

    public static class TL_starGiftUnique_layer197 extends TL_starGiftUnique {
        public static final int constructor = 0x3482f322;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            id = stream.readInt64(exception);
            title = stream.readString(exception);
            slug = stream.readString(exception);
            num = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                owner_id = new TLRPC.TL_peerUser();
                owner_id.user_id = stream.readInt64(exception);
            }
            if ((flags & 2) != 0) {
                owner_name = stream.readString(exception);
            }
            attributes = Vector.deserialize(stream, StarGiftAttribute::TLdeserialize, exception);
            availability_issued = stream.readInt32(exception);
            availability_total = stream.readInt32(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt64(id);
            stream.writeString(title);
            stream.writeString(slug);
            stream.writeInt32(num);
            if ((flags & 1) != 0) {
                stream.writeInt64(owner_id.user_id);
            }
            if ((flags & 2) != 0) {
                stream.writeString(owner_name);
            }
            Vector.serialize(stream, attributes);
            stream.writeInt32(availability_issued);
            stream.writeInt32(availability_total);
        }
    }

    public static class TL_starGiftUnique_layer196 extends TL_starGiftUnique {
        public static final int constructor = 0x6a1407cd;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            title = stream.readString(exception);
            num = stream.readInt32(exception);
            owner_id = new TLRPC.TL_peerUser();
            owner_id.user_id = stream.readInt64(exception);
            attributes = Vector.deserialize(stream, StarGiftAttribute::TLdeserialize, exception);
            availability_issued = stream.readInt32(exception);
            availability_total = stream.readInt32(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeString(title);
            stream.writeInt32(num);
            stream.writeInt64(owner_id.user_id);
            Vector.serialize(stream, attributes);
            stream.writeInt32(availability_issued);
            stream.writeInt32(availability_total);
        }
    }

    public static class TL_starGift extends StarGift {
        public static final int constructor = 0x1B9A4D7F;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = limited ? flags | 1 : flags &~ 1;
            flags = sold_out ? flags | 2 : flags &~ 2;
            flags = birthday ? flags | 4 : flags &~ 4;
            flags = can_upgrade ? flags | 8 : flags &~ 8;
            flags = setFlag(flags, FLAG_7, require_premium);
            flags = setFlag(flags, FLAG_8, limited_per_user);
            flags = setFlag(flags, FLAG_10, peer_color_available);
            flags = setFlag(flags, FLAG_11, auction);
            stream.writeInt32(flags);
            stream.writeInt64(id);
            sticker.serializeToStream(stream);
            stream.writeInt64(stars);
            if ((flags & 1) != 0) {
                stream.writeInt32(availability_remains);
                stream.writeInt32(availability_total);
            }
            if ((flags & 16) != 0) {
                stream.writeInt64(availability_resale);
            }
            stream.writeInt64(convert_stars);
            if ((flags & 2) != 0) {
                stream.writeInt32(first_sale_date);
                stream.writeInt32(last_sale_date);
            }
            if ((flags & 8) != 0) {
                stream.writeInt64(upgrade_stars);
            }
            if ((flags & 16) != 0) {
                stream.writeInt64(resell_min_stars);
            }
            if ((flags & 32) != 0) {
                stream.writeString(title);
            }
            if ((flags & 64) != 0) {
                released_by.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_8)) {
                stream.writeInt32(per_user_total);
                stream.writeInt32(per_user_remains);
            }
            if (hasFlag(flags, FLAG_9)) {
                stream.writeInt32(locked_until_date);
            }
            if (hasFlag(flags, FLAG_11)) {
                stream.writeString(auction_slug);
                stream.writeInt32(gifts_per_round);
            }
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            limited = (flags & 1) != 0;
            sold_out = (flags & 2) != 0;
            birthday = (flags & 4) != 0;
            can_upgrade = (flags & 8) != 0;
            require_premium = hasFlag(flags, FLAG_7);
            limited_per_user = hasFlag(flags, FLAG_8);
            peer_color_available = hasFlag(flags, FLAG_10);
            auction = hasFlag(flags, FLAG_11);
            id = stream.readInt64(exception);
            sticker = TLRPC.Document.TLdeserialize(stream, stream.readInt32(exception), exception);
            stars = stream.readInt64(exception);
            if ((flags & 1) != 0) {
                availability_remains = stream.readInt32(exception);
                availability_total = stream.readInt32(exception);
            }
            if ((flags & 16) != 0) {
                availability_resale = stream.readInt64(exception);
            }
            convert_stars = stream.readInt64(exception);
            if ((flags & 2) != 0) {
                first_sale_date = stream.readInt32(exception);
                last_sale_date = stream.readInt32(exception);
            }
            if ((flags & 8) != 0) {
                upgrade_stars = stream.readInt64(exception);
            }
            if ((flags & 16) != 0) {
                resell_min_stars = stream.readInt64(exception);
            }
            if ((flags & 32) != 0) {
                title = stream.readString(exception);
            }
            if ((flags & 64) != 0) {
                released_by = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_8)) {
                per_user_total = stream.readInt32(exception);
                per_user_remains = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_9)) {
                locked_until_date = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_11)) {
                auction_slug = stream.readString(exception);
                gifts_per_round = stream.readInt32(exception);
            }
        }
    }

    public static class TL_starGift_layer217 extends TL_starGift {
        public static final int constructor = 0x80ac53c3;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = limited ? flags | 1 : flags &~ 1;
            flags = sold_out ? flags | 2 : flags &~ 2;
            flags = birthday ? flags | 4 : flags &~ 4;
            flags = can_upgrade ? flags | 8 : flags &~ 8;
            flags = setFlag(flags, FLAG_7, require_premium);
            flags = setFlag(flags, FLAG_8, limited_per_user);
            flags = setFlag(flags, FLAG_10, peer_color_available);
            stream.writeInt32(flags);
            stream.writeInt64(id);
            sticker.serializeToStream(stream);
            stream.writeInt64(stars);
            if ((flags & 1) != 0) {
                stream.writeInt32(availability_remains);
                stream.writeInt32(availability_total);
            }
            if ((flags & 16) != 0) {
                stream.writeInt64(availability_resale);
            }
            stream.writeInt64(convert_stars);
            if ((flags & 2) != 0) {
                stream.writeInt32(first_sale_date);
                stream.writeInt32(last_sale_date);
            }
            if ((flags & 8) != 0) {
                stream.writeInt64(upgrade_stars);
            }
            if ((flags & 16) != 0) {
                stream.writeInt64(resell_min_stars);
            }
            if ((flags & 32) != 0) {
                stream.writeString(title);
            }
            if ((flags & 64) != 0) {
                released_by.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_8)) {
                stream.writeInt32(per_user_total);
                stream.writeInt32(per_user_remains);
            }
            if (hasFlag(flags, FLAG_9)) {
                stream.writeInt32(locked_until_date);
            }
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            limited = (flags & 1) != 0;
            sold_out = (flags & 2) != 0;
            birthday = (flags & 4) != 0;
            can_upgrade = (flags & 8) != 0;
            require_premium = hasFlag(flags, FLAG_7);
            limited_per_user = hasFlag(flags, FLAG_8);
            peer_color_available = hasFlag(flags, FLAG_10);
            id = stream.readInt64(exception);
            sticker = TLRPC.Document.TLdeserialize(stream, stream.readInt32(exception), exception);
            stars = stream.readInt64(exception);
            if ((flags & 1) != 0) {
                availability_remains = stream.readInt32(exception);
                availability_total = stream.readInt32(exception);
            }
            if ((flags & 16) != 0) {
                availability_resale = stream.readInt64(exception);
            }
            convert_stars = stream.readInt64(exception);
            if ((flags & 2) != 0) {
                first_sale_date = stream.readInt32(exception);
                last_sale_date = stream.readInt32(exception);
            }
            if ((flags & 8) != 0) {
                upgrade_stars = stream.readInt64(exception);
            }
            if ((flags & 16) != 0) {
                resell_min_stars = stream.readInt64(exception);
            }
            if ((flags & 32) != 0) {
                title = stream.readString(exception);
            }
            if ((flags & 64) != 0) {
                released_by = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_8)) {
                per_user_total = stream.readInt32(exception);
                per_user_remains = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_9)) {
                locked_until_date = stream.readInt32(exception);
            }
        }
    }

    public static class TL_starGift_layer212 extends TL_starGift {
        public static final int constructor = 0xbcff5b;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = limited ? flags | 1 : flags &~ 1;
            flags = sold_out ? flags | 2 : flags &~ 2;
            flags = birthday ? flags | 4 : flags &~ 4;
            flags = can_upgrade ? flags | 8 : flags &~ 8;
            flags = setFlag(flags, FLAG_7, require_premium);
            flags = setFlag(flags, FLAG_8, limited_per_user);
            stream.writeInt32(flags);
            stream.writeInt64(id);
            sticker.serializeToStream(stream);
            stream.writeInt64(stars);
            if ((flags & 1) != 0) {
                stream.writeInt32(availability_remains);
                stream.writeInt32(availability_total);
            }
            if ((flags & 16) != 0) {
                stream.writeInt64(availability_resale);
            }
            stream.writeInt64(convert_stars);
            if ((flags & 2) != 0) {
                stream.writeInt32(first_sale_date);
                stream.writeInt32(last_sale_date);
            }
            if ((flags & 8) != 0) {
                stream.writeInt64(upgrade_stars);
            }
            if ((flags & 16) != 0) {
                stream.writeInt64(resell_min_stars);
            }
            if ((flags & 32) != 0) {
                stream.writeString(title);
            }
            if ((flags & 64) != 0) {
                released_by.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_8)) {
                stream.writeInt32(per_user_total);
                stream.writeInt32(per_user_remains);
            }
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            limited = (flags & 1) != 0;
            sold_out = (flags & 2) != 0;
            birthday = (flags & 4) != 0;
            can_upgrade = (flags & 8) != 0;
            require_premium = hasFlag(flags, FLAG_7);
            limited_per_user = hasFlag(flags, FLAG_8);
            id = stream.readInt64(exception);
            sticker = TLRPC.Document.TLdeserialize(stream, stream.readInt32(exception), exception);
            stars = stream.readInt64(exception);
            if ((flags & 1) != 0) {
                availability_remains = stream.readInt32(exception);
                availability_total = stream.readInt32(exception);
            }
            if ((flags & 16) != 0) {
                availability_resale = stream.readInt64(exception);
            }
            convert_stars = stream.readInt64(exception);
            if ((flags & 2) != 0) {
                first_sale_date = stream.readInt32(exception);
                last_sale_date = stream.readInt32(exception);
            }
            if ((flags & 8) != 0) {
                upgrade_stars = stream.readInt64(exception);
            }
            if ((flags & 16) != 0) {
                resell_min_stars = stream.readInt64(exception);
            }
            if ((flags & 32) != 0) {
                title = stream.readString(exception);
            }
            if ((flags & 64) != 0) {
                released_by = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_8)) {
                per_user_total = stream.readInt32(exception);
                per_user_remains = stream.readInt32(exception);
            }
        }
    }

    public static class TL_starGift_layer209 extends TL_starGift {
        public static final int constructor = 0x7f853c12;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = limited ? flags | 1 : flags &~ 1;
            flags = sold_out ? flags | 2 : flags &~ 2;
            flags = birthday ? flags | 4 : flags &~ 4;
            flags = can_upgrade ? flags | 8 : flags &~ 8;
            stream.writeInt32(flags);
            stream.writeInt64(id);
            sticker.serializeToStream(stream);
            stream.writeInt64(stars);
            if ((flags & 1) != 0) {
                stream.writeInt32(availability_remains);
                stream.writeInt32(availability_total);
            }
            if ((flags & 16) != 0) {
                stream.writeInt64(availability_resale);
            }
            stream.writeInt64(convert_stars);
            if ((flags & 2) != 0) {
                stream.writeInt32(first_sale_date);
                stream.writeInt32(last_sale_date);
            }
            if ((flags & 8) != 0) {
                stream.writeInt64(upgrade_stars);
            }
            if ((flags & 16) != 0) {
                stream.writeInt64(resell_min_stars);
            }
            if ((flags & 32) != 0) {
                stream.writeString(title);
            }
            if ((flags & 64) != 0) {
                released_by.serializeToStream(stream);
            }
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            limited = (flags & 1) != 0;
            sold_out = (flags & 2) != 0;
            birthday = (flags & 4) != 0;
            can_upgrade = (flags & 8) != 0;
            id = stream.readInt64(exception);
            sticker = TLRPC.Document.TLdeserialize(stream, stream.readInt32(exception), exception);
            stars = stream.readInt64(exception);
            if ((flags & 1) != 0) {
                availability_remains = stream.readInt32(exception);
                availability_total = stream.readInt32(exception);
            }
            if ((flags & 16) != 0) {
                availability_resale = stream.readInt64(exception);
            }
            convert_stars = stream.readInt64(exception);
            if ((flags & 2) != 0) {
                first_sale_date = stream.readInt32(exception);
                last_sale_date = stream.readInt32(exception);
            }
            if ((flags & 8) != 0) {
                upgrade_stars = stream.readInt64(exception);
            }
            if ((flags & 16) != 0) {
                resell_min_stars = stream.readInt64(exception);
            }
            if ((flags & 32) != 0) {
                title = stream.readString(exception);
            }
            if ((flags & 64) != 0) {
                released_by = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }
    }

    public static class TL_starGift_layer206 extends TL_starGift {
        public static final int constructor = 0xc62aca28;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = limited ? flags | 1 : flags &~ 1;
            flags = sold_out ? flags | 2 : flags &~ 2;
            flags = birthday ? flags | 4 : flags &~ 4;
            flags = can_upgrade ? flags | 8 : flags &~ 8;
            stream.writeInt32(flags);
            stream.writeInt64(id);
            sticker.serializeToStream(stream);
            stream.writeInt64(stars);
            if ((flags & 1) != 0) {
                stream.writeInt32(availability_remains);
                stream.writeInt32(availability_total);
            }
            if ((flags & 16) != 0) {
                stream.writeInt64(availability_resale);
            }
            stream.writeInt64(convert_stars);
            if ((flags & 2) != 0) {
                stream.writeInt32(first_sale_date);
                stream.writeInt32(last_sale_date);
            }
            if ((flags & 8) != 0) {
                stream.writeInt64(upgrade_stars);
            }
            if ((flags & 16) != 0) {
                stream.writeInt64(resell_min_stars);
            }
            if ((flags & 32) != 0) {
                stream.writeString(title);
            }
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            limited = (flags & 1) != 0;
            sold_out = (flags & 2) != 0;
            birthday = (flags & 4) != 0;
            can_upgrade = (flags & 8) != 0;
            id = stream.readInt64(exception);
            sticker = TLRPC.Document.TLdeserialize(stream, stream.readInt32(exception), exception);
            stars = stream.readInt64(exception);
            if ((flags & 1) != 0) {
                availability_remains = stream.readInt32(exception);
                availability_total = stream.readInt32(exception);
            }
            if ((flags & 16) != 0) {
                availability_resale = stream.readInt64(exception);
            }
            convert_stars = stream.readInt64(exception);
            if ((flags & 2) != 0) {
                first_sale_date = stream.readInt32(exception);
                last_sale_date = stream.readInt32(exception);
            }
            if ((flags & 8) != 0) {
                upgrade_stars = stream.readInt64(exception);
            }
            if ((flags & 16) != 0) {
                resell_min_stars = stream.readInt64(exception);
            }
            if ((flags & 32) != 0) {
                title = stream.readString(exception);
            }
        }
    }

    public static class TL_starGift_layer202 extends TL_starGift {
        public static final int constructor = 0x2cc73c8;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = limited ? flags | 1 : flags &~ 1;
            flags = sold_out ? flags | 2 : flags &~ 2;
            flags = birthday ? flags | 4 : flags &~ 4;
            flags = can_upgrade ? flags | 8 : flags &~ 8;
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
            if ((flags & 8) != 0) {
                stream.writeInt64(upgrade_stars);
            }
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            limited = (flags & 1) != 0;
            sold_out = (flags & 2) != 0;
            birthday = (flags & 4) != 0;
            can_upgrade = (flags & 8) != 0;
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
            if ((flags & 8) != 0) {
                upgrade_stars = stream.readInt64(exception);
            }
        }
    }

    public static class TL_starGift_layer195 extends TL_starGift {
        public static final int constructor = 0x49c577cd;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
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
        public void readParams(InputSerializedData stream, boolean exception) {
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
        public void serializeToStream(OutputSerializedData stream) {
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
        public void readParams(InputSerializedData stream, boolean exception) {
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
        public static StarGifts TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            StarGifts result = null;
            switch (constructor) {
                case TL_starGifts.constructor:
                    result = new TL_starGifts();
                    break;
                case TL_starGiftsNotModified.constructor:
                    result = new TL_starGiftsNotModified();
                    break;
            }
            return TLdeserialize(StarGifts.class, result, stream, constructor, exception);
        }
    }
    public static class TL_starGifts extends StarGifts {
        public static final int constructor = 0x2ed82995;

        public int hash;
        public ArrayList<StarGift> gifts = new ArrayList<>();
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(hash);
            Vector.serialize(stream, gifts);
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            hash = stream.readInt32(exception);
            gifts = Vector.deserialize(stream, StarGift::TLdeserialize, exception);
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }
    }
    public static class TL_starGiftsNotModified extends StarGifts {
        public static final int constructor = 0xa388a368;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {}
    }

    public static class getStarGifts extends TLObject {
        public static final int constructor = 0xc4563590;

        public int hash;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return StarGifts.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(hash);
        }
    }

    public static class getSavedStarGifts extends TLMethod<TL_payments_savedStarGifts> {
        public static final int constructor = 0xa319e569;

        public int flags;
        public boolean exclude_unsaved;
        public boolean exclude_saved;
        public boolean exclude_unlimited;
        public boolean exclude_upgradable;
        public boolean exclude_unupgradable;
        public boolean exclude_unique;
        public boolean sort_by_value;
        public boolean peer_color_available;
        public int collection_id;
        public TLRPC.InputPeer peer;
        public String offset;
        public int limit;

        @Override
        public TL_payments_savedStarGifts deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TL_payments_savedStarGifts.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = exclude_unsaved ? flags | 1 : flags &~ 1;
            flags = exclude_saved ? flags | 2 : flags &~ 2;
            flags = exclude_unlimited ? flags | 4 : flags &~ 4;
            flags = exclude_unique ? flags | 16 : flags &~ 16;
            flags = sort_by_value ? flags | 32 : flags &~ 32;
            flags = setFlag(flags, FLAG_7, exclude_upgradable);
            flags = setFlag(flags, FLAG_8, exclude_unupgradable);
            flags = setFlag(flags, FLAG_9, peer_color_available);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            if (hasFlag(flags, FLAG_6)) {
                stream.writeInt32(collection_id);
            }
            stream.writeString(offset);
            stream.writeInt32(limit);
        }
    }

    public static class getSavedStarGift extends TLObject {
        public static final int constructor = 0xb455a106;

        public ArrayList<InputSavedStarGift> stargift = new ArrayList<>();

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_payments_savedStarGifts.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, stargift);
        }
    }

    public static class saveStarGift extends TLObject {
        public static final int constructor = 0x2a2a697c;

        public int flags;
        public boolean unsave;
        public InputSavedStarGift stargift;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = unsave ? flags | 1 : flags &~ 1;
            stream.writeInt32(flags);
            stargift.serializeToStream(stream);
        }
    }

    public static class convertStarGift extends TLObject {
        public static final int constructor = 0x74bf076b;

        public InputSavedStarGift stargift;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stargift.serializeToStream(stream);
        }
    }

    public static class upgradeStarGift extends TLObject {
        public static final int constructor = 0xaed6e4f5;

        public int flags;
        public boolean keep_original_details;
        public InputSavedStarGift stargift;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = keep_original_details ? flags | 1 : flags &~ 1;
            stream.writeInt32(flags);
            stargift.serializeToStream(stream);
        }
    }

    public static class transferStarGift extends TLObject {
        public static final int constructor = 0x7f18176a;

        public InputSavedStarGift stargift;
        public TLRPC.InputPeer to_id;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stargift.serializeToStream(stream);
            to_id.serializeToStream(stream);
        }
    }

    public static class StarGiftUpgradePrice extends TLObject {
        public static final int constructor = 0x99ea331d;

        public int date;
        public long upgrade_stars;

        public static StarGiftUpgradePrice TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final StarGiftUpgradePrice result = StarGiftUpgradePrice.constructor != constructor ? null : new StarGiftUpgradePrice();
            return TLdeserialize(StarGiftUpgradePrice.class, result, stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(date);
            stream.writeInt64(upgrade_stars);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            date = stream.readInt32(exception);
            upgrade_stars = stream.readInt64(exception);
        }
    }

    public static class starGiftUpgradePreview extends TLObject {
        public static final int constructor = 0x3de1dfed;

        public ArrayList<StarGiftAttribute> sample_attributes = new ArrayList<>();
        public ArrayList<StarGiftUpgradePrice> prices = new ArrayList<>();
        public ArrayList<StarGiftUpgradePrice> next_prices = new ArrayList<>();

        public static starGiftUpgradePreview TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final starGiftUpgradePreview result = starGiftUpgradePreview.constructor != constructor ? null : new starGiftUpgradePreview();
            return TLdeserialize(starGiftUpgradePreview.class, result, stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, sample_attributes);
            Vector.serialize(stream, prices);
            Vector.serialize(stream, next_prices);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            sample_attributes = Vector.deserialize(stream, StarGiftAttribute::TLdeserialize, exception);
            prices = Vector.deserialize(stream, StarGiftUpgradePrice::TLdeserialize, exception);
            next_prices = Vector.deserialize(stream, StarGiftUpgradePrice::TLdeserialize, exception);
        }
    }

    public static class getStarGiftUpgradePreview extends TLObject {
        public static final int constructor = 0x9c9abcb1;

        public long gift_id;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return starGiftUpgradePreview.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(gift_id);
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

        public static TL_starsTopupOption TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final TL_starsTopupOption result = TL_starsTopupOption.constructor != constructor ? null : new TL_starsTopupOption();
            return TLdeserialize(TL_starsTopupOption.class, result, stream, constructor, exception);
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            extended = (flags & 2) != 0;
            stars = stream.readInt64(exception);
            if ((flags & 1) != 0) {
                store_product = stream.readString(exception);
            }
            currency = stream.readString(exception);
            amount = stream.readInt64(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
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

        public static TL_starsGiftOption TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final TL_starsGiftOption result = TL_starsGiftOption.constructor != constructor ? null : new TL_starsGiftOption();
            return TLdeserialize(TL_starsGiftOption.class, result, stream, constructor, exception);
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            extended = (flags & 2) != 0;
            stars = stream.readInt64(exception);
            if ((flags & 1) != 0) {
                store_product = stream.readString(exception);
            }
            currency = stream.readString(exception);
            amount = stream.readInt64(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
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

        public static TL_starsGiveawayWinnersOption TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final TL_starsGiveawayWinnersOption result = TL_starsGiveawayWinnersOption.constructor != constructor ? null : new TL_starsGiveawayWinnersOption();
            return TLdeserialize(TL_starsGiveawayWinnersOption.class, result, stream, constructor, exception);
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            isDefault = (flags & 1) != 0;
            users = stream.readInt32(exception);
            per_user_stars = stream.readInt64(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
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

        public static TL_starsGiveawayOption TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final TL_starsGiveawayOption result = TL_starsGiveawayOption.constructor != constructor ? null : new TL_starsGiveawayOption();
            return TLdeserialize(TL_starsGiveawayOption.class, result, stream, constructor, exception);
        }

        public void readParams(InputSerializedData stream, boolean exception) {
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
            winners = Vector.deserialize(stream, TL_starsGiveawayWinnersOption::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
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
            Vector.serialize(stream, winners);
        }
    }

    public static class StarsTransactionPeer extends TLObject {

        public TLRPC.Peer peer;

        public static StarsTransactionPeer TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
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
            return TLdeserialize(StarsTransactionPeer.class, result, stream, constructor, exception);
        }
    }

    public static class TL_starsTransactionPeer extends StarsTransactionPeer {
        public static final int constructor = 0xd80da15d;

        public void readParams(InputSerializedData stream, boolean exception) {
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
        }
    }

    public static class TL_starsTransactionPeerAppStore extends StarsTransactionPeer {
        public static final int constructor = 0xb457b375;

        public void readParams(InputSerializedData stream, boolean exception) {}

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_starsTransactionPeerPlayMarket extends StarsTransactionPeer {
        public static final int constructor = 0x7b560a0b;

        public void readParams(InputSerializedData stream, boolean exception) {}

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_starsTransactionPeerFragment extends StarsTransactionPeer {
        public static final int constructor = 0xe92fd902;

        public void readParams(InputSerializedData stream, boolean exception) {}

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_starsTransactionPeerPremiumBot extends StarsTransactionPeer {
        public static final int constructor = 0x250dbaf8;

        public void readParams(InputSerializedData stream, boolean exception) {}

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_starsTransactionPeerUnsupported extends StarsTransactionPeer {
        public static final int constructor = 0x95f2bfe4;

        public void readParams(InputSerializedData stream, boolean exception) {}

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_starsTransactionPeerAds extends StarsTransactionPeer {
        public static final int constructor = 0x60682812;

        public void readParams(InputSerializedData stream, boolean exception) {}

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_starsTransactionPeerAPI extends StarsTransactionPeer {
        public static final int constructor = 0xf9677aad;

        public void readParams(InputSerializedData stream, boolean exception) {}

        public void serializeToStream(OutputSerializedData stream) {
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
        public boolean stargift_upgrade;
        public boolean subscription;
        public boolean floodskip;
        public boolean paid_message;
        public boolean premium_gift;
        public boolean business_transfer;
        public boolean stargift_resale;
        public boolean posts_search;
        public boolean stargift_prepaid_upgrade;
        public boolean stargift_drop_original_details;
        public boolean phonegroup_message;
        public boolean stargift_auction_bid;
        public String id;
        public StarsAmount amount = StarsAmount.ofStars(0);
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
        public int paid_messages;
        public int premium_gift_months;
        public int ads_proceeds_from_date;
        public int ads_proceeds_to_date;

        public TLRPC.Peer sent_by; //custom
        public TLRPC.Peer received_by; //custom

        public static StarsTransaction TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
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
                case TL_starsTransaction_layer199.constructor:
                    result = new TL_starsTransaction_layer199();
                    break;
                case TL_starsTransaction_layer199_2.constructor:
                    result = new TL_starsTransaction_layer199_2();
                    break;
                case TL_starsTransaction_layer205.constructor:
                    result = new TL_starsTransaction_layer205();
                    break;
                case TL_starsTransaction.constructor:
                    result = new TL_starsTransaction();
                    break;
            }
            return TLdeserialize(StarsTransaction.class, result, stream, constructor, exception);
        }

    }

    public static class TL_starsTransaction_layer181 extends StarsTransaction {
        public static final int constructor = 0xcc7079b2;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            refund = (flags & 8) != 0;
            id = stream.readString(exception);
            amount = StarsAmount.ofStars(stream.readInt64(exception));
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

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = refund ? flags | 8 : flags &~ 8;
            stream.writeInt32(flags);
            stream.writeString(id);
            stream.writeInt64(amount.amount);
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

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            refund = (flags & 8) != 0;
            pending = (flags & 16) != 0;
            failed = (flags & 64) != 0;
            id = stream.readString(exception);
            amount = StarsAmount.ofStars(stream.readInt64(exception));
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

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = refund ? flags | 8 : flags &~ 8;
            flags = pending ? flags | 16 : flags &~ 16;
            flags = failed ? flags | 64 : flags &~ 64;
            stream.writeInt32(flags);
            stream.writeString(id);
            stream.writeInt64(amount.amount);
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

    public static abstract class StarsAmount extends TLObject {
        public long amount;
        public int nanos;

        public abstract AmountUtils.Currency getCurrency();

        public static StarsAmount TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            StarsAmount result = null;
            switch (constructor) {
                case TL_starsAmount.constructor:
                    result = new TL_starsAmount();
                    break;
                case TL_starsTonAmount.constructor:
                    result = new TL_starsTonAmount();
                    break;
            }
            return TLdeserialize(StarsAmount.class, result, stream, constructor, exception);
        }

        public static StarsAmount ofStars(long stars) {
            TL_starsAmount starsAmount = new TL_starsAmount();
            starsAmount.amount = stars;
            return starsAmount;
        }

        public boolean equals(TL_stars.StarsAmount amount) {
            if (amount == null) return false;
            return this.amount == amount.amount && this.nanos == amount.nanos;
        }

        public double toDouble() {
            return amount + (double) nanos / 1_000_000_000L;
        }

        public boolean positive() {
            return amount == 0 ? nanos > 0 : amount > 0;
        }

        public boolean negative() {
            return amount == 0 ? nanos < 0 : amount < 0;
        }
    }

    public static class TL_starsTonAmount extends StarsAmount {
        public static final int constructor = 0x74aee3e0;

        @Override
        public AmountUtils.Currency getCurrency() {
            return AmountUtils.Currency.TON;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            amount = stream.readInt64(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(amount);
        }
    }

    public static class TL_starsAmount extends StarsAmount {
        public static final int constructor = 0xbbb6b4a3;

        @Override
        public AmountUtils.Currency getCurrency() {
            return AmountUtils.Currency.STARS;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            amount = stream.readInt64(exception);
            nanos = stream.readInt32(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(amount);
            stream.writeInt32(nanos);
        }
    }

    public static class TL_starsTransaction extends StarsTransaction {
        public static final int constructor = 0x13659eb0;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            refund = (flags & 8) != 0;
            pending = (flags & 16) != 0;
            failed = (flags & 64) != 0;
            gift = (flags & 1024) != 0;
            reaction = (flags & 2048) != 0;
            subscription = (flags & 4096) != 0;
            floodskip = (flags & 32768) != 0;
            stargift_upgrade = (flags & 262144) != 0;
            paid_message = (flags & 524288) != 0;
            premium_gift = (flags & 1048576) != 0;
            business_transfer = (flags & 2097152) != 0;
            stargift_resale = (flags & 4194304) != 0;
            posts_search = (flags & 16777216) != 0;
            stargift_prepaid_upgrade = (flags & 33554432) != 0;
            stargift_drop_original_details = (flags & 67108864) != 0;
            phonegroup_message = hasFlag(flags, FLAG_27);
            stargift_auction_bid = hasFlag(flags, FLAG_28);
            id = stream.readString(exception);
            amount = StarsAmount.TLdeserialize(stream, stream.readInt32(exception), exception);
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
                extended_media = Vector.deserialize(stream, TLRPC.MessageMedia::TLdeserialize, exception);
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
            if ((flags & 524288) != 0) {
                paid_messages = stream.readInt32(exception);
            }
            if ((flags & 1048576) != 0) {
                premium_gift_months = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_23)) {
                ads_proceeds_from_date = stream.readInt32(exception);
                ads_proceeds_to_date = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = refund ? flags | 8 : flags &~ 8;
            flags = pending ? flags | 16 : flags &~ 16;
            flags = failed ? flags | 64 : flags &~ 64;
            flags = gift ? flags | 1024 : flags &~ 1024;
            flags = reaction ? flags | 2048 : flags &~ 2048;
            flags = subscription ? flags | 4096 : flags &~ 4096;
            flags = floodskip ? flags | 32768 : flags &~ 32768;
            flags = stargift_upgrade ? flags | 262144 : flags &~ 262144;
            flags = paid_message ? flags | 524288 : flags &~ 524288;
            flags = premium_gift ? flags | 1048576 : flags &~ 1048576;
            flags = business_transfer ? flags | 2097152 : flags &~ 2097152;
            flags = stargift_resale ? flags | 4194304 : flags &~ 4194304;
            flags = posts_search ? flags | 16777216 : flags &~ 16777216;
            flags = stargift_prepaid_upgrade ? flags | 33554432 : flags &~ 33554432;
            flags = stargift_drop_original_details ? flags | 67108864 : flags &~ 67108864;
            flags = setFlag(flags, FLAG_27, phonegroup_message);
            flags = setFlag(flags, FLAG_28, stargift_auction_bid);
            stream.writeInt32(flags);
            stream.writeString(id);
            amount.serializeToStream(stream);
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
                Vector.serialize(stream, extended_media);
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
            if ((flags & 524288) != 0) {
                stream.writeInt32(paid_messages);
            }
            if ((flags & 1048576) != 0) {
                stream.writeInt32(premium_gift_months);
            }
            if (hasFlag(flags, FLAG_23)) {
                stream.writeInt32(ads_proceeds_from_date);
                stream.writeInt32(ads_proceeds_to_date);
            }
        }
    }

    public static class TL_starsTransaction_layer205 extends TL_starsTransaction {
        public static final int constructor = 0xa39fd94a;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            refund = (flags & 8) != 0;
            pending = (flags & 16) != 0;
            failed = (flags & 64) != 0;
            gift = (flags & 1024) != 0;
            reaction = (flags & 2048) != 0;
            subscription = (flags & 4096) != 0;
            floodskip = (flags & 32768) != 0;
            stargift_upgrade = (flags & 262144) != 0;
            paid_message = (flags & 524288) != 0;
            premium_gift = (flags & 1048576) != 0;
            stargift_resale = (flags & 4194304) != 0;
            id = stream.readString(exception);
            amount = StarsAmount.TLdeserialize(stream, stream.readInt32(exception), exception);
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
                extended_media = Vector.deserialize(stream, TLRPC.MessageMedia::TLdeserialize, exception);
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
            if ((flags & 524288) != 0) {
                paid_messages = stream.readInt32(exception);
            }
            if ((flags & 1048576) != 0) {
                premium_gift_months = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = refund ? flags | 8 : flags &~ 8;
            flags = pending ? flags | 16 : flags &~ 16;
            flags = failed ? flags | 64 : flags &~ 64;
            flags = gift ? flags | 1024 : flags &~ 1024;
            flags = reaction ? flags | 2048 : flags &~ 2048;
            flags = subscription ? flags | 4096 : flags &~ 4096;
            flags = floodskip ? flags | 32768 : flags &~ 32768;
            flags = stargift_upgrade ? flags | 262144 : flags &~ 262144;
            flags = paid_message ? flags | 524288 : flags &~ 524288;
            flags = premium_gift ? flags | 1048576 : flags &~ 1048576;
            flags = stargift_resale ? flags | 4194304 : flags &~ 4194304;
            stream.writeInt32(flags);
            stream.writeString(id);
            amount.serializeToStream(stream);
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
                Vector.serialize(stream, extended_media);
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
            if ((flags & 524288) != 0) {
                stream.writeInt32(paid_messages);
            }
            if ((flags & 1048576) != 0) {
                stream.writeInt32(premium_gift_months);
            }
        }
    }

    public static class TL_starsTransaction_layer199_2 extends TL_starsTransaction {
        public static final int constructor = 0xecd50924;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            refund = (flags & 8) != 0;
            pending = (flags & 16) != 0;
            failed = (flags & 64) != 0;
            gift = (flags & 1024) != 0;
            reaction = (flags & 2048) != 0;
            subscription = (flags & 4096) != 0;
            floodskip = (flags & 32768) != 0;
            stargift_upgrade = (flags & 262144) != 0;
            paid_message = (flags & 524288) != 0;
            premium_gift = (flags & 1048576) != 0;
            id = stream.readString(exception);
            amount = StarsAmount.TLdeserialize(stream, stream.readInt32(exception), exception);
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
                extended_media = Vector.deserialize(stream, TLRPC.MessageMedia::TLdeserialize, exception);
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
            if ((flags & 524288) != 0) {
                paid_messages = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = refund ? flags | 8 : flags &~ 8;
            flags = pending ? flags | 16 : flags &~ 16;
            flags = failed ? flags | 64 : flags &~ 64;
            flags = gift ? flags | 1024 : flags &~ 1024;
            flags = reaction ? flags | 2048 : flags &~ 2048;
            flags = subscription ? flags | 4096 : flags &~ 4096;
            flags = floodskip ? flags | 32768 : flags &~ 32768;
            flags = stargift_upgrade ? flags | 262144 : flags &~ 262144;
            flags = paid_message ? flags | 524288 : flags &~ 524288;
            flags = premium_gift ? flags | 1048576 : flags &~ 1048576;
            stream.writeInt32(flags);
            stream.writeString(id);
            amount.serializeToStream(stream);
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
                Vector.serialize(stream, extended_media);
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
            if ((flags & 524288) != 0) {
                stream.writeInt32(paid_messages);
            }
        }
    }

    public static class TL_starsTransaction_layer199 extends TL_starsTransaction {
        public static final int constructor = 0x64dfc926;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            refund = (flags & 8) != 0;
            pending = (flags & 16) != 0;
            failed = (flags & 64) != 0;
            gift = (flags & 1024) != 0;
            reaction = (flags & 2048) != 0;
            subscription = (flags & 4096) != 0;
            floodskip = (flags & 32768) != 0;
            stargift_upgrade = (flags & 262144) != 0;
            paid_message = (flags & 524288) != 0;
            id = stream.readString(exception);
            amount = StarsAmount.TLdeserialize(stream, stream.readInt32(exception), exception);
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
                extended_media = Vector.deserialize(stream, TLRPC.MessageMedia::TLdeserialize, exception);
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

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = refund ? flags | 8 : flags &~ 8;
            flags = pending ? flags | 16 : flags &~ 16;
            flags = failed ? flags | 64 : flags &~ 64;
            flags = gift ? flags | 1024 : flags &~ 1024;
            flags = reaction ? flags | 2048 : flags &~ 2048;
            flags = subscription ? flags | 4096 : flags &~ 4096;
            flags = floodskip ? flags | 32768 : flags &~ 32768;
            flags = stargift_upgrade ? flags | 262144 : flags &~ 262144;
            flags = paid_message ? flags | 524288 : flags &~ 524288;
            stream.writeInt32(flags);
            stream.writeString(id);
            amount.serializeToStream(stream);
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
                Vector.serialize(stream, extended_media);
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

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            refund = (flags & 8) != 0;
            pending = (flags & 16) != 0;
            failed = (flags & 64) != 0;
            gift = (flags & 1024) != 0;
            reaction = (flags & 2048) != 0;
            subscription = (flags & 4096) != 0;
            floodskip = (flags & 32768) != 0;
            id = stream.readString(exception);
            amount = StarsAmount.ofStars(stream.readInt64(exception));
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
                extended_media = Vector.deserialize(stream, TLRPC.MessageMedia::TLdeserialize, exception);
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

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = refund ? flags | 8 : flags &~ 8;
            flags = pending ? flags | 16 : flags &~ 16;
            flags = failed ? flags | 64 : flags &~ 64;
            flags = gift ? flags | 1024 : flags &~ 1024;
            flags = reaction ? flags | 2048 : flags &~ 2048;
            flags = subscription ? flags | 4096 : flags &~ 4096;
            flags = floodskip ? flags | 32768 : flags &~ 32768;
            stream.writeInt32(flags);
            stream.writeString(id);
            stream.writeInt64(amount.amount);
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
                Vector.serialize(stream, extended_media);
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

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            refund = (flags & 8) != 0;
            pending = (flags & 16) != 0;
            failed = (flags & 64) != 0;
            gift = (flags & 1024) != 0;
            reaction = (flags & 2048) != 0;
            subscription = (flags & 4096) != 0;
            id = stream.readString(exception);
            amount = StarsAmount.ofStars(stream.readInt64(exception));
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
                extended_media = Vector.deserialize(stream, TLRPC.MessageMedia::TLdeserialize, exception);
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

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = refund ? flags | 8 : flags &~ 8;
            flags = pending ? flags | 16 : flags &~ 16;
            flags = failed ? flags | 64 : flags &~ 64;
            flags = gift ? flags | 1024 : flags &~ 1024;
            flags = reaction ? flags | 2048 : flags &~ 2048;
            flags = subscription ? flags | 4096 : flags &~ 4096;
            stream.writeInt32(flags);
            stream.writeString(id);
            stream.writeInt64(amount.amount);
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
                Vector.serialize(stream, extended_media);
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

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            refund = (flags & 8) != 0;
            pending = (flags & 16) != 0;
            failed = (flags & 64) != 0;
            gift = (flags & 1024) != 0;
            reaction = (flags & 2048) != 0;
            subscription = (flags & 4096) != 0;
            id = stream.readString(exception);
            amount = StarsAmount.ofStars(stream.readInt64(exception));
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
                extended_media = Vector.deserialize(stream, TLRPC.MessageMedia::TLdeserialize, exception);
            }
            if ((flags & 4096) != 0) {
                subscription_period = stream.readInt32(exception);
            }
            if ((flags & 8192) != 0) {
                giveaway_post_id = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = refund ? flags | 8 : flags &~ 8;
            flags = pending ? flags | 16 : flags &~ 16;
            flags = failed ? flags | 64 : flags &~ 64;
            flags = gift ? flags | 1024 : flags &~ 1024;
            flags = reaction ? flags | 2048 : flags &~ 2048;
            flags = subscription ? flags | 4096 : flags &~ 4096;
            stream.writeInt32(flags);
            stream.writeString(id);
            stream.writeInt64(amount.amount);
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
                Vector.serialize(stream, extended_media);
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

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            refund = (flags & 8) != 0;
            pending = (flags & 16) != 0;
            failed = (flags & 64) != 0;
            gift = (flags & 1024) != 0;
            reaction = (flags & 2048) != 0;
            subscription = (flags & 4096) != 0;
            id = stream.readString(exception);
            amount = StarsAmount.ofStars(stream.readInt64(exception));
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
                extended_media = Vector.deserialize(stream, TLRPC.MessageMedia::TLdeserialize, exception);
            }
            if ((flags & 4096) != 0) {
                subscription_period = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = refund ? flags | 8 : flags &~ 8;
            flags = pending ? flags | 16 : flags &~ 16;
            flags = failed ? flags | 64 : flags &~ 64;
            flags = gift ? flags | 1024 : flags &~ 1024;
            flags = reaction ? flags | 2048 : flags &~ 2048;
            flags = subscription ? flags | 4096 : flags &~ 4096;
            stream.writeInt32(flags);
            stream.writeString(id);
            stream.writeInt64(amount.amount);
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
                Vector.serialize(stream, extended_media);
            }
            if ((flags & 4096) != 0) {
                stream.writeInt32(subscription_period);
            }
        }
    }

    public static class TL_starsTransaction_layer185 extends TL_starsTransaction {
        public static final int constructor = 0x2db5418f;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            refund = (flags & 8) != 0;
            pending = (flags & 16) != 0;
            failed = (flags & 64) != 0;
            gift = (flags & 1024) != 0;
            id = stream.readString(exception);
            amount = StarsAmount.ofStars(stream.readInt64(exception));
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
                extended_media = Vector.deserialize(stream, TLRPC.MessageMedia::TLdeserialize, exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = refund ? flags | 8 : flags &~ 8;
            flags = pending ? flags | 16 : flags &~ 16;
            flags = failed ? flags | 64 : flags &~ 64;
            flags = gift ? flags | 1024 : flags &~ 1024;
            stream.writeInt32(flags);
            stream.writeString(id);
            stream.writeInt64(amount.amount);
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
                Vector.serialize(stream, extended_media);
            }
        }
    }

    public static class StarsStatus extends TLObject {

        public int flags;
        public StarsAmount balance = StarsAmount.ofStars(0);
        public ArrayList<StarsSubscription> subscriptions = new ArrayList<>();
        public String subscriptions_next_offset;
        public long subscriptions_missing_balance;
        public ArrayList<StarsTransaction> history = new ArrayList<>();
        public String next_offset;
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static StarsStatus TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            StarsStatus result = null;
            switch (constructor) {
                case TL_payments_starsStatus_layer194.constructor:
                    result = new TL_payments_starsStatus_layer194();
                    break;
                case TL_payments_starsStatus.constructor:
                    result = new TL_payments_starsStatus();
                    break;
            }
            return TLdeserialize(StarsStatus.class, result, stream, constructor, exception);
        }
    }

    public static class TL_payments_starsStatus extends StarsStatus {
        public static final int constructor = 0x6c9ce8ed;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            balance = StarsAmount.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 2) != 0) {
                subscriptions = Vector.deserialize(stream, StarsSubscription::TLdeserialize, exception);
            }
            if ((flags & 4) != 0) {
                subscriptions_next_offset = stream.readString(exception);
            }
            if ((flags & 16) != 0) {
                subscriptions_missing_balance = stream.readInt64(exception);
            }
            if ((flags & 8) != 0) {
                history = Vector.deserialize(stream, StarsTransaction::TLdeserialize, exception);
            }
            if ((flags & 1) != 0) {
                next_offset = stream.readString(exception);
            }
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            balance.serializeToStream(stream);
            if ((flags & 2) != 0) {
                Vector.serialize(stream, subscriptions);
            }
            if ((flags & 4) != 0) {
                stream.writeString(subscriptions_next_offset);
            }
            if ((flags & 16) != 0) {
                stream.writeInt64(subscriptions_missing_balance);
            }
            if ((flags & 8) != 0) {
                Vector.serialize(stream, history);
            }
            if ((flags & 1) != 0) {
                stream.writeString(next_offset);
            }
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);
        }
    }

    public static class TL_payments_starsStatus_layer194 extends TL_payments_starsStatus {
        public static final int constructor = 0xbbfa316c;


        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            balance = StarsAmount.ofStars(stream.readInt64(exception));
            if ((flags & 2) != 0) {
                subscriptions = Vector.deserialize(stream, StarsSubscription::TLdeserialize, exception);
            }
            if ((flags & 4) != 0) {
                subscriptions_next_offset = stream.readString(exception);
            }
            if ((flags & 16) != 0) {
                subscriptions_missing_balance = stream.readInt64(exception);
            }
            if ((flags & 8) != 0) {
                history = Vector.deserialize(stream, StarsTransaction::TLdeserialize, exception);
            }
            if ((flags & 1) != 0) {
                next_offset = stream.readString(exception);
            }
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt64(balance.amount);
            if ((flags & 2) != 0) {
                Vector.serialize(stream, subscriptions);
            }
            if ((flags & 4) != 0) {
                stream.writeString(subscriptions_next_offset);
            }
            if ((flags & 16) != 0) {
                stream.writeInt64(subscriptions_missing_balance);
            }
            if ((flags & 8) != 0) {
                Vector.serialize(stream, history);
            }
            if ((flags & 1) != 0) {
                stream.writeString(next_offset);
            }
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);
        }
    }

    public static class TL_payments_getStarsTopupOptions extends TLObject {
        public static final int constructor = 0xc00ec7d3;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return Vector.TLDeserialize(stream, constructor, exception, TL_starsTopupOption::TLdeserialize);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_payments_getStarsGiftOptions extends TLObject {
        public static final int constructor = 0xd3c96bc8;

        public int flags;
        public TLRPC.InputUser user_id;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return Vector.TLDeserialize(stream, constructor, exception, TL_starsGiftOption::TLdeserialize);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                user_id.serializeToStream(stream);
            }
        }
    }

    public static class TL_payments_getStarsGiveawayOptions extends TLObject {
        public static final int constructor = 0xbd1efd3e;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return Vector.TLDeserialize(stream, constructor, exception, TL_starsGiveawayOption::TLdeserialize);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_payments_getStarsStatus extends TLObject {
        public static final int constructor = 0x4ea9b3bf;

        public TLRPC.InputPeer peer;
        public boolean ton;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return StarsStatus.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);

            int flags = 0;
            flags = setFlag(flags, FLAG_0, ton);
            stream.writeInt32(flags);

            peer.serializeToStream(stream);
        }
    }

    public static class TL_payments_getStarsTransactions extends TLObject {
        public static final int constructor = 0x69da4557;

        public int flags;
        public boolean inbound;
        public boolean outbound;
        public boolean ascending;
        public boolean ton;
        public String subscription_id;

        public TLRPC.InputPeer peer;
        public String offset;
        public int limit = 50;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return StarsStatus.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, inbound);
            flags = setFlag(flags, FLAG_1, outbound);
            flags = setFlag(flags, FLAG_2, ascending);
            flags = setFlag(flags, FLAG_3, subscription_id != null);
            flags = setFlag(flags, FLAG_4, ton);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_3)) {
                stream.writeString(subscription_id);
            }
            peer.serializeToStream(stream);
            stream.writeString(offset);
            stream.writeInt32(limit);
        }
    }

    public static class TL_payments_sendStarsForm extends TLMethod<TLRPC.payments_PaymentResult> {
        public static final int constructor = 0x7998c914;

        public long form_id;
        public TLRPC.InputInvoice invoice;

        public TLRPC.payments_PaymentResult deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.payments_PaymentResult.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
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

        public static StarsSubscription TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
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
            return TLdeserialize(StarsSubscription.class, result, stream, constructor, exception);
        }

    }

    public static class TL_starsSubscription extends StarsSubscription {
        public static final int constructor = 0x2e6eab1a;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
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
        public void serializeToStream(OutputSerializedData stream) {
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
        public void readParams(InputSerializedData stream, boolean exception) {
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
        public void serializeToStream(OutputSerializedData stream) {
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
        public void readParams(InputSerializedData stream, boolean exception) {
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
        public void serializeToStream(OutputSerializedData stream) {
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

        public static TL_starsSubscriptionPricing TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final TL_starsSubscriptionPricing result = TL_starsSubscriptionPricing.constructor != constructor ? null : new TL_starsSubscriptionPricing();
            return TLdeserialize(TL_starsSubscriptionPricing.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            period = stream.readInt32(exception);
            amount = stream.readInt64(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
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

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return StarsStatus.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
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

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
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

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeString(subscription_id);
        }
    }

    public static class starGiftAttributeCounter extends TLObject {
        public static int constructor = 0x2eb1b658;

        public StarGiftAttributeId attribute;
        public int count;

        public static starGiftAttributeCounter TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final starGiftAttributeCounter result = starGiftAttributeCounter.constructor != constructor ? null : new starGiftAttributeCounter();
            return TLdeserialize(starGiftAttributeCounter.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            attribute = StarGiftAttributeId.TLdeserialize(stream, stream.readInt32(exception), exception);
            count = stream.readInt32(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            attribute.serializeToStream(stream);
            stream.writeInt32(count);
        }
    }

    public static class StarGiftAttributeId extends TLObject {
        public long document_id;
        public int backdrop_id;

        public static StarGiftAttributeId TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            StarGiftAttributeId result = null;
            switch (constructor) {
                case starGiftAttributeIdModel.constructor:
                    result = new starGiftAttributeIdModel();
                    break;
                case starGiftAttributeIdPattern.constructor:
                    result = new starGiftAttributeIdPattern();
                    break;
                case starGiftAttributeIdBackdrop.constructor:
                    result = new starGiftAttributeIdBackdrop();
                    break;
            }
            return TLdeserialize(StarGiftAttributeId.class, result, stream, constructor, exception);
        }

    }

    public static class starGiftAttributeIdModel extends StarGiftAttributeId {
        public static final int constructor = 0x48aaae3c;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            document_id = stream.readInt64(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(document_id);
        }
    }

    public static class starGiftAttributeIdPattern extends StarGiftAttributeId {
        public static final int constructor = 0x4a162433;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            document_id = stream.readInt64(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(document_id);
        }
    }

    public static class starGiftAttributeIdBackdrop extends StarGiftAttributeId {
        public static final int constructor = 0x1f01c757;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            backdrop_id = stream.readInt32(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(backdrop_id);
        }
    }

    public static class StarGiftAttribute extends TLObject {

        public String name;
        public int rarity_permille;

        public static StarGiftAttribute TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            StarGiftAttribute result = null;
            switch (constructor) {
                case starGiftAttributeModel.constructor:
                    result = new starGiftAttributeModel();
                    break;
                case starGiftAttributePattern.constructor:
                    result = new starGiftAttributePattern();
                    break;
                case starGiftAttributeBackdrop.constructor:
                    result = new starGiftAttributeBackdrop();
                    break;
                case starGiftAttributeBackdrop_layer202.constructor:
                    result = new starGiftAttributeBackdrop_layer202();
                    break;
                case starGiftAttributeOriginalDetails.constructor:
                    result = new starGiftAttributeOriginalDetails();
                    break;
                case starGiftAttributeOriginalDetails_layer197.constructor:
                    result = new starGiftAttributeOriginalDetails_layer197();
                    break;
            }
            return TLdeserialize(StarGiftAttribute.class, result, stream, constructor, exception);
        }
    }

    public static class starGiftAttributeModel extends StarGiftAttribute {
        public static final int constructor = 0x39d99013;

        public TLRPC.Document document;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(name);
            document.serializeToStream(stream);
            stream.writeInt32(rarity_permille);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            name = stream.readString(exception);
            document = TLRPC.Document.TLdeserialize(stream, stream.readInt32(exception), exception);
            rarity_permille = stream.readInt32(exception);
        }
    }
    public static class starGiftAttributePattern extends StarGiftAttribute {
        public static final int constructor = 0x13acff19;

        public TLRPC.Document document;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(name);
            document.serializeToStream(stream);
            stream.writeInt32(rarity_permille);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            name = stream.readString(exception);
            document = TLRPC.Document.TLdeserialize(stream, stream.readInt32(exception), exception);
            rarity_permille = stream.readInt32(exception);
        }
    }
    public static class starGiftAttributeBackdrop extends StarGiftAttribute {
        public static final int constructor = 0xd93d859c;

        public int backdrop_id;
        public int center_color;
        public int edge_color;
        public int pattern_color;
        public int text_color;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(name);
            stream.writeInt32(backdrop_id);
            stream.writeInt32(center_color);
            stream.writeInt32(edge_color);
            stream.writeInt32(pattern_color);
            stream.writeInt32(text_color);
            stream.writeInt32(rarity_permille);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            name = stream.readString(exception);
            backdrop_id = stream.readInt32(exception);
            center_color = stream.readInt32(exception);
            edge_color = stream.readInt32(exception);
            pattern_color = stream.readInt32(exception);
            text_color = stream.readInt32(exception);
            rarity_permille = stream.readInt32(exception);
        }
    }
    public static class starGiftAttributeBackdrop_layer202 extends starGiftAttributeBackdrop {
        public static final int constructor = 0x94271762;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(name);
            stream.writeInt32(center_color);
            stream.writeInt32(edge_color);
            stream.writeInt32(pattern_color);
            stream.writeInt32(text_color);
            stream.writeInt32(rarity_permille);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            name = stream.readString(exception);
            center_color = stream.readInt32(exception);
            edge_color = stream.readInt32(exception);
            pattern_color = stream.readInt32(exception);
            text_color = stream.readInt32(exception);
            rarity_permille = stream.readInt32(exception);
        }
    }
    public static class starGiftAttributeOriginalDetails extends StarGiftAttribute {
        public static final int constructor = 0xe0bff26c;

        public int flags;
        public TLRPC.Peer sender_id;
        public TLRPC.Peer recipient_id;
        public int date;
        public TLRPC.TL_textWithEntities message;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                sender_id.serializeToStream(stream);
            }
            recipient_id.serializeToStream(stream);
            stream.writeInt32(date);
            if ((flags & 2) != 0) {
                message.serializeToStream(stream);
            }
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                sender_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            recipient_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            date = stream.readInt32(exception);
            if ((flags & 2) != 0) {
                message = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }
    }
    public static class starGiftAttributeOriginalDetails_layer197 extends starGiftAttributeOriginalDetails {
        public static final int constructor = 0xc02c4f4b;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                stream.writeInt64(sender_id.user_id);
            }
            stream.writeInt64(recipient_id.user_id);
            stream.writeInt32(date);
            if ((flags & 2) != 0) {
                message.serializeToStream(stream);
            }
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                sender_id = new TLRPC.TL_peerUser();
                sender_id.user_id = stream.readInt64(exception);
            }
            recipient_id = new TLRPC.TL_peerUser();
            recipient_id.user_id = stream.readInt64(exception);
            date = stream.readInt32(exception);
            if ((flags & 2) != 0) {
                message = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }
    }

    public static final class TL_payments_uniqueStarGift extends TLObject {
        public static final int constructor = 0x416c56e8;

        public StarGift gift;
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static TL_payments_uniqueStarGift TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final TL_payments_uniqueStarGift result = TL_payments_uniqueStarGift.constructor != constructor ? null : new TL_payments_uniqueStarGift();
            return TLdeserialize(TL_payments_uniqueStarGift.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            gift = StarGift.TLdeserialize(stream, stream.readInt32(exception), exception);
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            gift.serializeToStream(stream);
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);
        }
    }

    public static final class getUniqueStarGift extends TLObject {
        public static final int constructor = 0xa1974d72;

        public String slug;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_payments_uniqueStarGift.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(slug);
        }
    }

    public static class SavedStarGift extends TLObject {
        public int flags;
        public boolean name_hidden;
        public boolean unsaved;
        public boolean refunded;
        public boolean can_upgrade;
        public boolean pinned_to_top;
        public boolean upgrade_separate;
        public TLRPC.Peer from_id;
        public int date;
        public StarGift gift;
        public TLRPC.TL_textWithEntities message;
        public int msg_id;
        public long saved_id;
        public long convert_stars;
        public long upgrade_stars;
        public int can_export_at;
        public long transfer_stars;
        public int can_transfer_at;
        public int can_resell_at;
        public ArrayList<Integer> collection_id = new ArrayList<>();
        public String prepaid_upgrade_hash;
        public long drop_original_details_stars;

        public static SavedStarGift TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            SavedStarGift result = null;
            switch (constructor) {
                case TL_savedStarGift.constructor:
                    result = new TL_savedStarGift();
                    break;
                case TL_savedStarGift_layer214.constructor:
                    result = new TL_savedStarGift_layer214();
                    break;
                case TL_savedStarGift_layer211.constructor:
                    result = new TL_savedStarGift_layer211();
                    break;
                case TL_savedStarGift_layer209.constructor:
                    result = new TL_savedStarGift_layer209();
                    break;
                case TL_savedStarGift_layer202.constructor:
                    result = new TL_savedStarGift_layer202();
                    break;
            }
            return TLdeserialize(SavedStarGift.class, result, stream, constructor, exception);
        }
    }

    public static class TL_savedStarGift extends SavedStarGift {
        public static final int constructor = 0x8983a452;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            name_hidden = (flags & 1) != 0;
            unsaved = (flags & 32) != 0;
            refunded = (flags & 512) != 0;
            can_upgrade = (flags & 1024) != 0;
            pinned_to_top = (flags & 4096) != 0;
            upgrade_separate = (flags & 131072) != 0;
            if ((flags & 2) != 0) {
                from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            date = stream.readInt32(exception);
            gift = StarGift.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 4) != 0) {
                message = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 8) != 0) {
                msg_id = stream.readInt32(exception);
            }
            if ((flags & 2048) != 0) {
                saved_id = stream.readInt64(exception);
            }
            if ((flags & 16) != 0) {
                convert_stars = stream.readInt64(exception);
            }
            if ((flags & 64) != 0) {
                upgrade_stars = stream.readInt64(exception);
            }
            if ((flags & 128) != 0) {
                can_export_at = stream.readInt32(exception);
            }
            if ((flags & 256) != 0) {
                transfer_stars = stream.readInt64(exception);
            }
            if ((flags & 8192) != 0) {
                can_transfer_at = stream.readInt32(exception);
            }
            if ((flags & 16384) != 0) {
                can_resell_at = stream.readInt32(exception);
            }
            if ((flags & 32768) != 0) {
                collection_id = Vector.deserializeInt(stream, exception);
            }
            if ((flags & 65536) != 0) {
                prepaid_upgrade_hash = stream.readString(exception);
            }
            if ((flags & 262144) != 0) {
                drop_original_details_stars = stream.readInt64(exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = name_hidden ? flags | 1 : flags &~ 1;
            flags = unsaved ? flags | 32 : flags &~ 32;
            flags = refunded ? flags | 512 : flags &~ 512;
            flags = can_upgrade ? flags | 1024 : flags &~ 1024;
            flags = pinned_to_top ? flags | 4096 : flags &~ 4096;
            flags = upgrade_separate ? flags | 131072 : flags &~ 131072;
            stream.writeInt32(flags);
            if ((flags & 2) != 0) {
                from_id.serializeToStream(stream);
            }
            stream.writeInt32(date);
            gift.serializeToStream(stream);
            if ((flags & 4) != 0) {
                message.serializeToStream(stream);
            }
            if ((flags & 8) != 0) {
                stream.writeInt32(msg_id);
            }
            if ((flags & 2048) != 0) {
                stream.writeInt64(saved_id);
            }
            if ((flags & 16) != 0) {
                stream.writeInt64(convert_stars);
            }
            if ((flags & 64) != 0) {
                stream.writeInt64(upgrade_stars);
            }
            if ((flags & 128) != 0) {
                stream.writeInt32(can_export_at);
            }
            if ((flags & 256) != 0) {
                stream.writeInt64(transfer_stars);
            }
            if ((flags & 8192) != 0) {
                stream.writeInt32(can_transfer_at);
            }
            if ((flags & 16384) != 0) {
                stream.writeInt32(can_resell_at);
            }
            if ((flags & 32768) != 0) {
                Vector.serializeInt(stream, collection_id);
            }
            if ((flags & 65536) != 0) {
                stream.writeString(prepaid_upgrade_hash);
            }
            if ((flags & 262144) != 0) {
                stream.writeInt64(drop_original_details_stars);
            }
        }
    }

    public static class TL_savedStarGift_layer214 extends TL_savedStarGift {
        public static final int constructor = 0x19a9b572;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            name_hidden = (flags & 1) != 0;
            unsaved = (flags & 32) != 0;
            refunded = (flags & 512) != 0;
            can_upgrade = (flags & 1024) != 0;
            pinned_to_top = (flags & 4096) != 0;
            upgrade_separate = (flags & 131072) != 0;
            if ((flags & 2) != 0) {
                from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            date = stream.readInt32(exception);
            gift = StarGift.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 4) != 0) {
                message = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 8) != 0) {
                msg_id = stream.readInt32(exception);
            }
            if ((flags & 2048) != 0) {
                saved_id = stream.readInt64(exception);
            }
            if ((flags & 16) != 0) {
                convert_stars = stream.readInt64(exception);
            }
            if ((flags & 64) != 0) {
                upgrade_stars = stream.readInt64(exception);
            }
            if ((flags & 128) != 0) {
                can_export_at = stream.readInt32(exception);
            }
            if ((flags & 256) != 0) {
                transfer_stars = stream.readInt64(exception);
            }
            if ((flags & 8192) != 0) {
                can_transfer_at = stream.readInt32(exception);
            }
            if ((flags & 16384) != 0) {
                can_resell_at = stream.readInt32(exception);
            }
            if ((flags & 32768) != 0) {
                collection_id = Vector.deserializeInt(stream, exception);
            }
            if ((flags & 65536) != 0) {
                prepaid_upgrade_hash = stream.readString(exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = name_hidden ? flags | 1 : flags &~ 1;
            flags = unsaved ? flags | 32 : flags &~ 32;
            flags = refunded ? flags | 512 : flags &~ 512;
            flags = can_upgrade ? flags | 1024 : flags &~ 1024;
            flags = pinned_to_top ? flags | 4096 : flags &~ 4096;
            flags = upgrade_separate ? flags | 131072 : flags &~ 131072;
            stream.writeInt32(flags);
            if ((flags & 2) != 0) {
                from_id.serializeToStream(stream);
            }
            stream.writeInt32(date);
            gift.serializeToStream(stream);
            if ((flags & 4) != 0) {
                message.serializeToStream(stream);
            }
            if ((flags & 8) != 0) {
                stream.writeInt32(msg_id);
            }
            if ((flags & 2048) != 0) {
                stream.writeInt64(saved_id);
            }
            if ((flags & 16) != 0) {
                stream.writeInt64(convert_stars);
            }
            if ((flags & 64) != 0) {
                stream.writeInt64(upgrade_stars);
            }
            if ((flags & 128) != 0) {
                stream.writeInt32(can_export_at);
            }
            if ((flags & 256) != 0) {
                stream.writeInt64(transfer_stars);
            }
            if ((flags & 8192) != 0) {
                stream.writeInt32(can_transfer_at);
            }
            if ((flags & 16384) != 0) {
                stream.writeInt32(can_resell_at);
            }
            if ((flags & 32768) != 0) {
                Vector.serializeInt(stream, collection_id);
            }
            if ((flags & 65536) != 0) {
                stream.writeString(prepaid_upgrade_hash);
            }
        }
    }

    public static class TL_savedStarGift_layer211 extends TL_savedStarGift {
        public static final int constructor = 0x1ea646df;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            name_hidden = (flags & 1) != 0;
            unsaved = (flags & 32) != 0;
            refunded = (flags & 512) != 0;
            can_upgrade = (flags & 1024) != 0;
            pinned_to_top = (flags & 4096) != 0;
            if ((flags & 2) != 0) {
                from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            date = stream.readInt32(exception);
            gift = StarGift.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 4) != 0) {
                message = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 8) != 0) {
                msg_id = stream.readInt32(exception);
            }
            if ((flags & 2048) != 0) {
                saved_id = stream.readInt64(exception);
            }
            if ((flags & 16) != 0) {
                convert_stars = stream.readInt64(exception);
            }
            if ((flags & 64) != 0) {
                upgrade_stars = stream.readInt64(exception);
            }
            if ((flags & 128) != 0) {
                can_export_at = stream.readInt32(exception);
            }
            if ((flags & 256) != 0) {
                transfer_stars = stream.readInt64(exception);
            }
            if ((flags & 8192) != 0) {
                can_transfer_at = stream.readInt32(exception);
            }
            if ((flags & 16384) != 0) {
                can_resell_at = stream.readInt32(exception);
            }
            if ((flags & 32768) != 0) {
                collection_id = Vector.deserializeInt(stream, exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = name_hidden ? flags | 1 : flags &~ 1;
            flags = unsaved ? flags | 32 : flags &~ 32;
            flags = refunded ? flags | 512 : flags &~ 512;
            flags = can_upgrade ? flags | 1024 : flags &~ 1024;
            flags = pinned_to_top ? flags | 4096 : flags &~ 4096;
            stream.writeInt32(flags);
            if ((flags & 2) != 0) {
                from_id.serializeToStream(stream);
            }
            stream.writeInt32(date);
            gift.serializeToStream(stream);
            if ((flags & 4) != 0) {
                message.serializeToStream(stream);
            }
            if ((flags & 8) != 0) {
                stream.writeInt32(msg_id);
            }
            if ((flags & 2048) != 0) {
                stream.writeInt64(saved_id);
            }
            if ((flags & 16) != 0) {
                stream.writeInt64(convert_stars);
            }
            if ((flags & 64) != 0) {
                stream.writeInt64(upgrade_stars);
            }
            if ((flags & 128) != 0) {
                stream.writeInt32(can_export_at);
            }
            if ((flags & 256) != 0) {
                stream.writeInt64(transfer_stars);
            }
            if ((flags & 8192) != 0) {
                stream.writeInt32(can_transfer_at);
            }
            if ((flags & 16384) != 0) {
                stream.writeInt32(can_resell_at);
            }
            if ((flags & 32768) != 0) {
                Vector.serializeInt(stream, collection_id);
            }
        }
    }

    public static class TL_savedStarGift_layer209 extends TL_savedStarGift {
        public static final int constructor = 0xdfda0499;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            name_hidden = (flags & 1) != 0;
            unsaved = (flags & 32) != 0;
            refunded = (flags & 512) != 0;
            can_upgrade = (flags & 1024) != 0;
            pinned_to_top = (flags & 4096) != 0;
            if ((flags & 2) != 0) {
                from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            date = stream.readInt32(exception);
            gift = StarGift.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 4) != 0) {
                message = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 8) != 0) {
                msg_id = stream.readInt32(exception);
            }
            if ((flags & 2048) != 0) {
                saved_id = stream.readInt64(exception);
            }
            if ((flags & 16) != 0) {
                convert_stars = stream.readInt64(exception);
            }
            if ((flags & 64) != 0) {
                upgrade_stars = stream.readInt64(exception);
            }
            if ((flags & 128) != 0) {
                can_export_at = stream.readInt32(exception);
            }
            if ((flags & 256) != 0) {
                transfer_stars = stream.readInt64(exception);
            }
            if ((flags & 8192) != 0) {
                can_transfer_at = stream.readInt32(exception);
            }
            if ((flags & 16384) != 0) {
                can_resell_at = stream.readInt32(exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = name_hidden ? flags | 1 : flags &~ 1;
            flags = unsaved ? flags | 32 : flags &~ 32;
            flags = refunded ? flags | 512 : flags &~ 512;
            flags = can_upgrade ? flags | 1024 : flags &~ 1024;
            flags = pinned_to_top ? flags | 4096 : flags &~ 4096;
            stream.writeInt32(flags);
            if ((flags & 2) != 0) {
                from_id.serializeToStream(stream);
            }
            stream.writeInt32(date);
            gift.serializeToStream(stream);
            if ((flags & 4) != 0) {
                message.serializeToStream(stream);
            }
            if ((flags & 8) != 0) {
                stream.writeInt32(msg_id);
            }
            if ((flags & 2048) != 0) {
                stream.writeInt64(saved_id);
            }
            if ((flags & 16) != 0) {
                stream.writeInt64(convert_stars);
            }
            if ((flags & 64) != 0) {
                stream.writeInt64(upgrade_stars);
            }
            if ((flags & 128) != 0) {
                stream.writeInt32(can_export_at);
            }
            if ((flags & 256) != 0) {
                stream.writeInt64(transfer_stars);
            }
            if ((flags & 8192) != 0) {
                stream.writeInt32(can_transfer_at);
            }
            if ((flags & 16384) != 0) {
                stream.writeInt32(can_resell_at);
            }
        }
    }

    public static class TL_savedStarGift_layer202 extends TL_savedStarGift {
        public static final int constructor = 0x6056dba5;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            name_hidden = (flags & 1) != 0;
            unsaved = (flags & 32) != 0;
            refunded = (flags & 512) != 0;
            can_upgrade = (flags & 1024) != 0;
            pinned_to_top = (flags & 4096) != 0;
            if ((flags & 2) != 0) {
                from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            date = stream.readInt32(exception);
            gift = StarGift.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 4) != 0) {
                message = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 8) != 0) {
                msg_id = stream.readInt32(exception);
            }
            if ((flags & 2048) != 0) {
                saved_id = stream.readInt64(exception);
            }
            if ((flags & 16) != 0) {
                convert_stars = stream.readInt64(exception);
            }
            if ((flags & 64) != 0) {
                upgrade_stars = stream.readInt64(exception);
            }
            if ((flags & 128) != 0) {
                can_export_at = stream.readInt32(exception);
            }
            if ((flags & 256) != 0) {
                transfer_stars = stream.readInt64(exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = name_hidden ? flags | 1 : flags &~ 1;
            flags = unsaved ? flags | 32 : flags &~ 32;
            flags = refunded ? flags | 512 : flags &~ 512;
            flags = can_upgrade ? flags | 1024 : flags &~ 1024;
            flags = pinned_to_top ? flags | 4096 : flags &~ 4096;
            stream.writeInt32(flags);
            if ((flags & 2) != 0) {
                from_id.serializeToStream(stream);
            }
            stream.writeInt32(date);
            gift.serializeToStream(stream);
            if ((flags & 4) != 0) {
                message.serializeToStream(stream);
            }
            if ((flags & 8) != 0) {
                stream.writeInt32(msg_id);
            }
            if ((flags & 2048) != 0) {
                stream.writeInt64(saved_id);
            }
            if ((flags & 16) != 0) {
                stream.writeInt64(convert_stars);
            }
            if ((flags & 64) != 0) {
                stream.writeInt64(upgrade_stars);
            }
            if ((flags & 128) != 0) {
                stream.writeInt32(can_export_at);
            }
            if ((flags & 256) != 0) {
                stream.writeInt64(transfer_stars);
            }
        }
    }

    public static final class TL_payments_savedStarGifts extends TLObject {
        public static final int constructor = 0x95f389b1;

        public int flags;
        public int count;
        public boolean chat_notifications_enabled;
        public ArrayList<SavedStarGift> gifts = new ArrayList<>();
        public String next_offset;
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static TL_payments_savedStarGifts TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final TL_payments_savedStarGifts result = TL_payments_savedStarGifts.constructor != constructor ? null : new TL_payments_savedStarGifts();
            return TLdeserialize(TL_payments_savedStarGifts.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            count = stream.readInt32(exception);
            if ((flags & 2) != 0) {
                chat_notifications_enabled = stream.readBool(exception);
            }
            gifts = Vector.deserialize(stream, SavedStarGift::TLdeserialize, exception);
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
            stream.writeInt32(count);
            if ((flags & 2) != 0) {
                stream.writeBool(chat_notifications_enabled);
            }
            Vector.serialize(stream, gifts);
            if ((flags & 1) != 0) {
                stream.writeString(next_offset);
            }
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);
        }
    }

    public static class InputSavedStarGift extends TLObject {
        public static InputSavedStarGift TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            InputSavedStarGift result = null;
            switch (constructor) {
                case TL_inputSavedStarGiftUser.constructor:
                    result = new TL_inputSavedStarGiftUser();
                    break;
                case TL_inputSavedStarGiftChat.constructor:
                    result = new TL_inputSavedStarGiftChat();
                    break;
                case TL_inputSavedStarGiftSlug.constructor:
                    result = new TL_inputSavedStarGiftSlug();
                    break;
            }
            return TLdeserialize(InputSavedStarGift.class, result, stream, constructor, exception);
        }
    }

    public static final class TL_inputSavedStarGiftUser extends InputSavedStarGift {
        public static final int constructor = 0x69279795;

        public int msg_id;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(msg_id);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            msg_id = stream.readInt32(exception);
        }
    }

    public static final class TL_inputSavedStarGiftSlug extends InputSavedStarGift {
        public static final int constructor = 0x2085c238;

        public String slug;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(slug);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            slug = stream.readString(exception);
        }
    }

    public static final class TL_inputSavedStarGiftChat extends InputSavedStarGift {
        public static final int constructor = 0xf101aa7f;

        public TLRPC.InputPeer peer;
        public long saved_id;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt64(saved_id);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            peer = TLRPC.InputPeer.TLdeserialize(stream, stream.readInt32(exception), exception);
            saved_id = stream.readInt64(exception);
        }
    }

    public static final class toggleChatStarGiftNotifications extends TLObject {
        public static final int constructor = 0x60eaefa1;

        public int flags;
        public boolean enabled;
        public TLRPC.InputPeer peer;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = enabled ? flags | 1 : flags &~ 1;
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
        }
    }

    public static final class starGiftWithdrawalUrl extends TLObject {
        public static final int constructor = 0x84aa3a9c;

        public String url;

        public static starGiftWithdrawalUrl TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final starGiftWithdrawalUrl result = starGiftWithdrawalUrl.constructor != constructor ? null : new starGiftWithdrawalUrl();
            return TLdeserialize(starGiftWithdrawalUrl.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            url = stream.readString(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(url);
        }
    }

    public static final class getStarGiftWithdrawalUrl extends TLObject {
        public static final int constructor = 0xd06e93a8;

        public InputSavedStarGift stargift;
        public TLRPC.InputCheckPasswordSRP password;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return starGiftWithdrawalUrl.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stargift.serializeToStream(stream);
            password.serializeToStream(stream);
        }
    }

    public static class PaidReactionPrivacy extends TLObject {
        public TLRPC.InputPeer peer;

        public static PaidReactionPrivacy TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            PaidReactionPrivacy result = null;
            switch (constructor) {
                case paidReactionPrivacyDefault.constructor:
                    result = new paidReactionPrivacyDefault();
                    break;
                case paidReactionPrivacyAnonymous.constructor:
                    result = new paidReactionPrivacyAnonymous();
                    break;
                case paidReactionPrivacyPeer.constructor:
                    result = new paidReactionPrivacyPeer();
                    break;
            }
            return TLdeserialize(PaidReactionPrivacy.class, result, stream, constructor, exception);
        }

        public long getDialogId() {
            if (this instanceof paidReactionPrivacyDefault)
                return 0;
            if (this instanceof paidReactionPrivacyAnonymous)
                return UserObject.ANONYMOUS;
            if (this instanceof paidReactionPrivacyPeer)
                return DialogObject.getPeerDialogId(peer);
            return 0;
        }
    }

    public static class paidReactionPrivacyDefault extends PaidReactionPrivacy {
        public static final int constructor = 0x206ad49e;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class paidReactionPrivacyAnonymous extends PaidReactionPrivacy {
        public static final int constructor = 0x1f0c1ad9;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class paidReactionPrivacyPeer extends PaidReactionPrivacy {
        public static final int constructor = 0xdc6cfcf0;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            peer = TLRPC.InputPeer.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
        }
    }

    public static class updatePaidMessagesPrice extends TLObject {
        public static final int constructor = 0x4b12327b;

        public int flags;
        public boolean suggestions_allowed;
        public TLRPC.InputChannel channel;
        public long send_paid_messages_stars;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = suggestions_allowed ? flags | 1 : flags &~ 1;
            stream.writeInt32(flags);
            channel.serializeToStream(stream);
            stream.writeInt64(send_paid_messages_stars);
        }
    }

    public static class toggleStarGiftsPinnedToTop extends TLObject {
        public static final int constructor = 0x1513e7b0;

        public TLRPC.InputPeer peer;
        public ArrayList<InputSavedStarGift> stargift = new ArrayList<>();

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            Vector.serialize(stream, stargift);
        }
    }

    public static class updateStarGiftPrice extends TLMethod<TLRPC.Updates> {
        public static final int constructor = 0xedbe6ccb;

        public InputSavedStarGift stargift;
        public StarsAmount resell_amount;

        @Override
        public TLRPC.Updates deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stargift.serializeToStream(stream);
            resell_amount.serializeToStream(stream);
        }
    }

    public static class resaleStarGifts extends TLObject {
        public static final int constructor = 0x947a12df;

        public int flags;
        public int count;
        public ArrayList<TL_stars.StarGift> gifts = new ArrayList<>();
        public String next_offset;
        public ArrayList<TL_stars.StarGiftAttribute> attributes = new ArrayList<>();
        public long attributes_hash;
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TL_stars.starGiftAttributeCounter> counters = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static resaleStarGifts TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final resaleStarGifts result = resaleStarGifts.constructor != constructor ? null : new resaleStarGifts();
            return TLdeserialize(resaleStarGifts.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            count = stream.readInt32(exception);
            gifts = Vector.deserialize(stream, TL_stars.StarGift::TLdeserialize, exception);
            if ((flags & 1) != 0) {
                next_offset = stream.readString(exception);
            }
            if ((flags & 2) != 0) {
                attributes = Vector.deserialize(stream, TL_stars.StarGiftAttribute::TLdeserialize, exception);
                attributes_hash = stream.readInt64(exception);
            }
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            if ((flags & 4) != 0) {
                counters = Vector.deserialize(stream, TL_stars.starGiftAttributeCounter::TLdeserialize, exception);
            }
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(count);
            Vector.serialize(stream, gifts);
            if ((flags & 1) != 0) {
                stream.writeString(next_offset);
            }
            if ((flags & 2) != 0) {
                Vector.serialize(stream, attributes);
                stream.writeInt64(attributes_hash);
            }
            Vector.serialize(stream, chats);
            if ((flags & 4) != 0) {
                Vector.serialize(stream, counters);
            }
            Vector.serialize(stream, users);
        }
    }

    public static class getResaleStarGifts extends TLObject {
        public static final int constructor = 0x7a5fa236;

        public int flags;
        public boolean sort_by_price;
        public boolean sort_by_num;
        public long attributes_hash;
        public long gift_id;
        public ArrayList<TL_stars.StarGiftAttributeId> attributes = new ArrayList<>();
        public String offset;
        public int limit;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return resaleStarGifts.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = sort_by_price ? flags | 2 : flags &~ 2;
            flags = sort_by_num ? flags | 4 : flags &~ 4;
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                stream.writeInt64(attributes_hash);
            }
            stream.writeInt64(gift_id);
            if ((flags & 8) != 0) {
                Vector.serialize(stream, attributes);
            }
            stream.writeString(offset);
            stream.writeInt32(limit);
        }
    }

    public static class Tl_starsRating extends TLObject {
        public static final int constructor = 0x1b0e4f07;
        public int flags;
        public int level;
        public long current_level_stars;
        public long stars;
        public long next_level_stars;

        public static Tl_starsRating TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final Tl_starsRating result = Tl_starsRating.constructor != constructor ? null : new Tl_starsRating();
            return TLdeserialize(Tl_starsRating.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            level = stream.readInt32(exception);
            current_level_stars = stream.readInt64(exception);
            stars = stream.readInt64(exception);
            if (hasFlag(flags, FLAG_0)) {
                next_level_stars = stream.readInt64(exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(level);
            stream.writeInt64(current_level_stars);
            stream.writeInt64(stars);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeInt64(next_level_stars);
            }
        }
    }

    public static class TL_starGiftCollection extends TLObject {
        public static final int constructor = 0x9d6b13b0;

        public int flags;
        public int collection_id;
        public String title;
        public TLRPC.Document icon;
        public int gifts_count;
        public long hash;

        public TL_starGiftCollection() {

        }

        public static TL_starGiftCollection TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            TL_starGiftCollection result = null;
            if (constructor == TL_starGiftCollection.constructor) {
                result = new TL_starGiftCollection();
            }
            return TLdeserialize(TL_starGiftCollection.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            collection_id = stream.readInt32(exception);
            title = stream.readString(exception);
            if ((flags & 1) != 0) {
                icon = TLRPC.Document.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            gifts_count = stream.readInt32(exception);
            hash = stream.readInt64(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(collection_id);
            stream.writeString(title);
            if ((flags & 1) != 0) {
                icon.serializeToStream(stream);
            }
            stream.writeInt32(gifts_count);
            stream.writeInt64(hash);
        }
    }

    public static class StarGiftCollections extends TLObject {
        public ArrayList<TL_starGiftCollection> collections = new ArrayList<>();

        public static StarGiftCollections TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            StarGiftCollections result = null;
            switch (constructor) {
                case TL_starGiftCollectionsNotModified.constructor:
                    result = new TL_starGiftCollectionsNotModified();
                    break;
                case TL_starGiftCollections.constructor:
                    result = new TL_starGiftCollections();
                    break;
            }
            return TLdeserialize(StarGiftCollections.class, result, stream, constructor, exception);
        }
    }

    public static class TL_starGiftCollectionsNotModified extends StarGiftCollections {
        public static final int constructor = 0xa0ba4f17;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_starGiftCollections extends StarGiftCollections {
        public static final int constructor = 0x8a2932f3;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, collections);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            collections = Vector.deserialize(stream, TL_starGiftCollection::TLdeserialize, exception);
        }
    }

    public static class createStarGiftCollection extends TLObject {
        public static final int constructor = 0x1f4a0e87;

        public TLRPC.InputPeer peer;
        public String title;
        public ArrayList<InputSavedStarGift> stargift = new ArrayList<>();

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_starGiftCollection.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeString(title);
            Vector.serialize(stream, stargift);
        }
    }

    public static class updateStarGiftCollection extends TLObject {
        public static final int constructor = 0x4fddbee7;

        public int flags;
        public TLRPC.InputPeer peer;
        public int collection_id;
        public String title;
        public ArrayList<InputSavedStarGift> delete_stargift = new ArrayList<>();
        public ArrayList<InputSavedStarGift> add_stargift = new ArrayList<>();
        public ArrayList<InputSavedStarGift> order = new ArrayList<>();

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_starGiftCollection.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            stream.writeInt32(collection_id);
            if ((flags & 1) != 0) {
                stream.writeString(title);
            }
            if ((flags & 2) != 0) {
                Vector.serialize(stream, delete_stargift);
            }
            if ((flags & 4) != 0) {
                Vector.serialize(stream, add_stargift);
            }
            if ((flags & 8) != 0) {
                Vector.serialize(stream, order);
            }
        }
    }

    public static class reorderStarGiftCollections extends TLObject {
        public static final int constructor = 0xc32af4cc;

        public TLRPC.InputPeer peer;
        public ArrayList<Integer> order = new ArrayList<>();

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            Vector.serializeInt(stream, order);
        }
    }

    public static class deleteStarGiftCollection extends TLObject {
        public static final int constructor = 0xad5648e8;

        public TLRPC.InputPeer peer;
        public int collection_id;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(collection_id);
        }
    }

    public static class getStarGiftCollections extends TLObject {
        public static final int constructor = 0x981b91dd;

        public TLRPC.InputPeer peer;
        public long hash;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return StarGiftCollections.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt64(hash);
        }
    }

    public static class UniqueStarGiftValueInfo extends TLObject {
        public static final int constructor = 0x512fe446;

        public int flags;
        public boolean last_sale_on_fragment;
        public boolean value_is_average;
        public String currency;
        public long value;
        public int initial_sale_date;
        public long initial_sale_stars;
        public long initial_sale_price;
        public int last_sale_date;
        public long last_sale_price;
        public long floor_price;
        public long average_price;
        public int listed_count;
        public int fragment_listed_count;
        public String fragment_listed_url;

        public static UniqueStarGiftValueInfo TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            UniqueStarGiftValueInfo result = null;
            if (constructor == UniqueStarGiftValueInfo.constructor) {
                result = new UniqueStarGiftValueInfo();
            }
            return TLdeserialize(UniqueStarGiftValueInfo.class, result, stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);

            flags = setFlag(flags, FLAG_1, last_sale_on_fragment);
            flags = setFlag(flags, FLAG_6, value_is_average);
            stream.writeInt32(flags);

            stream.writeString(currency);
            stream.writeInt64(value);
            stream.writeInt32(initial_sale_date);
            stream.writeInt64(initial_sale_stars);
            stream.writeInt64(initial_sale_price);
            if (hasFlag(flags, FLAG_0)) {
                 stream.writeInt32(last_sale_date);
                 stream.writeInt64(last_sale_price);
            }
            if (hasFlag(flags, FLAG_2)) {
                stream.writeInt64(floor_price);
            }
            if (hasFlag(flags, FLAG_3)) {
                stream.writeInt64(average_price);
            }
            if (hasFlag(flags, FLAG_4)) {
                stream.writeInt32(listed_count);
            }
            if (hasFlag(flags, FLAG_5)) {
                 stream.writeInt32(fragment_listed_count);
                 stream.writeString(fragment_listed_url);
            }
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            last_sale_on_fragment = hasFlag(flags, FLAG_1);
            value_is_average = hasFlag(flags, FLAG_6);
            currency = stream.readString(exception);
            value = stream.readInt64(exception);
            initial_sale_date = stream.readInt32(exception);
            initial_sale_stars = stream.readInt64(exception);
            initial_sale_price = stream.readInt64(exception);
            if (hasFlag(flags, FLAG_0)) {
                last_sale_date = stream.readInt32(exception);
                last_sale_price = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_2)) {
                floor_price = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                average_price = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_4)) {
                listed_count = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_5)) {
                fragment_listed_count = stream.readInt32(exception);
                fragment_listed_url = stream.readString(exception);
            }
        }
    }

    public static class getUniqueStarGiftValueInfo extends TLObject {
        public static final int constructor = 0x4365af6b;

        public String slug;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return UniqueStarGiftValueInfo.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(slug);
        }
    }

    public static class CheckCanSendGiftResult extends TLObject {
        public static CheckCanSendGiftResult TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            CheckCanSendGiftResult result = null;
            switch (constructor) {
                case checkCanSendGiftResultOk.constructor:
                    result = new checkCanSendGiftResultOk();
                    break;
                case checkCanSendGiftResultFail.constructor:
                    result = new checkCanSendGiftResultFail();
                    break;
            }
            return TLdeserialize(CheckCanSendGiftResult.class, result, stream, constructor, exception);
        }
    }

    public static class checkCanSendGiftResultOk extends CheckCanSendGiftResult {
        public static final int constructor = 0x374fa7ad;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class checkCanSendGiftResultFail extends CheckCanSendGiftResult {
        public static final int constructor = 0xd5e58274;

        public TLRPC.TL_textWithEntities reason;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            reason = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            reason.serializeToStream(stream);
        }
    }

    public static class checkCanSendGift extends TLObject {
        public static final int constructor = 0xc0c4edc9;

        public long gift_id;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return CheckCanSendGiftResult.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(gift_id);
        }
    }

    public static abstract class StarGiftAuctionState extends TLObject {

        private static StarGiftAuctionState fromConstructor(int constructor) {
            switch (constructor) {
                case TL_starGiftAuctionStateNotModified.constructor:
                    return new TL_starGiftAuctionStateNotModified();
                case TL_starGiftAuctionStateFinished.constructor:
                    return new TL_starGiftAuctionStateFinished();
                case TL_starGiftAuctionState.constructor:
                    return new TL_starGiftAuctionState();
            }
            return null;
        }

        public static StarGiftAuctionState TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            return TLdeserialize(StarGiftAuctionState.class, fromConstructor(constructor), stream, constructor, exception);
        }
    }

    public static class TL_starGiftAuctionState extends StarGiftAuctionState {
        public static final int constructor = 0x5DB04F4B;

        public int version;
        public int start_date;
        public int end_date;
        public long min_bid_amount;
        public ArrayList<TL_AuctionBidLevel> bid_levels = new ArrayList<>();
        public ArrayList<Long> top_bidders = new ArrayList<>();
        public int next_round_at;
        public int gifts_left;
        public int current_round;
        public int total_rounds;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(version);
            stream.writeInt32(start_date);
            stream.writeInt32(end_date);
            stream.writeInt64(min_bid_amount);
            Vector.serialize(stream, bid_levels);
            Vector.serializeLong(stream, top_bidders);
            stream.writeInt32(next_round_at);
            stream.writeInt32(gifts_left);
            stream.writeInt32(current_round);
            stream.writeInt32(total_rounds);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            version = stream.readInt32(exception);
            start_date = stream.readInt32(exception);
            end_date = stream.readInt32(exception);
            min_bid_amount = stream.readInt64(exception);
            bid_levels = Vector.deserialize(stream, TL_AuctionBidLevel::TLdeserialize, exception);
            top_bidders = Vector.deserializeLong(stream, exception);
            next_round_at = stream.readInt32(exception);
            gifts_left = stream.readInt32(exception);
            current_round = stream.readInt32(exception);
            total_rounds = stream.readInt32(exception);
        }
    }
    
    public static class TL_starGiftAuctionStateNotModified extends StarGiftAuctionState {
        public static final int constructor = 0xFE333952;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {

        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_starGiftAuctionStateFinished extends StarGiftAuctionState {
        public static final int constructor = 0x7D967C3A;

        public int start_date;
        public int end_date;
        public long average_price;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            start_date = stream.readInt32(exception);
            end_date = stream.readInt32(exception);
            average_price = stream.readInt64(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(start_date);
            stream.writeInt32(end_date);
            stream.writeInt64(average_price);
        }
    }

    public static class TL_StarGiftAuctionUserState extends TLObject {
        public static final int constructor = 0x2eeed1c4;

        public int flags;
        public boolean returned;
        public int acquired_count;
        public long bid_amount;
        public int bid_date;
        public TLRPC.Peer peer;
        public long min_bid_amount;

        public static TL_StarGiftAuctionUserState TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final TL_StarGiftAuctionUserState result = TL_StarGiftAuctionUserState.constructor != constructor ? null : new TL_StarGiftAuctionUserState();
            return TLdeserialize(TL_StarGiftAuctionUserState.class, result, stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_1, returned);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeInt64(bid_amount);
                stream.writeInt32(bid_date);
                stream.writeInt64(min_bid_amount);
                peer.serializeToStream(stream);
            }
            stream.writeInt32(acquired_count);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            returned = hasFlag(flags, FLAG_1);
            if (hasFlag(flags, FLAG_0)) {
                bid_amount = stream.readInt64(exception);
                bid_date = stream.readInt32(exception);
                min_bid_amount = stream.readInt64(exception);
                peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            acquired_count = stream.readInt32(exception);
        }
    }

    public static class TL_StarGiftAuctionAcquiredGift extends TLObject {
        public static final int constructor = 0xAB60E20B;

        public boolean name_hidden;
        public TLRPC.Peer peer;
        public int date;
        public long bid_amount;
        public int round;
        public int pos;
        public TLRPC.TL_textWithEntities message;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);

            int flags = 0;
            flags = setFlag(flags, FLAG_0, name_hidden);
            flags = setFlag(flags, FLAG_1, message != null);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            stream.writeInt32(date);
            stream.writeInt64(bid_amount);
            stream.writeInt32(round);
            stream.writeInt32(pos);
            if (hasFlag(flags, FLAG_1)) {
                message.serializeToStream(stream);
            }
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            final int flags = stream.readInt32(exception);
            name_hidden = hasFlag(flags, FLAG_0);
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            date = stream.readInt32(exception);
            bid_amount = stream.readInt64(exception);
            round = stream.readInt32(exception);
            pos = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_1)) {
                message = TLRPC.TL_textWithEntities.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public static TL_StarGiftAuctionAcquiredGift TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final TL_StarGiftAuctionAcquiredGift result = TL_StarGiftAuctionAcquiredGift.constructor != constructor ? null : new TL_StarGiftAuctionAcquiredGift();
            return TLdeserialize(TL_StarGiftAuctionAcquiredGift.class, result, stream, constructor, exception);
        }
    }

    public static class TL_StarGiftActiveAuctionState extends TLObject {
        public static final int constructor = 0xD31BC45D;

        public StarGift gift;
        public StarGiftAuctionState state;
        public TL_StarGiftAuctionUserState user_state;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            gift.serializeToStream(stream);
            state.serializeToStream(stream);
            user_state.serializeToStream(stream);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            gift = StarGift.TLdeserialize(stream, stream.readInt32(exception), exception);
            state = StarGiftAuctionState.TLdeserialize(stream, stream.readInt32(exception), exception);
            user_state = TL_StarGiftAuctionUserState.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public static TL_StarGiftActiveAuctionState TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final TL_StarGiftActiveAuctionState result = TL_StarGiftActiveAuctionState.constructor != constructor ? null : new TL_StarGiftActiveAuctionState();
            return TLdeserialize(TL_StarGiftActiveAuctionState.class, result, stream, constructor, exception);
        }
    }

    public static abstract class InputStarGiftAuction extends TLObject {
        private static InputStarGiftAuction fromConstructor(int constructor) {
            switch (constructor) {
                case TL_inputStarGiftAuction.constructor:       return new TL_inputStarGiftAuction();
                case TL_inputStarGiftAuctionSlug.constructor:   return new TL_inputStarGiftAuctionSlug();
            }
            return null;
        }

        public static InputStarGiftAuction TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            return TLdeserialize(InputStarGiftAuction.class, fromConstructor(constructor), stream, constructor, exception);
        }
    }

    public static class TL_inputStarGiftAuction extends InputStarGiftAuction {
        public static final int constructor = 0x02E16C98;

        public long gift_id;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(gift_id);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            gift_id = stream.readInt64(exception);
        }
    }

    public static class TL_inputStarGiftAuctionSlug extends InputStarGiftAuction {
        public static final int constructor = 0x7AB58308;

        public String slug;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(slug);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            slug = stream.readString(exception);
        }
    }

    public static class TL_AuctionBidLevel extends TLObject {
        public static final int constructor = 0x310240CC;

        public int pos;
        public long amount;
        public int date;

        public static TL_AuctionBidLevel TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final TL_AuctionBidLevel result = TL_AuctionBidLevel.constructor != constructor ? null : new TL_AuctionBidLevel();
            return TLdeserialize(TL_AuctionBidLevel.class, result, stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(pos);
            stream.writeInt64(amount);
            stream.writeInt32(date);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            pos = stream.readInt32(exception);
            amount = stream.readInt64(exception);
            date = stream.readInt32(exception);
        }
    }
}

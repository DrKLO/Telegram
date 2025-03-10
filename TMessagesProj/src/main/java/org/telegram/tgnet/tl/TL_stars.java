package org.telegram.tgnet.tl;

import androidx.annotation.Nullable;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
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
        public boolean can_upgrade;
        public long id;
        public TLRPC.Document sticker;
        public long stars;
        public int availability_remains;
        public int availability_total;
        public long convert_stars;
        public int first_sale_date;
        public int last_sale_date;
        public long upgrade_stars;

        public String title;
        public String slug;
        public int num;
        public TLRPC.Peer owner_id;
        public String owner_name;
        public String owner_address;
        public ArrayList<StarGiftAttribute> attributes = new ArrayList<>();
        public int availability_issued;
        public String gift_address;

        public static StarGift TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            StarGift result = null;
            switch (constructor) {
                case TL_starGift.constructor:
                    result = new TL_starGift();
                    break;
                case TL_starGiftUnique.constructor:
                    result = new TL_starGiftUnique();
                    break;
                case TL_starGiftUnique_layer198.constructor:
                    result = new TL_starGiftUnique_layer198();
                    break;
                case TL_starGiftUnique_layer197.constructor:
                    result = new TL_starGiftUnique_layer197();
                    break;
                case TL_starGiftUnique_layer196.constructor:
                    result = new TL_starGiftUnique_layer196();
                    break;
                case TL_starGift_layer190.constructor:
                    result = new TL_starGift_layer190();
                    break;
                case TL_starGift_layer195.constructor:
                    result = new TL_starGift_layer195();
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
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(hash);
            Vector.serialize(stream, gifts);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            hash = stream.readInt32(exception);
            gifts = Vector.deserialize(stream, StarGift::TLdeserialize, exception);
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

    public static class getSavedStarGifts extends TLObject {
        public static final int constructor = 0x23830de9;

        public int flags;
        public boolean exclude_unsaved;
        public boolean exclude_saved;
        public boolean exclude_unlimited;
        public boolean exclude_limited;
        public boolean exclude_unique;
        public boolean sort_by_value;
        public TLRPC.InputPeer peer;
        public String offset;
        public int limit;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_payments_savedStarGifts.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = exclude_unsaved ? flags | 1 : flags &~ 1;
            flags = exclude_saved ? flags | 2 : flags &~ 2;
            flags = exclude_unlimited ? flags | 4 : flags &~ 4;
            flags = exclude_limited ? flags | 8 : flags &~ 8;
            flags = exclude_unique ? flags | 16 : flags &~ 16;
            flags = sort_by_value ? flags | 32 : flags &~ 32;
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
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

    public static class starGiftUpgradePreview extends TLObject {
        public static final int constructor = 0x167bd90b;

        public ArrayList<StarGiftAttribute> sample_attributes = new ArrayList<>();

        public static starGiftUpgradePreview TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (starGiftUpgradePreview.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in starGiftUpgradePreview", constructor));
                }
                return null;
            }
            starGiftUpgradePreview result = new starGiftUpgradePreview();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, sample_attributes);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            sample_attributes = Vector.deserialize(stream, StarGiftAttribute::TLdeserialize, exception);
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
        public int paid_messages;
        public int premium_gift_months;

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

        public void readParams(InputSerializedData stream, boolean exception) {
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

        public void serializeToStream(OutputSerializedData stream) {
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

        public void readParams(InputSerializedData stream, boolean exception) {
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

        public void serializeToStream(OutputSerializedData stream) {
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

        public static StarsAmount TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
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

    public static class TL_starsTransaction extends StarsTransaction {
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
                Vector.serialize(stream, extended_media);
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
            balance = new StarsAmount(stream.readInt64(exception));
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
        public static final int constructor = 0x104fcfa7;

        public TLRPC.InputPeer peer;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return StarsStatus.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
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

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return StarsStatus.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
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

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.TL_payments_paymentResult.TLdeserialize(stream, constructor, exception);
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
                case starGiftAttributeOriginalDetails.constructor:
                    result = new starGiftAttributeOriginalDetails();
                    break;
                case starGiftAttributeOriginalDetails_layer197.constructor:
                    result = new starGiftAttributeOriginalDetails_layer197();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in StarGiftAttribute", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
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
        public static final int constructor = 0x94271762;

        public int center_color;
        public int edge_color;
        public int pattern_color;
        public int text_color;

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
                recipient_id = new TLRPC.TL_peerUser();
                recipient_id.user_id = stream.readInt64(exception);
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
        public static final int constructor = 0xcaa2f60b;

        public StarGift gift;
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static TL_payments_uniqueStarGift TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (TL_payments_uniqueStarGift.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_payments_uniqueStarGift", constructor));
                } else {
                    return null;
                }
            }
            TL_payments_uniqueStarGift result = new TL_payments_uniqueStarGift();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            gift = StarGift.TLdeserialize(stream, stream.readInt32(exception), exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            gift.serializeToStream(stream);
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

    public static final class SavedStarGift extends TLObject {
        public static final int constructor = 0x6056dba5;

        public int flags;
        public boolean name_hidden;
        public boolean unsaved;
        public boolean refunded;
        public boolean can_upgrade;
        public boolean pinned_to_top;
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

        public static SavedStarGift TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (SavedStarGift.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in savedStarGift", constructor));
                } else {
                    return null;
                }
            }
            SavedStarGift result = new SavedStarGift();
            result.readParams(stream, exception);
            return result;
        }

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
            if (TL_payments_savedStarGifts.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_payments_savedStarGifts", constructor));
                } else {
                    return null;
                }
            }
            TL_payments_savedStarGifts result = new TL_payments_savedStarGifts();
            result.readParams(stream, exception);
            return result;
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
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in InputSavedStarGift", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
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
            if (starGiftWithdrawalUrl.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in starGiftWithdrawalUrl", constructor));
                }
                return null;
            }
            starGiftWithdrawalUrl result = new starGiftWithdrawalUrl();
            result.readParams(stream, exception);
            return result;
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
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in PaidReactionPrivacy", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
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
        public static final int constructor = 0xfc84653f;

        public TLRPC.InputChannel channel;
        public long send_paid_messages_stars;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
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

}

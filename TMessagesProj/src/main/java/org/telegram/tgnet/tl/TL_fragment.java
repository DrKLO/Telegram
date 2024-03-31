package org.telegram.tgnet.tl;

import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

public class TL_fragment {

    public static class InputCollectible extends TLObject {
        public static InputCollectible TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            InputCollectible result = null;
            switch (constructor) {
                case TL_inputCollectibleUsername.constructor:
                    result = new TL_inputCollectibleUsername();
                    break;
                case TL_inputCollectiblePhone.constructor:
                    result = new TL_inputCollectiblePhone();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in InputCollectible", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_inputCollectibleUsername extends InputCollectible {
        public static final int constructor = 0xe39460a9;

        public String username;

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(username);
        }

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            username = stream.readString(exception);
        }
    }

    public static class TL_inputCollectiblePhone extends InputCollectible {
        public static final int constructor = 0xa2e214a4;

        public String phone;

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(phone);
        }

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            phone = stream.readString(exception);
        }
    }

    public static class TL_collectibleInfo extends TLObject {
        public static final int constructor = 0x6ebdff91;

        public int purchase_date;
        public String currency;
        public long amount;
        public String crypto_currency;
        public long crypto_amount;
        public String url;

        public static TL_collectibleInfo TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (TL_collectibleInfo.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_collectibleInfo", constructor));
                } else {
                    return null;
                }
            }
            TL_collectibleInfo result = new TL_collectibleInfo();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(purchase_date);
            stream.writeString(currency);
            stream.writeInt64(amount);
            stream.writeString(crypto_currency);
            stream.writeInt64(crypto_amount);
            stream.writeString(url);
        }

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            purchase_date = stream.readInt32(exception);
            currency = stream.readString(exception);
            amount = stream.readInt64(exception);
            crypto_currency = stream.readString(exception);
            crypto_amount = stream.readInt64(exception);
            url = stream.readString(exception);
        }
    }

    public static class TL_getCollectibleInfo extends TLObject {
        public static final int constructor = 0xbe1e85ba;

        public InputCollectible collectible;

        @Override
        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TL_collectibleInfo.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            collectible.serializeToStream(stream);
        }
    }

}

package org.telegram.tgnet.tl;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLParseException;

public class TL_fragment {

    public static class InputCollectible extends TLObject {
        public static InputCollectible TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            InputCollectible result = null;
            switch (constructor) {
                case TL_inputCollectibleUsername.constructor:
                    result = new TL_inputCollectibleUsername();
                    break;
                case TL_inputCollectiblePhone.constructor:
                    result = new TL_inputCollectiblePhone();
                    break;
            }
            return TLdeserialize(InputCollectible.class, result, stream, constructor, exception);
        }
    }

    public static class TL_inputCollectibleUsername extends InputCollectible {
        public static final int constructor = 0xe39460a9;

        public String username;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(username);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            username = stream.readString(exception);
        }
    }

    public static class TL_inputCollectiblePhone extends InputCollectible {
        public static final int constructor = 0xa2e214a4;

        public String phone;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(phone);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
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

        public static TL_collectibleInfo TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final TL_collectibleInfo result = TL_collectibleInfo.constructor != constructor ? null : new TL_collectibleInfo();
            return TLdeserialize(TL_collectibleInfo.class, result, stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(purchase_date);
            stream.writeString(currency);
            stream.writeInt64(amount);
            stream.writeString(crypto_currency);
            stream.writeInt64(crypto_amount);
            stream.writeString(url);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
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
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_collectibleInfo.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            collectible.serializeToStream(stream);
        }
    }

}

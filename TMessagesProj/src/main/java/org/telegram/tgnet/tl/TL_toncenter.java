package org.telegram.tgnet.tl;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLMethod;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;

import java.util.ArrayList;

public class TL_toncenter {

    public static class apiResponse extends TLObject {
        public static final int constructor = 0xac8dfe19;

        public TLRPC.TL_dataJSON response;

        public static apiResponse TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final apiResponse result = apiResponse.constructor != constructor ? null : new apiResponse();
            return TLdeserialize(apiResponse.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            response = TLRPC.TL_dataJSON.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            response.serializeToStream(stream);
        }
    }
    public static class performApiRequest extends TLMethod<apiResponse> {
        public static final int constructor = 0x8d7bdd61;

        public boolean post;
        public String endpoint;
        public String query;
        public String payload;

        @Override
        public apiResponse deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return apiResponse.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            int flags = 0;
            flags = setFlag(flags, FLAG_0, post);
            flags = setFlag(flags, FLAG_1, query != null);
            flags = setFlag(flags, FLAG_2, payload != null);
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeString(endpoint);
            if (query != null)
                stream.writeString(query);
            if (payload != null)
                stream.writeString(payload);
        }
    }

    public static class streamingUrl extends TLObject {
        public static final int constructor = 0x19887601;

        public String url;
        public int expires;

        public static streamingUrl TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final streamingUrl result = streamingUrl.constructor != constructor ? null : new streamingUrl();
            return TLdeserialize(streamingUrl.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            url = stream.readString(exception);
            expires = stream.readInt32(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(url);
            stream.writeInt32(expires);
        }
    }
    public static class getStreamingUrl extends TLMethod<streamingUrl> {
        public static final int constructor = 0xcdaf63c7;

        @Override
        public streamingUrl deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return streamingUrl.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class onrampMethodAvailability extends TLObject {
        public static final int constructor = 0x9ec8c6a8;

        public int flags;
        public boolean available;
        public String payment_method;

        public static onrampMethodAvailability TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final onrampMethodAvailability result = onrampMethodAvailability.constructor != constructor ? null : new onrampMethodAvailability();
            return TLdeserialize(onrampMethodAvailability.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            available = hasFlag(flags, FLAG_0);
            payment_method = stream.readString(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, available);
            stream.writeInt32(flags);
            stream.writeString(payment_method);
        }
    }
    public static class onrampProviderInfo extends TLObject {
        public static final int constructor = 0xdc27582;

        public int flags;
        public boolean supports_base_currencies;
        public boolean supports_limits;
        public boolean supports_quote;
        public String id;
        public String name;
        public ArrayList<String> crypto_currencies = new ArrayList<>();

        public static onrampProviderInfo TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final onrampProviderInfo result = onrampProviderInfo.constructor != constructor ? null : new onrampProviderInfo();
            return TLdeserialize(onrampProviderInfo.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            supports_base_currencies = hasFlag(flags, FLAG_0);
            supports_limits = hasFlag(flags, FLAG_1);
            supports_quote = hasFlag(flags, FLAG_2);
            id = stream.readString(exception);
            name = stream.readString(exception);
            crypto_currencies = Vector.deserializeString(stream, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, supports_base_currencies);
            flags = setFlag(flags, FLAG_1, supports_limits);
            flags = setFlag(flags, FLAG_2, supports_quote);
            stream.writeInt32(flags);
            stream.writeString(id);
            stream.writeString(name);
            Vector.serializeString(stream, crypto_currencies);
        }
    }
    public static class onrampAvailability extends TLObject {
        public static final int constructor = 0xf9c84973;

        public int flags;
        public boolean allowed;
        public boolean buy_allowed;
        public String country_code;
        public String state;
        public ArrayList<onrampMethodAvailability> methods = new ArrayList<>();

        public static onrampAvailability TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final onrampAvailability result = onrampAvailability.constructor != constructor ? null : new onrampAvailability();
            return TLdeserialize(onrampAvailability.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            allowed = hasFlag(flags, FLAG_0);
            buy_allowed = hasFlag(flags, FLAG_1);
            country_code = stream.readString(exception);
            if (hasFlag(flags, FLAG_2)) {
                state = stream.readString(exception);
            }
            methods = Vector.deserialize(stream, onrampMethodAvailability::TLdeserialize, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, allowed);
            flags = setFlag(flags, FLAG_1, buy_allowed);
            flags = setFlag(flags, FLAG_2, state != null);
            stream.writeInt32(flags);
            stream.writeString(country_code);
            if (state != null) {
                stream.writeString(state);
            }
            Vector.serialize(stream, methods);
        }
    }
    public static class onrampLimits extends TLObject {
        public static final int constructor = 0x7be2677e;

        public String base_currency;
        public String base_min_amount;
        public String base_max_amount;
        public String crypto_min_amount;
        public String crypto_max_amount;
        public String payment_method;

        public static onrampLimits TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final onrampLimits result = onrampLimits.constructor != constructor ? null : new onrampLimits();
            return TLdeserialize(onrampLimits.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            base_currency = stream.readString(exception);
            base_min_amount = stream.readString(exception);
            base_max_amount = stream.readString(exception);
            crypto_min_amount = stream.readString(exception);
            crypto_max_amount = stream.readString(exception);
            payment_method = stream.readString(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(base_currency);
            stream.writeString(base_min_amount);
            stream.writeString(base_max_amount);
            stream.writeString(crypto_min_amount);
            stream.writeString(crypto_max_amount);
            stream.writeString(payment_method);
        }
    }
    public static class onrampQuote extends TLObject {
        public static final int constructor = 0x7a8011e9;

        public String base_currency;
        public String base_amount;
        public String crypto_currency;
        public String crypto_amount;
        public String crypto_price;
        public String fee_amount;
        public String extra_fee_amount;
        public String network_fee_amount;
        public String total_amount;
        public String payment_method;
        public int expires_date;

        public static onrampQuote TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final onrampQuote result = onrampQuote.constructor != constructor ? null : new onrampQuote();
            return TLdeserialize(onrampQuote.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            base_currency = stream.readString(exception);
            base_amount = stream.readString(exception);
            crypto_currency = stream.readString(exception);
            crypto_amount = stream.readString(exception);
            crypto_price = stream.readString(exception);
            fee_amount = stream.readString(exception);
            extra_fee_amount = stream.readString(exception);
            network_fee_amount = stream.readString(exception);
            total_amount = stream.readString(exception);
            payment_method = stream.readString(exception);
            expires_date = stream.readInt32(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(base_currency);
            stream.writeString(base_amount);
            stream.writeString(crypto_currency);
            stream.writeString(crypto_amount);
            stream.writeString(crypto_price);
            stream.writeString(fee_amount);
            stream.writeString(extra_fee_amount);
            stream.writeString(network_fee_amount);
            stream.writeString(total_amount);
            stream.writeString(payment_method);
            stream.writeInt32(expires_date);
        }
    }
    public static class onrampSession extends TLObject {
        public static final int constructor = 0xd1e42e24;

        public String provider;
        public String session_id;
        public String url;
        public int expires_date;

        public static onrampSession TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final onrampSession result = onrampSession.constructor != constructor ? null : new onrampSession();
            return TLdeserialize(onrampSession.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            provider = stream.readString(exception);
            session_id = stream.readString(exception);
            url = stream.readString(exception);
            expires_date = stream.readInt32(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(provider);
            stream.writeString(session_id);
            stream.writeString(url);
            stream.writeInt32(expires_date);
        }
    }
    public static class onrampBaseCurrencies extends TLObject {
        public ArrayList<String> currencies = new ArrayList<>();

        public static onrampBaseCurrencies TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (constructor != Vector.constructor) {
                return TLdeserialize(onrampBaseCurrencies.class, null, stream, constructor, exception);
            }
            onrampBaseCurrencies result = new onrampBaseCurrencies();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            currencies = Vector.deserializeString(stream, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            Vector.serializeString(stream, currencies);
        }
    }

    public static class getOnrampProviders extends TLMethod<Vector<onrampProviderInfo>> {
        public static final int constructor = 0x3f3e00dc;

        public String crypto_currency;

        @Override
        public Vector<onrampProviderInfo> deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return Vector.TLDeserialize(stream, constructor, exception, onrampProviderInfo::TLdeserialize);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            int flags = 0;
            flags = setFlag(flags, FLAG_0, crypto_currency != null);
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if (crypto_currency != null) {
                stream.writeString(crypto_currency);
            }
        }
    }
    public static class getOnrampBaseCurrencies extends TLMethod<onrampBaseCurrencies> {
        public static final int constructor = 0x1c8bedf3;

        public String provider;
        public String crypto_currency;

        @Override
        public onrampBaseCurrencies deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return onrampBaseCurrencies.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(provider);
            stream.writeString(crypto_currency);
        }
    }
    public static class getOnrampAvailability extends TLMethod<onrampAvailability> {
        public static final int constructor = 0x2ec29951;

        public String provider;
        public String crypto_currency;
        public String base_currency;

        @Override
        public onrampAvailability deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return onrampAvailability.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            int flags = 0;
            flags = setFlag(flags, FLAG_0, base_currency != null);
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeString(provider);
            stream.writeString(crypto_currency);
            if (base_currency != null) {
                stream.writeString(base_currency);
            }
        }
    }
    public static class getOnrampLimits extends TLMethod<onrampLimits> {
        public static final int constructor = 0x80e851fd;

        public String provider;
        public String crypto_currency;
        public String base_currency;
        public String payment_method;

        @Override
        public onrampLimits deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return onrampLimits.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            int flags = 0;
            flags = setFlag(flags, FLAG_0, payment_method != null);
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeString(provider);
            stream.writeString(crypto_currency);
            stream.writeString(base_currency);
            if (payment_method != null) {
                stream.writeString(payment_method);
            }
        }
    }
    public static class getOnrampQuote extends TLMethod<onrampQuote> {
        public static final int constructor = 0xe06645fa;

        public String provider;
        public String crypto_currency;
        public String base_currency;
        public String base_amount;
        public String crypto_amount;
        public String payment_method;

        @Override
        public onrampQuote deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return onrampQuote.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            int flags = 0;
            flags = setFlag(flags, FLAG_0, base_amount != null);
            flags = setFlag(flags, FLAG_1, crypto_amount != null);
            flags = setFlag(flags, FLAG_2, payment_method != null);
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeString(provider);
            stream.writeString(crypto_currency);
            stream.writeString(base_currency);
            if (base_amount != null) {
                stream.writeString(base_amount);
            }
            if (crypto_amount != null) {
                stream.writeString(crypto_amount);
            }
            if (payment_method != null) {
                stream.writeString(payment_method);
            }
        }
    }
    public static class createOnrampSession extends TLMethod<onrampSession> {
        public static final int constructor = 0xa2197cf6;

        public String provider;
        public String crypto_currency;
        public String address;
        public String payment_method;
        public String base_currency;
        public String base_amount;
        public String memo;
        public String theme;
        public String success_return_url;
        public String fail_return_url;
        public String crypto_amount;

        @Override
        public onrampSession deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return onrampSession.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            int flags = 0;
            flags = setFlag(flags, FLAG_0, payment_method != null);
            flags = setFlag(flags, FLAG_1, base_currency != null);
            flags = setFlag(flags, FLAG_2, base_amount != null);
            flags = setFlag(flags, FLAG_3, memo != null);
            flags = setFlag(flags, FLAG_4, theme != null);
            flags = setFlag(flags, FLAG_5, success_return_url != null);
            flags = setFlag(flags, FLAG_6, fail_return_url != null);
            flags = setFlag(flags, FLAG_7, crypto_amount != null);
            stream.writeInt32(flags);
            stream.writeString(provider);
            stream.writeString(crypto_currency);
            stream.writeString(address);
            if (payment_method != null) {
                stream.writeString(payment_method);
            }
            if (base_currency != null) {
                stream.writeString(base_currency);
            }
            if (base_amount != null) {
                stream.writeString(base_amount);
            }
            if (memo != null) {
                stream.writeString(memo);
            }
            if (theme != null) {
                stream.writeString(theme);
            }
            if (success_return_url != null) {
                stream.writeString(success_return_url);
            }
            if (fail_return_url != null) {
                stream.writeString(fail_return_url);
            }
            if (crypto_amount != null) {
                stream.writeString(crypto_amount);
            }
        }
    }

}

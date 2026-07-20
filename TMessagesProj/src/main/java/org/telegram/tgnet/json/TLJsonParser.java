package org.telegram.tgnet.json;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;


public class TLJsonParser {
    private final JSONObject jsonObject;

    public TLJsonParser(JSONObject jsonObject) {
        this.jsonObject = jsonObject;
    }

    public interface Deserializable {
        void deserializeFromJson(TLJsonParser parser);
    }

    @Nullable
    public <T extends Deserializable> T readObject(String key, Utilities.CallbackReturn<TLJsonParser, T> reader) {
        final JSONObject value = jsonObject.optJSONObject(key);
        if (value != null) {
            return parse(new TLJsonParser(value), reader);
        }
        return null;
    }

    public <T extends Deserializable> ArrayList<T> readVector(String key, Utilities.CallbackReturn<TLJsonParser, T> reader) {
        final ArrayList<T> result = new ArrayList<>();

        final JSONArray value = jsonObject.optJSONArray(key);
        if (value != null) {
            for (int a = 0, N = value.length(); a < N; a++) {
                try {
                    JSONObject o = value.getJSONObject(a);
                    T parsed = parse(new TLJsonParser(o), reader);
                    if (parsed != null) {
                        result.add(parsed);
                    }

                } catch (JSONException e) {
                    FileLog.e(e);
                    if (BuildConfig.DEBUG_PRIVATE_VERSION) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        return result;
    }

    public int readInt32(String key, int defaultValue) {
        return parseInt32(jsonObject.opt(key), defaultValue);
    }

    public long readInt64(String key, int defaultValue) {
        return parseInt64(jsonObject.opt(key), defaultValue);
    }

    public String readString(String key) {
        return readString(key, null);
    }

    public String readString(String key, String defaultValue) {
        return parseString(jsonObject.opt(key), defaultValue);
    }

    public boolean readBoolean(String key, boolean defaultValue) {
        return parseBoolean(jsonObject.opt(key), defaultValue);
    }




    private String parseString(Object value, String defaultValue) {
        if (value instanceof String) {
            return (String) value;
        }

        return defaultValue;
    }

    private boolean parseBoolean(Object value, boolean defaultValue) {
        try {
            if (value instanceof Boolean) {
                return (Boolean) value;
            } else if (value instanceof String) {
                return Boolean.parseBoolean((String) value);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        return defaultValue;
    }

    private long parseInt64(Object value, long defaultValue) {
        try {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            } else if (value instanceof String) {
                return Long.parseLong((String) value, 10);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        return defaultValue;
    }

    private int parseInt32(Object value, int defaultValue) {
        try {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            } else if (value instanceof String) {
                return Integer.parseInt((String) value, 10);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        return defaultValue;
    }

    @Nullable
    private static <T extends Deserializable> T parse(TLJsonParser parser, Utilities.CallbackReturn<TLJsonParser, T> reader) {
        try {
            return reader.run(parser);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }
}

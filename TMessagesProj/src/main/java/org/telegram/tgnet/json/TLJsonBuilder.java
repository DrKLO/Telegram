package org.telegram.tgnet.json;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;

import java.util.List;


public class TLJsonBuilder {
    private TLJsonBuilder() {

    }

    public interface Serializable {
        void serializeToJson(TLJsonBuilder builder);
    }

    public void writeObject(String key, Serializable obj) {
        write(key, serialize(obj));
    }

    public <T extends Serializable> void writeVector(String key, List<T> objects) {
        final JSONArray array = new JSONArray();
        for (Serializable obj : objects) {
            JSONObject value = serialize(obj);
            if (value != null) {
                array.put(value);
            }
        }
        write(key, array);
    }

    public void writeString(String key, String value) {
        write(key, value);
    }

    public void writeInt64(String key, long value) {
        writeString(key, Long.toString(value, 10));
    }

    public void writeInt32(String key, int value) {
        writeString(key, Integer.toString(value, 10));
    }

    public void writeBoolean(String key, boolean value) {
        write(key, value);
    }

    private void write(String key, Object value) {
        try {
            result2.putOpt(key, value);
        } catch (JSONException e) {
            FileLog.e(e);
            if (BuildConfig.DEBUG_PRIVATE_VERSION) {
                throw new RuntimeException(e);
            }
        }
    }

    private final JSONObject result2 = new JSONObject();

    @Nullable
    public static JSONObject serialize(@Nullable Serializable serializable) {
        if (serializable == null) {
            return null;
        }

        try {
            TLJsonBuilder builder = new TLJsonBuilder();
            serializable.serializeToJson(builder);
            return builder.result2;
        } catch (Exception e) {
            FileLog.e(e);
        }

        return null;
    }
}

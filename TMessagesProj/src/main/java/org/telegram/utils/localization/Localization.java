package org.telegram.utils.localization;

import android.content.Context;
import android.util.SparseArray;
import android.util.SparseIntArray;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.telegram.tgnet.SerializedData;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class Localization {
    private static SparseIntArray rawResBindings;

    public static final Localization EMPTY = new Localization();

    private final SparseArray<String> localizations;

    private Localization() {
        localizations = new SparseArray<>();
    }

    private Localization(Builder builder) {
        localizations = builder.localizations != null ?
            builder.localizations : new SparseArray<>();
    }

    public String getByResId(Context context, @StringRes int resId) {
        if (context == null || resId == 0) {
            return null;
        }

        if (rawResBindings == null) {
            rawResBindings = loadStringResBindings(context);
        }
        final int hash = rawResBindings.get(resId);
        if (hash == 0) {
            return null;
        }
        return get(hash);
    }

    public String getByResName(String resName) {
        if (resName != null) {
            return get(resName.hashCode());
        }
        return null;
    }

    public String getByResNameOrResId(Context context, String resName, @StringRes int resId) {
        String value = null;
        if (resName != null) {
            value = getByResName(resName);
        }
        if (value == null && resId != 0) {
            value = getByResId(context, resId);
        }
        return value;
    }

    public String get(int resNameHash) {
        return localizations.get(resNameHash);
    }


    /*  */

    public static class Builder {
        private SparseArray<String> localizations;

        public Builder addResLocalization(Context context, String localizationAssetPath) {
            try {
                localizations = load(context, localizationAssetPath, localizations);
            } catch (Exception ignored) {}
            return this;
        }

        public Builder addLocalization(HashMap<String, String> localization) {
            if (localizations == null) {
                localizations = new SparseArray<>(localization.size());
            }
            for (Map.Entry<String, String> entry : localization.entrySet()) {
                localizations.put(entry.getKey().hashCode(), entry.getValue());
            }
            return this;
        }

        public Builder addLocalization(Localization localization) {
            if (localizations == null) {
                localizations = localization.localizations.clone();
                return this;
            }

            for (int a = 0, N = localization.localizations.size(); a < N; a++) {
                final int key = localization.localizations.keyAt(a);
                final String value = localization.localizations.valueAt(a);
                localizations.put(key, value);
            }
            return this;
        }

        public Localization build() {
            return new Localization(this);
        }
    }

    private static SparseArray<String> load(Context context, String localizationAssetPath, @Nullable SparseArray<String> result) throws IOException {
        try (InputStream stream = new BufferedInputStream(
                context.getAssets().open(localizationAssetPath))) {
            final SerializedData data = new SerializedData(stream);
            final int count = data.readInt32(true);
            if (result == null) {
                result = new SparseArray<>(count);
                for (int a = 0; a < count; a++) {
                    final int hash = data.readInt32(true);
                    final String value = data.readString(true);
                    result.append(hash, value);
                }
            } else {
                for (int a = 0; a < count; a++) {
                    final int hash = data.readInt32(true);
                    final String value = data.readString(true);
                    result.put(hash, value);
                }
            }
            return result;
        }
    }

    private static SparseIntArray loadStringResBindings(Context context) {
        try (InputStream stream = new BufferedInputStream(
                context.getResources().getAssets().open("string_resource_ids.bin"))) {
            final SerializedData data = new SerializedData(stream);
            final int count = data.readInt32(true);

            final SparseIntArray result = new SparseIntArray(count);
            for (int a = 0; a < count; a++) {
                final int resId = data.readInt32(true);
                final int resHash = data.readInt32(true);
                result.append(resId, resHash);
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

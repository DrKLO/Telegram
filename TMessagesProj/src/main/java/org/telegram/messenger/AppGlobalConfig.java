package org.telegram.messenger;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.tgnet.TLRPC;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class AppGlobalConfig {
    private final HashMap<String, ConfigInternal> map = new HashMap<>();

    public final ConfigInt starsPaidMessagesChannelAmountDefault = ofInt("stars_paid_messages_channel_amount_default", 10);

    public final ConfigInt starsSuggestedPostCommissionPermille = ofInt("stars_suggested_post_commission_permille", 850);
    public final ConfigInt starsSuggestedPostAmountMin = ofInt("stars_suggested_post_amount_min", 5);
    public final ConfigInt starsSuggestedPostAmountMax = ofInt("stars_suggested_post_amount_max", 100000);

    public final ConfigInt tonSuggestedPostCommissionPermille = ofInt("ton_suggested_post_commission_permille", 850);
    public final ConfigLong tonSuggestedPostAmountMin = ofLong("ton_suggested_post_amount_min", 10_000_000L); // 0.01 TON
    public final ConfigLong tonSuggestedPostAmountMax = ofLong("ton_suggested_post_amount_max", 10_000_000_000_000L); // 10000 TON

    public final ConfigTime starsSuggestedPostAgeMin = ofTime("stars_suggested_post_age_min", 86400, TimeUnit.SECONDS);
    public final ConfigTime starsSuggestedPostFutureMin = ofTime("stars_suggested_post_future_min", 300, TimeUnit.SECONDS);
    public final ConfigTime starsSuggestedPostFutureMax = ofTime("stars_suggested_post_future_max", 2678400, TimeUnit.SECONDS);

    public final ConfigDouble tonUsdRate = ofDouble("ton_usd_rate", 3);


    /* * */

    public boolean apply(SharedPreferences.Editor editor, TLRPC.TL_jsonObject object) {
        boolean changed = false;

        for (int a = 0, N = object.value.size(); a < N; a++) {
            final TLRPC.TL_jsonObjectValue value = object.value.get(a);
            final ConfigInternal configHandler = map.get(value.key);
            if (configHandler == null) {
                continue;
            }

            changed |= configHandler.apply(editor, value.value);
        }

        return changed;
    }

    public void load(SharedPreferences preferences) {
        for (ConfigInternal configHandler : map.values()) {
            try {
                configHandler.load(preferences);
            } catch (ClassCastException e) {
                FileLog.e(e);
            }
        }
    }



    /* * */

    private interface ConfigInternal {
        boolean apply(SharedPreferences.Editor editor, TLRPC.JSONValue tlValue);
        void load(SharedPreferences preferences);
    }

    public static class ConfigInt {
        private final Internal handler;

        private ConfigInt(String name, int defaultValue) {
            this.handler = new Internal(name, defaultValue);
        }

        public int get() {
            return handler.value;
        }

        private static class Internal implements ConfigInternal {
            private final String name;
            private final int defaultValue;
            private int value;

            private Internal(String name, int defaultValue) {
                this.name = name;
                this.defaultValue = defaultValue;
            }

            @Override
            public boolean apply(SharedPreferences.Editor editor, TLRPC.JSONValue tlValue) {
                if (tlValue instanceof TLRPC.TL_jsonNumber) {
                    TLRPC.TL_jsonNumber num = (TLRPC.TL_jsonNumber) tlValue;
                    if (num.value != value) {
                        value = (int) num.value;
                        editor.putInt(name, value);
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void load(SharedPreferences preferences) {
                value = preferences.getInt(name, defaultValue);
            }
        }
    }

    public static class ConfigLong {
        private final Internal handler;

        private ConfigLong(String name, long defaultValue) {
            this.handler = new Internal(name, defaultValue);
        }

        public long get() {
            return handler.value;
        }

        private static class Internal implements ConfigInternal {
            private final String name;
            private final long defaultValue;
            private long value;

            private Internal(String name, long defaultValue) {
                this.name = name;
                this.defaultValue = defaultValue;
            }

            @Override
            public boolean apply(SharedPreferences.Editor editor, TLRPC.JSONValue tlValue) {
                if (tlValue instanceof TLRPC.TL_jsonNumber) {
                    TLRPC.TL_jsonNumber num = (TLRPC.TL_jsonNumber) tlValue;
                    if (num.value != value) {
                        value = (long) num.value;
                        editor.putLong(name, value);
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void load(SharedPreferences preferences) {
                value = preferences.getLong(name, defaultValue);
            }
        }
    }

    public static class ConfigDouble {
        private final Internal handler;

        private ConfigDouble(String name, double defaultValue) {
            this.handler = new Internal(name, defaultValue);
        }

        public double get() {
            return handler.value;
        }

        private static class Internal implements ConfigInternal {
            private final String name;
            private final double defaultValue;
            private double value;

            private Internal(String name, double defaultValue) {
                this.name = name;
                this.defaultValue = defaultValue;
            }

            @Override
            public boolean apply(SharedPreferences.Editor editor, TLRPC.JSONValue tlValue) {
                if (tlValue instanceof TLRPC.TL_jsonNumber) {
                    TLRPC.TL_jsonNumber num = (TLRPC.TL_jsonNumber) tlValue;
                    if (num.value != value) {
                        value = num.value;
                        editor.putFloat(name, (float) value);
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void load(SharedPreferences preferences) {
                value = preferences.getFloat(name, (float) defaultValue);
            }
        }
    }

    public static class ConfigString {
        private final Internal handler;

        private ConfigString(String name, String defaultValue) {
            this.handler = new Internal(name, defaultValue);
        }

        public String get() {
            return handler.value;
        }

        public boolean is(String value) {
            return TextUtils.equals(get(), value);
        }

        private static class Internal implements ConfigInternal {
            private final String name;
            private final String defaultValue;
            private String value;

            private Internal(String name, String defaultValue) {
                this.name = name;
                this.defaultValue = defaultValue;
            }

            @Override
            public boolean apply(SharedPreferences.Editor editor, TLRPC.JSONValue tlValue) {
                if (tlValue instanceof TLRPC.TL_jsonString) {
                    TLRPC.TL_jsonString num = (TLRPC.TL_jsonString) tlValue;
                    if (!TextUtils.equals(num.value, value)) {
                        value = num.value;
                        editor.putString(name, value);
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void load(SharedPreferences preferences) {
                value = preferences.getString(name, defaultValue);
            }
        }
    }

    public static class ConfigBoolean {
        private final Internal handler;

        private ConfigBoolean(String name, boolean defaultValue) {
            this.handler = new Internal(name, defaultValue);
        }

        public boolean get() {
            return handler.value;
        }

        private static class Internal implements ConfigInternal {
            private final String name;
            private final boolean defaultValue;
            private boolean value;

            private Internal(String name, boolean defaultValue) {
                this.name = name;
                this.defaultValue = defaultValue;
            }

            @Override
            public boolean apply(SharedPreferences.Editor editor, TLRPC.JSONValue tlValue) {
                if (tlValue instanceof TLRPC.TL_jsonBool) {
                    TLRPC.TL_jsonBool num = (TLRPC.TL_jsonBool) tlValue;
                    if (num.value != value) {
                        value = num.value;
                        editor.putBoolean(name, value);
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void load(SharedPreferences preferences) {
                value = preferences.getBoolean(name, defaultValue);
            }
        }
    }

    public static class ConfigTime {
        private final ConfigLong.Internal handler;
        private final TimeUnit timeUnit;

        private ConfigTime(String name, TimeUnit timeUnit, long defaultValue) {
            this.handler = new ConfigLong.Internal(name, defaultValue);
            this.timeUnit = timeUnit;
        }

        public long get(TimeUnit timeUnit) {
            return timeUnit.convert(handler.value, this.timeUnit);
        }
    }

    private ConfigInt ofInt(String name, int defaultValue) {
        ConfigInt configInt = new ConfigInt(name, defaultValue);
        map.put(name, configInt.handler);

        return configInt;
    }

    private ConfigLong ofLong(String name, long defaultValue) {
        ConfigLong configInt = new ConfigLong(name, defaultValue);
        map.put(name, configInt.handler);

        return configInt;
    }

    private ConfigDouble ofDouble(String name, double defaultValue) {
        ConfigDouble configDouble = new ConfigDouble(name, defaultValue);
        map.put(name, configDouble.handler);

        return configDouble;
    }

    private ConfigBoolean ofBoolean(String name, boolean defaultValue) {
        ConfigBoolean configBoolean = new ConfigBoolean(name, defaultValue);
        map.put(name, configBoolean.handler);

        return configBoolean;
    }

    private ConfigString ofString(String name, String defaultValue) {
        ConfigString config = new ConfigString(name, defaultValue);
        map.put(name, config.handler);

        return config;
    }

    private ConfigTime ofTime(String name, long defaultValue, TimeUnit timeUnit) {
        ConfigTime configInt = new ConfigTime(name, timeUnit, defaultValue);
        map.put(name, configInt.handler);

        return configInt;
    }
}

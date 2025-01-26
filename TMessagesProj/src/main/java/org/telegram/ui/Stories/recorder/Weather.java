package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.PermissionRequest;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.DarkThemeResourceProvider;


import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class Weather {

//    public static String[] emojis = new String[] {
//        "☀", // Clear sky
//        "🌤", // Mainly clear
//        "⛅", // Partly cloudy
//        "☁", // Overcast
//        "😶‍🌫", // Fog
//        "😶‍🌫", // Depositing rime fog
//        "🌦", // Light drizzle
//        "🌧", // Moderate drizzle
//        "🌧", // Dense drizzle
//        "❄", // Light freezing drizzle
//        "❄", // Dense freezing drizzle
//        "🌦", // Slight rain
//        "🌧", // Moderate rain
//        "🌧", // Heavy rain
//        "❄", // Light freezing rain
//        "❄", // Heavy freezing rain
//        "🌨", // Slight snow fall
//        "🌨", // Moderate snow fall
//        "❄", // Heavy snow fall
//        "🌨", // Snow grains
//        "🌦", // Slight rain showers
//        "🌧", // Moderate rain showers
//        "⛈", // Violent rain showers
//        "🌨", // Slight snow showers
//        "❄", // Heavy snow showers
//        "⚡", // Thunderstorm
//        "⚡"  // Thunderstorm with slight hail
//    };
//    public static int[] emojiKeys = new int[] {
//        0,  // Clear sky
//        1,  // Mainly clear
//        2,  // Partly cloudy
//        3,  // Overcast
//        45, // Fog
//        48, // Depositing rime fog
//        51, // Light drizzle
//        53, // Moderate drizzle
//        55, // Dense drizzle
//        56, // Light freezing drizzle
//        57, // Dense freezing drizzle
//        61, // Slight rain
//        63, // Moderate rain
//        65, // Heavy rain
//        66, // Light freezing rain
//        67, // Heavy freezing rain
//        71, // Slight snow fall
//        73, // Moderate snow fall
//        75, // Heavy snow fall
//        77, // Snow grains
//        80, // Slight rain showers
//        81, // Moderate rain showers
//        82, // Violent rain showers
//        85, // Slight snow showers
//        86, // Heavy snow showers
//        95, // Thunderstorm
//        96, // Thunderstorm with slight hail
//    };
//    public static String[] moonEmojis = new String[] {
//        "🌚", // New Moon
//        "🌛", // Waxing Crescent
//        "🌓", // First Quarter
//        "🌔", // Waxing Gibbous
//        "🌝", // Full Moon
//        "🌖", // Waning Gibbous
//        "🌗", // Last Quarter
//        "🌜", // Waning Crescent
//        "🌚"  // New Moon
//    };

//    public static String getEmoji(int type, double lat, double lng) {
//        if (type == 0 || type == 1 || type == 2) {
//            final Date now = new Date();
//            if (!isDay(lat, lng, LocalDateTime.now(ZoneOffset.UTC))) {
//                return getMoonPhaseEmoji(now);
//            }
//        }
//        for (int i = emojiKeys.length - 1; i >= 0; --i) {
//            if (type >= emojiKeys[i]) {
//                return emojis[i];
//            }
//        }
//        return emojis[0];
//    }
//
//    private static final double J1970 = 2440588;
//
//    public static String getMoonPhaseEmoji(Date date) {
//        double julianDate = toJulianDate(date);
//
//        double referenceNewMoon = 2451550.1; // January 6, 2000
//        double synodicMonth = 29.53058867; // Average length of a synodic month
//
//        double daysSinceNewMoon = julianDate - referenceNewMoon;
//        double newMoons = daysSinceNewMoon / synodicMonth;
//        double currentMoonPhase = (newMoons - Math.floor(newMoons)) * synodicMonth;
//
//        if (currentMoonPhase < 1.84566) {
//            return moonEmojis[0]; // New Moon
//        } else if (currentMoonPhase < 5.53699) {
//            return moonEmojis[1]; // Waxing Crescent
//        } else if (currentMoonPhase < 9.22831) {
//            return moonEmojis[2]; // First Quarter
//        } else if (currentMoonPhase < 12.91963) {
//            return moonEmojis[3]; // Waxing Gibbous
//        } else if (currentMoonPhase < 16.61096) {
//            return moonEmojis[4]; // Full Moon
//        } else if (currentMoonPhase < 20.30228) {
//            return moonEmojis[5]; // Waning Gibbous
//        } else if (currentMoonPhase < 23.99361) {
//            return moonEmojis[6]; // Last Quarter
//        } else if (currentMoonPhase < 27.68493) {
//            return moonEmojis[7]; // Waning Crescent
//        } else {
//            return moonEmojis[8]; // New Moon
//        }
//    }
//
//    public static boolean isDay(double latitude, double longitude, LocalDateTime dateTime) {
//        LocalDate date = dateTime.toLocalDate();
//        LocalTime time = dateTime.toLocalTime();
//
//        double sunrise = calculateSunrise(latitude, longitude, date);
//        double sunset = calculateSunset(latitude, longitude, date);
//
//        return time.isAfter(LocalTime.ofSecondOfDay((long) (sunrise * 3600))) &&
//                time.isBefore(LocalTime.ofSecondOfDay((long) (sunset * 3600)));
//    }
//
//    public static double calculateSunrise(double latitude, double longitude, LocalDate date) {
//        return calculateSunTime(latitude, longitude, date, true);
//    }
//
//    public static double calculateSunset(double latitude, double longitude, LocalDate date) {
//        return calculateSunTime(latitude, longitude, date, false);
//    }
//
//    public static double calculateSunTime(double latitude, double longitude, LocalDate date, boolean isSunrise) {
//        int dayOfYear = date.getDayOfYear();
//        double zenith = 90.833; // Official zenith for sunrise/sunset
//
//        double D2R = Math.PI / 180.0;
//        double R2D = 180.0 / Math.PI;
//
//        double lngHour = longitude / 15.0;
//        double t = dayOfYear + ((isSunrise ? 6 : 18) - lngHour) / 24.0;
//
//        double M = (0.9856 * t) - 3.289;
//        double L = M + (1.916 * Math.sin(M * D2R)) + (0.020 * Math.sin(2 * M * D2R)) + 282.634;
//
//        if (L > 360.0) {
//            L -= 360.0;
//        } else if (L < 0.0) {
//            L += 360.0;
//        }
//
//        double RA = R2D * Math.atan(0.91764 * Math.tan(L * D2R));
//        if (RA > 360.0) {
//            RA -= 360.0;
//        } else if (RA < 0.0) {
//            RA += 360.0;
//        }
//
//        double Lquadrant = (Math.floor(L / 90.0)) * 90.0;
//        double RAquadrant = (Math.floor(RA / 90.0)) * 90.0;
//        RA += (Lquadrant - RAquadrant);
//        RA /= 15.0;
//
//        double sinDec = 0.39782 * Math.sin(L * D2R);
//        double cosDec = Math.cos(Math.asin(sinDec));
//
//        double cosH = (Math.cos(zenith * D2R) - (sinDec * Math.sin(latitude * D2R))) / (cosDec * Math.cos(latitude * D2R));
//        if (cosH > 1.0) {
//            return -1;
//        } else if (cosH < -1.0) {
//            return -1;
//        }
//
//        double H = isSunrise ? (360.0 - R2D * Math.acos(cosH)) : R2D * Math.acos(cosH);
//        H /= 15.0;
//
//        double T = H + RA - (0.06571 * t) - 6.622;
//        double UT = T - lngHour;
//
//        if (UT > 24.0) {
//            UT -= 24.0;
//        } else if (UT < 0.0) {
//            UT += 24.0;
//        }
//        return UT;
//    }
//
//    private static double toJulianDate(Date date) {
//        Calendar calendar = new GregorianCalendar();
//        calendar.setTime(date);
//        return toJulianDate(calendar);
//    }
//
//    private static double toJulianDate(Calendar calendar) {
//        return calendar.getTimeInMillis() / 86400000.0 + J1970 - 0.5;
//    }

    public static boolean isDefaultCelsius() {
        final String timezone = TimeZone.getDefault().getID();
        return !(
            timezone.startsWith("US/") ||
            "America/Nassau".equals(timezone) ||
            "America/Belize".equals(timezone) ||
            "America/Cayman".equals(timezone) ||
            "Pacific/Palau".equals(timezone)
        );
    }

    public static class State extends TLObject {
        public double lat, lng;

//        public int type;
        public String emoji;
        public float temperature; // in celsius

        public String getEmoji() {
//            return Weather.getEmoji(type, lat, lng);
            return emoji;
        }

        public String getTemperature() {
            return getTemperature(isDefaultCelsius());
        }

        public String getTemperature(boolean celsius) {
            if (celsius) {
                return (int) Math.round(temperature) + "°C";
            } else {
                return (int) Math.round((this.temperature * 9.0 / 5.0) + 32) + "°F";
            }
        }

        public static Weather.State TLdeserialize(AbstractSerializedData stream) {
            Weather.State state = new Weather.State();
            state.lat = stream.readDouble(false);
            state.lng = stream.readDouble(false);
//            state.type = stream.readInt32(false);
            state.emoji = stream.readString(false);
            state.temperature = stream.readFloat(false);
            return state;
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeDouble(lat);
            stream.writeDouble(lng);
//            stream.writeInt32(type);
            stream.writeString(emoji);
            stream.writeFloat(temperature);
        }
    }

    public static void fetch(boolean withProgress, Utilities.Callback<State> whenFetched) {
        if (whenFetched == null) return;
        getUserLocation(withProgress, location -> {
            if (location == null) {
                whenFetched.run(null);
                return;
            }

            Activity activity = LaunchActivity.instance;
            if (activity == null) activity = AndroidUtilities.findActivity(ApplicationLoader.applicationContext);
            if (activity == null || activity.isFinishing()) {
                whenFetched.run(null);
                return;
            }

            final AlertDialog progressDialog = withProgress ? new AlertDialog(activity, AlertDialog.ALERT_TYPE_SPINNER, new DarkThemeResourceProvider()) : null;
            if (withProgress) progressDialog.showDelayed(200);
            Runnable cancel = fetch(location.getLatitude(), location.getLongitude(), weather -> {
                if (withProgress) {
                    progressDialog.dismissUnless(350);
                }
                whenFetched.run(weather);
            });
            if (withProgress && cancel != null) {
                progressDialog.setOnCancelListener(di -> cancel.run());
            }
        });
    }

    private static String cacheKey;
    private static State cacheValue;

    public static State getCached() {
        return cacheValue;
    }

//    public static Runnable fetch(double lat, double lng, Utilities.Callback<State> whenFetched) {
//        if (whenFetched == null) return null;
//
//        final Date date = new Date();
//        final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
//        calendar.setTime(date);
//        final long hours = calendar.getTimeInMillis() / 1_000L / 60L / 60L;
//        final String key = Math.round(lat * 1000) + ":" + Math.round(lng * 1000) + "at" + hours;
//        if (cacheValue != null && TextUtils.equals(cacheKey, key)) {
//            whenFetched.run(cacheValue);
//            return null;
//        }
//
//        AsyncTask task = new HttpGetTask(result -> {
//            try {
//                final JSONObject obj = new JSONObject(result);
//                final JSONObject current_weather = obj.getJSONObject("current_weather");
//                final int type = current_weather.getInt("weathercode");
//                int temperature = current_weather.getInt("temperature");
//                final JSONObject current_weather_units = obj.getJSONObject("current_weather_units");
//                if (current_weather_units.getString("temperature").equals("°F")) {
//                    temperature = (int) Math.round((temperature - 32) * 5.0 / 9.0);
//                }
//
//                final State state = new State();
//                state.lat = lat;
//                state.lng = lng;
//                state.type = type;
//                state.temperature = temperature;
//
//                cacheKey = key;
//                cacheValue = state;
//
//                whenFetched.run(state);
//
//            } catch (Exception e) {
//                FileLog.e(e);
//            }
//        }).execute(
//            "https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lng+"&current_weather=true"
//        );
//
//        return () -> task.cancel(true);
//    }

    public static Runnable fetch(double lat, double lng, Utilities.Callback<State> whenFetched) {
        if (whenFetched == null) return null;

        final Date date = new Date();
        final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.setTime(date);
        final long hours = calendar.getTimeInMillis() / 1_000L / 60L / 60L;
        final String key = Math.round(lat * 1000) + ":" + Math.round(lng * 1000) + "at" + hours;
        if (cacheValue != null && TextUtils.equals(cacheKey, key)) {
            whenFetched.run(cacheValue);
            return null;
        }

        final int[] currentReqId = new int[1];

        final MessagesController messagesController = MessagesController.getInstance(UserConfig.selectedAccount);
        final ConnectionsManager connectionsManager = ConnectionsManager.getInstance(UserConfig.selectedAccount);
        final String username = messagesController.weatherSearchUsername;

        final TLRPC.User[] bot = new TLRPC.User[] { messagesController.getUser(username) };
        Runnable request = () -> {
            TLRPC.TL_messages_getInlineBotResults req2 = new TLRPC.TL_messages_getInlineBotResults();
            req2.bot = messagesController.getInputUser(bot[0]);
            req2.query = "";
            req2.offset = "";
            req2.flags |= 1;
            req2.geo_point = new TLRPC.TL_inputGeoPoint();
            req2.geo_point.lat = lat;
            req2.geo_point._long = lng;
            req2.peer = new TLRPC.TL_inputPeerEmpty();

            currentReqId[0] = connectionsManager.sendRequest(req2, (res2, err2) -> AndroidUtilities.runOnUIThread(() -> {
                currentReqId[0] = 0;
                if (res2 instanceof TLRPC.messages_BotResults) {
                    TLRPC.messages_BotResults r = (TLRPC.messages_BotResults) res2;
                    if (!r.results.isEmpty()) {
                        TLRPC.BotInlineResult rr = r.results.get(0);
                        final String emoji = rr.title;
                        final float temp;
                        try {
                            temp = Float.parseFloat(rr.description);
                        } catch (Exception e) {
                            whenFetched.run(null);
                            return;
                        }
                        final State state = new State();
                        state.lat = lat;
                        state.lng = lng;
                        state.emoji = emoji;
                        state.temperature = temp;

                        cacheKey = key;
                        cacheValue = state;

                        whenFetched.run(state);
                        return;
                    }
                }
                whenFetched.run(null);
            }));
        };

        if (bot[0] == null) {
            TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
            req.username = username;
            currentReqId[0] = connectionsManager.sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                currentReqId[0] = 0;
                if (res instanceof TLRPC.TL_contacts_resolvedPeer) {
                    TLRPC.TL_contacts_resolvedPeer r = (TLRPC.TL_contacts_resolvedPeer) res;
                    messagesController.putUsers(r.users, false);
                    messagesController.putChats(r.chats, false);
                    long uid = DialogObject.getPeerDialogId(r.peer);
                    bot[0] = messagesController.getUser(uid);
                    if (bot[0] != null) {
                        request.run();
                        return;
                    }
                }
                whenFetched.run(null);
            }));
        } else {
            request.run();
        }

        return () -> {
            if (currentReqId[0] != 0) {
                connectionsManager.cancelRequest(currentReqId[0], true);
                currentReqId[0] = 0;
            }
        };
    }

    @SuppressLint("MissingPermission")
    public static void getUserLocation(boolean withProgress, Utilities.Callback<Location> whenGot) {
        if (whenGot == null) return;

        PermissionRequest.ensureEitherPermission(R.raw.permission_request_location, R.string.PermissionNoLocationStory,
            new String[] { Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION },
            new String[] { Manifest.permission.ACCESS_COARSE_LOCATION }, granted -> {

            if (!granted) {
                whenGot.run(null);
                return;
            }

            LocationManager lm = (LocationManager) ApplicationLoader.applicationContext.getSystemService(Context.LOCATION_SERVICE);
            List<String> providers = lm.getProviders(true);
            Location l = null;
            for (int i = providers.size() - 1; i >= 0; i--) {
                l = lm.getLastKnownLocation(providers.get(i));
                if (l != null) {
                    break;
                }
            }
            if (l == null && withProgress) {
                if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    Context context = LaunchActivity.instance;
                    if (context == null) context = ApplicationLoader.applicationContext;
                    if (context != null) {
                        try {
                            final Context finalContext = context;
                            AlertDialog.Builder builder = new AlertDialog.Builder(context);
                            builder.setTopAnimation(R.raw.permission_request_location, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground));
                            builder.setMessage(getString(R.string.GpsDisabledAlertText));
                            builder.setPositiveButton(getString(R.string.Enable), (dialog, id) -> {
                                try {
                                    finalContext.startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                                } catch (Exception ignore) {
                                }
                            });
                            builder.setNegativeButton(getString(R.string.Cancel), null);
                            builder.show();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                } else {
                    try {
                        final Utilities.Callback<Location>[] callback = new Utilities.Callback[] { whenGot };
                        final LocationListener[] listenerArr = new LocationListener[] { null };
                        final LocationListener listener = location -> {
                            if (listenerArr[0] != null) {
                                lm.removeUpdates(listenerArr[0]);
                                listenerArr[0] = null;
                            }
                            if (callback[0] != null) {
                                callback[0].run(location);
                                callback[0] = null;
                            }
                        };
                        listenerArr[0] = listener;
                        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1, 0, listener);
                    } catch (Exception e) {
                        FileLog.e(e);
                        whenGot.run(null);
                    }
                    return;
                }
            }
            whenGot.run(l);
        });
    }

}

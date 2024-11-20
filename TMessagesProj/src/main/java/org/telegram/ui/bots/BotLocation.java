package org.telegram.ui.bots;

import static org.telegram.messenger.AndroidUtilities.calcBitmapColor;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.util.Pair;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.checkerframework.checker.units.qual.A;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AttachableDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.PermissionRequest;
import org.telegram.ui.Components.Text;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProfileActivity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.WeakHashMap;

public class BotLocation {

    public static final String PREF = "botlocation_";

    public final Context context;
    public final int currentAccount;
    public final long botId;

    public boolean requested;
    public boolean granted;

    private final static HashMap<Pair<Integer, Long>, BotLocation> instances = new HashMap<>();

    public static BotLocation get(Context context, int currentAccount, long botId) {
        final Pair<Integer, Long> key = new Pair<>(currentAccount, botId);
        BotLocation instance = instances.get(key);
        if (instance == null) {
            instances.put(key, instance = new BotLocation(context, currentAccount, botId));
        }
        return instance;
    }

    private BotLocation(Context context, int currentAccount, long botId) {
        this.context = context;
        this.currentAccount = currentAccount;
        this.botId = botId;
        load();
    }

    public boolean asked() {
        return requested;
    }

    public boolean granted() {
        return appHasPermission() && granted;
    }

    public void setGranted(boolean granted, Runnable whenDone) {
        this.requested = true;
        if (granted && !appHasPermission()) {
            final Activity activity = getActivity();
            if (activity == null) return;
            final TLRPC.User bot = MessagesController.getInstance(currentAccount).getUser(botId);
            final AlertDialog.Builder b = new AlertDialog.Builder(getActivity(), null);
            b.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.BotLocationPermissionRequest, UserObject.getUserName(bot), UserObject.getUserName(bot))));
            b.setTopImage(new BotUserLocationDrawable(context, UserConfig.getInstance(currentAccount).getCurrentUser(), bot), Theme.getColor(Theme.key_dialogTopBackground));
            if (needToOpenSettings()) {
                b.setPositiveButton(LocaleController.getString(R.string.BotLocationPermissionSettings), (di, w) -> {
                    try {
                        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                        activity.startActivity(intent);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                });
            } else {
                b.setPositiveButton(LocaleController.getString(R.string.BotLocationPermissionAllow), (di, w) -> {
                    if (!appHasPermission()) {
                        PermissionRequest.requestPermissions(new String[] { Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION }, _granted -> {
                            boolean someGranted = false;
                            for (int i = 0; i < _granted.length; ++i) {
                                if (_granted[i] == PackageManager.PERMISSION_GRANTED) {
                                    someGranted = true;
                                }
                            }
                            this.requested = someGranted;
                            this.granted = someGranted;
                            this.save();
                            for (Runnable listener : listeners) {
                                listener.run();
                            }
                            if (whenDone != null) {
                                whenDone.run();
                            }
                        });
                    } else {
                        this.requested = true;
                        this.granted = true;
                        this.save();
                        for (Runnable listener : listeners) {
                            listener.run();
                        }
                    }
                });
            }
            b.setNegativeButton(LocaleController.getString(R.string.BotLocationPermissionDecline), (di, w) -> {
                this.requested = true;
                this.granted = false;
                this.save();
                for (Runnable listener : listeners) {
                    listener.run();
                }
                if (whenDone != null) {
                    whenDone.run();
                }
            });
            b.show();
        } else {
            this.granted = granted;
            for (Runnable listener : listeners) {
                listener.run();
            }
            if (whenDone != null) {
                whenDone.run();
            }
        }
        save();
    }

    private final HashSet<Runnable> listeners = new HashSet<>();
    public void listen(Runnable grantedChanged) {
        listeners.add(grantedChanged);
    }
    public void unlisten(Runnable grantedChanged) {
        listeners.remove(grantedChanged);
    }

    public void load() {
        SharedPreferences prefs = context.getSharedPreferences(PREF + currentAccount, Activity.MODE_PRIVATE);
        requested = prefs.getBoolean(botId + "_requested", false);
        granted = prefs.getBoolean(botId + "_granted", false);
        if (granted && !appHasPermission()) {
            granted = false;
            requested = false;
            save();
            for (Runnable listener : listeners) {
                listener.run();
            }
        }
    }

    public void save() {
        final SharedPreferences prefs = context.getSharedPreferences(PREF + currentAccount, Activity.MODE_PRIVATE);
        final SharedPreferences.Editor edit = prefs.edit();
        edit.putBoolean(botId + "_granted", granted);
        edit.putBoolean(botId + "_requested", requested);
        edit.apply();
    }

    private Activity getActivity() {
        Activity _activity = LaunchActivity.instance;
        if (_activity == null)
            _activity = AndroidUtilities.findActivity(context);
        if (_activity == null)
            _activity = AndroidUtilities.findActivity(ApplicationLoader.applicationContext);
        return _activity;
    }

    private boolean deviceHasLocation() {
        return getActivity() != null && getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
    }

    private boolean appHasPermission() {
        final Activity activity = getActivity();
        return (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            activity != null && (
                activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            )
        );
    }

    private boolean needToOpenSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;
        final Activity activity = getActivity();
        if (activity == null) return false;
        return (
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) ||
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
        );
    }

    public void request(Utilities.Callback2<Boolean, Boolean> whenDone) {
        final Activity activity = getActivity();
        if (activity == null) return;

        if (!deviceHasLocation()) {
            if (whenDone != null) {
                whenDone.run(false, false);
            }
            return;
        }

        if (!appHasPermission() || !requested && !granted) {
            final boolean[] sent = new boolean[1];
            final TLRPC.User bot = MessagesController.getInstance(currentAccount).getUser(botId);
            final AlertDialog.Builder b = new AlertDialog.Builder(activity, null);
            b.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.BotLocationPermissionRequest, UserObject.getUserName(bot), UserObject.getUserName(bot))));
            b.setTopImage(new BotUserLocationDrawable(context, UserConfig.getInstance(currentAccount).getCurrentUser(), bot), Theme.getColor(Theme.key_dialogTopBackground));
            if (!appHasPermission() && needToOpenSettings()) {
                b.setPositiveButton(LocaleController.getString(R.string.BotLocationPermissionSettings), (di, w) -> {
                    try {
                        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                        activity.startActivity(intent);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    sent[0] = true;
                    if (whenDone != null) {
                        whenDone.run(false, false);
                    }
                });
            } else {
                b.setPositiveButton(LocaleController.getString(R.string.BotLocationPermissionAllow), (di, w) -> {
                    sent[0] = true;
                    if (!appHasPermission()) {
                        PermissionRequest.requestPermissions(new String[] { Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION }, granted -> {
                            boolean someGranted = false;
                            for (int i = 0; i < granted.length; ++i) {
                                if (granted[i] == PackageManager.PERMISSION_GRANTED) {
                                    someGranted = true;
                                }
                            }
                            this.requested = true;
                            this.granted = true;
                            this.save();
                            for (Runnable listener : listeners) {
                                listener.run();
                            }
                            if (whenDone != null) {
                                whenDone.run(true, someGranted);
                            }
                        });
                    } else {
                        this.requested = true;
                        this.granted = true;
                        this.save();
                        for (Runnable listener : listeners) {
                            listener.run();
                        }
                        if (whenDone != null) {
                            whenDone.run(true, true);
                        }
                    }
                });
            }
            b.setNegativeButton(LocaleController.getString(R.string.BotLocationPermissionDecline), (di, w) -> {
                if (sent[0]) return;
                sent[0] = true;
                this.requested = true;
                this.granted = false;
                this.save();
                for (Runnable listener : listeners) {
                    listener.run();
                }
                if (whenDone != null) {
                    whenDone.run(true, false);
                }
            });
            b.setOnDismissListener((di) -> {
                if (!sent[0]) {
                    this.requested = true;
                    this.granted = false;
                    this.save();
                    for (Runnable listener : listeners) {
                        listener.run();
                    }
                    sent[0] = true;
                    if (whenDone != null) {
                        whenDone.run(true, false);
                    }
                }
            });
            b.show();
            return;
        }

        if (whenDone != null) {
            whenDone.run(false, true);
        }
    }

    public JSONObject checkObject() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("available", deviceHasLocation());
            if (deviceHasLocation()) {
                obj.put("access_requested", requested);
                if (requested) {
                    obj.put("access_granted", granted && appHasPermission());
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return obj;
    }

    public void requestObject(Utilities.Callback<JSONObject> whenDone) {
        if (whenDone == null) return;

        final JSONObject obj = new JSONObject();
        final boolean available = granted && appHasPermission() && deviceHasLocation();
        if (!available) {
            try {
                obj.put("available", false);
            } catch (Exception e) {
                FileLog.e(e);
            }
            whenDone.run(obj);
            return;
        }

        final LocationManager lm = (LocationManager) ApplicationLoader.applicationContext.getSystemService(Context.LOCATION_SERVICE);
        List<String> providers = lm.getProviders(true);
        Location l = null;
        for (int i = providers.size() - 1; i >= 0; i--) {
            l = lm.getLastKnownLocation(providers.get(i));
            if (l != null) {
                break;
            }
        }

        if (l == null && !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
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
                        } catch (Exception ignore) {}
                    });
                    builder.setNegativeButton(getString(R.string.Cancel), null);
                    builder.show();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            whenDone.run(locationObject(null));
            return;
        }

        if (l != null) {
            whenDone.run(locationObject(l));
            return;
        }

        try {
            final LocationListener[] listener = new LocationListener[1];
            listener[0] = new LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location location) {
                    lm.removeUpdates(listener[0]);
                    whenDone.run(locationObject(location));
                }
            };
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1, 0, listener[0]);
        } catch (Exception e) {
            FileLog.e(e);
            whenDone.run(locationObject(null));
        }
    }

    private JSONObject locationObject(Location location) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("available", location != null);
            if (location == null) return obj;

            obj.put("latitude", location.getLatitude());
            obj.put("longitude", location.getLongitude());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                obj.put("horizontal_accuracy", location.getAccuracy());
            } else {
                obj.put("horizontal_accuracy", null);
            }
            obj.put("altitude", location.getAltitude());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                obj.put("vertical_accuracy", location.getVerticalAccuracyMeters());
            } else {
                obj.put("vertical_accuracy", null);
            }
            obj.put("course", location.getBearing());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                obj.put("course_accuracy", location.getBearingAccuracyDegrees());
            } else {
                obj.put("course_accuracy", null);
            }
            obj.put("speed", location.getSpeed());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                obj.put("speed_accuracy", location.getSpeedAccuracyMetersPerSecond());
            } else {
                obj.put("speed_accuracy", null);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return obj;
    }

    public static class BotUserLocationDrawable extends Drawable implements AttachableDrawable {

        private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ImageReceiver userImageReceiver = new ImageReceiver();
        private final ImageReceiver botImageReceiver = new ImageReceiver();
        private final Drawable locationDrawable;
        private final RectF rect = new RectF();

        public BotUserLocationDrawable(Context context, TLRPC.User user, TLRPC.User bot) {

            arrowPaint.setColor(0xFFFFFFFF);
            arrowPaint.setStyle(Paint.Style.STROKE);
            arrowPaint.setStrokeWidth(dp(2));
            arrowPaint.setStrokeJoin(Paint.Join.ROUND);
            arrowPaint.setStrokeCap(Paint.Cap.ROUND);
            whitePaint.setColor(0xFFFFFFFF);

            locationDrawable = context.getResources().getDrawable(R.drawable.filled_location).mutate();
            locationDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTopBackground), PorterDuff.Mode.SRC_IN));

            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(user);
            userImageReceiver.setForUserOrChat(user, avatarDrawable);
            userImageReceiver.setRoundRadius(dp(25));

            avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(bot);
            botImageReceiver.setForUserOrChat(bot, avatarDrawable);
            botImageReceiver.setRoundRadius(dp(25));
        }

        @Override
        public void onAttachedToWindow(ImageReceiver parent) {
            userImageReceiver.onAttachedToWindow();
            botImageReceiver.onAttachedToWindow();
        }

        @Override
        public void onDetachedFromWindow(ImageReceiver parent) {
            userImageReceiver.onDetachedFromWindow();
            botImageReceiver.onDetachedFromWindow();
        }

        @Override
        public void setParent(View view) {
            botImageReceiver.setParentView(view);
            userImageReceiver.setParentView(view);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final Rect bounds = getBounds();

            bgPaint.setColor(Theme.getColor(Theme.key_dialogTopBackground));

            final float width = dp(50 + 36 + 50);

            userImageReceiver.setImageCoords(bounds.centerX() - width / 2f, bounds.centerY() - dp(25), dp(50), dp(50));
            userImageReceiver.draw(canvas);

            final float lcx = bounds.centerX() - width / 2f + dp(25 + 16), lcy = bounds.centerY() + dp(16);
            canvas.drawCircle(lcx, lcy, dp(14), bgPaint);
            canvas.drawCircle(lcx, lcy, dp(12), whitePaint);
            locationDrawable.setBounds((int) (lcx - dp(9)), (int) (lcy - dp(9)), (int) (lcx + dp(9)), (int) (lcy + dp(9)));
            locationDrawable.draw(canvas);

            canvas.drawLine(bounds.centerX() - dp(3.33f), bounds.centerY() - dp(7), bounds.centerX() + dp(3.33f), bounds.centerY(), arrowPaint);
            canvas.drawLine(bounds.centerX() - dp(3.33f), bounds.centerY() + dp(7), bounds.centerX() + dp(3.33f), bounds.centerY(), arrowPaint);

            botImageReceiver.setImageCoords(bounds.centerX() + width / 2f - dp(50), bounds.centerY() - dp(25), dp(50), dp(50));
            botImageReceiver.draw(canvas);
        }

        @Override
        public void setAlpha(int alpha) {

        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }
    }

    public static void clear() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) return;
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; ++i) {
            final SharedPreferences prefs = context.getSharedPreferences(PREF + i, Activity.MODE_PRIVATE);
            prefs.edit().clear().apply();
        }
        instances.clear();
    }

}

package org.telegram.ui.Components;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LaunchActivity;

public class PermissionRequest {

    private static int lastId = 1500;

    public static void ensurePermission(int iconResId, int stringResId, String permission) {
        ensurePermission(iconResId, stringResId, permission, null);
    }

    public static void ensurePermission(int iconResId, int stringResId, String permission, Utilities.Callback<Boolean> whenDone) {
        ensureEitherPermission(iconResId, stringResId, new String[] { permission }, new String[] { permission }, whenDone);
    }

    public static void ensureEitherPermission(int iconResId, int stringResId, String[] permissions, Utilities.Callback<Boolean> whenDone) {
        ensureEitherPermission(iconResId, stringResId, permissions, permissions, whenDone);
    }

    public static void ensureEitherPermission(int iconResId, int stringResId, String[] checkPermissions, String[] requestPermissions, Utilities.Callback<Boolean> whenDone) {
        Activity _activity = LaunchActivity.instance;
        if (_activity == null) _activity = AndroidUtilities.findActivity(ApplicationLoader.applicationContext);
        if (_activity == null) return;
        final Activity activity = _activity;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean granted = false;
            for (String permission : checkPermissions) {
                if (activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }
            if (granted) {
                if (whenDone != null) whenDone.run(true);
                return;
            }
            boolean needsPermissionRationale = false;
            for (String permission : checkPermissions) {
                if (activity.shouldShowRequestPermissionRationale(permission)) {
                    needsPermissionRationale = true;
                    break;
                }
            }
            if (needsPermissionRationale) {
                new AlertDialog.Builder(activity, null)
                    .setTopAnimation(iconResId, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground))
                    .setMessage(AndroidUtilities.replaceTags(LocaleController.getString(stringResId)))
                    .setPositiveButton(LocaleController.getString(R.string.PermissionOpenSettings), (dialogInterface, i) -> {
                        try {
                            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                            activity.startActivity(intent);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    })
                    .setNegativeButton(LocaleController.getString(R.string.ContactsPermissionAlertNotNow), null)
                    .create()
                    .show();
                if (whenDone != null) whenDone.run(false);
                return;
            }
            requestPermissions(requestPermissions, ids -> {
                boolean now_granted = false;
                for (String permission : requestPermissions) {
                    if (activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                        now_granted = true;
                        break;
                    }
                }
                if (whenDone != null) whenDone.run(now_granted);
            });
        } else {
            if (whenDone != null) whenDone.run(true);
        }
    }

    public static void ensureAllPermissions(int iconResId, int stringResId, String[] permissions, Utilities.Callback<Boolean> whenDone) {
        ensureAllPermissions(iconResId, stringResId, permissions, permissions, whenDone);
    }

    public static void ensureAllPermissions(int iconResId, int stringResId, String[] checkPermissions, String[] requestPermissions, Utilities.Callback<Boolean> whenDone) {
        Activity _activity = LaunchActivity.instance;
        if (_activity == null) _activity = AndroidUtilities.findActivity(ApplicationLoader.applicationContext);
        if (_activity == null) return;
        final Activity activity = _activity;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean granted = true;
            for (String permission : checkPermissions) {
                if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (granted) {
                if (whenDone != null) whenDone.run(true);
                return;
            }
            boolean needsPermissionRationale = false;
            for (String permission : checkPermissions) {
                if (activity.shouldShowRequestPermissionRationale(permission)) {
                    needsPermissionRationale = true;
                    break;
                }
            }
            if (needsPermissionRationale) {
                new AlertDialog.Builder(activity, null)
                        .setTopAnimation(iconResId, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground))
                        .setMessage(AndroidUtilities.replaceTags(LocaleController.getString(stringResId)))
                        .setPositiveButton(LocaleController.getString(R.string.PermissionOpenSettings), (dialogInterface, i) -> {
                            try {
                                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                                activity.startActivity(intent);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        })
                        .setNegativeButton(LocaleController.getString(R.string.ContactsPermissionAlertNotNow), null)
                        .create()
                        .show();
                if (whenDone != null) whenDone.run(false);
                return;
            }
            requestPermissions(requestPermissions, ids -> {
                boolean now_granted = true;
                for (String permission : requestPermissions) {
                    if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                        now_granted = false;
                        break;
                    }
                }
                if (whenDone != null) whenDone.run(now_granted);
            });
        } else {
            if (whenDone != null) whenDone.run(true);
        }
    }

    public static void requestPermission(String permission, Utilities.Callback<Boolean> whenDone) {
        requestPermissions(new String[] { permission }, whenDone != null ? res -> {
            whenDone.run(res.length >= 1 && res[0] == PackageManager.PERMISSION_GRANTED);
        } : null);
    }

    public static void requestPermissions(String[] permissions, Utilities.Callback<int[]> whenDone) {
        Activity _activity = LaunchActivity.instance;
        if (_activity == null) _activity = AndroidUtilities.findActivity(ApplicationLoader.applicationContext);
        if (_activity == null) return;
        final Activity activity = _activity;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final int code = lastId++;
            NotificationCenter.NotificationCenterDelegate[] observer = new NotificationCenter.NotificationCenterDelegate[1];
            observer[0] = new NotificationCenter.NotificationCenterDelegate() {
                @Override
                public void didReceivedNotification(int id, int account, Object... args) {
                    if (id == NotificationCenter.activityPermissionsGranted) {
                        int requestCode = (int) args[0];
                        String[] permissions = (String[]) args[1];
                        int[] grantResults = (int[]) args[2];
                        if (requestCode == code) {
                            if (whenDone != null) {
                                whenDone.run(grantResults);
                            }
                            NotificationCenter.getGlobalInstance().removeObserver(observer[0], NotificationCenter.activityPermissionsGranted);
                        }
                    }
                }
            };
            NotificationCenter.getGlobalInstance().addObserver(observer[0], NotificationCenter.activityPermissionsGranted);
            activity.requestPermissions(permissions, code);
        } else if (whenDone != null) {
            int[] res = new int[ permissions.length ];
            for (int i = 0; i < permissions.length; ++i) {
                res[i] = hasPermission(permissions[i]) ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
            }
            whenDone.run(res);
        }
    }

    public static boolean hasPermission(String permission) {
        Activity _activity = LaunchActivity.instance;
        if (_activity == null) _activity = AndroidUtilities.findActivity(ApplicationLoader.applicationContext);
        if (_activity == null) return false;
        final Activity activity = _activity;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    public static boolean canAskPermission(String permission) {
        Activity _activity = LaunchActivity.instance;
        if (_activity == null) _activity = AndroidUtilities.findActivity(ApplicationLoader.applicationContext);
        if (_activity == null) return false;
        final Activity activity = _activity;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return activity.shouldShowRequestPermissionRationale(permission);
        } else {
            return false;
        }
    }

    public static void showPermissionSettings(String permission) {
        Activity _activity = LaunchActivity.instance;
        if (_activity == null) _activity = AndroidUtilities.findActivity(ApplicationLoader.applicationContext);
        if (_activity == null) return;
        final Activity activity = _activity;

        final Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
        try {
            activity.startActivity(intent);
        } catch (Exception x) {
            FileLog.e(x);
        }
    }

}

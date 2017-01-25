/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.messenger;


import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.content.ContextCompat;

public abstract class PermissionsUtilities
{
    /**
     * Simple check is permission are granted
     */
    public static boolean isGranted(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Returns true only if permission are not granted and user dont press "Never Ask Again" button previously
     */
    public static boolean isNeedToRequest(Activity activity, String permission) {
        return !isGranted(activity, permission) && !isNeverAskAgainPressed(activity, permission);
    }

    /**
     * Returns true if user pressed "Never Ask Again" button or
     * if a device policy prohibits the app from having that permission
     */
    public static boolean isNeverAskAgainPressed(Activity activity, String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return !activity.shouldShowRequestPermissionRationale(permission);
        }
        return false;
    }
}
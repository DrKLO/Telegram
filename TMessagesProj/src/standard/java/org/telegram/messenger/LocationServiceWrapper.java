package org.telegram.messenger;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import org.telegram.ui.ApplicationActivity;
import org.telegram.ui.ApplicationLoader;
import org.telegram.ui.LocationActivity;

public class LocationServiceWrapper {
    public static boolean isGoogleMapsInstalled() {
        try {
            ApplicationInfo info = ApplicationLoader.applicationContext.getPackageManager().getApplicationInfo("com.google.android.apps.maps", 0 );
            return true;
        } catch(PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static void presentLocationView(ApplicationActivity parentActivity) {
        LocationActivity fragment = new LocationActivity();
        parentActivity.presentFragment(fragment, "location", false);
    }
}

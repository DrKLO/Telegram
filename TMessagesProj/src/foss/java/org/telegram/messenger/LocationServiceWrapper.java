package org.telegram.messenger;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import org.telegram.ui.ApplicationActivity;

public class LocationServiceWrapper {
    public static boolean isGoogleMapsInstalled() {
        return false;
    }

    public static void presentLocationView(ApplicationActivity parentActivity) {
        // Do nothing
    }
}

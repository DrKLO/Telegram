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
    private ApplicationActivity parentActivity;

//    public LocationServiceWrapper(ApplicationActivity activity) {
//        parentActivity = activity;
//    }

    public static boolean isGoogleMapsInstalled() {
        try {
            ApplicationInfo info = ApplicationLoader.applicationContext.getPackageManager().getApplicationInfo("com.google.android.apps.maps", 0 );
            return true;
        } catch(PackageManager.NameNotFoundException e) {
//            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
//            builder.setMessage("Install Google Maps?");
//            builder.setCancelable(true);
//            builder.setPositiveButton(getStringEntry(R.string.OK), new DialogInterface.OnClickListener() {
//                @Override
//                public void onClick(DialogInterface dialogInterface, int i) {
//                    try {
//                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.maps"));
//                        startActivity(intent);
//                    } catch (Exception e) {
//                        FileLog.e("tmessages", e);
//                    }
//                }
//            });
//            builder.setNegativeButton(R.string.Cancel, null);
//            visibleDialog = builder.create();
//            visibleDialog.setCanceledOnTouchOutside(true);
//            visibleDialog.show();
            return false;
        }
    }

    public static void presentLocationView(ApplicationActivity parentActivity) {
        LocationActivity fragment = new LocationActivity();
        parentActivity.presentFragment(fragment, "location", false);
    }
}

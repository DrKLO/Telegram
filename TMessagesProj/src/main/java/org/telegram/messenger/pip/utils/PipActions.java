package org.telegram.messenger.pip.utils;

import android.app.PendingIntent;
import android.app.RemoteAction;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;

import androidx.annotation.RequiresApi;

public class PipActions {
    public static final String ACTION = "PIP_CUSTOM_EVENT";

    private static int requestCode;

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static RemoteAction create(
        Context context,
        String sourceId,
        int actionId,
        String title,
        String description,
        Icon icon
    ) {
        final Intent intent = new Intent(ACTION);
        intent.setPackage(context.getPackageName());
        intent.putExtra("source_id", sourceId);
        intent.putExtra("action_id", actionId);

        return new RemoteAction(icon, title, description,
            PendingIntent.getBroadcast(context, requestCode++, intent, PendingIntent.FLAG_IMMUTABLE)
        );
    };

    public static boolean isPipIntent(Intent intent) {
        return ACTION.equals(intent.getAction());
    }

    public static String getSourceId(Intent intent) {
        return intent.getStringExtra("source_id");
    }

    public static int getActionId(Intent intent) {
        return intent.getIntExtra("action_id", -1);
    }
}

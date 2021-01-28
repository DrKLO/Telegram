package org.telegram.messenger;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.RemoteViews;

import org.telegram.ui.LaunchActivity;

public class ShortcutWidgetProvider extends AppWidgetProvider {

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);
        for (int i = 0; i < appWidgetIds.length; i++) {
            int appWidgetId = appWidgetIds[i];
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        for (int a = 0; a < appWidgetIds.length; a++) {
            SharedPreferences preferences = context.getSharedPreferences("shortcut_widget", Activity.MODE_PRIVATE);
            int accountId = preferences.getInt("account" + appWidgetIds[a], -1);
            if (accountId >= 0) {
                AccountInstance accountInstance = AccountInstance.getInstance(accountId);
                accountInstance.getMessagesStorage().clearWidgetDialogs(appWidgetIds[a]);
            }
        }
    }

    public static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        Intent intent2 = new Intent(context, ShortcutWidgetService.class);
        intent2.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent2.setData(Uri.parse(intent2.toUri(Intent.URI_INTENT_SCHEME)));
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.shortcut_widget_layout);
        rv.setRemoteAdapter(appWidgetId, R.id.list_view, intent2);
        rv.setEmptyView(R.id.list_view, R.id.empty_view);

        Intent intent = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
        intent.setAction("com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        PendingIntent contentIntent = PendingIntent.getActivity(ApplicationLoader.applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        rv.setPendingIntentTemplate(R.id.list_view, contentIntent);

        appWidgetManager.updateAppWidget(appWidgetId, rv);
    }
}

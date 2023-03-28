package org.telegram.messenger;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.RemoteViews;

import org.telegram.ui.EditWidgetActivity;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;

public class ChatsWidgetProvider extends AppWidgetProvider {

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
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        updateWidget(context, appWidgetManager, appWidgetId);
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        ApplicationLoader.postInitApplication();
        SharedPreferences preferences = context.getSharedPreferences("shortcut_widget", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        for (int a = 0; a < appWidgetIds.length; a++) {
            int accountId = preferences.getInt("account" + appWidgetIds[a], -1);
            if (accountId >= 0) {
                AccountInstance accountInstance = AccountInstance.getInstance(accountId);
                accountInstance.getMessagesStorage().clearWidgetDialogs(appWidgetIds[a]);
            }
            editor.remove("account" + appWidgetIds[a]);
            editor.remove("type" + appWidgetIds[a]);
            editor.remove("deleted" + appWidgetIds[a]);
        }
        editor.commit();
    }

    private static int getCellsForSize(int size) {
        int n = 2;
        while (72 * n < size) {
            ++n;
        }
        return n - 1;
    }

    public static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        ApplicationLoader.postInitApplication();
        Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
        int rows = getCellsForSize(minHeight);

        Intent intent2 = new Intent(context, ChatsWidgetService.class);
        intent2.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent2.setData(Uri.parse(intent2.toUri(Intent.URI_INTENT_SCHEME)));

        SharedPreferences preferences = context.getSharedPreferences("shortcut_widget", Activity.MODE_PRIVATE);
        boolean deleted = preferences.getBoolean("deleted" + appWidgetId, false);
        int id;
        if (!deleted) {
            int accountId = preferences.getInt("account" + appWidgetId, -1);
            if (accountId == -1) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putInt("account" + appWidgetId, UserConfig.selectedAccount);
                editor.putInt("type" + appWidgetId, EditWidgetActivity.TYPE_CHATS).commit();
            }
            ArrayList<Long> selectedDialogs = new ArrayList<>();
            if (accountId >= 0) {
                AccountInstance.getInstance(accountId).getMessagesStorage().getWidgetDialogIds(appWidgetId, EditWidgetActivity.TYPE_CHATS, selectedDialogs, null, null, false);
            }

            if (rows == 1 || selectedDialogs.size() <= 1) {
                id = R.layout.shortcut_widget_layout_1;
            } else if (rows == 2 || selectedDialogs.size() <= 2) {
                id = R.layout.shortcut_widget_layout_2;
            } else if (rows == 3 || selectedDialogs.size() <= 3) {
                id = R.layout.shortcut_widget_layout_3;
            } else {
                id = R.layout.shortcut_widget_layout_4;
            }
        } else {
            id = R.layout.shortcut_widget_layout_1;
        }
        RemoteViews rv = new RemoteViews(context.getPackageName(), id);
        rv.setRemoteAdapter(appWidgetId, R.id.list_view, intent2);
        rv.setEmptyView(R.id.list_view, R.id.empty_view);

        Intent intent = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
        intent.setAction("com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        PendingIntent contentIntent = PendingIntent.getActivity(ApplicationLoader.applicationContext, 0, intent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        rv.setPendingIntentTemplate(R.id.list_view, contentIntent);
        appWidgetManager.updateAppWidget(appWidgetId, rv);
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.list_view);
    }
}

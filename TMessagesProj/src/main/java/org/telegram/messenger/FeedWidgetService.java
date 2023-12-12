package org.telegram.messenger;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import androidx.core.content.FileProvider;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class FeedWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new FeedRemoteViewsFactory(getApplicationContext(), intent);
    }
}

class FeedRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory, NotificationCenter.NotificationCenterDelegate {

    private ArrayList<MessageObject> messages = new ArrayList<>();
    private Context mContext;
    private long dialogId;
    private int classGuid;
    private AccountInstance accountInstance;
    private CountDownLatch countDownLatch = new CountDownLatch(1);

    public FeedRemoteViewsFactory(Context context, Intent intent) {
        mContext = context;
        int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        SharedPreferences preferences = context.getSharedPreferences("shortcut_widget", Activity.MODE_PRIVATE);
        int accountId = preferences.getInt("account" + appWidgetId, -1);
        if (accountId >= 0) {
            dialogId = preferences.getLong("dialogId" + appWidgetId, 0);
            accountInstance = AccountInstance.getInstance(accountId);
        }
    }

    public void onCreate() {
        ApplicationLoader.postInitApplication();
    }

    public void onDestroy() {

    }

    public int getCount() {
        return messages.size();
    }

    protected void grantUriAccessToWidget(Context context, Uri uri) {
        Intent intent= new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> resInfoList = context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo resolveInfo : resInfoList) {
            String packageName = resolveInfo.activityInfo.packageName;
            context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
    }

    public RemoteViews getViewAt(int position) {
        MessageObject messageObject = messages.get(position);
        String name;

        RemoteViews rv = new RemoteViews(mContext.getPackageName(), R.layout.feed_widget_item);
        if (messageObject.type == MessageObject.TYPE_TEXT) {
            rv.setTextViewText(R.id.feed_widget_item_text, messageObject.messageText);
            rv.setViewVisibility(R.id.feed_widget_item_text, View.VISIBLE);
        } else {
            if (TextUtils.isEmpty(messageObject.caption)) {
                rv.setViewVisibility(R.id.feed_widget_item_text, View.GONE);
            } else {
                rv.setTextViewText(R.id.feed_widget_item_text, messageObject.caption);
                rv.setViewVisibility(R.id.feed_widget_item_text, View.VISIBLE);
            }
        }

        if (messageObject.photoThumbs == null || messageObject.photoThumbs.isEmpty()) {
            rv.setViewVisibility(R.id.feed_widget_item_image, View.GONE);
        } else {
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, AndroidUtilities.getPhotoSize());
            File f = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(size);
            if (f.exists()) {
                rv.setViewVisibility(R.id.feed_widget_item_image, View.VISIBLE);
                Uri uri = FileProvider.getUriForFile(mContext, ApplicationLoader.getApplicationId() + ".provider", f);
                grantUriAccessToWidget(mContext, uri);
                rv.setImageViewUri(R.id.feed_widget_item_image, uri);
            } else {
                rv.setViewVisibility(R.id.feed_widget_item_image, View.GONE);
            }
        }

        Bundle extras = new Bundle();
        extras.putLong("chatId", -messageObject.getDialogId());
        extras.putInt("message_id", messageObject.getId());
        extras.putInt("currentAccount", accountInstance.getCurrentAccount());

        Intent fillInIntent = new Intent();
        fillInIntent.putExtras(extras);
        rv.setOnClickFillInIntent(R.id.shortcut_widget_item, fillInIntent);

        return rv;
    }

    public RemoteViews getLoadingView() {
        return null;
    }

    public int getViewTypeCount() {
        return 1;
    }

    public long getItemId(int position) {
        return position;
    }

    public boolean hasStableIds() {
        return true;
    }

    public void onDataSetChanged() {
        if (accountInstance == null || !accountInstance.getUserConfig().isClientActivated()) {
            messages.clear();
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            accountInstance.getNotificationCenter().addObserver(FeedRemoteViewsFactory.this, NotificationCenter.messagesDidLoad);
            if (classGuid == 0) {
                classGuid = ConnectionsManager.generateClassGuid();
            }
            accountInstance.getMessagesController().loadMessages(dialogId, 0, false, 20, 0, 0, true, 0, classGuid, 0, 0, 0, 0, 0, 1, false);
        });
        try {
            countDownLatch.await();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagesDidLoad) {
            int guid = (Integer) args[10];
            if (guid == classGuid) {
                messages.clear();
                ArrayList<MessageObject> messArr = (ArrayList<MessageObject>) args[2];
                messages.addAll(messArr);
                countDownLatch.countDown();
            }
        }
    }
}

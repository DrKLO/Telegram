package org.telegram.messenger;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.EditWidgetActivity;

import java.io.File;
import java.util.ArrayList;

import androidx.collection.LongSparseArray;

public class ContactsWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new ContactsRemoteViewsFactory(getApplicationContext(), intent);
    }
}

class ContactsRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {

    private ArrayList<Long> dids = new ArrayList<>();
    private Context mContext;
    private int appWidgetId;
    private AccountInstance accountInstance;
    private Paint roundPaint;
    private RectF bitmapRect;
    private LongSparseArray<TLRPC.Dialog> dialogs = new LongSparseArray<>();
    private boolean deleted;

    public ContactsRemoteViewsFactory(Context context, Intent intent) {
        mContext = context;
        Theme.createDialogsResources(context);
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        SharedPreferences preferences = context.getSharedPreferences("shortcut_widget", Activity.MODE_PRIVATE);
        int accountId = preferences.getInt("account" + appWidgetId, -1);
        if (accountId >= 0) {
            accountInstance = AccountInstance.getInstance(accountId);
        }
        deleted = preferences.getBoolean("deleted" + appWidgetId, false) || accountInstance == null;
    }

    public void onCreate() {
        ApplicationLoader.postInitApplication();
    }

    public void onDestroy() {

    }

    public int getCount() {
        if (deleted) {
            return 1;
        }
        int count = (int) Math.ceil(dids.size() / 2.0f);
        return count + 1;
    }

    public RemoteViews getViewAt(int position) {
        if (deleted) {
            RemoteViews rv = new RemoteViews(mContext.getPackageName(), R.layout.widget_deleted);
            rv.setTextViewText(R.id.widget_deleted_text, LocaleController.getString("WidgetLoggedOff", R.string.WidgetLoggedOff));
            return rv;
        } else if (position >= getCount() - 1) {
            RemoteViews rv = new RemoteViews(mContext.getPackageName(), R.layout.widget_edititem);
            rv.setTextViewText(R.id.widget_edititem_text, LocaleController.getString("TapToEditWidgetShort", R.string.TapToEditWidgetShort));
            Bundle extras = new Bundle();
            extras.putInt("appWidgetId", appWidgetId);
            extras.putInt("appWidgetType", EditWidgetActivity.TYPE_CONTACTS);
            extras.putInt("currentAccount", accountInstance.getCurrentAccount());
            Intent fillInIntent = new Intent();
            fillInIntent.putExtras(extras);
            rv.setOnClickFillInIntent(R.id.widget_edititem, fillInIntent);
            return rv;
        }
        RemoteViews rv = new RemoteViews(mContext.getPackageName(), R.layout.contacts_widget_item);
        for (int a = 0; a < 2; a++) {
            int num = position * 2 + a;
            if (num >= dids.size()) {
                rv.setViewVisibility(a == 0 ? R.id.contacts_widget_item1 : R.id.contacts_widget_item2, View.INVISIBLE);
            } else {
                rv.setViewVisibility(a == 0 ? R.id.contacts_widget_item1 : R.id.contacts_widget_item2, View.VISIBLE);

                Long id = dids.get(position * 2 + a);
                String name;

                TLRPC.FileLocation photoPath = null;
                TLRPC.User user = null;
                TLRPC.Chat chat = null;
                if (DialogObject.isUserDialog(id)) {
                    user = accountInstance.getMessagesController().getUser(id);
                    if (UserObject.isUserSelf(user)) {
                        name = LocaleController.getString("SavedMessages", R.string.SavedMessages);
                    } else if (UserObject.isReplyUser(user)) {
                        name = LocaleController.getString("RepliesTitle", R.string.RepliesTitle);
                    } else if (UserObject.isDeleted(user)) {
                        name = LocaleController.getString("HiddenName", R.string.HiddenName);
                    } else {
                        name = UserObject.getFirstName(user);
                    }
                    if (!UserObject.isReplyUser(user) && !UserObject.isUserSelf(user) && user != null && user.photo != null && user.photo.photo_small != null && user.photo.photo_small.volume_id != 0 && user.photo.photo_small.local_id != 0) {
                        photoPath = user.photo.photo_small;
                    }
                } else {
                    chat = accountInstance.getMessagesController().getChat(-id);
                    if (chat != null) {
                        name = chat.title;
                        if (chat.photo != null && chat.photo.photo_small != null && chat.photo.photo_small.volume_id != 0 && chat.photo.photo_small.local_id != 0) {
                            photoPath = chat.photo.photo_small;
                        }
                    } else {
                        name = "";
                    }
                }
                rv.setTextViewText(a == 0 ? R.id.contacts_widget_item_text1 : R.id.contacts_widget_item_text2, name);

                try {
                    Bitmap bitmap = null;
                    if (photoPath != null) {
                        File path = FileLoader.getPathToAttach(photoPath, true);
                        bitmap = BitmapFactory.decodeFile(path.toString());
                    }

                    int size = AndroidUtilities.dp(48);
                    Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                    result.eraseColor(Color.TRANSPARENT);
                    Canvas canvas = new Canvas(result);
                    if (bitmap == null) {
                        AvatarDrawable avatarDrawable;
                        if (user != null) {
                            avatarDrawable = new AvatarDrawable(user);
                            if (UserObject.isReplyUser(user)) {
                                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                            } else if (UserObject.isUserSelf(user)) {
                                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                            }
                        } else {
                            avatarDrawable = new AvatarDrawable(chat);
                        }
                        avatarDrawable.setBounds(0, 0, size, size);
                        avatarDrawable.draw(canvas);
                    } else {
                        BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                        if (roundPaint == null) {
                            roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                            bitmapRect = new RectF();
                        }
                        float scale = size / (float) bitmap.getWidth();
                        canvas.save();
                        canvas.scale(scale, scale);
                        roundPaint.setShader(shader);
                        bitmapRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
                        canvas.drawRoundRect(bitmapRect, bitmap.getWidth(), bitmap.getHeight(), roundPaint);
                        canvas.restore();
                    }
                    canvas.setBitmap(null);
                    rv.setImageViewBitmap(a == 0 ? R.id.contacts_widget_item_avatar1 : R.id.contacts_widget_item_avatar2, result);
                } catch (Throwable e) {
                    FileLog.e(e);
                }

                TLRPC.Dialog dialog = dialogs.get(id);

                if (dialog != null && dialog.unread_count > 0) {
                    String count;
                    if (dialog.unread_count > 99) {
                        count = String.format("%d+", 99);
                    } else {
                        count = String.format("%d", dialog.unread_count);
                    }
                    rv.setTextViewText(a == 0 ? R.id.contacts_widget_item_badge1 : R.id.contacts_widget_item_badge2, count);
                    rv.setViewVisibility(a == 0 ? R.id.contacts_widget_item_badge_bg1 : R.id.contacts_widget_item_badge_bg2, View.VISIBLE);
                } else {
                    rv.setViewVisibility(a == 0 ? R.id.contacts_widget_item_badge_bg1 : R.id.contacts_widget_item_badge_bg2, View.GONE);
                }

                Bundle extras = new Bundle();

                if (DialogObject.isUserDialog(id)) {
                    extras.putLong("userId", id);
                } else {
                    extras.putLong("chatId", -id);
                }
                extras.putInt("currentAccount", accountInstance.getCurrentAccount());

                Intent fillInIntent = new Intent();
                fillInIntent.putExtras(extras);
                rv.setOnClickFillInIntent(a == 0 ? R.id.contacts_widget_item1 : R.id.contacts_widget_item2, fillInIntent);
            }
        }
        return rv;
    }

    public RemoteViews getLoadingView() {
        return null;
    }

    public int getViewTypeCount() {
        return 2;
    }

    public long getItemId(int position) {
        return position;
    }

    public boolean hasStableIds() {
        return true;
    }

    public void onDataSetChanged() {
        dids.clear();
        if (accountInstance == null || !accountInstance.getUserConfig().isClientActivated()) {
            return;
        }
        ArrayList<TLRPC.User> users = new ArrayList<>();
        ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        LongSparseArray<TLRPC.Message> messages = new LongSparseArray<>();
        accountInstance.getMessagesStorage().getWidgetDialogs(appWidgetId, 1, dids, dialogs, messages, users, chats);
        accountInstance.getMessagesController().putUsers(users, true);
        accountInstance.getMessagesController().putChats(chats, true);
    }
}

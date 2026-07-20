package org.telegram.messenger.car;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.CarText;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.MessageTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Tab;
import androidx.car.app.model.TabContents;
import androidx.car.app.model.TabTemplate;
import androidx.car.app.model.Template;
import androidx.car.app.messaging.model.CarMessage;
import androidx.car.app.messaging.model.ConversationCallback;
import androidx.car.app.messaging.model.ConversationItem;
import androidx.core.app.Person;
import androidx.core.graphics.drawable.IconCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.TelegramMediaSession;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HomeScreen extends Screen
        implements DefaultLifecycleObserver, NotificationCenter.NotificationCenterDelegate {

    private static final String TAB_NOTIFICATIONS = "tab_notifications";
    private static final String TAB_MUSIC = "tab_music";

    private static final int MAX_CONVERSATIONS = 6;
    private static final int MAX_MESSAGES_PER_CONV = 5;
    private static final int MAX_MUSIC_DIALOGS = 50;

    private final long sessionStartMillis;
    private int currentAccount;

    private String activeTabId = TAB_NOTIFICATIONS;
    private boolean musicLoadKicked;

    public HomeScreen(@NonNull CarContext carContext) {
        super(carContext);
        sessionStartMillis = System.currentTimeMillis();
        currentAccount = UserConfig.selectedAccount;
        getLifecycle().addObserver(this);
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.pushMessagesUpdated);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.notificationsCountUpdated);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.activeAccountChanged);
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.pushMessagesUpdated);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.notificationsCountUpdated);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.activeAccountChanged);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.activeAccountChanged) {
            currentAccount = UserConfig.selectedAccount;
            musicLoadKicked = false;
            invalidate();
        } else if ((id == NotificationCenter.pushMessagesUpdated || id == NotificationCenter.notificationsCountUpdated)
                && TAB_NOTIFICATIONS.equals(activeTabId)) {
            invalidate();
        }
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        TabTemplate.Builder builder = new TabTemplate.Builder(new TabTemplate.TabCallback() {
            @Override
            public void onTabSelected(@NonNull String tabContentId) {
                activeTabId = tabContentId;
                invalidate();
            }
        });
        builder.setHeaderAction(Action.APP_ICON);

        builder.addTab(new Tab.Builder()
                .setContentId(TAB_NOTIFICATIONS)
                .setIcon(iconResource(R.drawable.msg_notifications))
                .setTitle(LocaleController.getString(R.string.Notifications))
                .build());
        builder.addTab(new Tab.Builder()
                .setContentId(TAB_MUSIC)
                .setIcon(iconResource(R.drawable.filled_widget_music))
                .setTitle(LocaleController.getString(R.string.Music))
                .build());

        builder.setActiveTabContentId(activeTabId);
        builder.setTabContents(new TabContents.Builder(buildTabContent(activeTabId)).build());
        return builder.build();
    }

    private Template buildTabContent(String tabId) {
        switch (tabId) {
            case TAB_MUSIC:
                return buildMusicTemplate();
            case TAB_NOTIFICATIONS:
            default:
                return buildNotificationsTemplate();
        }
    }

    // ===== Notifications tab =====

    private Template buildNotificationsTemplate() {
        Map<Long, ArrayList<MessageObject>> grouped = collectUnreadDuringDrive();
        if (grouped.isEmpty()) {
            return new MessageTemplate.Builder(LocaleController.getString(R.string.NoNewCarMessages))
                    .build();
        }
        ItemList.Builder list = new ItemList.Builder();
        int count = 0;
        for (Map.Entry<Long, ArrayList<MessageObject>> entry : grouped.entrySet()) {
            if (count++ >= MAX_CONVERSATIONS) break;
            ConversationItem item = buildConversationItem(entry.getKey(), entry.getValue());
            if (item != null) list.addItem(item);
        }
        return new ListTemplate.Builder()
                .setSingleList(list.build())
                .build();
    }

    private Map<Long, ArrayList<MessageObject>> collectUnreadDuringDrive() {
        LinkedHashMap<Long, ArrayList<MessageObject>> grouped = new LinkedHashMap<>();
        NotificationsController nc = NotificationsController.getInstance(currentAccount);
        ArrayList<MessageObject> snapshot = nc.getPushMessagesSnapshot();
        long sessionStartSec = sessionStartMillis / 1000;
        for (MessageObject mo : snapshot) {
            if (mo == null || mo.messageOwner == null) continue;
            if (mo.messageOwner.date < sessionStartSec) continue;
            long dialogId = mo.getDialogId();
            if (DialogObject.isEncryptedDialog(dialogId)) continue;
            if (UserObject.isReplyUser(dialogId)) continue;
            ArrayList<MessageObject> bucket = grouped.get(dialogId);
            if (bucket == null) {
                bucket = new ArrayList<>();
                grouped.put(dialogId, bucket);
            }
            bucket.add(mo);
        }
        return grouped;
    }

    private ConversationItem buildConversationItem(long dialogId, ArrayList<MessageObject> messages) {
        if (messages.isEmpty()) return null;
        Collections.reverse(messages);
        AccountInstance ai = AccountInstance.getInstance(currentAccount);
        MessagesController mc = ai.getMessagesController();

        TLRPC.User user = null;
        TLRPC.Chat chat = null;
        String title;
        boolean isGroup = false;
        if (DialogObject.isUserDialog(dialogId)) {
            user = mc.getUser(dialogId);
            if (user == null) return null;
            title = ContactsController.formatName(user.first_name, user.last_name);
        } else {
            chat = mc.getChat(-dialogId);
            if (chat == null) return null;
            title = chat.title != null ? chat.title : "";
            isGroup = !ChatObject.isChannel(chat) || ChatObject.isMegagroup(chat);
        }

        Person.Builder convPersonBuilder = new Person.Builder()
                .setName(title)
                .setKey(String.valueOf(dialogId));
        IconCompat icon = loadAvatarIcon(user, chat);
        if (icon != null) convPersonBuilder.setIcon(icon);

        TLRPC.User self = UserConfig.getInstance(currentAccount).getCurrentUser();
        Person.Builder selfBuilder = new Person.Builder()
                .setName(LocaleController.getString(R.string.FromYou))
                .setKey("self_" + UserConfig.getInstance(currentAccount).clientUserId);
        if (self != null) {
            IconCompat selfIcon = loadUserAvatarIcon(self);
            if (selfIcon != null) selfBuilder.setIcon(selfIcon);
        }
        Person selfPerson = selfBuilder.build();

        List<CarMessage> carMessages = new ArrayList<>();
        int start = Math.max(0, messages.size() - MAX_MESSAGES_PER_CONV);
        for (int i = start; i < messages.size(); i++) {
            MessageObject mo = messages.get(i);
            CarMessage cm = buildCarMessage(mo, dialogId, isGroup, user, chat);
            if (cm != null) carMessages.add(cm);
        }
        if (carMessages.isEmpty()) return null;

        int latestMid = messages.get(messages.size() - 1).getId();

        ConversationItem.Builder cb = new ConversationItem.Builder()
                .setId(String.valueOf(dialogId))
                .setTitle(CarText.create(title))
                .setSelf(selfPerson)
                .setMessages(carMessages)
                .setGroupConversation(isGroup)
                .setConversationCallback(new TelegramConversationCallback(currentAccount, dialogId, latestMid));
        if (icon != null) {
            cb.setIcon(new CarIcon.Builder(icon).build());
        }
        return cb.build();
    }

    private CarMessage buildCarMessage(MessageObject mo, long dialogId, boolean isGroup, TLRPC.User user, TLRPC.Chat chat) {
        String[] senderName = new String[1];
        boolean[] preview = new boolean[1];
        String body;
        try {
            body = NotificationsController.getInstance(currentAccount)
                    .getShortStringForMessage(mo, senderName, preview);
        } catch (Throwable t) {
            body = mo.messageText != null ? mo.messageText.toString() : "";
        }
        if (body == null) return null;

        Person.Builder senderBuilder = new Person.Builder();
        if (senderName[0] != null) {
            senderBuilder.setName(senderName[0]).setKey("u" + mo.getSenderId());
        } else if (DialogObject.isUserDialog(dialogId) && user != null) {
            senderBuilder.setName(ContactsController.formatName(user.first_name, user.last_name))
                    .setKey("u" + user.id);
        } else if (chat != null) {
            senderBuilder.setName(chat.title != null ? chat.title : "").setKey("c" + chat.id);
        } else {
            senderBuilder.setName("");
        }

        return new CarMessage.Builder()
                .setBody(CarText.create(body))
                .setReceivedTimeEpochMillis(((long) mo.messageOwner.date) * 1000L)
                .setSender(senderBuilder.build())
                .setRead(false)
                .build();
    }

    // ===== Music tab =====

    private Template buildMusicTemplate() {
        TelegramMediaSession session = TelegramMediaSession.getInstance(getCarContext().getApplicationContext());
        if (!session.isChatsLoaded()) {
            if (!musicLoadKicked) {
                musicLoadKicked = true;
                session.ensureLoaded(this::invalidate);
            }
            return new ListTemplate.Builder().setLoading(true).build();
        }
        ArrayList<Long> dialogs = session.getMusicDialogsSortedByVisibleOrder();
        if (dialogs == null || dialogs.isEmpty()) {
            return new MessageTemplate.Builder(LocaleController.getString(R.string.NoCarMusic))
                    .build();
        }
        ItemList.Builder list = new ItemList.Builder();
        int added = 0;
        for (int i = 0; i < dialogs.size() && added < MAX_MUSIC_DIALOGS; i++) {
            long dialogId = dialogs.get(i);
            ArrayList<MessageObject> messages = session.getMusicMessages(dialogId);
            if (messages == null || messages.isEmpty()) continue;
            String title;
            TLRPC.FileLocation avatarLoc = null;
            if (DialogObject.isUserDialog(dialogId)) {
                TLRPC.User u = session.getMusicUser(dialogId);
                if (u == null) continue;
                if (UserObject.isUserSelf(u)) {
                    title = LocaleController.getString(R.string.SavedMessages);
                } else {
                    title = ContactsController.formatName(u.first_name, u.last_name);
                    if (u.photo != null && !(u.photo.photo_small instanceof TLRPC.TL_fileLocationUnavailable)) {
                        avatarLoc = u.photo.photo_small;
                    }
                }
            } else {
                TLRPC.Chat c = session.getMusicChat(-dialogId);
                if (c == null) continue;
                title = c.title != null ? c.title : "";
                if (c.photo != null && !(c.photo.photo_small instanceof TLRPC.TL_fileLocationUnavailable)) {
                    avatarLoc = c.photo.photo_small;
                }
            }
            if (TextUtils.isEmpty(title)) continue;

            Row.Builder row = new Row.Builder()
                    .setTitle(title)
                    .addText(LocaleController.formatPluralString("MusicFiles", messages.size()))
                    .setBrowsable(true)
                    .setOnClickListener(() -> getScreenManager().push(new MusicSongsScreen(getCarContext(), dialogId, title)));

            IconCompat icon = avatarLoc != null
                    ? iconFromAvatar(avatarLoc)
                    : null;
            if (icon != null) {
                row.setImage(new CarIcon.Builder(icon).build(), Row.IMAGE_TYPE_LARGE);
            }
            list.addItem(row.build());
            added++;
        }
        return new ListTemplate.Builder()
                .setSingleList(list.build())
                .build();
    }

    // ===== Icon helpers =====

    private CarIcon iconResource(int resId) {
        return new CarIcon.Builder(IconCompat.createWithResource(getCarContext(), resId)).build();
    }

    private IconCompat loadAvatarIcon(TLRPC.User user, TLRPC.Chat chat) {
        TLRPC.FileLocation fileLocation = null;
        if (user != null && user.photo != null) {
            fileLocation = user.photo.photo_small;
        } else if (chat != null && chat.photo != null) {
            fileLocation = chat.photo.photo_small;
        }
        return iconFromAvatar(fileLocation);
    }

    private IconCompat loadUserAvatarIcon(TLRPC.User user) {
        if (user == null || user.photo == null) return null;
        return iconFromAvatar(user.photo.photo_small);
    }

    private IconCompat iconFromAvatar(TLRPC.FileLocation loc) {
        if (loc == null || loc instanceof TLRPC.TL_fileLocationUnavailable) return null;
        try {
            File path = FileLoader.getInstance(currentAccount).getPathToAttach(loc, true);
            if (!path.exists()) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 2;
            Bitmap bitmap = BitmapFactory.decodeFile(path.getAbsolutePath(), opts);
            if (bitmap == null) return null;
            Bitmap rounded = makeRound(bitmap);
            return IconCompat.createWithBitmap(rounded != null ? rounded : bitmap);
        } catch (Throwable t) {
            return null;
        }
    }

    private Bitmap makeRound(Bitmap source) {
        try {
            Bitmap dst = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
            dst.eraseColor(Color.TRANSPARENT);
            Canvas canvas = new Canvas(dst);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setShader(new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            RectF r = new RectF(0, 0, source.getWidth(), source.getHeight());
            canvas.drawRoundRect(r, source.getWidth(), source.getHeight(), paint);
            return dst;
        } catch (Throwable t) {
            return null;
        }
    }

    private static final class TelegramConversationCallback implements ConversationCallback {
        private final int currentAccount;
        private final long dialogId;
        private final int maxId;

        TelegramConversationCallback(int currentAccount, long dialogId, int maxId) {
            this.currentAccount = currentAccount;
            this.dialogId = dialogId;
            this.maxId = maxId;
        }

        @Override
        public void onMarkAsRead() {
            AndroidUtilities.runOnUIThread(() -> {
                MessagesController.getInstance(currentAccount).markDialogAsRead(
                        dialogId, maxId, maxId, 0, false, 0, 0, true, 0);
                MessagesController.getInstance(currentAccount).markReactionsAsRead(dialogId, 0);
            });
        }

        @Override
        public void onTextReply(@NonNull String text) {
            AndroidUtilities.runOnUIThread(() -> {
                AccountInstance ai = AccountInstance.getInstance(currentAccount);
                ai.getSendMessagesHelper().sendMessage(SendMessagesHelper.SendMessageParams.of(
                        text, dialogId, null, null, null, true, null, null, null, true, 0, 0, null, false));
                MessagesController.getInstance(currentAccount).markDialogAsRead(
                        dialogId, maxId, maxId, 0, false, 0, 0, true, 0);
            });
        }
    }
}

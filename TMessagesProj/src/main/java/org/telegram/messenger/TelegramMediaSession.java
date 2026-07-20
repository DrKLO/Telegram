package org.telegram.messenger;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.browse.MediaBrowser;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressLint("StaticFieldLeak")
public class TelegramMediaSession {

    private static volatile TelegramMediaSession instance;

    public static TelegramMediaSession getInstance(Context context) {
        if (instance == null) {
            synchronized (TelegramMediaSession.class) {
                if (instance == null) {
                    instance = new TelegramMediaSession(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    @Nullable
    public static TelegramMediaSession peekInstance() {
        return instance;
    }

    private static final String SESSION_TAG = "TelegramMediaSession";
    private static final String MEDIA_ID_ROOT = "__ROOT__";
    private static final String MEDIA_ID_CHAT_PREFIX = "__CHAT_";

    private static final String SLOT_RESERVATION_SKIP_TO_NEXT = "com.google.android.gms.car.media.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_NEXT";
    private static final String SLOT_RESERVATION_SKIP_TO_PREV = "com.google.android.gms.car.media.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_PREVIOUS";
    private static final String SLOT_RESERVATION_QUEUE = "com.google.android.gms.car.media.ALWAYS_RESERVE_SPACE_FOR.ACTION_QUEUE";

    private static final String CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED";
    private static final String CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT";
    private static final String CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT";
    private static final int CONTENT_STYLE_LIST_ITEM_HINT_VALUE = 1;
    private static final int CONTENT_STYLE_GRID_ITEM_HINT_VALUE = 2;

    private final Context appContext;
    private final MediaSessionCompat session;

    private int currentAccount;
    private long lastSelectedDialog;

    private boolean chatsLoaded;
    private boolean loadingChats;
    private final ArrayList<Long> dialogs = new ArrayList<>();
    private final LongSparseArray<TLRPC.User> users = new LongSparseArray<>();
    private final LongSparseArray<TLRPC.Chat> chats = new LongSparseArray<>();
    private final LongSparseArray<ArrayList<MessageObject>> musicObjects = new LongSparseArray<>();
    private final LongSparseArray<ArrayList<MediaSessionCompat.QueueItem>> musicQueues = new LongSparseArray<>();

    private Paint roundPaint;
    private RectF bitmapRect;

    private TelegramMediaSession(Context appContext) {
        this.appContext = appContext;
        this.currentAccount = UserConfig.selectedAccount;
        this.lastSelectedDialog = AndroidUtilities.getPrefIntOrLong(MessagesController.getNotificationsSettings(currentAccount), "auto_lastSelectedDialog", 0);

        session = new MediaSessionCompat(appContext, SESSION_TAG);
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new SessionCallback());

        Intent activityIntent = new Intent(appContext, LaunchActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                appContext, 99, activityIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        session.setSessionActivity(pi);

        Bundle extras = new Bundle();
        extras.putBoolean(SLOT_RESERVATION_QUEUE, true);
        extras.putBoolean(SLOT_RESERVATION_SKIP_TO_PREV, true);
        extras.putBoolean(SLOT_RESERVATION_SKIP_TO_NEXT, true);
        session.setExtras(extras);

        session.setActive(true);

        PlaybackStateCompat.Builder pb = new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_NONE, 0, 1f)
                .setActions(getAvailableActions());
        session.setPlaybackState(pb.build());

        updateRepeatMode();
        updateShuffleMode();

        NotificationCenter.getGlobalInstance().addObserver(
                (id, account, args) -> {
                    if (id == NotificationCenter.activeAccountChanged) {
                        AndroidUtilities.runOnUIThread(this::onAccountSwitched);
                    }
                }, NotificationCenter.activeAccountChanged);
    }

    private void onAccountSwitched() {
        currentAccount = UserConfig.selectedAccount;
        lastSelectedDialog = AndroidUtilities.getPrefIntOrLong(
                MessagesController.getNotificationsSettings(currentAccount), "auto_lastSelectedDialog", 0);
        chatsLoaded = false;
        loadingChats = false;
        dialogs.clear();
        users.clear();
        chats.clear();
        musicObjects.clear();
        musicQueues.clear();
        try {
            session.setQueue(null);
            session.setQueueTitle(null);
        } catch (Throwable ignored) {
        }
    }

    public int getCurrentAccount() {
        return currentAccount;
    }

    public ArrayList<Long> getMusicDialogsSortedByVisibleOrder() {
        ArrayList<Long> sorted = new ArrayList<>(dialogs);
        ArrayList<TLRPC.Dialog> all = MessagesController.getInstance(currentAccount).getAllDialogs();
        final java.util.HashMap<Long, Integer> rank = new java.util.HashMap<>();
        for (int i = 0; i < all.size(); i++) {
            TLRPC.Dialog d = all.get(i);
            if (d != null) rank.put(d.id, i);
        }
        java.util.Collections.sort(sorted, (a, b) -> {
            Integer ra = rank.get(a);
            Integer rb = rank.get(b);
            if (ra == null && rb == null) return Long.compare(a, b);
            if (ra == null) return 1;
            if (rb == null) return -1;
            return Integer.compare(ra, rb);
        });
        return sorted;
    }

    public MediaSessionCompat getSession() {
        return session;
    }

    public MediaSessionCompat.Token getSessionToken() {
        return session.getSessionToken();
    }

    public android.media.session.MediaSession.Token getFrameworkSessionToken() {
        return (android.media.session.MediaSession.Token) session.getSessionToken().getToken();
    }

    public void release() {
        if (session != null) {
            session.release();
        }
    }

    public Bundle buildRootHints() {
        Bundle rootExtras = new Bundle();
        rootExtras.putBoolean(CONTENT_STYLE_SUPPORTED, true);
        rootExtras.putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_GRID_ITEM_HINT_VALUE);
        rootExtras.putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE);
        return rootExtras;
    }

    public boolean isPasscodeLocked() {
        final int uptime = (int) (SystemClock.elapsedRealtime() / 1000);
        return SharedConfig.passcodeHash.length() > 0 && (
                SharedConfig.appLocked
                        || SharedConfig.autoLockIn != 0 && SharedConfig.lastPauseTime != 0 && (SharedConfig.lastPauseTime + SharedConfig.autoLockIn) <= uptime
                        || uptime + 5 < SharedConfig.lastPauseTime
        );
    }

    public interface BrowseChildrenCallback {
        void onResult(List<MediaBrowser.MediaItem> items);
    }

    public boolean isChatsLoaded() {
        return chatsLoaded;
    }

    public ArrayList<Long> getMusicDialogs() {
        return dialogs;
    }

    public TLRPC.User getMusicUser(long userId) {
        return users.get(userId);
    }

    public TLRPC.Chat getMusicChat(long chatId) {
        return chats.get(chatId);
    }

    public ArrayList<MessageObject> getMusicMessages(long dialogId) {
        return musicObjects.get(dialogId);
    }

    public Bitmap getRoundedAvatar(File path) {
        return createRoundBitmap(path);
    }

    public void ensureLoaded(Runnable onLoaded) {
        if (chatsLoaded) {
            if (onLoaded != null) AndroidUtilities.runOnUIThread(onLoaded);
            return;
        }
        loadBrowseChildren(MEDIA_ID_ROOT, items -> {
            if (onLoaded != null) onLoaded.run();
        });
    }

    public void loadBrowseChildren(String parentMediaId, BrowseChildrenCallback callback) {
        if (chatsLoaded) {
            callback.onResult(loadChildrenSync(parentMediaId));
            return;
        }
        if (loadingChats) {
            // queue up: when load completes we still want a response. Caller may detach result.
            // For simplicity, attempt again once done by polling on the storage queue.
        }
        loadingChats = true;
        MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        messagesStorage.getStorageQueue().postRunnable(() -> {
            try {
                ArrayList<Long> usersToLoad = new ArrayList<>();
                ArrayList<Long> chatsToLoad = new ArrayList<>();
                SQLiteCursor cursor = messagesStorage.getDatabase().queryFinalized(String.format(Locale.US,
                        "SELECT DISTINCT uid FROM media_v4 WHERE uid != 0 AND mid > 0 AND type = %d", MediaDataController.MEDIA_MUSIC));
                while (cursor.next()) {
                    long dialogId = cursor.longValue(0);
                    if (DialogObject.isEncryptedDialog(dialogId)) {
                        continue;
                    }
                    dialogs.add(dialogId);
                    if (DialogObject.isUserDialog(dialogId)) {
                        usersToLoad.add(dialogId);
                    } else {
                        chatsToLoad.add(-dialogId);
                    }
                }
                cursor.dispose();
                if (!dialogs.isEmpty()) {
                    String ids = TextUtils.join(",", dialogs);
                    cursor = messagesStorage.getDatabase().queryFinalized(String.format(Locale.US,
                            "SELECT uid, data, mid FROM media_v4 WHERE uid IN (%s) AND mid > 0 AND type = %d ORDER BY date DESC, mid DESC",
                            ids, MediaDataController.MEDIA_MUSIC));
                    while (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(1);
                        if (data == null) continue;
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        message.readAttachPath(data, UserConfig.getInstance(currentAccount).clientUserId);
                        data.reuse();
                        if (!MessageObject.isMusicMessage(message)) continue;
                        long did = cursor.longValue(0);
                        message.id = cursor.intValue(2);
                        message.dialog_id = did;
                        ArrayList<MessageObject> arrayList = musicObjects.get(did);
                        ArrayList<MediaSessionCompat.QueueItem> queueList = musicQueues.get(did);
                        if (arrayList == null) {
                            arrayList = new ArrayList<>();
                            musicObjects.put(did, arrayList);
                            queueList = new ArrayList<>();
                            musicQueues.put(did, queueList);
                        }
                        MessageObject messageObject = new MessageObject(currentAccount, message, false, true);
                        arrayList.add(0, messageObject);
                        MediaDescriptionCompat.Builder builder = new MediaDescriptionCompat.Builder()
                                .setMediaId(did + "_" + arrayList.size());
                        builder.setTitle(messageObject.getMusicTitle());
                        builder.setSubtitle(messageObject.getMusicAuthor());
                        queueList.add(0, new MediaSessionCompat.QueueItem(builder.build(), queueList.size()));
                    }
                    cursor.dispose();
                    if (!usersToLoad.isEmpty()) {
                        ArrayList<TLRPC.User> usersArrayList = new ArrayList<>();
                        messagesStorage.getUsersInternal(usersToLoad, usersArrayList);
                        for (TLRPC.User user : usersArrayList) {
                            users.put(user.id, user);
                        }
                    }
                    if (!chatsToLoad.isEmpty()) {
                        ArrayList<TLRPC.Chat> chatsArrayList = new ArrayList<>();
                        messagesStorage.getChatsInternal(TextUtils.join(",", chatsToLoad), chatsArrayList);
                        for (TLRPC.Chat chat : chatsArrayList) {
                            chats.put(chat.id, chat);
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            AndroidUtilities.runOnUIThread(() -> {
                chatsLoaded = true;
                loadingChats = false;
                if (lastSelectedDialog == 0 && !dialogs.isEmpty()) {
                    lastSelectedDialog = dialogs.get(0);
                }
                applyQueueFor(lastSelectedDialog);
                callback.onResult(loadChildrenSync(parentMediaId));
            });
        });
    }

    private List<MediaBrowser.MediaItem> loadChildrenSync(String parentMediaId) {
        List<MediaBrowser.MediaItem> mediaItems = new ArrayList<>();
        if (MEDIA_ID_ROOT.equals(parentMediaId)) {
            for (int a = 0; a < dialogs.size(); a++) {
                long dialogId = dialogs.get(a);
                android.media.MediaDescription.Builder builder = new android.media.MediaDescription.Builder()
                        .setMediaId(MEDIA_ID_CHAT_PREFIX + dialogId);
                TLRPC.FileLocation avatar = null;
                if (DialogObject.isUserDialog(dialogId)) {
                    TLRPC.User user = users.get(dialogId);
                    if (user != null) {
                        builder.setTitle(ContactsController.formatName(user.first_name, user.last_name));
                        if (user.photo != null && !(user.photo.photo_small instanceof TLRPC.TL_fileLocationUnavailable)) {
                            avatar = user.photo.photo_small;
                        }
                    } else {
                        builder.setTitle("DELETED USER");
                    }
                } else {
                    TLRPC.Chat chat = chats.get(-dialogId);
                    if (chat != null) {
                        builder.setTitle(chat.title);
                        if (chat.photo != null && !(chat.photo.photo_small instanceof TLRPC.TL_fileLocationUnavailable)) {
                            avatar = chat.photo.photo_small;
                        }
                    } else {
                        builder.setTitle("DELETED CHAT");
                    }
                }
                Bitmap bitmap = null;
                if (avatar != null) {
                    bitmap = createRoundBitmap(FileLoader.getInstance(currentAccount).getPathToAttach(avatar, true));
                    if (bitmap != null) {
                        builder.setIconBitmap(bitmap);
                    }
                }
                if (avatar == null || bitmap == null) {
                    builder.setIconUri(Uri.parse("android.resource://" + appContext.getPackageName() + "/drawable/contact_blue"));
                }
                mediaItems.add(new MediaBrowser.MediaItem(builder.build(), MediaBrowser.MediaItem.FLAG_BROWSABLE));
            }
        } else if (parentMediaId != null && parentMediaId.startsWith(MEDIA_ID_CHAT_PREFIX)) {
            long did = 0;
            try {
                did = Long.parseLong(parentMediaId.replace(MEDIA_ID_CHAT_PREFIX, ""));
            } catch (Exception e) {
                FileLog.e(e);
            }
            ArrayList<MessageObject> arrayList = musicObjects.get(did);
            if (arrayList != null) {
                for (int a = 0; a < arrayList.size(); a++) {
                    MessageObject messageObject = arrayList.get(a);
                    android.media.MediaDescription.Builder builder = new android.media.MediaDescription.Builder()
                            .setMediaId(did + "_" + a);
                    builder.setTitle(messageObject.getMusicTitle());
                    builder.setSubtitle(messageObject.getMusicAuthor());
                    mediaItems.add(new MediaBrowser.MediaItem(builder.build(), MediaBrowser.MediaItem.FLAG_PLAYABLE));
                }
            }
        }
        return mediaItems;
    }

    private void applyQueueFor(long did) {
        if (did == 0) return;
        ArrayList<MessageObject> arrayList = musicObjects.get(did);
        ArrayList<MediaSessionCompat.QueueItem> queueList = musicQueues.get(did);
        if (arrayList == null || arrayList.isEmpty() || queueList == null) return;
        session.setQueue(queueList);
        if (DialogObject.isUserDialog(did)) {
            TLRPC.User user = users.get(did);
            session.setQueueTitle(user != null
                    ? ContactsController.formatName(user.first_name, user.last_name)
                    : "DELETED USER");
        } else {
            TLRPC.Chat chat = chats.get(-did);
            session.setQueueTitle(chat != null ? chat.title : "DELETED CHAT");
        }
        MessageObject messageObject = arrayList.get(0);
        MediaMetadataCompat.Builder mb = new MediaMetadataCompat.Builder()
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, (long) (messageObject.getDuration() * 1000))
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, messageObject.getMusicAuthor())
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, messageObject.getMusicTitle());
        session.setMetadata(mb.build());
    }

    public void publishMetadata(MessageObject messageObject, @Nullable AudioInfo audioInfo, @Nullable Bitmap albumArt) {
        if (messageObject == null) return;
        MediaMetadataCompat.Builder meta = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, messageObject.getMusicAuthor())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, messageObject.getMusicAuthor())
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, (long) (messageObject.getDuration() * 1000))
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, messageObject.getMusicTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM,
                        audioInfo != null && messageObject.isMusic() ? audioInfo.getAlbum() : null);
        if (albumArt != null && !albumArt.isRecycled()) {
            meta.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt);
        }
        session.setMetadata(meta.build());
    }

    public void publishPlaybackState(PlaybackStateCompat state) {
        session.setPlaybackState(state);
    }

    public long getAvailableActions() {
        long actions = PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                | PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                | PlaybackStateCompat.ACTION_PREPARE
                | PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID
                | PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH
                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_SEEK_TO
                | PlaybackStateCompat.ACTION_SET_REPEAT_MODE
                | PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE;
        MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        if (playing != null) {
            if (!MediaController.getInstance().isMessagePaused()) {
                actions |= PlaybackStateCompat.ACTION_PAUSE;
            }
            if (playing.isMusic()) {
                actions |= PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
            }
        }
        return actions;
    }

    public void updateRepeatMode() {
        int sessionRepeatMode;
        switch (SharedConfig.repeatMode) {
            case 1:
                sessionRepeatMode = PlaybackStateCompat.REPEAT_MODE_ALL;
                break;
            case 2:
                sessionRepeatMode = PlaybackStateCompat.REPEAT_MODE_ONE;
                break;
            default:
                sessionRepeatMode = PlaybackStateCompat.REPEAT_MODE_NONE;
                break;
        }
        session.setRepeatMode(sessionRepeatMode);
    }

    public void updateShuffleMode() {
        session.setShuffleMode(SharedConfig.shuffleMusic
                ? PlaybackStateCompat.SHUFFLE_MODE_ALL
                : PlaybackStateCompat.SHUFFLE_MODE_NONE);
    }

    private Bitmap createRoundBitmap(File path) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;
            Bitmap bitmap = BitmapFactory.decodeFile(path.toString(), options);
            if (bitmap != null) {
                Bitmap result = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
                result.eraseColor(Color.TRANSPARENT);
                Canvas canvas = new Canvas(result);
                BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                if (roundPaint == null) {
                    roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    bitmapRect = new RectF();
                }
                roundPaint.setShader(shader);
                bitmapRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
                canvas.drawRoundRect(bitmapRect, bitmap.getWidth(), bitmap.getHeight(), roundPaint);
                return result;
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return null;
    }

    private final class SessionCallback extends MediaSessionCompat.Callback {

        @Override
        public void onPlay() {
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject == null) {
                if (lastSelectedDialog != 0) {
                    onPlayFromMediaId(lastSelectedDialog + "_" + 0, null);
                }
            } else {
                MediaController.getInstance().playMessage(messageObject);
            }
        }

        @Override
        public void onPause() {
            MediaController.getInstance().pauseMessage(MediaController.getInstance().getPlayingMessageObject());
        }

        @Override
        public void onSkipToNext() {
            MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
            if (playing != null && playing.isMusic()) {
                MediaController.getInstance().playNextMessage();
            }
        }

        @Override
        public void onSkipToPrevious() {
            MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
            if (playing != null && playing.isMusic()) {
                MediaController.getInstance().playPreviousMessage();
            }
        }

        @Override
        public void onSkipToQueueItem(long queueId) {
            MediaController.getInstance().playMessageAtIndex((int) queueId);
        }

        @Override
        public void onSeekTo(long pos) {
            MessageObject object = MediaController.getInstance().getPlayingMessageObject();
            if (object != null) {
                MediaController.getInstance().seekToProgress(object, (float) (pos / 1000.0 / object.getDuration()));
            }
        }

        @Override
        public void onSetRepeatMode(int repeatMode) {
            int newMode;
            switch (repeatMode) {
                case PlaybackStateCompat.REPEAT_MODE_ONE:
                    newMode = 2;
                    break;
                case PlaybackStateCompat.REPEAT_MODE_ALL:
                case PlaybackStateCompat.REPEAT_MODE_GROUP:
                    newMode = 1;
                    break;
                default:
                    newMode = 0;
                    break;
            }
            SharedConfig.setRepeatMode(newMode);
            updateRepeatMode();
            notifyPlayStateForNotificationRefresh();
        }

        @Override
        public void onSetShuffleMode(int shuffleMode) {
            boolean shuffle = shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL
                    || shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_GROUP;
            if (shuffle != SharedConfig.shuffleMusic) {
                MediaController.getInstance().setPlaybackOrderType(shuffle ? 2 : 0);
            }
            updateShuffleMode();
            notifyPlayStateForNotificationRefresh();
        }

        private void notifyPlayStateForNotificationRefresh() {
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount)
                    .postNotificationName(NotificationCenter.messagePlayingPlayStateChanged, 0));
        }

        @Override
        public void onPrepare() {
            // No-op: nothing to prepare without a target. Hosts call prepareFromX with args.
        }

        @Override
        public void onPrepareFromMediaId(String mediaId, Bundle extras) {
            onPlayFromMediaId(mediaId, extras);
        }

        @Override
        public void onPrepareFromSearch(String query, Bundle extras) {
            onPlayFromSearch(query, extras);
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            if (TextUtils.isEmpty(mediaId)) return;
            String[] args = mediaId.split("_");
            if (args.length != 2) return;
            try {
                long did = Long.parseLong(args[0]);
                int id = Integer.parseInt(args[1]);
                ArrayList<MessageObject> arrayList = musicObjects.get(did);
                ArrayList<MediaSessionCompat.QueueItem> queueList = musicQueues.get(did);
                if (arrayList == null || id < 0 || id >= arrayList.size()) return;
                lastSelectedDialog = did;
                MessagesController.getNotificationsSettings(currentAccount).edit()
                        .putLong("auto_lastSelectedDialog", did).apply();
                MediaController.getInstance().setPlaylist(arrayList, arrayList.get(id), 0, false, null);
                session.setQueue(queueList);
                if (DialogObject.isUserDialog(did)) {
                    TLRPC.User user = users.get(did);
                    session.setQueueTitle(user != null
                            ? ContactsController.formatName(user.first_name, user.last_name)
                            : "DELETED USER");
                } else {
                    TLRPC.Chat chat = chats.get(-did);
                    session.setQueueTitle(chat != null ? chat.title : "DELETED CHAT");
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void onPlayFromSearch(String query, Bundle extras) {
            if (query == null || query.length() == 0) return;
            String q = query.toLowerCase();
            for (int a = 0; a < dialogs.size(); a++) {
                long did = dialogs.get(a);
                if (DialogObject.isUserDialog(did)) {
                    TLRPC.User user = users.get(did);
                    if (user == null) continue;
                    String first = user.first_name != null ? user.first_name.toLowerCase() : null;
                    String last = user.last_name != null ? user.last_name.toLowerCase() : null;
                    if (first != null && first.contains(q) || last != null && last.contains(q)) {
                        onPlayFromMediaId(did + "_" + 0, null);
                        return;
                    }
                } else {
                    TLRPC.Chat chat = chats.get(-did);
                    if (chat == null) continue;
                    if (chat.title != null && chat.title.toLowerCase().contains(q)) {
                        onPlayFromMediaId(did + "_" + 0, null);
                        return;
                    }
                }
            }
        }

        @Override
        public void onStop() {
            // session stays alive; let MusicPlayerService handle notification + service teardown
        }

        @Override
        public void onCustomAction(String action, Bundle extras) {
            if (MusicPlayerService.NOTIFY_REPEAT.equals(action)) {
                SharedConfig.setRepeatMode((SharedConfig.repeatMode + 1) % 3);
                updateRepeatMode();
            } else if (MusicPlayerService.NOTIFY_SHUFFLE.equals(action)) {
                MediaController.getInstance().setPlaybackOrderType(SharedConfig.shuffleMusic ? 0 : 2);
                updateShuffleMode();
            }
        }
    }
}

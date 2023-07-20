/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.annotation.TargetApi;
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
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.service.media.MediaBrowserService;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.collection.LongSparseArray;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class MusicBrowserService extends MediaBrowserService implements NotificationCenter.NotificationCenterDelegate {

    private static final String SLOT_RESERVATION_SKIP_TO_NEXT = "com.google.android.gms.car.media.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_NEXT";
    private static final String SLOT_RESERVATION_SKIP_TO_PREV = "com.google.android.gms.car.media.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_PREVIOUS";
    private static final String SLOT_RESERVATION_QUEUE = "com.google.android.gms.car.media.ALWAYS_RESERVE_SPACE_FOR.ACTION_QUEUE";

    private MediaSession mediaSession;
    private static final String MEDIA_ID_ROOT = "__ROOT__";

    private int currentAccount = UserConfig.selectedAccount;
    private boolean chatsLoaded;
    private boolean loadingChats;
    private ArrayList<Long> dialogs = new ArrayList<>();
    private LongSparseArray<TLRPC.User> users = new LongSparseArray<>();
    private LongSparseArray<TLRPC.Chat> chats = new LongSparseArray<>();
    private LongSparseArray<ArrayList<MessageObject>> musicObjects = new LongSparseArray<>();
    private LongSparseArray<ArrayList<MediaSession.QueueItem>> musicQueues = new LongSparseArray<>();

    public static final String ACTION_CMD = "com.example.android.mediabrowserservice.ACTION_CMD";
    public static final String CMD_NAME = "CMD_NAME";
    public static final String CMD_PAUSE = "CMD_PAUSE";

    private Paint roundPaint;
    private RectF bitmapRect;

    private boolean serviceStarted;

    private long lastSelectedDialog;

    private static final int STOP_DELAY = 30000;

    private DelayedStopHandler delayedStopHandler = new DelayedStopHandler(this);

    @Override
    public void onCreate() {
        super.onCreate();
        ApplicationLoader.postInitApplication();

        lastSelectedDialog = AndroidUtilities.getPrefIntOrLong(MessagesController.getNotificationsSettings(currentAccount), "auto_lastSelectedDialog", 0);

        mediaSession = new MediaSession(this, "MusicService");
        setSessionToken(mediaSession.getSessionToken());
        mediaSession.setCallback(new MediaSessionCallback());
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);

        Context context = getApplicationContext();
        Intent intent = new Intent(context, LaunchActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 99, intent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        mediaSession.setSessionActivity(pi);

        Bundle extras = new Bundle();
        extras.putBoolean(SLOT_RESERVATION_QUEUE, true);
        extras.putBoolean(SLOT_RESERVATION_SKIP_TO_PREV, true);
        extras.putBoolean(SLOT_RESERVATION_SKIP_TO_NEXT, true);
        mediaSession.setExtras(extras);

        updatePlaybackState(null);

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidReset);
    }

    @Override
    public int onStartCommand(Intent startIntent, int flags, int startId) {
        /*if (startIntent != null) {
            String action = startIntent.getAction();
            String command = startIntent.getStringExtra(CMD_NAME);
            if (ACTION_CMD.equals(action)) {
                if (CMD_PAUSE.equals(command)) {
                    if (mPlayback != null && mPlayback.isPlaying()) {
                        handlePauseRequest();
                    }
                }
            }
        }*/
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handleStopRequest(null);
        delayedStopHandler.removeCallbacksAndMessages(null);
        mediaSession.release();
    }

    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        if (clientPackageName == null || Process.SYSTEM_UID != clientUid && Process.myUid() != clientUid && !clientPackageName.equals("com.google.android.mediasimulator") && !clientPackageName.equals("com.google.android.projection.gearhead") || passcode()) {
            return null;
        }
        return new BrowserRoot(MEDIA_ID_ROOT, null);
    }

    @Override
    public void onLoadChildren(String parentMediaId, Result<List<MediaBrowser.MediaItem>> result) {
        if (passcode()) {
            Toast.makeText(getApplicationContext(), LocaleController.getString(R.string.EnterYourTelegramPasscode), Toast.LENGTH_LONG).show();
            stopSelf();
            result.detach();
            return;
        }
        if (!chatsLoaded) {
            result.detach();
            if (loadingChats) {
                return;
            }
            loadingChats = true;
            MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
            messagesStorage.getStorageQueue().postRunnable(() -> {
                try {
                    ArrayList<Long> usersToLoad = new ArrayList<>();
                    ArrayList<Long> chatsToLoad = new ArrayList<>();
                    SQLiteCursor cursor = messagesStorage.getDatabase().queryFinalized(String.format(Locale.US, "SELECT DISTINCT uid FROM media_v4 WHERE uid != 0 AND mid > 0 AND type = %d", MediaDataController.MEDIA_MUSIC));
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
                        cursor = messagesStorage.getDatabase().queryFinalized(String.format(Locale.US, "SELECT uid, data, mid FROM media_v4 WHERE uid IN (%s) AND mid > 0 AND type = %d ORDER BY date DESC, mid DESC", ids, MediaDataController.MEDIA_MUSIC));
                        while (cursor.next()) {
                            NativeByteBuffer data = cursor.byteBufferValue(1);
                            if (data != null) {
                                TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                message.readAttachPath(data, UserConfig.getInstance(currentAccount).clientUserId);
                                data.reuse();
                                if (MessageObject.isMusicMessage(message)) {
                                    long did = cursor.longValue(0);
                                    message.id = cursor.intValue(2);
                                    message.dialog_id = did;
                                    ArrayList<MessageObject> arrayList = musicObjects.get(did);
                                    ArrayList<MediaSession.QueueItem> arrayList1 = musicQueues.get(did);
                                    if (arrayList == null) {
                                        arrayList = new ArrayList<>();
                                        musicObjects.put(did, arrayList);
                                        arrayList1 = new ArrayList<>();
                                        musicQueues.put(did, arrayList1);
                                    }
                                    MessageObject messageObject = new MessageObject(currentAccount, message, false, true);
                                    arrayList.add(0, messageObject);
                                    MediaDescription.Builder builder = new MediaDescription.Builder().setMediaId(did + "_" + arrayList.size());
                                    builder.setTitle(messageObject.getMusicTitle());
                                    builder.setSubtitle(messageObject.getMusicAuthor());
                                    arrayList1.add(0, new MediaSession.QueueItem(builder.build(), arrayList1.size()));
                                }
                            }
                        }
                        cursor.dispose();
                        if (!usersToLoad.isEmpty()) {
                            ArrayList<TLRPC.User> usersArrayList = new ArrayList<>();
                            messagesStorage.getUsersInternal(TextUtils.join(",", usersToLoad), usersArrayList);
                            for (int a = 0; a < usersArrayList.size(); a++) {
                                TLRPC.User user = usersArrayList.get(a);
                                users.put(user.id, user);
                            }
                        }
                        if (!chatsToLoad.isEmpty()) {
                            ArrayList<TLRPC.Chat> chatsArrayList = new ArrayList<>();
                            messagesStorage.getChatsInternal(TextUtils.join(",", chatsToLoad), chatsArrayList);
                            for (int a = 0; a < chatsArrayList.size(); a++) {
                                TLRPC.Chat chat = chatsArrayList.get(a);
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
                    loadChildrenImpl(parentMediaId, result);
                    if (lastSelectedDialog == 0 && !dialogs.isEmpty()) {
                        lastSelectedDialog = dialogs.get(0);
                    }
                    if (lastSelectedDialog != 0) {
                        ArrayList<MessageObject> arrayList = musicObjects.get(lastSelectedDialog);
                        ArrayList<MediaSession.QueueItem> arrayList1 = musicQueues.get(lastSelectedDialog);
                        if (arrayList != null && !arrayList.isEmpty()) {
                            mediaSession.setQueue(arrayList1);
                            if (lastSelectedDialog > 0) {
                                TLRPC.User user = users.get(lastSelectedDialog);
                                if (user != null) {
                                    mediaSession.setQueueTitle(ContactsController.formatName(user.first_name, user.last_name));
                                } else {
                                    mediaSession.setQueueTitle("DELETED USER");
                                }
                            } else {
                                TLRPC.Chat chat = chats.get(-lastSelectedDialog);
                                if (chat != null) {
                                    mediaSession.setQueueTitle(chat.title);
                                } else {
                                    mediaSession.setQueueTitle("DELETED CHAT");
                                }
                            }
                            MessageObject messageObject = arrayList.get(0);
                            MediaMetadata.Builder builder = new MediaMetadata.Builder();
                            builder.putLong(MediaMetadata.METADATA_KEY_DURATION, (long) (messageObject.getDuration() * 1000));
                            builder.putString(MediaMetadata.METADATA_KEY_ARTIST, messageObject.getMusicAuthor());
                            builder.putString(MediaMetadata.METADATA_KEY_TITLE, messageObject.getMusicTitle());
                            mediaSession.setMetadata(builder.build());
                        }
                    }
                    updatePlaybackState(null);
                });
            });
        } else {
            loadChildrenImpl(parentMediaId, result);
        }
    }

    private static boolean passcode() {
        final int uptime = (int) (SystemClock.elapsedRealtime() / 1000);
        return (
            SharedConfig.passcodeHash.length() > 0 && (
                SharedConfig.appLocked ||
                SharedConfig.autoLockIn != 0 && SharedConfig.lastPauseTime != 0 && (SharedConfig.lastPauseTime + SharedConfig.autoLockIn) <= uptime ||
                uptime + 5 < SharedConfig.lastPauseTime
            )
        );
    }

    private void loadChildrenImpl(String parentMediaId, Result<List<MediaBrowser.MediaItem>> result) {
        List<MediaBrowser.MediaItem> mediaItems = new ArrayList<>();

        if (MEDIA_ID_ROOT.equals(parentMediaId)) {
            for (int a = 0; a < dialogs.size(); a++) {
                long dialogId = dialogs.get(a);
                MediaDescription.Builder builder = new MediaDescription.Builder().setMediaId("__CHAT_" + dialogId);
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
                    builder.setIconUri(Uri.parse("android.resource://" + getApplicationContext().getPackageName() + "/drawable/contact_blue"));
                }
                mediaItems.add(new MediaBrowser.MediaItem(builder.build(), MediaBrowser.MediaItem.FLAG_BROWSABLE));
            }
        } else if (parentMediaId != null && parentMediaId.startsWith("__CHAT_")) {
            int did = 0;
            try {
                did = Integer.parseInt(parentMediaId.replace("__CHAT_", ""));
            } catch (Exception e) {
                FileLog.e(e);
            }
            ArrayList<MessageObject> arrayList = musicObjects.get(did);
            if (arrayList != null) {
                for (int a = 0; a < arrayList.size(); a++) {
                    MessageObject messageObject = arrayList.get(a);
                    MediaDescription.Builder builder = new MediaDescription.Builder().setMediaId(did + "_" + a);
                    builder.setTitle(messageObject.getMusicTitle());
                    builder.setSubtitle(messageObject.getMusicAuthor());
                    mediaItems.add(new MediaBrowser.MediaItem(builder.build(), MediaBrowser.MediaItem.FLAG_PLAYABLE));
                }
            }
        }
        result.sendResult(mediaItems);
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

    private final class MediaSessionCallback extends MediaSession.Callback {
        @Override
        public void onPlay() {
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject == null) {
                onPlayFromMediaId(lastSelectedDialog + "_" + 0, null);
            } else {
                MediaController.getInstance().playMessage(messageObject);
            }
        }

        @Override
        public void onSkipToQueueItem(long queueId) {
            MediaController.getInstance().playMessageAtIndex((int) queueId);
            handlePlayRequest();
        }

        @Override
        public void onSeekTo(long position) {
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject != null) {
                MediaController.getInstance().seekToProgress(messageObject, position / 1000 / (float) messageObject.getDuration());
            }
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            String[] args = mediaId.split("_");
            if (args.length != 2) {
                return;
            }
            try {
                long did = Long.parseLong(args[0]);
                int id = Integer.parseInt(args[1]);
                ArrayList<MessageObject> arrayList = musicObjects.get(did);
                ArrayList<MediaSession.QueueItem> arrayList1 = musicQueues.get(did);
                if (arrayList == null || id < 0 || id >= arrayList.size()) {
                    return;
                }
                lastSelectedDialog = did;
                MessagesController.getNotificationsSettings(currentAccount).edit().putLong("auto_lastSelectedDialog", did).commit();
                MediaController.getInstance().setPlaylist(arrayList, arrayList.get(id), 0, false, null);
                mediaSession.setQueue(arrayList1);
                if (did > 0) {
                    TLRPC.User user = users.get(did);
                    if (user != null) {
                        mediaSession.setQueueTitle(ContactsController.formatName(user.first_name, user.last_name));
                    } else {
                        mediaSession.setQueueTitle("DELETED USER");
                    }
                } else {
                    TLRPC.Chat chat = chats.get(-did);
                    if (chat != null) {
                        mediaSession.setQueueTitle(chat.title);
                    } else {
                        mediaSession.setQueueTitle("DELETED CHAT");
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            handlePlayRequest();
        }

        @Override
        public void onPause() {
            handlePauseRequest();
        }

        @Override
        public void onStop() {
            handleStopRequest(null);
        }

        @Override
        public void onSkipToNext() {
            MediaController.getInstance().playNextMessage();
        }

        @Override
        public void onSkipToPrevious() {
            MediaController.getInstance().playPreviousMessage();
        }

        @Override
        public void onPlayFromSearch(String query, Bundle extras) {
            if (query == null || query.length() == 0) {
                return;
            }
            query = query.toLowerCase();
            for (int a = 0; a < dialogs.size(); a++) {
                long did = dialogs.get(a);
                if (DialogObject.isUserDialog(did)) {
                    TLRPC.User user = users.get(did);
                    if (user == null) {
                        continue;
                    }
                    if (user.first_name != null && user.first_name.startsWith(query) || user.last_name != null && user.last_name.startsWith(query)) {
                        onPlayFromMediaId(did + "_" + 0, null);
                        break;
                    }
                } else {
                    TLRPC.Chat chat = chats.get(-did);
                    if (chat == null) {
                        continue;
                    }
                    if (chat.title != null && chat.title.toLowerCase().contains(query)) {
                        onPlayFromMediaId(did + "_" + 0, null);
                        break;
                    }
                }
            }
        }
    }

    private void updatePlaybackState(String error) {
        long position = PlaybackState.PLAYBACK_POSITION_UNKNOWN;
        MessageObject playingMessageObject = MediaController.getInstance().getPlayingMessageObject();
        if (playingMessageObject != null) {
            position = playingMessageObject.audioProgressSec * 1000L;
        }

        PlaybackState.Builder stateBuilder = new PlaybackState.Builder().setActions(getAvailableActions());
        int state;
        if (playingMessageObject == null) {
            state = PlaybackState.STATE_STOPPED;
        } else {
            if (MediaController.getInstance().isDownloadingCurrentMessage()) {
                state = PlaybackState.STATE_BUFFERING;
            } else {
                state = MediaController.getInstance().isMessagePaused() ? PlaybackState.STATE_PAUSED : PlaybackState.STATE_PLAYING;
            }
        }

        if (error != null) {
            stateBuilder.setErrorMessage(error);
            state = PlaybackState.STATE_ERROR;
        }
        stateBuilder.setState(state, position, 1.0f, SystemClock.elapsedRealtime());
        if (playingMessageObject != null) {
            stateBuilder.setActiveQueueItemId(MediaController.getInstance().getPlayingMessageObjectNum());
        } else {
            stateBuilder.setActiveQueueItemId(0);
        }

        mediaSession.setPlaybackState(stateBuilder.build());
    }

    private long getAvailableActions() {
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_FROM_MEDIA_ID | PlaybackState.ACTION_PLAY_FROM_SEARCH;
        MessageObject playingMessageObject = MediaController.getInstance().getPlayingMessageObject();
        if (playingMessageObject != null) {
            if (!MediaController.getInstance().isMessagePaused()) {
                actions |= PlaybackState.ACTION_PAUSE;
            }
            actions |= PlaybackState.ACTION_SKIP_TO_PREVIOUS;
            actions |= PlaybackState.ACTION_SKIP_TO_NEXT;
        }
        return actions;
    }

    private void handleStopRequest(String withError) {
        delayedStopHandler.removeCallbacksAndMessages(null);
        delayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);
        updatePlaybackState(withError);
        stopSelf();
        serviceStarted = false;
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidReset);
    }

    private void handlePlayRequest() {
        delayedStopHandler.removeCallbacksAndMessages(null);
        if (!serviceStarted) {
            try {
                startService(new Intent(getApplicationContext(), MusicBrowserService.class));
            } catch (Throwable e) {
                FileLog.e(e);
            }
            serviceStarted = true;
        }

        if (!mediaSession.isActive()) {
            mediaSession.setActive(true);
        }

        MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        if (messageObject == null) {
            return;
        }
        MediaMetadata.Builder builder = new MediaMetadata.Builder();
        builder.putLong(MediaMetadata.METADATA_KEY_DURATION, (long) (messageObject.getDuration() * 1000));
        builder.putString(MediaMetadata.METADATA_KEY_ARTIST, messageObject.getMusicAuthor());
        builder.putString(MediaMetadata.METADATA_KEY_TITLE, messageObject.getMusicTitle());
        AudioInfo audioInfo = MediaController.getInstance().getAudioInfo();
        if (audioInfo != null) {
            Bitmap bitmap = audioInfo.getCover();
            if (bitmap != null) {
                builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap);
            }
        }
        mediaSession.setMetadata(builder.build());
    }

    private void handlePauseRequest() {
        MediaController.getInstance().pauseMessage(MediaController.getInstance().getPlayingMessageObject());
        delayedStopHandler.removeCallbacksAndMessages(null);
        delayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        updatePlaybackState(null);
        handlePlayRequest();
    }

    private static class DelayedStopHandler extends Handler {
        private final WeakReference<MusicBrowserService> mWeakReference;

        private DelayedStopHandler(MusicBrowserService service) {
            mWeakReference = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            MusicBrowserService service = mWeakReference.get();
            if (service != null) {
                MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                if (messageObject != null && !MediaController.getInstance().isMessagePaused()) {
                    return;
                }
                service.stopSelf();
                service.serviceStarted = false;
            }
        }
    }
}

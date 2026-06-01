/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.View;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;

import com.google.android.exoplayer2.C;

import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AudioPlayerAlert;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.LaunchActivity;

import java.io.File;

public class MusicPlayerService extends Service implements NotificationCenter.NotificationCenterDelegate {

    public static final String NOTIFY_PREVIOUS = "org.telegram.android.musicplayer.previous";
    public static final String NOTIFY_CLOSE = "org.telegram.android.musicplayer.close";
    public static final String NOTIFY_PAUSE = "org.telegram.android.musicplayer.pause";
    public static final String NOTIFY_PLAY = "org.telegram.android.musicplayer.play";
    public static final String NOTIFY_NEXT = "org.telegram.android.musicplayer.next";
    public static final String NOTIFY_SEEK = "org.telegram.android.musicplayer.seek";
    public static final String NOTIFY_REPEAT = "org.telegram.android.musicplayer.repeat";
    public static final String NOTIFY_SHUFFLE = "org.telegram.android.musicplayer.shuffle";

    private static final int ID_NOTIFICATION = 5;

    private RemoteControlClient remoteControlClient;
    private AudioManager audioManager;

    private static boolean supportBigNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    private static boolean supportLockScreenControls = Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || !TextUtils.isEmpty(AndroidUtilities.getSystemProperty("ro.miui.ui.version.code"));

    private MediaSessionCompat mediaSession;
    private PlaybackStateCompat.Builder playbackState;
    private Bitmap albumArtPlaceholder;
    private int notificationMessageID;
    private ImageReceiver imageReceiver;
    private boolean foregroundServiceIsStarted;

    private String loadingFilePath;

    private BroadcastReceiver headsetPlugReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                MediaController.getInstance().pauseMessage(MediaController.getInstance().getPlayingMessageObject());
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagePlayingDidSeek);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.httpFileDidLoad);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.fileLoaded);
        }
        imageReceiver = new ImageReceiver(null);
        imageReceiver.setDelegate((imageReceiver, set, thumb, memCache) -> {
            if (set && !TextUtils.isEmpty(loadingFilePath)) {
                MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                if (messageObject != null) {
                    createNotification(messageObject, true);
                }
                loadingFilePath = null;
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaSession = new MediaSessionCompat(this, "telegramAudioPlayer");
            playbackState = new PlaybackStateCompat.Builder();
            albumArtPlaceholder = Bitmap.createBitmap(AndroidUtilities.dp(102), AndroidUtilities.dp(102), Bitmap.Config.ARGB_8888);
            Drawable placeholder = getResources().getDrawable(R.drawable.nocover_big);
            placeholder.setBounds(0, 0, albumArtPlaceholder.getWidth(), albumArtPlaceholder.getHeight());
            placeholder.draw(new Canvas(albumArtPlaceholder));
            mediaSession.setCallback(new MediaSessionCompat.Callback() {
                @Override
                public void onPlay() {
                    MediaController.getInstance().playMessage(MediaController.getInstance().getPlayingMessageObject());
                }

                @Override
                public void onPause() {
                    MediaController.getInstance().pauseMessage(MediaController.getInstance().getPlayingMessageObject());
                }

                @Override
                public void onSkipToNext() {
                    MessageObject playingMessageObject = MediaController.getInstance().getPlayingMessageObject();
                    if (playingMessageObject != null && playingMessageObject.isMusic()) {
                        MediaController.getInstance().playNextMessage();
                    }
                }

                @Override
                public void onSkipToPrevious() {
                    MessageObject playingMessageObject = MediaController.getInstance().getPlayingMessageObject();
                    if (playingMessageObject != null && playingMessageObject.isMusic()) {
                        MediaController.getInstance().playPreviousMessage();
                    }
                }

                @Override
                public void onSeekTo(long pos) {
                    MessageObject object = MediaController.getInstance().getPlayingMessageObject();
                    if (object != null) {
                        MediaController.getInstance().seekToProgress(object, pos / 1000 / (float) object.getDuration());
                        updatePlaybackState(pos);
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
                    MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                    if (messageObject != null) {
                        createNotification(messageObject, false);
                    }
                }

                @Override
                public void onSetShuffleMode(int shuffleMode) {
                    if (shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL || shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_GROUP) {
                        if (!SharedConfig.shuffleMusic) {
                            MediaController.getInstance().setPlaybackOrderType(2);
                        }
                    } else {
                        if (SharedConfig.shuffleMusic) {
                            MediaController.getInstance().setPlaybackOrderType(0);
                        }
                    }
                    updateShuffleMode();
                    MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                    if (messageObject != null) {
                        createNotification(messageObject, false);
                    }
                }

                @Override
                public void onCustomAction(String action, android.os.Bundle extras) {
                    if (NOTIFY_REPEAT.equals(action)) {
                        SharedConfig.setRepeatMode((SharedConfig.repeatMode + 1) % 3);
                        updateRepeatMode();
                        if (AudioPlayerAlert.instance != null) {
                            AudioPlayerAlert.instance.updateRepeatButton();
                        }
                    } else if (NOTIFY_SHUFFLE.equals(action)) {
                        if (SharedConfig.shuffleMusic) {
                            MediaController.getInstance().setPlaybackOrderType(0);
                        } else {
                            MediaController.getInstance().setPlaybackOrderType(2);
                        }
                        updateShuffleMode();
                        if (AudioPlayerAlert.instance != null) {
                            AudioPlayerAlert.instance.updateRepeatButton();
                        }
                    }
                    MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                    if (messageObject != null) {
                        createNotification(messageObject, false);
                    }
                }

                @Override
                public void onStop() {
                    //stopSelf();
                }
            });
            mediaSession.setActive(true);
            updateRepeatMode();
            updateShuffleMode();
        }

        registerReceiver(headsetPlugReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));

        super.onCreate();
    }

    @SuppressLint("NewApi")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent != null && (getPackageName() + ".STOP_PLAYER").equals(intent.getAction())) {
                MediaController.getInstance().cleanupPlayer(true, true);
                return START_NOT_STICKY;
            }
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject == null) {
                AndroidUtilities.runOnUIThread(this::stopSelf);
                return START_STICKY;
            }
            if (supportLockScreenControls) {
                ComponentName remoteComponentName = new ComponentName(getApplicationContext(), MusicPlayerReceiver.class.getName());
                try {
                    if (remoteControlClient == null) {
                        audioManager.registerMediaButtonEventReceiver(remoteComponentName);
                        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                        mediaButtonIntent.setComponent(remoteComponentName);
                        PendingIntent mediaPendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, fixIntentFlags(PendingIntent.FLAG_MUTABLE));
                        remoteControlClient = new RemoteControlClient(mediaPendingIntent);
                        audioManager.registerRemoteControlClient(remoteControlClient);
                    }
                    remoteControlClient.setTransportControlFlags(RemoteControlClient.FLAG_KEY_MEDIA_PLAY | RemoteControlClient.FLAG_KEY_MEDIA_PAUSE | RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE |
                            RemoteControlClient.FLAG_KEY_MEDIA_STOP | RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS | RemoteControlClient.FLAG_KEY_MEDIA_NEXT);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            createNotification(messageObject, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return START_STICKY;
    }

    private Bitmap loadArtworkFromUrl(String artworkUrl, boolean big, boolean tryLoad) {
        File path = ImageLoader.getHttpFilePath(artworkUrl, "jpg");
        if (path.exists()) {
            return ImageLoader.loadBitmap(path.getAbsolutePath(), null, big ? 600 : 100, big ? 600 : 100, false);
        }
        if (tryLoad) {
            loadingFilePath = path.getAbsolutePath();
            if (!big) {
                imageReceiver.setImage(artworkUrl, "48_48", null, null, 0);
            }
        } else {
            loadingFilePath = null;
        }
        return null;
    }


    private Bitmap getAvatarBitmap(TLObject userOrChat, boolean big, boolean tryLoad) {
        int size = big ? 600 : 100;
        Bitmap bitmap = null;
        try {
            if (userOrChat instanceof TLRPC.User) {
                TLRPC.User user = (TLRPC.User) userOrChat;
                TLRPC.FileLocation photoPath = big ? user.photo.photo_big : user.photo.photo_small;
                if (photoPath != null) {
                    File path = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(photoPath, true);
                    if (path.exists()) {
                        return ImageLoader.loadBitmap(path.getAbsolutePath(), null, size, size, false);
                    }
                    if (big) {
                        if (tryLoad) {
                            loadingFilePath = FileLoader.getAttachFileName(photoPath);
                            ImageLocation photoLocation = ImageLocation.getForUser(UserConfig.selectedAccount, user, ImageLocation.TYPE_BIG);
                            imageReceiver.setImage(photoLocation, "", null, null, null, 0);
                        } else {
                            loadingFilePath = null;
                        }
                    }
                }
            } else {
                TLRPC.Chat chat = (TLRPC.Chat) userOrChat;
                TLRPC.FileLocation photoPath = big ? chat.photo.photo_big : chat.photo.photo_small;
                if (photoPath != null) {
                    File path = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(photoPath, true);
                    if (path.exists()) {
                        return ImageLoader.loadBitmap(path.getAbsolutePath(), null, size, size, false);
                    }
                    if (big) {
                        if (tryLoad) {
                            loadingFilePath = FileLoader.getAttachFileName(photoPath);
                            ImageLocation photoLocation = ImageLocation.getForChat(chat, ImageLocation.TYPE_BIG);
                            imageReceiver.setImage(photoLocation, "", null, null, null, 0);
                        } else {
                            loadingFilePath = null;
                        }
                    }
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        if (!big) {
            Theme.createDialogsResources(this);
            AvatarDrawable placeholder;
            if (userOrChat instanceof TLRPC.User) {
                placeholder = new AvatarDrawable((TLRPC.User) userOrChat);
            } else {
                placeholder = new AvatarDrawable((TLRPC.Chat) userOrChat);
            }
            placeholder.setRoundRadius(1);
            bitmap = Bitmap.createBitmap(AndroidUtilities.dp(size), AndroidUtilities.dp(size), Bitmap.Config.ARGB_8888);
            placeholder.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
            placeholder.draw(new Canvas(bitmap));
        }
        return bitmap;
    }

    @SuppressLint("NewApi")
    private void createNotification(MessageObject messageObject, boolean forBitmap) {
        String contentTitle = messageObject.getMusicTitle();
        String contentText = messageObject.getMusicAuthor();
        AudioInfo audioInfo = MediaController.getInstance().getAudioInfo();
        Intent intent = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
        if (messageObject.isMusic()) {
            intent.setAction("com.tmessages.openplayer");
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
        } else if (messageObject.isVoice() || messageObject.isRoundVideo()) {
            intent.setAction(Intent.ACTION_VIEW);
            long fromId = 0;
            TLRPC.Message owner = messageObject.messageOwner;
            if (owner.peer_id instanceof TLRPC.TL_peerUser) {
                fromId = owner.peer_id.user_id;
            } else if (owner.peer_id instanceof TLRPC.TL_peerChat) {
                fromId = owner.peer_id.chat_id;
            } else if (owner.peer_id instanceof TLRPC.TL_peerChannel) {
                fromId = owner.peer_id.channel_id;
            }
            if (fromId != 0) {
                if (owner.peer_id instanceof TLRPC.TL_peerUser) {
                    intent.setData(Uri.parse("tg://openmessage?user_id=" + fromId + "&message_id=" + messageObject.getId()));
                } else {
                    intent.setData(Uri.parse("tg://openmessage?chat_id=" + fromId + "&message_id=" + messageObject.getId()));
                }
            }
        }
        PendingIntent contentIntent = PendingIntent.getActivity(ApplicationLoader.applicationContext, 0, intent, fixIntentFlags(PendingIntent.FLAG_MUTABLE));

        Notification notification;
        Bitmap albumArt = null;
        Bitmap fullAlbumArt = null;
        long duration = (long) (messageObject.getDuration() * 1000);
        if (messageObject.isMusic()) {
            String artworkUrl = messageObject.getArtworkUrl(true);
            String artworkUrlBig = messageObject.getArtworkUrl(false);

            albumArt = audioInfo != null ? audioInfo.getSmallCover() : null;
            fullAlbumArt = audioInfo != null ? audioInfo.getCover() : null;

            loadingFilePath = null;
            imageReceiver.setImageBitmap((BitmapDrawable) null);
            if (albumArt == null && !TextUtils.isEmpty(artworkUrl)) {
                fullAlbumArt = loadArtworkFromUrl(artworkUrlBig, true, !forBitmap);
                if (fullAlbumArt == null) {
                    fullAlbumArt = albumArt = loadArtworkFromUrl(artworkUrl, false, !forBitmap);
                } else {
                    albumArt = loadArtworkFromUrl(artworkUrlBig, false, !forBitmap);
                }
            } else {
                loadingFilePath = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(messageObject.getDocument()).getAbsolutePath();
            }
        } else if (messageObject.isVoice() || messageObject.isRoundVideo()) {
            long senderId = messageObject.getSenderId();
            if (messageObject.isFromUser()) {
                TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(senderId);
                if (user != null) {
                    contentTitle = UserObject.getUserName(user);
                    fullAlbumArt = getAvatarBitmap(user, true, !forBitmap);
                    albumArt = getAvatarBitmap(user, false, !forBitmap);

                }
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-senderId);
                if (chat != null) {
                    contentTitle = chat.title;
                    fullAlbumArt = getAvatarBitmap(chat, true, !forBitmap);
                    albumArt = getAvatarBitmap(chat, false, !forBitmap);
                }
            }

            if (fullAlbumArt == null && albumArt != null) {
                fullAlbumArt = albumArt;
            }
            if (messageObject.isVoice()) {
                contentText = LocaleController.getString(R.string.AttachAudio);
            } else {
                contentText = LocaleController.getString(R.string.AttachRound);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            boolean isPlaying = !MediaController.getInstance().isMessagePaused();

            PendingIntent pendingPrev = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(NOTIFY_PREVIOUS).setComponent(new ComponentName(this, MusicPlayerReceiver.class)), fixIntentFlags(PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_CANCEL_CURRENT));
            //PendingIntent pendingStop = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(NOTIFY_CLOSE).setComponent(new ComponentName(this, MusicPlayerReceiver.class)), PendingIntent.FLAG_CANCEL_CURRENT);
            PendingIntent pendingStop = PendingIntent.getService(getApplicationContext(), 0, new Intent(this, getClass()).setAction(getPackageName() + ".STOP_PLAYER"), fixIntentFlags(PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_CANCEL_CURRENT));
            PendingIntent pendingPlaypause = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(isPlaying ? NOTIFY_PAUSE : NOTIFY_PLAY).setComponent(new ComponentName(this, MusicPlayerReceiver.class)), fixIntentFlags(PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_CANCEL_CURRENT));
            PendingIntent pendingNext = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(NOTIFY_NEXT).setComponent(new ComponentName(this, MusicPlayerReceiver.class)), fixIntentFlags(PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_CANCEL_CURRENT));
            PendingIntent pendingSeek = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(NOTIFY_SEEK).setComponent(new ComponentName(this, MusicPlayerReceiver.class)), fixIntentFlags(PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_CANCEL_CURRENT));
            PendingIntent pendingRepeat = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(NOTIFY_REPEAT).setComponent(new ComponentName(this, MusicPlayerReceiver.class)), fixIntentFlags(PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_CANCEL_CURRENT));
            PendingIntent pendingShuffle = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(NOTIFY_SHUFFLE).setComponent(new ComponentName(this, MusicPlayerReceiver.class)), fixIntentFlags(PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_CANCEL_CURRENT));

            Notification.MediaStyle mediaStyle = new Notification.MediaStyle().setMediaSession((android.media.session.MediaSession.Token) mediaSession.getSessionToken().getToken());
            if (messageObject.isMusic()) {
                mediaStyle.setShowActionsInCompactView(0, 1, 2, 3, 4);
            } else if (messageObject.isVoice() || messageObject.isRoundVideo()) {
                mediaStyle.setShowActionsInCompactView(0);
            }
            Notification.Builder bldr = new Notification.Builder(this);
            bldr.setSmallIcon(R.drawable.player)
                    .setOngoing(isPlaying)
                    .setContentTitle(contentTitle)
                    .setContentText(contentText)
                    .setSubText(audioInfo != null && messageObject.isMusic() ? audioInfo.getAlbum() : null)
                    .setContentIntent(contentIntent)
                    .setDeleteIntent(pendingStop)
                    .setShowWhen(false)
                    .setCategory(Notification.CATEGORY_TRANSPORT)
                    .setPriority(Notification.PRIORITY_MAX)
                    .setStyle(mediaStyle);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationsController.checkOtherNotificationsChannel();
                bldr.setChannelId(NotificationsController.OTHER_NOTIFICATIONS_CHANNEL);
            }
            if (albumArt != null) {
                bldr.setLargeIcon(albumArt);
            } else {
                bldr.setLargeIcon(albumArtPlaceholder);
            }

            final String nextDescription = LocaleController.getString(R.string.Next);
            final String previousDescription = LocaleController.getString(R.string.AccDescrPrevious);

            playbackState = new PlaybackStateCompat.Builder();
            if (MediaController.getInstance().isDownloadingCurrentMessage()) {
                playbackState.setState(PlaybackStateCompat.STATE_BUFFERING, 0, 1).setActions(0);
                if (messageObject.isMusic()) {
                    bldr.addAction(new Notification.Action.Builder(R.drawable.ic_action_previous, previousDescription, pendingPrev).build());
                }
                bldr.addAction(new Notification.Action.Builder(R.drawable.loading_animation2, LocaleController.getString(R.string.Loading), null).build());
                if (messageObject.isMusic()) {
                    bldr.addAction(new Notification.Action.Builder(R.drawable.ic_action_next, nextDescription, pendingNext).build());
                }
            } else {
                long actions = PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_SET_REPEAT_MODE | PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE;
                if (messageObject.isMusic()) {
                    actions |= PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
                }
                if (messageObject.isMusic()) {
                    int shuffleIcon = SharedConfig.shuffleMusic ? R.drawable.player_new_shuffle : R.drawable.player_new_shuffle_off;
                    playbackState.addCustomAction(new PlaybackStateCompat.CustomAction.Builder(
                            NOTIFY_SHUFFLE, LocaleController.getString(R.string.ShuffleList), shuffleIcon).build());
                }
                playbackState.setState(isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                                MediaController.getInstance().getPlayingMessageObject().audioProgressSec * 1000L,
                                getPlaybackSpeed(isPlaying, messageObject))
                        .setActions(actions);
                if (messageObject.isMusic()) {
                    int repeatIcon;
                    switch (SharedConfig.repeatMode) {
                        case 1: repeatIcon = R.drawable.player_new_repeatall; break;
                        case 2: repeatIcon = R.drawable.player_new_repeatone; break;
                        default: repeatIcon = R.drawable.player_new_repeat_off; break;
                    }
                    playbackState.addCustomAction(new PlaybackStateCompat.CustomAction.Builder(
                            NOTIFY_REPEAT, LocaleController.getString(R.string.RepeatSong), repeatIcon).build());
                }
                final String playPauseTitle = isPlaying ? LocaleController.getString(R.string.AccActionPause) : LocaleController.getString(R.string.AccActionPlay);
                if (messageObject.isMusic()) {
                    int shuffleIcon = SharedConfig.shuffleMusic ? R.drawable.player_new_shuffle : R.drawable.player_new_shuffle_off;
                    bldr.addAction(new Notification.Action.Builder(shuffleIcon, LocaleController.getString(R.string.ShuffleList), pendingShuffle).build());
                    bldr.addAction(new Notification.Action.Builder(R.drawable.ic_action_previous, previousDescription, pendingPrev).build());
                }
                bldr.addAction(new Notification.Action.Builder(isPlaying ? R.drawable.ic_action_pause : R.drawable.ic_action_play, playPauseTitle, pendingPlaypause).build());
                if (messageObject.isMusic()) {
                    bldr.addAction(new Notification.Action.Builder(R.drawable.ic_action_next, nextDescription, pendingNext).build());
                    int repeatIcon;
                    switch (SharedConfig.repeatMode) {
                        case 1: repeatIcon = R.drawable.player_new_repeatall; break;
                        case 2: repeatIcon = R.drawable.player_new_repeatone; break;
                        default: repeatIcon = R.drawable.player_new_repeat_off; break;
                    }
                    bldr.addAction(new Notification.Action.Builder(repeatIcon, LocaleController.getString(R.string.RepeatSong), pendingRepeat).build());
                }
            }

            mediaSession.setPlaybackState(playbackState.build());
            updateRepeatMode();
            updateShuffleMode();
            MediaMetadataCompat.Builder meta = new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, contentText)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, contentText)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, contentTitle)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, audioInfo != null && messageObject.isMusic() ? audioInfo.getAlbum() : null);
            if (fullAlbumArt != null && !fullAlbumArt.isRecycled()) {
                meta.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, fullAlbumArt);
            }

            mediaSession.setMetadata(meta.build());

            bldr.setVisibility(Notification.VISIBILITY_PUBLIC);

            notification = bldr.build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!foregroundServiceIsStarted) {
                    foregroundServiceIsStarted = true;
                    startForeground(ID_NOTIFICATION, notification);
                } else {
                    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    nm.notify(ID_NOTIFICATION, notification);
                }
            } else {
                if (isPlaying) {
                    startForeground(ID_NOTIFICATION, notification);
                } else {
                    stopForeground(false);
                    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    nm.notify(ID_NOTIFICATION, notification);
                }
            }

        } else {
            RemoteViews simpleContentView = new RemoteViews(getApplicationContext().getPackageName(), R.layout.player_small_notification);
            RemoteViews expandedView = null;
            if (supportBigNotifications) {
                expandedView = new RemoteViews(getApplicationContext().getPackageName(), R.layout.player_big_notification);
            }

            notification = new NotificationCompat.Builder(getApplicationContext())
                    .setSmallIcon(R.drawable.player)
                    .setContentIntent(contentIntent)
                    .setChannelId(NotificationsController.OTHER_NOTIFICATIONS_CHANNEL)
                    .setContentTitle(contentTitle).build();

            notification.contentView = simpleContentView;
            if (supportBigNotifications) {
                notification.bigContentView = expandedView;
            }

            setListeners(simpleContentView);
            if (supportBigNotifications) {
                setListeners(expandedView);
            }

            if (albumArt != null) {
                notification.contentView.setImageViewBitmap(R.id.player_album_art, albumArt);
                if (supportBigNotifications) {
                    notification.bigContentView.setImageViewBitmap(R.id.player_album_art, albumArt);
                }
            } else {
                notification.contentView.setImageViewResource(R.id.player_album_art, R.drawable.nocover_small);
                if (supportBigNotifications) {
                    notification.bigContentView.setImageViewResource(R.id.player_album_art, R.drawable.nocover_big);
                }
            }
            if (MediaController.getInstance().isDownloadingCurrentMessage()) {
                notification.contentView.setViewVisibility(R.id.player_pause, View.GONE);
                notification.contentView.setViewVisibility(R.id.player_play, View.GONE);
                notification.contentView.setViewVisibility(R.id.player_next, View.GONE);
                notification.contentView.setViewVisibility(R.id.player_previous, View.GONE);
                notification.contentView.setViewVisibility(R.id.player_progress_bar, View.VISIBLE);
                if (supportBigNotifications) {
                    notification.bigContentView.setViewVisibility(R.id.player_pause, View.GONE);
                    notification.bigContentView.setViewVisibility(R.id.player_play, View.GONE);
                    notification.bigContentView.setViewVisibility(R.id.player_next, View.GONE);
                    notification.bigContentView.setViewVisibility(R.id.player_previous, View.GONE);
                    notification.bigContentView.setViewVisibility(R.id.player_progress_bar, View.VISIBLE);
                }
            } else {
                notification.contentView.setViewVisibility(R.id.player_progress_bar, View.GONE);
                if (messageObject.isMusic()) {
                    notification.contentView.setViewVisibility(R.id.player_next, View.VISIBLE);
                    notification.contentView.setViewVisibility(R.id.player_previous, View.VISIBLE);
                } else {
                    notification.bigContentView.setViewVisibility(R.id.player_next, View.GONE);
                    notification.bigContentView.setViewVisibility(R.id.player_previous, View.GONE);
                }
                if (supportBigNotifications) {
                    if (messageObject.isMusic()) {
                        notification.bigContentView.setViewVisibility(R.id.player_next, View.VISIBLE);
                        notification.bigContentView.setViewVisibility(R.id.player_previous, View.VISIBLE);
                    } else {
                        notification.bigContentView.setViewVisibility(R.id.player_next, View.GONE);
                        notification.bigContentView.setViewVisibility(R.id.player_previous, View.GONE);
                    }
                    notification.bigContentView.setViewVisibility(R.id.player_progress_bar, View.GONE);
                }

                if (MediaController.getInstance().isMessagePaused()) {
                    notification.contentView.setViewVisibility(R.id.player_pause, View.GONE);
                    notification.contentView.setViewVisibility(R.id.player_play, View.VISIBLE);
                    if (supportBigNotifications) {
                        notification.bigContentView.setViewVisibility(R.id.player_pause, View.GONE);
                        notification.bigContentView.setViewVisibility(R.id.player_play, View.VISIBLE);
                    }
                } else {
                    notification.contentView.setViewVisibility(R.id.player_pause, View.VISIBLE);
                    notification.contentView.setViewVisibility(R.id.player_play, View.GONE);
                    if (supportBigNotifications) {
                        notification.bigContentView.setViewVisibility(R.id.player_pause, View.VISIBLE);
                        notification.bigContentView.setViewVisibility(R.id.player_play, View.GONE);
                    }
                }
            }

            notification.contentView.setTextViewText(R.id.player_song_name, contentTitle);
            notification.contentView.setTextViewText(R.id.player_author_name, contentText);
            if (supportBigNotifications) {
                notification.bigContentView.setTextViewText(R.id.player_song_name, contentTitle);
                notification.bigContentView.setTextViewText(R.id.player_author_name, contentText);
                notification.bigContentView.setTextViewText(R.id.player_album_title, audioInfo != null && !TextUtils.isEmpty(audioInfo.getAlbum()) ? audioInfo.getAlbum() : "");
            }
            notification.flags |= Notification.FLAG_ONGOING_EVENT;
            startForeground(ID_NOTIFICATION, notification);
        }

        if (remoteControlClient != null) {
            int currentID = MediaController.getInstance().getPlayingMessageObject().getId();
            if (notificationMessageID != currentID) {
                notificationMessageID = currentID;
                RemoteControlClient.MetadataEditor metadataEditor = remoteControlClient.editMetadata(true);
                metadataEditor.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, contentText);
                metadataEditor.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, contentTitle);
                if (audioInfo != null && !TextUtils.isEmpty(audioInfo.getAlbum())) {
                    metadataEditor.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, audioInfo.getAlbum());
                }
                metadataEditor.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, MediaController.getInstance().getPlayingMessageObject().audioPlayerDuration * 1000L);
                if (fullAlbumArt != null) {
                    try {
                        metadataEditor.putBitmap(RemoteControlClient.MetadataEditor.BITMAP_KEY_ARTWORK, fullAlbumArt);
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
                metadataEditor.apply();
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (remoteControlClient == null || MediaController.getInstance().getPlayingMessageObject() == null) {
                            return;
                        }
                        if (MediaController.getInstance().getPlayingMessageObject().audioPlayerDuration == C.TIME_UNSET) {
                            AndroidUtilities.runOnUIThread(this, 500);
                            return;
                        }
                        RemoteControlClient.MetadataEditor metadataEditor = remoteControlClient.editMetadata(false);
                        metadataEditor.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, MediaController.getInstance().getPlayingMessageObject().audioPlayerDuration * 1000L);
                        metadataEditor.apply();
                        if (Build.VERSION.SDK_INT >= 18) {
                            remoteControlClient.setPlaybackState(MediaController.getInstance().isMessagePaused() ? RemoteControlClient.PLAYSTATE_PAUSED : RemoteControlClient.PLAYSTATE_PLAYING,
                                    Math.max(MediaController.getInstance().getPlayingMessageObject().audioProgressSec * 1000L, 100),
                                    MediaController.getInstance().isMessagePaused() ? 0f : 1f);
                        } else {
                            remoteControlClient.setPlaybackState(MediaController.getInstance().isMessagePaused() ? RemoteControlClient.PLAYSTATE_PAUSED : RemoteControlClient.PLAYSTATE_PLAYING);
                        }
                    }
                }, 1000);
            }
            if (MediaController.getInstance().isDownloadingCurrentMessage()) {
                remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_BUFFERING);
            } else {
                RemoteControlClient.MetadataEditor metadataEditor = remoteControlClient.editMetadata(false);
                metadataEditor.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, MediaController.getInstance().getPlayingMessageObject().audioPlayerDuration * 1000L);
                metadataEditor.apply();
                if (Build.VERSION.SDK_INT >= 18) {
                    remoteControlClient.setPlaybackState(MediaController.getInstance().isMessagePaused() ? RemoteControlClient.PLAYSTATE_PAUSED : RemoteControlClient.PLAYSTATE_PLAYING,
                            Math.max(MediaController.getInstance().getPlayingMessageObject().audioProgressSec * 1000L, 100),
                            MediaController.getInstance().isMessagePaused() ? 0f : 1f);
                } else {
                    remoteControlClient.setPlaybackState(MediaController.getInstance().isMessagePaused() ? RemoteControlClient.PLAYSTATE_PAUSED : RemoteControlClient.PLAYSTATE_PLAYING);
                }
            }
        }
    }

    private void updatePlaybackState(long seekTo) {
        if (Build.VERSION.SDK_INT < 21) {
            return;
        }
        playbackState = new PlaybackStateCompat.Builder();
        boolean isPlaying = !MediaController.getInstance().isMessagePaused();
        if (MediaController.getInstance().isDownloadingCurrentMessage()) {
            playbackState.setState(PlaybackStateCompat.STATE_BUFFERING, 0, 1).setActions(0);
        } else {
            long actions = PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_SET_REPEAT_MODE | PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE;
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject != null && messageObject.isMusic()) {
                actions |= PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
                int shuffleIcon = SharedConfig.shuffleMusic ? R.drawable.player_new_shuffle : R.drawable.player_new_shuffle_off;
                playbackState.addCustomAction(new PlaybackStateCompat.CustomAction.Builder(
                        NOTIFY_SHUFFLE, LocaleController.getString(R.string.ShuffleList), shuffleIcon).build());
            }
            playbackState.setState(isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                            seekTo,
                            getPlaybackSpeed(isPlaying, messageObject))
                    .setActions(actions);
            if (messageObject != null && messageObject.isMusic()) {
                int repeatIcon;
                switch (SharedConfig.repeatMode) {
                    case 1: repeatIcon = R.drawable.player_new_repeatall; break;
                    case 2: repeatIcon = R.drawable.player_new_repeatone; break;
                    default: repeatIcon = R.drawable.player_new_repeat_off; break;
                }
                playbackState.addCustomAction(new PlaybackStateCompat.CustomAction.Builder(
                        NOTIFY_REPEAT, LocaleController.getString(R.string.RepeatSong), repeatIcon).build());
            }
        }
        mediaSession.setPlaybackState(playbackState.build());
    }

    private void updateRepeatMode() {
        if (mediaSession != null) {
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
            mediaSession.setRepeatMode(sessionRepeatMode);
        }
    }

    private void updateShuffleMode() {
        if (mediaSession != null) {
            mediaSession.setShuffleMode(SharedConfig.shuffleMusic ? PlaybackStateCompat.SHUFFLE_MODE_ALL : PlaybackStateCompat.SHUFFLE_MODE_NONE);
        }
    }

    private float getPlaybackSpeed(boolean isPlaying, MessageObject messageObject) {
        if (isPlaying) {
            if (messageObject != null && (messageObject.isVoice() || messageObject.isRoundVideo())) {
                return MediaController.getInstance().getPlaybackSpeed(false);
            }
            return 1;
        } else {
            return 0;
        }
    }

    public void setListeners(RemoteViews view) {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(NOTIFY_PREVIOUS), fixIntentFlags(PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        view.setOnClickPendingIntent(R.id.player_previous, pendingIntent);
        pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(NOTIFY_CLOSE), fixIntentFlags(PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        view.setOnClickPendingIntent(R.id.player_close, pendingIntent);
        pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(NOTIFY_PAUSE), fixIntentFlags(PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        view.setOnClickPendingIntent(R.id.player_pause, pendingIntent);
        pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(NOTIFY_NEXT), fixIntentFlags(PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        view.setOnClickPendingIntent(R.id.player_next, pendingIntent);
        pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(NOTIFY_PLAY), fixIntentFlags(PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        view.setOnClickPendingIntent(R.id.player_play, pendingIntent);
    }

    private int fixIntentFlags(int flags) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && XiaomiUtilities.isMIUI()) {
            return flags & ~(PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_MUTABLE);
        }
        return flags;
    }

    @SuppressLint("NewApi")
    @Override
    public void onDestroy() {
        unregisterReceiver(headsetPlugReceiver);
        super.onDestroy();
        stopForeground(true);
        if (remoteControlClient != null) {
            RemoteControlClient.MetadataEditor metadataEditor = remoteControlClient.editMetadata(true);
            metadataEditor.clear();
            metadataEditor.apply();
            audioManager.unregisterRemoteControlClient(remoteControlClient);
        }
        if (mediaSession != null) {
            mediaSession.release();
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingDidSeek);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.httpFileDidLoad);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.fileLoaded);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingPlayStateChanged) {
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject != null) {
                createNotification(messageObject, false);
            } else {
                stopSelf();
            }
        } else if (id == NotificationCenter.messagePlayingDidSeek) {
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject == null) {
                return;
            }
            long progress = Math.round(messageObject.audioPlayerDuration * (float) args[1]) * 1000L;
            updatePlaybackState(progress);
            if (remoteControlClient != null && Build.VERSION.SDK_INT >= 18) {
                remoteControlClient.setPlaybackState(MediaController.getInstance().isMessagePaused() ? RemoteControlClient.PLAYSTATE_PAUSED : RemoteControlClient.PLAYSTATE_PLAYING,
                        progress,
                        MediaController.getInstance().isMessagePaused() ? 0f : 1f);
            }
        } else if (id == NotificationCenter.httpFileDidLoad) {
            final String path = (String) args[0];
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject != null && loadingFilePath != null && loadingFilePath.equals(path)) {
                createNotification(messageObject, false);
            }
        } else if (id == NotificationCenter.fileLoaded) {
            final String path = (String) args[0];
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject != null && loadingFilePath != null && loadingFilePath.equals(path)) {
                createNotification(messageObject, false);
            }
        }
    }
}

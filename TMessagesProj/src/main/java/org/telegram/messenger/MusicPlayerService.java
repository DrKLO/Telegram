/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.view.View;
import android.widget.RemoteViews;

import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.ui.LaunchActivity;

public class MusicPlayerService extends Service implements NotificationCenter.NotificationCenterDelegate {

    public static final String NOTIFY_PREVIOUS = "org.telegram.android.musicplayer.previous";
    public static final String NOTIFY_CLOSE = "org.telegram.android.musicplayer.close";
    public static final String NOTIFY_PAUSE = "org.telegram.android.musicplayer.pause";
    public static final String NOTIFY_PLAY = "org.telegram.android.musicplayer.play";
    public static final String NOTIFY_NEXT = "org.telegram.android.musicplayer.next";

    private static final int ID_NOTIFICATION = 5;

    private RemoteControlClient remoteControlClient;
    private AudioManager audioManager;

    private static boolean supportBigNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    private static boolean supportLockScreenControls = Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP;

    private MediaSession mediaSession;
    private PlaybackState.Builder playbackState;
    private Bitmap albumArtPlaceholder;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaSession = new MediaSession(this, "telegramAudioPlayer");
            playbackState = new PlaybackState.Builder();
            albumArtPlaceholder = Bitmap.createBitmap(AndroidUtilities.dp(102), AndroidUtilities.dp(102), Bitmap.Config.ARGB_8888);
            Drawable placeholder = getResources().getDrawable(R.drawable.nocover_big);
            placeholder.setBounds(0, 0, albumArtPlaceholder.getWidth(), albumArtPlaceholder.getHeight());
            placeholder.draw(new Canvas(albumArtPlaceholder));
            mediaSession.setCallback(new MediaSession.Callback() {
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
                    MediaController.getInstance().playNextMessage();
                }

                @Override
                public void onSkipToPrevious() {
                    MediaController.getInstance().playPreviousMessage();
                }

                @Override
                public void onStop() {
                    //stopSelf();
                }
            });
            mediaSession.setActive(true);
        }

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
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        stopSelf();
                    }
                });
                return START_STICKY;
            }
            if (supportLockScreenControls) {
                ComponentName remoteComponentName = new ComponentName(getApplicationContext(), MusicPlayerReceiver.class.getName());
                try {
                    if (remoteControlClient == null) {
                        audioManager.registerMediaButtonEventReceiver(remoteComponentName);
                        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                        mediaButtonIntent.setComponent(remoteComponentName);
                        PendingIntent mediaPendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, 0);
                        remoteControlClient = new RemoteControlClient(mediaPendingIntent);
                        audioManager.registerRemoteControlClient(remoteControlClient);
                    }
                    remoteControlClient.setTransportControlFlags(RemoteControlClient.FLAG_KEY_MEDIA_PLAY | RemoteControlClient.FLAG_KEY_MEDIA_PAUSE | RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE |
                            RemoteControlClient.FLAG_KEY_MEDIA_STOP | RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS | RemoteControlClient.FLAG_KEY_MEDIA_NEXT);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            createNotification(messageObject);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return START_STICKY;
    }

    @SuppressLint("NewApi")
    private void createNotification(MessageObject messageObject) {
        String songName = messageObject.getMusicTitle();
        String authorName = messageObject.getMusicAuthor();
        AudioInfo audioInfo = MediaController.getInstance().getAudioInfo();

        Intent intent = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
        intent.setAction("com.tmessages.openplayer");
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(ApplicationLoader.applicationContext, 0, intent, 0);

        Notification notification;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            Bitmap albumArt = audioInfo != null ? audioInfo.getSmallCover() : null;
            Bitmap fullAlbumArt = audioInfo != null ? audioInfo.getCover() : null;
            boolean isPlaying = !MediaController.getInstance().isMessagePaused();

            PendingIntent pendingPrev = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(NOTIFY_PREVIOUS).setComponent(new ComponentName(this, MusicPlayerReceiver.class)), PendingIntent.FLAG_CANCEL_CURRENT);
            //PendingIntent pendingStop = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(NOTIFY_CLOSE).setComponent(new ComponentName(this, MusicPlayerReceiver.class)), PendingIntent.FLAG_CANCEL_CURRENT);
            PendingIntent pendingStop = PendingIntent.getService(getApplicationContext(), 0, new Intent(this, getClass()).setAction(getPackageName() + ".STOP_PLAYER"), PendingIntent.FLAG_CANCEL_CURRENT);
            PendingIntent pendingPlaypause = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(isPlaying ? NOTIFY_PAUSE : NOTIFY_PLAY).setComponent(new ComponentName(this, MusicPlayerReceiver.class)), PendingIntent.FLAG_CANCEL_CURRENT);
            PendingIntent pendingNext = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(NOTIFY_NEXT).setComponent(new ComponentName(this, MusicPlayerReceiver.class)), PendingIntent.FLAG_CANCEL_CURRENT);

            Notification.Builder bldr = new Notification.Builder(this);
            bldr.setSmallIcon(R.drawable.player)
                    .setOngoing(isPlaying)
                    .setContentTitle(songName)
                    .setContentText(authorName)
                    .setSubText(audioInfo != null ? audioInfo.getAlbum() : null)
                    .setContentIntent(contentIntent)
                    .setDeleteIntent(pendingStop)
                    .setShowWhen(false)
                    .setCategory(Notification.CATEGORY_TRANSPORT)
                    .setPriority(Notification.PRIORITY_MAX)
                    .setStyle(new Notification.MediaStyle()
                            .setMediaSession(mediaSession.getSessionToken())
                            .setShowActionsInCompactView(0, 1, 2));
            if (albumArt != null) {
                bldr.setLargeIcon(albumArt);
            } else {
                bldr.setLargeIcon(albumArtPlaceholder);
            }

            if (MediaController.getInstance().isDownloadingCurrentMessage()) {
                playbackState.setState(PlaybackState.STATE_BUFFERING, 0, 1).setActions(0);
                bldr.addAction(new Notification.Action.Builder(R.drawable.ic_action_previous, "", pendingPrev).build())
                        .addAction(new Notification.Action.Builder(R.drawable.loading_animation2, "", null).build())
                        .addAction(new Notification.Action.Builder(R.drawable.ic_action_next, "", pendingNext).build());
            } else {
                playbackState.setState(isPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        MediaController.getInstance().getPlayingMessageObject().audioProgressSec * 1000L,
                        isPlaying ? 1 : 0)
                        .setActions(PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SKIP_TO_NEXT);
                bldr.addAction(new Notification.Action.Builder(R.drawable.ic_action_previous, "", pendingPrev).build())
                        .addAction(new Notification.Action.Builder(isPlaying ? R.drawable.ic_action_pause : R.drawable.ic_action_play, "", pendingPlaypause).build())
                        .addAction(new Notification.Action.Builder(R.drawable.ic_action_next, "", pendingNext).build());
            }

            mediaSession.setPlaybackState(playbackState.build());
            MediaMetadata.Builder meta = new MediaMetadata.Builder()
                    .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, fullAlbumArt)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, authorName)
                    .putString(MediaMetadata.METADATA_KEY_TITLE, songName)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, audioInfo != null ? audioInfo.getAlbum() : null);

            mediaSession.setMetadata(meta.build());

            notification = bldr.build();

            if (isPlaying) {
                startForeground(ID_NOTIFICATION, notification);
            } else {
                stopForeground(false);
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                nm.notify(ID_NOTIFICATION, notification);
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
                    .setContentTitle(songName).build();

            notification.contentView = simpleContentView;
            if (supportBigNotifications) {
                notification.bigContentView = expandedView;
            }

            setListeners(simpleContentView);
            if (supportBigNotifications) {
                setListeners(expandedView);
            }

            Bitmap albumArt = audioInfo != null ? audioInfo.getSmallCover() : null;
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
                notification.contentView.setViewVisibility(R.id.player_next, View.VISIBLE);
                notification.contentView.setViewVisibility(R.id.player_previous, View.VISIBLE);
                if (supportBigNotifications) {
                    notification.bigContentView.setViewVisibility(R.id.player_next, View.VISIBLE);
                    notification.bigContentView.setViewVisibility(R.id.player_previous, View.VISIBLE);
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

            notification.contentView.setTextViewText(R.id.player_song_name, songName);
            notification.contentView.setTextViewText(R.id.player_author_name, authorName);
            if (supportBigNotifications) {
                notification.bigContentView.setTextViewText(R.id.player_song_name, songName);
                notification.bigContentView.setTextViewText(R.id.player_author_name, authorName);
                notification.bigContentView.setTextViewText(R.id.player_album_title, audioInfo != null && !TextUtils.isEmpty(audioInfo.getAlbum()) ? audioInfo.getAlbum() : "");
            }
            notification.flags |= Notification.FLAG_ONGOING_EVENT;
            startForeground(ID_NOTIFICATION, notification);

        }

        if (remoteControlClient != null) {
            RemoteControlClient.MetadataEditor metadataEditor = remoteControlClient.editMetadata(true);
            metadataEditor.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, authorName);
            metadataEditor.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, songName);
            if (audioInfo != null && audioInfo.getCover() != null) {
                try {
                    metadataEditor.putBitmap(RemoteControlClient.MetadataEditor.BITMAP_KEY_ARTWORK, audioInfo.getCover());
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
            metadataEditor.apply();
        }
    }

    public void setListeners(RemoteViews view) {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(NOTIFY_PREVIOUS), PendingIntent.FLAG_UPDATE_CURRENT);
        view.setOnClickPendingIntent(R.id.player_previous, pendingIntent);
        pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(NOTIFY_CLOSE), PendingIntent.FLAG_UPDATE_CURRENT);
        view.setOnClickPendingIntent(R.id.player_close, pendingIntent);
        pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(NOTIFY_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT);
        view.setOnClickPendingIntent(R.id.player_pause, pendingIntent);
        pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(NOTIFY_NEXT), PendingIntent.FLAG_UPDATE_CURRENT);
        view.setOnClickPendingIntent(R.id.player_next, pendingIntent);
        pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(NOTIFY_PLAY), PendingIntent.FLAG_UPDATE_CURRENT);
        view.setOnClickPendingIntent(R.id.player_play, pendingIntent);
    }

    @SuppressLint("NewApi")
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (remoteControlClient != null) {
            RemoteControlClient.MetadataEditor metadataEditor = remoteControlClient.editMetadata(true);
            metadataEditor.clear();
            metadataEditor.apply();
            audioManager.unregisterRemoteControlClient(remoteControlClient);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaSession.release();
        }
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.messagePlayingPlayStateChanged) {
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject != null) {
                createNotification(messageObject);
            } else {
                stopSelf();
            }
        }
    }
}

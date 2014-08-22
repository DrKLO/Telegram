/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.android;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.view.View;

import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ApplicationLoader;
import org.telegram.ui.Cells.ChatMediaCell;
import org.telegram.ui.Views.GifDrawable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

public class MediaController implements NotificationCenter.NotificationCenterDelegate {

    private native int startRecord(String path);
    private native int writeFrame(ByteBuffer frame, int len);
    private native void stopRecord();
    private native int openOpusFile(String path);
    private native int seekOpusFile(float position);
    private native int isOpusFile(String path);
    private native void closeOpusFile();
    private native void readOpusFile(ByteBuffer buffer, int capacity, int[] args);
    private native long getTotalPcmDuration();

    public static int[] readArgs = new int[3];

    public static interface FileDownloadProgressListener {
        public void onFailedDownload(String fileName);
        public void onSuccessDownload(String fileName);
        public void onProgressDownload(String fileName, float progress);
        public void onProgressUpload(String fileName, float progress, boolean isEncrypted);
        public int getObserverTag();
    }

    private class AudioBuffer {
        public AudioBuffer(int capacity) {
            buffer = ByteBuffer.allocateDirect(capacity);
            bufferBytes = new byte[capacity];
        }

        ByteBuffer buffer;
        byte[] bufferBytes;
        int size;
        int finished;
        long pcmOffset;
    }

    private static final String[] projectionPhotos = {
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.ORIENTATION
    };

    public static class AlbumEntry {
        public int bucketId;
        public String bucketName;
        public PhotoEntry coverPhoto;
        public ArrayList<PhotoEntry> photos = new ArrayList<PhotoEntry>();

        public AlbumEntry(int bucketId, String bucketName, PhotoEntry coverPhoto) {
            this.bucketId = bucketId;
            this.bucketName = bucketName;
            this.coverPhoto = coverPhoto;
        }

        public void addPhoto(PhotoEntry photoEntry) {
            photos.add(photoEntry);
        }
    }

    public static class PhotoEntry {
        public int bucketId;
        public int imageId;
        public long dateTaken;
        public String path;
        public int orientation;

        public PhotoEntry(int bucketId, int imageId, long dateTaken, String path, int orientation) {
            this.bucketId = bucketId;
            this.imageId = imageId;
            this.dateTaken = dateTaken;
            this.path = path;
            this.orientation = orientation;
        }
    }

    public static final int AUTODOWNLOAD_MASK_PHOTO = 1;
    public static final int AUTODOWNLOAD_MASK_AUDIO = 2;
    public static final int AUTODOWNLOAD_MASK_VIDEO = 4;
    public static final int AUTODOWNLOAD_MASK_DOCUMENT = 8;
    public int mobileDataDownloadMask = 0;
    public int wifiDownloadMask = 0;
    public int roamingDownloadMask = 0;
    private int lastCheckMask = 0;
    private ArrayList<DownloadObject> photoDownloadQueue = new ArrayList<DownloadObject>();
    private ArrayList<DownloadObject> audioDownloadQueue = new ArrayList<DownloadObject>();
    private ArrayList<DownloadObject> documentDownloadQueue = new ArrayList<DownloadObject>();
    private ArrayList<DownloadObject> videoDownloadQueue = new ArrayList<DownloadObject>();
    private HashMap<String, DownloadObject> downloadQueueKeys = new HashMap<String, DownloadObject>();

    private HashMap<String, ArrayList<WeakReference<FileDownloadProgressListener>>> loadingFileObservers = new HashMap<String, ArrayList<WeakReference<FileDownloadProgressListener>>>();
    private HashMap<Integer, String> observersByTag = new HashMap<Integer, String>();
    private boolean listenerInProgress = false;
    private HashMap<String, FileDownloadProgressListener> addLaterArray = new HashMap<String, FileDownloadProgressListener>();
    private ArrayList<FileDownloadProgressListener> deleteLaterArray = new ArrayList<FileDownloadProgressListener>();
    private int lastTag = 0;

    private GifDrawable currentGifDrawable;
    private MessageObject currentGifMessageObject;
    private ChatMediaCell currentMediaCell;

    private boolean isPaused = false;
    private MediaPlayer audioPlayer = null;
    private AudioTrack audioTrackPlayer = null;
    private int lastProgress = 0;
    private MessageObject playingMessageObject;
    private int playerBufferSize = 0;
    private boolean decodingFinished = false;
    private long currentTotalPcmDuration;
    private long lastPlayPcm;
    private int ignoreFirstProgress = 0;
    private Timer progressTimer = null;
    private final Integer progressTimerSync = 1;

    private AudioRecord audioRecorder = null;
    private TLRPC.TL_audio recordingAudio = null;
    private File recordingAudioFile = null;
    private long recordStartTime;
    private long recordTimeCount;
    private long recordDialogId;
    private DispatchQueue fileDecodingQueue;
    private DispatchQueue playerQueue;
    private ArrayList<AudioBuffer> usedPlayerBuffers = new ArrayList<AudioBuffer>();
    private ArrayList<AudioBuffer> freePlayerBuffers = new ArrayList<AudioBuffer>();
    private final Integer playerSync = 2;
    private final Integer playerObjectSync = 3;

    private final Integer sync = 1;

    private ArrayList<ByteBuffer> recordBuffers = new ArrayList<ByteBuffer>();
    private ByteBuffer fileBuffer;
    private int recordBufferSize;
    private boolean sendAfterDone;

    private DispatchQueue recordQueue;
    private DispatchQueue fileEncodingQueue;
    private Runnable recordRunnable = new Runnable() {
        @Override
        public void run() {
            if (audioRecorder != null) {
                ByteBuffer buffer = null;
                if (!recordBuffers.isEmpty()) {
                    buffer = recordBuffers.get(0);
                    recordBuffers.remove(0);
                } else {
                    buffer = ByteBuffer.allocateDirect(recordBufferSize);
                }
                buffer.rewind();
                int len = audioRecorder.read(buffer, buffer.capacity());
                if (len > 0) {
                    buffer.limit(len);
                    final ByteBuffer finalBuffer = buffer;
                    final boolean flush = len != buffer.capacity();
                    if (len != 0) {
                        fileEncodingQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                while (finalBuffer.hasRemaining()) {
                                    int oldLimit = -1;
                                    if (finalBuffer.remaining() > fileBuffer.remaining()) {
                                        oldLimit = finalBuffer.limit();
                                        finalBuffer.limit(fileBuffer.remaining() + finalBuffer.position());
                                    }
                                    fileBuffer.put(finalBuffer);
                                    if (fileBuffer.position() == fileBuffer.limit() || flush) {
                                        if (writeFrame(fileBuffer, !flush ? fileBuffer.limit() : finalBuffer.position()) != 0) {
                                            fileBuffer.rewind();
                                            recordTimeCount += fileBuffer.limit() / 2 / 16;
                                        }
                                    }
                                    if (oldLimit != -1) {
                                        finalBuffer.limit(oldLimit);
                                    }
                                }
                                recordQueue.postRunnable(new Runnable() {
                                    @Override
                                    public void run() {
                                        recordBuffers.add(finalBuffer);
                                    }
                                });
                            }
                        });
                    }
                    recordQueue.postRunnable(recordRunnable);
                    AndroidUtilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.recordProgressChanged, System.currentTimeMillis() - recordStartTime);
                        }
                    });
                } else {
                    recordBuffers.add(buffer);
                    stopRecordingInternal(sendAfterDone);
                }
            }
        }
    };

    private class InternalObserver extends ContentObserver {
        public InternalObserver() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            processMediaObserver(MediaStore.Images.Media.INTERNAL_CONTENT_URI);
        }
    }

    private class ExternalObserver extends ContentObserver {
        public ExternalObserver() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            processMediaObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        }
    }

    private ExternalObserver externalObserver = null;
    private InternalObserver internalObserver = null;
    private long lastSecretChatEnterTime = 0;
    private long lastSecretChatLeaveTime = 0;
    private long lastMediaCheckTime = 0;
    private TLRPC.EncryptedChat lastSecretChat = null;
    private ArrayList<Long> lastSecretChatVisibleMessages = null;
    private int startObserverToken = 0;
    private StopMediaObserverRunnable stopMediaObserverRunnable = null;
    private final class StopMediaObserverRunnable implements Runnable {
        public int currentObserverToken = 0;

        @Override
        public void run() {
            if (currentObserverToken == startObserverToken) {
                try {
                    if (internalObserver != null) {
                        ApplicationLoader.applicationContext.getContentResolver().unregisterContentObserver(internalObserver);
                        internalObserver = null;
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                try {
                    if (externalObserver != null) {
                        ApplicationLoader.applicationContext.getContentResolver().unregisterContentObserver(externalObserver);
                        externalObserver = null;
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        }
    }
    private String[] mediaProjections = new String[] {
            MediaStore.Images.ImageColumns.DATA,
            MediaStore.Images.ImageColumns.DISPLAY_NAME,
            MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Images.ImageColumns.DATE_TAKEN,
            MediaStore.Images.ImageColumns.TITLE
    };

    private static volatile MediaController Instance = null;
    public static MediaController getInstance() {
        MediaController localInstance = Instance;
        if (localInstance == null) {
            synchronized (MediaController.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new MediaController();
                }
            }
        }
        return localInstance;
    }

    public MediaController() {
        try {
            recordBufferSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (recordBufferSize <= 0) {
                recordBufferSize = 1280;
            }
            playerBufferSize = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (playerBufferSize <= 0) {
                playerBufferSize = 3840;
            }
            for (int a = 0; a < 5; a++) {
                ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
                recordBuffers.add(buffer);
            }
            for (int a = 0; a < 3; a++) {
                freePlayerBuffers.add(new AudioBuffer(playerBufferSize));
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        fileBuffer = ByteBuffer.allocateDirect(1920);
        recordQueue = new DispatchQueue("recordQueue");
        recordQueue.setPriority(Thread.MAX_PRIORITY);
        fileEncodingQueue = new DispatchQueue("fileEncodingQueue");
        fileEncodingQueue.setPriority(Thread.MAX_PRIORITY);
        playerQueue = new DispatchQueue("playerQueue");
        fileDecodingQueue = new DispatchQueue("fileDecodingQueue");

        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        mobileDataDownloadMask = preferences.getInt("mobileDataDownloadMask", AUTODOWNLOAD_MASK_PHOTO | AUTODOWNLOAD_MASK_AUDIO);
        wifiDownloadMask = preferences.getInt("wifiDownloadMask", AUTODOWNLOAD_MASK_PHOTO | AUTODOWNLOAD_MASK_AUDIO);
        roamingDownloadMask = preferences.getInt("roamingDownloadMask", 0);

        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileDidFailedLoad);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileLoadProgressChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileUploadProgressChanged);

        BroadcastReceiver networkStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                checkAutodownloadSettings();
            }
        };
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        ApplicationLoader.applicationContext.registerReceiver(networkStateReceiver, filter);

        if (UserConfig.isClientActivated()) {
            checkAutodownloadSettings();
        }
    }

    private void startProgressTimer() {
        synchronized (progressTimerSync) {
            if (progressTimer != null) {
                try {
                    progressTimer.cancel();
                    progressTimer = null;
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
            progressTimer = new Timer();
            progressTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    synchronized (sync) {
                        AndroidUtilities.RunOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (playingMessageObject != null && (audioPlayer != null || audioTrackPlayer != null) && !isPaused) {
                                    try {
                                        if (ignoreFirstProgress != 0) {
                                            ignoreFirstProgress--;
                                            return;
                                        }
                                        int progress = 0;
                                        float value = 0;
                                        if (audioPlayer != null) {
                                            progress = audioPlayer.getCurrentPosition();
                                            value = (float) lastProgress / (float) audioPlayer.getDuration();
                                            if (progress <= lastProgress) {
                                                return;
                                            }
                                        } else if (audioTrackPlayer != null) {
                                            progress = (int) (lastPlayPcm / 48.0f);
                                            value = (float) lastPlayPcm / (float) currentTotalPcmDuration;
                                            if (progress == lastProgress) {
                                                return;
                                            }
                                        }
                                        lastProgress = progress;
                                        playingMessageObject.audioProgress = value;
                                        playingMessageObject.audioProgressSec = lastProgress / 1000;
                                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.audioProgressDidChanged, playingMessageObject.messageOwner.id, value);
                                    } catch (Exception e) {
                                        FileLog.e("tmessages", e);
                                    }
                                }
                            }
                        });
                    }
                }
            }, 0, 17);
        }
    }

    private void stopProgressTimer() {
        synchronized (progressTimerSync) {
            if (progressTimer != null) {
                try {
                    progressTimer.cancel();
                    progressTimer = null;
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        }
    }

    public void cleanup() {
        clenupPlayer(false);
        if (currentGifDrawable != null) {
            currentGifDrawable.recycle();
            currentGifDrawable = null;
        }
        currentMediaCell = null;
        currentGifMessageObject = null;
        photoDownloadQueue.clear();
        audioDownloadQueue.clear();
        documentDownloadQueue.clear();
        videoDownloadQueue.clear();
        downloadQueueKeys.clear();
    }

    protected int getAutodownloadMask() {
        int mask = 0;
        if ((mobileDataDownloadMask & AUTODOWNLOAD_MASK_PHOTO) != 0 || (wifiDownloadMask & AUTODOWNLOAD_MASK_PHOTO) != 0 || (roamingDownloadMask & AUTODOWNLOAD_MASK_PHOTO) != 0) {
            mask |= AUTODOWNLOAD_MASK_PHOTO;
        }
        if ((mobileDataDownloadMask & AUTODOWNLOAD_MASK_AUDIO) != 0 || (wifiDownloadMask & AUTODOWNLOAD_MASK_AUDIO) != 0 || (roamingDownloadMask & AUTODOWNLOAD_MASK_AUDIO) != 0) {
            mask |= AUTODOWNLOAD_MASK_AUDIO;
        }
        if ((mobileDataDownloadMask & AUTODOWNLOAD_MASK_VIDEO) != 0 || (wifiDownloadMask & AUTODOWNLOAD_MASK_VIDEO) != 0 || (roamingDownloadMask & AUTODOWNLOAD_MASK_VIDEO) != 0) {
            mask |= AUTODOWNLOAD_MASK_VIDEO;
        }
        if ((mobileDataDownloadMask & AUTODOWNLOAD_MASK_DOCUMENT) != 0 || (wifiDownloadMask & AUTODOWNLOAD_MASK_DOCUMENT) != 0 || (roamingDownloadMask & AUTODOWNLOAD_MASK_DOCUMENT) != 0) {
            mask |= AUTODOWNLOAD_MASK_DOCUMENT;
        }
        return mask;
    }

    public void checkAutodownloadSettings() {
        int currentMask = getCurrentDownloadMask();
        if (currentMask == lastCheckMask) {
            return;
        }
        FileLog.e("tmessages", "check download mask = " + currentMask);
        lastCheckMask = currentMask;
        if ((currentMask & AUTODOWNLOAD_MASK_PHOTO) != 0) {
            if (photoDownloadQueue.isEmpty()) {
                newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_PHOTO);
            }
        } else {
            for (DownloadObject downloadObject : photoDownloadQueue) {
                FileLoader.getInstance().cancelLoadFile((TLRPC.PhotoSize)downloadObject.object);
            }
            photoDownloadQueue.clear();
        }
        if ((currentMask & AUTODOWNLOAD_MASK_AUDIO) != 0) {
            if (audioDownloadQueue.isEmpty()) {
                newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_AUDIO);
            }
        } else {
            for (DownloadObject downloadObject : audioDownloadQueue) {
                FileLoader.getInstance().cancelLoadFile((TLRPC.Audio)downloadObject.object);
            }
            audioDownloadQueue.clear();
        }
        if ((currentMask & AUTODOWNLOAD_MASK_DOCUMENT) != 0) {
            if (documentDownloadQueue.isEmpty()) {
                newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_DOCUMENT);
            }
        } else {
            for (DownloadObject downloadObject : documentDownloadQueue) {
                FileLoader.getInstance().cancelLoadFile((TLRPC.Document)downloadObject.object);
            }
            documentDownloadQueue.clear();
        }
        if ((currentMask & AUTODOWNLOAD_MASK_VIDEO) != 0) {
            if (videoDownloadQueue.isEmpty()) {
                newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_VIDEO);
            }
        } else {
            for (DownloadObject downloadObject : videoDownloadQueue) {
                FileLoader.getInstance().cancelLoadFile((TLRPC.Video)downloadObject.object);
            }
            videoDownloadQueue.clear();
        }

        int mask = getAutodownloadMask();
        if (mask == 0) {
            MessagesStorage.getInstance().clearDownloadQueue(0);
        } else {
            if ((mask & AUTODOWNLOAD_MASK_PHOTO) == 0) {
                MessagesStorage.getInstance().clearDownloadQueue(AUTODOWNLOAD_MASK_PHOTO);
            }
            if ((mask & AUTODOWNLOAD_MASK_AUDIO) == 0) {
                MessagesStorage.getInstance().clearDownloadQueue(AUTODOWNLOAD_MASK_AUDIO);
            }
            if ((mask & AUTODOWNLOAD_MASK_VIDEO) == 0) {
                MessagesStorage.getInstance().clearDownloadQueue(AUTODOWNLOAD_MASK_VIDEO);
            }
            if ((mask & AUTODOWNLOAD_MASK_DOCUMENT) == 0) {
                MessagesStorage.getInstance().clearDownloadQueue(AUTODOWNLOAD_MASK_DOCUMENT);
            }
        }
    }

    public boolean canDownloadMedia(int type) {
        return (getCurrentDownloadMask() & type) != 0;
    }

    private int getCurrentDownloadMask() {
        if (ConnectionsManager.isConnectedToWiFi()) {
            return wifiDownloadMask;
        } else if(ConnectionsManager.isRoaming()) {
            return roamingDownloadMask;
        } else {
            return mobileDataDownloadMask;
        }
    }

    protected void processDownloadObjects(int type, ArrayList<DownloadObject> objects) {
        if (objects.isEmpty()) {
            return;
        }
        ArrayList<DownloadObject> queue = null;
        if (type == AUTODOWNLOAD_MASK_PHOTO) {
            queue = photoDownloadQueue;
        } else if (type == AUTODOWNLOAD_MASK_AUDIO) {
            queue = audioDownloadQueue;
        } else if (type == AUTODOWNLOAD_MASK_VIDEO) {
            queue = videoDownloadQueue;
        } else if (type == AUTODOWNLOAD_MASK_DOCUMENT) {
            queue = documentDownloadQueue;
        }
        queue.addAll(objects);
        for (DownloadObject downloadObject : queue) {
            String path = FileLoader.getAttachFileName(downloadObject.object);
            downloadQueueKeys.put(path, downloadObject);
            if (downloadObject.object instanceof TLRPC.Audio) {
                FileLoader.getInstance().loadFile((TLRPC.Audio)downloadObject.object, false);
            } else if (downloadObject.object instanceof TLRPC.PhotoSize) {
                FileLoader.getInstance().loadFile((TLRPC.PhotoSize)downloadObject.object);
            } else if (downloadObject.object instanceof TLRPC.Video) {
                FileLoader.getInstance().loadFile((TLRPC.Video)downloadObject.object);
            } else if (downloadObject.object instanceof TLRPC.Document) {
                FileLoader.getInstance().loadFile((TLRPC.Document)downloadObject.object);
            }
        }
    }

    protected void newDownloadObjectsAvailable(int downloadMask) {
        int mask = getCurrentDownloadMask();
        if ((mask & AUTODOWNLOAD_MASK_PHOTO) != 0 && (downloadMask & AUTODOWNLOAD_MASK_PHOTO) != 0 && photoDownloadQueue.isEmpty()) {
            MessagesStorage.getInstance().getDownloadQueue(AUTODOWNLOAD_MASK_PHOTO);
        }
        if ((mask & AUTODOWNLOAD_MASK_AUDIO) != 0 && (downloadMask & AUTODOWNLOAD_MASK_AUDIO) != 0 && audioDownloadQueue.isEmpty()) {
            MessagesStorage.getInstance().getDownloadQueue(AUTODOWNLOAD_MASK_AUDIO);
        }
        if ((mask & AUTODOWNLOAD_MASK_VIDEO) != 0 && (downloadMask & AUTODOWNLOAD_MASK_VIDEO) != 0 && videoDownloadQueue.isEmpty()) {
            MessagesStorage.getInstance().getDownloadQueue(AUTODOWNLOAD_MASK_VIDEO);
        }
        if ((mask & AUTODOWNLOAD_MASK_DOCUMENT) != 0 && (downloadMask & AUTODOWNLOAD_MASK_DOCUMENT) != 0 && documentDownloadQueue.isEmpty()) {
            MessagesStorage.getInstance().getDownloadQueue(AUTODOWNLOAD_MASK_DOCUMENT);
        }
    }

    private void checkDownloadFinished(String fileName, boolean canceled) {
        DownloadObject downloadObject = downloadQueueKeys.get(fileName);
        if (downloadObject != null) {
            downloadQueueKeys.remove(fileName);
            if (!canceled) {
                MessagesStorage.getInstance().removeFromDownloadQueue(downloadObject.id, downloadObject.type);
            }
            if (downloadObject.type == AUTODOWNLOAD_MASK_PHOTO) {
                photoDownloadQueue.remove(downloadObject);
                if (photoDownloadQueue.isEmpty()) {
                    newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_PHOTO);
                }
            } else if (downloadObject.type == AUTODOWNLOAD_MASK_AUDIO) {
                audioDownloadQueue.remove(downloadObject);
                if (audioDownloadQueue.isEmpty()) {
                    newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_AUDIO);
                }
            } else if (downloadObject.type == AUTODOWNLOAD_MASK_VIDEO) {
                videoDownloadQueue.remove(downloadObject);
                if (videoDownloadQueue.isEmpty()) {
                    newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_VIDEO);
                }
            } else if (downloadObject.type == AUTODOWNLOAD_MASK_DOCUMENT) {
                documentDownloadQueue.remove(downloadObject);
                if (documentDownloadQueue.isEmpty()) {
                    newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_DOCUMENT);
                }
            }
        }
    }

    public void startMediaObserver() {
        if (android.os.Build.VERSION.SDK_INT > 0) { //disable while it's not perferct
            return;
        }
        ApplicationLoader.applicationHandler.removeCallbacks(stopMediaObserverRunnable);
        startObserverToken++;
        try {
            if (internalObserver == null) {
                ApplicationLoader.applicationContext.getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, externalObserver = new ExternalObserver());
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        try {
            if (externalObserver == null) {
                ApplicationLoader.applicationContext.getContentResolver().registerContentObserver(MediaStore.Images.Media.INTERNAL_CONTENT_URI, false, internalObserver = new InternalObserver());
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    public void stopMediaObserver() {
        if (android.os.Build.VERSION.SDK_INT > 0) { //disable while it's not perferct
            return;
        }
        if (stopMediaObserverRunnable == null) {
            stopMediaObserverRunnable = new StopMediaObserverRunnable();
        }
        stopMediaObserverRunnable.currentObserverToken = startObserverToken;
        ApplicationLoader.applicationHandler.postDelayed(stopMediaObserverRunnable, 5000);
    }

    public void processMediaObserver(Uri uri) {
        try {
            Cursor cursor = ApplicationLoader.applicationContext.getContentResolver().query(uri, mediaProjections, null, null, "date_added DESC LIMIT 1");
            final ArrayList<Long> screenshotDates = new ArrayList<Long>();
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String val = "";
                    String data = cursor.getString(0);
                    String display_name = cursor.getString(1);
                    String album_name = cursor.getString(2);
                    String title = cursor.getString(4);
                    long date = cursor.getLong(3);
                    if (data != null && data.toLowerCase().contains("screenshot") ||
                            display_name != null && display_name.toLowerCase().contains("screenshot") ||
                            album_name != null && album_name.toLowerCase().contains("screenshot") ||
                            title != null && title.toLowerCase().contains("screenshot")) {
                        /*BitmapRegionDecoder bitmapRegionDecoder = null;
                        boolean added = false;
                        try {
                            int waitCount = 0;
                            while (waitCount < 5 && bitmapRegionDecoder == null) {
                                try {
                                    bitmapRegionDecoder = BitmapRegionDecoder.newInstance(data, true);
                                    if (bitmapRegionDecoder != null) {
                                        break;
                                    }
                                } catch (Exception e) {
                                    FileLog.e("tmessages", e);
                                }
                                Thread.sleep(1000);
                            }
                            if (bitmapRegionDecoder != null) {
                                Bitmap bitmap = bitmapRegionDecoder.decodeRegion(new Rect(0, 0, AndroidUtilities.dp(44), AndroidUtilities.dp(44)), null);
                                int w = bitmap.getWidth();
                                int h = bitmap.getHeight();
                                for (int y = 0; y < h; y++) {
                                    int rowCount = 0;
                                    for (int x = 0; x < w; x++) {
                                        int px = bitmap.getPixel(x, y);
                                        if (px == 0xffffffff) {
                                            rowCount++;
                                        } else {
                                            rowCount = 0;
                                        }
                                        if (rowCount > 8) {
                                            break;
                                        }
                                    }
                                    if (rowCount > 8) {
                                        screenshotDates.add(date);
                                        added = true;
                                        break;
                                    }
                                }
                                bitmapRegionDecoder.recycle();
                                try {
                                    if (bitmap != null) {
                                        bitmap.recycle();
                                    }
                                } catch (Exception e) {
                                    FileLog.e("tmessages", e);
                                }
                            }
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                            try {
                                if (bitmapRegionDecoder != null) {
                                    bitmapRegionDecoder.recycle();
                                }
                            } catch (Exception e2) {
                                FileLog.e("tmessages", e2);
                            }
                            if (!added) {
                                screenshotDates.add(date);
                            }
                        }*/
                        screenshotDates.add(date);
                    }
                    FileLog.e("tmessages", "screenshot!");
                }
                cursor.close();
            }
            if (!screenshotDates.isEmpty()) {
                AndroidUtilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.screenshotTook);
                        checkScreenshots(screenshotDates);
                    }
                });
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    private void checkScreenshots(ArrayList<Long> dates) {
        if (dates == null || dates.isEmpty() || lastSecretChatEnterTime == 0 || lastSecretChat == null || !(lastSecretChat instanceof TLRPC.TL_encryptedChat)) {
            return;
        }
        long dt = 2000;
        boolean send = false;
        for (Long date : dates) {
            if (lastMediaCheckTime != 0 && date <= lastMediaCheckTime) {
                continue;
            }

            if (date >= lastSecretChatEnterTime) {
                if (lastSecretChatLeaveTime == 0 || date <= lastSecretChatLeaveTime + dt) {
                    lastMediaCheckTime = Math.max(lastMediaCheckTime, date);
                    send = true;
                }
            }
        }
        if (send) {
            SendMessagesHelper.getInstance().sendScreenshotMessage(lastSecretChat, lastSecretChatVisibleMessages);
        }
    }

    public void setLastEncryptedChatParams(long enterTime, long leaveTime, TLRPC.EncryptedChat encryptedChat, ArrayList<Long> visibleMessages) {
        lastSecretChatEnterTime = enterTime;
        lastSecretChatLeaveTime = leaveTime;
        lastSecretChat = encryptedChat;
        lastSecretChatVisibleMessages = visibleMessages;
    }

    public int generateObserverTag() {
        return lastTag++;
    }

    public void addLoadingFileObserver(String fileName, FileDownloadProgressListener observer) {
        if (listenerInProgress) {
            addLaterArray.put(fileName, observer);
            return;
        }
        removeLoadingFileObserver(observer);

        ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
        if (arrayList == null) {
            arrayList = new ArrayList<WeakReference<FileDownloadProgressListener>>();
            loadingFileObservers.put(fileName, arrayList);
        }
        arrayList.add(new WeakReference<FileDownloadProgressListener>(observer));

        observersByTag.put(observer.getObserverTag(), fileName);
    }

    public void removeLoadingFileObserver(FileDownloadProgressListener observer) {
        if (listenerInProgress) {
            deleteLaterArray.add(observer);
            return;
        }
        String fileName = observersByTag.get(observer.getObserverTag());
        if (fileName != null) {
            ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
            if (arrayList != null) {
                for (int a = 0; a < arrayList.size(); a++) {
                    WeakReference<FileDownloadProgressListener> reference = arrayList.get(a);
                    if (reference.get() == null || reference.get() == observer) {
                        arrayList.remove(a);
                        a--;
                    }
                }
                if (arrayList.isEmpty()) {
                    loadingFileObservers.remove(fileName);
                }
            }
            observersByTag.remove(observer.getObserverTag());
        }
    }

    private void processLaterArrays() {
        for (HashMap.Entry<String, FileDownloadProgressListener> listener : addLaterArray.entrySet()) {
            addLoadingFileObserver(listener.getKey(), listener.getValue());
        }
        addLaterArray.clear();
        for (FileDownloadProgressListener listener : deleteLaterArray) {
            removeLoadingFileObserver(listener);
        }
        deleteLaterArray.clear();
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.FileDidFailedLoad) {
            listenerInProgress = true;
            String fileName = (String)args[0];
            ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
            if (arrayList != null) {
                for (WeakReference<FileDownloadProgressListener> reference : arrayList) {
                    if (reference.get() != null) {
                        reference.get().onFailedDownload(fileName);
                        observersByTag.remove(reference.get().getObserverTag());
                    }
                }
                loadingFileObservers.remove(fileName);
            }
            listenerInProgress = false;
            processLaterArrays();
            checkDownloadFinished(fileName, (Boolean)args[1]);
        } else if (id == NotificationCenter.FileDidLoaded) {
            listenerInProgress = true;
            String fileName = (String)args[0];
            ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
            if (arrayList != null) {
                for (WeakReference<FileDownloadProgressListener> reference : arrayList) {
                    if (reference.get() != null) {
                        reference.get().onSuccessDownload(fileName);
                        observersByTag.remove(reference.get().getObserverTag());
                    }
                }
                loadingFileObservers.remove(fileName);
            }
            listenerInProgress = false;
            processLaterArrays();
            checkDownloadFinished(fileName, false);
        } else if (id == NotificationCenter.FileLoadProgressChanged) {
            listenerInProgress = true;
            String fileName = (String)args[0];
            ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
            if (arrayList != null) {
                Float progress = (Float)args[1];
                for (WeakReference<FileDownloadProgressListener> reference : arrayList) {
                    if (reference.get() != null) {
                        reference.get().onProgressDownload(fileName, progress);
                    }
                }
            }
            listenerInProgress = false;
            processLaterArrays();
        } else if (id == NotificationCenter.FileUploadProgressChanged) {
            String location = (String)args[0];
            listenerInProgress = true;
            String fileName = (String)args[0];
            ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
            if (arrayList != null) {
                Float progress = (Float)args[1];
                Boolean enc = (Boolean)args[2];
                for (WeakReference<FileDownloadProgressListener> reference : arrayList) {
                    if (reference.get() != null) {
                        reference.get().onProgressUpload(fileName, progress, enc);
                    }
                }
            }
            listenerInProgress = false;
            processLaterArrays();
        }
    }

    private void checkDecoderQueue() {
        fileDecodingQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (decodingFinished) {
                    checkPlayerQueue();
                    return;
                }
                boolean was = false;
                while (true) {
                    AudioBuffer buffer = null;
                    synchronized (playerSync) {
                        if (!freePlayerBuffers.isEmpty()) {
                            buffer = freePlayerBuffers.get(0);
                            freePlayerBuffers.remove(0);
                        }
                        if (!usedPlayerBuffers.isEmpty()) {
                            was = true;
                        }
                    }
                    if (buffer != null) {
                        readOpusFile(buffer.buffer, playerBufferSize, readArgs);
                        buffer.size = readArgs[0];
                        buffer.pcmOffset = readArgs[1];
                        buffer.finished = readArgs[2];
                        if (buffer.finished == 1) {
                            decodingFinished = true;
                        }
                        if (buffer.size != 0) {
                            buffer.buffer.rewind();
                            buffer.buffer.get(buffer.bufferBytes);
                            synchronized (playerSync) {
                                usedPlayerBuffers.add(buffer);
                            }
                        } else {
                            synchronized (playerSync) {
                                freePlayerBuffers.add(buffer);
                                break;
                            }
                        }
                        was = true;
                    } else {
                        break;
                    }
                }
                if (was) {
                    checkPlayerQueue();
                }
            }
        });
    }

    private void checkPlayerQueue() {
        playerQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                synchronized (playerObjectSync) {
                    if (audioTrackPlayer == null || audioTrackPlayer.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                        return;
                    }
                }
                AudioBuffer buffer = null;
                synchronized (playerSync) {
                    if (!usedPlayerBuffers.isEmpty()) {
                        buffer = usedPlayerBuffers.get(0);
                        usedPlayerBuffers.remove(0);
                    }
                }

                if (buffer != null) {
                    int count = 0;
                    try {
                        count = audioTrackPlayer.write(buffer.bufferBytes, 0, buffer.size);
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }

                    if (count > 0) {
                        final long pcm = buffer.pcmOffset;
                        final int marker = buffer.finished == 1 ? buffer.size : -1;
                        AndroidUtilities.RunOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                lastPlayPcm = pcm;
                                if (marker != -1) {
                                    if (audioTrackPlayer != null) {
                                        audioTrackPlayer.setNotificationMarkerPosition(1);
                                    }
                                }
                            }
                        });
                    }

                    if (buffer.finished != 1) {
                        checkPlayerQueue();
                    }
                }
                if (buffer == null || buffer != null && buffer.finished != 1) {
                    checkDecoderQueue();
                }

                if (buffer != null) {
                    synchronized (playerSync) {
                        freePlayerBuffers.add(buffer);
                    }
                }
            }
        });
    }

    private void clenupPlayer(boolean notify) {
        if (audioPlayer != null || audioTrackPlayer != null) {
            if (audioPlayer != null) {
                try {
                    audioPlayer.stop();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                try {
                    audioPlayer.release();
                    audioPlayer = null;
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            } else if (audioTrackPlayer != null) {
                synchronized (playerObjectSync) {
                    try {
                        audioTrackPlayer.pause();
                        audioTrackPlayer.flush();
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    try {
                        audioTrackPlayer.release();
                        audioTrackPlayer = null;
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }
            }
            stopProgressTimer();
            lastProgress = 0;
            isPaused = false;
            MessageObject lastFile = playingMessageObject;
            playingMessageObject.audioProgress = 0.0f;
            playingMessageObject.audioProgressSec = 0;
            playingMessageObject = null;
            if (notify) {
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.audioDidReset, lastFile.messageOwner.id);
            }
        }
    }

    private void seekOpusPlayer(final float progress) {
        if (currentTotalPcmDuration * progress == currentTotalPcmDuration) {
            return;
        }
        if (!isPaused) {
            audioTrackPlayer.pause();
        }
        audioTrackPlayer.flush();
        fileDecodingQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                seekOpusFile(progress);
                synchronized (playerSync) {
                    freePlayerBuffers.addAll(usedPlayerBuffers);
                    usedPlayerBuffers.clear();
                }
                AndroidUtilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isPaused) {
                            ignoreFirstProgress = 3;
                            lastPlayPcm = (long) (currentTotalPcmDuration * progress);
                            if (audioTrackPlayer != null) {
                                audioTrackPlayer.play();
                            }
                            lastProgress = (int) (currentTotalPcmDuration / 48.0f * progress);
                            checkPlayerQueue();
                        }
                    }
                });
            }
        });
    }

    public boolean seekToProgress(MessageObject messageObject, float progress) {
        if (audioTrackPlayer == null && audioPlayer == null || messageObject == null || playingMessageObject == null || playingMessageObject != null && playingMessageObject.messageOwner.id != messageObject.messageOwner.id) {
            return false;
        }
        try {
            if (audioPlayer != null) {
                int seekTo = (int) (audioPlayer.getDuration() * progress);
                audioPlayer.seekTo(seekTo);
                lastProgress = seekTo;
            } else if (audioTrackPlayer != null) {
                seekOpusPlayer(progress);
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            return false;
        }
        return true;
    }

    public boolean playAudio(MessageObject messageObject) {
        if (messageObject == null) {
            return false;
        }
        if ((audioTrackPlayer != null || audioPlayer != null) && playingMessageObject != null && messageObject.messageOwner.id == playingMessageObject.messageOwner.id) {
            if (isPaused) {
                resumeAudio(messageObject);
            }
            return true;
        }
        clenupPlayer(true);
        final File cacheFile = new File(AndroidUtilities.getCacheDir(), messageObject.getFileName());

        if (isOpusFile(cacheFile.getAbsolutePath()) == 1) {
            synchronized (playerObjectSync) {
                try {
                    ignoreFirstProgress = 3;
                    final Semaphore semaphore = new Semaphore(0);
                    final Boolean[] result = new Boolean[1];
                    fileDecodingQueue.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            result[0] = openOpusFile(cacheFile.getAbsolutePath()) != 0;
                            semaphore.release();
                        }
                    });
                    semaphore.acquire();

                    if (!result[0]) {
                        return false;
                    }
                    currentTotalPcmDuration = getTotalPcmDuration();

                    audioTrackPlayer = new AudioTrack(AudioManager.STREAM_MUSIC, 48000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, playerBufferSize, AudioTrack.MODE_STREAM);
                    audioTrackPlayer.setStereoVolume(1.0f, 1.0f);
                    //audioTrackPlayer.setNotificationMarkerPosition((int)currentTotalPcmDuration);
                    audioTrackPlayer.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
                        @Override
                        public void onMarkerReached(AudioTrack audioTrack) {
                            clenupPlayer(true);
                        }

                        @Override
                        public void onPeriodicNotification(AudioTrack audioTrack) {

                        }
                    });
                    audioTrackPlayer.play();
                    startProgressTimer();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                    if (audioTrackPlayer != null) {
                        audioTrackPlayer.release();
                        audioTrackPlayer = null;
                        isPaused = false;
                        playingMessageObject = null;
                    }
                    return false;
                }
            }
        } else {
            try {
                audioPlayer = new MediaPlayer();
                audioPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                audioPlayer.setDataSource(cacheFile.getAbsolutePath());
                audioPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mediaPlayer) {
                        clenupPlayer(true);
                    }
                });
                audioPlayer.prepare();
                audioPlayer.start();
                startProgressTimer();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
                if (audioPlayer != null) {
                    audioPlayer.release();
                    audioPlayer = null;
                    isPaused = false;
                    playingMessageObject = null;
                }
                return false;
            }
        }

        isPaused = false;
        lastProgress = 0;
        lastPlayPcm = 0;
        playingMessageObject = messageObject;

        if (audioPlayer != null) {
            try {
                if (playingMessageObject.audioProgress != 0) {
                    int seekTo = (int) (audioPlayer.getDuration() * playingMessageObject.audioProgress);
                    audioPlayer.seekTo(seekTo);
                }
            } catch (Exception e2) {
                playingMessageObject.audioProgress = 0;
                playingMessageObject.audioProgressSec = 0;
                FileLog.e("tmessages", e2);
            }
        } else if (audioTrackPlayer != null) {
            if (playingMessageObject.audioProgress == 1) {
                playingMessageObject.audioProgress = 0;
            }
            fileDecodingQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    if (playingMessageObject != null && playingMessageObject.audioProgress != 0) {
                        lastPlayPcm = (long)(currentTotalPcmDuration * playingMessageObject.audioProgress);
                        seekOpusFile(playingMessageObject.audioProgress);
                    }
                    synchronized (playerSync) {
                        freePlayerBuffers.addAll(usedPlayerBuffers);
                        usedPlayerBuffers.clear();
                    }
                    decodingFinished = false;
                    checkPlayerQueue();
                }
            });
        }

        return true;
    }

    public void stopAudio() {
        if (audioTrackPlayer == null && audioPlayer == null || playingMessageObject == null) {
            return;
        }
        try {
            if (audioPlayer != null) {
                audioPlayer.stop();
            } else if (audioTrackPlayer != null) {
                audioTrackPlayer.pause();
                audioTrackPlayer.flush();
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        try {
            if (audioPlayer != null) {
                audioPlayer.release();
                audioPlayer = null;
            } else if (audioTrackPlayer != null) {
                synchronized (playerObjectSync) {
                    audioTrackPlayer.release();
                    audioTrackPlayer = null;
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        stopProgressTimer();
        playingMessageObject = null;
        isPaused = false;
    }

    public boolean pauseAudio(MessageObject messageObject) {
        if (audioTrackPlayer == null && audioPlayer == null || messageObject == null || playingMessageObject == null || playingMessageObject != null && playingMessageObject.messageOwner.id != messageObject.messageOwner.id) {
            return false;
        }
        try {
            if (audioPlayer != null) {
                audioPlayer.pause();
            } else if (audioTrackPlayer != null) {
                audioTrackPlayer.pause();
            }
            isPaused = true;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            isPaused = false;
            return false;
        }
        return true;
    }

    public boolean resumeAudio(MessageObject messageObject) {
        if (audioTrackPlayer == null && audioPlayer == null || messageObject == null || playingMessageObject == null || playingMessageObject != null && playingMessageObject.messageOwner.id != messageObject.messageOwner.id) {
            return false;
        }
        try {
            if (audioPlayer != null) {
                audioPlayer.start();
            } else if (audioTrackPlayer != null) {
                audioTrackPlayer.play();
                checkPlayerQueue();
            }
            isPaused = false;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            return false;
        }
        return true;
    }

    public boolean isPlayingAudio(MessageObject messageObject) {
        return !(audioTrackPlayer == null && audioPlayer == null || messageObject == null || playingMessageObject == null || playingMessageObject != null && playingMessageObject.messageOwner.id != messageObject.messageOwner.id);
    }

    public boolean isAudioPaused() {
        return isPaused;
    }

    public void startRecording(final long dialog_id) {
        try {
            Vibrator v = (Vibrator) ApplicationLoader.applicationContext.getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(20);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }

        recordQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (audioRecorder != null) {
                    AndroidUtilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.recordStartError);
                        }
                    });
                    return;
                }

                recordingAudio = new TLRPC.TL_audio();
                recordingAudio.dc_id = Integer.MIN_VALUE;
                recordingAudio.id = UserConfig.lastLocalId;
                recordingAudio.user_id = UserConfig.getClientUserId();
                recordingAudio.mime_type = "audio/ogg";
                UserConfig.lastLocalId--;
                UserConfig.saveConfig(false);

                recordingAudioFile = new File(AndroidUtilities.getCacheDir(), FileLoader.getAttachFileName(recordingAudio));

                try {
                    if (startRecord(recordingAudioFile.getAbsolutePath()) == 0) {
                        AndroidUtilities.RunOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.recordStartError);
                            }
                        });
                        return;
                    }
                    audioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, recordBufferSize * 10);
                    recordStartTime = System.currentTimeMillis();
                    recordTimeCount = 0;
                    recordDialogId = dialog_id;
                    fileBuffer.rewind();

                    audioRecorder.startRecording();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                    recordingAudio = null;
                    stopRecord();
                    recordingAudioFile.delete();
                    recordingAudioFile = null;
                    try {
                        audioRecorder.release();
                        audioRecorder = null;
                    } catch (Exception e2) {
                        FileLog.e("tmessages", e2);
                    }

                    AndroidUtilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.recordStartError);
                        }
                    });
                    return;
                }

                recordQueue.postRunnable(recordRunnable);
                AndroidUtilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.recordStarted);
                    }
                });
            }
        });
    }

    private void stopRecordingInternal(final boolean send) {
        if (send) {
            final TLRPC.TL_audio audioToSend = recordingAudio;
            final File recordingAudioFileToSend = recordingAudioFile;
            fileEncodingQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    stopRecord();
                    AndroidUtilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            audioToSend.date = ConnectionsManager.getInstance().getCurrentTime();
                            audioToSend.size = (int) recordingAudioFileToSend.length();
                            audioToSend.path = recordingAudioFileToSend.getAbsolutePath();
                            long duration = recordTimeCount;
                            audioToSend.duration = (int) (duration / 1000);
                            if (duration > 700) {
                                SendMessagesHelper.getInstance().sendMessage(audioToSend, recordDialogId);
                            } else {
                                recordingAudioFileToSend.delete();
                            }
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.audioDidSent);
                        }
                    });
                }
            });
        }
        try {
            if (audioRecorder != null) {
                audioRecorder.release();
                audioRecorder = null;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        recordingAudio = null;
        recordingAudioFile = null;
    }

    public void stopRecording(final boolean send) {
        recordQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (audioRecorder == null) {
                    return;
                }
                try {
                    sendAfterDone = send;
                    audioRecorder.stop();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                    if (recordingAudioFile != null) {
                        recordingAudioFile.delete();
                    }
                }
                if (!send) {
                    stopRecordingInternal(false);
                }
                try {
                    Vibrator v = (Vibrator) ApplicationLoader.applicationContext.getSystemService(Context.VIBRATOR_SERVICE);
                    v.vibrate(20);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                AndroidUtilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.recordStopped);
                    }
                });
            }
        });
    }

    public static void saveFile(String path, String fullPath, Context context, final int type, final String name) {
        if (path == null && fullPath == null) {
            return;
        }

        File file = null;
        if (fullPath != null && fullPath.length() != 0) {
            file = new File(fullPath);
            if (!file.exists()) {
                file = null;
            }
        }
        if (file == null) {
            file = new File(AndroidUtilities.getCacheDir(), path);
        }

        final File sourceFile = file;
        if (sourceFile.exists()) {
            ProgressDialog progressDialog = null;
            if (context != null) {
                progressDialog = new ProgressDialog(context);
                progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
                progressDialog.setCanceledOnTouchOutside(false);
                progressDialog.setCancelable(false);
                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progressDialog.setMax(100);
                progressDialog.show();
            }

            final ProgressDialog finalProgress = progressDialog;

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        File destFile = null;
                        if (type == 0) {
                            destFile = Utilities.generatePicturePath();
                        } else if (type == 1) {
                            destFile = Utilities.generateVideoPath();
                        } else if (type == 2) {
                            File f = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                            destFile = new File(f, name);
                        }

                        if(!destFile.exists()) {
                            destFile.createNewFile();
                        }
                        FileChannel source = null;
                        FileChannel destination = null;
                        boolean result = true;
                        long lastProgress = System.currentTimeMillis() - 500;
                        try {
                            source = new FileInputStream(sourceFile).getChannel();
                            destination = new FileOutputStream(destFile).getChannel();
                            long size = source.size();
                            for (long a = 0; a < size; a += 1024) {
                                destination.transferFrom(source, a, Math.min(1024, size - a));
                                if (finalProgress != null) {
                                    if (lastProgress <= System.currentTimeMillis() - 500) {
                                        lastProgress = System.currentTimeMillis();
                                        final int progress = (int) ((float) a / (float) size * 100);
                                        AndroidUtilities.RunOnUIThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    finalProgress.setProgress(progress);
                                                } catch (Exception e) {
                                                    FileLog.e("tmessages", e);
                                                }
                                            }
                                        });
                                    }
                                }
                            }
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                            result = false;
                        } finally {
                            if(source != null) {
                                source.close();
                            }
                            if(destination != null) {
                                destination.close();
                            }
                        }

                        if (result && (type == 0 || type == 1)) {
                            Utilities.addMediaToGallery(Uri.fromFile(destFile));
                        }
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    if (finalProgress != null) {
                        AndroidUtilities.RunOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    finalProgress.dismiss();
                                } catch (Exception e) {
                                    FileLog.e("tmessages", e);
                                }
                            }
                        });
                    }
                }
            }).start();
        }
    }

    public GifDrawable getGifDrawable(ChatMediaCell cell, boolean create) {
        if (cell == null) {
            return null;
        }

        MessageObject messageObject = cell.getMessageObject();
        if (messageObject == null) {
            return null;
        }

        if (currentGifDrawable != null && currentGifMessageObject != null && messageObject.messageOwner.id == currentGifMessageObject.messageOwner.id) {
            currentMediaCell = cell;
            currentGifDrawable.parentView = new WeakReference<View>(cell);
            return currentGifDrawable;
        }

        if (create) {
            if (currentMediaCell != null) {
                if (currentGifDrawable != null) {
                    currentGifDrawable.stop();
                    currentGifDrawable.recycle();
                }
                currentMediaCell.clearGifImage();
            }
            currentGifMessageObject = cell.getMessageObject();
            currentMediaCell = cell;

            File cacheFile = null;
            if (currentGifMessageObject.messageOwner.attachPath != null && currentGifMessageObject.messageOwner.attachPath.length() != 0) {
                File f = new File(currentGifMessageObject.messageOwner.attachPath);
                if (f.length() > 0) {
                    cacheFile = f;
                }
            }
            if (cacheFile == null) {
                cacheFile = new File(AndroidUtilities.getCacheDir(), messageObject.getFileName());
            }
            try {
                currentGifDrawable = new GifDrawable(cacheFile);
                currentGifDrawable.parentView = new WeakReference<View>(cell);
                return currentGifDrawable;
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }

        return null;
    }

    public void clearGifDrawable(ChatMediaCell cell) {
        if (cell == null) {
            return;
        }

        MessageObject messageObject = cell.getMessageObject();
        if (messageObject == null) {
            return;
        }

        if (currentGifMessageObject != null && messageObject.messageOwner.id == currentGifMessageObject.messageOwner.id) {
            if (currentGifDrawable != null) {
                currentGifDrawable.stop();
                currentGifDrawable.recycle();
                currentGifDrawable = null;
            }
            currentMediaCell = null;
            currentGifMessageObject = null;
        }
    }

    public static boolean isGif(Uri uri) {
        ParcelFileDescriptor parcelFD = null;
        FileInputStream input = null;
        try {
            parcelFD = ApplicationLoader.applicationContext.getContentResolver().openFileDescriptor(uri, "r");
            input = new FileInputStream(parcelFD.getFileDescriptor());
            if (input.getChannel().size() > 3) {
                byte[] header = new byte[3];
                input.read(header, 0, 3);
                String str = new String(header);
                if (str != null && str.equalsIgnoreCase("gif")) {
                    return true;
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        } finally {
            try {
                if (parcelFD != null) {
                    parcelFD.close();
                }
            } catch (Exception e2) {
                FileLog.e("tmessages", e2);
            }
            try {
                if (input != null) {
                    input.close();
                }
            } catch (Exception e2) {
                FileLog.e("tmessages", e2);
            }
        }
        return false;
    }

    public static String copyDocumentToCache(Uri uri, String ext) {
        ParcelFileDescriptor parcelFD = null;
        FileInputStream input = null;
        FileOutputStream output = null;
        try {
            int id = UserConfig.lastLocalId;
            UserConfig.lastLocalId--;
            parcelFD = ApplicationLoader.applicationContext.getContentResolver().openFileDescriptor(uri, "r");
            input = new FileInputStream(parcelFD.getFileDescriptor());
            File f = new File(AndroidUtilities.getCacheDir(), String.format(Locale.US, "%d.%s", id, ext));
            output = new FileOutputStream(f);
            input.getChannel().transferTo(0, input.getChannel().size(), output.getChannel());
            UserConfig.saveConfig(false);
            return f.getAbsolutePath();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        } finally {
            try {
                if (parcelFD != null) {
                    parcelFD.close();
                }
            } catch (Exception e2) {
                FileLog.e("tmessages", e2);
            }
            try {
                if (input != null) {
                    input.close();
                }
            } catch (Exception e2) {
                FileLog.e("tmessages", e2);
            }
            try {
                if (output != null) {
                    output.close();
                }
            } catch (Exception e2) {
                FileLog.e("tmessages", e2);
            }
        }
        return null;
    }

    public static void loadGalleryPhotosAlbums(final int guid) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<AlbumEntry> albumsSorted = new ArrayList<AlbumEntry>();
                HashMap<Integer, AlbumEntry> albums = new HashMap<Integer, AlbumEntry>();
                AlbumEntry allPhotosAlbum = null;
                String cameraFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath() + "/" + "Camera/";
                Integer cameraAlbumId = null;

                Cursor cursor = null;
                try {
                    cursor = MediaStore.Images.Media.query(ApplicationLoader.applicationContext.getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projectionPhotos, "", null, MediaStore.Images.Media.DATE_TAKEN + " DESC");
                    if (cursor != null) {
                        int imageIdColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID);
                        int bucketIdColumn = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID);
                        int bucketNameColumn = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
                        int dataColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                        int dateColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN);
                        int orientationColumn = cursor.getColumnIndex(MediaStore.Images.Media.ORIENTATION);

                        while (cursor.moveToNext()) {
                            int imageId = cursor.getInt(imageIdColumn);
                            int bucketId = cursor.getInt(bucketIdColumn);
                            String bucketName = cursor.getString(bucketNameColumn);
                            String path = cursor.getString(dataColumn);
                            long dateTaken = cursor.getLong(dateColumn);
                            int orientation = cursor.getInt(orientationColumn);

                            if (path == null || path.length() == 0) {
                                continue;
                            }

                            PhotoEntry photoEntry = new PhotoEntry(bucketId, imageId, dateTaken, path, orientation);

                            if (allPhotosAlbum == null) {
                                allPhotosAlbum = new AlbumEntry(0, LocaleController.getString("AllPhotos", R.string.AllPhotos), photoEntry);
                                albumsSorted.add(0, allPhotosAlbum);
                            }
                            if (allPhotosAlbum != null) {
                                allPhotosAlbum.addPhoto(photoEntry);
                            }

                            AlbumEntry albumEntry = albums.get(bucketId);
                            if (albumEntry == null) {
                                albumEntry = new AlbumEntry(bucketId, bucketName, photoEntry);
                                albums.put(bucketId, albumEntry);
                                if (cameraAlbumId == null && cameraFolder != null && path != null && path.startsWith(cameraFolder)) {
                                    albumsSorted.add(0, albumEntry);
                                    cameraAlbumId = bucketId;
                                } else {
                                    albumsSorted.add(albumEntry);
                                }
                            }

                            albumEntry.addPhoto(photoEntry);
                        }
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                } finally {
                    if (cursor != null) {
                        try {
                            cursor.close();
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }
                }
                final Integer cameraAlbumIdFinal = cameraAlbumId;
                AndroidUtilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.albumsDidLoaded, guid, albumsSorted, cameraAlbumIdFinal);
                    }
                });
            }
        }).start();
    }
}

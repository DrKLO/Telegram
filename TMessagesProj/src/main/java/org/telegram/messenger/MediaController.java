/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.messenger.exoplayer2.ExoPlayer;
import org.telegram.messenger.exoplayer2.ui.AspectRatioFrameLayout;
import org.telegram.messenger.query.SharedMediaQuery;
import org.telegram.messenger.video.InputSurface;
import org.telegram.messenger.video.MP4Builder;
import org.telegram.messenger.video.Mp4Movie;
import org.telegram.messenger.video.OutputSurface;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.EmbedBottomSheet;
import org.telegram.ui.Components.PhotoFilterView;
import org.telegram.ui.Components.PipRoundVideoView;
import org.telegram.ui.Components.VideoPlayer;
import org.telegram.ui.PhotoViewer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

public class MediaController implements AudioManager.OnAudioFocusChangeListener, NotificationCenter.NotificationCenterDelegate, SensorEventListener {

    private native int startRecord(String path);
    private native int writeFrame(ByteBuffer frame, int len);
    private native void stopRecord();
    private native int openOpusFile(String path);
    private native int seekOpusFile(float position);
    private native int isOpusFile(String path);
    private native void closeOpusFile();
    private native void readOpusFile(ByteBuffer buffer, int capacity, int[] args);
    private native long getTotalPcmDuration();
    public native byte[] getWaveform(String path);
    public native byte[] getWaveform2(short[] array, int length);

    public static int[] readArgs = new int[3];

    public interface FileDownloadProgressListener {
        void onFailedDownload(String fileName);
        void onSuccessDownload(String fileName);
        void onProgressDownload(String fileName, float progress);
        void onProgressUpload(String fileName, float progress, boolean isEncrypted);
        int getObserverTag();
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

    private static final String[] projectionVideo = {
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.DURATION
    };

    public static class AudioEntry {
        public long id;
        public String author;
        public String title;
        public String genre;
        public int duration;
        public String path;
        public MessageObject messageObject;
    }

    public static class AlbumEntry {
        public int bucketId;
        public String bucketName;
        public PhotoEntry coverPhoto;
        public ArrayList<PhotoEntry> photos = new ArrayList<>();
        public HashMap<Integer, PhotoEntry> photosByIds = new HashMap<>();

        public AlbumEntry(int bucketId, String bucketName, PhotoEntry coverPhoto) {
            this.bucketId = bucketId;
            this.bucketName = bucketName;
            this.coverPhoto = coverPhoto;
        }

        public void addPhoto(PhotoEntry photoEntry) {
            photos.add(photoEntry);
            photosByIds.put(photoEntry.imageId, photoEntry);
        }
    }

    public static class SavedFilterState {
        public float enhanceValue;
        public float exposureValue;
        public float contrastValue;
        public float warmthValue;
        public float saturationValue;
        public float fadeValue;
        public int tintShadowsColor;
        public int tintHighlightsColor;
        public float highlightsValue;
        public float shadowsValue;
        public float vignetteValue;
        public float grainValue;
        public int blurType;
        public float sharpenValue;
        public PhotoFilterView.CurvesToolValue curvesToolValue = new PhotoFilterView.CurvesToolValue();
        public float blurExcludeSize;
        public org.telegram.ui.Components.Point blurExcludePoint;
        public float blurExcludeBlurSize;
        public float blurAngle;
    }

    public static class PhotoEntry {
        public int bucketId;
        public int imageId;
        public long dateTaken;
        public int duration;
        public String path;
        public int orientation;
        public String thumbPath;
        public String imagePath;
        public VideoEditedInfo editedInfo;
        public boolean isVideo;
        public CharSequence caption;
        public boolean isFiltered;
        public boolean isPainted;
        public boolean isCropped;
        public boolean isMuted;
        public int ttl;
        public SavedFilterState savedFilterState;
        public ArrayList<TLRPC.InputDocument> stickers = new ArrayList<>();

        public PhotoEntry(int bucketId, int imageId, long dateTaken, String path, int orientation, boolean isVideo) {
            this.bucketId = bucketId;
            this.imageId = imageId;
            this.dateTaken = dateTaken;
            this.path = path;
            if (isVideo) {
                this.duration = orientation;
            } else {
                this.orientation = orientation;
            }
            this.isVideo = isVideo;
        }

        public void reset() {
            isFiltered = false;
            isPainted = false;
            isCropped = false;
            ttl = 0;
            imagePath = null;
            thumbPath = null;
            caption = null;
            savedFilterState = null;
            stickers.clear();
        }
    }

    public static class SearchImage {
        public String id;
        public String imageUrl;
        public String thumbUrl;
        public String localUrl;
        public int width;
        public int height;
        public int size;
        public int type;
        public int date;
        public String thumbPath;
        public String imagePath;
        public CharSequence caption;
        public TLRPC.Document document;
        public boolean isFiltered;
        public boolean isPainted;
        public boolean isCropped;
        public int ttl;
        public SavedFilterState savedFilterState;
        public ArrayList<TLRPC.InputDocument> stickers = new ArrayList<>();

        public void reset() {
            isFiltered = false;
            isPainted = false;
            isCropped = false;
            ttl = 0;
            imagePath = null;
            thumbPath = null;
            caption = null;
            savedFilterState = null;
            stickers.clear();
        }
    }

    public final static String MIME_TYPE = "video/avc";
    private final static int PROCESSOR_TYPE_OTHER = 0;
    private final static int PROCESSOR_TYPE_QCOM = 1;
    private final static int PROCESSOR_TYPE_INTEL = 2;
    private final static int PROCESSOR_TYPE_MTK = 3;
    private final static int PROCESSOR_TYPE_SEC = 4;
    private final static int PROCESSOR_TYPE_TI = 5;
    private final Object videoConvertSync = new Object();

    private HashMap<Long, Long> typingTimes = new HashMap<>();

    private SensorManager sensorManager;
    private boolean ignoreProximity;
    private PowerManager.WakeLock proximityWakeLock;
    private Sensor proximitySensor;
    private Sensor accelerometerSensor;
    private Sensor linearSensor;
    private Sensor gravitySensor;
    private boolean raiseToEarRecord;
    private ChatActivity raiseChat;
    private boolean accelerometerVertical;
    private int raisedToTop;
    private int raisedToBack;
    private int countLess;
    private long timeSinceRaise;
    private long lastTimestamp = 0;
    private boolean proximityTouched;
    private boolean proximityHasDifferentValues;
    private float lastProximityValue = -100;
    private boolean useFrontSpeaker;
    private boolean inputFieldHasText;
    private boolean allowStartRecord;
    private boolean ignoreOnPause;
    private boolean sensorsStarted;
    private float previousAccValue;
    private float[] gravity = new float[3];
    private float[] gravityFast = new float[3];
    private float[] linearAcceleration = new float[3];

    private int hasAudioFocus;
    private boolean callInProgress;
    private int audioFocus = AUDIO_NO_FOCUS_NO_DUCK;
    private boolean resumeAudioOnFocusGain;

    private static final float VOLUME_DUCK = 0.2f;
    private static final float VOLUME_NORMAL = 1.0f;
    private static final int AUDIO_NO_FOCUS_NO_DUCK = 0;
    private static final int AUDIO_NO_FOCUS_CAN_DUCK = 1;
    private static final int AUDIO_FOCUSED  = 2;

    private ArrayList<MessageObject> videoConvertQueue = new ArrayList<>();
    private final Object videoQueueSync = new Object();
    private boolean cancelCurrentVideoConversion = false;
    private boolean videoConvertFirstWrite = true;
    private HashMap<String, MessageObject> generatingWaveform = new HashMap<>();

    private boolean voiceMessagesPlaylistUnread;
    private ArrayList<MessageObject> voiceMessagesPlaylist;
    private HashMap<Integer, MessageObject> voiceMessagesPlaylistMap;

    public static final int AUTODOWNLOAD_MASK_PHOTO = 1;
    public static final int AUTODOWNLOAD_MASK_AUDIO = 2;
    public static final int AUTODOWNLOAD_MASK_VIDEO = 4;
    public static final int AUTODOWNLOAD_MASK_DOCUMENT = 8;
    public static final int AUTODOWNLOAD_MASK_MUSIC = 16;
    public static final int AUTODOWNLOAD_MASK_GIF = 32;
    public static final int AUTODOWNLOAD_MASK_VIDEOMESSAGE = 64;
    public boolean globalAutodownloadEnabled;
    public int mobileDataDownloadMask[] = new int[4];
    public int wifiDownloadMask[] = new int[4];
    public int roamingDownloadMask[] = new int[4];
    public int mobileMaxFileSize[] = new int[7];
    public int wifiMaxFileSize[] = new int[7];
    public int roamingMaxFileSize[] = new int[7];
    private int lastCheckMask = 0;
    private ArrayList<DownloadObject> photoDownloadQueue = new ArrayList<>();
    private ArrayList<DownloadObject> audioDownloadQueue = new ArrayList<>();
    private ArrayList<DownloadObject> videoMessageDownloadQueue = new ArrayList<>();
    private ArrayList<DownloadObject> documentDownloadQueue = new ArrayList<>();
    private ArrayList<DownloadObject> musicDownloadQueue = new ArrayList<>();
    private ArrayList<DownloadObject> gifDownloadQueue = new ArrayList<>();
    private ArrayList<DownloadObject> videoDownloadQueue = new ArrayList<>();
    private HashMap<String, DownloadObject> downloadQueueKeys = new HashMap<>();

    private boolean saveToGallery = true;
    private boolean autoplayGifs = true;
    private boolean raiseToSpeak = true;
    private boolean customTabs = true;
    private boolean directShare = true;
    private boolean inappCamera = true;
    private boolean roundCamera16to9 = true;
    private boolean groupPhotosEnabled = true;
    private boolean shuffleMusic;
    private boolean playOrderReversed;
    private int repeatMode;

    private static Runnable refreshGalleryRunnable;
    public static AlbumEntry allMediaAlbumEntry;
    public static AlbumEntry allPhotosAlbumEntry;
    private static Runnable broadcastPhotosRunnable;

    private HashMap<String, ArrayList<WeakReference<FileDownloadProgressListener>>> loadingFileObservers = new HashMap<>();
    private HashMap<String, ArrayList<MessageObject>> loadingFileMessagesObservers = new HashMap<>();
    private HashMap<Integer, String> observersByTag = new HashMap<>();
    private boolean listenerInProgress = false;
    private HashMap<String, FileDownloadProgressListener> addLaterArray = new HashMap<>();
    private ArrayList<FileDownloadProgressListener> deleteLaterArray = new ArrayList<>();
    private int lastTag = 0;

    private boolean isPaused = false;
    private MediaPlayer audioPlayer = null;
    private AudioTrack audioTrackPlayer = null;
    private long lastProgress = 0;
    private MessageObject playingMessageObject;
    private int playerBufferSize = 0;
    private boolean decodingFinished = false;
    private long currentTotalPcmDuration;
    private long lastPlayPcm;
    private int ignoreFirstProgress = 0;
    private Timer progressTimer = null;
    private final Object progressTimerSync = new Object();
    private int buffersWrited;
    private ArrayList<MessageObject> playlist = new ArrayList<>();
    private ArrayList<MessageObject> shuffledPlaylist = new ArrayList<>();
    private int currentPlaylistNum;
    private boolean forceLoopCurrentPlaylist;
    private boolean downloadingCurrentMessage;
    private boolean playMusicAgain;
    private AudioInfo audioInfo;
    private VideoPlayer videoPlayer;
    private TextureView currentTextureView;
    private PipRoundVideoView pipRoundVideoView;
    private int pipSwitchingState;
    private Activity baseActivity;
    private AspectRatioFrameLayout currentAspectRatioFrameLayout;
    private boolean isDrawingWasReady;
    private FrameLayout currentTextureViewContainer;
    private int currentAspectRatioFrameLayoutRotation;
    private float currentAspectRatioFrameLayoutRatio;
    private boolean currentAspectRatioFrameLayoutReady;

    private AudioRecord audioRecorder;
    private TLRPC.TL_document recordingAudio;
    private File recordingAudioFile;
    private long recordStartTime;
    private long recordTimeCount;
    private long recordDialogId;
    private MessageObject recordReplyingMessageObject;
    private DispatchQueue fileDecodingQueue;
    private DispatchQueue playerQueue;
    private ArrayList<AudioBuffer> usedPlayerBuffers = new ArrayList<>();
    private ArrayList<AudioBuffer> freePlayerBuffers = new ArrayList<>();
    private final Object playerSync = new Object();
    private final Object playerObjectSync = new Object();
    private short[] recordSamples = new short[1024];
    private long samplesCount;

    private final Object sync = new Object();

    private ArrayList<ByteBuffer> recordBuffers = new ArrayList<>();
    private ByteBuffer fileBuffer;
    private int recordBufferSize;
    private int sendAfterDone;

    private Runnable recordStartRunnable;
    private DispatchQueue recordQueue;
    private DispatchQueue fileEncodingQueue;
    private Runnable recordRunnable = new Runnable() {
        @Override
        public void run() {
            if (audioRecorder != null) {
                ByteBuffer buffer;
                if (!recordBuffers.isEmpty()) {
                    buffer = recordBuffers.get(0);
                    recordBuffers.remove(0);
                } else {
                    buffer = ByteBuffer.allocateDirect(recordBufferSize);
                    buffer.order(ByteOrder.nativeOrder());
                }
                buffer.rewind();
                int len = audioRecorder.read(buffer, buffer.capacity());
                if (len > 0) {
                    buffer.limit(len);
                    double sum = 0;
                    try {
                        long newSamplesCount = samplesCount + len / 2;
                        int currentPart = (int) (((double) samplesCount / (double) newSamplesCount) * recordSamples.length);
                        int newPart = recordSamples.length - currentPart;
                        float sampleStep;
                        if (currentPart != 0) {
                            sampleStep = (float) recordSamples.length / (float) currentPart;
                            float currentNum = 0;
                            for (int a = 0; a < currentPart; a++) {
                                recordSamples[a] = recordSamples[(int) currentNum];
                                currentNum += sampleStep;
                            }
                        }
                        int currentNum = currentPart;
                        float nextNum = 0;
                        sampleStep = (float) len / 2 / (float) newPart;
                        for (int i = 0; i < len / 2; i++) {
                            short peak = buffer.getShort();
                            if (peak > 2500) {
                                sum += peak * peak;
                            }
                            if (i == (int) nextNum && currentNum < recordSamples.length) {
                                recordSamples[currentNum] = peak;
                                nextNum += sampleStep;
                                currentNum++;
                            }
                        }
                        samplesCount = newSamplesCount;
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    buffer.position(0);
                    final double amplitude = Math.sqrt(sum / len / 2);
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
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.recordProgressChanged, System.currentTimeMillis() - recordStartTime, amplitude);
                        }
                    });
                } else {
                    recordBuffers.add(buffer);
                    stopRecordingInternal(sendAfterDone);
                }
            }
        }
    };

    private class SmsObserver extends ContentObserver {
        public SmsObserver() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            readSms();
        }
    }

    private void readSms() {
        /*Cursor cursor = null;
        try {
            cursor = ApplicationLoader.applicationContext.getContentResolver().query(Uri.parse("content://sms/sent"), null, null, null, null);
            while (cursor.moveToNext()) {
                String address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
                long data = cursor.getLong(cursor.getColumnIndexOrThrow("date"));
                String smsBody = cursor.getString(cursor.getColumnIndexOrThrow("body"));
                FileLog.d(address + " body = " + smsBody);
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }*/
    }

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

    private class GalleryObserverInternal extends ContentObserver {
        public GalleryObserverInternal() {
            super(null);
        }

        private void scheduleReloadRunnable() {
            AndroidUtilities.runOnUIThread(refreshGalleryRunnable = new Runnable() {
                @Override
                public void run() {
                    if (PhotoViewer.getInstance().isVisible()) {
                        scheduleReloadRunnable();
                        return;
                    }
                    refreshGalleryRunnable = null;
                    loadGalleryPhotosAlbums(0);
                }
            }, 2000);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            if (refreshGalleryRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(refreshGalleryRunnable);
            }
            scheduleReloadRunnable();
        }
    }

    public static int maskToIndex(int mask) {
        if (mask == AUTODOWNLOAD_MASK_PHOTO) {
            return 0;
        } else if (mask == AUTODOWNLOAD_MASK_AUDIO) {
            return 1;
        } else if (mask == AUTODOWNLOAD_MASK_VIDEO) {
            return 2;
        } else if (mask == AUTODOWNLOAD_MASK_DOCUMENT) {
            return 3;
        } else if (mask == AUTODOWNLOAD_MASK_MUSIC) {
            return 4;
        } else if (mask == AUTODOWNLOAD_MASK_GIF) {
            return 5;
        } else if (mask == AUTODOWNLOAD_MASK_VIDEOMESSAGE) {
            return 6;
        }
        return 0;
    }

    private class GalleryObserverExternal extends ContentObserver {
        public GalleryObserverExternal() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            if (refreshGalleryRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(refreshGalleryRunnable);
            }
            AndroidUtilities.runOnUIThread(refreshGalleryRunnable = new Runnable() {
                @Override
                public void run() {
                    refreshGalleryRunnable = null;
                    loadGalleryPhotosAlbums(0);
                }
            }, 2000);
        }
    }

    public static void checkGallery() {
        if (Build.VERSION.SDK_INT < 24 || allPhotosAlbumEntry == null) {
            return;
        }
        final int prevSize = allPhotosAlbumEntry.photos.size();
        Utilities.globalQueue.postRunnable(new Runnable() {
            @SuppressLint("NewApi")
            @Override
            public void run() {
                int count = 0;
                Cursor cursor = null;
                try {
                    if (ApplicationLoader.applicationContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                        cursor = MediaStore.Images.Media.query(ApplicationLoader.applicationContext.getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new String[] {"COUNT(_id)"}, null, null, null);
                        if (cursor != null) {
                            if (cursor.moveToNext()) {
                                count += cursor.getInt(0);
                            }
                        }
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
                try {
                    if (ApplicationLoader.applicationContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                        cursor = MediaStore.Images.Media.query(ApplicationLoader.applicationContext.getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI, new String[] {"COUNT(_id)"}, null, null, null);
                        if (cursor != null) {
                            if (cursor.moveToNext()) {
                                count += cursor.getInt(0);
                            }
                        }
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
                if (prevSize != count) {
                    if (refreshGalleryRunnable != null) {
                        AndroidUtilities.cancelRunOnUIThread(refreshGalleryRunnable);
                        refreshGalleryRunnable = null;
                    }
                    loadGalleryPhotosAlbums(0);
                }
            }
        }, 2000);
    }


    private ExternalObserver externalObserver;
    private InternalObserver internalObserver;
    private SmsObserver smsObserver;
    private long lastChatEnterTime;
    private long lastChatLeaveTime;
    private long lastMediaCheckTime;
    private TLRPC.EncryptedChat lastSecretChat;
    private TLRPC.User lastUser;
    private int lastMessageId;
    private ArrayList<Long> lastChatVisibleMessages;
    private int startObserverToken;
    private StopMediaObserverRunnable stopMediaObserverRunnable;

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
                    FileLog.e(e);
                }
                try {
                    if (externalObserver != null) {
                        ApplicationLoader.applicationContext.getContentResolver().unregisterContentObserver(externalObserver);
                        externalObserver = null;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
    }

    private String[] mediaProjections = null;

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
                buffer.order(ByteOrder.nativeOrder());
                recordBuffers.add(buffer);
            }
            for (int a = 0; a < 3; a++) {
                freePlayerBuffers.add(new AudioBuffer(playerBufferSize));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            sensorManager = (SensorManager) ApplicationLoader.applicationContext.getSystemService(Context.SENSOR_SERVICE);
            linearSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
            gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            if (linearSensor == null || gravitySensor == null) {
                FileLog.e("gravity or linear sensor not found");
                accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                linearSensor = null;
                gravitySensor = null;
            }
            proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
            PowerManager powerManager = (PowerManager) ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE);
            proximityWakeLock = powerManager.newWakeLock(0x00000020, "proximity");
        } catch (Exception e) {
            FileLog.e(e);
        }
        fileBuffer = ByteBuffer.allocateDirect(1920);
        recordQueue = new DispatchQueue("recordQueue");
        recordQueue.setPriority(Thread.MAX_PRIORITY);
        fileEncodingQueue = new DispatchQueue("fileEncodingQueue");
        fileEncodingQueue.setPriority(Thread.MAX_PRIORITY);
        playerQueue = new DispatchQueue("playerQueue");
        fileDecodingQueue = new DispatchQueue("fileDecodingQueue");

        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        for (int a = 0; a < 4; a++) {
            String key = "mobileDataDownloadMask" + (a == 0 ? "" : a);
            if (a == 0 || preferences.contains(key)) {
                mobileDataDownloadMask[a] = preferences.getInt(key, AUTODOWNLOAD_MASK_PHOTO | AUTODOWNLOAD_MASK_AUDIO | AUTODOWNLOAD_MASK_MUSIC | AUTODOWNLOAD_MASK_GIF | AUTODOWNLOAD_MASK_VIDEOMESSAGE);
                wifiDownloadMask[a] = preferences.getInt("wifiDownloadMask" + (a == 0 ? "" : a), AUTODOWNLOAD_MASK_PHOTO | AUTODOWNLOAD_MASK_AUDIO | AUTODOWNLOAD_MASK_MUSIC | AUTODOWNLOAD_MASK_GIF | AUTODOWNLOAD_MASK_VIDEOMESSAGE);
                roamingDownloadMask[a] = preferences.getInt("roamingDownloadMask" + (a == 0 ? "" : a), 0);
            } else {
                mobileDataDownloadMask[a] = mobileDataDownloadMask[0];
                wifiDownloadMask[a] = wifiDownloadMask[0];
                roamingDownloadMask[a] = roamingDownloadMask[0];
            }
        }
        for (int a = 0; a < 7; a++) {
            int sdefault;
            if (a == 1) {
                sdefault = 2 * 1024 * 1024;
            } else if (a == 6) {
                sdefault = 5 * 1024 * 1024;
            } else {
                sdefault = 10 * 1024 * 1024;
            }
            mobileMaxFileSize[a] = preferences.getInt("mobileMaxDownloadSize" + a, sdefault);
            wifiMaxFileSize[a] = preferences.getInt("wifiMaxDownloadSize" + a, sdefault);
            roamingMaxFileSize[a] = preferences.getInt("roamingMaxDownloadSize" + a, sdefault);
        }
        globalAutodownloadEnabled = preferences.getBoolean("globalAutodownloadEnabled", true);
        saveToGallery = preferences.getBoolean("save_gallery", false);
        autoplayGifs = preferences.getBoolean("autoplay_gif", true);
        raiseToSpeak = preferences.getBoolean("raise_to_speak", true);
        customTabs = preferences.getBoolean("custom_tabs", true);
        directShare = preferences.getBoolean("direct_share", true);
        shuffleMusic = preferences.getBoolean("shuffleMusic", false);
        playOrderReversed = preferences.getBoolean("playOrderReversed", false);
        inappCamera = preferences.getBoolean("inappCamera", true);
        roundCamera16to9 = preferences.getBoolean("roundCamera16to9", true);
        groupPhotosEnabled = preferences.getBoolean("groupPhotosEnabled", true);
        repeatMode = preferences.getInt("repeatMode", 0);

        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                NotificationCenter.getInstance().addObserver(MediaController.this, NotificationCenter.FileDidFailedLoad);
                NotificationCenter.getInstance().addObserver(MediaController.this, NotificationCenter.didReceivedNewMessages);
                NotificationCenter.getInstance().addObserver(MediaController.this, NotificationCenter.messagesDeleted);
                NotificationCenter.getInstance().addObserver(MediaController.this, NotificationCenter.FileDidLoaded);
                NotificationCenter.getInstance().addObserver(MediaController.this, NotificationCenter.FileLoadProgressChanged);
                NotificationCenter.getInstance().addObserver(MediaController.this, NotificationCenter.FileUploadProgressChanged);
                NotificationCenter.getInstance().addObserver(MediaController.this, NotificationCenter.removeAllMessagesFromDialog);
                NotificationCenter.getInstance().addObserver(MediaController.this, NotificationCenter.musicDidLoaded);
                NotificationCenter.getInstance().addObserver(MediaController.this, NotificationCenter.httpFileDidLoaded);
                NotificationCenter.getInstance().addObserver(MediaController.this, NotificationCenter.httpFileDidFailedLoad);
            }
        });

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

        mediaProjections = new String[]{
                MediaStore.Images.ImageColumns.DATA,
                MediaStore.Images.ImageColumns.DISPLAY_NAME,
                MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
                MediaStore.Images.ImageColumns.DATE_TAKEN,
                MediaStore.Images.ImageColumns.TITLE,
                MediaStore.Images.ImageColumns.WIDTH,
                MediaStore.Images.ImageColumns.HEIGHT
        };

        try {
            ApplicationLoader.applicationContext.getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, new GalleryObserverExternal());
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            ApplicationLoader.applicationContext.getContentResolver().registerContentObserver(MediaStore.Images.Media.INTERNAL_CONTENT_URI, true, new GalleryObserverInternal());
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            ApplicationLoader.applicationContext.getContentResolver().registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, new GalleryObserverExternal());
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            ApplicationLoader.applicationContext.getContentResolver().registerContentObserver(MediaStore.Video.Media.INTERNAL_CONTENT_URI, true, new GalleryObserverInternal());
        } catch (Exception e) {
            FileLog.e(e);
        }

        try {
            PhoneStateListener phoneStateListener = new PhoneStateListener() {
                @Override
                public void onCallStateChanged(final int state, String incomingNumber) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (state == TelephonyManager.CALL_STATE_RINGING) {
                                if (isPlayingMessage(getPlayingMessageObject()) && !isMessagePaused()) {
                                    pauseMessage(getPlayingMessageObject());
                                } else if (recordStartRunnable != null || recordingAudio != null) {
                                    stopRecording(2);
                                }
                                EmbedBottomSheet embedBottomSheet = EmbedBottomSheet.getInstance();
                                if (embedBottomSheet != null) {
                                    embedBottomSheet.pause();
                                }
                                callInProgress = true;
                            } else if (state == TelephonyManager.CALL_STATE_IDLE) {
                                callInProgress = false;
                            } else if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                                EmbedBottomSheet embedBottomSheet = EmbedBottomSheet.getInstance();
                                if (embedBottomSheet != null) {
                                    embedBottomSheet.pause();
                                }
                                callInProgress = true;
                            }
                        }
                    });
                }
            };
            TelephonyManager mgr = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
            if (mgr != null) {
                mgr.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            if (isPlayingMessage(getPlayingMessageObject()) && !isMessagePaused()) {
                pauseMessage(getPlayingMessageObject());
            }
            hasAudioFocus = 0;
            audioFocus = AUDIO_NO_FOCUS_NO_DUCK;
        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            audioFocus = AUDIO_FOCUSED;
            if (resumeAudioOnFocusGain) {
                resumeAudioOnFocusGain = false;
                if (isPlayingMessage(getPlayingMessageObject()) && isMessagePaused()) {
                    playMessage(getPlayingMessageObject());
                }
            }
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            audioFocus = AUDIO_NO_FOCUS_CAN_DUCK;
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            audioFocus = AUDIO_NO_FOCUS_NO_DUCK;
            if (isPlayingMessage(getPlayingMessageObject()) && !isMessagePaused()) {
                pauseMessage(getPlayingMessageObject());
                resumeAudioOnFocusGain = true;
            }
        }
        setPlayerVolume();
    }

    private void setPlayerVolume() {
        try {
            float volume;
            if (audioFocus != AUDIO_NO_FOCUS_CAN_DUCK) {
                volume = VOLUME_NORMAL;
            } else {
                volume = VOLUME_DUCK;
            }
            if (audioPlayer != null) {
                audioPlayer.setVolume(volume, volume);
            } else if (audioTrackPlayer != null) {
                audioTrackPlayer.setStereoVolume(volume, volume);
            } else if (videoPlayer != null) {
                videoPlayer.setVolume(volume);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void startProgressTimer(final MessageObject currentPlayingMessageObject) {
        synchronized (progressTimerSync) {
            if (progressTimer != null) {
                try {
                    progressTimer.cancel();
                    progressTimer = null;
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            progressTimer = new Timer();
            progressTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    synchronized (sync) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (currentPlayingMessageObject != null && (audioPlayer != null || audioTrackPlayer != null || videoPlayer != null) && !isPaused) {
                                    try {
                                        if (ignoreFirstProgress != 0) {
                                            ignoreFirstProgress--;
                                            return;
                                        }
                                        long progress;
                                        float value;
                                        if (videoPlayer != null) {
                                            progress = videoPlayer.getCurrentPosition();
                                            value = (float) lastProgress / (float) videoPlayer.getDuration();
                                            if (progress <= lastProgress) {
                                                return;
                                            }
                                            if (value >= 1) {
                                                return;
                                            }
                                        } else if (audioPlayer != null) {
                                            progress = audioPlayer.getCurrentPosition();
                                            value = (float) lastProgress / (float) audioPlayer.getDuration();
                                            if (progress <= lastProgress) {
                                                return;
                                            }
                                        } else {
                                            progress = (int) (lastPlayPcm / 48.0f);
                                            value = (float) lastPlayPcm / (float) currentTotalPcmDuration;
                                            if (progress == lastProgress) {
                                                return;
                                            }
                                        }
                                        lastProgress = progress;
                                        currentPlayingMessageObject.audioProgress = value;
                                        currentPlayingMessageObject.audioProgressSec = (int) (lastProgress / 1000);
                                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.messagePlayingProgressDidChanged, currentPlayingMessageObject.getId(), value);
                                    } catch (Exception e) {
                                        FileLog.e(e);
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
                    FileLog.e(e);
                }
            }
        }
    }

    public void cleanup() {
        cleanupPlayer(false, true);
        audioInfo = null;
        playMusicAgain = false;
        photoDownloadQueue.clear();
        audioDownloadQueue.clear();
        videoMessageDownloadQueue.clear();
        documentDownloadQueue.clear();
        videoDownloadQueue.clear();
        musicDownloadQueue.clear();
        gifDownloadQueue.clear();
        downloadQueueKeys.clear();
        videoConvertQueue.clear();
        playlist.clear();
        shuffledPlaylist.clear();
        generatingWaveform.clear();
        typingTimes.clear();
        voiceMessagesPlaylist = null;
        voiceMessagesPlaylistMap = null;
        cancelVideoConvert(null);
    }

    protected int getAutodownloadMask() {
        if (!globalAutodownloadEnabled) {
            return 0;
        }
        int result = 0;
        int masksArray[];
        if (ConnectionsManager.isConnectedToWiFi()) {
            masksArray = wifiDownloadMask;
        } else if (ConnectionsManager.isRoaming()) {
            masksArray = roamingDownloadMask;
        } else {
            masksArray = mobileDataDownloadMask;
        }
        for (int a = 0; a < 4; a++) {
            int mask = 0;
            if ((masksArray[a] & AUTODOWNLOAD_MASK_PHOTO) != 0) {
                mask |= AUTODOWNLOAD_MASK_PHOTO;
            }
            if ((masksArray[a] & AUTODOWNLOAD_MASK_AUDIO) != 0) {
                mask |= AUTODOWNLOAD_MASK_AUDIO;
            }
            if ((masksArray[a] & AUTODOWNLOAD_MASK_VIDEOMESSAGE) != 0) {
                mask |= AUTODOWNLOAD_MASK_VIDEOMESSAGE;
            }
            if ((masksArray[a] & AUTODOWNLOAD_MASK_VIDEO) != 0) {
                mask |= AUTODOWNLOAD_MASK_VIDEO;
            }
            if ((masksArray[a] & AUTODOWNLOAD_MASK_DOCUMENT) != 0) {
                mask |= AUTODOWNLOAD_MASK_DOCUMENT;
            }
            if ((masksArray[a] & AUTODOWNLOAD_MASK_MUSIC) != 0) {
                mask |= AUTODOWNLOAD_MASK_MUSIC;
            }
            if ((masksArray[a] & AUTODOWNLOAD_MASK_GIF) != 0) {
                mask |= AUTODOWNLOAD_MASK_GIF;
            }
            result |= mask << (a * 8);
        }
        return result;
    }

    protected int getAutodownloadMaskAll() {
        if (!globalAutodownloadEnabled) {
            return 0;
        }
        int mask = 0;
        for (int a = 0; a < 4; a++) {
            if ((mobileDataDownloadMask[a] & AUTODOWNLOAD_MASK_PHOTO) != 0 || (wifiDownloadMask[a] & AUTODOWNLOAD_MASK_PHOTO) != 0 || (roamingDownloadMask[a] & AUTODOWNLOAD_MASK_PHOTO) != 0) {
                mask |= AUTODOWNLOAD_MASK_PHOTO;
            }
            if ((mobileDataDownloadMask[a] & AUTODOWNLOAD_MASK_AUDIO) != 0 || (wifiDownloadMask[a] & AUTODOWNLOAD_MASK_AUDIO) != 0 || (roamingDownloadMask[a] & AUTODOWNLOAD_MASK_AUDIO) != 0) {
                mask |= AUTODOWNLOAD_MASK_AUDIO;
            }
            if ((mobileDataDownloadMask[a] & AUTODOWNLOAD_MASK_VIDEOMESSAGE) != 0 || (wifiDownloadMask[a] & AUTODOWNLOAD_MASK_VIDEOMESSAGE) != 0 || (roamingDownloadMask[a] & AUTODOWNLOAD_MASK_VIDEOMESSAGE) != 0) {
                mask |= AUTODOWNLOAD_MASK_VIDEOMESSAGE;
            }
            if ((mobileDataDownloadMask[a] & AUTODOWNLOAD_MASK_VIDEO) != 0 || (wifiDownloadMask[a] & AUTODOWNLOAD_MASK_VIDEO) != 0 || (roamingDownloadMask[a] & AUTODOWNLOAD_MASK_VIDEO) != 0) {
                mask |= AUTODOWNLOAD_MASK_VIDEO;
            }
            if ((mobileDataDownloadMask[a] & AUTODOWNLOAD_MASK_DOCUMENT) != 0 || (wifiDownloadMask[a] & AUTODOWNLOAD_MASK_DOCUMENT) != 0 || (roamingDownloadMask[a] & AUTODOWNLOAD_MASK_DOCUMENT) != 0) {
                mask |= AUTODOWNLOAD_MASK_DOCUMENT;
            }
            if ((mobileDataDownloadMask[a] & AUTODOWNLOAD_MASK_MUSIC) != 0 || (wifiDownloadMask[a] & AUTODOWNLOAD_MASK_MUSIC) != 0 || (roamingDownloadMask[a] & AUTODOWNLOAD_MASK_MUSIC) != 0) {
                mask |= AUTODOWNLOAD_MASK_MUSIC;
            }
            if ((mobileDataDownloadMask[a] & AUTODOWNLOAD_MASK_GIF) != 0 || (wifiDownloadMask[a] & AUTODOWNLOAD_MASK_GIF) != 0 || (roamingDownloadMask[a] & AUTODOWNLOAD_MASK_GIF) != 0) {
                mask |= AUTODOWNLOAD_MASK_GIF;
            }
        }
        return mask;
    }

    public void checkAutodownloadSettings() {
        int currentMask = getCurrentDownloadMask();
        if (currentMask == lastCheckMask) {
            return;
        }
        lastCheckMask = currentMask;
        if ((currentMask & AUTODOWNLOAD_MASK_PHOTO) != 0) {
            if (photoDownloadQueue.isEmpty()) {
                newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_PHOTO);
            }
        } else {
            for (int a = 0; a < photoDownloadQueue.size(); a++) {
                DownloadObject downloadObject = photoDownloadQueue.get(a);
                FileLoader.getInstance().cancelLoadFile((TLRPC.PhotoSize) downloadObject.object);
            }
            photoDownloadQueue.clear();
        }
        if ((currentMask & AUTODOWNLOAD_MASK_AUDIO) != 0) {
            if (audioDownloadQueue.isEmpty()) {
                newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_AUDIO);
            }
        } else {
            for (int a = 0; a < audioDownloadQueue.size(); a++) {
                DownloadObject downloadObject = audioDownloadQueue.get(a);
                FileLoader.getInstance().cancelLoadFile((TLRPC.Document) downloadObject.object);
            }
            audioDownloadQueue.clear();
        }
        if ((currentMask & AUTODOWNLOAD_MASK_VIDEOMESSAGE) != 0) {
            if (videoMessageDownloadQueue.isEmpty()) {
                newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_VIDEOMESSAGE);
            }
        } else {
            for (int a = 0; a < videoMessageDownloadQueue.size(); a++) {
                DownloadObject downloadObject = videoMessageDownloadQueue.get(a);
                FileLoader.getInstance().cancelLoadFile((TLRPC.Document) downloadObject.object);
            }
            videoMessageDownloadQueue.clear();
        }
        if ((currentMask & AUTODOWNLOAD_MASK_DOCUMENT) != 0) {
            if (documentDownloadQueue.isEmpty()) {
                newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_DOCUMENT);
            }
        } else {
            for (int a = 0; a < documentDownloadQueue.size(); a++) {
                DownloadObject downloadObject = documentDownloadQueue.get(a);
                TLRPC.Document document = (TLRPC.Document) downloadObject.object;
                FileLoader.getInstance().cancelLoadFile(document);
            }
            documentDownloadQueue.clear();
        }
        if ((currentMask & AUTODOWNLOAD_MASK_VIDEO) != 0) {
            if (videoDownloadQueue.isEmpty()) {
                newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_VIDEO);
            }
        } else {
            for (int a = 0; a < videoDownloadQueue.size(); a++) {
                DownloadObject downloadObject = videoDownloadQueue.get(a);
                FileLoader.getInstance().cancelLoadFile((TLRPC.Document) downloadObject.object);
            }
            videoDownloadQueue.clear();
        }
        if ((currentMask & AUTODOWNLOAD_MASK_MUSIC) != 0) {
            if (musicDownloadQueue.isEmpty()) {
                newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_MUSIC);
            }
        } else {
            for (int a = 0; a < musicDownloadQueue.size(); a++) {
                DownloadObject downloadObject = musicDownloadQueue.get(a);
                TLRPC.Document document = (TLRPC.Document) downloadObject.object;
                FileLoader.getInstance().cancelLoadFile(document);
            }
            musicDownloadQueue.clear();
        }
        if ((currentMask & AUTODOWNLOAD_MASK_GIF) != 0) {
            if (gifDownloadQueue.isEmpty()) {
                newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_GIF);
            }
        } else {
            for (int a = 0; a < gifDownloadQueue.size(); a++) {
                DownloadObject downloadObject = gifDownloadQueue.get(a);
                TLRPC.Document document = (TLRPC.Document) downloadObject.object;
                FileLoader.getInstance().cancelLoadFile(document);
            }
            gifDownloadQueue.clear();
        }

        int mask = getAutodownloadMaskAll();
        if (mask == 0) {
            MessagesStorage.getInstance().clearDownloadQueue(0);
        } else {
            if ((mask & AUTODOWNLOAD_MASK_PHOTO) == 0) {
                MessagesStorage.getInstance().clearDownloadQueue(AUTODOWNLOAD_MASK_PHOTO);
            }
            if ((mask & AUTODOWNLOAD_MASK_AUDIO) == 0) {
                MessagesStorage.getInstance().clearDownloadQueue(AUTODOWNLOAD_MASK_AUDIO);
            }
            if ((mask & AUTODOWNLOAD_MASK_VIDEOMESSAGE) == 0) {
                MessagesStorage.getInstance().clearDownloadQueue(AUTODOWNLOAD_MASK_VIDEOMESSAGE);
            }
            if ((mask & AUTODOWNLOAD_MASK_VIDEO) == 0) {
                MessagesStorage.getInstance().clearDownloadQueue(AUTODOWNLOAD_MASK_VIDEO);
            }
            if ((mask & AUTODOWNLOAD_MASK_DOCUMENT) == 0) {
                MessagesStorage.getInstance().clearDownloadQueue(AUTODOWNLOAD_MASK_DOCUMENT);
            }
            if ((mask & AUTODOWNLOAD_MASK_MUSIC) == 0) {
                MessagesStorage.getInstance().clearDownloadQueue(AUTODOWNLOAD_MASK_MUSIC);
            }
            if ((mask & AUTODOWNLOAD_MASK_GIF) == 0) {
                MessagesStorage.getInstance().clearDownloadQueue(AUTODOWNLOAD_MASK_GIF);
            }
        }
    }

    public boolean canDownloadMedia(MessageObject messageObject) {
        return canDownloadMedia(messageObject.messageOwner);
    }

    public boolean canDownloadMedia(TLRPC.Message message) {
        if (!globalAutodownloadEnabled) {
            return false;
        }
        int type;
        if (MessageObject.isPhoto(message)) {
            type = MediaController.AUTODOWNLOAD_MASK_PHOTO;
        } else if (MessageObject.isVoiceMessage(message)) {
            type = MediaController.AUTODOWNLOAD_MASK_AUDIO;
        } else if (MessageObject.isRoundVideoMessage(message)) {
            type = MediaController.AUTODOWNLOAD_MASK_VIDEOMESSAGE;
        } else if (MessageObject.isVideoMessage(message)) {
            type = MediaController.AUTODOWNLOAD_MASK_VIDEO;
        } else if (MessageObject.isMusicMessage(message)) {
            type = MediaController.AUTODOWNLOAD_MASK_MUSIC;
        } else if (MessageObject.isGifMessage(message)) {
            type = MediaController.AUTODOWNLOAD_MASK_GIF;
        } else {
            type = MediaController.AUTODOWNLOAD_MASK_DOCUMENT;
        }
        int mask;
        int index;
        int maxSize;
        TLRPC.Peer peer = message.to_id;
        if (peer != null) {
            if (peer.user_id != 0) {
                if (ContactsController.getInstance().contactsDict.containsKey(peer.user_id)) {
                    index = 0;
                } else {
                    index = 1;
                }
            } else if (peer.chat_id != 0) {
                index = 2;
            } else {
                if (MessageObject.isMegagroup(message)) {
                    index = 2;
                } else {
                    index = 3;
                }
            }
        } else {
            index = 1;
        }
        if (ConnectionsManager.isConnectedToWiFi()) {
            mask = wifiDownloadMask[index];
            maxSize = wifiMaxFileSize[maskToIndex(type)];
        } else if (ConnectionsManager.isRoaming()) {
            mask = roamingDownloadMask[index];
            maxSize = roamingMaxFileSize[maskToIndex(type)];
        } else {
            mask = mobileDataDownloadMask[index];
            maxSize = mobileMaxFileSize[maskToIndex(type)];
        }
        return (type == MediaController.AUTODOWNLOAD_MASK_PHOTO || MessageObject.getMessageSize(message) <= maxSize) && (mask & type) != 0;
    }

    private int getCurrentDownloadMask() {
        if (!globalAutodownloadEnabled) {
            return 0;
        }
        if (ConnectionsManager.isConnectedToWiFi()) {
            int mask = 0;
            for (int a = 0; a < 4; a++) {
                mask |= wifiDownloadMask[a];
            }
            return mask;
        } else if (ConnectionsManager.isRoaming()) {
            int mask = 0;
            for (int a = 0; a < 4; a++) {
                mask |= roamingDownloadMask[a];
            }
            return mask;
        } else {
            int mask = 0;
            for (int a = 0; a < 4; a++) {
                mask |= mobileDataDownloadMask[a];
            }
            return mask;
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
        } else if (type == AUTODOWNLOAD_MASK_VIDEOMESSAGE) {
            queue = videoMessageDownloadQueue;
        } else if (type == AUTODOWNLOAD_MASK_VIDEO) {
            queue = videoDownloadQueue;
        } else if (type == AUTODOWNLOAD_MASK_DOCUMENT) {
            queue = documentDownloadQueue;
        } else if (type == AUTODOWNLOAD_MASK_MUSIC) {
            queue = musicDownloadQueue;
        } else if (type == AUTODOWNLOAD_MASK_GIF) {
            queue = gifDownloadQueue;
        }
        for (int a = 0; a < objects.size(); a++) {
            DownloadObject downloadObject = objects.get(a);
            String path;
            if (downloadObject.object instanceof TLRPC.Document) {
                TLRPC.Document document = (TLRPC.Document) downloadObject.object;
                path = FileLoader.getAttachFileName(document);
            } else {
                path = FileLoader.getAttachFileName(downloadObject.object);
            }
            if (downloadQueueKeys.containsKey(path)) {
                continue;
            }

            boolean added = true;
            if (downloadObject.object instanceof TLRPC.PhotoSize) {
                FileLoader.getInstance().loadFile((TLRPC.PhotoSize) downloadObject.object, null, downloadObject.secret ? 2 : 0);
            } else if (downloadObject.object instanceof TLRPC.Document) {
                TLRPC.Document document = (TLRPC.Document) downloadObject.object;
                FileLoader.getInstance().loadFile(document, false, downloadObject.secret ? 2 : 0);
            } else {
                added = false;
            }
            if (added) {
                queue.add(downloadObject);
                downloadQueueKeys.put(path, downloadObject);
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
        if ((mask & AUTODOWNLOAD_MASK_VIDEOMESSAGE) != 0 && (downloadMask & AUTODOWNLOAD_MASK_VIDEOMESSAGE) != 0 && videoMessageDownloadQueue.isEmpty()) {
            MessagesStorage.getInstance().getDownloadQueue(AUTODOWNLOAD_MASK_VIDEOMESSAGE);
        }
        if ((mask & AUTODOWNLOAD_MASK_VIDEO) != 0 && (downloadMask & AUTODOWNLOAD_MASK_VIDEO) != 0 && videoDownloadQueue.isEmpty()) {
            MessagesStorage.getInstance().getDownloadQueue(AUTODOWNLOAD_MASK_VIDEO);
        }
        if ((mask & AUTODOWNLOAD_MASK_DOCUMENT) != 0 && (downloadMask & AUTODOWNLOAD_MASK_DOCUMENT) != 0 && documentDownloadQueue.isEmpty()) {
            MessagesStorage.getInstance().getDownloadQueue(AUTODOWNLOAD_MASK_DOCUMENT);
        }
        if ((mask & AUTODOWNLOAD_MASK_MUSIC) != 0 && (downloadMask & AUTODOWNLOAD_MASK_MUSIC) != 0 && musicDownloadQueue.isEmpty()) {
            MessagesStorage.getInstance().getDownloadQueue(AUTODOWNLOAD_MASK_MUSIC);
        }
        if ((mask & AUTODOWNLOAD_MASK_GIF) != 0 && (downloadMask & AUTODOWNLOAD_MASK_GIF) != 0 && gifDownloadQueue.isEmpty()) {
            MessagesStorage.getInstance().getDownloadQueue(AUTODOWNLOAD_MASK_GIF);
        }
    }

    private void checkDownloadFinished(String fileName, int state) {
        DownloadObject downloadObject = downloadQueueKeys.get(fileName);
        if (downloadObject != null) {
            downloadQueueKeys.remove(fileName);
            if (state == 0 || state == 2) {
                MessagesStorage.getInstance().removeFromDownloadQueue(downloadObject.id, downloadObject.type, false /*state != 0*/);
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
            } else if (downloadObject.type == AUTODOWNLOAD_MASK_VIDEOMESSAGE) {
                videoMessageDownloadQueue.remove(downloadObject);
                if (videoMessageDownloadQueue.isEmpty()) {
                    newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_VIDEOMESSAGE);
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
            } else if (downloadObject.type == AUTODOWNLOAD_MASK_MUSIC) {
                musicDownloadQueue.remove(downloadObject);
                if (musicDownloadQueue.isEmpty()) {
                    newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_MUSIC);
                }
            } else if (downloadObject.type == AUTODOWNLOAD_MASK_GIF) {
                gifDownloadQueue.remove(downloadObject);
                if (gifDownloadQueue.isEmpty()) {
                    newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_GIF);
                }
            }
        }
    }

    public void startMediaObserver() {
        ApplicationLoader.applicationHandler.removeCallbacks(stopMediaObserverRunnable);
        startObserverToken++;
        try {
            if (internalObserver == null) {
                ApplicationLoader.applicationContext.getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, externalObserver = new ExternalObserver());
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            if (externalObserver == null) {
                ApplicationLoader.applicationContext.getContentResolver().registerContentObserver(MediaStore.Images.Media.INTERNAL_CONTENT_URI, false, internalObserver = new InternalObserver());
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void startSmsObserver() {
        try {
            if (smsObserver == null) {
                ApplicationLoader.applicationContext.getContentResolver().registerContentObserver(Uri.parse("content://sms"), false, smsObserver = new SmsObserver());
            }
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (smsObserver != null) {
                            ApplicationLoader.applicationContext.getContentResolver().unregisterContentObserver(smsObserver);
                            smsObserver = null;
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }, 5 * 60 * 1000);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void stopMediaObserver() {
        if (stopMediaObserverRunnable == null) {
            stopMediaObserverRunnable = new StopMediaObserverRunnable();
        }
        stopMediaObserverRunnable.currentObserverToken = startObserverToken;
        ApplicationLoader.applicationHandler.postDelayed(stopMediaObserverRunnable, 5000);
    }

    private void processMediaObserver(Uri uri) {
        try {
            Point size = AndroidUtilities.getRealScreenSize();

            Cursor cursor = ApplicationLoader.applicationContext.getContentResolver().query(uri, mediaProjections, null, null, "date_added DESC LIMIT 1");
            final ArrayList<Long> screenshotDates = new ArrayList<>();
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String val = "";
                    String data = cursor.getString(0);
                    String display_name = cursor.getString(1);
                    String album_name = cursor.getString(2);
                    long date = cursor.getLong(3);
                    String title = cursor.getString(4);
                    int photoW = cursor.getInt(5);
                    int photoH = cursor.getInt(6);
                    if (data != null && data.toLowerCase().contains("screenshot") ||
                            display_name != null && display_name.toLowerCase().contains("screenshot") ||
                            album_name != null && album_name.toLowerCase().contains("screenshot") ||
                            title != null && title.toLowerCase().contains("screenshot")) {
                        try {
                            if (photoW == 0 || photoH == 0) {
                                BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                                bmOptions.inJustDecodeBounds = true;
                                BitmapFactory.decodeFile(data, bmOptions);
                                photoW = bmOptions.outWidth;
                                photoH = bmOptions.outHeight;
                            }
                            if (photoW <= 0 || photoH <= 0 || (photoW == size.x && photoH == size.y || photoH == size.x && photoW == size.y)) {
                                screenshotDates.add(date);
                            }
                        } catch (Exception e) {
                            screenshotDates.add(date);
                        }
                    }
                }
                cursor.close();
            }
            if (!screenshotDates.isEmpty()) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.screenshotTook);
                        checkScreenshots(screenshotDates);
                    }
                });
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void checkScreenshots(ArrayList<Long> dates) {
        if (dates == null || dates.isEmpty() || lastChatEnterTime == 0 || (lastUser == null && !(lastSecretChat instanceof TLRPC.TL_encryptedChat))) {
            return;
        }
        long dt = 2000;
        boolean send = false;
        for (int a = 0; a < dates.size(); a++) {
            Long date = dates.get(a);
            if (lastMediaCheckTime != 0 && date <= lastMediaCheckTime) {
                continue;
            }

            if (date >= lastChatEnterTime) {
                if (lastChatLeaveTime == 0 || date <= lastChatLeaveTime + dt) {
                    lastMediaCheckTime = Math.max(lastMediaCheckTime, date);
                    send = true;
                }
            }
        }
        if (send) {
            if (lastSecretChat != null) {
                SecretChatHelper.getInstance().sendScreenshotMessage(lastSecretChat, lastChatVisibleMessages, null);
            } else {
                SendMessagesHelper.getInstance().sendScreenshotMessage(lastUser, lastMessageId, null);
            }
        }
    }

    public void setLastVisibleMessageIds(long enterTime, long leaveTime, TLRPC.User user, TLRPC.EncryptedChat encryptedChat, ArrayList<Long> visibleMessages, int visibleMessage) {
        lastChatEnterTime = enterTime;
        lastChatLeaveTime = leaveTime;
        lastSecretChat = encryptedChat;
        lastUser = user;
        lastMessageId = visibleMessage;
        lastChatVisibleMessages = visibleMessages;
    }

    public int generateObserverTag() {
        return lastTag++;
    }

    public void addLoadingFileObserver(String fileName, FileDownloadProgressListener observer) {
        addLoadingFileObserver(fileName, null, observer);
    }

    public void addLoadingFileObserver(String fileName, MessageObject messageObject, FileDownloadProgressListener observer) {
        if (listenerInProgress) {
            addLaterArray.put(fileName, observer);
            return;
        }
        removeLoadingFileObserver(observer);

        ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
        if (arrayList == null) {
            arrayList = new ArrayList<>();
            loadingFileObservers.put(fileName, arrayList);
        }
        arrayList.add(new WeakReference<>(observer));
        if (messageObject != null) {
            ArrayList<MessageObject> messageObjects = loadingFileMessagesObservers.get(fileName);
            if (messageObjects == null) {
                messageObjects = new ArrayList<>();
                loadingFileMessagesObservers.put(fileName, messageObjects);
            }
            messageObjects.add(messageObject);
        }

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

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.FileDidFailedLoad || id == NotificationCenter.httpFileDidFailedLoad) {
            listenerInProgress = true;
            String fileName = (String) args[0];
            ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
            if (arrayList != null) {
                for (int a = 0; a < arrayList.size(); a++) {
                    WeakReference<FileDownloadProgressListener> reference = arrayList.get(a);
                    if (reference.get() != null) {
                        reference.get().onFailedDownload(fileName);
                        observersByTag.remove(reference.get().getObserverTag());
                    }
                }
                loadingFileObservers.remove(fileName);
            }
            listenerInProgress = false;
            processLaterArrays();
            checkDownloadFinished(fileName, (Integer) args[1]);
        } else if (id == NotificationCenter.FileDidLoaded || id == NotificationCenter.httpFileDidLoaded) {
            listenerInProgress = true;
            String fileName = (String) args[0];
            if (downloadingCurrentMessage && playingMessageObject != null) {
                String file = FileLoader.getAttachFileName(playingMessageObject.getDocument());
                if (file.equals(fileName)) {
                    playMusicAgain = true;
                    playMessage(playingMessageObject);
                }
            }
            ArrayList<MessageObject> messageObjects = loadingFileMessagesObservers.get(fileName);
            if (messageObjects != null) {
                for (int a = 0; a < messageObjects.size(); a++) {
                    MessageObject messageObject = messageObjects.get(a);
                    messageObject.mediaExists = true;
                }
                loadingFileMessagesObservers.remove(fileName);
            }
            ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
            if (arrayList != null) {
                for (int a = 0; a < arrayList.size(); a++) {
                    WeakReference<FileDownloadProgressListener> reference = arrayList.get(a);
                    if (reference.get() != null) {
                        reference.get().onSuccessDownload(fileName);
                        observersByTag.remove(reference.get().getObserverTag());
                    }
                }
                loadingFileObservers.remove(fileName);
            }
            listenerInProgress = false;
            processLaterArrays();
            checkDownloadFinished(fileName, 0);
        } else if (id == NotificationCenter.FileLoadProgressChanged) {
            listenerInProgress = true;
            String fileName = (String) args[0];
            ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
            if (arrayList != null) {
                Float progress = (Float) args[1];
                for (WeakReference<FileDownloadProgressListener> reference : arrayList) {
                    if (reference.get() != null) {
                        reference.get().onProgressDownload(fileName, progress);
                    }
                }
            }
            listenerInProgress = false;
            processLaterArrays();
        } else if (id == NotificationCenter.FileUploadProgressChanged) {
            listenerInProgress = true;
            String fileName = (String) args[0];
            ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
            if (arrayList != null) {
                Float progress = (Float) args[1];
                Boolean enc = (Boolean) args[2];
                for (WeakReference<FileDownloadProgressListener> reference : arrayList) {
                    if (reference.get() != null) {
                        reference.get().onProgressUpload(fileName, progress, enc);
                    }
                }
            }
            listenerInProgress = false;
            processLaterArrays();
            try {
                ArrayList<SendMessagesHelper.DelayedMessage> delayedMessages = SendMessagesHelper.getInstance().getDelayedMessages(fileName);
                if (delayedMessages != null) {
                    for (int a = 0; a < delayedMessages.size(); a++) {
                        SendMessagesHelper.DelayedMessage delayedMessage = delayedMessages.get(a);
                        if (delayedMessage.encryptedChat == null) {
                            long dialog_id = delayedMessage.peer;
                            if (delayedMessage.type == 4) {
                                Long lastTime = typingTimes.get(dialog_id);
                                if (lastTime == null || lastTime + 4000 < System.currentTimeMillis()) {
                                    MessagesController.getInstance().sendTyping(dialog_id, 4, 0);
                                    typingTimes.put(dialog_id, System.currentTimeMillis());
                                }
                            } else {
                                Long lastTime = typingTimes.get(dialog_id);
                                TLRPC.Document document = delayedMessage.obj.getDocument();
                                if (lastTime == null || lastTime + 4000 < System.currentTimeMillis()) {
                                    if (delayedMessage.obj.isRoundVideo()) {
                                        MessagesController.getInstance().sendTyping(dialog_id, 8, 0);
                                    } else if (delayedMessage.obj.isVideo()) {
                                        MessagesController.getInstance().sendTyping(dialog_id, 5, 0);
                                    } else if (delayedMessage.obj.getDocument() != null) {
                                        MessagesController.getInstance().sendTyping(dialog_id, 3, 0);
                                    } else if (delayedMessage.location != null) {
                                        MessagesController.getInstance().sendTyping(dialog_id, 4, 0);
                                    }
                                    typingTimes.put(dialog_id, System.currentTimeMillis());
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else if (id == NotificationCenter.messagesDeleted) {
            int channelId = (Integer) args[1];
            ArrayList<Integer> markAsDeletedMessages = (ArrayList<Integer>) args[0];
            if (playingMessageObject != null) {
                if (channelId == playingMessageObject.messageOwner.to_id.channel_id) {
                    if (markAsDeletedMessages.contains(playingMessageObject.getId())) {
                        cleanupPlayer(true, true);
                    }
                }
            }
            if (voiceMessagesPlaylist != null && !voiceMessagesPlaylist.isEmpty()) {
                MessageObject messageObject = voiceMessagesPlaylist.get(0);
                if (channelId == messageObject.messageOwner.to_id.channel_id) {
                    for (int a = 0; a < markAsDeletedMessages.size(); a++) {
                        messageObject = voiceMessagesPlaylistMap.remove(markAsDeletedMessages.get(a));
                        if (messageObject != null) {
                            voiceMessagesPlaylist.remove(messageObject);
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.removeAllMessagesFromDialog) {
            long did = (Long) args[0];
            if (playingMessageObject != null && playingMessageObject.getDialogId() == did) {
                cleanupPlayer(false, true);
            }
        } else if (id == NotificationCenter.musicDidLoaded) {
            long did = (Long) args[0];
            if (playingMessageObject != null && playingMessageObject.isMusic() && playingMessageObject.getDialogId() == did) {
                ArrayList<MessageObject> arrayList = (ArrayList<MessageObject>) args[1];
                playlist.addAll(0, arrayList);
                if (shuffleMusic) {
                    buildShuffledPlayList();
                    currentPlaylistNum = 0;
                } else {
                    currentPlaylistNum += arrayList.size();
                }
            }
        } else if (id == NotificationCenter.didReceivedNewMessages) {
            if (voiceMessagesPlaylist != null && !voiceMessagesPlaylist.isEmpty()) {
                MessageObject messageObject = voiceMessagesPlaylist.get(0);
                long did = (Long) args[0];
                if (did == messageObject.getDialogId()) {
                    ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[1];
                    for (int a = 0; a < arr.size(); a++) {
                        messageObject = arr.get(a);
                        if ((messageObject.isVoice() || messageObject.isRoundVideo()) && (!voiceMessagesPlaylistUnread || messageObject.isContentUnread() && !messageObject.isOut())) {
                            voiceMessagesPlaylist.add(messageObject);
                            voiceMessagesPlaylistMap.put(messageObject.getId(), messageObject);
                        }
                    }
                }
            }
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
                        FileLog.e(e);
                    }
                    buffersWrited++;

                    if (count > 0) {
                        final long pcm = buffer.pcmOffset;
                        final int marker = buffer.finished == 1 ? count : -1;
                        final int finalBuffersWrited = buffersWrited;
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                lastPlayPcm = pcm;
                                if (marker != -1) {
                                    if (audioTrackPlayer != null) {
                                        audioTrackPlayer.setNotificationMarkerPosition(1);
                                    }
                                    if (finalBuffersWrited == 1) {
                                        cleanupPlayer(true, true, true);
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

    protected boolean isRecordingAudio() {
        return recordStartRunnable != null || recordingAudio != null;
    }

    private boolean isNearToSensor(float value) {
        return value < 5.0f && value != proximitySensor.getMaximumRange();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!sensorsStarted) {
            return;
        }
        if(VoIPService.getSharedInstance()!=null)
            return;
        if (event.sensor == proximitySensor) {
            FileLog.e("proximity changed to " + event.values[0]);
            if (lastProximityValue == -100) {
                lastProximityValue = event.values[0];
            } else if (lastProximityValue != event.values[0]) {
                proximityHasDifferentValues = true;
            }
            if (proximityHasDifferentValues) {
                proximityTouched = isNearToSensor(event.values[0]);
            }
        } else if (event.sensor == accelerometerSensor) {
            //0.98039215f
            final double alpha = lastTimestamp == 0 ? 0.98f : 1.0 / (1.0 + (event.timestamp - lastTimestamp) / 1000000000.0);
            final float alphaFast = 0.8f;
            lastTimestamp = event.timestamp;
            gravity[0] = (float) (alpha * gravity[0] + (1.0 - alpha) * event.values[0]);
            gravity[1] = (float) (alpha * gravity[1] + (1.0 - alpha) * event.values[1]);
            gravity[2] = (float) (alpha * gravity[2] + (1.0 - alpha) * event.values[2]);
            gravityFast[0] = (alphaFast * gravity[0] + (1.0f - alphaFast) * event.values[0]);
            gravityFast[1] = (alphaFast * gravity[1] + (1.0f - alphaFast) * event.values[1]);
            gravityFast[2] = (alphaFast * gravity[2] + (1.0f - alphaFast) * event.values[2]);

            linearAcceleration[0] = event.values[0] - gravity[0];
            linearAcceleration[1] = event.values[1] - gravity[1];
            linearAcceleration[2] = event.values[2] - gravity[2];
        } else if (event.sensor == linearSensor) {
            linearAcceleration[0] = event.values[0];
            linearAcceleration[1] = event.values[1];
            linearAcceleration[2] = event.values[2];
        } else if (event.sensor == gravitySensor) {
            gravityFast[0] = gravity[0] = event.values[0];
            gravityFast[1] = gravity[1] = event.values[1];
            gravityFast[2] = gravity[2] = event.values[2];
        }
        final float minDist = 15.0f;
        final int minCount = 6;
        final int countLessMax = 10;
        if (event.sensor == linearSensor || event.sensor == gravitySensor || event.sensor == accelerometerSensor) {
            float val = gravity[0] * linearAcceleration[0] + gravity[1] * linearAcceleration[1] + gravity[2] * linearAcceleration[2];
            if (raisedToBack != minCount) {
                if (val > 0 && previousAccValue > 0) {
                    if (val > minDist && raisedToBack == 0) {
                        if (raisedToTop < minCount && !proximityTouched) {
                            raisedToTop++;
                            if (raisedToTop == minCount) {
                                countLess = 0;
                            }
                        }
                    } else {
                        if (val < minDist) {
                            countLess++;
                        }
                        if (countLess == countLessMax || raisedToTop != minCount || raisedToBack != 0) {
                            raisedToBack = 0;
                            raisedToTop = 0;
                            countLess = 0;
                        }
                    }
                } else if (val < 0 && previousAccValue < 0) {
                    if (raisedToTop == minCount && val < -minDist) {
                        if (raisedToBack < minCount) {
                            raisedToBack++;
                            if (raisedToBack == minCount) {
                                raisedToTop = 0;
                                countLess = 0;
                                timeSinceRaise = System.currentTimeMillis();
                                //FileLog.e("motion detected");
                            }
                        }
                    } else {
                        if (val > -minDist) {
                            countLess++;
                        }
                        if (countLess == countLessMax || raisedToTop != minCount || raisedToBack != 0) {
                            raisedToTop = 0;
                            raisedToBack = 0;
                            countLess = 0;
                        }
                    }
                }
            }
            previousAccValue = val;
            accelerometerVertical = gravityFast[1] > 2.5f && Math.abs(gravityFast[2]) < 4.0f && /*Math.abs(gravityFast[0]) < 9.0f &&*/ Math.abs(gravityFast[0]) > 1.5f;
            //FileLog.e(accelerometerVertical + "    val = " + val + " acc (" + linearAcceleration[0] + ", " + linearAcceleration[1] + ", " + linearAcceleration[2] + ") grav (" + gravityFast[0] + ", " + gravityFast[1] + ", " + gravityFast[2] + ")");
        }
        if (raisedToBack == minCount && accelerometerVertical && proximityTouched && !NotificationsController.getInstance().audioManager.isWiredHeadsetOn()) {
            FileLog.e("sensor values reached");
            if (playingMessageObject == null && recordStartRunnable == null && recordingAudio == null && !PhotoViewer.getInstance().isVisible() && ApplicationLoader.isScreenOn && !inputFieldHasText && allowStartRecord && raiseChat != null && !callInProgress) {
                if (!raiseToEarRecord) {
                    FileLog.e("start record");
                    useFrontSpeaker = true;
                    if (!raiseChat.playFirstUnreadVoiceMessage()) {
                        raiseToEarRecord = true;
                        useFrontSpeaker = false;
                        startRecording(raiseChat.getDialogId(), null);
                    }
                    if (useFrontSpeaker) {
                        setUseFrontSpeaker(true);
                    }
                    ignoreOnPause = true;
                    if (proximityHasDifferentValues && proximityWakeLock != null && !proximityWakeLock.isHeld()) {
                        proximityWakeLock.acquire();
                    }
                }
            } else if (playingMessageObject != null && (playingMessageObject.isVoice() || playingMessageObject.isRoundVideo())) {
                if (!useFrontSpeaker) {
                    FileLog.e("start listen");
                    if (proximityHasDifferentValues && proximityWakeLock != null && !proximityWakeLock.isHeld()) {
                        proximityWakeLock.acquire();
                    }
                    setUseFrontSpeaker(true);
                    startAudioAgain(false);
                    ignoreOnPause = true;
                }
            }
            raisedToBack = 0;
            raisedToTop = 0;
            countLess = 0;
        } else if (proximityTouched) {
            if (playingMessageObject != null && (playingMessageObject.isVoice() || playingMessageObject.isRoundVideo())) {
                if (!useFrontSpeaker) {
                    FileLog.e("start listen by proximity only");
                    if (proximityHasDifferentValues && proximityWakeLock != null && !proximityWakeLock.isHeld()) {
                        proximityWakeLock.acquire();
                    }
                    setUseFrontSpeaker(true);
                    startAudioAgain(false);
                    ignoreOnPause = true;
                }
            }
        } else if (!proximityTouched) {
            if (raiseToEarRecord) {
                FileLog.e("stop record");
                stopRecording(2);
                raiseToEarRecord = false;
                ignoreOnPause = false;
                if (proximityHasDifferentValues && proximityWakeLock != null && proximityWakeLock.isHeld()) {
                    proximityWakeLock.release();
                }
            } else if (useFrontSpeaker) {
                FileLog.e("stop listen");
                useFrontSpeaker = false;
                startAudioAgain(true);
                ignoreOnPause = false;
                if (proximityHasDifferentValues && proximityWakeLock != null && proximityWakeLock.isHeld()) {
                    proximityWakeLock.release();
                }
            }
        }
        if (timeSinceRaise != 0 && raisedToBack == minCount && Math.abs(System.currentTimeMillis() - timeSinceRaise) > 1000) {
            raisedToBack = 0;
            raisedToTop = 0;
            countLess = 0;
            timeSinceRaise = 0;
        }
    }

    private void setUseFrontSpeaker(boolean value) {
        useFrontSpeaker = value;
        AudioManager audioManager = NotificationsController.getInstance().audioManager;
        if (useFrontSpeaker) {
            audioManager.setBluetoothScoOn(false);
            audioManager.setSpeakerphoneOn(false);
        } else {
            audioManager.setSpeakerphoneOn(true);
        }
    }

    public void startRecordingIfFromSpeaker() {
        if (!useFrontSpeaker || raiseChat == null || !allowStartRecord) {
            return;
        }
        raiseToEarRecord = true;
        startRecording(raiseChat.getDialogId(), null);
        ignoreOnPause = true;
    }

    private void startAudioAgain(boolean paused) {
        if (playingMessageObject == null) {
            return;
        }

        NotificationCenter.getInstance().postNotificationName(NotificationCenter.audioRouteChanged, useFrontSpeaker);
        if (videoPlayer != null) {
            videoPlayer.setStreamType(useFrontSpeaker ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);
            if (!paused) {
                videoPlayer.play();
            } else {
                videoPlayer.pause();
            }
        } else {
            boolean post = audioPlayer != null;
            final MessageObject currentMessageObject = playingMessageObject;
            float progress = playingMessageObject.audioProgress;
            cleanupPlayer(false, true);
            currentMessageObject.audioProgress = progress;
            playMessage(currentMessageObject);
            if (paused) {
                if (post) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            pauseMessage(currentMessageObject);
                        }
                    }, 100);
                } else {
                    pauseMessage(currentMessageObject);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void setInputFieldHasText(boolean value) {
        inputFieldHasText = value;
    }

    public void setAllowStartRecord(boolean value) {
        allowStartRecord = value;
    }

    public void startRaiseToEarSensors(ChatActivity chatActivity) {
        if (chatActivity == null || accelerometerSensor == null && (gravitySensor == null || linearAcceleration == null) || proximitySensor == null) {
            return;
        }
        raiseChat = chatActivity;
        if (!raiseToSpeak && (playingMessageObject == null || !playingMessageObject.isVoice() && !playingMessageObject.isRoundVideo())) {
            return;
        }
        if (!sensorsStarted) {
            gravity[0] = gravity[1] = gravity[2] = 0;
            linearAcceleration[0] = linearAcceleration[1] = linearAcceleration[2] = 0;
            gravityFast[0] = gravityFast[1] = gravityFast[2] = 0;
            lastTimestamp = 0;
            previousAccValue = 0;
            raisedToTop = 0;
            countLess = 0;
            raisedToBack = 0;
            Utilities.globalQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    if (gravitySensor != null) {
                        sensorManager.registerListener(MediaController.this, gravitySensor, 30000);
                    }
                    if (linearSensor != null) {
                        sensorManager.registerListener(MediaController.this, linearSensor, 30000);
                    }
                    if (accelerometerSensor != null) {
                        sensorManager.registerListener(MediaController.this, accelerometerSensor, 30000);
                    }
                    sensorManager.registerListener(MediaController.this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
                }
            });
            sensorsStarted = true;
        }
    }

    public void stopRaiseToEarSensors(ChatActivity chatActivity) {
        if (ignoreOnPause) {
            ignoreOnPause = false;
            return;
        }
        if (!sensorsStarted || ignoreOnPause || accelerometerSensor == null && (gravitySensor == null || linearAcceleration == null) || proximitySensor == null || raiseChat != chatActivity) {
            return;
        }
        raiseChat = null;
        stopRecording(0);
        sensorsStarted = false;
        accelerometerVertical = false;
        proximityTouched = false;
        raiseToEarRecord = false;
        useFrontSpeaker = false;
        Utilities.globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (linearSensor != null) {
                    sensorManager.unregisterListener(MediaController.this, linearSensor);
                }
                if (gravitySensor != null) {
                    sensorManager.unregisterListener(MediaController.this, gravitySensor);
                }
                if (accelerometerSensor != null) {
                    sensorManager.unregisterListener(MediaController.this, accelerometerSensor);
                }
                sensorManager.unregisterListener(MediaController.this, proximitySensor);
            }
        });
        if (proximityHasDifferentValues && proximityWakeLock != null && proximityWakeLock.isHeld()) {
            proximityWakeLock.release();
        }
    }

    public void cleanupPlayer(boolean notify, boolean stopService) {
        cleanupPlayer(notify, stopService, false);
    }

    public void cleanupPlayer(boolean notify, boolean stopService, boolean byVoiceEnd) {
        if (audioPlayer != null) {
            try {
                audioPlayer.reset();
            } catch (Exception e) {
                FileLog.e(e);
            }
            try {
                audioPlayer.stop();
            } catch (Exception e) {
                FileLog.e(e);
            }
            try {
                audioPlayer.release();
            } catch (Exception e) {
                FileLog.e(e);
            }
            audioPlayer = null;
        } else if (audioTrackPlayer != null) {
            synchronized (playerObjectSync) {
                try {
                    audioTrackPlayer.pause();
                    audioTrackPlayer.flush();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                try {
                    audioTrackPlayer.release();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                audioTrackPlayer = null;
            }
        } else if (videoPlayer != null) {
            currentAspectRatioFrameLayout = null;
            currentTextureViewContainer = null;
            currentAspectRatioFrameLayoutReady = false;
            currentTextureView = null;
            videoPlayer.releasePlayer();
            videoPlayer = null;
            try {
                baseActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        stopProgressTimer();
        lastProgress = 0;
        buffersWrited = 0;
        isPaused = false;
        if (playingMessageObject != null) {
            if (downloadingCurrentMessage) {
                FileLoader.getInstance().cancelLoadFile(playingMessageObject.getDocument());
            }
            MessageObject lastFile = playingMessageObject;
            playingMessageObject.audioProgress = 0.0f;
            playingMessageObject.audioProgressSec = 0;
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.messagePlayingProgressDidChanged, playingMessageObject.getId(), 0);
            playingMessageObject = null;
            downloadingCurrentMessage = false;
            if (notify) {
                NotificationsController.getInstance().audioManager.abandonAudioFocus(this);
                hasAudioFocus = 0;
                if (voiceMessagesPlaylist != null) {
                    if (byVoiceEnd && voiceMessagesPlaylist.get(0) == lastFile) {
                        voiceMessagesPlaylist.remove(0);
                        voiceMessagesPlaylistMap.remove(lastFile.getId());
                        if (voiceMessagesPlaylist.isEmpty()) {
                            voiceMessagesPlaylist = null;
                            voiceMessagesPlaylistMap = null;
                        }
                    } else {
                        voiceMessagesPlaylist = null;
                        voiceMessagesPlaylistMap = null;
                    }
                }
                boolean next = false;
                if (voiceMessagesPlaylist != null) {
                    MessageObject nextVoiceMessage = voiceMessagesPlaylist.get(0);
                    playMessage(nextVoiceMessage);
                    if (!nextVoiceMessage.isRoundVideo() && pipRoundVideoView != null) {
                        pipRoundVideoView.close(true);
                        pipRoundVideoView = null;
                    }
                } else {
                    if ((lastFile.isVoice() || lastFile.isRoundVideo()) && lastFile.getId() != 0) {
                        startRecordingIfFromSpeaker();
                    }
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.messagePlayingDidReset, lastFile.getId(), stopService);
                    pipSwitchingState = 0;
                    if (pipRoundVideoView != null) {
                        pipRoundVideoView.close(true);
                        pipRoundVideoView = null;
                    }
                }
            }
            if (stopService) {
                Intent intent = new Intent(ApplicationLoader.applicationContext, MusicPlayerService.class);
                ApplicationLoader.applicationContext.stopService(intent);
            }
        }
        if (!useFrontSpeaker && !raiseToSpeak) {
            ChatActivity chat = raiseChat;
            stopRaiseToEarSensors(raiseChat);
            raiseChat = chat;
        }
    }

    private void seekOpusPlayer(final float progress) {
        if (progress == 1.0f) {
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
                AndroidUtilities.runOnUIThread(new Runnable() {
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
        if (audioTrackPlayer == null && audioPlayer == null && videoPlayer == null || messageObject == null || playingMessageObject == null || playingMessageObject != null && playingMessageObject.getId() != messageObject.getId()) {
            return false;
        }
        try {
            if (audioPlayer != null) {
                int seekTo = (int) (audioPlayer.getDuration() * progress);
                audioPlayer.seekTo(seekTo);
                lastProgress = seekTo;
            } else if (audioTrackPlayer != null) {
                seekOpusPlayer(progress);
            } else if (videoPlayer != null) {
                videoPlayer.seekTo((long) (videoPlayer.getDuration() * progress));
            }
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
        return true;
    }

    public MessageObject getPlayingMessageObject() {
        return playingMessageObject;
    }

    public int getPlayingMessageObjectNum() {
        return currentPlaylistNum;
    }

    private void buildShuffledPlayList() {
        if (playlist.isEmpty()) {
            return;
        }
        ArrayList<MessageObject> all = new ArrayList<>(playlist);
        shuffledPlaylist.clear();

        MessageObject messageObject = playlist.get(currentPlaylistNum);
        all.remove(currentPlaylistNum);
        shuffledPlaylist.add(messageObject);

        int count = all.size();
        for (int a = 0; a < count; a++) {
            int index = Utilities.random.nextInt(all.size());
            shuffledPlaylist.add(all.get(index));
            all.remove(index);
        }
    }

    public boolean setPlaylist(ArrayList<MessageObject> messageObjects, MessageObject current) {
        return setPlaylist(messageObjects, current, true);
    }

    public boolean setPlaylist(ArrayList<MessageObject> messageObjects, MessageObject current, boolean loadMusic) {
        if (playingMessageObject == current) {
            return playMessage(current);
        }
        forceLoopCurrentPlaylist = !loadMusic;
        playMusicAgain = !playlist.isEmpty();
        playlist.clear();
        for (int a = messageObjects.size() - 1; a >= 0; a--) {
            MessageObject messageObject = messageObjects.get(a);
            if (messageObject.isMusic()) {
                playlist.add(messageObject);
            }
        }
        currentPlaylistNum = playlist.indexOf(current);
        if (currentPlaylistNum == -1) {
            playlist.clear();
            shuffledPlaylist.clear();
            currentPlaylistNum = playlist.size();
            playlist.add(current);
        }
        if (current.isMusic()) {
            if (shuffleMusic) {
                buildShuffledPlayList();
                currentPlaylistNum = 0;
            }
            if (loadMusic) {
                SharedMediaQuery.loadMusic(current.getDialogId(), playlist.get(0).getIdWithChannel());
            }
        }
        return playMessage(current);
    }

    public void playNextMessage() {
        playNextMessageWithoutOrder(false);
    }

    public boolean findMessageInPlaylistAndPlay(MessageObject messageObject) {
        int index = playlist.indexOf(messageObject);
        if (index == -1) {
            return playMessage(messageObject);
        } else {
            playMessageAtIndex(index);
        }
        return true;
    }

    public void playMessageAtIndex(int index) {
        if (currentPlaylistNum < 0 || currentPlaylistNum >= playlist.size()) {
            return;
        }
        currentPlaylistNum = index;
        playMusicAgain = true;
        playMessage(playlist.get(currentPlaylistNum));
    }

    private void playNextMessageWithoutOrder(boolean byStop) {
        ArrayList<MessageObject> currentPlayList = shuffleMusic ? shuffledPlaylist : playlist;

        if (byStop && repeatMode == 2 && !forceLoopCurrentPlaylist) {
            cleanupPlayer(false, false);
            playMessage(currentPlayList.get(currentPlaylistNum));
            return;
        }

        boolean last = false;
        if (playOrderReversed) {
            currentPlaylistNum++;
            if (currentPlaylistNum >= currentPlayList.size()) {
                currentPlaylistNum = 0;
                last = true;
            }
        } else {
            currentPlaylistNum--;
            if (currentPlaylistNum < 0) {
                currentPlaylistNum = currentPlayList.size() - 1;
                last = true;
            }
        }
        if (last && byStop && repeatMode == 0 && !forceLoopCurrentPlaylist) {
            if (audioPlayer != null || audioTrackPlayer != null || videoPlayer != null) {
                if (audioPlayer != null) {
                    try {
                        audioPlayer.reset();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    try {
                        audioPlayer.stop();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    try {
                        audioPlayer.release();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    audioPlayer = null;
                } else if (audioTrackPlayer != null) {
                    synchronized (playerObjectSync) {
                        try {
                            audioTrackPlayer.pause();
                            audioTrackPlayer.flush();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        try {
                            audioTrackPlayer.release();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        audioTrackPlayer = null;
                    }
                } else if (videoPlayer != null) {
                    currentAspectRatioFrameLayout = null;
                    currentTextureViewContainer = null;
                    currentAspectRatioFrameLayoutReady = false;
                    currentTextureView = null;
                    videoPlayer.releasePlayer();
                    videoPlayer = null;
                    try {
                        baseActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                stopProgressTimer();
                lastProgress = 0;
                buffersWrited = 0;
                isPaused = true;
                playingMessageObject.audioProgress = 0.0f;
                playingMessageObject.audioProgressSec = 0;
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.messagePlayingProgressDidChanged, playingMessageObject.getId(), 0);
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.messagePlayingPlayStateChanged, playingMessageObject.getId());
            }
            return;
        }
        if (currentPlaylistNum < 0 || currentPlaylistNum >= currentPlayList.size()) {
            return;
        }
        playMusicAgain = true;
        playMessage(currentPlayList.get(currentPlaylistNum));
    }

    public void playPreviousMessage() {
        ArrayList<MessageObject> currentPlayList = shuffleMusic ? shuffledPlaylist : playlist;
        if (currentPlayList.isEmpty()) {
            return;
        }
        MessageObject currentSong = currentPlayList.get(currentPlaylistNum);
        if (currentSong.audioProgressSec > 10) {
            MediaController.getInstance().seekToProgress(currentSong, 0);
            return;
        }

        if (playOrderReversed) {
            currentPlaylistNum--;
            if (currentPlaylistNum < 0) {
                currentPlaylistNum = currentPlayList.size() - 1;
            }
        } else {
            currentPlaylistNum++;
            if (currentPlaylistNum >= currentPlayList.size()) {
                currentPlaylistNum = 0;
            }
        }
        if (currentPlaylistNum < 0 || currentPlaylistNum >= currentPlayList.size()) {
            return;
        }
        playMusicAgain = true;
        playMessage(currentPlayList.get(currentPlaylistNum));
    }

    private void checkIsNextVoiceFileDownloaded() {
        if (voiceMessagesPlaylist == null || voiceMessagesPlaylist.size() < 2) {
            return;
        }
        MessageObject nextAudio = voiceMessagesPlaylist.get(1);
        File file = null;
        if (nextAudio.messageOwner.attachPath != null && nextAudio.messageOwner.attachPath.length() > 0) {
            file = new File(nextAudio.messageOwner.attachPath);
            if (!file.exists()) {
                file = null;
            }
        }
        final File cacheFile = file != null ? file : FileLoader.getPathToMessage(nextAudio.messageOwner);
        boolean exist = cacheFile != null && cacheFile.exists();
        if (cacheFile != null && cacheFile != file && !cacheFile.exists()) {
            FileLoader.getInstance().loadFile(nextAudio.getDocument(), false, 0);
        }
    }

    private void checkIsNextMusicFileDownloaded() {
        if ((getCurrentDownloadMask() & AUTODOWNLOAD_MASK_MUSIC) == 0) {
            return;
        }
        ArrayList<MessageObject> currentPlayList = shuffleMusic ? shuffledPlaylist : playlist;
        if (currentPlayList == null || currentPlayList.size() < 2) {
            return;
        }
        int nextIndex;
        if (playOrderReversed) {
            nextIndex = currentPlaylistNum + 1;
            if (nextIndex >= currentPlayList.size()) {
                nextIndex = 0;
            }
        } else {
            nextIndex = currentPlaylistNum - 1;
            if (nextIndex < 0) {
                nextIndex = currentPlayList.size() - 1;
            }
        }

        MessageObject nextAudio = currentPlayList.get(nextIndex);
        if (!canDownloadMedia(nextAudio)) {
            return;
        }
        File file = null;
        if (!TextUtils.isEmpty(nextAudio.messageOwner.attachPath)) {
            file = new File(nextAudio.messageOwner.attachPath);
            if (!file.exists()) {
                file = null;
            }
        }
        final File cacheFile = file != null ? file : FileLoader.getPathToMessage(nextAudio.messageOwner);
        boolean exist = cacheFile != null && cacheFile.exists();
        if (cacheFile != null && cacheFile != file && !cacheFile.exists() && nextAudio.isMusic()) {
            FileLoader.getInstance().loadFile(nextAudio.getDocument(), false, 0);
        }
    }

    public void setVoiceMessagesPlaylist(ArrayList<MessageObject> playlist, boolean unread) {
        voiceMessagesPlaylist = playlist;
        if (voiceMessagesPlaylist != null) {
            voiceMessagesPlaylistUnread = unread;
            voiceMessagesPlaylistMap = new HashMap<>();
            for (int a = 0; a < voiceMessagesPlaylist.size(); a++) {
                MessageObject messageObject = voiceMessagesPlaylist.get(a);
                voiceMessagesPlaylistMap.put(messageObject.getId(), messageObject);
            }
        }
    }

    private void checkAudioFocus(MessageObject messageObject) {
        int neededAudioFocus;
        if (messageObject.isVoice() || messageObject.isRoundVideo()) {
            if (useFrontSpeaker) {
                neededAudioFocus = 3;
            } else {
                neededAudioFocus = 2;
            }
        } else {
            neededAudioFocus = 1;
        }
        if (hasAudioFocus != neededAudioFocus) {
            hasAudioFocus = neededAudioFocus;
            int result;
            if (neededAudioFocus == 3) {
                result = NotificationsController.getInstance().audioManager.requestAudioFocus(this, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);
            } else {
                result = NotificationsController.getInstance().audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, neededAudioFocus == 2 ? AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK : AudioManager.AUDIOFOCUS_GAIN);
            }
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                audioFocus = AUDIO_FOCUSED;
            }
        }
    }

    public void setCurrentRoundVisible(boolean visible) {
        if (currentAspectRatioFrameLayout == null) {
            return;
        }
        if (visible) {
            if (pipRoundVideoView != null) {
                pipSwitchingState = 2;
                pipRoundVideoView.close(true);
                pipRoundVideoView = null;
            } else if (currentAspectRatioFrameLayout != null) {
                if (currentAspectRatioFrameLayout.getParent() == null) {
                    currentTextureViewContainer.addView(currentAspectRatioFrameLayout);
                }
                videoPlayer.setTextureView(currentTextureView);
            }
        } else {
            if (currentAspectRatioFrameLayout.getParent() != null) {
                pipSwitchingState = 1;
                currentTextureViewContainer.removeView(currentAspectRatioFrameLayout);
            } else {
                if (pipRoundVideoView == null) {
                    try {
                        pipRoundVideoView = new PipRoundVideoView();
                        pipRoundVideoView.show(baseActivity, new Runnable() {
                            @Override
                            public void run() {
                                cleanupPlayer(true, true);
                            }
                        });
                    } catch (Exception e) {
                        pipRoundVideoView = null;
                    }
                }
                if (pipRoundVideoView != null) {
                    videoPlayer.setTextureView(pipRoundVideoView.getTextureView());
                }
            }
        }
    }

    public void setTextureView(TextureView textureView, AspectRatioFrameLayout aspectRatioFrameLayout, FrameLayout container, boolean set) {
        if (!set && currentTextureView == textureView) {
            pipSwitchingState = 1;
            currentTextureView = null;
            currentAspectRatioFrameLayout = null;
            currentTextureViewContainer = null;
            return;
        }
        if (videoPlayer == null || textureView == currentTextureView) {
            return;
        }
        isDrawingWasReady = aspectRatioFrameLayout != null && aspectRatioFrameLayout.isDrawingReady();
        currentTextureView = textureView;
        if (pipRoundVideoView != null) {
            videoPlayer.setTextureView(pipRoundVideoView.getTextureView());
        } else {
            videoPlayer.setTextureView(currentTextureView);
        }
        currentAspectRatioFrameLayout = aspectRatioFrameLayout;
        currentTextureViewContainer = container;
        if (currentAspectRatioFrameLayoutReady && currentAspectRatioFrameLayout != null) {
            if (currentAspectRatioFrameLayout != null) {
                currentAspectRatioFrameLayout.setAspectRatio(currentAspectRatioFrameLayoutRatio, currentAspectRatioFrameLayoutRotation);
            }
            if (currentTextureViewContainer.getVisibility() != View.VISIBLE) {
                currentTextureViewContainer.setVisibility(View.VISIBLE);
            }
        }
    }

    public void setBaseActivity(Activity activity, boolean set) {
        if (set) {
            baseActivity = activity;
        } else if (baseActivity == activity) {
            baseActivity = null;
        }
    }

    public boolean playMessage(final MessageObject messageObject) {
        if (messageObject == null) {
            return false;
        }
        if ((audioTrackPlayer != null || audioPlayer != null || videoPlayer != null) && playingMessageObject != null && messageObject.getId() == playingMessageObject.getId()) {
            if (isPaused) {
                resumeAudio(messageObject);
            }
            if (!raiseToSpeak) {
                startRaiseToEarSensors(raiseChat);
            }
            return true;
        }
        if (!messageObject.isOut() && messageObject.isContentUnread()) {
            MessagesController.getInstance().markMessageContentAsRead(messageObject);
        }
        boolean notify = !playMusicAgain;
        if (playingMessageObject != null) {
            notify = false;
        }
        cleanupPlayer(notify, false);
        playMusicAgain = false;
        File file = null;
        if (messageObject.messageOwner.attachPath != null && messageObject.messageOwner.attachPath.length() > 0) {
            file = new File(messageObject.messageOwner.attachPath);
            if (!file.exists()) {
                file = null;
            }
        }
        final File cacheFile = file != null ? file : FileLoader.getPathToMessage(messageObject.messageOwner);
        if (cacheFile != null && cacheFile != file && !cacheFile.exists()) {
            FileLoader.getInstance().loadFile(messageObject.getDocument(), false, 0);
            downloadingCurrentMessage = true;
            isPaused = false;
            lastProgress = 0;
            lastPlayPcm = 0;
            audioInfo = null;
            playingMessageObject = messageObject;
            if (playingMessageObject.getDocument() != null) {
                Intent intent = new Intent(ApplicationLoader.applicationContext, MusicPlayerService.class);
                ApplicationLoader.applicationContext.startService(intent);
            } else {
                Intent intent = new Intent(ApplicationLoader.applicationContext, MusicPlayerService.class);
                ApplicationLoader.applicationContext.stopService(intent);
            }
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.messagePlayingPlayStateChanged, playingMessageObject.getId());
            return true;
        } else {
            downloadingCurrentMessage = false;
        }
        if (messageObject.isMusic()) {
            checkIsNextMusicFileDownloaded();
        } else {
            checkIsNextVoiceFileDownloaded();
        }

        if (currentAspectRatioFrameLayout != null) {
            isDrawingWasReady = false;
            currentAspectRatioFrameLayout.setDrawingReady(false);
        }
        if (messageObject.isRoundVideo()) {
            playlist.clear();
            shuffledPlaylist.clear();
            videoPlayer = new VideoPlayer();
            videoPlayer.setDelegate(new VideoPlayer.VideoPlayerDelegate() {
                @Override
                public void onStateChanged(boolean playWhenReady, int playbackState) {
                    if (videoPlayer == null) {
                        return;
                    }
                    if (playbackState != ExoPlayer.STATE_ENDED && playbackState != ExoPlayer.STATE_IDLE) {
                        try {
                            baseActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    } else {
                        try {
                            baseActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                    if (playbackState == ExoPlayer.STATE_READY) {
                        currentAspectRatioFrameLayoutReady = true;
                        if (currentTextureViewContainer != null && currentTextureViewContainer.getVisibility() != View.VISIBLE) {
                            currentTextureViewContainer.setVisibility(View.VISIBLE);
                        }
                    } else if (videoPlayer.isPlaying() && playbackState == ExoPlayer.STATE_ENDED) {
                        cleanupPlayer(true, true, true);
                    }
                }

                @Override
                public void onError(Exception e) {
                    FileLog.e(e);
                }

                @Override
                public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
                    currentAspectRatioFrameLayoutRotation = unappliedRotationDegrees;
                    if (unappliedRotationDegrees == 90 || unappliedRotationDegrees == 270) {
                        int temp = width;
                        width = height;
                        height = temp;
                    }
                    currentAspectRatioFrameLayoutRatio = height == 0 ? 1 : (width * pixelWidthHeightRatio) / height;

                    if (currentAspectRatioFrameLayout != null) {
                        currentAspectRatioFrameLayout.setAspectRatio(currentAspectRatioFrameLayoutRatio, currentAspectRatioFrameLayoutRotation);
                    }
                }

                @Override
                public void onRenderedFirstFrame() {
                    if (currentAspectRatioFrameLayout != null && !currentAspectRatioFrameLayout.isDrawingReady()) {
                        isDrawingWasReady = true;
                        currentAspectRatioFrameLayout.setDrawingReady(true);
                        if (currentTextureViewContainer != null && currentTextureViewContainer.getVisibility() != View.VISIBLE) {
                            currentTextureViewContainer.setVisibility(View.VISIBLE);
                        }
                    }
                }

                @Override
                public boolean onSurfaceDestroyed(SurfaceTexture surfaceTexture) {
                    if (videoPlayer == null) {
                        return false;
                    }
                    if (pipSwitchingState == 2) {
                        if (currentAspectRatioFrameLayout != null) {
                            if (isDrawingWasReady) {
                                currentAspectRatioFrameLayout.setDrawingReady(true);
                            }
                            if (currentAspectRatioFrameLayout.getParent() == null) {
                                currentTextureViewContainer.addView(currentAspectRatioFrameLayout);
                            }
                            if (currentTextureView.getSurfaceTexture() != surfaceTexture) {
                                currentTextureView.setSurfaceTexture(surfaceTexture);
                            }
                            videoPlayer.setTextureView(currentTextureView);
                        }
                        pipSwitchingState = 0;
                        return true;
                    } else if (pipSwitchingState == 1) {
                        if (baseActivity != null) {
                            if (pipRoundVideoView == null) {
                                try {
                                    pipRoundVideoView = new PipRoundVideoView();
                                    pipRoundVideoView.show(baseActivity, new Runnable() {
                                        @Override
                                        public void run() {
                                            cleanupPlayer(true, true);
                                        }
                                    });
                                } catch (Exception e) {
                                    pipRoundVideoView = null;
                                }
                            }
                            if (pipRoundVideoView != null) {
                                if (pipRoundVideoView.getTextureView().getSurfaceTexture() != surfaceTexture) {
                                    pipRoundVideoView.getTextureView().setSurfaceTexture(surfaceTexture);
                                }
                                videoPlayer.setTextureView(pipRoundVideoView.getTextureView());
                            }
                        }
                        pipSwitchingState = 0;
                        return true;
                    }
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

                }
            });
            currentAspectRatioFrameLayoutReady = false;
            if (pipRoundVideoView != null || !MessagesController.getInstance().isDialogCreated(messageObject.getDialogId())) {
                if (pipRoundVideoView == null) {
                    try {
                        pipRoundVideoView = new PipRoundVideoView();
                        pipRoundVideoView.show(baseActivity, new Runnable() {
                            @Override
                            public void run() {
                                cleanupPlayer(true, true);
                            }
                        });
                    } catch (Exception e) {
                        pipRoundVideoView = null;
                    }
                }
                if (pipRoundVideoView != null) {
                    videoPlayer.setTextureView(pipRoundVideoView.getTextureView());
                }
            } else if (currentTextureView != null) {
                videoPlayer.setTextureView(currentTextureView);
            }
            videoPlayer.preparePlayer(Uri.fromFile(cacheFile), "other");
            videoPlayer.setStreamType(useFrontSpeaker ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);
            videoPlayer.play();
        } else if (isOpusFile(cacheFile.getAbsolutePath()) == 1) {
            playlist.clear();
            shuffledPlaylist.clear();
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

                    audioTrackPlayer = new AudioTrack(useFrontSpeaker ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC, 48000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, playerBufferSize, AudioTrack.MODE_STREAM);
                    audioTrackPlayer.setStereoVolume(1.0f, 1.0f);
                    audioTrackPlayer.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
                        @Override
                        public void onMarkerReached(AudioTrack audioTrack) {
                            cleanupPlayer(true, true, true);
                        }

                        @Override
                        public void onPeriodicNotification(AudioTrack audioTrack) {

                        }
                    });
                    audioTrackPlayer.play();
                } catch (Exception e) {
                    FileLog.e(e);
                    if (audioTrackPlayer != null) {
                        audioTrackPlayer.release();
                        audioTrackPlayer = null;
                        isPaused = false;
                        playingMessageObject = null;
                        downloadingCurrentMessage = false;
                    }
                    return false;
                }
            }
        } else {
            try {
                audioPlayer = new MediaPlayer();
                audioPlayer.setAudioStreamType(useFrontSpeaker ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);
                audioPlayer.setDataSource(cacheFile.getAbsolutePath());
                audioPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mediaPlayer) {
                        if (!playlist.isEmpty() && playlist.size() > 1) {
                            playNextMessageWithoutOrder(true);
                        } else {
                            cleanupPlayer(true, true, messageObject != null && messageObject.isVoice());
                        }
                    }
                });
                audioPlayer.prepare();
                audioPlayer.start();
                if (messageObject.isVoice()) {
                    audioInfo = null;
                    playlist.clear();
                    shuffledPlaylist.clear();
                } else {
                    try {
                        audioInfo = AudioInfo.getAudioInfo(cacheFile);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.messagePlayingPlayStateChanged, playingMessageObject != null ? playingMessageObject.getId() : 0);
                if (audioPlayer != null) {
                    audioPlayer.release();
                    audioPlayer = null;
                    isPaused = false;
                    playingMessageObject = null;
                    downloadingCurrentMessage = false;
                }
                return false;
            }
        }
        checkAudioFocus(messageObject);
        setPlayerVolume();

        isPaused = false;
        lastProgress = 0;
        lastPlayPcm = 0;
        playingMessageObject = messageObject;
        if (!raiseToSpeak) {
            startRaiseToEarSensors(raiseChat);
        }
        startProgressTimer(playingMessageObject);
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.messagePlayingDidStarted, messageObject);

        if (videoPlayer != null) {
            try {
                if (playingMessageObject.audioProgress != 0) {
                    int seekTo = (int) (videoPlayer.getDuration() * playingMessageObject.audioProgress);
                    videoPlayer.seekTo(seekTo);
                }
            } catch (Exception e2) {
                playingMessageObject.audioProgress = 0;
                playingMessageObject.audioProgressSec = 0;
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.messagePlayingProgressDidChanged, playingMessageObject.getId(), 0);
                FileLog.e(e2);
            }
        } else if (audioPlayer != null) {
            try {
                if (playingMessageObject.audioProgress != 0) {
                    int seekTo = (int) (audioPlayer.getDuration() * playingMessageObject.audioProgress);
                    audioPlayer.seekTo(seekTo);
                }
            } catch (Exception e2) {
                playingMessageObject.audioProgress = 0;
                playingMessageObject.audioProgressSec = 0;
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.messagePlayingProgressDidChanged, playingMessageObject.getId(), 0);
                FileLog.e(e2);
            }
        } else if (audioTrackPlayer != null) {
            if (playingMessageObject.audioProgress == 1) {
                playingMessageObject.audioProgress = 0;
            }
            fileDecodingQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (playingMessageObject != null && playingMessageObject.audioProgress != 0) {
                            lastPlayPcm = (long) (currentTotalPcmDuration * playingMessageObject.audioProgress);
                            seekOpusFile(playingMessageObject.audioProgress);
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
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

        if (playingMessageObject.isMusic()) {
            Intent intent = new Intent(ApplicationLoader.applicationContext, MusicPlayerService.class);
            ApplicationLoader.applicationContext.startService(intent);
        } else {
            Intent intent = new Intent(ApplicationLoader.applicationContext, MusicPlayerService.class);
            ApplicationLoader.applicationContext.stopService(intent);
        }

        return true;
    }

    public void stopAudio() {
        if (audioTrackPlayer == null && audioPlayer == null && videoPlayer == null || playingMessageObject == null) {
            return;
        }
        try {
            if (audioPlayer != null) {
                try {
                    audioPlayer.reset();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                audioPlayer.stop();
            } else if (audioTrackPlayer != null) {
                audioTrackPlayer.pause();
                audioTrackPlayer.flush();
            } else if (videoPlayer != null) {
                videoPlayer.pause();
            }
        } catch (Exception e) {
            FileLog.e(e);
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
            } else if (videoPlayer != null) {
                currentAspectRatioFrameLayout = null;
                currentTextureViewContainer = null;
                currentAspectRatioFrameLayoutReady = false;
                currentTextureView = null;
                videoPlayer.releasePlayer();
                videoPlayer = null;
                try {
                    baseActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        stopProgressTimer();
        playingMessageObject = null;
        downloadingCurrentMessage = false;
        isPaused = false;

        Intent intent = new Intent(ApplicationLoader.applicationContext, MusicPlayerService.class);
        ApplicationLoader.applicationContext.stopService(intent);
    }

    public AudioInfo getAudioInfo() {
        return audioInfo;
    }

    public boolean isShuffleMusic() {
        return shuffleMusic;
    }

    public boolean isPlayOrderReversed() {
        return playOrderReversed;
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    public void toggleShuffleMusic(int type) {
        boolean oldShuffle = shuffleMusic;
        if (type == 2) {
            shuffleMusic = !shuffleMusic;
        } else {
            playOrderReversed = !playOrderReversed;
        }
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("shuffleMusic", shuffleMusic);
        editor.putBoolean("playOrderReversed", playOrderReversed);
        editor.commit();
        if (oldShuffle != shuffleMusic) {
            if (shuffleMusic) {
                buildShuffledPlayList();
                currentPlaylistNum = 0;
            } else {
                if (playingMessageObject != null) {
                    currentPlaylistNum = playlist.indexOf(playingMessageObject);
                    if (currentPlaylistNum == -1) {
                        playlist.clear();
                        shuffledPlaylist.clear();
                        cleanupPlayer(true, true);
                    }
                }
            }
        }
    }

    public void toggleRepeatMode() {
        repeatMode++;
        if (repeatMode > 2) {
            repeatMode = 0;
        }
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("repeatMode", repeatMode);
        editor.commit();
    }

    public boolean pauseMessage(MessageObject messageObject) {
        if (audioTrackPlayer == null && audioPlayer == null && videoPlayer == null || messageObject == null || playingMessageObject == null || playingMessageObject != null && playingMessageObject.getId() != messageObject.getId()) {
            return false;
        }
        stopProgressTimer();
        try {
            if (audioPlayer != null) {
                audioPlayer.pause();
            } else if (audioTrackPlayer != null) {
                audioTrackPlayer.pause();
            } else if (videoPlayer != null) {
                videoPlayer.pause();
            }
            isPaused = true;
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.messagePlayingPlayStateChanged, playingMessageObject.getId());
        } catch (Exception e) {
            FileLog.e(e);
            isPaused = false;
            return false;
        }
        return true;
    }

    public boolean resumeAudio(MessageObject messageObject) {
        if (audioTrackPlayer == null && audioPlayer == null && videoPlayer == null || messageObject == null || playingMessageObject == null || playingMessageObject != null && playingMessageObject.getId() != messageObject.getId()) {
            return false;
        }

        try {
            startProgressTimer(playingMessageObject);
            if (audioPlayer != null) {
                audioPlayer.start();
            } else if (audioTrackPlayer != null) {
                audioTrackPlayer.play();
                checkPlayerQueue();
            } else if (videoPlayer != null) {
                videoPlayer.play();
            }
            checkAudioFocus(messageObject);
            isPaused = false;
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.messagePlayingPlayStateChanged, playingMessageObject.getId());
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
        return true;
    }

    public boolean isRoundVideoDrawingReady() {
        return currentAspectRatioFrameLayout != null && currentAspectRatioFrameLayout.isDrawingReady();
    }

    public ArrayList<MessageObject> getPlaylist() {
        return playlist;
    }

    public boolean isPlayingMessage(MessageObject messageObject) {
        if (audioTrackPlayer == null && audioPlayer == null && videoPlayer == null || messageObject == null || playingMessageObject == null) {
            return false;
        }
        if (playingMessageObject.eventId != 0 && playingMessageObject.eventId == playingMessageObject.eventId) {
            return !downloadingCurrentMessage;
        }
        if (playingMessageObject.getDialogId() == messageObject.getDialogId() && playingMessageObject.getId() == messageObject.getId()) {
            return !downloadingCurrentMessage;
        }
        //
        return false;
    }

    public boolean isMessagePaused() {
        return isPaused || downloadingCurrentMessage;
    }

    public boolean isDownloadingCurrentMessage() {
        return downloadingCurrentMessage;
    }

    public void startRecording(final long dialog_id, final MessageObject reply_to_msg) {
        boolean paused = false;
        if (playingMessageObject != null && isPlayingMessage(playingMessageObject) && !isMessagePaused()) {
            paused = true;
            pauseMessage(playingMessageObject);
        }

        try {
            Vibrator v = (Vibrator) ApplicationLoader.applicationContext.getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(10);
            //NotificationsController.getInstance().playRecordSound();
        } catch (Exception e) {
            FileLog.e(e);
        }

        recordQueue.postRunnable(recordStartRunnable = new Runnable() {
            @Override
            public void run() {
                if (audioRecorder != null) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            recordStartRunnable = null;
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.recordStartError);
                        }
                    });
                    return;
                }

                recordingAudio = new TLRPC.TL_document();
                recordingAudio.dc_id = Integer.MIN_VALUE;
                recordingAudio.id = UserConfig.lastLocalId;
                recordingAudio.user_id = UserConfig.getClientUserId();
                recordingAudio.mime_type = "audio/ogg";
                recordingAudio.thumb = new TLRPC.TL_photoSizeEmpty();
                recordingAudio.thumb.type = "s";
                UserConfig.lastLocalId--;
                UserConfig.saveConfig(false);

                recordingAudioFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), FileLoader.getAttachFileName(recordingAudio));

                try {
                    if (startRecord(recordingAudioFile.getAbsolutePath()) == 0) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                recordStartRunnable = null;
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.recordStartError);
                            }
                        });
                        return;
                    }

                    audioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, recordBufferSize * 10);
                    recordStartTime = System.currentTimeMillis();
                    recordTimeCount = 0;
                    samplesCount = 0;
                    recordDialogId = dialog_id;
                    recordReplyingMessageObject = reply_to_msg;
                    fileBuffer.rewind();

                    audioRecorder.startRecording();
                } catch (Exception e) {
                    FileLog.e(e);
                    recordingAudio = null;
                    stopRecord();
                    recordingAudioFile.delete();
                    recordingAudioFile = null;
                    try {
                        audioRecorder.release();
                        audioRecorder = null;
                    } catch (Exception e2) {
                        FileLog.e(e2);
                    }

                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            recordStartRunnable = null;
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.recordStartError);
                        }
                    });
                    return;
                }

                recordQueue.postRunnable(recordRunnable);
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        recordStartRunnable = null;
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.recordStarted);
                    }
                });
            }
        }, paused ? 500 : 50);
    }

    public void generateWaveform(MessageObject messageObject) {
        final String id = messageObject.getId() + "_" + messageObject.getDialogId();
        final String path = FileLoader.getPathToMessage(messageObject.messageOwner).getAbsolutePath();
        if (generatingWaveform.containsKey(id)) {
            return;
        }
        generatingWaveform.put(id, messageObject);
        Utilities.globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                final byte[] waveform = MediaController.getInstance().getWaveform(path);
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        MessageObject messageObject = generatingWaveform.remove(id);
                        if (messageObject == null) {
                            return;
                        }
                        if (waveform != null) {
                            for (int a = 0; a < messageObject.getDocument().attributes.size(); a++) {
                                TLRPC.DocumentAttribute attribute = messageObject.getDocument().attributes.get(a);
                                if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                                    attribute.waveform = waveform;
                                    attribute.flags |= 4;
                                    break;
                                }
                            }
                            TLRPC.TL_messages_messages messagesRes = new TLRPC.TL_messages_messages();
                            messagesRes.messages.add(messageObject.messageOwner);
                            MessagesStorage.getInstance().putMessages(messagesRes, messageObject.getDialogId(), -1, 0, false);
                            ArrayList<MessageObject> arrayList = new ArrayList<>();
                            arrayList.add(messageObject);
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.replaceMessagesObjects, messageObject.getDialogId(), arrayList);
                        }
                    }
                });
            }
        });
    }

    private void stopRecordingInternal(final int send) {
        if (send != 0) {
            final TLRPC.TL_document audioToSend = recordingAudio;
            final File recordingAudioFileToSend = recordingAudioFile;
            fileEncodingQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    stopRecord();
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            audioToSend.date = ConnectionsManager.getInstance().getCurrentTime();
                            audioToSend.size = (int) recordingAudioFileToSend.length();
                            TLRPC.TL_documentAttributeAudio attributeAudio = new TLRPC.TL_documentAttributeAudio();
                            attributeAudio.voice = true;
                            attributeAudio.waveform = getWaveform2(recordSamples, recordSamples.length);
                            if (attributeAudio.waveform != null) {
                                attributeAudio.flags |= 4;
                            }
                            long duration = recordTimeCount;
                            attributeAudio.duration = (int) (recordTimeCount / 1000);
                            audioToSend.attributes.add(attributeAudio);
                            if (duration > 700) {
                                if (send == 1) {
                                    SendMessagesHelper.getInstance().sendMessage(audioToSend, null, recordingAudioFileToSend.getAbsolutePath(), recordDialogId, recordReplyingMessageObject, null, null, 0);
                                }
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.audioDidSent, send == 2 ? audioToSend : null, send == 2 ? recordingAudioFileToSend.getAbsolutePath() : null);
                            } else {
                                recordingAudioFileToSend.delete();
                            }
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
            FileLog.e(e);
        }
        recordingAudio = null;
        recordingAudioFile = null;
    }

    public void stopRecording(final int send) {
        if (recordStartRunnable != null) {
            recordQueue.cancelRunnable(recordStartRunnable);
            recordStartRunnable = null;
        }
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
                    FileLog.e(e);
                    if (recordingAudioFile != null) {
                        recordingAudioFile.delete();
                    }
                }
                if (send == 0) {
                    stopRecordingInternal(0);
                }
                try {
                    Vibrator v = (Vibrator) ApplicationLoader.applicationContext.getSystemService(Context.VIBRATOR_SERVICE);
                    v.vibrate(10);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.recordStopped, send == 2 ? 1 : 0);
                    }
                });
            }
        });
    }

    public static void saveFile(String fullPath, Context context, final int type, final String name, final String mime) {
        if (fullPath == null) {
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
            return;
        }

        final File sourceFile = file;
        final boolean[] cancelled = new boolean[] {false};
        if (sourceFile.exists()) {
            AlertDialog progressDialog = null;
            if (context != null && type != 0) {
                try {
                    progressDialog = new AlertDialog(context, 2);
                    progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
                    progressDialog.setCanceledOnTouchOutside(false);
                    progressDialog.setCancelable(true);
                    progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            cancelled[0] = true;
                        }
                    });
                    progressDialog.show();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            final AlertDialog finalProgress = progressDialog;

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        File destFile;
                        if (type == 0) {
                            destFile = AndroidUtilities.generatePicturePath();
                        } else if (type == 1) {
                            destFile = AndroidUtilities.generateVideoPath();
                        } else {
                            File dir;
                            if (type == 2) {
                                dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                            } else {
                                dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                            }
                            dir.mkdir();
                            destFile = new File(dir, name);
                            if (destFile.exists()) {
                                int idx = name.lastIndexOf('.');
                                for (int a = 0; a < 10; a++) {
                                    String newName;
                                    if (idx != -1) {
                                        newName = name.substring(0, idx) + "(" + (a + 1) + ")" + name.substring(idx);
                                    } else {
                                        newName = name + "(" + (a + 1) + ")";
                                    }
                                    destFile = new File(dir, newName);
                                    if (!destFile.exists()) {
                                        break;
                                    }
                                }
                            }
                        }
                        if (!destFile.exists()) {
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
                            for (long a = 0; a < size; a += 4096) {
                                if (cancelled[0]) {
                                    break;
                                }
                                destination.transferFrom(source, a, Math.min(4096, size - a));
                                if (finalProgress != null) {
                                    if (lastProgress <= System.currentTimeMillis() - 500) {
                                        lastProgress = System.currentTimeMillis();
                                        final int progress = (int) ((float) a / (float) size * 100);
                                        AndroidUtilities.runOnUIThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    finalProgress.setProgress(progress);
                                                } catch (Exception e) {
                                                    FileLog.e(e);
                                                }
                                            }
                                        });
                                    }
                                }
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                            result = false;
                        } finally {
                            try {
                                if (source != null) {
                                    source.close();
                                }
                            } catch (Exception e) {
                                //
                            }
                            try {
                                if (destination != null) {
                                    destination.close();
                                }
                            } catch (Exception e) {
                                //
                            }
                        }
                        if (cancelled[0]) {
                            destFile.delete();
                            result = false;
                        }

                        if (result) {
                            if (type == 2) {
                                DownloadManager downloadManager = (DownloadManager) ApplicationLoader.applicationContext.getSystemService(Context.DOWNLOAD_SERVICE);
                                downloadManager.addCompletedDownload(destFile.getName(), destFile.getName(), false, mime, destFile.getAbsolutePath(), destFile.length(), true);
                            } else {
                                AndroidUtilities.addMediaToGallery(Uri.fromFile(destFile));
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (finalProgress != null) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    finalProgress.dismiss();
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            }
                        });
                    }
                }
            }).start();
        }
    }

    public static boolean isWebp(Uri uri) {
        InputStream inputStream = null;
        try {
            inputStream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
            byte[] header = new byte[12];
            if (inputStream.read(header, 0, 12) == 12) {
                String str = new String(header);
                if (str != null) {
                    str = str.toLowerCase();
                    if (str.startsWith("riff") && str.endsWith("webp")) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception e2) {
                FileLog.e(e2);
            }
        }
        return false;
    }

    public static boolean isGif(Uri uri) {
        InputStream inputStream = null;
        try {
            inputStream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
            byte[] header = new byte[3];
            if (inputStream.read(header, 0, 3) == 3) {
                String str = new String(header);
                if (str != null && str.equalsIgnoreCase("gif")) {
                    return true;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception e2) {
                FileLog.e(e2);
            }
        }
        return false;
    }

    public static String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = null;
            try {
                cursor = ApplicationLoader.applicationContext.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
                if (cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    public static String copyFileToCache(Uri uri, String ext) {
        InputStream inputStream = null;
        FileOutputStream output = null;
        try {
            String name = getFileName(uri);
            if (name == null) {
                int id = UserConfig.lastLocalId;
                UserConfig.lastLocalId--;
                UserConfig.saveConfig(false);
                name = String.format(Locale.US, "%d.%s", id, ext);
            }
            inputStream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
            File f = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), "sharing/");
            f.mkdirs();
            f = new File(f, name);
            output = new FileOutputStream(f);
            byte[] buffer = new byte[1024 * 20];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                output.write(buffer, 0, len);
            }
            return f.getAbsolutePath();
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception e2) {
                FileLog.e(e2);
            }
            try {
                if (output != null) {
                    output.close();
                }
            } catch (Exception e2) {
                FileLog.e(e2);
            }
        }
        return null;
    }

    public void toggleSaveToGallery() {
        saveToGallery = !saveToGallery;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("save_gallery", saveToGallery);
        editor.commit();
        checkSaveToGalleryFiles();
    }

    public void toggleAutoplayGifs() {
        autoplayGifs = !autoplayGifs;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("autoplay_gif", autoplayGifs);
        editor.commit();
    }

    public void toogleRaiseToSpeak() {
        raiseToSpeak = !raiseToSpeak;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("raise_to_speak", raiseToSpeak);
        editor.commit();
    }

    public void toggleCustomTabs() {
        customTabs = !customTabs;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("custom_tabs", customTabs);
        editor.commit();
    }

    public void toggleDirectShare() {
        directShare = !directShare;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("direct_share", directShare);
        editor.commit();
    }

    public void toggleInappCamera() {
        inappCamera = !inappCamera;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("direct_share", inappCamera);
        editor.commit();
    }

    public void toggleRoundCamera16to9() {
        roundCamera16to9 = !roundCamera16to9;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("roundCamera16to9", roundCamera16to9);
        editor.commit();
    }

    public void toggleGroupPhotosEnabled() {
        groupPhotosEnabled = !groupPhotosEnabled;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("groupPhotosEnabled", groupPhotosEnabled);
        editor.commit();
    }

    public void checkSaveToGalleryFiles() {
        try {
            File telegramPath = new File(Environment.getExternalStorageDirectory(), "Telegram");
            File imagePath = new File(telegramPath, "Telegram Images");
            imagePath.mkdir();
            File videoPath = new File(telegramPath, "Telegram Video");
            videoPath.mkdir();

            if (saveToGallery) {
                if (imagePath.isDirectory()) {
                    new File(imagePath, ".nomedia").delete();
                }
                if (videoPath.isDirectory()) {
                    new File(videoPath, ".nomedia").delete();
                }
            } else {
                if (imagePath.isDirectory()) {
                    new File(imagePath, ".nomedia").createNewFile();
                }
                if (videoPath.isDirectory()) {
                    new File(videoPath, ".nomedia").createNewFile();
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public boolean canSaveToGallery() {
        return saveToGallery;
    }

    public boolean canAutoplayGifs() {
        return autoplayGifs;
    }

    public boolean canRaiseToSpeak() {
        return raiseToSpeak;
    }

    public boolean canCustomTabs() {
        return customTabs;
    }

    public boolean canDirectShare() {
        return directShare;
    }

    public boolean canInAppCamera() {
        return inappCamera;
    }

    public boolean canRoundCamera16to9() {
        return roundCamera16to9;
    }

    public boolean isGroupPhotosEnabled() {
        return groupPhotosEnabled;
    }

    public static void loadGalleryPhotosAlbums(final int guid) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<AlbumEntry> mediaAlbumsSorted = new ArrayList<>();
                final ArrayList<AlbumEntry> photoAlbumsSorted = new ArrayList<>();
                HashMap<Integer, AlbumEntry> mediaAlbums = new HashMap<>();
                HashMap<Integer, AlbumEntry> photoAlbums = new HashMap<>();
                AlbumEntry allPhotosAlbum = null;
                AlbumEntry allMediaAlbum = null;
                String cameraFolder = null;
                try {
                    cameraFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath() + "/" + "Camera/";
                } catch (Exception e) {
                    FileLog.e(e);
                }
                Integer mediaCameraAlbumId = null;
                Integer photoCameraAlbumId = null;

                Cursor cursor = null;
                try {
                    if (Build.VERSION.SDK_INT < 23 || Build.VERSION.SDK_INT >= 23 && ApplicationLoader.applicationContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                        cursor = MediaStore.Images.Media.query(ApplicationLoader.applicationContext.getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projectionPhotos, null, null, MediaStore.Images.Media.DATE_TAKEN + " DESC");
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

                                PhotoEntry photoEntry = new PhotoEntry(bucketId, imageId, dateTaken, path, orientation, false);

                                if (allPhotosAlbum == null) {
                                    allPhotosAlbum = new AlbumEntry(0, LocaleController.getString("AllPhotos", R.string.AllPhotos), photoEntry);
                                    photoAlbumsSorted.add(0, allPhotosAlbum);
                                }
                                if (allMediaAlbum == null) {
                                    allMediaAlbum = new AlbumEntry(0, LocaleController.getString("AllMedia", R.string.AllMedia), photoEntry);
                                    mediaAlbumsSorted.add(0, allMediaAlbum);
                                }
                                allPhotosAlbum.addPhoto(photoEntry);
                                allMediaAlbum.addPhoto(photoEntry);

                                AlbumEntry albumEntry = mediaAlbums.get(bucketId);
                                if (albumEntry == null) {
                                    albumEntry = new AlbumEntry(bucketId, bucketName, photoEntry);
                                    mediaAlbums.put(bucketId, albumEntry);
                                    if (mediaCameraAlbumId == null && cameraFolder != null && path != null && path.startsWith(cameraFolder)) {
                                        mediaAlbumsSorted.add(0, albumEntry);
                                        mediaCameraAlbumId = bucketId;
                                    } else {
                                        mediaAlbumsSorted.add(albumEntry);
                                    }
                                }
                                albumEntry.addPhoto(photoEntry);

                                albumEntry = photoAlbums.get(bucketId);
                                if (albumEntry == null) {
                                    albumEntry = new AlbumEntry(bucketId, bucketName, photoEntry);
                                    photoAlbums.put(bucketId, albumEntry);
                                    if (photoCameraAlbumId == null && cameraFolder != null && path != null && path.startsWith(cameraFolder)) {
                                        photoAlbumsSorted.add(0, albumEntry);
                                        photoCameraAlbumId = bucketId;
                                    } else {
                                        photoAlbumsSorted.add(albumEntry);
                                    }
                                }
                                albumEntry.addPhoto(photoEntry);
                            }
                        }
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                } finally {
                    if (cursor != null) {
                        try {
                            cursor.close();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }

                try {
                    if (Build.VERSION.SDK_INT < 23 || Build.VERSION.SDK_INT >= 23 && ApplicationLoader.applicationContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                        cursor = MediaStore.Images.Media.query(ApplicationLoader.applicationContext.getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projectionVideo, null, null, MediaStore.Video.Media.DATE_TAKEN + " DESC");
                        if (cursor != null) {
                            int imageIdColumn = cursor.getColumnIndex(MediaStore.Video.Media._ID);
                            int bucketIdColumn = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID);
                            int bucketNameColumn = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME);
                            int dataColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATA);
                            int dateColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_TAKEN);
                            int durationColumn = cursor.getColumnIndex(MediaStore.Video.Media.DURATION);

                            while (cursor.moveToNext()) {
                                int imageId = cursor.getInt(imageIdColumn);
                                int bucketId = cursor.getInt(bucketIdColumn);
                                String bucketName = cursor.getString(bucketNameColumn);
                                String path = cursor.getString(dataColumn);
                                long dateTaken = cursor.getLong(dateColumn);
                                long duration = cursor.getLong(durationColumn);


                                if (path == null || path.length() == 0) {
                                    continue;
                                }

                                PhotoEntry photoEntry = new PhotoEntry(bucketId, imageId, dateTaken, path, (int) (duration / 1000), true);

                                if (allMediaAlbum == null) {
                                    allMediaAlbum = new AlbumEntry(0, LocaleController.getString("AllMedia", R.string.AllMedia), photoEntry);
                                    mediaAlbumsSorted.add(0, allMediaAlbum);
                                }
                                allMediaAlbum.addPhoto(photoEntry);

                                AlbumEntry albumEntry = mediaAlbums.get(bucketId);
                                if (albumEntry == null) {
                                    albumEntry = new AlbumEntry(bucketId, bucketName, photoEntry);
                                    mediaAlbums.put(bucketId, albumEntry);
                                    if (mediaCameraAlbumId == null && cameraFolder != null && path != null && path.startsWith(cameraFolder)) {
                                        mediaAlbumsSorted.add(0, albumEntry);
                                        mediaCameraAlbumId = bucketId;
                                    } else {
                                        mediaAlbumsSorted.add(albumEntry);
                                    }
                                }

                                albumEntry.addPhoto(photoEntry);
                            }
                        }
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                } finally {
                    if (cursor != null) {
                        try {
                            cursor.close();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }
                for (int a = 0; a < mediaAlbumsSorted.size(); a++) {
                    Collections.sort(mediaAlbumsSorted.get(a).photos, new Comparator<PhotoEntry>() {
                        @Override
                        public int compare(PhotoEntry o1, PhotoEntry o2) {
                            if (o1.dateTaken < o2.dateTaken) {
                                return 1;
                            } else if (o1.dateTaken > o2.dateTaken) {
                                return -1;
                            }
                            return 0;
                        }
                    });
                }
                broadcastNewPhotos(guid, mediaAlbumsSorted, photoAlbumsSorted, mediaCameraAlbumId, allMediaAlbum, allPhotosAlbum, 0);
            }
        });
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private static void broadcastNewPhotos(final int guid, final ArrayList<AlbumEntry> mediaAlbumsSorted, final ArrayList<AlbumEntry> photoAlbumsSorted, final Integer cameraAlbumIdFinal, final AlbumEntry allMediaAlbumFinal, final AlbumEntry allPhotosAlbumFinal, int delay) {
        if (broadcastPhotosRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(broadcastPhotosRunnable);
        }
        AndroidUtilities.runOnUIThread(broadcastPhotosRunnable = new Runnable() {
            @Override
            public void run() {
                if (PhotoViewer.getInstance().isVisible()) {
                    broadcastNewPhotos(guid, mediaAlbumsSorted, photoAlbumsSorted, cameraAlbumIdFinal, allMediaAlbumFinal, allPhotosAlbumFinal, 1000);
                    return;
                }
                broadcastPhotosRunnable = null;
                allPhotosAlbumEntry = allPhotosAlbumFinal;
                allMediaAlbumEntry = allMediaAlbumFinal;
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.albumsDidLoaded, guid, mediaAlbumsSorted, photoAlbumsSorted, cameraAlbumIdFinal);
            }
        }, delay);
    }

    public void scheduleVideoConvert(MessageObject messageObject) {
        scheduleVideoConvert(messageObject, false);
    }

    public boolean scheduleVideoConvert(MessageObject messageObject, boolean isEmpty) {
        if (isEmpty && !videoConvertQueue.isEmpty()) {
            return false;
        } else if (isEmpty) {
            new File(messageObject.messageOwner.attachPath).delete();
        }
        videoConvertQueue.add(messageObject);
        if (videoConvertQueue.size() == 1) {
            startVideoConvertFromQueue();
        }
        return true;
    }

    public void cancelVideoConvert(MessageObject messageObject) {
        if (messageObject == null) {
            synchronized (videoConvertSync) {
                cancelCurrentVideoConversion = true;
            }
        } else {
            if (!videoConvertQueue.isEmpty()) {
                if (videoConvertQueue.get(0) == messageObject) {
                    synchronized (videoConvertSync) {
                        cancelCurrentVideoConversion = true;
                    }
                } else {
                    videoConvertQueue.remove(messageObject);
                }
            }
        }
    }

    private boolean startVideoConvertFromQueue() {
        if (!videoConvertQueue.isEmpty()) {
            synchronized (videoConvertSync) {
                cancelCurrentVideoConversion = false;
            }
            MessageObject messageObject = videoConvertQueue.get(0);
            Intent intent = new Intent(ApplicationLoader.applicationContext, VideoEncodingService.class);
            intent.putExtra("path", messageObject.messageOwner.attachPath);
            if (messageObject.messageOwner.media.document != null) {
                for (int a = 0; a < messageObject.messageOwner.media.document.attributes.size(); a++) {
                    TLRPC.DocumentAttribute documentAttribute = messageObject.messageOwner.media.document.attributes.get(a);
                    if (documentAttribute instanceof TLRPC.TL_documentAttributeAnimated) {
                        intent.putExtra("gif", true);
                        break;
                    }
                }
            }
            if (messageObject.getId() != 0) {
                ApplicationLoader.applicationContext.startService(intent);
            }
            VideoConvertRunnable.runConversion(messageObject);
            return true;
        }
        return false;
    }

    @SuppressLint("NewApi")
    public static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        MediaCodecInfo lastCodecInfo = null;
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mimeType)) {
                    lastCodecInfo = codecInfo;
                    if (!lastCodecInfo.getName().equals("OMX.SEC.avc.enc")) {
                        return lastCodecInfo;
                    } else if (lastCodecInfo.getName().equals("OMX.SEC.AVC.Encoder")) {
                        return lastCodecInfo;
                    }
                }
            }
        }
        return lastCodecInfo;
    }

    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    @SuppressLint("NewApi")
    public static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        int lastColorFormat = 0;
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                lastColorFormat = colorFormat;
                if (!(codecInfo.getName().equals("OMX.SEC.AVC.Encoder") && colorFormat == 19)) {
                    return colorFormat;
                }
            }
        }
        return lastColorFormat;
    }

    private int selectTrack(MediaExtractor extractor, boolean audio) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (audio) {
                if (mime.startsWith("audio/")) {
                    return i;
                }
            } else {
                if (mime.startsWith("video/")) {
                    return i;
                }
            }
        }
        return -5;
    }

    private void didWriteData(final MessageObject messageObject, final File file, final boolean last, final boolean error) {
        final boolean firstWrite = videoConvertFirstWrite;
        if (firstWrite) {
            videoConvertFirstWrite = false;
        }
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (error || last) {
                    synchronized (videoConvertSync) {
                        cancelCurrentVideoConversion = false;
                    }
                    videoConvertQueue.remove(messageObject);
                    startVideoConvertFromQueue();
                }
                if (error) {
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.FilePreparingFailed, messageObject, file.toString());
                } else {
                    if (firstWrite) {
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.FilePreparingStarted, messageObject, file.toString());
                    }
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.FileNewChunkAvailable, messageObject, file.toString(), last ? file.length() : 0);
                }
            }
        });
    }

    private long readAndWriteTrack(final MessageObject messageObject, MediaExtractor extractor, MP4Builder mediaMuxer, MediaCodec.BufferInfo info, long start, long end, File file, boolean isAudio) throws Exception {
        int trackIndex = selectTrack(extractor, isAudio);
        if (trackIndex >= 0) {
            extractor.selectTrack(trackIndex);
            MediaFormat trackFormat = extractor.getTrackFormat(trackIndex);
            int muxerTrackIndex = mediaMuxer.addTrack(trackFormat, isAudio);
            int maxBufferSize = trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
            boolean inputDone = false;
            if (start > 0) {
                extractor.seekTo(start, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            } else {
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            }
            ByteBuffer buffer = ByteBuffer.allocateDirect(maxBufferSize);
            long startTime = -1;

            checkConversionCanceled();

            while (!inputDone) {
                checkConversionCanceled();

                boolean eof = false;
                int index = extractor.getSampleTrackIndex();
                if (index == trackIndex) {
                    info.size = extractor.readSampleData(buffer, 0);
                    if (Build.VERSION.SDK_INT < 21) {
                        buffer.position(0);
                        buffer.limit(info.size);
                    }
                    if (!isAudio) {
                        byte[] array = buffer.array();
                        if (array != null) {
                            int offset = buffer.arrayOffset();
                            int len = offset + buffer.limit();
                            int writeStart = -1;
                            for (int a = offset; a <= len - 4; a++) {
                                if (array[a] == 0 && array[a + 1] == 0 && array[a + 2] == 0 && array[a + 3] == 1 || a == len - 4) {
                                    if (writeStart != -1) {
                                        int l = a - writeStart - (a != len - 4 ? 4 : 0);
                                        array[writeStart] = (byte) (l >> 24);
                                        array[writeStart + 1] = (byte) (l >> 16);
                                        array[writeStart + 2] = (byte) (l >> 8);
                                        array[writeStart + 3] = (byte) l;
                                        writeStart = a;
                                    } else {
                                        writeStart = a;
                                    }
                                }
                            }
                        }
                    }
                    if (info.size >= 0) {
                        info.presentationTimeUs = extractor.getSampleTime();
                    } else {
                        info.size = 0;
                        eof = true;
                    }

                    if (info.size > 0 && !eof) {
                        if (start > 0 && startTime == -1) {
                            startTime = info.presentationTimeUs;
                        }
                        if (end < 0 || info.presentationTimeUs < end) {
                            info.offset = 0;
                            info.flags = extractor.getSampleFlags();
                            if (mediaMuxer.writeSampleData(muxerTrackIndex, buffer, info, false)) {
                                didWriteData(messageObject, file, false, false);
                            }
                        } else {
                            eof = true;
                        }
                    }
                    if (!eof) {
                        extractor.advance();
                    }
                } else if (index == -1) {
                    eof = true;
                } else {
                    extractor.advance();
                }
                if (eof) {
                    inputDone = true;
                }
            }

            extractor.unselectTrack(trackIndex);
            return startTime;
        }
        return -1;
    }

    private static class VideoConvertRunnable implements Runnable {

        private MessageObject messageObject;

        private VideoConvertRunnable(MessageObject message) {
            messageObject = message;
        }

        @Override
        public void run() {
            MediaController.getInstance().convertVideo(messageObject);
        }

        public static void runConversion(final MessageObject obj) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        VideoConvertRunnable wrapper = new VideoConvertRunnable(obj);
                        Thread th = new Thread(wrapper, "VideoConvertRunnable");
                        th.start();
                        th.join();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }).start();
        }
    }

    private void checkConversionCanceled() throws Exception {
        boolean cancelConversion;
        synchronized (videoConvertSync) {
            cancelConversion = cancelCurrentVideoConversion;
        }
        if (cancelConversion) {
            throw new RuntimeException("canceled conversion");
        }
    }

    private boolean convertVideo(final MessageObject messageObject) {
        String videoPath = messageObject.videoEditedInfo.originalPath;
        long startTime = messageObject.videoEditedInfo.startTime;
        long endTime = messageObject.videoEditedInfo.endTime;
        int resultWidth = messageObject.videoEditedInfo.resultWidth;
        int resultHeight = messageObject.videoEditedInfo.resultHeight;
        int rotationValue = messageObject.videoEditedInfo.rotationValue;
        int originalWidth = messageObject.videoEditedInfo.originalWidth;
        int originalHeight = messageObject.videoEditedInfo.originalHeight;
        int bitrate = messageObject.videoEditedInfo.bitrate;
        int rotateRender = 0;
        File cacheFile = new File(messageObject.messageOwner.attachPath);

        if (Build.VERSION.SDK_INT < 18 && resultHeight > resultWidth && resultWidth != originalWidth && resultHeight != originalHeight) {
            int temp = resultHeight;
            resultHeight = resultWidth;
            resultWidth = temp;
            rotationValue = 90;
            rotateRender = 270;
        } else if (Build.VERSION.SDK_INT > 20) {
            if (rotationValue == 90) {
                int temp = resultHeight;
                resultHeight = resultWidth;
                resultWidth = temp;
                rotationValue = 0;
                rotateRender = 270;
            } else if (rotationValue == 180) {
                rotateRender = 180;
                rotationValue = 0;
            } else if (rotationValue == 270) {
                int temp = resultHeight;
                resultHeight = resultWidth;
                resultWidth = temp;
                rotationValue = 0;
                rotateRender = 90;
            }
        }

        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("videoconvert", Activity.MODE_PRIVATE);
        File inputFile = new File(videoPath);
        if (messageObject.getId() != 0) {
            boolean isPreviousOk = preferences.getBoolean("isPreviousOk", true);
            preferences.edit().putBoolean("isPreviousOk", false).commit();
            if (!inputFile.canRead() || !isPreviousOk) {
                didWriteData(messageObject, cacheFile, true, true);
                preferences.edit().putBoolean("isPreviousOk", true).commit();
                return false;
            }
        }

        videoConvertFirstWrite = true;
        boolean error = false;
        long videoStartTime = startTime;

        long time = System.currentTimeMillis();

        if (resultWidth != 0 && resultHeight != 0) {
            MP4Builder mediaMuxer = null;
            MediaExtractor extractor = null;

            try {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                Mp4Movie movie = new Mp4Movie();
                movie.setCacheFile(cacheFile);
                movie.setRotation(rotationValue);
                movie.setSize(resultWidth, resultHeight);
                mediaMuxer = new MP4Builder().createMovie(movie);
                extractor = new MediaExtractor();
                extractor.setDataSource(videoPath);

                checkConversionCanceled();

                if (resultWidth != originalWidth || resultHeight != originalHeight || rotateRender != 0 || messageObject.videoEditedInfo.roundVideo) {
                    int videoIndex;
                    videoIndex = selectTrack(extractor, false);
                    if (videoIndex >= 0) {
                        MediaCodec decoder = null;
                        MediaCodec encoder = null;
                        InputSurface inputSurface = null;
                        OutputSurface outputSurface = null;

                        try {
                            long videoTime = -1;
                            boolean outputDone = false;
                            boolean inputDone = false;
                            boolean decoderDone = false;
                            int swapUV = 0;
                            int videoTrackIndex = -5;

                            int colorFormat;
                            int processorType = PROCESSOR_TYPE_OTHER;
                            String manufacturer = Build.MANUFACTURER.toLowerCase();
                            if (Build.VERSION.SDK_INT < 18) {
                                MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
                                colorFormat = selectColorFormat(codecInfo, MIME_TYPE);
                                if (colorFormat == 0) {
                                    throw new RuntimeException("no supported color format");
                                }
                                String codecName = codecInfo.getName();
                                if (codecName.contains("OMX.qcom.")) {
                                    processorType = PROCESSOR_TYPE_QCOM;
                                    if (Build.VERSION.SDK_INT == 16) {
                                        if (manufacturer.equals("lge") || manufacturer.equals("nokia")) {
                                            swapUV = 1;
                                        }
                                    }
                                } else if (codecName.contains("OMX.Intel.")) {
                                    processorType = PROCESSOR_TYPE_INTEL;
                                } else if (codecName.equals("OMX.MTK.VIDEO.ENCODER.AVC")) {
                                    processorType = PROCESSOR_TYPE_MTK;
                                } else if (codecName.equals("OMX.SEC.AVC.Encoder")) {
                                    processorType = PROCESSOR_TYPE_SEC;
                                    swapUV = 1;
                                } else if (codecName.equals("OMX.TI.DUCATI1.VIDEO.H264E")) {
                                    processorType = PROCESSOR_TYPE_TI;
                                }
                                FileLog.e("codec = " + codecInfo.getName() + " manufacturer = " + manufacturer + "device = " + Build.MODEL);
                            } else {
                                colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
                            }
                            FileLog.e("colorFormat = " + colorFormat);

                            int resultHeightAligned = resultHeight;
                            int padding = 0;
                            int bufferSize = resultWidth * resultHeight * 3 / 2;
                            if (processorType == PROCESSOR_TYPE_OTHER) {
                                if (resultHeight % 16 != 0) {
                                    resultHeightAligned += (16 - (resultHeight % 16));
                                    padding = resultWidth * (resultHeightAligned - resultHeight);
                                    bufferSize += padding * 5 / 4;
                                }
                            } else if (processorType == PROCESSOR_TYPE_QCOM) {
                                if (!manufacturer.toLowerCase().equals("lge")) {
                                    int uvoffset = (resultWidth * resultHeight + 2047) & ~2047;
                                    padding = uvoffset - (resultWidth * resultHeight);
                                    bufferSize += padding;
                                }
                            } else if (processorType == PROCESSOR_TYPE_TI) {
                                //resultHeightAligned = 368;
                                //bufferSize = resultWidth * resultHeightAligned * 3 / 2;
                                //resultHeightAligned += (16 - (resultHeight % 16));
                                //padding = resultWidth * (resultHeightAligned - resultHeight);
                                //bufferSize += padding * 5 / 4;
                            } else if (processorType == PROCESSOR_TYPE_MTK) {
                                if (manufacturer.equals("baidu")) {
                                    resultHeightAligned += (16 - (resultHeight % 16));
                                    padding = resultWidth * (resultHeightAligned - resultHeight);
                                    bufferSize += padding * 5 / 4;
                                }
                            }

                            extractor.selectTrack(videoIndex);
                            if (startTime > 0) {
                                extractor.seekTo(startTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                            } else {
                                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                            }
                            MediaFormat inputFormat = extractor.getTrackFormat(videoIndex);

                            MediaFormat outputFormat = MediaFormat.createVideoFormat(MIME_TYPE, resultWidth, resultHeight);
                            outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
                            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate > 0 ? bitrate : 921600);
                            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25);
                            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
                            if (Build.VERSION.SDK_INT < 18) {
                                outputFormat.setInteger("stride", resultWidth + 32);
                                outputFormat.setInteger("slice-height", resultHeight);
                            }

                            encoder = MediaCodec.createEncoderByType(MIME_TYPE);
                            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                            if (Build.VERSION.SDK_INT >= 18) {
                                inputSurface = new InputSurface(encoder.createInputSurface());
                                inputSurface.makeCurrent();
                            }
                            encoder.start();

                            decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME));
                            if (Build.VERSION.SDK_INT >= 18) {
                                outputSurface = new OutputSurface();
                            } else {
                                outputSurface = new OutputSurface(resultWidth, resultHeight, rotateRender);
                            }
                            decoder.configure(inputFormat, outputSurface.getSurface(), null, 0);
                            decoder.start();

                            final int TIMEOUT_USEC = 2500;
                            ByteBuffer[] decoderInputBuffers = null;
                            ByteBuffer[] encoderOutputBuffers = null;
                            ByteBuffer[] encoderInputBuffers = null;
                            if (Build.VERSION.SDK_INT < 21) {
                                decoderInputBuffers = decoder.getInputBuffers();
                                encoderOutputBuffers = encoder.getOutputBuffers();
                                if (Build.VERSION.SDK_INT < 18) {
                                    encoderInputBuffers = encoder.getInputBuffers();
                                }
                            }

                            checkConversionCanceled();

                            while (!outputDone) {
                                checkConversionCanceled();
                                if (!inputDone) {
                                    boolean eof = false;
                                    int index = extractor.getSampleTrackIndex();
                                    if (index == videoIndex) {
                                        int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                                        if (inputBufIndex >= 0) {
                                            ByteBuffer inputBuf;
                                            if (Build.VERSION.SDK_INT < 21) {
                                                inputBuf = decoderInputBuffers[inputBufIndex];
                                            } else {
                                                inputBuf = decoder.getInputBuffer(inputBufIndex);
                                            }
                                            int chunkSize = extractor.readSampleData(inputBuf, 0);
                                            if (chunkSize < 0) {
                                                decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                                inputDone = true;
                                            } else {
                                                decoder.queueInputBuffer(inputBufIndex, 0, chunkSize, extractor.getSampleTime(), 0);
                                                extractor.advance();
                                            }
                                        }
                                    } else if (index == -1) {
                                        eof = true;
                                    }
                                    if (eof) {
                                        int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                                        if (inputBufIndex >= 0) {
                                            decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                            inputDone = true;
                                        }
                                    }
                                }

                                boolean decoderOutputAvailable = !decoderDone;
                                boolean encoderOutputAvailable = true;
                                while (decoderOutputAvailable || encoderOutputAvailable) {
                                    checkConversionCanceled();
                                    int encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                                    if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                        encoderOutputAvailable = false;
                                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                                        if (Build.VERSION.SDK_INT < 21) {
                                            encoderOutputBuffers = encoder.getOutputBuffers();
                                        }
                                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                        MediaFormat newFormat = encoder.getOutputFormat();
                                        if (videoTrackIndex == -5) {
                                            videoTrackIndex = mediaMuxer.addTrack(newFormat, false);
                                        }
                                    } else if (encoderStatus < 0) {
                                        throw new RuntimeException("unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                                    } else {
                                        ByteBuffer encodedData;
                                        if (Build.VERSION.SDK_INT < 21) {
                                            encodedData = encoderOutputBuffers[encoderStatus];
                                        } else {
                                            encodedData = encoder.getOutputBuffer(encoderStatus);
                                        }
                                        if (encodedData == null) {
                                            throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                                        }
                                        if (info.size > 1) {
                                            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                                if (mediaMuxer.writeSampleData(videoTrackIndex, encodedData, info, true)) {
                                                    didWriteData(messageObject, cacheFile, false, false);
                                                }
                                            } else if (videoTrackIndex == -5) {
                                                byte[] csd = new byte[info.size];
                                                encodedData.limit(info.offset + info.size);
                                                encodedData.position(info.offset);
                                                encodedData.get(csd);
                                                ByteBuffer sps = null;
                                                ByteBuffer pps = null;
                                                for (int a = info.size - 1; a >= 0; a--) {
                                                    if (a > 3) {
                                                        if (csd[a] == 1 && csd[a - 1] == 0 && csd[a - 2] == 0 && csd[a - 3] == 0) {
                                                            sps = ByteBuffer.allocate(a - 3);
                                                            pps = ByteBuffer.allocate(info.size - (a - 3));
                                                            sps.put(csd, 0, a - 3).position(0);
                                                            pps.put(csd, a - 3, info.size - (a - 3)).position(0);
                                                            break;
                                                        }
                                                    } else {
                                                        break;
                                                    }
                                                }

                                                MediaFormat newFormat = MediaFormat.createVideoFormat(MIME_TYPE, resultWidth, resultHeight);
                                                if (sps != null && pps != null) {
                                                    newFormat.setByteBuffer("csd-0", sps);
                                                    newFormat.setByteBuffer("csd-1", pps);
                                                }
                                                videoTrackIndex = mediaMuxer.addTrack(newFormat, false);
                                            }
                                        }
                                        outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                                        encoder.releaseOutputBuffer(encoderStatus, false);
                                    }
                                    if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                                        continue;
                                    }

                                    if (!decoderDone) {
                                        int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                                        if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                            decoderOutputAvailable = false;
                                        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {

                                        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                            MediaFormat newFormat = decoder.getOutputFormat();
                                            FileLog.e("newFormat = " + newFormat);
                                        } else if (decoderStatus < 0) {
                                            throw new RuntimeException("unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                                        } else {
                                            boolean doRender;
                                            if (Build.VERSION.SDK_INT >= 18) {
                                                doRender = info.size != 0;
                                            } else {
                                                doRender = info.size != 0 || info.presentationTimeUs != 0;
                                            }
                                            if (endTime > 0 && info.presentationTimeUs >= endTime) {
                                                inputDone = true;
                                                decoderDone = true;
                                                doRender = false;
                                                info.flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                                            }
                                            if (startTime > 0 && videoTime == -1) {
                                                if (info.presentationTimeUs < startTime) {
                                                    doRender = false;
                                                    FileLog.e("drop frame startTime = " + startTime + " present time = " + info.presentationTimeUs);
                                                } else {
                                                    videoTime = info.presentationTimeUs;
                                                }
                                            }
                                            decoder.releaseOutputBuffer(decoderStatus, doRender);
                                            if (doRender) {
                                                boolean errorWait = false;
                                                try {
                                                    outputSurface.awaitNewImage();
                                                } catch (Exception e) {
                                                    errorWait = true;
                                                    FileLog.e(e);
                                                }
                                                if (!errorWait) {
                                                    if (Build.VERSION.SDK_INT >= 18) {
                                                        outputSurface.drawImage(false);
                                                        inputSurface.setPresentationTime(info.presentationTimeUs * 1000);
                                                        inputSurface.swapBuffers();
                                                    } else {
                                                        int inputBufIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
                                                        if (inputBufIndex >= 0) {
                                                            outputSurface.drawImage(true);
                                                            ByteBuffer rgbBuf = outputSurface.getFrame();
                                                            ByteBuffer yuvBuf = encoderInputBuffers[inputBufIndex];
                                                            yuvBuf.clear();
                                                            Utilities.convertVideoFrame(rgbBuf, yuvBuf, colorFormat, resultWidth, resultHeight, padding, swapUV);
                                                            encoder.queueInputBuffer(inputBufIndex, 0, bufferSize, info.presentationTimeUs, 0);
                                                        } else {
                                                            FileLog.e("input buffer not available");
                                                        }
                                                    }
                                                }
                                            }
                                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                                decoderOutputAvailable = false;
                                                FileLog.e("decoder stream end");
                                                if (Build.VERSION.SDK_INT >= 18) {
                                                    encoder.signalEndOfInputStream();
                                                } else {
                                                    int inputBufIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
                                                    if (inputBufIndex >= 0) {
                                                        encoder.queueInputBuffer(inputBufIndex, 0, 1, info.presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (videoTime != -1) {
                                videoStartTime = videoTime;
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                            error = true;
                        }

                        extractor.unselectTrack(videoIndex);

                        if (outputSurface != null) {
                            outputSurface.release();
                        }
                        if (inputSurface != null) {
                            inputSurface.release();
                        }
                        if (decoder != null) {
                            decoder.stop();
                            decoder.release();
                        }
                        if (encoder != null) {
                            encoder.stop();
                            encoder.release();
                        }

                        checkConversionCanceled();
                    }
                } else {
                    long videoTime = readAndWriteTrack(messageObject, extractor, mediaMuxer, info, startTime, endTime, cacheFile, false);
                    if (videoTime != -1) {
                        videoStartTime = videoTime;
                    }
                }
                if (!error && bitrate != -1) {
                    readAndWriteTrack(messageObject, extractor, mediaMuxer, info, videoStartTime, endTime, cacheFile, true);
                }
            } catch (Exception e) {
                error = true;
                FileLog.e(e);
            } finally {
                if (extractor != null) {
                    extractor.release();
                }
                if (mediaMuxer != null) {
                    try {
                        mediaMuxer.finishMovie();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                FileLog.e("time = " + (System.currentTimeMillis() - time));
            }
        } else {
            preferences.edit().putBoolean("isPreviousOk", true).commit();
            didWriteData(messageObject, cacheFile, true, true);
            return false;
        }
        preferences.edit().putBoolean("isPreviousOk", true).commit();
        didWriteData(messageObject, cacheFile, true, error);
        return true;
    }
}

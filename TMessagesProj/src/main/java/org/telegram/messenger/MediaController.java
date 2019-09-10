/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
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
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;

import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.messenger.video.InputSurface;
import org.telegram.messenger.video.MP4Builder;
import org.telegram.messenger.video.Mp4Movie;
import org.telegram.messenger.video.OutputSurface;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
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
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MediaController implements AudioManager.OnAudioFocusChangeListener, NotificationCenter.NotificationCenterDelegate, SensorEventListener {

    private native int startRecord(String path);
    private native int writeFrame(ByteBuffer frame, int len);
    private native void stopRecord();
    public static native int isOpusFile(String path);
    public native byte[] getWaveform(String path);
    public native byte[] getWaveform2(short[] array, int length);

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
            Build.VERSION.SDK_INT > 28 ? MediaStore.Images.Media.DATE_MODIFIED : MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.ORIENTATION
    };

    private static final String[] projectionVideo = {
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            Build.VERSION.SDK_INT > 28 ? MediaStore.Images.Media.DATE_MODIFIED : MediaStore.Video.Media.DATE_TAKEN,
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
        public boolean videoOnly;
        public String bucketName;
        public PhotoEntry coverPhoto;
        public ArrayList<PhotoEntry> photos = new ArrayList<>();
        public SparseArray<PhotoEntry> photosByIds = new SparseArray<>();

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
        public ArrayList<TLRPC.MessageEntity> entities;
        public boolean isFiltered;
        public boolean isPainted;
        public boolean isCropped;
        public boolean isMuted;
        public int ttl;
        public boolean canDeleteAfter;
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
            if (!isVideo) {
                thumbPath = null;
            }
            editedInfo = null;
            caption = null;
            entities = null;
            savedFilterState = null;
            stickers.clear();
        }
    }

    public static class SearchImage {
        public String id;
        public String imageUrl;
        public String thumbUrl;
        public int width;
        public int height;
        public int size;
        public int type;
        public int date;
        public String thumbPath;
        public String imagePath;
        public CharSequence caption;
        public ArrayList<TLRPC.MessageEntity> entities;
        public TLRPC.Document document;
        public TLRPC.Photo photo;
        public TLRPC.PhotoSize photoSize;
        public TLRPC.PhotoSize thumbPhotoSize;
        public boolean isFiltered;
        public boolean isPainted;
        public boolean isCropped;
        public int ttl;
        public TLRPC.BotInlineResult inlineResult;
        public HashMap<String, String> params;
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
            entities = null;
            savedFilterState = null;
            stickers.clear();
        }

        public String getAttachName() {
            if (photoSize != null) {
                return FileLoader.getAttachFileName(photoSize);
            } else if (document != null) {
                return FileLoader.getAttachFileName(document);
            }
            return Utilities.MD5(imageUrl) + "." + ImageLoader.getHttpUrlExtension(imageUrl, "jpg");
        }

        public String getPathToAttach() {
            if (photoSize != null) {
                return FileLoader.getPathToAttach(photoSize, true).getAbsolutePath();
            } else if (document != null) {
                return FileLoader.getPathToAttach(document, true).getAbsolutePath();
            } else {
                return imageUrl;
            }
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
    private int raisedToTopSign;
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
    private SparseArray<MessageObject> voiceMessagesPlaylistMap;

    private static Runnable refreshGalleryRunnable;
    public static AlbumEntry allMediaAlbumEntry;
    public static AlbumEntry allPhotosAlbumEntry;
    public static AlbumEntry allVideosAlbumEntry;
    public static ArrayList<AlbumEntry> allMediaAlbums = new ArrayList<>();
    public static ArrayList<AlbumEntry> allPhotoAlbums = new ArrayList<>();
    private static Runnable broadcastPhotosRunnable;

    private boolean isPaused = false;
    private VideoPlayer audioPlayer = null;
    private float currentPlaybackSpeed = 1.0f;
    private float seekToProgressPending;
    private long lastProgress = 0;
    private MessageObject playingMessageObject;
    private MessageObject goingToShowMessageObject;
    private Timer progressTimer = null;
    private final Object progressTimerSync = new Object();
    private ArrayList<MessageObject> playlist = new ArrayList<>();
    private ArrayList<MessageObject> shuffledPlaylist = new ArrayList<>();
    private int currentPlaylistNum;
    private boolean forceLoopCurrentPlaylist;
    private boolean downloadingCurrentMessage;
    private boolean playMusicAgain;
    private AudioInfo audioInfo;
    private VideoPlayer videoPlayer;
    private boolean playerWasReady;
    private TextureView currentTextureView;
    private PipRoundVideoView pipRoundVideoView;
    private int pipSwitchingState;
    private Activity baseActivity;
    private BaseFragment flagSecureFragment;
    private View feedbackView;
    private AspectRatioFrameLayout currentAspectRatioFrameLayout;
    private boolean isDrawingWasReady;
    private FrameLayout currentTextureViewContainer;
    private int currentAspectRatioFrameLayoutRotation;
    private float currentAspectRatioFrameLayoutRatio;
    private boolean currentAspectRatioFrameLayoutReady;

    private Runnable setLoadingRunnable = new Runnable() {
        @Override
        public void run() {
            if (playingMessageObject == null) {
                return;
            }
            FileLoader.getInstance(playingMessageObject.currentAccount).setLoadingVideo(playingMessageObject.getDocument(), true, false);
        }
    };

    private AudioRecord audioRecorder;
    private TLRPC.TL_document recordingAudio;
    private int recordingGuid = -1;
    private int recordingCurrentAccount;
    private File recordingAudioFile;
    private long recordStartTime;
    private long recordTimeCount;
    private long recordDialogId;
    private MessageObject recordReplyingMessageObject;
    private short[] recordSamples = new short[1024];
    private long samplesCount;

    private final Object sync = new Object();

    private ArrayList<ByteBuffer> recordBuffers = new ArrayList<>();
    private ByteBuffer fileBuffer;
    private int recordBufferSize = 1280;
    private int sendAfterDone;
    private boolean sendAfterDoneNotify;
    private int sendAfterDoneScheduleDate;

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
                        fileEncodingQueue.postRunnable(() -> {
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
                            recordQueue.postRunnable(() -> recordBuffers.add(finalBuffer));
                        });
                    }
                    recordQueue.postRunnable(recordRunnable);
                    AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(recordingCurrentAccount).postNotificationName(NotificationCenter.recordProgressChanged, recordingGuid, System.currentTimeMillis() - recordStartTime, amplitude));
                } else {
                    recordBuffers.add(buffer);
                    if (sendAfterDone != 3) {
                        stopRecordingInternal(sendAfterDone, sendAfterDoneNotify, sendAfterDoneScheduleDate);
                    }
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

    private class GalleryObserverInternal extends ContentObserver {
        public GalleryObserverInternal() {
            super(null);
        }

        private void scheduleReloadRunnable() {
            AndroidUtilities.runOnUIThread(refreshGalleryRunnable = () -> {
                if (PhotoViewer.getInstance().isVisible()) {
                    scheduleReloadRunnable();
                    return;
                }
                refreshGalleryRunnable = null;
                loadGalleryPhotosAlbums(0);
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
            AndroidUtilities.runOnUIThread(refreshGalleryRunnable = () -> {
                refreshGalleryRunnable = null;
                loadGalleryPhotosAlbums(0);
            }, 2000);
        }
    }

    public static void checkGallery() {
        if (Build.VERSION.SDK_INT < 24 || allPhotosAlbumEntry == null) {
            return;
        }
        final int prevSize = allPhotosAlbumEntry.photos.size();
        Utilities.globalQueue.postRunnable(() -> {
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
        }, 2000);
    }


    private ExternalObserver externalObserver;
    private InternalObserver internalObserver;
    private long lastChatEnterTime;
    private int lastChatAccount;
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

    private String[] mediaProjections;

    private static volatile MediaController Instance;

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
        recordQueue = new DispatchQueue("recordQueue");
        recordQueue.setPriority(Thread.MAX_PRIORITY);
        fileEncodingQueue = new DispatchQueue("fileEncodingQueue");
        fileEncodingQueue.setPriority(Thread.MAX_PRIORITY);

        recordQueue.postRunnable(() -> {
            try {
                recordBufferSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (recordBufferSize <= 0) {
                    recordBufferSize = 1280;
                }
                for (int a = 0; a < 5; a++) {
                    ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
                    buffer.order(ByteOrder.nativeOrder());
                    recordBuffers.add(buffer);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
        Utilities.globalQueue.postRunnable(() -> {
            try {
                currentPlaybackSpeed = MessagesController.getGlobalMainSettings().getFloat("playbackSpeed", 1.0f);
                sensorManager = (SensorManager) ApplicationLoader.applicationContext.getSystemService(Context.SENSOR_SERVICE);
                linearSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
                gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
                if (linearSensor == null || gravitySensor == null) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("gravity or linear sensor not found");
                    }
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

            try {
                PhoneStateListener phoneStateListener = new PhoneStateListener() {
                    @Override
                    public void onCallStateChanged(final int state, String incomingNumber) {
                        AndroidUtilities.runOnUIThread(() -> {
                            if (state == TelephonyManager.CALL_STATE_RINGING) {
                                if (isPlayingMessage(playingMessageObject) && !isMessagePaused()) {
                                    pauseMessage(playingMessageObject);
                                } else if (recordStartRunnable != null || recordingAudio != null) {
                                    stopRecording(2, false, 0);
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
        });

        fileBuffer = ByteBuffer.allocateDirect(1920);

        AndroidUtilities.runOnUIThread(() -> {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                NotificationCenter.getInstance(a).addObserver(MediaController.this, NotificationCenter.fileDidLoad);
                NotificationCenter.getInstance(a).addObserver(MediaController.this, NotificationCenter.httpFileDidLoad);
                NotificationCenter.getInstance(a).addObserver(MediaController.this, NotificationCenter.didReceiveNewMessages);
                NotificationCenter.getInstance(a).addObserver(MediaController.this, NotificationCenter.messagesDeleted);
                NotificationCenter.getInstance(a).addObserver(MediaController.this, NotificationCenter.removeAllMessagesFromDialog);
                NotificationCenter.getInstance(a).addObserver(MediaController.this, NotificationCenter.musicDidLoad);
                NotificationCenter.getGlobalInstance().addObserver(MediaController.this, NotificationCenter.playerDidStartPlaying);
            }
        });

        mediaProjections = new String[]{
                MediaStore.Images.ImageColumns.DATA,
                MediaStore.Images.ImageColumns.DISPLAY_NAME,
                MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
                Build.VERSION.SDK_INT > 28 ? MediaStore.Images.ImageColumns.DATE_MODIFIED : MediaStore.Images.ImageColumns.DATE_TAKEN,
                MediaStore.Images.ImageColumns.TITLE,
                MediaStore.Images.ImageColumns.WIDTH,
                MediaStore.Images.ImageColumns.HEIGHT
        };

        ContentResolver contentResolver = ApplicationLoader.applicationContext.getContentResolver();
        try {
            contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, new GalleryObserverExternal());
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            contentResolver.registerContentObserver(MediaStore.Images.Media.INTERNAL_CONTENT_URI, true, new GalleryObserverInternal());
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, new GalleryObserverExternal());
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            contentResolver.registerContentObserver(MediaStore.Video.Media.INTERNAL_CONTENT_URI, true, new GalleryObserverInternal());
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            if (isPlayingMessage(getPlayingMessageObject()) && !isMessagePaused()) {
                pauseMessage(playingMessageObject);
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
                pauseMessage(playingMessageObject);
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
                audioPlayer.setVolume(volume);
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
            final String fileName = currentPlayingMessageObject.getFileName();
            progressTimer = new Timer();
            progressTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    synchronized (sync) {
                        AndroidUtilities.runOnUIThread(() -> {
                            if (currentPlayingMessageObject != null && (audioPlayer != null || videoPlayer != null) && !isPaused) {
                                try {
                                    long duration;
                                    long progress;
                                    float value;
                                    float bufferedValue;
                                    if (videoPlayer != null) {
                                        duration = videoPlayer.getDuration();
                                        progress = videoPlayer.getCurrentPosition();
                                        if (progress < 0 || duration <= 0) {
                                            return;
                                        }
                                        bufferedValue = videoPlayer.getBufferedPosition() / (float) duration;
                                        value = duration >= 0 ? progress / (float) duration : 0.0f;
                                        if (value >= 1) {
                                            return;
                                        }
                                    } else {
                                        duration = audioPlayer.getDuration();
                                        progress = audioPlayer.getCurrentPosition();
                                        value = duration >= 0 ? (progress / (float) duration) : 0.0f;
                                        bufferedValue = audioPlayer.getBufferedPosition() / (float) duration;
                                        /*if (audioPlayer.isStreaming()) {
                                            bufferedValue = FileLoader.getInstance(currentPlayingMessageObject.currentAccount).getBufferedProgressFromPosition(value, fileName);
                                        } else {
                                            bufferedValue = 1.0f;
                                        }*/
                                        if (duration == C.TIME_UNSET || progress < 0 || seekToProgressPending != 0) {
                                            return;
                                        }
                                    }
                                    lastProgress = progress;
                                    currentPlayingMessageObject.audioPlayerDuration = (int) (duration / 1000);
                                    currentPlayingMessageObject.audioProgress = value;
                                    currentPlayingMessageObject.audioProgressSec = (int) (lastProgress / 1000);
                                    currentPlayingMessageObject.bufferedProgress = bufferedValue;
                                    NotificationCenter.getInstance(currentPlayingMessageObject.currentAccount).postNotificationName(NotificationCenter.messagePlayingProgressDidChanged, currentPlayingMessageObject.getId(), value);
                                } catch (Exception e) {
                                    FileLog.e(e);
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
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            DownloadController.getInstance(a).cleanup();
        }
        videoConvertQueue.clear();
        playlist.clear();
        shuffledPlaylist.clear();
        generatingWaveform.clear();
        voiceMessagesPlaylist = null;
        voiceMessagesPlaylistMap = null;
        cancelVideoConvert(null);
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

    public void stopMediaObserver() {
        if (stopMediaObserverRunnable == null) {
            stopMediaObserverRunnable = new StopMediaObserverRunnable();
        }
        stopMediaObserverRunnable.currentObserverToken = startObserverToken;
        ApplicationLoader.applicationHandler.postDelayed(stopMediaObserverRunnable, 5000);
    }

    private void processMediaObserver(Uri uri) {
        Cursor cursor = null;
        try {
            Point size = AndroidUtilities.getRealScreenSize();

            cursor = ApplicationLoader.applicationContext.getContentResolver().query(uri, mediaProjections, null, null, "date_added DESC LIMIT 1");
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
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getInstance(lastChatAccount).postNotificationName(NotificationCenter.screenshotTook);
                    checkScreenshots(screenshotDates);
                });
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            try {
                if (cursor != null) {
                    cursor.close();
                }
            } catch (Exception ignore) {

            }
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
                SecretChatHelper.getInstance(lastChatAccount).sendScreenshotMessage(lastSecretChat, lastChatVisibleMessages, null);
            } else {
                SendMessagesHelper.getInstance(lastChatAccount).sendScreenshotMessage(lastUser, lastMessageId, null);
            }
        }
    }

    public void setLastVisibleMessageIds(int account, long enterTime, long leaveTime, TLRPC.User user, TLRPC.EncryptedChat encryptedChat, ArrayList<Long> visibleMessages, int visibleMessage) {
        lastChatEnterTime = enterTime;
        lastChatLeaveTime = leaveTime;
        lastChatAccount = account;
        lastSecretChat = encryptedChat;
        lastUser = user;
        lastMessageId = visibleMessage;
        lastChatVisibleMessages = visibleMessages;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.fileDidLoad || id == NotificationCenter.httpFileDidLoad) {
            String fileName = (String) args[0];
            if (downloadingCurrentMessage && playingMessageObject != null && playingMessageObject.currentAccount == account) {
                String file = FileLoader.getAttachFileName(playingMessageObject.getDocument());
                if (file.equals(fileName)) {
                    playMusicAgain = true;
                    playMessage(playingMessageObject);
                }
            }
        } else if (id == NotificationCenter.messagesDeleted) {
            boolean scheduled = (Boolean) args[2];
            if (scheduled) {
                return;
            }
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
                        Integer key = markAsDeletedMessages.get(a);
                        messageObject = voiceMessagesPlaylistMap.get(key);
                        voiceMessagesPlaylistMap.remove(key);
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
        } else if (id == NotificationCenter.musicDidLoad) {
            long did = (Long) args[0];
            if (playingMessageObject != null && playingMessageObject.isMusic() && playingMessageObject.getDialogId() == did && !playingMessageObject.scheduled) {
                ArrayList<MessageObject> arrayList = (ArrayList<MessageObject>) args[1];
                playlist.addAll(0, arrayList);
                if (SharedConfig.shuffleMusic) {
                    buildShuffledPlayList();
                    currentPlaylistNum = 0;
                } else {
                    currentPlaylistNum += arrayList.size();
                }
            }
        } else if (id == NotificationCenter.didReceiveNewMessages) {
            boolean scheduled = (Boolean) args[2];
            if (scheduled) {
                return;
            }
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
        } else if (id == NotificationCenter.playerDidStartPlaying) {
            VideoPlayer p = (VideoPlayer) args[0];
            if (!MediaController.getInstance().isCurrentPlayer(p)) {
                MediaController.getInstance().pauseMessage(MediaController.getInstance().getPlayingMessageObject());
            }
        }
    }

    protected boolean isRecordingAudio() {
        return recordStartRunnable != null || recordingAudio != null;
    }

    private boolean isNearToSensor(float value) {
        return value < 5.0f && value != proximitySensor.getMaximumRange();
    }

    public boolean isRecordingOrListeningByProximity() {
        return proximityTouched && (isRecordingAudio() || playingMessageObject != null && (playingMessageObject.isVoice() || playingMessageObject.isRoundVideo()));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!sensorsStarted || VoIPService.getSharedInstance() != null) {
            return;
        }
        if (event.sensor == proximitySensor) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("proximity changed to " + event.values[0] + " max value = " + proximitySensor.getMaximumRange());
            }
            if (lastProximityValue == -100) {
                lastProximityValue = event.values[0];
            } else if (lastProximityValue != event.values[0]) {
                proximityHasDifferentValues = true;
            }
            if (proximityHasDifferentValues) {
                proximityTouched = isNearToSensor(event.values[0]);
            }
        } else if (event.sensor == accelerometerSensor) {
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
                if (val > 0 && previousAccValue > 0 || val < 0 && previousAccValue < 0) {
                    boolean goodValue;
                    int sign;
                    if (val > 0) {
                        goodValue = val > minDist;
                        sign = 1;
                    } else {
                        goodValue = val < -minDist;
                        sign = 2;
                    }
                    if (raisedToTopSign != 0 && raisedToTopSign != sign) {
                        if (raisedToTop == minCount && goodValue) {
                            if (raisedToBack < minCount) {
                                raisedToBack++;
                                if (raisedToBack == minCount) {
                                    raisedToTop = 0;
                                    raisedToTopSign = 0;
                                    countLess = 0;
                                    timeSinceRaise = System.currentTimeMillis();
                                    if (BuildVars.LOGS_ENABLED && BuildVars.DEBUG_PRIVATE_VERSION) {
                                        FileLog.d("motion detected");
                                    }
                                }
                            }
                        } else {
                            if (!goodValue) {
                                countLess++;
                            }
                            if (countLess == countLessMax || raisedToTop != minCount || raisedToBack != 0) {
                                raisedToTop = 0;
                                raisedToTopSign = 0;
                                raisedToBack = 0;
                                countLess = 0;
                            }
                        }
                    } else {
                        if (goodValue && raisedToBack == 0 && (raisedToTopSign == 0 || raisedToTopSign == sign)) {
                            if (raisedToTop < minCount && !proximityTouched) {
                                raisedToTopSign = sign;
                                raisedToTop++;
                                if (raisedToTop == minCount) {
                                    countLess = 0;
                                }
                            }
                        } else {
                            if (!goodValue) {
                                countLess++;
                            }
                            if (raisedToTopSign != sign || countLess == countLessMax || raisedToTop != minCount || raisedToBack != 0) {
                                raisedToBack = 0;
                                raisedToTop = 0;
                                raisedToTopSign = 0;
                                countLess = 0;
                            }
                        }
                    }
                }
                /*if (val > 0 && previousAccValue > 0) {
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
                                if (BuildVars.LOGS_ENABLED && BuildVars.DEBUG_PRIVATE_VERSION) {
                                    FileLog.e("motion detected");
                                }
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
                }*/
                /*if (BuildVars.LOGS_ENABLED && BuildVars.DEBUG_PRIVATE_VERSION) {
                    FileLog.e("raise2 to top = " + raisedToTop + " to back = " + raisedToBack + " val = " + val + " countLess = " + countLess);
                }*/
            }
            previousAccValue = val;
            accelerometerVertical = gravityFast[1] > 2.5f && Math.abs(gravityFast[2]) < 4.0f && Math.abs(gravityFast[0]) > 1.5f;
            /*if (BuildVars.LOGS_ENABLED && BuildVars.DEBUG_PRIVATE_VERSION) {
                FileLog.d(accelerometerVertical + "    val = " + val + " acc (" + linearAcceleration[0] + ", " + linearAcceleration[1] + ", " + linearAcceleration[2] + ") grav (" + gravityFast[0] + ", " + gravityFast[1] + ", " + gravityFast[2] + ")");
            }*/
        }
        if (raisedToBack == minCount && accelerometerVertical && proximityTouched && !NotificationsController.audioManager.isWiredHeadsetOn()) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("sensor values reached");
            }
            if (playingMessageObject == null && recordStartRunnable == null && recordingAudio == null && !PhotoViewer.getInstance().isVisible() && ApplicationLoader.isScreenOn && !inputFieldHasText && allowStartRecord && raiseChat != null && !callInProgress) {
                if (!raiseToEarRecord) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("start record");
                    }
                    useFrontSpeaker = true;
                    if (!raiseChat.playFirstUnreadVoiceMessage()) {
                        raiseToEarRecord = true;
                        useFrontSpeaker = false;
                        startRecording(raiseChat.getCurrentAccount(), raiseChat.getDialogId(), null, raiseChat.getClassGuid());
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
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("start listen");
                    }
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
            raisedToTopSign = 0;
            countLess = 0;
        } else if (proximityTouched) {
            if (playingMessageObject != null && !ApplicationLoader.mainInterfacePaused && (playingMessageObject.isVoice() || playingMessageObject.isRoundVideo())) {
                if (!useFrontSpeaker) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("start listen by proximity only");
                    }
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
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("stop record");
                }
                stopRecording(2, false, 0);
                raiseToEarRecord = false;
                ignoreOnPause = false;
                if (proximityHasDifferentValues && proximityWakeLock != null && proximityWakeLock.isHeld()) {
                    proximityWakeLock.release();
                }
            } else if (useFrontSpeaker) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("stop listen");
                }
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
            raisedToTopSign = 0;
            countLess = 0;
            timeSinceRaise = 0;
        }
    }

    private void setUseFrontSpeaker(boolean value) {
        useFrontSpeaker = value;
        AudioManager audioManager = NotificationsController.audioManager;
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
        startRecording(raiseChat.getCurrentAccount(), raiseChat.getDialogId(), null, raiseChat.getClassGuid());
        ignoreOnPause = true;
    }

    private void startAudioAgain(boolean paused) {
        if (playingMessageObject == null) {
            return;
        }

        NotificationCenter.getInstance(playingMessageObject.currentAccount).postNotificationName(NotificationCenter.audioRouteChanged, useFrontSpeaker);
        if (videoPlayer != null) {
            videoPlayer.setStreamType(useFrontSpeaker ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);
            if (!paused) {
                videoPlayer.play();
            } else {
                pauseMessage(playingMessageObject);
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
                    AndroidUtilities.runOnUIThread(() -> pauseMessage(currentMessageObject), 100);
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
        if (!SharedConfig.raiseToSpeak && (playingMessageObject == null || !playingMessageObject.isVoice() && !playingMessageObject.isRoundVideo())) {
            return;
        }
        if (!sensorsStarted) {
            gravity[0] = gravity[1] = gravity[2] = 0;
            linearAcceleration[0] = linearAcceleration[1] = linearAcceleration[2] = 0;
            gravityFast[0] = gravityFast[1] = gravityFast[2] = 0;
            lastTimestamp = 0;
            previousAccValue = 0;
            raisedToTop = 0;
            raisedToTopSign = 0;
            countLess = 0;
            raisedToBack = 0;
            Utilities.globalQueue.postRunnable(() -> {
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
            });
            sensorsStarted = true;
        }
    }

    public void stopRaiseToEarSensors(ChatActivity chatActivity, boolean fromChat) {
        if (ignoreOnPause) {
            ignoreOnPause = false;
            return;
        }
        stopRecording(fromChat ? 2 : 0, false, 0);
        if (!sensorsStarted || ignoreOnPause || accelerometerSensor == null && (gravitySensor == null || linearAcceleration == null) || proximitySensor == null || raiseChat != chatActivity) {
            return;
        }
        raiseChat = null;
        sensorsStarted = false;
        accelerometerVertical = false;
        proximityTouched = false;
        raiseToEarRecord = false;
        useFrontSpeaker = false;
        Utilities.globalQueue.postRunnable(() -> {
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
        });
        if (proximityHasDifferentValues && proximityWakeLock != null && proximityWakeLock.isHeld()) {
            proximityWakeLock.release();
        }
    }

    public void cleanupPlayer(boolean notify, boolean stopService) {
        cleanupPlayer(notify, stopService, false, false);
    }

    public void cleanupPlayer(boolean notify, boolean stopService, boolean byVoiceEnd, boolean transferPlayerToPhotoViewer) {
        if (audioPlayer != null) {
            try {
                audioPlayer.releasePlayer(true);
            } catch (Exception e) {
                FileLog.e(e);
            }
            audioPlayer = null;
        } else if (videoPlayer != null) {
            currentAspectRatioFrameLayout = null;
            currentTextureViewContainer = null;
            currentAspectRatioFrameLayoutReady = false;
            isDrawingWasReady = false;
            currentTextureView = null;
            goingToShowMessageObject = null;
            if (transferPlayerToPhotoViewer) {
                PhotoViewer.getInstance().injectVideoPlayer(videoPlayer);
                goingToShowMessageObject = playingMessageObject;
                NotificationCenter.getInstance(playingMessageObject.currentAccount).postNotificationName(NotificationCenter.messagePlayingGoingToStop, playingMessageObject, true);
            } else {
                long position = videoPlayer.getCurrentPosition();
                if (playingMessageObject != null && playingMessageObject.isVideo() && position > 0 && position != C.TIME_UNSET) {
                    playingMessageObject.audioProgressMs = (int) position;
                    NotificationCenter.getInstance(playingMessageObject.currentAccount).postNotificationName(NotificationCenter.messagePlayingGoingToStop, playingMessageObject, false);
                }
                videoPlayer.releasePlayer(true);
                videoPlayer = null;
            }
            try {
                baseActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (playingMessageObject != null && !transferPlayerToPhotoViewer) {
                AndroidUtilities.cancelRunOnUIThread(setLoadingRunnable);
                FileLoader.getInstance(playingMessageObject.currentAccount).removeLoadingVideo(playingMessageObject.getDocument(), true, false);
            }
        }
        stopProgressTimer();
        lastProgress = 0;
        isPaused = false;
        if (!useFrontSpeaker && !SharedConfig.raiseToSpeak) {
            ChatActivity chat = raiseChat;
            stopRaiseToEarSensors(raiseChat, false);
            raiseChat = chat;
        }
        if (playingMessageObject != null) {
            if (downloadingCurrentMessage) {
                FileLoader.getInstance(playingMessageObject.currentAccount).cancelLoadFile(playingMessageObject.getDocument());
            }
            MessageObject lastFile = playingMessageObject;
            if (notify) {
                playingMessageObject.resetPlayingProgress();
                NotificationCenter.getInstance(lastFile.currentAccount).postNotificationName(NotificationCenter.messagePlayingProgressDidChanged, playingMessageObject.getId(), 0);
            }
            playingMessageObject = null;
            downloadingCurrentMessage = false;
            if (notify) {
                NotificationsController.audioManager.abandonAudioFocus(this);
                hasAudioFocus = 0;
                int index = -1;
                if (voiceMessagesPlaylist != null) {
                    if (byVoiceEnd && (index = voiceMessagesPlaylist.indexOf(lastFile)) >= 0) {
                        voiceMessagesPlaylist.remove(index);
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
                if (voiceMessagesPlaylist != null && index < voiceMessagesPlaylist.size()) {
                    MessageObject nextVoiceMessage = voiceMessagesPlaylist.get(index);
                    playMessage(nextVoiceMessage);
                    if (!nextVoiceMessage.isRoundVideo() && pipRoundVideoView != null) {
                        pipRoundVideoView.close(true);
                        pipRoundVideoView = null;
                    }
                } else {
                    if ((lastFile.isVoice() || lastFile.isRoundVideo()) && lastFile.getId() != 0) {
                        startRecordingIfFromSpeaker();
                    }
                    NotificationCenter.getInstance(lastFile.currentAccount).postNotificationName(NotificationCenter.messagePlayingDidReset, lastFile.getId(), stopService);
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
    }

    public boolean isGoingToShowMessageObject(MessageObject messageObject) {
        return goingToShowMessageObject == messageObject;
    }

    public void resetGoingToShowMessageObject() {
        goingToShowMessageObject = null;
    }

    private boolean isSamePlayingMessage(MessageObject messageObject) {
        return playingMessageObject != null && playingMessageObject.getDialogId() == messageObject.getDialogId() && playingMessageObject.getId() == messageObject.getId() && ((playingMessageObject.eventId == 0) == (messageObject.eventId == 0));
    }

    public boolean seekToProgress(MessageObject messageObject, float progress) {
        if (audioPlayer == null && videoPlayer == null || messageObject == null || playingMessageObject == null || !isSamePlayingMessage(messageObject)) {
            return false;
        }
        try {
            if (audioPlayer != null) {
                long duration = audioPlayer.getDuration();
                if (duration == C.TIME_UNSET) {
                    seekToProgressPending = progress;
                } else {
                    int seekTo = (int) (duration * progress);
                    audioPlayer.seekTo(seekTo);
                    lastProgress = seekTo;
                }
            } else if (videoPlayer != null) {
                videoPlayer.seekTo((long) (videoPlayer.getDuration() * progress));
            }
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
        NotificationCenter.getInstance(messageObject.currentAccount).postNotificationName(NotificationCenter.messagePlayingDidSeek, playingMessageObject.getId(), progress);
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
        if (current.isMusic() && !current.scheduled) {
            if (SharedConfig.shuffleMusic) {
                buildShuffledPlayList();
                currentPlaylistNum = 0;
            }
            if (loadMusic) {
                MediaDataController.getInstance(current.currentAccount).loadMusic(current.getDialogId(), playlist.get(0).getIdWithChannel());
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
        if (playingMessageObject != null) {
            playingMessageObject.resetPlayingProgress();
        }
        playMessage(playlist.get(currentPlaylistNum));
    }

    private void playNextMessageWithoutOrder(boolean byStop) {
        ArrayList<MessageObject> currentPlayList = SharedConfig.shuffleMusic ? shuffledPlaylist : playlist;

        if (byStop && (SharedConfig.repeatMode == 2 || SharedConfig.repeatMode == 1 && currentPlayList.size() == 1) && !forceLoopCurrentPlaylist) {
            cleanupPlayer(false, false);
            MessageObject messageObject = currentPlayList.get(currentPlaylistNum);
            messageObject.audioProgress = 0;
            messageObject.audioProgressSec = 0;
            playMessage(messageObject);
            return;
        }

        boolean last = false;
        if (SharedConfig.playOrderReversed) {
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
        if (last && byStop && SharedConfig.repeatMode == 0 && !forceLoopCurrentPlaylist) {
            if (audioPlayer != null || videoPlayer != null) {
                if (audioPlayer != null) {
                    try {
                        audioPlayer.releasePlayer(true);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    audioPlayer = null;
                } else if (videoPlayer != null) {
                    currentAspectRatioFrameLayout = null;
                    currentTextureViewContainer = null;
                    currentAspectRatioFrameLayoutReady = false;
                    currentTextureView = null;
                    videoPlayer.releasePlayer(true);
                    videoPlayer = null;
                    try {
                        baseActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    AndroidUtilities.cancelRunOnUIThread(setLoadingRunnable);
                    FileLoader.getInstance(playingMessageObject.currentAccount).removeLoadingVideo(playingMessageObject.getDocument(), true, false);
                }
                stopProgressTimer();
                lastProgress = 0;
                isPaused = true;
                playingMessageObject.audioProgress = 0.0f;
                playingMessageObject.audioProgressSec = 0;
                NotificationCenter.getInstance(playingMessageObject.currentAccount).postNotificationName(NotificationCenter.messagePlayingProgressDidChanged, playingMessageObject.getId(), 0);
                NotificationCenter.getInstance(playingMessageObject.currentAccount).postNotificationName(NotificationCenter.messagePlayingPlayStateChanged, playingMessageObject.getId());
            }
            return;
        }
        if (currentPlaylistNum < 0 || currentPlaylistNum >= currentPlayList.size()) {
            return;
        }
        if (playingMessageObject != null) {
            playingMessageObject.resetPlayingProgress();
        }
        playMusicAgain = true;
        playMessage(currentPlayList.get(currentPlaylistNum));
    }

    public void playPreviousMessage() {
        ArrayList<MessageObject> currentPlayList = SharedConfig.shuffleMusic ? shuffledPlaylist : playlist;
        if (currentPlayList.isEmpty() || currentPlaylistNum < 0 || currentPlaylistNum >= currentPlayList.size()) {
            return;
        }
        MessageObject currentSong = currentPlayList.get(currentPlaylistNum);
        if (currentSong.audioProgressSec > 10) {
            seekToProgress(currentSong, 0);
            return;
        }

        if (SharedConfig.playOrderReversed) {
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

    protected void checkIsNextMediaFileDownloaded() {
        if (playingMessageObject == null || !playingMessageObject.isMusic()) {
            return;
        }
        checkIsNextMusicFileDownloaded(playingMessageObject.currentAccount);
    }

    private void checkIsNextVoiceFileDownloaded(int currentAccount) {
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
            FileLoader.getInstance(currentAccount).loadFile(nextAudio.getDocument(), nextAudio, 0, 0);
        }
    }

    private void checkIsNextMusicFileDownloaded(int currentAccount) {
        if (!DownloadController.getInstance(currentAccount).canDownloadNextTrack()) {
            return;
        }
        ArrayList<MessageObject> currentPlayList = SharedConfig.shuffleMusic ? shuffledPlaylist : playlist;
        if (currentPlayList == null || currentPlayList.size() < 2) {
            return;
        }
        int nextIndex;
        if (SharedConfig.playOrderReversed) {
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
        if (nextIndex < 0 || nextIndex >= currentPlayList.size()) {
            return;
        }

        MessageObject nextAudio = currentPlayList.get(nextIndex);
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
            FileLoader.getInstance(currentAccount).loadFile(nextAudio.getDocument(), nextAudio, 0, 0);
        }
    }

    public void setVoiceMessagesPlaylist(ArrayList<MessageObject> playlist, boolean unread) {
        voiceMessagesPlaylist = playlist;
        if (voiceMessagesPlaylist != null) {
            voiceMessagesPlaylistUnread = unread;
            voiceMessagesPlaylistMap = new SparseArray<>();
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
                result = NotificationsController.audioManager.requestAudioFocus(this, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);
            } else {
                result = NotificationsController.audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, neededAudioFocus == 2 ? AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK : AudioManager.AUDIOFOCUS_GAIN);
            }
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                audioFocus = AUDIO_FOCUSED;
            }
        }
    }

    public void setCurrentVideoVisible(boolean visible) {
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
                        pipRoundVideoView.show(baseActivity, () -> cleanupPlayer(true, true));
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
        if (textureView == null) {
            return;
        }
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
            //if (currentTextureViewContainer.getVisibility() != View.VISIBLE) {
            //    currentTextureViewContainer.setVisibility(View.VISIBLE);
            //}
        }
    }

    public boolean hasFlagSecureFragment() {
        return flagSecureFragment != null;
    }

    public void setFlagSecure(BaseFragment parentFragment, boolean set) {
        if (set) {
            try {
                parentFragment.getParentActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            } catch (Exception ignore) {

            }
            flagSecureFragment = parentFragment;
        } else if (flagSecureFragment == parentFragment) {
            try {
                parentFragment.getParentActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            } catch (Exception ignore) {

            }
            flagSecureFragment = null;
        }
    }

    public void setBaseActivity(Activity activity, boolean set) {
        if (set) {
            baseActivity = activity;
        } else if (baseActivity == activity) {
            baseActivity = null;
        }
    }

    public void setFeedbackView(View view, boolean set) {
        if (set) {
            feedbackView = view;
        } else if (feedbackView == view) {
            feedbackView = null;
        }
    }

    public void setPlaybackSpeed(float speed) {
        currentPlaybackSpeed = speed;
        if (audioPlayer != null) {
            audioPlayer.setPlaybackSpeed(currentPlaybackSpeed);
        } else if (videoPlayer != null) {
            videoPlayer.setPlaybackSpeed(currentPlaybackSpeed);
        }
        MessagesController.getGlobalMainSettings().edit().putFloat("playbackSpeed", speed).commit();
    }

    public float getPlaybackSpeed() {
        return currentPlaybackSpeed;
    }

    private void updateVideoState(MessageObject messageObject, int[] playCount, boolean destroyAtEnd, boolean playWhenReady, int playbackState) {
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
            playerWasReady = true;
            if (playingMessageObject != null && (playingMessageObject.isVideo() || playingMessageObject.isRoundVideo())) {
                AndroidUtilities.cancelRunOnUIThread(setLoadingRunnable);
                FileLoader.getInstance(messageObject.currentAccount).removeLoadingVideo(playingMessageObject.getDocument(), true, false);
            }
            currentAspectRatioFrameLayoutReady = true;
        } else if (playbackState == ExoPlayer.STATE_BUFFERING) {
            if (playWhenReady && playingMessageObject != null && (playingMessageObject.isVideo() || playingMessageObject.isRoundVideo())) {
                if (playerWasReady) {
                    setLoadingRunnable.run();
                } else {
                    AndroidUtilities.runOnUIThread(setLoadingRunnable, 1000);
                }
            }
        } else if (videoPlayer.isPlaying() && playbackState == ExoPlayer.STATE_ENDED) {
            if (playingMessageObject.isVideo() && !destroyAtEnd && (playCount == null || playCount[0] < 4)) {
                videoPlayer.seekTo(0);
                if (playCount != null) {
                    playCount[0]++;
                }
            } else {
                cleanupPlayer(true, true, true, false);
            }
        }
    }

    public void injectVideoPlayer(VideoPlayer player, MessageObject messageObject) {
        if (player == null || messageObject == null) {
            return;
        }
        FileLoader.getInstance(messageObject.currentAccount).setLoadingVideoForPlayer(messageObject.getDocument(), true);
        playerWasReady = false;
        boolean destroyAtEnd = true;
        int[] playCount = null;
        playlist.clear();
        shuffledPlaylist.clear();
        videoPlayer = player;
        playingMessageObject = messageObject;
        videoPlayer.setDelegate(new VideoPlayer.VideoPlayerDelegate() {
            @Override
            public void onStateChanged(boolean playWhenReady, int playbackState) {
                updateVideoState(messageObject, playCount, destroyAtEnd, playWhenReady, playbackState);
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
                    currentTextureViewContainer.setTag(1);
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
                                pipRoundVideoView.show(baseActivity, () -> cleanupPlayer(true, true));
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
                } else if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isInjectingVideoPlayer()) {
                    PhotoViewer.getInstance().injectVideoPlayerSurface(surfaceTexture);
                    return true;
                }
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

            }
        });
        currentAspectRatioFrameLayoutReady = false;
        if (currentTextureView != null) {
            videoPlayer.setTextureView(currentTextureView);
        }



        checkAudioFocus(messageObject);
        setPlayerVolume();

        isPaused = false;
        lastProgress = 0;
        playingMessageObject = messageObject;
        if (!SharedConfig.raiseToSpeak) {
            startRaiseToEarSensors(raiseChat);
        }
        startProgressTimer(playingMessageObject);
        NotificationCenter.getInstance(messageObject.currentAccount).postNotificationName(NotificationCenter.messagePlayingDidStart, messageObject);

        /*try {
            if (playingMessageObject.audioProgress != 0) {
                long duration = videoPlayer.getDuration();
                if (duration == C.TIME_UNSET) {
                    duration = (long) playingMessageObject.getDuration() * 1000;
                }
                int seekTo = (int) (duration * playingMessageObject.audioProgress);
                if (playingMessageObject.audioProgressMs != 0) {
                    seekTo = playingMessageObject.audioProgressMs;
                    playingMessageObject.audioProgressMs = 0;
                }
                videoPlayer.seekTo(seekTo);
            }
        } catch (Exception e2) {
            playingMessageObject.audioProgress = 0;
            playingMessageObject.audioProgressSec = 0;
            NotificationCenter.getInstance(messageObject.currentAccount).postNotificationName(NotificationCenter.messagePlayingProgressDidChanged, playingMessageObject.getId(), 0);
            FileLog.e(e2);
        }*/
    }

    public boolean playMessage(final MessageObject messageObject) {
        if (messageObject == null) {
            return false;
        }
        if ((audioPlayer != null || videoPlayer != null) && isSamePlayingMessage(messageObject)) {
            if (isPaused) {
                resumeAudio(messageObject);
            }
            if (!SharedConfig.raiseToSpeak) {
                startRaiseToEarSensors(raiseChat);
            }
            return true;
        }
        if (!messageObject.isOut() && messageObject.isContentUnread()) {
            MessagesController.getInstance(messageObject.currentAccount).markMessageContentAsRead(messageObject);
        }
        boolean notify = !playMusicAgain;
        if (playingMessageObject != null) {
            notify = false;
            if (!playMusicAgain) {
                playingMessageObject.resetPlayingProgress();
            }
        }
        cleanupPlayer(notify, false);
        playMusicAgain = false;
        seekToProgressPending = 0;
        File file = null;
        boolean exists = false;
        if (messageObject.messageOwner.attachPath != null && messageObject.messageOwner.attachPath.length() > 0) {
            file = new File(messageObject.messageOwner.attachPath);
            exists = file.exists();
            if (!exists) {
                file = null;
            }
        }
        final File cacheFile = file != null ? file : FileLoader.getPathToMessage(messageObject.messageOwner);
        boolean canStream = SharedConfig.streamMedia && (messageObject.isMusic() || messageObject.isRoundVideo() || messageObject.isVideo() && messageObject.canStreamVideo()) && (int) messageObject.getDialogId() != 0;
        if (cacheFile != null && cacheFile != file && !(exists = cacheFile.exists()) && !canStream) {
            FileLoader.getInstance(messageObject.currentAccount).loadFile(messageObject.getDocument(), messageObject, 0, 0);
            downloadingCurrentMessage = true;
            isPaused = false;
            lastProgress = 0;
            audioInfo = null;
            playingMessageObject = messageObject;
            if (playingMessageObject.isMusic()) {
                Intent intent = new Intent(ApplicationLoader.applicationContext, MusicPlayerService.class);
                try {
                    /*if (Build.VERSION.SDK_INT >= 26) {
                        ApplicationLoader.applicationContext.startForegroundService(intent);
                    } else {*/
                        ApplicationLoader.applicationContext.startService(intent);
                    //}
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            } else {
                Intent intent = new Intent(ApplicationLoader.applicationContext, MusicPlayerService.class);
                ApplicationLoader.applicationContext.stopService(intent);
            }
            NotificationCenter.getInstance(playingMessageObject.currentAccount).postNotificationName(NotificationCenter.messagePlayingPlayStateChanged, playingMessageObject.getId());
            return true;
        } else {
            downloadingCurrentMessage = false;
        }
        if (messageObject.isMusic()) {
            checkIsNextMusicFileDownloaded(messageObject.currentAccount);
        } else {
            checkIsNextVoiceFileDownloaded(messageObject.currentAccount);
        }

        if (currentAspectRatioFrameLayout != null) {
            isDrawingWasReady = false;
            currentAspectRatioFrameLayout.setDrawingReady(false);
        }
        boolean isVideo = messageObject.isVideo();
        if (messageObject.isRoundVideo() || isVideo) {
            FileLoader.getInstance(messageObject.currentAccount).setLoadingVideoForPlayer(messageObject.getDocument(), true);
            playerWasReady = false;
            boolean destroyAtEnd = !isVideo || messageObject.messageOwner.to_id.channel_id == 0 && messageObject.audioProgress <= 0.1f;
            int[] playCount = isVideo && messageObject.getDuration() <= 30 ? new int[]{1} : null;
            playlist.clear();
            shuffledPlaylist.clear();
            videoPlayer = new VideoPlayer();
            videoPlayer.setDelegate(new VideoPlayer.VideoPlayerDelegate() {
                @Override
                public void onStateChanged(boolean playWhenReady, int playbackState) {
                    updateVideoState(messageObject, playCount, destroyAtEnd, playWhenReady, playbackState);
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
                        currentTextureViewContainer.setTag(1);
                        //if (currentTextureViewContainer != null && currentTextureViewContainer.getVisibility() != View.VISIBLE) {
                        //    currentTextureViewContainer.setVisibility(View.VISIBLE);
                        //}
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
                                    pipRoundVideoView.show(baseActivity, () -> cleanupPlayer(true, true));
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
                    } else if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isInjectingVideoPlayer()) {
                        PhotoViewer.getInstance().injectVideoPlayerSurface(surfaceTexture);
                        return true;
                    }
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

                }
            });
            currentAspectRatioFrameLayoutReady = false;
            if (pipRoundVideoView != null || !MessagesController.getInstance(messageObject.currentAccount).isDialogVisible(messageObject.getDialogId(), messageObject.scheduled)) {
                if (pipRoundVideoView == null) {
                    try {
                        pipRoundVideoView = new PipRoundVideoView();
                        pipRoundVideoView.show(baseActivity, () -> cleanupPlayer(true, true));
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

            if (exists) {
                if (!messageObject.mediaExists && cacheFile != file) {
                    AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(messageObject.currentAccount).postNotificationName(NotificationCenter.fileDidLoad, FileLoader.getAttachFileName(messageObject.getDocument()), cacheFile));
                }
                videoPlayer.preparePlayer(Uri.fromFile(cacheFile), "other");
            } else {
                try {
                    int reference = FileLoader.getInstance(messageObject.currentAccount).getFileReference(messageObject);
                    TLRPC.Document document = messageObject.getDocument();
                    String params = "?account=" + messageObject.currentAccount +
                            "&id=" + document.id +
                            "&hash=" + document.access_hash +
                            "&dc=" + document.dc_id +
                            "&size=" + document.size +
                            "&mime=" + URLEncoder.encode(document.mime_type, "UTF-8") +
                            "&rid=" + reference +
                            "&name=" + URLEncoder.encode(FileLoader.getDocumentFileName(document), "UTF-8") +
                            "&reference=" + Utilities.bytesToHex(document.file_reference != null ? document.file_reference : new byte[0]);
                    Uri uri = Uri.parse("tg://" + messageObject.getFileName() + params);
                    videoPlayer.preparePlayer(uri, "other");
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (messageObject.isRoundVideo()) {
                videoPlayer.setStreamType(useFrontSpeaker ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);
                if (currentPlaybackSpeed > 1.0f) {
                    videoPlayer.setPlaybackSpeed(currentPlaybackSpeed);
                }
            } else {
                videoPlayer.setStreamType(AudioManager.STREAM_MUSIC);
            }
        } else {
            if (pipRoundVideoView != null) {
                pipRoundVideoView.close(true);
                pipRoundVideoView = null;
            }
            try {
                audioPlayer = new VideoPlayer();
                audioPlayer.setDelegate(new VideoPlayer.VideoPlayerDelegate() {
                    @Override
                    public void onStateChanged(boolean playWhenReady, int playbackState) {
                        if (playbackState == ExoPlayer.STATE_ENDED || (playbackState == ExoPlayer.STATE_IDLE || playbackState == ExoPlayer.STATE_BUFFERING) && playWhenReady && messageObject.audioProgress >= 0.999f) {
                            if (!playlist.isEmpty() && (playlist.size() > 1 || !messageObject.isVoice())) {
                                playNextMessageWithoutOrder(true);
                            } else {
                                cleanupPlayer(true, true, messageObject != null && messageObject.isVoice(), false);
                            }
                        } else if (seekToProgressPending != 0 && (playbackState == ExoPlayer.STATE_READY || playbackState == ExoPlayer.STATE_IDLE)) {
                            int seekTo = (int) (audioPlayer.getDuration() * seekToProgressPending);
                            audioPlayer.seekTo(seekTo);
                            lastProgress = seekTo;
                            seekToProgressPending = 0;
                        }
                    }

                    @Override
                    public void onError(Exception e) {

                    }

                    @Override
                    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {

                    }

                    @Override
                    public void onRenderedFirstFrame() {

                    }

                    @Override
                    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

                    }

                    @Override
                    public boolean onSurfaceDestroyed(SurfaceTexture surfaceTexture) {
                        return false;
                    }
                });
                if (exists) {
                    if (!messageObject.mediaExists && cacheFile != file) {
                        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(messageObject.currentAccount).postNotificationName(NotificationCenter.fileDidLoad, FileLoader.getAttachFileName(messageObject.getDocument()), cacheFile));
                    }
                    audioPlayer.preparePlayer(Uri.fromFile(cacheFile), "other");
                } else {
                    int reference = FileLoader.getInstance(messageObject.currentAccount).getFileReference(messageObject);
                    TLRPC.Document document = messageObject.getDocument();
                    String params = "?account=" + messageObject.currentAccount +
                            "&id=" + document.id +
                            "&hash=" + document.access_hash +
                            "&dc=" + document.dc_id +
                            "&size=" + document.size +
                            "&mime=" + URLEncoder.encode(document.mime_type, "UTF-8") +
                            "&rid=" + reference +
                            "&name=" + URLEncoder.encode(FileLoader.getDocumentFileName(document), "UTF-8") +
                            "&reference=" + Utilities.bytesToHex(document.file_reference != null ? document.file_reference : new byte[0]);
                    Uri uri = Uri.parse("tg://" + messageObject.getFileName() + params);
                    audioPlayer.preparePlayer(uri, "other");
                }
                if (messageObject.isVoice()) {
                    if (currentPlaybackSpeed > 1.0f) {
                        audioPlayer.setPlaybackSpeed(currentPlaybackSpeed);
                    }
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
                audioPlayer.setStreamType(useFrontSpeaker ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);
                audioPlayer.play();
            } catch (Exception e) {
                FileLog.e(e);
                NotificationCenter.getInstance(messageObject.currentAccount).postNotificationName(NotificationCenter.messagePlayingPlayStateChanged, playingMessageObject != null ? playingMessageObject.getId() : 0);
                if (audioPlayer != null) {
                    audioPlayer.releasePlayer(true);
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
        playingMessageObject = messageObject;
        if (!SharedConfig.raiseToSpeak) {
            startRaiseToEarSensors(raiseChat);
        }
        startProgressTimer(playingMessageObject);
        NotificationCenter.getInstance(messageObject.currentAccount).postNotificationName(NotificationCenter.messagePlayingDidStart, messageObject);

        if (videoPlayer != null) {
            try {
                if (playingMessageObject.audioProgress != 0) {
                    long duration = videoPlayer.getDuration();
                    if (duration == C.TIME_UNSET) {
                        duration = (long) playingMessageObject.getDuration() * 1000;
                    }
                    int seekTo = (int) (duration * playingMessageObject.audioProgress);
                    if (playingMessageObject.audioProgressMs != 0) {
                        seekTo = playingMessageObject.audioProgressMs;
                        playingMessageObject.audioProgressMs = 0;
                    }
                    videoPlayer.seekTo(seekTo);
                }
            } catch (Exception e2) {
                playingMessageObject.audioProgress = 0;
                playingMessageObject.audioProgressSec = 0;
                NotificationCenter.getInstance(messageObject.currentAccount).postNotificationName(NotificationCenter.messagePlayingProgressDidChanged, playingMessageObject.getId(), 0);
                FileLog.e(e2);
            }
            videoPlayer.play();
        } else if (audioPlayer != null) {
            try {
                if (playingMessageObject.audioProgress != 0) {
                    long duration = audioPlayer.getDuration();
                    if (duration == C.TIME_UNSET) {
                        duration = (long) playingMessageObject.getDuration() * 1000;
                    }
                    int seekTo = (int) (duration * playingMessageObject.audioProgress);
                    audioPlayer.seekTo(seekTo);
                }
            } catch (Exception e2) {
                playingMessageObject.resetPlayingProgress();
                NotificationCenter.getInstance(messageObject.currentAccount).postNotificationName(NotificationCenter.messagePlayingProgressDidChanged, playingMessageObject.getId(), 0);
                FileLog.e(e2);
            }
        }

        if (playingMessageObject != null && playingMessageObject.isMusic()) {
            Intent intent = new Intent(ApplicationLoader.applicationContext, MusicPlayerService.class);
            try {
                /*if (Build.VERSION.SDK_INT >= 26) {
                    ApplicationLoader.applicationContext.startForegroundService(intent);
                } else {*/
                    ApplicationLoader.applicationContext.startService(intent);
                //}
            } catch (Throwable e) {
                FileLog.e(e);
            }
        } else {
            Intent intent = new Intent(ApplicationLoader.applicationContext, MusicPlayerService.class);
            ApplicationLoader.applicationContext.stopService(intent);
        }

        return true;
    }

    public AudioInfo getAudioInfo() {
        return audioInfo;
    }

    public void toggleShuffleMusic(int type) {
        boolean oldShuffle = SharedConfig.shuffleMusic;
        SharedConfig.toggleShuffleMusic(type);
        if (oldShuffle != SharedConfig.shuffleMusic) {
            if (SharedConfig.shuffleMusic) {
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

    public boolean isCurrentPlayer(VideoPlayer player) {
        return videoPlayer == player || audioPlayer == player;
    }

    public boolean pauseMessage(MessageObject messageObject) {
        if (audioPlayer == null && videoPlayer == null || messageObject == null || playingMessageObject == null || !isSamePlayingMessage(messageObject)) {
            return false;
        }
        stopProgressTimer();
        try {
            if (audioPlayer != null) {
                audioPlayer.pause();
            } else if (videoPlayer != null) {
                videoPlayer.pause();
            }
            isPaused = true;
            NotificationCenter.getInstance(playingMessageObject.currentAccount).postNotificationName(NotificationCenter.messagePlayingPlayStateChanged, playingMessageObject.getId());
        } catch (Exception e) {
            FileLog.e(e);
            isPaused = false;
            return false;
        }
        return true;
    }

    public boolean resumeAudio(MessageObject messageObject) {
        if (audioPlayer == null && videoPlayer == null || messageObject == null || playingMessageObject == null || !isSamePlayingMessage(messageObject)) {
            return false;
        }

        try {
            startProgressTimer(playingMessageObject);
            if (audioPlayer != null) {
                audioPlayer.play();
            } else if (videoPlayer != null) {
                videoPlayer.play();
            }
            checkAudioFocus(messageObject);
            isPaused = false;
            NotificationCenter.getInstance(playingMessageObject.currentAccount).postNotificationName(NotificationCenter.messagePlayingPlayStateChanged, playingMessageObject.getId());
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
        return true;
    }

    public boolean isVideoDrawingReady() {
        return currentAspectRatioFrameLayout != null && currentAspectRatioFrameLayout.isDrawingReady();
    }

    public ArrayList<MessageObject> getPlaylist() {
        return playlist;
    }

    public boolean isPlayingMessage(MessageObject messageObject) {
        if (audioPlayer == null && videoPlayer == null || messageObject == null || playingMessageObject == null) {
            return false;
        }
        if (playingMessageObject.eventId != 0 && playingMessageObject.eventId == messageObject.eventId) {
            return !downloadingCurrentMessage;
        }
        if (isSamePlayingMessage(messageObject)) {
            return !downloadingCurrentMessage;
        }
        //
        return false;
    }

    public boolean isPlayingMessageAndReadyToDraw(MessageObject messageObject) {
        return isDrawingWasReady && isPlayingMessage(messageObject);
    }

    public boolean isMessagePaused() {
        return isPaused || downloadingCurrentMessage;
    }

    public boolean isDownloadingCurrentMessage() {
        return downloadingCurrentMessage;
    }

    public void setReplyingMessage(MessageObject reply_to_msg) {
        recordReplyingMessageObject = reply_to_msg;
    }

    public void startRecording(final int currentAccount, final long dialog_id, final MessageObject reply_to_msg, int guid) {
        boolean paused = false;
        if (playingMessageObject != null && isPlayingMessage(playingMessageObject) && !isMessagePaused()) {
            paused = true;
            pauseMessage(playingMessageObject);
        }

        try {
            feedbackView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignore) {

        }

        recordQueue.postRunnable(recordStartRunnable = () -> {
            if (audioRecorder != null) {
                AndroidUtilities.runOnUIThread(() -> {
                    recordStartRunnable = null;
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.recordStartError, guid);
                });
                return;
            }

            sendAfterDone = 0;
            recordingAudio = new TLRPC.TL_document();
            recordingGuid = guid;
            recordingAudio.file_reference = new byte[0];
            recordingAudio.dc_id = Integer.MIN_VALUE;
            recordingAudio.id = SharedConfig.getLastLocalId();
            recordingAudio.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
            recordingAudio.mime_type = "audio/ogg";
            recordingAudio.file_reference = new byte[0];
            SharedConfig.saveConfig();

            recordingAudioFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), FileLoader.getAttachFileName(recordingAudio));

            try {
                if (startRecord(recordingAudioFile.getAbsolutePath()) == 0) {
                    AndroidUtilities.runOnUIThread(() -> {
                        recordStartRunnable = null;
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.recordStartError, guid);
                    });
                    return;
                }

                audioRecorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, recordBufferSize * 10);
                recordStartTime = System.currentTimeMillis();
                recordTimeCount = 0;
                samplesCount = 0;
                recordDialogId = dialog_id;
                recordingCurrentAccount = currentAccount;
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

                AndroidUtilities.runOnUIThread(() -> {
                    recordStartRunnable = null;
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.recordStartError, guid);
                });
                return;
            }

            recordQueue.postRunnable(recordRunnable);
            AndroidUtilities.runOnUIThread(() -> {
                recordStartRunnable = null;
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.recordStarted, guid);
            });
        }, paused ? 500 : 50);
    }

    public void generateWaveform(MessageObject messageObject) {
        final String id = messageObject.getId() + "_" + messageObject.getDialogId();
        final String path = FileLoader.getPathToMessage(messageObject.messageOwner).getAbsolutePath();
        if (generatingWaveform.containsKey(id)) {
            return;
        }
        generatingWaveform.put(id, messageObject);
        Utilities.globalQueue.postRunnable(() -> {
            final byte[] waveform = getWaveform(path);
            AndroidUtilities.runOnUIThread(() -> {
                MessageObject messageObject1 = generatingWaveform.remove(id);
                if (messageObject1 == null) {
                    return;
                }
                if (waveform != null) {
                    for (int a = 0; a < messageObject1.getDocument().attributes.size(); a++) {
                        TLRPC.DocumentAttribute attribute = messageObject1.getDocument().attributes.get(a);
                        if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                            attribute.waveform = waveform;
                            attribute.flags |= 4;
                            break;
                        }
                    }
                    TLRPC.TL_messages_messages messagesRes = new TLRPC.TL_messages_messages();
                    messagesRes.messages.add(messageObject1.messageOwner);
                    MessagesStorage.getInstance(messageObject1.currentAccount).putMessages(messagesRes, messageObject1.getDialogId(), -1, 0, false, messageObject.scheduled);
                    ArrayList<MessageObject> arrayList = new ArrayList<>();
                    arrayList.add(messageObject1);
                    NotificationCenter.getInstance(messageObject1.currentAccount).postNotificationName(NotificationCenter.replaceMessagesObjects, messageObject1.getDialogId(), arrayList);
                }
            });
        });
    }

    private void stopRecordingInternal(final int send, boolean notify, int scheduleDate) {
        if (send != 0) {
            final TLRPC.TL_document audioToSend = recordingAudio;
            final File recordingAudioFileToSend = recordingAudioFile;
            fileEncodingQueue.postRunnable(() -> {
                stopRecord();
                AndroidUtilities.runOnUIThread(() -> {
                    audioToSend.date = ConnectionsManager.getInstance(recordingCurrentAccount).getCurrentTime();
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
                            SendMessagesHelper.getInstance(recordingCurrentAccount).sendMessage(audioToSend, null, recordingAudioFileToSend.getAbsolutePath(), recordDialogId, recordReplyingMessageObject, null, null, null, null, notify, scheduleDate, 0, null);
                        }
                        NotificationCenter.getInstance(recordingCurrentAccount).postNotificationName(NotificationCenter.audioDidSent, recordingGuid, send == 2 ? audioToSend : null, send == 2 ? recordingAudioFileToSend.getAbsolutePath() : null);
                    } else {
                        NotificationCenter.getInstance(recordingCurrentAccount).postNotificationName(NotificationCenter.audioRecordTooShort, recordingGuid, false);
                        recordingAudioFileToSend.delete();
                    }
                });
            });
        } else {
            if (recordingAudioFile != null) {
                recordingAudioFile.delete();
            }
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

    public void stopRecording(final int send, boolean notify, int scheduleDate) {
        if (recordStartRunnable != null) {
            recordQueue.cancelRunnable(recordStartRunnable);
            recordStartRunnable = null;
        }
        recordQueue.postRunnable(() -> {
            if (sendAfterDone == 3) {
                sendAfterDone = 0;
                stopRecordingInternal(send, notify, scheduleDate);
                return;
            }
            if (audioRecorder == null) {
                return;
            }
            try {
                sendAfterDone = send;
                sendAfterDoneNotify = notify;
                sendAfterDoneScheduleDate = scheduleDate;
                audioRecorder.stop();
            } catch (Exception e) {
                FileLog.e(e);
                if (recordingAudioFile != null) {
                    recordingAudioFile.delete();
                }
            }
            if (send == 0) {
                stopRecordingInternal(0, false, 0);
            }
            try {
                feedbackView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignore) {

            }
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(recordingCurrentAccount).postNotificationName(NotificationCenter.recordStopped, recordingGuid, send == 2 ? 1 : 0));
        });
    }

    public static void saveFile(String fullPath, Context context, final int type, final String name, final String mime) {
        if (fullPath == null) {
            return;
        }

        File file = null;
        if (fullPath != null && fullPath.length() != 0) {
            file = new File(fullPath);
            if (!file.exists() || AndroidUtilities.isInternalUri(Uri.fromFile(file))) {
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
                    progressDialog.setOnCancelListener(dialog -> cancelled[0] = true);
                    progressDialog.show();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            final AlertDialog finalProgress = progressDialog;

            new Thread(() -> {
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
                    boolean result = true;
                    long lastProgress = System.currentTimeMillis() - 500;
                    try (FileChannel source = new FileInputStream(sourceFile).getChannel(); FileChannel destination = new FileOutputStream(destFile).getChannel()) {
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
                                    AndroidUtilities.runOnUIThread(() -> {
                                        try {
                                            finalProgress.setProgress(progress);
                                        } catch (Exception e) {
                                            FileLog.e(e);
                                        }
                                    });
                                }
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                        result = false;
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
                    AndroidUtilities.runOnUIThread(() -> {
                        try {
                            finalProgress.dismiss();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    });
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
            try (Cursor cursor = ApplicationLoader.applicationContext.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } catch (Exception e) {
                FileLog.e(e);
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
            String name = FileLoader.fixFileName(getFileName(uri));
            if (name == null) {
                int id = SharedConfig.getLastLocalId();
                SharedConfig.saveConfig();
                name = String.format(Locale.US, "%d.%s", id, ext);
            }
            File f = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "sharing/");
            f.mkdirs();
            f = new File(f, name);
            if (AndroidUtilities.isInternalUri(Uri.fromFile(f))) {
                return null;
            }
            inputStream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
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

    public static void loadGalleryPhotosAlbums(final int guid) {
        Thread thread = new Thread(() -> {
            final ArrayList<AlbumEntry> mediaAlbumsSorted = new ArrayList<>();
            final ArrayList<AlbumEntry> photoAlbumsSorted = new ArrayList<>();
            SparseArray<AlbumEntry> mediaAlbums = new SparseArray<>();
            SparseArray<AlbumEntry> photoAlbums = new SparseArray<>();
            AlbumEntry allPhotosAlbum = null;
            AlbumEntry allVideosAlbum = null;
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
                    cursor = MediaStore.Images.Media.query(ApplicationLoader.applicationContext.getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projectionPhotos, null, null, Build.VERSION.SDK_INT > 28 ? MediaStore.Images.Media.DATE_MODIFIED : MediaStore.Images.Media.DATE_TAKEN + " DESC");
                    if (cursor != null) {
                        int imageIdColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID);
                        int bucketIdColumn = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID);
                        int bucketNameColumn = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
                        int dataColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                        int dateColumn = cursor.getColumnIndex(Build.VERSION.SDK_INT > 28 ? MediaStore.Images.Media.DATE_MODIFIED : MediaStore.Images.Media.DATE_TAKEN);
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
                    cursor = MediaStore.Images.Media.query(ApplicationLoader.applicationContext.getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projectionVideo, null, null, Build.VERSION.SDK_INT > 28 ? MediaStore.Video.Media.DATE_MODIFIED : MediaStore.Video.Media.DATE_TAKEN + " DESC");
                    if (cursor != null) {
                        int imageIdColumn = cursor.getColumnIndex(MediaStore.Video.Media._ID);
                        int bucketIdColumn = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID);
                        int bucketNameColumn = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME);
                        int dataColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATA);
                        int dateColumn = cursor.getColumnIndex(Build.VERSION.SDK_INT > 28 ? MediaStore.Video.Media.DATE_MODIFIED : MediaStore.Video.Media.DATE_TAKEN);
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

                            if (allVideosAlbum == null) {
                                allVideosAlbum = new AlbumEntry(0, LocaleController.getString("AllVideos", R.string.AllVideos), photoEntry);
                                allVideosAlbum.videoOnly = true;
                                int index = 0;
                                if (allMediaAlbum != null) {
                                    index++;
                                }
                                if (allPhotosAlbum != null) {
                                    index++;
                                }
                                mediaAlbumsSorted.add(index, allVideosAlbum);
                            }
                            if (allMediaAlbum == null) {
                                allMediaAlbum = new AlbumEntry(0, LocaleController.getString("AllMedia", R.string.AllMedia), photoEntry);
                                mediaAlbumsSorted.add(0, allMediaAlbum);
                            }
                            allVideosAlbum.addPhoto(photoEntry);
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
                Collections.sort(mediaAlbumsSorted.get(a).photos, (o1, o2) -> {
                    if (o1.dateTaken < o2.dateTaken) {
                        return 1;
                    } else if (o1.dateTaken > o2.dateTaken) {
                        return -1;
                    }
                    return 0;
                });
            }
            broadcastNewPhotos(guid, mediaAlbumsSorted, photoAlbumsSorted, mediaCameraAlbumId, allMediaAlbum, allPhotosAlbum, allVideosAlbum, 0);
        });
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private static void broadcastNewPhotos(final int guid, final ArrayList<AlbumEntry> mediaAlbumsSorted, final ArrayList<AlbumEntry> photoAlbumsSorted, final Integer cameraAlbumIdFinal, final AlbumEntry allMediaAlbumFinal, final AlbumEntry allPhotosAlbumFinal, final AlbumEntry allVideosAlbumFinal, int delay) {
        if (broadcastPhotosRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(broadcastPhotosRunnable);
        }
        AndroidUtilities.runOnUIThread(broadcastPhotosRunnable = () -> {
            if (PhotoViewer.getInstance().isVisible()) {
                broadcastNewPhotos(guid, mediaAlbumsSorted, photoAlbumsSorted, cameraAlbumIdFinal, allMediaAlbumFinal, allPhotosAlbumFinal, allVideosAlbumFinal, 1000);
                return;
            }
            allMediaAlbums = mediaAlbumsSorted;
            allPhotoAlbums = photoAlbumsSorted;
            broadcastPhotosRunnable = null;
            allPhotosAlbumEntry = allPhotosAlbumFinal;
            allMediaAlbumEntry = allMediaAlbumFinal;
            allVideosAlbumEntry = allVideosAlbumFinal;
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.albumsDidLoad, guid, mediaAlbumsSorted, photoAlbumsSorted, cameraAlbumIdFinal);
            }
        }, delay);
    }

    public void scheduleVideoConvert(MessageObject messageObject) {
        scheduleVideoConvert(messageObject, false);
    }

    public boolean scheduleVideoConvert(MessageObject messageObject, boolean isEmpty) {
        if (messageObject == null || messageObject.videoEditedInfo == null) {
            return false;
        }
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
                for (int a = 0; a < videoConvertQueue.size(); a++) {
                    MessageObject object = videoConvertQueue.get(a);
                    if (object.getId() == messageObject.getId() && object.currentAccount == messageObject.currentAccount) {
                        if (a == 0) {
                            synchronized (videoConvertSync) {
                                cancelCurrentVideoConversion = true;
                            }
                        } else {
                            videoConvertQueue.remove(a);
                        }
                        break;
                    }
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
            intent.putExtra("currentAccount", messageObject.currentAccount);
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
                try {
                    ApplicationLoader.applicationContext.startService(intent);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
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
                    String name = lastCodecInfo.getName();
                    if (name != null) {
                        if (!name.equals("OMX.SEC.avc.enc")) {
                            return lastCodecInfo;
                        } else if (name.equals("OMX.SEC.AVC.Encoder")) {
                            return lastCodecInfo;
                        }
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

    private int findTrack(MediaExtractor extractor, boolean audio) {
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

    private void didWriteData(final MessageObject messageObject, final File file, final boolean last, long availableSize, final boolean error) {
        final boolean firstWrite = videoConvertFirstWrite;
        if (firstWrite) {
            videoConvertFirstWrite = false;
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (error || last) {
                synchronized (videoConvertSync) {
                    cancelCurrentVideoConversion = false;
                }
                videoConvertQueue.remove(messageObject);
                startVideoConvertFromQueue();
            }
            if (error) {
                NotificationCenter.getInstance(messageObject.currentAccount).postNotificationName(NotificationCenter.filePreparingFailed, messageObject, file.toString());
            } else {
                if (firstWrite) {
                    NotificationCenter.getInstance(messageObject.currentAccount).postNotificationName(NotificationCenter.filePreparingStarted, messageObject, file.toString());
                }
                NotificationCenter.getInstance(messageObject.currentAccount).postNotificationName(NotificationCenter.fileNewChunkAvailable, messageObject, file.toString(), availableSize, last ? file.length() : 0);
            }
        });
    }

    private long readAndWriteTracks(final MessageObject messageObject, MediaExtractor extractor, MP4Builder mediaMuxer, MediaCodec.BufferInfo info, long start, long end, File file, boolean needAudio) throws Exception {
        int videoTrackIndex = findTrack(extractor, false);
        int audioTrackIndex = needAudio ? findTrack(extractor, true) : -1;
        int muxerVideoTrackIndex = -1;
        int muxerAudioTrackIndex = -1;
        boolean inputDone = false;
        int maxBufferSize = 0;
        if (videoTrackIndex >= 0) {
            extractor.selectTrack(videoTrackIndex);
            MediaFormat trackFormat = extractor.getTrackFormat(videoTrackIndex);
            muxerVideoTrackIndex = mediaMuxer.addTrack(trackFormat, false);
            maxBufferSize = trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
            if (start > 0) {
                extractor.seekTo(start, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            } else {
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            }
        }
        if (audioTrackIndex >= 0) {
            extractor.selectTrack(audioTrackIndex);
            MediaFormat trackFormat = extractor.getTrackFormat(audioTrackIndex);
            muxerAudioTrackIndex = mediaMuxer.addTrack(trackFormat, true);
            maxBufferSize = Math.max(trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE), maxBufferSize);
            if (start > 0) {
                extractor.seekTo(start, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            } else {
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            }
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(maxBufferSize);
        if (audioTrackIndex >= 0 || videoTrackIndex >= 0) {
            long startTime = -1;
            checkConversionCanceled();
            while (!inputDone) {
                checkConversionCanceled();
                boolean eof = false;
                int muxerTrackIndex;
                info.size = extractor.readSampleData(buffer, 0);
                int index = extractor.getSampleTrackIndex();
                if (index == videoTrackIndex) {
                    muxerTrackIndex = muxerVideoTrackIndex;
                } else if (index == audioTrackIndex) {
                    muxerTrackIndex = muxerAudioTrackIndex;
                } else {
                    muxerTrackIndex = -1;
                }
                if (muxerTrackIndex != -1) {
                    if (Build.VERSION.SDK_INT < 21) {
                        buffer.position(0);
                        buffer.limit(info.size);
                    }
                    if (index != audioTrackIndex) {
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
                        if (index == videoTrackIndex && start > 0 && startTime == -1) {
                            startTime = info.presentationTimeUs;
                        }
                        if (end < 0 || info.presentationTimeUs < end) {
                            info.offset = 0;
                            info.flags = extractor.getSampleFlags();
                            long availableSize = mediaMuxer.writeSampleData(muxerTrackIndex, buffer, info, false);
                            if (availableSize != 0) {
                                didWriteData(messageObject, file, false, availableSize, false);
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
            if (videoTrackIndex >= 0) {
                extractor.unselectTrack(videoTrackIndex);
            }
            if (audioTrackIndex >= 0) {
                extractor.unselectTrack(audioTrackIndex);
            }
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
            new Thread(() -> {
                try {
                    VideoConvertRunnable wrapper = new VideoConvertRunnable(obj);
                    Thread th = new Thread(wrapper, "VideoConvertRunnable");
                    th.start();
                    th.join();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }).start();
        }
    }

    private void checkConversionCanceled() {
        boolean cancelConversion;
        synchronized (videoConvertSync) {
            cancelConversion = cancelCurrentVideoConversion;
        }
        if (cancelConversion) {
            throw new RuntimeException("canceled conversion");
        }
    }

    private boolean convertVideo(final MessageObject messageObject) {
        if (messageObject == null || messageObject.videoEditedInfo == null) {
            return false;
        }
        String videoPath = messageObject.videoEditedInfo.originalPath;
        long startTime = messageObject.videoEditedInfo.startTime;
        long endTime = messageObject.videoEditedInfo.endTime;
        int resultWidth = messageObject.videoEditedInfo.resultWidth;
        int resultHeight = messageObject.videoEditedInfo.resultHeight;
        int rotationValue = messageObject.videoEditedInfo.rotationValue;
        int originalWidth = messageObject.videoEditedInfo.originalWidth;
        int originalHeight = messageObject.videoEditedInfo.originalHeight;
        int framerate = messageObject.videoEditedInfo.framerate;
        int bitrate = messageObject.videoEditedInfo.bitrate;
        int rotateRender = 0;
        boolean isSecret = ((int) messageObject.getDialogId()) == 0;
        File cacheFile = new File(messageObject.messageOwner.attachPath);
        if (videoPath == null) {
            videoPath = "";
        }

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
                didWriteData(messageObject, cacheFile, true, 0, true);
                preferences.edit().putBoolean("isPreviousOk", true).commit();
                return false;
            }
        }

        videoConvertFirstWrite = true;
        boolean error = false;

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
                mediaMuxer = new MP4Builder().createMovie(movie, isSecret);
                extractor = new MediaExtractor();
                extractor.setDataSource(videoPath);

                checkConversionCanceled();

                if (resultWidth != originalWidth || resultHeight != originalHeight || rotateRender != 0 || messageObject.videoEditedInfo.roundVideo || Build.VERSION.SDK_INT >= 18 && startTime != -1) {
                    int videoIndex = findTrack(extractor, false);
                    int audioIndex = bitrate != -1 ? findTrack(extractor, true) : -1;
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
                            int audioTrackIndex = -5;

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
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d("codec = " + codecInfo.getName() + " manufacturer = " + manufacturer + "device = " + Build.MODEL);
                                }
                            } else {
                                colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
                            }
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("colorFormat = " + colorFormat);
                            }

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
                            MediaFormat videoFormat = extractor.getTrackFormat(videoIndex);
                            ByteBuffer audioBuffer = null;
                            if (audioIndex >= 0) {
                                extractor.selectTrack(audioIndex);
                                MediaFormat audioFormat = extractor.getTrackFormat(audioIndex);
                                int maxBufferSize = audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                                audioBuffer = ByteBuffer.allocateDirect(maxBufferSize);
                                audioTrackIndex = mediaMuxer.addTrack(audioFormat, true);
                            }

                            if (startTime > 0) {
                                extractor.seekTo(startTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                            } else {
                                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                            }

                            MediaFormat outputFormat = MediaFormat.createVideoFormat(MIME_TYPE, resultWidth, resultHeight);
                            outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
                            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate > 0 ? bitrate : 921600);
                            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framerate != 0 ? framerate : 25);
                            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
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

                            decoder = MediaCodec.createDecoderByType(videoFormat.getString(MediaFormat.KEY_MIME));
                            if (Build.VERSION.SDK_INT >= 18) {
                                outputSurface = new OutputSurface();
                            } else {
                                outputSurface = new OutputSurface(resultWidth, resultHeight, rotateRender);
                            }
                            decoder.configure(videoFormat, outputSurface.getSurface(), null, 0);
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
                                    } else if (audioIndex != -1 && index == audioIndex) {
                                        info.size = extractor.readSampleData(audioBuffer, 0);
                                        if (Build.VERSION.SDK_INT < 21) {
                                            audioBuffer.position(0);
                                            audioBuffer.limit(info.size);
                                        }
                                        if (info.size >= 0) {
                                            info.presentationTimeUs = extractor.getSampleTime();
                                            extractor.advance();
                                        } else {
                                            info.size = 0;
                                            inputDone = true;
                                        }
                                        if (info.size > 0 && (endTime < 0 || info.presentationTimeUs < endTime)) {
                                            info.offset = 0;
                                            info.flags = extractor.getSampleFlags();
                                            long availableSize = mediaMuxer.writeSampleData(audioTrackIndex, audioBuffer, info, false);
                                            if (availableSize != 0) {
                                                didWriteData(messageObject, cacheFile, false, availableSize, false);
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
                                                long availableSize = mediaMuxer.writeSampleData(videoTrackIndex, encodedData, info, true);
                                                if (availableSize != 0) {
                                                    didWriteData(messageObject, cacheFile, false, availableSize, false);
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
                                            if (BuildVars.LOGS_ENABLED) {
                                                FileLog.d("newFormat = " + newFormat);
                                            }
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
                                                    if (BuildVars.LOGS_ENABLED) {
                                                        FileLog.d("drop frame startTime = " + startTime + " present time = " + info.presentationTimeUs);
                                                    }
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
                                                            if (BuildVars.LOGS_ENABLED) {
                                                                FileLog.d("input buffer not available");
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                                decoderOutputAvailable = false;
                                                if (BuildVars.LOGS_ENABLED) {
                                                    FileLog.d("decoder stream end");
                                                }
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
                    readAndWriteTracks(messageObject, extractor, mediaMuxer, info, startTime, endTime, cacheFile, bitrate != -1);
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
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("time = " + (System.currentTimeMillis() - time));
                }
            }
        } else {
            preferences.edit().putBoolean("isPreviousOk", true).commit();
            didWriteData(messageObject, cacheFile, true, 0, true);
            return false;
        }
        preferences.edit().putBoolean("isPreviousOk", true).commit();
        didWriteData(messageObject, cacheFile, true, 0, error);
        return true;
    }
}

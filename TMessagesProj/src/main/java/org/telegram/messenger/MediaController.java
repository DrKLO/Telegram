/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.messenger;

import android.app.ProgressDialog;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.audiofx.AutomaticGainControl;
import android.net.Uri;
import android.os.Environment;
import android.os.Vibrator;

import org.telegram.objects.MessageObject;
import org.telegram.ui.ApplicationLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
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
    private native void readOpusFile(ByteBuffer buffer, int capacity);
    private native int getFinished();
    private native int getSize();
    private native long getPcmOffset();
    private native long getTotalPcmDuration();


    public static interface FileDownloadProgressListener {
        public void onFailedDownload(String fileName);
        public void onSuccessDownload(String fileName);
        public void onProgressDownload(String fileName, float progress);
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

    public final static int audioProgressDidChanged = 50001;
    public final static int audioDidReset = 50002;
    public final static int recordProgressChanged = 50003;
    public final static int recordStarted = 50004;
    public final static int recordStartError = 50005;
    public final static int recordStopped = 50006;

    private HashMap<String, ArrayList<WeakReference<FileDownloadProgressListener>>> loadingFileObservers = new HashMap<String, ArrayList<WeakReference<FileDownloadProgressListener>>>();
    private HashMap<Integer, String> observersByTag = new HashMap<Integer, String>();
    private boolean listenerInProgress = false;
    private HashMap<String, FileDownloadProgressListener> addLaterArray = new HashMap<String, FileDownloadProgressListener>();
    private ArrayList<FileDownloadProgressListener> deleteLaterArray = new ArrayList<FileDownloadProgressListener>();
    private int lastTag = 0;

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

    private AudioRecord audioRecorder = null;
    private Object audioGainObj = null;
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
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationCenter.getInstance().postNotificationName(recordProgressChanged, System.currentTimeMillis() - recordStartTime);
                        }
                    });
                } else {
                    recordBuffers.add(buffer);
                    stopRecordingInternal(sendAfterDone);
                }
            }
        }
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

    public MediaController () {
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


        NotificationCenter.getInstance().addObserver(this, FileLoader.FileDidFailedLoad);
        NotificationCenter.getInstance().addObserver(this, FileLoader.FileDidLoaded);
        NotificationCenter.getInstance().addObserver(this, FileLoader.FileLoadProgressChanged);

        Timer progressTimer = new Timer();
        progressTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (sync) {
                    Utilities.RunOnUIThread(new Runnable() {
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
                                        progress = (int)(lastPlayPcm / 48.0f);
                                        value = (float)lastPlayPcm / (float)currentTotalPcmDuration;
                                        if (progress == lastProgress) {
                                            return;
                                        }
                                    }
                                    lastProgress = progress;
                                    playingMessageObject.audioProgress = value;
                                    playingMessageObject.audioProgressSec = lastProgress / 1000;
                                    NotificationCenter.getInstance().postNotificationName(audioProgressDidChanged, playingMessageObject.messageOwner.id, value);
                                } catch (Exception e) {
                                    FileLog.e("tmessages", e);
                                }
                            }
                        }
                    });
                }
            }
        }, 100, 17);
    }

    public void cleanup() {
        clenupPlayer(false);
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
        if (id == FileLoader.FileDidFailedLoad) {
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
        } else if (id == FileLoader.FileDidLoaded) {
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
        } else if (id == FileLoader.FileLoadProgressChanged) {
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
                        readOpusFile(buffer.buffer, playerBufferSize);
                        buffer.size = getSize();
                        buffer.pcmOffset = getPcmOffset();
                        buffer.finished = getFinished();
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
                        Utilities.RunOnUIThread(new Runnable() {
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
            lastProgress = 0;
            isPaused = false;
            MessageObject lastFile = playingMessageObject;
            playingMessageObject.audioProgress = 0.0f;
            playingMessageObject.audioProgressSec = 0;
            playingMessageObject = null;
            if (notify) {
                NotificationCenter.getInstance().postNotificationName(audioDidReset, lastFile.messageOwner.id);
            }
        }
    }

    private void seekOpusPlayer(final float progress) {
        if (currentTotalPcmDuration * progress == currentTotalPcmDuration) {
            return;
        }
        audioTrackPlayer.pause();
        audioTrackPlayer.flush();
        fileDecodingQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                seekOpusFile(progress);
                synchronized (playerSync) {
                    freePlayerBuffers.addAll(usedPlayerBuffers);
                    usedPlayerBuffers.clear();
                }
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        ignoreFirstProgress = 3;
                        //audioTrackPlayer.setNotificationMarkerPosition((int)(currentTotalPcmDuration * (1 - playingMessageObject.audioProgress)));
                        lastPlayPcm = (long)(currentTotalPcmDuration * progress);
                        if (audioTrackPlayer != null) {
                            audioTrackPlayer.play();
                        }
                        lastProgress = (int)(currentTotalPcmDuration / 48.0f * progress);
                        checkPlayerQueue();
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
        final File cacheFile = new File(Utilities.getCacheDir(), messageObject.getFileName());

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
            //audioTrackPlayer.setNotificationMarkerPosition((int)(currentTotalPcmDuration * (1 - playingMessageObject.audioProgress)));
            fileDecodingQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    if (playingMessageObject.audioProgress != 0) {
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
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationCenter.getInstance().postNotificationName(recordStartError);
                        }
                    });
                    return;
                }

                recordingAudio = new TLRPC.TL_audio();
                recordingAudio.dc_id = Integer.MIN_VALUE;
                recordingAudio.id = UserConfig.lastLocalId;
                recordingAudio.user_id = UserConfig.clientUserId;
                UserConfig.lastLocalId--;
                UserConfig.saveConfig(false);

                recordingAudioFile = new File(Utilities.getCacheDir(), MessageObject.getAttachFileName(recordingAudio));

                try {
                    if (startRecord(recordingAudioFile.getAbsolutePath()) == 0) {
                        Utilities.RunOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                NotificationCenter.getInstance().postNotificationName(recordStartError);
                            }
                        });
                        return;
                    }
                    audioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, recordBufferSize * 10);
                    recordStartTime = System.currentTimeMillis();
                    recordTimeCount = 0;
                    recordDialogId = dialog_id;
                    fileBuffer.rewind();

                    if (android.os.Build.VERSION.SDK_INT >= 16) {
                        AutomaticGainControl agc = null;
                        try {
                            if (AutomaticGainControl.isAvailable()) {
                                agc = AutomaticGainControl.create(audioRecorder.getAudioSessionId());
                                agc.setEnabled(true);
                                audioGainObj = agc;
                            }
                        } catch (Exception e) {
                            try {
                                if (agc != null) {
                                    agc.release();
                                    agc = null;
                                }
                            } catch (Exception e2) {
                                FileLog.e("tmessages", e2);
                            }
                            FileLog.e("tmessages", e);
                        }
                    }

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

                    if (android.os.Build.VERSION.SDK_INT >= 16 && audioGainObj != null) {
                        AutomaticGainControl agc = (AutomaticGainControl)audioGainObj;
                        try {
                            if (agc != null) {
                                agc.release();
                                agc = null;
                            }
                        } catch (Exception e2) {
                            FileLog.e("tmessages", e2);
                        }
                    }

                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationCenter.getInstance().postNotificationName(recordStartError);
                        }
                    });
                    return;
                }

                recordQueue.postRunnable(recordRunnable);
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().postNotificationName(recordStarted);
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
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            audioToSend.date = ConnectionsManager.getInstance().getCurrentTime();
                            audioToSend.size = (int) recordingAudioFileToSend.length();
                            audioToSend.path = recordingAudioFileToSend.getAbsolutePath();
                            long duration = recordTimeCount;
                            audioToSend.duration = (int) (duration / 1000);
                            if (duration > 700) {
                                MessagesController.getInstance().sendMessage(audioToSend, recordDialogId);
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
            if (android.os.Build.VERSION.SDK_INT >= 16 && audioGainObj != null) {
                AutomaticGainControl agc = (AutomaticGainControl)audioGainObj;
                try {
                    if (agc != null) {
                        agc.release();
                        agc = null;
                    }
                } catch (Exception e2) {
                    FileLog.e("tmessages", e2);
                }
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
                //recordTimeCount = System.currentTimeMillis() - recordStartTime;
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
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().postNotificationName(recordStopped);
                    }
                });
            }
        });
    }

    public static void saveFile(String path, Context context, final int type, final String name) {
        final File sourceFile = new File(Utilities.getCacheDir(), path);
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

            Utilities.globalQueue.postRunnable(new Runnable() {
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
                                        Utilities.RunOnUIThread(new Runnable() {
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
                        Utilities.RunOnUIThread(new Runnable() {
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
            });
        }
    }
}

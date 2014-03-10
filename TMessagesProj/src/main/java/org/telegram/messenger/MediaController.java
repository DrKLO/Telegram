/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.messenger;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Vibrator;

import org.telegram.objects.MessageObject;
import org.telegram.ui.ApplicationLoader;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class MediaController implements NotificationCenter.NotificationCenterDelegate {

    public static interface FileDownloadProgressListener {
        public void onFailedDownload(String fileName);
        public void onSuccessDownload(String fileName);
        public void onProgressDownload(String fileName, float progress);
        public int getObserverTag();
    }

    public final static int audioProgressDidChanged = 50001;
    public final static int audioDidReset = 50002;
    public final static int recordProgressChanged = 50003;

    public static MediaController Instance = new MediaController();

    private HashMap<String, ArrayList<WeakReference<FileDownloadProgressListener>>> loadingFileObservers = new HashMap<String, ArrayList<WeakReference<FileDownloadProgressListener>>>();
    private HashMap<Integer, String> observersByTag = new HashMap<Integer, String>();
    private boolean listenerInProgress = false;
    private HashMap<String, FileDownloadProgressListener> addLaterArray = new HashMap<String, FileDownloadProgressListener>();
    private ArrayList<FileDownloadProgressListener> deleteLaterArray = new ArrayList<FileDownloadProgressListener>();

    private boolean isPaused = false;
    private MediaPlayer audioPlayer = null;
    private int lastProgress = 0;
    private MessageObject playingMessageObject;

    private MediaRecorder audioRecorder = null;
    private TLRPC.TL_audio recordingAudio = null;
    private File recordingAudioFile = null;
    private long recordStartTime;
    private long recordDialogId;

    private final Integer sync = 1;

    private int lastTag = 0;

    public MediaController () {
        NotificationCenter.Instance.addObserver(this, FileLoader.FileDidFailedLoad);
        NotificationCenter.Instance.addObserver(this, FileLoader.FileDidLoaded);
        NotificationCenter.Instance.addObserver(this, FileLoader.FileLoadProgressChanged);

        if (ConnectionsManager.enableAudio) {
            Timer progressTimer = new Timer();
            progressTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    synchronized (sync) {
                        Utilities.RunOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (playingMessageObject != null && audioPlayer != null && !isPaused) {
                                    try {
                                        int progress = audioPlayer.getCurrentPosition();
                                        if (progress <= lastProgress) {
                                            return;
                                        }
                                        lastProgress = progress;
                                        final float value = (float)lastProgress / (float)audioPlayer.getDuration();
                                        playingMessageObject.audioProgress = value;
                                        playingMessageObject.audioProgressSec = lastProgress / 1000;
                                        NotificationCenter.Instance.postNotificationName(audioProgressDidChanged, playingMessageObject.messageOwner.id, value);
                                    } catch (Exception e) {
                                        FileLog.e("tmessages", e);
                                    }
                                }
                                if (audioRecorder != null) {
                                    NotificationCenter.Instance.postNotificationName(recordProgressChanged, System.currentTimeMillis() - recordStartTime);
                                }
                            }
                        });
                    }
                }
            }, 100, 17);
        }
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

    private void clenupPlayer(boolean notify) {
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
            lastProgress = 0;
            isPaused = false;
            MessageObject lastFile = playingMessageObject;
            playingMessageObject.audioProgress = 0.0f;
            playingMessageObject.audioProgressSec = 0;
            playingMessageObject = null;
            if (notify) {
                NotificationCenter.Instance.postNotificationName(audioDidReset, lastFile.messageOwner.id);
            }
        }
    }

    public boolean seekToProgress(MessageObject messageObject, float progress) {
        if (audioPlayer == null || messageObject == null || playingMessageObject == null || playingMessageObject != null && playingMessageObject.messageOwner.id != messageObject.messageOwner.id) {
            return false;
        }
        try {
            int seekTo = (int)(audioPlayer.getDuration() * progress);
            audioPlayer.seekTo(seekTo);
            lastProgress = seekTo;
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
        if (audioPlayer != null && playingMessageObject != null && messageObject.messageOwner.id == playingMessageObject.messageOwner.id) {
            if (isPaused) {
                resumeAudio(messageObject);
            }
            return true;
        }
        clenupPlayer(true);
        try {
            audioPlayer = new MediaPlayer();
            audioPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            File cacheFile = new File(Utilities.getCacheDir(), messageObject.getFileName());
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

        isPaused = false;
        lastProgress = 0;
        playingMessageObject = messageObject;

        try {
            if (playingMessageObject.audioProgress != 0) {
                int seekTo = (int)(audioPlayer.getDuration() * playingMessageObject.audioProgress);
                audioPlayer.seekTo(seekTo);
            }
        } catch (Exception e2) {
            playingMessageObject.audioProgress = 0;
            playingMessageObject.audioProgressSec = 0;
            FileLog.e("tmessages", e2);
        }

        return true;
    }

    public void stopAudio() {
        if (audioPlayer == null || playingMessageObject == null) {
            return;
        }
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
        playingMessageObject = null;
        isPaused = false;
    }

    public boolean pauseAudio(MessageObject messageObject) {
        if (audioPlayer == null || messageObject == null || playingMessageObject == null || playingMessageObject != null && playingMessageObject.messageOwner.id != messageObject.messageOwner.id) {
            return false;
        }
        try {
            audioPlayer.pause();
            isPaused = true;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            isPaused = false;
            return false;
        }
        return true;
    }

    public boolean resumeAudio(MessageObject messageObject) {
        if (audioPlayer == null || messageObject == null || playingMessageObject == null || playingMessageObject != null && playingMessageObject.messageOwner.id != messageObject.messageOwner.id) {
            return false;
        }
        try {
            audioPlayer.start();
            isPaused = false;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            return false;
        }
        return true;
    }

    public boolean isPlayingAudio(MessageObject messageObject) {
        return !(audioPlayer == null || messageObject == null || playingMessageObject == null || playingMessageObject != null && playingMessageObject.messageOwner.id != messageObject.messageOwner.id);
    }

    public boolean isAudioPaused() {
        return isPaused;
    }

    public boolean startRecording(long dialog_id) {
        if (audioRecorder != null) {
            return false;
        }

        recordingAudio = new TLRPC.TL_audio();
        recordingAudio.dc_id = Integer.MIN_VALUE;
        recordingAudio.id = UserConfig.lastLocalId;
        recordingAudio.user_id = UserConfig.clientUserId;
        UserConfig.lastLocalId--;
        UserConfig.saveConfig(false);

        recordingAudioFile = new File(Utilities.getCacheDir(), MessageObject.getAttachFileName(recordingAudio));

        audioRecorder = new MediaRecorder();
        audioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        audioRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        audioRecorder.setOutputFile(recordingAudioFile.getAbsolutePath());
        if(android.os.Build.VERSION.SDK_INT >= 10) {
            audioRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        } else {
            audioRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        }
        audioRecorder.setAudioSamplingRate(16000);
        audioRecorder.setAudioChannels(1);
        audioRecorder.setAudioEncodingBitRate(16000*4*1);

        try {
            audioRecorder.prepare();
            audioRecorder.start();
            recordStartTime = System.currentTimeMillis();
            recordDialogId = dialog_id;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            recordingAudio = null;
            recordingAudioFile.delete();
            recordingAudioFile = null;
            try {
                audioRecorder.release();
                audioRecorder = null;
            } catch (Exception e2) {
                FileLog.e("tmessages", e2);
            }
            return false;
        }
        try {
            Vibrator v = (Vibrator) ApplicationLoader.applicationContext.getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(20);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return true;
    }

    public void stopRecording(boolean send) {
        if (audioRecorder == null) {
            return;
        }
        try {
            audioRecorder.stop();
            if (send) {
                recordingAudio.date = ConnectionsManager.Instance.getCurrentTime();
                recordingAudio.size = (int)recordingAudioFile.length();
                recordingAudio.path = recordingAudioFile.getAbsolutePath();
                long duration = System.currentTimeMillis() - recordStartTime;
                recordingAudio.duration = (int)(duration / 1000);
                if (duration > 500) {
                    MessagesController.Instance.sendMessage(recordingAudio, recordDialogId);
                } else {
                    recordingAudioFile.delete();
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
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
            FileLog.e("tmessages", e);
        }
        recordingAudio = null;
        recordingAudioFile = null;
        try {
            Vibrator v = (Vibrator) ApplicationLoader.applicationContext.getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(20);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }
}

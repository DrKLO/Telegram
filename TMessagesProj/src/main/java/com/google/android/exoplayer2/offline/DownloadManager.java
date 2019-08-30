/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.offline;

import static com.google.android.exoplayer2.offline.DownloadState.FAILURE_REASON_NONE;
import static com.google.android.exoplayer2.offline.DownloadState.FAILURE_REASON_UNKNOWN;
import static com.google.android.exoplayer2.offline.DownloadState.STATE_COMPLETED;
import static com.google.android.exoplayer2.offline.DownloadState.STATE_DOWNLOADING;
import static com.google.android.exoplayer2.offline.DownloadState.STATE_FAILED;
import static com.google.android.exoplayer2.offline.DownloadState.STATE_QUEUED;
import static com.google.android.exoplayer2.offline.DownloadState.STATE_REMOVED;
import static com.google.android.exoplayer2.offline.DownloadState.STATE_REMOVING;
import static com.google.android.exoplayer2.offline.DownloadState.STATE_RESTARTING;
import static com.google.android.exoplayer2.offline.DownloadState.STATE_STOPPED;
import static com.google.android.exoplayer2.offline.DownloadState.STOP_FLAG_DOWNLOAD_MANAGER_NOT_READY;
import static com.google.android.exoplayer2.offline.DownloadState.STOP_FLAG_STOPPED;

import android.content.Context;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.scheduler.RequirementsWatcher;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Manages multiple stream download and remove requests.
 *
 * <p>A download manager instance must be accessed only from the thread that created it, unless that
 * thread does not have a {@link Looper}. In that case, it must be accessed only from the
 * application's main thread. Registered listeners will be called on the same thread.
 */
public final class DownloadManager {

  /** Listener for {@link DownloadManager} events. */
  public interface Listener {
    /**
     * Called when all actions have been restored.
     *
     * @param downloadManager The reporting instance.
     */
    void onInitialized(DownloadManager downloadManager);
    /**
     * Called when the state of a download changes.
     *
     * @param downloadManager The reporting instance.
     * @param downloadState The state of the download.
     */
    void onDownloadStateChanged(DownloadManager downloadManager, DownloadState downloadState);

    /**
     * Called when there is no active download left.
     *
     * @param downloadManager The reporting instance.
     */
    void onIdle(DownloadManager downloadManager);

    /**
     * Called when the download requirements state changed.
     *
     * @param downloadManager The reporting instance.
     * @param requirements Requirements needed to be met to start downloads.
     * @param notMetRequirements {@link Requirements.RequirementFlags RequirementFlags} that are not
     *     met, or 0.
     */
    void onRequirementsStateChanged(
        DownloadManager downloadManager,
        Requirements requirements,
        @Requirements.RequirementFlags int notMetRequirements);
  }

  /** The default maximum number of simultaneous downloads. */
  public static final int DEFAULT_MAX_SIMULTANEOUS_DOWNLOADS = 1;
  /** The default minimum number of times a download must be retried before failing. */
  public static final int DEFAULT_MIN_RETRY_COUNT = 5;
  /** The default requirement is that the device has network connectivity. */
  public static final Requirements DEFAULT_REQUIREMENTS =
      new Requirements(Requirements.NETWORK_TYPE_ANY, false, false);

  private static final String TAG = "DownloadManager";
  private static final boolean DEBUG = false;

  private final int maxActiveDownloads;
  private final int minRetryCount;
  private final Context context;
  private final ActionFile actionFile;
  private final DownloaderFactory downloaderFactory;
  private final ArrayList<Download> downloads;
  private final ArrayList<Download> activeDownloads;
  private final Handler handler;
  private final HandlerThread fileIOThread;
  private final Handler fileIOHandler;
  private final CopyOnWriteArraySet<Listener> listeners;
  private final ArrayDeque<DownloadAction> actionQueue;

  private boolean initialized;
  private boolean released;
  @DownloadState.StopFlags private int stickyStopFlags;
  private RequirementsWatcher requirementsWatcher;

  /**
   * Constructs a {@link DownloadManager}.
   *
   * @param context Any context.
   * @param actionFile The file in which active actions are saved.
   * @param downloaderFactory A factory for creating {@link Downloader}s.
   */
  public DownloadManager(Context context, File actionFile, DownloaderFactory downloaderFactory) {
    this(
        context,
        actionFile,
        downloaderFactory,
        DEFAULT_MAX_SIMULTANEOUS_DOWNLOADS,
        DEFAULT_MIN_RETRY_COUNT,
        DEFAULT_REQUIREMENTS);
  }

  /**
   * Constructs a {@link DownloadManager}.
   *
   * @param context Any context.
   * @param actionFile The file in which active actions are saved.
   * @param downloaderFactory A factory for creating {@link Downloader}s.
   * @param maxSimultaneousDownloads The maximum number of simultaneous downloads.
   * @param minRetryCount The minimum number of times a download must be retried before failing.
   * @param requirements The requirements needed to be met to start downloads.
   */
  public DownloadManager(
      Context context,
      File actionFile,
      DownloaderFactory downloaderFactory,
      int maxSimultaneousDownloads,
      int minRetryCount,
      Requirements requirements) {
    this.context = context.getApplicationContext();
    this.actionFile = new ActionFile(actionFile);
    this.downloaderFactory = downloaderFactory;
    this.maxActiveDownloads = maxSimultaneousDownloads;
    this.minRetryCount = minRetryCount;
    this.stickyStopFlags = STOP_FLAG_STOPPED | STOP_FLAG_DOWNLOAD_MANAGER_NOT_READY;

    downloads = new ArrayList<>();
    activeDownloads = new ArrayList<>();

    Looper looper = Looper.myLooper();
    if (looper == null) {
      looper = Looper.getMainLooper();
    }
    handler = new Handler(looper);

    fileIOThread = new HandlerThread("DownloadManager file i/o");
    fileIOThread.start();
    fileIOHandler = new Handler(fileIOThread.getLooper());

    listeners = new CopyOnWriteArraySet<>();
    actionQueue = new ArrayDeque<>();

    watchRequirements(requirements);
    loadActions();
    logd("Created");
  }

  /**
   * Sets the requirements needed to be met to start downloads.
   *
   * @param requirements Need to be met to start downloads.
   */
  public void setRequirements(Requirements requirements) {
    Assertions.checkState(!released);
    if (requirements.equals(requirementsWatcher.getRequirements())) {
      return;
    }
    requirementsWatcher.stop();
    notifyListenersRequirementsStateChange(watchRequirements(requirements));
  }

  /** Returns the requirements needed to be met to start downloads. */
  public Requirements getRequirements() {
    return requirementsWatcher.getRequirements();
  }

  /**
   * Adds a {@link Listener}.
   *
   * @param listener The listener to be added.
   */
  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  /**
   * Removes a {@link Listener}.
   *
   * @param listener The listener to be removed.
   */
  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  /** Starts the downloads. */
  public void startDownloads() {
    clearStopFlags(STOP_FLAG_STOPPED);
  }

  /** Stops all of the downloads. Call {@link #startDownloads()} to restart downloads. */
  public void stopDownloads() {
    setStopFlags(STOP_FLAG_STOPPED);
  }

  private void setStopFlags(int flags) {
    updateStopFlags(flags, flags);
  }

  private void clearStopFlags(int flags) {
    updateStopFlags(flags, 0);
  }

  private void updateStopFlags(int flags, int values) {
    Assertions.checkState(!released);
    int updatedStickyStopFlags = (values & flags) | (stickyStopFlags & ~flags);
    if (stickyStopFlags != updatedStickyStopFlags) {
      stickyStopFlags = updatedStickyStopFlags;
      for (int i = 0; i < downloads.size(); i++) {
        downloads.get(i).updateStopFlags(flags, values);
      }
      logdFlags("Sticky stop flags are updated", updatedStickyStopFlags);
    }
  }

  /**
   * Handles the given action.
   *
   * @param action The action to be executed.
   */
  public void handleAction(DownloadAction action) {
    Assertions.checkState(!released);
    if (initialized) {
      addDownloadForAction(action);
      saveActions();
    } else {
      actionQueue.add(action);
    }
  }

  /** Returns the number of downloads. */
  public int getDownloadCount() {
    Assertions.checkState(!released);
    return downloads.size();
  }

  /**
   * Returns {@link DownloadState} for the given content id, or null if no such download exists.
   *
   * @param id The unique content id.
   * @return DownloadState for the given content id, or null if no such download exists.
   */
  @Nullable
  public DownloadState getDownloadState(String id) {
    Assertions.checkState(!released);
    for (int i = 0; i < downloads.size(); i++) {
      Download download = downloads.get(i);
      if (download.id.equals(id)) {
        return download.getDownloadState();
      }
    }
    return null;
  }

  /** Returns the states of all current downloads. */
  public DownloadState[] getAllDownloadStates() {
    Assertions.checkState(!released);
    DownloadState[] states = new DownloadState[downloads.size()];
    for (int i = 0; i < states.length; i++) {
      states[i] = downloads.get(i).getDownloadState();
    }
    return states;
  }

  /** Returns whether the manager has completed initialization. */
  public boolean isInitialized() {
    Assertions.checkState(!released);
    return initialized;
  }

  /** Returns whether there are no active downloads. */
  public boolean isIdle() {
    Assertions.checkState(!released);
    if (!initialized) {
      return false;
    }
    for (int i = 0; i < downloads.size(); i++) {
      if (!downloads.get(i).isIdle()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Stops all of the downloads and releases resources. If the action file isn't up to date, waits
   * for the changes to be written. The manager must not be accessed after this method has been
   * called.
   */
  public void release() {
    if (released) {
      return;
    }
    setStopFlags(STOP_FLAG_DOWNLOAD_MANAGER_NOT_READY);
    released = true;
    if (requirementsWatcher != null) {
      requirementsWatcher.stop();
    }
    final ConditionVariable fileIOFinishedCondition = new ConditionVariable();
    fileIOHandler.post(fileIOFinishedCondition::open);
    fileIOFinishedCondition.block();
    fileIOThread.quit();
    logd("Released");
  }

  private void addDownloadForAction(DownloadAction action) {
    for (int i = 0; i < downloads.size(); i++) {
      Download download = downloads.get(i);
      if (download.addAction(action)) {
        logd("Action is added to existing download", download);
        return;
      }
    }
    Download download =
        new Download(this, downloaderFactory, action, minRetryCount, stickyStopFlags);
    downloads.add(download);
    logd("Download is added", download);
  }

  private void maybeStartDownload(Download download) {
    if (activeDownloads.size() < maxActiveDownloads) {
      if (download.start()) {
        activeDownloads.add(download);
      }
    }
  }

  private void maybeNotifyListenersIdle() {
    if (!isIdle()) {
      return;
    }
    logd("Notify idle state");
    for (Listener listener : listeners) {
      listener.onIdle(this);
    }
  }

  private void onDownloadStateChange(Download download) {
    if (released) {
      return;
    }
    boolean idle = download.isIdle();
    if (idle) {
      activeDownloads.remove(download);
    }
    notifyListenersDownloadStateChange(download);
    if (download.isFinished()) {
      downloads.remove(download);
      saveActions();
    }
    if (idle) {
      for (int i = 0; i < downloads.size(); i++) {
        maybeStartDownload(downloads.get(i));
      }
      maybeNotifyListenersIdle();
    }
  }

  private void notifyListenersDownloadStateChange(Download download) {
    logd("Download state is changed", download);
    DownloadState downloadState = download.getDownloadState();
    for (Listener listener : listeners) {
      listener.onDownloadStateChanged(this, downloadState);
    }
  }

  private void notifyListenersRequirementsStateChange(
      @Requirements.RequirementFlags int notMetRequirements) {
    logdFlags("Not met requirements are changed", notMetRequirements);
    for (Listener listener : listeners) {
      listener.onRequirementsStateChanged(
          DownloadManager.this, requirementsWatcher.getRequirements(), notMetRequirements);
    }
  }

  private void loadActions() {
    fileIOHandler.post(
        () -> {
          DownloadAction[] loadedActions;
          try {
            loadedActions = actionFile.load();
            logd("Action file is loaded.");
          } catch (Throwable e) {
            Log.e(TAG, "Action file loading failed.", e);
            loadedActions = new DownloadAction[0];
          }
          final DownloadAction[] actions = loadedActions;
          handler.post(
              () -> {
                if (released) {
                  return;
                }
                for (DownloadAction action : actions) {
                  addDownloadForAction(action);
                }
                if (!actionQueue.isEmpty()) {
                  while (!actionQueue.isEmpty()) {
                    addDownloadForAction(actionQueue.remove());
                  }
                  saveActions();
                }
                logd("Downloads are created.");
                initialized = true;
                for (Listener listener : listeners) {
                  listener.onInitialized(DownloadManager.this);
                }
                clearStopFlags(STOP_FLAG_DOWNLOAD_MANAGER_NOT_READY);
              });
        });
  }

  private void saveActions() {
    if (released) {
      return;
    }
    ArrayList<DownloadAction> actions = new ArrayList<>(downloads.size());
    for (int i = 0; i < downloads.size(); i++) {
      actions.addAll(downloads.get(i).actionQueue);
    }
    final DownloadAction[] actionsArray = actions.toArray(new DownloadAction[0]);
    fileIOHandler.post(
        () -> {
          try {
            actionFile.store(actionsArray);
            logd("Actions persisted.");
          } catch (IOException e) {
            Log.e(TAG, "Persisting actions failed.", e);
          }
        });
  }

  private static void logd(String message) {
    if (DEBUG) {
      Log.d(TAG, message);
    }
  }

  private static void logd(String message, Download download) {
    if (DEBUG) {
      logd(message + ": " + download);
    }
  }

  private static void logdFlags(String message, int flags) {
    if (DEBUG) {
      logd(message + ": " + Integer.toBinaryString(flags));
    }
  }

  @Requirements.RequirementFlags
  private int watchRequirements(Requirements requirements) {
    requirementsWatcher = new RequirementsWatcher(context, new RequirementListener(), requirements);
    @Requirements.RequirementFlags int notMetRequirements = requirementsWatcher.start();
    if (notMetRequirements == 0) {
      startDownloads();
    } else {
      stopDownloads();
    }
    return notMetRequirements;
  }

  private static final class Download {

    private final String id;
    private final DownloadManager downloadManager;
    private final DownloaderFactory downloaderFactory;
    private final int minRetryCount;
    private final long startTimeMs;
    private final ArrayDeque<DownloadAction> actionQueue;
    /** The current state of the download. */
    @DownloadState.State private int state;

    @MonotonicNonNull private Downloader downloader;
    @MonotonicNonNull private DownloadThread downloadThread;
    @MonotonicNonNull @DownloadState.FailureReason private int failureReason;
    @DownloadState.StopFlags private int stopFlags;

    private Download(
        DownloadManager downloadManager,
        DownloaderFactory downloaderFactory,
        DownloadAction action,
        int minRetryCount,
        int stopFlags) {
      this.id = action.id;
      this.downloadManager = downloadManager;
      this.downloaderFactory = downloaderFactory;
      this.minRetryCount = minRetryCount;
      this.stopFlags = stopFlags;
      this.startTimeMs = System.currentTimeMillis();
      actionQueue = new ArrayDeque<>();
      actionQueue.add(action);
      initialize(/* restart= */ false);
    }

    public boolean addAction(DownloadAction newAction) {
      DownloadAction action = actionQueue.peek();
      if (!action.isSameMedia(newAction)) {
        return false;
      }
      Assertions.checkState(action.type.equals(newAction.type));
      actionQueue.add(newAction);
      DownloadAction updatedAction = DownloadActionUtil.mergeActions(actionQueue);
      if (state == STATE_REMOVING) {
        Assertions.checkState(updatedAction.isRemoveAction);
        if (actionQueue.size() > 1) {
          setState(STATE_RESTARTING);
        }
      } else if (state == STATE_RESTARTING) {
        Assertions.checkState(updatedAction.isRemoveAction);
        if (actionQueue.size() == 1) {
          setState(STATE_REMOVING);
        }
      } else if (!action.equals(updatedAction)) {
        if (state == STATE_DOWNLOADING) {
          stopDownloadThread();
        } else {
          Assertions.checkState(state == STATE_QUEUED || state == STATE_STOPPED);
          initialize(/* restart= */ false);
        }
      }
      return true;
    }

    public DownloadState getDownloadState() {
      float downloadPercentage = C.PERCENTAGE_UNSET;
      long downloadedBytes = 0;
      long totalBytes = C.LENGTH_UNSET;
      if (downloader != null) {
        downloadPercentage = downloader.getDownloadPercentage();
        downloadedBytes = downloader.getDownloadedBytes();
        totalBytes = downloader.getTotalBytes();
      }
      DownloadAction action = actionQueue.peek();
      return new DownloadState(
          action.id,
          action.type,
          action.uri,
          action.customCacheKey,
          state,
          downloadPercentage,
          downloadedBytes,
          totalBytes,
          failureReason,
          stopFlags,
          startTimeMs,
          /* updateTimeMs= */ System.currentTimeMillis(),
          action.keys.toArray(new StreamKey[0]),
          action.data);
    }

    public boolean isFinished() {
      return state == STATE_FAILED || state == STATE_COMPLETED || state == STATE_REMOVED;
    }

    public boolean isIdle() {
      return state != STATE_DOWNLOADING && state != STATE_REMOVING && state != STATE_RESTARTING;
    }

    @Override
    public String toString() {
      return id + ' ' + DownloadState.getStateString(state);
    }

    public boolean start() {
      if (state != STATE_QUEUED) {
        return false;
      }
      startDownloadThread(actionQueue.peek());
      setState(STATE_DOWNLOADING);
      return true;
    }

    public void setStopFlags(int flags) {
      updateStopFlags(flags, flags);
    }

    public void clearStopFlags(int flags) {
      updateStopFlags(flags, 0);
    }

    public void updateStopFlags(int flags, int values) {
      stopFlags = (values & flags) | (stopFlags & ~flags);
      if (stopFlags != 0) {
        if (state == STATE_DOWNLOADING) {
          stopDownloadThread();
        } else if (state == STATE_QUEUED) {
          setState(STATE_STOPPED);
        }
      } else if (state == STATE_STOPPED) {
        startOrQueue(/* restart= */ false);
      }
    }

    private void initialize(boolean restart) {
      DownloadAction action = actionQueue.peek();
      if (action.isRemoveAction) {
        if (!downloadManager.released) {
          startDownloadThread(action);
        }
        setState(actionQueue.size() == 1 ? STATE_REMOVING : STATE_RESTARTING);
      } else if (stopFlags != 0) {
        setState(STATE_STOPPED);
      } else {
        startOrQueue(restart);
      }
    }

    private void startOrQueue(boolean restart) {
      // Set to queued state but don't notify listeners until we make sure we can't start now.
      state = STATE_QUEUED;
      if (restart) {
        start();
      } else {
        downloadManager.maybeStartDownload(this);
      }
      if (state == STATE_QUEUED) {
        downloadManager.onDownloadStateChange(this);
      }
    }

    private void setState(@DownloadState.State int newState) {
      state = newState;
      downloadManager.onDownloadStateChange(this);
    }

    private void startDownloadThread(DownloadAction action) {
      downloader = downloaderFactory.createDownloader(action);
      downloadThread =
          new DownloadThread(
              this, downloader, action.isRemoveAction, minRetryCount, downloadManager.handler);
    }

    private void stopDownloadThread() {
      Assertions.checkNotNull(downloadThread).cancel();
    }

    private void onDownloadThreadStopped(@Nullable Throwable finalError) {
      failureReason = FAILURE_REASON_NONE;
      if (!downloadThread.isCanceled) {
        if (finalError != null && state != STATE_REMOVING && state != STATE_RESTARTING) {
          failureReason = FAILURE_REASON_UNKNOWN;
          setState(STATE_FAILED);
          return;
        }
        if (actionQueue.size() == 1) {
          if (state == STATE_REMOVING) {
            setState(STATE_REMOVED);
          } else {
            Assertions.checkState(state == STATE_DOWNLOADING);
            setState(STATE_COMPLETED);
          }
          return;
        }
        actionQueue.remove();
      }
      initialize(/* restart= */ state == STATE_DOWNLOADING);
    }
  }

  private static class DownloadThread implements Runnable {

    private final Download download;
    private final Downloader downloader;
    private final boolean remove;
    private final int minRetryCount;
    private final Handler callbackHandler;
    private final Thread thread;
    private volatile boolean isCanceled;

    private DownloadThread(
        Download download,
        Downloader downloader,
        boolean remove,
        int minRetryCount,
        Handler callbackHandler) {
      this.download = download;
      this.downloader = downloader;
      this.remove = remove;
      this.minRetryCount = minRetryCount;
      this.callbackHandler = callbackHandler;
      thread = new Thread(this);
      thread.start();
    }

    public void cancel() {
      isCanceled = true;
      downloader.cancel();
      thread.interrupt();
    }

    // Methods running on download thread.

    @Override
    public void run() {
      logd("Download is started", download);
      Throwable error = null;
      try {
        if (remove) {
          downloader.remove();
        } else {
          int errorCount = 0;
          long errorPosition = C.LENGTH_UNSET;
          while (!isCanceled) {
            try {
              downloader.download();
              break;
            } catch (IOException e) {
              if (!isCanceled) {
                long downloadedBytes = downloader.getDownloadedBytes();
                if (downloadedBytes != errorPosition) {
                  logd("Reset error count. downloadedBytes = " + downloadedBytes, download);
                  errorPosition = downloadedBytes;
                  errorCount = 0;
                }
                if (++errorCount > minRetryCount) {
                  throw e;
                }
                logd("Download error. Retry " + errorCount, download);
                Thread.sleep(getRetryDelayMillis(errorCount));
              }
            }
          }
        }
      } catch (Throwable e) {
        error = e;
      }
      final Throwable finalError = error;
      callbackHandler.post(() -> download.onDownloadThreadStopped(isCanceled ? null : finalError));
    }

    private int getRetryDelayMillis(int errorCount) {
      return Math.min((errorCount - 1) * 1000, 5000);
    }
  }

  private class RequirementListener implements RequirementsWatcher.Listener {
    @Override
    public void requirementsMet(RequirementsWatcher requirementsWatcher) {
      startDownloads();
      notifyListenersRequirementsStateChange(0);
    }

    @Override
    public void requirementsNotMet(
        RequirementsWatcher requirementsWatcher,
        @Requirements.RequirementFlags int notMetRequirements) {
      stopDownloads();
      notifyListenersRequirementsStateChange(notMetRequirements);
    }
  }
}

/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.telegram.messenger.exoplayer2.upstream;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.TraceUtil;
import org.telegram.messenger.exoplayer2.util.Util;
import java.io.IOException;
import java.util.concurrent.ExecutorService;

/**
 * Manages the background loading of {@link Loadable}s.
 */
public final class Loader implements LoaderErrorThrower {

  /**
   * Thrown when an unexpected exception or error is encountered during loading.
   */
  public static final class UnexpectedLoaderException extends IOException {

    public UnexpectedLoaderException(Throwable cause) {
      super("Unexpected " + cause.getClass().getSimpleName() + ": " + cause.getMessage(), cause);
    }

  }

  /**
   * An object that can be loaded using a {@link Loader}.
   */
  public interface Loadable {

    /**
     * Cancels the load.
     */
    void cancelLoad();

    /**
     * Returns whether the load has been canceled.
     */
    boolean isLoadCanceled();

    /**
     * Performs the load, returning on completion or cancellation.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    void load() throws IOException, InterruptedException;

  }

  /**
   * A callback to be notified of {@link Loader} events.
   */
  public interface Callback<T extends Loadable> {

    /**
     * Called when a load has completed.
     * <p>
     * Note: There is guaranteed to be a memory barrier between {@link Loadable#load()} exiting and
     * this callback being called.
     *
     * @param loadable The loadable whose load has completed.
     * @param elapsedRealtimeMs {@link SystemClock#elapsedRealtime} when the load ended.
     * @param loadDurationMs The duration of the load.
     */
    void onLoadCompleted(T loadable, long elapsedRealtimeMs, long loadDurationMs);

    /**
     * Called when a load has been canceled.
     * <p>
     * Note: If the {@link Loader} has not been released then there is guaranteed to be a memory
     * barrier between {@link Loadable#load()} exiting and this callback being called. If the
     * {@link Loader} has been released then this callback may be called before
     * {@link Loadable#load()} exits.
     *
     * @param loadable The loadable whose load has been canceled.
     * @param elapsedRealtimeMs {@link SystemClock#elapsedRealtime} when the load was canceled.
     * @param loadDurationMs The duration of the load up to the point at which it was canceled.
     * @param released True if the load was canceled because the {@link Loader} was released. False
     *     otherwise.
     */
    void onLoadCanceled(T loadable, long elapsedRealtimeMs, long loadDurationMs, boolean released);

    /**
     * Called when a load encounters an error.
     * <p>
     * Note: There is guaranteed to be a memory barrier between {@link Loadable#load()} exiting and
     * this callback being called.
     *
     * @param loadable The loadable whose load has encountered an error.
     * @param elapsedRealtimeMs {@link SystemClock#elapsedRealtime} when the error occurred.
     * @param loadDurationMs The duration of the load up to the point at which the error occurred.
     * @param error The load error.
     * @return The desired retry action. One of {@link Loader#RETRY},
     *     {@link Loader#RETRY_RESET_ERROR_COUNT}, {@link Loader#DONT_RETRY} and
     *     {@link Loader#DONT_RETRY_FATAL}.
     */
    int onLoadError(T loadable, long elapsedRealtimeMs, long loadDurationMs, IOException error);

  }

  /**
   * A callback to be notified when a {@link Loader} has finished being released.
   */
  public interface ReleaseCallback {

    /**
     * Called when the {@link Loader} has finished being released.
     */
    void onLoaderReleased();

  }

  public static final int RETRY = 0;
  public static final int RETRY_RESET_ERROR_COUNT = 1;
  public static final int DONT_RETRY = 2;
  public static final int DONT_RETRY_FATAL = 3;

  private final ExecutorService downloadExecutorService;

  private LoadTask<? extends Loadable> currentTask;
  private IOException fatalError;

  /**
   * @param threadName A name for the loader's thread.
   */
  public Loader(String threadName) {
    this.downloadExecutorService = Util.newSingleThreadExecutor(threadName);
  }

  /**
   * Starts loading a {@link Loadable}.
   * <p>
   * The calling thread must be a {@link Looper} thread, which is the thread on which the
   * {@link Callback} will be called.
   *
   * @param <T> The type of the loadable.
   * @param loadable The {@link Loadable} to load.
   * @param callback A callback to be called when the load ends.
   * @param defaultMinRetryCount The minimum number of times the load must be retried before
   *     {@link #maybeThrowError()} will propagate an error.
   * @throws IllegalStateException If the calling thread does not have an associated {@link Looper}.
   * @return {@link SystemClock#elapsedRealtime} when the load started.
   */
  public <T extends Loadable> long startLoading(T loadable, Callback<T> callback,
      int defaultMinRetryCount) {
    Looper looper = Looper.myLooper();
    Assertions.checkState(looper != null);
    long startTimeMs = SystemClock.elapsedRealtime();
    new LoadTask<>(looper, loadable, callback, defaultMinRetryCount, startTimeMs).start(0);
    return startTimeMs;
  }

  /**
   * Returns whether the {@link Loader} is currently loading a {@link Loadable}.
   */
  public boolean isLoading() {
    return currentTask != null;
  }

  /**
   * Cancels the current load. This method should only be called when a load is in progress.
   */
  public void cancelLoading() {
    currentTask.cancel(false);
  }

  /**
   * Releases the {@link Loader}. This method should be called when the {@link Loader} is no longer
   * required.
   */
  public void release() {
    release(null);
  }

  /**
   * Releases the {@link Loader}. This method should be called when the {@link Loader} is no longer
   * required.
   *
   * @param callback A callback to be called when the release ends. Will be called synchronously
   *     from this method if no load is in progress, or asynchronously once the load has been
   *     canceled otherwise. May be null.
   * @return True if {@code callback} was called synchronously. False if it will be called
   *     asynchronously or if {@code callback} is null.
   */
  public boolean release(ReleaseCallback callback) {
    boolean callbackInvoked = false;
    if (currentTask != null) {
      currentTask.cancel(true);
      if (callback != null) {
        downloadExecutorService.execute(new ReleaseTask(callback));
      }
    } else if (callback != null) {
      callback.onLoaderReleased();
      callbackInvoked = true;
    }
    downloadExecutorService.shutdown();
    return callbackInvoked;
  }

  // LoaderErrorThrower implementation.

  @Override
  public void maybeThrowError() throws IOException {
    maybeThrowError(Integer.MIN_VALUE);
  }

  @Override
  public void maybeThrowError(int minRetryCount) throws IOException {
    if (fatalError != null) {
      throw fatalError;
    } else if (currentTask != null) {
      currentTask.maybeThrowError(minRetryCount == Integer.MIN_VALUE
          ? currentTask.defaultMinRetryCount : minRetryCount);
    }
  }

  // Internal classes.

  @SuppressLint("HandlerLeak")
  private final class LoadTask<T extends Loadable> extends Handler implements Runnable {

    private static final String TAG = "LoadTask";

    private static final int MSG_START = 0;
    private static final int MSG_CANCEL = 1;
    private static final int MSG_END_OF_SOURCE = 2;
    private static final int MSG_IO_EXCEPTION = 3;
    private static final int MSG_FATAL_ERROR = 4;

    private final T loadable;
    private final Loader.Callback<T> callback;
    public final int defaultMinRetryCount;
    private final long startTimeMs;

    private IOException currentError;
    private int errorCount;

    private volatile Thread executorThread;
    private volatile boolean released;

    public LoadTask(Looper looper, T loadable, Loader.Callback<T> callback,
        int defaultMinRetryCount, long startTimeMs) {
      super(looper);
      this.loadable = loadable;
      this.callback = callback;
      this.defaultMinRetryCount = defaultMinRetryCount;
      this.startTimeMs = startTimeMs;
    }

    public void maybeThrowError(int minRetryCount) throws IOException {
      if (currentError != null && errorCount > minRetryCount) {
        throw currentError;
      }
    }

    public void start(long delayMillis) {
      Assertions.checkState(currentTask == null);
      currentTask = this;
      if (delayMillis > 0) {
        sendEmptyMessageDelayed(MSG_START, delayMillis);
      } else {
        execute();
      }
    }

    public void cancel(boolean released) {
      this.released = released;
      currentError = null;
      if (hasMessages(MSG_START)) {
        removeMessages(MSG_START);
        if (!released) {
          sendEmptyMessage(MSG_CANCEL);
        }
      } else {
        loadable.cancelLoad();
        if (executorThread != null) {
          executorThread.interrupt();
        }
      }
      if (released) {
        finish();
        long nowMs = SystemClock.elapsedRealtime();
        callback.onLoadCanceled(loadable, nowMs, nowMs - startTimeMs, true);
      }
    }

    @Override
    public void run() {
      try {
        executorThread = Thread.currentThread();
        if (!loadable.isLoadCanceled()) {
          TraceUtil.beginSection("load:" + loadable.getClass().getSimpleName());
          try {
            loadable.load();
          } finally {
            TraceUtil.endSection();
          }
        }
        if (!released) {
          sendEmptyMessage(MSG_END_OF_SOURCE);
        }
      } catch (IOException e) {
        if (!released) {
          obtainMessage(MSG_IO_EXCEPTION, e).sendToTarget();
        }
      } catch (InterruptedException e) {
        // The load was canceled.
        Assertions.checkState(loadable.isLoadCanceled());
        if (!released) {
          sendEmptyMessage(MSG_END_OF_SOURCE);
        }
      } catch (Exception e) {
        // This should never happen, but handle it anyway.
        Log.e(TAG, "Unexpected exception loading stream", e);
        if (!released) {
          obtainMessage(MSG_IO_EXCEPTION, new UnexpectedLoaderException(e)).sendToTarget();
        }
      } catch (OutOfMemoryError e) {
        // This can occur if a stream is malformed in a way that causes an extractor to think it
        // needs to allocate a large amount of memory. We don't want the process to die in this
        // case, but we do want the playback to fail.
        Log.e(TAG, "OutOfMemory error loading stream", e);
        if (!released) {
          obtainMessage(MSG_IO_EXCEPTION, new UnexpectedLoaderException(e)).sendToTarget();
        }
      } catch (Error e) {
        // We'd hope that the platform would kill the process if an Error is thrown here, but the
        // executor may catch the error (b/20616433). Throw it here, but also pass and throw it from
        // the handler thread so that the process dies even if the executor behaves in this way.
        Log.e(TAG, "Unexpected error loading stream", e);
        if (!released) {
          obtainMessage(MSG_FATAL_ERROR, e).sendToTarget();
        }
        throw e;
      }
    }

    @Override
    public void handleMessage(Message msg) {
      if (released) {
        return;
      }
      if (msg.what == MSG_START) {
        execute();
        return;
      }
      if (msg.what == MSG_FATAL_ERROR) {
        throw (Error) msg.obj;
      }
      finish();
      long nowMs = SystemClock.elapsedRealtime();
      long durationMs = nowMs - startTimeMs;
      if (loadable.isLoadCanceled()) {
        callback.onLoadCanceled(loadable, nowMs, durationMs, false);
        return;
      }
      switch (msg.what) {
        case MSG_CANCEL:
          callback.onLoadCanceled(loadable, nowMs, durationMs, false);
          break;
        case MSG_END_OF_SOURCE:
          callback.onLoadCompleted(loadable, nowMs, durationMs);
          break;
        case MSG_IO_EXCEPTION:
          currentError = (IOException) msg.obj;
          int retryAction = callback.onLoadError(loadable, nowMs, durationMs, currentError);
          if (retryAction == DONT_RETRY_FATAL) {
            fatalError = currentError;
          } else if (retryAction != DONT_RETRY) {
            errorCount = retryAction == RETRY_RESET_ERROR_COUNT ? 1 : errorCount + 1;
            start(getRetryDelayMillis());
          }
          break;
      }
    }

    private void execute() {
      currentError = null;
      downloadExecutorService.execute(currentTask);
    }

    private void finish() {
      currentTask = null;
    }

    private long getRetryDelayMillis() {
      return Math.min((errorCount - 1) * 1000, 5000);
    }

  }

  private static final class ReleaseTask extends Handler implements Runnable {

    private final ReleaseCallback callback;

    public ReleaseTask(ReleaseCallback callback) {
      this.callback = callback;
    }

    @Override
    public void run() {
      sendEmptyMessage(0);
    }

    @Override
    public void handleMessage(Message msg) {
      callback.onLoaderReleased();
    }

  }

}

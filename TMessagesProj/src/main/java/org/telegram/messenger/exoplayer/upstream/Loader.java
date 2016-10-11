/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.upstream;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.TraceUtil;
import org.telegram.messenger.exoplayer.util.Util;
import java.io.IOException;
import java.util.concurrent.ExecutorService;

/**
 * Manages the background loading of {@link Loadable}s.
 */
public final class Loader {

  /**
   * Thrown when an unexpected exception is encountered during loading.
   */
  public static final class UnexpectedLoaderException extends IOException {

    public UnexpectedLoaderException(Exception cause) {
      super("Unexpected " + cause.getClass().getSimpleName() + ": " + cause.getMessage(), cause);
    }

  }

  /**
   * Interface definition of an object that can be loaded using a {@link Loader}.
   */
  public interface Loadable {

    /**
     * Cancels the load.
     */
    void cancelLoad();

    /**
     * Whether the load has been canceled.
     *
     * @return True if the load has been canceled. False otherwise.
     */
    boolean isLoadCanceled();

    /**
     * Performs the load, returning on completion or cancelation.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    void load() throws IOException, InterruptedException;

  }

  /**
   * Interface definition for a callback to be notified of {@link Loader} events.
   */
  public interface Callback {

    /**
     * Invoked when loading has been canceled.
     *
     * @param loadable The loadable whose load has been canceled.
     */
    void onLoadCanceled(Loadable loadable);

    /**
     * Invoked when the data source has been fully loaded.
     *
     * @param loadable The loadable whose load has completed.
     */
    void onLoadCompleted(Loadable loadable);

    /**
     * Invoked when the data source is stopped due to an error.
     *
     * @param loadable The loadable whose load has failed.
     */
    void onLoadError(Loadable loadable, IOException exception);

  }

  private static final int MSG_END_OF_SOURCE = 0;
  private static final int MSG_IO_EXCEPTION = 1;
  private static final int MSG_FATAL_ERROR = 2;

  private final ExecutorService downloadExecutorService;

  private LoadTask currentTask;
  private boolean loading;

  /**
   * @param threadName A name for the loader's thread.
   */
  public Loader(String threadName) {
    this.downloadExecutorService = Util.newSingleThreadExecutor(threadName);
  }

  /**
   * Invokes {@link #startLoading(Looper, Loadable, Callback)}, using the {@link Looper}
   * associated with the calling thread.
   *
   * @param loadable The {@link Loadable} to load.
   * @param callback A callback to invoke when the load ends.
   * @throws IllegalStateException If the calling thread does not have an associated {@link Looper}.
   */
  public void startLoading(Loadable loadable, Callback callback) {
    Looper myLooper = Looper.myLooper();
    Assertions.checkState(myLooper != null);
    startLoading(myLooper, loadable, callback);
  }

  /**
   * Start loading a {@link Loadable}.
   * <p>
   * A {@link Loader} instance can only load one {@link Loadable} at a time, and so this method
   * must not be called when another load is in progress.
   *
   * @param looper The looper of the thread on which the callback should be invoked.
   * @param loadable The {@link Loadable} to load.
   * @param callback A callback to invoke when the load ends.
   */
  public void startLoading(Looper looper, Loadable loadable, Callback callback) {
    Assertions.checkState(!loading);
    loading = true;
    currentTask = new LoadTask(looper, loadable, callback);
    downloadExecutorService.submit(currentTask);
  }

  /**
   * Whether the {@link Loader} is currently loading a {@link Loadable}.
   *
   * @return Whether the {@link Loader} is currently loading a {@link Loadable}.
   */
  public boolean isLoading() {
    return loading;
  }

  /**
   * Cancels the current load.
   * <p>
   * This method should only be called when a load is in progress.
   */
  public void cancelLoading() {
    Assertions.checkState(loading);
    currentTask.quit();
  }

  /**
   * Releases the {@link Loader}.
   * <p>
   * This method should be called when the {@link Loader} is no longer required.
   */
  public void release() {
    release(null);
  }

  /**
   * Releases the {@link Loader}, running {@code postLoadAction} on its thread.
   * <p>
   * This method should be called when the {@link Loader} is no longer required.
   *
   * @param postLoadAction A {@link Runnable} to run on the loader's thread when
   *     {@link Loadable#load()} is no longer running.
   */
  public void release(Runnable postLoadAction) {
    if (loading) {
      cancelLoading();
    }
    if (postLoadAction != null) {
      downloadExecutorService.submit(postLoadAction);
    }
    downloadExecutorService.shutdown();
  }

  @SuppressLint("HandlerLeak")
  private final class LoadTask extends Handler implements Runnable {

    private static final String TAG = "LoadTask";

    private final Loadable loadable;
    private final Loader.Callback callback;

    private volatile Thread executorThread;

    public LoadTask(Looper looper, Loadable loadable, Loader.Callback callback) {
      super(looper);
      this.loadable = loadable;
      this.callback = callback;
    }

    public void quit() {
      loadable.cancelLoad();
      if (executorThread != null) {
        executorThread.interrupt();
      }
    }

    @Override
    public void run() {
      try {
        executorThread = Thread.currentThread();
        if (!loadable.isLoadCanceled()) {
          TraceUtil.beginSection(loadable.getClass().getSimpleName() + ".load()");
          loadable.load();
          TraceUtil.endSection();
        }
        sendEmptyMessage(MSG_END_OF_SOURCE);
      } catch (IOException e) {
        obtainMessage(MSG_IO_EXCEPTION, e).sendToTarget();
      } catch (InterruptedException e) {
        // The load was canceled.
        Assertions.checkState(loadable.isLoadCanceled());
        sendEmptyMessage(MSG_END_OF_SOURCE);
      } catch (Exception e) {
        // This should never happen, but handle it anyway.
        Log.e(TAG, "Unexpected exception loading stream", e);
        obtainMessage(MSG_IO_EXCEPTION, new UnexpectedLoaderException(e)).sendToTarget();
      } catch (Error e) {
        // We'd hope that the platform would kill the process if an Error is thrown here, but the
        // executor may catch the error (b/20616433). Throw it here, but also pass and throw it from
        // the handler thread so that the process dies even if the executor behaves in this way.
        Log.e(TAG, "Unexpected error loading stream", e);
        obtainMessage(MSG_FATAL_ERROR, e).sendToTarget();
        throw e;
      }
    }

    @Override
    public void handleMessage(Message msg) {
      if (msg.what == MSG_FATAL_ERROR) {
        throw (Error) msg.obj;
      }
      onFinished();
      if (loadable.isLoadCanceled()) {
        callback.onLoadCanceled(loadable);
        return;
      }
      switch (msg.what) {
        case MSG_END_OF_SOURCE:
          callback.onLoadCompleted(loadable);
          break;
        case MSG_IO_EXCEPTION:
          callback.onLoadError(loadable, (IOException) msg.obj);
          break;
      }
    }

    private void onFinished() {
      loading = false;
      currentTask = null;
    }

  }

}

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
package org.telegram.messenger.exoplayer.util;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Pair;
import org.telegram.messenger.exoplayer.upstream.Loader;
import org.telegram.messenger.exoplayer.upstream.Loader.Loadable;
import org.telegram.messenger.exoplayer.upstream.UriDataSource;
import org.telegram.messenger.exoplayer.upstream.UriLoadable;
import java.io.IOException;
import java.util.concurrent.CancellationException;

/**
 * Performs both single and repeated loads of media manifests.
 * <p>
 * Client code is responsible for ensuring that only one load is taking place at any one time.
 * Typical usage of this class is as follows:
 * <ol>
 * <li>Create an instance.</li>
 * <li>Obtain an initial manifest by calling {@link #singleLoad(Looper, ManifestCallback)} and
 *     waiting for the callback to be invoked.</li>
 * <li>For on-demand playbacks, the loader is no longer required. For live playbacks, the loader
 *     may be required to periodically refresh the manifest. In this case it is injected into any
 *     components that require it. These components will call {@link #requestRefresh()} on the
 *     loader whenever a refresh is required.</li>
 * </ol>
 *
 * @param <T> The type of manifest.
 */
public class ManifestFetcher<T> implements Loader.Callback {

  /**
   * Thrown when an error occurs trying to fetch a manifest.
   */
  public static final class ManifestIOException extends IOException{
    public ManifestIOException(Throwable cause) { super(cause); }

  }

  /**
   * Interface definition for a callback to be notified of {@link ManifestFetcher} events.
   */
  public interface EventListener {

    public void onManifestRefreshStarted();

    public void onManifestRefreshed();

    public void onManifestError(IOException e);

  }

  /**
   * Callback for the result of a single load.
   *
   * @param <T> The type of manifest.
   */
  public interface ManifestCallback<T> {

    /**
     * Invoked when the load has successfully completed.
     *
     * @param manifest The loaded manifest.
     */
    void onSingleManifest(T manifest);

    /**
     * Invoked when the load has failed.
     *
     * @param e The cause of the failure.
     */
    void onSingleManifestError(IOException e);

  }

  /**
   * Interface for manifests that are able to specify that subsequent loads should use a different
   * URI.
   */
  public interface RedirectingManifest {

    /**
     * Returns the URI from which subsequent manifests should be requested, or null to continue
     * using the current URI.
     */
    public String getNextManifestUri();

  }

  private final UriLoadable.Parser<T> parser;
  private final UriDataSource uriDataSource;
  private final Handler eventHandler;
  private final EventListener eventListener;

  /* package */ volatile String manifestUri;

  private int enabledCount;
  private Loader loader;
  private UriLoadable<T> currentLoadable;
  private long currentLoadStartTimestamp;

  private int loadExceptionCount;
  private long loadExceptionTimestamp;
  private ManifestIOException loadException;

  private volatile T manifest;
  private volatile long manifestLoadStartTimestamp;
  private volatile long manifestLoadCompleteTimestamp;

  /**
   * @param manifestUri The manifest location.
   * @param uriDataSource The {@link UriDataSource} to use when loading the manifest.
   * @param parser A parser to parse the loaded manifest data.
   */
  public ManifestFetcher(String manifestUri, UriDataSource uriDataSource,
      UriLoadable.Parser<T> parser) {
    this(manifestUri, uriDataSource, parser, null, null);
  }

  /**
   * @param manifestUri The manifest location.
   * @param uriDataSource The {@link UriDataSource} to use when loading the manifest.
   * @param parser A parser to parse the loaded manifest data.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public ManifestFetcher(String manifestUri, UriDataSource uriDataSource,
      UriLoadable.Parser<T> parser, Handler eventHandler, EventListener eventListener) {
    this.parser = parser;
    this.manifestUri = manifestUri;
    this.uriDataSource = uriDataSource;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
  }

  /**
   * Updates the manifest location.
   *
   * @param manifestUri The manifest location.
   */
  public void updateManifestUri(String manifestUri) {
    this.manifestUri = manifestUri;
  }

  /**
   * Performs a single manifest load.
   *
   * @param callbackLooper The looper associated with the thread on which the callback should be
   *     invoked.
   * @param callback The callback to receive the result.
   */
  public void singleLoad(Looper callbackLooper, final ManifestCallback<T> callback) {
    SingleFetchHelper fetchHelper = new SingleFetchHelper(
        new UriLoadable<>(manifestUri, uriDataSource, parser), callbackLooper, callback);
    fetchHelper.startLoading();
  }

  /**
   * Gets a {@link Pair} containing the most recently loaded manifest together with the timestamp
   * at which the load completed.
   *
   * @return The most recently loaded manifest and the timestamp at which the load completed, or
   *     null if no manifest has loaded.
   */
  public T getManifest() {
    return manifest;
  }

  /**
   * Gets the value of {@link SystemClock#elapsedRealtime()} when the last completed load started.
   *
   * @return The value of {@link SystemClock#elapsedRealtime()} when the last completed load
   *     started.
   */
  public long getManifestLoadStartTimestamp() {
    return manifestLoadStartTimestamp;
  }

  /**
   * Gets the value of {@link SystemClock#elapsedRealtime()} when the last load completed.
   *
   * @return The value of {@link SystemClock#elapsedRealtime()} when the last load completed.
   */
  public long getManifestLoadCompleteTimestamp() {
    return manifestLoadCompleteTimestamp;
  }

  /**
   * Throws the error that affected the most recent attempt to load the manifest. Does nothing if
   * the most recent attempt was successful.
   *
   * @throws ManifestIOException The error that affected the most recent attempt to load the
   *     manifest.
   */
  public void maybeThrowError() throws ManifestIOException {
    // Don't throw an exception until at least 1 retry attempt has been made.
    if (loadException == null || loadExceptionCount <= 1) {
      return;
    }
    throw loadException;
  }

  /**
   * Enables refresh functionality.
   */
  public void enable() {
    if (enabledCount++ == 0) {
      loadExceptionCount = 0;
      loadException = null;
    }
  }

  /**
   * Disables refresh functionality.
   */
  public void disable() {
    if (--enabledCount == 0) {
      if (loader != null) {
        loader.release();
        loader = null;
      }
    }
  }

  /**
   * Should be invoked repeatedly by callers who require an updated manifest.
   */
  public void requestRefresh() {
    if (loadException != null && SystemClock.elapsedRealtime()
        < (loadExceptionTimestamp + getRetryDelayMillis(loadExceptionCount))) {
      // The previous load failed, and it's too soon to try again.
      return;
    }
    if (loader == null) {
      loader = new Loader("manifestLoader");
    }
    if (!loader.isLoading()) {
      currentLoadable = new UriLoadable<>(manifestUri, uriDataSource, parser);
      currentLoadStartTimestamp = SystemClock.elapsedRealtime();
      loader.startLoading(currentLoadable, this);
      notifyManifestRefreshStarted();
    }
  }

  @Override
  public void onLoadCompleted(Loadable loadable) {
    if (currentLoadable != loadable) {
      // Stale event.
      return;
    }

    manifest = currentLoadable.getResult();
    manifestLoadStartTimestamp = currentLoadStartTimestamp;
    manifestLoadCompleteTimestamp = SystemClock.elapsedRealtime();
    loadExceptionCount = 0;
    loadException = null;

    if (manifest instanceof RedirectingManifest) {
      RedirectingManifest redirectingManifest = (RedirectingManifest) manifest;
      String nextLocation = redirectingManifest.getNextManifestUri();
      if (!TextUtils.isEmpty(nextLocation)) {
        manifestUri = nextLocation;
      }
    }

    notifyManifestRefreshed();
  }

  @Override
  public void onLoadCanceled(Loadable loadable) {
    // Do nothing.
  }

  @Override
  public void onLoadError(Loadable loadable, IOException exception) {
    if (currentLoadable != loadable) {
      // Stale event.
      return;
    }

    loadExceptionCount++;
    loadExceptionTimestamp = SystemClock.elapsedRealtime();
    loadException = new ManifestIOException(exception);

    notifyManifestError(loadException);
  }

  /* package */ void onSingleFetchCompleted(T result, long loadStartTimestamp) {
    manifest = result;
    manifestLoadStartTimestamp = loadStartTimestamp;
    manifestLoadCompleteTimestamp = SystemClock.elapsedRealtime();
  }

  private long getRetryDelayMillis(long errorCount) {
    return Math.min((errorCount - 1) * 1000, 5000);
  }

  private void notifyManifestRefreshStarted() {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onManifestRefreshStarted();
        }
      });
    }
  }

  private void notifyManifestRefreshed() {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onManifestRefreshed();
        }
      });
    }
  }

  private void notifyManifestError(final IOException e) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onManifestError(e);
        }
      });
    }
  }

  private class SingleFetchHelper implements Loader.Callback {

    private final UriLoadable<T> singleUseLoadable;
    private final Looper callbackLooper;
    private final ManifestCallback<T> wrappedCallback;
    private final Loader singleUseLoader;

    private long loadStartTimestamp;

    public SingleFetchHelper(UriLoadable<T> singleUseLoadable, Looper callbackLooper,
        ManifestCallback<T> wrappedCallback) {
      this.singleUseLoadable = singleUseLoadable;
      this.callbackLooper = callbackLooper;
      this.wrappedCallback = wrappedCallback;
      singleUseLoader = new Loader("manifestLoader:single");
    }

    public void startLoading() {
      loadStartTimestamp = SystemClock.elapsedRealtime();
      singleUseLoader.startLoading(callbackLooper, singleUseLoadable, this);
    }

    @Override
    public void onLoadCompleted(Loadable loadable) {
      try {
        T result = singleUseLoadable.getResult();
        onSingleFetchCompleted(result, loadStartTimestamp);
        wrappedCallback.onSingleManifest(result);
      } finally {
        releaseLoader();
      }
    }

    @Override
    public void onLoadCanceled(Loadable loadable) {
      // This shouldn't ever happen, but handle it anyway.
      try {
        IOException exception = new ManifestIOException(new CancellationException());
        wrappedCallback.onSingleManifestError(exception);
      } finally {
        releaseLoader();
      }
    }

    @Override
    public void onLoadError(Loadable loadable, IOException exception) {
      try {
        wrappedCallback.onSingleManifestError(exception);
      } finally {
        releaseLoader();
      }
    }

    private void releaseLoader() {
      singleUseLoader.release();
    }

  }

}

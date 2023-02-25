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
package com.google.android.exoplayer2.video;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.Choreographer;
import android.view.Choreographer.FrameCallback;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import androidx.annotation.DoNotInline;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Helps a video {@link Renderer} release frames to a {@link Surface}. The helper:
 *
 * <ul>
 *   <li>Adjusts frame release timestamps to achieve a smoother visual result. The release
 *       timestamps are smoothed, and aligned with the default display's vsync signal.
 *   <li>Adjusts the {@link Surface} frame rate to inform the underlying platform of a fixed frame
 *       rate, when there is one.
 * </ul>
 */
public final class VideoFrameReleaseHelper {

  private static final String TAG = "VideoFrameReleaseHelper";

  /**
   * The minimum sum of frame durations used to calculate the current fixed frame rate estimate, for
   * the estimate to be treated as a high confidence estimate.
   */
  private static final long MINIMUM_MATCHING_FRAME_DURATION_FOR_HIGH_CONFIDENCE_NS = 5_000_000_000L;

  /**
   * The minimum change in media frame rate that will trigger a change in surface frame rate, given
   * a high confidence estimate.
   */
  private static final float MINIMUM_MEDIA_FRAME_RATE_CHANGE_FOR_UPDATE_HIGH_CONFIDENCE = 0.02f;

  /**
   * The minimum change in media frame rate that will trigger a change in surface frame rate, given
   * a low confidence estimate.
   */
  private static final float MINIMUM_MEDIA_FRAME_RATE_CHANGE_FOR_UPDATE_LOW_CONFIDENCE = 1f;

  /**
   * The minimum number of frames without a frame rate estimate, for the surface frame rate to be
   * cleared.
   */
  private static final int MINIMUM_FRAMES_WITHOUT_SYNC_TO_CLEAR_SURFACE_FRAME_RATE =
      2 * FixedFrameRateEstimator.CONSECUTIVE_MATCHING_FRAME_DURATIONS_FOR_SYNC;

  /** The period between sampling display VSYNC timestamps, in milliseconds. */
  private static final long VSYNC_SAMPLE_UPDATE_PERIOD_MS = 500;
  /**
   * The maximum adjustment that can be made to a frame release timestamp, in nanoseconds, excluding
   * the part of the adjustment that aligns frame release timestamps with the display VSYNC.
   */
  private static final long MAX_ALLOWED_ADJUSTMENT_NS = 20_000_000;
  /**
   * If a frame is targeted to a display VSYNC with timestamp {@code vsyncTime}, the adjusted frame
   * release timestamp will be calculated as {@code releaseTime = vsyncTime - ((vsyncDuration *
   * VSYNC_OFFSET_PERCENTAGE) / 100)}.
   */
  private static final long VSYNC_OFFSET_PERCENTAGE = 80;

  private final FixedFrameRateEstimator frameRateEstimator;
  @Nullable private final DisplayHelper displayHelper;
  @Nullable private final VSyncSampler vsyncSampler;

  private boolean started;
  @Nullable private Surface surface;

  /** The media frame rate specified in the {@link Format}. */
  private float formatFrameRate;
  /**
   * The media frame rate used to calculate the playback frame rate of the {@link Surface}. This may
   * be different to {@link #formatFrameRate} if {@link #formatFrameRate} is unspecified or
   * inaccurate.
   */
  private float surfaceMediaFrameRate;
  /** The playback frame rate set on the {@link Surface}. */
  private float surfacePlaybackFrameRate;

  private float playbackSpeed;
  private @C.VideoChangeFrameRateStrategy int changeFrameRateStrategy;

  private long vsyncDurationNs;
  private long vsyncOffsetNs;

  private long frameIndex;
  private long pendingLastAdjustedFrameIndex;
  private long pendingLastAdjustedReleaseTimeNs;
  private long lastAdjustedFrameIndex;
  private long lastAdjustedReleaseTimeNs;

  /**
   * Constructs an instance.
   *
   * @param context A context from which information about the default display can be retrieved.
   */
  public VideoFrameReleaseHelper(@Nullable Context context) {
    frameRateEstimator = new FixedFrameRateEstimator();
    displayHelper = maybeBuildDisplayHelper(context);
    vsyncSampler = displayHelper != null ? VSyncSampler.getInstance() : null;
    vsyncDurationNs = C.TIME_UNSET;
    vsyncOffsetNs = C.TIME_UNSET;
    formatFrameRate = Format.NO_VALUE;
    playbackSpeed = 1f;
    changeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS;
  }

  /**
   * Change the {@link C.VideoChangeFrameRateStrategy} used when calling {@link
   * Surface#setFrameRate}.
   */
  public void setChangeFrameRateStrategy(
      @C.VideoChangeFrameRateStrategy int changeFrameRateStrategy) {
    if (this.changeFrameRateStrategy == changeFrameRateStrategy) {
      return;
    }
    this.changeFrameRateStrategy = changeFrameRateStrategy;
    updateSurfacePlaybackFrameRate(/* forceUpdate= */ true);
  }

  /** Called when the renderer is started. */
  public void onStarted() {
    started = true;
    resetAdjustment();
    if (displayHelper != null) {
      checkNotNull(vsyncSampler).addObserver();
      displayHelper.register(this::updateDefaultDisplayRefreshRateParams);
    }
    updateSurfacePlaybackFrameRate(/* forceUpdate= */ false);
  }

  /**
   * Called when the renderer changes which {@link Surface} it's rendering to renders to.
   *
   * @param surface The new {@link Surface}, or {@code null} if the renderer does not have one.
   */
  public void onSurfaceChanged(@Nullable Surface surface) {
    if (surface instanceof PlaceholderSurface) {
      // We don't care about dummy surfaces for release timing, since they're not visible.
      surface = null;
    }
    if (this.surface == surface) {
      return;
    }
    clearSurfaceFrameRate();
    this.surface = surface;
    updateSurfacePlaybackFrameRate(/* forceUpdate= */ true);
  }

  /** Called when the renderer's position is reset. */
  public void onPositionReset() {
    resetAdjustment();
  }

  /**
   * Called when the renderer's playback speed changes.
   *
   * @param playbackSpeed The factor by which playback is sped up.
   */
  public void onPlaybackSpeed(float playbackSpeed) {
    this.playbackSpeed = playbackSpeed;
    resetAdjustment();
    updateSurfacePlaybackFrameRate(/* forceUpdate= */ false);
  }

  /**
   * Called when the renderer's output format changes.
   *
   * @param formatFrameRate The format's frame rate, or {@link Format#NO_VALUE} if unknown.
   */
  public void onFormatChanged(float formatFrameRate) {
    this.formatFrameRate = formatFrameRate;
    frameRateEstimator.reset();
    updateSurfaceMediaFrameRate();
  }

  /**
   * Called by the renderer for each frame, prior to it being skipped, dropped or rendered.
   *
   * @param framePresentationTimeUs The frame presentation timestamp, in microseconds.
   */
  public void onNextFrame(long framePresentationTimeUs) {
    if (pendingLastAdjustedFrameIndex != C.INDEX_UNSET) {
      lastAdjustedFrameIndex = pendingLastAdjustedFrameIndex;
      lastAdjustedReleaseTimeNs = pendingLastAdjustedReleaseTimeNs;
    }
    frameIndex++;
    frameRateEstimator.onNextFrame(framePresentationTimeUs * 1000);
    updateSurfaceMediaFrameRate();
  }

  /** Called when the renderer is stopped. */
  public void onStopped() {
    started = false;
    if (displayHelper != null) {
      displayHelper.unregister();
      checkNotNull(vsyncSampler).removeObserver();
    }
    clearSurfaceFrameRate();
  }

  // Frame release time adjustment.

  /**
   * Adjusts the release timestamp for the next frame. This is the frame whose presentation
   * timestamp was most recently passed to {@link #onNextFrame}.
   *
   * <p>This method may be called any number of times for each frame, including zero times (for
   * skipped frames, or when rendering the first frame prior to playback starting), or more than
   * once (if the caller wishes to give the helper the opportunity to refine a release time closer
   * to when the frame needs to be released).
   *
   * @param releaseTimeNs The frame's unadjusted release time, in nanoseconds and in the same time
   *     base as {@link System#nanoTime()}.
   * @return The adjusted frame release timestamp, in nanoseconds and in the same time base as
   *     {@link System#nanoTime()}.
   */
  public long adjustReleaseTime(long releaseTimeNs) {
    // Until we know better, the adjustment will be a no-op.
    long adjustedReleaseTimeNs = releaseTimeNs;

    if (lastAdjustedFrameIndex != C.INDEX_UNSET && frameRateEstimator.isSynced()) {
      long frameDurationNs = frameRateEstimator.getFrameDurationNs();
      long candidateAdjustedReleaseTimeNs =
          lastAdjustedReleaseTimeNs
              + (long) ((frameDurationNs * (frameIndex - lastAdjustedFrameIndex)) / playbackSpeed);
      if (adjustmentAllowed(releaseTimeNs, candidateAdjustedReleaseTimeNs)) {
        adjustedReleaseTimeNs = candidateAdjustedReleaseTimeNs;
      } else {
        resetAdjustment();
      }
    }
    pendingLastAdjustedFrameIndex = frameIndex;
    pendingLastAdjustedReleaseTimeNs = adjustedReleaseTimeNs;

    if (vsyncSampler == null || vsyncDurationNs == C.TIME_UNSET) {
      return adjustedReleaseTimeNs;
    }
    long sampledVsyncTimeNs = vsyncSampler.sampledVsyncTimeNs;
    if (sampledVsyncTimeNs == C.TIME_UNSET) {
      return adjustedReleaseTimeNs;
    }
    // Find the timestamp of the closest vsync. This is the vsync that we're targeting.
    long snappedTimeNs = closestVsync(adjustedReleaseTimeNs, sampledVsyncTimeNs, vsyncDurationNs);
    // Apply an offset so that we release before the target vsync, but after the previous one.
    return snappedTimeNs - vsyncOffsetNs;
  }

  private void resetAdjustment() {
    frameIndex = 0;
    lastAdjustedFrameIndex = C.INDEX_UNSET;
    pendingLastAdjustedFrameIndex = C.INDEX_UNSET;
  }

  private static boolean adjustmentAllowed(
      long unadjustedReleaseTimeNs, long adjustedReleaseTimeNs) {
    return Math.abs(unadjustedReleaseTimeNs - adjustedReleaseTimeNs) <= MAX_ALLOWED_ADJUSTMENT_NS;
  }

  // Surface frame rate adjustment.

  /**
   * Updates the media frame rate that's used to calculate the playback frame rate of the current
   * {@link #surface}. If the frame rate is updated then {@link #updateSurfacePlaybackFrameRate} is
   * called to update the surface.
   */
  private void updateSurfaceMediaFrameRate() {
    if (Util.SDK_INT < 30 || surface == null) {
      return;
    }

    float candidateFrameRate =
        frameRateEstimator.isSynced() ? frameRateEstimator.getFrameRate() : formatFrameRate;
    if (candidateFrameRate == surfaceMediaFrameRate) {
      return;
    }

    // The candidate is different to the current surface media frame rate. Decide whether to update
    // the surface media frame rate.
    boolean shouldUpdate;
    if (candidateFrameRate != Format.NO_VALUE && surfaceMediaFrameRate != Format.NO_VALUE) {
      boolean candidateIsHighConfidence =
          frameRateEstimator.isSynced()
              && frameRateEstimator.getMatchingFrameDurationSumNs()
                  >= MINIMUM_MATCHING_FRAME_DURATION_FOR_HIGH_CONFIDENCE_NS;
      float minimumChangeForUpdate =
          candidateIsHighConfidence
              ? MINIMUM_MEDIA_FRAME_RATE_CHANGE_FOR_UPDATE_HIGH_CONFIDENCE
              : MINIMUM_MEDIA_FRAME_RATE_CHANGE_FOR_UPDATE_LOW_CONFIDENCE;
      shouldUpdate = Math.abs(candidateFrameRate - surfaceMediaFrameRate) >= minimumChangeForUpdate;
    } else if (candidateFrameRate != Format.NO_VALUE) {
      shouldUpdate = true;
    } else {
      shouldUpdate =
          frameRateEstimator.getFramesWithoutSyncCount()
              >= MINIMUM_FRAMES_WITHOUT_SYNC_TO_CLEAR_SURFACE_FRAME_RATE;
    }

    if (shouldUpdate) {
      surfaceMediaFrameRate = candidateFrameRate;
      updateSurfacePlaybackFrameRate(/* forceUpdate= */ false);
    }
  }

  /**
   * Updates the playback frame rate of the current {@link #surface} based on the playback speed,
   * frame rate of the content, and whether the renderer is started.
   *
   * <p>Does nothing if {@link #changeFrameRateStrategy} is {@link
   * C#VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF}.
   *
   * @param forceUpdate Whether to call {@link Surface#setFrameRate} even if the frame rate is
   *     unchanged.
   */
  private void updateSurfacePlaybackFrameRate(boolean forceUpdate) {
    if (Util.SDK_INT < 30
        || surface == null
        || changeFrameRateStrategy == C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF) {
      return;
    }

    float surfacePlaybackFrameRate = 0;
    if (started && surfaceMediaFrameRate != Format.NO_VALUE) {
      surfacePlaybackFrameRate = surfaceMediaFrameRate * playbackSpeed;
    }
    // We always set the frame-rate if we have a new surface, since we have no way of knowing what
    // it might have been set to previously.
    if (!forceUpdate && this.surfacePlaybackFrameRate == surfacePlaybackFrameRate) {
      return;
    }
    this.surfacePlaybackFrameRate = surfacePlaybackFrameRate;
    Api30.setSurfaceFrameRate(surface, surfacePlaybackFrameRate);
  }

  /**
   * Clears the frame-rate of the current {@link #surface}.
   *
   * <p>Does nothing if {@link #changeFrameRateStrategy} is {@link
   * C#VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF}.
   */
  private void clearSurfaceFrameRate() {
    if (Util.SDK_INT < 30
        || surface == null
        || changeFrameRateStrategy == C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
        || surfacePlaybackFrameRate == 0) {
      return;
    }
    surfacePlaybackFrameRate = 0;
    Api30.setSurfaceFrameRate(surface, /* frameRate= */ 0);
  }

  // Display refresh rate and vsync logic.

  private void updateDefaultDisplayRefreshRateParams(@Nullable Display defaultDisplay) {
    if (defaultDisplay != null) {
      double defaultDisplayRefreshRate = defaultDisplay.getRefreshRate();
      vsyncDurationNs = (long) (C.NANOS_PER_SECOND / defaultDisplayRefreshRate);
      vsyncOffsetNs = (vsyncDurationNs * VSYNC_OFFSET_PERCENTAGE) / 100;
    } else {
      Log.w(TAG, "Unable to query display refresh rate");
      vsyncDurationNs = C.TIME_UNSET;
      vsyncOffsetNs = C.TIME_UNSET;
    }
  }

  private static long closestVsync(long releaseTime, long sampledVsyncTime, long vsyncDuration) {
    long vsyncCount = (releaseTime - sampledVsyncTime) / vsyncDuration;
    long snappedTimeNs = sampledVsyncTime + (vsyncDuration * vsyncCount);
    long snappedBeforeNs;
    long snappedAfterNs;
    if (releaseTime <= snappedTimeNs) {
      snappedBeforeNs = snappedTimeNs - vsyncDuration;
      snappedAfterNs = snappedTimeNs;
    } else {
      snappedBeforeNs = snappedTimeNs;
      snappedAfterNs = snappedTimeNs + vsyncDuration;
    }
    long snappedAfterDiff = snappedAfterNs - releaseTime;
    long snappedBeforeDiff = releaseTime - snappedBeforeNs;
    return snappedAfterDiff < snappedBeforeDiff ? snappedAfterNs : snappedBeforeNs;
  }

  @Nullable
  private static DisplayHelper maybeBuildDisplayHelper(@Nullable Context context) {
    @Nullable DisplayHelper displayHelper = null;
    if (context != null) {
      context = context.getApplicationContext();
      if (Util.SDK_INT >= 17) {
        displayHelper = DisplayHelperV17.maybeBuildNewInstance(context);
      }
      if (displayHelper == null) {
        displayHelper = DisplayHelperV16.maybeBuildNewInstance(context);
      }
    }
    return displayHelper;
  }

  // Nested classes.

  @RequiresApi(30)
  private static final class Api30 {
    @DoNotInline
    public static void setSurfaceFrameRate(Surface surface, float frameRate) {
      int compatibility =
          frameRate == 0
              ? Surface.FRAME_RATE_COMPATIBILITY_DEFAULT
              : Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE;
      try {
        surface.setFrameRate(frameRate, compatibility);
      } catch (IllegalStateException e) {
        Log.e(TAG, "Failed to call Surface.setFrameRate", e);
      }
    }
  }

  /** Helper for listening to changes to the default display. */
  private interface DisplayHelper {

    /** Listener for changes to the default display. */
    interface Listener {

      /**
       * Called when the default display changes.
       *
       * @param defaultDisplay The default display, or {@code null} if a corresponding {@link
       *     Display} object could not be obtained.
       */
      void onDefaultDisplayChanged(@Nullable Display defaultDisplay);
    }

    /**
     * Enables the helper, invoking {@link Listener#onDefaultDisplayChanged(Display)} to pass the
     * initial default display.
     */
    void register(Listener listener);

    /** Disables the helper. */
    void unregister();
  }

  private static final class DisplayHelperV16 implements DisplayHelper {

    @Nullable
    public static DisplayHelper maybeBuildNewInstance(Context context) {
      WindowManager windowManager =
          (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
      return windowManager != null ? new DisplayHelperV16(windowManager) : null;
    }

    private final WindowManager windowManager;

    private DisplayHelperV16(WindowManager windowManager) {
      this.windowManager = windowManager;
    }

    @Override
    public void register(Listener listener) {
      listener.onDefaultDisplayChanged(windowManager.getDefaultDisplay());
    }

    @Override
    public void unregister() {
      // Do nothing.
    }
  }

  @RequiresApi(17)
  private static final class DisplayHelperV17
      implements DisplayHelper, DisplayManager.DisplayListener {

    @Nullable
    public static DisplayHelper maybeBuildNewInstance(Context context) {
      DisplayManager displayManager =
          (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
      return displayManager != null ? new DisplayHelperV17(displayManager) : null;
    }

    private final DisplayManager displayManager;
    @Nullable private Listener listener;

    private DisplayHelperV17(DisplayManager displayManager) {
      this.displayManager = displayManager;
    }

    @Override
    public void register(Listener listener) {
      this.listener = listener;
      displayManager.registerDisplayListener(this, Util.createHandlerForCurrentLooper());
      listener.onDefaultDisplayChanged(getDefaultDisplay());
    }

    @Override
    public void unregister() {
      displayManager.unregisterDisplayListener(this);
      listener = null;
    }

    @Override
    public void onDisplayChanged(int displayId) {
      if (listener != null && displayId == Display.DEFAULT_DISPLAY) {
        listener.onDefaultDisplayChanged(getDefaultDisplay());
      }
    }

    @Override
    public void onDisplayAdded(int displayId) {
      // Do nothing.
    }

    @Override
    public void onDisplayRemoved(int displayId) {
      // Do nothing.
    }

    private Display getDefaultDisplay() {
      return displayManager.getDisplay(Display.DEFAULT_DISPLAY);
    }
  }

  /**
   * Samples display vsync timestamps. A single instance using a single {@link Choreographer} is
   * shared by all {@link VideoFrameReleaseHelper} instances. This is done to avoid a resource leak
   * in the platform on API levels prior to 23. See [Internal: b/12455729].
   */
  private static final class VSyncSampler implements FrameCallback, Handler.Callback {

    public volatile long sampledVsyncTimeNs;

    private static final int CREATE_CHOREOGRAPHER = 0;
    private static final int MSG_ADD_OBSERVER = 1;
    private static final int MSG_REMOVE_OBSERVER = 2;

    private static final VSyncSampler INSTANCE = new VSyncSampler();

    private final Handler handler;
    private final HandlerThread choreographerOwnerThread;
    private @MonotonicNonNull Choreographer choreographer;
    private int observerCount;

    public static VSyncSampler getInstance() {
      return INSTANCE;
    }

    private VSyncSampler() {
      sampledVsyncTimeNs = C.TIME_UNSET;
      choreographerOwnerThread = new HandlerThread("ExoPlayer:FrameReleaseChoreographer");
      choreographerOwnerThread.start();
      handler = Util.createHandler(choreographerOwnerThread.getLooper(), /* callback= */ this);
      handler.sendEmptyMessage(CREATE_CHOREOGRAPHER);
    }

    /**
     * Notifies the sampler that a {@link VideoFrameReleaseHelper} is observing {@link
     * #sampledVsyncTimeNs}, and hence that the value should be periodically updated.
     */
    public void addObserver() {
      handler.sendEmptyMessage(MSG_ADD_OBSERVER);
    }

    /**
     * Notifies the sampler that a {@link VideoFrameReleaseHelper} is no longer observing {@link
     * #sampledVsyncTimeNs}.
     */
    public void removeObserver() {
      handler.sendEmptyMessage(MSG_REMOVE_OBSERVER);
    }

    @Override
    public void doFrame(long vsyncTimeNs) {
      sampledVsyncTimeNs = vsyncTimeNs;
      checkNotNull(choreographer).postFrameCallbackDelayed(this, VSYNC_SAMPLE_UPDATE_PERIOD_MS);
    }

    @Override
    public boolean handleMessage(Message message) {
      switch (message.what) {
        case CREATE_CHOREOGRAPHER:
          createChoreographerInstanceInternal();
          return true;
        case MSG_ADD_OBSERVER:
          addObserverInternal();
          return true;
        case MSG_REMOVE_OBSERVER:
          removeObserverInternal();
          return true;
        default:
          return false;
      }
    }

    private void createChoreographerInstanceInternal() {
      try {
        choreographer = Choreographer.getInstance();
      } catch (RuntimeException e) {
        // See [Internal: b/213926330].
        Log.w(TAG, "Vsync sampling disabled due to platform error", e);
      }
    }

    private void addObserverInternal() {
      if (choreographer != null) {
        observerCount++;
        if (observerCount == 1) {
          choreographer.postFrameCallback(this);
        }
      }
    }

    private void removeObserverInternal() {
      if (choreographer != null) {
        observerCount--;
        if (observerCount == 0) {
          choreographer.removeFrameCallback(this);
          sampledVsyncTimeNs = C.TIME_UNSET;
        }
      }
    }
  }
}

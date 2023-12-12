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

import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.media.MediaCodec;
import android.media.MediaCodec.CodecException;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.decoder.DecoderException;
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation;
import com.google.android.exoplayer2.util.Assertions;

/**
 * Listener of video {@link Renderer} events. All methods have no-op default implementations to
 * allow selective overrides.
 */
public interface VideoRendererEventListener {

  /**
   * Called when the renderer is enabled.
   *
   * @param counters {@link DecoderCounters} that will be updated by the renderer for as long as it
   *     remains enabled.
   */
  default void onVideoEnabled(DecoderCounters counters) {}

  /**
   * Called when a decoder is created.
   *
   * @param decoderName The decoder that was created.
   * @param initializedTimestampMs {@link SystemClock#elapsedRealtime()} when initialization
   *     finished.
   * @param initializationDurationMs The time taken to initialize the decoder in milliseconds.
   */
  default void onVideoDecoderInitialized(
      String decoderName, long initializedTimestampMs, long initializationDurationMs) {}

  /**
   * @deprecated Use {@link #onVideoInputFormatChanged(Format, DecoderReuseEvaluation)}.
   */
  @Deprecated
  default void onVideoInputFormatChanged(Format format) {}

  /**
   * Called when the format of the media being consumed by the renderer changes.
   *
   * @param format The new format.
   * @param decoderReuseEvaluation The result of the evaluation to determine whether an existing
   *     decoder instance can be reused for the new format, or {@code null} if the renderer did not
   *     have a decoder.
   */
  default void onVideoInputFormatChanged(
      Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {}

  /**
   * Called to report the number of frames dropped by the renderer. Dropped frames are reported
   * whenever the renderer is stopped having dropped frames, and optionally, whenever the count
   * reaches a specified threshold whilst the renderer is started.
   *
   * @param count The number of dropped frames.
   * @param elapsedMs The duration in milliseconds over which the frames were dropped. This duration
   *     is timed from when the renderer was started or from when dropped frames were last reported
   *     (whichever was more recent), and not from when the first of the reported drops occurred.
   */
  default void onDroppedFrames(int count, long elapsedMs) {}

  /**
   * Called to report the video processing offset of video frames processed by the video renderer.
   *
   * <p>Video processing offset represents how early a video frame is processed compared to the
   * player's current position. For each video frame, the offset is calculated as <em>P<sub>vf</sub>
   * - P<sub>pl</sub></em> where <em>P<sub>vf</sub></em> is the presentation timestamp of the video
   * frame and <em>P<sub>pl</sub></em> is the current position of the player. Positive values
   * indicate the frame was processed early enough whereas negative values indicate that the
   * player's position had progressed beyond the frame's timestamp when the frame was processed (and
   * the frame was probably dropped).
   *
   * <p>The renderer reports the sum of video processing offset samples (one sample per processed
   * video frame: dropped, skipped or rendered) and the total number of samples.
   *
   * @param totalProcessingOffsetUs The sum of all video frame processing offset samples for the
   *     video frames processed by the renderer in microseconds.
   * @param frameCount The number of samples included in the {@code totalProcessingOffsetUs}.
   */
  default void onVideoFrameProcessingOffset(long totalProcessingOffsetUs, int frameCount) {}

  /**
   * Called before a frame is rendered for the first time since setting the surface, and each time
   * there's a change in the size, rotation or pixel aspect ratio of the video being rendered.
   *
   * @param videoSize The new size of the video.
   */
  default void onVideoSizeChanged(VideoSize videoSize) {}

  /**
   * Called when a frame is rendered for the first time since setting the output, or since the
   * renderer was reset, or since the stream being rendered was changed.
   *
   * @param output The output of the video renderer. Normally a {@link Surface}, however some video
   *     renderers may have other output types (e.g., a {@link VideoDecoderOutputBufferRenderer}).
   * @param renderTimeMs The {@link SystemClock#elapsedRealtime()} when the frame was rendered.
   */
  default void onRenderedFirstFrame(Object output, long renderTimeMs) {}

  /**
   * Called when a decoder is released.
   *
   * @param decoderName The decoder that was released.
   */
  default void onVideoDecoderReleased(String decoderName) {}

  /**
   * Called when the renderer is disabled.
   *
   * @param counters {@link DecoderCounters} that were updated by the renderer.
   */
  default void onVideoDisabled(DecoderCounters counters) {}

  /**
   * Called when a video decoder encounters an error.
   *
   * <p>This method being called does not indicate that playback has failed, or that it will fail.
   * The player may be able to recover from the error. Hence applications should <em>not</em>
   * implement this method to display a user visible error or initiate an application level retry.
   * {@link Player.Listener#onPlayerError} is the appropriate place to implement such behavior. This
   * method is called to provide the application with an opportunity to log the error if it wishes
   * to do so.
   *
   * @param videoCodecError The error. Typically a {@link CodecException} if the renderer uses
   *     {@link MediaCodec}, or a {@link DecoderException} if the renderer uses a software decoder.
   */
  default void onVideoCodecError(Exception videoCodecError) {}

  /** Dispatches events to a {@link VideoRendererEventListener}. */
  final class EventDispatcher {

    @Nullable private final Handler handler;
    @Nullable private final VideoRendererEventListener listener;

    /**
     * @param handler A handler for dispatching events, or null if events should not be dispatched.
     * @param listener The listener to which events should be dispatched, or null if events should
     *     not be dispatched.
     */
    public EventDispatcher(
        @Nullable Handler handler, @Nullable VideoRendererEventListener listener) {
      this.handler = listener != null ? Assertions.checkNotNull(handler) : null;
      this.listener = listener;
    }

    /** Invokes {@link VideoRendererEventListener#onVideoEnabled(DecoderCounters)}. */
    public void enabled(DecoderCounters decoderCounters) {
      if (handler != null) {
        handler.post(() -> castNonNull(listener).onVideoEnabled(decoderCounters));
      }
    }

    /** Invokes {@link VideoRendererEventListener#onVideoDecoderInitialized(String, long, long)}. */
    public void decoderInitialized(
        String decoderName, long initializedTimestampMs, long initializationDurationMs) {
      if (handler != null) {
        handler.post(
            () ->
                castNonNull(listener)
                    .onVideoDecoderInitialized(
                        decoderName, initializedTimestampMs, initializationDurationMs));
      }
    }

    /**
     * Invokes {@link VideoRendererEventListener#onVideoInputFormatChanged(Format,
     * DecoderReuseEvaluation)}.
     */
    @SuppressWarnings("deprecation") // Calling deprecated listener method.
    public void inputFormatChanged(
        Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
      if (handler != null) {
        handler.post(
            () -> {
              castNonNull(listener).onVideoInputFormatChanged(format);
              castNonNull(listener).onVideoInputFormatChanged(format, decoderReuseEvaluation);
            });
      }
    }

    /** Invokes {@link VideoRendererEventListener#onDroppedFrames(int, long)}. */
    public void droppedFrames(int droppedFrameCount, long elapsedMs) {
      if (handler != null) {
        handler.post(() -> castNonNull(listener).onDroppedFrames(droppedFrameCount, elapsedMs));
      }
    }

    /** Invokes {@link VideoRendererEventListener#onVideoFrameProcessingOffset}. */
    public void reportVideoFrameProcessingOffset(long totalProcessingOffsetUs, int frameCount) {
      if (handler != null) {
        handler.post(
            () ->
                castNonNull(listener)
                    .onVideoFrameProcessingOffset(totalProcessingOffsetUs, frameCount));
      }
    }

    /** Invokes {@link VideoRendererEventListener#onVideoSizeChanged(VideoSize)}. */
    public void videoSizeChanged(VideoSize videoSize) {
      if (handler != null) {
        handler.post(() -> castNonNull(listener).onVideoSizeChanged(videoSize));
      }
    }

    /** Invokes {@link VideoRendererEventListener#onRenderedFirstFrame(Object, long)}. */
    public void renderedFirstFrame(Object output) {
      if (handler != null) {
        // TODO: Replace this timestamp with the actual frame release time.
        long renderTimeMs = SystemClock.elapsedRealtime();
        handler.post(() -> castNonNull(listener).onRenderedFirstFrame(output, renderTimeMs));
      }
    }

    /** Invokes {@link VideoRendererEventListener#onVideoDecoderReleased(String)}. */
    public void decoderReleased(String decoderName) {
      if (handler != null) {
        handler.post(() -> castNonNull(listener).onVideoDecoderReleased(decoderName));
      }
    }

    /** Invokes {@link VideoRendererEventListener#onVideoDisabled(DecoderCounters)}. */
    public void disabled(DecoderCounters counters) {
      counters.ensureUpdated();
      if (handler != null) {
        handler.post(
            () -> {
              counters.ensureUpdated();
              castNonNull(listener).onVideoDisabled(counters);
            });
      }
    }

    /** Invokes {@link VideoRendererEventListener#onVideoCodecError(Exception)}. */
    public void videoCodecError(Exception videoCodecError) {
      if (handler != null) {
        handler.post(() -> castNonNull(listener).onVideoCodecError(videoCodecError));
      }
    }
  }
}

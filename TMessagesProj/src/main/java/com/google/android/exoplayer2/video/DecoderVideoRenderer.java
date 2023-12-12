/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.DISCARD_REASON_DRM_SESSION_CHANGED;
import static com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.DISCARD_REASON_REUSE_NOT_IMPLEMENTED;
import static com.google.android.exoplayer2.decoder.DecoderReuseEvaluation.REUSE_RESULT_NO;
import static com.google.android.exoplayer2.source.SampleStream.FLAG_REQUIRE_FORMAT;
import static java.lang.Math.max;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Handler;
import android.os.SystemClock;
import android.view.Surface;
import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.BaseRenderer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.C.VideoOutputMode;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlayerMessage;
import com.google.android.exoplayer2.decoder.CryptoConfig;
import com.google.android.exoplayer2.decoder.Decoder;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.decoder.DecoderException;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation;
import com.google.android.exoplayer2.decoder.VideoDecoderOutputBuffer;
import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.drm.DrmSession.DrmSessionException;
import com.google.android.exoplayer2.source.SampleStream.ReadDataResult;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.TimedValueQueue;
import com.google.android.exoplayer2.util.TraceUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoRendererEventListener.EventDispatcher;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Decodes and renders video using a {@link Decoder}.
 *
 * <p>This renderer accepts the following messages sent via {@link
 * ExoPlayer#createMessage(PlayerMessage.Target)} on the playback thread:
 *
 * <ul>
 *   <li>Message with type {@link #MSG_SET_VIDEO_OUTPUT} to set the output surface. The message
 *       payload should be the target {@link Surface} or {@link VideoDecoderOutputBufferRenderer},
 *       or null. Other non-null payloads have the effect of clearing the output.
 *   <li>Message with type {@link #MSG_SET_VIDEO_FRAME_METADATA_LISTENER} to set a listener for
 *       metadata associated with frames being rendered. The message payload should be the {@link
 *       VideoFrameMetadataListener}, or null.
 * </ul>
 */
public abstract class DecoderVideoRenderer extends BaseRenderer {

  private static final String TAG = "DecoderVideoRenderer";

  /** Decoder reinitialization states. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    REINITIALIZATION_STATE_NONE,
    REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM,
    REINITIALIZATION_STATE_WAIT_END_OF_STREAM
  })
  private @interface ReinitializationState {}
  /** The decoder does not need to be re-initialized. */
  private static final int REINITIALIZATION_STATE_NONE = 0;
  /**
   * The input format has changed in a way that requires the decoder to be re-initialized, but we
   * haven't yet signaled an end of stream to the existing decoder. We need to do so in order to
   * ensure that it outputs any remaining buffers before we release it.
   */
  private static final int REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM = 1;
  /**
   * The input format has changed in a way that requires the decoder to be re-initialized, and we've
   * signaled an end of stream to the existing decoder. We're waiting for the decoder to output an
   * end of stream signal to indicate that it has output any remaining buffers before we release it.
   */
  private static final int REINITIALIZATION_STATE_WAIT_END_OF_STREAM = 2;

  private final long allowedJoiningTimeMs;
  private final int maxDroppedFramesToNotify;
  private final EventDispatcher eventDispatcher;
  private final TimedValueQueue<Format> formatQueue;
  private final DecoderInputBuffer flagsOnlyBuffer;

  private Format inputFormat;
  private Format outputFormat;

  @Nullable
  private Decoder<
          DecoderInputBuffer, ? extends VideoDecoderOutputBuffer, ? extends DecoderException>
      decoder;

  private DecoderInputBuffer inputBuffer;
  private VideoDecoderOutputBuffer outputBuffer;
  private @VideoOutputMode int outputMode;
  @Nullable private Object output;
  @Nullable private Surface outputSurface;
  @Nullable private VideoDecoderOutputBufferRenderer outputBufferRenderer;
  @Nullable private VideoFrameMetadataListener frameMetadataListener;

  @Nullable private DrmSession decoderDrmSession;
  @Nullable private DrmSession sourceDrmSession;

  private @ReinitializationState int decoderReinitializationState;
  private boolean decoderReceivedBuffers;

  private boolean renderedFirstFrameAfterReset;
  private boolean mayRenderFirstFrameAfterEnableIfNotStarted;
  private boolean renderedFirstFrameAfterEnable;
  private long initialPositionUs;
  private long joiningDeadlineMs;
  private boolean waitingForFirstSampleInFormat;

  private boolean inputStreamEnded;
  private boolean outputStreamEnded;
  @Nullable private VideoSize reportedVideoSize;

  private long droppedFrameAccumulationStartTimeMs;
  private int droppedFrames;
  private int consecutiveDroppedFrameCount;
  private int buffersInCodecCount;
  private long lastRenderTimeUs;
  private long outputStreamOffsetUs;

  /** Decoder event counters used for debugging purposes. */
  protected DecoderCounters decoderCounters;

  /**
   * @param allowedJoiningTimeMs The maximum duration in milliseconds for which this video renderer
   *     can attempt to seamlessly join an ongoing playback.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param maxDroppedFramesToNotify The maximum number of frames that can be dropped between
   *     invocations of {@link VideoRendererEventListener#onDroppedFrames(int, long)}.
   */
  protected DecoderVideoRenderer(
      long allowedJoiningTimeMs,
      @Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener,
      int maxDroppedFramesToNotify) {
    super(C.TRACK_TYPE_VIDEO);
    this.allowedJoiningTimeMs = allowedJoiningTimeMs;
    this.maxDroppedFramesToNotify = maxDroppedFramesToNotify;
    joiningDeadlineMs = C.TIME_UNSET;
    clearReportedVideoSize();
    formatQueue = new TimedValueQueue<>();
    flagsOnlyBuffer = DecoderInputBuffer.newNoDataInstance();
    eventDispatcher = new EventDispatcher(eventHandler, eventListener);
    decoderReinitializationState = REINITIALIZATION_STATE_NONE;
    outputMode = C.VIDEO_OUTPUT_MODE_NONE;
  }

  // BaseRenderer implementation.

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (outputStreamEnded) {
      return;
    }

    if (inputFormat == null) {
      // We don't have a format yet, so try and read one.
      FormatHolder formatHolder = getFormatHolder();
      flagsOnlyBuffer.clear();
      @ReadDataResult int result = readSource(formatHolder, flagsOnlyBuffer, FLAG_REQUIRE_FORMAT);
      if (result == C.RESULT_FORMAT_READ) {
        onInputFormatChanged(formatHolder);
      } else if (result == C.RESULT_BUFFER_READ) {
        // End of stream read having not read a format.
        Assertions.checkState(flagsOnlyBuffer.isEndOfStream());
        inputStreamEnded = true;
        outputStreamEnded = true;
        return;
      } else {
        // We still don't have a format and can't make progress without one.
        return;
      }
    }

    // If we don't have a decoder yet, we need to instantiate one.
    maybeInitDecoder();

    if (decoder != null) {
      try {
        // Rendering loop.
        TraceUtil.beginSection("drainAndFeed");
        while (drainOutputBuffer(positionUs, elapsedRealtimeUs)) {}
        while (feedInputBuffer()) {}
        TraceUtil.endSection();
      } catch (DecoderException e) {
        Log.e(TAG, "Video codec error", e);
        eventDispatcher.videoCodecError(e);
        throw createRendererException(e, inputFormat, PlaybackException.ERROR_CODE_DECODING_FAILED);
      }
      decoderCounters.ensureUpdated();
    }
  }

  @Override
  public boolean isEnded() {
    return outputStreamEnded;
  }

  @Override
  public boolean isReady() {
    if (inputFormat != null
        && (isSourceReady() || outputBuffer != null)
        && (renderedFirstFrameAfterReset || !hasOutput())) {
      // Ready. If we were joining then we've now joined, so clear the joining deadline.
      joiningDeadlineMs = C.TIME_UNSET;
      return true;
    } else if (joiningDeadlineMs == C.TIME_UNSET) {
      // Not joining.
      return false;
    } else if (SystemClock.elapsedRealtime() < joiningDeadlineMs) {
      // Joining and still within the joining deadline.
      return true;
    } else {
      // The joining deadline has been exceeded. Give up and clear the deadline.
      joiningDeadlineMs = C.TIME_UNSET;
      return false;
    }
  }

  // PlayerMessage.Target implementation.

  @Override
  public void handleMessage(@MessageType int messageType, @Nullable Object message)
      throws ExoPlaybackException {
    if (messageType == MSG_SET_VIDEO_OUTPUT) {
      setOutput(message);
    } else if (messageType == MSG_SET_VIDEO_FRAME_METADATA_LISTENER) {
      frameMetadataListener = (VideoFrameMetadataListener) message;
    } else {
      super.handleMessage(messageType, message);
    }
  }

  // Protected methods.

  @Override
  protected void onEnabled(boolean joining, boolean mayRenderStartOfStream)
      throws ExoPlaybackException {
    decoderCounters = new DecoderCounters();
    eventDispatcher.enabled(decoderCounters);
    mayRenderFirstFrameAfterEnableIfNotStarted = mayRenderStartOfStream;
    renderedFirstFrameAfterEnable = false;
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    inputStreamEnded = false;
    outputStreamEnded = false;
    clearRenderedFirstFrame();
    initialPositionUs = C.TIME_UNSET;
    consecutiveDroppedFrameCount = 0;
    if (decoder != null) {
      flushDecoder();
    }
    if (joining) {
      setJoiningDeadlineMs();
    } else {
      joiningDeadlineMs = C.TIME_UNSET;
    }
    formatQueue.clear();
  }

  @Override
  protected void onStarted() {
    droppedFrames = 0;
    droppedFrameAccumulationStartTimeMs = SystemClock.elapsedRealtime();
    lastRenderTimeUs = SystemClock.elapsedRealtime() * 1000;
  }

  @Override
  protected void onStopped() {
    joiningDeadlineMs = C.TIME_UNSET;
    maybeNotifyDroppedFrames();
  }

  @Override
  protected void onDisabled() {
    inputFormat = null;
    clearReportedVideoSize();
    clearRenderedFirstFrame();
    try {
      setSourceDrmSession(null);
      releaseDecoder();
    } finally {
      eventDispatcher.disabled(decoderCounters);
    }
  }

  @Override
  protected void onStreamChanged(Format[] formats, long startPositionUs, long offsetUs)
      throws ExoPlaybackException {
    // TODO: This shouldn't just update the output stream offset as long as there are still buffers
    // of the previous stream in the decoder. It should also make sure to render the first frame of
    // the next stream if the playback position reached the new stream.
    outputStreamOffsetUs = offsetUs;
    super.onStreamChanged(formats, startPositionUs, offsetUs);
  }

  /**
   * Flushes the decoder.
   *
   * @throws ExoPlaybackException If an error occurs reinitializing a decoder.
   */
  @CallSuper
  protected void flushDecoder() throws ExoPlaybackException {
    buffersInCodecCount = 0;
    if (decoderReinitializationState != REINITIALIZATION_STATE_NONE) {
      releaseDecoder();
      maybeInitDecoder();
    } else {
      inputBuffer = null;
      if (outputBuffer != null) {
        outputBuffer.release();
        outputBuffer = null;
      }
      decoder.flush();
      decoderReceivedBuffers = false;
    }
  }

  /** Releases the decoder. */
  @CallSuper
  protected void releaseDecoder() {
    inputBuffer = null;
    outputBuffer = null;
    decoderReinitializationState = REINITIALIZATION_STATE_NONE;
    decoderReceivedBuffers = false;
    buffersInCodecCount = 0;
    if (decoder != null) {
      decoderCounters.decoderReleaseCount++;
      decoder.release();
      eventDispatcher.decoderReleased(decoder.getName());
      decoder = null;
    }
    setDecoderDrmSession(null);
  }

  /**
   * Called when a new format is read from the upstream source.
   *
   * @param formatHolder A {@link FormatHolder} that holds the new {@link Format}.
   * @throws ExoPlaybackException If an error occurs (re-)initializing the decoder.
   */
  @CallSuper
  protected void onInputFormatChanged(FormatHolder formatHolder) throws ExoPlaybackException {
    waitingForFirstSampleInFormat = true;
    Format newFormat = Assertions.checkNotNull(formatHolder.format);
    setSourceDrmSession(formatHolder.drmSession);
    Format oldFormat = inputFormat;
    inputFormat = newFormat;

    if (decoder == null) {
      maybeInitDecoder();
      eventDispatcher.inputFormatChanged(inputFormat, /* decoderReuseEvaluation= */ null);
      return;
    }

    DecoderReuseEvaluation evaluation;
    if (sourceDrmSession != decoderDrmSession) {
      evaluation =
          new DecoderReuseEvaluation(
              decoder.getName(),
              oldFormat,
              newFormat,
              REUSE_RESULT_NO,
              DISCARD_REASON_DRM_SESSION_CHANGED);
    } else {
      evaluation = canReuseDecoder(decoder.getName(), oldFormat, newFormat);
    }

    if (evaluation.result == REUSE_RESULT_NO) {
      if (decoderReceivedBuffers) {
        // Signal end of stream and wait for any final output buffers before re-initialization.
        decoderReinitializationState = REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM;
      } else {
        // There aren't any final output buffers, so release the decoder immediately.
        releaseDecoder();
        maybeInitDecoder();
      }
    }
    eventDispatcher.inputFormatChanged(inputFormat, evaluation);
  }

  /**
   * Called immediately before an input buffer is queued into the decoder.
   *
   * <p>The default implementation is a no-op.
   *
   * @param buffer The buffer that will be queued.
   */
  protected void onQueueInputBuffer(DecoderInputBuffer buffer) {
    // Do nothing.
  }

  /**
   * Called when an output buffer is successfully processed.
   *
   * @param presentationTimeUs The timestamp associated with the output buffer.
   */
  @CallSuper
  protected void onProcessedOutputBuffer(long presentationTimeUs) {
    buffersInCodecCount--;
  }

  /**
   * Returns whether the buffer being processed should be dropped.
   *
   * @param earlyUs The time until the buffer should be presented in microseconds. A negative value
   *     indicates that the buffer is late.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     measured at the start of the current iteration of the rendering loop.
   */
  protected boolean shouldDropOutputBuffer(long earlyUs, long elapsedRealtimeUs) {
    return isBufferLate(earlyUs);
  }

  /**
   * Returns whether to drop all buffers from the buffer being processed to the keyframe at or after
   * the current playback position, if possible.
   *
   * @param earlyUs The time until the current buffer should be presented in microseconds. A
   *     negative value indicates that the buffer is late.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     measured at the start of the current iteration of the rendering loop.
   */
  protected boolean shouldDropBuffersToKeyframe(long earlyUs, long elapsedRealtimeUs) {
    return isBufferVeryLate(earlyUs);
  }

  /**
   * Returns whether to force rendering an output buffer.
   *
   * @param earlyUs The time until the current buffer should be presented in microseconds. A
   *     negative value indicates that the buffer is late.
   * @param elapsedSinceLastRenderUs The elapsed time since the last output buffer was rendered, in
   *     microseconds.
   * @return Returns whether to force rendering an output buffer.
   */
  protected boolean shouldForceRenderOutputBuffer(long earlyUs, long elapsedSinceLastRenderUs) {
    return isBufferLate(earlyUs) && elapsedSinceLastRenderUs > 100000;
  }

  /**
   * Skips the specified output buffer and releases it.
   *
   * @param outputBuffer The output buffer to skip.
   */
  protected void skipOutputBuffer(VideoDecoderOutputBuffer outputBuffer) {
    decoderCounters.skippedOutputBufferCount++;
    outputBuffer.release();
  }

  /**
   * Drops the specified output buffer and releases it.
   *
   * @param outputBuffer The output buffer to drop.
   */
  protected void dropOutputBuffer(VideoDecoderOutputBuffer outputBuffer) {
    updateDroppedBufferCounters(
        /* droppedInputBufferCount= */ 0, /* droppedDecoderBufferCount= */ 1);
    outputBuffer.release();
  }

  /**
   * Drops frames from the current output buffer to the next keyframe at or before the playback
   * position. If no such keyframe exists, as the playback position is inside the same group of
   * pictures as the buffer being processed, returns {@code false}. Returns {@code true} otherwise.
   *
   * @param positionUs The current playback position, in microseconds.
   * @return Whether any buffers were dropped.
   * @throws ExoPlaybackException If an error occurs flushing the decoder.
   */
  protected boolean maybeDropBuffersToKeyframe(long positionUs) throws ExoPlaybackException {
    int droppedSourceBufferCount = skipSource(positionUs);
    if (droppedSourceBufferCount == 0) {
      return false;
    }
    decoderCounters.droppedToKeyframeCount++;
    // We dropped some buffers to catch up, so update the decoder counters and flush the decoder,
    // which releases all pending buffers buffers including the current output buffer.
    updateDroppedBufferCounters(
        droppedSourceBufferCount, /* droppedDecoderBufferCount= */ buffersInCodecCount);
    flushDecoder();
    return true;
  }

  /**
   * Updates local counters and {@link #decoderCounters} to reflect that buffers were dropped.
   *
   * @param droppedInputBufferCount The number of buffers dropped from the source before being
   *     passed to the decoder.
   * @param droppedDecoderBufferCount The number of buffers dropped after being passed to the
   *     decoder.
   */
  protected void updateDroppedBufferCounters(
      int droppedInputBufferCount, int droppedDecoderBufferCount) {
    decoderCounters.droppedInputBufferCount += droppedInputBufferCount;
    int totalDroppedBufferCount = droppedInputBufferCount + droppedDecoderBufferCount;
    decoderCounters.droppedBufferCount += totalDroppedBufferCount;
    droppedFrames += totalDroppedBufferCount;
    consecutiveDroppedFrameCount += totalDroppedBufferCount;
    decoderCounters.maxConsecutiveDroppedBufferCount =
        max(consecutiveDroppedFrameCount, decoderCounters.maxConsecutiveDroppedBufferCount);
    if (maxDroppedFramesToNotify > 0 && droppedFrames >= maxDroppedFramesToNotify) {
      maybeNotifyDroppedFrames();
    }
  }

  /**
   * Creates a decoder for the given format.
   *
   * @param format The format for which a decoder is required.
   * @param cryptoConfig The {@link CryptoConfig} object required for decoding encrypted content.
   *     May be null and can be ignored if decoder does not handle encrypted content.
   * @return The decoder.
   * @throws DecoderException If an error occurred creating a suitable decoder.
   */
  protected abstract Decoder<
          DecoderInputBuffer, ? extends VideoDecoderOutputBuffer, ? extends DecoderException>
      createDecoder(Format format, @Nullable CryptoConfig cryptoConfig) throws DecoderException;

  /**
   * Renders the specified output buffer.
   *
   * <p>The implementation of this method takes ownership of the output buffer and is responsible
   * for calling {@link VideoDecoderOutputBuffer#release()} either immediately or in the future.
   *
   * @param outputBuffer {@link VideoDecoderOutputBuffer} to render.
   * @param presentationTimeUs Presentation time in microseconds.
   * @param outputFormat Output {@link Format}.
   * @throws DecoderException If an error occurs when rendering the output buffer.
   */
  protected void renderOutputBuffer(
      VideoDecoderOutputBuffer outputBuffer, long presentationTimeUs, Format outputFormat)
      throws DecoderException {
    if (frameMetadataListener != null) {
      frameMetadataListener.onVideoFrameAboutToBeRendered(
          presentationTimeUs, System.nanoTime(), outputFormat, /* mediaFormat= */ null);
    }
    lastRenderTimeUs = Util.msToUs(SystemClock.elapsedRealtime() * 1000);
    int bufferMode = outputBuffer.mode;
    boolean renderSurface = bufferMode == C.VIDEO_OUTPUT_MODE_SURFACE_YUV && outputSurface != null;
    boolean renderYuv = bufferMode == C.VIDEO_OUTPUT_MODE_YUV && outputBufferRenderer != null;
    if (!renderYuv && !renderSurface) {
      dropOutputBuffer(outputBuffer);
    } else {
      maybeNotifyVideoSizeChanged(outputBuffer.width, outputBuffer.height);
      if (renderYuv) {
        outputBufferRenderer.setOutputBuffer(outputBuffer);
      } else {
        renderOutputBufferToSurface(outputBuffer, outputSurface);
      }
      consecutiveDroppedFrameCount = 0;
      decoderCounters.renderedOutputBufferCount++;
      maybeNotifyRenderedFirstFrame();
    }
  }

  /**
   * Renders the specified output buffer to the passed surface.
   *
   * <p>The implementation of this method takes ownership of the output buffer and is responsible
   * for calling {@link VideoDecoderOutputBuffer#release()} either immediately or in the future.
   *
   * @param outputBuffer {@link VideoDecoderOutputBuffer} to render.
   * @param surface Output {@link Surface}.
   * @throws DecoderException If an error occurs when rendering the output buffer.
   */
  protected abstract void renderOutputBufferToSurface(
      VideoDecoderOutputBuffer outputBuffer, Surface surface) throws DecoderException;

  /** Sets the video output. */
  protected final void setOutput(@Nullable Object output) {
    if (output instanceof Surface) {
      outputSurface = (Surface) output;
      outputBufferRenderer = null;
      outputMode = C.VIDEO_OUTPUT_MODE_SURFACE_YUV;
    } else if (output instanceof VideoDecoderOutputBufferRenderer) {
      outputSurface = null;
      outputBufferRenderer = (VideoDecoderOutputBufferRenderer) output;
      outputMode = C.VIDEO_OUTPUT_MODE_YUV;
    } else {
      // Handle unsupported outputs by clearing the output.
      output = null;
      outputSurface = null;
      outputBufferRenderer = null;
      outputMode = C.VIDEO_OUTPUT_MODE_NONE;
    }
    if (this.output != output) {
      this.output = output;
      if (output != null) {
        if (decoder != null) {
          setDecoderOutputMode(outputMode);
        }
        onOutputChanged();
      } else {
        // The output has been removed. We leave the outputMode of the underlying decoder unchanged
        // in anticipation that a subsequent output will likely be of the same type.
        onOutputRemoved();
      }
    } else if (output != null) {
      // The output is unchanged and non-null.
      onOutputReset();
    }
  }

  /**
   * Sets output mode of the decoder.
   *
   * @param outputMode Output mode.
   */
  protected abstract void setDecoderOutputMode(@VideoOutputMode int outputMode);

  /**
   * Evaluates whether the existing decoder can be reused for a new {@link Format}.
   *
   * <p>The default implementation does not allow decoder reuse.
   *
   * @param oldFormat The previous format.
   * @param newFormat The new format.
   * @return The result of the evaluation.
   */
  protected DecoderReuseEvaluation canReuseDecoder(
      String decoderName, Format oldFormat, Format newFormat) {
    return new DecoderReuseEvaluation(
        decoderName, oldFormat, newFormat, REUSE_RESULT_NO, DISCARD_REASON_REUSE_NOT_IMPLEMENTED);
  }

  // Internal methods.

  private void setSourceDrmSession(@Nullable DrmSession session) {
    DrmSession.replaceSession(sourceDrmSession, session);
    sourceDrmSession = session;
  }

  private void setDecoderDrmSession(@Nullable DrmSession session) {
    DrmSession.replaceSession(decoderDrmSession, session);
    decoderDrmSession = session;
  }

  private void maybeInitDecoder() throws ExoPlaybackException {
    if (decoder != null) {
      return;
    }

    setDecoderDrmSession(sourceDrmSession);

    CryptoConfig cryptoConfig = null;
    if (decoderDrmSession != null) {
      cryptoConfig = decoderDrmSession.getCryptoConfig();
      if (cryptoConfig == null) {
        DrmSessionException drmError = decoderDrmSession.getError();
        if (drmError != null) {
          // Continue for now. We may be able to avoid failure if a new input format causes the
          // session to be replaced without it having been used.
        } else {
          // The drm session isn't open yet.
          return;
        }
      }
    }

    try {
      long decoderInitializingTimestamp = SystemClock.elapsedRealtime();
      decoder = createDecoder(inputFormat, cryptoConfig);
      setDecoderOutputMode(outputMode);
      long decoderInitializedTimestamp = SystemClock.elapsedRealtime();
      eventDispatcher.decoderInitialized(
          decoder.getName(),
          decoderInitializedTimestamp,
          decoderInitializedTimestamp - decoderInitializingTimestamp);
      decoderCounters.decoderInitCount++;
    } catch (DecoderException e) {
      Log.e(TAG, "Video codec error", e);
      eventDispatcher.videoCodecError(e);
      throw createRendererException(
          e, inputFormat, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED);
    } catch (OutOfMemoryError e) {
      throw createRendererException(
          e, inputFormat, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED);
    }
  }

  private boolean feedInputBuffer() throws DecoderException, ExoPlaybackException {
    if (decoder == null
        || decoderReinitializationState == REINITIALIZATION_STATE_WAIT_END_OF_STREAM
        || inputStreamEnded) {
      // We need to reinitialize the decoder or the input stream has ended.
      return false;
    }

    if (inputBuffer == null) {
      inputBuffer = decoder.dequeueInputBuffer();
      if (inputBuffer == null) {
        return false;
      }
    }

    if (decoderReinitializationState == REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM) {
      inputBuffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
      decoder.queueInputBuffer(inputBuffer);
      inputBuffer = null;
      decoderReinitializationState = REINITIALIZATION_STATE_WAIT_END_OF_STREAM;
      return false;
    }

    FormatHolder formatHolder = getFormatHolder();
    switch (readSource(formatHolder, inputBuffer, /* readFlags= */ 0)) {
      case C.RESULT_NOTHING_READ:
        return false;
      case C.RESULT_FORMAT_READ:
        onInputFormatChanged(formatHolder);
        return true;
      case C.RESULT_BUFFER_READ:
        if (inputBuffer.isEndOfStream()) {
          inputStreamEnded = true;
          decoder.queueInputBuffer(inputBuffer);
          inputBuffer = null;
          return false;
        }
        if (waitingForFirstSampleInFormat) {
          formatQueue.add(inputBuffer.timeUs, inputFormat);
          waitingForFirstSampleInFormat = false;
        }
        inputBuffer.flip();
        inputBuffer.format = inputFormat;
        onQueueInputBuffer(inputBuffer);
        decoder.queueInputBuffer(inputBuffer);
        buffersInCodecCount++;
        decoderReceivedBuffers = true;
        decoderCounters.queuedInputBufferCount++;
        inputBuffer = null;
        return true;
      default:
        throw new IllegalStateException();
    }
  }

  /**
   * Attempts to dequeue an output buffer from the decoder and, if successful, passes it to {@link
   * #processOutputBuffer(long, long)}.
   *
   * @param positionUs The player's current position.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     measured at the start of the current iteration of the rendering loop.
   * @return Whether it may be possible to drain more output data.
   * @throws ExoPlaybackException If an error occurs draining the output buffer.
   */
  private boolean drainOutputBuffer(long positionUs, long elapsedRealtimeUs)
      throws ExoPlaybackException, DecoderException {
    if (outputBuffer == null) {
      outputBuffer = decoder.dequeueOutputBuffer();
      if (outputBuffer == null) {
        return false;
      }
      decoderCounters.skippedOutputBufferCount += outputBuffer.skippedOutputBufferCount;
      buffersInCodecCount -= outputBuffer.skippedOutputBufferCount;
    }

    if (outputBuffer.isEndOfStream()) {
      if (decoderReinitializationState == REINITIALIZATION_STATE_WAIT_END_OF_STREAM) {
        // We're waiting to re-initialize the decoder, and have now processed all final buffers.
        releaseDecoder();
        maybeInitDecoder();
      } else {
        outputBuffer.release();
        outputBuffer = null;
        outputStreamEnded = true;
      }
      return false;
    }

    boolean processedOutputBuffer = processOutputBuffer(positionUs, elapsedRealtimeUs);
    if (processedOutputBuffer) {
      onProcessedOutputBuffer(outputBuffer.timeUs);
      outputBuffer = null;
    }
    return processedOutputBuffer;
  }

  /**
   * Processes {@link #outputBuffer} by rendering it, skipping it or doing nothing, and returns
   * whether it may be possible to process another output buffer.
   *
   * @param positionUs The player's current position.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     measured at the start of the current iteration of the rendering loop.
   * @return Whether it may be possible to drain another output buffer.
   * @throws ExoPlaybackException If an error occurs processing the output buffer.
   */
  private boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs)
      throws ExoPlaybackException, DecoderException {
    if (initialPositionUs == C.TIME_UNSET) {
      initialPositionUs = positionUs;
    }

    long earlyUs = outputBuffer.timeUs - positionUs;
    if (!hasOutput()) {
      // Skip frames in sync with playback, so we'll be at the right frame if the mode changes.
      if (isBufferLate(earlyUs)) {
        skipOutputBuffer(outputBuffer);
        return true;
      }
      return false;
    }

    long presentationTimeUs = outputBuffer.timeUs - outputStreamOffsetUs;
    Format format = formatQueue.pollFloor(presentationTimeUs);
    if (format != null) {
      outputFormat = format;
    }

    long elapsedRealtimeNowUs = SystemClock.elapsedRealtime() * 1000;
    long elapsedSinceLastRenderUs = elapsedRealtimeNowUs - lastRenderTimeUs;
    boolean isStarted = getState() == STATE_STARTED;
    boolean shouldRenderFirstFrame =
        !renderedFirstFrameAfterEnable
            ? (isStarted || mayRenderFirstFrameAfterEnableIfNotStarted)
            : !renderedFirstFrameAfterReset;
    // TODO: We shouldn't force render while we are joining an ongoing playback.
    if (shouldRenderFirstFrame
        || (isStarted && shouldForceRenderOutputBuffer(earlyUs, elapsedSinceLastRenderUs))) {
      renderOutputBuffer(outputBuffer, presentationTimeUs, outputFormat);
      return true;
    }

    if (!isStarted || positionUs == initialPositionUs) {
      return false;
    }

    // TODO: Treat dropped buffers as skipped while we are joining an ongoing playback.
    if (shouldDropBuffersToKeyframe(earlyUs, elapsedRealtimeUs)
        && maybeDropBuffersToKeyframe(positionUs)) {
      return false;
    } else if (shouldDropOutputBuffer(earlyUs, elapsedRealtimeUs)) {
      dropOutputBuffer(outputBuffer);
      return true;
    }

    if (earlyUs < 30000) {
      renderOutputBuffer(outputBuffer, presentationTimeUs, outputFormat);
      return true;
    }

    return false;
  }

  private boolean hasOutput() {
    return outputMode != C.VIDEO_OUTPUT_MODE_NONE;
  }

  private void onOutputChanged() {
    // If we know the video size, report it again immediately.
    maybeRenotifyVideoSizeChanged();
    // We haven't rendered to the new output yet.
    clearRenderedFirstFrame();
    if (getState() == STATE_STARTED) {
      setJoiningDeadlineMs();
    }
  }

  private void onOutputRemoved() {
    clearReportedVideoSize();
    clearRenderedFirstFrame();
  }

  private void onOutputReset() {
    // The output is unchanged and non-null. If we know the video size and/or have already
    // rendered to the output, report these again immediately.
    maybeRenotifyVideoSizeChanged();
    maybeRenotifyRenderedFirstFrame();
  }

  private void setJoiningDeadlineMs() {
    joiningDeadlineMs =
        allowedJoiningTimeMs > 0
            ? (SystemClock.elapsedRealtime() + allowedJoiningTimeMs)
            : C.TIME_UNSET;
  }

  private void clearRenderedFirstFrame() {
    renderedFirstFrameAfterReset = false;
  }

  private void maybeNotifyRenderedFirstFrame() {
    renderedFirstFrameAfterEnable = true;
    if (!renderedFirstFrameAfterReset) {
      renderedFirstFrameAfterReset = true;
      eventDispatcher.renderedFirstFrame(output);
    }
  }

  private void maybeRenotifyRenderedFirstFrame() {
    if (renderedFirstFrameAfterReset) {
      eventDispatcher.renderedFirstFrame(output);
    }
  }

  private void clearReportedVideoSize() {
    reportedVideoSize = null;
  }

  private void maybeNotifyVideoSizeChanged(int width, int height) {
    if (reportedVideoSize == null
        || reportedVideoSize.width != width
        || reportedVideoSize.height != height) {
      reportedVideoSize = new VideoSize(width, height);
      eventDispatcher.videoSizeChanged(reportedVideoSize);
    }
  }

  private void maybeRenotifyVideoSizeChanged() {
    if (reportedVideoSize != null) {
      eventDispatcher.videoSizeChanged(reportedVideoSize);
    }
  }

  private void maybeNotifyDroppedFrames() {
    if (droppedFrames > 0) {
      long now = SystemClock.elapsedRealtime();
      long elapsedMs = now - droppedFrameAccumulationStartTimeMs;
      eventDispatcher.droppedFrames(droppedFrames, elapsedMs);
      droppedFrames = 0;
      droppedFrameAccumulationStartTimeMs = now;
    }
  }

  private static boolean isBufferLate(long earlyUs) {
    // Class a buffer as late if it should have been presented more than 30 ms ago.
    return earlyUs < -30000;
  }

  private static boolean isBufferVeryLate(long earlyUs) {
    // Class a buffer as very late if it should have been presented more than 500 ms ago.
    return earlyUs < -500000;
  }
}

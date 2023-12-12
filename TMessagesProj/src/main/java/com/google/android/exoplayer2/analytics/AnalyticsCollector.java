/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.analytics;

import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodec.CodecException;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.decoder.DecoderException;
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.video.VideoDecoderOutputBufferRenderer;
import java.util.List;

/**
 * Interface for data collectors that forward analytics events to {@link AnalyticsListener
 * AnalyticsListeners}.
 */
public interface AnalyticsCollector
    extends Player.Listener,
        MediaSourceEventListener,
        BandwidthMeter.EventListener,
        DrmSessionEventListener {

  /**
   * Adds a listener for analytics events.
   *
   * @param listener The listener to add.
   */
  void addListener(AnalyticsListener listener);

  /**
   * Removes a previously added analytics event listener.
   *
   * @param listener The listener to remove.
   */
  void removeListener(AnalyticsListener listener);

  /**
   * Sets the player for which data will be collected. Must only be called if no player has been set
   * yet or the current player is idle.
   *
   * @param player The {@link Player} for which data will be collected.
   * @param looper The {@link Looper} used for listener callbacks.
   */
  void setPlayer(Player player, Looper looper);

  /**
   * Releases the collector. Must be called after the player for which data is collected has been
   * released.
   */
  void release();

  /**
   * Updates the playback queue information used for event association.
   *
   * <p>Should only be called by the player controlling the queue and not from app code.
   *
   * @param queue The playback queue of media periods identified by their {@link MediaPeriodId}.
   * @param readingPeriod The media period in the queue that is currently being read by renderers,
   *     or null if the queue is empty.
   */
  void updateMediaPeriodQueueInfo(List<MediaPeriodId> queue, @Nullable MediaPeriodId readingPeriod);

  /**
   * Notify analytics collector that a seek operation will start. Should be called before the player
   * adjusts its state and position to the seek.
   */
  void notifySeekStarted();

  // Audio events.

  /**
   * Called when the audio renderer is enabled.
   *
   * @param counters {@link DecoderCounters} that will be updated by the audio renderer for as long
   *     as it remains enabled.
   */
  void onAudioEnabled(DecoderCounters counters);

  /**
   * Called when a audio decoder is created.
   *
   * @param decoderName The audio decoder that was created.
   * @param initializedTimestampMs {@link SystemClock#elapsedRealtime()} when initialization
   *     finished.
   * @param initializationDurationMs The time taken to initialize the decoder in milliseconds.
   */
  void onAudioDecoderInitialized(
      String decoderName, long initializedTimestampMs, long initializationDurationMs);

  /**
   * Called when the format of the media being consumed by the audio renderer changes.
   *
   * @param format The new format.
   * @param decoderReuseEvaluation The result of the evaluation to determine whether an existing
   *     decoder instance can be reused for the new format, or {@code null} if the renderer did not
   *     have a decoder.
   */
  void onAudioInputFormatChanged(
      Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation);

  /**
   * Called when the audio position has increased for the first time since the last pause or
   * position reset.
   *
   * @param playoutStartSystemTimeMs The approximate derived {@link System#currentTimeMillis()} at
   *     which playout started.
   */
  void onAudioPositionAdvancing(long playoutStartSystemTimeMs);

  /**
   * Called when an audio underrun occurs.
   *
   * @param bufferSize The size of the audio output buffer, in bytes.
   * @param bufferSizeMs The size of the audio output buffer, in milliseconds, if it contains PCM
   *     encoded audio. {@link C#TIME_UNSET} if the output buffer contains non-PCM encoded audio.
   * @param elapsedSinceLastFeedMs The time since audio was last written to the output buffer.
   */
  void onAudioUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs);

  /**
   * Called when a audio decoder is released.
   *
   * @param decoderName The audio decoder that was released.
   */
  void onAudioDecoderReleased(String decoderName);

  /**
   * Called when the audio renderer is disabled.
   *
   * @param counters {@link DecoderCounters} that were updated by the audio renderer.
   */
  void onAudioDisabled(DecoderCounters counters);

  /**
   * Called when {@link AudioSink} has encountered an error.
   *
   * <p>If the sink writes to a platform {@link AudioTrack}, this will be called for all {@link
   * AudioTrack} errors.
   *
   * @param audioSinkError The error that occurred. Typically an {@link
   *     AudioSink.InitializationException}, a {@link AudioSink.WriteException}, or an {@link
   *     AudioSink.UnexpectedDiscontinuityException}.
   */
  void onAudioSinkError(Exception audioSinkError);

  /**
   * Called when an audio decoder encounters an error.
   *
   * @param audioCodecError The error. Typically a {@link CodecException} if the renderer uses
   *     {@link MediaCodec}, or a {@link DecoderException} if the renderer uses a software decoder.
   */
  void onAudioCodecError(Exception audioCodecError);

  // Video events.

  /**
   * Called when the video renderer is enabled.
   *
   * @param counters {@link DecoderCounters} that will be updated by the video renderer for as long
   *     as it remains enabled.
   */
  void onVideoEnabled(DecoderCounters counters);

  /**
   * Called when a video decoder is created.
   *
   * @param decoderName The decoder that was created.
   * @param initializedTimestampMs {@link SystemClock#elapsedRealtime()} when initialization
   *     finished.
   * @param initializationDurationMs The time taken to initialize the decoder in milliseconds.
   */
  void onVideoDecoderInitialized(
      String decoderName, long initializedTimestampMs, long initializationDurationMs);

  /**
   * Called when the format of the media being consumed by the video renderer changes.
   *
   * @param format The new format.
   * @param decoderReuseEvaluation The result of the evaluation to determine whether an existing
   *     decoder instance can be reused for the new format, or {@code null} if the renderer did not
   *     have a decoder.
   */
  void onVideoInputFormatChanged(
      Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation);

  /**
   * Called to report the number of frames dropped by the video renderer. Dropped frames are
   * reported whenever the renderer is stopped having dropped frames, and optionally, whenever the
   * count reaches a specified threshold whilst the renderer is started.
   *
   * @param count The number of dropped frames.
   * @param elapsedMs The duration in milliseconds over which the frames were dropped. This duration
   *     is timed from when the renderer was started or from when dropped frames were last reported
   *     (whichever was more recent), and not from when the first of the reported drops occurred.
   */
  void onDroppedFrames(int count, long elapsedMs);

  /**
   * Called when a video decoder is released.
   *
   * @param decoderName The video decoder that was released.
   */
  void onVideoDecoderReleased(String decoderName);

  /**
   * Called when the video renderer is disabled.
   *
   * @param counters {@link DecoderCounters} that were updated by the video renderer.
   */
  void onVideoDisabled(DecoderCounters counters);

  /**
   * Called when a frame is rendered for the first time since setting the output, or since the
   * renderer was reset, or since the stream being rendered was changed.
   *
   * @param output The output of the video renderer. Normally a {@link Surface}, however some video
   *     renderers may have other output types (e.g., a {@link VideoDecoderOutputBufferRenderer}).
   * @param renderTimeMs The {@link SystemClock#elapsedRealtime()} when the frame was rendered.
   */
  void onRenderedFirstFrame(Object output, long renderTimeMs);

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
  void onVideoFrameProcessingOffset(long totalProcessingOffsetUs, int frameCount);

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
  void onVideoCodecError(Exception videoCodecError);
}

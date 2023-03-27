/*
 * Copyright 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.audio;

import com.google.android.exoplayer2.PlaybackParameters;

/**
 * Provides a chain of audio processors, which are used for any user-defined processing and applying
 * playback parameters (if supported). Because applying playback parameters can skip and
 * stretch/compress audio, the sink will query the chain for information on how to transform its
 * output position to map it onto a media position, via {@link #getMediaDuration(long)} and {@link
 * #getSkippedOutputFrameCount()}.
 */
public interface AudioProcessorChain {

  /**
   * Returns the fixed chain of audio processors that will process audio. This method is called once
   * during initialization, but audio processors may change state to become active/inactive during
   * playback.
   */
  AudioProcessor[] getAudioProcessors();

  /**
   * Configures audio processors to apply the specified playback parameters immediately, returning
   * the new playback parameters, which may differ from those passed in. Only called when processors
   * have no input pending.
   *
   * @param playbackParameters The playback parameters to try to apply.
   * @return The playback parameters that were actually applied.
   */
  PlaybackParameters applyPlaybackParameters(PlaybackParameters playbackParameters);

  /**
   * Configures audio processors to apply whether to skip silences immediately, returning the new
   * value. Only called when processors have no input pending.
   *
   * @param skipSilenceEnabled Whether silences should be skipped in the audio stream.
   * @return The new value.
   */
  boolean applySkipSilenceEnabled(boolean skipSilenceEnabled);

  /**
   * Returns the media duration corresponding to the specified playout duration, taking speed
   * adjustment due to audio processing into account.
   *
   * <p>The scaling performed by this method will use the actual playback speed achieved by the
   * audio processor chain, on average, since it was last flushed. This may differ very slightly
   * from the target playback speed.
   *
   * @param playoutDuration The playout duration to scale.
   * @return The corresponding media duration, in the same units as {@code duration}.
   */
  long getMediaDuration(long playoutDuration);

  /**
   * Returns the number of output audio frames skipped since the audio processors were last flushed.
   */
  long getSkippedOutputFrameCount();
}

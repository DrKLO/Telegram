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
package org.telegram.messenger.exoplayer2.audio;

import android.os.Handler;
import android.os.SystemClock;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.Renderer;
import org.telegram.messenger.exoplayer2.decoder.DecoderCounters;
import org.telegram.messenger.exoplayer2.util.Assertions;

/**
 * Listener of audio {@link Renderer} events.
 */
public interface AudioRendererEventListener {

  /**
   * Called when the renderer is enabled.
   *
   * @param counters {@link DecoderCounters} that will be updated by the renderer for as long as it
   *     remains enabled.
   */
  void onAudioEnabled(DecoderCounters counters);

  /**
   * Called when the audio session is set.
   *
   * @param audioSessionId The audio session id.
   */
  void onAudioSessionId(int audioSessionId);

  /**
   * Called when a decoder is created.
   *
   * @param decoderName The decoder that was created.
   * @param initializedTimestampMs {@link SystemClock#elapsedRealtime()} when initialization
   *     finished.
   * @param initializationDurationMs The time taken to initialize the decoder in milliseconds.
   */
  void onAudioDecoderInitialized(String decoderName, long initializedTimestampMs,
      long initializationDurationMs);

  /**
   * Called when the format of the media being consumed by the renderer changes.
   *
   * @param format The new format.
   */
  void onAudioInputFormatChanged(Format format);

  /**
   * Called when an {@link AudioTrack} underrun occurs.
   *
   * @param bufferSize The size of the {@link AudioTrack}'s buffer, in bytes.
   * @param bufferSizeMs The size of the {@link AudioTrack}'s buffer, in milliseconds, if it is
   *     configured for PCM output. {@link C#TIME_UNSET} if it is configured for passthrough output,
   *     as the buffered media can have a variable bitrate so the duration may be unknown.
   * @param elapsedSinceLastFeedMs The time since the {@link AudioTrack} was last fed data.
   */
  void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs);

  /**
   * Called when the renderer is disabled.
   *
   * @param counters {@link DecoderCounters} that were updated by the renderer.
   */
  void onAudioDisabled(DecoderCounters counters);

  /**
   * Dispatches events to a {@link AudioRendererEventListener}.
   */
  final class EventDispatcher {

    private final Handler handler;
    private final AudioRendererEventListener listener;

    /**
     * @param handler A handler for dispatching events, or null if creating a dummy instance.
     * @param listener The listener to which events should be dispatched, or null if creating a
     *     dummy instance.
     */
    public EventDispatcher(Handler handler, AudioRendererEventListener listener) {
      this.handler = listener != null ? Assertions.checkNotNull(handler) : null;
      this.listener = listener;
    }

    /**
     * Invokes {@link AudioRendererEventListener#onAudioEnabled(DecoderCounters)}.
     */
    public void enabled(final DecoderCounters decoderCounters) {
      if (listener != null) {
        handler.post(new Runnable() {
          @Override
          public void run() {
            listener.onAudioEnabled(decoderCounters);
          }
        });
      }
    }

    /**
     * Invokes {@link AudioRendererEventListener#onAudioDecoderInitialized(String, long, long)}.
     */
    public void decoderInitialized(final String decoderName,
        final long initializedTimestampMs, final long initializationDurationMs) {
      if (listener != null) {
        handler.post(new Runnable() {
          @Override
          public void run() {
            listener.onAudioDecoderInitialized(decoderName, initializedTimestampMs,
                initializationDurationMs);
          }
        });
      }
    }

    /**
     * Invokes {@link AudioRendererEventListener#onAudioInputFormatChanged(Format)}.
     */
    public void inputFormatChanged(final Format format) {
      if (listener != null) {
        handler.post(new Runnable() {
          @Override
          public void run() {
            listener.onAudioInputFormatChanged(format);
          }
        });
      }
    }

    /**
     * Invokes {@link AudioRendererEventListener#onAudioTrackUnderrun(int, long, long)}.
     */
    public void audioTrackUnderrun(final int bufferSize, final long bufferSizeMs,
        final long elapsedSinceLastFeedMs) {
      if (listener != null) {
        handler.post(new Runnable()  {
          @Override
          public void run() {
            listener.onAudioTrackUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
          }
        });
      }
    }

    /**
     * Invokes {@link AudioRendererEventListener#onAudioDisabled(DecoderCounters)}.
     */
    public void disabled(final DecoderCounters counters) {
      if (listener != null) {
        handler.post(new Runnable() {
          @Override
          public void run() {
            counters.ensureUpdated();
            listener.onAudioDisabled(counters);
          }
        });
      }
    }

    /**
     * Invokes {@link AudioRendererEventListener#onAudioSessionId(int)}.
     */
    public void audioSessionId(final int audioSessionId) {
      if (listener != null) {
        handler.post(new Runnable() {
          @Override
          public void run() {
            listener.onAudioSessionId(audioSessionId);
          }
        });
      }
    }

  }

}

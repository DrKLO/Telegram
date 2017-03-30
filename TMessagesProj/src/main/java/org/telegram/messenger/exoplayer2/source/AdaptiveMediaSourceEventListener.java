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
package org.telegram.messenger.exoplayer2.source;

import android.os.Handler;
import android.os.SystemClock;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.upstream.DataSpec;
import org.telegram.messenger.exoplayer2.util.Assertions;
import java.io.IOException;

/**
 * Interface for callbacks to be notified of adaptive {@link MediaSource} events.
 */
public interface AdaptiveMediaSourceEventListener {

  /**
   * Called when a load begins.
   *
   * @param dataSpec Defines the data being loaded.
   * @param dataType One of the {@link C} {@code DATA_TYPE_*} constants defining the type of data
   *     being loaded.
   * @param trackType One of the {@link C} {@code TRACK_TYPE_*} constants if the data corresponds
   *     to media of a specific type. {@link C#TRACK_TYPE_UNKNOWN} otherwise.
   * @param trackFormat The format of the track to which the data belongs. Null if the data does
   *     not belong to a track.
   * @param trackSelectionReason One of the {@link C} {@code SELECTION_REASON_*} constants if the
   *     data belongs to a track. {@link C#SELECTION_REASON_UNKNOWN} otherwise.
   * @param trackSelectionData Optional data associated with the selection of the track to which the
   *     data belongs. Null if the data does not belong to a track.
   * @param mediaStartTimeMs The start time of the media being loaded, or {@link C#TIME_UNSET} if
   *     the load is not for media data.
   * @param mediaEndTimeMs The end time of the media being loaded, or {@link C#TIME_UNSET} if the
   *     load is not for media data.
   * @param elapsedRealtimeMs The value of {@link SystemClock#elapsedRealtime} when the load began.
   */
  void onLoadStarted(DataSpec dataSpec, int dataType, int trackType, Format trackFormat,
      int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs,
      long mediaEndTimeMs, long elapsedRealtimeMs);

  /**
   * Called when a load ends.
   *
   * @param dataSpec Defines the data being loaded.
   * @param dataType One of the {@link C} {@code DATA_TYPE_*} constants defining the type of data
   *     being loaded.
   * @param trackType One of the {@link C} {@code TRACK_TYPE_*} constants if the data corresponds
   *     to media of a specific type. {@link C#TRACK_TYPE_UNKNOWN} otherwise.
   * @param trackFormat The format of the track to which the data belongs. Null if the data does
   *     not belong to a track.
   * @param trackSelectionReason One of the {@link C} {@code SELECTION_REASON_*} constants if the
   *     data belongs to a track. {@link C#SELECTION_REASON_UNKNOWN} otherwise.
   * @param trackSelectionData Optional data associated with the selection of the track to which the
   *     data belongs. Null if the data does not belong to a track.
   * @param mediaStartTimeMs The start time of the media being loaded, or {@link C#TIME_UNSET} if
   *     the load is not for media data.
   * @param mediaEndTimeMs The end time of the media being loaded, or {@link C#TIME_UNSET} if the
   *     load is not for media data.
   * @param elapsedRealtimeMs The value of {@link SystemClock#elapsedRealtime} when the load ended.
   * @param loadDurationMs The duration of the load.
   * @param bytesLoaded The number of bytes that were loaded.
   */
  void onLoadCompleted(DataSpec dataSpec, int dataType, int trackType, Format trackFormat,
      int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs,
      long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded);

  /**
   * Called when a load is canceled.
   *
   * @param dataSpec Defines the data being loaded.
   * @param dataType One of the {@link C} {@code DATA_TYPE_*} constants defining the type of data
   *     being loaded.
   * @param trackType One of the {@link C} {@code TRACK_TYPE_*} constants if the data corresponds
   *     to media of a specific type. {@link C#TRACK_TYPE_UNKNOWN} otherwise.
   * @param trackFormat The format of the track to which the data belongs. Null if the data does
   *     not belong to a track.
   * @param trackSelectionReason One of the {@link C} {@code SELECTION_REASON_*} constants if the
   *     data belongs to a track. {@link C#SELECTION_REASON_UNKNOWN} otherwise.
   * @param trackSelectionData Optional data associated with the selection of the track to which the
   *     data belongs. Null if the data does not belong to a track.
   * @param mediaStartTimeMs The start time of the media being loaded, or {@link C#TIME_UNSET} if
   *     the load is not for media data.
   * @param mediaEndTimeMs The end time of the media being loaded, or {@link C#TIME_UNSET} if the
   *     load is not for media data.
   * @param elapsedRealtimeMs The value of {@link SystemClock#elapsedRealtime} when the load was
   *     canceled.
   * @param loadDurationMs The duration of the load up to the point at which it was canceled.
   * @param bytesLoaded The number of bytes that were loaded prior to cancelation.
   */
  void onLoadCanceled(DataSpec dataSpec, int dataType, int trackType, Format trackFormat,
      int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs,
      long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded);

  /**
   * Called when a load error occurs.
   * <p>
   * The error may or may not have resulted in the load being canceled, as indicated by the
   * {@code wasCanceled} parameter. If the load was canceled, {@link #onLoadCanceled} will
   * <em>not</em> be called in addition to this method.
   *
   * @param dataSpec Defines the data being loaded.
   * @param dataType One of the {@link C} {@code DATA_TYPE_*} constants defining the type of data
   *     being loaded.
   * @param trackType One of the {@link C} {@code TRACK_TYPE_*} constants if the data corresponds
   *     to media of a specific type. {@link C#TRACK_TYPE_UNKNOWN} otherwise.
   * @param trackFormat The format of the track to which the data belongs. Null if the data does
   *     not belong to a track.
   * @param trackSelectionReason One of the {@link C} {@code SELECTION_REASON_*} constants if the
   *     data belongs to a track. {@link C#SELECTION_REASON_UNKNOWN} otherwise.
   * @param trackSelectionData Optional data associated with the selection of the track to which the
   *     data belongs. Null if the data does not belong to a track.
   * @param mediaStartTimeMs The start time of the media being loaded, or {@link C#TIME_UNSET} if
   *     the load is not for media data.
   * @param mediaEndTimeMs The end time of the media being loaded, or {@link C#TIME_UNSET} if the
   *     load is not for media data.
   * @param elapsedRealtimeMs The value of {@link SystemClock#elapsedRealtime} when the error
   *     occurred.
   * @param loadDurationMs The duration of the load up to the point at which the error occurred.
   * @param bytesLoaded The number of bytes that were loaded prior to the error.
   * @param error The load error.
   * @param wasCanceled Whether the load was canceled as a result of the error.
   */
  void onLoadError(DataSpec dataSpec, int dataType, int trackType, Format trackFormat,
      int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs,
      long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded,
      IOException error, boolean wasCanceled);

  /**
   * Called when data is removed from the back of a media buffer, typically so that it can be
   * re-buffered in a different format.
   *
   * @param trackType The type of the media. One of the {@link C} {@code TRACK_TYPE_*} constants.
   * @param mediaStartTimeMs The start time of the media being discarded.
   * @param mediaEndTimeMs The end time of the media being discarded.
   */
  void onUpstreamDiscarded(int trackType, long mediaStartTimeMs, long mediaEndTimeMs);

  /**
   * Called when a downstream format change occurs (i.e. when the format of the media being read
   * from one or more {@link SampleStream}s provided by the source changes).
   *
   * @param trackType The type of the media. One of the {@link C} {@code TRACK_TYPE_*} constants.
   * @param trackFormat The format of the track to which the data belongs. Null if the data does
   *     not belong to a track.
   * @param trackSelectionReason One of the {@link C} {@code SELECTION_REASON_*} constants if the
   *     data belongs to a track. {@link C#SELECTION_REASON_UNKNOWN} otherwise.
   * @param trackSelectionData Optional data associated with the selection of the track to which the
   *     data belongs. Null if the data does not belong to a track.
   * @param mediaTimeMs The media time at which the change occurred.
   */
  void onDownstreamFormatChanged(int trackType, Format trackFormat, int trackSelectionReason,
      Object trackSelectionData, long mediaTimeMs);

  /**
   * Dispatches events to a {@link AdaptiveMediaSourceEventListener}.
   */
  final class EventDispatcher {

    private final Handler handler;
    private final AdaptiveMediaSourceEventListener listener;
    private final long mediaTimeOffsetMs;

    public EventDispatcher(Handler handler, AdaptiveMediaSourceEventListener listener) {
      this(handler, listener, 0);
    }

    public EventDispatcher(Handler handler, AdaptiveMediaSourceEventListener listener,
        long mediaTimeOffsetMs) {
      this.handler = listener != null ? Assertions.checkNotNull(handler) : null;
      this.listener = listener;
      this.mediaTimeOffsetMs = mediaTimeOffsetMs;
    }

    public EventDispatcher copyWithMediaTimeOffsetMs(long mediaTimeOffsetMs) {
      return new EventDispatcher(handler, listener, mediaTimeOffsetMs);
    }

    public void loadStarted(DataSpec dataSpec, int dataType, long elapsedRealtimeMs) {
      loadStarted(dataSpec, dataType, C.TRACK_TYPE_UNKNOWN, null, C.SELECTION_REASON_UNKNOWN,
          null, C.TIME_UNSET, C.TIME_UNSET, elapsedRealtimeMs);
    }

    public void loadStarted(final DataSpec dataSpec, final int dataType, final int trackType,
        final Format trackFormat, final int trackSelectionReason, final Object trackSelectionData,
        final long mediaStartTimeUs, final long mediaEndTimeUs, final long elapsedRealtimeMs) {
      if (listener != null) {
        handler.post(new Runnable()  {
          @Override
          public void run() {
            listener.onLoadStarted(dataSpec, dataType, trackType, trackFormat, trackSelectionReason,
                trackSelectionData, adjustMediaTime(mediaStartTimeUs),
                adjustMediaTime(mediaEndTimeUs), elapsedRealtimeMs);
          }
        });
      }
    }

    public void loadCompleted(DataSpec dataSpec, int dataType, long elapsedRealtimeMs,
        long loadDurationMs, long bytesLoaded) {
      loadCompleted(dataSpec, dataType, C.TRACK_TYPE_UNKNOWN, null, C.SELECTION_REASON_UNKNOWN,
          null, C.TIME_UNSET, C.TIME_UNSET, elapsedRealtimeMs, loadDurationMs, bytesLoaded);
    }

    public void loadCompleted(final DataSpec dataSpec, final int dataType, final int trackType,
        final Format trackFormat, final int trackSelectionReason, final Object trackSelectionData,
        final long mediaStartTimeUs, final long mediaEndTimeUs, final long elapsedRealtimeMs,
        final long loadDurationMs, final long bytesLoaded) {
      if (listener != null) {
        handler.post(new Runnable()  {
          @Override
          public void run() {
            listener.onLoadCompleted(dataSpec, dataType, trackType, trackFormat,
                trackSelectionReason, trackSelectionData, adjustMediaTime(mediaStartTimeUs),
                adjustMediaTime(mediaEndTimeUs), elapsedRealtimeMs, loadDurationMs, bytesLoaded);
          }
        });
      }
    }

    public void loadCanceled(DataSpec dataSpec, int dataType, long elapsedRealtimeMs,
        long loadDurationMs, long bytesLoaded) {
      loadCanceled(dataSpec, dataType, C.TRACK_TYPE_UNKNOWN, null, C.SELECTION_REASON_UNKNOWN,
          null, C.TIME_UNSET, C.TIME_UNSET, elapsedRealtimeMs, loadDurationMs, bytesLoaded);
    }

    public void loadCanceled(final DataSpec dataSpec, final int dataType, final int trackType,
        final Format trackFormat, final int trackSelectionReason, final Object trackSelectionData,
        final long mediaStartTimeUs, final long mediaEndTimeUs, final long elapsedRealtimeMs,
        final long loadDurationMs, final long bytesLoaded) {
      if (listener != null) {
        handler.post(new Runnable()  {
          @Override
          public void run() {
            listener.onLoadCanceled(dataSpec, dataType, trackType, trackFormat,
                trackSelectionReason, trackSelectionData, adjustMediaTime(mediaStartTimeUs),
                adjustMediaTime(mediaEndTimeUs), elapsedRealtimeMs, loadDurationMs, bytesLoaded);
          }
        });
      }
    }

    public void loadError(DataSpec dataSpec, int dataType, long elapsedRealtimeMs,
        long loadDurationMs, long bytesLoaded, IOException error, boolean wasCanceled) {
      loadError(dataSpec, dataType, C.TRACK_TYPE_UNKNOWN, null, C.SELECTION_REASON_UNKNOWN,
          null, C.TIME_UNSET, C.TIME_UNSET, elapsedRealtimeMs, loadDurationMs, bytesLoaded,
          error, wasCanceled);
    }

    public void loadError(final DataSpec dataSpec, final int dataType, final int trackType,
        final Format trackFormat, final int trackSelectionReason, final Object trackSelectionData,
        final long mediaStartTimeUs, final long mediaEndTimeUs, final long elapsedRealtimeMs,
        final long loadDurationMs, final long bytesLoaded, final IOException error,
        final boolean wasCanceled) {
      if (listener != null) {
        handler.post(new Runnable()  {
          @Override
          public void run() {
            listener.onLoadError(dataSpec, dataType, trackType, trackFormat, trackSelectionReason,
                trackSelectionData, adjustMediaTime(mediaStartTimeUs),
                adjustMediaTime(mediaEndTimeUs), elapsedRealtimeMs, loadDurationMs, bytesLoaded,
                error, wasCanceled);
          }
        });
      }
    }

    public void upstreamDiscarded(final int trackType, final long mediaStartTimeUs,
        final long mediaEndTimeUs) {
      if (listener != null) {
        handler.post(new Runnable()  {
          @Override
          public void run() {
            listener.onUpstreamDiscarded(trackType, adjustMediaTime(mediaStartTimeUs),
                adjustMediaTime(mediaEndTimeUs));
          }
        });
      }
    }

    public void downstreamFormatChanged(final int trackType, final Format trackFormat,
        final int trackSelectionReason, final Object trackSelectionData,
        final long mediaTimeUs) {
      if (listener != null) {
        handler.post(new Runnable()  {
          @Override
          public void run() {
            listener.onDownstreamFormatChanged(trackType, trackFormat, trackSelectionReason,
                trackSelectionData, adjustMediaTime(mediaTimeUs));
          }
        });
      }
    }

    private long adjustMediaTime(long mediaTimeUs) {
      long mediaTimeMs = C.usToMs(mediaTimeUs);
      return mediaTimeMs == C.TIME_UNSET ? C.TIME_UNSET : mediaTimeOffsetMs + mediaTimeMs;
    }

  }

}

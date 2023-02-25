/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.source;

import static com.google.android.exoplayer2.util.Util.postOrRun;

import android.os.Handler;
import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.C.DataType;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

/** Interface for callbacks to be notified of {@link MediaSource} events. */
public interface MediaSourceEventListener {

  /**
   * Called when a load begins.
   *
   * @param windowIndex The window index in the timeline of the media source this load belongs to.
   * @param mediaPeriodId The {@link MediaPeriodId} this load belongs to. Null if the load does not
   *     belong to a specific media period.
   * @param loadEventInfo The {@link LoadEventInfo} corresponding to the event. The value of {@link
   *     LoadEventInfo#uri} won't reflect potential redirection yet and {@link
   *     LoadEventInfo#responseHeaders} will be empty.
   * @param mediaLoadData The {@link MediaLoadData} defining the data being loaded.
   */
  default void onLoadStarted(
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData) {}

  /**
   * Called when a load ends.
   *
   * @param windowIndex The window index in the timeline of the media source this load belongs to.
   * @param mediaPeriodId The {@link MediaPeriodId} this load belongs to. Null if the load does not
   *     belong to a specific media period.
   * @param loadEventInfo The {@link LoadEventInfo} corresponding to the event. The values of {@link
   *     LoadEventInfo#elapsedRealtimeMs} and {@link LoadEventInfo#bytesLoaded} are relative to the
   *     corresponding {@link #onLoadStarted(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}
   *     event.
   * @param mediaLoadData The {@link MediaLoadData} defining the data being loaded.
   */
  default void onLoadCompleted(
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData) {}

  /**
   * Called when a load is canceled.
   *
   * @param windowIndex The window index in the timeline of the media source this load belongs to.
   * @param mediaPeriodId The {@link MediaPeriodId} this load belongs to. Null if the load does not
   *     belong to a specific media period.
   * @param loadEventInfo The {@link LoadEventInfo} corresponding to the event. The values of {@link
   *     LoadEventInfo#elapsedRealtimeMs} and {@link LoadEventInfo#bytesLoaded} are relative to the
   *     corresponding {@link #onLoadStarted(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}
   *     event.
   * @param mediaLoadData The {@link MediaLoadData} defining the data being loaded.
   */
  default void onLoadCanceled(
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData) {}

  /**
   * Called when a load error occurs.
   *
   * <p>The error may or may not have resulted in the load being canceled, as indicated by the
   * {@code wasCanceled} parameter. If the load was canceled, {@link #onLoadCanceled} will
   * <em>not</em> be called in addition to this method.
   *
   * <p>This method being called does not indicate that playback has failed, or that it will fail.
   * The player may be able to recover from the error. Hence applications should <em>not</em>
   * implement this method to display a user visible error or initiate an application level retry.
   * {@link Player.Listener#onPlayerError} is the appropriate place to implement such behavior. This
   * method is called to provide the application with an opportunity to log the error if it wishes
   * to do so.
   *
   * @param windowIndex The window index in the timeline of the media source this load belongs to.
   * @param mediaPeriodId The {@link MediaPeriodId} this load belongs to. Null if the load does not
   *     belong to a specific media period.
   * @param loadEventInfo The {@link LoadEventInfo} corresponding to the event. The values of {@link
   *     LoadEventInfo#elapsedRealtimeMs} and {@link LoadEventInfo#bytesLoaded} are relative to the
   *     corresponding {@link #onLoadStarted(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}
   *     event.
   * @param mediaLoadData The {@link MediaLoadData} defining the data being loaded.
   * @param error The load error.
   * @param wasCanceled Whether the load was canceled as a result of the error.
   */
  default void onLoadError(
      int windowIndex,
      @Nullable MediaPeriodId mediaPeriodId,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData,
      IOException error,
      boolean wasCanceled) {}

  /**
   * Called when data is removed from the back of a media buffer, typically so that it can be
   * re-buffered in a different format.
   *
   * @param windowIndex The window index in the timeline of the media source this load belongs to.
   * @param mediaPeriodId The {@link MediaPeriodId} the media belongs to.
   * @param mediaLoadData The {@link MediaLoadData} defining the media being discarded.
   */
  default void onUpstreamDiscarded(
      int windowIndex, MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData) {}

  /**
   * Called when a downstream format change occurs (i.e. when the format of the media being read
   * from one or more {@link SampleStream}s provided by the source changes).
   *
   * @param windowIndex The window index in the timeline of the media source this load belongs to.
   * @param mediaPeriodId The {@link MediaPeriodId} the media belongs to.
   * @param mediaLoadData The {@link MediaLoadData} defining the newly selected downstream data.
   */
  default void onDownstreamFormatChanged(
      int windowIndex, @Nullable MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData) {}

  /** Dispatches events to {@link MediaSourceEventListener MediaSourceEventListeners}. */
  class EventDispatcher {

    /** The timeline window index reported with the events. */
    public final int windowIndex;
    /** The {@link MediaPeriodId} reported with the events. */
    @Nullable public final MediaPeriodId mediaPeriodId;

    private final CopyOnWriteArrayList<ListenerAndHandler> listenerAndHandlers;
    private final long mediaTimeOffsetMs;

    /** Creates an event dispatcher. */
    public EventDispatcher() {
      this(
          /* listenerAndHandlers= */ new CopyOnWriteArrayList<>(),
          /* windowIndex= */ 0,
          /* mediaPeriodId= */ null,
          /* mediaTimeOffsetMs= */ 0);
    }

    private EventDispatcher(
        CopyOnWriteArrayList<ListenerAndHandler> listenerAndHandlers,
        int windowIndex,
        @Nullable MediaPeriodId mediaPeriodId,
        long mediaTimeOffsetMs) {
      this.listenerAndHandlers = listenerAndHandlers;
      this.windowIndex = windowIndex;
      this.mediaPeriodId = mediaPeriodId;
      this.mediaTimeOffsetMs = mediaTimeOffsetMs;
    }

    /**
     * Creates a view of the event dispatcher with pre-configured window index, media period id, and
     * media time offset.
     *
     * @param windowIndex The timeline window index to be reported with the events.
     * @param mediaPeriodId The {@link MediaPeriodId} to be reported with the events.
     * @param mediaTimeOffsetMs The offset to be added to all media times, in milliseconds.
     * @return A view of the event dispatcher with the pre-configured parameters.
     */
    @CheckResult
    public EventDispatcher withParameters(
        int windowIndex, @Nullable MediaPeriodId mediaPeriodId, long mediaTimeOffsetMs) {
      return new EventDispatcher(
          listenerAndHandlers, windowIndex, mediaPeriodId, mediaTimeOffsetMs);
    }

    /**
     * Adds a listener to the event dispatcher.
     *
     * @param handler A handler on the which listener events will be posted.
     * @param eventListener The listener to be added.
     */
    public void addEventListener(Handler handler, MediaSourceEventListener eventListener) {
      Assertions.checkNotNull(handler);
      Assertions.checkNotNull(eventListener);
      listenerAndHandlers.add(new ListenerAndHandler(handler, eventListener));
    }

    /**
     * Removes a listener from the event dispatcher.
     *
     * @param eventListener The listener to be removed.
     */
    public void removeEventListener(MediaSourceEventListener eventListener) {
      for (ListenerAndHandler listenerAndHandler : listenerAndHandlers) {
        if (listenerAndHandler.listener == eventListener) {
          listenerAndHandlers.remove(listenerAndHandler);
        }
      }
    }

    /** Dispatches {@link #onLoadStarted(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}. */
    public void loadStarted(LoadEventInfo loadEventInfo, @DataType int dataType) {
      loadStarted(
          loadEventInfo,
          dataType,
          /* trackType= */ C.TRACK_TYPE_UNKNOWN,
          /* trackFormat= */ null,
          /* trackSelectionReason= */ C.SELECTION_REASON_UNKNOWN,
          /* trackSelectionData= */ null,
          /* mediaStartTimeUs= */ C.TIME_UNSET,
          /* mediaEndTimeUs= */ C.TIME_UNSET);
    }

    /** Dispatches {@link #onLoadStarted(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}. */
    public void loadStarted(
        LoadEventInfo loadEventInfo,
        @DataType int dataType,
        @C.TrackType int trackType,
        @Nullable Format trackFormat,
        @C.SelectionReason int trackSelectionReason,
        @Nullable Object trackSelectionData,
        long mediaStartTimeUs,
        long mediaEndTimeUs) {
      loadStarted(
          loadEventInfo,
          new MediaLoadData(
              dataType,
              trackType,
              trackFormat,
              trackSelectionReason,
              trackSelectionData,
              adjustMediaTime(mediaStartTimeUs),
              adjustMediaTime(mediaEndTimeUs)));
    }

    /** Dispatches {@link #onLoadStarted(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}. */
    public void loadStarted(LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
      for (ListenerAndHandler listenerAndHandler : listenerAndHandlers) {
        MediaSourceEventListener listener = listenerAndHandler.listener;
        postOrRun(
            listenerAndHandler.handler,
            () -> listener.onLoadStarted(windowIndex, mediaPeriodId, loadEventInfo, mediaLoadData));
      }
    }

    /** Dispatches {@link #onLoadCompleted(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}. */
    public void loadCompleted(LoadEventInfo loadEventInfo, @DataType int dataType) {
      loadCompleted(
          loadEventInfo,
          dataType,
          /* trackType= */ C.TRACK_TYPE_UNKNOWN,
          /* trackFormat= */ null,
          /* trackSelectionReason= */ C.SELECTION_REASON_UNKNOWN,
          /* trackSelectionData= */ null,
          /* mediaStartTimeUs= */ C.TIME_UNSET,
          /* mediaEndTimeUs= */ C.TIME_UNSET);
    }

    /** Dispatches {@link #onLoadCompleted(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}. */
    public void loadCompleted(
        LoadEventInfo loadEventInfo,
        @DataType int dataType,
        @C.TrackType int trackType,
        @Nullable Format trackFormat,
        @C.SelectionReason int trackSelectionReason,
        @Nullable Object trackSelectionData,
        long mediaStartTimeUs,
        long mediaEndTimeUs) {
      loadCompleted(
          loadEventInfo,
          new MediaLoadData(
              dataType,
              trackType,
              trackFormat,
              trackSelectionReason,
              trackSelectionData,
              adjustMediaTime(mediaStartTimeUs),
              adjustMediaTime(mediaEndTimeUs)));
    }

    /** Dispatches {@link #onLoadCompleted(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}. */
    public void loadCompleted(LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
      for (ListenerAndHandler listenerAndHandler : listenerAndHandlers) {
        MediaSourceEventListener listener = listenerAndHandler.listener;
        postOrRun(
            listenerAndHandler.handler,
            () ->
                listener.onLoadCompleted(windowIndex, mediaPeriodId, loadEventInfo, mediaLoadData));
      }
    }

    /** Dispatches {@link #onLoadCanceled(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}. */
    public void loadCanceled(LoadEventInfo loadEventInfo, @DataType int dataType) {
      loadCanceled(
          loadEventInfo,
          dataType,
          /* trackType= */ C.TRACK_TYPE_UNKNOWN,
          /* trackFormat= */ null,
          /* trackSelectionReason= */ C.SELECTION_REASON_UNKNOWN,
          /* trackSelectionData= */ null,
          /* mediaStartTimeUs= */ C.TIME_UNSET,
          /* mediaEndTimeUs= */ C.TIME_UNSET);
    }

    /** Dispatches {@link #onLoadCanceled(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}. */
    public void loadCanceled(
        LoadEventInfo loadEventInfo,
        @DataType int dataType,
        @C.TrackType int trackType,
        @Nullable Format trackFormat,
        @C.SelectionReason int trackSelectionReason,
        @Nullable Object trackSelectionData,
        long mediaStartTimeUs,
        long mediaEndTimeUs) {
      loadCanceled(
          loadEventInfo,
          new MediaLoadData(
              dataType,
              trackType,
              trackFormat,
              trackSelectionReason,
              trackSelectionData,
              adjustMediaTime(mediaStartTimeUs),
              adjustMediaTime(mediaEndTimeUs)));
    }

    /** Dispatches {@link #onLoadCanceled(int, MediaPeriodId, LoadEventInfo, MediaLoadData)}. */
    public void loadCanceled(LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
      for (ListenerAndHandler listenerAndHandler : listenerAndHandlers) {
        MediaSourceEventListener listener = listenerAndHandler.listener;
        postOrRun(
            listenerAndHandler.handler,
            () ->
                listener.onLoadCanceled(windowIndex, mediaPeriodId, loadEventInfo, mediaLoadData));
      }
    }

    /**
     * Dispatches {@link #onLoadError(int, MediaPeriodId, LoadEventInfo, MediaLoadData, IOException,
     * boolean)}.
     */
    public void loadError(
        LoadEventInfo loadEventInfo,
        @DataType int dataType,
        IOException error,
        boolean wasCanceled) {
      loadError(
          loadEventInfo,
          dataType,
          /* trackType= */ C.TRACK_TYPE_UNKNOWN,
          /* trackFormat= */ null,
          /* trackSelectionReason= */ C.SELECTION_REASON_UNKNOWN,
          /* trackSelectionData= */ null,
          /* mediaStartTimeUs= */ C.TIME_UNSET,
          /* mediaEndTimeUs= */ C.TIME_UNSET,
          error,
          wasCanceled);
    }

    /**
     * Dispatches {@link #onLoadError(int, MediaPeriodId, LoadEventInfo, MediaLoadData, IOException,
     * boolean)}.
     */
    public void loadError(
        LoadEventInfo loadEventInfo,
        @DataType int dataType,
        @C.TrackType int trackType,
        @Nullable Format trackFormat,
        @C.SelectionReason int trackSelectionReason,
        @Nullable Object trackSelectionData,
        long mediaStartTimeUs,
        long mediaEndTimeUs,
        IOException error,
        boolean wasCanceled) {
      loadError(
          loadEventInfo,
          new MediaLoadData(
              dataType,
              trackType,
              trackFormat,
              trackSelectionReason,
              trackSelectionData,
              adjustMediaTime(mediaStartTimeUs),
              adjustMediaTime(mediaEndTimeUs)),
          error,
          wasCanceled);
    }

    /**
     * Dispatches {@link #onLoadError(int, MediaPeriodId, LoadEventInfo, MediaLoadData, IOException,
     * boolean)}.
     */
    public void loadError(
        LoadEventInfo loadEventInfo,
        MediaLoadData mediaLoadData,
        IOException error,
        boolean wasCanceled) {
      for (ListenerAndHandler listenerAndHandler : listenerAndHandlers) {
        MediaSourceEventListener listener = listenerAndHandler.listener;
        postOrRun(
            listenerAndHandler.handler,
            () ->
                listener.onLoadError(
                    windowIndex, mediaPeriodId, loadEventInfo, mediaLoadData, error, wasCanceled));
      }
    }

    /** Dispatches {@link #onUpstreamDiscarded(int, MediaPeriodId, MediaLoadData)}. */
    public void upstreamDiscarded(int trackType, long mediaStartTimeUs, long mediaEndTimeUs) {
      upstreamDiscarded(
          new MediaLoadData(
              C.DATA_TYPE_MEDIA,
              trackType,
              /* trackFormat= */ null,
              C.SELECTION_REASON_ADAPTIVE,
              /* trackSelectionData= */ null,
              adjustMediaTime(mediaStartTimeUs),
              adjustMediaTime(mediaEndTimeUs)));
    }

    /** Dispatches {@link #onUpstreamDiscarded(int, MediaPeriodId, MediaLoadData)}. */
    public void upstreamDiscarded(MediaLoadData mediaLoadData) {
      MediaPeriodId mediaPeriodId = Assertions.checkNotNull(this.mediaPeriodId);
      for (ListenerAndHandler listenerAndHandler : listenerAndHandlers) {
        MediaSourceEventListener listener = listenerAndHandler.listener;
        postOrRun(
            listenerAndHandler.handler,
            () -> listener.onUpstreamDiscarded(windowIndex, mediaPeriodId, mediaLoadData));
      }
    }

    /** Dispatches {@link #onDownstreamFormatChanged(int, MediaPeriodId, MediaLoadData)}. */
    public void downstreamFormatChanged(
        @C.TrackType int trackType,
        @Nullable Format trackFormat,
        @C.SelectionReason int trackSelectionReason,
        @Nullable Object trackSelectionData,
        long mediaTimeUs) {
      downstreamFormatChanged(
          new MediaLoadData(
              C.DATA_TYPE_MEDIA,
              trackType,
              trackFormat,
              trackSelectionReason,
              trackSelectionData,
              adjustMediaTime(mediaTimeUs),
              /* mediaEndTimeMs= */ C.TIME_UNSET));
    }

    /** Dispatches {@link #onDownstreamFormatChanged(int, MediaPeriodId, MediaLoadData)}. */
    public void downstreamFormatChanged(MediaLoadData mediaLoadData) {
      for (ListenerAndHandler listenerAndHandler : listenerAndHandlers) {
        MediaSourceEventListener listener = listenerAndHandler.listener;
        postOrRun(
            listenerAndHandler.handler,
            () -> listener.onDownstreamFormatChanged(windowIndex, mediaPeriodId, mediaLoadData));
      }
    }

    private long adjustMediaTime(long mediaTimeUs) {
      long mediaTimeMs = Util.usToMs(mediaTimeUs);
      return mediaTimeMs == C.TIME_UNSET ? C.TIME_UNSET : mediaTimeOffsetMs + mediaTimeMs;
    }

    private static final class ListenerAndHandler {

      public Handler handler;
      public MediaSourceEventListener listener;

      public ListenerAndHandler(Handler handler, MediaSourceEventListener listener) {
        this.handler = handler;
        this.listener = listener;
      }
    }
  }
}

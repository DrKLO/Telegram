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
package com.google.android.exoplayer2.source;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceUtil;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy.LoadErrorInfo;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.Loader.LoadErrorAction;
import com.google.android.exoplayer2.upstream.Loader.Loadable;
import com.google.android.exoplayer2.upstream.StatsDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A {@link MediaPeriod} with a single sample. */
/* package */ final class SingleSampleMediaPeriod
    implements MediaPeriod, Loader.Callback<SingleSampleMediaPeriod.SourceLoadable> {

  private static final String TAG = "SingleSampleMediaPeriod";

  /** The initial size of the allocation used to hold the sample data. */
  private static final int INITIAL_SAMPLE_SIZE = 1024;

  private final DataSpec dataSpec;
  private final DataSource.Factory dataSourceFactory;
  @Nullable private final TransferListener transferListener;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final EventDispatcher eventDispatcher;
  private final TrackGroupArray tracks;
  private final ArrayList<SampleStreamImpl> sampleStreams;
  private final long durationUs;

  // Package private to avoid thunk methods.
  /* package */ final Loader loader;
  /* package */ final Format format;
  /* package */ final boolean treatLoadErrorsAsEndOfStream;

  /* package */ boolean loadingFinished;
  /* package */ byte @MonotonicNonNull [] sampleData;
  /* package */ int sampleSize;

  public SingleSampleMediaPeriod(
      DataSpec dataSpec,
      DataSource.Factory dataSourceFactory,
      @Nullable TransferListener transferListener,
      Format format,
      long durationUs,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      EventDispatcher eventDispatcher,
      boolean treatLoadErrorsAsEndOfStream) {
    this.dataSpec = dataSpec;
    this.dataSourceFactory = dataSourceFactory;
    this.transferListener = transferListener;
    this.format = format;
    this.durationUs = durationUs;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.eventDispatcher = eventDispatcher;
    this.treatLoadErrorsAsEndOfStream = treatLoadErrorsAsEndOfStream;
    tracks = new TrackGroupArray(new TrackGroup(format));
    sampleStreams = new ArrayList<>();
    loader = new Loader("SingleSampleMediaPeriod");
  }

  public void release() {
    loader.release();
  }

  @Override
  public void prepare(Callback callback, long positionUs) {
    callback.onPrepared(this);
  }

  @Override
  public void maybeThrowPrepareError() {
    // Do nothing.
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    return tracks;
  }

  @Override
  public long selectTracks(
      @NullableType ExoTrackSelection[] selections,
      boolean[] mayRetainStreamFlags,
      @NullableType SampleStream[] streams,
      boolean[] streamResetFlags,
      long positionUs) {
    for (int i = 0; i < selections.length; i++) {
      if (streams[i] != null && (selections[i] == null || !mayRetainStreamFlags[i])) {
        sampleStreams.remove(streams[i]);
        streams[i] = null;
      }
      if (streams[i] == null && selections[i] != null) {
        SampleStreamImpl stream = new SampleStreamImpl();
        sampleStreams.add(stream);
        streams[i] = stream;
        streamResetFlags[i] = true;
      }
    }
    return positionUs;
  }

  @Override
  public void discardBuffer(long positionUs, boolean toKeyframe) {
    // Do nothing.
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    // Do nothing.
  }

  @Override
  public boolean continueLoading(long positionUs) {
    if (loadingFinished || loader.isLoading() || loader.hasFatalError()) {
      return false;
    }
    DataSource dataSource = dataSourceFactory.createDataSource();
    if (transferListener != null) {
      dataSource.addTransferListener(transferListener);
    }
    SourceLoadable loadable = new SourceLoadable(dataSpec, dataSource);
    long elapsedRealtimeMs =
        loader.startLoading(
            loadable,
            /* callback= */ this,
            loadErrorHandlingPolicy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA));
    eventDispatcher.loadStarted(
        new LoadEventInfo(loadable.loadTaskId, dataSpec, elapsedRealtimeMs),
        C.DATA_TYPE_MEDIA,
        C.TRACK_TYPE_UNKNOWN,
        format,
        C.SELECTION_REASON_UNKNOWN,
        /* trackSelectionData= */ null,
        /* mediaStartTimeUs= */ 0,
        durationUs);
    return true;
  }

  @Override
  public boolean isLoading() {
    return loader.isLoading();
  }

  @Override
  public long readDiscontinuity() {
    return C.TIME_UNSET;
  }

  @Override
  public long getNextLoadPositionUs() {
    return loadingFinished || loader.isLoading() ? C.TIME_END_OF_SOURCE : 0;
  }

  @Override
  public long getBufferedPositionUs() {
    return loadingFinished ? C.TIME_END_OF_SOURCE : 0;
  }

  @Override
  public long seekToUs(long positionUs) {
    for (int i = 0; i < sampleStreams.size(); i++) {
      sampleStreams.get(i).reset();
    }
    return positionUs;
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    return positionUs;
  }

  // Loader.Callback implementation.

  @Override
  public void onLoadCompleted(
      SourceLoadable loadable, long elapsedRealtimeMs, long loadDurationMs) {
    sampleSize = (int) loadable.dataSource.getBytesRead();
    sampleData = Assertions.checkNotNull(loadable.sampleData);
    loadingFinished = true;
    StatsDataSource dataSource = loadable.dataSource;
    LoadEventInfo loadEventInfo =
        new LoadEventInfo(
            loadable.loadTaskId,
            loadable.dataSpec,
            dataSource.getLastOpenedUri(),
            dataSource.getLastResponseHeaders(),
            elapsedRealtimeMs,
            loadDurationMs,
            sampleSize);
    loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
    eventDispatcher.loadCompleted(
        loadEventInfo,
        C.DATA_TYPE_MEDIA,
        C.TRACK_TYPE_UNKNOWN,
        format,
        C.SELECTION_REASON_UNKNOWN,
        /* trackSelectionData= */ null,
        /* mediaStartTimeUs= */ 0,
        durationUs);
  }

  @Override
  public void onLoadCanceled(
      SourceLoadable loadable, long elapsedRealtimeMs, long loadDurationMs, boolean released) {
    StatsDataSource dataSource = loadable.dataSource;
    LoadEventInfo loadEventInfo =
        new LoadEventInfo(
            loadable.loadTaskId,
            loadable.dataSpec,
            dataSource.getLastOpenedUri(),
            dataSource.getLastResponseHeaders(),
            elapsedRealtimeMs,
            loadDurationMs,
            dataSource.getBytesRead());
    loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
    eventDispatcher.loadCanceled(
        loadEventInfo,
        C.DATA_TYPE_MEDIA,
        C.TRACK_TYPE_UNKNOWN,
        /* trackFormat= */ null,
        C.SELECTION_REASON_UNKNOWN,
        /* trackSelectionData= */ null,
        /* mediaStartTimeUs= */ 0,
        durationUs);
  }

  @Override
  public LoadErrorAction onLoadError(
      SourceLoadable loadable,
      long elapsedRealtimeMs,
      long loadDurationMs,
      IOException error,
      int errorCount) {
    StatsDataSource dataSource = loadable.dataSource;
    LoadEventInfo loadEventInfo =
        new LoadEventInfo(
            loadable.loadTaskId,
            loadable.dataSpec,
            dataSource.getLastOpenedUri(),
            dataSource.getLastResponseHeaders(),
            elapsedRealtimeMs,
            loadDurationMs,
            dataSource.getBytesRead());
    MediaLoadData mediaLoadData =
        new MediaLoadData(
            C.DATA_TYPE_MEDIA,
            C.TRACK_TYPE_UNKNOWN,
            format,
            C.SELECTION_REASON_UNKNOWN,
            /* trackSelectionData= */ null,
            /* mediaStartTimeMs= */ 0,
            Util.usToMs(durationUs));
    long retryDelay =
        loadErrorHandlingPolicy.getRetryDelayMsFor(
            new LoadErrorInfo(loadEventInfo, mediaLoadData, error, errorCount));
    boolean errorCanBePropagated =
        retryDelay == C.TIME_UNSET
            || errorCount
                >= loadErrorHandlingPolicy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA);

    LoadErrorAction action;
    if (treatLoadErrorsAsEndOfStream && errorCanBePropagated) {
      Log.w(TAG, "Loading failed, treating as end-of-stream.", error);
      loadingFinished = true;
      action = Loader.DONT_RETRY;
    } else {
      action =
          retryDelay != C.TIME_UNSET
              ? Loader.createRetryAction(/* resetErrorCount= */ false, retryDelay)
              : Loader.DONT_RETRY_FATAL;
    }
    boolean wasCanceled = !action.isRetry();
    eventDispatcher.loadError(
        loadEventInfo,
        C.DATA_TYPE_MEDIA,
        C.TRACK_TYPE_UNKNOWN,
        format,
        C.SELECTION_REASON_UNKNOWN,
        /* trackSelectionData= */ null,
        /* mediaStartTimeUs= */ 0,
        durationUs,
        error,
        wasCanceled);
    if (wasCanceled) {
      loadErrorHandlingPolicy.onLoadTaskConcluded(loadable.loadTaskId);
    }
    return action;
  }

  private final class SampleStreamImpl implements SampleStream {

    private static final int STREAM_STATE_SEND_FORMAT = 0;
    private static final int STREAM_STATE_SEND_SAMPLE = 1;
    private static final int STREAM_STATE_END_OF_STREAM = 2;

    private int streamState;
    private boolean notifiedDownstreamFormat;

    public void reset() {
      if (streamState == STREAM_STATE_END_OF_STREAM) {
        streamState = STREAM_STATE_SEND_SAMPLE;
      }
    }

    @Override
    public boolean isReady() {
      return loadingFinished;
    }

    @Override
    public void maybeThrowError() throws IOException {
      if (!treatLoadErrorsAsEndOfStream) {
        loader.maybeThrowError();
      }
    }

    @Override
    public int readData(
        FormatHolder formatHolder, DecoderInputBuffer buffer, @ReadFlags int readFlags) {
      maybeNotifyDownstreamFormat();
      if (loadingFinished && sampleData == null) {
        streamState = STREAM_STATE_END_OF_STREAM;
      }

      if (streamState == STREAM_STATE_END_OF_STREAM) {
        buffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
        return C.RESULT_BUFFER_READ;
      }

      if ((readFlags & FLAG_REQUIRE_FORMAT) != 0 || streamState == STREAM_STATE_SEND_FORMAT) {
        formatHolder.format = format;
        streamState = STREAM_STATE_SEND_SAMPLE;
        return C.RESULT_FORMAT_READ;
      }

      if (!loadingFinished) {
        return C.RESULT_NOTHING_READ;
      }
      Assertions.checkNotNull(sampleData);

      buffer.addFlag(C.BUFFER_FLAG_KEY_FRAME);
      buffer.timeUs = 0;
      if ((readFlags & FLAG_OMIT_SAMPLE_DATA) == 0) {
        buffer.ensureSpaceForWrite(sampleSize);
        buffer.data.put(sampleData, 0, sampleSize);
      }
      if ((readFlags & FLAG_PEEK) == 0) {
        streamState = STREAM_STATE_END_OF_STREAM;
      }
      return C.RESULT_BUFFER_READ;
    }

    @Override
    public int skipData(long positionUs) {
      maybeNotifyDownstreamFormat();
      if (positionUs > 0 && streamState != STREAM_STATE_END_OF_STREAM) {
        streamState = STREAM_STATE_END_OF_STREAM;
        return 1;
      }
      return 0;
    }

    private void maybeNotifyDownstreamFormat() {
      if (!notifiedDownstreamFormat) {
        eventDispatcher.downstreamFormatChanged(
            MimeTypes.getTrackType(format.sampleMimeType),
            format,
            C.SELECTION_REASON_UNKNOWN,
            /* trackSelectionData= */ null,
            /* mediaTimeUs= */ 0);
        notifiedDownstreamFormat = true;
      }
    }
  }

  /* package */ static final class SourceLoadable implements Loadable {

    public final long loadTaskId;
    public final DataSpec dataSpec;

    private final StatsDataSource dataSource;

    @Nullable private byte[] sampleData;

    public SourceLoadable(DataSpec dataSpec, DataSource dataSource) {
      this.loadTaskId = LoadEventInfo.getNewId();
      this.dataSpec = dataSpec;
      this.dataSource = new StatsDataSource(dataSource);
    }

    @Override
    public void cancelLoad() {
      // Never happens.
    }

    @Override
    public void load() throws IOException {
      // We always load from the beginning, so reset bytesRead to 0.
      dataSource.resetBytesRead();
      try {
        // Create and open the input.
        dataSource.open(dataSpec);
        // Load the sample data.
        int result = 0;
        while (result != C.RESULT_END_OF_INPUT) {
          int sampleSize = (int) dataSource.getBytesRead();
          if (sampleData == null) {
            sampleData = new byte[INITIAL_SAMPLE_SIZE];
          } else if (sampleSize == sampleData.length) {
            sampleData = Arrays.copyOf(sampleData, sampleData.length * 2);
          }
          result = dataSource.read(sampleData, sampleSize, sampleData.length - sampleSize);
        }
      } finally {
        DataSourceUtil.closeQuietly(dataSource);
      }
    }
  }
}

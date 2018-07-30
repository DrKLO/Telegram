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

import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.FormatHolder;
import org.telegram.messenger.exoplayer2.SeekParameters;
import org.telegram.messenger.exoplayer2.decoder.DecoderInputBuffer;
import org.telegram.messenger.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelection;
import org.telegram.messenger.exoplayer2.upstream.DataSource;
import org.telegram.messenger.exoplayer2.upstream.DataSpec;
import org.telegram.messenger.exoplayer2.upstream.Loader;
import org.telegram.messenger.exoplayer2.upstream.Loader.Loadable;
import org.telegram.messenger.exoplayer2.util.MimeTypes;
import org.telegram.messenger.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * A {@link MediaPeriod} with a single sample.
 */
/* package */ final class SingleSampleMediaPeriod implements MediaPeriod,
    Loader.Callback<SingleSampleMediaPeriod.SourceLoadable>  {

  /**
   * The initial size of the allocation used to hold the sample data.
   */
  private static final int INITIAL_SAMPLE_SIZE = 1024;

  private final DataSpec dataSpec;
  private final DataSource.Factory dataSourceFactory;
  private final int minLoadableRetryCount;
  private final EventDispatcher eventDispatcher;
  private final TrackGroupArray tracks;
  private final ArrayList<SampleStreamImpl> sampleStreams;
  private final long durationUs;

  // Package private to avoid thunk methods.
  /* package */ final Loader loader;
  /* package */ final Format format;
  /* package */ final boolean treatLoadErrorsAsEndOfStream;

  /* package */ boolean notifiedReadingStarted;
  /* package */ boolean loadingFinished;
  /* package */ boolean loadingSucceeded;
  /* package */ byte[] sampleData;
  /* package */ int sampleSize;
  private int errorCount;

  public SingleSampleMediaPeriod(
      DataSpec dataSpec,
      DataSource.Factory dataSourceFactory,
      Format format,
      long durationUs,
      int minLoadableRetryCount,
      EventDispatcher eventDispatcher,
      boolean treatLoadErrorsAsEndOfStream) {
    this.dataSpec = dataSpec;
    this.dataSourceFactory = dataSourceFactory;
    this.format = format;
    this.durationUs = durationUs;
    this.minLoadableRetryCount = minLoadableRetryCount;
    this.eventDispatcher = eventDispatcher;
    this.treatLoadErrorsAsEndOfStream = treatLoadErrorsAsEndOfStream;
    tracks = new TrackGroupArray(new TrackGroup(format));
    sampleStreams = new ArrayList<>();
    loader = new Loader("Loader:SingleSampleMediaPeriod");
    eventDispatcher.mediaPeriodCreated();
  }

  public void release() {
    loader.release();
    eventDispatcher.mediaPeriodReleased();
  }

  @Override
  public void prepare(Callback callback, long positionUs) {
    callback.onPrepared(this);
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    // Do nothing.
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    return tracks;
  }

  @Override
  public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags,
      SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
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
    if (loadingFinished || loader.isLoading()) {
      return false;
    }
    long elapsedRealtimeMs =
        loader.startLoading(
            new SourceLoadable(dataSpec, dataSourceFactory.createDataSource()),
            this,
            minLoadableRetryCount);
    eventDispatcher.loadStarted(
        dataSpec,
        C.DATA_TYPE_MEDIA,
        C.TRACK_TYPE_UNKNOWN,
        format,
        C.SELECTION_REASON_UNKNOWN,
        /* trackSelectionData= */ null,
        /* mediaStartTimeUs= */ 0,
        durationUs,
        elapsedRealtimeMs);
    return true;
  }

  @Override
  public long readDiscontinuity() {
    if (!notifiedReadingStarted) {
      eventDispatcher.readingStarted();
      notifiedReadingStarted = true;
    }
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
  public void onLoadCompleted(SourceLoadable loadable, long elapsedRealtimeMs,
      long loadDurationMs) {
    eventDispatcher.loadCompleted(
        loadable.dataSpec,
        C.DATA_TYPE_MEDIA,
        C.TRACK_TYPE_UNKNOWN,
        format,
        C.SELECTION_REASON_UNKNOWN,
        /* trackSelectionData= */ null,
        /* mediaStartTimeUs= */ 0,
        durationUs,
        elapsedRealtimeMs,
        loadDurationMs,
        loadable.sampleSize);
    sampleSize = loadable.sampleSize;
    sampleData = loadable.sampleData;
    loadingFinished = true;
    loadingSucceeded = true;
  }

  @Override
  public void onLoadCanceled(SourceLoadable loadable, long elapsedRealtimeMs, long loadDurationMs,
      boolean released) {
    eventDispatcher.loadCanceled(
        loadable.dataSpec,
        C.DATA_TYPE_MEDIA,
        C.TRACK_TYPE_UNKNOWN,
        /* trackFormat= */ null,
        C.SELECTION_REASON_UNKNOWN,
        /* trackSelectionData= */ null,
        /* mediaStartTimeUs= */ 0,
        durationUs,
        elapsedRealtimeMs,
        loadDurationMs,
        loadable.sampleSize);
  }

  @Override
  public @Loader.RetryAction int onLoadError(
      SourceLoadable loadable, long elapsedRealtimeMs, long loadDurationMs, IOException error) {
    errorCount++;
    boolean cancel = treatLoadErrorsAsEndOfStream && errorCount >= minLoadableRetryCount;
    eventDispatcher.loadError(
        loadable.dataSpec,
        C.DATA_TYPE_MEDIA,
        C.TRACK_TYPE_UNKNOWN,
        format,
        C.SELECTION_REASON_UNKNOWN,
        /* trackSelectionData= */ null,
        /* mediaStartTimeUs= */ 0,
        durationUs,
        elapsedRealtimeMs,
        loadDurationMs,
        loadable.sampleSize,
        error,
        /* wasCanceled= */ cancel);
    if (cancel) {
      loadingFinished = true;
      return Loader.DONT_RETRY;
    }
    return Loader.RETRY;
  }

  private final class SampleStreamImpl implements SampleStream {

    private static final int STREAM_STATE_SEND_FORMAT = 0;
    private static final int STREAM_STATE_SEND_SAMPLE = 1;
    private static final int STREAM_STATE_END_OF_STREAM = 2;

    private int streamState;
    private boolean formatSent;

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
    public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer,
        boolean requireFormat) {
      if (streamState == STREAM_STATE_END_OF_STREAM) {
        buffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
        return C.RESULT_BUFFER_READ;
      } else if (requireFormat || streamState == STREAM_STATE_SEND_FORMAT) {
        formatHolder.format = format;
        streamState = STREAM_STATE_SEND_SAMPLE;
        return C.RESULT_FORMAT_READ;
      } else if (loadingFinished) {
        if (loadingSucceeded) {
          buffer.timeUs = 0;
          buffer.addFlag(C.BUFFER_FLAG_KEY_FRAME);
          buffer.ensureSpaceForWrite(sampleSize);
          buffer.data.put(sampleData, 0, sampleSize);
          sendFormat();
        } else {
          buffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
        }
        streamState = STREAM_STATE_END_OF_STREAM;
        return C.RESULT_BUFFER_READ;
      }
      return C.RESULT_NOTHING_READ;
    }

    @Override
    public int skipData(long positionUs) {
      if (positionUs > 0 && streamState != STREAM_STATE_END_OF_STREAM) {
        streamState = STREAM_STATE_END_OF_STREAM;
        sendFormat();
        return 1;
      }
      return 0;
    }

    private void sendFormat() {
      if (!formatSent) {
        eventDispatcher.downstreamFormatChanged(
            MimeTypes.getTrackType(format.sampleMimeType),
            format,
            C.SELECTION_REASON_UNKNOWN,
            /* trackSelectionData= */ null,
            /* mediaTimeUs= */ 0);
        formatSent = true;
      }
    }
  }

  /* package */ static final class SourceLoadable implements Loadable {

    public final DataSpec dataSpec;

    private final DataSource dataSource;

    private int sampleSize;
    private byte[] sampleData;

    public SourceLoadable(DataSpec dataSpec, DataSource dataSource) {
      this.dataSpec = dataSpec;
      this.dataSource = dataSource;
    }

    @Override
    public void cancelLoad() {
      // Never happens.
    }

    @Override
    public boolean isLoadCanceled() {
      return false;
    }

    @Override
    public void load() throws IOException, InterruptedException {
      // We always load from the beginning, so reset the sampleSize to 0.
      sampleSize = 0;
      try {
        // Create and open the input.
        dataSource.open(dataSpec);
        // Load the sample data.
        int result = 0;
        while (result != C.RESULT_END_OF_INPUT) {
          sampleSize += result;
          if (sampleData == null) {
            sampleData = new byte[INITIAL_SAMPLE_SIZE];
          } else if (sampleSize == sampleData.length) {
            sampleData = Arrays.copyOf(sampleData, sampleData.length * 2);
          }
          result = dataSource.read(sampleData, sampleSize, sampleData.length - sampleSize);
        }
      } finally {
        Util.closeQuietly(dataSource);
      }
    }

  }

}

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

import android.net.Uri;
import android.os.Handler;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.FormatHolder;
import org.telegram.messenger.exoplayer2.decoder.DecoderInputBuffer;
import org.telegram.messenger.exoplayer2.source.SingleSampleMediaSource.EventListener;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelection;
import org.telegram.messenger.exoplayer2.upstream.DataSource;
import org.telegram.messenger.exoplayer2.upstream.DataSpec;
import org.telegram.messenger.exoplayer2.upstream.Loader;
import org.telegram.messenger.exoplayer2.upstream.Loader.Loadable;
import org.telegram.messenger.exoplayer2.util.Assertions;
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

  private final Uri uri;
  private final DataSource.Factory dataSourceFactory;
  private final int minLoadableRetryCount;
  private final Handler eventHandler;
  private final EventListener eventListener;
  private final int eventSourceId;
  private final TrackGroupArray tracks;
  private final ArrayList<SampleStreamImpl> sampleStreams;
  /* package */ final Loader loader;
  /* package */ final Format format;

  /* package */ boolean loadingFinished;
  /* package */ byte[] sampleData;
  /* package */ int sampleSize;

  public SingleSampleMediaPeriod(Uri uri, DataSource.Factory dataSourceFactory, Format format,
      int minLoadableRetryCount, Handler eventHandler, EventListener eventListener,
      int eventSourceId) {
    this.uri = uri;
    this.dataSourceFactory = dataSourceFactory;
    this.format = format;
    this.minLoadableRetryCount = minLoadableRetryCount;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    this.eventSourceId = eventSourceId;
    tracks = new TrackGroupArray(new TrackGroup(format));
    sampleStreams = new ArrayList<>();
    loader = new Loader("Loader:SingleSampleMediaPeriod");
  }

  public void release() {
    loader.release();
  }

  @Override
  public void prepare(Callback callback, long positionUs) {
    callback.onPrepared(this);
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    loader.maybeThrowError();
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
  public void discardBuffer(long positionUs) {
    // Do nothing.
  }

  @Override
  public boolean continueLoading(long positionUs) {
    if (loadingFinished || loader.isLoading()) {
      return false;
    }
    loader.startLoading(new SourceLoadable(uri, dataSourceFactory.createDataSource()), this,
        minLoadableRetryCount);
    return true;
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
      sampleStreams.get(i).seekToUs(positionUs);
    }
    return positionUs;
  }

  // Loader.Callback implementation.

  @Override
  public void onLoadCompleted(SourceLoadable loadable, long elapsedRealtimeMs,
      long loadDurationMs) {
    sampleSize = loadable.sampleSize;
    sampleData = loadable.sampleData;
    loadingFinished = true;
  }

  @Override
  public void onLoadCanceled(SourceLoadable loadable, long elapsedRealtimeMs, long loadDurationMs,
      boolean released) {
    // Do nothing.
  }

  @Override
  public int onLoadError(SourceLoadable loadable, long elapsedRealtimeMs, long loadDurationMs,
      IOException error) {
    notifyLoadError(error);
    return Loader.RETRY;
  }

  // Internal methods.

  private void notifyLoadError(final IOException e) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable() {
        @Override
        public void run() {
          eventListener.onLoadError(eventSourceId, e);
        }
      });
    }
  }

  private final class SampleStreamImpl implements SampleStream {

    private static final int STREAM_STATE_SEND_FORMAT = 0;
    private static final int STREAM_STATE_SEND_SAMPLE = 1;
    private static final int STREAM_STATE_END_OF_STREAM = 2;

    private int streamState;

    public void seekToUs(long positionUs) {
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
      loader.maybeThrowError();
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
      }

      Assertions.checkState(streamState == STREAM_STATE_SEND_SAMPLE);
      if (!loadingFinished) {
        return C.RESULT_NOTHING_READ;
      } else {
        buffer.timeUs = 0;
        buffer.addFlag(C.BUFFER_FLAG_KEY_FRAME);
        buffer.ensureSpaceForWrite(sampleSize);
        buffer.data.put(sampleData, 0, sampleSize);
        streamState = STREAM_STATE_END_OF_STREAM;
        return C.RESULT_BUFFER_READ;
      }
    }

    @Override
    public void skipData(long positionUs) {
      if (positionUs > 0) {
        streamState = STREAM_STATE_END_OF_STREAM;
      }
    }

  }

  /* package */ static final class SourceLoadable implements Loadable {

    private final Uri uri;
    private final DataSource dataSource;

    private int sampleSize;
    private byte[] sampleData;

    public SourceLoadable(Uri uri, DataSource dataSource) {
      this.uri = uri;
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
        dataSource.open(new DataSpec(uri));
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

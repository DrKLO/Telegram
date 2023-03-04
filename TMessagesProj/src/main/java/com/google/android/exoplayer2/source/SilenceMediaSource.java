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
package com.google.android.exoplayer2.source;

import static java.lang.Math.min;

import android.net.Uri;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/** Media source with a single period consisting of silent raw audio of a given duration. */
public final class SilenceMediaSource extends BaseMediaSource {

  /** Factory for {@link SilenceMediaSource SilenceMediaSources}. */
  public static final class Factory {

    private long durationUs;
    @Nullable private Object tag;

    /**
     * Sets the duration of the silent audio. The value needs to be a positive value.
     *
     * @param durationUs The duration of silent audio to output, in microseconds.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setDurationUs(@IntRange(from = 1) long durationUs) {
      this.durationUs = durationUs;
      return this;
    }

    /**
     * Sets a tag for the media source which will be published in the {@link Timeline} of the source
     * as {@link MediaItem.LocalConfiguration#tag Window#mediaItem.localConfiguration.tag}.
     *
     * @param tag A tag for the media source.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setTag(@Nullable Object tag) {
      this.tag = tag;
      return this;
    }

    /**
     * Creates a new {@link SilenceMediaSource}.
     *
     * @throws IllegalStateException if the duration is a non-positive value.
     */
    public SilenceMediaSource createMediaSource() {
      Assertions.checkState(durationUs > 0);
      return new SilenceMediaSource(durationUs, MEDIA_ITEM.buildUpon().setTag(tag).build());
    }
  }

  /** The media id used by any media item of silence media sources. */
  public static final String MEDIA_ID = "SilenceMediaSource";

  private static final int SAMPLE_RATE_HZ = 44100;
  private static final @C.PcmEncoding int PCM_ENCODING = C.ENCODING_PCM_16BIT;
  private static final int CHANNEL_COUNT = 2;
  private static final Format FORMAT =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.AUDIO_RAW)
          .setChannelCount(CHANNEL_COUNT)
          .setSampleRate(SAMPLE_RATE_HZ)
          .setPcmEncoding(PCM_ENCODING)
          .build();
  private static final MediaItem MEDIA_ITEM =
      new MediaItem.Builder()
          .setMediaId(MEDIA_ID)
          .setUri(Uri.EMPTY)
          .setMimeType(FORMAT.sampleMimeType)
          .build();
  private static final byte[] SILENCE_SAMPLE =
      new byte[Util.getPcmFrameSize(PCM_ENCODING, CHANNEL_COUNT) * 1024];

  private final long durationUs;
  private final MediaItem mediaItem;

  /**
   * Creates a new media source providing silent audio of the given duration.
   *
   * @param durationUs The duration of silent audio to output, in microseconds.
   */
  public SilenceMediaSource(long durationUs) {
    this(durationUs, MEDIA_ITEM);
  }

  /**
   * Creates a new media source providing silent audio of the given duration.
   *
   * @param durationUs The duration of silent audio to output, in microseconds.
   * @param mediaItem The media item associated with this media source.
   */
  private SilenceMediaSource(long durationUs, MediaItem mediaItem) {
    Assertions.checkArgument(durationUs >= 0);
    this.durationUs = durationUs;
    this.mediaItem = mediaItem;
  }

  @Override
  protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
    refreshSourceInfo(
        new SinglePeriodTimeline(
            durationUs,
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* useLiveConfiguration= */ false,
            /* manifest= */ null,
            mediaItem));
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() {}

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    return new SilenceMediaPeriod(durationUs);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {}

  @Override
  public MediaItem getMediaItem() {
    return mediaItem;
  }

  @Override
  protected void releaseSourceInternal() {}

  private static final class SilenceMediaPeriod implements MediaPeriod {

    private static final TrackGroupArray TRACKS = new TrackGroupArray(new TrackGroup(FORMAT));

    private final long durationUs;
    private final ArrayList<SampleStream> sampleStreams;

    public SilenceMediaPeriod(long durationUs) {
      this.durationUs = durationUs;
      sampleStreams = new ArrayList<>();
    }

    @Override
    public void prepare(Callback callback, long positionUs) {
      callback.onPrepared(/* mediaPeriod= */ this);
    }

    @Override
    public void maybeThrowPrepareError() {}

    @Override
    public TrackGroupArray getTrackGroups() {
      return TRACKS;
    }

    @Override
    public long selectTracks(
        @NullableType ExoTrackSelection[] selections,
        boolean[] mayRetainStreamFlags,
        @NullableType SampleStream[] streams,
        boolean[] streamResetFlags,
        long positionUs) {
      positionUs = constrainSeekPosition(positionUs);
      for (int i = 0; i < selections.length; i++) {
        if (streams[i] != null && (selections[i] == null || !mayRetainStreamFlags[i])) {
          sampleStreams.remove(streams[i]);
          streams[i] = null;
        }
        if (streams[i] == null && selections[i] != null) {
          SilenceSampleStream stream = new SilenceSampleStream(durationUs);
          stream.seekTo(positionUs);
          sampleStreams.add(stream);
          streams[i] = stream;
          streamResetFlags[i] = true;
        }
      }
      return positionUs;
    }

    @Override
    public void discardBuffer(long positionUs, boolean toKeyframe) {}

    @Override
    public long readDiscontinuity() {
      return C.TIME_UNSET;
    }

    @Override
    public long seekToUs(long positionUs) {
      positionUs = constrainSeekPosition(positionUs);
      for (int i = 0; i < sampleStreams.size(); i++) {
        ((SilenceSampleStream) sampleStreams.get(i)).seekTo(positionUs);
      }
      return positionUs;
    }

    @Override
    public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
      return constrainSeekPosition(positionUs);
    }

    @Override
    public long getBufferedPositionUs() {
      return C.TIME_END_OF_SOURCE;
    }

    @Override
    public long getNextLoadPositionUs() {
      return C.TIME_END_OF_SOURCE;
    }

    @Override
    public boolean continueLoading(long positionUs) {
      return false;
    }

    @Override
    public boolean isLoading() {
      return false;
    }

    @Override
    public void reevaluateBuffer(long positionUs) {}

    private long constrainSeekPosition(long positionUs) {
      return Util.constrainValue(positionUs, 0, durationUs);
    }
  }

  private static final class SilenceSampleStream implements SampleStream {

    private final long durationBytes;

    private boolean sentFormat;
    private long positionBytes;

    public SilenceSampleStream(long durationUs) {
      durationBytes = getAudioByteCount(durationUs);
      seekTo(0);
    }

    public void seekTo(long positionUs) {
      positionBytes = Util.constrainValue(getAudioByteCount(positionUs), 0, durationBytes);
    }

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public void maybeThrowError() {}

    @Override
    public int readData(
        FormatHolder formatHolder, DecoderInputBuffer buffer, @ReadFlags int readFlags) {
      if (!sentFormat || (readFlags & FLAG_REQUIRE_FORMAT) != 0) {
        formatHolder.format = FORMAT;
        sentFormat = true;
        return C.RESULT_FORMAT_READ;
      }

      long bytesRemaining = durationBytes - positionBytes;
      if (bytesRemaining == 0) {
        buffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
        return C.RESULT_BUFFER_READ;
      }

      buffer.timeUs = getAudioPositionUs(positionBytes);
      buffer.addFlag(C.BUFFER_FLAG_KEY_FRAME);
      int bytesToWrite = (int) min(SILENCE_SAMPLE.length, bytesRemaining);
      if ((readFlags & FLAG_OMIT_SAMPLE_DATA) == 0) {
        buffer.ensureSpaceForWrite(bytesToWrite);
        buffer.data.put(SILENCE_SAMPLE, /* offset= */ 0, bytesToWrite);
      }
      if ((readFlags & FLAG_PEEK) == 0) {
        positionBytes += bytesToWrite;
      }
      return C.RESULT_BUFFER_READ;
    }

    @Override
    public int skipData(long positionUs) {
      long oldPositionBytes = positionBytes;
      seekTo(positionUs);
      return (int) ((positionBytes - oldPositionBytes) / SILENCE_SAMPLE.length);
    }
  }

  private static long getAudioByteCount(long durationUs) {
    long audioSampleCount = durationUs * SAMPLE_RATE_HZ / C.MICROS_PER_SECOND;
    return Util.getPcmFrameSize(PCM_ENCODING, CHANNEL_COUNT) * audioSampleCount;
  }

  private static long getAudioPositionUs(long bytes) {
    long audioSampleCount = bytes / Util.getPcmFrameSize(PCM_ENCODING, CHANNEL_COUNT);
    return audioSampleCount * C.MICROS_PER_SECOND / SAMPLE_RATE_HZ;
  }
}

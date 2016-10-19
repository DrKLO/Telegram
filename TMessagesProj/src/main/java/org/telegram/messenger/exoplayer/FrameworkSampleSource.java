/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.telegram.messenger.exoplayer;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaExtractor;
import android.net.Uri;
import org.telegram.messenger.exoplayer.SampleSource.SampleSourceReader;
import org.telegram.messenger.exoplayer.drm.DrmInitData;
import org.telegram.messenger.exoplayer.drm.DrmInitData.SchemeInitData;
import org.telegram.messenger.exoplayer.extractor.ExtractorSampleSource;
import org.telegram.messenger.exoplayer.extractor.mp4.PsshAtomUtil;
import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.MimeTypes;
import org.telegram.messenger.exoplayer.util.Util;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/**
 * Extracts samples from a stream using Android's {@link MediaExtractor}.
 * <p>
 * Warning - This class is marked as deprecated because there are known device specific issues
 * associated with its use, including playbacks not starting, playbacks stuttering and other
 * miscellaneous failures. For mp4, m4a, mp3, webm, mkv, mpeg-ts, ogg, wav and aac playbacks it is
 * strongly recommended to use {@link ExtractorSampleSource} instead. Where this is not possible
 * this class can still be used, but please be aware of the associated risks. Playing container
 * formats for which an ExoPlayer extractor does not yet exist (e.g. avi) is a valid use case of
 * this class.
 * <p>
 * Over time we hope to enhance {@link ExtractorSampleSource} to support more formats, and hence
 * make use of this class unnecessary.
 */
// TODO: This implementation needs to be fixed so that its methods are non-blocking (either
// through use of a background thread, or through changes to the framework's MediaExtractor API).
@Deprecated
@TargetApi(16)
public final class FrameworkSampleSource implements SampleSource, SampleSourceReader {

  private static final int ALLOWED_FLAGS_MASK = C.SAMPLE_FLAG_SYNC | C.SAMPLE_FLAG_ENCRYPTED;

  private static final int TRACK_STATE_DISABLED = 0;
  private static final int TRACK_STATE_ENABLED = 1;
  private static final int TRACK_STATE_FORMAT_SENT = 2;

  // Parameters for a Uri data source.
  private final Context context;
  private final Uri uri;
  private final Map<String, String> headers;

  // Parameters for a FileDescriptor data source.
  private final FileDescriptor fileDescriptor;
  private final long fileDescriptorOffset;
  private final long fileDescriptorLength;

  private IOException preparationError;
  private MediaExtractor extractor;
  private MediaFormat[] trackFormats;
  private boolean prepared;
  private int remainingReleaseCount;
  private int[] trackStates;
  private boolean[] pendingDiscontinuities;

  private long lastSeekPositionUs;
  private long pendingSeekPositionUs;

  /**
   * Instantiates a new sample extractor reading from the specified {@code uri}.
   *
   * @param context Context for resolving {@code uri}.
   * @param uri The content URI from which to extract data.
   * @param headers Headers to send with requests for data.
   */
  public FrameworkSampleSource(Context context, Uri uri, Map<String, String> headers) {
    Assertions.checkState(Util.SDK_INT >= 16);
    this.context = Assertions.checkNotNull(context);
    this.uri = Assertions.checkNotNull(uri);
    this.headers = headers;
    fileDescriptor = null;
    fileDescriptorOffset = 0;
    fileDescriptorLength = 0;
  }

  /**
   * Instantiates a new sample extractor reading from the specified seekable {@code fileDescriptor}.
   * The caller is responsible for releasing the file descriptor.
   *
   * @param fileDescriptor File descriptor from which to read.
   * @param fileDescriptorOffset The offset in bytes where the data to be extracted starts.
   * @param fileDescriptorLength The length in bytes of the data to be extracted.
   */
  public FrameworkSampleSource(FileDescriptor fileDescriptor, long fileDescriptorOffset,
      long fileDescriptorLength) {
    Assertions.checkState(Util.SDK_INT >= 16);
    this.fileDescriptor = Assertions.checkNotNull(fileDescriptor);
    this.fileDescriptorOffset = fileDescriptorOffset;
    this.fileDescriptorLength = fileDescriptorLength;
    context = null;
    uri = null;
    headers = null;
  }

  @Override
  public SampleSourceReader register() {
    remainingReleaseCount++;
    return this;
  }

  @Override
  public boolean prepare(long positionUs) {
    if (!prepared) {
      if (preparationError != null) {
        return false;
      }

      extractor = new MediaExtractor();
      try {
        if (context != null) {
          extractor.setDataSource(context, uri, headers);
        } else {
          extractor.setDataSource(fileDescriptor, fileDescriptorOffset, fileDescriptorLength);
        }
      } catch (IOException e) {
        preparationError = e;
        return false;
      }

      trackStates = new int[extractor.getTrackCount()];
      pendingDiscontinuities = new boolean[trackStates.length];
      trackFormats = new MediaFormat[trackStates.length];
      for (int i = 0; i < trackStates.length; i++) {
        trackFormats[i] = createMediaFormat(extractor.getTrackFormat(i));
      }
      prepared = true;
    }
    return true;
  }

  @Override
  public int getTrackCount() {
    Assertions.checkState(prepared);
    return trackStates.length;
  }

  @Override
  public MediaFormat getFormat(int track) {
    Assertions.checkState(prepared);
    return trackFormats[track];
  }

  @Override
  public void enable(int track, long positionUs) {
    Assertions.checkState(prepared);
    Assertions.checkState(trackStates[track] == TRACK_STATE_DISABLED);
    trackStates[track] = TRACK_STATE_ENABLED;
    extractor.selectTrack(track);
    seekToUsInternal(positionUs, positionUs != 0);
  }

  @Override
  public boolean continueBuffering(int track, long positionUs) {
    // MediaExtractor takes care of buffering and blocks until it has samples, so we can always
    // return true here. Although note that the blocking behavior is itself as bug, as per the
    // TODO further up this file. This method will need to return something else as part of fixing
    // the TODO.
    return true;
  }

  @Override
  public long readDiscontinuity(int track) {
    if (pendingDiscontinuities[track]) {
      pendingDiscontinuities[track] = false;
      return lastSeekPositionUs;
    }
    return NO_DISCONTINUITY;
  }

  @Override
  public int readData(int track, long positionUs, MediaFormatHolder formatHolder,
      SampleHolder sampleHolder) {
    Assertions.checkState(prepared);
    Assertions.checkState(trackStates[track] != TRACK_STATE_DISABLED);
    if (pendingDiscontinuities[track]) {
      return NOTHING_READ;
    }
    if (trackStates[track] != TRACK_STATE_FORMAT_SENT) {
      formatHolder.format = trackFormats[track];
      formatHolder.drmInitData = Util.SDK_INT >= 18 ? getDrmInitDataV18() : null;
      trackStates[track] = TRACK_STATE_FORMAT_SENT;
      return FORMAT_READ;
    }
    int extractorTrackIndex = extractor.getSampleTrackIndex();
    if (extractorTrackIndex == track) {
      if (sampleHolder.data != null) {
        int offset = sampleHolder.data.position();
        sampleHolder.size = extractor.readSampleData(sampleHolder.data, offset);
        sampleHolder.data.position(offset + sampleHolder.size);
      } else {
        sampleHolder.size = 0;
      }
      sampleHolder.timeUs = extractor.getSampleTime();
      sampleHolder.flags = extractor.getSampleFlags() & ALLOWED_FLAGS_MASK;
      if (sampleHolder.isEncrypted()) {
        sampleHolder.cryptoInfo.setFromExtractorV16(extractor);
      }
      pendingSeekPositionUs = C.UNKNOWN_TIME_US;
      extractor.advance();
      return SAMPLE_READ;
    } else {
      return extractorTrackIndex < 0 ? END_OF_STREAM : NOTHING_READ;
    }
  }

  @Override
  public void disable(int track) {
    Assertions.checkState(prepared);
    Assertions.checkState(trackStates[track] != TRACK_STATE_DISABLED);
    extractor.unselectTrack(track);
    pendingDiscontinuities[track] = false;
    trackStates[track] = TRACK_STATE_DISABLED;
  }

  @Override
  public void maybeThrowError() throws IOException {
    if (preparationError != null) {
      throw preparationError;
    }
  }

  @Override
  public void seekToUs(long positionUs) {
    Assertions.checkState(prepared);
    seekToUsInternal(positionUs, false);
  }

  @Override
  public long getBufferedPositionUs() {
    Assertions.checkState(prepared);
    long bufferedDurationUs = extractor.getCachedDuration();
    if (bufferedDurationUs == -1) {
      return TrackRenderer.UNKNOWN_TIME_US;
    } else {
      long sampleTime = extractor.getSampleTime();
      return sampleTime == -1 ? TrackRenderer.END_OF_TRACK_US : sampleTime + bufferedDurationUs;
    }
  }

  @Override
  public void release() {
    Assertions.checkState(remainingReleaseCount > 0);
    if (--remainingReleaseCount == 0 && extractor != null) {
      extractor.release();
      extractor = null;
    }
  }

  @TargetApi(18)
  private DrmInitData getDrmInitDataV18() {
    // MediaExtractor only supports psshInfo for MP4, so it's ok to hard code the mimeType here.
    Map<UUID, byte[]> psshInfo = extractor.getPsshInfo();
    if (psshInfo == null || psshInfo.isEmpty()) {
      return null;
    }
    DrmInitData.Mapped drmInitData = new DrmInitData.Mapped();
    for (UUID uuid : psshInfo.keySet()) {
      byte[] psshAtom = PsshAtomUtil.buildPsshAtom(uuid, psshInfo.get(uuid));
      drmInitData.put(uuid, new SchemeInitData(MimeTypes.VIDEO_MP4, psshAtom));
    }
    return drmInitData;
  }

  private void seekToUsInternal(long positionUs, boolean force) {
    // Unless forced, avoid duplicate calls to the underlying extractor's seek method in the case
    // that there have been no interleaving calls to readSample.
    if (force || pendingSeekPositionUs != positionUs) {
      lastSeekPositionUs = positionUs;
      pendingSeekPositionUs = positionUs;
      extractor.seekTo(positionUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
      for (int i = 0; i < trackStates.length; ++i) {
        if (trackStates[i] != TRACK_STATE_DISABLED) {
          pendingDiscontinuities[i] = true;
        }
      }
    }
  }

  @SuppressLint("InlinedApi")
  private static MediaFormat createMediaFormat(android.media.MediaFormat format) {
    String mimeType = format.getString(android.media.MediaFormat.KEY_MIME);
    String language = getOptionalStringV16(format, android.media.MediaFormat.KEY_LANGUAGE);
    int maxInputSize = getOptionalIntegerV16(format, android.media.MediaFormat.KEY_MAX_INPUT_SIZE);
    int width = getOptionalIntegerV16(format, android.media.MediaFormat.KEY_WIDTH);
    int height = getOptionalIntegerV16(format, android.media.MediaFormat.KEY_HEIGHT);
    int rotationDegrees = getOptionalIntegerV16(format, "rotation-degrees");
    int channelCount = getOptionalIntegerV16(format, android.media.MediaFormat.KEY_CHANNEL_COUNT);
    int sampleRate = getOptionalIntegerV16(format, android.media.MediaFormat.KEY_SAMPLE_RATE);
    int encoderDelay = getOptionalIntegerV16(format, "encoder-delay");
    int encoderPadding = getOptionalIntegerV16(format, "encoder-padding");
    ArrayList<byte[]> initializationData = new ArrayList<>();
    for (int i = 0; format.containsKey("csd-" + i); i++) {
      ByteBuffer buffer = format.getByteBuffer("csd-" + i);
      byte[] data = new byte[buffer.limit()];
      buffer.get(data);
      initializationData.add(data);
      buffer.flip();
    }
    long durationUs = format.containsKey(android.media.MediaFormat.KEY_DURATION)
        ? format.getLong(android.media.MediaFormat.KEY_DURATION) : C.UNKNOWN_TIME_US;
    int pcmEncoding = MimeTypes.AUDIO_RAW.equals(mimeType) ? C.ENCODING_PCM_16BIT
        : MediaFormat.NO_VALUE;
    MediaFormat mediaFormat = new MediaFormat(null, mimeType, MediaFormat.NO_VALUE, maxInputSize,
        durationUs, width, height, rotationDegrees, MediaFormat.NO_VALUE, channelCount, sampleRate,
        language, MediaFormat.OFFSET_SAMPLE_RELATIVE, initializationData, false,
        MediaFormat.NO_VALUE, MediaFormat.NO_VALUE, pcmEncoding, encoderDelay, encoderPadding);
    mediaFormat.setFrameworkFormatV16(format);
    return mediaFormat;
  }

  @TargetApi(16)
  private static final String getOptionalStringV16(android.media.MediaFormat format, String key) {
    return format.containsKey(key) ? format.getString(key) : null;
  }

  @TargetApi(16)
  private static final int getOptionalIntegerV16(android.media.MediaFormat format, String key) {
    return format.containsKey(key) ? format.getInteger(key) : MediaFormat.NO_VALUE;
  }

}

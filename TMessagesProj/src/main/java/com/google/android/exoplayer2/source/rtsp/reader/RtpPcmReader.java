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
package com.google.android.exoplayer2.source.rtsp.reader;

import static com.google.android.exoplayer2.source.rtsp.reader.RtpReaderUtils.toSampleTimeUs;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.util.Log;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.rtsp.RtpPacket;
import com.google.android.exoplayer2.source.rtsp.RtpPayloadFormat;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Parses byte stream carried on RTP packets, and extracts PCM frames. Refer to RFC3551 for more
 * details.
 */
/* package */ public final class RtpPcmReader implements RtpPayloadReader {

  private static final String TAG = "RtpPcmReader";
  private final RtpPayloadFormat payloadFormat;

  private @MonotonicNonNull TrackOutput trackOutput;
  private long firstReceivedTimestamp;
  private long startTimeOffsetUs;
  private int previousSequenceNumber;

  public RtpPcmReader(RtpPayloadFormat payloadFormat) {
    this.payloadFormat = payloadFormat;
    firstReceivedTimestamp = C.TIME_UNSET;
    // Start time offset must be 0 before the first seek.
    startTimeOffsetUs = 0;
    previousSequenceNumber = C.INDEX_UNSET;
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, int trackId) {
    trackOutput = extractorOutput.track(trackId, C.TRACK_TYPE_AUDIO);
    trackOutput.format(payloadFormat.format);
  }

  @Override
  public void onReceivingFirstPacket(long timestamp, int sequenceNumber) {
    firstReceivedTimestamp = timestamp;
  }

  @Override
  public void consume(
      ParsableByteArray data, long timestamp, int sequenceNumber, boolean rtpMarker) {
    checkNotNull(trackOutput);
    if (previousSequenceNumber != C.INDEX_UNSET) {
      int expectedSequenceNumber = RtpPacket.getNextSequenceNumber(previousSequenceNumber);
      if (sequenceNumber != expectedSequenceNumber) {
        Log.w(
            TAG,
            Util.formatInvariant(
                "Received RTP packet with unexpected sequence number. Expected: %d; received: %d.",
                expectedSequenceNumber, sequenceNumber));
      }
    }

    long sampleTimeUs =
        toSampleTimeUs(
            startTimeOffsetUs, timestamp, firstReceivedTimestamp, payloadFormat.clockRate);
    int size = data.bytesLeft();
    trackOutput.sampleData(data, size);
    trackOutput.sampleMetadata(
        sampleTimeUs, C.BUFFER_FLAG_KEY_FRAME, size, /* offset= */ 0, /* cryptoData= */ null);

    previousSequenceNumber = sequenceNumber;
  }

  @Override
  public void seek(long nextRtpTimestamp, long timeUs) {
    // TODO(b/198620566) Rename firstReceivedTimestamp to timestampBase for all RtpPayloadReaders.
    firstReceivedTimestamp = nextRtpTimestamp;
    startTimeOffsetUs = timeUs;
  }
}

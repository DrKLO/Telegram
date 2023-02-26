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
package com.google.android.exoplayer2.extractor.avi;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;

/** Parses and holds information from the AVISTREAMHEADER structure. */
/* package */ final class AviStreamHeaderChunk implements AviChunk {
  private static final String TAG = "AviStreamHeaderChunk";

  public static AviStreamHeaderChunk parseFrom(ParsableByteArray body) {
    int streamType = body.readLittleEndianInt();
    body.skipBytes(12); // fccHandler (4 bytes), dwFlags (4 bytes), wPriority (2 bytes),
    // wLanguage (2 bytes).
    int initialFrames = body.readLittleEndianInt();
    int scale = body.readLittleEndianInt();
    int rate = body.readLittleEndianInt();
    body.skipBytes(4); // dwStart (4 bytes).
    int length = body.readLittleEndianInt();
    int suggestedBufferSize = body.readLittleEndianInt();
    body.skipBytes(8); // dwQuality (4 bytes), dwSampleSize (4 bytes).
    return new AviStreamHeaderChunk(
        streamType, initialFrames, scale, rate, length, suggestedBufferSize);
  }

  public final int streamType;
  public final int initialFrames;
  public final int scale;
  public final int rate;
  public final int length;
  public final int suggestedBufferSize;

  private AviStreamHeaderChunk(
      int streamType, int initialFrames, int scale, int rate, int length, int suggestedBufferSize) {
    this.streamType = streamType;
    this.initialFrames = initialFrames;
    this.scale = scale;
    this.rate = rate;
    this.length = length;
    this.suggestedBufferSize = suggestedBufferSize;
  }

  @Override
  public int getType() {
    return AviExtractor.FOURCC_strh;
  }

  public @C.TrackType int getTrackType() {
    switch (streamType) {
      case AviExtractor.FOURCC_auds:
        return C.TRACK_TYPE_AUDIO;
      case AviExtractor.FOURCC_vids:
        return C.TRACK_TYPE_VIDEO;
      case AviExtractor.FOURCC_txts:
        return C.TRACK_TYPE_TEXT;
      default:
        Log.w(TAG, "Found unsupported streamType fourCC: " + Integer.toHexString(streamType));
        return C.TRACK_TYPE_UNKNOWN;
    }
  }

  public float getFrameRate() {
    return rate / (float) scale;
  }

  public long getDurationUs() {
    return Util.scaleLargeTimestamp(
        /* timestamp= */ length,
        /* multiplier= */ C.MICROS_PER_SECOND * scale,
        /* divisor= */ rate);
  }
}

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

import com.google.android.exoplayer2.util.ParsableByteArray;

/** Wrapper around the AVIMAINHEADER structure */
/* package */ final class AviMainHeaderChunk implements AviChunk {

  private static final int AVIF_HAS_INDEX = 0x10;

  public static AviMainHeaderChunk parseFrom(ParsableByteArray body) {
    int microSecPerFrame = body.readLittleEndianInt();
    body.skipBytes(8); // Skip dwMaxBytesPerSec (4 bytes), dwPaddingGranularity (4 bytes).
    int flags = body.readLittleEndianInt();
    int totalFrames = body.readLittleEndianInt();
    body.skipBytes(4); // dwInitialFrames (4 bytes).
    int streams = body.readLittleEndianInt();
    body.skipBytes(12); // dwSuggestedBufferSize (4 bytes), dwWidth (4 bytes), dwHeight (4 bytes).
    return new AviMainHeaderChunk(microSecPerFrame, flags, totalFrames, streams);
  }

  public final int frameDurationUs;
  public final int flags;
  public final int totalFrames;
  public final int streams;

  private AviMainHeaderChunk(int frameDurationUs, int flags, int totalFrames, int streams) {
    this.frameDurationUs = frameDurationUs;
    this.flags = flags;
    this.totalFrames = totalFrames;
    this.streams = streams;
  }

  @Override
  public int getType() {
    return AviExtractor.FOURCC_avih;
  }

  public boolean hasIndex() {
    return (flags & AVIF_HAS_INDEX) == AVIF_HAS_INDEX;
  }
}

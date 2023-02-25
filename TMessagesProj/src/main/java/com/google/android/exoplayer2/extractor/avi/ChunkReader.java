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

import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.SeekPoint;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;

/** Reads chunks holding sample data. */
/* package */ final class ChunkReader {

  /** Parser states. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    CHUNK_TYPE_VIDEO_COMPRESSED,
    CHUNK_TYPE_VIDEO_UNCOMPRESSED,
    CHUNK_TYPE_AUDIO,
  })
  private @interface ChunkType {}

  private static final int INITIAL_INDEX_SIZE = 512;
  private static final int CHUNK_TYPE_VIDEO_COMPRESSED = ('d' << 16) | ('c' << 24);
  private static final int CHUNK_TYPE_VIDEO_UNCOMPRESSED = ('d' << 16) | ('b' << 24);
  private static final int CHUNK_TYPE_AUDIO = ('w' << 16) | ('b' << 24);

  protected final TrackOutput trackOutput;

  /** The chunk id fourCC (example: `01wb`), as defined in the index and the movi. */
  private final int chunkId;
  /** Secondary chunk id. Bad muxers sometimes use an uncompressed video id (db) for key frames */
  private final int alternativeChunkId;

  private final long durationUs;
  private final int streamHeaderChunkCount;

  private int currentChunkSize;
  private int bytesRemainingInCurrentChunk;

  /** Number of chunks as calculated by the index */
  private int currentChunkIndex;

  private int indexChunkCount;
  private int indexSize;
  private long[] keyFrameOffsets;
  private int[] keyFrameIndices;

  public ChunkReader(
      int id,
      @C.TrackType int trackType,
      long durationnUs,
      int streamHeaderChunkCount,
      TrackOutput trackOutput) {
    Assertions.checkArgument(trackType == C.TRACK_TYPE_AUDIO || trackType == C.TRACK_TYPE_VIDEO);
    this.durationUs = durationnUs;
    this.streamHeaderChunkCount = streamHeaderChunkCount;
    this.trackOutput = trackOutput;
    @ChunkType
    int chunkType =
        trackType == C.TRACK_TYPE_VIDEO ? CHUNK_TYPE_VIDEO_COMPRESSED : CHUNK_TYPE_AUDIO;
    chunkId = getChunkIdFourCc(id, chunkType);
    alternativeChunkId =
        trackType == C.TRACK_TYPE_VIDEO ? getChunkIdFourCc(id, CHUNK_TYPE_VIDEO_UNCOMPRESSED) : -1;
    keyFrameOffsets = new long[INITIAL_INDEX_SIZE];
    keyFrameIndices = new int[INITIAL_INDEX_SIZE];
  }

  public void appendKeyFrameToIndex(long offset) {
    if (indexSize == keyFrameIndices.length) {
      keyFrameOffsets = Arrays.copyOf(keyFrameOffsets, keyFrameOffsets.length * 3 / 2);
      keyFrameIndices = Arrays.copyOf(keyFrameIndices, keyFrameIndices.length * 3 / 2);
    }
    keyFrameOffsets[indexSize] = offset;
    keyFrameIndices[indexSize] = indexChunkCount;
    indexSize++;
  }

  public void advanceCurrentChunk() {
    currentChunkIndex++;
  }

  public long getCurrentChunkTimestampUs() {
    return getChunkTimestampUs(currentChunkIndex);
  }

  public long getFrameDurationUs() {
    return getChunkTimestampUs(/* chunkIndex= */ 1);
  }

  public void incrementIndexChunkCount() {
    indexChunkCount++;
  }

  public void compactIndex() {
    keyFrameOffsets = Arrays.copyOf(keyFrameOffsets, indexSize);
    keyFrameIndices = Arrays.copyOf(keyFrameIndices, indexSize);
  }

  public boolean handlesChunkId(int chunkId) {
    return this.chunkId == chunkId || alternativeChunkId == chunkId;
  }

  public boolean isCurrentFrameAKeyFrame() {
    return Arrays.binarySearch(keyFrameIndices, currentChunkIndex) >= 0;
  }

  public boolean isVideo() {
    return (chunkId & CHUNK_TYPE_VIDEO_COMPRESSED) == CHUNK_TYPE_VIDEO_COMPRESSED;
  }

  public boolean isAudio() {
    return (chunkId & CHUNK_TYPE_AUDIO) == CHUNK_TYPE_AUDIO;
  }

  /** Prepares for parsing a chunk with the given {@code size}. */
  public void onChunkStart(int size) {
    currentChunkSize = size;
    bytesRemainingInCurrentChunk = size;
  }

  /**
   * Provides data associated to the current chunk and returns whether the full chunk has been
   * parsed.
   */
  public boolean onChunkData(ExtractorInput input) throws IOException {
    bytesRemainingInCurrentChunk -=
        trackOutput.sampleData(input, bytesRemainingInCurrentChunk, false);
    boolean done = bytesRemainingInCurrentChunk == 0;
    if (done) {
      if (currentChunkSize > 0) {
        trackOutput.sampleMetadata(
            getCurrentChunkTimestampUs(),
            (isCurrentFrameAKeyFrame() ? C.BUFFER_FLAG_KEY_FRAME : 0),
            currentChunkSize,
            0,
            null);
      }
      advanceCurrentChunk();
    }
    return done;
  }

  public void seekToPosition(long position) {
    if (indexSize == 0) {
      currentChunkIndex = 0;
    } else {
      int index =
          Util.binarySearchFloor(
              keyFrameOffsets, position, /* inclusive= */ true, /* stayInBounds= */ true);
      currentChunkIndex = keyFrameIndices[index];
    }
  }

  public SeekMap.SeekPoints getSeekPoints(long timeUs) {
    int targetFrameIndex = (int) (timeUs / getFrameDurationUs());
    int keyFrameIndex =
        Util.binarySearchFloor(
            keyFrameIndices, targetFrameIndex, /* inclusive= */ true, /* stayInBounds= */ true);
    if (keyFrameIndices[keyFrameIndex] == targetFrameIndex) {
      return new SeekMap.SeekPoints(getSeekPoint(keyFrameIndex));
    }
    // The target frame is not a key frame, we look for the two closest ones.
    SeekPoint precedingKeyFrameSeekPoint = getSeekPoint(keyFrameIndex);
    if (keyFrameIndex + 1 < keyFrameOffsets.length) {
      return new SeekMap.SeekPoints(precedingKeyFrameSeekPoint, getSeekPoint(keyFrameIndex + 1));
    } else {
      return new SeekMap.SeekPoints(precedingKeyFrameSeekPoint);
    }
  }

  private long getChunkTimestampUs(int chunkIndex) {
    return durationUs * chunkIndex / streamHeaderChunkCount;
  }

  private SeekPoint getSeekPoint(int keyFrameIndex) {
    return new SeekPoint(
        keyFrameIndices[keyFrameIndex] * getFrameDurationUs(), keyFrameOffsets[keyFrameIndex]);
  }

  private static int getChunkIdFourCc(int streamId, @ChunkType int chunkType) {
    int tens = streamId / 10;
    int ones = streamId % 10;
    return (('0' + ones) << 8) | ('0' + tens) | chunkType;
  }
}

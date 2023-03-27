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
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.DummyExtractorOutput;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Extracts data from the AVI container format.
 *
 * <p>Spec: https://docs.microsoft.com/en-us/windows/win32/directshow/avi-riff-file-reference.
 */
public final class AviExtractor implements Extractor {

  private static final String TAG = "AviExtractor";

  public static final int FOURCC_RIFF = 0x46464952;
  public static final int FOURCC_AVI_ = 0x20495641; // AVI<space>
  public static final int FOURCC_LIST = 0x5453494c;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int FOURCC_avih = 0x68697661;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int FOURCC_hdrl = 0x6c726468;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int FOURCC_strl = 0x6c727473;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int FOURCC_movi = 0x69766f6d;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int FOURCC_idx1 = 0x31786469;

  public static final int FOURCC_JUNK = 0x4b4e554a;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int FOURCC_strf = 0x66727473;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int FOURCC_strn = 0x6e727473;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int FOURCC_strh = 0x68727473;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int FOURCC_auds = 0x73647561;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int FOURCC_txts = 0x73747874;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int FOURCC_vids = 0x73646976;

  /** Parser states. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    STATE_SKIPPING_TO_HDRL,
    STATE_READING_HDRL_HEADER,
    STATE_READING_HDRL_BODY,
    STATE_FINDING_MOVI_HEADER,
    STATE_FINDING_IDX1_HEADER,
    STATE_READING_IDX1_BODY,
    STATE_READING_SAMPLES,
  })
  private @interface State {}

  private static final int STATE_SKIPPING_TO_HDRL = 0;
  private static final int STATE_READING_HDRL_HEADER = 1;
  private static final int STATE_READING_HDRL_BODY = 2;
  private static final int STATE_FINDING_MOVI_HEADER = 3;
  private static final int STATE_FINDING_IDX1_HEADER = 4;
  private static final int STATE_READING_IDX1_BODY = 5;
  private static final int STATE_READING_SAMPLES = 6;

  private static final int AVIIF_KEYFRAME = 16;

  /**
   * Maximum size to skip using {@link ExtractorInput#skip}. Boxes larger than this size are skipped
   * using {@link #RESULT_SEEK}.
   */
  private static final long RELOAD_MINIMUM_SEEK_DISTANCE = 256 * 1024;

  private final ParsableByteArray scratch;
  private final ChunkHeaderHolder chunkHeaderHolder;

  private @State int state;
  private ExtractorOutput extractorOutput;
  private @MonotonicNonNull AviMainHeaderChunk aviHeader;
  private long durationUs;
  private ChunkReader[] chunkReaders;

  private long pendingReposition;
  @Nullable private ChunkReader currentChunkReader;
  private int hdrlSize;
  private long moviStart;
  private long moviEnd;
  private int idx1BodySize;
  private boolean seekMapHasBeenOutput;

  public AviExtractor() {
    scratch = new ParsableByteArray(/* limit= */ 12);
    chunkHeaderHolder = new ChunkHeaderHolder();
    extractorOutput = new DummyExtractorOutput();
    chunkReaders = new ChunkReader[0];
    moviStart = C.POSITION_UNSET;
    moviEnd = C.POSITION_UNSET;
    hdrlSize = C.LENGTH_UNSET;
    durationUs = C.TIME_UNSET;
  }

  // Extractor implementation.

  @Override
  public void init(ExtractorOutput output) {
    this.state = STATE_SKIPPING_TO_HDRL;
    this.extractorOutput = output;
    pendingReposition = C.POSITION_UNSET;
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    input.peekFully(scratch.getData(), /* offset= */ 0, /* length= */ 12);
    scratch.setPosition(0);
    if (scratch.readLittleEndianInt() != FOURCC_RIFF) {
      return false;
    }
    scratch.skipBytes(4); // Skip the RIFF chunk length.
    return scratch.readLittleEndianInt() == FOURCC_AVI_;
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    if (resolvePendingReposition(input, seekPosition)) {
      return RESULT_SEEK;
    }
    switch (state) {
      case STATE_SKIPPING_TO_HDRL:
        // Check for RIFF and AVI fourcc's just in case the caller did not sniff, in order to
        // provide a meaningful error if the input is not an AVI file.
        if (sniff(input)) {
          input.skipFully(/* length= */ 12);
        } else {
          throw ParserException.createForMalformedContainer(
              /* message= */ "AVI Header List not found", /* cause= */ null);
        }
        state = STATE_READING_HDRL_HEADER;
        return RESULT_CONTINUE;
      case STATE_READING_HDRL_HEADER:
        input.readFully(scratch.getData(), /* offset= */ 0, /* length= */ 12);
        scratch.setPosition(0);
        chunkHeaderHolder.populateWithListHeaderFrom(scratch);
        if (chunkHeaderHolder.listType != FOURCC_hdrl) {
          throw ParserException.createForMalformedContainer(
              /* message= */ "hdrl expected, found: " + chunkHeaderHolder.listType,
              /* cause= */ null);
        }
        hdrlSize = chunkHeaderHolder.size;
        state = STATE_READING_HDRL_BODY;
        return RESULT_CONTINUE;
      case STATE_READING_HDRL_BODY:
        // hdrlSize includes the LIST type (hdrl), so we subtract 4 to the size.
        int bytesToRead = hdrlSize - 4;
        ParsableByteArray hdrlBody = new ParsableByteArray(bytesToRead);
        input.readFully(hdrlBody.getData(), /* offset= */ 0, bytesToRead);
        parseHdrlBody(hdrlBody);
        state = STATE_FINDING_MOVI_HEADER;
        return RESULT_CONTINUE;
      case STATE_FINDING_MOVI_HEADER:
        if (moviStart != C.POSITION_UNSET && input.getPosition() != moviStart) {
          pendingReposition = moviStart;
          return RESULT_CONTINUE;
        }
        input.peekFully(scratch.getData(), /* offset= */ 0, /* length= */ 12);
        input.resetPeekPosition();
        scratch.setPosition(0);
        chunkHeaderHolder.populateFrom(scratch);
        int listType = scratch.readLittleEndianInt();
        if (chunkHeaderHolder.chunkType == FOURCC_RIFF) {
          // We are at the start of the file. The movi chunk is in the RIFF chunk, so we skip the
          // header, so as to read the RIFF chunk's body.
          input.skipFully(12);
          return RESULT_CONTINUE;
        }
        if (chunkHeaderHolder.chunkType != FOURCC_LIST || listType != FOURCC_movi) {
          // The chunk header (8 bytes) plus the whole body.
          pendingReposition = input.getPosition() + chunkHeaderHolder.size + 8;
          return RESULT_CONTINUE;
        }
        moviStart = input.getPosition();
        // Size includes the list type, but not the LIST or size fields, so we add 8.
        moviEnd = moviStart + chunkHeaderHolder.size + 8;
        if (!seekMapHasBeenOutput) {
          if (Assertions.checkNotNull(aviHeader).hasIndex()) {
            state = STATE_FINDING_IDX1_HEADER;
            pendingReposition = moviEnd;
            return RESULT_CONTINUE;
          } else {
            extractorOutput.seekMap(new SeekMap.Unseekable(durationUs));
            seekMapHasBeenOutput = true;
          }
        }
        // No need to parse the idx1, so we start reading the samples from the movi chunk straight
        // away. We skip 12 bytes to move to the start of the movi's body.
        pendingReposition = input.getPosition() + 12;
        state = STATE_READING_SAMPLES;
        return RESULT_CONTINUE;
      case STATE_FINDING_IDX1_HEADER:
        input.readFully(scratch.getData(), /* offset= */ 0, /* length= */ 8);
        scratch.setPosition(0);
        int idx1Fourcc = scratch.readLittleEndianInt();
        int boxSize = scratch.readLittleEndianInt();
        if (idx1Fourcc == FOURCC_idx1) {
          state = STATE_READING_IDX1_BODY;
          idx1BodySize = boxSize;
        } else {
          // This one is not idx1, skip to the next box.
          pendingReposition = input.getPosition() + boxSize;
        }
        return RESULT_CONTINUE;
      case STATE_READING_IDX1_BODY:
        ParsableByteArray idx1Body = new ParsableByteArray(idx1BodySize);
        input.readFully(idx1Body.getData(), /* offset= */ 0, /* length= */ idx1BodySize);
        parseIdx1Body(idx1Body);
        state = STATE_READING_SAMPLES;
        pendingReposition = moviStart;
        return RESULT_CONTINUE;
      case STATE_READING_SAMPLES:
        return readMoviChunks(input);
      default:
        throw new AssertionError(); // Should never happen.
    }
  }

  @Override
  public void seek(long position, long timeUs) {
    pendingReposition = C.POSITION_UNSET;
    currentChunkReader = null;
    for (ChunkReader chunkReader : chunkReaders) {
      chunkReader.seekToPosition(position);
    }
    if (position == 0) {
      if (chunkReaders.length == 0) {
        // Still unprepared.
        state = STATE_SKIPPING_TO_HDRL;
      } else {
        state = STATE_FINDING_MOVI_HEADER;
      }
      return;
    }
    state = STATE_READING_SAMPLES;
  }

  @Override
  public void release() {
    // Nothing to release.
  }

  // Internal methods.

  /**
   * Returns whether a {@link #RESULT_SEEK} is required for the pending reposition. A seek may not
   * be necessary when the desired position (as held by {@link #pendingReposition}) is after the
   * {@link ExtractorInput#getPosition() current position}, but not further than {@link
   * #RELOAD_MINIMUM_SEEK_DISTANCE}.
   */
  private boolean resolvePendingReposition(ExtractorInput input, PositionHolder seekPosition)
      throws IOException {
    boolean needSeek = false;
    if (pendingReposition != C.POSITION_UNSET) {
      long currentPosition = input.getPosition();
      if (pendingReposition < currentPosition
          || pendingReposition > currentPosition + RELOAD_MINIMUM_SEEK_DISTANCE) {
        seekPosition.position = pendingReposition;
        needSeek = true;
      } else {
        // The distance to the target position is short enough that it makes sense to just skip the
        // bytes, instead of doing a seek which might re-create an HTTP connection.
        input.skipFully((int) (pendingReposition - currentPosition));
      }
    }
    pendingReposition = C.POSITION_UNSET;
    return needSeek;
  }

  private void parseHdrlBody(ParsableByteArray hrdlBody) throws IOException {
    ListChunk headerList = ListChunk.parseFrom(FOURCC_hdrl, hrdlBody);
    if (headerList.getType() != FOURCC_hdrl) {
      throw ParserException.createForMalformedContainer(
          /* message= */ "Unexpected header list type " + headerList.getType(), /* cause= */ null);
    }
    @Nullable AviMainHeaderChunk aviHeader = headerList.getChild(AviMainHeaderChunk.class);
    if (aviHeader == null) {
      throw ParserException.createForMalformedContainer(
          /* message= */ "AviHeader not found", /* cause= */ null);
    }
    this.aviHeader = aviHeader;
    // This is usually wrong, so it will be overwritten by video if present
    durationUs = aviHeader.totalFrames * (long) aviHeader.frameDurationUs;
    ArrayList<ChunkReader> chunkReaderList = new ArrayList<>();
    int streamId = 0;
    for (AviChunk aviChunk : headerList.children) {
      if (aviChunk.getType() == FOURCC_strl) {
        ListChunk streamList = (ListChunk) aviChunk;
        // Note the streamId needs to increment even if the corresponding `strl` is discarded.
        // See
        // https://docs.microsoft.com/en-us/windows/win32/directshow/avi-riff-file-reference#avi-stream-headers.
        @Nullable ChunkReader chunkReader = processStreamList(streamList, streamId++);
        if (chunkReader != null) {
          chunkReaderList.add(chunkReader);
        }
      }
    }
    chunkReaders = chunkReaderList.toArray(new ChunkReader[0]);
    extractorOutput.endTracks();
  }

  /** Builds and outputs the {@link SeekMap} from the idx1 chunk. */
  private void parseIdx1Body(ParsableByteArray body) {
    long seekOffset = peekSeekOffset(body);
    while (body.bytesLeft() >= 16) {
      int chunkId = body.readLittleEndianInt();
      int flags = body.readLittleEndianInt();
      long offset = body.readLittleEndianInt() + seekOffset;
      body.readLittleEndianInt(); // We ignore the size.
      ChunkReader chunkReader = getChunkReader(chunkId);
      if (chunkReader == null) {
        // We ignore unknown chunk IDs.
        continue;
      }
      if ((flags & AVIIF_KEYFRAME) == AVIIF_KEYFRAME) {
        chunkReader.appendKeyFrameToIndex(offset);
      }
      chunkReader.incrementIndexChunkCount();
    }
    for (ChunkReader chunkReader : chunkReaders) {
      chunkReader.compactIndex();
    }
    seekMapHasBeenOutput = true;
    extractorOutput.seekMap(new AviSeekMap(durationUs));
  }

  private long peekSeekOffset(ParsableByteArray idx1Body) {
    // The spec states the offset is based on the start of the movi list type fourcc, but it also
    // says some files base the offset on the start of the file. We use a best effort approach to
    // figure out which is the case. See:
    // https://docs.microsoft.com/en-us/previous-versions/windows/desktop/api/Aviriff/ns-aviriff-avioldindex#dwoffset.
    if (idx1Body.bytesLeft() < 16) {
      // There are no full entries in the index, meaning we don't need to apply an offset.
      return 0;
    }
    int startingPosition = idx1Body.getPosition();
    idx1Body.skipBytes(8); // Skip chunkId (4 bytes) and flags (4 bytes).
    int offset = idx1Body.readLittleEndianInt();

    // moviStart poitns at the start of the LIST, while the seek offset is based at the start of the
    // movi fourCC, so we add 8 to reconcile the difference.
    long seekOffset = offset > moviStart ? 0L : moviStart + 8;
    idx1Body.setPosition(startingPosition);
    return seekOffset;
  }

  @Nullable
  private ChunkReader getChunkReader(int chunkId) {
    for (ChunkReader chunkReader : chunkReaders) {
      if (chunkReader.handlesChunkId(chunkId)) {
        return chunkReader;
      }
    }
    return null;
  }

  private int readMoviChunks(ExtractorInput input) throws IOException {
    if (input.getPosition() >= moviEnd) {
      return C.RESULT_END_OF_INPUT;
    } else if (currentChunkReader != null) {
      if (currentChunkReader.onChunkData(input)) {
        currentChunkReader = null;
      }
    } else {
      alignInputToEvenPosition(input);
      input.peekFully(scratch.getData(), /* offset= */ 0, 12);
      scratch.setPosition(0);
      int chunkType = scratch.readLittleEndianInt();
      if (chunkType == FOURCC_LIST) {
        scratch.setPosition(8);
        int listType = scratch.readLittleEndianInt();
        input.skipFully(listType == FOURCC_movi ? 12 : 8);
        input.resetPeekPosition();
        return RESULT_CONTINUE;
      }
      int size = scratch.readLittleEndianInt();
      if (chunkType == FOURCC_JUNK) {
        pendingReposition = input.getPosition() + size + 8;
        return RESULT_CONTINUE;
      }
      input.skipFully(8);
      input.resetPeekPosition();
      ChunkReader chunkReader = getChunkReader(chunkType);
      if (chunkReader == null) {
        // No handler for this chunk. We skip it.
        pendingReposition = input.getPosition() + size;
        return RESULT_CONTINUE;
      } else {
        chunkReader.onChunkStart(size);
        this.currentChunkReader = chunkReader;
      }
    }
    return RESULT_CONTINUE;
  }

  @Nullable
  private ChunkReader processStreamList(ListChunk streamList, int streamId) {
    AviStreamHeaderChunk aviStreamHeaderChunk = streamList.getChild(AviStreamHeaderChunk.class);
    StreamFormatChunk streamFormatChunk = streamList.getChild(StreamFormatChunk.class);
    if (aviStreamHeaderChunk == null) {
      Log.w(TAG, "Missing Stream Header");
      return null;
    }
    if (streamFormatChunk == null) {
      Log.w(TAG, "Missing Stream Format");
      return null;
    }
    long durationUs = aviStreamHeaderChunk.getDurationUs();
    Format streamFormat = streamFormatChunk.format;
    Format.Builder builder = streamFormat.buildUpon();
    builder.setId(streamId);
    int suggestedBufferSize = aviStreamHeaderChunk.suggestedBufferSize;
    if (suggestedBufferSize != 0) {
      builder.setMaxInputSize(suggestedBufferSize);
    }
    StreamNameChunk streamName = streamList.getChild(StreamNameChunk.class);
    if (streamName != null) {
      builder.setLabel(streamName.name);
    }
    int trackType = MimeTypes.getTrackType(streamFormat.sampleMimeType);
    if (trackType == C.TRACK_TYPE_AUDIO || trackType == C.TRACK_TYPE_VIDEO) {
      TrackOutput trackOutput = extractorOutput.track(streamId, trackType);
      trackOutput.format(builder.build());
      ChunkReader chunkReader =
          new ChunkReader(
              streamId, trackType, durationUs, aviStreamHeaderChunk.length, trackOutput);
      this.durationUs = durationUs;
      return chunkReader;
    } else {
      // We don't currently support tracks other than video and audio.
      return null;
    }
  }

  /**
   * Skips one byte from the given {@code input} if the current position is odd.
   *
   * <p>This isn't documented anywhere, but AVI files are aligned to even bytes and fill gaps with
   * zeros.
   */
  private static void alignInputToEvenPosition(ExtractorInput input) throws IOException {
    if ((input.getPosition() & 1) == 1) {
      input.skipFully(1);
    }
  }

  // Internal classes.

  private class AviSeekMap implements SeekMap {

    private final long durationUs;

    public AviSeekMap(long durationUs) {
      this.durationUs = durationUs;
    }

    @Override
    public boolean isSeekable() {
      return true;
    }

    @Override
    public long getDurationUs() {
      return durationUs;
    }

    @Override
    public SeekPoints getSeekPoints(long timeUs) {
      SeekPoints result = chunkReaders[0].getSeekPoints(timeUs);
      for (int i = 1; i < chunkReaders.length; i++) {
        SeekPoints seekPoints = chunkReaders[i].getSeekPoints(timeUs);
        if (seekPoints.first.position < result.first.position) {
          result = seekPoints;
        }
      }
      return result;
    }
  }

  private static class ChunkHeaderHolder {
    public int chunkType;
    public int size;
    public int listType;

    public void populateWithListHeaderFrom(ParsableByteArray headerBytes) throws ParserException {
      populateFrom(headerBytes);
      if (chunkType != AviExtractor.FOURCC_LIST) {
        throw ParserException.createForMalformedContainer(
            /* message= */ "LIST expected, found: " + chunkType, /* cause= */ null);
      }
      listType = headerBytes.readLittleEndianInt();
    }

    public void populateFrom(ParsableByteArray headerBytes) {
      chunkType = headerBytes.readLittleEndianInt();
      size = headerBytes.readLittleEndianInt();
      listType = 0;
    }
  }
}

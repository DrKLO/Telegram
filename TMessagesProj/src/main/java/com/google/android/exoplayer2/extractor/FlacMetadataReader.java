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
package com.google.android.exoplayer2.extractor;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.VorbisUtil.CommentHeader;
import com.google.android.exoplayer2.extractor.flac.FlacConstants;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.flac.PictureFrame;
import com.google.android.exoplayer2.metadata.id3.Id3Decoder;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Reads and peeks FLAC stream metadata elements according to the <a
 * href="https://xiph.org/flac/format.html">FLAC format specification</a>.
 */
public final class FlacMetadataReader {

  /** Holds a {@link FlacStreamMetadata}. */
  public static final class FlacStreamMetadataHolder {
    /** The FLAC stream metadata. */
    @Nullable public FlacStreamMetadata flacStreamMetadata;

    public FlacStreamMetadataHolder(@Nullable FlacStreamMetadata flacStreamMetadata) {
      this.flacStreamMetadata = flacStreamMetadata;
    }
  }

  private static final int STREAM_MARKER = 0x664C6143; // ASCII for "fLaC"
  private static final int SYNC_CODE = 0x3FFE;
  private static final int SEEK_POINT_SIZE = 18;

  /**
   * Peeks ID3 Data.
   *
   * @param input Input stream to peek the ID3 data from.
   * @param parseData Whether to parse the ID3 frames.
   * @return The parsed ID3 data, or {@code null} if there is no such data or if {@code parseData}
   *     is {@code false}.
   * @throws IOException If peeking from the input fails. In this case, there is no guarantee on the
   *     peek position.
   */
  @Nullable
  public static Metadata peekId3Metadata(ExtractorInput input, boolean parseData)
      throws IOException {
    @Nullable
    Id3Decoder.FramePredicate id3FramePredicate = parseData ? null : Id3Decoder.NO_FRAMES_PREDICATE;
    @Nullable Metadata id3Metadata = new Id3Peeker().peekId3Data(input, id3FramePredicate);
    return id3Metadata == null || id3Metadata.length() == 0 ? null : id3Metadata;
  }

  /**
   * Peeks the FLAC stream marker.
   *
   * @param input Input stream to peek the stream marker from.
   * @return Whether the data peeked is the FLAC stream marker.
   * @throws IOException If peeking from the input fails. In this case, the peek position is left
   *     unchanged.
   */
  public static boolean checkAndPeekStreamMarker(ExtractorInput input) throws IOException {
    ParsableByteArray scratch = new ParsableByteArray(FlacConstants.STREAM_MARKER_SIZE);
    input.peekFully(scratch.getData(), 0, FlacConstants.STREAM_MARKER_SIZE);
    return scratch.readUnsignedInt() == STREAM_MARKER;
  }

  /**
   * Reads ID3 Data.
   *
   * <p>If no exception is thrown, the peek position of {@code input} is aligned with the read
   * position.
   *
   * @param input Input stream to read the ID3 data from.
   * @param parseData Whether to parse the ID3 frames.
   * @return The parsed ID3 data, or {@code null} if there is no such data or if {@code parseData}
   *     is {@code false}.
   * @throws IOException If reading from the input fails. In this case, the read position is left
   *     unchanged and there is no guarantee on the peek position.
   */
  @Nullable
  public static Metadata readId3Metadata(ExtractorInput input, boolean parseData)
      throws IOException {
    input.resetPeekPosition();
    long startingPeekPosition = input.getPeekPosition();
    @Nullable Metadata id3Metadata = peekId3Metadata(input, parseData);
    int peekedId3Bytes = (int) (input.getPeekPosition() - startingPeekPosition);
    input.skipFully(peekedId3Bytes);
    return id3Metadata;
  }

  /**
   * Reads the FLAC stream marker.
   *
   * @param input Input stream to read the stream marker from.
   * @throws ParserException If an error occurs parsing the stream marker. In this case, the
   *     position of {@code input} is advanced by {@link FlacConstants#STREAM_MARKER_SIZE} bytes.
   * @throws IOException If reading from the input fails. In this case, the position is left
   *     unchanged.
   */
  public static void readStreamMarker(ExtractorInput input) throws IOException {
    ParsableByteArray scratch = new ParsableByteArray(FlacConstants.STREAM_MARKER_SIZE);
    input.readFully(scratch.getData(), 0, FlacConstants.STREAM_MARKER_SIZE);
    if (scratch.readUnsignedInt() != STREAM_MARKER) {
      throw ParserException.createForMalformedContainer(
          "Failed to read FLAC stream marker.", /* cause= */ null);
    }
  }

  /**
   * Reads one FLAC metadata block.
   *
   * <p>If no exception is thrown, the peek position of {@code input} is aligned with the read
   * position.
   *
   * @param input Input stream to read the metadata block from (header included).
   * @param metadataHolder A holder for the metadata read. If the stream info block (which must be
   *     the first metadata block) is read, the holder contains a new instance representing the
   *     stream info data. If the block read is a Vorbis comment block or a picture block, the
   *     holder contains a copy of the existing stream metadata with the corresponding metadata
   *     added. Otherwise, the metadata in the holder is unchanged.
   * @return Whether the block read is the last metadata block.
   * @throws IllegalArgumentException If the block read is not a stream info block and the metadata
   *     in {@code metadataHolder} is {@code null}. In this case, the read position will be at the
   *     start of a metadata block and there is no guarantee on the peek position.
   * @throws IOException If reading from the input fails. In this case, the read position will be at
   *     the start of a metadata block and there is no guarantee on the peek position.
   */
  public static boolean readMetadataBlock(
      ExtractorInput input, FlacStreamMetadataHolder metadataHolder) throws IOException {
    input.resetPeekPosition();
    ParsableBitArray scratch = new ParsableBitArray(new byte[4]);
    input.peekFully(scratch.data, 0, FlacConstants.METADATA_BLOCK_HEADER_SIZE);

    boolean isLastMetadataBlock = scratch.readBit();
    int type = scratch.readBits(7);
    int length = FlacConstants.METADATA_BLOCK_HEADER_SIZE + scratch.readBits(24);
    if (type == FlacConstants.METADATA_TYPE_STREAM_INFO) {
      metadataHolder.flacStreamMetadata = readStreamInfoBlock(input);
    } else {
      @Nullable FlacStreamMetadata flacStreamMetadata = metadataHolder.flacStreamMetadata;
      if (flacStreamMetadata == null) {
        throw new IllegalArgumentException();
      }
      if (type == FlacConstants.METADATA_TYPE_SEEK_TABLE) {
        FlacStreamMetadata.SeekTable seekTable = readSeekTableMetadataBlock(input, length);
        metadataHolder.flacStreamMetadata = flacStreamMetadata.copyWithSeekTable(seekTable);
      } else if (type == FlacConstants.METADATA_TYPE_VORBIS_COMMENT) {
        List<String> vorbisComments = readVorbisCommentMetadataBlock(input, length);
        metadataHolder.flacStreamMetadata =
            flacStreamMetadata.copyWithVorbisComments(vorbisComments);
      } else if (type == FlacConstants.METADATA_TYPE_PICTURE) {
        ParsableByteArray pictureBlock = new ParsableByteArray(length);
        input.readFully(pictureBlock.getData(), 0, length);
        pictureBlock.skipBytes(FlacConstants.METADATA_BLOCK_HEADER_SIZE);
        PictureFrame pictureFrame = PictureFrame.fromPictureBlock(pictureBlock);
        metadataHolder.flacStreamMetadata =
            flacStreamMetadata.copyWithPictureFrames(ImmutableList.of(pictureFrame));
      } else {
        input.skipFully(length);
      }
    }

    return isLastMetadataBlock;
  }

  /**
   * Reads a FLAC seek table metadata block.
   *
   * <p>The position of {@code data} is moved to the byte following the seek table metadata block
   * (placeholder points included).
   *
   * @param data The array to read the data from, whose position must correspond to the seek table
   *     metadata block (header included).
   * @return The seek table, without the placeholder points.
   */
  public static FlacStreamMetadata.SeekTable readSeekTableMetadataBlock(ParsableByteArray data) {
    data.skipBytes(1);
    int length = data.readUnsignedInt24();

    long seekTableEndPosition = (long) data.getPosition() + length;
    int seekPointCount = length / SEEK_POINT_SIZE;
    long[] pointSampleNumbers = new long[seekPointCount];
    long[] pointOffsets = new long[seekPointCount];
    for (int i = 0; i < seekPointCount; i++) {
      // The sample number is expected to fit in a signed long, except if it is a placeholder, in
      // which case its value is -1.
      long sampleNumber = data.readLong();
      if (sampleNumber == -1) {
        pointSampleNumbers = Arrays.copyOf(pointSampleNumbers, i);
        pointOffsets = Arrays.copyOf(pointOffsets, i);
        break;
      }
      pointSampleNumbers[i] = sampleNumber;
      pointOffsets[i] = data.readLong();
      data.skipBytes(2);
    }

    data.skipBytes((int) (seekTableEndPosition - data.getPosition()));
    return new FlacStreamMetadata.SeekTable(pointSampleNumbers, pointOffsets);
  }

  /**
   * Returns the frame start marker, consisting of the 2 first bytes of the first frame.
   *
   * <p>The read position of {@code input} is left unchanged and the peek position is aligned with
   * the read position.
   *
   * @param input Input stream to get the start marker from (starting from the read position).
   * @return The frame start marker (which must be the same for all the frames in the stream).
   * @throws ParserException If an error occurs parsing the frame start marker.
   * @throws IOException If peeking from the input fails.
   */
  public static int getFrameStartMarker(ExtractorInput input) throws IOException {
    input.resetPeekPosition();
    ParsableByteArray scratch = new ParsableByteArray(2);
    input.peekFully(scratch.getData(), 0, 2);

    int frameStartMarker = scratch.readUnsignedShort();
    int syncCode = frameStartMarker >> 2;
    if (syncCode != SYNC_CODE) {
      input.resetPeekPosition();
      throw ParserException.createForMalformedContainer(
          "First frame does not start with sync code.", /* cause= */ null);
    }

    input.resetPeekPosition();
    return frameStartMarker;
  }

  private static FlacStreamMetadata readStreamInfoBlock(ExtractorInput input) throws IOException {
    byte[] scratchData = new byte[FlacConstants.STREAM_INFO_BLOCK_SIZE];
    input.readFully(scratchData, 0, FlacConstants.STREAM_INFO_BLOCK_SIZE);
    return new FlacStreamMetadata(
        scratchData, /* offset= */ FlacConstants.METADATA_BLOCK_HEADER_SIZE);
  }

  private static FlacStreamMetadata.SeekTable readSeekTableMetadataBlock(
      ExtractorInput input, int length) throws IOException {
    ParsableByteArray scratch = new ParsableByteArray(length);
    input.readFully(scratch.getData(), 0, length);
    return readSeekTableMetadataBlock(scratch);
  }

  private static List<String> readVorbisCommentMetadataBlock(ExtractorInput input, int length)
      throws IOException {
    ParsableByteArray scratch = new ParsableByteArray(length);
    input.readFully(scratch.getData(), 0, length);
    scratch.skipBytes(FlacConstants.METADATA_BLOCK_HEADER_SIZE);
    CommentHeader commentHeader =
        VorbisUtil.readVorbisCommentHeader(
            scratch, /* hasMetadataHeader= */ false, /* hasFramingBit= */ false);
    return Arrays.asList(commentHeader.comments);
  }

  private FlacMetadataReader() {}
}

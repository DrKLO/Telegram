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
package com.google.android.exoplayer2.util;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.flac.PictureFrame;
import com.google.android.exoplayer2.metadata.flac.VorbisComment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holder for FLAC metadata.
 *
 * @see <a href="https://xiph.org/flac/format.html#metadata_block_streaminfo">FLAC format
 *     METADATA_BLOCK_STREAMINFO</a>
 * @see <a href="https://xiph.org/flac/format.html#metadata_block_seektable">FLAC format
 *     METADATA_BLOCK_SEEKTABLE</a>
 * @see <a href="https://xiph.org/flac/format.html#metadata_block_vorbis_comment">FLAC format
 *     METADATA_BLOCK_VORBIS_COMMENT</a>
 * @see <a href="https://xiph.org/flac/format.html#metadata_block_picture">FLAC format
 *     METADATA_BLOCK_PICTURE</a>
 */
public final class FlacStreamMetadata {

  /** A FLAC seek table. */
  public static class SeekTable {
    /** Seek points sample numbers. */
    public final long[] pointSampleNumbers;
    /** Seek points byte offsets from the first frame. */
    public final long[] pointOffsets;

    public SeekTable(long[] pointSampleNumbers, long[] pointOffsets) {
      this.pointSampleNumbers = pointSampleNumbers;
      this.pointOffsets = pointOffsets;
    }
  }

  private static final String TAG = "FlacStreamMetadata";

  /** Indicates that a value is not in the corresponding lookup table. */
  public static final int NOT_IN_LOOKUP_TABLE = -1;
  /** Separator between the field name of a Vorbis comment and the corresponding value. */
  private static final String SEPARATOR = "=";

  /** Minimum number of samples per block. */
  public final int minBlockSizeSamples;
  /** Maximum number of samples per block. */
  public final int maxBlockSizeSamples;
  /** Minimum frame size in bytes, or 0 if the value is unknown. */
  public final int minFrameSize;
  /** Maximum frame size in bytes, or 0 if the value is unknown. */
  public final int maxFrameSize;
  /** Sample rate in Hertz. */
  public final int sampleRate;
  /**
   * Lookup key corresponding to the stream sample rate, or {@link #NOT_IN_LOOKUP_TABLE} if it is
   * not in the lookup table.
   *
   * <p>This key is used to indicate the sample rate in the frame header for the most common values.
   *
   * <p>The sample rate lookup table is described in https://xiph.org/flac/format.html#frame_header.
   */
  public final int sampleRateLookupKey;
  /** Number of audio channels. */
  public final int channels;
  /** Number of bits per sample. */
  public final int bitsPerSample;
  /**
   * Lookup key corresponding to the number of bits per sample of the stream, or {@link
   * #NOT_IN_LOOKUP_TABLE} if it is not in the lookup table.
   *
   * <p>This key is used to indicate the number of bits per sample in the frame header for the most
   * common values.
   *
   * <p>The sample size lookup table is described in https://xiph.org/flac/format.html#frame_header.
   */
  public final int bitsPerSampleLookupKey;
  /** Total number of samples, or 0 if the value is unknown. */
  public final long totalSamples;
  /** Seek table, or {@code null} if it is not provided. */
  @Nullable public final SeekTable seekTable;
  /** Content metadata, or {@code null} if it is not provided. */
  @Nullable private final Metadata metadata;

  /**
   * Parses binary FLAC stream info metadata.
   *
   * @param data An array containing binary FLAC stream info block.
   * @param offset The offset of the stream info block in {@code data}, excluding the header (i.e.
   *     the offset points to the first byte of the minimum block size).
   */
  public FlacStreamMetadata(byte[] data, int offset) {
    ParsableBitArray scratch = new ParsableBitArray(data);
    scratch.setPosition(offset * 8);
    minBlockSizeSamples = scratch.readBits(16);
    maxBlockSizeSamples = scratch.readBits(16);
    minFrameSize = scratch.readBits(24);
    maxFrameSize = scratch.readBits(24);
    sampleRate = scratch.readBits(20);
    sampleRateLookupKey = getSampleRateLookupKey(sampleRate);
    channels = scratch.readBits(3) + 1;
    bitsPerSample = scratch.readBits(5) + 1;
    bitsPerSampleLookupKey = getBitsPerSampleLookupKey(bitsPerSample);
    totalSamples = scratch.readBitsToLong(36);
    seekTable = null;
    metadata = null;
  }

  // Used in native code.
  public FlacStreamMetadata(
      int minBlockSizeSamples,
      int maxBlockSizeSamples,
      int minFrameSize,
      int maxFrameSize,
      int sampleRate,
      int channels,
      int bitsPerSample,
      long totalSamples,
      ArrayList<String> vorbisComments,
      ArrayList<PictureFrame> pictureFrames) {
    this(
        minBlockSizeSamples,
        maxBlockSizeSamples,
        minFrameSize,
        maxFrameSize,
        sampleRate,
        channels,
        bitsPerSample,
        totalSamples,
        /* seekTable= */ null,
        buildMetadata(vorbisComments, pictureFrames));
  }

  private FlacStreamMetadata(
      int minBlockSizeSamples,
      int maxBlockSizeSamples,
      int minFrameSize,
      int maxFrameSize,
      int sampleRate,
      int channels,
      int bitsPerSample,
      long totalSamples,
      @Nullable SeekTable seekTable,
      @Nullable Metadata metadata) {
    this.minBlockSizeSamples = minBlockSizeSamples;
    this.maxBlockSizeSamples = maxBlockSizeSamples;
    this.minFrameSize = minFrameSize;
    this.maxFrameSize = maxFrameSize;
    this.sampleRate = sampleRate;
    this.sampleRateLookupKey = getSampleRateLookupKey(sampleRate);
    this.channels = channels;
    this.bitsPerSample = bitsPerSample;
    this.bitsPerSampleLookupKey = getBitsPerSampleLookupKey(bitsPerSample);
    this.totalSamples = totalSamples;
    this.seekTable = seekTable;
    this.metadata = metadata;
  }

  /** Returns the maximum size for a decoded frame from the FLAC stream. */
  public int getMaxDecodedFrameSize() {
    return maxBlockSizeSamples * channels * (bitsPerSample / 8);
  }

  /** Returns the bit-rate of the FLAC stream. */
  public int getBitRate() {
    return bitsPerSample * sampleRate * channels;
  }

  /**
   * Returns the duration of the FLAC stream in microseconds, or {@link C#TIME_UNSET} if the total
   * number of samples if unknown.
   */
  public long getDurationUs() {
    return totalSamples == 0 ? C.TIME_UNSET : totalSamples * C.MICROS_PER_SECOND / sampleRate;
  }

  /**
   * Returns the sample number of the sample at a given time.
   *
   * @param timeUs Time position in microseconds in the FLAC stream.
   * @return The sample number corresponding to the time position.
   */
  public long getSampleNumber(long timeUs) {
    long sampleNumber = (timeUs * sampleRate) / C.MICROS_PER_SECOND;
    return Util.constrainValue(sampleNumber, /* min= */ 0, totalSamples - 1);
  }

  /** Returns the approximate number of bytes per frame for the current FLAC stream. */
  public long getApproxBytesPerFrame() {
    long approxBytesPerFrame;
    if (maxFrameSize > 0) {
      approxBytesPerFrame = ((long) maxFrameSize + minFrameSize) / 2 + 1;
    } else {
      // Uses the stream's block-size if it's a known fixed block-size stream, otherwise uses the
      // default value for FLAC block-size, which is 4096.
      long blockSizeSamples =
          (minBlockSizeSamples == maxBlockSizeSamples && minBlockSizeSamples > 0)
              ? minBlockSizeSamples
              : 4096;
      approxBytesPerFrame = (blockSizeSamples * channels * bitsPerSample) / 8 + 64;
    }
    return approxBytesPerFrame;
  }

  /**
   * Returns a {@link Format} extracted from the FLAC stream metadata.
   *
   * <p>{@code streamMarkerAndInfoBlock} is updated to set the bit corresponding to the stream info
   * last metadata block flag to true.
   *
   * @param streamMarkerAndInfoBlock An array containing the FLAC stream marker followed by the
   *     stream info block.
   * @param id3Metadata The ID3 metadata of the stream, or {@code null} if there is no such data.
   * @return The extracted {@link Format}.
   */
  public Format getFormat(byte[] streamMarkerAndInfoBlock, @Nullable Metadata id3Metadata) {
    // Set the last metadata block flag, ignore the other blocks.
    streamMarkerAndInfoBlock[4] = (byte) 0x80;
    int maxInputSize = maxFrameSize > 0 ? maxFrameSize : Format.NO_VALUE;
    @Nullable Metadata metadataWithId3 = getMetadataCopyWithAppendedEntriesFrom(id3Metadata);

    return Format.createAudioSampleFormat(
        /* id= */ null,
        MimeTypes.AUDIO_FLAC,
        /* codecs= */ null,
        getBitRate(),
        maxInputSize,
        channels,
        sampleRate,
        /* pcmEncoding= */ Format.NO_VALUE,
        /* encoderDelay= */ 0,
        /* encoderPadding= */ 0,
        /* initializationData= */ Collections.singletonList(streamMarkerAndInfoBlock),
        /* drmInitData= */ null,
        /* selectionFlags= */ 0,
        /* language= */ null,
        metadataWithId3);
  }

  /** Returns a copy of the content metadata with entries from {@code other} appended. */
  @Nullable
  public Metadata getMetadataCopyWithAppendedEntriesFrom(@Nullable Metadata other) {
    return metadata == null ? other : metadata.copyWithAppendedEntriesFrom(other);
  }

  /** Returns a copy of {@code this} with the seek table replaced by the one given. */
  public FlacStreamMetadata copyWithSeekTable(@Nullable SeekTable seekTable) {
    return new FlacStreamMetadata(
        minBlockSizeSamples,
        maxBlockSizeSamples,
        minFrameSize,
        maxFrameSize,
        sampleRate,
        channels,
        bitsPerSample,
        totalSamples,
        seekTable,
        metadata);
  }

  /** Returns a copy of {@code this} with the given Vorbis comments added to the metadata. */
  public FlacStreamMetadata copyWithVorbisComments(List<String> vorbisComments) {
    @Nullable
    Metadata appendedMetadata =
        getMetadataCopyWithAppendedEntriesFrom(
            buildMetadata(vorbisComments, Collections.emptyList()));
    return new FlacStreamMetadata(
        minBlockSizeSamples,
        maxBlockSizeSamples,
        minFrameSize,
        maxFrameSize,
        sampleRate,
        channels,
        bitsPerSample,
        totalSamples,
        seekTable,
        appendedMetadata);
  }

  /** Returns a copy of {@code this} with the given picture frames added to the metadata. */
  public FlacStreamMetadata copyWithPictureFrames(List<PictureFrame> pictureFrames) {
    @Nullable
    Metadata appendedMetadata =
        getMetadataCopyWithAppendedEntriesFrom(
            buildMetadata(Collections.emptyList(), pictureFrames));
    return new FlacStreamMetadata(
        minBlockSizeSamples,
        maxBlockSizeSamples,
        minFrameSize,
        maxFrameSize,
        sampleRate,
        channels,
        bitsPerSample,
        totalSamples,
        seekTable,
        appendedMetadata);
  }

  private static int getSampleRateLookupKey(int sampleRate) {
    switch (sampleRate) {
      case 88200:
        return 1;
      case 176400:
        return 2;
      case 192000:
        return 3;
      case 8000:
        return 4;
      case 16000:
        return 5;
      case 22050:
        return 6;
      case 24000:
        return 7;
      case 32000:
        return 8;
      case 44100:
        return 9;
      case 48000:
        return 10;
      case 96000:
        return 11;
      default:
        return NOT_IN_LOOKUP_TABLE;
    }
  }

  private static int getBitsPerSampleLookupKey(int bitsPerSample) {
    switch (bitsPerSample) {
      case 8:
        return 1;
      case 12:
        return 2;
      case 16:
        return 4;
      case 20:
        return 5;
      case 24:
        return 6;
      default:
        return NOT_IN_LOOKUP_TABLE;
    }
  }

  @Nullable
  private static Metadata buildMetadata(
      List<String> vorbisComments, List<PictureFrame> pictureFrames) {
    if (vorbisComments.isEmpty() && pictureFrames.isEmpty()) {
      return null;
    }

    ArrayList<Metadata.Entry> metadataEntries = new ArrayList<>();
    for (int i = 0; i < vorbisComments.size(); i++) {
      String vorbisComment = vorbisComments.get(i);
      String[] keyAndValue = Util.splitAtFirst(vorbisComment, SEPARATOR);
      if (keyAndValue.length != 2) {
        Log.w(TAG, "Failed to parse Vorbis comment: " + vorbisComment);
      } else {
        VorbisComment entry = new VorbisComment(keyAndValue[0], keyAndValue[1]);
        metadataEntries.add(entry);
      }
    }
    metadataEntries.addAll(pictureFrames);

    return metadataEntries.isEmpty() ? null : new Metadata(metadataEntries);
  }
}

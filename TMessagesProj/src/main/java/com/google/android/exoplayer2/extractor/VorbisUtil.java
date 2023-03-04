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
package com.google.android.exoplayer2.extractor;

import android.util.Base64;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.Metadata.Entry;
import com.google.android.exoplayer2.metadata.flac.PictureFrame;
import com.google.android.exoplayer2.metadata.vorbis.VorbisComment;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Utility methods for parsing Vorbis streams. */
public final class VorbisUtil {

  /** Vorbis comment header. */
  public static final class CommentHeader {

    public final String vendor;
    public final String[] comments;
    public final int length;

    public CommentHeader(String vendor, String[] comments, int length) {
      this.vendor = vendor;
      this.comments = comments;
      this.length = length;
    }
  }

  /**
   * Vorbis identification header.
   *
   * @see <a href="https://www.xiph.org/vorbis/doc/Vorbis_I_spec.html#x1-630004.2.2">Vorbis
   *     spec/Identification header</a>
   */
  public static final class VorbisIdHeader {

    /** The {@code vorbis_version} field. */
    public final int version;
    /** The {@code audio_channels} field. */
    public final int channels;
    /** The {@code audio_sample_rate} field. */
    public final int sampleRate;
    /** The {@code bitrate_maximum} field, or {@link Format#NO_VALUE} if not greater than zero. */
    public final int bitrateMaximum;
    /** The {@code bitrate_nominal} field, or {@link Format#NO_VALUE} if not greater than zero. */
    public final int bitrateNominal;
    /** The {@code bitrate_minimum} field, or {@link Format#NO_VALUE} if not greater than zero. */
    public final int bitrateMinimum;
    /** The {@code blocksize_0} field. */
    public final int blockSize0;
    /** The {@code blocksize_1} field. */
    public final int blockSize1;
    /** The {@code framing_flag} field. */
    public final boolean framingFlag;
    /** The raw header data. */
    public final byte[] data;

    /**
     * @param version See {@link #version}.
     * @param channels See {@link #channels}.
     * @param sampleRate See {@link #sampleRate}.
     * @param bitrateMaximum See {@link #bitrateMaximum}.
     * @param bitrateNominal See {@link #bitrateNominal}.
     * @param bitrateMinimum See {@link #bitrateMinimum}.
     * @param blockSize0 See {@link #version}.
     * @param blockSize1 See {@link #blockSize1}.
     * @param framingFlag See {@link #framingFlag}.
     * @param data See {@link #data}.
     */
    public VorbisIdHeader(
        int version,
        int channels,
        int sampleRate,
        int bitrateMaximum,
        int bitrateNominal,
        int bitrateMinimum,
        int blockSize0,
        int blockSize1,
        boolean framingFlag,
        byte[] data) {
      this.version = version;
      this.channels = channels;
      this.sampleRate = sampleRate;
      this.bitrateMaximum = bitrateMaximum;
      this.bitrateNominal = bitrateNominal;
      this.bitrateMinimum = bitrateMinimum;
      this.blockSize0 = blockSize0;
      this.blockSize1 = blockSize1;
      this.framingFlag = framingFlag;
      this.data = data;
    }
  }

  /** Vorbis setup header modes. */
  public static final class Mode {

    public final boolean blockFlag;
    public final int windowType;
    public final int transformType;
    public final int mapping;

    public Mode(boolean blockFlag, int windowType, int transformType, int mapping) {
      this.blockFlag = blockFlag;
      this.windowType = windowType;
      this.transformType = transformType;
      this.mapping = mapping;
    }
  }

  private static final String TAG = "VorbisUtil";

  /**
   * Returns ilog(x), which is the index of the highest set bit in {@code x}.
   *
   * @see <a href="https://www.xiph.org/vorbis/doc/Vorbis_I_spec.html#x1-1190009.2.1">Vorbis
   *     spec</a>
   * @param x the value of which the ilog should be calculated.
   * @return ilog(x)
   */
  public static int iLog(int x) {
    int val = 0;
    while (x > 0) {
      val++;
      x >>>= 1;
    }
    return val;
  }

  /**
   * Reads a Vorbis identification header from {@code headerData}.
   *
   * @see <a href="https://www.xiph.org/vorbis/doc/Vorbis_I_spec.html#x1-630004.2.2">Vorbis
   *     spec/Identification header</a>
   * @param headerData a {@link ParsableByteArray} wrapping the header data.
   * @return a {@link VorbisUtil.VorbisIdHeader} with meta data.
   * @throws ParserException thrown if invalid capture pattern is detected.
   */
  public static VorbisIdHeader readVorbisIdentificationHeader(ParsableByteArray headerData)
      throws ParserException {

    verifyVorbisHeaderCapturePattern(0x01, headerData, false);

    int version = headerData.readLittleEndianUnsignedIntToInt();
    int channels = headerData.readUnsignedByte();
    int sampleRate = headerData.readLittleEndianUnsignedIntToInt();
    int bitrateMaximum = headerData.readLittleEndianInt();
    if (bitrateMaximum <= 0) {
      bitrateMaximum = Format.NO_VALUE;
    }
    int bitrateNominal = headerData.readLittleEndianInt();
    if (bitrateNominal <= 0) {
      bitrateNominal = Format.NO_VALUE;
    }
    int bitrateMinimum = headerData.readLittleEndianInt();
    if (bitrateMinimum <= 0) {
      bitrateMinimum = Format.NO_VALUE;
    }
    int blockSize = headerData.readUnsignedByte();
    int blockSize0 = (int) Math.pow(2, blockSize & 0x0F);
    int blockSize1 = (int) Math.pow(2, (blockSize & 0xF0) >> 4);

    boolean framingFlag = (headerData.readUnsignedByte() & 0x01) > 0;
    // raw data of Vorbis setup header has to be passed to decoder as CSD buffer #1
    byte[] data = Arrays.copyOf(headerData.getData(), headerData.limit());

    return new VorbisIdHeader(
        version,
        channels,
        sampleRate,
        bitrateMaximum,
        bitrateNominal,
        bitrateMinimum,
        blockSize0,
        blockSize1,
        framingFlag,
        data);
  }

  /**
   * Reads a Vorbis comment header.
   *
   * @see <a href="https://www.xiph.org/vorbis/doc/Vorbis_I_spec.html#x1-640004.2.3">Vorbis
   *     spec/Comment header</a>
   * @param headerData A {@link ParsableByteArray} wrapping the header data.
   * @return A {@link VorbisUtil.CommentHeader} with all the comments.
   * @throws ParserException If an error occurs parsing the comment header.
   */
  public static CommentHeader readVorbisCommentHeader(ParsableByteArray headerData)
      throws ParserException {
    return readVorbisCommentHeader(
        headerData, /* hasMetadataHeader= */ true, /* hasFramingBit= */ true);
  }

  /**
   * Reads a Vorbis comment header.
   *
   * <p>The data provided may not contain the Vorbis metadata common header and the framing bit.
   *
   * @see <a href="https://www.xiph.org/vorbis/doc/Vorbis_I_spec.html#x1-640004.2.3">Vorbis
   *     spec/Comment header</a>
   * @param headerData A {@link ParsableByteArray} wrapping the header data.
   * @param hasMetadataHeader Whether the {@code headerData} contains a Vorbis metadata common
   *     header preceding the comment header.
   * @param hasFramingBit Whether the {@code headerData} contains a framing bit.
   * @return A {@link VorbisUtil.CommentHeader} with all the comments.
   * @throws ParserException If an error occurs parsing the comment header.
   */
  public static CommentHeader readVorbisCommentHeader(
      ParsableByteArray headerData, boolean hasMetadataHeader, boolean hasFramingBit)
      throws ParserException {

    if (hasMetadataHeader) {
      verifyVorbisHeaderCapturePattern(/* headerType= */ 0x03, headerData, /* quiet= */ false);
    }
    int length = 7;

    int len = (int) headerData.readLittleEndianUnsignedInt();
    length += 4;
    String vendor = headerData.readString(len);
    length += vendor.length();

    long commentListLen = headerData.readLittleEndianUnsignedInt();
    String[] comments = new String[(int) commentListLen];
    length += 4;
    for (int i = 0; i < commentListLen; i++) {
      len = (int) headerData.readLittleEndianUnsignedInt();
      length += 4;
      comments[i] = headerData.readString(len);
      length += comments[i].length();
    }
    if (hasFramingBit && (headerData.readUnsignedByte() & 0x01) == 0) {
      throw ParserException.createForMalformedContainer(
          "framing bit expected to be set", /* cause= */ null);
    }
    length += 1;
    return new CommentHeader(vendor, comments, length);
  }

  /**
   * Builds a {@link Metadata} instance from a list of Vorbis Comments.
   *
   * <p>METADATA_BLOCK_PICTURE comments will be transformed into {@link PictureFrame} entries. All
   * others will be transformed into {@link VorbisComment} entries.
   *
   * @param vorbisComments The raw input of comments, as a key-value pair KEY=VAL.
   * @return The fully parsed Metadata instance. Null if no vorbis comments could be parsed.
   */
  @Nullable
  public static Metadata parseVorbisComments(List<String> vorbisComments) {
    List<Entry> metadataEntries = new ArrayList<>();
    for (int i = 0; i < vorbisComments.size(); i++) {
      String vorbisComment = vorbisComments.get(i);
      String[] keyAndValue = Util.splitAtFirst(vorbisComment, "=");
      if (keyAndValue.length != 2) {
        Log.w(TAG, "Failed to parse Vorbis comment: " + vorbisComment);
        continue;
      }

      if (keyAndValue[0].equals("METADATA_BLOCK_PICTURE")) {
        // This tag is a special cover art tag, outlined by
        // https://wiki.xiph.org/index.php/VorbisComment#Cover_art.
        // Decode it from Base64 and transform it into a PictureFrame.
        try {
          byte[] decoded = Base64.decode(keyAndValue[1], Base64.DEFAULT);
          metadataEntries.add(PictureFrame.fromPictureBlock(new ParsableByteArray(decoded)));
        } catch (RuntimeException e) {
          Log.w(TAG, "Failed to parse vorbis picture", e);
        }
      } else {
        VorbisComment entry = new VorbisComment(keyAndValue[0], keyAndValue[1]);
        metadataEntries.add(entry);
      }
    }

    return metadataEntries.isEmpty() ? null : new Metadata(metadataEntries);
  }

  /**
   * Verifies whether the next bytes in {@code header} are a Vorbis header of the given {@code
   * headerType}.
   *
   * @param headerType the type of the header expected.
   * @param header the alleged header bytes.
   * @param quiet if {@code true} no exceptions are thrown. Instead {@code false} is returned.
   * @return the number of bytes read.
   * @throws ParserException thrown if header type or capture pattern is not as expected.
   */
  public static boolean verifyVorbisHeaderCapturePattern(
      int headerType, ParsableByteArray header, boolean quiet) throws ParserException {
    if (header.bytesLeft() < 7) {
      if (quiet) {
        return false;
      } else {
        throw ParserException.createForMalformedContainer(
            "too short header: " + header.bytesLeft(), /* cause= */ null);
      }
    }

    if (header.readUnsignedByte() != headerType) {
      if (quiet) {
        return false;
      } else {
        throw ParserException.createForMalformedContainer(
            "expected header type " + Integer.toHexString(headerType), /* cause= */ null);
      }
    }

    if (!(header.readUnsignedByte() == 'v'
        && header.readUnsignedByte() == 'o'
        && header.readUnsignedByte() == 'r'
        && header.readUnsignedByte() == 'b'
        && header.readUnsignedByte() == 'i'
        && header.readUnsignedByte() == 's')) {
      if (quiet) {
        return false;
      } else {
        throw ParserException.createForMalformedContainer(
            "expected characters 'vorbis'", /* cause= */ null);
      }
    }
    return true;
  }

  /**
   * This method reads the modes which are located at the very end of the Vorbis setup header.
   * That's why we need to partially decode or at least read the entire setup header to know where
   * to start reading the modes.
   *
   * @see <a href="https://www.xiph.org/vorbis/doc/Vorbis_I_spec.html#x1-650004.2.4">Vorbis
   *     spec/Setup header</a>
   * @param headerData a {@link ParsableByteArray} containing setup header data.
   * @param channels the number of channels.
   * @return an array of {@link Mode}s.
   * @throws ParserException thrown if bit stream is invalid.
   */
  public static Mode[] readVorbisModes(ParsableByteArray headerData, int channels)
      throws ParserException {

    verifyVorbisHeaderCapturePattern(0x05, headerData, false);

    int numberOfBooks = headerData.readUnsignedByte() + 1;

    VorbisBitArray bitArray = new VorbisBitArray(headerData.getData());
    bitArray.skipBits(headerData.getPosition() * 8);

    for (int i = 0; i < numberOfBooks; i++) {
      readBook(bitArray);
    }

    int timeCount = bitArray.readBits(6) + 1;
    for (int i = 0; i < timeCount; i++) {
      if (bitArray.readBits(16) != 0x00) {
        throw ParserException.createForMalformedContainer(
            "placeholder of time domain transforms not zeroed out", /* cause= */ null);
      }
    }
    readFloors(bitArray);
    readResidues(bitArray);
    readMappings(channels, bitArray);

    Mode[] modes = readModes(bitArray);
    if (!bitArray.readBit()) {
      throw ParserException.createForMalformedContainer(
          "framing bit after modes not set as expected", /* cause= */ null);
    }
    return modes;
  }

  private static Mode[] readModes(VorbisBitArray bitArray) {
    int modeCount = bitArray.readBits(6) + 1;
    Mode[] modes = new Mode[modeCount];
    for (int i = 0; i < modeCount; i++) {
      boolean blockFlag = bitArray.readBit();
      int windowType = bitArray.readBits(16);
      int transformType = bitArray.readBits(16);
      int mapping = bitArray.readBits(8);
      modes[i] = new Mode(blockFlag, windowType, transformType, mapping);
    }
    return modes;
  }

  private static void readMappings(int channels, VorbisBitArray bitArray) throws ParserException {
    int mappingsCount = bitArray.readBits(6) + 1;
    for (int i = 0; i < mappingsCount; i++) {
      int mappingType = bitArray.readBits(16);
      if (mappingType != 0) {
        Log.e(TAG, "mapping type other than 0 not supported: " + mappingType);
        continue;
      }
      int submaps;
      if (bitArray.readBit()) {
        submaps = bitArray.readBits(4) + 1;
      } else {
        submaps = 1;
      }
      int couplingSteps;
      if (bitArray.readBit()) {
        couplingSteps = bitArray.readBits(8) + 1;
        for (int j = 0; j < couplingSteps; j++) {
          bitArray.skipBits(iLog(channels - 1)); // magnitude
          bitArray.skipBits(iLog(channels - 1)); // angle
        }
      } /*else {
          couplingSteps = 0;
        }*/
      if (bitArray.readBits(2) != 0x00) {
        throw ParserException.createForMalformedContainer(
            "to reserved bits must be zero after mapping coupling steps", /* cause= */ null);
      }
      if (submaps > 1) {
        for (int j = 0; j < channels; j++) {
          bitArray.skipBits(4); // mappingMux
        }
      }
      for (int j = 0; j < submaps; j++) {
        bitArray.skipBits(8); // discard
        bitArray.skipBits(8); // submapFloor
        bitArray.skipBits(8); // submapResidue
      }
    }
  }

  private static void readResidues(VorbisBitArray bitArray) throws ParserException {
    int residueCount = bitArray.readBits(6) + 1;
    for (int i = 0; i < residueCount; i++) {
      int residueType = bitArray.readBits(16);
      if (residueType > 2) {
        throw ParserException.createForMalformedContainer(
            "residueType greater than 2 is not decodable", /* cause= */ null);
      } else {
        bitArray.skipBits(24); // begin
        bitArray.skipBits(24); // end
        bitArray.skipBits(24); // partitionSize (add one)
        int classifications = bitArray.readBits(6) + 1;
        bitArray.skipBits(8); // classbook
        int[] cascade = new int[classifications];
        for (int j = 0; j < classifications; j++) {
          int highBits = 0;
          int lowBits = bitArray.readBits(3);
          if (bitArray.readBit()) {
            highBits = bitArray.readBits(5);
          }
          cascade[j] = highBits * 8 + lowBits;
        }
        for (int j = 0; j < classifications; j++) {
          for (int k = 0; k < 8; k++) {
            if ((cascade[j] & (0x01 << k)) != 0) {
              bitArray.skipBits(8); // discard
            }
          }
        }
      }
    }
  }

  private static void readFloors(VorbisBitArray bitArray) throws ParserException {
    int floorCount = bitArray.readBits(6) + 1;
    for (int i = 0; i < floorCount; i++) {
      int floorType = bitArray.readBits(16);
      switch (floorType) {
        case 0:
          bitArray.skipBits(8); // order
          bitArray.skipBits(16); // rate
          bitArray.skipBits(16); // barkMapSize
          bitArray.skipBits(6); // amplitudeBits
          bitArray.skipBits(8); // amplitudeOffset
          int floorNumberOfBooks = bitArray.readBits(4) + 1;
          for (int j = 0; j < floorNumberOfBooks; j++) {
            bitArray.skipBits(8);
          }
          break;
        case 1:
          int partitions = bitArray.readBits(5);
          int maximumClass = -1;
          int[] partitionClassList = new int[partitions];
          for (int j = 0; j < partitions; j++) {
            partitionClassList[j] = bitArray.readBits(4);
            if (partitionClassList[j] > maximumClass) {
              maximumClass = partitionClassList[j];
            }
          }
          int[] classDimensions = new int[maximumClass + 1];
          for (int j = 0; j < classDimensions.length; j++) {
            classDimensions[j] = bitArray.readBits(3) + 1;
            int classSubclasses = bitArray.readBits(2);
            if (classSubclasses > 0) {
              bitArray.skipBits(8); // classMasterbooks
            }
            for (int k = 0; k < (1 << classSubclasses); k++) {
              bitArray.skipBits(8); // subclassBook (subtract 1)
            }
          }
          bitArray.skipBits(2); // multiplier (add one)
          int rangeBits = bitArray.readBits(4);
          int count = 0;
          for (int j = 0, k = 0; j < partitions; j++) {
            int idx = partitionClassList[j];
            count += classDimensions[idx];
            for (; k < count; k++) {
              bitArray.skipBits(rangeBits); // floorValue
            }
          }
          break;
        default:
          throw ParserException.createForMalformedContainer(
              "floor type greater than 1 not decodable: " + floorType, /* cause= */ null);
      }
    }
  }

  private static CodeBook readBook(VorbisBitArray bitArray) throws ParserException {
    if (bitArray.readBits(24) != 0x564342) {
      throw ParserException.createForMalformedContainer(
          "expected code book to start with [0x56, 0x43, 0x42] at " + bitArray.getPosition(),
          /* cause= */ null);
    }
    int dimensions = bitArray.readBits(16);
    int entries = bitArray.readBits(24);
    long[] lengthMap = new long[entries];

    boolean isOrdered = bitArray.readBit();
    if (!isOrdered) {
      boolean isSparse = bitArray.readBit();
      for (int i = 0; i < lengthMap.length; i++) {
        if (isSparse) {
          if (bitArray.readBit()) {
            lengthMap[i] = (long) (bitArray.readBits(5) + 1);
          } else { // entry unused
            lengthMap[i] = 0;
          }
        } else { // not sparse
          lengthMap[i] = (long) (bitArray.readBits(5) + 1);
        }
      }
    } else {
      int length = bitArray.readBits(5) + 1;
      for (int i = 0; i < lengthMap.length; ) {
        int num = bitArray.readBits(iLog(entries - i));
        for (int j = 0; j < num && i < lengthMap.length; i++, j++) {
          lengthMap[i] = length;
        }
        length++;
      }
    }

    int lookupType = bitArray.readBits(4);
    if (lookupType > 2) {
      throw ParserException.createForMalformedContainer(
          "lookup type greater than 2 not decodable: " + lookupType, /* cause= */ null);
    } else if (lookupType == 1 || lookupType == 2) {
      bitArray.skipBits(32); // minimumValue
      bitArray.skipBits(32); // deltaValue
      int valueBits = bitArray.readBits(4) + 1;
      bitArray.skipBits(1); // sequenceP
      long lookupValuesCount;
      if (lookupType == 1) {
        if (dimensions != 0) {
          lookupValuesCount = mapType1QuantValues(entries, dimensions);
        } else {
          lookupValuesCount = 0;
        }
      } else {
        lookupValuesCount = (long) entries * dimensions;
      }
      // discard (no decoding required yet)
      bitArray.skipBits((int) (lookupValuesCount * valueBits));
    }
    return new CodeBook(dimensions, entries, lengthMap, lookupType, isOrdered);
  }

  /**
   * @see <a href="http://svn.xiph.org/trunk/vorbis/lib/sharedbook.c">_book_maptype1_quantvals</a>
   */
  private static long mapType1QuantValues(long entries, long dimension) {
    return (long) Math.floor(Math.pow(entries, 1.d / dimension));
  }

  private VorbisUtil() {
    // Prevent instantiation.
  }

  private static final class CodeBook {

    public final int dimensions;
    public final int entries;
    public final long[] lengthMap;
    public final int lookupType;
    public final boolean isOrdered;

    public CodeBook(
        int dimensions, int entries, long[] lengthMap, int lookupType, boolean isOrdered) {
      this.dimensions = dimensions;
      this.entries = entries;
      this.lengthMap = lengthMap;
      this.lookupType = lookupType;
      this.isOrdered = isOrdered;
    }
  }
}

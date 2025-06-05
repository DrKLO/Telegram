/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.ts;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static com.google.android.exoplayer2.util.Util.castNonNull;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Collections;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Parses an ISO/IEC 14496-2 (MPEG-4 Part 2) or ITU-T Recommendation H.263 byte stream and extracts
 * individual frames.
 */
public final class H263Reader implements ElementaryStreamReader {

  private static final String TAG = "H263Reader";

  private static final int START_CODE_VALUE_VISUAL_OBJECT_SEQUENCE = 0xB0;
  private static final int START_CODE_VALUE_USER_DATA = 0xB2;
  private static final int START_CODE_VALUE_GROUP_OF_VOP = 0xB3;
  private static final int START_CODE_VALUE_VISUAL_OBJECT = 0xB5;
  private static final int START_CODE_VALUE_VOP = 0xB6;
  private static final int START_CODE_VALUE_MAX_VIDEO_OBJECT = 0x1F;
  private static final int START_CODE_VALUE_UNSET = -1;

  // See ISO 14496-2 (2001) table 6-12 for the mapping from aspect_ratio_info to pixel aspect ratio.
  private static final float[] PIXEL_WIDTH_HEIGHT_RATIO_BY_ASPECT_RATIO_INFO =
      new float[] {1f, 1f, 12 / 11f, 10 / 11f, 16 / 11f, 40 / 33f, 1f};
  private static final int VIDEO_OBJECT_LAYER_SHAPE_RECTANGULAR = 0;

  @Nullable private final UserDataReader userDataReader;
  @Nullable private final ParsableByteArray userDataParsable;

  // State that should be reset on seek.
  private final boolean[] prefixFlags;
  private final CsdBuffer csdBuffer;
  @Nullable private final NalUnitTargetBuffer userData;
  private H263Reader.@MonotonicNonNull SampleReader sampleReader;
  private long totalBytesWritten;

  // State initialized once when tracks are created.
  private @MonotonicNonNull String formatId;
  private @MonotonicNonNull TrackOutput output;

  // State that should not be reset on seek.
  private boolean hasOutputFormat;

  // Per packet state that gets reset at the start of each packet.
  private long pesTimeUs;

  /** Creates a new reader. */
  public H263Reader() {
    this(null);
  }

  /* package */ H263Reader(@Nullable UserDataReader userDataReader) {
    this.userDataReader = userDataReader;
    prefixFlags = new boolean[4];
    csdBuffer = new CsdBuffer(128);
    pesTimeUs = C.TIME_UNSET;
    if (userDataReader != null) {
      userData = new NalUnitTargetBuffer(START_CODE_VALUE_USER_DATA, 128);
      userDataParsable = new ParsableByteArray();
    } else {
      userData = null;
      userDataParsable = null;
    }
  }

  @Override
  public void seek() {
    NalUnitUtil.clearPrefixFlags(prefixFlags);
    csdBuffer.reset();
    if (sampleReader != null) {
      sampleReader.reset();
    }
    if (userData != null) {
      userData.reset();
    }
    totalBytesWritten = 0;
    pesTimeUs = C.TIME_UNSET;
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
    idGenerator.generateNewId();
    formatId = idGenerator.getFormatId();
    output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_VIDEO);
    sampleReader = new SampleReader(output);
    if (userDataReader != null) {
      userDataReader.createTracks(extractorOutput, idGenerator);
    }
  }

  @Override
  public void packetStarted(long pesTimeUs, @TsPayloadReader.Flags int flags) {
    // TODO (Internal b/32267012): Consider using random access indicator.
    if (pesTimeUs != C.TIME_UNSET) {
      this.pesTimeUs = pesTimeUs;
    }
  }

  @Override
  public void consume(ParsableByteArray data) {
    // Assert that createTracks has been called.
    checkStateNotNull(sampleReader);
    checkStateNotNull(output);
    int offset = data.getPosition();
    int limit = data.limit();
    byte[] dataArray = data.getData();

    // Append the data to the buffer.
    totalBytesWritten += data.bytesLeft();
    output.sampleData(data, data.bytesLeft());

    while (true) {
      int startCodeOffset = NalUnitUtil.findNalUnit(dataArray, offset, limit, prefixFlags);

      if (startCodeOffset == limit) {
        // We've scanned to the end of the data without finding another start code.
        if (!hasOutputFormat) {
          csdBuffer.onData(dataArray, offset, limit);
        }
        sampleReader.onData(dataArray, offset, limit);
        if (userData != null) {
          userData.appendToNalUnit(dataArray, offset, limit);
        }
        return;
      }

      // We've found a start code with the following value.
      int startCodeValue = data.getData()[startCodeOffset + 3] & 0xFF;
      // This is the number of bytes from the current offset to the start of the next start
      // code. It may be negative if the start code started in the previously consumed data.
      int lengthToStartCode = startCodeOffset - offset;

      if (!hasOutputFormat) {
        if (lengthToStartCode > 0) {
          csdBuffer.onData(dataArray, offset, /* limit= */ startCodeOffset);
        }
        // This is the number of bytes belonging to the next start code that have already been
        // passed to csdBuffer.
        int bytesAlreadyPassed = lengthToStartCode < 0 ? -lengthToStartCode : 0;
        if (csdBuffer.onStartCode(startCodeValue, bytesAlreadyPassed)) {
          // The csd data is complete, so we can decode and output the media format.
          output.format(
              parseCsdBuffer(csdBuffer, csdBuffer.volStartPosition, checkNotNull(formatId)));
          hasOutputFormat = true;
        }
      }

      sampleReader.onData(dataArray, offset, /* limit= */ startCodeOffset);

      if (userData != null) {
        int bytesAlreadyPassed = 0;
        if (lengthToStartCode > 0) {
          userData.appendToNalUnit(dataArray, offset, /* limit= */ startCodeOffset);
        } else {
          bytesAlreadyPassed = -lengthToStartCode;
        }

        if (userData.endNalUnit(bytesAlreadyPassed)) {
          int unescapedLength = NalUnitUtil.unescapeStream(userData.nalData, userData.nalLength);
          castNonNull(userDataParsable).reset(userData.nalData, unescapedLength);
          castNonNull(userDataReader).consume(pesTimeUs, userDataParsable);
        }

        if (startCodeValue == START_CODE_VALUE_USER_DATA
            && data.getData()[startCodeOffset + 2] == 0x1) {
          userData.startNalUnit(startCodeValue);
        }
      }

      int bytesWrittenPastPosition = limit - startCodeOffset;
      long absolutePosition = totalBytesWritten - bytesWrittenPastPosition;
      sampleReader.onDataEnd(absolutePosition, bytesWrittenPastPosition, hasOutputFormat);
      // Indicate the start of the next chunk.
      sampleReader.onStartCode(startCodeValue, pesTimeUs);
      // Continue scanning the data.
      offset = startCodeOffset + 3;
    }
  }

  @Override
  public void packetFinished() {
    // Do nothing.
  }

  /**
   * Parses a codec-specific data buffer, returning the {@link Format} of the media.
   *
   * @param csdBuffer The buffer to parse.
   * @param volStartPosition The byte offset of the start of the video object layer in the buffer.
   * @param formatId The ID for the generated format.
   * @return The {@link Format} of the media represented in the buffer.
   */
  private static Format parseCsdBuffer(CsdBuffer csdBuffer, int volStartPosition, String formatId) {
    byte[] csdData = Arrays.copyOf(csdBuffer.data, csdBuffer.length);
    ParsableBitArray buffer = new ParsableBitArray(csdData);
    buffer.skipBytes(volStartPosition);

    // Parse the video object layer defined in ISO 14496-2 (2001) subsection 6.2.3.
    buffer.skipBytes(4); // video_object_layer_start_code
    buffer.skipBit(); // random_accessible_vol
    buffer.skipBits(8); // video_object_type_indication
    if (buffer.readBit()) { // is_object_layer_identifier
      buffer.skipBits(4); // video_object_layer_verid
      buffer.skipBits(3); // video_object_layer_priority
    }
    float pixelWidthHeightRatio;
    int aspectRatioInfo = buffer.readBits(4);
    if (aspectRatioInfo == 0x0F) { // extended_PAR
      int parWidth = buffer.readBits(8);
      int parHeight = buffer.readBits(8);
      if (parHeight == 0) {
        Log.w(TAG, "Invalid aspect ratio");
        pixelWidthHeightRatio = 1f;
      } else {
        pixelWidthHeightRatio = (float) parWidth / parHeight;
      }
    } else if (aspectRatioInfo < PIXEL_WIDTH_HEIGHT_RATIO_BY_ASPECT_RATIO_INFO.length) {
      pixelWidthHeightRatio = PIXEL_WIDTH_HEIGHT_RATIO_BY_ASPECT_RATIO_INFO[aspectRatioInfo];
    } else {
      Log.w(TAG, "Invalid aspect ratio");
      pixelWidthHeightRatio = 1f;
    }
    if (buffer.readBit()) { // vol_control_parameters
      buffer.skipBits(2); // chroma_format
      buffer.skipBits(1); // low_delay
      if (buffer.readBit()) { // vbv_parameters
        buffer.skipBits(15); // first_half_bit_rate
        buffer.skipBit(); // marker_bit
        buffer.skipBits(15); // latter_half_bit_rate
        buffer.skipBit(); // marker_bit
        buffer.skipBits(15); // first_half_vbv_buffer_size
        buffer.skipBit(); // marker_bit
        buffer.skipBits(3); // latter_half_vbv_buffer_size
        buffer.skipBits(11); // first_half_vbv_occupancy
        buffer.skipBit(); // marker_bit
        buffer.skipBits(15); // latter_half_vbv_occupancy
        buffer.skipBit(); // marker_bit
      }
    }
    int videoObjectLayerShape = buffer.readBits(2);
    if (videoObjectLayerShape != VIDEO_OBJECT_LAYER_SHAPE_RECTANGULAR) {
      Log.w(TAG, "Unhandled video object layer shape");
    }
    buffer.skipBit(); // marker_bit
    int vopTimeIncrementResolution = buffer.readBits(16);
    buffer.skipBit(); // marker_bit
    if (buffer.readBit()) { // fixed_vop_rate
      if (vopTimeIncrementResolution == 0) {
        Log.w(TAG, "Invalid vop_increment_time_resolution");
      } else {
        vopTimeIncrementResolution--;
        int numBits = 0;
        while (vopTimeIncrementResolution > 0) {
          ++numBits;
          vopTimeIncrementResolution >>= 1;
        }
        buffer.skipBits(numBits); // fixed_vop_time_increment
      }
    }
    buffer.skipBit(); // marker_bit
    int videoObjectLayerWidth = buffer.readBits(13);
    buffer.skipBit(); // marker_bit
    int videoObjectLayerHeight = buffer.readBits(13);
    buffer.skipBit(); // marker_bit
    buffer.skipBit(); // interlaced
    return new Format.Builder()
        .setId(formatId)
        .setSampleMimeType(MimeTypes.VIDEO_MP4V)
        .setWidth(videoObjectLayerWidth)
        .setHeight(videoObjectLayerHeight)
        .setPixelWidthHeightRatio(pixelWidthHeightRatio)
        .setInitializationData(Collections.singletonList(csdData))
        .build();
  }

  private static final class CsdBuffer {

    private static final byte[] START_CODE = new byte[] {0, 0, 1};

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef({
      STATE_SKIP_TO_VISUAL_OBJECT_SEQUENCE_START,
      STATE_EXPECT_VISUAL_OBJECT_START,
      STATE_EXPECT_VIDEO_OBJECT_START,
      STATE_EXPECT_VIDEO_OBJECT_LAYER_START,
      STATE_WAIT_FOR_VOP_START
    })
    private @interface State {}

    private static final int STATE_SKIP_TO_VISUAL_OBJECT_SEQUENCE_START = 0;
    private static final int STATE_EXPECT_VISUAL_OBJECT_START = 1;
    private static final int STATE_EXPECT_VIDEO_OBJECT_START = 2;
    private static final int STATE_EXPECT_VIDEO_OBJECT_LAYER_START = 3;
    private static final int STATE_WAIT_FOR_VOP_START = 4;

    private boolean isFilling;
    private @State int state;

    public int length;
    public int volStartPosition;
    public byte[] data;

    public CsdBuffer(int initialCapacity) {
      data = new byte[initialCapacity];
    }

    public void reset() {
      isFilling = false;
      length = 0;
      state = STATE_SKIP_TO_VISUAL_OBJECT_SEQUENCE_START;
    }

    /**
     * Called when a start code is encountered in the stream.
     *
     * @param startCodeValue The start code value.
     * @param bytesAlreadyPassed The number of bytes of the start code that have been passed to
     *     {@link #onData(byte[], int, int)}, or 0.
     * @return Whether the csd data is now complete. If true is returned, neither this method nor
     *     {@link #onData(byte[], int, int)} should be called again without an interleaving call to
     *     {@link #reset()}.
     */
    public boolean onStartCode(int startCodeValue, int bytesAlreadyPassed) {
      switch (state) {
        case STATE_SKIP_TO_VISUAL_OBJECT_SEQUENCE_START:
          if (startCodeValue == START_CODE_VALUE_VISUAL_OBJECT_SEQUENCE) {
            state = STATE_EXPECT_VISUAL_OBJECT_START;
            isFilling = true;
          }
          break;
        case STATE_EXPECT_VISUAL_OBJECT_START:
          if (startCodeValue != START_CODE_VALUE_VISUAL_OBJECT) {
            Log.w(TAG, "Unexpected start code value");
            reset();
          } else {
            state = STATE_EXPECT_VIDEO_OBJECT_START;
          }
          break;
        case STATE_EXPECT_VIDEO_OBJECT_START:
          if (startCodeValue > START_CODE_VALUE_MAX_VIDEO_OBJECT) {
            Log.w(TAG, "Unexpected start code value");
            reset();
          } else {
            state = STATE_EXPECT_VIDEO_OBJECT_LAYER_START;
          }
          break;
        case STATE_EXPECT_VIDEO_OBJECT_LAYER_START:
          if ((startCodeValue & 0xF0) != 0x20) {
            Log.w(TAG, "Unexpected start code value");
            reset();
          } else {
            volStartPosition = length;
            state = STATE_WAIT_FOR_VOP_START;
          }
          break;
        case STATE_WAIT_FOR_VOP_START:
          if (startCodeValue == START_CODE_VALUE_GROUP_OF_VOP
              || startCodeValue == START_CODE_VALUE_VISUAL_OBJECT) {
            length -= bytesAlreadyPassed;
            isFilling = false;
            return true;
          }
          break;
        default:
          throw new IllegalStateException();
      }
      onData(START_CODE, /* offset= */ 0, /* limit= */ START_CODE.length);
      return false;
    }

    public void onData(byte[] newData, int offset, int limit) {
      if (!isFilling) {
        return;
      }
      int readLength = limit - offset;
      if (data.length < length + readLength) {
        data = Arrays.copyOf(data, (length + readLength) * 2);
      }
      System.arraycopy(newData, offset, data, length, readLength);
      length += readLength;
    }
  }

  private static final class SampleReader {

    /** Byte offset of vop_coding_type after the start code value. */
    private static final int OFFSET_VOP_CODING_TYPE = 1;
    /** Value of vop_coding_type for intra video object planes. */
    private static final int VOP_CODING_TYPE_INTRA = 0;

    private final TrackOutput output;

    private boolean readingSample;
    private boolean lookingForVopCodingType;
    private boolean sampleIsKeyframe;
    private int startCodeValue;
    private int vopBytesRead;
    private long samplePosition;
    private long sampleTimeUs;

    public SampleReader(TrackOutput output) {
      this.output = output;
    }

    public void reset() {
      readingSample = false;
      lookingForVopCodingType = false;
      sampleIsKeyframe = false;
      startCodeValue = START_CODE_VALUE_UNSET;
    }

    public void onStartCode(int startCodeValue, long pesTimeUs) {
      this.startCodeValue = startCodeValue;
      sampleIsKeyframe = false;
      readingSample =
          startCodeValue == START_CODE_VALUE_VOP || startCodeValue == START_CODE_VALUE_GROUP_OF_VOP;
      lookingForVopCodingType = startCodeValue == START_CODE_VALUE_VOP;
      vopBytesRead = 0;
      sampleTimeUs = pesTimeUs;
    }

    public void onData(byte[] data, int offset, int limit) {
      if (lookingForVopCodingType) {
        int headerOffset = offset + OFFSET_VOP_CODING_TYPE - vopBytesRead;
        if (headerOffset < limit) {
          sampleIsKeyframe = ((data[headerOffset] & 0xC0) >> 6) == VOP_CODING_TYPE_INTRA;
          lookingForVopCodingType = false;
        } else {
          vopBytesRead += limit - offset;
        }
      }
    }

    public void onDataEnd(long position, int bytesWrittenPastPosition, boolean hasOutputFormat) {
      if (startCodeValue == START_CODE_VALUE_VOP
          && hasOutputFormat
          && readingSample
          && sampleTimeUs != C.TIME_UNSET) {
        int size = (int) (position - samplePosition);
        @C.BufferFlags int flags = sampleIsKeyframe ? C.BUFFER_FLAG_KEY_FRAME : 0;
        output.sampleMetadata(
            sampleTimeUs, flags, size, bytesWrittenPastPosition, /* cryptoData= */ null);
      }
      // Start a new sample, unless this is a 'group of video object plane' in which case we
      // include the data at the start of a 'video object plane' coming next.
      if (startCodeValue != START_CODE_VALUE_GROUP_OF_VOP) {
        samplePosition = position;
      }
    }
  }
}

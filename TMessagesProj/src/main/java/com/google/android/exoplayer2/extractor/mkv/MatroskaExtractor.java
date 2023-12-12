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
package com.google.android.exoplayer2.extractor.mkv;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.util.Pair;
import android.util.SparseArray;
import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.audio.AacUtil;
import com.google.android.exoplayer2.audio.MpegAudioUtil;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData;
import com.google.android.exoplayer2.extractor.ChunkIndex;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.TrueHdSampleRechunker;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.LongArray;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.AvcConfig;
import com.google.android.exoplayer2.video.ColorInfo;
import com.google.android.exoplayer2.video.DolbyVisionConfig;
import com.google.android.exoplayer2.video.HevcConfig;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** Extracts data from the Matroska and WebM container formats. */
public class MatroskaExtractor implements Extractor {

  /** Factory for {@link MatroskaExtractor} instances. */
  public static final ExtractorsFactory FACTORY = () -> new Extractor[] {new MatroskaExtractor()};

  /**
   * Flags controlling the behavior of the extractor. Possible flag value is {@link
   * #FLAG_DISABLE_SEEK_FOR_CUES}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef(
      flag = true,
      value = {FLAG_DISABLE_SEEK_FOR_CUES})
  public @interface Flags {}
  /**
   * Flag to disable seeking for cues.
   *
   * <p>Normally (i.e. when this flag is not set) the extractor will seek to the cues element if its
   * position is specified in the seek head and if it's after the first cluster. Setting this flag
   * disables seeking to the cues element. If the cues element is after the first cluster then the
   * media is treated as being unseekable.
   */
  public static final int FLAG_DISABLE_SEEK_FOR_CUES = 1;

  private static final String TAG = "MatroskaExtractor";

  private static final int UNSET_ENTRY_ID = -1;

  private static final int BLOCK_STATE_START = 0;
  private static final int BLOCK_STATE_HEADER = 1;
  private static final int BLOCK_STATE_DATA = 2;

  private static final String DOC_TYPE_MATROSKA = "matroska";
  private static final String DOC_TYPE_WEBM = "webm";
  private static final String CODEC_ID_VP8 = "V_VP8";
  private static final String CODEC_ID_VP9 = "V_VP9";
  private static final String CODEC_ID_AV1 = "V_AV1";
  private static final String CODEC_ID_MPEG2 = "V_MPEG2";
  private static final String CODEC_ID_MPEG4_SP = "V_MPEG4/ISO/SP";
  private static final String CODEC_ID_MPEG4_ASP = "V_MPEG4/ISO/ASP";
  private static final String CODEC_ID_MPEG4_AP = "V_MPEG4/ISO/AP";
  private static final String CODEC_ID_H264 = "V_MPEG4/ISO/AVC";
  private static final String CODEC_ID_H265 = "V_MPEGH/ISO/HEVC";
  private static final String CODEC_ID_FOURCC = "V_MS/VFW/FOURCC";
  private static final String CODEC_ID_THEORA = "V_THEORA";
  private static final String CODEC_ID_VORBIS = "A_VORBIS";
  private static final String CODEC_ID_OPUS = "A_OPUS";
  private static final String CODEC_ID_AAC = "A_AAC";
  private static final String CODEC_ID_MP2 = "A_MPEG/L2";
  private static final String CODEC_ID_MP3 = "A_MPEG/L3";
  private static final String CODEC_ID_AC3 = "A_AC3";
  private static final String CODEC_ID_E_AC3 = "A_EAC3";
  private static final String CODEC_ID_TRUEHD = "A_TRUEHD";
  private static final String CODEC_ID_DTS = "A_DTS";
  private static final String CODEC_ID_DTS_EXPRESS = "A_DTS/EXPRESS";
  private static final String CODEC_ID_DTS_LOSSLESS = "A_DTS/LOSSLESS";
  private static final String CODEC_ID_FLAC = "A_FLAC";
  private static final String CODEC_ID_ACM = "A_MS/ACM";
  private static final String CODEC_ID_PCM_INT_LIT = "A_PCM/INT/LIT";
  private static final String CODEC_ID_PCM_INT_BIG = "A_PCM/INT/BIG";
  private static final String CODEC_ID_PCM_FLOAT = "A_PCM/FLOAT/IEEE";
  private static final String CODEC_ID_SUBRIP = "S_TEXT/UTF8";
  private static final String CODEC_ID_ASS = "S_TEXT/ASS";
  private static final String CODEC_ID_VTT = "S_TEXT/WEBVTT";
  private static final String CODEC_ID_VOBSUB = "S_VOBSUB";
  private static final String CODEC_ID_PGS = "S_HDMV/PGS";
  private static final String CODEC_ID_DVBSUB = "S_DVBSUB";

  private static final int VORBIS_MAX_INPUT_SIZE = 8192;
  private static final int OPUS_MAX_INPUT_SIZE = 5760;
  private static final int ENCRYPTION_IV_SIZE = 8;
  private static final int TRACK_TYPE_AUDIO = 2;

  private static final int ID_EBML = 0x1A45DFA3;
  private static final int ID_EBML_READ_VERSION = 0x42F7;
  private static final int ID_DOC_TYPE = 0x4282;
  private static final int ID_DOC_TYPE_READ_VERSION = 0x4285;
  private static final int ID_SEGMENT = 0x18538067;
  private static final int ID_SEGMENT_INFO = 0x1549A966;
  private static final int ID_SEEK_HEAD = 0x114D9B74;
  private static final int ID_SEEK = 0x4DBB;
  private static final int ID_SEEK_ID = 0x53AB;
  private static final int ID_SEEK_POSITION = 0x53AC;
  private static final int ID_INFO = 0x1549A966;
  private static final int ID_TIMECODE_SCALE = 0x2AD7B1;
  private static final int ID_DURATION = 0x4489;
  private static final int ID_CLUSTER = 0x1F43B675;
  private static final int ID_TIME_CODE = 0xE7;
  private static final int ID_SIMPLE_BLOCK = 0xA3;
  private static final int ID_BLOCK_GROUP = 0xA0;
  private static final int ID_BLOCK = 0xA1;
  private static final int ID_BLOCK_DURATION = 0x9B;
  private static final int ID_BLOCK_ADDITIONS = 0x75A1;
  private static final int ID_BLOCK_MORE = 0xA6;
  private static final int ID_BLOCK_ADD_ID = 0xEE;
  private static final int ID_BLOCK_ADDITIONAL = 0xA5;
  private static final int ID_REFERENCE_BLOCK = 0xFB;
  private static final int ID_TRACKS = 0x1654AE6B;
  private static final int ID_TRACK_ENTRY = 0xAE;
  private static final int ID_TRACK_NUMBER = 0xD7;
  private static final int ID_TRACK_TYPE = 0x83;
  private static final int ID_FLAG_DEFAULT = 0x88;
  private static final int ID_FLAG_FORCED = 0x55AA;
  private static final int ID_DEFAULT_DURATION = 0x23E383;
  private static final int ID_MAX_BLOCK_ADDITION_ID = 0x55EE;
  private static final int ID_BLOCK_ADDITION_MAPPING = 0x41E4;
  private static final int ID_BLOCK_ADD_ID_TYPE = 0x41E7;
  private static final int ID_BLOCK_ADD_ID_EXTRA_DATA = 0x41ED;
  private static final int ID_NAME = 0x536E;
  private static final int ID_CODEC_ID = 0x86;
  private static final int ID_CODEC_PRIVATE = 0x63A2;
  private static final int ID_CODEC_DELAY = 0x56AA;
  private static final int ID_SEEK_PRE_ROLL = 0x56BB;
  private static final int ID_DISCARD_PADDING = 0x75A2;
  private static final int ID_VIDEO = 0xE0;
  private static final int ID_PIXEL_WIDTH = 0xB0;
  private static final int ID_PIXEL_HEIGHT = 0xBA;
  private static final int ID_DISPLAY_WIDTH = 0x54B0;
  private static final int ID_DISPLAY_HEIGHT = 0x54BA;
  private static final int ID_DISPLAY_UNIT = 0x54B2;
  private static final int ID_AUDIO = 0xE1;
  private static final int ID_CHANNELS = 0x9F;
  private static final int ID_AUDIO_BIT_DEPTH = 0x6264;
  private static final int ID_SAMPLING_FREQUENCY = 0xB5;
  private static final int ID_CONTENT_ENCODINGS = 0x6D80;
  private static final int ID_CONTENT_ENCODING = 0x6240;
  private static final int ID_CONTENT_ENCODING_ORDER = 0x5031;
  private static final int ID_CONTENT_ENCODING_SCOPE = 0x5032;
  private static final int ID_CONTENT_COMPRESSION = 0x5034;
  private static final int ID_CONTENT_COMPRESSION_ALGORITHM = 0x4254;
  private static final int ID_CONTENT_COMPRESSION_SETTINGS = 0x4255;
  private static final int ID_CONTENT_ENCRYPTION = 0x5035;
  private static final int ID_CONTENT_ENCRYPTION_ALGORITHM = 0x47E1;
  private static final int ID_CONTENT_ENCRYPTION_KEY_ID = 0x47E2;
  private static final int ID_CONTENT_ENCRYPTION_AES_SETTINGS = 0x47E7;
  private static final int ID_CONTENT_ENCRYPTION_AES_SETTINGS_CIPHER_MODE = 0x47E8;
  private static final int ID_CUES = 0x1C53BB6B;
  private static final int ID_CUE_POINT = 0xBB;
  private static final int ID_CUE_TIME = 0xB3;
  private static final int ID_CUE_TRACK_POSITIONS = 0xB7;
  private static final int ID_CUE_CLUSTER_POSITION = 0xF1;
  private static final int ID_LANGUAGE = 0x22B59C;
  private static final int ID_PROJECTION = 0x7670;
  private static final int ID_PROJECTION_TYPE = 0x7671;
  private static final int ID_PROJECTION_PRIVATE = 0x7672;
  private static final int ID_PROJECTION_POSE_YAW = 0x7673;
  private static final int ID_PROJECTION_POSE_PITCH = 0x7674;
  private static final int ID_PROJECTION_POSE_ROLL = 0x7675;
  private static final int ID_STEREO_MODE = 0x53B8;
  private static final int ID_COLOUR = 0x55B0;
  private static final int ID_COLOUR_RANGE = 0x55B9;
  private static final int ID_COLOUR_TRANSFER = 0x55BA;
  private static final int ID_COLOUR_PRIMARIES = 0x55BB;
  private static final int ID_MAX_CLL = 0x55BC;
  private static final int ID_MAX_FALL = 0x55BD;
  private static final int ID_MASTERING_METADATA = 0x55D0;
  private static final int ID_PRIMARY_R_CHROMATICITY_X = 0x55D1;
  private static final int ID_PRIMARY_R_CHROMATICITY_Y = 0x55D2;
  private static final int ID_PRIMARY_G_CHROMATICITY_X = 0x55D3;
  private static final int ID_PRIMARY_G_CHROMATICITY_Y = 0x55D4;
  private static final int ID_PRIMARY_B_CHROMATICITY_X = 0x55D5;
  private static final int ID_PRIMARY_B_CHROMATICITY_Y = 0x55D6;
  private static final int ID_WHITE_POINT_CHROMATICITY_X = 0x55D7;
  private static final int ID_WHITE_POINT_CHROMATICITY_Y = 0x55D8;
  private static final int ID_LUMNINANCE_MAX = 0x55D9;
  private static final int ID_LUMNINANCE_MIN = 0x55DA;

  /**
   * BlockAddID value for ITU T.35 metadata in a VP9 track. See also
   * https://www.webmproject.org/docs/container/.
   */
  private static final int BLOCK_ADDITIONAL_ID_VP9_ITU_T_35 = 4;

  /**
   * BlockAddIdType value for Dolby Vision configuration with profile <= 7. See also
   * https://www.matroska.org/technical/codec_specs.html.
   */
  private static final int BLOCK_ADD_ID_TYPE_DVCC = 0x64766343;
  /**
   * BlockAddIdType value for Dolby Vision configuration with profile > 7. See also
   * https://www.matroska.org/technical/codec_specs.html.
   */
  private static final int BLOCK_ADD_ID_TYPE_DVVC = 0x64767643;

  private static final int LACING_NONE = 0;
  private static final int LACING_XIPH = 1;
  private static final int LACING_FIXED_SIZE = 2;
  private static final int LACING_EBML = 3;

  private static final int FOURCC_COMPRESSION_DIVX = 0x58564944;
  private static final int FOURCC_COMPRESSION_H263 = 0x33363248;
  private static final int FOURCC_COMPRESSION_VC1 = 0x31435657;

  /**
   * A template for the prefix that must be added to each subrip sample.
   *
   * <p>The display time of each subtitle is passed as {@code timeUs} to {@link
   * TrackOutput#sampleMetadata}. The start and end timecodes in this template are relative to
   * {@code timeUs}. Hence the start timecode is always zero. The 12 byte end timecode starting at
   * {@link #SUBRIP_PREFIX_END_TIMECODE_OFFSET} is set to a placeholder value, and must be replaced
   * with the duration of the subtitle.
   *
   * <p>Equivalent to the UTF-8 string: "1\n00:00:00,000 --> 00:00:00,000\n".
   */
  private static final byte[] SUBRIP_PREFIX =
      new byte[] {
        49, 10, 48, 48, 58, 48, 48, 58, 48, 48, 44, 48, 48, 48, 32, 45, 45, 62, 32, 48, 48, 58, 48,
        48, 58, 48, 48, 44, 48, 48, 48, 10
      };
  /** The byte offset of the end timecode in {@link #SUBRIP_PREFIX}. */
  private static final int SUBRIP_PREFIX_END_TIMECODE_OFFSET = 19;
  /**
   * The value by which to divide a time in microseconds to convert it to the unit of the last value
   * in a subrip timecode (milliseconds).
   */
  private static final long SUBRIP_TIMECODE_LAST_VALUE_SCALING_FACTOR = 1000;
  /** The format of a subrip timecode. */
  private static final String SUBRIP_TIMECODE_FORMAT = "%02d:%02d:%02d,%03d";

  /** Matroska specific format line for SSA subtitles. */
  private static final byte[] SSA_DIALOGUE_FORMAT =
      Util.getUtf8Bytes(
          "Format: Start, End, "
              + "ReadOrder, Layer, Style, Name, MarginL, MarginR, MarginV, Effect, Text");
  /**
   * A template for the prefix that must be added to each SSA sample.
   *
   * <p>The display time of each subtitle is passed as {@code timeUs} to {@link
   * TrackOutput#sampleMetadata}. The start and end timecodes in this template are relative to
   * {@code timeUs}. Hence the start timecode is always zero. The 12 byte end timecode starting at
   * {@link #SUBRIP_PREFIX_END_TIMECODE_OFFSET} is set to a placeholder value, and must be replaced
   * with the duration of the subtitle.
   *
   * <p>Equivalent to the UTF-8 string: "Dialogue: 0:00:00:00,0:00:00:00,".
   */
  private static final byte[] SSA_PREFIX =
      new byte[] {
        68, 105, 97, 108, 111, 103, 117, 101, 58, 32, 48, 58, 48, 48, 58, 48, 48, 58, 48, 48, 44,
        48, 58, 48, 48, 58, 48, 48, 58, 48, 48, 44
      };
  /** The byte offset of the end timecode in {@link #SSA_PREFIX}. */
  private static final int SSA_PREFIX_END_TIMECODE_OFFSET = 21;
  /**
   * The value by which to divide a time in microseconds to convert it to the unit of the last value
   * in an SSA timecode (1/100ths of a second).
   */
  private static final long SSA_TIMECODE_LAST_VALUE_SCALING_FACTOR = 10_000;
  /** The format of an SSA timecode. */
  private static final String SSA_TIMECODE_FORMAT = "%01d:%02d:%02d:%02d";

  /**
   * A template for the prefix that must be added to each VTT sample.
   *
   * <p>The display time of each subtitle is passed as {@code timeUs} to {@link
   * TrackOutput#sampleMetadata}. The start and end timecodes in this template are relative to
   * {@code timeUs}. Hence the start timecode is always zero. The 12 byte end timecode starting at
   * {@link #VTT_PREFIX_END_TIMECODE_OFFSET} is set to a placeholder value, and must be replaced
   * with the duration of the subtitle.
   *
   * <p>Equivalent to the UTF-8 string: "WEBVTT\n\n00:00:00.000 --> 00:00:00.000\n".
   */
  private static final byte[] VTT_PREFIX =
      new byte[] {
        87, 69, 66, 86, 84, 84, 10, 10, 48, 48, 58, 48, 48, 58, 48, 48, 46, 48, 48, 48, 32, 45, 45,
        62, 32, 48, 48, 58, 48, 48, 58, 48, 48, 46, 48, 48, 48, 10
      };
  /** The byte offset of the end timecode in {@link #VTT_PREFIX}. */
  private static final int VTT_PREFIX_END_TIMECODE_OFFSET = 25;
  /**
   * The value by which to divide a time in microseconds to convert it to the unit of the last value
   * in a VTT timecode (milliseconds).
   */
  private static final long VTT_TIMECODE_LAST_VALUE_SCALING_FACTOR = 1000;
  /** The format of a VTT timecode. */
  private static final String VTT_TIMECODE_FORMAT = "%02d:%02d:%02d.%03d";

  /** The length in bytes of a WAVEFORMATEX structure. */
  private static final int WAVE_FORMAT_SIZE = 18;
  /** Format tag indicating a WAVEFORMATEXTENSIBLE structure. */
  private static final int WAVE_FORMAT_EXTENSIBLE = 0xFFFE;
  /** Format tag for PCM. */
  private static final int WAVE_FORMAT_PCM = 1;
  /** Sub format for PCM. */
  private static final UUID WAVE_SUBFORMAT_PCM = new UUID(0x0100000000001000L, 0x800000AA00389B71L);

  /** Some HTC devices signal rotation in track names. */
  private static final Map<String, Integer> TRACK_NAME_TO_ROTATION_DEGREES;

  static {
    Map<String, Integer> trackNameToRotationDegrees = new HashMap<>();
    trackNameToRotationDegrees.put("htc_video_rotA-000", 0);
    trackNameToRotationDegrees.put("htc_video_rotA-090", 90);
    trackNameToRotationDegrees.put("htc_video_rotA-180", 180);
    trackNameToRotationDegrees.put("htc_video_rotA-270", 270);
    TRACK_NAME_TO_ROTATION_DEGREES = Collections.unmodifiableMap(trackNameToRotationDegrees);
  }

  private final EbmlReader reader;
  private final VarintReader varintReader;
  private final SparseArray<Track> tracks;
  private final boolean seekForCuesEnabled;

  // Temporary arrays.
  private final ParsableByteArray nalStartCode;
  private final ParsableByteArray nalLength;
  private final ParsableByteArray scratch;
  private final ParsableByteArray vorbisNumPageSamples;
  private final ParsableByteArray seekEntryIdBytes;
  private final ParsableByteArray sampleStrippedBytes;
  private final ParsableByteArray subtitleSample;
  private final ParsableByteArray encryptionInitializationVector;
  private final ParsableByteArray encryptionSubsampleData;
  private final ParsableByteArray supplementalData;
  private @MonotonicNonNull ByteBuffer encryptionSubsampleDataBuffer;

  private long segmentContentSize;
  private long segmentContentPosition = C.POSITION_UNSET;
  private long timecodeScale = C.TIME_UNSET;
  private long durationTimecode = C.TIME_UNSET;
  private long durationUs = C.TIME_UNSET;

  // The track corresponding to the current TrackEntry element, or null.
  @Nullable private Track currentTrack;

  // Whether a seek map has been sent to the output.
  private boolean sentSeekMap;

  // Master seek entry related elements.
  private int seekEntryId;
  private long seekEntryPosition;

  // Cue related elements.
  private boolean seekForCues;
  private long cuesContentPosition = C.POSITION_UNSET;
  private long seekPositionAfterBuildingCues = C.POSITION_UNSET;
  private long clusterTimecodeUs = C.TIME_UNSET;
  @Nullable private LongArray cueTimesUs;
  @Nullable private LongArray cueClusterPositions;
  private boolean seenClusterPositionForCurrentCuePoint;

  // Reading state.
  private boolean haveOutputSample;

  // Block reading state.
  private int blockState;
  private long blockTimeUs;
  private long blockDurationUs;
  private int blockSampleIndex;
  private int blockSampleCount;
  private int[] blockSampleSizes;
  private int blockTrackNumber;
  private int blockTrackNumberLength;
  private @C.BufferFlags int blockFlags;
  private int blockAdditionalId;
  private boolean blockHasReferenceBlock;
  private long blockGroupDiscardPaddingNs;

  // Sample writing state.
  private int sampleBytesRead;
  private int sampleBytesWritten;
  private int sampleCurrentNalBytesRemaining;
  private boolean sampleEncodingHandled;
  private boolean sampleSignalByteRead;
  private boolean samplePartitionCountRead;
  private int samplePartitionCount;
  private byte sampleSignalByte;
  private boolean sampleInitializationVectorRead;

  // Extractor outputs.
  private @MonotonicNonNull ExtractorOutput extractorOutput;

  public MatroskaExtractor() {
    this(0);
  }

  public MatroskaExtractor(@Flags int flags) {
    this(new DefaultEbmlReader(), flags);
  }

  /* package */ MatroskaExtractor(EbmlReader reader, @Flags int flags) {
    this.reader = reader;
    this.reader.init(new InnerEbmlProcessor());
    seekForCuesEnabled = (flags & FLAG_DISABLE_SEEK_FOR_CUES) == 0;
    varintReader = new VarintReader();
    tracks = new SparseArray<>();
    scratch = new ParsableByteArray(4);
    vorbisNumPageSamples = new ParsableByteArray(ByteBuffer.allocate(4).putInt(-1).array());
    seekEntryIdBytes = new ParsableByteArray(4);
    nalStartCode = new ParsableByteArray(NalUnitUtil.NAL_START_CODE);
    nalLength = new ParsableByteArray(4);
    sampleStrippedBytes = new ParsableByteArray();
    subtitleSample = new ParsableByteArray();
    encryptionInitializationVector = new ParsableByteArray(ENCRYPTION_IV_SIZE);
    encryptionSubsampleData = new ParsableByteArray();
    supplementalData = new ParsableByteArray();
    blockSampleSizes = new int[1];
  }

  @Override
  public final boolean sniff(ExtractorInput input) throws IOException {
    return new Sniffer().sniff(input);
  }

  @Override
  public final void init(ExtractorOutput output) {
    extractorOutput = output;
  }

  @CallSuper
  @Override
  public void seek(long position, long timeUs) {
    clusterTimecodeUs = C.TIME_UNSET;
    blockState = BLOCK_STATE_START;
    reader.reset();
    varintReader.reset();
    resetWriteSampleData();
    for (int i = 0; i < tracks.size(); i++) {
      tracks.valueAt(i).reset();
    }
  }

  @Override
  public final void release() {
    // Do nothing
  }

  @Override
  public final int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    haveOutputSample = false;
    boolean continueReading = true;
    while (continueReading && !haveOutputSample) {
      continueReading = reader.read(input);
      if (continueReading && maybeSeekForCues(seekPosition, input.getPosition())) {
        return Extractor.RESULT_SEEK;
      }
    }
    if (!continueReading) {
      for (int i = 0; i < tracks.size(); i++) {
        Track track = tracks.valueAt(i);
        track.assertOutputInitialized();
        track.outputPendingSampleMetadata();
      }
      return Extractor.RESULT_END_OF_INPUT;
    }
    return Extractor.RESULT_CONTINUE;
  }

  /**
   * Maps an element ID to a corresponding type.
   *
   * @see EbmlProcessor#getElementType(int)
   */
  @CallSuper
  protected @EbmlProcessor.ElementType int getElementType(int id) {
    switch (id) {
      case ID_EBML:
      case ID_SEGMENT:
      case ID_SEEK_HEAD:
      case ID_SEEK:
      case ID_INFO:
      case ID_CLUSTER:
      case ID_TRACKS:
      case ID_TRACK_ENTRY:
      case ID_BLOCK_ADDITION_MAPPING:
      case ID_AUDIO:
      case ID_VIDEO:
      case ID_CONTENT_ENCODINGS:
      case ID_CONTENT_ENCODING:
      case ID_CONTENT_COMPRESSION:
      case ID_CONTENT_ENCRYPTION:
      case ID_CONTENT_ENCRYPTION_AES_SETTINGS:
      case ID_CUES:
      case ID_CUE_POINT:
      case ID_CUE_TRACK_POSITIONS:
      case ID_BLOCK_GROUP:
      case ID_BLOCK_ADDITIONS:
      case ID_BLOCK_MORE:
      case ID_PROJECTION:
      case ID_COLOUR:
      case ID_MASTERING_METADATA:
        return EbmlProcessor.ELEMENT_TYPE_MASTER;
      case ID_EBML_READ_VERSION:
      case ID_DOC_TYPE_READ_VERSION:
      case ID_SEEK_POSITION:
      case ID_TIMECODE_SCALE:
      case ID_TIME_CODE:
      case ID_BLOCK_DURATION:
      case ID_PIXEL_WIDTH:
      case ID_PIXEL_HEIGHT:
      case ID_DISPLAY_WIDTH:
      case ID_DISPLAY_HEIGHT:
      case ID_DISPLAY_UNIT:
      case ID_TRACK_NUMBER:
      case ID_TRACK_TYPE:
      case ID_FLAG_DEFAULT:
      case ID_FLAG_FORCED:
      case ID_DEFAULT_DURATION:
      case ID_MAX_BLOCK_ADDITION_ID:
      case ID_BLOCK_ADD_ID_TYPE:
      case ID_CODEC_DELAY:
      case ID_SEEK_PRE_ROLL:
      case ID_DISCARD_PADDING:
      case ID_CHANNELS:
      case ID_AUDIO_BIT_DEPTH:
      case ID_CONTENT_ENCODING_ORDER:
      case ID_CONTENT_ENCODING_SCOPE:
      case ID_CONTENT_COMPRESSION_ALGORITHM:
      case ID_CONTENT_ENCRYPTION_ALGORITHM:
      case ID_CONTENT_ENCRYPTION_AES_SETTINGS_CIPHER_MODE:
      case ID_CUE_TIME:
      case ID_CUE_CLUSTER_POSITION:
      case ID_REFERENCE_BLOCK:
      case ID_STEREO_MODE:
      case ID_COLOUR_RANGE:
      case ID_COLOUR_TRANSFER:
      case ID_COLOUR_PRIMARIES:
      case ID_MAX_CLL:
      case ID_MAX_FALL:
      case ID_PROJECTION_TYPE:
      case ID_BLOCK_ADD_ID:
        return EbmlProcessor.ELEMENT_TYPE_UNSIGNED_INT;
      case ID_DOC_TYPE:
      case ID_NAME:
      case ID_CODEC_ID:
      case ID_LANGUAGE:
        return EbmlProcessor.ELEMENT_TYPE_STRING;
      case ID_SEEK_ID:
      case ID_BLOCK_ADD_ID_EXTRA_DATA:
      case ID_CONTENT_COMPRESSION_SETTINGS:
      case ID_CONTENT_ENCRYPTION_KEY_ID:
      case ID_SIMPLE_BLOCK:
      case ID_BLOCK:
      case ID_CODEC_PRIVATE:
      case ID_PROJECTION_PRIVATE:
      case ID_BLOCK_ADDITIONAL:
        return EbmlProcessor.ELEMENT_TYPE_BINARY;
      case ID_DURATION:
      case ID_SAMPLING_FREQUENCY:
      case ID_PRIMARY_R_CHROMATICITY_X:
      case ID_PRIMARY_R_CHROMATICITY_Y:
      case ID_PRIMARY_G_CHROMATICITY_X:
      case ID_PRIMARY_G_CHROMATICITY_Y:
      case ID_PRIMARY_B_CHROMATICITY_X:
      case ID_PRIMARY_B_CHROMATICITY_Y:
      case ID_WHITE_POINT_CHROMATICITY_X:
      case ID_WHITE_POINT_CHROMATICITY_Y:
      case ID_LUMNINANCE_MAX:
      case ID_LUMNINANCE_MIN:
      case ID_PROJECTION_POSE_YAW:
      case ID_PROJECTION_POSE_PITCH:
      case ID_PROJECTION_POSE_ROLL:
        return EbmlProcessor.ELEMENT_TYPE_FLOAT;
      default:
        return EbmlProcessor.ELEMENT_TYPE_UNKNOWN;
    }
  }

  /**
   * Checks if the given id is that of a level 1 element.
   *
   * @see EbmlProcessor#isLevel1Element(int)
   */
  @CallSuper
  protected boolean isLevel1Element(int id) {
    return id == ID_SEGMENT_INFO || id == ID_CLUSTER || id == ID_CUES || id == ID_TRACKS;
  }

  /**
   * Called when the start of a master element is encountered.
   *
   * @see EbmlProcessor#startMasterElement(int, long, long)
   */
  @CallSuper
  protected void startMasterElement(int id, long contentPosition, long contentSize)
      throws ParserException {
    assertInitialized();
    switch (id) {
      case ID_SEGMENT:
        if (segmentContentPosition != C.POSITION_UNSET
            && segmentContentPosition != contentPosition) {
          throw ParserException.createForMalformedContainer(
              "Multiple Segment elements not supported", /* cause= */ null);
        }
        segmentContentPosition = contentPosition;
        segmentContentSize = contentSize;
        break;
      case ID_SEEK:
        seekEntryId = UNSET_ENTRY_ID;
        seekEntryPosition = C.POSITION_UNSET;
        break;
      case ID_CUES:
        cueTimesUs = new LongArray();
        cueClusterPositions = new LongArray();
        break;
      case ID_CUE_POINT:
        seenClusterPositionForCurrentCuePoint = false;
        break;
      case ID_CLUSTER:
        if (!sentSeekMap) {
          // We need to build cues before parsing the cluster.
          if (seekForCuesEnabled && cuesContentPosition != C.POSITION_UNSET) {
            // We know where the Cues element is located. Seek to request it.
            seekForCues = true;
          } else {
            // We don't know where the Cues element is located. It's most likely omitted. Allow
            // playback, but disable seeking.
            extractorOutput.seekMap(new SeekMap.Unseekable(durationUs));
            sentSeekMap = true;
          }
        }
        break;
      case ID_BLOCK_GROUP:
        blockHasReferenceBlock = false;
        blockGroupDiscardPaddingNs = 0L;
        break;
      case ID_CONTENT_ENCODING:
        // TODO: check and fail if more than one content encoding is present.
        break;
      case ID_CONTENT_ENCRYPTION:
        getCurrentTrack(id).hasContentEncryption = true;
        break;
      case ID_TRACK_ENTRY:
        currentTrack = new Track();
        break;
      case ID_MASTERING_METADATA:
        getCurrentTrack(id).hasColorInfo = true;
        break;
      default:
        break;
    }
  }

  /**
   * Called when the end of a master element is encountered.
   *
   * @see EbmlProcessor#endMasterElement(int)
   */
  @CallSuper
  protected void endMasterElement(int id) throws ParserException {
    assertInitialized();
    switch (id) {
      case ID_SEGMENT_INFO:
        if (timecodeScale == C.TIME_UNSET) {
          // timecodeScale was omitted. Use the default value.
          timecodeScale = 1000000;
        }
        if (durationTimecode != C.TIME_UNSET) {
          durationUs = scaleTimecodeToUs(durationTimecode);
        }
        break;
      case ID_SEEK:
        if (seekEntryId == UNSET_ENTRY_ID || seekEntryPosition == C.POSITION_UNSET) {
          throw ParserException.createForMalformedContainer(
              "Mandatory element SeekID or SeekPosition not found", /* cause= */ null);
        }
        if (seekEntryId == ID_CUES) {
          cuesContentPosition = seekEntryPosition;
        }
        break;
      case ID_CUES:
        if (!sentSeekMap) {
          extractorOutput.seekMap(buildSeekMap(cueTimesUs, cueClusterPositions));
          sentSeekMap = true;
        } else {
          // We have already built the cues. Ignore.
        }
        this.cueTimesUs = null;
        this.cueClusterPositions = null;
        break;
      case ID_BLOCK_GROUP:
        if (blockState != BLOCK_STATE_DATA) {
          // We've skipped this block (due to incompatible track number).
          return;
        }
        Track track = tracks.get(blockTrackNumber);
        track.assertOutputInitialized();
        if (blockGroupDiscardPaddingNs > 0L && CODEC_ID_OPUS.equals(track.codecId)) {
          // For Opus, attach DiscardPadding to the block group samples as supplemental data.
          supplementalData.reset(
              ByteBuffer.allocate(8)
                  .order(ByteOrder.LITTLE_ENDIAN)
                  .putLong(blockGroupDiscardPaddingNs)
                  .array());
        }

        // Commit sample metadata.
        int sampleOffset = 0;
        for (int i = 0; i < blockSampleCount; i++) {
          sampleOffset += blockSampleSizes[i];
        }
        for (int i = 0; i < blockSampleCount; i++) {
          long sampleTimeUs = blockTimeUs + (i * track.defaultSampleDurationNs) / 1000;
          int sampleFlags = blockFlags;
          if (i == 0 && !blockHasReferenceBlock) {
            // If the ReferenceBlock element was not found in this block, then the first frame is a
            // keyframe.
            sampleFlags |= C.BUFFER_FLAG_KEY_FRAME;
          }
          int sampleSize = blockSampleSizes[i];
          sampleOffset -= sampleSize; // The offset is to the end of the sample.
          commitSampleToOutput(track, sampleTimeUs, sampleFlags, sampleSize, sampleOffset);
        }
        blockState = BLOCK_STATE_START;
        break;
      case ID_CONTENT_ENCODING:
        assertInTrackEntry(id);
        if (currentTrack.hasContentEncryption) {
          if (currentTrack.cryptoData == null) {
            throw ParserException.createForMalformedContainer(
                "Encrypted Track found but ContentEncKeyID was not found", /* cause= */ null);
          }
          currentTrack.drmInitData =
              new DrmInitData(
                  new SchemeData(
                      C.UUID_NIL, MimeTypes.VIDEO_WEBM, currentTrack.cryptoData.encryptionKey));
        }
        break;
      case ID_CONTENT_ENCODINGS:
        assertInTrackEntry(id);
        if (currentTrack.hasContentEncryption && currentTrack.sampleStrippedBytes != null) {
          throw ParserException.createForMalformedContainer(
              "Combining encryption and compression is not supported", /* cause= */ null);
        }
        break;
      case ID_TRACK_ENTRY:
        Track currentTrack = checkStateNotNull(this.currentTrack);
        if (currentTrack.codecId == null) {
          throw ParserException.createForMalformedContainer(
              "CodecId is missing in TrackEntry element", /* cause= */ null);
        } else {
          if (isCodecSupported(currentTrack.codecId)) {
            currentTrack.initializeOutput(extractorOutput, currentTrack.number);
            tracks.put(currentTrack.number, currentTrack);
          }
        }
        this.currentTrack = null;
        break;
      case ID_TRACKS:
        if (tracks.size() == 0) {
          throw ParserException.createForMalformedContainer(
              "No valid tracks were found", /* cause= */ null);
        }
        extractorOutput.endTracks();
        break;
      default:
        break;
    }
  }

  /**
   * Called when an integer element is encountered.
   *
   * @see EbmlProcessor#integerElement(int, long)
   */
  @CallSuper
  protected void integerElement(int id, long value) throws ParserException {
    switch (id) {
      case ID_EBML_READ_VERSION:
        // Validate that EBMLReadVersion is supported. This extractor only supports v1.
        if (value != 1) {
          throw ParserException.createForMalformedContainer(
              "EBMLReadVersion " + value + " not supported", /* cause= */ null);
        }
        break;
      case ID_DOC_TYPE_READ_VERSION:
        // Validate that DocTypeReadVersion is supported. This extractor only supports up to v2.
        if (value < 1 || value > 2) {
          throw ParserException.createForMalformedContainer(
              "DocTypeReadVersion " + value + " not supported", /* cause= */ null);
        }
        break;
      case ID_SEEK_POSITION:
        // Seek Position is the relative offset beginning from the Segment. So to get absolute
        // offset from the beginning of the file, we need to add segmentContentPosition to it.
        seekEntryPosition = value + segmentContentPosition;
        break;
      case ID_TIMECODE_SCALE:
        timecodeScale = value;
        break;
      case ID_PIXEL_WIDTH:
        getCurrentTrack(id).width = (int) value;
        break;
      case ID_PIXEL_HEIGHT:
        getCurrentTrack(id).height = (int) value;
        break;
      case ID_DISPLAY_WIDTH:
        getCurrentTrack(id).displayWidth = (int) value;
        break;
      case ID_DISPLAY_HEIGHT:
        getCurrentTrack(id).displayHeight = (int) value;
        break;
      case ID_DISPLAY_UNIT:
        getCurrentTrack(id).displayUnit = (int) value;
        break;
      case ID_TRACK_NUMBER:
        getCurrentTrack(id).number = (int) value;
        break;
      case ID_FLAG_DEFAULT:
        getCurrentTrack(id).flagDefault = value == 1;
        break;
      case ID_FLAG_FORCED:
        getCurrentTrack(id).flagForced = value == 1;
        break;
      case ID_TRACK_TYPE:
        getCurrentTrack(id).type = (int) value;
        break;
      case ID_DEFAULT_DURATION:
        getCurrentTrack(id).defaultSampleDurationNs = (int) value;
        break;
      case ID_MAX_BLOCK_ADDITION_ID:
        getCurrentTrack(id).maxBlockAdditionId = (int) value;
        break;
      case ID_BLOCK_ADD_ID_TYPE:
        getCurrentTrack(id).blockAddIdType = (int) value;
        break;
      case ID_CODEC_DELAY:
        getCurrentTrack(id).codecDelayNs = value;
        break;
      case ID_SEEK_PRE_ROLL:
        getCurrentTrack(id).seekPreRollNs = value;
        break;
      case ID_DISCARD_PADDING:
        blockGroupDiscardPaddingNs = value;
        break;
      case ID_CHANNELS:
        getCurrentTrack(id).channelCount = (int) value;
        break;
      case ID_AUDIO_BIT_DEPTH:
        getCurrentTrack(id).audioBitDepth = (int) value;
        break;
      case ID_REFERENCE_BLOCK:
        blockHasReferenceBlock = true;
        break;
      case ID_CONTENT_ENCODING_ORDER:
        // This extractor only supports one ContentEncoding element and hence the order has to be 0.
        if (value != 0) {
          throw ParserException.createForMalformedContainer(
              "ContentEncodingOrder " + value + " not supported", /* cause= */ null);
        }
        break;
      case ID_CONTENT_ENCODING_SCOPE:
        // This extractor only supports the scope of all frames.
        if (value != 1) {
          throw ParserException.createForMalformedContainer(
              "ContentEncodingScope " + value + " not supported", /* cause= */ null);
        }
        break;
      case ID_CONTENT_COMPRESSION_ALGORITHM:
        // This extractor only supports header stripping.
        if (value != 3) {
          throw ParserException.createForMalformedContainer(
              "ContentCompAlgo " + value + " not supported", /* cause= */ null);
        }
        break;
      case ID_CONTENT_ENCRYPTION_ALGORITHM:
        // Only the value 5 (AES) is allowed according to the WebM specification.
        if (value != 5) {
          throw ParserException.createForMalformedContainer(
              "ContentEncAlgo " + value + " not supported", /* cause= */ null);
        }
        break;
      case ID_CONTENT_ENCRYPTION_AES_SETTINGS_CIPHER_MODE:
        // Only the value 1 is allowed according to the WebM specification.
        if (value != 1) {
          throw ParserException.createForMalformedContainer(
              "AESSettingsCipherMode " + value + " not supported", /* cause= */ null);
        }
        break;
      case ID_CUE_TIME:
        assertInCues(id);
        cueTimesUs.add(scaleTimecodeToUs(value));
        break;
      case ID_CUE_CLUSTER_POSITION:
        if (!seenClusterPositionForCurrentCuePoint) {
          assertInCues(id);
          // If there's more than one video/audio track, then there could be more than one
          // CueTrackPositions within a single CuePoint. In such a case, ignore all but the first
          // one (since the cluster position will be quite close for all the tracks).
          cueClusterPositions.add(value);
          seenClusterPositionForCurrentCuePoint = true;
        }
        break;
      case ID_TIME_CODE:
        clusterTimecodeUs = scaleTimecodeToUs(value);
        break;
      case ID_BLOCK_DURATION:
        blockDurationUs = scaleTimecodeToUs(value);
        break;
      case ID_STEREO_MODE:
        int layout = (int) value;
        assertInTrackEntry(id);
        switch (layout) {
          case 0:
            currentTrack.stereoMode = C.STEREO_MODE_MONO;
            break;
          case 1:
            currentTrack.stereoMode = C.STEREO_MODE_LEFT_RIGHT;
            break;
          case 3:
            currentTrack.stereoMode = C.STEREO_MODE_TOP_BOTTOM;
            break;
          case 15:
            currentTrack.stereoMode = C.STEREO_MODE_STEREO_MESH;
            break;
          default:
            break;
        }
        break;
      case ID_COLOUR_PRIMARIES:
        assertInTrackEntry(id);
        currentTrack.hasColorInfo = true;
        int colorSpace = ColorInfo.isoColorPrimariesToColorSpace((int) value);
        if (colorSpace != Format.NO_VALUE) {
          currentTrack.colorSpace = colorSpace;
        }
        break;
      case ID_COLOUR_TRANSFER:
        assertInTrackEntry(id);
        int colorTransfer = ColorInfo.isoTransferCharacteristicsToColorTransfer((int) value);
        if (colorTransfer != Format.NO_VALUE) {
          currentTrack.colorTransfer = colorTransfer;
        }
        break;
      case ID_COLOUR_RANGE:
        assertInTrackEntry(id);
        switch ((int) value) {
          case 1: // Broadcast range.
            currentTrack.colorRange = C.COLOR_RANGE_LIMITED;
            break;
          case 2:
            currentTrack.colorRange = C.COLOR_RANGE_FULL;
            break;
          default:
            break;
        }
        break;
      case ID_MAX_CLL:
        getCurrentTrack(id).maxContentLuminance = (int) value;
        break;
      case ID_MAX_FALL:
        getCurrentTrack(id).maxFrameAverageLuminance = (int) value;
        break;
      case ID_PROJECTION_TYPE:
        assertInTrackEntry(id);
        switch ((int) value) {
          case 0:
            currentTrack.projectionType = C.PROJECTION_RECTANGULAR;
            break;
          case 1:
            currentTrack.projectionType = C.PROJECTION_EQUIRECTANGULAR;
            break;
          case 2:
            currentTrack.projectionType = C.PROJECTION_CUBEMAP;
            break;
          case 3:
            currentTrack.projectionType = C.PROJECTION_MESH;
            break;
          default:
            break;
        }
        break;
      case ID_BLOCK_ADD_ID:
        blockAdditionalId = (int) value;
        break;
      default:
        break;
    }
  }

  /**
   * Called when a float element is encountered.
   *
   * @see EbmlProcessor#floatElement(int, double)
   */
  @CallSuper
  protected void floatElement(int id, double value) throws ParserException {
    switch (id) {
      case ID_DURATION:
        durationTimecode = (long) value;
        break;
      case ID_SAMPLING_FREQUENCY:
        getCurrentTrack(id).sampleRate = (int) value;
        break;
      case ID_PRIMARY_R_CHROMATICITY_X:
        getCurrentTrack(id).primaryRChromaticityX = (float) value;
        break;
      case ID_PRIMARY_R_CHROMATICITY_Y:
        getCurrentTrack(id).primaryRChromaticityY = (float) value;
        break;
      case ID_PRIMARY_G_CHROMATICITY_X:
        getCurrentTrack(id).primaryGChromaticityX = (float) value;
        break;
      case ID_PRIMARY_G_CHROMATICITY_Y:
        getCurrentTrack(id).primaryGChromaticityY = (float) value;
        break;
      case ID_PRIMARY_B_CHROMATICITY_X:
        getCurrentTrack(id).primaryBChromaticityX = (float) value;
        break;
      case ID_PRIMARY_B_CHROMATICITY_Y:
        getCurrentTrack(id).primaryBChromaticityY = (float) value;
        break;
      case ID_WHITE_POINT_CHROMATICITY_X:
        getCurrentTrack(id).whitePointChromaticityX = (float) value;
        break;
      case ID_WHITE_POINT_CHROMATICITY_Y:
        getCurrentTrack(id).whitePointChromaticityY = (float) value;
        break;
      case ID_LUMNINANCE_MAX:
        getCurrentTrack(id).maxMasteringLuminance = (float) value;
        break;
      case ID_LUMNINANCE_MIN:
        getCurrentTrack(id).minMasteringLuminance = (float) value;
        break;
      case ID_PROJECTION_POSE_YAW:
        getCurrentTrack(id).projectionPoseYaw = (float) value;
        break;
      case ID_PROJECTION_POSE_PITCH:
        getCurrentTrack(id).projectionPosePitch = (float) value;
        break;
      case ID_PROJECTION_POSE_ROLL:
        getCurrentTrack(id).projectionPoseRoll = (float) value;
        break;
      default:
        break;
    }
  }

  /**
   * Called when a string element is encountered.
   *
   * @see EbmlProcessor#stringElement(int, String)
   */
  @CallSuper
  protected void stringElement(int id, String value) throws ParserException {
    switch (id) {
      case ID_DOC_TYPE:
        // Validate that DocType is supported.
        if (!DOC_TYPE_WEBM.equals(value) && !DOC_TYPE_MATROSKA.equals(value)) {
          throw ParserException.createForMalformedContainer(
              "DocType " + value + " not supported", /* cause= */ null);
        }
        break;
      case ID_NAME:
        getCurrentTrack(id).name = value;
        break;
      case ID_CODEC_ID:
        getCurrentTrack(id).codecId = value;
        break;
      case ID_LANGUAGE:
        getCurrentTrack(id).language = value;
        break;
      default:
        break;
    }
  }

  /**
   * Called when a binary element is encountered.
   *
   * @see EbmlProcessor#binaryElement(int, int, ExtractorInput)
   */
  @CallSuper
  protected void binaryElement(int id, int contentSize, ExtractorInput input) throws IOException {
    switch (id) {
      case ID_SEEK_ID:
        Arrays.fill(seekEntryIdBytes.getData(), (byte) 0);
        input.readFully(seekEntryIdBytes.getData(), 4 - contentSize, contentSize);
        seekEntryIdBytes.setPosition(0);
        seekEntryId = (int) seekEntryIdBytes.readUnsignedInt();
        break;
      case ID_BLOCK_ADD_ID_EXTRA_DATA:
        handleBlockAddIDExtraData(getCurrentTrack(id), input, contentSize);
        break;
      case ID_CODEC_PRIVATE:
        assertInTrackEntry(id);
        currentTrack.codecPrivate = new byte[contentSize];
        input.readFully(currentTrack.codecPrivate, 0, contentSize);
        break;
      case ID_PROJECTION_PRIVATE:
        assertInTrackEntry(id);
        currentTrack.projectionData = new byte[contentSize];
        input.readFully(currentTrack.projectionData, 0, contentSize);
        break;
      case ID_CONTENT_COMPRESSION_SETTINGS:
        assertInTrackEntry(id);
        // This extractor only supports header stripping, so the payload is the stripped bytes.
        currentTrack.sampleStrippedBytes = new byte[contentSize];
        input.readFully(currentTrack.sampleStrippedBytes, 0, contentSize);
        break;
      case ID_CONTENT_ENCRYPTION_KEY_ID:
        byte[] encryptionKey = new byte[contentSize];
        input.readFully(encryptionKey, 0, contentSize);
        getCurrentTrack(id).cryptoData =
            new TrackOutput.CryptoData(
                C.CRYPTO_MODE_AES_CTR, encryptionKey, 0, 0); // We assume patternless AES-CTR.
        break;
      case ID_SIMPLE_BLOCK:
      case ID_BLOCK:
        // Please refer to http://www.matroska.org/technical/specs/index.html#simpleblock_structure
        // and http://matroska.org/technical/specs/index.html#block_structure
        // for info about how data is organized in SimpleBlock and Block elements respectively. They
        // differ only in the way flags are specified.

        if (blockState == BLOCK_STATE_START) {
          blockTrackNumber = (int) varintReader.readUnsignedVarint(input, false, true, 8);
          blockTrackNumberLength = varintReader.getLastLength();
          blockDurationUs = C.TIME_UNSET;
          blockState = BLOCK_STATE_HEADER;
          scratch.reset(/* limit= */ 0);
        }

        Track track = tracks.get(blockTrackNumber);

        // Ignore the block if we don't know about the track to which it belongs.
        if (track == null) {
          input.skipFully(contentSize - blockTrackNumberLength);
          blockState = BLOCK_STATE_START;
          return;
        }

        track.assertOutputInitialized();

        if (blockState == BLOCK_STATE_HEADER) {
          // Read the relative timecode (2 bytes) and flags (1 byte).
          readScratch(input, 3);
          int lacing = (scratch.getData()[2] & 0x06) >> 1;
          if (lacing == LACING_NONE) {
            blockSampleCount = 1;
            blockSampleSizes = ensureArrayCapacity(blockSampleSizes, 1);
            blockSampleSizes[0] = contentSize - blockTrackNumberLength - 3;
          } else {
            // Read the sample count (1 byte).
            readScratch(input, 4);
            blockSampleCount = (scratch.getData()[3] & 0xFF) + 1;
            blockSampleSizes = ensureArrayCapacity(blockSampleSizes, blockSampleCount);
            if (lacing == LACING_FIXED_SIZE) {
              int blockLacingSampleSize =
                  (contentSize - blockTrackNumberLength - 4) / blockSampleCount;
              Arrays.fill(blockSampleSizes, 0, blockSampleCount, blockLacingSampleSize);
            } else if (lacing == LACING_XIPH) {
              int totalSamplesSize = 0;
              int headerSize = 4;
              for (int sampleIndex = 0; sampleIndex < blockSampleCount - 1; sampleIndex++) {
                blockSampleSizes[sampleIndex] = 0;
                int byteValue;
                do {
                  readScratch(input, ++headerSize);
                  byteValue = scratch.getData()[headerSize - 1] & 0xFF;
                  blockSampleSizes[sampleIndex] += byteValue;
                } while (byteValue == 0xFF);
                totalSamplesSize += blockSampleSizes[sampleIndex];
              }
              blockSampleSizes[blockSampleCount - 1] =
                  contentSize - blockTrackNumberLength - headerSize - totalSamplesSize;
            } else if (lacing == LACING_EBML) {
              int totalSamplesSize = 0;
              int headerSize = 4;
              for (int sampleIndex = 0; sampleIndex < blockSampleCount - 1; sampleIndex++) {
                blockSampleSizes[sampleIndex] = 0;
                readScratch(input, ++headerSize);
                if (scratch.getData()[headerSize - 1] == 0) {
                  throw ParserException.createForMalformedContainer(
                      "No valid varint length mask found", /* cause= */ null);
                }
                long readValue = 0;
                for (int i = 0; i < 8; i++) {
                  int lengthMask = 1 << (7 - i);
                  if ((scratch.getData()[headerSize - 1] & lengthMask) != 0) {
                    int readPosition = headerSize - 1;
                    headerSize += i;
                    readScratch(input, headerSize);
                    readValue = (scratch.getData()[readPosition++] & 0xFF) & ~lengthMask;
                    while (readPosition < headerSize) {
                      readValue <<= 8;
                      readValue |= (scratch.getData()[readPosition++] & 0xFF);
                    }
                    // The first read value is the first size. Later values are signed offsets.
                    if (sampleIndex > 0) {
                      readValue -= (1L << (6 + i * 7)) - 1;
                    }
                    break;
                  }
                }
                if (readValue < Integer.MIN_VALUE || readValue > Integer.MAX_VALUE) {
                  throw ParserException.createForMalformedContainer(
                      "EBML lacing sample size out of range.", /* cause= */ null);
                }
                int intReadValue = (int) readValue;
                blockSampleSizes[sampleIndex] =
                    sampleIndex == 0
                        ? intReadValue
                        : blockSampleSizes[sampleIndex - 1] + intReadValue;
                totalSamplesSize += blockSampleSizes[sampleIndex];
              }
              blockSampleSizes[blockSampleCount - 1] =
                  contentSize - blockTrackNumberLength - headerSize - totalSamplesSize;
            } else {
              // Lacing is always in the range 0--3.
              throw ParserException.createForMalformedContainer(
                  "Unexpected lacing value: " + lacing, /* cause= */ null);
            }
          }

          int timecode = (scratch.getData()[0] << 8) | (scratch.getData()[1] & 0xFF);
          blockTimeUs = clusterTimecodeUs + scaleTimecodeToUs(timecode);
          boolean isKeyframe =
              track.type == TRACK_TYPE_AUDIO
                  || (id == ID_SIMPLE_BLOCK && (scratch.getData()[2] & 0x80) == 0x80);
          blockFlags = isKeyframe ? C.BUFFER_FLAG_KEY_FRAME : 0;
          blockState = BLOCK_STATE_DATA;
          blockSampleIndex = 0;
        }

        if (id == ID_SIMPLE_BLOCK) {
          // For SimpleBlock, we can write sample data and immediately commit the corresponding
          // sample metadata.
          while (blockSampleIndex < blockSampleCount) {
            int sampleSize =
                writeSampleData(
                    input, track, blockSampleSizes[blockSampleIndex], /* isBlockGroup= */ false);
            long sampleTimeUs =
                blockTimeUs + (blockSampleIndex * track.defaultSampleDurationNs) / 1000;
            commitSampleToOutput(track, sampleTimeUs, blockFlags, sampleSize, /* offset= */ 0);
            blockSampleIndex++;
          }
          blockState = BLOCK_STATE_START;
        } else {
          // For Block, we need to wait until the end of the BlockGroup element before committing
          // sample metadata. This is so that we can handle ReferenceBlock (which can be used to
          // infer whether the first sample in the block is a keyframe), and BlockAdditions (which
          // can contain additional sample data to append) contained in the block group. Just output
          // the sample data, storing the final sample sizes for when we commit the metadata.
          while (blockSampleIndex < blockSampleCount) {
            blockSampleSizes[blockSampleIndex] =
                writeSampleData(
                    input, track, blockSampleSizes[blockSampleIndex], /* isBlockGroup= */ true);
            blockSampleIndex++;
          }
        }

        break;
      case ID_BLOCK_ADDITIONAL:
        if (blockState != BLOCK_STATE_DATA) {
          return;
        }
        handleBlockAdditionalData(
            tracks.get(blockTrackNumber), blockAdditionalId, input, contentSize);
        break;
      default:
        throw ParserException.createForMalformedContainer(
            "Unexpected id: " + id, /* cause= */ null);
    }
  }

  protected void handleBlockAddIDExtraData(Track track, ExtractorInput input, int contentSize)
      throws IOException {
    if (track.blockAddIdType == BLOCK_ADD_ID_TYPE_DVVC
        || track.blockAddIdType == BLOCK_ADD_ID_TYPE_DVCC) {
      track.dolbyVisionConfigBytes = new byte[contentSize];
      input.readFully(track.dolbyVisionConfigBytes, 0, contentSize);
    } else {
      // Unhandled BlockAddIDExtraData.
      input.skipFully(contentSize);
    }
  }

  protected void handleBlockAdditionalData(
      Track track, int blockAdditionalId, ExtractorInput input, int contentSize)
      throws IOException {
    if (blockAdditionalId == BLOCK_ADDITIONAL_ID_VP9_ITU_T_35
        && CODEC_ID_VP9.equals(track.codecId)) {
      supplementalData.reset(contentSize);
      input.readFully(supplementalData.getData(), 0, contentSize);
    } else {
      // Unhandled block additional data.
      input.skipFully(contentSize);
    }
  }

  @EnsuresNonNull("currentTrack")
  private void assertInTrackEntry(int id) throws ParserException {
    if (currentTrack == null) {
      throw ParserException.createForMalformedContainer(
          "Element " + id + " must be in a TrackEntry", /* cause= */ null);
    }
  }

  @EnsuresNonNull({"cueTimesUs", "cueClusterPositions"})
  private void assertInCues(int id) throws ParserException {
    if (cueTimesUs == null || cueClusterPositions == null) {
      throw ParserException.createForMalformedContainer(
          "Element " + id + " must be in a Cues", /* cause= */ null);
    }
  }

  /**
   * Returns the track corresponding to the current TrackEntry element.
   *
   * @throws ParserException if the element id is not in a TrackEntry.
   */
  protected Track getCurrentTrack(int currentElementId) throws ParserException {
    assertInTrackEntry(currentElementId);
    return currentTrack;
  }

  @RequiresNonNull("#1.output")
  private void commitSampleToOutput(
      Track track, long timeUs, @C.BufferFlags int flags, int size, int offset) {
    if (track.trueHdSampleRechunker != null) {
      track.trueHdSampleRechunker.sampleMetadata(
          track.output, timeUs, flags, size, offset, track.cryptoData);
    } else {
      if (CODEC_ID_SUBRIP.equals(track.codecId)
          || CODEC_ID_ASS.equals(track.codecId)
          || CODEC_ID_VTT.equals(track.codecId)) {
        if (blockSampleCount > 1) {
          Log.w(TAG, "Skipping subtitle sample in laced block.");
        } else if (blockDurationUs == C.TIME_UNSET) {
          Log.w(TAG, "Skipping subtitle sample with no duration.");
        } else {
          setSubtitleEndTime(track.codecId, blockDurationUs, subtitleSample.getData());
          // The Matroska spec doesn't clearly define whether subtitle samples are null-terminated
          // or the sample should instead be sized precisely. We truncate the sample at a null-byte
          // to gracefully handle null-terminated strings followed by garbage bytes.
          for (int i = subtitleSample.getPosition(); i < subtitleSample.limit(); i++) {
            if (subtitleSample.getData()[i] == 0) {
              subtitleSample.setLimit(i);
              break;
            }
          }
          // Note: If we ever want to support DRM protected subtitles then we'll need to output the
          // appropriate encryption data here.
          track.output.sampleData(subtitleSample, subtitleSample.limit());
          size += subtitleSample.limit();
        }
      }

      if ((flags & C.BUFFER_FLAG_HAS_SUPPLEMENTAL_DATA) != 0) {
        if (blockSampleCount > 1) {
          // There were multiple samples in the block. Appending the additional data to the last
          // sample doesn't make sense. Skip instead.
          supplementalData.reset(/* limit= */ 0);
        } else {
          // Append supplemental data.
          int supplementalDataSize = supplementalData.limit();
          track.output.sampleData(
              supplementalData, supplementalDataSize, TrackOutput.SAMPLE_DATA_PART_SUPPLEMENTAL);
          size += supplementalDataSize;
        }
      }
      track.output.sampleMetadata(timeUs, flags, size, offset, track.cryptoData);
    }
    haveOutputSample = true;
  }

  /**
   * Ensures {@link #scratch} contains at least {@code requiredLength} bytes of data, reading from
   * the extractor input if necessary.
   */
  private void readScratch(ExtractorInput input, int requiredLength) throws IOException {
    if (scratch.limit() >= requiredLength) {
      return;
    }
    if (scratch.capacity() < requiredLength) {
      scratch.ensureCapacity(max(scratch.capacity() * 2, requiredLength));
    }
    input.readFully(scratch.getData(), scratch.limit(), requiredLength - scratch.limit());
    scratch.setLimit(requiredLength);
  }

  /**
   * Writes data for a single sample to the track output.
   *
   * @param input The input from which to read sample data.
   * @param track The track to output the sample to.
   * @param size The size of the sample data on the input side.
   * @param isBlockGroup Whether the samples are from a BlockGroup.
   * @return The final size of the written sample.
   * @throws IOException If an error occurs reading from the input.
   */
  @RequiresNonNull("#2.output")
  private int writeSampleData(ExtractorInput input, Track track, int size, boolean isBlockGroup)
      throws IOException {
    if (CODEC_ID_SUBRIP.equals(track.codecId)) {
      writeSubtitleSampleData(input, SUBRIP_PREFIX, size);
      return finishWriteSampleData();
    } else if (CODEC_ID_ASS.equals(track.codecId)) {
      writeSubtitleSampleData(input, SSA_PREFIX, size);
      return finishWriteSampleData();
    } else if (CODEC_ID_VTT.equals(track.codecId)) {
      writeSubtitleSampleData(input, VTT_PREFIX, size);
      return finishWriteSampleData();
    }

    TrackOutput output = track.output;
    if (!sampleEncodingHandled) {
      if (track.hasContentEncryption) {
        // If the sample is encrypted, read its encryption signal byte and set the IV size.
        // Clear the encrypted flag.
        blockFlags &= ~C.BUFFER_FLAG_ENCRYPTED;
        if (!sampleSignalByteRead) {
          input.readFully(scratch.getData(), 0, 1);
          sampleBytesRead++;
          if ((scratch.getData()[0] & 0x80) == 0x80) {
            throw ParserException.createForMalformedContainer(
                "Extension bit is set in signal byte", /* cause= */ null);
          }
          sampleSignalByte = scratch.getData()[0];
          sampleSignalByteRead = true;
        }
        boolean isEncrypted = (sampleSignalByte & 0x01) == 0x01;
        if (isEncrypted) {
          boolean hasSubsampleEncryption = (sampleSignalByte & 0x02) == 0x02;
          blockFlags |= C.BUFFER_FLAG_ENCRYPTED;
          if (!sampleInitializationVectorRead) {
            input.readFully(encryptionInitializationVector.getData(), 0, ENCRYPTION_IV_SIZE);
            sampleBytesRead += ENCRYPTION_IV_SIZE;
            sampleInitializationVectorRead = true;
            // Write the signal byte, containing the IV size and the subsample encryption flag.
            scratch.getData()[0] =
                (byte) (ENCRYPTION_IV_SIZE | (hasSubsampleEncryption ? 0x80 : 0x00));
            scratch.setPosition(0);
            output.sampleData(scratch, 1, TrackOutput.SAMPLE_DATA_PART_ENCRYPTION);
            sampleBytesWritten++;
            // Write the IV.
            encryptionInitializationVector.setPosition(0);
            output.sampleData(
                encryptionInitializationVector,
                ENCRYPTION_IV_SIZE,
                TrackOutput.SAMPLE_DATA_PART_ENCRYPTION);
            sampleBytesWritten += ENCRYPTION_IV_SIZE;
          }
          if (hasSubsampleEncryption) {
            if (!samplePartitionCountRead) {
              input.readFully(scratch.getData(), 0, 1);
              sampleBytesRead++;
              scratch.setPosition(0);
              samplePartitionCount = scratch.readUnsignedByte();
              samplePartitionCountRead = true;
            }
            int samplePartitionDataSize = samplePartitionCount * 4;
            scratch.reset(samplePartitionDataSize);
            input.readFully(scratch.getData(), 0, samplePartitionDataSize);
            sampleBytesRead += samplePartitionDataSize;
            short subsampleCount = (short) (1 + (samplePartitionCount / 2));
            int subsampleDataSize = 2 + 6 * subsampleCount;
            if (encryptionSubsampleDataBuffer == null
                || encryptionSubsampleDataBuffer.capacity() < subsampleDataSize) {
              encryptionSubsampleDataBuffer = ByteBuffer.allocate(subsampleDataSize);
            }
            encryptionSubsampleDataBuffer.position(0);
            encryptionSubsampleDataBuffer.putShort(subsampleCount);
            // Loop through the partition offsets and write out the data in the way ExoPlayer
            // wants it (ISO 23001-7 Part 7):
            //   2 bytes - sub sample count.
            //   for each sub sample:
            //     2 bytes - clear data size.
            //     4 bytes - encrypted data size.
            int partitionOffset = 0;
            for (int i = 0; i < samplePartitionCount; i++) {
              int previousPartitionOffset = partitionOffset;
              partitionOffset = scratch.readUnsignedIntToInt();
              if ((i % 2) == 0) {
                encryptionSubsampleDataBuffer.putShort(
                    (short) (partitionOffset - previousPartitionOffset));
              } else {
                encryptionSubsampleDataBuffer.putInt(partitionOffset - previousPartitionOffset);
              }
            }
            int finalPartitionSize = size - sampleBytesRead - partitionOffset;
            if ((samplePartitionCount % 2) == 1) {
              encryptionSubsampleDataBuffer.putInt(finalPartitionSize);
            } else {
              encryptionSubsampleDataBuffer.putShort((short) finalPartitionSize);
              encryptionSubsampleDataBuffer.putInt(0);
            }
            encryptionSubsampleData.reset(encryptionSubsampleDataBuffer.array(), subsampleDataSize);
            output.sampleData(
                encryptionSubsampleData,
                subsampleDataSize,
                TrackOutput.SAMPLE_DATA_PART_ENCRYPTION);
            sampleBytesWritten += subsampleDataSize;
          }
        }
      } else if (track.sampleStrippedBytes != null) {
        // If the sample has header stripping, prepare to read/output the stripped bytes first.
        sampleStrippedBytes.reset(track.sampleStrippedBytes, track.sampleStrippedBytes.length);
      }

      if (track.samplesHaveSupplementalData(isBlockGroup)) {
        blockFlags |= C.BUFFER_FLAG_HAS_SUPPLEMENTAL_DATA;
        supplementalData.reset(/* limit= */ 0);
        // If there is supplemental data, the structure of the sample data is:
        // encryption data (if any) || sample size (4 bytes) || sample data || supplemental data
        int sampleSize = size + sampleStrippedBytes.limit() - sampleBytesRead;
        scratch.reset(/* limit= */ 4);
        scratch.getData()[0] = (byte) ((sampleSize >> 24) & 0xFF);
        scratch.getData()[1] = (byte) ((sampleSize >> 16) & 0xFF);
        scratch.getData()[2] = (byte) ((sampleSize >> 8) & 0xFF);
        scratch.getData()[3] = (byte) (sampleSize & 0xFF);
        output.sampleData(scratch, 4, TrackOutput.SAMPLE_DATA_PART_SUPPLEMENTAL);
        sampleBytesWritten += 4;
      }

      sampleEncodingHandled = true;
    }
    size += sampleStrippedBytes.limit();

    if (CODEC_ID_H264.equals(track.codecId) || CODEC_ID_H265.equals(track.codecId)) {
      // TODO: Deduplicate with Mp4Extractor.

      // Zero the top three bytes of the array that we'll use to decode nal unit lengths, in case
      // they're only 1 or 2 bytes long.
      byte[] nalLengthData = nalLength.getData();
      nalLengthData[0] = 0;
      nalLengthData[1] = 0;
      nalLengthData[2] = 0;
      int nalUnitLengthFieldLength = track.nalUnitLengthFieldLength;
      int nalUnitLengthFieldLengthDiff = 4 - track.nalUnitLengthFieldLength;
      // NAL units are length delimited, but the decoder requires start code delimited units.
      // Loop until we've written the sample to the track output, replacing length delimiters with
      // start codes as we encounter them.
      while (sampleBytesRead < size) {
        if (sampleCurrentNalBytesRemaining == 0) {
          // Read the NAL length so that we know where we find the next one.
          writeToTarget(
              input, nalLengthData, nalUnitLengthFieldLengthDiff, nalUnitLengthFieldLength);
          sampleBytesRead += nalUnitLengthFieldLength;
          nalLength.setPosition(0);
          sampleCurrentNalBytesRemaining = nalLength.readUnsignedIntToInt();
          // Write a start code for the current NAL unit.
          nalStartCode.setPosition(0);
          output.sampleData(nalStartCode, 4);
          sampleBytesWritten += 4;
        } else {
          // Write the payload of the NAL unit.
          int bytesWritten = writeToOutput(input, output, sampleCurrentNalBytesRemaining);
          sampleBytesRead += bytesWritten;
          sampleBytesWritten += bytesWritten;
          sampleCurrentNalBytesRemaining -= bytesWritten;
        }
      }
    } else {
      if (track.trueHdSampleRechunker != null) {
        checkState(sampleStrippedBytes.limit() == 0);
        track.trueHdSampleRechunker.startSample(input);
      }
      while (sampleBytesRead < size) {
        int bytesWritten = writeToOutput(input, output, size - sampleBytesRead);
        sampleBytesRead += bytesWritten;
        sampleBytesWritten += bytesWritten;
      }
    }

    if (CODEC_ID_VORBIS.equals(track.codecId)) {
      // Vorbis decoder in android MediaCodec [1] expects the last 4 bytes of the sample to be the
      // number of samples in the current page. This definition holds good only for Ogg and
      // irrelevant for Matroska. So we always set this to -1 (the decoder will ignore this value if
      // we set it to -1). The android platform media extractor [2] does the same.
      // [1]
      // https://android.googlesource.com/platform/frameworks/av/+/lollipop-release/media/libstagefright/codecs/vorbis/dec/SoftVorbis.cpp#314
      // [2]
      // https://android.googlesource.com/platform/frameworks/av/+/lollipop-release/media/libstagefright/NuMediaExtractor.cpp#474
      vorbisNumPageSamples.setPosition(0);
      output.sampleData(vorbisNumPageSamples, 4);
      sampleBytesWritten += 4;
    }

    return finishWriteSampleData();
  }

  /**
   * Called by {@link #writeSampleData(ExtractorInput, Track, int, boolean)} when the sample has
   * been written. Returns the final sample size and resets state for the next sample.
   */
  private int finishWriteSampleData() {
    int sampleSize = sampleBytesWritten;
    resetWriteSampleData();
    return sampleSize;
  }

  /** Resets state used by {@link #writeSampleData(ExtractorInput, Track, int, boolean)}. */
  private void resetWriteSampleData() {
    sampleBytesRead = 0;
    sampleBytesWritten = 0;
    sampleCurrentNalBytesRemaining = 0;
    sampleEncodingHandled = false;
    sampleSignalByteRead = false;
    samplePartitionCountRead = false;
    samplePartitionCount = 0;
    sampleSignalByte = (byte) 0;
    sampleInitializationVectorRead = false;
    sampleStrippedBytes.reset(/* limit= */ 0);
  }

  private void writeSubtitleSampleData(ExtractorInput input, byte[] samplePrefix, int size)
      throws IOException {
    int sizeWithPrefix = samplePrefix.length + size;
    if (subtitleSample.capacity() < sizeWithPrefix) {
      // Initialize subripSample to contain the required prefix and have space to hold a subtitle
      // twice as long as this one.
      subtitleSample.reset(Arrays.copyOf(samplePrefix, sizeWithPrefix + size));
    } else {
      System.arraycopy(samplePrefix, 0, subtitleSample.getData(), 0, samplePrefix.length);
    }
    input.readFully(subtitleSample.getData(), samplePrefix.length, size);
    subtitleSample.setPosition(0);
    subtitleSample.setLimit(sizeWithPrefix);
    // Defer writing the data to the track output. We need to modify the sample data by setting
    // the correct end timecode, which we might not have yet.
  }

  /**
   * Overwrites the end timecode in {@code subtitleData} with the correctly formatted time derived
   * from {@code durationUs}.
   *
   * <p>See documentation on {@link #SSA_DIALOGUE_FORMAT} and {@link #SUBRIP_PREFIX} for why we use
   * the duration as the end timecode.
   *
   * @param codecId The subtitle codec; must be {@link #CODEC_ID_SUBRIP}, {@link #CODEC_ID_ASS} or
   *     {@link #CODEC_ID_VTT}.
   * @param durationUs The duration of the sample, in microseconds.
   * @param subtitleData The subtitle sample in which to overwrite the end timecode (output
   *     parameter).
   */
  private static void setSubtitleEndTime(String codecId, long durationUs, byte[] subtitleData) {
    byte[] endTimecode;
    int endTimecodeOffset;
    switch (codecId) {
      case CODEC_ID_SUBRIP:
        endTimecode =
            formatSubtitleTimecode(
                durationUs, SUBRIP_TIMECODE_FORMAT, SUBRIP_TIMECODE_LAST_VALUE_SCALING_FACTOR);
        endTimecodeOffset = SUBRIP_PREFIX_END_TIMECODE_OFFSET;
        break;
      case CODEC_ID_ASS:
        endTimecode =
            formatSubtitleTimecode(
                durationUs, SSA_TIMECODE_FORMAT, SSA_TIMECODE_LAST_VALUE_SCALING_FACTOR);
        endTimecodeOffset = SSA_PREFIX_END_TIMECODE_OFFSET;
        break;
      case CODEC_ID_VTT:
        endTimecode =
            formatSubtitleTimecode(
                durationUs, VTT_TIMECODE_FORMAT, VTT_TIMECODE_LAST_VALUE_SCALING_FACTOR);
        endTimecodeOffset = VTT_PREFIX_END_TIMECODE_OFFSET;
        break;
      default:
        throw new IllegalArgumentException();
    }
    System.arraycopy(endTimecode, 0, subtitleData, endTimecodeOffset, endTimecode.length);
  }

  /**
   * Formats {@code timeUs} using {@code timecodeFormat}, and sets it as the end timecode in {@code
   * subtitleSampleData}.
   */
  private static byte[] formatSubtitleTimecode(
      long timeUs, String timecodeFormat, long lastTimecodeValueScalingFactor) {
    checkArgument(timeUs != C.TIME_UNSET);
    byte[] timeCodeData;
    int hours = (int) (timeUs / (3600 * C.MICROS_PER_SECOND));
    timeUs -= (hours * 3600L * C.MICROS_PER_SECOND);
    int minutes = (int) (timeUs / (60 * C.MICROS_PER_SECOND));
    timeUs -= (minutes * 60L * C.MICROS_PER_SECOND);
    int seconds = (int) (timeUs / C.MICROS_PER_SECOND);
    timeUs -= (seconds * C.MICROS_PER_SECOND);
    int lastValue = (int) (timeUs / lastTimecodeValueScalingFactor);
    timeCodeData =
        Util.getUtf8Bytes(
            String.format(Locale.US, timecodeFormat, hours, minutes, seconds, lastValue));
    return timeCodeData;
  }

  /**
   * Writes {@code length} bytes of sample data into {@code target} at {@code offset}, consisting of
   * pending {@link #sampleStrippedBytes} and any remaining data read from {@code input}.
   */
  private void writeToTarget(ExtractorInput input, byte[] target, int offset, int length)
      throws IOException {
    int pendingStrippedBytes = min(length, sampleStrippedBytes.bytesLeft());
    input.readFully(target, offset + pendingStrippedBytes, length - pendingStrippedBytes);
    if (pendingStrippedBytes > 0) {
      sampleStrippedBytes.readBytes(target, offset, pendingStrippedBytes);
    }
  }

  /**
   * Outputs up to {@code length} bytes of sample data to {@code output}, consisting of either
   * {@link #sampleStrippedBytes} or data read from {@code input}.
   */
  private int writeToOutput(ExtractorInput input, TrackOutput output, int length)
      throws IOException {
    int bytesWritten;
    int strippedBytesLeft = sampleStrippedBytes.bytesLeft();
    if (strippedBytesLeft > 0) {
      bytesWritten = min(length, strippedBytesLeft);
      output.sampleData(sampleStrippedBytes, bytesWritten);
    } else {
      bytesWritten = output.sampleData(input, length, false);
    }
    return bytesWritten;
  }

  /**
   * Builds a {@link SeekMap} from the recently gathered Cues information.
   *
   * @return The built {@link SeekMap}. The returned {@link SeekMap} may be unseekable if cues
   *     information was missing or incomplete.
   */
  private SeekMap buildSeekMap(
      @Nullable LongArray cueTimesUs, @Nullable LongArray cueClusterPositions) {
    if (segmentContentPosition == C.POSITION_UNSET
        || durationUs == C.TIME_UNSET
        || cueTimesUs == null
        || cueTimesUs.size() == 0
        || cueClusterPositions == null
        || cueClusterPositions.size() != cueTimesUs.size()) {
      // Cues information is missing or incomplete.
      return new SeekMap.Unseekable(durationUs);
    }
    int cuePointsSize = cueTimesUs.size();
    int[] sizes = new int[cuePointsSize];
    long[] offsets = new long[cuePointsSize];
    long[] durationsUs = new long[cuePointsSize];
    long[] timesUs = new long[cuePointsSize];
    for (int i = 0; i < cuePointsSize; i++) {
      timesUs[i] = cueTimesUs.get(i);
      offsets[i] = segmentContentPosition + cueClusterPositions.get(i);
    }
    for (int i = 0; i < cuePointsSize - 1; i++) {
      sizes[i] = (int) (offsets[i + 1] - offsets[i]);
      durationsUs[i] = timesUs[i + 1] - timesUs[i];
    }
    sizes[cuePointsSize - 1] =
        (int) (segmentContentPosition + segmentContentSize - offsets[cuePointsSize - 1]);
    durationsUs[cuePointsSize - 1] = durationUs - timesUs[cuePointsSize - 1];

    long lastDurationUs = durationsUs[cuePointsSize - 1];
    if (lastDurationUs <= 0) {
      Log.w(TAG, "Discarding last cue point with unexpected duration: " + lastDurationUs);
      sizes = Arrays.copyOf(sizes, sizes.length - 1);
      offsets = Arrays.copyOf(offsets, offsets.length - 1);
      durationsUs = Arrays.copyOf(durationsUs, durationsUs.length - 1);
      timesUs = Arrays.copyOf(timesUs, timesUs.length - 1);
    }

    return new ChunkIndex(sizes, offsets, durationsUs, timesUs);
  }

  /**
   * Updates the position of the holder to Cues element's position if the extractor configuration
   * permits use of master seek entry. After building Cues sets the holder's position back to where
   * it was before.
   *
   * @param seekPosition The holder whose position will be updated.
   * @param currentPosition Current position of the input.
   * @return Whether the seek position was updated.
   */
  private boolean maybeSeekForCues(PositionHolder seekPosition, long currentPosition) {
    if (seekForCues) {
      seekPositionAfterBuildingCues = currentPosition;
      seekPosition.position = cuesContentPosition;
      seekForCues = false;
      return true;
    }
    // After parsing Cues, seek back to original position if available. We will not do this unless
    // we seeked to get to the Cues in the first place.
    if (sentSeekMap && seekPositionAfterBuildingCues != C.POSITION_UNSET) {
      seekPosition.position = seekPositionAfterBuildingCues;
      seekPositionAfterBuildingCues = C.POSITION_UNSET;
      return true;
    }
    return false;
  }

  private long scaleTimecodeToUs(long unscaledTimecode) throws ParserException {
    if (timecodeScale == C.TIME_UNSET) {
      throw ParserException.createForMalformedContainer(
          "Can't scale timecode prior to timecodeScale being set.", /* cause= */ null);
    }
    return Util.scaleLargeTimestamp(unscaledTimecode, timecodeScale, 1000);
  }

  private static boolean isCodecSupported(String codecId) {
    switch (codecId) {
      case CODEC_ID_VP8:
      case CODEC_ID_VP9:
      case CODEC_ID_AV1:
      case CODEC_ID_MPEG2:
      case CODEC_ID_MPEG4_SP:
      case CODEC_ID_MPEG4_ASP:
      case CODEC_ID_MPEG4_AP:
      case CODEC_ID_H264:
      case CODEC_ID_H265:
      case CODEC_ID_FOURCC:
      case CODEC_ID_THEORA:
      case CODEC_ID_OPUS:
      case CODEC_ID_VORBIS:
      case CODEC_ID_AAC:
      case CODEC_ID_MP2:
      case CODEC_ID_MP3:
      case CODEC_ID_AC3:
      case CODEC_ID_E_AC3:
      case CODEC_ID_TRUEHD:
      case CODEC_ID_DTS:
      case CODEC_ID_DTS_EXPRESS:
      case CODEC_ID_DTS_LOSSLESS:
      case CODEC_ID_FLAC:
      case CODEC_ID_ACM:
      case CODEC_ID_PCM_INT_LIT:
      case CODEC_ID_PCM_INT_BIG:
      case CODEC_ID_PCM_FLOAT:
      case CODEC_ID_SUBRIP:
      case CODEC_ID_ASS:
      case CODEC_ID_VTT:
      case CODEC_ID_VOBSUB:
      case CODEC_ID_PGS:
      case CODEC_ID_DVBSUB:
        return true;
      default:
        return false;
    }
  }

  /**
   * Returns an array that can store (at least) {@code length} elements, which will be either a new
   * array or {@code array} if it's not null and large enough.
   */
  private static int[] ensureArrayCapacity(@Nullable int[] array, int length) {
    if (array == null) {
      return new int[length];
    } else if (array.length >= length) {
      return array;
    } else {
      // Double the size to avoid allocating constantly if the required length increases gradually.
      return new int[max(array.length * 2, length)];
    }
  }

  @EnsuresNonNull("extractorOutput")
  private void assertInitialized() {
    checkStateNotNull(extractorOutput);
  }

  /** Passes events through to the outer {@link MatroskaExtractor}. */
  private final class InnerEbmlProcessor implements EbmlProcessor {

    @Override
    public @ElementType int getElementType(int id) {
      return MatroskaExtractor.this.getElementType(id);
    }

    @Override
    public boolean isLevel1Element(int id) {
      return MatroskaExtractor.this.isLevel1Element(id);
    }

    @Override
    public void startMasterElement(int id, long contentPosition, long contentSize)
        throws ParserException {
      MatroskaExtractor.this.startMasterElement(id, contentPosition, contentSize);
    }

    @Override
    public void endMasterElement(int id) throws ParserException {
      MatroskaExtractor.this.endMasterElement(id);
    }

    @Override
    public void integerElement(int id, long value) throws ParserException {
      MatroskaExtractor.this.integerElement(id, value);
    }

    @Override
    public void floatElement(int id, double value) throws ParserException {
      MatroskaExtractor.this.floatElement(id, value);
    }

    @Override
    public void stringElement(int id, String value) throws ParserException {
      MatroskaExtractor.this.stringElement(id, value);
    }

    @Override
    public void binaryElement(int id, int contentsSize, ExtractorInput input) throws IOException {
      MatroskaExtractor.this.binaryElement(id, contentsSize, input);
    }
  }

  /** Holds data corresponding to a single track. */
  protected static final class Track {

    private static final int DISPLAY_UNIT_PIXELS = 0;
    private static final int MAX_CHROMATICITY = 50_000; // Defined in CTA-861.3.
    /** Default max content light level (CLL) that should be encoded into hdrStaticInfo. */
    private static final int DEFAULT_MAX_CLL = 1000; // nits.

    /** Default frame-average light level (FALL) that should be encoded into hdrStaticInfo. */
    private static final int DEFAULT_MAX_FALL = 200; // nits.

    // Common elements.
    public @MonotonicNonNull String name;
    public @MonotonicNonNull String codecId;
    public int number;
    public int type;
    public int defaultSampleDurationNs;
    public int maxBlockAdditionId;
    private int blockAddIdType;
    public boolean hasContentEncryption;
    public byte @MonotonicNonNull [] sampleStrippedBytes;
    public TrackOutput.@MonotonicNonNull CryptoData cryptoData;
    public byte @MonotonicNonNull [] codecPrivate;
    public @MonotonicNonNull DrmInitData drmInitData;

    // Video elements.
    public int width = Format.NO_VALUE;
    public int height = Format.NO_VALUE;
    public int displayWidth = Format.NO_VALUE;
    public int displayHeight = Format.NO_VALUE;
    public int displayUnit = DISPLAY_UNIT_PIXELS;
    public @C.Projection int projectionType = Format.NO_VALUE;
    public float projectionPoseYaw = 0f;
    public float projectionPosePitch = 0f;
    public float projectionPoseRoll = 0f;
    public byte @MonotonicNonNull [] projectionData = null;
    public @C.StereoMode int stereoMode = Format.NO_VALUE;
    public boolean hasColorInfo = false;
    public @C.ColorSpace int colorSpace = Format.NO_VALUE;
    public @C.ColorTransfer int colorTransfer = Format.NO_VALUE;
    public @C.ColorRange int colorRange = Format.NO_VALUE;
    public int maxContentLuminance = DEFAULT_MAX_CLL;
    public int maxFrameAverageLuminance = DEFAULT_MAX_FALL;
    public float primaryRChromaticityX = Format.NO_VALUE;
    public float primaryRChromaticityY = Format.NO_VALUE;
    public float primaryGChromaticityX = Format.NO_VALUE;
    public float primaryGChromaticityY = Format.NO_VALUE;
    public float primaryBChromaticityX = Format.NO_VALUE;
    public float primaryBChromaticityY = Format.NO_VALUE;
    public float whitePointChromaticityX = Format.NO_VALUE;
    public float whitePointChromaticityY = Format.NO_VALUE;
    public float maxMasteringLuminance = Format.NO_VALUE;
    public float minMasteringLuminance = Format.NO_VALUE;
    public byte @MonotonicNonNull [] dolbyVisionConfigBytes;

    // Audio elements. Initially set to their default values.
    public int channelCount = 1;
    public int audioBitDepth = Format.NO_VALUE;
    public int sampleRate = 8000;
    public long codecDelayNs = 0;
    public long seekPreRollNs = 0;
    public @MonotonicNonNull TrueHdSampleRechunker trueHdSampleRechunker;

    // Text elements.
    public boolean flagForced;
    public boolean flagDefault = true;
    private String language = "eng";

    // Set when the output is initialized. nalUnitLengthFieldLength is only set for H264/H265.
    public @MonotonicNonNull TrackOutput output;
    public int nalUnitLengthFieldLength;

    /** Initializes the track with an output. */
    @RequiresNonNull("codecId")
    @EnsuresNonNull("this.output")
    public void initializeOutput(ExtractorOutput output, int trackId) throws ParserException {
      String mimeType;
      int maxInputSize = Format.NO_VALUE;
      @C.PcmEncoding int pcmEncoding = Format.NO_VALUE;
      @Nullable List<byte[]> initializationData = null;
      @Nullable String codecs = null;
      switch (codecId) {
        case CODEC_ID_VP8:
          mimeType = MimeTypes.VIDEO_VP8;
          break;
        case CODEC_ID_VP9:
          mimeType = MimeTypes.VIDEO_VP9;
          break;
        case CODEC_ID_AV1:
          mimeType = MimeTypes.VIDEO_AV1;
          break;
        case CODEC_ID_MPEG2:
          mimeType = MimeTypes.VIDEO_MPEG2;
          break;
        case CODEC_ID_MPEG4_SP:
        case CODEC_ID_MPEG4_ASP:
        case CODEC_ID_MPEG4_AP:
          mimeType = MimeTypes.VIDEO_MP4V;
          initializationData =
              codecPrivate == null ? null : Collections.singletonList(codecPrivate);
          break;
        case CODEC_ID_H264:
          mimeType = MimeTypes.VIDEO_H264;
          AvcConfig avcConfig = AvcConfig.parse(new ParsableByteArray(getCodecPrivate(codecId)));
          initializationData = avcConfig.initializationData;
          nalUnitLengthFieldLength = avcConfig.nalUnitLengthFieldLength;
          codecs = avcConfig.codecs;
          break;
        case CODEC_ID_H265:
          mimeType = MimeTypes.VIDEO_H265;
          HevcConfig hevcConfig = HevcConfig.parse(new ParsableByteArray(getCodecPrivate(codecId)));
          initializationData = hevcConfig.initializationData;
          nalUnitLengthFieldLength = hevcConfig.nalUnitLengthFieldLength;
          codecs = hevcConfig.codecs;
          break;
        case CODEC_ID_FOURCC:
          Pair<String, @NullableType List<byte[]>> pair =
              parseFourCcPrivate(new ParsableByteArray(getCodecPrivate(codecId)));
          mimeType = pair.first;
          initializationData = pair.second;
          break;
        case CODEC_ID_THEORA:
          // TODO: This can be set to the real mimeType if/when we work out what initializationData
          // should be set to for this case.
          mimeType = MimeTypes.VIDEO_UNKNOWN;
          break;
        case CODEC_ID_VORBIS:
          mimeType = MimeTypes.AUDIO_VORBIS;
          maxInputSize = VORBIS_MAX_INPUT_SIZE;
          initializationData = parseVorbisCodecPrivate(getCodecPrivate(codecId));
          break;
        case CODEC_ID_OPUS:
          mimeType = MimeTypes.AUDIO_OPUS;
          maxInputSize = OPUS_MAX_INPUT_SIZE;
          initializationData = new ArrayList<>(3);
          initializationData.add(getCodecPrivate(codecId));
          initializationData.add(
              ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(codecDelayNs).array());
          initializationData.add(
              ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(seekPreRollNs).array());
          break;
        case CODEC_ID_AAC:
          mimeType = MimeTypes.AUDIO_AAC;
          initializationData = Collections.singletonList(getCodecPrivate(codecId));
          AacUtil.Config aacConfig = AacUtil.parseAudioSpecificConfig(codecPrivate);
          // Update sampleRate and channelCount from the AudioSpecificConfig initialization data,
          // which is more reliable. See [Internal: b/10903778].
          sampleRate = aacConfig.sampleRateHz;
          channelCount = aacConfig.channelCount;
          codecs = aacConfig.codecs;
          break;
        case CODEC_ID_MP2:
          mimeType = MimeTypes.AUDIO_MPEG_L2;
          maxInputSize = MpegAudioUtil.MAX_FRAME_SIZE_BYTES;
          break;
        case CODEC_ID_MP3:
          mimeType = MimeTypes.AUDIO_MPEG;
          maxInputSize = MpegAudioUtil.MAX_FRAME_SIZE_BYTES;
          break;
        case CODEC_ID_AC3:
          mimeType = MimeTypes.AUDIO_AC3;
          break;
        case CODEC_ID_E_AC3:
          mimeType = MimeTypes.AUDIO_E_AC3;
          break;
        case CODEC_ID_TRUEHD:
          mimeType = MimeTypes.AUDIO_TRUEHD;
          trueHdSampleRechunker = new TrueHdSampleRechunker();
          break;
        case CODEC_ID_DTS:
        case CODEC_ID_DTS_EXPRESS:
          mimeType = MimeTypes.AUDIO_DTS;
          break;
        case CODEC_ID_DTS_LOSSLESS:
          mimeType = MimeTypes.AUDIO_DTS_HD;
          break;
        case CODEC_ID_FLAC:
          mimeType = MimeTypes.AUDIO_FLAC;
          initializationData = Collections.singletonList(getCodecPrivate(codecId));
          break;
        case CODEC_ID_ACM:
          mimeType = MimeTypes.AUDIO_RAW;
          if (parseMsAcmCodecPrivate(new ParsableByteArray(getCodecPrivate(codecId)))) {
            pcmEncoding = Util.getPcmEncoding(audioBitDepth);
            if (pcmEncoding == C.ENCODING_INVALID) {
              pcmEncoding = Format.NO_VALUE;
              mimeType = MimeTypes.AUDIO_UNKNOWN;
              Log.w(
                  TAG,
                  "Unsupported PCM bit depth: "
                      + audioBitDepth
                      + ". Setting mimeType to "
                      + mimeType);
            }
          } else {
            mimeType = MimeTypes.AUDIO_UNKNOWN;
            Log.w(TAG, "Non-PCM MS/ACM is unsupported. Setting mimeType to " + mimeType);
          }
          break;
        case CODEC_ID_PCM_INT_LIT:
          mimeType = MimeTypes.AUDIO_RAW;
          pcmEncoding = Util.getPcmEncoding(audioBitDepth);
          if (pcmEncoding == C.ENCODING_INVALID) {
            pcmEncoding = Format.NO_VALUE;
            mimeType = MimeTypes.AUDIO_UNKNOWN;
            Log.w(
                TAG,
                "Unsupported little endian PCM bit depth: "
                    + audioBitDepth
                    + ". Setting mimeType to "
                    + mimeType);
          }
          break;
        case CODEC_ID_PCM_INT_BIG:
          mimeType = MimeTypes.AUDIO_RAW;
          if (audioBitDepth == 8) {
            pcmEncoding = C.ENCODING_PCM_8BIT;
          } else if (audioBitDepth == 16) {
            pcmEncoding = C.ENCODING_PCM_16BIT_BIG_ENDIAN;
          } else {
            pcmEncoding = Format.NO_VALUE;
            mimeType = MimeTypes.AUDIO_UNKNOWN;
            Log.w(
                TAG,
                "Unsupported big endian PCM bit depth: "
                    + audioBitDepth
                    + ". Setting mimeType to "
                    + mimeType);
          }
          break;
        case CODEC_ID_PCM_FLOAT:
          mimeType = MimeTypes.AUDIO_RAW;
          if (audioBitDepth == 32) {
            pcmEncoding = C.ENCODING_PCM_FLOAT;
          } else {
            pcmEncoding = Format.NO_VALUE;
            mimeType = MimeTypes.AUDIO_UNKNOWN;
            Log.w(
                TAG,
                "Unsupported floating point PCM bit depth: "
                    + audioBitDepth
                    + ". Setting mimeType to "
                    + mimeType);
          }
          break;
        case CODEC_ID_SUBRIP:
          mimeType = MimeTypes.APPLICATION_SUBRIP;
          break;
        case CODEC_ID_ASS:
          mimeType = MimeTypes.TEXT_SSA;
          initializationData = ImmutableList.of(SSA_DIALOGUE_FORMAT, getCodecPrivate(codecId));
          break;
        case CODEC_ID_VTT:
          mimeType = MimeTypes.TEXT_VTT;
          break;
        case CODEC_ID_VOBSUB:
          mimeType = MimeTypes.APPLICATION_VOBSUB;
          initializationData = ImmutableList.of(getCodecPrivate(codecId));
          break;
        case CODEC_ID_PGS:
          mimeType = MimeTypes.APPLICATION_PGS;
          break;
        case CODEC_ID_DVBSUB:
          mimeType = MimeTypes.APPLICATION_DVBSUBS;
          // Init data: composition_page (2), ancillary_page (2)
          byte[] initializationDataBytes = new byte[4];
          System.arraycopy(getCodecPrivate(codecId), 0, initializationDataBytes, 0, 4);
          initializationData = ImmutableList.of(initializationDataBytes);
          break;
        default:
          throw ParserException.createForMalformedContainer(
              "Unrecognized codec identifier.", /* cause= */ null);
      }

      if (dolbyVisionConfigBytes != null) {
        @Nullable
        DolbyVisionConfig dolbyVisionConfig =
            DolbyVisionConfig.parse(new ParsableByteArray(this.dolbyVisionConfigBytes));
        if (dolbyVisionConfig != null) {
          codecs = dolbyVisionConfig.codecs;
          mimeType = MimeTypes.VIDEO_DOLBY_VISION;
        }
      }

      @C.SelectionFlags int selectionFlags = 0;
      selectionFlags |= flagDefault ? C.SELECTION_FLAG_DEFAULT : 0;
      selectionFlags |= flagForced ? C.SELECTION_FLAG_FORCED : 0;

      int type;
      Format.Builder formatBuilder = new Format.Builder();
      // TODO: Consider reading the name elements of the tracks and, if present, incorporating them
      // into the trackId passed when creating the formats.
      if (MimeTypes.isAudio(mimeType)) {
        type = C.TRACK_TYPE_AUDIO;
        formatBuilder
            .setChannelCount(channelCount)
            .setSampleRate(sampleRate)
            .setPcmEncoding(pcmEncoding);
      } else if (MimeTypes.isVideo(mimeType)) {
        type = C.TRACK_TYPE_VIDEO;
        if (displayUnit == Track.DISPLAY_UNIT_PIXELS) {
          displayWidth = displayWidth == Format.NO_VALUE ? width : displayWidth;
          displayHeight = displayHeight == Format.NO_VALUE ? height : displayHeight;
        }
        float pixelWidthHeightRatio = Format.NO_VALUE;
        if (displayWidth != Format.NO_VALUE && displayHeight != Format.NO_VALUE) {
          pixelWidthHeightRatio = ((float) (height * displayWidth)) / (width * displayHeight);
        }
        @Nullable ColorInfo colorInfo = null;
        if (hasColorInfo) {
          @Nullable byte[] hdrStaticInfo = getHdrStaticInfo();
          colorInfo = new ColorInfo(colorSpace, colorRange, colorTransfer, hdrStaticInfo);
        }
        int rotationDegrees = Format.NO_VALUE;

        if (name != null && TRACK_NAME_TO_ROTATION_DEGREES.containsKey(name)) {
          rotationDegrees = TRACK_NAME_TO_ROTATION_DEGREES.get(name);
        }
        if (projectionType == C.PROJECTION_RECTANGULAR
            && Float.compare(projectionPoseYaw, 0f) == 0
            && Float.compare(projectionPosePitch, 0f) == 0) {
          // The range of projectionPoseRoll is [-180, 180].
          if (Float.compare(projectionPoseRoll, 0f) == 0) {
            rotationDegrees = 0;
          } else if (Float.compare(projectionPosePitch, 90f) == 0) {
            rotationDegrees = 90;
          } else if (Float.compare(projectionPosePitch, -180f) == 0
              || Float.compare(projectionPosePitch, 180f) == 0) {
            rotationDegrees = 180;
          } else if (Float.compare(projectionPosePitch, -90f) == 0) {
            rotationDegrees = 270;
          }
        }
        formatBuilder
            .setWidth(width)
            .setHeight(height)
            .setPixelWidthHeightRatio(pixelWidthHeightRatio)
            .setRotationDegrees(rotationDegrees)
            .setProjectionData(projectionData)
            .setStereoMode(stereoMode)
            .setColorInfo(colorInfo);
      } else if (MimeTypes.APPLICATION_SUBRIP.equals(mimeType)
          || MimeTypes.TEXT_SSA.equals(mimeType)
          || MimeTypes.TEXT_VTT.equals(mimeType)
          || MimeTypes.APPLICATION_VOBSUB.equals(mimeType)
          || MimeTypes.APPLICATION_PGS.equals(mimeType)
          || MimeTypes.APPLICATION_DVBSUBS.equals(mimeType)) {
        type = C.TRACK_TYPE_TEXT;
      } else {
        throw ParserException.createForMalformedContainer(
            "Unexpected MIME type.", /* cause= */ null);
      }

      if (name != null && !TRACK_NAME_TO_ROTATION_DEGREES.containsKey(name)) {
        formatBuilder.setLabel(name);
      }

      Format format =
          formatBuilder
              .setId(trackId)
              .setSampleMimeType(mimeType)
              .setMaxInputSize(maxInputSize)
              .setLanguage(language)
              .setSelectionFlags(selectionFlags)
              .setInitializationData(initializationData)
              .setCodecs(codecs)
              .setDrmInitData(drmInitData)
              .build();

      this.output = output.track(number, type);
      this.output.format(format);
    }

    /** Forces any pending sample metadata to be flushed to the output. */
    @RequiresNonNull("output")
    public void outputPendingSampleMetadata() {
      if (trueHdSampleRechunker != null) {
        trueHdSampleRechunker.outputPendingSampleMetadata(output, cryptoData);
      }
    }

    /** Resets any state stored in the track in response to a seek. */
    public void reset() {
      if (trueHdSampleRechunker != null) {
        trueHdSampleRechunker.reset();
      }
    }

    /**
     * Returns true if supplemental data will be attached to the samples.
     *
     * @param isBlockGroup Whether the samples are from a BlockGroup.
     */
    private boolean samplesHaveSupplementalData(boolean isBlockGroup) {
      if (CODEC_ID_OPUS.equals(codecId)) {
        // At the end of a BlockGroup, a positive DiscardPadding value will be written out as
        // supplemental data for Opus codec. Otherwise (i.e. DiscardPadding <= 0) supplemental data
        // size will be 0.
        return isBlockGroup;
      }
      return maxBlockAdditionId > 0;
    }

    /** Returns the HDR Static Info as defined in CTA-861.3. */
    @Nullable
    private byte[] getHdrStaticInfo() {
      // Are all fields present.
      if (primaryRChromaticityX == Format.NO_VALUE
          || primaryRChromaticityY == Format.NO_VALUE
          || primaryGChromaticityX == Format.NO_VALUE
          || primaryGChromaticityY == Format.NO_VALUE
          || primaryBChromaticityX == Format.NO_VALUE
          || primaryBChromaticityY == Format.NO_VALUE
          || whitePointChromaticityX == Format.NO_VALUE
          || whitePointChromaticityY == Format.NO_VALUE
          || maxMasteringLuminance == Format.NO_VALUE
          || minMasteringLuminance == Format.NO_VALUE) {
        return null;
      }

      byte[] hdrStaticInfoData = new byte[25];
      ByteBuffer hdrStaticInfo = ByteBuffer.wrap(hdrStaticInfoData).order(ByteOrder.LITTLE_ENDIAN);
      hdrStaticInfo.put((byte) 0); // Type.
      hdrStaticInfo.putShort((short) ((primaryRChromaticityX * MAX_CHROMATICITY) + 0.5f));
      hdrStaticInfo.putShort((short) ((primaryRChromaticityY * MAX_CHROMATICITY) + 0.5f));
      hdrStaticInfo.putShort((short) ((primaryGChromaticityX * MAX_CHROMATICITY) + 0.5f));
      hdrStaticInfo.putShort((short) ((primaryGChromaticityY * MAX_CHROMATICITY) + 0.5f));
      hdrStaticInfo.putShort((short) ((primaryBChromaticityX * MAX_CHROMATICITY) + 0.5f));
      hdrStaticInfo.putShort((short) ((primaryBChromaticityY * MAX_CHROMATICITY) + 0.5f));
      hdrStaticInfo.putShort((short) ((whitePointChromaticityX * MAX_CHROMATICITY) + 0.5f));
      hdrStaticInfo.putShort((short) ((whitePointChromaticityY * MAX_CHROMATICITY) + 0.5f));
      hdrStaticInfo.putShort((short) (maxMasteringLuminance + 0.5f));
      hdrStaticInfo.putShort((short) (minMasteringLuminance + 0.5f));
      hdrStaticInfo.putShort((short) maxContentLuminance);
      hdrStaticInfo.putShort((short) maxFrameAverageLuminance);
      return hdrStaticInfoData;
    }

    /**
     * Builds initialization data for a {@link Format} from FourCC codec private data.
     *
     * @return The codec mime type and initialization data. If the compression type is not supported
     *     then the mime type is set to {@link MimeTypes#VIDEO_UNKNOWN} and the initialization data
     *     is {@code null}.
     * @throws ParserException If the initialization data could not be built.
     */
    private static Pair<String, @NullableType List<byte[]>> parseFourCcPrivate(
        ParsableByteArray buffer) throws ParserException {
      try {
        buffer.skipBytes(16); // size(4), width(4), height(4), planes(2), bitcount(2).
        long compression = buffer.readLittleEndianUnsignedInt();
        if (compression == FOURCC_COMPRESSION_DIVX) {
          return new Pair<>(MimeTypes.VIDEO_DIVX, null);
        } else if (compression == FOURCC_COMPRESSION_H263) {
          return new Pair<>(MimeTypes.VIDEO_H263, null);
        } else if (compression == FOURCC_COMPRESSION_VC1) {
          // Search for the initialization data from the end of the BITMAPINFOHEADER. The last 20
          // bytes of which are: sizeImage(4), xPel/m (4), yPel/m (4), clrUsed(4), clrImportant(4).
          int startOffset = buffer.getPosition() + 20;
          byte[] bufferData = buffer.getData();
          for (int offset = startOffset; offset < bufferData.length - 4; offset++) {
            if (bufferData[offset] == 0x00
                && bufferData[offset + 1] == 0x00
                && bufferData[offset + 2] == 0x01
                && bufferData[offset + 3] == 0x0F) {
              // We've found the initialization data.
              byte[] initializationData = Arrays.copyOfRange(bufferData, offset, bufferData.length);
              return new Pair<>(MimeTypes.VIDEO_VC1, Collections.singletonList(initializationData));
            }
          }
          throw ParserException.createForMalformedContainer(
              "Failed to find FourCC VC1 initialization data", /* cause= */ null);
        }
      } catch (ArrayIndexOutOfBoundsException e) {
        throw ParserException.createForMalformedContainer(
            "Error parsing FourCC private data", /* cause= */ null);
      }

      Log.w(TAG, "Unknown FourCC. Setting mimeType to " + MimeTypes.VIDEO_UNKNOWN);
      return new Pair<>(MimeTypes.VIDEO_UNKNOWN, null);
    }

    /**
     * Builds initialization data for a {@link Format} from Vorbis codec private data.
     *
     * @return The initialization data for the {@link Format}.
     * @throws ParserException If the initialization data could not be built.
     */
    private static List<byte[]> parseVorbisCodecPrivate(byte[] codecPrivate)
        throws ParserException {
      try {
        if (codecPrivate[0] != 0x02) {
          throw ParserException.createForMalformedContainer(
              "Error parsing vorbis codec private", /* cause= */ null);
        }
        int offset = 1;
        int vorbisInfoLength = 0;
        while ((codecPrivate[offset] & 0xFF) == 0xFF) {
          vorbisInfoLength += 0xFF;
          offset++;
        }
        vorbisInfoLength += codecPrivate[offset++] & 0xFF;

        int vorbisSkipLength = 0;
        while ((codecPrivate[offset] & 0xFF) == 0xFF) {
          vorbisSkipLength += 0xFF;
          offset++;
        }
        vorbisSkipLength += codecPrivate[offset++] & 0xFF;

        if (codecPrivate[offset] != 0x01) {
          throw ParserException.createForMalformedContainer(
              "Error parsing vorbis codec private", /* cause= */ null);
        }
        byte[] vorbisInfo = new byte[vorbisInfoLength];
        System.arraycopy(codecPrivate, offset, vorbisInfo, 0, vorbisInfoLength);
        offset += vorbisInfoLength;
        if (codecPrivate[offset] != 0x03) {
          throw ParserException.createForMalformedContainer(
              "Error parsing vorbis codec private", /* cause= */ null);
        }
        offset += vorbisSkipLength;
        if (codecPrivate[offset] != 0x05) {
          throw ParserException.createForMalformedContainer(
              "Error parsing vorbis codec private", /* cause= */ null);
        }
        byte[] vorbisBooks = new byte[codecPrivate.length - offset];
        System.arraycopy(codecPrivate, offset, vorbisBooks, 0, codecPrivate.length - offset);
        List<byte[]> initializationData = new ArrayList<>(2);
        initializationData.add(vorbisInfo);
        initializationData.add(vorbisBooks);
        return initializationData;
      } catch (ArrayIndexOutOfBoundsException e) {
        throw ParserException.createForMalformedContainer(
            "Error parsing vorbis codec private", /* cause= */ null);
      }
    }

    /**
     * Parses an MS/ACM codec private, returning whether it indicates PCM audio.
     *
     * @return Whether the codec private indicates PCM audio.
     * @throws ParserException If a parsing error occurs.
     */
    private static boolean parseMsAcmCodecPrivate(ParsableByteArray buffer) throws ParserException {
      try {
        int formatTag = buffer.readLittleEndianUnsignedShort();
        if (formatTag == WAVE_FORMAT_PCM) {
          return true;
        } else if (formatTag == WAVE_FORMAT_EXTENSIBLE) {
          buffer.setPosition(WAVE_FORMAT_SIZE + 6); // unionSamples(2), channelMask(4)
          return buffer.readLong() == WAVE_SUBFORMAT_PCM.getMostSignificantBits()
              && buffer.readLong() == WAVE_SUBFORMAT_PCM.getLeastSignificantBits();
        } else {
          return false;
        }
      } catch (ArrayIndexOutOfBoundsException e) {
        throw ParserException.createForMalformedContainer(
            "Error parsing MS/ACM codec private", /* cause= */ null);
      }
    }

    /**
     * Checks that the track has an output.
     *
     * <p>It is unfortunately not possible to mark {@link MatroskaExtractor#tracks} as only
     * containing tracks with output with the nullness checker. This method is used to check that
     * fact at runtime.
     */
    @EnsuresNonNull("output")
    private void assertOutputInitialized() {
      checkNotNull(output);
    }

    @EnsuresNonNull("codecPrivate")
    private byte[] getCodecPrivate(String codecId) throws ParserException {
      if (codecPrivate == null) {
        throw ParserException.createForMalformedContainer(
            "Missing CodecPrivate for codec " + codecId, /* cause= */ null);
      }
      return codecPrivate;
    }
  }
}

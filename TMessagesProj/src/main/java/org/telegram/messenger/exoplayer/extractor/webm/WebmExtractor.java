/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.extractor.webm;

import android.util.Pair;
import android.util.SparseArray;
import org.telegram.messenger.exoplayer.C;
import org.telegram.messenger.exoplayer.MediaFormat;
import org.telegram.messenger.exoplayer.ParserException;
import org.telegram.messenger.exoplayer.drm.DrmInitData;
import org.telegram.messenger.exoplayer.drm.DrmInitData.SchemeInitData;
import org.telegram.messenger.exoplayer.extractor.ChunkIndex;
import org.telegram.messenger.exoplayer.extractor.Extractor;
import org.telegram.messenger.exoplayer.extractor.ExtractorInput;
import org.telegram.messenger.exoplayer.extractor.ExtractorOutput;
import org.telegram.messenger.exoplayer.extractor.PositionHolder;
import org.telegram.messenger.exoplayer.extractor.SeekMap;
import org.telegram.messenger.exoplayer.extractor.TrackOutput;
import org.telegram.messenger.exoplayer.util.LongArray;
import org.telegram.messenger.exoplayer.util.MimeTypes;
import org.telegram.messenger.exoplayer.util.NalUnitUtil;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;
import org.telegram.messenger.exoplayer.util.Util;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * An extractor to facilitate data retrieval from the WebM container format.
 * <p>
 * WebM is a subset of the EBML elements defined for Matroska. More information about EBML and
 * Matroska is available <a href="http://www.matroska.org/technical/specs/index.html">here</a>.
 * More info about WebM is <a href="http://www.webmproject.org/code/specs/container/">here</a>.
 * RFC on encrypted WebM can be found
 * <a href="http://wiki.webmproject.org/encryption/webm-encryption-rfc">here</a>.
 */
public final class WebmExtractor implements Extractor {

  private static final int BLOCK_STATE_START = 0;
  private static final int BLOCK_STATE_HEADER = 1;
  private static final int BLOCK_STATE_DATA = 2;

  private static final String DOC_TYPE_WEBM = "webm";
  private static final String DOC_TYPE_MATROSKA = "matroska";
  private static final String CODEC_ID_VP8 = "V_VP8";
  private static final String CODEC_ID_VP9 = "V_VP9";
  private static final String CODEC_ID_MPEG2 = "V_MPEG2";
  private static final String CODEC_ID_MPEG4_SP = "V_MPEG4/ISO/SP";
  private static final String CODEC_ID_MPEG4_ASP = "V_MPEG4/ISO/ASP";
  private static final String CODEC_ID_MPEG4_AP = "V_MPEG4/ISO/AP";
  private static final String CODEC_ID_H264 = "V_MPEG4/ISO/AVC";
  private static final String CODEC_ID_H265 = "V_MPEGH/ISO/HEVC";
  private static final String CODEC_ID_FOURCC = "V_MS/VFW/FOURCC";
  private static final String CODEC_ID_VORBIS = "A_VORBIS";
  private static final String CODEC_ID_OPUS = "A_OPUS";
  private static final String CODEC_ID_AAC = "A_AAC";
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
  private static final String CODEC_ID_SUBRIP = "S_TEXT/UTF8";
  private static final String CODEC_ID_VOBSUB = "S_VOBSUB";
  private static final String CODEC_ID_PGS = "S_HDMV/PGS";

  private static final int VORBIS_MAX_INPUT_SIZE = 8192;
  private static final int OPUS_MAX_INPUT_SIZE = 5760;
  private static final int MP3_MAX_INPUT_SIZE = 4096;
  private static final int ENCRYPTION_IV_SIZE = 8;
  private static final int TRACK_TYPE_AUDIO = 2;
  private static final int UNKNOWN = -1;

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
  private static final int ID_REFERENCE_BLOCK = 0xFB;
  private static final int ID_TRACKS = 0x1654AE6B;
  private static final int ID_TRACK_ENTRY = 0xAE;
  private static final int ID_TRACK_NUMBER = 0xD7;
  private static final int ID_TRACK_TYPE = 0x83;
  private static final int ID_DEFAULT_DURATION = 0x23E383;
  private static final int ID_CODEC_ID = 0x86;
  private static final int ID_CODEC_PRIVATE = 0x63A2;
  private static final int ID_CODEC_DELAY = 0x56AA;
  private static final int ID_SEEK_PRE_ROLL = 0x56BB;
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

  private static final int LACING_NONE = 0;
  private static final int LACING_XIPH = 1;
  private static final int LACING_FIXED_SIZE = 2;
  private static final int LACING_EBML = 3;

  private static final int FOURCC_COMPRESSION_VC1 = 0x31435657;

  /**
   * A template for the prefix that must be added to each subrip sample. The 12 byte end timecode
   * starting at {@link #SUBRIP_PREFIX_END_TIMECODE_OFFSET} is set to a dummy value, and must be
   * replaced with the duration of the subtitle.
   * <p>
   * Equivalent to the UTF-8 string: "1\n00:00:00,000 --> 00:00:00,000\n".
   */
  private static final byte[] SUBRIP_PREFIX = new byte[] {49, 10, 48, 48, 58, 48, 48, 58, 48, 48,
      44, 48, 48, 48, 32, 45, 45, 62, 32, 48, 48, 58, 48, 48, 58, 48, 48, 44, 48, 48, 48, 10};
  /**
   * A special end timecode indicating that a subtitle should be displayed until the next subtitle,
   * or until the end of the media in the case of the last subtitle.
   * <p>
   * Equivalent to the UTF-8 string: "            ".
   */
  private static final byte[] SUBRIP_TIMECODE_EMPTY =
      new byte[] {32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32};
  /**
   * The byte offset of the end timecode in {@link #SUBRIP_PREFIX}.
   */
  private static final int SUBRIP_PREFIX_END_TIMECODE_OFFSET = 19;
  /**
   * The length in bytes of a timecode in a subrip prefix.
   */
  private static final int SUBRIP_TIMECODE_LENGTH = 12;

  /**
   * The length in bytes of a WAVEFORMATEX structure.
   */
  private static final int WAVE_FORMAT_SIZE = 18;
  /**
   * Format tag indicating a WAVEFORMATEXTENSIBLE structure.
   */
  private static final int WAVE_FORMAT_EXTENSIBLE = 0xFFFE;
  /**
   * Format tag for PCM.
   */
  private static final int WAVE_FORMAT_PCM = 1;
  /**
   * Sub format for PCM.
   */
  private static final UUID WAVE_SUBFORMAT_PCM = new UUID(0x0100000000001000L, 0x800000AA00389B71L);

  private final EbmlReader reader;
  private final VarintReader varintReader;
  private final SparseArray<Track> tracks;

  // Temporary arrays.
  private final ParsableByteArray nalStartCode;
  private final ParsableByteArray nalLength;
  private final ParsableByteArray scratch;
  private final ParsableByteArray vorbisNumPageSamples;
  private final ParsableByteArray seekEntryIdBytes;
  private final ParsableByteArray sampleStrippedBytes;
  private final ParsableByteArray subripSample;

  private long segmentContentPosition = UNKNOWN;
  private long segmentContentSize = UNKNOWN;
  private long timecodeScale = C.UNKNOWN_TIME_US;
  private long durationTimecode = C.UNKNOWN_TIME_US;
  private long durationUs = C.UNKNOWN_TIME_US;

  // The track corresponding to the current TrackEntry element, or null.
  private Track currentTrack;

  // Whether drm init data has been sent to the output.
  private boolean sentDrmInitData;
  private boolean sentSeekMap;

  // Master seek entry related elements.
  private int seekEntryId;
  private long seekEntryPosition;

  // Cue related elements.
  private boolean seekForCues;
  private long cuesContentPosition = UNKNOWN;
  private long seekPositionAfterBuildingCues = UNKNOWN;
  private long clusterTimecodeUs = UNKNOWN;
  private LongArray cueTimesUs;
  private LongArray cueClusterPositions;
  private boolean seenClusterPositionForCurrentCuePoint;

  // Block reading state.
  private int blockState;
  private long blockTimeUs;
  private long blockDurationUs;
  private int blockLacingSampleIndex;
  private int blockLacingSampleCount;
  private int[] blockLacingSampleSizes;
  private int blockTrackNumber;
  private int blockTrackNumberLength;
  private int blockFlags;

  // Sample reading state.
  private int sampleBytesRead;
  private boolean sampleEncodingHandled;
  private int sampleCurrentNalBytesRemaining;
  private int sampleBytesWritten;
  private boolean sampleRead;
  private boolean sampleSeenReferenceBlock;

  // Extractor outputs.
  private ExtractorOutput extractorOutput;

  public WebmExtractor() {
    this(new DefaultEbmlReader());
  }

  /* package */ WebmExtractor(EbmlReader reader) {
    this.reader = reader;
    this.reader.init(new InnerEbmlReaderOutput());
    varintReader = new VarintReader();
    tracks = new SparseArray<>();
    scratch = new ParsableByteArray(4);
    vorbisNumPageSamples = new ParsableByteArray(ByteBuffer.allocate(4).putInt(-1).array());
    seekEntryIdBytes = new ParsableByteArray(4);
    nalStartCode = new ParsableByteArray(NalUnitUtil.NAL_START_CODE);
    nalLength = new ParsableByteArray(4);
    sampleStrippedBytes = new ParsableByteArray();
    subripSample = new ParsableByteArray();
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    return new Sniffer().sniff(input);
  }

  @Override
  public void init(ExtractorOutput output) {
    extractorOutput = output;
  }

  @Override
  public void seek() {
    clusterTimecodeUs = UNKNOWN;
    blockState = BLOCK_STATE_START;
    reader.reset();
    varintReader.reset();
    resetSample();
  }

  @Override
  public void release() {
    // Do nothing
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException,
      InterruptedException {
    sampleRead = false;
    boolean continueReading = true;
    while (continueReading && !sampleRead) {
      continueReading = reader.read(input);
      if (continueReading && maybeSeekForCues(seekPosition, input.getPosition())) {
        return Extractor.RESULT_SEEK;
      }
    }
    return continueReading ? Extractor.RESULT_CONTINUE : Extractor.RESULT_END_OF_INPUT;
  }

  /* package */ int getElementType(int id) {
    switch (id) {
      case ID_EBML:
      case ID_SEGMENT:
      case ID_SEEK_HEAD:
      case ID_SEEK:
      case ID_INFO:
      case ID_CLUSTER:
      case ID_TRACKS:
      case ID_TRACK_ENTRY:
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
        return EbmlReader.TYPE_MASTER;
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
      case ID_DEFAULT_DURATION:
      case ID_CODEC_DELAY:
      case ID_SEEK_PRE_ROLL:
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
        return EbmlReader.TYPE_UNSIGNED_INT;
      case ID_DOC_TYPE:
      case ID_CODEC_ID:
      case ID_LANGUAGE:
        return EbmlReader.TYPE_STRING;
      case ID_SEEK_ID:
      case ID_CONTENT_COMPRESSION_SETTINGS:
      case ID_CONTENT_ENCRYPTION_KEY_ID:
      case ID_SIMPLE_BLOCK:
      case ID_BLOCK:
      case ID_CODEC_PRIVATE:
        return EbmlReader.TYPE_BINARY;
      case ID_DURATION:
      case ID_SAMPLING_FREQUENCY:
        return EbmlReader.TYPE_FLOAT;
      default:
        return EbmlReader.TYPE_UNKNOWN;
    }
  }

  /* package */ boolean isLevel1Element(int id) {
    return id == ID_SEGMENT_INFO || id == ID_CLUSTER || id == ID_CUES || id == ID_TRACKS;
  }

  /* package */ void startMasterElement(int id, long contentPosition, long contentSize)
      throws ParserException {
    switch (id) {
      case ID_SEGMENT:
        if (segmentContentPosition != UNKNOWN && segmentContentPosition != contentPosition) {
          throw new ParserException("Multiple Segment elements not supported");
        }
        segmentContentPosition = contentPosition;
        segmentContentSize = contentSize;
        return;
      case ID_SEEK:
        seekEntryId = UNKNOWN;
        seekEntryPosition = UNKNOWN;
        return;
      case ID_CUES:
        cueTimesUs = new LongArray();
        cueClusterPositions = new LongArray();
        return;
      case ID_CUE_POINT:
        seenClusterPositionForCurrentCuePoint = false;
        return;
      case ID_CLUSTER:
        if (!sentSeekMap) {
          // We need to build cues before parsing the cluster.
          if (cuesContentPosition != UNKNOWN) {
            // We know where the Cues element is located. Seek to request it.
            seekForCues = true;
          } else {
            // We don't know where the Cues element is located. It's most likely omitted. Allow
            // playback, but disable seeking.
            extractorOutput.seekMap(SeekMap.UNSEEKABLE);
            sentSeekMap = true;
          }
        }
        return;
      case ID_BLOCK_GROUP:
        sampleSeenReferenceBlock = false;
        return;
      case ID_CONTENT_ENCODING:
        // TODO: check and fail if more than one content encoding is present.
        return;
      case ID_CONTENT_ENCRYPTION:
        currentTrack.hasContentEncryption = true;
        return;
      case ID_TRACK_ENTRY:
        currentTrack = new Track();
        return;
      default:
        return;
    }
  }

  /* package */ void endMasterElement(int id) throws ParserException {
    switch (id) {
      case ID_SEGMENT_INFO:
        if (timecodeScale == C.UNKNOWN_TIME_US) {
          // timecodeScale was omitted. Use the default value.
          timecodeScale = 1000000;
        }
        if (durationTimecode != C.UNKNOWN_TIME_US) {
          durationUs = scaleTimecodeToUs(durationTimecode);
        }
        return;
      case ID_SEEK:
        if (seekEntryId == UNKNOWN || seekEntryPosition == UNKNOWN) {
          throw new ParserException("Mandatory element SeekID or SeekPosition not found");
        }
        if (seekEntryId == ID_CUES) {
          cuesContentPosition = seekEntryPosition;
        }
        return;
      case ID_CUES:
        if (!sentSeekMap) {
          extractorOutput.seekMap(buildSeekMap());
          sentSeekMap = true;
        } else {
          // We have already built the cues. Ignore.
        }
        return;
      case ID_BLOCK_GROUP:
        if (blockState != BLOCK_STATE_DATA) {
          // We've skipped this block (due to incompatible track number).
          return;
        }
        // If the ReferenceBlock element was not found for this sample, then it is a keyframe.
        if (!sampleSeenReferenceBlock) {
          blockFlags |= C.SAMPLE_FLAG_SYNC;
        }
        commitSampleToOutput(tracks.get(blockTrackNumber), blockTimeUs);
        blockState = BLOCK_STATE_START;
        return;
      case ID_CONTENT_ENCODING:
        if (currentTrack.hasContentEncryption) {
          if (currentTrack.encryptionKeyId == null) {
            throw new ParserException("Encrypted Track found but ContentEncKeyID was not found");
          }
          if (!sentDrmInitData) {
            extractorOutput.drmInitData(new DrmInitData.Universal(
                new SchemeInitData(MimeTypes.VIDEO_WEBM, currentTrack.encryptionKeyId)));
            sentDrmInitData = true;
          }
        }
        return;
      case ID_CONTENT_ENCODINGS:
        if (currentTrack.hasContentEncryption && currentTrack.sampleStrippedBytes != null) {
          throw new ParserException("Combining encryption and compression is not supported");
        }
        return;
      case ID_TRACK_ENTRY:
        if (tracks.get(currentTrack.number) == null && isCodecSupported(currentTrack.codecId)) {
          currentTrack.initializeOutput(extractorOutput, currentTrack.number, durationUs);
          tracks.put(currentTrack.number, currentTrack);
        } else {
          // We've seen this track entry before, or the codec is unsupported. Do nothing.
        }
        currentTrack = null;
        return;
      case ID_TRACKS:
        if (tracks.size() == 0) {
          throw new ParserException("No valid tracks were found");
        }
        extractorOutput.endTracks();
        return;
      default:
        return;
    }
  }

  /* package */ void integerElement(int id, long value) throws ParserException {
    switch (id) {
      case ID_EBML_READ_VERSION:
        // Validate that EBMLReadVersion is supported. This extractor only supports v1.
        if (value != 1) {
          throw new ParserException("EBMLReadVersion " + value + " not supported");
        }
        return;
      case ID_DOC_TYPE_READ_VERSION:
        // Validate that DocTypeReadVersion is supported. This extractor only supports up to v2.
        if (value < 1 || value > 2) {
          throw new ParserException("DocTypeReadVersion " + value + " not supported");
        }
        return;
      case ID_SEEK_POSITION:
        // Seek Position is the relative offset beginning from the Segment. So to get absolute
        // offset from the beginning of the file, we need to add segmentContentPosition to it.
        seekEntryPosition = value + segmentContentPosition;
        return;
      case ID_TIMECODE_SCALE:
        timecodeScale = value;
        return;
      case ID_PIXEL_WIDTH:
        currentTrack.width = (int) value;
        return;
      case ID_PIXEL_HEIGHT:
        currentTrack.height = (int) value;
        return;
      case ID_DISPLAY_WIDTH:
        currentTrack.displayWidth = (int) value;
        return;
      case ID_DISPLAY_HEIGHT:
        currentTrack.displayHeight = (int) value;
        return;
      case ID_DISPLAY_UNIT:
        currentTrack.displayUnit = (int) value;
        return;
      case ID_TRACK_NUMBER:
        currentTrack.number = (int) value;
        return;
      case ID_TRACK_TYPE:
        currentTrack.type = (int) value;
        return;
      case ID_DEFAULT_DURATION:
        currentTrack.defaultSampleDurationNs = (int) value;
        return;
      case ID_CODEC_DELAY:
        currentTrack.codecDelayNs = value;
        return;
      case ID_SEEK_PRE_ROLL:
        currentTrack.seekPreRollNs = value;
        return;
      case ID_CHANNELS:
        currentTrack.channelCount = (int) value;
        return;
      case ID_AUDIO_BIT_DEPTH:
        currentTrack.audioBitDepth = (int) value;
        return;
      case ID_REFERENCE_BLOCK:
        sampleSeenReferenceBlock = true;
        return;
      case ID_CONTENT_ENCODING_ORDER:
        // This extractor only supports one ContentEncoding element and hence the order has to be 0.
        if (value != 0) {
          throw new ParserException("ContentEncodingOrder " + value + " not supported");
        }
        return;
      case ID_CONTENT_ENCODING_SCOPE:
        // This extractor only supports the scope of all frames.
        if (value != 1) {
          throw new ParserException("ContentEncodingScope " + value + " not supported");
        }
        return;
      case ID_CONTENT_COMPRESSION_ALGORITHM:
        // This extractor only supports header stripping.
        if (value != 3) {
          throw new ParserException("ContentCompAlgo " + value + " not supported");
        }
        return;
      case ID_CONTENT_ENCRYPTION_ALGORITHM:
        // Only the value 5 (AES) is allowed according to the WebM specification.
        if (value != 5) {
          throw new ParserException("ContentEncAlgo " + value + " not supported");
        }
        return;
      case ID_CONTENT_ENCRYPTION_AES_SETTINGS_CIPHER_MODE:
        // Only the value 1 is allowed according to the WebM specification.
        if (value != 1) {
          throw new ParserException("AESSettingsCipherMode " + value + " not supported");
        }
        return;
      case ID_CUE_TIME:
        cueTimesUs.add(scaleTimecodeToUs(value));
        return;
      case ID_CUE_CLUSTER_POSITION:
        if (!seenClusterPositionForCurrentCuePoint) {
          // If there's more than one video/audio track, then there could be more than one
          // CueTrackPositions within a single CuePoint. In such a case, ignore all but the first
          // one (since the cluster position will be quite close for all the tracks).
          cueClusterPositions.add(value);
          seenClusterPositionForCurrentCuePoint = true;
        }
        return;
      case ID_TIME_CODE:
        clusterTimecodeUs = scaleTimecodeToUs(value);
        return;
      case ID_BLOCK_DURATION:
        blockDurationUs = scaleTimecodeToUs(value);
        return;
      default:
        return;
    }
  }

  /* package */ void floatElement(int id, double value) {
    switch (id) {
      case ID_DURATION:
        durationTimecode = (long) value;
        return;
      case ID_SAMPLING_FREQUENCY:
        currentTrack.sampleRate = (int) value;
        return;
      default:
        return;
    }
  }

  /* package */ void stringElement(int id, String value) throws ParserException {
    switch (id) {
      case ID_DOC_TYPE:
        // Validate that DocType is supported.
        if (!DOC_TYPE_WEBM.equals(value) && !DOC_TYPE_MATROSKA.equals(value)) {
          throw new ParserException("DocType " + value + " not supported");
        }
        return;
      case ID_CODEC_ID:
        currentTrack.codecId = value;
        return;
      case ID_LANGUAGE:
        currentTrack.language = value;
        return;
      default:
        return;
    }
  }

  /* package */ void binaryElement(int id, int contentSize, ExtractorInput input)
      throws IOException, InterruptedException {
    switch (id) {
      case ID_SEEK_ID:
        Arrays.fill(seekEntryIdBytes.data, (byte) 0);
        input.readFully(seekEntryIdBytes.data, 4 - contentSize, contentSize);
        seekEntryIdBytes.setPosition(0);
        seekEntryId = (int) seekEntryIdBytes.readUnsignedInt();
        return;
      case ID_CODEC_PRIVATE:
        currentTrack.codecPrivate = new byte[contentSize];
        input.readFully(currentTrack.codecPrivate, 0, contentSize);
        return;
      case ID_CONTENT_COMPRESSION_SETTINGS:
        // This extractor only supports header stripping, so the payload is the stripped bytes.
        currentTrack.sampleStrippedBytes = new byte[contentSize];
        input.readFully(currentTrack.sampleStrippedBytes, 0, contentSize);
        return;
      case ID_CONTENT_ENCRYPTION_KEY_ID:
        currentTrack.encryptionKeyId = new byte[contentSize];
        input.readFully(currentTrack.encryptionKeyId, 0, contentSize);
        return;
      case ID_SIMPLE_BLOCK:
      case ID_BLOCK:
        // Please refer to http://www.matroska.org/technical/specs/index.html#simpleblock_structure
        // and http://matroska.org/technical/specs/index.html#block_structure
        // for info about how data is organized in SimpleBlock and Block elements respectively. They
        // differ only in the way flags are specified.

        if (blockState == BLOCK_STATE_START) {
          blockTrackNumber = (int) varintReader.readUnsignedVarint(input, false, true, 8);
          blockTrackNumberLength = varintReader.getLastLength();
          blockDurationUs = UNKNOWN;
          blockState = BLOCK_STATE_HEADER;
          scratch.reset();
        }

        Track track = tracks.get(blockTrackNumber);

        // Ignore the block if we don't know about the track to which it belongs.
        if (track == null) {
          input.skipFully(contentSize - blockTrackNumberLength);
          blockState = BLOCK_STATE_START;
          return;
        }

        if (blockState == BLOCK_STATE_HEADER) {
          // Read the relative timecode (2 bytes) and flags (1 byte).
          readScratch(input, 3);
          int lacing = (scratch.data[2] & 0x06) >> 1;
          if (lacing == LACING_NONE) {
            blockLacingSampleCount = 1;
            blockLacingSampleSizes = ensureArrayCapacity(blockLacingSampleSizes, 1);
            blockLacingSampleSizes[0] = contentSize - blockTrackNumberLength - 3;
          } else {
            if (id != ID_SIMPLE_BLOCK) {
              throw new ParserException("Lacing only supported in SimpleBlocks.");
            }

            // Read the sample count (1 byte).
            readScratch(input, 4);
            blockLacingSampleCount = (scratch.data[3] & 0xFF) + 1;
            blockLacingSampleSizes =
                ensureArrayCapacity(blockLacingSampleSizes, blockLacingSampleCount);
            if (lacing == LACING_FIXED_SIZE) {
              int blockLacingSampleSize =
                  (contentSize - blockTrackNumberLength - 4) / blockLacingSampleCount;
              Arrays.fill(blockLacingSampleSizes, 0, blockLacingSampleCount, blockLacingSampleSize);
            } else if (lacing == LACING_XIPH) {
              int totalSamplesSize = 0;
              int headerSize = 4;
              for (int sampleIndex = 0; sampleIndex < blockLacingSampleCount - 1; sampleIndex++) {
                blockLacingSampleSizes[sampleIndex] = 0;
                int byteValue;
                do {
                  readScratch(input, ++headerSize);
                  byteValue = scratch.data[headerSize - 1] & 0xFF;
                  blockLacingSampleSizes[sampleIndex] += byteValue;
                } while (byteValue == 0xFF);
                totalSamplesSize += blockLacingSampleSizes[sampleIndex];
              }
              blockLacingSampleSizes[blockLacingSampleCount - 1] =
                  contentSize - blockTrackNumberLength - headerSize - totalSamplesSize;
            } else if (lacing == LACING_EBML) {
              int totalSamplesSize = 0;
              int headerSize = 4;
              for (int sampleIndex = 0; sampleIndex < blockLacingSampleCount - 1; sampleIndex++) {
                blockLacingSampleSizes[sampleIndex] = 0;
                readScratch(input, ++headerSize);
                if (scratch.data[headerSize - 1] == 0) {
                  throw new ParserException("No valid varint length mask found");
                }
                long readValue = 0;
                for (int i = 0; i < 8; i++) {
                  int lengthMask = 1 << (7 - i);
                  if ((scratch.data[headerSize - 1] & lengthMask) != 0) {
                    int readPosition = headerSize - 1;
                    headerSize += i;
                    readScratch(input, headerSize);
                    readValue = (scratch.data[readPosition++] & 0xFF) & ~lengthMask;
                    while (readPosition < headerSize) {
                      readValue <<= 8;
                      readValue |= (scratch.data[readPosition++] & 0xFF);
                    }
                    // The first read value is the first size. Later values are signed offsets.
                    if (sampleIndex > 0) {
                      readValue -= (1L << 6 + i * 7) - 1;
                    }
                    break;
                  }
                }
                if (readValue < Integer.MIN_VALUE || readValue > Integer.MAX_VALUE) {
                  throw new ParserException("EBML lacing sample size out of range.");
                }
                int intReadValue = (int) readValue;
                blockLacingSampleSizes[sampleIndex] = sampleIndex == 0
                    ? intReadValue : blockLacingSampleSizes[sampleIndex - 1] + intReadValue;
                totalSamplesSize += blockLacingSampleSizes[sampleIndex];
              }
              blockLacingSampleSizes[blockLacingSampleCount - 1] =
                  contentSize - blockTrackNumberLength - headerSize - totalSamplesSize;
            } else {
              // Lacing is always in the range 0--3.
              throw new ParserException("Unexpected lacing value: " + lacing);
            }
          }

          int timecode = (scratch.data[0] << 8) | (scratch.data[1] & 0xFF);
          blockTimeUs = clusterTimecodeUs + scaleTimecodeToUs(timecode);
          boolean isInvisible = (scratch.data[2] & 0x08) == 0x08;
          boolean isKeyframe = track.type == TRACK_TYPE_AUDIO
              || (id == ID_SIMPLE_BLOCK && (scratch.data[2] & 0x80) == 0x80);
          blockFlags = (isKeyframe ? C.SAMPLE_FLAG_SYNC : 0)
              | (isInvisible ? C.SAMPLE_FLAG_DECODE_ONLY : 0);
          blockState = BLOCK_STATE_DATA;
          blockLacingSampleIndex = 0;
        }

        if (id == ID_SIMPLE_BLOCK) {
          // For SimpleBlock, we have metadata for each sample here.
          while (blockLacingSampleIndex < blockLacingSampleCount) {
            writeSampleData(input, track, blockLacingSampleSizes[blockLacingSampleIndex]);
            long sampleTimeUs = this.blockTimeUs
                + (blockLacingSampleIndex * track.defaultSampleDurationNs) / 1000;
            commitSampleToOutput(track, sampleTimeUs);
            blockLacingSampleIndex++;
          }
          blockState = BLOCK_STATE_START;
        } else {
          // For Block, we send the metadata at the end of the BlockGroup element since we'll know
          // if the sample is a keyframe or not only at that point.
          writeSampleData(input, track, blockLacingSampleSizes[0]);
        }

        return;
      default:
        throw new ParserException("Unexpected id: " + id);
    }
  }

  private void commitSampleToOutput(Track track, long timeUs) {
    if (CODEC_ID_SUBRIP.equals(track.codecId)) {
      writeSubripSample(track);
    }
    track.output.sampleMetadata(timeUs, blockFlags, sampleBytesWritten, 0, track.encryptionKeyId);
    sampleRead = true;
    resetSample();
  }

  private void resetSample() {
    sampleBytesRead = 0;
    sampleBytesWritten = 0;
    sampleCurrentNalBytesRemaining = 0;
    sampleEncodingHandled = false;
    sampleStrippedBytes.reset();
  }

  /**
   * Ensures {@link #scratch} contains at least {@code requiredLength} bytes of data, reading from
   * the extractor input if necessary.
   */
  private void readScratch(ExtractorInput input, int requiredLength)
      throws IOException, InterruptedException {
    if (scratch.limit() >= requiredLength) {
      return;
    }
    if (scratch.capacity() < requiredLength) {
      scratch.reset(Arrays.copyOf(scratch.data, Math.max(scratch.data.length * 2, requiredLength)),
          scratch.limit());
    }
    input.readFully(scratch.data, scratch.limit(), requiredLength - scratch.limit());
    scratch.setLimit(requiredLength);
  }

  private void writeSampleData(ExtractorInput input, Track track, int size)
      throws IOException, InterruptedException {
    if (CODEC_ID_SUBRIP.equals(track.codecId)) {
      int sizeWithPrefix = SUBRIP_PREFIX.length + size;
      if (subripSample.capacity() < sizeWithPrefix) {
        // Initialize subripSample to contain the required prefix and have space to hold a subtitle
        // twice as long as this one.
        subripSample.data = Arrays.copyOf(SUBRIP_PREFIX, sizeWithPrefix + size);
      }
      input.readFully(subripSample.data, SUBRIP_PREFIX.length, size);
      subripSample.setPosition(0);
      subripSample.setLimit(sizeWithPrefix);
      // Defer writing the data to the track output. We need to modify the sample data by setting
      // the correct end timecode, which we might not have yet.
      return;
    }

    TrackOutput output = track.output;
    if (!sampleEncodingHandled) {
      if (track.hasContentEncryption) {
        // If the sample is encrypted, read its encryption signal byte and set the IV size.
        // Clear the encrypted flag.
        blockFlags &= ~C.SAMPLE_FLAG_ENCRYPTED;
        input.readFully(scratch.data, 0, 1);
        sampleBytesRead++;
        if ((scratch.data[0] & 0x80) == 0x80) {
          throw new ParserException("Extension bit is set in signal byte");
        }
        if ((scratch.data[0] & 0x01) == 0x01) {
          scratch.data[0] = (byte) ENCRYPTION_IV_SIZE;
          scratch.setPosition(0);
          output.sampleData(scratch, 1);
          sampleBytesWritten++;
          blockFlags |= C.SAMPLE_FLAG_ENCRYPTED;
        }
      } else if (track.sampleStrippedBytes != null) {
        // If the sample has header stripping, prepare to read/output the stripped bytes first.
        sampleStrippedBytes.reset(track.sampleStrippedBytes, track.sampleStrippedBytes.length);
      }
      sampleEncodingHandled = true;
    }
    size += sampleStrippedBytes.limit();

    if (CODEC_ID_H264.equals(track.codecId) || CODEC_ID_H265.equals(track.codecId)) {
      // TODO: Deduplicate with Mp4Extractor.

      // Zero the top three bytes of the array that we'll use to parse nal unit lengths, in case
      // they're only 1 or 2 bytes long.
      byte[] nalLengthData = nalLength.data;
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
          readToTarget(input, nalLengthData, nalUnitLengthFieldLengthDiff,
              nalUnitLengthFieldLength);
          nalLength.setPosition(0);
          sampleCurrentNalBytesRemaining = nalLength.readUnsignedIntToInt();
          // Write a start code for the current NAL unit.
          nalStartCode.setPosition(0);
          output.sampleData(nalStartCode, 4);
          sampleBytesWritten += 4;
        } else {
          // Write the payload of the NAL unit.
          sampleCurrentNalBytesRemaining -=
              readToOutput(input, output, sampleCurrentNalBytesRemaining);
        }
      }
    } else {
      while (sampleBytesRead < size) {
        readToOutput(input, output, size - sampleBytesRead);
      }
    }

    if (CODEC_ID_VORBIS.equals(track.codecId)) {
      // Vorbis decoder in android MediaCodec [1] expects the last 4 bytes of the sample to be the
      // number of samples in the current page. This definition holds good only for Ogg and
      // irrelevant for WebM. So we always set this to -1 (the decoder will ignore this value if we
      // set it to -1). The android platform media extractor [2] does the same.
      // [1] https://android.googlesource.com/platform/frameworks/av/+/lollipop-release/media/libstagefright/codecs/vorbis/dec/SoftVorbis.cpp#314
      // [2] https://android.googlesource.com/platform/frameworks/av/+/lollipop-release/media/libstagefright/NuMediaExtractor.cpp#474
      vorbisNumPageSamples.setPosition(0);
      output.sampleData(vorbisNumPageSamples, 4);
      sampleBytesWritten += 4;
    }
  }

  private void writeSubripSample(Track track) {
    setSubripSampleEndTimecode(subripSample.data, blockDurationUs);
    // Note: If we ever want to support DRM protected subtitles then we'll need to output the
    // appropriate encryption data here.
    track.output.sampleData(subripSample, subripSample.limit());
    sampleBytesWritten += subripSample.limit();
  }

  private static void setSubripSampleEndTimecode(byte[] subripSampleData, long timeUs) {
    byte[] timeCodeData;
    if (timeUs == UNKNOWN) {
      timeCodeData = SUBRIP_TIMECODE_EMPTY;
    } else {
      int hours = (int) (timeUs / 3600000000L);
      timeUs -= (hours * 3600000000L);
      int minutes = (int) (timeUs / 60000000);
      timeUs -= (minutes * 60000000);
      int seconds = (int) (timeUs / 1000000);
      timeUs -= (seconds * 1000000);
      int milliseconds = (int) (timeUs / 1000);
      timeCodeData = String.format(Locale.US, "%02d:%02d:%02d,%03d",
          hours, minutes, seconds, milliseconds).getBytes();
    }
    System.arraycopy(timeCodeData, 0, subripSampleData, SUBRIP_PREFIX_END_TIMECODE_OFFSET,
        SUBRIP_TIMECODE_LENGTH);
  }

  /**
   * Writes {@code length} bytes of sample data into {@code target} at {@code offset}, consisting of
   * pending {@link #sampleStrippedBytes} and any remaining data read from {@code input}.
   */
  private void readToTarget(ExtractorInput input, byte[] target, int offset, int length)
      throws IOException, InterruptedException {
    int pendingStrippedBytes = Math.min(length, sampleStrippedBytes.bytesLeft());
    input.readFully(target, offset + pendingStrippedBytes, length - pendingStrippedBytes);
    if (pendingStrippedBytes > 0) {
      sampleStrippedBytes.readBytes(target, offset, pendingStrippedBytes);
    }
    sampleBytesRead += length;
  }

  /**
   * Outputs up to {@code length} bytes of sample data to {@code output}, consisting of either
   * {@link #sampleStrippedBytes} or data read from {@code input}.
   */
  private int readToOutput(ExtractorInput input, TrackOutput output, int length)
      throws IOException, InterruptedException {
    int bytesRead;
    int strippedBytesLeft = sampleStrippedBytes.bytesLeft();
    if (strippedBytesLeft > 0) {
      bytesRead = Math.min(length, strippedBytesLeft);
      output.sampleData(sampleStrippedBytes, bytesRead);
    } else {
      bytesRead = output.sampleData(input, length, false);
    }
    sampleBytesRead += bytesRead;
    sampleBytesWritten += bytesRead;
    return bytesRead;
  }

  /**
   * Builds a {@link SeekMap} from the recently gathered Cues information.
   *
   * @return The built {@link SeekMap}. May be {@link SeekMap#UNSEEKABLE} if cues information was
   *     missing or incomplete.
   */
  private SeekMap buildSeekMap() {
    if (segmentContentPosition == UNKNOWN || durationUs == C.UNKNOWN_TIME_US
        || cueTimesUs == null || cueTimesUs.size() == 0
        || cueClusterPositions == null || cueClusterPositions.size() != cueTimesUs.size()) {
      // Cues information is missing or incomplete.
      cueTimesUs = null;
      cueClusterPositions = null;
      return SeekMap.UNSEEKABLE;
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
    cueTimesUs = null;
    cueClusterPositions = null;
    return new ChunkIndex(sizes, offsets, durationsUs, timesUs);
  }

  /**
   * Updates the position of the holder to Cues element's position if the extractor configuration
   * permits use of master seek entry. After building Cues sets the holder's position back to where
   * it was before.
   *
   * @param seekPosition The holder whose position will be updated.
   * @param currentPosition Current position of the input.
   * @return true if the seek position was updated, false otherwise.
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
    if (sentSeekMap && seekPositionAfterBuildingCues != UNKNOWN) {
      seekPosition.position = seekPositionAfterBuildingCues;
      seekPositionAfterBuildingCues = UNKNOWN;
      return true;
    }
    return false;
  }

  private long scaleTimecodeToUs(long unscaledTimecode) throws ParserException {
    if (timecodeScale == C.UNKNOWN_TIME_US) {
      throw new ParserException("Can't scale timecode prior to timecodeScale being set.");
    }
    return Util.scaleLargeTimestamp(unscaledTimecode, timecodeScale, 1000);
  }

  private static boolean isCodecSupported(String codecId) {
    return CODEC_ID_VP8.equals(codecId)
        || CODEC_ID_VP9.equals(codecId)
        || CODEC_ID_MPEG2.equals(codecId)
        || CODEC_ID_MPEG4_SP.equals(codecId)
        || CODEC_ID_MPEG4_ASP.equals(codecId)
        || CODEC_ID_MPEG4_AP.equals(codecId)
        || CODEC_ID_H264.equals(codecId)
        || CODEC_ID_H265.equals(codecId)
        || CODEC_ID_FOURCC.equals(codecId)
        || CODEC_ID_OPUS.equals(codecId)
        || CODEC_ID_VORBIS.equals(codecId)
        || CODEC_ID_AAC.equals(codecId)
        || CODEC_ID_MP3.equals(codecId)
        || CODEC_ID_AC3.equals(codecId)
        || CODEC_ID_E_AC3.equals(codecId)
        || CODEC_ID_TRUEHD.equals(codecId)
        || CODEC_ID_DTS.equals(codecId)
        || CODEC_ID_DTS_EXPRESS.equals(codecId)
        || CODEC_ID_DTS_LOSSLESS.equals(codecId)
        || CODEC_ID_FLAC.equals(codecId)
        || CODEC_ID_ACM.equals(codecId)
        || CODEC_ID_PCM_INT_LIT.equals(codecId)
        || CODEC_ID_SUBRIP.equals(codecId)
        || CODEC_ID_VOBSUB.equals(codecId)
        || CODEC_ID_PGS.equals(codecId);
  }

  /**
   * Returns an array that can store (at least) {@code length} elements, which will be either a new
   * array or {@code array} if it's not null and large enough.
   */
  private static int[] ensureArrayCapacity(int[] array, int length) {
    if (array == null) {
      return new int[length];
    } else if (array.length >= length) {
      return array;
    } else {
      // Double the size to avoid allocating constantly if the required length increases gradually.
      return new int[Math.max(array.length * 2, length)];
    }
  }

  /**
   * Passes events through to the outer {@link WebmExtractor}.
   */
  private final class InnerEbmlReaderOutput implements EbmlReaderOutput {

    @Override
    public int getElementType(int id) {
      return WebmExtractor.this.getElementType(id);
    }

    @Override
    public boolean isLevel1Element(int id) {
      return WebmExtractor.this.isLevel1Element(id);
    }

    @Override
    public void startMasterElement(int id, long contentPosition, long contentSize)
        throws ParserException {
      WebmExtractor.this.startMasterElement(id, contentPosition, contentSize);
    }

    @Override
    public void endMasterElement(int id) throws ParserException {
      WebmExtractor.this.endMasterElement(id);
    }

    @Override
    public void integerElement(int id, long value) throws ParserException {
      WebmExtractor.this.integerElement(id, value);
    }

    @Override
    public void floatElement(int id, double value) throws ParserException {
      WebmExtractor.this.floatElement(id, value);
    }

    @Override
    public void stringElement(int id, String value) throws ParserException {
      WebmExtractor.this.stringElement(id, value);
    }

    @Override
    public void binaryElement(int id, int contentsSize, ExtractorInput input)
        throws IOException, InterruptedException {
      WebmExtractor.this.binaryElement(id, contentsSize, input);
    }

  }

  private static final class Track {

    private static final int DISPLAY_UNIT_PIXELS = 0;

    // Common elements.
    public String codecId;
    public int number;
    public int type;
    public int defaultSampleDurationNs;
    public boolean hasContentEncryption;
    public byte[] sampleStrippedBytes;
    public byte[] encryptionKeyId;
    public byte[] codecPrivate;

    // Video elements.
    public int width = MediaFormat.NO_VALUE;
    public int height = MediaFormat.NO_VALUE;
    public int displayWidth = MediaFormat.NO_VALUE;
    public int displayHeight = MediaFormat.NO_VALUE;
    public int displayUnit = DISPLAY_UNIT_PIXELS;

    // Audio elements. Initially set to their default values.
    public int channelCount = 1;
    public int audioBitDepth = -1;
    public int sampleRate = 8000;
    public long codecDelayNs = 0;
    public long seekPreRollNs = 0;

    // Text elements.
    private String language = "eng";

    // Set when the output is initialized. nalUnitLengthFieldLength is only set for H264/H265.
    public TrackOutput output;
    public int nalUnitLengthFieldLength;

    /**
     * Initializes the track with an output.
     */
    public void initializeOutput(ExtractorOutput output, int trackId, long durationUs)
        throws ParserException {
      String mimeType;
      int maxInputSize = MediaFormat.NO_VALUE;
      int pcmEncoding = MediaFormat.NO_VALUE;
      List<byte[]> initializationData = null;
      switch (codecId) {
        case CODEC_ID_VP8:
          mimeType = MimeTypes.VIDEO_VP8;
          break;
        case CODEC_ID_VP9:
          mimeType = MimeTypes.VIDEO_VP9;
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
          Pair<List<byte[]>, Integer> h264Data = parseAvcCodecPrivate(
              new ParsableByteArray(codecPrivate));
          initializationData = h264Data.first;
          nalUnitLengthFieldLength = h264Data.second;
          break;
        case CODEC_ID_H265:
          mimeType = MimeTypes.VIDEO_H265;
          Pair<List<byte[]>, Integer> hevcData = parseHevcCodecPrivate(
              new ParsableByteArray(codecPrivate));
          initializationData = hevcData.first;
          nalUnitLengthFieldLength = hevcData.second;
          break;
        case CODEC_ID_FOURCC:
          mimeType = MimeTypes.VIDEO_VC1;
          initializationData = parseFourCcVc1Private(new ParsableByteArray(codecPrivate));
          break;
        case CODEC_ID_VORBIS:
          mimeType = MimeTypes.AUDIO_VORBIS;
          maxInputSize = VORBIS_MAX_INPUT_SIZE;
          initializationData = parseVorbisCodecPrivate(codecPrivate);
          break;
        case CODEC_ID_OPUS:
          mimeType = MimeTypes.AUDIO_OPUS;
          maxInputSize = OPUS_MAX_INPUT_SIZE;
          initializationData = new ArrayList<>(3);
          initializationData.add(codecPrivate);
          initializationData.add(
              ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(codecDelayNs).array());
          initializationData.add(
              ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(seekPreRollNs).array());
          break;
        case CODEC_ID_AAC:
          mimeType = MimeTypes.AUDIO_AAC;
          initializationData = Collections.singletonList(codecPrivate);
          break;
        case CODEC_ID_MP3:
          mimeType = MimeTypes.AUDIO_MPEG;
          maxInputSize = MP3_MAX_INPUT_SIZE;
          break;
        case CODEC_ID_AC3:
          mimeType = MimeTypes.AUDIO_AC3;
          break;
        case CODEC_ID_E_AC3:
          mimeType = MimeTypes.AUDIO_E_AC3;
          break;
        case CODEC_ID_TRUEHD:
          mimeType = MimeTypes.AUDIO_TRUEHD;
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
          initializationData = Collections.singletonList(codecPrivate);
          break;
        case CODEC_ID_ACM:
          mimeType = MimeTypes.AUDIO_RAW;
          if (!parseMsAcmCodecPrivate(new ParsableByteArray(codecPrivate))) {
            throw new ParserException("Non-PCM MS/ACM is unsupported");
          }
          pcmEncoding = Util.getPcmEncoding(audioBitDepth);
          if (pcmEncoding == C.ENCODING_INVALID) {
            throw new ParserException("Unsupported PCM bit depth: " + audioBitDepth);
          }
          break;
        case CODEC_ID_PCM_INT_LIT:
          mimeType = MimeTypes.AUDIO_RAW;
          pcmEncoding = Util.getPcmEncoding(audioBitDepth);
          if (pcmEncoding == C.ENCODING_INVALID) {
            throw new ParserException("Unsupported PCM bit depth: " + audioBitDepth);
          }
          break;
        case CODEC_ID_SUBRIP:
          mimeType = MimeTypes.APPLICATION_SUBRIP;
          break;
        case CODEC_ID_VOBSUB:
          mimeType = MimeTypes.APPLICATION_VOBSUB;
          initializationData = Collections.singletonList(codecPrivate);
          break;
        case CODEC_ID_PGS:
          mimeType = MimeTypes.APPLICATION_PGS;
          break;
        default:
          throw new ParserException("Unrecognized codec identifier.");
      }

      MediaFormat format;
      // TODO: Consider reading the name elements of the tracks and, if present, incorporating them
      // into the trackId passed when creating the formats.
      if (MimeTypes.isAudio(mimeType)) {
        format = MediaFormat.createAudioFormat(Integer.toString(trackId), mimeType,
            MediaFormat.NO_VALUE, maxInputSize, durationUs, channelCount, sampleRate,
            initializationData, language, pcmEncoding);
      } else if (MimeTypes.isVideo(mimeType)) {
        if (displayUnit == Track.DISPLAY_UNIT_PIXELS) {
          displayWidth = displayWidth == MediaFormat.NO_VALUE ? width : displayWidth;
          displayHeight = displayHeight == MediaFormat.NO_VALUE ? height : displayHeight;
        }
        float pixelWidthHeightRatio = MediaFormat.NO_VALUE;
        if (displayWidth != MediaFormat.NO_VALUE && displayHeight != MediaFormat.NO_VALUE) {
          pixelWidthHeightRatio = ((float) (height * displayWidth)) / (width * displayHeight);
        }
        format = MediaFormat.createVideoFormat(Integer.toString(trackId), mimeType,
            MediaFormat.NO_VALUE, maxInputSize, durationUs, width, height, initializationData,
            MediaFormat.NO_VALUE, pixelWidthHeightRatio);
      } else if (MimeTypes.APPLICATION_SUBRIP.equals(mimeType)) {
        format = MediaFormat.createTextFormat(Integer.toString(trackId), mimeType,
            MediaFormat.NO_VALUE, durationUs, language);
      } else if (MimeTypes.APPLICATION_VOBSUB.equals(mimeType)
          || MimeTypes.APPLICATION_PGS.equals(mimeType)) {
        format = MediaFormat.createImageFormat(Integer.toString(trackId), mimeType,
            MediaFormat.NO_VALUE, durationUs, initializationData, language);
      } else {
        throw new ParserException("Unexpected MIME type.");
      }

      this.output = output.track(number);
      this.output.format(format);
    }

    /**
     * Builds initialization data for a {@link MediaFormat} from FourCC codec private data.
     * <p>
     * VC1 is the only supported compression type.
     *
     * @return The initialization data for the {@link MediaFormat}.
     * @throws ParserException If the initialization data could not be built.
     */
    private static List<byte[]> parseFourCcVc1Private(ParsableByteArray buffer)
        throws ParserException {
      try {
        buffer.skipBytes(16); // size(4), width(4), height(4), planes(2), bitcount(2).
        long compression = buffer.readLittleEndianUnsignedInt();
        if (compression != FOURCC_COMPRESSION_VC1) {
          throw new ParserException("Unsupported FourCC compression type: " + compression);
        }

        // Search for the initialization data from the end of the BITMAPINFOHEADER. The last 20
        // bytes of which are: sizeImage(4), xPel/m (4), yPel/m (4), clrUsed(4), clrImportant(4).
        int startOffset = buffer.getPosition() + 20;
        byte[] bufferData = buffer.data;
        for (int offset = startOffset; offset < bufferData.length - 4; offset++) {
          if (bufferData[offset] == 0x00 && bufferData[offset + 1] == 0x00
              && bufferData[offset + 2] == 0x01 && bufferData[offset + 3] == 0x0F) {
            // We've found the initialization data.
            byte[] initializationData = Arrays.copyOfRange(bufferData, offset, bufferData.length);
            return Collections.singletonList(initializationData);
          }
        }

        throw new ParserException("Failed to find FourCC VC1 initialization data");
      } catch (ArrayIndexOutOfBoundsException e) {
        throw new ParserException("Error parsing FourCC VC1 codec private");
      }
    }

    /**
     * Builds initialization data for a {@link MediaFormat} from H.264 (AVC) codec private data.
     *
     * @return The initialization data for the {@link MediaFormat}.
     * @throws ParserException If the initialization data could not be built.
     */
    private static Pair<List<byte[]>, Integer> parseAvcCodecPrivate(ParsableByteArray buffer)
        throws ParserException {
      try {
        // TODO: Deduplicate with AtomParsers.parseAvcCFromParent.
        buffer.setPosition(4);
        int nalUnitLengthFieldLength = (buffer.readUnsignedByte() & 0x03) + 1;
        if (nalUnitLengthFieldLength == 3) {
          throw new ParserException();
        }
        List<byte[]> initializationData = new ArrayList<>();
        int numSequenceParameterSets = buffer.readUnsignedByte() & 0x1F;
        for (int i = 0; i < numSequenceParameterSets; i++) {
          initializationData.add(NalUnitUtil.parseChildNalUnit(buffer));
        }
        int numPictureParameterSets = buffer.readUnsignedByte();
        for (int j = 0; j < numPictureParameterSets; j++) {
          initializationData.add(NalUnitUtil.parseChildNalUnit(buffer));
        }
        return Pair.create(initializationData, nalUnitLengthFieldLength);
      } catch (ArrayIndexOutOfBoundsException e) {
        throw new ParserException("Error parsing AVC codec private");
      }
    }

    /**
     * Builds initialization data for a {@link MediaFormat} from H.265 (HEVC) codec private data.
     *
     * @return The initialization data for the {@link MediaFormat}.
     * @throws ParserException If the initialization data could not be built.
     */
    private static Pair<List<byte[]>, Integer> parseHevcCodecPrivate(ParsableByteArray parent)
        throws ParserException {
      try {
        // TODO: Deduplicate with AtomParsers.parseHvcCFromParent.
        parent.setPosition(21);
        int lengthSizeMinusOne = parent.readUnsignedByte() & 0x03;

        // Calculate the combined size of all VPS/SPS/PPS bitstreams.
        int numberOfArrays = parent.readUnsignedByte();
        int csdLength = 0;
        int csdStartPosition = parent.getPosition();
        for (int i = 0; i < numberOfArrays; i++) {
          parent.skipBytes(1); // completeness (1), nal_unit_type (7)
          int numberOfNalUnits = parent.readUnsignedShort();
          for (int j = 0; j < numberOfNalUnits; j++) {
            int nalUnitLength = parent.readUnsignedShort();
            csdLength += 4 + nalUnitLength; // Start code and NAL unit.
            parent.skipBytes(nalUnitLength);
          }
        }

        // Concatenate the codec-specific data into a single buffer.
        parent.setPosition(csdStartPosition);
        byte[] buffer = new byte[csdLength];
        int bufferPosition = 0;
        for (int i = 0; i < numberOfArrays; i++) {
          parent.skipBytes(1); // completeness (1), nal_unit_type (7)
          int numberOfNalUnits = parent.readUnsignedShort();
          for (int j = 0; j < numberOfNalUnits; j++) {
            int nalUnitLength = parent.readUnsignedShort();
            System.arraycopy(NalUnitUtil.NAL_START_CODE, 0, buffer, bufferPosition,
                NalUnitUtil.NAL_START_CODE.length);
            bufferPosition += NalUnitUtil.NAL_START_CODE.length;
            System.arraycopy(parent.data, parent.getPosition(), buffer, bufferPosition,
                nalUnitLength);
            bufferPosition += nalUnitLength;
            parent.skipBytes(nalUnitLength);
          }
        }

        List<byte[]> initializationData = csdLength == 0 ? null : Collections.singletonList(buffer);
        return Pair.create(initializationData, lengthSizeMinusOne + 1);
      } catch (ArrayIndexOutOfBoundsException e) {
        throw new ParserException("Error parsing HEVC codec private");
      }
    }

    /**
     * Builds initialization data for a {@link MediaFormat} from Vorbis codec private data.
     *
     * @return The initialization data for the {@link MediaFormat}.
     * @throws ParserException If the initialization data could not be built.
     */
    private static List<byte[]> parseVorbisCodecPrivate(byte[] codecPrivate)
        throws ParserException {
      try {
        if (codecPrivate[0] != 0x02) {
          throw new ParserException("Error parsing vorbis codec private");
        }
        int offset = 1;
        int vorbisInfoLength = 0;
        while (codecPrivate[offset] == (byte) 0xFF) {
          vorbisInfoLength += 0xFF;
          offset++;
        }
        vorbisInfoLength += codecPrivate[offset++];

        int vorbisSkipLength = 0;
        while (codecPrivate[offset] == (byte) 0xFF) {
          vorbisSkipLength += 0xFF;
          offset++;
        }
        vorbisSkipLength += codecPrivate[offset++];

        if (codecPrivate[offset] != 0x01) {
          throw new ParserException("Error parsing vorbis codec private");
        }
        byte[] vorbisInfo = new byte[vorbisInfoLength];
        System.arraycopy(codecPrivate, offset, vorbisInfo, 0, vorbisInfoLength);
        offset += vorbisInfoLength;
        if (codecPrivate[offset] != 0x03) {
          throw new ParserException("Error parsing vorbis codec private");
        }
        offset += vorbisSkipLength;
        if (codecPrivate[offset] != 0x05) {
          throw new ParserException("Error parsing vorbis codec private");
        }
        byte[] vorbisBooks = new byte[codecPrivate.length - offset];
        System.arraycopy(codecPrivate, offset, vorbisBooks, 0, codecPrivate.length - offset);
        List<byte[]> initializationData = new ArrayList<>(2);
        initializationData.add(vorbisInfo);
        initializationData.add(vorbisBooks);
        return initializationData;
      } catch (ArrayIndexOutOfBoundsException e) {
        throw new ParserException("Error parsing vorbis codec private");
      }
    }

    /**
     * Parses an MS/ACM codec private, returning whether it indicates PCM audio.
     *
     * @return True if the codec private indicates PCM audio. False otherwise.
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
        throw new ParserException("Error parsing MS/ACM codec private");
      }
    }

  }

}

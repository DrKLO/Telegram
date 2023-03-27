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
package com.google.android.exoplayer2.source.mediaparser;

import static android.media.MediaParser.PARSER_NAME_AC3;
import static android.media.MediaParser.PARSER_NAME_AC4;
import static android.media.MediaParser.PARSER_NAME_ADTS;
import static android.media.MediaParser.PARSER_NAME_AMR;
import static android.media.MediaParser.PARSER_NAME_FLAC;
import static android.media.MediaParser.PARSER_NAME_FLV;
import static android.media.MediaParser.PARSER_NAME_FMP4;
import static android.media.MediaParser.PARSER_NAME_MATROSKA;
import static android.media.MediaParser.PARSER_NAME_MP3;
import static android.media.MediaParser.PARSER_NAME_MP4;
import static android.media.MediaParser.PARSER_NAME_OGG;
import static android.media.MediaParser.PARSER_NAME_PS;
import static android.media.MediaParser.PARSER_NAME_TS;
import static android.media.MediaParser.PARSER_NAME_WAV;

import android.annotation.SuppressLint;
import android.media.DrmInitData.SchemeInitData;
import android.media.MediaCodec;
import android.media.MediaCodec.CryptoInfo;
import android.media.MediaFormat;
import android.media.MediaParser;
import android.media.MediaParser.TrackData;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.C.SelectionFlags;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData;
import com.google.android.exoplayer2.extractor.ChunkIndex;
import com.google.android.exoplayer2.extractor.DummyExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.SeekPoint;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.TrackOutput.CryptoData;
import com.google.android.exoplayer2.upstream.DataReader;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MediaFormatUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/**
 * {@link MediaParser.OutputConsumer} implementation that redirects output to an {@link
 * ExtractorOutput}.
 */
@RequiresApi(30)
@SuppressLint("Override") // TODO: Remove once the SDK becomes stable.
public final class OutputConsumerAdapterV30 implements MediaParser.OutputConsumer {

  private static final String TAG = "OConsumerAdapterV30";

  private static final Pair<MediaParser.SeekPoint, MediaParser.SeekPoint> SEEK_POINT_PAIR_START =
      Pair.create(MediaParser.SeekPoint.START, MediaParser.SeekPoint.START);
  private static final String MEDIA_FORMAT_KEY_TRACK_TYPE = "track-type-string";
  private static final String MEDIA_FORMAT_KEY_CHUNK_INDEX_SIZES = "chunk-index-int-sizes";
  private static final String MEDIA_FORMAT_KEY_CHUNK_INDEX_OFFSETS = "chunk-index-long-offsets";
  private static final String MEDIA_FORMAT_KEY_CHUNK_INDEX_DURATIONS =
      "chunk-index-long-us-durations";
  private static final String MEDIA_FORMAT_KEY_CHUNK_INDEX_TIMES = "chunk-index-long-us-times";
  private static final Pattern REGEX_CRYPTO_INFO_PATTERN =
      Pattern.compile("pattern \\(encrypt: (\\d+), skip: (\\d+)\\)");

  private final ArrayList<@NullableType TrackOutput> trackOutputs;
  private final ArrayList<@NullableType Format> trackFormats;
  private final ArrayList<@NullableType CryptoInfo> lastReceivedCryptoInfos;
  private final ArrayList<@NullableType CryptoData> lastOutputCryptoDatas;
  private final DataReaderAdapter scratchDataReaderAdapter;
  private final boolean expectDummySeekMap;
  private final @C.TrackType int primaryTrackType;
  @Nullable private final Format primaryTrackManifestFormat;

  private ExtractorOutput extractorOutput;
  @Nullable private MediaParser.SeekMap dummySeekMap;
  @Nullable private MediaParser.SeekMap lastSeekMap;
  @Nullable private String containerMimeType;
  @Nullable private ChunkIndex lastChunkIndex;
  @Nullable private TimestampAdjuster timestampAdjuster;
  private List<Format> muxedCaptionFormats;
  private int primaryTrackIndex;
  private long sampleTimestampUpperLimitFilterUs;
  private boolean tracksFoundCalled;
  private boolean tracksEnded;
  private boolean seekingDisabled;

  /**
   * Equivalent to {@link #OutputConsumerAdapterV30(Format, int, boolean)
   * OutputConsumerAdapterV30(primaryTrackManifestFormat= null, primaryTrackType= C.TRACK_TYPE_NONE,
   * expectDummySeekMap= false)}
   */
  public OutputConsumerAdapterV30() {
    this(
        /* primaryTrackManifestFormat= */ null,
        /* primaryTrackType= */ C.TRACK_TYPE_NONE,
        /* expectDummySeekMap= */ false);
  }

  /**
   * Creates a new instance.
   *
   * @param primaryTrackManifestFormat The manifest-obtained format of the primary track, or null if
   *     not applicable.
   * @param primaryTrackType The {@link C.TrackType type} of the primary track. {@link
   *     C#TRACK_TYPE_NONE} if there is no primary track.
   * @param expectDummySeekMap Whether the output consumer should expect an initial dummy seek map
   *     which should be exposed through {@link #getDummySeekMap()}.
   */
  public OutputConsumerAdapterV30(
      @Nullable Format primaryTrackManifestFormat,
      @C.TrackType int primaryTrackType,
      boolean expectDummySeekMap) {
    this.expectDummySeekMap = expectDummySeekMap;
    this.primaryTrackManifestFormat = primaryTrackManifestFormat;
    this.primaryTrackType = primaryTrackType;
    trackOutputs = new ArrayList<>();
    trackFormats = new ArrayList<>();
    lastReceivedCryptoInfos = new ArrayList<>();
    lastOutputCryptoDatas = new ArrayList<>();
    scratchDataReaderAdapter = new DataReaderAdapter();
    extractorOutput = new DummyExtractorOutput();
    sampleTimestampUpperLimitFilterUs = C.TIME_UNSET;
    muxedCaptionFormats = ImmutableList.of();
  }

  /**
   * Sets an upper limit for sample timestamp filtering.
   *
   * <p>When set, samples with timestamps greater than {@code sampleTimestampUpperLimitFilterUs}
   * will be discarded.
   *
   * @param sampleTimestampUpperLimitFilterUs The maximum allowed sample timestamp, or {@link
   *     C#TIME_UNSET} to remove filtering.
   */
  public void setSampleTimestampUpperLimitFilterUs(long sampleTimestampUpperLimitFilterUs) {
    this.sampleTimestampUpperLimitFilterUs = sampleTimestampUpperLimitFilterUs;
  }

  /** Sets a {@link TimestampAdjuster} for adjusting the timestamps of the output samples. */
  public void setTimestampAdjuster(TimestampAdjuster timestampAdjuster) {
    this.timestampAdjuster = timestampAdjuster;
  }

  /**
   * Sets the {@link ExtractorOutput} to which {@link MediaParser MediaParser's} output is directed.
   */
  public void setExtractorOutput(ExtractorOutput extractorOutput) {
    this.extractorOutput = extractorOutput;
  }

  /** Sets {@link Format} information associated to the caption tracks multiplexed in the media. */
  public void setMuxedCaptionFormats(List<Format> muxedCaptionFormats) {
    this.muxedCaptionFormats = muxedCaptionFormats;
  }

  /** Overrides future received {@link SeekMap SeekMaps} with non-seekable instances. */
  public void disableSeeking() {
    seekingDisabled = true;
  }

  /**
   * Returns a dummy {@link MediaParser.SeekMap}, or null if not available.
   *
   * <p>the dummy {@link MediaParser.SeekMap} returns a single {@link MediaParser.SeekPoint} whose
   * {@link MediaParser.SeekPoint#timeMicros} matches the requested timestamp, and {@link
   * MediaParser.SeekPoint#position} is 0.
   */
  @Nullable
  public MediaParser.SeekMap getDummySeekMap() {
    return dummySeekMap;
  }

  /** Returns the most recently output {@link ChunkIndex}, or null if none has been output. */
  @Nullable
  public ChunkIndex getChunkIndex() {
    return lastChunkIndex;
  }

  /**
   * Returns the {@link MediaParser.SeekPoint} instances corresponding to the given timestamp.
   *
   * @param seekTimeUs The timestamp in microseconds to retrieve {@link MediaParser.SeekPoint}
   *     instances for.
   * @return The {@link MediaParser.SeekPoint} instances corresponding to the given timestamp.
   */
  public Pair<MediaParser.SeekPoint, MediaParser.SeekPoint> getSeekPoints(long seekTimeUs) {
    return lastSeekMap != null ? lastSeekMap.getSeekPoints(seekTimeUs) : SEEK_POINT_PAIR_START;
  }

  /**
   * Defines the container mime type to propagate through {@link TrackOutput#format}.
   *
   * @param parserName The name of the selected parser.
   */
  public void setSelectedParserName(String parserName) {
    containerMimeType = getMimeType(parserName);
  }

  /**
   * Returns the last output format for each track, or null if not all the tracks have been
   * identified.
   */
  @Nullable
  public Format[] getSampleFormats() {
    if (!tracksFoundCalled) {
      return null;
    }
    Format[] sampleFormats = new Format[trackFormats.size()];
    for (int i = 0; i < trackFormats.size(); i++) {
      sampleFormats[i] = Assertions.checkNotNull(trackFormats.get(i));
    }
    return sampleFormats;
  }

  // MediaParser.OutputConsumer implementation.

  @Override
  public void onTrackCountFound(int numberOfTracks) {
    tracksFoundCalled = true;
    maybeEndTracks();
  }

  @Override
  public void onSeekMapFound(MediaParser.SeekMap seekMap) {
    if (expectDummySeekMap && dummySeekMap == null) {
      // This is a dummy seek map.
      dummySeekMap = seekMap;
    } else {
      lastSeekMap = seekMap;
      long durationUs = seekMap.getDurationMicros();
      extractorOutput.seekMap(
          seekingDisabled
              ? new SeekMap.Unseekable(
                  durationUs != MediaParser.SeekMap.UNKNOWN_DURATION ? durationUs : C.TIME_UNSET)
              : new SeekMapAdapter(seekMap));
    }
  }

  @Override
  public void onTrackDataFound(int trackIndex, TrackData trackData) {
    if (maybeObtainChunkIndex(trackData.mediaFormat)) {
      // The MediaFormat contains a chunk index. It does not contain anything else.
      return;
    }

    ensureSpaceForTrackIndex(trackIndex);
    @Nullable TrackOutput trackOutput = trackOutputs.get(trackIndex);
    if (trackOutput == null) {
      @Nullable
      String trackTypeString = trackData.mediaFormat.getString(MEDIA_FORMAT_KEY_TRACK_TYPE);
      int trackType =
          toTrackTypeConstant(
              trackTypeString != null
                  ? trackTypeString
                  : trackData.mediaFormat.getString(MediaFormat.KEY_MIME));
      if (trackType == primaryTrackType) {
        primaryTrackIndex = trackIndex;
      }
      trackOutput = extractorOutput.track(trackIndex, trackType);
      trackOutputs.set(trackIndex, trackOutput);
      if (trackTypeString != null) {
        // The MediaFormat includes the track type string, so it cannot include any other keys, as
        // per the android.media.mediaparser.eagerlyExposeTrackType parameter documentation.
        return;
      }
    }
    Format format = toExoPlayerFormat(trackData);
    trackOutput.format(
        primaryTrackManifestFormat != null && trackIndex == primaryTrackIndex
            ? format.withManifestFormatInfo(primaryTrackManifestFormat)
            : format);
    trackFormats.set(trackIndex, format);
    maybeEndTracks();
  }

  @Override
  public void onSampleDataFound(int trackIndex, MediaParser.InputReader sampleData)
      throws IOException {
    ensureSpaceForTrackIndex(trackIndex);
    scratchDataReaderAdapter.input = sampleData;
    TrackOutput trackOutput = trackOutputs.get(trackIndex);
    if (trackOutput == null) {
      trackOutput = extractorOutput.track(trackIndex, C.TRACK_TYPE_UNKNOWN);
      trackOutputs.set(trackIndex, trackOutput);
    }
    trackOutput.sampleData(
        scratchDataReaderAdapter, (int) sampleData.getLength(), /* allowEndOfInput= */ true);
  }

  @Override
  public void onSampleCompleted(
      int trackIndex,
      long timeUs,
      int flags,
      int size,
      int offset,
      @Nullable MediaCodec.CryptoInfo cryptoInfo) {
    if (sampleTimestampUpperLimitFilterUs != C.TIME_UNSET
        && timeUs >= sampleTimestampUpperLimitFilterUs) {
      // Ignore this sample.
      return;
    } else if (timestampAdjuster != null) {
      timeUs = timestampAdjuster.adjustSampleTimestamp(timeUs);
    }
    Assertions.checkNotNull(trackOutputs.get(trackIndex))
        .sampleMetadata(timeUs, flags, size, offset, toExoPlayerCryptoData(trackIndex, cryptoInfo));
  }

  // Private methods.

  private boolean maybeObtainChunkIndex(MediaFormat mediaFormat) {
    @Nullable
    ByteBuffer chunkIndexSizesByteBuffer =
        mediaFormat.getByteBuffer(MEDIA_FORMAT_KEY_CHUNK_INDEX_SIZES);
    if (chunkIndexSizesByteBuffer == null) {
      return false;
    }
    IntBuffer chunkIndexSizes = chunkIndexSizesByteBuffer.asIntBuffer();
    LongBuffer chunkIndexOffsets =
        Assertions.checkNotNull(mediaFormat.getByteBuffer(MEDIA_FORMAT_KEY_CHUNK_INDEX_OFFSETS))
            .asLongBuffer();
    LongBuffer chunkIndexDurationsUs =
        Assertions.checkNotNull(mediaFormat.getByteBuffer(MEDIA_FORMAT_KEY_CHUNK_INDEX_DURATIONS))
            .asLongBuffer();
    LongBuffer chunkIndexTimesUs =
        Assertions.checkNotNull(mediaFormat.getByteBuffer(MEDIA_FORMAT_KEY_CHUNK_INDEX_TIMES))
            .asLongBuffer();
    int[] sizes = new int[chunkIndexSizes.remaining()];
    long[] offsets = new long[chunkIndexOffsets.remaining()];
    long[] durationsUs = new long[chunkIndexDurationsUs.remaining()];
    long[] timesUs = new long[chunkIndexTimesUs.remaining()];
    chunkIndexSizes.get(sizes);
    chunkIndexOffsets.get(offsets);
    chunkIndexDurationsUs.get(durationsUs);
    chunkIndexTimesUs.get(timesUs);
    lastChunkIndex = new ChunkIndex(sizes, offsets, durationsUs, timesUs);
    extractorOutput.seekMap(lastChunkIndex);
    return true;
  }

  private void ensureSpaceForTrackIndex(int trackIndex) {
    for (int i = trackOutputs.size(); i <= trackIndex; i++) {
      trackOutputs.add(null);
      trackFormats.add(null);
      lastReceivedCryptoInfos.add(null);
      lastOutputCryptoDatas.add(null);
    }
  }

  @Nullable
  private CryptoData toExoPlayerCryptoData(int trackIndex, @Nullable CryptoInfo cryptoInfo) {
    if (cryptoInfo == null) {
      return null;
    }

    @Nullable CryptoInfo lastReceivedCryptoInfo = lastReceivedCryptoInfos.get(trackIndex);
    CryptoData cryptoDataToOutput;
    // MediaParser keeps identity and value equality aligned for efficient comparison.
    if (lastReceivedCryptoInfo == cryptoInfo) {
      // They match, we can reuse the last one we created.
      cryptoDataToOutput = Assertions.checkNotNull(lastOutputCryptoDatas.get(trackIndex));
    } else {
      // They don't match, we create a new CryptoData.

      // TODO: Access pattern encryption info directly once the Android SDK makes it visible.
      // See [Internal ref: b/154248283].
      int encryptedBlocks;
      int clearBlocks;
      try {
        Matcher matcher = REGEX_CRYPTO_INFO_PATTERN.matcher(cryptoInfo.toString());
        matcher.find();
        encryptedBlocks = Integer.parseInt(Util.castNonNull(matcher.group(1)));
        clearBlocks = Integer.parseInt(Util.castNonNull(matcher.group(2)));
      } catch (RuntimeException e) {
        // Should never happen.
        Log.e(TAG, "Unexpected error while parsing CryptoInfo: " + cryptoInfo, e);
        // Assume no-pattern encryption.
        encryptedBlocks = 0;
        clearBlocks = 0;
      }
      cryptoDataToOutput =
          new CryptoData(cryptoInfo.mode, cryptoInfo.key, encryptedBlocks, clearBlocks);
      lastReceivedCryptoInfos.set(trackIndex, cryptoInfo);
      lastOutputCryptoDatas.set(trackIndex, cryptoDataToOutput);
    }
    return cryptoDataToOutput;
  }

  private void maybeEndTracks() {
    if (!tracksFoundCalled || tracksEnded) {
      return;
    }
    int size = trackOutputs.size();
    for (int i = 0; i < size; i++) {
      if (trackOutputs.get(i) == null) {
        return;
      }
    }
    extractorOutput.endTracks();
    tracksEnded = true;
  }

  private static @C.TrackType int toTrackTypeConstant(@Nullable String string) {
    if (string == null) {
      return C.TRACK_TYPE_UNKNOWN;
    }
    switch (string) {
      case "audio":
        return C.TRACK_TYPE_AUDIO;
      case "video":
        return C.TRACK_TYPE_VIDEO;
      case "text":
        return C.TRACK_TYPE_TEXT;
      case "metadata":
        return C.TRACK_TYPE_METADATA;
      case "unknown":
        return C.TRACK_TYPE_UNKNOWN;
      default:
        // Must be a MIME type.
        return MimeTypes.getTrackType(string);
    }
  }

  private Format toExoPlayerFormat(TrackData trackData) {
    // TODO: Consider adding support for the following:
    //    format.id
    //    format.stereoMode
    //    format.projectionData
    MediaFormat mediaFormat = trackData.mediaFormat;
    @Nullable String mediaFormatMimeType = mediaFormat.getString(MediaFormat.KEY_MIME);
    int mediaFormatAccessibilityChannel =
        mediaFormat.getInteger(
            MediaFormat.KEY_CAPTION_SERVICE_NUMBER, /* defaultValue= */ Format.NO_VALUE);
    Format.Builder formatBuilder =
        new Format.Builder()
            .setDrmInitData(
                toExoPlayerDrmInitData(
                    mediaFormat.getString("crypto-mode-fourcc"), trackData.drmInitData))
            .setContainerMimeType(containerMimeType)
            .setPeakBitrate(
                mediaFormat.getInteger(
                    MediaFormat.KEY_BIT_RATE, /* defaultValue= */ Format.NO_VALUE))
            .setChannelCount(
                mediaFormat.getInteger(
                    MediaFormat.KEY_CHANNEL_COUNT, /* defaultValue= */ Format.NO_VALUE))
            .setColorInfo(MediaFormatUtil.getColorInfo(mediaFormat))
            .setSampleMimeType(mediaFormatMimeType)
            .setCodecs(mediaFormat.getString(MediaFormat.KEY_CODECS_STRING))
            .setFrameRate(
                mediaFormat.getFloat(
                    MediaFormat.KEY_FRAME_RATE, /* defaultValue= */ Format.NO_VALUE))
            .setWidth(
                mediaFormat.getInteger(MediaFormat.KEY_WIDTH, /* defaultValue= */ Format.NO_VALUE))
            .setHeight(
                mediaFormat.getInteger(MediaFormat.KEY_HEIGHT, /* defaultValue= */ Format.NO_VALUE))
            .setInitializationData(getInitializationData(mediaFormat))
            .setLanguage(mediaFormat.getString(MediaFormat.KEY_LANGUAGE))
            .setMaxInputSize(
                mediaFormat.getInteger(
                    MediaFormat.KEY_MAX_INPUT_SIZE, /* defaultValue= */ Format.NO_VALUE))
            .setPcmEncoding(
                mediaFormat.getInteger("exo-pcm-encoding", /* defaultValue= */ Format.NO_VALUE))
            .setRotationDegrees(
                mediaFormat.getInteger(MediaFormat.KEY_ROTATION, /* defaultValue= */ 0))
            .setSampleRate(
                mediaFormat.getInteger(
                    MediaFormat.KEY_SAMPLE_RATE, /* defaultValue= */ Format.NO_VALUE))
            .setSelectionFlags(getSelectionFlags(mediaFormat))
            .setEncoderDelay(
                mediaFormat.getInteger(MediaFormat.KEY_ENCODER_DELAY, /* defaultValue= */ 0))
            .setEncoderPadding(
                mediaFormat.getInteger(MediaFormat.KEY_ENCODER_PADDING, /* defaultValue= */ 0))
            .setPixelWidthHeightRatio(
                mediaFormat.getFloat("pixel-width-height-ratio-float", /* defaultValue= */ 1f))
            .setSubsampleOffsetUs(
                mediaFormat.getLong(
                    "subsample-offset-us-long", /* defaultValue= */ Format.OFFSET_SAMPLE_RELATIVE))
            .setAccessibilityChannel(mediaFormatAccessibilityChannel);
    for (int i = 0; i < muxedCaptionFormats.size(); i++) {
      Format muxedCaptionFormat = muxedCaptionFormats.get(i);
      if (Util.areEqual(muxedCaptionFormat.sampleMimeType, mediaFormatMimeType)
          && muxedCaptionFormat.accessibilityChannel == mediaFormatAccessibilityChannel) {
        // The track's format matches this muxedCaptionFormat, so we apply the manifest format
        // information to the track.
        formatBuilder
            .setLanguage(muxedCaptionFormat.language)
            .setRoleFlags(muxedCaptionFormat.roleFlags)
            .setSelectionFlags(muxedCaptionFormat.selectionFlags)
            .setLabel(muxedCaptionFormat.label)
            .setMetadata(muxedCaptionFormat.metadata);
        break;
      }
    }
    return formatBuilder.build();
  }

  @Nullable
  private static DrmInitData toExoPlayerDrmInitData(
      @Nullable String schemeType, @Nullable android.media.DrmInitData drmInitData) {
    if (drmInitData == null) {
      return null;
    }
    SchemeData[] schemeDatas = new SchemeData[drmInitData.getSchemeInitDataCount()];
    for (int i = 0; i < schemeDatas.length; i++) {
      SchemeInitData schemeInitData = drmInitData.getSchemeInitDataAt(i);
      schemeDatas[i] =
          new SchemeData(schemeInitData.uuid, schemeInitData.mimeType, schemeInitData.data);
    }
    return new DrmInitData(schemeType, schemeDatas);
  }

  private static @SelectionFlags int getSelectionFlags(MediaFormat mediaFormat) {
    int selectionFlags = 0;
    selectionFlags |=
        getFlag(
            mediaFormat,
            /* key= */ MediaFormat.KEY_IS_AUTOSELECT,
            /* returnValueIfPresent= */ C.SELECTION_FLAG_AUTOSELECT);
    selectionFlags |=
        getFlag(
            mediaFormat,
            /* key= */ MediaFormat.KEY_IS_DEFAULT,
            /* returnValueIfPresent= */ C.SELECTION_FLAG_DEFAULT);
    selectionFlags |=
        getFlag(
            mediaFormat,
            /* key= */ MediaFormat.KEY_IS_FORCED_SUBTITLE,
            /* returnValueIfPresent= */ C.SELECTION_FLAG_FORCED);
    return selectionFlags;
  }

  private static int getFlag(MediaFormat mediaFormat, String key, int returnValueIfPresent) {
    return mediaFormat.getInteger(key, /* defaultValue= */ 0) != 0 ? returnValueIfPresent : 0;
  }

  private static List<byte[]> getInitializationData(MediaFormat mediaFormat) {
    ArrayList<byte[]> initData = new ArrayList<>();
    int i = 0;
    while (true) {
      @Nullable ByteBuffer byteBuffer = mediaFormat.getByteBuffer("csd-" + i++);
      if (byteBuffer == null) {
        break;
      }
      initData.add(MediaFormatUtil.getArray(byteBuffer));
    }
    return initData;
  }

  private static String getMimeType(String parserName) {
    switch (parserName) {
      case PARSER_NAME_MATROSKA:
        return MimeTypes.VIDEO_WEBM;
      case PARSER_NAME_FMP4:
      case PARSER_NAME_MP4:
        return MimeTypes.VIDEO_MP4;
      case PARSER_NAME_MP3:
        return MimeTypes.AUDIO_MPEG;
      case PARSER_NAME_ADTS:
        return MimeTypes.AUDIO_AAC;
      case PARSER_NAME_AC3:
        return MimeTypes.AUDIO_AC3;
      case PARSER_NAME_TS:
        return MimeTypes.VIDEO_MP2T;
      case PARSER_NAME_FLV:
        return MimeTypes.VIDEO_FLV;
      case PARSER_NAME_OGG:
        return MimeTypes.AUDIO_OGG;
      case PARSER_NAME_PS:
        return MimeTypes.VIDEO_PS;
      case PARSER_NAME_WAV:
        return MimeTypes.AUDIO_RAW;
      case PARSER_NAME_AMR:
        return MimeTypes.AUDIO_AMR;
      case PARSER_NAME_AC4:
        return MimeTypes.AUDIO_AC4;
      case PARSER_NAME_FLAC:
        return MimeTypes.AUDIO_FLAC;
      default:
        throw new IllegalArgumentException("Illegal parser name: " + parserName);
    }
  }

  private static final class SeekMapAdapter implements SeekMap {

    private final MediaParser.SeekMap adaptedSeekMap;

    public SeekMapAdapter(MediaParser.SeekMap adaptedSeekMap) {
      this.adaptedSeekMap = adaptedSeekMap;
    }

    @Override
    public boolean isSeekable() {
      return adaptedSeekMap.isSeekable();
    }

    @Override
    public long getDurationUs() {
      long durationMicros = adaptedSeekMap.getDurationMicros();
      return durationMicros != MediaParser.SeekMap.UNKNOWN_DURATION ? durationMicros : C.TIME_UNSET;
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public SeekPoints getSeekPoints(long timeUs) {
      Pair<MediaParser.SeekPoint, MediaParser.SeekPoint> seekPoints =
          adaptedSeekMap.getSeekPoints(timeUs);
      SeekPoints exoPlayerSeekPoints;
      if (seekPoints.first == seekPoints.second) {
        exoPlayerSeekPoints = new SeekPoints(asExoPlayerSeekPoint(seekPoints.first));
      } else {
        exoPlayerSeekPoints =
            new SeekPoints(
                asExoPlayerSeekPoint(seekPoints.first), asExoPlayerSeekPoint(seekPoints.second));
      }
      return exoPlayerSeekPoints;
    }

    private static SeekPoint asExoPlayerSeekPoint(MediaParser.SeekPoint seekPoint) {
      return new SeekPoint(seekPoint.timeMicros, seekPoint.position);
    }
  }

  private static final class DataReaderAdapter implements DataReader {

    @Nullable public MediaParser.InputReader input;

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      return Util.castNonNull(input).read(buffer, offset, length);
    }
  }
}

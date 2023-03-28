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
package com.google.android.exoplayer2.source.hls;

import static android.media.MediaParser.PARAMETER_TS_IGNORE_AAC_STREAM;
import static android.media.MediaParser.PARAMETER_TS_IGNORE_AVC_STREAM;
import static android.media.MediaParser.PARAMETER_TS_IGNORE_SPLICE_INFO_STREAM;
import static android.media.MediaParser.PARAMETER_TS_MODE;
import static com.google.android.exoplayer2.source.mediaparser.MediaParserUtil.PARAMETER_EAGERLY_EXPOSE_TRACK_TYPE;
import static com.google.android.exoplayer2.source.mediaparser.MediaParserUtil.PARAMETER_EXPOSE_CAPTION_FORMATS;
import static com.google.android.exoplayer2.source.mediaparser.MediaParserUtil.PARAMETER_IGNORE_TIMESTAMP_OFFSET;
import static com.google.android.exoplayer2.source.mediaparser.MediaParserUtil.PARAMETER_IN_BAND_CRYPTO_INFO;
import static com.google.android.exoplayer2.source.mediaparser.MediaParserUtil.PARAMETER_OVERRIDE_IN_BAND_CAPTION_DECLARATIONS;

import android.annotation.SuppressLint;
import android.media.MediaFormat;
import android.media.MediaParser;
import android.media.MediaParser.OutputConsumer;
import android.media.MediaParser.SeekPoint;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.source.mediaparser.InputReaderAdapterV30;
import com.google.android.exoplayer2.source.mediaparser.MediaParserUtil;
import com.google.android.exoplayer2.source.mediaparser.OutputConsumerAdapterV30;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.FileTypes;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.io.IOException;

/** {@link HlsMediaChunkExtractor} implemented on top of the platform's {@link MediaParser}. */
@RequiresApi(30)
public final class MediaParserHlsMediaChunkExtractor implements HlsMediaChunkExtractor {

  /**
   * {@link HlsExtractorFactory} implementation that produces {@link
   * MediaParserHlsMediaChunkExtractor} for all container formats except WebVTT, for which a {@link
   * BundledHlsMediaChunkExtractor} is returned.
   */
  public static final HlsExtractorFactory FACTORY =
      (uri,
          format,
          muxedCaptionFormats,
          timestampAdjuster,
          responseHeaders,
          sniffingExtractorInput,
          playerId) -> {
        if (FileTypes.inferFileTypeFromMimeType(format.sampleMimeType) == FileTypes.WEBVTT) {
          // The segment contains WebVTT. MediaParser does not support WebVTT parsing, so we use the
          // bundled extractor.
          return new BundledHlsMediaChunkExtractor(
              new WebvttExtractor(format.language, timestampAdjuster), format, timestampAdjuster);
        }

        boolean overrideInBandCaptionDeclarations = muxedCaptionFormats != null;
        ImmutableList.Builder<MediaFormat> muxedCaptionMediaFormatsBuilder =
            ImmutableList.builder();
        if (muxedCaptionFormats != null) {
          // The manifest contains captions declarations. We use those to determine which captions
          // will be exposed by MediaParser.
          for (int i = 0; i < muxedCaptionFormats.size(); i++) {
            muxedCaptionMediaFormatsBuilder.add(
                MediaParserUtil.toCaptionsMediaFormat(muxedCaptionFormats.get(i)));
          }
        } else {
          // The manifest does not declare any captions in the stream. Imitate the default HLS
          // extractor factory and declare a 608 track by default.
          muxedCaptionMediaFormatsBuilder.add(
              MediaParserUtil.toCaptionsMediaFormat(
                  new Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_CEA608).build()));
        }

        ImmutableList<MediaFormat> muxedCaptionMediaFormats =
            muxedCaptionMediaFormatsBuilder.build();

        // TODO: Factor out code for optimizing the sniffing order across both factories.
        OutputConsumerAdapterV30 outputConsumerAdapter = new OutputConsumerAdapterV30();
        outputConsumerAdapter.setMuxedCaptionFormats(
            muxedCaptionFormats != null ? muxedCaptionFormats : ImmutableList.of());
        outputConsumerAdapter.setTimestampAdjuster(timestampAdjuster);
        MediaParser mediaParser =
            createMediaParserInstance(
                outputConsumerAdapter,
                format,
                overrideInBandCaptionDeclarations,
                muxedCaptionMediaFormats,
                playerId,
                MediaParser.PARSER_NAME_FMP4,
                MediaParser.PARSER_NAME_AC3,
                MediaParser.PARSER_NAME_AC4,
                MediaParser.PARSER_NAME_ADTS,
                MediaParser.PARSER_NAME_MP3,
                MediaParser.PARSER_NAME_TS);

        PeekingInputReader peekingInputReader = new PeekingInputReader(sniffingExtractorInput);
        // The chunk extractor constructor requires an instance with a known parser name, so we
        // advance once for MediaParser to sniff the content.
        mediaParser.advance(peekingInputReader);
        outputConsumerAdapter.setSelectedParserName(mediaParser.getParserName());

        return new MediaParserHlsMediaChunkExtractor(
            mediaParser,
            outputConsumerAdapter,
            format,
            overrideInBandCaptionDeclarations,
            muxedCaptionMediaFormats,
            /* leadingBytesToSkip= */ peekingInputReader.totalPeekedBytes,
            playerId);
      };

  private final OutputConsumerAdapterV30 outputConsumerAdapter;
  private final InputReaderAdapterV30 inputReaderAdapter;
  private final MediaParser mediaParser;
  private final Format format;
  private final boolean overrideInBandCaptionDeclarations;
  private final ImmutableList<MediaFormat> muxedCaptionMediaFormats;
  private final PlayerId playerId;

  private int pendingSkipBytes;

  /**
   * Creates a new instance.
   *
   * @param mediaParser The {@link MediaParser} instance to use for extraction of segments. The
   *     provided instance must have completed sniffing, or must have been created by name.
   * @param outputConsumerAdapter The {@link OutputConsumerAdapterV30} with which {@code
   *     mediaParser} was created.
   * @param format The {@link Format} associated with the segment.
   * @param overrideInBandCaptionDeclarations Whether to ignore any in-band caption track
   *     declarations in favor of using the {@code muxedCaptionMediaFormats} instead. If false,
   *     caption declarations found in the extracted media will be used, causing {@code
   *     muxedCaptionMediaFormats} to be ignored instead.
   * @param muxedCaptionMediaFormats The list of in-band caption {@link MediaFormat MediaFormats}
   *     that {@link MediaParser} should expose.
   * @param leadingBytesToSkip The number of bytes to skip from the start of the input before
   *     starting extraction.
   * @param playerId The {@link PlayerId} of the player using this chunk extractor.
   */
  public MediaParserHlsMediaChunkExtractor(
      MediaParser mediaParser,
      OutputConsumerAdapterV30 outputConsumerAdapter,
      Format format,
      boolean overrideInBandCaptionDeclarations,
      ImmutableList<MediaFormat> muxedCaptionMediaFormats,
      int leadingBytesToSkip,
      PlayerId playerId) {
    this.mediaParser = mediaParser;
    this.outputConsumerAdapter = outputConsumerAdapter;
    this.overrideInBandCaptionDeclarations = overrideInBandCaptionDeclarations;
    this.muxedCaptionMediaFormats = muxedCaptionMediaFormats;
    this.format = format;
    this.playerId = playerId;
    pendingSkipBytes = leadingBytesToSkip;
    inputReaderAdapter = new InputReaderAdapterV30();
  }

  // ChunkExtractor implementation.

  @Override
  public void init(ExtractorOutput extractorOutput) {
    outputConsumerAdapter.setExtractorOutput(extractorOutput);
  }

  @Override
  public boolean read(ExtractorInput extractorInput) throws IOException {
    extractorInput.skipFully(pendingSkipBytes);
    pendingSkipBytes = 0;
    inputReaderAdapter.setDataReader(extractorInput, extractorInput.getLength());
    return mediaParser.advance(inputReaderAdapter);
  }

  @Override
  public boolean isPackedAudioExtractor() {
    String parserName = mediaParser.getParserName();
    return MediaParser.PARSER_NAME_AC3.equals(parserName)
        || MediaParser.PARSER_NAME_AC4.equals(parserName)
        || MediaParser.PARSER_NAME_ADTS.equals(parserName)
        || MediaParser.PARSER_NAME_MP3.equals(parserName);
  }

  @Override
  public boolean isReusable() {
    String parserName = mediaParser.getParserName();
    return MediaParser.PARSER_NAME_FMP4.equals(parserName)
        || MediaParser.PARSER_NAME_TS.equals(parserName);
  }

  @Override
  public HlsMediaChunkExtractor recreate() {
    Assertions.checkState(!isReusable());
    return new MediaParserHlsMediaChunkExtractor(
        createMediaParserInstance(
            outputConsumerAdapter,
            format,
            overrideInBandCaptionDeclarations,
            muxedCaptionMediaFormats,
            playerId,
            mediaParser.getParserName()),
        outputConsumerAdapter,
        format,
        overrideInBandCaptionDeclarations,
        muxedCaptionMediaFormats,
        /* leadingBytesToSkip= */ 0,
        playerId);
  }

  @Override
  public void onTruncatedSegmentParsed() {
    mediaParser.seek(SeekPoint.START);
  }

  // Allow constants that are not part of the public MediaParser API.
  @SuppressLint({"WrongConstant"})
  private static MediaParser createMediaParserInstance(
      OutputConsumer outputConsumer,
      Format format,
      boolean overrideInBandCaptionDeclarations,
      ImmutableList<MediaFormat> muxedCaptionMediaFormats,
      PlayerId playerId,
      String... parserNames) {
    MediaParser mediaParser =
        parserNames.length == 1
            ? MediaParser.createByName(parserNames[0], outputConsumer)
            : MediaParser.create(outputConsumer, parserNames);
    mediaParser.setParameter(PARAMETER_EXPOSE_CAPTION_FORMATS, muxedCaptionMediaFormats);
    mediaParser.setParameter(
        PARAMETER_OVERRIDE_IN_BAND_CAPTION_DECLARATIONS, overrideInBandCaptionDeclarations);
    mediaParser.setParameter(PARAMETER_IN_BAND_CRYPTO_INFO, true);
    mediaParser.setParameter(PARAMETER_EAGERLY_EXPOSE_TRACK_TYPE, true);
    mediaParser.setParameter(PARAMETER_IGNORE_TIMESTAMP_OFFSET, true);
    mediaParser.setParameter(PARAMETER_TS_IGNORE_SPLICE_INFO_STREAM, true);
    mediaParser.setParameter(PARAMETER_TS_MODE, "hls");
    @Nullable String codecs = format.codecs;
    if (!TextUtils.isEmpty(codecs)) {
      // Sometimes AAC and H264 streams are declared in TS chunks even though they don't really
      // exist. If we know from the codec attribute that they don't exist, then we can
      // explicitly ignore them even if they're declared.
      if (!MimeTypes.AUDIO_AAC.equals(MimeTypes.getAudioMediaMimeType(codecs))) {
        mediaParser.setParameter(PARAMETER_TS_IGNORE_AAC_STREAM, true);
      }
      if (!MimeTypes.VIDEO_H264.equals(MimeTypes.getVideoMediaMimeType(codecs))) {
        mediaParser.setParameter(PARAMETER_TS_IGNORE_AVC_STREAM, true);
      }
    }
    if (Util.SDK_INT >= 31) {
      MediaParserUtil.setLogSessionIdOnMediaParser(mediaParser, playerId);
    }
    return mediaParser;
  }

  private static final class PeekingInputReader implements MediaParser.SeekableInputReader {

    private final ExtractorInput extractorInput;
    private int totalPeekedBytes;

    private PeekingInputReader(ExtractorInput extractorInput) {
      this.extractorInput = extractorInput;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
      int peekedBytes = extractorInput.peek(buffer, offset, readLength);
      totalPeekedBytes += peekedBytes;
      return peekedBytes;
    }

    @Override
    public long getPosition() {
      return extractorInput.getPeekPosition();
    }

    @Override
    public long getLength() {
      return extractorInput.getLength();
    }

    @Override
    public void seekToPosition(long position) {
      // Seeking is not allowed when sniffing the content.
      throw new UnsupportedOperationException();
    }
  }
}

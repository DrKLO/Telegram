/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.source.rtsp;

import static com.google.android.exoplayer2.source.rtsp.MediaDescription.MEDIA_TYPE_AUDIO;
import static com.google.android.exoplayer2.source.rtsp.RtpPayloadFormat.getMimeTypeFromRtpMediaType;
import static com.google.android.exoplayer2.source.rtsp.SessionDescription.ATTR_CONTROL;
import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.NalUnitUtil.NAL_START_CODE;
import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.net.Uri;
import android.util.Base64;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.audio.AacUtil;
import com.google.android.exoplayer2.util.CodecSpecificDataUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/** Represents a media track in an RTSP playback. */
/* package */ final class RtspMediaTrack {
  // Format specific parameter names.
  private static final String PARAMETER_PROFILE_LEVEL_ID = "profile-level-id";
  private static final String PARAMETER_SPROP_PARAMS = "sprop-parameter-sets";

  private static final String PARAMETER_AMR_OCTET_ALIGN = "octet-align";
  private static final String PARAMETER_AMR_INTERLEAVING = "interleaving";
  private static final String PARAMETER_H265_SPROP_SPS = "sprop-sps";
  private static final String PARAMETER_H265_SPROP_PPS = "sprop-pps";
  private static final String PARAMETER_H265_SPROP_VPS = "sprop-vps";
  private static final String PARAMETER_H265_SPROP_MAX_DON_DIFF = "sprop-max-don-diff";
  private static final String PARAMETER_MP4A_CONFIG = "config";
  private static final String PARAMETER_MP4A_C_PRESENT = "cpresent";

  /** Prefix for the RFC6381 codecs string for AAC formats. */
  private static final String AAC_CODECS_PREFIX = "mp4a.40.";
  /** Prefix for the RFC6381 codecs string for AVC formats. */
  private static final String H264_CODECS_PREFIX = "avc1.";
  /** Prefix for the RFC6416 codecs string for MPEG4V-ES formats. */
  private static final String MPEG4_CODECS_PREFIX = "mp4v.";

  private static final String GENERIC_CONTROL_ATTR = "*";
  /**
   * Default height for MP4V.
   *
   * <p>RFC6416 does not mandate codec specific data (like width and height) in the fmtp attribute.
   * These values are taken from <a
   * href=https://cs.android.com/android/platform/superproject/+/master:frameworks/av/media/codec2/components/mpeg4_h263/C2SoftMpeg4Dec.cpp;l=130
   * >Android's software MP4V decoder</a>.
   */
  private static final int DEFAULT_MP4V_WIDTH = 352;

  /**
   * Default height for MP4V.
   *
   * <p>RFC6416 does not mandate codec specific data (like width and height) in the fmtp attribute.
   * These values are taken from <a
   * href=https://cs.android.com/android/platform/superproject/+/master:frameworks/av/media/codec2/components/mpeg4_h263/C2SoftMpeg4Dec.cpp;l=130
   * >Android's software MP4V decoder</a>.
   */
  private static final int DEFAULT_MP4V_HEIGHT = 288;

  /**
   * Default width for VP8.
   *
   * <p>RFC7741 never uses codec specific data (like width and height) in the fmtp attribute. These
   * values are taken from <a
   * href=https://cs.android.com/android/platform/superproject/+/master:frameworks/av/media/codec2/components/vpx/C2SoftVpxDec.cpp;drc=749a74cc3e081c16ea0e8c530953d0a247177867;l=70>Android's
   * software VP8 decoder</a>.
   */
  private static final int DEFAULT_VP8_WIDTH = 320;
  /**
   * Default height for VP8.
   *
   * <p>RFC7741 never uses codec specific data (like width and height) in the fmtp attribute. These
   * values are taken from <a
   * href=https://cs.android.com/android/platform/superproject/+/master:frameworks/av/media/codec2/components/vpx/C2SoftVpxDec.cpp;drc=749a74cc3e081c16ea0e8c530953d0a247177867;l=70>Android's
   * software VP8 decoder</a>.
   */
  private static final int DEFAULT_VP8_HEIGHT = 240;

  /** RFC7587 Section 6.1 Sampling rate for OPUS is fixed at 48KHz. */
  private static final int OPUS_CLOCK_RATE = 48_000;

  /**
   * Default width for VP9.
   *
   * <p>VP9 RFC (<a href=https://datatracker.ietf.org/doc/html/draft-ietf-payload-vp9>this draft
   * RFC</a>) never uses codec specific data (like width and height) in the fmtp attribute. These
   * values are taken from <a
   * href=https://cs.android.com/android/platform/superproject/+/master:frameworks/av/media/codec2/components/vpx/C2SoftVpxDec.cpp;drc=749a74cc3e081c16ea0e8c530953d0a247177867;l=70>Android's
   * software VP9 decoder</a>.
   */
  private static final int DEFAULT_VP9_WIDTH = 320;
  /**
   * Default height for VP9.
   *
   * <p>VP9 RFC (<a href=https://datatracker.ietf.org/doc/html/draft-ietf-payload-vp9>this draft
   * RFC</a>) never uses codec specific data (like width and height) in the fmtp attribute. These
   * values are taken from <a
   * href=https://cs.android.com/android/platform/superproject/+/master:frameworks/av/media/codec2/components/vpx/C2SoftVpxDec.cpp;drc=749a74cc3e081c16ea0e8c530953d0a247177867;l=70>Android's
   * software VP9 decoder</a>.
   */
  private static final int DEFAULT_VP9_HEIGHT = 240;

  /**
   * Default height for H263.
   *
   * <p>RFC4629 does not mandate codec specific data (like width and height) in the fmtp attribute.
   * These values are taken from <a
   * href=https://cs.android.com/android/platform/superproject/+/master:frameworks/av/media/codec2/components/mpeg4_h263/C2SoftMpeg4Dec.cpp;l=130
   * >Android's software H263 decoder</a>.
   */
  private static final int DEFAULT_H263_WIDTH = 352;
  /**
   * Default height for H263.
   *
   * <p>RFC4629 does not mandate codec specific data (like width and height) in the fmtp attribute.
   * These values are taken from <a
   * href=https://cs.android.com/android/platform/superproject/+/master:frameworks/av/media/codec2/components/mpeg4_h263/C2SoftMpeg4Dec.cpp;l=130
   * >Android's software H263 decoder</a>.
   */
  private static final int DEFAULT_H263_HEIGHT = 288;

  /** The track's associated {@link RtpPayloadFormat}. */
  public final RtpPayloadFormat payloadFormat;
  /** The track's URI. */
  public final Uri uri;

  /**
   * Creates a new instance from a {@link MediaDescription}.
   *
   * @param mediaDescription The {@link MediaDescription} of this track.
   * @param sessionUri The {@link Uri} of the RTSP playback session.
   */
  public RtspMediaTrack(MediaDescription mediaDescription, Uri sessionUri) {
    checkArgument(mediaDescription.attributes.containsKey(ATTR_CONTROL));
    payloadFormat = generatePayloadFormat(mediaDescription);
    uri = extractTrackUri(sessionUri, castNonNull(mediaDescription.attributes.get(ATTR_CONTROL)));
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RtspMediaTrack that = (RtspMediaTrack) o;
    return payloadFormat.equals(that.payloadFormat) && uri.equals(that.uri);
  }

  @Override
  public int hashCode() {
    int result = 7;
    result = 31 * result + payloadFormat.hashCode();
    result = 31 * result + uri.hashCode();
    return result;
  }

  @VisibleForTesting
  /* package */ static RtpPayloadFormat generatePayloadFormat(MediaDescription mediaDescription) {
    Format.Builder formatBuilder = new Format.Builder();

    if (mediaDescription.bitrate > 0) {
      formatBuilder.setAverageBitrate(mediaDescription.bitrate);
    }

    int rtpPayloadType = mediaDescription.rtpMapAttribute.payloadType;
    String mediaEncoding = mediaDescription.rtpMapAttribute.mediaEncoding;

    String mimeType = getMimeTypeFromRtpMediaType(mediaEncoding);
    formatBuilder.setSampleMimeType(mimeType);

    int clockRate = mediaDescription.rtpMapAttribute.clockRate;
    int channelCount = C.INDEX_UNSET;
    if (MEDIA_TYPE_AUDIO.equals(mediaDescription.mediaType)) {
      channelCount =
          inferChannelCount(mediaDescription.rtpMapAttribute.encodingParameters, mimeType);
      formatBuilder.setSampleRate(clockRate).setChannelCount(channelCount);
    }

    ImmutableMap<String, String> fmtpParameters = mediaDescription.getFmtpParametersAsMap();
    switch (mimeType) {
      case MimeTypes.AUDIO_AAC:
        checkArgument(channelCount != C.INDEX_UNSET);
        checkArgument(!fmtpParameters.isEmpty());
        if (mediaEncoding.equals(RtpPayloadFormat.RTP_MEDIA_MPEG4_LATM_AUDIO)) {
          // cpresent is defined in RFC3016 Section 5.3. cpresent=0 means the config fmtp parameter
          // must exist.
          checkArgument(
              fmtpParameters.containsKey(PARAMETER_MP4A_C_PRESENT)
                  && fmtpParameters.get(PARAMETER_MP4A_C_PRESENT).equals("0"),
              "Only supports cpresent=0 in AAC audio.");
          @Nullable String config = fmtpParameters.get(PARAMETER_MP4A_CONFIG);
          checkNotNull(config, "AAC audio stream must include config fmtp parameter");
          // config is a hex string.
          checkArgument(config.length() % 2 == 0, "Malformat MPEG4 config: " + config);
          AacUtil.Config aacConfig = parseAacStreamMuxConfig(config);
          formatBuilder
              .setSampleRate(aacConfig.sampleRateHz)
              .setChannelCount(aacConfig.channelCount)
              .setCodecs(aacConfig.codecs);
        }
        processAacFmtpAttribute(formatBuilder, fmtpParameters, channelCount, clockRate);
        break;
      case MimeTypes.AUDIO_AMR_NB:
      case MimeTypes.AUDIO_AMR_WB:
        checkArgument(channelCount == 1, "Multi channel AMR is not currently supported.");
        checkArgument(
            !fmtpParameters.isEmpty(),
            "fmtp parameters must include " + PARAMETER_AMR_OCTET_ALIGN + ".");
        checkArgument(
            fmtpParameters.containsKey(PARAMETER_AMR_OCTET_ALIGN),
            "Only octet aligned mode is currently supported.");
        checkArgument(
            !fmtpParameters.containsKey(PARAMETER_AMR_INTERLEAVING),
            "Interleaving mode is not currently supported.");
        break;
      case MimeTypes.AUDIO_OPUS:
        checkArgument(channelCount != C.INDEX_UNSET);
        // RFC7587 Section 6.1: the RTP timestamp is incremented with a 48000 Hz clock rate
        // for all modes of Opus and all sampling rates.
        checkArgument(clockRate == OPUS_CLOCK_RATE, "Invalid OPUS clock rate.");
        break;
      case MimeTypes.VIDEO_MP4V:
        checkArgument(!fmtpParameters.isEmpty());
        processMPEG4FmtpAttribute(formatBuilder, fmtpParameters);
        break;
      case MimeTypes.VIDEO_H263:
        // H263 never uses fmtp width and height attributes (RFC4629 Section 8.2), setting default
        // width and height.
        formatBuilder.setWidth(DEFAULT_H263_WIDTH).setHeight(DEFAULT_H263_HEIGHT);
        break;
      case MimeTypes.VIDEO_H264:
        checkArgument(!fmtpParameters.isEmpty());
        processH264FmtpAttribute(formatBuilder, fmtpParameters);
        break;
      case MimeTypes.VIDEO_H265:
        checkArgument(!fmtpParameters.isEmpty());
        processH265FmtpAttribute(formatBuilder, fmtpParameters);
        break;
      case MimeTypes.VIDEO_VP8:
        // VP8 never uses fmtp width and height attributes (RFC7741 Section 6.2), setting default
        // width and height.
        formatBuilder.setWidth(DEFAULT_VP8_WIDTH).setHeight(DEFAULT_VP8_HEIGHT);
        break;
      case MimeTypes.VIDEO_VP9:
        // VP9 never uses fmtp width and height attributes, setting default width and height.
        formatBuilder.setWidth(DEFAULT_VP9_WIDTH).setHeight(DEFAULT_VP9_HEIGHT);
        break;
      case MimeTypes.AUDIO_RAW:
        formatBuilder.setPcmEncoding(RtpPayloadFormat.getRawPcmEncodingType(mediaEncoding));
        break;
      case MimeTypes.AUDIO_AC3:
      case MimeTypes.AUDIO_ALAW:
      case MimeTypes.AUDIO_MLAW:
        // Does not require a fmtp attribute. Fall through.
      default:
        // Do nothing.
    }

    checkArgument(clockRate > 0);
    return new RtpPayloadFormat(
        formatBuilder.build(), rtpPayloadType, clockRate, fmtpParameters, mediaEncoding);
  }

  private static int inferChannelCount(int encodingParameter, String mimeType) {
    if (encodingParameter != C.INDEX_UNSET) {
      // The encoding parameter specifies the number of channels in audio streams when
      // present. If omitted, the number of channels is one. This parameter has no significance in
      // video streams. (RFC2327 Page 22).
      return encodingParameter;
    }

    if (mimeType.equals(MimeTypes.AUDIO_AC3)) {
      // If RTPMAP attribute does not include channel count for AC3, default to 6.
      return 6;
    }

    return 1;
  }

  private static void processAacFmtpAttribute(
      Format.Builder formatBuilder,
      ImmutableMap<String, String> fmtpAttributes,
      int channelCount,
      int sampleRate) {
    checkArgument(fmtpAttributes.containsKey(PARAMETER_PROFILE_LEVEL_ID));
    String profileLevel = checkNotNull(fmtpAttributes.get(PARAMETER_PROFILE_LEVEL_ID));
    formatBuilder.setCodecs(AAC_CODECS_PREFIX + profileLevel);
    formatBuilder.setInitializationData(
        ImmutableList.of(
            // Clock rate equals to sample rate in RTP.
            AacUtil.buildAacLcAudioSpecificConfig(sampleRate, channelCount)));
  }

  /**
   * Returns the {@link AacUtil.Config} by parsing the MPEG4 Audio Stream Mux configuration.
   *
   * <p>fmtp attribute {@code config} includes the MPEG4 Audio Stream Mux configuration
   * (ISO/IEC14496-3, Chapter 1.7.3).
   */
  private static AacUtil.Config parseAacStreamMuxConfig(String streamMuxConfig) {
    ParsableBitArray config = new ParsableBitArray(Util.getBytesFromHexString(streamMuxConfig));
    checkArgument(config.readBits(1) == 0, "Only supports audio mux version 0.");
    checkArgument(config.readBits(1) == 1, "Only supports allStreamsSameTimeFraming.");
    config.skipBits(6);
    checkArgument(config.readBits(4) == 0, "Only supports one program.");
    checkArgument(config.readBits(3) == 0, "Only supports one numLayer.");
    try {
      return AacUtil.parseAudioSpecificConfig(config, false);
    } catch (ParserException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private static void processMPEG4FmtpAttribute(
      Format.Builder formatBuilder, ImmutableMap<String, String> fmtpAttributes) {
    @Nullable String configInput = fmtpAttributes.get(PARAMETER_MP4A_CONFIG);
    if (configInput != null) {
      byte[] configBuffer = Util.getBytesFromHexString(configInput);
      formatBuilder.setInitializationData(ImmutableList.of(configBuffer));
      Pair<Integer, Integer> resolution =
          CodecSpecificDataUtil.getVideoResolutionFromMpeg4VideoConfig(configBuffer);
      formatBuilder.setWidth(resolution.first).setHeight(resolution.second);
    } else {
      // set the default width and height
      formatBuilder.setWidth(DEFAULT_MP4V_WIDTH).setHeight(DEFAULT_MP4V_HEIGHT);
    }
    @Nullable String profileLevel = fmtpAttributes.get(PARAMETER_PROFILE_LEVEL_ID);
    formatBuilder.setCodecs(MPEG4_CODECS_PREFIX + (profileLevel == null ? "1" : profileLevel));
  }

  /** Returns H264/H265 initialization data from the RTP parameter set. */
  private static byte[] getInitializationDataFromParameterSet(String parameterSet) {
    byte[] decodedParameterNalData = Base64.decode(parameterSet, Base64.DEFAULT);
    byte[] decodedParameterNalUnit =
        new byte[decodedParameterNalData.length + NAL_START_CODE.length];
    System.arraycopy(
        NAL_START_CODE,
        /* srcPos= */ 0,
        decodedParameterNalUnit,
        /* destPos= */ 0,
        NAL_START_CODE.length);
    System.arraycopy(
        decodedParameterNalData,
        /* srcPos= */ 0,
        decodedParameterNalUnit,
        /* destPos= */ NAL_START_CODE.length,
        decodedParameterNalData.length);
    return decodedParameterNalUnit;
  }

  private static void processH264FmtpAttribute(
      Format.Builder formatBuilder, ImmutableMap<String, String> fmtpAttributes) {
    checkArgument(fmtpAttributes.containsKey(PARAMETER_SPROP_PARAMS));
    String spropParameterSets = checkNotNull(fmtpAttributes.get(PARAMETER_SPROP_PARAMS));
    String[] parameterSets = Util.split(spropParameterSets, ",");
    checkArgument(parameterSets.length == 2);
    ImmutableList<byte[]> initializationData =
        ImmutableList.of(
            getInitializationDataFromParameterSet(parameterSets[0]),
            getInitializationDataFromParameterSet(parameterSets[1]));
    formatBuilder.setInitializationData(initializationData);

    // Process SPS (Sequence Parameter Set).
    byte[] spsNalDataWithStartCode = initializationData.get(0);
    NalUnitUtil.SpsData spsData =
        NalUnitUtil.parseSpsNalUnit(
            spsNalDataWithStartCode, NAL_START_CODE.length, spsNalDataWithStartCode.length);
    formatBuilder.setPixelWidthHeightRatio(spsData.pixelWidthHeightRatio);
    formatBuilder.setHeight(spsData.height);
    formatBuilder.setWidth(spsData.width);

    @Nullable String profileLevel = fmtpAttributes.get(PARAMETER_PROFILE_LEVEL_ID);
    if (profileLevel != null) {
      formatBuilder.setCodecs(H264_CODECS_PREFIX + profileLevel);
    } else {
      formatBuilder.setCodecs(
          CodecSpecificDataUtil.buildAvcCodecString(
              spsData.profileIdc, spsData.constraintsFlagsAndReservedZero2Bits, spsData.levelIdc));
    }
  }

  private static void processH265FmtpAttribute(
      Format.Builder formatBuilder, ImmutableMap<String, String> fmtpAttributes) {
    if (fmtpAttributes.containsKey(PARAMETER_H265_SPROP_MAX_DON_DIFF)) {
      int maxDonDiff =
          Integer.parseInt(checkNotNull(fmtpAttributes.get(PARAMETER_H265_SPROP_MAX_DON_DIFF)));
      checkArgument(
          maxDonDiff == 0, "non-zero sprop-max-don-diff " + maxDonDiff + " is not supported");
    }

    checkArgument(fmtpAttributes.containsKey(PARAMETER_H265_SPROP_VPS));
    String spropVPS = checkNotNull(fmtpAttributes.get(PARAMETER_H265_SPROP_VPS));
    checkArgument(fmtpAttributes.containsKey(PARAMETER_H265_SPROP_SPS));
    String spropSPS = checkNotNull(fmtpAttributes.get(PARAMETER_H265_SPROP_SPS));
    checkArgument(fmtpAttributes.containsKey(PARAMETER_H265_SPROP_PPS));
    String spropPPS = checkNotNull(fmtpAttributes.get(PARAMETER_H265_SPROP_PPS));
    ImmutableList<byte[]> initializationData =
        ImmutableList.of(
            getInitializationDataFromParameterSet(spropVPS),
            getInitializationDataFromParameterSet(spropSPS),
            getInitializationDataFromParameterSet(spropPPS));
    formatBuilder.setInitializationData(initializationData);

    // Process the SPS (Sequence Parameter Set).
    byte[] spsNalDataWithStartCode = initializationData.get(1);
    NalUnitUtil.H265SpsData spsData =
        NalUnitUtil.parseH265SpsNalUnit(
            spsNalDataWithStartCode, NAL_START_CODE.length, spsNalDataWithStartCode.length);
    formatBuilder.setPixelWidthHeightRatio(spsData.pixelWidthHeightRatio);
    formatBuilder.setHeight(spsData.height).setWidth(spsData.width);

    formatBuilder.setCodecs(
        CodecSpecificDataUtil.buildHevcCodecString(
            spsData.generalProfileSpace,
            spsData.generalTierFlag,
            spsData.generalProfileIdc,
            spsData.generalProfileCompatibilityFlags,
            spsData.constraintBytes,
            spsData.generalLevelIdc));
  }

  /**
   * Extracts the track URI.
   *
   * <p>The processing logic is specified in RFC2326 Section C.1.1.
   *
   * @param sessionUri The session URI.
   * @param controlAttributeString The control attribute from the track's {@link MediaDescription}.
   * @return The extracted track URI.
   */
  private static Uri extractTrackUri(Uri sessionUri, String controlAttributeString) {
    Uri controlAttributeUri = Uri.parse(controlAttributeString);
    if (controlAttributeUri.isAbsolute()) {
      return controlAttributeUri;
    } else if (controlAttributeString.equals(GENERIC_CONTROL_ATTR)) {
      return sessionUri;
    } else {
      return sessionUri.buildUpon().appendEncodedPath(controlAttributeString).build();
    }
  }
}

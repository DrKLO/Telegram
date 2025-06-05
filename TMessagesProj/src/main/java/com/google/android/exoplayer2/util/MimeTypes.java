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

import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.common.base.Ascii;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Defines common MIME types and helper methods. */
public final class MimeTypes {

  public static final String BASE_TYPE_VIDEO = "video";
  public static final String BASE_TYPE_AUDIO = "audio";
  public static final String BASE_TYPE_TEXT = "text";
  public static final String BASE_TYPE_IMAGE = "image";
  public static final String BASE_TYPE_APPLICATION = "application";

  // video/ MIME types

  public static final String VIDEO_MP4 = BASE_TYPE_VIDEO + "/mp4";
  public static final String VIDEO_MATROSKA = BASE_TYPE_VIDEO + "/x-matroska";
  public static final String VIDEO_WEBM = BASE_TYPE_VIDEO + "/webm";
  public static final String VIDEO_H263 = BASE_TYPE_VIDEO + "/3gpp";
  public static final String VIDEO_H264 = BASE_TYPE_VIDEO + "/avc";
  public static final String VIDEO_H265 = BASE_TYPE_VIDEO + "/hevc";
  public static final String VIDEO_VP8 = BASE_TYPE_VIDEO + "/x-vnd.on2.vp8";
  public static final String VIDEO_VP9 = BASE_TYPE_VIDEO + "/x-vnd.on2.vp9";
  public static final String VIDEO_AV1 = BASE_TYPE_VIDEO + "/av01";
  public static final String VIDEO_MP2T = BASE_TYPE_VIDEO + "/mp2t";
  public static final String VIDEO_MP4V = BASE_TYPE_VIDEO + "/mp4v-es";
  public static final String VIDEO_MPEG = BASE_TYPE_VIDEO + "/mpeg";
  public static final String VIDEO_PS = BASE_TYPE_VIDEO + "/mp2p";
  public static final String VIDEO_MPEG2 = BASE_TYPE_VIDEO + "/mpeg2";
  public static final String VIDEO_VC1 = BASE_TYPE_VIDEO + "/wvc1";
  public static final String VIDEO_DIVX = BASE_TYPE_VIDEO + "/divx";
  public static final String VIDEO_FLV = BASE_TYPE_VIDEO + "/x-flv";
  public static final String VIDEO_DOLBY_VISION = BASE_TYPE_VIDEO + "/dolby-vision";
  public static final String VIDEO_OGG = BASE_TYPE_VIDEO + "/ogg";
  public static final String VIDEO_AVI = BASE_TYPE_VIDEO + "/x-msvideo";
  public static final String VIDEO_MJPEG = BASE_TYPE_VIDEO + "/mjpeg";
  public static final String VIDEO_MP42 = BASE_TYPE_VIDEO + "/mp42";
  public static final String VIDEO_MP43 = BASE_TYPE_VIDEO + "/mp43";
  public static final String VIDEO_UNKNOWN = BASE_TYPE_VIDEO + "/x-unknown";

  // audio/ MIME types

  public static final String AUDIO_MP4 = BASE_TYPE_AUDIO + "/mp4";
  public static final String AUDIO_AAC = BASE_TYPE_AUDIO + "/mp4a-latm";
  public static final String AUDIO_MATROSKA = BASE_TYPE_AUDIO + "/x-matroska";
  public static final String AUDIO_WEBM = BASE_TYPE_AUDIO + "/webm";
  public static final String AUDIO_MPEG = BASE_TYPE_AUDIO + "/mpeg";
  public static final String AUDIO_MPEG_L1 = BASE_TYPE_AUDIO + "/mpeg-L1";
  public static final String AUDIO_MPEG_L2 = BASE_TYPE_AUDIO + "/mpeg-L2";
  public static final String AUDIO_MPEGH_MHA1 = BASE_TYPE_AUDIO + "/mha1";
  public static final String AUDIO_MPEGH_MHM1 = BASE_TYPE_AUDIO + "/mhm1";
  public static final String AUDIO_RAW = BASE_TYPE_AUDIO + "/raw";
  public static final String AUDIO_ALAW = BASE_TYPE_AUDIO + "/g711-alaw";
  public static final String AUDIO_MLAW = BASE_TYPE_AUDIO + "/g711-mlaw";
  public static final String AUDIO_AC3 = BASE_TYPE_AUDIO + "/ac3";
  public static final String AUDIO_E_AC3 = BASE_TYPE_AUDIO + "/eac3";
  public static final String AUDIO_E_AC3_JOC = BASE_TYPE_AUDIO + "/eac3-joc";
  public static final String AUDIO_AC4 = BASE_TYPE_AUDIO + "/ac4";
  public static final String AUDIO_TRUEHD = BASE_TYPE_AUDIO + "/true-hd";
  public static final String AUDIO_DTS = BASE_TYPE_AUDIO + "/vnd.dts";
  public static final String AUDIO_DTS_HD = BASE_TYPE_AUDIO + "/vnd.dts.hd";
  public static final String AUDIO_DTS_EXPRESS = BASE_TYPE_AUDIO + "/vnd.dts.hd;profile=lbr";
  public static final String AUDIO_DTS_X = BASE_TYPE_AUDIO + "/vnd.dts.uhd;profile=p2";
  public static final String AUDIO_VORBIS = BASE_TYPE_AUDIO + "/vorbis";
  public static final String AUDIO_OPUS = BASE_TYPE_AUDIO + "/opus";
  public static final String AUDIO_AMR = BASE_TYPE_AUDIO + "/amr";
  public static final String AUDIO_AMR_NB = BASE_TYPE_AUDIO + "/3gpp";
  public static final String AUDIO_AMR_WB = BASE_TYPE_AUDIO + "/amr-wb";
  public static final String AUDIO_FLAC = BASE_TYPE_AUDIO + "/flac";
  public static final String AUDIO_ALAC = BASE_TYPE_AUDIO + "/alac";
  public static final String AUDIO_MSGSM = BASE_TYPE_AUDIO + "/gsm";
  public static final String AUDIO_OGG = BASE_TYPE_AUDIO + "/ogg";
  public static final String AUDIO_WAV = BASE_TYPE_AUDIO + "/wav";
  public static final String AUDIO_MIDI = BASE_TYPE_AUDIO + "/midi";

  public static final String AUDIO_EXOPLAYER_MIDI = BASE_TYPE_AUDIO + "/x-exoplayer-midi";

  public static final String AUDIO_UNKNOWN = BASE_TYPE_AUDIO + "/x-unknown";

  // text/ MIME types

  public static final String TEXT_VTT = BASE_TYPE_TEXT + "/vtt";
  public static final String TEXT_SSA = BASE_TYPE_TEXT + "/x-ssa";

  public static final String TEXT_EXOPLAYER_CUES = BASE_TYPE_TEXT + "/x-exoplayer-cues";

  public static final String TEXT_UNKNOWN = BASE_TYPE_TEXT + "/x-unknown";

  // application/ MIME types

  public static final String APPLICATION_MP4 = BASE_TYPE_APPLICATION + "/mp4";
  public static final String APPLICATION_WEBM = BASE_TYPE_APPLICATION + "/webm";

  public static final String APPLICATION_MATROSKA = BASE_TYPE_APPLICATION + "/x-matroska";

  public static final String APPLICATION_MPD = BASE_TYPE_APPLICATION + "/dash+xml";
  public static final String APPLICATION_M3U8 = BASE_TYPE_APPLICATION + "/x-mpegURL";
  public static final String APPLICATION_SS = BASE_TYPE_APPLICATION + "/vnd.ms-sstr+xml";
  public static final String APPLICATION_ID3 = BASE_TYPE_APPLICATION + "/id3";
  public static final String APPLICATION_CEA608 = BASE_TYPE_APPLICATION + "/cea-608";
  public static final String APPLICATION_CEA708 = BASE_TYPE_APPLICATION + "/cea-708";
  public static final String APPLICATION_SUBRIP = BASE_TYPE_APPLICATION + "/x-subrip";
  public static final String APPLICATION_TTML = BASE_TYPE_APPLICATION + "/ttml+xml";
  public static final String APPLICATION_TX3G = BASE_TYPE_APPLICATION + "/x-quicktime-tx3g";
  public static final String APPLICATION_MP4VTT = BASE_TYPE_APPLICATION + "/x-mp4-vtt";
  public static final String APPLICATION_MP4CEA608 = BASE_TYPE_APPLICATION + "/x-mp4-cea-608";
  public static final String APPLICATION_RAWCC = BASE_TYPE_APPLICATION + "/x-rawcc";
  public static final String APPLICATION_VOBSUB = BASE_TYPE_APPLICATION + "/vobsub";
  public static final String APPLICATION_PGS = BASE_TYPE_APPLICATION + "/pgs";
  public static final String APPLICATION_SCTE35 = BASE_TYPE_APPLICATION + "/x-scte35";

  public static final String APPLICATION_CAMERA_MOTION = BASE_TYPE_APPLICATION + "/x-camera-motion";

  public static final String APPLICATION_EMSG = BASE_TYPE_APPLICATION + "/x-emsg";
  public static final String APPLICATION_DVBSUBS = BASE_TYPE_APPLICATION + "/dvbsubs";
  public static final String APPLICATION_EXIF = BASE_TYPE_APPLICATION + "/x-exif";
  public static final String APPLICATION_ICY = BASE_TYPE_APPLICATION + "/x-icy";
  public static final String APPLICATION_AIT = BASE_TYPE_APPLICATION + "/vnd.dvb.ait";
  public static final String APPLICATION_RTSP = BASE_TYPE_APPLICATION + "/x-rtsp";

  // image/ MIME types

  public static final String IMAGE_JPEG = BASE_TYPE_IMAGE + "/jpeg";

  /**
   * A non-standard codec string for E-AC3-JOC. Use of this constant allows for disambiguation
   * between regular E-AC3 ("ec-3") and E-AC3-JOC ("ec+3") streams from the codec string alone. The
   * standard is to use "ec-3" for both, as per the <a href="https://mp4ra.org/#/codecs">MP4RA
   * registered codec types</a>.
   */
  public static final String CODEC_E_AC3_JOC = "ec+3";

  private static final ArrayList<CustomMimeType> customMimeTypes = new ArrayList<>();

  private static final Pattern MP4A_RFC_6381_CODEC_PATTERN =
      Pattern.compile("^mp4a\\.([a-zA-Z0-9]{2})(?:\\.([0-9]{1,2}))?$");

  /**
   * Registers a custom MIME type. Most applications do not need to call this method, as handling of
   * standard MIME types is built in. These built-in MIME types take precedence over any registered
   * via this method. If this method is used, it must be called before creating any player(s).
   *
   * @param mimeType The custom MIME type to register.
   * @param codecPrefix The RFC 6381 codec string prefix associated with the MIME type.
   * @param trackType The {@link C.TrackType track type} associated with the MIME type. This value
   *     is ignored if the top-level type of {@code mimeType} is audio, video or text.
   */
  public static void registerCustomMimeType(
      String mimeType, String codecPrefix, @C.TrackType int trackType) {
    CustomMimeType customMimeType = new CustomMimeType(mimeType, codecPrefix, trackType);
    int customMimeTypeCount = customMimeTypes.size();
    for (int i = 0; i < customMimeTypeCount; i++) {
      if (mimeType.equals(customMimeTypes.get(i).mimeType)) {
        customMimeTypes.remove(i);
        break;
      }
    }
    customMimeTypes.add(customMimeType);
  }

  /** Returns whether the given string is an audio MIME type. */
  public static boolean isAudio(@Nullable String mimeType) {
    return BASE_TYPE_AUDIO.equals(getTopLevelType(mimeType));
  }

  /** Returns whether the given string is a video MIME type. */
  public static boolean isVideo(@Nullable String mimeType) {
    return BASE_TYPE_VIDEO.equals(getTopLevelType(mimeType));
  }

  /**
   * Returns whether the given string is a text MIME type, including known text types that use
   * &quot;application&quot; as their base type.
   */
  public static boolean isText(@Nullable String mimeType) {
    return BASE_TYPE_TEXT.equals(getTopLevelType(mimeType))
        || APPLICATION_CEA608.equals(mimeType)
        || APPLICATION_CEA708.equals(mimeType)
        || APPLICATION_MP4CEA608.equals(mimeType)
        || APPLICATION_SUBRIP.equals(mimeType)
        || APPLICATION_TTML.equals(mimeType)
        || APPLICATION_TX3G.equals(mimeType)
        || APPLICATION_MP4VTT.equals(mimeType)
        || APPLICATION_RAWCC.equals(mimeType)
        || APPLICATION_VOBSUB.equals(mimeType)
        || APPLICATION_PGS.equals(mimeType)
        || APPLICATION_DVBSUBS.equals(mimeType);
  }

  /** Returns whether the given string is an image MIME type. */
  public static boolean isImage(@Nullable String mimeType) {
    return BASE_TYPE_IMAGE.equals(getTopLevelType(mimeType));
  }

  /**
   * Returns true if it is known that all samples in a stream of the given MIME type and codec are
   * guaranteed to be sync samples (i.e., {@link C#BUFFER_FLAG_KEY_FRAME} is guaranteed to be set on
   * every sample).
   *
   * @param mimeType The MIME type of the stream.
   * @param codec The RFC 6381 codec string of the stream, or {@code null} if unknown.
   * @return Whether it is known that all samples in the stream are guaranteed to be sync samples.
   */
  public static boolean allSamplesAreSyncSamples(
      @Nullable String mimeType, @Nullable String codec) {
    if (mimeType == null) {
      return false;
    }
    // TODO: Add additional audio MIME types. Also consider evaluating based on Format rather than
    // just MIME type, since in some cases the property is true for a subset of the profiles
    // belonging to a single MIME type. If we do this, we should move the method to a different
    // class. See [Internal ref: http://go/exo-audio-format-random-access].
    switch (mimeType) {
      case AUDIO_MPEG:
      case AUDIO_MPEG_L1:
      case AUDIO_MPEG_L2:
      case AUDIO_RAW:
      case AUDIO_ALAW:
      case AUDIO_MLAW:
      case AUDIO_FLAC:
      case AUDIO_AC3:
      case AUDIO_E_AC3:
      case AUDIO_E_AC3_JOC:
        return true;
      case AUDIO_AAC:
        if (codec == null) {
          return false;
        }
        @Nullable Mp4aObjectType objectType = getObjectTypeFromMp4aRFC6381CodecString(codec);
        if (objectType == null) {
          return false;
        }
        @C.Encoding int encoding = objectType.getEncoding();
        // xHE-AAC is an exception in which it's not true that all samples will be sync samples.
        // Also return false for ENCODING_INVALID, which indicates we weren't able to parse the
        // encoding from the codec string.
        return encoding != C.ENCODING_INVALID && encoding != C.ENCODING_AAC_XHE;
      default:
        return false;
    }
  }

  /**
   * Returns the first video MIME type derived from an RFC 6381 codecs string.
   *
   * @param codecs An RFC 6381 codecs string.
   * @return The first derived video MIME type, or {@code null}.
   */
  @Nullable
  public static String getVideoMediaMimeType(@Nullable String codecs) {
    if (codecs == null) {
      return null;
    }
    String[] codecList = Util.splitCodecs(codecs);
    for (String codec : codecList) {
      @Nullable String mimeType = getMediaMimeType(codec);
      if (mimeType != null && isVideo(mimeType)) {
        return mimeType;
      }
    }
    return null;
  }

  /**
   * Returns whether the given {@code codecs} string contains a codec which corresponds to the given
   * {@code mimeType}.
   *
   * @param codecs An RFC 6381 codecs string.
   * @param mimeType A MIME type to look for.
   * @return Whether the given {@code codecs} string contains a codec which corresponds to the given
   *     {@code mimeType}.
   */
  public static boolean containsCodecsCorrespondingToMimeType(
      @Nullable String codecs, String mimeType) {
    return getCodecsCorrespondingToMimeType(codecs, mimeType) != null;
  }

  /**
   * Returns a subsequence of {@code codecs} containing the codec strings that correspond to the
   * given {@code mimeType}. Returns null if {@code mimeType} is null, {@code codecs} is null, or
   * {@code codecs} does not contain a codec that corresponds to {@code mimeType}.
   *
   * @param codecs An RFC 6381 codecs string.
   * @param mimeType A MIME type to look for.
   * @return A subsequence of {@code codecs} containing the codec strings that correspond to the
   *     given {@code mimeType}. Returns null if {@code mimeType} is null, {@code codecs} is null,
   *     or {@code codecs} does not contain a codec that corresponds to {@code mimeType}.
   */
  @Nullable
  public static String getCodecsCorrespondingToMimeType(
      @Nullable String codecs, @Nullable String mimeType) {
    if (codecs == null || mimeType == null) {
      return null;
    }
    String[] codecList = Util.splitCodecs(codecs);
    StringBuilder builder = new StringBuilder();
    for (String codec : codecList) {
      if (mimeType.equals(getMediaMimeType(codec))) {
        if (builder.length() > 0) {
          builder.append(",");
        }
        builder.append(codec);
      }
    }
    return builder.length() > 0 ? builder.toString() : null;
  }

  /**
   * Returns the first audio MIME type derived from an RFC 6381 codecs string.
   *
   * @param codecs An RFC 6381 codecs string.
   * @return The first derived audio MIME type, or {@code null}.
   */
  @Nullable
  public static String getAudioMediaMimeType(@Nullable String codecs) {
    if (codecs == null) {
      return null;
    }
    String[] codecList = Util.splitCodecs(codecs);
    for (String codec : codecList) {
      @Nullable String mimeType = getMediaMimeType(codec);
      if (mimeType != null && isAudio(mimeType)) {
        return mimeType;
      }
    }
    return null;
  }

  /**
   * Returns the first text MIME type derived from an RFC 6381 codecs string.
   *
   * @param codecs An RFC 6381 codecs string.
   * @return The first derived text MIME type, or {@code null}.
   */
  @Nullable
  public static String getTextMediaMimeType(@Nullable String codecs) {
    if (codecs == null) {
      return null;
    }
    String[] codecList = Util.splitCodecs(codecs);
    for (String codec : codecList) {
      @Nullable String mimeType = getMediaMimeType(codec);
      if (mimeType != null && isText(mimeType)) {
        return mimeType;
      }
    }
    return null;
  }

  /**
   * Returns the MIME type corresponding to an RFC 6381 codec string, or {@code null} if it could
   * not be determined.
   *
   * @param codec An RFC 6381 codec string.
   * @return The corresponding MIME type, or {@code null} if it could not be determined.
   */
  @Nullable
  public static String getMediaMimeType(@Nullable String codec) {
    if (codec == null) {
      return null;
    }
    codec = Ascii.toLowerCase(codec.trim());
    if (codec.startsWith("avc1") || codec.startsWith("avc3")) {
      return MimeTypes.VIDEO_H264;
    } else if (codec.startsWith("hev1") || codec.startsWith("hvc1")) {
      return MimeTypes.VIDEO_H265;
    } else if (codec.startsWith("dvav")
        || codec.startsWith("dva1")
        || codec.startsWith("dvhe")
        || codec.startsWith("dvh1")) {
      return MimeTypes.VIDEO_DOLBY_VISION;
    } else if (codec.startsWith("av01")) {
      return MimeTypes.VIDEO_AV1;
    } else if (codec.startsWith("vp9") || codec.startsWith("vp09")) {
      return MimeTypes.VIDEO_VP9;
    } else if (codec.startsWith("vp8") || codec.startsWith("vp08")) {
      return MimeTypes.VIDEO_VP8;
    } else if (codec.startsWith("mp4a")) {
      @Nullable String mimeType = null;
      if (codec.startsWith("mp4a.")) {
        @Nullable Mp4aObjectType objectType = getObjectTypeFromMp4aRFC6381CodecString(codec);
        if (objectType != null) {
          mimeType = getMimeTypeFromMp4ObjectType(objectType.objectTypeIndication);
        }
      }
      return mimeType == null ? MimeTypes.AUDIO_AAC : mimeType;
    } else if (codec.startsWith("mha1")) {
      return MimeTypes.AUDIO_MPEGH_MHA1;
    } else if (codec.startsWith("mhm1")) {
      return MimeTypes.AUDIO_MPEGH_MHM1;
    } else if (codec.startsWith("ac-3") || codec.startsWith("dac3")) {
      return MimeTypes.AUDIO_AC3;
    } else if (codec.startsWith("ec-3") || codec.startsWith("dec3")) {
      return MimeTypes.AUDIO_E_AC3;
    } else if (codec.startsWith(CODEC_E_AC3_JOC)) {
      return MimeTypes.AUDIO_E_AC3_JOC;
    } else if (codec.startsWith("ac-4") || codec.startsWith("dac4")) {
      return MimeTypes.AUDIO_AC4;
    } else if (codec.startsWith("dtsc")) {
      return MimeTypes.AUDIO_DTS;
    } else if (codec.startsWith("dtse")) {
      return MimeTypes.AUDIO_DTS_EXPRESS;
    } else if (codec.startsWith("dtsh") || codec.startsWith("dtsl")) {
      return MimeTypes.AUDIO_DTS_HD;
    } else if (codec.startsWith("dtsx")) {
      return MimeTypes.AUDIO_DTS_X;
    } else if (codec.startsWith("opus")) {
      return MimeTypes.AUDIO_OPUS;
    } else if (codec.startsWith("vorbis")) {
      return MimeTypes.AUDIO_VORBIS;
    } else if (codec.startsWith("flac")) {
      return MimeTypes.AUDIO_FLAC;
    } else if (codec.startsWith("stpp")) {
      return MimeTypes.APPLICATION_TTML;
    } else if (codec.startsWith("wvtt")) {
      return MimeTypes.TEXT_VTT;
    } else if (codec.contains("cea708")) {
      return MimeTypes.APPLICATION_CEA708;
    } else if (codec.contains("eia608") || codec.contains("cea608")) {
      return MimeTypes.APPLICATION_CEA608;
    } else {
      return getCustomMimeTypeForCodec(codec);
    }
  }

  /**
   * Returns the MIME type corresponding to an MP4 object type identifier, as defined in RFC 6381
   * and https://mp4ra.org/#/object_types.
   *
   * @param objectType An MP4 object type identifier.
   * @return The corresponding MIME type, or {@code null} if it could not be determined.
   */
  @Nullable
  public static String getMimeTypeFromMp4ObjectType(int objectType) {
    switch (objectType) {
      case 0x20:
        return MimeTypes.VIDEO_MP4V;
      case 0x21:
        return MimeTypes.VIDEO_H264;
      case 0x23:
        return MimeTypes.VIDEO_H265;
      case 0x60:
      case 0x61:
      case 0x62:
      case 0x63:
      case 0x64:
      case 0x65:
        return MimeTypes.VIDEO_MPEG2;
      case 0x6A:
        return MimeTypes.VIDEO_MPEG;
      case 0x69:
      case 0x6B:
        return MimeTypes.AUDIO_MPEG;
      case 0xA3:
        return MimeTypes.VIDEO_VC1;
      case 0xB1:
        return MimeTypes.VIDEO_VP9;
      case 0x40:
      case 0x66:
      case 0x67:
      case 0x68:
        return MimeTypes.AUDIO_AAC;
      case 0xA5:
        return MimeTypes.AUDIO_AC3;
      case 0xA6:
        return MimeTypes.AUDIO_E_AC3;
      case 0xA9:
      case 0xAC:
        return MimeTypes.AUDIO_DTS;
      case 0xAA:
      case 0xAB:
        return MimeTypes.AUDIO_DTS_HD;
      case 0xAD:
        return MimeTypes.AUDIO_OPUS;
      case 0xAE:
        return MimeTypes.AUDIO_AC4;
      default:
        return null;
    }
  }

  /**
   * Returns the {@link C.TrackType track type} constant corresponding to a specified MIME type,
   * which may be {@link C#TRACK_TYPE_UNKNOWN} if it could not be determined.
   *
   * @param mimeType A MIME type.
   * @return The corresponding {@link C.TrackType track type}, which may be {@link
   *     C#TRACK_TYPE_UNKNOWN} if it could not be determined.
   */
  public static @C.TrackType int getTrackType(@Nullable String mimeType) {
    if (TextUtils.isEmpty(mimeType)) {
      return C.TRACK_TYPE_UNKNOWN;
    } else if (isAudio(mimeType)) {
      return C.TRACK_TYPE_AUDIO;
    } else if (isVideo(mimeType)) {
      return C.TRACK_TYPE_VIDEO;
    } else if (isText(mimeType)) {
      return C.TRACK_TYPE_TEXT;
    } else if (isImage(mimeType)) {
      return C.TRACK_TYPE_IMAGE;
    } else if (APPLICATION_ID3.equals(mimeType)
        || APPLICATION_EMSG.equals(mimeType)
        || APPLICATION_SCTE35.equals(mimeType)) {
      return C.TRACK_TYPE_METADATA;
    } else if (APPLICATION_CAMERA_MOTION.equals(mimeType)) {
      return C.TRACK_TYPE_CAMERA_MOTION;
    } else {
      return getTrackTypeForCustomMimeType(mimeType);
    }
  }

  /**
   * Returns the {@link C.Encoding} constant corresponding to the specified audio MIME type and RFC
   * 6381 codec string, or {@link C#ENCODING_INVALID} if the corresponding {@link C.Encoding} cannot
   * be determined.
   *
   * @param mimeType A MIME type.
   * @param codec An RFC 6381 codec string, or {@code null} if unknown or not applicable.
   * @return The corresponding {@link C.Encoding}, or {@link C#ENCODING_INVALID}.
   */
  public static @C.Encoding int getEncoding(String mimeType, @Nullable String codec) {
    switch (mimeType) {
      case MimeTypes.AUDIO_MPEG:
        return C.ENCODING_MP3;
      case MimeTypes.AUDIO_AAC:
        if (codec == null) {
          return C.ENCODING_INVALID;
        }
        @Nullable Mp4aObjectType objectType = getObjectTypeFromMp4aRFC6381CodecString(codec);
        if (objectType == null) {
          return C.ENCODING_INVALID;
        }
        return objectType.getEncoding();
      case MimeTypes.AUDIO_AC3:
        return C.ENCODING_AC3;
      case MimeTypes.AUDIO_E_AC3:
        return C.ENCODING_E_AC3;
      case MimeTypes.AUDIO_E_AC3_JOC:
        return C.ENCODING_E_AC3_JOC;
      case MimeTypes.AUDIO_AC4:
        return C.ENCODING_AC4;
      case MimeTypes.AUDIO_DTS:
        return C.ENCODING_DTS;
      case MimeTypes.AUDIO_DTS_HD:
        return C.ENCODING_DTS_HD;
      case MimeTypes.AUDIO_TRUEHD:
        return C.ENCODING_DOLBY_TRUEHD;
      case MimeTypes.AUDIO_OPUS:
        return C.ENCODING_OPUS;
      default:
        return C.ENCODING_INVALID;
    }
  }

  /**
   * Equivalent to {@code getTrackType(getMediaMimeType(codec))}.
   *
   * @param codec An RFC 6381 codec string.
   * @return The corresponding {@link C.TrackType track type}, which may be {@link
   *     C#TRACK_TYPE_UNKNOWN} if it could not be determined.
   */
  public static @C.TrackType int getTrackTypeOfCodec(String codec) {
    return getTrackType(getMediaMimeType(codec));
  }

  /**
   * Normalizes the MIME type provided so that equivalent MIME types are uniquely represented.
   *
   * @param mimeType A MIME type to normalize.
   * @return The normalized MIME type, or the argument MIME type if its normalized form is unknown.
   */
  public static String normalizeMimeType(String mimeType) {
    switch (mimeType) {
      case BASE_TYPE_AUDIO + "/x-flac":
        return AUDIO_FLAC;
      case BASE_TYPE_AUDIO + "/mp3":
        return AUDIO_MPEG;
      case BASE_TYPE_AUDIO + "/x-wav":
        return AUDIO_WAV;
      default:
        return mimeType;
    }
  }

  /** Returns whether the given {@code mimeType} is a Matroska MIME type, including WebM. */
  public static boolean isMatroska(@Nullable String mimeType) {
    if (mimeType == null) {
      return false;
    }
    return mimeType.startsWith(MimeTypes.VIDEO_WEBM)
        || mimeType.startsWith(MimeTypes.AUDIO_WEBM)
        || mimeType.startsWith(MimeTypes.APPLICATION_WEBM)
        || mimeType.startsWith(MimeTypes.VIDEO_MATROSKA)
        || mimeType.startsWith(MimeTypes.AUDIO_MATROSKA)
        || mimeType.startsWith(MimeTypes.APPLICATION_MATROSKA);
  }

  /**
   * Returns the top-level type of {@code mimeType}, or null if {@code mimeType} is null or does not
   * contain a forward slash character ({@code '/'}).
   */
  @Nullable
  private static String getTopLevelType(@Nullable String mimeType) {
    if (mimeType == null) {
      return null;
    }
    int indexOfSlash = mimeType.indexOf('/');
    if (indexOfSlash == -1) {
      return null;
    }
    return mimeType.substring(0, indexOfSlash);
  }

  @Nullable
  private static String getCustomMimeTypeForCodec(String codec) {
    int customMimeTypeCount = customMimeTypes.size();
    for (int i = 0; i < customMimeTypeCount; i++) {
      CustomMimeType customMimeType = customMimeTypes.get(i);
      if (codec.startsWith(customMimeType.codecPrefix)) {
        return customMimeType.mimeType;
      }
    }
    return null;
  }

  private static @C.TrackType int getTrackTypeForCustomMimeType(String mimeType) {
    int customMimeTypeCount = customMimeTypes.size();
    for (int i = 0; i < customMimeTypeCount; i++) {
      CustomMimeType customMimeType = customMimeTypes.get(i);
      if (mimeType.equals(customMimeType.mimeType)) {
        return customMimeType.trackType;
      }
    }
    return C.TRACK_TYPE_UNKNOWN;
  }

  private MimeTypes() {
    // Prevent instantiation.
  }

  /**
   * Returns the {@link Mp4aObjectType} of an RFC 6381 MP4 audio codec string.
   *
   * <p>Per https://mp4ra.org/#/object_types and https://tools.ietf.org/html/rfc6381#section-3.3, an
   * MP4 codec string has the form:
   *
   * <pre>
   *         ~~~~~~~~~~~~~~ Object Type Indication (OTI) byte in hex
   *    mp4a.[a-zA-Z0-9]{2}(.[0-9]{1,2})?
   *                         ~~~~~~~~~~ audio OTI, decimal. Only for certain OTI.
   * </pre>
   *
   * For example, mp4a.40.2 has an OTI of 0x40 and an audio OTI of 2.
   *
   * @param codec An RFC 6381 MP4 audio codec string.
   * @return The {@link Mp4aObjectType}, or {@code null} if the input was invalid.
   */
  @VisibleForTesting
  @Nullable
  /* package */ static Mp4aObjectType getObjectTypeFromMp4aRFC6381CodecString(String codec) {
    Matcher matcher = MP4A_RFC_6381_CODEC_PATTERN.matcher(codec);
    if (!matcher.matches()) {
      return null;
    }
    String objectTypeIndicationHex = Assertions.checkNotNull(matcher.group(1));
    @Nullable String audioObjectTypeIndicationDec = matcher.group(2);
    int objectTypeIndication;
    int audioObjectTypeIndication = 0;
    try {
      objectTypeIndication = Integer.parseInt(objectTypeIndicationHex, 16);
      if (audioObjectTypeIndicationDec != null) {
        audioObjectTypeIndication = Integer.parseInt(audioObjectTypeIndicationDec);
      }
    } catch (NumberFormatException e) {
      return null;
    }
    return new Mp4aObjectType(objectTypeIndication, audioObjectTypeIndication);
  }

  /** An MP4A Object Type Indication (OTI) and its optional audio OTI is defined by RFC 6381. */
  @VisibleForTesting
  /* package */ static final class Mp4aObjectType {
    /** The Object Type Indication of the MP4A codec. */
    public final int objectTypeIndication;
    /** The Audio Object Type Indication of the MP4A codec, or 0 if it is absent. */
    public final int audioObjectTypeIndication;

    public Mp4aObjectType(int objectTypeIndication, int audioObjectTypeIndication) {
      this.objectTypeIndication = objectTypeIndication;
      this.audioObjectTypeIndication = audioObjectTypeIndication;
    }

    /** Returns the encoding for {@link #audioObjectTypeIndication}. */
    public @C.Encoding int getEncoding() {
      // See AUDIO_OBJECT_TYPE_AAC_* constants in AacUtil.
      switch (audioObjectTypeIndication) {
        case 2:
          return C.ENCODING_AAC_LC;
        case 5:
          return C.ENCODING_AAC_HE_V1;
        case 29:
          return C.ENCODING_AAC_HE_V2;
        case 42:
          return C.ENCODING_AAC_XHE;
        case 23:
          return C.ENCODING_AAC_ELD;
        case 22:
          return C.ENCODING_AAC_ER_BSAC;
        default:
          return C.ENCODING_INVALID;
      }
    }
  }

  private static final class CustomMimeType {
    public final String mimeType;
    public final String codecPrefix;
    public final @C.TrackType int trackType;

    public CustomMimeType(String mimeType, String codecPrefix, @C.TrackType int trackType) {
      this.mimeType = mimeType;
      this.codecPrefix = codecPrefix;
      this.trackType = trackType;
    }
  }
}

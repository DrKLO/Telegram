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
package org.telegram.messenger.exoplayer.util;

/**
 * Defines common MIME types and helper methods.
 */
public final class MimeTypes {

  public static final String BASE_TYPE_VIDEO = "video";
  public static final String BASE_TYPE_AUDIO = "audio";
  public static final String BASE_TYPE_TEXT = "text";
  public static final String BASE_TYPE_APPLICATION = "application";

  public static final String VIDEO_UNKNOWN = BASE_TYPE_VIDEO + "/x-unknown";
  public static final String VIDEO_MP4 = BASE_TYPE_VIDEO + "/mp4";
  public static final String VIDEO_WEBM = BASE_TYPE_VIDEO + "/webm";
  public static final String VIDEO_H263 = BASE_TYPE_VIDEO + "/3gpp";
  public static final String VIDEO_H264 = BASE_TYPE_VIDEO + "/avc";
  public static final String VIDEO_H265 = BASE_TYPE_VIDEO + "/hevc";
  public static final String VIDEO_VP8 = BASE_TYPE_VIDEO + "/x-vnd.on2.vp8";
  public static final String VIDEO_VP9 = BASE_TYPE_VIDEO + "/x-vnd.on2.vp9";
  public static final String VIDEO_MP4V = BASE_TYPE_VIDEO + "/mp4v-es";
  public static final String VIDEO_MPEG2 = BASE_TYPE_VIDEO + "/mpeg2";
  public static final String VIDEO_VC1 = BASE_TYPE_VIDEO + "/wvc1";

  public static final String AUDIO_UNKNOWN = BASE_TYPE_AUDIO + "/x-unknown";
  public static final String AUDIO_MP4 = BASE_TYPE_AUDIO + "/mp4";
  public static final String AUDIO_AAC = BASE_TYPE_AUDIO + "/mp4a-latm";
  public static final String AUDIO_WEBM = BASE_TYPE_AUDIO + "/webm";
  public static final String AUDIO_MPEG = BASE_TYPE_AUDIO + "/mpeg";
  public static final String AUDIO_MPEG_L1 = BASE_TYPE_AUDIO + "/mpeg-L1";
  public static final String AUDIO_MPEG_L2 = BASE_TYPE_AUDIO + "/mpeg-L2";
  public static final String AUDIO_RAW = BASE_TYPE_AUDIO + "/raw";
  public static final String AUDIO_AC3 = BASE_TYPE_AUDIO + "/ac3";
  public static final String AUDIO_E_AC3 = BASE_TYPE_AUDIO + "/eac3";
  public static final String AUDIO_TRUEHD = BASE_TYPE_AUDIO + "/true-hd";
  public static final String AUDIO_DTS = BASE_TYPE_AUDIO + "/vnd.dts";
  public static final String AUDIO_DTS_HD = BASE_TYPE_AUDIO + "/vnd.dts.hd";
  public static final String AUDIO_DTS_EXPRESS = BASE_TYPE_AUDIO + "/vnd.dts.hd;profile=lbr";
  public static final String AUDIO_VORBIS = BASE_TYPE_AUDIO + "/vorbis";
  public static final String AUDIO_OPUS = BASE_TYPE_AUDIO + "/opus";
  public static final String AUDIO_AMR_NB = BASE_TYPE_AUDIO + "/3gpp";
  public static final String AUDIO_AMR_WB = BASE_TYPE_AUDIO + "/amr-wb";
  public static final String AUDIO_FLAC = BASE_TYPE_AUDIO + "/x-flac";

  public static final String TEXT_UNKNOWN = BASE_TYPE_TEXT + "/x-unknown";
  public static final String TEXT_VTT = BASE_TYPE_TEXT + "/vtt";

  public static final String APPLICATION_MP4 = BASE_TYPE_APPLICATION + "/mp4";
  public static final String APPLICATION_WEBM = BASE_TYPE_APPLICATION + "/webm";
  public static final String APPLICATION_ID3 = BASE_TYPE_APPLICATION + "/id3";
  public static final String APPLICATION_EIA608 = BASE_TYPE_APPLICATION + "/eia-608";
  public static final String APPLICATION_SUBRIP = BASE_TYPE_APPLICATION + "/x-subrip";
  public static final String APPLICATION_TTML = BASE_TYPE_APPLICATION + "/ttml+xml";
  public static final String APPLICATION_M3U8 = BASE_TYPE_APPLICATION + "/x-mpegURL";
  public static final String APPLICATION_TX3G = BASE_TYPE_APPLICATION + "/x-quicktime-tx3g";
  public static final String APPLICATION_MP4VTT = BASE_TYPE_APPLICATION + "/x-mp4vtt";
  public static final String APPLICATION_VOBSUB = BASE_TYPE_APPLICATION + "/vobsub";
  public static final String APPLICATION_PGS = BASE_TYPE_APPLICATION + "/pgs";

  private MimeTypes() {}

  /**
   * Whether the top-level type of {@code mimeType} is audio.
   *
   * @param mimeType The mimeType to test.
   * @return Whether the top level type is audio.
   */
  public static boolean isAudio(String mimeType) {
    return getTopLevelType(mimeType).equals(BASE_TYPE_AUDIO);
  }

  /**
   * Whether the top-level type of {@code mimeType} is video.
   *
   * @param mimeType The mimeType to test.
   * @return Whether the top level type is video.
   */
  public static boolean isVideo(String mimeType) {
    return getTopLevelType(mimeType).equals(BASE_TYPE_VIDEO);
  }

  /**
   * Whether the top-level type of {@code mimeType} is text.
   *
   * @param mimeType The mimeType to test.
   * @return Whether the top level type is text.
   */
  public static boolean isText(String mimeType) {
    return getTopLevelType(mimeType).equals(BASE_TYPE_TEXT);
  }

  /**
   * Whether the top-level type of {@code mimeType} is application.
   *
   * @param mimeType The mimeType to test.
   * @return Whether the top level type is application.
   */
  public static boolean isApplication(String mimeType) {
    return getTopLevelType(mimeType).equals(BASE_TYPE_APPLICATION);
  }

  /**
   * Returns the top-level type of {@code mimeType}.
   *
   * @param mimeType The mimeType whose top-level type is required.
   * @return The top-level type.
   */
  private static String getTopLevelType(String mimeType) {
    int indexOfSlash = mimeType.indexOf('/');
    if (indexOfSlash == -1) {
      throw new IllegalArgumentException("Invalid mime type: " + mimeType);
    }
    return mimeType.substring(0, indexOfSlash);
  }

  /**
   * Returns the video mimeType type of {@code codecs}.
   *
   * @param codecs The codecs for which the video mimeType is required.
   * @return The video mimeType.
   */
  public static String getVideoMediaMimeType(String codecs) {
    if (codecs == null) {
      return MimeTypes.VIDEO_UNKNOWN;
    }
    String[] codecList = codecs.split(",");
    for (String codec : codecList) {
      codec = codec.trim();
      if (codec.startsWith("avc1") || codec.startsWith("avc3")) {
        return MimeTypes.VIDEO_H264;
      } else if (codec.startsWith("hev1") || codec.startsWith("hvc1")) {
        return MimeTypes.VIDEO_H265;
      } else if (codec.startsWith("vp9")) {
        return MimeTypes.VIDEO_VP9;
      } else if (codec.startsWith("vp8")) {
        return MimeTypes.VIDEO_VP8;
      }
    }
    return MimeTypes.VIDEO_UNKNOWN;
  }

  /**
   * Returns the audio mimeType type of {@code codecs}.
   *
   * @param codecs The codecs for which the audio mimeType is required.
   * @return The audio mimeType.
   */
  public static String getAudioMediaMimeType(String codecs) {
    if (codecs == null) {
      return MimeTypes.AUDIO_UNKNOWN;
    }
    String[] codecList = codecs.split(",");
    for (String codec : codecList) {
      codec = codec.trim();
      if (codec.startsWith("mp4a")) {
        return MimeTypes.AUDIO_AAC;
      } else if (codec.startsWith("ac-3") || codec.startsWith("dac3")) {
        return MimeTypes.AUDIO_AC3;
      } else if (codec.startsWith("ec-3") || codec.startsWith("dec3")) {
        return MimeTypes.AUDIO_E_AC3;
      } else if (codec.startsWith("dtsc")) {
        return MimeTypes.AUDIO_DTS;
      } else if (codec.startsWith("dtsh") || codec.startsWith("dtsl")) {
        return MimeTypes.AUDIO_DTS_HD;
      } else if (codec.startsWith("dtse")) {
        return MimeTypes.AUDIO_DTS_EXPRESS;
      } else if (codec.startsWith("opus")) {
        return MimeTypes.AUDIO_OPUS;
      } else if (codec.startsWith("vorbis")) {
        return MimeTypes.AUDIO_VORBIS;
      }
    }
    return MimeTypes.AUDIO_UNKNOWN;
  }

}

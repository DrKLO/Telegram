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
package com.google.android.exoplayer2.util;

import static com.google.android.exoplayer2.util.MimeTypes.normalizeMimeType;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.net.Uri;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;

/** Defines common file type constants and helper methods. */
public final class FileTypes {

  /**
   * File types. One of {@link #UNKNOWN}, {@link #AC3}, {@link #AC4}, {@link #ADTS}, {@link #AMR},
   * {@link #FLAC}, {@link #FLV}, {@link #MATROSKA}, {@link #MP3}, {@link #MP4}, {@link #OGG},
   * {@link #PS}, {@link #TS}, {@link #WAV}, {@link #WEBVTT}, {@link #JPEG} and {@link #MIDI}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    UNKNOWN, AC3, AC4, ADTS, AMR, FLAC, FLV, MATROSKA, MP3, MP4, OGG, PS, TS, WAV, WEBVTT, JPEG,
    MIDI, AVI
  })
  public @interface Type {}
  /** Unknown file type. */
  public static final int UNKNOWN = -1;
  /** File type for the AC-3 and E-AC-3 formats. */
  public static final int AC3 = 0;
  /** File type for the AC-4 format. */
  public static final int AC4 = 1;
  /** File type for the ADTS format. */
  public static final int ADTS = 2;
  /** File type for the AMR format. */
  public static final int AMR = 3;
  /** File type for the FLAC format. */
  public static final int FLAC = 4;
  /** File type for the FLV format. */
  public static final int FLV = 5;
  /** File type for the Matroska and WebM formats. */
  public static final int MATROSKA = 6;
  /** File type for the MP3 format. */
  public static final int MP3 = 7;
  /** File type for the MP4 format. */
  public static final int MP4 = 8;
  /** File type for the Ogg format. */
  public static final int OGG = 9;
  /** File type for the MPEG-PS format. */
  public static final int PS = 10;
  /** File type for the MPEG-TS format. */
  public static final int TS = 11;
  /** File type for the WAV format. */
  public static final int WAV = 12;
  /** File type for the WebVTT format. */
  public static final int WEBVTT = 13;
  /** File type for the JPEG format. */
  public static final int JPEG = 14;
  /** File type for the MIDI format. */
  public static final int MIDI = 15;
  /** File type for the AVI format. */
  public static final int AVI = 16;

  @VisibleForTesting /* package */ static final String HEADER_CONTENT_TYPE = "Content-Type";

  private static final String EXTENSION_AC3 = ".ac3";
  private static final String EXTENSION_EC3 = ".ec3";
  private static final String EXTENSION_AC4 = ".ac4";
  private static final String EXTENSION_ADTS = ".adts";
  private static final String EXTENSION_AAC = ".aac";
  private static final String EXTENSION_AMR = ".amr";
  private static final String EXTENSION_FLAC = ".flac";
  private static final String EXTENSION_FLV = ".flv";
  private static final String EXTENSION_MID = ".mid";
  private static final String EXTENSION_MIDI = ".midi";
  private static final String EXTENSION_SMF = ".smf";
  private static final String EXTENSION_PREFIX_MK = ".mk";
  private static final String EXTENSION_WEBM = ".webm";
  private static final String EXTENSION_PREFIX_OG = ".og";
  private static final String EXTENSION_OPUS = ".opus";
  private static final String EXTENSION_MP3 = ".mp3";
  private static final String EXTENSION_MP4 = ".mp4";
  private static final String EXTENSION_PREFIX_M4 = ".m4";
  private static final String EXTENSION_PREFIX_MP4 = ".mp4";
  private static final String EXTENSION_PREFIX_CMF = ".cmf";
  private static final String EXTENSION_PS = ".ps";
  private static final String EXTENSION_MPEG = ".mpeg";
  private static final String EXTENSION_MPG = ".mpg";
  private static final String EXTENSION_M2P = ".m2p";
  private static final String EXTENSION_TS = ".ts";
  private static final String EXTENSION_PREFIX_TS = ".ts";
  private static final String EXTENSION_WAV = ".wav";
  private static final String EXTENSION_WAVE = ".wave";
  private static final String EXTENSION_VTT = ".vtt";
  private static final String EXTENSION_WEBVTT = ".webvtt";
  private static final String EXTENSION_JPG = ".jpg";
  private static final String EXTENSION_JPEG = ".jpeg";
  private static final String EXTENSION_AVI = ".avi";

  private FileTypes() {}

  /** Returns the {@link Type} corresponding to the response headers provided. */
  public static @FileTypes.Type int inferFileTypeFromResponseHeaders(
      Map<String, List<String>> responseHeaders) {
    @Nullable List<String> contentTypes = responseHeaders.get(HEADER_CONTENT_TYPE);
    @Nullable
    String mimeType = contentTypes == null || contentTypes.isEmpty() ? null : contentTypes.get(0);
    return inferFileTypeFromMimeType(mimeType);
  }

  /**
   * Returns the {@link Type} corresponding to the MIME type provided.
   *
   * <p>Returns {@link #UNKNOWN} if the mime type is {@code null}.
   */
  public static @FileTypes.Type int inferFileTypeFromMimeType(@Nullable String mimeType) {
    if (mimeType == null) {
      return FileTypes.UNKNOWN;
    }
    mimeType = normalizeMimeType(mimeType);
    switch (mimeType) {
      case MimeTypes.AUDIO_AC3:
      case MimeTypes.AUDIO_E_AC3:
      case MimeTypes.AUDIO_E_AC3_JOC:
        return FileTypes.AC3;
      case MimeTypes.AUDIO_AC4:
        return FileTypes.AC4;
      case MimeTypes.AUDIO_AMR:
      case MimeTypes.AUDIO_AMR_NB:
      case MimeTypes.AUDIO_AMR_WB:
        return FileTypes.AMR;
      case MimeTypes.AUDIO_FLAC:
        return FileTypes.FLAC;
      case MimeTypes.VIDEO_FLV:
        return FileTypes.FLV;
      case MimeTypes.AUDIO_MIDI:
        return FileTypes.MIDI;
      case MimeTypes.VIDEO_MATROSKA:
      case MimeTypes.AUDIO_MATROSKA:
      case MimeTypes.VIDEO_WEBM:
      case MimeTypes.AUDIO_WEBM:
      case MimeTypes.APPLICATION_WEBM:
        return FileTypes.MATROSKA;
      case MimeTypes.AUDIO_MPEG:
        return FileTypes.MP3;
      case MimeTypes.VIDEO_MP4:
      case MimeTypes.AUDIO_MP4:
      case MimeTypes.APPLICATION_MP4:
        return FileTypes.MP4;
      case MimeTypes.AUDIO_OGG:
        return FileTypes.OGG;
      case MimeTypes.VIDEO_PS:
        return FileTypes.PS;
      case MimeTypes.VIDEO_MP2T:
        return FileTypes.TS;
      case MimeTypes.AUDIO_WAV:
        return FileTypes.WAV;
      case MimeTypes.TEXT_VTT:
        return FileTypes.WEBVTT;
      case MimeTypes.IMAGE_JPEG:
        return FileTypes.JPEG;
      case MimeTypes.VIDEO_AVI:
        return FileTypes.AVI;
      default:
        return FileTypes.UNKNOWN;
    }
  }

  /** Returns the {@link Type} corresponding to the {@link Uri} provided. */
  public static @FileTypes.Type int inferFileTypeFromUri(Uri uri) {
    @Nullable String filename = uri.getLastPathSegment();
    if (filename == null) {
      return FileTypes.UNKNOWN;
    } else if (filename.endsWith(EXTENSION_AC3) || filename.endsWith(EXTENSION_EC3)) {
      return FileTypes.AC3;
    } else if (filename.endsWith(EXTENSION_AC4)) {
      return FileTypes.AC4;
    } else if (filename.endsWith(EXTENSION_ADTS) || filename.endsWith(EXTENSION_AAC)) {
      return FileTypes.ADTS;
    } else if (filename.endsWith(EXTENSION_AMR)) {
      return FileTypes.AMR;
    } else if (filename.endsWith(EXTENSION_FLAC)) {
      return FileTypes.FLAC;
    } else if (filename.endsWith(EXTENSION_FLV)) {
      return FileTypes.FLV;
    } else if (filename.endsWith(EXTENSION_MID)
        || filename.endsWith(EXTENSION_MIDI)
        || filename.endsWith(EXTENSION_SMF)) {
      return FileTypes.MIDI;
    } else if (filename.startsWith(
            EXTENSION_PREFIX_MK,
            /* toffset= */ filename.length() - (EXTENSION_PREFIX_MK.length() + 1))
        || filename.endsWith(EXTENSION_WEBM)) {
      return FileTypes.MATROSKA;
    } else if (filename.endsWith(EXTENSION_MP3)) {
      return FileTypes.MP3;
    } else if (filename.endsWith(EXTENSION_MP4)
        || filename.startsWith(
            EXTENSION_PREFIX_M4,
            /* toffset= */ filename.length() - (EXTENSION_PREFIX_M4.length() + 1))
        || filename.startsWith(
            EXTENSION_PREFIX_MP4,
            /* toffset= */ filename.length() - (EXTENSION_PREFIX_MP4.length() + 1))
        || filename.startsWith(
            EXTENSION_PREFIX_CMF,
            /* toffset= */ filename.length() - (EXTENSION_PREFIX_CMF.length() + 1))) {
      return FileTypes.MP4;
    } else if (filename.startsWith(
            EXTENSION_PREFIX_OG,
            /* toffset= */ filename.length() - (EXTENSION_PREFIX_OG.length() + 1))
        || filename.endsWith(EXTENSION_OPUS)) {
      return FileTypes.OGG;
    } else if (filename.endsWith(EXTENSION_PS)
        || filename.endsWith(EXTENSION_MPEG)
        || filename.endsWith(EXTENSION_MPG)
        || filename.endsWith(EXTENSION_M2P)) {
      return FileTypes.PS;
    } else if (filename.endsWith(EXTENSION_TS)
        || filename.startsWith(
            EXTENSION_PREFIX_TS,
            /* toffset= */ filename.length() - (EXTENSION_PREFIX_TS.length() + 1))) {
      return FileTypes.TS;
    } else if (filename.endsWith(EXTENSION_WAV) || filename.endsWith(EXTENSION_WAVE)) {
      return FileTypes.WAV;
    } else if (filename.endsWith(EXTENSION_VTT) || filename.endsWith(EXTENSION_WEBVTT)) {
      return FileTypes.WEBVTT;
    } else if (filename.endsWith(EXTENSION_JPG) || filename.endsWith(EXTENSION_JPEG)) {
      return FileTypes.JPEG;
    } else if (filename.endsWith(EXTENSION_AVI)) {
      return FileTypes.AVI;
    } else {
      return FileTypes.UNKNOWN;
    }
  }
}

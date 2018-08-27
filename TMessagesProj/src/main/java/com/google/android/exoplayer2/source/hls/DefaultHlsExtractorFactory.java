/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.net.Uri;
import android.text.TextUtils;
import android.util.Pair;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor;
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer2.extractor.ts.Ac3Extractor;
import com.google.android.exoplayer2.extractor.ts.AdtsExtractor;
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory;
import com.google.android.exoplayer2.extractor.ts.TsExtractor;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Default {@link HlsExtractorFactory} implementation.
 */
public final class DefaultHlsExtractorFactory implements HlsExtractorFactory {

  public static final String AAC_FILE_EXTENSION = ".aac";
  public static final String AC3_FILE_EXTENSION = ".ac3";
  public static final String EC3_FILE_EXTENSION = ".ec3";
  public static final String MP3_FILE_EXTENSION = ".mp3";
  public static final String MP4_FILE_EXTENSION = ".mp4";
  public static final String M4_FILE_EXTENSION_PREFIX = ".m4";
  public static final String MP4_FILE_EXTENSION_PREFIX = ".mp4";
  public static final String VTT_FILE_EXTENSION = ".vtt";
  public static final String WEBVTT_FILE_EXTENSION = ".webvtt";

  @Override
  public Pair<Extractor, Boolean> createExtractor(
      Extractor previousExtractor,
      Uri uri,
      Format format,
      List<Format> muxedCaptionFormats,
      DrmInitData drmInitData,
      TimestampAdjuster timestampAdjuster,
      Map<String, List<String>> responseHeaders) {
    String lastPathSegment = uri.getLastPathSegment();
    if (lastPathSegment == null) {
      lastPathSegment = "";
    }
    boolean isPackedAudioExtractor = false;
    Extractor extractor;
    if (MimeTypes.TEXT_VTT.equals(format.sampleMimeType)
        || lastPathSegment.endsWith(WEBVTT_FILE_EXTENSION)
        || lastPathSegment.endsWith(VTT_FILE_EXTENSION)) {
      extractor = new WebvttExtractor(format.language, timestampAdjuster);
    } else if (lastPathSegment.endsWith(AAC_FILE_EXTENSION)) {
      isPackedAudioExtractor = true;
      extractor = new AdtsExtractor();
    } else if (lastPathSegment.endsWith(AC3_FILE_EXTENSION)
        || lastPathSegment.endsWith(EC3_FILE_EXTENSION)) {
      isPackedAudioExtractor = true;
      extractor = new Ac3Extractor();
    } else if (lastPathSegment.endsWith(MP3_FILE_EXTENSION)) {
      isPackedAudioExtractor = true;
      extractor = new Mp3Extractor(0, 0);
    } else if (previousExtractor != null) {
      // Only reuse TS and fMP4 extractors.
      extractor = previousExtractor;
    } else if (lastPathSegment.endsWith(MP4_FILE_EXTENSION)
        || lastPathSegment.startsWith(M4_FILE_EXTENSION_PREFIX, lastPathSegment.length() - 4)
        || lastPathSegment.startsWith(MP4_FILE_EXTENSION_PREFIX, lastPathSegment.length() - 5)) {
      extractor =
          new FragmentedMp4Extractor(
              /* flags= */ 0,
              timestampAdjuster,
              /* sideloadedTrack= */ null,
              drmInitData,
              muxedCaptionFormats != null ? muxedCaptionFormats : Collections.emptyList());
    } else {
      // For any other file extension, we assume TS format.
      @DefaultTsPayloadReaderFactory.Flags
      int esReaderFactoryFlags = DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM;
      if (muxedCaptionFormats != null) {
        // The playlist declares closed caption renditions, we should ignore descriptors.
        esReaderFactoryFlags |= DefaultTsPayloadReaderFactory.FLAG_OVERRIDE_CAPTION_DESCRIPTORS;
      } else {
        // The playlist does not provide any closed caption information. We preemptively declare a
        // closed caption track on channel 0.
        muxedCaptionFormats =
            Collections.singletonList(
                Format.createTextSampleFormat(
                    /* id= */ null,
                    MimeTypes.APPLICATION_CEA608,
                    /* selectionFlags= */ 0,
                    /* language= */ null));
      }
      String codecs = format.codecs;
      if (!TextUtils.isEmpty(codecs)) {
        // Sometimes AAC and H264 streams are declared in TS chunks even though they don't really
        // exist. If we know from the codec attribute that they don't exist, then we can
        // explicitly ignore them even if they're declared.
        if (!MimeTypes.AUDIO_AAC.equals(MimeTypes.getAudioMediaMimeType(codecs))) {
          esReaderFactoryFlags |= DefaultTsPayloadReaderFactory.FLAG_IGNORE_AAC_STREAM;
        }
        if (!MimeTypes.VIDEO_H264.equals(MimeTypes.getVideoMediaMimeType(codecs))) {
          esReaderFactoryFlags |= DefaultTsPayloadReaderFactory.FLAG_IGNORE_H264_STREAM;
        }
      }
      extractor = new TsExtractor(TsExtractor.MODE_HLS, timestampAdjuster,
          new DefaultTsPayloadReaderFactory(esReaderFactoryFlags, muxedCaptionFormats));
    }
    return Pair.create(extractor, isPackedAudioExtractor);
  }

}

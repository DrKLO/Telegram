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

package com.google.android.exoplayer2.source.rtsp.reader;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.source.rtsp.RtpPayloadFormat;
import com.google.android.exoplayer2.util.MimeTypes;

/** Default {@link RtpPayloadReader.Factory} implementation. */
/* package */ public final class DefaultRtpPayloadReaderFactory
    implements RtpPayloadReader.Factory {

  @Override
  @Nullable
  public RtpPayloadReader createPayloadReader(RtpPayloadFormat payloadFormat) {
    switch (checkNotNull(payloadFormat.format.sampleMimeType)) {
      case MimeTypes.AUDIO_AC3:
        return new RtpAc3Reader(payloadFormat);
      case MimeTypes.AUDIO_AAC:
        if (payloadFormat.mediaEncoding.equals(RtpPayloadFormat.RTP_MEDIA_MPEG4_LATM_AUDIO)) {
          return new RtpMp4aReader(payloadFormat);
        } else {
          return new RtpAacReader(payloadFormat);
        }
      case MimeTypes.AUDIO_AMR_NB:
      case MimeTypes.AUDIO_AMR_WB:
        return new RtpAmrReader(payloadFormat);
      case MimeTypes.AUDIO_OPUS:
        return new RtpOpusReader(payloadFormat);
      case MimeTypes.AUDIO_RAW:
      case MimeTypes.AUDIO_ALAW:
      case MimeTypes.AUDIO_MLAW:
        return new RtpPcmReader(payloadFormat);
      case MimeTypes.VIDEO_H263:
        return new RtpH263Reader(payloadFormat);
      case MimeTypes.VIDEO_H264:
        return new RtpH264Reader(payloadFormat);
      case MimeTypes.VIDEO_H265:
        return new RtpH265Reader(payloadFormat);
      case MimeTypes.VIDEO_MP4V:
        return new RtpMpeg4Reader(payloadFormat);
      case MimeTypes.VIDEO_VP8:
        return new RtpVp8Reader(payloadFormat);
      case MimeTypes.VIDEO_VP9:
        return new RtpVp9Reader(payloadFormat);
      default:
        // No supported reader, returning null.
    }
    return null;
  }
}

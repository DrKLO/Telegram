/*
 * Copyright 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.avi;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;

/** Holds the {@link Format} information contained in an STRF chunk. */
/* package */ final class StreamFormatChunk implements AviChunk {
  private static final String TAG = "StreamFormatChunk";

  @Nullable
  public static AviChunk parseFrom(int trackType, ParsableByteArray body) {
    if (trackType == C.TRACK_TYPE_VIDEO) {
      return parseBitmapInfoHeader(body);
    } else if (trackType == C.TRACK_TYPE_AUDIO) {
      return parseWaveFormatEx(body);
    } else {
      Log.w(
          TAG,
          "Ignoring strf box for unsupported track type: " + Util.getTrackTypeString(trackType));
      return null;
    }
  }

  public final Format format;

  public StreamFormatChunk(Format format) {
    this.format = format;
  }

  @Override
  public int getType() {
    return AviExtractor.FOURCC_strf;
  }

  @Nullable
  private static AviChunk parseBitmapInfoHeader(ParsableByteArray body) {
    body.skipBytes(4); // biSize.
    int width = body.readLittleEndianInt();
    int height = body.readLittleEndianInt();
    body.skipBytes(4); // biPlanes (2 bytes), biBitCount (2 bytes).
    int compression = body.readLittleEndianInt();
    String mimeType = getMimeTypeFromCompression(compression);
    if (mimeType == null) {
      Log.w(TAG, "Ignoring track with unsupported compression " + compression);
      return null;
    }
    Format.Builder formatBuilder = new Format.Builder();
    formatBuilder.setWidth(width).setHeight(height).setSampleMimeType(mimeType);
    return new StreamFormatChunk(formatBuilder.build());
  }

  // Syntax defined by the WAVEFORMATEX structure. See
  // https://docs.microsoft.com/en-us/previous-versions/dd757713(v=vs.85).
  @Nullable
  private static AviChunk parseWaveFormatEx(ParsableByteArray body) {
    int formatTag = body.readLittleEndianUnsignedShort();
    @Nullable String mimeType = getMimeTypeFromTag(formatTag);
    if (mimeType == null) {
      Log.w(TAG, "Ignoring track with unsupported format tag " + formatTag);
      return null;
    }
    int channelCount = body.readLittleEndianUnsignedShort();
    int samplesPerSecond = body.readLittleEndianInt();
    body.skipBytes(6); // averageBytesPerSecond (4 bytes), nBlockAlign (2 bytes).
    int bitsPerSample = body.readUnsignedShort();
    int pcmEncoding = Util.getPcmEncoding(bitsPerSample);
    int cbSize = body.readLittleEndianUnsignedShort();
    byte[] codecData = new byte[cbSize];
    body.readBytes(codecData, /* offset= */ 0, codecData.length);

    Format.Builder formatBuilder = new Format.Builder();
    formatBuilder
        .setSampleMimeType(mimeType)
        .setChannelCount(channelCount)
        .setSampleRate(samplesPerSecond);
    if (MimeTypes.AUDIO_RAW.equals(mimeType) && pcmEncoding != C.ENCODING_INVALID) {
      formatBuilder.setPcmEncoding(pcmEncoding);
    }
    if (MimeTypes.AUDIO_AAC.equals(mimeType) && codecData.length > 0) {
      formatBuilder.setInitializationData(ImmutableList.of(codecData));
    }
    return new StreamFormatChunk(formatBuilder.build());
  }

  @Nullable
  private static String getMimeTypeFromTag(int tag) {
    switch (tag) {
      case 0x1: // WAVE_FORMAT_PCM
        return MimeTypes.AUDIO_RAW;
      case 0x55: // WAVE_FORMAT_MPEGLAYER3
        return MimeTypes.AUDIO_MPEG;
      case 0xff: // WAVE_FORMAT_AAC
        return MimeTypes.AUDIO_AAC;
      case 0x2000: // WAVE_FORMAT_DVM - AC3
        return MimeTypes.AUDIO_AC3;
      case 0x2001: // WAVE_FORMAT_DTS2
        return MimeTypes.AUDIO_DTS;
      default:
        return null;
    }
  }

  @Nullable
  private static String getMimeTypeFromCompression(int compression) {
    switch (compression) {
      case 0x3234504d: // MP42
        return MimeTypes.VIDEO_MP42;
      case 0x3334504d: // MP43
        return MimeTypes.VIDEO_MP43;
      case 0x34363248: // H264
      case 0x31637661: // avc1
      case 0x31435641: // AVC1
        return MimeTypes.VIDEO_H264;
      case 0x44495633: // 3VID
      case 0x78766964: // divx
      case 0x58564944: // DIVX
      case 0x30355844: // DX50
      case 0x34504d46: // FMP4
      case 0x64697678: // xvid
      case 0x44495658: // XVID
        return MimeTypes.VIDEO_MP4V;
      case 0x47504a4d: // MJPG
      case 0x67706a6d: // mjpg
        return MimeTypes.VIDEO_MJPEG;
      default:
        return null;
    }
  }
}

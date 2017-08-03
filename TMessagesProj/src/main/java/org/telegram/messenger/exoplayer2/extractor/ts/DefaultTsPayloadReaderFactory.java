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
package org.telegram.messenger.exoplayer2.extractor.ts;

import android.support.annotation.IntDef;
import android.util.SparseArray;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.extractor.ts.TsPayloadReader.EsInfo;
import org.telegram.messenger.exoplayer2.util.MimeTypes;
import org.telegram.messenger.exoplayer2.util.ParsableByteArray;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default implementation for {@link TsPayloadReader.Factory}.
 */
public final class DefaultTsPayloadReaderFactory implements TsPayloadReader.Factory {

  /**
   * Flags controlling elementary stream readers' behavior.
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(flag = true, value = {FLAG_ALLOW_NON_IDR_KEYFRAMES, FLAG_IGNORE_AAC_STREAM,
      FLAG_IGNORE_H264_STREAM, FLAG_DETECT_ACCESS_UNITS, FLAG_IGNORE_SPLICE_INFO_STREAM,
      FLAG_OVERRIDE_CAPTION_DESCRIPTORS})
  public @interface Flags {}
  public static final int FLAG_ALLOW_NON_IDR_KEYFRAMES = 1;
  public static final int FLAG_IGNORE_AAC_STREAM = 1 << 1;
  public static final int FLAG_IGNORE_H264_STREAM = 1 << 2;
  public static final int FLAG_DETECT_ACCESS_UNITS = 1 << 3;
  public static final int FLAG_IGNORE_SPLICE_INFO_STREAM = 1 << 4;
  public static final int FLAG_OVERRIDE_CAPTION_DESCRIPTORS = 1 << 5;

  private static final int DESCRIPTOR_TAG_CAPTION_SERVICE = 0x86;

  @Flags private final int flags;
  private final List<Format> closedCaptionFormats;

  public DefaultTsPayloadReaderFactory() {
    this(0);
  }

  /**
   * @param flags A combination of {@code FLAG_*} values that control the behavior of the created
   *     readers.
   */
  public DefaultTsPayloadReaderFactory(@Flags int flags) {
    this(flags, Collections.<Format>emptyList());
  }

  /**
   * @param flags A combination of {@code FLAG_*} values that control the behavior of the created
   *     readers.
   * @param closedCaptionFormats {@link Format}s to be exposed by payload readers for streams with
   *     embedded closed captions when no caption service descriptors are provided. If
   *     {@link #FLAG_OVERRIDE_CAPTION_DESCRIPTORS} is set, {@code closedCaptionFormats} overrides
   *     any descriptor information. If not set, and {@code closedCaptionFormats} is empty, a
   *     closed caption track with {@link Format#accessibilityChannel} {@link Format#NO_VALUE} will
   *     be exposed.
   */
  public DefaultTsPayloadReaderFactory(@Flags int flags, List<Format> closedCaptionFormats) {
    this.flags = flags;
    if (!isSet(FLAG_OVERRIDE_CAPTION_DESCRIPTORS) && closedCaptionFormats.isEmpty()) {
      closedCaptionFormats = Collections.singletonList(Format.createTextSampleFormat(null,
          MimeTypes.APPLICATION_CEA608, null, Format.NO_VALUE, 0, null, null));
    }
    this.closedCaptionFormats = closedCaptionFormats;
  }

  @Override
  public SparseArray<TsPayloadReader> createInitialPayloadReaders() {
    return new SparseArray<>();
  }

  @Override
  public TsPayloadReader createPayloadReader(int streamType, EsInfo esInfo) {
    switch (streamType) {
      case TsExtractor.TS_STREAM_TYPE_MPA:
      case TsExtractor.TS_STREAM_TYPE_MPA_LSF:
        return new PesReader(new MpegAudioReader(esInfo.language));
      case TsExtractor.TS_STREAM_TYPE_AAC:
        return isSet(FLAG_IGNORE_AAC_STREAM)
            ? null : new PesReader(new AdtsReader(false, esInfo.language));
      case TsExtractor.TS_STREAM_TYPE_AC3:
      case TsExtractor.TS_STREAM_TYPE_E_AC3:
        return new PesReader(new Ac3Reader(esInfo.language));
      case TsExtractor.TS_STREAM_TYPE_DTS:
      case TsExtractor.TS_STREAM_TYPE_HDMV_DTS:
        return new PesReader(new DtsReader(esInfo.language));
      case TsExtractor.TS_STREAM_TYPE_H262:
        return new PesReader(new H262Reader());
      case TsExtractor.TS_STREAM_TYPE_H264:
        return isSet(FLAG_IGNORE_H264_STREAM) ? null
            : new PesReader(new H264Reader(buildSeiReader(esInfo),
                isSet(FLAG_ALLOW_NON_IDR_KEYFRAMES), isSet(FLAG_DETECT_ACCESS_UNITS)));
      case TsExtractor.TS_STREAM_TYPE_H265:
        return new PesReader(new H265Reader(buildSeiReader(esInfo)));
      case TsExtractor.TS_STREAM_TYPE_SPLICE_INFO:
        return isSet(FLAG_IGNORE_SPLICE_INFO_STREAM)
            ? null : new SectionReader(new SpliceInfoSectionReader());
      case TsExtractor.TS_STREAM_TYPE_ID3:
        return new PesReader(new Id3Reader());
      case TsExtractor.TS_STREAM_TYPE_DVBSUBS:
        return new PesReader(
            new DvbSubtitleReader(esInfo.dvbSubtitleInfos));
      default:
        return null;
    }
  }

  /**
   * If {@link #FLAG_OVERRIDE_CAPTION_DESCRIPTORS} is set, returns a {@link SeiReader} for
   * {@link #closedCaptionFormats}. If unset, parses the PMT descriptor information and returns a
   * {@link SeiReader} for the declared formats, or {@link #closedCaptionFormats} if the descriptor
   * is not present.
   *
   * @param esInfo The {@link EsInfo} passed to {@link #createPayloadReader(int, EsInfo)}.
   * @return A {@link SeiReader} for closed caption tracks.
   */
  private SeiReader buildSeiReader(EsInfo esInfo) {
    if (isSet(FLAG_OVERRIDE_CAPTION_DESCRIPTORS)) {
      return new SeiReader(closedCaptionFormats);
    }
    ParsableByteArray scratchDescriptorData = new ParsableByteArray(esInfo.descriptorBytes);
    List<Format> closedCaptionFormats = this.closedCaptionFormats;
    while (scratchDescriptorData.bytesLeft() > 0) {
      int descriptorTag = scratchDescriptorData.readUnsignedByte();
      int descriptorLength = scratchDescriptorData.readUnsignedByte();
      int nextDescriptorPosition = scratchDescriptorData.getPosition() + descriptorLength;
      if (descriptorTag == DESCRIPTOR_TAG_CAPTION_SERVICE) {
        // Note: see ATSC A/65 for detailed information about the caption service descriptor.
        closedCaptionFormats = new ArrayList<>();
        int numberOfServices = scratchDescriptorData.readUnsignedByte() & 0x1F;
        for (int i = 0; i < numberOfServices; i++) {
          String language = scratchDescriptorData.readString(3);
          int captionTypeByte = scratchDescriptorData.readUnsignedByte();
          boolean isDigital = (captionTypeByte & 0x80) != 0;
          String mimeType;
          int accessibilityChannel;
          if (isDigital) {
            mimeType = MimeTypes.APPLICATION_CEA708;
            accessibilityChannel = captionTypeByte & 0x3F;
          } else {
            mimeType = MimeTypes.APPLICATION_CEA608;
            accessibilityChannel = 1;
          }
          closedCaptionFormats.add(Format.createTextSampleFormat(null, mimeType, null,
              Format.NO_VALUE, 0, language, accessibilityChannel, null));
          // Skip easy_reader(1), wide_aspect_ratio(1), reserved(14).
          scratchDescriptorData.skipBytes(2);
        }
      } else {
        // Unknown descriptor. Ignore.
      }
      scratchDescriptorData.setPosition(nextDescriptorPosition);
    }
    return new SeiReader(closedCaptionFormats);
  }

  private boolean isSet(@Flags int flag) {
    return (flags & flag) != 0;
  }

}

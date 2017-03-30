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
import org.telegram.messenger.exoplayer2.extractor.ts.TsPayloadReader.EsInfo;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Default implementation for {@link TsPayloadReader.Factory}.
 */
public final class DefaultTsPayloadReaderFactory implements TsPayloadReader.Factory {

  /**
   * Flags controlling elementary stream readers behaviour.
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(flag = true, value = {FLAG_ALLOW_NON_IDR_KEYFRAMES, FLAG_IGNORE_AAC_STREAM,
      FLAG_IGNORE_H264_STREAM, FLAG_DETECT_ACCESS_UNITS})
  public @interface Flags {
  }
  public static final int FLAG_ALLOW_NON_IDR_KEYFRAMES = 1;
  public static final int FLAG_IGNORE_AAC_STREAM = 2;
  public static final int FLAG_IGNORE_H264_STREAM = 4;
  public static final int FLAG_DETECT_ACCESS_UNITS = 8;

  @Flags
  private final int flags;

  public DefaultTsPayloadReaderFactory() {
    this(0);
  }

  public DefaultTsPayloadReaderFactory(@Flags int flags) {
    this.flags = flags;
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
        return (flags & FLAG_IGNORE_AAC_STREAM) != 0 ? null
            : new PesReader(new AdtsReader(false, esInfo.language));
      case TsExtractor.TS_STREAM_TYPE_AC3:
      case TsExtractor.TS_STREAM_TYPE_E_AC3:
        return new PesReader(new Ac3Reader(esInfo.language));
      case TsExtractor.TS_STREAM_TYPE_DTS:
      case TsExtractor.TS_STREAM_TYPE_HDMV_DTS:
        return new PesReader(new DtsReader(esInfo.language));
      case TsExtractor.TS_STREAM_TYPE_H262:
        return new PesReader(new H262Reader());
      case TsExtractor.TS_STREAM_TYPE_H264:
        return (flags & FLAG_IGNORE_H264_STREAM) != 0 ? null
            : new PesReader(new H264Reader((flags & FLAG_ALLOW_NON_IDR_KEYFRAMES) != 0,
                (flags & FLAG_DETECT_ACCESS_UNITS) != 0));
      case TsExtractor.TS_STREAM_TYPE_H265:
        return new PesReader(new H265Reader());
      case TsExtractor.TS_STREAM_TYPE_SPLICE_INFO:
        return new SectionReader(new SpliceInfoSectionReader());
      case TsExtractor.TS_STREAM_TYPE_ID3:
        return new PesReader(new Id3Reader());
      default:
        return null;
    }
  }

}

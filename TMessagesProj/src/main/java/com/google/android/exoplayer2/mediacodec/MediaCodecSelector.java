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
package com.google.android.exoplayer2.mediacodec;

import android.media.MediaCodec;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import java.util.Collections;
import java.util.List;

/**
 * Selector of {@link MediaCodec} instances.
 */
public interface MediaCodecSelector {

  /**
   * Default implementation of {@link MediaCodecSelector}, which returns the preferred decoder for
   * the given format.
   */
  MediaCodecSelector DEFAULT =
      new MediaCodecSelector() {
        @Override
        public List<MediaCodecInfo> getDecoderInfos(Format format, boolean requiresSecureDecoder)
            throws DecoderQueryException {
          List<MediaCodecInfo> decoderInfos =
              MediaCodecUtil.getDecoderInfos(format.sampleMimeType, requiresSecureDecoder);
          return decoderInfos.isEmpty()
              ? Collections.emptyList()
              : Collections.singletonList(decoderInfos.get(0));
        }

        @Override
        public @Nullable MediaCodecInfo getPassthroughDecoderInfo() throws DecoderQueryException {
          return MediaCodecUtil.getPassthroughDecoderInfo();
        }
      };

  /**
   * A {@link MediaCodecSelector} that returns a list of decoders in priority order, allowing
   * fallback to less preferred decoders if initialization fails.
   *
   * <p>Note: if a hardware-accelerated video decoder fails to initialize, this selector may provide
   * a software video decoder to use as a fallback. Using software decoding can be inefficient, and
   * the decoder may be too slow to keep up with the playback position.
   */
  MediaCodecSelector DEFAULT_WITH_FALLBACK =
      new MediaCodecSelector() {
        @Override
        public List<MediaCodecInfo> getDecoderInfos(Format format, boolean requiresSecureDecoder)
            throws DecoderQueryException {
          return MediaCodecUtil.getDecoderInfos(format.sampleMimeType, requiresSecureDecoder);
        }

        @Override
        public @Nullable MediaCodecInfo getPassthroughDecoderInfo() throws DecoderQueryException {
          return MediaCodecUtil.getPassthroughDecoderInfo();
        }
      };

  /**
   * Returns a list of decoders that can decode media in the specified format, in priority order.
   *
   * @param format The format for which a decoder is required.
   * @param requiresSecureDecoder Whether a secure decoder is required.
   * @return A list of {@link MediaCodecInfo}s corresponding to decoders. May be empty.
   * @throws DecoderQueryException Thrown if there was an error querying decoders.
   */
  List<MediaCodecInfo> getDecoderInfos(Format format, boolean requiresSecureDecoder)
      throws DecoderQueryException;

  /**
   * Selects a decoder to instantiate for audio passthrough.
   *
   * @return A {@link MediaCodecInfo} describing the decoder, or null if no suitable decoder exists.
   * @throws DecoderQueryException Thrown if there was an error querying decoders.
   */
  @Nullable
  MediaCodecInfo getPassthroughDecoderInfo() throws DecoderQueryException;
}

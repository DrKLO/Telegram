/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.decoder;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotEmpty;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.video.ColorInfo;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The result of an evaluation to determine whether a decoder can be reused for a new input format.
 */
public final class DecoderReuseEvaluation {

  /** Possible outcomes of the evaluation. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    REUSE_RESULT_NO,
    REUSE_RESULT_YES_WITH_FLUSH,
    REUSE_RESULT_YES_WITH_RECONFIGURATION,
    REUSE_RESULT_YES_WITHOUT_RECONFIGURATION
  })
  public @interface DecoderReuseResult {}
  /** The decoder cannot be reused. */
  public static final int REUSE_RESULT_NO = 0;
  /** The decoder can be reused, but must be flushed. */
  public static final int REUSE_RESULT_YES_WITH_FLUSH = 1;
  /**
   * The decoder can be reused. It does not need to be flushed, but must be reconfigured by
   * prefixing the next input buffer with the new format's configuration data.
   */
  public static final int REUSE_RESULT_YES_WITH_RECONFIGURATION = 2;
  /** The decoder can be kept. It does not need to be flushed and no reconfiguration is required. */
  public static final int REUSE_RESULT_YES_WITHOUT_RECONFIGURATION = 3;

  /** Possible reasons why reuse is not possible. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef(
      flag = true,
      value = {
        DISCARD_REASON_REUSE_NOT_IMPLEMENTED,
        DISCARD_REASON_WORKAROUND,
        DISCARD_REASON_APP_OVERRIDE,
        DISCARD_REASON_MIME_TYPE_CHANGED,
        DISCARD_REASON_OPERATING_RATE_CHANGED,
        DISCARD_REASON_INITIALIZATION_DATA_CHANGED,
        DISCARD_REASON_DRM_SESSION_CHANGED,
        DISCARD_REASON_MAX_INPUT_SIZE_EXCEEDED,
        DISCARD_REASON_VIDEO_MAX_RESOLUTION_EXCEEDED,
        DISCARD_REASON_VIDEO_RESOLUTION_CHANGED,
        DISCARD_REASON_VIDEO_ROTATION_CHANGED,
        DISCARD_REASON_VIDEO_COLOR_INFO_CHANGED,
        DISCARD_REASON_AUDIO_CHANNEL_COUNT_CHANGED,
        DISCARD_REASON_AUDIO_SAMPLE_RATE_CHANGED,
        DISCARD_REASON_AUDIO_ENCODING_CHANGED
      })
  public @interface DecoderDiscardReasons {}

  /** Decoder reuse is not implemented. */
  public static final int DISCARD_REASON_REUSE_NOT_IMPLEMENTED = 1 << 0;
  /** Decoder reuse is disabled by a workaround. */
  public static final int DISCARD_REASON_WORKAROUND = 1 << 1;
  /** Decoder reuse is disabled by overriding behavior in application code. */
  public static final int DISCARD_REASON_APP_OVERRIDE = 1 << 2;
  /** The sample MIME type is changing. */
  public static final int DISCARD_REASON_MIME_TYPE_CHANGED = 1 << 3;
  /** The codec's operating rate is changing. */
  public static final int DISCARD_REASON_OPERATING_RATE_CHANGED = 1 << 4;
  /** The format initialization data is changing. */
  public static final int DISCARD_REASON_INITIALIZATION_DATA_CHANGED = 1 << 5;
  /** The new format may exceed the decoder's configured maximum sample size, in bytes. */
  public static final int DISCARD_REASON_MAX_INPUT_SIZE_EXCEEDED = 1 << 6;
  /** The DRM session is changing. */
  public static final int DISCARD_REASON_DRM_SESSION_CHANGED = 1 << 7;
  /** The new format may exceed the decoder's configured maximum resolution. */
  public static final int DISCARD_REASON_VIDEO_MAX_RESOLUTION_EXCEEDED = 1 << 8;
  /** The video resolution is changing. */
  public static final int DISCARD_REASON_VIDEO_RESOLUTION_CHANGED = 1 << 9;
  /** The video rotation is changing. */
  public static final int DISCARD_REASON_VIDEO_ROTATION_CHANGED = 1 << 10;
  /** The video {@link ColorInfo} is changing. */
  public static final int DISCARD_REASON_VIDEO_COLOR_INFO_CHANGED = 1 << 11;
  /** The audio channel count is changing. */
  public static final int DISCARD_REASON_AUDIO_CHANNEL_COUNT_CHANGED = 1 << 12;
  /** The audio sample rate is changing. */
  public static final int DISCARD_REASON_AUDIO_SAMPLE_RATE_CHANGED = 1 << 13;
  /** The audio encoding is changing. */
  public static final int DISCARD_REASON_AUDIO_ENCODING_CHANGED = 1 << 14;

  /** The name of the decoder. */
  public final String decoderName;

  /** The {@link Format} for which the decoder was previously configured. */
  public final Format oldFormat;

  /** The new {@link Format} being evaluated. */
  public final Format newFormat;

  /** The {@link DecoderReuseResult result} of the evaluation. */
  public final @DecoderReuseResult int result;

  /**
   * {@link DecoderDiscardReasons Reasons} why the decoder cannot be reused. Always {@code 0} if
   * reuse is possible. May also be {code 0} if reuse is not possible for an unspecified reason.
   */
  public final @DecoderDiscardReasons int discardReasons;

  /**
   * @param decoderName The name of the decoder.
   * @param oldFormat The {@link Format} for which the decoder was previously configured.
   * @param newFormat The new {@link Format} being evaluated.
   * @param result The {@link DecoderReuseResult result} of the evaluation.
   * @param discardReasons One or more {@link DecoderDiscardReasons reasons} why the decoder cannot
   *     be reused, or {@code 0} if reuse is possible.
   */
  public DecoderReuseEvaluation(
      String decoderName,
      Format oldFormat,
      Format newFormat,
      @DecoderReuseResult int result,
      @DecoderDiscardReasons int discardReasons) {
    checkArgument(result == REUSE_RESULT_NO || discardReasons == 0);
    this.decoderName = checkNotEmpty(decoderName);
    this.oldFormat = checkNotNull(oldFormat);
    this.newFormat = checkNotNull(newFormat);
    this.result = result;
    this.discardReasons = discardReasons;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    DecoderReuseEvaluation other = (DecoderReuseEvaluation) obj;
    return result == other.result
        && discardReasons == other.discardReasons
        && decoderName.equals(other.decoderName)
        && oldFormat.equals(other.oldFormat)
        && newFormat.equals(other.newFormat);
  }

  @Override
  public int hashCode() {
    int hashCode = 17;
    hashCode = 31 * hashCode + result;
    hashCode = 31 * hashCode + discardReasons;
    hashCode = 31 * hashCode + decoderName.hashCode();
    hashCode = 31 * hashCode + oldFormat.hashCode();
    hashCode = 31 * hashCode + newFormat.hashCode();
    return hashCode;
  }
}

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
package org.telegram.messenger.exoplayer;

import android.annotation.TargetApi;
import android.media.MediaCodecInfo.CodecCapabilities;
import org.telegram.messenger.exoplayer.util.Util;

/**
 * Contains information about a media decoder.
 */
@TargetApi(16)
public final class DecoderInfo {

  /**
   * The name of the decoder.
   * <p>
   * May be passed to {@link android.media.MediaCodec#createByCodecName(String)} to create an
   * instance of the decoder.
   */
  public final String name;

  /**
   * {@link CodecCapabilities} for this decoder.
   */
  public final CodecCapabilities capabilities;

  /**
   * Whether the decoder supports seamless resolution switches.
   *
   * @see android.media.MediaCodecInfo.CodecCapabilities#isFeatureSupported(String)
   * @see android.media.MediaCodecInfo.CodecCapabilities#FEATURE_AdaptivePlayback
   */
  public final boolean adaptive;

  /**
   * @param name The name of the decoder.
   * @param capabilities {@link CodecCapabilities} of the decoder.
   */
  /* package */ DecoderInfo(String name, CodecCapabilities capabilities) {
    this.name = name;
    this.capabilities = capabilities;
    this.adaptive = isAdaptive(capabilities);
  }

  private static boolean isAdaptive(CodecCapabilities capabilities) {
    return capabilities != null && Util.SDK_INT >= 19 && isAdaptiveV19(capabilities);
  }

  @TargetApi(19)
  private static boolean isAdaptiveV19(CodecCapabilities capabilities) {
    return capabilities.isFeatureSupported(CodecCapabilities.FEATURE_AdaptivePlayback);
  }

}

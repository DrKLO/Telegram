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
package com.google.android.exoplayer2.drm;

import android.annotation.TargetApi;
import android.media.MediaCrypto;
import com.google.android.exoplayer2.util.Assertions;

/**
 * An {@link ExoMediaCrypto} implementation that wraps the framework {@link MediaCrypto}.
 */
@TargetApi(16)
public final class FrameworkMediaCrypto implements ExoMediaCrypto {

  private final MediaCrypto mediaCrypto;
  private final boolean forceAllowInsecureDecoderComponents;

  /**
   * @param mediaCrypto The {@link MediaCrypto} to wrap.
   */
  public FrameworkMediaCrypto(MediaCrypto mediaCrypto) {
    this(mediaCrypto, false);
  }

  /**
   * @param mediaCrypto The {@link MediaCrypto} to wrap.
   * @param forceAllowInsecureDecoderComponents Whether to force
   *     {@link #requiresSecureDecoderComponent(String)} to return {@code false}, rather than
   *     {@link MediaCrypto#requiresSecureDecoderComponent(String)} of the wrapped
   *     {@link MediaCrypto}.
   */
  public FrameworkMediaCrypto(MediaCrypto mediaCrypto,
      boolean forceAllowInsecureDecoderComponents) {
    this.mediaCrypto = Assertions.checkNotNull(mediaCrypto);
    this.forceAllowInsecureDecoderComponents = forceAllowInsecureDecoderComponents;
  }

  /**
   * Returns the wrapped {@link MediaCrypto}.
   */
  public MediaCrypto getWrappedMediaCrypto() {
    return mediaCrypto;
  }

  @Override
  public boolean requiresSecureDecoderComponent(String mimeType) {
    return !forceAllowInsecureDecoderComponents
        && mediaCrypto.requiresSecureDecoderComponent(mimeType);
  }

}

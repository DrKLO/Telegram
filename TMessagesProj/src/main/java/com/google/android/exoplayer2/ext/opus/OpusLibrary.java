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
package com.google.android.exoplayer2.ext.opus;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.util.LibraryLoader;
import com.google.android.exoplayer2.util.Util;

/**
 * Configures and queries the underlying native library.
 */
public final class OpusLibrary {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.opus");
  }

  @Nullable private static Class<? extends ExoMediaCrypto> exoMediaCryptoType;

  private OpusLibrary() {}

  /**
   * Override the names of the Opus native libraries. If an application wishes to call this method,
   * it must do so before calling any other method defined by this class, and before instantiating a
   * {@link LibopusAudioRenderer} instance.
   *
   * @param exoMediaCryptoType The {@link ExoMediaCrypto} type expected for decoding protected
   *     content.
   * @param libraries The names of the Opus native libraries.
   */
  public static void setLibraries(
      Class<? extends ExoMediaCrypto> exoMediaCryptoType, String... libraries) {
    OpusLibrary.exoMediaCryptoType = exoMediaCryptoType;
  }

  /** Returns the version of the underlying library if available, or null otherwise. */
  @Nullable
  public static String getVersion() {
    return opusGetVersion();
  }

  /**
   * Returns whether the given {@link ExoMediaCrypto} type matches the one required for decoding
   * protected content.
   */
  public static boolean matchesExpectedExoMediaCryptoType(
      @Nullable Class<? extends ExoMediaCrypto> exoMediaCryptoType) {
    return Util.areEqual(OpusLibrary.exoMediaCryptoType, exoMediaCryptoType);
  }

  public static native String opusGetVersion();
  public static native boolean opusIsSecureDecodeSupported();
}

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
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.util.LibraryLoader;

import org.telegram.messenger.NativeLoader;

/** Configures and queries the underlying native library. */
public final class OpusLibrary {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.opus");
  }

  private static @C.CryptoType int cryptoType = C.CRYPTO_TYPE_UNSUPPORTED;

  private OpusLibrary() {}

  /**
   * Override the names of the Opus native libraries. If an application wishes to call this method,
   * it must do so before calling any other method defined by this class, and before instantiating a
   * {@link LibopusAudioRenderer} instance.
   *
   * @param cryptoType The {@link C.CryptoType} for which the decoder library supports decrypting
   *     protected content, or {@link C#CRYPTO_TYPE_UNSUPPORTED} if the library does not support
   *     decryption.
   * @param libraries The names of the Opus native libraries.
   */
  public static void setLibraries(@C.CryptoType int cryptoType, String... libraries) {
    OpusLibrary.cryptoType = cryptoType;
  }

  /** Returns whether the underlying library is available, loading it if necessary. */
  public static boolean isAvailable() {
    return NativeLoader.loaded();
  }

  /** Returns the version of the underlying library if available, or null otherwise. */
  @Nullable
  public static String getVersion() {
    return isAvailable() ? opusGetVersion() : null;
  }

  /** Returns whether the library supports the given {@link C.CryptoType}. */
  public static boolean supportsCryptoType(@C.CryptoType int cryptoType) {
    return cryptoType == C.CRYPTO_TYPE_NONE
        || (cryptoType != C.CRYPTO_TYPE_UNSUPPORTED && cryptoType == OpusLibrary.cryptoType);
  }

  public static native String opusGetVersion();

  public static native boolean opusIsSecureDecodeSupported();
}

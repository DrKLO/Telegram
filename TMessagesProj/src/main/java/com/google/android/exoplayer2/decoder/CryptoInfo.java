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
package com.google.android.exoplayer2.decoder;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

/**
 * Metadata describing the structure of an encrypted input sample.
 *
 * <p>This class is a compatibility wrapper for {@link android.media.MediaCodec.CryptoInfo}.
 */
public final class CryptoInfo {

  /**
   * The 16 byte initialization vector. If the initialization vector of the content is shorter than
   * 16 bytes, 0 byte padding is appended to extend the vector to the required 16 byte length.
   *
   * @see android.media.MediaCodec.CryptoInfo#iv
   */
  @Nullable public byte[] iv;
  /**
   * The 16 byte key id.
   *
   * @see android.media.MediaCodec.CryptoInfo#key
   */
  @Nullable public byte[] key;
  /**
   * The type of encryption that has been applied. Must be one of the {@link C.CryptoMode} values.
   *
   * @see android.media.MediaCodec.CryptoInfo#mode
   */
  public @C.CryptoMode int mode;
  /**
   * The number of leading unencrypted bytes in each sub-sample. If null, all bytes are treated as
   * encrypted and {@link #numBytesOfEncryptedData} must be specified.
   *
   * @see android.media.MediaCodec.CryptoInfo#numBytesOfClearData
   */
  @Nullable public int[] numBytesOfClearData;
  /**
   * The number of trailing encrypted bytes in each sub-sample. If null, all bytes are treated as
   * clear and {@link #numBytesOfClearData} must be specified.
   *
   * @see android.media.MediaCodec.CryptoInfo#numBytesOfEncryptedData
   */
  @Nullable public int[] numBytesOfEncryptedData;
  /**
   * The number of subSamples that make up the buffer's contents.
   *
   * @see android.media.MediaCodec.CryptoInfo#numSubSamples
   */
  public int numSubSamples;
  /**
   * @see android.media.MediaCodec.CryptoInfo.Pattern
   */
  public int encryptedBlocks;
  /**
   * @see android.media.MediaCodec.CryptoInfo.Pattern
   */
  public int clearBlocks;

  private final android.media.MediaCodec.CryptoInfo frameworkCryptoInfo;
  @Nullable private final PatternHolderV24 patternHolder;

  public CryptoInfo() {
    frameworkCryptoInfo = new android.media.MediaCodec.CryptoInfo();
    patternHolder = Util.SDK_INT >= 24 ? new PatternHolderV24(frameworkCryptoInfo) : null;
  }

  /**
   * @see android.media.MediaCodec.CryptoInfo#set(int, int[], int[], byte[], byte[], int)
   */
  public void set(
      int numSubSamples,
      int[] numBytesOfClearData,
      int[] numBytesOfEncryptedData,
      byte[] key,
      byte[] iv,
      @C.CryptoMode int mode,
      int encryptedBlocks,
      int clearBlocks) {
    this.numSubSamples = numSubSamples;
    this.numBytesOfClearData = numBytesOfClearData;
    this.numBytesOfEncryptedData = numBytesOfEncryptedData;
    this.key = key;
    this.iv = iv;
    this.mode = mode;
    this.encryptedBlocks = encryptedBlocks;
    this.clearBlocks = clearBlocks;
    // Update frameworkCryptoInfo fields directly because CryptoInfo.set performs an unnecessary
    // object allocation on Android N.
    frameworkCryptoInfo.numSubSamples = numSubSamples;
    frameworkCryptoInfo.numBytesOfClearData = numBytesOfClearData;
    frameworkCryptoInfo.numBytesOfEncryptedData = numBytesOfEncryptedData;
    frameworkCryptoInfo.key = key;
    frameworkCryptoInfo.iv = iv;
    frameworkCryptoInfo.mode = mode;
    if (Util.SDK_INT >= 24) {
      Assertions.checkNotNull(patternHolder).set(encryptedBlocks, clearBlocks);
    }
  }

  /**
   * Returns an equivalent {@link android.media.MediaCodec.CryptoInfo} instance.
   *
   * <p>Successive calls to this method on a single {@link CryptoInfo} will return the same
   * instance. Changes to the {@link CryptoInfo} will be reflected in the returned object. The
   * return object should not be modified directly.
   *
   * @return The equivalent {@link android.media.MediaCodec.CryptoInfo} instance.
   */
  public android.media.MediaCodec.CryptoInfo getFrameworkCryptoInfo() {
    return frameworkCryptoInfo;
  }

  /**
   * Increases the number of clear data for the first sub sample by {@code count}.
   *
   * <p>If {@code count} is 0, this method is a no-op. Otherwise, it adds {@code count} to {@link
   * #numBytesOfClearData}[0].
   *
   * <p>If {@link #numBytesOfClearData} is null (which is permitted), this method will instantiate
   * it to a new {@code int[1]}.
   *
   * @param count The number of bytes to be added to the first subSample of {@link
   *     #numBytesOfClearData}.
   */
  public void increaseClearDataFirstSubSampleBy(int count) {
    if (count == 0) {
      return;
    }
    if (numBytesOfClearData == null) {
      numBytesOfClearData = new int[1];
      frameworkCryptoInfo.numBytesOfClearData = numBytesOfClearData;
    }
    numBytesOfClearData[0] += count;
  }

  @RequiresApi(24)
  private static final class PatternHolderV24 {

    private final android.media.MediaCodec.CryptoInfo frameworkCryptoInfo;
    private final android.media.MediaCodec.CryptoInfo.Pattern pattern;

    private PatternHolderV24(android.media.MediaCodec.CryptoInfo frameworkCryptoInfo) {
      this.frameworkCryptoInfo = frameworkCryptoInfo;
      pattern = new android.media.MediaCodec.CryptoInfo.Pattern(0, 0);
    }

    private void set(int encryptedBlocks, int clearBlocks) {
      pattern.set(encryptedBlocks, clearBlocks);
      frameworkCryptoInfo.setPattern(pattern);
    }
  }
}

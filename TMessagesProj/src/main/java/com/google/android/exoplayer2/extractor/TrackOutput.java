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
package com.google.android.exoplayer2.extractor;

import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.upstream.DataReader;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.EOFException;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;

/** Receives track level data extracted by an {@link Extractor}. */
public interface TrackOutput {

  /** Holds data required to decrypt a sample. */
  final class CryptoData {

    /** The encryption mode used for the sample. */
    public final @C.CryptoMode int cryptoMode;

    /** The encryption key associated with the sample. Its contents must not be modified. */
    public final byte[] encryptionKey;

    /**
     * The number of encrypted blocks in the encryption pattern, 0 if pattern encryption does not
     * apply.
     */
    public final int encryptedBlocks;

    /**
     * The number of clear blocks in the encryption pattern, 0 if pattern encryption does not apply.
     */
    public final int clearBlocks;

    /**
     * @param cryptoMode See {@link #cryptoMode}.
     * @param encryptionKey See {@link #encryptionKey}.
     * @param encryptedBlocks See {@link #encryptedBlocks}.
     * @param clearBlocks See {@link #clearBlocks}.
     */
    public CryptoData(
        @C.CryptoMode int cryptoMode, byte[] encryptionKey, int encryptedBlocks, int clearBlocks) {
      this.cryptoMode = cryptoMode;
      this.encryptionKey = encryptionKey;
      this.encryptedBlocks = encryptedBlocks;
      this.clearBlocks = clearBlocks;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      CryptoData other = (CryptoData) obj;
      return cryptoMode == other.cryptoMode
          && encryptedBlocks == other.encryptedBlocks
          && clearBlocks == other.clearBlocks
          && Arrays.equals(encryptionKey, other.encryptionKey);
    }

    @Override
    public int hashCode() {
      int result = cryptoMode;
      result = 31 * result + Arrays.hashCode(encryptionKey);
      result = 31 * result + encryptedBlocks;
      result = 31 * result + clearBlocks;
      return result;
    }
  }

  /** Defines the part of the sample data to which a call to {@link #sampleData} corresponds. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({SAMPLE_DATA_PART_MAIN, SAMPLE_DATA_PART_ENCRYPTION, SAMPLE_DATA_PART_SUPPLEMENTAL})
  @interface SampleDataPart {}

  /** Main media sample data. */
  int SAMPLE_DATA_PART_MAIN = 0;
  /**
   * Sample encryption data.
   *
   * <p>The format for encryption information is:
   *
   * <ul>
   *   <li>(1 byte) {@code encryption_signal_byte}: Most significant bit signals whether the
   *       encryption data contains subsample encryption data. The remaining bits contain {@code
   *       initialization_vector_size}.
   *   <li>({@code initialization_vector_size} bytes) Initialization vector.
   *   <li>If subsample encryption data is present, as per {@code encryption_signal_byte}, the
   *       encryption data also contains:
   *       <ul>
   *         <li>(2 bytes) {@code subsample_encryption_data_length}.
   *         <li>({@code subsample_encryption_data_length * 6} bytes) Subsample encryption data
   *             (repeated {@code subsample_encryption_data_length} times:
   *             <ul>
   *               <li>(2 bytes) Size of a clear section in sample.
   *               <li>(4 bytes) Size of an encryption section in sample.
   *             </ul>
   *       </ul>
   * </ul>
   */
  int SAMPLE_DATA_PART_ENCRYPTION = 1;
  /**
   * Sample supplemental data.
   *
   * <p>If a sample contains supplemental data, the format of the entire sample data will be:
   *
   * <ul>
   *   <li>If the sample has the {@link C#BUFFER_FLAG_ENCRYPTED} flag set, all encryption
   *       information.
   *   <li>(4 bytes) {@code sample_data_size}: The size of the actual sample data, not including
   *       supplemental data or encryption information.
   *   <li>({@code sample_data_size} bytes): The media sample data.
   *   <li>(remaining bytes) The supplemental data.
   * </ul>
   */
  int SAMPLE_DATA_PART_SUPPLEMENTAL = 2;

  /**
   * Called when the {@link Format} of the track has been extracted from the stream.
   *
   * @param format The extracted {@link Format}.
   */
  void format(Format format);

  /**
   * Equivalent to {@link #sampleData(DataReader, int, boolean, int) sampleData(input, length,
   * allowEndOfInput, SAMPLE_DATA_PART_MAIN)}.
   */
  default int sampleData(DataReader input, int length, boolean allowEndOfInput) throws IOException {
    return sampleData(input, length, allowEndOfInput, SAMPLE_DATA_PART_MAIN);
  }

  /**
   * Equivalent to {@link #sampleData(ParsableByteArray, int, int)} sampleData(data, length,
   * SAMPLE_DATA_PART_MAIN)}.
   */
  default void sampleData(ParsableByteArray data, int length) {
    sampleData(data, length, SAMPLE_DATA_PART_MAIN);
  }

  /**
   * Called to write sample data to the output.
   *
   * @param input A {@link DataReader} from which to read the sample data.
   * @param length The maximum length to read from the input.
   * @param allowEndOfInput True if encountering the end of the input having read no data is
   *     allowed, and should result in {@link C#RESULT_END_OF_INPUT} being returned. False if it
   *     should be considered an error, causing an {@link EOFException} to be thrown.
   * @param sampleDataPart The part of the sample data to which this call corresponds.
   * @return The number of bytes appended.
   * @throws IOException If an error occurred reading from the input.
   */
  int sampleData(
      DataReader input, int length, boolean allowEndOfInput, @SampleDataPart int sampleDataPart)
      throws IOException;

  /**
   * Called to write sample data to the output.
   *
   * @param data A {@link ParsableByteArray} from which to read the sample data.
   * @param length The number of bytes to read, starting from {@code data.getPosition()}.
   * @param sampleDataPart The part of the sample data to which this call corresponds.
   */
  void sampleData(ParsableByteArray data, int length, @SampleDataPart int sampleDataPart);

  /**
   * Called when metadata associated with a sample has been extracted from the stream.
   *
   * <p>The corresponding sample data will have already been passed to the output via calls to
   * {@link #sampleData(DataReader, int, boolean)} or {@link #sampleData(ParsableByteArray, int)}.
   *
   * @param timeUs The media timestamp associated with the sample, in microseconds.
   * @param flags Flags associated with the sample. See {@code C.BUFFER_FLAG_*}.
   * @param size The size of the sample data, in bytes.
   * @param offset The number of bytes that have been passed to {@link #sampleData(DataReader, int,
   *     boolean)} or {@link #sampleData(ParsableByteArray, int)} since the last byte belonging to
   *     the sample whose metadata is being passed.
   * @param cryptoData The encryption data required to decrypt the sample. May be null.
   */
  void sampleMetadata(
      long timeUs, @C.BufferFlags int flags, int size, int offset, @Nullable CryptoData cryptoData);
}

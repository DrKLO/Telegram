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

import static androidx.annotation.VisibleForTesting.PACKAGE_PRIVATE;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.decoder.CryptoConfig;
import com.google.android.exoplayer2.decoder.CryptoException;
import com.google.android.exoplayer2.decoder.CryptoInfo;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.decoder.SimpleDecoder;
import com.google.android.exoplayer2.decoder.SimpleDecoderOutputBuffer;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/** Opus decoder. */
@VisibleForTesting(otherwise = PACKAGE_PRIVATE)
public final class OpusDecoder
    extends SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, OpusDecoderException> {

  /** Opus streams are always 48000 Hz. */
  /* package */ static final int SAMPLE_RATE = 48_000;

  private static final int DEFAULT_SEEK_PRE_ROLL_SAMPLES = 3840;
  private static final int FULL_CODEC_INITIALIZATION_DATA_BUFFER_COUNT = 3;

  private static final int NO_ERROR = 0;
  private static final int DECODE_ERROR = -1;
  private static final int DRM_ERROR = -2;

  public final boolean outputFloat;
  public final int channelCount;

  @Nullable private final CryptoConfig cryptoConfig;
  private final int preSkipSamples;
  private final int seekPreRollSamples;
  private final long nativeDecoderContext;
  private boolean experimentalDiscardPaddingEnabled;

  private int skipSamples;

  /**
   * Creates an Opus decoder.
   *
   * @param numInputBuffers The number of input buffers.
   * @param numOutputBuffers The number of output buffers.
   * @param initialInputBufferSize The initial size of each input buffer.
   * @param initializationData Codec-specific initialization data. The first element must contain an
   *     opus header. Optionally, the list may contain two additional buffers, which must contain
   *     the encoder delay and seek pre roll values in nanoseconds, encoded as longs.
   * @param cryptoConfig The {@link CryptoConfig} object required for decoding encrypted content.
   *     May be null and can be ignored if decoder does not handle encrypted content.
   * @param outputFloat Forces the decoder to output float PCM samples when set
   * @throws OpusDecoderException Thrown if an exception occurs when initializing the decoder.
   */
  public OpusDecoder(
      int numInputBuffers,
      int numOutputBuffers,
      int initialInputBufferSize,
      List<byte[]> initializationData,
      @Nullable CryptoConfig cryptoConfig,
      boolean outputFloat)
      throws OpusDecoderException {
    super(new DecoderInputBuffer[numInputBuffers], new SimpleDecoderOutputBuffer[numOutputBuffers]);
    if (!OpusLibrary.isAvailable()) {
      throw new OpusDecoderException("Failed to load decoder native libraries");
    }
    this.cryptoConfig = cryptoConfig;
    if (cryptoConfig != null && !OpusLibrary.opusIsSecureDecodeSupported()) {
      throw new OpusDecoderException("Opus decoder does not support secure decode");
    }
    int initializationDataSize = initializationData.size();
    if (initializationDataSize != 1 && initializationDataSize != 3) {
      throw new OpusDecoderException("Invalid initialization data size");
    }
    if (initializationDataSize == 3
        && (initializationData.get(1).length != 8 || initializationData.get(2).length != 8)) {
      throw new OpusDecoderException("Invalid pre-skip or seek pre-roll");
    }
    preSkipSamples = getPreSkipSamples(initializationData);
    seekPreRollSamples = getSeekPreRollSamples(initializationData);
    skipSamples = preSkipSamples;

    byte[] headerBytes = initializationData.get(0);
    if (headerBytes.length < 19) {
      throw new OpusDecoderException("Invalid header length");
    }
    channelCount = getChannelCount(headerBytes);
    if (channelCount > 8) {
      throw new OpusDecoderException("Invalid channel count: " + channelCount);
    }
    int gain = readSignedLittleEndian16(headerBytes, 16);

    byte[] streamMap = new byte[8];
    int numStreams;
    int numCoupled;
    if (headerBytes[18] == 0) { // Channel mapping
      // If there is no channel mapping, use the defaults.
      if (channelCount > 2) { // Maximum channel count with default layout.
        throw new OpusDecoderException("Invalid header, missing stream map");
      }
      numStreams = 1;
      numCoupled = (channelCount == 2) ? 1 : 0;
      streamMap[0] = 0;
      streamMap[1] = 1;
    } else {
      if (headerBytes.length < 21 + channelCount) {
        throw new OpusDecoderException("Invalid header length");
      }
      // Read the channel mapping.
      numStreams = headerBytes[19] & 0xFF;
      numCoupled = headerBytes[20] & 0xFF;
      System.arraycopy(headerBytes, 21, streamMap, 0, channelCount);
    }
    nativeDecoderContext =
        opusInit(SAMPLE_RATE, channelCount, numStreams, numCoupled, gain, streamMap);
    if (nativeDecoderContext == 0) {
      throw new OpusDecoderException("Failed to initialize decoder");
    }
    setInitialInputBufferSize(initialInputBufferSize);

    this.outputFloat = outputFloat;
    if (outputFloat) {
      opusSetFloatOutput();
    }
  }

  /**
   * Sets whether discard padding is enabled. When enabled, discard padding samples (provided as
   * supplemental data on the input buffer) will be removed from the end of the decoder output.
   *
   * <p>This method is experimental, and will be renamed or removed in a future release.
   */
  public void experimentalSetDiscardPaddingEnabled(boolean enabled) {
    this.experimentalDiscardPaddingEnabled = enabled;
  }

  @Override
  public String getName() {
    return "libopus" + OpusLibrary.getVersion();
  }

  @Override
  protected DecoderInputBuffer createInputBuffer() {
    return new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);
  }

  @Override
  protected SimpleDecoderOutputBuffer createOutputBuffer() {
    return new SimpleDecoderOutputBuffer(this::releaseOutputBuffer);
  }

  @Override
  protected OpusDecoderException createUnexpectedDecodeException(Throwable error) {
    return new OpusDecoderException("Unexpected decode error", error);
  }

  @Override
  @Nullable
  protected OpusDecoderException decode(
      DecoderInputBuffer inputBuffer, SimpleDecoderOutputBuffer outputBuffer, boolean reset) {
    if (reset) {
      opusReset(nativeDecoderContext);
      // When seeking to 0, skip number of samples as specified in opus header. When seeking to
      // any other time, skip number of samples as specified by seek preroll.
      skipSamples = (inputBuffer.timeUs == 0) ? preSkipSamples : seekPreRollSamples;
    }
    ByteBuffer inputData = Util.castNonNull(inputBuffer.data);
    CryptoInfo cryptoInfo = inputBuffer.cryptoInfo;
    int result =
        inputBuffer.isEncrypted()
            ? opusSecureDecode(
                nativeDecoderContext,
                inputBuffer.timeUs,
                inputData,
                inputData.limit(),
                outputBuffer,
                SAMPLE_RATE,
                cryptoConfig,
                cryptoInfo.mode,
                Assertions.checkNotNull(cryptoInfo.key),
                Assertions.checkNotNull(cryptoInfo.iv),
                cryptoInfo.numSubSamples,
                cryptoInfo.numBytesOfClearData,
                cryptoInfo.numBytesOfEncryptedData)
            : opusDecode(
                nativeDecoderContext,
                inputBuffer.timeUs,
                inputData,
                inputData.limit(),
                outputBuffer);
    if (result < 0) {
      if (result == DRM_ERROR) {
        String message = "Drm error: " + opusGetErrorMessage(nativeDecoderContext);
        CryptoException cause =
            new CryptoException(opusGetErrorCode(nativeDecoderContext), message);
        return new OpusDecoderException(message, cause);
      } else {
        return new OpusDecoderException("Decode error: " + opusGetErrorMessage(result));
      }
    }

    ByteBuffer outputData = Util.castNonNull(outputBuffer.data);
    outputData.position(0);
    outputData.limit(result);
    if (skipSamples > 0) {
      int bytesPerSample = samplesToBytes(1, channelCount, outputFloat);
      int skipBytes = skipSamples * bytesPerSample;
      if (result <= skipBytes) {
        skipSamples -= result / bytesPerSample;
        outputBuffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY);
        outputData.position(result);
      } else {
        skipSamples = 0;
        outputData.position(skipBytes);
      }
    } else if (experimentalDiscardPaddingEnabled && inputBuffer.hasSupplementalData()) {
      int discardPaddingSamples = getDiscardPaddingSamples(inputBuffer.supplementalData);
      if (discardPaddingSamples > 0) {
        int discardBytes = samplesToBytes(discardPaddingSamples, channelCount, outputFloat);
        if (result >= discardBytes) {
          outputData.limit(result - discardBytes);
        }
      }
    }
    return null;
  }

  @Override
  public void release() {
    super.release();
    opusClose(nativeDecoderContext);
  }

  /**
   * Parses the channel count from an Opus Identification Header.
   *
   * @param header An Opus Identification Header, as defined by RFC 7845.
   * @return The parsed channel count.
   */
  @VisibleForTesting
  /* package */ static int getChannelCount(byte[] header) {
    return header[9] & 0xFF;
  }

  /**
   * Returns the number of pre-skip samples specified by the given Opus codec initialization data.
   *
   * @param initializationData The codec initialization data.
   * @return The number of pre-skip samples.
   */
  @VisibleForTesting
  /* package */ static int getPreSkipSamples(List<byte[]> initializationData) {
    if (initializationData.size() == FULL_CODEC_INITIALIZATION_DATA_BUFFER_COUNT) {
      long codecDelayNs =
          ByteBuffer.wrap(initializationData.get(1)).order(ByteOrder.nativeOrder()).getLong();
      return (int) ((codecDelayNs * SAMPLE_RATE) / C.NANOS_PER_SECOND);
    }
    // Fall back to parsing directly from the Opus Identification header.
    byte[] headerData = initializationData.get(0);
    return ((headerData[11] & 0xFF) << 8) | (headerData[10] & 0xFF);
  }

  /**
   * Returns the number of seek per-roll samples specified by the given Opus codec initialization
   * data.
   *
   * @param initializationData The codec initialization data.
   * @return The number of seek pre-roll samples.
   */
  @VisibleForTesting
  /* package */ static int getSeekPreRollSamples(List<byte[]> initializationData) {
    if (initializationData.size() == FULL_CODEC_INITIALIZATION_DATA_BUFFER_COUNT) {
      long seekPreRollNs =
          ByteBuffer.wrap(initializationData.get(2)).order(ByteOrder.nativeOrder()).getLong();
      return (int) ((seekPreRollNs * SAMPLE_RATE) / C.NANOS_PER_SECOND);
    }
    // Fall back to returning the default seek pre-roll.
    return DEFAULT_SEEK_PRE_ROLL_SAMPLES;
  }

  /**
   * Returns the number of discard padding samples specified by the supplemental data attached to an
   * input buffer.
   *
   * @param supplementalData Supplemental data related to the an input buffer.
   * @return The number of discard padding samples to remove from the decoder output.
   */
  @VisibleForTesting
  /* package */ static int getDiscardPaddingSamples(@Nullable ByteBuffer supplementalData) {
    if (supplementalData == null || supplementalData.remaining() != 8) {
      return 0;
    }
    long discardPaddingNs = supplementalData.order(ByteOrder.LITTLE_ENDIAN).getLong();
    if (discardPaddingNs < 0) {
      return 0;
    }
    return (int) ((discardPaddingNs * SAMPLE_RATE) / C.NANOS_PER_SECOND);
  }

  /** Returns number of bytes to represent {@code samples}. */
  private static int samplesToBytes(int samples, int channelCount, boolean outputFloat) {
    int bytesPerChannel = outputFloat ? 4 : 2;
    return samples * channelCount * bytesPerChannel;
  }

  private static int readSignedLittleEndian16(byte[] input, int offset) {
    int value = input[offset] & 0xFF;
    value |= (input[offset + 1] & 0xFF) << 8;
    return (short) value;
  }

  private native long opusInit(
      int sampleRate, int channelCount, int numStreams, int numCoupled, int gain, byte[] streamMap);

  private native int opusDecode(
      long decoder,
      long timeUs,
      ByteBuffer inputBuffer,
      int inputSize,
      SimpleDecoderOutputBuffer outputBuffer);

  private native int opusSecureDecode(
      long decoder,
      long timeUs,
      ByteBuffer inputBuffer,
      int inputSize,
      SimpleDecoderOutputBuffer outputBuffer,
      int sampleRate,
      @Nullable CryptoConfig mediaCrypto,
      int inputMode,
      byte[] key,
      byte[] iv,
      int numSubSamples,
      @Nullable int[] numBytesOfClearData,
      @Nullable int[] numBytesOfEncryptedData);

  private native void opusClose(long decoder);

  private native void opusReset(long decoder);

  private native int opusGetErrorCode(long decoder);

  private native String opusGetErrorMessage(long decoder);

  private native void opusSetFloatOutput();
}

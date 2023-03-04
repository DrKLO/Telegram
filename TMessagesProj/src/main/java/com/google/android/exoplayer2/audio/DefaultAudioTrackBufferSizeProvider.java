/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.audio;

import static com.google.android.exoplayer2.audio.DefaultAudioSink.OUTPUT_MODE_OFFLOAD;
import static com.google.android.exoplayer2.audio.DefaultAudioSink.OUTPUT_MODE_PASSTHROUGH;
import static com.google.android.exoplayer2.audio.DefaultAudioSink.OUTPUT_MODE_PCM;
import static com.google.android.exoplayer2.util.Util.constrainValue;
import static com.google.common.math.IntMath.divide;
import static com.google.common.primitives.Ints.checkedCast;
import static java.lang.Math.max;

import android.media.AudioTrack;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.DefaultAudioSink.OutputMode;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.math.RoundingMode;

/** Provide the buffer size to use when creating an {@link AudioTrack}. */
public class DefaultAudioTrackBufferSizeProvider
    implements DefaultAudioSink.AudioTrackBufferSizeProvider {

  /** Default minimum length for the {@link AudioTrack} buffer, in microseconds. */
  private static final int MIN_PCM_BUFFER_DURATION_US = 250_000;
  /** Default maximum length for the {@link AudioTrack} buffer, in microseconds. */
  private static final int MAX_PCM_BUFFER_DURATION_US = 750_000;
  /** Default multiplication factor to apply to the minimum buffer size requested. */
  private static final int PCM_BUFFER_MULTIPLICATION_FACTOR = 4;
  /** Default length for passthrough {@link AudioTrack} buffers, in microseconds. */
  private static final int PASSTHROUGH_BUFFER_DURATION_US = 250_000;
  /** Default length for offload {@link AudioTrack} buffers, in microseconds. */
  private static final int OFFLOAD_BUFFER_DURATION_US = 50_000_000;
  /**
   * Default multiplication factor to apply to AC3 passthrough buffer to avoid underruns on some
   * devices (e.g., Broadcom 7271).
   */
  private static final int AC3_BUFFER_MULTIPLICATION_FACTOR = 2;

  /** A builder to create {@link DefaultAudioTrackBufferSizeProvider} instances. */
  public static class Builder {

    private int minPcmBufferDurationUs;
    private int maxPcmBufferDurationUs;
    private int pcmBufferMultiplicationFactor;
    private int passthroughBufferDurationUs;
    private int offloadBufferDurationUs;
    private int ac3BufferMultiplicationFactor;

    /** Creates a new builder. */
    public Builder() {
      minPcmBufferDurationUs = MIN_PCM_BUFFER_DURATION_US;
      maxPcmBufferDurationUs = MAX_PCM_BUFFER_DURATION_US;
      pcmBufferMultiplicationFactor = PCM_BUFFER_MULTIPLICATION_FACTOR;
      passthroughBufferDurationUs = PASSTHROUGH_BUFFER_DURATION_US;
      offloadBufferDurationUs = OFFLOAD_BUFFER_DURATION_US;
      ac3BufferMultiplicationFactor = AC3_BUFFER_MULTIPLICATION_FACTOR;
    }

    /**
     * Sets the minimum length for PCM {@link AudioTrack} buffers, in microseconds. Default is
     * {@value #MIN_PCM_BUFFER_DURATION_US}.
     */
    @CanIgnoreReturnValue
    public Builder setMinPcmBufferDurationUs(int minPcmBufferDurationUs) {
      this.minPcmBufferDurationUs = minPcmBufferDurationUs;
      return this;
    }

    /**
     * Sets the maximum length for PCM {@link AudioTrack} buffers, in microseconds. Default is
     * {@value #MAX_PCM_BUFFER_DURATION_US}.
     */
    @CanIgnoreReturnValue
    public Builder setMaxPcmBufferDurationUs(int maxPcmBufferDurationUs) {
      this.maxPcmBufferDurationUs = maxPcmBufferDurationUs;
      return this;
    }

    /**
     * Sets the multiplication factor to apply to the minimum buffer size requested. Default is
     * {@value #PCM_BUFFER_MULTIPLICATION_FACTOR}.
     */
    @CanIgnoreReturnValue
    public Builder setPcmBufferMultiplicationFactor(int pcmBufferMultiplicationFactor) {
      this.pcmBufferMultiplicationFactor = pcmBufferMultiplicationFactor;
      return this;
    }

    /**
     * Sets the length for passthrough {@link AudioTrack} buffers, in microseconds. Default is
     * {@value #PASSTHROUGH_BUFFER_DURATION_US}.
     */
    @CanIgnoreReturnValue
    public Builder setPassthroughBufferDurationUs(int passthroughBufferDurationUs) {
      this.passthroughBufferDurationUs = passthroughBufferDurationUs;
      return this;
    }

    /**
     * The length for offload {@link AudioTrack} buffers, in microseconds. Default is {@value
     * #OFFLOAD_BUFFER_DURATION_US}.
     */
    @CanIgnoreReturnValue
    public Builder setOffloadBufferDurationUs(int offloadBufferDurationUs) {
      this.offloadBufferDurationUs = offloadBufferDurationUs;
      return this;
    }

    /**
     * Sets the multiplication factor to apply to the passthrough buffer for AC3 to avoid underruns
     * on some devices (e.g., Broadcom 7271). Default is {@value #AC3_BUFFER_MULTIPLICATION_FACTOR}.
     */
    @CanIgnoreReturnValue
    public Builder setAc3BufferMultiplicationFactor(int ac3BufferMultiplicationFactor) {
      this.ac3BufferMultiplicationFactor = ac3BufferMultiplicationFactor;
      return this;
    }

    /** Build the {@link DefaultAudioTrackBufferSizeProvider}. */
    public DefaultAudioTrackBufferSizeProvider build() {
      return new DefaultAudioTrackBufferSizeProvider(this);
    }
  }

  /** The minimum length for PCM {@link AudioTrack} buffers, in microseconds. */
  protected final int minPcmBufferDurationUs;
  /** The maximum length for PCM {@link AudioTrack} buffers, in microseconds. */
  protected final int maxPcmBufferDurationUs;
  /** The multiplication factor to apply to the minimum buffer size requested. */
  protected final int pcmBufferMultiplicationFactor;
  /** The length for passthrough {@link AudioTrack} buffers, in microseconds. */
  protected final int passthroughBufferDurationUs;
  /** The length for offload {@link AudioTrack} buffers, in microseconds. */
  protected final int offloadBufferDurationUs;
  /**
   * The multiplication factor to apply to AC3 passthrough buffer to avoid underruns on some devices
   * (e.g., Broadcom 7271).
   */
  public final int ac3BufferMultiplicationFactor;

  protected DefaultAudioTrackBufferSizeProvider(Builder builder) {
    minPcmBufferDurationUs = builder.minPcmBufferDurationUs;
    maxPcmBufferDurationUs = builder.maxPcmBufferDurationUs;
    pcmBufferMultiplicationFactor = builder.pcmBufferMultiplicationFactor;
    passthroughBufferDurationUs = builder.passthroughBufferDurationUs;
    offloadBufferDurationUs = builder.offloadBufferDurationUs;
    ac3BufferMultiplicationFactor = builder.ac3BufferMultiplicationFactor;
  }

  @Override
  public int getBufferSizeInBytes(
      int minBufferSizeInBytes,
      @C.Encoding int encoding,
      @OutputMode int outputMode,
      int pcmFrameSize,
      int sampleRate,
      int bitrate,
      double maxAudioTrackPlaybackSpeed) {
    int bufferSize =
        get1xBufferSizeInBytes(
            minBufferSizeInBytes, encoding, outputMode, pcmFrameSize, sampleRate, bitrate);
    // Maintain the buffer duration by scaling the size accordingly.
    bufferSize = (int) (bufferSize * maxAudioTrackPlaybackSpeed);
    // Buffer size must not be lower than the AudioTrack min buffer size for this format.
    bufferSize = max(minBufferSizeInBytes, bufferSize);
    // Increase if needed to make sure the buffers contains an integer number of frames.
    return (bufferSize + pcmFrameSize - 1) / pcmFrameSize * pcmFrameSize;
  }

  /** Returns the buffer size for playback at 1x speed. */
  protected int get1xBufferSizeInBytes(
      int minBufferSizeInBytes,
      int encoding,
      int outputMode,
      int pcmFrameSize,
      int sampleRate,
      int bitrate) {
    switch (outputMode) {
      case OUTPUT_MODE_PCM:
        return getPcmBufferSizeInBytes(minBufferSizeInBytes, sampleRate, pcmFrameSize);
      case OUTPUT_MODE_PASSTHROUGH:
        return getPassthroughBufferSizeInBytes(encoding, bitrate);
      case OUTPUT_MODE_OFFLOAD:
        return getOffloadBufferSizeInBytes(encoding);
      default:
        throw new IllegalArgumentException();
    }
  }

  /** Returns the buffer size for PCM playback. */
  protected int getPcmBufferSizeInBytes(int minBufferSizeInBytes, int samplingRate, int frameSize) {
    int targetBufferSize = minBufferSizeInBytes * pcmBufferMultiplicationFactor;
    int minAppBufferSize = durationUsToBytes(minPcmBufferDurationUs, samplingRate, frameSize);
    int maxAppBufferSize = durationUsToBytes(maxPcmBufferDurationUs, samplingRate, frameSize);
    return constrainValue(targetBufferSize, minAppBufferSize, maxAppBufferSize);
  }

  /** Returns the buffer size for passthrough playback. */
  protected int getPassthroughBufferSizeInBytes(@C.Encoding int encoding, int bitrate) {
    int bufferSizeUs = passthroughBufferDurationUs;
    if (encoding == C.ENCODING_AC3) {
      bufferSizeUs *= ac3BufferMultiplicationFactor;
    }
    int byteRate =
        bitrate != Format.NO_VALUE
            ? divide(bitrate, 8, RoundingMode.CEILING)
            : getMaximumEncodedRateBytesPerSecond(encoding);
    return checkedCast((long) bufferSizeUs * byteRate / C.MICROS_PER_SECOND);
  }

  /** Returns the buffer size for offload playback. */
  protected int getOffloadBufferSizeInBytes(@C.Encoding int encoding) {
    int maxByteRate = getMaximumEncodedRateBytesPerSecond(encoding);
    return checkedCast((long) offloadBufferDurationUs * maxByteRate / C.MICROS_PER_SECOND);
  }

  protected static int durationUsToBytes(int durationUs, int samplingRate, int frameSize) {
    return checkedCast((long) durationUs * samplingRate * frameSize / C.MICROS_PER_SECOND);
  }

  protected static int getMaximumEncodedRateBytesPerSecond(@C.Encoding int encoding) {
    switch (encoding) {
      case C.ENCODING_MP3:
        return MpegAudioUtil.MAX_RATE_BYTES_PER_SECOND;
      case C.ENCODING_AAC_LC:
        return AacUtil.AAC_LC_MAX_RATE_BYTES_PER_SECOND;
      case C.ENCODING_AAC_HE_V1:
        return AacUtil.AAC_HE_V1_MAX_RATE_BYTES_PER_SECOND;
      case C.ENCODING_AAC_HE_V2:
        return AacUtil.AAC_HE_V2_MAX_RATE_BYTES_PER_SECOND;
      case C.ENCODING_AAC_XHE:
        return AacUtil.AAC_XHE_MAX_RATE_BYTES_PER_SECOND;
      case C.ENCODING_AAC_ELD:
        return AacUtil.AAC_ELD_MAX_RATE_BYTES_PER_SECOND;
      case C.ENCODING_AC3:
        return Ac3Util.AC3_MAX_RATE_BYTES_PER_SECOND;
      case C.ENCODING_E_AC3:
      case C.ENCODING_E_AC3_JOC:
        return Ac3Util.E_AC3_MAX_RATE_BYTES_PER_SECOND;
      case C.ENCODING_AC4:
        return Ac4Util.MAX_RATE_BYTES_PER_SECOND;
      case C.ENCODING_DTS:
        return DtsUtil.DTS_MAX_RATE_BYTES_PER_SECOND;
      case C.ENCODING_DTS_HD:
        return DtsUtil.DTS_HD_MAX_RATE_BYTES_PER_SECOND;
      case C.ENCODING_DOLBY_TRUEHD:
        return Ac3Util.TRUEHD_MAX_RATE_BYTES_PER_SECOND;
      case C.ENCODING_OPUS:
        return OpusUtil.MAX_BYTES_PER_SECOND;
      case C.ENCODING_PCM_16BIT:
      case C.ENCODING_PCM_16BIT_BIG_ENDIAN:
      case C.ENCODING_PCM_24BIT:
      case C.ENCODING_PCM_32BIT:
      case C.ENCODING_PCM_8BIT:
      case C.ENCODING_PCM_FLOAT:
      case C.ENCODING_AAC_ER_BSAC:
      case C.ENCODING_INVALID:
      case Format.NO_VALUE:
      default:
        throw new IllegalArgumentException();
    }
  }
}

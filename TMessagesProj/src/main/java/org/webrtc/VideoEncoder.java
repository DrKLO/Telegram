/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import androidx.annotation.Nullable;
import org.webrtc.EncodedImage;

/**
 * Interface for a video encoder that can be used with WebRTC. All calls will be made on the
 * encoding thread. The encoder may be constructed on a different thread and changing thread after
 * calling release is allowed.
 */
public interface VideoEncoder {
  /** Settings passed to the encoder by WebRTC. */
  public class Settings {
    public final int numberOfCores;
    public final int width;
    public final int height;
    public final int startBitrate; // Kilobits per second.
    public final int maxFramerate;
    public final int numberOfSimulcastStreams;
    public final boolean automaticResizeOn;
    public final Capabilities capabilities;

    // TODO(bugs.webrtc.org/10720): Remove.
    @Deprecated
    public Settings(int numberOfCores, int width, int height, int startBitrate, int maxFramerate,
        int numberOfSimulcastStreams, boolean automaticResizeOn) {
      this(numberOfCores, width, height, startBitrate, maxFramerate, numberOfSimulcastStreams,
          automaticResizeOn, new VideoEncoder.Capabilities(false /* lossNotification */));
    }

    @CalledByNative("Settings")
    public Settings(int numberOfCores, int width, int height, int startBitrate, int maxFramerate,
        int numberOfSimulcastStreams, boolean automaticResizeOn, Capabilities capabilities) {
      this.numberOfCores = numberOfCores;
      this.width = width;
      this.height = height;
      this.startBitrate = startBitrate;
      this.maxFramerate = maxFramerate;
      this.numberOfSimulcastStreams = numberOfSimulcastStreams;
      this.automaticResizeOn = automaticResizeOn;
      this.capabilities = capabilities;
    }
  }

  /** Capabilities (loss notification, etc.) passed to the encoder by WebRTC. */
  public class Capabilities {
    /**
     * The remote side has support for the loss notification RTCP feedback message format, and will
     * be sending these feedback messages if necessary.
     */
    public final boolean lossNotification;

    @CalledByNative("Capabilities")
    public Capabilities(boolean lossNotification) {
      this.lossNotification = lossNotification;
    }
  }

  /** Additional info for encoding. */
  public class EncodeInfo {
    public final EncodedImage.FrameType[] frameTypes;

    @CalledByNative("EncodeInfo")
    public EncodeInfo(EncodedImage.FrameType[] frameTypes) {
      this.frameTypes = frameTypes;
    }
  }

  // TODO(sakal): Add values to these classes as necessary.
  /** Codec specific information about the encoded frame. */
  public class CodecSpecificInfo {}

  public class CodecSpecificInfoVP8 extends CodecSpecificInfo {}

  public class CodecSpecificInfoVP9 extends CodecSpecificInfo {}

  public class CodecSpecificInfoH264 extends CodecSpecificInfo {}

  public class CodecSpecificInfoAV1 extends CodecSpecificInfo {}

  /**
   * Represents bitrate allocated for an encoder to produce frames. Bitrate can be divided between
   * spatial and temporal layers.
   */
  public class BitrateAllocation {
    // First index is the spatial layer and second the temporal layer.
    public final int[][] bitratesBbs;

    /**
     * Initializes the allocation with a two dimensional array of bitrates. The first index of the
     * array is the spatial layer and the second index in the temporal layer.
     */
    @CalledByNative("BitrateAllocation")
    public BitrateAllocation(int[][] bitratesBbs) {
      this.bitratesBbs = bitratesBbs;
    }

    /**
     * Gets the total bitrate allocated for all layers.
     */
    public int getSum() {
      int sum = 0;
      for (int[] spatialLayer : bitratesBbs) {
        for (int bitrate : spatialLayer) {
          sum += bitrate;
        }
      }
      return sum;
    }
  }

  /** Settings for WebRTC quality based scaling. */
  public class ScalingSettings {
    public final boolean on;
    @Nullable public final Integer low;
    @Nullable public final Integer high;

    /**
     * Settings to disable quality based scaling.
     */
    public static final ScalingSettings OFF = new ScalingSettings();

    /**
     * Creates settings to enable quality based scaling.
     *
     * @param low Average QP at which to scale up the resolution.
     * @param high Average QP at which to scale down the resolution.
     */
    public ScalingSettings(int low, int high) {
      this.on = true;
      this.low = low;
      this.high = high;
    }

    private ScalingSettings() {
      this.on = false;
      this.low = null;
      this.high = null;
    }

    // TODO(bugs.webrtc.org/8830): Below constructors are deprecated.
    // Default thresholds are going away, so thresholds have to be set
    // when scaling is on.
    /**
     * Creates quality based scaling setting.
     *
     * @param on True if quality scaling is turned on.
     */
    @Deprecated
    public ScalingSettings(boolean on) {
      this.on = on;
      this.low = null;
      this.high = null;
    }

    /**
     * Creates quality based scaling settings with custom thresholds.
     *
     * @param on True if quality scaling is turned on.
     * @param low Average QP at which to scale up the resolution.
     * @param high Average QP at which to scale down the resolution.
     */
    @Deprecated
    public ScalingSettings(boolean on, int low, int high) {
      this.on = on;
      this.low = low;
      this.high = high;
    }

    @Override
    public String toString() {
      return on ? "[ " + low + ", " + high + " ]" : "OFF";
    }
  }

  /**
   * Bitrate limits for resolution.
   */
  public class ResolutionBitrateLimits {
    /**
     * Maximum size of video frame, in pixels, the bitrate limits are intended for.
     */
    public final int frameSizePixels;

    /**
     * Recommended minimum bitrate to start encoding.
     */
    public final int minStartBitrateBps;

    /**
     * Recommended minimum bitrate.
     */
    public final int minBitrateBps;

    /**
     * Recommended maximum bitrate.
     */
    public final int maxBitrateBps;

    public ResolutionBitrateLimits(
        int frameSizePixels, int minStartBitrateBps, int minBitrateBps, int maxBitrateBps) {
      this.frameSizePixels = frameSizePixels;
      this.minStartBitrateBps = minStartBitrateBps;
      this.minBitrateBps = minBitrateBps;
      this.maxBitrateBps = maxBitrateBps;
    }

    @CalledByNative("ResolutionBitrateLimits")
    public int getFrameSizePixels() {
      return frameSizePixels;
    }

    @CalledByNative("ResolutionBitrateLimits")
    public int getMinStartBitrateBps() {
      return minStartBitrateBps;
    }

    @CalledByNative("ResolutionBitrateLimits")
    public int getMinBitrateBps() {
      return minBitrateBps;
    }

    @CalledByNative("ResolutionBitrateLimits")
    public int getMaxBitrateBps() {
      return maxBitrateBps;
    }
  }

  /** Rate control parameters. */
  public class RateControlParameters {
    /**
     * Adjusted target bitrate, per spatial/temporal layer. May be lower or higher than the target
     * depending on encoder behaviour.
     */
    public final BitrateAllocation bitrate;

    /**
     * Target framerate, in fps. A value <= 0.0 is invalid and should be interpreted as framerate
     * target not available. In this case the encoder should fall back to the max framerate
     * specified in `codec_settings` of the last InitEncode() call.
     */
    public final double framerateFps;

    @CalledByNative("RateControlParameters")
    public RateControlParameters(BitrateAllocation bitrate, double framerateFps) {
      this.bitrate = bitrate;
      this.framerateFps = framerateFps;
    }
  }

  /**
   * Metadata about the Encoder.
   */
  public class EncoderInfo {
    /**
     * The width and height of the incoming video frames should be divisible by
     * |requested_resolution_alignment|
     */
    public final int requestedResolutionAlignment;

    /**
     * Same as above but if true, each simulcast layer should also be divisible by
     * |requested_resolution_alignment|.
     */
    public final boolean applyAlignmentToAllSimulcastLayers;

    public EncoderInfo(
        int requestedResolutionAlignment, boolean applyAlignmentToAllSimulcastLayers) {
      this.requestedResolutionAlignment = requestedResolutionAlignment;
      this.applyAlignmentToAllSimulcastLayers = applyAlignmentToAllSimulcastLayers;
    }

    @CalledByNative("EncoderInfo")
    public int getRequestedResolutionAlignment() {
      return requestedResolutionAlignment;
    }

    @CalledByNative("EncoderInfo")
    public boolean getApplyAlignmentToAllSimulcastLayers() {
      return applyAlignmentToAllSimulcastLayers;
    }
  }

  public interface Callback {
    /**
     * Old encoders assume that the byte buffer held by `frame` is not accessed after the call to
     * this method returns. If the pipeline downstream needs to hold on to the buffer, it then has
     * to make its own copy. We want to move to a model where no copying is needed, and instead use
     * retain()/release() to signal to the encoder when it is safe to reuse the buffer.
     *
     * Over the transition, implementations of this class should use the maybeRetain() method if
     * they want to keep a reference to the buffer, and fall back to copying if that method returns
     * false.
     */
    void onEncodedFrame(EncodedImage frame, CodecSpecificInfo info);
  }

  /**
   * The encoder implementation backing this interface is either 1) a Java
   * encoder (e.g., an Android platform encoder), or alternatively 2) a native
   * encoder (e.g., a software encoder or a C++ encoder adapter).
   *
   * For case 1), createNativeVideoEncoder() should return zero.
   * In this case, we expect the native library to call the encoder through
   * JNI using the Java interface declared below.
   *
   * For case 2), createNativeVideoEncoder() should return a non-zero value.
   * In this case, we expect the native library to treat the returned value as
   * a raw pointer of type webrtc::VideoEncoder* (ownership is transferred to
   * the caller). The native library should then directly call the
   * webrtc::VideoEncoder interface without going through JNI. All calls to
   * the Java interface methods declared below should thus throw an
   * UnsupportedOperationException.
   */
  @CalledByNative
  default long createNativeVideoEncoder() {
    return 0;
  }

  /**
   * Returns true if the encoder is backed by hardware.
   */
  @CalledByNative
  default boolean isHardwareEncoder() {
    return true;
  }

  /**
   * Initializes the encoding process. Call before any calls to encode.
   */
  @CalledByNative VideoCodecStatus initEncode(Settings settings, Callback encodeCallback);

  /**
   * Releases the encoder. No more calls to encode will be made after this call.
   */
  @CalledByNative VideoCodecStatus release();

  /**
   * Requests the encoder to encode a frame.
   */
  @CalledByNative VideoCodecStatus encode(VideoFrame frame, EncodeInfo info);

  /** Sets the bitrate allocation and the target framerate for the encoder. */
  VideoCodecStatus setRateAllocation(BitrateAllocation allocation, int framerate);

  /** Sets the bitrate allocation and the target framerate for the encoder. */
  default @CalledByNative VideoCodecStatus setRates(RateControlParameters rcParameters) {
    // Round frame rate up to avoid overshoots.
    int framerateFps = (int) Math.ceil(rcParameters.framerateFps);
    return setRateAllocation(rcParameters.bitrate, framerateFps);
  }

  /** Any encoder that wants to use WebRTC provided quality scaler must implement this method. */
  @CalledByNative ScalingSettings getScalingSettings();

  /** Returns the list of bitrate limits. */
  @CalledByNative
  default ResolutionBitrateLimits[] getResolutionBitrateLimits() {
    // TODO(ssilkin): Update downstream projects and remove default implementation.
    ResolutionBitrateLimits bitrate_limits[] = {};
    return bitrate_limits;
  }

  /**
   * Should return a descriptive name for the implementation. Gets called once and cached. May be
   * called from arbitrary thread.
   */
  @CalledByNative String getImplementationName();

  @CalledByNative
  default EncoderInfo getEncoderInfo() {
    return new EncoderInfo(
        /* requestedResolutionAlignment= */ 1, /* applyAlignmentToAllSimulcastLayers= */ false);
  }
}

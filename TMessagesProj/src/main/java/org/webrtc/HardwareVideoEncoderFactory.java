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

import static org.webrtc.MediaCodecUtils.EXYNOS_PREFIX;
import static org.webrtc.MediaCodecUtils.EXYNOS_PREFIX_C2;
import static org.webrtc.MediaCodecUtils.HISI_PREFIX;
import static org.webrtc.MediaCodecUtils.INTEL_PREFIX;
import static org.webrtc.MediaCodecUtils.QCOM_PREFIX;

import android.media.MediaCodecInfo;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import org.telegram.messenger.voip.Instance;
import org.telegram.messenger.voip.VoIPService;

import java.util.ArrayList;
import java.util.List;

/** Factory for android hardware video encoders. */
@SuppressWarnings("deprecation") // API 16 requires the use of deprecated methods.
public class HardwareVideoEncoderFactory implements VideoEncoderFactory {
  private static final String TAG = "HardwareVideoEncoderFactory";

  // Forced key frame interval - used to reduce color distortions on Qualcomm platforms.
  private static final int QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_L_MS = 15000;
  private static final int QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_M_MS = 20000;
  private static final int QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_N_MS = 15000;

  @Nullable private final EglBase14.Context sharedContext;
  private final boolean enableIntelVp8Encoder;
  private final boolean enableH264HighProfile;
  @Nullable private final Predicate<MediaCodecInfo> codecAllowedPredicate;

  /**
   * Creates a HardwareVideoEncoderFactory that supports surface texture encoding.
   *
   * @param sharedContext The textures generated will be accessible from this context. May be null,
   *                      this disables texture support.
   * @param enableIntelVp8Encoder true if Intel's VP8 encoder enabled.
   * @param enableH264HighProfile true if H264 High Profile enabled.
   */
  public HardwareVideoEncoderFactory(
      EglBase.Context sharedContext, boolean enableIntelVp8Encoder, boolean enableH264HighProfile) {
    this(sharedContext, enableIntelVp8Encoder, enableH264HighProfile,
        /* codecAllowedPredicate= */ null);
  }

  /**
   * Creates a HardwareVideoEncoderFactory that supports surface texture encoding.
   *
   * @param sharedContext The textures generated will be accessible from this context. May be null,
   *                      this disables texture support.
   * @param enableIntelVp8Encoder true if Intel's VP8 encoder enabled.
   * @param enableH264HighProfile true if H264 High Profile enabled.
   * @param codecAllowedPredicate optional predicate to filter codecs. All codecs are allowed
   *                              when predicate is not provided.
   */
  public HardwareVideoEncoderFactory(EglBase.Context sharedContext, boolean enableIntelVp8Encoder,
      boolean enableH264HighProfile, @Nullable Predicate<MediaCodecInfo> codecAllowedPredicate) {
    // Texture mode requires EglBase14.
    if (sharedContext instanceof EglBase14.Context) {
      this.sharedContext = (EglBase14.Context) sharedContext;
    } else {
      Logging.w(TAG, "No shared EglBase.Context.  Encoders will not use texture mode.");
      this.sharedContext = null;
    }
    this.enableIntelVp8Encoder = enableIntelVp8Encoder;
    this.enableH264HighProfile = enableH264HighProfile;
    this.codecAllowedPredicate = codecAllowedPredicate;
  }

  @Deprecated
  public HardwareVideoEncoderFactory(boolean enableIntelVp8Encoder, boolean enableH264HighProfile) {
    this(null, enableIntelVp8Encoder, enableH264HighProfile);
  }

  @Nullable
  @Override
  public VideoEncoder createEncoder(VideoCodecInfo input) {
    // HW encoding is not supported below Android Kitkat.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
      return null;
    }

    VideoCodecMimeType type = VideoCodecMimeType.valueOf(input.name);
    MediaCodecInfo info = findCodecForType(type);

    if (info == null) {
      return null;
    }

    String codecName = info.getName();
    String mime = type.mimeType();
    Integer surfaceColorFormat = MediaCodecUtils.selectColorFormat(
        MediaCodecUtils.TEXTURE_COLOR_FORMATS, info.getCapabilitiesForType(mime));
    Integer yuvColorFormat = MediaCodecUtils.selectColorFormat(
        MediaCodecUtils.ENCODER_COLOR_FORMATS, info.getCapabilitiesForType(mime));

    if (type == VideoCodecMimeType.H264) {
      boolean isHighProfile = H264Utils.isSameH264Profile(
          input.params, MediaCodecUtils.getCodecProperties(type, /* highProfile= */ true));
      boolean isBaselineProfile = H264Utils.isSameH264Profile(
          input.params, MediaCodecUtils.getCodecProperties(type, /* highProfile= */ false));

      if (!isHighProfile && !isBaselineProfile) {
        return null;
      }
      if (isHighProfile && !isH264HighProfileSupported(info)) {
        return null;
      }
    }

    return new HardwareVideoEncoder(new MediaCodecWrapperFactoryImpl(), codecName, type,
        surfaceColorFormat, yuvColorFormat, input.params, getKeyFrameIntervalSec(type),
        getForcedKeyFrameIntervalMs(type, codecName), createBitrateAdjuster(type, codecName),
        sharedContext);
  }

  @Override
  public VideoCodecInfo[] getSupportedCodecs() {
    // HW encoding is not supported below Android Kitkat.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT || VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().groupCall != null) {
      return new VideoCodecInfo[0];
    }

    List<VideoCodecInfo> supportedCodecInfos = new ArrayList<VideoCodecInfo>();
    // Generate a list of supported codecs in order of preference:
    // VP8, VP9, H.265(optional), H264 (high profile), and H264 (baseline profile).
    for (VideoCodecMimeType type : new VideoCodecMimeType[] {
             VideoCodecMimeType.VP8, VideoCodecMimeType.VP9, VideoCodecMimeType.H264,
			     VideoCodecMimeType.H265}) {
      MediaCodecInfo codec = findCodecForType(type);
      if (codec != null) {
        String name = type.name();
        // TODO(sakal): Always add H264 HP once WebRTC correctly removes codecs that are not
        // supported by the decoder.
        if (type == VideoCodecMimeType.H264 && isH264HighProfileSupported(codec)) {
          supportedCodecInfos.add(new VideoCodecInfo(
              name, MediaCodecUtils.getCodecProperties(type, /* highProfile= */ true)));
        }

        supportedCodecInfos.add(new VideoCodecInfo(
            name, MediaCodecUtils.getCodecProperties(type, /* highProfile= */ false)));
      }
    }

    return supportedCodecInfos.toArray(new VideoCodecInfo[supportedCodecInfos.size()]);
  }

  private @Nullable MediaCodecInfo findCodecForType(VideoCodecMimeType type) {
    ArrayList<MediaCodecInfo> infos = MediaCodecUtils.getSortedCodecsList();
    int count = infos.size();
    for (int i = 0; i < count; ++i) {
      MediaCodecInfo info = infos.get(i);
      if (info == null || !info.isEncoder()) {
        continue;
      }
      if (isSupportedCodec(info, type)) {
        return info;
      }
    }
    return null; // No support for this type.
  }

  // Returns true if the given MediaCodecInfo indicates a supported encoder for the given type.
  private boolean isSupportedCodec(MediaCodecInfo info, VideoCodecMimeType type) {
    if (!MediaCodecUtils.codecSupportsType(info, type)) {
      return false;
    }
    // Check for a supported color format.
    if (MediaCodecUtils.selectColorFormat(
            MediaCodecUtils.ENCODER_COLOR_FORMATS, info.getCapabilitiesForType(type.mimeType()))
        == null) {
      return false;
    }
    return isHardwareSupportedInCurrentSdk(info, type) && isMediaCodecAllowed(info);
  }

  // Returns true if the given MediaCodecInfo indicates a hardware module that is supported on the
  // current SDK.
  private boolean isHardwareSupportedInCurrentSdk(MediaCodecInfo info, VideoCodecMimeType type) {
    if (VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().groupCall != null) {
      return false;
    }
    Instance.ServerConfig config = Instance.getGlobalServerConfig();
    if (!config.enable_h264_encoder && !config.enable_h265_encoder && !config.enable_vp8_encoder && !config.enable_vp9_encoder) {
      return false;
    }
    switch (type) {
      case VP8:
        return isHardwareSupportedInCurrentSdkVp8(info);
      case VP9:
        return isHardwareSupportedInCurrentSdkVp9(info);
      case H264:
        return isHardwareSupportedInCurrentSdkH264(info);
      case H265:
        return isHardwareSupportedInCurrentSdkH265(info);
    }
    return false;
  }

  private boolean isHardwareSupportedInCurrentSdkVp8(MediaCodecInfo info) {
    if (!Instance.getGlobalServerConfig().enable_vp8_encoder) {
      return false;
    }
    String name = info.getName();
    // QCOM Vp8 encoder is supported in KITKAT or later.
    return (name.startsWith(QCOM_PREFIX) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
        // Hisi VP8 encoder seems to be supported. Needs more testing.
        || (name.startsWith(HISI_PREFIX) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
        // Exynos VP8 encoder is supported in M or later.
        || (name.startsWith(EXYNOS_PREFIX) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        // Intel Vp8 encoder is supported in LOLLIPOP or later, with the intel encoder enabled.
        || (name.startsWith(INTEL_PREFIX) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
               && enableIntelVp8Encoder)
        || ((name.startsWith(EXYNOS_PREFIX_C2)  && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M));
  }

  private boolean isHardwareSupportedInCurrentSdkVp9(MediaCodecInfo info) {
    if (!Instance.getGlobalServerConfig().enable_vp9_encoder) {
      return false;
    }
    String name = info.getName();
    return (name.startsWith(QCOM_PREFIX) || name.startsWith(EXYNOS_PREFIX) || name.startsWith(HISI_PREFIX))
        // Both QCOM and Exynos VP9 encoders are supported in N or later.
        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
  }

  private boolean isHardwareSupportedInCurrentSdkH264(MediaCodecInfo info) {
    if (!Instance.getGlobalServerConfig().enable_h264_encoder) {
      return false;
    }
    String name = info.getName();
    // QCOM H264 encoder is supported in KITKAT or later.
    return (name.startsWith(QCOM_PREFIX) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
        // Exynos H264 encoder is supported in LOLLIPOP or later.
        || (name.startsWith(EXYNOS_PREFIX)
               && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
  }

  private boolean isHardwareSupportedInCurrentSdkH265(MediaCodecInfo info) {
    if (!Instance.getGlobalServerConfig().enable_h265_encoder) {
      return false;
    }
    String name = info.getName();
    // QCOM H265 encoder is supported in KITKAT or later.
    return (name.startsWith(QCOM_PREFIX) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
           // Exynos H265 encoder is supported in LOLLIPOP or later.
           || (name.startsWith(EXYNOS_PREFIX)
               && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
           // Hisi VP8 encoder seems to be supported. Needs more testing.
           /*|| (name.startsWith(HISI_PREFIX) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)*/;
  }

  private boolean isMediaCodecAllowed(MediaCodecInfo info) {
    if (codecAllowedPredicate == null) {
      return true;
    }
    return codecAllowedPredicate.test(info);
  }

  private int getKeyFrameIntervalSec(VideoCodecMimeType type) {
    switch (type) {
      case VP8: // Fallthrough intended.
      case VP9:
        return 100;
      case H264:
      case H265:
        return 20;
    }
    throw new IllegalArgumentException("Unsupported VideoCodecMimeType " + type);
  }

  private int getForcedKeyFrameIntervalMs(VideoCodecMimeType type, String codecName) {
    if (type == VideoCodecMimeType.VP8 && codecName.startsWith(QCOM_PREFIX)) {
      if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP
          || Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP_MR1) {
        return QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_L_MS;
      } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
        return QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_M_MS;
      } else if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
        return QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_N_MS;
      }
    }
    // Other codecs don't need key frame forcing.
    return 0;
  }

  private BitrateAdjuster createBitrateAdjuster(VideoCodecMimeType type, String codecName) {
    if (codecName.startsWith(EXYNOS_PREFIX)) {
      if (type == VideoCodecMimeType.VP8) {
        // Exynos VP8 encoders need dynamic bitrate adjustment.
        return new DynamicBitrateAdjuster();
      } else {
        // Exynos VP9 and H264 encoders need framerate-based bitrate adjustment.
        return new FramerateBitrateAdjuster();
      }
    }
    // Other codecs don't need bitrate adjustment.
    return new BaseBitrateAdjuster();
  }

  private boolean isH264HighProfileSupported(MediaCodecInfo info) {
    return enableH264HighProfile && Build.VERSION.SDK_INT > Build.VERSION_CODES.M
        && info.getName().startsWith(EXYNOS_PREFIX);
  }
}

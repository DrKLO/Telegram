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

import android.annotation.TargetApi;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecList;
import android.os.Build;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.voip.VoIPService;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** Container class for static constants and helpers used with MediaCodec. */
// We are forced to use the old API because we want to support API level < 21.
@SuppressWarnings("deprecation")
class MediaCodecUtils {
  private static final String TAG = "MediaCodecUtils";

  // Prefixes for supported hardware encoder/decoder component names.
  static final String EXYNOS_PREFIX = "OMX.Exynos.";
  static final String INTEL_PREFIX = "OMX.Intel.";
  static final String NVIDIA_PREFIX = "OMX.Nvidia.";
  static final String QCOM_PREFIX = "OMX.qcom.";
  static final String HISI_PREFIX = "OMX.hisi.";
  static final String[] SOFTWARE_IMPLEMENTATION_PREFIXES = {
      "OMX.google.", "OMX.SEC.", "c2.android"};

  // NV12 color format supported by QCOM codec, but not declared in MediaCodec -
  // see /hardware/qcom/media/mm-core/inc/OMX_QCOMExtns.h
  static final int COLOR_QCOM_FORMATYVU420PackedSemiPlanar32m4ka = 0x7FA30C01;
  static final int COLOR_QCOM_FORMATYVU420PackedSemiPlanar16m4ka = 0x7FA30C02;
  static final int COLOR_QCOM_FORMATYVU420PackedSemiPlanar64x32Tile2m8ka = 0x7FA30C03;
  static final int COLOR_QCOM_FORMATYUV420PackedSemiPlanar32m = 0x7FA30C04;

  // Color formats supported by hardware decoder - in order of preference.
  static final int[] DECODER_COLOR_FORMATS = new int[] {CodecCapabilities.COLOR_FormatYUV420Planar,
      CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
      CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar,
      MediaCodecUtils.COLOR_QCOM_FORMATYVU420PackedSemiPlanar32m4ka,
      MediaCodecUtils.COLOR_QCOM_FORMATYVU420PackedSemiPlanar16m4ka,
      MediaCodecUtils.COLOR_QCOM_FORMATYVU420PackedSemiPlanar64x32Tile2m8ka,
      MediaCodecUtils.COLOR_QCOM_FORMATYUV420PackedSemiPlanar32m};

  // Color formats supported by hardware encoder - in order of preference.
  static final int[] ENCODER_COLOR_FORMATS = {
      MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
      MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
      MediaCodecInfo.CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar,
      MediaCodecUtils.COLOR_QCOM_FORMATYUV420PackedSemiPlanar32m};

  // Color formats supported by texture mode encoding - in order of preference.
  static final int[] TEXTURE_COLOR_FORMATS = getTextureColorFormats();

  private static int[] getTextureColorFormats() {
    if (Build.VERSION.SDK_INT >= 18) {
      return new int[] {MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface};
    } else {
      return new int[] {};
    }
  }

  public static ArrayList<MediaCodecInfo> getSortedCodecsList() {
    ArrayList<MediaCodecInfo> result = new ArrayList<>();
    try {
      int numberOfCodecs = MediaCodecList.getCodecCount();
      for (int a = 0; a < numberOfCodecs; a++) {
        try {
          result.add(MediaCodecList.getCodecInfoAt(a));
        } catch (IllegalArgumentException e) {
          Logging.e(TAG, "Cannot retrieve codec info", e);
        }
      }
      Collections.sort(result, (o1, o2) -> o1.getName().compareTo(o2.getName()));
    } catch (Exception e) {
      FileLog.e(e);
    }
    return result;
  }

  static @Nullable Integer selectColorFormat(
      int[] supportedColorFormats, CodecCapabilities capabilities) {
    for (int supportedColorFormat : supportedColorFormats) {
      for (int codecColorFormat : capabilities.colorFormats) {
        if (codecColorFormat == supportedColorFormat) {
          return codecColorFormat;
        }
      }
    }
    return null;
  }

  static boolean codecSupportsType(MediaCodecInfo info, VideoCodecMimeType type) {
    for (String mimeType : info.getSupportedTypes()) {
      if (type.mimeType().equals(mimeType)) {
        return true;
      }
    }
    return false;
  }

  static Map<String, String> getCodecProperties(VideoCodecMimeType type, boolean highProfile) {
    switch (type) {
      case VP8:
      case VP9:
      case H265:
      case AV1:
        return new HashMap<String, String>();
      case H264:
        return H264Utils.getDefaultH264Params(highProfile);
      default:
        throw new IllegalArgumentException("Unsupported codec: " + type);
    }
  }

  static boolean isHardwareAccelerated(MediaCodecInfo info) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      return isHardwareAcceleratedQOrHigher(info);
    }
    return !isSoftwareOnly(info);
  }

  @TargetApi(29)
  private static boolean isHardwareAcceleratedQOrHigher(android.media.MediaCodecInfo codecInfo) {
    return codecInfo.isHardwareAccelerated();
  }

  static boolean isSoftwareOnly(android.media.MediaCodecInfo codecInfo) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      return isSoftwareOnlyQOrHigher(codecInfo);
    }
    String name = codecInfo.getName();
    for (String prefix : SOFTWARE_IMPLEMENTATION_PREFIXES) {
      if (name.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  @TargetApi(29)
  private static boolean isSoftwareOnlyQOrHigher(android.media.MediaCodecInfo codecInfo) {
    return codecInfo.isSoftwareOnly();
  }

  private MediaCodecUtils() {
    // This class should not be instantiated.
  }
}

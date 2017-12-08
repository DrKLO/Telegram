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
package org.telegram.messenger.exoplayer2.mediacodec;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecList;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.SparseIntArray;
import org.telegram.messenger.exoplayer2.util.MimeTypes;
import org.telegram.messenger.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A utility class for querying the available codecs.
 */
@TargetApi(16)
@SuppressLint("InlinedApi")
public final class MediaCodecUtil {

  /**
   * Thrown when an error occurs querying the device for its underlying media capabilities.
   * <p>
   * Such failures are not expected in normal operation and are normally temporary (e.g. if the
   * mediaserver process has crashed and is yet to restart).
   */
  public static class DecoderQueryException extends Exception {

    private DecoderQueryException(Throwable cause) {
      super("Failed to query underlying media codecs", cause);
    }

  }

  private static final String TAG = "MediaCodecUtil";
  private static final String GOOGLE_RAW_DECODER_NAME = "OMX.google.raw.decoder";
  private static final String MTK_RAW_DECODER_NAME = "OMX.MTK.AUDIO.DECODER.RAW";
  private static final MediaCodecInfo PASSTHROUGH_DECODER_INFO =
      MediaCodecInfo.newPassthroughInstance(GOOGLE_RAW_DECODER_NAME);
  private static final Pattern PROFILE_PATTERN = Pattern.compile("^\\D?(\\d+)$");

  private static final HashMap<CodecKey, List<MediaCodecInfo>> decoderInfosCache = new HashMap<>();

  // Codecs to constant mappings.
  // AVC.
  private static final SparseIntArray AVC_PROFILE_NUMBER_TO_CONST;
  private static final SparseIntArray AVC_LEVEL_NUMBER_TO_CONST;
  private static final String CODEC_ID_AVC1 = "avc1";
  private static final String CODEC_ID_AVC2 = "avc2";
  // HEVC.
  private static final Map<String, Integer> HEVC_CODEC_STRING_TO_PROFILE_LEVEL;
  private static final String CODEC_ID_HEV1 = "hev1";
  private static final String CODEC_ID_HVC1 = "hvc1";

  // Lazily initialized.
  private static int maxH264DecodableFrameSize = -1;

  private MediaCodecUtil() {}

  /**
   * Optional call to warm the codec cache for a given mime type.
   * <p>
   * Calling this method may speed up subsequent calls to {@link #getDecoderInfo(String, boolean)}
   * and {@link #getDecoderInfos(String, boolean)}.
   *
   * @param mimeType The mime type.
   * @param secure Whether the decoder is required to support secure decryption. Always pass false
   *     unless secure decryption really is required.
   */
  public static void warmDecoderInfoCache(String mimeType, boolean secure) {
    try {
      getDecoderInfos(mimeType, secure);
    } catch (DecoderQueryException e) {
      // Codec warming is best effort, so we can swallow the exception.
      Log.e(TAG, "Codec warming failed", e);
    }
  }

  /**
   * Returns information about a decoder suitable for audio passthrough.
   *
   * @return A {@link MediaCodecInfo} describing the decoder, or null if no suitable decoder
   *     exists.
   */
  public static MediaCodecInfo getPassthroughDecoderInfo() {
    // TODO: Return null if the raw decoder doesn't exist.
    return PASSTHROUGH_DECODER_INFO;
  }

  /**
   * Returns information about the preferred decoder for a given mime type.
   *
   * @param mimeType The mime type.
   * @param secure Whether the decoder is required to support secure decryption. Always pass false
   *     unless secure decryption really is required.
   * @return A {@link MediaCodecInfo} describing the decoder, or null if no suitable decoder
   *     exists.
   * @throws DecoderQueryException If there was an error querying the available decoders.
   */
  public static MediaCodecInfo getDecoderInfo(String mimeType, boolean secure)
      throws DecoderQueryException {
    List<MediaCodecInfo> decoderInfos = getDecoderInfos(mimeType, secure);
    return decoderInfos.isEmpty() ? null : decoderInfos.get(0);
  }

  /**
   * Returns all {@link MediaCodecInfo}s for the given mime type, in the order given by
   * {@link MediaCodecList}.
   *
   * @param mimeType The mime type.
   * @param secure Whether the decoder is required to support secure decryption. Always pass false
   *     unless secure decryption really is required.
   * @return A list of all @{link MediaCodecInfo}s for the given mime type, in the order
   *     given by {@link MediaCodecList}.
   * @throws DecoderQueryException If there was an error querying the available decoders.
   */
  public static synchronized List<MediaCodecInfo> getDecoderInfos(String mimeType,
      boolean secure) throws DecoderQueryException {
    CodecKey key = new CodecKey(mimeType, secure);
    List<MediaCodecInfo> decoderInfos = decoderInfosCache.get(key);
    if (decoderInfos != null) {
      return decoderInfos;
    }
    MediaCodecListCompat mediaCodecList = Util.SDK_INT >= 21
        ? new MediaCodecListCompatV21(secure) : new MediaCodecListCompatV16();
    decoderInfos = getDecoderInfosInternal(key, mediaCodecList);
    if (secure && decoderInfos.isEmpty() && 21 <= Util.SDK_INT && Util.SDK_INT <= 23) {
      // Some devices don't list secure decoders on API level 21 [Internal: b/18678462]. Try the
      // legacy path. We also try this path on API levels 22 and 23 as a defensive measure.
      mediaCodecList = new MediaCodecListCompatV16();
      decoderInfos = getDecoderInfosInternal(key, mediaCodecList);
      if (!decoderInfos.isEmpty()) {
        Log.w(TAG, "MediaCodecList API didn't list secure decoder for: " + mimeType
            + ". Assuming: " + decoderInfos.get(0).name);
      }
    }
    applyWorkarounds(decoderInfos);
    decoderInfos = Collections.unmodifiableList(decoderInfos);
    decoderInfosCache.put(key, decoderInfos);
    return decoderInfos;
  }

  /**
   * Returns the maximum frame size supported by the default H264 decoder.
   *
   * @return The maximum frame size for an H264 stream that can be decoded on the device.
   */
  public static int maxH264DecodableFrameSize() throws DecoderQueryException {
    if (maxH264DecodableFrameSize == -1) {
      int result = 0;
      MediaCodecInfo decoderInfo = getDecoderInfo(MimeTypes.VIDEO_H264, false);
      if (decoderInfo != null) {
        for (CodecProfileLevel profileLevel : decoderInfo.getProfileLevels()) {
          result = Math.max(avcLevelToMaxFrameSize(profileLevel.level), result);
        }
        // We assume support for at least 480p (SDK_INT >= 21) or 360p (SDK_INT < 21), which are
        // the levels mandated by the Android CDD.
        result = Math.max(result, Util.SDK_INT >= 21 ? (720 * 480) : (480 * 360));
      }
      maxH264DecodableFrameSize = result;
    }
    return maxH264DecodableFrameSize;
  }

  /**
   * Returns profile and level (as defined by {@link CodecProfileLevel}) corresponding to the given
   * codec description string (as defined by RFC 6381).
   *
   * @param codec A codec description string, as defined by RFC 6381.
   * @return A pair (profile constant, level constant) if {@code codec} is well-formed and
   *     recognized, or null otherwise
   */
  public static Pair<Integer, Integer> getCodecProfileAndLevel(String codec) {
    if (codec == null) {
      return null;
    }
    String[] parts = codec.split("\\.");
    switch (parts[0]) {
      case CODEC_ID_HEV1:
      case CODEC_ID_HVC1:
        return getHevcProfileAndLevel(codec, parts);
      case CODEC_ID_AVC1:
      case CODEC_ID_AVC2:
        return getAvcProfileAndLevel(codec, parts);
      default:
        return null;
    }
  }

  // Internal methods.

  private static List<MediaCodecInfo> getDecoderInfosInternal(
      CodecKey key, MediaCodecListCompat mediaCodecList) throws DecoderQueryException {
    try {
      List<MediaCodecInfo> decoderInfos = new ArrayList<>();
      String mimeType = key.mimeType;
      int numberOfCodecs = mediaCodecList.getCodecCount();
      boolean secureDecodersExplicit = mediaCodecList.secureDecodersExplicit();
      // Note: MediaCodecList is sorted by the framework such that the best decoders come first.
      for (int i = 0; i < numberOfCodecs; i++) {
        android.media.MediaCodecInfo codecInfo = mediaCodecList.getCodecInfoAt(i);
        String codecName = codecInfo.getName();
        if (isCodecUsableDecoder(codecInfo, codecName, secureDecodersExplicit)) {
          for (String supportedType : codecInfo.getSupportedTypes()) {
            if (supportedType.equalsIgnoreCase(mimeType)) {
              try {
                CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(supportedType);
                boolean secure = mediaCodecList.isSecurePlaybackSupported(mimeType, capabilities);
                boolean forceDisableAdaptive = codecNeedsDisableAdaptationWorkaround(codecName);
                if ((secureDecodersExplicit && key.secure == secure)
                    || (!secureDecodersExplicit && !key.secure)) {
                  decoderInfos.add(MediaCodecInfo.newInstance(codecName, mimeType, capabilities,
                      forceDisableAdaptive, false));
                } else if (!secureDecodersExplicit && secure) {
                  decoderInfos.add(MediaCodecInfo.newInstance(codecName + ".secure", mimeType,
                      capabilities, forceDisableAdaptive, true));
                  // It only makes sense to have one synthesized secure decoder, return immediately.
                  return decoderInfos;
                }
              } catch (Exception e) {
                if (Util.SDK_INT <= 23 && !decoderInfos.isEmpty()) {
                  // Suppress error querying secondary codec capabilities up to API level 23.
                  Log.e(TAG, "Skipping codec " + codecName + " (failed to query capabilities)");
                } else {
                  // Rethrow error querying primary codec capabilities, or secondary codec
                  // capabilities if API level is greater than 23.
                  Log.e(TAG, "Failed to query codec " + codecName + " (" + supportedType + ")");
                  throw e;
                }
              }
            }
          }
        }
      }
      return decoderInfos;
    } catch (Exception e) {
      // If the underlying mediaserver is in a bad state, we may catch an IllegalStateException
      // or an IllegalArgumentException here.
      throw new DecoderQueryException(e);
    }
  }

  /**
   * Returns whether the specified codec is usable for decoding on the current device.
   */
  private static boolean isCodecUsableDecoder(android.media.MediaCodecInfo info, String name,
      boolean secureDecodersExplicit) {
    if (info.isEncoder() || (!secureDecodersExplicit && name.endsWith(".secure"))) {
      return false;
    }

    // Work around broken audio decoders.
    if (Util.SDK_INT < 21
        && ("CIPAACDecoder".equals(name)
            || "CIPMP3Decoder".equals(name)
            || "CIPVorbisDecoder".equals(name)
            || "CIPAMRNBDecoder".equals(name)
            || "AACDecoder".equals(name)
            || "MP3Decoder".equals(name))) {
      return false;
    }

    // Work around https://github.com/google/ExoPlayer/issues/398
    if (Util.SDK_INT < 18 && "OMX.SEC.MP3.Decoder".equals(name)) {
      return false;
    }

    // Work around https://github.com/google/ExoPlayer/issues/1528 and
    // https://github.com/google/ExoPlayer/issues/3171
    if (Util.SDK_INT < 18 && "OMX.MTK.AUDIO.DECODER.AAC".equals(name)
        && ("a70".equals(Util.DEVICE)
            || ("Xiaomi".equals(Util.MANUFACTURER) && Util.DEVICE.startsWith("HM")))) {
      return false;
    }

    // Work around an issue where querying/creating a particular MP3 decoder on some devices on
    // platform API version 16 fails.
    if (Util.SDK_INT == 16
        && "OMX.qcom.audio.decoder.mp3".equals(name)
        && ("dlxu".equals(Util.DEVICE) // HTC Butterfly
            || "protou".equals(Util.DEVICE) // HTC Desire X
            || "ville".equals(Util.DEVICE) // HTC One S
            || "villeplus".equals(Util.DEVICE)
            || "villec2".equals(Util.DEVICE)
            || Util.DEVICE.startsWith("gee") // LGE Optimus G
            || "C6602".equals(Util.DEVICE) // Sony Xperia Z
            || "C6603".equals(Util.DEVICE)
            || "C6606".equals(Util.DEVICE)
            || "C6616".equals(Util.DEVICE)
            || "L36h".equals(Util.DEVICE)
            || "SO-02E".equals(Util.DEVICE))) {
      return false;
    }

    // Work around an issue where large timestamps are not propagated correctly.
    if (Util.SDK_INT == 16
        && "OMX.qcom.audio.decoder.aac".equals(name)
        && ("C1504".equals(Util.DEVICE) // Sony Xperia E
            || "C1505".equals(Util.DEVICE)
            || "C1604".equals(Util.DEVICE) // Sony Xperia E dual
            || "C1605".equals(Util.DEVICE))) {
      return false;
    }

    // Work around https://github.com/google/ExoPlayer/issues/3249.
    if (Util.SDK_INT < 24
        && ("OMX.SEC.aac.dec".equals(name) || "OMX.Exynos.AAC.Decoder".equals(name))
        && Util.MANUFACTURER.equals("samsung")
        && (Util.DEVICE.startsWith("zeroflte") // Galaxy S6
            || Util.DEVICE.startsWith("zerolte") // Galaxy S6 Edge
            || Util.DEVICE.startsWith("zenlte") // Galaxy S6 Edge+
            || Util.DEVICE.equals("SC-05G") // Galaxy S6
            || Util.DEVICE.equals("marinelteatt") // Galaxy S6 Active
            || Util.DEVICE.equals("404SC") // Galaxy S6 Edge
            || Util.DEVICE.equals("SC-04G")
            || Util.DEVICE.equals("SCV31"))) {
      return false;
    }

    // Work around https://github.com/google/ExoPlayer/issues/548.
    // VP8 decoder on Samsung Galaxy S3/S4/S4 Mini/Tab 3/Note 2 does not render video.
    if (Util.SDK_INT <= 19
        && "OMX.SEC.vp8.dec".equals(name) && "samsung".equals(Util.MANUFACTURER)
        && (Util.DEVICE.startsWith("d2") || Util.DEVICE.startsWith("serrano")
            || Util.DEVICE.startsWith("jflte") || Util.DEVICE.startsWith("santos")
            || Util.DEVICE.startsWith("t0"))) {
      return false;
    }

    // VP8 decoder on Samsung Galaxy S4 cannot be queried.
    if (Util.SDK_INT <= 19 && Util.DEVICE.startsWith("jflte")
        && "OMX.qcom.video.decoder.vp8".equals(name)) {
      return false;
    }

    return true;
  }

  /**
   * Modifies a list of {@link MediaCodecInfo}s to apply workarounds where we know better than the
   * platform.
   *
   * @param decoderInfos The list to modify.
   */
  private static void applyWorkarounds(List<MediaCodecInfo> decoderInfos) {
    if (Util.SDK_INT < 26 && decoderInfos.size() > 1
        && MTK_RAW_DECODER_NAME.equals(decoderInfos.get(0).name)) {
      // Prefer the Google raw decoder over the MediaTek one [Internal: b/62337687].
      for (int i = 1; i < decoderInfos.size(); i++) {
        MediaCodecInfo decoderInfo = decoderInfos.get(i);
        if (GOOGLE_RAW_DECODER_NAME.equals(decoderInfo.name)) {
          decoderInfos.remove(i);
          decoderInfos.add(0, decoderInfo);
          break;
        }
      }
    }
  }

  /**
   * Returns whether the decoder is known to fail when adapting, despite advertising itself as an
   * adaptive decoder.
   *
   * @param name The decoder name.
   * @return True if the decoder is known to fail when adapting.
   */
  private static boolean codecNeedsDisableAdaptationWorkaround(String name) {
    return Util.SDK_INT <= 22
        && (Util.MODEL.equals("ODROID-XU3") || Util.MODEL.equals("Nexus 10"))
        && ("OMX.Exynos.AVC.Decoder".equals(name) || "OMX.Exynos.AVC.Decoder.secure".equals(name));
  }

  private static Pair<Integer, Integer> getHevcProfileAndLevel(String codec, String[] parts) {
    if (parts.length < 4) {
      // The codec has fewer parts than required by the HEVC codec string format.
      Log.w(TAG, "Ignoring malformed HEVC codec string: " + codec);
      return null;
    }
    // The profile_space gets ignored.
    Matcher matcher = PROFILE_PATTERN.matcher(parts[1]);
    if (!matcher.matches()) {
      Log.w(TAG, "Ignoring malformed HEVC codec string: " + codec);
      return null;
    }
    String profileString = matcher.group(1);
    int profile;
    if ("1".equals(profileString)) {
      profile = CodecProfileLevel.HEVCProfileMain;
    } else if ("2".equals(profileString)) {
      profile = CodecProfileLevel.HEVCProfileMain10;
    } else {
      Log.w(TAG, "Unknown HEVC profile string: " + profileString);
      return null;
    }
    Integer level = HEVC_CODEC_STRING_TO_PROFILE_LEVEL.get(parts[3]);
    if (level == null) {
      Log.w(TAG, "Unknown HEVC level string: " + matcher.group(1));
      return null;
    }
    return new Pair<>(profile, level);
  }

  private static Pair<Integer, Integer> getAvcProfileAndLevel(String codec, String[] codecsParts) {
    if (codecsParts.length < 2) {
      // The codec has fewer parts than required by the AVC codec string format.
      Log.w(TAG, "Ignoring malformed AVC codec string: " + codec);
      return null;
    }
    Integer profileInteger;
    Integer levelInteger;
    try {
      if (codecsParts[1].length() == 6) {
        // Format: avc1.xxccyy, where xx is profile and yy level, both hexadecimal.
        profileInteger = Integer.parseInt(codecsParts[1].substring(0, 2), 16);
        levelInteger = Integer.parseInt(codecsParts[1].substring(4), 16);
      } else if (codecsParts.length >= 3) {
        // Format: avc1.xx.[y]yy where xx is profile and [y]yy level, both decimal.
        profileInteger = Integer.parseInt(codecsParts[1]);
        levelInteger = Integer.parseInt(codecsParts[2]);
      } else {
        // We don't recognize the format.
        Log.w(TAG, "Ignoring malformed AVC codec string: " + codec);
        return null;
      }
    } catch (NumberFormatException e) {
      Log.w(TAG, "Ignoring malformed AVC codec string: " + codec);
      return null;
    }

    Integer profile = AVC_PROFILE_NUMBER_TO_CONST.get(profileInteger);
    if (profile == null) {
      Log.w(TAG, "Unknown AVC profile: " + profileInteger);
      return null;
    }
    Integer level = AVC_LEVEL_NUMBER_TO_CONST.get(levelInteger);
    if (level == null) {
      Log.w(TAG, "Unknown AVC level: " + levelInteger);
      return null;
    }
    return new Pair<>(profile, level);
  }

  /**
   * Conversion values taken from ISO 14496-10 Table A-1.
   *
   * @param avcLevel one of CodecProfileLevel.AVCLevel* constants.
   * @return maximum frame size that can be decoded by a decoder with the specified avc level
   *     (or {@code -1} if the level is not recognized)
   */
  private static int avcLevelToMaxFrameSize(int avcLevel) {
    switch (avcLevel) {
      case CodecProfileLevel.AVCLevel1: return 99 * 16 * 16;
      case CodecProfileLevel.AVCLevel1b: return 99 * 16 * 16;
      case CodecProfileLevel.AVCLevel12: return 396 * 16 * 16;
      case CodecProfileLevel.AVCLevel13: return 396 * 16 * 16;
      case CodecProfileLevel.AVCLevel2: return 396 * 16 * 16;
      case CodecProfileLevel.AVCLevel21: return 792 * 16 * 16;
      case CodecProfileLevel.AVCLevel22: return 1620 * 16 * 16;
      case CodecProfileLevel.AVCLevel3: return 1620 * 16 * 16;
      case CodecProfileLevel.AVCLevel31: return 3600 * 16 * 16;
      case CodecProfileLevel.AVCLevel32: return 5120 * 16 * 16;
      case CodecProfileLevel.AVCLevel4: return 8192 * 16 * 16;
      case CodecProfileLevel.AVCLevel41: return 8192 * 16 * 16;
      case CodecProfileLevel.AVCLevel42: return 8704 * 16 * 16;
      case CodecProfileLevel.AVCLevel5: return 22080 * 16 * 16;
      case CodecProfileLevel.AVCLevel51: return 36864 * 16 * 16;
      case CodecProfileLevel.AVCLevel52: return 36864 * 16 * 16;
      default: return -1;
    }
  }

  private interface MediaCodecListCompat {

    /**
     * The number of codecs in the list.
     */
    int getCodecCount();

    /**
     * The info at the specified index in the list.
     *
     * @param index The index.
     */
    android.media.MediaCodecInfo getCodecInfoAt(int index);

    /**
     * Returns whether secure decoders are explicitly listed, if present.
     */
    boolean secureDecodersExplicit();

    /**
     * Whether secure playback is supported for the given {@link CodecCapabilities}, which should
     * have been obtained from a {@link android.media.MediaCodecInfo} obtained from this list.
     */
    boolean isSecurePlaybackSupported(String mimeType, CodecCapabilities capabilities);

  }

  @TargetApi(21)
  private static final class MediaCodecListCompatV21 implements MediaCodecListCompat {

    private final int codecKind;

    private android.media.MediaCodecInfo[] mediaCodecInfos;

    public MediaCodecListCompatV21(boolean includeSecure) {
      codecKind = includeSecure ? MediaCodecList.ALL_CODECS : MediaCodecList.REGULAR_CODECS;
    }

    @Override
    public int getCodecCount() {
      ensureMediaCodecInfosInitialized();
      return mediaCodecInfos.length;
    }

    @Override
    public android.media.MediaCodecInfo getCodecInfoAt(int index) {
      ensureMediaCodecInfosInitialized();
      return mediaCodecInfos[index];
    }

    @Override
    public boolean secureDecodersExplicit() {
      return true;
    }

    @Override
    public boolean isSecurePlaybackSupported(String mimeType, CodecCapabilities capabilities) {
      return capabilities.isFeatureSupported(CodecCapabilities.FEATURE_SecurePlayback);
    }

    private void ensureMediaCodecInfosInitialized() {
      if (mediaCodecInfos == null) {
        mediaCodecInfos = new MediaCodecList(codecKind).getCodecInfos();
      }
    }

  }

  @SuppressWarnings("deprecation")
  private static final class MediaCodecListCompatV16 implements MediaCodecListCompat {

    @Override
    public int getCodecCount() {
      return MediaCodecList.getCodecCount();
    }

    @Override
    public android.media.MediaCodecInfo getCodecInfoAt(int index) {
      return MediaCodecList.getCodecInfoAt(index);
    }

    @Override
    public boolean secureDecodersExplicit() {
      return false;
    }

    @Override
    public boolean isSecurePlaybackSupported(String mimeType, CodecCapabilities capabilities) {
      // Secure decoders weren't explicitly listed prior to API level 21. We assume that a secure
      // H264 decoder exists.
      return MimeTypes.VIDEO_H264.equals(mimeType);
    }

  }

  private static final class CodecKey {

    public final String mimeType;
    public final boolean secure;

    public CodecKey(String mimeType, boolean secure) {
      this.mimeType = mimeType;
      this.secure = secure;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((mimeType == null) ? 0 : mimeType.hashCode());
      result = prime * result + (secure ? 1231 : 1237);
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || obj.getClass() != CodecKey.class) {
        return false;
      }
      CodecKey other = (CodecKey) obj;
      return TextUtils.equals(mimeType, other.mimeType) && secure == other.secure;
    }

  }

  static {
    AVC_PROFILE_NUMBER_TO_CONST = new SparseIntArray();
    AVC_PROFILE_NUMBER_TO_CONST.put(66, CodecProfileLevel.AVCProfileBaseline);
    AVC_PROFILE_NUMBER_TO_CONST.put(77, CodecProfileLevel.AVCProfileMain);
    AVC_PROFILE_NUMBER_TO_CONST.put(88, CodecProfileLevel.AVCProfileExtended);
    AVC_PROFILE_NUMBER_TO_CONST.put(100, CodecProfileLevel.AVCProfileHigh);

    AVC_LEVEL_NUMBER_TO_CONST = new SparseIntArray();
    AVC_LEVEL_NUMBER_TO_CONST.put(10, CodecProfileLevel.AVCLevel1);
    // TODO: Find int for CodecProfileLevel.AVCLevel1b.
    AVC_LEVEL_NUMBER_TO_CONST.put(11, CodecProfileLevel.AVCLevel11);
    AVC_LEVEL_NUMBER_TO_CONST.put(12, CodecProfileLevel.AVCLevel12);
    AVC_LEVEL_NUMBER_TO_CONST.put(13, CodecProfileLevel.AVCLevel13);
    AVC_LEVEL_NUMBER_TO_CONST.put(20, CodecProfileLevel.AVCLevel2);
    AVC_LEVEL_NUMBER_TO_CONST.put(21, CodecProfileLevel.AVCLevel21);
    AVC_LEVEL_NUMBER_TO_CONST.put(22, CodecProfileLevel.AVCLevel22);
    AVC_LEVEL_NUMBER_TO_CONST.put(30, CodecProfileLevel.AVCLevel3);
    AVC_LEVEL_NUMBER_TO_CONST.put(31, CodecProfileLevel.AVCLevel31);
    AVC_LEVEL_NUMBER_TO_CONST.put(32, CodecProfileLevel.AVCLevel32);
    AVC_LEVEL_NUMBER_TO_CONST.put(40, CodecProfileLevel.AVCLevel4);
    AVC_LEVEL_NUMBER_TO_CONST.put(41, CodecProfileLevel.AVCLevel41);
    AVC_LEVEL_NUMBER_TO_CONST.put(42, CodecProfileLevel.AVCLevel42);
    AVC_LEVEL_NUMBER_TO_CONST.put(50, CodecProfileLevel.AVCLevel5);
    AVC_LEVEL_NUMBER_TO_CONST.put(51, CodecProfileLevel.AVCLevel51);
    AVC_LEVEL_NUMBER_TO_CONST.put(52, CodecProfileLevel.AVCLevel52);

    HEVC_CODEC_STRING_TO_PROFILE_LEVEL = new HashMap<>();
    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L30", CodecProfileLevel.HEVCMainTierLevel1);
    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L60", CodecProfileLevel.HEVCMainTierLevel2);
    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L63", CodecProfileLevel.HEVCMainTierLevel21);
    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L90", CodecProfileLevel.HEVCMainTierLevel3);
    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L93", CodecProfileLevel.HEVCMainTierLevel31);
    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L120", CodecProfileLevel.HEVCMainTierLevel4);
    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L123", CodecProfileLevel.HEVCMainTierLevel41);
    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L150", CodecProfileLevel.HEVCMainTierLevel5);
    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L153", CodecProfileLevel.HEVCMainTierLevel51);
    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L156", CodecProfileLevel.HEVCMainTierLevel52);
    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L180", CodecProfileLevel.HEVCMainTierLevel6);
    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L183", CodecProfileLevel.HEVCMainTierLevel61);
    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L186", CodecProfileLevel.HEVCMainTierLevel62);

    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H30", CodecProfileLevel.HEVCHighTierLevel1);
    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H60", CodecProfileLevel.HEVCHighTierLevel2);
    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H63", CodecProfileLevel.HEVCHighTierLevel21);
    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H90", CodecProfileLevel.HEVCHighTierLevel3);
    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H93", CodecProfileLevel.HEVCHighTierLevel31);
    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H120", CodecProfileLevel.HEVCHighTierLevel4);
    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H123", CodecProfileLevel.HEVCHighTierLevel41);
    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H150", CodecProfileLevel.HEVCHighTierLevel5);
    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H153", CodecProfileLevel.HEVCHighTierLevel51);
    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H156", CodecProfileLevel.HEVCHighTierLevel52);
    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H180", CodecProfileLevel.HEVCHighTierLevel6);
    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H183", CodecProfileLevel.HEVCHighTierLevel61);
    HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H186", CodecProfileLevel.HEVCHighTierLevel62);
  }

}

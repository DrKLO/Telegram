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
package com.google.android.exoplayer2.drm;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.annotation.SuppressLint;
import android.media.DeniedByServerException;
import android.media.MediaCrypto;
import android.media.MediaCryptoException;
import android.media.MediaDrm;
import android.media.MediaDrmException;
import android.media.NotProvisionedException;
import android.media.UnsupportedSchemeException;
import android.media.metrics.LogSessionId;
import android.os.PersistableBundle;
import android.text.TextUtils;
import androidx.annotation.DoNotInline;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData;
import com.google.android.exoplayer2.extractor.mp4.PsshAtomUtil;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Charsets;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** An {@link ExoMediaDrm} implementation that wraps the framework {@link MediaDrm}. */
@RequiresApi(18)
public final class FrameworkMediaDrm implements ExoMediaDrm {

  private static final String TAG = "FrameworkMediaDrm";

  /**
   * {@link ExoMediaDrm.Provider} that returns a new {@link FrameworkMediaDrm} for the requested
   * UUID. Returns a {@link DummyExoMediaDrm} if the protection scheme identified by the given UUID
   * is not supported by the device.
   */
  public static final Provider DEFAULT_PROVIDER =
      uuid -> {
        try {
          return newInstance(uuid);
        } catch (UnsupportedDrmException e) {
          Log.e(TAG, "Failed to instantiate a FrameworkMediaDrm for uuid: " + uuid + ".");
          return new DummyExoMediaDrm();
        }
      };

  private static final String CENC_SCHEME_MIME_TYPE = "cenc";
  private static final String MOCK_LA_URL_VALUE = "https://x";
  private static final String MOCK_LA_URL = "<LA_URL>" + MOCK_LA_URL_VALUE + "</LA_URL>";
  private static final int UTF_16_BYTES_PER_CHARACTER = 2;

  private final UUID uuid;
  private final MediaDrm mediaDrm;
  private int referenceCount;

  /**
   * Returns whether the DRM scheme with the given UUID is supported on this device.
   *
   * @see MediaDrm#isCryptoSchemeSupported(UUID)
   */
  public static boolean isCryptoSchemeSupported(UUID uuid) {
    return MediaDrm.isCryptoSchemeSupported(adjustUuid(uuid));
  }

  /**
   * Creates an instance with an initial reference count of 1. {@link #release()} must be called on
   * the instance when it's no longer required.
   *
   * @param uuid The scheme uuid.
   * @return The created instance.
   * @throws UnsupportedDrmException If the DRM scheme is unsupported or cannot be instantiated.
   */
  public static FrameworkMediaDrm newInstance(UUID uuid) throws UnsupportedDrmException {
    try {
      return new FrameworkMediaDrm(uuid);
    } catch (UnsupportedSchemeException e) {
      throw new UnsupportedDrmException(UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME, e);
    } catch (Exception e) {
      throw new UnsupportedDrmException(UnsupportedDrmException.REASON_INSTANTIATION_ERROR, e);
    }
  }

  private FrameworkMediaDrm(UUID uuid) throws UnsupportedSchemeException {
    Assertions.checkNotNull(uuid);
    Assertions.checkArgument(!C.COMMON_PSSH_UUID.equals(uuid), "Use C.CLEARKEY_UUID instead");
    this.uuid = uuid;
    this.mediaDrm = new MediaDrm(adjustUuid(uuid));
    // Creators of an instance automatically acquire ownership of the created instance.
    referenceCount = 1;
    if (C.WIDEVINE_UUID.equals(uuid) && needsForceWidevineL3Workaround()) {
      forceWidevineL3(mediaDrm);
    }
  }

  @Override
  public void setOnEventListener(@Nullable ExoMediaDrm.OnEventListener listener) {
    mediaDrm.setOnEventListener(
        listener == null
            ? null
            : (mediaDrm, sessionId, event, extra, data) ->
                listener.onEvent(FrameworkMediaDrm.this, sessionId, event, extra, data));
  }

  /**
   * {@inheritDoc}
   *
   * @param listener The listener to receive events, or {@code null} to stop receiving events.
   * @throws UnsupportedOperationException on API levels lower than 23.
   */
  @Override
  @RequiresApi(23)
  public void setOnKeyStatusChangeListener(
      @Nullable ExoMediaDrm.OnKeyStatusChangeListener listener) {
    if (Util.SDK_INT < 23) {
      throw new UnsupportedOperationException();
    }

    mediaDrm.setOnKeyStatusChangeListener(
        listener == null
            ? null
            : (mediaDrm, sessionId, keyInfo, hasNewUsableKey) -> {
              List<KeyStatus> exoKeyInfo = new ArrayList<>();
              for (MediaDrm.KeyStatus keyStatus : keyInfo) {
                exoKeyInfo.add(new KeyStatus(keyStatus.getStatusCode(), keyStatus.getKeyId()));
              }
              listener.onKeyStatusChange(
                  FrameworkMediaDrm.this, sessionId, exoKeyInfo, hasNewUsableKey);
            },
        /* handler= */ null);
  }

  /**
   * {@inheritDoc}
   *
   * @param listener The listener to receive events, or {@code null} to stop receiving events.
   * @throws UnsupportedOperationException on API levels lower than 23.
   */
  @Override
  @RequiresApi(23)
  public void setOnExpirationUpdateListener(@Nullable OnExpirationUpdateListener listener) {
    if (Util.SDK_INT < 23) {
      throw new UnsupportedOperationException();
    }

    mediaDrm.setOnExpirationUpdateListener(
        listener == null
            ? null
            : (mediaDrm, sessionId, expirationTimeMs) ->
                listener.onExpirationUpdate(FrameworkMediaDrm.this, sessionId, expirationTimeMs),
        /* handler= */ null);
  }

  @Override
  public byte[] openSession() throws MediaDrmException {
    return mediaDrm.openSession();
  }

  @Override
  public void closeSession(byte[] sessionId) {
    mediaDrm.closeSession(sessionId);
  }

  @Override
  public void setPlayerIdForSession(byte[] sessionId, PlayerId playerId) {
    if (Util.SDK_INT >= 31) {
      try {
        Api31.setLogSessionIdOnMediaDrmSession(mediaDrm, sessionId, playerId);
      } catch (UnsupportedOperationException e) {
        Log.w(TAG, "setLogSessionId failed.");
      }
    }
  }

  // Return values of MediaDrm.KeyRequest.getRequestType are equal to KeyRequest.RequestType.
  @SuppressLint("WrongConstant")
  @Override
  public KeyRequest getKeyRequest(
      byte[] scope,
      @Nullable List<DrmInitData.SchemeData> schemeDatas,
      int keyType,
      @Nullable HashMap<String, String> optionalParameters)
      throws NotProvisionedException {
    SchemeData schemeData = null;
    byte[] initData = null;
    String mimeType = null;
    if (schemeDatas != null) {
      schemeData = getSchemeData(uuid, schemeDatas);
      initData = adjustRequestInitData(uuid, Assertions.checkNotNull(schemeData.data));
      mimeType = adjustRequestMimeType(uuid, schemeData.mimeType);
    }
    MediaDrm.KeyRequest request =
        mediaDrm.getKeyRequest(scope, initData, mimeType, keyType, optionalParameters);

    byte[] requestData = adjustRequestData(uuid, request.getData());
    String licenseServerUrl = adjustLicenseServerUrl(request.getDefaultUrl());
    if (TextUtils.isEmpty(licenseServerUrl)
        && schemeData != null
        && !TextUtils.isEmpty(schemeData.licenseServerUrl)) {
      licenseServerUrl = schemeData.licenseServerUrl;
    }

    @KeyRequest.RequestType
    int requestType =
        Util.SDK_INT >= 23 ? request.getRequestType() : KeyRequest.REQUEST_TYPE_UNKNOWN;

    return new KeyRequest(requestData, licenseServerUrl, requestType);
  }

  private static String adjustLicenseServerUrl(String licenseServerUrl) {
    if (MOCK_LA_URL.equals(licenseServerUrl)) {
      return "";
    } else if (Util.SDK_INT == 33 && "https://default.url".equals(licenseServerUrl)) {
      // Work around b/247808112
      return "";
    } else {
      return licenseServerUrl;
    }
  }

  @Override
  @Nullable
  public byte[] provideKeyResponse(byte[] scope, byte[] response)
      throws NotProvisionedException, DeniedByServerException {
    if (C.CLEARKEY_UUID.equals(uuid)) {
      response = ClearKeyUtil.adjustResponseData(response);
    }

    return mediaDrm.provideKeyResponse(scope, response);
  }

  @Override
  public ProvisionRequest getProvisionRequest() {
    final MediaDrm.ProvisionRequest request = mediaDrm.getProvisionRequest();
    return new ProvisionRequest(request.getData(), request.getDefaultUrl());
  }

  @Override
  public void provideProvisionResponse(byte[] response) throws DeniedByServerException {
    mediaDrm.provideProvisionResponse(response);
  }

  @Override
  public Map<String, String> queryKeyStatus(byte[] sessionId) {
    return mediaDrm.queryKeyStatus(sessionId);
  }

  @Override
  public boolean requiresSecureDecoder(byte[] sessionId, String mimeType) {
    if (Util.SDK_INT >= 31) {
      return Api31.requiresSecureDecoder(mediaDrm, mimeType);
    }

    MediaCrypto mediaCrypto;
    try {
      mediaCrypto = new MediaCrypto(uuid, sessionId);
    } catch (MediaCryptoException e) {
      // This shouldn't happen, but if it does then assume that a secure decoder may be required.
      return true;
    }
    try {
      return mediaCrypto.requiresSecureDecoderComponent(mimeType);
    } finally {
      mediaCrypto.release();
    }
  }

  @Override
  public synchronized void acquire() {
    Assertions.checkState(referenceCount > 0);
    referenceCount++;
  }

  @Override
  public synchronized void release() {
    if (--referenceCount == 0) {
      mediaDrm.release();
    }
  }

  @Override
  public void restoreKeys(byte[] sessionId, byte[] keySetId) {
    mediaDrm.restoreKeys(sessionId, keySetId);
  }

  @Override
  @Nullable
  public PersistableBundle getMetrics() {
    if (Util.SDK_INT < 28) {
      return null;
    }
    return mediaDrm.getMetrics();
  }

  @Override
  public String getPropertyString(String propertyName) {
    return mediaDrm.getPropertyString(propertyName);
  }

  @Override
  public byte[] getPropertyByteArray(String propertyName) {
    return mediaDrm.getPropertyByteArray(propertyName);
  }

  @Override
  public void setPropertyString(String propertyName, String value) {
    mediaDrm.setPropertyString(propertyName, value);
  }

  @Override
  public void setPropertyByteArray(String propertyName, byte[] value) {
    mediaDrm.setPropertyByteArray(propertyName, value);
  }

  @Override
  public FrameworkCryptoConfig createCryptoConfig(byte[] sessionId) throws MediaCryptoException {
    // Work around a bug prior to Lollipop where L1 Widevine forced into L3 mode would still
    // indicate that it required secure video decoders [Internal ref: b/11428937].
    boolean forceAllowInsecureDecoderComponents =
        Util.SDK_INT < 21
            && C.WIDEVINE_UUID.equals(uuid)
            && "L3".equals(getPropertyString("securityLevel"));
    return new FrameworkCryptoConfig(
        adjustUuid(uuid), sessionId, forceAllowInsecureDecoderComponents);
  }

  @Override
  public @C.CryptoType int getCryptoType() {
    return C.CRYPTO_TYPE_FRAMEWORK;
  }

  private static SchemeData getSchemeData(UUID uuid, List<SchemeData> schemeDatas) {
    if (!C.WIDEVINE_UUID.equals(uuid)) {
      // For non-Widevine CDMs always use the first scheme data.
      return schemeDatas.get(0);
    }

    if (Util.SDK_INT >= 28 && schemeDatas.size() > 1) {
      // For API level 28 and above, concatenate multiple PSSH scheme datas if possible.
      SchemeData firstSchemeData = schemeDatas.get(0);
      int concatenatedDataLength = 0;
      boolean canConcatenateData = true;
      for (int i = 0; i < schemeDatas.size(); i++) {
        SchemeData schemeData = schemeDatas.get(i);
        byte[] schemeDataData = Assertions.checkNotNull(schemeData.data);
        if (Util.areEqual(schemeData.mimeType, firstSchemeData.mimeType)
            && Util.areEqual(schemeData.licenseServerUrl, firstSchemeData.licenseServerUrl)
            && PsshAtomUtil.isPsshAtom(schemeDataData)) {
          concatenatedDataLength += schemeDataData.length;
        } else {
          canConcatenateData = false;
          break;
        }
      }
      if (canConcatenateData) {
        byte[] concatenatedData = new byte[concatenatedDataLength];
        int concatenatedDataPosition = 0;
        for (int i = 0; i < schemeDatas.size(); i++) {
          SchemeData schemeData = schemeDatas.get(i);
          byte[] schemeDataData = Assertions.checkNotNull(schemeData.data);
          int schemeDataLength = schemeDataData.length;
          System.arraycopy(
              schemeDataData, 0, concatenatedData, concatenatedDataPosition, schemeDataLength);
          concatenatedDataPosition += schemeDataLength;
        }
        return firstSchemeData.copyWithData(concatenatedData);
      }
    }

    // For API levels 23 - 27, prefer the first V1 PSSH box. For API levels 22 and earlier, prefer
    // the first V0 box.
    for (int i = 0; i < schemeDatas.size(); i++) {
      SchemeData schemeData = schemeDatas.get(i);
      int version = PsshAtomUtil.parseVersion(Assertions.checkNotNull(schemeData.data));
      if (Util.SDK_INT < 23 && version == 0) {
        return schemeData;
      } else if (Util.SDK_INT >= 23 && version == 1) {
        return schemeData;
      }
    }

    // If all else fails, use the first scheme data.
    return schemeDatas.get(0);
  }

  private static UUID adjustUuid(UUID uuid) {
    // ClearKey had to be accessed using the Common PSSH UUID prior to API level 27.
    return Util.SDK_INT < 27 && C.CLEARKEY_UUID.equals(uuid) ? C.COMMON_PSSH_UUID : uuid;
  }

  private static byte[] adjustRequestInitData(UUID uuid, byte[] initData) {
    // TODO: Add API level check once [Internal ref: b/112142048] is fixed.
    if (C.PLAYREADY_UUID.equals(uuid)) {
      byte[] schemeSpecificData = PsshAtomUtil.parseSchemeSpecificData(initData, uuid);
      if (schemeSpecificData == null) {
        // The init data is not contained in a pssh box.
        schemeSpecificData = initData;
      }
      initData =
          PsshAtomUtil.buildPsshAtom(
              C.PLAYREADY_UUID, addLaUrlAttributeIfMissing(schemeSpecificData));
    }

    // Prior to API level 21, the Widevine CDM required scheme specific data to be extracted from
    // the PSSH atom. We also extract the data on API levels 21 and 22 because these API levels
    // don't handle V1 PSSH atoms, but do handle scheme specific data regardless of whether it's
    // extracted from a V0 or a V1 PSSH atom. Hence extracting the data allows us to support content
    // that only provides V1 PSSH atoms. API levels 23 and above understand V0 and V1 PSSH atoms,
    // and so we do not extract the data.
    // Some Amazon devices also require data to be extracted from the PSSH atom for PlayReady.
    if ((Util.SDK_INT < 23 && C.WIDEVINE_UUID.equals(uuid))
        || (C.PLAYREADY_UUID.equals(uuid)
            && "Amazon".equals(Util.MANUFACTURER)
            && ("AFTB".equals(Util.MODEL) // Fire TV Gen 1
                || "AFTS".equals(Util.MODEL) // Fire TV Gen 2
                || "AFTM".equals(Util.MODEL) // Fire TV Stick Gen 1
                || "AFTT".equals(Util.MODEL)))) { // Fire TV Stick Gen 2
      byte[] psshData = PsshAtomUtil.parseSchemeSpecificData(initData, uuid);
      if (psshData != null) {
        // Extraction succeeded, so return the extracted data.
        return psshData;
      }
    }
    return initData;
  }

  private static String adjustRequestMimeType(UUID uuid, String mimeType) {
    // Prior to API level 26 the ClearKey CDM only accepted "cenc" as the scheme for MP4.
    if (Util.SDK_INT < 26
        && C.CLEARKEY_UUID.equals(uuid)
        && (MimeTypes.VIDEO_MP4.equals(mimeType) || MimeTypes.AUDIO_MP4.equals(mimeType))) {
      return CENC_SCHEME_MIME_TYPE;
    }
    return mimeType;
  }

  private static byte[] adjustRequestData(UUID uuid, byte[] requestData) {
    if (C.CLEARKEY_UUID.equals(uuid)) {
      return ClearKeyUtil.adjustRequestData(requestData);
    }
    return requestData;
  }

  private static void forceWidevineL3(MediaDrm mediaDrm) {
    mediaDrm.setPropertyString("securityLevel", "L3");
  }

  /**
   * Returns whether the device codec is known to fail if security level L1 is used.
   *
   * <p>See <a href="https://github.com/google/ExoPlayer/issues/4413">GitHub issue #4413</a>.
   */
  private static boolean needsForceWidevineL3Workaround() {
    return "ASUS_Z00AD".equals(Util.MODEL);
  }

  /**
   * If the LA_URL tag is missing, injects a mock LA_URL value to avoid causing the CDM to throw
   * when creating the key request. The LA_URL attribute is optional but some Android PlayReady
   * implementations are known to require it. Does nothing it the provided {@code data} already
   * contains an LA_URL value.
   */
  private static byte[] addLaUrlAttributeIfMissing(byte[] data) {
    ParsableByteArray byteArray = new ParsableByteArray(data);
    // See https://docs.microsoft.com/en-us/playready/specifications/specifications for more
    // information about the init data format.
    int length = byteArray.readLittleEndianInt();
    int objectRecordCount = byteArray.readLittleEndianShort();
    int recordType = byteArray.readLittleEndianShort();
    if (objectRecordCount != 1 || recordType != 1) {
      Log.i(TAG, "Unexpected record count or type. Skipping LA_URL workaround.");
      return data;
    }
    int recordLength = byteArray.readLittleEndianShort();
    String xml = byteArray.readString(recordLength, Charsets.UTF_16LE);
    if (xml.contains("<LA_URL>")) {
      // LA_URL already present. Do nothing.
      return data;
    }
    // This PlayReady object record does not include an LA_URL. We add a mock value for it.
    int endOfDataTagIndex = xml.indexOf("</DATA>");
    if (endOfDataTagIndex == -1) {
      Log.w(TAG, "Could not find the </DATA> tag. Skipping LA_URL workaround.");
    }
    String xmlWithMockLaUrl =
        xml.substring(/* beginIndex= */ 0, /* endIndex= */ endOfDataTagIndex)
            + MOCK_LA_URL
            + xml.substring(/* beginIndex= */ endOfDataTagIndex);
    int extraBytes = MOCK_LA_URL.length() * UTF_16_BYTES_PER_CHARACTER;
    ByteBuffer newData = ByteBuffer.allocate(length + extraBytes);
    newData.order(ByteOrder.LITTLE_ENDIAN);
    newData.putInt(length + extraBytes);
    newData.putShort((short) objectRecordCount);
    newData.putShort((short) recordType);
    newData.putShort((short) (xmlWithMockLaUrl.length() * UTF_16_BYTES_PER_CHARACTER));
    newData.put(xmlWithMockLaUrl.getBytes(Charsets.UTF_16LE));
    return newData.array();
  }

  @RequiresApi(31)
  private static class Api31 {
    private Api31() {}

    @DoNotInline
    public static boolean requiresSecureDecoder(MediaDrm mediaDrm, String mimeType) {
      return mediaDrm.requiresSecureDecoder(mimeType);
    }

    @DoNotInline
    public static void setLogSessionIdOnMediaDrmSession(
        MediaDrm mediaDrm, byte[] drmSessionId, PlayerId playerId) {
      LogSessionId logSessionId = playerId.getLogSessionId();
      if (!logSessionId.equals(LogSessionId.LOG_SESSION_ID_NONE)) {
        MediaDrm.PlaybackComponent playbackComponent =
            checkNotNull(mediaDrm.getPlaybackComponent(drmSessionId));
        playbackComponent.setLogSessionId(logSessionId);
      }
    }
  }
}

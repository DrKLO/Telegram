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
package org.telegram.messenger.exoplayer2.drm;

import android.annotation.TargetApi;
import android.media.DeniedByServerException;
import android.media.MediaCrypto;
import android.media.MediaCryptoException;
import android.media.MediaDrm;
import android.media.MediaDrmException;
import android.media.NotProvisionedException;
import android.media.UnsupportedSchemeException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An {@link ExoMediaDrm} implementation that wraps the framework {@link MediaDrm}.
 */
@TargetApi(23)
public final class FrameworkMediaDrm implements ExoMediaDrm<FrameworkMediaCrypto> {

  private final UUID uuid;
  private final MediaDrm mediaDrm;

  /**
   * Creates an instance for the specified scheme UUID.
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
    // ClearKey had to be accessed using the Common PSSH UUID prior to API level 27.
    uuid = Util.SDK_INT < 27 && C.CLEARKEY_UUID.equals(uuid) ? C.COMMON_PSSH_UUID : uuid;
    this.uuid = uuid;
    this.mediaDrm = new MediaDrm(uuid);
  }

  @Override
  public void setOnEventListener(
      final ExoMediaDrm.OnEventListener<? super FrameworkMediaCrypto> listener) {
    mediaDrm.setOnEventListener(listener == null ? null : new MediaDrm.OnEventListener() {
      @Override
      public void onEvent(@NonNull MediaDrm md, @Nullable byte[] sessionId, int event, int extra,
          byte[] data) {
        listener.onEvent(FrameworkMediaDrm.this, sessionId, event, extra, data);
      }
    });
  }

  @Override
  public void setOnKeyStatusChangeListener(
      final ExoMediaDrm.OnKeyStatusChangeListener<? super FrameworkMediaCrypto> listener) {
    if (Util.SDK_INT < 23) {
      throw new UnsupportedOperationException();
    }
    
    mediaDrm.setOnKeyStatusChangeListener(listener == null ? null
        : new MediaDrm.OnKeyStatusChangeListener() {
          @Override
          public void onKeyStatusChange(@NonNull MediaDrm md, @NonNull byte[] sessionId,
              @NonNull List<MediaDrm.KeyStatus> keyInfo, boolean hasNewUsableKey) {
            List<KeyStatus> exoKeyInfo = new ArrayList<>();
            for (MediaDrm.KeyStatus keyStatus : keyInfo) {
              exoKeyInfo.add(new DefaultKeyStatus(keyStatus.getStatusCode(), keyStatus.getKeyId()));
            }
            listener.onKeyStatusChange(FrameworkMediaDrm.this, sessionId, exoKeyInfo,
                hasNewUsableKey);
          }
        }, null);
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
  public KeyRequest getKeyRequest(byte[] scope, byte[] init, String mimeType, int keyType,
      HashMap<String, String> optionalParameters) throws NotProvisionedException {
    final MediaDrm.KeyRequest request = mediaDrm.getKeyRequest(scope, init, mimeType, keyType,
        optionalParameters);
    return new DefaultKeyRequest(request.getData(), request.getDefaultUrl());
  }

  @Override
  public byte[] provideKeyResponse(byte[] scope, byte[] response)
      throws NotProvisionedException, DeniedByServerException {
    return mediaDrm.provideKeyResponse(scope, response);
  }

  @Override
  public ProvisionRequest getProvisionRequest() {
    final MediaDrm.ProvisionRequest request = mediaDrm.getProvisionRequest();
    return new DefaultProvisionRequest(request.getData(), request.getDefaultUrl());
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
  public void release() {
    mediaDrm.release();
  }

  @Override
  public void restoreKeys(byte[] sessionId, byte[] keySetId) {
    mediaDrm.restoreKeys(sessionId, keySetId);
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
  public FrameworkMediaCrypto createMediaCrypto(byte[] initData) throws MediaCryptoException {
    // Work around a bug prior to Lollipop where L1 Widevine forced into L3 mode would still
    // indicate that it required secure video decoders [Internal ref: b/11428937].
    boolean forceAllowInsecureDecoderComponents = Util.SDK_INT < 21
        && C.WIDEVINE_UUID.equals(uuid) && "L3".equals(getPropertyString("securityLevel"));
    return new FrameworkMediaCrypto(new MediaCrypto(uuid, initData),
        forceAllowInsecureDecoderComponents);
  }

}

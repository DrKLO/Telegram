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
import android.media.NotProvisionedException;
import android.media.ResourceBusyException;
import android.media.UnsupportedSchemeException;
import android.support.annotation.NonNull;
import org.telegram.messenger.exoplayer2.util.Assertions;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * An {@link ExoMediaDrm} implementation that wraps the framework {@link MediaDrm}.
 */
@TargetApi(18)
public final class FrameworkMediaDrm implements ExoMediaDrm<FrameworkMediaCrypto> {

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
    this.mediaDrm = new MediaDrm(Assertions.checkNotNull(uuid));
  }

  @Override
  public void setOnEventListener(
      final ExoMediaDrm.OnEventListener<? super FrameworkMediaCrypto> listener) {
    mediaDrm.setOnEventListener(listener == null ? null : new MediaDrm.OnEventListener() {
      @Override
      public void onEvent(@NonNull MediaDrm md, byte[] sessionId, int event, int extra,
          byte[] data) {
        listener.onEvent(FrameworkMediaDrm.this, sessionId, event, extra, data);
      }
    });
  }

  @Override
  public byte[] openSession() throws NotProvisionedException, ResourceBusyException {
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
    return new KeyRequest() {
      @Override
      public byte[] getData() {
        return request.getData();
      }

      @Override
      public String getDefaultUrl() {
        return request.getDefaultUrl();
      }
    };
  }

  @Override
  public byte[] provideKeyResponse(byte[] scope, byte[] response)
      throws NotProvisionedException, DeniedByServerException {
    return mediaDrm.provideKeyResponse(scope, response);
  }

  @Override
  public ProvisionRequest getProvisionRequest() {
    final MediaDrm.ProvisionRequest provisionRequest = mediaDrm.getProvisionRequest();
    return new ProvisionRequest() {
      @Override
      public byte[] getData() {
        return provisionRequest.getData();
      }

      @Override
      public String getDefaultUrl() {
        return provisionRequest.getDefaultUrl();
      }
    };
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
  public FrameworkMediaCrypto createMediaCrypto(UUID uuid, byte[] initData)
      throws MediaCryptoException {
    return new FrameworkMediaCrypto(new MediaCrypto(uuid, initData));
  }

}

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

import android.media.DeniedByServerException;
import android.media.MediaCryptoException;
import android.media.MediaDrm;
import android.media.MediaDrmException;
import android.media.NotProvisionedException;
import android.os.Handler;
import android.support.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Used to obtain keys for decrypting protected media streams. See {@link android.media.MediaDrm}.
 */
public interface ExoMediaDrm<T extends ExoMediaCrypto> {

  /**
   * @see MediaDrm#EVENT_KEY_REQUIRED
   */
  @SuppressWarnings("InlinedApi")
  int EVENT_KEY_REQUIRED = MediaDrm.EVENT_KEY_REQUIRED;
  /**
   * @see MediaDrm#EVENT_KEY_EXPIRED
   */
  @SuppressWarnings("InlinedApi")
  int EVENT_KEY_EXPIRED = MediaDrm.EVENT_KEY_EXPIRED;
  /**
   * @see MediaDrm#EVENT_PROVISION_REQUIRED
   */
  @SuppressWarnings("InlinedApi")
  int EVENT_PROVISION_REQUIRED = MediaDrm.EVENT_PROVISION_REQUIRED;

  /**
   * @see MediaDrm#KEY_TYPE_STREAMING
   */
  @SuppressWarnings("InlinedApi")
  int KEY_TYPE_STREAMING = MediaDrm.KEY_TYPE_STREAMING;
  /**
   * @see MediaDrm#KEY_TYPE_OFFLINE
   */
  @SuppressWarnings("InlinedApi")
  int KEY_TYPE_OFFLINE = MediaDrm.KEY_TYPE_OFFLINE;
  /**
   * @see MediaDrm#KEY_TYPE_RELEASE
   */
  @SuppressWarnings("InlinedApi")
  int KEY_TYPE_RELEASE = MediaDrm.KEY_TYPE_RELEASE;

  /**
   * @see android.media.MediaDrm.OnEventListener
   */
  interface OnEventListener<T extends ExoMediaCrypto> {
    /**
     * Called when an event occurs that requires the app to be notified
     *
     * @param mediaDrm The {@link ExoMediaDrm} object on which the event occurred.
     * @param sessionId The DRM session ID on which the event occurred.
     * @param event Indicates the event type.
     * @param extra A secondary error code.
     * @param data Optional byte array of data that may be associated with the event.
     */
    void onEvent(
        ExoMediaDrm<? extends T> mediaDrm,
        byte[] sessionId,
        int event,
        int extra,
        @Nullable byte[] data);
  }

  /**
   * @see android.media.MediaDrm.OnKeyStatusChangeListener
   */
  interface OnKeyStatusChangeListener<T extends ExoMediaCrypto> {
    /**
     * Called when the keys in a session change status, such as when the license is renewed or
     * expires.
     *
     * @param mediaDrm The {@link ExoMediaDrm} object on which the event occurred.
     * @param sessionId The DRM session ID on which the event occurred.
     * @param exoKeyInformation A list of {@link KeyStatus} that contains key ID and status.
     * @param hasNewUsableKey Whether a new key became usable.
     */
    void onKeyStatusChange(
        ExoMediaDrm<? extends T> mediaDrm,
        byte[] sessionId,
        List<KeyStatus> exoKeyInformation,
        boolean hasNewUsableKey);
  }

  /**
   * @see android.media.MediaDrm.KeyStatus
   */
  interface KeyStatus {
    /** Returns the status code for the key. */
    int getStatusCode();
    /** Returns the id for the key. */
    byte[] getKeyId();
  }

  /**
   * Default implementation of {@link KeyStatus}.
   */
  final class DefaultKeyStatus implements KeyStatus {

    private final int statusCode;
    private final byte[] keyId;

    DefaultKeyStatus(int statusCode, byte[] keyId) {
      this.statusCode = statusCode;
      this.keyId = keyId;
    }

    @Override
    public int getStatusCode() {
      return statusCode;
    }

    @Override
    public byte[] getKeyId() {
      return keyId;
    }

  }

  /**
   * @see android.media.MediaDrm.KeyRequest
   */
  interface KeyRequest {
    byte[] getData();
    String getDefaultUrl();
  }

  /**
   * Default implementation of {@link KeyRequest}.
   */
  final class DefaultKeyRequest implements KeyRequest {

    private final byte[] data;
    private final String defaultUrl;

    public DefaultKeyRequest(byte[] data, String defaultUrl) {
      this.data = data;
      this.defaultUrl = defaultUrl;
    }

    @Override
    public byte[] getData() {
      return data;
    }

    @Override
    public String getDefaultUrl() {
      return defaultUrl;
    }

  }

  /**
   * @see android.media.MediaDrm.ProvisionRequest
   */
  interface ProvisionRequest {
    byte[] getData();
    String getDefaultUrl();
  }

  /**
   * Default implementation of {@link ProvisionRequest}.
   */
  final class DefaultProvisionRequest implements ProvisionRequest {

    private final byte[] data;
    private final String defaultUrl;

    public DefaultProvisionRequest(byte[] data, String defaultUrl) {
      this.data = data;
      this.defaultUrl = defaultUrl;
    }

    @Override
    public byte[] getData() {
      return data;
    }

    @Override
    public String getDefaultUrl() {
      return defaultUrl;
    }

  }

  /**
   * @see MediaDrm#setOnEventListener(MediaDrm.OnEventListener)
   */
  void setOnEventListener(OnEventListener<? super T> listener);

  /**
   * @see MediaDrm#setOnKeyStatusChangeListener(MediaDrm.OnKeyStatusChangeListener, Handler)
   */
  void setOnKeyStatusChangeListener(OnKeyStatusChangeListener<? super T> listener);

  /**
   * @see MediaDrm#openSession()
   */
  byte[] openSession() throws MediaDrmException;

  /**
   * @see MediaDrm#closeSession(byte[])
   */
  void closeSession(byte[] sessionId);

  /** @see MediaDrm#getKeyRequest(byte[], byte[], String, int, HashMap) */
  KeyRequest getKeyRequest(
      byte[] scope,
      byte[] init,
      String mimeType,
      int keyType,
      HashMap<String, String> optionalParameters)
      throws NotProvisionedException;

  /** @see MediaDrm#provideKeyResponse(byte[], byte[]) */
  byte[] provideKeyResponse(byte[] scope, byte[] response)
      throws NotProvisionedException, DeniedByServerException;

  /**
   * @see MediaDrm#getProvisionRequest()
   */
  ProvisionRequest getProvisionRequest();

  /**
   * @see MediaDrm#provideProvisionResponse(byte[])
   */
  void provideProvisionResponse(byte[] response) throws DeniedByServerException;

  /**
   * @see MediaDrm#queryKeyStatus(byte[])
   */
  Map<String, String> queryKeyStatus(byte[] sessionId);

  /**
   * @see MediaDrm#release()
   */
  void release();

  /**
   * @see MediaDrm#restoreKeys(byte[], byte[])
   */
  void restoreKeys(byte[] sessionId, byte[] keySetId);

  /**
   * @see MediaDrm#getPropertyString(String)
   */
  String getPropertyString(String propertyName);

  /**
   * @see MediaDrm#getPropertyByteArray(String)
   */
  byte[] getPropertyByteArray(String propertyName);

  /**
   * @see MediaDrm#setPropertyString(String, String)
   */
  void setPropertyString(String propertyName, String value);

  /**
   * @see MediaDrm#setPropertyByteArray(String, byte[])
   */
  void setPropertyByteArray(String propertyName, byte[] value);

  /**
   * @see android.media.MediaCrypto#MediaCrypto(UUID, byte[])
   *
   * @param initData Opaque initialization data specific to the crypto scheme.
   * @return An object extends {@link ExoMediaCrypto}, using opaque crypto scheme specific data.
   * @throws MediaCryptoException If the instance can't be created.
   */
  T createMediaCrypto(byte[] initData) throws MediaCryptoException;

}

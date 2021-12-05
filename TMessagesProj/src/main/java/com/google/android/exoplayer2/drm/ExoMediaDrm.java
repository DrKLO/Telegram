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
import android.os.PersistableBundle;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Used to obtain keys for decrypting protected media streams. See {@link android.media.MediaDrm}.
 *
 * <h3>Reference counting</h3>
 *
 * <p>Access to an instance is managed by reference counting, where {@link #acquire()} increments
 * the reference count and {@link #release()} decrements it. When the reference count drops to 0
 * underlying resources are released, and the instance cannot be re-used.
 *
 * <p>Each new instance has an initial reference count of 1. Hence application code that creates a
 * new instance does not normally need to call {@link #acquire()}, and must call {@link #release()}
 * when the instance is no longer required.
 */
public interface ExoMediaDrm<T extends ExoMediaCrypto> {

  /** {@link ExoMediaDrm} instances provider. */
  interface Provider<T extends ExoMediaCrypto> {

    /**
     * Returns an {@link ExoMediaDrm} instance with an incremented reference count. When the caller
     * no longer needs to use the instance, it must call {@link ExoMediaDrm#release()} to decrement
     * the reference count.
     */
    ExoMediaDrm<T> acquireExoMediaDrm(UUID uuid);
  }

  /**
   * Provides an {@link ExoMediaDrm} instance owned by the app.
   *
   * <p>Note that when using this provider the app will have instantiated the {@link ExoMediaDrm}
   * instance, and remains responsible for calling {@link ExoMediaDrm#release()} on the instance
   * when it's no longer being used.
   */
  final class AppManagedProvider<T extends ExoMediaCrypto> implements Provider<T> {

    private final ExoMediaDrm<T> exoMediaDrm;

    /** Creates an instance that provides the given {@link ExoMediaDrm}. */
    public AppManagedProvider(ExoMediaDrm<T> exoMediaDrm) {
      this.exoMediaDrm = exoMediaDrm;
    }

    @Override
    public ExoMediaDrm<T> acquireExoMediaDrm(UUID uuid) {
      exoMediaDrm.acquire();
      return exoMediaDrm;
    }
  }

  /** @see MediaDrm#EVENT_KEY_REQUIRED */
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
        @Nullable byte[] sessionId,
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

  /** @see android.media.MediaDrm.KeyStatus */
  final class KeyStatus {

    private final int statusCode;
    private final byte[] keyId;

    public KeyStatus(int statusCode, byte[] keyId) {
      this.statusCode = statusCode;
      this.keyId = keyId;
    }

    public int getStatusCode() {
      return statusCode;
    }

    public byte[] getKeyId() {
      return keyId;
    }

  }

  /** @see android.media.MediaDrm.KeyRequest */
  final class KeyRequest {

    private final byte[] data;
    private final String licenseServerUrl;

    public KeyRequest(byte[] data, String licenseServerUrl) {
      this.data = data;
      this.licenseServerUrl = licenseServerUrl;
    }

    public byte[] getData() {
      return data;
    }

    public String getLicenseServerUrl() {
      return licenseServerUrl;
    }

  }

  /** @see android.media.MediaDrm.ProvisionRequest */
  final class ProvisionRequest {

    private final byte[] data;
    private final String defaultUrl;

    public ProvisionRequest(byte[] data, String defaultUrl) {
      this.data = data;
      this.defaultUrl = defaultUrl;
    }

    public byte[] getData() {
      return data;
    }

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

  /**
   * Generates a key request.
   *
   * @param scope If {@code keyType} is {@link #KEY_TYPE_STREAMING} or {@link #KEY_TYPE_OFFLINE},
   *     the session id that the keys will be provided to. If {@code keyType} is {@link
   *     #KEY_TYPE_RELEASE}, the keySetId of the keys to release.
   * @param schemeDatas If key type is {@link #KEY_TYPE_STREAMING} or {@link #KEY_TYPE_OFFLINE}, a
   *     list of {@link SchemeData} instances extracted from the media. Null otherwise.
   * @param keyType The type of the request. Either {@link #KEY_TYPE_STREAMING} to acquire keys for
   *     streaming, {@link #KEY_TYPE_OFFLINE} to acquire keys for offline usage, or {@link
   *     #KEY_TYPE_RELEASE} to release acquired keys. Releasing keys invalidates them for all
   *     sessions.
   * @param optionalParameters Are included in the key request message to allow a client application
   *     to provide additional message parameters to the server. This may be {@code null} if no
   *     additional parameters are to be sent.
   * @return The generated key request.
   * @see MediaDrm#getKeyRequest(byte[], byte[], String, int, HashMap)
   */
  KeyRequest getKeyRequest(
      byte[] scope,
      @Nullable List<SchemeData> schemeDatas,
      int keyType,
      @Nullable HashMap<String, String> optionalParameters)
      throws NotProvisionedException;

  /** @see MediaDrm#provideKeyResponse(byte[], byte[]) */
  @Nullable
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
   * Increments the reference count. When the caller no longer needs to use the instance, it must
   * call {@link #release()} to decrement the reference count.
   *
   * <p>A new instance will have an initial reference count of 1, and therefore it is not normally
   * necessary for application code to call this method.
   */
  void acquire();

  /**
   * Decrements the reference count. If the reference count drops to 0 underlying resources are
   * released, and the instance cannot be re-used.
   */
  void release();

  /**
   * @see MediaDrm#restoreKeys(byte[], byte[])
   */
  void restoreKeys(byte[] sessionId, byte[] keySetId);

  /**
   * Returns drm metrics. May be null if unavailable.
   *
   * @see MediaDrm#getMetrics()
   */
  @Nullable
  PersistableBundle getMetrics();

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
   * @param sessionId The DRM session ID.
   * @return An object extends {@link ExoMediaCrypto}, using opaque crypto scheme specific data.
   * @throws MediaCryptoException If the instance can't be created.
   */
  T createMediaCrypto(byte[] sessionId) throws MediaCryptoException;

  /**
   * Returns the {@link ExoMediaCrypto} type created by {@link #createMediaCrypto(byte[])}, or null
   * if this instance cannot create any {@link ExoMediaCrypto} instances.
   */
  @Nullable
  Class<T> getExoMediaCryptoType();
}

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

import static java.lang.annotation.ElementType.TYPE_USE;

import android.media.DeniedByServerException;
import android.media.MediaCryptoException;
import android.media.MediaDrm;
import android.media.MediaDrmException;
import android.media.NotProvisionedException;
import android.media.ResourceBusyException;
import android.os.Handler;
import android.os.PersistableBundle;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.decoder.CryptoConfig;
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Used to obtain keys for decrypting protected media streams.
 *
 * <h2>Reference counting</h2>
 *
 * <p>Access to an instance is managed by reference counting, where {@link #acquire()} increments
 * the reference count and {@link #release()} decrements it. When the reference count drops to 0
 * underlying resources are released, and the instance cannot be re-used.
 *
 * <p>Each new instance has an initial reference count of 1. Hence application code that creates a
 * new instance does not normally need to call {@link #acquire()}, and must call {@link #release()}
 * when the instance is no longer required.
 *
 * @see MediaDrm
 */
public interface ExoMediaDrm {

  /** Provider for {@link ExoMediaDrm} instances. */
  interface Provider {

    /**
     * Returns an {@link ExoMediaDrm} instance with an incremented reference count. When the caller
     * no longer needs the instance, it must call {@link ExoMediaDrm#release()} to decrement the
     * reference count.
     */
    ExoMediaDrm acquireExoMediaDrm(UUID uuid);
  }

  /**
   * Provides an {@link ExoMediaDrm} instance owned by the app.
   *
   * <p>Note that when using this provider the app will have instantiated the {@link ExoMediaDrm}
   * instance, and remains responsible for calling {@link ExoMediaDrm#release()} on the instance
   * when it's no longer being used.
   */
  final class AppManagedProvider implements Provider {

    private final ExoMediaDrm exoMediaDrm;

    /** Creates an instance that provides the given {@link ExoMediaDrm}. */
    public AppManagedProvider(ExoMediaDrm exoMediaDrm) {
      this.exoMediaDrm = exoMediaDrm;
    }

    @Override
    public ExoMediaDrm acquireExoMediaDrm(UUID uuid) {
      exoMediaDrm.acquire();
      return exoMediaDrm;
    }
  }

  /** Event indicating that keys need to be requested from the license server. */
  @SuppressWarnings("InlinedApi")
  int EVENT_KEY_REQUIRED = MediaDrm.EVENT_KEY_REQUIRED;
  /** Event indicating that keys have expired, and are no longer usable. */
  @SuppressWarnings("InlinedApi")
  int EVENT_KEY_EXPIRED = MediaDrm.EVENT_KEY_EXPIRED;
  /** Event indicating that a certificate needs to be requested from the provisioning server. */
  @SuppressWarnings("InlinedApi")
  int EVENT_PROVISION_REQUIRED = MediaDrm.EVENT_PROVISION_REQUIRED;

  /**
   * Key request type for keys that will be used for online use. Streaming keys will not be saved to
   * the device for subsequent use when the device is not connected to a network.
   */
  @SuppressWarnings("InlinedApi")
  int KEY_TYPE_STREAMING = MediaDrm.KEY_TYPE_STREAMING;
  /**
   * Key request type for keys that will be used for offline use. They will be saved to the device
   * for subsequent use when the device is not connected to a network.
   */
  @SuppressWarnings("InlinedApi")
  int KEY_TYPE_OFFLINE = MediaDrm.KEY_TYPE_OFFLINE;
  /** Key request type indicating that saved offline keys should be released. */
  @SuppressWarnings("InlinedApi")
  int KEY_TYPE_RELEASE = MediaDrm.KEY_TYPE_RELEASE;

  /**
   * Called when a DRM event occurs.
   *
   * @see MediaDrm.OnEventListener
   */
  interface OnEventListener {
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
        ExoMediaDrm mediaDrm,
        @Nullable byte[] sessionId,
        int event,
        int extra,
        @Nullable byte[] data);
  }

  /**
   * Called when the keys in a DRM session change state.
   *
   * @see MediaDrm.OnKeyStatusChangeListener
   */
  interface OnKeyStatusChangeListener {
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
        ExoMediaDrm mediaDrm,
        byte[] sessionId,
        List<KeyStatus> exoKeyInformation,
        boolean hasNewUsableKey);
  }

  /**
   * Called when a session expiration update occurs.
   *
   * @see MediaDrm.OnExpirationUpdateListener
   */
  interface OnExpirationUpdateListener {

    /**
     * Called when a session expiration update occurs, to inform the app about the change in
     * expiration time.
     *
     * @param mediaDrm The {@link ExoMediaDrm} object on which the event occurred.
     * @param sessionId The DRM session ID on which the event occurred
     * @param expirationTimeMs The new expiration time for the keys in the session. The time is in
     *     milliseconds, relative to the Unix epoch. A time of 0 indicates that the keys never
     *     expire.
     */
    void onExpirationUpdate(ExoMediaDrm mediaDrm, byte[] sessionId, long expirationTimeMs);
  }

  /**
   * Defines the status of a key.
   *
   * @see MediaDrm.KeyStatus
   */
  final class KeyStatus {

    private final int statusCode;
    private final byte[] keyId;

    /**
     * Creates an instance.
     *
     * @param statusCode The status code of the key, as defined by {@link
     *     MediaDrm.KeyStatus#getStatusCode()}.
     * @param keyId The ID of the key.
     */
    public KeyStatus(int statusCode, byte[] keyId) {
      this.statusCode = statusCode;
      this.keyId = keyId;
    }

    /** Returns the status of the key, as defined by {@link MediaDrm.KeyStatus#getStatusCode()}. */
    public int getStatusCode() {
      return statusCode;
    }

    /** Returns the ID of the key. */
    public byte[] getKeyId() {
      return keyId;
    }
  }

  /**
   * Contains data used to request keys from a license server.
   *
   * @see MediaDrm.KeyRequest
   */
  final class KeyRequest {

    /**
     * Key request types. One of {@link #REQUEST_TYPE_UNKNOWN}, {@link #REQUEST_TYPE_INITIAL},
     * {@link #REQUEST_TYPE_RENEWAL}, {@link #REQUEST_TYPE_RELEASE}, {@link #REQUEST_TYPE_NONE} or
     * {@link #REQUEST_TYPE_UPDATE}.
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef({
      REQUEST_TYPE_UNKNOWN,
      REQUEST_TYPE_INITIAL,
      REQUEST_TYPE_RENEWAL,
      REQUEST_TYPE_RELEASE,
      REQUEST_TYPE_NONE,
      REQUEST_TYPE_UPDATE,
    })
    public @interface RequestType {}

    /**
     * Value returned from {@link #getRequestType()} if the underlying key request does not specify
     * a type.
     */
    public static final int REQUEST_TYPE_UNKNOWN = Integer.MIN_VALUE;

    /** Key request type for an initial license request. */
    public static final int REQUEST_TYPE_INITIAL = MediaDrm.KeyRequest.REQUEST_TYPE_INITIAL;
    /** Key request type for license renewal. */
    public static final int REQUEST_TYPE_RENEWAL = MediaDrm.KeyRequest.REQUEST_TYPE_RENEWAL;
    /** Key request type for license release. */
    public static final int REQUEST_TYPE_RELEASE = MediaDrm.KeyRequest.REQUEST_TYPE_RELEASE;
    /**
     * Key request type if keys are already loaded and available for use. No license request is
     * necessary, and no key request data is returned.
     */
    public static final int REQUEST_TYPE_NONE = MediaDrm.KeyRequest.REQUEST_TYPE_NONE;
    /**
     * Key request type if keys have been loaded, but an additional license request is needed to
     * update their values.
     */
    public static final int REQUEST_TYPE_UPDATE = MediaDrm.KeyRequest.REQUEST_TYPE_UPDATE;

    private final byte[] data;
    private final String licenseServerUrl;
    private final @RequestType int requestType;

    /**
     * Creates an instance with {@link #REQUEST_TYPE_UNKNOWN}.
     *
     * @param data The opaque key request data.
     * @param licenseServerUrl The license server URL to which the request should be made.
     */
    public KeyRequest(byte[] data, String licenseServerUrl) {
      this(data, licenseServerUrl, REQUEST_TYPE_UNKNOWN);
    }

    /**
     * Creates an instance.
     *
     * @param data The opaque key request data.
     * @param licenseServerUrl The license server URL to which the request should be made.
     * @param requestType The type of the request, or {@link #REQUEST_TYPE_UNKNOWN}.
     */
    public KeyRequest(byte[] data, String licenseServerUrl, @RequestType int requestType) {
      this.data = data;
      this.licenseServerUrl = licenseServerUrl;
      this.requestType = requestType;
    }

    /** Returns the opaque key request data. */
    public byte[] getData() {
      return data;
    }

    /** Returns the URL of the license server to which the request should be made. */
    public String getLicenseServerUrl() {
      return licenseServerUrl;
    }

    /**
     * Returns the type of the request, or {@link #REQUEST_TYPE_UNKNOWN} if the underlying key
     * request does not specify a type. Note that when using a platform {@link MediaDrm} instance,
     * key requests only specify a type on API levels 23 and above.
     */
    public @RequestType int getRequestType() {
      return requestType;
    }
  }

  /**
   * Contains data to request a certificate from a provisioning server.
   *
   * @see MediaDrm.ProvisionRequest
   */
  final class ProvisionRequest {

    private final byte[] data;
    private final String defaultUrl;

    /**
     * Creates an instance.
     *
     * @param data The opaque provisioning request data.
     * @param defaultUrl The default URL of the provisioning server to which the request can be
     *     made, or the empty string if not known.
     */
    public ProvisionRequest(byte[] data, String defaultUrl) {
      this.data = data;
      this.defaultUrl = defaultUrl;
    }

    /** Returns the opaque provisioning request data. */
    public byte[] getData() {
      return data;
    }

    /**
     * Returns the default URL of the provisioning server to which the request can be made, or the
     * empty string if not known.
     */
    public String getDefaultUrl() {
      return defaultUrl;
    }
  }

  /**
   * Sets the listener for DRM events.
   *
   * <p>This is an optional method, and some implementations may only support it on certain Android
   * API levels.
   *
   * @param listener The listener to receive events, or {@code null} to stop receiving events.
   * @throws UnsupportedOperationException if the implementation doesn't support this method.
   * @see MediaDrm#setOnEventListener(MediaDrm.OnEventListener)
   */
  void setOnEventListener(@Nullable OnEventListener listener);

  /**
   * Sets the listener for key status change events.
   *
   * <p>This is an optional method, and some implementations may only support it on certain Android
   * API levels.
   *
   * @param listener The listener to receive events, or {@code null} to stop receiving events.
   * @throws UnsupportedOperationException if the implementation doesn't support this method.
   * @see MediaDrm#setOnKeyStatusChangeListener(MediaDrm.OnKeyStatusChangeListener, Handler)
   */
  void setOnKeyStatusChangeListener(@Nullable OnKeyStatusChangeListener listener);

  /**
   * Sets the listener for session expiration events.
   *
   * <p>This is an optional method, and some implementations may only support it on certain Android
   * API levels.
   *
   * @param listener The listener to receive events, or {@code null} to stop receiving events.
   * @throws UnsupportedOperationException if the implementation doesn't support this method.
   * @see MediaDrm#setOnExpirationUpdateListener(MediaDrm.OnExpirationUpdateListener, Handler)
   */
  void setOnExpirationUpdateListener(@Nullable OnExpirationUpdateListener listener);

  /**
   * Opens a new DRM session. A session ID is returned.
   *
   * @return The session ID.
   * @throws NotProvisionedException If provisioning is needed.
   * @throws ResourceBusyException If required resources are in use.
   * @throws MediaDrmException If the session could not be opened.
   */
  byte[] openSession() throws MediaDrmException;

  /**
   * Closes a DRM session.
   *
   * @param sessionId The ID of the session to close.
   */
  void closeSession(byte[] sessionId);

  /**
   * Sets the {@link PlayerId} of the player using a session.
   *
   * @param sessionId The ID of the session.
   * @param playerId The {@link PlayerId} of the player using the session.
   */
  default void setPlayerIdForSession(byte[] sessionId, PlayerId playerId) {}

  /**
   * Generates a key request.
   *
   * @param scope If {@code keyType} is {@link #KEY_TYPE_STREAMING} or {@link #KEY_TYPE_OFFLINE},
   *     the ID of the session that the keys will be provided to. If {@code keyType} is {@link
   *     #KEY_TYPE_RELEASE}, the {@code keySetId} of the keys to release.
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

  /**
   * Provides a key response for the last request to be generated using {@link #getKeyRequest}.
   *
   * @param scope If the request had type {@link #KEY_TYPE_STREAMING} or {@link #KEY_TYPE_OFFLINE},
   *     the ID of the session to provide the keys to. If {@code keyType} is {@link
   *     #KEY_TYPE_RELEASE}, the {@code keySetId} of the keys being released.
   * @param response The response data from the server.
   * @return If the request had type {@link #KEY_TYPE_OFFLINE}, the {@code keySetId} for the offline
   *     keys. An empty byte array or {@code null} may be returned for other cases.
   * @throws NotProvisionedException If the response indicates that provisioning is needed.
   * @throws DeniedByServerException If the response indicates that the server rejected the request.
   */
  @Nullable
  byte[] provideKeyResponse(byte[] scope, byte[] response)
      throws NotProvisionedException, DeniedByServerException;

  /**
   * Generates a provisioning request.
   *
   * @return The generated provisioning request.
   */
  ProvisionRequest getProvisionRequest();

  /**
   * Provides a provisioning response for the last request to be generated using {@link
   * #getProvisionRequest()}.
   *
   * @param response The response data from the server.
   * @throws DeniedByServerException If the response indicates that the server rejected the request.
   */
  void provideProvisionResponse(byte[] response) throws DeniedByServerException;

  /**
   * Returns the key status for a given session, as {name, value} pairs. Since DRM license policies
   * vary by vendor, the returned entries depend on the DRM plugin being used. Refer to your DRM
   * provider's documentation for more information.
   *
   * @param sessionId The ID of the session being queried.
   * @return The key status for the session.
   */
  Map<String, String> queryKeyStatus(byte[] sessionId);

  /**
   * Returns whether the given session requires use of a secure decoder for the given MIME type.
   * Assumes a license policy that requires the highest level of security supported by the session.
   *
   * @param sessionId The ID of the session.
   * @param mimeType The content MIME type to query.
   */
  boolean requiresSecureDecoder(byte[] sessionId, String mimeType);

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
   * Restores persisted offline keys into a session.
   *
   * @param sessionId The ID of the session into which the keys will be restored.
   * @param keySetId The {@code keySetId} of the keys to restore, as provided by the call to {@link
   *     #provideKeyResponse} that persisted them.
   */
  void restoreKeys(byte[] sessionId, byte[] keySetId);

  /**
   * Returns metrics data for this ExoMediaDrm instance, or {@code null} if metrics are unavailable.
   */
  @Nullable
  PersistableBundle getMetrics();

  /**
   * Returns the value of a string property. For standard property names, see {@link
   * MediaDrm#getPropertyString}.
   *
   * @param propertyName The property name.
   * @return The property value.
   * @throws IllegalArgumentException If the underlying DRM plugin does not support the property.
   */
  String getPropertyString(String propertyName);

  /**
   * Returns the value of a byte array property. For standard property names, see {@link
   * MediaDrm#getPropertyByteArray}.
   *
   * @param propertyName The property name.
   * @return The property value.
   * @throws IllegalArgumentException If the underlying DRM plugin does not support the property.
   */
  byte[] getPropertyByteArray(String propertyName);

  /**
   * Sets the value of a string property.
   *
   * @param propertyName The property name.
   * @param value The value.
   * @throws IllegalArgumentException If the underlying DRM plugin does not support the property.
   */
  void setPropertyString(String propertyName, String value);

  /**
   * Sets the value of a byte array property.
   *
   * @param propertyName The property name.
   * @param value The value.
   * @throws IllegalArgumentException If the underlying DRM plugin does not support the property.
   */
  void setPropertyByteArray(String propertyName, byte[] value);

  /**
   * Creates a {@link CryptoConfig} that can be passed to a compatible decoder to allow decryption
   * of protected content using the specified session.
   *
   * @param sessionId The ID of the session.
   * @return A {@link CryptoConfig} for the given session.
   * @throws MediaCryptoException If a {@link CryptoConfig} could not be created.
   */
  CryptoConfig createCryptoConfig(byte[] sessionId) throws MediaCryptoException;

  /**
   * Returns the {@link C.CryptoType type} of {@link CryptoConfig} instances returned by {@link
   * #createCryptoConfig}.
   */
  @C.CryptoType
  int getCryptoType();
}

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

import android.media.DeniedByServerException;
import android.media.MediaCryptoException;
import android.media.MediaDrm;
import android.media.NotProvisionedException;
import android.media.ResourceBusyException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Used to obtain keys for decrypting protected media streams. See {@link android.media.MediaDrm}.
 */
public interface ExoMediaDrm<T extends ExoMediaCrypto> {

  /**
   * @see android.media.MediaDrm.OnEventListener
   */
  interface OnEventListener<T extends ExoMediaCrypto> {
    /**
     * Called when an event occurs that requires the app to be notified
     *
     * @param mediaDrm the {@link ExoMediaDrm} object on which the event occurred.
     * @param sessionId the DRM session ID on which the event occurred
     * @param event indicates the event type
     * @param extra an secondary error code
     * @param data optional byte array of data that may be associated with the event
     */
    void onEvent(ExoMediaDrm<? extends T> mediaDrm, byte[] sessionId, int event, int extra,
        byte[] data);
  }

  /**
   * @see android.media.MediaDrm.KeyRequest
   */
  interface KeyRequest {
    byte[] getData();
    String getDefaultUrl();
  }

  /**
   * @see android.media.MediaDrm.ProvisionRequest
   */
  interface ProvisionRequest {
    byte[] getData();
    String getDefaultUrl();
  }

  /**
   * @see MediaDrm#setOnEventListener(MediaDrm.OnEventListener)
   */
  void setOnEventListener(OnEventListener<? super T> listener);

  /**
   * @see MediaDrm#openSession()
   */
  byte[] openSession() throws NotProvisionedException, ResourceBusyException;

  /**
   * @see MediaDrm#closeSession(byte[])
   */
  void closeSession(byte[] sessionId);

  /**
   * @see MediaDrm#getKeyRequest(byte[], byte[], String, int, HashMap)
   */
  KeyRequest getKeyRequest(byte[] scope, byte[] init, String mimeType, int keyType,
      HashMap<String, String> optionalParameters) throws NotProvisionedException;

  /**
   * @see MediaDrm#provideKeyResponse(byte[], byte[])
   */
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
   * @param uuid The UUID of the crypto scheme.
   * @param initData Opaque initialization data specific to the crypto scheme.
   * @return An object extends {@link ExoMediaCrypto}, using opaque crypto scheme specific data.
   * @throws MediaCryptoException
   */
  T createMediaCrypto(UUID uuid, byte[] initData) throws MediaCryptoException;

}

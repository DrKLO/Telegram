/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.media.MediaDrmException;
import android.os.PersistableBundle;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.decoder.CryptoConfig;
import com.google.android.exoplayer2.util.Util;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** An {@link ExoMediaDrm} that does not support any protection schemes. */
@RequiresApi(18)
public final class DummyExoMediaDrm implements ExoMediaDrm {

  /** Returns a new instance. */
  public static DummyExoMediaDrm getInstance() {
    return new DummyExoMediaDrm();
  }

  @Override
  public void setOnEventListener(@Nullable OnEventListener listener) {
    // Do nothing.
  }

  @Override
  public void setOnKeyStatusChangeListener(@Nullable OnKeyStatusChangeListener listener) {
    // Do nothing.
  }

  @Override
  public void setOnExpirationUpdateListener(@Nullable OnExpirationUpdateListener listener) {
    // Do nothing.
  }

  @Override
  public byte[] openSession() throws MediaDrmException {
    throw new MediaDrmException("Attempting to open a session using a dummy ExoMediaDrm.");
  }

  @Override
  public void closeSession(byte[] sessionId) {
    // Do nothing.
  }

  @Override
  public KeyRequest getKeyRequest(
      byte[] scope,
      @Nullable List<DrmInitData.SchemeData> schemeDatas,
      int keyType,
      @Nullable HashMap<String, String> optionalParameters) {
    // Should not be invoked. No session should exist.
    throw new IllegalStateException();
  }

  @Override
  @Nullable
  public byte[] provideKeyResponse(byte[] scope, byte[] response) {
    // Should not be invoked. No session should exist.
    throw new IllegalStateException();
  }

  @Override
  public ProvisionRequest getProvisionRequest() {
    // Should not be invoked. No provision should be required.
    throw new IllegalStateException();
  }

  @Override
  public void provideProvisionResponse(byte[] response) {
    // Should not be invoked. No provision should be required.
    throw new IllegalStateException();
  }

  @Override
  public Map<String, String> queryKeyStatus(byte[] sessionId) {
    // Should not be invoked. No session should exist.
    throw new IllegalStateException();
  }

  @Override
  public boolean requiresSecureDecoder(byte[] sessionId, String mimeType) {
    // Should not be invoked. No session should exist.
    throw new IllegalStateException();
  }

  @Override
  public void acquire() {
    // Do nothing.
  }

  @Override
  public void release() {
    // Do nothing.
  }

  @Override
  public void restoreKeys(byte[] sessionId, byte[] keySetId) {
    // Should not be invoked. No session should exist.
    throw new IllegalStateException();
  }

  @Override
  @Nullable
  public PersistableBundle getMetrics() {
    return null;
  }

  @Override
  public String getPropertyString(String propertyName) {
    return "";
  }

  @Override
  public byte[] getPropertyByteArray(String propertyName) {
    return Util.EMPTY_BYTE_ARRAY;
  }

  @Override
  public void setPropertyString(String propertyName, String value) {
    // Do nothing.
  }

  @Override
  public void setPropertyByteArray(String propertyName, byte[] value) {
    // Do nothing.
  }

  @Override
  public CryptoConfig createCryptoConfig(byte[] sessionId) {
    // Should not be invoked. No session should exist.
    throw new IllegalStateException();
  }

  @Override
  public @C.CryptoType int getCryptoType() {
    return C.CRYPTO_TYPE_UNSUPPORTED;
  }
}

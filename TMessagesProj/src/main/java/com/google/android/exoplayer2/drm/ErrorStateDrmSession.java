/*
 * Copyright (C) 2017 The Android Open Source Project
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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Assertions;
import java.util.Map;

/** A {@link DrmSession} that's in a terminal error state. */
public final class ErrorStateDrmSession<T extends ExoMediaCrypto> implements DrmSession<T> {

  private final DrmSessionException error;

  public ErrorStateDrmSession(DrmSessionException error) {
    this.error = Assertions.checkNotNull(error);
  }

  @Override
  public int getState() {
    return STATE_ERROR;
  }

  @Override
  public boolean playClearSamplesWithoutKeys() {
    return false;
  }

  @Override
  @Nullable
  public DrmSessionException getError() {
    return error;
  }

  @Override
  @Nullable
  public T getMediaCrypto() {
    return null;
  }

  @Override
  @Nullable
  public Map<String, String> queryKeyStatus() {
    return null;
  }

  @Override
  @Nullable
  public byte[] getOfflineLicenseKeySetId() {
    return null;
  }

  @Override
  public void acquire() {
    // Do nothing.
  }

  @Override
  public void release() {
    // Do nothing.
  }
}

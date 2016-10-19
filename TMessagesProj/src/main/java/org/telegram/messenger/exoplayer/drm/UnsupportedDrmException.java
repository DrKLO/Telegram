/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.drm;

/**
 * Thrown when the requested DRM scheme is not supported.
 */
public final class UnsupportedDrmException extends Exception {

  /**
   * The requested DRM scheme is unsupported by the device.
   */
  public static final int REASON_UNSUPPORTED_SCHEME = 1;
  /**
   * There device advertises support for the requested DRM scheme, but there was an error
   * instantiating it. The cause can be retrieved using {@link #getCause()}.
   */
  public static final int REASON_INSTANTIATION_ERROR = 2;

  public final int reason;

  public UnsupportedDrmException(int reason) {
    this.reason = reason;
  }

  public UnsupportedDrmException(int reason, Exception cause) {
    super(cause);
    this.reason = reason;
  }

}

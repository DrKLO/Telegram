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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Thrown when the requested DRM scheme is not supported. */
public final class UnsupportedDrmException extends Exception {

  /**
   * The reason for the exception. One of {@link #REASON_UNSUPPORTED_SCHEME} or {@link
   * #REASON_INSTANTIATION_ERROR}.
   */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({REASON_UNSUPPORTED_SCHEME, REASON_INSTANTIATION_ERROR})
  public @interface Reason {}
  /** The requested DRM scheme is unsupported by the device. */
  public static final int REASON_UNSUPPORTED_SCHEME = 1;
  /**
   * There device advertises support for the requested DRM scheme, but there was an error
   * instantiating it. The cause can be retrieved using {@link #getCause()}.
   */
  public static final int REASON_INSTANTIATION_ERROR = 2;

  /** Either {@link #REASON_UNSUPPORTED_SCHEME} or {@link #REASON_INSTANTIATION_ERROR}. */
  public final @Reason int reason;

  /**
   * @param reason {@link #REASON_UNSUPPORTED_SCHEME} or {@link #REASON_INSTANTIATION_ERROR}.
   */
  public UnsupportedDrmException(@Reason int reason) {
    this.reason = reason;
  }

  /**
   * @param reason {@link #REASON_UNSUPPORTED_SCHEME} or {@link #REASON_INSTANTIATION_ERROR}.
   * @param cause The cause of this exception.
   */
  public UnsupportedDrmException(@Reason int reason, Exception cause) {
    super(cause);
    this.reason = reason;
  }
}

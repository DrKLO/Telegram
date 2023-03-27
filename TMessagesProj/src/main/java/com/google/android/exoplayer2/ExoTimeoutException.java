/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2;

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

/** A timeout of an operation on the ExoPlayer playback thread. */
public final class ExoTimeoutException extends RuntimeException {

  /**
   * The operation which produced the timeout error. One of {@link #TIMEOUT_OPERATION_RELEASE},
   * {@link #TIMEOUT_OPERATION_SET_FOREGROUND_MODE}, {@link #TIMEOUT_OPERATION_DETACH_SURFACE} or
   * {@link #TIMEOUT_OPERATION_UNDEFINED}. Note that new operations may be added in the future and
   * error handling should handle unknown operation values.
   */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({
    TIMEOUT_OPERATION_UNDEFINED,
    TIMEOUT_OPERATION_RELEASE,
    TIMEOUT_OPERATION_SET_FOREGROUND_MODE,
    TIMEOUT_OPERATION_DETACH_SURFACE
  })
  public @interface TimeoutOperation {}

  /** The operation where this error occurred is not defined. */
  public static final int TIMEOUT_OPERATION_UNDEFINED = 0;
  /** The error occurred in {@link Player#release}. */
  public static final int TIMEOUT_OPERATION_RELEASE = 1;
  /** The error occurred in {@link ExoPlayer#setForegroundMode}. */
  public static final int TIMEOUT_OPERATION_SET_FOREGROUND_MODE = 2;
  /** The error occurred while detaching a surface from the player. */
  public static final int TIMEOUT_OPERATION_DETACH_SURFACE = 3;

  /** The operation on the ExoPlayer playback thread that timed out. */
  public final @TimeoutOperation int timeoutOperation;

  /**
   * Creates the timeout exception.
   *
   * @param timeoutOperation The {@link TimeoutOperation operation} that produced the timeout.
   */
  public ExoTimeoutException(@TimeoutOperation int timeoutOperation) {
    super(getErrorMessage(timeoutOperation));
    this.timeoutOperation = timeoutOperation;
  }

  private static String getErrorMessage(@TimeoutOperation int timeoutOperation) {
    switch (timeoutOperation) {
      case TIMEOUT_OPERATION_RELEASE:
        return "Player release timed out.";
      case TIMEOUT_OPERATION_SET_FOREGROUND_MODE:
        return "Setting foreground mode timed out.";
      case TIMEOUT_OPERATION_DETACH_SURFACE:
        return "Detaching surface timed out.";
      case TIMEOUT_OPERATION_UNDEFINED:
      default:
        return "Undefined timeout.";
    }
  }
}

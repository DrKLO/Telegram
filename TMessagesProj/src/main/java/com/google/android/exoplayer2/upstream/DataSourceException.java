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
package com.google.android.exoplayer2.upstream;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.PlaybackException;
import java.io.IOException;

/** Used to specify reason of a DataSource error. */
public class DataSourceException extends IOException {

  /**
   * Returns whether the given {@link IOException} was caused by a {@link DataSourceException} whose
   * {@link #reason} is {@link PlaybackException#ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE} in its
   * cause stack.
   */
  public static boolean isCausedByPositionOutOfRange(IOException e) {
    @Nullable Throwable cause = e;
    while (cause != null) {
      if (cause instanceof DataSourceException) {
        int reason = ((DataSourceException) cause).reason;
        if (reason == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE) {
          return true;
        }
      }
      cause = cause.getCause();
    }
    return false;
  }

  /**
   * Indicates that the {@link DataSpec#position starting position} of the request was outside the
   * bounds of the data.
   *
   * @deprecated Use {@link PlaybackException#ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE}.
   */
  @Deprecated
  public static final int POSITION_OUT_OF_RANGE =
      PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE;

  /**
   * The reason of this {@link DataSourceException}, should be one of the {@code ERROR_CODE_IO_*} in
   * {@link PlaybackException.ErrorCode}.
   */
  public final @PlaybackException.ErrorCode int reason;

  /**
   * Constructs a DataSourceException.
   *
   * @param reason Reason of the error, should be one of the {@code ERROR_CODE_IO_*} in {@link
   *     PlaybackException.ErrorCode}.
   */
  public DataSourceException(@PlaybackException.ErrorCode int reason) {
    this.reason = reason;
  }

  /**
   * Constructs a DataSourceException.
   *
   * @param cause The error cause.
   * @param reason Reason of the error, should be one of the {@code ERROR_CODE_IO_*} in {@link
   *     PlaybackException.ErrorCode}.
   */
  public DataSourceException(@Nullable Throwable cause, @PlaybackException.ErrorCode int reason) {
    super(cause);
    this.reason = reason;
  }

  /**
   * Constructs a DataSourceException.
   *
   * @param message The error message.
   * @param reason Reason of the error, should be one of the {@code ERROR_CODE_IO_*} in {@link
   *     PlaybackException.ErrorCode}.
   */
  public DataSourceException(@Nullable String message, @PlaybackException.ErrorCode int reason) {
    super(message);
    this.reason = reason;
  }

  /**
   * Constructs a DataSourceException.
   *
   * @param message The error message.
   * @param cause The error cause.
   * @param reason Reason of the error, should be one of the {@code ERROR_CODE_IO_*} in {@link
   *     PlaybackException.ErrorCode}.
   */
  public DataSourceException(
      @Nullable String message,
      @Nullable Throwable cause,
      @PlaybackException.ErrorCode int reason) {
    super(message, cause);
    this.reason = reason;
  }
}

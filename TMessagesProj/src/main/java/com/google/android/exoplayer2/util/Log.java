/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.util;

import android.text.TextUtils;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.UnknownHostException;

/** Wrapper around {@link android.util.Log} which allows to set the log level. */
public final class Log {

  /**
   * Log level for ExoPlayer logcat logging. One of {@link #LOG_LEVEL_ALL}, {@link #LOG_LEVEL_INFO},
   * {@link #LOG_LEVEL_WARNING}, {@link #LOG_LEVEL_ERROR} or {@link #LOG_LEVEL_OFF}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({LOG_LEVEL_ALL, LOG_LEVEL_INFO, LOG_LEVEL_WARNING, LOG_LEVEL_ERROR, LOG_LEVEL_OFF})
  @interface LogLevel {}
  /** Log level to log all messages. */
  public static final int LOG_LEVEL_ALL = 0;
  /** Log level to only log informative, warning and error messages. */
  public static final int LOG_LEVEL_INFO = 1;
  /** Log level to only log warning and error messages. */
  public static final int LOG_LEVEL_WARNING = 2;
  /** Log level to only log error messages. */
  public static final int LOG_LEVEL_ERROR = 3;
  /** Log level to disable all logging. */
  public static final int LOG_LEVEL_OFF = Integer.MAX_VALUE;

  private static int logLevel = LOG_LEVEL_ALL;
  private static boolean logStackTraces = true;

  private Log() {}

  /** Returns current {@link LogLevel} for ExoPlayer logcat logging. */
  public static @LogLevel int getLogLevel() {
    return logLevel;
  }

  /** Returns whether stack traces of {@link Throwable}s will be logged to logcat. */
  public boolean getLogStackTraces() {
    return logStackTraces;
  }

  /**
   * Sets the {@link LogLevel} for ExoPlayer logcat logging.
   *
   * @param logLevel The new {@link LogLevel}.
   */
  public static void setLogLevel(@LogLevel int logLevel) {
    Log.logLevel = logLevel;
  }

  /**
   * Sets whether stack traces of {@link Throwable}s will be logged to logcat. Stack trace logging
   * is enabled by default.
   *
   * @param logStackTraces Whether stack traces will be logged.
   */
  public static void setLogStackTraces(boolean logStackTraces) {
    Log.logStackTraces = logStackTraces;
  }

  /** @see android.util.Log#d(String, String) */
  public static void d(String tag, String message) {
    if (logLevel == LOG_LEVEL_ALL) {
      android.util.Log.d(tag, message);
    }
  }

  /** @see android.util.Log#d(String, String, Throwable) */
  public static void d(String tag, String message, @Nullable Throwable throwable) {
    d(tag, appendThrowableString(message, throwable));
  }

  /** @see android.util.Log#i(String, String) */
  public static void i(String tag, String message) {
    if (logLevel <= LOG_LEVEL_INFO) {
      android.util.Log.i(tag, message);
    }
  }

  /** @see android.util.Log#i(String, String, Throwable) */
  public static void i(String tag, String message, @Nullable Throwable throwable) {
    i(tag, appendThrowableString(message, throwable));
  }

  /** @see android.util.Log#w(String, String) */
  public static void w(String tag, String message) {
    if (logLevel <= LOG_LEVEL_WARNING) {
      android.util.Log.w(tag, message);
    }
  }

  /** @see android.util.Log#w(String, String, Throwable) */
  public static void w(String tag, String message, @Nullable Throwable throwable) {
    w(tag, appendThrowableString(message, throwable));
  }

  /** @see android.util.Log#e(String, String) */
  public static void e(String tag, String message) {
    if (logLevel <= LOG_LEVEL_ERROR) {
      android.util.Log.e(tag, message);
    }
  }

  /** @see android.util.Log#e(String, String, Throwable) */
  public static void e(String tag, String message, @Nullable Throwable throwable) {
    e(tag, appendThrowableString(message, throwable));
  }

  /**
   * Returns a string representation of a {@link Throwable} suitable for logging, taking into
   * account whether {@link #setLogStackTraces(boolean)} stack trace logging} is enabled.
   *
   * <p>Stack trace logging may be unconditionally suppressed for some expected failure modes (e.g.,
   * {@link Throwable Throwables} that are expected if the device doesn't have network connectivity)
   * to avoid log spam.
   *
   * @param throwable The {@link Throwable}.
   * @return The string representation of the {@link Throwable}.
   */
  @Nullable
  public static String getThrowableString(@Nullable Throwable throwable) {
    if (throwable == null) {
      return null;
    } else if (isCausedByUnknownHostException(throwable)) {
      // UnknownHostException implies the device doesn't have network connectivity.
      // UnknownHostException.getMessage() may return a string that's more verbose than desired for
      // logging an expected failure mode. Conversely, android.util.Log.getStackTraceString has
      // special handling to return the empty string, which can result in logging that doesn't
      // indicate the failure mode at all. Hence we special case this exception to always return a
      // concise but useful message.
      return "UnknownHostException (no network)";
    } else if (!logStackTraces) {
      return throwable.getMessage();
    } else {
      return android.util.Log.getStackTraceString(throwable).trim().replace("\t", "    ");
    }
  }

  private static String appendThrowableString(String message, @Nullable Throwable throwable) {
    @Nullable String throwableString = getThrowableString(throwable);
    if (!TextUtils.isEmpty(throwableString)) {
      message += "\n  " + throwableString.replace("\n", "\n  ") + '\n';
    }
    return message;
  }

  private static boolean isCausedByUnknownHostException(@Nullable Throwable throwable) {
    while (throwable != null) {
      if (throwable instanceof UnknownHostException) {
        return true;
      }
      throwable = throwable.getCause();
    }
    return false;
  }
}

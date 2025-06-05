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

import static java.lang.annotation.ElementType.TYPE_USE;

import android.text.TextUtils;
import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.UnknownHostException;
import org.checkerframework.dataflow.qual.Pure;

/**
 * Wrapper around {@link android.util.Log} which allows to set the log level and to specify a custom
 * log output.
 */
public final class Log {

  /**
   * Log level for ExoPlayer logcat logging. One of {@link #LOG_LEVEL_ALL}, {@link #LOG_LEVEL_INFO},
   * {@link #LOG_LEVEL_WARNING}, {@link #LOG_LEVEL_ERROR} or {@link #LOG_LEVEL_OFF}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({LOG_LEVEL_ALL, LOG_LEVEL_INFO, LOG_LEVEL_WARNING, LOG_LEVEL_ERROR, LOG_LEVEL_OFF})
  public @interface LogLevel {}
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

  /**
   * Interface for a logger that can output messages with a tag.
   *
   * <p>Use {@link #DEFAULT} to output to {@link android.util.Log}.
   */
  public interface Logger {

    /** The default instance logging to {@link android.util.Log}. */
    Logger DEFAULT =
        new Logger() {
          @Override
          public void d(String tag, String message) {
            android.util.Log.d(tag, message);
          }

          @Override
          public void i(String tag, String message) {
            android.util.Log.i(tag, message);
          }

          @Override
          public void w(String tag, String message) {
            android.util.Log.w(tag, message);
          }

          @Override
          public void e(String tag, String message) {
            android.util.Log.e(tag, message);
          }
        };

    /**
     * Logs a debug-level message.
     *
     * @param tag The tag of the message.
     * @param message The message.
     */
    void d(String tag, String message);

    /**
     * Logs an information-level message.
     *
     * @param tag The tag of the message.
     * @param message The message.
     */
    void i(String tag, String message);

    /**
     * Logs a warning-level message.
     *
     * @param tag The tag of the message.
     * @param message The message.
     */
    void w(String tag, String message);

    /**
     * Logs an error-level message.
     *
     * @param tag The tag of the message.
     * @param message The message.
     */
    void e(String tag, String message);
  }

  private static final Object lock = new Object();

  @GuardedBy("lock")
  private static int logLevel = LOG_LEVEL_ALL;

  @GuardedBy("lock")
  private static boolean logStackTraces = true;

  @GuardedBy("lock")
  private static Logger logger = Logger.DEFAULT;

  private Log() {}

  /** Returns current {@link LogLevel} for ExoPlayer logcat logging. */
  @Pure
  public static @LogLevel int getLogLevel() {
    synchronized (lock) {
      return logLevel;
    }
  }

  /**
   * Sets the {@link LogLevel} for ExoPlayer logcat logging.
   *
   * @param logLevel The new {@link LogLevel}.
   */
  public static void setLogLevel(@LogLevel int logLevel) {
    synchronized (lock) {
      Log.logLevel = logLevel;
    }
  }

  /**
   * Sets whether stack traces of {@link Throwable}s will be logged to logcat. Stack trace logging
   * is enabled by default.
   *
   * @param logStackTraces Whether stack traces will be logged.
   */
  public static void setLogStackTraces(boolean logStackTraces) {
    synchronized (lock) {
      Log.logStackTraces = logStackTraces;
    }
  }

  /**
   * Sets a custom {@link Logger} as the output.
   *
   * @param logger The {@link Logger}.
   */
  public static void setLogger(Logger logger) {
    synchronized (lock) {
      Log.logger = logger;
    }
  }

  /**
   * @see android.util.Log#d(String, String)
   */
  @Pure
  public static void d(@Size(max = 23) String tag, String message) {
    synchronized (lock) {
      if (logLevel == LOG_LEVEL_ALL) {
        logger.d(tag, message);
      }
    }
  }

  /**
   * @see android.util.Log#d(String, String, Throwable)
   */
  @Pure
  public static void d(@Size(max = 23) String tag, String message, @Nullable Throwable throwable) {
    d(tag, appendThrowableString(message, throwable));
  }

  /**
   * @see android.util.Log#i(String, String)
   */
  @Pure
  public static void i(@Size(max = 23) String tag, String message) {
    synchronized (lock) {
      if (logLevel <= LOG_LEVEL_INFO) {
        logger.i(tag, message);
      }
    }
  }

  /**
   * @see android.util.Log#i(String, String, Throwable)
   */
  @Pure
  public static void i(@Size(max = 23) String tag, String message, @Nullable Throwable throwable) {
    i(tag, appendThrowableString(message, throwable));
  }

  /**
   * @see android.util.Log#w(String, String)
   */
  @Pure
  public static void w(@Size(max = 23) String tag, String message) {
    synchronized (lock) {
      if (logLevel <= LOG_LEVEL_WARNING) {
        logger.w(tag, message);
      }
    }
  }

  /**
   * @see android.util.Log#w(String, String, Throwable)
   */
  @Pure
  public static void w(@Size(max = 23) String tag, String message, @Nullable Throwable throwable) {
    w(tag, appendThrowableString(message, throwable));
  }

  /**
   * @see android.util.Log#e(String, String)
   */
  @Pure
  public static void e(@Size(max = 23) String tag, String message) {
    synchronized (lock) {
      if (logLevel <= LOG_LEVEL_ERROR) {
        logger.e(tag, message);
      }
    }
  }

  /**
   * @see android.util.Log#e(String, String, Throwable)
   */
  @Pure
  public static void e(@Size(max = 23) String tag, String message, @Nullable Throwable throwable) {
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
  @Pure
  public static String getThrowableString(@Nullable Throwable throwable) {
    synchronized (lock) {
      if (throwable == null) {
        return null;
      } else if (isCausedByUnknownHostException(throwable)) {
        // UnknownHostException implies the device doesn't have network connectivity.
        // UnknownHostException.getMessage() may return a string that's more verbose than desired
        // for
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
  }

  @Pure
  private static String appendThrowableString(String message, @Nullable Throwable throwable) {
    @Nullable String throwableString = getThrowableString(throwable);
    if (!TextUtils.isEmpty(throwableString)) {
      message += "\n  " + throwableString.replace("\n", "\n  ") + '\n';
    }
    return message;
  }

  @Pure
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

/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import androidx.annotation.Nullable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.EnumSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.webrtc.Loggable;

/**
 * Java wrapper for WebRTC logging. Logging defaults to java.util.logging.Logger, but a custom
 * logger implementing the Loggable interface can be injected along with a Severity. All subsequent
 * log messages will then be redirected to the injected Loggable, except those with a severity lower
 * than the specified severity, which will be discarded.
 *
 * It is also possible to switch to native logging (rtc::LogMessage) if one of the following static
 * functions are called from the app:
 * - Logging.enableLogThreads
 * - Logging.enableLogTimeStamps
 * - Logging.enableLogToDebugOutput
 *
 * The priority goes:
 * 1. Injected loggable
 * 2. Native logging
 * 3. Fallback logging.
 * Only one method will be used at a time.
 *
 * Injecting a Loggable or using any of the enable... methods requires that the native library is
 * loaded, using PeerConnectionFactory.initialize.
 */
public class Logging {
  private static final Logger fallbackLogger = createFallbackLogger();
  private static volatile boolean loggingEnabled;
  @Nullable private static Loggable loggable;
  private static Severity loggableSeverity;

  private static Logger createFallbackLogger() {
    final Logger fallbackLogger = Logger.getLogger("org.webrtc.Logging");
    fallbackLogger.setLevel(Level.ALL);
    return fallbackLogger;
  }

  static void injectLoggable(Loggable injectedLoggable, Severity severity) {
    if (injectedLoggable != null) {
      loggable = injectedLoggable;
      loggableSeverity = severity;
    }
  }

  static void deleteInjectedLoggable() {
    loggable = null;
  }

  // TODO(solenberg): Remove once dependent projects updated.
  @Deprecated
  public enum TraceLevel {
    TRACE_NONE(0x0000),
    TRACE_STATEINFO(0x0001),
    TRACE_WARNING(0x0002),
    TRACE_ERROR(0x0004),
    TRACE_CRITICAL(0x0008),
    TRACE_APICALL(0x0010),
    TRACE_DEFAULT(0x00ff),
    TRACE_MODULECALL(0x0020),
    TRACE_MEMORY(0x0100),
    TRACE_TIMER(0x0200),
    TRACE_STREAM(0x0400),
    TRACE_DEBUG(0x0800),
    TRACE_INFO(0x1000),
    TRACE_TERSEINFO(0x2000),
    TRACE_ALL(0xffff);

    public final int level;
    TraceLevel(int level) {
      this.level = level;
    }
  }

  // Keep in sync with webrtc/rtc_base/logging.h:LoggingSeverity.
  public enum Severity { LS_VERBOSE, LS_INFO, LS_WARNING, LS_ERROR, LS_NONE }

  public static void enableLogThreads() {
    nativeEnableLogThreads();
  }

  public static void enableLogTimeStamps() {
    nativeEnableLogTimeStamps();
  }

  // TODO(solenberg): Remove once dependent projects updated.
  @Deprecated
  public static void enableTracing(String path, EnumSet<TraceLevel> levels) {}

  // Enable diagnostic logging for messages of `severity` to the platform debug
  // output. On Android, the output will be directed to Logcat.
  // Note: this function starts collecting the output of the RTC_LOG() macros.
  // TODO(bugs.webrtc.org/8491): Remove NoSynchronizedMethodCheck suppression.
  @SuppressWarnings("NoSynchronizedMethodCheck")
  public static synchronized void enableLogToDebugOutput(Severity severity) {
    if (loggable != null) {
      throw new IllegalStateException(
          "Logging to native debug output not supported while Loggable is injected. "
          + "Delete the Loggable before calling this method.");
    }
    nativeEnableLogToDebugOutput(severity.ordinal());
    loggingEnabled = true;
  }

  public static void log(Severity severity, String tag, String message) {
    if (tag == null || message == null) {
      throw new IllegalArgumentException("Logging tag or message may not be null.");
    }
    if (loggable != null) {
      // Filter log messages below loggableSeverity.
      if (severity.ordinal() < loggableSeverity.ordinal()) {
        return;
      }
      loggable.onLogMessage(message, severity, tag);
      return;
    }

    // Try native logging if no loggable is injected.
    if (loggingEnabled) {
      nativeLog(severity.ordinal(), tag, message);
      return;
    }

    // Fallback to system log.
    Level level;
    switch (severity) {
      case LS_ERROR:
        level = Level.SEVERE;
        break;
      case LS_WARNING:
        level = Level.WARNING;
        break;
      case LS_INFO:
        level = Level.INFO;
        break;
      default:
        level = Level.FINE;
        break;
    }
    fallbackLogger.log(level, tag + ": " + message);
  }

  public static void d(String tag, String message) {
    log(Severity.LS_INFO, tag, message);
  }

  public static void e(String tag, String message) {
    log(Severity.LS_ERROR, tag, message);
  }

  public static void w(String tag, String message) {
    log(Severity.LS_WARNING, tag, message);
  }

  public static void e(String tag, String message, Throwable e) {
    log(Severity.LS_ERROR, tag, message);
    log(Severity.LS_ERROR, tag, e.toString());
    log(Severity.LS_ERROR, tag, getStackTraceString(e));
  }

  public static void w(String tag, String message, Throwable e) {
    log(Severity.LS_WARNING, tag, message);
    log(Severity.LS_WARNING, tag, e.toString());
    log(Severity.LS_WARNING, tag, getStackTraceString(e));
  }

  public static void v(String tag, String message) {
    log(Severity.LS_VERBOSE, tag, message);
  }

  private static String getStackTraceString(Throwable e) {
    if (e == null) {
      return "";
    }

    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    e.printStackTrace(pw);
    return sw.toString();
  }

  private static native void nativeEnableLogToDebugOutput(int nativeSeverity);
  private static native void nativeEnableLogThreads();
  private static native void nativeEnableLogTimeStamps();
  private static native void nativeLog(int severity, String tag, String message);
}

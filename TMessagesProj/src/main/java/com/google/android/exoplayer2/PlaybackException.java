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

import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.text.TextUtils;
import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Thrown when a non locally recoverable playback failure occurs. */
public class PlaybackException extends Exception implements Bundleable {

  /**
   * Codes that identify causes of player errors.
   *
   * <p>This list of errors may be extended in future versions, and {@link Player} implementations
   * may define custom error codes.
   */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef(
      open = true,
      value = {
        ERROR_CODE_UNSPECIFIED,
        ERROR_CODE_REMOTE_ERROR,
        ERROR_CODE_BEHIND_LIVE_WINDOW,
        ERROR_CODE_TIMEOUT,
        ERROR_CODE_FAILED_RUNTIME_CHECK,
        ERROR_CODE_IO_UNSPECIFIED,
        ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        ERROR_CODE_IO_BAD_HTTP_STATUS,
        ERROR_CODE_IO_FILE_NOT_FOUND,
        ERROR_CODE_IO_NO_PERMISSION,
        ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
        ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
        ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        ERROR_CODE_DECODER_INIT_FAILED,
        ERROR_CODE_DECODER_QUERY_FAILED,
        ERROR_CODE_DECODING_FAILED,
        ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        ERROR_CODE_DRM_UNSPECIFIED,
        ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
        ERROR_CODE_DRM_PROVISIONING_FAILED,
        ERROR_CODE_DRM_CONTENT_ERROR,
        ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
        ERROR_CODE_DRM_DISALLOWED_OPERATION,
        ERROR_CODE_DRM_SYSTEM_ERROR,
        ERROR_CODE_DRM_DEVICE_REVOKED,
        ERROR_CODE_DRM_LICENSE_EXPIRED
      })
  public @interface ErrorCode {}

  // Miscellaneous errors (1xxx).

  /** Caused by an error whose cause could not be identified. */
  public static final int ERROR_CODE_UNSPECIFIED = 1000;
  /**
   * Caused by an unidentified error in a remote Player, which is a Player that runs on a different
   * host or process.
   */
  public static final int ERROR_CODE_REMOTE_ERROR = 1001;
  /** Caused by the loading position falling behind the sliding window of available live content. */
  public static final int ERROR_CODE_BEHIND_LIVE_WINDOW = 1002;
  /** Caused by a generic timeout. */
  public static final int ERROR_CODE_TIMEOUT = 1003;
  /**
   * Caused by a failed runtime check.
   *
   * <p>This can happen when the application fails to comply with the player's API requirements (for
   * example, by passing invalid arguments), or when the player reaches an invalid state.
   */
  public static final int ERROR_CODE_FAILED_RUNTIME_CHECK = 1004;

  // Input/Output errors (2xxx).

  /** Caused by an Input/Output error which could not be identified. */
  public static final int ERROR_CODE_IO_UNSPECIFIED = 2000;
  /**
   * Caused by a network connection failure.
   *
   * <p>The following is a non-exhaustive list of possible reasons:
   *
   * <ul>
   *   <li>There is no network connectivity (you can check this by querying {@link
   *       ConnectivityManager#getActiveNetwork}).
   *   <li>The URL's domain is misspelled or does not exist.
   *   <li>The target host is unreachable.
   *   <li>The server unexpectedly closes the connection.
   * </ul>
   */
  public static final int ERROR_CODE_IO_NETWORK_CONNECTION_FAILED = 2001;
  /** Caused by a network timeout, meaning the server is taking too long to fulfill a request. */
  public static final int ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT = 2002;
  /**
   * Caused by a server returning a resource with an invalid "Content-Type" HTTP header value.
   *
   * <p>For example, this can happen when the player is expecting a piece of media, but the server
   * returns a paywall HTML page, with content type "text/html".
   */
  public static final int ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE = 2003;
  /** Caused by an HTTP server returning an unexpected HTTP response status code. */
  public static final int ERROR_CODE_IO_BAD_HTTP_STATUS = 2004;
  /** Caused by a non-existent file. */
  public static final int ERROR_CODE_IO_FILE_NOT_FOUND = 2005;
  /**
   * Caused by lack of permission to perform an IO operation. For example, lack of permission to
   * access internet or external storage.
   */
  public static final int ERROR_CODE_IO_NO_PERMISSION = 2006;
  /**
   * Caused by the player trying to access cleartext HTTP traffic (meaning http:// rather than
   * https://) when the app's Network Security Configuration does not permit it.
   *
   * <p>See <a href="https://exoplayer.dev/issues/cleartext-not-permitted">this corresponding
   * troubleshooting topic</a>.
   */
  public static final int ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED = 2007;
  /** Caused by reading data out of the data bound. */
  public static final int ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE = 2008;

  // Content parsing errors (3xxx).

  /** Caused by a parsing error associated with a media container format bitstream. */
  public static final int ERROR_CODE_PARSING_CONTAINER_MALFORMED = 3001;
  /**
   * Caused by a parsing error associated with a media manifest. Examples of a media manifest are a
   * DASH or a SmoothStreaming manifest, or an HLS playlist.
   */
  public static final int ERROR_CODE_PARSING_MANIFEST_MALFORMED = 3002;
  /**
   * Caused by attempting to extract a file with an unsupported media container format, or an
   * unsupported media container feature.
   */
  public static final int ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED = 3003;
  /**
   * Caused by an unsupported feature in a media manifest. Examples of a media manifest are a DASH
   * or a SmoothStreaming manifest, or an HLS playlist.
   */
  public static final int ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED = 3004;

  // Decoding errors (4xxx).

  /** Caused by a decoder initialization failure. */
  public static final int ERROR_CODE_DECODER_INIT_FAILED = 4001;
  /** Caused by a decoder query failure. */
  public static final int ERROR_CODE_DECODER_QUERY_FAILED = 4002;
  /** Caused by a failure while trying to decode media samples. */
  public static final int ERROR_CODE_DECODING_FAILED = 4003;
  /** Caused by trying to decode content whose format exceeds the capabilities of the device. */
  public static final int ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES = 4004;
  /** Caused by trying to decode content whose format is not supported. */
  public static final int ERROR_CODE_DECODING_FORMAT_UNSUPPORTED = 4005;

  // AudioTrack errors (5xxx).

  /** Caused by an AudioTrack initialization failure. */
  public static final int ERROR_CODE_AUDIO_TRACK_INIT_FAILED = 5001;
  /** Caused by an AudioTrack write operation failure. */
  public static final int ERROR_CODE_AUDIO_TRACK_WRITE_FAILED = 5002;

  // DRM errors (6xxx).

  /** Caused by an unspecified error related to DRM protection. */
  public static final int ERROR_CODE_DRM_UNSPECIFIED = 6000;
  /**
   * Caused by a chosen DRM protection scheme not being supported by the device. Examples of DRM
   * protection schemes are ClearKey and Widevine.
   */
  public static final int ERROR_CODE_DRM_SCHEME_UNSUPPORTED = 6001;
  /** Caused by a failure while provisioning the device. */
  public static final int ERROR_CODE_DRM_PROVISIONING_FAILED = 6002;
  /**
   * Caused by attempting to play incompatible DRM-protected content.
   *
   * <p>For example, this can happen when attempting to play a DRM protected stream using a scheme
   * (like Widevine) for which there is no corresponding license acquisition data (like a pssh box).
   */
  public static final int ERROR_CODE_DRM_CONTENT_ERROR = 6003;
  /** Caused by a failure while trying to obtain a license. */
  public static final int ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED = 6004;
  /** Caused by an operation being disallowed by a license policy. */
  public static final int ERROR_CODE_DRM_DISALLOWED_OPERATION = 6005;
  /** Caused by an error in the DRM system. */
  public static final int ERROR_CODE_DRM_SYSTEM_ERROR = 6006;
  /** Caused by the device having revoked DRM privileges. */
  public static final int ERROR_CODE_DRM_DEVICE_REVOKED = 6007;
  /** Caused by an expired DRM license being loaded into an open DRM session. */
  public static final int ERROR_CODE_DRM_LICENSE_EXPIRED = 6008;

  /**
   * Player implementations that want to surface custom errors can use error codes greater than this
   * value, so as to avoid collision with other error codes defined in this class.
   */
  public static final int CUSTOM_ERROR_CODE_BASE = 1000000;

  /** Returns the name of a given {@code errorCode}. */
  public static String getErrorCodeName(@ErrorCode int errorCode) {
    switch (errorCode) {
      case ERROR_CODE_UNSPECIFIED:
        return "ERROR_CODE_UNSPECIFIED";
      case ERROR_CODE_REMOTE_ERROR:
        return "ERROR_CODE_REMOTE_ERROR";
      case ERROR_CODE_BEHIND_LIVE_WINDOW:
        return "ERROR_CODE_BEHIND_LIVE_WINDOW";
      case ERROR_CODE_TIMEOUT:
        return "ERROR_CODE_TIMEOUT";
      case ERROR_CODE_FAILED_RUNTIME_CHECK:
        return "ERROR_CODE_FAILED_RUNTIME_CHECK";
      case ERROR_CODE_IO_UNSPECIFIED:
        return "ERROR_CODE_IO_UNSPECIFIED";
      case ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
        return "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED";
      case ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
        return "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT";
      case ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE:
        return "ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE";
      case ERROR_CODE_IO_BAD_HTTP_STATUS:
        return "ERROR_CODE_IO_BAD_HTTP_STATUS";
      case ERROR_CODE_IO_FILE_NOT_FOUND:
        return "ERROR_CODE_IO_FILE_NOT_FOUND";
      case ERROR_CODE_IO_NO_PERMISSION:
        return "ERROR_CODE_IO_NO_PERMISSION";
      case ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED:
        return "ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED";
      case ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE:
        return "ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE";
      case ERROR_CODE_PARSING_CONTAINER_MALFORMED:
        return "ERROR_CODE_PARSING_CONTAINER_MALFORMED";
      case ERROR_CODE_PARSING_MANIFEST_MALFORMED:
        return "ERROR_CODE_PARSING_MANIFEST_MALFORMED";
      case ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
        return "ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED";
      case ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED:
        return "ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED";
      case ERROR_CODE_DECODER_INIT_FAILED:
        return "ERROR_CODE_DECODER_INIT_FAILED";
      case ERROR_CODE_DECODER_QUERY_FAILED:
        return "ERROR_CODE_DECODER_QUERY_FAILED";
      case ERROR_CODE_DECODING_FAILED:
        return "ERROR_CODE_DECODING_FAILED";
      case ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES:
        return "ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES";
      case ERROR_CODE_DECODING_FORMAT_UNSUPPORTED:
        return "ERROR_CODE_DECODING_FORMAT_UNSUPPORTED";
      case ERROR_CODE_AUDIO_TRACK_INIT_FAILED:
        return "ERROR_CODE_AUDIO_TRACK_INIT_FAILED";
      case ERROR_CODE_AUDIO_TRACK_WRITE_FAILED:
        return "ERROR_CODE_AUDIO_TRACK_WRITE_FAILED";
      case ERROR_CODE_DRM_UNSPECIFIED:
        return "ERROR_CODE_DRM_UNSPECIFIED";
      case ERROR_CODE_DRM_SCHEME_UNSUPPORTED:
        return "ERROR_CODE_DRM_SCHEME_UNSUPPORTED";
      case ERROR_CODE_DRM_PROVISIONING_FAILED:
        return "ERROR_CODE_DRM_PROVISIONING_FAILED";
      case ERROR_CODE_DRM_CONTENT_ERROR:
        return "ERROR_CODE_DRM_CONTENT_ERROR";
      case ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED:
        return "ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED";
      case ERROR_CODE_DRM_DISALLOWED_OPERATION:
        return "ERROR_CODE_DRM_DISALLOWED_OPERATION";
      case ERROR_CODE_DRM_SYSTEM_ERROR:
        return "ERROR_CODE_DRM_SYSTEM_ERROR";
      case ERROR_CODE_DRM_DEVICE_REVOKED:
        return "ERROR_CODE_DRM_DEVICE_REVOKED";
      case ERROR_CODE_DRM_LICENSE_EXPIRED:
        return "ERROR_CODE_DRM_LICENSE_EXPIRED";
      default:
        if (errorCode >= CUSTOM_ERROR_CODE_BASE) {
          return "custom error code";
        } else {
          return "invalid error code";
        }
    }
  }

  /**
   * Equivalent to {@link PlaybackException#getErrorCodeName(int)
   * PlaybackException.getErrorCodeName(this.errorCode)}.
   */
  public final String getErrorCodeName() {
    return getErrorCodeName(errorCode);
  }

  /** An error code which identifies the cause of the playback failure. */
  public final @ErrorCode int errorCode;

  /** The value of {@link SystemClock#elapsedRealtime()} when this exception was created. */
  public final long timestampMs;

  /**
   * Creates an instance.
   *
   * @param errorCode A number which identifies the cause of the error. May be one of the {@link
   *     ErrorCode ErrorCodes}.
   * @param cause See {@link #getCause()}.
   * @param message See {@link #getMessage()}.
   */
  public PlaybackException(
      @Nullable String message, @Nullable Throwable cause, @ErrorCode int errorCode) {
    this(message, cause, errorCode, Clock.DEFAULT.elapsedRealtime());
  }

  /** Creates a new instance using the fields obtained from the given {@link Bundle}. */
  protected PlaybackException(Bundle bundle) {
    this(
        /* message= */ bundle.getString(FIELD_STRING_MESSAGE),
        /* cause= */ getCauseFromBundle(bundle),
        /* errorCode= */ bundle.getInt(
            FIELD_INT_ERROR_CODE, /* defaultValue= */ ERROR_CODE_UNSPECIFIED),
        /* timestampMs= */ bundle.getLong(
            FIELD_LONG_TIMESTAMP_MS, /* defaultValue= */ SystemClock.elapsedRealtime()));
  }

  /** Creates a new instance using the given values. */
  protected PlaybackException(
      @Nullable String message,
      @Nullable Throwable cause,
      @ErrorCode int errorCode,
      long timestampMs) {
    super(message, cause);
    this.errorCode = errorCode;
    this.timestampMs = timestampMs;
  }

  /**
   * Returns whether the error data associated to this exception equals the error data associated to
   * {@code other}.
   *
   * <p>Note that this method does not compare the exceptions' stacktraces.
   */
  @CallSuper
  public boolean errorInfoEquals(@Nullable PlaybackException other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }

    @Nullable Throwable thisCause = getCause();
    @Nullable Throwable thatCause = other.getCause();
    if (thisCause != null && thatCause != null) {
      if (!Util.areEqual(thisCause.getMessage(), thatCause.getMessage())) {
        return false;
      }
      if (!Util.areEqual(thisCause.getClass(), thatCause.getClass())) {
        return false;
      }
    } else if (thisCause != null || thatCause != null) {
      return false;
    }
    return errorCode == other.errorCode
        && Util.areEqual(getMessage(), other.getMessage())
        && timestampMs == other.timestampMs;
  }

  // Bundleable implementation.

  private static final String FIELD_INT_ERROR_CODE = Util.intToStringMaxRadix(0);
  private static final String FIELD_LONG_TIMESTAMP_MS = Util.intToStringMaxRadix(1);
  private static final String FIELD_STRING_MESSAGE = Util.intToStringMaxRadix(2);
  private static final String FIELD_STRING_CAUSE_CLASS_NAME = Util.intToStringMaxRadix(3);
  private static final String FIELD_STRING_CAUSE_MESSAGE = Util.intToStringMaxRadix(4);

  /**
   * Defines a minimum field ID value for subclasses to use when implementing {@link #toBundle()}
   * and {@link Bundleable.Creator}.
   *
   * <p>Subclasses should obtain their {@link Bundle Bundle's} field keys by applying a non-negative
   * offset on this constant and passing the result to {@link Util#intToStringMaxRadix(int)}.
   */
  protected static final int FIELD_CUSTOM_ID_BASE = 1000;

  /** Object that can create a {@link PlaybackException} from a {@link Bundle}. */
  public static final Creator<PlaybackException> CREATOR = PlaybackException::new;

  @CallSuper
  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(FIELD_INT_ERROR_CODE, errorCode);
    bundle.putLong(FIELD_LONG_TIMESTAMP_MS, timestampMs);
    bundle.putString(FIELD_STRING_MESSAGE, getMessage());
    @Nullable Throwable cause = getCause();
    if (cause != null) {
      bundle.putString(FIELD_STRING_CAUSE_CLASS_NAME, cause.getClass().getName());
      bundle.putString(FIELD_STRING_CAUSE_MESSAGE, cause.getMessage());
    }
    return bundle;
  }

  // Creates a new {@link Throwable} with possibly {@code null} message.
  @SuppressWarnings("nullness:argument")
  private static Throwable createThrowable(Class<?> clazz, @Nullable String message)
      throws Exception {
    return (Throwable) clazz.getConstructor(String.class).newInstance(message);
  }

  // Creates a new {@link RemoteException} with possibly {@code null} message.
  @SuppressWarnings("nullness:argument")
  private static RemoteException createRemoteException(@Nullable String message) {
    return new RemoteException(message);
  }

  @Nullable
  private static Throwable getCauseFromBundle(Bundle bundle) {
    @Nullable String causeClassName = bundle.getString(FIELD_STRING_CAUSE_CLASS_NAME);
    @Nullable String causeMessage = bundle.getString(FIELD_STRING_CAUSE_MESSAGE);
    @Nullable Throwable cause = null;
    if (!TextUtils.isEmpty(causeClassName)) {
      try {
        Class<?> clazz =
            Class.forName(
                causeClassName, /* initialize= */ true, PlaybackException.class.getClassLoader());
        if (Throwable.class.isAssignableFrom(clazz)) {
          cause = createThrowable(clazz, causeMessage);
        }
      } catch (Throwable e) {
        // There was an error while creating the cause using reflection, do nothing here and let the
        // finally block handle the issue.
      } finally {
        if (cause == null) {
          // The bundle has fields to represent the cause, but we were unable to re-create the
          // exception using reflection. We instantiate a RemoteException to reflect this problem.
          cause = createRemoteException(causeMessage);
        }
      }
    }
    return cause;
  }
}

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
package com.google.android.exoplayer2;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import androidx.annotation.CheckResult;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C.FormatSupport;
import com.google.android.exoplayer2.source.MediaPeriodId;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Thrown when a non locally recoverable playback failure occurs. */
public final class ExoPlaybackException extends PlaybackException {

  /**
   * The type of source that produced the error. One of {@link #TYPE_SOURCE}, {@link #TYPE_RENDERER}
   * {@link #TYPE_UNEXPECTED} or {@link #TYPE_REMOTE}. Note that new types may be added in the
   * future and error handling should handle unknown type values.
   */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({TYPE_SOURCE, TYPE_RENDERER, TYPE_UNEXPECTED, TYPE_REMOTE})
  public @interface Type {}
  /**
   * The error occurred loading data from a {@link MediaSource}.
   *
   * <p>Call {@link #getSourceException()} to retrieve the underlying cause.
   */
  public static final int TYPE_SOURCE = 0;
  /**
   * The error occurred in a {@link Renderer}.
   *
   * <p>Call {@link #getRendererException()} to retrieve the underlying cause.
   */
  public static final int TYPE_RENDERER = 1;
  /**
   * The error was an unexpected {@link RuntimeException}.
   *
   * <p>Call {@link #getUnexpectedException()} to retrieve the underlying cause.
   */
  public static final int TYPE_UNEXPECTED = 2;
  /**
   * The error occurred in a remote component.
   *
   * <p>Call {@link #getMessage()} to retrieve the message associated with the error.
   */
  public static final int TYPE_REMOTE = 3;

  /** The {@link Type} of the playback failure. */
  public final @Type int type;

  /** If {@link #type} is {@link #TYPE_RENDERER}, this is the name of the renderer. */
  @Nullable public final String rendererName;

  /** If {@link #type} is {@link #TYPE_RENDERER}, this is the index of the renderer. */
  public final int rendererIndex;

  /**
   * If {@link #type} is {@link #TYPE_RENDERER}, this is the {@link Format} the renderer was using
   * at the time of the exception, or null if the renderer wasn't using a {@link Format}.
   */
  @Nullable public final Format rendererFormat;

  /**
   * If {@link #type} is {@link #TYPE_RENDERER}, this is the level of {@link FormatSupport} of the
   * renderer for {@link #rendererFormat}. If {@link #rendererFormat} is null, this is {@link
   * C#FORMAT_HANDLED}.
   */
  public final @FormatSupport int rendererFormatSupport;

  /** The {@link MediaPeriodId} of the media associated with this error, or null if undetermined. */
  @Nullable public final MediaPeriodId mediaPeriodId;

  /**
   * If {@link #type} is {@link #TYPE_RENDERER}, this field indicates whether the error may be
   * recoverable by disabling and re-enabling (but <em>not</em> resetting) the renderers. For other
   * {@link Type types} this field will always be {@code false}.
   */
  /* package */ final boolean isRecoverable;

  /**
   * Creates an instance of type {@link #TYPE_SOURCE}.
   *
   * @param cause The cause of the failure.
   * @param errorCode See {@link #errorCode}.
   * @return The created instance.
   */
  public static ExoPlaybackException createForSource(IOException cause, int errorCode) {
    return new ExoPlaybackException(TYPE_SOURCE, cause, errorCode);
  }

  /**
   * Creates an instance of type {@link #TYPE_RENDERER}.
   *
   * @param cause The cause of the failure.
   * @param rendererIndex The index of the renderer in which the failure occurred.
   * @param rendererFormat The {@link Format} the renderer was using at the time of the exception,
   *     or null if the renderer wasn't using a {@link Format}.
   * @param rendererFormatSupport The {@link FormatSupport} of the renderer for {@code
   *     rendererFormat}. Ignored if {@code rendererFormat} is null.
   * @param isRecoverable If the failure can be recovered by disabling and re-enabling the renderer.
   * @param errorCode See {@link #errorCode}.
   * @return The created instance.
   */
  public static ExoPlaybackException createForRenderer(
      Throwable cause,
      String rendererName,
      int rendererIndex,
      @Nullable Format rendererFormat,
      @FormatSupport int rendererFormatSupport,
      boolean isRecoverable,
      @ErrorCode int errorCode) {

    return new ExoPlaybackException(
        TYPE_RENDERER,
        cause,
        /* customMessage= */ null,
        errorCode,
        rendererName,
        rendererIndex,
        rendererFormat,
        rendererFormat == null ? C.FORMAT_HANDLED : rendererFormatSupport,
        isRecoverable);
  }

  /**
   * @deprecated Use {@link #createForUnexpected(RuntimeException, int)
   *     createForUnexpected(RuntimeException, ERROR_CODE_UNSPECIFIED)} instead.
   */
  @Deprecated
  public static ExoPlaybackException createForUnexpected(RuntimeException cause) {
    return createForUnexpected(cause, ERROR_CODE_UNSPECIFIED);
  }

  /**
   * Creates an instance of type {@link #TYPE_UNEXPECTED}.
   *
   * @param cause The cause of the failure.
   * @param errorCode See {@link #errorCode}.
   * @return The created instance.
   */
  public static ExoPlaybackException createForUnexpected(
      RuntimeException cause, @ErrorCode int errorCode) {
    return new ExoPlaybackException(TYPE_UNEXPECTED, cause, errorCode);
  }

  /**
   * Creates an instance of type {@link #TYPE_REMOTE}.
   *
   * @param message The message associated with the error.
   * @return The created instance.
   */
  public static ExoPlaybackException createForRemote(String message) {
    return new ExoPlaybackException(
        TYPE_REMOTE,
        /* cause= */ null,
        /* customMessage= */ message,
        ERROR_CODE_REMOTE_ERROR,
        /* rendererName= */ null,
        /* rendererIndex= */ C.INDEX_UNSET,
        /* rendererFormat= */ null,
        /* rendererFormatSupport= */ C.FORMAT_HANDLED,
        /* isRecoverable= */ false);
  }

  private ExoPlaybackException(@Type int type, Throwable cause, @ErrorCode int errorCode) {
    this(
        type,
        cause,
        /* customMessage= */ null,
        errorCode,
        /* rendererName= */ null,
        /* rendererIndex= */ C.INDEX_UNSET,
        /* rendererFormat= */ null,
        /* rendererFormatSupport= */ C.FORMAT_HANDLED,
        /* isRecoverable= */ false);
  }

  private ExoPlaybackException(
      @Type int type,
      @Nullable Throwable cause,
      @Nullable String customMessage,
      @ErrorCode int errorCode,
      @Nullable String rendererName,
      int rendererIndex,
      @Nullable Format rendererFormat,
      @FormatSupport int rendererFormatSupport,
      boolean isRecoverable) {
    this(
        deriveMessage(
            type,
            customMessage,
            rendererName,
            rendererIndex,
            rendererFormat,
            rendererFormatSupport),
        cause,
        errorCode,
        type,
        rendererName,
        rendererIndex,
        rendererFormat,
        rendererFormatSupport,
        /* mediaPeriodId= */ null,
        /* timestampMs= */ SystemClock.elapsedRealtime(),
        isRecoverable);
  }

  private ExoPlaybackException(Bundle bundle) {
    super(bundle);
    type = bundle.getInt(FIELD_TYPE, /* defaultValue= */ TYPE_UNEXPECTED);
    rendererName = bundle.getString(FIELD_RENDERER_NAME);
    rendererIndex = bundle.getInt(FIELD_RENDERER_INDEX, /* defaultValue= */ C.INDEX_UNSET);
    @Nullable Bundle rendererFormatBundle = bundle.getBundle(FIELD_RENDERER_FORMAT);
    rendererFormat =
        rendererFormatBundle == null ? null : Format.CREATOR.fromBundle(rendererFormatBundle);
    rendererFormatSupport =
        bundle.getInt(FIELD_RENDERER_FORMAT_SUPPORT, /* defaultValue= */ C.FORMAT_HANDLED);
    isRecoverable = bundle.getBoolean(FIELD_IS_RECOVERABLE, /* defaultValue= */ false);
    mediaPeriodId = null;
  }

  private ExoPlaybackException(
      String message,
      @Nullable Throwable cause,
      @ErrorCode int errorCode,
      @Type int type,
      @Nullable String rendererName,
      int rendererIndex,
      @Nullable Format rendererFormat,
      @FormatSupport int rendererFormatSupport,
      @Nullable MediaPeriodId mediaPeriodId,
      long timestampMs,
      boolean isRecoverable) {
    super(message, cause, errorCode, timestampMs);
    Assertions.checkArgument(!isRecoverable || type == TYPE_RENDERER);
    Assertions.checkArgument(cause != null || type == TYPE_REMOTE);
    this.type = type;
    this.rendererName = rendererName;
    this.rendererIndex = rendererIndex;
    this.rendererFormat = rendererFormat;
    this.rendererFormatSupport = rendererFormatSupport;
    this.mediaPeriodId = mediaPeriodId;
    this.isRecoverable = isRecoverable;
  }

  /**
   * Retrieves the underlying error when {@link #type} is {@link #TYPE_SOURCE}.
   *
   * @throws IllegalStateException If {@link #type} is not {@link #TYPE_SOURCE}.
   */
  public IOException getSourceException() {
    Assertions.checkState(type == TYPE_SOURCE);
    return (IOException) Assertions.checkNotNull(getCause());
  }

  /**
   * Retrieves the underlying error when {@link #type} is {@link #TYPE_RENDERER}.
   *
   * @throws IllegalStateException If {@link #type} is not {@link #TYPE_RENDERER}.
   */
  public Exception getRendererException() {
    Assertions.checkState(type == TYPE_RENDERER);
    return (Exception) Assertions.checkNotNull(getCause());
  }

  /**
   * Retrieves the underlying error when {@link #type} is {@link #TYPE_UNEXPECTED}.
   *
   * @throws IllegalStateException If {@link #type} is not {@link #TYPE_UNEXPECTED}.
   */
  public RuntimeException getUnexpectedException() {
    Assertions.checkState(type == TYPE_UNEXPECTED);
    return (RuntimeException) Assertions.checkNotNull(getCause());
  }

  @Override
  public boolean errorInfoEquals(@Nullable PlaybackException that) {
    if (!super.errorInfoEquals(that)) {
      return false;
    }
    // We know that is not null and is an ExoPlaybackException because of the super call returning
    // true.
    ExoPlaybackException other = (ExoPlaybackException) Util.castNonNull(that);
    return type == other.type
        && Util.areEqual(rendererName, other.rendererName)
        && rendererIndex == other.rendererIndex
        && Util.areEqual(rendererFormat, other.rendererFormat)
        && rendererFormatSupport == other.rendererFormatSupport
        && Util.areEqual(mediaPeriodId, other.mediaPeriodId)
        && isRecoverable == other.isRecoverable;
  }

  /**
   * Returns a copy of this exception with the provided {@link MediaPeriodId}.
   *
   * @param mediaPeriodId The {@link MediaPeriodId}.
   * @return The copied exception.
   */
  @CheckResult
  /* package */ ExoPlaybackException copyWithMediaPeriodId(@Nullable MediaPeriodId mediaPeriodId) {
    return new ExoPlaybackException(
        Util.castNonNull(getMessage()),
        getCause(),
        errorCode,
        type,
        rendererName,
        rendererIndex,
        rendererFormat,
        rendererFormatSupport,
        mediaPeriodId,
        timestampMs,
        isRecoverable);
  }

  private static String deriveMessage(
      @Type int type,
      @Nullable String customMessage,
      @Nullable String rendererName,
      int rendererIndex,
      @Nullable Format rendererFormat,
      @FormatSupport int rendererFormatSupport) {
    String message;
    switch (type) {
      case TYPE_SOURCE:
        message = "Source error";
        break;
      case TYPE_RENDERER:
        message =
            rendererName
                + " error"
                + ", index="
                + rendererIndex
                + ", format="
                + rendererFormat
                + ", format_supported="
                + Util.getFormatSupportString(rendererFormatSupport);
        break;
      case TYPE_REMOTE:
        message = "Remote error";
        break;
      case TYPE_UNEXPECTED:
      default:
        message = "Unexpected runtime error";
        break;
    }
    if (!TextUtils.isEmpty(customMessage)) {
      message += ": " + customMessage;
    }
    return message;
  }

  // Bundleable implementation.

  /** Object that can restore {@link ExoPlaybackException} from a {@link Bundle}. */
  public static final Creator<ExoPlaybackException> CREATOR = ExoPlaybackException::new;

  private static final String FIELD_TYPE = Util.intToStringMaxRadix(FIELD_CUSTOM_ID_BASE + 1);
  private static final String FIELD_RENDERER_NAME =
      Util.intToStringMaxRadix(FIELD_CUSTOM_ID_BASE + 2);
  private static final String FIELD_RENDERER_INDEX =
      Util.intToStringMaxRadix(FIELD_CUSTOM_ID_BASE + 3);
  private static final String FIELD_RENDERER_FORMAT =
      Util.intToStringMaxRadix(FIELD_CUSTOM_ID_BASE + 4);
  private static final String FIELD_RENDERER_FORMAT_SUPPORT =
      Util.intToStringMaxRadix(FIELD_CUSTOM_ID_BASE + 5);
  private static final String FIELD_IS_RECOVERABLE =
      Util.intToStringMaxRadix(FIELD_CUSTOM_ID_BASE + 6);

  /**
   * {@inheritDoc}
   *
   * <p>It omits the {@link #mediaPeriodId} field. The {@link #mediaPeriodId} of an instance
   * restored by {@link #CREATOR} will always be {@code null}.
   */
  @Override
  public Bundle toBundle() {
    Bundle bundle = super.toBundle();
    bundle.putInt(FIELD_TYPE, type);
    bundle.putString(FIELD_RENDERER_NAME, rendererName);
    bundle.putInt(FIELD_RENDERER_INDEX, rendererIndex);
    if (rendererFormat != null) {
      bundle.putBundle(FIELD_RENDERER_FORMAT, rendererFormat.toBundle());
    }
    bundle.putInt(FIELD_RENDERER_FORMAT_SUPPORT, rendererFormatSupport);
    bundle.putBoolean(FIELD_IS_RECOVERABLE, isRecoverable);
    return bundle;
  }
}

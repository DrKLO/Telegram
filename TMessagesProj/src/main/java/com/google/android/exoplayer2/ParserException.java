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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C.DataType;
import java.io.IOException;

/** Thrown when an error occurs parsing media data and metadata. */
public class ParserException extends IOException {

  /**
   * Creates a new instance for which {@link #contentIsMalformed} is true and {@link #dataType} is
   * {@link C#DATA_TYPE_UNKNOWN}.
   *
   * @param message See {@link #getMessage()}.
   * @param cause See {@link #getCause()}.
   * @return The created instance.
   */
  public static ParserException createForMalformedDataOfUnknownType(
      @Nullable String message, @Nullable Throwable cause) {
    return new ParserException(message, cause, /* contentIsMalformed= */ true, C.DATA_TYPE_UNKNOWN);
  }

  /**
   * Creates a new instance for which {@link #contentIsMalformed} is true and {@link #dataType} is
   * {@link C#DATA_TYPE_MEDIA}.
   *
   * @param message See {@link #getMessage()}.
   * @param cause See {@link #getCause()}.
   * @return The created instance.
   */
  public static ParserException createForMalformedContainer(
      @Nullable String message, @Nullable Throwable cause) {
    return new ParserException(message, cause, /* contentIsMalformed= */ true, C.DATA_TYPE_MEDIA);
  }

  /**
   * Creates a new instance for which {@link #contentIsMalformed} is true and {@link #dataType} is
   * {@link C#DATA_TYPE_MANIFEST}.
   *
   * @param message See {@link #getMessage()}.
   * @param cause See {@link #getCause()}.
   * @return The created instance.
   */
  public static ParserException createForMalformedManifest(
      @Nullable String message, @Nullable Throwable cause) {
    return new ParserException(
        message, cause, /* contentIsMalformed= */ true, C.DATA_TYPE_MANIFEST);
  }

  /**
   * Creates a new instance for which {@link #contentIsMalformed} is false and {@link #dataType} is
   * {@link C#DATA_TYPE_MANIFEST}.
   *
   * @param message See {@link #getMessage()}.
   * @param cause See {@link #getCause()}.
   * @return The created instance.
   */
  public static ParserException createForManifestWithUnsupportedFeature(
      @Nullable String message, @Nullable Throwable cause) {
    return new ParserException(
        message, cause, /* contentIsMalformed= */ false, C.DATA_TYPE_MANIFEST);
  }

  /**
   * Creates a new instance for which {@link #contentIsMalformed} is false and {@link #dataType} is
   * {@link C#DATA_TYPE_MEDIA}.
   *
   * @param message See {@link #getMessage()}.
   * @return The created instance.
   */
  public static ParserException createForUnsupportedContainerFeature(@Nullable String message) {
    return new ParserException(
        message, /* cause= */ null, /* contentIsMalformed= */ false, C.DATA_TYPE_MEDIA);
  }

  /**
   * Whether the parsing error was caused by a bitstream not following the expected format. May be
   * false when a parser encounters a legal condition which it does not support.
   */
  public final boolean contentIsMalformed;
  /** The {@link DataType data type} of the parsed bitstream. */
  public final int dataType;

  protected ParserException(
      @Nullable String message,
      @Nullable Throwable cause,
      boolean contentIsMalformed,
      @DataType int dataType) {
    super(message, cause);
    this.contentIsMalformed = contentIsMalformed;
    this.dataType = dataType;
  }
}

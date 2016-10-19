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
package org.telegram.messenger.exoplayer.util;

import android.os.Looper;
import android.text.TextUtils;
import org.telegram.messenger.exoplayer.ExoPlayerLibraryInfo;

/**
 * Provides methods for asserting the truth of expressions and properties.
 */
public final class Assertions {

  private Assertions() {}

  /**
   * Ensures the truth of an expression involving one or more arguments passed to the calling
   * method.
   *
   * @param expression A boolean expression.
   * @throws IllegalArgumentException If {@code expression} is false.
   */
  public static void checkArgument(boolean expression) {
    if (ExoPlayerLibraryInfo.ASSERTIONS_ENABLED && !expression) {
      throw new IllegalArgumentException();
    }
  }

  /**
   * Ensures the truth of an expression involving one or more arguments passed to the calling
   * method.
   *
   * @param expression A boolean expression.
   * @param errorMessage The exception message to use if the check fails. The message is converted
   *     to a {@link String} using {@link String#valueOf(Object)}.
   * @throws IllegalArgumentException If {@code expression} is false.
   */
  public static void checkArgument(boolean expression, Object errorMessage) {
    if (ExoPlayerLibraryInfo.ASSERTIONS_ENABLED && !expression) {
      throw new IllegalArgumentException(String.valueOf(errorMessage));
    }
  }

  /**
   * Ensures the truth of an expression involving the state of the calling instance.
   *
   * @param expression A boolean expression.
   * @throws IllegalStateException If {@code expression} is false.
   */
  public static void checkState(boolean expression) {
    if (ExoPlayerLibraryInfo.ASSERTIONS_ENABLED && !expression) {
      throw new IllegalStateException();
    }
  }

  /**
   * Ensures the truth of an expression involving the state of the calling instance.
   *
   * @param expression A boolean expression.
   * @param errorMessage The exception message to use if the check fails. The message is converted
   *     to a string using {@link String#valueOf(Object)}.
   * @throws IllegalStateException If {@code expression} is false.
   */
  public static void checkState(boolean expression, Object errorMessage) {
    if (ExoPlayerLibraryInfo.ASSERTIONS_ENABLED && !expression) {
      throw new IllegalStateException(String.valueOf(errorMessage));
    }
  }

  /**
   * Ensures that an object reference is not null.
   *
   * @param reference An object reference.
   * @return The non-null reference that was validated.
   * @throws NullPointerException If {@code reference} is null.
   */
  public static <T> T checkNotNull(T reference) {
    if (ExoPlayerLibraryInfo.ASSERTIONS_ENABLED && reference == null) {
      throw new NullPointerException();
    }
    return reference;
  }

  /**
   * Ensures that an object reference is not null.
   *
   * @param reference An object reference.
   * @param errorMessage The exception message to use if the check fails. The message is converted
   *     to a string using {@link String#valueOf(Object)}.
   * @return The non-null reference that was validated.
   * @throws NullPointerException If {@code reference} is null.
   */
  public static <T> T checkNotNull(T reference, Object errorMessage) {
    if (ExoPlayerLibraryInfo.ASSERTIONS_ENABLED && reference == null) {
      throw new NullPointerException(String.valueOf(errorMessage));
    }
    return reference;
  }

  /**
   * Ensures that a string passed as an argument to the calling method is not null or 0-length.
   *
   * @param string A string.
   * @return The non-null, non-empty string that was validated.
   * @throws IllegalArgumentException If {@code string} is null or 0-length.
   */
  public static String checkNotEmpty(String string) {
    if (ExoPlayerLibraryInfo.ASSERTIONS_ENABLED && TextUtils.isEmpty(string)) {
      throw new IllegalArgumentException();
    }
    return string;
  }

  /**
   * Ensures that a string passed as an argument to the calling method is not null or 0-length.
   *
   * @param string A string.
   * @param errorMessage The exception message to use if the check fails. The message is converted
   *     to a string using {@link String#valueOf(Object)}.
   * @return The non-null, non-empty string that was validated.
   * @throws IllegalArgumentException If {@code string} is null or 0-length.
   */
  public static String checkNotEmpty(String string, Object errorMessage) {
    if (ExoPlayerLibraryInfo.ASSERTIONS_ENABLED && TextUtils.isEmpty(string)) {
      throw new IllegalArgumentException(String.valueOf(errorMessage));
    }
    return string;
  }

  /**
   * Ensures that the calling thread is the application's main thread.
   *
   * @throws IllegalStateException If the calling thread is not the application's main thread.
   */
  public static void checkMainThread() {
    if (ExoPlayerLibraryInfo.ASSERTIONS_ENABLED && Looper.myLooper() != Looper.getMainLooper()) {
      throw new IllegalStateException("Not in applications main thread");
    }
  }

}

/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.text.span;

import android.text.Spannable;
import android.text.style.ForegroundColorSpan;

/**
 * Utility methods for Android <a href="https://developer.android.com/guide/topics/text/spans">span
 * styling</a>.
 */
public final class SpanUtil {

  /**
   * Adds {@code span} to {@code spannable} between {@code start} and {@code end}, removing any
   * existing spans of the same type and with the same indices and flags.
   *
   * <p>This is useful for types of spans that don't make sense to duplicate and where the
   * evaluation order might have an unexpected impact on the final text, e.g. {@link
   * ForegroundColorSpan}.
   *
   * @param spannable The {@link Spannable} to add {@code span} to.
   * @param span The span object to be added.
   * @param start The start index to add the new span at.
   * @param end The end index to add the new span at.
   * @param spanFlags The flags to pass to {@link Spannable#setSpan(Object, int, int, int)}.
   */
  public static void addOrReplaceSpan(
      Spannable spannable, Object span, int start, int end, int spanFlags) {
    Object[] existingSpans = spannable.getSpans(start, end, span.getClass());
    for (Object existingSpan : existingSpans) {
      if (spannable.getSpanStart(existingSpan) == start
          && spannable.getSpanEnd(existingSpan) == end
          && spannable.getSpanFlags(existingSpan) == spanFlags) {
        spannable.removeSpan(existingSpan);
      }
    }
    spannable.setSpan(span, start, end, spanFlags);
  }

  private SpanUtil() {}
}

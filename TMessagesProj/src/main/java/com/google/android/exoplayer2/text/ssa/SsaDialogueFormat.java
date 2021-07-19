/*
 * Copyright (C) 2019 The Android Open Source Project
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
 *
 */
package com.google.android.exoplayer2.text.ssa;

import static com.google.android.exoplayer2.text.ssa.SsaDecoder.FORMAT_LINE_PREFIX;

import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

/**
 * Represents a {@code Format:} line from the {@code [Events]} section
 *
 * <p>The indices are used to determine the location of particular properties in each {@code
 * Dialogue:} line.
 */
/* package */ final class SsaDialogueFormat {

  public final int startTimeIndex;
  public final int endTimeIndex;
  public final int styleIndex;
  public final int textIndex;
  public final int length;

  private SsaDialogueFormat(
      int startTimeIndex, int endTimeIndex, int styleIndex, int textIndex, int length) {
    this.startTimeIndex = startTimeIndex;
    this.endTimeIndex = endTimeIndex;
    this.styleIndex = styleIndex;
    this.textIndex = textIndex;
    this.length = length;
  }

  /**
   * Parses the format info from a 'Format:' line in the [Events] section.
   *
   * @return the parsed info, or null if {@code formatLine} doesn't contain both 'start' and 'end'.
   */
  @Nullable
  public static SsaDialogueFormat fromFormatLine(String formatLine) {
    int startTimeIndex = C.INDEX_UNSET;
    int endTimeIndex = C.INDEX_UNSET;
    int styleIndex = C.INDEX_UNSET;
    int textIndex = C.INDEX_UNSET;
    Assertions.checkArgument(formatLine.startsWith(FORMAT_LINE_PREFIX));
    String[] keys = TextUtils.split(formatLine.substring(FORMAT_LINE_PREFIX.length()), ",");
    for (int i = 0; i < keys.length; i++) {
      switch (Util.toLowerInvariant(keys[i].trim())) {
        case "start":
          startTimeIndex = i;
          break;
        case "end":
          endTimeIndex = i;
          break;
        case "style":
          styleIndex = i;
          break;
        case "text":
          textIndex = i;
          break;
      }
    }
    return (startTimeIndex != C.INDEX_UNSET && endTimeIndex != C.INDEX_UNSET)
        ? new SsaDialogueFormat(startTimeIndex, endTimeIndex, styleIndex, textIndex, keys.length)
        : null;
  }
}

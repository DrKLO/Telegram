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
package org.telegram.messenger.exoplayer2.text.webvtt;

import org.telegram.messenger.exoplayer2.text.SubtitleDecoderException;
import org.telegram.messenger.exoplayer2.util.ParsableByteArray;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for parsing WebVTT data.
 */
public final class WebvttParserUtil {

  private static final Pattern COMMENT = Pattern.compile("^NOTE((\u0020|\u0009).*)?$");
  private static final Pattern HEADER = Pattern.compile("^\uFEFF?WEBVTT((\u0020|\u0009).*)?$");

  private WebvttParserUtil() {}

  /**
   * Reads and validates the first line of a WebVTT file.
   *
   * @param input The input from which the line should be read.
   * @throws SubtitleDecoderException If the line isn't the start of a valid WebVTT file.
   */
  public static void validateWebvttHeaderLine(ParsableByteArray input)
      throws SubtitleDecoderException {
    String line = input.readLine();
    if (line == null || !HEADER.matcher(line).matches()) {
      throw new SubtitleDecoderException("Expected WEBVTT. Got " + line);
    }
  }

  /**
   * Parses a WebVTT timestamp.
   *
   * @param timestamp The timestamp string.
   * @return The parsed timestamp in microseconds.
   * @throws NumberFormatException If the timestamp could not be parsed.
   */
  public static long parseTimestampUs(String timestamp) throws NumberFormatException {
    long value = 0;
    String[] parts = timestamp.split("\\.", 2);
    String[] subparts = parts[0].split(":");
    for (String subpart : subparts) {
      value = value * 60 + Long.parseLong(subpart);
    }
    return (value * 1000 + Long.parseLong(parts[1])) * 1000;
  }

  /**
   * Parses a percentage string.
   *
   * @param s The percentage string.
   * @return The parsed value, where 1.0 represents 100%.
   * @throws NumberFormatException If the percentage could not be parsed.
   */
  public static float parsePercentage(String s) throws NumberFormatException {
    if (!s.endsWith("%")) {
      throw new NumberFormatException("Percentages must end with %");
    }
    return Float.parseFloat(s.substring(0, s.length() - 1)) / 100;
  }
  
  /**
   * Reads lines up to and including the next WebVTT cue header.
   *
   * @param input The input from which lines should be read.
   * @return A {@link Matcher} for the WebVTT cue header, or null if the end of the input was
   *     reached without a cue header being found. In the case that a cue header is found, groups 1,
   *     2 and 3 of the returned matcher contain the start time, end time and settings list.
   */
  public static Matcher findNextCueHeader(ParsableByteArray input) {
    String line;
    while ((line = input.readLine()) != null) {
      if (COMMENT.matcher(line).matches()) {
        // Skip until the end of the comment block.
        while ((line = input.readLine()) != null && !line.isEmpty()) {}
      } else {
        Matcher cueHeaderMatcher = WebvttCueParser.CUE_HEADER_PATTERN.matcher(line);
        if (cueHeaderMatcher.matches()) {
          return cueHeaderMatcher;
        }
      }
    }
    return null;
  }

}

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
package org.telegram.messenger.exoplayer.text.webvtt;

import org.telegram.messenger.exoplayer.ParserException;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;
import java.util.regex.Pattern;

/**
 * Utility methods for parsing WebVTT data.
 */
public final class WebvttParserUtil {

  private static final Pattern HEADER = Pattern.compile("^\uFEFF?WEBVTT((\u0020|\u0009).*)?$");

  private WebvttParserUtil() {}

  /**
   * Reads and validates the first line of a WebVTT file.
   *
   * @param input The input from which the line should be read.
   * @throws ParserException If the line isn't the start of a valid WebVTT file.
   */
  public static void validateWebvttHeaderLine(ParsableByteArray input) throws ParserException {
    String line = input.readLine();
    if (line == null || !HEADER.matcher(line).matches()) {
      throw new ParserException("Expected WEBVTT. Got " + line);
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
    for (int i = 0; i < subparts.length; i++) {
      value = value * 60 + Long.parseLong(subparts[i]);
    }
    return (value * 1000 + Long.parseLong(parts[1])) * 1000;
  }

  /**
   * Parses a percentage and returns a scaled float.
   * @param s contains the number to parse.
   * @return a float scaled number. 1.0 represents 100%.
   * @throws NumberFormatException if the number format is invalid or does not end with '%'.
   */
  public static float parsePercentage(String s) throws NumberFormatException {
    if (!s.endsWith("%")) {
      throw new NumberFormatException("Percentages must end with %");
    }
    return Float.parseFloat(s.substring(0, s.length() - 1)) / 100;
  }

}

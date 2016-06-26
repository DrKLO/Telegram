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
package org.telegram.messenger.exoplayer.extractor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for parsing and representing gapless playback information.
 */
public final class GaplessInfo {

  private static final String GAPLESS_COMMENT_ID = "iTunSMPB";
  private static final Pattern GAPLESS_COMMENT_PATTERN =
      Pattern.compile("^ [0-9a-fA-F]{8} ([0-9a-fA-F]{8}) ([0-9a-fA-F]{8})");

  /**
   * Parses a gapless playback comment (stored in an ID3 header or MPEG 4 user data).
   *
   * @param name The comment's identifier.
   * @param data The comment's payload data.
   * @return Parsed gapless playback information, if present and non-zero. {@code null} otherwise.
   */
  public static GaplessInfo createFromComment(String name, String data) {
    if (!GAPLESS_COMMENT_ID.equals(name)) {
      return null;
    }
    Matcher matcher = GAPLESS_COMMENT_PATTERN.matcher(data);
    if (matcher.find()) {
      try {
        int encoderDelay = Integer.parseInt(matcher.group(1), 16);
        int encoderPadding = Integer.parseInt(matcher.group(2), 16);
        return encoderDelay == 0 && encoderPadding == 0 ? null
            : new GaplessInfo(encoderDelay, encoderPadding);
      } catch (NumberFormatException e) {
        // Ignore incorrectly formatted comments.
      }
    }
    return null;
  }

  /**
   * Parses gapless playback information associated with an MP3 Xing header.
   *
   * @param value The 24-bit value to parse.
   * @return Parsed gapless playback information, if non-zero. {@code null} otherwise.
   */
  public static GaplessInfo createFromXingHeaderValue(int value) {
    int encoderDelay = value >> 12;
    int encoderPadding = value & 0x0FFF;
    return encoderDelay == 0 && encoderPadding == 0 ? null
        : new GaplessInfo(encoderDelay, encoderPadding);
  }

  /**
   * The number of samples to trim from the start of the decoded audio stream.
   */
  public final int encoderDelay;
  /**
   * The number of samples to trim from the end of the decoded audio stream.
   */
  public final int encoderPadding;

  /**
   * Creates a new {@link GaplessInfo} with the specified encoder delay and padding.
   *
   * @param encoderDelay The encoder delay.
   * @param encoderPadding The encoder padding.
   */
  private GaplessInfo(int encoderDelay, int encoderPadding) {
    this.encoderDelay = encoderDelay;
    this.encoderPadding = encoderPadding;
  }

}

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
package com.google.android.exoplayer2.text.webvtt;

import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.text.SimpleSubtitleDecoder;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link SimpleSubtitleDecoder} for WebVTT.
 *
 * @see <a href="http://dev.w3.org/html5/webvtt">WebVTT specification</a>
 */
public final class WebvttDecoder extends SimpleSubtitleDecoder {

  private static final int EVENT_NONE = -1;
  private static final int EVENT_END_OF_FILE = 0;
  private static final int EVENT_COMMENT = 1;
  private static final int EVENT_STYLE_BLOCK = 2;
  private static final int EVENT_CUE = 3;

  private static final String COMMENT_START = "NOTE";
  private static final String STYLE_START = "STYLE";

  private final ParsableByteArray parsableWebvttData;
  private final WebvttCssParser cssParser;

  public WebvttDecoder() {
    super("WebvttDecoder");
    parsableWebvttData = new ParsableByteArray();
    cssParser = new WebvttCssParser();
  }

  @Override
  protected Subtitle decode(byte[] data, int length, boolean reset)
      throws SubtitleDecoderException {
    parsableWebvttData.reset(data, length);
    List<WebvttCssStyle> definedStyles = new ArrayList<>();

    // Validate the first line of the header, and skip the remainder.
    try {
      WebvttParserUtil.validateWebvttHeaderLine(parsableWebvttData);
    } catch (ParserException e) {
      throw new SubtitleDecoderException(e);
    }
    while (!TextUtils.isEmpty(parsableWebvttData.readLine())) {}

    int event;
    List<WebvttCueInfo> cueInfos = new ArrayList<>();
    while ((event = getNextEvent(parsableWebvttData)) != EVENT_END_OF_FILE) {
      if (event == EVENT_COMMENT) {
        skipComment(parsableWebvttData);
      } else if (event == EVENT_STYLE_BLOCK) {
        if (!cueInfos.isEmpty()) {
          throw new SubtitleDecoderException("A style block was found after the first cue.");
        }
        parsableWebvttData.readLine(); // Consume the "STYLE" header.
        definedStyles.addAll(cssParser.parseBlock(parsableWebvttData));
      } else if (event == EVENT_CUE) {
        @Nullable
        WebvttCueInfo cueInfo = WebvttCueParser.parseCue(parsableWebvttData, definedStyles);
        if (cueInfo != null) {
          cueInfos.add(cueInfo);
        }
      }
    }
    return new WebvttSubtitle(cueInfos);
  }

  /**
   * Positions the input right before the next event, and returns the kind of event found. Does not
   * consume any data from such event, if any.
   *
   * @return The kind of event found.
   */
  private static int getNextEvent(ParsableByteArray parsableWebvttData) {
    int foundEvent = EVENT_NONE;
    int currentInputPosition = 0;
    while (foundEvent == EVENT_NONE) {
      currentInputPosition = parsableWebvttData.getPosition();
      String line = parsableWebvttData.readLine();
      if (line == null) {
        foundEvent = EVENT_END_OF_FILE;
      } else if (STYLE_START.equals(line)) {
        foundEvent = EVENT_STYLE_BLOCK;
      } else if (line.startsWith(COMMENT_START)) {
        foundEvent = EVENT_COMMENT;
      } else {
        foundEvent = EVENT_CUE;
      }
    }
    parsableWebvttData.setPosition(currentInputPosition);
    return foundEvent;
  }

  private static void skipComment(ParsableByteArray parsableWebvttData) {
    while (!TextUtils.isEmpty(parsableWebvttData.readLine())) {}
  }
}

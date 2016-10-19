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
package org.telegram.messenger.exoplayer.text.subrip;

import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import org.telegram.messenger.exoplayer.text.Cue;
import org.telegram.messenger.exoplayer.text.SubtitleParser;
import org.telegram.messenger.exoplayer.util.LongArray;
import org.telegram.messenger.exoplayer.util.MimeTypes;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple SubRip parser.
 */
public final class SubripParser implements SubtitleParser {

  private static final String TAG = "SubripParser";

  private static final Pattern SUBRIP_TIMING_LINE = Pattern.compile("(\\S*)\\s*-->\\s*(\\S*)");
  private static final Pattern SUBRIP_TIMESTAMP =
      Pattern.compile("(?:(\\d+):)?(\\d+):(\\d+),(\\d+)");

  private final StringBuilder textBuilder;

  public SubripParser() {
    textBuilder = new StringBuilder();
  }

  @Override
  public boolean canParse(String mimeType) {
    return MimeTypes.APPLICATION_SUBRIP.equals(mimeType);
  }

  @Override
  public SubripSubtitle parse(byte[] bytes, int offset, int length) {
    ArrayList<Cue> cues = new ArrayList<>();
    LongArray cueTimesUs = new LongArray();
    ParsableByteArray subripData = new ParsableByteArray(bytes, offset + length);
    subripData.setPosition(offset);
    boolean haveEndTimecode;
    String currentLine;

    while ((currentLine = subripData.readLine()) != null) {
      if (currentLine.length() == 0) {
        // Skip blank lines.
        continue;
      }

      // Parse the index line as a sanity check.
      try {
        Integer.parseInt(currentLine);
      } catch (NumberFormatException e) {
        Log.w(TAG, "Skipping invalid index: " + currentLine);
        continue;
      }

      // Read and parse the timing line.
      haveEndTimecode = false;
      currentLine = subripData.readLine();
      Matcher matcher = SUBRIP_TIMING_LINE.matcher(currentLine);
      if (matcher.find()) {
        cueTimesUs.add(parseTimecode(matcher.group(1)));
        String endTimecode = matcher.group(2);
        if (!TextUtils.isEmpty(endTimecode)) {
          haveEndTimecode = true;
          cueTimesUs.add(parseTimecode(matcher.group(2)));
        }
      } else {
        Log.w(TAG, "Skipping invalid timing: " + currentLine);
        continue;
      }

      // Read and parse the text.
      textBuilder.setLength(0);
      while (!TextUtils.isEmpty(currentLine = subripData.readLine())) {
        if (textBuilder.length() > 0) {
          textBuilder.append("<br>");
        }
        textBuilder.append(currentLine.trim());
      }

      Spanned text = Html.fromHtml(textBuilder.toString());
      cues.add(new Cue(text));
      if (haveEndTimecode) {
        cues.add(null);
      }
    }

    Cue[] cuesArray = new Cue[cues.size()];
    cues.toArray(cuesArray);
    long[] cueTimesUsArray = cueTimesUs.toArray();
    return new SubripSubtitle(cuesArray, cueTimesUsArray);
  }

  private static long parseTimecode(String s) throws NumberFormatException {
    Matcher matcher = SUBRIP_TIMESTAMP.matcher(s);
    if (!matcher.matches()) {
      throw new NumberFormatException("has invalid format");
    }
    long timestampMs = Long.parseLong(matcher.group(1)) * 60 * 60 * 1000;
    timestampMs += Long.parseLong(matcher.group(2)) * 60 * 1000;
    timestampMs += Long.parseLong(matcher.group(3)) * 1000;
    timestampMs += Long.parseLong(matcher.group(4));
    return timestampMs * 1000;
  }

}

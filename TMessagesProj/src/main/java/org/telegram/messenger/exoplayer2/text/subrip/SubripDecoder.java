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
package org.telegram.messenger.exoplayer2.text.subrip;

import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import org.telegram.messenger.exoplayer2.text.Cue;
import org.telegram.messenger.exoplayer2.text.SimpleSubtitleDecoder;
import org.telegram.messenger.exoplayer2.util.LongArray;
import org.telegram.messenger.exoplayer2.util.ParsableByteArray;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link SimpleSubtitleDecoder} for SubRip.
 */
public final class SubripDecoder extends SimpleSubtitleDecoder {

  private static final String TAG = "SubripDecoder";

  private static final String SUBRIP_TIMECODE = "(?:(\\d+):)?(\\d+):(\\d+),(\\d+)";
  private static final Pattern SUBRIP_TIMING_LINE =
      Pattern.compile("\\s*(" + SUBRIP_TIMECODE + ")\\s*-->\\s*(" + SUBRIP_TIMECODE + ")?\\s*");

  private final StringBuilder textBuilder;

  public SubripDecoder() {
    super("SubripDecoder");
    textBuilder = new StringBuilder();
  }

  @Override
  protected SubripSubtitle decode(byte[] bytes, int length, boolean reset) {
    ArrayList<Cue> cues = new ArrayList<>();
    LongArray cueTimesUs = new LongArray();
    ParsableByteArray subripData = new ParsableByteArray(bytes, length);
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
      boolean haveEndTimecode = false;
      currentLine = subripData.readLine();
      if (currentLine == null) {
        Log.w(TAG, "Unexpected end");
        break;
      }

      Matcher matcher = SUBRIP_TIMING_LINE.matcher(currentLine);
      if (matcher.matches()) {
        cueTimesUs.add(parseTimecode(matcher, 1));
        if (!TextUtils.isEmpty(matcher.group(6))) {
          haveEndTimecode = true;
          cueTimesUs.add(parseTimecode(matcher, 6));
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

  private static long parseTimecode(Matcher matcher, int groupOffset) {
    long timestampMs = Long.parseLong(matcher.group(groupOffset + 1)) * 60 * 60 * 1000;
    timestampMs += Long.parseLong(matcher.group(groupOffset + 2)) * 60 * 1000;
    timestampMs += Long.parseLong(matcher.group(groupOffset + 3)) * 1000;
    timestampMs += Long.parseLong(matcher.group(groupOffset + 4));
    return timestampMs * 1000;
  }

}

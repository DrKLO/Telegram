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
package org.telegram.messenger.exoplayer.text.ttml;

import android.text.TextUtils;
import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.Util;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser to parse ttml color value expression
 * (http://www.w3.org/TR/ttml1/#style-value-color)
 */
/*package*/ final class TtmlColorParser {

  private static final String RGB = "rgb";
  private static final String RGBA = "rgba";

  private static final Pattern RGB_PATTERN = Pattern.compile(
      "^rgb\\((\\d{1,3}),(\\d{1,3}),(\\d{1,3})\\)$");

  private static final Pattern RGBA_PATTERN = Pattern.compile(
      "^rgba\\((\\d{1,3}),(\\d{1,3}),(\\d{1,3}),(\\d{1,3})\\)$");

  static final int TRANSPARENT = 0x00000000;
  static final int BLACK = 0xFF000000;
  static final int SILVER = 0xFFC0C0C0;
  static final int GRAY = 0xFF808080;
  static final int WHITE = 0xFFFFFFFF;
  static final int MAROON = 0xFF800000;
  static final int RED = 0xFFFF0000;
  static final int PURPLE = 0xFF800080;
  static final int FUCHSIA = 0xFFFF00FF;
  static final int MAGENTA = FUCHSIA;
  static final int GREEN = 0xFF008000;
  static final int LIME = 0xFF00FF00;
  static final int OLIVE = 0xFF808000;
  static final int YELLOW = 0xFFFFFF00;
  static final int NAVY = 0xFF000080;
  static final int BLUE = 0xFF0000FF;
  static final int TEAL = 0xFF008080;
  static final int AQUA = 0x00FFFFFF;
  static final int CYAN = 0xFF00FFFF;

  private static final Map<String, Integer> COLOR_NAME_MAP;
  static {
    COLOR_NAME_MAP = new HashMap<>();
    COLOR_NAME_MAP.put("transparent", TRANSPARENT);
    COLOR_NAME_MAP.put("black", BLACK);
    COLOR_NAME_MAP.put("silver", SILVER);
    COLOR_NAME_MAP.put("gray", GRAY);
    COLOR_NAME_MAP.put("white", WHITE);
    COLOR_NAME_MAP.put("maroon", MAROON);
    COLOR_NAME_MAP.put("red", RED);
    COLOR_NAME_MAP.put("purple", PURPLE);
    COLOR_NAME_MAP.put("fuchsia", FUCHSIA);
    COLOR_NAME_MAP.put("magenta", MAGENTA);
    COLOR_NAME_MAP.put("green", GREEN);
    COLOR_NAME_MAP.put("lime", LIME);
    COLOR_NAME_MAP.put("olive", OLIVE);
    COLOR_NAME_MAP.put("yellow", YELLOW);
    COLOR_NAME_MAP.put("navy", NAVY);
    COLOR_NAME_MAP.put("blue", BLUE);
    COLOR_NAME_MAP.put("teal", TEAL);
    COLOR_NAME_MAP.put("aqua", AQUA);
    COLOR_NAME_MAP.put("cyan", CYAN);
  }

  public static int parseColor(String colorExpression) {
    Assertions.checkArgument(!TextUtils.isEmpty(colorExpression));
    colorExpression = colorExpression.replace(" ", "");
    if (colorExpression.charAt(0) == '#') {
      // Parse using Long to avoid failure when colorExpression is greater than #7FFFFFFF.
      int color = (int) Long.parseLong(colorExpression.substring(1), 16);
      if (colorExpression.length() == 7) {
        // Set the alpha value
        color |= 0xFF000000;
      } else if (colorExpression.length() == 9) {
        // We have #RRGGBBAA, but we need #AARRGGBB
        color = ((color & 0xFF) << 24) | (color >>> 8);
      } else {
        throw new IllegalArgumentException();
      }
      return color;
    } else if (colorExpression.startsWith(RGBA)) {
      Matcher matcher = RGBA_PATTERN.matcher(colorExpression);
      if (matcher.matches()) {
        return argb(
          255 - Integer.parseInt(matcher.group(4), 10),
          Integer.parseInt(matcher.group(1), 10),
          Integer.parseInt(matcher.group(2), 10),
          Integer.parseInt(matcher.group(3), 10)
        );
      }
    } else if (colorExpression.startsWith(RGB)) {
      Matcher matcher = RGB_PATTERN.matcher(colorExpression);
      if (matcher.matches()) {
        return rgb(
          Integer.parseInt(matcher.group(1), 10),
          Integer.parseInt(matcher.group(2), 10),
          Integer.parseInt(matcher.group(3), 10)
        );
      }
    } else {
      // we use our own color map
      Integer color = COLOR_NAME_MAP.get(Util.toLowerInvariant(colorExpression));
      if (color != null) {
        return color;
      }
    }
    throw new IllegalArgumentException();
  }

  private static int argb(int alpha, int red, int green, int blue) {
    return (alpha << 24) | (red << 16) | (green << 8) | blue;
  }

  private static int rgb(int red, int green, int blue) {
    return argb(0xFF, red, green, blue);
  }

}

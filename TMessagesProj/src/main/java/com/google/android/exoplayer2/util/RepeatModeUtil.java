/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.util;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import com.google.android.exoplayer2.Player;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Util class for repeat mode handling. */
public final class RepeatModeUtil {

  /**
   * Set of repeat toggle modes. Can be combined using bit-wise operations. Possible flag values are
   * {@link #REPEAT_TOGGLE_MODE_NONE}, {@link #REPEAT_TOGGLE_MODE_ONE} and {@link
   * #REPEAT_TOGGLE_MODE_ALL}.
   */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef(
      flag = true,
      value = {REPEAT_TOGGLE_MODE_NONE, REPEAT_TOGGLE_MODE_ONE, REPEAT_TOGGLE_MODE_ALL})
  public @interface RepeatToggleModes {}
  /** All repeat mode buttons disabled. */
  public static final int REPEAT_TOGGLE_MODE_NONE = 0;
  /** "Repeat One" button enabled. */
  public static final int REPEAT_TOGGLE_MODE_ONE = 1;
  /** "Repeat All" button enabled. */
  public static final int REPEAT_TOGGLE_MODE_ALL = 1 << 1; // 2

  private RepeatModeUtil() {
    // Prevent instantiation.
  }

  /**
   * Gets the next repeat mode out of {@code enabledModes} starting from {@code currentMode}.
   *
   * @param currentMode The current repeat mode.
   * @param enabledModes Bitmask of enabled modes.
   * @return The next repeat mode.
   */
  public static @Player.RepeatMode int getNextRepeatMode(
      @Player.RepeatMode int currentMode, int enabledModes) {
    for (int offset = 1; offset <= 2; offset++) {
      @Player.RepeatMode int proposedMode = (currentMode + offset) % 3;
      if (isRepeatModeEnabled(proposedMode, enabledModes)) {
        return proposedMode;
      }
    }
    return currentMode;
  }

  /**
   * Verifies whether a given {@code repeatMode} is enabled in the bitmask {@code enabledModes}.
   *
   * @param repeatMode The mode to check.
   * @param enabledModes The bitmask representing the enabled modes.
   * @return {@code true} if enabled.
   */
  public static boolean isRepeatModeEnabled(@Player.RepeatMode int repeatMode, int enabledModes) {
    switch (repeatMode) {
      case Player.REPEAT_MODE_OFF:
        return true;
      case Player.REPEAT_MODE_ONE:
        return (enabledModes & REPEAT_TOGGLE_MODE_ONE) != 0;
      case Player.REPEAT_MODE_ALL:
        return (enabledModes & REPEAT_TOGGLE_MODE_ALL) != 0;
      default:
        return false;
    }
  }
}

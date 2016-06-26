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
package org.telegram.messenger.exoplayer.util;

/**
 * Utility class for managing a set of tags for which verbose logging should be enabled.
 */
public final class VerboseLogUtil {

  private static volatile String[] enabledTags;
  private static volatile boolean enableAllTags;

  private VerboseLogUtil() {}

  /**
   * Sets the tags for which verbose logging should be enabled.
   *
   * @param tags The set of tags.
   */
  public static void setEnabledTags(String... tags) {
    enabledTags = tags;
    enableAllTags = false;
  }

  /**
   * Specifies whether or not all logging should be enabled.
   *
   * @param enable True if all logging should be enabled; false if only tags enabled by
   *     setEnabledTags should have logging enabled.
   */
  public static void setEnableAllTags(boolean enable) {
    enableAllTags = enable;
  }

  /**
   * Checks whether verbose logging should be output for a given tag.
   *
   * @param tag The tag.
   * @return Whether verbose logging should be output for the tag.
   */
  public static boolean isTagEnabled(String tag) {
    if (enableAllTags) {
      return true;
    }

    // Take a local copy of the array to ensure thread safety.
    String[] tags = enabledTags;
    if (tags == null || tags.length == 0) {
      return false;
    }
    for (int i = 0; i < tags.length; i++) {
      if (tags[i].equals(tag)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks whether all logging is enabled;
   *
   * @return True if all logging is enabled; false otherwise.
   */
  public static boolean areAllTagsEnabled() {
    return enableAllTags;
  }

}

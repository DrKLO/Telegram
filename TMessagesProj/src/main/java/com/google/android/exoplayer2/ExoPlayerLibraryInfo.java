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
package com.google.android.exoplayer2;

import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.TraceUtil;
import java.util.HashSet;

/** Information about the media libraries. */
public final class ExoPlayerLibraryInfo {

  /** A tag to use when logging library information. */
  public static final String TAG = "ExoPlayerLib";

  /** The version of the library expressed as a string, for example "1.2.3". */
  // Intentionally hardcoded. Do not derive from other constants (e.g. VERSION_INT) or vice versa.
  public static final String VERSION = "2.18.3";

  /** The version of the library expressed as {@code TAG + "/" + VERSION}. */
  // Intentionally hardcoded. Do not derive from other constants (e.g. VERSION) or vice versa.
  public static final String VERSION_SLASHY = "ExoPlayerLib/2.18.3";

  /**
   * The version of the library expressed as an integer, for example 1002003.
   *
   * <p>Three digits are used for each component of {@link #VERSION}. For example "1.2.3" has the
   * corresponding integer version 1002003 (001-002-003), and "123.45.6" has the corresponding
   * integer version 123045006 (123-045-006).
   */
  // Intentionally hardcoded. Do not derive from other constants (e.g. VERSION) or vice versa.
  public static final int VERSION_INT = 2_018_003;

  /** Whether the library was compiled with {@link Assertions} checks enabled. */
  public static final boolean ASSERTIONS_ENABLED = true;

  /** Whether the library was compiled with {@link TraceUtil} trace enabled. */
  public static final boolean TRACE_ENABLED = true;

  private static final HashSet<String> registeredModules = new HashSet<>();
  private static String registeredModulesString = "goog.exo.core";

  private ExoPlayerLibraryInfo() {} // Prevents instantiation.

  /** Returns a string consisting of registered module names separated by ", ". */
  public static synchronized String registeredModules() {
    return registeredModulesString;
  }

  /**
   * Registers a module to be returned in the {@link #registeredModules()} string.
   *
   * @param name The name of the module being registered.
   */
  public static synchronized void registerModule(String name) {
    if (registeredModules.add(name)) {
      registeredModulesString = registeredModulesString + ", " + name;
    }
  }
}

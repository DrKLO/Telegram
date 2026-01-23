/*
 * This file is a part of X-Core
 * Copyright © Vyacheslav Krylov 2014
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

package me.vkryl.core;

public final class BitwiseUtils {
  private BitwiseUtils () { }

  public static boolean hasFlag (int flags, int flag) {
    return (flags & flag) != 0;
  }

  public static boolean hasFlag (long flags, long flag) {
    return (flags & flag) != 0;
  }

  public static boolean flagChanged (long flags, long oldFlags, long flag) {
    return (flags & flag) != (oldFlags & flag);
  }

  public static boolean hasAllFlags (int flags, int flag) {
    return (flags & flag) == flag;
  }

  public static boolean hasAllFlags (long flags, long flag) {
    return (flags & flag) == flag;
  }

  public static int setFlag (int flags, int flag, boolean enabled) {
    if (enabled) {
      flags |= flag;
    } else {
      flags &= ~flag;
    }
    return flags;
  }

  public static long setFlag (long flags, long flag, boolean enabled) {
    if (enabled) {
      flags |= flag;
    } else {
      flags &= ~flag;
    }
    return flags;
  }

  public static int optional (int flag, boolean condition) {
    return condition ? flag : 0;
  }
}

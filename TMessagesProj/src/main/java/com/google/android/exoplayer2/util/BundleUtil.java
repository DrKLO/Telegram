/*
 * Copyright 2021 The Android Open Source Project
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

import android.os.Bundle;
import android.os.IBinder;
import androidx.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Utilities for {@link Bundle}. */
public final class BundleUtil {

  private static final String TAG = "BundleUtil";

  @Nullable private static Method getIBinderMethod;
  @Nullable private static Method putIBinderMethod;

  /**
   * Gets an {@link IBinder} inside a {@link Bundle} for all Android versions.
   *
   * @param bundle The bundle to get the {@link IBinder}.
   * @param key The key to use while getting the {@link IBinder}.
   * @return The {@link IBinder} that was obtained.
   */
  @Nullable
  public static IBinder getBinder(Bundle bundle, @Nullable String key) {
    if (Util.SDK_INT >= 18) {
      return bundle.getBinder(key);
    } else {
      return getBinderByReflection(bundle, key);
    }
  }

  /**
   * Puts an {@link IBinder} inside a {@link Bundle} for all Android versions.
   *
   * @param bundle The bundle to insert the {@link IBinder}.
   * @param key The key to use while putting the {@link IBinder}.
   * @param binder The {@link IBinder} to put.
   */
  public static void putBinder(Bundle bundle, @Nullable String key, @Nullable IBinder binder) {
    if (Util.SDK_INT >= 18) {
      bundle.putBinder(key, binder);
    } else {
      putBinderByReflection(bundle, key, binder);
    }
  }

  // Method.invoke may take null "key".
  @SuppressWarnings("nullness:argument")
  @Nullable
  private static IBinder getBinderByReflection(Bundle bundle, @Nullable String key) {
    @Nullable Method getIBinder = getIBinderMethod;
    if (getIBinder == null) {
      try {
        getIBinderMethod = Bundle.class.getMethod("getIBinder", String.class);
        getIBinderMethod.setAccessible(true);
      } catch (NoSuchMethodException e) {
        Log.i(TAG, "Failed to retrieve getIBinder method", e);
        return null;
      }
      getIBinder = getIBinderMethod;
    }

    try {
      return (IBinder) getIBinder.invoke(bundle, key);
    } catch (InvocationTargetException | IllegalAccessException | IllegalArgumentException e) {
      Log.i(TAG, "Failed to invoke getIBinder via reflection", e);
      return null;
    }
  }

  // Method.invoke may take null "key" and "binder".
  @SuppressWarnings("nullness:argument")
  private static void putBinderByReflection(
      Bundle bundle, @Nullable String key, @Nullable IBinder binder) {
    @Nullable Method putIBinder = putIBinderMethod;
    if (putIBinder == null) {
      try {
        putIBinderMethod = Bundle.class.getMethod("putIBinder", String.class, IBinder.class);
        putIBinderMethod.setAccessible(true);
      } catch (NoSuchMethodException e) {
        Log.i(TAG, "Failed to retrieve putIBinder method", e);
        return;
      }
      putIBinder = putIBinderMethod;
    }

    try {
      putIBinder.invoke(bundle, key, binder);
    } catch (InvocationTargetException | IllegalAccessException | IllegalArgumentException e) {
      Log.i(TAG, "Failed to invoke putIBinder via reflection", e);
    }
  }

  private BundleUtil() {}
}

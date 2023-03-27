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
package com.google.android.exoplayer2.scheduler;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;

/** Schedules a service to be started in the foreground when some {@link Requirements} are met. */
public interface Scheduler {

  /**
   * Schedules a service to be started in the foreground when some {@link Requirements} are met.
   * Anything that was previously scheduled will be canceled.
   *
   * <p>The service to be started must be declared in the manifest of {@code servicePackage} with an
   * intent filter containing {@code serviceAction}. Note that when started with {@code
   * serviceAction}, the service must call {@link Service#startForeground(int, Notification)} to
   * make itself a foreground service, as documented by {@link
   * Service#startForegroundService(Intent)}.
   *
   * @param requirements The requirements.
   * @param servicePackage The package name.
   * @param serviceAction The action with which the service will be started.
   * @return Whether scheduling was successful.
   */
  boolean schedule(Requirements requirements, String servicePackage, String serviceAction);

  /**
   * Cancels anything that was previously scheduled, or else does nothing.
   *
   * @return Whether cancellation was successful.
   */
  boolean cancel();

  /**
   * Checks whether this {@link Scheduler} supports the provided {@link Requirements}. If all of the
   * requirements are supported then the same {@link Requirements} instance is returned. If not then
   * a new instance is returned containing the subset of the requirements that are supported.
   *
   * @param requirements The requirements to check.
   * @return The supported requirements.
   */
  Requirements getSupportedRequirements(Requirements requirements);
}

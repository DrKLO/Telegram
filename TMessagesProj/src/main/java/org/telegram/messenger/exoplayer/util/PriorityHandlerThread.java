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

import android.os.HandlerThread;
import android.os.Process;

/**
 * A {@link HandlerThread} with a specified process priority.
 */
public final class PriorityHandlerThread extends HandlerThread {

  private final int priority;

  /**
   * @param name The name of the thread.
   * @param priority The priority level. See {@link Process#setThreadPriority(int)} for details.
   */
  public PriorityHandlerThread(String name, int priority) {
    super(name);
    this.priority = priority;
  }

  @Override
  public void run() {
    Process.setThreadPriority(priority);
    super.run();
  }

}

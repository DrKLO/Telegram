/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.offline;

/**
 * Identifies a given track by the index of the containing period, the index of the containing group
 * within the period, and the index of the track within the group.
 */
public final class TrackKey {

  /** The period index. */
  public final int periodIndex;
  /** The group index. */
  public final int groupIndex;
  /** The track index. */
  public final int trackIndex;

  /**
   * @param periodIndex The period index.
   * @param groupIndex The group index.
   * @param trackIndex The track index.
   */
  public TrackKey(int periodIndex, int groupIndex, int trackIndex) {
    this.periodIndex = periodIndex;
    this.groupIndex = groupIndex;
    this.trackIndex = trackIndex;
  }
}

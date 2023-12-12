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
package com.google.android.exoplayer2.text;

import java.util.List;

/** Receives text output. */
public interface TextOutput {

  /**
   * Called when there is a change in the {@link Cue Cues}.
   *
   * <p>Both {@link #onCues(List)} and {@link #onCues(CueGroup)} are called when there is a change
   * in the cues. You should only implement one or the other.
   *
   * @deprecated Use {@link #onCues(CueGroup)} instead.
   */
  @Deprecated
  default void onCues(List<Cue> cues) {}

  /**
   * Called when there is a change in the {@link CueGroup}.
   *
   * <p>Both {@link #onCues(List)} and {@link #onCues(CueGroup)} are called when there is a change
   * in the cues. You should only implement one or the other.
   */
  void onCues(CueGroup cueGroup);
}

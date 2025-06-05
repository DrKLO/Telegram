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
package com.google.android.exoplayer2.text.cea;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.util.Assertions;
import java.util.Collections;
import java.util.List;

/** A representation of a CEA subtitle. */
/* package */ final class CeaSubtitle implements Subtitle {

  private final List<Cue> cues;

  /**
   * @param cues The subtitle cues.
   */
  public CeaSubtitle(List<Cue> cues) {
    this.cues = cues;
  }

  @Override
  public int getNextEventTimeIndex(long timeUs) {
    return timeUs < 0 ? 0 : C.INDEX_UNSET;
  }

  @Override
  public int getEventTimeCount() {
    return 1;
  }

  @Override
  public long getEventTime(int index) {
    Assertions.checkArgument(index == 0);
    return 0;
  }

  @Override
  public List<Cue> getCues(long timeUs) {
    return timeUs >= 0 ? cues : Collections.emptyList();
  }
}

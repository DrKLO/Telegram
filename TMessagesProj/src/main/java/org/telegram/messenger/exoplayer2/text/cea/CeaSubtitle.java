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
package org.telegram.messenger.exoplayer2.text.cea;

import org.telegram.messenger.exoplayer2.text.Cue;
import org.telegram.messenger.exoplayer2.text.Subtitle;
import java.util.Collections;
import java.util.List;

/**
 * A representation of a CEA subtitle.
 */
/* package */ final class CeaSubtitle implements Subtitle {

  private final List<Cue> cues;

  /**
   * @param cue The subtitle cue.
   */
  public CeaSubtitle(Cue cue) {
    if (cue == null) {
      cues = Collections.emptyList();
    } else {
      cues = Collections.singletonList(cue);
    }
  }

  @Override
  public int getNextEventTimeIndex(long timeUs) {
    return 0;
  }

  @Override
  public int getEventTimeCount() {
    return 1;
  }

  @Override
  public long getEventTime(int index) {
    return 0;
  }

  @Override
  public List<Cue> getCues(long timeUs) {
    return cues;

  }

}

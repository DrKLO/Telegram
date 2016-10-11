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
package org.telegram.messenger.exoplayer.text.tx3g;

import org.telegram.messenger.exoplayer.text.Cue;
import org.telegram.messenger.exoplayer.text.Subtitle;
import org.telegram.messenger.exoplayer.util.Assertions;
import java.util.Collections;
import java.util.List;

/**
 * A representation of a tx3g subtitle.
 */
/* package */ final class Tx3gSubtitle implements Subtitle {

  public static final Tx3gSubtitle EMPTY = new Tx3gSubtitle();

  private final List<Cue> cues;

  public Tx3gSubtitle(Cue cue) {
    this.cues = Collections.singletonList(cue);
  }

  private Tx3gSubtitle() {
    this.cues = Collections.emptyList();
  }

  @Override
  public int getNextEventTimeIndex(long timeUs) {
    return timeUs < 0 ? 0 : -1;
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
  public long getLastEventTime() {
    return 0;
  }

  @Override
  public List<Cue> getCues(long timeUs) {
    return timeUs >= 0 ? cues : Collections.<Cue>emptyList();
  }

}

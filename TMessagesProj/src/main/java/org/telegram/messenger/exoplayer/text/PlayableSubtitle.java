package org.telegram.messenger.exoplayer.text;

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
import java.util.List;

/**
 * A subtitle that wraps another subtitle, making it playable by adjusting it to be correctly
 * aligned with the playback timebase.
 */
/* package */ final class PlayableSubtitle implements Subtitle {

  /**
   * The start time of the subtitle.
   * <p>
   * May be less than {@code getEventTime(0)}, since a subtitle may begin prior to the time of the
   * first event.
   */
  public final long startTimeUs;

  private final Subtitle subtitle;
  private final long offsetUs;

  /**
   * @param subtitle The subtitle to wrap.
   * @param isRelative True if the wrapped subtitle's timestamps are relative to the start time.
   *     False if they are absolute.
   * @param startTimeUs The start time of the subtitle.
   * @param offsetUs An offset to add to the subtitle timestamps.
   */
  public PlayableSubtitle(Subtitle subtitle, boolean isRelative, long startTimeUs, long offsetUs) {
    this.subtitle = subtitle;
    this.startTimeUs = startTimeUs;
    this.offsetUs = (isRelative ? startTimeUs : 0) + offsetUs;
  }

  @Override
  public int getEventTimeCount() {
    return subtitle.getEventTimeCount();
  }

  @Override
  public long getEventTime(int index) {
    return subtitle.getEventTime(index) + offsetUs;
  }

  @Override
  public long getLastEventTime() {
    return subtitle.getLastEventTime() + offsetUs;
  }

  @Override
  public int getNextEventTimeIndex(long timeUs) {
    return subtitle.getNextEventTimeIndex(timeUs - offsetUs);
  }

  @Override
  public List<Cue> getCues(long timeUs) {
    return subtitle.getCues(timeUs - offsetUs);
  }

}

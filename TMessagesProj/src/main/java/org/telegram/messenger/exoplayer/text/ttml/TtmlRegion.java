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
package org.telegram.messenger.exoplayer.text.ttml;

import org.telegram.messenger.exoplayer.text.Cue;

/**
 * Represents a TTML Region.
 */
/* package */ final class TtmlRegion {

  public final float position;
  public final float line;
  public final int lineType;
  public final float width;

  public TtmlRegion() {
    this(Cue.DIMEN_UNSET, Cue.DIMEN_UNSET, Cue.TYPE_UNSET, Cue.DIMEN_UNSET);
  }

  public TtmlRegion(float position, float line, int lineType, float width) {
    this.position = position;
    this.line = line;
    this.lineType = lineType;
    this.width = width;
  }

}

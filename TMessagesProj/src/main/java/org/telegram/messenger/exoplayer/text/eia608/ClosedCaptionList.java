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
package org.telegram.messenger.exoplayer.text.eia608;

/* package */ final class ClosedCaptionList implements Comparable<ClosedCaptionList> {

  public final long timeUs;
  public final boolean decodeOnly;
  public final ClosedCaption[] captions;

  public ClosedCaptionList(long timeUs, boolean decodeOnly, ClosedCaption[] captions) {
    this.timeUs = timeUs;
    this.decodeOnly = decodeOnly;
    this.captions = captions;
  }

  @Override
  public int compareTo(ClosedCaptionList other) {
    long delta = timeUs - other.timeUs;
    if (delta == 0) {
      return 0;
    }
    return delta > 0 ? 1 : -1;
  }

}

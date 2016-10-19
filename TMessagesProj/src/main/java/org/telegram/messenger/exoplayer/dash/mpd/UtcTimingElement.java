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
package org.telegram.messenger.exoplayer.dash.mpd;

/**
 * Represents a UTCTiming element.
 */
public final class UtcTimingElement {

  public final String schemeIdUri;
  public final String value;

  public UtcTimingElement(String schemeIdUri, String value) {
    this.schemeIdUri = schemeIdUri;
    this.value = value;
  }

  @Override
  public String toString() {
    return schemeIdUri + ", " + value;
  }

}

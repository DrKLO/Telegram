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
package com.google.android.exoplayer2.metadata.scte35;

import com.google.android.exoplayer2.metadata.Metadata;

/** Superclass for SCTE35 splice commands. */
public abstract class SpliceCommand implements Metadata.Entry {

  @Override
  public String toString() {
    return "SCTE-35 splice command: type=" + getClass().getSimpleName();
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }
}

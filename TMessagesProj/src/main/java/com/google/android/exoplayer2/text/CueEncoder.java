/*
 * Copyright 2021 The Android Open Source Project
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

import android.os.Bundle;
import android.os.Parcel;
import com.google.android.exoplayer2.util.BundleableUtil;
import java.util.ArrayList;
import java.util.List;

/** Encodes data that can be decoded by {@link CueDecoder}. */
public final class CueEncoder {
  /**
   * Encodes an {@link List} of {@link Cue} to a byte array that can be decoded by {@link
   * CueDecoder}.
   *
   * @param cues Cues to be encoded.
   * @return The serialized byte array.
   */
  public byte[] encode(List<Cue> cues) {
    ArrayList<Bundle> bundledCues = BundleableUtil.toBundleArrayList(cues);
    Bundle allCuesBundle = new Bundle();
    allCuesBundle.putParcelableArrayList(CueDecoder.BUNDLED_CUES, bundledCues);
    Parcel parcel = Parcel.obtain();
    parcel.writeBundle(allCuesBundle);
    byte[] bytes = parcel.marshall();
    parcel.recycle();

    return bytes;
  }
}

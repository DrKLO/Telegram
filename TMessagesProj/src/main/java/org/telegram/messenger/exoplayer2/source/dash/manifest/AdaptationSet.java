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
package org.telegram.messenger.exoplayer2.source.dash.manifest;

import java.util.Collections;
import java.util.List;

/**
 * Represents a set of interchangeable encoded versions of a media content component.
 */
public class AdaptationSet {

  /**
   * Value of {@link #id} indicating no value is set.=
   */
  public static final int ID_UNSET = -1;

  /**
   * A non-negative identifier for the adaptation set that's unique in the scope of its containing
   * period, or {@link #ID_UNSET} if not specified.
   */
  public final int id;

  /**
   * The type of the adaptation set. One of the {@link org.telegram.messenger.exoplayer2.C}
   * {@code TRACK_TYPE_*} constants.
   */
  public final int type;

  /**
   * The {@link Representation}s in the adaptation set.
   */
  public final List<Representation> representations;

  /**
   * The accessibility descriptors in the adaptation set.
   */
  public final List<SchemeValuePair> accessibilityDescriptors;

  /**
   * @param id A non-negative identifier for the adaptation set that's unique in the scope of its
   *     containing period, or {@link #ID_UNSET} if not specified.
   * @param type The type of the adaptation set. One of the {@link org.telegram.messenger.exoplayer2.C}
   *     {@code TRACK_TYPE_*} constants.
   * @param representations The {@link Representation}s in the adaptation set.
   * @param accessibilityDescriptors The accessibility descriptors in the adaptation set.
   */
  public AdaptationSet(int id, int type, List<Representation> representations,
      List<SchemeValuePair> accessibilityDescriptors) {
    this.id = id;
    this.type = type;
    this.representations = Collections.unmodifiableList(representations);
    this.accessibilityDescriptors = accessibilityDescriptors == null
        ? Collections.<SchemeValuePair>emptyList()
        : Collections.unmodifiableList(accessibilityDescriptors);
  }

}

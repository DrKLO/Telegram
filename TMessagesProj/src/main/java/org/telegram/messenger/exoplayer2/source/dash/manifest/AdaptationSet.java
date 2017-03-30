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

  public static final int UNSET_ID = -1;

  public final int id;

  public final int type;

  public final List<Representation> representations;

  public AdaptationSet(int id, int type, List<Representation> representations) {
    this.id = id;
    this.type = type;
    this.representations = Collections.unmodifiableList(representations);
  }

}

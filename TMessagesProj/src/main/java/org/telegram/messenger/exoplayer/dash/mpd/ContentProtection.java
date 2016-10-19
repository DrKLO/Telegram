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

import org.telegram.messenger.exoplayer.drm.DrmInitData.SchemeInitData;
import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.Util;
import java.util.UUID;

/**
 * Represents a ContentProtection tag in an AdaptationSet.
 */
public class ContentProtection {

  /**
   * Identifies the content protection scheme.
   */
  public final String schemeUriId;

  /**
   * The UUID of the protection scheme. May be null.
   */
  public final UUID uuid;

  /**
   * Protection scheme specific initialization data. May be null.
   */
  public final SchemeInitData data;

  /**
   * @param schemeUriId Identifies the content protection scheme.
   * @param uuid The UUID of the protection scheme, if known. May be null.
   * @param data Protection scheme specific initialization data. May be null.
   */
  public ContentProtection(String schemeUriId, UUID uuid, SchemeInitData data) {
    this.schemeUriId = Assertions.checkNotNull(schemeUriId);
    this.uuid = uuid;
    this.data = data;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ContentProtection)) {
      return false;
    }
    if (obj == this) {
      return true;
    }

    ContentProtection other = (ContentProtection) obj;
    return schemeUriId.equals(other.schemeUriId)
        && Util.areEqual(uuid, other.uuid)
        && Util.areEqual(data, other.data);
  }

  @Override
  public int hashCode() {
    int hashCode = schemeUriId.hashCode();
    hashCode = (37 * hashCode) + (uuid != null ? uuid.hashCode() : 0);
    hashCode = (37 * hashCode) + (data != null ? data.hashCode() : 0);
    return hashCode;
  }

}

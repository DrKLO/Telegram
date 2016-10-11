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
package org.telegram.messenger.exoplayer.drm;

import android.media.MediaDrm;
import org.telegram.messenger.exoplayer.util.Assertions;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Encapsulates initialization data required by a {@link MediaDrm} instances.
 */
public interface DrmInitData {

  /**
   * Retrieves initialization data for a given DRM scheme, specified by its UUID.
   *
   * @param schemeUuid The DRM scheme's UUID.
   * @return The initialization data for the scheme, or null if the scheme is not supported.
   */
  public abstract SchemeInitData get(UUID schemeUuid);

  /**
   * A {@link DrmInitData} implementation that maps UUID onto scheme specific data.
   */
  public static final class Mapped implements DrmInitData {

    private final Map<UUID, SchemeInitData> schemeData;

    public Mapped() {
      schemeData = new HashMap<>();
    }

    @Override
    public SchemeInitData get(UUID schemeUuid) {
      return schemeData.get(schemeUuid);
    }

    /**
     * Inserts scheme specific initialization data.
     *
     * @param schemeUuid The scheme UUID.
     * @param schemeInitData The corresponding initialization data.
     */
    public void put(UUID schemeUuid, SchemeInitData schemeInitData) {
      schemeData.put(schemeUuid, schemeInitData);
    }

  }

  /**
   * A {@link DrmInitData} implementation that returns the same initialization data for all schemes.
   */
  public static final class Universal implements DrmInitData {

    private SchemeInitData data;

    public Universal(SchemeInitData data) {
      this.data = data;
    }

    @Override
    public SchemeInitData get(UUID schemeUuid) {
      return data;
    }

  }

  /**
   * Scheme initialization data.
   */
  public static final class SchemeInitData {

    /**
     * The mimeType of {@link #data}.
     */
    public final String mimeType;
    /**
     * The initialization data.
     */
    public final byte[] data;

    /**
     * @param mimeType The mimeType of the initialization data.
     * @param data The initialization data.
     */
    public SchemeInitData(String mimeType, byte[] data) {
      this.mimeType = Assertions.checkNotNull(mimeType);
      this.data = Assertions.checkNotNull(data);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof SchemeInitData)) {
        return false;
      }
      if (obj == this) {
        return true;
      }

      SchemeInitData other = (SchemeInitData) obj;
      return mimeType.equals(other.mimeType) && Arrays.equals(data, other.data);
    }

    @Override
    public int hashCode() {
      return mimeType.hashCode() + 31 * Arrays.hashCode(data);
    }

  }

}

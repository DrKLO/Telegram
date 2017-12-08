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
package org.telegram.messenger.exoplayer2.drm;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.drm.DrmInitData.SchemeData;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.Util;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Initialization data for one or more DRM schemes.
 */
public final class DrmInitData implements Comparator<SchemeData>, Parcelable {

  private final SchemeData[] schemeDatas;

  // Lazily initialized hashcode.
  private int hashCode;

  /**
   * Number of {@link SchemeData}s.
   */
  public final int schemeDataCount;

  /**
   * @param schemeDatas Scheme initialization data for possibly multiple DRM schemes.
   */
  public DrmInitData(List<SchemeData> schemeDatas) {
    this(false, schemeDatas.toArray(new SchemeData[schemeDatas.size()]));
  }

  /**
   * @param schemeDatas Scheme initialization data for possibly multiple DRM schemes.
   */
  public DrmInitData(SchemeData... schemeDatas) {
    this(true, schemeDatas);
  }

  private DrmInitData(boolean cloneSchemeDatas, SchemeData... schemeDatas) {
    if (cloneSchemeDatas) {
      schemeDatas = schemeDatas.clone();
    }
    // Sorting ensures that universal scheme data(i.e. data that applies to all schemes) is matched
    // last. It's also required by the equals and hashcode implementations.
    Arrays.sort(schemeDatas, this);
    // Check for no duplicates.
    for (int i = 1; i < schemeDatas.length; i++) {
      if (schemeDatas[i - 1].uuid.equals(schemeDatas[i].uuid)) {
        throw new IllegalArgumentException("Duplicate data for uuid: " + schemeDatas[i].uuid);
      }
    }
    this.schemeDatas = schemeDatas;
    schemeDataCount = schemeDatas.length;
  }

  /* package */ DrmInitData(Parcel in) {
    schemeDatas = in.createTypedArray(SchemeData.CREATOR);
    schemeDataCount = schemeDatas.length;
  }

  /**
   * Retrieves data for a given DRM scheme, specified by its UUID.
   *
   * @param uuid The DRM scheme's UUID.
   * @return The initialization data for the scheme, or null if the scheme is not supported.
   */
  public SchemeData get(UUID uuid) {
    for (SchemeData schemeData : schemeDatas) {
      if (schemeData.matches(uuid)) {
        return schemeData;
      }
    }
    return null;
  }

  /**
   * Retrieves the {@link SchemeData} at a given index.
   *
   * @param index index of the scheme to return.
   * @return The {@link SchemeData} at the index.
   */
  public SchemeData get(int index) {
    return schemeDatas[index];
  }

  /**
   * Returns a copy of the {@link DrmInitData} instance whose {@link SchemeData}s have been updated
   * to have the specified scheme type.
   *
   * @param schemeType A protection scheme type. May be null.
   * @return A copy of the {@link DrmInitData} instance whose {@link SchemeData}s have been updated
   *     to have the specified scheme type.
   */
  public DrmInitData copyWithSchemeType(@Nullable String schemeType) {
    boolean isCopyRequired = false;
    for (SchemeData schemeData : schemeDatas) {
      if (!Util.areEqual(schemeData.type, schemeType)) {
        isCopyRequired = true;
        break;
      }
    }
    if (isCopyRequired) {
      SchemeData[] schemeDatas = new SchemeData[this.schemeDatas.length];
      for (int i = 0; i < schemeDatas.length; i++) {
        schemeDatas[i] = this.schemeDatas[i].copyWithSchemeType(schemeType);
      }
      return new DrmInitData(schemeDatas);
    } else {
      return this;
    }
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      hashCode = Arrays.hashCode(schemeDatas);
    }
    return hashCode;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    return Arrays.equals(schemeDatas, ((DrmInitData) obj).schemeDatas);
  }

  @Override
  public int compare(SchemeData first, SchemeData second) {
    return C.UUID_NIL.equals(first.uuid) ? (C.UUID_NIL.equals(second.uuid) ? 0 : 1)
        : first.uuid.compareTo(second.uuid);
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeTypedArray(schemeDatas, 0);
  }

  public static final Parcelable.Creator<DrmInitData> CREATOR =
      new Parcelable.Creator<DrmInitData>() {

    @Override
    public DrmInitData createFromParcel(Parcel in) {
      return new DrmInitData(in);
    }

    @Override
    public DrmInitData[] newArray(int size) {
      return new DrmInitData[size];
    }

  };

  /**
   * Scheme initialization data.
   */
  public static final class SchemeData implements Parcelable {

    // Lazily initialized hashcode.
    private int hashCode;

    /**
     * The {@link UUID} of the DRM scheme, or {@link C#UUID_NIL} if the data is universal (i.e.
     * applies to all schemes).
     */
    private final UUID uuid;
    /**
     * The protection scheme type, or null if not applicable or unknown.
     */
    @Nullable public final String type;
    /**
     * The mimeType of {@link #data}.
     */
    public final String mimeType;
    /**
     * The initialization data.
     */
    public final byte[] data;
    /**
     * Whether secure decryption is required.
     */
    public final boolean requiresSecureDecryption;

    /**
     * @param uuid The {@link UUID} of the DRM scheme, or {@link C#UUID_NIL} if the data is
     *     universal (i.e. applies to all schemes).
     * @param type The type of the protection scheme, or null if not applicable or unknown.
     * @param mimeType The mimeType of the initialization data.
     * @param data The initialization data.
     */
    public SchemeData(UUID uuid, @Nullable String type, String mimeType, byte[] data) {
      this(uuid, type, mimeType, data, false);
    }

    /**
     * @param uuid The {@link UUID} of the DRM scheme, or {@link C#UUID_NIL} if the data is
     *     universal (i.e. applies to all schemes).
     * @param type The type of the protection scheme, or null if not applicable or unknown.
     * @param mimeType The mimeType of the initialization data.
     * @param data The initialization data.
     * @param requiresSecureDecryption Whether secure decryption is required.
     */
    public SchemeData(UUID uuid, @Nullable String type, String mimeType, byte[] data,
        boolean requiresSecureDecryption) {
      this.uuid = Assertions.checkNotNull(uuid);
      this.type = type;
      this.mimeType = Assertions.checkNotNull(mimeType);
      this.data = Assertions.checkNotNull(data);
      this.requiresSecureDecryption = requiresSecureDecryption;
    }

    /* package */ SchemeData(Parcel in) {
      uuid = new UUID(in.readLong(), in.readLong());
      type = in.readString();
      mimeType = in.readString();
      data = in.createByteArray();
      requiresSecureDecryption = in.readByte() != 0;
    }

    /**
     * Returns whether this initialization data applies to the specified scheme.
     *
     * @param schemeUuid The scheme {@link UUID}.
     * @return Whether this initialization data applies to the specified scheme.
     */
    public boolean matches(UUID schemeUuid) {
      return C.UUID_NIL.equals(uuid) || schemeUuid.equals(uuid);
    }

    /**
     * Returns a copy of the {@link SchemeData} instance with the given scheme type.
     *
     * @param type A protection scheme type.
     * @return A copy of the {@link SchemeData} instance with the given scheme type.
     */
    public SchemeData copyWithSchemeType(String type) {
      if (Util.areEqual(this.type, type)) {
        return this;
      }
      return new SchemeData(uuid, type, mimeType, data, requiresSecureDecryption);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof SchemeData)) {
        return false;
      }
      if (obj == this) {
        return true;
      }
      SchemeData other = (SchemeData) obj;
      return mimeType.equals(other.mimeType) && Util.areEqual(uuid, other.uuid)
          && Util.areEqual(type, other.type) && Arrays.equals(data, other.data);
    }

    @Override
    public int hashCode() {
      if (hashCode == 0) {
        int result = uuid.hashCode();
        result = 31 * result + (type == null ? 0 : type.hashCode());
        result = 31 * result + mimeType.hashCode();
        result = 31 * result + Arrays.hashCode(data);
        hashCode = result;
      }
      return hashCode;
    }

    // Parcelable implementation.

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeLong(uuid.getMostSignificantBits());
      dest.writeLong(uuid.getLeastSignificantBits());
      dest.writeString(type);
      dest.writeString(mimeType);
      dest.writeByteArray(data);
      dest.writeByte((byte) (requiresSecureDecryption ? 1 : 0));
    }

    @SuppressWarnings("hiding")
    public static final Parcelable.Creator<SchemeData> CREATOR =
        new Parcelable.Creator<SchemeData>() {

      @Override
      public SchemeData createFromParcel(Parcel in) {
        return new SchemeData(in);
      }

      @Override
      public SchemeData[] newArray(int size) {
        return new SchemeData[size];
      }

    };

  }

}

/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.metadata.emsg;

import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.util.Util;
import java.util.Arrays;

/**
 * An Event Message (emsg) as defined in ISO 23009-1.
 */
public final class EventMessage implements Metadata.Entry {

  /**
   * The message scheme.
   */
  public final String schemeIdUri;

  /**
   * The value for the event.
   */
  public final String value;

  /**
   * The duration of the event in milliseconds.
   */
  public final long durationMs;

  /**
   * The presentation time value of this event message in microseconds.
   * <p>
   * Except in special cases, application code should <em>not</em> use this field.
   */
  public final long presentationTimeUs;

  /**
   * The instance identifier.
   */
  public final long id;

  /**
   * The body of the message.
   */
  public final byte[] messageData;

  // Lazily initialized hashcode.
  private int hashCode;

  /**
   * @param schemeIdUri The message scheme.
   * @param value The value for the event.
   * @param durationMs The duration of the event in milliseconds.
   * @param id The instance identifier.
   * @param messageData The body of the message.
   * @param presentationTimeUs The presentation time value of this event message in microseconds.
   */
  public EventMessage(String schemeIdUri, String value, long durationMs, long id,
      byte[] messageData, long presentationTimeUs) {
    this.schemeIdUri = schemeIdUri;
    this.value = value;
    this.durationMs = durationMs;
    this.id = id;
    this.messageData = messageData;
    this.presentationTimeUs = presentationTimeUs;
  }

  /* package */ EventMessage(Parcel in) {
    schemeIdUri = castNonNull(in.readString());
    value = castNonNull(in.readString());
    presentationTimeUs = in.readLong();
    durationMs = in.readLong();
    id = in.readLong();
    messageData = castNonNull(in.createByteArray());
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      int result = 17;
      result = 31 * result + (schemeIdUri != null ? schemeIdUri.hashCode() : 0);
      result = 31 * result + (value != null ? value.hashCode() : 0);
      result = 31 * result + (int) (presentationTimeUs ^ (presentationTimeUs >>> 32));
      result = 31 * result + (int) (durationMs ^ (durationMs >>> 32));
      result = 31 * result + (int) (id ^ (id >>> 32));
      result = 31 * result + Arrays.hashCode(messageData);
      hashCode = result;
    }
    return hashCode;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    EventMessage other = (EventMessage) obj;
    return presentationTimeUs == other.presentationTimeUs && durationMs == other.durationMs
        && id == other.id && Util.areEqual(schemeIdUri, other.schemeIdUri)
        && Util.areEqual(value, other.value) && Arrays.equals(messageData, other.messageData);
  }

  @Override
  public String toString() {
    return "EMSG: scheme=" + schemeIdUri + ", id=" + id + ", value=" + value;
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(schemeIdUri);
    dest.writeString(value);
    dest.writeLong(presentationTimeUs);
    dest.writeLong(durationMs);
    dest.writeLong(id);
    dest.writeByteArray(messageData);
  }

  public static final Parcelable.Creator<EventMessage> CREATOR =
      new Parcelable.Creator<EventMessage>() {

    @Override
    public EventMessage createFromParcel(Parcel in) {
      return new EventMessage(in);
    }

    @Override
    public EventMessage[] newArray(int size) {
      return new EventMessage[size];
    }

  };

}

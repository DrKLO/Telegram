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
package org.telegram.messenger.exoplayer2.metadata.id3;

import android.os.Parcel;
import android.os.Parcelable;
import org.telegram.messenger.exoplayer2.util.Util;

/**
 * Comment ID3 frame.
 */
public final class CommentFrame extends Id3Frame {

  public static final String ID = "COMM";

  public final String language;
  public final String description;
  public final String text;

  public CommentFrame(String language, String description, String text) {
    super(ID);
    this.language = language;
    this.description = description;
    this.text = text;
  }

  /* package */ CommentFrame(Parcel in) {
    super(ID);
    language = in.readString();
    description = in.readString();
    text = in.readString();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    CommentFrame other = (CommentFrame) obj;
    return Util.areEqual(description, other.description) && Util.areEqual(language, other.language)
        && Util.areEqual(text, other.text);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + (language != null ? language.hashCode() : 0);
    result = 31 * result + (description != null ? description.hashCode() : 0);
    result = 31 * result + (text != null ? text.hashCode() : 0);
    return result;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(id);
    dest.writeString(language);
    dest.writeString(text);
  }

  public static final Parcelable.Creator<CommentFrame> CREATOR =
      new Parcelable.Creator<CommentFrame>() {

        @Override
        public CommentFrame createFromParcel(Parcel in) {
          return new CommentFrame(in);
        }

        @Override
        public CommentFrame[] newArray(int size) {
          return new CommentFrame[size];
        }

      };

}

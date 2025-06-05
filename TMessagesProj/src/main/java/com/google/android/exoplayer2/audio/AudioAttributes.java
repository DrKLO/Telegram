/*
 * Copyright 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.audio;

import android.os.Bundle;
import androidx.annotation.DoNotInline;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.Bundleable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Util;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * Attributes for audio playback, which configure the underlying platform {@link
 * android.media.AudioTrack}.
 *
 * <p>To set the audio attributes, create an instance using the {@link Builder} and either pass it
 * to the player or send a message of type {@code Renderer#MSG_SET_AUDIO_ATTRIBUTES} to the audio
 * renderers.
 *
 * <p>This class is based on {@link android.media.AudioAttributes}, but can be used on all supported
 * API versions.
 */
public final class AudioAttributes implements Bundleable {

  /** A direct wrapper around {@link android.media.AudioAttributes}. */
  @RequiresApi(21)
  public static final class AudioAttributesV21 {
    public final android.media.AudioAttributes audioAttributes;

    private AudioAttributesV21(AudioAttributes audioAttributes) {
      android.media.AudioAttributes.Builder builder =
          new android.media.AudioAttributes.Builder()
              .setContentType(audioAttributes.contentType)
              .setFlags(audioAttributes.flags)
              .setUsage(audioAttributes.usage);
      if (Util.SDK_INT >= 29) {
        Api29.setAllowedCapturePolicy(builder, audioAttributes.allowedCapturePolicy);
      }
      if (Util.SDK_INT >= 32) {
        Api32.setSpatializationBehavior(builder, audioAttributes.spatializationBehavior);
      }
      this.audioAttributes = builder.build();
    }
  }

  /**
   * The default audio attributes, where the content type is {@link C#AUDIO_CONTENT_TYPE_UNKNOWN},
   * usage is {@link C#USAGE_MEDIA}, capture policy is {@link C#ALLOW_CAPTURE_BY_ALL} and no flags
   * are set.
   */
  public static final AudioAttributes DEFAULT = new Builder().build();

  /** Builder for {@link AudioAttributes}. */
  public static final class Builder {

    private @C.AudioContentType int contentType;
    private @C.AudioFlags int flags;
    private @C.AudioUsage int usage;
    private @C.AudioAllowedCapturePolicy int allowedCapturePolicy;
    private @C.SpatializationBehavior int spatializationBehavior;

    /**
     * Creates a new builder for {@link AudioAttributes}.
     *
     * <p>By default the content type is {@link C#AUDIO_CONTENT_TYPE_UNKNOWN}, usage is {@link
     * C#USAGE_MEDIA}, capture policy is {@link C#ALLOW_CAPTURE_BY_ALL} and no flags are set.
     */
    public Builder() {
      contentType = C.AUDIO_CONTENT_TYPE_UNKNOWN;
      flags = 0;
      usage = C.USAGE_MEDIA;
      allowedCapturePolicy = C.ALLOW_CAPTURE_BY_ALL;
      spatializationBehavior = C.SPATIALIZATION_BEHAVIOR_AUTO;
    }

    /** See {@link android.media.AudioAttributes.Builder#setContentType(int)} */
    @CanIgnoreReturnValue
    public Builder setContentType(@C.AudioContentType int contentType) {
      this.contentType = contentType;
      return this;
    }

    /** See {@link android.media.AudioAttributes.Builder#setFlags(int)} */
    @CanIgnoreReturnValue
    public Builder setFlags(@C.AudioFlags int flags) {
      this.flags = flags;
      return this;
    }

    /** See {@link android.media.AudioAttributes.Builder#setUsage(int)} */
    @CanIgnoreReturnValue
    public Builder setUsage(@C.AudioUsage int usage) {
      this.usage = usage;
      return this;
    }

    /** See {@link android.media.AudioAttributes.Builder#setAllowedCapturePolicy(int)}. */
    @CanIgnoreReturnValue
    public Builder setAllowedCapturePolicy(@C.AudioAllowedCapturePolicy int allowedCapturePolicy) {
      this.allowedCapturePolicy = allowedCapturePolicy;
      return this;
    }

    /** See {@link android.media.AudioAttributes.Builder#setSpatializationBehavior(int)}. */
    @CanIgnoreReturnValue
    public Builder setSpatializationBehavior(@C.SpatializationBehavior int spatializationBehavior) {
      this.spatializationBehavior = spatializationBehavior;
      return this;
    }

    /** Creates an {@link AudioAttributes} instance from this builder. */
    public AudioAttributes build() {
      return new AudioAttributes(
          contentType, flags, usage, allowedCapturePolicy, spatializationBehavior);
    }
  }

  /** The {@link C.AudioContentType}. */
  public final @C.AudioContentType int contentType;
  /** The {@link C.AudioFlags}. */
  public final @C.AudioFlags int flags;
  /** The {@link C.AudioUsage}. */
  public final @C.AudioUsage int usage;
  /** The {@link C.AudioAllowedCapturePolicy}. */
  public final @C.AudioAllowedCapturePolicy int allowedCapturePolicy;
  /** The {@link C.SpatializationBehavior}. */
  public final @C.SpatializationBehavior int spatializationBehavior;

  @Nullable private AudioAttributesV21 audioAttributesV21;

  private AudioAttributes(
      @C.AudioContentType int contentType,
      @C.AudioFlags int flags,
      @C.AudioUsage int usage,
      @C.AudioAllowedCapturePolicy int allowedCapturePolicy,
      @C.SpatializationBehavior int spatializationBehavior) {
    this.contentType = contentType;
    this.flags = flags;
    this.usage = usage;
    this.allowedCapturePolicy = allowedCapturePolicy;
    this.spatializationBehavior = spatializationBehavior;
  }

  /**
   * Returns a {@link AudioAttributesV21} from this instance.
   *
   * <p>Some fields are ignored if the corresponding {@link android.media.AudioAttributes.Builder}
   * setter is not available on the current API level.
   */
  @RequiresApi(21)
  public AudioAttributesV21 getAudioAttributesV21() {
    if (audioAttributesV21 == null) {
      audioAttributesV21 = new AudioAttributesV21(this);
    }
    return audioAttributesV21;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    AudioAttributes other = (AudioAttributes) obj;
    return this.contentType == other.contentType
        && this.flags == other.flags
        && this.usage == other.usage
        && this.allowedCapturePolicy == other.allowedCapturePolicy
        && this.spatializationBehavior == other.spatializationBehavior;
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + contentType;
    result = 31 * result + flags;
    result = 31 * result + usage;
    result = 31 * result + allowedCapturePolicy;
    result = 31 * result + spatializationBehavior;
    return result;
  }

  // Bundleable implementation.

  private static final String FIELD_CONTENT_TYPE = Util.intToStringMaxRadix(0);
  private static final String FIELD_FLAGS = Util.intToStringMaxRadix(1);
  private static final String FIELD_USAGE = Util.intToStringMaxRadix(2);
  private static final String FIELD_ALLOWED_CAPTURE_POLICY = Util.intToStringMaxRadix(3);
  private static final String FIELD_SPATIALIZATION_BEHAVIOR = Util.intToStringMaxRadix(4);

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(FIELD_CONTENT_TYPE, contentType);
    bundle.putInt(FIELD_FLAGS, flags);
    bundle.putInt(FIELD_USAGE, usage);
    bundle.putInt(FIELD_ALLOWED_CAPTURE_POLICY, allowedCapturePolicy);
    bundle.putInt(FIELD_SPATIALIZATION_BEHAVIOR, spatializationBehavior);
    return bundle;
  }

  /** Object that can restore {@link AudioAttributes} from a {@link Bundle}. */
  public static final Creator<AudioAttributes> CREATOR =
      bundle -> {
        Builder builder = new Builder();
        if (bundle.containsKey(FIELD_CONTENT_TYPE)) {
          builder.setContentType(bundle.getInt(FIELD_CONTENT_TYPE));
        }
        if (bundle.containsKey(FIELD_FLAGS)) {
          builder.setFlags(bundle.getInt(FIELD_FLAGS));
        }
        if (bundle.containsKey(FIELD_USAGE)) {
          builder.setUsage(bundle.getInt(FIELD_USAGE));
        }
        if (bundle.containsKey(FIELD_ALLOWED_CAPTURE_POLICY)) {
          builder.setAllowedCapturePolicy(bundle.getInt(FIELD_ALLOWED_CAPTURE_POLICY));
        }
        if (bundle.containsKey(FIELD_SPATIALIZATION_BEHAVIOR)) {
          builder.setSpatializationBehavior(bundle.getInt(FIELD_SPATIALIZATION_BEHAVIOR));
        }
        return builder.build();
      };

  @RequiresApi(29)
  private static final class Api29 {
    @DoNotInline
    public static void setAllowedCapturePolicy(
        android.media.AudioAttributes.Builder builder,
        @C.AudioAllowedCapturePolicy int allowedCapturePolicy) {
      builder.setAllowedCapturePolicy(allowedCapturePolicy);
    }
  }

  @RequiresApi(32)
  private static final class Api32 {
    @DoNotInline
    public static void setSpatializationBehavior(
        android.media.AudioAttributes.Builder builder,
        @C.SpatializationBehavior int spatializationBehavior) {
      builder.setSpatializationBehavior(spatializationBehavior);
    }
  }
}

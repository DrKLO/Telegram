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

package com.google.android.exoplayer2.source.rtsp;

import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.HashMap;

/**
 * Records all the information in a SDP message.
 *
 * <p>SDP messages encapsulate information on the media play back session, including session
 * configuration information, formats of each playable track, etc. SDP is defined in RFC4566.
 */
/* package */ final class SessionDescription {

  /** Builder class for {@link SessionDescription}. */
  public static final class Builder {
    private final HashMap<String, String> attributes;
    private final ImmutableList.Builder<MediaDescription> mediaDescriptionListBuilder;
    private int bitrate;
    @Nullable private String sessionName;
    @Nullable private String origin;
    @Nullable private String timing;
    @Nullable private Uri uri;
    @Nullable private String connection;
    @Nullable private String key;
    @Nullable private String sessionInfo;
    @Nullable private String emailAddress;
    @Nullable private String phoneNumber;

    /** Creates a new instance. */
    public Builder() {
      attributes = new HashMap<>();
      mediaDescriptionListBuilder = new ImmutableList.Builder<>();
      bitrate = Format.NO_VALUE;
    }

    /**
     * Sets {@link SessionDescription#sessionName}.
     *
     * <p>This property must be set before calling {@link #build()}.
     *
     * @param sessionName The {@link SessionDescription#sessionName}.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setSessionName(String sessionName) {
      this.sessionName = sessionName;
      return this;
    }

    /**
     * Sets {@link SessionDescription#sessionInfo}. The default is {@code null}.
     *
     * @param sessionInfo The {@link SessionDescription#sessionInfo}.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setSessionInfo(String sessionInfo) {
      this.sessionInfo = sessionInfo;
      return this;
    }

    /**
     * Sets {@link SessionDescription#uri}. The default is {@code null}.
     *
     * @param uri The {@link SessionDescription#uri}.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setUri(Uri uri) {
      this.uri = uri;
      return this;
    }

    /**
     * Sets {@link SessionDescription#origin}.
     *
     * <p>This property must be set before calling {@link #build()}.
     *
     * @param origin The {@link SessionDescription#origin}.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setOrigin(String origin) {
      this.origin = origin;
      return this;
    }

    /**
     * Sets {@link SessionDescription#connection}. The default is {@code null}.
     *
     * @param connection The {@link SessionDescription#connection}.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setConnection(String connection) {
      this.connection = connection;
      return this;
    }

    /**
     * Sets {@link SessionDescription#bitrate}. The default is {@link Format#NO_VALUE}.
     *
     * @param bitrate The {@link SessionDescription#bitrate} in bits per second.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setBitrate(int bitrate) {
      this.bitrate = bitrate;
      return this;
    }

    /**
     * Sets {@link SessionDescription#timing}.
     *
     * <p>This property must be set before calling {@link #build()}.
     *
     * @param timing The {@link SessionDescription#timing}.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setTiming(String timing) {
      this.timing = timing;
      return this;
    }

    /**
     * Sets {@link SessionDescription#key}. The default is {@code null}.
     *
     * @param key The {@link SessionDescription#key}.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setKey(String key) {
      this.key = key;
      return this;
    }

    /**
     * Sets {@link SessionDescription#emailAddress}. The default is {@code null}.
     *
     * @param emailAddress The {@link SessionDescription#emailAddress}.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setEmailAddress(String emailAddress) {
      this.emailAddress = emailAddress;
      return this;
    }

    /**
     * Sets {@link SessionDescription#phoneNumber}. The default is {@code null}.
     *
     * @param phoneNumber The {@link SessionDescription#phoneNumber}.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setPhoneNumber(String phoneNumber) {
      this.phoneNumber = phoneNumber;
      return this;
    }

    /**
     * Adds one attribute to {@link SessionDescription#attributes}.
     *
     * @param attributeName The name of the attribute.
     * @param attributeValue The value of the attribute.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder addAttribute(String attributeName, String attributeValue) {
      attributes.put(attributeName, attributeValue);
      return this;
    }

    /**
     * Adds one {@link MediaDescription} to the {@link SessionDescription#mediaDescriptionList}.
     *
     * @param mediaDescription The {@link MediaDescription}.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder addMediaDescription(MediaDescription mediaDescription) {
      mediaDescriptionListBuilder.add(mediaDescription);
      return this;
    }

    /**
     * Builds a new {@link SessionDescription} instance.
     *
     * @return The newly built {@link SessionDescription} instance.
     */
    public SessionDescription build() {
      return new SessionDescription(this);
    }
  }

  /** The only supported SDP version, will be checked against every SDP message received. */
  public static final String SUPPORTED_SDP_VERSION = "0";
  /** The control attribute name. */
  public static final String ATTR_CONTROL = "control";
  /** The format property attribute name. */
  public static final String ATTR_FMTP = "fmtp";
  /** The length property attribute name. */
  public static final String ATTR_LENGTH = "length";
  /** The range property attribute name. */
  public static final String ATTR_RANGE = "range";
  /** The RTP format mapping property attribute name. */
  public static final String ATTR_RTPMAP = "rtpmap";
  /** The tool property attribute name. */
  public static final String ATTR_TOOL = "tool";
  /** The type property attribute name. */
  public static final String ATTR_TYPE = "type";

  /**
   * All the session attributes, mapped from attribute name to value. The value is {@code ""} if not
   * present.
   */
  public final ImmutableMap<String, String> attributes;
  /**
   * The {@link MediaDescription MediaDescriptions} for each media track included in the session.
   */
  public final ImmutableList<MediaDescription> mediaDescriptionList;
  /** The name of a session. */
  @Nullable public final String sessionName;
  /** The origin sender info. */
  @Nullable public final String origin;
  /** The timing info. */
  @Nullable public final String timing;
  /** The estimated bitrate in bits per seconds. */
  public final int bitrate;
  /** The uri of a linked content. */
  @Nullable public final Uri uri;
  /** The connection info. */
  @Nullable public final String connection;
  /** The encryption method and key info. */
  @Nullable public final String key;
  /** The email info. */
  @Nullable public final String emailAddress;
  /** The phone number info. */
  @Nullable public final String phoneNumber;
  /** The session info, a detailed description of the session. */
  @Nullable public final String sessionInfo;

  /** Creates a new instance. */
  private SessionDescription(Builder builder) {
    this.attributes = ImmutableMap.copyOf(builder.attributes);
    this.mediaDescriptionList = builder.mediaDescriptionListBuilder.build();
    this.sessionName = castNonNull(builder.sessionName);
    this.origin = castNonNull(builder.origin);
    this.timing = castNonNull(builder.timing);
    this.uri = builder.uri;
    this.connection = builder.connection;
    this.bitrate = builder.bitrate;
    this.key = builder.key;
    this.emailAddress = builder.emailAddress;
    this.phoneNumber = builder.phoneNumber;
    this.sessionInfo = builder.sessionInfo;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SessionDescription that = (SessionDescription) o;
    return bitrate == that.bitrate
        && attributes.equals(that.attributes)
        && mediaDescriptionList.equals(that.mediaDescriptionList)
        && Util.areEqual(origin, that.origin)
        && Util.areEqual(sessionName, that.sessionName)
        && Util.areEqual(timing, that.timing)
        && Util.areEqual(sessionInfo, that.sessionInfo)
        && Util.areEqual(uri, that.uri)
        && Util.areEqual(emailAddress, that.emailAddress)
        && Util.areEqual(phoneNumber, that.phoneNumber)
        && Util.areEqual(connection, that.connection)
        && Util.areEqual(key, that.key);
  }

  @Override
  public int hashCode() {
    int result = 7;
    result = 31 * result + attributes.hashCode();
    result = 31 * result + mediaDescriptionList.hashCode();
    result = 31 * result + (origin == null ? 0 : origin.hashCode());
    result = 31 * result + (sessionName == null ? 0 : sessionName.hashCode());
    result = 31 * result + (timing == null ? 0 : timing.hashCode());
    result = 31 * result + bitrate;
    result = 31 * result + (sessionInfo == null ? 0 : sessionInfo.hashCode());
    result = 31 * result + (uri == null ? 0 : uri.hashCode());
    result = 31 * result + (emailAddress == null ? 0 : emailAddress.hashCode());
    result = 31 * result + (phoneNumber == null ? 0 : phoneNumber.hashCode());
    result = 31 * result + (connection == null ? 0 : connection.hashCode());
    result = 31 * result + (key == null ? 0 : key.hashCode());
    return result;
  }
}

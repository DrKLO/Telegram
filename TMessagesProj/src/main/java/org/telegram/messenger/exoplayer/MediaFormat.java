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
package org.telegram.messenger.exoplayer;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.os.Parcel;
import android.os.Parcelable;
import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.MimeTypes;
import org.telegram.messenger.exoplayer.util.Util;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Defines the format of an elementary media stream.
 */
public final class MediaFormat implements Parcelable {

  public static final int NO_VALUE = -1;

  /**
   * A value for {@link #subsampleOffsetUs} to indicate that subsample timestamps are relative to
   * the timestamps of their parent samples.
   */
  public static final long OFFSET_SAMPLE_RELATIVE = Long.MAX_VALUE;

  /**
   * The identifier for the track represented by the format, or null if unknown or not applicable.
   */
  public final String trackId;
  /**
   * The mime type of the format.
   */
  public final String mimeType;
  /**
   * The average bandwidth in bits per second, or {@link #NO_VALUE} if unknown or not applicable.
   */
  public final int bitrate;
  /**
   * The maximum size of a buffer of data (typically one sample) in the format, or {@link #NO_VALUE}
   * if unknown or not applicable.
   */
  public final int maxInputSize;
  /**
   * The duration in microseconds, or {@link C#UNKNOWN_TIME_US} if the duration is unknown, or
   * {@link C#MATCH_LONGEST_US} if the duration should match the duration of the longest track whose
   * duration is known.
   */
  public final long durationUs;
  /**
   * Initialization data that must be provided to the decoder. Will not be null, but may be empty
   * if initialization data is not required.
   */
  public final List<byte[]> initializationData;
  /**
   * Whether the format represents an adaptive track, meaning that the format of the actual media
   * data may change (e.g. to adapt to network conditions).
   */
  public final boolean adaptive;

  // Video specific.

  /**
   * The width of the video in pixels, or {@link #NO_VALUE} if unknown or not applicable.
   */
  public final int width;

  /**
   * The height of the video in pixels, or {@link #NO_VALUE} if unknown or not applicable.
   */
  public final int height;
  /**
   * For formats that belong to an adaptive video track (either describing the track, or describing
   * a specific format within it), this is the maximum width of the video in pixels that will be
   * encountered in the stream. Set to {@link #NO_VALUE} if unknown or not applicable.
   */
  public final int maxWidth;
  /**
   * For formats that belong to an adaptive video track (either describing the track, or describing
   * a specific format within it), this is the maximum height of the video in pixels that will be
   * encountered in the stream. Set to {@link #NO_VALUE} if unknown or not applicable.
   */
  public final int maxHeight;
  /**
   * The clockwise rotation that should be applied to the video for it to be rendered in the correct
   * orientation, or {@link #NO_VALUE} if unknown or not applicable. Only 0, 90, 180 and 270 are
   * supported.
   */
  public final int rotationDegrees;
  /**
   * The width to height ratio of pixels in the video, or {@link #NO_VALUE} if unknown or not
   * applicable.
   */
  public final float pixelWidthHeightRatio;

  // Audio specific.

  /**
   * The number of audio channels, or {@link #NO_VALUE} if unknown or not applicable.
   */
  public final int channelCount;
  /**
   * The audio sampling rate in Hz, or {@link #NO_VALUE} if unknown or not applicable.
   */
  public final int sampleRate;
  /**
   * The encoding for PCM audio streams. If {@link #mimeType} is {@link MimeTypes#AUDIO_RAW} then
   * one of {@link C#ENCODING_PCM_8BIT}, {@link C#ENCODING_PCM_16BIT}, {@link C#ENCODING_PCM_24BIT}
   * and {@link C#ENCODING_PCM_32BIT}. Set to {@link #NO_VALUE} for other media types.
   */
  public final int pcmEncoding;
  /**
   * The number of samples to trim from the start of the decoded audio stream.
   */
  public final int encoderDelay;
  /**
   * The number of samples to trim from the end of the decoded audio stream.
   */
  public final int encoderPadding;

  // Text specific.

  /**
   * The language of the track, or null if unknown or not applicable.
   */
  public final String language;

  /**
   * For samples that contain subsamples, this is an offset that should be added to subsample
   * timestamps. A value of {@link #OFFSET_SAMPLE_RELATIVE} indicates that subsample timestamps are
   * relative to the timestamps of their parent samples.
   */
  public final long subsampleOffsetUs;

  // Lazy-initialized hashcode and framework media format.

  private int hashCode;
  private android.media.MediaFormat frameworkMediaFormat;

  public static MediaFormat createVideoFormat(String trackId, String mimeType, int bitrate,
      int maxInputSize, long durationUs, int width, int height, List<byte[]> initializationData) {
    return createVideoFormat(trackId, mimeType, bitrate, maxInputSize, durationUs, width, height,
        initializationData, NO_VALUE, NO_VALUE);
  }

  public static MediaFormat createVideoFormat(String trackId, String mimeType, int bitrate,
      int maxInputSize, long durationUs, int width, int height, List<byte[]> initializationData,
      int rotationDegrees, float pixelWidthHeightRatio) {
    return new MediaFormat(trackId, mimeType, bitrate, maxInputSize, durationUs, width, height,
        rotationDegrees, pixelWidthHeightRatio, NO_VALUE, NO_VALUE, null, OFFSET_SAMPLE_RELATIVE,
        initializationData, false, NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE);
  }

  public static MediaFormat createAudioFormat(String trackId, String mimeType, int bitrate,
      int maxInputSize, long durationUs, int channelCount, int sampleRate,
      List<byte[]> initializationData, String language) {
    return createAudioFormat(trackId, mimeType, bitrate, maxInputSize, durationUs, channelCount,
        sampleRate, initializationData, language, NO_VALUE);
  }

  public static MediaFormat createAudioFormat(String trackId, String mimeType, int bitrate,
      int maxInputSize, long durationUs, int channelCount, int sampleRate,
      List<byte[]> initializationData, String language, int pcmEncoding) {
    return new MediaFormat(trackId, mimeType, bitrate, maxInputSize, durationUs, NO_VALUE, NO_VALUE,
        NO_VALUE, NO_VALUE, channelCount, sampleRate, language, OFFSET_SAMPLE_RELATIVE,
        initializationData, false, NO_VALUE, NO_VALUE, pcmEncoding, NO_VALUE, NO_VALUE);
  }

  public static MediaFormat createTextFormat(String trackId, String mimeType, int bitrate,
      long durationUs, String language) {
    return createTextFormat(trackId, mimeType, bitrate, durationUs, language,
        OFFSET_SAMPLE_RELATIVE);
  }

  public static MediaFormat createTextFormat(String trackId, String mimeType, int bitrate,
      long durationUs, String language, long subsampleOffsetUs) {
    return new MediaFormat(trackId, mimeType, bitrate, NO_VALUE, durationUs, NO_VALUE, NO_VALUE,
        NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE, language, subsampleOffsetUs, null, false, NO_VALUE,
        NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE);
  }

  public static MediaFormat createImageFormat(String trackId, String mimeType, int bitrate,
      long durationUs, List<byte[]> initializationData, String language) {
    return new MediaFormat(trackId, mimeType, bitrate, NO_VALUE, durationUs, NO_VALUE, NO_VALUE,
        NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE, language, OFFSET_SAMPLE_RELATIVE,
        initializationData, false, NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE);
  }

  public static MediaFormat createFormatForMimeType(String trackId, String mimeType, int bitrate,
      long durationUs) {
    return new MediaFormat(trackId, mimeType, bitrate, NO_VALUE, durationUs, NO_VALUE, NO_VALUE,
        NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE, null, OFFSET_SAMPLE_RELATIVE, null, false, NO_VALUE,
        NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE);
  }

  public static MediaFormat createId3Format() {
    return createFormatForMimeType(null, MimeTypes.APPLICATION_ID3, MediaFormat.NO_VALUE,
        C.UNKNOWN_TIME_US);
  }

  /* package */ MediaFormat(Parcel in) {
    trackId = in.readString();
    mimeType = in.readString();
    bitrate = in.readInt();
    maxInputSize = in.readInt();
    durationUs = in.readLong();
    width = in.readInt();
    height = in.readInt();
    rotationDegrees = in.readInt();
    pixelWidthHeightRatio = in.readFloat();
    channelCount = in.readInt();
    sampleRate = in.readInt();
    language = in.readString();
    subsampleOffsetUs = in.readLong();
    initializationData = new ArrayList<>();
    in.readList(initializationData, null);
    adaptive = in.readInt() == 1;
    maxWidth = in.readInt();
    maxHeight = in.readInt();
    pcmEncoding = in.readInt();
    encoderDelay = in.readInt();
    encoderPadding = in.readInt();
  }

  /* package */ MediaFormat(String trackId, String mimeType, int bitrate, int maxInputSize,
      long durationUs, int width, int height, int rotationDegrees, float pixelWidthHeightRatio,
      int channelCount, int sampleRate, String language, long subsampleOffsetUs,
      List<byte[]> initializationData, boolean adaptive, int maxWidth, int maxHeight,
      int pcmEncoding, int encoderDelay, int encoderPadding) {
    this.trackId = trackId;
    this.mimeType = Assertions.checkNotEmpty(mimeType);
    this.bitrate = bitrate;
    this.maxInputSize = maxInputSize;
    this.durationUs = durationUs;
    this.width = width;
    this.height = height;
    this.rotationDegrees = rotationDegrees;
    this.pixelWidthHeightRatio = pixelWidthHeightRatio;
    this.channelCount = channelCount;
    this.sampleRate = sampleRate;
    this.language = language;
    this.subsampleOffsetUs = subsampleOffsetUs;
    this.initializationData = initializationData == null ? Collections.<byte[]>emptyList()
        : initializationData;
    this.adaptive = adaptive;
    this.maxWidth = maxWidth;
    this.maxHeight = maxHeight;
    this.pcmEncoding = pcmEncoding;
    this.encoderDelay = encoderDelay;
    this.encoderPadding = encoderPadding;
  }

  public MediaFormat copyWithMaxInputSize(int maxInputSize) {
    return new MediaFormat(trackId, mimeType, bitrate, maxInputSize, durationUs, width, height,
        rotationDegrees, pixelWidthHeightRatio, channelCount, sampleRate, language,
        subsampleOffsetUs, initializationData, adaptive, maxWidth, maxHeight, pcmEncoding,
        encoderDelay, encoderPadding);
  }

  public MediaFormat copyWithMaxVideoDimensions(int maxWidth, int maxHeight) {
    return new MediaFormat(trackId, mimeType, bitrate, maxInputSize, durationUs, width, height,
        rotationDegrees, pixelWidthHeightRatio, channelCount, sampleRate, language,
        subsampleOffsetUs, initializationData, adaptive, maxWidth, maxHeight, pcmEncoding,
        encoderDelay, encoderPadding);
  }

  public MediaFormat copyWithSubsampleOffsetUs(long subsampleOffsetUs) {
    return new MediaFormat(trackId, mimeType, bitrate, maxInputSize, durationUs, width, height,
        rotationDegrees, pixelWidthHeightRatio, channelCount, sampleRate, language,
        subsampleOffsetUs, initializationData, adaptive, maxWidth, maxHeight, pcmEncoding,
        encoderDelay, encoderPadding);
  }

  public MediaFormat copyWithDurationUs(long durationUs) {
    return new MediaFormat(trackId, mimeType, bitrate, maxInputSize, durationUs, width, height,
        rotationDegrees, pixelWidthHeightRatio, channelCount, sampleRate, language,
        subsampleOffsetUs, initializationData, adaptive, maxWidth, maxHeight, pcmEncoding,
        encoderDelay, encoderPadding);
  }

  public MediaFormat copyWithLanguage(String language) {
    return new MediaFormat(trackId, mimeType, bitrate, maxInputSize, durationUs, width, height,
        rotationDegrees, pixelWidthHeightRatio, channelCount, sampleRate, language,
        subsampleOffsetUs, initializationData, adaptive, maxWidth, maxHeight, pcmEncoding,
        encoderDelay, encoderPadding);
  }

  public MediaFormat copyWithFixedTrackInfo(String trackId, int bitrate, int width, int height,
      String language) {
    return new MediaFormat(trackId, mimeType, bitrate, maxInputSize, durationUs, width, height,
        rotationDegrees, pixelWidthHeightRatio, channelCount, sampleRate, language,
        subsampleOffsetUs, initializationData, adaptive, NO_VALUE, NO_VALUE, pcmEncoding,
        encoderDelay, encoderPadding);
  }

  public MediaFormat copyAsAdaptive(String trackId) {
    return new MediaFormat(trackId, mimeType, NO_VALUE, NO_VALUE, durationUs, NO_VALUE, NO_VALUE,
        NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE, null, OFFSET_SAMPLE_RELATIVE, null, true, maxWidth,
        maxHeight, NO_VALUE, NO_VALUE, NO_VALUE);
  }

  public MediaFormat copyWithGaplessInfo(int encoderDelay, int encoderPadding) {
    return new MediaFormat(trackId, mimeType, bitrate, maxInputSize, durationUs, width, height,
        rotationDegrees, pixelWidthHeightRatio, channelCount, sampleRate, language,
        subsampleOffsetUs, initializationData, adaptive, maxWidth, maxHeight, pcmEncoding,
        encoderDelay, encoderPadding);
  }

  /**
   * @return A {@link MediaFormat} representation of this format.
   */
  @SuppressLint("InlinedApi")
  @TargetApi(16)
  public final android.media.MediaFormat getFrameworkMediaFormatV16() {
    if (frameworkMediaFormat == null) {
      android.media.MediaFormat format = new android.media.MediaFormat();
      format.setString(android.media.MediaFormat.KEY_MIME, mimeType);
      maybeSetStringV16(format, android.media.MediaFormat.KEY_LANGUAGE, language);
      maybeSetIntegerV16(format, android.media.MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize);
      maybeSetIntegerV16(format, android.media.MediaFormat.KEY_WIDTH, width);
      maybeSetIntegerV16(format, android.media.MediaFormat.KEY_HEIGHT, height);
      maybeSetIntegerV16(format, "rotation-degrees", rotationDegrees);
      maybeSetIntegerV16(format, android.media.MediaFormat.KEY_MAX_WIDTH, maxWidth);
      maybeSetIntegerV16(format, android.media.MediaFormat.KEY_MAX_HEIGHT, maxHeight);
      maybeSetIntegerV16(format, android.media.MediaFormat.KEY_CHANNEL_COUNT, channelCount);
      maybeSetIntegerV16(format, android.media.MediaFormat.KEY_SAMPLE_RATE, sampleRate);
      maybeSetIntegerV16(format, "encoder-delay", encoderDelay);
      maybeSetIntegerV16(format, "encoder-padding", encoderPadding);
      for (int i = 0; i < initializationData.size(); i++) {
        format.setByteBuffer("csd-" + i, ByteBuffer.wrap(initializationData.get(i)));
      }
      if (durationUs != C.UNKNOWN_TIME_US) {
        format.setLong(android.media.MediaFormat.KEY_DURATION, durationUs);
      }
      frameworkMediaFormat = format;
    }
    return frameworkMediaFormat;
  }

  /**
   * Sets the framework format returned by {@link #getFrameworkMediaFormatV16()}.
   *
   * @deprecated This method only exists for FrameworkSampleSource, which is itself deprecated.
   * @param format The framework format.
   */
  @Deprecated
  @TargetApi(16)
  /* package */ final void setFrameworkFormatV16(android.media.MediaFormat format) {
    frameworkMediaFormat = format;
  }

  @Override
  public String toString() {
    return "MediaFormat(" + trackId + ", " + mimeType + ", " + bitrate + ", " + maxInputSize
        + ", " + width + ", " + height + ", " + rotationDegrees + ", " + pixelWidthHeightRatio
        + ", " + channelCount + ", " + sampleRate + ", " + language + ", " + durationUs + ", "
        + adaptive + ", " + maxWidth + ", " + maxHeight + ", " + pcmEncoding + ", " + encoderDelay
        + ", " + encoderPadding + ")";
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      int result = 17;
      result = 31 * result + (trackId == null ? 0 : trackId.hashCode());
      result = 31 * result + (mimeType == null ? 0 : mimeType.hashCode());
      result = 31 * result + bitrate;
      result = 31 * result + maxInputSize;
      result = 31 * result + width;
      result = 31 * result + height;
      result = 31 * result + rotationDegrees;
      result = 31 * result + Float.floatToRawIntBits(pixelWidthHeightRatio);
      result = 31 * result + (int) durationUs;
      result = 31 * result + (adaptive ? 1231 : 1237);
      result = 31 * result + maxWidth;
      result = 31 * result + maxHeight;
      result = 31 * result + channelCount;
      result = 31 * result + sampleRate;
      result = 31 * result + pcmEncoding;
      result = 31 * result + encoderDelay;
      result = 31 * result + encoderPadding;
      result = 31 * result + (language == null ? 0 : language.hashCode());
      result = 31 * result + (int) subsampleOffsetUs;
      for (int i = 0; i < initializationData.size(); i++) {
        result = 31 * result + Arrays.hashCode(initializationData.get(i));
      }
      hashCode = result;
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
    MediaFormat other = (MediaFormat) obj;
    if (adaptive != other.adaptive || bitrate != other.bitrate || maxInputSize != other.maxInputSize
        || durationUs != other.durationUs || width != other.width || height != other.height
        || rotationDegrees != other.rotationDegrees
        || pixelWidthHeightRatio != other.pixelWidthHeightRatio
        || maxWidth != other.maxWidth || maxHeight != other.maxHeight
        || channelCount != other.channelCount || sampleRate != other.sampleRate
        || pcmEncoding != other.pcmEncoding || encoderDelay != other.encoderDelay
        || encoderPadding != other.encoderPadding || subsampleOffsetUs != other.subsampleOffsetUs
        || !Util.areEqual(trackId, other.trackId) || !Util.areEqual(language, other.language)
        || !Util.areEqual(mimeType, other.mimeType)
        || initializationData.size() != other.initializationData.size()) {
      return false;
    }
    for (int i = 0; i < initializationData.size(); i++) {
      if (!Arrays.equals(initializationData.get(i), other.initializationData.get(i))) {
        return false;
      }
    }
    return true;
  }

  @TargetApi(16)
  private static final void maybeSetStringV16(android.media.MediaFormat format, String key,
      String value) {
    if (value != null) {
      format.setString(key, value);
    }
  }

  @TargetApi(16)
  private static final void maybeSetIntegerV16(android.media.MediaFormat format, String key,
      int value) {
    if (value != NO_VALUE) {
      format.setInteger(key, value);
    }
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(trackId);
    dest.writeString(mimeType);
    dest.writeInt(bitrate);
    dest.writeInt(maxInputSize);
    dest.writeLong(durationUs);
    dest.writeInt(width);
    dest.writeInt(height);
    dest.writeInt(rotationDegrees);
    dest.writeFloat(pixelWidthHeightRatio);
    dest.writeInt(channelCount);
    dest.writeInt(sampleRate);
    dest.writeString(language);
    dest.writeLong(subsampleOffsetUs);
    dest.writeList(initializationData);
    dest.writeInt(adaptive ? 1 : 0);
    dest.writeInt(maxWidth);
    dest.writeInt(maxHeight);
    dest.writeInt(pcmEncoding);
    dest.writeInt(encoderDelay);
    dest.writeInt(encoderPadding);
  }

  public static final Creator<MediaFormat> CREATOR = new Creator<MediaFormat>() {

    @Override
    public MediaFormat createFromParcel(Parcel in) {
      return new MediaFormat(in);
    }

    @Override
    public MediaFormat[] newArray(int size) {
      return new MediaFormat[size];
    }

  };

}

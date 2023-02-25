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
package com.google.android.exoplayer2.source.dash.manifest;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.dash.DashSegmentIndex;
import com.google.android.exoplayer2.source.dash.manifest.SegmentBase.MultiSegmentBase;
import com.google.android.exoplayer2.source.dash.manifest.SegmentBase.SingleSegmentBase;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;

/** A DASH representation. */
public abstract class Representation {

  /** A default value for {@link #revisionId}. */
  public static final long REVISION_ID_DEFAULT = -1;

  /**
   * Identifies the revision of the media contained within the representation. If the media can
   * change over time (e.g. as a result of it being re-encoded), then this identifier can be set to
   * uniquely identify the revision of the media. The timestamp at which the media was encoded is
   * often a suitable.
   */
  public final long revisionId;
  /** The format of the representation. */
  public final Format format;
  /** The base URLs of the representation. */
  public final ImmutableList<BaseUrl> baseUrls;
  /** The offset of the presentation timestamps in the media stream relative to media time. */
  public final long presentationTimeOffsetUs;
  /** The in-band event streams in the representation. May be empty. */
  public final List<Descriptor> inbandEventStreams;
  /** Essential properties in the representation. May be empty. */
  public final List<Descriptor> essentialProperties;
  /** Supplemental properties in the adaptation set. May be empty. */
  public final List<Descriptor> supplementalProperties;

  private final RangedUri initializationUri;

  /**
   * Constructs a new instance.
   *
   * @param revisionId Identifies the revision of the content.
   * @param format The format of the representation.
   * @param baseUrls The list of base URLs of the representation.
   * @param segmentBase A segment base element for the representation.
   * @return The constructed instance.
   */
  public static Representation newInstance(
      long revisionId, Format format, List<BaseUrl> baseUrls, SegmentBase segmentBase) {
    return newInstance(
        revisionId,
        format,
        baseUrls,
        segmentBase,
        /* inbandEventStreams= */ null,
        /* essentialProperties= */ ImmutableList.of(),
        /* supplementalProperties= */ ImmutableList.of(),
        /* cacheKey= */ null);
  }

  /**
   * Constructs a new instance.
   *
   * @param revisionId Identifies the revision of the content.
   * @param format The format of the representation.
   * @param baseUrls The list of base URLs of the representation.
   * @param segmentBase A segment base element for the representation.
   * @param inbandEventStreams The in-band event streams in the representation. May be null.
   * @param essentialProperties Essential properties in the representation. May be empty.
   * @param supplementalProperties Supplemental properties in the representation. May be empty.
   * @param cacheKey An optional key to be returned from {@link #getCacheKey()}, or null. This
   *     parameter is ignored if {@code segmentBase} consists of multiple segments.
   * @return The constructed instance.
   */
  public static Representation newInstance(
      long revisionId,
      Format format,
      List<BaseUrl> baseUrls,
      SegmentBase segmentBase,
      @Nullable List<Descriptor> inbandEventStreams,
      List<Descriptor> essentialProperties,
      List<Descriptor> supplementalProperties,
      @Nullable String cacheKey) {
    if (segmentBase instanceof SingleSegmentBase) {
      return new SingleSegmentRepresentation(
          revisionId,
          format,
          baseUrls,
          (SingleSegmentBase) segmentBase,
          inbandEventStreams,
          essentialProperties,
          supplementalProperties,
          cacheKey,
          /* contentLength= */ C.LENGTH_UNSET);
    } else if (segmentBase instanceof MultiSegmentBase) {
      return new MultiSegmentRepresentation(
          revisionId,
          format,
          baseUrls,
          (MultiSegmentBase) segmentBase,
          inbandEventStreams,
          essentialProperties,
          supplementalProperties);
    } else {
      throw new IllegalArgumentException(
          "segmentBase must be of type SingleSegmentBase or " + "MultiSegmentBase");
    }
  }

  private Representation(
      long revisionId,
      Format format,
      List<BaseUrl> baseUrls,
      SegmentBase segmentBase,
      @Nullable List<Descriptor> inbandEventStreams,
      List<Descriptor> essentialProperties,
      List<Descriptor> supplementalProperties) {
    checkArgument(!baseUrls.isEmpty());
    this.revisionId = revisionId;
    this.format = format;
    this.baseUrls = ImmutableList.copyOf(baseUrls);
    this.inbandEventStreams =
        inbandEventStreams == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(inbandEventStreams);
    this.essentialProperties = essentialProperties;
    this.supplementalProperties = supplementalProperties;
    initializationUri = segmentBase.getInitialization(this);
    presentationTimeOffsetUs = segmentBase.getPresentationTimeOffsetUs();
  }

  /**
   * Returns a {@link RangedUri} defining the location of the representation's initialization data,
   * or null if no initialization data exists.
   */
  @Nullable
  public RangedUri getInitializationUri() {
    return initializationUri;
  }

  /**
   * Returns a {@link RangedUri} defining the location of the representation's segment index, or
   * null if the representation provides an index directly.
   */
  @Nullable
  public abstract RangedUri getIndexUri();

  /** Returns an index if the representation provides one directly, or null otherwise. */
  @Nullable
  public abstract DashSegmentIndex getIndex();

  /** Returns a cache key for the representation if set, or null. */
  @Nullable
  public abstract String getCacheKey();

  /** A DASH representation consisting of a single segment. */
  public static class SingleSegmentRepresentation extends Representation {

    /** The uri of the single segment. */
    public final Uri uri;
    /** The content length, or {@link C#LENGTH_UNSET} if unknown. */
    public final long contentLength;

    @Nullable private final String cacheKey;
    @Nullable private final RangedUri indexUri;
    @Nullable private final SingleSegmentIndex segmentIndex;

    /**
     * @param revisionId Identifies the revision of the content.
     * @param format The format of the representation.
     * @param uri The uri of the media.
     * @param initializationStart The offset of the first byte of initialization data.
     * @param initializationEnd The offset of the last byte of initialization data.
     * @param indexStart The offset of the first byte of index data.
     * @param indexEnd The offset of the last byte of index data.
     * @param inbandEventStreams The in-band event streams in the representation. May be null.
     * @param cacheKey An optional key to be returned from {@link #getCacheKey()}, or null.
     * @param contentLength The content length, or {@link C#LENGTH_UNSET} if unknown.
     */
    public static SingleSegmentRepresentation newInstance(
        long revisionId,
        Format format,
        String uri,
        long initializationStart,
        long initializationEnd,
        long indexStart,
        long indexEnd,
        List<Descriptor> inbandEventStreams,
        @Nullable String cacheKey,
        long contentLength) {
      RangedUri rangedUri =
          new RangedUri(null, initializationStart, initializationEnd - initializationStart + 1);
      SingleSegmentBase segmentBase =
          new SingleSegmentBase(rangedUri, 1, 0, indexStart, indexEnd - indexStart + 1);
      ImmutableList<BaseUrl> baseUrls = ImmutableList.of(new BaseUrl(uri));
      return new SingleSegmentRepresentation(
          revisionId,
          format,
          baseUrls,
          segmentBase,
          inbandEventStreams,
          /* essentialProperties= */ ImmutableList.of(),
          /* supplementalProperties= */ ImmutableList.of(),
          cacheKey,
          contentLength);
    }

    /**
     * @param revisionId Identifies the revision of the content.
     * @param format The format of the representation.
     * @param baseUrls The base urls of the representation.
     * @param segmentBase The segment base underlying the representation.
     * @param inbandEventStreams The in-band event streams in the representation. May be null.
     * @param essentialProperties Essential properties in the representation. May be empty.
     * @param supplementalProperties Supplemental properties in the representation. May be empty.
     * @param cacheKey An optional key to be returned from {@link #getCacheKey()}, or null.
     * @param contentLength The content length, or {@link C#LENGTH_UNSET} if unknown.
     */
    public SingleSegmentRepresentation(
        long revisionId,
        Format format,
        List<BaseUrl> baseUrls,
        SingleSegmentBase segmentBase,
        @Nullable List<Descriptor> inbandEventStreams,
        List<Descriptor> essentialProperties,
        List<Descriptor> supplementalProperties,
        @Nullable String cacheKey,
        long contentLength) {
      super(
          revisionId,
          format,
          baseUrls,
          segmentBase,
          inbandEventStreams,
          essentialProperties,
          supplementalProperties);
      this.uri = Uri.parse(baseUrls.get(0).url);
      this.indexUri = segmentBase.getIndex();
      this.cacheKey = cacheKey;
      this.contentLength = contentLength;
      // If we have an index uri then the index is defined externally, and we shouldn't return one
      // directly. If we don't, then we can't do better than an index defining a single segment.
      segmentIndex =
          indexUri != null ? null : new SingleSegmentIndex(new RangedUri(null, 0, contentLength));
    }

    @Override
    @Nullable
    public RangedUri getIndexUri() {
      return indexUri;
    }

    @Override
    @Nullable
    public DashSegmentIndex getIndex() {
      return segmentIndex;
    }

    @Override
    @Nullable
    public String getCacheKey() {
      return cacheKey;
    }
  }

  /** A DASH representation consisting of multiple segments. */
  public static class MultiSegmentRepresentation extends Representation
      implements DashSegmentIndex {

    @VisibleForTesting /* package */ final MultiSegmentBase segmentBase;

    /**
     * Creates the multi-segment Representation.
     *
     * @param revisionId Identifies the revision of the content.
     * @param format The format of the representation.
     * @param baseUrls The base URLs of the representation.
     * @param segmentBase The segment base underlying the representation.
     * @param inbandEventStreams The in-band event streams in the representation. May be null.
     * @param essentialProperties Essential properties in the representation. May be empty.
     * @param supplementalProperties Supplemental properties in the representation. May be empty.
     */
    public MultiSegmentRepresentation(
        long revisionId,
        Format format,
        List<BaseUrl> baseUrls,
        MultiSegmentBase segmentBase,
        @Nullable List<Descriptor> inbandEventStreams,
        List<Descriptor> essentialProperties,
        List<Descriptor> supplementalProperties) {
      super(
          revisionId,
          format,
          baseUrls,
          segmentBase,
          inbandEventStreams,
          essentialProperties,
          supplementalProperties);
      this.segmentBase = segmentBase;
    }

    @Override
    @Nullable
    public RangedUri getIndexUri() {
      return null;
    }

    @Override
    public DashSegmentIndex getIndex() {
      return this;
    }

    @Override
    @Nullable
    public String getCacheKey() {
      return null;
    }

    // DashSegmentIndex implementation.

    @Override
    public RangedUri getSegmentUrl(long segmentNum) {
      return segmentBase.getSegmentUrl(this, segmentNum);
    }

    @Override
    public long getSegmentNum(long timeUs, long periodDurationUs) {
      return segmentBase.getSegmentNum(timeUs, periodDurationUs);
    }

    @Override
    public long getTimeUs(long segmentNum) {
      return segmentBase.getSegmentTimeUs(segmentNum);
    }

    @Override
    public long getDurationUs(long segmentNum, long periodDurationUs) {
      return segmentBase.getSegmentDurationUs(segmentNum, periodDurationUs);
    }

    @Override
    public long getFirstSegmentNum() {
      return segmentBase.getFirstSegmentNum();
    }

    @Override
    public long getFirstAvailableSegmentNum(long periodDurationUs, long nowUnixTimeUs) {
      return segmentBase.getFirstAvailableSegmentNum(periodDurationUs, nowUnixTimeUs);
    }

    @Override
    public long getSegmentCount(long periodDurationUs) {
      return segmentBase.getSegmentCount(periodDurationUs);
    }

    @Override
    public long getAvailableSegmentCount(long periodDurationUs, long nowUnixTimeUs) {
      return segmentBase.getAvailableSegmentCount(periodDurationUs, nowUnixTimeUs);
    }

    @Override
    public long getNextSegmentAvailableTimeUs(long periodDurationUs, long nowUnixTimeUs) {
      return segmentBase.getNextSegmentAvailableTimeUs(periodDurationUs, nowUnixTimeUs);
    }

    @Override
    public boolean isExplicit() {
      return segmentBase.isExplicit();
    }
  }
}

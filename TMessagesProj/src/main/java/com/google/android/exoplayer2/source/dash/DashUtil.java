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
package com.google.android.exoplayer2.source.dash;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ChunkIndex;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.mkv.MatroskaExtractor;
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer2.source.chunk.BundledChunkExtractor;
import com.google.android.exoplayer2.source.chunk.ChunkExtractor;
import com.google.android.exoplayer2.source.chunk.InitializationChunk;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.dash.manifest.Period;
import com.google.android.exoplayer2.source.dash.manifest.RangedUri;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import java.io.IOException;
import java.util.List;

/** Utility methods for DASH streams. */
public final class DashUtil {

  /**
   * Builds a {@link DataSpec} for a given {@link RangedUri} belonging to {@link Representation}.
   *
   * @param representation The {@link Representation} to which the request belongs.
   * @param baseUrl The base url with which to resolve the request URI.
   * @param requestUri The {@link RangedUri} of the data to request.
   * @param flags Flags to be set on the returned {@link DataSpec}. See {@link
   *     DataSpec.Builder#setFlags(int)}.
   * @return The {@link DataSpec}.
   */
  public static DataSpec buildDataSpec(
      Representation representation, String baseUrl, RangedUri requestUri, int flags) {
    return new DataSpec.Builder()
        .setUri(requestUri.resolveUri(baseUrl))
        .setPosition(requestUri.start)
        .setLength(requestUri.length)
        .setKey(resolveCacheKey(representation, requestUri))
        .setFlags(flags)
        .build();
  }

  /**
   * Builds a {@link DataSpec} for a given {@link RangedUri} belonging to {@link Representation}.
   *
   * <p>Uses the first base URL of the representation to build the data spec.
   *
   * @param representation The {@link Representation} to which the request belongs.
   * @param requestUri The {@link RangedUri} of the data to request.
   * @param flags Flags to be set on the returned {@link DataSpec}. See {@link
   *     DataSpec.Builder#setFlags(int)}.
   * @return The {@link DataSpec}.
   */
  public static DataSpec buildDataSpec(
      Representation representation, RangedUri requestUri, int flags) {
    return buildDataSpec(representation, representation.baseUrls.get(0).url, requestUri, flags);
  }

  /**
   * Loads a DASH manifest.
   *
   * @param dataSource The {@link DataSource} from which the manifest should be read.
   * @param uri The {@link Uri} of the manifest to be read.
   * @return An instance of {@link DashManifest}.
   * @throws IOException Thrown when there is an error while loading.
   */
  public static DashManifest loadManifest(DataSource dataSource, Uri uri) throws IOException {
    return ParsingLoadable.load(dataSource, new DashManifestParser(), uri, C.DATA_TYPE_MANIFEST);
  }

  /**
   * Loads a {@link Format} for acquiring keys for a given period in a DASH manifest.
   *
   * @param dataSource The {@link DataSource} from which data should be loaded.
   * @param period The {@link Period}.
   * @return The loaded {@link Format}, or null if none is defined.
   * @throws IOException Thrown when there is an error while loading.
   */
  @Nullable
  public static Format loadFormatWithDrmInitData(DataSource dataSource, Period period)
      throws IOException {
    @C.TrackType int primaryTrackType = C.TRACK_TYPE_VIDEO;
    Representation representation = getFirstRepresentation(period, primaryTrackType);
    if (representation == null) {
      primaryTrackType = C.TRACK_TYPE_AUDIO;
      representation = getFirstRepresentation(period, primaryTrackType);
      if (representation == null) {
        return null;
      }
    }
    Format manifestFormat = representation.format;
    @Nullable
    Format sampleFormat = DashUtil.loadSampleFormat(dataSource, primaryTrackType, representation);
    return sampleFormat == null
        ? manifestFormat
        : sampleFormat.withManifestFormatInfo(manifestFormat);
  }

  /**
   * Loads initialization data for the {@code representation} and returns the sample {@link Format}.
   *
   * @param dataSource The source from which the data should be loaded.
   * @param trackType The type of the representation. Typically one of the {@link C
   *     com.google.android.exoplayer2.C} {@code TRACK_TYPE_*} constants.
   * @param representation The representation which initialization chunk belongs to.
   * @param baseUrlIndex The index of the base URL to be picked from the {@link
   *     Representation#baseUrls list of base URLs}.
   * @return the sample {@link Format} of the given representation.
   * @throws IOException Thrown when there is an error while loading.
   */
  @Nullable
  public static Format loadSampleFormat(
      DataSource dataSource, int trackType, Representation representation, int baseUrlIndex)
      throws IOException {
    if (representation.getInitializationUri() == null) {
      return null;
    }
    ChunkExtractor chunkExtractor = newChunkExtractor(trackType, representation.format);
    try {
      loadInitializationData(
          chunkExtractor, dataSource, representation, baseUrlIndex, /* loadIndex= */ false);
    } finally {
      chunkExtractor.release();
    }
    return Assertions.checkStateNotNull(chunkExtractor.getSampleFormats())[0];
  }

  /**
   * Loads initialization data for the {@code representation} and returns the sample {@link Format}.
   *
   * <p>Uses the first base URL for loading the format.
   *
   * @param dataSource The source from which the data should be loaded.
   * @param trackType The type of the representation. Typically one of the {@link C
   *     com.google.android.exoplayer2.C} {@code TRACK_TYPE_*} constants.
   * @param representation The representation which initialization chunk belongs to.
   * @return the sample {@link Format} of the given representation.
   * @throws IOException Thrown when there is an error while loading.
   */
  @Nullable
  public static Format loadSampleFormat(
      DataSource dataSource, int trackType, Representation representation) throws IOException {
    return loadSampleFormat(dataSource, trackType, representation, /* baseUrlIndex= */ 0);
  }

  /**
   * Loads initialization and index data for the {@code representation} and returns the {@link
   * ChunkIndex}.
   *
   * @param dataSource The source from which the data should be loaded.
   * @param trackType The type of the representation. Typically one of the {@link C
   *     com.google.android.exoplayer2.C} {@code TRACK_TYPE_*} constants.
   * @param representation The representation which initialization chunk belongs to.
   * @param baseUrlIndex The index of the base URL with which to resolve the request URI.
   * @return The {@link ChunkIndex} of the given representation, or null if no initialization or
   *     index data exists.
   * @throws IOException Thrown when there is an error while loading.
   */
  @Nullable
  public static ChunkIndex loadChunkIndex(
      DataSource dataSource, int trackType, Representation representation, int baseUrlIndex)
      throws IOException {
    if (representation.getInitializationUri() == null) {
      return null;
    }
    ChunkExtractor chunkExtractor = newChunkExtractor(trackType, representation.format);
    try {
      loadInitializationData(
          chunkExtractor, dataSource, representation, baseUrlIndex, /* loadIndex= */ true);
    } finally {
      chunkExtractor.release();
    }
    return chunkExtractor.getChunkIndex();
  }

  /**
   * Loads initialization and index data for the {@code representation} and returns the {@link
   * ChunkIndex}.
   *
   * <p>Uses the first base URL for loading the index.
   *
   * @param dataSource The source from which the data should be loaded.
   * @param trackType The type of the representation. Typically one of the {@link C
   *     com.google.android.exoplayer2.C} {@code TRACK_TYPE_*} constants.
   * @param representation The representation which initialization chunk belongs to.
   * @return The {@link ChunkIndex} of the given representation, or null if no initialization or
   *     index data exists.
   * @throws IOException Thrown when there is an error while loading.
   */
  @Nullable
  public static ChunkIndex loadChunkIndex(
      DataSource dataSource, int trackType, Representation representation) throws IOException {
    return loadChunkIndex(dataSource, trackType, representation, /* baseUrlIndex= */ 0);
  }

  /**
   * Loads initialization data for the {@code representation} and optionally index data then returns
   * a {@link BundledChunkExtractor} which contains the output.
   *
   * @param chunkExtractor The {@link ChunkExtractor} to use.
   * @param dataSource The source from which the data should be loaded.
   * @param representation The representation which initialization chunk belongs to.
   * @param baseUrlIndex The index of the base URL with which to resolve the request URI.
   * @param loadIndex Whether to load index data too.
   * @throws IOException Thrown when there is an error while loading.
   */
  private static void loadInitializationData(
      ChunkExtractor chunkExtractor,
      DataSource dataSource,
      Representation representation,
      int baseUrlIndex,
      boolean loadIndex)
      throws IOException {
    RangedUri initializationUri = Assertions.checkNotNull(representation.getInitializationUri());
    @Nullable RangedUri requestUri;
    if (loadIndex) {
      @Nullable RangedUri indexUri = representation.getIndexUri();
      if (indexUri == null) {
        return;
      }
      // It's common for initialization and index data to be stored adjacently. Attempt to merge
      // the two requests together to request both at once.
      requestUri =
          initializationUri.attemptMerge(indexUri, representation.baseUrls.get(baseUrlIndex).url);
      if (requestUri == null) {
        loadInitializationData(
            dataSource, representation, baseUrlIndex, chunkExtractor, initializationUri);
        requestUri = indexUri;
      }
    } else {
      requestUri = initializationUri;
    }
    loadInitializationData(dataSource, representation, baseUrlIndex, chunkExtractor, requestUri);
  }

  /**
   * Loads initialization data for the {@code representation} and optionally index data then returns
   * a {@link BundledChunkExtractor} which contains the output.
   *
   * <p>Uses the first base URL for loading the initialization data.
   *
   * @param chunkExtractor The {@link ChunkExtractor} to use.
   * @param dataSource The source from which the data should be loaded.
   * @param representation The representation which initialization chunk belongs to.
   * @param loadIndex Whether to load index data too.
   * @throws IOException Thrown when there is an error while loading.
   */
  public static void loadInitializationData(
      ChunkExtractor chunkExtractor,
      DataSource dataSource,
      Representation representation,
      boolean loadIndex)
      throws IOException {
    loadInitializationData(
        chunkExtractor, dataSource, representation, /* baseUrlIndex= */ 0, loadIndex);
  }

  private static void loadInitializationData(
      DataSource dataSource,
      Representation representation,
      int baseUrlIndex,
      ChunkExtractor chunkExtractor,
      RangedUri requestUri)
      throws IOException {
    DataSpec dataSpec =
        DashUtil.buildDataSpec(
            representation,
            representation.baseUrls.get(baseUrlIndex).url,
            requestUri,
            /* flags= */ 0);
    InitializationChunk initializationChunk =
        new InitializationChunk(
            dataSource,
            dataSpec,
            representation.format,
            C.SELECTION_REASON_UNKNOWN,
            null /* trackSelectionData */,
            chunkExtractor);
    initializationChunk.load();
  }

  /**
   * Resolves the cache key to be used when requesting the given ranged URI for the given {@link
   * Representation}.
   *
   * @param representation The {@link Representation} to which the URI belongs to.
   * @param rangedUri The URI for which to resolve the cache key.
   * @return The cache key.
   */
  public static String resolveCacheKey(Representation representation, RangedUri rangedUri) {
    @Nullable String cacheKey = representation.getCacheKey();
    return cacheKey != null
        ? cacheKey
        : rangedUri.resolveUri(representation.baseUrls.get(0).url).toString();
  }

  private static ChunkExtractor newChunkExtractor(int trackType, Format format) {
    String mimeType = format.containerMimeType;
    boolean isWebm =
        mimeType != null
            && (mimeType.startsWith(MimeTypes.VIDEO_WEBM)
                || mimeType.startsWith(MimeTypes.AUDIO_WEBM));
    Extractor extractor = isWebm ? new MatroskaExtractor() : new FragmentedMp4Extractor();
    return new BundledChunkExtractor(extractor, trackType, format);
  }

  @Nullable
  private static Representation getFirstRepresentation(Period period, @C.TrackType int type) {
    int index = period.getAdaptationSetIndex(type);
    if (index == C.INDEX_UNSET) {
      return null;
    }
    List<Representation> representations = period.adaptationSets.get(index).representations;
    return representations.isEmpty() ? null : representations.get(0);
  }

  private DashUtil() {}
}

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
package org.telegram.messenger.exoplayer2.source.dash;

import android.net.Uri;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.drm.DrmInitData;
import org.telegram.messenger.exoplayer2.extractor.ChunkIndex;
import org.telegram.messenger.exoplayer2.extractor.Extractor;
import org.telegram.messenger.exoplayer2.extractor.mkv.MatroskaExtractor;
import org.telegram.messenger.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import org.telegram.messenger.exoplayer2.source.chunk.ChunkExtractorWrapper;
import org.telegram.messenger.exoplayer2.source.chunk.InitializationChunk;
import org.telegram.messenger.exoplayer2.source.dash.manifest.AdaptationSet;
import org.telegram.messenger.exoplayer2.source.dash.manifest.DashManifest;
import org.telegram.messenger.exoplayer2.source.dash.manifest.DashManifestParser;
import org.telegram.messenger.exoplayer2.source.dash.manifest.Period;
import org.telegram.messenger.exoplayer2.source.dash.manifest.RangedUri;
import org.telegram.messenger.exoplayer2.source.dash.manifest.Representation;
import org.telegram.messenger.exoplayer2.upstream.DataSource;
import org.telegram.messenger.exoplayer2.upstream.DataSourceInputStream;
import org.telegram.messenger.exoplayer2.upstream.DataSpec;
import org.telegram.messenger.exoplayer2.upstream.HttpDataSource;
import org.telegram.messenger.exoplayer2.util.MimeTypes;
import java.io.IOException;
import java.util.List;

/**
 * Utility methods for DASH streams.
 */
public final class DashUtil {

  /**
   * Loads a DASH manifest.
   *
   * @param dataSource The {@link HttpDataSource} from which the manifest should be read.
   * @param manifestUri The URI of the manifest to be read.
   * @return An instance of {@link DashManifest}.
   * @throws IOException Thrown when there is an error while loading.
   */
  public static DashManifest loadManifest(DataSource dataSource, String manifestUri)
      throws IOException {
    DataSourceInputStream inputStream = new DataSourceInputStream(dataSource,
        new DataSpec(Uri.parse(manifestUri), DataSpec.FLAG_ALLOW_CACHING_UNKNOWN_LENGTH));
    try {
      inputStream.open();
      DashManifestParser parser = new DashManifestParser();
      return parser.parse(dataSource.getUri(), inputStream);
    } finally {
      inputStream.close();
    }
  }

  /**
   * Loads {@link DrmInitData} for a given manifest.
   *
   * @param dataSource The {@link HttpDataSource} from which data should be loaded.
   * @param dashManifest The {@link DashManifest} of the DASH content.
   * @return The loaded {@link DrmInitData}.
   */
  public static DrmInitData loadDrmInitData(DataSource dataSource, DashManifest dashManifest)
      throws IOException, InterruptedException {
    // Prefer drmInitData obtained from the manifest over drmInitData obtained from the stream,
    // as per DASH IF Interoperability Recommendations V3.0, 7.5.3.
    if (dashManifest.getPeriodCount() < 1) {
      return null;
    }
    Period period = dashManifest.getPeriod(0);
    int adaptationSetIndex = period.getAdaptationSetIndex(C.TRACK_TYPE_VIDEO);
    if (adaptationSetIndex == C.INDEX_UNSET) {
      adaptationSetIndex = period.getAdaptationSetIndex(C.TRACK_TYPE_AUDIO);
      if (adaptationSetIndex == C.INDEX_UNSET) {
        return null;
      }
    }
    AdaptationSet adaptationSet = period.adaptationSets.get(adaptationSetIndex);
    if (adaptationSet.representations.isEmpty()) {
      return null;
    }
    Representation representation = adaptationSet.representations.get(0);
    DrmInitData drmInitData = representation.format.drmInitData;
    if (drmInitData == null) {
      Format sampleFormat = DashUtil.loadSampleFormat(dataSource, representation);
      if (sampleFormat != null) {
        drmInitData = sampleFormat.drmInitData;
      }
      if (drmInitData == null) {
        return null;
      }
    }
    return drmInitData;
  }

  /**
   * Loads initialization data for the {@code representation} and returns the sample {@link
   * Format}.
   * Loads {@link DrmInitData} for a given period in a DASH manifest.
   *
   * @param dataSource The {@link HttpDataSource} from which data should be loaded.
   * @param period The {@link Period}.
   * @return The loaded {@link DrmInitData}, or null if none is defined.
   * @throws IOException Thrown when there is an error while loading.
   * @throws InterruptedException Thrown if the thread was interrupted.
   */
  public static DrmInitData loadDrmInitData(DataSource dataSource, Period period)
      throws IOException, InterruptedException {
    Representation representation = getFirstRepresentation(period, C.TRACK_TYPE_VIDEO);
    if (representation == null) {
      representation = getFirstRepresentation(period, C.TRACK_TYPE_AUDIO);
      if (representation == null) {
        return null;
      }
    }
    DrmInitData drmInitData = representation.format.drmInitData;
    if (drmInitData != null) {
      // Prefer drmInitData obtained from the manifest over drmInitData obtained from the stream,
      // as per DASH IF Interoperability Recommendations V3.0, 7.5.3.
      return drmInitData;
    }
    Format sampleFormat = DashUtil.loadSampleFormat(dataSource, representation);
    return sampleFormat == null ? null : sampleFormat.drmInitData;
  }

  /**
   * Loads initialization data for the {@code representation} and returns the sample {@link Format}.
   *
   * @param dataSource The source from which the data should be loaded.
   * @param representation The representation which initialization chunk belongs to.
   * @return the sample {@link Format} of the given representation.
   * @throws IOException Thrown when there is an error while loading.
   * @throws InterruptedException Thrown if the thread was interrupted.
   */
  public static Format loadSampleFormat(DataSource dataSource, Representation representation)
      throws IOException, InterruptedException {
    ChunkExtractorWrapper extractorWrapper = loadInitializationData(dataSource, representation,
        false);
    return extractorWrapper == null ? null : extractorWrapper.getSampleFormats()[0];
  }

  /**
   * Loads initialization and index data for the {@code representation} and returns the {@link
   * ChunkIndex}.
   *
   * @param dataSource The source from which the data should be loaded.
   * @param representation The representation which initialization chunk belongs to.
   * @return {@link ChunkIndex} of the given representation.
   * @throws IOException Thrown when there is an error while loading.
   * @throws InterruptedException Thrown if the thread was interrupted.
   */
  public static ChunkIndex loadChunkIndex(DataSource dataSource, Representation representation)
      throws IOException, InterruptedException {
    ChunkExtractorWrapper extractorWrapper = loadInitializationData(dataSource, representation,
        true);
    return extractorWrapper == null ? null : (ChunkIndex) extractorWrapper.getSeekMap();
  }

  /**
   * Loads initialization data for the {@code representation} and optionally index data then
   * returns a {@link ChunkExtractorWrapper} which contains the output.
   *
   * @param dataSource The source from which the data should be loaded.
   * @param representation The representation which initialization chunk belongs to.
   * @param loadIndex Whether to load index data too.
   * @return A {@link ChunkExtractorWrapper} for the {@code representation}, or null if no
   *     initialization or (if requested) index data exists.
   * @throws IOException Thrown when there is an error while loading.
   * @throws InterruptedException Thrown if the thread was interrupted.
   */
  private static ChunkExtractorWrapper loadInitializationData(DataSource dataSource,
      Representation representation, boolean loadIndex)
      throws IOException, InterruptedException {
    RangedUri initializationUri = representation.getInitializationUri();
    if (initializationUri == null) {
      return null;
    }
    ChunkExtractorWrapper extractorWrapper = newWrappedExtractor(representation.format);
    RangedUri requestUri;
    if (loadIndex) {
      RangedUri indexUri = representation.getIndexUri();
      if (indexUri == null) {
        return null;
      }
      // It's common for initialization and index data to be stored adjacently. Attempt to merge
      // the two requests together to request both at once.
      requestUri = initializationUri.attemptMerge(indexUri, representation.baseUrl);
      if (requestUri == null) {
        loadInitializationData(dataSource, representation, extractorWrapper, initializationUri);
        requestUri = indexUri;
      }
    } else {
      requestUri = initializationUri;
    }
    loadInitializationData(dataSource, representation, extractorWrapper, requestUri);
    return extractorWrapper;
  }

  private static void loadInitializationData(DataSource dataSource,
      Representation representation, ChunkExtractorWrapper extractorWrapper, RangedUri requestUri)
      throws IOException, InterruptedException {
    DataSpec dataSpec = new DataSpec(requestUri.resolveUri(representation.baseUrl),
        requestUri.start, requestUri.length, representation.getCacheKey());
    InitializationChunk initializationChunk = new InitializationChunk(dataSource, dataSpec,
        representation.format, C.SELECTION_REASON_UNKNOWN, null /* trackSelectionData */,
        extractorWrapper);
    initializationChunk.load();
  }

  private static ChunkExtractorWrapper newWrappedExtractor(Format format) {
    String mimeType = format.containerMimeType;
    boolean isWebm = mimeType.startsWith(MimeTypes.VIDEO_WEBM)
        || mimeType.startsWith(MimeTypes.AUDIO_WEBM);
    Extractor extractor = isWebm ? new MatroskaExtractor() : new FragmentedMp4Extractor();
    return new ChunkExtractorWrapper(extractor, format);
  }

  private static Representation getFirstRepresentation(Period period, int type) {
    int index = period.getAdaptationSetIndex(type);
    if (index == C.INDEX_UNSET) {
      return null;
    }
    List<Representation> representations = period.adaptationSets.get(index).representations;
    return representations.isEmpty() ? null : representations.get(0);
  }

  private DashUtil() {}

}

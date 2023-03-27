/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.jpeg;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.mp4.Mp4Extractor;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.mp4.MotionPhotoMetadata;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Extracts JPEG image using the Exif format. */
public final class JpegExtractor implements Extractor {

  /** Parser states. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    STATE_READING_MARKER,
    STATE_READING_SEGMENT_LENGTH,
    STATE_READING_SEGMENT,
    STATE_SNIFFING_MOTION_PHOTO_VIDEO,
    STATE_READING_MOTION_PHOTO_VIDEO,
    STATE_ENDED,
  })
  private @interface State {}

  private static final int STATE_READING_MARKER = 0;
  private static final int STATE_READING_SEGMENT_LENGTH = 1;
  private static final int STATE_READING_SEGMENT = 2;
  private static final int STATE_SNIFFING_MOTION_PHOTO_VIDEO = 4;
  private static final int STATE_READING_MOTION_PHOTO_VIDEO = 5;
  private static final int STATE_ENDED = 6;

  private static final int EXIF_ID_CODE_LENGTH = 6;
  private static final long EXIF_HEADER = 0x45786966; // Exif
  private static final int MARKER_SOI = 0xFFD8; // Start of image marker
  private static final int MARKER_SOS = 0xFFDA; // Start of scan (image data) marker
  private static final int MARKER_APP0 = 0xFFE0; // Application data 0 marker
  private static final int MARKER_APP1 = 0xFFE1; // Application data 1 marker
  private static final String HEADER_XMP_APP1 = "http://ns.adobe.com/xap/1.0/";

  /**
   * The identifier to use for the image track. Chosen to avoid colliding with track IDs used by
   * {@link Mp4Extractor} for motion photos.
   */
  private static final int IMAGE_TRACK_ID = 1024;

  private final ParsableByteArray scratch;

  private @MonotonicNonNull ExtractorOutput extractorOutput;

  private @State int state;
  private int marker;
  private int segmentLength;
  private long mp4StartPosition;

  @Nullable private MotionPhotoMetadata motionPhotoMetadata;
  private @MonotonicNonNull ExtractorInput lastExtractorInput;
  private @MonotonicNonNull StartOffsetExtractorInput mp4ExtractorStartOffsetExtractorInput;
  @Nullable private Mp4Extractor mp4Extractor;

  public JpegExtractor() {
    scratch = new ParsableByteArray(EXIF_ID_CODE_LENGTH);
    mp4StartPosition = C.POSITION_UNSET;
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    // See ITU-T.81 (1992) subsection B.1.1.3 and Exif version 2.2 (2002) subsection 4.5.4.
    if (peekMarker(input) != MARKER_SOI) {
      return false;
    }
    marker = peekMarker(input);
    // Even though JFIF and Exif standards are incompatible in theory, Exif files often contain a
    // JFIF APP0 marker segment preceding the Exif APP1 marker segment. Skip the JFIF segment if
    // present.
    if (marker == MARKER_APP0) {
      advancePeekPositionToNextSegment(input);
      marker = peekMarker(input);
    }
    if (marker != MARKER_APP1) {
      return false;
    }
    input.advancePeekPosition(2); // Unused segment length
    scratch.reset(/* limit= */ EXIF_ID_CODE_LENGTH);
    input.peekFully(scratch.getData(), /* offset= */ 0, EXIF_ID_CODE_LENGTH);
    return scratch.readUnsignedInt() == EXIF_HEADER && scratch.readUnsignedShort() == 0; // Exif\0\0
  }

  @Override
  public void init(ExtractorOutput output) {
    extractorOutput = output;
  }

  @Override
  public @ReadResult int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException {
    switch (state) {
      case STATE_READING_MARKER:
        readMarker(input);
        return RESULT_CONTINUE;
      case STATE_READING_SEGMENT_LENGTH:
        readSegmentLength(input);
        return RESULT_CONTINUE;
      case STATE_READING_SEGMENT:
        readSegment(input);
        return RESULT_CONTINUE;
      case STATE_SNIFFING_MOTION_PHOTO_VIDEO:
        if (input.getPosition() != mp4StartPosition) {
          seekPosition.position = mp4StartPosition;
          return RESULT_SEEK;
        }
        sniffMotionPhotoVideo(input);
        return RESULT_CONTINUE;
      case STATE_READING_MOTION_PHOTO_VIDEO:
        if (mp4ExtractorStartOffsetExtractorInput == null || input != lastExtractorInput) {
          lastExtractorInput = input;
          mp4ExtractorStartOffsetExtractorInput =
              new StartOffsetExtractorInput(input, mp4StartPosition);
        }
        @ReadResult
        int readResult =
            checkNotNull(mp4Extractor).read(mp4ExtractorStartOffsetExtractorInput, seekPosition);
        if (readResult == RESULT_SEEK) {
          seekPosition.position += mp4StartPosition;
        }
        return readResult;
      case STATE_ENDED:
        return RESULT_END_OF_INPUT;
      default:
        throw new IllegalStateException();
    }
  }

  @Override
  public void seek(long position, long timeUs) {
    if (position == 0) {
      state = STATE_READING_MARKER;
      mp4Extractor = null;
    } else if (state == STATE_READING_MOTION_PHOTO_VIDEO) {
      checkNotNull(mp4Extractor).seek(position, timeUs);
    }
  }

  @Override
  public void release() {
    if (mp4Extractor != null) {
      mp4Extractor.release();
    }
  }

  private int peekMarker(ExtractorInput input) throws IOException {
    scratch.reset(/* limit= */ 2);
    input.peekFully(scratch.getData(), /* offset= */ 0, /* length= */ 2);
    return scratch.readUnsignedShort();
  }

  private void advancePeekPositionToNextSegment(ExtractorInput input) throws IOException {
    scratch.reset(/* limit= */ 2);
    input.peekFully(scratch.getData(), /* offset= */ 0, /* length= */ 2);
    int segmentLength = scratch.readUnsignedShort() - 2;
    input.advancePeekPosition(segmentLength);
  }

  private void readMarker(ExtractorInput input) throws IOException {
    scratch.reset(/* limit= */ 2);
    input.readFully(scratch.getData(), /* offset= */ 0, /* length= */ 2);
    marker = scratch.readUnsignedShort();
    if (marker == MARKER_SOS) { // Start of scan.
      if (mp4StartPosition != C.POSITION_UNSET) {
        state = STATE_SNIFFING_MOTION_PHOTO_VIDEO;
      } else {
        endReadingWithImageTrack();
      }
    } else if ((marker < 0xFFD0 || marker > 0xFFD9) && marker != 0xFF01) {
      state = STATE_READING_SEGMENT_LENGTH;
    }
  }

  private void readSegmentLength(ExtractorInput input) throws IOException {
    scratch.reset(2);
    input.readFully(scratch.getData(), /* offset= */ 0, /* length= */ 2);
    segmentLength = scratch.readUnsignedShort() - 2;
    state = STATE_READING_SEGMENT;
  }

  private void readSegment(ExtractorInput input) throws IOException {
    if (marker == MARKER_APP1) {
      ParsableByteArray payload = new ParsableByteArray(segmentLength);
      input.readFully(payload.getData(), /* offset= */ 0, /* length= */ segmentLength);
      if (motionPhotoMetadata == null
          && HEADER_XMP_APP1.equals(payload.readNullTerminatedString())) {
        @Nullable String xmpString = payload.readNullTerminatedString();
        if (xmpString != null) {
          motionPhotoMetadata = getMotionPhotoMetadata(xmpString, input.getLength());
          if (motionPhotoMetadata != null) {
            mp4StartPosition = motionPhotoMetadata.videoStartPosition;
          }
        }
      }
    } else {
      input.skipFully(segmentLength);
    }
    state = STATE_READING_MARKER;
  }

  private void sniffMotionPhotoVideo(ExtractorInput input) throws IOException {
    // Check if the file is truncated.
    boolean peekedData =
        input.peekFully(
            scratch.getData(), /* offset= */ 0, /* length= */ 1, /* allowEndOfInput= */ true);
    if (!peekedData) {
      endReadingWithImageTrack();
    } else {
      input.resetPeekPosition();
      if (mp4Extractor == null) {
        mp4Extractor = new Mp4Extractor();
      }
      mp4ExtractorStartOffsetExtractorInput =
          new StartOffsetExtractorInput(input, mp4StartPosition);
      if (mp4Extractor.sniff(mp4ExtractorStartOffsetExtractorInput)) {
        mp4Extractor.init(
            new StartOffsetExtractorOutput(mp4StartPosition, checkNotNull(extractorOutput)));
        startReadingMotionPhoto();
      } else {
        endReadingWithImageTrack();
      }
    }
  }

  private void startReadingMotionPhoto() {
    outputImageTrack(checkNotNull(motionPhotoMetadata));
    state = STATE_READING_MOTION_PHOTO_VIDEO;
  }

  private void endReadingWithImageTrack() {
    outputImageTrack();
    checkNotNull(extractorOutput).endTracks();
    extractorOutput.seekMap(new SeekMap.Unseekable(/* durationUs= */ C.TIME_UNSET));
    state = STATE_ENDED;
  }

  private void outputImageTrack(Metadata.Entry... metadataEntries) {
    TrackOutput imageTrackOutput =
        checkNotNull(extractorOutput).track(IMAGE_TRACK_ID, C.TRACK_TYPE_IMAGE);
    imageTrackOutput.format(
        new Format.Builder()
            .setContainerMimeType(MimeTypes.IMAGE_JPEG)
            .setMetadata(new Metadata(metadataEntries))
            .build());
  }

  /**
   * Attempts to parse the specified XMP data describing the motion photo, returning the resulting
   * {@link MotionPhotoMetadata} or {@code null} if it wasn't possible to derive motion photo
   * metadata.
   *
   * @param xmpString A string of XML containing XMP motion photo metadata to attempt to parse.
   * @param inputLength The length of the input stream in bytes, or {@link C#LENGTH_UNSET} if
   *     unknown.
   * @return The {@link MotionPhotoMetadata}, or {@code null} if it wasn't possible to derive motion
   *     photo metadata.
   * @throws IOException If an error occurs parsing the XMP string.
   */
  @Nullable
  private static MotionPhotoMetadata getMotionPhotoMetadata(String xmpString, long inputLength)
      throws IOException {
    // Metadata defines offsets from the end of the stream, so we need the stream length to
    // determine start offsets.
    if (inputLength == C.LENGTH_UNSET) {
      return null;
    }

    // Motion photos have (at least) a primary image media item and a secondary video media item.
    @Nullable
    MotionPhotoDescription motionPhotoDescription =
        XmpMotionPhotoDescriptionParser.parse(xmpString);
    if (motionPhotoDescription == null) {
      return null;
    }
    return motionPhotoDescription.getMotionPhotoMetadata(inputLength);
  }
}
